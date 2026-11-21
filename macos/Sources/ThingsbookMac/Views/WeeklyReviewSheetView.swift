import SwiftUI
import ThingsbookCore

public struct WeeklyReviewSheetView: View {
    @EnvironmentObject private var store: ThingsbookStore
    @Environment(\.dismiss) private var dismiss

    @State private var currentStep: Int = 0
    @State private var nextActionDrafts: [String: String] = [:]
    @State private var feedbackMessage: String? = nil
    @State private var feedbackDismissTask: Task<Void, Never>? = nil

    public init() {}

    private var todayAndDeadlineTasks: [TaskItem] {
        let cal = Calendar.current
        let sodToday = cal.startOfDay(for: Date())
        let weekAhead = cal.date(byAdding: .day, value: 7, to: sodToday) ?? sodToday
        let weekAheadMillis = DateEpochConverter.startOfDayMillis(for: weekAhead, calendar: cal)

        var seen = Set<String>()
        var result: [TaskItem] = []

        for task in (store.todayDaytimeTasks + store.thisEveningTasks) where !seen.contains(task.id) {
            seen.insert(task.id)
            result.append(task)
        }
        for task in store.allOpenTasks where !seen.contains(task.id) {
            if let dl = task.deadline, dl <= weekAheadMillis {
                seen.insert(task.id)
                result.append(task)
            }
        }
        return result.sorted { lhs, rhs in
            let lDl = lhs.deadline ?? Int64.max
            let rDl = rhs.deadline ?? Int64.max
            if lDl != rDl { return lDl < rDl }
            return lhs.sortOrder < rhs.sortOrder
        }
    }

    private var stepSpecs: [(index: Int, title: String, icon: String, count: Int, tint: Color)] {
        [
            (0, store.tr("v2_review_step_inbox"), "tray.fill", store.inboxTasks.count, ThingsMacPalette.inboxBlue),
            (1, store.tr("v2_review_step_today"), "sun.max.fill", todayAndDeadlineTasks.count, ThingsMacPalette.todayYellow),
            (2, store.tr("v2_review_step_projects"), "checklist", store.stalledProjects.count, ThingsMacPalette.somedayAmber),
            (3, store.tr("v2_review_step_someday"), "cloud.fill", store.somedayTasks.count, ThingsMacPalette.logbookGreen)
        ]
    }

    public var body: some View {
        VStack(spacing: 0) {
            headerBar
            Divider()
            stepSelectorBar
            Divider()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    switch currentStep {
                    case 0:
                        inboxStepContent
                    case 1:
                        todayAndDeadlinesStepContent
                    case 2:
                        stalledProjectsStepContent
                    default:
                        somedayWrapUpStepContent
                    }
                }
                .padding(20)
            }
            .background(ThingsMacPalette.canvasBackground)

            Divider()
            footerBar
        }
        .frame(minWidth: 820, idealWidth: 860, maxWidth: 940, minHeight: 580, idealHeight: 620)
        .background(ThingsMacPalette.cardSurface)
    }

    private var headerBar: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(ThingsMacPalette.accentBlue.opacity(0.14))
                    .frame(width: 38, height: 38)
                Image(systemName: "checklist.checked")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(store.tr("v2_weekly_review_title"))
                    .font(.system(size: 17, weight: .bold))
                Text(store.tr("v2_weekly_review_subtitle"))
                    .font(.system(size: 12.5))
                    .foregroundStyle(.secondary)
            }
            .fixedSize(horizontal: true, vertical: false)

            Spacer(minLength: 8)

            if let feedbackMessage {
                HStack(spacing: 5) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 11.5, weight: .semibold))
                    Text(feedbackMessage)
                        .font(.system(size: 11.5, weight: .semibold))
                        .lineLimit(2)
                        .minimumScaleFactor(0.86)
                }
                .foregroundStyle(ThingsMacPalette.logbookGreen)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(
                    Capsule(style: .continuous)
                        .fill(ThingsMacPalette.logbookGreen.opacity(0.14))
                )
                .transition(.opacity)
            }

            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(.secondary)
                    .padding(4)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .thingsTooltip(store.tr("action_cancel"))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
    }

    private var stepSelectorBar: some View {
        HStack(alignment: .top, spacing: 8) {
            ForEach(stepSpecs, id: \.index) { spec in
                let isSelected = currentStep == spec.index
                Button {
                    withAnimation(.easeOut(duration: 0.16)) {
                        currentStep = spec.index
                    }
                } label: {
                    VStack(alignment: .leading, spacing: 5) {
                        HStack(alignment: .center, spacing: 6) {
                            Image(systemName: spec.icon)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(isSelected ? Color.white : spec.tint)

                            Spacer(minLength: 4)

                            Text("\(spec.count)")
                                .font(.system(size: 10.5, weight: .bold, design: .rounded))
                                .padding(.horizontal, 6.5)
                                .padding(.vertical, 1.5)
                                .background(
                                    Capsule(style: .continuous)
                                        .fill(isSelected ? Color.white.opacity(0.22) : Color.primary.opacity(0.08))
                                )
                        }

                        Text(spec.title)
                            .font(.system(size: 12.5, weight: isSelected ? .bold : .semibold))
                            .multilineTextAlignment(.leading)
                            .lineLimit(2)
                            .minimumScaleFactor(0.85)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .foregroundStyle(isSelected ? Color.white : Color.primary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(isSelected ? ThingsMacPalette.accentBlue : ThingsMacPalette.subtleContainer)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .strokeBorder(
                                isSelected ? ThingsMacPalette.accentBlue.opacity(0.45) : Color.primary.opacity(0.06),
                                lineWidth: 0.75
                            )
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    // MARK: - Step 1: Clear Inbox

    @ViewBuilder
    private var inboxStepContent: some View {
        if store.inboxTasks.isEmpty {
            calmEmptyState(icon: "checkmark.circle.fill", color: ThingsMacPalette.logbookGreen, message: store.tr("empty_inbox"))
        } else {
            ForEach(store.inboxTasks) { task in
                let displayTitle = task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 10) {
                        ThingsCheckboxButton(
                            status: task.status,
                            isSomeday: false,
                            isMilestone: task.isMilestone,
                            helpText: store.tr("macos_checkbox_help"),
                            onToggleComplete: {
                                store.toggleTaskCompletion(task)
                                showReviewFeedback("\(displayTitle) → \(store.tr("action_complete"))")
                            },
                            onCancelTask: { store.cancelTask(task) }
                        )

                        Button {
                            openTaskInWorkspace(task)
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(displayTitle)
                                    .font(.system(size: 13.5, weight: .medium))
                                    .foregroundStyle(.primary)
                                if !task.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                    Text(task.notes.trimmingCharacters(in: .whitespacesAndNewlines))
                                        .font(.system(size: 11.5))
                                        .foregroundStyle(.secondary)
                                        .lineLimit(1)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }

                    HStack(spacing: 6) {
                        reviewActionChip(icon: "sun.max.fill", tint: ThingsMacPalette.todayYellow, label: store.tr("view_today")) {
                            scheduleTask(task, to: .today, daysOffset: 0, actionLabel: store.tr("view_today"))
                        }
                        reviewActionChip(icon: "moon.stars.fill", tint: ThingsMacPalette.eveningPurple, label: store.tr("view_this_evening")) {
                            scheduleTask(task, to: .thisEvening, daysOffset: 0, actionLabel: store.tr("view_this_evening"))
                        }
                        reviewActionChip(icon: "sun.horizon.fill", tint: ThingsMacPalette.upcomingRed, label: store.tr("date_tomorrow")) {
                            scheduleTask(task, to: .upcoming, daysOffset: 1, actionLabel: store.tr("date_tomorrow"))
                        }
                        reviewActionChip(icon: "cloud.fill", tint: ThingsMacPalette.somedayAmber, label: store.tr("view_someday")) {
                            scheduleTask(task, to: .someday, daysOffset: nil, actionLabel: store.tr("view_someday"))
                        }
                        Spacer()
                        Button {
                            store.toggleTaskCompletion(task)
                            showReviewFeedback("\(displayTitle) → \(store.tr("action_complete"))")
                        } label: {
                            Label(store.tr("action_complete"), systemImage: "checkmark")
                                .font(.system(size: 11.5, weight: .medium))
                        }
                        .controlSize(.small)

                        Button(role: .destructive) {
                            store.deleteTask(task)
                            showReviewFeedback("\(displayTitle) → \(store.tr("action_delete"))")
                        } label: {
                            Image(systemName: "trash")
                                .font(.system(size: 11.5))
                                .foregroundStyle(ThingsMacPalette.upcomingRed)
                        }
                        .buttonStyle(.borderless)
                        .thingsTooltip(store.tr("action_delete"))
                    }
                }
                .padding(12)
                .thingsCardSurface(cornerRadius: 10)
            }
        }
    }

    // MARK: - Step 2: Today & Deadlines

    @ViewBuilder
    private var todayAndDeadlinesStepContent: some View {
        let items = todayAndDeadlineTasks
        let todayDay = DateEpochConverter.epochDay(for: Date())
        if items.isEmpty {
            calmEmptyState(icon: "sun.max.circle.fill", color: ThingsMacPalette.todayYellow, message: store.tr("empty_today"))
        } else {
            ForEach(items) { task in
                let displayTitle = task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title
                let whenDay = task.whenDate.map { DateEpochConverter.epochDay(forMillis: $0) }
                let isTodaySelected = task.scheduleState == .today || task.scheduleState == .thisEvening || whenDay == todayDay
                let isTomorrowSelected = task.scheduleState == .upcoming && whenDay == (todayDay + 1)
                let isPlus7Selected = task.scheduleState == .upcoming && whenDay == (todayDay + 7)

                HStack(spacing: 10) {
                    ThingsCheckboxButton(
                        status: task.status,
                        isSomeday: task.scheduleState == .someday,
                        isMilestone: task.isMilestone,
                        helpText: store.tr("macos_checkbox_help"),
                        onToggleComplete: {
                            store.toggleTaskCompletion(task)
                            showReviewFeedback("\(displayTitle) → \(store.tr("action_complete"))")
                        },
                        onCancelTask: { store.cancelTask(task) }
                    )

                    Button {
                        openTaskInWorkspace(task)
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(displayTitle)
                                .font(.system(size: 13.5, weight: .medium))
                                .foregroundStyle(.primary)
                            HStack(spacing: 6) {
                                if let proj = store.project(byId: task.projectId) {
                                    Text(proj.title)
                                        .font(.system(size: 11))
                                        .foregroundStyle(.secondary)
                                }
                                taskSchedulePill(task, todayDay: todayDay)
                                if let dlMillis = task.deadline {
                                    deadlinePill(dlMillis)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    HStack(spacing: 6) {
                        reviewActionChip(
                            icon: "sun.max.fill",
                            tint: ThingsMacPalette.todayYellow,
                            label: store.tr("view_today"),
                            isSelected: isTodaySelected
                        ) {
                            scheduleTask(task, to: .today, daysOffset: 0, actionLabel: store.tr("view_today"))
                        }
                        reviewActionChip(
                            icon: "sun.horizon.fill",
                            tint: ThingsMacPalette.upcomingRed,
                            label: store.tr("date_tomorrow"),
                            isSelected: isTomorrowSelected
                        ) {
                            scheduleTask(task, to: .upcoming, daysOffset: 1, actionLabel: store.tr("date_tomorrow"))
                        }
                        reviewActionChip(
                            icon: "calendar.badge.plus",
                            tint: ThingsMacPalette.accentBlue,
                            label: store.tr("macos_deadline_plus_7d"),
                            isSelected: isPlus7Selected
                        ) {
                            scheduleTask(task, to: .upcoming, daysOffset: 7, actionLabel: store.tr("macos_deadline_plus_7d"))
                        }
                        Button {
                            store.toggleTaskCompletion(task)
                            showReviewFeedback("\(displayTitle) → \(store.tr("action_complete"))")
                        } label: {
                            Image(systemName: "checkmark.circle")
                                .font(.system(size: 13.5))
                                .padding(3)
                                .contentShape(Circle())
                        }
                        .buttonStyle(.borderless)
                        .thingsTooltip(store.tr("action_complete"))
                    }
                }
                .padding(12)
                .thingsCardSurface(cornerRadius: 10)
            }
        }
    }

    // MARK: - Step 3: Projects Requiring Attention

    @ViewBuilder
    private var stalledProjectsStepContent: some View {
        let stalled = store.stalledProjects
        if stalled.isEmpty {
            calmEmptyState(icon: "checkmark.circle.fill", color: ThingsMacPalette.logbookGreen, message: store.tr("empty_view"))
        } else {
            ForEach(stalled) { project in
                let info = store.projectStalledInfo(project)
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 10) {
                        ProjectProgressRing(progress: project.progress, size: 17)

                        Button {
                            store.selectedDestination = .project(project.id)
                            dismiss()
                        } label: {
                            Text(project.title)
                                .font(.system(size: 14, weight: .semibold))
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)

                        if let area = store.area(byId: project.areaId) {
                            Text(area.title)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(ThingsMacPalette.areaGreen)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(ThingsMacPalette.areaGreen.opacity(0.12)))
                        }

                        Spacer()

                        if info.hasNoNextAction {
                            reasonPill(store.tr("v2_stalled_no_next_action"), color: ThingsMacPalette.upcomingRed)
                        }
                        if info.isUnreviewed14Days {
                            reasonPill(store.tr("v2_stalled_unreviewed"), color: ThingsMacPalette.somedayAmber)
                        }
                    }

                    HStack(spacing: 8) {
                        TextField(
                            store.tr("v2_add_next_action"),
                            text: Binding(
                                get: { nextActionDrafts[project.id] ?? "" },
                                set: { nextActionDrafts[project.id] = $0 }
                            )
                        )
                        .textFieldStyle(.roundedBorder)
                        .font(.system(size: 12.5))
                        .onSubmit {
                            submitNextAction(for: project.id, projectTitle: project.title)
                        }

                        Button(store.tr("macos_action_add")) {
                            submitNextAction(for: project.id, projectTitle: project.title)
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                        .disabled((nextActionDrafts[project.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                        Button(store.tr("v2_mark_reviewed")) {
                            store.markProjectReviewed(project.id)
                            showReviewFeedback("\(project.title) → \(store.tr("v2_mark_reviewed"))")
                        }
                        .controlSize(.small)

                        Button(store.tr("view_someday")) {
                            store.moveProjectToSomeday(project)
                            showReviewFeedback("\(project.title) → \(store.tr("view_someday"))")
                        }
                        .controlSize(.small)
                    }
                }
                .padding(13)
                .thingsCardSurface(cornerRadius: 10)
            }
        }
    }

    // MARK: - Step 4: Someday & Wrap-Up

    @ViewBuilder
    private var somedayWrapUpStepContent: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(ThingsMacPalette.logbookGreen.opacity(0.15))
                    .frame(width: 42, height: 42)
                Image(systemName: "sparkles")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.logbookGreen)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("\(store.completedTasksLast7DaysCount) · \(store.tr("settings_stat_completed")) (7d)")
                    .font(.system(size: 14.5, weight: .bold))
                Text("\(store.tr("view_logbook")) · \(store.tr("v2_weekly_review_subtitle"))")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(14)
        .thingsCardSurface(cornerRadius: 11)

        if store.somedayTasks.isEmpty {
            calmEmptyState(icon: "cloud.circle.fill", color: ThingsMacPalette.somedayAmber, message: store.tr("empty_view"))
        } else {
            ForEach(store.somedayTasks) { task in
                let displayTitle = task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title
                HStack(spacing: 10) {
                    ThingsCheckboxButton(
                        status: task.status,
                        isSomeday: true,
                        isMilestone: task.isMilestone,
                        helpText: store.tr("macos_checkbox_help"),
                        onToggleComplete: {
                            store.toggleTaskCompletion(task)
                            showReviewFeedback("\(displayTitle) → \(store.tr("action_complete"))")
                        },
                        onCancelTask: { store.cancelTask(task) }
                    )

                    Button {
                        openTaskInWorkspace(task)
                    } label: {
                        Text(displayTitle)
                            .font(.system(size: 13.5, weight: .medium))
                            .foregroundStyle(.primary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    reviewActionChip(icon: "sun.max.fill", tint: ThingsMacPalette.todayYellow, label: store.tr("view_today")) {
                        scheduleTask(task, to: .today, daysOffset: 0, actionLabel: store.tr("view_today"))
                    }
                    reviewActionChip(icon: "safari.fill", tint: ThingsMacPalette.anytimeTeal, label: store.tr("view_anytime")) {
                        scheduleTask(task, to: .anytime, daysOffset: nil, actionLabel: store.tr("view_anytime"))
                    }
                    Button(role: .destructive) {
                        store.deleteTask(task)
                        showReviewFeedback("\(displayTitle) → \(store.tr("action_delete"))")
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 11.5))
                            .foregroundStyle(ThingsMacPalette.upcomingRed)
                    }
                    .buttonStyle(.borderless)
                    .thingsTooltip(store.tr("action_delete"))
                }
                .padding(12)
                .thingsCardSurface(cornerRadius: 10)
            }
        }
    }

    private var footerBar: some View {
        HStack {
            if currentStep > 0 {
                Button {
                    withAnimation(.easeOut(duration: 0.16)) {
                        currentStep -= 1
                    }
                } label: {
                    Image(systemName: "chevron.left")
                }
                .controlSize(.regular)
            }

            if currentStep < 3 {
                Button {
                    withAnimation(.easeOut(duration: 0.16)) {
                        currentStep += 1
                    }
                } label: {
                    Image(systemName: "chevron.right")
                }
                .controlSize(.regular)
            }

            Spacer()

            Button(store.tr("action_cancel")) {
                dismiss()
            }

            Button {
                store.completeWeeklyReview()
                dismiss()
            } label: {
                Label(store.tr("v2_complete_weekly_review"), systemImage: "checkmark.circle.fill")
                    .font(.system(size: 13, weight: .semibold))
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    // MARK: - Helpers

    private func openTaskInWorkspace(_ task: TaskItem) {
        if let pid = task.projectId {
            store.selectedDestination = .project(pid)
        } else if let aid = task.areaId {
            store.selectedDestination = .area(aid)
        } else {
            switch task.scheduleState {
            case .today, .thisEvening:
                store.selectedDestination = .smartView(.today)
            case .upcoming:
                store.selectedDestination = .smartView(.upcoming)
            case .someday:
                store.selectedDestination = .smartView(.someday)
            case .anytime:
                store.selectedDestination = .smartView(.anytime)
            case .inbox:
                store.selectedDestination = .smartView(.inbox)
            }
        }
        dismiss()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            store.selectedTaskIds = [task.id]
            store.expandedTaskId = task.id
        }
    }

    private func showReviewFeedback(_ message: String) {
        feedbackDismissTask?.cancel()
        withAnimation(.easeOut(duration: 0.15)) {
            feedbackMessage = message
        }
        feedbackDismissTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_800_000_000)
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.2)) {
                if feedbackMessage == message {
                    feedbackMessage = nil
                }
            }
        }
    }

    private func submitNextAction(for projectId: String, projectTitle: String) {
        let raw = nextActionDrafts[projectId] ?? ""
        guard store.addNextActionToProject(projectId, title: raw) != nil else { return }
        nextActionDrafts[projectId] = ""
        showReviewFeedback("\(projectTitle) → \(store.tr("view_today"))")
    }

    private func scheduleTask(_ task: TaskItem, to state: ScheduleState, daysOffset: Int?, actionLabel: String) {
        var copy = task
        copy.scheduleState = state
        if let daysOffset {
            let base = Calendar.current.startOfDay(for: Date())
            let target = Calendar.current.date(byAdding: .day, value: daysOffset, to: base) ?? base
            let targetMillis = DateEpochConverter.startOfDayMillis(for: target)
            copy.whenDate = targetMillis
            if daysOffset > 0, let existingDl = copy.deadline, existingDl < targetMillis {
                copy.deadline = targetMillis
            }
        } else {
            copy.whenDate = nil
        }
        store.updateTask(copy)
        let displayTitle = task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title
        showReviewFeedback("\(displayTitle) → \(actionLabel)")
    }

    @ViewBuilder
    private func taskSchedulePill(_ task: TaskItem, todayDay: Int) -> some View {
        let spec: (icon: String, text: String, tint: Color)? = {
            switch task.scheduleState {
            case .today:
                return ("sun.max.fill", store.tr("view_today"), ThingsMacPalette.todayYellow)
            case .thisEvening:
                return ("moon.stars.fill", store.tr("view_this_evening"), ThingsMacPalette.eveningPurple)
            case .upcoming:
                if let whenMs = task.whenDate {
                    let wDay = DateEpochConverter.epochDay(forMillis: whenMs)
                    if wDay == todayDay + 1 {
                        return ("sun.horizon.fill", store.tr("date_tomorrow"), ThingsMacPalette.upcomingRed)
                    }
                    let df = DateFormatter()
                    df.locale = Locale(identifier: store.preferences.language.resolvedCode)
                    df.setLocalizedDateFormatFromTemplate("dMMM")
                    return ("calendar", df.string(from: DateEpochConverter.date(fromMillis: whenMs)), ThingsMacPalette.accentBlue)
                }
                return ("calendar", store.tr("view_upcoming"), ThingsMacPalette.accentBlue)
            case .someday:
                return ("cloud.fill", store.tr("view_someday"), ThingsMacPalette.somedayAmber)
            case .anytime:
                return ("safari.fill", store.tr("view_anytime"), ThingsMacPalette.anytimeTeal)
            case .inbox:
                return ("tray.fill", store.tr("view_inbox"), ThingsMacPalette.inboxBlue)
            }
        }()

        if let spec {
            HStack(spacing: 3.5) {
                Image(systemName: spec.icon)
                    .font(.system(size: 9))
                Text(spec.text)
                    .font(.system(size: 10.5, weight: .semibold))
                    .lineLimit(1)
            }
            .fixedSize(horizontal: true, vertical: false)
            .foregroundStyle(spec.tint)
            .padding(.horizontal, 6.5)
            .padding(.vertical, 2)
            .background(Capsule().fill(spec.tint.opacity(0.13)))
        }
    }

    private func deadlinePill(_ dlMillis: Int64) -> some View {
        let todayDay = DateEpochConverter.epochDay(for: Date())
        let dlDay = DateEpochConverter.epochDay(forMillis: dlMillis)
        let diff = dlDay - todayDay
        let text: String = {
            if diff == 0 { return store.tr("deadline_today") }
            if diff < 0 { return store.trf("deadline_days_overdue", abs(diff)) }
            return store.trf("deadline_days_left", diff)
        }()
        let tint: Color = diff <= 0 ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.somedayAmber
        return HStack(spacing: 4) {
            Image(systemName: "flag.fill")
                .font(.system(size: 9.5))
            Text(text)
                .font(.system(size: 11, weight: .semibold))
                .lineLimit(1)
        }
        .fixedSize(horizontal: true, vertical: false)
        .foregroundStyle(tint)
        .padding(.horizontal, 7)
        .padding(.vertical, 2)
        .background(Capsule().fill(tint.opacity(0.12)))
    }

    private func reasonPill(_ label: String, color: Color) -> some View {
        Text(label)
            .font(.system(size: 10.5, weight: .semibold))
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 2.5)
            .background(Capsule().fill(color.opacity(0.13)))
    }

    private func reviewActionChip(
        icon: String,
        tint: Color,
        label: String,
        isSelected: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: isSelected ? "checkmark" : icon)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(tint)
                Text(label)
                    .font(.system(size: 11.5, weight: isSelected ? .semibold : .medium))
                    .foregroundStyle(isSelected ? tint : .primary)
                    .lineLimit(1)
            }
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 8.5)
            .padding(.vertical, 4.5)
            .background(
                Capsule(style: .continuous)
                    .fill(isSelected ? tint.opacity(0.18) : Color.primary.opacity(0.06))
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(isSelected ? tint.opacity(0.55) : Color.clear, lineWidth: 1)
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func calmEmptyState(icon: String, color: Color, message: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 34))
                .foregroundStyle(color)
            Text(message)
                .font(.system(size: 13.5, weight: .medium))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 56)
    }
}
