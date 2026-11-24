package com.pv.thingsbook.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.pv.thingsbook.data.local.ThingsDatabase
import com.pv.thingsbook.data.local.entity.TaskEntity
import com.pv.thingsbook.data.notifications.ReminderScheduler
import com.pv.thingsbook.data.remote.dto.AreaFirestoreDto
import com.pv.thingsbook.data.remote.dto.AttachmentFirestoreDto
import com.pv.thingsbook.data.remote.dto.ChecklistItemFirestoreDto
import com.pv.thingsbook.data.remote.dto.CloudDeltaBatchPayload
import com.pv.thingsbook.data.remote.dto.CloudUserAuthEnvelope
import com.pv.thingsbook.data.remote.dto.HeadingFirestoreDto
import com.pv.thingsbook.data.remote.dto.ProjectFirestoreDto
import com.pv.thingsbook.data.remote.dto.ProjectTemplateFirestoreDto
import com.pv.thingsbook.data.remote.dto.TagFirestoreDto
import com.pv.thingsbook.data.remote.dto.TaskFirestoreDto
import com.pv.thingsbook.data.remote.dto.toFirestoreDto
import com.pv.thingsbook.domain.model.RecurrenceRule
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.util.runCatchingCancellable
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
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

    private suspend fun hasStaleOfflineDirtyEntities(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val cutoff = nowMillis - OFFLINE_COMMIT_RESTAMP_THRESHOLD_MS
        return database.taskDao().getDirtyTasks().any { it.updatedAt < cutoff } ||
            database.projectDao().getDirtyProjects().any { it.updatedAt < cutoff } ||
            database.areaDao().getDirtyAreas().any { it.updatedAt < cutoff } ||
            database.headingDao().getDirtyHeadings().any { it.updatedAt < cutoff } ||
            database.checklistDao().getDirtyChecklistItems().any { it.updatedAt < cutoff } ||
            database.tagDao().getDirtyTags().any { it.updatedAt < cutoff } ||
            database.projectTemplateDao().getDirtyTemplates().any { it.updatedAt < cutoff } ||
            database.attachmentDao().getDirtyAttachments().any { it.updatedAt < cutoff }
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
                    val email = prefs[KEY_ACTIVE_USER_EMAIL]
                    val channelTopic = prefs[KEY_ACTIVE_CHANNEL_TOPIC] ?: deriveOAuthChannelTopic(userId)

                    // If dirty rows accumulated while offline (>30s old), pull remote deltas FIRST
                    // and restamp surviving dirty rows so peers querying with 120s overlap never miss them.
                    if (hasStaleOfflineDirtyEntities()) {
                        syncBidirectionalDeltas()
                        return@runCatchingCancellable
                    }

                    syncMutex.withLock {
                        val pushedOk = pushDirtyRecordsPrimaryFirst(
                            userId = userId,
                            channelTopic = channelTopic,
                            email = email,
                            restampStaleOffline = false
                        )
                        if (pushedOk) {
                            val nowMillis = System.currentTimeMillis()
                            _lastSyncedAt.value = nowMillis
                            _syncStatus.value = CloudSyncStatus.SYNCED
                        }
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
                syncMutex.withLock {
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
            startRealtimeMultiDeviceSync(userId, channelTopic, email)
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
                startRealtimeMultiDeviceSync(fbUser.uid, resolvedTopic, fbEmail)
            }
        }
    }

    private fun sha256Hex(input: String): String = Companion.sha256Hex(input)

    private fun deriveUserId(normalizedEmail: String): String =
        UUID.nameUUIDFromBytes("thingsbook-uid:$normalizedEmail".toByteArray(StandardCharsets.UTF_8)).toString()

    private fun derivePasswordVerifier(normalizedEmail: String, password: String): String =
        sha256Hex("thingsbook-pw-v1:$normalizedEmail:$password")

    private fun deriveUserRegistryTopic(normalizedEmail: String): String =
        "tb_usr_${sha256Hex("thingsbook-reg-v1:$normalizedEmail").take(26)}"

    private fun deriveDeltaChannelTopic(normalizedEmail: String, passwordVerifier: String): String =
        "tb_sync_${sha256Hex("thingsbook-chan-v1:$normalizedEmail:$passwordVerifier").take(28)}"

    private fun deriveOAuthChannelTopic(uid: String): String =
        Companion.deriveOAuthChannelTopicStatic(uid)

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

    private suspend fun performInitialSessionSync(uid: String, email: String, channelTopic: String) {
        lastNtfyMessageIdByTopic.remove(channelTopic)
        context.syncDataStore.edit { prefs ->
            prefs[KEY_ACTIVE_USER_ID] = uid
            prefs[KEY_ACTIVE_USER_EMAIL] = email
            prefs[KEY_ACTIVE_CHANNEL_TOPIC] = channelTopic
            prefs[lastSyncKeyForUser(uid)] = 0L
            for (col in SYNC_COLLECTION_NAMES) {
                prefs.remove(collectionCursorKeyForUser(uid, col))
            }
            prefs.remove(lastNtfyMsgIdKeyForTopic(channelTopic))
        }
        _activeUserIdentifier.value = email

        if (!isLocalOnlyDbModeEnabled()) {
            var maxPulled = 0L
            val fbPulledOk = if (isLiveFirebaseConfigured()) {
                runCatchingCancellable { pullDeltaRecordsFromFirestore(uid, 0L, forceFullPull = true) }
                    .onSuccess { maxPulled = maxOf(maxPulled, it) }
                    .isSuccess
            } else {
                false
            }
            var ntfyPulledOk = false
            if (!fbPulledOk) {
                val legacyPull = pullDeltaRecordsFromBuiltInCloudWithStatus(
                    userId = uid,
                    channelTopic = channelTopic,
                    lastSyncTimestamp = 0L,
                    forceFullHistory = true
                )
                ntfyPulledOk = legacyPull.second
                maxPulled = maxOf(maxPulled, legacyPull.first)
            } else {
                scope.launch {
                    runCatchingCancellable {
                        for (legacyTopic in deriveLegacySyncTopics(uid, email, channelTopic)) {
                            pullDeltaRecordsFromBuiltInCloud(uid, legacyTopic, 0L, forceFullHistory = true)
                        }
                    }
                }
            }
            pushDirtyRecordsPrimaryFirst(
                userId = uid,
                channelTopic = channelTopic,
                email = email,
                restampStaleOffline = true
            )
            val nowMillis = System.currentTimeMillis()
            // Never advance pull cursor to nowMillis if BOTH Firestore and fallback pull failed!
            if (fbPulledOk || (!isLiveFirebaseConfigured() && ntfyPulledOk)) {
                saveLastSyncTimestamp(uid, maxOf(clampRemoteTimestamp(maxPulled, nowMillis), nowMillis))
            }
            _lastSyncedAt.value = nowMillis
            _syncStatus.value = if (fbPulledOk || ntfyPulledOk) CloudSyncStatus.SYNCED else CloudSyncStatus.OFFLINE
            startRealtimeMultiDeviceSync(uid, channelTopic, email)
        } else {
            _syncStatus.value = CloudSyncStatus.OFFLINE
        }
    }

    private suspend fun signInWithGoogleViaWebBridge(activity: Activity): AuthResult {
        val stateNonce = UUID.randomUUID().toString().replace("-", "")
        val deferred = CompletableDeferred<GoogleWebBridgeTokens>()
        registerPendingWebGoogleOAuth(stateNonce, deferred)
        try {
            val configuredProjectId = runCatching { firebaseAuth.app.options.projectId }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "thingsbook-offline-local" }
                ?: "thingsbook-prod"
            val bridgeUrl = "https://$configuredProjectId.web.app/?android_google_auth=1&state=" +
                URLEncoder.encode(stateNonce, StandardCharsets.UTF_8.name())

            withContext(Dispatchers.Main) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(bridgeUrl)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                activity.startActivity(browserIntent)
            }

            val tokens = withTimeout(180_000L) {
                deferred.await()
            }
            if (!tokens.error.isNullOrBlank()) {
                throw IllegalStateException(tokens.error)
            }
            if (tokens.idToken.isBlank() && tokens.accessToken.isBlank()) {
                throw IllegalStateException(localizedGoogleSignInFailedError())
            }
            val credential = GoogleAuthProvider.getCredential(
                tokens.idToken.takeIf { it.isNotBlank() },
                tokens.accessToken.takeIf { it.isNotBlank() }
            )
            return firebaseAuth.signInWithCredential(credential).await()
        } finally {
            clearPendingWebGoogleOAuth(stateNonce)
        }
    }

    suspend fun signInWithGoogleOAuth(activity: Activity): Result<String> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            _syncStatus.value = CloudSyncStatus.SYNCING

            val provider = OAuthProvider.newBuilder("google.com").apply {
                addCustomParameter("prompt", "select_account")
            }.build()

            val authResult = try {
                withContext(Dispatchers.Main) {
                    firebaseAuth.pendingAuthResult?.await()
                        ?: firebaseAuth.startActivityForSignInWithProvider(activity, provider).await()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (primaryErr: Exception) {
                if (shouldFallbackToWebGoogleOAuth(primaryErr)) {
                    Log.w(
                        "SyncRepository",
                        "Primary Android Google OAuth failed (${primaryErr.message}); falling back to authorized Web OAuth bridge",
                        primaryErr
                    )
                    signInWithGoogleViaWebBridge(activity)
                } else {
                    throw primaryErr
                }
            }

            val fbUser = authResult.user
                ?: throw IllegalStateException(localizedGoogleSignInFailedError())
            val uid = fbUser.uid
            val email = fbUser.email?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: uid
            val channelTopic = deriveOAuthChannelTopic(uid)

            performInitialSessionSync(uid, email, channelTopic)
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

            performInitialSessionSync(userId, email, channelTopic)
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

            performInitialSessionSync(userId, email, channelTopic)
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

    private fun collectionCursorKeyForUser(userId: String, collectionName: String) =
        longPreferencesKey("col_sync_ts_v2_${collectionName}_$userId")

    private fun reconcileB22KeyForUser(userId: String) =
        booleanPreferencesKey("reconcile_v2_22_$userId")

    private fun attachmentsBackfillKeyForUser(userId: String) =
        booleanPreferencesKey("attachments_backfill_v1_$userId")

    private fun projectTemplatesBackfillKeyForUser(userId: String) =
        booleanPreferencesKey("project_templates_backfill_v1_$userId")

    private fun projectsReviewBackfillKeyForUser(userId: String) =
        booleanPreferencesKey("projects_review_backfill_v1_$userId")

    private suspend fun applyRemoteProjectDto(
        dto: ProjectFirestoreDto,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val local = database.projectDao().getById(dto.id)
        val mergedLastReviewed = if (dto.isDeleted) {
            dto.lastReviewedAt
        } else {
            mergeMonotonicReviewTimestamp(local?.lastReviewedAt, dto.lastReviewedAt)
        }
        val clampedTs = clampRemoteTimestamp(dto.updatedAt, nowMillis)
        if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
            val localWasPurged = local != null && local.isDeleted && local.title.isEmpty() && local.notes.isEmpty()
            val effectiveDto = if (dto.isDeleted && localWasPurged) dto.copy(purgedFromTrash = true) else dto
            val baseEntity = effectiveDto.toEntity()
            val keepLocalV30Proj = effectiveDto.omittedV30ProjectFields &&
                !(effectiveDto.isDeleted && effectiveDto.purgedFromTrash) &&
                local != null
            database.projectDao().upsertProjectWithTags(
                baseEntity.copy(
                    updatedAt = clampedTs,
                    lastReviewedAt = mergedLastReviewed,
                    workspaceId = if (keepLocalV30Proj) local!!.workspaceId else baseEntity.workspaceId,
                    viewMode = if (keepLocalV30Proj) local!!.viewMode else baseEntity.viewMode
                ),
                if (effectiveDto.isDeleted && effectiveDto.purgedFromTrash) emptyList() else effectiveDto.tagIds
            )
        } else if (local != null && !dto.isDeleted && !local.isDeleted &&
            mergedLastReviewed != null && mergedLastReviewed > (local.lastReviewedAt ?: 0L)
        ) {
            database.projectDao().upsert(
                local.copy(lastReviewedAt = mergedLastReviewed)
            )
        }
    }

    private suspend fun applyRemoteHeadingDto(
        dto: HeadingFirestoreDto,
        nowMillis: Long
    ) {
        val local = database.headingDao().getById(dto.id)
        val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
        if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
            val preservedEndDate = if (dto.omittedEndDate && local != null && !dto.isDeleted) {
                local.endDate
            } else {
                dto.endDate
            }
            val preservedStartDate = if (dto.omittedStartDate && local != null && !dto.isDeleted) {
                local.startDate
            } else {
                dto.startDate
            }
            database.headingDao().upsert(
                dto.toEntity().copy(
                    startDate = preservedStartDate,
                    endDate = preservedEndDate,
                    updatedAt = clamped
                )
            )
        }
    }

    private fun buildSeriesLookupKeyFromParts(
        title: String,
        recurrenceRuleJson: String?,
        projectId: String?
    ): String? {
        val normTitle = normalizeRecurringTitleKey(title)
        if (normTitle.isEmpty()) return null
        val rawRule = recurrenceRuleJson ?: return null
        val rule = runCatching { json.decodeFromString<RecurrenceRule>(rawRule) }.getOrNull() ?: return null
        val ruleKey = canonicalizeRecurrenceRuleKey(rule) ?: return null
        return "$normTitle\u001F$ruleKey\u001F${projectId?.trim().orEmpty()}"
    }

    private suspend fun buildOpenSeriesIndex(extraDtos: List<TaskFirestoreDto> = emptyList()): Map<String, Set<String>> {
        val map = LinkedHashMap<String, MutableSet<String>>()
        for (tk in database.taskDao().getOpenRecurringTasks()) {
            if (tk.isDeleted || tk.status != TaskStatus.OPEN) continue
            val key = buildSeriesLookupKeyFromParts(tk.title, tk.recurrenceRuleJson, tk.projectId) ?: continue
            map.getOrPut(key) { LinkedHashSet() }.add(tk.id)
        }
        for (dto in extraDtos) {
            if (dto.isDeleted || !dto.status.equals(TaskStatus.OPEN.name, ignoreCase = true)) continue
            val key = buildSeriesLookupKeyFromParts(dto.title, dto.recurrenceRule, dto.projectId) ?: continue
            map.getOrPut(key) { LinkedHashSet() }.add(dto.id)
        }
        return map
    }

    private suspend fun applyRemoteTaskDtoLocked(
        dto: TaskFirestoreDto,
        nowMillis: Long,
        openSeriesIndex: Map<String, Set<String>>
    ): Pair<TaskEntity?, Boolean> {
        val clampedTs = clampRemoteTimestamp(dto.updatedAt, nowMillis)
        val local = database.taskDao().getById(dto.id)

        // Guard against v1.8 startup rollover resurrecting a COMPLETED recurring parent when
        // another OPEN occurrence in the same series already exists.
        if (local != null &&
            !local.isDeleted &&
            local.status == TaskStatus.COMPLETED &&
            local.recurrenceRuleJson != null &&
            !dto.isDeleted &&
            dto.status.equals(TaskStatus.OPEN.name, ignoreCase = true)
        ) {
            val sKey = buildSeriesLookupKeyFromParts(
                title = local.title.ifBlank { dto.title },
                recurrenceRuleJson = local.recurrenceRuleJson ?: dto.recurrenceRule,
                projectId = local.projectId ?: dto.projectId
            )
            val hasOtherOpenOccurrence = sKey != null &&
                openSeriesIndex[sKey]?.any { it != dto.id } == true
            if (hasOtherOpenOccurrence) {
                val healed = local.copy(
                    updatedAt = maxOf(nowMillis, clampedTs + 1L, local.updatedAt + 1L),
                    isDirty = true
                )
                database.taskDao().upsert(healed)
                return null to true
            }
        }

        if (!shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
            return null to false
        }

        val localWasPurged = local != null && local.isDeleted && local.title.isEmpty() && local.notes.isEmpty()
        val effectiveDto = if (dto.isDeleted && localWasPurged) dto.copy(purgedFromTrash = true) else dto
        val baseEntity = effectiveDto.toEntity(local)
        val keepLocalTimeblock = effectiveDto.omittedTimeblockFields &&
            !effectiveDto.isDeleted &&
            local != null &&
            (baseEntity.scheduleState == ScheduleState.TODAY ||
                baseEntity.scheduleState == ScheduleState.THIS_EVENING ||
                baseEntity.scheduleState == ScheduleState.UPCOMING)
        val keepLocalPin = effectiveDto.omittedPinField &&
            !(effectiveDto.isDeleted && effectiveDto.purgedFromTrash) &&
            local != null
        val keepLocalV30 = effectiveDto.omittedV30TaskFields &&
            !(effectiveDto.isDeleted && effectiveDto.purgedFromTrash) &&
            local != null

        val finalEntity = baseEntity.copy(
            updatedAt = clampedTs,
            scheduledStartMinutes = if (keepLocalTimeblock) local?.scheduledStartMinutes else baseEntity.scheduledStartMinutes,
            durationMinutes = if (keepLocalTimeblock) local?.durationMinutes else baseEntity.durationMinutes,
            isPinned = if (keepLocalPin) local!!.isPinned else baseEntity.isPinned,
            effortLevel = if (keepLocalV30) local!!.effortLevel else baseEntity.effortLevel,
            blockedByJson = if (keepLocalV30) local!!.blockedByJson else baseEntity.blockedByJson,
            workspaceId = if (keepLocalV30) local!!.workspaceId else baseEntity.workspaceId,
            assigneeId = if (keepLocalV30) local!!.assigneeId else baseEntity.assigneeId
        )
        val effectiveTagIds = if (effectiveDto.isDeleted && effectiveDto.purgedFromTrash) emptyList() else effectiveDto.tagIds
        database.taskDao().upsertTaskWithTags(finalEntity, effectiveTagIds)
        return finalEntity to false
    }

    private suspend fun reconcileDuplicateTagsLocked(
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val activeTags = database.tagDao().getAllNonDeletedTagsList()
        if (activeTags.size < 2) return false
        val byNormTitle = activeTags
            .filter { !it.isDeleted && it.title.isNotBlank() }
            .groupBy { it.title.trim().lowercase(Locale.ROOT) }
        val remap = LinkedHashMap<String, String>()
        val losers = mutableListOf<com.pv.thingsbook.data.local.entity.TagEntity>()
        for (group in byNormTitle.values) {
            if (group.size < 2) continue
            val sorted = group.sortedWith { a, b ->
                val ac = if (a.createdAt > 0L) a.createdAt else Long.MAX_VALUE
                val bc = if (b.createdAt > 0L) b.createdAt else Long.MAX_VALUE
                if (ac != bc) ac.compareTo(bc) else a.id.compareTo(b.id)
            }
            val winner = sorted.first()
            for (loser in sorted.drop(1)) {
                remap[loser.id] = winner.id
                losers += loser
            }
        }
        if (remap.isEmpty()) return false

        fun remapJsonList(rawJson: String): Pair<List<String>, Boolean> {
            val orig = runCatching { json.decodeFromString<List<String>>(rawJson) }.getOrDefault(emptyList())
            var changed = false
            val out = mutableListOf<String>()
            for (id in orig) {
                val mapped = remap[id] ?: id
                if (mapped != id) changed = true
                if (mapped !in out) {
                    out += mapped
                } else {
                    changed = true
                }
            }
            return out to changed
        }

        database.withTransaction {
            for (loser in losers) {
                database.tagDao().deleteTaskTagCrossRefsForTag(loser.id)
                database.tagDao().deleteProjectTagCrossRefsForTag(loser.id)
                database.tagDao().upsert(
                    loser.copy(
                        isDeleted = true,
                        updatedAt = maxOf(nowMillis, loser.updatedAt + 1L),
                        isDirty = true
                    )
                )
            }
            for (task in database.taskDao().getAllNonDeletedTasksList()) {
                val (nextTags, changed) = remapJsonList(task.tagIdsJson)
                if (changed) {
                    database.taskDao().upsertTaskWithTags(
                        task.copy(
                            tagIdsJson = json.encodeToString(nextTags),
                            updatedAt = maxOf(nowMillis, task.updatedAt + 1L),
                            isDirty = true
                        ),
                        nextTags
                    )
                }
            }
            for (proj in database.projectDao().getAllNonDeletedProjectsList()) {
                val (nextTags, changed) = remapJsonList(proj.tagIdsJson)
                if (changed) {
                    database.projectDao().upsertProjectWithTags(
                        proj.copy(
                            tagIdsJson = json.encodeToString(nextTags),
                            updatedAt = maxOf(nowMillis, proj.updatedAt + 1L),
                            isDirty = true
                        ),
                        nextTags
                    )
                }
            }
            val updatedAreas = database.areaDao().getActiveAreasList().mapNotNull { area ->
                val (nextTags, changed) = remapJsonList(area.tagIdsJson)
                if (changed) {
                    area.copy(
                        tagIdsJson = json.encodeToString(nextTags),
                        updatedAt = maxOf(nowMillis, area.updatedAt + 1L),
                        isDirty = true
                    )
                } else null
            }
            if (updatedAreas.isNotEmpty()) database.areaDao().upsertAll(updatedAreas)

            val updatedTemplates = database.projectTemplateDao().getAllNonDeletedTemplatesList().mapNotNull { tpl ->
                val (nextTags, changed) = remapJsonList(tpl.tagIdsJson)
                if (changed) {
                    tpl.copy(
                        tagIdsJson = json.encodeToString(nextTags),
                        updatedAt = maxOf(nowMillis, tpl.updatedAt + 1L),
                        isDirty = true
                    )
                } else null
            }
            if (updatedTemplates.isNotEmpty()) database.projectTemplateDao().upsertAll(updatedTemplates)
        }
        return true
    }

    suspend fun deduplicateOpenRecurringTasks(): Int = dbApplyMutex.withLock {
        deduplicateOpenRecurringTasksLocked()
    }

    private suspend fun deduplicateOpenRecurringTasksLocked(
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val openRecurring = database.taskDao().getOpenRecurringTasks()
        val plan = planRecurringDeduplication(openRecurring)
        if (plan.loserIds.isEmpty() && plan.winnerEnrichments.isEmpty()) return 0

        val byId = openRecurring.associateBy { it.id }
        val loserSet = plan.loserIds.toSet()
        val losers = openRecurring.filter { it.id in loserSet }
        val enrichedWinners = mutableListOf<TaskEntity>()

        database.withTransaction {
            for (patch in plan.winnerEnrichments) {
                val current = byId[patch.winnerId] ?: continue
                val existingTags = runCatching { json.decodeFromString<List<String>>(current.tagIdsJson) }
                    .getOrDefault(emptyList())
                val mergedTags = patch.tagIds ?: existingTags
                val updatedWinner = current.copy(
                    areaId = patch.areaId ?: current.areaId,
                    headingId = patch.headingId ?: current.headingId,
                    notes = patch.notes ?: current.notes,
                    tagIdsJson = json.encodeToString(mergedTags),
                    scheduledStartMinutes = patch.scheduledStartMinutes ?: current.scheduledStartMinutes,
                    durationMinutes = patch.durationMinutes ?: current.durationMinutes,
                    deadline = patch.deadline ?: current.deadline,
                    reminderTime = patch.reminderTime ?: current.reminderTime,
                    updatedAt = maxOf(nowMillis, patch.minUpdatedAt),
                    isDirty = true
                )
                database.taskDao().upsertTaskWithTags(updatedWinner, mergedTags)
                enrichedWinners += updatedWinner
            }
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
                        actualMinutes = null,
                        isPinned = false,
                        effortLevel = null,
                        blockedByJson = "[]",
                        workspaceId = null,
                        assigneeId = null,
                        isDeleted = true,
                        isDirty = true,
                        updatedAt = tombstoneAt
                    ),
                    emptyList()
                )
            }
        }
        for (winner in enrichedWinners) {
            syncTaskReminderSchedule(winner)
        }
        for (loser in losers) {
            reminderScheduler.cancelReminder(loser.id)
        }
        return losers.size + enrichedWinners.size
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
                val email = prefs[KEY_ACTIVE_USER_EMAIL]
                val channelTopic = prefs[KEY_ACTIVE_CHANNEL_TOPIC] ?: deriveOAuthChannelTopic(userId)

                _syncStatus.value = CloudSyncStatus.SYNCING

                val liveFb = isLiveFirebaseConfigured()
                var anyTransportSucceeded = false

                // 1. Pull remote deltas FIRST so offline/stale local rows never overwrite newer cloud state
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

                val legacyTopics = deriveLegacySyncTopics(userId, email, channelTopic)
                if (!fbPullSucceeded) {
                    for (topic in legacyTopics) {
                        val (topicMax, ok) = pullDeltaRecordsFromBuiltInCloudWithStatus(
                            userId = userId,
                            channelTopic = topic,
                            lastSyncTimestamp = lastSync,
                            forceFullHistory = false
                        )
                        if (ok) {
                            anyTransportSucceeded = true
                            val clamped = clampRemoteTimestamp(topicMax)
                            if (clamped > maxObservedTimestamp) maxObservedTimestamp = clamped
                        }
                    }
                } else {
                    scope.launch {
                        runCatchingCancellable {
                            for (topic in legacyTopics) {
                                pullDeltaRecordsFromBuiltInCloud(userId, topic, lastSync, forceFullHistory = false)
                            }
                        }
                    }
                }

                // 2. Restamp surviving stale offline dirty rows and push outbound deltas SECOND
                if (pushDirtyRecordsPrimaryFirst(
                        userId = userId,
                        channelTopic = channelTopic,
                        email = email,
                        restampStaleOffline = true
                    )
                ) {
                    anyTransportSucceeded = true
                }

                if (!anyTransportSucceeded) {
                    throw IOException(localizedCloudOfflineError())
                }

                val nowMillis = System.currentTimeMillis()
                if (fbPullSucceeded || !liveFb) {
                    if (maxObservedTimestamp > lastSync) {
                        saveLastSyncTimestamp(userId, clampRemoteTimestamp(maxObservedTimestamp, nowMillis))
                    } else if (lastSync == 0L) {
                        saveLastSyncTimestamp(userId, nowMillis)
                    }
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

    private suspend fun restampStaleOfflineDirtyEntities(pushNow: Long) {
        val cutoff = pushNow - OFFLINE_COMMIT_RESTAMP_THRESHOLD_MS
        database.withTransaction {
            val staleAreas = database.areaDao().getDirtyAreas()
                .filter { it.updatedAt < cutoff }
                .map { it.copy(updatedAt = pushNow) }
            if (staleAreas.isNotEmpty()) database.areaDao().upsertAll(staleAreas)

            val staleProjects = database.projectDao().getDirtyProjects()
                .filter { it.updatedAt < cutoff }
                .map { it.copy(updatedAt = pushNow) }
            if (staleProjects.isNotEmpty()) database.projectDao().upsertAll(staleProjects)

            val staleHeadings = database.headingDao().getDirtyHeadings()
                .filter { it.updatedAt < cutoff }
                .map { it.copy(updatedAt = pushNow) }
            if (staleHeadings.isNotEmpty()) database.headingDao().upsertAll(staleHeadings)

            val staleTasks = database.taskDao().getDirtyTasks()
                .filter { it.updatedAt < cutoff }
                .map { it.copy(updatedAt = pushNow) }
            if (staleTasks.isNotEmpty()) database.taskDao().upsertAll(staleTasks)

            val staleChecklists = database.checklistDao().getDirtyChecklistItems()
                .filter { it.updatedAt < cutoff }
                .map { it.copy(updatedAt = pushNow) }
            if (staleChecklists.isNotEmpty()) database.checklistDao().upsertAll(staleChecklists)

            val staleTags = database.tagDao().getDirtyTags()
                .filter { it.updatedAt < cutoff }
                .map { it.copy(updatedAt = pushNow) }
            if (staleTags.isNotEmpty()) database.tagDao().upsertAll(staleTags)

            val staleTemplates = database.projectTemplateDao().getDirtyTemplates()
                .filter { it.updatedAt < cutoff }
                .map { it.copy(updatedAt = pushNow) }
            if (staleTemplates.isNotEmpty()) database.projectTemplateDao().upsertAll(staleTemplates)

            val staleAttachments = database.attachmentDao().getDirtyAttachments()
                .filter { it.updatedAt < cutoff }
                .map { it.copy(updatedAt = pushNow) }
            if (staleAttachments.isNotEmpty()) database.attachmentDao().upsertAll(staleAttachments)
        }
    }

    private suspend fun markDirtyEntitiesSyncedExact(
        dirtyAreas: List<com.pv.thingsbook.data.local.entity.AreaEntity>,
        dirtyProjects: List<com.pv.thingsbook.data.local.entity.ProjectEntity>,
        dirtyHeadings: List<com.pv.thingsbook.data.local.entity.HeadingEntity>,
        dirtyTasks: List<TaskEntity>,
        dirtyChecklists: List<com.pv.thingsbook.data.local.entity.ChecklistItemEntity>,
        dirtyTags: List<com.pv.thingsbook.data.local.entity.TagEntity>,
        dirtyTemplates: List<com.pv.thingsbook.data.local.entity.ProjectTemplateEntity>,
        dirtyAttachments: List<com.pv.thingsbook.data.local.entity.AttachmentEntity> = emptyList()
    ) {
        dirtyAreas.groupBy { it.updatedAt }.forEach { (ts, grp) ->
            database.areaDao().markSyncedSafe(grp.map { it.id }, ts)
        }
        dirtyProjects.groupBy { it.updatedAt }.forEach { (ts, grp) ->
            database.projectDao().markSyncedSafe(grp.map { it.id }, ts)
        }
        dirtyHeadings.groupBy { it.updatedAt }.forEach { (ts, grp) ->
            database.headingDao().markSyncedSafe(grp.map { it.id }, ts)
        }
        dirtyTasks.groupBy { it.updatedAt }.forEach { (ts, grp) ->
            database.taskDao().markSyncedSafe(grp.map { it.id }, ts)
        }
        dirtyChecklists.groupBy { it.updatedAt }.forEach { (ts, grp) ->
            database.checklistDao().markSyncedSafe(grp.map { it.id }, ts)
        }
        dirtyTags.groupBy { it.updatedAt }.forEach { (ts, grp) ->
            database.tagDao().markSyncedSafe(grp.map { it.id }, ts)
        }
        dirtyTemplates.groupBy { it.updatedAt }.forEach { (ts, grp) ->
            database.projectTemplateDao().markSyncedSafe(grp.map { it.id }, ts)
        }
        dirtyAttachments.groupBy { it.updatedAt }.forEach { (ts, grp) ->
            database.attachmentDao().markSyncedSafe(grp.map { it.id }, ts)
        }
    }

    private suspend fun pushDirtyRecordsPrimaryFirst(
        userId: String,
        channelTopic: String,
        email: String? = null,
        restampStaleOffline: Boolean = false
    ): Boolean {
        if (isLocalOnlyDbModeEnabled()) return false
        val now = System.currentTimeMillis()
        if (restampStaleOffline) {
            restampStaleOfflineDirtyEntities(now)
        }

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
        val uidWakeTopic = deriveUidWakeChannelTopic(userId)
        val legacyTopics = deriveLegacySyncTopics(userId, email, channelTopic)

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
                    if (uidWakeTopic.isNotBlank()) {
                        buildNtfyInlineOrWakePayload(ntfySnapshotPayload)?.let { wakeBody ->
                            runCatching { postJsonToBuiltInCloud(uidWakeTopic, wakeBody) }
                        }
                    }
                    // Legacy tb_sync_* topics NEVER receive wakeOnly=true so v1.8 clients never advance
                    // lastSyncTimestamp on an empty wake batch.
                    val legacyChunks = buildLegacyCompatibleNtfyChunks(ntfySnapshotPayload, json, maxChunks = 3)
                    for (legacyTopic in legacyTopics) {
                        for (chunk in legacyChunks) {
                            runCatching { postJsonToBuiltInCloud(legacyTopic, chunk) }
                        }
                    }
                }
                return true
            }
        }

        var fallbackOk = false
        for ((index, topic) in legacyTopics.withIndex()) {
            val ok = pushDirtyRecordsToBuiltInCloud(
                userId = userId,
                channelTopic = topic,
                markAsSynced = (index == 0)
            )
            if (ok) fallbackOk = true
        }
        if (fallbackOk && uidWakeTopic.isNotBlank() && uidWakeTopic !in legacyTopics) {
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
    ): List<String> = buildLegacyCompatibleNtfyChunks(
        fullPayload.copy(userId = userId, deviceId = deviceId, batchTimestamp = batchTimestamp, wakeOnly = false),
        json,
        maxChunks = Int.MAX_VALUE
    )

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
            markDirtyEntitiesSyncedExact(
                dirtyAreas = dirtyAreas,
                dirtyProjects = dirtyProjects,
                dirtyHeadings = dirtyHeadings,
                dirtyTasks = dirtyTasks,
                dirtyChecklists = dirtyChecklists,
                dirtyTags = dirtyTags,
                dirtyTemplates = dirtyTemplates
            )
        }
        return allSucceeded
    }

    private suspend fun pullDeltaRecordsFromBuiltInCloudWithStatus(
        userId: String,
        channelTopic: String,
        lastSyncTimestamp: Long,
        forceFullHistory: Boolean = false
    ): Pair<Long, Boolean> {
        if (isLocalOnlyDbModeEnabled() || channelTopic.isBlank()) return lastSyncTimestamp to false
        var maxTimestamp = lastSyncTimestamp
        val localDeviceId = getOrCreateDeviceId()

        val sinceMsgId = if (forceFullHistory || lastSyncTimestamp <= 0L) null else getLastNtfyMessageId(channelTopic)
        val pollResult = fetchDeltaBatchesResultFromBuiltInCloud(channelTopic, sinceMessageId = sinceMsgId)
            .getOrNull() ?: return maxTimestamp to false
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
        return maxTimestamp to true
    }

    private suspend fun pullDeltaRecordsFromBuiltInCloud(
        userId: String,
        channelTopic: String,
        lastSyncTimestamp: Long,
        forceFullHistory: Boolean = false
    ): Long = pullDeltaRecordsFromBuiltInCloudWithStatus(
        userId = userId,
        channelTopic = channelTopic,
        lastSyncTimestamp = lastSyncTimestamp,
        forceFullHistory = forceFullHistory
    ).first

    private suspend fun applyRemoteAttachmentDto(
        dto: AttachmentFirestoreDto,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (dto.id.isBlank()) return
        val localExisting = database.attachmentDao().getById(dto.id)
        val clampedTs = clampRemoteTimestamp(dto.updatedAt, nowMillis)
        val missingLocalFile = localExisting != null &&
            !localExisting.isDeleted &&
            (localExisting.localRelativePath.isBlank() ||
                !runCatching { resolveAttachmentFile(localExisting.localRelativePath).isFile }.getOrDefault(false)) &&
            dto.dataBase64.isNotBlank()
        if (shouldRemoteWin(localExisting?.updatedAt, localExisting?.isDirty == true, dto.updatedAt, nowMillis) || missingLocalFile) {
            val generatedLocalPath = buildSafeLocalAttachmentPath(dto.id, dto.fileName)
            if (dto.isDeleted) {
                localExisting?.localRelativePath?.takeIf { it.isNotBlank() }?.let { deleteAttachmentFile(it) }
                deleteAttachmentFile(generatedLocalPath)
                database.attachmentDao().upsert(dto.toEntity(generatedLocalPath).copy(updatedAt = clampedTs))
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
                database.attachmentDao().upsert(dto.toEntity(targetRelPath).copy(updatedAt = clampedTs))
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
        var healedCompletedSeries = false
        var tagsMutated = false

        val openSeriesIndex = if (batch.tasks.isNotEmpty()) buildOpenSeriesIndex(batch.tasks) else emptyMap()

        database.withTransaction {
            for (dto in batch.tags) {
                if (dto.id.isBlank()) continue
                val local = database.tagDao().getById(dto.id)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                    database.tagDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                    tagsMutated = true
                }
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.areas) {
                if (dto.id.isBlank()) continue
                val local = database.areaDao().getById(dto.id)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                    database.areaDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                }
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.projects) {
                if (dto.id.isBlank()) continue
                applyRemoteProjectDto(dto, nowMillis)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.headings) {
                if (dto.id.isBlank() || dto.projectId.isBlank()) continue
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                applyRemoteHeadingDto(dto, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.tasks) {
                if (dto.id.isBlank()) continue
                val (appliedEntity, healed) = applyRemoteTaskDtoLocked(dto, nowMillis, openSeriesIndex)
                if (appliedEntity != null) updatedTasks += appliedEntity
                if (healed) healedCompletedSeries = true
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.checklistItems) {
                if (dto.id.isBlank() || dto.taskId.isBlank()) continue
                val local = database.checklistDao().getById(dto.id)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                    database.checklistDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                }
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.projectTemplates) {
                if (dto.id.isBlank()) continue
                val local = database.projectTemplateDao().getById(dto.id)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                    database.projectTemplateDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                }
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }

            for (dto in batch.attachments) {
                if (dto.id.isBlank()) continue
                applyRemoteAttachmentDto(dto, nowMillis)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTimestamp) maxTimestamp = clamped
            }
        }

        updatedTasks.forEach { syncTaskReminderSchedule(it) }
        val tagsRemapped = if (tagsMutated) reconcileDuplicateTagsLocked(nowMillis) else false
        val deduped = if (batch.tasks.isNotEmpty()) deduplicateOpenRecurringTasksLocked(nowMillis) else 0
        if (deduped > 0 || healedCompletedSeries || tagsRemapped) {
            triggerBackgroundSync()
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
                                val batch = CloudDeltaBatchPayload.decodeWithHints(streamLine.message, json)
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

    private fun startRealtimeMultiDeviceSync(
        userId: String,
        channelTopic: String,
        email: String? = null
    ) {
        if (isLocalOnlyDbModeEnabled()) return
        detachRealtimeDeltaListeners()

        val pollIntervalMs = if (isLiveFirebaseConfigured()) 25_000L else 15_000L
        realtimeCloudPollJob = scope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                if (isLocalOnlyDbModeEnabled()) break
                syncBidirectionalDeltas()
            }
        }

        val wakeTopics = buildList {
            val uidTopic = deriveUidWakeChannelTopic(userId)
            if (uidTopic.isNotBlank()) add(uidTopic)
            addAll(deriveLegacySyncTopics(userId, email, channelTopic))
        }.distinct().joinToString(",")
        startLiveNtfyWakeStream(userId, wakeTopics)

        if (isLiveFirebaseConfigured()) {
            scope.launch {
                runCatchingCancellable { attachRealtimeDeltaListeners(userId) }
            }
        }
    }

    private fun startLiveNtfyWakeStream(userId: String, topicsCsv: String) {
        if (topicsCsv.isBlank() || isLocalOnlyDbModeEnabled()) return
        liveNtfyStreamJob?.cancel()
        liveNtfyStreamJob = scope.launch {
            while (isActive && !isLocalOnlyDbModeEnabled()) {
                runCatching {
                    val localDeviceId = getOrCreateDeviceId()
                    val url = URL("https://ntfy.sh/$topicsCsv/json?since=none")
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
                                val batch = CloudDeltaBatchPayload.decodeWithHints(streamLine.message, json) ?: continue
                                if (batch.userId != userId || batch.deviceId == localDeviceId) continue

                                if (batch.wakeOnly) {
                                    scheduleIncrementalFirestorePull(userId, 5L)
                                } else {
                                    val lastSync = getLastSyncTimestamp(userId)
                                    val batchMax = applyDeltaBatchToRoom(batch, lastSync)
                                    if (isLiveFirebaseConfigured()) {
                                        if (batchMax > 0L) {
                                            _lastSyncedAt.value = System.currentTimeMillis()
                                            scheduleIncrementalFirestorePull(userId, 35L)
                                        }
                                    } else if (batchMax > lastSync) {
                                        saveLastSyncTimestamp(userId, batchMax)
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
            markDirtyEntitiesSyncedExact(
                dirtyAreas = dirtyAreas,
                dirtyProjects = dirtyProjects,
                dirtyHeadings = dirtyHeadings,
                dirtyTasks = dirtyTasks,
                dirtyChecklists = dirtyChecklists,
                dirtyTags = dirtyTags,
                dirtyTemplates = dirtyTemplates,
                dirtyAttachments = dirtyAttachments
            )
        }
    }

    private fun queryCollectionSince(
        userDoc: com.google.firebase.firestore.DocumentReference,
        collectionName: String,
        floor: Long
    ): com.google.firebase.firestore.Query {
        val col = userDoc.collection(collectionName)
        return if (floor > 0L) col.whereGreaterThan("updatedAt", floor) else col
    }

    suspend fun pullDeltaRecordsFromFirestore(
        userId: String,
        lastSyncTimestamp: Long,
        forceFullPull: Boolean = false
    ): Long {
        if (isLocalOnlyDbModeEnabled()) return lastSyncTimestamp
        val userDoc = firestore.collection("users").document(userId)

        val prefs = context.syncDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()
        val needsB22FullReconcile = prefs[reconcileB22KeyForUser(userId)] != true
        val needsAttachmentsBackfill = prefs[attachmentsBackfillKeyForUser(userId)] != true
        val needsTemplatesBackfill = prefs[projectTemplatesBackfillKeyForUser(userId)] != true
        val needsProjectsReviewBackfill = prefs[projectsReviewBackfillKeyForUser(userId)] != true

        val activeTasksLocal = database.taskDao().getAllNonDeletedTasksList()
        val activeHeadingsLocal = database.headingDao().getAllNonDeletedHeadingsList()
        val activeProjectsLocal = database.projectDao().getAllNonDeletedProjectsList()
        val referencedProjIds = buildSet {
            activeTasksLocal.mapNotNullTo(this) { it.projectId?.trim()?.takeIf(String::isNotEmpty) }
            activeHeadingsLocal.mapNotNullTo(this) { it.projectId.trim().takeIf(String::isNotEmpty) }
        }
        val referencedAreaIds = buildSet {
            activeTasksLocal.mapNotNullTo(this) { it.areaId?.trim()?.takeIf(String::isNotEmpty) }
            activeProjectsLocal.mapNotNullTo(this) { it.areaId?.trim()?.takeIf(String::isNotEmpty) }
        }
        val referencedHeadingIds = buildSet {
            activeTasksLocal.mapNotNullTo(this) { it.headingId?.trim()?.takeIf(String::isNotEmpty) }
        }
        val hasOrphanProjectRef = referencedProjIds.any { database.projectDao().getById(it) == null }
        val hasOrphanAreaRef = referencedAreaIds.any { database.areaDao().getById(it) == null }
        val hasOrphanHeadingRef = referencedHeadingIds.any { database.headingDao().getById(it) == null }

        fun floorForCollection(col: String, forceZero: Boolean = false): Pair<Long, Long> {
            val prevCursor = if (forceFullPull || needsB22FullReconcile || forceZero) {
                0L
            } else {
                prefs[collectionCursorKeyForUser(userId, col)] ?: lastSyncTimestamp
            }
            return prevCursor to computeDeltaQueryFloor(prevCursor)
        }

        val (prevTags, tagFloor) = floorForCollection("tags")
        val (prevAreas, areaFloor) = floorForCollection("areas", hasOrphanAreaRef || hasOrphanProjectRef)
        val (prevProjects, projectFloor) = floorForCollection("projects", needsProjectsReviewBackfill || hasOrphanProjectRef)
        val (prevHeadings, headingFloor) = floorForCollection("headings", hasOrphanHeadingRef || hasOrphanProjectRef)
        val (prevTasks, taskFloor) = floorForCollection("tasks")
        val (prevChecklists, checklistFloor) = floorForCollection("checklist_items")
        val (prevAttachments, attachmentFloor) = floorForCollection("attachments", needsAttachmentsBackfill)
        val (prevTemplates, templateFloor) = floorForCollection("project_templates", needsTemplatesBackfill)

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
            val tagDef = async { queryCollectionSince(userDoc, "tags", tagFloor).get().await() }
            val areaDef = async { queryCollectionSince(userDoc, "areas", areaFloor).get().await() }
            val projectDef = async { queryCollectionSince(userDoc, "projects", projectFloor).get().await() }
            val headingDef = async { queryCollectionSince(userDoc, "headings", headingFloor).get().await() }
            val taskDef = async { queryCollectionSince(userDoc, "tasks", taskFloor).get().await() }
            val checklistDef = async { queryCollectionSince(userDoc, "checklist_items", checklistFloor).get().await() }
            val attachmentDef = async { queryCollectionSince(userDoc, "attachments", attachmentFloor).get().await() }
            val templateDef = async {
                runCatchingCancellable {
                    queryCollectionSince(userDoc, "project_templates", templateFloor).get().await()
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
        var maxTags = 0L
        var maxAreas = 0L
        var maxProjects = 0L
        var maxHeadings = 0L
        var maxTasks = 0L
        var maxChecklists = 0L
        var maxAttachments = 0L
        var maxTemplates = 0L
        var healedCompletedSeries = false
        var tagsMutated = false

        dbApplyMutex.withLock {
            for (doc in tagSnapshots.documents) {
                val map = normalizeFirestoreMapWithId(doc.id, doc.data)
                val dto = runCatching { TagFirestoreDto.fromFirestoreMap(map) }.getOrNull() ?: continue
                if (dto.id.isBlank()) continue
                val local = database.tagDao().getById(dto.id)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                    database.tagDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                    tagsMutated = true
                }
                if (clamped > maxTags) maxTags = clamped
            }

            for (doc in areaSnapshots.documents) {
                val map = normalizeFirestoreMapWithId(doc.id, doc.data)
                val dto = runCatching { AreaFirestoreDto.fromFirestoreMap(map) }.getOrNull() ?: continue
                if (dto.id.isBlank()) continue
                val local = database.areaDao().getById(dto.id)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                    database.areaDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                }
                if (clamped > maxAreas) maxAreas = clamped
            }

            for (doc in projectSnapshots.documents) {
                val map = normalizeFirestoreMapWithId(doc.id, doc.data)
                val dto = runCatching { ProjectFirestoreDto.fromFirestoreMap(map) }.getOrNull() ?: continue
                if (dto.id.isBlank()) continue
                applyRemoteProjectDto(dto, nowMillis)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxProjects) maxProjects = clamped
            }

            for (doc in headingSnapshots.documents) {
                val map = normalizeFirestoreMapWithId(doc.id, doc.data)
                val dto = runCatching { HeadingFirestoreDto.fromFirestoreMap(map) }.getOrNull() ?: continue
                if (dto.id.isBlank() || dto.projectId.isBlank()) continue
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                applyRemoteHeadingDto(dto, nowMillis)
                if (clamped > maxHeadings) maxHeadings = clamped
            }

            val parsedTaskDtos = taskSnapshots.documents.mapNotNull { doc ->
                val map = normalizeFirestoreMapWithId(doc.id, doc.data)
                runCatching { TaskFirestoreDto.fromFirestoreMap(map) }.getOrNull()?.takeIf { it.id.isNotBlank() }
            }
            val openSeriesIndex = if (parsedTaskDtos.isNotEmpty()) buildOpenSeriesIndex(parsedTaskDtos) else emptyMap()
            for (dto in parsedTaskDtos) {
                val (appliedEntity, healed) = applyRemoteTaskDtoLocked(dto, nowMillis, openSeriesIndex)
                if (appliedEntity != null) syncTaskReminderSchedule(appliedEntity)
                if (healed) healedCompletedSeries = true
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxTasks) maxTasks = clamped
            }

            for (doc in checklistSnapshots.documents) {
                val map = normalizeFirestoreMapWithId(doc.id, doc.data)
                val dto = runCatching { ChecklistItemFirestoreDto.fromFirestoreMap(map) }.getOrNull() ?: continue
                if (dto.id.isBlank() || dto.taskId.isBlank()) continue
                val local = database.checklistDao().getById(dto.id)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                    database.checklistDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                }
                if (clamped > maxChecklists) maxChecklists = clamped
            }

            if (templateSnapshots != null) {
                for (doc in templateSnapshots.documents) {
                    val map = normalizeFirestoreMapWithId(doc.id, doc.data)
                    val dto = runCatching { ProjectTemplateFirestoreDto.fromFirestoreMap(map) }.getOrNull() ?: continue
                    if (dto.id.isBlank()) continue
                    val local = database.projectTemplateDao().getById(dto.id)
                    val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                    if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                        database.projectTemplateDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                    }
                    if (clamped > maxTemplates) maxTemplates = clamped
                }
            }

            for (doc in attachmentSnapshots.documents) {
                val map = normalizeFirestoreMapWithId(doc.id, doc.data)
                val dto = runCatching { AttachmentFirestoreDto.fromFirestoreMap(map) }.getOrNull() ?: continue
                if (dto.id.isBlank()) continue
                applyRemoteAttachmentDto(dto, nowMillis)
                val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                if (clamped > maxAttachments) maxAttachments = clamped
            }

            val tagsRemapped = if (tagsMutated || needsB22FullReconcile) reconcileDuplicateTagsLocked(nowMillis) else false
            val deduped = if (parsedTaskDtos.isNotEmpty() || needsB22FullReconcile) {
                deduplicateOpenRecurringTasksLocked(nowMillis)
            } else {
                0
            }
            if (deduped > 0 || healedCompletedSeries || tagsRemapped) {
                triggerBackgroundSync()
            }
        }

        val nextTags = computeNextCollectionCursor(prevTags, maxTags, nowMillis)
        val nextAreas = computeNextCollectionCursor(prevAreas, maxAreas, nowMillis)
        val nextProjects = computeNextCollectionCursor(prevProjects, maxProjects, nowMillis)
        val nextHeadings = computeNextCollectionCursor(prevHeadings, maxHeadings, nowMillis)
        val nextTasks = computeNextCollectionCursor(prevTasks, maxTasks, nowMillis)
        val nextChecklists = computeNextCollectionCursor(prevChecklists, maxChecklists, nowMillis)
        val nextAttachments = computeNextCollectionCursor(prevAttachments, maxAttachments, nowMillis)
        val nextTemplates = if (templateSnapshots != null) {
            computeNextCollectionCursor(prevTemplates, maxTemplates, nowMillis)
        } else {
            prevTemplates
        }

        runCatchingCancellable {
            context.syncDataStore.edit { editPrefs ->
                editPrefs[collectionCursorKeyForUser(userId, "tags")] = nextTags
                editPrefs[collectionCursorKeyForUser(userId, "areas")] = nextAreas
                editPrefs[collectionCursorKeyForUser(userId, "projects")] = nextProjects
                editPrefs[collectionCursorKeyForUser(userId, "headings")] = nextHeadings
                editPrefs[collectionCursorKeyForUser(userId, "tasks")] = nextTasks
                editPrefs[collectionCursorKeyForUser(userId, "checklist_items")] = nextChecklists
                editPrefs[collectionCursorKeyForUser(userId, "attachments")] = nextAttachments
                if (templateSnapshots != null) {
                    editPrefs[collectionCursorKeyForUser(userId, "project_templates")] = nextTemplates
                }
                if (needsB22FullReconcile) {
                    editPrefs[reconcileB22KeyForUser(userId)] = true
                }
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

        val cursorsForWatermark = buildList {
            add(nextTags)
            add(nextAreas)
            add(nextProjects)
            add(nextHeadings)
            add(nextTasks)
            add(nextChecklists)
            add(nextAttachments)
            if (templateSnapshots != null) add(nextTemplates)
        }.filter { it > 0L }
        val lowWatermark = cursorsForWatermark.minOrNull() ?: maxOf(lastSyncTimestamp, nowMillis)
        return maxOf(lastSyncTimestamp, lowWatermark)
    }

    suspend fun attachRealtimeDeltaListeners(userId: String) {
        if (isLocalOnlyDbModeEnabled()) return
        runCatchingCancellable {
            synchronized(activeListeners) {
                activeListeners.forEach { runCatching { it.remove() } }
                activeListeners.clear()
            }

            val prefs = context.syncDataStore.data
                .catch { emit(emptyPreferences()) }
                .first()
            val fallbackSince = prefs[lastSyncKeyForUser(userId)] ?: 0L
            fun listenerFloor(col: String): Long {
                val cursor = prefs[collectionCursorKeyForUser(userId, col)] ?: fallbackSince
                return computeDeltaQueryFloor(cursor)
            }
            val userDoc = firestore.collection("users").document(userId)

            activeListeners += queryCollectionSince(userDoc, "tasks", listenerFloor("tasks"))
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            scheduleListenerReconnect(userId)
                            return@addSnapshotListener
                        }
                        if (snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                val nowMillis = System.currentTimeMillis()
                                val incomingDtos = snapshot.documentChanges.mapNotNull { change ->
                                    if (change.type == DocumentChange.Type.REMOVED ||
                                        change.document.metadata.hasPendingWrites()
                                    ) {
                                        return@mapNotNull null
                                    }
                                    val map = normalizeFirestoreMapWithId(change.document.id, change.document.data)
                                    runCatching { TaskFirestoreDto.fromFirestoreMap(map) }
                                        .getOrNull()
                                        ?.takeIf { it.id.isNotBlank() }
                                }
                                if (incomingDtos.isEmpty()) return@withLock
                                val openSeriesIndex = buildOpenSeriesIndex(incomingDtos)
                                var appliedAnyTask = false
                                var healedSeries = false
                                for (dto in incomingDtos) {
                                    runCatchingCancellable {
                                        val (applied, healed) = applyRemoteTaskDtoLocked(dto, nowMillis, openSeriesIndex)
                                        if (applied != null) {
                                            syncTaskReminderSchedule(applied)
                                            appliedAnyTask = true
                                        }
                                        if (healed) healedSeries = true
                                    }
                                }
                                val deduped = if (appliedAnyTask) deduplicateOpenRecurringTasksLocked(nowMillis) else 0
                                if (deduped > 0 || healedSeries) {
                                    triggerBackgroundSync()
                                }
                                _lastSyncedAt.value = nowMillis
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += queryCollectionSince(userDoc, "projects", listenerFloor("projects"))
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            scheduleListenerReconnect(userId)
                            return@addSnapshotListener
                        }
                        if (snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                val nowMillis = System.currentTimeMillis()
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    if (change.type == DocumentChange.Type.REMOVED ||
                                        change.document.metadata.hasPendingWrites()
                                    ) continue
                                    runCatchingCancellable {
                                        val map = normalizeFirestoreMapWithId(change.document.id, change.document.data)
                                        val dto = ProjectFirestoreDto.fromFirestoreMap(map) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank()) {
                                            applyRemoteProjectDto(dto, nowMillis)
                                            changed = true
                                        }
                                    }
                                }
                                if (changed) _lastSyncedAt.value = nowMillis
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += queryCollectionSince(userDoc, "areas", listenerFloor("areas"))
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            scheduleListenerReconnect(userId)
                            return@addSnapshotListener
                        }
                        if (snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                val nowMillis = System.currentTimeMillis()
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    if (change.type == DocumentChange.Type.REMOVED ||
                                        change.document.metadata.hasPendingWrites()
                                    ) continue
                                    runCatchingCancellable {
                                        val map = normalizeFirestoreMapWithId(change.document.id, change.document.data)
                                        val dto = AreaFirestoreDto.fromFirestoreMap(map) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank()) {
                                            val local = database.areaDao().getById(dto.id)
                                            val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                                            if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                                                database.areaDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                                            }
                                            changed = true
                                        }
                                    }
                                }
                                if (changed) _lastSyncedAt.value = nowMillis
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += queryCollectionSince(userDoc, "headings", listenerFloor("headings"))
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            scheduleListenerReconnect(userId)
                            return@addSnapshotListener
                        }
                        if (snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                val nowMillis = System.currentTimeMillis()
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    if (change.type == DocumentChange.Type.REMOVED ||
                                        change.document.metadata.hasPendingWrites()
                                    ) continue
                                    runCatchingCancellable {
                                        val map = normalizeFirestoreMapWithId(change.document.id, change.document.data)
                                        val dto = HeadingFirestoreDto.fromFirestoreMap(map) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank() && dto.projectId.isNotBlank()) {
                                            applyRemoteHeadingDto(dto, nowMillis)
                                            changed = true
                                        }
                                    }
                                }
                                if (changed) _lastSyncedAt.value = nowMillis
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += queryCollectionSince(userDoc, "checklist_items", listenerFloor("checklist_items"))
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            scheduleListenerReconnect(userId)
                            return@addSnapshotListener
                        }
                        if (snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                val nowMillis = System.currentTimeMillis()
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    if (change.type == DocumentChange.Type.REMOVED ||
                                        change.document.metadata.hasPendingWrites()
                                    ) continue
                                    runCatchingCancellable {
                                        val map = normalizeFirestoreMapWithId(change.document.id, change.document.data)
                                        val dto = ChecklistItemFirestoreDto.fromFirestoreMap(map) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank() && dto.taskId.isNotBlank()) {
                                            val local = database.checklistDao().getById(dto.id)
                                            val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                                            if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                                                database.checklistDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                                            }
                                            changed = true
                                        }
                                    }
                                }
                                if (changed) _lastSyncedAt.value = nowMillis
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += queryCollectionSince(userDoc, "tags", listenerFloor("tags"))
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            scheduleListenerReconnect(userId)
                            return@addSnapshotListener
                        }
                        if (snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                val nowMillis = System.currentTimeMillis()
                                var changed = false
                                var tagsMutated = false
                                for (change in snapshot.documentChanges) {
                                    if (change.type == DocumentChange.Type.REMOVED ||
                                        change.document.metadata.hasPendingWrites()
                                    ) continue
                                    runCatchingCancellable {
                                        val map = normalizeFirestoreMapWithId(change.document.id, change.document.data)
                                        val dto = TagFirestoreDto.fromFirestoreMap(map) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank()) {
                                            val local = database.tagDao().getById(dto.id)
                                            val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                                            if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                                                database.tagDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                                                tagsMutated = true
                                            }
                                            changed = true
                                        }
                                    }
                                }
                                if (tagsMutated && reconcileDuplicateTagsLocked(nowMillis)) {
                                    triggerBackgroundSync()
                                }
                                if (changed) _lastSyncedAt.value = nowMillis
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += queryCollectionSince(userDoc, "project_templates", listenerFloor("project_templates"))
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            scheduleListenerReconnect(userId)
                            return@addSnapshotListener
                        }
                        if (snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                val nowMillis = System.currentTimeMillis()
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    if (change.type == DocumentChange.Type.REMOVED ||
                                        change.document.metadata.hasPendingWrites()
                                    ) continue
                                    runCatchingCancellable {
                                        val map = normalizeFirestoreMapWithId(change.document.id, change.document.data)
                                        val dto = ProjectTemplateFirestoreDto.fromFirestoreMap(map) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank()) {
                                            val local = database.projectTemplateDao().getById(dto.id)
                                            val clamped = clampRemoteTimestamp(dto.updatedAt, nowMillis)
                                            if (shouldRemoteWin(local?.updatedAt, local?.isDirty == true, dto.updatedAt, nowMillis)) {
                                                database.projectTemplateDao().upsert(dto.toEntity().copy(updatedAt = clamped))
                                            }
                                            changed = true
                                        }
                                    }
                                }
                                if (changed) _lastSyncedAt.value = nowMillis
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += queryCollectionSince(userDoc, "attachments", listenerFloor("attachments"))
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            scheduleListenerReconnect(userId)
                            return@addSnapshotListener
                        }
                        if (snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            dbApplyMutex.withLock {
                                val nowMillis = System.currentTimeMillis()
                                var changed = false
                                for (change in snapshot.documentChanges) {
                                    if (change.type == DocumentChange.Type.REMOVED ||
                                        change.document.metadata.hasPendingWrites()
                                    ) continue
                                    runCatchingCancellable {
                                        val map = normalizeFirestoreMapWithId(change.document.id, change.document.data)
                                        val dto = AttachmentFirestoreDto.fromFirestoreMap(map) ?: return@runCatchingCancellable
                                        if (dto.id.isNotBlank()) {
                                            applyRemoteAttachmentDto(dto, nowMillis)
                                            changed = true
                                        }
                                    }
                                }
                                if (changed) _lastSyncedAt.value = nowMillis
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
                        if (docSnap.metadata.hasPendingWrites()) return@addSnapshotListener
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
                runCatchingCancellable { syncBidirectionalDeltas() }
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

    data class RecurringWinnerEnrichment(
        val winnerId: String,
        val areaId: String? = null,
        val headingId: String? = null,
        val notes: String? = null,
        val tagIds: List<String>? = null,
        val scheduledStartMinutes: Int? = null,
        val durationMinutes: Int? = null,
        val deadline: Long? = null,
        val reminderTime: Long? = null,
        val minUpdatedAt: Long
    )

    data class RecurringDeduplicationPlan(
        val loserIds: List<String>,
        val winnerEnrichments: List<RecurringWinnerEnrichment>
    )

    companion object {
        const val MAX_INLINE_SYNC_BYTES = 700_000L
        const val CLOCK_SKEW_OVERLAP_MS = 120_000L
        const val MAX_FUTURE_DRIFT_MS = 60_000L
        const val OFFLINE_COMMIT_RESTAMP_THRESHOLD_MS = 30_000L
        const val MAX_NTFY_CHUNK_BYTES = 3800
        const val FAST_PUSH_COALESCE_MS = 35L

        val SYNC_COLLECTION_NAMES = listOf(
            "tags",
            "areas",
            "projects",
            "headings",
            "tasks",
            "checklist_items",
            "attachments",
            "project_templates"
        )

        private val dedupJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun sha256Hex(input: String): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        fun deriveUidWakeChannelTopic(uid: String): String {
            val seed = "thingsbook-uid-v2:${uid.trim()}"
            return "tb_uid_${sha256Hex(seed).take(28)}"
        }

        fun deriveOAuthChannelTopicStatic(uid: String): String {
            val seed = "thingsbook-oauth-chan-v1:${uid.trim()}"
            return "tb_sync_${sha256Hex(seed).take(28)}"
        }

        fun deriveInstantChannelTopic(identifier: String): String {
            val norm = identifier.trim().lowercase(Locale.ROOT)
            val seed = "thingsbook-instant-chan-v1:$norm"
            return "tb_sync_${sha256Hex(seed).take(28)}"
        }

        fun deriveLegacySyncTopics(
            uid: String,
            email: String?,
            primaryTopic: String?
        ): List<String> {
            val topics = LinkedHashSet<String>()
            primaryTopic?.trim()?.takeIf { it.startsWith("tb_sync_") }?.let { topics.add(it) }
            val trimmedUid = uid.trim()
            if (trimmedUid.isNotEmpty()) {
                topics.add(deriveOAuthChannelTopicStatic(trimmedUid))
            }
            val normEmail = email?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (normEmail.isNotEmpty() && normEmail.contains("@")) {
                topics.add(deriveInstantChannelTopic(normEmail))
            }
            return topics.toList()
        }

        fun buildLegacyCompatibleNtfyChunks(
            fullPayload: CloudDeltaBatchPayload,
            codec: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
            maxChunks: Int = 3
        ): List<String> {
            val sanitized = fullPayload.copy(
                wakeOnly = false,
                attachments = emptyList(),
                projectTemplates = emptyList()
            )
            val hasLegacyEntities = sanitized.tags.isNotEmpty() ||
                sanitized.areas.isNotEmpty() ||
                sanitized.projects.isNotEmpty() ||
                sanitized.headings.isNotEmpty() ||
                sanitized.tasks.isNotEmpty() ||
                sanitized.checklistItems.isNotEmpty()
            if (!hasLegacyEntities) return emptyList()

            val fullJson = codec.encodeToString(sanitized)
            if (fullJson.toByteArray(StandardCharsets.UTF_8).size < MAX_NTFY_CHUNK_BYTES) {
                return listOf(fullJson)
            }

            val out = mutableListOf<String>()
            fun <T> appendSplitChunks(items: List<T>, makePayload: (List<T>) -> CloudDeltaBatchPayload) {
                if (items.isEmpty() || out.size >= maxChunks) return
                val encoded = codec.encodeToString(makePayload(items))
                if (encoded.toByteArray(StandardCharsets.UTF_8).size < MAX_NTFY_CHUNK_BYTES) {
                    out += encoded
                } else if (items.size > 1) {
                    val mid = items.size / 2
                    appendSplitChunks(items.subList(0, mid), makePayload)
                    appendSplitChunks(items.subList(mid, items.size), makePayload)
                }
            }

            val uid = sanitized.userId
            val dev = sanitized.deviceId
            val ts = sanitized.batchTimestamp
            appendSplitChunks(sanitized.tags) { CloudDeltaBatchPayload(userId = uid, deviceId = dev, batchTimestamp = ts, tags = it) }
            appendSplitChunks(sanitized.areas) { CloudDeltaBatchPayload(userId = uid, deviceId = dev, batchTimestamp = ts, areas = it) }
            appendSplitChunks(sanitized.projects) { CloudDeltaBatchPayload(userId = uid, deviceId = dev, batchTimestamp = ts, projects = it) }
            appendSplitChunks(sanitized.headings) { CloudDeltaBatchPayload(userId = uid, deviceId = dev, batchTimestamp = ts, headings = it) }
            appendSplitChunks(sanitized.tasks) { CloudDeltaBatchPayload(userId = uid, deviceId = dev, batchTimestamp = ts, tasks = it) }
            appendSplitChunks(sanitized.checklistItems) { CloudDeltaBatchPayload(userId = uid, deviceId = dev, batchTimestamp = ts, checklistItems = it) }
            return out.take(maxChunks)
        }

        fun formatDeterministicUuidFromSha256Hex(sha256HexDigest: String): String {
            val h = sha256HexDigest.trim().lowercase(Locale.ROOT)
            return "${h.substring(0, 8)}-${h.substring(8, 12)}-5${h.substring(13, 16)}-a${h.substring(17, 20)}-${h.substring(20, 32)}"
        }

        fun deriveRecurringNextTaskId(completedTaskId: String, nextWhenYyyyMmDd: String): String {
            val seed = "thingsbook-rec-next-v1:${completedTaskId.trim()}:${nextWhenYyyyMmDd.trim()}"
            return formatDeterministicUuidFromSha256Hex(sha256Hex(seed))
        }

        fun deriveRecurringChecklistItemId(nextTaskId: String, sourceChecklistId: String): String {
            val seed = "thingsbook-rec-chk-v1:${nextTaskId.trim()}:${sourceChecklistId.trim()}"
            return formatDeterministicUuidFromSha256Hex(sha256Hex(seed))
        }

        fun normalizeRecurringTitleKey(rawTitle: String): String =
            rawTitle.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

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
            val normTitle = normalizeRecurringTitleKey(entity.title)
            if (normTitle.isEmpty()) return null
            val rawRule = entity.recurrenceRuleJson ?: return null
            val rule = runCatching { dedupJson.decodeFromString<RecurrenceRule>(rawRule) }.getOrNull()
                ?: return null
            val ruleKey = canonicalizeRecurrenceRuleKey(rule) ?: return null
            val proj = entity.projectId?.trim().orEmpty()
            val area = entity.areaId?.trim().orEmpty()
            val normContainer = if (proj.isNotEmpty()) "P:$proj" else "A:$area"
            return "$normTitle\u001F$normContainer\u001F$ruleKey"
        }

        fun areSameRecurringSeries(a: TaskEntity, b: TaskEntity): Boolean {
            if (a.isDeleted || b.isDeleted || a.status != TaskStatus.OPEN || b.status != TaskStatus.OPEN) return false
            val normTitleA = normalizeRecurringTitleKey(a.title)
            if (normTitleA.isEmpty() || normTitleA != normalizeRecurringTitleKey(b.title)) return false
            val ruleA = a.recurrenceRuleJson?.let { runCatching { dedupJson.decodeFromString<RecurrenceRule>(it) }.getOrNull() }
            val ruleB = b.recurrenceRuleJson?.let { runCatching { dedupJson.decodeFromString<RecurrenceRule>(it) }.getOrNull() }
            val keyA = canonicalizeRecurrenceRuleKey(ruleA) ?: return false
            val keyB = canonicalizeRecurrenceRuleKey(ruleB) ?: return false
            if (keyA != keyB) return false
            val projA = a.projectId?.trim().orEmpty()
            val projB = b.projectId?.trim().orEmpty()
            if (projA != projB) return false
            if (projA.isNotEmpty()) return true
            val areaA = a.areaId?.trim().orEmpty()
            val areaB = b.areaId?.trim().orEmpty()
            return areaA.isEmpty() || areaB.isEmpty() || areaA == areaB
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

        fun planRecurringDeduplication(tasks: List<TaskEntity>): RecurringDeduplicationPlan {
            val coarseGroups = LinkedHashMap<String, MutableList<TaskEntity>>()
            for (task in tasks) {
                if (task.isDeleted || task.status != TaskStatus.OPEN) continue
                val normTitle = normalizeRecurringTitleKey(task.title)
                if (normTitle.isEmpty()) continue
                val rawRule = task.recurrenceRuleJson ?: continue
                val rule = runCatching { dedupJson.decodeFromString<RecurrenceRule>(rawRule) }.getOrNull() ?: continue
                val ruleKey = canonicalizeRecurrenceRuleKey(rule) ?: continue
                val proj = task.projectId?.trim().orEmpty()
                val coarseKey = "$normTitle\u001F$ruleKey\u001F$proj"
                coarseGroups.getOrPut(coarseKey) { mutableListOf() }.add(task)
            }

            val resolvedBuckets = mutableListOf<List<TaskEntity>>()
            for ((_, group) in coarseGroups) {
                if (group.size < 2) continue
                val proj = group.first().projectId?.trim().orEmpty()
                if (proj.isNotEmpty()) {
                    resolvedBuckets += group
                } else {
                    val distinctAreas = group.mapNotNull { it.areaId?.trim()?.takeIf(String::isNotEmpty) }.distinct()
                    if (distinctAreas.size <= 1) {
                        resolvedBuckets += group
                    } else {
                        val byArea = LinkedHashMap<String, MutableList<TaskEntity>>()
                        for (item in group) {
                            val areaKey = item.areaId?.trim().orEmpty()
                            byArea.getOrPut(areaKey) { mutableListOf() }.add(item)
                        }
                        for (sub in byArea.values) {
                            if (sub.size >= 2) resolvedBuckets += sub
                        }
                    }
                }
            }

            val loserIds = mutableListOf<String>()
            val enrichments = mutableListOf<RecurringWinnerEnrichment>()

            fun parseTagIds(jsonStr: String): List<String> =
                runCatching { dedupJson.decodeFromString<List<String>>(jsonStr) }
                    .getOrDefault(emptyList())
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

            for (bucket in resolvedBuckets) {
                if (bucket.size < 2) continue
                val sorted = bucket.sortedWith(::compareRecurringTaskPriority)
                val winner = sorted.first()
                val losers = sorted.drop(1)
                losers.forEach { loserIds += it.id }

                val proj = winner.projectId?.trim().orEmpty()
                val winnerArea = winner.areaId?.trim()?.takeIf { it.isNotEmpty() }
                val donorArea = if (proj.isEmpty() && winnerArea == null) {
                    losers.firstNotNullOfOrNull { it.areaId?.trim()?.takeIf(String::isNotEmpty) }
                } else {
                    null
                }
                val winnerHeading = winner.headingId?.trim()?.takeIf { it.isNotEmpty() }
                val donorHeading = if (proj.isNotEmpty() && winnerHeading == null) {
                    losers.firstNotNullOfOrNull { it.headingId?.trim()?.takeIf(String::isNotEmpty) }
                } else {
                    null
                }
                val donorNotes = if (winner.notes.isBlank()) {
                    losers.firstNotNullOfOrNull { it.notes.takeIf(String::isNotBlank) }
                } else {
                    null
                }
                val winnerTags = parseTagIds(winner.tagIdsJson)
                val unionTags = LinkedHashSet<String>(winnerTags).apply {
                    losers.forEach { addAll(parseTagIds(it.tagIdsJson)) }
                }.toList()
                val addedTags = unionTags.size > winnerTags.size

                val donorStartMin = if (winner.scheduledStartMinutes == null) {
                    losers.firstNotNullOfOrNull { it.scheduledStartMinutes }
                } else {
                    null
                }
                val donorDuration = if (winner.durationMinutes == null) {
                    losers.firstNotNullOfOrNull { it.durationMinutes }
                } else {
                    null
                }
                val donorDeadline = if (winner.deadline == null) {
                    losers.firstNotNullOfOrNull { it.deadline }
                } else {
                    null
                }
                val donorReminder = if (winner.reminderTime == null) {
                    losers.firstNotNullOfOrNull { it.reminderTime }
                } else {
                    null
                }

                if (donorArea != null ||
                    donorHeading != null ||
                    donorNotes != null ||
                    addedTags ||
                    donorStartMin != null ||
                    donorDuration != null ||
                    donorDeadline != null ||
                    donorReminder != null
                ) {
                    val maxGroupUpdatedAt = sorted.maxOf { it.updatedAt }
                    enrichments += RecurringWinnerEnrichment(
                        winnerId = winner.id,
                        areaId = donorArea,
                        headingId = donorHeading,
                        notes = donorNotes,
                        tagIds = if (addedTags) unionTags else null,
                        scheduledStartMinutes = donorStartMin,
                        durationMinutes = donorDuration,
                        deadline = donorDeadline,
                        reminderTime = donorReminder,
                        minUpdatedAt = maxGroupUpdatedAt + 1L
                    )
                }
            }

            return RecurringDeduplicationPlan(
                loserIds = loserIds,
                winnerEnrichments = enrichments
            )
        }

        fun selectRecurringDuplicateLoserIds(tasks: List<TaskEntity>): List<String> =
            planRecurringDeduplication(tasks).loserIds

        fun shouldRemoteWin(
            localUpdatedAt: Long?,
            localIsDirty: Boolean,
            remoteUpdatedAt: Long,
            nowMillis: Long = System.currentTimeMillis()
        ): Boolean {
            if (localUpdatedAt == null) return true
            val clampedRemote = clampRemoteTimestamp(remoteUpdatedAt, nowMillis)
            return if (localIsDirty) {
                clampedRemote > localUpdatedAt
            } else {
                clampedRemote >= localUpdatedAt
            }
        }

        fun computeNextCollectionCursor(
            previousCursor: Long,
            maxDocUpdatedAt: Long,
            pullStartedAt: Long
        ): Long {
            val clampedMax = clampRemoteTimestamp(maxDocUpdatedAt, pullStartedAt)
            return when {
                clampedMax > previousCursor -> clampedMax
                previousCursor == 0L -> pullStartedAt
                else -> previousCursor
            }
        }

        fun computeDeltaQueryFloor(lastSyncTimestamp: Long): Long =
            maxOf(0L, lastSyncTimestamp - CLOCK_SKEW_OVERLAP_MS)

        fun clampRemoteTimestamp(
            remoteUpdatedAt: Long,
            nowMillis: Long = System.currentTimeMillis()
        ): Long = minOf(remoteUpdatedAt, nowMillis + MAX_FUTURE_DRIFT_MS)

        fun normalizeFirestoreMapWithId(
            docId: String,
            rawData: Map<String, Any>?
        ): Map<String, Any>? {
            val base = rawData ?: return null
            val fieldId = (base["id"] as? String)?.trim().orEmpty()
            if (fieldId.isNotEmpty()) return base
            val fallbackId = docId.trim()
            if (fallbackId.isEmpty()) return base
            return base + ("id" to fallbackId)
        }

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

        data class GoogleWebBridgeTokens(
            val state: String,
            val idToken: String,
            val accessToken: String,
            val error: String? = null
        )

        private val webOAuthLock = Any()
        @Volatile
        private var pendingWebGoogleOAuthState: String? = null
        @Volatile
        private var pendingWebGoogleOAuthDeferred: CompletableDeferred<GoogleWebBridgeTokens>? = null

        internal fun registerPendingWebGoogleOAuth(
            state: String,
            deferred: CompletableDeferred<GoogleWebBridgeTokens>
        ) {
            synchronized(webOAuthLock) {
                pendingWebGoogleOAuthDeferred?.cancel()
                pendingWebGoogleOAuthState = state
                pendingWebGoogleOAuthDeferred = deferred
            }
        }

        internal fun clearPendingWebGoogleOAuth(state: String) {
            synchronized(webOAuthLock) {
                if (pendingWebGoogleOAuthState == state) {
                    pendingWebGoogleOAuthState = null
                    pendingWebGoogleOAuthDeferred = null
                }
            }
        }

        fun shouldFallbackToWebGoogleOAuth(err: Throwable): Boolean {
            var current: Throwable? = err
            var depth = 0
            while (current != null && depth < 6) {
                val msg = (current.message ?: "").lowercase(Locale.ROOT)
                if (
                    msg.contains("invalid_app_id") ||
                    msg.contains("invalid_cert_hash") ||
                    msg.contains("developer_error") ||
                    msg.contains("api_not_available") ||
                    msg.contains("service_missing") ||
                    msg.contains("service_disabled") ||
                    msg.contains("play_services")
                ) {
                    return true
                }
                current = current.cause
                depth += 1
            }
            return false
        }

        fun parseGoogleOAuthCallbackUri(rawUri: String): GoogleWebBridgeTokens? {
            val trimmed = rawUri.trim()
            if (trimmed.isEmpty()) return null
            return runCatching {
                val parsed = URI(trimmed)
                if (
                    !parsed.scheme.equals("thingsbook", ignoreCase = true) ||
                    !parsed.host.equals("oauth-callback", ignoreCase = true)
                ) {
                    return null
                }
                val params = linkedMapOf<String, String>()
                val rawQuery = parsed.rawQuery ?: ""
                for (pair in rawQuery.split('&')) {
                    if (pair.isBlank()) continue
                    val eqIdx = pair.indexOf('=')
                    val key = if (eqIdx >= 0) pair.substring(0, eqIdx) else pair
                    val rawVal = if (eqIdx >= 0) pair.substring(eqIdx + 1) else ""
                    val decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8.name())
                    val decodedVal = URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name())
                    params[decodedKey] = decodedVal
                }
                val state = params["state"]?.trim().orEmpty()
                if (state.isEmpty()) return null
                GoogleWebBridgeTokens(
                    state = state,
                    idToken = params["id_token"]?.trim().orEmpty(),
                    accessToken = params["access_token"]?.trim().orEmpty(),
                    error = params["error"]?.trim()?.takeIf { it.isNotEmpty() }
                )
            }.getOrNull()
        }

        fun handleIncomingGoogleOAuthUriString(rawUri: String): Boolean {
            val tokens = parseGoogleOAuthCallbackUri(rawUri) ?: return false
            synchronized(webOAuthLock) {
                val expected = pendingWebGoogleOAuthState
                val deferred = pendingWebGoogleOAuthDeferred
                if (expected != null && deferred != null && expected == tokens.state) {
                    pendingWebGoogleOAuthState = null
                    pendingWebGoogleOAuthDeferred = null
                    deferred.complete(tokens)
                    return true
                }
            }
            return false
        }
    }
}
