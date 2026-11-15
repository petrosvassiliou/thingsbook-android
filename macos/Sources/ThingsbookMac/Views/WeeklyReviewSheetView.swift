import SwiftUI
import ThingsbookCore

public struct WeeklyReviewSheetView: View {
    @EnvironmentObject private var store: ThingsbookStore
    @Environment(\.dismiss) private var dismiss

    @State private var currentStep: Int = 0
    @State private var nextActionDrafts: [String: String] = [:]

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
            (1, store.tr("v2_review_step_today"), "star.fill", todayAndDeadlineTasks.count, ThingsMacPalette.todayYellow),
            (2, store.tr("v2_review_step_projects"), "chart.pie.fill", store.stalledProjects.count, ThingsMacPalette.somedayAmber),
            (3, store.tr("v2_review_step_someday"), "archivebox.fill", store.somedayTasks.count, ThingsMacPalette.logbookGreen)
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
        .frame(width: 740, height: 590)
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

            Spacer()

            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .help(store.tr("action_cancel"))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
    }

    private var stepSelectorBar: some View {
        HStack(spacing: 8) {
            ForEach(stepSpecs, id: \.index) { spec in
                let isSelected = currentStep == spec.index
                Button {
                    withAnimation(.easeOut(duration: 0.16)) {
                        currentStep = spec.index
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: spec.icon)
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundStyle(isSelected ? Color.white : spec.tint)
                        Text(spec.title)
                            .font(.system(size: 12, weight: isSelected ? .semibold : .medium))
                            .lineLimit(1)
                        Text("\(spec.count)")
                            .font(.system(size: 10.5, weight: .bold, design: .rounded))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 1.5)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(isSelected ? Color.white.opacity(0.22) : Color.primary.opacity(0.08))
                            )
                    }
                    .foregroundStyle(isSelected ? Color.white : Color.primary)
                    .padding(.horizontal, 11)
                    .padding(.vertical, 7)
                    .frame(maxWidth: .infinity)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(isSelected ? ThingsMacPalette.accentBlue : ThingsMacPalette.subtleContainer)
                    )
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
            calmEmptyState(icon: "checkmark.seal.fill", color: ThingsMacPalette.logbookGreen, message: store.tr("empty_inbox"))
        } else {
            ForEach(store.inboxTasks) { task in
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 10) {
                        ThingsCheckboxButton(
                            status: task.status,
                            isSomeday: false,
                            helpText: store.tr("macos_checkbox_help"),
                            onToggleComplete: { store.toggleTaskCompletion(task) },
                            onCancelTask: { store.cancelTask(task) }
                        )

                        VStack(alignment: .leading, spacing: 2) {
                            Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                                .font(.system(size: 13.5, weight: .medium))
                            if !task.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                Text(task.notes.trimmingCharacters(in: .whitespacesAndNewlines))
                                    .font(.system(size: 11.5))
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        Spacer()
                    }

                    HStack(spacing: 6) {
                        reviewActionChip(icon: "star.fill", tint: ThingsMacPalette.todayYellow, label: store.tr("view_today")) {
                            scheduleTask(task, to: .today, daysOffset: 0)
                        }
                        reviewActionChip(icon: "moon.stars.fill", tint: ThingsMacPalette.eveningPurple, label: store.tr("view_this_evening")) {
                            scheduleTask(task, to: .thisEvening, daysOffset: 0)
                        }
                        reviewActionChip(icon: "sun.horizon.fill", tint: ThingsMacPalette.upcomingRed, label: store.tr("date_tomorrow")) {
                            scheduleTask(task, to: .upcoming, daysOffset: 1)
                        }
                        reviewActionChip(icon: "archivebox.fill", tint: ThingsMacPalette.somedayAmber, label: store.tr("view_someday")) {
                            scheduleTask(task, to: .someday, daysOffset: nil)
                        }
                        Spacer()
                        Button {
                            store.toggleTaskCompletion(task)
                        } label: {
                            Label(store.tr("action_complete"), systemImage: "checkmark")
                                .font(.system(size: 11.5, weight: .medium))
                        }
                        .controlSize(.small)

                        Button(role: .destructive) {
                            store.deleteTasksWithUndo([task.id])
                        } label: {
                            Image(systemName: "trash")
                                .font(.system(size: 11.5))
                                .foregroundStyle(ThingsMacPalette.upcomingRed)
                        }
                        .buttonStyle(.borderless)
                        .help(store.tr("action_delete"))
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
        if items.isEmpty {
            calmEmptyState(icon: "star.circle.fill", color: ThingsMacPalette.todayYellow, message: store.tr("empty_today"))
        } else {
            ForEach(items) { task in
                HStack(spacing: 10) {
                    ThingsCheckboxButton(
                        status: task.status,
                        isSomeday: task.scheduleState == .someday,
                        helpText: store.tr("macos_checkbox_help"),
                        onToggleComplete: { store.toggleTaskCompletion(task) },
                        onCancelTask: { store.cancelTask(task) }
                    )

                    VStack(alignment: .leading, spacing: 3) {
                        Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                            .font(.system(size: 13.5, weight: .medium))
                        HStack(spacing: 8) {
                            if let proj = store.project(byId: task.projectId) {
                                Text(proj.title)
                                    .font(.system(size: 11))
                                    .foregroundStyle(.secondary)
                            }
                            if let dlMillis = task.deadline {
                                deadlinePill(dlMillis)
                            }
                        }
                    }

                    Spacer()

                    HStack(spacing: 6) {
                        reviewActionChip(icon: "star.fill", tint: ThingsMacPalette.todayYellow, label: store.tr("view_today")) {
                            scheduleTask(task, to: .today, daysOffset: 0)
                        }
                        reviewActionChip(icon: "sun.horizon.fill", tint: ThingsMacPalette.upcomingRed, label: store.tr("date_tomorrow")) {
                            scheduleTask(task, to: .upcoming, daysOffset: 1)
                        }
                        reviewActionChip(icon: "calendar.badge.plus", tint: ThingsMacPalette.accentBlue, label: store.tr("macos_deadline_plus_7d")) {
                            scheduleTask(task, to: .upcoming, daysOffset: 7)
                        }
                        Button {
                            store.toggleTaskCompletion(task)
                        } label: {
                            Image(systemName: "checkmark.circle")
                                .font(.system(size: 13))
                        }
                        .buttonStyle(.borderless)
                        .help(store.tr("action_complete"))
                    }
                }
                .padding(12)
                .thingsCardSurface(cornerRadius: 10)
            }
        }
    }

    // MARK: - Step 3: Stalled Projects

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
                            submitNextAction(for: project.id)
                        }

                        Button(store.tr("macos_action_add")) {
                            submitNextAction(for: project.id)
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                        .disabled((nextActionDrafts[project.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                        Button(store.tr("v2_mark_reviewed")) {
                            store.markProjectReviewed(project.id)
                        }
                        .controlSize(.small)

                        Button(store.tr("view_someday")) {
                            store.moveProjectToSomeday(project)
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
            calmEmptyState(icon: "archivebox.circle.fill", color: ThingsMacPalette.somedayAmber, message: store.tr("empty_view"))
        } else {
            ForEach(store.somedayTasks) { task in
                HStack(spacing: 10) {
                    ThingsCheckboxButton(
                        status: task.status,
                        isSomeday: true,
                        helpText: store.tr("macos_checkbox_help"),
                        onToggleComplete: { store.toggleTaskCompletion(task) },
                        onCancelTask: { store.cancelTask(task) }
                    )

                    Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                        .font(.system(size: 13.5, weight: .medium))

                    Spacer()

                    reviewActionChip(icon: "star.fill", tint: ThingsMacPalette.todayYellow, label: store.tr("view_today")) {
                        scheduleTask(task, to: .today, daysOffset: 0)
                    }
                    reviewActionChip(icon: "square.stack.3d.up.fill", tint: ThingsMacPalette.anytimeTeal, label: store.tr("view_anytime")) {
                        scheduleTask(task, to: .anytime, daysOffset: nil)
                    }
                    Button(role: .destructive) {
                        store.deleteTasksWithUndo([task.id])
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 11.5))
                            .foregroundStyle(ThingsMacPalette.upcomingRed)
                    }
                    .buttonStyle(.borderless)
                    .help(store.tr("action_delete"))
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
                Label(store.tr("v2_complete_weekly_review"), systemImage: "checkmark.seal.fill")
                    .font(.system(size: 13, weight: .semibold))
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    // MARK: - Helpers

    private func submitNextAction(for projectId: String) {
        let raw = nextActionDrafts[projectId] ?? ""
        guard store.addNextActionToProject(projectId, title: raw) != nil else { return }
        nextActionDrafts[projectId] = ""
    }

    private func scheduleTask(_ task: TaskItem, to state: ScheduleState, daysOffset: Int?) {
        var copy = task
        copy.scheduleState = state
        if let daysOffset {
            let base = Calendar.current.startOfDay(for: Date())
            let target = Calendar.current.date(byAdding: .day, value: daysOffset, to: base) ?? base
            copy.whenDate = DateEpochConverter.startOfDayMillis(for: target)
        } else {
            copy.whenDate = nil
        }
        store.updateTask(copy)
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
        }
        .foregroundStyle(tint)
        .padding(.horizontal, 7)
        .padding(.vertical, 2)
        .background(Capsule().fill(tint.opacity(0.12)))
    }

    private func reasonPill(_ label: String, color: Color) -> some View {
        Text(label)
            .font(.system(size: 10.5, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 2.5)
            .background(Capsule().fill(color.opacity(0.13)))
    }

    private func reviewActionChip(
        icon: String,
        tint: Color,
        label: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(tint)
                Text(label)
                    .font(.system(size: 11.5, weight: .medium))
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                Capsule(style: .continuous)
                    .fill(Color.primary.opacity(0.06))
            )
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
