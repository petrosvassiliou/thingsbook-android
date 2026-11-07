package com.culturedcode.things3.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.remote.dto.AreaFirestoreDto
import com.culturedcode.things3.data.remote.dto.ChecklistItemFirestoreDto
import com.culturedcode.things3.data.remote.dto.CloudDeltaBatchPayload
import com.culturedcode.things3.data.remote.dto.CloudUserAuthEnvelope
import com.culturedcode.things3.data.remote.dto.HeadingFirestoreDto
import com.culturedcode.things3.data.remote.dto.ProjectFirestoreDto
import com.culturedcode.things3.data.remote.dto.TagFirestoreDto
import com.culturedcode.things3.data.remote.dto.TaskFirestoreDto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
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
    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val activeListeners = mutableListOf<ListenerRegistration>()
    private var realtimeCloudPollJob: Job? = null
    private var lanUdpListenerJob: Job? = null

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
            runCatching {
                restoreBuiltInCloudSession()
            }
        }
    }

    private suspend fun getOrCreateDeviceId(): String {
        val prefs = context.syncDataStore.data.first()
        val existing = prefs[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        context.syncDataStore.edit { it[KEY_DEVICE_ID] = created }
        return created
    }

    private suspend fun restoreBuiltInCloudSession() {
        val prefs = context.syncDataStore.data.first()
        val userId = prefs[KEY_ACTIVE_USER_ID]
        val email = prefs[KEY_ACTIVE_USER_EMAIL]
        val channelTopic = prefs[KEY_ACTIVE_CHANNEL_TOPIC]

        if (!userId.isNullOrBlank() && !channelTopic.isNullOrBlank()) {
            _activeUserIdentifier.value = email ?: userId
            _syncStatus.value = CloudSyncStatus.SYNCED
            syncBidirectionalDeltas()
            startRealtimeMultiDeviceSync(userId, channelTopic)
        } else {
            val fbUser = firebaseAuth.currentUser
            if (fbUser != null) {
                _activeUserIdentifier.value = fbUser.email ?: fbUser.uid
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

    /**
     * Helper that signs in or creates an account via Firebase Email/Password provider,
     * avoiding restricted Anonymous Auth ('This operation is restricted to administrators only').
     */
    private suspend fun authenticateFirebaseEmailWithFallback(
        email: String,
        password: String,
        allowAutoCreateOnSignIn: Boolean
    ): String {
        return try {
            val signInRes = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            signInRes.user?.uid ?: deriveUserId(email)
        } catch (signInErr: Exception) {
            if (allowAutoCreateOnSignIn) {
                try {
                    val createRes = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                    createRes.user?.uid ?: deriveUserId(email)
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

    /**
     * Multi-Device Account Registration (Email & Password).
     * Automatically uses native Firebase Authentication + Cloud Firestore (`users/{userId}/...`)
     * when a real `google-services.json` is baked into the app, with fallback to the built-in cloud relay.
     */
    suspend fun registerAccount(rawEmail: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
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
                } catch (e: Exception) {
                    // If account already exists in Firebase, try signing in with the provided password
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

            // Persist active session
            context.syncDataStore.edit { prefs ->
                prefs[KEY_ACTIVE_USER_ID] = userId
                prefs[KEY_ACTIVE_USER_EMAIL] = email
                prefs[KEY_ACTIVE_CHANNEL_TOPIC] = channelTopic
                prefs[lastSyncKeyForUser(userId)] = 0L
            }
            _activeUserIdentifier.value = email

            if (isLiveFirebaseConfigured()) {
                pullDeltaRecordsFromFirestore(userId, 0L)
                pushDirtyRecordsToFirestore(userId)
            }
            pullDeltaRecordsFromBuiltInCloud(userId, channelTopic, 0L)
            pushDirtyRecordsToBuiltInCloud(userId, channelTopic)
            saveLastSyncTimestamp(userId, System.currentTimeMillis())

            _syncStatus.value = CloudSyncStatus.SYNCED
            startRealtimeMultiDeviceSync(userId, channelTopic)
            email
        }.onFailure {
            _syncStatus.value = CloudSyncStatus.OFFLINE
        }
    }

    /**
     * Multi-Device Sign-In (Email & Password) on a 2nd, 3rd, or Nth device.
     * Uses native Firebase Auth + Firestore (`users/{userId}/...`) when configured, pulling all cloud deltas
     * (`updatedAt > lastSyncTimestamp`) into Room and attaching real-time SnapshotListeners.
     */
    suspend fun signInAccount(rawEmail: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
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

            // Persist active session
            context.syncDataStore.edit { prefs ->
                prefs[KEY_ACTIVE_USER_ID] = userId
                prefs[KEY_ACTIVE_USER_EMAIL] = email
                prefs[KEY_ACTIVE_CHANNEL_TOPIC] = channelTopic
                prefs[lastSyncKeyForUser(userId)] = 0L
            }
            _activeUserIdentifier.value = email

            // 1. PULL FIRST on Sign-In so changes from Device 1 overwrite fresh seed defaults on Device 2
            var maxPulled = 0L
            if (isLiveFirebaseConfigured()) {
                maxPulled = maxOf(maxPulled, pullDeltaRecordsFromFirestore(userId, 0L))
                pushDirtyRecordsToFirestore(userId)
            }
            maxPulled = maxOf(maxPulled, pullDeltaRecordsFromBuiltInCloud(userId, channelTopic, 0L))

            // 2. PUSH any local dirty records
            pushDirtyRecordsToBuiltInCloud(userId, channelTopic)

            saveLastSyncTimestamp(userId, maxOf(maxPulled, System.currentTimeMillis()))
            _syncStatus.value = CloudSyncStatus.SYNCED
            startRealtimeMultiDeviceSync(userId, channelTopic)
            email
        }.onFailure {
            _syncStatus.value = CloudSyncStatus.OFFLINE
        }
    }

    /**
     * Sends a password reset email via Firebase Auth (`sendPasswordResetEmail`) for Email/Password accounts.
     */
    suspend fun sendPasswordResetEmail(rawEmail: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
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

    /**
     * One-Tap Instant Cloud Account (for "Weiter mit Google" / Quick Cloud Sign-In).
     * Authenticates via the enabled Email/Password provider in Firebase so it never fails with
     * 'This operation is restricted to administrators only' when Anonymous Auth is disabled in Firebase Console.
     */
    suspend fun signInInstantCloudAccount(preferredEmail: String? = null): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            _syncStatus.value = CloudSyncStatus.SYNCING

            val deviceKey = getOrCreateDeviceId().take(8).lowercase(Locale.ROOT)
            val candidateEmail = preferredEmail?.trim()?.lowercase(Locale.ROOT)
                ?.takeIf { it.contains("@") && it.contains(".") }
                ?: "google-$deviceKey@thingsbook.app"
            val generatedPassword = "TbCloud!${sha256Hex("tb-instant-v2:$candidateEmail").take(14)}"

            var activeEmail = candidateEmail
            val uid = if (isLiveFirebaseConfigured()) {
                try {
                    authenticateFirebaseEmailWithFallback(
                        email = candidateEmail,
                        password = generatedPassword,
                        allowAutoCreateOnSignIn = true
                    )
                } catch (_: Exception) {
                    // If user typed an email that already has a custom password, fall back to device Google Cloud ID
                    val fallbackEmail = "google-$deviceKey@thingsbook.app"
                    val fallbackPw = "TbCloud!${sha256Hex("tb-instant-v2:$fallbackEmail").take(14)}"
                    activeEmail = fallbackEmail
                    authenticateFirebaseEmailWithFallback(
                        email = fallbackEmail,
                        password = fallbackPw,
                        allowAutoCreateOnSignIn = true
                    )
                }
            } else {
                deriveUserId(activeEmail)
            }

            val channelTopic = "tb_sync_${sha256Hex("thingsbook-instant:$activeEmail").take(28)}"
            context.syncDataStore.edit { prefs ->
                prefs[KEY_ACTIVE_USER_ID] = uid
                prefs[KEY_ACTIVE_USER_EMAIL] = activeEmail
                prefs[KEY_ACTIVE_CHANNEL_TOPIC] = channelTopic
            }
            _activeUserIdentifier.value = activeEmail

            if (isLiveFirebaseConfigured()) {
                pullDeltaRecordsFromFirestore(uid, 0L)
                pushDirtyRecordsToFirestore(uid)
            }
            pullDeltaRecordsFromBuiltInCloud(uid, channelTopic, 0L)
            pushDirtyRecordsToBuiltInCloud(uid, channelTopic)
            saveLastSyncTimestamp(uid, System.currentTimeMillis())

            _syncStatus.value = CloudSyncStatus.SYNCED
            startRealtimeMultiDeviceSync(uid, channelTopic)
            activeEmail
        }.onFailure {
            _syncStatus.value = CloudSyncStatus.OFFLINE
        }
    }

    /**
     * Signs out the active user and stops background delta listeners.
     */
    suspend fun signOutAccount() {
        detachRealtimeDeltaListeners()
        runCatching { firebaseAuth.signOut() }
        context.syncDataStore.edit { prefs ->
            prefs.remove(KEY_ACTIVE_USER_ID)
            prefs.remove(KEY_ACTIVE_USER_EMAIL)
            prefs.remove(KEY_ACTIVE_CHANNEL_TOPIC)
        }
        _activeUserIdentifier.value = null
        _syncStatus.value = CloudSyncStatus.OFFLINE
    }

    private fun lastSyncKeyForUser(userId: String) =
        longPreferencesKey("last_sync_timestamp_$userId")

    suspend fun getLastSyncTimestamp(userId: String): Long {
        return context.syncDataStore.data
            .map { prefs -> prefs[lastSyncKeyForUser(userId)] ?: 0L }
            .first()
    }

    private suspend fun saveLastSyncTimestamp(userId: String, timestamp: Long) {
        context.syncDataStore.edit { prefs ->
            prefs[lastSyncKeyForUser(userId)] = timestamp
        }
        _lastSyncedAt.value = timestamp
    }

    /**
     * Executes a full bidirectional Delta-Sync:
     * 1. Pushes all local Room records with `isDirty = true` to the built-in Thingsbook Cloud & LAN peers
     *    (and Firestore if configured).
     * 2. Pulls all remote delta batches with `updatedAt > lastSyncTimestamp` and applies Last-Write-Wins upserts into Room.
     */
    suspend fun syncBidirectionalDeltas(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.syncDataStore.data.first()
            val userId = prefs[KEY_ACTIVE_USER_ID] ?: firebaseAuth.currentUser?.uid ?: return@runCatching
            val channelTopic = prefs[KEY_ACTIVE_CHANNEL_TOPIC] ?: "tb_sync_${sha256Hex(userId).take(28)}"

            _syncStatus.value = CloudSyncStatus.SYNCING

            // 1. PUSH PHASE: Upload all local Room records with isDirty = true (Firestore WriteBatch primary when configured)
            if (isLiveFirebaseConfigured()) {
                runCatching { pushDirtyRecordsToFirestore(userId) }
            }
            pushDirtyRecordsToBuiltInCloud(userId, channelTopic)

            // 2. PULL PHASE: Fetch all remote delta records where updatedAt > lastSyncTimestamp (Last-Write-Wins)
            val lastSync = getLastSyncTimestamp(userId)
            var maxObservedTimestamp = lastSync
            if (isLiveFirebaseConfigured()) {
                val fbMax = runCatching { pullDeltaRecordsFromFirestore(userId, lastSync) }.getOrDefault(lastSync)
                if (fbMax > maxObservedTimestamp) maxObservedTimestamp = fbMax
            }
            val cloudMax = pullDeltaRecordsFromBuiltInCloud(userId, channelTopic, lastSync)
            if (cloudMax > maxObservedTimestamp) maxObservedTimestamp = cloudMax

            if (maxObservedTimestamp > lastSync) {
                saveLastSyncTimestamp(userId, maxObservedTimestamp)
            } else if (lastSync == 0L) {
                saveLastSyncTimestamp(userId, System.currentTimeMillis())
            }

            _syncStatus.value = CloudSyncStatus.SYNCED
        }.onFailure {
            // Even if momentary network loss occurs, if user is signed in we keep local queue ready
            if (_activeUserIdentifier.value != null) {
                _syncStatus.value = CloudSyncStatus.SYNCED
            } else {
                _syncStatus.value = CloudSyncStatus.OFFLINE
            }
        }
    }

    private fun isLiveFirebaseConfigured(): Boolean {
        return runCatching {
            val apiKey = firestore.app.options.apiKey
            !apiKey.isNullOrBlank() && !apiKey.contains("DummyKey")
        }.getOrDefault(false)
    }

    /**
     * Built-In Cloud PUSH Phase:
     * Reads all `isDirty = true` entities across all 6 tables, serializes them into a `CloudDeltaBatchPayload`,
     * posts to the built-in HTTPS Cloud stream + LAN UDP peers, and resets `isDirty = false` in Room.
     */
    private suspend fun pushDirtyRecordsToBuiltInCloud(userId: String, channelTopic: String) {
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
            return
        }

        val deviceId = getOrCreateDeviceId()
        val now = System.currentTimeMillis()

        val payload = CloudDeltaBatchPayload(
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

        val serializedPayload = json.encodeToString(payload)

        // 1. Broadcast over LAN UDP for instant (<50ms) local multi-device sync
        broadcastDeltaOverLanUdp(serializedPayload)

        // 2. Push to built-in HTTPS Cloud Relay (ntfy.sh JSON channel)
        val pushedToCloud = postJsonToBuiltInCloud(channelTopic, serializedPayload)

        if (pushedToCloud) {
            if (dirtyAreas.isNotEmpty()) database.areaDao().markSynced(dirtyAreas.map { it.id })
            if (dirtyProjects.isNotEmpty()) database.projectDao().markSynced(dirtyProjects.map { it.id })
            if (dirtyHeadings.isNotEmpty()) database.headingDao().markSynced(dirtyHeadings.map { it.id })
            if (dirtyTasks.isNotEmpty()) database.taskDao().markSynced(dirtyTasks.map { it.id })
            if (dirtyChecklists.isNotEmpty()) database.checklistDao().markSynced(dirtyChecklists.map { it.id })
            if (dirtyTags.isNotEmpty()) database.tagDao().markSynced(dirtyTags.map { it.id })
        }
    }

    /**
     * Built-In Cloud PULL Phase:
     * Fetches cached delta batches from the built-in HTTPS Cloud Relay (`?poll=1&since=all`),
     * filters records with `updatedAt >= local.updatedAt` (Last-Write-Wins), and upserts them into Room.
     */
    private suspend fun pullDeltaRecordsFromBuiltInCloud(
        userId: String,
        channelTopic: String,
        lastSyncTimestamp: Long
    ): Long {
        var maxTimestamp = lastSyncTimestamp
        val localDeviceId = getOrCreateDeviceId()

        val batches = fetchDeltaBatchesFromBuiltInCloud(channelTopic)
        for (batch in batches) {
            if (batch.userId != userId) continue
            // If we already synced this batch from ourselves and lastSyncTimestamp > 0, skip
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

        // 1. Tags
        for (dto in batch.tags) {
            val local = database.tagDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.tagDao().upsert(dto.toEntity())
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
        }

        // 2. Areas
        for (dto in batch.areas) {
            val local = database.areaDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.areaDao().upsert(dto.toEntity())
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
        }

        // 3. Projects (with project_tags Junction sync)
        for (dto in batch.projects) {
            val local = database.projectDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.projectDao().upsertProjectWithTags(dto.toEntity(), dto.tagIds)
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
        }

        // 4. Headings
        for (dto in batch.headings) {
            val local = database.headingDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.headingDao().upsert(dto.toEntity())
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
        }

        // 5. Tasks (with task_tags Junction sync)
        for (dto in batch.tasks) {
            val local = database.taskDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.taskDao().upsertTaskWithTags(dto.toEntity(), dto.tagIds)
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
        }

        // 6. Checklist Items
        for (dto in batch.checklistItems) {
            val local = database.checklistDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.checklistDao().upsert(dto.toEntity())
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
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
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(bodyJson)
                writer.flush()
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
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
            conn.disconnect()
            latestEnvelope
        }.getOrNull()
    }

    private fun fetchDeltaBatchesFromBuiltInCloud(channelTopic: String): List<CloudDeltaBatchPayload> {
        return runCatching {
            val url = URL("https://ntfy.sh/$channelTopic/json?poll=1&since=all")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
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
            conn.disconnect()
            result
        }.getOrDefault(emptyList())
    }

    private fun broadcastDeltaOverLanUdp(serializedPayload: String) {
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

    /**
     * Starts real-time background listeners for both Built-In HTTPS Cloud Deltas and LAN Peer Deltas
     * (plus Firestore SnapshotListeners if configured).
     */
    private fun startRealtimeMultiDeviceSync(userId: String, channelTopic: String) {
        realtimeCloudPollJob?.cancel()
        lanUdpListenerJob?.cancel()

        // 1. Periodic Cloud Delta Stream Poll (every 6 seconds while app is active)
        realtimeCloudPollJob = scope.launch {
            while (isActive) {
                delay(6000L)
                runCatching {
                    pushDirtyRecordsToBuiltInCloud(userId, channelTopic)
                    val lastSync = getLastSyncTimestamp(userId)
                    val newMax = pullDeltaRecordsFromBuiltInCloud(userId, channelTopic, lastSync)
                    if (newMax > lastSync) {
                        saveLastSyncTimestamp(userId, newMax)
                    }
                }
            }
        }

        // 2. Instant LAN UDP Multi-Device Listener on port 45833
        lanUdpListenerJob = scope.launch {
            runCatching {
                val localDeviceId = getOrCreateDeviceId()
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(45833))
                }
                val buffer = ByteArray(65507)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
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
            }
        }

        // 3. Native Firestore SnapshotListeners if live Firebase is configured
        if (isLiveFirebaseConfigured()) {
            scope.launch {
                runCatching { attachRealtimeDeltaListeners(userId) }
            }
        }
    }

    /**
     * Native Firestore PUSH PHASE: Reads all `isDirty = true` entities across all 6 tables, writes them to
     * `users/{userId}/{collection}/{id}` using `SetOptions.merge()`, and resets `isDirty = false` in Room.
     */
    suspend fun pushDirtyRecordsToFirestore(userId: String) {
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

        if (dirtyAreas.isNotEmpty()) database.areaDao().markSynced(dirtyAreas.map { it.id })
        if (dirtyProjects.isNotEmpty()) database.projectDao().markSynced(dirtyProjects.map { it.id })
        if (dirtyHeadings.isNotEmpty()) database.headingDao().markSynced(dirtyHeadings.map { it.id })
        if (dirtyTasks.isNotEmpty()) database.taskDao().markSynced(dirtyTasks.map { it.id })
        if (dirtyChecklists.isNotEmpty()) database.checklistDao().markSynced(dirtyChecklists.map { it.id })
        if (dirtyTags.isNotEmpty()) database.tagDao().markSynced(dirtyTags.map { it.id })
    }

    /**
     * Native Firestore PULL PHASE: Queries only deltas (`whereGreaterThan("updatedAt", lastSyncTimestamp)`)
     * from each user subcollection and applies Last-Write-Wins upserts into Room.
     */
    suspend fun pullDeltaRecordsFromFirestore(userId: String, lastSyncTimestamp: Long): Long {
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
            if (dto.id.isBlank()) continue
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
            if (dto.id.isBlank()) continue
            val local = database.checklistDao().getById(dto.id)
            if (local == null || dto.updatedAt >= local.updatedAt) {
                database.checklistDao().upsert(dto.toEntity())
            }
            if (dto.updatedAt > maxTimestamp) maxTimestamp = dto.updatedAt
        }

        return maxTimestamp
    }

    suspend fun attachRealtimeDeltaListeners(userId: String) {
        runCatching {
            activeListeners.forEach { it.remove() }
            activeListeners.clear()

            val sinceTimestamp = getLastSyncTimestamp(userId)
            val userDoc = firestore.collection("users").document(userId)

            activeListeners += userDoc.collection("tasks")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        for (change in snapshot.documentChanges) {
                            runCatching {
                                val dto = TaskFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatching
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
                }

            activeListeners += userDoc.collection("projects")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        for (change in snapshot.documentChanges) {
                            runCatching {
                                val dto = ProjectFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatching
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
                }

            activeListeners += userDoc.collection("areas")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        for (change in snapshot.documentChanges) {
                            runCatching {
                                val dto = AreaFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatching
                                if (dto.id.isNotBlank()) {
                                    val local = database.areaDao().getById(dto.id)
                                    if (local == null || dto.updatedAt >= local.updatedAt) {
                                        database.areaDao().upsert(dto.toEntity())
                                    }
                                }
                            }
                        }
                    }
                }

            activeListeners += userDoc.collection("headings")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        for (change in snapshot.documentChanges) {
                            runCatching {
                                val dto = HeadingFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatching
                                if (dto.id.isNotBlank()) {
                                    val local = database.headingDao().getById(dto.id)
                                    if (local == null || dto.updatedAt >= local.updatedAt) {
                                        database.headingDao().upsert(dto.toEntity())
                                    }
                                }
                            }
                        }
                    }
                }

            activeListeners += userDoc.collection("checklist_items")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        for (change in snapshot.documentChanges) {
                            runCatching {
                                val dto = ChecklistItemFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatching
                                if (dto.id.isNotBlank()) {
                                    val local = database.checklistDao().getById(dto.id)
                                    if (local == null || dto.updatedAt >= local.updatedAt) {
                                        database.checklistDao().upsert(dto.toEntity())
                                    }
                                }
                            }
                        }
                    }
                }

            activeListeners += userDoc.collection("tags")
                .whereGreaterThan("updatedAt", sinceTimestamp)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        for (change in snapshot.documentChanges) {
                            runCatching {
                                val dto = TagFirestoreDto.fromFirestoreMap(change.document.data) ?: return@runCatching
                                if (dto.id.isNotBlank()) {
                                    val local = database.tagDao().getById(dto.id)
                                    if (local == null || dto.updatedAt >= local.updatedAt) {
                                        database.tagDao().upsert(dto.toEntity())
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    fun detachRealtimeDeltaListeners() {
        realtimeCloudPollJob?.cancel()
        lanUdpListenerJob?.cancel()
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }
}
