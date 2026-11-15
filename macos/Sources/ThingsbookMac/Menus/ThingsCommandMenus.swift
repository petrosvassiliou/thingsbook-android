import SwiftUI
import ThingsbookCore

public struct ThingsCommandMenus: Commands {
    @ObservedObject var store: ThingsbookStore

    public init(store: ThingsbookStore) {
        self.store = store
    }

    public var body: some Commands {
        CommandGroup(replacing: .newItem) {
            Button(store.tr("entity_new_todo")) {
                store.createNewTaskInCurrentContext()
            }
            .keyboardShortcut("n", modifiers: [.command])

            Button(store.tr("macos_menubar_quick_entry")) {
                store.quickEntryPrefillTitle = ""
                store.quickEntryPrefillNotes = ""
                store.quickEntryAutoStartVoice = false
                store.isQuickEntryPresented = true
            }
            .keyboardShortcut("n", modifiers: [.command, .shift])

            Button(store.tr("shortcut_voice_capture_short")) {
                store.quickEntryPrefillTitle = ""
                store.quickEntryPrefillNotes = ""
                store.quickEntryAutoStartVoice = true
                store.isQuickEntryPresented = true
            }
            .keyboardShortcut("v", modifiers: [.command, .shift, .control])

            Divider()

            Button(store.tr("entity_new_project")) {
                store.newProjectDefaultAreaId = nil
                store.isNewProjectSheetPresented = true
            }
            .keyboardShortcut("n", modifiers: [.command, .option])

            Button(store.tr("entity_new_area")) {
                store.isNewAreaSheetPresented = true
            }
            .keyboardShortcut("n", modifiers: [.command, .control])

            Divider()

            Button(store.tr("backup_create_button")) {
                store.exportBackupInteractively(password: nil)
            }
            .keyboardShortcut("e", modifiers: [.command, .shift])

            Button(store.tr("backup_restore_button")) {
                store.importBackupInteractively()
            }
            .keyboardShortcut("i", modifiers: [.command, .shift])
        }

        CommandGroup(replacing: .undoRedo) {
            Button(store.tr("action_undo")) {
                store.performUndo()
            }
            .keyboardShortcut("z", modifiers: [.command])
            .disabled(store.undoBanner == nil)
        }

        CommandMenu(store.tr("macos_todo_menu")) {
            Button(store.tr("action_complete")) {
                if let id = store.expandedTaskId ?? store.selectedTaskIds.first,
                   let task = (store.allOpenTasks + store.logbookTasks).first(where: { $0.id == id }) {
                    store.toggleTaskCompletion(task)
                }
            }
            .keyboardShortcut("k", modifiers: [.command])

            Button(store.tr("action_duplicate_todo")) {
                store.duplicateSelectedOrExpandedTask()
            }
            .keyboardShortcut("d", modifiers: [.command])

            Button(store.tr("action_attach_file")) {
                if let id = store.expandedTaskId ?? store.selectedTaskIds.first {
                    store.pickAndAttachFiles(taskId: id)
                } else if case .project(let pid) = store.selectedDestination {
                    store.pickAndAttachFiles(projectId: pid)
                }
            }
            .keyboardShortcut("a", modifiers: [.command, .shift])

            Button(store.tr("action_focus_timer")) {
                if let id = store.expandedTaskId ?? store.selectedTaskIds.first,
                   let task = store.allOpenTasks.first(where: { $0.id == id }) {
                    store.startFocusTimer(for: task)
                }
            }
            .keyboardShortcut("f", modifiers: [.command, .option])

            Divider()

            Button(store.tr("view_today")) {
                scheduleSelected(.today)
            }
            .keyboardShortcut("t", modifiers: [.command])

            Button(store.tr("view_this_evening")) {
                scheduleSelected(.thisEvening)
            }
            .keyboardShortcut("e", modifiers: [.command])

            Button(store.tr("view_someday")) {
                scheduleSelected(.someday)
            }
            .keyboardShortcut("o", modifiers: [.command])

            Divider()

            Button(store.tr("action_delete")) {
                store.deleteSelectedTasks()
            }
            .keyboardShortcut(.delete, modifiers: [.command])
        }

        CommandMenu(store.tr("macos_go_menu")) {
            Button(store.tr("quick_find_title")) {
                store.isQuickFindPresented = true
            }
            .keyboardShortcut("f", modifiers: [.command])

            Divider()

            Button(store.tr("view_inbox")) {
                store.selectedDestination = .smartView(.inbox)
            }
            .keyboardShortcut("1", modifiers: [.command])

            Button(store.tr("view_today")) {
                store.selectedDestination = .smartView(.today)
            }
            .keyboardShortcut("2", modifiers: [.command])

            Button(store.tr("view_upcoming")) {
                store.selectedDestination = .smartView(.upcoming)
            }
            .keyboardShortcut("3", modifiers: [.command])

            Button(store.tr("view_anytime")) {
                store.selectedDestination = .smartView(.anytime)
            }
            .keyboardShortcut("4", modifiers: [.command])

            Button(store.tr("view_someday")) {
                store.selectedDestination = .smartView(.someday)
            }
            .keyboardShortcut("5", modifiers: [.command])

            Button(store.tr("view_logbook")) {
                store.selectedDestination = .smartView(.logbook)
            }
            .keyboardShortcut("6", modifiers: [.command])

            Button(store.tr("view_trash")) {
                store.selectedDestination = .smartView(.trash)
            }
            .keyboardShortcut("7", modifiers: [.command])

            Divider()

            Button(store.tr("areas_manage_title")) {
                store.isAreaManagerPresented = true
            }

            Button(store.tr("tags_manage_title")) {
                store.isTagManagerPresented = true
            }

            Divider()

            Button(store.tr("gemini_hub_title")) {
                store.geminiInitialTab = .brainDump
                store.isGeminiSheetPresented = true
            }
            .keyboardShortcut("g", modifiers: [.command])

            Button(store.tr("sync_action_now")) {
                store.syncNow()
            }
            .keyboardShortcut("r", modifiers: [.command, .shift])
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
