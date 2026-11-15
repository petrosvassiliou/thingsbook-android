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
        guard let data = try? JSONEncoder().encode(self),
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

    public static let builtInStarterTemplates: [ProjectTemplate] = [
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

public enum UndoActionKind: Equatable, Sendable {
    case taskCompleted(taskId: String, title: String, spawnedRecurringTaskId: String?)
    case taskDeleted(taskId: String, title: String)
    case tasksDeleted([String])
    case projectDeleted(projectId: String, title: String, previousTaskProjects: [String: DetachedTaskPlacement])
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
