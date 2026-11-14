import Foundation
import CryptoKit

public struct FirebaseConfig: Codable, Equatable, Sendable {
    public var projectId: String
    public var apiKey: String
    public var appId: String

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
              let decoded = try? JSONDecoder().decode(FirebaseConfig.self, from: data),
              !decoded.projectId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !decoded.apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return .defaultProduction
        }
        return decoded
    }

    public static func saveCustom(_ config: FirebaseConfig?, defaults: UserDefaults = .standard) {
        if let config,
           !config.projectId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
           !config.apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            if let data = try? JSONEncoder().encode(config) {
                defaults.set(data, forKey: storageKey)
            }
        } else {
            defaults.removeObject(forKey: storageKey)
        }
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

/// Pure URLSession Firebase Auth + Firestore v1 Delta Sync engine matching Android (`FirebaseSyncManager.kt`) & ChromeOS (`cloudSync.ts`).
public actor FirebaseCloudSyncService {
    private let database: ThingsSQLiteDatabase
    private let session: URLSession
    private var authSession: PersistedAuthSession?
    private var snapshot = CloudSyncSnapshot()
    private var onStateChange: (@Sendable (CloudSyncSnapshot) -> Void)?
    private var isSyncing = false

    private static let keychainAccount = "firebase_auth_session_v1"
    private static let lastSyncPrefix = "thingsbook_macos_last_sync_ts_"

    public init(database: ThingsSQLiteDatabase, session: URLSession = .shared) {
        self.database = database
        self.session = session
    }

    public func setObserver(_ observer: @escaping @Sendable (CloudSyncSnapshot) -> Void) {
        self.onStateChange = observer
        observer(snapshot)
    }

    public func currentSnapshot() -> CloudSyncSnapshot {
        snapshot
    }

    public func restoreSessionIfAvailable(localOnlyMode: Bool) async {
        guard let raw = KeychainStore.loadString(account: Self.keychainAccount),
              let data = raw.data(using: .utf8),
              let saved = try? JSONDecoder().decode(PersistedAuthSession.self, from: data) else {
            updateSnapshot(CloudSyncSnapshot(status: .offline))
            return
        }
        self.authSession = saved
        let lastSync = loadLastSyncTimestamp(uid: saved.uid)
        updateSnapshot(
            CloudSyncSnapshot(
                status: localOnlyMode ? .offline : .syncing,
                userEmailOrId: saved.email,
                userId: saved.uid,
                lastSyncedAt: lastSync,
                errorMessage: nil
            )
        )
        if !localOnlyMode {
            await syncBidirectionalDeltas(forceFullPull: false, localOnlyMode: false)
        }
    }

    // MARK: - Authentication Flows

    public func signInEmail(email rawEmail: String, password: String, mergeLocalTasks: Bool, localOnlyMode: Bool) async throws -> String {
        let email = rawEmail.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard email.contains("@"), password.count >= 6 else {
            throw CloudSyncError.invalidCredentials("Please enter a valid email address and password (min. 6 characters).")
        }
        let config = FirebaseConfig.loadActive()
        updateSnapshot(patchStatus: .syncing, error: nil)

        do {
            let authRes = try await callAuthEndpoint(
                endpoint: "accounts:signInWithPassword",
                apiKey: config.apiKey,
                body: ["email": email, "password": password, "returnSecureToken": true]
            )
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
                        "A custom password is already set for \(email). Enter your password above and click Sign In."
                    )
                }
            }
            let topic = "tb_sync_" + String(Self.sha256Hex("thingsbook-instant:\(email)").prefix(28))
            try await finalizeLogin(authRes: authRes, email: email, topic: topic, mergeLocalTasks: mergeLocalTasks, localOnlyMode: localOnlyMode)
            return email
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
        let url = URL(string: "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=\(config.apiKey)")!
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
        authSession = nil
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
        saveLastSyncTimestamp(uid: authRes.localId, timestamp: 0)

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
            await syncBidirectionalDeltas(forceFullPull: true, localOnlyMode: false)
        }
    }

    // MARK: - Bidirectional Firestore v1 Delta Sync

    public func syncBidirectionalDeltas(forceFullPull: Bool = false, localOnlyMode: Bool) async {
        if localOnlyMode {
            updateSnapshot(patchStatus: .offline, error: nil)
            return
        }
        guard !isSyncing, let current = authSession else { return }
        isSyncing = true
        defer { isSyncing = false }

        updateSnapshot(patchStatus: .syncing, error: nil)

        do {
            let validToken = try await validIdToken()
            let config = FirebaseConfig.loadActive()
            let lastSync = forceFullPull ? 0 : loadLastSyncTimestamp(uid: current.uid)

            try await pushDirtyBatchToFirestore(uid: current.uid, idToken: validToken, projectId: config.projectId)
            let pulledMax = try await pullDeltasFromFirestore(uid: current.uid, idToken: validToken, projectId: config.projectId, sinceTimestamp: lastSync)

            if !current.channelTopic.isEmpty {
                try? await pollSecondaryNtfyRelay(uid: current.uid, topic: current.channelTopic, deviceId: current.deviceId, sinceTimestamp: lastSync)
            }

            let now = Int64(Date().timeIntervalSince1970 * 1000.0)
            let nextTs = pulledMax > lastSync ? pulledMax : (lastSync == 0 ? now : lastSync)
            saveLastSyncTimestamp(uid: current.uid, timestamp: nextTs)
            updateSnapshot(
                CloudSyncSnapshot(
                    status: .synced,
                    userEmailOrId: current.email,
                    userId: current.uid,
                    lastSyncedAt: nextTs,
                    errorMessage: nil
                )
            )
        } catch {
            updateSnapshot(patchStatus: .offline, error: error.localizedDescription)
        }
    }

    private func pushDirtyBatchToFirestore(uid: String, idToken: String, projectId: String) async throws {
        let dirty = try database.fetchDirtyBatch()
        var writes: [[String: Any]] = []
        let baseDocPrefix = "projects/\(projectId)/databases/(default)/documents/users/\(uid)"

        for tag in dirty.tags {
            writes.append(makeUpdateWrite(path: "\(baseDocPrefix)/tags/\(tag.id)", fields: [
                "id": fsString(tag.id),
                "title": fsString(tag.title),
                "parentTagId": fsNullableString(tag.parentTagId),
                "shortcut": fsNullableString(tag.shortcut),
                "sortOrder": fsDouble(tag.sortOrder),
                "createdAt": fsInt(tag.createdAt),
                "updatedAt": fsInt(tag.updatedAt),
                "isDeleted": fsBool(tag.isDeleted)
            ]))
        }

        for area in dirty.areas {
            writes.append(makeUpdateWrite(path: "\(baseDocPrefix)/areas/\(area.id)", fields: [
                "id": fsString(area.id),
                "title": fsString(area.title),
                "sortOrder": fsDouble(area.sortOrder),
                "tagIds": fsStringArray(area.tagIds),
                "createdAt": fsInt(area.createdAt),
                "updatedAt": fsInt(area.updatedAt),
                "isDeleted": fsBool(area.isDeleted)
            ]))
        }

        for proj in dirty.projects {
            writes.append(makeUpdateWrite(path: "\(baseDocPrefix)/projects/\(proj.id)", fields: [
                "id": fsString(proj.id),
                "areaId": fsNullableString(proj.areaId),
                "title": fsString(proj.title),
                "notes": fsString(proj.notes),
                "status": fsString(proj.status.rawValue),
                "scheduleState": fsString(proj.scheduleState.rawValue),
                "whenDate": fsNullableInt(proj.whenDate),
                "deadline": fsNullableInt(proj.deadline),
                "completedAt": fsNullableInt(proj.completedAt),
                "sortOrder": fsDouble(proj.sortOrder),
                "tagIds": fsStringArray(proj.tagIds),
                "createdAt": fsInt(proj.createdAt),
                "updatedAt": fsInt(proj.updatedAt),
                "isDeleted": fsBool(proj.isDeleted)
            ]))
        }

        for head in dirty.headings {
            writes.append(makeUpdateWrite(path: "\(baseDocPrefix)/headings/\(head.id)", fields: [
                "id": fsString(head.id),
                "projectId": fsString(head.projectId),
                "title": fsString(head.title),
                "sortOrder": fsDouble(head.sortOrder),
                "isArchived": fsBool(head.isArchived),
                "createdAt": fsInt(head.createdAt),
                "updatedAt": fsInt(head.updatedAt),
                "isDeleted": fsBool(head.isDeleted)
            ]))
        }

        let jsonEncoder = JSONEncoder()
        for task in dirty.tasks {
            let recString: String? = task.recurrenceRule.flatMap {
                guard let d = try? jsonEncoder.encode($0) else { return nil }
                return String(data: d, encoding: .utf8)
            }
            writes.append(makeUpdateWrite(path: "\(baseDocPrefix)/tasks/\(task.id)", fields: [
                "id": fsString(task.id),
                "title": fsString(task.title),
                "notes": fsString(task.notes),
                "status": fsString(task.status.rawValue),
                "scheduleState": fsString(task.scheduleState.rawValue),
                "whenDate": fsNullableInt(task.whenDate),
                "deadline": fsNullableInt(task.deadline),
                "reminderTime": fsNullableInt(task.reminderTime),
                "completedAt": fsNullableInt(task.completedAt),
                "projectId": fsNullableString(task.projectId),
                "headingId": fsNullableString(task.headingId),
                "areaId": fsNullableString(task.areaId),
                "recurrenceRule": fsNullableString(recString),
                "sortOrder": fsDouble(task.sortOrder),
                "tagIds": fsStringArray(task.tagIds),
                "createdAt": fsInt(task.createdAt),
                "updatedAt": fsInt(task.updatedAt),
                "isDeleted": fsBool(task.isDeleted)
            ]))
        }

        for chk in dirty.checklistItems {
            writes.append(makeUpdateWrite(path: "\(baseDocPrefix)/checklist_items/\(chk.id)", fields: [
                "id": fsString(chk.id),
                "taskId": fsString(chk.taskId),
                "title": fsString(chk.title),
                "isCompleted": fsBool(chk.isCompleted),
                "sortOrder": fsDouble(chk.sortOrder),
                "createdAt": fsInt(chk.createdAt),
                "updatedAt": fsInt(chk.updatedAt),
                "isDeleted": fsBool(chk.isDeleted)
            ]))
        }

        guard !writes.isEmpty else { return }

        let commitURL = URL(string: "https://firestore.googleapis.com/v1/projects/\(projectId)/databases/(default)/documents:commit")!
        let chunkSize = 450
        var idx = 0
        while idx < writes.count {
            let slice = Array(writes[idx..<min(idx + chunkSize, writes.count)])
            var req = URLRequest(url: commitURL)
            req.httpMethod = "POST"
            req.setValue("Bearer \(idToken)", forHTTPHeaderField: "Authorization")
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: ["writes": slice])
            let (data, res) = try await session.data(for: req)
            if let http = res as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                throw CloudSyncError.networkFailure(extractFirebaseError(from: data) ?? "Firestore commit failed (HTTP \(http.statusCode)).")
            }
            idx += chunkSize
        }

        try database.markDirtyBatchSynced(dirty)
    }

    private func pullDeltasFromFirestore(uid: String, idToken: String, projectId: String, sinceTimestamp: Int64) async throws -> Int64 {
        async let rawTags = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "tags", since: sinceTimestamp)
        async let rawAreas = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "areas", since: sinceTimestamp)
        async let rawProjects = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "projects", since: sinceTimestamp)
        async let rawHeadings = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "headings", since: sinceTimestamp)
        async let rawTasks = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "tasks", since: sinceTimestamp)
        async let rawChecklists = queryCollection(projectId: projectId, uid: uid, idToken: idToken, collection: "checklist_items", since: sinceTimestamp)

        let (tagMaps, areaMaps, projMaps, headMaps, taskMaps, chkMaps) = try await (
            rawTags, rawAreas, rawProjects, rawHeadings, rawTasks, rawChecklists
        )

        let tags = tagMaps.compactMap(Self.parseTag)
        let areas = areaMaps.compactMap(Self.parseArea)
        let projects = projMaps.compactMap(Self.parseProject)
        let headings = headMaps.compactMap(Self.parseHeading)
        var taskTagIds: [String: [String]] = [:]
        let tasks = taskMaps.compactMap { map -> TaskItem? in
            guard let (t, tids) = Self.parseTask(map) else { return nil }
            taskTagIds[t.id] = tids
            return t
        }
        let checklists = chkMaps.compactMap(Self.parseChecklist)

        if tags.isEmpty && areas.isEmpty && projects.isEmpty && headings.isEmpty && tasks.isEmpty && checklists.isEmpty {
            return sinceTimestamp
        }

        let batch = RemoteDeltaBatch(
            tags: tags,
            areas: areas,
            projects: projects,
            headings: headings,
            tasks: tasks,
            taskTagIdsByTaskId: taskTagIds,
            checklistItems: checklists
        )
        return try database.applyRemoteDeltas(batch)
    }

    private func queryCollection(
        projectId: String,
        uid: String,
        idToken: String,
        collection: String,
        since: Int64
    ) async throws -> [[String: Any]] {
        let url = URL(string: "https://firestore.googleapis.com/v1/projects/\(projectId)/databases/(default)/documents/users/\(uid):runQuery")!
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
                if let val = Self.unwrapFirestoreValue(v) {
                    plain[k] = val
                }
            }
            unpacked.append(plain)
        }
        return unpacked
    }

    private func pollSecondaryNtfyRelay(uid: String, topic: String, deviceId: String, sinceTimestamp: Int64) async throws {
        guard let encoded = topic.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed),
              let url = URL(string: "https://ntfy.sh/\(encoded)/json?poll=1&since=24h") else { return }
        var req = URLRequest(url: url)
        req.timeoutInterval = 3.5
        let (data, res) = try await session.data(for: req)
        guard let http = res as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let text = String(data: data, encoding: .utf8) else { return }

        for line in text.split(separator: "\n") {
            guard let lineData = line.data(using: .utf8),
                  let env = try? JSONSerialization.jsonObject(with: lineData) as? [String: Any],
                  env["event"] as? String == "message",
                  let msgStr = env["message"] as? String,
                  let msgData = msgStr.data(using: .utf8),
                  let batch = try? JSONSerialization.jsonObject(with: msgData) as? [String: Any],
                  batch["userId"] as? String == uid else { continue }

            let batchTs = (batch["batchTimestamp"] as? NSNumber)?.int64Value ?? 0
            if batch["deviceId"] as? String == deviceId, sinceTimestamp > 0, batchTs <= sinceTimestamp {
                continue
            }

            let tags = (batch["tags"] as? [[String: Any]] ?? []).compactMap(Self.parseTag)
            let areas = (batch["areas"] as? [[String: Any]] ?? []).compactMap(Self.parseArea)
            let projects = (batch["projects"] as? [[String: Any]] ?? []).compactMap(Self.parseProject)
            let headings = (batch["headings"] as? [[String: Any]] ?? []).compactMap(Self.parseHeading)
            var taskTags: [String: [String]] = [:]
            let tasks = (batch["tasks"] as? [[String: Any]] ?? []).compactMap { m -> TaskItem? in
                guard let (t, tids) = Self.parseTask(m) else { return nil }
                taskTags[t.id] = tids
                return t
            }
            let checklists = (batch["checklistItems"] as? [[String: Any]] ?? []).compactMap(Self.parseChecklist)

            _ = try? database.applyRemoteDeltas(
                RemoteDeltaBatch(
                    tags: tags,
                    areas: areas,
                    projects: projects,
                    headings: headings,
                    tasks: tasks,
                    taskTagIdsByTaskId: taskTags,
                    checklistItems: checklists
                )
            )
        }
    }

    // MARK: - Token Management & Helpers

    private struct AuthTokenResponse: Decodable {
        let idToken: String
        let refreshToken: String
        let expiresIn: String
        let localId: String
    }

    private func callAuthEndpoint(endpoint: String, apiKey: String, body: [String: Any]) async throws -> AuthTokenResponse {
        let url = URL(string: "https://identitytoolkit.googleapis.com/v1/\(endpoint)?key=\(apiKey)")!
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

        let config = FirebaseConfig.loadActive()
        let url = URL(string: "https://securetoken.googleapis.com/v1/token?key=\(config.apiKey)")!
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let form = "grant_type=refresh_token&refresh_token=\(current.refreshToken.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
        req.httpBody = Data(form.utf8)

        let (data, res) = try await session.data(for: req)
        if let http = res as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw CloudSyncError.invalidCredentials("Cloud session expired. Please sign in again.")
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

    private func persistAuthSession(_ s: PersistedAuthSession) {
        if let data = try? JSONEncoder().encode(s), let str = String(data: data, encoding: .utf8) {
            KeychainStore.saveString(str, account: Self.keychainAccount)
        }
    }

    private func loadLastSyncTimestamp(uid: String) -> Int64 {
        Int64(UserDefaults.standard.integer(forKey: "\(Self.lastSyncPrefix)\(uid)"))
    }

    private func saveLastSyncTimestamp(uid: String, timestamp: Int64) {
        UserDefaults.standard.set(Int(timestamp), forKey: "\(Self.lastSyncPrefix)\(uid)")
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

    private func fsString(_ v: String) -> [String: Any] { ["stringValue": v] }
    private func fsNullableString(_ v: String?) -> [String: Any] {
        guard let v, !v.isEmpty else { return ["nullValue": NSNull()] }
        return ["stringValue": v]
    }
    private func fsInt(_ v: Int64) -> [String: Any] { ["integerValue": String(v)] }
    private func fsNullableInt(_ v: Int64?) -> [String: Any] {
        guard let v else { return ["nullValue": NSNull()] }
        return ["integerValue": String(v)]
    }
    private func fsDouble(_ v: Double) -> [String: Any] { ["doubleValue": v] }
    private func fsBool(_ v: Bool) -> [String: Any] { ["booleanValue": v] }
    private func fsStringArray(_ arr: [String]) -> [String: Any] {
        ["arrayValue": ["values": arr.map { ["stringValue": $0] }]]
    }

    private func makeUpdateWrite(path: String, fields: [String: [String: Any]]) -> [String: Any] {
        [
            "update": [
                "name": path,
                "fields": fields
            ]
        ]
    }

    private static func unwrapFirestoreValue(_ wrapper: [String: Any]) -> Any? {
        if wrapper["nullValue"] != nil { return nil }
        if let s = wrapper["stringValue"] as? String { return s }
        if let b = wrapper["booleanValue"] as? Bool { return b }
        if let iStr = wrapper["integerValue"] as? String, let i = Int64(iStr) { return i }
        if let iNum = wrapper["integerValue"] as? NSNumber { return iNum.int64Value }
        if let dNum = wrapper["doubleValue"] as? NSNumber { return dNum.doubleValue }
        if let arrObj = wrapper["arrayValue"] as? [String: Any],
           let vals = arrObj["values"] as? [[String: Any]] {
            return vals.compactMap { unwrapFirestoreValue($0) }
        }
        return nil
    }

    private static func asInt64(_ any: Any?) -> Int64? {
        if let i = any as? Int64 { return i }
        if let i = any as? Int { return Int64(i) }
        if let n = any as? NSNumber { return n.int64Value }
        if let s = any as? String, let parsed = Int64(s) { return parsed }
        return nil
    }

    private static func asDouble(_ any: Any?, fallback: Double = 0) -> Double {
        if let d = any as? Double { return d }
        if let n = any as? NSNumber { return n.doubleValue }
        if let s = any as? String, let parsed = Double(s) { return parsed }
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

    private static func parseTag(_ map: [String: Any]) -> Tag? {
        guard let id = asString(map["id"]) else { return nil }
        return Tag(
            id: id,
            title: (map["title"] as? String) ?? "",
            parentTagId: asString(map["parentTagId"]),
            shortcut: asString(map["shortcut"]),
            sortOrder: asDouble(map["sortOrder"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: (map["isDeleted"] as? Bool) ?? false
        )
    }

    private static func parseArea(_ map: [String: Any]) -> Area? {
        guard let id = asString(map["id"]) else { return nil }
        return Area(
            id: id,
            title: (map["title"] as? String) ?? "",
            sortOrder: asDouble(map["sortOrder"]),
            tagIds: asStringList(map["tagIds"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: (map["isDeleted"] as? Bool) ?? false
        )
    }

    private static func parseProject(_ map: [String: Any]) -> Project? {
        guard let id = asString(map["id"]) else { return nil }
        return Project(
            id: id,
            areaId: asString(map["areaId"]),
            title: (map["title"] as? String) ?? "",
            notes: (map["notes"] as? String) ?? "",
            status: ProjectStatus.normalized(map["status"] as? String),
            scheduleState: ScheduleState.normalized(map["scheduleState"] as? String, fallback: .anytime),
            whenDate: asInt64(map["whenDate"]),
            deadline: asInt64(map["deadline"]),
            completedAt: asInt64(map["completedAt"]),
            sortOrder: asDouble(map["sortOrder"]),
            tagIds: asStringList(map["tagIds"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: (map["isDeleted"] as? Bool) ?? false
        )
    }

    private static func parseHeading(_ map: [String: Any]) -> Heading? {
        guard let id = asString(map["id"]), let projectId = asString(map["projectId"]) else { return nil }
        return Heading(
            id: id,
            projectId: projectId,
            title: (map["title"] as? String) ?? "",
            sortOrder: asDouble(map["sortOrder"]),
            isArchived: (map["isArchived"] as? Bool) ?? false,
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: (map["isDeleted"] as? Bool) ?? false
        )
    }

    private static func parseTask(_ map: [String: Any]) -> (TaskItem, [String])? {
        guard let id = asString(map["id"]) else { return nil }
        var rule: RecurrenceRule? = nil
        if let recStr = asString(map["recurrenceRule"]), let data = recStr.data(using: .utf8) {
            rule = try? JSONDecoder().decode(RecurrenceRule.self, from: data)
        }
        let tids = asStringList(map["tagIds"])
        let task = TaskItem(
            id: id,
            title: (map["title"] as? String) ?? "",
            notes: (map["notes"] as? String) ?? "",
            status: TaskStatus.normalized(map["status"] as? String),
            scheduleState: ScheduleState.normalized(map["scheduleState"] as? String, fallback: .inbox),
            whenDate: asInt64(map["whenDate"]),
            deadline: asInt64(map["deadline"]),
            reminderTime: asInt64(map["reminderTime"]),
            completedAt: asInt64(map["completedAt"]),
            projectId: asString(map["projectId"]),
            headingId: asString(map["headingId"]),
            areaId: asString(map["areaId"]),
            recurrenceRule: rule,
            sortOrder: asDouble(map["sortOrder"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: (map["isDeleted"] as? Bool) ?? false
        )
        return (task, tids)
    }

    private static func parseChecklist(_ map: [String: Any]) -> ChecklistItem? {
        guard let id = asString(map["id"]), let taskId = asString(map["taskId"]) else { return nil }
        return ChecklistItem(
            id: id,
            taskId: taskId,
            title: (map["title"] as? String) ?? "",
            isCompleted: (map["isCompleted"] as? Bool) ?? false,
            sortOrder: asDouble(map["sortOrder"]),
            createdAt: asInt64(map["createdAt"]) ?? 0,
            updatedAt: asInt64(map["updatedAt"]) ?? 0,
            isDeleted: (map["isDeleted"] as? Bool) ?? false
        )
    }
}
