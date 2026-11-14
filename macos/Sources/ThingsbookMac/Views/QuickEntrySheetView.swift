import SwiftUI
import ThingsbookCore

public struct QuickEntrySheetView: View {
    @ObservedObject var store: ThingsbookStore
    public var isMenuBarPopover: Bool = false
    public var onSaved: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    @State private var title: String = ""
    @State private var notes: String = ""
    @State private var selectedDestKey: String = "inbox"
    @State private var hasDeadline: Bool = false
    @State private var deadlineDate: Date = Date()
    @FocusState private var titleFocused: Bool

    public init(store: ThingsbookStore, isMenuBarPopover: Bool = false, onSaved: (() -> Void)? = nil) {
        self.store = store
        self.isMenuBarPopover = isMenuBarPopover
        self.onSaved = onSaved
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                Text(store.tr("quick_share_sheet_title"))
                    .font(.system(size: 14.5, weight: .semibold))
                Spacer()
                if isMenuBarPopover {
                    Text("\(store.todayTotalBadgeCount) Today")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.secondary)
                }
            }

            TextField(store.tr("quick_share_title_hint"), text: $title)
                .textFieldStyle(.roundedBorder)
                .font(.system(size: 13.5, weight: .medium))
                .focused($titleFocused)
                .onSubmit(saveQuickTask)

            ZStack(alignment: .topLeading) {
                if notes.isEmpty {
                    Text(store.tr("quick_share_notes_hint"))
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
                    .background(RoundedRectangle(cornerRadius: 7).strokeBorder(Color.primary.opacity(0.14)))
            }

            HStack(spacing: 10) {
                Picker(store.tr("quick_share_destination_label"), selection: $selectedDestKey) {
                    Text(store.tr("view_inbox")).tag("inbox")
                    Text(store.tr("view_today")).tag("today")
                    Text(store.tr("section_this_evening")).tag("evening")
                    Text(store.tr("view_anytime")).tag("anytime")
                    Text(store.tr("view_someday")).tag("someday")
                    if !store.areas.isEmpty {
                        Divider()
                        ForEach(store.areas) { area in
                            Text("▣ \(area.title)").tag("area:\(area.id)")
                        }
                    }
                    if !store.projects.filter({ $0.status == .active }).isEmpty {
                        Divider()
                        ForEach(store.projects.filter { $0.status == .active }) { proj in
                            Text("◉ \(proj.title)").tag("proj:\(proj.id)")
                        }
                    }
                }
                .labelsHidden()
                .frame(maxWidth: 200)

                Toggle(store.tr("deadline_title"), isOn: $hasDeadline)
                    .toggleStyle(.checkbox)
                    .font(.system(size: 12))

                if hasDeadline {
                    DatePicker("", selection: $deadlineDate, displayedComponents: [.date])
                        .labelsHidden()
                        .frame(width: 105)
                }
            }

            if isMenuBarPopover && !store.todayDaytimeTasks.isEmpty {
                Divider()
                Text(store.tr("view_today"))
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)
                ForEach(store.todayDaytimeTasks.prefix(4)) { task in
                    HStack(spacing: 8) {
                        ThingsCheckboxButton(
                            status: task.status,
                            onToggleComplete: { store.toggleTaskCompletion(task) },
                            onCancelTask: { store.cancelTask(task) }
                        )
                        Text(task.title.isEmpty ? store.tr("todo_untitled") : task.title)
                            .font(.system(size: 12.5))
                            .lineLimit(1)
                        Spacer()
                    }
                }
            }

            Divider()

            HStack {
                if !isMenuBarPopover {
                    Button(store.tr("action_cancel")) {
                        dismiss()
                    }
                } else {
                    Button("Open Thingsbook") {
                        NSApplication.shared.activate(ignoringOtherApps: true)
                    }
                    .font(.system(size: 12))
                }

                Spacer()

                Button(store.tr("quick_share_save_action")) {
                    saveQuickTask()
                }
                .buttonStyle(.borderedProminent)
                .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(16)
        .frame(width: isMenuBarPopover ? 350 : 440)
        .onAppear {
            if !isMenuBarPopover {
                title = store.quickEntryPrefillTitle
                notes = store.quickEntryPrefillNotes
            }
            titleFocused = true
        }
    }

    private func saveQuickTask() {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty else { return }

        let dest: QuickShareDestination
        if selectedDestKey == "today" {
            dest = .today
        } else if selectedDestKey == "evening" {
            dest = .thisEvening
        } else if selectedDestKey == "anytime" {
            dest = .anytime
        } else if selectedDestKey == "someday" {
            dest = .someday
        } else if selectedDestKey.hasPrefix("area:") {
            dest = .area(String(selectedDestKey.dropFirst(5)))
        } else if selectedDestKey.hasPrefix("proj:") {
            dest = .project(String(selectedDestKey.dropFirst(5)))
        } else {
            dest = .inbox
        }

        let dl = hasDeadline ? DateEpochConverter.startOfDayMillis(for: deadlineDate) : nil
        store.quickCaptureTask(title: cleanTitle, notes: notes, destination: dest, deadline: dl)
        title = ""
        notes = ""
        store.quickEntryPrefillTitle = ""
        store.quickEntryPrefillNotes = ""
        onSaved?()
        if !isMenuBarPopover {
            dismiss()
        }
    }
}
