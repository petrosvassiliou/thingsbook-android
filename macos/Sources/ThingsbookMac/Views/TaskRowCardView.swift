import SwiftUI
import AppKit
import UniformTypeIdentifiers
import ThingsbookCore

public struct TaskRowCardView: View {
    @ObservedObject var store: ThingsbookStore
    public let task: TaskItem
    public var showContextSubtitle: Bool = true

    @StateObject private var rowVoiceController = MacVoiceDictationController()
    @State private var draftTitle: String = ""
    @State private var draftNotes: String = ""
    @State private var newChecklistTitle: String = ""
    @State private var isWhenPopoverOpen = false
    @State private var isDeadlinePopoverOpen = false
    @State private var isRepeatPopoverOpen = false
    @State private var isMovePopoverOpen = false
    @State private var isTagPopoverOpen = false
    @State private var isTimeblockPopoverOpen = false
    @State private var isCollapsedTimeblockPopoverOpen = false
    @State private var isGeneratingChecklist = false
    @State private var isEditingNotes = false
    @State private var isHoveringRow = false
    @State private var isHoveringDone = false
    @State private var isFileDropTargeted = false
    @State private var draftSaveTask: Task<Void, Never>?
    @FocusState private var isTitleFocused: Bool

    private var isExpanded: Bool {
        store.expandedTaskId == task.id
    }

    private var isSelected: Bool {
        store.selectedTaskIds.contains(task.id)
    }

    private var latestTask: TaskItem {
        (store.allOpenTasks + store.logbookTasks + store.trashedTasks).first(where: { $0.id == task.id }) ?? task
    }

    public init(store: ThingsbookStore, task: TaskItem, showContextSubtitle: Bool = true) {
        self.store = store
        self.task = task
        self.showContextSubtitle = showContextSubtitle
    }

    public var body: some View {
        Group {
            if isExpanded {
                expandedCard
            } else {
                collapsedRow
            }
        }
        .contextMenu {
            taskContextMenu
        }
        .onAppear {
            syncDrafts()
            if isExpanded {
                applyInitialExpandFocusState()
            }
        }
        .onDisappear {
            draftSaveTask?.cancel()
            commitDraftEditsIfNeeded(commitPendingChecklist: true)
        }
        .onChange(of: task.title) { _, newValue in
            if !isExpanded && draftTitle != newValue {
                draftTitle = newValue
            }
        }
        .onChange(of: task.notes) { _, newValue in
            if !isExpanded && draftNotes != newValue {
                draftNotes = newValue
            }
        }
        .onChange(of: draftTitle) { _, _ in
            if isExpanded {
                scheduleDraftAutoSave()
            }
        }
        .onChange(of: draftNotes) { _, _ in
            if isExpanded {
                scheduleDraftAutoSave()
            }
        }
        .onChange(of: isExpanded) { _, expanded in
            if expanded {
                syncDrafts()
                applyInitialExpandFocusState()
            } else {
                draftSaveTask?.cancel()
                rowVoiceController.stopRecording()
                commitDraftEditsIfNeeded(commitPendingChecklist: true)
            }
        }
    }

    // MARK: - Collapsed Things-for-Mac Row

    private var collapsedRow: some View {
        HStack(alignment: .center, spacing: 12) {
            ThingsCheckboxButton(
                status: task.status,
                isSomeday: task.scheduleState == .someday,
                helpText: store.tr("macos_checkbox_help"),
                onToggleComplete: { store.toggleTaskCompletion(latestTask) },
                onCancelTask: { store.cancelTask(latestTask) }
            )

            VStack(alignment: .leading, spacing: 2.5) {
                HStack(spacing: 6) {
                    if task.scheduleState == .thisEvening && store.selectedDestination != .smartView(.today) {
                        Image(systemName: "moon.fill")
                            .font(.system(size: 10.5))
                            .foregroundStyle(ThingsMacPalette.eveningPurple)
                    }

                    Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(
                            task.status == .open
                                ? (task.title.isEmpty ? Color.secondary : Color.primary)
                                : Color.secondary
                        )
                        .strikethrough(task.status == .canceled, color: .secondary)
                        .lineLimit(1)

                    if !task.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !showNotesPreviewInCollapsed {
                        Image(systemName: "doc.text")
                            .font(.system(size: 10.5))
                            .foregroundStyle(.tertiary)
                    }
                }

                if showContextSubtitle, let subtitle = contextBreadcrumb {
                    Text(subtitle)
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                } else if showNotesPreviewInCollapsed {
                    Text(task.notes.replacingOccurrences(of: "\n", with: " "))
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 8)

            // Trailing metadata badges
            HStack(spacing: 6) {
                if !task.attachments.isEmpty {
                    HStack(spacing: 3) {
                        Image(systemName: "paperclip")
                            .font(.system(size: 10, weight: .semibold))
                        Text("\(task.attachments.count)")
                            .font(.system(size: 11, weight: .medium, design: .rounded))
                    }
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2.5)
                    .background(Capsule().fill(Color.primary.opacity(0.055)))
                    .thingsTooltip("\(store.tr("attachments_section_title")): \(task.attachments.count)")
                }

                if let tbPill = task.formattedTimeblockPill {
                    Button {
                        isCollapsedTimeblockPopoverOpen = true
                    } label: {
                        HStack(spacing: 3.5) {
                            Image(systemName: "clock.badge.checkmark")
                                .font(.system(size: 10, weight: .semibold))
                            Text(tbPill)
                                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                        }
                        .foregroundStyle(ThingsMacPalette.anytimeTeal)
                        .padding(.horizontal, 7.5)
                        .padding(.vertical, 2.5)
                        .background(Capsule().fill(ThingsMacPalette.anytimeTeal.opacity(0.12)))
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip("\(store.tr("macos_tooltip_timeblock")) — \(tbPill)")
                    .popover(isPresented: $isCollapsedTimeblockPopoverOpen) {
                        TimeblockPopoverView(
                            store: store,
                            currentStartMinutes: latestTask.scheduledStartMinutes,
                            currentDurationMinutes: latestTask.durationMinutes
                        ) { startMins, durMins in
                            store.setTaskTimeblock(latestTask.id, startMinutes: startMins, durationMinutes: durMins)
                        }
                    }
                }

                if let rem = task.reminderTime {
                    HStack(spacing: 3) {
                        Image(systemName: "bell")
                            .font(.system(size: 10, weight: .semibold))
                        Text(shortTimeString(rem))
                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                    }
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2.5)
                    .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.11)))
                    .thingsTooltip(store.trf("when_task_reminder_set", shortTimeString(rem)))
                }

                if !task.checklistItems.isEmpty {
                    let done = task.checklistItems.filter(\.isCompleted).count
                    HStack(spacing: 3.5) {
                        Image(systemName: "checklist")
                            .font(.system(size: 10))
                        Text("\(done)/\(task.checklistItems.count)")
                            .font(.system(size: 11, weight: .medium, design: .rounded))
                    }
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2.5)
                    .background(Capsule().fill(Color.primary.opacity(0.055)))
                    .thingsTooltip("\(store.tr("placeholder_checklist")): \(done)/\(task.checklistItems.count)")
                }

                if let rule = task.recurrenceRule {
                    HStack(spacing: 3.5) {
                        Image(systemName: "repeat")
                            .font(.system(size: 10))
                        Text(rule.formatShortLabel(locale: store.preferences.language.locale))
                            .font(.system(size: 11, weight: .medium))
                    }
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2.5)
                    .background(Capsule().fill(Color.primary.opacity(0.055)))
                    .thingsTooltip("\(store.tr("action_repeat")): \(rule.formatShortLabel(locale: store.preferences.language.locale))")
                }

                ForEach(task.tags.prefix(3)) { tag in
                    TagPillBadge(
                        title: tag.title,
                        isSelected: store.selectedFilterTagId == tag.id,
                        showIcon: true
                    ) {
                        store.selectedFilterTagId = (store.selectedFilterTagId == tag.id) ? nil : tag.id
                    }
                }

                if let dl = task.deadline {
                    deadlineChip(dl)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8.5)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(
                    isSelected
                        ? ThingsMacPalette.accentBlue.opacity(0.13)
                        : (isHoveringRow ? Color.primary.opacity(0.04) : Color.clear)
                )
        )
        .contentShape(Rectangle())
        .onHover { hovering in
            isHoveringRow = hovering
        }
        .onTapGesture {
            if NSEvent.modifierFlags.contains(.command) {
                if store.selectedTaskIds.contains(task.id) {
                    store.selectedTaskIds.remove(task.id)
                } else {
                    store.selectedTaskIds.insert(task.id)
                }
            } else {
                withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                    store.selectedTaskIds = [task.id]
                    store.expandedTaskId = task.id
                }
            }
        }
    }

    private var showNotesPreviewInCollapsed: Bool {
        !showContextSubtitle && !task.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    // MARK: - Inline Spring-Expanded Things Card

    private var expandedCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Top Title Row + Inline Dictation Mic
            HStack(alignment: .center, spacing: 12) {
                ThingsCheckboxButton(
                    status: task.status,
                    isSomeday: task.scheduleState == .someday,
                    helpText: store.tr("macos_checkbox_help"),
                    onToggleComplete: {
                        commitDraftEditsIfNeeded(commitPendingChecklist: true)
                        store.toggleTaskCompletion(latestTask)
                    },
                    onCancelTask: {
                        commitDraftEditsIfNeeded(commitPendingChecklist: true)
                        store.cancelTask(latestTask)
                    }
                )

                TextField(store.tr("placeholder_todo_title"), text: $draftTitle)
                    .textFieldStyle(.plain)
                    .font(.system(size: 16, weight: .semibold))
                    .focused($isTitleFocused)
                    .onSubmit {
                        commitDraftEditsIfNeeded(commitPendingChecklist: true)
                        withAnimation(.spring(response: 0.25, dampingFraction: 0.86)) {
                            store.expandedTaskId = nil
                        }
                    }

                Spacer()

                if rowVoiceController.isRecording {
                    Button {
                        rowVoiceController.stopRecording()
                    } label: {
                        Image(systemName: "waveform.circle.fill")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(store.tr("macos_voice_stop"))
                }
            }

            // Markdown Notes Box + Detected Smart Link Chips
            VStack(alignment: .leading, spacing: 8) {
                if !isEditingNotes {
                    let hasNotes = !draftNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    Group {
                        if hasNotes {
                            Text(MarkdownNotesParser.renderAttributed(draftNotes))
                                .font(.system(size: 13.5))
                                .frame(maxWidth: .infinity, alignment: .leading)
                        } else {
                            Text(store.tr("placeholder_notes"))
                                .font(.system(size: 13.5))
                                .foregroundStyle(.tertiary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    .padding(11)
                    .background(
                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                            .fill(ThingsMacPalette.subtleContainer)
                            .overlay(
                                RoundedRectangle(cornerRadius: 9, style: .continuous)
                                    .strokeBorder(Color.primary.opacity(0.06), lineWidth: 0.8)
                            )
                    )
                    .contentShape(Rectangle())
                    .onTapGesture {
                        isEditingNotes = true
                    }
                } else {
                    ZStack(alignment: .topLeading) {
                        if draftNotes.isEmpty {
                            Text(store.tr("placeholder_notes"))
                                .font(.system(size: 13.5))
                                .foregroundStyle(.tertiary)
                                .padding(.top, 9)
                                .padding(.leading, 11)
                                .allowsHitTesting(false)
                        }
                        TextEditor(text: $draftNotes)
                            .font(.system(size: 13.5))
                            .scrollContentBackground(.hidden)
                            .padding(6)
                            .frame(minHeight: 62, maxHeight: 170)
                            .background(
                                RoundedRectangle(cornerRadius: 9, style: .continuous)
                                    .fill(ThingsMacPalette.subtleContainer)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                                            .strokeBorder(Color.primary.opacity(0.07), lineWidth: 0.8)
                                    )
                            )
                    }
                }

                let links = MarkdownNotesParser.extractDetectedLinks(from: draftNotes)
                if !links.isEmpty || !draftNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    HStack(spacing: 6) {
                        ForEach(links) { link in
                            Button {
                                if let url = URL(string: link.targetUri) {
                                    NSWorkspace.shared.open(url)
                                }
                            } label: {
                                HStack(spacing: 5) {
                                    Image(systemName: link.type.sfSymbol)
                                        .font(.system(size: 10.5, weight: .semibold))
                                    Text("\(link.displayLabel) ↗")
                                        .font(.system(size: 11.5, weight: .semibold))
                                        .lineLimit(1)
                                }
                                .fixedSize(horizontal: true, vertical: false)
                                .foregroundStyle(ThingsMacPalette.accentBlue)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(
                                    Capsule(style: .continuous)
                                        .fill(ThingsMacPalette.accentBlue.opacity(0.10))
                                        .overlay(
                                            Capsule(style: .continuous)
                                                .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.24), lineWidth: 0.8)
                                        )
                                )
                            }
                            .buttonStyle(.plain)
                            .thingsTooltip(link.targetUri)
                        }

                        Spacer()

                        if !draftNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            let toggleLabel = isEditingNotes ? store.tr("macos_preview_markdown") : store.tr("macos_edit_markdown")
                            Button(toggleLabel) {
                                isEditingNotes.toggle()
                            }
                            .font(.system(size: 11.5, weight: .medium))
                            .buttonStyle(.plain)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: true, vertical: false)
                            .thingsTooltip(toggleLabel)
                        }
                    }
                }
            }
            .padding(.leading, 30)

            // File & Image Attachments Strip (v1.8)
            if !task.attachments.isEmpty || isFileDropTargeted {
                VStack(alignment: .leading, spacing: 6) {
                    if !task.attachments.isEmpty {
                        AttachmentsStripView(store: store, attachments: task.attachments) {
                            commitDraftEditsIfNeeded()
                            store.pickAndAttachFiles(taskId: task.id)
                        }
                    }
                    if isFileDropTargeted {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.down.doc.fill")
                                .foregroundStyle(ThingsMacPalette.accentBlue)
                            Text(store.tr("macos_attachment_drop_hint"))
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(ThingsMacPalette.accentBlue)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 9, style: .continuous)
                                .strokeBorder(ThingsMacPalette.accentBlue, style: StrokeStyle(lineWidth: 1.4, dash: [5, 4]))
                                .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(ThingsMacPalette.accentBlue.opacity(0.06)))
                        )
                    }
                }
                .padding(.leading, 30)
            }

            // Checklist Section + Gemini Magic Breakdown
            VStack(alignment: .leading, spacing: 6) {
                if !task.checklistItems.isEmpty {
                    let done = task.checklistItems.filter(\.isCompleted).count
                    Text("\(store.tr("placeholder_checklist")) (\(done)/\(task.checklistItems.count))")
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundStyle(.secondary)
                        .padding(.top, 2)
                }

                ForEach(task.checklistItems) { item in
                    ChecklistItemRowView(store: store, item: item, allItems: task.checklistItems)
                }

                HStack(spacing: 8) {
                    Image(systemName: "checklist")
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .frame(width: 16)

                    TextField(store.tr("placeholder_add_checklist_item"), text: $newChecklistTitle)
                        .textFieldStyle(.plain)
                        .font(.system(size: 13))
                        .onSubmit {
                            let clean = newChecklistTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard !clean.isEmpty else { return }
                            commitDraftEditsIfNeeded()
                            _ = try? store.database.addChecklistItem(taskId: task.id, title: clean)
                            newChecklistTitle = ""
                        }

                    Spacer()

                    Button {
                        runMagicBreakdown()
                    } label: {
                        HStack(spacing: 5) {
                            if isGeneratingChecklist {
                                ProgressView().controlSize(.mini)
                            } else {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 11, weight: .semibold))
                            }
                            Text(store.tr("gemini_magic_breakdown"))
                                .font(.system(size: 11.5, weight: .semibold))
                                .lineLimit(1)
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.12)))
                    }
                    .buttonStyle(.plain)
                    .fixedSize(horizontal: true, vertical: false)
                    .disabled(isGeneratingChecklist)
                    .thingsTooltip(store.tr("gemini_magic_breakdown_desc"))
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(ThingsMacPalette.subtleContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .strokeBorder(Color.primary.opacity(0.07), lineWidth: 0.8)
                        )
                )
            }
            .padding(.leading, 30)

            // v2.1 Unified Single Footer Row: Labeled Metadata Pills on Left + [Focus, ⋯ Menu, ✓ Done] on Right
            HStack(alignment: .center, spacing: 10) {
                ThingsFlowLayout(horizontalSpacing: 7, verticalSpacing: 6) {
                    ThingsActionChipButton(
                        title: whenSummaryLabel ?? store.tr("action_when"),
                        systemImage: latestTask.scheduleState == .thisEvening
                            ? "moon.stars.fill"
                            : (latestTask.scheduleState == .today ? "sun.max.fill" : "calendar"),
                        isActive: whenSummaryLabel != nil || latestTask.reminderTime != nil,
                        activeTint: latestTask.scheduleState == .thisEvening ? ThingsMacPalette.eveningPurple : ThingsMacPalette.accentBlue,
                        helpText: whenButtonTooltip
                    ) {
                        commitDraftEditsIfNeeded()
                        isWhenPopoverOpen = true
                    }
                    .popover(isPresented: $isWhenPopoverOpen) {
                        WhenPopoverView(
                            store: store,
                            currentSchedule: latestTask.scheduleState,
                            currentWhenMillis: latestTask.whenDate,
                            currentReminderMillis: latestTask.reminderTime
                        ) { resolved in
                            var copy = latestTask
                            copy.title = draftTitle
                            copy.notes = draftNotes
                            copy.scheduleState = resolved.scheduleState
                            copy.whenDate = resolved.whenDateEpochMillis
                            copy.reminderTime = resolved.reminderEpochMillis
                            store.updateTask(copy)
                        }
                    }

                    if showQuickThisEveningPill {
                        ThingsActionChipButton(
                            title: store.tr("view_this_evening"),
                            systemImage: "moon.stars.fill",
                            isActive: true,
                            activeTint: ThingsMacPalette.eveningPurple,
                            helpText: store.tr("view_this_evening")
                        ) {
                            quickMoveToThisEveningAndCollapse()
                        }
                    }

                    if let rem = latestTask.reminderTime {
                        ThingsActionChipButton(
                            title: shortTimeString(rem),
                            systemImage: "bell",
                            isActive: true,
                            helpText: store.trf("when_task_reminder_set", shortTimeString(rem))
                        ) {
                            commitDraftEditsIfNeeded()
                            isWhenPopoverOpen = true
                        }
                    }

                    ThingsActionChipButton(
                        title: latestTask.formattedTimeblockPill ?? store.tr("v2_timeblock_title"),
                        systemImage: "clock.badge.checkmark",
                        isActive: latestTask.formattedTimeblockPill != nil,
                        activeTint: ThingsMacPalette.anytimeTeal,
                        helpText: timeblockButtonTooltip
                    ) {
                        commitDraftEditsIfNeeded()
                        isTimeblockPopoverOpen = true
                    }
                    .popover(isPresented: $isTimeblockPopoverOpen) {
                        TimeblockPopoverView(
                            store: store,
                            currentStartMinutes: latestTask.scheduledStartMinutes,
                            currentDurationMinutes: latestTask.durationMinutes
                        ) { startMins, durMins in
                            var copy = latestTask
                            copy.title = draftTitle
                            copy.notes = draftNotes
                            copy.scheduledStartMinutes = startMins
                            copy.durationMinutes = durMins
                            store.updateTask(copy)
                        }
                    }

                    ThingsActionChipButton(
                        title: latestTask.recurrenceRule?.formatShortLabel(locale: store.preferences.language.locale) ?? store.tr("action_repeat"),
                        systemImage: "repeat",
                        isActive: latestTask.recurrenceRule != nil,
                        helpText: repeatButtonTooltip
                    ) {
                        commitDraftEditsIfNeeded()
                        isRepeatPopoverOpen = true
                    }
                    .popover(isPresented: $isRepeatPopoverOpen) {
                        RepeatEditorPopoverView(store: store, currentRule: latestTask.recurrenceRule) { newRule in
                            var copy = latestTask
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

                    let dlInfo = latestTask.deadline.map { deadlineSummaryInfo(for: $0) }
                    ThingsActionChipButton(
                        title: dlInfo?.label ?? store.tr("action_deadline"),
                        systemImage: "flag",
                        isActive: dlInfo != nil,
                        activeTint: (dlInfo?.isUrgent == true) ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue,
                        helpText: store.tr("context_adjust_deadline")
                    ) {
                        commitDraftEditsIfNeeded()
                        isDeadlinePopoverOpen = true
                    }
                    .popover(isPresented: $isDeadlinePopoverOpen) {
                        DeadlinePopoverView(store: store, currentDeadlineMillis: latestTask.deadline) { dl in
                            var copy = latestTask
                            copy.title = draftTitle
                            copy.notes = draftNotes
                            copy.deadline = dl
                            store.updateTask(copy)
                        }
                    }

                    ThingsActionChipButton(
                        title: contextBreadcrumb ?? "\(store.tr("entity_project")) / \(store.tr("entity_area"))",
                        systemImage: "folder",
                        isActive: latestTask.projectId != nil || latestTask.areaId != nil,
                        helpText: moveButtonTooltip
                    ) {
                        commitDraftEditsIfNeeded()
                        isMovePopoverOpen = true
                    }
                    .popover(isPresented: $isMovePopoverOpen) {
                        MoveDestinationPopoverView(store: store, task: latestTask) { projId, headId, areaId, schedOverride in
                            var copy = latestTask
                            copy.title = draftTitle
                            copy.notes = draftNotes
                            copy.projectId = projId
                            copy.headingId = headId
                            copy.areaId = areaId
                            if let schedOverride { copy.scheduleState = schedOverride }
                            store.updateTask(copy)
                        }
                    }

                    ForEach(latestTask.tags) { tag in
                        ThingsActionChipButton(
                            title: tag.title,
                            systemImage: "tag",
                            isActive: true,
                            helpText: "#\(tag.title)"
                        ) {
                            commitDraftEditsIfNeeded()
                            isTagPopoverOpen = true
                        }
                    }

                    ThingsActionChipButton(
                        title: "+ \(store.tr("action_tags"))",
                        systemImage: "tag",
                        isActive: false,
                        helpText: tagsButtonTooltip
                    ) {
                        commitDraftEditsIfNeeded()
                        isTagPopoverOpen = true
                    }
                    .popover(isPresented: $isTagPopoverOpen) {
                        TagPickerPopoverView(
                            store: store,
                            selectedTagIds: Set(latestTask.tagIds),
                            onToggleTag: { tagId in
                                let base = latestTask
                                var tids = Set(base.tagIds)
                                if tids.contains(tagId) { tids.remove(tagId) } else { tids.insert(tagId) }
                                var copy = base
                                copy.title = draftTitle
                                copy.notes = draftNotes
                                store.updateTask(copy, tagIds: Array(tids))
                            }
                        )
                    }
                }

                HStack(spacing: 6) {
                    ThingsUtilityIconButton(
                        systemImage: "timer",
                        tint: Color.secondary,
                        helpText: store.tr("action_focus_timer")
                    ) {
                        commitDraftEditsIfNeeded()
                        store.startFocusTimer(for: latestTask)
                    }

                    Menu {
                        Button {
                            commitDraftEditsIfNeeded()
                            store.pickAndAttachFiles(taskId: task.id)
                        } label: {
                            Label(store.tr("action_attach_file"), systemImage: "paperclip")
                        }

                        Button {
                            rowVoiceController.toggleRecording(
                                locale: store.preferences.language.locale,
                                onTranscript: { spoken in
                                    if draftTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                        draftTitle = spoken
                                    } else if draftNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                        draftNotes = spoken
                                    } else {
                                        draftNotes = "\(draftNotes)\n\(spoken)"
                                    }
                                },
                                onUnavailable: {
                                    store.showToast(store.tr("voice_not_available"))
                                }
                            )
                        } label: {
                            Label(
                                rowVoiceController.isRecording ? store.tr("macos_voice_stop") : store.tr("voice_capture_btn"),
                                systemImage: rowVoiceController.isRecording ? "waveform.circle.fill" : "mic"
                            )
                        }

                        Button {
                            commitDraftEditsIfNeeded(commitPendingChecklist: true)
                            _ = try? store.database.duplicateTask(task.id)
                        } label: {
                            Label(store.tr("action_duplicate_todo"), systemImage: "plus.square.on.square")
                        }

                        Divider()

                        Button(role: .destructive) {
                            draftSaveTask?.cancel()
                            store.deleteTask(latestTask)
                        } label: {
                            Label(store.tr("action_delete"), systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color.primary.opacity(0.86))
                            .frame(width: 28, height: 28)
                            .background(
                                RoundedRectangle(cornerRadius: 7, style: .continuous)
                                    .fill(ThingsMacPalette.subtleContainer)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 7, style: .continuous)
                                    .strokeBorder(Color.primary.opacity(0.13), lineWidth: 0.8)
                            )
                    }
                    .menuStyle(.borderlessButton)
                    .menuIndicator(.hidden)
                    .fixedSize()
                    .thingsTooltip(store.tr("action_more_options"))

                    Button {
                        commitDraftEditsIfNeeded(commitPendingChecklist: true)
                        withAnimation(.spring(response: 0.25, dampingFraction: 0.86)) {
                            store.expandedTaskId = nil
                        }
                    } label: {
                        Image(systemName: "checkmark")
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 30, height: 28)
                            .background(
                                RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                    .fill(ThingsMacPalette.accentBlue)
                                    .brightness(isHoveringDone ? 0.06 : 0.0)
                            )
                    }
                    .buttonStyle(.plain)
                    .fixedSize()
                    .onHover { hovering in
                        withAnimation(.easeOut(duration: 0.12)) {
                            isHoveringDone = hovering
                        }
                    }
                    .thingsTooltip("\(store.tr("action_done")) (Esc)")
                    .keyboardShortcut(.escape, modifiers: [])
                }
                .fixedSize(horizontal: true, vertical: false)
                .layoutPriority(1)
            }
            .padding(.leading, 30)
        }
        .padding(18)
        .thingsCardSurface(cornerRadius: 14, isSelected: true)
        .onDrop(of: [UTType.fileURL], isTargeted: $isFileDropTargeted) { providers in
            commitDraftEditsIfNeeded()
            return store.handleFileDropProviders(providers, taskId: task.id)
        }
        .padding(.vertical, 5)
    }

    // MARK: - Context Menu & Helpers

    @ViewBuilder
    private var taskContextMenu: some View {
        Button(task.status == .completed ? store.tr("action_reopen_todo") : store.tr("action_complete")) {
            store.toggleTaskCompletion(latestTask)
        }
        Button(task.status == .canceled ? store.tr("action_reopen_todo") : store.tr("action_cancel_todo")) {
            store.cancelTask(latestTask)
        }
        Divider()
        Button(store.tr("view_today")) {
            var copy = latestTask
            copy.scheduleState = .today
            copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
            store.updateTask(copy)
        }
        Button(store.tr("view_this_evening")) {
            var copy = latestTask
            copy.scheduleState = .thisEvening
            copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
            store.updateTask(copy)
        }
        Button(store.tr("view_someday")) {
            var copy = latestTask
            copy.scheduleState = .someday
            copy.whenDate = nil
            store.updateTask(copy)
        }
        Menu(store.tr("v2_timeblock_title")) {
            Button("09:00 · 30m") { store.setTaskTimeblock(latestTask.id, startMinutes: 9 * 60, durationMinutes: 30) }
            Button("11:00 · 45m") { store.setTaskTimeblock(latestTask.id, startMinutes: 11 * 60, durationMinutes: 45) }
            Button("14:00 · 60m") { store.setTaskTimeblock(latestTask.id, startMinutes: 14 * 60, durationMinutes: 60) }
            Button("16:00 · 45m") { store.setTaskTimeblock(latestTask.id, startMinutes: 16 * 60, durationMinutes: 45) }
            if latestTask.scheduledStartMinutes != nil || latestTask.durationMinutes != nil {
                Divider()
                Button(store.tr("v2_timeblock_clear"), role: .destructive) {
                    store.setTaskTimeblock(latestTask.id, startMinutes: nil, durationMinutes: nil)
                }
            }
        }
        Divider()
        Button(store.tr("action_attach_file")) {
            store.pickAndAttachFiles(taskId: task.id)
        }
        Button(store.tr("action_focus_timer")) {
            store.startFocusTimer(for: latestTask)
        }
        Button(store.tr("action_duplicate_todo")) {
            _ = try? store.database.duplicateTask(task.id)
        }
        Button(store.tr("context_select_task")) {
            store.selectedTaskIds.insert(task.id)
        }
        Divider()
        Button(role: .destructive) {
            store.deleteTask(latestTask)
        } label: {
            Text(store.tr("action_delete"))
        }
    }

    private var showQuickThisEveningPill: Bool {
        TaskCardInteractionRules.shouldShowQuickThisEveningPill(
            isTodayView: store.selectedDestination == .smartView(.today),
            status: latestTask.status,
            scheduleState: latestTask.scheduleState
        )
    }

    private func applyInitialExpandFocusState() {
        isEditingNotes = TaskCardInteractionRules.shouldAutoEditNotesOnExpand(notes: latestTask.notes)
        if TaskCardInteractionRules.shouldAutoFocusTitleOnExpand(title: latestTask.title) {
            DispatchQueue.main.async {
                isTitleFocused = true
            }
        } else {
            isTitleFocused = false
            DispatchQueue.main.async {
                isTitleFocused = false
                if let window = NSApp.keyWindow, window.firstResponder is NSTextView {
                    window.makeFirstResponder(nil)
                }
            }
        }
    }

    private func quickMoveToThisEveningAndCollapse() {
        draftSaveTask?.cancel()
        rowVoiceController.stopRecording()
        let pendingChecklist = newChecklistTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        if !pendingChecklist.isEmpty {
            _ = try? store.database.addChecklistItem(taskId: task.id, title: pendingChecklist)
            newChecklistTitle = ""
        }
        var copy = latestTask
        copy.title = draftTitle
        copy.notes = draftNotes
        copy.scheduleState = .thisEvening
        copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
        store.updateTask(copy)
        withAnimation(.spring(response: 0.25, dampingFraction: 0.86)) {
            store.expandedTaskId = nil
        }
    }

    private func syncDrafts() {
        let base = latestTask
        draftTitle = base.title
        draftNotes = base.notes
    }

    private func scheduleDraftAutoSave() {
        draftSaveTask?.cancel()
        draftSaveTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 450_000_000)
            guard !Task.isCancelled else { return }
            commitDraftEditsIfNeeded()
        }
    }

    private func commitDraftEditsIfNeeded(commitPendingChecklist: Bool = false) {
        let base = latestTask
        if draftTitle != base.title || draftNotes != base.notes {
            var copy = base
            copy.title = draftTitle
            copy.notes = draftNotes
            store.updateTask(copy)
        }
        if commitPendingChecklist {
            let pending = newChecklistTitle.trimmingCharacters(in: .whitespacesAndNewlines)
            if !pending.isEmpty {
                _ = try? store.database.addChecklistItem(taskId: task.id, title: pending)
                newChecklistTitle = ""
            }
        }
    }

    private func runMagicBreakdown() {
        commitDraftEditsIfNeeded()
        isGeneratingChecklist = true
        let base = latestTask
        let titleToBreak = draftTitle.isEmpty ? base.title : draftTitle
        let notesToBreak = draftNotes
        Task {
            do {
                let steps = try await ThingsbookGeminiService.generateMagicChecklist(
                    taskTitle: titleToBreak,
                    taskNotes: notesToBreak,
                    lang: store.preferences.language
                )
                await MainActor.run {
                    for step in steps {
                        _ = try? store.database.addChecklistItem(taskId: task.id, title: step)
                    }
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

    private var contextBreadcrumb: String? {
        if let pid = task.projectId, let proj = store.project(byId: pid) {
            if let aid = proj.areaId, let area = store.area(byId: aid) {
                return "\(area.title) › \(proj.title)"
            }
            return proj.title
        }
        if let aid = task.areaId, let area = store.area(byId: aid) {
            return area.title
        }
        return nil
    }

    private var whenSummaryLabel: String? {
        switch task.scheduleState {
        case .today:
            return store.tr("view_today")
        case .thisEvening:
            return store.tr("view_this_evening")
        case .someday:
            return store.tr("view_someday")
        case .upcoming:
            if let w = task.whenDate {
                let df = DateFormatter()
                df.locale = store.preferences.language.locale
                df.dateStyle = .medium
                return df.string(from: DateEpochConverter.date(fromMillis: w))
            }
            return store.tr("view_upcoming")
        default:
            return nil
        }
    }

    private var whenButtonTooltip: String {
        let base = store.tr("context_when_date_reminder")
        if let whenSummaryLabel {
            return "\(base) — \(whenSummaryLabel)"
        }
        return base
    }

    private var repeatButtonTooltip: String {
        let base = store.tr("context_configure_repeat")
        if let rule = latestTask.recurrenceRule {
            return "\(base) — \(rule.formatShortLabel(locale: store.preferences.language.locale))"
        }
        return base
    }

    private var moveButtonTooltip: String {
        let base = store.tr("move_dialog_title")
        if let contextBreadcrumb {
            return "\(base) — \(contextBreadcrumb)"
        }
        return base
    }

    private var tagsButtonTooltip: String {
        let base = store.tr("context_assign_tags")
        if !latestTask.tags.isEmpty {
            let names = latestTask.tags.map { "#\($0.title)" }.joined(separator: ", ")
            return "\(base) — \(names)"
        }
        return base
    }

    private var timeblockButtonTooltip: String {
        let base = store.tr("macos_tooltip_timeblock")
        if let pill = latestTask.formattedTimeblockPill {
            return "\(base) — \(pill)"
        }
        return base
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

    private func deadlineChip(_ millis: Int64) -> some View {
        let info = deadlineSummaryInfo(for: millis)
        return HStack(spacing: 3.5) {
            Image(systemName: "flag")
                .font(.system(size: 10, weight: .semibold))
            Text(info.label)
                .font(.system(size: 11, weight: .semibold))
                .lineLimit(1)
        }
        .fixedSize(horizontal: true, vertical: false)
        .foregroundStyle(info.isUrgent ? ThingsMacPalette.deadlineCrimson : Color.secondary)
        .padding(.horizontal, 8)
        .padding(.vertical, 2.5)
        .background(
            Capsule().fill(info.isUrgent ? ThingsMacPalette.deadlineCrimson.opacity(0.12) : Color.primary.opacity(0.055))
        )
        .thingsTooltip("\(store.tr("action_deadline")): \(info.label)")
    }

    private func shortTimeString(_ millis: Int64) -> String {
        let df = DateFormatter()
        df.timeStyle = .short
        return df.string(from: DateEpochConverter.date(fromMillis: millis))
    }
}

// MARK: - Checklist Row with Up / Down / Delete Controls

private struct ChecklistItemRowView: View {
    @ObservedObject var store: ThingsbookStore
    let item: ChecklistItem
    let allItems: [ChecklistItem]
    @State private var text: String = ""
    @State private var saveTask: Task<Void, Never>?

    private var latestParentChecklistItems: [ChecklistItem] {
        (store.allOpenTasks + store.logbookTasks + store.trashedTasks)
            .first(where: { $0.id == item.taskId })?
            .checklistItems ?? allItems
    }

    private var latestItem: ChecklistItem {
        latestParentChecklistItems.first(where: { $0.id == item.id }) ?? item
    }

    var body: some View {
        HStack(spacing: 8) {
            Button {
                saveTask?.cancel()
                var copy = latestItem
                let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty {
                    copy.title = clean
                }
                copy.isCompleted.toggle()
                try? store.database.updateChecklistItem(copy)
            } label: {
                Image(systemName: item.isCompleted ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 14))
                    .foregroundStyle(item.isCompleted ? ThingsMacPalette.accentBlue : .secondary)
            }
            .buttonStyle(.plain)
            .thingsTooltip(item.isCompleted ? store.tr("action_reopen_todo") : store.tr("action_complete"))

            TextField("", text: $text)
                .textFieldStyle(.plain)
                .font(.system(size: 13))
                .foregroundStyle(item.isCompleted ? .secondary : .primary)
                .strikethrough(item.isCompleted, color: .secondary)
                .onSubmit {
                    saveTask?.cancel()
                    commitTitle()
                }

            Spacer()

            HStack(spacing: 5) {
                Button {
                    saveTask?.cancel()
                    commitTitle()
                    store.moveChecklistItem(latestItem, in: latestParentChecklistItems, direction: -1)
                } label: {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 9.5, weight: .semibold))
                        .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("subtask_move_up"))

                Button {
                    saveTask?.cancel()
                    commitTitle()
                    store.moveChecklistItem(latestItem, in: latestParentChecklistItems, direction: 1)
                } label: {
                    Image(systemName: "arrow.down")
                        .font(.system(size: 9.5, weight: .semibold))
                        .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("subtask_move_down"))

                Button {
                    saveTask?.cancel()
                    try? store.database.deleteChecklistItem(id: item.id, taskId: item.taskId)
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("subtask_delete"))
            }
        }
        .padding(.vertical, 2)
        .onAppear { text = latestItem.title }
        .onDisappear {
            saveTask?.cancel()
            commitTitle()
        }
        .onChange(of: item.title) { _, newVal in
            if text != newVal { text = newVal }
        }
        .onChange(of: text) { _, _ in
            saveTask?.cancel()
            saveTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 450_000_000)
                guard !Task.isCancelled else { return }
                commitTitle()
            }
        }
    }

    private func commitTitle() {
        let current = latestItem
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean != current.title else { return }
        var copy = current
        copy.title = clean
        try? store.database.updateChecklistItem(copy)
    }
}
