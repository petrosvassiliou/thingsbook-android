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
    @State private var brainDumpDrafts: [BrainDumpTaskDraft] = []
    @State private var isWorking = false
    @State private var errorMessage: String?

    // Project Architect state
    @State private var architectGoal = ""
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
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                VStack(alignment: .leading, spacing: 1) {
                    Text(store.tr("gemini_sheet_title"))
                        .font(.system(size: 16, weight: .semibold))
                    Text(store.tr("gemini_sheet_subtitle"))
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button {
                    showApiKeyField.toggle()
                } label: {
                    Label("API Key", systemImage: "key.fill")
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
                HStack(spacing: 8) {
                    SecureField("Gemini 2.5 Flash API Key (optional — stored in macOS Keychain)", text: $apiKeyInput)
                        .textFieldStyle(.roundedBorder)
                        .font(.system(size: 12))
                    Button(store.tr("action_save")) {
                        ThingsbookGeminiService.saveApiKey(apiKeyInput)
                        showApiKeyField = false
                        store.showToast("Gemini API Key saved in Keychain")
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                }
            }

            Picker("", selection: $selectedTab) {
                Text(store.tr("gemini_tab_braindump")).tag(GeminiSheetTab.brainDump)
                Text(store.tr("gemini_tab_architect")).tag(GeminiSheetTab.projectArchitect)
                Text(store.tr("gemini_tab_review")).tag(GeminiSheetTab.logbookReview)
            }
            .pickerStyle(.segmented)
            .labelsHidden()

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
        .frame(width: 580, height: 480)
        .onAppear {
            selectedTab = store.geminiInitialTab
            apiKeyInput = ThingsbookGeminiService.storedApiKey()
        }
    }

    // MARK: - Tab 1: Brain Dump -> GTD Tasks

    private var brainDumpTab: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(store.tr("gemini_braindump_hint"))
                .font(.system(size: 12))
                .foregroundStyle(.secondary)

            TextEditor(text: $brainDumpText)
                .font(.system(size: 13))
                .frame(height: 110)
                .padding(4)
                .background(RoundedRectangle(cornerRadius: 8).strokeBorder(Color.primary.opacity(0.14)))

            HStack {
                Button {
                    runBrainDump()
                } label: {
                    HStack(spacing: 6) {
                        if isWorking { ProgressView().controlSize(.small) }
                        Text(store.tr("gemini_braindump_extract_btn"))
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(brainDumpText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isWorking)

                Spacer()

                if !brainDumpDrafts.isEmpty {
                    Button(store.trf("gemini_import_count_btn", brainDumpDrafts.count)) {
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
                                Text(draft.scheduleState.rawValue)
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
                TextField(store.tr("gemini_architect_hint"), text: $architectGoal)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit(runArchitect)

                Button {
                    runArchitect()
                } label: {
                    if isWorking {
                        ProgressView().controlSize(.small)
                    } else {
                        Text(store.tr("gemini_architect_generate_btn"))
                    }
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
                            .padding(8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .thingsCardSurface(cornerRadius: 8)
                        }
                    }
                }

                HStack {
                    Spacer()
                    Button(store.tr("gemini_architect_create_project_btn")) {
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
                HStack(spacing: 6) {
                    if isWorking { ProgressView().controlSize(.small) }
                    Text(store.tr("gemini_logbook_review_btn"))
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isWorking)

            if let ref = reflection {
                VStack(alignment: .leading, spacing: 10) {
                    Text(ref.summaryParagraph)
                        .font(.system(size: 13.5))
                        .padding(12)
                        .thingsCardSurface(cornerRadius: 10)

                    Text("Suggested Today Focus:")
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(.secondary)

                    ForEach(ref.suggestedFocusTasks, id: \.self) { suggestion in
                        HStack {
                            Text(suggestion)
                                .font(.system(size: 13))
                            Spacer()
                            Button("+ Today") {
                                let todayEpoch = DateEpochConverter.startOfDayMillis(for: Date())
                                _ = try? store.database.createTask(
                                    title: suggestion,
                                    scheduleState: .today,
                                    whenDate: todayEpoch
                                )
                                store.showToast("Added to Today")
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                        }
                        .padding(8)
                        .thingsCardSurface(cornerRadius: 8)
                    }
                }
            }
            Spacer()
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
            _ = try? store.database.createTask(
                title: d.title,
                notes: d.notes,
                scheduleState: d.scheduleState,
                whenDate: whenMillis,
                deadline: deadMillis,
                checklistTitles: d.checklist
            )
        }
        store.selectedDestination = .smartView(.today)
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
                    headingId: createdH?.id
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
