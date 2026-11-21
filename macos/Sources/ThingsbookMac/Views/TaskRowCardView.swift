import SwiftUI
import AppKit
import ThingsbookCore

public struct TaskRowCardView: View {
    @ObservedObject var store: ThingsbookStore
    public let task: TaskItem
    public var showContextSubtitle: Bool = true

    @State private var isCollapsedTimeblockPopoverOpen = false
    @State private var isCollapsedDependenciesPopoverOpen = false
    @State private var isCollapsedDeadlinePopoverOpen = false
    @State private var isCollapsedRepeatPopoverOpen = false
    @State private var isCollapsedReminderPopoverOpen = false
    @State private var isCollapsedCustomDurationPopoverOpen = false
    @State private var isInlineQuickEditing = false
    @State private var inlineDraftTitle = ""
    @FocusState private var isInlineTitleFocused: Bool
    @State private var isRowWhenPopoverOpen = false
    @State private var isHoveringRow = false
    @State private var isHoveringQuickEditIcon = false
    @State private var isHoveringQuickWhenIcon = false
    @State private var isHoveringOpenIcon = false

    private var isSelected: Bool {
        store.selectedTaskIds.contains(task.id)
    }

    private var latestTask: TaskItem {
        store.taskIncludingTrash(byId: task.id) ?? task
    }

    public init(store: ThingsbookStore, task: TaskItem, showContextSubtitle: Bool = true) {
        self.store = store
        self.task = task
        self.showContextSubtitle = showContextSubtitle
    }

    public var body: some View {
        collapsedRow
            .contextMenu {
                taskContextMenu
            }
            .onChange(of: isInlineTitleFocused) { wasFocused, nowFocused in
                if wasFocused && !nowFocused && isInlineQuickEditing {
                    commitInlineQuickEdit()
                }
            }
    }

    // MARK: - Always-Collapsed Calm Things-for-Mac Row

    private var collapsedRow: some View {
        let isActivelyBlocked = store.isTaskActivelyBlocked(task)

        return HStack(alignment: .center, spacing: 12) {
            ThingsCheckboxButton(
                status: task.status,
                isSomeday: task.scheduleState == .someday,
                isMilestone: task.isMilestone,
                helpText: store.tr("macos_checkbox_help"),
                onToggleComplete: {
                    if isInlineQuickEditing {
                        commitInlineQuickEdit()
                    }
                    store.toggleTaskCompletion(latestTask)
                },
                onCancelTask: {
                    if isInlineQuickEditing {
                        commitInlineQuickEdit()
                    }
                    store.cancelTask(latestTask)
                }
            )

            VStack(alignment: .leading, spacing: 2.5) {
                HStack(spacing: 6) {
                    if task.isPinned {
                        Image(systemName: "pin.fill")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.todayYellow)
                            .thingsTooltip(store.tr("v25_pinned_badge"))
                    }

                    if task.scheduleState == .thisEvening && store.selectedDestination != .smartView(.today) {
                        Image(systemName: "moon.fill")
                            .font(.system(size: 10.5))
                            .foregroundStyle(ThingsMacPalette.eveningPurple)
                    }

                    if isInlineQuickEditing {
                        HStack(spacing: 6) {
                            TextField(store.tr("placeholder_todo_title"), text: $inlineDraftTitle)
                                .textFieldStyle(.plain)
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(.primary)
                                .focused($isInlineTitleFocused)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(
                                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                                        .fill(ThingsMacPalette.cardSurface)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 6, style: .continuous)
                                                .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.55), lineWidth: 1)
                                        )
                                )
                                .onSubmit {
                                    commitInlineQuickEdit()
                                }
                                .onExitCommand {
                                    cancelInlineQuickEdit()
                                }

                            Button {
                                commitInlineQuickEdit()
                            } label: {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 10.5, weight: .bold))
                                    .foregroundStyle(.white)
                                    .frame(width: 22, height: 22)
                                    .background(
                                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                                            .fill(ThingsMacPalette.accentBlue)
                                    )
                                    .contentShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .thingsTooltip(store.tr("action_done"))
                        }
                    } else {
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
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)

            // Trailing metadata badges + permanently reserved hover Quick Edit / When / Open Details slot (zero layout shift on hover)
            HStack(spacing: 6) {
                if task.isMilestone {
                    Text("◆ \(store.tr("v30_milestone_label"))")
                        .font(.system(size: 11, weight: .semibold))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(ThingsMacPalette.milestoneViolet)
                        .padding(.horizontal, 7.5)
                        .padding(.vertical, 2.5)
                        .background(
                            Capsule(style: .continuous)
                                .fill(ThingsMacPalette.milestoneViolet.opacity(0.13))
                        )
                        .overlay(
                            Capsule(style: .continuous)
                                .strokeBorder(ThingsMacPalette.milestoneViolet.opacity(0.28), lineWidth: 0.75)
                        )
                        .thingsTooltip(store.tr("v30_milestone_label"))
                }
                if let blockedBadge = store.blockedByBadgeText(for: task) {
                    Button {
                        isCollapsedDependenciesPopoverOpen = true
                    } label: {
                        HStack(spacing: 3.5) {
                            Image(systemName: "hourglass")
                                .font(.system(size: 10, weight: .semibold))
                            Text(blockedBadge)
                                .font(.system(size: 11, weight: .semibold))
                                .lineLimit(1)
                                .truncationMode(.tail)
                                .frame(maxWidth: 230, alignment: .leading)
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(ThingsMacPalette.somedayAmber)
                        .padding(.horizontal, 7.5)
                        .padding(.vertical, 2.5)
                        .background(Capsule().fill(ThingsMacPalette.somedayAmber.opacity(0.14)))
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(blockedBadge)
                }

                if let effort = task.resolvedEffort {
                    let tint = effortTint(for: effort)
                    let label = effortLocalizedTitle(for: effort)
                    Menu {
                        effortMenuItems(for: latestTask)
                    } label: {
                        HStack(spacing: 3.5) {
                            Image(systemName: effort.sfSymbol)
                                .font(.system(size: 10, weight: .semibold))
                            Text(label)
                                .font(.system(size: 11, weight: .semibold))
                                .lineLimit(1)
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(tint)
                        .padding(.horizontal, 7.5)
                        .padding(.vertical, 2.5)
                        .background(Capsule().fill(tint.opacity(0.12)))
                    }
                    .menuStyle(.borderlessButton)
                    .menuIndicator(.hidden)
                    .fixedSize()
                    .thingsTooltip("\(store.tr("v30_effort_label")): \(label)")
                }

                if !task.attachments.isEmpty {
                    HStack(spacing: 3) {
                        Image(systemName: "paperclip")
                            .font(.system(size: 10, weight: .semibold))
                        Text("\(task.attachments.count)")
                            .font(.system(size: 11, weight: .medium, design: .rounded))
                    }
                    .fixedSize(horizontal: true, vertical: false)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2.5)
                    .background(Capsule().fill(Color.primary.opacity(0.055)))
                    .thingsTooltip("\(store.tr("attachments_section_title")): \(task.attachments.count)")
                }

                if task.scheduledStartMinutes != nil, let tbPill = task.formattedTimeblockPill {
                    Button {
                        isCollapsedTimeblockPopoverOpen = true
                    } label: {
                        HStack(spacing: 3.5) {
                            Image(systemName: "clock.badge.checkmark")
                                .font(.system(size: 10, weight: .semibold))
                            Text(tbPill)
                                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                .lineLimit(1)
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(ThingsMacPalette.anytimeTeal)
                        .padding(.horizontal, 7.5)
                        .padding(.vertical, 2.5)
                        .background(Capsule().fill(ThingsMacPalette.anytimeTeal.opacity(0.12)))
                        .contentShape(Capsule())
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
                } else if let durPill = task.formattedStandaloneDurationPill {
                    Menu {
                        durationMenuItems(for: latestTask)
                    } label: {
                        HStack(spacing: 3.5) {
                            Image(systemName: "timer")
                                .font(.system(size: 10, weight: .semibold))
                            Text(durPill)
                                .font(.system(size: 11, weight: .semibold, design: .rounded))
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(ThingsMacPalette.anytimeTeal)
                        .padding(.horizontal, 7.5)
                        .padding(.vertical, 2.5)
                        .background(Capsule().fill(ThingsMacPalette.anytimeTeal.opacity(0.12)))
                    }
                    .menuStyle(.borderlessButton)
                    .menuIndicator(.hidden)
                    .fixedSize()
                    .thingsTooltip("\(store.tr("v30_duration_label")): \(durPill)")
                }

                if let rem = task.reminderTime {
                    Button {
                        isCollapsedReminderPopoverOpen = true
                    } label: {
                        HStack(spacing: 3) {
                            Image(systemName: "bell")
                                .font(.system(size: 10, weight: .semibold))
                            Text(shortTimeString(rem))
                                .font(.system(size: 11, weight: .semibold, design: .rounded))
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2.5)
                        .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.11)))
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(store.trf("when_task_reminder_set", shortTimeString(rem)))
                    .popover(isPresented: $isCollapsedReminderPopoverOpen) {
                        WhenPopoverView(
                            store: store,
                            currentSchedule: latestTask.scheduleState,
                            currentWhenMillis: latestTask.whenDate,
                            currentReminderMillis: latestTask.reminderTime,
                            onApply: applyRowWhenSelection
                        )
                    }
                }

                if !task.checklistItems.isEmpty {
                    let done = task.checklistItems.filter(\.isCompleted).count
                    HStack(spacing: 3.5) {
                        Image(systemName: "checklist")
                            .font(.system(size: 10))
                        Text("\(done)/\(task.checklistItems.count)")
                            .font(.system(size: 11, weight: .medium, design: .rounded))
                    }
                    .fixedSize(horizontal: true, vertical: false)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2.5)
                    .background(Capsule().fill(Color.primary.opacity(0.055)))
                    .thingsTooltip("\(store.tr("placeholder_checklist")): \(done)/\(task.checklistItems.count)")
                }

                if let rule = task.recurrenceRule {
                    Button {
                        isCollapsedRepeatPopoverOpen = true
                    } label: {
                        HStack(spacing: 3.5) {
                            Image(systemName: "repeat")
                                .font(.system(size: 10))
                            Text(rule.formatShortLabel(locale: store.preferences.language.locale))
                                .font(.system(size: 11, weight: .medium))
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2.5)
                        .background(Capsule().fill(Color.primary.opacity(0.055)))
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip("\(store.tr("action_repeat")): \(rule.formatShortLabel(locale: store.preferences.language.locale))")
                    .popover(isPresented: $isCollapsedRepeatPopoverOpen) {
                        RepeatEditorPopoverView(store: store, currentRule: latestTask.recurrenceRule) { newRule in
                            var copy = latestTask
                            copy.recurrenceRule = newRule
                            if newRule != nil && copy.whenDate == nil {
                                copy.scheduleState = .today
                                copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
                            }
                            store.updateTask(copy)
                        }
                    }
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

                if !isInlineQuickEditing {
                    let showHoverActions = isHoveringRow || isRowWhenPopoverOpen
                    HStack(spacing: 3) {
                        rowHoverActionButton(
                            systemImage: "pencil",
                            isHovered: isHoveringQuickEditIcon,
                            tooltip: store.tr("v30_quick_edit_action"),
                            onHoverChange: { isHoveringQuickEditIcon = $0 }
                        ) {
                            beginInlineQuickEdit()
                        }

                        rowHoverActionButton(
                            systemImage: "calendar",
                            isHovered: isHoveringQuickWhenIcon || isRowWhenPopoverOpen,
                            tooltip: store.tr("action_when"),
                            onHoverChange: { isHoveringQuickWhenIcon = $0 }
                        ) {
                            isRowWhenPopoverOpen = true
                        }
                        .popover(isPresented: $isRowWhenPopoverOpen) {
                            WhenPopoverView(
                                store: store,
                                currentSchedule: latestTask.scheduleState,
                                currentWhenMillis: latestTask.whenDate,
                                currentReminderMillis: latestTask.reminderTime,
                                onApply: applyRowWhenSelection
                            )
                        }

                        rowHoverActionButton(
                            systemImage: "slider.horizontal.3",
                            isHovered: isHoveringOpenIcon,
                            tooltip: store.tr("v30_open_details_action"),
                            onHoverChange: { isHoveringOpenIcon = $0 }
                        ) {
                            store.openTaskModal(task.id)
                        }
                    }
                    .opacity(showHoverActions ? 1.0 : 0.0)
                    .allowsHitTesting(showHoverActions)
                }
            }
            .fixedSize(horizontal: true, vertical: false)
        }
        .frame(maxWidth: .infinity, minHeight: 22, alignment: .leading)
        .opacity(isActivelyBlocked ? 0.62 : 1.0)
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
        .background(alignment: .trailing) {
            HStack(spacing: 0) {
                Color.clear
                    .frame(width: 1, height: 1)
                    .popover(isPresented: $isCollapsedDependenciesPopoverOpen) {
                        DependenciesPopoverView(store: store, task: latestTask)
                    }
                Color.clear
                    .frame(width: 1, height: 1)
                    .popover(isPresented: $isCollapsedCustomDurationPopoverOpen) {
                        CustomDurationPopoverView(
                            store: store,
                            currentDurationMinutes: latestTask.durationMinutes
                        ) { customMins in
                            store.setTaskStandaloneDuration(latestTask.id, durationMinutes: customMins)
                        }
                    }
            }
            .padding(.trailing, 24)
        }
        .contentShape(Rectangle())
        .onHover { hovering in
            isHoveringRow = hovering
            if !hovering {
                isHoveringQuickEditIcon = false
                isHoveringQuickWhenIcon = false
                isHoveringOpenIcon = false
            }
        }
        .thingsInstantTaskTap(
            isEnabled: !isInlineQuickEditing,
            onSingleClick: { modifiers in
                store.handleRowClick(taskId: task.id, modifiers: modifiers)
            },
            onDoubleClick: {
                store.openTaskModal(task.id)
            }
        )
    }

    private func rowHoverActionButton(
        systemImage: String,
        isHovered: Bool,
        tooltip: String,
        onHoverChange: @escaping (Bool) -> Void,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(isHovered ? ThingsMacPalette.accentBlue : .secondary)
                .frame(width: 24, height: 22)
                .background(
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(
                            isHovered
                                ? ThingsMacPalette.accentBlue.opacity(0.13)
                                : Color.primary.opacity(0.055)
                        )
                )
                .contentShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover(perform: onHoverChange)
        .thingsTooltip(tooltip)
    }

    private func beginInlineQuickEdit() {
        inlineDraftTitle = latestTask.title
        isInlineQuickEditing = true
        store.selectedTaskIds = [task.id]
        store.lastSelectedTaskId = task.id
        DispatchQueue.main.async {
            isInlineTitleFocused = true
        }
    }

    private func commitInlineQuickEdit() {
        let trimmed = inlineDraftTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        isInlineQuickEditing = false
        isInlineTitleFocused = false
        var copy = latestTask
        if copy.title != trimmed {
            copy.title = trimmed
            store.updateTask(copy)
        }
    }

    private func cancelInlineQuickEdit() {
        isInlineQuickEditing = false
        isInlineTitleFocused = false
        inlineDraftTitle = latestTask.title
    }

    private func applyRowWhenSelection(_ resolved: ResolvedWhenSelection) {
        var copy = latestTask
        if isInlineQuickEditing {
            let trimmed = inlineDraftTitle.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                copy.title = trimmed
            }
        }
        copy.scheduleState = resolved.scheduleState
        copy.whenDate = resolved.whenDateEpochMillis
        copy.reminderTime = resolved.reminderEpochMillis
        store.updateTask(copy)
    }

    private var showNotesPreviewInCollapsed: Bool {
        !showContextSubtitle && !task.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    // MARK: - Context Menu & Helpers

    @ViewBuilder
    private var taskContextMenu: some View {
        let isBatchContext = store.selectedTaskIds.count > 1 && store.selectedTaskIds.contains(task.id)

        if isBatchContext {
            Button(store.tr("action_complete")) {
                store.completeSelectedTasks()
            }
            Button(store.tr("view_today")) {
                store.scheduleSelectedTasks(.today)
            }
            Button(store.tr("v25_batch_someday")) {
                store.scheduleSelectedTasks(.someday)
            }
            Button(store.tr("v25_batch_pin")) {
                store.togglePinForSelectedTasks()
            }
            Button {
                store.toggleMilestoneForSelectedTasks()
            } label: {
                Label(store.tr("v30_mark_as_milestone"), systemImage: "diamond")
            }
            Button(store.tr("v25_batch_duplicate")) {
                store.duplicateSelectedTasks()
            }
            Button {
                store.copySelectedTasksToPasteboard()
            } label: {
                Label(store.tr("action_copy_todo"), systemImage: "doc.on.doc")
            }
            Divider()
            Button(role: .destructive) {
                store.deleteSelectedTasks()
            } label: {
                Text(store.tr("action_delete"))
            }
        } else {
            Button {
                beginInlineQuickEdit()
            } label: {
                Label(store.tr("v30_quick_edit_action"), systemImage: "pencil")
            }
            Button {
                isRowWhenPopoverOpen = true
            } label: {
                Label(store.tr("action_when"), systemImage: "calendar")
            }
            Button {
                store.openTaskModal(task.id)
            } label: {
                Label(store.tr("v30_open_details_action"), systemImage: "slider.horizontal.3")
            }
            Divider()
            Button(task.status == .completed ? store.tr("action_reopen_todo") : store.tr("action_complete")) {
                store.toggleTaskCompletion(latestTask)
            }
            Button(task.status == .canceled ? store.tr("action_reopen_todo") : store.tr("action_cancel_todo")) {
                store.cancelTask(latestTask)
            }
            Button(store.tr(latestTask.isPinned ? "v25_unpin_task" : "v25_pin_task")) {
                store.setTasksPinned([latestTask.id], isPinned: !latestTask.isPinned)
            }
            Button {
                store.setTasksMilestone([latestTask.id], isMilestone: !latestTask.isMilestone)
            } label: {
                Label(
                    store.tr(latestTask.isMilestone ? "v30_remove_milestone" : "v30_mark_as_milestone"),
                    systemImage: latestTask.isMilestone ? "diamond.fill" : "diamond"
                )
            }
            Divider()
            Button(store.tr("view_today")) {
                store.scheduleTasks([latestTask.id], to: .today)
            }
            Button(store.tr("view_this_evening")) {
                store.scheduleTasks([latestTask.id], to: .thisEvening)
            }
            Button(store.tr("view_someday")) {
                store.scheduleTasks([latestTask.id], to: .someday)
            }
            Menu(store.tr("v30_duration_label")) {
                durationMenuItems(for: latestTask)
            }
            Menu(store.tr("v30_effort_label")) {
                effortMenuItems(for: latestTask)
            }
            Button(store.tr("v30_blocked_by_action")) {
                isCollapsedDependenciesPopoverOpen = true
            }
            Menu(store.tr("v2_timeblock_title")) {
                Button("09:00 · 30m") { store.setTaskTimeblock(latestTask.id, startMinutes: 9 * 60, durationMinutes: 30) }
                Button("11:00 · 45m") { store.setTaskTimeblock(latestTask.id, startMinutes: 11 * 60, durationMinutes: 45) }
                Button("14:00 · 60m") { store.setTaskTimeblock(latestTask.id, startMinutes: 14 * 60, durationMinutes: 60) }
                Button("16:00 · 45m") { store.setTaskTimeblock(latestTask.id, startMinutes: 16 * 60, durationMinutes: 45) }
                if latestTask.scheduledStartMinutes != nil {
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
                _ = try? store.database.duplicateTask(task.id, copySuffix: store.tr("v25_copy_suffix"))
            }
            Button {
                store.copyTaskToPasteboard(latestTask)
            } label: {
                Label(store.tr("action_copy_todo"), systemImage: "doc.on.doc")
            }
            Button(store.tr("context_select_task")) {
                store.selectedTaskIds.insert(task.id)
                store.lastSelectedTaskId = task.id
            }
            Divider()
            Button(role: .destructive) {
                store.deleteTask(latestTask)
            } label: {
                Text(store.tr("action_delete"))
            }
        }
    }

    @ViewBuilder
    private func durationMenuItems(for targetTask: TaskItem) -> some View {
        ForEach(TaskItem.standaloneDurationPresets, id: \.self) { mins in
            let isCurrent = targetTask.durationMinutes == mins
            Button {
                store.setTaskStandaloneDuration(targetTask.id, durationMinutes: mins)
            } label: {
                if isCurrent {
                    Label(TaskItem.formatCompactDuration(mins), systemImage: "checkmark")
                } else {
                    Text(TaskItem.formatCompactDuration(mins))
                }
            }
        }
        Divider()
        Button(store.tr("v30_duration_custom")) {
            if targetTask.id == task.id {
                isCollapsedCustomDurationPopoverOpen = true
            } else {
                store.openTaskModal(targetTask.id)
            }
        }
        if (targetTask.durationMinutes ?? 0) > 0 {
            Button(store.tr("v30_duration_clear"), role: .destructive) {
                store.setTaskStandaloneDuration(targetTask.id, durationMinutes: nil)
            }
        }
    }

    @ViewBuilder
    private func effortMenuItems(for targetTask: TaskItem) -> some View {
        ForEach(TaskEffortLevel.allCases, id: \.rawValue) { level in
            let isCurrent = targetTask.resolvedEffort == level
            Button {
                store.setTaskEffort(targetTask.id, effortLevel: level.rawValue)
            } label: {
                Label(
                    effortLocalizedTitle(for: level),
                    systemImage: isCurrent ? "checkmark" : level.sfSymbol
                )
            }
        }
        if targetTask.resolvedEffort != nil {
            Divider()
            Button(store.tr("v30_effort_none")) {
                store.setTaskEffort(targetTask.id, effortLevel: nil)
            }
        }
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
        return Button {
            isCollapsedDeadlinePopoverOpen = true
        } label: {
            HStack(spacing: 3.5) {
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
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .thingsTooltip("\(store.tr("action_deadline")): \(info.label)")
        .popover(isPresented: $isCollapsedDeadlinePopoverOpen) {
            DeadlinePopoverView(store: store, currentDeadlineMillis: latestTask.deadline) { dl in
                var copy = latestTask
                copy.deadline = dl
                store.updateTask(copy)
            }
        }
    }

    private func shortTimeString(_ millis: Int64) -> String {
        let df = DateFormatter()
        df.timeStyle = .short
        return df.string(from: DateEpochConverter.date(fromMillis: millis))
    }
}

// MARK: - Checklist Row with Up / Down / Delete Controls

struct ChecklistItemRowView: View {
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
