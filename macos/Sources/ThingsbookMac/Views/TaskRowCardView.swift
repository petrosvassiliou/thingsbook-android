import SwiftUI
import AppKit
import ThingsbookCore

public struct TaskRowCardView: View {
    @ObservedObject var store: ThingsbookStore
    public let task: TaskItem
    public var showContextSubtitle: Bool = true

    @State private var draftTitle: String = ""
    @State private var draftNotes: String = ""
    @State private var newChecklistTitle: String = ""
    @State private var isWhenPopoverOpen = false
    @State private var isDeadlinePopoverOpen = false
    @State private var isRepeatPopoverOpen = false
    @State private var isMovePopoverOpen = false
    @State private var isTagPopoverOpen = false
    @State private var isGeneratingChecklist = false
    @State private var showMarkdownPreview = false
    @FocusState private var isTitleFocused: Bool

    private var isExpanded: Bool {
        store.expandedTaskId == task.id
    }

    private var isSelected: Bool {
        store.selectedTaskIds.contains(task.id)
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
        .onChange(of: task.title) { _, newValue in
            if draftTitle != newValue { draftTitle = newValue }
        }
        .onChange(of: task.notes) { _, newValue in
            if draftNotes != newValue { draftNotes = newValue }
        }
        .onChange(of: isExpanded) { _, expanded in
            if expanded {
                syncDrafts()
                if task.title.isEmpty {
                    isTitleFocused = true
                }
            } else {
                commitDraftEditsIfNeeded()
            }
        }
    }

    // MARK: - Collapsed Things-for-Mac Row

    private var collapsedRow: some View {
        HStack(alignment: .center, spacing: 10) {
            ThingsCheckboxButton(
                status: task.status,
                isSomeday: task.scheduleState == .someday,
                isRecurring: task.recurrenceRule != nil,
                onToggleComplete: { store.toggleTaskCompletion(task) },
                onCancelTask: { store.cancelTask(task) }
            )

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if task.scheduleState == .thisEvening && store.selectedDestination != .smartView(.today) {
                        Image(systemName: "moon.fill")
                            .font(.system(size: 10.5))
                            .foregroundStyle(ThingsMacPalette.eveningPurple)
                    }

                    Text(task.title.isEmpty ? store.tr("todo_untitled") : task.title)
                        .font(.system(size: 13.5, weight: .regular))
                        .foregroundStyle(
                            task.status == .open
                                ? (task.title.isEmpty ? Color.secondary : Color.primary)
                                : Color.secondary
                        )
                        .strikethrough(task.status == .canceled, color: .secondary)
                        .lineLimit(1)

                    if !task.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Image(systemName: "doc.text")
                            .font(.system(size: 10))
                            .foregroundStyle(.tertiary)
                    }
                }

                if showContextSubtitle, let subtitle = contextBreadcrumb {
                    Text(subtitle)
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 8)

            // Trailing metadata badges
            HStack(spacing: 6) {
                if !task.checklistItems.isEmpty {
                    let done = task.checklistItems.filter(\.isCompleted).count
                    HStack(spacing: 3) {
                        Image(systemName: "checklist")
                            .font(.system(size: 10))
                        Text("\(done)/\(task.checklistItems.count)")
                            .font(.system(size: 11, weight: .medium, design: .rounded))
                    }
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Capsule().fill(Color.primary.opacity(0.05)))
                }

                ForEach(task.tags.prefix(3)) { tag in
                    TagPillBadge(title: tag.title, isSelected: store.selectedFilterTagId == tag.id) {
                        store.selectedFilterTagId = (store.selectedFilterTagId == tag.id) ? nil : tag.id
                    }
                }

                if let rule = task.recurrenceRule {
                    HStack(spacing: 3) {
                        Image(systemName: "repeat")
                            .font(.system(size: 10))
                        Text(rule.formatShortLabel(locale: store.preferences.language.locale))
                            .font(.system(size: 10.5, weight: .medium))
                    }
                    .foregroundStyle(.secondary)
                }

                if let rem = task.reminderTime {
                    HStack(spacing: 2) {
                        Image(systemName: "bell.fill")
                            .font(.system(size: 9.5))
                        Text(shortTimeString(rem))
                            .font(.system(size: 10.5))
                    }
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                }

                if let dl = task.deadline {
                    deadlineChip(dl)
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6.5)
        .background(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(isSelected ? ThingsMacPalette.accentBlue.opacity(0.14) : Color.clear)
        )
        .contentShape(Rectangle())
        .onTapGesture(count: 2) {
            withAnimation(.spring(response: 0.28, dampingFraction: 0.85)) {
                store.selectedTaskIds = [task.id]
                store.expandedTaskId = task.id
            }
        }
        .simultaneousGesture(
            TapGesture(count: 1).onEnded {
                if NSEvent.modifierFlags.contains(.command) {
                    if store.selectedTaskIds.contains(task.id) {
                        store.selectedTaskIds.remove(task.id)
                    } else {
                        store.selectedTaskIds.insert(task.id)
                    }
                } else {
                    store.selectedTaskIds = [task.id]
                }
            }
        )
    }

    // MARK: - Inline Spring-Expanded Things Card

    private var expandedCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Top Title Row
            HStack(alignment: .center, spacing: 10) {
                ThingsCheckboxButton(
                    status: task.status,
                    isSomeday: task.scheduleState == .someday,
                    isRecurring: task.recurrenceRule != nil,
                    onToggleComplete: {
                        commitDraftEditsIfNeeded()
                        store.toggleTaskCompletion(task)
                    },
                    onCancelTask: {
                        commitDraftEditsIfNeeded()
                        store.cancelTask(task)
                    }
                )

                TextField(store.tr("todo_new_placeholder"), text: $draftTitle)
                    .textFieldStyle(.plain)
                    .font(.system(size: 14.5, weight: .medium))
                    .focused($isTitleFocused)
                    .onSubmit {
                        commitDraftEditsIfNeeded()
                        withAnimation(.spring(response: 0.25, dampingFraction: 0.86)) {
                            store.expandedTaskId = nil
                        }
                    }

                Spacer()

                Button {
                    commitDraftEditsIfNeeded()
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.86)) {
                        store.expandedTaskId = nil
                    }
                } label: {
                    Image(systemName: "chevron.up.circle.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
                .keyboardShortcut(.escape, modifiers: [])
            }

            // Markdown Notes Editor / Preview + Detected Link Chips
            VStack(alignment: .leading, spacing: 6) {
                if showMarkdownPreview && !draftNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(MarkdownNotesParser.renderAttributed(draftNotes))
                        .font(.system(size: 13))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(6)
                        .background(RoundedRectangle(cornerRadius: 6).fill(Color.primary.opacity(0.03)))
                        .onTapGesture { showMarkdownPreview = false }
                } else {
                    ZStack(alignment: .topLeading) {
                        if draftNotes.isEmpty {
                            Text(store.tr("todo_notes_placeholder"))
                                .font(.system(size: 13))
                                .foregroundStyle(.tertiary)
                                .padding(.top, 4)
                                .padding(.leading, 4)
                                .allowsHitTesting(false)
                        }
                        TextEditor(text: $draftNotes)
                            .font(.system(size: 13))
                            .scrollContentBackground(.hidden)
                            .frame(minHeight: 54, maxHeight: 160)
                    }
                }

                let links = MarkdownNotesParser.extractDetectedLinks(from: draftNotes)
                if !links.isEmpty || !draftNotes.isEmpty {
                    HStack(spacing: 6) {
                        ForEach(links) { link in
                            Button {
                                if let url = URL(string: link.targetUri) {
                                    NSWorkspace.shared.open(url)
                                }
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: link.type.sfSymbol)
                                        .font(.system(size: 10))
                                    Text(link.displayLabel)
                                        .font(.system(size: 11, weight: .medium))
                                }
                                .foregroundStyle(ThingsMacPalette.accentBlue)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.11)))
                            }
                            .buttonStyle(.plain)
                        }

                        Spacer()

                        if !draftNotes.isEmpty {
                            Button(showMarkdownPreview ? "Edit Markdown" : "Preview Markdown") {
                                showMarkdownPreview.toggle()
                            }
                            .font(.system(size: 11))
                            .buttonStyle(.plain)
                            .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .padding(.leading, 27)

            // Checklist Section + Gemini Magic Breakdown
            VStack(alignment: .leading, spacing: 5) {
                ForEach(task.checklistItems) { item in
                    ChecklistItemRowView(store: store, item: item)
                }

                HStack(spacing: 8) {
                    Image(systemName: "plus")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(.tertiary)
                        .frame(width: 15)

                    TextField(store.tr("checklist_add_item"), text: $newChecklistTitle)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12.5))
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
                        HStack(spacing: 4) {
                            if isGeneratingChecklist {
                                ProgressView().controlSize(.mini)
                            } else {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 11))
                            }
                            Text(store.tr("gemini_magic_breakdown_btn"))
                                .font(.system(size: 11, weight: .medium))
                        }
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.10)))
                    }
                    .buttonStyle(.plain)
                    .disabled(isGeneratingChecklist)
                }
            }
            .padding(.leading, 27)

            // Active Badges Summary Row
            if task.whenDate != nil || task.deadline != nil || task.recurrenceRule != nil || !task.tags.isEmpty {
                HStack(spacing: 6) {
                    if let whenStr = whenSummaryLabel {
                        Label(whenStr, systemImage: task.scheduleState == .thisEvening ? "moon.stars.fill" : "calendar")
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundStyle(task.scheduleState == .thisEvening ? ThingsMacPalette.eveningPurple : ThingsMacPalette.todayYellow)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2.5)
                            .background(Capsule().fill(Color.primary.opacity(0.05)))
                    }
                    if let dl = task.deadline {
                        deadlineChip(dl)
                    }
                    if let rule = task.recurrenceRule {
                        Label(rule.formatShortLabel(locale: store.preferences.language.locale), systemImage: "repeat")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(.secondary)
                    }
                    ForEach(task.tags) { tag in
                        TagPillBadge(title: tag.title)
                    }
                }
                .padding(.leading, 27)
            }

            Divider()

            // Bottom Action Bar (Things for Mac style)
            HStack(spacing: 8) {
                Button {
                    commitDraftEditsIfNeeded()
                    isWhenPopoverOpen = true
                } label: {
                    Label(store.tr("when_title"), systemImage: "calendar")
                        .font(.system(size: 11.5, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .popover(isPresented: $isWhenPopoverOpen) {
                    WhenPopoverView(
                        store: store,
                        currentSchedule: task.scheduleState,
                        currentWhenMillis: task.whenDate,
                        currentReminderMillis: task.reminderTime
                    ) { resolved in
                        var copy = task
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
                    isTagPopoverOpen = true
                } label: {
                    Label(store.tr("tag_dialog_title"), systemImage: "tag")
                        .font(.system(size: 11.5, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .popover(isPresented: $isTagPopoverOpen) {
                    TagPickerPopoverView(
                        store: store,
                        selectedTagIds: Set(task.tagIds),
                        onToggleTag: { tagId in
                            var tids = Set(task.tagIds)
                            if tids.contains(tagId) { tids.remove(tagId) } else { tids.insert(tagId) }
                            var copy = task
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
                    Label(store.tr("deadline_title"), systemImage: "flag")
                        .font(.system(size: 11.5, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .popover(isPresented: $isDeadlinePopoverOpen) {
                    DeadlinePopoverView(store: store, currentDeadlineMillis: task.deadline) { dl in
                        var copy = task
                        copy.title = draftTitle
                        copy.notes = draftNotes
                        copy.deadline = dl
                        store.updateTask(copy)
                    }
                }

                Button {
                    commitDraftEditsIfNeeded()
                    isRepeatPopoverOpen = true
                } label: {
                    Label(store.tr("todo_repeat_add"), systemImage: "repeat")
                        .font(.system(size: 11.5, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .popover(isPresented: $isRepeatPopoverOpen) {
                    RepeatEditorPopoverView(store: store, currentRule: task.recurrenceRule) { newRule in
                        var copy = task
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
                    Label(store.tr("todo_move"), systemImage: "folder")
                        .font(.system(size: 11.5, weight: .medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .popover(isPresented: $isMovePopoverOpen) {
                    MoveDestinationPopoverView(store: store, task: task) { projId, headId, areaId, schedOverride in
                        var copy = task
                        copy.title = draftTitle
                        copy.notes = draftNotes
                        copy.projectId = projId
                        copy.headingId = headId
                        copy.areaId = areaId
                        if let schedOverride { copy.scheduleState = schedOverride }
                        store.updateTask(copy)
                    }
                }

                Spacer()

                Button {
                    commitDraftEditsIfNeeded()
                    store.startFocusTimer(for: task)
                } label: {
                    Image(systemName: "timer")
                        .help(store.tr("menu_start_focus"))
                }
                .buttonStyle(.borderless)

                Button {
                    commitDraftEditsIfNeeded()
                    _ = try? store.database.duplicateTask(task.id)
                } label: {
                    Image(systemName: "plus.square.on.square")
                        .help(store.tr("menu_duplicate"))
                }
                .buttonStyle(.borderless)

                Button(role: .destructive) {
                    store.deleteTask(task)
                } label: {
                    Image(systemName: "trash")
                        .foregroundStyle(.red.opacity(0.8))
                        .help(store.tr("action_delete"))
                }
                .buttonStyle(.borderless)
            }
            .padding(.leading, 27)
        }
        .padding(14)
        .thingsCardSurface(cornerRadius: 12, isSelected: true)
        .padding(.vertical, 4)
    }

    // MARK: - Context Menu & Helpers

    @ViewBuilder
    private var taskContextMenu: some View {
        Button(task.status == .completed ? store.tr("action_undo") : store.tr("action_complete")) {
            store.toggleTaskCompletion(task)
        }
        Button(task.status == .canceled ? store.tr("action_restore") : store.tr("menu_cancel_task")) {
            store.cancelTask(task)
        }
        Divider()
        Button(store.tr("when_today")) {
            var copy = task
            copy.scheduleState = .today
            copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
            store.updateTask(copy)
        }
        Button(store.tr("when_this_evening")) {
            var copy = task
            copy.scheduleState = .thisEvening
            copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
            store.updateTask(copy)
        }
        Button(store.tr("when_someday")) {
            var copy = task
            copy.scheduleState = .someday
            copy.whenDate = nil
            store.updateTask(copy)
        }
        Divider()
        Button(store.tr("menu_start_focus")) {
            store.startFocusTimer(for: task)
        }
        Button(store.tr("menu_duplicate")) {
            _ = try? store.database.duplicateTask(task.id)
        }
        Divider()
        Button(role: .destructive) {
            store.deleteTask(task)
        } label: {
            Text(store.tr("action_delete"))
        }
    }

    private func syncDrafts() {
        draftTitle = task.title
        draftNotes = task.notes
    }

    private func commitDraftEditsIfNeeded() {
        if draftTitle != task.title || draftNotes != task.notes {
            var copy = task
            copy.title = draftTitle
            copy.notes = draftNotes
            store.updateTask(copy)
        }
    }

    private func runMagicBreakdown() {
        commitDraftEditsIfNeeded()
        isGeneratingChecklist = true
        let titleToBreak = draftTitle.isEmpty ? task.title : draftTitle
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
            return store.tr("when_today")
        case .thisEvening:
            return store.tr("when_this_evening")
        case .someday:
            return store.tr("when_someday")
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
        let isUrgent = diff <= 0
        let label: String = {
            if diff == 0 { return store.tr("deadline_due_today") }
            if diff < 0 { return "\(abs(diff))d overdue" }
            return "\(diff)d left"
        }()

        return HStack(spacing: 3) {
            Image(systemName: "flag.fill")
                .font(.system(size: 9.5))
            Text(label)
                .font(.system(size: 10.5, weight: .semibold))
        }
        .foregroundStyle(isUrgent ? ThingsMacPalette.deadlineCrimson : Color.secondary)
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background(
            Capsule().fill(isUrgent ? ThingsMacPalette.deadlineCrimson.opacity(0.12) : Color.primary.opacity(0.05))
        )
    }

    private func shortTimeString(_ millis: Int64) -> String {
        let df = DateFormatter()
        df.timeStyle = .short
        return df.string(from: DateEpochConverter.date(fromMillis: millis))
    }
}

// MARK: - Checklist Row

private struct ChecklistItemRowView: View {
    @ObservedObject var store: ThingsbookStore
    let item: ChecklistItem
    @State private var text: String = ""

    var body: some View {
        HStack(spacing: 8) {
            Button {
                var copy = item
                copy.isCompleted.toggle()
                try? store.database.updateChecklistItem(copy)
            } label: {
                Image(systemName: item.isCompleted ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 13))
                    .foregroundStyle(item.isCompleted ? ThingsMacPalette.accentBlue : .secondary)
            }
            .buttonStyle(.plain)

            TextField("", text: $text)
                .textFieldStyle(.plain)
                .font(.system(size: 12.5))
                .foregroundStyle(item.isCompleted ? .secondary : .primary)
                .strikethrough(item.isCompleted, color: .secondary)
                .onSubmit {
                    commitTitle()
                }

            Spacer()

            Button {
                try? store.database.deleteChecklistItem(id: item.id, taskId: item.taskId)
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 10))
                    .foregroundStyle(.tertiary)
            }
            .buttonStyle(.plain)
        }
        .onAppear { text = item.title }
        .onChange(of: item.title) { _, newVal in
            if text != newVal { text = newVal }
        }
    }

    private func commitTitle() {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean != item.title else { return }
        var copy = item
        copy.title = clean
        try? store.database.updateChecklistItem(copy)
    }
}
