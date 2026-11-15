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

                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
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
            }
        }
        .padding(20)
        .frame(width: 600, height: 500)
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

    // MARK: - Tab 2: AI Project Architect

    private var projectArchitectTab: some View {
        VStack(alignment: .leading, spacing: 10) {
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
                            VStack(alignment: .leading, spacing: 4) {
                                Text(h.title)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(ThingsMacPalette.accentBlue)
                                ForEach(h.tasks, id: \.self) { t in
                                    Text("• \(t)")
                                        .font(.system(size: 12.5))
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
        guard let proj = try? store.database.createProject(
            title: bp.projectTitle,
            notes: bp.projectNotes,
            areaId: architectAreaId,
            scheduleState: .today,
            whenDate: DateEpochConverter.startOfDayMillis(for: Date())
        ) else { return }

        for h in bp.headings {
            let createdH = try? store.database.createHeading(projectId: proj.id, title: h.title)
            for t in h.tasks {
                _ = try? store.database.createTask(
                    title: t,
                    scheduleState: .anytime,
                    projectId: proj.id,
                    headingId: createdH?.id,
                    areaId: architectAreaId
                )
            }
        }
        store.selectedDestination = .project(proj.id)
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
}
