import SwiftUI
import ThingsbookCore

public struct SmartViewCanvas: View {
    @ObservedObject var store: ThingsbookStore
    public var isInspectorOpen: Bool = false

    private static let generalSectionRenameSentinel = "__general__"

    @State private var newHeadingTitle = ""
    @State private var isAddingHeading = false
    @State private var renamingHeadingId: String? = nil
    @State private var renameHeadingText = ""
    @State private var showProjectLoggedItems = false
    @FocusState private var isRenameHeadingFocused: Bool

    public init(store: ThingsbookStore, isInspectorOpen: Bool = false) {
        self.store = store
        self.isInspectorOpen = isInspectorOpen
    }

    private var isWideProjectWorkspace: Bool {
        switch store.selectedDestination {
        case .project(let projId):
            guard let project = store.project(byId: projId) else { return false }
            let mode = ProjectViewMode.normalized(project.viewMode)
            return mode == .board || mode == .roadmap
        case .area(let areaId):
            return store.areaViewMode(for: areaId) == .roadmap
        default:
            return false
        }
    }

    private var isTodayTimelineFullScreen: Bool {
        store.isTimelineFullScreen
            && store.selectedDestination == .smartView(.today)
            && store.todayViewMode == .timeline
    }

    private var isProjectRoadmapFullScreen: Bool {
        guard store.isTimelineFullScreen,
              case .project(let projId) = store.selectedDestination,
              let project = store.project(byId: projId) else {
            return false
        }
        return ProjectViewMode.normalized(project.viewMode) == .roadmap
    }

    private var isAreaRoadmapFullScreen: Bool {
        guard store.isTimelineFullScreen,
              case .area(let areaId) = store.selectedDestination else {
            return false
        }
        return store.areaViewMode(for: areaId) == .roadmap
    }

    private var isAnyTimelineFullScreen: Bool {
        isTodayTimelineFullScreen || isProjectRoadmapFullScreen || isAreaRoadmapFullScreen
    }

    public var body: some View {
        ScrollViewReader { scrollProxy in
            ScrollView {
                VStack(alignment: .leading, spacing: isAnyTimelineFullScreen ? 14 : 18) {
                    if !isAnyTimelineFullScreen {
                        // App Update Banner
                        if let update = store.availableUpdate {
                            updateAvailableBanner(update)
                        }

                        // Initial Sample Data Setup Banner
                        if store.hasSampleTasks && !store.preferences.hideSampleSetupBanner && store.syncSnapshot.userId == nil {
                            initialSampleSetupBanner
                        }
                    }

                    switch store.selectedDestination {
                    case .smartView(let sv):
                        smartViewContent(sv, scrollProxy: scrollProxy)
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
                            emptyPlaceholder(icon: "folder", text: store.tr("empty_view"))
                        }
                    case .perspective(let perspectiveId):
                        if let perspective = store.perspective(byId: perspectiveId) {
                            perspectiveCanvas(perspective)
                        } else {
                            emptyPlaceholder(icon: "line.3.horizontal.decrease.circle", text: store.tr("empty_view"))
                        }
                    }
                }
                .padding(.horizontal, isAnyTimelineFullScreen ? 20 : 26)
                .padding(.top, isAnyTimelineFullScreen ? 14 : 24)
                .padding(.bottom, isAnyTimelineFullScreen ? 22 : 96)
                .frame(maxWidth: (isWideProjectWorkspace || isAnyTimelineFullScreen || isInspectorOpen) ? .infinity : 1360, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .leading)
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
    }

    // MARK: - Smart Views

    @ViewBuilder
    private func smartViewContent(_ view: SmartView, scrollProxy: ScrollViewProxy) -> some View {
        if view == .today && store.todayViewMode == .timeline && store.isTimelineFullScreen {
            todayFullScreenHeaderBar
            todayCanvas
        } else {
            // Responsive Header (never wraps view title mid-word)
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .center, spacing: 12) {
                    smartViewTitleCluster(view)
                    Spacer(minLength: 12)
                    smartViewTrailingControls(view)
                }

                VStack(alignment: .leading, spacing: 10) {
                    smartViewTitleCluster(view)
                    HStack(alignment: .center, spacing: 8) {
                        smartViewTrailingControls(view)
                        Spacer(minLength: 0)
                    }
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
                upcomingCanvas(scrollProxy: scrollProxy)

            case .anytime:
                let list = store.filterTasksByActiveTag(store.anytimeTasks)
                if list.isEmpty {
                    emptyPlaceholder(icon: "safari", text: store.tr("empty_view"))
                } else {
                    groupedByContainerStack(list)
                }

            case .someday:
                let list = store.filterTasksByActiveTag(store.somedayTasks)
                if list.isEmpty {
                    emptyPlaceholder(icon: "cloud", text: store.tr("empty_view"))
                } else {
                    reorderableTaskStack(list)
                }

            case .logbook:
                logbookCanvas

            case .trash:
                trashCanvas
            }
        }
    }

    private func smartViewTitleCluster(_ view: SmartView) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: view.sfSymbol)
                .font(.system(size: 25, weight: .bold))
                .foregroundStyle(ThingsMacPalette.color(for: view, isSelected: true))

            Text(store.tr(view.titleKey))
                .font(.system(size: 28, weight: .bold))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)

            if view == .today {
                if let cap = store.todayCapacitySummary() {
                    todayCapacityBadge(cap.pillText)
                }
                planMyDayShortcutPill
            } else if view == .logbook {
                let focused7d = store.logbookFocusedMinutesLast7Days
                if focused7d > 0 {
                    logbookFocused7dBadge(
                        store.trf("v32_logbook_focused_7d", TaskItem.formatCompactDuration(focused7d))
                    )
                }
            }
        }
    }

    private func logbookFocused7dBadge(_ pillText: String) -> some View {
        HStack(spacing: 5) {
            Image(systemName: "clock.badge.checkmark")
                .font(.system(size: 11, weight: .semibold))
            Text(pillText)
                .font(.system(size: 11.5, weight: .semibold, design: .rounded))
                .lineLimit(1)
        }
        .fixedSize(horizontal: true, vertical: false)
        .foregroundStyle(ThingsMacPalette.logbookGreen)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(
            Capsule(style: .continuous)
                .fill(ThingsMacPalette.logbookGreen.opacity(0.12))
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(ThingsMacPalette.logbookGreen.opacity(0.28), lineWidth: 0.75)
                )
        )
    }

    private var planMyDayShortcutPill: some View {
        Button {
            store.openGeminiPlanMyDay()
        } label: {
            HStack(spacing: 5) {
                Image(systemName: "sparkles")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.geminiPurple)
                Text(store.tr("v31_gemini_plan_day_tab"))
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
            }
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 10.5)
            .padding(.vertical, 5)
            .background(
                Capsule(style: .continuous)
                    .fill(ThingsMacPalette.cardSurface)
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(ThingsMacPalette.geminiPurple.opacity(0.32), lineWidth: 0.85)
                    )
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .fixedSize(horizontal: true, vertical: false)
        .thingsTooltip(store.tr("v31_gemini_plan_day_desc"))
    }

    @ViewBuilder
    private func smartViewTrailingControls(_ view: SmartView) -> some View {
        HStack(alignment: .center, spacing: 8) {
            if view != .logbook && view != .trash {
                viewSortMenu
            }

            if view == .today {
                todayModePicker

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
                } else {
                    timelineFullScreenButton
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
    }

    private var todayFullScreenHeaderBar: some View {
        HStack(alignment: .center, spacing: 10) {
            Image(systemName: SmartView.today.sfSymbol)
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(ThingsMacPalette.color(for: .today, isSelected: true))

            Text(store.tr(SmartView.today.titleKey))
                .font(.system(size: 18, weight: .bold))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)

            if let cap = store.todayCapacitySummary() {
                todayCapacityBadge(cap.pillText)
            }

            Spacer(minLength: 12)

            timelineFullScreenButton
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer)
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.85)
                )
        )
    }

    private func todayCapacityBadge(_ pillText: String) -> some View {
        HStack(spacing: 5) {
            Image(systemName: "gauge.with.dots.needle.33percent")
                .font(.system(size: 11, weight: .semibold))
            Text(pillText)
                .font(.system(size: 11.5, weight: .semibold, design: .rounded))
                .lineLimit(1)
        }
        .fixedSize(horizontal: true, vertical: false)
        .foregroundStyle(ThingsMacPalette.accentBlue)
        .padding(.horizontal, 10.5)
        .padding(.vertical, 5)
        .background(
            Capsule(style: .continuous)
                .fill(ThingsMacPalette.accentBlue.opacity(0.12))
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.24), lineWidth: 0.8)
                )
        )
        .thingsTooltip(pillText)
    }

    private var todayModePicker: some View {
        Picker("", selection: $store.todayViewMode) {
            Text(store.tr("v2_list_view")).tag(TodayViewMode.list)
            Text(store.tr("v2_timeline_view")).tag(TodayViewMode.timeline)
        }
        .pickerStyle(.segmented)
        .labelsHidden()
        .fixedSize()
        .thingsTooltip("\(store.tr("v2_list_view")) / \(store.tr("v2_timeline_view"))")
    }

    private var timelineFullScreenButton: some View {
        let isFull = store.isTimelineFullScreen
        let label = isFull ? store.tr("v30_timeline_exit_fullscreen") : store.tr("v30_timeline_fullscreen")
        let icon = isFull ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right"

        return Button {
            store.toggleTimelineFullScreen()
        } label: {
            HStack(spacing: 5.5) {
                Image(systemName: icon)
                    .font(.system(size: 11, weight: .semibold))
                Text(label)
                    .font(.system(size: 12, weight: .semibold))
                    .lineLimit(1)
            }
            .fixedSize(horizontal: true, vertical: false)
            .foregroundStyle(isFull ? ThingsMacPalette.accentBlue : Color.primary.opacity(0.88))
            .padding(.horizontal, 11)
            .padding(.vertical, 5.5)
            .background(
                Capsule(style: .continuous)
                    .fill(isFull ? ThingsMacPalette.accentBlue.opacity(0.14) : Color.primary.opacity(0.055))
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(
                                isFull ? ThingsMacPalette.accentBlue.opacity(0.32) : Color.primary.opacity(0.09),
                                lineWidth: 0.8
                            )
                    )
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .fixedSize(horizontal: true, vertical: false)
        .keyboardShortcut("f", modifiers: [.command, .control])
        .thingsTooltip(isFull ? "\(label) (Esc)" : "\(label) (⌃⌘F)")
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
                emptyPlaceholder(icon: "sun.max", text: store.tr("empty_today"))
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

    // MARK: - Upcoming Timeline View (Tasks + Phase Milestones)

    private struct UpcomingPhaseMilestone: Identifiable {
        var id: String { heading.id }
        let heading: Heading
        let project: Project
        let epochDay: Int
        let completedCount: Int
        let totalCount: Int

        var progress: Double {
            guard totalCount > 0 else { return 0.0 }
            return min(1.0, max(0.0, Double(completedCount) / Double(totalCount)))
        }
    }

    private var upcomingPhaseMilestones: [UpcomingPhaseMilestone] {
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        let activeTagId = store.selectedFilterTagId
        var items: [UpcomingPhaseMilestone] = []

        for project in store.projects where project.status == .active && !project.isDeleted {
            let headings = store.headingsByProject[project.id] ?? []
            guard !headings.isEmpty else { continue }
            let projOpen = store.allOpenTasks.filter { $0.projectId == project.id }
            let projLogged = store.completedTasksForProject(project.id)

            for heading in headings where !heading.isDeleted && !heading.isArchived && !heading.title.isEmpty {
                guard let endMs = heading.endDate else { continue }
                let epochDay = DateEpochConverter.epochDay(forMillis: endMs)
                guard epochDay >= todayEpochDay else { continue }

                let openInPhase = projOpen.filter { $0.headingId == heading.id }
                let doneInPhase = projLogged.filter { $0.headingId == heading.id }

                if let tagId = activeTagId {
                    let matchesTag = project.tagIds.contains(tagId)
                        || openInPhase.contains(where: { $0.tagIds.contains(tagId) })
                        || doneInPhase.contains(where: { $0.tagIds.contains(tagId) })
                    if !matchesTag { continue }
                }

                items.append(
                    UpcomingPhaseMilestone(
                        heading: heading,
                        project: project,
                        epochDay: epochDay,
                        completedCount: doneInPhase.count,
                        totalCount: openInPhase.count + doneInPhase.count
                    )
                )
            }
        }
        return items.sorted { lhs, rhs in
            if lhs.epochDay != rhs.epochDay { return lhs.epochDay < rhs.epochDay }
            return lhs.heading.sortOrder < rhs.heading.sortOrder
        }
    }

    @ViewBuilder
    private func upcomingCanvas(scrollProxy: ScrollViewProxy) -> some View {
        let filtered = store.filterTasksByActiveTag(store.upcomingTasks)
        let milestones = upcomingPhaseMilestones
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        let groupedTasks = Dictionary(grouping: filtered) { task -> Int in
            guard let w = task.whenDate ?? (task.isMilestone ? task.deadline : nil) else { return Int.max }
            return max(todayEpochDay + 1, DateEpochConverter.epochDay(forMillis: w))
        }
        let groupedMilestones = Dictionary(grouping: milestones, by: \.epochDay)
        let sortedDays = Set(groupedTasks.keys).union(groupedMilestones.keys).sorted()
        let maxGroupEpochDay = sortedDays.last(where: { $0 < Int.max }) ?? todayEpochDay
        let clampedDaySpan = max(todayEpochDay, min(todayEpochDay + 365, maxGroupEpochDay)) - todayEpochDay + 1
        let forecastDaysCount = max(7, min(366, clampedDaySpan))
        let forecast = store.sevenDayWorkloadForecast(daysCount: forecastDaysCount)
        let workloadByEpochDay = Dictionary(forecast.map { ($0.epochDay, $0) }, uniquingKeysWith: { first, _ in first })

        Group {
            if filtered.isEmpty && milestones.isEmpty {
                emptyPlaceholder(icon: "calendar", text: store.tr("empty_view"))
            } else {
                VStack(alignment: .leading, spacing: 22) {
                    ForEach(sortedDays, id: \.self) { epochDay in
                        let dayTasks = TaskViewSorter.sort(groupedTasks[epochDay] ?? [], mode: store.preferences.viewSortMode)
                        let dayMilestones = groupedMilestones[epochDay] ?? []
                        let sampleMillis = dayTasks.first?.whenDate
                            ?? dayTasks.first?.deadline
                            ?? dayMilestones.first?.heading.endDate
                        VStack(alignment: .leading, spacing: 8) {
                            upcomingDayHeader(
                                sampleMillis: sampleMillis,
                                dayWorkload: workloadByEpochDay[epochDay]
                            )

                            let events = store.calendarEventsForEpochDay(
                                epochDay,
                                hasScheduledTasksOnDay: !dayTasks.isEmpty || !dayMilestones.isEmpty,
                                upcomingOnly: epochDay > todayEpochDay
                            )
                            if !events.isEmpty {
                                calendarEventsCard(events)
                            }

                            if !dayMilestones.isEmpty {
                                VStack(spacing: 6) {
                                    ForEach(dayMilestones) { milestone in
                                        upcomingPhaseMilestoneCard(milestone)
                                    }
                                }
                            }

                            if !dayTasks.isEmpty {
                                VStack(spacing: 3) {
                                    ForEach(Array(dayTasks.enumerated()), id: \.element.id) { idx, task in
                                        TaskRowCardView(store: store, task: task)
                                            .thingsTaskDraggable(store: store, task: task)
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
                        .id("upcoming-day-\(epochDay)")
                    }
                }
            }
        }
        .onAppear {
            consumePendingUpcomingScroll(sortedDays: sortedDays, scrollProxy: scrollProxy)
        }
        .onChange(of: store.pendingUpcomingScrollEpochDay) { _, _ in
            consumePendingUpcomingScroll(sortedDays: sortedDays, scrollProxy: scrollProxy)
        }
    }

    private func consumePendingUpcomingScroll(sortedDays: [Int], scrollProxy: ScrollViewProxy) {
        guard let target = store.pendingUpcomingScrollEpochDay else { return }
        store.pendingUpcomingScrollEpochDay = nil
        guard let resolvedDay = sortedDays.first(where: { $0 >= target }) ?? sortedDays.last else { return }
        DispatchQueue.main.async {
            withAnimation(.easeInOut(duration: 0.25)) {
                scrollProxy.scrollTo("upcoming-day-\(resolvedDay)", anchor: .top)
            }
        }
    }

    private func upcomingPhaseMilestoneCard(_ milestone: UpcomingPhaseMilestone) -> some View {
        Button {
            store.selectedDestination = .project(milestone.project.id)
        } label: {
            HStack(spacing: 11) {
                ZStack {
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(ThingsMacPalette.accentBlue.opacity(0.14))
                        .frame(width: 28, height: 28)
                    Image(systemName: "flag.checkered")
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(milestone.heading.title)
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text("\(store.tr("phase_milestone_badge")) · \(milestone.project.title)")
                        .font(.system(size: 11.5, weight: .medium))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .lineLimit(1)
                }

                Spacer()

                if milestone.totalCount > 0 {
                    let phaseInProgCount = store.allOpenTasks.filter {
                        $0.projectId == milestone.project.id
                            && $0.headingId == milestone.heading.id
                            && $0.status == .open
                            && $0.isInProgress
                    }.count
                    let phaseInProgRatio = min(
                        max(0.0, 1.0 - milestone.progress),
                        Double(phaseInProgCount) / Double(milestone.totalCount)
                    )
                    ProjectProgressRing(
                        progress: milestone.progress,
                        inProgressProgress: phaseInProgRatio,
                        size: 15,
                        tint: ThingsMacPalette.accentBlue,
                        isCompleted: milestone.completedCount == milestone.totalCount,
                        showFraction: true,
                        completedCount: milestone.completedCount,
                        totalCount: milestone.totalCount
                    )
                }

                Image(systemName: "chevron.right")
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 13)
            .padding(.vertical, 9)
            .background(
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .fill(ThingsMacPalette.accentBlue.opacity(0.06))
                    .overlay(
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.22), lineWidth: 0.85)
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func upcomingDayHeader(
        sampleMillis: Int64?,
        dayWorkload: DayWorkloadSummary? = nil
    ) -> some View {
        let date = sampleMillis.map { DateEpochConverter.date(fromMillis: $0) } ?? Date()
        let dayNum = Calendar.current.component(.day, from: date)
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.setLocalizedDateFormatFromTemplate("EEEEdMMMM")

        return HStack(alignment: .center, spacing: 9) {
            HStack(alignment: .firstTextBaseline, spacing: 9) {
                Text("\(dayNum)")
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                Text(df.string(from: date))
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.secondary)
            }

            Spacer()

            if let dayWorkload, dayWorkload.plannedTaskMinutes > 0 {
                let plannedFmt = TaskItem.formatCompactDuration(dayWorkload.plannedTaskMinutes)
                let pillText: String = {
                    if dayWorkload.isOverloaded {
                        return store.trf(
                            "v31_workload_overloaded_short",
                            TaskItem.formatCompactDuration(dayWorkload.overloadedByMinutes)
                        )
                    } else if store.preferences.calendarEventsEnabled {
                        let freeFmt = TaskItem.formatCompactDuration(dayWorkload.freeWorkdayMinutes)
                        return store.trf("v30_today_capacity_summary", plannedFmt, freeFmt)
                    } else {
                        return store.trf("v30_today_planned_only", plannedFmt)
                    }
                }()
                let pillColor = dayWorkload.isOverloaded
                    ? ThingsMacPalette.deadlineCrimson
                    : ThingsMacPalette.accentBlue

                Text(pillText)
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .foregroundStyle(pillColor)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 3)
                    .background(
                        Capsule(style: .continuous)
                            .fill(pillColor.opacity(0.12))
                    )
                    .lineLimit(1)
            }
        }
        .padding(.bottom, 4)
        .overlay(alignment: .bottom) {
            Divider().opacity(0.65)
        }
    }

    // MARK: - Project View (Linear Progress Bar, Markdown Notes, Action Pills, Phases, Drag & Drop)

    @ViewBuilder
    private func projectCanvas(_ project: Project) -> some View {
        let openTasks = store.tasksForProject(project.id)
        let headings = store.headingsByProject[project.id] ?? []
        let validHeadingIds = Set(headings.map(\.id))
        let unheaded = openTasks.filter { task in
            guard let hid = task.headingId else { return true }
            return !validHeadingIds.contains(hid)
        }
        let logged = store.completedTasksForProject(project.id)
        let projectMode = ProjectViewMode.normalized(project.viewMode)

        if projectMode == .roadmap && store.isTimelineFullScreen {
            ProjectRoadmapWorkspaceView(
                store: store,
                project: project,
                openTasks: openTasks,
                completedTasks: logged,
                headings: headings
            )
        } else {
            VStack(alignment: .leading, spacing: 14) {
                // Row 1: Generous Full-Width Project Title Row (never breaks mid-word)
                let isAllProjectTasksDone = project.totalTaskCount > 0 && project.completedTaskCount >= project.totalTaskCount
                HStack(alignment: .center, spacing: 12) {
                    ProjectProgressRing(
                        progress: project.progress,
                        inProgressProgress: project.inProgressProgress,
                        size: 22,
                        tint: ThingsMacPalette.accentBlue,
                        isCompleted: project.status == .completed,
                        showFraction: project.totalTaskCount > 0,
                        completedCount: project.completedTaskCount,
                        totalCount: project.totalTaskCount
                    )
                    .fixedSize()

                    Text(project.title)
                        .font(.system(size: 27, weight: .bold))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .layoutPriority(1)
                        .thingsTooltip(project.title)

                    Spacer(minLength: 8)

                    HStack(spacing: 6) {
                        if project.status != .completed {
                            Button {
                                store.requestCompleteProject(project)
                            } label: {
                                HStack(spacing: 5) {
                                    Image(systemName: "checkmark.circle")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(isAllProjectTasksDone ? ThingsMacPalette.logbookGreen : ThingsMacPalette.accentBlue)
                                    Text(store.tr("v31_project_complete_btn"))
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(isAllProjectTasksDone ? ThingsMacPalette.logbookGreen : .primary)
                                        .lineLimit(1)
                                }
                                .padding(.horizontal, 10)
                                .frame(height: 28)
                                .background(
                                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                        .fill(
                                            isAllProjectTasksDone
                                                ? ThingsMacPalette.logbookGreen.opacity(0.14)
                                                : Color.primary.opacity(0.055)
                                        )
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                        .strokeBorder(
                                            isAllProjectTasksDone
                                                ? ThingsMacPalette.logbookGreen.opacity(0.35)
                                                : Color.primary.opacity(0.08),
                                            lineWidth: 0.75
                                        )
                                )
                                .contentShape(RoundedRectangle(cornerRadius: 7.5, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .fixedSize()
                            .thingsTooltip(store.tr("macos_complete_project"))
                        }

                        Button {
                            store.editingProject = project
                        } label: {
                            Image(systemName: "pencil")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.secondary)
                                .frame(width: 28, height: 28)
                                .background(
                                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                        .fill(Color.primary.opacity(0.055))
                                )
                                .contentShape(RoundedRectangle(cornerRadius: 7.5, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .thingsTooltip(store.tr("project_edit_title"))

                        Menu {
                            Button(store.tr("project_edit_title")) {
                                store.editingProject = project
                            }
                            Button(store.tr("macos_complete_project")) {
                                store.requestCompleteProject(project)
                            }
                            Button(store.tr("v2_mark_reviewed")) {
                                store.markProjectReviewed(project.id)
                            }
                            Button(store.tr("action_duplicate_project")) {
                                if let copy = try? store.database.duplicateProject(project.id, copySuffix: store.tr("v25_copy_suffix")) {
                                    store.selectedDestination = .project(copy.id)
                                }
                            }
                            Button(store.tr("v2_save_as_template")) {
                                store.saveProjectAsTemplate(project.id)
                            }
                            Divider()
                            Button(store.tr("project_move_up")) {
                                store.moveProjectOrder(project, direction: -1)
                            }
                            Button(store.tr("project_move_down")) {
                                store.moveProjectOrder(project, direction: 1)
                            }
                            Menu(store.tr("v30_external_export_section")) {
                                Button(store.tr("v30_export_asana_csv")) {
                                    store.exportProjectExternalInteractively(projectId: project.id, format: .asanaCsv)
                                }
                                Button(store.tr("v30_export_asana_json")) {
                                    store.exportProjectExternalInteractively(projectId: project.id, format: .asanaJson)
                                }
                                Divider()
                                Button(store.tr("v30_export_planner_xlsx")) {
                                    store.exportProjectExternalInteractively(projectId: project.id, format: .plannerXlsx)
                                }
                                Button(store.tr("v30_export_planner_csv")) {
                                    store.exportProjectExternalInteractively(projectId: project.id, format: .plannerCsv)
                                }
                                Divider()
                                Button(store.tr("v30_external_import_export_tab")) {
                                    store.templatesInitialTab = .externalImportExport
                                    store.isTemplatesSheetPresented = true
                                }
                            }
                            Divider()
                            Button(role: .destructive) {
                                store.deleteProjectWithUndo(project)
                            } label: {
                                Text(store.tr("macos_delete_project"))
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(.secondary)
                                .frame(width: 28, height: 28)
                                .background(
                                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                        .fill(Color.primary.opacity(0.055))
                                )
                        }
                        .menuStyle(.borderlessButton)
                        .menuIndicator(.hidden)
                        .fixedSize()
                        .thingsTooltip(store.tr("macos_tooltip_project_options"))
                    }
                    .fixedSize()
                }

                // Row 2: Mode Switcher + Sort + Stalled Status Badge
                HStack(alignment: .center, spacing: 10) {
                    ProjectModeSegmentedSwitcher(store: store, project: project)

                    viewSortMenu

                    if project.status == .active && project.inProgressTaskCount > 0 {
                        Text("◐ \(store.trf("v33_project_in_progress_count", project.inProgressTaskCount))")
                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .foregroundStyle(ThingsMacPalette.somedayAmber)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4.5)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(ThingsMacPalette.somedayAmber.opacity(0.14))
                            )
                            .overlay(
                                Capsule(style: .continuous)
                                    .strokeBorder(ThingsMacPalette.somedayAmber.opacity(0.28), lineWidth: 0.75)
                            )
                    }

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
                                    .lineLimit(1)
                            }
                            .fixedSize(horizontal: true, vertical: false)
                            .foregroundStyle(ThingsMacPalette.somedayAmber)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4.5)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(ThingsMacPalette.somedayAmber.opacity(0.14))
                            )
                        }
                        .menuStyle(.borderlessButton)
                        .fixedSize()
                        .thingsTooltip(badgeLabel)
                    }

                    Spacer(minLength: 0)
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
                            .padding(.horizontal, 11)
                            .padding(.vertical, 5)
                            .background(
                                Capsule(style: .continuous)
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
                                Image(systemName: "folder")
                                    .font(.system(size: 11, weight: .semibold))
                                Text("\(store.tr("entity_area")): \(area.title)")
                                    .font(.system(size: 12, weight: .semibold))
                                    .lineLimit(1)
                            }
                            .fixedSize(horizontal: true, vertical: false)
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                            .padding(.horizontal, 11)
                            .padding(.vertical, 5)
                            .background(
                                Capsule(style: .continuous)
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
                        systemImage: "flag.checkered",
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

                tagFilterBar(for: openTasks)

                switch projectMode {
                case .list:
                    if openTasks.isEmpty && headings.isEmpty {
                        emptyPlaceholder(icon: "checkmark.circle", text: store.tr("empty_view"))
                    } else {
                        if !headings.isEmpty {
                            if !unheaded.isEmpty {
                                projectGeneralDropSection(project: project, unheadedTasks: unheaded)
                            }
                        } else if !unheaded.isEmpty {
                            reorderableTaskStack(
                                unheaded,
                                showSubtitle: false,
                                projectId: project.id,
                                targetHeadingId: .some(nil)
                            )
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

                case .board:
                    ProjectBoardWorkspaceView(
                        store: store,
                        project: project,
                        openTasks: openTasks,
                        headings: headings
                    )

                case .roadmap:
                    ProjectRoadmapWorkspaceView(
                        store: store,
                        project: project,
                        openTasks: openTasks,
                        completedTasks: logged,
                        headings: headings
                    )
                }

                // Logged Items toggle
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
                                    .thingsTaskDraggable(store: store, task: t)
                            }
                        }
                    }
                }
            }
        }
    }

    private func projectGeneralDropSection(
        project: Project,
        unheadedTasks: [TaskItem]
    ) -> some View {
        let isRenamingGeneral = renamingHeadingId == Self.generalSectionRenameSentinel

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Capsule(style: .continuous)
                    .fill(ThingsMacPalette.inboxBlue)
                    .frame(width: 3.5, height: 16)

                if isRenamingGeneral {
                    TextField(store.tr("entity_new_heading"), text: $renameHeadingText)
                        .textFieldStyle(.roundedBorder)
                        .frame(maxWidth: 260)
                        .focused($isRenameHeadingFocused)
                        .onSubmit {
                            commitGeneralSectionRename(projectId: project.id)
                        }
                    Button(store.tr("action_save")) {
                        commitGeneralSectionRename(projectId: project.id)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                    Button(store.tr("action_cancel")) {
                        renamingHeadingId = nil
                    }
                    .controlSize(.small)
                } else {
                    Text(store.tr("v30_board_ungrouped_column"))
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(.secondary)

                    if !unheadedTasks.isEmpty {
                        Text("\(unheadedTasks.count)")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 6.5)
                            .padding(.vertical, 1.5)
                            .background(Capsule().fill(Color.primary.opacity(0.055)))
                    }
                }

                Spacer(minLength: 4)

                Button {
                    store.createNewTaskInCurrentContext(preferredHeadingId: nil)
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("entity_new_todo"))

                if !unheadedTasks.isEmpty {
                    Button {
                        renameHeadingText = store.tr("v30_board_ungrouped_column")
                        renamingHeadingId = Self.generalSectionRenameSentinel
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
                        if renamingHeadingId == Self.generalSectionRenameSentinel {
                            renamingHeadingId = nil
                        }
                        store.deleteTasks(Set(unheadedTasks.map(\.id)))
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 11.5))
                            .foregroundStyle(ThingsMacPalette.deadlineCrimson.opacity(0.85))
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(store.tr("action_delete"))
                }
            }

            if !unheadedTasks.isEmpty {
                reorderableTaskStack(
                    unheadedTasks,
                    showSubtitle: false,
                    projectId: project.id,
                    targetHeadingId: .some(nil)
                )
            }
        }
        .padding(.horizontal, 13)
        .padding(.vertical, unheadedTasks.isEmpty ? 8 : 11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer.opacity(unheadedTasks.isEmpty ? 0.42 : 0.68))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.065), lineWidth: 0.8)
                )
        )
        .thingsDropTarget(cornerRadius: 14) { payloads in
            store.dropTasksIntoProjectSection(
                payloads,
                projectId: project.id,
                headingId: nil,
                beforeTaskId: nil
            )
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
        let rawOpenTasks = store.allOpenTasks.filter { $0.projectId == project.id && $0.headingId == heading.id }
        let rawOpenCount = rawOpenTasks.count
        let inProgCount = rawOpenTasks.filter { $0.status == .open && $0.isInProgress }.count
        let doneCount = store.completedTasksForProject(project.id).filter { $0.headingId == heading.id }.count
        let totalPhaseCount = rawOpenCount + doneCount
        let phaseProgress = totalPhaseCount > 0 ? Double(doneCount) / Double(totalPhaseCount) : 0.0
        let phaseInProgress = totalPhaseCount > 0 ? min(max(0.0, 1.0 - phaseProgress), Double(inProgCount) / Double(totalPhaseCount)) : 0.0

        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 8) {
                Capsule(style: .continuous)
                    .fill(ThingsMacPalette.accentBlue)
                    .frame(width: 3.5, height: 17)
                    .padding(.top, 2)

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
                        HStack(alignment: .firstTextBaseline, spacing: 6) {
                            Text(heading.title)
                                .font(.system(size: 14.5, weight: .bold))
                                .foregroundStyle(.primary)
                                .lineLimit(nil)
                                .multilineTextAlignment(.leading)
                                .fixedSize(horizontal: false, vertical: true)
                            if isCollapsed && !tasks.isEmpty {
                                Text("(\(tasks.count))")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundStyle(.secondary)
                                    .fixedSize()
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .layoutPriority(1)

                    if totalPhaseCount > 0 {
                        ProjectProgressRing(
                            progress: phaseProgress,
                            inProgressProgress: phaseInProgress,
                            size: 13.5,
                            tint: ThingsMacPalette.accentBlue,
                            isCompleted: rawOpenCount == 0 && doneCount > 0,
                            showFraction: true,
                            completedCount: doneCount,
                            totalCount: totalPhaseCount
                        )
                        .fixedSize()
                        .padding(.top, 2)
                    }

                    PhaseStartDatePillButton(
                        store: store,
                        heading: heading
                    )
                    .fixedSize()

                    PhaseEndDatePillButton(
                        store: store,
                        heading: heading,
                        hasOpenTasks: rawOpenCount > 0
                    )
                    .fixedSize()
                }

                Spacer(minLength: 4)

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

            if !isCollapsed && !tasks.isEmpty {
                reorderableTaskStack(
                    tasks,
                    showSubtitle: false,
                    projectId: project.id,
                    targetHeadingId: .some(heading.id)
                )
            }
        }
        .padding(.horizontal, 13)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer.opacity(0.68))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.065), lineWidth: 0.8)
                )
        )
        .padding(.top, 4)
        .thingsDropTarget(cornerRadius: 14) { payloads in
            store.dropTasksIntoProjectSection(
                payloads,
                projectId: project.id,
                headingId: heading.id,
                beforeTaskId: nil
            )
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

    private func commitGeneralSectionRename(projectId: String) {
        let clean = renameHeadingText.trimmingCharacters(in: .whitespacesAndNewlines)
        defer { renamingHeadingId = nil }
        guard !clean.isEmpty else { return }
        _ = store.promoteUngroupedTasksToHeading(projectId: projectId, title: clean)
    }

    // MARK: - Area View

    @ViewBuilder
    private func areaCanvas(_ area: Area) -> some View {
        let areaMode = store.areaViewMode(for: area.id)

        if areaMode == .roadmap && store.isTimelineFullScreen {
            AreaPortfolioRoadmapWorkspaceView(store: store, area: area)
        } else {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 12) {
                    Image(systemName: "folder.fill")
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

                HStack(alignment: .center, spacing: 10) {
                    AreaModeSegmentedSwitcher(store: store, area: area)

                    if areaMode != .roadmap {
                        viewSortMenu
                    }

                    Spacer(minLength: 0)
                }

                if areaMode == .roadmap {
                    AreaPortfolioRoadmapWorkspaceView(store: store, area: area)
                } else {
                    let areaProjects = store.projectsForArea(area.id)
                    let looseTasks = store.looseTasksForArea(area.id)

                    if !areaProjects.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(areaProjects) { proj in
                                Button {
                                    store.selectedDestination = .project(proj.id)
                                } label: {
                                    HStack(spacing: 12) {
                                        ProjectProgressRing(
                                            progress: proj.progress,
                                            inProgressProgress: proj.inProgressProgress,
                                            size: 18,
                                            showFraction: proj.totalTaskCount > 0,
                                            completedCount: proj.completedTaskCount,
                                            totalCount: proj.totalTaskCount
                                        )
                                        Text(proj.title)
                                            .font(.system(size: 14.5, weight: .semibold))
                                        Spacer()
                                        if proj.inProgressTaskCount > 0 {
                                            Text("◐ \(store.trf("v33_project_in_progress_count", proj.inProgressTaskCount))")
                                                .font(.system(size: 11, weight: .semibold, design: .rounded))
                                                .foregroundStyle(ThingsMacPalette.somedayAmber)
                                                .padding(.horizontal, 7.5)
                                                .padding(.vertical, 2.5)
                                                .background(
                                                    Capsule(style: .continuous)
                                                        .fill(ThingsMacPalette.somedayAmber.opacity(0.13))
                                                )
                                        }
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
                                    .thingsCardSurface(cornerRadius: 12)
                                }
                                .buttonStyle(.plain)
                                .thingsDropTarget(cornerRadius: 12) { payloads in
                                    store.dropTasks(payloads, onto: .project(proj.id))
                                }
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
                                        if let copy = try? store.database.duplicateProject(proj.id, copySuffix: store.tr("v25_copy_suffix")) {
                                            store.selectedDestination = .project(copy.id)
                                        }
                                    }
                                    Button(store.tr("macos_complete_project")) {
                                        store.requestCompleteProject(proj)
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
                        emptyPlaceholder(icon: "folder", text: store.tr("empty_view"))
                    }
                }
            }
        }
    }

    // MARK: - Custom Saved Perspective View (v3.0)

    @ViewBuilder
    private func perspectiveCanvas(_ perspective: SavedPerspective) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: "line.3.horizontal.decrease.circle.fill")
                    .font(.system(size: 25, weight: .bold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                Text(perspective.title)
                    .font(.system(size: 28, weight: .bold))

                Spacer()

                viewSortMenu

                Button {
                    store.editingPerspective = perspective
                    store.isPerspectiveEditorPresented = true
                } label: {
                    Label(store.tr("v30_perspective_edit"), systemImage: "slider.horizontal.3")
                        .font(.system(size: 12, weight: .medium))
                        .lineLimit(1)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip(store.tr("v30_perspective_edit"))

                Menu {
                    Button(store.tr("v30_perspective_edit")) {
                        store.editingPerspective = perspective
                        store.isPerspectiveEditorPresented = true
                    }
                    Divider()
                    Button(role: .destructive) {
                        store.deletePerspective(perspective.id)
                    } label: {
                        Text(store.tr("v30_perspective_delete"))
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.system(size: 15))
                }
                .menuStyle(.borderlessButton)
                .fixedSize()
            }

            let summaryBadges = perspectiveFilterSummaryBadges(perspective)
            if !summaryBadges.isEmpty {
                ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                    ForEach(summaryBadges, id: \.self) { badge in
                        Text(badge)
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                            .padding(.horizontal, 9.5)
                            .padding(.vertical, 4)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(ThingsMacPalette.accentBlue.opacity(0.11))
                            )
                    }
                }
            }

            let tasks = store.tasksForPerspective(perspective)
            tagFilterBar(for: tasks)

            if tasks.isEmpty {
                emptyPlaceholder(icon: "line.3.horizontal.decrease.circle", text: store.tr("empty_view"))
            } else {
                reorderableTaskStack(tasks)
            }
        }
    }

    private func perspectiveFilterSummaryBadges(_ perspective: SavedPerspective) -> [String] {
        var badges: [String] = []
        let dw = perspective.dueWindow.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        switch dw {
        case "TODAY": badges.append(store.tr("view_today"))
        case "UPCOMING": badges.append(store.tr("view_upcoming"))
        case "ANYTIME": badges.append(store.tr("view_anytime"))
        case "SOMEDAY": badges.append(store.tr("view_someday"))
        default: break
        }
        if let effort = TaskEffortLevel.normalized(perspective.effortLevel) {
            switch effort {
            case .quickWin: badges.append(store.tr("v30_effort_quick_win"))
            case .medium: badges.append(store.tr("v30_effort_medium"))
            case .deepFocus: badges.append(store.tr("v30_effort_deep_focus"))
            }
        }
        if let maxDur = perspective.maxDurationMinutes, maxDur > 0 {
            badges.append("≤ \(TaskItem.formatCompactDuration(maxDur))")
        }
        if let aid = perspective.areaId, let area = store.area(byId: aid) {
            badges.append("\(store.tr("entity_area")): \(area.title)")
        }
        for cid in perspective.categoryIds {
            if let tag = store.tags.first(where: { $0.id == cid }) {
                badges.append("#\(tag.title)")
            }
        }
        if perspective.actionableOnly {
            badges.append(store.tr("v30_perspective_actionable_only"))
        }
        if perspective.inProgressOnly {
            badges.append("◐ \(store.tr("v33_filter_in_progress_only"))")
        }
        return badges
    }

    private var viewSortMenu: some View {
        let currentMode = store.preferences.viewSortMode.lowercased()
        let activeLabel: String = {
            switch currentMode {
            case "deadline": return store.tr("v25_view_sort_deadline")
            case "duration": return store.tr("v25_view_sort_duration")
            case "effort": return store.tr("v30_view_sort_effort")
            case "alpha": return store.tr("v25_view_sort_alpha")
            default: return store.tr("v25_view_sort_manual")
            }
        }()
        let isCustomSort = currentMode != "manual" && !currentMode.isEmpty

        return Menu {
            let options: [(id: String, label: String)] = [
                ("manual", store.tr("v25_view_sort_manual")),
                ("deadline", store.tr("v25_view_sort_deadline")),
                ("duration", store.tr("v25_view_sort_duration")),
                ("effort", store.tr("v30_view_sort_effort")),
                ("alpha", store.tr("v25_view_sort_alpha")),
            ]
            ForEach(options, id: \.id) { opt in
                let selected = (currentMode == opt.id) || (opt.id == "manual" && !isCustomSort)
                Button {
                    store.preferences.viewSortMode = opt.id
                } label: {
                    if selected {
                        Label(opt.label, systemImage: "checkmark")
                    } else {
                        Text(opt.label)
                    }
                }
            }
        } label: {
            HStack(spacing: 5) {
                Image(systemName: "arrow.up.arrow.down")
                    .font(.system(size: 11, weight: .semibold))
                Text(isCustomSort ? activeLabel : store.tr("v25_view_sort_label"))
                    .font(.system(size: 12, weight: .semibold))
                    .lineLimit(1)
            }
            .fixedSize(horizontal: true, vertical: false)
            .foregroundStyle(isCustomSort ? ThingsMacPalette.accentBlue : .secondary)
            .padding(.horizontal, 11)
            .padding(.vertical, 5.5)
            .background(
                Capsule(style: .continuous)
                    .fill(isCustomSort ? ThingsMacPalette.accentBlue.opacity(0.13) : Color.primary.opacity(0.055))
            )
        }
        .menuStyle(.borderlessButton)
        .menuIndicator(.hidden)
        .fixedSize()
        .thingsTooltip("\(store.tr("v25_view_sort_label")): \(activeLabel)")
    }

    // MARK: - Logbook & Trash Views

    @ViewBuilder
    private var logbookCanvas: some View {
        let filtered = store.filterTasksByActiveTag(store.logbookTasks)
        if filtered.isEmpty {
            emptyPlaceholder(icon: "clock.arrow.circlepath", text: store.tr("empty_view"))
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
                            .thingsTaskDraggable(store: store, task: task)
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
                                Image(systemName: "list.bullet.rectangle")
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
                            .thingsCardSurface(cornerRadius: 10, isSelected: store.selectedTaskIds.contains(task.id))
                            .onTapGesture {
                                store.handleRowClick(taskId: task.id, modifiers: NSEvent.modifierFlags)
                            }
                            .thingsTaskDraggable(store: store, task: task)
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
        projectId: String? = nil,
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
                    .thingsTaskDraggable(store: store, task: task)
                    .thingsDropTarget(cornerRadius: 10) { droppedPayloads in
                        store.dropTasksBeforeTask(
                            droppedPayloads,
                            targetTask: task,
                            in: tasks,
                            projectId: projectId,
                            targetHeadingId: targetHeadingId
                        )
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

        return VStack(alignment: .leading, spacing: 14) {
            ForEach(sortedKeys, id: \.self) { groupName in
                let groupTasks = TaskViewSorter.sort(grouped[groupName] ?? [], mode: store.preferences.viewSortMode)
                VStack(alignment: .leading, spacing: 7) {
                    HStack(spacing: 7) {
                        Capsule(style: .continuous)
                            .fill(ThingsMacPalette.accentBlue)
                            .frame(width: 3, height: 14)
                        Text(groupName)
                            .font(.system(size: 13.5, weight: .bold))
                            .foregroundStyle(.primary)
                        Spacer()
                    }
                    ForEach(Array(groupTasks.enumerated()), id: \.element.id) { idx, task in
                        TaskRowCardView(store: store, task: task, showContextSubtitle: false)
                            .thingsTaskDraggable(store: store, task: task)
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
                .padding(.horizontal, 13)
                .padding(.vertical, 11)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(ThingsMacPalette.subtleContainer.opacity(0.65))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .strokeBorder(Color.primary.opacity(0.065), lineWidth: 0.8)
                        )
                )
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(.tertiary)
                .frame(width: 34, height: 34)
                .background(
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(Color.primary.opacity(0.045))
                )

            Text(text)
                .font(.system(size: 13.5, weight: .regular))
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(maxWidth: 520, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 13, style: .continuous)
                .fill(ThingsMacPalette.cardSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.075), lineWidth: 0.85)
                )
        )
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 4)
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
            let eMins = min(1440, max(sMins + 15, rawEndMins))
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
            let sMins = min(1435, max(0, task.scheduledStartMinutes ?? 540))
            let eMins = min(1440, sMins + task.effectiveDurationMinutes)
            slots.append(
                TimedScheduleSlot(
                    id: "task_\(task.id)",
                    startMinutes: sMins,
                    endMinutes: min(1440, max(sMins + 5, eMins)),
                    kind: .task(task)
                )
            )
        }

        return slots.sorted { lhs, rhs in
            if lhs.startMinutes != rhs.startMinutes { return lhs.startMinutes < rhs.startMinutes }
            return lhs.endMinutes < rhs.endMinutes
        }
    }

    private func formatTimelineEndMinutes(_ endMinutes: Int) -> String {
        if endMinutes >= 1440 {
            return "24:00"
        }
        return TaskItem.formatMinutesOfDay(endMinutes)
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
        let isFullScreen = store.isTimelineFullScreen
        let slots = timedSlots
        let conflicts = conflictingSlotIds
        let visibleUnscheduled = isFullScreen ? [] : unscheduledTasks

        VStack(alignment: .leading, spacing: 18) {
            if !allDayEvents.isEmpty {
                MacCompactCalendarEventsBlock(store: store, events: allDayEvents)
            }

            if slots.isEmpty && visibleUnscheduled.isEmpty && (!isFullScreen || allDayEvents.isEmpty) {
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

                if !visibleUnscheduled.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 7) {
                            Image(systemName: "tray.and.arrow.down.fill")
                                .font(.system(size: 12.5, weight: .semibold))
                                .foregroundStyle(ThingsMacPalette.todayYellow)
                            Text(store.tr("v2_unscheduled_today"))
                                .font(.system(size: 14, weight: .bold))
                            Text("\(visibleUnscheduled.count)")
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(Color.primary.opacity(0.06)))
                            Spacer()
                        }
                        .padding(.top, slots.isEmpty ? 0 : 8)

                        Divider().opacity(0.65)

                        ForEach(visibleUnscheduled) { task in
                            unscheduledTaskRow(task)
                        }
                    }
                }
            }
        }
        .onChange(of: store.isTimelineFullScreen) { _, fullScreen in
            if fullScreen {
                activePopoverTaskId = nil
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
            guard !store.isTimelineFullScreen else { return }
            AppleCalendarService.shared.openEventInAppleCalendar(ev)
        } label: {
            HStack(spacing: 10) {
                Text("\(TaskItem.formatMinutesOfDay(startMinutes))–\(formatTimelineEndMinutes(endMinutes))")
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
        .disabled(store.isTimelineFullScreen)
        .allowsHitTesting(!store.isTimelineFullScreen)
        .thingsTooltip(store.isTimelineFullScreen ? "" : store.tr("macos_open_in_calendar"))
    }

    @ViewBuilder
    private func timelineTaskRow(
        _ task: TaskItem,
        startMinutes: Int,
        endMinutes: Int,
        hasConflict: Bool
    ) -> some View {
        let card = timelineTaskRowCard(
            task,
            startMinutes: startMinutes,
            endMinutes: endMinutes,
            hasConflict: hasConflict
        )
        if store.isTimelineFullScreen {
            card
        } else {
            card
                .thingsInstantTaskTap(
                    onSingleClick: { modifiers in
                        store.handleRowClick(taskId: task.id, modifiers: modifiers)
                    },
                    onDoubleClick: {
                        store.openTaskModal(task.id)
                    }
                )
                .contextMenu {
                    Button {
                        store.openTaskModal(task.id)
                    } label: {
                        Label("\(store.tr("action_more_options"))…", systemImage: "slider.horizontal.3")
                    }
                    Divider()
                    Button(task.status == .completed ? store.tr("action_reopen_todo") : store.tr("action_complete")) {
                        store.toggleTaskCompletion(task)
                    }
                    Button {
                        store.setTasksMilestone([task.id], isMilestone: !task.isMilestone)
                    } label: {
                        Label(
                            store.tr(task.isMilestone ? "v30_remove_milestone" : "v30_mark_as_milestone"),
                            systemImage: task.isMilestone ? "diamond.fill" : "diamond"
                        )
                    }
                    if task.status == .open {
                        Button {
                            store.toggleTaskInProgress(task)
                        } label: {
                            Label(
                                store.tr(task.isInProgress ? "v33_clear_in_progress" : "v33_mark_in_progress"),
                                systemImage: "circle.lefthalf.filled"
                            )
                        }
                    }
                    Button {
                        store.copyTaskToPasteboard(task)
                    } label: {
                        Label(store.tr("action_copy_todo"), systemImage: "doc.on.doc")
                    }
                    Divider()
                    Button(role: .destructive) {
                        store.deleteTask(task)
                    } label: {
                        Text(store.tr("action_delete"))
                    }
                }
                .thingsTaskDraggable(store: store, task: task)
        }
    }

    private func timelineTaskRowCard(
        _ task: TaskItem,
        startMinutes: Int,
        endMinutes: Int,
        hasConflict: Bool
    ) -> some View {
        let isFullScreen = store.isTimelineFullScreen
        let timeLabelText = "\(TaskItem.formatMinutesOfDay(startMinutes))–\(formatTimelineEndMinutes(endMinutes))"
        let focusMinutes = min(max(1, task.durationMinutes ?? 25), 120)
        let isPopoverPresented = Binding<Bool>(
            get: { !isFullScreen && activePopoverTaskId == task.id },
            set: { open in activePopoverTaskId = (open && !isFullScreen) ? task.id : nil }
        )

        return HStack(spacing: 12) {
            if isFullScreen {
                Text(timeLabelText)
                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                    .foregroundStyle(task.isMilestone ? ThingsMacPalette.milestoneViolet : ThingsMacPalette.anytimeTeal)
                    .frame(width: 98, alignment: .leading)
            } else {
                Button {
                    activePopoverTaskId = task.id
                } label: {
                    Text(timeLabelText)
                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                        .foregroundStyle(task.isMilestone ? ThingsMacPalette.milestoneViolet : ThingsMacPalette.anytimeTeal)
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
            }

            ThingsCheckboxButton(
                status: task.status,
                isSomeday: false,
                isMilestone: task.isMilestone,
                isInProgress: task.isInProgress,
                helpText: isFullScreen ? "" : store.tr("macos_checkbox_help"),
                onToggleComplete: {
                    guard !store.isTimelineFullScreen else { return }
                    store.toggleTaskCompletion(task)
                },
                onCancelTask: {
                    guard !store.isTimelineFullScreen else { return }
                    store.cancelTask(task)
                },
                onToggleInProgress: {
                    guard !store.isTimelineFullScreen else { return }
                    store.toggleTaskInProgress(task)
                }
            )
            .disabled(isFullScreen)
            .allowsHitTesting(!isFullScreen)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 7) {
                    Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                        .font(.system(size: 14, weight: task.isMilestone ? .semibold : .medium))
                        .lineLimit(1)

                    if task.status == .open && task.isInProgress {
                        Text("◐ \(store.tr("v33_in_progress_badge"))")
                            .font(.system(size: 10.5, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.somedayAmber)
                            .padding(.horizontal, 6.5)
                            .padding(.vertical, 1.5)
                            .background(Capsule().fill(ThingsMacPalette.somedayAmber.opacity(0.14)))
                    }

                    if task.isMilestone {
                        milestoneTimelinePill
                    }

                    Text(TaskItem.formatCompactDuration(task.effectiveDurationMinutes))
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

            if !isFullScreen {
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
                            Text(TaskItem.formatCompactDuration(focusMinutes))
                                .font(.system(size: 11, weight: .semibold, design: .rounded))
                        }
                        .fixedSize(horizontal: true, vertical: false)
                    }
                    .controlSize(.small)
                    .buttonStyle(.borderedProminent)
                    .fixedSize(horizontal: true, vertical: false)
                    .thingsTooltip(store.tr("action_focus_timer"))

                    ThingsUtilityIconButton(
                        systemImage: "slider.horizontal.3",
                        tint: .secondary,
                        helpText: store.tr("action_more_options")
                    ) {
                        store.openTaskModal(task.id)
                    }

                    Button {
                        store.setTaskTimeblock(task.id, startMinutes: nil, durationMinutes: task.durationMinutes)
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 14))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(store.tr("v2_timeblock_clear"))
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .thingsCardSurface(cornerRadius: 11, isSelected: !isFullScreen && store.selectedTaskIds.contains(task.id))
        .overlay(
            RoundedRectangle(cornerRadius: 11, style: .continuous)
                .strokeBorder(
                    hasConflict
                        ? ThingsMacPalette.somedayAmber.opacity(0.65)
                        : (task.isMilestone ? ThingsMacPalette.milestoneViolet.opacity(0.52) : Color.clear),
                    lineWidth: (hasConflict || task.isMilestone) ? 1.2 : 1.0
                )
        )
        .contentShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
        .thingsTimelineMouseTooltip(
            todayTimelineTaskTooltipText(task, startMinutes: startMinutes, endMinutes: endMinutes),
            isSuppressed: activePopoverTaskId == task.id
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
                isMilestone: task.isMilestone,
                isInProgress: task.isInProgress,
                helpText: store.tr("macos_checkbox_help"),
                onToggleComplete: { store.toggleTaskCompletion(task) },
                onCancelTask: { store.cancelTask(task) },
                onToggleInProgress: { store.toggleTaskInProgress(task) }
            )

            Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                .font(.system(size: 13.5, weight: task.isMilestone ? .semibold : .medium))
                .lineLimit(1)

            if task.status == .open && task.isInProgress {
                Text("◐ \(store.tr("v33_in_progress_badge"))")
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.somedayAmber)
                    .padding(.horizontal, 6.5)
                    .padding(.vertical, 1.5)
                    .background(Capsule().fill(ThingsMacPalette.somedayAmber.opacity(0.14)))
            }

            if task.isMilestone {
                milestoneTimelinePill
            }

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

            ThingsUtilityIconButton(
                systemImage: "slider.horizontal.3",
                tint: .secondary,
                helpText: store.tr("action_more_options")
            ) {
                store.openTaskModal(task.id)
            }
        }
        .padding(.horizontal, 13)
        .padding(.vertical, 9)
        .thingsCardSurface(cornerRadius: 10, isSelected: store.selectedTaskIds.contains(task.id))
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(
                    task.isMilestone ? ThingsMacPalette.milestoneViolet.opacity(0.48) : Color.clear,
                    lineWidth: 1.15
                )
        )
        .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .thingsTimelineMouseTooltip(
            todayTimelineTaskTooltipText(task),
            isSuppressed: activePopoverTaskId == "unsched_\(task.id)"
        )
        .thingsInstantTaskTap(
            onSingleClick: { modifiers in
                store.handleRowClick(taskId: task.id, modifiers: modifiers)
            },
            onDoubleClick: {
                store.openTaskModal(task.id)
            }
        )
        .contextMenu {
            Button {
                store.openTaskModal(task.id)
            } label: {
                Label("\(store.tr("action_more_options"))…", systemImage: "slider.horizontal.3")
            }
            Divider()
            Button(task.status == .completed ? store.tr("action_reopen_todo") : store.tr("action_complete")) {
                store.toggleTaskCompletion(task)
            }
            Button {
                store.setTasksMilestone([task.id], isMilestone: !task.isMilestone)
            } label: {
                Label(
                    store.tr(task.isMilestone ? "v30_remove_milestone" : "v30_mark_as_milestone"),
                    systemImage: task.isMilestone ? "diamond.fill" : "diamond"
                )
            }
            if task.status == .open {
                Button {
                    store.toggleTaskInProgress(task)
                } label: {
                    Label(
                        store.tr(task.isInProgress ? "v33_clear_in_progress" : "v33_mark_in_progress"),
                        systemImage: "circle.lefthalf.filled"
                    )
                }
            }
            Divider()
            Button(role: .destructive) {
                store.deleteTask(task)
            } label: {
                Text(store.tr("action_delete"))
            }
        }
        .thingsTaskDraggable(store: store, task: task)
    }

    private func todayTimelineTaskTooltipText(
        _ task: TaskItem,
        startMinutes: Int? = nil,
        endMinutes: Int? = nil
    ) -> String {
        TimelineTaskTooltipFormatter.formatTodayTimelineTask(
            task: task,
            startMinutes: startMinutes,
            endMinutes: endMinutes,
            projectTitle: store.project(byId: task.projectId)?.title,
            isBlocked: store.isTaskActivelyBlocked(task),
            language: store.preferences.language
        )
    }

    private var milestoneTimelinePill: some View {
        Text("◆ \(store.tr("v30_milestone_label"))")
            .font(.system(size: 10.5, weight: .semibold))
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .foregroundStyle(ThingsMacPalette.milestoneViolet)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(
                Capsule(style: .continuous)
                    .fill(ThingsMacPalette.milestoneViolet.opacity(0.14))
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(ThingsMacPalette.milestoneViolet.opacity(0.30), lineWidth: 0.75)
            )
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
            guard !store.isTimelineFullScreen else { return }
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
                    .fill(Color.primary.opacity((!store.isTimelineFullScreen && isHovering) ? 0.045 : 0))
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(store.isTimelineFullScreen)
        .allowsHitTesting(!store.isTimelineFullScreen)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovering = hovering && !store.isTimelineFullScreen
            }
        }
        .thingsTooltip(store.isTimelineFullScreen ? "" : store.tr("macos_open_in_calendar"))
    }
}

