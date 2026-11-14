import Foundation
import CryptoKit
import CommonCrypto
import zlib

public enum ThingsBackupError: LocalizedError, Equatable {
    case invalidFormat(String)
    case passwordRequired
    case invalidPassword

    public var errorDescription: String? {
        switch self {
        case .invalidFormat(let msg):
            return msg
        case .passwordRequired:
            return "This backup file is password-protected. Please enter the password."
        case .invalidPassword:
            return "Incorrect password or tampered backup file."
        }
    }
}

public struct ThingsbookBackupEnvelope: Codable, Equatable, Sendable {
    public var magic: String
    public var formatVersion: Int
    public var appVersionName: String
    public var appVersionCode: Int
    public var createdAt: Int64
    public var encrypted: Bool
    public var kdf: String?
    public var kdfIterations: Int?
    public var saltBase64: String?
    public var cipher: String?
    public var ivBase64: String?
    public var gcmTagBits: Int?
    public var payloadGzipBase64: String?
    public var ciphertextBase64: String?

    public init(
        magic: String = ThingsBackupCodec.backupMagic,
        formatVersion: Int = ThingsBackupCodec.formatVersion,
        appVersionName: String = "",
        appVersionCode: Int = 0,
        createdAt: Int64 = 0,
        encrypted: Bool = false,
        kdf: String? = nil,
        kdfIterations: Int? = nil,
        saltBase64: String? = nil,
        cipher: String? = nil,
        ivBase64: String? = nil,
        gcmTagBits: Int? = nil,
        payloadGzipBase64: String? = nil,
        ciphertextBase64: String? = nil
    ) {
        self.magic = magic
        self.formatVersion = formatVersion
        self.appVersionName = appVersionName
        self.appVersionCode = appVersionCode
        self.createdAt = createdAt
        self.encrypted = encrypted
        self.kdf = kdf
        self.kdfIterations = kdfIterations
        self.saltBase64 = saltBase64
        self.cipher = cipher
        self.ivBase64 = ivBase64
        self.gcmTagBits = gcmTagBits
        self.payloadGzipBase64 = payloadGzipBase64
        self.ciphertextBase64 = ciphertextBase64
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.magic = (try? c.decode(String.self, forKey: .magic)) ?? ""
        self.formatVersion = (try? c.decode(Int.self, forKey: .formatVersion)) ?? 1
        self.appVersionName = (try? c.decode(String.self, forKey: .appVersionName)) ?? ""
        self.appVersionCode = (try? c.decode(Int.self, forKey: .appVersionCode)) ?? 0
        self.createdAt = (try? c.decode(Int64.self, forKey: .createdAt)) ?? 0
        self.encrypted = (try? c.decode(Bool.self, forKey: .encrypted)) ?? false
        self.kdf = try? c.decodeIfPresent(String.self, forKey: .kdf)
        self.kdfIterations = try? c.decodeIfPresent(Int.self, forKey: .kdfIterations)
        self.saltBase64 = try? c.decodeIfPresent(String.self, forKey: .saltBase64)
        self.cipher = try? c.decodeIfPresent(String.self, forKey: .cipher)
        self.ivBase64 = try? c.decodeIfPresent(String.self, forKey: .ivBase64)
        self.gcmTagBits = try? c.decodeIfPresent(Int.self, forKey: .gcmTagBits)
        self.payloadGzipBase64 = try? c.decodeIfPresent(String.self, forKey: .payloadGzipBase64)
        self.ciphertextBase64 = try? c.decodeIfPresent(String.self, forKey: .ciphertextBase64)
    }
}

public struct BackupAreaDto: Codable, Equatable, Sendable {
    public var id: String
    public var title: String
    public var sortOrder: Double
    public var tagIdsJson: String
    public var createdAt: Int64
    public var updatedAt: Int64

    public init(id: String, title: String, sortOrder: Double = 0, tagIdsJson: String = "[]", createdAt: Int64 = 0, updatedAt: Int64 = 0) {
        self.id = id
        self.title = title
        self.sortOrder = sortOrder
        self.tagIdsJson = tagIdsJson
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.title = (try? c.decode(String.self, forKey: .title)) ?? ""
        self.sortOrder = (try? c.decode(Double.self, forKey: .sortOrder)) ?? 0
        self.tagIdsJson = (try? c.decode(String.self, forKey: .tagIdsJson)) ?? "[]"
        self.createdAt = (try? c.decode(Int64.self, forKey: .createdAt)) ?? 0
        self.updatedAt = (try? c.decode(Int64.self, forKey: .updatedAt)) ?? 0
    }
}

public struct BackupProjectDto: Codable, Equatable, Sendable {
    public var id: String
    public var areaId: String?
    public var title: String
    public var notes: String
    public var status: String
    public var scheduleState: String
    public var whenDate: Int64?
    public var deadline: Int64?
    public var completedAt: Int64?
    public var sortOrder: Double
    public var tagIdsJson: String
    public var createdAt: Int64
    public var updatedAt: Int64

    public init(
        id: String,
        areaId: String? = nil,
        title: String,
        notes: String = "",
        status: String = ProjectStatus.active.rawValue,
        scheduleState: String = ScheduleState.anytime.rawValue,
        whenDate: Int64? = nil,
        deadline: Int64? = nil,
        completedAt: Int64? = nil,
        sortOrder: Double = 0,
        tagIdsJson: String = "[]",
        createdAt: Int64 = 0,
        updatedAt: Int64 = 0
    ) {
        self.id = id
        self.areaId = areaId
        self.title = title
        self.notes = notes
        self.status = status
        self.scheduleState = scheduleState
        self.whenDate = whenDate
        self.deadline = deadline
        self.completedAt = completedAt
        self.sortOrder = sortOrder
        self.tagIdsJson = tagIdsJson
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.areaId = try? c.decodeIfPresent(String.self, forKey: .areaId)
        self.title = (try? c.decode(String.self, forKey: .title)) ?? ""
        self.notes = (try? c.decode(String.self, forKey: .notes)) ?? ""
        self.status = (try? c.decode(String.self, forKey: .status)) ?? ProjectStatus.active.rawValue
        self.scheduleState = (try? c.decode(String.self, forKey: .scheduleState)) ?? ScheduleState.anytime.rawValue
        self.whenDate = try? c.decodeIfPresent(Int64.self, forKey: .whenDate)
        self.deadline = try? c.decodeIfPresent(Int64.self, forKey: .deadline)
        self.completedAt = try? c.decodeIfPresent(Int64.self, forKey: .completedAt)
        self.sortOrder = (try? c.decode(Double.self, forKey: .sortOrder)) ?? 0
        self.tagIdsJson = (try? c.decode(String.self, forKey: .tagIdsJson)) ?? "[]"
        self.createdAt = (try? c.decode(Int64.self, forKey: .createdAt)) ?? 0
        self.updatedAt = (try? c.decode(Int64.self, forKey: .updatedAt)) ?? 0
    }
}

public struct BackupHeadingDto: Codable, Equatable, Sendable {
    public var id: String
    public var projectId: String
    public var title: String
    public var sortOrder: Double
    public var isArchived: Bool
    public var createdAt: Int64
    public var updatedAt: Int64

    public init(
        id: String,
        projectId: String,
        title: String,
        sortOrder: Double = 0,
        isArchived: Bool = false,
        createdAt: Int64 = 0,
        updatedAt: Int64 = 0
    ) {
        self.id = id
        self.projectId = projectId
        self.title = title
        self.sortOrder = sortOrder
        self.isArchived = isArchived
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.projectId = try c.decode(String.self, forKey: .projectId)
        self.title = (try? c.decode(String.self, forKey: .title)) ?? ""
        self.sortOrder = (try? c.decode(Double.self, forKey: .sortOrder)) ?? 0
        self.isArchived = (try? c.decode(Bool.self, forKey: .isArchived)) ?? false
        self.createdAt = (try? c.decode(Int64.self, forKey: .createdAt)) ?? 0
        self.updatedAt = (try? c.decode(Int64.self, forKey: .updatedAt)) ?? 0
    }
}

public struct BackupTaskDto: Codable, Equatable, Sendable {
    public var id: String
    public var title: String
    public var notes: String
    public var status: String
    public var scheduleState: String
    public var whenDate: Int64?
    public var deadline: Int64?
    public var reminderTime: Int64?
    public var completedAt: Int64?
    public var projectId: String?
    public var headingId: String?
    public var areaId: String?
    public var recurrenceRuleJson: String?
    public var sortOrder: Double
    public var tagIdsJson: String
    public var createdAt: Int64
    public var updatedAt: Int64

    public init(
        id: String,
        title: String,
        notes: String = "",
        status: String = TaskStatus.open.rawValue,
        scheduleState: String = ScheduleState.inbox.rawValue,
        whenDate: Int64? = nil,
        deadline: Int64? = nil,
        reminderTime: Int64? = nil,
        completedAt: Int64? = nil,
        projectId: String? = nil,
        headingId: String? = nil,
        areaId: String? = nil,
        recurrenceRuleJson: String? = nil,
        sortOrder: Double = 0,
        tagIdsJson: String = "[]",
        createdAt: Int64 = 0,
        updatedAt: Int64 = 0
    ) {
        self.id = id
        self.title = title
        self.notes = notes
        self.status = status
        self.scheduleState = scheduleState
        self.whenDate = whenDate
        self.deadline = deadline
        self.reminderTime = reminderTime
        self.completedAt = completedAt
        self.projectId = projectId
        self.headingId = headingId
        self.areaId = areaId
        self.recurrenceRuleJson = recurrenceRuleJson
        self.sortOrder = sortOrder
        self.tagIdsJson = tagIdsJson
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.title = (try? c.decode(String.self, forKey: .title)) ?? ""
        self.notes = (try? c.decode(String.self, forKey: .notes)) ?? ""
        self.status = (try? c.decode(String.self, forKey: .status)) ?? TaskStatus.open.rawValue
        self.scheduleState = (try? c.decode(String.self, forKey: .scheduleState)) ?? ScheduleState.inbox.rawValue
        self.whenDate = try? c.decodeIfPresent(Int64.self, forKey: .whenDate)
        self.deadline = try? c.decodeIfPresent(Int64.self, forKey: .deadline)
        self.reminderTime = try? c.decodeIfPresent(Int64.self, forKey: .reminderTime)
        self.completedAt = try? c.decodeIfPresent(Int64.self, forKey: .completedAt)
        self.projectId = try? c.decodeIfPresent(String.self, forKey: .projectId)
        self.headingId = try? c.decodeIfPresent(String.self, forKey: .headingId)
        self.areaId = try? c.decodeIfPresent(String.self, forKey: .areaId)
        self.recurrenceRuleJson = try? c.decodeIfPresent(String.self, forKey: .recurrenceRuleJson)
        self.sortOrder = (try? c.decode(Double.self, forKey: .sortOrder)) ?? 0
        self.tagIdsJson = (try? c.decode(String.self, forKey: .tagIdsJson)) ?? "[]"
        self.createdAt = (try? c.decode(Int64.self, forKey: .createdAt)) ?? 0
        self.updatedAt = (try? c.decode(Int64.self, forKey: .updatedAt)) ?? 0
    }
}

public struct BackupChecklistItemDto: Codable, Equatable, Sendable {
    public var id: String
    public var taskId: String
    public var title: String
    public var isCompleted: Bool
    public var sortOrder: Double
    public var createdAt: Int64
    public var updatedAt: Int64

    public init(
        id: String,
        taskId: String,
        title: String,
        isCompleted: Bool = false,
        sortOrder: Double = 0,
        createdAt: Int64 = 0,
        updatedAt: Int64 = 0
    ) {
        self.id = id
        self.taskId = taskId
        self.title = title
        self.isCompleted = isCompleted
        self.sortOrder = sortOrder
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.taskId = try c.decode(String.self, forKey: .taskId)
        self.title = (try? c.decode(String.self, forKey: .title)) ?? ""
        self.isCompleted = (try? c.decode(Bool.self, forKey: .isCompleted)) ?? false
        self.sortOrder = (try? c.decode(Double.self, forKey: .sortOrder)) ?? 0
        self.createdAt = (try? c.decode(Int64.self, forKey: .createdAt)) ?? 0
        self.updatedAt = (try? c.decode(Int64.self, forKey: .updatedAt)) ?? 0
    }
}

public struct BackupTagDto: Codable, Equatable, Sendable {
    public var id: String
    public var title: String
    public var parentTagId: String?
    public var shortcut: String?
    public var sortOrder: Double
    public var createdAt: Int64
    public var updatedAt: Int64

    public init(
        id: String,
        title: String,
        parentTagId: String? = nil,
        shortcut: String? = nil,
        sortOrder: Double = 0,
        createdAt: Int64 = 0,
        updatedAt: Int64 = 0
    ) {
        self.id = id
        self.title = title
        self.parentTagId = parentTagId
        self.shortcut = shortcut
        self.sortOrder = sortOrder
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.title = (try? c.decode(String.self, forKey: .title)) ?? ""
        self.parentTagId = try? c.decodeIfPresent(String.self, forKey: .parentTagId)
        self.shortcut = try? c.decodeIfPresent(String.self, forKey: .shortcut)
        self.sortOrder = (try? c.decode(Double.self, forKey: .sortOrder)) ?? 0
        self.createdAt = (try? c.decode(Int64.self, forKey: .createdAt)) ?? 0
        self.updatedAt = (try? c.decode(Int64.self, forKey: .updatedAt)) ?? 0
    }
}

public struct BackupTaskTagRefDto: Codable, Equatable, Sendable {
    public var taskId: String
    public var tagId: String

    public init(taskId: String, tagId: String) {
        self.taskId = taskId
        self.tagId = tagId
    }
}

public struct BackupProjectTagRefDto: Codable, Equatable, Sendable {
    public var projectId: String
    public var tagId: String

    public init(projectId: String, tagId: String) {
        self.projectId = projectId
        self.tagId = tagId
    }
}

public struct BackupPreferencesDto: Codable, Equatable, Sendable {
    public var sidebarVisible: Bool?
    public var dailyDigestEnabled: Bool?
    public var dailyDigestHour: Int?
    public var dailyDigestMinute: Int?
    public var languageTag: String?
    public var calendarEventsEnabled: Bool?
    public var selectedCalendarIds: [String]?
    public var groupTodayByProjectOrArea: Bool?

    public init(
        sidebarVisible: Bool? = nil,
        dailyDigestEnabled: Bool? = nil,
        dailyDigestHour: Int? = nil,
        dailyDigestMinute: Int? = nil,
        languageTag: String? = nil,
        calendarEventsEnabled: Bool? = nil,
        selectedCalendarIds: [String]? = nil,
        groupTodayByProjectOrArea: Bool? = nil
    ) {
        self.sidebarVisible = sidebarVisible
        self.dailyDigestEnabled = dailyDigestEnabled
        self.dailyDigestHour = dailyDigestHour
        self.dailyDigestMinute = dailyDigestMinute
        self.languageTag = languageTag
        self.calendarEventsEnabled = calendarEventsEnabled
        self.selectedCalendarIds = selectedCalendarIds
        self.groupTodayByProjectOrArea = groupTodayByProjectOrArea
    }
}

public struct ThingsbookBackupPayload: Codable, Equatable, Sendable {
    public var schemaVersion: Int
    public var createdAt: Int64
    public var appVersionName: String
    public var areas: [BackupAreaDto]
    public var projects: [BackupProjectDto]
    public var headings: [BackupHeadingDto]
    public var tasks: [BackupTaskDto]
    public var checklistItems: [BackupChecklistItemDto]
    public var tags: [BackupTagDto]
    public var taskTagRefs: [BackupTaskTagRefDto]
    public var projectTagRefs: [BackupProjectTagRefDto]
    public var preferences: BackupPreferencesDto

    public init(
        schemaVersion: Int = ThingsBackupCodec.formatVersion,
        createdAt: Int64 = 0,
        appVersionName: String = "",
        areas: [BackupAreaDto] = [],
        projects: [BackupProjectDto] = [],
        headings: [BackupHeadingDto] = [],
        tasks: [BackupTaskDto] = [],
        checklistItems: [BackupChecklistItemDto] = [],
        tags: [BackupTagDto] = [],
        taskTagRefs: [BackupTaskTagRefDto] = [],
        projectTagRefs: [BackupProjectTagRefDto] = [],
        preferences: BackupPreferencesDto = BackupPreferencesDto()
    ) {
        self.schemaVersion = schemaVersion
        self.createdAt = createdAt
        self.appVersionName = appVersionName
        self.areas = areas
        self.projects = projects
        self.headings = headings
        self.tasks = tasks
        self.checklistItems = checklistItems
        self.tags = tags
        self.taskTagRefs = taskTagRefs
        self.projectTagRefs = projectTagRefs
        self.preferences = preferences
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.schemaVersion = (try? c.decode(Int.self, forKey: .schemaVersion)) ?? 1
        self.createdAt = (try? c.decode(Int64.self, forKey: .createdAt)) ?? 0
        self.appVersionName = (try? c.decode(String.self, forKey: .appVersionName)) ?? ""
        self.areas = (try? c.decode([BackupAreaDto].self, forKey: .areas)) ?? []
        self.projects = (try? c.decode([BackupProjectDto].self, forKey: .projects)) ?? []
        self.headings = (try? c.decode([BackupHeadingDto].self, forKey: .headings)) ?? []
        self.tasks = (try? c.decode([BackupTaskDto].self, forKey: .tasks)) ?? []
        self.checklistItems = (try? c.decode([BackupChecklistItemDto].self, forKey: .checklistItems)) ?? []
        self.tags = (try? c.decode([BackupTagDto].self, forKey: .tags)) ?? []
        self.taskTagRefs = (try? c.decode([BackupTaskTagRefDto].self, forKey: .taskTagRefs)) ?? []
        self.projectTagRefs = (try? c.decode([BackupProjectTagRefDto].self, forKey: .projectTagRefs)) ?? []
        self.preferences = (try? c.decode(BackupPreferencesDto.self, forKey: .preferences)) ?? BackupPreferencesDto()
    }
}

public struct BackupSummary: Equatable, Sendable {
    public let createdAt: Int64
    public let appVersionName: String
    public let encrypted: Bool
    public let areasCount: Int
    public let projectsCount: Int
    public let headingsCount: Int
    public let tasksCount: Int
    public let checklistItemsCount: Int
    public let tagsCount: Int

    public init(
        createdAt: Int64,
        appVersionName: String,
        encrypted: Bool,
        areasCount: Int = 0,
        projectsCount: Int = 0,
        headingsCount: Int = 0,
        tasksCount: Int = 0,
        checklistItemsCount: Int = 0,
        tagsCount: Int = 0
    ) {
        self.createdAt = createdAt
        self.appVersionName = appVersionName
        self.encrypted = encrypted
        self.areasCount = areasCount
        self.projectsCount = projectsCount
        self.headingsCount = headingsCount
        self.tasksCount = tasksCount
        self.checklistItemsCount = checklistItemsCount
        self.tagsCount = tagsCount
    }
}

public enum BackupInspectionResult: Equatable, Sendable {
    case passwordProtected(createdAt: Int64, appVersionName: String)
    case unencryptedPreview(createdAt: Int64, appVersionName: String, summary: BackupSummary)
}

/// 100% binary-compatible encoder/decoder for Android & ChromeOS `.tbbackup` v1 files.
public enum ThingsBackupCodec {
    public static let backupMagic = "THINGSBOOK_BACKUP_V1"
    public static let formatVersion = 1

    private static let kdfAlgorithm = "PBKDF2WithHmacSHA256"
    private static let cipherTransformation = "AES/GCM/NoPadding"
    private static let kdfIterations = 120_000
    private static let keyLengthBytes = 32
    private static let saltBytes = 16
    private static let ivBytes = 12
    private static let gcmTagBits = 128
    private static let maxDecompressedBytes = 50 * 1024 * 1024 // 50 MB guard

    public static func summarizePayload(_ payload: ThingsbookBackupPayload, encrypted: Bool) -> BackupSummary {
        BackupSummary(
            createdAt: payload.createdAt,
            appVersionName: payload.appVersionName,
            encrypted: encrypted,
            areasCount: payload.areas.count,
            projectsCount: payload.projects.count,
            headingsCount: payload.headings.count,
            tasksCount: payload.tasks.count,
            checklistItemsCount: payload.checklistItems.count,
            tagsCount: payload.tags.count
        )
    }

    public static func encodeBackup(
        payload: ThingsbookBackupPayload,
        password: String?,
        appVersionName: String = "1.7.0",
        appVersionCode: Int = 41
    ) throws -> Data {
        let compactEncoder = JSONEncoder()
        let rawJsonData = try compactEncoder.encode(payload)
        let compressedData = try gzipCompress(rawJsonData)
        let createdAt = payload.createdAt

        let envelope: ThingsbookBackupEnvelope
        if let password, !password.isEmpty {
            let salt = randomBytes(count: saltBytes)
            let iv = randomBytes(count: ivBytes)
            let keyData = try derivePbkdf2Key(password: password, salt: salt, iterations: kdfIterations)
            let symmetricKey = SymmetricKey(data: keyData)
            let nonce = try AES.GCM.Nonce(data: iv)
            let aad = buildAad(magic: backupMagic, version: formatVersion)
            let sealed = try AES.GCM.seal(compressedData, using: symmetricKey, nonce: nonce, authenticating: aad)

            // Java Cipher & WebCrypto store `ciphertext || 16-byte tag` inside `ciphertextBase64`
            var ctAndTag = Data(sealed.ciphertext)
            ctAndTag.append(sealed.tag)

            envelope = ThingsbookBackupEnvelope(
                magic: backupMagic,
                formatVersion: formatVersion,
                appVersionName: appVersionName,
                appVersionCode: appVersionCode,
                createdAt: createdAt,
                encrypted: true,
                kdf: kdfAlgorithm,
                kdfIterations: kdfIterations,
                saltBase64: salt.base64EncodedString(),
                cipher: cipherTransformation,
                ivBase64: iv.base64EncodedString(),
                gcmTagBits: gcmTagBits,
                payloadGzipBase64: nil,
                ciphertextBase64: ctAndTag.base64EncodedString()
            )
        } else {
            envelope = ThingsbookBackupEnvelope(
                magic: backupMagic,
                formatVersion: formatVersion,
                appVersionName: appVersionName,
                appVersionCode: appVersionCode,
                createdAt: createdAt,
                encrypted: false,
                payloadGzipBase64: compressedData.base64EncodedString()
            )
        }

        let prettyEncoder = JSONEncoder()
        prettyEncoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try prettyEncoder.encode(envelope)
    }

    public static func inspectBackup(rawBytes: Data) throws -> BackupInspectionResult {
        let envelope = try parseAndValidateEnvelope(rawBytes: rawBytes)
        if envelope.encrypted {
            return .passwordProtected(createdAt: envelope.createdAt, appVersionName: envelope.appVersionName)
        } else {
            let payload = try decodePayloadFromEnvelope(envelope, password: nil)
            return .unencryptedPreview(
                createdAt: envelope.createdAt > 0 ? envelope.createdAt : payload.createdAt,
                appVersionName: envelope.appVersionName.isEmpty ? payload.appVersionName : envelope.appVersionName,
                summary: summarizePayload(payload, encrypted: false)
            )
        }
    }

    public static func decodeBackup(rawBytes: Data, password: String?) throws -> ThingsbookBackupPayload {
        let envelope = try parseAndValidateEnvelope(rawBytes: rawBytes)
        return try decodePayloadFromEnvelope(envelope, password: password)
    }

    private static func parseAndValidateEnvelope(rawBytes: Data) throws -> ThingsbookBackupEnvelope {
        guard !rawBytes.isEmpty else {
            throw ThingsBackupError.invalidFormat("Selected backup file is empty.")
        }
        guard var text = String(data: rawBytes, encoding: .utf8) else {
            throw ThingsBackupError.invalidFormat("Backup file is not valid UTF-8.")
        }
        if text.hasPrefix("\u{FEFF}") {
            text.removeFirst()
        }
        text = text.trimmingCharacters(in: .whitespacesAndNewlines)

        guard let cleanData = text.data(using: .utf8),
              let envelope = try? JSONDecoder().decode(ThingsbookBackupEnvelope.self, from: cleanData) else {
            throw ThingsBackupError.invalidFormat("Invalid file format: not a valid Thingsbook backup (.tbbackup).")
        }
        guard envelope.magic == backupMagic else {
            throw ThingsBackupError.invalidFormat("Unknown backup magic header (\(envelope.magic)).")
        }
        guard envelope.formatVersion >= 1 && envelope.formatVersion <= formatVersion else {
            throw ThingsBackupError.invalidFormat("Unsupported backup format version v\(envelope.formatVersion).")
        }
        return envelope
    }

    private static func decodePayloadFromEnvelope(_ envelope: ThingsbookBackupEnvelope, password: String?) throws -> ThingsbookBackupPayload {
        let compressedBytes: Data
        if !envelope.encrypted {
            guard let b64 = envelope.payloadGzipBase64?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !b64.isEmpty,
                  let decoded = Data(base64Encoded: b64, options: .ignoreUnknownCharacters) else {
                throw ThingsBackupError.invalidFormat("Unencrypted backup file has missing or corrupt payload.")
            }
            compressedBytes = decoded
        } else {
            guard let password, !password.isEmpty else {
                throw ThingsBackupError.passwordRequired
            }
            guard let saltB64 = envelope.saltBase64?.trimmingCharacters(in: .whitespacesAndNewlines),
                  let ivB64 = envelope.ivBase64?.trimmingCharacters(in: .whitespacesAndNewlines),
                  let ctB64 = envelope.ciphertextBase64?.trimmingCharacters(in: .whitespacesAndNewlines),
                  let salt = Data(base64Encoded: saltB64, options: .ignoreUnknownCharacters),
                  let iv = Data(base64Encoded: ivB64, options: .ignoreUnknownCharacters),
                  let ctAndTag = Data(base64Encoded: ctB64, options: .ignoreUnknownCharacters),
                  salt.count >= 8,
                  iv.count >= 8,
                  ctAndTag.count >= 16 else {
                throw ThingsBackupError.invalidFormat("Missing or invalid encryption parameters in backup file.")
            }

            let iterations = min(1_000_000, max(10_000, envelope.kdfIterations ?? kdfIterations))
            let tagBits = [96, 104, 112, 120, 128].contains(envelope.gcmTagBits ?? gcmTagBits) ? (envelope.gcmTagBits ?? gcmTagBits) : gcmTagBits
            let tagByteLen = tagBits / 8
            guard ctAndTag.count >= tagByteLen else {
                throw ThingsBackupError.invalidFormat("Encrypted payload is truncated.")
            }

            let keyData = try derivePbkdf2Key(password: password, salt: salt, iterations: iterations)
            let symmetricKey = SymmetricKey(data: keyData)
            let ctOnly = ctAndTag.prefix(ctAndTag.count - tagByteLen)
            let tagOnly = ctAndTag.suffix(tagByteLen)
            let aad = buildAad(magic: envelope.magic, version: envelope.formatVersion)

            do {
                let nonce = try AES.GCM.Nonce(data: iv)
                let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ctOnly, tag: tagOnly)
                compressedBytes = try AES.GCM.open(box, using: symmetricKey, authenticating: aad)
            } catch {
                throw ThingsBackupError.invalidPassword
            }
        }

        let decompressed = try gzipDecompressBounded(compressedBytes, maxBytes: maxDecompressedBytes)
        do {
            return try JSONDecoder().decode(ThingsbookBackupPayload.self, from: decompressed)
        } catch {
            throw ThingsBackupError.invalidFormat("Backup payload JSON structure is corrupted.")
        }
    }

    private static func buildAad(magic: String, version: Int) -> Data {
        Data("\(magic)|\(version)".utf8)
    }

    private static func randomBytes(count: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        _ = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        return Data(bytes)
    }

    private static func derivePbkdf2Key(password: String, salt: Data, iterations: Int) throws -> Data {
        let passwordData = Data(password.utf8)
        var derived = [UInt8](repeating: 0, count: keyLengthBytes)
        let status = derived.withUnsafeMutableBytes { derivedPtr in
            salt.withUnsafeBytes { saltPtr in
                passwordData.withUnsafeBytes { passPtr in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passPtr.bindMemory(to: Int8.self).baseAddress,
                        passwordData.count,
                        saltPtr.bindMemory(to: UInt8.self).baseAddress,
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        UInt32(iterations),
                        derivedPtr.bindMemory(to: UInt8.self).baseAddress,
                        keyLengthBytes
                    )
                }
            }
        }
        guard status == kCCSuccess else {
            throw ThingsBackupError.invalidFormat("PBKDF2 key derivation failed (\(status)).")
        }
        return Data(derived)
    }

    // MARK: - GZIP (RFC 1952 via zlib windowBits = 15 + 16)

    public static func gzipCompress(_ input: Data) throws -> Data {
        var stream = z_stream()
        let initStatus = deflateInit2_(
            &stream,
            Z_DEFAULT_COMPRESSION,
            Z_DEFLATED,
            MAX_WBITS + 16,
            8,
            Z_DEFAULT_STRATEGY,
            ZLIB_VERSION,
            Int32(MemoryLayout<z_stream>.size)
        )
        guard initStatus == Z_OK else {
            throw ThingsBackupError.invalidFormat("GZIP compression initialization failed.")
        }
        defer { deflateEnd(&stream) }

        var output = Data()
        let chunkSize = 16_384
        var buffer = [UInt8](repeating: 0, count: chunkSize)

        try input.withUnsafeBytes { rawInput in
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: rawInput.bindMemory(to: Bytef.self).baseAddress)
            stream.avail_in = uInt(input.count)

            repeat {
                let status: Int32 = buffer.withUnsafeMutableBytes { rawOut in
                    stream.next_out = rawOut.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(chunkSize)
                    return deflate(&stream, Z_FINISH)
                }
                if status == Z_STREAM_ERROR {
                    throw ThingsBackupError.invalidFormat("GZIP compression stream error.")
                }
                let produced = chunkSize - Int(stream.avail_out)
                if produced > 0 {
                    output.append(buffer, count: produced)
                }
            } while stream.avail_out == 0
        }

        return output
    }

    public static func gzipDecompressBounded(_ compressed: Data, maxBytes: Int) throws -> Data {
        guard !compressed.isEmpty else {
            throw ThingsBackupError.invalidFormat("Compressed GZIP stream is empty.")
        }
        var stream = z_stream()
        let initStatus = inflateInit2_(
            &stream,
            MAX_WBITS + 16,
            ZLIB_VERSION,
            Int32(MemoryLayout<z_stream>.size)
        )
        guard initStatus == Z_OK else {
            throw ThingsBackupError.invalidFormat("GZIP decompression initialization failed.")
        }
        defer { inflateEnd(&stream) }

        var output = Data()
        let chunkSize = 16_384
        var buffer = [UInt8](repeating: 0, count: chunkSize)

        try compressed.withUnsafeBytes { rawInput in
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: rawInput.bindMemory(to: Bytef.self).baseAddress)
            stream.avail_in = uInt(compressed.count)

            var finished = false
            while !finished {
                let status: Int32 = buffer.withUnsafeMutableBytes { rawOut in
                    stream.next_out = rawOut.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(chunkSize)
                    return inflate(&stream, Z_NO_FLUSH)
                }
                if status == Z_NEED_DICT || status == Z_DATA_ERROR || status == Z_MEM_ERROR || status == Z_STREAM_ERROR {
                    throw ThingsBackupError.invalidFormat("Backup GZIP payload is corrupted.")
                }
                let produced = chunkSize - Int(stream.avail_out)
                if produced > 0 {
                    if output.count + produced > maxBytes {
                        throw ThingsBackupError.invalidFormat("Backup exceeds 50 MB decompression safety limit.")
                    }
                    output.append(buffer, count: produced)
                }
                if status == Z_STREAM_END {
                    finished = true
                } else if produced == 0 && stream.avail_in == 0 {
                    throw ThingsBackupError.invalidFormat("Unexpected end of GZIP stream.")
                }
            }
        }

        return output
    }
}
