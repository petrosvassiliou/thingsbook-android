package com.culturedcode.things3.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.remote.dto.AreaFirestoreDto
import com.culturedcode.things3.data.remote.dto.ChecklistItemFirestoreDto
import com.culturedcode.things3.data.remote.dto.CloudDeltaBatchPayload
import com.culturedcode.things3.data.remote.dto.CloudUserAuthEnvelope
import com.culturedcode.things3.data.remote.dto.HeadingFirestoreDto
import com.culturedcode.things3.data.remote.dto.ProjectFirestoreDto
import com.culturedcode.things3.data.remote.dto.TagFirestoreDto
import com.culturedcode.things3.data.remote.dto.TaskFirestoreDto
import com.culturedcode.things3.domain.util.runCatchingCancellable
import com.google.firebase.auth.FirebaseAuth
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
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "things3_cloud_sync_prefs")

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
    private val firebaseAuth: FirebaseAuth
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w("SyncRepository", "Unhandled background sync exception", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val syncMutex = Mutex()
    private val activeListeners = java.util.Collections.synchronizedList(mutableListOf<ListenerRegistration>())
    private var realtimeCloudPollJob: Job? = null
    private var lanUdpListenerJob: Job? = null
    @Volatile
    private var activeUdpSocket: DatagramSocket? = null

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
            delay(800L)
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
        uiPrefs || settingsPrefs
    }.getOrDefault(false)

    fun triggerBackgroundSync() {
        if (isLocalOnlyDbModeEnabled()) {
            _syncStatus.value = CloudSyncStatus.OFFLINE
            return
        }
        scope.launch {
            syncBidirectionalDeltas()
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
        if (isLocalOnlyDbModeEnabled()) {
            _syncStatus.value = CloudSyncStatus.OFFLINE
            return
        }
        val prefs = context.syncDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()
        val userId = prefs[KEY_ACTIVE_USER_ID]
        val email = prefs[KEY_ACTIVE_USER_EMAIL]
        val channelTopic = prefs[KEY_ACTIVE_CHANNEL_TOPIC]

        if (email != null && email.endsWith("@thingsbook.app")) {
            signOutAccount()
            return
        }

        if (!userId.isNullOrBlank() && !channelTopic.isNullOrBlank()) {
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
                _activeUserIdentifier.value = fbEmail ?: fbUser.uid
                syncBidirectionalDeltas()
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
                        throw IllegalArgumentException(
                            "Falsches Passwort für $email. Klicke unten auf 'Passwort vergessen?', um es per E-Mail zurückzusetzen."
                        )
                    }
                    throw createErr
                }
            } else {
                throw signInErr
            }
        }
    }

    suspend fun registerAccount(rawEmail: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val email = rawEmail.trim().lowercase(Locale.ROOT)
            require(email.contains("@") && email.contains(".")) { "Bitte gib eine gültige E-Mail-Adresse ein." }
            require(password.length >= 6) { "Das Passwort muss mindestens 6 Zeichen lang sein." }

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
                    throw IllegalStateException("Dieses Konto existiert bereits. Bitte melde dich mit deinem Passwort an oder nutze 'Passwort vergessen?'.")
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

            context.syncDataStore.edit { prefs ->
                prefs[KEY_ACTIVE_USER_ID] = userId
                prefs[KEY_ACTIVE_USER_EMAIL] = email
                prefs[KEY_ACTIVE_CHANNEL_TOPIC] = channelTopic
                prefs[lastSyncKeyForUser(userId)] = 0L
            }
            _activeUserIdentifier.value = email

            if (!isLocalOnlyDbModeEnabled()) {
                if (isLiveFirebaseConfigured()) {
                    pullDeltaRecordsFromFirestore(userId, 0L)
                    pushDirtyRecordsToFirestore(userId)
                }
                pullDeltaRecordsFromBuiltInCloud(userId, channelTopic, 0L)
                pushDirtyRecordsToBuiltInCloud(userId, channelTopic)
                saveLastSyncTimestamp(userId, System.currentTimeMillis())
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
            require(email.contains("@") && email.contains(".")) { "Bitte gib eine gültige E-Mail-Adresse ein." }
            require(password.length >= 6) { "Das Passwort muss mindestens 6 Zeichen lang sein." }

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
                    throw IllegalArgumentException("Ungültiges Passwort für $email. Tipp: Nutze 'Passwort vergessen?'.")
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

            context.syncDataStore.edit { prefs ->
                prefs[KEY_ACTIVE_USER_ID] = userId
                prefs[KEY_ACTIVE_USER_EMAIL] = email
                prefs[KEY_ACTIVE_CHANNEL_TOPIC] = channelTopic
                prefs[lastSyncKeyForUser(userId)] = 0L
            }
            _activeUserIdentifier.value = email

            if (!isLocalOnlyDbModeEnabled()) {
                var maxPulled = 0L
                if (isLiveFirebaseConfigured()) {
                    maxPulled = maxOf(maxPulled, pullDeltaRecordsFromFirestore(userId, 0L))
                    pushDirtyRecordsToFirestore(userId)
                }
                maxPulled = maxOf(maxPulled, pullDeltaRecordsFromBuiltInCloud(userId, channelTopic, 0L))
                pushDirtyRecordsToBuiltInCloud(userId, channelTopic)
                saveLastSyncTimestamp(userId, maxOf(maxPulled, System.currentTimeMillis()))
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
                "Bitte gib oben zuerst deine E-Mail-Adresse ein und tippe dann auf 'Passwort zurücksetzen'."
            }
            if (isLiveFirebaseConfigured()) {
                firebaseAuth.sendPasswordResetEmail(email).await()
            }
            "E-Mail zum Zurücksetzen des Passworts wurde an $email gesendet. Bitte prüfe dein Postfach (auch den Spam-Ordner)."
        }
    }

    suspend fun signInInstantCloudAccount(preferredEmail: String? = null): Result<String> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val googleEmail = preferredEmail?.trim()?.lowercase(Locale.ROOT)
                ?.takeIf { it.contains("@") && it.contains(".") && !it.endsWith("@thingsbook.app") }
                ?: throw IllegalArgumentException(
                    "Bitte wähle dein Google-Konto im Android-Auswahldialog aus oder gib oben deine E-Mail-Adresse ein."
                )

            _syncStatus.value = CloudSyncStatus.SYNCING

            val generatedPassword = "TbCloud!${sha256Hex("tb-instant-v2:$googleEmail").take(14)}"

            val uid = if (isLiveFirebaseConfigured()) {
                try {
                    authenticateFirebaseEmailWithFallback(
                        email = googleEmail,
                        password = generatedPassword,
                        allowAutoCreateOnSignIn = true
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    throw IllegalArgumentException(
                        "Für $googleEmail wurde bereits ein eigenes Passwort vergeben. Bitte gib oben dein Passwort ein und tippe auf 'Mit E-Mail anmelden' (oder nutze 'Passwort vergessen?')."
                    )
                }
            } else {
                deriveUserId(googleEmail)
            }

            val channelTopic = "tb_sync_${sha256Hex("thingsbook-instant:$googleEmail").take(28)}"
            context.syncDataStore.edit { prefs ->
                prefs[KEY_ACTIVE_USER_ID] = uid
                prefs[KEY_ACTIVE_USER_EMAIL] = googleEmail
                prefs[KEY_ACTIVE_CHANNEL_TOPIC] = channelTopic
            }
            _activeUserIdentifier.value = googleEmail

            if (!isLocalOnlyDbModeEnabled()) {
                if (isLiveFirebaseConfigured()) {
                    pullDeltaRecordsFromFirestore(uid, 0L)
                    pushDirtyRecordsToFirestore(uid)
                }
                pullDeltaRecordsFromBuiltInCloud(uid, channelTopic, 0L)
                pushDirtyRecordsToBuiltInCloud(uid, channelTopic)
                saveLastSyncTimestamp(uid, System.currentTimeMillis())

                _syncStatus.value = CloudSyncStatus.SYNCED
                startRealtimeMultiDeviceSync(uid, channelTopic)
            } else {
                _syncStatus.value = CloudSyncStatus.OFFLINE
            }
            googleEmail
        }.onFailure { err ->
            Log.w("SyncRepository", "signInInstantCloudAccount failed", err)
            _syncStatus.value = CloudSyncStatus.OFFLINE
        }
    }

    suspend fun signOutAccount() {
        detachRealtimeDeltaListeners()
        runCatching { firebaseAuth.signOut() }
        runCatchingCancellable {
            context.syncDataStore.edit { prefs ->
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

    suspend fun getLastSyncTimestamp(userId: String): Long {
        return context.syncDataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[lastSyncKeyForUser(userId)] ?: 0L }
            .first()
    }

    private suspend fun saveLastSyncTimestamp(userId: String, timestamp: Long) {
        runCatchingCancellable {
            context.syncDataStore.edit { prefs ->
                prefs[lastSyncKeyForUser(userId)] = timestamp
            }
        }
        _lastSyncedAt.value = timestamp
    }

    suspend fun syncBidirectionalDeltas(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isLocalOnlyDbModeEnabled()) {
            _syncStatus.value = CloudSyncStatus.OFFLINE
            return@withContext Result.success(Unit)
        }
        syncMutex.withLock {
            runCatchingCancellable {
                if (isLocalOnlyDbModeEnabled()) {
                    _syncStatus.value = CloudSyncStatus.OFFLINE
                    return@runCatchingCancellable
                }
                val prefs = context.syncDataStore.data
                    .catch { emit(emptyPreferences()) }
                    .first()
                val userId = prefs[KEY_ACTIVE_USER_ID]
                    ?: runCatching { firebaseAuth.currentUser?.uid }.getOrNull()
                    ?: return@runCatchingCancellable
                val channelTopic = prefs[KEY_ACTIVE_CHANNEL_TOPIC] ?: "tb_sync_${sha256Hex(userId).take(28)}"

                _syncStatus.value = CloudSyncStatus.SYNCING

                val liveFb = isLiveFirebaseConfigured()
                var anyTransportSucceeded = false

                if (liveFb) {
                    val fbPushOk = runCatchingCancellable { pushDirtyRecordsToFirestore(userId) }.isSuccess
                    if (fbPushOk) anyTransportSucceeded = true
                }
                val cloudPushOk = pushDirtyRecordsToBuiltInCloud(userId, channelTopic)
                if (cloudPushOk) anyTransportSucceeded = true

                val lastSync = getLastSyncTimestamp(userId)
                var maxObservedTimestamp = lastSync
                if (liveFb) {
                    runCatchingCancellable { pullDeltaRecordsFromFirestore(userId, lastSync) }
                        .onSuccess { fbMax ->
                            anyTransportSucceeded = true
                            if (fbMax > maxObservedTimestamp) maxObservedTimestamp = fbMax
                        }
                }
                fetchDeltaBatchesResultFromBuiltInCloud(channelTopic).onSuccess { batches ->
                    anyTransportSucceeded = true
                    val localDeviceId = getOrCreateDeviceId()
                    for (batch in batches) {
                        if (batch.userId != userId) continue
                        if (batch.deviceId == localDeviceId && lastSync > 0L && batch.batchTimestamp <= lastSync) {
                            continue
                        }
                        val batchMax = applyDeltaBatchToRoom(batch, maxObservedTimestamp)
                        if (batchMax > maxObservedTimestamp) maxObservedTimestamp = batchMax
                    }
                }

                if (!anyTransportSucceeded) {
                    throw IOException("Cloud-Relay ist derzeit offline oder nicht erreichbar.")
                }

                if (maxObservedTimestamp > lastSync) {
                    saveLastSyncTimestamp(userId, maxObservedTimestamp)
                } else if (lastSync == 0L) {
                    saveLastSyncTimestamp(userId, System.currentTimeMillis())
                }

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

    private suspend fun pushDirtyRecordsToBuiltInCloud(userId: String, channelTopic: String): Boolean {
        if (isLocalOnlyDbModeEnabled()) return false

        val dirtyAreas = database.areaDao().getDirtyAreas()
        val dirtyProjects = database.projectDao().getDirtyProjects()
        val dirtyHeadings = database.headingDao().getDirtyHeadings()
        val dirtyTasks = database.taskDao().getDirtyTasks()
        val dirtyChecklists = database.checklistDao().getDirtyChecklistItems()
        val dirtyTags = database.tagDao().getDirtyTags()

        if (
            dirtyAreas.isEmpty() &&
            dirtyProjects.isEmpty() &&
            dirtyHeadings.isEmpty() &&
            dirtyTasks.isEmpty() &&
            dirtyChecklists.isEmpty() &&
            dirtyTags.isEmpty()
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
            checklistItems = dirtyChecklists.map { ChecklistItemFirestoreDto.fromEntity(it) }
        )

        val serializedFull = json.encodeToString(fullPayload)
        broadcastDeltaOverLanUdp(serializedFull)

        val payloadsToPost: List<String> = if (serializedFull.toByteArray(StandardCharsets.UTF_8).size <= 3800) {
            listOf(serializedFull)
        } else {
            buildList {
                if (fullPayload.tags.isNotEmpty() || fullPayload.areas.isNotEmpty() || fullPayload.projects.isNotEmpty() || fullPayload.headings.isNotEmpty()) {
                    add(
                        json.encodeToString(
                            CloudDeltaBatchPayload(
                                userId = userId,
                                deviceId = deviceId,
                                batchTimestamp = now,
                                tags = fullPayload.tags,
                                areas = fullPayload.areas,
                                projects = fullPayload.projects,
                                headings = fullPayload.headings
                            )
                        )
                    )
                }
                fullPayload.tasks.chunked(12).forEach { taskChunk ->
                    add(
                        json.encodeToString(
                            CloudDeltaBatchPayload(
                                userId = userId,
                                deviceId = deviceId,
                                batchTimestamp = now,
                                tasks = taskChunk
                            )
                        )
                    )
                }
                fullPayload.checklistItems.chunked(25).forEach { clChunk ->
                    add(
                        json.encodeToString(
                            CloudDeltaBatchPayload(
                                userId = userId,
                                deviceId = deviceId,
                                batchTimestamp = now,
                                checklistItems = clChunk
                            )
                        )
                    )
                }
            }
        }

        var allSucceeded = true
        for (body in payloadsToPost) {
            if (!postJsonToBuiltInCloud(channelTopic, body)) {
                allSucceeded = false
                break
            }
        }

        if (allSucceeded) {
            if (dirtyAreas.isNotEmpty()) database.areaDao().markSyncedSafe(dirtyAreas.map { it.id })
            if (dirtyProjects.isNotEmpty()) database.projectDao().markSyncedSafe(dirtyProjects.map { it.id })
            if (dirtyHeadings.isNotEmpty()) database.headingDao().markSyncedSafe(dirtyHeadings.map { it.id })
            if (dirtyTasks.isNotEmpty()) database.taskDao().markSyncedSafe(dirtyTasks.map { it.id })
            if (dirtyChecklists.isNotEmpty()) database.checklistDao().markSyncedSafe(dirtyChecklists.map { it.id })
            if (dirtyTags.isNotEmpty()) database.tagDao().markSyncedSafe(dirtyTags.map { it.id })
        }
        return allSucceeded
    }

    private suspend fun pullDeltaRecordsFromBuiltInCloud(
        userId: String,
        channelTopic: String,
        lastSyncTimestamp: Long
    ): Long {
        if (isLocalOnlyDbModeEnabled()) return lastSyncTimestamp
        var maxTimestamp = lastSyncTimestamp
        val localDeviceId = getOrCreateDeviceId()

        val batches = fetchDeltaBatchesFromBuiltInCloud(channelTopic)
        for (batch in batches) {
            if (batch.userId != userId) continue
            if (batch.deviceId == localDeviceId && lastSyncTimestamp > 0L && batch.batchTimestamp <= lastSyncTimestamp) {
                continue
            }
            val batchMax = applyDeltaBatchToRoom(batch, maxTimestamp)
            if (batchMax > maxTimestamp) maxTimestamp = batchMax
        }
        return maxTimestamp
    }

    private suspend fun applyDeltaBatchToRoom(
        batch: CloudDeltaBatchPayload,
        currentMaxTimestamp: Long
    ): Long {
        var maxTimestamp = currentMaxTimestamp

        database.withTransaction {
            for (dto in batch.tags) {
                if (dto.id.isBlank()) continue
                val local = database.tagDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.tagDao().upsert(dto.toEntity())
                }
                if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
            }

            for (dto in batch.areas) {
                if (dto.id.isBlank()) continue
                val local = database.areaDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.areaDao().upsert(dto.toEntity())
                }
                if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
            }

            for (dto in batch.projects) {
                if (dto.id.isBlank()) continue
                val local = database.projectDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.projectDao().upsertProjectWithTags(dto.toEntity(), dto.tagIds)
                }
                if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
            }

            for (dto in batch.headings) {
                if (dto.id.isBlank() || dto.projectId.isBlank()) continue
                val local = database.headingDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.headingDao().upsert(dto.toEntity())
                }
                if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
            }

            for (dto in batch.tasks) {
                if (dto.id.isBlank()) continue
                val local = database.taskDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.taskDao().upsertTaskWithTags(dto.toEntity(), dto.tagIds)
                }
                if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
            }

            for (dto in batch.checklistItems) {
                if (dto.id.isBlank() || dto.taskId.isBlank()) continue
                val local = database.checklistDao().getById(dto.id)
                if (local == null || dto.updatedAt >= local.updatedAt) {
                    database.checklistDao().upsert(dto.toEntity())
                }
                if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
            }
        }

        if (batch.batchTimestamp > maxTimestamp) {
            maxTimestamp = batch.batchTimestamp
        }
        return maxTimestamp
    }

    private fun postJsonToBuiltInCloud(topic: String, bodyJson: String): Boolean {
        return runCatching {
            val url = URL("https://ntfy.sh/$topic")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
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
                connectTimeout = 7000
                readTimeout = 7000
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

    private fun fetchDeltaBatchesResultFromBuiltInCloud(channelTopic: String): Result<List<CloudDeltaBatchPayload>> {
        return runCatching {
            val url = URL("https://ntfy.sh/$channelTopic/json?poll=1&since=all")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            try {
                val result = mutableListOf<CloudDeltaBatchPayload>()
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val raw = line?.trim().orEmpty()
                        if (raw.isEmpty()) continue
                        val streamLine = runCatching { json.decodeFromString<NtfyStreamLine>(raw) }.getOrNull()
                        if (streamLine != null && streamLine.event == "message" && streamLine.message.isNotBlank()) {
                            val batch = runCatching {
                                json.decodeFromString<CloudDeltaBatchPayload>(streamLine.message)
                            }.getOrNull()
                            if (batch != null && batch.userId.isNotBlank()) {
                                result += batch
                            }
                        }
                    }
                }
                result
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun fetchDeltaBatchesFromBuiltInCloud(channelTopic: String): List<CloudDeltaBatchPayload> =
        fetchDeltaBatchesResultFromBuiltInCloud(channelTopic).getOrDefault(emptyList())

    private fun broadcastDeltaOverLanUdp(serializedPayload: String) {
        if (isLocalOnlyDbModeEnabled()) return
        runCatching {
            val bytes = serializedPayload.toByteArray(StandardCharsets.UTF_8)
            if (bytes.size > 60000) return
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val packet = DatagramPacket(
                    bytes,
                    bytes.size,
                    InetAddress.getByName("255.255.255.255"),
                    45833
                )
                socket.send(packet)
            }
        }
    }

    private fun startRealtimeMultiDeviceSync(userId: String, channelTopic: String) {
        if (isLocalOnlyDbModeEnabled()) return
        detachRealtimeDeltaListeners()

        realtimeCloudPollJob = scope.launch {
            while (isActive) {
                delay(8000L)
                if (isLocalOnlyDbModeEnabled()) break
                syncBidirectionalDeltas()
            }
        }

        lanUdpListenerJob = scope.launch {
            runCatchingCancellable {
                val localDeviceId = getOrCreateDeviceId()
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 3000
                    bind(InetSocketAddress(45833))
                }
                activeUdpSocket = socket
                try {
                    val buffer = ByteArray(65507)
                    while (isActive && !isLocalOnlyDbModeEnabled()) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            continue
                        }
                        val text = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                        val batch = runCatching { json.decodeFromString<CloudDeltaBatchPayload>(text) }.getOrNull()
                        if (batch != null && batch.userId == userId && batch.deviceId != localDeviceId) {
                            val lastSync = getLastSyncTimestamp(userId)
                            val newMax = applyDeltaBatchToRoom(batch, lastSync)
                            if (newMax > lastSync) {
                                saveLastSyncTimestamp(userId, newMax)
                            }
                        }
                    }
                } finally {
                    runCatching { socket.close() }
                    if (activeUdpSocket === socket) {
                        activeUdpSocket = null
                    }
                }
            }.onFailure { err ->
                Log.w("SyncRepository", "LAN UDP listener stopped", err)
            }
        }

        if (isLiveFirebaseConfigured()) {
            scope.launch {
                runCatchingCancellable { attachRealtimeDeltaListeners(userId) }
            }
        }
    }

    suspend fun pushDirtyRecordsToFirestore(userId: String) {
        if (isLocalOnlyDbModeEnabled()) return
        val userDoc = firestore.collection("users").document(userId)

        val dirtyAreas = database.areaDao().getDirtyAreas()
        val dirtyProjects = database.projectDao().getDirtyProjects()
        val dirtyHeadings = database.headingDao().getDirtyHeadings()
        val dirtyTasks = database.taskDao().getDirtyTasks()
        val dirtyChecklists = database.checklistDao().getDirtyChecklistItems()
        val dirtyTags = database.tagDao().getDirtyTags()

        data class BatchOp(val collectionName: String, val docId: String, val dataMap: Map<String, Any?>)

        val allOps = buildList {
            dirtyAreas.forEach { add(BatchOp("areas", it.id, AreaFirestoreDto.fromEntity(it).toFirestoreMap())) }
            dirtyProjects.forEach { add(BatchOp("projects", it.id, ProjectFirestoreDto.fromEntity(it).toFirestoreMap())) }
            dirtyHeadings.forEach { add(BatchOp("headings", it.id, HeadingFirestoreDto.fromEntity(it).toFirestoreMap())) }
            dirtyTasks.forEach { add(BatchOp("tasks", it.id, TaskFirestoreDto.fromEntity(it).toFirestoreMap())) }
            dirtyChecklists.forEach { add(BatchOp("checklist_items", it.id, ChecklistItemFirestoreDto.fromEntity(it).toFirestoreMap())) }
            dirtyTags.forEach { add(BatchOp("tags", it.id, TagFirestoreDto.fromEntity(it).toFirestoreMap())) }
        }

        if (allOps.isEmpty()) return

        allOps.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { op ->
                val ref = userDoc.collection(op.collectionName).document(op.docId)
                batch.set(ref, op.dataMap, SetOptions.merge())
            }
            batch.commit().await()
        }

        if (dirtyAreas.isNotEmpty()) database.areaDao().markSyncedSafe(dirtyAreas.map { it.id })
        if (dirtyProjects.isNotEmpty()) database.projectDao().markSyncedSafe(dirtyProjects.map { it.id })
        if (dirtyHeadings.isNotEmpty()) database.headingDao().markSyncedSafe(dirtyHeadings.map { it.id })
        if (dirtyTasks.isNotEmpty()) database.taskDao().markSyncedSafe(dirtyTasks.map { it.id })
        if (dirtyChecklists.isNotEmpty()) database.checklistDao().markSyncedSafe(dirtyChecklists.map { it.id })
        if (dirtyTags.isNotEmpty()) database.tagDao().markSyncedSafe(dirtyTags.map { it.id })
    }

    suspend fun pullDeltaRecordsFromFirestore(userId: String, lastSyncTimestamp: Long): Long {
        if (isLocalOnlyDbModeEnabled()) return lastSyncTimestamp
        val userDoc = firestore.collection("users").document(userId)
        var maxTimestamp = lastSyncTimestamp

        val tagSnapshots = userDoc.collection("tags")
            .whereGreaterThan("updatedAt", lastSyncTimestamp)
            .get()
            .await()
        for (doc in tagSnapshots.documents) {
            val dto = runCatching { TagFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
            if (dto.id.isBlank()) continue
            val local = database.tagDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.tagDao().upsert(dto.toEntity())
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
        }

        val areaSnapshots = userDoc.collection("areas")
            .whereGreaterThan("updatedAt", lastSyncTimestamp)
            .get()
            .await()
        for (doc in areaSnapshots.documents) {
            val dto = runCatching { AreaFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
            if (dto.id.isBlank()) continue
            val local = database.areaDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.areaDao().upsert(dto.toEntity())
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
        }

        val projectSnapshots = userDoc.collection("projects")
            .whereGreaterThan("updatedAt", lastSyncTimestamp)
            .get()
            .await()
        for (doc in projectSnapshots.documents) {
            val dto = runCatching { ProjectFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
            if (dto.id.isBlank()) continue
            val local = database.projectDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.projectDao().upsertProjectWithTags(dto.toEntity(), dto.tagIds)
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
        }

        val headingSnapshots = userDoc.collection("headings")
            .whereGreaterThan("updatedAt", lastSyncTimestamp)
            .get()
            .await()
        for (doc in headingSnapshots.documents) {
            val dto = runCatching { HeadingFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
            if (dto.id.isBlank() || dto.projectId.isBlank()) continue
            val local = database.headingDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.headingDao().upsert(dto.toEntity())
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
        }

        val taskSnapshots = userDoc.collection("tasks")
            .whereGreaterThan("updatedAt", lastSyncTimestamp)
            .get()
            .await()
        for (doc in taskSnapshots.documents) {
            val dto = runCatching { TaskFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
            if (dto.id.isBlank()) continue
            val local = database.taskDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.taskDao().upsertTaskWithTags(dto.toEntity(), dto.tagIds)
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
        }

        val checklistSnapshots = userDoc.collection("checklist_items")
            .whereGreaterThan("updatedAt", lastSyncTimestamp)
            .get()
            .await()
        for (doc in checklistSnapshots.documents) {
            val dto = runCatching { ChecklistItemFirestoreDto.fromFirestoreMap(doc.data) }.getOrNull() ?: continue
            if (dto.id.isBlank() || dto.taskId.isBlank()) continue
            val local = database.checklistDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.checklistDao().upsert(dto.toEntity())
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
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
            val userDoc = firestore.collection("users").document(userId)

            activeListeners += userDoc.collection("tasks")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            for (change in snapshot.documentChanges) {
                                runCatchingCancellable {
                                    val dto = TaskFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                    if (dto.id.isNotBlank()) {
                                        val local = database.taskDao().getById(dto.id)
                                        if (local == null || dto.updatedAt >= local.updatedAt) {
                                            database.taskDao().upsertTaskWithTags(dto.toEntity(), dto.tagIds)
                                            saveLastSyncTimestamp(userId, maxOf(getLastSyncTimestamp(userId), dto.updatedAt))
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("projects")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            for (change in snapshot.documentChanges) {
                                runCatchingCancellable {
                                    val dto = ProjectFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                    if (dto.id.isNotBlank()) {
                                        val local = database.projectDao().getById(dto.id)
                                        if (local == null || dto.updatedAt >= local.updatedAt) {
                                            database.projectDao().upsertProjectWithTags(dto.toEntity(), dto.tagIds)
                                            saveLastSyncTimestamp(userId, maxOf(getLastSyncTimestamp(userId), dto.updatedAt))
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("areas")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            for (change in snapshot.documentChanges) {
                                runCatchingCancellable {
                                    val dto = AreaFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                    if (dto.id.isNotBlank()) {
                                        val local = database.areaDao().getById(dto.id)
                                        if (local == null || dto.updatedAt >= local.updatedAt) {
                                            database.areaDao().upsert(dto.toEntity())
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("headings")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            for (change in snapshot.documentChanges) {
                                runCatchingCancellable {
                                    val dto = HeadingFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                    if (dto.id.isNotBlank() && dto.projectId.isNotBlank()) {
                                        val local = database.headingDao().getById(dto.id)
                                        if (local == null || dto.updatedAt >= local.updatedAt) {
                                            database.headingDao().upsert(dto.toEntity())
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("checklist_items")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            for (change in snapshot.documentChanges) {
                                runCatchingCancellable {
                                    val dto = ChecklistItemFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                    if (dto.id.isNotBlank() && dto.taskId.isNotBlank()) {
                                        val local = database.checklistDao().getById(dto.id)
                                        if (local == null || dto.updatedAt >= local.updatedAt) {
                                            database.checklistDao().upsert(dto.toEntity())
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }

            activeListeners += userDoc.collection("tags")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null || snapshot == null || isLocalOnlyDbModeEnabled()) return@addSnapshotListener
                        scope.launch {
                            for (change in snapshot.documentChanges) {
                                runCatchingCancellable {
                                    val dto = TagFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatchingCancellable
                                    if (dto.id.isNotBlank()) {
                                        val local = database.tagDao().getById(dto.id)
                                        if (local == null || dto.updatedAt >= local.updatedAt) {
                                            database.tagDao().upsert(dto.toEntity())
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
        }
    }

    fun detachRealtimeDeltaListeners() {
        runCatching { realtimeCloudPollJob?.cancel() }
        realtimeCloudPollJob = null
        runCatching { lanUdpListenerJob?.cancel() }
        lanUdpListenerJob = null
        runCatching { activeUdpSocket?.close() }
        activeUdpSocket = null
        synchronized(activeListeners) {
            activeListeners.forEach { runCatching { it.remove() } }
            activeListeners.clear()
        }
    }
}
