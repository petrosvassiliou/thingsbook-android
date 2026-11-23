import Foundation
import SwiftUI
import AppKit
import UniformTypeIdentifiers
import ThingsbookCore

public enum GeminiSheetTab: String, CaseIterable, Identifiable {
    case brainDump
    case projectArchitect
    case logbookReview
    case planMyDay

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
    case externalImportExport = 2

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
            if oldValue.viewSortMode != preferences.viewSortMode {
                applyViewSortToLoadedTasks()
            }
        }
    }
    @Published public var selectedDestination: NavigationDestination = .smartView(.today) {
        didSet {
            selectedFilterTagId = nil
            selectedTaskIds.removeAll()
            lastSelectedTaskId = nil
            expandedTaskId = nil
            if isTimelineFullScreen {
                isTimelineFullScreen = false
            }
        }
    }
    @Published public var areas: [Area] = []
    @Published public var projects: [Project] = []
    @Published public var projectTemplates: [ProjectTemplate] = []
    @Published public var headingsByProject: [String: [Heading]] = [:]
    @Published public var projectAttachments: [String: [TaskAttachment]] = [:]
    @Published public var tags: [Tag] = []
    @Published public var todayViewMode: TodayViewMode = .list {
        didSet {
            if todayViewMode != .timeline, selectedDestination == .smartView(.today), isTimelineFullScreen {
                isTimelineFullScreen = false
            }
        }
    }
    @Published public var isTimelineFullScreen: Bool = false {
        didSet {
            if isTimelineFullScreen {
                expandedTaskId = nil
                selectedTaskIds.removeAll()
                lastSelectedTaskId = nil
            }
        }
    }
    public static let inspectorVisibleDefaultsKey = "thingsbook.macos.inspectorVisible"
    @Published public var isInspectorVisible: Bool = (UserDefaults.standard.object(forKey: "thingsbook.macos.inspectorVisible") as? Bool) ?? true {
        didSet {
            UserDefaults.standard.set(isInspectorVisible, forKey: Self.inspectorVisibleDefaultsKey)
        }
    }

    public func toggleInspector() {
        withAnimation(.spring(response: 0.25, dampingFraction: 0.86)) {
            isInspectorVisible.toggle()
        }
    }

    public static let showPerspectivesDefaultsKey = "thingsbook.macos.showPerspectives"
    @Published public var showPerspectivesInSidebar: Bool {
        didSet {
            UserDefaults.standard.set(showPerspectivesInSidebar, forKey: Self.showPerspectivesDefaultsKey)
        }
    }

    public func setShowPerspectivesInSidebar(_ visible: Bool) {
        showPerspectivesInSidebar = visible
        UserDefaults.standard.set(visible, forKey: Self.showPerspectivesDefaultsKey)
        if !visible, case .perspective = selectedDestination {
            selectedDestination = .smartView(.today)
        }
    }

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

    // MARK: - Selection, Filtering & Magic Plus Drag-to-Insert
    @Published public var selectedFilterTagId: String? = nil
    @Published public var selectedTaskIds: Set<String> = []
    @Published public var lastSelectedTaskId: String? = nil
    @Published public var expandedTaskId: String? = nil
    @Published public var collapsedHeadingIds: Set<String> = []
    @Published public var magicPlusActiveDrop: MacMagicPlusResolvedDrop? = nil
    @Published public var newlyCreatedHeadingId: String? = nil
    public var registeredMagicPlusSlots: [MacMagicPlusSlotGeometry] = []
    public var flushOpenTaskModalDrafts: (() -> Void)? = nil
    public var flushOpenTaskModalToken: UUID? = nil
    private var isFlushingModalDrafts = false

    public func flushModalDraftsIfNeeded() {
        guard !isFlushingModalDrafts else { return }
        isFlushingModalDrafts = true
        defer { isFlushingModalDrafts = false }
        flushOpenTaskModalDrafts?()
    }

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
    @Published public var pendingProjectCompletion: PendingProjectCompletion? = nil
    @Published public var isNewAreaSheetPresented = false
    @Published public var editingArea: Area? = nil
    @Published public var isAreaManagerPresented = false
    @Published public var isTagManagerPresented = false
    @Published public var isPerspectiveEditorPresented = false
    @Published public var editingPerspective: SavedPerspective? = nil

    // MARK: - Undo, Focus Timer, Sync, Calendar, Lock & Updates
    @Published public var undoBanner: UndoBannerState? = nil
    @Published public var focusTimer: FocusTimerState? = nil
    @Published public var syncSnapshot = CloudSyncSnapshot()
    @Published public var calendarEvents: [CalendarEventItem] = []
    @Published public private(set) var calendarNowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)
    private var lastObservedEpochDay: Int = DateEpochConverter.epochDay(for: Date())
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
        if pendingProjectCompletion != nil { return "confirmProjectCompletion" }
        if isNewAreaSheetPresented { return "newArea" }
        if editingArea != nil { return "editArea" }
        if isAreaManagerPresented { return "areaManager" }
        if isTagManagerPresented { return "tagManager" }
        if isPerspectiveEditorPresented { return "perspectiveEditor" }
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
        if let explicitShowPerspectives = UserDefaults.standard.object(forKey: Self.showPerspectivesDefaultsKey) as? Bool {
            self.showPerspectivesInSidebar = explicitShowPerspectives
        } else {
            self.showPerspectivesInSidebar = !prefs.savedPerspectives.isEmpty
        }
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
        let sortMode = preferences.viewSortMode
        self.areas = snap.areas
        self.projects = snap.projects
        self.projectTemplates = snap.projectTemplates
        self.headingsByProject = snap.headingsByProject
        self.projectAttachments = snap.projectAttachments
        self.tags = snap.tags
        self.inboxTasks = TaskViewSorter.sort(snap.inboxTasks, mode: sortMode)
        self.todayDaytimeTasks = TaskViewSorter.sort(snap.todayDaytimeTasks, mode: sortMode)
        self.thisEveningTasks = TaskViewSorter.sort(snap.thisEveningTasks, mode: sortMode)
        self.upcomingTasks = sortUpcomingTasksByDayBucket(snap.upcomingTasks, mode: sortMode)
        self.anytimeTasks = TaskViewSorter.sort(snap.anytimeTasks, mode: sortMode)
        self.somedayTasks = TaskViewSorter.sort(snap.somedayTasks, mode: sortMode)
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

    public func applyViewSortToLoadedTasks() {
        let sortMode = preferences.viewSortMode
        inboxTasks = TaskViewSorter.sort(inboxTasks, mode: sortMode)
        todayDaytimeTasks = TaskViewSorter.sort(todayDaytimeTasks, mode: sortMode)
        thisEveningTasks = TaskViewSorter.sort(thisEveningTasks, mode: sortMode)
        upcomingTasks = sortUpcomingTasksByDayBucket(upcomingTasks, mode: sortMode)
        anytimeTasks = TaskViewSorter.sort(anytimeTasks, mode: sortMode)
        somedayTasks = TaskViewSorter.sort(somedayTasks, mode: sortMode)
    }

    private func sortUpcomingTasksByDayBucket(_ list: [TaskItem], mode: String) -> [TaskItem] {
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        let grouped = Dictionary(grouping: list) { task -> Int in
            guard let w = task.whenDate ?? (task.isMilestone ? task.deadline : nil) else { return Int.max }
            return max(todayEpochDay + 1, DateEpochConverter.epochDay(forMillis: w))
        }
        return grouped.keys.sorted().flatMap { day in
            TaskViewSorter.sort(grouped[day] ?? [], mode: mode)
        }
    }

    public func deleteSampleTasks() {
        let shouldTombstoneInCloud = syncSnapshot.userId != nil && !preferences.localOnlyDbMode
        try? database.purgeSampleDataLocally(
            preserveUsedContainers: true,
            emitCloudTombstones: shouldTombstoneInCloud
        )
        preferences.hideSampleSetupBanner = true
        reloadSnapshot()
        if shouldTombstoneInCloud {
            scheduleCloudSyncDebounced()
        }
    }

    public var todayTotalBadgeCount: Int {
        todayDaytimeTasks.count + thisEveningTasks.count
    }

    public var openTaskIds: Set<String> {
        Set(allOpenTasks.map(\.id))
    }

    public func isTaskActivelyBlocked(_ task: TaskItem) -> Bool {
        TaskDependencyGraph.isActivelyBlocked(task, openTaskIds: openTaskIds)
    }

    public func activeBlockers(for task: TaskItem) -> [TaskItem] {
        let ids = TaskDependencyGraph.activeBlockerIds(for: task, openTaskIds: openTaskIds)
        guard !ids.isEmpty else { return [] }
        let byId = Dictionary(uniqueKeysWithValues: allOpenTasks.map { ($0.id, $0) })
        return ids.compactMap { byId[$0] }
    }

    public func blockedByBadgeText(for task: TaskItem) -> String? {
        let blockers = activeBlockers(for: task)
        guard !blockers.isEmpty else { return nil }
        if blockers.count == 1 {
            let trimmed = blockers[0].title.trimmingCharacters(in: .whitespacesAndNewlines)
            let label = trimmed.isEmpty ? tr("macos_untitled_todo") : trimmed
            return trf("v30_blocked_by_badge", label)
        }
        return trf("v30_blocked_by_count_badge", blockers.count)
    }

    public func todayCapacitySummary() -> (plannedMinutes: Int, freeMinutes: Int, pillText: String)? {
        var seen = Set<String>()
        let todayTasks = (todayDaytimeTasks + thisEveningTasks).filter { seen.insert($0.id).inserted }
        let cal = Calendar.current
        let sod = cal.startOfDay(for: Date())
        let nextSod = cal.date(byAdding: .day, value: 1, to: sod) ?? sod.addingTimeInterval(86_400)
        let dayStartMs = DateEpochConverter.startOfDayMillis(for: sod, calendar: cal)
        let dayEndMs = DateEpochConverter.startOfDayMillis(for: nextSod, calendar: cal)

        let cap = TodayCapacityCalculator.compute(
            tasks: todayTasks,
            calendarEvents: todayTimelineCalendarEvents(),
            dayStartMillis: dayStartMs,
            dayEndMillis: dayEndMs,
            workdayStartMinutes: preferences.workdayStartMinutes,
            workdayEndMinutes: preferences.workdayEndMinutes
        )
        guard cap.plannedMinutes > 0 else { return nil }
        let plannedStr = TodayCapacityCalculator.formatMinutes(cap.plannedMinutes)
        let pillText: String
        if preferences.calendarEventsEnabled {
            pillText = trf(
                "v30_today_capacity_summary",
                plannedStr,
                TodayCapacityCalculator.formatMinutes(cap.freeMinutes)
            )
        } else {
            pillText = trf("v30_today_planned_only", plannedStr)
        }
        return (cap.plannedMinutes, cap.freeMinutes, pillText)
    }

    public func setWorkdayBounds(startMinutes: Int, endMinutes: Int) {
        let normalized = TodayCapacityCalculator.normalizeWorkdayBounds(
            startMinutes: startMinutes,
            endMinutes: endMinutes
        )
        var nextPrefs = preferences
        nextPrefs.workdayStartMinutes = normalized.startMinutes
        nextPrefs.workdayEndMinutes = normalized.endMinutes
        preferences = nextPrefs
    }

    public func sevenDayWorkloadForecast() -> [DayWorkloadSummary] {
        var seen = Set<String>()
        let todayTasks = (todayDaytimeTasks + thisEveningTasks).filter { seen.insert($0.id).inserted }
        let activeEvents = preferences.calendarEventsEnabled ? calendarEvents : []
        let todayMs = DateEpochConverter.startOfDayMillis(for: Date())
        return TodayCapacityCalculator.computeSevenDayForecast(
            todayTasks: todayTasks,
            allOpenTasks: allOpenTasks,
            calendarEvents: activeEvents,
            todayMillis: todayMs,
            daysCount: 7,
            workdayStartMinutes: preferences.workdayStartMinutes,
            workdayEndMinutes: preferences.workdayEndMinutes
        )
    }

    public var tagUsageCounts: [String: Int] {
        var counts: [String: Int] = [:]
        for task in allOpenTasks {
            for tid in Set(task.tagIds) {
                counts[tid, default: 0] += 1
            }
        }
        for proj in projects where !proj.isDeleted && proj.status == .active {
            for tid in Set(proj.tagIds) {
                counts[tid, default: 0] += 1
            }
        }
        for area in areas where !area.isDeleted {
            for tid in Set(area.tagIds) {
                counts[tid, default: 0] += 1
            }
        }
        for tmpl in projectTemplates where !tmpl.isDeleted {
            for tid in Set(tmpl.tagIds) {
                counts[tid, default: 0] += 1
            }
        }
        return counts
    }

    public var sortedTags: [Tag] {
        TagListSorter.sort(tags, mode: preferences.tagSortMode, usageCounts: tagUsageCounts)
    }

    public var sortedPerspectives: [SavedPerspective] {
        preferences.savedPerspectives.sorted {
            if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
            return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
        }
    }

    public func perspective(byId id: String?) -> SavedPerspective? {
        guard let id else { return nil }
        return preferences.savedPerspectives.first { $0.id == id }
    }

    public func tasksForPerspective(_ perspective: SavedPerspective) -> [TaskItem] {
        let projectsById = Dictionary(uniqueKeysWithValues: projects.map { ($0.id, $0) })
        let todayMs = DateEpochConverter.startOfDayMillis(for: Date())
        let matched = PerspectiveFilterEngine.filter(
            tasks: allOpenTasks,
            perspective: perspective,
            openTaskIds: openTaskIds,
            todayEpochMillis: todayMs,
            projectsById: projectsById
        )
        return filterTasksByActiveTag(TaskViewSorter.sort(matched, mode: preferences.viewSortMode))
    }

    public func savePerspective(_ perspective: SavedPerspective) {
        var copy = perspective
        let cleanTitle = copy.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty else { return }
        copy.title = cleanTitle
        var next = preferences.savedPerspectives
        if let idx = next.firstIndex(where: { $0.id == copy.id }) {
            next[idx] = copy
        } else {
            let maxOrd = next.map(\.sortOrder).max() ?? 0.0
            copy.sortOrder = maxOrd + 100.0
            next.append(copy)
        }
        preferences.savedPerspectives = next
        showPerspectivesInSidebar = true
        editingPerspective = nil
        isPerspectiveEditorPresented = false
        selectedDestination = .perspective(copy.id)
    }

    public func deletePerspective(_ perspectiveId: String) {
        preferences.savedPerspectives.removeAll { $0.id == perspectiveId }
        if selectedDestination == .perspective(perspectiveId) {
            selectedDestination = .smartView(.today)
        }
        if editingPerspective?.id == perspectiveId {
            editingPerspective = nil
            isPerspectiveEditorPresented = false
        }
    }

    public func filterTasksByActiveTag(_ list: [TaskItem]) -> [TaskItem] {
        guard let filterTagId = selectedFilterTagId else { return list }
        return list.filter { $0.tagIds.contains(filterTagId) }
    }

    public func tasksForProject(_ projectId: String) -> [TaskItem] {
        filterTasksByActiveTag(
            TaskViewSorter.sort(
                allOpenTasks.filter { $0.projectId == projectId },
                mode: preferences.viewSortMode
            )
        )
    }

    public func completedTasksForProject(_ projectId: String) -> [TaskItem] {
        logbookTasks.filter { $0.projectId == projectId }
    }

    public func looseTasksForArea(_ areaId: String) -> [TaskItem] {
        filterTasksByActiveTag(
            TaskViewSorter.sort(
                allOpenTasks.filter { $0.areaId == areaId && $0.projectId == nil },
                mode: preferences.viewSortMode
            )
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

    public func task(byId id: String) -> TaskItem? {
        if let open = allOpenTasks.first(where: { $0.id == id && !$0.isDeleted }) {
            return open
        }
        return logbookTasks.first(where: { $0.id == id && !$0.isDeleted })
    }

    public func taskIncludingTrash(byId id: String) -> TaskItem? {
        if let open = allOpenTasks.first(where: { $0.id == id }) {
            return open
        }
        if let logged = logbookTasks.first(where: { $0.id == id }) {
            return logged
        }
        return trashedTasks.first(where: { $0.id == id })
    }

    private func containerGroupKey(for task: TaskItem) -> String {
        if let pid = task.projectId, let proj = project(byId: pid) {
            if let aid = proj.areaId, let area = area(byId: aid) {
                return "\(area.title) › \(proj.title)"
            }
            return proj.title
        }
        if let aid = task.areaId, let area = area(byId: aid) {
            return area.title
        }
        return tr("macos_today_unassigned")
    }

    private func containerGroupedTasks(_ tasks: [TaskItem]) -> [TaskItem] {
        let grouped = Dictionary(grouping: tasks, by: containerGroupKey(for:))
        return grouped.keys.sorted().flatMap { key in
            TaskViewSorter.sort(grouped[key] ?? [], mode: preferences.viewSortMode)
        }
    }

    public func currentVisibleTaskIdsInOrder() -> [String] {
        switch selectedDestination {
        case .smartView(let sv):
            switch sv {
            case .inbox:
                return filterTasksByActiveTag(inboxTasks).map(\.id)
            case .today:
                let daytime = filterTasksByActiveTag(todayDaytimeTasks)
                let evening = filterTasksByActiveTag(thisEveningTasks)
                if todayViewMode == .list && preferences.groupTodayByProjectOrArea {
                    return (containerGroupedTasks(daytime) + evening).map(\.id)
                }
                return (daytime + evening).map(\.id)
            case .upcoming:
                return filterTasksByActiveTag(upcomingTasks).map(\.id)
            case .anytime:
                return containerGroupedTasks(filterTasksByActiveTag(anytimeTasks)).map(\.id)
            case .someday:
                return filterTasksByActiveTag(somedayTasks).map(\.id)
            case .logbook:
                return filterTasksByActiveTag(logbookTasks).map(\.id)
            case .trash:
                return trashedTasks.map(\.id)
            }
        case .project(let pid):
            let open = tasksForProject(pid)
            let headings = (headingsByProject[pid] ?? []).filter { !$0.isDeleted && !$0.isArchived }
            let validHeadingIds = Set(headings.map(\.id))
            let mode = ProjectViewMode.normalized(project(byId: pid)?.viewMode)
            let unheaded = TaskViewSorter.sort(
                open.filter { task in
                    guard let hid = task.headingId else { return true }
                    return !validHeadingIds.contains(hid)
                },
                mode: preferences.viewSortMode
            )
            switch mode {
            case .list:
                var ordered = unheaded.map(\.id)
                for heading in headings where !collapsedHeadingIds.contains(heading.id) {
                    let sec = TaskViewSorter.sort(open.filter { $0.headingId == heading.id }, mode: preferences.viewSortMode)
                    ordered.append(contentsOf: sec.map(\.id))
                }
                return ordered
            case .board:
                var ordered = unheaded.map(\.id)
                for heading in headings {
                    let sec = TaskViewSorter.sort(open.filter { $0.headingId == heading.id }, mode: preferences.viewSortMode)
                    ordered.append(contentsOf: sec.map(\.id))
                }
                return ordered
            case .roadmap:
                let proj = project(byId: pid) ?? Project(id: pid, title: "", createdAt: 0, updatedAt: 0)
                let layout = ProjectRoadmapEngine.buildLayout(
                    project: proj,
                    headings: headings,
                    tasks: open,
                    todayMillis: DateEpochConverter.startOfDayMillis(for: Date())
                )
                var ordered = layout.allTaskBars.map(\.id)
                let plottedSet = Set(ordered)
                for unsched in layout.unscheduledTasks where !plottedSet.contains(unsched.id) {
                    ordered.append(unsched.id)
                }
                return ordered
            }
        case .area(let aid):
            return looseTasksForArea(aid).map(\.id)
        case .perspective(let pid):
            guard let p = perspective(byId: pid) else { return [] }
            return tasksForPerspective(p).map(\.id)
        }
    }

    public func selectAllVisibleTasks() {
        let visible = currentVisibleTaskIdsInOrder()
        guard !visible.isEmpty else { return }
        withAnimation(.spring(response: 0.24, dampingFraction: 0.86)) {
            selectedTaskIds = Set(visible)
            lastSelectedTaskId = visible.last
            if visible.count > 1 {
                expandedTaskId = nil
            }
        }
    }

    public func dragPayload(for taskId: String) -> String {
        if selectedTaskIds.count <= 1 || !selectedTaskIds.contains(taskId) {
            return TaskDragPayloadCodec.encode(
                primaryTaskId: taskId,
                selectedSet: [],
                orderedSelectedIds: []
            )
        }
        return TaskDragPayloadCodec.encode(
            primaryTaskId: taskId,
            selectedSet: selectedTaskIds,
            orderedSelectedIds: orderedTaskIds(matching: selectedTaskIds)
        )
    }

    public static func decodeDragPayloads(_ payloads: [String]) -> [String] {
        TaskDragPayloadCodec.decode(payloads)
    }

    public func orderedTaskIds(matching set: Set<String>) -> [String] {
        guard !set.isEmpty else { return [] }
        let visible = currentVisibleTaskIdsInOrder().filter { set.contains($0) }
        if visible.count == set.count {
            return visible
        }
        let seen = Set(visible)
        let remainder = set.subtracting(seen).sorted()
        return visible + remainder
    }

    public func handleRowClick(taskId: String, modifiers: NSEvent.ModifierFlags = NSEvent.modifierFlags) {
        guard !isTimelineFullScreen else { return }
        let isShift = modifiers.contains(.shift)
        let isCmd = modifiers.contains(.command)

        if isShift {
            let visible = currentVisibleTaskIdsInOrder()
            let anchorId = lastSelectedTaskId ?? expandedTaskId ?? selectedTaskIds.first
            if let anchorId,
               let aIdx = visible.firstIndex(of: anchorId),
               let bIdx = visible.firstIndex(of: taskId) {
                let low = min(aIdx, bIdx)
                let high = max(aIdx, bIdx)
                let rangeIds = Set(visible[low...high])
                if isCmd {
                    selectedTaskIds.formUnion(rangeIds)
                } else {
                    selectedTaskIds = rangeIds
                }
                if selectedTaskIds.count > 1 {
                    expandedTaskId = nil
                }
                if lastSelectedTaskId == nil {
                    lastSelectedTaskId = anchorId
                }
                return
            }
        }

        if isCmd {
            if selectedTaskIds.contains(taskId) {
                selectedTaskIds.remove(taskId)
            } else {
                selectedTaskIds.insert(taskId)
                lastSelectedTaskId = taskId
            }
            if selectedTaskIds.count > 1 {
                expandedTaskId = nil
            }
            return
        }

        if selectedTaskIds != [taskId] {
            selectedTaskIds = [taskId]
        }
        if lastSelectedTaskId != taskId {
            lastSelectedTaskId = taskId
        }
    }

    public func openTaskModal(_ taskId: String) {
        guard !isTimelineFullScreen, task(byId: taskId) != nil else { return }
        withAnimation(.spring(response: 0.26, dampingFraction: 0.86)) {
            selectedTaskIds = [taskId]
            lastSelectedTaskId = taskId
            expandedTaskId = taskId
        }
    }

    public func clearSelection() {
        flushModalDraftsIfNeeded()
        withAnimation(.spring(response: 0.22, dampingFraction: 0.86)) {
            selectedTaskIds.removeAll()
            lastSelectedTaskId = nil
        }
    }

    // MARK: - v2.0 Weekly Review, Stalled Projects, Templates & Timeblocking

    public static let stalledNoNextActionGraceMillis: Int64 = 86_400_000
    public static let stalledReviewThresholdMillis: Int64 = 14 * 86_400_000

    public func projectStalledInfo(
        _ project: Project,
        nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000.0)
    ) -> (isStalled: Bool, hasNoNextAction: Bool, isUnreviewed14Days: Bool) {
        guard project.status == .active, !project.isDeleted, project.scheduleState != .someday else {
            return (false, false, false)
        }
        var hasOpenNextAction = false
        var latestTaskActivityAt: Int64 = 0
        for t in allOpenTasks where t.projectId == project.id {
            hasOpenNextAction = true
            latestTaskActivityAt = max(latestTaskActivityAt, t.createdAt, t.updatedAt, t.completedAt ?? 0)
        }
        for t in logbookTasks where t.projectId == project.id {
            latestTaskActivityAt = max(latestTaskActivityAt, t.createdAt, t.updatedAt, t.completedAt ?? 0)
        }

        let activityAnchor = max(
            project.createdAt,
            project.updatedAt,
            project.lastReviewedAt ?? 0,
            latestTaskActivityAt
        )
        if !hasOpenNextAction &&
            activityAnchor > 0 &&
            (nowMillis - activityAnchor) < Self.stalledNoNextActionGraceMillis {
            return (false, false, false)
        }
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
        flushModalDraftsIfNeeded()
        try? database.setTaskTimeblock(
            taskId: taskId,
            scheduledStartMinutes: startMinutes,
            durationMinutes: durationMinutes
        )
    }

    public func shiftTaskTimeblock(_ task: TaskItem, deltaMinutes: Int) {
        flushModalDraftsIfNeeded()
        let fresh = self.taskIncludingTrash(byId: task.id) ?? task
        let currentStart = fresh.scheduledStartMinutes ?? (9 * 60)
        let nextStart = min(23 * 60 + 45, max(0, currentStart + deltaMinutes))
        let duration = fresh.durationMinutes ?? 30
        setTaskTimeblock(fresh.id, startMinutes: nextStart, durationMinutes: duration)
    }

    public func todayTimelineCalendarEvents() -> [CalendarEventItem] {
        guard preferences.calendarEventsEnabled else { return [] }
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        let nowMs = max(calendarNowMillis, Int64(Date().timeIntervalSince1970 * 1000.0))
        return CalendarEventItem.filterActiveOrUpcoming(
            calendarEvents,
            forEpochDay: todayEpochDay,
            todayEpochDay: todayEpochDay,
            nowMillis: nowMs,
            upcomingOnly: false
        ).sorted { $0.startMillis < $1.startMillis }
    }


    // MARK: - Context-Aware New To-Do Creation

    public func createNewTaskInCurrentContext(
        preferredHeadingId: String? = nil,
        evening: Bool = false,
        explicitSortOrder: Double? = nil,
        epochDayOverride: Int? = nil
    ) {
        let todayEpoch = DateEpochConverter.startOfDayMillis(for: Date())
        let tomorrowDate = Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: Date())) ?? Date()
        let tomorrowEpoch = DateEpochConverter.startOfDayMillis(for: tomorrowDate)

        var sched: ScheduleState = .inbox
        var whenMillis: Int64? = nil
        var projId: String? = nil
        var areaId: String? = nil
        var firstCurrentOrder: Double? = nil
        var perspectiveTagIds: [String] = []
        var perspectiveEffort: String? = nil
        var perspectiveMaxDuration: Int? = nil

        switch selectedDestination {
        case .smartView(let sv):
            switch sv {
            case .inbox:
                sched = .inbox
                firstCurrentOrder = inboxTasks.map(\.sortOrder).min()
            case .today:
                sched = evening ? .thisEvening : .today
                whenMillis = todayEpoch
                firstCurrentOrder = (evening ? thisEveningTasks : todayDaytimeTasks).map(\.sortOrder).min()
            case .upcoming:
                sched = .upcoming
                if let epochDayOverride {
                    let targetDate = DateEpochConverter.date(fromEpochDay: epochDayOverride)
                    whenMillis = DateEpochConverter.startOfDayMillis(for: targetDate)
                } else {
                    whenMillis = tomorrowEpoch
                }
                firstCurrentOrder = upcomingTasks.map(\.sortOrder).min()
            case .anytime:
                sched = .anytime
                firstCurrentOrder = anytimeTasks.map(\.sortOrder).min()
            case .someday:
                sched = .someday
                firstCurrentOrder = somedayTasks.map(\.sortOrder).min()
            case .logbook, .trash:
                sched = .inbox
                firstCurrentOrder = inboxTasks.map(\.sortOrder).min()
                selectedDestination = .smartView(.inbox)
            }
        case .project(let pid):
            sched = .anytime
            projId = pid
            areaId = project(byId: pid)?.areaId
            firstCurrentOrder = tasksForProject(pid)
                .filter { $0.headingId == preferredHeadingId }
                .map(\.sortOrder)
                .min()
        case .area(let aid):
            sched = .anytime
            areaId = aid
            firstCurrentOrder = looseTasksForArea(aid).map(\.sortOrder).min()
        case .perspective(let pid):
            if let p = perspective(byId: pid) {
                let dw = p.dueWindow.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                if dw == "TODAY" {
                    sched = .today
                    whenMillis = todayEpoch
                } else if dw == "SOMEDAY" {
                    sched = .someday
                } else if dw == "INBOX" {
                    sched = .inbox
                } else {
                    sched = .anytime
                }
                areaId = p.areaId
                perspectiveTagIds = p.categoryIds
                perspectiveEffort = TaskEffortLevel.normalized(p.effortLevel)?.rawValue
                perspectiveMaxDuration = p.maxDurationMinutes
                firstCurrentOrder = tasksForPerspective(p).map(\.sortOrder).min()
            } else {
                sched = .anytime
                firstCurrentOrder = anytimeTasks.map(\.sortOrder).min()
            }
        }

        var tagIds: [String] = perspectiveTagIds
        if let activeTag = selectedFilterTagId, !tagIds.contains(activeTag) {
            tagIds.append(activeTag)
        }

        let targetSortOrder = explicitSortOrder ?? ThingsSQLiteDatabase.computeMidpointOrder(
            prevOrder: nil,
            nextOrder: firstCurrentOrder
        )

        if let headingId = preferredHeadingId {
            collapsedHeadingIds.remove(headingId)
        }

        if let created = try? database.createTask(
            title: "",
            notes: "",
            scheduleState: sched,
            whenDate: whenMillis,
            projectId: projId,
            headingId: preferredHeadingId,
            areaId: areaId,
            tagIds: tagIds,
            sortOrder: targetSortOrder
        ) {
            if perspectiveEffort != nil || perspectiveMaxDuration != nil {
                try? database.setTaskEffortAndDuration(
                    taskId: created.id,
                    effortLevel: perspectiveEffort,
                    durationMinutes: perspectiveMaxDuration
                )
            }
            withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) {
                selectedTaskIds = [created.id]
                lastSelectedTaskId = created.id
                expandedTaskId = created.id
            }
        }
    }

    public func createHeadingInProject(_ projectId: String, sortOrder: Double? = nil) {
        if let created = try? database.createHeading(
            projectId: projectId,
            title: tr("entity_new_heading"),
            sortOrder: sortOrder
        ) {
            collapsedHeadingIds.remove(created.id)
            newlyCreatedHeadingId = created.id
        }
    }

    @discardableResult
    public func promoteUngroupedTasksToHeading(projectId: String, title: String) -> Heading? {
        flushModalDraftsIfNeeded()
        let clean = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty,
              let created = try? database.promoteUngroupedTasksToHeading(projectId: projectId, title: clean) else {
            return nil
        }
        collapsedHeadingIds.remove(created.id)
        reloadSnapshot()
        return created
    }

    public func setProjectViewMode(_ projectId: String, mode: ProjectViewMode) {
        if mode != .roadmap, isTimelineFullScreen {
            isTimelineFullScreen = false
        }
        try? database.setProjectViewMode(projectId: projectId, viewMode: mode.rawValue)
    }

    public func areaViewMode(for areaId: String) -> ProjectViewMode {
        ProjectViewMode.normalized(preferences.areaViewModes[areaId]) == .roadmap ? .roadmap : .list
    }

    public func setAreaViewMode(_ areaId: String, mode: ProjectViewMode) {
        guard !areaId.isEmpty else { return }
        let effective: ProjectViewMode = (mode == .roadmap) ? .roadmap : .list
        if effective != .roadmap, isTimelineFullScreen {
            isTimelineFullScreen = false
        }
        var nextPrefs = preferences
        nextPrefs.areaViewModes[areaId] = effective.rawValue
        preferences = nextPrefs
    }

    public func openGeminiPlanMyDay() {
        geminiInitialTab = .planMyDay
        isGeminiSheetPresented = true
    }

    public func toggleTimelineFullScreen() {
        withAnimation(.spring(response: 0.26, dampingFraction: 0.86)) {
            let next = !isTimelineFullScreen
            if next {
                expandedTaskId = nil
                selectedTaskIds.removeAll()
                lastSelectedTaskId = nil
            }
            isTimelineFullScreen = next
        }
    }

    public func updateHeadingStartDate(_ headingId: String, startDate: Int64?) {
        try? database.updateHeadingStartDate(headingId, startDate: startDate)
    }

    public func updateHeadingEndDate(_ headingId: String, endDate: Int64?) {
        try? database.updateHeadingEndDate(headingId, endDate: endDate)
    }

    public func updateHeadingDates(_ headingId: String, startDate: Int64?, endDate: Int64?) {
        try? database.updateHeadingDates(headingId, startDate: startDate, endDate: endDate)
    }

    public func shiftHeadingRoadmapDates(
        _ heading: Heading,
        deltaDays: Int,
        fallbackEpochDays: (startEpochDay: Int, endEpochDay: Int)? = nil
    ) {
        guard deltaDays != 0 else { return }
        let previousStart = heading.startDate
        let previousEnd = heading.endDate
        var effectiveHeading = heading
        if effectiveHeading.startDate == nil && effectiveHeading.endDate == nil {
            if let fallbackEpochDays {
                effectiveHeading.startDate = DateEpochConverter.startOfDayMillis(
                    for: DateEpochConverter.date(fromEpochDay: fallbackEpochDays.startEpochDay)
                )
                effectiveHeading.endDate = DateEpochConverter.startOfDayMillis(
                    for: DateEpochConverter.date(fromEpochDay: fallbackEpochDays.endEpochDay)
                )
            } else {
                let open = allOpenTasks.filter { $0.projectId == heading.projectId && !$0.isDeleted }
                if let proj = project(byId: heading.projectId) {
                    let layout = ProjectRoadmapEngine.buildLayout(
                        project: proj,
                        headings: headingsByProject[proj.id] ?? [heading],
                        tasks: open,
                        todayMillis: DateEpochConverter.startOfDayMillis(for: Date())
                    )
                    if let inferred = layout.phaseBar(for: heading.id) {
                        effectiveHeading.startDate = DateEpochConverter.startOfDayMillis(
                            for: DateEpochConverter.date(fromEpochDay: inferred.startEpochDay)
                        )
                        effectiveHeading.endDate = DateEpochConverter.startOfDayMillis(
                            for: DateEpochConverter.date(fromEpochDay: inferred.endEpochDay)
                        )
                    }
                }
            }
        }
        let shifted = ProjectRoadmapEngine.shiftHeadingDates(heading: effectiveHeading, deltaDays: deltaDays)
        updateHeadingDates(heading.id, startDate: shifted.startDate, endDate: shifted.endDate)
        presentUndo(
            message: tr("v31_roadmap_rescheduled_undo"),
            action: .headingDatesChanged(
                headingId: heading.id,
                previousStart: previousStart,
                previousEnd: previousEnd
            )
        )
    }

    public func shiftTaskRoadmapDates(_ task: TaskItem, deltaDays: Int) {
        guard deltaDays != 0 else { return }
        let todayMs = DateEpochConverter.startOfDayMillis(for: Date())
        let byId = Dictionary(uniqueKeysWithValues: (allOpenTasks + logbookTasks).map { ($0.id, $0) })

        var shiftsToApply: [CascadedTaskShift] = []
        if selectedTaskIds.contains(task.id) && selectedTaskIds.count > 1 {
            let targetIds = orderedTaskIds(matching: selectedTaskIds)
            var mergedByTaskId: [String: CascadedTaskShift] = [:]
            var orderedIds: [String] = []
            for tid in targetIds {
                guard let current = byId[tid] ?? (tid == task.id ? task : nil) else { continue }
                let peerTasks = current.projectId != nil
                    ? allOpenTasks.filter { $0.projectId == current.projectId }
                    : [current]
                let cascaded = ProjectRoadmapEngine.computeSmartDependencyCascade(
                    anchorTask: current,
                    deltaDays: deltaDays,
                    projectTasks: peerTasks,
                    todayMillis: todayMs
                )
                for item in cascaded {
                    let isSelectedAnchor = selectedTaskIds.contains(item.task.id)
                    if let prev = mergedByTaskId[item.task.id] {
                        if abs(item.deltaDays) > abs(prev.deltaDays) || (isSelectedAnchor && !prev.isAnchor) {
                            mergedByTaskId[item.task.id] = CascadedTaskShift(
                                task: item.task,
                                deltaDays: item.deltaDays,
                                nextScheduleState: item.nextScheduleState,
                                nextWhenMillis: item.nextWhenMillis,
                                nextDeadlineMillis: item.nextDeadlineMillis,
                                isAnchor: isSelectedAnchor
                            )
                        }
                    } else {
                        orderedIds.append(item.task.id)
                        mergedByTaskId[item.task.id] = CascadedTaskShift(
                            task: item.task,
                            deltaDays: item.deltaDays,
                            nextScheduleState: item.nextScheduleState,
                            nextWhenMillis: item.nextWhenMillis,
                            nextDeadlineMillis: item.nextDeadlineMillis,
                            isAnchor: isSelectedAnchor
                        )
                    }
                }
            }
            shiftsToApply = orderedIds.compactMap { mergedByTaskId[$0] }
        } else {
            let current = byId[task.id] ?? task
            let peerTasks = current.projectId != nil
                ? allOpenTasks.filter { $0.projectId == current.projectId }
                : [current]
            shiftsToApply = ProjectRoadmapEngine.computeSmartDependencyCascade(
                anchorTask: current,
                deltaDays: deltaDays,
                projectTasks: peerTasks,
                todayMillis: todayMs
            )
        }

        guard !shiftsToApply.isEmpty,
              let snapshots = try? database.applyCascadedTaskShifts(shiftsToApply),
              !snapshots.isEmpty else {
            return
        }
        reloadSnapshot()
        NotificationReminderService.shared.syncAllTaskReminders(
            openTasks: allOpenTasks,
            lang: preferences.language
        )
        let cascadedCount = shiftsToApply.filter { !$0.isAnchor }.count
        let undoMessage = cascadedCount > 0
            ? trf("v31_roadmap_cascade_undo", cascadedCount)
            : tr("v31_roadmap_rescheduled_undo")
        presentUndo(
            message: undoMessage,
            action: .tasksMoved(snapshots: snapshots, spawnedRecurringTaskIds: [])
        )
    }

    @discardableResult
    public func dropTasks(_ payloads: [String], onto destination: NavigationDestination) -> Bool {
        let decodedIds = Self.decodeDragPayloads(payloads)
        guard !decodedIds.isEmpty else { return false }
        if case .perspective = destination { return false }

        if destination == .smartView(.trash) || destination == .smartView(.logbook) {
            if let ft = focusTimer, decodedIds.contains(ft.taskId) {
                stopFocusTimer()
            }
            NotificationReminderService.shared.cancelTaskReminders(taskIds: decodedIds)
        }

        guard let result = try? database.moveTasksToDestination(decodedIds, destination: destination),
              !result.snapshots.isEmpty else {
            return false
        }

        reloadSnapshot()

        let visibleAfter = Set(currentVisibleTaskIdsInOrder())
        let remainingSelected = selectedTaskIds.intersection(visibleAfter)
        if remainingSelected != selectedTaskIds {
            selectedTaskIds = remainingSelected
            if !remainingSelected.contains(lastSelectedTaskId ?? "") {
                lastSelectedTaskId = remainingSelected.first
            }
        }
        if let exp = expandedTaskId, !visibleAfter.contains(exp) {
            expandedTaskId = nil
        }

        NotificationReminderService.shared.syncAllTaskReminders(
            openTasks: allOpenTasks,
            lang: preferences.language
        )

        if destination == .smartView(.trash) {
            if result.snapshots.count == 1, let single = result.snapshots.first?.task {
                let displayTitle = single.title.isEmpty ? tr("macos_untitled_todo") : single.title
                presentUndo(
                    message: trf("undo_task_deleted", displayTitle),
                    action: .tasksMoved(snapshots: result.snapshots)
                )
            } else {
                presentUndo(
                    message: trf("undo_task_deleted", "\(result.snapshots.count)"),
                    action: .tasksMoved(snapshots: result.snapshots)
                )
            }
        } else if destination == .smartView(.logbook), result.snapshots.count == 1, let single = result.snapshots.first?.task {
            let displayTitle = single.title.isEmpty ? tr("macos_untitled_todo") : single.title
            presentUndo(
                message: trf("undo_task_completed", displayTitle),
                action: .tasksMoved(snapshots: result.snapshots, spawnedRecurringTaskIds: result.spawnedRecurringTaskIds)
            )
        } else {
            presentUndo(
                message: trf("v30_drop_moved_feedback", result.snapshots.count),
                action: .tasksMoved(snapshots: result.snapshots, spawnedRecurringTaskIds: result.spawnedRecurringTaskIds)
            )
        }

        return true
    }

    @discardableResult
    public func dropTasksIntoProjectSection(
        _ payloads: [String],
        projectId: String,
        headingId: String?,
        beforeTaskId: String? = nil
    ) -> Bool {
        let decodedIds = Self.decodeDragPayloads(payloads)
        guard !decodedIds.isEmpty else { return false }
        if decodedIds.count == 1, decodedIds[0] == beforeTaskId {
            return false
        }

        guard let snapshots = try? database.moveTasksIntoProjectSection(
            decodedIds,
            projectId: projectId,
            headingId: headingId,
            beforeTaskId: beforeTaskId
        ), !snapshots.isEmpty else {
            return false
        }

        reloadSnapshot()
        let visibleAfter = Set(currentVisibleTaskIdsInOrder())
        let remainingSelected = selectedTaskIds.intersection(visibleAfter)
        if remainingSelected != selectedTaskIds {
            selectedTaskIds = remainingSelected
            if !remainingSelected.contains(lastSelectedTaskId ?? "") {
                lastSelectedTaskId = remainingSelected.first
            }
        }
        if let exp = expandedTaskId, !visibleAfter.contains(exp) {
            expandedTaskId = nil
        }

        NotificationReminderService.shared.syncAllTaskReminders(
            openTasks: allOpenTasks,
            lang: preferences.language
        )

        presentUndo(
            message: trf("v30_drop_moved_feedback", snapshots.count),
            action: .tasksMoved(snapshots: snapshots)
        )
        return true
    }

    @discardableResult
    public func dropTasksBeforeTask(
        _ payloads: [String],
        targetTask: TaskItem,
        in sectionTasks: [TaskItem],
        projectId: String? = nil,
        targetHeadingId: String?? = nil
    ) -> Bool {
        let decodedIds = Self.decodeDragPayloads(payloads)
        guard !decodedIds.isEmpty else { return false }
        if decodedIds.count == 1, decodedIds[0] == targetTask.id {
            return false
        }

        if let pid = projectId {
            let resolvedHeading: String? = {
                if let explicit = targetHeadingId {
                    return explicit
                }
                return targetTask.headingId
            }()
            return dropTasksIntoProjectSection(
                payloads,
                projectId: pid,
                headingId: resolvedHeading,
                beforeTaskId: targetTask.id
            )
        }

        // Non-project view: if dragged tasks include trashed/external tasks, apply current destination rules first
        let needsContainerAdoption = decodedIds.contains { tid in
            trashedTasks.contains(where: { $0.id == tid }) || logbookTasks.contains(where: { $0.id == tid })
        }
        if needsContainerAdoption {
            switch selectedDestination {
            case .smartView(.inbox), .smartView(.today), .smartView(.upcoming), .smartView(.anytime), .smartView(.someday), .area:
                _ = try? database.moveTasksToDestination(decodedIds, destination: selectedDestination)
            default:
                break
            }
        }

        let movingSet = Set(decodedIds)
        let remaining = sectionTasks.filter { !movingSet.contains($0.id) }
        let prevOrder: Double?
        let nextOrder: Double?
        if let remIdx = remaining.firstIndex(where: { $0.id == targetTask.id }) {
            prevOrder = remIdx > 0 ? remaining[remIdx - 1].sortOrder : nil
            nextOrder = remaining[remIdx].sortOrder
        } else {
            prevOrder = remaining.last?.sortOrder
            nextOrder = nil
        }

        guard let snapshots = try? database.reorderMultipleTasks(
            decodedIds,
            prevOrder: prevOrder,
            nextOrder: nextOrder,
            newHeadingId: targetHeadingId
        ), !snapshots.isEmpty else {
            return false
        }

        reloadSnapshot()
        if snapshots.count > 1 {
            presentUndo(
                message: trf("v30_drop_moved_feedback", snapshots.count),
                action: .tasksMoved(snapshots: snapshots)
            )
        }
        return true
    }

    public func setTaskStandaloneDuration(_ taskId: String, durationMinutes: Int?) {
        flushModalDraftsIfNeeded()
        let existingEffort = (allOpenTasks + logbookTasks).first(where: { $0.id == taskId })?.effortLevel
        try? database.setTaskEffortAndDuration(
            taskId: taskId,
            effortLevel: existingEffort,
            durationMinutes: durationMinutes
        )
    }

    public func setTaskEffort(_ taskId: String, effortLevel: String?) {
        flushModalDraftsIfNeeded()
        let existingDuration = (allOpenTasks + logbookTasks).first(where: { $0.id == taskId })?.durationMinutes
        try? database.setTaskEffortAndDuration(
            taskId: taskId,
            effortLevel: effortLevel,
            durationMinutes: existingDuration
        )
    }

    public func setTaskEffortAndDuration(_ taskId: String, effortLevel: String?, durationMinutes: Int?) {
        flushModalDraftsIfNeeded()
        try? database.setTaskEffortAndDuration(
            taskId: taskId,
            effortLevel: effortLevel,
            durationMinutes: durationMinutes
        )
    }

    @discardableResult
    public func setTaskBlockedBy(_ taskId: String, blockedByTaskIds: [String]) -> Bool {
        flushModalDraftsIfNeeded()
        let ok = (try? database.setTaskBlockedBy(taskId: taskId, blockedByTaskIds: blockedByTaskIds)) ?? false
        if !ok {
            showToast(tr("v30_blocked_by_cycle_warning"))
        }
        return ok
    }

    public func updateMagicPlusDrag(locationInDetail: CGPoint) {
        let isProjectView: Bool
        if case .project = selectedDestination {
            isProjectView = true
        } else {
            isProjectView = false
        }
        let resolved = MacMagicPlusDropResolver.resolve(
            pointerX: Double(locationInDetail.x),
            pointerY: Double(locationInDetail.y),
            isProjectView: isProjectView,
            slots: registeredMagicPlusSlots
        )
        if magicPlusActiveDrop != resolved {
            if magicPlusActiveDrop?.indicatorSlotId != resolved.indicatorSlotId ||
               magicPlusActiveDrop?.indicatorEdge != resolved.indicatorEdge ||
               magicPlusActiveDrop?.mode != resolved.mode {
                NSHapticFeedbackManager.defaultPerformer.perform(.alignment, performanceTime: .default)
            }
            magicPlusActiveDrop = resolved
        }
    }

    public func cancelMagicPlusDrag() {
        magicPlusActiveDrop = nil
    }

    public func commitMagicPlusDrop(locationInDetail: CGPoint) {
        let isProjectView: Bool
        var activeProjectId: String? = nil
        if case .project(let pid) = selectedDestination {
            isProjectView = true
            activeProjectId = pid
        } else {
            isProjectView = false
        }

        let resolved = MacMagicPlusDropResolver.resolve(
            pointerX: Double(locationInDetail.x),
            pointerY: Double(locationInDetail.y),
            isProjectView: isProjectView,
            slots: registeredMagicPlusSlots
        )
        magicPlusActiveDrop = nil
        NSHapticFeedbackManager.defaultPerformer.perform(.generic, performanceTime: .default)

        if resolved.mode == .heading, let pid = activeProjectId {
            let headingOrder = ThingsSQLiteDatabase.computeMidpointOrder(
                prevOrder: resolved.prevHeadingOrder,
                nextOrder: resolved.nextHeadingOrder
            )
            createHeadingInProject(pid, sortOrder: headingOrder)
        } else {
            let taskOrder = ThingsSQLiteDatabase.computeMidpointOrder(
                prevOrder: resolved.prevTaskOrder,
                nextOrder: resolved.nextTaskOrder
            )
            createNewTaskInCurrentContext(
                preferredHeadingId: resolved.headingId,
                evening: resolved.evening,
                explicitSortOrder: taskOrder,
                epochDayOverride: resolved.epochDayOverride
            )
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
        flushModalDraftsIfNeeded()
        let fresh = self.taskIncludingTrash(byId: task.id) ?? task
        let nextStatus: TaskStatus = (fresh.status == .completed) ? .open : .completed
        let spawnedId = (try? database.setTaskStatus(taskId: fresh.id, status: nextStatus)) ?? nil
        reloadSnapshot()
        if nextStatus == .completed {
            if focusTimer?.taskId == fresh.id {
                stopFocusTimer()
            }
            NotificationReminderService.shared.cancelTaskReminder(taskId: fresh.id)
            if let spawnedId, let spawnedTask = allOpenTasks.first(where: { $0.id == spawnedId }) {
                NotificationReminderService.shared.syncTaskReminder(spawnedTask, lang: preferences.language)
            }
            let displayTitle = fresh.title.isEmpty ? tr("macos_untitled_todo") : fresh.title
            presentUndo(
                message: trf("undo_task_completed", displayTitle),
                action: .taskCompleted(
                    taskId: fresh.id,
                    title: displayTitle,
                    spawnedRecurringTaskId: spawnedId
                )
            )
        } else if let reopened = allOpenTasks.first(where: { $0.id == fresh.id }) {
            NotificationReminderService.shared.syncTaskReminder(reopened, lang: preferences.language)
        }
    }

    public func cancelTask(_ task: TaskItem) {
        flushModalDraftsIfNeeded()
        let fresh = self.taskIncludingTrash(byId: task.id) ?? task
        let nextStatus: TaskStatus = (fresh.status == .canceled) ? .open : .canceled
        _ = try? database.setTaskStatus(taskId: fresh.id, status: nextStatus)
        reloadSnapshot()
        if nextStatus == .canceled {
            if focusTimer?.taskId == fresh.id {
                stopFocusTimer()
            }
            NotificationReminderService.shared.cancelTaskReminder(taskId: fresh.id)
        } else if let reopened = allOpenTasks.first(where: { $0.id == fresh.id }) {
            NotificationReminderService.shared.syncTaskReminder(reopened, lang: preferences.language)
        }
    }

    public func copyTaskToPasteboard(_ task: TaskItem) {
        flushModalDraftsIfNeeded()
        let fresh = self.taskIncludingTrash(byId: task.id) ?? task
        let markdown = TaskItem.formatMarkdown(fresh, untitledFallback: tr("macos_untitled_todo"))
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(markdown, forType: .string)
        showToast(tr("action_copy_todo"))
    }

    public func copySelectedTasksToPasteboard() {
        flushModalDraftsIfNeeded()
        let orderedIds = orderedTaskIds(matching: selectedTaskIds)
        let pool = allOpenTasks + logbookTasks
        let byId = Dictionary(uniqueKeysWithValues: pool.map { ($0.id, $0) })
        let items = orderedIds.compactMap { byId[$0] }
        guard !items.isEmpty else { return }
        let markdown = items
            .map { TaskItem.formatMarkdown($0, untitledFallback: tr("macos_untitled_todo")) }
            .joined(separator: "\n")
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(markdown, forType: .string)
        showToast(tr("action_copy_todo"))
    }

    public func updateTask(_ task: TaskItem, tagIds: [String]? = nil) {
        try? database.updateTask(task, tagIds: tagIds)
        reloadSnapshot()
        NotificationReminderService.shared.syncTaskReminder(task, lang: preferences.language)
        if preferences.calendarEventsEnabled {
            Task { await refreshCalendarEvents() }
        }
    }

    public func checklistItems(for taskId: String) -> [ChecklistItem] {
        (taskIncludingTrash(byId: taskId)?.checklistItems ?? [])
            .filter { !$0.isDeleted }
            .sorted {
                if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                return $0.createdAt < $1.createdAt
            }
    }

    public func toggleChecklistItem(_ item: ChecklistItem) {
        flushModalDraftsIfNeeded()
        var copy = checklistItems(for: item.taskId).first(where: { $0.id == item.id }) ?? item
        copy.isCompleted.toggle()
        try? database.updateChecklistItem(copy)
        reloadSnapshot()
    }

    @discardableResult
    public func addChecklistItem(taskId: String, title: String) -> ChecklistItem? {
        flushModalDraftsIfNeeded()
        let clean = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return nil }
        let created = try? database.addChecklistItem(taskId: taskId, title: clean)
        reloadSnapshot()
        return created
    }

    public func deleteChecklistItem(_ item: ChecklistItem) {
        flushModalDraftsIfNeeded()
        try? database.deleteChecklistItem(id: item.id, taskId: item.taskId)
        reloadSnapshot()
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
        flushModalDraftsIfNeeded()
        let fresh = self.taskIncludingTrash(byId: task.id) ?? task
        if focusTimer?.taskId == fresh.id {
            stopFocusTimer()
        }
        NotificationReminderService.shared.cancelTaskReminder(taskId: fresh.id)
        try? database.softDeleteTask(fresh.id)
        if expandedTaskId == fresh.id { expandedTaskId = nil }
        selectedTaskIds.remove(fresh.id)
        let displayTitle = fresh.title.isEmpty ? tr("macos_untitled_todo") : fresh.title
        presentUndo(
            message: trf("undo_task_deleted", displayTitle),
            action: .taskDeleted(taskId: fresh.id, title: displayTitle)
        )
    }

    public func deleteTasks(_ ids: Set<String>) {
        flushModalDraftsIfNeeded()
        guard !ids.isEmpty else { return }
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
        selectedTaskIds.subtract(ids)
        if let exp = expandedTaskId, ids.contains(exp) {
            expandedTaskId = nil
        }
        reloadSnapshot()
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

    public func deleteSelectedTasks() {
        flushModalDraftsIfNeeded()
        let ids = selectedTaskIds
        guard !ids.isEmpty else {
            if let exp = expandedTaskId, let t = (allOpenTasks + logbookTasks).first(where: { $0.id == exp }) {
                deleteTask(t)
            }
            return
        }
        deleteTasks(ids)
    }

    public func setTasksPinned(_ taskIds: [String], isPinned: Bool) {
        flushModalDraftsIfNeeded()
        guard !taskIds.isEmpty else { return }
        try? database.setTasksPinned(taskIds, isPinned: isPinned)
    }

    public func togglePinForSelectedTasks() {
        flushModalDraftsIfNeeded()
        let ids = Array(selectedTaskIds)
        guard !ids.isEmpty else { return }
        let candidates = (allOpenTasks + logbookTasks).filter { selectedTaskIds.contains($0.id) }
        let shouldPin = candidates.isEmpty ? true : !candidates.allSatisfy(\.isPinned)
        setTasksPinned(ids, isPinned: shouldPin)
    }

    public func togglePinForSelectedOrExpandedTasks() {
        flushModalDraftsIfNeeded()
        let ids: [String]
        if !selectedTaskIds.isEmpty {
            ids = Array(selectedTaskIds)
        } else if let exp = expandedTaskId {
            ids = [exp]
        } else {
            return
        }
        let candidates = (allOpenTasks + logbookTasks).filter { ids.contains($0.id) }
        let shouldPin = candidates.isEmpty ? true : !candidates.allSatisfy(\.isPinned)
        setTasksPinned(ids, isPinned: shouldPin)
    }

    public func setTasksMilestone(_ ids: [String], isMilestone: Bool) {
        flushModalDraftsIfNeeded()
        guard !ids.isEmpty else { return }
        try? database.setTasksMilestone(ids, isMilestone: isMilestone)
    }

    public func toggleMilestoneForSelectedTasks() {
        flushModalDraftsIfNeeded()
        let ids = Array(selectedTaskIds)
        guard !ids.isEmpty else { return }
        let candidates = (allOpenTasks + logbookTasks).filter { selectedTaskIds.contains($0.id) }
        let shouldMark = candidates.isEmpty ? true : !candidates.allSatisfy(\.isMilestone)
        setTasksMilestone(ids, isMilestone: shouldMark)
    }

    public func toggleMilestoneForSelectedOrExpandedTasks() {
        flushModalDraftsIfNeeded()
        let ids: [String]
        if !selectedTaskIds.isEmpty {
            ids = Array(selectedTaskIds)
        } else if let exp = expandedTaskId {
            ids = [exp]
        } else {
            return
        }
        let candidates = (allOpenTasks + logbookTasks).filter { ids.contains($0.id) }
        let shouldMark = candidates.isEmpty ? true : !candidates.allSatisfy(\.isMilestone)
        setTasksMilestone(ids, isMilestone: shouldMark)
    }

    public func completeSelectedTasks() {
        flushModalDraftsIfNeeded()
        let ids = orderedTaskIds(matching: selectedTaskIds)
        guard !ids.isEmpty else { return }
        for id in ids {
            guard allOpenTasks.contains(where: { $0.id == id }) else { continue }
            if focusTimer?.taskId == id {
                stopFocusTimer()
            }
            NotificationReminderService.shared.cancelTaskReminder(taskId: id)
            let spawnedId = (try? database.setTaskStatus(taskId: id, status: .completed)) ?? nil
            if let spawnedId {
                reloadSnapshot()
                if let spawnedTask = allOpenTasks.first(where: { $0.id == spawnedId }) {
                    NotificationReminderService.shared.syncTaskReminder(spawnedTask, lang: preferences.language)
                }
            }
        }
        reloadSnapshot()
        selectedTaskIds.removeAll()
        if let exp = expandedTaskId, ids.contains(exp) {
            expandedTaskId = nil
        }
    }

    public func toggleSelectedTasksCompletion() {
        flushModalDraftsIfNeeded()
        if selectedTaskIds.count > 1 {
            completeSelectedTasks()
        } else if let id = expandedTaskId ?? selectedTaskIds.first,
                  let task = (allOpenTasks + logbookTasks).first(where: { $0.id == id }) {
            toggleTaskCompletion(task)
        }
    }

    public func scheduleTasks(_ ids: [String], to preset: ScheduleState) {
        flushModalDraftsIfNeeded()
        guard !ids.isEmpty else { return }
        let cal = Calendar.current
        let now = Date()
        for id in ids {
            if var t = (allOpenTasks + logbookTasks).first(where: { $0.id == id }) {
                let hasExistingReminder = t.reminderTime != nil
                let remComps: DateComponents? = t.reminderTime.map {
                    cal.dateComponents([.hour, .minute], from: DateEpochConverter.date(fromMillis: $0))
                }
                let resolved = WhenScheduleResolver.resolvePresetSelection(
                    preset: preset,
                    todayDate: now,
                    calendar: cal,
                    reminderEnabled: hasExistingReminder,
                    reminderHour: remComps?.hour ?? 9,
                    reminderMinute: remComps?.minute ?? 0
                )
                t.scheduleState = resolved.scheduleState
                t.whenDate = resolved.whenDateEpochMillis
                switch preset {
                case .someday, .anytime, .inbox:
                    t.reminderTime = nil
                case .today, .thisEvening, .upcoming:
                    t.reminderTime = resolved.reminderEpochMillis
                }
                updateTask(t)
            }
        }
    }

    public func scheduleSelectedTasks(_ preset: ScheduleState) {
        flushModalDraftsIfNeeded()
        scheduleTasks(Array(selectedTaskIds), to: preset)
    }

    public func batchTagSelectionState() -> (allSelected: Set<String>, partialSelected: Set<String>) {
        let selectedTasks = allOpenTasks.filter { selectedTaskIds.contains($0.id) }
        guard !selectedTasks.isEmpty else { return ([], []) }
        var counts: [String: Int] = [:]
        for t in selectedTasks {
            for tid in Set(t.tagIds) {
                counts[tid, default: 0] += 1
            }
        }
        var allSet = Set<String>()
        var partialSet = Set<String>()
        for (tid, cnt) in counts {
            if cnt == selectedTasks.count {
                allSet.insert(tid)
            } else if cnt > 0 {
                partialSet.insert(tid)
            }
        }
        return (allSet, partialSet)
    }

    public func batchToggleTagOnSelectedTasks(_ tagId: String) {
        flushModalDraftsIfNeeded()
        let ids = Array(selectedTaskIds)
        guard !ids.isEmpty else { return }
        let state = batchTagSelectionState()
        if state.allSelected.contains(tagId) {
            try? database.batchUpdateTasksTags(ids, addTagIds: [], removeTagIds: [tagId])
        } else {
            try? database.batchUpdateTasksTags(ids, addTagIds: [tagId], removeTagIds: [])
        }
    }

    public func duplicateSelectedTasks() {
        flushModalDraftsIfNeeded()
        let orderedIds = orderedTaskIds(matching: selectedTaskIds)
        guard !orderedIds.isEmpty else { return }
        let copySuffix = tr("v25_copy_suffix")
        if let copies = try? database.duplicateTasks(orderedIds, copySuffix: copySuffix), !copies.isEmpty {
            selectedTaskIds = Set(copies.map(\.id))
            lastSelectedTaskId = copies.last?.id
            if copies.count == 1 {
                expandedTaskId = copies[0].id
            } else {
                expandedTaskId = nil
            }
        }
    }

    public func duplicateSelectedOrExpandedTask() {
        flushModalDraftsIfNeeded()
        let copySuffix = tr("v25_copy_suffix")
        if let expId = expandedTaskId {
            if let copy = try? database.duplicateTask(expId, copySuffix: copySuffix) {
                selectedTaskIds = [copy.id]
                lastSelectedTaskId = copy.id
                expandedTaskId = copy.id
            }
            return
        }
        if !selectedTaskIds.isEmpty {
            duplicateSelectedTasks()
            return
        }
        if case .project(let pid) = selectedDestination {
            if let copy = try? database.duplicateProject(pid, copySuffix: copySuffix) {
                selectedDestination = .project(copy.id)
            }
        }
    }

    public func moveTagOrder(_ tagId: String, moveUp: Bool) {
        try? database.moveTagOrder(tagId, moveUp: moveUp)
    }

    public func mergeTag(sourceTag: Tag, into targetTag: Tag) {
        guard sourceTag.id != targetTag.id else { return }
        if selectedFilterTagId == sourceTag.id {
            selectedFilterTagId = targetTag.id
        }
        if (try? database.mergeTags(sourceTagId: sourceTag.id, into: targetTag.id)) == true {
            var updatedPerspectives = preferences.savedPerspectives
            var didRemapPerspectives = false
            for idx in updatedPerspectives.indices where updatedPerspectives[idx].categoryIds.contains(sourceTag.id) {
                var nextCats = updatedPerspectives[idx].categoryIds.filter { $0 != sourceTag.id }
                if !nextCats.contains(targetTag.id) {
                    nextCats.append(targetTag.id)
                }
                updatedPerspectives[idx].categoryIds = nextCats
                didRemapPerspectives = true
            }
            if didRemapPerspectives {
                preferences.savedPerspectives = updatedPerspectives
            }
            showToast(trf("v25_tags_merged_toast", sourceTag.title, targetTag.title))
        }
    }

    public func applyTagShortcut(_ char: String) {
        flushModalDraftsIfNeeded()
        let lower = char.lowercased()
        guard let matchedTag = tags.first(where: { $0.shortcut?.lowercased() == lower }) else { return }
        if selectedTaskIds.count > 1 {
            batchToggleTagOnSelectedTasks(matchedTag.id)
            return
        }
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

    public func requestCompleteProject(_ project: Project) {
        if project.status == .completed {
            try? database.setProjectStatus(projectId: project.id, status: .active)
            return
        }
        let openCount = (try? database.countOpenTasksInProject(project.id)) ?? 0
        if openCount > 0 {
            pendingProjectCompletion = PendingProjectCompletion(project: project, openTaskCount: openCount)
        } else {
            confirmCompleteProject(project, completeOpenTasks: false)
        }
    }

    public func confirmCompleteProject(_ project: Project, completeOpenTasks: Bool) {
        pendingProjectCompletion = nil
        let wasViewingProject = (selectedDestination == .project(project.id))
        guard let completedTaskIds = try? database.completeProjectWithUndo(
            projectId: project.id,
            completeOpenTasks: completeOpenTasks
        ) else { return }

        if !completedTaskIds.isEmpty {
            if let ft = focusTimer, completedTaskIds.contains(ft.taskId) {
                stopFocusTimer()
            }
            NotificationReminderService.shared.cancelTaskReminders(taskIds: completedTaskIds)
            selectedTaskIds.subtract(completedTaskIds)
            if let exp = expandedTaskId, completedTaskIds.contains(exp) {
                expandedTaskId = nil
            }
        }
        if wasViewingProject {
            selectedDestination = .smartView(.logbook)
        }
        presentUndo(
            message: trf("v31_project_completed_undo", project.title),
            action: .projectCompleted(
                projectId: project.id,
                title: project.title,
                reopenedTaskIds: completedTaskIds,
                wasViewingProject: wasViewingProject
            )
        )
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
        case .tasksMoved(let snapshots, let spawnedIds):
            if !spawnedIds.isEmpty {
                NotificationReminderService.shared.cancelTaskReminders(taskIds: spawnedIds)
            }
            try? database.restoreTaskPlacements(snapshots, spawnedRecurringTaskIds: spawnedIds)
            reloadSnapshot()
            NotificationReminderService.shared.syncAllTaskReminders(
                openTasks: allOpenTasks,
                lang: preferences.language
            )
        case .headingDatesChanged(let headingId, let previousStart, let previousEnd):
            try? database.updateHeadingDates(headingId, startDate: previousStart, endDate: previousEnd)
            reloadSnapshot()
        case .projectCompleted(let projectId, _, let reopenedTaskIds, let wasViewingProject):
            try? database.restoreCompletedProject(projectId: projectId, reopenedTaskIds: reopenedTaskIds)
            reloadSnapshot()
            if wasViewingProject {
                selectedDestination = .project(projectId)
            }
            if !reopenedTaskIds.isEmpty {
                NotificationReminderService.shared.syncAllTaskReminders(
                    openTasks: allOpenTasks,
                    lang: preferences.language
                )
            }
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
        flushModalDraftsIfNeeded()
        focusTimerCancellable?.invalidate()
        let fresh = self.taskIncludingTrash(byId: task.id) ?? task
        let title = fresh.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? tr("macos_untitled_todo") : fresh.title
        let sessionMinutes = min(max(1, fresh.durationMinutes ?? 25), 120)
        let durationSeconds = sessionMinutes * 60
        focusTimer = FocusTimerState(
            taskId: fresh.id,
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
            try? await Task.sleep(nanoseconds: 50_000_000)
            guard !Task.isCancelled else { return }
            await cloudSync.pushDirtyFast(localOnlyMode: preferences.localOnlyDbMode)
        }
    }

    public func triggerImmediateCloudSync() {
        guard !preferences.localOnlyDbMode else { return }
        syncDebounceTask?.cancel()
        syncDebounceTask = Task {
            guard !Task.isCancelled else { return }
            await cloudSync.pushDirtyFast(localOnlyMode: preferences.localOnlyDbMode)
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
        self.calendarNowMillis = Int64(Date().timeIntervalSince1970 * 1000.0)
    }

    /// Per Thingsbook Calendar Rules: only show non-past calendar events on dates that have at least one scheduled task!
    public func calendarEventsForEpochDay(
        _ epochDay: Int,
        hasScheduledTasksOnDay: Bool,
        upcomingOnly: Bool = false
    ) -> [CalendarEventItem] {
        guard preferences.calendarEventsEnabled, hasScheduledTasksOnDay else { return [] }
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        let nowMs = max(calendarNowMillis, Int64(Date().timeIntervalSince1970 * 1000.0))
        return CalendarEventItem.filterActiveOrUpcoming(
            calendarEvents,
            forEpochDay: epochDay,
            todayEpochDay: todayEpochDay,
            nowMillis: nowMs,
            upcomingOnly: upcomingOnly
        )
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
            if let tsm = payload.preferences.tagSortMode, !tsm.isEmpty { prefs.tagSortMode = tsm }
            if let vsm = payload.preferences.viewSortMode, !vsm.isEmpty { prefs.viewSortMode = vsm }
            if let rawPerspJson = payload.preferences.savedPerspectivesJson,
               let pData = rawPerspJson.data(using: .utf8),
               let decodedPerspectives = try? JSONDecoder().decode([SavedPerspective].self, from: pData) {
                prefs.savedPerspectives = decodedPerspectives
                if !decodedPerspectives.isEmpty,
                   UserDefaults.standard.object(forKey: Self.showPerspectivesDefaultsKey) == nil {
                    showPerspectivesInSidebar = true
                }
            }
            if let tag = payload.preferences.languageTag {
                prefs.language = tag.isEmpty ? .system : (AppLanguage(rawValue: tag) ?? .system)
            }
            if payload.preferences.workdayStartMinutes != nil || payload.preferences.workdayEndMinutes != nil {
                let normalized = TodayCapacityCalculator.normalizeWorkdayBounds(
                    startMinutes: payload.preferences.workdayStartMinutes ?? prefs.workdayStartMinutes,
                    endMinutes: payload.preferences.workdayEndMinutes ?? prefs.workdayEndMinutes
                )
                prefs.workdayStartMinutes = normalized.startMinutes
                prefs.workdayEndMinutes = normalized.endMinutes
            }
            if case .perspective(let activePid) = selectedDestination,
               !prefs.savedPerspectives.contains(where: { $0.id == activePid }) {
                selectedDestination = .smartView(.today)
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

    // MARK: - v3.0 Asana & Microsoft Planner Import / Export

    @discardableResult
    public func importAsanaProjectInteractively(areaIdOverride: String? = nil) -> Bool {
        let panel = NSOpenPanel()
        panel.title = tr("v30_import_asana")
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        panel.allowedContentTypes = [.commaSeparatedText, .json, .plainText]

        guard panel.runModal() == .OK, let url = panel.url else {
            return false
        }
        return importExternalProjectFromURL(url, areaIdOverride: areaIdOverride)
    }

    @discardableResult
    public func importPlannerProjectInteractively(areaIdOverride: String? = nil) -> Bool {
        let xlsxType = UTType(filenameExtension: "xlsx") ?? .data
        let panel = NSOpenPanel()
        panel.title = tr("v30_import_planner")
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        panel.allowedContentTypes = [xlsxType, .commaSeparatedText, .plainText]

        guard panel.runModal() == .OK, let url = panel.url else {
            return false
        }
        return importExternalProjectFromURL(url, areaIdOverride: areaIdOverride)
    }

    @discardableResult
    private func importExternalProjectFromURL(_ url: URL, areaIdOverride: String?) -> Bool {
        do {
            let rawData = try Data(contentsOf: url)
            let parsed = try ExternalProjectCodec.importAutoDetect(
                data: rawData,
                fileNameHint: url.lastPathComponent,
                fallbackTitle: tr("entity_new_project")
            )
            let effectiveAreaId: String? = {
                if let areaIdOverride { return areaIdOverride }
                if case .area(let aid) = selectedDestination { return aid }
                return nil
            }()
            let res = try database.importExternalProject(parsed, areaId: effectiveAreaId)
            reloadSnapshot()
            isTemplatesSheetPresented = false
            selectedDestination = .project(res.project.id)
            showToast(trf("v30_external_import_success", res.project.title, res.importedTaskCount))
            triggerImmediateCloudSync()
            return true
        } catch {
            showToast(error.localizedDescription)
            return false
        }
    }

    public func exportProjectExternalInteractively(projectId: String, format: ExternalExportFormat) {
        do {
            let exported = try database.exportProjectExternal(projectId: projectId, format: format)
            let xlsxType = UTType(filenameExtension: "xlsx") ?? .data

            let panel = NSSavePanel()
            switch format {
            case .asanaCsv:
                panel.title = tr("v30_export_asana_csv")
                panel.allowedContentTypes = [.commaSeparatedText]
            case .asanaJson:
                panel.title = tr("v30_export_asana_json")
                panel.allowedContentTypes = [.json]
            case .plannerXlsx:
                panel.title = tr("v30_export_planner_xlsx")
                panel.allowedContentTypes = [xlsxType]
            case .plannerCsv:
                panel.title = tr("v30_export_planner_csv")
                panel.allowedContentTypes = [.commaSeparatedText]
            }
            panel.nameFieldStringValue = exported.suggestedFileName
            panel.canCreateDirectories = true

            if panel.runModal() == .OK, let url = panel.url {
                try exported.data.write(to: url, options: .atomic)
                showToast(trf("v30_external_export_success", exported.projectTitle))
            }
        } catch {
            showToast(error.localizedDescription)
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
        let effectiveBeta = AppUpdateService.resolveEffectiveIncludeBeta(requested: preferences.includeBetaUpdates)
        guard let info = try? await AppUpdateService.checkForUpdates(includeBeta: effectiveBeta) else {
            return
        }
        self.availableUpdate = info
        if preferences.autoUpdateCheckEnabled && Self.isRunningFromAppBundle {
            stageUpdateIfNeeded(info, installWhenReady: false)
        }
    }

    public func performManualUpdateCheck() async -> ReleaseUpdateInfo? {
        try? await performManualUpdateCheckResult().get()
    }

    public func performManualUpdateCheckResult() async -> Result<ReleaseUpdateInfo?, Error> {
        let effectiveBeta = AppUpdateService.resolveEffectiveIncludeBeta(requested: preferences.includeBetaUpdates)
        do {
            let found = try await AppUpdateService.checkForUpdates(includeBeta: effectiveBeta)
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
            return .success(found)
        } catch {
            return .failure(error)
        }
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

    private func tickCalendarClockAndRolloverIfNeeded(forceCalendarReload: Bool = false) async {
        self.calendarNowMillis = Int64(Date().timeIntervalSince1970 * 1000.0)
        let currentDay = DateEpochConverter.epochDay(for: Date())
        let dayAdvanced = currentDay != self.lastObservedEpochDay
        if dayAdvanced {
            self.lastObservedEpochDay = currentDay
        }
        let rolledOver = (try? self.database.performMidnightRollover()) ?? 0
        if dayAdvanced || rolledOver > 0 {
            self.reloadSnapshot()
        }
        if (dayAdvanced || forceCalendarReload) && self.preferences.calendarEventsEnabled {
            await self.refreshCalendarEvents()
        }
    }

    private func setupMidnightObserver() {
        NotificationCenter.default.addObserver(
            forName: .NSCalendarDayChanged,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                await self?.tickCalendarClockAndRolloverIfNeeded(forceCalendarReload: true)
            }
        }

        midnightTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                await self?.tickCalendarClockAndRolloverIfNeeded(forceCalendarReload: false)
            }
        }
    }

    private func setupCloudSyncLifecycleObservers() {
        backgroundSyncTask?.cancel()
        backgroundSyncTask = Task { [weak self] in
            while !Task.isCancelled {
                let isAppActive = await MainActor.run { NSApplication.shared.isActive }
                let intervalNs: UInt64 = isAppActive ? 1_800_000_000 : 8_000_000_000
                try? await Task.sleep(nanoseconds: intervalNs)
                guard !Task.isCancelled, let self else { break }
                let localOnly = await MainActor.run { self.preferences.localOnlyDbMode }
                if localOnly {
                    await self.cloudSync.stopLiveWakeStream()
                    continue
                }
                if await self.cloudSync.isAuthenticated {
                    await self.cloudSync.checkRemotePulseAndSyncIfNeeded(localOnlyMode: false)
                }
            }
        }

        appActivationObserver = NotificationCenter.default.addObserver(
            forName: NSApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                await self.tickCalendarClockAndRolloverIfNeeded(forceCalendarReload: true)
                guard !self.preferences.localOnlyDbMode else { return }
                if await self.cloudSync.isAuthenticated {
                    await self.cloudSync.ensureLiveWakeStreamRunning(localOnlyMode: false)
                    await self.cloudSync.pushDirtyFast(localOnlyMode: false)
                    await self.cloudSync.pullIncrementalDeltasOnly(localOnlyMode: false)
                }
            }
        }

        workspaceWakeObserver = NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.didWakeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                await self.tickCalendarClockAndRolloverIfNeeded(forceCalendarReload: true)
                guard !self.preferences.localOnlyDbMode else { return }
                if await self.cloudSync.isAuthenticated {
                    await self.cloudSync.stopLiveWakeStream()
                    await self.cloudSync.ensureLiveWakeStreamRunning(localOnlyMode: false)
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
    public func createProjectFromBlueprint(
        _ blueprint: ProjectBlueprintResponse,
        areaId: String? = nil
    ) -> Project? {
        guard let project = try? database.createProjectFromBlueprint(
            blueprint,
            areaId: areaId,
            languageCode: preferences.language.resolvedCode
        ) else {
            return nil
        }
        reloadSnapshot()
        NotificationReminderService.shared.syncAllTaskReminders(
            openTasks: allOpenTasks,
            lang: preferences.language
        )
        selectedDestination = .project(project.id)
        return project
    }

    public func generateGeminiDayPlan(
        includeAlreadyScheduled: Bool
    ) async throws -> DayTimeblockPlanResponse {
        let cal = Calendar.current
        let now = Date()
        let sod = cal.startOfDay(for: now)
        let nextSod = cal.date(byAdding: .day, value: 1, to: sod) ?? sod.addingTimeInterval(86_400)
        let dayStartMs = DateEpochConverter.startOfDayMillis(for: sod, calendar: cal)
        let dayEndMs = DateEpochConverter.startOfDayMillis(for: nextSod, calendar: cal)
        let todayEpochDay = DateEpochConverter.epochDay(for: sod, calendar: cal)
        let nowComps = cal.dateComponents([.hour, .minute], from: now)
        let nowMins = (nowComps.hour ?? 9) * 60 + (nowComps.minute ?? 0)

        var seen = Set<String>()
        let allTodayOpen = (todayDaytimeTasks + thisEveningTasks).filter {
            !$0.isDeleted && $0.status == .open && seen.insert($0.id).inserted
        }

        let candidates: [DayTimeblockCandidate] = allTodayOpen
            .filter { includeAlreadyScheduled || $0.scheduledStartMinutes == nil }
            .map { task in
                let hasTodayDeadline: Bool = {
                    guard let dl = task.deadline else { return false }
                    return DateEpochConverter.epochDay(forMillis: dl, calendar: cal) <= todayEpochDay
                }()
                let dur = min(240, max(15, (task.durationMinutes ?? 0) > 0 ? task.durationMinutes! : 30))
                return DayTimeblockCandidate(
                    taskId: task.id,
                    title: task.title,
                    durationMinutes: dur,
                    effortLevel: task.effortLevel,
                    isPinned: task.isPinned,
                    hasTodayDeadline: hasTodayDeadline,
                    existingStartMinutes: task.scheduledStartMinutes
                )
            }

        var busyIntervals: [(Int, Int)] = []
        if preferences.calendarEventsEnabled {
            for ev in todayTimelineCalendarEvents() where !ev.isAllDay && ev.endMillis > ev.startMillis {
                let s = min(1440, max(0, Int((max(ev.startMillis, dayStartMs) - dayStartMs) / 60_000)))
                let e = min(1440, max(0, Int((min(ev.endMillis, dayEndMs) - dayStartMs) / 60_000)))
                if e > s {
                    busyIntervals.append((s, e))
                }
            }
        }
        if !includeAlreadyScheduled {
            for task in allTodayOpen {
                guard let s = task.scheduledStartMinutes else { continue }
                let d = min(480, max(15, (task.durationMinutes ?? 0) > 0 ? task.durationMinutes! : 30))
                busyIntervals.append((s, min(1440, s + d)))
            }
        }

        let (workStart, workEnd) = TodayCapacityCalculator.normalizeWorkdayBounds(
            startMinutes: preferences.workdayStartMinutes,
            endMinutes: preferences.workdayEndMinutes
        )
        let earliest = min(22 * 60, max(workStart, ((nowMins + 14) / 15) * 15))

        return try await ThingsbookGeminiService.generateDailyTimeblockPlan(
            candidates: candidates,
            busyIntervalsMinutes: busyIntervals,
            workdayStartMinutes: workStart,
            workdayEndMinutes: workEnd,
            earliestStartMinutes: earliest,
            lang: preferences.language
        )
    }

    public func applyGeminiDayPlan(_ blocks: [DayTimeblockItemDTO]) {
        guard !blocks.isEmpty else { return }
        let updates: [(taskId: String, startMinutes: Int?, durationMinutes: Int?)] = blocks.map {
            (taskId: $0.taskId, startMinutes: Optional($0.startMinutes), durationMinutes: Optional($0.durationMinutes))
        }
        guard let snapshots = try? database.applyBatchTaskTimeblocks(updates),
              !snapshots.isEmpty else {
            return
        }
        todayViewMode = .timeline
        selectedDestination = .smartView(.today)
        reloadSnapshot()
        presentUndo(
            message: trf("v31_gemini_plan_day_applied_undo", snapshots.count),
            action: .tasksMoved(snapshots: snapshots, spawnedRecurringTaskIds: [])
        )
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

public extension View {
    @MainActor
    @ViewBuilder
    func thingsTaskDraggable(store: ThingsbookStore, task: TaskItem) -> some View {
        if store.isTimelineFullScreen {
            self
        } else {
            let isMulti = store.selectedTaskIds.contains(task.id) && store.selectedTaskIds.count > 1
            let previewTitle = isMulti
                ? store.trf("v30_drag_multi_count", store.selectedTaskIds.count)
                : (task.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    ? store.tr("macos_untitled_todo")
                    : task.title)
            self.draggable(store.dragPayload(for: task.id)) {
                HStack(spacing: 7) {
                    Image(systemName: isMulti ? "square.stack.3d.up.fill" : "checkmark.circle")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                    Text(previewTitle)
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(ThingsMacPalette.cardSurface)
                        .shadow(color: Color.black.opacity(0.14), radius: 8, y: 3)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.45), lineWidth: 1)
                )
            }
        }
    }
}

