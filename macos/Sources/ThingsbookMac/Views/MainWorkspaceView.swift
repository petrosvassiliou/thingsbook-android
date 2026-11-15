import SwiftUI
import AppKit
import ThingsbookCore

public struct MainWorkspaceView: View {
    @ObservedObject var store: ThingsbookStore

    @State private var columnVisibility: NavigationSplitViewVisibility = .all
    @State private var isMagicPlusMenuOpen = false
    @State private var decryptPasswordInput = ""
    @State private var decryptError: String?

    public init(store: ThingsbookStore) {
        self.store = store
    }

    public var body: some View {
        ZStack {
            NavigationSplitView(columnVisibility: $columnVisibility) {
                SidebarView(store: store)
                    .navigationSplitViewColumnWidth(min: 232, ideal: 262, max: 330)
            } detail: {
                ZStack(alignment: .bottom) {
                    VStack(spacing: 0) {
                        if let focus = store.focusTimer {
                            focusTimerBanner(focus)
                                .padding(.horizontal, 36)
                                .padding(.top, 14)
                                .padding(.bottom, 4)
                        }
                        SmartViewCanvas(store: store)
                    }
                    .background(ThingsMacPalette.canvasBackground)

                    // Bottom Overlays: Multi-select bar, Undo Banner, Toast & Magic Plus Button
                    HStack(alignment: .bottom, spacing: 14) {
                        Spacer()

                        if store.selectedTaskIds.count > 1 {
                            multiSelectBar
                        } else if let undo = store.undoBanner {
                            undoPillBanner(undo)
                        } else if let toast = store.toastMessage {
                            toastPill(toast)
                        }

                        Spacer()

                        magicPlusButton
                    }
                    .padding(.horizontal, 28)
                    .padding(.bottom, 22)
                }
                .toolbar {
                    ToolbarItem(placement: .navigation) {
                        Button {
                            store.isQuickFindPresented = true
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "magnifyingglass")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundStyle(.secondary)
                                Text(store.tr("quick_find_title"))
                                    .font(.system(size: 12.5, weight: .medium))
                                    .foregroundStyle(.secondary)
                                Text("⌘F")
                                    .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                                    .foregroundStyle(.tertiary)
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 1)
                                    .background(
                                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                                            .fill(Color.primary.opacity(0.06))
                                    )
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4.5)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(Color.primary.opacity(0.05))
                            )
                        }
                        .buttonStyle(.plain)
                        .help(store.tr("quick_find_title") + " (⌘F)")
                    }

                    ToolbarItemGroup(placement: .primaryAction) {
                        // Gemini Intelligence Pill
                        Button {
                            store.geminiInitialTab = .brainDump
                            store.isGeminiSheetPresented = true
                        } label: {
                            HStack(spacing: 5) {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 12, weight: .semibold))
                                Text("Gemini")
                                    .font(.system(size: 12, weight: .semibold))
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
                        .help(store.tr("gemini_hub_title") + " (⌘G)")

                        // Quick Entry
                        Button {
                            store.quickEntryPrefillTitle = ""
                            store.quickEntryPrefillNotes = ""
                            store.isQuickEntryPresented = true
                        } label: {
                            Image(systemName: "tray.and.arrow.down")
                        }
                        .help(store.tr("quick_share_title") + " (⇧⌘N)")

                        // Manage Areas
                        Button {
                            store.isAreaManagerPresented = true
                        } label: {
                            Image(systemName: "cube")
                        }
                        .help(store.tr("areas_manage_title"))

                        // Manage Tags
                        Button {
                            store.isTagManagerPresented = true
                        } label: {
                            Image(systemName: "tag")
                        }
                        .help(store.tr("tags_manage_title"))

                        // Notifications
                        Button {
                            store.settingsTab = .notifications
                            store.isSettingsPresented = true
                        } label: {
                            Image(systemName: store.preferences.dailyDigestEnabled ? "bell.badge.fill" : "bell")
                        }
                        .help(store.tr("notifications_title"))

                        // Instant Language Switcher Menu
                        Menu {
                            Picker(store.tr("settings_language"), selection: $store.preferences.language) {
                                Text(store.tr("settings_system_default")).tag(AppLanguage.system)
                                Divider()
                                Text(store.tr("lang_en")).tag(AppLanguage.en)
                                Text(store.tr("lang_de")).tag(AppLanguage.de)
                                Text(store.tr("lang_fr")).tag(AppLanguage.fr)
                                Text(store.tr("lang_it")).tag(AppLanguage.it)
                                Text(store.tr("lang_es")).tag(AppLanguage.es)
                            }
                        } label: {
                            Image(systemName: "globe")
                        }
                        .help(store.tr("settings_language"))

                        // Cloud Sync Status Button
                        Button {
                            store.settingsTab = .sync
                            store.isSettingsPresented = true
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: store.preferences.localOnlyDbMode ? "internaldrive" : "icloud")
                                Circle()
                                    .fill(syncDotColor)
                                    .frame(width: 6, height: 6)
                            }
                        }
                        .help(syncHelpText)

                        // Settings
                        Button {
                            store.settingsTab = .general
                            store.isSettingsPresented = true
                        } label: {
                            Image(systemName: "gearshape")
                        }
                        .help(store.tr("settings_title") + " (⌘,)")
                    }
                }
            }

            // Touch ID Biometric App Lock Overlay
            if store.isLocked {
                biometricLockOverlay
            }
        }
        .onChange(of: store.preferences.language) { _, newLang in
            NotificationReminderService.shared.configureCategories(lang: newLang)
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
        .sheet(isPresented: $store.isAreaManagerPresented) {
            ManageAreasSheetView(store: store)
        }
        .sheet(isPresented: $store.isTagManagerPresented) {
            ManageTagsSheetView(store: store)
        }
        .sheet(isPresented: $store.isBackupPasswordSheetPresented) {
            backupDecryptSheet
        }
    }

    // MARK: - Focus Session (25m Pomodoro) Card

    private func focusTimerBanner(_ focus: FocusTimerState) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Image(systemName: "timer")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                VStack(alignment: .leading, spacing: 1) {
                    Text(store.tr("focus_timer_active_label").uppercased())
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                    Text(focus.taskTitle)
                        .font(.system(size: 13.5, weight: .semibold))
                        .lineLimit(1)
                }

                Spacer()

                Text(focus.formattedCountdown)
                    .font(.system(size: 17, weight: .bold, design: .monospaced))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                Button {
                    store.toggleFocusTimerPause()
                } label: {
                    Label(
                        focus.isPaused ? store.tr("focus_timer_resume") : store.tr("focus_timer_pause"),
                        systemImage: focus.isPaused ? "play.fill" : "pause.fill"
                    )
                    .font(.system(size: 11.5, weight: .semibold))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)

                Button(store.tr("focus_timer_plus_5m")) {
                    store.addFiveMinutesToFocusTimer()
                }
                .font(.system(size: 11.5, weight: .semibold))
                .buttonStyle(.bordered)
                .controlSize(.small)

                Button {
                    if let task = store.allOpenTasks.first(where: { $0.id == focus.taskId }) {
                        store.toggleTaskCompletion(task)
                    }
                    store.stopFocusTimer()
                } label: {
                    Label(store.tr("action_complete"), systemImage: "checkmark")
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.logbookGreen)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)

                Button {
                    store.stopFocusTimer()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .help(store.tr("focus_timer_stop"))
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(ThingsMacPalette.accentBlue.opacity(0.14))
                    Capsule()
                        .fill(ThingsMacPalette.accentBlue)
                        .frame(width: max(8, geo.size.width * focus.progress))
                }
            }
            .frame(height: 4)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(ThingsMacPalette.accentBlue.opacity(0.08))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.24), lineWidth: 1)
                )
        )
        .frame(maxWidth: 880)
    }

    // MARK: - Magic Plus Button & Popover Menu

    private var magicPlusButton: some View {
        Button {
            if NSEvent.modifierFlags.contains(.option) {
                store.createNewTaskInCurrentContext()
            } else {
                isMagicPlusMenuOpen.toggle()
            }
        } label: {
            Image(systemName: isMagicPlusMenuOpen ? "xmark" : "plus")
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 46, height: 46)
                .background(
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: ThingsMacPalette.accentBlue.opacity(0.36), radius: 12, x: 0, y: 5)
                )
        }
        .buttonStyle(.plain)
        .help(store.tr("magic_plus_fab_tooltip"))
        .popover(isPresented: $isMagicPlusMenuOpen, arrowEdge: .bottom) {
            VStack(alignment: .leading, spacing: 4) {
                magicPlusItem(
                    icon: "checkmark.circle",
                    tint: ThingsMacPalette.accentBlue,
                    title: store.tr("entity_new_todo"),
                    subtitle: magicPlusTodoSubtitle,
                    shortcut: "⌘N"
                ) {
                    isMagicPlusMenuOpen = false
                    store.createNewTaskInCurrentContext()
                }

                if case .project(let projId) = store.selectedDestination {
                    magicPlusItem(
                        icon: "text.insert",
                        tint: ThingsMacPalette.anytimeTeal,
                        title: store.tr("entity_new_heading"),
                        subtitle: store.tr("magic_plus_heading_sub"),
                        shortcut: nil
                    ) {
                        isMagicPlusMenuOpen = false
                        _ = try? store.database.createHeading(projectId: projId, title: store.tr("entity_new_heading"))
                    }
                }

                magicPlusItem(
                    icon: "folder.badge.plus",
                    tint: ThingsMacPalette.inboxBlue,
                    title: store.tr("entity_new_project"),
                    subtitle: store.tr("magic_plus_project_sub_default"),
                    shortcut: "⌥⌘N"
                ) {
                    isMagicPlusMenuOpen = false
                    if case .area(let aid) = store.selectedDestination {
                        store.newProjectDefaultAreaId = aid
                    } else {
                        store.newProjectDefaultAreaId = nil
                    }
                    store.isNewProjectSheetPresented = true
                }

                magicPlusItem(
                    icon: "cube",
                    tint: ThingsMacPalette.areaGreen,
                    title: store.tr("entity_new_area"),
                    subtitle: store.tr("magic_plus_area_sub"),
                    shortcut: "⌃⌘N"
                ) {
                    isMagicPlusMenuOpen = false
                    store.isNewAreaSheetPresented = true
                }
            }
            .padding(10)
            .frame(width: 295)
        }
    }

    private var magicPlusTodoSubtitle: String {
        switch store.selectedDestination {
        case .project: return store.tr("magic_plus_todo_sub_project")
        case .area: return store.tr("magic_plus_todo_sub_area")
        case .smartView(.inbox): return store.tr("magic_plus_todo_sub_inbox")
        default: return store.tr("magic_plus_todo_sub_view")
        }
    }

    private func magicPlusItem(
        icon: String,
        tint: Color,
        title: String,
        subtitle: String,
        shortcut: String?,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 11) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 28, height: 28)
                    .background(
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .fill(tint.opacity(0.12))
                    )

                VStack(alignment: .leading, spacing: 1.5) {
                    HStack(spacing: 6) {
                        Text(title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.primary)
                        if let shortcut {
                            Text(shortcut)
                                .font(.system(size: 10, weight: .semibold, design: .rounded))
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 1)
                                .background(
                                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                                        .fill(Color.primary.opacity(0.07))
                                )
                        }
                    }
                    Text(subtitle)
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer()
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 7)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Bottom Multi-Select, Undo & Toast Banners

    private var multiSelectBar: some View {
        HStack(spacing: 12) {
            Text(store.trf("multiselect_count", store.selectedTaskIds.count))
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(.white)

            Divider()
                .frame(height: 14)
                .overlay(Color.white.opacity(0.25))

            Button(store.tr("view_today")) {
                let sod = DateEpochConverter.startOfDayMillis(for: Date())
                for id in store.selectedTaskIds {
                    if var t = store.allOpenTasks.first(where: { $0.id == id }) {
                        t.scheduleState = .today
                        t.whenDate = sod
                        store.updateTask(t)
                    }
                }
            }
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(ThingsMacPalette.todayYellow)
            .buttonStyle(.plain)

            Button(store.tr("action_delete")) {
                store.deleteSelectedTasks()
            }
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(ThingsMacPalette.upcomingRed)
            .buttonStyle(.plain)

            Button {
                store.selectedTaskIds.removeAll()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .foregroundStyle(.white.opacity(0.75))
            }
            .buttonStyle(.plain)
            .help(store.tr("multiselect_clear"))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(
            Capsule(style: .continuous)
                .fill(Color.black.opacity(0.86))
                .shadow(color: .black.opacity(0.22), radius: 12, y: 4)
        )
    }

    private func undoPillBanner(_ undo: UndoBannerState) -> some View {
        HStack(spacing: 14) {
            Text(undo.message)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
            Button(store.tr("action_undo")) {
                store.performUndo()
            }
            .font(.system(size: 12.5, weight: .bold))
            .foregroundStyle(ThingsMacPalette.todayYellow)
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 10)
        .background(
            Capsule(style: .continuous)
                .fill(Color.black.opacity(0.86))
                .shadow(color: .black.opacity(0.22), radius: 12, y: 4)
        )
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    private func toastPill(_ toast: String) -> some View {
        Text(toast)
            .font(.system(size: 12.5, weight: .medium))
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 9)
            .background(Capsule().fill(Color.black.opacity(0.82)))
            .transition(.opacity)
    }

    // MARK: - Touch ID Lock & Encrypted Backup Unlock

    private var biometricLockOverlay: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                Image(systemName: "touchid")
                    .font(.system(size: 48))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                Text(store.tr("app_lock_title"))
                    .font(.system(size: 20, weight: .bold))

                Text(store.tr("macos_app_lock_subtitle"))
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 320)

                Button(store.tr("app_lock_unlock_btn")) {
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
            Label(store.tr("backup_restore_encrypted_title"), systemImage: "lock.fill")
                .font(.system(size: 15, weight: .semibold))

            Text(store.tr("backup_restore_encrypted_desc"))
                .font(.system(size: 12))
                .foregroundStyle(.secondary)

            SecureField(store.tr("backup_restore_enter_password"), text: $decryptPasswordInput)
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
                Button(store.tr("backup_restore_confirm_decrypt")) {
                    unlockPendingBackup()
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(22)
        .frame(width: 400)
    }

    private func unlockPendingBackup() {
        do {
            try store.unlockAndRestorePendingBackup(password: decryptPasswordInput)
            decryptPasswordInput = ""
            decryptError = nil
        } catch {
            decryptError = store.tr("backup_err_wrong_password")
        }
    }

    private var syncDotColor: Color {
        if store.preferences.localOnlyDbMode { return .secondary.opacity(0.6) }
        switch store.syncSnapshot.status {
        case .synced: return ThingsMacPalette.logbookGreen
        case .syncing: return ThingsMacPalette.todayYellow
        case .offline: return .secondary.opacity(0.55)
        }
    }

    private var syncHelpText: String {
        if store.preferences.localOnlyDbMode {
            return store.tr("settings_local_mode_active")
        }
        switch store.syncSnapshot.status {
        case .synced: return store.tr("sync_status_synced")
        case .syncing: return store.tr("sync_status_syncing")
        case .offline: return store.tr("sync_status_offline")
        }
    }
}
