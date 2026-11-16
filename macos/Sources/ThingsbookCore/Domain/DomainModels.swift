import Foundation

public struct Area: Identifiable, Equatable, Hashable, Codable, Sendable {
    public var id: String
    public var title: String
    public var sortOrder: Double
    public var tagIds: [String]
    public var createdAt: Int64
    public var updatedAt: Int64
    public var isDeleted: Bool

    public init(
        id: String,
        title: String,
        sortOrder: Double,
        tagIds: [String] = [],
        createdAt: Int64,
        updatedAt: Int64,
        isDeleted: Bool = false
    ) {
        self.id = id
        self.title = title
        self.sortOrder = sortOrder
        self.tagIds = tagIds
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isDeleted = isDeleted
    }
}

public struct Project: Identifiable, Equatable, Hashable, Codable, Sendable {
    public var id: String
    public var areaId: String?
    public var title: String
    public var notes: String
    public var status: ProjectStatus
    public var scheduleState: ScheduleState
    public var whenDate: Int64?
    public var deadline: Int64?
    public var completedAt: Int64?
    public var lastReviewedAt: Int64?
    public var sortOrder: Double
    public var tagIds: [String]
    public var totalTaskCount: Int
    public var completedTaskCount: Int
    public var createdAt: Int64
    public var updatedAt: Int64
    public var isDeleted: Bool

    public init(
        id: String,
        areaId: String? = nil,
        title: String,
        notes: String = "",
        status: ProjectStatus = .active,
        scheduleState: ScheduleState = .anytime,
        whenDate: Int64? = nil,
        deadline: Int64? = nil,
        completedAt: Int64? = nil,
        lastReviewedAt: Int64? = nil,
        sortOrder: Double = 1000.0,
        tagIds: [String] = [],
        totalTaskCount: Int = 0,
        completedTaskCount: Int = 0,
        createdAt: Int64,
        updatedAt: Int64,
        isDeleted: Bool = false
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
        self.lastReviewedAt = lastReviewedAt
        self.sortOrder = sortOrder
        self.tagIds = tagIds
        self.totalTaskCount = totalTaskCount
        self.completedTaskCount = completedTaskCount
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isDeleted = isDeleted
    }

    private enum CodingKeys: String, CodingKey {
        case id, areaId, title, notes, status, scheduleState, whenDate, deadline, completedAt
        case lastReviewedAt, sortOrder, tagIds, totalTaskCount, completedTaskCount, createdAt, updatedAt, isDeleted
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.areaId = try c.decodeIfPresent(String.self, forKey: .areaId)
        self.title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        self.notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        self.status = try c.decodeIfPresent(ProjectStatus.self, forKey: .status) ?? .active
        self.scheduleState = try c.decodeIfPresent(ScheduleState.self, forKey: .scheduleState) ?? .anytime
        self.whenDate = try c.decodeIfPresent(Int64.self, forKey: .whenDate)
        self.deadline = try c.decodeIfPresent(Int64.self, forKey: .deadline)
        self.completedAt = try c.decodeIfPresent(Int64.self, forKey: .completedAt)
        self.lastReviewedAt = try c.decodeIfPresent(Int64.self, forKey: .lastReviewedAt)
        self.sortOrder = try c.decodeIfPresent(Double.self, forKey: .sortOrder) ?? 1000.0
        self.tagIds = try c.decodeIfPresent([String].self, forKey: .tagIds) ?? []
        self.totalTaskCount = try c.decodeIfPresent(Int.self, forKey: .totalTaskCount) ?? 0
        self.completedTaskCount = try c.decodeIfPresent(Int.self, forKey: .completedTaskCount) ?? 0
        self.createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.isDeleted = try c.decodeIfPresent(Bool.self, forKey: .isDeleted) ?? false
    }

    public var progress: Double {
        guard totalTaskCount > 0 else { return 0.0 }
        return min(1.0, max(0.0, Double(completedTaskCount) / Double(totalTaskCount)))
    }
}

public struct Heading: Identifiable, Equatable, Hashable, Codable, Sendable {
    public var id: String
    public var projectId: String
    public var title: String
    public var sortOrder: Double
    public var isArchived: Bool
    public var createdAt: Int64
    public var updatedAt: Int64
    public var isDeleted: Bool

    public init(
        id: String,
        projectId: String,
        title: String,
        sortOrder: Double,
        isArchived: Bool = false,
        createdAt: Int64,
        updatedAt: Int64,
        isDeleted: Bool = false
    ) {
        self.id = id
        self.projectId = projectId
        self.title = title
        self.sortOrder = sortOrder
        self.isArchived = isArchived
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isDeleted = isDeleted
    }
}

public struct ChecklistItem: Identifiable, Equatable, Hashable, Codable, Sendable {
    public var id: String
    public var taskId: String
    public var title: String
    public var isCompleted: Bool
    public var sortOrder: Double
    public var createdAt: Int64
    public var updatedAt: Int64
    public var isDeleted: Bool

    public init(
        id: String,
        taskId: String,
        title: String,
        isCompleted: Bool = false,
        sortOrder: Double = 100.0,
        createdAt: Int64,
        updatedAt: Int64,
        isDeleted: Bool = false
    ) {
        self.id = id
        self.taskId = taskId
        self.title = title
        self.isCompleted = isCompleted
        self.sortOrder = sortOrder
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isDeleted = isDeleted
    }
}

public struct Tag: Identifiable, Equatable, Hashable, Codable, Sendable {
    public var id: String
    public var title: String
    public var parentTagId: String?
    public var shortcut: String?
    public var sortOrder: Double
    public var createdAt: Int64
    public var updatedAt: Int64
    public var isDeleted: Bool

    public init(
        id: String,
        title: String,
        parentTagId: String? = nil,
        shortcut: String? = nil,
        sortOrder: Double = 100.0,
        createdAt: Int64,
        updatedAt: Int64,
        isDeleted: Bool = false
    ) {
        self.id = id
        self.title = title
        self.parentTagId = parentTagId
        self.shortcut = shortcut
        self.sortOrder = sortOrder
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isDeleted = isDeleted
    }
}

public struct TaskAttachment: Identifiable, Equatable, Hashable, Codable, Sendable {
    public var id: String
    public var taskId: String?
    public var projectId: String?
    public var fileName: String
    public var mimeType: String
    public var sizeBytes: Int64
    public var localRelativePath: String
    public var createdAt: Int64
    public var updatedAt: Int64
    public var isDeleted: Bool

    public init(
        id: String,
        taskId: String? = nil,
        projectId: String? = nil,
        fileName: String,
        mimeType: String,
        sizeBytes: Int64 = 0,
        localRelativePath: String = "",
        createdAt: Int64,
        updatedAt: Int64,
        isDeleted: Bool = false
    ) {
        self.id = id
        self.taskId = taskId
        self.projectId = projectId
        self.fileName = fileName
        self.mimeType = mimeType
        self.sizeBytes = sizeBytes
        self.localRelativePath = localRelativePath
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isDeleted = isDeleted
    }

    public var isImage: Bool {
        let lower = mimeType.lowercased()
        if lower.hasPrefix("image/") { return true }
        let ext = (fileName as NSString).pathExtension.lowercased()
        return ["jpg", "jpeg", "png", "webp", "heic", "gif", "tiff"].contains(ext)
    }

    public var isPdf: Bool {
        mimeType.lowercased() == "application/pdf" || fileName.lowercased().hasSuffix(".pdf")
    }

    public var formattedSize: String {
        if sizeBytes < 1024 {
            return "\(max(0, sizeBytes)) B"
        } else if sizeBytes < 1024 * 1024 {
            return String(format: "%.0f KB", Double(sizeBytes) / 1024.0)
        } else {
            return String(format: "%.1f MB", Double(sizeBytes) / (1024.0 * 1024.0))
        }
    }
}

public struct TaskItem: Identifiable, Equatable, Hashable, Codable, Sendable {
    public var id: String
    public var title: String
    public var notes: String
    public var status: TaskStatus
    public var scheduleState: ScheduleState
    public var whenDate: Int64?
    public var deadline: Int64?
    public var reminderTime: Int64?
    public var scheduledStartMinutes: Int?
    public var durationMinutes: Int?
    public var completedAt: Int64?
    public var projectId: String?
    public var headingId: String?
    public var areaId: String?
    public var recurrenceRule: RecurrenceRule?
    public var sortOrder: Double
    public var checklistItems: [ChecklistItem]
    public var tags: [Tag]
    public var attachments: [TaskAttachment]
    public var createdAt: Int64
    public var updatedAt: Int64
    public var isDeleted: Bool

    public init(
        id: String,
        title: String,
        notes: String = "",
        status: TaskStatus = .open,
        scheduleState: ScheduleState = .inbox,
        whenDate: Int64? = nil,
        deadline: Int64? = nil,
        reminderTime: Int64? = nil,
        scheduledStartMinutes: Int? = nil,
        durationMinutes: Int? = nil,
        completedAt: Int64? = nil,
        projectId: String? = nil,
        headingId: String? = nil,
        areaId: String? = nil,
        recurrenceRule: RecurrenceRule? = nil,
        sortOrder: Double = 1000.0,
        checklistItems: [ChecklistItem] = [],
        tags: [Tag] = [],
        attachments: [TaskAttachment] = [],
        createdAt: Int64,
        updatedAt: Int64,
        isDeleted: Bool = false
    ) {
        self.id = id
        self.title = title
        self.notes = notes
        self.status = status
        self.scheduleState = scheduleState
        self.whenDate = whenDate
        self.deadline = deadline
        self.reminderTime = reminderTime
        self.scheduledStartMinutes = scheduledStartMinutes
        self.durationMinutes = durationMinutes
        self.completedAt = completedAt
        self.projectId = projectId
        self.headingId = headingId
        self.areaId = areaId
        self.recurrenceRule = recurrenceRule
        self.sortOrder = sortOrder
        self.checklistItems = checklistItems
        self.tags = tags
        self.attachments = attachments
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isDeleted = isDeleted
    }

    private enum CodingKeys: String, CodingKey {
        case id, title, notes, status, scheduleState, whenDate, deadline, reminderTime
        case scheduledStartMinutes, durationMinutes, completedAt, projectId, headingId, areaId
        case recurrenceRule, sortOrder, checklistItems, tags, attachments, createdAt, updatedAt, isDeleted
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        self.notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        self.status = try c.decodeIfPresent(TaskStatus.self, forKey: .status) ?? .open
        self.scheduleState = try c.decodeIfPresent(ScheduleState.self, forKey: .scheduleState) ?? .inbox
        self.whenDate = try c.decodeIfPresent(Int64.self, forKey: .whenDate)
        self.deadline = try c.decodeIfPresent(Int64.self, forKey: .deadline)
        self.reminderTime = try c.decodeIfPresent(Int64.self, forKey: .reminderTime)
        self.scheduledStartMinutes = try c.decodeIfPresent(Int.self, forKey: .scheduledStartMinutes)
        self.durationMinutes = try c.decodeIfPresent(Int.self, forKey: .durationMinutes)
        self.completedAt = try c.decodeIfPresent(Int64.self, forKey: .completedAt)
        self.projectId = try c.decodeIfPresent(String.self, forKey: .projectId)
        self.headingId = try c.decodeIfPresent(String.self, forKey: .headingId)
        self.areaId = try c.decodeIfPresent(String.self, forKey: .areaId)
        self.recurrenceRule = try c.decodeIfPresent(RecurrenceRule.self, forKey: .recurrenceRule)
        self.sortOrder = try c.decodeIfPresent(Double.self, forKey: .sortOrder) ?? 1000.0
        self.checklistItems = try c.decodeIfPresent([ChecklistItem].self, forKey: .checklistItems) ?? []
        self.tags = try c.decodeIfPresent([Tag].self, forKey: .tags) ?? []
        self.attachments = try c.decodeIfPresent([TaskAttachment].self, forKey: .attachments) ?? []
        self.createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.isDeleted = try c.decodeIfPresent(Bool.self, forKey: .isDeleted) ?? false
    }

    public var tagIds: [String] {
        tags.map(\.id)
    }

    public var effectiveDurationMinutes: Int {
        if let d = durationMinutes, d > 0 { return d }
        return 30
    }

    public var formattedTimeblockPill: String? {
        if let start = scheduledStartMinutes {
            let clampedStart = ((start % 1440) + 1440) % 1440
            let dur = effectiveDurationMinutes
            let end = (clampedStart + dur) % 1440
            return "\(Self.formatMinutesOfDay(clampedStart))–\(Self.formatMinutesOfDay(end)) · \(dur)m"
        }
        if let dur = durationMinutes, dur > 0 {
            return "\(dur)m"
        }
        return nil
    }

    public static func formatMinutesOfDay(_ minutes: Int) -> String {
        let normalized = ((minutes % 1440) + 1440) % 1440
        let h = normalized / 60
        let m = normalized % 60
        return String(format: "%02d:%02d", h, m)
    }
}

// MARK: - v2.0 Project Templates & Blueprints

public struct TemplateTaskSpec: Codable, Equatable, Hashable, Sendable {
    public var title: String
    public var notes: String
    public var checklistItems: [String]
    public var scheduledStartMinutes: Int?
    public var durationMinutes: Int?

    public init(
        title: String,
        notes: String = "",
        checklistItems: [String] = [],
        scheduledStartMinutes: Int? = nil,
        durationMinutes: Int? = nil
    ) {
        self.title = title
        self.notes = notes
        self.checklistItems = checklistItems
        self.scheduledStartMinutes = scheduledStartMinutes
        self.durationMinutes = durationMinutes
    }

    private enum CodingKeys: String, CodingKey {
        case title, notes, checklistItems, checklist, subtasks, scheduledStartMinutes, durationMinutes
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        self.notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        if let items = try c.decodeIfPresent([String].self, forKey: .checklistItems) {
            self.checklistItems = items
        } else if let items = try c.decodeIfPresent([String].self, forKey: .checklist) {
            self.checklistItems = items
        } else if let items = try c.decodeIfPresent([String].self, forKey: .subtasks) {
            self.checklistItems = items
        } else {
            self.checklistItems = []
        }
        self.scheduledStartMinutes = try c.decodeIfPresent(Int.self, forKey: .scheduledStartMinutes)
        self.durationMinutes = try c.decodeIfPresent(Int.self, forKey: .durationMinutes)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(title, forKey: .title)
        try c.encode(notes, forKey: .notes)
        try c.encode(checklistItems, forKey: .checklistItems)
        try c.encodeIfPresent(scheduledStartMinutes, forKey: .scheduledStartMinutes)
        try c.encodeIfPresent(durationMinutes, forKey: .durationMinutes)
    }
}

public struct TemplateHeadingSpec: Codable, Equatable, Hashable, Sendable {
    public var title: String
    public var tasks: [TemplateTaskSpec]

    public init(title: String, tasks: [TemplateTaskSpec] = []) {
        self.title = title
        self.tasks = tasks
    }

    private enum CodingKeys: String, CodingKey {
        case title, tasks
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        self.tasks = try c.decodeIfPresent([TemplateTaskSpec].self, forKey: .tasks) ?? []
    }
}

public struct ProjectTemplateBlueprint: Codable, Equatable, Hashable, Sendable {
    public var unheadedTasks: [TemplateTaskSpec]
    public var headings: [TemplateHeadingSpec]

    public init(
        unheadedTasks: [TemplateTaskSpec] = [],
        headings: [TemplateHeadingSpec] = []
    ) {
        self.unheadedTasks = unheadedTasks
        self.headings = headings
    }

    private enum CodingKeys: String, CodingKey {
        case unheadedTasks, tasks, rootTasks, headings
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let root = try c.decodeIfPresent([TemplateTaskSpec].self, forKey: .unheadedTasks) {
            self.unheadedTasks = root
        } else if let root = try c.decodeIfPresent([TemplateTaskSpec].self, forKey: .tasks) {
            self.unheadedTasks = root
        } else if let root = try c.decodeIfPresent([TemplateTaskSpec].self, forKey: .rootTasks) {
            self.unheadedTasks = root
        } else {
            self.unheadedTasks = []
        }
        self.headings = try c.decodeIfPresent([TemplateHeadingSpec].self, forKey: .headings) ?? []
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(unheadedTasks, forKey: .unheadedTasks)
        try c.encode(headings, forKey: .headings)
    }

    public var totalTaskCount: Int {
        unheadedTasks.count + headings.reduce(0) { $0 + $1.tasks.count }
    }

    public func toJSONString() -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        guard let data = try? encoder.encode(self),
              let s = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return s
    }

    public static func decode(from json: String) -> ProjectTemplateBlueprint {
        guard let data = json.data(using: .utf8),
              let decoded = try? JSONDecoder().decode(ProjectTemplateBlueprint.self, from: data) else {
            return ProjectTemplateBlueprint()
        }
        return decoded
    }
}

public struct ProjectTemplate: Identifiable, Codable, Equatable, Hashable, Sendable {
    public var id: String
    public var title: String
    public var notes: String
    public var defaultAreaId: String?
    public var tagIds: [String]
    public var blueprintJson: String
    public var sortOrder: Double
    public var createdAt: Int64
    public var updatedAt: Int64
    public var isDeleted: Bool
    public var isDirty: Bool

    public init(
        id: String,
        title: String,
        notes: String = "",
        defaultAreaId: String? = nil,
        tagIds: [String] = [],
        blueprintJson: String = "{}",
        sortOrder: Double = 0,
        createdAt: Int64 = 0,
        updatedAt: Int64 = 0,
        isDeleted: Bool = false,
        isDirty: Bool = false
    ) {
        self.id = id
        self.title = title
        self.notes = notes
        self.defaultAreaId = defaultAreaId
        self.tagIds = tagIds
        self.blueprintJson = blueprintJson
        self.sortOrder = sortOrder
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isDeleted = isDeleted
        self.isDirty = isDirty
    }

    private enum CodingKeys: String, CodingKey {
        case id, title, notes, defaultAreaId, tagIds, blueprintJson, sortOrder, createdAt, updatedAt, isDeleted, isDirty
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(String.self, forKey: .id)
        self.title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        self.notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        self.defaultAreaId = try c.decodeIfPresent(String.self, forKey: .defaultAreaId)
        self.tagIds = try c.decodeIfPresent([String].self, forKey: .tagIds) ?? []
        self.blueprintJson = try c.decodeIfPresent(String.self, forKey: .blueprintJson) ?? "{}"
        self.sortOrder = try c.decodeIfPresent(Double.self, forKey: .sortOrder) ?? 0
        self.createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
        self.updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
        self.isDeleted = try c.decodeIfPresent(Bool.self, forKey: .isDeleted) ?? false
        self.isDirty = try c.decodeIfPresent(Bool.self, forKey: .isDirty) ?? false
    }

    public var blueprint: ProjectTemplateBlueprint {
        get { ProjectTemplateBlueprint.decode(from: blueprintJson) }
        set { blueprintJson = newValue.toJSONString() }
    }

    public static var builtInStarterTemplates: [ProjectTemplate] {
        builtInStarterTemplates(for: "en")
    }

    public static func builtInStarterTemplates(for languageCode: String) -> [ProjectTemplate] {
        let normalized = String(languageCode.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().prefix(2))
        switch normalized {
        case "de":
            return [
                ProjectTemplate(
                    id: "starter-trip-packing",
                    title: "Reise & Packliste",
                    notes: "Entspannte Checkliste vor der Abreise, Buchungen und Reiseutensilien.",
                    blueprintJson: ProjectTemplateBlueprint(
                        unheadedTasks: [
                            TemplateTaskSpec(
                                title: "Flüge, Hotel & Nahverkehrstickets bestätigen",
                                checklistItems: ["Bordkarten offline speichern", "Reiseplan mit der Familie teilen"],
                                durationMinutes: 30
                            )
                        ],
                        headings: [
                            TemplateHeadingSpec(
                                title: "Dokumente & Technik",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Reisepass, Ausweis, Karten & Reiseversicherung einpacken",
                                        checklistItems: ["Reisepass / Ausweis noch mind. 6 Monate gültig", "Kreditkarte für Auslandsreise freischalten"],
                                        durationMinutes: 15
                                    ),
                                    TemplateTaskSpec(
                                        title: "Ladegeräte, Powerbank & Reiseadapter einpacken",
                                        checklistItems: ["USB-C-Kabel für Laptop & Smartphone", "Kopfhörer mit Geräuschunterdrückung"],
                                        durationMinutes: 15
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Zuhause vor der Abreise",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Wohnung für die Abreise vorbereiten",
                                        checklistItems: ["Kühlschrank leeren & Müll rausbringen", "Nicht benötigte Geräte vom Strom trennen", "Fenster schließen & Heizung regeln"],
                                        durationMinutes: 20
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 10
                ),
                ProjectTemplate(
                    id: "starter-product-launch",
                    title: "Produkt-Launch",
                    notes: "Durchgängiger Meilensteinplan vom QA-Abschluss bis zur Ankündigung.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "1. Qualität & Startbereitschaft",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Vollständige Regressions- & Upgrade-Tests durchführen",
                                        checklistItems: ["Neuinstallation prüfen", "Upgrade von vorheriger stabiler Version testen", "Cloud-Sync-Kurztest durchführen"],
                                        durationMinutes: 60
                                    ),
                                    TemplateTaskSpec(
                                        title: "Versionshinweise & Screenshots fertigstellen",
                                        checklistItems: ["Die 3 wichtigsten Vorteile hervorheben", "Darstellung im Dunkel- & Hellmodus prüfen"],
                                        durationMinutes: 45
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "2. Verteilung & Kommunikation",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Release taggen & Build-Artefakte prüfen",
                                        checklistItems: ["Signiertes Universal-DMG & APK", "SHA-256-Prüfsummen verifiziert"],
                                        durationMinutes: 30
                                    ),
                                    TemplateTaskSpec(
                                        title: "Ankündigung veröffentlichen & Feedback beobachten",
                                        checklistItems: ["Changelog veröffentlichen", "Rückmeldungen am ersten Tag sichten"],
                                        durationMinutes: 45
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 20
                ),
                ProjectTemplate(
                    id: "starter-client-onboarding",
                    title: "Kunden-Onboarding",
                    notes: "Strukturierter Kick-off-Ablauf für neue Kundenprojekte.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "Kick-off & Zugänge",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Vertrag abschließen & gemeinsamen Arbeitsbereich einrichten",
                                        checklistItems: ["Gegengezeichnetes Angebot / NDA", "Gemeinsamen Ordner & Kommunikationskanal anlegen"],
                                        durationMinutes: 30
                                    ),
                                    TemplateTaskSpec(
                                        title: "Kick-off-Gespräch zur Abstimmung durchführen",
                                        checklistItems: ["Erfolgskriterien bestätigen", "Wöchentlichen Rhythmus & Ansprechpartner abstimmen"],
                                        durationMinutes: 45
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Meilensteine der ersten 30 Tage",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Erste Roadmap & schnelles Startergebnis liefern",
                                        checklistItems: ["Meilenstein-Zeitplan entwerfen", "Erstes Ergebnis gemeinsam mit dem Kunden besprechen"],
                                        durationMinutes: 60
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 30
                ),
                ProjectTemplate(
                    id: "starter-weekly-sprint",
                    title: "Wochensprint",
                    notes: "Fokussierter 5-Tage-Sprint mit klaren Ergebnissen und Freitags-Retrospektive.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "Sprint-Ziele",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Die 3 wichtigsten Ergebnisse der Woche festlegen",
                                        checklistItems: ["Ergebnis Nr. 1", "Ergebnis Nr. 2", "Ergebnis Nr. 3"],
                                        durationMinutes: 30
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Fokusblöcke (Deep Work)",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Kernergebnis Nr. 1",
                                        durationMinutes: 90
                                    ),
                                    TemplateTaskSpec(
                                        title: "Kernergebnis Nr. 2",
                                        durationMinutes: 90
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Abschluss & Retrospektive",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Fortschritt präsentieren & Freitags-Wochenrückblick durchführen",
                                        checklistItems: ["Abgeschlossene Arbeit ausliefern", "Erkenntnisse & offene Punkte festhalten"],
                                        durationMinutes: 30
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 40
                )
            ]

        case "fr":
            return [
                ProjectTemplate(
                    id: "starter-trip-packing",
                    title: "Voyage & Bagages",
                    notes: "Liste sereine avant le départ, réservations et essentiels de voyage.",
                    blueprintJson: ProjectTemplateBlueprint(
                        unheadedTasks: [
                            TemplateTaskSpec(
                                title: "Confirmer les vols, l'hôtel et les titres de transport",
                                checklistItems: ["Enregistrer les cartes d'embarquement hors ligne", "Partager l'itinéraire avec les proches"],
                                durationMinutes: 30
                            )
                        ],
                        headings: [
                            TemplateHeadingSpec(
                                title: "Documents & Électronique",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Préparer passeport, pièce d'identité, cartes et assurance voyage",
                                        checklistItems: ["Passeport / pièce d'identité valide 6+ mois", "Activer l'option voyage de la carte bancaire"],
                                        durationMinutes: 15
                                    ),
                                    TemplateTaskSpec(
                                        title: "Emporter chargeurs, batterie externe et adaptateur",
                                        checklistItems: ["Câbles USB-C pour ordinateur et téléphone", "Casque à réduction de bruit"],
                                        durationMinutes: 15
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Maison avant le départ",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Préparer le logement avant de partir",
                                        checklistItems: ["Vider le réfrigérateur et sortir les poubelles", "Débrancher les appareils non essentiels", "Fermer les fenêtres et régler le chauffage"],
                                        durationMinutes: 20
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 10
                ),
                ProjectTemplate(
                    id: "starter-product-launch",
                    title: "Lancement de produit",
                    notes: "Plan complet par étapes, de la validation QA jusqu'à l'annonce officielle.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "1. Qualité & Préparation",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Exécuter la suite complète de tests de non-régression et de migration",
                                        checklistItems: ["Vérifier une installation propre", "Tester la mise à jour depuis la version stable précédente", "Test rapide de synchronisation cloud"],
                                        durationMinutes: 60
                                    ),
                                    TemplateTaskSpec(
                                        title: "Finaliser les notes de version et les captures d'écran",
                                        checklistItems: ["Mettre en avant les 3 bénéfices clés", "Vérifier le rendu en mode clair et sombre"],
                                        durationMinutes: 45
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "2. Distribution & Communication",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Taguer la version et vérifier les paquets compilés",
                                        checklistItems: ["DMG universel et APK signés", "Sommes de contrôle SHA-256 vérifiées"],
                                        durationMinutes: 30
                                    ),
                                    TemplateTaskSpec(
                                        title: "Publier l'annonce et suivre les retours",
                                        checklistItems: ["Publier le journal des modifications", "Trier les retours du premier jour"],
                                        durationMinutes: 45
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 20
                ),
                ProjectTemplate(
                    id: "starter-client-onboarding",
                    title: "Intégration client",
                    notes: "Parcours de démarrage structuré pour les nouvelles missions clients.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "Lancement & Accès",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Signer le contrat et préparer l'espace de travail partagé",
                                        checklistItems: ["Contrat / accord de confidentialité contresigné", "Créer le dossier partagé et le canal d'échange"],
                                        durationMinutes: 30
                                    ),
                                    TemplateTaskSpec(
                                        title: "Animer la réunion de lancement et d'alignement",
                                        checklistItems: ["Confirmer les indicateurs de réussite", "Définir le rythme hebdomadaire et les interlocuteurs"],
                                        durationMinutes: 45
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Jalons des 30 premiers jours",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Livrer la feuille de route initiale et un premier livrable rapide",
                                        checklistItems: ["Rédiger le calendrier des jalons", "Passer en revue le premier livrable avec le client"],
                                        durationMinutes: 60
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 30
                ),
                ProjectTemplate(
                    id: "starter-weekly-sprint",
                    title: "Sprint hebdomadaire",
                    notes: "Sprint ciblé de 5 jours avec objectifs clairs et rétrospective du vendredi.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "Objectifs du sprint",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Définir les 3 résultats prioritaires de la semaine",
                                        checklistItems: ["Résultat n° 1", "Résultat n° 2", "Résultat n° 3"],
                                        durationMinutes: 30
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Blocs de travail approfondi",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Livrable principal n° 1",
                                        durationMinutes: 90
                                    ),
                                    TemplateTaskSpec(
                                        title: "Livrable principal n° 2",
                                        durationMinutes: 90
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Bilan & Rétrospective",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Présenter les avancées et faire la revue hebdomadaire du vendredi",
                                        checklistItems: ["Livrer le travail terminé", "Noter les enseignements et les suites"],
                                        durationMinutes: 30
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 40
                )
            ]

        case "it":
            return [
                ProjectTemplate(
                    id: "starter-trip-packing",
                    title: "Viaggio e Valigia",
                    notes: "Checklist serena prima della partenza, prenotazioni ed essenziali da viaggio.",
                    blueprintJson: ProjectTemplateBlueprint(
                        unheadedTasks: [
                            TemplateTaskSpec(
                                title: "Confermare voli, hotel e abbonamenti per i mezzi locali",
                                checklistItems: ["Salvare le carte d'imbarco offline", "Condividere l'itinerario con la famiglia"],
                                durationMinutes: 30
                            )
                        ],
                        headings: [
                            TemplateHeadingSpec(
                                title: "Documenti e Tecnologia",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Preparare passaporto, documento, carte e assicurazione viaggio",
                                        checklistItems: ["Passaporto / documento valido per oltre 6 mesi", "Avviso di viaggio per la carta di credito"],
                                        durationMinutes: 15
                                    ),
                                    TemplateTaskSpec(
                                        title: "Mettere in valigia caricabatterie, powerbank e adattatore",
                                        checklistItems: ["Cavi USB-C per laptop e telefono", "Cuffie con cancellazione del rumore"],
                                        durationMinutes: 15
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Casa prima di partire",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Preparare la casa per la partenza",
                                        checklistItems: ["Svuotare il frigo e portare fuori i rifiuti", "Scollegare gli elettrodomestici non essenziali", "Chiudere le finestre e regolare il termostato"],
                                        durationMinutes: 20
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 10
                ),
                ProjectTemplate(
                    id: "starter-product-launch",
                    title: "Lancio Prodotto",
                    notes: "Piano completo a tappe dal blocco QA fino all'annuncio.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "1. Qualità e Preparazione",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Eseguire i test completi di regressione e migrazione",
                                        checklistItems: ["Verifica installazione pulita", "Aggiornamento dalla precedente versione stabile", "Test rapido della sincronizzazione cloud"],
                                        durationMinutes: 60
                                    ),
                                    TemplateTaskSpec(
                                        title: "Finalizzare note di rilascio e schermate",
                                        checklistItems: ["Evidenziare i 3 principali vantaggi per l'utente", "Verificare la resa in modalità chiara e scura"],
                                        durationMinutes: 45
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "2. Distribuzione e Comunicazione",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Taggare la versione e verificare i pacchetti di build",
                                        checklistItems: ["DMG universale e APK firmati", "Checksum SHA-256 verificati"],
                                        durationMinutes: 30
                                    ),
                                    TemplateTaskSpec(
                                        title: "Pubblicare l'annuncio e monitorare i feedback",
                                        checklistItems: ["Pubblicare il changelog", "Smistare i feedback del primo giorno"],
                                        durationMinutes: 45
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 20
                ),
                ProjectTemplate(
                    id: "starter-client-onboarding",
                    title: "Onboarding Cliente",
                    notes: "Flusso di avvio strutturato per nuove collaborazioni con i clienti.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "Avvio e Accessi",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Firmare l'accordo e configurare lo spazio di lavoro condiviso",
                                        checklistItems: ["Contratto / NDA controfirmato", "Creare cartella condivisa e canale di comunicazione"],
                                        durationMinutes: 30
                                    ),
                                    TemplateTaskSpec(
                                        title: "Tenere la call iniziale di allineamento",
                                        checklistItems: ["Confermare le metriche di successo", "Concordare cadenza settimanale e referenti"],
                                        durationMinutes: 45
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Traguardi dei primi 30 giorni",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Consegnare la roadmap iniziale e un primo risultato rapido",
                                        checklistItems: ["Bozza del calendario delle tappe", "Rivedere il primo deliverable con il cliente"],
                                        durationMinutes: 60
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 30
                ),
                ProjectTemplate(
                    id: "starter-weekly-sprint",
                    title: "Sprint Settimanale",
                    notes: "Sprint focalizzato di 5 giorni con obiettivi chiari e retrospettiva del venerdì.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "Obiettivi dello Sprint",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Definire i 3 risultati principali della settimana",
                                        checklistItems: ["Risultato n. 1", "Risultato n. 2", "Risultato n. 3"],
                                        durationMinutes: 30
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Blocchi di Lavoro Concentrato",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Risultato chiave n. 1",
                                        durationMinutes: 90
                                    ),
                                    TemplateTaskSpec(
                                        title: "Risultato chiave n. 2",
                                        durationMinutes: 90
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Chiusura e Retrospettiva",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Mostrare i progressi ed eseguire la revisione settimanale del venerdì",
                                        checklistItems: ["Rilasciare il lavoro completato", "Annotare lezioni apprese e attività da riportare"],
                                        durationMinutes: 30
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 40
                )
            ]

        case "es":
            return [
                ProjectTemplate(
                    id: "starter-trip-packing",
                    title: "Viaje y Equipaje",
                    notes: "Lista tranquila antes de salir, reservas e imprescindibles de viaje.",
                    blueprintJson: ProjectTemplateBlueprint(
                        unheadedTasks: [
                            TemplateTaskSpec(
                                title: "Confirmar vuelos, hotel y abonos de transporte local",
                                checklistItems: ["Guardar tarjetas de embarque sin conexión", "Compartir itinerario con la familia"],
                                durationMinutes: 30
                            )
                        ],
                        headings: [
                            TemplateHeadingSpec(
                                title: "Documentos y Tecnología",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Preparar pasaporte, documento, tarjetas y seguro de viaje",
                                        checklistItems: ["Pasaporte / documento válido más de 6 meses", "Aviso de viaje en la tarjeta bancaria"],
                                        durationMinutes: 15
                                    ),
                                    TemplateTaskSpec(
                                        title: "Guardar cargadores, batería externa y adaptador de viaje",
                                        checklistItems: ["Cables USB-C para portátil y móvil", "Auriculares con cancelación de ruido"],
                                        durationMinutes: 15
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Casa antes de salir",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Preparar la casa para la salida",
                                        checklistItems: ["Vaciar la nevera y sacar la basura", "Desenchufar aparatos no esenciales", "Cerrar ventanas y ajustar el termostato"],
                                        durationMinutes: 20
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 10
                ),
                ProjectTemplate(
                    id: "starter-product-launch",
                    title: "Lanzamiento de Producto",
                    notes: "Plan integral de hitos desde el cierre de QA hasta el anuncio.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "1. Calidad y Preparación",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Ejecutar pruebas completas de regresión y migración",
                                        checklistItems: ["Verificar instalación limpia", "Probar actualización desde la versión estable anterior", "Prueba rápida de sincronización en la nube"],
                                        durationMinutes: 60
                                    ),
                                    TemplateTaskSpec(
                                        title: "Finalizar notas de la versión y capturas de pantalla",
                                        checklistItems: ["Destacar los 3 beneficios principales", "Revisar el diseño en modo claro y oscuro"],
                                        durationMinutes: 45
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "2. Distribución y Comunicación",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Etiquetar versión y verificar artefactos de compilación",
                                        checklistItems: ["DMG universal y APK firmados", "Sumas SHA-256 verificadas"],
                                        durationMinutes: 30
                                    ),
                                    TemplateTaskSpec(
                                        title: "Publicar anuncio y supervisar comentarios",
                                        checklistItems: ["Publicar registro de cambios", "Clasificar comentarios del primer día"],
                                        durationMinutes: 45
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 20
                ),
                ProjectTemplate(
                    id: "starter-client-onboarding",
                    title: "Incorporación de Cliente",
                    notes: "Flujo de inicio estructurado para nuevos proyectos con clientes.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "Inicio y Accesos",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Firmar acuerdo y configurar espacio de trabajo compartido",
                                        checklistItems: ["Contrato / acuerdo de confidencialidad firmado", "Crear carpeta compartida y canal de comunicación"],
                                        durationMinutes: 30
                                    ),
                                    TemplateTaskSpec(
                                        title: "Realizar reunión inicial de alineación",
                                        checklistItems: ["Confirmar métricas de éxito", "Acordar cadencia semanal e interlocutores"],
                                        durationMinutes: 45
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Hitos de los primeros 30 días",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Entregar hoja de ruta inicial y primer entregable rápido",
                                        checklistItems: ["Redactar cronograma de hitos", "Revisar el primer entregable con el cliente"],
                                        durationMinutes: 60
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 30
                ),
                ProjectTemplate(
                    id: "starter-weekly-sprint",
                    title: "Sprint Semanal",
                    notes: "Sprint enfocado de 5 días con resultados claros y retrospectiva del viernes.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "Objetivos del Sprint",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Definir los 3 resultados clave de la semana",
                                        checklistItems: ["Resultado n.º 1", "Resultado n.º 2", "Resultado n.º 3"],
                                        durationMinutes: 30
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Bloques de Trabajo Profundo",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Entregable clave n.º 1",
                                        durationMinutes: 90
                                    ),
                                    TemplateTaskSpec(
                                        title: "Entregable clave n.º 2",
                                        durationMinutes: 90
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Cierre y Retrospectiva",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Mostrar avances y realizar la revisión semanal del viernes",
                                        checklistItems: ["Entregar el trabajo completado", "Anotar aprendizajes y tareas pendientes"],
                                        durationMinutes: 30
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 40
                )
            ]

        default:
            return [
                ProjectTemplate(
                    id: "starter-trip-packing",
                    title: "Trip & Packing",
                    notes: "Calm pre-departure checklist, bookings, and travel essentials.",
                    blueprintJson: ProjectTemplateBlueprint(
                        unheadedTasks: [
                            TemplateTaskSpec(
                                title: "Confirm flights, hotel & local transit passes",
                                checklistItems: ["Save boarding passes offline", "Share itinerary with family"],
                                durationMinutes: 30
                            )
                        ],
                        headings: [
                            TemplateHeadingSpec(
                                title: "Documents & Tech",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Pack passport, ID, cards & travel insurance",
                                        checklistItems: ["Passport / ID valid 6+ months", "Credit card travel notice"],
                                        durationMinutes: 15
                                    ),
                                    TemplateTaskSpec(
                                        title: "Pack chargers, power bank & travel adapter",
                                        checklistItems: ["USB-C laptop & phone cables", "Noise-canceling headphones"],
                                        durationMinutes: 15
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Home Before Leaving",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Prep home for departure",
                                        checklistItems: ["Empty fridge & take out trash", "Unplug non-essential appliances", "Lock windows & set thermostat"],
                                        durationMinutes: 20
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 10
                ),
                ProjectTemplate(
                    id: "starter-product-launch",
                    title: "Product Launch",
                    notes: "End-to-end milestone plan from QA freeze to announcement.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "1. Quality & Readiness",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Run full regression & upgrade migration suite",
                                        checklistItems: ["Fresh install verification", "Upgrade from prior stable release", "Cloud sync smoke test"],
                                        durationMinutes: 60
                                    ),
                                    TemplateTaskSpec(
                                        title: "Finalize release notes & screenshots",
                                        checklistItems: ["Highlight top 3 user benefits", "Verify dark & light mode visuals"],
                                        durationMinutes: 45
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "2. Distribution & Comms",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Tag release & verify build artifacts",
                                        checklistItems: ["Signed Universal DMG & APK", "SHA-256 checksums verified"],
                                        durationMinutes: 30
                                    ),
                                    TemplateTaskSpec(
                                        title: "Publish announcement & monitor feedback",
                                        checklistItems: ["Post changelog", "Triage first-day feedback"],
                                        durationMinutes: 45
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 20
                ),
                ProjectTemplate(
                    id: "starter-client-onboarding",
                    title: "Client Onboarding",
                    notes: "Structured kickoff workflow for new client engagements.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "Kickoff & Access",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Execute agreement & set up shared workspace",
                                        checklistItems: ["Countersigned SOW / NDA", "Create shared folder & communication channel"],
                                        durationMinutes: 30
                                    ),
                                    TemplateTaskSpec(
                                        title: "Host alignment kickoff call",
                                        checklistItems: ["Confirm success metrics", "Agree on weekly cadence & stakeholders"],
                                        durationMinutes: 45
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "First 30-Day Milestones",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Deliver initial roadmap & quick-win deliverable",
                                        checklistItems: ["Draft milestone timeline", "Review first deliverable with client"],
                                        durationMinutes: 60
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 30
                ),
                ProjectTemplate(
                    id: "starter-weekly-sprint",
                    title: "Weekly Sprint",
                    notes: "Focused 5-day execution sprint with clear outcomes and Friday retro.",
                    blueprintJson: ProjectTemplateBlueprint(
                        headings: [
                            TemplateHeadingSpec(
                                title: "Sprint Goals",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Define top 3 outcomes for the week",
                                        checklistItems: ["Outcome #1", "Outcome #2", "Outcome #3"],
                                        durationMinutes: 30
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Deep Work Blocks",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Core Deliverable #1",
                                        durationMinutes: 90
                                    ),
                                    TemplateTaskSpec(
                                        title: "Core Deliverable #2",
                                        durationMinutes: 90
                                    )
                                ]
                            ),
                            TemplateHeadingSpec(
                                title: "Wrap-Up & Retro",
                                tasks: [
                                    TemplateTaskSpec(
                                        title: "Demo progress & run Friday Weekly Review",
                                        checklistItems: ["Ship completed work", "Capture lessons & carry-overs"],
                                        durationMinutes: 30
                                    )
                                ]
                            )
                        ]
                    ).toJSONString(),
                    sortOrder: 40
                )
            ]
        }
    }
}

public struct MacCalendarInfo: Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let sourceTitle: String
    public let colorHex: UInt32
    public let isPrimary: Bool

    public init(id: String, title: String, sourceTitle: String, colorHex: UInt32, isPrimary: Bool = false) {
        self.id = id
        self.title = title
        self.sourceTitle = sourceTitle
        self.colorHex = colorHex
        self.isPrimary = isPrimary
    }
}

public struct CalendarEventItem: Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public let eventIdentifier: String
    public let calendarId: String
    public let calendarDisplayName: String
    public let title: String
    public let startMillis: Int64
    public let endMillis: Int64
    public let isAllDay: Bool
    public let colorHex: UInt32
    public let startDateEpochDay: Int
    public let endDateInclusiveEpochDay: Int

    public init(
        id: String,
        eventIdentifier: String,
        calendarId: String,
        calendarDisplayName: String,
        title: String,
        startMillis: Int64,
        endMillis: Int64,
        isAllDay: Bool,
        colorHex: UInt32,
        startDateEpochDay: Int,
        endDateInclusiveEpochDay: Int
    ) {
        self.id = id
        self.eventIdentifier = eventIdentifier
        self.calendarId = calendarId
        self.calendarDisplayName = calendarDisplayName
        self.title = title
        self.startMillis = startMillis
        self.endMillis = endMillis
        self.isAllDay = isAllDay
        self.colorHex = colorHex
        self.startDateEpochDay = startDateEpochDay
        self.endDateInclusiveEpochDay = endDateInclusiveEpochDay
    }
}

public struct DatabaseStatistics: Equatable, Sendable {
    public var openTasks: Int = 0
    public var completedTasks: Int = 0
    public var deletedTasks: Int = 0
    public var canceledTasks: Int = 0
    public var todayActiveTasks: Int = 0
    public var recurringTasks: Int = 0
    public var activeProjects: Int = 0
    public var savedTags: Int = 0
    public var totalRecords: Int = 0

    public init(
        openTasks: Int = 0,
        completedTasks: Int = 0,
        deletedTasks: Int = 0,
        canceledTasks: Int = 0,
        todayActiveTasks: Int = 0,
        recurringTasks: Int = 0,
        activeProjects: Int = 0,
        savedTags: Int = 0,
        totalRecords: Int = 0
    ) {
        self.openTasks = openTasks
        self.completedTasks = completedTasks
        self.deletedTasks = deletedTasks
        self.canceledTasks = canceledTasks
        self.todayActiveTasks = todayActiveTasks
        self.recurringTasks = recurringTasks
        self.activeProjects = activeProjects
        self.savedTags = savedTags
        self.totalRecords = totalRecords
    }
}

public struct AppPreferences: Codable, Equatable, Sendable {
    public var localOnlyDbMode: Bool
    public var sidebarVisible: Bool
    public var dailyDigestEnabled: Bool
    public var dailyDigestHour: Int
    public var dailyDigestMinute: Int
    public var language: AppLanguage
    public var calendarEventsEnabled: Bool
    public var selectedCalendarIds: [String]
    public var groupTodayByProjectOrArea: Bool
    public var biometricLockEnabled: Bool
    public var autoUpdateCheckEnabled: Bool
    public var includeBetaUpdates: Bool
    public var hideSampleSetupBanner: Bool
    public var menuBarSelectedProjectId: String?

    public init(
        localOnlyDbMode: Bool = false,
        sidebarVisible: Bool = true,
        dailyDigestEnabled: Bool = false,
        dailyDigestHour: Int = 9,
        dailyDigestMinute: Int = 0,
        language: AppLanguage = .system,
        calendarEventsEnabled: Bool = false,
        selectedCalendarIds: [String] = [],
        groupTodayByProjectOrArea: Bool = false,
        biometricLockEnabled: Bool = false,
        autoUpdateCheckEnabled: Bool = true,
        includeBetaUpdates: Bool = AppUpdateService.currentVersionIsPrerelease,
        hideSampleSetupBanner: Bool = false,
        menuBarSelectedProjectId: String? = nil
    ) {
        self.localOnlyDbMode = localOnlyDbMode
        self.sidebarVisible = sidebarVisible
        self.dailyDigestEnabled = dailyDigestEnabled
        self.dailyDigestHour = dailyDigestHour
        self.dailyDigestMinute = dailyDigestMinute
        self.language = language
        self.calendarEventsEnabled = calendarEventsEnabled
        self.selectedCalendarIds = selectedCalendarIds
        self.groupTodayByProjectOrArea = groupTodayByProjectOrArea
        self.biometricLockEnabled = biometricLockEnabled
        self.autoUpdateCheckEnabled = autoUpdateCheckEnabled
        self.includeBetaUpdates = includeBetaUpdates
        self.hideSampleSetupBanner = hideSampleSetupBanner
        self.menuBarSelectedProjectId = menuBarSelectedProjectId
    }

    private enum CodingKeys: String, CodingKey {
        case localOnlyDbMode, sidebarVisible, dailyDigestEnabled, dailyDigestHour, dailyDigestMinute
        case language, calendarEventsEnabled, selectedCalendarIds, groupTodayByProjectOrArea
        case biometricLockEnabled, autoUpdateCheckEnabled, includeBetaUpdates
        case hideSampleSetupBanner, menuBarSelectedProjectId
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.localOnlyDbMode = try c.decodeIfPresent(Bool.self, forKey: .localOnlyDbMode) ?? false
        self.sidebarVisible = try c.decodeIfPresent(Bool.self, forKey: .sidebarVisible) ?? true
        self.dailyDigestEnabled = try c.decodeIfPresent(Bool.self, forKey: .dailyDigestEnabled) ?? false
        self.dailyDigestHour = try c.decodeIfPresent(Int.self, forKey: .dailyDigestHour) ?? 9
        self.dailyDigestMinute = try c.decodeIfPresent(Int.self, forKey: .dailyDigestMinute) ?? 0
        self.language = try c.decodeIfPresent(AppLanguage.self, forKey: .language) ?? .system
        self.calendarEventsEnabled = try c.decodeIfPresent(Bool.self, forKey: .calendarEventsEnabled) ?? false
        self.selectedCalendarIds = try c.decodeIfPresent([String].self, forKey: .selectedCalendarIds) ?? []
        self.groupTodayByProjectOrArea = try c.decodeIfPresent(Bool.self, forKey: .groupTodayByProjectOrArea) ?? false
        self.biometricLockEnabled = try c.decodeIfPresent(Bool.self, forKey: .biometricLockEnabled) ?? false
        self.autoUpdateCheckEnabled = try c.decodeIfPresent(Bool.self, forKey: .autoUpdateCheckEnabled) ?? true
        self.includeBetaUpdates = try c.decodeIfPresent(Bool.self, forKey: .includeBetaUpdates) ?? AppUpdateService.currentVersionIsPrerelease
        self.hideSampleSetupBanner = try c.decodeIfPresent(Bool.self, forKey: .hideSampleSetupBanner) ?? false
        self.menuBarSelectedProjectId = try c.decodeIfPresent(String.self, forKey: .menuBarSelectedProjectId)
    }

    private static let storageKey = "thingsbook_macos_preferences_v1"

    public static func load(from defaults: UserDefaults = .standard) -> AppPreferences {
        guard let data = defaults.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode(AppPreferences.self, from: data) else {
            return AppPreferences()
        }
        return decoded
    }

    public func save(to defaults: UserDefaults = .standard) {
        if let data = try? JSONEncoder().encode(self) {
            defaults.set(data, forKey: Self.storageKey)
        }
    }
}

public struct DetachedTaskPlacement: Equatable, Hashable, Sendable {
    public let projectId: String?
    public let headingId: String?

    public init(projectId: String?, headingId: String?) {
        self.projectId = projectId
        self.headingId = headingId
    }
}

public struct PendingProjectDeletion: Identifiable, Equatable, Sendable {
    public var id: String { project.id }
    public let project: Project
    public let taskCount: Int

    public init(project: Project, taskCount: Int) {
        self.project = project
        self.taskCount = taskCount
    }
}

public enum UndoActionKind: Equatable, Sendable {
    case taskCompleted(taskId: String, title: String, spawnedRecurringTaskId: String?)
    case taskDeleted(taskId: String, title: String)
    case tasksDeleted([String])
    case projectDeleted(projectId: String, title: String, previousTaskProjects: [String: DetachedTaskPlacement], deletedTaskIds: [String] = [])
}

public struct UndoBannerState: Identifiable, Equatable, Sendable {
    public let id = UUID()
    public let message: String
    public let action: UndoActionKind
    public let createdAt: Date

    public init(message: String, action: UndoActionKind, createdAt: Date = Date()) {
        self.message = message
        self.action = action
        self.createdAt = createdAt
    }
}

public struct FocusTimerState: Equatable, Sendable {
    public var taskId: String
    public var taskTitle: String
    public var remainingSeconds: Int
    public var totalSeconds: Int
    public var isPaused: Bool

    public init(
        taskId: String,
        taskTitle: String,
        remainingSeconds: Int = 25 * 60,
        totalSeconds: Int = 25 * 60,
        isPaused: Bool = false
    ) {
        self.taskId = taskId
        self.taskTitle = taskTitle
        self.remainingSeconds = remainingSeconds
        self.totalSeconds = totalSeconds
        self.isPaused = isPaused
    }

    public var formattedCountdown: String {
        let mins = max(0, remainingSeconds) / 60
        let secs = max(0, remainingSeconds) % 60
        return String(format: "%02d:%02d", mins, secs)
    }

    public var progress: Double {
        guard totalSeconds > 0 else { return 0 }
        return min(1.0, max(0.0, 1.0 - (Double(remainingSeconds) / Double(totalSeconds))))
    }
}

public struct MacToolbarLayoutMetrics: Equatable, Sendable {
    public let usableWidth: Double
    public let searchWidth: Double
    public let buttonWidth: Double
    public let buttonHeight: Double
    public let syncButtonWidth: Double
    public let geminiPillMinWidth: Double
    public let itemSpacing: Double
    public let groupDividerPadding: Double
    public let trailingActionsPadding: Double
    public let centerGap: Double
    public let showSearchShortcutBadge: Bool

    public var estimatedActionsBarWidth: Double {
        let group1 = geminiPillMinWidth + (2.0 * buttonWidth) + (2.0 * itemSpacing)
        let divider = (2.0 * groupDividerPadding) + 1.0
        let group2 = (4.0 * buttonWidth) + (3.0 * itemSpacing)
        let group3 = (3.0 * buttonWidth) + syncButtonWidth + (3.0 * itemSpacing)
        return group1 + divider + group2 + divider + group3 + trailingActionsPadding
    }

    public var estimatedTotalControlsWidth: Double {
        searchWidth + estimatedActionsBarWidth
    }

    public var totalBarWidth: Double {
        searchWidth + centerGap + estimatedActionsBarWidth
    }

    public static func compute(
        detailWidth: Double,
        isSidebarCollapsed: Bool = false
    ) -> MacToolbarLayoutMetrics {
        let leadingReserved = isSidebarCollapsed ? 136.0 : 0.0
        let toolbarMargins = 56.0
        let usable = max(494.0, detailWidth - leadingReserved - toolbarMargins)

        let lerp: (Double, Double, Double) -> Double = { a, b, frac in
            a + (b - a) * frac
        }

        let rawSearchW: Double
        let rawBtnW: Double
        let rawGeminiMinW: Double
        let rawSpacing: Double
        let rawDivPad: Double

        if usable < 562.0 {
            // Compact sidebar-expanded minimum window tier (494pt..562pt usable)
            let s = max(0.0, (usable - 494.0) / 68.0)
            rawSearchW = lerp(126.0, 144.0, s)
            rawBtnW = lerp(24.0, 27.0, s)
            rawGeminiMinW = lerp(72.0, 78.0, s)
            rawSpacing = lerp(2.0, 3.0, s)
            rawDivPad = lerp(3.0, 5.0, s)
        } else if usable <= 822.0 {
            // Standard tier up to default 1140pt window / 878pt detail pane (562pt..822pt usable)
            let t = (usable - 562.0) / 260.0
            rawSearchW = lerp(144.0, 208.0, t)
            rawBtnW = lerp(27.0, 33.0, t)
            rawGeminiMinW = lerp(78.0, 88.0, t)
            rawSpacing = lerp(3.0, 10.0, t)
            rawDivPad = lerp(5.0, 13.0, t)
        } else {
            // Wide & fullscreen tier (822pt..1050pt+ usable)
            let u = min(1.0, (usable - 822.0) / 228.0)
            rawSearchW = lerp(208.0, 252.0, u)
            rawBtnW = lerp(33.0, 37.0, u)
            rawGeminiMinW = lerp(88.0, 93.0, u)
            rawSpacing = lerp(10.0, 15.0, u)
            rawDivPad = lerp(13.0, 18.0, u)
        }

        let roundRule: FloatingPointRoundingRule = usable < 590.0 ? .down : .toNearestOrAwayFromZero
        let searchW = rawSearchW.rounded(roundRule)
        let btnW = rawBtnW.rounded(roundRule)
        let syncW = btnW + 8.0
        let geminiMinW = rawGeminiMinW.rounded(roundRule)
        let spacing = rawSpacing.rounded(roundRule)
        let divPad = rawDivPad.rounded(roundRule)
        let trailingPad = 4.0

        let group1 = geminiMinW + (2.0 * btnW) + (2.0 * spacing)
        let divider = (2.0 * divPad) + 1.0
        let group2 = (4.0 * btnW) + (3.0 * spacing)
        let group3 = (3.0 * btnW) + syncW + (3.0 * spacing)
        let actionsW = group1 + divider + group2 + divider + group3 + trailingPad
        let computedGap = max(8.0, (usable - searchW - actionsW).rounded(.down))

        return MacToolbarLayoutMetrics(
            usableWidth: usable,
            searchWidth: searchW,
            buttonWidth: btnW,
            buttonHeight: 30.0,
            syncButtonWidth: syncW,
            geminiPillMinWidth: geminiMinW,
            itemSpacing: spacing,
            groupDividerPadding: divPad,
            trailingActionsPadding: trailingPad,
            centerGap: computedGap,
            showSearchShortcutBadge: searchW >= 165.0
        )
    }
}

public enum QuickFindDateFilter: String, CaseIterable, Identifiable, Equatable, Hashable, Codable, Sendable {
    case all = "ALL"
    case today = "TODAY"
    case overdue = "OVERDUE"
    case next7Days = "NEXT_7_DAYS"
    case thisMonth = "THIS_MONTH"
    case noDate = "NO_DATE"

    public var id: String { rawValue }

    public var titleKey: String {
        switch self {
        case .all: return "search_filter_date_all"
        case .today: return "search_filter_date_today"
        case .overdue: return "search_filter_date_overdue"
        case .next7Days: return "search_filter_date_next_7_days"
        case .thisMonth: return "search_filter_date_this_month"
        case .noDate: return "search_filter_date_none"
        }
    }
}

public enum QuickFindEntityScope: String, CaseIterable, Identifiable, Equatable, Hashable, Codable, Sendable {
    case all = "ALL"
    case tasks = "TASKS"
    case projects = "PROJECTS"

    public var id: String { rawValue }

    public var titleKey: String {
        switch self {
        case .all: return "search_filter_scope_all"
        case .tasks: return "search_filter_scope_tasks"
        case .projects: return "search_filter_scope_projects"
        }
    }
}

public struct QuickFindFilter: Equatable, Hashable, Sendable {
    public var hideCompleted: Bool
    public var dateFilter: QuickFindDateFilter
    public var hasDeadlineOnly: Bool
    public var hasChecklistOnly: Bool
    public var tagId: String?
    public var scope: QuickFindEntityScope

    public init(
        hideCompleted: Bool = false,
        dateFilter: QuickFindDateFilter = .all,
        hasDeadlineOnly: Bool = false,
        hasChecklistOnly: Bool = false,
        tagId: String? = nil,
        scope: QuickFindEntityScope = .all
    ) {
        self.hideCompleted = hideCompleted
        self.dateFilter = dateFilter
        self.hasDeadlineOnly = hasDeadlineOnly
        self.hasChecklistOnly = hasChecklistOnly
        self.tagId = tagId
        self.scope = scope
    }

    public var hasActiveFilters: Bool {
        hideCompleted
            || dateFilter != .all
            || hasDeadlineOnly
            || hasChecklistOnly
            || tagId != nil
            || scope != .all
    }

    public func shouldShowSmartViewsAndAreas(forQuery query: String) -> Bool {
        guard scope == .all,
              dateFilter == .all,
              !hasDeadlineOnly,
              !hasChecklistOnly,
              tagId == nil else {
            return false
        }
        if hideCompleted && query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return false
        }
        return true
    }

    public func matchesTask(
        _ task: TaskItem,
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) -> Bool {
        guard !task.isDeleted else { return false }
        if scope == .projects { return false }
        if hideCompleted && task.status != .open { return false }
        if hasDeadlineOnly && task.deadline == nil { return false }
        if hasChecklistOnly && task.checklistItems.isEmpty { return false }
        if let requiredTagId = tagId, !task.tags.contains(where: { $0.id == requiredTagId }) {
            return false
        }

        return Self.matchesDatePredicate(
            dateFilter: dateFilter,
            whenDate: task.whenDate,
            deadline: task.deadline,
            scheduleState: task.scheduleState,
            isOpenOrActive: task.status == .open,
            referenceDate: referenceDate,
            calendar: calendar
        )
    }

    public func matchesProject(
        _ project: Project,
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) -> Bool {
        guard !project.isDeleted else { return false }
        if scope == .tasks { return false }
        if hasChecklistOnly { return false }
        if hideCompleted && project.status != .active { return false }
        if hasDeadlineOnly && project.deadline == nil { return false }
        if let requiredTagId = tagId, !project.tagIds.contains(requiredTagId) {
            return false
        }

        return Self.matchesDatePredicate(
            dateFilter: dateFilter,
            whenDate: project.whenDate,
            deadline: project.deadline,
            scheduleState: project.scheduleState,
            isOpenOrActive: project.status == .active,
            referenceDate: referenceDate,
            calendar: calendar
        )
    }

    private static func matchesDatePredicate(
        dateFilter: QuickFindDateFilter,
        whenDate: Int64?,
        deadline: Int64?,
        scheduleState: ScheduleState,
        isOpenOrActive: Bool,
        referenceDate: Date,
        calendar: Calendar
    ) -> Bool {
        guard dateFilter != .all else { return true }

        let startOfTodayDate = calendar.startOfDay(for: referenceDate)
        let todayStart = Int64(startOfTodayDate.timeIntervalSince1970 * 1000.0)
        let tomorrowDate = calendar.date(byAdding: .day, value: 1, to: startOfTodayDate) ?? startOfTodayDate.addingTimeInterval(86_400)
        let tomorrowStart = Int64(tomorrowDate.timeIntervalSince1970 * 1000.0)
        let next7EndDate = calendar.date(byAdding: .day, value: 8, to: startOfTodayDate) ?? startOfTodayDate.addingTimeInterval(8 * 86_400)
        let next7EndExclusive = Int64(next7EndDate.timeIntervalSince1970 * 1000.0)

        let monthComps = calendar.dateComponents([.year, .month], from: startOfTodayDate)
        let startOfMonthDate = calendar.date(from: monthComps) ?? startOfTodayDate
        let monthStart = Int64(startOfMonthDate.timeIntervalSince1970 * 1000.0)
        let nextMonthDate = calendar.date(byAdding: .month, value: 1, to: startOfMonthDate) ?? startOfMonthDate.addingTimeInterval(31 * 86_400)
        let nextMonthStart = Int64(nextMonthDate.timeIntervalSince1970 * 1000.0)

        let isTodaySchedule = scheduleState == .today || scheduleState == .thisEvening

        switch dateFilter {
        case .all:
            return true
        case .today:
            let whenToday = whenDate.map { $0 >= todayStart && $0 < tomorrowStart } ?? false
            let deadlineToday = deadline.map { $0 >= todayStart && $0 < tomorrowStart } ?? false
            return (isOpenOrActive && isTodaySchedule) || whenToday || deadlineToday
        case .overdue:
            let deadlinePast = deadline.map { $0 < todayStart } ?? false
            let whenPast = whenDate.map { $0 < todayStart } ?? false
            return isOpenOrActive && (deadlinePast || whenPast)
        case .next7Days:
            let whenIn7 = whenDate.map { $0 >= todayStart && $0 < next7EndExclusive } ?? false
            let deadlineIn7 = deadline.map { $0 >= todayStart && $0 < next7EndExclusive } ?? false
            return (isOpenOrActive && isTodaySchedule) || whenIn7 || deadlineIn7
        case .thisMonth:
            let whenInMonth = whenDate.map { $0 >= monthStart && $0 < nextMonthStart } ?? false
            let deadlineInMonth = deadline.map { $0 >= monthStart && $0 < nextMonthStart } ?? false
            return (isOpenOrActive && isTodaySchedule) || whenInMonth || deadlineInMonth
        case .noDate:
            return whenDate == nil
                && deadline == nil
                && scheduleState != .today
                && scheduleState != .thisEvening
                && scheduleState != .upcoming
        }
    }
}

// MARK: - Instant Cross-Control Tooltip Timing Coordinator

/// Pure state machine governing responsive hover tooltip timing:
/// - Cold hover waits `initialDelayMs` (160ms) so casual pointer sweeps do not flicker.
/// - Moving between controls while a tooltip is visible or within `warmHandoffWindowMs` (450ms)
///   transitions in `0ms` so tooltips never lag behind the cursor or display stale text.
/// - Dynamic text changes on the currently hovered control update in `0ms`.
/// - Pointer exit from an older control never clobbers a newer control that already took ownership.
public struct TooltipTimingCoordinator: Sendable, Equatable {
    public enum Action: Sendable, Equatable {
        case none
        case scheduleShow(anchorId: String, text: String, delayMs: Int64)
        case showImmediately(anchorId: String, text: String)
        case hideImmediately
    }

    public let initialDelayMs: Int64
    public let warmHandoffWindowMs: Int64

    public private(set) var hoveredAnchorId: String?
    public private(set) var visibleAnchorId: String?
    public private(set) var visibleText: String
    public private(set) var pendingText: String
    public private(set) var lastHiddenAtMs: Int64?

    public init(
        initialDelayMs: Int64 = 160,
        warmHandoffWindowMs: Int64 = 450
    ) {
        self.initialDelayMs = max(0, initialDelayMs)
        self.warmHandoffWindowMs = max(0, warmHandoffWindowMs)
        self.hoveredAnchorId = nil
        self.visibleAnchorId = nil
        self.visibleText = ""
        self.pendingText = ""
        self.lastHiddenAtMs = nil
    }

    public var isShowing: Bool {
        visibleAnchorId != nil && !visibleText.isEmpty
    }

    public mutating func pointerEntered(
        anchorId: String,
        text: String,
        nowMs: Int64
    ) -> Action {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !anchorId.isEmpty else {
            let wasActive = hoveredAnchorId != nil || visibleAnchorId != nil
            if visibleAnchorId != nil {
                lastHiddenAtMs = nowMs
            }
            hoveredAnchorId = nil
            visibleAnchorId = nil
            visibleText = ""
            pendingText = ""
            return wasActive ? .hideImmediately : .none
        }

        let isWarm = isWarmAt(nowMs: nowMs)
        hoveredAnchorId = anchorId
        pendingText = trimmed

        if isWarm {
            visibleAnchorId = anchorId
            visibleText = trimmed
            lastHiddenAtMs = nil
            return .showImmediately(anchorId: anchorId, text: trimmed)
        } else {
            return .scheduleShow(anchorId: anchorId, text: trimmed, delayMs: initialDelayMs)
        }
    }

    public mutating func commitScheduledShow(
        anchorId: String,
        nowMs: Int64
    ) -> Action {
        guard hoveredAnchorId == anchorId, !pendingText.isEmpty else {
            return .none
        }
        visibleAnchorId = anchorId
        visibleText = pendingText
        lastHiddenAtMs = nil
        return .showImmediately(anchorId: anchorId, text: pendingText)
    }

    public mutating func updateText(
        anchorId: String,
        text: String,
        nowMs: Int64
    ) -> Action {
        guard hoveredAnchorId == anchorId else {
            return .none
        }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            if visibleAnchorId != nil {
                lastHiddenAtMs = nowMs
            }
            hoveredAnchorId = nil
            visibleAnchorId = nil
            visibleText = ""
            pendingText = ""
            return .hideImmediately
        }

        pendingText = trimmed
        if visibleAnchorId == anchorId {
            if visibleText == trimmed {
                return .none
            }
            visibleText = trimmed
            return .showImmediately(anchorId: anchorId, text: trimmed)
        }
        return .none
    }

    public mutating func pointerExited(
        anchorId: String,
        nowMs: Int64
    ) -> Action {
        guard hoveredAnchorId == anchorId || visibleAnchorId == anchorId else {
            return .none
        }
        if visibleAnchorId != nil {
            lastHiddenAtMs = nowMs
        }
        hoveredAnchorId = nil
        visibleAnchorId = nil
        visibleText = ""
        pendingText = ""
        return .hideImmediately
    }

    public mutating func dismissAll(
        nowMs: Int64,
        allowWarmHandoff: Bool = false
    ) -> Action {
        let hadState = hoveredAnchorId != nil || visibleAnchorId != nil
        if allowWarmHandoff && visibleAnchorId != nil {
            lastHiddenAtMs = nowMs
        } else if !allowWarmHandoff {
            lastHiddenAtMs = nil
        }
        hoveredAnchorId = nil
        visibleAnchorId = nil
        visibleText = ""
        pendingText = ""
        return hadState ? .hideImmediately : .none
    }

    private func isWarmAt(nowMs: Int64) -> Bool {
        if visibleAnchorId != nil {
            return true
        }
        if let lastHidden = lastHiddenAtMs, nowMs >= lastHidden {
            return (nowMs - lastHidden) <= warmHandoffWindowMs
        }
        return false
    }
}



