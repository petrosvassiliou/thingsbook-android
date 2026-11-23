import SwiftUI
import ThingsbookCore

public struct GeminiAssistantSheet: View {
    @ObservedObject var store: ThingsbookStore
    @Environment(\.dismiss) private var dismiss

    @State private var selectedTab: GeminiSheetTab = .brainDump
    @State private var apiKeyInput: String = ""
    @State private var showApiKeyField = false

    // Brain Dump state
    @State private var brainDumpText = ""
    @State private var brainDumpAreaId: String? = nil
    @State private var brainDumpDrafts: [BrainDumpTaskDraft] = []
    @State private var isWorking = false
    @State private var errorMessage: String?

    // Project Architect state
    @State private var architectGoal = ""
    @State private var architectAreaId: String? = nil
    @State private var blueprint: ProjectBlueprintResponse? = nil

    // Logbook Reflection state
    @State private var reflection: LogbookReflectionResponse? = nil

    // Plan My Day Auto-Timeblocker state
    @State private var includeScheduledInDayPlan = false
    @State private var dayPlanPreview: DayTimeblockPlanResponse? = nil
    @State private var editablePlanBlocks: [DayTimeblockItemDTO] = []
    @State private var selectedPlanTaskIds: Set<String> = []

    public init(store: ThingsbookStore) {
        self.store = store
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Image(systemName: "sparkles")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.geminiPurple)
                VStack(alignment: .leading, spacing: 1) {
                    Text(store.tr("gemini_hub_title"))
                        .font(.system(size: 16, weight: .semibold))
                    Text(store.tr("macos_gemini_subtitle"))
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button {
                    showApiKeyField.toggle()
                } label: {
                    Label(store.tr("macos_gemini_api_key_btn"), systemImage: "key.fill")
                        .font(.system(size: 11.5))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .thingsTooltip(store.tr("gemini_api_key_label"))

                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .thingsTooltip("\(store.tr("action_done")) (Esc)")
            }

            if showApiKeyField {
                VStack(alignment: .leading, spacing: 4) {
                    Text(store.tr("gemini_api_key_label"))
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                    HStack(spacing: 8) {
                        SecureField(store.tr("gemini_api_key_placeholder"), text: $apiKeyInput)
                            .textFieldStyle(.roundedBorder)
                            .font(.system(size: 12))
                        Button(store.tr("action_save")) {
                            ThingsbookGeminiService.saveApiKey(apiKeyInput)
                            showApiKeyField = false
                            store.showToast(store.tr("macos_gemini_key_saved"))
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                    }
                }
                .padding(10)
                .thingsCardSurface(cornerRadius: 9)
            }

            Picker("", selection: $selectedTab) {
                Text(store.tr("gemini_brain_dump_title")).tag(GeminiSheetTab.brainDump)
                Text(store.tr("gemini_project_architect_title")).tag(GeminiSheetTab.projectArchitect)
                Text(store.tr("gemini_logbook_review_title")).tag(GeminiSheetTab.logbookReview)
                Text(store.tr("v31_gemini_plan_day_tab")).tag(GeminiSheetTab.planMyDay)
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            if isWorking {
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    Text(store.tr("gemini_generating"))
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.system(size: 12))
                    .foregroundStyle(.red)
            }

            switch selectedTab {
            case .brainDump:
                brainDumpTab
            case .projectArchitect:
                projectArchitectTab
            case .logbookReview:
                logbookReviewTab
            case .planMyDay:
                planMyDayTab
            }
        }
        .padding(20)
        .frame(width: 660, height: 540)
        .background(ThingsMacPalette.canvasBackground)
        .onAppear {
            selectedTab = store.geminiInitialTab
            apiKeyInput = ThingsbookGeminiService.storedApiKey()
            if case .area(let aid) = store.selectedDestination {
                brainDumpAreaId = aid
                architectAreaId = aid
            }
        }
    }

    // MARK: - Tab 1: Brain Dump -> GTD Tasks

    private var brainDumpTab: some View {
        VStack(alignment: .leading, spacing: 10) {
            ZStack(alignment: .topLeading) {
                if brainDumpText.isEmpty {
                    Text(store.tr("gemini_brain_dump_placeholder"))
                        .font(.system(size: 12.5))
                        .foregroundStyle(.tertiary)
                        .padding(8)
                        .allowsHitTesting(false)
                }
                TextEditor(text: $brainDumpText)
                    .font(.system(size: 13))
                    .frame(height: 110)
                    .padding(4)
                    .background(RoundedRectangle(cornerRadius: 8).strokeBorder(Color.primary.opacity(0.14)))
            }

            HStack(spacing: 10) {
                Picker(store.tr("entity_area"), selection: $brainDumpAreaId) {
                    Text(store.tr("areas_none")).tag(String?.none)
                    ForEach(store.areas) { area in
                        Text(area.title).tag(Optional(area.id))
                    }
                }
                .frame(maxWidth: 190)

                Button {
                    runBrainDump()
                } label: {
                    Label(store.tr("gemini_brain_dump_action"), systemImage: "sparkles")
                }
                .buttonStyle(.borderedProminent)
                .disabled(brainDumpText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isWorking)

                Spacer()

                if !brainDumpDrafts.isEmpty {
                    Button(store.trf("macos_gemini_import_count", brainDumpDrafts.count)) {
                        importBrainDumpDrafts()
                    }
                    .buttonStyle(.borderedProminent)
                }
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(brainDumpDrafts) { draft in
                        HStack {
                            Image(systemName: "circle")
                                .foregroundStyle(ThingsMacPalette.accentBlue)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(draft.title)
                                    .font(.system(size: 13, weight: .medium))
                                Text(localizedScheduleLabel(draft.scheduleState))
                                    .font(.system(size: 10.5))
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                        }
                        .padding(8)
                        .thingsCardSurface(cornerRadius: 8)
                    }
                }
            }
        }
    }

    // MARK: - Tab 2: v3-Aware AI Roadmap Architect

    private var projectArchitectTab: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(store.tr("v31_gemini_architect_v3_hint"))
                .font(.system(size: 11.5))
                .foregroundStyle(.secondary)

            HStack(spacing: 8) {
                TextField(store.tr("gemini_project_architect_placeholder"), text: $architectGoal)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit(runArchitect)

                Picker("", selection: $architectAreaId) {
                    Text(store.tr("areas_none")).tag(String?.none)
                    ForEach(store.areas) { area in
                        Text(area.title).tag(Optional(area.id))
                    }
                }
                .labelsHidden()
                .frame(width: 120)

                Button {
                    runArchitect()
                } label: {
                    Text(store.tr("gemini_project_architect_action"))
                }
                .buttonStyle(.borderedProminent)
                .disabled(architectGoal.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isWorking)
            }

            if let bp = blueprint {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(bp.projectTitle)
                            .font(.system(size: 16, weight: .bold))
                        if !bp.projectNotes.isEmpty {
                            Text(bp.projectNotes)
                                .font(.system(size: 12.5))
                                .foregroundStyle(.secondary)
                        }
                        ForEach(bp.headings) { h in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack(spacing: 8) {
                                    Text(h.title)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(ThingsMacPalette.accentBlue)
                                    Spacer()
                                    if let s = h.startDayOffset, let e = h.endDayOffset {
                                        Text("D+\(s) – D+\(e)")
                                            .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                                            .foregroundStyle(ThingsMacPalette.accentBlue)
                                            .padding(.horizontal, 7)
                                            .padding(.vertical, 2)
                                            .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.11)))
                                    }
                                }

                                if !h.richTasks.isEmpty {
                                    ForEach(h.richTasks) { rt in
                                        architectRichTaskPreviewRow(rt)
                                    }
                                } else {
                                    ForEach(h.tasks, id: \.self) { t in
                                        Text("• \(t)")
                                            .font(.system(size: 12.5))
                                    }
                                }
                            }
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .thingsCardSurface(cornerRadius: 8)
                        }
                    }
                }

                HStack {
                    Spacer()
                    Button(store.tr("macos_gemini_create_project")) {
                        createProjectFromBlueprint(bp)
                    }
                    .buttonStyle(.borderedProminent)
                }
            } else {
                Spacer()
            }
        }
    }

    private func architectRichTaskPreviewRow(_ rt: RoadmapTaskBlueprintDTO) -> some View {
        let effortEnum = TaskEffortLevel.normalized(rt.effortLevel)
        return HStack(alignment: .center, spacing: 6) {
            if rt.isMilestone {
                Text("◆")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(ThingsMacPalette.milestoneViolet)
            } else {
                Text("•")
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(.secondary)
            }

            Text(rt.title)
                .font(.system(size: 12.5, weight: rt.isMilestone ? .semibold : .regular))
                .foregroundStyle(rt.isMilestone ? ThingsMacPalette.milestoneViolet : .primary)
                .lineLimit(1)

            Spacer(minLength: 4)

            if !rt.dependsOnKeys.isEmpty {
                Text("↳ \(rt.dependsOnKeys.count)")
                    .font(.system(size: 10, weight: .semibold, design: .rounded))
                    .foregroundStyle(ThingsMacPalette.somedayAmber)
                    .padding(.horizontal, 5.5)
                    .padding(.vertical, 1.5)
                    .background(Capsule().fill(ThingsMacPalette.somedayAmber.opacity(0.14)))
            }

            if let effortEnum {
                Text(localizedEffortTitle(effortEnum))
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 1.5)
                    .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.11)))
            }

            Text(TaskItem.formatCompactDuration(rt.durationMinutes))
                .font(.system(size: 10, weight: .semibold, design: .rounded))
                .foregroundStyle(ThingsMacPalette.anytimeTeal)
                .padding(.horizontal, 6)
                .padding(.vertical, 1.5)
                .background(Capsule().fill(ThingsMacPalette.anytimeTeal.opacity(0.12)))
        }
    }

    private func localizedEffortTitle(_ effort: TaskEffortLevel) -> String {
        switch effort {
        case .quickWin: return store.tr("v30_effort_quick_win")
        case .medium: return store.tr("v30_effort_medium")
        case .deepFocus: return store.tr("v30_effort_deep_focus")
        }
    }

    // MARK: - Tab 3: Logbook AI Reflection

    private var logbookReviewTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                runLogbookReflection()
            } label: {
                Label(store.tr("gemini_logbook_review_action"), systemImage: "sparkles")
            }
            .buttonStyle(.borderedProminent)
            .disabled(isWorking)

            if let ref = reflection {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(ref.summaryParagraph)
                            .font(.system(size: 13.5))
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .thingsCardSurface(cornerRadius: 10)

                        Text(store.tr("macos_gemini_suggested_focus"))
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(.secondary)

                        ForEach(ref.suggestedFocusTasks, id: \.self) { suggestion in
                            HStack {
                                Text(suggestion)
                                    .font(.system(size: 13))
                                Spacer()
                                Button("+ \(store.tr("view_today"))") {
                                    let todayEpoch = DateEpochConverter.startOfDayMillis(for: Date())
                                    _ = try? store.database.createTask(
                                        title: suggestion,
                                        scheduleState: .today,
                                        whenDate: todayEpoch
                                    )
                                    store.showToast(store.tr("macos_gemini_added_today"))
                                }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                            }
                            .padding(8)
                            .thingsCardSurface(cornerRadius: 8)
                        }
                    }
                }
            }
            Spacer()
        }
    }

    // MARK: - Tab 4: "Plan My Day" Auto-Timeblocker

    private var planMyDayTab: some View {
        let todayTasksById = Dictionary(
            uniqueKeysWithValues: (store.todayDaytimeTasks + store.thisEveningTasks + store.allOpenTasks).map { ($0.id, $0) }
        )
        let chosenBlocks = editablePlanBlocks.filter { selectedPlanTaskIds.contains($0.taskId) }

        return VStack(alignment: .leading, spacing: 10) {
            Text(store.tr("v31_gemini_plan_day_desc"))
                .font(.system(size: 12))
                .foregroundStyle(.secondary)

            HStack(spacing: 12) {
                Toggle(store.tr("v31_gemini_plan_day_replan"), isOn: $includeScheduledInDayPlan)
                    .font(.system(size: 12.5))

                Spacer()

                Button {
                    runPlanMyDay()
                } label: {
                    Label(store.tr("v31_gemini_plan_day_generate"), systemImage: "sparkles")
                }
                .buttonStyle(.borderedProminent)
                .disabled(isWorking)
            }

            if let preview = dayPlanPreview {
                if editablePlanBlocks.isEmpty {
                    Text(store.tr("v31_gemini_plan_day_empty"))
                        .font(.system(size: 12.5))
                        .foregroundStyle(.secondary)
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .thingsCardSurface(cornerRadius: 10)
                    Spacer()
                } else {
                    if !preview.summary.isEmpty {
                        Text(preview.summary)
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                    }

                    ScrollView {
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(Array(editablePlanBlocks.enumerated()), id: \.element.taskId) { idx, block in
                                let isChecked = selectedPlanTaskIds.contains(block.taskId)
                                let rawTitle = todayTasksById[block.taskId]?.title ?? block.taskId
                                let taskTitle = rawTitle.isEmpty ? store.tr("macos_untitled_todo") : rawTitle
                                let startStr = TaskItem.formatMinutesOfDay(block.startMinutes)
                                let endMins = min(1440, block.startMinutes + block.durationMinutes)
                                let endStr = endMins >= 1440 ? "24:00" : TaskItem.formatMinutesOfDay(endMins)

                                HStack(spacing: 10) {
                                    Toggle(
                                        "",
                                        isOn: Binding(
                                            get: { isChecked },
                                            set: { checked in
                                                if checked {
                                                    selectedPlanTaskIds.insert(block.taskId)
                                                } else {
                                                    selectedPlanTaskIds.remove(block.taskId)
                                                }
                                            }
                                        )
                                    )
                                    .labelsHidden()

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("\(startStr) – \(endStr) · \(taskTitle)")
                                            .font(.system(size: 12.5, weight: .semibold))
                                            .foregroundStyle(isChecked ? .primary : .secondary)
                                            .lineLimit(1)
                                        if !block.reason.isEmpty {
                                            Text(block.reason)
                                                .font(.system(size: 11))
                                                .foregroundStyle(.secondary)
                                                .lineLimit(1)
                                        }
                                    }

                                    Spacer()

                                    HStack(spacing: 4) {
                                        Button("-15m") {
                                            editablePlanBlocks[idx].startMinutes = max(0, block.startMinutes - 15)
                                        }
                                        .buttonStyle(.bordered)
                                        .controlSize(.mini)

                                        Button("+15m") {
                                            editablePlanBlocks[idx].startMinutes = min(23 * 60 + 45, block.startMinutes + 15)
                                        }
                                        .buttonStyle(.bordered)
                                        .controlSize(.mini)
                                    }
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 8)
                                .opacity(isChecked ? 1.0 : 0.58)
                                .thingsCardSurface(cornerRadius: 9)
                            }
                        }
                    }

                    HStack {
                        Spacer()
                        Button {
                            store.applyGeminiDayPlan(chosenBlocks)
                            dismiss()
                        } label: {
                            Label(
                                store.trf("v31_gemini_plan_day_apply", chosenBlocks.count),
                                systemImage: "checkmark.circle.fill"
                            )
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(isWorking || chosenBlocks.isEmpty)
                    }
                }
            } else {
                Spacer()
            }
        }
    }

    private func localizedScheduleLabel(_ state: ScheduleState) -> String {
        switch state {
        case .inbox: return store.tr("view_inbox")
        case .today: return store.tr("view_today")
        case .thisEvening: return store.tr("view_this_evening")
        case .upcoming: return store.tr("view_upcoming")
        case .anytime: return store.tr("view_anytime")
        case .someday: return store.tr("view_someday")
        }
    }

    // MARK: - Actions

    private func runBrainDump() {
        isWorking = true
        errorMessage = nil
        Task {
            do {
                let res = try await ThingsbookGeminiService.parseBrainDumpToTasks(
                    rawBrainDump: brainDumpText,
                    lang: store.preferences.language
                )
                await MainActor.run {
                    self.brainDumpDrafts = res
                    self.isWorking = false
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.isWorking = false
                }
            }
        }
    }

    private func importBrainDumpDrafts() {
        let sodToday = Calendar.current.startOfDay(for: Date())
        for d in brainDumpDrafts {
            let whenMillis = d.whenDaysOffset.flatMap { offset -> Int64? in
                guard let dt = Calendar.current.date(byAdding: .day, value: offset, to: sodToday) else { return nil }
                return DateEpochConverter.startOfDayMillis(for: dt)
            }
            let deadMillis = d.deadlineDaysOffset.flatMap { offset -> Int64? in
                guard let dt = Calendar.current.date(byAdding: .day, value: offset, to: sodToday) else { return nil }
                return DateEpochConverter.startOfDayMillis(for: dt)
            }
            let resolvedSchedule: ScheduleState = (brainDumpAreaId != nil && d.scheduleState == .inbox)
                ? .anytime
                : d.scheduleState
            _ = try? store.database.createTask(
                title: d.title,
                notes: d.notes,
                scheduleState: resolvedSchedule,
                whenDate: whenMillis,
                deadline: deadMillis,
                areaId: brainDumpAreaId,
                checklistTitles: d.checklist
            )
        }
        if let aid = brainDumpAreaId {
            store.selectedDestination = .area(aid)
        } else {
            store.selectedDestination = .smartView(.today)
        }
        dismiss()
    }

    private func runArchitect() {
        isWorking = true
        errorMessage = nil
        Task {
            do {
                let bp = try await ThingsbookGeminiService.generateProjectBlueprint(
                    goalPrompt: architectGoal,
                    lang: store.preferences.language
                )
                await MainActor.run {
                    self.blueprint = bp
                    self.isWorking = false
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.isWorking = false
                }
            }
        }
    }

    private func createProjectFromBlueprint(_ bp: ProjectBlueprintResponse) {
        guard store.createProjectFromBlueprint(bp, areaId: architectAreaId) != nil else { return }
        dismiss()
    }

    private func runLogbookReflection() {
        isWorking = true
        errorMessage = nil
        let titles = store.logbookTasks.map(\.title)
        Task {
            do {
                let res = try await ThingsbookGeminiService.generateLogbookReflection(
                    completedTaskTitles: titles,
                    lang: store.preferences.language
                )
                await MainActor.run {
                    self.reflection = res
                    self.isWorking = false
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.isWorking = false
                }
            }
        }
    }

    private func runPlanMyDay() {
        isWorking = true
        errorMessage = nil
        let includeScheduled = includeScheduledInDayPlan
        Task {
            do {
                let plan = try await store.generateGeminiDayPlan(includeAlreadyScheduled: includeScheduled)
                await MainActor.run {
                    self.dayPlanPreview = plan
                    self.editablePlanBlocks = plan.blocks
                    self.selectedPlanTaskIds = Set(plan.blocks.map(\.taskId))
                    self.isWorking = false
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.isWorking = false
                }
            }
        }
    }
}
