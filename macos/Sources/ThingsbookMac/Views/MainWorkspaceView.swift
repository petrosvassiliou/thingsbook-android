import SwiftUI
import ThingsbookCore

public struct MainWorkspaceView: View {
    @ObservedObject var store: ThingsbookStore

    @State private var columnVisibility: NavigationSplitViewVisibility = .all
    @State private var decryptPasswordInput = ""
    @State private var decryptError: String?

    public init(store: ThingsbookStore) {
        self.store = store
    }

    public var body: some View {
        ZStack {
            NavigationSplitView(columnVisibility: $columnVisibility) {
                SidebarView(store: store)
                    .navigationSplitViewColumnWidth(min: 215, ideal: 245, max: 310)
            } detail: {
                ZStack(alignment: .bottom) {
                    VStack(spacing: 0) {
                        if let focus = store.focusTimer {
                            focusTimerBanner(focus)
                        }
                        SmartViewCanvas(store: store)
                    }

                    // Bottom Overlays: Undo Banner, Toast & Magic Plus Button
                    HStack(alignment: .bottom) {
                        Spacer()

                        if let undo = store.undoBanner {
                            HStack(spacing: 12) {
                                Text(undo.message)
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundStyle(.white)
                                Button(store.tr("action_undo")) {
                                    store.performUndo()
                                }
                                .font(.system(size: 12.5, weight: .bold))
                                .foregroundStyle(ThingsMacPalette.todayYellow)
                                .buttonStyle(.plain)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(Color.black.opacity(0.84))
                                    .shadow(color: .black.opacity(0.22), radius: 12, y: 4)
                            )
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                        } else if let toast = store.toastMessage {
                            Text(toast)
                                .font(.system(size: 12.5, weight: .medium))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(Capsule().fill(Color.black.opacity(0.8)))
                                .transition(.opacity)
                        }

                        Spacer()

                        // Things-for-Mac Magic Plus Button
                        Button {
                            store.createNewTaskInCurrentContext()
                        } label: {
                            Image(systemName: "plus")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: 42, height: 42)
                                .background(
                                    Circle()
                                        .fill(
                                            LinearGradient(
                                                colors: [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            )
                                        )
                                        .shadow(color: ThingsMacPalette.accentBlue.opacity(0.38), radius: 10, x: 0, y: 4)
                                )
                        }
                        .buttonStyle(.plain)
                        .help(store.tr("fab_new_todo") + " (⌘N)")
                    }
                    .padding(.horizontal, 26)
                    .padding(.bottom, 20)
                }
            }
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    Button {
                        store.createNewTaskInCurrentContext()
                    } label: {
                        Image(systemName: "plus")
                    }
                    .help(store.tr("fab_new_todo") + " (⌘N)")

                    Button {
                        store.quickEntryPrefillTitle = ""
                        store.quickEntryPrefillNotes = ""
                        store.isQuickEntryPresented = true
                    } label: {
                        Image(systemName: "tray.and.arrow.down")
                    }
                    .help(store.tr("quick_share_sheet_title") + " (⇧⌘N)")

                    Button {
                        store.geminiInitialTab = .brainDump
                        store.isGeminiSheetPresented = true
                    } label: {
                        Image(systemName: "sparkles")
                    }
                    .help("Thingsbook Intelligence (⌘G)")

                    Button {
                        store.isQuickFindPresented = true
                    } label: {
                        Image(systemName: "magnifyingglass")
                    }
                    .help(store.tr("sidebar_quick_find") + " (⌘F)")
                }
            }

            // Touch ID Biometric App Lock Overlay
            if store.isLocked {
                biometricLockOverlay
            }
        }
        .sheet(isPresented: $store.isQuickFindPresented) {
            QuickFindOverlay(store: store)
        }
        .sheet(isPresented: $store.isQuickEntryPresented) {
            QuickEntrySheetView(store: store)
        }
        .sheet(isPresented: $store.isGeminiSheetPresented) {
            GeminiAssistantSheet(store: store)
        }
        .sheet(isPresented: $store.isSettingsPresented) {
            SettingsWindowView(store: store, showCloseButton: true)
        }
        .sheet(isPresented: $store.isNewProjectSheetPresented) {
            ProjectEditorSheetView(store: store, existingProject: nil, defaultAreaId: store.newProjectDefaultAreaId)
        }
        .sheet(item: $store.editingProject) { proj in
            ProjectEditorSheetView(store: store, existingProject: proj)
        }
        .sheet(isPresented: $store.isNewAreaSheetPresented) {
            AreaEditorSheetView(store: store, existingArea: nil)
        }
        .sheet(item: $store.editingArea) { area in
            AreaEditorSheetView(store: store, existingArea: area)
        }
        .sheet(isPresented: $store.isBackupPasswordSheetPresented) {
            backupDecryptSheet
        }
    }

    private func focusTimerBanner(_ focus: FocusTimerState) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "timer")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(ThingsMacPalette.accentBlue)

            Text(focus.taskTitle)
                .font(.system(size: 13, weight: .semibold))
                .lineLimit(1)

            Spacer()

            Text(focus.formattedCountdown)
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundStyle(ThingsMacPalette.accentBlue)

            Button {
                store.toggleFocusTimerPause()
            } label: {
                Image(systemName: focus.isPaused ? "play.fill" : "pause.fill")
            }
            .buttonStyle(.bordered)
            .controlSize(.small)

            Button(store.tr("action_complete")) {
                if let task = store.allOpenTasks.first(where: { $0.id == focus.taskId }) {
                    store.toggleTaskCompletion(task)
                }
                store.stopFocusTimer()
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)

            Button {
                store.stopFocusTimer()
            } label: {
                Image(systemName: "xmark")
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 9)
        .background(.ultraThinMaterial)
        .overlay(alignment: .bottom) { Divider() }
    }

    private var biometricLockOverlay: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                Image(systemName: "touchid")
                    .font(.system(size: 48))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                Text(store.tr("biometric_locked_title"))
                    .font(.system(size: 20, weight: .bold))

                Text(store.tr("biometric_prompt_subtitle"))
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)

                Button(store.tr("biometric_unlock_btn")) {
                    store.unlockWithBiometrics()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
            }
            .padding(36)
            .thingsCardSurface(cornerRadius: 18)
        }
        .onAppear {
            store.unlockWithBiometrics()
        }
    }

    private var backupDecryptSheet: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label("Unlock Encrypted Backup", systemImage: "lock.fill")
                .font(.system(size: 15, weight: .semibold))

            SecureField(store.tr("sync_password_label"), text: $decryptPasswordInput)
                .textFieldStyle(.roundedBorder)
                .onSubmit(unlockPendingBackup)

            if let decryptError {
                Text(decryptError)
                    .font(.system(size: 12))
                    .foregroundStyle(.red)
            }

            HStack {
                Spacer()
                Button(store.tr("action_cancel")) {
                    store.pendingEncryptedBackupData = nil
                    store.isBackupPasswordSheetPresented = false
                    decryptPasswordInput = ""
                    decryptError = nil
                }
                Button(store.tr("backup_import_btn")) {
                    unlockPendingBackup()
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(20)
        .frame(width: 360)
    }

    private func unlockPendingBackup() {
        do {
            try store.unlockAndRestorePendingBackup(password: decryptPasswordInput)
            decryptPasswordInput = ""
            decryptError = nil
        } catch {
            decryptError = error.localizedDescription
        }
    }
}
