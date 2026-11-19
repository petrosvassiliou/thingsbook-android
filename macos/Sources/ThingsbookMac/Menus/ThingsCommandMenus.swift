import SwiftUI
import AppKit
import ThingsbookCore

public struct ThingsCommandMenus: Commands {
    @ObservedObject var store: ThingsbookStore

    public init(store: ThingsbookStore) {
        self.store = store
    }

    @MainActor
    private static func isTextInputActive() -> Bool {
        if let responder = NSApp.keyWindow?.firstResponder, responder is NSTextView {
            return true
        }
        return false
    }

    @MainActor
    private func isShortcutBlocked() -> Bool {
        store.isLocked || store.activeSheet != nil || Self.isTextInputActive()
    }

    @MainActor
    private var pinTargetIds: [String] {
        if !store.selectedTaskIds.isEmpty {
            return Array(store.selectedTaskIds)
        }
        if let exp = store.expandedTaskId {
            return [exp]
        }
        return []
    }

    @MainActor
    private var allPinTargetsPinned: Bool {
        let ids = pinTargetIds
        guard !ids.isEmpty else { return false }
        let matches = (store.allOpenTasks + store.logbookTasks).filter { ids.contains($0.id) }
        return !matches.isEmpty && matches.allSatisfy(\.isPinned)
    }

    public var body: some Commands {
        CommandGroup(after: .appInfo) {
            Button(store.tr("settings_updates_search_btn") + "…") {
                guard !store.isLocked else { return }
                store.settingsTab = .about
                store.isSettingsPresented = true
                Task {
                    _ = await store.performManualUpdateCheck()
                }
            }
            .disabled(store.isLocked)
        }

        CommandGroup(replacing: .newItem) {
            Button(store.tr("entity_new_todo")) {
                guard !store.isLocked else { return }
                store.createNewTaskInCurrentContext()
            }
            .keyboardShortcut("n", modifiers: [.command])
            .disabled(store.isLocked)

            Button(store.tr("macos_menubar_quick_entry")) {
                guard !store.isLocked else { return }
                store.quickEntryPrefillTitle = ""
                store.quickEntryPrefillNotes = ""
                store.quickEntryAutoStartVoice = false
                store.isQuickEntryPresented = true
            }
            .keyboardShortcut("n", modifiers: [.command, .shift])
            .disabled(store.isLocked)

            Button(store.tr("shortcut_voice_capture_short")) {
                guard !store.isLocked else { return }
                store.quickEntryPrefillTitle = ""
                store.quickEntryPrefillNotes = ""
                store.quickEntryAutoStartVoice = true
                store.isQuickEntryPresented = true
            }
            .keyboardShortcut("v", modifiers: [.command, .shift, .control])
            .disabled(store.isLocked)

            Divider()

            Button(store.tr("entity_new_project")) {
                guard !store.isLocked else { return }
                store.newProjectDefaultAreaId = nil
                store.isNewProjectSheetPresented = true
            }
            .keyboardShortcut("n", modifiers: [.command, .option])
            .disabled(store.isLocked)

            Button(store.tr("v2_templates_title")) {
                guard !store.isLocked else { return }
                store.templatesInitialTab = .gallery
                store.isTemplatesSheetPresented = true
            }
            .disabled(store.isLocked)

            Button(store.tr("v2_import_markdown_title")) {
                guard !store.isLocked else { return }
                store.templatesInitialTab = .importOutline
                store.isTemplatesSheetPresented = true
            }
            .disabled(store.isLocked)

            Button(store.tr("v2_save_as_template")) {
                guard !store.isLocked, case .project(let pid) = store.selectedDestination else { return }
                store.saveProjectAsTemplate(pid)
            }
            .disabled(store.isLocked || {
                if case .project = store.selectedDestination { return false }
                return true
            }())

            Button(store.tr("entity_new_area")) {
                guard !store.isLocked else { return }
                store.isNewAreaSheetPresented = true
            }
            .keyboardShortcut("n", modifiers: [.command, .control])
            .disabled(store.isLocked)

            Button(store.tr("v30_perspective_new")) {
                guard !store.isLocked else { return }
                store.editingPerspective = nil
                store.isPerspectiveEditorPresented = true
            }
            .disabled(store.isLocked)

            Divider()

            Button(store.tr("backup_create_button")) {
                guard !store.isLocked else { return }
                store.exportBackupInteractively(password: nil)
            }
            .keyboardShortcut("e", modifiers: [.command, .shift])
            .disabled(store.isLocked)

            Button(store.tr("backup_restore_button")) {
                guard !store.isLocked else { return }
                store.importBackupInteractively()
            }
            .keyboardShortcut("i", modifiers: [.command, .shift])
            .disabled(store.isLocked)
        }

        CommandGroup(replacing: .undoRedo) {
            Button(store.tr("action_undo")) {
                if let responder = NSApp.keyWindow?.firstResponder, responder is NSTextView {
                    if let undoManager = responder.undoManager, undoManager.canUndo {
                        undoManager.undo()
                        return
                    }
                }
                guard !store.isLocked else { return }
                store.performUndo()
            }
            .keyboardShortcut("z", modifiers: [.command])
            .disabled(store.isLocked || store.undoBanner == nil)
        }

        CommandMenu(store.tr("macos_todo_menu")) {
            Button(store.tr("action_complete")) {
                guard !store.isLocked, store.activeSheet == nil else { return }
                if store.selectedTaskIds.count > 1 {
                    store.completeSelectedTasks()
                } else if let id = store.expandedTaskId ?? store.selectedTaskIds.first,
                          let task = (store.allOpenTasks + store.logbookTasks).first(where: { $0.id == id }) {
                    store.toggleTaskCompletion(task)
                }
            }
            .keyboardShortcut("k", modifiers: [.command])
            .disabled(store.isLocked || store.activeSheet != nil)

            Button(store.tr(allPinTargetsPinned ? "v25_unpin_task" : "v25_pin_task")) {
                guard !isShortcutBlocked() else { return }
                store.togglePinForSelectedOrExpandedTasks()
            }
            .keyboardShortcut("p", modifiers: [.command, .shift])
            .disabled(store.isLocked || store.activeSheet != nil || pinTargetIds.isEmpty)

            Button(store.tr("action_duplicate_todo")) {
                guard !isShortcutBlocked() else { return }
                store.duplicateSelectedOrExpandedTask()
            }
            .keyboardShortcut("d", modifiers: [.command])
            .disabled(store.isLocked || store.activeSheet != nil)

            Button(store.tr("action_attach_file")) {
                guard !store.isLocked, store.activeSheet == nil else { return }
                if let id = store.expandedTaskId ?? store.selectedTaskIds.first {
                    store.pickAndAttachFiles(taskId: id)
                } else if case .project(let pid) = store.selectedDestination {
                    store.pickAndAttachFiles(projectId: pid)
                }
            }
            .keyboardShortcut("a", modifiers: [.command, .shift])
            .disabled(store.isLocked || store.activeSheet != nil)

            Button(store.tr("action_focus_timer")) {
                guard !store.isLocked, store.activeSheet == nil else { return }
                if let id = store.expandedTaskId ?? store.selectedTaskIds.first,
                   let task = store.allOpenTasks.first(where: { $0.id == id }) {
                    store.startFocusTimer(for: task)
                }
            }
            .keyboardShortcut("f", modifiers: [.command, .option])
            .disabled(store.isLocked || store.activeSheet != nil)

            Divider()

            Button(store.tr("view_today")) {
                guard !isShortcutBlocked() else { return }
                scheduleSelected(.today)
            }
            .keyboardShortcut("t", modifiers: [.command])
            .disabled(store.isLocked || store.activeSheet != nil)

            Button(store.tr("view_this_evening")) {
                guard !isShortcutBlocked() else { return }
                scheduleSelected(.thisEvening)
            }
            .keyboardShortcut("e", modifiers: [.command])
            .disabled(store.isLocked || store.activeSheet != nil)

            Button(store.tr("view_someday")) {
                guard !isShortcutBlocked() else { return }
                scheduleSelected(.someday)
            }
            .keyboardShortcut("o", modifiers: [.command])
            .disabled(store.isLocked || store.activeSheet != nil)

            let shortcutTags = store.tags.filter { ($0.shortcut?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false) }
            if !shortcutTags.isEmpty {
                Divider()
                ForEach(shortcutTags) { tag in
                    if let firstChar = tag.shortcut?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().first {
                        Button("#\(tag.title)") {
                            guard !isShortcutBlocked() else { return }
                            store.applyTagShortcut(String(firstChar))
                        }
                        .keyboardShortcut(KeyEquivalent(firstChar), modifiers: [.control])
                        .disabled(store.isLocked || store.activeSheet != nil)
                    }
                }
            }

            Divider()

            Button(store.tr("action_delete")) {
                if let responder = NSApp.keyWindow?.firstResponder, responder is NSTextView {
                    responder.doCommand(by: #selector(NSStandardKeyBindingResponding.deleteToBeginningOfLine(_:)))
                    return
                }
                guard !store.isLocked, store.activeSheet == nil else { return }
                store.deleteSelectedTasks()
            }
            .keyboardShortcut(.delete, modifiers: [.command])
            .disabled(store.isLocked || store.activeSheet != nil)
        }

        CommandMenu(store.tr("macos_go_menu")) {
            Button(store.tr("quick_find_title")) {
                guard !store.isLocked else { return }
                store.isQuickFindPresented = true
            }
            .keyboardShortcut("f", modifiers: [.command])
            .disabled(store.isLocked)

            Button(store.tr("v2_weekly_review_title")) {
                guard !store.isLocked else { return }
                store.isWeeklyReviewPresented = true
            }
            .keyboardShortcut("r", modifiers: [.command, .shift])
            .disabled(store.isLocked)

            Divider()

            Button(store.tr("view_inbox")) {
                guard !store.isLocked else { return }
                store.selectedDestination = .smartView(.inbox)
            }
            .keyboardShortcut("1", modifiers: [.command])
            .disabled(store.isLocked)

            Button(store.tr("view_today")) {
                guard !store.isLocked else { return }
                store.selectedDestination = .smartView(.today)
            }
            .keyboardShortcut("2", modifiers: [.command])
            .disabled(store.isLocked)

            Button(store.tr("view_upcoming")) {
                guard !store.isLocked else { return }
                store.selectedDestination = .smartView(.upcoming)
            }
            .keyboardShortcut("3", modifiers: [.command])
            .disabled(store.isLocked)

            Button(store.tr("view_anytime")) {
                guard !store.isLocked else { return }
                store.selectedDestination = .smartView(.anytime)
            }
            .keyboardShortcut("4", modifiers: [.command])
            .disabled(store.isLocked)

            Button(store.tr("view_someday")) {
                guard !store.isLocked else { return }
                store.selectedDestination = .smartView(.someday)
            }
            .keyboardShortcut("5", modifiers: [.command])
            .disabled(store.isLocked)

            Button(store.tr("view_logbook")) {
                guard !store.isLocked else { return }
                store.selectedDestination = .smartView(.logbook)
            }
            .keyboardShortcut("6", modifiers: [.command])
            .disabled(store.isLocked)

            Button(store.tr("view_trash")) {
                guard !store.isLocked else { return }
                store.selectedDestination = .smartView(.trash)
            }
            .keyboardShortcut("7", modifiers: [.command])
            .disabled(store.isLocked)

            Divider()

            Button(store.tr("areas_manage_title")) {
                guard !store.isLocked else { return }
                store.isAreaManagerPresented = true
            }
            .disabled(store.isLocked)

            Button(store.tr("tags_manage_title")) {
                guard !store.isLocked else { return }
                store.isTagManagerPresented = true
            }
            .disabled(store.isLocked)

            Divider()

            Button(store.tr("gemini_hub_title")) {
                guard !store.isLocked else { return }
                store.geminiInitialTab = .brainDump
                store.isGeminiSheetPresented = true
            }
            .keyboardShortcut("g", modifiers: [.command])
            .disabled(store.isLocked)

            Button(store.tr("sync_action_now")) {
                guard !store.isLocked else { return }
                store.syncNow()
            }
            .keyboardShortcut("r", modifiers: [.command, .option])
            .disabled(store.isLocked)
        }
    }

    private func scheduleSelected(_ preset: ScheduleState) {
        let resolved = WhenScheduleResolver.resolvePresetSelection(
            preset: preset,
            reminderEnabled: false,
            reminderHour: 9,
            reminderMinute: 0
        )
        let ids = !store.selectedTaskIds.isEmpty ? Array(store.selectedTaskIds) : (store.expandedTaskId.map { [$0] } ?? [])
        for id in ids {
            if var t = store.allOpenTasks.first(where: { $0.id == id }) {
                t.scheduleState = resolved.scheduleState
                t.whenDate = resolved.whenDateEpochMillis
                store.updateTask(t)
            }
        }
    }
}
