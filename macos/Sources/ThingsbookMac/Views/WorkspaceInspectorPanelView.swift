import SwiftUI
import AppKit
import UniformTypeIdentifiers
import ThingsbookCore

public struct WorkspaceInspectorPanelView: View {
    @ObservedObject var store: ThingsbookStore

    @StateObject private var voiceDictation = MacVoiceDictationController()
    @State private var activeDraftTaskId: String? = nil
    @State private var draftTitle: String = ""
    @State private var draftNotes: String = ""
    @State private var newChecklistTitle: String = ""
    @State private var dictationTargetsTitle: Bool = false
    @State private var dictationBaseNotes: String = ""
    @State private var draftSaveTask: Task<Void, Never>? = nil

    @State private var isWhenPopoverOpen = false
    @State private var isDeadlinePopoverOpen = false
    @State private var isTimeblockPopoverOpen = false
    @State private var isRepeatPopoverOpen = false
    @State private var isDependenciesPopoverOpen = false
    @State private var isCustomDurationPopoverOpen = false
    @State private var isMovePopoverOpen = false
    @State private var isTagPopoverOpen = false

    @State private var isGeneratingChecklist = false
    @State private var isEditingNotes = false
    @State private var isFileDropTargeted = false
    @State private var isHoveringMic = false
    @State private var isHoveringMoreMenu = false
    @State private var isHoveringMagic = false
    @State private var isHoveringAttachBar = false

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

    private var parsedDraftSyntax: ParsedSmartSyntax {
        store.parseSmartSyntax(draftTitle)
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
        .overlay(
            Rectangle()
                .strokeBorder(
                    (isFileDropTargeted && singleSelectedTask != nil)
                        ? ThingsMacPalette.accentBlue
                        : Color.clear,
                    lineWidth: 1.8
                )
                .allowsHitTesting(false)
        )
        .onDrop(of: [UTType.fileURL], isTargeted: $isFileDropTargeted) { providers in
            guard let task = singleSelectedTask else { return false }
            draftSaveTask?.cancel()
            commitDraftEditsIfNeeded()
            return store.handleFileDropProviders(providers, taskId: task.id)
        }
        .onAppear {
            syncDraftsForSelectedTask(singleSelectedTask)
        }
        .onDisappear {
            draftSaveTask?.cancel()
            voiceDictation.stopRecording()
            commitDraftEditsIfNeeded(commitPendingChecklist: true)
        }
        .onChange(of: singleSelectedTask?.id) { _, _ in
            draftSaveTask?.cancel()
            voiceDictation.stopRecording()
            commitDraftEditsIfNeeded(commitPendingChecklist: true)
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
        HStack(spacing: 6) {
            Image(systemName: task.isMilestone ? "diamond.fill" : "slider.horizontal.3")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(task.isMilestone ? ThingsMacPalette.milestoneViolet : ThingsMacPalette.accentBlue)

            Text(store.tr("v30_inspector_title"))
                .font(.system(size: 13.5, weight: .bold))
                .foregroundStyle(.primary)
                .lineLimit(1)

            Spacer(minLength: 4)

            // Live Voice Dictation Button
            Button {
                toggleVoiceDictation()
            } label: {
                Image(systemName: voiceDictation.isRecording ? "waveform" : "mic.fill")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(
                        voiceDictation.isRecording
                            ? .white
                            : (isHoveringMic ? ThingsMacPalette.accentBlue : .secondary)
                    )
                    .frame(width: 26, height: 26)
                    .background(
                        RoundedRectangle(cornerRadius: 6.5, style: .continuous)
                            .fill(
                                voiceDictation.isRecording
                                    ? ThingsMacPalette.deadlineCrimson
                                    : (isHoveringMic ? ThingsMacPalette.accentBlue.opacity(0.11) : Color.primary.opacity(0.04))
                            )
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 6.5, style: .continuous)
                            .strokeBorder(
                                voiceDictation.isRecording
                                    ? ThingsMacPalette.deadlineCrimson
                                    : (isHoveringMic ? ThingsMacPalette.accentBlue.opacity(0.28) : Color.primary.opacity(0.07)),
                                lineWidth: 0.75
                            )
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 6.5, style: .continuous))
            }
            .buttonStyle(.plain)
            .onHover { hovering in
                withAnimation(.easeOut(duration: 0.12)) {
                    isHoveringMic = hovering
                }
            }
            .thingsTooltip(store.tr(voiceDictation.isRecording ? "macos_voice_stop" : "voice_capture_btn"))

            // More Actions Menu (Duplicate, Copy Markdown, Cancel/Reopen, Delete)
            Menu {
                Button {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded(commitPendingChecklist: true)
                    let fresh = store.task(byId: task.id) ?? task
                    if let copy = try? store.database.duplicateTask(fresh.id, copySuffix: store.tr("v25_copy_suffix")) {
                        store.reloadSnapshot()
                        store.selectedTaskIds = [copy.id]
                        store.lastSelectedTaskId = copy.id
                    }
                } label: {
                    Label(store.tr("action_duplicate_todo"), systemImage: "plus.square.on.square")
                }

                Button {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded(commitPendingChecklist: true)
                    let fresh = store.task(byId: task.id) ?? task
                    store.copyTaskToPasteboard(fresh)
                } label: {
                    Label(store.tr("action_copy_todo"), systemImage: "doc.on.doc")
                }

                Divider()

                if task.status == .completed {
                    Button {
                        draftSaveTask?.cancel()
                        commitDraftEditsIfNeeded(commitPendingChecklist: true)
                        let fresh = store.task(byId: task.id) ?? task
                        store.toggleTaskCompletion(fresh)
                    } label: {
                        Label(store.tr("action_reopen_todo"), systemImage: "arrow.uturn.backward.circle")
                    }
                }

                Button {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded(commitPendingChecklist: true)
                    let fresh = store.task(byId: task.id) ?? task
                    store.cancelTask(fresh)
                } label: {
                    Label(
                        task.status == .canceled ? store.tr("action_reopen_todo") : store.tr("action_cancel_todo"),
                        systemImage: task.status == .canceled ? "arrow.uturn.backward.circle" : "xmark.circle"
                    )
                }

                Divider()

                Button(role: .destructive) {
                    draftSaveTask?.cancel()
                    voiceDictation.stopRecording()
                    activeDraftTaskId = nil
                    let fresh = store.taskIncludingTrash(byId: task.id) ?? task
                    store.deleteTask(fresh)
                } label: {
                    Label(store.tr("action_delete"), systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis.circle")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(isHoveringMoreMenu ? ThingsMacPalette.accentBlue : .secondary)
                    .frame(width: 26, height: 26)
                    .background(
                        RoundedRectangle(cornerRadius: 6.5, style: .continuous)
                            .fill(isHoveringMoreMenu ? ThingsMacPalette.accentBlue.opacity(0.11) : Color.primary.opacity(0.04))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 6.5, style: .continuous)
                            .strokeBorder(
                                isHoveringMoreMenu ? ThingsMacPalette.accentBlue.opacity(0.28) : Color.primary.opacity(0.07),
                                lineWidth: 0.75
                            )
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 6.5, style: .continuous))
            }
            .menuStyle(.borderlessButton)
            .menuIndicator(.hidden)
            .fixedSize()
            .onHover { hovering in
                withAnimation(.easeOut(duration: 0.12)) {
                    isHoveringMoreMenu = hovering
                }
            }
            .thingsTooltip(store.tr("action_more_options"))

            InspectorHeaderIconButton(
                systemImage: "arrow.up.left.and.arrow.down.right",
                tint: ThingsMacPalette.accentBlue,
                helpText: store.tr("v30_open_details_action")
            ) {
                draftSaveTask?.cancel()
                voiceDictation.stopRecording()
                commitDraftEditsIfNeeded(commitPendingChecklist: true)
                store.openTaskModal(task.id)
            }

            InspectorHeaderIconButton(
                systemImage: "xmark",
                tint: .secondary,
                helpText: store.tr("context_deselect_task")
            ) {
                draftSaveTask?.cancel()
                voiceDictation.stopRecording()
                commitDraftEditsIfNeeded(commitPendingChecklist: true)
                store.clearSelection()
            }

            InspectorHeaderIconButton(
                systemImage: "sidebar.right",
                tint: .secondary,
                helpText: store.tr("v30_inspector_toggle_tooltip")
            ) {
                draftSaveTask?.cancel()
                voiceDictation.stopRecording()
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
        if voiceDictation.isRecording {
            HStack(spacing: 7) {
                Circle()
                    .fill(ThingsMacPalette.deadlineCrimson)
                    .frame(width: 7, height: 7)
                Text(store.tr("macos_voice_listening"))
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(ThingsMacPalette.deadlineCrimson.opacity(0.09))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(ThingsMacPalette.deadlineCrimson.opacity(0.22), lineWidth: 0.85)
                    )
            )
        }

        titleAndFlagsCard(for: task)
        scheduleAndPropertiesCard(for: task)
        checklistCard(for: task)
        notesCard(for: task)
        attachmentsCard(for: task)
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

            if parsedDraftSyntax.hasAnyMatch {
                SmartSyntaxPreviewPillsView(
                    store: store,
                    parsed: parsedDraftSyntax,
                    showDetectedLabel: true,
                    onApply: {
                        applyDraftSmartSyntax(for: task)
                    }
                )
            }

            ThingsFlowLayout(horizontalSpacing: 7, verticalSpacing: 6) {
                if task.status == .open {
                    InspectorFlagChipButton(
                        title: store.tr(task.isInProgress ? "v33_in_progress_badge" : "v33_mark_in_progress"),
                        systemImage: "circle.lefthalf.filled",
                        isActive: task.isInProgress,
                        activeTint: ThingsMacPalette.somedayAmber,
                        helpText: store.tr(task.isInProgress ? "v33_clear_in_progress" : "v33_mark_in_progress")
                    ) {
                        draftSaveTask?.cancel()
                        commitDraftEditsIfNeeded()
                        store.toggleTaskInProgress(task)
                    }
                }

                InspectorFlagChipButton(
                    title: store.tr(task.isPinned ? "v25_pinned_badge" : "v25_pin_task"),
                    systemImage: task.isPinned ? "pin.fill" : "pin",
                    isActive: task.isPinned,
                    activeTint: ThingsMacPalette.todayYellow,
                    helpText: store.tr(task.isPinned ? "v25_unpin_task" : "v25_pin_task")
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    store.setTasksPinned([task.id], isPinned: !task.isPinned)
                }

                InspectorFlagChipButton(
                    title: store.tr("v30_milestone_label"),
                    systemImage: task.isMilestone ? "diamond.fill" : "diamond",
                    isActive: task.isMilestone,
                    activeTint: ThingsMacPalette.milestoneViolet,
                    helpText: store.tr(task.isMilestone ? "v30_remove_milestone" : "v30_mark_as_milestone")
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    store.setTasksMilestone([task.id], isMilestone: !task.isMilestone)
                }
            }
        }
        .padding(12)
        .background(inspectorBentoCardSurface())
    }

    private func scheduleAndPropertiesCard(for task: TaskItem) -> some View {
        let datePickerSpec = whenDateButtonSpec(for: task)
        let dlInfo = task.deadline.map { deadlineSummaryInfo(for: $0) }
        let currentEffort = task.resolvedEffort
        let currentDuration = (task.durationMinutes ?? 0) > 0 ? task.durationMinutes : nil
        let blockedLabel = store.blockedByBadgeText(for: task)
        let isBlockedActive = blockedLabel != nil || !task.blockedByTaskIds.isEmpty
        let breadcrumb = contextBreadcrumb(for: task)
        let projectHeadings: [Heading] = {
            guard let pid = task.projectId else { return [] }
            return (store.headingsByProject[pid] ?? [])
                .filter { !$0.isDeleted && !$0.isArchived }
                .sorted {
                    if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                    return $0.id < $1.id
                }
        }()
        let activeHeading = projectHeadings.first { $0.id == task.headingId }
        let isFocusRunningForTask = store.focusTimer?.taskId == task.id

        return VStack(alignment: .leading, spacing: 10) {
            inspectorSectionHeader(icon: "calendar", title: store.tr("action_when"))

            // Streamlined 3-control When row: [Today] + [This Evening] + [Date…] (+ optional active Reminder pill)
            ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                ThingsActionChipButton(
                    title: store.tr("view_today"),
                    systemImage: "sun.max.fill",
                    isActive: task.scheduleState == .today,
                    activeTint: ThingsMacPalette.todayYellow,
                    quietWhenInactive: true,
                    helpText: store.tr("view_today")
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    store.scheduleTasks([task.id], to: .today)
                }

                ThingsActionChipButton(
                    title: store.tr("view_this_evening"),
                    systemImage: "moon.stars.fill",
                    isActive: task.scheduleState == .thisEvening,
                    activeTint: ThingsMacPalette.eveningPurple,
                    quietWhenInactive: true,
                    helpText: store.tr("view_this_evening")
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    store.scheduleTasks([task.id], to: .thisEvening)
                }

                ThingsActionChipButton(
                    title: datePickerSpec.title,
                    systemImage: datePickerSpec.icon,
                    isActive: datePickerSpec.isActive,
                    activeTint: datePickerSpec.tint,
                    quietWhenInactive: true,
                    helpText: store.tr("context_when_date_reminder")
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    isWhenPopoverOpen = true
                }
                .popover(isPresented: $isWhenPopoverOpen) {
                    WhenPopoverView(
                        store: store,
                        currentSchedule: task.scheduleState,
                        currentWhenMillis: task.whenDate,
                        currentReminderMillis: task.reminderTime
                    ) { resolved in
                        var copy = store.task(byId: task.id) ?? task
                        copy.title = draftTitle
                        copy.notes = draftNotes
                        copy.scheduleState = resolved.scheduleState
                        copy.whenDate = resolved.whenDateEpochMillis
                        copy.reminderTime = resolved.reminderEpochMillis
                        store.updateTask(copy)
                    }
                }

                if let rem = task.reminderTime {
                    ThingsActionChipButton(
                        title: shortTimeString(rem),
                        systemImage: "bell.fill",
                        isActive: true,
                        quietWhenInactive: true,
                        helpText: store.trf("when_task_reminder_set", shortTimeString(rem))
                    ) {
                        draftSaveTask?.cancel()
                        commitDraftEditsIfNeeded()
                        isWhenPopoverOpen = true
                    }
                }
            }

            Divider()
                .opacity(0.55)

            // Deadline, Timeblock, Repeat, Duration, Effort & Dependencies Badges
            ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                ThingsActionChipButton(
                    title: dlInfo?.label ?? store.tr("action_deadline"),
                    systemImage: "flag.fill",
                    isActive: dlInfo != nil,
                    activeTint: (dlInfo?.isUrgent == true) ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue,
                    quietWhenInactive: true,
                    helpText: store.tr("context_adjust_deadline")
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    isDeadlinePopoverOpen = true
                }
                .popover(isPresented: $isDeadlinePopoverOpen) {
                    DeadlinePopoverView(store: store, currentDeadlineMillis: task.deadline) { dl in
                        var copy = store.task(byId: task.id) ?? task
                        copy.title = draftTitle
                        copy.notes = draftNotes
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
                    quietWhenInactive: true,
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

                ThingsActionChipButton(
                    title: task.recurrenceRule?.formatShortLabel(locale: store.preferences.language.locale) ?? store.tr("action_repeat"),
                    systemImage: "repeat",
                    isActive: task.recurrenceRule != nil,
                    quietWhenInactive: true,
                    helpText: repeatButtonTooltip(for: task)
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    isRepeatPopoverOpen = true
                }
                .popover(isPresented: $isRepeatPopoverOpen) {
                    RepeatEditorPopoverView(store: store, currentRule: task.recurrenceRule) { newRule in
                        var copy = store.task(byId: task.id) ?? task
                        copy.title = draftTitle
                        copy.notes = draftNotes
                        copy.recurrenceRule = newRule
                        if newRule != nil && copy.whenDate == nil {
                            copy.scheduleState = .today
                            copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
                        }
                        store.updateTask(copy)
                    }
                }

                // Standalone Duration Menu
                InspectorMenuPill(
                    title: currentDuration.map { TaskItem.formatCompactDuration($0) } ?? store.tr("v30_duration_label"),
                    systemImage: "timer",
                    isActive: currentDuration != nil,
                    activeTint: ThingsMacPalette.anytimeTeal,
                    helpText: store.tr("v30_duration_label")
                ) {
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
                }
                .popover(isPresented: $isCustomDurationPopoverOpen) {
                    CustomDurationPopoverView(
                        store: store,
                        currentDurationMinutes: currentDuration
                    ) { customMins in
                        store.setTaskStandaloneDuration(task.id, durationMinutes: customMins)
                    }
                }

                if let actualBadge = store.formattedActualTimeBadge(for: task) {
                    InspectorMenuPill(
                        title: actualBadge,
                        systemImage: "clock.arrow.circlepath",
                        isActive: true,
                        activeTint: ThingsMacPalette.logbookGreen,
                        helpText: actualBadge
                    ) {
                        Button(store.tr("v32_reset_actual_time"), role: .destructive) {
                            draftSaveTask?.cancel()
                            commitDraftEditsIfNeeded()
                            store.setTaskActualMinutes(task.id, actualMinutes: nil)
                        }
                    }
                }

                // Effort Menu
                InspectorMenuPill(
                    title: currentEffort.map { effortLocalizedTitle(for: $0) } ?? store.tr("v30_effort_label"),
                    systemImage: currentEffort?.sfSymbol ?? "bolt",
                    isActive: currentEffort != nil,
                    activeTint: currentEffort.map { effortTint(for: $0) } ?? ThingsMacPalette.accentBlue,
                    helpText: store.tr("v30_effort_label")
                ) {
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
                }

                // Dependencies / Blocked By Chip
                ThingsActionChipButton(
                    title: blockedLabel ?? store.tr("v30_blocked_by_label"),
                    systemImage: "hourglass",
                    isActive: isBlockedActive,
                    activeTint: ThingsMacPalette.somedayAmber,
                    quietWhenInactive: true,
                    helpText: blockedLabel ?? store.tr("v30_blocked_by_label")
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    isDependenciesPopoverOpen = true
                }
                .popover(isPresented: $isDependenciesPopoverOpen) {
                    DependenciesPopoverView(store: store, task: task)
                }
            }

            Divider()
                .opacity(0.55)

            // Area › Project › Heading Breadcrumb, Phase/Heading Menu & Tag Pills
            ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                InspectorWrappingChip(
                    title: breadcrumb ?? store.tr("action_move"),
                    systemImage: "folder",
                    isActive: task.projectId != nil || task.areaId != nil,
                    activeTint: ThingsMacPalette.accentBlue,
                    helpText: breadcrumb ?? store.tr("move_dialog_title")
                ) {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    isMovePopoverOpen = true
                }
                .popover(isPresented: $isMovePopoverOpen) {
                    MoveDestinationPopoverView(store: store, task: task) { projId, headId, areaId, schedOverride in
                        var copy = store.task(byId: task.id) ?? task
                        copy.title = draftTitle
                        copy.notes = draftNotes
                        copy.projectId = projId
                        copy.headingId = headId
                        copy.areaId = areaId
                        if let schedOverride {
                            copy.scheduleState = schedOverride
                        }
                        store.updateTask(copy)
                    }
                }

                if !projectHeadings.isEmpty {
                    InspectorMenuPill(
                        title: activeHeading?.title ?? store.tr("entity_heading"),
                        systemImage: "flag.checkered",
                        isActive: activeHeading != nil,
                        activeTint: ThingsMacPalette.accentBlue,
                        helpText: store.tr("entity_heading")
                    ) {
                        Button {
                            draftSaveTask?.cancel()
                            commitDraftEditsIfNeeded()
                            var copy = store.task(byId: task.id) ?? task
                            copy.title = draftTitle
                            copy.notes = draftNotes
                            copy.headingId = nil
                            store.updateTask(copy)
                        } label: {
                            if activeHeading == nil {
                                Label(store.tr("v30_board_ungrouped_column"), systemImage: "checkmark")
                            } else {
                                Text(store.tr("v30_board_ungrouped_column"))
                            }
                        }
                        Divider()
                        ForEach(projectHeadings) { heading in
                            Button {
                                draftSaveTask?.cancel()
                                commitDraftEditsIfNeeded()
                                var copy = store.task(byId: task.id) ?? task
                                copy.title = draftTitle
                                copy.notes = draftNotes
                                copy.headingId = heading.id
                                store.updateTask(copy)
                            } label: {
                                if activeHeading?.id == heading.id {
                                    Label(heading.title, systemImage: "checkmark")
                                } else {
                                    Text(heading.title)
                                }
                            }
                        }
                    }
                }

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
                    quietWhenInactive: true,
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
                            var copy = base
                            copy.title = draftTitle
                            copy.notes = draftNotes
                            store.updateTask(copy, tagIds: Array(tids))
                        }
                    )
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
                Image(systemName: "checklist")
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(.secondary)
                Text(store.tr("action_checklist"))
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

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

                Spacer(minLength: 4)

                Button {
                    runMagicBreakdown(for: task)
                } label: {
                    HStack(spacing: 4) {
                        if isGeneratingChecklist {
                            ProgressView().controlSize(.mini)
                        } else {
                            Image(systemName: "sparkles")
                                .font(.system(size: 10, weight: .semibold))
                        }
                        Text(store.tr("gemini_magic_breakdown"))
                            .font(.system(size: 10.5, weight: .semibold))
                            .lineLimit(1)
                    }
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3.5)
                    .background(
                        Capsule(style: .continuous)
                            .fill(ThingsMacPalette.accentBlue.opacity(isHoveringMagic ? 0.19 : 0.12))
                            .overlay(
                                Capsule(style: .continuous)
                                    .strokeBorder(
                                        ThingsMacPalette.accentBlue.opacity(isHoveringMagic ? 0.42 : 0.0),
                                        lineWidth: 0.8
                                    )
                            )
                    )
                }
                .buttonStyle(.plain)
                .disabled(isGeneratingChecklist)
                .onHover { hovering in
                    withAnimation(.easeOut(duration: 0.12)) {
                        isHoveringMagic = hovering
                    }
                }
                .thingsTooltip(store.tr("gemini_magic_breakdown_desc"))
            }

            if !items.isEmpty {
                VStack(alignment: .leading, spacing: 3) {
                    ForEach(items) { item in
                        ChecklistItemRowView(store: store, item: item, allItems: items)
                    }
                }
                .padding(.horizontal, 2)
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
        let hasNotes = !draftNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let links = MarkdownNotesParser.extractDetectedLinks(from: draftNotes)
        let toggleLabel = isEditingNotes ? store.tr("macos_preview_markdown") : store.tr("macos_edit_markdown")
        let noteLineCount = max(1, draftNotes.components(separatedBy: .newlines).count)
        let notesEditorHeight = min(196, max(92, CGFloat(noteLineCount) * 20 + 20))

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "doc.text")
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(.secondary)
                Text(store.tr("placeholder_notes"))
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Spacer(minLength: 4)

                if hasNotes || isEditingNotes {
                    Button {
                        isEditingNotes.toggle()
                        if isEditingNotes {
                            DispatchQueue.main.async {
                                isNotesFocused = true
                            }
                        }
                    } label: {
                        Text(toggleLabel)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(toggleLabel)
                }
            }

            if !isEditingNotes {
                Group {
                    if hasNotes {
                        Text(MarkdownNotesParser.renderAttributed(draftNotes))
                            .font(.system(size: 13))
                            .fixedSize(horizontal: false, vertical: true)
                    } else {
                        Text(store.tr("placeholder_notes"))
                            .font(.system(size: 13))
                            .foregroundStyle(.tertiary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 68, alignment: .topLeading)
                .padding(9)
                .background(
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(ThingsMacPalette.subtleContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 9, style: .continuous)
                                .strokeBorder(Color.primary.opacity(0.07), lineWidth: 0.75)
                        )
                )
                .contentShape(Rectangle())
                .onTapGesture {
                    isEditingNotes = true
                    DispatchQueue.main.async {
                        isNotesFocused = true
                    }
                }
            } else {
                ZStack(alignment: .topLeading) {
                    if draftNotes.isEmpty {
                        Text(store.tr("placeholder_notes"))
                            .font(.system(size: 13))
                            .foregroundStyle(.tertiary)
                            .padding(.top, 9)
                            .padding(.leading, 11)
                            .allowsHitTesting(false)
                    }
                    TextEditor(text: $draftNotes)
                        .font(.system(size: 13))
                        .scrollContentBackground(.hidden)
                        .focused($isNotesFocused)
                        .padding(6)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .frame(
                    maxWidth: .infinity,
                    minHeight: notesEditorHeight,
                    idealHeight: notesEditorHeight,
                    alignment: .topLeading
                )
                .background(
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(ThingsMacPalette.subtleContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 9, style: .continuous)
                                .strokeBorder(
                                    isNotesFocused ? ThingsMacPalette.accentBlue.opacity(0.42) : Color.primary.opacity(0.09),
                                    lineWidth: isNotesFocused ? 1.0 : 0.75
                                )
                        )
                )
            }

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

    private func attachmentsCard(for task: TaskItem) -> some View {
        Group {
            if !task.attachments.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    AttachmentsStripView(store: store, attachments: task.attachments) {
                        draftSaveTask?.cancel()
                        commitDraftEditsIfNeeded()
                        store.pickAndAttachFiles(taskId: task.id)
                    }
                }
                .padding(12)
                .background(inspectorBentoCardSurface())
            } else {
                Button {
                    draftSaveTask?.cancel()
                    commitDraftEditsIfNeeded()
                    store.pickAndAttachFiles(taskId: task.id)
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: isFileDropTargeted ? "arrow.down.doc.fill" : "paperclip")
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundStyle(isFileDropTargeted ? ThingsMacPalette.accentBlue : .secondary)

                        Text(store.tr("attachments_section_title"))
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundStyle(isFileDropTargeted ? ThingsMacPalette.accentBlue : .secondary)
                            .lineLimit(1)

                        Spacer(minLength: 6)

                        HStack(spacing: 3.5) {
                            Image(systemName: "plus")
                                .font(.system(size: 10, weight: .bold))
                            Text(store.tr("action_attach_file"))
                                .font(.system(size: 11, weight: .semibold))
                                .lineLimit(1)
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4)
                        .background(
                            Capsule(style: .continuous)
                                .fill(ThingsMacPalette.accentBlue.opacity(isHoveringAttachBar ? 0.18 : 0.11))
                        )
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9.5)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(
                                isFileDropTargeted
                                    ? ThingsMacPalette.accentBlue.opacity(0.08)
                                    : (isHoveringAttachBar ? ThingsMacPalette.accentBlue.opacity(0.045) : ThingsMacPalette.cardSurface)
                            )
                            .shadow(color: Color.black.opacity(0.035), radius: 5, x: 0, y: 1.5)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(
                                        isFileDropTargeted
                                            ? ThingsMacPalette.accentBlue
                                            : (isHoveringAttachBar ? ThingsMacPalette.accentBlue.opacity(0.32) : Color.primary.opacity(0.08)),
                                        lineWidth: isFileDropTargeted ? 1.3 : 0.75
                                    )
                            )
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .onHover { hovering in
                    withAnimation(.easeOut(duration: 0.12)) {
                        isHoveringAttachBar = hovering
                    }
                }
                .thingsTooltip(store.tr("macos_attachment_drop_hint"))
            }
        }
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
            isEditingNotes = false
            return
        }
        activeDraftTaskId = task.id
        draftTitle = task.title
        draftNotes = task.notes
        isEditingNotes = TaskCardInteractionRules.shouldAutoEditNotesOnExpand(notes: task.notes)
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

    private func applyDraftSmartSyntax(for task: TaskItem) {
        let parsed = parsedDraftSyntax
        guard parsed.hasAnyMatch else { return }
        draftSaveTask?.cancel()

        var copy = store.task(byId: task.id) ?? task
        copy.title = parsed.cleanTitle
        copy.notes = draftNotes

        if let sched = parsed.scheduleState {
            copy.scheduleState = sched
            copy.whenDate = parsed.whenDate
        }
        if let dl = parsed.deadline {
            copy.deadline = dl
        }
        if let dur = parsed.durationMinutes {
            copy.durationMinutes = dur
        }
        if parsed.isMilestone {
            copy.isMilestone = true
        }
        if parsed.isInProgress && copy.status == .open {
            copy.isInProgress = true
        }
        if let proj = parsed.matchedProject {
            copy.projectId = proj.id
            copy.headingId = nil
            copy.areaId = proj.areaId
            if copy.scheduleState == .inbox && parsed.scheduleState == nil {
                copy.scheduleState = .anytime
            }
        } else if let area = parsed.matchedArea {
            copy.projectId = nil
            copy.headingId = nil
            copy.areaId = area.id
            if copy.scheduleState == .inbox && parsed.scheduleState == nil {
                copy.scheduleState = .anytime
            }
        }

        var mergedTagIds = copy.tagIds
        for tid in parsed.matchedTagIds where !mergedTagIds.contains(tid) {
            mergedTagIds.append(tid)
        }

        draftTitle = parsed.cleanTitle
        store.updateTask(copy, tagIds: mergedTagIds)
    }

    private func toggleVoiceDictation() {
        if !voiceDictation.isRecording {
            dictationTargetsTitle = draftTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            dictationBaseNotes = draftNotes.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        voiceDictation.toggleRecording(
            locale: store.preferences.language.locale,
            onTranscript: { spoken in
                if dictationTargetsTitle {
                    let lines = spoken.components(separatedBy: .newlines)
                    draftTitle = String((lines.first ?? spoken).prefix(240))
                    if lines.count > 1 {
                        let extra = lines.dropFirst().joined(separator: "\n")
                        draftNotes = dictationBaseNotes.isEmpty ? extra : "\(dictationBaseNotes)\n\(extra)"
                    }
                } else {
                    draftNotes = dictationBaseNotes.isEmpty ? spoken : "\(dictationBaseNotes)\n\(spoken)"
                }
            },
            onUnavailable: {
                store.showToast(store.tr("voice_not_available"))
            }
        )
    }

    private func runMagicBreakdown(for task: TaskItem) {
        draftSaveTask?.cancel()
        commitDraftEditsIfNeeded()
        isGeneratingChecklist = true
        let base = store.task(byId: task.id) ?? task
        let titleToBreak = draftTitle.isEmpty ? base.title : draftTitle
        let notesToBreak = draftNotes
        let targetId = base.id
        Task {
            do {
                let steps = try await ThingsbookGeminiService.generateMagicChecklist(
                    taskTitle: titleToBreak,
                    taskNotes: notesToBreak,
                    lang: store.preferences.language
                )
                await MainActor.run {
                    for step in steps {
                        _ = try? store.database.addChecklistItem(taskId: targetId, title: step)
                    }
                    store.reloadSnapshot()
                    isGeneratingChecklist = false
                }
            } catch {
                await MainActor.run {
                    isGeneratingChecklist = false
                    store.showToast(error.localizedDescription)
                }
            }
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
        isRepeatPopoverOpen = false
        isDependenciesPopoverOpen = false
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

    private func whenDateButtonSpec(for task: TaskItem) -> (title: String, icon: String, isActive: Bool, tint: Color) {
        if task.scheduleState == .upcoming, let w = task.whenDate {
            let wDate = DateEpochConverter.date(fromMillis: w)
            if Calendar.current.isDateInTomorrow(wDate) {
                return (store.tr("date_tomorrow"), "calendar", true, ThingsMacPalette.accentBlue)
            }
            let df = DateFormatter()
            df.locale = store.preferences.language.locale
            df.setLocalizedDateFormatFromTemplate("EEEdMMM")
            return (df.string(from: wDate), "calendar", true, ThingsMacPalette.accentBlue)
        } else if task.scheduleState == .someday {
            return (store.tr("view_someday"), "cloud.fill", true, ThingsMacPalette.somedayAmber)
        } else {
            return (store.tr("repeat_ends_pick_date"), "calendar", false, ThingsMacPalette.accentBlue)
        }
    }

    private func repeatButtonTooltip(for target: TaskItem) -> String {
        let base = store.tr("context_configure_repeat")
        if let rule = target.recurrenceRule {
            return "\(base) — \(rule.formatShortLabel(locale: store.preferences.language.locale))"
        }
        return base
    }

    private func contextBreadcrumb(for target: TaskItem) -> String? {
        if let pid = target.projectId, let proj = store.project(byId: pid) {
            let activeHeadingTitle = target.headingId.flatMap { hid in
                (store.headingsByProject[pid] ?? []).first(where: { !$0.isDeleted && $0.id == hid })?.title
            }
            var parts: [String] = []
            if let aid = proj.areaId, let area = store.area(byId: aid) {
                parts.append(area.title)
            }
            parts.append(proj.title)
            if let headingTitle = activeHeadingTitle, !headingTitle.isEmpty {
                parts.append(headingTitle)
            }
            return parts.joined(separator: " › ")
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

private struct InspectorFlagChipButton: View {
    let title: String
    let systemImage: String
    let isActive: Bool
    let activeTint: Color
    let helpText: String
    let action: () -> Void

    @State private var isHovering = false

    var body: some View {
        let inactiveFg: Color = isHovering ? Color.primary : Color.secondary.opacity(0.82)

        Button(action: action) {
            HStack(spacing: 5) {
                if systemImage == "circle.lefthalf.filled" {
                    Image(systemName: systemImage)
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(isActive ? activeTint : inactiveFg)
                } else {
                    Image(systemName: systemImage)
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(isActive ? activeTint : inactiveFg)
                }
                Text(title)
                    .font(.system(size: 11.5, weight: isActive ? .semibold : .medium))
                    .lineLimit(1)
            }
            .foregroundStyle(isActive ? activeTint : inactiveFg)
            .padding(.horizontal, 9.5)
            .padding(.vertical, 5)
            .background(
                Capsule(style: .continuous)
                    .fill(
                        isActive
                            ? activeTint.opacity(isHovering ? 0.19 : 0.14)
                            : (isHovering ? Color.primary.opacity(0.06) : Color.clear)
                    )
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(
                        isActive ? activeTint.opacity(isHovering ? 0.48 : 0.36) : Color.clear,
                        lineWidth: isActive ? 0.85 : 0
                    )
            )
            .contentShape(Capsule(style: .continuous))
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

private struct InspectorMenuPill<MenuContent: View>: View {
    let title: String
    let systemImage: String
    let isActive: Bool
    let activeTint: Color
    let helpText: String
    @ViewBuilder let menuContent: () -> MenuContent

    @State private var isHovering = false

    var body: some View {
        Menu {
            menuContent()
        } label: {
            HStack(spacing: 5) {
                Image(systemName: systemImage)
                    .font(.system(size: 11, weight: .semibold))
                Text(title)
                    .font(.system(size: 12, weight: isActive ? .semibold : .medium))
                    .lineLimit(1)
            }
            .foregroundStyle(
                isActive
                    ? activeTint
                    : (isHovering ? Color.primary : Color.secondary.opacity(0.82))
            )
        }
        .menuStyle(.borderlessButton)
        .menuIndicator(.hidden)
        .fixedSize()
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(
            Capsule(style: .continuous)
                .fill(
                    isActive
                        ? activeTint.opacity(isHovering ? 0.19 : 0.14)
                        : (isHovering ? Color.primary.opacity(0.06) : Color.clear)
                )
        )
        .overlay(
            Capsule(style: .continuous)
                .strokeBorder(
                    isActive ? activeTint.opacity(isHovering ? 0.48 : 0.36) : Color.clear,
                    lineWidth: isActive ? 0.85 : 0
                )
        )
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovering = hovering
            }
        }
        .thingsTooltip(helpText)
    }
}

private struct InspectorWrappingChip: View {
    let title: String
    let systemImage: String
    let isActive: Bool
    let activeTint: Color
    let helpText: String
    let action: () -> Void

    @State private var isHovering = false

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 5.5) {
                Image(systemName: systemImage)
                    .font(.system(size: 11, weight: .semibold))
                    .padding(.top, 1.5)
                Text(title)
                    .font(.system(size: 12, weight: isActive ? .semibold : .medium))
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(
                isActive
                    ? activeTint
                    : (isHovering ? Color.primary : Color.secondary.opacity(0.82))
            )
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .fill(
                        isActive
                            ? activeTint.opacity(isHovering ? 0.19 : 0.14)
                            : (isHovering ? Color.primary.opacity(0.06) : Color.clear)
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .strokeBorder(
                        isActive ? activeTint.opacity(isHovering ? 0.48 : 0.36) : Color.clear,
                        lineWidth: isActive ? 0.85 : 0
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
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
