import Foundation
import SwiftUI
import AppKit
import UniformTypeIdentifiers
import ThingsbookCore

public enum GeminiSheetTab: String, CaseIterable, Identifiable {
    case brainDump
    case projectArchitect
    case logbookReview

    public var id: String { rawValue }
}

public enum SettingsSectionTab: String, CaseIterable, Identifiable {
    case general
    case sync
    case calendar
    case notifications
    case backup
    case about

    public var id: String { rawValue }
}

public enum TodayViewMode: String, CaseIterable, Identifiable, Sendable {
    case list
    case timeline

    public var id: String { rawValue }
}

public enum TemplatesSheetTab: Int, CaseIterable, Identifiable, Sendable {
    case gallery = 0
    case importOutline = 1

    public var id: Int { rawValue }
}

@MainActor
public final class ThingsbookStore: ObservableObject {
    public let database: ThingsSQLiteDatabase
    public let cloudSync: FirebaseCloudSyncService

    // MARK: - Published Workspace State
    @Published public var preferences: AppPreferences {
        didSet {
            preferences.save()
        }
    }
    @Published public var selectedDestination: NavigationDestination = .smartView(.today) {
        didSet {
            selectedFilterTagId = nil
            selectedTaskIds.removeAll()
            expandedTaskId = nil
        }
    }
    @Published public var areas: [Area] = []
    @Published public var projects: [Project] = []
    @Published public var projectTemplates: [ProjectTemplate] = []
    @Published public var headingsByProject: [String: [Heading]] = [:]
    @Published public var projectAttachments: [String: [TaskAttachment]] = [:]
    @Published public var tags: [Tag] = []
    @Published public var todayViewMode: TodayViewMode = .list

    @Published public var inboxTasks: [TaskItem] = []
    @Published public var todayDaytimeTasks: [TaskItem] = []
    @Published public var thisEveningTasks: [TaskItem] = []
    @Published public var upcomingTasks: [TaskItem] = []
    @Published public var anytimeTasks: [TaskItem] = []
    @Published public var somedayTasks: [TaskItem] = []
    @Published public var logbookTasks: [TaskItem] = []
    @Published public var trashedTasks: [TaskItem] = []
    @Published public var trashedProjects: [Project] = []
    @Published public var allOpenTasks: [TaskItem] = []
    @Published public var statistics = DatabaseStatistics()
    @Published public var hasSampleTasks = false
    @Published public var hasNonSampleUserTasks = false

    // MARK: - Selection & Filtering
    @Published public var selectedFilterTagId: String? = nil
    @Published public var selectedTaskIds: Set<String> = []
    @Published public var expandedTaskId: String? = nil
    @Published public var collapsedHeadingIds: Set<String> = []

    // MARK: - Overlays, Sheets & Modals
    @Published public var isQuickFindPresented = false
    @Published public var isQuickEntryPresented = false
    @Published public var quickEntryPrefillTitle = ""
    @Published public var quickEntryPrefillNotes = ""
    @Published public var quickEntryAutoStartVoice = false
    @Published public var previewingAttachment: TaskAttachment? = nil
    @Published public var isGeminiSheetPresented = false
    @Published public var geminiInitialTab: GeminiSheetTab = .brainDump
    @Published public var isWeeklyReviewPresented = false
    @Published public var isTemplatesSheetPresented = false
    @Published public var templatesInitialTab: TemplatesSheetTab = .gallery
    @Published public var isSettingsPresented = false
    @Published public var settingsTab: SettingsSectionTab = .general

    @Published public var isNewProjectSheetPresented = false
    @Published public var newProjectDefaultAreaId: String? = nil
    @Published public var editingProject: Project? = nil
    @Published public var pendingProjectDeletion: PendingProjectDeletion? = nil
    @Published public var isNewAreaSheetPresented = false
    @Published public var editingArea: Area? = nil
    @Published public var isAreaManagerPresented = false
    @Published public var isTagManagerPresented = false

    // MARK: - Undo, Focus Timer, Sync, Calendar, Lock & Updates
    @Published public var undoBanner: UndoBannerState? = nil
    @Published public var focusTimer: FocusTimerState? = nil
    @Published public var syncSnapshot = CloudSyncSnapshot()
    @Published public var calendarEvents: [CalendarEventItem] = []
    @Published public var availableCalendars: [MacCalendarInfo] = []
    @Published public var isLocked = false
    @Published public var availableUpdate: ReleaseUpdateInfo? = nil
    @Published public var updatePhase: MacInAppUpdatePhase = .idle
    @Published public var toastMessage: String? = nil

    // Backup password prompt state
    @Published public var pendingEncryptedBackupData: Data? = nil
    @Published public var isBackupPasswordSheetPresented = false

    /// Non-nil identifier whenever any modal sheet or overlay is currently presented.
    public var activeSheet: String? {
        if isQuickFindPresented { return "quickFind" }
        if isQuickEntryPresented { return "quickEntry" }
        if previewingAttachment != nil { return "attachmentPreview" }
        if isGeminiSheetPresented { return "gemini" }
        if isWeeklyReviewPresented { return "weeklyReview" }
        if isTemplatesSheetPresented { return "templates" }
        if isSettingsPresented { return "settings" }
        if isNewProjectSheetPresented { return "newProject" }
        if editingProject != nil { return "editProject" }
        if pendingProjectDeletion != nil { return "confirmProjectDeletion" }
        if isNewAreaSheetPresented { return "newArea" }
        if editingArea != nil { return "editArea" }
        if isAreaManagerPresented { return "areaManager" }
        if isTagManagerPresented { return "tagManager" }
        if isBackupPasswordSheetPresented { return "backupPassword" }
        return nil
    }

    private var undoDismissTask: Task<Void, Never>?
    private var focusTimerCancellable: Timer?
    private var midnightTimer: Timer?
    private var syncDebounceTask: Task<Void, Never>?
    private var backgroundSyncTask: Task<Void, Never>?
    private var updateDownloadTask: Task<Void, Never>?
    private var backgroundUpdateCheckTask: Task<Void, Never>?
    private var installImmediatelyWhenStaged = false
    private var appActivationObserver: NSObjectProtocol?
    private var workspaceWakeObserver: NSObjectProtocol?
    private var appTerminateObserver: NSObjectProtocol?

    public init(databasePath: String? = nil) {
        let prefs = AppPreferences.load()
        self.preferences = prefs
        let db = (try? ThingsSQLiteDatabase(path: databasePath)) ?? (try! ThingsSQLiteDatabase(path: ":memory:"))
        self.database = db
        self.cloudSync = FirebaseCloudSyncService(database: db)

        try? db.seedIfEmpty(languageCode: prefs.language.resolvedCode)
        _ = try? db.performMidnightRollover()
        reloadSnapshot()

        if prefs.biometricLockEnabled {
            self.isLocked = true
        }

        db.onMutation = { [weak self] in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.reloadSnapshot()
                self.scheduleCloudSyncDebounced()
            }
        }

        db.onRemoteSyncApplied = { [weak self] in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.reloadSnapshot()
                NotificationReminderService.shared.syncAllTaskReminders(
                    openTasks: self.allOpenTasks,
                    lang: self.preferences.language
                )
            }
        }

        NotificationReminderService.shared.configureCategories(lang: prefs.language)
        NotificationReminderService.shared.onActionReceived = { [weak self] taskId, actionId in
            Task { @MainActor [weak self] in
                self?.handleNotificationAction(taskId: taskId, actionIdentifier: actionId)
            }
        }

        setupMidnightObserver()
        setupCloudSyncLifecycleObservers()
        setupBackgroundUpdateObserver()

        Task {
            await cloudSync.setObserver { [weak self] snap in
                Task { @MainActor [weak self] in
                    self?.syncSnapshot = snap
                }
            }
            await cloudSync.restoreSessionIfAvailable(localOnlyMode: prefs.localOnlyDbMode)
            await refreshCalendarEvents()
            if prefs.autoUpdateCheckEnabled {
                await checkForAppUpdatesSilently()
            }
        }
    }

    // MARK: - Localization Helpers

    public func tr(_ key: String) -> String {
        ThingsStrings.shared.text(key, lang: preferences.language)
    }

    public func trf(_ key: String, _ args: CustomStringConvertible...) -> String {
        let template = ThingsStrings.shared.text(key, lang: preferences.language)
        return ThingsStrings.formatPositional(template, args: args)
    }

    public func showToast(_ message: String) {
        toastMessage = message
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 3_200_000_000)
            if self.toastMessage == message {
                withAnimation { self.toastMessage = nil }
            }
        }
    }

    // MARK: - Snapshot & Computed Collections

    public func reloadSnapshot() {
        guard let snap = try? database.loadWorkspaceSnapshot() else { return }
        self.areas = snap.areas
        self.projects = snap.projects
        self.projectTemplates = snap.projectTemplates
        self.headingsByProject = snap.headingsByProject
        self.projectAttachments = snap.projectAttachments
        self.tags = snap.tags
        self.inboxTasks = snap.inboxTasks
        self.todayDaytimeTasks = snap.todayDaytimeTasks
        self.thisEveningTasks = snap.thisEveningTasks
        self.upcomingTasks = snap.upcomingTasks
        self.anytimeTasks = snap.anytimeTasks
        self.somedayTasks = snap.somedayTasks
        self.logbookTasks = snap.logbookTasks
        self.trashedTasks = snap.trashedTasks
        self.trashedProjects = snap.trashedProjects
        self.allOpenTasks = snap.allOpenTasks
        self.statistics = snap.statistics
        self.hasSampleTasks = snap.hasSampleTasks
        self.hasNonSampleUserTasks = snap.hasNonSampleUserTasks

        NotificationReminderService.shared.syncDailyDigest(
            preferences: preferences,
            todayTaskCount: snap.todayDaytimeTasks.count + snap.thisEveningTasks.count
        )
    }

    public var todayTotalBadgeCount: Int {
        todayDaytimeTasks.count + thisEveningTasks.count
    }

    public func filterTasksByActiveTag(_ list: [TaskItem]) -> [TaskItem] {
        guard let filterTagId = selectedFilterTagId else { return list }
        return list.filter { $0.tagIds.contains(filterTagId) }
    }

    public func tasksForProject(_ projectId: String) -> [TaskItem] {
        filterTasksByActiveTag(
            allOpenTasks.filter { $0.projectId == projectId }.sorted { $0.sortOrder < $1.sortOrder }
        )
    }

    public func completedTasksForProject(_ projectId: String) -> [TaskItem] {
        logbookTasks.filter { $0.projectId == projectId }
    }

    public func looseTasksForArea(_ areaId: String) -> [TaskItem] {
        filterTasksByActiveTag(
            allOpenTasks.filter { $0.areaId == areaId && $0.projectId == nil }.sorted { $0.sortOrder < $1.sortOrder }
        )
    }

    public func projectsForArea(_ areaId: String) -> [Project] {
        projects.filter { $0.areaId == areaId && $0.status == .active }.sorted { $0.sortOrder < $1.sortOrder }
    }

    public var standaloneActiveProjects: [Project] {
        projects.filter { $0.areaId == nil && $0.status == .active }.sorted { $0.sortOrder < $1.sortOrder }
    }

    public func project(byId id: String?) -> Project? {
        guard let id else { return nil }
        return projects.first { $0.id == id }
    }

    public func area(byId id: String?) -> Area? {
        guard let id else { return nil }
        return areas.first { $0.id == id }
    }

    // MARK: - v2.0 Weekly Review, Stalled Projects, Templates & Timeblocking

    public static let stalledReviewThresholdMillis: Int64 = 14 * 86_400_000

    public func projectStalledInfo(
        _ project: Project,
        nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)
    ) -> (isStalled: Bool, hasNoNextAction: Bool, isUnreviewed14Days: Bool) {
        guard project.status == .active, !project.isDeleted, project.scheduleState != .someday else {
            return (false, false, false)
        }
        let hasOpenNextAction = allOpenTasks.contains { $0.projectId == project.id }
        let hasNoNextAction = !hasOpenNextAction
        let reviewAnchor: Int64
        if let lr = project.lastReviewedAt, lr > 0 {
            reviewAnchor = lr
        } else {
            reviewAnchor = max(project.updatedAt, project.createdAt)
        }
        let isUnreviewed14Days = reviewAnchor <= 0 || (nowMillis - reviewAnchor) >= Self.stalledReviewThresholdMillis
        return (hasNoNextAction || isUnreviewed14Days, hasNoNextAction, isUnreviewed14Days)
    }

    public var stalledProjects: [Project] {
        let now = Int64(Date().timeIntervalSince1970 * 1000.0)
        return projects
            .filter { projectStalledInfo($0, nowMillis: now).isStalled }
            .sorted { $0.sortOrder < $1.sortOrder }
    }

    public var completedTasksLast7DaysCount: Int {
        let cutoff = Int64(Date().addingTimeInterval(-7 * 86_400).timeIntervalSince1970 * 1000.0)
        return logbookTasks.filter { ($0.completedAt ?? 0) >= cutoff && $0.status == .completed }.count
    }

    public func markProjectReviewed(_ projectId: String) {
        try? database.markProjectReviewed(projectId: projectId)
        triggerImmediateCloudSync()
    }

    @discardableResult
    public func addNextActionToProject(_ projectId: String, title: String) -> TaskItem? {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let proj = project(byId: projectId) else { return nil }
        let created = try? database.createTask(
            title: trimmed,
            scheduleState: .anytime,
            projectId: proj.id,
            areaId: proj.areaId
        )
        try? database.markProjectReviewed(projectId: proj.id)
        triggerImmediateCloudSync()
        return created
    }

    public func moveProjectToSomeday(_ project: Project) {
        var copy = project
        copy.scheduleState = .someday
        copy.whenDate = nil
        let now = Int64(Date().timeIntervalSince1970 * 1000.0)
        copy.lastReviewedAt = max(copy.lastReviewedAt ?? 0, now)
        try? database.updateProject(copy)
        triggerImmediateCloudSync()
    }

    public func completeWeeklyReview() {
        let activeIds = projects
            .filter { $0.status == .active && !$0.isDeleted }
            .map(\.id)
        try? database.markMultipleProjectsReviewed(activeIds)
        triggerImmediateCloudSync()
        isWeeklyReviewPresented = false
        showToast(tr("v2_weekly_review_done_toast"))
    }

    public func saveProjectAsTemplate(_ projectId: String) {
        if (try? database.saveProjectAsTemplate(projectId: projectId)) != nil {
            showToast(tr("v2_template_saved_toast"))
        }
    }

    @discardableResult
    public func createProjectFromTemplate(
        _ template: ProjectTemplate,
        titleOverride: String? = nil,
        areaIdOverride: String? = nil
    ) -> Project? {
        guard let created = try? database.instantiateProjectFromTemplate(
            template,
            titleOverride: titleOverride,
            areaIdOverride: areaIdOverride
        ) else {
            return nil
        }
        isTemplatesSheetPresented = false
        selectedDestination = .project(created.id)
        return created
    }

    public func deleteCustomTemplate(_ templateId: String) {
        try? database.deleteProjectTemplate(templateId)
    }

    @discardableResult
    public func importProjectFromOutline(
        _ rawInput: String,
        areaId: String? = nil
    ) -> Project? {
        let trimmed = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        guard let created = try? database.importProjectFromMarkdownOrJson(
            trimmed,
            defaultTitle: tr("entity_new_project"),
            areaId: areaId
        ) else {
            return nil
        }
        isTemplatesSheetPresented = false
        selectedDestination = .project(created.id)
        return created
    }

    public func setTaskTimeblock(
        _ taskId: String,
        startMinutes: Int?,
        durationMinutes: Int?
    ) {
        try? database.setTaskTimeblock(
            taskId: taskId,
            scheduledStartMinutes: startMinutes,
            durationMinutes: durationMinutes
        )
    }

    public func shiftTaskTimeblock(_ task: TaskItem, deltaMinutes: Int) {
        let currentStart = task.scheduledStartMinutes ?? (9 * 60)
        let nextStart = min(23 * 60 + 45, max(0, currentStart + deltaMinutes))
        let duration = task.durationMinutes ?? 30
        setTaskTimeblock(task.id, startMinutes: nextStart, durationMinutes: duration)
    }

    public func todayTimelineCalendarEvents() -> [CalendarEventItem] {
        guard preferences.calendarEventsEnabled else { return [] }
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        return calendarEvents
            .filter { todayEpochDay >= $0.startDateEpochDay && todayEpochDay <= $0.endDateInclusiveEpochDay }
            .sorted { $0.startMillis < $1.startMillis }
    }


    // MARK: - Context-Aware New To-Do Creation

    public func createNewTaskInCurrentContext(
        preferredHeadingId: String? = nil,
        evening: Bool = false
    ) {
        let todayEpoch = DateEpochConverter.startOfDayMillis(for: Date())
        let tomorrowDate = Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: Date())) ?? Date()
        let tomorrowEpoch = DateEpochConverter.startOfDayMillis(for: tomorrowDate)

        var sched: ScheduleState = .inbox
        var whenMillis: Int64? = nil
        var projId: String? = nil
        var areaId: String? = nil

        switch selectedDestination {
        case .smartView(let sv):
            switch sv {
            case .inbox:
                sched = .inbox
            case .today:
                sched = evening ? .thisEvening : .today
                whenMillis = todayEpoch
            case .upcoming:
                sched = .upcoming
                whenMillis = tomorrowEpoch
            case .anytime:
                sched = .anytime
            case .someday:
                sched = .someday
            case .logbook, .trash:
                sched = .inbox
                selectedDestination = .smartView(.inbox)
            }
        case .project(let pid):
            sched = .anytime
            projId = pid
            areaId = project(byId: pid)?.areaId
        case .area(let aid):
            sched = .anytime
            areaId = aid
        }

        var tagIds: [String] = []
        if let activeTag = selectedFilterTagId {
            tagIds.append(activeTag)
        }

        if let created = try? database.createTask(
            title: "",
            notes: "",
            scheduleState: sched,
            whenDate: whenMillis,
            projectId: projId,
            headingId: preferredHeadingId,
            areaId: areaId,
            tagIds: tagIds
        ) {
            withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                selectedTaskIds = [created.id]
                expandedTaskId = created.id
            }
        }
    }

    public func quickCaptureTask(
        title: String,
        notes: String,
        destination: QuickShareDestination,
        whenDate: Int64? = nil,
        deadline: Int64? = nil,
        tagIds: [String] = []
    ) {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty else { return }
        let todayEpoch = DateEpochConverter.startOfDayMillis(for: Date())

        var sched: ScheduleState = .inbox
        var resolvedWhen = whenDate
        var projId: String? = nil
        var areaId: String? = nil

        switch destination {
        case .inbox:
            sched = .inbox
        case .today:
            sched = .today
            resolvedWhen = resolvedWhen ?? todayEpoch
        case .thisEvening:
            sched = .thisEvening
            resolvedWhen = resolvedWhen ?? todayEpoch
        case .anytime:
            sched = .anytime
        case .someday:
            sched = .someday
        case .area(let aid):
            sched = .anytime
            areaId = aid
        case .project(let pid):
            sched = .anytime
            projId = pid
            areaId = project(byId: pid)?.areaId
        }

        if let task = try? database.createTask(
            title: cleanTitle,
            notes: notes,
            scheduleState: sched,
            whenDate: resolvedWhen,
            deadline: deadline,
            projectId: projId,
            areaId: areaId,
            tagIds: tagIds
        ) {
            NotificationReminderService.shared.syncTaskReminder(task, lang: preferences.language)
            showToast(tr("macos_quick_share_saved"))
        }
    }

    // MARK: - Task, Checklist, Project & Area Reordering / Actions

    public func toggleTaskCompletion(_ task: TaskItem) {
        let nextStatus: TaskStatus = (task.status == .completed) ? .open : .completed
        let spawnedId = (try? database.setTaskStatus(taskId: task.id, status: nextStatus)) ?? nil
        reloadSnapshot()
        if nextStatus == .completed {
            if focusTimer?.taskId == task.id {
                stopFocusTimer()
            }
            NotificationReminderService.shared.cancelTaskReminder(taskId: task.id)
            if let spawnedId, let spawnedTask = allOpenTasks.first(where: { $0.id == spawnedId }) {
                NotificationReminderService.shared.syncTaskReminder(spawnedTask, lang: preferences.language)
            }
            let displayTitle = task.title.isEmpty ? tr("macos_untitled_todo") : task.title
            presentUndo(
                message: trf("undo_task_completed", displayTitle),
                action: .taskCompleted(
                    taskId: task.id,
                    title: displayTitle,
                    spawnedRecurringTaskId: spawnedId
                )
            )
        } else if let reopened = allOpenTasks.first(where: { $0.id == task.id }) {
            NotificationReminderService.shared.syncTaskReminder(reopened, lang: preferences.language)
        }
    }

    public func cancelTask(_ task: TaskItem) {
        let nextStatus: TaskStatus = (task.status == .canceled) ? .open : .canceled
        _ = try? database.setTaskStatus(taskId: task.id, status: nextStatus)
        reloadSnapshot()
        if nextStatus == .canceled {
            if focusTimer?.taskId == task.id {
                stopFocusTimer()
            }
            NotificationReminderService.shared.cancelTaskReminder(taskId: task.id)
        } else if let reopened = allOpenTasks.first(where: { $0.id == task.id }) {
            NotificationReminderService.shared.syncTaskReminder(reopened, lang: preferences.language)
        }
    }

    public func updateTask(_ task: TaskItem, tagIds: [String]? = nil) {
        try? database.updateTask(task, tagIds: tagIds)
        NotificationReminderService.shared.syncTaskReminder(task, lang: preferences.language)
        if preferences.calendarEventsEnabled {
            Task { await refreshCalendarEvents() }
        }
    }

    public func moveChecklistItem(_ item: ChecklistItem, in items: [ChecklistItem], direction: Int) {
        guard let idx = items.firstIndex(where: { $0.id == item.id }) else { return }
        let targetIdx = idx + direction
        guard items.indices.contains(targetIdx) else { return }
        var a = items[idx]
        var b = items[targetIdx]
        let tmp = a.sortOrder
        a.sortOrder = (a.sortOrder == b.sortOrder) ? (b.sortOrder + Double(direction)) : b.sortOrder
        b.sortOrder = tmp
        try? database.updateChecklistItem(a)
        try? database.updateChecklistItem(b)
    }

    public func moveProjectOrder(_ project: Project, direction: Int) {
        let peers = project.areaId != nil ? projectsForArea(project.areaId!) : standaloneActiveProjects
        guard let idx = peers.firstIndex(where: { $0.id == project.id }) else { return }
        let targetIdx = idx + direction
        guard peers.indices.contains(targetIdx) else { return }
        var a = peers[idx]
        var b = peers[targetIdx]
        let tmp = a.sortOrder
        a.sortOrder = (a.sortOrder == b.sortOrder) ? (b.sortOrder + Double(direction)) : b.sortOrder
        b.sortOrder = tmp
        try? database.updateProject(a)
        try? database.updateProject(b)
    }

    public func moveAreaOrder(_ area: Area, direction: Int) {
        guard let idx = areas.firstIndex(where: { $0.id == area.id }) else { return }
        let targetIdx = idx + direction
        guard areas.indices.contains(targetIdx) else { return }
        var a = areas[idx]
        var b = areas[targetIdx]
        let tmp = a.sortOrder
        a.sortOrder = (a.sortOrder == b.sortOrder) ? (b.sortOrder + Double(direction)) : b.sortOrder
        b.sortOrder = tmp
        try? database.updateArea(a)
        try? database.updateArea(b)
    }

    public func deleteTask(_ task: TaskItem) {
        if focusTimer?.taskId == task.id {
            stopFocusTimer()
        }
        NotificationReminderService.shared.cancelTaskReminder(taskId: task.id)
        try? database.softDeleteTask(task.id)
        if expandedTaskId == task.id { expandedTaskId = nil }
        selectedTaskIds.remove(task.id)
        let displayTitle = task.title.isEmpty ? tr("macos_untitled_todo") : task.title
        presentUndo(
            message: trf("undo_task_deleted", displayTitle),
            action: .taskDeleted(taskId: task.id, title: displayTitle)
        )
    }

    public func deleteSelectedTasks() {
        let ids = selectedTaskIds
        guard !ids.isEmpty else {
            if let exp = expandedTaskId, let t = (allOpenTasks + logbookTasks).first(where: { $0.id == exp }) {
                deleteTask(t)
            }
            return
        }
        let candidates = allOpenTasks + logbookTasks
        var deletedTasks: [TaskItem] = []
        for id in ids {
            if let t = candidates.first(where: { $0.id == id }) {
                if focusTimer?.taskId == id {
                    stopFocusTimer()
                }
                NotificationReminderService.shared.cancelTaskReminder(taskId: id)
                try? database.softDeleteTask(id)
                deletedTasks.append(t)
            }
        }
        selectedTaskIds.removeAll()
        if let exp = expandedTaskId, ids.contains(exp) {
            expandedTaskId = nil
        }
        if deletedTasks.count == 1, let single = deletedTasks.first {
            let displayTitle = single.title.isEmpty ? tr("macos_untitled_todo") : single.title
            presentUndo(
                message: trf("undo_task_deleted", displayTitle),
                action: .taskDeleted(taskId: single.id, title: displayTitle)
            )
        } else if deletedTasks.count > 1 {
            presentUndo(
                message: trf("undo_task_deleted", "\(deletedTasks.count)"),
                action: .tasksDeleted(deletedTasks.map(\.id))
            )
        }
    }

    public func duplicateSelectedOrExpandedTask() {
        let targetId = expandedTaskId ?? selectedTaskIds.first
        guard let targetId else { return }
        if let copy = try? database.duplicateTask(targetId) {
            selectedTaskIds = [copy.id]
            expandedTaskId = copy.id
        }
    }

    public func applyTagShortcut(_ char: String) {
        let lower = char.lowercased()
        guard let matchedTag = tags.first(where: { $0.shortcut?.lowercased() == lower }) else { return }
        let targetIds = !selectedTaskIds.isEmpty ? Array(selectedTaskIds) : (expandedTaskId.map { [$0] } ?? [])
        for id in targetIds {
            if let task = allOpenTasks.first(where: { $0.id == id }) {
                var tids = Set(task.tagIds)
                if tids.contains(matchedTag.id) {
                    tids.remove(matchedTag.id)
                } else {
                    tids.insert(matchedTag.id)
                }
                updateTask(task, tagIds: Array(tids))
            }
        }
    }

    public func deleteProjectWithUndo(_ project: Project) {
        let count = (try? database.countNonDeletedTasksInProject(project.id)) ?? 0
        if count > 0 {
            pendingProjectDeletion = PendingProjectDeletion(project: project, taskCount: count)
        } else {
            confirmDeleteProject(project, deleteAssociatedTasks: true)
        }
    }

    public func confirmDeleteProject(_ project: Project, deleteAssociatedTasks: Bool) {
        pendingProjectDeletion = nil
        guard let detached = try? database.softDeleteProject(project.id, deleteAssociatedTasks: deleteAssociatedTasks) else { return }
        let cascadeDeletedTaskIds = deleteAssociatedTasks ? Array(detached.keys) : []
        if deleteAssociatedTasks && !cascadeDeletedTaskIds.isEmpty {
            if let ft = focusTimer, cascadeDeletedTaskIds.contains(ft.taskId) {
                stopFocusTimer()
            }
            NotificationReminderService.shared.cancelTaskReminders(taskIds: cascadeDeletedTaskIds)
            selectedTaskIds.subtract(cascadeDeletedTaskIds)
            if let exp = expandedTaskId, cascadeDeletedTaskIds.contains(exp) {
                expandedTaskId = nil
            }
        }
        if selectedDestination == .project(project.id) {
            selectedDestination = .smartView(.today)
        }
        presentUndo(
            message: trf("undo_project_deleted", project.title),
            action: .projectDeleted(
                projectId: project.id,
                title: project.title,
                previousTaskProjects: detached,
                deletedTaskIds: cascadeDeletedTaskIds
            )
        )
    }

    public func restoreTrashedTask(_ taskId: String) {
        try? database.restoreTask(taskId)
        reloadSnapshot()
        if let restored = allOpenTasks.first(where: { $0.id == taskId }) {
            NotificationReminderService.shared.syncTaskReminder(restored, lang: preferences.language)
        }
    }

    public func permanentlyDeleteTask(_ taskId: String) {
        NotificationReminderService.shared.cancelTaskReminder(taskId: taskId)
        try? database.permanentlyDeleteTask(taskId)
    }

    public func restoreTrashedProject(_ project: Project) {
        try? database.restoreProject(project.id)
        reloadSnapshot()
        NotificationReminderService.shared.syncAllTaskReminders(
            openTasks: allOpenTasks,
            lang: preferences.language
        )
    }

    public func permanentlyDeleteProject(_ project: Project) {
        let deletedChildIds = trashedTasks.filter { $0.projectId == project.id }.map(\.id)
        if !deletedChildIds.isEmpty {
            NotificationReminderService.shared.cancelTaskReminders(taskIds: deletedChildIds)
        }
        try? database.permanentlyDeleteProject(project.id)
    }

    public func emptyAllTrash() {
        NotificationReminderService.shared.cancelTaskReminders(taskIds: trashedTasks.map(\.id))
        try? database.emptyTrash()
    }

    private func presentUndo(message: String, action: UndoActionKind) {
        undoDismissTask?.cancel()
        let banner = UndoBannerState(message: message, action: action)
        withAnimation(.spring(response: 0.28, dampingFraction: 0.85)) {
            self.undoBanner = banner
        }
        undoDismissTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            guard let self, self.undoBanner?.id == banner.id else { return }
            withAnimation { self.undoBanner = nil }
        }
    }

    public func performUndo() {
        guard let current = undoBanner else { return }
        undoDismissTask?.cancel()
        withAnimation { undoBanner = nil }

        switch current.action {
        case .taskCompleted(let taskId, _, let spawnedId):
            if let spawnedId {
                NotificationReminderService.shared.cancelTaskReminder(taskId: spawnedId)
                try? database.softDeleteTask(spawnedId)
            }
            _ = try? database.setTaskStatus(taskId: taskId, status: .open)
            reloadSnapshot()
            if let reopened = allOpenTasks.first(where: { $0.id == taskId }) {
                NotificationReminderService.shared.syncTaskReminder(reopened, lang: preferences.language)
            }
        case .taskDeleted(let taskId, _):
            try? database.restoreTask(taskId)
            reloadSnapshot()
            if let restored = allOpenTasks.first(where: { $0.id == taskId }) {
                NotificationReminderService.shared.syncTaskReminder(restored, lang: preferences.language)
            }
        case .tasksDeleted(let taskIds):
            for id in taskIds {
                try? database.restoreTask(id)
            }
            reloadSnapshot()
            NotificationReminderService.shared.syncAllTaskReminders(
                openTasks: allOpenTasks,
                lang: preferences.language
            )
        case .projectDeleted(let projectId, _, let previousTasks, let deletedTaskIds):
            try? database.restoreDeletedProject(
                projectId: projectId,
                previousTasks: previousTasks,
                restoredDeletedTaskIds: deletedTaskIds
            )
            reloadSnapshot()
            NotificationReminderService.shared.syncAllTaskReminders(
                openTasks: allOpenTasks,
                lang: preferences.language
            )
        }
    }

    // MARK: - 25m Focus Pomodoro Timer

    public func startFocusTimer(for task: TaskItem) {
        focusTimerCancellable?.invalidate()
        let title = task.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? tr("macos_untitled_todo") : task.title
        let durationSeconds = max(1, task.durationMinutes ?? 25) * 60
        focusTimer = FocusTimerState(
            taskId: task.id,
            taskTitle: title,
            remainingSeconds: durationSeconds,
            totalSeconds: durationSeconds
        )
        focusTimerCancellable = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, var ft = self.focusTimer, !ft.isPaused else { return }
                if ft.remainingSeconds > 1 {
                    ft.remainingSeconds -= 1
                    self.focusTimer = ft
                } else {
                    ft.remainingSeconds = 0
                    self.focusTimer = ft
                    self.focusTimerCancellable?.invalidate()
                    NotificationReminderService.shared.postFocusTimerCompleted(taskTitle: ft.taskTitle, lang: self.preferences.language)
                }
            }
        }
    }

    public func toggleFocusTimerPause() {
        guard var ft = focusTimer else { return }
        ft.isPaused.toggle()
        focusTimer = ft
    }

    public func addFiveMinutesToFocusTimer() {
        guard var ft = focusTimer else { return }
        ft.remainingSeconds += 5 * 60
        ft.totalSeconds += 5 * 60
        focusTimer = ft
    }

    public func stopFocusTimer() {
        focusTimerCancellable?.invalidate()
        focusTimerCancellable = nil
        withAnimation { focusTimer = nil }
    }

    // MARK: - Cloud Sync & Calendar

    public func scheduleCloudSyncDebounced() {
        guard !preferences.localOnlyDbMode else { return }
        syncDebounceTask?.cancel()
        syncDebounceTask = Task {
            try? await Task.sleep(nanoseconds: 700_000_000)
            guard !Task.isCancelled else { return }
            await cloudSync.syncBidirectionalDeltas(forceFullPull: false, localOnlyMode: preferences.localOnlyDbMode)
        }
    }

    public func triggerImmediateCloudSync() {
        guard !preferences.localOnlyDbMode else { return }
        syncDebounceTask?.cancel()
        syncDebounceTask = Task {
            guard !Task.isCancelled else { return }
            await cloudSync.syncBidirectionalDeltas(forceFullPull: false, localOnlyMode: preferences.localOnlyDbMode)
        }
    }

    public func syncNow() {
        syncDebounceTask?.cancel()
        Task {
            await cloudSync.syncBidirectionalDeltas(forceFullPull: true, localOnlyMode: preferences.localOnlyDbMode)
        }
    }

    public func refreshCalendarEvents() async {
        guard preferences.calendarEventsEnabled else {
            self.calendarEvents = []
            return
        }
        if !AppleCalendarService.shared.authorizationGranted {
            let granted = await AppleCalendarService.shared.requestAccess()
            if !granted {
                self.calendarEvents = []
                return
            }
        }
        self.availableCalendars = AppleCalendarService.shared.listCalendars()
        let start = Calendar.current.startOfDay(for: Date())
        let end = Calendar.current.date(byAdding: .day, value: 90, to: start) ?? start.addingTimeInterval(90 * 86_400)
        self.calendarEvents = AppleCalendarService.shared.queryEvents(
            selectedCalendarIds: preferences.selectedCalendarIds,
            startDate: start,
            endDate: end
        )
    }

    /// Per Thingsbook Calendar Rules: only show calendar events on dates that have at least one scheduled task!
    public func calendarEventsForEpochDay(_ epochDay: Int, hasScheduledTasksOnDay: Bool) -> [CalendarEventItem] {
        guard preferences.calendarEventsEnabled, hasScheduledTasksOnDay else { return [] }
        return calendarEvents.filter { epochDay >= $0.startDateEpochDay && epochDay <= $0.endDateInclusiveEpochDay }
    }

    // MARK: - Backup Export / Import & CSV

    public func exportBackupInteractively(password: String?) {
        let panel = NSSavePanel()
        panel.title = tr("backup_create_button")
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        panel.nameFieldStringValue = "Thingsbook-Backup-\(df.string(from: Date())).tbbackup"
        panel.canCreateDirectories = true

        if panel.runModal() == .OK, let url = panel.url {
            do {
                let payload = try database.buildBackupPayload(preferences: preferences)
                let encoded = try ThingsBackupCodec.encodeBackup(payload: payload, password: password)
                try encoded.write(to: url, options: .atomic)
                let modeLabel = (password != nil && !(password?.isEmpty ?? true))
                    ? tr("backup_badge_encrypted")
                    : tr("backup_badge_unencrypted")
                showToast(trf(
                    "backup_status_saved",
                    modeLabel,
                    payload.tasks.count,
                    payload.projects.count,
                    payload.areas.count,
                    payload.tags.count
                ))
            } catch {
                showToast(trf("backup_status_failed", error.localizedDescription))
            }
        }
    }

    public func importBackupInteractively() {
        let panel = NSOpenPanel()
        panel.title = tr("backup_restore_button")
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false

        if panel.runModal() == .OK, let url = panel.url {
            importBackupFromURL(url)
        }
    }

    public func importBackupFromURL(_ url: URL) {
        guard let rawData = try? Data(contentsOf: url) else { return }
        do {
            let inspection = try ThingsBackupCodec.inspectBackup(rawBytes: rawData)
            switch inspection {
            case .passwordProtected:
                self.pendingEncryptedBackupData = rawData
                self.isBackupPasswordSheetPresented = true
            case .unencryptedPreview:
                let payload = try ThingsBackupCodec.decodeBackup(rawBytes: rawData, password: nil)
                applyRestoredBackupPayload(payload)
            }
        } catch {
            showToast(trf("backup_status_restore_failed", error.localizedDescription))
        }
    }

    public func unlockAndRestorePendingBackup(password: String) throws {
        guard let data = pendingEncryptedBackupData else { return }
        let payload = try ThingsBackupCodec.decodeBackup(rawBytes: data, password: password)
        pendingEncryptedBackupData = nil
        isBackupPasswordSheetPresented = false
        applyRestoredBackupPayload(payload)
    }

    private func applyRestoredBackupPayload(_ payload: ThingsbookBackupPayload) {
        do {
            try database.restoreBackupPayload(payload)
            var prefs = preferences
            if let sv = payload.preferences.sidebarVisible { prefs.sidebarVisible = sv }
            if let dd = payload.preferences.dailyDigestEnabled { prefs.dailyDigestEnabled = dd }
            if let dh = payload.preferences.dailyDigestHour { prefs.dailyDigestHour = dh }
            if let dm = payload.preferences.dailyDigestMinute { prefs.dailyDigestMinute = dm }
            if let gt = payload.preferences.groupTodayByProjectOrArea { prefs.groupTodayByProjectOrArea = gt }
            if let ib = payload.preferences.includeBetaUpdates { prefs.includeBetaUpdates = ib }
            if let tag = payload.preferences.languageTag {
                prefs.language = tag.isEmpty ? .system : (AppLanguage(rawValue: tag) ?? .system)
            }
            self.preferences = prefs
            reloadSnapshot()
            showToast(trf(
                "backup_status_restored",
                payload.tasks.count,
                payload.projects.count,
                payload.areas.count,
                payload.tags.count
            ))
        } catch {
            showToast(trf("backup_status_restore_failed", error.localizedDescription))
        }
    }

    public func exportCsvInteractively() {
        let panel = NSSavePanel()
        panel.title = tr("settings_csv_export")
        panel.nameFieldStringValue = "Thingsbook-Tasks.csv"
        panel.allowedContentTypes = [.commaSeparatedText]
        if panel.runModal() == .OK, let url = panel.url {
            if let csv = try? database.exportCsv() {
                try? Data(csv.utf8).write(to: url, options: .atomic)
                showToast(trf("csv_status_exported", allOpenTasks.count + logbookTasks.count))
            }
        }
    }

    public func importCsvInteractively() {
        let panel = NSOpenPanel()
        panel.title = tr("settings_csv_import")
        panel.allowedContentTypes = [.commaSeparatedText, .plainText]
        if panel.runModal() == .OK, let url = panel.url,
           let raw = try? String(contentsOf: url, encoding: .utf8) {
            let count = (try? database.importCsv(raw)) ?? 0
            showToast(trf("csv_status_imported", count))
        }
    }

    // MARK: - App Lock, Updates, Midnight & Notification Actions

    public func unlockWithBiometrics() {
        Task {
            let ok = await BiometricLockService.authenticate(reason: tr("macos_app_lock_subtitle"))
            if ok {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                    self.isLocked = false
                }
            }
        }
    }

    public func checkForAppUpdatesSilently() async {
        guard let info = try? await AppUpdateService.checkForUpdates(includeBeta: preferences.includeBetaUpdates) else {
            return
        }
        self.availableUpdate = info
        if preferences.autoUpdateCheckEnabled && Self.isRunningFromAppBundle {
            stageUpdateIfNeeded(info, installWhenReady: false)
        }
    }

    public func performManualUpdateCheck() async -> ReleaseUpdateInfo? {
        let found = try? await AppUpdateService.checkForUpdates(includeBeta: preferences.includeBetaUpdates)
        self.availableUpdate = found
        if let found {
            if preferences.autoUpdateCheckEnabled && Self.isRunningFromAppBundle {
                stageUpdateIfNeeded(found, installWhenReady: false)
            }
        } else {
            installImmediatelyWhenStaged = false
            updateDownloadTask?.cancel()
            updateDownloadTask = nil
            cleanupStagedUpdateDirectoryIfNeeded(except: nil)
            updatePhase = .idle
        }
        return found
    }

    public func installAvailableUpdateNow() {
        guard let info = availableUpdate else { return }

        if case .readyToInstall(let v, let stagedAppPath, let stagingRootPath) = updatePhase,
           v == info.versionString,
           FileManager.default.fileExists(atPath: stagedAppPath) {
            installImmediatelyWhenStaged = false
            updatePhase = .installingAndRelaunching(version: v)
            do {
                try AppUpdateService.installStagedUpdateAndRelaunch(
                    stagedAppURL: URL(fileURLWithPath: stagedAppPath),
                    stagingRootURL: URL(fileURLWithPath: stagingRootPath)
                )
            } catch {
                updatePhase = .failed(message: error.localizedDescription)
            }
            return
        }

        switch updatePhase {
        case .downloading(let v, _), .extracting(let v):
            if v == info.versionString {
                installImmediatelyWhenStaged = true
                return
            }
        case .installingAndRelaunching:
            return
        default:
            break
        }

        stageUpdateIfNeeded(info, installWhenReady: true)
    }

    private static var isRunningFromAppBundle: Bool {
        Bundle.main.bundleURL.pathExtension.lowercased() == "app"
    }

    private func stageUpdateIfNeeded(_ info: ReleaseUpdateInfo, installWhenReady: Bool) {
        if installWhenReady {
            installImmediatelyWhenStaged = true
        }

        if case .readyToInstall(let v, let stagedAppPath, _) = updatePhase,
           v == info.versionString,
           FileManager.default.fileExists(atPath: stagedAppPath) {
            if installImmediatelyWhenStaged {
                installImmediatelyWhenStaged = false
                installAvailableUpdateNow()
            }
            return
        }

        switch updatePhase {
        case .downloading(let v, _), .extracting(let v), .installingAndRelaunching(let v):
            if v == info.versionString {
                return
            }
        default:
            break
        }

        updateDownloadTask?.cancel()
        cleanupStagedUpdateDirectoryIfNeeded(except: nil)

        updateDownloadTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let (stagedAppURL, stagingRootURL) = try await AppUpdateService.downloadAndStageUpdate(info) { [weak self] phase in
                    Task { @MainActor [weak self] in
                        guard let self else { return }
                        self.updatePhase = phase
                    }
                }
                guard !Task.isCancelled else {
                    try? FileManager.default.removeItem(at: stagingRootURL)
                    return
                }
                self.updatePhase = .readyToInstall(
                    version: info.versionString,
                    stagedAppPath: stagedAppURL.path,
                    stagingRootPath: stagingRootURL.path
                )
                if self.installImmediatelyWhenStaged {
                    self.installImmediatelyWhenStaged = false
                    self.installAvailableUpdateNow()
                }
            } catch is CancellationError {
                return
            } catch {
                guard !Task.isCancelled else { return }
                self.installImmediatelyWhenStaged = false
                self.updatePhase = .failed(message: error.localizedDescription)
            }
        }
    }

    private func cleanupStagedUpdateDirectoryIfNeeded(except keepRootPath: String?) {
        if case .readyToInstall(_, _, let stagingRootPath) = updatePhase,
           stagingRootPath != keepRootPath {
            try? FileManager.default.removeItem(atPath: stagingRootPath)
        }
    }

    private func setupBackgroundUpdateObserver() {
        backgroundUpdateCheckTask?.cancel()
        backgroundUpdateCheckTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 4 * 3_600 * 1_000_000_000)
                guard !Task.isCancelled, let self else { break }
                let autoEnabled = await MainActor.run { self.preferences.autoUpdateCheckEnabled }
                guard autoEnabled else { continue }
                await self.checkForAppUpdatesSilently()
            }
        }
    }

    private func setupMidnightObserver() {
        NotificationCenter.default.addObserver(
            forName: .NSCalendarDayChanged,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                _ = try? self.database.performMidnightRollover()
                self.reloadSnapshot()
                await self.refreshCalendarEvents()
            }
        }

        midnightTimer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                if let count = try? self.database.performMidnightRollover(), count > 0 {
                    self.reloadSnapshot()
                }
            }
        }
    }

    private func setupCloudSyncLifecycleObservers() {
        backgroundSyncTask?.cancel()
        backgroundSyncTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                guard !Task.isCancelled, let self else { break }
                let localOnly = await MainActor.run { self.preferences.localOnlyDbMode }
                guard !localOnly else { continue }
                if await self.cloudSync.isAuthenticated {
                    await self.cloudSync.syncBidirectionalDeltas(forceFullPull: false, localOnlyMode: false)
                }
            }
        }

        appActivationObserver = NotificationCenter.default.addObserver(
            forName: NSApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, !self.preferences.localOnlyDbMode else { return }
                if await self.cloudSync.isAuthenticated {
                    await self.cloudSync.syncBidirectionalDeltas(forceFullPull: false, localOnlyMode: false)
                }
            }
        }

        workspaceWakeObserver = NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.didWakeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, !self.preferences.localOnlyDbMode else { return }
                if await self.cloudSync.isAuthenticated {
                    await self.cloudSync.syncBidirectionalDeltas(forceFullPull: true, localOnlyMode: false)
                }
            }
        }

        appTerminateObserver = NotificationCenter.default.addObserver(
            forName: NSApplication.willTerminateNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self, !self.preferences.localOnlyDbMode else { return }
            self.syncDebounceTask?.cancel()
            let sync = self.cloudSync
            let sem = DispatchSemaphore(value: 0)
            Task.detached(priority: .userInitiated) {
                await sync.pushDirtyOnlyIfSignedIn(localOnlyMode: false)
                sem.signal()
            }
            _ = sem.wait(timeout: .now() + .milliseconds(1200))
        }
    }

    private func handleNotificationAction(taskId: String, actionIdentifier: String) {
        guard var task = allOpenTasks.first(where: { $0.id == taskId }) else { return }
        switch actionIdentifier {
        case NotificationActionIdentifier.complete:
            toggleTaskCompletion(task)
        case NotificationActionIdentifier.snoozeOneHour:
            task.reminderTime = Int64(Date().addingTimeInterval(3600).timeIntervalSince1970 * 1000.0)
            updateTask(task)
        case NotificationActionIdentifier.moveToTomorrow:
            let tmr = Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: Date())) ?? Date()
            task.scheduleState = .upcoming
            task.whenDate = DateEpochConverter.startOfDayMillis(for: tmr)
            task.reminderTime = nil
            updateTask(task)
        default:
            selectedTaskIds = [task.id]
            expandedTaskId = task.id
        }
    }

    // MARK: - File & Image Attachments + Voice Smart Parse (v1.8)

    public func pickAndAttachFiles(taskId: String? = nil, projectId: String? = nil) {
        guard taskId != nil || projectId != nil else { return }
        let panel = NSOpenPanel()
        panel.title = tr("action_attach_file")
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = true
        panel.allowedContentTypes = [.image, .pdf, .item]
        if panel.runModal() == .OK {
            attachDroppedURLs(panel.urls, taskId: taskId, projectId: projectId)
        }
    }

    public func attachDroppedURLs(_ urls: [URL], taskId: String? = nil, projectId: String? = nil) {
        guard taskId != nil || projectId != nil else { return }
        for url in urls {
            do {
                let prepared = try MacAttachmentImporter.prepareFileForImport(url: url)
                _ = try database.addAttachment(
                    taskId: taskId,
                    projectId: projectId,
                    fileName: prepared.fileName,
                    mimeType: prepared.mimeType,
                    data: prepared.data
                )
            } catch MacAttachmentImportError.tooLarge {
                showToast(tr("attachment_too_large"))
            } catch {
                showToast(error.localizedDescription)
            }
        }
    }

    @discardableResult
    public func handleFileDropProviders(
        _ providers: [NSItemProvider],
        taskId: String? = nil,
        projectId: String? = nil
    ) -> Bool {
        guard !providers.isEmpty else { return false }
        var accepted = false
        for provider in providers where provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) {
            accepted = true
            provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier, options: nil) { [weak self] item, _ in
                let resolvedURL: URL? = {
                    if let url = item as? URL { return url }
                    if let data = item as? Data { return URL(dataRepresentation: data, relativeTo: nil) }
                    if let str = item as? String { return URL(string: str) }
                    return nil
                }()
                guard let fileURL = resolvedURL else { return }
                Task { @MainActor [weak self] in
                    self?.attachDroppedURLs([fileURL], taskId: taskId, projectId: projectId)
                }
            }
        }
        return accepted
    }

    public func removeAttachment(_ attachment: TaskAttachment) {
        if previewingAttachment?.id == attachment.id {
            previewingAttachment = nil
        }
        try? database.deleteAttachment(attachment.id)
    }

    @discardableResult
    public func smartParseVoiceDump(
        rawText: String,
        defaultDestination: QuickShareDestination
    ) async -> Int {
        let clean = rawText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return 0 }

        do {
            let drafts = try await ThingsbookGeminiService.parseBrainDumpToTasks(
                rawBrainDump: clean,
                lang: preferences.language
            )
            guard !drafts.isEmpty else { return 0 }

            let cal = Calendar.current
            let sodToday = cal.startOfDay(for: Date())
            var defaultProjId: String? = nil
            var defaultAreaId: String? = nil
            switch defaultDestination {
            case .project(let pid):
                defaultProjId = pid
                defaultAreaId = project(byId: pid)?.areaId
            case .area(let aid):
                defaultAreaId = aid
            default:
                break
            }

            var createdCount = 0
            for draft in drafts {
                let whenMillis: Int64? = draft.whenDaysOffset.map { offset in
                    let target = cal.date(byAdding: .day, value: max(0, offset), to: sodToday) ?? sodToday
                    return DateEpochConverter.startOfDayMillis(for: target, calendar: cal)
                }
                let deadlineMillis: Int64? = draft.deadlineDaysOffset.map { offset in
                    let target = cal.date(byAdding: .day, value: max(0, offset), to: sodToday) ?? sodToday
                    return DateEpochConverter.startOfDayMillis(for: target, calendar: cal)
                }

                var finalSched = draft.scheduleState
                if defaultProjId == nil && defaultAreaId == nil && finalSched == .inbox {
                    switch defaultDestination {
                    case .today: finalSched = .today
                    case .thisEvening: finalSched = .thisEvening
                    case .anytime: finalSched = .anytime
                    case .someday: finalSched = .someday
                    default: break
                    }
                }
                let finalWhen: Int64? = (finalSched == .today || finalSched == .thisEvening)
                    ? (whenMillis ?? DateEpochConverter.startOfDayMillis(for: sodToday, calendar: cal))
                    : whenMillis

                if let task = try? database.createTask(
                    title: draft.title,
                    notes: draft.notes,
                    scheduleState: finalSched,
                    whenDate: finalWhen,
                    deadline: deadlineMillis,
                    projectId: defaultProjId,
                    areaId: defaultAreaId,
                    checklistTitles: draft.checklist
                ) {
                    NotificationReminderService.shared.syncTaskReminder(task, lang: preferences.language)
                    createdCount += 1
                }
            }
            if createdCount > 0 {
                showToast(trf("macos_smart_parsed_toast", createdCount))
            }
            return createdCount
        } catch {
            showToast(error.localizedDescription)
            return 0
        }
    }

    // MARK: - URL Scheme (`thingsbook://`) Handler

    public func handleDeepLink(_ url: URL) {
        guard !isLocked else { return }
        guard url.scheme?.lowercased() == "thingsbook" else { return }
        let host = (url.host ?? "").lowercased()
        let comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let queryValue: (String) -> String? = { name in
            comps?.queryItems?.first(where: { $0.name == name })?.value
        }

        switch host {
        case "add", "quick-entry":
            quickEntryPrefillTitle = queryValue("title") ?? ""
            quickEntryPrefillNotes = queryValue("notes") ?? ""
            quickEntryAutoStartVoice = false
            isQuickEntryPresented = true
        case "voice", "voice-capture":
            quickEntryPrefillTitle = queryValue("title") ?? ""
            quickEntryPrefillNotes = queryValue("notes") ?? ""
            quickEntryAutoStartVoice = true
            isQuickEntryPresented = true
        case "today":
            selectedDestination = .smartView(.today)
        case "inbox":
            selectedDestination = .smartView(.inbox)
        case "upcoming":
            selectedDestination = .smartView(.upcoming)
        case "anytime":
            selectedDestination = .smartView(.anytime)
        case "someday":
            selectedDestination = .smartView(.someday)
        case "logbook":
            selectedDestination = .smartView(.logbook)
        case "search":
            isQuickFindPresented = true
        case "review", "weekly-review":
            isWeeklyReviewPresented = true
        case "templates":
            templatesInitialTab = .gallery
            isTemplatesSheetPresented = true
        default:
            break
        }
    }
}
