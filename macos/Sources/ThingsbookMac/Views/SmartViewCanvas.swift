import SwiftUI
import ThingsbookCore

public struct SmartViewCanvas: View {
    @ObservedObject var store: ThingsbookStore

    @State private var newHeadingTitle = ""
    @State private var isAddingHeading = false
    @State private var renamingHeadingId: String? = nil
    @State private var renameHeadingText = ""
    @State private var showProjectLoggedItems = false
    @FocusState private var isRenameHeadingFocused: Bool

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
        .onPreferenceChange(MacMagicPlusSlotsPreferenceKey.self) { slots in
            store.registeredMagicPlusSlots = slots
        }
        .onChange(of: store.selectedDestination) { _, _ in
            store.registeredMagicPlusSlots = []
        }
        .onChange(of: store.newlyCreatedHeadingId, initial: true) { _, newId in
            guard let headingId = newId else { return }
            renamingHeadingId = headingId
            renameHeadingText = ""
            store.newlyCreatedHeadingId = nil
            DispatchQueue.main.async {
                isRenameHeadingFocused = true
            }
        }
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
                Picker("", selection: $store.todayViewMode) {
                    Text(store.tr("v2_list_view")).tag(TodayViewMode.list)
                    Text(store.tr("v2_timeline_view")).tag(TodayViewMode.timeline)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .fixedSize()
                .thingsTooltip("\(store.tr("v2_list_view")) / \(store.tr("v2_timeline_view"))")

                if store.todayViewMode == .list {
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
                                .lineLimit(1)
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(grouped ? ThingsMacPalette.accentBlue : .secondary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            Capsule(style: .continuous)
                                .fill(grouped ? ThingsMacPalette.accentBlue.opacity(0.13) : Color.primary.opacity(0.055))
                        )
                    }
                    .buttonStyle(.plain)
                    .fixedSize(horizontal: true, vertical: false)
                    .thingsTooltip(store.tr("settings_group_today_title"))
                }
            }

            if view == .logbook && !store.logbookTasks.isEmpty {
                Button {
                    store.geminiInitialTab = .logbookReview
                    store.isGeminiSheetPresented = true
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "sparkles")
                        Text(store.tr("gemini_logbook_review_title"))
                            .lineLimit(1)
                    }
                    .fixedSize(horizontal: true, vertical: false)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.geminiPurple)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Capsule().fill(ThingsMacPalette.geminiPurple.opacity(0.12)))
                }
                .buttonStyle(.plain)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip(store.tr("gemini_logbook_review_action"))
            }

            if view == .trash && (!store.trashedTasks.isEmpty || !store.trashedProjects.isEmpty) {
                Button(role: .destructive) {
                    store.emptyAllTrash()
                } label: {
                    Label(store.tr("trash_empty_btn"), systemImage: "trash.slash")
                        .font(.system(size: 12, weight: .medium))
                        .lineLimit(1)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip(store.tr("trash_empty_btn"))
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

    // MARK: - Today View (Daytime + Calendar Events + Optional Grouping + This Evening + v2.0 Timeline)

    @ViewBuilder
    private var todayCanvas: some View {
        let daytime = store.filterTasksByActiveTag(store.todayDaytimeTasks)
        let evening = store.filterTasksByActiveTag(store.thisEveningTasks)
        let hasTasksToday = !daytime.isEmpty || !evening.isEmpty
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        let todayEvents = store.calendarEventsForEpochDay(todayEpochDay, hasScheduledTasksOnDay: hasTasksToday)

        if store.todayViewMode == .timeline {
            TodayDailyTimelineView(store: store, tasks: daytime + evening)
        } else {
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
                            .thingsTooltip(store.tr("entity_new_todo"))
                        }
                        .padding(.top, 14)

                        Divider()
                            .opacity(0.65)

                        reorderableTaskStack(evening, evening: true)
                    }
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
            let todayEpochDay = DateEpochConverter.epochDay(for: Date())
            let grouped = Dictionary(grouping: filtered) { task -> Int in
                guard let w = task.whenDate else { return Int.max }
                return max(todayEpochDay + 1, DateEpochConverter.epochDay(forMillis: w))
            }
            let sortedDays = grouped.keys.sorted()

            VStack(alignment: .leading, spacing: 22) {
                ForEach(sortedDays, id: \.self) { epochDay in
                    let dayTasks = grouped[epochDay] ?? []
                    let sampleMillis = dayTasks.first?.whenDate
                    VStack(alignment: .leading, spacing: 8) {
                        upcomingDayHeader(sampleMillis: sampleMillis)

                        let events = store.calendarEventsForEpochDay(
                            epochDay,
                            hasScheduledTasksOnDay: !dayTasks.isEmpty,
                            upcomingOnly: true
                        )
                        if !events.isEmpty {
                            calendarEventsCard(events)
                        }

                        VStack(spacing: 3) {
                            ForEach(Array(dayTasks.enumerated()), id: \.element.id) { idx, task in
                                TaskRowCardView(store: store, task: task)
                                    .magicPlusDropSlot(
                                        id: "utask_\(task.id)",
                                        kind: .task(
                                            taskId: task.id,
                                            headingId: nil,
                                            prevOrder: idx > 0 ? dayTasks[idx - 1].sortOrder : nil,
                                            taskOrder: task.sortOrder,
                                            nextOrder: (idx + 1 < dayTasks.count) ? dayTasks[idx + 1].sortOrder : nil,
                                            epochDayOverride: epochDay == Int.max ? nil : epochDay,
                                            evening: false
                                        ),
                                        activeDrop: store.magicPlusActiveDrop,
                                        newTodoLabel: store.tr("entity_new_todo"),
                                        newHeadingLabel: store.tr("entity_new_heading")
                                    )
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
        df.setLocalizedDateFormatFromTemplate("EEEEdMMMM")

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

                let stalledInfo = store.projectStalledInfo(project)
                if stalledInfo.isStalled {
                    let badgeIcon = stalledInfo.hasNoNextAction ? "exclamationmark.circle" : "clock.arrow.circlepath"
                    let badgeLabel = stalledInfo.hasNoNextAction
                        ? store.tr("v2_stalled_project_badge")
                        : store.tr("v2_requires_review_badge")
                    Menu {
                        Button(store.tr("v2_mark_reviewed")) {
                            store.markProjectReviewed(project.id)
                        }
                        Button(store.tr("v2_weekly_review_title")) {
                            store.isWeeklyReviewPresented = true
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: badgeIcon)
                                .font(.system(size: 10.5, weight: .semibold))
                            Text(badgeLabel)
                                .font(.system(size: 11, weight: .semibold))
                        }
                        .foregroundStyle(ThingsMacPalette.somedayAmber)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3.5)
                        .background(
                            Capsule(style: .continuous)
                                .fill(ThingsMacPalette.somedayAmber.opacity(0.14))
                        )
                    }
                    .menuStyle(.borderlessButton)
                    .fixedSize()
                    .thingsTooltip(badgeLabel)
                }

                Spacer()

                HStack(spacing: 6) {
                    Button {
                        store.moveProjectOrder(project, direction: -1)
                    } label: {
                        Image(systemName: "arrow.up")
                            .font(.system(size: 12.5, weight: .medium))
                    }
                    .buttonStyle(.borderless)
                    .thingsTooltip(store.tr("project_move_up"))

                    Button {
                        store.moveProjectOrder(project, direction: 1)
                    } label: {
                        Image(systemName: "arrow.down")
                            .font(.system(size: 12.5, weight: .medium))
                    }
                    .buttonStyle(.borderless)
                    .thingsTooltip(store.tr("project_move_down"))

                    Button {
                        store.editingProject = project
                    } label: {
                        Image(systemName: "pencil")
                            .font(.system(size: 13))
                    }
                    .buttonStyle(.borderless)
                    .thingsTooltip(store.tr("project_edit_title"))

                    Button {
                        let next: ProjectStatus = project.status == .completed ? .active : .completed
                        try? store.database.setProjectStatus(projectId: project.id, status: next)
                    } label: {
                        Image(systemName: "checkmark.circle")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.logbookGreen)
                    }
                    .buttonStyle(.borderless)
                    .thingsTooltip(store.tr("macos_complete_project"))

                    Button(role: .destructive) {
                        store.deleteProjectWithUndo(project)
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 13))
                            .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                    }
                    .buttonStyle(.borderless)
                    .thingsTooltip(store.tr("macos_delete_project"))

                    Menu {
                        Button(store.tr("project_edit_title")) {
                            store.editingProject = project
                        }
                        Button(store.tr("v2_save_as_template")) {
                            store.saveProjectAsTemplate(project.id)
                        }
                        Button(store.tr("v2_mark_reviewed")) {
                            store.markProjectReviewed(project.id)
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
                    .thingsTooltip(store.tr("macos_tooltip_project_options"))
                }
            }

            if !project.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text(MarkdownNotesParser.renderAttributed(project.notes))
                    .font(.system(size: 14))
                    .foregroundStyle(.secondary)
            }

            // Metadata & Quick Action Pills Row (matching Thingsbook Project Header)
            ThingsFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
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
                                .lineLimit(1)
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(diff <= 0 ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(
                            RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .fill((diff <= 0 ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue).opacity(0.12))
                        )
                    }
                    .buttonStyle(.plain)
                    .fixedSize(horizontal: true, vertical: false)
                    .thingsTooltip(store.tr("project_set_target_date"))
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
                                .lineLimit(1)
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(
                            RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .fill(ThingsMacPalette.accentBlue.opacity(0.12))
                        )
                    }
                    .buttonStyle(.plain)
                    .fixedSize(horizontal: true, vertical: false)
                    .thingsTooltip("\(store.tr("entity_area")): \(area.title)")
                }

                ThingsActionChipButton(
                    title: store.tr("entity_todo"),
                    systemImage: "plus",
                    helpText: "\(store.tr("entity_new_todo")) (⌘N)"
                ) {
                    store.createNewTaskInCurrentContext()
                }

                ThingsActionChipButton(
                    title: store.tr("entity_heading"),
                    systemImage: "text.insert",
                    helpText: store.tr("entity_new_heading")
                ) {
                    isAddingHeading = true
                }

                ThingsActionChipButton(
                    title: store.tr("action_attach_file"),
                    systemImage: "paperclip",
                    helpText: store.tr("action_attach_file")
                ) {
                    store.pickAndAttachFiles(projectId: project.id)
                }
            }

            let projAttachments = store.projectAttachments[project.id] ?? []
            if !projAttachments.isEmpty {
                AttachmentsStripView(store: store, attachments: projAttachments) {
                    store.pickAndAttachFiles(projectId: project.id)
                }
                .padding(.top, 4)
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

                ForEach(Array(headings.enumerated()), id: \.element.id) { hIdx, heading in
                    let sectionTasks = openTasks.filter { $0.headingId == heading.id }
                    let prevHeadingOrder = hIdx > 0 ? headings[hIdx - 1].sortOrder : nil
                    let prevSectionId: String? = hIdx > 0 ? headings[hIdx - 1].id : nil
                    let prevSectionLastTaskOrder: Double? = hIdx > 0
                        ? openTasks.filter { $0.headingId == headings[hIdx - 1].id }.last?.sortOrder
                        : unheaded.last?.sortOrder

                    projectHeadingSection(
                        project: project,
                        heading: heading,
                        tasks: sectionTasks,
                        prevHeadingOrder: prevHeadingOrder,
                        prevSectionId: prevSectionId,
                        prevSectionLastTaskOrder: prevSectionLastTaskOrder
                    )
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

    private func projectHeadingSection(
        project: Project,
        heading: Heading,
        tasks: [TaskItem],
        prevHeadingOrder: Double?,
        prevSectionId: String?,
        prevSectionLastTaskOrder: Double?
    ) -> some View {
        let isCollapsed = store.collapsedHeadingIds.contains(heading.id)
        let isRenaming = renamingHeadingId == heading.id

        return VStack(alignment: .leading, spacing: 6) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    if isRenaming {
                        TextField(store.tr("entity_new_heading"), text: $renameHeadingText)
                            .textFieldStyle(.roundedBorder)
                            .frame(maxWidth: 260)
                            .focused($isRenameHeadingFocused)
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
                    .thingsTooltip(store.tr("heading_add_task"))

                    Button {
                        renameHeadingText = heading.title
                        renamingHeadingId = heading.id
                        DispatchQueue.main.async {
                            isRenameHeadingFocused = true
                        }
                    } label: {
                        Image(systemName: "pencil")
                            .font(.system(size: 11.5))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(store.tr("macos_rename_heading"))

                    Button {
                        try? store.database.deleteHeading(heading.id)
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 11.5))
                            .foregroundStyle(ThingsMacPalette.deadlineCrimson.opacity(0.85))
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(store.tr("action_delete"))

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
                    .thingsTooltip(store.tr("action_collapse_heading"))
                }
                .padding(.top, 14)

                Rectangle()
                    .fill(ThingsMacPalette.accentBlue.opacity(0.28))
                    .frame(height: 1)
            }
            .magicPlusDropSlot(
                id: "heading_\(heading.id)",
                kind: .heading(
                    headingId: heading.id,
                    prevHeadingOrder: prevHeadingOrder,
                    headingOrder: heading.sortOrder,
                    firstTaskOrder: tasks.first?.sortOrder,
                    prevSectionId: prevSectionId,
                    prevSectionLastTaskOrder: prevSectionLastTaskOrder
                ),
                activeDrop: store.magicPlusActiveDrop,
                newTodoLabel: store.tr("entity_new_todo"),
                newHeadingLabel: store.tr("entity_new_heading")
            )

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
                .thingsTooltip(store.tr("areas_rename_label"))

                Button {
                    store.newProjectDefaultAreaId = area.id
                    store.isNewProjectSheetPresented = true
                } label: {
                    Label(store.tr("entity_new_project"), systemImage: "plus.circle")
                        .font(.system(size: 12))
                        .lineLimit(1)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip(store.trf("sidebar_new_project_in_area", area.title))
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
                        .contextMenu {
                            Button(store.tr("project_edit_title")) {
                                store.editingProject = proj
                            }
                            Button(store.tr("v2_save_as_template")) {
                                store.saveProjectAsTemplate(proj.id)
                            }
                            Button(store.tr("v2_mark_reviewed")) {
                                store.markProjectReviewed(proj.id)
                            }
                            Button(store.tr("action_duplicate_project")) {
                                if let copy = try? store.database.duplicateProject(proj.id, copySuffix: store.tr("macos_copy_suffix")) {
                                    store.selectedDestination = .project(copy.id)
                                }
                            }
                            Button(store.tr("macos_complete_project")) {
                                try? store.database.setProjectStatus(projectId: proj.id, status: .completed)
                            }
                            Divider()
                            Button(role: .destructive) {
                                store.deleteProjectWithUndo(proj)
                            } label: {
                                Text(store.tr("macos_delete_project"))
                            }
                        }
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
        if store.trashedTasks.isEmpty && store.trashedProjects.isEmpty {
            emptyPlaceholder(icon: "trash", text: store.tr("trash_empty_state"))
        } else {
            VStack(alignment: .leading, spacing: 14) {
                if !store.trashedProjects.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(store.tr("entity_projects"))
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 4)

                        ForEach(store.trashedProjects) { proj in
                            HStack(spacing: 12) {
                                Image(systemName: "chart.pie")
                                    .foregroundStyle(ThingsMacPalette.accentBlue)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(proj.title.isEmpty ? store.tr("entity_new_project") : proj.title)
                                        .font(.system(size: 14, weight: .semibold))
                                    if !proj.notes.isEmpty {
                                        Text(proj.notes)
                                            .font(.system(size: 12))
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                }
                                Spacer()
                                Button(store.tr("trash_restore_btn")) {
                                    store.restoreTrashedProject(proj)
                                }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                                .fixedSize(horizontal: true, vertical: false)
                                .thingsTooltip(store.tr("trash_restore_btn"))

                                Button(role: .destructive) {
                                    store.permanentlyDeleteProject(proj)
                                } label: {
                                    Text(store.tr("trash_delete_forever_btn"))
                                        .lineLimit(1)
                                }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                                .fixedSize(horizontal: true, vertical: false)
                                .thingsTooltip(store.tr("trash_delete_forever_btn"))
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .thingsCardSurface(cornerRadius: 10)
                        }
                    }
                }

                if !store.trashedTasks.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        if !store.trashedProjects.isEmpty {
                            Text(store.tr("entity_todos"))
                                .font(.system(size: 12.5, weight: .semibold))
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 4)
                        }

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
                                    store.restoreTrashedTask(task.id)
                                }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                                .fixedSize(horizontal: true, vertical: false)
                                .thingsTooltip(store.tr("trash_restore_btn"))

                                Button(role: .destructive) {
                                    store.permanentlyDeleteTask(task.id)
                                } label: {
                                    Text(store.tr("trash_delete_forever_btn"))
                                        .lineLimit(1)
                                }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                                .fixedSize(horizontal: true, vertical: false)
                                .thingsTooltip(store.tr("trash_delete_forever_btn"))
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .thingsCardSurface(cornerRadius: 10)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Shared Subcomponents

    private func calendarEventsCard(_ events: [CalendarEventItem]) -> some View {
        MacCompactCalendarEventsBlock(store: store, events: events)
    }

    private func reorderableTaskStack(
        _ tasks: [TaskItem],
        showSubtitle: Bool = true,
        targetHeadingId: String?? = nil,
        evening: Bool = false,
        epochDayOverride: Int? = nil
    ) -> some View {
        let resolvedHeadingId: String? = {
            if let explicit = targetHeadingId {
                return explicit
            }
            return nil
        }()

        return VStack(spacing: 3) {
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
                    .magicPlusDropSlot(
                        id: "task_\(task.id)",
                        kind: .task(
                            taskId: task.id,
                            headingId: resolvedHeadingId ?? task.headingId,
                            prevOrder: idx > 0 ? tasks[idx - 1].sortOrder : nil,
                            taskOrder: task.sortOrder,
                            nextOrder: (idx + 1 < tasks.count) ? tasks[idx + 1].sortOrder : nil,
                            epochDayOverride: epochDayOverride,
                            evening: evening
                        ),
                        activeDrop: store.magicPlusActiveDrop,
                        newTodoLabel: store.tr("entity_new_todo"),
                        newHeadingLabel: store.tr("entity_new_heading")
                    )
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
                let groupTasks = grouped[groupName] ?? []
                VStack(alignment: .leading, spacing: 5) {
                    Text(groupName)
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                    Rectangle()
                        .fill(ThingsMacPalette.accentBlue.opacity(0.22))
                        .frame(height: 1)
                    ForEach(Array(groupTasks.enumerated()), id: \.element.id) { idx, task in
                        TaskRowCardView(store: store, task: task, showContextSubtitle: false)
                            .magicPlusDropSlot(
                                id: "gtask_\(task.id)",
                                kind: .task(
                                    taskId: task.id,
                                    headingId: task.headingId,
                                    prevOrder: idx > 0 ? groupTasks[idx - 1].sortOrder : nil,
                                    taskOrder: task.sortOrder,
                                    nextOrder: (idx + 1 < groupTasks.count) ? groupTasks[idx + 1].sortOrder : nil,
                                    epochDayOverride: nil,
                                    evening: false
                                ),
                                activeDrop: store.magicPlusActiveDrop,
                                newTodoLabel: store.tr("entity_new_todo"),
                                newHeadingLabel: store.tr("entity_new_heading")
                            )
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
                store.deleteSampleTasks()
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
            .thingsTooltip(store.tr("setup_banner_hide_tooltip"))
        }
        .padding(14)
        .thingsCardSurface(cornerRadius: 12)
    }

    private func updateAvailableBanner(_ info: ReleaseUpdateInfo) -> some View {
        let isReady: Bool = {
            if case .readyToInstall(let v, _, _) = store.updatePhase {
                return v == info.versionString
            }
            return false
        }()
        let isBusy: Bool = {
            switch store.updatePhase {
            case .downloading, .extracting, .installingAndRelaunching:
                return true
            default:
                return false
            }
        }()

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                Image(systemName: isReady ? "checkmark.circle.fill" : "arrow.down.circle.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(ThingsMacPalette.logbookGreen)
                VStack(alignment: .leading, spacing: 2) {
                    Text(store.trf("update_banner_title", info.versionString))
                        .font(.system(size: 13, weight: .semibold))
                    Text(updateBannerSubtitle(for: info))
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button {
                    store.installAvailableUpdateNow()
                } label: {
                    HStack(spacing: 6) {
                        if isBusy {
                            ProgressView().controlSize(.small)
                        }
                        Text(
                            store.tr(
                                isReady
                                    ? "macos_updates_install_relaunch_btn"
                                    : "update_banner_action_btn"
                            )
                        )
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
                .disabled(
                    {
                        if case .installingAndRelaunching = store.updatePhase { return true }
                        return false
                    }()
                )
            }

            if case .downloading(_, let progress) = store.updatePhase {
                if let progress {
                    ProgressView(value: progress)
                        .progressViewStyle(.linear)
                } else {
                    ProgressView()
                        .progressViewStyle(.linear)
                }
            } else if case .extracting = store.updatePhase {
                ProgressView()
                    .progressViewStyle(.linear)
            } else if case .installingAndRelaunching = store.updatePhase {
                ProgressView()
                    .progressViewStyle(.linear)
            }
        }
        .padding(13)
        .thingsCardSurface(cornerRadius: 12)
    }

    private func updateBannerSubtitle(for info: ReleaseUpdateInfo) -> String {
        switch store.updatePhase {
        case .downloading(let v, let progress):
            if let progress {
                return store.trf("settings_updates_downloading", v, Int((progress * 100.0).rounded()))
            }
            return store.trf("settings_updates_downloading_indeterminate", v)
        case .extracting(let v):
            return store.trf("macos_updates_extracting", v)
        case .readyToInstall(let v, _, _):
            return store.trf("settings_updates_ready_title", v)
        case .installingAndRelaunching(let v):
            return store.trf("macos_updates_installing_relaunching", v)
        case .failed(let message):
            return store.trf("settings_updates_download_failed", message)
        default:
            return store.tr("macos_updates_auto_desc")
        }
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
        .magicPlusDropSlot(
            id: "empty_canvas",
            kind: .emptyContainer(headingId: nil, epochDayOverride: nil, evening: false),
            activeDrop: store.magicPlusActiveDrop,
            newTodoLabel: store.tr("entity_new_todo"),
            newHeadingLabel: store.tr("entity_new_heading")
        )
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
}

// MARK: - v2.0 Interactive Daily Timeline View in Today

private struct TodayDailyTimelineView: View {
    @ObservedObject var store: ThingsbookStore
    let tasks: [TaskItem]

    @State private var activePopoverTaskId: String? = nil

    private enum TimelineSlotKind {
        case calendarEvent(CalendarEventItem)
        case task(TaskItem)
    }

    private struct TimedScheduleSlot: Identifiable {
        let id: String
        let startMinutes: Int
        let endMinutes: Int
        let kind: TimelineSlotKind
    }

    private var allCalendarEventsToday: [CalendarEventItem] {
        store.todayTimelineCalendarEvents()
    }

    private var allDayEvents: [CalendarEventItem] {
        allCalendarEventsToday.filter(\.isAllDay)
    }

    private var scheduledTasks: [TaskItem] {
        tasks
            .filter { $0.scheduledStartMinutes != nil }
            .sorted { ($0.scheduledStartMinutes ?? 0) < ($1.scheduledStartMinutes ?? 0) }
    }

    private var unscheduledTasks: [TaskItem] {
        tasks.filter { $0.scheduledStartMinutes == nil }
    }

    private var timedSlots: [TimedScheduleSlot] {
        var slots: [TimedScheduleSlot] = []
        let cal = Calendar.current
        let sodToday = cal.startOfDay(for: Date())
        let sodMillis = DateEpochConverter.startOfDayMillis(for: sodToday, calendar: cal)
        let eodMillis = sodMillis + 86_400_000

        for ev in allCalendarEventsToday where !ev.isAllDay {
            let clampedStart = max(sodMillis, ev.startMillis)
            let clampedEnd = min(eodMillis, max(clampedStart + 60_000, ev.endMillis))
            let startDate = DateEpochConverter.date(fromMillis: clampedStart)
            let endDate = DateEpochConverter.date(fromMillis: clampedEnd)
            let sComps = cal.dateComponents([.hour, .minute], from: startDate)
            let eComps = cal.dateComponents([.hour, .minute], from: endDate)
            let sMins = ((sComps.hour ?? 0) * 60) + (sComps.minute ?? 0)
            let rawEndMins = clampedEnd >= eodMillis ? 1440 : (((eComps.hour ?? 0) * 60) + (eComps.minute ?? 0))
            let eMins = max(sMins + 15, rawEndMins)
            slots.append(
                TimedScheduleSlot(
                    id: "ev_\(ev.id)",
                    startMinutes: sMins,
                    endMinutes: eMins,
                    kind: .calendarEvent(ev)
                )
            )
        }

        for task in scheduledTasks {
            let sMins = task.scheduledStartMinutes ?? 540
            let eMins = min(1440, sMins + task.effectiveDurationMinutes)
            slots.append(
                TimedScheduleSlot(
                    id: "task_\(task.id)",
                    startMinutes: sMins,
                    endMinutes: max(sMins + 5, eMins),
                    kind: .task(task)
                )
            )
        }

        return slots.sorted { lhs, rhs in
            if lhs.startMinutes != rhs.startMinutes { return lhs.startMinutes < rhs.startMinutes }
            return lhs.endMinutes < rhs.endMinutes
        }
    }

    private var conflictingSlotIds: Set<String> {
        let slots = timedSlots
        var conflicts = Set<String>()
        guard slots.count >= 2 else { return conflicts }
        for i in 0..<slots.count {
            for j in (i + 1)..<slots.count {
                let a = slots[i]
                let b = slots[j]
                if a.startMinutes < b.endMinutes && b.startMinutes < a.endMinutes {
                    conflicts.insert(a.id)
                    conflicts.insert(b.id)
                }
            }
        }
        return conflicts
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            if !allDayEvents.isEmpty {
                MacCompactCalendarEventsBlock(store: store, events: allDayEvents)
            }

            let slots = timedSlots
            let conflicts = conflictingSlotIds

            if slots.isEmpty && unscheduledTasks.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "clock.badge.checkmark")
                        .font(.system(size: 36, weight: .light))
                        .foregroundStyle(.quaternary)
                    Text(store.tr("empty_today"))
                        .font(.system(size: 14))
                        .foregroundStyle(.tertiary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 60)
            } else {
                if !slots.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(slots) { slot in
                            let hasConflict = conflicts.contains(slot.id)
                            switch slot.kind {
                            case .calendarEvent(let ev):
                                timelineCalendarRow(ev, startMinutes: slot.startMinutes, endMinutes: slot.endMinutes, hasConflict: hasConflict)
                            case .task(let task):
                                timelineTaskRow(task, startMinutes: slot.startMinutes, endMinutes: slot.endMinutes, hasConflict: hasConflict)
                            }
                        }
                    }
                }

                if !unscheduledTasks.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 7) {
                            Image(systemName: "tray.and.arrow.down.fill")
                                .font(.system(size: 12.5, weight: .semibold))
                                .foregroundStyle(ThingsMacPalette.todayYellow)
                            Text(store.tr("v2_unscheduled_today"))
                                .font(.system(size: 14, weight: .bold))
                            Text("\(unscheduledTasks.count)")
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(Color.primary.opacity(0.06)))
                            Spacer()
                        }
                        .padding(.top, slots.isEmpty ? 0 : 8)

                        Divider().opacity(0.65)

                        ForEach(unscheduledTasks) { task in
                            unscheduledTaskRow(task)
                        }
                    }
                }
            }
        }
    }

    private func timelineCalendarRow(
        _ ev: CalendarEventItem,
        startMinutes: Int,
        endMinutes: Int,
        hasConflict: Bool
    ) -> some View {
        Button {
            AppleCalendarService.shared.openEventInAppleCalendar(ev)
        } label: {
            HStack(spacing: 10) {
                Text("\(TaskItem.formatMinutesOfDay(startMinutes))–\(TaskItem.formatMinutesOfDay(endMinutes % 1440))")
                    .font(.system(size: CGFloat(MacCalendarAppointmentStyleMetrics.eventTimeFontSize), weight: .medium, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .frame(width: 98, alignment: .leading)

                RoundedRectangle(cornerRadius: 1.5)
                    .fill(ThingsMacPalette.colorFromArgb(ev.colorHex))
                    .frame(
                        width: CGFloat(MacCalendarAppointmentStyleMetrics.colorBarWidth),
                        height: CGFloat(MacCalendarAppointmentStyleMetrics.colorBarHeight + 1.0)
                    )

                Text(ev.title.isEmpty ? store.tr("calendar_untitled_event") : ev.title)
                    .font(.system(size: CGFloat(MacCalendarAppointmentStyleMetrics.eventTitleFontSize), weight: .regular))
                    .foregroundStyle(Color.primary.opacity(0.84))
                    .lineLimit(1)

                if hasConflict {
                    conflictBadge
                }

                Spacer(minLength: 6)

                if !ev.calendarDisplayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(ev.calendarDisplayName)
                        .font(.system(size: CGFloat(MacCalendarAppointmentStyleMetrics.eventCalendarNameFontSize)))
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                        .frame(maxWidth: 120, alignment: .trailing)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 5.5)
            .background(
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
                    .overlay(
                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                            .strokeBorder(
                                hasConflict ? ThingsMacPalette.somedayAmber.opacity(0.65) : Color.primary.opacity(0.07),
                                lineWidth: hasConflict ? 1.1 : 0.75
                            )
                    )
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .thingsTooltip(store.tr("macos_open_in_calendar"))
    }

    private func timelineTaskRow(
        _ task: TaskItem,
        startMinutes: Int,
        endMinutes: Int,
        hasConflict: Bool
    ) -> some View {
        let isPopoverPresented = Binding<Bool>(
            get: { activePopoverTaskId == task.id },
            set: { open in activePopoverTaskId = open ? task.id : nil }
        )

        return HStack(spacing: 12) {
            Button {
                activePopoverTaskId = task.id
            } label: {
                Text("\(TaskItem.formatMinutesOfDay(startMinutes))–\(TaskItem.formatMinutesOfDay(endMinutes % 1440))")
                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                    .foregroundStyle(ThingsMacPalette.anytimeTeal)
                    .frame(width: 98, alignment: .leading)
            }
            .buttonStyle(.plain)
            .popover(isPresented: isPopoverPresented) {
                TimeblockPopoverView(
                    store: store,
                    currentStartMinutes: task.scheduledStartMinutes,
                    currentDurationMinutes: task.durationMinutes
                ) { sMins, dMins in
                    store.setTaskTimeblock(task.id, startMinutes: sMins, durationMinutes: dMins)
                }
            }

            ThingsCheckboxButton(
                status: task.status,
                isSomeday: false,
                helpText: store.tr("macos_checkbox_help"),
                onToggleComplete: { store.toggleTaskCompletion(task) },
                onCancelTask: { store.cancelTask(task) }
            )

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 7) {
                    Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                        .font(.system(size: 14, weight: .medium))
                        .lineLimit(1)

                    Text("\(task.effectiveDurationMinutes)m")
                        .font(.system(size: 11, weight: .semibold, design: .rounded))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1.5)
                        .background(Capsule().fill(Color.primary.opacity(0.06)))

                    if hasConflict {
                        conflictBadge
                    }
                }

                if let proj = store.project(byId: task.projectId) {
                    Text(proj.title)
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            HStack(spacing: 6) {
                Button("+15m") {
                    store.shiftTaskTimeblock(task, deltaMinutes: 15)
                }
                .controlSize(.small)
                .buttonStyle(.bordered)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip(store.tr("macos_tooltip_shift_15m"))

                Button {
                    store.startFocusTimer(for: task)
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 9.5))
                        Text("\(task.durationMinutes ?? 25)m")
                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                    }
                    .fixedSize(horizontal: true, vertical: false)
                }
                .controlSize(.small)
                .buttonStyle(.borderedProminent)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip(store.tr("action_focus_timer"))

                Button {
                    store.setTaskTimeblock(task.id, startMinutes: nil, durationMinutes: nil)
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 14))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("v2_timeblock_clear"))
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .thingsCardSurface(cornerRadius: 11)
        .overlay(
            RoundedRectangle(cornerRadius: 11, style: .continuous)
                .strokeBorder(hasConflict ? ThingsMacPalette.somedayAmber.opacity(0.65) : Color.clear, lineWidth: 1.2)
        )
    }

    private func unscheduledTaskRow(_ task: TaskItem) -> some View {
        let isPopoverPresented = Binding<Bool>(
            get: { activePopoverTaskId == "unsched_\(task.id)" },
            set: { open in activePopoverTaskId = open ? "unsched_\(task.id)" : nil }
        )

        return HStack(spacing: 10) {
            ThingsCheckboxButton(
                status: task.status,
                isSomeday: false,
                helpText: store.tr("macos_checkbox_help"),
                onToggleComplete: { store.toggleTaskCompletion(task) },
                onCancelTask: { store.cancelTask(task) }
            )

            Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                .font(.system(size: 13.5, weight: .medium))
                .lineLimit(1)

            Spacer()

            ForEach([9, 11, 14, 16], id: \.self) { hour in
                let slotLabel = String(format: "%02d:00", hour)
                Button(slotLabel) {
                    store.setTaskTimeblock(
                        task.id,
                        startMinutes: hour * 60,
                        durationMinutes: task.durationMinutes ?? 30
                    )
                }
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .controlSize(.small)
                .buttonStyle(.bordered)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip("\(store.tr("macos_tooltip_timeblock")) — \(slotLabel)")
            }

            ThingsActionChipButton(
                title: store.tr("v2_timeblock_title"),
                systemImage: "clock.badge.checkmark",
                activeTint: ThingsMacPalette.anytimeTeal,
                helpText: store.tr("macos_tooltip_timeblock")
            ) {
                activePopoverTaskId = "unsched_\(task.id)"
            }
            .popover(isPresented: isPopoverPresented) {
                TimeblockPopoverView(
                    store: store,
                    currentStartMinutes: task.scheduledStartMinutes,
                    currentDurationMinutes: task.durationMinutes
                ) { sMins, dMins in
                    store.setTaskTimeblock(task.id, startMinutes: sMins, durationMinutes: dMins)
                }
            }
        }
        .padding(.horizontal, 13)
        .padding(.vertical, 9)
        .thingsCardSurface(cornerRadius: 10)
    }

    private var conflictBadge: some View {
        HStack(spacing: 3.5) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 9.5))
            Text(store.tr("v2_schedule_conflict"))
                .font(.system(size: 10.5, weight: .semibold))
        }
        .foregroundStyle(ThingsMacPalette.somedayAmber)
        .padding(.horizontal, 7)
        .padding(.vertical, 2)
        .background(Capsule().fill(ThingsMacPalette.somedayAmber.opacity(0.14)))
    }
}

// MARK: - Magic Plus Drag-to-Insert Slot Geometry & Drop Indicator

private struct MacMagicPlusSlotsPreferenceKey: PreferenceKey {
    static let defaultValue: [MacMagicPlusSlotGeometry] = []

    static func reduce(value: inout [MacMagicPlusSlotGeometry], nextValue: () -> [MacMagicPlusSlotGeometry]) {
        value.append(contentsOf: nextValue())
    }
}

private struct MacMagicPlusInsertionBar: View {
    let mode: MacMagicPlusDropMode
    let newTodoLabel: String
    let newHeadingLabel: String

    private var tint: Color {
        mode == .heading ? ThingsMacPalette.anytimeTeal : ThingsMacPalette.accentBlue
    }

    var body: some View {
        HStack(spacing: 8) {
            if mode == .heading {
                Image(systemName: "text.insert")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(tint)
                Text(newHeadingLabel)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(tint)
                Rectangle()
                    .fill(tint.opacity(0.55))
                    .frame(height: 2)
            } else {
                Circle()
                    .strokeBorder(tint, lineWidth: 2)
                    .background(Circle().fill(ThingsMacPalette.cardSurface))
                    .frame(width: 11, height: 11)
                RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                    .fill(tint)
                    .frame(height: 2.5)
                Text(newTodoLabel)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(tint)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(
            RoundedRectangle(cornerRadius: 7, style: .continuous)
                .fill(tint.opacity(0.08))
        )
        .allowsHitTesting(false)
    }
}

private struct MacMagicPlusSlotModifier: ViewModifier {
    let id: String
    let kind: MacMagicPlusSlotGeometry.Kind
    let activeDrop: MacMagicPlusResolvedDrop?
    let newTodoLabel: String
    let newHeadingLabel: String

    private var isTargeted: Bool {
        activeDrop?.indicatorSlotId == id
    }

    func body(content: Content) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if isTargeted, let drop = activeDrop, drop.indicatorEdge == .before {
                MacMagicPlusInsertionBar(
                    mode: drop.mode,
                    newTodoLabel: newTodoLabel,
                    newHeadingLabel: newHeadingLabel
                )
            }

            content
                .overlay {
                    if isTargeted, let drop = activeDrop, drop.indicatorEdge == .inside {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(
                                (drop.mode == .heading ? ThingsMacPalette.anytimeTeal : ThingsMacPalette.accentBlue).opacity(0.65),
                                style: StrokeStyle(lineWidth: 1.8, dash: [6, 4])
                            )
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill((drop.mode == .heading ? ThingsMacPalette.anytimeTeal : ThingsMacPalette.accentBlue).opacity(0.05))
                            )
                            .overlay(alignment: .top) {
                                MacMagicPlusInsertionBar(
                                    mode: drop.mode,
                                    newTodoLabel: newTodoLabel,
                                    newHeadingLabel: newHeadingLabel
                                )
                                .padding(12)
                            }
                            .allowsHitTesting(false)
                    }
                }
                .background(
                    GeometryReader { geo in
                        let frame = geo.frame(in: .named(MacMagicPlusCoordinateSpace.name))
                        Color.clear.preference(
                            key: MacMagicPlusSlotsPreferenceKey.self,
                            value: (frame.width > 4 && frame.height > 4)
                                ? [
                                    MacMagicPlusSlotGeometry(
                                        id: id,
                                        kind: kind,
                                        minX: Double(frame.minX),
                                        maxX: Double(frame.maxX),
                                        minY: Double(frame.minY),
                                        maxY: Double(frame.maxY)
                                    )
                                ]
                                : []
                        )
                    }
                )

            if isTargeted, let drop = activeDrop, drop.indicatorEdge == .after {
                MacMagicPlusInsertionBar(
                    mode: drop.mode,
                    newTodoLabel: newTodoLabel,
                    newHeadingLabel: newHeadingLabel
                )
            }
        }
        .animation(.spring(response: 0.20, dampingFraction: 0.84), value: isTargeted)
    }
}

private extension View {
    func magicPlusDropSlot(
        id: String,
        kind: MacMagicPlusSlotGeometry.Kind,
        activeDrop: MacMagicPlusResolvedDrop?,
        newTodoLabel: String,
        newHeadingLabel: String
    ) -> some View {
        modifier(
            MacMagicPlusSlotModifier(
                id: id,
                kind: kind,
                activeDrop: activeDrop,
                newTodoLabel: newTodoLabel,
                newHeadingLabel: newHeadingLabel
            )
        )
    }
}

// MARK: - Compact Grouped Calendar Appointments Block (Android & ChromeOS Parity)

private struct MacCompactCalendarEventsBlock: View {
    @ObservedObject var store: ThingsbookStore
    let events: [CalendarEventItem]

    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(MacCalendarAppointmentStyleMetrics.rowSpacing)) {
            ForEach(events) { event in
                MacCompactCalendarEventRow(store: store, event: event)
            }
        }
        .padding(.horizontal, CGFloat(MacCalendarAppointmentStyleMetrics.containerHorizontalPadding))
        .padding(.vertical, CGFloat(MacCalendarAppointmentStyleMetrics.containerVerticalPadding))
        .background(
            RoundedRectangle(cornerRadius: 9, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer)
                .overlay(
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.065), lineWidth: 0.75)
                )
        )
    }
}

private struct MacCompactCalendarEventRow: View {
    @ObservedObject var store: ThingsbookStore
    let event: CalendarEventItem

    @State private var isHovering = false

    private static let shortTimeFormatter: DateFormatter = {
        let df = DateFormatter()
        df.timeStyle = .short
        return df
    }()

    private var formattedTimeRange: String {
        let start = Self.shortTimeFormatter.string(from: DateEpochConverter.date(fromMillis: event.startMillis))
        let end = Self.shortTimeFormatter.string(from: DateEpochConverter.date(fromMillis: event.endMillis))
        return "\(start) – \(end)"
    }

    var body: some View {
        let calColor = ThingsMacPalette.colorFromArgb(event.colorHex)
        let trimmedCalendarName = event.calendarDisplayName.trimmingCharacters(in: .whitespacesAndNewlines)

        Button {
            AppleCalendarService.shared.openEventInAppleCalendar(event)
        } label: {
            HStack(spacing: 7.5) {
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(calColor)
                    .frame(
                        width: CGFloat(MacCalendarAppointmentStyleMetrics.colorBarWidth),
                        height: CGFloat(MacCalendarAppointmentStyleMetrics.colorBarHeight)
                    )

                if event.isAllDay {
                    Text(store.tr("calendar_all_day"))
                        .font(.system(size: CGFloat(MacCalendarAppointmentStyleMetrics.eventAllDayPillFontSize), weight: .semibold))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 5.5)
                        .padding(.vertical, 1)
                        .background(
                            RoundedRectangle(cornerRadius: 4.5, style: .continuous)
                                .fill(calColor.opacity(0.15))
                        )
                } else {
                    Text(formattedTimeRange)
                        .font(.system(size: CGFloat(MacCalendarAppointmentStyleMetrics.eventTimeFontSize), weight: .medium).monospacedDigit())
                        .foregroundStyle(.secondary)
                }

                Text(event.title.isEmpty ? store.tr("calendar_untitled_event") : event.title)
                    .font(.system(size: CGFloat(MacCalendarAppointmentStyleMetrics.eventTitleFontSize), weight: .regular))
                    .foregroundStyle(Color.primary.opacity(0.84))
                    .lineLimit(1)

                Spacer(minLength: 6)

                if !trimmedCalendarName.isEmpty {
                    Text(trimmedCalendarName)
                        .font(.system(size: CGFloat(MacCalendarAppointmentStyleMetrics.eventCalendarNameFontSize)))
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                        .frame(maxWidth: 115, alignment: .trailing)
                }
            }
            .padding(.horizontal, CGFloat(MacCalendarAppointmentStyleMetrics.rowHorizontalPadding))
            .padding(.vertical, CGFloat(MacCalendarAppointmentStyleMetrics.rowVerticalPadding))
            .background(
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(Color.primary.opacity(isHovering ? 0.045 : 0))
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovering = hovering
            }
        }
        .thingsTooltip(store.tr("macos_open_in_calendar"))
    }
}


