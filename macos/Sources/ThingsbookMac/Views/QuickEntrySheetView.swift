import SwiftUI
import AppKit
import ThingsbookCore

public struct QuickEntrySheetView: View {
    @ObservedObject var store: ThingsbookStore
    public var isMenuBarPopover: Bool = false
    public var onSaved: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openWindow) private var openWindow

    @StateObject private var voiceController = MacVoiceDictationController()
    @State private var title: String = ""
    @State private var notes: String = ""
    @State private var selectedDestKey: String = "inbox"
    @State private var hasDeadline: Bool = false
    @State private var deadlineDate: Date = Date()
    @State private var isSmartParsing: Bool = false
    @State private var menuBarTab: String = "today"
    @FocusState private var titleFocused: Bool

    public init(store: ThingsbookStore, isMenuBarPopover: Bool = false, onSaved: (() -> Void)? = nil) {
        self.store = store
        self.isMenuBarPopover = isMenuBarPopover
        self.onSaved = onSaved
    }

    private var activeProjects: [Project] {
        store.projects.filter { $0.status == .active }.sorted { $0.sortOrder < $1.sortOrder }
    }

    private var resolvedDestination: QuickShareDestination {
        if selectedDestKey == "today" {
            return .today
        } else if selectedDestKey == "evening" {
            return .thisEvening
        } else if selectedDestKey == "anytime" {
            return .anytime
        } else if selectedDestKey == "someday" {
            return .someday
        } else if selectedDestKey.hasPrefix("area:") {
            return .area(String(selectedDestKey.dropFirst(5)))
        } else if selectedDestKey.hasPrefix("proj:") {
            return .project(String(selectedDestKey.dropFirst(5)))
        }
        return .inbox
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                if let appIcon = ThingsResourceLoader.appIconImage() {
                    Image(nsImage: appIcon)
                        .resizable()
                        .interpolation(.high)
                        .frame(width: 24, height: 24)
                } else {
                    Image(systemName: "book.pages.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                }
                Text(store.tr("topbar_tooltip_quick_entry"))
                    .font(.system(size: 14.5, weight: .bold))
                Spacer()
                if isMenuBarPopover {
                    HStack(spacing: 4) {
                        Image(systemName: "sun.max.fill")
                            .font(.system(size: 10.5, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.todayYellow)
                        Text("\(store.todayTotalBadgeCount) · \(store.tr("view_today"))")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 3.5)
                    .background(
                        Capsule(style: .continuous)
                            .fill(ThingsMacPalette.accentBlue.opacity(0.13))
                    )
                }
            }

            // Title input + 1-Tap Voice Dictation Button
            HStack(spacing: 8) {
                TextField(store.tr("placeholder_todo_title"), text: $title)
                    .textFieldStyle(.roundedBorder)
                    .font(.system(size: 13.5, weight: .medium))
                    .focused($titleFocused)
                    .onSubmit(saveQuickTask)

                Button {
                    toggleDictation()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: voiceController.isRecording ? "waveform.circle.fill" : "mic.fill")
                            .font(.system(size: 13, weight: .semibold))
                        if !isMenuBarPopover {
                            Text(voiceController.isRecording ? store.tr("macos_voice_stop") : store.tr("voice_capture_btn"))
                                .font(.system(size: 11.5, weight: .semibold))
                        }
                    }
                    .foregroundStyle(voiceController.isRecording ? .white : ThingsMacPalette.accentBlue)
                    .padding(.horizontal, isMenuBarPopover ? 8 : 10)
                    .padding(.vertical, 5)
                    .background(
                        Capsule(style: .continuous)
                            .fill(voiceController.isRecording ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue.opacity(0.12))
                    )
                }
                .buttonStyle(.plain)
                .thingsTooltip(voiceController.isRecording ? store.tr("macos_voice_stop") : store.tr("voice_capture_prompt"))
            }

            if voiceController.isRecording {
                HStack(spacing: 6) {
                    Circle()
                        .fill(ThingsMacPalette.deadlineCrimson)
                        .frame(width: 7, height: 7)
                    Text(store.tr("macos_voice_listening"))
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                    Spacer()
                }
            }

            ZStack(alignment: .topLeading) {
                if notes.isEmpty {
                    Text(store.tr("placeholder_notes"))
                        .font(.system(size: 12.5))
                        .foregroundStyle(.tertiary)
                        .padding(.top, 6)
                        .padding(.leading, 6)
                        .allowsHitTesting(false)
                }
                TextEditor(text: $notes)
                    .font(.system(size: 12.5))
                    .frame(height: 68)
                    .padding(3)
                    .background(RoundedRectangle(cornerRadius: 8, style: .continuous).strokeBorder(Color.primary.opacity(0.12)))
            }

            // 1-Tap Gemini Smart Parse Pill when spoken/typed brain dump is present
            if !combinedCaptureText.isEmpty {
                HStack {
                    Button {
                        runSmartParse()
                    } label: {
                        HStack(spacing: 5) {
                            if isSmartParsing {
                                ProgressView().controlSize(.mini)
                            } else {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 11, weight: .semibold))
                            }
                            Text(store.tr("voice_smart_parse_gemini"))
                                .font(.system(size: 11.5, weight: .semibold))
                        }
                        .foregroundStyle(ThingsMacPalette.geminiPurple)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4.5)
                        .background(
                            Capsule(style: .continuous)
                                .fill(ThingsMacPalette.geminiPurple.opacity(0.13))
                        )
                    }
                    .buttonStyle(.plain)
                    .fixedSize(horizontal: true, vertical: false)
                    .disabled(isSmartParsing)
                    .thingsTooltip(store.tr("gemini_brain_dump_action"))

                    Spacer()
                }
            }

            HStack(spacing: 10) {
                Picker(store.tr("quick_share_destination_label"), selection: $selectedDestKey) {
                    Text(store.tr("view_inbox")).tag("inbox")
                    Text(store.tr("view_today")).tag("today")
                    Text(store.tr("view_this_evening")).tag("evening")
                    Text(store.tr("view_anytime")).tag("anytime")
                    Text(store.tr("view_someday")).tag("someday")
                    if !store.areas.isEmpty {
                        Divider()
                        ForEach(store.areas) { area in
                            Text("▣ \(area.title)").tag("area:\(area.id)")
                        }
                    }
                    if !activeProjects.isEmpty {
                        Divider()
                        ForEach(activeProjects) { proj in
                            Text("📁 \(proj.title)").tag("proj:\(proj.id)")
                        }
                    }
                }
                .labelsHidden()
                .frame(maxWidth: 195)

                Toggle(store.tr("action_deadline"), isOn: $hasDeadline)
                    .toggleStyle(.checkbox)
                    .font(.system(size: 12))

                if hasDeadline {
                    DatePicker("", selection: $deadlineDate, displayedComponents: [.date])
                        .environment(\.locale, store.preferences.language.locale)
                        .labelsHidden()
                        .frame(width: 105)
                }
            }

            if isMenuBarPopover {
                menuBarQuickListSection
            } else {
                Text(store.tr("macos_quick_entry_shortcut_hint"))
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }

            Divider()

            HStack {
                if !isMenuBarPopover {
                    Button(store.tr("action_cancel")) {
                        voiceController.stopRecording()
                        dismiss()
                    }
                } else {
                    Button(store.tr("macos_menubar_open_app")) {
                        openWindow(id: "main")
                        NSApplication.shared.activate(ignoringOtherApps: true)
                    }
                    .font(.system(size: 12))

                    Button(store.tr("macos_menubar_quit")) {
                        NSApplication.shared.terminate(nil)
                    }
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                }

                Spacer()

                Button(store.tr("action_save")) {
                    saveQuickTask()
                }
                .buttonStyle(.borderedProminent)
                .tint(ThingsMacPalette.accentBlue)
                .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSmartParsing)
            }
        }
        .padding(16)
        .frame(width: isMenuBarPopover ? 386 : 460)
        .onAppear {
            if !isMenuBarPopover {
                title = store.quickEntryPrefillTitle
                notes = store.quickEntryPrefillNotes
                switch store.selectedDestination {
                case .project(let pid) where activeProjects.contains(where: { $0.id == pid }):
                    selectedDestKey = "proj:\(pid)"
                case .area(let aid) where store.areas.contains(where: { $0.id == aid }):
                    selectedDestKey = "area:\(aid)"
                case .smartView(.today):
                    selectedDestKey = "today"
                case .smartView(.anytime):
                    selectedDestKey = "anytime"
                case .smartView(.someday):
                    selectedDestKey = "someday"
                default:
                    selectedDestKey = "inbox"
                }
                if store.quickEntryAutoStartVoice {
                    store.quickEntryAutoStartVoice = false
                    toggleDictation()
                }
            } else if let savedProj = store.preferences.menuBarSelectedProjectId,
                      activeProjects.contains(where: { $0.id == savedProj }) {
                menuBarTab = "project"
                selectedDestKey = "proj:\(savedProj)"
            }
            titleFocused = true
        }
        .onDisappear {
            voiceController.stopRecording()
        }
    }

    @ViewBuilder
    private var menuBarQuickListSection: some View {
        Divider()
        HStack(spacing: 6) {
            menuBarBentoTabButton(
                id: "today",
                icon: "sun.max.fill",
                iconColor: ThingsMacPalette.todayYellow,
                label: store.tr("view_today")
            ) {
                menuBarTab = "today"
                store.preferences.menuBarSelectedProjectId = nil
            }
            menuBarBentoTabButton(
                id: "project",
                icon: "folder.fill",
                iconColor: ThingsMacPalette.accentBlue,
                label: store.tr("widget_pin_project_tab")
            ) {
                menuBarTab = "project"
                if store.preferences.menuBarSelectedProjectId == nil, let first = activeProjects.first {
                    store.preferences.menuBarSelectedProjectId = first.id
                }
            }
        }
        .padding(3)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color.primary.opacity(0.05))
        )

        if menuBarTab == "project" {
            if activeProjects.isEmpty {
                Text(store.tr("empty_view"))
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
            } else {
                let currentProjId = Binding<String>(
                    get: {
                        if let saved = store.preferences.menuBarSelectedProjectId,
                           activeProjects.contains(where: { $0.id == saved }) {
                            return saved
                        }
                        return activeProjects.first?.id ?? ""
                    },
                    set: { newId in
                        store.preferences.menuBarSelectedProjectId = newId
                        selectedDestKey = "proj:\(newId)"
                    }
                )

                Picker(store.tr("entity_project"), selection: currentProjId) {
                    ForEach(activeProjects) { proj in
                        Text("📁 \(proj.title)").tag(proj.id)
                    }
                }
                .labelsHidden()

                let projTasks = store.tasksForProject(currentProjId.wrappedValue)
                if projTasks.isEmpty {
                    Text(store.tr("empty_view"))
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)
                } else {
                    VStack(spacing: 4) {
                        ForEach(projTasks.prefix(5)) { task in
                            menuBarTaskRow(task)
                        }
                    }
                }
            }
        } else {
            let todayCombined = store.todayDaytimeTasks + store.thisEveningTasks
            if todayCombined.isEmpty {
                Text(store.tr("empty_today"))
                    .font(.system(size: 11.5))
                    .foregroundStyle(.secondary)
            } else {
                VStack(spacing: 4) {
                    ForEach(todayCombined.prefix(5)) { task in
                        menuBarTaskRow(task)
                    }
                }
            }
        }
    }

    private func menuBarBentoTabButton(
        id: String,
        icon: String,
        iconColor: Color,
        label: String,
        action: @escaping () -> Void
    ) -> some View {
        let isActive = menuBarTab == id
        return Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: icon)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(isActive ? iconColor : .secondary)
                Text(label)
                    .font(.system(size: 11.5, weight: isActive ? .semibold : .medium))
                    .foregroundStyle(isActive ? .primary : .secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 5)
            .background(
                RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                    .fill(isActive ? ThingsMacPalette.accentBlue.opacity(0.14) : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                    .strokeBorder(isActive ? ThingsMacPalette.accentBlue.opacity(0.30) : Color.clear, lineWidth: 0.9)
            )
        }
        .buttonStyle(.plain)
    }

    private func menuBarTaskRow(_ task: TaskItem) -> some View {
        HStack(spacing: 8) {
            ThingsCheckboxButton(
                status: task.status,
                isMilestone: task.isMilestone,
                helpText: store.tr("macos_checkbox_help"),
                onToggleComplete: { store.toggleTaskCompletion(task) },
                onCancelTask: { store.cancelTask(task) }
            )
            Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                .font(.system(size: 12.5, weight: .medium))
                .lineLimit(1)
            Spacer()
            if let firstTag = task.tags.first {
                Text(firstTag.title)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 1.5)
                    .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.12)))
            }
            if !task.attachments.isEmpty {
                Image(systemName: "paperclip")
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 5)
        .background(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(Color.primary.opacity(0.035))
        )
    }

    private var combinedCaptureText: String {
        let t = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let n = notes.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return n }
        if n.isEmpty { return t }
        return "\(t)\n\(n)"
    }

    private func toggleDictation() {
        voiceController.toggleRecording(
            locale: store.preferences.language.locale,
            onTranscript: { spoken in
                let lines = spoken.components(separatedBy: .newlines)
                title = String((lines.first ?? spoken).prefix(240))
                if lines.count > 1 {
                    notes = lines.dropFirst().joined(separator: "\n")
                }
            },
            onUnavailable: {
                store.showToast(store.tr("voice_not_available"))
            }
        )
    }

    private func runSmartParse() {
        voiceController.stopRecording()
        let dump = combinedCaptureText
        guard !dump.isEmpty else { return }
        isSmartParsing = true
        let dest = resolvedDestination
        Task {
            let count = await store.smartParseVoiceDump(rawText: dump, defaultDestination: dest)
            await MainActor.run {
                isSmartParsing = false
                if count > 0 {
                    title = ""
                    notes = ""
                    hasDeadline = false
                    deadlineDate = Date()
                    store.quickEntryPrefillTitle = ""
                    store.quickEntryPrefillNotes = ""
                    onSaved?()
                    if !isMenuBarPopover {
                        dismiss()
                    }
                }
            }
        }
    }

    private func saveQuickTask() {
        voiceController.stopRecording()
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty else { return }

        let dl = hasDeadline ? DateEpochConverter.startOfDayMillis(for: deadlineDate) : nil
        store.quickCaptureTask(title: cleanTitle, notes: notes, destination: resolvedDestination, deadline: dl)
        title = ""
        notes = ""
        hasDeadline = false
        deadlineDate = Date()
        store.quickEntryPrefillTitle = ""
        store.quickEntryPrefillNotes = ""
        onSaved?()
        if !isMenuBarPopover {
            dismiss()
        }
    }
}
