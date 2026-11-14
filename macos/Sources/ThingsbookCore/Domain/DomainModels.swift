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
        self.sortOrder = sortOrder
        self.tagIds = tagIds
        self.totalTaskCount = totalTaskCount
        self.completedTaskCount = completedTaskCount
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isDeleted = isDeleted
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

public struct TaskItem: Identifiable, Equatable, Hashable, Codable, Sendable {
    public var id: String
    public var title: String
    public var notes: String
    public var status: TaskStatus
    public var scheduleState: ScheduleState
    public var whenDate: Int64?
    public var deadline: Int64?
    public var reminderTime: Int64?
    public var completedAt: Int64?
    public var projectId: String?
    public var headingId: String?
    public var areaId: String?
    public var recurrenceRule: RecurrenceRule?
    public var sortOrder: Double
    public var checklistItems: [ChecklistItem]
    public var tags: [Tag]
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
        completedAt: Int64? = nil,
        projectId: String? = nil,
        headingId: String? = nil,
        areaId: String? = nil,
        recurrenceRule: RecurrenceRule? = nil,
        sortOrder: Double = 1000.0,
        checklistItems: [ChecklistItem] = [],
        tags: [Tag] = [],
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
        self.completedAt = completedAt
        self.projectId = projectId
        self.headingId = headingId
        self.areaId = areaId
        self.recurrenceRule = recurrenceRule
        self.sortOrder = sortOrder
        self.checklistItems = checklistItems
        self.tags = tags
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isDeleted = isDeleted
    }

    public var tagIds: [String] {
        tags.map(\.id)
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
    public var hideSampleSetupBanner: Bool

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
        hideSampleSetupBanner: Bool = false
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
        self.hideSampleSetupBanner = hideSampleSetupBanner
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
