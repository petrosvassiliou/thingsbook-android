import SwiftUI
import AppKit
import ThingsbookCore

public struct WorkspaceInspectorPanelView: View {
    @ObservedObject var store: ThingsbookStore

    @State private var activeDraftTaskId: String? = nil
    @State private var draftTitle: String = ""
    @State private var draftNotes: String = ""
    @State private var newChecklistTitle: String = ""
    @State private var draftSaveTask: Task<Void, Never>? = nil

    @State private var isWhenPopoverOpen = false
    @State private var isDeadlinePopoverOpen = false
    @State private var isTimeblockPopoverOpen = false
    @State private var isCustomDurationPopoverOpen = false
    @State private var isMovePopoverOpen = false
    @State private var isTagPopoverOpen = false

    @FocusState private var isTitleFocused: Bool
    @FocusState private var isNotesFocused: Bool

    public init(store: ThingsbookStore) {
        self.store = store
    }

    private var singleSelectedTask: TaskItem? {
        guard store.selectedTaskIds.count == 1,
              let firstId = store.selectedTaskIds.first else {
            return nil
        }
        return store.task(byId: firstId)
    }

    public var body: some View {
        VStack(spacing: 0) {
            if let task = singleSelectedTask {
                singleTaskHeader(for: task)
            } else {
                overviewHeader
            }

            Divider()
                .opacity(0.65)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 12) {
                    if let task = singleSelectedTask {
                        singleTaskInspectorContent(for: task)
                    } else {
                        overviewInspectorContent
                    }
                }
                .padding(.horizontal, 14)
                .padding(.top, 14)
                .padding(.bottom, 24)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .frame(width: 348)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(ThingsMacPalette.subtleContainer.opacity(0.60))
        .onAppear {
            syncDraftsForSelectedTask(singleSelectedTask)
        }
        .onDisappear {
            draftSaveTask?.cancel()
            commitDraftEditsIfNeeded()
        }
        .onChange(of: singleSelectedTask?.id) { _, _ in
            draftSaveTask?.cancel()
            commitDraftEditsIfNeeded()
            resetTransientPopovers()
            newChecklistTitle = ""
            syncDraftsForSelectedTask(singleSelectedTask)
        }
        .onChange(of: singleSelectedTask?.title) { _, updatedTitle in
            guard let updatedTitle, !isTitleFocused, singleSelectedTask?.id == activeDraftTaskId else { return }
            if draftTitle != updatedTitle {
                draftTitle = updatedTitle
            }
        }
        .onChange(of: singleSelectedTask?.notes) { _, updatedNotes in
            guard let updatedNotes, !isNotesFocused, singleSelectedTask?.id == activeDraftTaskId else { return }
            if draftNotes != updatedNotes {
                draftNotes = updatedNotes
            }
        }
        .onChange(of: draftTitle) { _, _ in
            guard activeDraftTaskId != nil else { return }
            scheduleDraftAutoSave()
        }
        .onChange(of: draftNotes) { _, _ in
            guard activeDraftTaskId != nil else { return }
            scheduleDraftAutoSave()
        }
        .onChange(of: isTitleFocused) { wasFocused, nowFocused in
            if wasFocused && !nowFocused {
                draftSaveTask?.cancel()
                commitDraftEditsIfNeeded()
            }
        }
        .onChange(of: isNotesFocused) { wasFocused, nowFocused in
            if wasFocused && !nowFocused {
                draftSaveTask?.cancel()
                commitDraftEditsIfNeeded()
            }
        }
    }

    // MARK: - Headers

    private func singleTaskHeader(for task: TaskItem) -> some View {
        HStack(spacing: 8) {
            Image(systemName: task.isMilestone ? "diamond.fill" : "slider.horizontal.3")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(task.isMilestone ? ThingsMacPalette.milestoneViolet : ThingsMacPalette.accentBlue)

            Text(store.tr("v30_inspector_title"))
                .font(.system(size: 13.5, weight: .bold))
                .foregroundStyle(.primary)
                .lineLimit(1)

            Spacer(minLength: 6)

            InspectorHeaderIconButton(
                systemImage: "arrow.up.left.and.arrow.down.right",
                tint: ThingsMacPalette.accentBlue,
                helpText: store.tr("v30_open_details_action")
            ) {
                draftSaveTask?.cancel()
                commitDraftEditsIfNeeded(commitPendingChecklist: true)
                store.openTaskModal(task.id)
            }

            InspectorHeaderIconButton(
                systemImage: "xmark",
                tint: .secondary,
                helpText: store.tr("context_deselect_task")
            ) {
                draftSaveTask?.cancel()
                commitDraftEditsIfNeeded(commitPendingChecklist: true)
                store.clearSelection()
            }

            InspectorHeaderIconButton(
                systemImage: "sidebar.right",
                tint: .secondary,
                helpText: store.tr("v30_inspector_toggle_tooltip")
            ) {
                draftSaveTask?.cancel()
                commitDraftEditsIfNeeded(commitPendingChecklist: true)
                store.toggleInspector()
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(ThingsMacPalette.cardSurface.opacity(0.82))
    }

    private var overviewHeader: some View {
        HStack(spacing: 8) {
            Image(systemName: "sparkles.rectangle.stack")
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(ThingsMacPalette.accentBlue)

            Text(store.tr("v30_inspector_overview_title"))
                .font(.system(size: 13.5, weight: .bold))
                .foregroundStyle(.primary)
                .lineLimit(1)

            Spacer(minLength: 6)

            InspectorHeaderIconButton(
                systemImage: "sidebar.right",
                tint: .secondary,
                helpText: store.tr("v30_inspector_toggle_tooltip")
            ) {
                store.toggleInspector()
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(ThingsMacPalette.cardSurface.opacity(0.82))
    }

    // MARK: - Mode A: Single Selected Task Inspector

    @ViewBuilder
    private func singleTaskInspectorContent(for task: TaskItem) -> some View {
        titleAndFlagsCard(for: task)
        scheduleAndPropertiesCard(for: task)
        checklistCard(for: task)
        notesCard(for: task)
    }

    private func titleAndFlagsCard(for task: TaskItem) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                ThingsCheckboxButton(
                    status: task.status,
                    isSomeday: task.scheduleState == .someday,
                    isMilestone: task.isMilestone,
                    isInProgress: task.isInProgress,
                    helpText: store.tr("macos_checkbox_help"),
                    onToggleComplete: {
                        draftSaveTask?.cancel()
                        commitDraftEditsIfNeeded(commitPendingChecklist: true)
                        let fresh = store.task(byId: task.id) ?? task
                        store.toggleTaskCompletion(fresh)
                    },
                    onCancelTask: {
                        draftSaveTask?.cancel()
                        commitDraftEditsIfNeeded(commitPendingChecklist: true)
                        let fresh = store.task(byId: task.id) ?? task
                        store.cancelTask(fresh)
                    },
                    onToggleInProgress: {
                        draftSaveTask?.cancel()
                        commitDraftEditsIfNeeded(commitPendingChecklist: true)
                        let fresh = store.task(byId: task.id) ?? task
                        store.toggleTaskInProgress(fresh)
                    }
                )
                .padding(.top, 2)

                TextField(store.tr("placeholder_todo_title"), text: $draftTitle, axis: .vertical)
                    .textFieldStyle(.plain)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(task.status == .open ? .primary : .secondary)
                    .strikethrough(task.status == .canceled, color: .secondary)
                    .lineLimit(1...4)
                    .focused($isTitleFocused)
                    .onSubmit {
                        draftSaveTask?.cancel()
                        commitDraftEditsIfNeeded()
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            ThingsFlowLayout(horizontalSpacing: 7, verticalSpacing: 6) {
                // In Progress toggle
                if task.status == .open {
                    Button {
                        draftSaveTask?.cancel()
                        commitDraftEditsIfNeeded()
                        store.toggleTaskInProgress(task)
                    } label: {
                        HStack(spacing: 5) {
                            Image(systemName: "circle.lefthalf.filled")
                                .font(.system(size: 10.5, weight: .semibold))
                            Text(store.tr(task.isInProgress ? "v33_in_progress_badge" : "v33_mark_in_progress"))
                                .font(.system(size: 11.5, weight: .semibold))
                                .lineLimit(1)
                        }
                        .foregroundStyle(task.isInProgress ? ThingsMacPalette.somedayAmber : .secondary)
                        .padding(.horizontal, 9.5)
                        .padding(.vertical, 5)
                        .background(
                            Capsule(style: .continuous)
                                .fill(task.isInProgress ? ThingsMacPalette.somedayAmber.opacity(0.15) : ThingsMacPalette.subtleContainer)
                        )
                        .overlay(
                            Capsule(style: .continuous)
                                .strokeBorder(
                                    task.isInProgress ? ThingsMacPalette.somedayAmber.opacity(0.38) : Color.primary.opacity(0.08),
                                    lineWidth: 0.75
                                )
                        )
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(store.tr(task.isInProgress ? "v33_clear_in_progress" : "v33_mark_in_progress"))
                }

                // Pin toggle
                Button {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    store.setTasksPinned([task.id], isPinned: !task.isPinned)
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "pin.fill")
                            .font(.system(size: 10.5, weight: .semibold))
                        Text(store.tr(task.isPinned ? "v25_pinned_badge" : "v25_pin_task"))
                            .font(.system(size: 11.5, weight: .semibold))
                            .lineLimit(1)
                    }
                    .foregroundStyle(task.isPinned ? ThingsMacPalette.todayYellow : .secondary)
                    .padding(.horizontal, 9.5)
                    .padding(.vertical, 5)
                    .background(
                        Capsule(style: .continuous)
                            .fill(task.isPinned ? ThingsMacPalette.todayYellow.opacity(0.15) : ThingsMacPalette.subtleContainer)
                    )
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(
                                task.isPinned ? ThingsMacPalette.todayYellow.opacity(0.38) : Color.primary.opacity(0.08),
                                lineWidth: 0.75
                            )
                    )
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr(task.isPinned ? "v25_unpin_task" : "v25_pin_task"))

                // Milestone toggle
                Button {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    store.setTasksMilestone([task.id], isMilestone: !task.isMilestone)
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: task.isMilestone ? "diamond.fill" : "diamond")
                            .font(.system(size: 10.5, weight: .semibold))
                        Text(store.tr("v30_milestone_label"))
                            .font(.system(size: 11.5, weight: .semibold))
                            .lineLimit(1)
                    }
                    .foregroundStyle(task.isMilestone ? ThingsMacPalette.milestoneViolet : .secondary)
                    .padding(.horizontal, 9.5)
                    .padding(.vertical, 5)
                    .background(
                        Capsule(style: .continuous)
                            .fill(task.isMilestone ? ThingsMacPalette.milestoneViolet.opacity(0.15) : ThingsMacPalette.subtleContainer)
                    )
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(
                                task.isMilestone ? ThingsMacPalette.milestoneViolet.opacity(0.38) : Color.primary.opacity(0.08),
                                lineWidth: 0.75
                            )
                    )
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr(task.isMilestone ? "v30_remove_milestone" : "v30_mark_as_milestone"))
            }
        }
        .padding(12)
        .background(inspectorBentoCardSurface())
    }

    private func scheduleAndPropertiesCard(for task: TaskItem) -> some View {
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        let isTomorrow = task.scheduleState == .upcoming
            && task.whenDate.map { DateEpochConverter.epochDay(forMillis: $0) == todayEpochDay + 1 } == true
        let dlInfo = task.deadline.map { deadlineSummaryInfo(for: $0) }
        let currentEffort = task.resolvedEffort
        let currentDuration = (task.durationMinutes ?? 0) > 0 ? task.durationMinutes : nil
        let breadcrumb = contextBreadcrumb(for: task)
        let isFocusRunningForTask = store.focusTimer?.taskId == task.id

        return VStack(alignment: .leading, spacing: 10) {
            inspectorSectionHeader(icon: "calendar", title: store.tr("action_when"))

            // Quick When Pills (Today, Evening, Tomorrow, Someday) + Custom Date Popover
            ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                quickWhenPill(
                    title: store.tr("view_today"),
                    icon: "sun.max.fill",
                    isActive: task.scheduleState == .today,
                    tint: ThingsMacPalette.todayYellow
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    store.scheduleTasks([task.id], to: .today)
                }

                quickWhenPill(
                    title: store.tr("view_this_evening"),
                    icon: "moon.stars.fill",
                    isActive: task.scheduleState == .thisEvening,
                    tint: ThingsMacPalette.eveningPurple
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    store.scheduleTasks([task.id], to: .thisEvening)
                }

                quickWhenPill(
                    title: store.tr("date_tomorrow"),
                    icon: "sunrise.fill",
                    isActive: isTomorrow,
                    tint: ThingsMacPalette.upcomingRed
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    store.scheduleTasks([task.id], to: .upcoming)
                }

                quickWhenPill(
                    title: store.tr("view_someday"),
                    icon: "cloud.fill",
                    isActive: task.scheduleState == .someday,
                    tint: ThingsMacPalette.somedayAmber
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    store.scheduleTasks([task.id], to: .someday)
                }

                let customWhenLabel = customUpcomingOrReminderLabel(for: task, isTomorrow: isTomorrow)
                Button {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    isWhenPopoverOpen = true
                } label: {
                    HStack(spacing: 4.5) {
                        Image(systemName: task.reminderTime != nil ? "bell.fill" : "calendar.badge.clock")
                            .font(.system(size: 10.5, weight: .semibold))
                        if let customWhenLabel {
                            Text(customWhenLabel)
                                .font(.system(size: 11, weight: .semibold))
                                .lineLimit(1)
                        }
                    }
                    .foregroundStyle(customWhenLabel != nil ? ThingsMacPalette.accentBlue : .secondary)
                    .padding(.horizontal, customWhenLabel != nil ? 8.5 : 7.5)
                    .padding(.vertical, 4.5)
                    .background(
                        Capsule(style: .continuous)
                            .fill(customWhenLabel != nil ? ThingsMacPalette.accentBlue.opacity(0.12) : ThingsMacPalette.subtleContainer)
                    )
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(
                                customWhenLabel != nil ? ThingsMacPalette.accentBlue.opacity(0.32) : Color.primary.opacity(0.08),
                                lineWidth: 0.75
                            )
                    )
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("context_when_date_reminder"))
                .popover(isPresented: $isWhenPopoverOpen) {
                    WhenPopoverView(
                        store: store,
                        currentSchedule: task.scheduleState,
                        currentWhenMillis: task.whenDate,
                        currentReminderMillis: task.reminderTime
                    ) { resolved in
                        var copy = store.task(byId: task.id) ?? task
                        copy.scheduleState = resolved.scheduleState
                        copy.whenDate = resolved.whenDateEpochMillis
                        copy.reminderTime = resolved.reminderEpochMillis
                        store.updateTask(copy)
                    }
                }
            }

            Divider()
                .opacity(0.55)

            // Deadline, Timeblock, Duration & Effort Badges
            ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                ThingsActionChipButton(
                    title: dlInfo?.label ?? store.tr("action_deadline"),
                    systemImage: "flag.fill",
                    isActive: dlInfo != nil,
                    activeTint: (dlInfo?.isUrgent == true) ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue,
                    helpText: store.tr("context_adjust_deadline")
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    isDeadlinePopoverOpen = true
                }
                .popover(isPresented: $isDeadlinePopoverOpen) {
                    DeadlinePopoverView(store: store, currentDeadlineMillis: task.deadline) { dl in
                        var copy = store.task(byId: task.id) ?? task
                        copy.deadline = dl
                        store.updateTask(copy)
                    }
                }

                ThingsActionChipButton(
                    title: task.scheduledStartMinutes != nil
                        ? (task.formattedTimeblockPill ?? store.tr("v2_timeblock_title"))
                        : store.tr("v2_timeblock_title"),
                    systemImage: "clock.badge.checkmark",
                    isActive: task.scheduledStartMinutes != nil,
                    activeTint: ThingsMacPalette.anytimeTeal,
                    helpText: store.tr("macos_tooltip_timeblock")
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    isTimeblockPopoverOpen = true
                }
                .popover(isPresented: $isTimeblockPopoverOpen) {
                    TimeblockPopoverView(
                        store: store,
                        currentStartMinutes: task.scheduledStartMinutes,
                        currentDurationMinutes: task.durationMinutes
                    ) { startMins, durMins in
                        store.setTaskTimeblock(task.id, startMinutes: startMins, durationMinutes: durMins)
                    }
                }

                // Standalone Duration Menu
                Menu {
                    ForEach(TaskItem.standaloneDurationPresets, id: \.self) { mins in
                        Button {
                            draftSaveTask?.cancel()
                            commitDraftEditsIfNeeded()
                            store.setTaskStandaloneDuration(task.id, durationMinutes: mins)
                        } label: {
                            if task.durationMinutes == mins {
                                Label(TaskItem.formatCompactDuration(mins), systemImage: "checkmark")
                            } else {
                                Text(TaskItem.formatCompactDuration(mins))
                            }
                        }
                    }
                    Divider()
                    Button(store.tr("v30_duration_custom")) {
                        draftSaveTask?.cancel()
                        commitDraftEditsIfNeeded()
                        isCustomDurationPopoverOpen = true
                    }
                    if currentDuration != nil {
                        Button(store.tr("v30_duration_clear"), role: .destructive) {
                            draftSaveTask?.cancel()
                            commitDraftEditsIfNeeded()
                            store.setTaskStandaloneDuration(task.id, durationMinutes: nil)
                        }
                    }
                    if (task.actualMinutes ?? 0) > 0 {
                        Button(store.tr("v32_reset_actual_time"), role: .destructive) {
                            draftSaveTask?.cancel()
                            commitDraftEditsIfNeeded()
                            store.setTaskActualMinutes(task.id, actualMinutes: nil)
                        }
                    }
                } label: {
                    inspectorPropertyPillLabel(
                        title: currentDuration.map { TaskItem.formatCompactDuration($0) } ?? store.tr("v30_duration_label"),
                        systemImage: "timer",
                        isActive: currentDuration != nil,
                        tint: ThingsMacPalette.anytimeTeal
                    )
                }
                .menuStyle(.borderlessButton)
                .menuIndicator(.hidden)
                .fixedSize()
                .popover(isPresented: $isCustomDurationPopoverOpen) {
                    CustomDurationPopoverView(
                        store: store,
                        currentDurationMinutes: currentDuration
                    ) { customMins in
                        store.setTaskStandaloneDuration(task.id, durationMinutes: customMins)
                    }
                }

                if let actualBadge = store.formattedActualTimeBadge(for: task) {
                    Menu {
                        Button(store.tr("v32_reset_actual_time"), role: .destructive) {
                            draftSaveTask?.cancel()
                            commitDraftEditsIfNeeded()
                            store.setTaskActualMinutes(task.id, actualMinutes: nil)
                        }
                    } label: {
                        inspectorPropertyPillLabel(
                            title: actualBadge,
                            systemImage: "clock.arrow.circlepath",
                            isActive: true,
                            tint: ThingsMacPalette.logbookGreen
                        )
                    }
                    .menuStyle(.borderlessButton)
                    .menuIndicator(.hidden)
                    .fixedSize()
                    .thingsTooltip(actualBadge)
                }

                // Effort Menu
                Menu {
                    ForEach(TaskEffortLevel.allCases, id: \.rawValue) { level in
                        Button {
                            draftSaveTask?.cancel()
                            commitDraftEditsIfNeeded()
                            store.setTaskEffort(task.id, effortLevel: level.rawValue)
                        } label: {
                            Label(
                                effortLocalizedTitle(for: level),
                                systemImage: currentEffort == level ? "checkmark" : level.sfSymbol
                            )
                        }
                    }
                    if currentEffort != nil {
                        Divider()
                        Button(store.tr("v30_effort_none")) {
                            draftSaveTask?.cancel()
                            commitDraftEditsIfNeeded()
                            store.setTaskEffort(task.id, effortLevel: nil)
                        }
                    }
                } label: {
                    inspectorPropertyPillLabel(
                        title: currentEffort.map { effortLocalizedTitle(for: $0) } ?? store.tr("v30_effort_label"),
                        systemImage: currentEffort?.sfSymbol ?? "bolt",
                        isActive: currentEffort != nil,
                        tint: currentEffort.map { effortTint(for: $0) } ?? ThingsMacPalette.accentBlue
                    )
                }
                .menuStyle(.borderlessButton)
                .menuIndicator(.hidden)
                .fixedSize()
            }

            Divider()
                .opacity(0.55)

            // Area › Project Breadcrumb & Tag Pills
            VStack(alignment: .leading, spacing: 7) {
                Button {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    isMovePopoverOpen = true
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "folder")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(breadcrumb != nil ? ThingsMacPalette.accentBlue : .secondary)
                        Text(breadcrumb ?? store.tr("action_move"))
                            .font(.system(size: 12, weight: breadcrumb != nil ? .semibold : .medium))
                            .foregroundStyle(breadcrumb != nil ? Color.primary : Color.secondary)
                            .lineLimit(1)
                        Spacer(minLength: 4)
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.system(size: 9.5, weight: .semibold))
                            .foregroundStyle(.tertiary)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6.5)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(ThingsMacPalette.subtleContainer)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8, style: .continuous)
                                    .strokeBorder(Color.primary.opacity(0.07), lineWidth: 0.75)
                            )
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                .buttonStyle(.plain)
                .popover(isPresented: $isMovePopoverOpen) {
                    MoveDestinationPopoverView(store: store, task: task) { projId, headId, areaId, schedOverride in
                        var copy = store.task(byId: task.id) ?? task
                        copy.projectId = projId
                        copy.headingId = headId
                        copy.areaId = areaId
                        if let schedOverride {
                            copy.scheduleState = schedOverride
                        }
                        store.updateTask(copy)
                    }
                }

                ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                    ForEach(task.tags) { tag in
                        TagPillBadge(
                            title: tag.title,
                            isSelected: true,
                            showIcon: true,
                            helpText: "#\(tag.title)"
                        ) {
                            draftSaveTask?.cancel()
                            commitDraftEditsIfNeeded()
                            isTagPopoverOpen = true
                        }
                    }

                    ThingsActionChipButton(
                        title: "+ \(store.tr("action_tags"))",
                        systemImage: "square.grid.2x2",
                        isActive: false,
                        helpText: store.tr("action_tags")
                    ) {
                        draftSaveTask?.cancel()
                        commitDraftEditsIfNeeded()
                        isTagPopoverOpen = true
                    }
                    .popover(isPresented: $isTagPopoverOpen) {
                        TagPickerPopoverView(
                            store: store,
                            selectedTagIds: Set(task.tagIds),
                            onToggleTag: { tagId in
                                let base = store.task(byId: task.id) ?? task
                                var tids = Set(base.tagIds)
                                if tids.contains(tagId) {
                                    tids.remove(tagId)
                                } else {
                                    tids.insert(tagId)
                                }
                                store.updateTask(base, tagIds: Array(tids))
                            }
                        )
                    }
                }
            }

            // 1-Click Start Focus Timer Button
            if task.status == .open {
                Button {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded(commitPendingChecklist: true)
                    let fresh = store.task(byId: task.id) ?? task
                    store.startFocusTimer(for: fresh)
                } label: {
                    HStack(spacing: 7) {
                        Image(systemName: isFocusRunningForTask ? "timer" : "play.fill")
                            .font(.system(size: 11, weight: .bold))
                        Text(
                            isFocusRunningForTask
                                ? "\(store.tr("focus_timer_active_label")) · \(store.focusTimer?.formattedCountdown ?? "")"
                                : store.tr("action_focus_timer")
                        )
                        .font(.system(size: 12, weight: .semibold))
                        .lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    .foregroundStyle(isFocusRunningForTask ? .white : ThingsMacPalette.accentBlue)
                    .padding(.horizontal, 11)
                    .padding(.vertical, 7.5)
                    .background(
                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                            .fill(
                                isFocusRunningForTask
                                    ? ThingsMacPalette.accentBlue
                                    : ThingsMacPalette.accentBlue.opacity(0.11)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 9, style: .continuous)
                                    .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.28), lineWidth: 0.75)
                            )
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("action_focus_timer"))
            }
        }
        .padding(12)
        .background(inspectorBentoCardSurface())
    }

    private func checklistCard(for task: TaskItem) -> some View {
        let items = store.checklistItems(for: task.id)
        let doneCount = items.filter(\.isCompleted).count

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                inspectorSectionHeader(icon: "checklist", title: store.tr("action_checklist"))

                if !items.isEmpty {
                    Text("\(doneCount)/\(items.count)")
                        .font(.system(size: 10.5, weight: .bold, design: .rounded))
                        .foregroundStyle(doneCount == items.count ? ThingsMacPalette.logbookGreen : ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 6.5)
                        .padding(.vertical, 1.5)
                        .background(
                            Capsule(style: .continuous)
                                .fill(
                                    (doneCount == items.count ? ThingsMacPalette.logbookGreen : ThingsMacPalette.accentBlue)
                                        .opacity(0.13)
                                )
                        )
                }
            }

            if !items.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(items) { item in
                        InspectorChecklistRow(
                            store: store,
                            item: item,
                            onToggle: { store.toggleChecklistItem(item) },
                            onDelete: { store.deleteChecklistItem(item) }
                        )
                    }
                }
            }

            HStack(spacing: 7) {
                Image(systemName: "plus")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .frame(width: 14)

                TextField(store.tr("placeholder_add_checklist_item"), text: $newChecklistTitle)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12.5))
                    .onSubmit {
                        submitNewChecklistItem(for: task.id)
                    }

                if !newChecklistTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Button(store.tr("macos_action_add")) {
                        submitNewChecklistItem(for: task.id)
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 9)
            .padding(.vertical, 6.5)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.07), lineWidth: 0.75)
                    )
            )
        }
        .padding(12)
        .background(inspectorBentoCardSurface())
    }

    private func notesCard(for task: TaskItem) -> some View {
        let links = MarkdownNotesParser.extractDetectedLinks(from: draftNotes)

        return VStack(alignment: .leading, spacing: 8) {
            inspectorSectionHeader(icon: "doc.text", title: store.tr("placeholder_notes"))

            TextField(store.tr("placeholder_notes"), text: $draftNotes, axis: .vertical)
                .textFieldStyle(.plain)
                .font(.system(size: 13))
                .lineLimit(4...14)
                .focused($isNotesFocused)
                .padding(9)
                .frame(maxWidth: .infinity, minHeight: 84, alignment: .topLeading)
                .background(
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(ThingsMacPalette.subtleContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 9, style: .continuous)
                                .strokeBorder(
                                    isNotesFocused ? ThingsMacPalette.accentBlue.opacity(0.42) : Color.primary.opacity(0.07),
                                    lineWidth: isNotesFocused ? 1.0 : 0.75
                                )
                        )
                )

            if !links.isEmpty {
                ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                    ForEach(links) { link in
                        Button {
                            if let url = URL(string: link.targetUri) {
                                NSWorkspace.shared.open(url)
                            }
                        } label: {
                            HStack(spacing: 4.5) {
                                Image(systemName: link.type.sfSymbol)
                                    .font(.system(size: 10, weight: .semibold))
                                Text("\(link.displayLabel) ↗")
                                    .font(.system(size: 11, weight: .semibold))
                                    .lineLimit(1)
                            }
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                            .padding(.horizontal, 8.5)
                            .padding(.vertical, 3.5)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(ThingsMacPalette.accentBlue.opacity(0.11))
                            )
                        }
                        .buttonStyle(.plain)
                        .thingsTooltip(link.targetUri)
                    }
                }
            }
        }
        .padding(12)
        .background(inspectorBentoCardSurface())
    }

    // MARK: - Mode B: Day & Workspace Overview

    @ViewBuilder
    private var overviewInspectorContent: some View {
        todayCapacityAndProgressCard
        inspectorWorkloadForecastCard
        todayMiniTimelineCard
        upcomingDeadlinesAndMilestonesCard
        if !visiblePinnedOpenTasks.isEmpty {
            pinnedTasksCard
        }
    }

    @ViewBuilder
    private var inspectorWorkloadForecastCard: some View {
        let forecast = store.sevenDayWorkloadForecast()
        if !forecast.isEmpty {
            SevenDayWorkloadStripCard(store: store, forecast: forecast, compact: true)
        }
    }

    private var deduplicatedOpenTodayTasks: [TaskItem] {
        var seen = Set<String>()
        return (store.todayDaytimeTasks + store.thisEveningTasks)
            .filter { !$0.isDeleted && $0.status == .open && seen.insert($0.id).inserted }
    }

    private var completedTodayCount: Int {
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        return store.logbookTasks.filter { task in
            guard !task.isDeleted, task.status == .completed, let completedAt = task.completedAt else {
                return false
            }
            return DateEpochConverter.epochDay(forMillis: completedAt) == todayEpochDay
        }.count
    }

    private var todayCapacityAndProgressCard: some View {
        let openToday = deduplicatedOpenTodayTasks
        let openCount = openToday.count
        let doneCount = completedTodayCount
        let totalTodayCount = openCount + doneCount
        let completionFraction: Double = totalTodayCount > 0
            ? min(1.0, Double(doneCount) / Double(totalTodayCount))
            : 0.0

        let cal = Calendar.current
        let sod = cal.startOfDay(for: Date())
        let nextSod = cal.date(byAdding: .day, value: 1, to: sod) ?? sod.addingTimeInterval(86_400)
        let normBounds = TodayCapacityCalculator.normalizeWorkdayBounds(
            startMinutes: store.preferences.workdayStartMinutes,
            endMinutes: store.preferences.workdayEndMinutes
        )
        let totalWorkdayMinutes = Double(max(60, normBounds.endMinutes - normBounds.startMinutes))
        let cap = TodayCapacityCalculator.compute(
            tasks: openToday,
            calendarEvents: store.todayTimelineCalendarEvents(),
            dayStartMillis: DateEpochConverter.startOfDayMillis(for: sod, calendar: cal),
            dayEndMillis: DateEpochConverter.startOfDayMillis(for: nextSod, calendar: cal),
            workdayStartMinutes: normBounds.startMinutes,
            workdayEndMinutes: normBounds.endMinutes
        )
        let plannedStr = TodayCapacityCalculator.formatMinutes(cap.plannedMinutes)
        let freeStr = TodayCapacityCalculator.formatMinutes(cap.freeMinutes)
        let capacityText = store.trf("v30_today_capacity_summary", plannedStr, freeStr)
        let capacityLoadFraction = min(1.0, max(0.0, (totalWorkdayMinutes - Double(cap.freeMinutes)) / totalWorkdayMinutes))
        let barFraction = totalTodayCount > 0 ? max(completionFraction, capacityLoadFraction * 0.35) : capacityLoadFraction

        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "sun.max.fill")
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(ThingsMacPalette.todayYellow)
                Text(store.tr("view_today"))
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(.secondary)
                Spacer()
                Text(capacityText)
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .lineLimit(1)
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule(style: .continuous)
                        .fill(ThingsMacPalette.accentBlue.opacity(0.13))
                    if barFraction > 0.005 {
                        Capsule(style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: doneCount > 0 && openCount == 0
                                        ? [ThingsMacPalette.logbookGreen, ThingsMacPalette.anytimeTeal]
                                        : [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(width: max(8, geo.size.width * barFraction))
                    }
                }
            }
            .frame(height: 6.5)

            HStack(spacing: 8) {
                HStack(spacing: 4.5) {
                    Circle()
                        .fill(ThingsMacPalette.accentBlue)
                        .frame(width: 6.5, height: 6.5)
                    Text(store.trf("macos_items_count", openCount))
                        .font(.system(size: 11.5, weight: .semibold, design: .rounded))
                        .foregroundStyle(.primary)
                }
                .padding(.horizontal, 8.5)
                .padding(.vertical, 4)
                .background(
                    Capsule(style: .continuous)
                        .fill(ThingsMacPalette.subtleContainer)
                )

                HStack(spacing: 4.5) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.logbookGreen)
                    Text("\(doneCount) · \(store.tr("action_complete"))")
                        .font(.system(size: 11.5, weight: .semibold, design: .rounded))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .padding(.horizontal, 8.5)
                .padding(.vertical, 4)
                .background(
                    Capsule(style: .continuous)
                        .fill(ThingsMacPalette.subtleContainer)
                )

                Spacer(minLength: 0)
            }
        }
        .padding(12)
        .background(inspectorBentoCardSurface())
    }

    private enum MiniTimelineEntry: Identifiable {
        case calendarEvent(CalendarEventItem, startMinuteOfDay: Int)
        case timeblockedTask(TaskItem, startMinuteOfDay: Int)

        var id: String {
            switch self {
            case .calendarEvent(let ev, _):
                return "ev_\(ev.id)"
            case .timeblockedTask(let task, _):
                return "tb_\(task.id)"
            }
        }

        var sortMinutes: Int {
            switch self {
            case .calendarEvent(_, let mins), .timeblockedTask(_, let mins):
                return mins
            }
        }

        var isEvent: Bool {
            if case .calendarEvent = self { return true }
            return false
        }
    }

    private var todayMiniTimelineEntries: [MiniTimelineEntry] {
        let cal = Calendar.current
        let sodMillis = DateEpochConverter.startOfDayMillis(for: Date(), calendar: cal)

        let timedEvents: [MiniTimelineEntry] = store.todayTimelineCalendarEvents()
            .filter { !$0.isAllDay }
            .map { ev in
                let clampedStart = max(sodMillis, ev.startMillis)
                let mins = max(0, min(1439, Int((clampedStart - sodMillis) / 60_000)))
                return .calendarEvent(ev, startMinuteOfDay: mins)
            }

        let timedTasks: [MiniTimelineEntry] = deduplicatedOpenTodayTasks
            .compactMap { task in
                guard let startMins = task.scheduledStartMinutes else { return nil }
                return .timeblockedTask(task, startMinuteOfDay: startMins)
            }

        return (timedEvents + timedTasks).sorted { lhs, rhs in
            if lhs.sortMinutes != rhs.sortMinutes {
                return lhs.sortMinutes < rhs.sortMinutes
            }
            if lhs.isEvent != rhs.isEvent {
                return lhs.isEvent && !rhs.isEvent
            }
            return lhs.id < rhs.id
        }
    }

    private var todayMiniTimelineCard: some View {
        let entries = todayMiniTimelineEntries

        return VStack(alignment: .leading, spacing: 8) {
            inspectorSectionHeader(icon: "clock", title: store.tr("v2_timeline_view"))

            if entries.isEmpty {
                Text(store.tr("empty_today"))
                    .font(.system(size: 12))
                    .foregroundStyle(.tertiary)
                    .padding(.vertical, 4)
            } else {
                VStack(alignment: .leading, spacing: 5) {
                    ForEach(entries) { entry in
                        switch entry {
                        case .calendarEvent(let event, _):
                            miniTimelineEventRow(event)
                        case .timeblockedTask(let task, let startMins):
                            miniTimelineTaskRow(task, startMinutes: startMins)
                        }
                    }
                }
            }
        }
        .padding(12)
        .background(inspectorBentoCardSurface())
    }

    private func miniTimelineEventRow(_ event: CalendarEventItem) -> some View {
        let calColor = ThingsMacPalette.colorFromArgb(event.colorHex)
        let timeStr = shortTimeString(event.startMillis)

        return Button {
            AppleCalendarService.shared.openEventInAppleCalendar(event)
        } label: {
            HStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                    .fill(calColor)
                    .frame(width: 3, height: 15)

                Text(timeStr)
                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .frame(width: 46, alignment: .leading)

                Text(event.title.isEmpty ? store.tr("calendar_untitled_event") : event.title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.primary.opacity(0.86))
                    .lineLimit(1)

                Spacer(minLength: 4)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5.5)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
            )
            .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
        .thingsTooltip(store.tr("macos_open_in_calendar"))
    }

    private func miniTimelineTaskRow(_ task: TaskItem, startMinutes: Int) -> some View {
        let isSelected = store.selectedTaskIds.contains(task.id)

        return HStack(spacing: 8) {
            Text(TaskItem.formatMinutesOfDay(startMinutes))
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundStyle(task.isMilestone ? ThingsMacPalette.milestoneViolet : ThingsMacPalette.anytimeTeal)
                .frame(width: 46, alignment: .leading)

            if task.isMilestone {
                Text("◆")
                    .font(.system(size: 10.5, weight: .bold))
                    .foregroundStyle(ThingsMacPalette.milestoneViolet)
            }

            Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                .font(.system(size: 12.5, weight: .medium))
                .foregroundStyle(.primary)
                .lineLimit(1)

            Spacer(minLength: 4)

            Text(TaskItem.formatCompactDuration(task.effectiveDurationMinutes))
                .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 8.5)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(isSelected ? ThingsMacPalette.accentBlue.opacity(0.12) : ThingsMacPalette.subtleContainer)
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .strokeBorder(
                            isSelected ? ThingsMacPalette.accentBlue.opacity(0.35) : Color.primary.opacity(0.06),
                            lineWidth: 0.75
                        )
                )
        )
        .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .thingsInstantTaskTap(
            onSingleClick: { _ in
                store.selectedTaskIds = [task.id]
                store.lastSelectedTaskId = task.id
            },
            onDoubleClick: {
                store.openTaskModal(task.id)
            }
        )
    }

    private struct UrgentTaskCandidate: Identifiable {
        var id: String { task.id }
        let task: TaskItem
        let targetEpochDay: Int
        let dayDiff: Int
    }

    private var upcomingDeadlineAndMilestoneCandidates: [UrgentTaskCandidate] {
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        let horizonEpochDay = todayEpochDay + 14

        var candidates: [UrgentTaskCandidate] = []
        for task in store.allOpenTasks where !task.isDeleted && task.status == .open {
            guard task.isMilestone || task.deadline != nil else { continue }
            let resolvedDay: Int? = {
                if let dl = task.deadline {
                    return DateEpochConverter.epochDay(forMillis: dl)
                }
                if task.isMilestone, let w = task.whenDate {
                    return DateEpochConverter.epochDay(forMillis: w)
                }
                if task.isMilestone && (task.scheduleState == .today || task.scheduleState == .thisEvening) {
                    return todayEpochDay
                }
                return nil
            }()
            guard let targetDay = resolvedDay, targetDay <= horizonEpochDay else { continue }
            candidates.append(
                UrgentTaskCandidate(
                    task: task,
                    targetEpochDay: targetDay,
                    dayDiff: targetDay - todayEpochDay
                )
            )
        }

        candidates.sort { lhs, rhs in
            if lhs.targetEpochDay != rhs.targetEpochDay {
                return lhs.targetEpochDay < rhs.targetEpochDay
            }
            if lhs.task.isMilestone != rhs.task.isMilestone {
                return lhs.task.isMilestone && !rhs.task.isMilestone
            }
            if lhs.task.sortOrder != rhs.task.sortOrder {
                return lhs.task.sortOrder < rhs.task.sortOrder
            }
            return lhs.task.id < rhs.task.id
        }

        return Array(candidates.prefix(8))
    }

    private var upcomingDeadlinesAndMilestonesCard: some View {
        let items = upcomingDeadlineAndMilestoneCandidates

        return VStack(alignment: .leading, spacing: 8) {
            inspectorSectionHeader(
                icon: "flag.fill",
                title: store.tr("v30_inspector_upcoming_deadlines")
            )

            if items.isEmpty {
                Text(store.tr("empty_view"))
                    .font(.system(size: 12))
                    .foregroundStyle(.tertiary)
                    .padding(.vertical, 4)
            } else {
                VStack(alignment: .leading, spacing: 5) {
                    ForEach(items) { entry in
                        upcomingDeadlineRow(entry)
                    }
                }
            }
        }
        .padding(12)
        .background(inspectorBentoCardSurface())
    }

    private func upcomingDeadlineRow(_ entry: UrgentTaskCandidate) -> some View {
        let task = entry.task
        let isUrgent = entry.dayDiff <= 1
        let badgeText: String = {
            if entry.dayDiff == 0 { return store.tr("deadline_today") }
            if entry.dayDiff < 0 { return store.trf("deadline_days_overdue", abs(entry.dayDiff)) }
            return store.trf("deadline_days_left", entry.dayDiff)
        }()
        let badgeTint: Color = isUrgent
            ? ThingsMacPalette.deadlineCrimson
            : (task.isMilestone ? ThingsMacPalette.milestoneViolet : ThingsMacPalette.accentBlue)

        return HStack(spacing: 7) {
            if task.isMilestone {
                Text("◆")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(ThingsMacPalette.milestoneViolet)
                    .padding(.horizontal, 5.5)
                    .padding(.vertical, 2)
                    .background(
                        Capsule(style: .continuous)
                            .fill(ThingsMacPalette.milestoneViolet.opacity(0.15))
                    )
            }

            VStack(alignment: .leading, spacing: 1.5) {
                Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                    .font(.system(size: 12.5, weight: task.isMilestone ? .semibold : .medium))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                if let crumb = contextBreadcrumb(for: task) {
                    Text(crumb)
                        .font(.system(size: 10.5))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)

            Text(badgeText)
                .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                .foregroundStyle(badgeTint)
                .padding(.horizontal, 6.5)
                .padding(.vertical, 2)
                .background(
                    Capsule(style: .continuous)
                        .fill(badgeTint.opacity(0.13))
                )
                .fixedSize(horizontal: true, vertical: false)

            Button {
                store.openTaskModal(task.id)
            } label: {
                Image(systemName: "arrow.up.left.and.arrow.down.right")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .frame(width: 22, height: 22)
                    .background(
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(Color.primary.opacity(0.05))
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            .buttonStyle(.plain)
            .thingsTooltip(store.tr("v30_open_details_action"))
        }
        .padding(.horizontal, 8.5)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer)
                .overlay(
                    RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                        .strokeBorder(
                            task.isMilestone ? ThingsMacPalette.milestoneViolet.opacity(0.26) : Color.primary.opacity(0.06),
                            lineWidth: 0.75
                        )
                )
        )
        .contentShape(RoundedRectangle(cornerRadius: 8.5, style: .continuous))
        .thingsInstantTaskTap(
            onSingleClick: { _ in
                store.selectedTaskIds = [task.id]
                store.lastSelectedTaskId = task.id
            },
            onDoubleClick: {
                store.openTaskModal(task.id)
            }
        )
    }

    private var visiblePinnedOpenTasks: [TaskItem] {
        let openById = Dictionary(
            store.allOpenTasks
                .filter { !$0.isDeleted && $0.status == .open }
                .map { ($0.id, $0) },
            uniquingKeysWith: { first, _ in first }
        )
        var seen = Set<String>()
        var result: [TaskItem] = []
        for id in store.currentVisibleTaskIdsInOrder() {
            guard seen.insert(id).inserted,
                  let task = openById[id],
                  task.isPinned else {
                continue
            }
            result.append(task)
        }
        return result
    }

    private var pinnedTasksCard: some View {
        let pinned = visiblePinnedOpenTasks

        return VStack(alignment: .leading, spacing: 8) {
            inspectorSectionHeader(icon: "pin.fill", title: store.tr("v25_pinned_badge"))

            VStack(alignment: .leading, spacing: 5) {
                ForEach(pinned) { task in
                    HStack(spacing: 8) {
                        ThingsCheckboxButton(
                            status: task.status,
                            isSomeday: task.scheduleState == .someday,
                            isMilestone: task.isMilestone,
                            isInProgress: task.isInProgress,
                            helpText: store.tr("macos_checkbox_help"),
                            onToggleComplete: { store.toggleTaskCompletion(task) },
                            onCancelTask: { store.cancelTask(task) },
                            onToggleInProgress: { store.toggleTaskInProgress(task) }
                        )

                        Image(systemName: "pin.fill")
                            .font(.system(size: 9.5, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.todayYellow)

                        Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                            .font(.system(size: 12.5, weight: .medium))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)

                        Button {
                            store.openTaskModal(task.id)
                        } label: {
                            Image(systemName: "arrow.up.left.and.arrow.down.right")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundStyle(.secondary)
                                .frame(width: 22, height: 22)
                                .background(
                                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                                        .fill(Color.primary.opacity(0.05))
                                )
                                .contentShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .thingsTooltip(store.tr("v30_open_details_action"))
                    }
                    .padding(.horizontal, 8.5)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                            .fill(ThingsMacPalette.subtleContainer)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                                    .strokeBorder(Color.primary.opacity(0.06), lineWidth: 0.75)
                            )
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 8.5, style: .continuous))
                    .thingsInstantTaskTap(
                        onSingleClick: { _ in
                            store.selectedTaskIds = [task.id]
                            store.lastSelectedTaskId = task.id
                        },
                        onDoubleClick: {
                            store.openTaskModal(task.id)
                        }
                    )
                }
            }
        }
        .padding(12)
        .background(inspectorBentoCardSurface())
    }

    // MARK: - Draft Persistence & Shared Helpers

    private func syncDraftsForSelectedTask(_ task: TaskItem?) {
        guard let task else {
            activeDraftTaskId = nil
            draftTitle = ""
            draftNotes = ""
            return
        }
        activeDraftTaskId = task.id
        draftTitle = task.title
        draftNotes = task.notes
    }

    private func scheduleDraftAutoSave() {
        draftSaveTask?.cancel()
        draftSaveTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 380_000_000)
            guard !Task.isCancelled else { return }
            commitDraftEditsIfNeeded()
        }
    }

    private func commitDraftEditsIfNeeded(commitPendingChecklist: Bool = false) {
        guard let targetId = activeDraftTaskId,
              let base = store.taskIncludingTrash(byId: targetId) else {
            return
        }
        if draftTitle != base.title || draftNotes != base.notes {
            var copy = base
            copy.title = draftTitle
            copy.notes = draftNotes
            store.updateTask(copy)
        }
        if commitPendingChecklist {
            submitNewChecklistItem(for: targetId)
        }
    }

    private func submitNewChecklistItem(for taskId: String) {
        let clean = newChecklistTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        _ = store.addChecklistItem(taskId: taskId, title: clean)
        newChecklistTitle = ""
    }

    private func resetTransientPopovers() {
        isWhenPopoverOpen = false
        isDeadlinePopoverOpen = false
        isTimeblockPopoverOpen = false
        isCustomDurationPopoverOpen = false
        isMovePopoverOpen = false
        isTagPopoverOpen = false
    }

    private func inspectorBentoCardSurface() -> some View {
        RoundedRectangle(cornerRadius: 12, style: .continuous)
            .fill(ThingsMacPalette.cardSurface)
            .shadow(color: Color.black.opacity(0.035), radius: 5, x: 0, y: 1.5)
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.75)
            )
    }

    private func inspectorSectionHeader(icon: String, title: String) -> some View {
        HStack(spacing: 5.5) {
            Image(systemName: icon)
                .font(.system(size: 10.5, weight: .semibold))
                .foregroundStyle(.secondary)
            Text(title)
                .font(.system(size: 11.5, weight: .bold))
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
    }

    private func quickWhenPill(
        title: String,
        icon: String,
        isActive: Bool,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 4.5) {
                Image(systemName: icon)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(isActive ? tint : .secondary)
                Text(title)
                    .font(.system(size: 11.5, weight: isActive ? .semibold : .medium))
                    .foregroundStyle(isActive ? Color.primary : Color.primary.opacity(0.84))
                    .lineLimit(1)
            }
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 9)
            .padding(.vertical, 4.5)
            .background(
                Capsule(style: .continuous)
                    .fill(isActive ? tint.opacity(0.16) : ThingsMacPalette.subtleContainer)
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(
                        isActive ? tint.opacity(0.42) : Color.primary.opacity(0.08),
                        lineWidth: 0.75
                    )
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .thingsTooltip(title)
    }

    private func inspectorPropertyPillLabel(
        title: String,
        systemImage: String,
        isActive: Bool,
        tint: Color
    ) -> some View {
        HStack(spacing: 5) {
            Image(systemName: systemImage)
                .font(.system(size: 11, weight: .semibold))
            Text(title)
                .font(.system(size: 12, weight: isActive ? .semibold : .medium))
                .lineLimit(1)
        }
        .foregroundStyle(isActive ? tint : Color.primary.opacity(0.88))
        .padding(.horizontal, 10)
        .padding(.vertical, 5.5)
        .background(
            RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                .fill(isActive ? tint.opacity(0.12) : ThingsMacPalette.cardSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                        .strokeBorder(
                            isActive ? tint.opacity(0.34) : Color.primary.opacity(0.11),
                            lineWidth: 0.85
                        )
                )
        )
    }

    private func customUpcomingOrReminderLabel(for task: TaskItem, isTomorrow: Bool) -> String? {
        var parts: [String] = []
        if task.scheduleState == .upcoming && !isTomorrow, let w = task.whenDate {
            let df = DateFormatter()
            df.locale = store.preferences.language.locale
            df.dateStyle = .short
            parts.append(df.string(from: DateEpochConverter.date(fromMillis: w)))
        }
        if let rem = task.reminderTime {
            parts.append(shortTimeString(rem))
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private func contextBreadcrumb(for target: TaskItem) -> String? {
        if let pid = target.projectId, let proj = store.project(byId: pid) {
            if let aid = proj.areaId, let area = store.area(byId: aid) {
                return "\(area.title) › \(proj.title)"
            }
            return proj.title
        }
        if let aid = target.areaId, let area = store.area(byId: aid) {
            return area.title
        }
        return nil
    }

    private func effortLocalizedTitle(for effort: TaskEffortLevel) -> String {
        switch effort {
        case .quickWin:
            return store.tr("v30_effort_quick_win")
        case .medium:
            return store.tr("v30_effort_medium")
        case .deepFocus:
            return store.tr("v30_effort_deep_focus")
        }
    }

    private func effortTint(for effort: TaskEffortLevel) -> Color {
        switch effort {
        case .quickWin:
            return ThingsMacPalette.anytimeTeal
        case .medium:
            return ThingsMacPalette.accentBlue
        case .deepFocus:
            return ThingsMacPalette.eveningPurple
        }
    }

    private func deadlineSummaryInfo(for millis: Int64) -> (label: String, isUrgent: Bool) {
        let todayDay = DateEpochConverter.epochDay(for: Date())
        let dlDay = DateEpochConverter.epochDay(forMillis: millis)
        let diff = dlDay - todayDay
        let isUrgent = diff <= 1
        let label: String = {
            if diff == 0 { return store.tr("deadline_today") }
            if diff < 0 { return store.trf("deadline_days_overdue", abs(diff)) }
            return store.trf("deadline_days_left", diff)
        }()
        return (label, isUrgent)
    }

    private func shortTimeString(_ millis: Int64) -> String {
        let df = DateFormatter()
        df.timeStyle = .short
        return df.string(from: DateEpochConverter.date(fromMillis: millis))
    }
}

private struct InspectorHeaderIconButton: View {
    let systemImage: String
    let tint: Color
    let helpText: String
    let action: () -> Void

    @State private var isHovering = false

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(isHovering ? ThingsMacPalette.accentBlue : tint)
                .frame(width: 26, height: 26)
                .background(
                    RoundedRectangle(cornerRadius: 6.5, style: .continuous)
                        .fill(isHovering ? ThingsMacPalette.accentBlue.opacity(0.11) : Color.primary.opacity(0.04))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 6.5, style: .continuous)
                        .strokeBorder(
                            isHovering ? ThingsMacPalette.accentBlue.opacity(0.28) : Color.primary.opacity(0.07),
                            lineWidth: 0.75
                        )
                )
                .contentShape(RoundedRectangle(cornerRadius: 6.5, style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovering = hovering
            }
        }
        .thingsTooltip(helpText)
    }
}

private struct InspectorChecklistRow: View {
    @ObservedObject var store: ThingsbookStore
    let item: ChecklistItem
    let onToggle: () -> Void
    let onDelete: () -> Void

    @State private var draftText: String = ""
    @State private var saveTask: Task<Void, Never>? = nil
    @State private var isHovering = false

    var body: some View {
        HStack(spacing: 7) {
            Button(action: {
                saveTask?.cancel()
                commitTitleIfNeeded()
                onToggle()
            }) {
                Image(systemName: item.isCompleted ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 13.5))
                    .foregroundStyle(item.isCompleted ? ThingsMacPalette.accentBlue : .secondary)
            }
            .buttonStyle(.plain)
            .thingsTooltip(store.tr(item.isCompleted ? "action_reopen_todo" : "action_complete"))

            TextField("", text: $draftText)
                .textFieldStyle(.plain)
                .font(.system(size: 12.5))
                .foregroundStyle(item.isCompleted ? .secondary : .primary)
                .strikethrough(item.isCompleted, color: .secondary)
                .onSubmit {
                    saveTask?.cancel()
                    commitTitleIfNeeded()
                }

            if isHovering {
                Button(action: {
                    saveTask?.cancel()
                    onDelete()
                }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 9.5, weight: .bold))
                        .foregroundStyle(.tertiary)
                        .frame(width: 18, height: 18)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("action_delete"))
            }
        }
        .padding(.vertical, 2)
        .onHover { hovering in
            isHovering = hovering
        }
        .onAppear {
            draftText = item.title
        }
        .onDisappear {
            saveTask?.cancel()
            commitTitleIfNeeded()
        }
        .onChange(of: item.title) { _, updated in
            if draftText != updated {
                draftText = updated
            }
        }
        .onChange(of: draftText) { _, _ in
            saveTask?.cancel()
            saveTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 400_000_000)
                guard !Task.isCancelled else { return }
                commitTitleIfNeeded()
            }
        }
    }

    private func commitTitleIfNeeded() {
        let clean = draftText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty,
              let fresh = store.checklistItems(for: item.taskId).first(where: { $0.id == item.id }),
              clean != fresh.title else {
            return
        }
        var copy = fresh
        copy.title = clean
        try? store.database.updateChecklistItem(copy)
        store.reloadSnapshot()
    }
}
