import SwiftUI
import ThingsbookCore

public struct SmartViewCanvas: View {
    @ObservedObject var store: ThingsbookStore

    @State private var newHeadingTitle = ""
    @State private var isAddingHeading = false
    @State private var showProjectLoggedItems = false

    public init(store: ThingsbookStore) {
        self.store = store
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                // App Update Banner (if newer GitHub Release exists)
                if let update = store.availableUpdate {
                    updateAvailableBanner(update)
                }

                // Initial Sample Data Setup Banner
                if store.hasSampleTasks && !store.preferences.hideSampleSetupBanner && store.syncSnapshot.userId == nil {
                    initialSampleSetupBanner
                }

                switch store.selectedDestination {
                case .smartView(let sv):
                    smartViewContent(sv)
                case .project(let projId):
                    if let project = store.project(byId: projId) {
                        projectCanvas(project)
                    } else {
                        emptyPlaceholder(icon: "circle.dashed", text: store.tr("empty_project"))
                    }
                case .area(let areaId):
                    if let area = store.area(byId: areaId) {
                        areaCanvas(area)
                    } else {
                        emptyPlaceholder(icon: "cube", text: store.tr("empty_area"))
                    }
                }
            }
            .padding(.horizontal, 38)
            .padding(.top, 24)
            .padding(.bottom, 90)
            .frame(maxWidth: 860, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .background(Color(nsColor: .windowBackgroundColor))
    }

    // MARK: - Smart Views

    @ViewBuilder
    private func smartViewContent(_ view: SmartView) -> some View {
        // Header
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: view.sfSymbol)
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(ThingsMacPalette.color(for: view))

            Text(store.tr(view.titleKey))
                .font(.system(size: 26, weight: .bold, design: .default))

            Spacer()

            if view == .today {
                Button {
                    store.preferences.groupTodayByProjectOrArea.toggle()
                } label: {
                    Label(
                        store.preferences.groupTodayByProjectOrArea ? "Grouped" : "List",
                        systemImage: store.preferences.groupTodayByProjectOrArea ? "rectangle.3.group" : "list.bullet"
                    )
                    .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .help(store.tr("settings_today_group_title"))
            }

            if view == .logbook && !store.logbookTasks.isEmpty {
                Button {
                    store.geminiInitialTab = .logbookReview
                    store.isGeminiSheetPresented = true
                } label: {
                    Label(store.tr("gemini_logbook_review_btn"), systemImage: "sparkles")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }

            if view == .trash && !store.trashedTasks.isEmpty {
                Button(role: .destructive) {
                    try? store.database.emptyTrash()
                } label: {
                    Label(store.tr("trash_empty_action"), systemImage: "trash.slash")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
        }

        // Tag Filter Strip
        let baseTasks = rawTasksForSmartView(view)
        tagFilterBar(for: baseTasks)

        switch view {
        case .inbox:
            let list = store.filterTasksByActiveTag(store.inboxTasks)
            if list.isEmpty {
                emptyPlaceholder(icon: "tray", text: store.tr("empty_inbox"))
            } else {
                reorderableTaskStack(list)
            }

        case .today:
            todayCanvas

        case .upcoming:
            upcomingCanvas

        case .anytime:
            let list = store.filterTasksByActiveTag(store.anytimeTasks)
            if list.isEmpty {
                emptyPlaceholder(icon: "square.stack.3d.up", text: store.tr("empty_anytime"))
            } else {
                groupedByContainerStack(list)
            }

        case .someday:
            let list = store.filterTasksByActiveTag(store.somedayTasks)
            if list.isEmpty {
                emptyPlaceholder(icon: "archivebox", text: store.tr("empty_someday"))
            } else {
                reorderableTaskStack(list)
            }

        case .logbook:
            logbookCanvas

        case .trash:
            trashCanvas
        }
    }

    // MARK: - Today View (Daytime + Calendar Events + Optional Grouping + This Evening)

    @ViewBuilder
    private var todayCanvas: some View {
        let daytime = store.filterTasksByActiveTag(store.todayDaytimeTasks)
        let evening = store.filterTasksByActiveTag(store.thisEveningTasks)
        let hasTasksToday = !daytime.isEmpty || !evening.isEmpty
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        let todayEvents = store.calendarEventsForEpochDay(todayEpochDay, hasScheduledTasksOnDay: hasTasksToday)

        if !todayEvents.isEmpty {
            calendarEventsCard(todayEvents)
        }

        if !hasTasksToday {
            emptyPlaceholder(icon: "star", text: store.tr("empty_today"))
        } else {
            if !daytime.isEmpty {
                if store.preferences.groupTodayByProjectOrArea {
                    groupedByContainerStack(daytime)
                } else {
                    reorderableTaskStack(daytime)
                }
            }

            if !evening.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 7) {
                        Image(systemName: "moon.stars.fill")
                            .font(.system(size: 14))
                            .foregroundStyle(ThingsMacPalette.eveningPurple)
                        Text(store.tr("section_this_evening"))
                            .font(.system(size: 15, weight: .semibold))
                        Spacer()
                        Button {
                            store.createNewTaskInCurrentContext(evening: true)
                        } label: {
                            Image(systemName: "plus")
                                .font(.system(size: 11.5, weight: .semibold))
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 12)
                    Divider()
                    reorderableTaskStack(evening)
                }
            }
        }
    }

    // MARK: - Upcoming Timeline View

    @ViewBuilder
    private var upcomingCanvas: some View {
        let filtered = store.filterTasksByActiveTag(store.upcomingTasks)
        if filtered.isEmpty {
            emptyPlaceholder(icon: "calendar", text: store.tr("empty_upcoming"))
        } else {
            let grouped = Dictionary(grouping: filtered) { task -> Int in
                guard let w = task.whenDate else { return Int.max }
                return DateEpochConverter.epochDay(forMillis: w)
            }
            let sortedDays = grouped.keys.sorted()

            VStack(alignment: .leading, spacing: 20) {
                ForEach(sortedDays, id: \.self) { epochDay in
                    let dayTasks = grouped[epochDay] ?? []
                    let sampleMillis = dayTasks.first?.whenDate
                    VStack(alignment: .leading, spacing: 8) {
                        upcomingDayHeader(sampleMillis: sampleMillis)

                        let events = store.calendarEventsForEpochDay(epochDay, hasScheduledTasksOnDay: !dayTasks.isEmpty)
                        if !events.isEmpty {
                            calendarEventsCard(events)
                        }

                        VStack(spacing: 2) {
                            ForEach(dayTasks) { task in
                                TaskRowCardView(store: store, task: task)
                            }
                        }
                    }
                }
            }
        }
    }

    private func upcomingDayHeader(sampleMillis: Int64?) -> some View {
        let date = sampleMillis.map { DateEpochConverter.date(fromMillis: $0) } ?? Date()
        let dayNum = Calendar.current.component(.day, from: date)
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.dateFormat = "EEEE, MMMM"

        return HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text("\(dayNum)")
                .font(.system(size: 22, weight: .bold, design: .rounded))
            Text(df.string(from: date))
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.secondary)
            Spacer()
        }
        .overlay(alignment: .bottom) {
            Divider().offset(y: 4)
        }
    }

    // MARK: - Project View (Progress Ring, Markdown Notes, Headings, Drag & Drop)

    @ViewBuilder
    private func projectCanvas(_ project: Project) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center, spacing: 12) {
                ProjectProgressRing(
                    progress: project.progress,
                    size: 24,
                    tint: ThingsMacPalette.accentBlue,
                    isCompleted: project.status == .completed
                )

                Text(project.title)
                    .font(.system(size: 25, weight: .bold))

                Spacer()

                if let dl = project.deadline {
                    let df: DateFormatter = {
                        let f = DateFormatter()
                        f.dateStyle = .medium
                        return f
                    }()
                    Label(df.string(from: DateEpochConverter.date(fromMillis: dl)), systemImage: "flag.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                }

                Button {
                    isAddingHeading = true
                } label: {
                    Label(store.tr("project_new_heading_btn"), systemImage: "text.insert")
                        .font(.system(size: 12))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)

                Menu {
                    Button(store.tr("project_menu_edit")) {
                        store.editingProject = project
                    }
                    Button(store.tr("project_menu_duplicate")) {
                        if let copy = try? store.database.duplicateProject(project.id, copySuffix: store.tr("project_copy_suffix")) {
                            store.selectedDestination = .project(copy.id)
                        }
                    }
                    Button(store.tr("project_complete_action")) {
                        try? store.database.setProjectStatus(projectId: project.id, status: .completed)
                    }
                    Divider()
                    Button(role: .destructive) {
                        store.deleteProjectWithUndo(project)
                    } label: {
                        Text(store.tr("project_menu_delete"))
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.system(size: 16))
                }
                .menuStyle(.borderlessButton)
                .fixedSize()
            }

            if !project.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text(MarkdownNotesParser.renderAttributed(project.notes))
                    .font(.system(size: 13.5))
                    .foregroundStyle(.secondary)
                    .padding(.leading, 36)
            }

            if isAddingHeading {
                HStack(spacing: 8) {
                    TextField(store.tr("project_heading_name_label"), text: $newHeadingTitle)
                        .textFieldStyle(.roundedBorder)
                        .onSubmit {
                            submitNewHeading(projectId: project.id)
                        }
                    Button(store.tr("action_add")) {
                        submitNewHeading(projectId: project.id)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                    Button(store.tr("action_cancel")) {
                        isAddingHeading = false
                        newHeadingTitle = ""
                    }
                    .controlSize(.small)
                }
                .padding(.leading, 36)
            }

            let openTasks = store.tasksForProject(project.id)
            let headings = store.headingsByProject[project.id] ?? []
            let unheaded = openTasks.filter { $0.headingId == nil }

            tagFilterBar(for: openTasks)

            if openTasks.isEmpty && headings.isEmpty {
                emptyPlaceholder(icon: "checkmark.circle", text: store.tr("empty_project"))
            } else {
                if !unheaded.isEmpty {
                    reorderableTaskStack(unheaded, showSubtitle: false, targetHeadingId: .some(nil))
                }

                ForEach(headings) { heading in
                    projectHeadingSection(project: project, heading: heading, tasks: openTasks.filter { $0.headingId == heading.id })
                }
            }

            // Logged Items toggle
            let logged = store.completedTasksForProject(project.id)
            if !logged.isEmpty {
                Divider().padding(.top, 8)
                Button {
                    withAnimation { showProjectLoggedItems.toggle() }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: showProjectLoggedItems ? "chevron.down" : "chevron.right")
                            .font(.system(size: 11, weight: .semibold))
                        Text(
                            showProjectLoggedItems
                                ? store.tr("project_hide_logged")
                                : store.trf("project_show_logged_count", logged.count)
                        )
                        .font(.system(size: 12.5, weight: .medium))
                    }
                    .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)

                if showProjectLoggedItems {
                    VStack(spacing: 2) {
                        ForEach(logged) { t in
                            TaskRowCardView(store: store, task: t, showContextSubtitle: false)
                        }
                    }
                }
            }
        }
    }

    private func projectHeadingSection(project: Project, heading: Heading, tasks: [TaskItem]) -> some View {
        let isCollapsed = store.collapsedHeadingIds.contains(heading.id)
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Button {
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                        if isCollapsed {
                            store.collapsedHeadingIds.remove(heading.id)
                        } else {
                            store.collapsedHeadingIds.insert(heading.id)
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: isCollapsed ? "chevron.right" : "chevron.down")
                            .font(.system(size: 10.5, weight: .bold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                        Text(heading.title)
                            .font(.system(size: 14.5, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                        if isCollapsed && !tasks.isEmpty {
                            Text("(\(tasks.count))")
                                .font(.system(size: 12))
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .buttonStyle(.plain)

                Spacer()

                Button {
                    store.createNewTaskInCurrentContext(preferredHeadingId: heading.id)
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)

                Button {
                    try? store.database.deleteHeading(heading.id)
                } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 11))
                        .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 10)

            Divider()

            if !isCollapsed {
                reorderableTaskStack(tasks, showSubtitle: false, targetHeadingId: .some(heading.id))
            }
        }
        .dropDestination(for: String.self) { droppedIds, _ in
            guard let draggedId = droppedIds.first else { return false }
            try? store.database.reorderTask(taskId: draggedId, prevOrder: tasks.last?.sortOrder, nextOrder: nil, newHeadingId: .some(heading.id))
            return true
        }
    }

    private func submitNewHeading(projectId: String) {
        let clean = newHeadingTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        _ = try? store.database.createHeading(projectId: projectId, title: clean)
        newHeadingTitle = ""
        isAddingHeading = false
    }

    // MARK: - Area View

    @ViewBuilder
    private func areaCanvas(_ area: Area) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 10) {
                Image(systemName: "cube.fill")
                    .font(.system(size: 24))
                    .foregroundStyle(ThingsMacPalette.areaGreen)
                Text(area.title)
                    .font(.system(size: 26, weight: .bold))
                Spacer()
                Button {
                    store.newProjectDefaultAreaId = area.id
                    store.isNewProjectSheetPresented = true
                } label: {
                    Label(store.tr("sidebar_new_project"), systemImage: "plus.circle")
                        .font(.system(size: 12))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }

            let areaProjects = store.projectsForArea(area.id)
            let looseTasks = store.looseTasksForArea(area.id)

            if !areaProjects.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(areaProjects) { proj in
                        Button {
                            store.selectedDestination = .project(proj.id)
                        } label: {
                            HStack(spacing: 10) {
                                ProjectProgressRing(progress: proj.progress, size: 17)
                                Text(proj.title)
                                    .font(.system(size: 14, weight: .semibold))
                                Spacer()
                                Text("\(max(0, proj.totalTaskCount - proj.completedTaskCount)) items")
                                    .font(.system(size: 12))
                                    .foregroundStyle(.secondary)
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 11))
                                    .foregroundStyle(.tertiary)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 9)
                            .thingsCardSurface(cornerRadius: 9)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            if !looseTasks.isEmpty {
                Divider()
                reorderableTaskStack(looseTasks, showSubtitle: false)
            } else if areaProjects.isEmpty {
                emptyPlaceholder(icon: "cube", text: store.tr("empty_area"))
            }
        }
    }

    // MARK: - Logbook & Trash Views

    @ViewBuilder
    private var logbookCanvas: some View {
        let filtered = store.filterTasksByActiveTag(store.logbookTasks)
        if filtered.isEmpty {
            emptyPlaceholder(icon: "checkmark.square", text: store.tr("empty_logbook"))
        } else {
            let grouped = Dictionary(grouping: filtered) { t -> String in
                let ms = t.completedAt ?? t.updatedAt
                let df = DateFormatter()
                df.locale = store.preferences.language.locale
                df.dateStyle = .long
                return df.string(from: DateEpochConverter.date(fromMillis: ms))
            }
            let orderedKeys = filtered.reduce(into: [String]()) { acc, t in
                let ms = t.completedAt ?? t.updatedAt
                let df = DateFormatter()
                df.locale = store.preferences.language.locale
                df.dateStyle = .long
                let k = df.string(from: DateEpochConverter.date(fromMillis: ms))
                if acc.last != k && !acc.contains(k) { acc.append(k) }
            }

            ForEach(orderedKeys, id: \.self) { dayTitle in
                VStack(alignment: .leading, spacing: 6) {
                    Text(dayTitle)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .padding(.top, 6)
                    Divider()
                    ForEach(grouped[dayTitle] ?? []) { task in
                        TaskRowCardView(store: store, task: task)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var trashCanvas: some View {
        if store.trashedTasks.isEmpty {
            emptyPlaceholder(icon: "trash", text: store.tr("empty_trash"))
        } else {
            VStack(spacing: 6) {
                ForEach(store.trashedTasks) { task in
                    HStack(spacing: 12) {
                        Image(systemName: "trash")
                            .foregroundStyle(.secondary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(task.title.isEmpty ? store.tr("todo_untitled") : task.title)
                                .font(.system(size: 13.5, weight: .medium))
                            if !task.notes.isEmpty {
                                Text(task.notes)
                                    .font(.system(size: 11.5))
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        Spacer()
                        Button(store.tr("action_restore")) {
                            try? store.database.restoreTask(task.id)
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)

                        Button(role: .destructive) {
                            try? store.database.permanentlyDeleteTask(task.id)
                        } label: {
                            Text(store.tr("trash_delete_forever"))
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .thingsCardSurface(cornerRadius: 9)
                }
            }
        }
    }

    // MARK: - Shared Subcomponents

    private func calendarEventsCard(_ events: [CalendarEventItem]) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            ForEach(events) { ev in
                Button {
                    AppleCalendarService.shared.openEventInAppleCalendar(ev)
                } label: {
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(ThingsMacPalette.colorFromArgb(ev.colorHex))
                            .frame(width: 3.5, height: 15)

                        if ev.isAllDay {
                            Text(store.tr("calendar_all_day"))
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(.secondary)
                        } else {
                            Text(formatEventTime(ev.startMillis))
                                .font(.system(size: 11.5, weight: .semibold, design: .monospaced))
                                .foregroundStyle(.secondary)
                        }

                        Text(ev.title.isEmpty ? store.tr("calendar_untitled_event") : ev.title)
                            .font(.system(size: 12.5, weight: .medium))
                            .foregroundStyle(.primary)
                            .lineLimit(1)

                        Spacer()

                        Text(ev.calendarDisplayName)
                            .font(.system(size: 11))
                            .foregroundStyle(.tertiary)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 7, style: .continuous).fill(Color.primary.opacity(0.035)))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func reorderableTaskStack(
        _ tasks: [TaskItem],
        showSubtitle: Bool = true,
        targetHeadingId: String?? = nil
    ) -> some View {
        VStack(spacing: 2) {
            ForEach(Array(tasks.enumerated()), id: \.element.id) { idx, task in
                TaskRowCardView(store: store, task: task, showContextSubtitle: showSubtitle)
                    .draggable(task.id)
                    .dropDestination(for: String.self) { droppedIds, _ in
                        guard let draggedId = droppedIds.first, draggedId != task.id else { return false }
                        let prev = idx > 0 ? tasks[idx - 1].sortOrder : nil
                        let next = task.sortOrder
                        try? store.database.reorderTask(taskId: draggedId, prevOrder: prev, nextOrder: next, newHeadingId: targetHeadingId)
                        return true
                    }
            }
        }
    }

    private func groupedByContainerStack(_ tasks: [TaskItem]) -> some View {
        let grouped = Dictionary(grouping: tasks) { t -> String in
            if let pid = t.projectId, let proj = store.project(byId: pid) {
                return proj.title
            }
            if let aid = t.areaId, let area = store.area(byId: aid) {
                return area.title
            }
            return store.tr("today_group_unassigned")
        }
        let sortedKeys = grouped.keys.sorted()

        return VStack(alignment: .leading, spacing: 14) {
            ForEach(sortedKeys, id: \.self) { groupName in
                VStack(alignment: .leading, spacing: 4) {
                    Text(groupName)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                    Divider()
                    ForEach(grouped[groupName] ?? []) { task in
                        TaskRowCardView(store: store, task: task, showContextSubtitle: false)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func tagFilterBar(for tasks: [TaskItem]) -> some View {
        let availableTags: [Tag] = {
            var seen = Set<String>()
            var out: [Tag] = []
            for t in tasks {
                for tg in t.tags where !seen.contains(tg.id) {
                    seen.insert(tg.id)
                    out.append(tg)
                }
            }
            return out
        }()

        if !availableTags.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    TagPillBadge(title: store.tr("filter_all"), isSelected: store.selectedFilterTagId == nil) {
                        store.selectedFilterTagId = nil
                    }
                    ForEach(availableTags) { tag in
                        TagPillBadge(title: tag.title, isSelected: store.selectedFilterTagId == tag.id) {
                            store.selectedFilterTagId = (store.selectedFilterTagId == tag.id) ? nil : tag.id
                        }
                    }
                }
            }
        }
    }

    private var initialSampleSetupBanner: some View {
        HStack(spacing: 14) {
            Image(systemName: "sparkles.rectangle.stack.fill")
                .font(.system(size: 22))
                .foregroundStyle(ThingsMacPalette.accentBlue)

            VStack(alignment: .leading, spacing: 3) {
                Text(store.tr("sample_banner_title"))
                    .font(.system(size: 13.5, weight: .semibold))
                Text(store.tr("sample_banner_subtitle"))
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Button(store.tr("sample_banner_clear_btn")) {
                try? store.database.purgeSampleDataLocally(preserveUsedContainers: true)
                store.preferences.hideSampleSetupBanner = true
            }
            .buttonStyle(.bordered)
            .controlSize(.small)

            Button(store.tr("sample_banner_signin_btn")) {
                store.settingsTab = .sync
                store.isSettingsPresented = true
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)

            Button {
                store.preferences.hideSampleSetupBanner = true
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .thingsCardSurface(cornerRadius: 12)
    }

    private func updateAvailableBanner(_ info: ReleaseUpdateInfo) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.down.circle.fill")
                .font(.system(size: 20))
                .foregroundStyle(ThingsMacPalette.logbookGreen)
            VStack(alignment: .leading, spacing: 2) {
                Text(store.trf("update_available_title", info.versionString))
                    .font(.system(size: 13, weight: .semibold))
                Text(store.tr("update_banner_subtitle"))
                    .font(.system(size: 11.5))
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button(store.tr("update_install_now")) {
                AppUpdateService.openUpdatePage(info)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)
        }
        .padding(12)
        .thingsCardSurface(cornerRadius: 11)
    }

    private func emptyPlaceholder(icon: String, text: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 38, weight: .light))
                .foregroundStyle(.quaternary)
            Text(text)
                .font(.system(size: 14))
                .foregroundStyle(.tertiary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 80)
    }

    private func rawTasksForSmartView(_ view: SmartView) -> [TaskItem] {
        switch view {
        case .inbox: return store.inboxTasks
        case .today: return store.todayDaytimeTasks + store.thisEveningTasks
        case .upcoming: return store.upcomingTasks
        case .anytime: return store.anytimeTasks
        case .someday: return store.somedayTasks
        case .logbook: return store.logbookTasks
        case .trash: return store.trashedTasks
        }
    }

    private func formatEventTime(_ millis: Int64) -> String {
        let df = DateFormatter()
        df.timeStyle = .short
        return df.string(from: DateEpochConverter.date(fromMillis: millis))
    }
}
