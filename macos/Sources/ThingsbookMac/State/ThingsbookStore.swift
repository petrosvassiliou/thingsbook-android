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
    @Published public var headingsByProject: [String: [Heading]] = [:]
    @Published public var tags: [Tag] = []

    @Published public var inboxTasks: [TaskItem] = []
    @Published public var todayDaytimeTasks: [TaskItem] = []
    @Published public var thisEveningTasks: [TaskItem] = []
    @Published public var upcomingTasks: [TaskItem] = []
    @Published public var anytimeTasks: [TaskItem] = []
    @Published public var somedayTasks: [TaskItem] = []
    @Published public var logbookTasks: [TaskItem] = []
    @Published public var trashedTasks: [TaskItem] = []
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
    @Published public var isGeminiSheetPresented = false
    @Published public var geminiInitialTab: GeminiSheetTab = .brainDump
    @Published public var isSettingsPresented = false
    @Published public var settingsTab: SettingsSectionTab = .general

    @Published public var isNewProjectSheetPresented = false
    @Published public var newProjectDefaultAreaId: String? = nil
    @Published public var editingProject: Project? = nil
    @Published public var isNewAreaSheetPresented = false
    @Published public var editingArea: Area? = nil
    @Published public var isTagManagerPresented = false

    // MARK: - Undo, Focus Timer, Sync, Calendar, Lock & Updates
    @Published public var undoBanner: UndoBannerState? = nil
    @Published public var focusTimer: FocusTimerState? = nil
    @Published public var syncSnapshot = CloudSyncSnapshot()
    @Published public var calendarEvents: [CalendarEventItem] = []
    @Published public var availableCalendars: [MacCalendarInfo] = []
    @Published public var isLocked = false
    @Published public var availableUpdate: ReleaseUpdateInfo? = nil
    @Published public var toastMessage: String? = nil

    // Backup password prompt state
    @Published public var pendingEncryptedBackupData: Data? = nil
    @Published public var isBackupPasswordSheetPresented = false

    private var undoDismissTask: Task<Void, Never>?
    private var focusTimerCancellable: Timer?
    private var midnightTimer: Timer?
    private var syncDebounceTask: Task<Void, Never>?

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

        NotificationReminderService.shared.configureCategories(lang: prefs.language)
        NotificationReminderService.shared.onActionReceived = { [weak self] taskId, actionId in
            Task { @MainActor [weak self] in
                self?.handleNotificationAction(taskId: taskId, actionIdentifier: actionId)
            }
        }

        setupMidnightObserver()

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
        self.headingsByProject = snap.headingsByProject
        self.tags = snap.tags
        self.inboxTasks = snap.inboxTasks
        self.todayDaytimeTasks = snap.todayDaytimeTasks
        self.thisEveningTasks = snap.thisEveningTasks
        self.upcomingTasks = snap.upcomingTasks
        self.anytimeTasks = snap.anytimeTasks
        self.somedayTasks = snap.somedayTasks
        self.logbookTasks = snap.logbookTasks
        self.trashedTasks = snap.trashedTasks
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
        projects.filter { $0.areaId == areaId && $0.status == .active }
    }

    public var standaloneActiveProjects: [Project] {
        projects.filter { $0.areaId == nil && $0.status == .active }
    }

    public func project(byId id: String?) -> Project? {
        guard let id else { return nil }
        return projects.first { $0.id == id }
    }

    public func area(byId id: String?) -> Area? {
        guard let id else { return nil }
        return areas.first { $0.id == id }
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
            showToast(tr("quick_share_saved"))
        }
    }

    // MARK: - Task Actions & Undo

    public func toggleTaskCompletion(_ task: TaskItem) {
        let nextStatus: TaskStatus = (task.status == .completed) ? .open : .completed
        let spawnedId = try? database.setTaskStatus(taskId: task.id, status: nextStatus)
        if nextStatus == .completed {
            if focusTimer?.taskId == task.id {
                stopFocusTimer()
            }
            presentUndo(
                message: tr("snack_todo_completed"),
                action: .taskCompleted(
                    taskId: task.id,
                    title: task.title.isEmpty ? tr("todo_untitled") : task.title,
                    spawnedRecurringTaskId: spawnedId
                )
            )
        }
    }

    public func cancelTask(_ task: TaskItem) {
        let nextStatus: TaskStatus = (task.status == .canceled) ? .open : .canceled
        _ = try? database.setTaskStatus(taskId: task.id, status: nextStatus)
    }

    public func updateTask(_ task: TaskItem, tagIds: [String]? = nil) {
        try? database.updateTask(task, tagIds: tagIds)
        NotificationReminderService.shared.syncTaskReminder(task, lang: preferences.language)
        if preferences.calendarEventsEnabled {
            Task { await refreshCalendarEvents() }
        }
    }

    public func deleteTask(_ task: TaskItem) {
        try? database.softDeleteTask(task.id)
        if expandedTaskId == task.id { expandedTaskId = nil }
        selectedTaskIds.remove(task.id)
        presentUndo(
            message: tr("snack_moved_to_trash"),
            action: .taskDeleted(taskId: task.id, title: task.title)
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
        var lastDeleted: TaskItem?
        for id in ids {
            if let t = (allOpenTasks + logbookTasks).first(where: { $0.id == id }) {
                try? database.softDeleteTask(id)
                lastDeleted = t
            }
        }
        selectedTaskIds.removeAll()
        expandedTaskId = nil
        if let lastDeleted {
            presentUndo(
                message: tr("snack_moved_to_trash"),
                action: .taskDeleted(taskId: lastDeleted.id, title: lastDeleted.title)
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
        guard let detached = try? database.softDeleteProject(project.id) else { return }
        if selectedDestination == .project(project.id) {
            selectedDestination = .smartView(.today)
        }
        presentUndo(
            message: trf("snack_project_deleted", project.title),
            action: .projectDeleted(projectId: project.id, title: project.title, previousTaskProjects: detached)
        )
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
                try? database.permanentlyDeleteTask(spawnedId)
            }
            _ = try? database.setTaskStatus(taskId: taskId, status: .open)
        case .taskDeleted(let taskId, _):
            try? database.restoreTask(taskId)
        case .projectDeleted(let projectId, _, let previousTasks):
            try? database.restoreDeletedProject(projectId: projectId, previousTasks: previousTasks)
        }
    }

    // MARK: - 25m Focus Pomodoro Timer

    public func startFocusTimer(for task: TaskItem) {
        focusTimerCancellable?.invalidate()
        let title = task.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? tr("todo_untitled") : task.title
        focusTimer = FocusTimerState(taskId: task.id, taskTitle: title)
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

    public func syncNow() {
        Task {
            await cloudSync.syncBidirectionalDeltas(forceFullPull: false, localOnlyMode: preferences.localOnlyDbMode)
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
        panel.title = tr("backup_export_btn")
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        panel.nameFieldStringValue = "Thingsbook-Backup-\(df.string(from: Date())).tbbackup"
        panel.canCreateDirectories = true

        if panel.runModal() == .OK, let url = panel.url {
            do {
                let payload = try database.buildBackupPayload(preferences: preferences)
                let encoded = try ThingsBackupCodec.encodeBackup(payload: payload, password: password)
                try encoded.write(to: url, options: .atomic)
                showToast(tr("backup_export_success"))
            } catch {
                showToast(error.localizedDescription)
            }
        }
    }

    public func importBackupInteractively() {
        let panel = NSOpenPanel()
        panel.title = tr("backup_import_btn")
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
            showToast(error.localizedDescription)
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
            if let tag = payload.preferences.languageTag {
                prefs.language = tag.isEmpty ? .system : (AppLanguage(rawValue: tag) ?? .system)
            }
            self.preferences = prefs
            reloadSnapshot()
            showToast(tr("backup_restore_success"))
        } catch {
            showToast(error.localizedDescription)
        }
    }

    public func exportCsvInteractively() {
        let panel = NSSavePanel()
        panel.title = tr("settings_csv_export_btn")
        panel.nameFieldStringValue = "Thingsbook-Tasks.csv"
        panel.allowedContentTypes = [.commaSeparatedText]
        if panel.runModal() == .OK, let url = panel.url {
            if let csv = try? database.exportCsv() {
                try? Data(csv.utf8).write(to: url, options: .atomic)
                showToast(tr("settings_csv_export_btn") + " ✓")
            }
        }
    }

    public func importCsvInteractively() {
        let panel = NSOpenPanel()
        panel.title = tr("settings_csv_import_btn")
        panel.allowedContentTypes = [.commaSeparatedText, .plainText]
        if panel.runModal() == .OK, let url = panel.url,
           let raw = try? String(contentsOf: url, encoding: .utf8) {
            let count = (try? database.importCsv(raw)) ?? 0
            showToast(trf("settings_csv_import_success", count))
        }
    }

    // MARK: - App Lock, Updates, Midnight & Notification Actions

    public func unlockWithBiometrics() {
        Task {
            let ok = await BiometricLockService.authenticate(reason: tr("biometric_prompt_subtitle"))
            if ok {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                    self.isLocked = false
                }
            }
        }
    }

    public func checkForAppUpdatesSilently() async {
        if let info = try? await AppUpdateService.checkForUpdates() {
            self.availableUpdate = info
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

    // MARK: - URL Scheme (`thingsbook://`) Handler

    public func handleDeepLink(_ url: URL) {
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
        default:
            break
        }
    }
}
