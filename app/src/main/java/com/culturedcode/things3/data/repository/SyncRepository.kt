package com.culturedcode.things3.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.local.entity.TaskEntity
import com.culturedcode.things3.data.notifications.ReminderScheduler
import com.culturedcode.things3.data.remote.dto.AreaFirestoreDto
import com.culturedcode.things3.data.remote.dto.AttachmentFirestoreDto
import com.culturedcode.things3.data.remote.dto.ChecklistItemFirestoreDto
import com.culturedcode.things3.data.remote.dto.CloudDeltaBatchPayload
import com.culturedcode.things3.data.remote.dto.CloudUserAuthEnvelope
import com.culturedcode.things3.data.remote.dto.HeadingFirestoreDto
import com.culturedcode.things3.data.remote.dto.ProjectFirestoreDto
import com.culturedcode.things3.data.remote.dto.ProjectTemplateFirestoreDto
import com.culturedcode.things3.data.remote.dto.TagFirestoreDto
import com.culturedcode.things3.data.remote.dto.TaskFirestoreDto
import com.culturedcode.things3.data.remote.dto.toFirestoreDto
import com.culturedcode.things3.domain.model.RecurrenceRule
import com.culturedcode.things3.domain.model.TaskStatus
import com.culturedcode.things3.domain.util.runCatchingCancellable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

val Context.syncDataStore by preferencesDataStore(name = "things3_cloud_sync_prefs")
val SYNC_KEY_LOCAL_ONLY_DB_MODE = booleanPreferencesKey("local_only_db_mode")

enum class CloudSyncStatus {
    OFFLINE,
    SYNCING,
    SYNCED
}

@Serializable
private data class NtfyStreamLine(
    val id: String = "",
    val time: Long = 0L,
    val event: String = "",
    val topic: String = "",
    val message: String = ""
)

@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ThingsDatabase,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val reminderScheduler: ReminderScheduler
) {
    fun resolveAttachmentFile(localRelativePath: String): File {
        return resolveVerifiedAttachmentFile(context.filesDir, localRelativePath)
    }

    fun buildSafeLocalAttachmentPath(attachmentId: String, fileName: String): String {
        return Companion.buildSafeLocalAttachmentPath(attachmentId, fileName)
    }

    fun readAttachmentBase64(localRelativePath: String, maxRawBytes: Long = MAX_INLINE_SYNC_BYTES): String {
        return readAttachmentBase64InRoot(context.filesDir, localRelativePath, maxRawBytes)
    }

    fun writeAttachmentBase64(localRelativePath: String, dataBase64: String): Long {
        return writeAttachmentBase64InRoot(context.filesDir, localRelativePath, dataBase64)
    }

    fun deleteAttachmentFile(localRelativePath: String) {
        deleteAttachmentFileInRoot(context.filesDir, localRelativePath)
    }

    fun deleteLocalAttachmentFile(localRelativePath: String) {
        deleteAttachmentFile(localRelativePath)
    }

    fun clearAllLocalAttachmentFiles() {
        runCatching {
            val attachmentsRoot = File(context.filesDir, "attachments")
            if (attachmentsRoot.exists() && attachmentsRoot.isDirectory) {
                attachmentsRoot.listFiles()?.forEach { child ->
                    if (child.isFile) {
                        runCatching { child.delete() }
                    } else if (child.isDirectory) {
                        runCatching { child.deleteRecursively() }
                    }
                }
            }
        }
    }

    private fun syncTaskReminderSchedule(entity: TaskEntity) {
        val now = System.currentTimeMillis()
        if (!entity.isDeleted && entity.status == TaskStatus.OPEN &&
            entity.reminderTime != null && entity.reminderTime > now
        ) {
            reminderScheduler.scheduleExactReminder(entity.id, entity.title, entity.reminderTime)
        } else {
            reminderScheduler.cancelReminder(entity.id)
        }
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w("SyncRepository", "Unhandled background sync exception", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val syncMutex = Mutex()
    private val fastPushMutex = Mutex()
    private val dbApplyMutex = Mutex()
    private val activeListeners = java.util.Collections.synchronizedList(mutableListOf<ListenerRegistration>())
    private var realtimeCloudPollJob: Job? = null
    private var liveNtfyStreamJob: Job? = null
    private var listenerReconnectJob: Job? = null
    private var fastPushCoalesceJob: Job? = null
    private var incrementalPullJob: Job? = null
    @Volatile
    private var pendingFastPush: Boolean = false
    @Volatile
    private var lastHandledPulseAt: Long = 0L
    private val lastNtfyMessageIdByTopic = ConcurrentHashMap<String, String>()

    @Volatile
    private var dataStoreLocalOnlyFlag: Boolean = false

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val KEY_ACTIVE_USER_ID = stringPreferencesKey("builtin_cloud_user_id")
    private val KEY_ACTIVE_USER_EMAIL = stringPreferencesKey("builtin_cloud_user_email")
    private val KEY_ACTIVE_CHANNEL_TOPIC = stringPreferencesKey("builtin_cloud_channel_topic")
    private val KEY_DEVICE_ID = stringPreferencesKey("builtin_cloud_device_id")

    private val _syncStatus = MutableStateFlow(CloudSyncStatus.OFFLINE)
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow(0L)
    val lastSyncedAt: StateFlow<Long> = _lastSyncedAt.asStateFlow()

    private val _activeUserIdentifier = MutableStateFlow<String?>(null)
    val activeUserIdentifier: StateFlow<String?> = _activeUserIdentifier.asStateFlow()

    init {
        scope.launch {
            context.syncDataStore.data
                .catch { emit(emptyPreferences()) }
                .collect { prefs ->
                    dataStoreLocalOnlyFlag = prefs[SYNC_KEY_LOCAL_ONLY_DB_MODE] == true
                }
        }
        scope.launch {
            delay(150L)
            runCatchingCancellable {
                restoreBuiltInCloudSession()
            }.onFailure { err ->
                Log.w("SyncRepository", "Failed to restore cloud session on startup", err)
            }
        }
    }

    fun isLocalOnlyDbModeEnabled(): Boolean = runCatching {
        val uiPrefs = context.getSharedPreferences("thingsbook_ui_prefs", Context.MODE_PRIVATE)
            .getBoolean("local_only_db_mode", false)
        val settingsPrefs = context.getSharedPreferences("thingsbook_settings_prefs", Context.MODE_PRIVATE)
            .getBoolean("local_only_db_mode", false)
        uiPrefs || settingsPrefs || dataStoreLocalOnlyFlag
    }.getOrDefault(dataStoreLocalOnlyFlag)

    suspend fun setLocalOnlyDbModePreference(localOnly: Boolean) {
        dataStoreLocalOnlyFlag = localOnly
        runCatchingCancellable {
            context.syncDataStore.edit { prefs ->
                prefs[SYNC_KEY_LOCAL_ONLY_DB_MODE] = localOnly
            }
        }
        if (localOnly) {
            detachRealtimeDeltaListeners()
            _syncStatus.value = CloudSyncStatus.OFFLINE
        }
    }

    fun hasActiveCloudAccount(): Boolean {
        if (!_activeUserIdentifier.value.isNullOrBlank()) return true
        return runCatching { firebaseAuth.currentUser != null }.getOrDefault(false)
    }

    fun triggerBackgroundSync() {
        if (isLocalOnlyDbModeEnabled()) {
            _syncStatus.value = CloudSyncStatus.OFFLINE
            return
        }
        triggerFastOutboundPush(FAST_PUSH_COALESCE_MS)
    }

    private fun triggerFastOutboundPush(delayMs: Long = FAST_PUSH_COALESCE_MS) {
        if (isLocalOnlyDbModeEnabled()) return
        pendingFastPush = true
        fastPushCoalesceJob?.cancel()
        fastPushCoalesceJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            runFastPushLoop()
        }
    }

    private suspend fun runFastPushLoop() {
        if (isLocalOnlyDbModeEnabled()) return
        fastPushMutex.withLock {
            while (pendingFastPush) {
                pendingFastPush = false
                runCatchingCancellable {
                    val prefs = context.syncDataStore.data
                        .catch { emit(emptyPreferences()) }
                        .first()
                    dataStoreLocalOnlyFlag = prefs[SYNC_KEY_LOCAL_ONLY_DB_MODE] == true
                    if (isLocalOnlyDbModeEnabled()) {
                        _syncStatus.value = CloudSyncStatus.OFFLINE
                        return@runCatchingCancellable
                    }
                    val userId = prefs[KEY_ACTIVE_USER_ID]
                        ?: runCatching { firebaseAuth.currentUser?.uid }.getOrNull()
                        ?: return@runCatchingCancellable
                    val channelTopic = prefs[KEY_ACTIVE_CHANNEL_TOPIC] ?: deriveOAuthChannelTopic(userId)

                    val pushedOk = pushDirtyRecordsPrimaryFirst(userId, channelTopic)
                    if (pushedOk) {
                        val nowMillis = System.currentTimeMillis()
                        _lastSyncedAt.value = nowMillis
                        _syncStatus.value = CloudSyncStatus.SYNCED
                    }
                }.onFailure { err ->
                    Log.w("SyncRepository", "runFastPushLoop failed", err)
                }
            }
        }
    }

    private fun scheduleIncrementalFirestorePull(userId: String, delayMs: Long = 15L) {
        if (isLocalOnlyDbModeEnabled() || !isLiveFirebaseConfigured()) return
        incrementalPullJob?.cancel()
        incrementalPullJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            runCatchingCancellable {
                val lastSync = getLastSyncTimestamp(userId)
                val fbMax = pullDeltaRecordsFromFirestore(userId, lastSync)
                val nowMillis = System.currentTimeMillis()
                val clamped = clampRemoteTimestamp(fbMax, nowMillis)
                if (clamped > lastSync) {
                    saveLastSyncTimestamp(userId, clamped)
                }
                _lastSyncedAt.value = nowMillis
                _syncStatus.value = CloudSyncStatus.SYNCED
            }
        }
    }

    private suspend fun getOrCreateDeviceId(): String {
        val prefs = context.syncDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()
        val existing = prefs[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        runCatchingCancellable {
            context.syncDataStore.edit { it[KEY_DEVICE_ID] = created }
        }
        return created
    }

    private suspend fun restoreBuiltInCloudSession() {
        val prefs = context.syncDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()
        dataStoreLocalOnlyFlag = prefs[SYNC_KEY_LOCAL_ONLY_DB_MODE] == true
        if (isLocalOnlyDbModeEnabled()) {
            _syncStatus.value = CloudSyncStatus.OFFLINE
            return
        }

        val userId = prefs[KEY_ACTIVE_USER_ID]
        val email = prefs[KEY_ACTIVE_USER_EMAIL]
        val channelTopic = prefs[KEY_ACTIVE_CHANNEL_TOPIC]

        if (email != null && email.endsWith("@thingsbook.app")) {
            signOutAccount()
            return
        }

        if (!userId.isNullOrBlank() && !channelTopic.isNullOrBlank()) {
            _lastSyncedAt.value = prefs[lastSyncKeyForUser(userId)] ?: 0L
            prefs[lastNtfyMsgIdKeyForTopic(channelTopic)]?.takeIf { it.isNotBlank() }?.let {
                lastNtfyMessageIdByTopic[channelTopic] = it
            }
            _activeUserIdentifier.value = email ?: userId
            syncBidirectionalDeltas()
            startRealtimeMultiDeviceSync(userId, channelTopic)
        } else {
            val fbUser = runCatching { firebaseAuth.currentUser }.getOrNull()
            if (fbUser != null) {
                val fbEmail = fbUser.email
                if (fbEmail != null && fbEmail.endsWith("@thingsbook.app")) {
                    signOutAccount()
                    return
                }
                val resolvedTopic = channelTopic?.takeIf { it.isNotBlank() }
                    ?: deriveOAuthChannelTopic(fbUser.uid)
                runCatchingCancellable {
                    context.syncDataStore.edit { editPrefs ->
                        editPrefs[KEY_ACTIVE_USER_ID] = fbUser.uid
                        if (!fbEmail.isNullOrBlank()) {
                            editPrefs[KEY_ACTIVE_USER_EMAIL] = fbEmail
                        }
                        editPrefs[KEY_ACTIVE_CHANNEL_TOPIC] = resolvedTopic
                    }
                }
                _lastSyncedAt.value = prefs[lastSyncKeyForUser(fbUser.uid)] ?: 0L
                _activeUserIdentifier.value = fbEmail ?: fbUser.uid
                syncBidirectionalDeltas()
                startRealtimeMultiDeviceSync(fbUser.uid, resolvedTopic)
            }
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun deriveUserId(normalizedEmail: String): String =
        UUID.nameUUIDFromBytes("thingsbook-uid:$normalizedEmail".toByteArray(StandardCharsets.UTF_8)).toString()

    private fun derivePasswordVerifier(normalizedEmail: String, password: String): String =
        sha256Hex("thingsbook-pw-v1:$normalizedEmail:$password")

    private fun deriveUserRegistryTopic(normalizedEmail: String): String =
        "tb_usr_${sha256Hex("thingsbook-reg-v1:$normalizedEmail").take(26)}"

    private fun deriveDeltaChannelTopic(normalizedEmail: String, passwordVerifier: String): String =
        "tb_sync_${sha256Hex("thingsbook-chan-v1:$normalizedEmail:$passwordVerifier").take(28)}"

    private fun deriveOAuthChannelTopic(uid: String): String =
        "tb_sync_${sha256Hex("thingsbook-oauth:$uid").take(28)}"

    private suspend fun authenticateFirebaseEmailWithFallback(
        email: String,
        password: String,
        allowAutoCreateOnSignIn: Boolean
    ): String {
        return try {
            val signInRes = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            signInRes.user?.uid ?: deriveUserId(email)
        } catch (ce: CancellationException) {
            throw ce
        } catch (signInErr: Exception) {
            if (allowAutoCreateOnSignIn) {
                try {
                    val createRes = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                    createRes.user?.uid ?: deriveUserId(email)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (createErr: Exception) {
                    val msg = (createErr.message ?: signInErr.message ?: "").lowercase(Locale.ROOT)
                    if (msg.contains("password") || msg.contains("credential") || msg.contains("already in use")) {
                        throw IllegalArgumentException(localizedWrongPasswordError(email))
                    }
                    throw createErr
                }
            } else {
                throw signInErr
            }
        }
    }

    suspend fun signInWithGoogleOAuth(activity: Activity): Result<String> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            _syncStatus.value = CloudSyncStatus.SYNCING

            val provider = OAuthProvider.newBuilder("google.com").apply {
                addCustomParameter("prompt", "select_account")
            }.build()

            val authResult = withContext(Dispatchers.Main) {
                firebaseAuth.pendingAuthResult?.await()
                    ?: firebaseAuth.startActivityForSignInWithProvider(activity, provider).await()
            }

            val fbUser = authResult.user
                ?: throw IllegalStateException(localizedGoogleSignInFailedError())
            val uid = fbUser.uid
            val email = fbUser.email?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: uid
            val channelTopic = deriveOAuthChannelTopic(uid)

            lastNtfyMessageIdByTopic.remove(channelTopic)
            context.syncDataStore.edit { prefs ->
                prefs[KEY_ACTIVE_USER_ID] = uid
                prefs[KEY_ACTIVE_USER_EMAIL] = email
                prefs[KEY_ACTIVE_CHANNEL_TOPIC] = channelTopic
                prefs[lastSyncKeyForUser(uid)] = 0L
                prefs.remove(lastNtfyMsgIdKeyForTopic(channelTopic))
            }
            _activeUserIdentifier.value = email

            if (!isLocalOnlyDbModeEnabled()) {
                var maxPulled = 0L
                val fbPulledOk = if (isLiveFirebaseConfigured()) {
                    runCatchingCancellable { pullDeltaRecordsFromFirestore(uid, 0L) }
                        .onSuccess { maxPulled = maxOf(maxPulled, it) }
                        .isSuccess
                } else {
                    false
                }
                if (!fbPulledOk) {
                    maxPulled = maxOf(
                        maxPulled,
                        pullDeltaRecordsFromBuiltInCloud(uid, channelTopic, 0L, forceFullHistory = true)
                    )
                } else {
                    scope.launch {
                        runCatchingCancellable {
                            pullDeltaRecordsFromBuiltInCloud(uid, channelTopic, 0L, forceFullHistory = true)
                        }
                    }
                }
                pushDirtyRecordsPrimaryFirst(uid, channelTopic)
                val nowMillis = System.currentTimeMillis()
                saveLastSyncTimestamp(uid, maxOf(clampRemoteTimestamp(maxPulled, nowMillis), nowMillis))
                _lastSyncedAt.value = nowMillis
                _syncStatus.value = CloudSyncStatus.SYNCED
                startRealtimeMultiDeviceSync(uid, channelTopic)
            } else {
                _syncStatus.value = CloudSyncStatus.OFFLINE
            }
            email
        }.onFailure { err ->
            Log.w("SyncRepository", "signInWithGoogleOAuth failed", err)
            _syncStatus.value = CloudSyncStatus.OFFLINE
        }
    }

    suspend fun registerAccount(rawEmail: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val email = rawEmail.trim().lowercase(Locale.ROOT)
            require(email.contains("@") && email.contains(".")) { localizedInvalidEmailError() }
            require(password.length >= 6) { localizedPasswordTooShortError() }

            _syncStatus.value = CloudSyncStatus.SYNCING

            val passwordVerifier = derivePasswordVerifier(email, password)
            val registryTopic = deriveUserRegistryTopic(email)
            val channelTopic = deriveDeltaChannelTopic(email, passwordVerifier)

            val userId = if (isLiveFirebaseConfigured()) {
                try {
                    val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                    authResult.user?.uid ?: deriveUserId(email)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    authenticateFirebaseEmailWithFallback(email, password, allowAutoCreateOnSignIn = false)
                }
            } else {
                val existingEnvelope = fetchCloudUserEnvelope(registryTopic)
                if (existingEnvelope != null && existingEnvelope.passwordVerifierHash != passwordVerifier) {
                    throw IllegalStateException(localizedAccountAlreadyExistsError())
                }
                val derivedId = deriveUserId(email)
                if (existingEnvelope == null) {
                    val envelope = CloudUserAuthEnvelope(
                        userId = derivedId,
                        email = email,
                        passwordVerifierHash = passwordVerifier,
                        createdAt = System.currentTimeMillis()
                    )
                    postJsonToBuiltInCloud(registryTopic, json.encodeToString(envelope))
                }
                derivedId
            }

            lastNtfyMessageIdByTopic.remove(channelTopic)
            context.syncDataStore.edit { prefs ->
                prefs[KEY_ACTIVE_USER_ID] = userId
                prefs[KEY_ACTIVE_USER_EMAIL] = email
                prefs[KEY_ACTIVE_CHANNEL_TOPIC] = channelTopic
                prefs[lastSyncKeyForUser(userId)] = 0L
                prefs.remove(lastNtfyMsgIdKeyForTopic(channelTopic))
            }
            _activeUserIdentifier.value = email

            if (!isLocalOnlyDbModeEnabled()) {
                var maxPulled = 0L
                val fbPulledOk = if (isLiveFirebaseConfigured()) {
                    runCatchingCancellable { pullDeltaRecordsFromFirestore(userId, 0L) }
                        .onSuccess { maxPulled = maxOf(maxPulled, it) }
                        .isSuccess
                } else {
                    false
                }
                if (!fbPulledOk) {
                    maxPulled = maxOf(
                        maxPulled,
                        pullDeltaRecordsFromBuiltInCloud(userId, channelTopic, 0L, forceFullHistory = true)
                    )
                } else {
                    scope.launch {
                        runCatchingCancellable {
                            pullDeltaRecordsFromBuiltInCloud(userId, channelTopic, 0L, forceFullHistory = true)
                        }
                    }
                }
                pushDirtyRecordsPrimaryFirst(userId, channelTopic)
                val nowMillis = System.currentTimeMillis()
                saveLastSyncTimestamp(userId, maxOf(clampRemoteTimestamp(maxPulled, nowMillis), nowMillis))
                _lastSyncedAt.value = nowMillis
                _syncStatus.value = CloudSyncStatus.SYNCED
                startRealtimeMultiDeviceSync(userId, channelTopic)
            } else {
                _syncStatus.value = CloudSyncStatus.OFFLINE
            }
            email
        }.onFailure { err ->
            Log.w("SyncRepository", "registerAccount failed", err)
            _syncStatus.value = CloudSyncStatus.OFFLINE
        }
    }

    suspend fun signInAccount(rawEmail: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val email = rawEmail.trim().lowercase(Locale.ROOT)
            require(email.contains("@") && email.contains(".")) { localizedInvalidEmailError() }
            require(password.length >= 6) { localizedPasswordTooShortError() }

            _syncStatus.value = CloudSyncStatus.SYNCING

            val passwordVerifier = derivePasswordVerifier(email, password)
            val registryTopic = deriveUserRegistryTopic(email)
            val channelTopic = deriveDeltaChannelTopic(email, passwordVerifier)

            val userId = if (isLiveFirebaseConfigured()) {
                authenticateFirebaseEmailWithFallback(email, password, allowAutoCreateOnSignIn = true)
            } else {
                val existingEnvelope = fetchCloudUserEnvelope(registryTopic)
                val derivedId = deriveUserId(email)
                if (existingEnvelope != null && existingEnvelope.passwordVerifierHash != passwordVerifier) {
                    throw IllegalArgumentException(localizedWrongPasswordError(email))
                } else if (existingEnvelope == null) {
                    val envelope = CloudUserAuthEnvelope(
                        userId = derivedId,
                        email = email,
                        passwordVerifierHash = passwordVerifier,
                        createdAt = System.currentTimeMillis()
                    )
                    postJsonToBuiltInCloud(registryTopic, json.encodeToString(envelope))
                }
                derivedId
            }

            lastNtfyMessageIdByTopic.remove(channelTopic)
            context.syncDataStore.edit { prefs ->
                prefs[KEY_ACTIVE_USER_ID] = userId
                prefs[KEY_ACTIVE_USER_EMAIL] = email
                prefs[KEY_ACTIVE_CHANNEL_TOPIC] = channelTopic
                prefs[lastSyncKeyForUser(userId)] = 0L
                prefs.remove(lastNtfyMsgIdKeyForTopic(channelTopic))
            }
            _activeUserIdentifier.value = email

            if (!isLocalOnlyDbModeEnabled()) {
                var maxPulled = 0L
                val fbPulledOk = if (isLiveFirebaseConfigured()) {
                    runCatchingCancellable { pullDeltaRecordsFromFirestore(userId, 0L) }
                        .onSuccess { maxPulled = maxOf(maxPulled, it) }
                        .isSuccess
                } else {
                    false
                }
                if (!fbPulledOk) {
                    maxPulled = maxOf(
                        maxPulled,
                        pullDeltaRecordsFromBuiltInCloud(userId, channelTopic, 0L, forceFullHistory = true)
                    )
                } else {
                    scope.launch {
                        runCatchingCancellable {
                            pullDeltaRecordsFromBuiltInCloud(userId, channelTopic, 0L, forceFullHistory = true)
                        }
                    }
                }
                pushDirtyRecordsPrimaryFirst(userId, channelTopic)
                val nowMillis = System.currentTimeMillis()
                saveLastSyncTimestamp(userId, maxOf(clampRemoteTimestamp(maxPulled, nowMillis), nowMillis))
                _lastSyncedAt.value = nowMillis
                _syncStatus.value = CloudSyncStatus.SYNCED
                startRealtimeMultiDeviceSync(userId, channelTopic)
            } else {
                _syncStatus.value = CloudSyncStatus.OFFLINE
            }
            email
        }.onFailure { err ->
            Log.w("SyncRepository", "signInAccount failed", err)
            _syncStatus.value = CloudSyncStatus.OFFLINE
        }
    }

    suspend fun sendPasswordResetEmail(rawEmail: String): Result<String> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val email = rawEmail.trim().lowercase(Locale.ROOT)
            require(email.contains("@") && email.contains(".")) {
                localizedResetEmailMissingError()
            }
            if (isLiveFirebaseConfigured()) {
                firebaseAuth.sendPasswordResetEmail(email).await()
            }
            localizedPasswordResetSentInfo(email)
        }
    }

    suspend fun signOutAccount() {
        detachRealtimeDeltaListeners()
        lastNtfyMessageIdByTopic.clear()
        runCatching { firebaseAuth.signOut() }
        runCatchingCancellable {
            context.syncDataStore.edit { prefs ->
                prefs[KEY_ACTIVE_CHANNEL_TOPIC]?.let { topic ->
                    prefs.remove(lastNtfyMsgIdKeyForTopic(topic))
                }
                prefs.remove(KEY_ACTIVE_USER_ID)
                prefs.remove(KEY_ACTIVE_USER_EMAIL)
                prefs.remove(KEY_ACTIVE_CHANNEL_TOPIC)
            }
        }
        _activeUserIdentifier.value = null
        _syncStatus.value = CloudSyncStatus.OFFLINE
    }

    private fun lastSyncKeyForUser(userId: String) =
        longPreferencesKey("last_sync_timestamp_$userId")

    private fun attachmentsBackfillKeyForUser(userId: String) =
        booleanPreferencesKey("attachments_backfill_v1_$userId")

    private fun projectTemplatesBackfillKeyForUser(userId: String) =
        booleanPreferencesKey("project_templates_backfill_v1_$userId")

    private fun projectsReviewBackfillKeyForUser(userId: String) =
        booleanPreferencesKey("projects_review_backfill_v1_$userId")

    private suspend fun applyRemoteProjectDto(dto: ProjectFirestoreDto) {
        val local = database.projectDao().getById(dto.id)
        val mergedLastReviewed = if (dto.isDeleted) {
            dto.lastReviewedAt
        } else {
            mergeMonotonicReviewTimestamp(local?.lastReviewedAt, dto.lastReviewedAt)
        }
        if (local == null || dto.updatedAt >= local.updatedAt) {
            database.projectDao().upsertProjectWithTags(
                dto.toEntity().copy(lastReviewedAt = mergedLastReviewed),
                dto.tagIds
            )
        } else if (!dto.isDeleted && !local.isDeleted &&
            mergedLastReviewed != null && mergedLastReviewed > (local.lastReviewedAt ?: 0L)
        ) {
            database.projectDao().upsert(
                local.copy(lastReviewedAt = mergedLastReviewed)
            )
        }
    }

    suspend fun deduplicateOpenRecurringTasks(): Int = dbApplyMutex.withLock {
        deduplicateOpenRecurringTasksLocked()
    }

    private suspend fun deduplicateOpenRecurringTasksLocked(
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val openRecurring = database.taskDao().getOpenRecurringTasks()
        val loserIds = selectRecurringDuplicateLoserIds(openRecurring)
        if (loserIds.isEmpty()) return 0

        val loserSet = loserIds.toSet()
        val losers = openRecurring.filter { it.id in loserSet }
        if (losers.isEmpty()) return 0

        database.withTransaction {
            for (loser in losers) {
                val tombstoneAt = maxOf(nowMillis, loser.updatedAt + 1L)
                database.checklistDao().softDeleteByTaskId(loser.id, tombstoneAt)
                database.taskDao().upsertTaskWithTags(
                    loser.copy(
                        title = "",
                        notes = "",
                        recurrenceRuleJson = null,
                        tagIdsJson = "[]",
                        scheduledStartMinutes = null,
                        durationMinutes = null,
                        isDeleted = true,
                        isDirty = true,
                        updatedAt = tombstoneAt
                    ),
                    emptyList()
                )
            }
        }
        for (loser in losers) {
            reminderScheduler.cancelReminder(loser.id)
        }
        return losers.size
    }

    private fun lastNtfyMsgIdKeyForTopic(channelTopic: String) =
        stringPreferencesKey("last_ntfy_msg_id_$channelTopic")

    suspend fun getLastSyncTimestamp(userId: String): Long {
        return context.syncDataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[lastSyncKeyForUser(userId)] ?: 0L }
            .first()
    }

    private suspend fun saveLastSyncTimestamp(userId: String, timestamp: Long) {
        val clamped = clampRemoteTimestamp(timestamp)
        runCatchingCancellable {
            context.syncDataStore.edit { prefs ->
                prefs[lastSyncKeyForUser(userId)] = clamped
            }
        }
        _lastSyncedAt.value = clamped
    }

    private suspend fun getLastNtfyMessageId(channelTopic: String): String? {
        lastNtfyMessageIdByTopic[channelTopic]?.takeIf { it.isNotBlank() }?.let { return it }
        val stored = context.syncDataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[lastNtfyMsgIdKeyForTopic(channelTopic)] }
            .first()
        if (!stored.isNullOrBlank()) {
            lastNtfyMessageIdByTopic[channelTopic] = stored
        }
        return stored
    }

    private suspend fun saveLastNtfyMessageId(channelTopic: String, msgId: String) {
        if (msgId.isBlank()) return
        lastNtfyMessageIdByTopic[channelTopic] = msgId
        runCatchingCancellable {
            context.syncDataStore.edit { prefs ->
                prefs[lastNtfyMsgIdKeyForTopic(channelTopic)] = msgId
            }
        }
    }

    suspend fun syncBidirectionalDeltas(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isLocalOnlyDbModeEnabled()) {
            _syncStatus.value = CloudSyncStatus.OFFLINE
            return@withContext Result.success(Unit)
        }
        syncMutex.withLock {
            runCatchingCancellable {
                val prefs = context.syncDataStore.data
                    .catch { emit(emptyPreferences()) }
                    .first()
                dataStoreLocalOnlyFlag = prefs[SYNC_KEY_LOCAL_ONLY_DB_MODE] == true
                if (isLocalOnlyDbModeEnabled()) {
                    _syncStatus.value = CloudSyncStatus.OFFLINE
                    return@runCatchingCancellable
                }
                val userId = prefs[KEY_ACTIVE_USER_ID]
                    ?: runCatching { firebaseAuth.currentUser?.uid }.getOrNull()
                    ?: return@runCatchingCancellable
                val channelTopic = prefs[KEY_ACTIVE_CHANNEL_TOPIC] ?: deriveOAuthChannelTopic(userId)

                _syncStatus.value = CloudSyncStatus.SYNCING

                val liveFb = isLiveFirebaseConfigured()
                var anyTransportSucceeded = false

                if (pushDirtyRecordsPrimaryFirst(userId, channelTopic)) {
                    anyTransportSucceeded = true
                }

                val lastSync = getLastSyncTimestamp(userId)
                var maxObservedTimestamp = lastSync
                var fbPullSucceeded = false
                if (liveFb) {
                    runCatchingCancellable { pullDeltaRecordsFromFirestore(userId, lastSync) }
                        .onSuccess { fbMax ->
                            fbPullSucceeded = true
                            anyTransportSucceeded = true
                            val clampedFbMax = clampRemoteTimestamp(fbMax)
                            if (clampedFbMax > maxObservedTimestamp) maxObservedTimestamp = clampedFbMax
                        }
                }

                if (!fbPullSucceeded) {
                    val sinceMsgId = if (lastSync <= 0L) null else getLastNtfyMessageId(channelTopic)
                    fetchDeltaBatchesResultFromBuiltInCloud(channelTopic, sinceMessageId = sinceMsgId).onSuccess { pollResult ->
                        anyTransportSucceeded = true
                        pollResult.latestMessageId?.let { saveLastNtfyMessageId(channelTopic, it) }
                        val localDeviceId = getOrCreateDeviceId()
                        for (batch in pollResult.batches) {
                            if (batch.userId != userId) continue
                            if (batch.deviceId == localDeviceId && lastSync > 0L && batch.batchTimestamp <= lastSync) {
                                continue
                            }
                            if (batch.wakeOnly) continue
                            val batchMax = applyDeltaBatchToRoom(batch, maxObservedTimestamp)
                            if (batchMax > maxObservedTimestamp) maxObservedTimestamp = batchMax
                        }
                    }
                } else {
                    scope.launch {
                        runCatchingCancellable {
                            pullDeltaRecordsFromBuiltInCloud(userId, channelTopic, lastSync, forceFullHistory = false)
                        }
                    }
                }

                if (!anyTransportSucceeded) {
                    throw IOException(localizedCloudOfflineError())
                }

                val nowMillis = System.currentTimeMillis()
                if (maxObservedTimestamp > lastSync) {
                    saveLastSyncTimestamp(userId, clampRemoteTimestamp(maxObservedTimestamp, nowMillis))
                } else if (lastSync == 0L) {
                    saveLastSyncTimestamp(userId, nowMillis)
                }
                _lastSyncedAt.value = nowMillis

                _syncStatus.value = CloudSyncStatus.SYNCED
            }.onFailure { err ->
                Log.w("SyncRepository", "syncBidirectionalDeltas failed", err)
                _syncStatus.value = CloudSyncStatus.OFFLINE
            }
        }
    }

    private fun isLiveFirebaseConfigured(): Boolean {
        return runCatching {
            val apiKey = firestore.app.options.apiKey
            !apiKey.isNullOrBlank() &&
                !apiKey.contains("DummyKey") &&
                !apiKey.contains("OfflinePlaceholderKey")
        }.getOrDefault(false)
    }

    private fun buildNtfyInlineOrWakePayload(fullPayload: CloudDeltaBatchPayload): String? {
        val fullJson = json.encodeToString(fullPayload)
        if (fullJson.toByteArray(StandardCharsets.UTF_8).size < MAX_NTFY_CHUNK_BYTES) {
            return fullJson
        }
        val wakeJson = json.encodeToString(
            CloudDeltaBatchPayload(
                userId = fullPayload.userId,
                deviceId = fullPayload.deviceId,
                batchTimestamp = fullPayload.batchTimestamp,
                wakeOnly = true
            )
        )
        return wakeJson.takeIf { it.toByteArray(StandardCharsets.UTF_8).size < MAX_NTFY_CHUNK_BYTES }
    }

    private suspend fun pushDirtyRecordsPrimaryFirst(
        userId: String,
        channelTopic: String
    ): Boolean {
        if (isLocalOnlyDbModeEnabled()) return false
        val dirtyAreas = database.areaDao().getDirtyAreas()
        val dirtyProjects = database.projectDao().getDirtyProjects()
        val dirtyHeadings = database.headingDao().getDirtyHeadings()
        val dirtyTasks = database.taskDao().getDirtyTasks()
        val dirtyChecklists = database.checklistDao().getDirtyChecklistItems()
        val dirtyTags = database.tagDao().getDirtyTags()
        val dirtyTemplates = database.projectTemplateDao().getDirtyTemplates()
        val dirtyAttachments = database.attachmentDao().getDirtyAttachments()

        val hasNonAttEntities = dirtyAreas.isNotEmpty() ||
            dirtyProjects.isNotEmpty() ||
            dirtyHeadings.isNotEmpty() ||
            dirtyTasks.isNotEmpty() ||
            dirtyChecklists.isNotEmpty() ||
            dirtyTags.isNotEmpty() ||
            dirtyTemplates.isNotEmpty()

        if (!hasNonAttEntities && dirtyAttachments.isEmpty()) {
            return true
        }

        val deviceId = getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val uidWakeTopic = deriveUidWakeChannelTopic(userId)

        val compactAttachments = dirtyAttachments.map { att ->
            att.toFirestoreDto(dataBase64 = "")
        }
        val ntfySnapshotPayload = CloudDeltaBatchPayload(
            userId = userId,
            deviceId = deviceId,
            batchTimestamp = now,
            tags = dirtyTags.map { TagFirestoreDto.fromEntity(it) },
            areas = dirtyAreas.map { AreaFirestoreDto.fromEntity(it) },
            projects = dirtyProjects.map { ProjectFirestoreDto.fromEntity(it) },
            headings = dirtyHeadings.map { HeadingFirestoreDto.fromEntity(it) },
            tasks = dirtyTasks.map { TaskFirestoreDto.fromEntity(it) },
            checklistItems = dirtyChecklists.map { ChecklistItemFirestoreDto.fromEntity(it) },
            attachments = compactAttachments,
            projectTemplates = dirtyTemplates.map { ProjectTemplateFirestoreDto.fromEntity(it) }
        )

        if (isLiveFirebaseConfigured()) {
            val fbOk = runCatchingCancellable {
                pushDirtyRecordsToFirestore(userId, markAsSynced = true)
            }.isSuccess
            if (fbOk) {
                scope.launch {
                    val wakeBody = buildNtfyInlineOrWakePayload(ntfySnapshotPayload) ?: return@launch
                    val targets = buildSet {
                        if (uidWakeTopic.isNotBlank()) add(uidWakeTopic)
                        if (channelTopic.isNotBlank()) add(channelTopic)
                    }
                    for (topic in targets) {
                        runCatching { postJsonToBuiltInCloud(topic, wakeBody) }
                    }
                }
                return true
            }
        }

        val fallbackOk = pushDirtyRecordsToBuiltInCloud(userId, channelTopic, markAsSynced = true)
        if (fallbackOk && uidWakeTopic.isNotBlank() && uidWakeTopic != channelTopic) {
            scope.launch {
                buildNtfyInlineOrWakePayload(ntfySnapshotPayload)?.let { postJsonToBuiltInCloud(uidWakeTopic, it) }
            }
        }
        return fallbackOk
    }

    private fun buildSafeNtfyPayloadChunks(
        userId: String,
        deviceId: String,
        batchTimestamp: Long,
        fullPayload: CloudDeltaBatchPayload
    ): List<String> {
        val fullJson = json.encodeToString(fullPayload)
        if (fullJson.toByteArray(StandardCharsets.UTF_8).size < MAX_NTFY_CHUNK_BYTES) {
            return listOf(fullJson)
        }

        val out = mutableListOf<String>()
        fun <T> appendSplitChunks(items: List<T>, makePayload: (List<T>) -> CloudDeltaBatchPayload) {
            if (items.isEmpty()) return
            val encoded = json.encodeToString(makePayload(items))
            if (encoded.toByteArray(StandardCharsets.UTF_8).size < MAX_NTFY_CHUNK_BYTES) {
                out += encoded
            } else if (items.size > 1) {
                val mid = items.size / 2
                appendSplitChunks(items.subList(0, mid), makePayload)
                appendSplitChunks(items.subList(mid, items.size), makePayload)
            } else {
                Log.w("SyncRepository", "Skipping oversized single ntfy relay item (>= $MAX_NTFY_CHUNK_BYTES bytes)")
            }
        }

        appendSplitChunks(fullPayload.tags) {
            CloudDeltaBatchPayload(userId = userId, deviceId = deviceId, batchTimestamp = batchTimestamp, tags = it)
        }
        appendSplitChunks(fullPayload.areas) {
            CloudDeltaBatchPayload(userId = userId, deviceId = deviceId, batchTimestamp = batchTimestamp, areas = it)
        }
        appendSplitChunks(fullPayload.projects) {
            CloudDeltaBatchPayload(userId = userId, deviceId = deviceId, batchTimestamp = batchTimestamp, projects = it)
        }
        appendSplitChunks(fullPayload.headings) {
            CloudDeltaBatchPayload(userId = userId, deviceId = deviceId, batchTimestamp = batchTimestamp, headings = it)
        }
        appendSplitChunks(fullPayload.tasks) {
            CloudDeltaBatchPayload(userId = userId, deviceId = deviceId, batchTimestamp = batchTimestamp, tasks = it)
        }
        appendSplitChunks(fullPayload.checklistItems) {
            CloudDeltaBatchPayload(userId = userId, deviceId = deviceId, batchTimestamp = batchTimestamp, checklistItems = it)
        }
        appendSplitChunks(fullPayload.projectTemplates) {
            CloudDeltaBatchPayload(userId = userId, deviceId = deviceId, batchTimestamp = batchTimestamp, projectTemplates = it)
        }
        return out
    }

    private suspend fun pushDirtyRecordsToBuiltInCloud(
        userId: String,
        channelTopic: String,
        markAsSynced: Boolean = true
    ): Boolean {
        if (isLocalOnlyDbModeEnabled()) return false

        val dirtyAreas = database.areaDao().getDirtyAreas()
        val dirtyProjects = database.projectDao().getDirtyProjects()
        val dirtyHeadings = database.headingDao().getDirtyHeadings()
        val dirtyTasks = database.taskDao().getDirtyTasks()
        val dirtyChecklists = database.checklistDao().getDirtyChecklistItems()
        val dirtyTags = database.tagDao().getDirtyTags()
        val dirtyTemplates = database.projectTemplateDao().getDirtyTemplates()

        if (
            dirtyAreas.isEmpty() &&
            dirtyProjects.isEmpty() &&
            dirtyHeadings.isEmpty() &&
            dirtyTasks.isEmpty() &&
            dirtyChecklists.isEmpty() &&
            dirtyTags.isEmpty() &&
            dirtyTemplates.isEmpty()
        ) {
            return true
        }

        val deviceId = getOrCreateDeviceId()
        val now = System.currentTimeMillis()

        val fullPayload = CloudDeltaBatchPayload(
            userId = userId,
            deviceId = deviceId,
            batchTimestamp = now,
            tags = dirtyTags.map { TagFirestoreDto.fromEntity(it) },
            areas = dirtyAreas.map { AreaFirestoreDto.fromEntity(it) },
            projects = dirtyProjects.map { ProjectFirestoreDto.fromEntity(it) },
            headings = dirtyHeadings.map { HeadingFirestoreDto.fromEntity(it) },
            tasks = dirtyTasks.map { TaskFirestoreDto.fromEntity(it) },
            checklistItems = dirtyChecklists.map { ChecklistItemFirestoreDto.fromEntity(it) },
            projectTemplates = dirtyTemplates.map { ProjectTemplateFirestoreDto.fromEntity(it) }
        )

        val payloadsToPost = buildSafeNtfyPayloadChunks(userId, deviceId, now, fullPayload)

        var allSucceeded = true
        for (body in payloadsToPost) {
            if (!postJsonToBuiltInCloud(channelTopic, body)) {
                allSucceeded = false
                break
            }
        }

        if (allSucceeded && markAsSynced) {
            if (dirtyAreas.isNotEmpty()) {
                database.areaDao().markSyncedSafe(dirtyAreas.map { it.id }, dirtyAreas.maxOf { it.updatedAt })
            }
            if (dirtyProjects.isNotEmpty()) {
                database.projectDao().markSyncedSafe(dirtyProjects.map { it.id }, dirtyProjects.maxOf { it.updatedAt })
            }
            if (dirtyHeadings.isNotEmpty()) {
                database.headingDao().markSyncedSafe(dirtyHeadings.map { it.id }, dirtyHeadings.maxOf { it.updatedAt })
            }
            if (dirtyTasks.isNotEmpty()) {
                database.taskDao().markSyncedSafe(dirtyTasks.map { it.id }, dirtyTasks.maxOf { it.updatedAt })
            }
            if (dirtyChecklists.isNotEmpty()) {
                database.checklistDao().markSyncedSafe(dirtyChecklists.map { it.id }, dirtyChecklists.maxOf { it.updatedAt })
            }
            if (dirtyTags.isNotEmpty()) {
                database.tagDao().markSyncedSafe(dirtyTags.map { it.id }, dirtyTags.maxOf { it.updatedAt })
            }
            if (dirtyTemplates.isNotEmpty()) {
                database.projectTemplateDao().markSyncedSafe(dirtyTemplates.map { it.id }, dirtyTemplates.maxOf { it.updatedAt })
            }
        }
        return allSucceeded
    }

    private suspend fun pullDeltaRecordsFromBuiltInCloud(
        userId: String,
        channelTopic: String,
        lastSyncTimestamp: Long,
        forceFullHistory: Boolean = false
    ): Long {
        if (isLocalOnlyDbModeEnabled()) return lastSyncTimestamp
        var maxTimestamp = lastSyncTimestamp
        val localDeviceId = getOrCreateDeviceId()

        val sinceMsgId = if (forceFullHistory || lastSyncTimestamp <= 0L) null else getLastNtfyMessageId(channelTopic)
        val pollResult = fetchDeltaBatchesResultFromBuiltInCloud(channelTopic, sinceMessageId = sinceMsgId)
            .getOrNull() ?: return maxTimestamp
        pollResult.latestMessageId?.let { saveLastNtfyMessageId(channelTopic, it) }

        for (batch in pollResult.batches) {
            if (batch.userId != userId) continue
            if (batch.deviceId == localDeviceId && lastSyncTimestamp > 0L && batch.batchTimestamp <= lastSyncTimestamp) {
                continue
            }
            if (batch.wakeOnly) continue
            val batchMax = applyDeltaBatchToRoom(batch, maxTimestamp)
            if (batchMax > maxTimestamp) maxTimestamp = batchMax
        }
        return maxTimestamp
    }

    private suspend fun applyRemoteAttachmentDto(dto: AttachmentFirestoreDto) {
        if (dto.id.isBlank()) return
        val localExisting = database.attachmentDao().getById(dto.id)
        if (localExisting == null || dto.updatedAt >= localExisting.updatedAt) {
            val generatedLocalPath = buildSafeLocalAttachmentPath(dto.id, dto.fileName)
            if (dto.isDeleted) {
                localExisting?.localRelativePath?.takeIf { it.isNotBlank() }?.let { deleteAttachmentFile(it) }
                deleteAttachmentFile(generatedLocalPath)
                database.attachmentDao().upsert(dto.toEntity(generatedLocalPath))
            } else {
                if (localExisting != null &&
                    localExisting.localRelativePath.isNotBlank() &&
                    localExisting.localRelativePath != generatedLocalPath &&
                    dto.dataBase64.isNotBlank()
                ) {
                    deleteAttachmentFile(localExisting.localRelativePath)
                }
                val targetRelPath = if (dto.dataBase64.isNotBlank()) {
                    writeAttachmentBase64(generatedLocalPath, dto.dataBase64)
                    generatedLocalPath
                } else {
                    localExisting?.localRelativePath
                        ?.takeIf { path ->
                            path.isNotBlank() && runCatching { resolveAttachmentFile(path).isFile }.getOrDefault(false)
                        }
                        ?: generatedLocalPath
                }
                database.attachmentDao().upsert(dto.toEntity(targetRelPath))
            }
        }
    }

    private suspend fun applyDeltaBatchToRoom(
        batch: CloudDeltaBatchPayload,
        currentMaxTimestamp: Long
    ): Long = dbApplyMutex.withLock {
        val nowMillis = System.currentTimeMillis()
        var maxTimestamp = currentMaxTimestamp
        val updatedTasks = mutableListOf<TaskEntity>()

        database.withTransaction {
            for (dto in batch.tags) {
                if (dto.id.isBlank()) continue
                val local = database.tagDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.tagDao().upsert(dto.toEntity())
                }
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.areas) {
                if (dto.id.isBlank()) continue
                val local = database.areaDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.areaDao().upsert(dto.toEntity())
                }
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.projects) {
                if (dto.id.isBlank()) continue
                applyRemoteProjectDto(dto)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.headings) {
                if (dto.id.isBlank() || dto.projectId.isBlank()) continue
                val local = database.headingDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.headingDao().upsert(dto.toEntity())
                }
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.tasks) {
                if (dto.id.isBlank()) continue
                val local = database.taskDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    val entity = dto.toEntity()
                    database.taskDao().upsertTaskWithTags(entity, dto.tagIds)
                    updatedTasks += entity
                }
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.checklistItems) {
                if (dto.id.isBlank() || dto.taskId.isBlank()) continue
                val local = database.checklistDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.checklistDao().upsert(dto.toEntity())
                }
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.projectTemplates) {
                if (dto.id.isBlank()) continue
                val local = database.projectTemplateDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.projectTemplateDao().upsert(dto.toEntity())
                }
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.attachments) {
                if (dto.id.isBlank()) continue
                applyRemoteAttachmentDto(dto)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }
        }

        updatedTasks.forEach { syncTaskReminderSchedule(it) }
        if (batch.tasks.isNotEmpty()) {
            val deduped = deduplicateOpenRecurringTasksLocked(nowMillis)
            if (deduped > 0) {
                triggerBackgroundSync()
            }
        }

        val clampedBatchTs = clampRemoteTimestamp(batch.batchTimestamp, nowMillis)
        if (clampedBatchTs > maxTimestamp) {
            maxTimestamp = clampedBatchTs
        }
        maxTimestamp
    }

    private fun postJsonToBuiltInCloud(topic: String, bodyJson: String): Boolean {
        if (bodyJson.toByteArray(StandardCharsets.UTF_8).size >= MAX_NTFY_CHUNK_BYTES) {
            return false
        }
        return runCatching {
            val url = URL("https://ntfy.sh/$topic")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2500
                readTimeout = 2500
                doOutput = true
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                setRequestProperty("Cache", "yes")
            }
            try {
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(bodyJson)
                    writer.flush()
                }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    private fun fetchCloudUserEnvelope(registryTopic: String): CloudUserAuthEnvelope? {
        return runCatching {
            val url = URL("https://ntfy.sh/$registryTopic/json?poll=1&since=all")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
            }
            try {
                var latestEnvelope: CloudUserAuthEnvelope? = null
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val raw = line?.trim().orEmpty()
                        if (raw.isEmpty()) continue
                        val streamLine = runCatching { json.decodeFromString<NtfyStreamLine>(raw) }.getOrNull()
                        if (streamLine != null && streamLine.event == "message" && streamLine.message.isNotBlank()) {
                            val env = runCatching {
                                json.decodeFromString<CloudUserAuthEnvelope>(streamLine.message)
                            }.getOrNull()
                            if (env != null && env.userId.isNotBlank()) {
                                latestEnvelope = env
                            }
                        }
                    }
                }
                latestEnvelope
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private data class NtfyPollResult(
        val batches: List<CloudDeltaBatchPayload>,
        val latestMessageId: String?
    )

    private fun fetchDeltaBatchesResultFromBuiltInCloud(
        channelTopic: String,
        sinceMessageId: String? = null
    ): Result<NtfyPollResult> {
        return runCatching {
            val sinceParam = sinceMessageId?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.name()) }
                ?: "all"
            val url = URL("https://ntfy.sh/$channelTopic/json?poll=1&since=$sinceParam")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
            }
            try {
                val result = mutableListOf<CloudDeltaBatchPayload>()
                var latestId: String? = null
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val raw = line?.trim().orEmpty()
                        if (raw.isEmpty()) continue
                        val streamLine = runCatching { json.decodeFromString<NtfyStreamLine>(raw) }.getOrNull()
                        if (streamLine != null && streamLine.event == "message") {
                            if (streamLine.id.isNotBlank()) {
                                latestId = streamLine.id
                            }
                            if (streamLine.message.isNotBlank()) {
                                val batch = runCatching {
                                    json.decodeFromString<CloudDeltaBatchPayload>(streamLine.message)
                                }.getOrNull()
                                if (batch != null && batch.userId.isNotBlank()) {
                                    result += batch
                                }
                            }
                        }
                    }
                }
                NtfyPollResult(batches = result, latestMessageId = latestId)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun startRealtimeMultiDeviceSync(userId: String, channelTopic: String) {
        if (isLocalOnlyDbModeEnabled()) return
        detachRealtimeDeltaListeners()

        val pollIntervalMs = if (isLiveFirebaseConfigured()) 45_000L else 15_000L
        realtimeCloudPollJob = scope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                if (isLocalOnlyDbModeEnabled()) break
                syncBidirectionalDeltas()
            }
        }

        startLiveNtfyWakeStream(userId, deriveUidWakeChannelTopic(userId))

        if (isLiveFirebaseConfigured()) {
            scope.launch {
                runCatchingCancellable { attachRealtimeDeltaListeners(userId) }
            }
        }
    }

    private fun startLiveNtfyWakeStream(userId: String, uidWakeTopic: String) {
        if (uidWakeTopic.isBlank() || isLocalOnlyDbModeEnabled()) return
        liveNtfyStreamJob?.cancel()
        liveNtfyStreamJob = scope.launch {
            while (isActive && !isLocalOnlyDbModeEnabled()) {
                runCatching {
                    val localDeviceId = getOrCreateDeviceId()
                    val url = URL("https://ntfy.sh/$uidWakeTopic/json?since=none")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 75_000
                    }
                    try {
                        BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                            while (isActive && !isLocalOnlyDbModeEnabled()) {
                                val raw = (reader.readLine() ?: break).trim()
                                if (raw.isEmpty()) continue
                                val streamLine = runCatching { json.decodeFromString<NtfyStreamLine>(raw) }.getOrNull() ?: continue
                                if (streamLine.event != "message" || streamLine.message.isBlank()) continue
                                val batch = runCatching {
                                    json.decodeFromString<CloudDeltaBatchPayload>(streamLine.message)
                                }.getOrNull() ?: continue
                                if (batch.userId != userId || batch.deviceId == localDeviceId) continue

                                if (batch.wakeOnly) {
                                    scheduleIncrementalFirestorePull(userId, 5L)
                                } else {
                                    val lastSync = getLastSyncTimestamp(userId)
                                    val batchMax = applyDeltaBatchToRoom(batch, lastSync)
                                    if (batchMax > lastSync) {
                                        saveLastSyncTimestamp(userId, batchMax)
                                    }
                                    if (batch.attachments.isNotEmpty() && isLiveFirebaseConfigured()) {
                                        scheduleIncrementalFirestorePull(userId, 20L)
                                    }
                                }
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
                if (!isActive || isLocalOnlyDbModeEnabled()) break
                delay(2500L)
            }
        }
    }

    suspend fun pushDirtyRecordsToFirestore(userId: String, markAsSynced: Boolean = true) {
        if (isLocalOnlyDbModeEnabled()) return
        val userDoc = firestore.collection("users").document(userId)

        val dirtyAreas = database.areaDao().getDirtyAreas()
        val dirtyProjects = database.projectDao().getDirtyProjects()
        val dirtyHeadings = database.headingDao().getDirtyHeadings()
        val dirtyTasks = database.taskDao().getDirtyTasks()
        val dirtyChecklists = database.checklistDao().getDirtyChecklistItems()
        val dirtyTags = database.tagDao().getDirtyTags()
        val dirtyTemplates = database.projectTemplateDao().getDirtyTemplates()
        val dirtyAttachments = database.attachmentDao().getDirtyAttachments()

        data class BatchOp(val collectionName: String, val docId: String, val dataMap: Map<String, Any?>)

        val allOps = buildList {
            dirtyAreas.forEach { add(BatchOp("areas", it.id, AreaFirestoreDto.fromEntity(it).toFirestoreMap())) }
            dirtyProjects.forEach { add(BatchOp("projects", it.id, ProjectFirestoreDto.fromEntity(it).toFirestoreMap())) }
            dirtyHeadings.forEach { add(BatchOp("headings", it.id, HeadingFirestoreDto.fromEntity(it).toFirestoreMap())) }
            dirtyTasks.forEach { add(BatchOp("tasks", it.id, TaskFirestoreDto.fromEntity(it).toFirestoreMap())) }
            dirtyChecklists.forEach { add(BatchOp("checklist_items", it.id, ChecklistItemFirestoreDto.fromEntity(it).toFirestoreMap())) }
            dirtyTags.forEach { add(BatchOp("tags", it.id, TagFirestoreDto.fromEntity(it).toFirestoreMap())) }
            dirtyTemplates.forEach { add(BatchOp("project_templates", it.id, ProjectTemplateFirestoreDto.fromEntity(it).toFirestoreMap())) }
        }

        if (allOps.isEmpty() && dirtyAttachments.isEmpty()) return

        val pulseNow = System.currentTimeMillis()
        val deviceId = getOrCreateDeviceId()
        val pulseRef = userDoc.collection("sync_meta").document("pulse")
        val pulseMap = mapOf<String, Any>(
            "pulseAt" to pulseNow,
            "senderDeviceId" to deviceId
        )

        val metaChunks = allOps.chunked(450)
        metaChunks.forEachIndexed { idx, chunk ->
            val batch = firestore.batch()
            chunk.forEach { op ->
                val ref = userDoc.collection(op.collectionName).document(op.docId)
                batch.set(ref, op.dataMap, SetOptions.merge())
            }
            if (idx == metaChunks.lastIndex && dirtyAttachments.isEmpty()) {
                batch.set(pulseRef, pulseMap, SetOptions.merge())
            }
            batch.commit().await()
        }

        // Sync attachments in batches of 8 with 700 KB inline cap and metadata-only merge when file exceeds cap/missing
        val attChunks = dirtyAttachments.chunked(8)
        attChunks.forEachIndexed { idx, chunk ->
            val batch = firestore.batch()
            chunk.forEach { att ->
                val ref = userDoc.collection("attachments").document(att.id)
                if (att.isDeleted) {
                    val dto = att.toFirestoreDto(dataBase64 = "")
                    batch.set(ref, dto.toFirestoreMap(), SetOptions.merge())
                } else {
                    val b64 = readAttachmentBase64(att.localRelativePath, maxRawBytes = MAX_INLINE_SYNC_BYTES)
                    val dto = att.toFirestoreDto(dataBase64 = b64)
                    val mapToWrite = if (b64.isNotEmpty()) {
                        dto.toFirestoreMap()
                    } else {
                        dto.toFirestoreMap().minus("dataBase64")
                    }
                    batch.set(ref, mapToWrite, SetOptions.merge())
                }
            }
            if (idx == attChunks.lastIndex) {
                batch.set(pulseRef, pulseMap, SetOptions.merge())
            }
            batch.commit().await()
        }

        if (markAsSynced) {
            if (dirtyAreas.isNotEmpty()) {
                database.areaDao().markSyncedSafe(dirtyAreas.map { it.id }, dirtyAreas.maxOf { it.updatedAt })
            }
            if (dirtyProjects.isNotEmpty()) {
                database.projectDao().markSyncedSafe(dirtyProjects.map { it.id }, dirtyProjects.maxOf { it.updatedAt })
            }
            if (dirtyHeadings.isNotEmpty()) {
                database.headingDao().markSyncedSafe(dirtyHeadings.map { it.id }, dirtyHeadings.maxOf { it.updatedAt })
            }
            if (dirtyTasks.isNotEmpty()) {
                database.taskDao().markSyncedSafe(dirtyTasks.map { it.id }, dirtyTasks.maxOf { it.updatedAt })
            }
            if (dirtyChecklists.isNotEmpty()) {
                database.checklistDao().markSyncedSafe(dirtyChecklists.map { it.id }, dirtyChecklists.maxOf { it.updatedAt })
            }
            if (dirtyTags.isNotEmpty()) {
                database.tagDao().markSyncedSafe(dirtyTags.map { it.id }, dirtyTags.maxOf { it.updatedAt })
            }
            if (dirtyTemplates.isNotEmpty()) {
                database.projectTemplateDao().markSyncedSafe(dirtyTemplates.map { it.id }, dirtyTemplates.maxOf { it.updatedAt })
            }
            if (dirtyAttachments.isNotEmpty()) {
                database.attachmentDao().markSyncedSafe(dirtyAttachments.map { it.id }, dirtyAttachments.maxOf { it.updatedAt })
            }
        }
    }

    suspend fun pullDeltaRecordsFromFirestore(userId: String, lastSyncTimestamp: Long): Long {
        if (isLocalOnlyDbModeEnabled()) return lastSyncTimestamp
        val userDoc = firestore.collection("users").document(userId)
        val queryFloor = computeDeltaQueryFloor(lastSyncTimestamp)
        var maxTimestamp = lastSyncTimestamp

        val prefs = context.syncDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()
        val needsAttachmentsBackfill = prefs[attachmentsBackfillKeyForUser(userId)] != true
        val needsTemplatesBackfill = prefs[projectTemplatesBackfillKeyForUser(userId)] != true
        val needsProjectsReviewBackfill = prefs[projectsReviewBackfillKeyForUser(userId)] != true

        data class ParallelSnapshots(
            val tags: com.google.firebase.firestore.QuerySnapshot,
            val areas: com.google.firebase.firestore.QuerySnapshot,
            val projects: com.google.firebase.firestore.QuerySnapshot,
            val headings: com.google.firebase.firestore.QuerySnapshot,
            val tasks: com.google.firebase.firestore.QuerySnapshot,
            val checklists: com.google.firebase.firestore.QuerySnapshot,
            val attachments: com.google.firebase.firestore.QuerySnapshot,
            val templates: com.google.firebase.firestore.QuerySnapshot?
        )

        val fetched = coroutineScope {
            val tagDef = async {
                userDoc.collection("tags")
                    .whereGreaterThan("updatedAt", queryFloor)
                    .get()
                    .await()
            }
            val areaDef = async {
                userDoc.collection("areas")
                    .whereGreaterThan("updatedAt", queryFloor)
                    .get()
                    .await()
            }
            val projectDef = async {
                userDoc.collection("projects")
                    .whereGreaterThan("updatedAt", if (needsProjectsReviewBackfill) 0L else queryFloor)
                    .get()
                    .await()
            }
            val headingDef = async {
                userDoc.collection("headings")
                    .whereGreaterThan("updatedAt", queryFloor)
                    .get()
                    .await()
            }
            val taskDef = async {
                userDoc.collection("tasks")
                    .whereGreaterThan("updatedAt", queryFloor)
                    .get()
                    .await()
            }
            val checklistDef = async {
                userDoc.collection("checklist_items")
                    .whereGreaterThan("updatedAt", queryFloor)
                    .get()
                    .await()
            }
            val attachmentDef = async {
                userDoc.collection("attachments")
                    .whereGreaterThan("updatedAt", if (needsAttachmentsBackfill) 0L else queryFloor)
                    .get()
                    .await()
            }
            val templateDef = async {
                runCatchingCancellable {
                    val floor = if (needsTemplatesBackfill) 0L else queryFloor
                    userDoc.collection("project_templates")
                        .whereGreaterThan("updatedAt", floor)
                        .get()
                        .await()
                }.getOrNull()
            }
            ParallelSnapshots(
                tags = tagDef.await(),
                areas = areaDef.await(),
                projects = projectDef.await(),
                headings = headingDef.await(),
                tasks = taskDef.await(),
                checklists = checklistDef.await(),
                attachments = attachmentDef.await(),
                templates = templateDef.await()
            )
        }
        val tagSnapshots = fetched.tags
        val areaSnapshots = fetched.areas
        val projectSnapshots = fetched.projects
        val headingSnapshots = fetched.headings
        val taskSnapshots = fetched.tasks
        val checklistSnapshots = fetched.checklists
        val attachmentSnapshots = fetched.attachments
        val templateSnapshots = fetched.templates

        val nowMillis = System.currentTimeMillis()
        dbApplyMutex.withLock {
            for (doc in tagSnapshots.documents) {
                val dto = runCatching { TagFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
                if (dto.id.isBlank()) continue
                val local = database.tagDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.tagDao().upsert(dto.toEntity())
                }
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (doc in areaSnapshots.documents) {
                val dto = runCatching { AreaFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
                if (dto.id.isBlank()) continue
                val local = database.areaDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.areaDao().upsert(dto.toEntity())
                }
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (doc in projectSnapshots.documents) {
                val dto = runCatching { ProjectFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
                if (dto.id.isBlank()) continue
                applyRemoteProjectDto(dto)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (doc in headingSnapshots.documents) {
                val dto = runCatching { HeadingFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
                if (dto.id.isBlank() || dto.projectId.isBlank()) continue
                val local = database.headingDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.headingDao().upsert(dto.toEntity())
                }
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (doc in taskSnapshots.documents) {
                val dto = runCatching { TaskFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
                if (dto.id.isBlank()) continue
                val local = database.taskDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    val entity = dto.toEntity()
                    database.taskDao().upsertTaskWithTags(entity, dto.tagIds)
                    syncTaskReminderSchedule(entity)
                }
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (doc in checklistSnapshots.documents) {
                val dto = runCatching { ChecklistItemFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
                if (dto.id.isBlank() || dto.taskId.isBlank()) continue
                val local = database.checklistDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.checklistDao().upsert(dto.toEntity())
                }
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            if (templateSnapshots != null) {
                for (doc in templateSnapshots.documents) {
                    val dto = runCatching { ProjectTemplateFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
                    if (dto.id.isBlank()) continue
                    val local = database.projectTemplateDao().getById(dto.id)
                    if (local == null || dto.updatedAt >= local.updatedAt) {
                        database.projectTemplateDao().upsert(dto.toEntity())
                    }
                    val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                    if (clamped > maxTimestamp) maxTimestamp = clamped
                }
            }

            for (doc in attachmentSnapshots.documents) {
                val dto = runCatching { AttachmentFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
                if (dto.id.isBlank()) continue
                applyRemoteAttachmentDto(dto)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            if (!taskSnapshots.isEmpty) {
                val deduped = deduplicateOpenRecurringTasksLocked(nowMillis)
                if (deduped > 0) {
                    triggerBackgroundSync()
                }
            }
        }

        if (needsAttachmentsBackfill ||
            needsProjectsReviewBackfill ||
            (needsTemplatesBackfill && templateSnapshots != null)
        ) {
            runCatchingCancellable {
                context.syncDataStore.edit { editPrefs ->
                    if (needsAttachmentsBackfill) {
                        editPrefs[attachmentsBackfillKeyForUser(userId)] = true
                    }
                    if (needsProjectsReviewBackfill) {
                        editPrefs[projectsReviewBackfillKeyForUser(userId)] = true
                    }
                    if (needsTemplatesBackfill && templateSnapshots != null) {
                        editPrefs[projectTemplatesBackfillKeyForUser(userId)] = true
                    }
                }
            }
        }

        return maxTimestamp
    }

    suspend fun attachRealtimeDeltaListeners(userId: String) {
        if (isLocalOnlyDbModeEnabled()) return
        runCatchingCancellable {
            synchronized(activeListeners) {
                activeListeners.forEach { runCatching { it.remove() } }
                activeListeners.clear()
            }

            val sinceTimestamp = getLastSyncTimestamp(userId)
            val queryFloor = computeDeltaQueryFloor(sinceTimestamp)
            val userDoc = firestore.collection("users").document(userId)

            activeListeners += userDoc.collection("tasks")
                .whereGreaterThan("updatedAt", queryFloor)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                var maxTs = getLastSyncTimestamp(userId)
                                var changed = false
                                var appliedAnyTask = false
                                for (change in snapshot.documentChanges) {
                                    runCatchingCancellable {
                                        val dto = TaskFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank()) {
                                            val local = database.taskDao().getById(dto.id)
                                            if (local == null || dto.updatedAt >= local.updatedAt) {
                                                val entity = dto.toEntity()
                                                database.taskDao().upsertTaskWithTags(entity, dto.tagIds)
                                                syncTaskReminderSchedule(entity)
                                                appliedAnyTask = true
                                            }
                                            val clamped = clampRemoteTimestamp(dto.updatedAt)
                                            if (clamped > maxTs) {
                                                maxTs = clamped
                                                changed = true
                                            }
                                        }
                                    }
                                }
                                if (appliedAnyTask) {
                                    val deduped = deduplicateOpenRecurringTasksLocked()
                                    if (deduped > 0) {
                                        triggerBackgroundSync()
                                    }
                                }
                                if (changed) saveLastSyncTimestamp(userId, maxTs)
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("projects")
                .whereGreaterThan("updatedAt", queryFloor)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                var maxTs = getLastSyncTimestamp(userId)
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    runCatchingCancellable {
                                        val dto = ProjectFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank()) {
                                            applyRemoteProjectDto(dto)
                                            val clamped = clampRemoteTimestamp(dto.updatedAt)
                                            if (clamped > maxTs) {
                                                maxTs = clamped
                                                changed = true
                                            }
                                        }
                                    }
                                }
                                if (changed) saveLastSyncTimestamp(userId, maxTs)
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("areas")
                .whereGreaterThan("updatedAt", queryFloor)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                var maxTs = getLastSyncTimestamp(userId)
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    runCatchingCancellable {
                                        val dto = AreaFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank()) {
                                            val local = database.areaDao().getById(dto.id)
                                            if (local == null || dto.updatedAt >= local.updatedAt) {
                                                database.areaDao().upsert(dto.toEntity())
                                            }
                                            val clamped = clampRemoteTimestamp(dto.updatedAt)
                                            if (clamped > maxTs) {
                                                maxTs = clamped
                                                changed = true
                                            }
                                        }
                                    }
                                }
                                if (changed) saveLastSyncTimestamp(userId, maxTs)
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("headings")
                .whereGreaterThan("updatedAt", queryFloor)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                var maxTs = getLastSyncTimestamp(userId)
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    runCatchingCancellable {
                                        val dto = HeadingFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank() && dto.projectId.isNotBlank()) {
                                            val local = database.headingDao().getById(dto.id)
                                            if (local == null || dto.updatedAt >= local.updatedAt) {
                                                database.headingDao().upsert(dto.toEntity())
                                            }
                                            val clamped = clampRemoteTimestamp(dto.updatedAt)
                                            if (clamped > maxTs) {
                                                maxTs = clamped
                                                changed = true
                                            }
                                        }
                                    }
                                }
                                if (changed) saveLastSyncTimestamp(userId, maxTs)
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("checklist_items")
                .whereGreaterThan("updatedAt", queryFloor)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                var maxTs = getLastSyncTimestamp(userId)
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    runCatchingCancellable {
                                        val dto = ChecklistItemFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank() && dto.taskId.isNotBlank()) {
                                            val local = database.checklistDao().getById(dto.id)
                                            if (local == null || dto.updatedAt >= local.updatedAt) {
                                                database.checklistDao().upsert(dto.toEntity())
                                            }
                                            val clamped = clampRemoteTimestamp(dto.updatedAt)
                                            if (clamped > maxTs) {
                                                maxTs = clamped
                                                changed = true
                                            }
                                        }
                                    }
                                }
                                if (changed) saveLastSyncTimestamp(userId, maxTs)
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("tags")
                .whereGreaterThan("updatedAt", queryFloor)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                var maxTs = getLastSyncTimestamp(userId)
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    runCatchingCancellable {
                                        val dto = TagFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank()) {
                                            val local = database.tagDao().getById(dto.id)
                                            if (local == null || dto.updatedAt >= local.updatedAt) {
                                                database.tagDao().upsert(dto.toEntity())
                                            }
                                            val clamped = clampRemoteTimestamp(dto.updatedAt)
                                            if (clamped > maxTs) {
                                                maxTs = clamped
                                                changed = true
                                            }
                                        }
                                    }
                                }
                                if (changed) saveLastSyncTimestamp(userId, maxTs)
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("project_templates")
                .whereGreaterThan("updatedAt", queryFloor)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                var maxTs = getLastSyncTimestamp(userId)
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    runCatchingCancellable {
                                        val dto = ProjectTemplateFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank()) {
                                            val local = database.projectTemplateDao().getById(dto.id)
                                            if (local == null || dto.updatedAt >= local.updatedAt) {
                                                database.projectTemplateDao().upsert(dto.toEntity())
                                            }
                                            val clamped = clampRemoteTimestamp(dto.updatedAt)
                                            if (clamped > maxTs) {
                                                maxTs = clamped
                                                changed = true
                                            }
                                        }
                                    }
                                }
                                if (changed) saveLastSyncTimestamp(userId, maxTs)
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("attachments")
                .whereGreaterThan("updatedAt", queryFloor)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            scheduleListenerReconnect(userId)
                            return@addSnapshotListener
                        }
                        if (snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                var maxTs = getLastSyncTimestamp(userId)
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    runCatchingCancellable {
                                        val dto = AttachmentFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank()) {
                                            applyRemoteAttachmentDto(dto)
                                            val clamped = clampRemoteTimestamp(dto.updatedAt)
                                            if (clamped > maxTs) {
                                                maxTs = clamped
                                                changed = true
                                            }
                                        }
                                    }
                                }
                                if (changed) saveLastSyncTimestamp(userId, maxTs)
                            }
                        }
                    } catch (_: Throwable) {}
                }

            val localDeviceId = getOrCreateDeviceId()
            activeListeners += userDoc.collection("sync_meta").document("pulse")
                .addSnapshotListener { docSnap, error ->
                    try {
                        if (error != null) {
                            scheduleListenerReconnect(userId)
                            return@addSnapshotListener
                        }
                        if (docSnap == null || !docSnap.exists() || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        val data = docSnap.data ?: return@addSnapshotListener
                        val sender = data["senderDeviceId"] as? String
                        val pulseAt = (data["pulseAt"] as? Number)?.toLong() ?: 0L
                        if (!sender.isNullOrBlank() && sender == localDeviceId) return@addSnapshotListener
                        if (pulseAt > 0L && pulseAt != lastHandledPulseAt) {
                            lastHandledPulseAt = pulseAt
                            scheduleIncrementalFirestorePull(userId, 15L)
                        }
                    } catch (_: Throwable) {}
                }
        }
    }

    private fun scheduleListenerReconnect(userId: String) {
        if (isLocalOnlyDbModeEnabled() || !isLiveFirebaseConfigured()) return
        if (listenerReconnectJob?.isActive == true) return
        listenerReconnectJob = scope.launch {
            delay(2000L)
            if (!isLocalOnlyDbModeEnabled()) {
                runCatchingCancellable { attachRealtimeDeltaListeners(userId) }
            }
        }
    }

    fun detachRealtimeDeltaListeners() {
        runCatching { realtimeCloudPollJob?.cancel() }
        realtimeCloudPollJob = null
        runCatching { liveNtfyStreamJob?.cancel() }
        liveNtfyStreamJob = null
        runCatching { listenerReconnectJob?.cancel() }
        listenerReconnectJob = null
        synchronized(activeListeners) {
            activeListeners.forEach { runCatching { it.remove() } }
            activeListeners.clear()
        }
    }

    private fun activeLang(): String = Locale.getDefault().language.lowercase(Locale.ROOT)

    private fun localizedInvalidEmailError(): String = when (activeLang()) {
        "de" -> "Bitte gib eine gültige E-Mail-Adresse ein."
        "fr" -> "Veuillez saisir une adresse e-mail valide."
        "it" -> "Inserisci un indirizzo e-mail valido."
        "es" -> "Introduce una dirección de correo electrónico válida."
        else -> "Please enter a valid email address."
    }

    private fun localizedPasswordTooShortError(): String = when (activeLang()) {
        "de" -> "Das Passwort muss mindestens 6 Zeichen lang sein."
        "fr" -> "Le mot de passe doit contenir au moins 6 caractères."
        "it" -> "La password deve contenere almeno 6 caratteri."
        "es" -> "La contraseña debe tener al menos 6 caracteres."
        else -> "Password must be at least 6 characters long."
    }

    private fun localizedWrongPasswordError(email: String): String = when (activeLang()) {
        "de" -> "Falsches Passwort für $email. Tippe auf „Passwort vergessen?“, um es per E-Mail zurückzusetzen."
        "fr" -> "Mot de passe incorrect pour $email. Utilisez « Mot de passe oublié ? » pour le réinitialiser."
        "it" -> "Password errata per $email. Tocca «Password dimenticata?» per reimpostarla via e-mail."
        "es" -> "Contraseña incorrecta para $email. Usa «¿Olvidaste tu contraseña?» para restablecerla."
        else -> "Incorrect password for $email. Tap 'Forgot password?' below to reset it via email."
    }

    private fun localizedAccountAlreadyExistsError(): String = when (activeLang()) {
        "de" -> "Dieses Konto existiert bereits. Bitte melde dich mit deinem Passwort an oder nutze „Passwort vergessen?“."
        "fr" -> "Ce compte existe déjà. Connectez-vous avec votre mot de passe ou utilisez « Mot de passe oublié ? »."
        "it" -> "Questo account esiste già. Accedi con la tua password o usa «Password dimenticata?»."
        "es" -> "Esta cuenta ya existe. Inicia sesión con tu contraseña o usa «¿Olvidaste tu contraseña?»."
        else -> "This account already exists. Please sign in with your password or use 'Forgot password?'."
    }

    private fun localizedResetEmailMissingError(): String = when (activeLang()) {
        "de" -> "Bitte gib oben zuerst deine E-Mail-Adresse ein und tippe dann auf „Passwort zurücksetzen“."
        "fr" -> "Veuillez d'abord saisir votre adresse e-mail ci-dessus avant de réinitialiser le mot de passe."
        "it" -> "Inserisci prima il tuo indirizzo e-mail sopra, poi tocca «Reimposta password»."
        "es" -> "Introduce primero tu correo electrónico arriba y luego pulsa restablecer contraseña."
        else -> "Please enter your email address above first, then tap reset password."
    }

    private fun localizedPasswordResetSentInfo(email: String): String = when (activeLang()) {
        "de" -> "E-Mail zum Zurücksetzen des Passworts wurde an $email gesendet. Bitte prüfe dein Postfach."
        "fr" -> "Un e-mail de réinitialisation du mot de passe a été envoyé à $email."
        "it" -> "E-mail di reimpostazione della password inviata a $email."
        "es" -> "Se ha enviado un correo para restablecer la contraseña a $email."
        else -> "Password reset email sent to $email. Please check your inbox."
    }

    private fun localizedGoogleSignInFailedError(): String = when (activeLang()) {
        "de" -> "Google-Anmeldung fehlgeschlagen. Bitte versuche es erneut."
        "fr" -> "Échec de la connexion Google. Veuillez réessayer."
        "it" -> "Accesso con Google non riuscito. Riprova."
        "es" -> "Error al iniciar sesión con Google. Inténtalo de nuevo."
        else -> "Google sign-in failed. Please try again."
    }

    private fun localizedCloudOfflineError(): String = when (activeLang()) {
        "de" -> "Cloud-Synchronisierung ist derzeit offline oder nicht erreichbar."
        "fr" -> "La synchronisation Cloud est actuellement hors ligne ou inaccessible."
        "it" -> "La sincronizzazione Cloud è attualmente offline o non raggiungibile."
        "es" -> "La sincronización en la nube está desconectada o inaccesible."
        else -> "Cloud sync service is currently offline or unreachable."
    }

    companion object {
        const val MAX_INLINE_SYNC_BYTES = 700_000L
        const val CLOCK_SKEW_OVERLAP_MS = 120_000L
        const val MAX_FUTURE_DRIFT_MS = 60_000L
        const val MAX_NTFY_CHUNK_BYTES = 3800
        const val FAST_PUSH_COALESCE_MS = 35L

        private val dedupJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun deriveUidWakeChannelTopic(uid: String): String {
            val seed = "thingsbook-uid-v2:${uid.trim()}"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(seed.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return "tb_uid_${digest.take(28)}"
        }

        fun formatDeterministicUuidFromSha256Hex(sha256HexDigest: String): String {
            val h = sha256HexDigest.trim().lowercase(Locale.ROOT)
            return "${h.substring(0, 8)}-${h.substring(8, 12)}-5${h.substring(13, 16)}-a${h.substring(17, 20)}-${h.substring(20, 32)}"
        }

        fun deriveRecurringNextTaskId(completedTaskId: String, nextWhenYyyyMmDd: String): String {
            val seed = "thingsbook-rec-next-v1:${completedTaskId.trim()}:${nextWhenYyyyMmDd.trim()}"
            val hex = MessageDigest.getInstance("SHA-256")
                .digest(seed.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return formatDeterministicUuidFromSha256Hex(hex)
        }

        fun deriveRecurringChecklistItemId(nextTaskId: String, sourceChecklistId: String): String {
            val seed = "thingsbook-rec-chk-v1:${nextTaskId.trim()}:${sourceChecklistId.trim()}"
            val hex = MessageDigest.getInstance("SHA-256")
                .digest(seed.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return formatDeterministicUuidFromSha256Hex(hex)
        }

        fun canonicalizeRecurrenceRuleKey(rule: RecurrenceRule?): String? {
            if (rule == null) return null
            val interval = rule.interval.coerceIn(1, 3650)
            val validDays = rule.daysOfWeek
                .filter { it in 1..7 }
                .distinct()
                .sorted()
                .joinToString(",")
            val dom = rule.dayOfMonth?.toString().orEmpty()
            val endMs = rule.endDateEpochMillis?.toString().orEmpty()
            return "${rule.mode.name}|${rule.unit.name}|$interval|$validDays|$dom|$endMs"
        }

        fun buildRecurringSeriesKey(entity: TaskEntity): String? {
            if (entity.isDeleted || entity.status != TaskStatus.OPEN) return null
            val normTitle = entity.title.trim()
            if (normTitle.isEmpty()) return null
            val rawRule = entity.recurrenceRuleJson ?: return null
            val rule = runCatching { dedupJson.decodeFromString<RecurrenceRule>(rawRule) }.getOrNull()
                ?: return null
            val ruleKey = canonicalizeRecurrenceRuleKey(rule) ?: return null
            val normNotes = entity.notes.trim()
            val proj = entity.projectId?.trim().orEmpty()
            val head = entity.headingId?.trim().orEmpty()
            val area = entity.areaId?.trim().orEmpty()
            val normContainer = if (proj.isNotEmpty()) "P:$proj|H:$head" else "A:$area"
            val normTags = runCatching { dedupJson.decodeFromString<List<String>>(entity.tagIdsJson) }
                .getOrDefault(emptyList())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .joinToString(",")
            val normTimeblock = "${entity.scheduledStartMinutes?.toString().orEmpty()}:${entity.durationMinutes?.toString().orEmpty()}"
            return "$normTitle\u001F$normNotes\u001F$normContainer\u001F$ruleKey\u001F$normTags\u001F$normTimeblock"
        }

        fun compareRecurringTaskPriority(a: TaskEntity, b: TaskEntity): Int {
            val aWhen = a.whenDate ?: -1L
            val bWhen = b.whenDate ?: -1L
            if (aWhen != bWhen) {
                return if (aWhen > bWhen) -1 else 1
            }
            val aCreated = if (a.createdAt > 0L) a.createdAt else Long.MAX_VALUE
            val bCreated = if (b.createdAt > 0L) b.createdAt else Long.MAX_VALUE
            if (aCreated != bCreated) {
                return if (aCreated < bCreated) -1 else 1
            }
            return a.id.compareTo(b.id)
        }

        fun selectRecurringDuplicateLoserIds(tasks: List<TaskEntity>): List<String> {
            val groups = LinkedHashMap<String, MutableList<TaskEntity>>()
            for (task in tasks) {
                val key = buildRecurringSeriesKey(task) ?: continue
                groups.getOrPut(key) { mutableListOf() }.add(task)
            }
            val losers = mutableListOf<String>()
            for (group in groups.values) {
                if (group.size < 2) continue
                val sorted = group.sortedWith(::compareRecurringTaskPriority)
                for (i in 1 until sorted.size) {
                    losers.add(sorted[i].id)
                }
            }
            return losers
        }

        fun computeDeltaQueryFloor(lastSyncTimestamp: Long): Long =
            maxOf(0L, lastSyncTimestamp - CLOCK_SKEW_OVERLAP_MS)

        fun clampRemoteTimestamp(
            remoteUpdatedAt: Long,
            nowMillis: Long = System.currentTimeMillis()
        ): Long = minOf(remoteUpdatedAt, nowMillis + MAX_FUTURE_DRIFT_MS)

        fun mergeMonotonicReviewTimestamp(
            localLastReviewedAt: Long?,
            remoteLastReviewedAt: Long?
        ): Long? {
            val l = localLastReviewedAt?.takeIf { it > 0L }
            val r = remoteLastReviewedAt?.takeIf { it > 0L }
            return when {
                l != null && r != null -> maxOf(l, r)
                l != null -> l
                else -> r
            }
        }

        fun buildSafeLocalAttachmentPath(attachmentId: String, fileName: String): String {
            val safeId = attachmentId.trim()
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .replace(Regex("\\.{2,}"), "_")
                .trim('.', '_')
                .take(64)
                .ifEmpty { "att" }
            val safeFileName = fileName.trim()
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .replace(Regex("\\.{2,}"), "_")
                .trim('.', '_')
                .take(80)
                .ifEmpty { "file" }
            return "attachments/${safeId}_${safeFileName}"
        }

        fun resolveVerifiedAttachmentFile(filesDir: File, localRelativePath: String): File {
            val rel = localRelativePath.trim()
            if (rel.isEmpty() || rel.startsWith("/") || rel.startsWith("\\") || rel.contains('\u0000')) {
                throw SecurityException("Invalid attachment path: $localRelativePath")
            }
            val allowedRoot = File(filesDir, "attachments").apply { mkdirs() }
            val canonicalRootPrefix = allowedRoot.canonicalPath + File.separator
            val candidate = File(filesDir, rel).canonicalFile
            if (!candidate.path.startsWith(canonicalRootPrefix)) {
                throw SecurityException("Path traversal blocked for attachment path: $localRelativePath")
            }
            candidate.parentFile?.mkdirs()
            return candidate
        }

        fun readAttachmentBase64InRoot(
            filesDir: File,
            localRelativePath: String,
            maxRawBytes: Long = MAX_INLINE_SYNC_BYTES
        ): String {
            if (localRelativePath.isBlank()) return ""
            return runCatching {
                val file = resolveVerifiedAttachmentFile(filesDir, localRelativePath)
                if (file.exists() && file.isFile && file.length() in 1..maxRawBytes) {
                    Base64.getEncoder().encodeToString(file.readBytes())
                } else {
                    ""
                }
            }.getOrDefault("")
        }

        fun writeAttachmentBase64InRoot(
            filesDir: File,
            localRelativePath: String,
            dataBase64: String
        ): Long {
            if (localRelativePath.isBlank() || dataBase64.isBlank()) return 0L
            return runCatching {
                val file = resolveVerifiedAttachmentFile(filesDir, localRelativePath)
                val bytes = Base64.getMimeDecoder().decode(dataBase64.trim())
                file.writeBytes(bytes)
                bytes.size.toLong()
            }.getOrDefault(0L)
        }

        fun deleteAttachmentFileInRoot(filesDir: File, localRelativePath: String) {
            if (localRelativePath.isBlank()) return
            runCatching {
                val file = resolveVerifiedAttachmentFile(filesDir, localRelativePath)
                if (file.exists() && file.isFile) {
                    file.delete()
                }
            }
        }
    }
}
