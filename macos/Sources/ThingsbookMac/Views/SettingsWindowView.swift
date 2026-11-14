import SwiftUI
import AppKit
import ThingsbookCore

public struct SettingsWindowView: View {
    @ObservedObject var store: ThingsbookStore
    public var showCloseButton: Bool = false
    @Environment(\.dismiss) private var dismiss

    // Cloud Sync form state
    @State private var emailInput = ""
    @State private var passwordInput = ""
    @State private var mergeLocalOnLogin = true
    @State private var authMessage: String?
    @State private var isAuthBusy = false

    // Custom Firebase Config state
    @State private var showCustomFirebase = false
    @State private var customProjectId = ""
    @State private var customApiKey = ""
    @State private var customAppId = ""

    // Backup encryption password
    @State private var protectBackupWithPassword = false
    @State private var backupPassword = ""
    @State private var backupPasswordConfirm = ""
    @State private var backupValidationError: String?

    // Updates state
    @State private var isCheckingUpdate = false
    @State private var updateStatusText: String?

    public init(store: ThingsbookStore, showCloseButton: Bool = false) {
        self.store = store
        self.showCloseButton = showCloseButton
    }

    public var body: some View {
        VStack(spacing: 0) {
            if showCloseButton {
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(store.tr("settings_title"))
                            .font(.system(size: 16, weight: .bold))
                        Text(store.tr("settings_subtitle"))
                            .font(.system(size: 11.5))
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 18))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .help(store.tr("settings_close_tooltip"))
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 8)
            }

            TabView(selection: $store.settingsTab) {
                generalTab
                    .tabItem { Label(store.tr("macos_tab_general"), systemImage: "gearshape") }
                    .tag(SettingsSectionTab.general)

                syncTab
                    .tabItem { Label(store.tr("macos_tab_sync"), systemImage: "icloud") }
                    .tag(SettingsSectionTab.sync)

                calendarTab
                    .tabItem { Label(store.tr("macos_tab_calendar"), systemImage: "calendar") }
                    .tag(SettingsSectionTab.calendar)

                notificationsTab
                    .tabItem { Label(store.tr("macos_tab_notifications"), systemImage: "bell.badge") }
                    .tag(SettingsSectionTab.notifications)

                backupTab
                    .tabItem { Label(store.tr("macos_tab_backup"), systemImage: "externaldrive.badge.timemachine") }
                    .tag(SettingsSectionTab.backup)

                aboutTab
                    .tabItem { Label(store.tr("macos_tab_updates"), systemImage: "arrow.triangle.2.circlepath.circle") }
                    .tag(SettingsSectionTab.about)
            }
            .padding(16)
        }
        .frame(width: 680, height: 540)
        .background(ThingsMacPalette.canvasBackground)
        .onAppear {
            let active = FirebaseConfig.loadActive()
            if active.isCustom {
                customProjectId = active.projectId
                customApiKey = active.apiKey
                customAppId = active.appId
                showCustomFirebase = true
            }
            if store.preferences.calendarEventsEnabled {
                store.availableCalendars = AppleCalendarService.shared.listCalendars()
            }
        }
    }

    // MARK: - Tab 1: General & Keyboard Shortcuts

    private var generalTab: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // Language Card
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Label(store.tr("settings_language"), systemImage: "globe")
                            .font(.system(size: 13.5, weight: .semibold))
                        Spacer()
                        Picker("", selection: $store.preferences.language) {
                            Text(store.tr("settings_system_default")).tag(AppLanguage.system)
                            Divider()
                            Text(store.tr("lang_en")).tag(AppLanguage.en)
                            Text(store.tr("lang_de")).tag(AppLanguage.de)
                            Text(store.tr("lang_fr")).tag(AppLanguage.fr)
                            Text(store.tr("lang_it")).tag(AppLanguage.it)
                            Text(store.tr("lang_es")).tag(AppLanguage.es)
                        }
                        .labelsHidden()
                        .frame(width: 200)
                        .onChange(of: store.preferences.language) { _, newLang in
                            NotificationReminderService.shared.configureCategories(lang: newLang)
                        }
                    }
                }
                .padding(14)
                .thingsCardSurface(cornerRadius: 11)

                // Workflow & Security Card
                VStack(alignment: .leading, spacing: 12) {
                    Text(store.tr("settings_workflow_security_section"))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.secondary)

                    HStack(alignment: .top, spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(store.tr("settings_group_today_title"))
                                .font(.system(size: 13.5, weight: .medium))
                            Text(store.tr("settings_group_today_desc"))
                                .font(.system(size: 11.5))
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Toggle("", isOn: $store.preferences.groupTodayByProjectOrArea)
                            .toggleStyle(.switch)
                            .labelsHidden()
                    }

                    Divider()

                    HStack(alignment: .top, spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(store.tr("macos_touch_id_title"))
                                .font(.system(size: 13.5, weight: .medium))
                            Text(store.tr("macos_touch_id_desc"))
                                .font(.system(size: 11.5))
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Toggle("", isOn: $store.preferences.biometricLockEnabled)
                            .toggleStyle(.switch)
                            .labelsHidden()
                    }
                }
                .padding(14)
                .thingsCardSurface(cornerRadius: 11)

                // Pre-installed Sample Tasks Purge Card
                if store.hasSampleTasks {
                    HStack(spacing: 12) {
                        Image(systemName: "sparkles.rectangle.stack")
                            .font(.system(size: 18))
                            .foregroundStyle(ThingsMacPalette.somedayAmber)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(store.tr("settings_sample_tasks_title"))
                                .font(.system(size: 13, weight: .semibold))
                            Text(store.tr("settings_sample_tasks_desc"))
                                .font(.system(size: 11.5))
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button(role: .destructive) {
                            try? store.database.purgeSampleDataLocally(preserveUsedContainers: true)
                        } label: {
                            Text(store.tr("setup_banner_delete_samples_btn"))
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                    }
                    .padding(14)
                    .thingsCardSurface(cornerRadius: 11)
                }

                // Keyboard Shortcuts Cheat Sheet
                VStack(alignment: .leading, spacing: 10) {
                    Label(store.tr("macos_shortcuts_section"), systemImage: "keyboard")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.secondary)

                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)], spacing: 7) {
                        shortcutRow(title: store.tr("entity_new_todo"), keys: "⌘N")
                        shortcutRow(title: store.tr("macos_menubar_quick_entry"), keys: "⇧⌘N")
                        shortcutRow(title: store.tr("entity_new_project"), keys: "⌥⌘N")
                        shortcutRow(title: store.tr("entity_new_area"), keys: "⌃⌘N")
                        shortcutRow(title: store.tr("action_complete"), keys: "⌘K")
                        shortcutRow(title: store.tr("action_duplicate_todo"), keys: "⌘D")
                        shortcutRow(title: store.tr("view_today"), keys: "⌘T")
                        shortcutRow(title: store.tr("view_this_evening"), keys: "⌘E")
                        shortcutRow(title: store.tr("view_someday"), keys: "⌘O")
                        shortcutRow(title: store.tr("quick_find_title"), keys: "⌘F")
                        shortcutRow(title: store.tr("gemini_hub_title"), keys: "⌘G")
                        shortcutRow(title: store.tr("action_focus_timer"), keys: "⌥⌘F")
                        shortcutRow(title: store.tr("action_undo"), keys: "⌘Z")
                        shortcutRow(title: store.tr("action_delete"), keys: "⌘⌫")
                    }
                }
                .padding(14)
                .thingsCardSurface(cornerRadius: 11)
            }
            .padding(6)
        }
    }

    private func shortcutRow(title: String, keys: String) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 12))
                .lineLimit(1)
            Spacer()
            Text(keys)
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .fill(Color.primary.opacity(0.06))
                )
        }
    }

    // MARK: - Tab 2: Database & Cloud Sync

    private var syncTab: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(store.tr("settings_db_sync_section"))
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.secondary)

                // Local DB vs Cloud Sync Mode Selector Cards (matching Android Settings)
                HStack(spacing: 12) {
                    dbModeCard(
                        selected: store.preferences.localOnlyDbMode,
                        icon: "internaldrive.fill",
                        title: store.tr("settings_local_db_title"),
                        subtitle: store.tr("settings_local_db_desc")
                    ) {
                        store.preferences.localOnlyDbMode = true
                    }

                    dbModeCard(
                        selected: !store.preferences.localOnlyDbMode,
                        icon: "icloud.fill",
                        title: store.tr("settings_cloud_sync_title"),
                        subtitle: store.tr("settings_cloud_sync_desc")
                    ) {
                        store.preferences.localOnlyDbMode = false
                        store.syncNow()
                    }
                }

                if let userEmail = store.syncSnapshot.userEmailOrId {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 12) {
                            Image(systemName: "person.crop.circle.badge.checkmark")
                                .font(.system(size: 24))
                                .foregroundStyle(ThingsMacPalette.logbookGreen)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(userEmail)
                                    .font(.system(size: 14, weight: .semibold))
                                if store.syncSnapshot.lastSyncedAt > 0 {
                                    let formattedDate = DateEpochConverter.date(fromMillis: store.syncSnapshot.lastSyncedAt).formatted()
                                    Text(store.trf("macos_last_synced", formattedDate))
                                        .font(.system(size: 11.5))
                                        .foregroundStyle(.secondary)
                                } else {
                                    Text(store.tr("sync_status_synced"))
                                        .font(.system(size: 11.5))
                                        .foregroundStyle(.secondary)
                                }
                            }

                            Spacer()

                            Button(store.tr("sync_action_now")) {
                                store.syncNow()
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(store.preferences.localOnlyDbMode)

                            Button(store.tr("auth_sign_out")) {
                                Task { await store.cloudSync.signOut() }
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(14)
                    .thingsCardSurface(cornerRadius: 11)
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(store.tr("sync_title"))
                            .font(.system(size: 14, weight: .semibold))

                        Text(store.tr("auth_multi_device_hint"))
                            .font(.system(size: 11.5))
                            .foregroundStyle(.secondary)

                        TextField(store.tr("auth_email_label"), text: $emailInput)
                            .textFieldStyle(.roundedBorder)

                        SecureField(store.tr("auth_password_label"), text: $passwordInput)
                            .textFieldStyle(.roundedBorder)

                        Toggle(store.tr("merge_dialog_confirm_merge"), isOn: $mergeLocalOnLogin)
                            .font(.system(size: 12))

                        if let err = authMessage ?? store.syncSnapshot.errorMessage {
                            Text(err)
                                .font(.system(size: 11.5))
                                .foregroundStyle(.red)
                        }

                        HStack(spacing: 8) {
                            Button(store.tr("auth_login_email")) {
                                performAuth {
                                    store.preferences.localOnlyDbMode = false
                                    _ = try await store.cloudSync.signInEmail(
                                        email: emailInput,
                                        password: passwordInput,
                                        mergeLocalTasks: mergeLocalOnLogin,
                                        localOnlyMode: false
                                    )
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(isAuthBusy || emailInput.trimmingCharacters(in: .whitespaces).isEmpty)

                            Button(store.tr("auth_register_account")) {
                                performAuth {
                                    store.preferences.localOnlyDbMode = false
                                    _ = try await store.cloudSync.registerEmail(
                                        email: emailInput,
                                        password: passwordInput,
                                        mergeLocalTasks: mergeLocalOnLogin,
                                        localOnlyMode: false
                                    )
                                }
                            }
                            .buttonStyle(.bordered)
                            .disabled(isAuthBusy || emailInput.trimmingCharacters(in: .whitespaces).isEmpty)

                            Button(store.tr("macos_instant_sync_btn")) {
                                performAuth {
                                    store.preferences.localOnlyDbMode = false
                                    _ = try await store.cloudSync.signInInstantEmail(
                                        email: emailInput,
                                        mergeLocalTasks: mergeLocalOnLogin,
                                        localOnlyMode: false
                                    )
                                }
                            }
                            .buttonStyle(.bordered)
                            .disabled(isAuthBusy || emailInput.trimmingCharacters(in: .whitespaces).isEmpty)

                            Spacer()

                            Button(store.tr("auth_forgot_password")) {
                                performAuth {
                                    try await store.cloudSync.sendPasswordResetEmail(email: emailInput)
                                    await MainActor.run {
                                        authMessage = store.tr("macos_reset_email_sent")
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                            .font(.system(size: 11.5))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                            .disabled(emailInput.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                    }
                    .padding(14)
                    .thingsCardSurface(cornerRadius: 11)
                }

                DisclosureGroup(store.tr("auth_custom_firebase_toggle"), isExpanded: $showCustomFirebase) {
                    VStack(alignment: .leading, spacing: 8) {
                        TextField(store.tr("auth_firebase_project_id"), text: $customProjectId)
                            .textFieldStyle(.roundedBorder)
                        TextField(store.tr("auth_firebase_api_key"), text: $customApiKey)
                            .textFieldStyle(.roundedBorder)
                        TextField(store.tr("auth_firebase_app_id"), text: $customAppId)
                            .textFieldStyle(.roundedBorder)
                        HStack {
                            Button(store.tr("auth_firebase_apply")) {
                                FirebaseConfig.saveCustom(
                                    FirebaseConfig(
                                        projectId: customProjectId,
                                        apiKey: customApiKey,
                                        appId: customAppId
                                    )
                                )
                                store.showToast(store.tr("macos_firebase_saved_toast"))
                            }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.small)
                            .disabled(customProjectId.isEmpty || customApiKey.isEmpty)

                            Button(store.tr("macos_firebase_reset_default")) {
                                FirebaseConfig.saveCustom(nil)
                                customProjectId = ""
                                customApiKey = ""
                                customAppId = ""
                                store.showToast(store.tr("macos_firebase_restored_toast"))
                            }
                            .controlSize(.small)
                        }
                    }
                    .padding(.top, 8)
                }
                .font(.system(size: 12, weight: .medium))
                .padding(12)
                .thingsCardSurface(cornerRadius: 11)
            }
            .padding(6)
        }
    }

    private func dbModeCard(
        selected: Bool,
        icon: String,
        title: String,
        subtitle: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Image(systemName: icon)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .secondary)
                    Spacer()
                    Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .secondary.opacity(0.5))
                }
                Text(title)
                    .font(.system(size: 13.5, weight: .semibold))
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .fill(selected ? ThingsMacPalette.accentBlue.opacity(0.08) : ThingsMacPalette.cardSurface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .strokeBorder(
                                selected ? ThingsMacPalette.accentBlue : Color.primary.opacity(0.08),
                                lineWidth: selected ? 1.5 : 1
                            )
                    )
            )
        }
        .buttonStyle(.plain)
    }

    private func performAuth(_ work: @escaping () async throws -> Void) {
        isAuthBusy = true
        authMessage = nil
        Task {
            do {
                try await work()
                await MainActor.run {
                    isAuthBusy = false
                    store.reloadSnapshot()
                }
            } catch {
                await MainActor.run {
                    isAuthBusy = false
                    authMessage = error.localizedDescription
                }
            }
        }
    }

    // MARK: - Tab 3: Apple Calendar & Google Calendar (EventKit)

    private var selectedCalendarCount: Int {
        if store.preferences.selectedCalendarIds.isEmpty {
            return store.availableCalendars.count
        }
        let validIds = Set(store.availableCalendars.map(\.id))
        return store.preferences.selectedCalendarIds.filter { validIds.contains($0) }.count
    }

    private var calendarTab: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 10) {
                Text(store.tr("macos_calendar_section"))
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.secondary)

                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(store.tr("settings_calendar_enable_title"))
                            .font(.system(size: 13.5, weight: .semibold))
                        Text(store.tr("macos_calendar_enable_desc"))
                            .font(.system(size: 11.5))
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Toggle("", isOn: $store.preferences.calendarEventsEnabled)
                        .toggleStyle(.switch)
                        .labelsHidden()
                        .onChange(of: store.preferences.calendarEventsEnabled) { _, enabled in
                            if enabled {
                                Task { await store.refreshCalendarEvents() }
                            } else {
                                store.calendarEvents = []
                            }
                        }
                }
            }
            .padding(14)
            .thingsCardSurface(cornerRadius: 11)

            if store.preferences.calendarEventsEnabled {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 8) {
                        Text(store.trf("settings_calendar_select_hint", selectedCalendarCount, store.availableCalendars.count))
                            .font(.system(size: 12.5, weight: .semibold))
                        Spacer()
                        Button(store.tr("settings_calendar_select_all")) {
                            store.preferences.selectedCalendarIds = store.availableCalendars.map(\.id)
                            Task { await store.refreshCalendarEvents() }
                        }
                        .controlSize(.small)

                        Button(store.tr("settings_calendar_select_none")) {
                            store.preferences.selectedCalendarIds = ["__none__"]
                            store.calendarEvents = []
                        }
                        .controlSize(.small)

                        Button(store.tr("macos_calendar_refresh")) {
                            Task { await store.refreshCalendarEvents() }
                        }
                        .controlSize(.small)
                    }

                    if store.availableCalendars.isEmpty {
                        Text(store.tr("settings_calendar_none_found"))
                            .font(.system(size: 12.5))
                            .foregroundStyle(.secondary)
                            .padding(.vertical, 16)
                    } else {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 6) {
                                ForEach(store.availableCalendars) { cal in
                                    let isSelected = store.preferences.selectedCalendarIds.isEmpty
                                        || store.preferences.selectedCalendarIds.contains(cal.id)
                                    HStack(spacing: 10) {
                                        Circle()
                                            .fill(ThingsMacPalette.colorFromArgb(cal.colorHex))
                                            .frame(width: 10, height: 10)
                                        Text(cal.title)
                                            .font(.system(size: 13, weight: .medium))
                                        Text("(\(cal.sourceTitle))")
                                            .font(.system(size: 11.5))
                                            .foregroundStyle(.secondary)
                                        Spacer()
                                        Toggle("", isOn: Binding(
                                            get: { isSelected },
                                            set: { checked in
                                                var current = store.preferences.selectedCalendarIds.isEmpty
                                                    ? store.availableCalendars.map(\.id)
                                                    : store.preferences.selectedCalendarIds.filter { $0 != "__none__" }
                                                if checked {
                                                    if !current.contains(cal.id) { current.append(cal.id) }
                                                } else {
                                                    current.removeAll { $0 == cal.id }
                                                    if current.isEmpty { current = ["__none__"] }
                                                }
                                                store.preferences.selectedCalendarIds = current
                                                Task { await store.refreshCalendarEvents() }
                                            }
                                        ))
                                        .labelsHidden()
                                    }
                                    .padding(.vertical, 4)
                                }
                            }
                        }
                    }
                }
                .padding(14)
                .thingsCardSurface(cornerRadius: 11)
            }
            Spacer()
        }
        .padding(6)
    }

    // MARK: - Tab 4: Notifications & Daily Digest

    private var digestTimeBinding: Binding<Date> {
        Binding<Date>(
            get: {
                var comps = Calendar.current.dateComponents([.year, .month, .day], from: Date())
                comps.hour = store.preferences.dailyDigestHour
                comps.minute = store.preferences.dailyDigestMinute
                return Calendar.current.date(from: comps) ?? Date()
            },
            set: { newDate in
                let comps = Calendar.current.dateComponents([.hour, .minute], from: newDate)
                store.preferences.dailyDigestHour = comps.hour ?? 9
                store.preferences.dailyDigestMinute = comps.minute ?? 0
                store.reloadSnapshot()
            }
        )
    }

    private var notificationsTab: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 12) {
                Text(store.tr("notifications_title"))
                    .font(.system(size: 13.5, weight: .semibold))

                HStack(spacing: 10) {
                    Button {
                        Task {
                            let granted = await NotificationReminderService.shared.requestPermissionIfNeeded()
                            await MainActor.run {
                                store.showToast(store.tr(granted ? "macos_notif_enabled_toast" : "macos_notif_denied_toast"))
                            }
                        }
                    } label: {
                        Label(store.tr("macos_notif_permission_btn"), systemImage: "bell.badge.fill")
                    }
                    .buttonStyle(.borderedProminent)

                    Button {
                        NotificationReminderService.shared.postTestNotification(lang: store.preferences.language)
                        store.showToast(store.tr("macos_test_notif_title"))
                    } label: {
                        Label(store.tr("notifications_test_now"), systemImage: "paperplane.fill")
                    }
                    .buttonStyle(.bordered)
                }

                Text(store.tr("notifications_task_specific_desc"))
                    .font(.system(size: 11.5))
                    .foregroundStyle(.secondary)
            }
            .padding(14)
            .thingsCardSurface(cornerRadius: 11)

            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(store.tr("notifications_daily_digest_title"))
                            .font(.system(size: 13.5, weight: .semibold))
                        Text(store.tr("notifications_daily_digest_desc"))
                            .font(.system(size: 11.5))
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Toggle("", isOn: $store.preferences.dailyDigestEnabled)
                        .toggleStyle(.switch)
                        .labelsHidden()
                        .onChange(of: store.preferences.dailyDigestEnabled) { _, _ in
                            store.reloadSnapshot()
                        }
                }

                if store.preferences.dailyDigestEnabled {
                    Divider()
                    HStack {
                        Text(store.tr("notifications_daily_time_label"))
                            .font(.system(size: 13, weight: .medium))
                        Spacer()
                        DatePicker("", selection: digestTimeBinding, displayedComponents: [.hourAndMinute])
                            .labelsHidden()
                            .frame(width: 96)
                    }
                }
            }
            .padding(14)
            .thingsCardSurface(cornerRadius: 11)

            Spacer()
        }
        .padding(6)
    }

    // MARK: - Tab 5: Backup (.tbbackup), CSV & 9-Tile Statistics

    private var backupTab: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // Full Encrypted Backup (.tbbackup)
                VStack(alignment: .leading, spacing: 10) {
                    Text(store.tr("backup_section_title"))
                        .font(.system(size: 13.5, weight: .semibold))
                    Text(store.tr("backup_section_desc"))
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)

                    Toggle(isOn: $protectBackupWithPassword) {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(store.tr("backup_password_protect_title"))
                                .font(.system(size: 12.5, weight: .medium))
                            Text(store.tr(protectBackupWithPassword ? "backup_password_protect_on" : "backup_password_protect_off"))
                                .font(.system(size: 11))
                                .foregroundStyle(.secondary)
                        }
                    }

                    if protectBackupWithPassword {
                        HStack(spacing: 8) {
                            SecureField(store.tr("backup_password_label"), text: $backupPassword)
                                .textFieldStyle(.roundedBorder)
                            SecureField(store.tr("backup_password_confirm_label"), text: $backupPasswordConfirm)
                                .textFieldStyle(.roundedBorder)
                        }
                        Text(store.tr("backup_password_warning"))
                            .font(.system(size: 11))
                            .foregroundStyle(ThingsMacPalette.somedayAmber)
                    }

                    if let backupValidationError {
                        Text(backupValidationError)
                            .font(.system(size: 11.5))
                            .foregroundStyle(.red)
                    }

                    HStack(spacing: 10) {
                        Button {
                            if protectBackupWithPassword {
                                if backupPassword.count < 4 {
                                    backupValidationError = store.tr("backup_password_err_too_short")
                                    return
                                }
                                if backupPassword != backupPasswordConfirm {
                                    backupValidationError = store.tr("backup_password_err_mismatch")
                                    return
                                }
                                backupValidationError = nil
                                store.exportBackupInteractively(password: backupPassword)
                            } else {
                                backupValidationError = nil
                                store.exportBackupInteractively(password: nil)
                            }
                        } label: {
                            Label(store.tr("backup_create_button"), systemImage: "square.and.arrow.up")
                        }
                        .buttonStyle(.borderedProminent)

                        Button {
                            store.importBackupInteractively()
                        } label: {
                            Label(store.tr("backup_restore_button"), systemImage: "square.and.arrow.down")
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .padding(14)
                .thingsCardSurface(cornerRadius: 11)

                // CSV Import & Export Card
                VStack(alignment: .leading, spacing: 8) {
                    Text(store.tr("settings_csv_section"))
                        .font(.system(size: 13.5, weight: .semibold))
                    Text(store.tr("settings_csv_desc"))
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)

                    HStack(spacing: 10) {
                        Button {
                            store.exportCsvInteractively()
                        } label: {
                            Label(store.tr("settings_csv_export"), systemImage: "tablecells")
                        }
                        .buttonStyle(.bordered)

                        Button {
                            store.importCsvInteractively()
                        } label: {
                            Label(store.tr("settings_csv_import"), systemImage: "tray.and.arrow.down")
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .padding(14)
                .thingsCardSurface(cornerRadius: 11)

                // 9-Tile Live Database Statistics (matching Android Settings)
                VStack(alignment: .leading, spacing: 10) {
                    Text(store.tr("settings_stats_section"))
                        .font(.system(size: 13.5, weight: .semibold))
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                        statTile(title: store.tr("settings_stat_open"), value: store.statistics.openTasks)
                        statTile(title: store.tr("settings_stat_completed"), value: store.statistics.completedTasks)
                        statTile(title: store.tr("settings_stat_today_active"), value: store.statistics.todayActiveTasks)
                        statTile(title: store.tr("settings_stat_active_projects"), value: store.statistics.activeProjects)
                        statTile(title: store.tr("settings_stat_recurring"), value: store.statistics.recurringTasks)
                        statTile(title: store.tr("settings_stat_saved_tags"), value: store.statistics.savedTags)
                        statTile(title: store.tr("settings_stat_canceled"), value: store.statistics.canceledTasks)
                        statTile(title: store.tr("settings_stat_deleted"), value: store.statistics.deletedTasks)
                        statTile(title: store.tr("settings_stat_total_records"), value: store.statistics.totalRecords)
                    }
                }
                .padding(14)
                .thingsCardSurface(cornerRadius: 11)
            }
            .padding(6)
        }
    }

    private func statTile(title: String, value: Int) -> some View {
        VStack(spacing: 3) {
            Text("\(value)")
                .font(.system(size: 18, weight: .bold, design: .rounded))
            Text(title)
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 9)
        .padding(.horizontal, 6)
        .background(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(Color.primary.opacity(0.04))
        )
    }

    // MARK: - Tab 6: Updates & About

    private var appIconImage: NSImage? {
        guard let url = ThingsResourceLoader.url(forResource: "AppIcon-1024", withExtension: "png") else {
            return nil
        }
        return NSImage(contentsOf: url)
    }

    private var aboutTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 16) {
                if let icon = appIconImage {
                    Image(nsImage: icon)
                        .resizable()
                        .interpolation(.high)
                        .frame(width: 64, height: 64)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .shadow(color: .black.opacity(0.14), radius: 6, y: 2)
                } else {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(store.tr("app_name"))
                        .font(.system(size: 20, weight: .bold))
                    Text(
                        store.trf(
                            "macos_version_subtitle",
                            AppUpdateService.currentVersion,
                            "\(AppUpdateService.currentBuildNumber)"
                        )
                    )
                    .font(.system(size: 12.5))
                    .foregroundStyle(.secondary)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .thingsCardSurface(cornerRadius: 12)

            VStack(alignment: .leading, spacing: 12) {
                Text(store.tr("settings_updates_section"))
                    .font(.system(size: 13.5, weight: .semibold))

                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(store.tr("settings_updates_auto_title"))
                            .font(.system(size: 13, weight: .medium))
                        Text(store.tr("settings_updates_auto_desc"))
                            .font(.system(size: 11.5))
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Toggle("", isOn: $store.preferences.autoUpdateCheckEnabled)
                        .toggleStyle(.switch)
                        .labelsHidden()
                }

                Divider()

                HStack(spacing: 10) {
                    Button {
                        isCheckingUpdate = true
                        updateStatusText = store.tr("settings_updates_checking")
                        Task {
                            let found = try? await AppUpdateService.checkForUpdates()
                            await MainActor.run {
                                isCheckingUpdate = false
                                store.availableUpdate = found
                                if let found {
                                    updateStatusText = store.trf(
                                        "settings_updates_available_title",
                                        found.versionString,
                                        AppUpdateService.currentVersion
                                    )
                                } else {
                                    updateStatusText = store.trf(
                                        "settings_updates_up_to_date",
                                        AppUpdateService.currentVersion
                                    )
                                }
                            }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            if isCheckingUpdate {
                                ProgressView().controlSize(.small)
                            }
                            Text(store.tr("settings_updates_search_btn"))
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isCheckingUpdate)

                    if let found = store.availableUpdate {
                        Button(store.tr("settings_updates_open_github_btn")) {
                            AppUpdateService.openUpdatePage(found)
                        }
                        .buttonStyle(.bordered)
                    } else {
                        Button(store.tr("settings_updates_open_github_btn")) {
                            if let url = URL(string: "https://github.com/petrosvassiliou/thingsbook-android/releases") {
                                NSWorkspace.shared.open(url)
                            }
                        }
                        .buttonStyle(.bordered)
                    }
                }

                if let updateStatusText {
                    Text(updateStatusText)
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }
            .padding(16)
            .thingsCardSurface(cornerRadius: 12)

            Spacer()
        }
        .padding(6)
    }
}
