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
    private var isCommandBlocked: Bool {
        store.isLocked || store.isTimelineFullScreen || store.activeSheet != nil
    }

    @MainActor
    private func isShortcutBlocked() -> Bool {
        isCommandBlocked || Self.isTextInputActive()
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

    @MainActor
    private var allTargetsMilestone: Bool {
        let ids = pinTargetIds
        guard !ids.isEmpty else { return false }
        let matches = (store.allOpenTasks + store.logbookTasks).filter { ids.contains($0.id) }
        return !matches.isEmpty && matches.allSatisfy(\.isMilestone)
    }

    @MainActor
    private var openInProgressTargetTasks: [TaskItem] {
        let ids = pinTargetIds
        guard !ids.isEmpty else { return [] }
        return store.allOpenTasks.filter { ids.contains($0.id) && $0.status == .open && !$0.isDeleted }
    }

    @MainActor
    private var allTargetsInProgress: Bool {
        let matches = openInProgressTargetTasks
        return !matches.isEmpty && matches.allSatisfy(\.isInProgress)
    }

    public var body: some Commands {
        CommandGroup(after: .appInfo) {
            Button(store.tr("settings_updates_search_btn") + "…") {
                guard !isCommandBlocked else { return }
                store.settingsTab = .about
                store.isSettingsPresented = true
                Task {
                    _ = await store.performManualUpdateCheck()
                }
            }
            .disabled(isCommandBlocked)
        }

        CommandGroup(replacing: .newItem) {
            Button(store.tr("entity_new_todo")) {
                guard !isCommandBlocked else { return }
                store.createNewTaskInCurrentContext()
            }
            .keyboardShortcut("n", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("macos_menubar_quick_entry")) {
                guard !isCommandBlocked else { return }
                store.quickEntryPrefillTitle = ""
                store.quickEntryPrefillNotes = ""
                store.quickEntryAutoStartVoice = false
                store.isQuickEntryPresented = true
            }
            .keyboardShortcut("n", modifiers: [.command, .shift])
            .disabled(isCommandBlocked)

            Button(store.tr("shortcut_voice_capture_short")) {
                guard !isCommandBlocked else { return }
                store.quickEntryPrefillTitle = ""
                store.quickEntryPrefillNotes = ""
                store.quickEntryAutoStartVoice = true
                store.isQuickEntryPresented = true
            }
            .keyboardShortcut("v", modifiers: [.command, .shift, .control])
            .disabled(isCommandBlocked)

            Divider()

            Button(store.tr("entity_new_project")) {
                guard !isCommandBlocked else { return }
                store.newProjectDefaultAreaId = nil
                store.isNewProjectSheetPresented = true
            }
            .keyboardShortcut("n", modifiers: [.command, .option])
            .disabled(isCommandBlocked)

            Button(store.tr("v2_templates_title")) {
                guard !isCommandBlocked else { return }
                store.templatesInitialTab = .gallery
                store.isTemplatesSheetPresented = true
            }
            .disabled(isCommandBlocked)

            Button(store.tr("v2_import_markdown_title")) {
                guard !isCommandBlocked else { return }
                store.templatesInitialTab = .importOutline
                store.isTemplatesSheetPresented = true
            }
            .disabled(isCommandBlocked)

            Button(store.tr("v30_external_import_export_tab")) {
                guard !isCommandBlocked else { return }
                store.templatesInitialTab = .externalImportExport
                store.isTemplatesSheetPresented = true
            }
            .disabled(isCommandBlocked)

            if case .project(let selectedProjectId) = store.selectedDestination {
                Menu(store.tr("v30_external_export_section")) {
                    Button(store.tr("v30_export_asana_csv")) {
                        guard !isCommandBlocked else { return }
                        store.exportProjectExternalInteractively(projectId: selectedProjectId, format: .asanaCsv)
                    }
                    Button(store.tr("v30_export_asana_json")) {
                        guard !isCommandBlocked else { return }
                        store.exportProjectExternalInteractively(projectId: selectedProjectId, format: .asanaJson)
                    }
                    Divider()
                    Button(store.tr("v30_export_planner_xlsx")) {
                        guard !isCommandBlocked else { return }
                        store.exportProjectExternalInteractively(projectId: selectedProjectId, format: .plannerXlsx)
                    }
                    Button(store.tr("v30_export_planner_csv")) {
                        guard !isCommandBlocked else { return }
                        store.exportProjectExternalInteractively(projectId: selectedProjectId, format: .plannerCsv)
                    }
                }
                .disabled(isCommandBlocked)
            }

            Button(store.tr("v2_save_as_template")) {
                guard !isCommandBlocked, case .project(let pid) = store.selectedDestination else { return }
                store.saveProjectAsTemplate(pid)
            }
            .disabled(isCommandBlocked || {
                if case .project = store.selectedDestination { return false }
                return true
            }())

            Button(store.tr("entity_new_area")) {
                guard !isCommandBlocked else { return }
                store.isNewAreaSheetPresented = true
            }
            .keyboardShortcut("n", modifiers: [.command, .control])
            .disabled(isCommandBlocked)

            Button(store.tr("v30_perspective_new")) {
                guard !isCommandBlocked else { return }
                store.editingPerspective = nil
                store.isPerspectiveEditorPresented = true
            }
            .disabled(isCommandBlocked)

            Divider()

            Button(store.tr("backup_create_button")) {
                guard !isCommandBlocked else { return }
                store.exportBackupInteractively(password: nil)
            }
            .keyboardShortcut("e", modifiers: [.command, .shift])
            .disabled(isCommandBlocked)

            Button(store.tr("backup_restore_button")) {
                guard !isCommandBlocked else { return }
                store.importBackupInteractively()
            }
            .keyboardShortcut("i", modifiers: [.command, .shift])
            .disabled(isCommandBlocked)
        }

        CommandGroup(replacing: .undoRedo) {
            Button(store.tr("action_undo")) {
                if let responder = NSApp.keyWindow?.firstResponder, responder is NSTextView {
                    if let undoManager = responder.undoManager, undoManager.canUndo {
                        undoManager.undo()
                        return
                    }
                }
                guard !isCommandBlocked else { return }
                store.performUndo()
            }
            .keyboardShortcut("z", modifiers: [.command])
            .disabled(isCommandBlocked || store.undoBanner == nil)
        }

        CommandGroup(after: .pasteboard) {
            Button(store.tr("multiselect_select_all")) {
                if let responder = NSApp.keyWindow?.firstResponder, responder is NSTextView {
                    responder.selectAll(nil)
                    return
                }
                guard !isCommandBlocked else { return }
                store.selectAllVisibleTasks()
            }
            .keyboardShortcut("a", modifiers: [.command])
            .disabled(isCommandBlocked)
        }

        CommandMenu(store.tr("macos_todo_menu")) {
            Button(store.tr("action_complete")) {
                guard !isCommandBlocked else { return }
                store.toggleSelectedTasksCompletion()
            }
            .keyboardShortcut(".", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr(allPinTargetsPinned ? "v25_unpin_task" : "v25_pin_task")) {
                guard !isShortcutBlocked() else { return }
                store.togglePinForSelectedOrExpandedTasks()
            }
            .keyboardShortcut("p", modifiers: [.command, .shift])
            .disabled(isCommandBlocked || pinTargetIds.isEmpty)

            Button(store.tr(allTargetsMilestone ? "v30_remove_milestone" : "v30_mark_as_milestone")) {
                guard !isShortcutBlocked() else { return }
                store.toggleMilestoneForSelectedOrExpandedTasks()
            }
            .disabled(isCommandBlocked || pinTargetIds.isEmpty)

            Button(store.tr(allTargetsInProgress ? "v33_clear_in_progress" : "v33_mark_in_progress")) {
                guard !isShortcutBlocked() else { return }
                store.toggleInProgressForSelectedOrExpandedTasks()
            }
            .disabled(isCommandBlocked || openInProgressTargetTasks.isEmpty)

            Button(store.tr("action_duplicate_todo")) {
                guard !isShortcutBlocked() else { return }
                store.duplicateSelectedOrExpandedTask()
            }
            .keyboardShortcut("d", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("action_copy_todo")) {
                guard !isShortcutBlocked() else { return }
                if store.selectedTaskIds.count > 1 {
                    store.copySelectedTasksToPasteboard()
                } else if let id = store.expandedTaskId ?? store.selectedTaskIds.first,
                          let task = (store.allOpenTasks + store.logbookTasks).first(where: { $0.id == id }) {
                    store.copyTaskToPasteboard(task)
                }
            }
            .disabled(isCommandBlocked || (store.expandedTaskId == nil && store.selectedTaskIds.isEmpty))

            Button(store.tr("action_attach_file")) {
                guard !isCommandBlocked else { return }
                store.flushModalDraftsIfNeeded()
                if let id = store.expandedTaskId ?? store.selectedTaskIds.first {
                    store.pickAndAttachFiles(taskId: id)
                } else if case .project(let pid) = store.selectedDestination {
                    store.pickAndAttachFiles(projectId: pid)
                }
            }
            .keyboardShortcut("a", modifiers: [.command, .shift])
            .disabled(isCommandBlocked)

            Button(store.tr("action_focus_timer")) {
                guard !isCommandBlocked else { return }
                store.flushModalDraftsIfNeeded()
                if let id = store.expandedTaskId ?? store.selectedTaskIds.first,
                   let task = store.allOpenTasks.first(where: { $0.id == id }) {
                    store.startFocusTimer(for: task)
                }
            }
            .keyboardShortcut("f", modifiers: [.command, .option])
            .disabled(isCommandBlocked)

            Divider()

            Button(store.tr("view_today")) {
                guard !isShortcutBlocked() else { return }
                scheduleSelected(.today)
            }
            .keyboardShortcut("t", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("view_this_evening")) {
                guard !isShortcutBlocked() else { return }
                scheduleSelected(.thisEvening)
            }
            .keyboardShortcut("e", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("view_someday")) {
                guard !isShortcutBlocked() else { return }
                scheduleSelected(.someday)
            }
            .keyboardShortcut("o", modifiers: [.command])
            .disabled(isCommandBlocked)

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
                        .disabled(isCommandBlocked)
                    }
                }
            }

            Divider()

            Button(store.tr("action_delete")) {
                if let responder = NSApp.keyWindow?.firstResponder, responder is NSTextView {
                    responder.doCommand(by: #selector(NSStandardKeyBindingResponding.deleteToBeginningOfLine(_:)))
                    return
                }
                guard !isCommandBlocked else { return }
                store.deleteSelectedTasks()
            }
            .keyboardShortcut(.delete, modifiers: [.command])
            .disabled(isCommandBlocked)
        }

        CommandMenu(store.tr("macos_go_menu")) {
            Button(store.tr("quick_find_title")) {
                guard !isCommandBlocked else { return }
                store.isQuickFindPresented = true
            }
            .keyboardShortcut("k", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("v2_weekly_review_title")) {
                guard !isCommandBlocked else { return }
                store.isWeeklyReviewPresented = true
            }
            .keyboardShortcut("r", modifiers: [.command, .shift])
            .disabled(isCommandBlocked)

            Divider()

            Button(store.tr("view_inbox")) {
                guard !isCommandBlocked else { return }
                store.selectedDestination = .smartView(.inbox)
            }
            .keyboardShortcut("1", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("view_today")) {
                guard !isCommandBlocked else { return }
                store.selectedDestination = .smartView(.today)
            }
            .keyboardShortcut("2", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("view_upcoming")) {
                guard !isCommandBlocked else { return }
                store.selectedDestination = .smartView(.upcoming)
            }
            .keyboardShortcut("3", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("view_anytime")) {
                guard !isCommandBlocked else { return }
                store.selectedDestination = .smartView(.anytime)
            }
            .keyboardShortcut("4", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("view_someday")) {
                guard !isCommandBlocked else { return }
                store.selectedDestination = .smartView(.someday)
            }
            .keyboardShortcut("5", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("view_logbook")) {
                guard !isCommandBlocked else { return }
                store.selectedDestination = .smartView(.logbook)
            }
            .keyboardShortcut("6", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("view_trash")) {
                guard !isCommandBlocked else { return }
                store.selectedDestination = .smartView(.trash)
            }
            .keyboardShortcut("7", modifiers: [.command])
            .disabled(isCommandBlocked)

            Divider()

            Button(store.tr("areas_manage_title")) {
                guard !isCommandBlocked else { return }
                store.isAreaManagerPresented = true
            }
            .disabled(isCommandBlocked)

            Button(store.tr("tags_manage_title")) {
                guard !isCommandBlocked else { return }
                store.isTagManagerPresented = true
            }
            .disabled(isCommandBlocked)

            Divider()

            Button(store.tr("gemini_hub_title")) {
                guard !isCommandBlocked else { return }
                store.geminiInitialTab = .brainDump
                store.isGeminiSheetPresented = true
            }
            .keyboardShortcut("g", modifiers: [.command])
            .disabled(isCommandBlocked)

            Button(store.tr("sync_action_now")) {
                guard !isCommandBlocked else { return }
                store.syncNow()
            }
            .keyboardShortcut("r", modifiers: [.command, .option])
            .disabled(isCommandBlocked)

            if case .project(let activeProjectId) = store.selectedDestination {
                Divider()

                Button(store.tr("v30_project_mode_list")) {
                    guard !isCommandBlocked else { return }
                    store.setProjectViewMode(activeProjectId, mode: .list)
                }
                .keyboardShortcut("1", modifiers: [.command, .control])
                .disabled(isCommandBlocked)

                Button(store.tr("v30_project_mode_board")) {
                    guard !isCommandBlocked else { return }
                    store.setProjectViewMode(activeProjectId, mode: .board)
                }
                .keyboardShortcut("2", modifiers: [.command, .control])
                .disabled(isCommandBlocked)

                Button(store.tr("v30_project_mode_roadmap")) {
                    guard !isCommandBlocked else { return }
                    store.setProjectViewMode(activeProjectId, mode: .roadmap)
                }
                .keyboardShortcut("3", modifiers: [.command, .control])
                .disabled(isCommandBlocked)
            } else if case .area(let activeAreaId) = store.selectedDestination {
                Divider()

                Button(store.tr("v30_project_mode_list")) {
                    guard !isCommandBlocked else { return }
                    store.setAreaViewMode(activeAreaId, mode: .list)
                }
                .keyboardShortcut("1", modifiers: [.command, .control])
                .disabled(isCommandBlocked)

                Button(store.tr("v31_area_portfolio_timeline")) {
                    guard !isCommandBlocked else { return }
                    store.setAreaViewMode(activeAreaId, mode: .roadmap)
                }
                .keyboardShortcut("2", modifiers: [.command, .control])
                .disabled(isCommandBlocked)
            }
        }
    }

    private func scheduleSelected(_ preset: ScheduleState) {
        store.flushModalDraftsIfNeeded()
        let ids = !store.selectedTaskIds.isEmpty ? Array(store.selectedTaskIds) : (store.expandedTaskId.map { [$0] } ?? [])
        store.scheduleTasks(ids, to: preset)
    }
}
