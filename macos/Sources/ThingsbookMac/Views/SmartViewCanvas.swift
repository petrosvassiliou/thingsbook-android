import SwiftUI
import ThingsbookCore

public struct SmartViewCanvas: View {
    @ObservedObject var store: ThingsbookStore

    @State private var newHeadingTitle = ""
    @State private var isAddingHeading = false
    @State private var renamingHeadingId: String? = nil
    @State private var renameHeadingText = ""
    @State private var showProjectLoggedItems = false

    public init(store: ThingsbookStore) {
        self.store = store
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                // App Update Banner
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
                        emptyPlaceholder(icon: "circle.dashed", text: store.tr("empty_view"))
                    }
                case .area(let areaId):
                    if let area = store.area(byId: areaId) {
                        areaCanvas(area)
                    } else {
                        emptyPlaceholder(icon: "cube", text: store.tr("empty_view"))
                    }
                }
            }
            .padding(.horizontal, 40)
            .padding(.top, 26)
            .padding(.bottom, 96)
            .frame(maxWidth: 880, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .background(ThingsMacPalette.canvasBackground)
    }

    // MARK: - Smart Views

    @ViewBuilder
    private func smartViewContent(_ view: SmartView) -> some View {
        // Header
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: view.sfSymbol)
                .font(.system(size: 25, weight: .bold))
                .foregroundStyle(ThingsMacPalette.color(for: view))

            Text(store.tr(view.titleKey))
                .font(.system(size: 28, weight: .bold))

            Spacer()

            if view == .today {
                let grouped = store.preferences.groupTodayByProjectOrArea
                Button {
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.86)) {
                        store.preferences.groupTodayByProjectOrArea.toggle()
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: grouped ? "rectangle.3.group.fill" : "list.bullet")
                            .font(.system(size: 11, weight: .semibold))
                        Text(grouped ? store.tr("macos_today_grouped_badge") : store.tr("macos_today_list_badge"))
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundStyle(grouped ? ThingsMacPalette.accentBlue : .secondary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(
                        Capsule(style: .continuous)
                            .fill(grouped ? ThingsMacPalette.accentBlue.opacity(0.13) : Color.primary.opacity(0.055))
                    )
                }
                .buttonStyle(.plain)
                .help(store.tr("settings_group_today_title"))
            }

            if view == .logbook && !store.logbookTasks.isEmpty {
                Button {
                    store.geminiInitialTab = .logbookReview
                    store.isGeminiSheetPresented = true
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "sparkles")
                        Text(store.tr("gemini_logbook_review_title"))
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.geminiPurple)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Capsule().fill(ThingsMacPalette.geminiPurple.opacity(0.12)))
                }
                .buttonStyle(.plain)
            }

            if view == .trash && !store.trashedTasks.isEmpty {
                Button(role: .destructive) {
                    try? store.database.emptyTrash()
                } label: {
                    Label(store.tr("trash_empty_btn"), systemImage: "trash.slash")
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
                emptyPlaceholder(icon: "square.stack.3d.up", text: store.tr("empty_view"))
            } else {
                groupedByContainerStack(list)
            }

        case .someday:
            let list = store.filterTasksByActiveTag(store.somedayTasks)
            if list.isEmpty {
                emptyPlaceholder(icon: "archivebox", text: store.tr("empty_view"))
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
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 8) {
                        Image(systemName: "moon.stars.fill")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.eveningPurple)
                        Text(store.tr("view_this_evening"))
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.primary)
                        Spacer()
                        Button {
                            store.createNewTaskInCurrentContext(evening: true)
                        } label: {
                            Image(systemName: "plus")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(.secondary)
                                .frame(width: 22, height: 22)
                        }
                        .buttonStyle(.plain)
                        .help(store.tr("entity_new_todo"))
                    }
                    .padding(.top, 14)

                    Divider()
                        .opacity(0.65)

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
            emptyPlaceholder(icon: "calendar", text: store.tr("empty_view"))
        } else {
            let grouped = Dictionary(grouping: filtered) { task -> Int in
                guard let w = task.whenDate else { return Int.max }
                return DateEpochConverter.epochDay(forMillis: w)
            }
            let sortedDays = grouped.keys.sorted()

            VStack(alignment: .leading, spacing: 22) {
                ForEach(sortedDays, id: \.self) { epochDay in
                    let dayTasks = grouped[epochDay] ?? []
                    let sampleMillis = dayTasks.first?.whenDate
                    VStack(alignment: .leading, spacing: 8) {
                        upcomingDayHeader(sampleMillis: sampleMillis)

                        let events = store.calendarEventsForEpochDay(epochDay, hasScheduledTasksOnDay: !dayTasks.isEmpty)
                        if !events.isEmpty {
                            calendarEventsCard(events)
                        }

                        VStack(spacing: 3) {
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
        df.dateFormat = "EEEE, d. MMMM"

        return HStack(alignment: .firstTextBaseline, spacing: 9) {
            Text("\(dayNum)")
                .font(.system(size: 22, weight: .bold, design: .rounded))
            Text(df.string(from: date))
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.secondary)
            Spacer()
        }
        .padding(.bottom, 4)
        .overlay(alignment: .bottom) {
            Divider().opacity(0.65)
        }
    }

    // MARK: - Project View (Progress Ring, Markdown Notes, Action Pills, Headings, Drag & Drop)

    @ViewBuilder
    private func projectCanvas(_ project: Project) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            // Top Header
            HStack(alignment: .center, spacing: 12) {
                ProjectProgressRing(
                    progress: project.progress,
                    size: 26,
                    tint: ThingsMacPalette.accentBlue,
                    isCompleted: project.status == .completed
                )

                Text(project.title)
                    .font(.system(size: 27, weight: .bold))

                Spacer()

                HStack(spacing: 6) {
                    Button {
                        store.moveProjectOrder(project, direction: -1)
                    } label: {
                        Image(systemName: "arrow.up")
                            .font(.system(size: 12.5, weight: .medium))
                    }
                    .buttonStyle(.borderless)

                    Button {
                        store.moveProjectOrder(project, direction: 1)
                    } label: {
                        Image(systemName: "arrow.down")
                            .font(.system(size: 12.5, weight: .medium))
                    }
                    .buttonStyle(.borderless)

                    Button {
                        store.editingProject = project
                    } label: {
                        Image(systemName: "pencil")
                            .font(.system(size: 13))
                    }
                    .buttonStyle(.borderless)
                    .help(store.tr("project_edit_title"))

                    Button {
                        let next: ProjectStatus = project.status == .completed ? .active : .completed
                        try? store.database.setProjectStatus(projectId: project.id, status: next)
                    } label: {
                        Image(systemName: "checkmark.circle")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.logbookGreen)
                    }
                    .buttonStyle(.borderless)
                    .help(store.tr("macos_complete_project"))

                    Button(role: .destructive) {
                        store.deleteProjectWithUndo(project)
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 13))
                            .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                    }
                    .buttonStyle(.borderless)
                    .help(store.tr("macos_delete_project"))

                    Menu {
                        Button(store.tr("project_edit_title")) {
                            store.editingProject = project
                        }
                        Button(store.tr("action_duplicate_project")) {
                            if let copy = try? store.database.duplicateProject(project.id, copySuffix: store.tr("macos_copy_suffix")) {
                                store.selectedDestination = .project(copy.id)
                            }
                        }
                        Button(store.tr("macos_complete_project")) {
                            try? store.database.setProjectStatus(projectId: project.id, status: .completed)
                        }
                        Divider()
                        Button(role: .destructive) {
                            store.deleteProjectWithUndo(project)
                        } label: {
                            Text(store.tr("macos_delete_project"))
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .font(.system(size: 15))
                    }
                    .menuStyle(.borderlessButton)
                    .fixedSize()
                }
            }

            if !project.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text(MarkdownNotesParser.renderAttributed(project.notes))
                    .font(.system(size: 14))
                    .foregroundStyle(.secondary)
            }

            // Metadata & Quick Action Pills Row (matching Thingsbook Project Header)
            HStack(spacing: 8) {
                if let dl = project.deadline {
                    let todayDay = DateEpochConverter.epochDay(for: Date())
                    let dlDay = DateEpochConverter.epochDay(forMillis: dl)
                    let diff = dlDay - todayDay
                    let dlText: String = {
                        if diff == 0 { return store.tr("deadline_today") }
                        if diff < 0 { return store.trf("deadline_days_overdue", abs(diff)) }
                        return store.trf("deadline_days_left", diff)
                    }()

                    Button {
                        store.editingProject = project
                    } label: {
                        HStack(spacing: 5) {
                            Image(systemName: "flag")
                                .font(.system(size: 11, weight: .semibold))
                            Text("\(store.tr("project_target_date_label")): \(dlText)")
                                .font(.system(size: 12, weight: .semibold))
                        }
                        .foregroundStyle(diff <= 0 ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(
                            RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .fill((diff <= 0 ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue).opacity(0.12))
                        )
                    }
                    .buttonStyle(.plain)
                }

                if let aid = project.areaId, let area = store.area(byId: aid) {
                    Button {
                        store.selectedDestination = .area(area.id)
                    } label: {
                        HStack(spacing: 5) {
                            Image(systemName: "cube")
                                .font(.system(size: 11, weight: .semibold))
                            Text("\(store.tr("entity_area")): \(area.title)")
                                .font(.system(size: 12, weight: .semibold))
                        }
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(
                            RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .fill(ThingsMacPalette.accentBlue.opacity(0.12))
                        )
                    }
                    .buttonStyle(.plain)
                }

                Button {
                    store.createNewTaskInCurrentContext()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                            .font(.system(size: 11, weight: .semibold))
                        Text(store.tr("entity_todo"))
                            .font(.system(size: 12, weight: .medium))
                    }
                    .foregroundStyle(.primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .fill(Color.primary.opacity(0.06))
                    )
                }
                .buttonStyle(.plain)

                Button {
                    isAddingHeading = true
                } label: {
                    HStack(spacing: 4) {
                        Text("H")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(.secondary)
                        Text("+ \(store.tr("entity_heading"))")
                            .font(.system(size: 12, weight: .medium))
                    }
                    .foregroundStyle(.primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .fill(Color.primary.opacity(0.06))
                    )
                }
                .buttonStyle(.plain)
            }

            if isAddingHeading {
                HStack(spacing: 8) {
                    TextField(store.tr("entity_new_heading"), text: $newHeadingTitle)
                        .textFieldStyle(.roundedBorder)
                        .onSubmit {
                            submitNewHeading(projectId: project.id)
                        }
                    Button(store.tr("macos_action_add")) {
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
            }

            let openTasks = store.tasksForProject(project.id)
            let headings = store.headingsByProject[project.id] ?? []
            let unheaded = openTasks.filter { $0.headingId == nil }

            tagFilterBar(for: openTasks)

            if openTasks.isEmpty && headings.isEmpty {
                emptyPlaceholder(icon: "checkmark.circle", text: store.tr("empty_view"))
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
                                ? store.tr("macos_logged_hide")
                                : store.trf("macos_logged_show_count", logged.count)
                        )
                        .font(.system(size: 12.5, weight: .medium))
                    }
                    .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)

                if showProjectLoggedItems {
                    VStack(spacing: 3) {
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
        let isRenaming = renamingHeadingId == heading.id

        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                if isRenaming {
                    TextField(store.tr("entity_heading"), text: $renameHeadingText)
                        .textFieldStyle(.roundedBorder)
                        .frame(maxWidth: 260)
                        .onSubmit {
                            commitHeadingRename(heading)
                        }
                    Button(store.tr("action_save")) {
                        commitHeadingRename(heading)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                    Button(store.tr("action_cancel")) {
                        renamingHeadingId = nil
                    }
                    .controlSize(.small)
                } else {
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
                            Text(heading.title)
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(ThingsMacPalette.accentBlue)
                            if isCollapsed && !tasks.isEmpty {
                                Text("(\(tasks.count))")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }

                Spacer()

                Button {
                    store.createNewTaskInCurrentContext(preferredHeadingId: heading.id)
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .help(store.tr("heading_add_task"))

                Button {
                    renameHeadingText = heading.title
                    renamingHeadingId = heading.id
                } label: {
                    Image(systemName: "pencil")
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .help(store.tr("macos_rename_heading"))

                Button {
                    try? store.database.deleteHeading(heading.id)
                } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 11.5))
                        .foregroundStyle(ThingsMacPalette.deadlineCrimson.opacity(0.85))
                }
                .buttonStyle(.plain)
                .help(store.tr("action_delete"))

                Button {
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                        if isCollapsed {
                            store.collapsedHeadingIds.remove(heading.id)
                        } else {
                            store.collapsedHeadingIds.insert(heading.id)
                        }
                    }
                } label: {
                    Image(systemName: isCollapsed ? "chevron.down" : "chevron.up")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .help(store.tr("action_collapse_heading"))
            }
            .padding(.top, 14)

            Rectangle()
                .fill(ThingsMacPalette.accentBlue.opacity(0.28))
                .frame(height: 1)

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

    private func commitHeadingRename(_ heading: Heading) {
        let clean = renameHeadingText.trimmingCharacters(in: .whitespacesAndNewlines)
        defer { renamingHeadingId = nil }
        guard !clean.isEmpty else { return }
        var copy = heading
        copy.title = clean
        try? store.database.updateHeading(copy)
    }

    // MARK: - Area View

    @ViewBuilder
    private func areaCanvas(_ area: Area) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 12) {
                Image(systemName: "cube.fill")
                    .font(.system(size: 25))
                    .foregroundStyle(ThingsMacPalette.areaGreen)
                Text(area.title)
                    .font(.system(size: 28, weight: .bold))
                Spacer()
                Button {
                    store.editingArea = area
                } label: {
                    Image(systemName: "pencil")
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .help(store.tr("areas_rename_label"))

                Button {
                    store.newProjectDefaultAreaId = area.id
                    store.isNewProjectSheetPresented = true
                } label: {
                    Label(store.tr("entity_new_project"), systemImage: "plus.circle")
                        .font(.system(size: 12))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }

            let areaProjects = store.projectsForArea(area.id)
            let looseTasks = store.looseTasksForArea(area.id)

            if !areaProjects.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(areaProjects) { proj in
                        Button {
                            store.selectedDestination = .project(proj.id)
                        } label: {
                            HStack(spacing: 12) {
                                ProjectProgressRing(progress: proj.progress, size: 18)
                                Text(proj.title)
                                    .font(.system(size: 14.5, weight: .semibold))
                                Spacer()
                                if proj.deadline != nil {
                                    Image(systemName: "flag")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                                }
                                Text(store.trf("macos_items_count", max(0, proj.totalTaskCount - proj.completedTaskCount)))
                                    .font(.system(size: 12))
                                    .foregroundStyle(.secondary)
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 11))
                                    .foregroundStyle(.tertiary)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 11)
                            .thingsCardSurface(cornerRadius: 10)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            if !looseTasks.isEmpty {
                Divider()
                reorderableTaskStack(looseTasks, showSubtitle: false)
            } else if areaProjects.isEmpty {
                emptyPlaceholder(icon: "cube", text: store.tr("empty_view"))
            }
        }
    }

    // MARK: - Logbook & Trash Views

    @ViewBuilder
    private var logbookCanvas: some View {
        let filtered = store.filterTasksByActiveTag(store.logbookTasks)
        if filtered.isEmpty {
            emptyPlaceholder(icon: "checkmark.square", text: store.tr("empty_view"))
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
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .padding(.top, 8)
                    Divider().opacity(0.65)
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
            emptyPlaceholder(icon: "trash", text: store.tr("trash_empty_state"))
        } else {
            VStack(spacing: 8) {
                ForEach(store.trashedTasks) { task in
                    HStack(spacing: 12) {
                        Image(systemName: "trash")
                            .foregroundStyle(.secondary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                                .font(.system(size: 14, weight: .medium))
                            if !task.notes.isEmpty {
                                Text(task.notes)
                                    .font(.system(size: 12))
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        Spacer()
                        Button(store.tr("trash_restore_btn")) {
                            try? store.database.restoreTask(task.id)
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)

                        Button(role: .destructive) {
                            try? store.database.permanentlyDeleteTask(task.id)
                        } label: {
                            Text(store.tr("trash_delete_forever_btn"))
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .thingsCardSurface(cornerRadius: 10)
                }
            }
        }
    }

    // MARK: - Shared Subcomponents

    private func calendarEventsCard(_ events: [CalendarEventItem]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(events) { ev in
                Button {
                    AppleCalendarService.shared.openEventInAppleCalendar(ev)
                } label: {
                    HStack(spacing: 10) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(ThingsMacPalette.colorFromArgb(ev.colorHex))
                            .frame(width: 4, height: 18)

                        if ev.isAllDay {
                            Text(store.tr("calendar_all_day"))
                                .font(.system(size: 11.5, weight: .bold))
                                .foregroundStyle(ThingsMacPalette.accentBlue)
                        } else {
                            Text("\(formatEventTime(ev.startMillis)) – \(formatEventTime(ev.endMillis))")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                                .foregroundStyle(ThingsMacPalette.accentBlue)
                        }

                        Text(ev.title.isEmpty ? store.tr("calendar_untitled_event") : ev.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)

                        Spacer()

                        Text(store.tr("macos_open_in_calendar"))
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Capsule().fill(Color.primary.opacity(0.05)))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(ThingsMacPalette.accentBlue.opacity(0.06))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.16), lineWidth: 0.8)
                            )
                    )
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
        VStack(spacing: 3) {
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
                if let aid = proj.areaId, let area = store.area(byId: aid) {
                    return "\(area.title) › \(proj.title)"
                }
                return proj.title
            }
            if let aid = t.areaId, let area = store.area(byId: aid) {
                return area.title
            }
            return store.tr("macos_today_unassigned")
        }
        let sortedKeys = grouped.keys.sorted()

        return VStack(alignment: .leading, spacing: 16) {
            ForEach(sortedKeys, id: \.self) { groupName in
                VStack(alignment: .leading, spacing: 5) {
                    Text(groupName)
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                    Rectangle()
                        .fill(ThingsMacPalette.accentBlue.opacity(0.22))
                        .frame(height: 1)
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
                    TagPillBadge(title: store.tr("tags_filter_all"), isSelected: store.selectedFilterTagId == nil) {
                        store.selectedFilterTagId = nil
                    }
                    ForEach(availableTags) { tag in
                        TagPillBadge(
                            title: tag.title,
                            isSelected: store.selectedFilterTagId == tag.id,
                            showIcon: true
                        ) {
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
                Text(store.tr("setup_banner_title"))
                    .font(.system(size: 13.5, weight: .semibold))
                Text(store.tr("setup_banner_desc"))
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Button(store.tr("setup_banner_delete_samples_btn")) {
                try? store.database.purgeSampleDataLocally(preserveUsedContainers: true)
                store.preferences.hideSampleSetupBanner = true
            }
            .buttonStyle(.bordered)
            .controlSize(.small)

            Button(store.tr("setup_banner_connect_btn")) {
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
            .help(store.tr("setup_banner_hide_tooltip"))
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
                Text(store.trf("update_banner_title", info.versionString))
                    .font(.system(size: 13, weight: .semibold))
                Text(store.tr("settings_updates_auto_desc"))
                    .font(.system(size: 11.5))
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button(store.tr("update_banner_action_btn")) {
                AppUpdateService.openUpdatePage(info)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)
        }
        .padding(13)
        .thingsCardSurface(cornerRadius: 12)
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
