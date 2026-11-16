import SwiftUI
import AppKit
import ThingsbookCore

public struct MainWorkspaceView: View {
    @ObservedObject var store: ThingsbookStore

    @State private var columnVisibility: NavigationSplitViewVisibility = .all
    @State private var detailPaneWidth: CGFloat = 618
    @State private var isMagicPlusMenuOpen = false
    @State private var isMagicPlusHovered = false
    @State private var isQuickFindHovered = false
    @State private var isLanguagePopoverOpen = false
    @State private var decryptPasswordInput = ""
    @State private var decryptError: String?

    public init(store: ThingsbookStore) {
        self.store = store
    }

    private var toolbarMetrics: MacToolbarLayoutMetrics {
        MacToolbarLayoutMetrics.compute(
            detailWidth: Double(detailPaneWidth),
            isSidebarCollapsed: columnVisibility == .detailOnly
        )
    }

    public var body: some View {
        ZStack {
            NavigationSplitView(columnVisibility: $columnVisibility) {
                SidebarView(store: store)
                    .navigationSplitViewColumnWidth(min: 232, ideal: 262, max: 330)
            } detail: {
                ZStack(alignment: .bottom) {
                    VStack(spacing: 0) {
                        Rectangle()
                            .fill(Color(nsColor: .separatorColor).opacity(0.65))
                            .frame(height: 1)

                        if let focus = store.focusTimer {
                            focusTimerBanner(focus)
                                .padding(.horizontal, 36)
                                .padding(.top, 14)
                                .padding(.bottom, 4)
                        }
                        SmartViewCanvas(store: store)
                            .clipped()
                    }
                    .background(
                        ThingsMacPalette.canvasBackground
                            .ignoresSafeArea(.container, edges: .top)
                    )
                    .background(MacWindowTitlebarConfigurator())
                    .background(
                        GeometryReader { geo in
                            Color.clear
                                .preference(
                                    key: DetailPaneWidthPreferenceKey.self,
                                    value: geo.size.width
                                )
                                .onChange(of: geo.size.width, initial: true) { _, newWidth in
                                    if newWidth > 200, abs(newWidth - detailPaneWidth) >= 2 {
                                        detailPaneWidth = newWidth
                                    }
                                }
                        }
                    )

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
                .onPreferenceChange(DetailPaneWidthPreferenceKey.self) { newWidth in
                    if newWidth > 200, abs(newWidth - detailPaneWidth) >= 2 {
                        detailPaneWidth = newWidth
                    }
                }
                .toolbarBackground(.hidden, for: .windowToolbar)
                .toolbar {
                    ToolbarItem(placement: .primaryAction) {
                        unifiedDetailToolbar(metrics: toolbarMetrics)
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
        .sheet(isPresented: $store.isWeeklyReviewPresented) {
            WeeklyReviewSheetView()
                .environmentObject(store)
        }
        .sheet(isPresented: $store.isTemplatesSheetPresented) {
            TemplatesAndImportSheetView()
                .environmentObject(store)
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
        .sheet(item: $store.previewingAttachment) { att in
            AttachmentPreviewSheet(store: store, attachment: att)
        }
        .alert(
            store.pendingProjectDeletion.map { store.trf("project_delete_prompt_title", $0.project.title) } ?? "",
            isPresented: Binding(
                get: { store.pendingProjectDeletion != nil },
                set: { if !$0 { store.pendingProjectDeletion = nil } }
            ),
            presenting: store.pendingProjectDeletion
        ) { prompt in
            Button(store.tr("project_delete_with_tasks_btn"), role: .destructive) {
                store.confirmDeleteProject(prompt.project, deleteAssociatedTasks: true)
            }
            Button(store.tr("project_delete_keep_tasks_btn")) {
                store.confirmDeleteProject(prompt.project, deleteAssociatedTasks: false)
            }
            Button(store.tr("action_cancel"), role: .cancel) {
                store.pendingProjectDeletion = nil
            }
        } message: { prompt in
            Text(store.trf("project_delete_prompt_tasks_body", prompt.taskCount))
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
                withAnimation(.spring(response: 0.24, dampingFraction: 0.76)) {
                    isMagicPlusMenuOpen.toggle()
                }
            }
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(.white)
                .rotationEffect(.degrees(isMagicPlusMenuOpen ? 45 : 0))
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
                        .brightness(isMagicPlusHovered ? 0.06 : 0.0)
                        .overlay(
                            Circle()
                                .strokeBorder(Color.white.opacity(isMagicPlusHovered ? 0.30 : 0.0), lineWidth: 1)
                        )
                        .shadow(
                            color: ThingsMacPalette.accentBlue.opacity(isMagicPlusHovered ? 0.50 : 0.36),
                            radius: isMagicPlusHovered ? 15 : 12,
                            x: 0,
                            y: isMagicPlusHovered ? 7 : 5
                        )
                )
                .scaleEffect(isMagicPlusHovered ? 1.06 : 1.0)
                .offset(y: (isMagicPlusHovered && !isMagicPlusMenuOpen) ? -1.5 : 0)
                .animation(.spring(response: 0.22, dampingFraction: 0.72), value: isMagicPlusHovered)
                .animation(.spring(response: 0.24, dampingFraction: 0.76), value: isMagicPlusMenuOpen)
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            isMagicPlusHovered = hovering
            if hovering {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
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

                magicPlusItem(
                    icon: "mic.fill",
                    tint: ThingsMacPalette.geminiPurple,
                    title: store.tr("shortcut_voice_capture_short"),
                    subtitle: store.tr("voice_smart_parse_gemini"),
                    shortcut: "⌃⇧⌘V"
                ) {
                    isMagicPlusMenuOpen = false
                    store.quickEntryPrefillTitle = ""
                    store.quickEntryPrefillNotes = ""
                    store.quickEntryAutoStartVoice = true
                    store.isQuickEntryPresented = true
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
                    icon: "square.on.square",
                    tint: ThingsMacPalette.anytimeTeal,
                    title: store.tr("v2_templates_title"),
                    subtitle: store.tr("v2_import_markdown_title"),
                    shortcut: nil
                ) {
                    isMagicPlusMenuOpen = false
                    store.templatesInitialTab = .gallery
                    store.isTemplatesSheetPresented = true
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
        MagicPlusPopoverRow(
            icon: icon,
            tint: tint,
            title: title,
            subtitle: subtitle,
            shortcut: shortcut,
            action: action
        )
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
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(Color.white.opacity(0.14), lineWidth: 0.75)
                )
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
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(Color.white.opacity(0.14), lineWidth: 0.75)
                )
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
            .background(
                Capsule(style: .continuous)
                    .fill(Color.black.opacity(0.82))
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(Color.white.opacity(0.14), lineWidth: 0.75)
                    )
            )
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

    private var languageSelectorPopover: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(AppLanguage.orderedMenuCases) { lang in
                let isSelected = store.preferences.language == lang
                Button {
                    store.preferences.language = lang
                    isLanguagePopoverOpen = false
                } label: {
                    HStack(spacing: 10) {
                        Text(lang.flagEmoji)
                            .font(.system(size: 16))
                            .frame(width: 22, alignment: .center)

                        Text(toolbarLanguageTitle(for: lang))
                            .font(.system(size: 13, weight: isSelected ? .semibold : .regular))
                            .foregroundStyle(.primary)

                        Spacer(minLength: 14)

                        if isSelected {
                            Image(systemName: "checkmark")
                                .font(.system(size: 11.5, weight: .bold))
                                .foregroundStyle(ThingsMacPalette.accentBlue)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6.5)
                    .frame(minWidth: 186, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .fill(isSelected ? ThingsMacPalette.accentBlue.opacity(0.12) : Color.clear)
                    )
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if lang == .system {
                    Divider()
                        .padding(.vertical, 3)
                }
            }
        }
        .padding(8)
    }

    private func toolbarLanguageTitle(for lang: AppLanguage) -> String {
        switch lang {
        case .system: return store.tr("settings_system_default")
        case .en: return store.tr("lang_en")
        case .de: return store.tr("lang_de")
        case .it: return store.tr("lang_it")
        case .fr: return store.tr("lang_fr")
        case .es: return store.tr("lang_es")
        }
    }

    private var syncDotColor: Color {
        if store.preferences.localOnlyDbMode { return .secondary.opacity(0.6) }
        if store.syncSnapshot.errorMessage != nil { return ThingsMacPalette.deadlineCrimson }
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
        if let err = store.syncSnapshot.errorMessage, !err.isEmpty {
            return err
        }
        switch store.syncSnapshot.status {
        case .synced: return store.tr("sync_status_synced")
        case .syncing: return store.tr("sync_status_syncing")
        case .offline: return store.tr("sync_status_offline")
        }
    }

    // MARK: - Adaptive Top Toolbar Controls

    private func unifiedDetailToolbar(metrics: MacToolbarLayoutMetrics) -> some View {
        HStack(spacing: 0) {
            quickFindToolbarButton(metrics: metrics)
            Color.clear
                .frame(width: CGFloat(metrics.centerGap), height: 1)
            detailToolbarActionsBar(metrics: metrics)
        }
        .fixedSize(horizontal: true, vertical: false)
    }

    private func quickFindToolbarButton(metrics: MacToolbarLayoutMetrics) -> some View {
        Button {
            guard !store.isLocked else { return }
            store.isQuickFindPresented = true
        } label: {
            HStack(spacing: 7) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12.5, weight: .medium))
                    .foregroundStyle(isQuickFindHovered ? ThingsMacPalette.accentBlue : .secondary)

                Text(store.tr("quick_find_title"))
                    .font(.system(size: 12.5, weight: .regular))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Spacer(minLength: 4)

                if metrics.showSearchShortcutBadge {
                    Text("⌘F")
                        .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                        .foregroundStyle(.tertiary)
                        .padding(.horizontal, 5.5)
                        .padding(.vertical, 1.5)
                        .background(
                            RoundedRectangle(cornerRadius: 4, style: .continuous)
                                .fill(Color.primary.opacity(0.06))
                        )
                }
            }
            .padding(.horizontal, 10)
            .frame(width: CGFloat(metrics.searchWidth), height: CGFloat(metrics.buttonHeight))
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(ThingsMacPalette.cardSurface.opacity(isQuickFindHovered ? 1.0 : 0.88))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .strokeBorder(
                        isQuickFindHovered
                            ? ThingsMacPalette.accentBlue.opacity(0.45)
                            : Color.primary.opacity(0.14),
                        lineWidth: 1
                    )
            )
            .shadow(color: Color.black.opacity(0.03), radius: 1.5, x: 0, y: 0.5)
            .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
        .fixedSize(horizontal: true, vertical: false)
        .disabled(store.isLocked)
        .help(store.tr("quick_find_title") + " (⌘F)")
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isQuickFindHovered = hovering
            }
            if hovering && !store.isLocked {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }

    private func detailToolbarActionsBar(metrics: MacToolbarLayoutMetrics) -> some View {
        let btnW = CGFloat(metrics.buttonWidth)
        let btnH = CGFloat(metrics.buttonHeight)
        let spacing = CGFloat(metrics.itemSpacing)

        return HStack(spacing: 0) {
            // Group 1: Intelligence & Review Planning
            HStack(spacing: spacing) {
                MacToolbarGeminiButton(
                    minWidth: CGFloat(metrics.geminiPillMinWidth),
                    height: btnH,
                    isDisabled: store.isLocked,
                    helpText: store.tr("gemini_hub_title") + " (⌘G)"
                ) {
                    guard !store.isLocked else { return }
                    store.geminiInitialTab = .brainDump
                    store.isGeminiSheetPresented = true
                }

                MacToolbarIconButton(
                    width: btnW,
                    height: btnH,
                    isDisabled: store.isLocked,
                    helpText: store.tr("v2_weekly_review_title") + " (⇧⌘R)",
                    action: {
                        guard !store.isLocked else { return }
                        store.isWeeklyReviewPresented = true
                    }
                ) {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "checklist.checked")
                            .font(.system(size: 14, weight: .medium))
                        if !store.stalledProjects.isEmpty {
                            Circle()
                                .fill(ThingsMacPalette.accentBlue)
                                .frame(width: 6, height: 6)
                                .offset(x: 3.5, y: -2.5)
                        }
                    }
                }

                MacToolbarIconButton(
                    systemImage: "square.on.square",
                    width: btnW,
                    height: btnH,
                    isDisabled: store.isLocked,
                    helpText: store.tr("v2_templates_title")
                ) {
                    guard !store.isLocked else { return }
                    store.templatesInitialTab = .gallery
                    store.isTemplatesSheetPresented = true
                }
            }

            toolbarGroupDivider(metrics: metrics)

            // Group 2: Quick Capture & Organization
            HStack(spacing: spacing) {
                MacToolbarIconButton(
                    systemImage: "tray.and.arrow.down",
                    width: btnW,
                    height: btnH,
                    isDisabled: store.isLocked,
                    helpText: store.tr("quick_share_title") + " (⇧⌘N)"
                ) {
                    guard !store.isLocked else { return }
                    store.quickEntryPrefillTitle = ""
                    store.quickEntryPrefillNotes = ""
                    store.quickEntryAutoStartVoice = false
                    store.isQuickEntryPresented = true
                }

                MacToolbarIconButton(
                    systemImage: "mic",
                    width: btnW,
                    height: btnH,
                    isDisabled: store.isLocked,
                    helpText: store.tr("voice_capture_btn") + " (⌃⇧⌘V)"
                ) {
                    guard !store.isLocked else { return }
                    store.quickEntryPrefillTitle = ""
                    store.quickEntryPrefillNotes = ""
                    store.quickEntryAutoStartVoice = true
                    store.isQuickEntryPresented = true
                }

                MacToolbarIconButton(
                    systemImage: "cube",
                    width: btnW,
                    height: btnH,
                    isDisabled: store.isLocked,
                    helpText: store.tr("areas_manage_title")
                ) {
                    guard !store.isLocked else { return }
                    store.isAreaManagerPresented = true
                }

                MacToolbarIconButton(
                    systemImage: "tag",
                    width: btnW,
                    height: btnH,
                    isDisabled: store.isLocked,
                    helpText: store.tr("tags_manage_title")
                ) {
                    guard !store.isLocked else { return }
                    store.isTagManagerPresented = true
                }
            }

            toolbarGroupDivider(metrics: metrics)

            // Group 3: Workspace Status & Preferences
            HStack(spacing: spacing) {
                MacToolbarIconButton(
                    systemImage: store.preferences.dailyDigestEnabled ? "bell.badge.fill" : "bell",
                    width: btnW,
                    height: btnH,
                    isActive: store.preferences.dailyDigestEnabled,
                    isDisabled: store.isLocked,
                    helpText: store.tr("notifications_title")
                ) {
                    guard !store.isLocked else { return }
                    store.settingsTab = .notifications
                    store.isSettingsPresented = true
                }

                MacToolbarIconButton(
                    width: btnW,
                    height: btnH,
                    isActive: isLanguagePopoverOpen,
                    isDisabled: store.isLocked,
                    helpText: store.tr("settings_language"),
                    action: {
                        guard !store.isLocked else { return }
                        isLanguagePopoverOpen.toggle()
                    }
                ) {
                    if store.preferences.language == .system {
                        Image(systemName: "globe")
                            .font(.system(size: 14, weight: .medium))
                    } else {
                        Text(store.preferences.language.flagEmoji)
                            .font(.system(size: 15))
                    }
                }
                .popover(isPresented: $isLanguagePopoverOpen, arrowEdge: .bottom) {
                    languageSelectorPopover
                }

                MacToolbarIconButton(
                    width: CGFloat(metrics.syncButtonWidth),
                    height: btnH,
                    isDisabled: store.isLocked,
                    helpText: syncHelpText,
                    action: {
                        guard !store.isLocked else { return }
                        if !store.preferences.localOnlyDbMode && store.syncSnapshot.userEmailOrId != nil {
                            store.syncNow()
                        }
                        store.settingsTab = .sync
                        store.isSettingsPresented = true
                    }
                ) {
                    HStack(spacing: 4.5) {
                        Image(systemName: store.preferences.localOnlyDbMode ? "internaldrive" : "icloud")
                            .font(.system(size: 14, weight: .medium))
                        Circle()
                            .fill(syncDotColor)
                            .frame(width: 6.5, height: 6.5)
                    }
                }

                MacToolbarIconButton(
                    systemImage: "gearshape",
                    width: btnW,
                    height: btnH,
                    isDisabled: store.isLocked,
                    helpText: store.tr("settings_title") + " (⌘,)"
                ) {
                    guard !store.isLocked else { return }
                    store.settingsTab = .general
                    store.isSettingsPresented = true
                }
            }
        }
        .padding(.trailing, 4)
        .fixedSize(horizontal: true, vertical: false)
    }

    private func toolbarGroupDivider(metrics: MacToolbarLayoutMetrics) -> some View {
        Rectangle()
            .fill(Color.primary.opacity(0.11))
            .frame(width: 1, height: 16)
            .padding(.horizontal, CGFloat(metrics.groupDividerPadding))
    }
}

private struct DetailPaneWidthPreferenceKey: PreferenceKey {
    static let defaultValue: CGFloat = 880

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        let next = nextValue()
        if next > 0 {
            value = next
        }
    }
}

private struct MacToolbarGeminiButton: View {
    let minWidth: CGFloat
    let height: CGFloat
    let isDisabled: Bool
    let helpText: String
    let action: () -> Void

    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5.5) {
                Image(systemName: "sparkles")
                    .font(.system(size: 12, weight: .semibold))
                Text("Gemini")
                    .font(.system(size: 12.5, weight: .semibold))
                    .lineLimit(1)
            }
            .foregroundStyle(ThingsMacPalette.geminiPurple)
            .padding(.horizontal, 11)
            .frame(minWidth: minWidth, minHeight: height, maxHeight: height)
            .background(
                Capsule(style: .continuous)
                    .fill(ThingsMacPalette.geminiPurple.opacity(isHovered ? 0.21 : 0.13))
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(ThingsMacPalette.geminiPurple.opacity(isHovered ? 0.28 : 0.14), lineWidth: 0.75)
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .help(helpText)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovered = hovering
            }
            if hovering && !isDisabled {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }
}

private struct ToolbarSymbolIcon: View {
    let systemImage: String

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 14, weight: .medium))
    }
}

private struct MacToolbarIconButton<Label: View>: View {
    let width: CGFloat
    let height: CGFloat
    let isActive: Bool
    let isDisabled: Bool
    let helpText: String
    let action: () -> Void
    let label: Label

    @State private var isHovered = false

    init(
        width: CGFloat,
        height: CGFloat,
        isActive: Bool = false,
        isDisabled: Bool = false,
        helpText: String,
        action: @escaping () -> Void,
        @ViewBuilder label: () -> Label
    ) {
        self.width = width
        self.height = height
        self.isActive = isActive
        self.isDisabled = isDisabled
        self.helpText = helpText
        self.action = action
        self.label = label()
    }

    var body: some View {
        Button(action: action) {
            label
                .foregroundStyle(
                    isActive
                        ? ThingsMacPalette.accentBlue
                        : (isHovered ? Color.primary : Color.primary.opacity(0.78))
                )
                .frame(width: width, height: height)
                .background(
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(
                            isActive
                                ? ThingsMacPalette.accentBlue.opacity(isHovered ? 0.16 : 0.10)
                                : Color.primary.opacity(isHovered ? 0.075 : 0.0)
                        )
                )
                .contentShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .help(helpText)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovered = hovering
            }
            if hovering && !isDisabled {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }
}

extension MacToolbarIconButton where Label == ToolbarSymbolIcon {
    init(
        systemImage: String,
        width: CGFloat,
        height: CGFloat,
        isActive: Bool = false,
        isDisabled: Bool = false,
        helpText: String,
        action: @escaping () -> Void
    ) {
        self.init(
            width: width,
            height: height,
            isActive: isActive,
            isDisabled: isDisabled,
            helpText: helpText,
            action: action
        ) {
            ToolbarSymbolIcon(systemImage: systemImage)
        }
    }
}

private struct MacWindowTitlebarConfigurator: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView {
        TitlebarSyncNSView()
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        (nsView as? TitlebarSyncNSView)?.applyWindowStyle()
    }

    private final class TitlebarSyncNSView: NSView {
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            applyWindowStyle()
        }

        override func layout() {
            super.layout()
            applyWindowStyle()
        }

        func applyWindowStyle() {
            guard let window else { return }
            if !window.titlebarAppearsTransparent {
                window.titlebarAppearsTransparent = true
            }
            if window.titlebarSeparatorStyle != .none {
                window.titlebarSeparatorStyle = .none
            }
        }
    }
}

private struct MagicPlusPopoverRow: View {
    let icon: String
    let tint: Color
    let title: String
    let subtitle: String
    let shortcut: String?
    let action: () -> Void

    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 11) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 28, height: 28)
                    .background(
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .fill(tint.opacity(isHovered ? 0.18 : 0.12))
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
            .background(
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(isHovered ? ThingsMacPalette.accentBlue.opacity(0.09) : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovered = hovering
            }
            if hovering {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }
}


