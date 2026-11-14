import SwiftUI
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
    @State private var backupPassword = ""
    @State private var isCheckingUpdate = false
    @State private var updateStatusText: String?

    public init(store: ThingsbookStore, showCloseButton: Bool = false) {
        self.store = store
        self.showCloseButton = showCloseButton
    }

    public var body: some View {
        VStack(spacing: 0) {
            if showCloseButton {
                HStack {
                    Text(store.tr("settings_title"))
                        .font(.system(size: 15, weight: .semibold))
                    Spacer()
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 16))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 18)
                .padding(.top, 14)
                .padding(.bottom, 6)
            }

            TabView(selection: $store.settingsTab) {
                generalTab
                    .tabItem { Label("General", systemImage: "gearshape") }
                    .tag(SettingsSectionTab.general)

                syncTab
                    .tabItem { Label("Sync", systemImage: "icloud") }
                    .tag(SettingsSectionTab.sync)

                calendarTab
                    .tabItem { Label("Calendar", systemImage: "calendar") }
                    .tag(SettingsSectionTab.calendar)

                notificationsTab
                    .tabItem { Label("Notifications", systemImage: "bell.badge") }
                    .tag(SettingsSectionTab.notifications)

                backupTab
                    .tabItem { Label("Backup & CSV", systemImage: "externaldrive.badge.timemachine") }
                    .tag(SettingsSectionTab.backup)

                aboutTab
                    .tabItem { Label("Updates", systemImage: "sparkles.tv") }
                    .tag(SettingsSectionTab.about)
            }
            .padding(16)
        }
        .frame(width: 600, height: 470)
        .onAppear {
            let active = FirebaseConfig.loadActive()
            if active.isCustom {
                customProjectId = active.projectId
                customApiKey = active.apiKey
                customAppId = active.appId
            }
            if store.preferences.calendarEventsEnabled {
                store.availableCalendars = AppleCalendarService.shared.listCalendars()
            }
        }
    }

    // MARK: - General

    private var generalTab: some View {
        Form {
            Section {
                Picker(store.tr("settings_language_title"), selection: $store.preferences.language) {
                    Text(store.tr("settings_lang_system")).tag(AppLanguage.system)
                    Text(store.tr("settings_lang_en")).tag(AppLanguage.en)
                    Text(store.tr("settings_lang_de")).tag(AppLanguage.de)
                    Text(store.tr("settings_lang_fr")).tag(AppLanguage.fr)
                    Text(store.tr("settings_lang_it")).tag(AppLanguage.it)
                    Text(store.tr("settings_lang_es")).tag(AppLanguage.es)
                }
                .onChange(of: store.preferences.language) { _, newLang in
                    NotificationReminderService.shared.configureCategories(lang: newLang)
                }

                Toggle(store.tr("settings_today_group_title"), isOn: $store.preferences.groupTodayByProjectOrArea)
            }

            Section {
                Toggle(store.tr("settings_biometric_title"), isOn: $store.preferences.biometricLockEnabled)
                Text(store.tr("settings_biometric_desc"))
                    .font(.system(size: 11.5))
                    .foregroundStyle(.secondary)
            }

            if store.hasSampleTasks {
                Section {
                    Button(store.tr("sample_banner_clear_btn")) {
                        try? store.database.purgeSampleDataLocally(preserveUsedContainers: true)
                    }
                }
            }
        }
        .formStyle(.grouped)
    }

    // MARK: - Cloud Sync

    private var syncTab: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Toggle(store.tr("settings_db_mode_local"), isOn: $store.preferences.localOnlyDbMode)
                        .onChange(of: store.preferences.localOnlyDbMode) { _, localOnly in
                            if !localOnly {
                                store.syncNow()
                            }
                        }
                    Spacer()
                    Text(store.preferences.localOnlyDbMode ? "Local SQLite Only" : store.syncSnapshot.status.rawValue)
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(.secondary)
                }
                .padding(12)
                .thingsCardSurface(cornerRadius: 10)

                if let userEmail = store.syncSnapshot.userEmailOrId {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Image(systemName: "person.crop.circle.badge.checkmark")
                                .font(.system(size: 20))
                                .foregroundStyle(ThingsMacPalette.logbookGreen)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(userEmail)
                                    .font(.system(size: 14, weight: .semibold))
                                if store.syncSnapshot.lastSyncedAt > 0 {
                                    Text("Last synced: \(DateEpochConverter.date(fromMillis: store.syncSnapshot.lastSyncedAt).formatted())")
                                        .font(.system(size: 11.5))
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Button(store.tr("sync_btn_sync_now")) {
                                store.syncNow()
                            }
                            .buttonStyle(.borderedProminent)

                            Button(store.tr("sync_btn_sign_out")) {
                                Task { await store.cloudSync.signOut() }
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(12)
                    .thingsCardSurface(cornerRadius: 10)
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(store.tr("sync_account_card_title"))
                            .font(.system(size: 14, weight: .semibold))

                        TextField(store.tr("sync_email_label"), text: $emailInput)
                            .textFieldStyle(.roundedBorder)

                        SecureField(store.tr("sync_password_label"), text: $passwordInput)
                            .textFieldStyle(.roundedBorder)

                        Toggle(store.tr("merge_dialog_keep_btn"), isOn: $mergeLocalOnLogin)
                            .font(.system(size: 12))

                        if let err = authMessage ?? store.syncSnapshot.errorMessage {
                            Text(err)
                                .font(.system(size: 11.5))
                                .foregroundStyle(.red)
                        }

                        HStack(spacing: 8) {
                            Button(store.tr("sync_btn_sign_in")) {
                                performAuth {
                                    _ = try await store.cloudSync.signInEmail(
                                        email: emailInput,
                                        password: passwordInput,
                                        mergeLocalTasks: mergeLocalOnLogin,
                                        localOnlyMode: store.preferences.localOnlyDbMode
                                    )
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(isAuthBusy)

                            Button(store.tr("sync_btn_register")) {
                                performAuth {
                                    _ = try await store.cloudSync.registerEmail(
                                        email: emailInput,
                                        password: passwordInput,
                                        mergeLocalTasks: mergeLocalOnLogin,
                                        localOnlyMode: store.preferences.localOnlyDbMode
                                    )
                                }
                            }
                            .buttonStyle(.bordered)
                            .disabled(isAuthBusy)

                            Button("Instant Email Sync") {
                                performAuth {
                                    _ = try await store.cloudSync.signInInstantEmail(
                                        email: emailInput,
                                        mergeLocalTasks: mergeLocalOnLogin,
                                        localOnlyMode: store.preferences.localOnlyDbMode
                                    )
                                }
                            }
                            .buttonStyle(.bordered)
                            .disabled(isAuthBusy)

                            Spacer()

                            Button(store.tr("sync_forgot_password")) {
                                performAuth {
                                    try await store.cloudSync.sendPasswordResetEmail(email: emailInput)
                                    await MainActor.run {
                                        authMessage = store.tr("sync_reset_sent")
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                            .font(.system(size: 11.5))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                        }
                    }
                    .padding(14)
                    .thingsCardSurface(cornerRadius: 10)
                }

                DisclosureGroup("Custom Firebase Project (Advanced)", isExpanded: $showCustomFirebase) {
                    VStack(alignment: .leading, spacing: 8) {
                        TextField("Project ID (default: thingsbook-prod)", text: $customProjectId)
                            .textFieldStyle(.roundedBorder)
                        TextField("Web / Android API Key", text: $customApiKey)
                            .textFieldStyle(.roundedBorder)
                        TextField("App ID", text: $customAppId)
                            .textFieldStyle(.roundedBorder)
                        HStack {
                            Button("Save Custom Project") {
                                FirebaseConfig.saveCustom(FirebaseConfig(projectId: customProjectId, apiKey: customApiKey, appId: customAppId))
                                store.showToast("Saved Custom Firebase Config")
                            }
                            .controlSize(.small)
                            Button("Reset to Official Thingsbook Cloud") {
                                FirebaseConfig.saveCustom(nil)
                                customProjectId = ""
                                customApiKey = ""
                                customAppId = ""
                                store.showToast("Restored Default Thingsbook Cloud")
                            }
                            .controlSize(.small)
                        }
                    }
                    .padding(.top, 6)
                }
                .font(.system(size: 12, weight: .medium))
            }
            .padding(6)
        }
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

    // MARK: - Apple Calendar (EventKit)

    private var calendarTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle(store.tr("settings_calendar_enable_title"), isOn: $store.preferences.calendarEventsEnabled)
                .onChange(of: store.preferences.calendarEventsEnabled) { _, enabled in
                    if enabled {
                        Task { await store.refreshCalendarEvents() }
                    } else {
                        store.calendarEvents = []
                    }
                }

            Text(store.tr("settings_calendar_enable_desc"))
                .font(.system(size: 12))
                .foregroundStyle(.secondary)

            if store.preferences.calendarEventsEnabled {
                HStack {
                    Text(store.tr("settings_calendar_select_header"))
                        .font(.system(size: 12.5, weight: .semibold))
                    Spacer()
                    Button("Refresh macOS Calendars") {
                        Task { await store.refreshCalendarEvents() }
                    }
                    .controlSize(.small)
                }

                ScrollView {
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(store.availableCalendars) { cal in
                            let isSelected = store.preferences.selectedCalendarIds.isEmpty || store.preferences.selectedCalendarIds.contains(cal.id)
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(ThingsMacPalette.colorFromArgb(cal.colorHex))
                                    .frame(width: 10, height: 10)
                                Text(cal.title)
                                    .font(.system(size: 13))
                                Text("(\(cal.sourceTitle))")
                                    .font(.system(size: 11.5))
                                    .foregroundStyle(.secondary)
                                Spacer()
                                Toggle("", isOn: Binding(
                                    get: { isSelected },
                                    set: { checked in
                                        var current = store.preferences.selectedCalendarIds.isEmpty
                                            ? store.availableCalendars.map(\.id)
                                            : store.preferences.selectedCalendarIds
                                        if checked {
                                            if !current.contains(cal.id) { current.append(cal.id) }
                                        } else {
                                            current.removeAll { $0 == cal.id }
                                        }
                                        store.preferences.selectedCalendarIds = current
                                        Task { await store.refreshCalendarEvents() }
                                    }
                                ))
                                .labelsHidden()
                            }
                            .padding(.vertical, 3)
                        }
                    }
                }
            }
            Spacer()
        }
        .padding(8)
    }

    // MARK: - Notifications & Daily Digest

    private var notificationsTab: some View {
        Form {
            Section {
                Button("Grant macOS Notification Permission") {
                    Task {
                        let granted = await NotificationReminderService.shared.requestPermissionIfNeeded()
                        await MainActor.run {
                            store.showToast(granted ? "Notifications enabled" : "Notification permission not granted")
                        }
                    }
                }
            }

            Section {
                Toggle(store.tr("settings_digest_title"), isOn: $store.preferences.dailyDigestEnabled)
                    .onChange(of: store.preferences.dailyDigestEnabled) { _, _ in
                        store.reloadSnapshot()
                    }

                if store.preferences.dailyDigestEnabled {
                    Stepper(value: $store.preferences.dailyDigestHour, in: 0...23) {
                        Text("Hour: \(String(format: "%02d", store.preferences.dailyDigestHour)):\(String(format: "%02d", store.preferences.dailyDigestMinute))")
                    }
                    Stepper(value: $store.preferences.dailyDigestMinute, in: 0...55, step: 5) {
                        Text("Minute: \(String(format: "%02d", store.preferences.dailyDigestMinute))")
                    }
                }
            }
        }
        .formStyle(.grouped)
    }

    // MARK: - Backup (.tbbackup), CSV & Statistics

    private var backupTab: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Encrypted Cross-Platform Backup (.tbbackup)")
                        .font(.system(size: 13.5, weight: .semibold))
                    Text("100% compatible with Thingsbook for Android & ChromeOS (AES-256-GCM + PBKDF2-SHA256).")
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)

                    SecureField("Optional backup password (leave empty for unencrypted)", text: $backupPassword)
                        .textFieldStyle(.roundedBorder)

                    HStack(spacing: 10) {
                        Button {
                            let pwd = backupPassword.isEmpty ? nil : backupPassword
                            store.exportBackupInteractively(password: pwd)
                        } label: {
                            Label(store.tr("backup_export_btn"), systemImage: "square.and.arrow.up")
                        }
                        .buttonStyle(.borderedProminent)

                        Button {
                            store.importBackupInteractively()
                        } label: {
                            Label(store.tr("backup_import_btn"), systemImage: "square.and.arrow.down")
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .padding(12)
                .thingsCardSurface(cornerRadius: 10)

                HStack(spacing: 10) {
                    Button {
                        store.exportCsvInteractively()
                    } label: {
                        Label(store.tr("settings_csv_export_btn"), systemImage: "tablecells")
                    }
                    .buttonStyle(.bordered)

                    Button {
                        store.importCsvInteractively()
                    } label: {
                        Label(store.tr("settings_csv_import_btn"), systemImage: "tray.and.arrow.down")
                    }
                    .buttonStyle(.bordered)
                }

                // Live Database Statistics
                VStack(alignment: .leading, spacing: 8) {
                    Text(store.tr("settings_stats_header"))
                        .font(.system(size: 13, weight: .semibold))
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                        statTile(title: "Open", value: store.statistics.openTasks)
                        statTile(title: "Completed", value: store.statistics.completedTasks)
                        statTile(title: "Today", value: store.statistics.todayActiveTasks)
                        statTile(title: "Projects", value: store.statistics.activeProjects)
                        statTile(title: "Recurring", value: store.statistics.recurringTasks)
                        statTile(title: "Tags", value: store.statistics.savedTags)
                    }
                }
                .padding(12)
                .thingsCardSurface(cornerRadius: 10)
            }
            .padding(6)
        }
    }

    private func statTile(title: String, value: Int) -> some View {
        VStack(spacing: 2) {
            Text("\(value)")
                .font(.system(size: 18, weight: .bold, design: .rounded))
            Text(title)
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color.primary.opacity(0.04)))
    }

    // MARK: - Updates & About

    private var aboutTab: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 40))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Thingsbook for macOS")
                        .font(.system(size: 18, weight: .bold))
                    Text("Version \(AppUpdateService.currentVersion) (Build \(AppUpdateService.currentBuildNumber)) · Android Parity Synced")
                        .font(.system(size: 12.5))
                        .foregroundStyle(.secondary)
                }
            }

            Toggle(store.tr("settings_update_auto_title"), isOn: $store.preferences.autoUpdateCheckEnabled)

            HStack(spacing: 10) {
                Button(store.tr("settings_update_check_btn")) {
                    isCheckingUpdate = true
                    Task {
                        let found = try? await AppUpdateService.checkForUpdates()
                        await MainActor.run {
                            isCheckingUpdate = false
                            store.availableUpdate = found
                            updateStatusText = found == nil
                                ? store.tr("update_up_to_date")
                                : store.trf("update_available_title", found!.versionString)
                        }
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(isCheckingUpdate)

                if let updateStatusText {
                    Text(updateStatusText)
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()
        }
        .padding(16)
    }
}
