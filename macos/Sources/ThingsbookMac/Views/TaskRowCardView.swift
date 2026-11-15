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
                isEditingNotes = latestTask.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                if latestTask.title.isEmpty {
                    isTitleFocused = true
                }
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

                Button {
                    rowVoiceController.toggleRecording(
                        locale: store.preferences.language.locale,
                        onTranscript: { spoken in
                            draftTitle = spoken
                        },
                        onUnavailable: {
                            store.showToast(store.tr("voice_not_available"))
                        }
                    )
                } label: {
                    Image(systemName: rowVoiceController.isRecording ? "waveform.circle.fill" : "mic")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(rowVoiceController.isRecording ? ThingsMacPalette.deadlineCrimson : .secondary)
                }
                .buttonStyle(.plain)
                .help(rowVoiceController.isRecording ? store.tr("macos_voice_stop") : store.tr("voice_capture_btn"))
            }

            // Markdown Notes Box + Detected Smart Link Chips
            VStack(alignment: .leading, spacing: 8) {
                if !isEditingNotes && !draftNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(MarkdownNotesParser.renderAttributed(draftNotes))
                        .font(.system(size: 13.5))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(11)
                        .background(
                            RoundedRectangle(cornerRadius: 9, style: .continuous)
                                .fill(ThingsMacPalette.subtleContainer)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                                        .strokeBorder(Color.primary.opacity(0.06), lineWidth: 0.8)
                                )
                        )
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
                                }
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
                        }

                        Spacer()

                        if !draftNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            Button(isEditingNotes ? store.tr("macos_preview_markdown") : store.tr("macos_edit_markdown")) {
                                isEditingNotes.toggle()
                            }
                            .font(.system(size: 11.5, weight: .medium))
                            .buttonStyle(.plain)
                            .foregroundStyle(.secondary)
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
                    Image(systemName: "plus")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.tertiary)
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
                        }
                        .foregroundStyle(ThingsMacPalette.geminiPurple)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4.5)
                        .background(Capsule().fill(ThingsMacPalette.geminiPurple.opacity(0.12)))
                    }
                    .buttonStyle(.plain)
                    .disabled(isGeneratingChecklist)
                    .help(store.tr("gemini_magic_breakdown_desc"))
                }
            }
            .padding(.leading, 30)

            // Active Badges Summary Row
            let hasContext = contextBreadcrumb != nil
            if task.whenDate != nil || task.formattedTimeblockPill != nil || task.reminderTime != nil || task.deadline != nil || task.recurrenceRule != nil || hasContext || !task.tags.isEmpty {
                HStack(spacing: 6) {
                    if let whenStr = whenSummaryLabel {
                        HStack(spacing: 4) {
                            Image(systemName: task.scheduleState == .thisEvening ? "moon.stars.fill" : "star.fill")
                                .font(.system(size: 10.5))
                                .foregroundStyle(task.scheduleState == .thisEvening ? ThingsMacPalette.eveningPurple : ThingsMacPalette.todayYellow)
                            Text(whenStr)
                                .font(.system(size: 11.5, weight: .semibold))
                                .foregroundStyle(ThingsMacPalette.accentBlue)
                        }
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3.5)
                        .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.10)))
                    }

                    if let tbPill = task.formattedTimeblockPill {
                        HStack(spacing: 4) {
                            Image(systemName: "clock.badge.checkmark")
                                .font(.system(size: 10.5, weight: .semibold))
                            Text(tbPill)
                                .font(.system(size: 11.5, weight: .semibold, design: .monospaced))
                        }
                        .foregroundStyle(ThingsMacPalette.anytimeTeal)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3.5)
                        .background(Capsule().fill(ThingsMacPalette.anytimeTeal.opacity(0.12)))
                    }

                    if let rem = task.reminderTime {
                        HStack(spacing: 3.5) {
                            Image(systemName: "bell")
                                .font(.system(size: 10, weight: .semibold))
                            Text(shortTimeString(rem))
                                .font(.system(size: 11.5, weight: .semibold))
                        }
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3.5)
                        .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.10)))
                    }

                    if let dl = task.deadline {
                        deadlineChip(dl)
                    }

                    if let crumb = contextBreadcrumb {
                        HStack(spacing: 4) {
                            Image(systemName: "folder")
                                .font(.system(size: 10.5))
                            Text(crumb)
                                .font(.system(size: 11.5, weight: .medium))
                        }
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 3.5)
                        .background(Capsule().fill(Color.primary.opacity(0.06)))
                    }

                    if let rule = task.recurrenceRule {
                        Label(rule.formatShortLabel(locale: store.preferences.language.locale), systemImage: "repeat")
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundStyle(.secondary)
                    }

                    ForEach(task.tags) { tag in
                        TagPillBadge(title: tag.title, showIcon: true)
                    }
                }
                .padding(.leading, 30)
            }

            Divider()
                .opacity(0.6)

            // Bottom Action Bar + Done Pill Button
            HStack(spacing: 8) {
                Button {
                    commitDraftEditsIfNeeded()
                    isWhenPopoverOpen = true
                } label: {
                    Label(store.tr("action_when"), systemImage: "calendar")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
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

                Button {
                    commitDraftEditsIfNeeded()
                    isRepeatPopoverOpen = true
                } label: {
                    Label(store.tr("action_repeat"), systemImage: "repeat")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
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

                Button {
                    commitDraftEditsIfNeeded()
                    isMovePopoverOpen = true
                } label: {
                    Label(store.tr("action_move"), systemImage: "folder")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
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

                Button {
                    commitDraftEditsIfNeeded()
                    isTagPopoverOpen = true
                } label: {
                    Label(store.tr("action_tags"), systemImage: "tag")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
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

                Button {
                    commitDraftEditsIfNeeded()
                    isDeadlinePopoverOpen = true
                } label: {
                    Label(store.tr("action_deadline"), systemImage: "flag")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .popover(isPresented: $isDeadlinePopoverOpen) {
                    DeadlinePopoverView(store: store, currentDeadlineMillis: latestTask.deadline) { dl in
                        var copy = latestTask
                        copy.title = draftTitle
                        copy.notes = draftNotes
                        copy.deadline = dl
                        store.updateTask(copy)
                    }
                }

                Button {
                    commitDraftEditsIfNeeded()
                    isTimeblockPopoverOpen = true
                } label: {
                    Label(store.tr("v2_timeblock_title"), systemImage: "clock.badge.checkmark")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
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

                Button {
                    commitDraftEditsIfNeeded()
                    store.pickAndAttachFiles(taskId: task.id)
                } label: {
                    Image(systemName: "paperclip")
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .help(store.tr("action_attach_file"))
                }
                .buttonStyle(.borderless)

                Button {
                    commitDraftEditsIfNeeded()
                    store.startFocusTimer(for: latestTask)
                } label: {
                    Image(systemName: "timer")
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .help(store.tr("action_focus_timer"))
                }
                .buttonStyle(.borderless)

                Button {
                    commitDraftEditsIfNeeded(commitPendingChecklist: true)
                    _ = try? store.database.duplicateTask(task.id)
                } label: {
                    Image(systemName: "plus.square.on.square")
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .help(store.tr("action_duplicate_todo"))
                }
                .buttonStyle(.borderless)

                Button(role: .destructive) {
                    draftSaveTask?.cancel()
                    store.deleteTask(latestTask)
                } label: {
                    Image(systemName: "trash")
                        .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                        .help(store.tr("action_delete"))
                }
                .buttonStyle(.borderless)

                Spacer()

                Button {
                    commitDraftEditsIfNeeded(commitPendingChecklist: true)
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.86)) {
                        store.expandedTaskId = nil
                    }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .bold))
                        Text(store.tr("action_done"))
                            .font(.system(size: 12.5, weight: .bold))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(Capsule().fill(ThingsMacPalette.accentBlue))
                }
                .buttonStyle(.plain)
                .keyboardShortcut(.escape, modifiers: [])
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

    private func deadlineChip(_ millis: Int64) -> some View {
        let todayDay = DateEpochConverter.epochDay(for: Date())
        let dlDay = DateEpochConverter.epochDay(forMillis: millis)
        let diff = dlDay - todayDay
        let isUrgent = diff <= 1
        let label: String = {
            if diff == 0 { return store.tr("deadline_today") }
            if diff < 0 { return store.trf("deadline_days_overdue", abs(diff)) }
            return store.trf("deadline_days_left", diff)
        }()

        return HStack(spacing: 3.5) {
            Image(systemName: "flag")
                .font(.system(size: 10, weight: .semibold))
            Text(label)
                .font(.system(size: 11, weight: .semibold))
        }
        .foregroundStyle(isUrgent ? ThingsMacPalette.deadlineCrimson : Color.secondary)
        .padding(.horizontal, 8)
        .padding(.vertical, 2.5)
        .background(
            Capsule().fill(isUrgent ? ThingsMacPalette.deadlineCrimson.opacity(0.12) : Color.primary.opacity(0.055))
        )
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
                .help(store.tr("subtask_move_up"))

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
                .help(store.tr("subtask_move_down"))

                Button {
                    saveTask?.cancel()
                    try? store.database.deleteChecklistItem(id: item.id, taskId: item.taskId)
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
                .help(store.tr("subtask_delete"))
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
