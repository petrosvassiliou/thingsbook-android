import Foundation
import AppKit
import CryptoKit
import Darwin

public struct FirebaseConfig: Codable, Equatable, Sendable {
    public var projectId: String
    public var apiKey: String
    public var appId: String

    public init(projectId: String, apiKey: String, appId: String) {
        self.projectId = projectId.trimmingCharacters(in: .whitespacesAndNewlines)
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        self.appId = appId.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public static let defaultProduction = FirebaseConfig(
        projectId: "thingsbook-prod",
        apiKey: "AIzaSyCUtSvssQOesHW-heLhNzNvykI1zCl7deY",
        appId: "1:116792699915:android:2e24f279bece36e1c82b0e"
    )

    public var isCustom: Bool {
        self != .defaultProduction
    }

    private static let storageKey = "thingsbook_macos_custom_firebase_v1"

    public static func loadActive(defaults: UserDefaults = .standard) -> FirebaseConfig {
        guard let data = defaults.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode(FirebaseConfig.self, from: data) else {
            return .defaultProduction
        }
        let normalized = FirebaseConfig(
            projectId: decoded.projectId,
            apiKey: decoded.apiKey,
            appId: decoded.appId
        )
        guard !normalized.projectId.isEmpty, !normalized.apiKey.isEmpty else {
            return .defaultProduction
        }
        return normalized
    }

    public static func saveCustom(_ config: FirebaseConfig?, defaults: UserDefaults = .standard) {
        if let config {
            let normalized = FirebaseConfig(
                projectId: config.projectId,
                apiKey: config.apiKey,
                appId: config.appId
            )
            if !normalized.projectId.isEmpty, !normalized.apiKey.isEmpty {
                if let data = try? JSONEncoder().encode(normalized) {
                    defaults.set(data, forKey: storageKey)
                }
                return
            }
        }
        defaults.removeObject(forKey: storageKey)
    }
}

public struct CloudSyncSnapshot: Equatable, Sendable {
    public var status: CloudSyncStatus
    public var userEmailOrId: String?
    public var userId: String?
    public var lastSyncedAt: Int64
    public var errorMessage: String?

    public init(
        status: CloudSyncStatus = .offline,
        userEmailOrId: String? = nil,
        userId: String? = nil,
        lastSyncedAt: Int64 = 0,
        errorMessage: String? = nil
    ) {
        self.status = status
        self.userEmailOrId = userEmailOrId
        self.userId = userId
        self.lastSyncedAt = lastSyncedAt
        self.errorMessage = errorMessage
    }
}

private struct PersistedAuthSession: Codable {
    var uid: String
    var email: String
    var idToken: String
    var refreshToken: String
    var expiresAtMillis: Int64
    var channelTopic: String
    var deviceId: String
}

public enum CloudSyncError: LocalizedError {
    case invalidCredentials(String)
    case networkFailure(String)

    public var errorDescription: String? {
        switch self {
        case .invalidCredentials(let msg): return msg
        case .networkFailure(let msg): return msg
        }
    }
}

/// Pure URLSession Firebase Auth + Firestore v1 + Secondary ntfy.sh Delta Sync engine
/// matching Android (`SyncRepository.kt`) & ChromeOS (`cloudSync.ts`).
public actor FirebaseCloudSyncService {
    private let database: ThingsSQLiteDatabase
    private let session: URLSession
    private let liveStreamSession: URLSession
    private var authSession: PersistedAuthSession?
    private var snapshot = CloudSyncSnapshot()
    private var onStateChange: (@Sendable (CloudSyncSnapshot) -> Void)?
    private var isSyncing = false
    private var pendingResyncRequested = false
    private var pendingForceFullPull = false
    private var pendingPullBeforePush = false
    private var isPushingFast = false
    private var pendingFastPush = false
    private var liveWakeStreamTask: Task<Void, Never>?
    private var liveWakeStreamUid: String?
    private var lastObservedRemotePulseAt: Int64 = 0
    private var lastFullFallbackPullMillis: Int64 = 0

    public static let maxInlineAttachmentBytes = 700_000
    public static let maxNtfyPayloadBytes = 3_800
    public static let offlineRestampThresholdMillis: Int64 = 30_000
    public static let syncCollectionNames: [String] = [
        "tags",
        "areas",
        "projects",
        "headings",
        "tasks",
        "checklist_items",
        "attachments",
        "project_templates"
    ]

    public static func attachmentBackfillDefaultsKey(uid: String) -> String {
        "thingsbook.sync.attBackfillV1.\(uid)"
    }
    public static func templateBackfillDefaultsKey(uid: String) -> String {
        "thingsbook.sync.tmplBackfillV1.\(uid)"
    }
    public static func projectReviewBackfillDefaultsKey(uid: String) -> String {
        "thingsbook.sync.projReviewBackfillV1.\(uid)"
    }
    public static func reconcileV222DefaultsKey(uid: String) -> String {
        "thingsbook.sync.reconcileV222.\(uid)"
    }
    public static func collectionCursorDefaultsKey(uid: String, collection: String) -> String {
        "thingsbook_macos_col_sync_ts_v2_\(uid)_\(collection)"
    }
    public static func deriveUidWakeTopic(uid: String) -> String {
        let trimmed = uid.trimmingCharacters(in: .whitespacesAndNewlines)
        let digest = sha256Hex("thingsbook-uid-v2:\(trimmed)")
        return "tb_uid_" + String(digest.prefix(28))
    }
    public static func deriveOAuthChannelTopic(uid: String) -> String {
        let trimmed = uid.trimmingCharacters(in: .whitespacesAndNewlines)
        let digest = sha256Hex("thingsbook-oauth:\(trimmed)")
        return "tb_sync_" + String(digest.prefix(28))
    }
    public static func deriveInstantChannelTopic(email: String) -> String {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let digest = sha256Hex("thingsbook-instant:\(trimmed)")
        return "tb_sync_" + String(digest.prefix(28))
    }
    public static func deriveLegacySyncTopics(
        uid: String,
        email: String?,
        primaryTopic: String?
    ) -> [String] {
        var topics: [String] = []
        func appendUnique(_ candidate: String?) {
            guard let raw = candidate?.trimmingCharacters(in: .whitespacesAndNewlines),
                  raw.hasPrefix("tb_sync_"),
                  !topics.contains(raw) else { return }
            topics.append(raw)
        }
        appendUnique(primaryTopic)
        let cleanUid = uid.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanUid.isEmpty {
            appendUnique(deriveOAuthChannelTopic(uid: cleanUid))
        }
        if let cleanEmail = email?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
           cleanEmail.contains("@") {
            appendUnique(deriveInstantChannelTopic(email: cleanEmail))
        }
        if !cleanUid.isEmpty {
            appendUnique("tb_sync_" + String(sha256Hex(cleanUid).prefix(28)))
        }
        return topics
    }

    private static let keychainAccount = "firebase_auth_session_v1"
    private static let lastSyncCursorPrefix = "thingsbook_macos_last_sync_ts_"
    private static let lastWallSyncPrefix = "thingsbook_macos_last_wall_sync_"
    /// 2-minute overlap window on incremental pulls so device clock skew never skips peer writes.
    private static let clockSkewOverlapMillis: Int64 = 120_000

    public init(database: ThingsSQLiteDatabase, session: URLSession = .shared) {
        self.database = database
        self.session = session
        let streamCfg = URLSessionConfiguration.ephemeral
        streamCfg.timeoutIntervalForRequest = 90
        streamCfg.timeoutIntervalForResource = 3600
        streamCfg.requestCachePolicy = .reloadIgnoringLocalCacheData
        self.liveStreamSession = URLSession(configuration: streamCfg)
    }

    public func setObserver(_ observer: @escaping @Sendable (CloudSyncSnapshot) -> Void) {
        self.onStateChange = observer
        observer(snapshot)
    }

    public func currentSnapshot() -> CloudSyncSnapshot {
        snapshot
    }

    public var isAuthenticated: Bool {
        authSession != nil
    }

    public func restoreSessionIfAvailable(localOnlyMode: Bool) async {
        guard let raw = KeychainStore.loadString(account: Self.keychainAccount),
              let data = raw.data(using: .utf8),
              let saved = try? JSONDecoder().decode(PersistedAuthSession.self, from: data) else {
            updateSnapshot(CloudSyncSnapshot(status: .offline))
            return
        }
        self.authSession = saved
        let lastWall = loadLastWallSyncTimestamp(uid: saved.uid)
        let fallbackCursor = loadLastSyncCursor(uid: saved.uid)
        updateSnapshot(
            CloudSyncSnapshot(
                status: localOnlyMode ? .offline : .syncing,
                userEmailOrId: saved.email,
                userId: saved.uid,
                lastSyncedAt: lastWall > 0 ? lastWall : fallbackCursor,
                errorMessage: nil
            )
        )
        if !localOnlyMode {
            ensureLiveWakeStreamRunning(localOnlyMode: false)
            // Reconcile full cloud state on launch so upgrades (v1.8 <-> v2.x) and offline edits never miss records
            await syncBidirectionalDeltas(forceFullPull: true, pullBeforePush: true, localOnlyMode: false)
        }
    }

    // MARK: - Authentication Flows (Email/Password, Instant Email, & Native Google OAuth)

    public func signInEmail(email rawEmail: String, password: String, mergeLocalTasks: Bool, localOnlyMode: Bool) async throws -> String {
        let email = rawEmail.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard email.contains("@"), password.count >= 6 else {
            throw CloudSyncError.invalidCredentials("Please enter a valid email address and password (min. 6 characters).")
        }
        let config = FirebaseConfig.loadActive()
        updateSnapshot(patchStatus: .syncing, error: nil)

        do {
            let authRes: AuthTokenResponse
            do {
                authRes = try await callAuthEndpoint(
                    endpoint: "accounts:signInWithPassword",
                    apiKey: config.apiKey,
                    body: ["email": email, "password": password, "returnSecureToken": true]
                )
            } catch let err as CloudSyncError {
                let raw = err.localizedDescription.uppercased()
                if raw.contains("INVALID_LOGIN_CREDENTIALS") || raw.contains("INVALID_PASSWORD") {
                    throw CloudSyncError.invalidCredentials(
                        "Incorrect password, or this account uses Google Sign-In. Click 'Continue with Google' below or reset your password."
                    )
                }
                throw err
            }
            let pwVerifier = Self.sha256Hex("thingsbook-pw-v1:\(email):\(password)")
            let topic = "tb_sync_" + String(Self.sha256Hex("thingsbook-chan-v1:\(email):\(pwVerifier)").prefix(28))
            try await finalizeLogin(authRes: authRes, email: email, topic: topic, mergeLocalTasks: mergeLocalTasks, localOnlyMode: localOnlyMode)
            return email
        } catch {
            updateSnapshot(patchStatus: .offline, error: error.localizedDescription)
            throw error
        }
    }

    public func registerEmail(email rawEmail: String, password: String, mergeLocalTasks: Bool, localOnlyMode: Bool) async throws -> String {
        let email = rawEmail.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard email.contains("@"), password.count >= 6 else {
            throw CloudSyncError.invalidCredentials("Please enter a valid email address and a password with at least 6 characters.")
        }
        let config = FirebaseConfig.loadActive()
        updateSnapshot(patchStatus: .syncing, error: nil)

        do {
            let authRes: AuthTokenResponse
            do {
                authRes = try await callAuthEndpoint(
                    endpoint: "accounts:signUp",
                    apiKey: config.apiKey,
                    body: ["email": email, "password": password, "returnSecureToken": true]
                )
            } catch let err as CloudSyncError {
                if err.localizedDescription.uppercased().contains("EMAIL_EXISTS") {
                    return try await signInEmail(email: email, password: password, mergeLocalTasks: mergeLocalTasks, localOnlyMode: localOnlyMode)
                }
                throw err
            }
            let pwVerifier = Self.sha256Hex("thingsbook-pw-v1:\(email):\(password)")
            let topic = "tb_sync_" + String(Self.sha256Hex("thingsbook-chan-v1:\(email):\(pwVerifier)").prefix(28))
            try await finalizeLogin(authRes: authRes, email: email, topic: topic, mergeLocalTasks: mergeLocalTasks, localOnlyMode: localOnlyMode)
            return email
        } catch {
            updateSnapshot(patchStatus: .offline, error: error.localizedDescription)
            throw error
        }
    }

    public func signInInstantEmail(email rawEmail: String, mergeLocalTasks: Bool, localOnlyMode: Bool) async throws -> String {
        let email = rawEmail.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard email.contains("@"), email.contains("."), !email.hasSuffix("@thingsbook.app") else {
            throw CloudSyncError.invalidCredentials("Please enter a valid email address for Instant Cloud Sync.")
        }
        let derivedHash = Self.sha256Hex("tb-instant-v2:\(email)")
        let derivedPassword = "TbCloud!\(derivedHash.prefix(14))"
        let config = FirebaseConfig.loadActive()
        updateSnapshot(patchStatus: .syncing, error: nil)

        do {
            let authRes: AuthTokenResponse
            do {
                authRes = try await callAuthEndpoint(
                    endpoint: "accounts:signInWithPassword",
                    apiKey: config.apiKey,
                    body: ["email": email, "password": derivedPassword, "returnSecureToken": true]
                )
            } catch {
                do {
                    authRes = try await callAuthEndpoint(
                        endpoint: "accounts:signUp",
                        apiKey: config.apiKey,
                        body: ["email": email, "password": derivedPassword, "returnSecureToken": true]
                    )
                } catch {
                    throw CloudSyncError.invalidCredentials(
                        "Account \(email) is linked with Google or has a custom password. Click 'Continue with Google' below, or enter your password above."
                    )
                }
            }
            let topic = Self.deriveInstantChannelTopic(email: email)
            try await finalizeLogin(authRes: authRes, email: email, topic: topic, mergeLocalTasks: mergeLocalTasks, localOnlyMode: localOnlyMode)
            return email
        } catch {
            updateSnapshot(patchStatus: .offline, error: error.localizedDescription)
            throw error
        }
    }

    /// Signs in with Google via secure localhost loopback callback bridged through Thingsbook's authorized Firebase Hosting domain,
    /// exchanging the Google credential via Firebase IdentityToolkit `accounts:signInWithIdp` for a native macOS session.
    public func signInWithGoogleBrowser(mergeLocalTasks: Bool, localOnlyMode: Bool) async throws -> String {
        let config = FirebaseConfig.loadActive()
        updateSnapshot(patchStatus: .syncing, error: nil)

        do {
            let stateNonce = UUID().uuidString.replacingOccurrences(of: "-", with: "")
            let loopback = try Self.createLoopbackServerSocket()
            defer { Darwin.close(loopback.fd) }

            let webHost = "https://\(config.projectId).web.app"
            guard var comps = URLComponents(string: "\(webHost)/") else {
                throw CloudSyncError.networkFailure("Invalid Firebase Hosting URL.")
            }
            comps.queryItems = [
                URLQueryItem(name: "macos_google_auth", value: "1"),
                URLQueryItem(name: "port", value: String(loopback.port)),
                URLQueryItem(name: "state", value: stateNonce)
            ]
            guard let authUrl = comps.url else {
                throw CloudSyncError.networkFailure("Could not construct Google Sign-In URL.")
            }

            await MainActor.run {
                _ = NSWorkspace.shared.open(authUrl)
            }

            let callbackParams = try await Self.awaitLoopbackOAuthCallback(
                serverFd: loopback.fd,
                expectedState: stateNonce,
                timeoutSeconds: 180
            )

            await MainActor.run {
                NSApplication.shared.activate(ignoringOtherApps: true)
            }

            let idToken = callbackParams["id_token"] ?? ""
            let accessToken = callbackParams["access_token"] ?? ""
            guard !idToken.isEmpty || !accessToken.isEmpty else {
                let errMsg = callbackParams["error"] ?? "Google Sign-In did not return a credential."
                throw CloudSyncError.invalidCredentials(errMsg)
            }

            var postParts: [String] = []
            if !idToken.isEmpty {
                postParts.append("id_token=\(Self.formUrlEncode(idToken))")
            }
            if !accessToken.isEmpty {
                postParts.append("access_token=\(Self.formUrlEncode(accessToken))")
            }
            postParts.append("providerId=google.com")

            let idpRes = try await callAuthEndpoint(
                endpoint: "accounts:signInWithIdp",
                apiKey: config.apiKey,
                body: [
                    "postBody": postParts.joined(separator: "&"),
                    "requestUri": webHost,
                    "returnIdpCredential": true,
                    "returnSecureToken": true
                ]
            )

            let resolvedEmail = (idpRes.email ?? callbackParams["email"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let displayEmail = resolvedEmail.isEmpty ? idpRes.localId : resolvedEmail
            let topic = Self.deriveOAuthChannelTopic(uid: idpRes.localId)

            try await finalizeLogin(
                authRes: idpRes,
                email: displayEmail,
                topic: topic,
                mergeLocalTasks: mergeLocalTasks,
                localOnlyMode: localOnlyMode
            )
            return displayEmail
        } catch {
            updateSnapshot(patchStatus: .offline, error: error.localizedDescription)
            throw error
        }
    }

    public func sendPasswordResetEmail(email rawEmail: String) async throws {
        let email = rawEmail.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard email.contains("@") else {
            throw CloudSyncError.invalidCredentials("Please enter your email address first.")
        }
        let config = FirebaseConfig.loadActive()
        guard let url = URL(string: "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=\(config.apiKey)") else {
            throw CloudSyncError.networkFailure("Invalid Firebase configuration URL.")
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: [
            "requestType": "PASSWORD_RESET",
            "email": email
        ])
        let (data, res) = try await session.data(for: req)
        if let http = res as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw CloudSyncError.invalidCredentials(extractFirebaseError(from: data) ?? "Password reset request failed.")
        }
    }

    public func signOut() {
        stopLiveWakeStream()
        authSession = nil
        pendingResyncRequested = false
        pendingForceFullPull = false
        pendingPullBeforePush = false
        pendingFastPush = false
        lastObservedRemotePulseAt = 0
        KeychainStore.delete(account: Self.keychainAccount)
        updateSnapshot(CloudSyncSnapshot(status: .offline, userEmailOrId: nil, userId: nil, lastSyncedAt: 0, errorMessage: nil))
    }

    private func finalizeLogin(
        authRes: AuthTokenResponse,
        email: String,
        topic: String,
        mergeLocalTasks: Bool,
        localOnlyMode: Bool
    ) async throws {
        if mergeLocalTasks {
            try database.purgeSampleDataLocally(preserveUsedContainers: true)
        } else {
            try database.purgeAllLocalDataBeforeAccountPull()
        }

        let deviceId = authSession?.deviceId ?? UUID().uuidString
        let expiresMillis = Int64(Date().timeIntervalSince1970 * 1000.0) + max(60, (Int64(authRes.expiresIn) ?? 3600) - 60) * 1000
        let newSession = PersistedAuthSession(
            uid: authRes.localId,
            email: email,
            idToken: authRes.idToken,
            refreshToken: authRes.refreshToken,
            expiresAtMillis: expiresMillis,
            channelTopic: topic,
            deviceId: deviceId
        )
        self.authSession = newSession
        persistAuthSession(newSession)
        saveLastSyncCursor(uid: authRes.localId, timestamp: 0)
        resetCollectionSyncCursors(uid: authRes.localId)
        saveLastWallSyncTimestamp(uid: authRes.localId, timestamp: 0)

        updateSnapshot(
            CloudSyncSnapshot(
                status: localOnlyMode ? .offline : .syncing,
                userEmailOrId: email,
                userId: authRes.localId,
                lastSyncedAt: 0,
                errorMessage: nil
            )
        )

        if !localOnlyMode {
            ensureLiveWakeStreamRunning(localOnlyMode: false)
            // Pull remote changes BEFORE pushing local dirty records on login (matching Android & ChromeOS)
            await syncBidirectionalDeltas(forceFullPull: true, pullBeforePush: true, localOnlyMode: false)
        }
    }

    /// Best-effort quick push of local dirty records used when the app is terminating.
    public func pushDirtyOnlyIfSignedIn(localOnlyMode: Bool) async {
        guard !localOnlyMode, let current = authSession else { return }
        do {
            let dirty = try database.fetchDirtyBatch()
            let hasAny = !dirty.tags.isEmpty || !dirty.areas.isEmpty || !dirty.projects.isEmpty
                || !dirty.headings.isEmpty || !dirty.tasks.isEmpty || !dirty.checklistItems.isEmpty
                || !dirty.attachments.isEmpty || !dirty.projectTemplates.isEmpty
            guard hasAny else { return }
            let validToken = try await validIdToken()
            let config = FirebaseConfig.loadActive()
            try await pushDirtyBatchToFirestore(
                dirty: dirty,
                uid: current.uid,
                deviceId: current.deviceId,
                idToken: validToken,
                projectId: config.projectId
            )
            await broadcastDirtyBatchToNtfyTopics(
                dirty: dirty,
                uid: current.uid,
                email: current.email,
                channelTopic: current.channelTopic,
                deviceId: current.deviceId
            )
        } catch {
            // Best-effort termination push; dirty flags remain intact on failure for next launch.
        }
    }

    // MARK: - Sub-200ms Fast Outbound Push & Live Multi-Topic Wake Stream

    public func pushDirtyFast(localOnlyMode: Bool) async {
        if localOnlyMode {
            stopLiveWakeStream()
            updateSnapshot(patchStatus: .offline, error: nil)
            return
        }
        guard let initialSession = authSession else { return }
        ensureLiveWakeStreamRunning(localOnlyMode: false)

        // If a full sync is already running, or we haven't completed an initial pull yet, or local dirty
        // records were queued offline (>30s old), run pull-before-push + restamping so offline edits never
        // lose to newer peer timestamps or overwrite newer cloud changes!
        let hasStaleOfflineEdits = (try? database.hasStaleOfflineDirtyRecords(olderThanMillis: Self.offlineRestampThresholdMillis)) ?? false
        if isSyncing || loadLastSyncCursor(uid: initialSession.uid) <= 0 || hasStaleOfflineEdits {
            await syncBidirectionalDeltas(forceFullPull: false, pullBeforePush: true, localOnlyMode: false)
            return
        }

        pendingFastPush = true
        guard !isPushingFast else { return }
        isPushingFast = true
        defer { isPushingFast = false }

        while pendingFastPush {
            pendingFastPush = false
            guard !localOnlyMode, let current = authSession else { break }
            if isSyncing {
                await syncBidirectionalDeltas(forceFullPull: false, pullBeforePush: true, localOnlyMode: false)
                break
            }
            do {
                let dirty = try database.fetchDirtyBatch()
                let hasAny = !dirty.tags.isEmpty || !dirty.areas.isEmpty || !dirty.projects.isEmpty
                    || !dirty.headings.isEmpty || !dirty.tasks.isEmpty || !dirty.checklistItems.isEmpty
                    || !dirty.attachments.isEmpty || !dirty.projectTemplates.isEmpty
                guard hasAny else { continue }

                let validToken = try await validIdToken()
                let config = FirebaseConfig.loadActive()
                try await pushDirtyBatchToFirestore(
                    dirty: dirty,
                    uid: current.uid,
                    deviceId: current.deviceId,
                    idToken: validToken,
                    projectId: config.projectId
                )

                let now = Int64(Date().timeIntervalSince1970 * 1000.0)
                saveLastWallSyncTimestamp(uid: current.uid, timestamp: now)
                updateSnapshot(
                    CloudSyncSnapshot(
                        status: .synced,
                        userEmailOrId: current.email,
                        userId: current.uid,
                        lastSyncedAt: now,
                        errorMessage: nil
                    )
                )

                let uidCopy = current.uid
                let emailCopy = current.email
                let topicCopy = current.channelTopic
                let deviceCopy = current.deviceId
                Task {
                    await self.broadcastDirtyBatchToNtfyTopics(
                        dirty: dirty,
                        uid: uidCopy,
                        email: emailCopy,
                        channelTopic: topicCopy,
                        deviceId: deviceCopy
                    )
                }
            } catch {
                // Fall back to secondary relay if primary Firestore is temporarily unreachable; dirty flags stay set
                if let current = authSession, let dirty = try? database.fetchDirtyBatch() {
                    await broadcastDirtyBatchToNtfyTopics(
                        dirty: dirty,
                        uid: current.uid,
                        email: current.email,
                        channelTopic: current.channelTopic,
                        deviceId: current.deviceId
                    )
                }
            }
        }
    }

    public func ensureLiveWakeStreamRunning(localOnlyMode: Bool) {
        if localOnlyMode {
            stopLiveWakeStream()
            return
        }
        guard let current = authSession else {
            stopLiveWakeStream()
            return
        }
        if liveWakeStreamTask != nil, liveWakeStreamUid == current.uid {
            return
        }
        stopLiveWakeStream()
        let targetUid = current.uid
        var allTopics: [String] = []
        let uidWakeTopic = Self.deriveUidWakeTopic(uid: targetUid)
        if !uidWakeTopic.isEmpty {
            allTopics.append(uidWakeTopic)
        }
        for legacyTopic in Self.deriveLegacySyncTopics(
            uid: targetUid,
            email: current.email,
            primaryTopic: current.channelTopic
        ) where !allTopics.contains(legacyTopic) {
            allTopics.append(legacyTopic)
        }
        let joinedTopics = allTopics.joined(separator: ",")
        guard !joinedTopics.isEmpty else { return }
        liveWakeStreamUid = targetUid

        liveWakeStreamTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { break }
                let activeSession = await self.currentAuthSessionForUid(targetUid)
                guard let activeSession else { break }

                if let url = URL(string: "https://ntfy.sh/\(joinedTopics)/json?since=none") {
                    var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 90)
                    req.setValue("application/x-ndjson", forHTTPHeaderField: "Accept")
                    do {
                        let (asyncBytes, response) = try await self.liveStreamSession.bytes(for: req)
                        if let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) {
                            for try await line in asyncBytes.lines {
                                if Task.isCancelled { break }
                                await self.handleLiveNtfyEnvelopeLine(
                                    line,
                                    expectedUid: targetUid,
                                    localDeviceId: activeSession.deviceId
                                )
                            }
                        }
                    } catch {
                        // Stream closed or timed out after idle keepalive window; reconnect after brief backoff
                    }
                }

                if Task.isCancelled { break }
                try? await Task.sleep(nanoseconds: 1_500_000_000)
            }
        }
    }

    public func stopLiveWakeStream() {
        liveWakeStreamTask?.cancel()
        liveWakeStreamTask = nil
        liveWakeStreamUid = nil
    }

    private func currentAuthSessionForUid(_ expectedUid: String) -> PersistedAuthSession? {
        guard let s = authSession, s.uid == expectedUid else { return nil }
        return s
    }

    private func handleLiveNtfyEnvelopeLine(_ rawLine: String, expectedUid: String, localDeviceId: String) async {
        let trimmed = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              let lineData = trimmed.data(using: .utf8),
              let env = try? JSONSerialization.jsonObject(with: lineData) as? [String: Any],
              env["event"] as? String == "message",
              let msgStr = env["message"] as? String,
              let msgData = msgStr.data(using: .utf8),
              let batch = try? JSONSerialization.jsonObject(with: msgData) as? [String: Any],
              batch["userId"] as? String == expectedUid else { return }

        if let senderDevice = batch["deviceId"] as? String, !senderDevice.isEmpty, senderDevice == localDeviceId {
            return
        }

        if batch["wakeOnly"] as? Bool == true {
            await pullIncrementalDeltasOnly(localOnlyMode: false)
            return
        }

        let tags = (batch["tags"] as? [[String: Any]] ?? []).compactMap(Self.parseTag)
        let areas = (batch["areas"] as? [[String: Any]] ?? []).compactMap(Self.parseArea)
        var omittedProjectV30Ids = Set<String>()
        let projects = (batch["projects"] as? [[String: Any]] ?? []).compactMap { m -> Project? in
            guard let parsed = Self.parseProjectWithHints(m) else { return nil }
            if parsed.omittedV30Fields {
                omittedProjectV30Ids.insert(parsed.project.id)
            }
            return parsed.project
        }
        var omittedHeadingStartDateIds = Set<String>()
        var omittedHeadingEndDateIds = Set<String>()
        let headings = (batch["headings"] as? [[String: Any]] ?? []).compactMap { m -> Heading? in
            guard let parsed = Self.parseHeadingWithHints(m) else { return nil }
            if parsed.omittedStartDate {
                omittedHeadingStartDateIds.insert(parsed.heading.id)
            }
            if parsed.omittedEndDate {
                omittedHeadingEndDateIds.insert(parsed.heading.id)
            }
            return parsed.heading
        }
        var taskTags: [String: [String]] = [:]
        var omittedTimeblockTaskIds = Set<String>()
        var omittedPinTaskIds = Set<String>()
        var omittedV30TaskIds = Set<String>()
        let tasks = (batch["tasks"] as? [[String: Any]] ?? []).compactMap { m -> TaskItem? in
            guard let parsed = Self.parseTaskWithHints(m) else { return nil }
            taskTags[parsed.task.id] = parsed.tagIds
            if parsed.omittedTimeblock {
                omittedTimeblockTaskIds.insert(parsed.task.id)
            }
            if parsed.omittedPin {
                omittedPinTaskIds.insert(parsed.task.id)
            }
            if parsed.omittedV30Fields {
                omittedV30TaskIds.insert(parsed.task.id)
            }
            return parsed.task
        }
        let checklists = (batch["checklistItems"] as? [[String: Any]] ?? []).compactMap(Self.parseChecklist)
        let attachments = (batch["attachments"] as? [[String: Any]] ?? []).compactMap(Self.parseAttachment)
        let projectTemplates = (batch["projectTemplates"] as? [[String: Any]] ?? []).compactMap(Self.parseProjectTemplate)

        let hasAttachments = !attachments.isEmpty
        if (try? database.applyRemoteDeltas(
            RemoteDeltaBatch(
                tags: tags,
                areas: areas,
                projects: projects,
                projectOmittedV30Ids: omittedProjectV30Ids,
                headings: headings,
                headingOmittedStartDateIds: omittedHeadingStartDateIds,
                headingOmittedEndDateIds: omittedHeadingEndDateIds,
                tasks: tasks,
                taskTagIdsByTaskId: taskTags,
                taskOmittedTimeblockTaskIds: omittedTimeblockTaskIds,
                taskOmittedPinTaskIds: omittedPinTaskIds,
                taskOmittedV30TaskIds: omittedV30TaskIds,
                checklistItems: checklists,
                attachments: attachments,
                projectTemplates: projectTemplates
            )
        )) != nil {
            // Do NOT advance Firestore pull cursor on partial ntfy wake messages; only update wall clock!
            let now = Int64(Date().timeIntervalSince1970 * 1000.0)
            saveLastWallSyncTimestamp(uid: expectedUid, timestamp: now)
            if let current = authSession, current.uid == expectedUid {
                updateSnapshot(
                    CloudSyncSnapshot(
                        status: .synced,
                        userEmailOrId: current.email,
                        userId: current.uid,
                        lastSyncedAt: now,
                        errorMessage: nil
                    )
                )
            }
        }

        if hasAttachments {
            // Pull binary attachment payload from Firestore since ntfy carries compact metadata only
            await pullIncrementalDeltasOnly(localOnlyMode: false)
        }
    }

    @discardableResult
    public func pullIncrementalDeltasOnly(localOnlyMode: Bool) async -> Bool {
        guard !localOnlyMode, authSession != nil else { return false }
        guard !isSyncing else {
            pendingResyncRequested = true
            return false
        }
        isSyncing = true
        var succeeded = false
        var needsFullFollowUp = false
        var followUpForceFull = false

        while true {
            pendingResyncRequested = false
            guard !localOnlyMode, let current = authSession else { break }

            do {
                let validToken = try await validIdToken()
                let config = FirebaseConfig.loadActive()
                let cursor = loadLastSyncCursor(uid: current.uid)
                let safeQuerySince = max(0, cursor - Self.clockSkewOverlapMillis)
                let firestoreMax = try await pullDeltasFromFirestore(
                    uid: current.uid,
                    idToken: validToken,
                    projectId: config.projectId,
                    sinceTimestamp: safeQuerySince
                )
                _ = try? database.performMidnightRollover()
                let now = Int64(Date().timeIntervalSince1970 * 1000.0)
                if firestoreMax > cursor {
                    saveLastSyncCursor(uid: current.uid, timestamp: min(firestoreMax, now + 60_000))
                } else if cursor == 0 {
                    saveLastSyncCursor(uid: current.uid, timestamp: now)
                }
                saveLastWallSyncTimestamp(uid: current.uid, timestamp: now)
                lastFullFallbackPullMillis = now
                succeeded = true
                updateSnapshot(
                    CloudSyncSnapshot(
                        status: .synced,
                        userEmailOrId: current.email,
                        userId: current.uid,
                        lastSyncedAt: now,
                        errorMessage: nil
                    )
                )
            } catch {
                // Leave existing cursor intact on transient network hiccup
                break
            }

            if pendingForceFullPull || pendingPullBeforePush {
                needsFullFollowUp = true
                followUpForceFull = pendingForceFullPull
                pendingForceFullPull = false
                pendingPullBeforePush = false
                break
            }
            if pendingResyncRequested {
                continue
            }
            break
        }

        isSyncing = false
        if needsFullFollowUp && !localOnlyMode {
            await syncBidirectionalDeltas(forceFullPull: followUpForceFull, pullBeforePush: true, localOnlyMode: false)
        }
        return succeeded
    }

    /// Lightweight single-document check on `/users/{uid}/sync_meta/pulse` so active macOS windows
    /// detect remote edits in ~1-2s even on networks that block ntfy.sh streaming.
    public func checkRemotePulseAndSyncIfNeeded(localOnlyMode: Bool) async {
        if localOnlyMode {
            stopLiveWakeStream()
            updateSnapshot(patchStatus: .offline, error: nil)
            return
        }
        guard let current = authSession else { return }
        ensureLiveWakeStreamRunning(localOnlyMode: false)

        // Flush any local dirty records if present (pushDirtyFast automatically pulls first if offline/stale)
        if !isPushingFast && !isSyncing {
            if let dirty = try? database.fetchDirtyBatch() {
                let hasDirty = !dirty.tags.isEmpty || !dirty.areas.isEmpty || !dirty.projects.isEmpty
                    || !dirty.headings.isEmpty || !dirty.tasks.isEmpty || !dirty.checklistItems.isEmpty
                    || !dirty.attachments.isEmpty || !dirty.projectTemplates.isEmpty
                if hasDirty {
                    await pushDirtyFast(localOnlyMode: false)
                }
            }
        }

        guard !isSyncing else { return }
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000.0)

        do {
            let validToken = try await validIdToken()
            let config = FirebaseConfig.loadActive()
            guard let pulseURL = URL(string: "https://firestore.googleapis.com/v1/projects/\(config.projectId)/databases/(default)/documents/users/\(current.uid)/sync_meta/pulse") else {
                throw CloudSyncError.networkFailure("Invalid Firebase configuration URL.")
            }
            var req = URLRequest(url: pulseURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 4.0)
            req.httpMethod = "GET"
            req.setValue("Bearer \(validToken)", forHTTPHeaderField: "Authorization")

            let (data, res) = try await session.data(for: req)
            if let http = res as? HTTPURLResponse {
                if http.statusCode == 401 {
                    invalidateCachedIdTokenExpiry()
                    return
                }
                if (200..<300).contains(http.statusCode),
                   let doc = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let fields = doc["fields"] as? [String: [String: Any]] {
                    let sender = fields["senderDeviceId"].flatMap(Self.unwrapFirestoreValue) as? String
                    let pulseAt = Self.asInt64(fields["pulseAt"].flatMap(Self.unwrapFirestoreValue)) ?? 0
                    if pulseAt > 0 && pulseAt != lastObservedRemotePulseAt {
                        if sender == current.deviceId {
                            lastObservedRemotePulseAt = pulseAt
                        } else {
                            let ok = await pullIncrementalDeltasOnly(localOnlyMode: false)
                            if ok {
                                lastObservedRemotePulseAt = pulseAt
                            }
                            return
                        }
                    }
                }
            }
        } catch {
            // Ignore transient pulse read errors
        }

        if nowMillis - lastFullFallbackPullMillis >= 20_000 {
            let ok = await pullIncrementalDeltasOnly(localOnlyMode: false)
            if ok {
                lastFullFallbackPullMillis = nowMillis
            }
        }
    }

    // MARK: - Bidirectional Firestore v1 + Secondary ntfy.sh Delta Sync

    public func syncBidirectionalDeltas(
        forceFullPull: Bool = false,
        pullBeforePush: Bool = true,
        localOnlyMode: Bool
    ) async {
        let _ = pullBeforePush
        if localOnlyMode {
            stopLiveWakeStream()
            updateSnapshot(patchStatus: .offline, error: nil)
            return
        }
        guard authSession != nil else { return }
        ensureLiveWakeStreamRunning(localOnlyMode: false)

        if isSyncing {
            pendingResyncRequested = true
            if forceFullPull {
                pendingForceFullPull = true
            }
            pendingPullBeforePush = true
            return
        }

        isSyncing = true
        defer { isSyncing = false }
        var runForceFull = forceFullPull

        while true {
            pendingResyncRequested = false
            pendingForceFullPull = false
            pendingPullBeforePush = false

            guard let current = authSession else { break }
            updateSnapshot(patchStatus: .syncing, error: nil)

            do {
                let validToken = try await validIdToken()
                let config = FirebaseConfig.loadActive()
                let cursor = runForceFull ? 0 : loadLastSyncCursor(uid: current.uid)
                let safeQuerySince = runForceFull ? 0 : max(0, cursor - Self.clockSkewOverlapMillis)

                var firestoreMax: Int64 = 0
                var ntfyMax: Int64 = 0
                var firestorePullSucceeded = false
                var pullError: Error?

                // 1. ALWAYS pull remote deltas FIRST so newer cloud records reconcile before local dirty push
                do {
                    firestoreMax = try await pullDeltasFromFirestore(
                        uid: current.uid,
                        idToken: validToken,
                        projectId: config.projectId,
                        sinceTimestamp: safeQuerySince,
                        forceFullPull: runForceFull
                    )
                    firestorePullSucceeded = true
                } catch {
                    pullError = error
                }

                // Poll legacy secondary ntfy channels on fresh sign-in / full pull or when Firestore pull failed
                if cursor == 0 || !firestorePullSucceeded {
                    let legacyTopics = Self.deriveLegacySyncTopics(
                        uid: current.uid,
                        email: current.email,
                        primaryTopic: current.channelTopic
                    )
                    for legacyTopic in legacyTopics {
                        let relayedMax = (try? await pollSecondaryNtfyRelay(
                            uid: current.uid,
                            topic: legacyTopic,
                            deviceId: current.deviceId,
                            sinceTimestamp: safeQuerySince,
                            fetchAllHistory: runForceFull || cursor == 0
                        )) ?? 0
                        ntfyMax = max(ntfyMax, relayedMax)
                    }
                }

                // 2. Refresh stale offline dirty timestamps (>30s old) on surviving local edits so reconnect pushes win cleanly
                if firestorePullSucceeded {
                    _ = try? database.restampStaleOfflineDirtyRecords(olderThanMillis: Self.offlineRestampThresholdMillis)
                }

                // 3. Push remaining local dirty records to primary Firestore, then broadcast instant ntfy wake
                let dirty = try database.fetchDirtyBatch()
                let hasDirty = !dirty.tags.isEmpty || !dirty.areas.isEmpty || !dirty.projects.isEmpty
                    || !dirty.headings.isEmpty || !dirty.tasks.isEmpty || !dirty.checklistItems.isEmpty
                    || !dirty.attachments.isEmpty || !dirty.projectTemplates.isEmpty

                if hasDirty {
                    do {
                        try await pushDirtyBatchToFirestore(
                            dirty: dirty,
                            uid: current.uid,
                            deviceId: current.deviceId,
                            idToken: validToken,
                            projectId: config.projectId
                        )
                        let uidCopy = current.uid
                        let emailCopy = current.email
                        let topicCopy = current.channelTopic
                        let deviceCopy = current.deviceId
                        Task {
                            await self.broadcastDirtyBatchToNtfyTopics(
                                dirty: dirty,
                                uid: uidCopy,
                                email: emailCopy,
                                channelTopic: topicCopy,
                                deviceId: deviceCopy
                            )
                        }
                    } catch {
                        // Broadcast over secondary relay while keeping local dirty flags intact
                        await broadcastDirtyBatchToNtfyTopics(
                            dirty: dirty,
                            uid: current.uid,
                            email: current.email,
                            channelTopic: current.channelTopic,
                            deviceId: current.deviceId
                        )
                        if !firestorePullSucceeded {
                            throw error
                        }
                    }
                } else if let pullError, ntfyMax == 0 {
                    throw pullError
                }

                // 4. Promote any due UPCOMING tasks pulled from remote into TODAY cleanly
                _ = try? database.performMidnightRollover()

                let now = Int64(Date().timeIntervalSince1970 * 1000.0)
                if firestorePullSucceeded {
                    lastFullFallbackPullMillis = now
                    let nextCursor: Int64
                    if firestoreMax > cursor {
                        nextCursor = min(firestoreMax, now + 60_000)
                    } else if cursor == 0 {
                        nextCursor = now
                    } else {
                        nextCursor = cursor
                    }
                    saveLastSyncCursor(uid: current.uid, timestamp: nextCursor)
                }
                saveLastWallSyncTimestamp(uid: current.uid, timestamp: now)
                updateSnapshot(
                    CloudSyncSnapshot(
                        status: .synced,
                        userEmailOrId: current.email,
                        userId: current.uid,
                        lastSyncedAt: now,
                        errorMessage: nil
                    )
                )
            } catch {
                updateSnapshot(patchStatus: .offline, error: error.localizedDescription)
                break
            }

            if pendingResyncRequested && !localOnlyMode {
                runForceFull = pendingForceFullPull
                continue
            }
            break
        }
    }

    private func pushDirtyBatchToFirestore(
        dirty: SyncDirtyBatch,
        uid: String,
        deviceId: String,
        idToken: String,
        projectId: String
    ) async throws {
        var writes: [[String: Any]] = []
        let baseDocPrefix = "projects/\(projectId)/databases/(default)/documents/users/\(uid)"

        for tag in dirty.tags {
            writes.append(Self.makeEntityUpdateWrite(path: "\(baseDocPrefix)/tags/\(tag.id)", fields: [
                "id": Self.fsString(tag.id),
                "title": Self.fsString(tag.title),
                "parentTagId": Self.fsNullableString(tag.parentTagId),
                "shortcut": Self.fsNullableString(tag.shortcut),
                "sortOrder": Self.fsDouble(tag.sortOrder),
                "createdAt": Self.fsInt(tag.createdAt),
                "updatedAt": Self.fsInt(tag.updatedAt),
                "isDeleted": Self.fsBool(tag.isDeleted)
            ]))
        }

        for area in dirty.areas {
            writes.append(Self.makeEntityUpdateWrite(path: "\(baseDocPrefix)/areas/\(area.id)", fields: [
                "id": Self.fsString(area.id),
                "title": Self.fsString(area.title),
                "sortOrder": Self.fsDouble(area.sortOrder),
                "tagIds": Self.fsStringArray(area.tagIds),
                "createdAt": Self.fsInt(area.createdAt),
                "updatedAt": Self.fsInt(area.updatedAt),
                "isDeleted": Self.fsBool(area.isDeleted)
            ]))
        }

        for proj in dirty.projects {
            let isPurged = proj.isDeleted && proj.title.isEmpty && proj.notes.isEmpty
            writes.append(Self.makeEntityUpdateWrite(path: "\(baseDocPrefix)/projects/\(proj.id)", fields: [
                "id": Self.fsString(proj.id),
                "areaId": Self.fsNullableString(proj.areaId),
                "title": Self.fsString(proj.title),
                "notes": Self.fsString(proj.notes),
                "status": Self.fsString(proj.status.rawValue),
                "scheduleState": Self.fsString(proj.scheduleState.rawValue),
                "whenDate": Self.fsNullableInt(proj.whenDate),
                "deadline": Self.fsNullableInt(proj.deadline),
                "completedAt": Self.fsNullableInt(proj.completedAt),
                "lastReviewedAt": Self.fsNullableInt(proj.lastReviewedAt),
                "sortOrder": Self.fsDouble(proj.sortOrder),
                "tagIds": Self.fsStringArray(proj.tagIds),
                "workspaceId": Self.fsNullableString(isPurged ? nil : proj.workspaceId),
                "viewMode": Self.fsNullableString(isPurged ? nil : proj.viewMode),
                "createdAt": Self.fsInt(proj.createdAt),
                "updatedAt": Self.fsInt(proj.updatedAt),
                "isDeleted": Self.fsBool(proj.isDeleted),
                "purgedFromTrash": Self.fsBool(isPurged)
            ]))
        }

        for head in dirty.headings {
            writes.append(Self.makeEntityUpdateWrite(path: "\(baseDocPrefix)/headings/\(head.id)", fields: [
                "id": Self.fsString(head.id),
                "projectId": Self.fsString(head.projectId),
                "title": Self.fsString(head.title),
                "sortOrder": Self.fsDouble(head.sortOrder),
                "isArchived": Self.fsBool(head.isArchived),
                "startDate": Self.fsNullableInt(head.startDate),
                "endDate": Self.fsNullableInt(head.endDate),
                "createdAt": Self.fsInt(head.createdAt),
                "updatedAt": Self.fsInt(head.updatedAt),
                "isDeleted": Self.fsBool(head.isDeleted)
            ]))
        }

        let jsonEncoder = JSONEncoder()
        for task in dirty.tasks {
            let recString: String? = task.recurrenceRule.flatMap {
                guard let d = try? jsonEncoder.encode($0) else { return nil }
                return String(data: d, encoding: .utf8)
            }
            let isPurged = task.isDeleted && task.title.isEmpty && task.notes.isEmpty
            writes.append(Self.makeEntityUpdateWrite(path: "\(baseDocPrefix)/tasks/\(task.id)", fields: [
                "id": Self.fsString(task.id),
                "title": Self.fsString(task.title),
                "notes": Self.fsString(task.notes),
                "status": Self.fsString(task.status.rawValue),
                "scheduleState": Self.fsString(task.scheduleState.rawValue),
                "whenDate": Self.fsNullableInt(task.whenDate),
                "deadline": Self.fsNullableInt(task.deadline),
                "reminderTime": Self.fsNullableInt(task.reminderTime),
                "scheduledStartMinutes": Self.fsNullableInt(task.scheduledStartMinutes.map(Int64.init)),
                "durationMinutes": Self.fsNullableInt(task.durationMinutes.map(Int64.init)),
                "completedAt": Self.fsNullableInt(task.completedAt),
                "projectId": Self.fsNullableString(task.projectId),
                "headingId": Self.fsNullableString(task.headingId),
                "areaId": Self.fsNullableString(task.areaId),
                "recurrenceRule": Self.fsNullableString(recString),
                "sortOrder": Self.fsDouble(task.sortOrder),
                "isPinned": Self.fsBool(!task.isDeleted && task.isPinned),
                "effortLevel": Self.fsNullableString(task.isDeleted ? nil : task.effortLevel),
                "blockedByTaskIds": Self.fsStringArray(task.isDeleted ? [] : task.blockedByTaskIds),
                "workspaceId": Self.fsNullableString(task.isDeleted ? nil : task.workspaceId),
                "assigneeId": Self.fsNullableString(task.isDeleted ? nil : task.assigneeId),
                "tagIds": Self.fsStringArray(task.tagIds),
                "createdAt": Self.fsInt(task.createdAt),
                "updatedAt": Self.fsInt(task.updatedAt),
                "isDeleted": Self.fsBool(task.isDeleted),
                "purgedFromTrash": Self.fsBool(isPurged)
            ]))
        }

        for chk in dirty.checklistItems {
            writes.append(Self.makeEntityUpdateWrite(path: "\(baseDocPrefix)/checklist_items/\(chk.id)", fields: [
                "id": Self.fsString(chk.id),
                "taskId": Self.fsString(chk.taskId),
                "title": Self.fsString(chk.title),
                "isCompleted": Self.fsBool(chk.isCompleted),
                "sortOrder": Self.fsDouble(chk.sortOrder),
                "createdAt": Self.fsInt(chk.createdAt),
                "updatedAt": Self.fsInt(chk.updatedAt),
                "isDeleted": Self.fsBool(chk.isDeleted)
            ]))
        }

        for tmpl in dirty.projectTemplates {
            writes.append(Self.makeEntityUpdateWrite(path: "\(baseDocPrefix)/project_templates/\(tmpl.id)", fields: [
                "id": Self.fsString(tmpl.id),
                "title": Self.fsString(tmpl.title),
                "notes": Self.fsString(tmpl.notes),
                "defaultAreaId": Self.fsNullableString(tmpl.defaultAreaId),
                "tagIds": Self.fsStringArray(tmpl.tagIds),
                "blueprintJson": Self.fsString(tmpl.blueprintJson),
                "sortOrder": Self.fsDouble(tmpl.sortOrder),
                "createdAt": Self.fsInt(tmpl.createdAt),
                "updatedAt": Self.fsInt(tmpl.updatedAt),
                "isDeleted": Self.fsBool(tmpl.isDeleted)
            ]))
        }

        guard !writes.isEmpty || !dirty.attachments.isEmpty else { return }

        let pulseNow = Int64(Date().timeIntervalSince1970 * 1000.0)
        let pulseWrite = Self.makeEntityUpdateWrite(
            path: "\(baseDocPrefix)/sync_meta/pulse",
            fields: [
                "pulseAt": Self.fsInt(pulseNow),
                "senderDeviceId": Self.fsString(deviceId)
            ]
        )

        guard let commitURL = URL(string: "https://firestore.googleapis.com/v1/projects/\(projectId)/databases/(default)/documents:commit") else {
            throw CloudSyncError.networkFailure("Invalid Firebase configuration URL.")
        }
        if !writes.isEmpty {
            let chunkSize = 449
            var idx = 0
            while idx < writes.count {
                let end = min(idx + chunkSize, writes.count)
                var slice = Array(writes[idx..<end])
                if end == writes.count && dirty.attachments.isEmpty {
                    slice.append(pulseWrite)
                }
                var req = URLRequest(url: commitURL)
                req.httpMethod = "POST"
                req.setValue("Bearer \(idToken)", forHTTPHeaderField: "Authorization")
                req.setValue("application/json", forHTTPHeaderField: "Content-Type")
                req.httpBody = try JSONSerialization.data(withJSONObject: ["writes": slice])
                let (data, res) = try await session.data(for: req)
                if let http = res as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                    if http.statusCode == 401 {
                        invalidateCachedIdTokenExpiry()
                    }
                    throw CloudSyncError.networkFailure(extractFirebaseError(from: data) ?? "Firestore commit failed (HTTP \(http.statusCode)).")
                }
                idx += chunkSize
            }
        }

        if !dirty.attachments.isEmpty {
            var attWrites: [[String: Any]] = []
            for att in dirty.attachments {
                let inlineB64 = att.isDeleted ? "" : database.readAttachmentBase64(att, maxRawBytes: Self.maxInlineAttachmentBytes)
                attWrites.append(Self.makeAttachmentWrite(baseDocPrefix: baseDocPrefix, attachment: att, inlineBase64: inlineB64))
            }
            let attChunkSize = 8
            var attIdx = 0
            while attIdx < attWrites.count {
                let end = min(attIdx + attChunkSize, attWrites.count)
                var slice = Array(attWrites[attIdx..<end])
                if end == attWrites.count {
                    slice.append(pulseWrite)
                }
                var req = URLRequest(url: commitURL)
                req.httpMethod = "POST"
                req.setValue("Bearer \(idToken)", forHTTPHeaderField: "Authorization")
                req.setValue("application/json", forHTTPHeaderField: "Content-Type")
                req.httpBody = try JSONSerialization.data(withJSONObject: ["writes": slice])
                let (data, res) = try await session.data(for: req)
                if let http = res as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                    if http.statusCode == 401 {
                        invalidateCachedIdTokenExpiry()
                    }
                    throw CloudSyncError.networkFailure(extractFirebaseError(from: data) ?? "Firestore attachment commit failed (HTTP \(http.statusCode)).")
                }
                attIdx += attChunkSize
            }
        }

        try database.markDirtyBatchSynced(dirty)
    }

    public static func buildLegacyCompatibleNtfyChunks(
        uid: String,
        deviceId: String,
        batchTimestamp: Int64,
        collections: [(key: String, items: [[String: Any]])],
        maxBytes: Int = maxNtfyPayloadBytes,
        maxChunks: Int = 6
    ) -> [Data] {
        var fullBatch: [String: Any] = [
            "userId": uid,
            "deviceId": deviceId,
            "batchTimestamp": batchTimestamp
        ]
        var nonEmptyCollections: [(key: String, items: [[String: Any]])] = []
        for entry in collections where !entry.items.isEmpty {
            fullBatch[entry.key] = entry.items
            nonEmptyCollections.append(entry)
        }
        guard !nonEmptyCollections.isEmpty else { return [] }

        if let single = try? JSONSerialization.data(withJSONObject: fullBatch), single.count <= maxBytes {
            return [single]
        }

        var chunks: [Data] = []
        var currentMap: [String: [[String: Any]]] = [:]
        var currentHasAny = false

        func serializeCurrent() -> Data? {
            guard currentHasAny else { return nil }
            var obj: [String: Any] = [
                "userId": uid,
                "deviceId": deviceId,
                "batchTimestamp": batchTimestamp
            ]
            for (k, arr) in currentMap where !arr.isEmpty {
                obj[k] = arr
            }
            return try? JSONSerialization.data(withJSONObject: obj)
        }

        outer: for (colKey, items) in nonEmptyCollections {
            for item in items {
                var candidateMap = currentMap
                candidateMap[colKey, default: []].append(item)
                var candidateObj: [String: Any] = [
                    "userId": uid,
                    "deviceId": deviceId,
                    "batchTimestamp": batchTimestamp
                ]
                for (k, arr) in candidateMap where !arr.isEmpty {
                    candidateObj[k] = arr
                }
                if let candidateData = try? JSONSerialization.data(withJSONObject: candidateObj),
                   candidateData.count <= maxBytes {
                    currentMap = candidateMap
                    currentHasAny = true
                } else {
                    if let flushed = serializeCurrent() {
                        chunks.append(flushed)
                        if chunks.count >= maxChunks {
                            currentHasAny = false
                            break outer
                        }
                    }
                    currentMap = [:]
                    currentHasAny = false

                    let soloObj: [String: Any] = [
                        "userId": uid,
                        "deviceId": deviceId,
                        "batchTimestamp": batchTimestamp,
                        colKey: [item]
                    ]
                    if let soloData = try? JSONSerialization.data(withJSONObject: soloObj),
                       soloData.count <= maxBytes {
                        currentMap = [colKey: [item]]
                        currentHasAny = true
                    }
                }
            }
        }

        if chunks.count < maxChunks, let tail = serializeCurrent() {
            chunks.append(tail)
        }
        return chunks
    }

    private func broadcastDirtyBatchToNtfyTopics(
        dirty: SyncDirtyBatch,
        uid: String,
        email: String?,
        channelTopic: String,
        deviceId: String
    ) async {
        let hasEntities = !dirty.tags.isEmpty || !dirty.areas.isEmpty || !dirty.projects.isEmpty
            || !dirty.headings.isEmpty || !dirty.tasks.isEmpty || !dirty.checklistItems.isEmpty
            || !dirty.attachments.isEmpty || !dirty.projectTemplates.isEmpty
        guard hasEntities else { return }

        let now = Int64(Date().timeIntervalSince1970 * 1000.0)
        let jsonEncoder = JSONEncoder()

        func sanitize(_ dict: [String: Any]) -> [String: Any] {
            var out: [String: Any] = [:]
            for (k, v) in dict {
                let mirror = Mirror(reflecting: v)
                if mirror.displayStyle == .optional {
                    if let child = mirror.children.first {
                        out[k] = child.value
                    } else {
                        out[k] = NSNull()
                    }
                } else {
                    out[k] = v
                }
            }
            return out
        }

        let tagMaps: [[String: Any]] = dirty.tags.map { tag in
            sanitize([
                "id": tag.id,
                "title": tag.title,
                "parentTagId": tag.parentTagId as Any,
                "shortcut": tag.shortcut as Any,
                "sortOrder": tag.sortOrder,
                "createdAt": tag.createdAt,
                "updatedAt": tag.updatedAt,
                "isDeleted": tag.isDeleted
            ])
        }
        let areaMaps: [[String: Any]] = dirty.areas.map { area in
            sanitize([
                "id": area.id,
                "title": area.title,
                "sortOrder": area.sortOrder,
                "tagIds": area.tagIds,
                "createdAt": area.createdAt,
                "updatedAt": area.updatedAt,
                "isDeleted": area.isDeleted
            ])
        }
        let projMaps: [[String: Any]] = dirty.projects.map { proj in
            let isPurged = proj.isDeleted && proj.title.isEmpty && proj.notes.isEmpty
            return sanitize([
                "id": proj.id,
                "areaId": proj.areaId as Any,
                "title": proj.title,
                "notes": proj.notes,
                "status": proj.status.rawValue,
                "scheduleState": proj.scheduleState.rawValue,
                "whenDate": proj.whenDate as Any,
                "deadline": proj.deadline as Any,
                "completedAt": proj.completedAt as Any,
                "lastReviewedAt": proj.lastReviewedAt as Any,
                "sortOrder": proj.sortOrder,
                "tagIds": proj.tagIds,
                "workspaceId": (isPurged ? nil : proj.workspaceId) as Any,
                "viewMode": (isPurged ? nil : proj.viewMode) as Any,
                "createdAt": proj.createdAt,
                "updatedAt": proj.updatedAt,
                "isDeleted": proj.isDeleted,
                "purgedFromTrash": isPurged
            ])
        }
        let headMaps: [[String: Any]] = dirty.headings.map { head in
            sanitize([
                "id": head.id,
                "projectId": head.projectId,
                "title": head.title,
                "sortOrder": head.sortOrder,
                "isArchived": head.isArchived,
                "startDate": head.startDate as Any,
                "endDate": head.endDate as Any,
                "createdAt": head.createdAt,
                "updatedAt": head.updatedAt,
                "isDeleted": head.isDeleted
            ])
        }
        let taskMaps: [[String: Any]] = dirty.tasks.map { task in
            let recString: String? = task.recurrenceRule.flatMap {
                guard let d = try? jsonEncoder.encode($0) else { return nil }
                return String(data: d, encoding: .utf8)
            }
            let isPurged = task.isDeleted && task.title.isEmpty && task.notes.isEmpty
            return sanitize([
                "id": task.id,
                "title": task.title,
                "notes": task.notes,
                "status": task.status.rawValue,
                "scheduleState": task.scheduleState.rawValue,
                "whenDate": task.whenDate as Any,
                "deadline": task.deadline as Any,
                "reminderTime": task.reminderTime as Any,
                "scheduledStartMinutes": task.scheduledStartMinutes as Any,
                "durationMinutes": task.durationMinutes as Any,
                "completedAt": task.completedAt as Any,
                "projectId": task.projectId as Any,
                "headingId": task.headingId as Any,
                "areaId": task.areaId as Any,
                "recurrenceRule": recString as Any,
                "sortOrder": task.sortOrder,
                "isPinned": !task.isDeleted && task.isPinned,
                "effortLevel": (task.isDeleted ? nil : task.effortLevel) as Any,
                "blockedByTaskIds": task.isDeleted ? [] : task.blockedByTaskIds,
                "workspaceId": (task.isDeleted ? nil : task.workspaceId) as Any,
                "assigneeId": (task.isDeleted ? nil : task.assigneeId) as Any,
                "tagIds": task.tagIds,
                "createdAt": task.createdAt,
                "updatedAt": task.updatedAt,
                "isDeleted": task.isDeleted,
                "purgedFromTrash": isPurged
            ])
        }
        let chkMaps: [[String: Any]] = dirty.checklistItems.map { chk in
            sanitize([
                "id": chk.id,
                "taskId": chk.taskId,
                "title": chk.title,
                "isCompleted": chk.isCompleted,
                "sortOrder": chk.sortOrder,
                "createdAt": chk.createdAt,
                "updatedAt": chk.updatedAt,
                "isDeleted": chk.isDeleted
            ])
        }
        let attMaps: [[String: Any]] = dirty.attachments.map { att in
            sanitize([
                "id": att.id,
                "taskId": att.taskId as Any,
                "projectId": att.projectId as Any,
                "fileName": att.fileName,
                "mimeType": att.mimeType,
                "sizeBytes": att.sizeBytes,
                "localRelativePath": "",
                "createdAt": att.createdAt,
                "updatedAt": att.updatedAt,
                "isDeleted": att.isDeleted
            ])
        }
        let tmplMaps: [[String: Any]] = dirty.projectTemplates.map { tmpl in
            sanitize([
                "id": tmpl.id,
                "title": tmpl.title,
                "notes": tmpl.notes,
                "defaultAreaId": tmpl.defaultAreaId as Any,
                "tagIds": tmpl.tagIds,
                "blueprintJson": tmpl.blueprintJson,
                "sortOrder": tmpl.sortOrder,
                "createdAt": tmpl.createdAt,
                "updatedAt": tmpl.updatedAt,
                "isDeleted": tmpl.isDeleted
            ])
        }

        let orderedCollections: [(key: String, items: [[String: Any]])] = [
            ("tags", tagMaps),
            ("areas", areaMaps),
            ("projects", projMaps),
            ("headings", headMaps),
            ("tasks", taskMaps),
            ("checklistItems", chkMaps),
            ("attachments", attMaps),
            ("projectTemplates", tmplMaps)
        ]

        var fullBatch: [String: Any] = [
            "userId": uid,
            "deviceId": deviceId,
            "batchTimestamp": now
        ]
        for (k, arr) in orderedCollections where !arr.isEmpty {
            fullBatch[k] = arr
        }

        // 1. v2.x UID wake topic (`tb_uid_*`): full batch if compact, else lightweight `wakeOnly: true`
        let uidWakeTopic = Self.deriveUidWakeTopic(uid: uid)
        if !uidWakeTopic.isEmpty {
            let uidPayload: Data?
            if let single = try? JSONSerialization.data(withJSONObject: fullBatch),
               single.count < Self.maxNtfyPayloadBytes {
                uidPayload = single
            } else {
                uidPayload = try? JSONSerialization.data(withJSONObject: [
                    "userId": uid,
                    "deviceId": deviceId,
                    "batchTimestamp": now,
                    "wakeOnly": true
                ])
            }
            if let uidPayload {
                await postNtfyPayload(topic: uidWakeTopic, body: uidPayload)
            }
        }

        // 2. Legacy v1.8-compatible `tb_sync_*` topics: NEVER send `wakeOnly: true` (v1.8 ignores empty batches!);
        //    instead send chunked entity payloads so v1.8 Android & PWA peers apply deltas immediately in <200ms!
        let legacyChunks = Self.buildLegacyCompatibleNtfyChunks(
            uid: uid,
            deviceId: deviceId,
            batchTimestamp: now,
            collections: orderedCollections
        )
        guard !legacyChunks.isEmpty else { return }
        let legacyTopics = Self.deriveLegacySyncTopics(uid: uid, email: email, primaryTopic: channelTopic)
        for topic in legacyTopics {
            for chunk in legacyChunks {
                await postNtfyPayload(topic: topic, body: chunk)
            }
        }
    }

    private func postNtfyPayload(topic: String, body: Data) async {
        guard let encoded = topic.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed),
              let url = URL(string: "https://ntfy.sh/\(encoded)") else { return }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = 2.5
        req.setValue("text/plain; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.setValue("yes", forHTTPHeaderField: "Cache")
        req.httpBody = body
        _ = try? await session.data(for: req)
    }

    private func pullDeltasFromFirestore(
        uid: String,
        idToken: String,
        projectId: String,
        sinceTimestamp: Int64,
        forceFullPull: Bool = false
    ) async throws -> Int64 {
        let reconcileKey = Self.reconcileV222DefaultsKey(uid: uid)
        let needsReconcileV222 = !UserDefaults.standard.bool(forKey: reconcileKey)
        let fullAll = forceFullPull || needsReconcileV222 || sinceTimestamp <= 0

        let attBackfillKey = Self.attachmentBackfillDefaultsKey(uid: uid)
        let needsAttBackfill = !UserDefaults.standard.bool(forKey: attBackfillKey)

        let tmplBackfillKey = Self.templateBackfillDefaultsKey(uid: uid)
        let needsTmplBackfill = !UserDefaults.standard.bool(forKey: tmplBackfillKey)

        let projReviewBackfillKey = Self.projectReviewBackfillDefaultsKey(uid: uid)
        let needsProjReviewBackfill = !UserDefaults.standard.bool(forKey: projReviewBackfillKey)

        let orphanFlags: (hasOrphanProject: Bool, hasOrphanArea: Bool, hasOrphanHeading: Bool) =
            (try? database.missingContainerFlags()) ?? (hasOrphanProject: false, hasOrphanArea: false, hasOrphanHeading: false)

        func collectionSince(_ collection: String, forceZero: Bool = false) -> Int64 {
            if fullAll || forceZero { return 0 }
            let colCursor = loadCollectionSyncCursor(uid: uid, collection: collection)
            if colCursor <= 0 { return 0 }
            return max(0, colCursor - Self.clockSkewOverlapMillis)
        }

        let tagsSince = collectionSince("tags")
        let areasSince = collectionSince("areas", forceZero: orphanFlags.hasOrphanArea)
        let projSince = collectionSince("projects", forceZero: needsProjReviewBackfill || orphanFlags.hasOrphanProject)
        let headSince = collectionSince("headings", forceZero: orphanFlags.hasOrphanHeading)
        let tasksSince = collectionSince("tasks")
        let chkSince = collectionSince("checklist_items")
        let attSince = collectionSince("attachments", forceZero: needsAttBackfill)
        let tmplSince = collectionSince("project_templates", forceZero: needsTmplBackfill)

        async let rawTags = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "tags", since: tagsSince)
        async let rawAreas = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "areas", since: areasSince)
        async let rawProjects = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "projects", since: projSince)
        async let rawHeadings = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "headings", since: headSince)
        async let rawTasks = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "tasks", since: tasksSince)
        async let rawChecklists = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "checklist_items", since: chkSince)
        async let rawAttachments = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "attachments", since: attSince)
        async let rawTemplates = queryCollectionOptional(projectId: projectId, uid: uid, idToken: idToken, collection: "project_templates", since: tmplSince)

        let (tagMaps, areaMaps, projMaps, headMaps, taskMaps, chkMaps, attMaps, tmplResult) = try await (
            rawTags, rawAreas, rawProjects, rawHeadings, rawTasks, rawChecklists, rawAttachments, rawTemplates
        )
        let tmplMaps = tmplResult.docs

        let tags: [Tag] = tagMaps.compactMap(Self.parseTag)
        let areas: [Area] = areaMaps.compactMap(Self.parseArea)
        var omittedProjectV30Ids = Set<String>()
        let projects: [Project] = projMaps.compactMap { map -> Project? in
            guard let parsed = Self.parseProjectWithHints(map) else { return nil }
            if parsed.omittedV30Fields {
                omittedProjectV30Ids.insert(parsed.project.id)
            }
            return parsed.project
        }
        var omittedHeadingStartDateIds = Set<String>()
        var omittedHeadingEndDateIds = Set<String>()
        let headings: [Heading] = headMaps.compactMap { map -> Heading? in
            guard let parsed = Self.parseHeadingWithHints(map) else { return nil }
            if parsed.omittedStartDate {
                omittedHeadingStartDateIds.insert(parsed.heading.id)
            }
            if parsed.omittedEndDate {
                omittedHeadingEndDateIds.insert(parsed.heading.id)
            }
            return parsed.heading
        }
        var taskTagIds: [String: [String]] = [:]
        var omittedTimeblockTaskIds = Set<String>()
        var omittedPinTaskIds = Set<String>()
        var omittedV30TaskIds = Set<String>()
        let tasks: [TaskItem] = taskMaps.compactMap { map -> TaskItem? in
            guard let parsed = Self.parseTaskWithHints(map) else { return nil }
            taskTagIds[parsed.task.id] = parsed.tagIds
            if parsed.omittedTimeblock {
                omittedTimeblockTaskIds.insert(parsed.task.id)
            }
            if parsed.omittedPin {
                omittedPinTaskIds.insert(parsed.task.id)
            }
            if parsed.omittedV30Fields {
                omittedV30TaskIds.insert(parsed.task.id)
            }
            return parsed.task
        }
        let checklists: [ChecklistItem] = chkMaps.compactMap(Self.parseChecklist)
        let attachments: [RemoteAttachmentDelta] = attMaps.compactMap(Self.parseAttachment)
        let projectTemplates: [ProjectTemplate] = tmplMaps.compactMap(Self.parseProjectTemplate)

        let now = Int64(Date().timeIntervalSince1970 * 1000.0)
        let maxAllowed = now + 60_000

        func commitCollectionCursor(_ collection: String, maxRemoteUpdatedAt: Int64?) {
            let prev = loadCollectionSyncCursor(uid: uid, collection: collection)
            if let maxRemoteUpdatedAt, maxRemoteUpdatedAt > 0 {
                let clamped = min(maxRemoteUpdatedAt, maxAllowed)
                if clamped > prev {
                    saveCollectionSyncCursor(uid: uid, collection: collection, timestamp: clamped)
                }
            } else if prev <= 0 {
                saveCollectionSyncCursor(uid: uid, collection: collection, timestamp: now)
            }
        }

        let appliedMax: Int64
        if tags.isEmpty && areas.isEmpty && projects.isEmpty && headings.isEmpty && tasks.isEmpty && checklists.isEmpty && attachments.isEmpty && projectTemplates.isEmpty {
            appliedMax = sinceTimestamp
        } else {
            let batch = RemoteDeltaBatch(
                tags: tags,
                areas: areas,
                projects: projects,
                projectOmittedV30Ids: omittedProjectV30Ids,
                headings: headings,
                headingOmittedStartDateIds: omittedHeadingStartDateIds,
                headingOmittedEndDateIds: omittedHeadingEndDateIds,
                tasks: tasks,
                taskTagIdsByTaskId: taskTagIds,
                taskOmittedTimeblockTaskIds: omittedTimeblockTaskIds,
                taskOmittedPinTaskIds: omittedPinTaskIds,
                taskOmittedV30TaskIds: omittedV30TaskIds,
                checklistItems: checklists,
                attachments: attachments,
                projectTemplates: projectTemplates
            )
            appliedMax = try database.applyRemoteDeltas(batch)
        }

        commitCollectionCursor("tags", maxRemoteUpdatedAt: tags.map { $0.updatedAt }.max())
        commitCollectionCursor("areas", maxRemoteUpdatedAt: areas.map { $0.updatedAt }.max())
        commitCollectionCursor("projects", maxRemoteUpdatedAt: projects.map { $0.updatedAt }.max())
        commitCollectionCursor("headings", maxRemoteUpdatedAt: headings.map { $0.updatedAt }.max())
        commitCollectionCursor("tasks", maxRemoteUpdatedAt: tasks.map { $0.updatedAt }.max())
        commitCollectionCursor("checklist_items", maxRemoteUpdatedAt: checklists.map { $0.updatedAt }.max())
        commitCollectionCursor("attachments", maxRemoteUpdatedAt: attachments.map { $0.attachment.updatedAt }.max())
        if tmplResult.succeeded {
            commitCollectionCursor("project_templates", maxRemoteUpdatedAt: projectTemplates.map { $0.updatedAt }.max())
            if needsTmplBackfill {
                UserDefaults.standard.set(true, forKey: tmplBackfillKey)
            }
        }
        if needsAttBackfill {
            UserDefaults.standard.set(true, forKey: attBackfillKey)
        }
        if needsProjReviewBackfill {
            UserDefaults.standard.set(true, forKey: projReviewBackfillKey)
        }
        if needsReconcileV222 {
            UserDefaults.standard.set(true, forKey: reconcileKey)
        }
        return appliedMax
    }

    private func queryCollectionOptional(
        projectId: String,
        uid: String,
        idToken: String,
        collection: String,
        since: Int64
    ) async -> (docs: [[String: Any]], succeeded: Bool) {
        do {
            let rows = try await queryCollection(
                projectId: projectId,
                uid: uid,
                idToken: idToken,
                collection: collection,
                since: since
            )
            return (rows, true)
        } catch {
            return ([], false)
        }
    }

    private func queryCollection(
        projectId: String,
        uid: String,
        idToken: String,
        collection: String,
        since: Int64
    ) async throws -> [[String: Any]] {
        guard let url = URL(string: "https://firestore.googleapis.com/v1/projects/\(projectId)/databases/(default)/documents/users/\(uid):runQuery") else {
            throw CloudSyncError.networkFailure("Invalid Firebase configuration URL.")
        }
        var structuredQuery: [String: Any] = [
            "from": [["collectionId": collection]]
        ]
        if since > 0 {
            structuredQuery["where"] = [
                "fieldFilter": [
                    "field": ["fieldPath": "updatedAt"],
                    "op": "GREATER_THAN",
                    "value": ["integerValue": String(since)]
                ]
            ]
        }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("Bearer \(idToken)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["structuredQuery": structuredQuery])

        let (data, res) = try await session.data(for: req)
        if let http = res as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            if http.statusCode == 401 {
                invalidateCachedIdTokenExpiry()
            }
            throw CloudSyncError.networkFailure(extractFirebaseError(from: data) ?? "Firestore query failed (HTTP \(http.statusCode)).")
        }

        guard let rows = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }

        var unpacked: [[String: Any]] = []
        for row in rows {
            guard let doc = row["document"] as? [String: Any],
                  let fields = doc["fields"] as? [String: [String: Any]] else { continue }
            var plain: [String: Any] = [:]
            if let docName = doc["name"] as? String, let docId = docName.split(separator: "/").last {
                plain["id"] = String(docId)
            }
            for (k, v) in fields {
                if v["nullValue"] != nil {
                    if k != "id" {
                        plain[k] = NSNull()
                    }
                    continue
                }
                if let val = Self.unwrapFirestoreValue(v) {
                    if k == "id", let s = val as? String, s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        continue
                    }
                    plain[k] = val
                }
            }
            unpacked.append(plain)
        }
        return unpacked
    }

    private func pollSecondaryNtfyRelay(
        uid: String,
        topic: String,
        deviceId: String,
        sinceTimestamp: Int64,
        fetchAllHistory: Bool
    ) async throws -> Int64 {
        let sinceParam = fetchAllHistory ? "all" : "24h"
        guard let encoded = topic.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed),
              let url = URL(string: "https://ntfy.sh/\(encoded)/json?poll=1&since=\(sinceParam)") else { return 0 }
        var req = URLRequest(url: url)
        req.timeoutInterval = 3.0
        let (data, res) = try await session.data(for: req)
        guard let http = res as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let text = String(data: data, encoding: .utf8) else { return 0 }

        var maxApplied: Int64 = 0
        for line in text.split(separator: "\n") {
            guard let lineData = line.data(using: .utf8),
                  let env = try? JSONSerialization.jsonObject(with: lineData) as? [String: Any],
                  env["event"] as? String == "message",
                  let msgStr = env["message"] as? String,
                  let msgData = msgStr.data(using: .utf8),
                  let batch = try? JSONSerialization.jsonObject(with: msgData) as? [String: Any],
                  batch["userId"] as? String == uid else { continue }

            let batchTs = Self.asInt64(batch["batchTimestamp"]) ?? 0
            if batch["deviceId"] as? String == deviceId, sinceTimestamp > 0, batchTs <= sinceTimestamp {
                continue
            }
            if batch["wakeOnly"] as? Bool == true {
                continue
            }

            let tags = (batch["tags"] as? [[String: Any]] ?? []).compactMap(Self.parseTag)
            let areas = (batch["areas"] as? [[String: Any]] ?? []).compactMap(Self.parseArea)
            var omittedProjectV30Ids = Set<String>()
            let projects = (batch["projects"] as? [[String: Any]] ?? []).compactMap { m -> Project? in
                guard let parsed = Self.parseProjectWithHints(m) else { return nil }
                if parsed.omittedV30Fields {
                    omittedProjectV30Ids.insert(parsed.project.id)
                }
                return parsed.project
            }
            var omittedHeadingStartDateIds = Set<String>()
            var omittedHeadingEndDateIds = Set<String>()
            let headings = (batch["headings"] as? [[String: Any]] ?? []).compactMap { m -> Heading? in
                guard let parsed = Self.parseHeadingWithHints(m) else { return nil }
                if parsed.omittedStartDate {
                    omittedHeadingStartDateIds.insert(parsed.heading.id)
                }
                if parsed.omittedEndDate {
                    omittedHeadingEndDateIds.insert(parsed.heading.id)
                }
                return parsed.heading
            }
            var taskTags: [String: [String]] = [:]
            var omittedTimeblockTaskIds = Set<String>()
            var omittedPinTaskIds = Set<String>()
            var omittedV30TaskIds = Set<String>()
            let tasks = (batch["tasks"] as? [[String: Any]] ?? []).compactMap { m -> TaskItem? in
                guard let parsed = Self.parseTaskWithHints(m) else { return nil }
                taskTags[parsed.task.id] = parsed.tagIds
                if parsed.omittedTimeblock {
                    omittedTimeblockTaskIds.insert(parsed.task.id)
                }
                if parsed.omittedPin {
                    omittedPinTaskIds.insert(parsed.task.id)
                }
                if parsed.omittedV30Fields {
                    omittedV30TaskIds.insert(parsed.task.id)
                }
                return parsed.task
            }
            let checklists = (batch["checklistItems"] as? [[String: Any]] ?? []).compactMap(Self.parseChecklist)
            let attachments = (batch["attachments"] as? [[String: Any]] ?? []).compactMap(Self.parseAttachment)
            let projectTemplates = (batch["projectTemplates"] as? [[String: Any]] ?? []).compactMap(Self.parseProjectTemplate)

            if let batchMax = try? database.applyRemoteDeltas(
                RemoteDeltaBatch(
                    tags: tags,
                    areas: areas,
                    projects: projects,
                    projectOmittedV30Ids: omittedProjectV30Ids,
                    headings: headings,
                    headingOmittedStartDateIds: omittedHeadingStartDateIds,
                    headingOmittedEndDateIds: omittedHeadingEndDateIds,
                    tasks: tasks,
                    taskTagIdsByTaskId: taskTags,
                    taskOmittedTimeblockTaskIds: omittedTimeblockTaskIds,
                    taskOmittedPinTaskIds: omittedPinTaskIds,
                    taskOmittedV30TaskIds: omittedV30TaskIds,
                    checklistItems: checklists,
                    attachments: attachments,
                    projectTemplates: projectTemplates
                )
            ) {
                maxApplied = max(maxApplied, max(batchMax, batchTs))
            }
        }
        return maxApplied
    }

    // MARK: - Loopback OAuth Socket Helper (127.0.0.1)

    private static func createLoopbackServerSocket() throws -> (fd: Int32, port: UInt16) {
        let fd = Darwin.socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else {
            throw CloudSyncError.networkFailure("Could not open local sign-in callback port.")
        }
        var reuse: Int32 = 1
        _ = Darwin.setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &reuse, socklen_t(MemoryLayout<Int32>.size))

        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = 0
        addr.sin_addr.s_addr = inet_addr("127.0.0.1")

        let bindRes = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        guard bindRes == 0, Darwin.listen(fd, 4) == 0 else {
            Darwin.close(fd)
            throw CloudSyncError.networkFailure("Could not bind local Google Sign-In callback port.")
        }

        var boundAddr = sockaddr_in()
        var addrLen = socklen_t(MemoryLayout<sockaddr_in>.size)
        let nameRes = withUnsafeMutablePointer(to: &boundAddr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.getsockname(fd, $0, &addrLen)
            }
        }
        guard nameRes == 0 else {
            Darwin.close(fd)
            throw CloudSyncError.networkFailure("Could not determine local Google Sign-In callback port.")
        }
        return (fd, UInt16(bigEndian: boundAddr.sin_port))
    }

    private static func awaitLoopbackOAuthCallback(
        serverFd: Int32,
        expectedState: String,
        timeoutSeconds: Int
    ) async throws -> [String: String] {
        try await Task.detached(priority: .userInitiated) {
            let deadline = Date().addingTimeInterval(TimeInterval(timeoutSeconds))
            while Date() < deadline {
                var pfd = pollfd(fd: serverFd, events: Int16(POLLIN), revents: 0)
                let ready = Darwin.poll(&pfd, 1, 1000)
                if ready <= 0 { continue }

                var clientAddr = sockaddr_in()
                var clientLen = socklen_t(MemoryLayout<sockaddr_in>.size)
                let clientFd = withUnsafeMutablePointer(to: &clientAddr) {
                    $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                        Darwin.accept(serverFd, $0, &clientLen)
                    }
                }
                if clientFd < 0 { continue }
                defer { Darwin.close(clientFd) }

                var buffer = [UInt8](repeating: 0, count: 16_384)
                let bytesRead = Darwin.read(clientFd, &buffer, buffer.count - 1)
                guard bytesRead > 0 else { continue }
                let reqString = String(decoding: buffer.prefix(bytesRead), as: UTF8.self)
                guard let firstLine = reqString.components(separatedBy: "\r\n").first else { continue }
                let parts = firstLine.split(separator: " ")
                guard parts.count >= 2 else { continue }
                let pathAndQuery = String(parts[1])
                if pathAndQuery.hasPrefix("/favicon") {
                    let resp = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
                    _ = resp.withCString { Darwin.write(clientFd, $0, strlen($0)) }
                    continue
                }

                guard let comps = URLComponents(string: "http://127.0.0.1\(pathAndQuery)") else { continue }
                var params: [String: String] = [:]
                for item in comps.queryItems ?? [] {
                    params[item.name] = item.value ?? ""
                }

                if params["state"] != expectedState {
                    let badHtml = "<html><body style='font-family:-apple-system,sans-serif;padding:40px;text-align:center;'><h3>Invalid OAuth state</h3></body></html>"
                    let resp = "HTTP/1.1 400 Bad Request\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n\(badHtml)"
                    _ = resp.withCString { Darwin.write(clientFd, $0, strlen($0)) }
                    throw CloudSyncError.invalidCredentials("Google Sign-In verification state mismatch. Please try again.")
                }

                let okHtml = """
                <!doctype html><html><head><meta charset="utf-8"><title>Thingsbook Cloud Sync</title></head>
                <body style="font-family:-apple-system,BlinkMacSystemFont,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;background:#f5f7fa;color:#1c1e21;">
                <div style="background:#fff;padding:32px 40px;border-radius:16px;box-shadow:0 8px 30px rgba(0,0,0,0.08);text-align:center;max-width:420px;">
                <div style="font-size:40px;margin-bottom:12px;">✅</div>
                <h2 style="margin:0 0 8px;font-size:20px;">Thingsbook Connected</h2>
                <p style="margin:0;color:#606770;font-size:14px;">You can close this browser tab and return to Thingsbook on your Mac.</p>
                </div>
                <script>setTimeout(function(){ window.close(); }, 800);</script>
                </body></html>
                """
                let okResp = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: \(okHtml.utf8.count)\r\nConnection: close\r\n\r\n\(okHtml)"
                _ = okResp.withCString { Darwin.write(clientFd, $0, strlen($0)) }
                return params
            }
            throw CloudSyncError.invalidCredentials("Google Sign-In timed out. Please try again.")
        }.value
    }

    private static func formUrlEncode(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    // MARK: - Token Management & Helpers

    private struct AuthTokenResponse: Decodable {
        let idToken: String
        let refreshToken: String
        let expiresIn: String
        let localId: String
        let email: String?

        enum CodingKeys: String, CodingKey {
            case idToken
            case refreshToken
            case expiresIn
            case localId
            case email
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            self.idToken = try c.decode(String.self, forKey: .idToken)
            self.refreshToken = (try? c.decode(String.self, forKey: .refreshToken)) ?? ""
            if let expStr = try? c.decode(String.self, forKey: .expiresIn) {
                self.expiresIn = expStr
            } else if let expInt = try? c.decode(Int.self, forKey: .expiresIn) {
                self.expiresIn = String(expInt)
            } else {
                self.expiresIn = "3600"
            }
            self.localId = try c.decode(String.self, forKey: .localId)
            self.email = try? c.decodeIfPresent(String.self, forKey: .email)
        }
    }

    private func callAuthEndpoint(endpoint: String, apiKey: String, body: [String: Any]) async throws -> AuthTokenResponse {
        guard let url = URL(string: "https://identitytoolkit.googleapis.com/v1/\(endpoint)?key=\(apiKey)") else {
            throw CloudSyncError.networkFailure("Invalid Firebase configuration URL.")
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, res) = try await session.data(for: req)
        if let http = res as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw CloudSyncError.invalidCredentials(extractFirebaseError(from: data) ?? "Authentication failed (HTTP \(http.statusCode)).")
        }
        return try JSONDecoder().decode(AuthTokenResponse.self, from: data)
    }

    private func validIdToken() async throws -> String {
        guard var current = authSession else {
            throw CloudSyncError.invalidCredentials("Not signed in.")
        }
        let now = Int64(Date().timeIntervalSince1970 * 1000.0)
        if now < current.expiresAtMillis && !current.idToken.isEmpty {
            return current.idToken
        }
        guard !current.refreshToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw CloudSyncError.invalidCredentials("Cloud session expired. Please sign out and sign in with Google or Email.")
        }

        let config = FirebaseConfig.loadActive()
        guard let url = URL(string: "https://securetoken.googleapis.com/v1/token?key=\(config.apiKey)") else {
            throw CloudSyncError.networkFailure("Invalid Firebase configuration URL.")
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let form = "grant_type=refresh_token&refresh_token=\(Self.formUrlEncode(current.refreshToken))"
        req.httpBody = Data(form.utf8)

        let (data, res) = try await session.data(for: req)
        if let http = res as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            // Only clear refreshToken on permanent HTTP 400 auth errors (INVALID_REFRESH_TOKEN / TOKEN_EXPIRED / USER_DISABLED),
            // NEVER on transient 5xx / 429 / gateway errors.
            if http.statusCode == 400 {
                current.idToken = ""
                current.refreshToken = ""
                current.expiresAtMillis = 0
                self.authSession = current
                persistAuthSession(current)
                throw CloudSyncError.invalidCredentials("Cloud session expired. Please sign out and sign in with Google or Email.")
            }
            throw CloudSyncError.networkFailure(
                extractFirebaseError(from: data) ?? "Temporary token refresh failure (HTTP \(http.statusCode))."
            )
        }
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let newIdToken = obj["id_token"] as? String else {
            throw CloudSyncError.networkFailure("Invalid token refresh response.")
        }
        let newRefresh = (obj["refresh_token"] as? String) ?? current.refreshToken
        let expSec = Int64((obj["expires_in"] as? String) ?? "3600") ?? 3600
        current.idToken = newIdToken
        current.refreshToken = newRefresh
        current.expiresAtMillis = now + max(60, expSec - 60) * 1000
        self.authSession = current
        persistAuthSession(current)
        return newIdToken
    }

    private func invalidateCachedIdTokenExpiry() {
        guard var current = authSession else { return }
        current.expiresAtMillis = 0
        self.authSession = current
        persistAuthSession(current)
    }

    private func persistAuthSession(_ s: PersistedAuthSession) {
        if let data = try? JSONEncoder().encode(s), let str = String(data: data, encoding: .utf8) {
            KeychainStore.saveString(str, account: Self.keychainAccount)
        }
    }

    private func loadLastSyncCursor(uid: String) -> Int64 {
        Int64(UserDefaults.standard.integer(forKey: "\(Self.lastSyncCursorPrefix)\(uid)"))
    }

    private func saveLastSyncCursor(uid: String, timestamp: Int64) {
        UserDefaults.standard.set(Int(timestamp), forKey: "\(Self.lastSyncCursorPrefix)\(uid)")
    }

    private func loadCollectionSyncCursor(uid: String, collection: String) -> Int64 {
        let key = Self.collectionCursorDefaultsKey(uid: uid, collection: collection)
        let saved = Int64(UserDefaults.standard.integer(forKey: key))
        if saved > 0 { return saved }
        return loadLastSyncCursor(uid: uid)
    }

    private func saveCollectionSyncCursor(uid: String, collection: String, timestamp: Int64) {
        let key = Self.collectionCursorDefaultsKey(uid: uid, collection: collection)
        UserDefaults.standard.set(Int(timestamp), forKey: key)
    }

    private func resetCollectionSyncCursors(uid: String) {
        for col in Self.syncCollectionNames {
            saveCollectionSyncCursor(uid: uid, collection: col, timestamp: 0)
        }
    }

    private func loadLastWallSyncTimestamp(uid: String) -> Int64 {
        Int64(UserDefaults.standard.integer(forKey: "\(Self.lastWallSyncPrefix)\(uid)"))
    }

    private func saveLastWallSyncTimestamp(uid: String, timestamp: Int64) {
        UserDefaults.standard.set(Int(timestamp), forKey: "\(Self.lastWallSyncPrefix)\(uid)")
    }

    private func updateSnapshot(_ next: CloudSyncSnapshot) {
        self.snapshot = next
        onStateChange?(next)
    }

    private func updateSnapshot(patchStatus: CloudSyncStatus, error: String?) {
        var copy = snapshot
        copy.status = patchStatus
        copy.errorMessage = error
        updateSnapshot(copy)
    }

    private func extractFirebaseError(from data: Data) -> String? {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let err = root["error"] as? [String: Any],
              let msg = err["message"] as? String else { return nil }
        return msg
    }

    public static func sha256Hex(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Firestore Value Marshaling

    private static func fsString(_ v: String) -> [String: Any] { ["stringValue": v] }
    private static func fsNullableString(_ v: String?) -> [String: Any] {
        guard let v, !v.isEmpty else { return ["nullValue": NSNull()] }
        return ["stringValue": v]
    }
    private static func fsInt(_ v: Int64) -> [String: Any] { ["integerValue": String(v)] }
    private static func fsNullableInt(_ v: Int64?) -> [String: Any] {
        guard let v else { return ["nullValue": NSNull()] }
        return ["integerValue": String(v)]
    }
    private static func fsDouble(_ v: Double) -> [String: Any] { ["doubleValue": v] }
    private static func fsBool(_ v: Bool) -> [String: Any] { ["booleanValue": v] }
    private static func fsStringArray(_ arr: [String]) -> [String: Any] {
        ["arrayValue": ["values": arr.map { ["stringValue": $0] }]]
    }

    public static func makeUpdateWrite(
        path: String,
        fields: [String: [String: Any]],
        updateMaskFieldPaths: [String]? = nil
    ) -> [String: Any] {
        var write: [String: Any] = [
            "update": [
                "name": path,
                "fields": fields
            ]
        ]
        if let mask = updateMaskFieldPaths {
            write["updateMask"] = ["fieldPaths": mask]
        }
        return write
    }

    public static func makeEntityUpdateWrite(
        path: String,
        fields: [String: [String: Any]]
    ) -> [String: Any] {
        makeUpdateWrite(
            path: path,
            fields: fields,
            updateMaskFieldPaths: Array(fields.keys).sorted()
        )
    }

    public static func makeAttachmentWrite(
        baseDocPrefix: String,
        attachment att: TaskAttachment,
        inlineBase64 inlineB64: String
    ) -> [String: Any] {
        var fields: [String: [String: Any]] = [
            "id": fsString(att.id),
            "taskId": fsNullableString(att.taskId),
            "projectId": fsNullableString(att.projectId),
            "fileName": fsString(att.fileName),
            "mimeType": fsString(att.mimeType),
            "sizeBytes": fsInt(att.sizeBytes),
            "localRelativePath": fsString(""),
            "sortOrder": fsDouble(Double(att.createdAt)),
            "createdAt": fsInt(att.createdAt),
            "updatedAt": fsInt(att.updatedAt),
            "isDeleted": fsBool(att.isDeleted)
        ]
        if att.isDeleted {
            fields["dataBase64"] = fsString("")
            return makeUpdateWrite(path: "\(baseDocPrefix)/attachments/\(att.id)", fields: fields)
        } else if !inlineB64.isEmpty {
            fields["dataBase64"] = fsString(inlineB64)
            return makeUpdateWrite(path: "\(baseDocPrefix)/attachments/\(att.id)", fields: fields)
        } else {
            // Raw bytes > 700_000 or unavailable locally: update metadata fields ONLY so existing Firestore dataBase64 is never wiped!
            let metadataFieldPaths = [
                "id", "taskId", "projectId", "fileName", "mimeType",
                "sizeBytes", "localRelativePath", "sortOrder", "createdAt", "updatedAt", "isDeleted"
            ]
            return makeUpdateWrite(
                path: "\(baseDocPrefix)/attachments/\(att.id)",
                fields: fields,
                updateMaskFieldPaths: metadataFieldPaths
            )
        }
    }

    public static func unwrapFirestoreValue(_ wrapper: [String: Any]) -> Any? {
        if wrapper["nullValue"] != nil { return nil }
        if let s = wrapper["stringValue"] as? String { return s }
        if let b = wrapper["booleanValue"] as? Bool { return b }
        if let iStr = wrapper["integerValue"] as? String, let i = Int64(iStr) { return i }
        if let iNum = wrapper["integerValue"] as? NSNumber { return iNum.int64Value }
        if let dNum = wrapper["doubleValue"] as? NSNumber { return dNum.doubleValue }
        if let dStr = wrapper["doubleValue"] as? String, let d = Double(dStr) { return d }
        if let arrObj = wrapper["arrayValue"] as? [String: Any],
           let vals = arrObj["values"] as? [[String: Any]] {
            return vals.compactMap { unwrapFirestoreValue($0) }
        }
        return nil
    }

    public static func asInt64(_ any: Any?) -> Int64? {
        if let i = any as? Int64 { return i }
        if let i = any as? Int { return Int64(i) }
        if let d = any as? Double { return d.isFinite ? Int64(d) : nil }
        if let n = any as? NSNumber { return n.int64Value }
        if let s = any as? String {
            if let parsed = Int64(s) { return parsed }
            if let parsedDouble = Double(s), parsedDouble.isFinite { return Int64(parsedDouble) }
        }
        return nil
    }

    public static func asDouble(_ any: Any?, fallback: Double = 0) -> Double {
        if let d = any as? Double { return d.isFinite ? d : fallback }
        if let i = any as? Int64 { return Double(i) }
        if let i = any as? Int { return Double(i) }
        if let n = any as? NSNumber {
            let d = n.doubleValue
            return d.isFinite ? d : fallback
        }
        if let s = any as? String, let parsed = Double(s), parsed.isFinite { return parsed }
        return fallback
    }

    private static func asBool(_ any: Any?, fallback: Bool = false) -> Bool {
        if let b = any as? Bool { return b }
        if let n = any as? NSNumber { return n.boolValue }
        if let s = any as? String {
            return s.lowercased() == "true" || s == "1"
        }
        return fallback
    }

    private static func asString(_ any: Any?) -> String? {
        guard let s = any as? String else { return nil }
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }

    private static func asStringList(_ any: Any?) -> [String] {
        guard let arr = any as? [Any] else { return [] }
        return arr.compactMap { asString($0) }
    }

    public static func parseTag(_ map: [String: Any]) -> Tag? {
        guard let id = asString(map["id"]) else { return nil }
        return Tag(
            id: id,
            title: (map["title"] as? String) ?? "",
            parentTagId: asString(map["parentTagId"]),
            shortcut: asString(map["shortcut"]),
            sortOrder: asDouble(map["sortOrder"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: asBool(map["isDeleted"])
        )
    }

    public static func parseArea(_ map: [String: Any]) -> Area? {
        guard let id = asString(map["id"]) else { return nil }
        return Area(
            id: id,
            title: (map["title"] as? String) ?? "",
            sortOrder: asDouble(map["sortOrder"]),
            tagIds: asStringList(map["tagIds"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: asBool(map["isDeleted"])
        )
    }

    public static func parseProject(_ map: [String: Any]) -> Project? {
        parseProjectWithHints(map)?.project
    }

    public static func parseProjectWithHints(_ map: [String: Any]) -> (project: Project, omittedV30Fields: Bool)? {
        guard let id = asString(map["id"]) else { return nil }
        let isDeleted = asBool(map["isDeleted"])
        let purged = isDeleted && asBool(map["purgedFromTrash"])
        let omittedV30Fields = !map.keys.contains("workspaceId") && !map.keys.contains("viewMode")
        let project = Project(
            id: id,
            areaId: asString(map["areaId"]),
            title: purged ? "" : ((map["title"] as? String) ?? ""),
            notes: purged ? "" : ((map["notes"] as? String) ?? ""),
            status: ProjectStatus.normalized(map["status"] as? String),
            scheduleState: ScheduleState.normalized(map["scheduleState"] as? String, fallback: .anytime),
            whenDate: asInt64(map["whenDate"]),
            deadline: asInt64(map["deadline"]),
            completedAt: asInt64(map["completedAt"]),
            lastReviewedAt: asInt64(map["lastReviewedAt"]),
            sortOrder: asDouble(map["sortOrder"]),
            tagIds: asStringList(map["tagIds"]),
            workspaceId: purged ? nil : asString(map["workspaceId"]),
            viewMode: purged ? nil : asString(map["viewMode"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: isDeleted
        )
        return (project, omittedV30Fields)
    }

    public static func parseHeading(_ map: [String: Any]) -> Heading? {
        parseHeadingWithHints(map)?.heading
    }

    public static func parseHeadingWithHints(_ map: [String: Any]) -> (heading: Heading, omittedStartDate: Bool, omittedEndDate: Bool)? {
        guard let id = asString(map["id"]), let projectId = asString(map["projectId"]) else { return nil }
        let omittedStartDate = !map.keys.contains("startDate")
        let omittedEndDate = !map.keys.contains("endDate")
        let heading = Heading(
            id: id,
            projectId: projectId,
            title: (map["title"] as? String) ?? "",
            sortOrder: asDouble(map["sortOrder"]),
            isArchived: asBool(map["isArchived"]),
            startDate: asInt64(map["startDate"]),
            endDate: asInt64(map["endDate"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: asBool(map["isDeleted"])
        )
        return (heading, omittedStartDate, omittedEndDate)
    }

    public static func parseTaskWithHints(
        _ map: [String: Any]
    ) -> (task: TaskItem, tagIds: [String], omittedTimeblock: Bool, omittedPin: Bool, omittedV30Fields: Bool)? {
        guard let id = asString(map["id"]) else { return nil }
        var rule: RecurrenceRule? = nil
        if let recStr = asString(map["recurrenceRule"]), let data = recStr.data(using: .utf8) {
            rule = try? JSONDecoder().decode(RecurrenceRule.self, from: data)
        }
        let tids = asStringList(map["tagIds"])
        let isDeleted = asBool(map["isDeleted"])
        let purged = isDeleted && asBool(map["purgedFromTrash"])
        let omittedTimeblock = !map.keys.contains("scheduledStartMinutes") && !map.keys.contains("durationMinutes")
        let omittedPin = !map.keys.contains("isPinned")
        let omittedV30Fields = !map.keys.contains("effortLevel")
            && !map.keys.contains("blockedByTaskIds")
            && !map.keys.contains("blockedByJson")
            && !map.keys.contains("workspaceId")
            && !map.keys.contains("assigneeId")

        var blockedIds = asStringList(map["blockedByTaskIds"])
        if blockedIds.isEmpty,
           let rawBlockedJson = asString(map["blockedByJson"]),
           let bData = rawBlockedJson.data(using: .utf8),
           let decoded = try? JSONDecoder().decode([String].self, from: bData) {
            blockedIds = decoded
        }

        let task = TaskItem(
            id: id,
            title: purged ? "" : ((map["title"] as? String) ?? ""),
            notes: purged ? "" : ((map["notes"] as? String) ?? ""),
            status: TaskStatus.normalized(map["status"] as? String),
            scheduleState: ScheduleState.normalized(map["scheduleState"] as? String, fallback: .inbox),
            whenDate: asInt64(map["whenDate"]),
            deadline: asInt64(map["deadline"]),
            reminderTime: asInt64(map["reminderTime"]),
            scheduledStartMinutes: asInt64(map["scheduledStartMinutes"]).map(Int.init),
            durationMinutes: asInt64(map["durationMinutes"]).map(Int.init),
            completedAt: asInt64(map["completedAt"]),
            projectId: asString(map["projectId"]),
            headingId: asString(map["headingId"]),
            areaId: asString(map["areaId"]),
            recurrenceRule: rule,
            sortOrder: asDouble(map["sortOrder"]),
            isPinned: !isDeleted && asBool(map["isPinned"]),
            effortLevel: isDeleted ? nil : TaskEffortLevel.normalized(asString(map["effortLevel"]))?.rawValue,
            blockedByTaskIds: isDeleted ? [] : blockedIds.filter { $0 != id && !$0.isEmpty },
            workspaceId: isDeleted ? nil : asString(map["workspaceId"]),
            assigneeId: isDeleted ? nil : asString(map["assigneeId"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: isDeleted
        )
        return (task, tids, omittedTimeblock, omittedPin, omittedV30Fields)
    }

    public static func parseTask(_ map: [String: Any]) -> (TaskItem, [String])? {
        guard let parsed = parseTaskWithHints(map) else { return nil }
        return (parsed.task, parsed.tagIds)
    }

    public static func parseChecklist(_ map: [String: Any]) -> ChecklistItem? {
        guard let id = asString(map["id"]), let taskId = asString(map["taskId"]) else { return nil }
        return ChecklistItem(
            id: id,
            taskId: taskId,
            title: (map["title"] as? String) ?? "",
            isCompleted: asBool(map["isCompleted"]),
            sortOrder: asDouble(map["sortOrder"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: asBool(map["isDeleted"])
        )
    }

    public static func parseAttachment(_ map: [String: Any]) -> RemoteAttachmentDelta? {
        guard let id = asString(map["id"]) else { return nil }
        // Security: never trust remote `localRelativePath` strings from peers so path traversal outside `attachments/` is impossible.
        let att = TaskAttachment(
            id: id,
            taskId: asString(map["taskId"]),
            projectId: asString(map["projectId"]),
            fileName: (map["fileName"] as? String) ?? "attachment",
            mimeType: (map["mimeType"] as? String) ?? "application/octet-stream",
            sizeBytes: asInt64(map["sizeBytes"]) ?? 0,
            localRelativePath: "",
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: asBool(map["isDeleted"])
        )
        let dataB64 = (map["dataBase64"] as? String) ?? ""
        return RemoteAttachmentDelta(attachment: att, dataBase64: dataB64)
    }

    public static func parseProjectTemplate(_ map: [String: Any]) -> ProjectTemplate? {
        guard let id = asString(map["id"]) else { return nil }
        var tids = asStringList(map["tagIds"])
        if tids.isEmpty,
           let rawJson = asString(map["tagIdsJson"]),
           let data = rawJson.data(using: .utf8),
           let decoded = try? JSONDecoder().decode([String].self, from: data) {
            tids = decoded
        }
        return ProjectTemplate(
            id: id,
            title: (map["title"] as? String) ?? "",
            notes: (map["notes"] as? String) ?? "",
            defaultAreaId: asString(map["defaultAreaId"]),
            tagIds: tids,
            blueprintJson: (map["blueprintJson"] as? String) ?? "{}",
            sortOrder: asDouble(map["sortOrder"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: asBool(map["isDeleted"]),
            isDirty: false
        )
    }
}
