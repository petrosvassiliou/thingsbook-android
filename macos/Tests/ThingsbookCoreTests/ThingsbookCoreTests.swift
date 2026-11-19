import XCTest
import SQLite3
@testable import ThingsbookCore

final class ThingsbookCoreTests: XCTestCase {

    // MARK: - 1. Localization Parity & Android Positional Formatting

    func testLocalizationFiveLanguagesLoadedAndFormatted() {
        let strings = ThingsStrings.shared
        let langs = Set(strings.availableLanguages)
        XCTAssertEqual(langs, ["de", "en", "es", "fr", "it"])

        let enKeys = strings.keys(for: "en")
        XCTAssertGreaterThanOrEqual(enKeys.count, 405, "Expected >=405 Android + macOS string keys")

        let criticalKeys = [
            "view_inbox", "view_today", "view_this_evening", "view_upcoming",
            "view_anytime", "view_someday", "view_logbook", "view_trash",
            "tags_filter_all", "quick_find_title", "settings_language",
            "areas_manage_title", "tags_manage_title", "focus_timer_active_label",
            "macos_tab_general", "macos_tab_sync", "macos_touch_id_title"
        ]

        for lang in ["en", "de", "fr", "it", "es"] {
            XCTAssertEqual(strings.keys(for: lang), enKeys, "All 5 languages must share the exact key set")
            let appLang = AppLanguage(rawValue: lang) ?? .en
            for k in criticalKeys {
                let resolved = strings.text(k, lang: appLang)
                XCTAssertNotEqual(resolved, k, "Key \(k) leaked raw identifier in \(lang)")
                XCTAssertFalse(resolved.isEmpty)
            }
        }

        let formatted = ThingsStrings.formatPositional("Project \"%1$s\" has %2$d tasks (100%% parity)", args: ["macOS Port", 42])
        XCTAssertEqual(formatted, "Project \"macOS Port\" has 42 tasks (100% parity)")

        let timer = FocusTimerState(taskId: "t1", taskTitle: "Deep Work", remainingSeconds: 750, totalSeconds: 1500)
        XCTAssertEqual(timer.formattedCountdown, "12:30")
        XCTAssertEqual(timer.progress, 0.5, accuracy: 0.001)
    }

    // MARK: - 2. 5-Language Natural Language Date Parser

    func testMultilingualDateParserAllFiveLanguages() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let refDate = cal.date(from: DateComponents(year: 2026, month: 6, day: 15, hour: 10))! // Monday

        let enToday = MultilingualDateParser.parse("today", locale: Locale(identifier: "en"), referenceDate: refDate, calendar: cal)
        XCTAssertEqual(enToday?.scheduleState, .today)

        let deEvening = MultilingualDateParser.parse("heute abend", locale: Locale(identifier: "de"), referenceDate: refDate, calendar: cal)
        XCTAssertEqual(deEvening?.scheduleState, .thisEvening)

        let itTomorrow = MultilingualDateParser.parse("domani", locale: Locale(identifier: "it"), referenceDate: refDate, calendar: cal)
        XCTAssertEqual(itTomorrow?.scheduleState, .upcoming)
        let expectedTomorrow = DateEpochConverter.startOfDayMillis(for: cal.date(byAdding: .day, value: 1, to: refDate)!, calendar: cal)
        XCTAssertEqual(itTomorrow?.whenDateEpochMillis, expectedTomorrow)

        let frIn3Days = MultilingualDateParser.parse("dans 3 jours", locale: Locale(identifier: "fr"), referenceDate: refDate, calendar: cal)
        let expected3Days = DateEpochConverter.startOfDayMillis(for: cal.date(byAdding: .day, value: 3, to: refDate)!, calendar: cal)
        XCTAssertEqual(frIn3Days?.whenDateEpochMillis, expected3Days)

        let esSomeday = MultilingualDateParser.parse("algún día", locale: Locale(identifier: "es"), referenceDate: refDate, calendar: cal)
        XCTAssertEqual(esSomeday?.scheduleState, .someday)
        XCTAssertNil(esSomeday?.whenDateEpochMillis)
    }

    // MARK: - 3. Markdown Link Extraction

    func testMarkdownLinkExtraction() {
        let notes = """
        Review spec at https://github.com/petrosvassiliou/thingsbook-android,
        email team@thingsbook.app, or call +49 30 12345678 before 2026-07-01.
        """
        let links = MarkdownNotesParser.extractDetectedLinks(from: notes)
        XCTAssertEqual(links.count, 3)
        XCTAssertEqual(links[0].type, .web)
        XCTAssertEqual(links[0].targetUri, "https://github.com/petrosvassiliou/thingsbook-android")
        XCTAssertEqual(links[1].type, .email)
        XCTAssertEqual(links[1].targetUri, "mailto:team@thingsbook.app")
        XCTAssertEqual(links[2].type, .phone)
    }

    // MARK: - 4. SQLite + FTS5 CRUD, Recurrence, Midnight Rollover, Trash & Duplicate

    func testSQLiteFts5RecurrenceRolloverAndDuplicate() throws {
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        try db.seedIfEmpty(languageCode: "de")

        var snap = try db.loadWorkspaceSnapshot()
        XCTAssertTrue(snap.hasSampleTasks)
        XCTAssertEqual(snap.areas.count, 2)
        XCTAssertEqual(snap.projects.count, 2)
        XCTAssertEqual(snap.tags.count, 5)

        // Diacritic-insensitive FTS5 search ("Zahnarzt" or "Gültigkeit" -> "Gultigkeit")
        let ftsHits = try db.searchQuickFind(rawQuery: "Gultigkeit")
        XCTAssertFalse(ftsHits.tasks.isEmpty)

        // Max 8 Areas guard
        for idx in 3...8 {
            _ = try db.createArea(title: "Area \(idx)")
        }
        XCTAssertThrowsError(try db.createArea(title: "Area 9 Overflow"))

        // Recurrence spawning (After Completion: 4 days)
        let spawnedId = try db.setTaskStatus(taskId: "task-evening-004", status: .completed)
        XCTAssertNotNil(spawnedId)

        snap = try db.loadWorkspaceSnapshot()
        XCTAssertTrue(snap.upcomingTasks.contains { $0.id == spawnedId })

        // Duplicate Project
        let dupProj = try db.duplicateProject("proj-vacation-001", copySuffix: "Copy")
        XCTAssertNotNil(dupProj)
        XCTAssertTrue(dupProj!.title.contains("(Copy)"))

        // Soft delete -> Restore -> Permanent Delete
        try db.softDeleteTask("task-inbox-001")
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(snap.trashedTasks.map(\.id), ["task-inbox-001"])

        try db.restoreTask("task-inbox-001")
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertTrue(snap.trashedTasks.isEmpty)

        // Midnight Rollover moves due UPCOMING tasks into TODAY
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: Date())!
        let yesterdayMillis = DateEpochConverter.startOfDayMillis(for: yesterday)
        let dueUpcoming = try db.createTask(
            title: "Rollover Candidate",
            scheduleState: .upcoming,
            whenDate: yesterdayMillis
        )
        let rolled = try db.performMidnightRollover()
        XCTAssertGreaterThanOrEqual(rolled, 1)
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertTrue(snap.todayDaytimeTasks.contains { $0.id == dueUpcoming.id && $0.scheduleState == .today })
    }

    // MARK: - 5. CSV Export & Import Roundtrip

    func testCsvExportAndImportRoundtrip() throws {
        let db1 = try ThingsSQLiteDatabase(path: ":memory:")
        let area = try db1.createArea(title: "Engineering")
        let proj = try db1.createProject(title: "macOS Launch", areaId: area.id)
        let tag = try db1.createOrUpdateTag(title: "Ship", shortcut: "s")
        _ = try db1.createTask(
            title: "Package Universal DMG",
            notes: "Verify arm64 + x86_64",
            scheduleState: .today,
            whenDate: DateEpochConverter.startOfDayMillis(for: Date()),
            projectId: proj.id,
            areaId: area.id,
            tagIds: [tag.id],
            checklistTitles: ["Run verify-android-parity.py", "Run swift test"]
        )

        let csv = try db1.exportCsv()
        XCTAssertTrue(csv.contains("Package Universal DMG"))

        let db2 = try ThingsSQLiteDatabase(path: ":memory:")
        let importedCount = try db2.importCsv(csv)
        XCTAssertEqual(importedCount, 1)
        let snap2 = try db2.loadWorkspaceSnapshot()
        XCTAssertEqual(snap2.allOpenTasks.first?.title, "Package Universal DMG")
        XCTAssertEqual(snap2.allOpenTasks.first?.checklistItems.count, 2)
    }

    // MARK: - 6. Binary `.tbbackup` v1 Unencrypted, Encrypted & Cross-Platform Android Vector

    func testTbbackupPlainEncryptedAndAndroidCrossPlatformVector() throws {
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        try db.seedIfEmpty(languageCode: "en")
        let payload = try db.buildBackupPayload(preferences: AppPreferences())

        // Plain roundtrip
        let plainBytes = try ThingsBackupCodec.encodeBackup(payload: payload, password: nil)
        let plainInspected = try ThingsBackupCodec.inspectBackup(rawBytes: plainBytes)
        if case .unencryptedPreview(_, _, let summary) = plainInspected {
            XCTAssertEqual(summary.tasksCount, payload.tasks.count)
        } else {
            XCTFail("Expected unencryptedPreview")
        }
        let plainDecoded = try ThingsBackupCodec.decodeBackup(rawBytes: plainBytes, password: nil)
        XCTAssertEqual(plainDecoded.tasks.count, payload.tasks.count)

        // Encrypted roundtrip (AES-256-GCM + PBKDF2-HMAC-SHA256 120k)
        let encryptedBytes = try ThingsBackupCodec.encodeBackup(payload: payload, password: "CorrectHorseBatteryStaple!")
        let encInspected = try ThingsBackupCodec.inspectBackup(rawBytes: encryptedBytes)
        XCTAssertEqual(encInspected, .passwordProtected(createdAt: payload.createdAt, appVersionName: AppUpdateService.currentVersion))

        XCTAssertThrowsError(try ThingsBackupCodec.decodeBackup(rawBytes: encryptedBytes, password: nil))
        XCTAssertThrowsError(try ThingsBackupCodec.decodeBackup(rawBytes: encryptedBytes, password: "WrongPassword"))

        let decrypted = try ThingsBackupCodec.decodeBackup(rawBytes: encryptedBytes, password: "CorrectHorseBatteryStaple!")
        XCTAssertEqual(decrypted, payload)

        // Authentic Android / ChromeOS WebCrypto AES-256-GCM + GZIP v1.7.0 test vector (v1.7 -> v1.8 upgrade compatibility)
        let androidVectorJson = """
        {"magic":"THINGSBOOK_BACKUP_V1","formatVersion":1,"appVersionName":"1.7.0","appVersionCode":41,"createdAt":1731571200000,"encrypted":true,"kdf":"PBKDF2WithHmacSHA256","kdfIterations":120000,"saltBase64":"AQIDBAUGBwgJCgsMDQ4PEA==","cipher":"AES/GCM/NoPadding","ivBase64":"oaKjpKWmp6ipqqus","gcmTagBits":128,"ciphertextBase64":"CSC233S0+7eSLIfV0yXB8qY0+1244/IZ0OY1B/lk1h7ywTKb49XbD1dHO3EpIgkmybthWy6+BwY/Eyx8Et/AJ7ywor113WLpR/aGUmJ0dMvignjksZ2LNUY8Aj8HZRe7cZBtGsP7/dHjA+03KQ3yoJspVHAlzqcdHq0qXaGprtvn2tvaKSI5O5RTuWbXH6uG989iRoVYhyNzzmKIBxA5LZk7TKIoHYoqG9GTb3chTKVh1IbWjnT/ldg8DXehlL345Eyjnx/Eh40SZlw9ogSzg5koHp2ZUah4yIPytoB69yVemu6buHx0fe+gO7lY9Yn+L2WNei/kzUl2Evsgh81U/XAnHeFHoLF0SAyo7vikaUJQzBJHGXVyp2664jSZWTFRkm4Vlyyo6sL+0C+EqOUy2IQbJR8zQ6pw6yLZ5JOzcPMacB7Z+duMrJdFMyWD2wwOq2ttYXvqyagRAcF3oV0OjnIxfaeF5BbSekRm8T9uVw=="}
        """
        let androidDecoded = try ThingsBackupCodec.decodeBackup(
            rawBytes: Data(androidVectorJson.utf8),
            password: "ThingsbookSecret2026!"
        )
        XCTAssertEqual(androidDecoded.areas.first?.title, "Android Synced Area")
        XCTAssertEqual(androidDecoded.tasks.first?.title, "Encrypted Cross-Platform Task")
        XCTAssertTrue(androidDecoded.attachments.isEmpty)

        // Atomic restore into fresh DB
        let freshDb = try ThingsSQLiteDatabase(path: ":memory:")
        try freshDb.restoreBackupPayload(androidDecoded)
        let restoredSnap = try freshDb.loadWorkspaceSnapshot()
        XCTAssertEqual(restoredSnap.areas.map(\.title), ["Android Synced Area"])
        XCTAssertEqual(restoredSnap.allOpenTasks.map(\.title), ["Encrypted Cross-Platform Task"])
    }

    func testMapLocationChipsDetectionV18() {
        let notes = """
        Meet client at 📍 Bahnhofstrasse 10, Zürich
        Backup coordinates: 47.3769, 8.5417
        Office: Musterstraße 42, 10115 Berlin
        US HQ: 742 Evergreen Terrace
        """
        let links = MarkdownNotesParser.extractDetectedLinks(from: notes)
        let mapLinks = links.filter { $0.type == .map }
        XCTAssertEqual(mapLinks.count, 4)
        XCTAssertTrue(mapLinks.allSatisfy { $0.targetUri.hasPrefix("https://maps.apple.com/") })
    }

    func testAttachmentsCrudBackupAndCloudDeltaV18() throws {
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let proj = try db.createProject(title: "Architecture Spec")
        let task = try db.createTask(title: "Review Floorplan", scheduleState: .today, projectId: proj.id)

        let pdfBytes = Data("%PDF-1.4 sample floorplan".utf8)
        let imgBytes = Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])

        let taskAtt = try db.addAttachment(
            taskId: task.id,
            fileName: "floorplan.pdf",
            mimeType: "application/pdf",
            data: pdfBytes
        )
        let projAtt = try db.addAttachment(
            projectId: proj.id,
            fileName: "facade.png",
            mimeType: "image/png",
            data: imgBytes
        )

        XCTAssertTrue(taskAtt.isPdf)
        XCTAssertTrue(projAtt.isImage)
        XCTAssertNotNil(db.resolveAttachmentURL(taskAtt))
        XCTAssertEqual(db.readAttachmentBase64(taskAtt), pdfBytes.base64EncodedString())

        let snap = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(snap.allOpenTasks.first(where: { $0.id == task.id })?.attachments.count, 1)
        XCTAssertEqual(snap.projectAttachments[proj.id]?.count, 1)

        // Dirty batch includes both attachments
        let dirty = try db.fetchDirtyBatch()
        XCTAssertEqual(dirty.attachments.count, 2)
        try db.markDirtyBatchSynced(dirty)
        XCTAssertTrue(try db.fetchDirtyBatch().attachments.isEmpty)

        // v1.8 .tbbackup roundtrip preserves binary attachment bytes
        let backupPayload = try db.buildBackupPayload(preferences: AppPreferences())
        XCTAssertEqual(backupPayload.attachments.count, 2)
        let encoded = try ThingsBackupCodec.encodeBackup(payload: backupPayload, password: "V18UpgradePassword!")
        let decoded = try ThingsBackupCodec.decodeBackup(rawBytes: encoded, password: "V18UpgradePassword!")
        XCTAssertEqual(decoded.attachments.count, 2)

        let restoredDb = try ThingsSQLiteDatabase(path: ":memory:")
        try restoredDb.restoreBackupPayload(decoded)
        let restoredSnap = try restoredDb.loadWorkspaceSnapshot()
        let restoredTaskAtt = restoredSnap.allOpenTasks.first?.attachments.first
        XCTAssertEqual(restoredTaskAtt?.fileName, "floorplan.pdf")
        if let restoredTaskAtt {
            XCTAssertEqual(restoredDb.readAttachmentBase64(restoredTaskAtt), pdfBytes.base64EncodedString())
        }

        // Remote cloud sync delta applies inline Base64 attachment & tombstone deletion
        let remotePayload = Data("Remote Cloud Receipt".utf8)
        let remoteAtt = TaskAttachment(
            id: "remote-att-1",
            taskId: task.id,
            fileName: "receipt.jpg",
            mimeType: "image/jpeg",
            sizeBytes: Int64(remotePayload.count),
            createdAt: 1_750_000_000_000,
            updatedAt: 1_750_000_000_000
        )
        _ = try restoredDb.applyRemoteDeltas(
            RemoteDeltaBatch(attachments: [
                RemoteAttachmentDelta(attachment: remoteAtt, dataBase64: remotePayload.base64EncodedString())
            ])
        )
        let afterRemoteSnap = try restoredDb.loadWorkspaceSnapshot()
        XCTAssertEqual(afterRemoteSnap.allOpenTasks.first?.attachments.count, 2)

        // Delete attachment cleans up file and marks tombstone
        try db.deleteAttachment(taskAtt.id)
        XCTAssertNil(db.resolveAttachmentURL(taskAtt))
        XCTAssertTrue(try db.loadWorkspaceSnapshot().allOpenTasks.first?.attachments.isEmpty ?? false)
    }

    func testUpgradeFromV17PreferencesAndDatabase() throws {
        // 1. Simulate v1.7 AppPreferences JSON (without menuBarSelectedProjectId)
        let v17PrefsJson = """
        {"localOnlyDbMode":false,"sidebarVisible":true,"dailyDigestEnabled":true,"dailyDigestHour":8,"dailyDigestMinute":30,"language":"de","calendarEventsEnabled":false,"selectedCalendarIds":[],"groupTodayByProjectOrArea":true,"biometricLockEnabled":false,"autoUpdateCheckEnabled":true,"hideSampleSetupBanner":true}
        """
        let decodedPrefs = try JSONDecoder().decode(AppPreferences.self, from: Data(v17PrefsJson.utf8))
        XCTAssertEqual(decodedPrefs.language, .de)
        XCTAssertTrue(decodedPrefs.groupTodayByProjectOrArea)
        XCTAssertNil(decodedPrefs.menuBarSelectedProjectId)

        // 2. Verify all v1.8 localization keys exist in EN/DE/FR/IT/ES
        let v18Keys = [
            "voice_capture_btn",
            "voice_smart_parse_gemini",
            "action_attach_file",
            "attachments_section_title",
            "widget_pin_project_tab",
            "macos_voice_listening",
            "macos_attachment_drop_hint"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in v18Keys {
                let val = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertFalse(val.isEmpty, "Missing \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(val, key, "Untranslated \(key) in \(lang.rawValue)")
            }
        }
    }

    func testCloudSyncTypeCoercionRemoteFtsAndLanguageIconsV181() throws {
        // 1. Verify language flag icons & menu order match Android
        XCTAssertEqual(AppLanguage.orderedMenuCases, [.system, .en, .de, .it, .fr, .es])
        XCTAssertEqual(AppLanguage.system.flagEmoji, "🌐")
        XCTAssertEqual(AppLanguage.en.flagEmoji, "🇬🇧")
        XCTAssertEqual(AppLanguage.de.flagEmoji, "🇩🇪")
        XCTAssertEqual(AppLanguage.it.flagEmoji, "🇮🇹")
        XCTAssertEqual(AppLanguage.fr.flagEmoji, "🇫🇷")
        XCTAssertEqual(AppLanguage.es.flagEmoji, "🇪🇸")

        // 2. Verify semantic update check upgrades 1.7.0 & 1.8.0 -> 1.8.0+1 -> 1.8.1 without looping
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "1.8.0+1", current: "1.7.0"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "1.8.0+1", current: "1.8.0"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "1.8.0+1", current: "1.8.0+1"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "1.8.1", current: "1.8.0+1"))

        // 3. Verify Firestore mixed integerValue / doubleValue / string coercion
        let intSortWrapped: [String: Any] = ["integerValue": "4"]
        let doubleSortWrapped: [String: Any] = ["doubleValue": NSNumber(value: 2.5)]
        XCTAssertEqual(FirebaseCloudSyncService.asDouble(FirebaseCloudSyncService.unwrapFirestoreValue(intSortWrapped)), 4.0)
        XCTAssertEqual(FirebaseCloudSyncService.asDouble(FirebaseCloudSyncService.unwrapFirestoreValue(doubleSortWrapped)), 2.5)
        XCTAssertEqual(FirebaseCloudSyncService.asInt64(NSNumber(value: 1_794_736_150_463.0)), 1_794_736_150_463)

        let syncTs = Int64(Date().timeIntervalSince1970 * 1000.0) - 5_000
        let todayStart = DateEpochConverter.startOfDayMillis(for: Date())
        let parsedTask = FirebaseCloudSyncService.parseTask([
            "id": "remote-sync-task-1",
            "title": "Synced From ChromeOS",
            "notes": "Realtime delta verification",
            "status": "OPEN",
            "scheduleState": "TODAY",
            "whenDate": todayStart,
            "sortOrder": Int64(7),
            "createdAt": syncTs - 500,
            "updatedAt": syncTs,
            "isDeleted": false,
            "tagIds": []
        ])
        XCTAssertNotNil(parsedTask)
        XCTAssertEqual(parsedTask?.0.sortOrder, 7.0)

        // 4. Verify applyRemoteDeltas triggers onRemoteSyncApplied (NOT onMutation), leaves dirty batch empty, and indexes FTS5
        final class CounterBox: @unchecked Sendable {
            var mutations = 0
            var remoteApplies = 0
        }
        let box = CounterBox()
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        db.onMutation = { box.mutations += 1 }
        db.onRemoteSyncApplied = { box.remoteApplies += 1 }

        if let (remoteItem, remoteTags) = parsedTask {
            let maxUpdatedAt = try db.applyRemoteDeltas(
                RemoteDeltaBatch(
                    tasks: [remoteItem],
                    taskTagIdsByTaskId: [remoteItem.id: remoteTags]
                )
            )
            XCTAssertEqual(maxUpdatedAt, syncTs)
        }
        XCTAssertEqual(box.mutations, 0, "Remote delta apply must not mark local DB mutated or bounce sync")
        XCTAssertEqual(box.remoteApplies, 1, "Remote delta apply must notify UI observer immediately")
        XCTAssertTrue(try db.fetchDirtyBatch().tasks.isEmpty, "Remote delta records must have syncDirty = 0")

        let searchHits = try db.searchQuickFind(rawQuery: "ChromeOS")
        XCTAssertEqual(searchHits.tasks.map(\.id), ["remote-sync-task-1"], "FTS5 must index remotely synced tasks")
    }

    // MARK: - 7. v2.0.0-beta.11 AppUpdateService Beta Channel, Stable Isolation & Version Bump

    func testAppUpdateServiceSemanticVersioningAndBetaIsolationV181() {
        XCTAssertEqual(AppUpdateService.currentVersion, "3.0.0-alpha.6")
        XCTAssertEqual(AppUpdateService.displayVersion, "3.0.0-alpha.6")
        XCTAssertEqual(AppUpdateService.currentBuildNumber, 80)
        XCTAssertTrue(AppUpdateService.currentVersionIsPrerelease)
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "3.0.0-alpha.6", current: "2.1.2", includeBeta: false))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "3.0.0-alpha.6", current: "2.1.2", includeBeta: true))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "3.0.0-alpha.7", current: "3.0.0-alpha.6"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "3.0.0", current: "3.0.0-alpha.6"))
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            XCTAssertFalse(ThingsStrings.shared.text("v2_beta_channel_desc", lang: lang).contains("2.0"))
        }
        XCTAssertEqual(
            FirebaseCloudSyncService.deriveUidWakeTopic(uid: " user-abc-123 "),
            "tb_uid_9d8ecb07e35952112a013d4819b4"
        )

        // Core [major, minor, patch] + build metadata comparisons on stable channel (includeBeta inferred false for 1.8.1)
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "1.8.1", current: "1.8.0+1"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "1.8.0+1", current: "1.8.0"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.1", current: "1.8.1"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.1", current: "1.8.1", includeBeta: false))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "v2.0.0-alpha.1", current: "1.8.1", includeBeta: false))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "v2.0.0-rc.1+2", current: "1.8.1+1", includeBeta: false))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "1.8.1", current: "1.8.1"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "v1.8.2", current: "1.8.1"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "v2.0.0", current: "1.8.1", includeBeta: false))

        // Opt-in beta channel from stable 1.8.1 accepts 2.0.0-beta.1
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.1", current: "1.8.1", includeBeta: true))

        // Pre-release channel users upgrade across numeric pre-release tokens and to final 2.0.0
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.1", current: "2.0.0-beta.1"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "1.8.2", current: "2.0.0-beta.1"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.2", current: "2.0.0-beta.1"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.3", current: "2.0.0-beta.2"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.4", current: "2.0.0-beta.3"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.5", current: "2.0.0-beta.4"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.6", current: "2.0.0-beta.5"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.7", current: "2.0.0-beta.6"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.8", current: "2.0.0-beta.7"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.9", current: "2.0.0-beta.8"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.10", current: "2.0.0-beta.9"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.11", current: "2.0.0-beta.10"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.12", current: "2.0.0-beta.11"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.13", current: "2.0.0-beta.12"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.14", current: "2.0.0-beta.13"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.15", current: "2.0.0-beta.14"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.16", current: "2.0.0-beta.15"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.17", current: "2.0.0-beta.16"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.18", current: "2.0.0-beta.17"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.19", current: "2.0.0-beta.18"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.20", current: "2.0.0-beta.19"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.21", current: "2.0.0-beta.20"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.22", current: "2.0.0-beta.21"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.23", current: "2.0.0-beta.22"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.24", current: "2.0.0-beta.23"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.25", current: "2.0.0-beta.24"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.26", current: "2.0.0-beta.25"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.9", current: "2.0.0-beta.10"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0", current: "2.0.0-beta.1"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0", current: "2.0.0-beta.26"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0", current: "2.0.0-beta.99"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.99", current: "2.0.0", includeBeta: true))

        // Release feed filtering: reject draft, macos-build-*, and Android-only releases without .dmg;
        // enforce beta isolation when includeBeta == false while allowing beta upgrades when includeBeta == true!
        let mockReleases: [[String: Any]] = [
            [
                "tag_name": "v1.9.0",
                "draft": true,
                "prerelease": false,
                "assets": [["name": "Thingsbook-1.9.0.dmg", "browser_download_url": "https://example.com/1.9.0.dmg"]]
            ],
            [
                "tag_name": "v2.0.0-beta.1",
                "draft": false,
                "prerelease": false, // Even if someone forgot to tick GitHub's prerelease checkbox!
                "assets": [["name": "Thingsbook-2.0.0-beta.1.dmg", "browser_download_url": "https://example.com/2.0.0-beta.1.dmg"]]
            ],
            [
                "tag_name": "macos-build-99",
                "draft": false,
                "prerelease": false,
                "assets": [["name": "Thingsbook-CI.dmg", "browser_download_url": "https://example.com/ci.dmg"]]
            ],
            [
                "tag_name": "v1.8.3",
                "draft": false,
                "prerelease": false,
                "assets": [["name": "app-release.apk", "browser_download_url": "https://example.com/app-release.apk"]]
            ],
            [
                "tag_name": "v1.8.2",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/petrosvassiliou/thingsbook-android/releases/tag/v1.8.2",
                "body": "Bug fixes for macOS",
                "assets": [
                    ["name": "app-release.apk", "browser_download_url": "https://example.com/1.8.2.apk"],
                    ["name": "Thingsbook-1.8.2-Universal.dmg", "browser_download_url": "https://example.com/Thingsbook-1.8.2.dmg"]
                ]
            ]
        ]

        // 1. Stable 1.8.1 without beta opt-in STILL strictly rejects v2.0.0-beta.1 and selects v1.8.2
        let selectedStable = AppUpdateService.selectEligibleRelease(from: mockReleases, current: "1.8.1", includeBeta: false)
        XCTAssertNotNil(selectedStable)
        XCTAssertEqual(selectedStable?.tagName, "v1.8.2")
        XCTAssertEqual(selectedStable?.versionString, "1.8.2")
        XCTAssertEqual(selectedStable?.dmgDownloadUrl, "https://example.com/Thingsbook-1.8.2.dmg")
        XCTAssertNil(AppUpdateService.selectEligibleRelease(from: mockReleases, current: "1.8.2", includeBeta: false))

        // 2. Stable 1.8.1 with includeBeta: true upgrades to v2.0.0-beta.1
        let selectedBetaOptIn = AppUpdateService.selectEligibleRelease(from: mockReleases, current: "1.8.1", includeBeta: true)
        XCTAssertEqual(selectedBetaOptIn?.tagName, "v2.0.0-beta.1")
        XCTAssertEqual(selectedBetaOptIn?.dmgDownloadUrl, "https://example.com/2.0.0-beta.1.dmg")

        // 3. Running 2.0.0-beta.1 or 2.0.0-beta.9 upgrades to highest beta EVEN if includeBeta: false was persisted from 1.8.x!
        let betaProgressionFeed: [[String: Any]] = mockReleases + [
            [
                "tag_name": "v2.0.0-beta.9",
                "draft": false,
                "prerelease": true,
                "assets": [["name": "Thingsbook-2.0.0-beta.9.dmg", "browser_download_url": "https://example.com/2.0.0-beta.9.dmg"]]
            ],
            [
                "tag_name": "v2.0.0-beta.2",
                "draft": false,
                "prerelease": true,
                "assets": [["name": "Thingsbook-2.0.0-beta.2.dmg", "browser_download_url": "https://example.com/2.0.0-beta.2.dmg"]]
            ],
            [
                "tag_name": "v2.0.0-beta.10",
                "draft": false,
                "prerelease": true,
                "assets": [["name": "Thingsbook-2.0.0-beta.10.dmg", "browser_download_url": "https://example.com/2.0.0-beta.10.dmg"]]
            ]
        ]
        let selectedNextBeta = AppUpdateService.selectEligibleRelease(from: betaProgressionFeed, current: "2.0.0-beta.1")
        XCTAssertEqual(selectedNextBeta?.tagName, "v2.0.0-beta.10")
        let selectedFromBeta9WithFalsePref = AppUpdateService.selectEligibleRelease(
            from: betaProgressionFeed,
            current: "2.0.0-beta.9",
            includeBeta: false
        )
        XCTAssertEqual(selectedFromBeta9WithFalsePref?.tagName, "v2.0.0-beta.10")

        // 4. Raw PARITY_MANIFEST.json fallback works when GitHub REST API is rate-limited
        let manifestJson = #"{"macosVersion":"2.0.0-beta.12","syncedAndroidVersionName":"2.0.0-beta.12"}"#.data(using: .utf8)!
        let fallbackBeta = AppUpdateService.parseFallbackParityManifestData(manifestJson, current: "2.0.0-beta.9", includeBeta: false)
        XCTAssertEqual(fallbackBeta?.tagName, "v2.0.0-beta.12")
        XCTAssertEqual(
            fallbackBeta?.dmgDownloadUrl,
            "https://github.com/petrosvassiliou/thingsbook-android/releases/download/v2.0.0-beta.12/Thingsbook-macOS-Universal.dmg"
        )
        XCTAssertNil(AppUpdateService.parseFallbackParityManifestData(manifestJson, current: "1.8.1", includeBeta: false))

        let finalFeed: [[String: Any]] = betaProgressionFeed + [
            [
                "tag_name": "v2.0.0",
                "draft": false,
                "prerelease": false,
                "assets": [["name": "Thingsbook-2.0.0.dmg", "browser_download_url": "https://example.com/2.0.0.dmg"]]
            ]
        ]
        let selectedFinal = AppUpdateService.selectEligibleRelease(from: finalFeed, current: "2.0.0-beta.1")
        XCTAssertEqual(selectedFinal?.tagName, "v2.0.0")
    }

    // MARK: - 8. Out-of-Order Foreign Keys in Cloud Sync Deltas & .tbbackup Restore

    func testOutOfOrderForeignKeysInRemoteDeltasAndBackupRestoreV181() throws {
        XCTAssertEqual(ThingsBackupCodec.maxCompressedBytes, 80 * 1024 * 1024)
        XCTAssertEqual(ThingsBackupCodec.maxDecompressedBytes, 120 * 1024 * 1024)

        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let ts = Int64(Date().timeIntervalSince1970 * 1000.0) - 10_000

        // 1. Out-of-order tags (grandchild -> child -> parent) AND task referencing missing/not-yet-synced project/heading
        let grandchildTag = Tag(id: "tag-grandchild", title: "iOS", parentTagId: "tag-child", createdAt: ts, updatedAt: ts)
        let childTag = Tag(id: "tag-child", title: "Mobile", parentTagId: "tag-root", createdAt: ts, updatedAt: ts)
        let rootTag = Tag(id: "tag-root", title: "Engineering", parentTagId: nil, createdAt: ts, updatedAt: ts)

        let orphanContainerTask = TaskItem(
            id: "task-before-parent",
            title: "Task Arriving Before Project Delta",
            scheduleState: .anytime,
            projectId: "proj-arriving-later",
            headingId: "hdg-arriving-later",
            areaId: "area-arriving-later",
            createdAt: ts,
            updatedAt: ts
        )
        let chkBeforeParent = ChecklistItem(
            id: "chk-1",
            taskId: "task-before-parent",
            title: "Substep 1",
            createdAt: ts,
            updatedAt: ts
        )

        // Must succeed without SQLITE_CONSTRAINT_FOREIGNKEY because PRAGMA foreign_keys = OFF runs BEFORE BEGIN TRANSACTION
        _ = try db.applyRemoteDeltas(
            RemoteDeltaBatch(
                tags: [grandchildTag, childTag, rootTag],
                tasks: [orphanContainerTask],
                taskTagIdsByTaskId: ["task-before-parent": ["tag-grandchild"]],
                checklistItems: [chkBeforeParent]
            )
        )

        let snapAfterDelta = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(snapAfterDelta.tags.count, 3)
        XCTAssertEqual(snapAfterDelta.allOpenTasks.first?.id, "task-before-parent")
        XCTAssertEqual(snapAfterDelta.allOpenTasks.first?.tagIds, ["tag-grandchild"])
        XCTAssertEqual(snapAfterDelta.allOpenTasks.first?.checklistItems.count, 1)

        // 2. Out-of-order .tbbackup importSnapshotReplacingAll (child tags before parent tags + full graph)
        let backupPayload = ThingsbookBackupPayload(
            schemaVersion: 1,
            createdAt: ts,
            appVersionName: "1.8.1",
            areas: [BackupAreaDto(id: "b-area", title: "Work", sortOrder: 1, createdAt: ts, updatedAt: ts)],
            projects: [BackupProjectDto(id: "b-proj", areaId: "b-area", title: "Q4 Ship", createdAt: ts, updatedAt: ts)],
            headings: [BackupHeadingDto(id: "b-hdg", projectId: "b-proj", title: "Phase 1", createdAt: ts, updatedAt: ts)],
            tasks: [
                BackupTaskDto(
                    id: "b-task",
                    title: "Verify FK Restore",
                    scheduleState: ScheduleState.anytime.rawValue,
                    projectId: "b-proj",
                    headingId: "b-hdg",
                    areaId: "b-area",
                    tagIdsJson: "[\"b-tag-leaf\"]",
                    createdAt: ts,
                    updatedAt: ts
                )
            ],
            checklistItems: [BackupChecklistItemDto(id: "b-chk", taskId: "b-task", title: "Done", createdAt: ts, updatedAt: ts)],
            tags: [
                BackupTagDto(id: "b-tag-leaf", title: "Leaf", parentTagId: "b-tag-mid", createdAt: ts, updatedAt: ts),
                BackupTagDto(id: "b-tag-mid", title: "Mid", parentTagId: "b-tag-top", createdAt: ts, updatedAt: ts),
                BackupTagDto(id: "b-tag-top", title: "Top", parentTagId: nil, createdAt: ts, updatedAt: ts)
            ],
            taskTagRefs: [BackupTaskTagRefDto(taskId: "b-task", tagId: "b-tag-leaf")],
            projectTagRefs: [BackupProjectTagRefDto(projectId: "b-proj", tagId: "b-tag-top")]
        )

        try db.importSnapshotReplacingAll(backupPayload)
        let restored = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(restored.tags.count, 3)
        XCTAssertEqual(restored.projects.first?.tagIds, ["b-tag-top"])
        XCTAssertEqual(restored.allOpenTasks.first?.projectId, "b-proj")
        XCTAssertEqual(restored.allOpenTasks.first?.headingId, "b-hdg")
        XCTAssertEqual(restored.allOpenTasks.first?.tagIds, ["b-tag-leaf"])
    }

    // MARK: - 9. Remote Container Tombstone Detachment & Tag Delete + Same-Title Recreation

    func testRemoteDeletedContainersDetachLocalTasksAndTagRecreationV181() throws {
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let area = try db.createArea(title: "Operations")
        let proj = try db.createProject(title: "Server Migration", areaId: area.id)
        let hdg = try db.createHeading(projectId: proj.id, title: "Cutover")
        let task = try db.createTask(
            title: "Verify DNS TTL",
            scheduleState: .anytime,
            projectId: proj.id,
            headingId: hdg.id,
            areaId: area.id
        )

        let remoteDeleteTs = Int64(Date().timeIntervalSince1970 * 1000.0) + 1_000
        let deletedHeading = Heading(
            id: hdg.id,
            projectId: proj.id,
            title: hdg.title,
            sortOrder: hdg.sortOrder,
            createdAt: hdg.createdAt,
            updatedAt: remoteDeleteTs,
            isDeleted: true
        )
        let deletedProject = Project(
            id: proj.id,
            areaId: area.id,
            title: proj.title,
            createdAt: proj.createdAt,
            updatedAt: remoteDeleteTs,
            isDeleted: true
        )
        let deletedArea = Area(
            id: area.id,
            title: area.title,
            sortOrder: area.sortOrder,
            createdAt: area.createdAt,
            updatedAt: remoteDeleteTs,
            isDeleted: true
        )

        _ = try db.applyRemoteDeltas(
            RemoteDeltaBatch(
                areas: [deletedArea],
                projects: [deletedProject],
                headings: [deletedHeading]
            )
        )

        let snap = try db.loadWorkspaceSnapshot()
        guard let survivingTask = snap.allOpenTasks.first(where: { $0.id == task.id }) else {
            return XCTFail("Local open task must not vanish when its remote Project/Area/Heading is deleted")
        }
        XCTAssertNil(survivingTask.projectId)
        XCTAssertNil(survivingTask.headingId)
        XCTAssertNil(survivingTask.areaId)
        XCTAssertTrue(snap.anytimeTasks.contains { $0.id == task.id })

        // Partial unique tag index: delete a tag and create a new tag with the exact same title
        let activeProj = try db.createProject(title: "Tag Host Project")
        let tagV1 = try db.createOrUpdateTag(title: "Urgent", shortcut: "u")
        var taggedTask = survivingTask
        try db.updateTask(taggedTask, tagIds: [tagV1.id])
        var updatedProj = activeProj
        updatedProj.tagIds = [tagV1.id]
        try db.updateProject(updatedProj)

        try db.deleteTag(tagV1.id)
        let afterTagDelete = try db.loadWorkspaceSnapshot()
        XCTAssertTrue(afterTagDelete.tags.isEmpty)
        XCTAssertEqual(afterTagDelete.allOpenTasks.first(where: { $0.id == task.id })?.tagIds, [], "Deleted tag ID must not resurrect via stale tag_ids_json")
        XCTAssertEqual(afterTagDelete.projects.first(where: { $0.id == activeProj.id })?.tagIds, [], "Deleted tag ID must be scrubbed from project tagIds")

        // Creating a new tag with identical title "Urgent" must succeed cleanly and keep the old tag tombstone dirty for sync
        let tagV2 = try db.createOrUpdateTag(title: "Urgent", shortcut: "x")
        XCTAssertNotEqual(tagV2.id, tagV1.id)
        let afterRecreate = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(afterRecreate.tags.map(\.id), [tagV2.id])

        taggedTask = afterRecreate.allOpenTasks.first(where: { $0.id == task.id })!
        try db.updateTask(taggedTask, tagIds: [tagV2.id])
        XCTAssertEqual(try db.loadWorkspaceSnapshot().allOpenTasks.first(where: { $0.id == task.id })?.tagIds, [tagV2.id])

        let dirtyTags = try db.fetchDirtyBatch().tags
        XCTAssertTrue(dirtyTags.contains { $0.id == tagV1.id && $0.isDeleted }, "Deleted tag tombstone must remain preserved for Cloud Sync")
        XCTAssertTrue(dirtyTags.contains { $0.id == tagV2.id && !$0.isDeleted })
    }

    // MARK: - 10. LWW Timestamp Tie Idempotency & Fast-Clock Cursor Clamp

    func testLwwTimestampTieIdempotencyAndClockSkewClampV181() throws {
        final class SyncCounter: @unchecked Sendable {
            var remoteCallbacks = 0
        }
        let counter = SyncCounter()
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        db.onRemoteSyncApplied = { counter.remoteCallbacks += 1 }

        let baseTs = Int64(Date().timeIntervalSince1970 * 1000.0) - 2_000
        let remoteTask = TaskItem(
            id: "lww-task-1",
            title: "Initial Remote Title",
            scheduleState: .inbox,
            createdAt: baseTs,
            updatedAt: baseTs
        )

        // First apply: inserts record and notifies once
        _ = try db.applyRemoteDeltas(RemoteDeltaBatch(tasks: [remoteTask]))
        XCTAssertEqual(db.lastRemoteAppliedCount, 1)
        XCTAssertEqual(counter.remoteCallbacks, 1)

        // Idle 15s poll re-delivering the exact same document (remote.updatedAt == local.updatedAt): must NOT re-apply or rebuild FTS!
        var staleTieCopy = remoteTask
        staleTieCopy.title = "Should Not Overwrite On Timestamp Tie"
        _ = try db.applyRemoteDeltas(RemoteDeltaBatch(tasks: [staleTieCopy]))
        XCTAssertEqual(db.lastRemoteAppliedCount, 0)
        XCTAssertEqual(counter.remoteCallbacks, 1)
        XCTAssertEqual(try db.loadWorkspaceSnapshot().inboxTasks.first?.title, "Initial Remote Title")

        // Strictly newer remote timestamp (> local.updatedAt) applies cleanly
        var newerCopy = remoteTask
        newerCopy.title = "Updated Remote Title"
        newerCopy.updatedAt = baseTs + 500
        _ = try db.applyRemoteDeltas(RemoteDeltaBatch(tasks: [newerCopy]))
        XCTAssertEqual(db.lastRemoteAppliedCount, 1)
        XCTAssertEqual(counter.remoteCallbacks, 2)
        XCTAssertEqual(try db.loadWorkspaceSnapshot().inboxTasks.first?.title, "Updated Remote Title")

        // Future peer clock (+1 hour ahead) must be clamped to <= now + 60_000ms so sync cursor is never poisoned
        let oneHourAhead = Int64(Date().timeIntervalSince1970 * 1000.0) + 3_600_000
        var futureTask = newerCopy
        futureTask.id = "future-clock-task"
        futureTask.updatedAt = oneHourAhead
        let clampedCursor = try db.applyRemoteDeltas(RemoteDeltaBatch(tasks: [futureTask]))
        let upperBound = Int64(Date().timeIntervalSince1970 * 1000.0) + 65_000
        XCTAssertLessThanOrEqual(clampedCursor, upperBound, "Fast peer clock must be clamped to now + 60s")
    }

    // MARK: - 11. Trashed Projects, Detached Task Restore, Syncable Purge Tombstones & Smart View Rollover

    func testTrashProjectsRestoreDetachedTasksTombstonePurgeAndSmartViewRolloverV181() throws {
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let proj = try db.createProject(title: "Redesign Website", notes: "v1.8.1 Trash Project Flow")
        let hdg = try db.createHeading(projectId: proj.id, title: "Wireframes")
        let taskInProj = try db.createTask(
            title: "Export Hero SVG",
            scheduleState: .anytime,
            projectId: proj.id,
            headingId: hdg.id,
            checklistTitles: ["Check retina 2x"]
        )
        _ = try db.addAttachment(
            projectId: proj.id,
            fileName: "brief.txt",
            mimeType: "text/plain",
            data: Data("brief".utf8)
        )

        // 1. Soft-deleting project moves it to trashedProjects, detaches its open task, and cascades child headings/attachments
        _ = try db.softDeleteProject(proj.id)
        var snap = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(snap.trashedProjects.map(\.id), [proj.id])
        XCTAssertTrue(snap.projects.isEmpty)
        XCTAssertNil(snap.allOpenTasks.first(where: { $0.id == taskInProj.id })?.projectId)
        XCTAssertNil(snap.allOpenTasks.first(where: { $0.id == taskInProj.id })?.headingId)

        let dirtyAfterSoftDelete = try db.fetchDirtyBatch()
        XCTAssertTrue(dirtyAfterSoftDelete.headings.contains { $0.id == hdg.id && $0.isDeleted })
        XCTAssertTrue(dirtyAfterSoftDelete.attachments.contains { $0.projectId == proj.id && $0.isDeleted })

        // 2. Restoring project from Trash restores both the project AND its detached tasks & headings!
        try db.restoreProject(proj.id)
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertTrue(snap.trashedProjects.isEmpty)
        XCTAssertEqual(snap.projects.map(\.id), [proj.id])
        XCTAssertEqual(snap.headingsByProject[proj.id]?.map(\.id), [hdg.id])
        XCTAssertEqual(snap.allOpenTasks.first(where: { $0.id == taskInProj.id })?.projectId, proj.id)
        XCTAssertEqual(snap.allOpenTasks.first(where: { $0.id == taskInProj.id })?.headingId, hdg.id)

        // 3. Completed/Canceled projects hide their open tasks from active Smart Views
        var completedProj = snap.projects.first!
        completedProj.status = .completed
        try db.updateProject(completedProj)
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertFalse(snap.anytimeTasks.contains { $0.id == taskInProj.id }, "Tasks in completed projects must not leak into Anytime")
        completedProj.status = .active
        try db.updateProject(completedProj)

        // 4. Permanent delete & Empty Trash keep compact dirty tombstones (preventing forceFullPull resurrection!)
        let doomedTask = try db.createTask(title: "Secret Draft", notes: "Sensitive notes", scheduleState: .inbox)
        try db.markDirtyBatchSynced(try db.fetchDirtyBatch())

        try db.softDeleteTask(doomedTask.id)
        try db.softDeleteProject(proj.id)
        try db.emptyTrash()

        snap = try db.loadWorkspaceSnapshot()
        XCTAssertTrue(snap.trashedTasks.isEmpty)
        XCTAssertTrue(snap.trashedProjects.isEmpty)

        let dirtyAfterPurge = try db.fetchDirtyBatch()
        guard let purgedTaskTombstone = dirtyAfterPurge.tasks.first(where: { $0.id == doomedTask.id }) else {
            return XCTFail("Permanently deleted task must retain a dirty tombstone for Cloud Sync push")
        }
        XCTAssertTrue(purgedTaskTombstone.isDeleted)
        XCTAssertEqual(purgedTaskTombstone.title, "")
        XCTAssertEqual(purgedTaskTombstone.notes, "")

        guard let purgedProjTombstone = dirtyAfterPurge.projects.first(where: { $0.id == proj.id }) else {
            return XCTFail("Permanently deleted project must retain a dirty tombstone for Cloud Sync push")
        }
        XCTAssertTrue(purgedProjTombstone.isDeleted)
        XCTAssertEqual(purgedProjTombstone.title, "")

        // Simulate older remote copy arriving during forceFullPull: tombstone updatedAt wins so item NEVER resurrects!
        let olderRemoteTask = TaskItem(
            id: doomedTask.id,
            title: "Resurrected From Stale Cloud?",
            scheduleState: .inbox,
            createdAt: doomedTask.createdAt,
            updatedAt: doomedTask.updatedAt
        )
        _ = try db.applyRemoteDeltas(RemoteDeltaBatch(tasks: [olderRemoteTask]))
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertFalse(snap.allOpenTasks.contains { $0.id == doomedTask.id })
        XCTAssertTrue(snap.trashedTasks.isEmpty)

        // 5. Midnight rollover rolls stale THIS_EVENING tasks and due UPCOMING projects into TODAY
        let yesterdayMillis = DateEpochConverter.startOfDayMillis(for: Calendar.current.date(byAdding: .day, value: -1, to: Date())!)
        let staleEvening = try db.createTask(
            title: "Yesterday Evening Task",
            scheduleState: .thisEvening,
            whenDate: yesterdayMillis
        )
        var dueProject = try db.createProject(title: "Due Project")
        dueProject.scheduleState = .upcoming
        dueProject.whenDate = yesterdayMillis
        try db.updateProject(dueProject)

        let rolledCount = try db.performMidnightRollover()
        XCTAssertGreaterThanOrEqual(rolledCount, 2)
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertTrue(snap.todayDaytimeTasks.contains { $0.id == staleEvening.id && $0.scheduleState == .today })
        XCTAssertFalse(snap.inboxTasks.contains { $0.id == staleEvening.id }, "Inbox bucket must be mutually exclusive with Today")
        XCTAssertEqual(snap.projects.first(where: { $0.id == dueProject.id })?.scheduleState, .today)
    }

    // MARK: - 12. Cloud Sync Attachment Security & Firestore updateMask Preservation

    func testAttachmentSecuritySanitizationAndFirestoreUpdateMaskV181() {
        XCTAssertEqual(FirebaseCloudSyncService.maxInlineAttachmentBytes, 700_000)

        // 1. Path traversal sanitization in parseAttachment
        let maliciousRemote: [String: Any] = [
            "id": "att-sec-1",
            "taskId": "task-1",
            "fileName": "invoice.pdf",
            "mimeType": "application/pdf",
            "sizeBytes": 1024,
            "localRelativePath": "../../../../etc/passwd",
            "dataBase64": "JVBERi0xLjQ=",
            "createdAt": 1_750_000_000_000,
            "updatedAt": 1_750_000_000_000,
            "isDeleted": false
        ]
        let parsedDelta = FirebaseCloudSyncService.parseAttachment(maliciousRemote)
        XCTAssertNotNil(parsedDelta)
        XCTAssertEqual(parsedDelta?.attachment.localRelativePath, "", "Remote localRelativePath must always be stripped to empty string")

        // 2. Non-deleted attachment with missing/oversized local bytes uses updateMask EXCLUDING dataBase64
        let oversizedAtt = TaskAttachment(
            id: "att-large-1",
            taskId: "task-1",
            fileName: "highres.png",
            mimeType: "image/png",
            sizeBytes: 950_000,
            localRelativePath: "attachments/att-large-1.png",
            createdAt: 1_750_000_000_000,
            updatedAt: 1_750_000_001_000,
            isDeleted: false
        )
        let metadataOnlyWrite = FirebaseCloudSyncService.makeAttachmentWrite(
            baseDocPrefix: "projects/demo/databases/(default)/documents/users/u1",
            attachment: oversizedAtt,
            inlineBase64: ""
        )
        let updateObj = metadataOnlyWrite["update"] as? [String: Any]
        let fieldsObj = updateObj?["fields"] as? [String: [String: Any]]
        let maskPaths = (metadataOnlyWrite["updateMask"] as? [String: Any])?["fieldPaths"] as? [String]

        XCTAssertNil(fieldsObj?["dataBase64"], "Oversized/unavailable attachment push must NOT write empty dataBase64")
        XCTAssertNotNil(maskPaths, "Metadata-only attachment update must specify updateMask.fieldPaths")
        XCTAssertFalse(maskPaths?.contains("dataBase64") ?? true, "updateMask.fieldPaths must exclude dataBase64")
        XCTAssertTrue(maskPaths?.contains("fileName") ?? false)
        XCTAssertTrue(maskPaths?.contains("updatedAt") ?? false)

        // 3. Deleted attachment explicitly clears dataBase64 = "" without restrictive mask
        var deletedAtt = oversizedAtt
        deletedAtt.isDeleted = true
        let deletedWrite = FirebaseCloudSyncService.makeAttachmentWrite(
            baseDocPrefix: "projects/demo/databases/(default)/documents/users/u1",
            attachment: deletedAtt,
            inlineBase64: ""
        )
        let delFields = (deletedWrite["update"] as? [String: Any])?["fields"] as? [String: [String: Any]]
        XCTAssertEqual(delFields?["dataBase64"]?["stringValue"] as? String, "")
        XCTAssertNil(deletedWrite["updateMask"])
    }

    // MARK: - 13. v1.8.1 -> v2.0.0-beta.1 In-Place SQLite Migration, .tbbackup & Flagship Features

    func testUpgradeFromV181SQLiteAndBackupToV200Beta1() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("tb-v181-upgrade-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let rawDbURL = tempDir.appendingPathComponent("thingsbook.sqlite")

        // 1. Construct an authentic raw v1.8.1 SQLite database WITHOUT scheduled_start_minutes,
        //    duration_minutes, last_reviewed_at, or project_templates table.
        do {
            var rawHandle: OpaquePointer?
            XCTAssertEqual(sqlite3_open(rawDbURL.path, &rawHandle), SQLITE_OK)
            defer { if let rawHandle { sqlite3_close(rawHandle) } }

            let legacyV181SchemaAndData = """
            CREATE TABLE areas (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                sort_order REAL NOT NULL DEFAULT 0.0,
                tag_ids_json TEXT NOT NULL DEFAULT '[]',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                is_dirty INTEGER NOT NULL DEFAULT 1
            );
            CREATE TABLE projects (
                id TEXT PRIMARY KEY NOT NULL,
                area_id TEXT REFERENCES areas(id) ON DELETE SET NULL,
                title TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                schedule_state TEXT NOT NULL DEFAULT 'ANYTIME',
                when_date INTEGER,
                deadline INTEGER,
                completed_at INTEGER,
                sort_order REAL NOT NULL DEFAULT 0.0,
                tag_ids_json TEXT NOT NULL DEFAULT '[]',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                is_purged INTEGER NOT NULL DEFAULT 0,
                is_dirty INTEGER NOT NULL DEFAULT 1
            );
            CREATE TABLE headings (
                id TEXT PRIMARY KEY NOT NULL,
                project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
                title TEXT NOT NULL,
                sort_order REAL NOT NULL DEFAULT 0.0,
                is_archived INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                is_dirty INTEGER NOT NULL DEFAULT 1
            );
            CREATE TABLE tasks (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'OPEN',
                schedule_state TEXT NOT NULL DEFAULT 'INBOX',
                when_date INTEGER,
                deadline INTEGER,
                reminder_time INTEGER,
                completed_at INTEGER,
                project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
                heading_id TEXT REFERENCES headings(id) ON DELETE SET NULL,
                area_id TEXT REFERENCES areas(id) ON DELETE SET NULL,
                recurrence_rule TEXT,
                sort_order REAL NOT NULL DEFAULT 0.0,
                tag_ids_json TEXT NOT NULL DEFAULT '[]',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                is_purged INTEGER NOT NULL DEFAULT 0,
                is_dirty INTEGER NOT NULL DEFAULT 1
            );
            CREATE TABLE checklist_items (
                id TEXT PRIMARY KEY NOT NULL,
                task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
                title TEXT NOT NULL,
                is_completed INTEGER NOT NULL DEFAULT 0,
                sort_order REAL NOT NULL DEFAULT 0.0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                is_dirty INTEGER NOT NULL DEFAULT 1
            );
            CREATE TABLE tags (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                parent_tag_id TEXT REFERENCES tags(id) ON DELETE SET NULL,
                shortcut TEXT,
                sort_order REAL NOT NULL DEFAULT 0.0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                is_dirty INTEGER NOT NULL DEFAULT 1
            );
            CREATE TABLE task_tags (
                task_id TEXT NOT NULL,
                tag_id TEXT NOT NULL,
                PRIMARY KEY (task_id, tag_id)
            );
            CREATE TABLE project_tags (
                project_id TEXT NOT NULL,
                tag_id TEXT NOT NULL,
                PRIMARY KEY (project_id, tag_id)
            );
            CREATE TABLE attachments (
                id TEXT PRIMARY KEY NOT NULL,
                task_id TEXT,
                project_id TEXT,
                file_name TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL DEFAULT 0,
                local_relative_path TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                is_dirty INTEGER NOT NULL DEFAULT 0
            );

            INSERT INTO areas (id, title, sort_order, tag_ids_json, created_at, updated_at, is_deleted, is_dirty)
            VALUES ('area-1', 'Engineering', 1000.0, '[]', 1760000000000, 1760000000000, 0, 0);

            INSERT INTO tags (id, title, parent_tag_id, shortcut, sort_order, created_at, updated_at, is_deleted, is_dirty)
            VALUES ('tag-1', 'Deep Work', NULL, 'd', 100.0, 1760000000000, 1760000000000, 0, 0);

            INSERT INTO projects (id, area_id, title, notes, status, schedule_state, sort_order, tag_ids_json, created_at, updated_at, is_deleted, is_purged, is_dirty)
            VALUES ('proj-1', 'area-1', 'v1.8.1 Production App', 'Preserve every byte across 2.0 upgrade', 'ACTIVE', 'ANYTIME', 1000.0, '["tag-1"]', 1760000000000, 1760000000000, 0, 0, 0);

            INSERT INTO project_tags (project_id, tag_id) VALUES ('proj-1', 'tag-1');

            INSERT INTO headings (id, project_id, title, sort_order, is_archived, created_at, updated_at, is_deleted, is_dirty)
            VALUES ('head-1', 'proj-1', 'Core Engine', 1000.0, 0, 1760000000000, 1760000000000, 0, 0);

            INSERT INTO tasks (id, title, notes, status, schedule_state, when_date, project_id, heading_id, area_id, sort_order, tag_ids_json, created_at, updated_at, is_deleted, is_purged, is_dirty)
            VALUES ('task-1', 'Verify SQLite Migration', 'Zero data loss!', 'OPEN', 'TODAY', 1760000000000, 'proj-1', 'head-1', 'area-1', 1000.0, '["tag-1"]', 1760000000000, 1760000000000, 0, 0, 0);

            INSERT INTO tasks (id, title, notes, status, schedule_state, completed_at, project_id, sort_order, tag_ids_json, created_at, updated_at, is_deleted, is_purged, is_dirty)
            VALUES ('task-done-1', 'Shipped v1.8.1', '', 'COMPLETED', 'ANYTIME', 1760000500000, 'proj-1', 2000.0, '[]', 1760000000000, 1760000500000, 0, 0, 0);

            INSERT INTO task_tags (task_id, tag_id) VALUES ('task-1', 'tag-1');

            INSERT INTO checklist_items (id, task_id, title, is_completed, sort_order, created_at, updated_at, is_deleted, is_dirty)
            VALUES ('chk-1', 'task-1', 'Check tasks table columns', 1, 100.0, 1760000000000, 1760000000000, 0, 0);
            """
            var errMsg: UnsafeMutablePointer<CChar>?
            let rc = sqlite3_exec(rawHandle, legacyV181SchemaAndData, nil, nil, &errMsg)
            if let errMsg { sqlite3_free(errMsg) }
            XCTAssertEqual(rc, SQLITE_OK)
        }

        // 2. Open the v1.8.1 SQLite file in-place with v2.0.0-beta.1 ThingsSQLiteDatabase
        let upgradedDb = try ThingsSQLiteDatabase(path: rawDbURL.path)
        var snap = try upgradedDb.loadWorkspaceSnapshot()

        XCTAssertEqual(snap.areas.map(\.title), ["Engineering"])
        XCTAssertEqual(snap.projects.map(\.title), ["v1.8.1 Production App"])
        XCTAssertNil(snap.projects.first?.lastReviewedAt)
        XCTAssertEqual(snap.headingsByProject["proj-1"]?.map(\.title), ["Core Engine"])
        XCTAssertEqual(snap.allOpenTasks.map(\.title), ["Verify SQLite Migration"])
        XCTAssertEqual(snap.logbookTasks.map(\.title), ["Shipped v1.8.1"])
        XCTAssertEqual(snap.allOpenTasks.first?.checklistItems.map(\.title), ["Check tasks table columns"])
        XCTAssertEqual(snap.allOpenTasks.first?.tags.map(\.title), ["Deep Work"])
        XCTAssertNil(snap.allOpenTasks.first?.scheduledStartMinutes)
        XCTAssertNil(snap.allOpenTasks.first?.durationMinutes)
        XCTAssertTrue(snap.projectTemplates.isEmpty)

        // 3. Exercise v2.0 Timeblocking, Weekly Review, Project Templates & Markdown/JSON Importer
        try upgradedDb.setTaskTimeblock(taskId: "task-1", scheduledStartMinutes: 540, durationMinutes: 45)
        let reviewStamp: Int64 = 1_765_000_000_000
        try upgradedDb.markProjectReviewed(projectId: "proj-1", reviewedAt: reviewStamp)

        snap = try upgradedDb.loadWorkspaceSnapshot()
        let timeblockedTask = try XCTUnwrap(snap.allOpenTasks.first(where: { $0.id == "task-1" }))
        XCTAssertEqual(timeblockedTask.scheduledStartMinutes, 540)
        XCTAssertEqual(timeblockedTask.durationMinutes, 45)
        XCTAssertEqual(timeblockedTask.formattedTimeblockPill, "09:00–09:45 · 45m")
        XCTAssertEqual(snap.projects.first(where: { $0.id == "proj-1" })?.lastReviewedAt, reviewStamp)

        // Built-in starter templates + custom saved template + instantiation
        XCTAssertEqual(ProjectTemplate.builtInStarterTemplates.count, 4)
        let savedTemplate = try upgradedDb.saveProjectAsTemplate(projectId: "proj-1", templateTitle: "Reusable Release Playbook")
        snap = try upgradedDb.loadWorkspaceSnapshot()
        XCTAssertEqual(snap.projectTemplates.map(\.title), ["Reusable Release Playbook"])

        let instantiated = try upgradedDb.instantiateProjectFromTemplate(savedTemplate, titleOverride: "v2.0.0 Launch")
        snap = try upgradedDb.loadWorkspaceSnapshot()
        XCTAssertTrue(snap.projects.contains(where: { $0.id == instantiated.id && $0.title == "v2.0.0 Launch" }))
        XCTAssertEqual(snap.headingsByProject[instantiated.id]?.map(\.title), ["Core Engine"])
        let clonedTask = try XCTUnwrap(snap.allOpenTasks.first(where: { $0.projectId == instantiated.id }))
        XCTAssertEqual(clonedTask.title, "Verify SQLite Migration")
        XCTAssertEqual(clonedTask.scheduledStartMinutes, 540)
        XCTAssertEqual(clonedTask.durationMinutes, 45)
        XCTAssertEqual(clonedTask.checklistItems.map(\.title), ["Check tasks table columns"])

        // Markdown & JSON Outline Importer
        let markdownOutline = """
        # Q4 Architecture Review
        High-priority migration audit

        - [ ] Prepare kickoff deck
          - [ ] Gather metrics
          - [ ] Draft slides
        ## Security & Sync
        - [ ] Verify Firestore rules
        """
        let mdProject = try upgradedDb.importProjectFromMarkdownOrJson(markdownOutline, areaId: "area-1")
        snap = try upgradedDb.loadWorkspaceSnapshot()
        XCTAssertEqual(mdProject.title, "Q4 Architecture Review")
        XCTAssertEqual(snap.headingsByProject[mdProject.id]?.map(\.title), ["Security & Sync"])
        let mdTasks = snap.allOpenTasks.filter { $0.projectId == mdProject.id }
        XCTAssertEqual(mdTasks.count, 2)
        XCTAssertEqual(mdTasks.first(where: { $0.title == "Prepare kickoff deck" })?.checklistItems.count, 2)

        let jsonOutline = """
        {"title":"JSON Imported Sprint","notes":"From CLI","blueprint":{"unheadedTasks":[{"title":"Audit backups","notes":"","checklistItems":["v1.7","v1.8.1"]}],"headings":[{"title":"QA","tasks":[{"title":"Run XCTest","notes":"","checklistItems":[]}]}]}}
        """
        let jsonProject = try upgradedDb.importProjectFromMarkdownOrJson(jsonOutline)
        snap = try upgradedDb.loadWorkspaceSnapshot()
        XCTAssertEqual(jsonProject.title, "JSON Imported Sprint")
        XCTAssertEqual(snap.headingsByProject[jsonProject.id]?.map(\.title), ["QA"])

        // 4. Verify v2.0 Encrypted .tbbackup Roundtrip preserves templates, timeblocks, lastReviewedAt & includeBetaUpdates
        XCTAssertEqual(ThingsBackupCodec.backupMagic, "THINGSBOOK_BACKUP_V1")
        XCTAssertEqual(ThingsBackupCodec.formatVersion, 1)
        var v2Prefs = AppPreferences()
        v2Prefs.includeBetaUpdates = true
        let v2BackupPayload = try upgradedDb.buildBackupPayload(preferences: v2Prefs)
        XCTAssertEqual(v2BackupPayload.projectTemplates.count, 1)
        let v2Encrypted = try ThingsBackupCodec.encodeBackup(payload: v2BackupPayload, password: "Beta20UpgradePassword!")
        let v2Decoded = try ThingsBackupCodec.decodeBackup(rawBytes: v2Encrypted, password: "Beta20UpgradePassword!")
        XCTAssertEqual(v2Decoded.projectTemplates.map(\.title), ["Reusable Release Playbook"])
        XCTAssertEqual(v2Decoded.tasks.first(where: { $0.id == "task-1" })?.scheduledStartMinutes, 540)
        XCTAssertEqual(v2Decoded.tasks.first(where: { $0.id == "task-1" })?.durationMinutes, 45)
        XCTAssertEqual(v2Decoded.projects.first(where: { $0.id == "proj-1" })?.lastReviewedAt, reviewStamp)
        XCTAssertEqual(v2Decoded.preferences.includeBetaUpdates, true)

        // 5. Verify authentic v1.8.1 .tbbackup archive (missing v2.0 fields) restores cleanly into v2.0.0-beta.1!
        let v181BackupJson = """
        {"magic":"THINGSBOOK_BACKUP_V1","formatVersion":1,"appVersionName":"1.8.1","appVersionCode":44,"createdAt":1762000000000,"encrypted":false,"payloadGzipBase64":"H4sIAAAAAAACA71U3W/aMBD/VyI/d2jsYet4SwFtmcqHCqKq2j4c9pF4JE5kO0wM8b/vDkgXojBNaFre7s53/n2csxNOJpjBAq3TuRG97o2QFsGjCj1Fnz5+eF99NwKK4nRwDBmKnuh2bjtdQQVqcaL3vBNaUZrDd5vuLZe89ikffcztOthUDS63fmIVWrqDRndouIc4Uu6bYxTi+VX8CUhZqNbK/vVGFDb/jtLX4HCmgsPQoksY7zEGuQ0OIIN7KI1MqGpyjzRNRFlBqMH4wOagMiiYhwdfcjHsz6PFkDOkpypTnFGFZ4bjp3k0Gv4N5xeOD5hexPX8EwSlTVzjz5mK6kmdqKlLJcE0AYfBBYu0C61M9AapewWpw2sxenDrGkAOm0BmiS5O+xIMRl9qPtAK6tU2oBCs/gme97bmxGQ6HLf4MJ8MwifK/0jQDA6pBt5Lypz0jJpKtm7Sf/KYuMl1qp2PPGY1IWXyW0fSNLoorocixcBruUYv2Nh+nlHGs7PelthkwkSutTo+czpuYnnAlH4eyOIldKcsaYqw4l8icOs5xA+4OgJpVwbi6Axg7VdSb774ftr6wXuQSYbm8Dc6DMQVWjSSF3kn0lxCOjHpdrAc5QrfHpXTCpdgF9rpJWt0dESBppM6RueHBqigWipf85L0+nyWG2lT8sKTTimYuISYdUd+NXQ/GgV2uGGQb2MrHGSNJFn7p1O0x0cesc3LYp4r2N5tp0dFJjYkdypExLm8Azs7DZi2qrbf/wK06zVlfwYAAA=="}
        """
        let freshV2Db = try ThingsSQLiteDatabase(path: ":memory:")
        let decodedV181Backup = try ThingsBackupCodec.decodeBackup(rawBytes: Data(v181BackupJson.utf8), password: nil)
        XCTAssertTrue(decodedV181Backup.projectTemplates.isEmpty)
        XCTAssertNil(decodedV181Backup.tasks.first?.scheduledStartMinutes)
        XCTAssertNil(decodedV181Backup.projects.first?.lastReviewedAt)
        try freshV2Db.restoreBackupPayload(decodedV181Backup)
        let restoredV181Snap = try freshV2Db.loadWorkspaceSnapshot()
        XCTAssertEqual(restoredV181Snap.areas.map(\.title), ["Work v1.8.1"])
        XCTAssertEqual(restoredV181Snap.projects.map(\.title), ["Legacy 1.8.1 Launch"])
        XCTAssertEqual(restoredV181Snap.allOpenTasks.map(\.title), ["Ship v1.8.1 DMG"])

        // 6. Verify Cloud Sync legacy v1.8 & v2.0 document parsing + project_templates delta sync
        let legacyCloudTask = FirebaseCloudSyncService.parseTask([
            "id": "cloud-v18-task",
            "title": "From v1.8 Android",
            "status": "OPEN",
            "scheduleState": "TODAY",
            "updatedAt": Int64(1_766_000_000_000)
        ])
        XCTAssertNil(legacyCloudTask?.0.scheduledStartMinutes)
        XCTAssertNil(legacyCloudTask?.0.durationMinutes)

        let v2CloudTask = FirebaseCloudSyncService.parseTask([
            "id": "cloud-v20-task",
            "title": "Timeblocked on Android 2.0",
            "status": "OPEN",
            "scheduleState": "TODAY",
            "scheduledStartMinutes": Int64(630),
            "durationMinutes": Int64(60),
            "updatedAt": Int64(1_766_000_001_000)
        ])
        XCTAssertEqual(v2CloudTask?.0.scheduledStartMinutes, 630)
        XCTAssertEqual(v2CloudTask?.0.durationMinutes, 60)

        let remoteTemplate = try XCTUnwrap(FirebaseCloudSyncService.parseProjectTemplate([
            "id": "cloud-tmpl-1",
            "title": "Synced Sprint Template",
            "notes": "Cross-device template",
            "tagIds": ["tag-v181"],
            "blueprintJson": ProjectTemplateBlueprint(unheadedTasks: [TemplateTaskSpec(title: "Sync check")]).toJSONString(),
            "sortOrder": 500.0,
            "createdAt": Int64(1_766_000_000_000),
            "updatedAt": Int64(1_766_000_002_000),
            "isDeleted": false
        ]))
        _ = try freshV2Db.applyRemoteDeltas(RemoteDeltaBatch(projectTemplates: [remoteTemplate]))
        XCTAssertEqual(try freshV2Db.loadWorkspaceSnapshot().projectTemplates.map(\.title), ["Synced Sprint Template"])

        // 7. Verify all 34 shared v2_* localization keys exist and are non-empty across EN/DE/FR/IT/ES
        let requiredV2Keys = [
            "v2_beta_badge", "v2_beta_channel_toggle", "v2_beta_channel_desc",
            "v2_weekly_review_title", "v2_weekly_review_subtitle",
            "v2_review_step_inbox", "v2_review_step_today", "v2_review_step_projects", "v2_review_step_someday",
            "v2_stalled_project_badge", "v2_requires_review_badge", "v2_stalled_no_next_action", "v2_stalled_unreviewed",
            "v2_add_next_action", "v2_mark_reviewed", "v2_complete_weekly_review", "v2_weekly_review_done_toast",
            "v2_templates_title", "v2_save_as_template", "v2_template_saved_toast",
            "v2_create_from_template", "v2_starter_templates", "v2_my_templates",
            "v2_import_markdown_title", "v2_import_markdown_hint", "v2_import_create_project",
            "v2_timeblock_title", "v2_timeblock_start", "v2_timeblock_duration", "v2_timeblock_clear",
            "v2_timeline_view", "v2_list_view", "v2_unscheduled_today", "v2_schedule_conflict"
        ]
        XCTAssertEqual(requiredV2Keys.count, 34)
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in requiredV2Keys {
                let text = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertFalse(text.isEmpty, "Missing \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(text, key, "Untranslated \(key) in \(lang.rawValue)")
            }
        }
    }

    // MARK: - 14. v2.0.0-beta.2 Localized Built-In Starter Project Templates & Attention Badges

    func testLocalizedBuiltInStarterTemplatesAcrossAllFiveLanguages() throws {
        let expectedIds = [
            "starter-trip-packing",
            "starter-product-launch",
            "starter-client-onboarding",
            "starter-weekly-sprint"
        ]

        let enTemplates = ProjectTemplate.builtInStarterTemplates(for: "en")
        XCTAssertEqual(enTemplates.count, 4)
        XCTAssertEqual(enTemplates.map(\.id), expectedIds)
        XCTAssertEqual(ProjectTemplate.builtInStarterTemplates, enTemplates)
        XCTAssertEqual(ProjectTemplate.builtInStarterTemplates(for: "ja_JP"), enTemplates)

        // Verify normalization of locale identifiers with region tags & whitespace
        XCTAssertEqual(
            ProjectTemplate.builtInStarterTemplates(for: " de_DE "),
            ProjectTemplate.builtInStarterTemplates(for: "de")
        )
        XCTAssertEqual(
            ProjectTemplate.builtInStarterTemplates(for: "FR-FR"),
            ProjectTemplate.builtInStarterTemplates(for: "fr")
        )

        for langCode in ["en", "de", "fr", "it", "es"] {
            let localized = ProjectTemplate.builtInStarterTemplates(for: langCode)
            XCTAssertEqual(localized.count, 4, "Expected 4 starter templates for \(langCode)")
            XCTAssertEqual(localized.map(\.id), expectedIds)

            for (tIdx, tmpl) in localized.enumerated() {
                let enTmpl = enTemplates[tIdx]
                XCTAssertFalse(tmpl.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                XCTAssertFalse(tmpl.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                XCTAssertEqual(tmpl.sortOrder, enTmpl.sortOrder)

                let bp = tmpl.blueprint
                let enBp = enTmpl.blueprint
                XCTAssertEqual(bp.unheadedTasks.count, enBp.unheadedTasks.count)
                XCTAssertEqual(bp.headings.count, enBp.headings.count)
                XCTAssertFalse(bp.headings.isEmpty)
                XCTAssertEqual(bp.totalTaskCount, enBp.totalTaskCount)

                if langCode != "en" {
                    XCTAssertNotEqual(tmpl.title, enTmpl.title, "Template title not localized in \(langCode): \(tmpl.id)")
                    XCTAssertNotEqual(tmpl.notes, enTmpl.notes, "Template notes not localized in \(langCode): \(tmpl.id)")
                }

                for (uIdx, uTask) in bp.unheadedTasks.enumerated() {
                    let enUTask = enBp.unheadedTasks[uIdx]
                    XCTAssertFalse(uTask.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    XCTAssertEqual(uTask.durationMinutes, enUTask.durationMinutes)
                    XCTAssertEqual(uTask.checklistItems.count, enUTask.checklistItems.count)
                    if langCode != "en" {
                        XCTAssertNotEqual(uTask.title, enUTask.title, "Unheaded task title not localized in \(langCode)")
                    }
                    for (cIdx, item) in uTask.checklistItems.enumerated() {
                        XCTAssertFalse(item.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        if langCode != "en" {
                            XCTAssertNotEqual(item, enUTask.checklistItems[cIdx], "Unheaded checklist item not localized in \(langCode)")
                        }
                    }
                }

                for (hIdx, heading) in bp.headings.enumerated() {
                    let enHeading = enBp.headings[hIdx]
                    XCTAssertFalse(heading.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    XCTAssertEqual(heading.tasks.count, enHeading.tasks.count)
                    XCTAssertFalse(heading.tasks.isEmpty)
                    if langCode != "en" {
                        XCTAssertNotEqual(heading.title, enHeading.title, "Heading title not localized in \(langCode): \(heading.title)")
                    }

                    for (taskIdx, task) in heading.tasks.enumerated() {
                        let enTask = enHeading.tasks[taskIdx]
                        XCTAssertFalse(task.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        XCTAssertEqual(task.durationMinutes, enTask.durationMinutes)
                        XCTAssertEqual(task.checklistItems.count, enTask.checklistItems.count)
                        if langCode != "en" {
                            XCTAssertNotEqual(task.title, enTask.title, "Task title not localized in \(langCode): \(task.title)")
                        }
                        for (cIdx, item) in task.checklistItems.enumerated() {
                            XCTAssertFalse(item.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                            if langCode != "en" {
                                XCTAssertNotEqual(item, enTask.checklistItems[cIdx], "Checklist item not localized in \(langCode): \(item)")
                            }
                        }
                    }
                }
            }
        }

        // Verify SQLite instantiation of a localized starter template preserves localized project, headings, tasks, and checklists
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let deTripTemplate = ProjectTemplate.builtInStarterTemplates(for: "de")[0]
        let createdDeProject = try db.instantiateProjectFromTemplate(deTripTemplate)
        let snap = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(createdDeProject.title, "Reise & Packliste")
        XCTAssertEqual(snap.headingsByProject[createdDeProject.id]?.map(\.title), ["Dokumente & Technik", "Zuhause vor der Abreise"])
        let createdDeTasks = snap.allOpenTasks.filter { $0.projectId == createdDeProject.id }
        XCTAssertEqual(createdDeTasks.count, 4)
        XCTAssertTrue(createdDeTasks.contains(where: {
            $0.title == "Flüge, Hotel & Nahverkehrstickets bestätigen" &&
            $0.checklistItems.map(\.title) == ["Bordkarten offline speichern", "Reiseplan mit der Familie teilen"]
        }))
    }

    // MARK: - 15. v2.0.0-beta.3 Project Deletion Prompt, Cascade Task Deletion, Restore & Tombstone Purge

    func testDeleteProjectWithTasksCascadeRestoreAndDetachOptions() throws {
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let proj = try db.createProject(title: "Mobile App v2.0", notes: "Cascade deletion test")
        let hdg = try db.createHeading(projectId: proj.id, title: "Release Engineering")

        let openTask1 = try db.createTask(
            title: "Build Universal DMG",
            scheduleState: .today,
            whenDate: DateEpochConverter.startOfDayMillis(for: Date()),
            projectId: proj.id,
            headingId: hdg.id,
            checklistTitles: ["Notarize binary", "Verify SHA-256"]
        )
        let openTask2 = try db.createTask(
            title: "Publish Release Notes",
            scheduleState: .anytime,
            projectId: proj.id
        )
        let completedTask = try db.createTask(
            title: "Draft Architecture RFC",
            scheduleState: .anytime,
            projectId: proj.id,
            headingId: hdg.id
        )
        _ = try db.setTaskStatus(taskId: completedTask.id, status: .completed)

        let alreadyDeletedTask = try db.createTask(
            title: "Abandoned Spike",
            scheduleState: .anytime,
            projectId: proj.id
        )
        try db.softDeleteTask(alreadyDeletedTask.id)

        _ = try db.addAttachment(
            taskId: openTask1.id,
            fileName: "spec.txt",
            mimeType: "text/plain",
            data: Data("spec".utf8)
        )
        _ = try db.addAttachment(
            projectId: proj.id,
            fileName: "roadmap.txt",
            mimeType: "text/plain",
            data: Data("roadmap".utf8)
        )

        // 1. countNonDeletedTasksInProject counts both open (2) and completed (1) non-deleted tasks, excluding already trashed tasks
        XCTAssertEqual(try db.countNonDeletedTasksInProject(proj.id), 3)

        // 2. softDeleteProject(..., deleteAssociatedTasks: true) soft-deletes both open AND completed tasks inside the project
        let affectedMap = try db.softDeleteProject(proj.id, deleteAssociatedTasks: true)
        XCTAssertEqual(Set(affectedMap.keys), [openTask1.id, openTask2.id, completedTask.id])
        XCTAssertEqual(try db.countNonDeletedTasksInProject(proj.id), 0)

        var snap = try db.loadWorkspaceSnapshot()
        XCTAssertTrue(snap.projects.isEmpty)
        XCTAssertEqual(snap.trashedProjects.map(\.id), [proj.id])
        XCTAssertFalse(snap.allOpenTasks.contains { $0.id == openTask1.id || $0.id == openTask2.id })
        XCTAssertFalse(snap.logbookTasks.contains { $0.id == completedTask.id })
        XCTAssertTrue(snap.trashedTasks.contains { $0.id == openTask1.id && $0.projectId == proj.id && $0.headingId == hdg.id })
        XCTAssertTrue(snap.trashedTasks.contains { $0.id == openTask2.id && $0.projectId == proj.id })
        XCTAssertTrue(snap.trashedTasks.contains { $0.id == completedTask.id && $0.projectId == proj.id && $0.status == .completed })

        let dirtyAfterCascade = try db.fetchDirtyBatch()
        XCTAssertTrue(dirtyAfterCascade.attachments.contains { $0.taskId == openTask1.id && $0.isDeleted })
        XCTAssertTrue(dirtyAfterCascade.attachments.contains { $0.projectId == proj.id && $0.isDeleted })

        // Verify immediate Undo restores project, headings, and ONLY the tasks deleted by this action (not alreadyDeletedTask)
        try db.restoreDeletedProject(
            projectId: proj.id,
            previousTasks: affectedMap,
            restoredDeletedTaskIds: Array(affectedMap.keys)
        )
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(snap.projects.map(\.id), [proj.id])
        XCTAssertEqual(snap.allOpenTasks.count, 2)
        XCTAssertEqual(snap.logbookTasks.map(\.id), [completedTask.id])
        XCTAssertEqual(snap.trashedTasks.map(\.id), [alreadyDeletedTask.id])
        try db.permanentlyDeleteTask(alreadyDeletedTask.id)

        // Cascade-delete again to test Trash flows
        _ = try db.softDeleteProject(proj.id, deleteAssociatedTasks: true)

        // 3. Restoring an individual trashed task while proj is still in Trash detaches projectId = nil so it reappears in Smart Views
        try db.restoreTask(openTask2.id)
        snap = try db.loadWorkspaceSnapshot()
        let individuallyRestored = try XCTUnwrap(snap.allOpenTasks.first(where: { $0.id == openTask2.id }))
        XCTAssertNil(individuallyRestored.projectId)
        XCTAssertNil(individuallyRestored.headingId)
        XCTAssertTrue(snap.anytimeTasks.contains { $0.id == openTask2.id })

        // 4. restoreProject(proj.id) restores proj, its headings, AND its remaining cascade-deleted tasks with headings & checklists intact
        try db.restoreProject(proj.id)
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertTrue(snap.trashedProjects.isEmpty)
        XCTAssertEqual(snap.projects.map(\.id), [proj.id])
        XCTAssertEqual(snap.headingsByProject[proj.id]?.map(\.id), [hdg.id])
        let restoredOpen1 = try XCTUnwrap(snap.allOpenTasks.first(where: { $0.id == openTask1.id }))
        XCTAssertEqual(restoredOpen1.projectId, proj.id)
        XCTAssertEqual(restoredOpen1.headingId, hdg.id)
        XCTAssertEqual(restoredOpen1.checklistItems.map(\.title), ["Notarize binary", "Verify SHA-256"])
        let restoredCompleted = try XCTUnwrap(snap.logbookTasks.first(where: { $0.id == completedTask.id }))
        XCTAssertEqual(restoredCompleted.projectId, proj.id)
        XCTAssertEqual(restoredCompleted.headingId, hdg.id)
        XCTAssertNil(snap.allOpenTasks.first(where: { $0.id == openTask2.id })?.projectId)

        // Verify "Keep To-Dos (Delete Project Only)" option (deleteAssociatedTasks: false) detaches tasks and keeps them active
        let keepProj = try db.createProject(title: "Keep Tasks Project")
        let keptTask = try db.createTask(title: "Surviving Detached Task", scheduleState: .anytime, projectId: keepProj.id)
        _ = try db.softDeleteProject(keepProj.id, deleteAssociatedTasks: false)
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertNil(snap.allOpenTasks.first(where: { $0.id == keptTask.id })?.projectId)
        XCTAssertTrue(snap.anytimeTasks.contains { $0.id == keptTask.id })

        // 5. permanentlyDeleteProject(proj.id) after softDeleteProject(..., deleteAssociatedTasks: true) creates dirty tombstones for both project AND cascade-deleted tasks
        try db.markDirtyBatchSynced(try db.fetchDirtyBatch())
        _ = try db.softDeleteProject(proj.id, deleteAssociatedTasks: true)
        try db.permanentlyDeleteProject(proj.id)

        snap = try db.loadWorkspaceSnapshot()
        XCTAssertFalse(snap.trashedProjects.contains { $0.id == proj.id })
        XCTAssertFalse(snap.trashedTasks.contains { $0.id == openTask1.id || $0.id == completedTask.id })

        let dirtyTombstones = try db.fetchDirtyBatch()
        let projTombstone = try XCTUnwrap(dirtyTombstones.projects.first(where: { $0.id == proj.id }))
        XCTAssertTrue(projTombstone.isDeleted)
        XCTAssertEqual(projTombstone.title, "")

        let openTask1Tombstone = try XCTUnwrap(dirtyTombstones.tasks.first(where: { $0.id == openTask1.id }))
        XCTAssertTrue(openTask1Tombstone.isDeleted)
        XCTAssertEqual(openTask1Tombstone.title, "")
        XCTAssertEqual(openTask1Tombstone.notes, "")

        let completedTaskTombstone = try XCTUnwrap(dirtyTombstones.tasks.first(where: { $0.id == completedTask.id }))
        XCTAssertTrue(completedTaskTombstone.isDeleted)
        XCTAssertEqual(completedTaskTombstone.title, "")

        // 6. Verify the 4 new project_delete_* keys across all 5 languages (en, de, fr, it, es)
        let projectDeleteKeys = [
            "project_delete_prompt_title",
            "project_delete_prompt_tasks_body",
            "project_delete_with_tasks_btn",
            "project_delete_keep_tasks_btn"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in projectDeleteKeys {
                let text = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertFalse(text.isEmpty, "Missing \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(text, key, "Untranslated \(key) in \(lang.rawValue)")
            }
            let formattedTitle = ThingsStrings.shared.format("project_delete_prompt_title", lang: lang, "Roadmap")
            XCTAssertTrue(formattedTitle.contains("Roadmap"))
            let formattedBody = ThingsStrings.shared.format("project_delete_prompt_tasks_body", lang: lang, 5)
            XCTAssertTrue(formattedBody.contains("5"))
        }
    }

    // MARK: - 14. v2.0.0-beta.5 macOS In-App Automatic Updater (Zero-DMG Staging, Validation & Atomic Swap)

    func testMacInAppAutoUpdaterTargetResolutionValidationAndAtomicSwapScript() throws {
        // 1. Target destination resolution
        XCTAssertEqual(
            AppUpdateService.resolveInstallTargetAppURL(bundleURL: URL(fileURLWithPath: "/Applications/Thingsbook.app")).path,
            "/Applications/Thingsbook.app"
        )
        XCTAssertEqual(
            AppUpdateService.resolveInstallTargetAppURL(bundleURL: URL(fileURLWithPath: "/Users/petros/Applications/Thingsbook.app/Contents/MacOS/Thingsbook")).path,
            "/Users/petros/Applications/Thingsbook.app"
        )
        XCTAssertEqual(
            AppUpdateService.resolveInstallTargetAppURL(bundleURL: URL(fileURLWithPath: "/Volumes/Thingsbook/Thingsbook.app")).path,
            "/Applications/Thingsbook.app",
            "Launching from a mounted read-only DMG must redirect updates to /Applications/Thingsbook.app"
        )
        XCTAssertEqual(
            AppUpdateService.resolveInstallTargetAppURL(bundleURL: URL(fileURLWithPath: "/private/var/folders/ab/cd/T/AppTranslocation/UUID/d/Thingsbook.app")).path,
            "/Applications/Thingsbook.app",
            "Gatekeeper AppTranslocation paths must redirect updates to /Applications/Thingsbook.app"
        )
        XCTAssertEqual(
            AppUpdateService.resolveInstallTargetAppURL(bundleURL: URL(fileURLWithPath: "/usr/local/bin/thingsbook-cli")).path,
            "/Applications/Thingsbook.app"
        )

        // 2. Extracted .app bundle validation (CFBundleIdentifier + executable permissions)
        let fm = FileManager.default
        let tempRoot = fm.temporaryDirectory.appendingPathComponent("tb-updater-test-\(UUID().uuidString)", isDirectory: true)
        defer { try? fm.removeItem(at: tempRoot) }

        let mockApp = tempRoot.appendingPathComponent("Thingsbook.app", isDirectory: true)
        let contentsDir = mockApp.appendingPathComponent("Contents", isDirectory: true)
        let macOSDir = contentsDir.appendingPathComponent("MacOS", isDirectory: true)
        try fm.createDirectory(at: macOSDir, withIntermediateDirectories: true)

        let validPlist: [String: Any] = [
            "CFBundleIdentifier": "com.thingsbook.macos",
            "CFBundleExecutable": "Thingsbook",
            "CFBundleShortVersionString": "2.0.0-beta.5"
        ]
        let plistData = try PropertyListSerialization.data(fromPropertyList: validPlist, format: .xml, options: 0)
        try plistData.write(to: contentsDir.appendingPathComponent("Info.plist"))

        // Missing executable -> throws invalidAppBundle
        XCTAssertThrowsError(try AppUpdateService.validateExtractedAppBundle(at: mockApp))

        // Create executable binary with +x permission -> passes validation
        let execURL = macOSDir.appendingPathComponent("Thingsbook")
        try Data("#!/bin/sh\nexit 0\n".utf8).write(to: execURL)
        try fm.setAttributes([.posixPermissions: 0o755], ofItemAtPath: execURL.path)
        XCTAssertNoThrow(try AppUpdateService.validateExtractedAppBundle(at: mockApp))

        // Wrong bundle identifier -> throws invalidAppBundle
        XCTAssertThrowsError(
            try AppUpdateService.validateExtractedAppBundle(
                at: mockApp,
                expectedBundleIdentifier: "com.other.bundle"
            )
        )

        // 3. Atomic swap, rollback, and relaunch shell script generation (non-admin & admin paths + single-quote safety)
        let userScript = AppUpdateService.buildAtomicSwapAndRelaunchScript(
            oldPID: 42424,
            stagedAppPath: "/var/folders/tmp/Petros's Stage/Thingsbook.app",
            targetAppPath: "/Users/petros/Applications/Thingsbook.app",
            stagingRootPath: "/var/folders/tmp/Petros's Stage",
            requiresAdmin: false
        )
        XCTAssertTrue(userScript.contains("OLD_PID=42424"))
        XCTAssertTrue(userScript.contains("STAGED_APP='/var/folders/tmp/Petros'\\''s Stage/Thingsbook.app'"))
        XCTAssertTrue(userScript.contains("rollback-Thingsbook.app"))
        XCTAssertTrue(userScript.contains("/usr/bin/ditto \"$STAGED_APP\" \"$TARGET_APP\""))
        XCTAssertTrue(userScript.contains("/usr/bin/xattr -cr \"$TARGET_APP\""))
        XCTAssertTrue(userScript.contains("/usr/bin/open \"$TARGET_APP\""))

        let adminScript = AppUpdateService.buildAtomicSwapAndRelaunchScript(
            oldPID: 51515,
            stagedAppPath: "/tmp/stage/Thingsbook.app",
            targetAppPath: "/Applications/Thingsbook.app",
            stagingRootPath: "/tmp/stage",
            requiresAdmin: true
        )
        XCTAssertTrue(adminScript.contains("/usr/bin/osascript -e"))
        XCTAssertTrue(adminScript.contains("with administrator privileges"))
        XCTAssertTrue(adminScript.contains("/usr/bin/open \"$TARGET_APP\""))

        // 4. Verify all 4 new macOS in-app auto-updater localization keys across all 5 languages
        let updaterKeys = [
            "macos_updates_auto_desc",
            "macos_updates_install_relaunch_btn",
            "macos_updates_extracting",
            "macos_updates_installing_relaunching"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in updaterKeys {
                let text = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertFalse(text.isEmpty, "Missing \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(text, key, "Untranslated \(key) in \(lang.rawValue)")
            }
            XCTAssertTrue(ThingsStrings.shared.format("macos_updates_extracting", lang: lang, "2.0.0-beta.5").contains("2.0.0-beta.5"))
            XCTAssertTrue(ThingsStrings.shared.format("macos_updates_installing_relaunching", lang: lang, "2.0.0-beta.5").contains("2.0.0-beta.5"))
        }
    }

    // MARK: - 15. v2.0.0-beta.7 Cross-Device Project Review Sync & Monotonic lastReviewedAt Merge

    func testCrossDeviceProjectReviewSyncAndMonotonicMergeV200Beta7() throws {
        let dbDeviceA = try ThingsSQLiteDatabase(path: ":memory:")
        let dbDeviceB = try ThingsSQLiteDatabase(path: ":memory:")

        // 1. Newly created project on macOS initializes non-nil lastReviewedAt (matching Android & ChromeOS)
        let newlyCreated = try dbDeviceA.createProject(title: "Fresh Launch Plan")
        XCTAssertNotNil(newlyCreated.lastReviewedAt)
        XCTAssertGreaterThan(newlyCreated.lastReviewedAt ?? 0, 0)

        // 2. Seed a 16-day-old unreviewed project on BOTH Device A and Device B
        let dayMs: Int64 = 86_400_000
        let now: Int64 = 1_785_000_000_000
        let sixteenDaysAgo = now - 16 * dayMs
        let sharedStaleProject = Project(
            id: "proj-shared-review-1",
            title: "Q4 Architecture Review",
            notes: "Needs review",
            status: .active,
            scheduleState: .anytime,
            lastReviewedAt: sixteenDaysAgo,
            sortOrder: 500,
            createdAt: now - 30 * dayMs,
            updatedAt: sixteenDaysAgo
        )
        _ = try dbDeviceA.applyRemoteDeltas(RemoteDeltaBatch(projects: [sharedStaleProject]))
        _ = try dbDeviceB.applyRemoteDeltas(RemoteDeltaBatch(projects: [sharedStaleProject]))

        // 3. Review the project on Device A -> fetch dirty batch -> parse via CloudSync -> apply on Device B
        let reviewTimestamp = now
        try dbDeviceA.markProjectReviewed(projectId: sharedStaleProject.id, reviewedAt: reviewTimestamp)
        let dirtyA = try dbDeviceA.fetchDirtyBatch()
        guard let dirtyReviewedProj = dirtyA.projects.first(where: { $0.id == sharedStaleProject.id }) else {
            return XCTFail("Expected dirty reviewed project on Device A")
        }
        XCTAssertEqual(dirtyReviewedProj.lastReviewedAt, reviewTimestamp)
        XCTAssertGreaterThanOrEqual(dirtyReviewedProj.updatedAt, reviewTimestamp)

        let wireMap: [String: Any] = [
            "id": dirtyReviewedProj.id,
            "title": dirtyReviewedProj.title,
            "notes": dirtyReviewedProj.notes,
            "status": dirtyReviewedProj.status.rawValue,
            "scheduleState": dirtyReviewedProj.scheduleState.rawValue,
            "lastReviewedAt": dirtyReviewedProj.lastReviewedAt as Any,
            "sortOrder": dirtyReviewedProj.sortOrder,
            "tagIds": dirtyReviewedProj.tagIds,
            "createdAt": dirtyReviewedProj.createdAt,
            "updatedAt": dirtyReviewedProj.updatedAt,
            "isDeleted": dirtyReviewedProj.isDeleted
        ]
        guard let parsedRemote = FirebaseCloudSyncService.parseProject(wireMap) else {
            return XCTFail("Expected parseProject to decode reviewed project map")
        }
        _ = try dbDeviceB.applyRemoteDeltas(RemoteDeltaBatch(projects: [parsedRemote]))

        let snapB1 = try dbDeviceB.loadWorkspaceSnapshot()
        guard let syncedOnB = snapB1.projects.first(where: { $0.id == sharedStaleProject.id }) else {
            return XCTFail("Expected synced project on Device B")
        }
        XCTAssertEqual(syncedOnB.lastReviewedAt, reviewTimestamp, "Device B must immediately receive Device A's project review status")

        // 4. Even if Device B later receives an edit with nil/older lastReviewedAt or has a newer local updatedAt (clock skew),
        //    monotonic merge NEVER regresses lastReviewedAt!
        let olderReviewEdit = Project(
            id: sharedStaleProject.id,
            title: "Q4 Architecture Review (Renamed)",
            notes: "Needs review",
            status: .active,
            scheduleState: .anytime,
            lastReviewedAt: nil,
            sortOrder: 500,
            createdAt: sharedStaleProject.createdAt,
            updatedAt: dirtyReviewedProj.updatedAt + 5_000
        )
        _ = try dbDeviceB.applyRemoteDeltas(RemoteDeltaBatch(projects: [olderReviewEdit]))
        let snapB2 = try dbDeviceB.loadWorkspaceSnapshot()
        guard let afterRenameOnB = snapB2.projects.first(where: { $0.id == sharedStaleProject.id }) else {
            return XCTFail("Expected renamed project on Device B")
        }
        XCTAssertEqual(afterRenameOnB.title, "Q4 Architecture Review (Renamed)")
        XCTAssertEqual(afterRenameOnB.lastReviewedAt, reviewTimestamp, "Non-review edits with nil lastReviewedAt must not wipe synced review timestamp")

        // Clock-skewed peer review arriving with updatedAt < local.updatedAt still advances lastReviewedAt!
        let newerPeerReview = reviewTimestamp + 60_000
        let clockSkewedPeerReview = Project(
            id: sharedStaleProject.id,
            title: "Q4 Architecture Review",
            notes: "Needs review",
            status: .active,
            scheduleState: .anytime,
            lastReviewedAt: newerPeerReview,
            sortOrder: 500,
            createdAt: sharedStaleProject.createdAt,
            updatedAt: afterRenameOnB.updatedAt - 1_000
        )
        _ = try dbDeviceB.applyRemoteDeltas(RemoteDeltaBatch(projects: [clockSkewedPeerReview]))
        let snapB3 = try dbDeviceB.loadWorkspaceSnapshot()
        guard let afterSkewedPeerReview = snapB3.projects.first(where: { $0.id == sharedStaleProject.id }) else {
            return XCTFail("Expected project after skewed peer review on Device B")
        }
        XCTAssertEqual(afterSkewedPeerReview.title, "Q4 Architecture Review (Renamed)")
        XCTAssertEqual(afterSkewedPeerReview.lastReviewedAt, newerPeerReview)
        XCTAssertEqual(
            FirebaseCloudSyncService.projectReviewBackfillDefaultsKey(uid: "u123"),
            "thingsbook.sync.projReviewBackfillV1.u123"
        )
    }

    // MARK: - 16. v2.0.0-beta.9 Unified macOS Top Toolbar Search Box + Action Bar Layout & Quick Find Search Filters

    func testMacToolbarLayoutMetricsSpacingAndNoOverflowAcrossWindowWidths() {
        // 1. Default 1140pt window minus 262pt sidebar = 878pt detail pane:
        //    unified single-item toolbar containing 208pt Search Box + 110pt finite centerGap + 504pt grouped action bar = 822pt
        let defaultMetrics = MacToolbarLayoutMetrics.compute(detailWidth: 878.0, isSidebarCollapsed: false)
        XCTAssertEqual(defaultMetrics.usableWidth, 822.0)
        XCTAssertEqual(defaultMetrics.searchWidth, 208.0)
        XCTAssertEqual(defaultMetrics.buttonWidth, 32.0)
        XCTAssertEqual(defaultMetrics.buttonHeight, 30.0)
        XCTAssertEqual(defaultMetrics.syncButtonWidth, 40.0)
        XCTAssertEqual(defaultMetrics.geminiPillMinWidth, 86.0)
        XCTAssertEqual(defaultMetrics.itemSpacing, 6.0)
        XCTAssertEqual(defaultMetrics.groupDividerPadding, 9.0)
        XCTAssertEqual(defaultMetrics.trailingActionsPadding, 4.0)
        XCTAssertTrue(defaultMetrics.showSearchShortcutBadge)
        XCTAssertEqual(defaultMetrics.estimatedActionsBarWidth, 504.0)
        XCTAssertEqual(defaultMetrics.estimatedTotalControlsWidth, 712.0)
        XCTAssertEqual(defaultMetrics.centerGap, 110.0)
        XCTAssertEqual(defaultMetrics.totalBarWidth, 822.0)
        XCTAssertLessThanOrEqual(defaultMetrics.totalBarWidth, defaultMetrics.usableWidth)

        // 2. Minimum 880pt window with default 262pt sidebar (618pt detail pane — safe frame-0 initial width!)
        let minStandardMetrics = MacToolbarLayoutMetrics.compute(detailWidth: 618.0, isSidebarCollapsed: false)
        XCTAssertEqual(minStandardMetrics.usableWidth, 562.0)
        XCTAssertEqual(minStandardMetrics.searchWidth, 144.0)
        XCTAssertEqual(minStandardMetrics.buttonWidth, 27.0)
        XCTAssertEqual(minStandardMetrics.itemSpacing, 3.0)
        XCTAssertEqual(minStandardMetrics.groupDividerPadding, 5.0)
        XCTAssertFalse(minStandardMetrics.showSearchShortcutBadge)
        XCTAssertEqual(minStandardMetrics.estimatedActionsBarWidth, 406.0)
        XCTAssertEqual(minStandardMetrics.estimatedTotalControlsWidth, 550.0)
        XCTAssertEqual(minStandardMetrics.centerGap, 12.0)
        XCTAssertEqual(minStandardMetrics.totalBarWidth, 562.0)
        XCTAssertLessThanOrEqual(minStandardMetrics.totalBarWidth, minStandardMetrics.usableWidth)

        // 3. Minimum 880pt window with maximum 330pt sidebar (550pt detail pane)
        let minMaxSidebarMetrics = MacToolbarLayoutMetrics.compute(detailWidth: 550.0, isSidebarCollapsed: false)
        XCTAssertEqual(minMaxSidebarMetrics.usableWidth, 494.0)
        XCTAssertEqual(minMaxSidebarMetrics.searchWidth, 126.0)
        XCTAssertEqual(minMaxSidebarMetrics.centerGap, 14.0)
        XCTAssertEqual(minMaxSidebarMetrics.totalBarWidth, 494.0)
        XCTAssertFalse(minMaxSidebarMetrics.showSearchShortcutBadge)
        XCTAssertLessThanOrEqual(minMaxSidebarMetrics.totalBarWidth, minMaxSidebarMetrics.usableWidth)

        // 4. 1024pt detail pane inside a 1286pt window (reproduces Project view screenshot — full Schnellsuche ⌘F + right-aligned icons)
        let projectDetailMetrics = MacToolbarLayoutMetrics.compute(detailWidth: 1024.0, isSidebarCollapsed: false)
        XCTAssertEqual(projectDetailMetrics.usableWidth, 968.0)
        XCTAssertEqual(projectDetailMetrics.searchWidth, 228.0)
        XCTAssertTrue(projectDetailMetrics.showSearchShortcutBadge)
        XCTAssertEqual(projectDetailMetrics.estimatedActionsBarWidth, 529.0)
        XCTAssertEqual(projectDetailMetrics.centerGap, 211.0)
        XCTAssertEqual(projectDetailMetrics.totalBarWidth, 968.0)

        // 5. Wide / fullscreen 1280pt detail pane
        let wideMetrics = MacToolbarLayoutMetrics.compute(detailWidth: 1280.0, isSidebarCollapsed: false)
        XCTAssertEqual(wideMetrics.usableWidth, 1224.0)
        XCTAssertEqual(wideMetrics.searchWidth, 240.0)
        XCTAssertEqual(wideMetrics.buttonWidth, 34.0)
        XCTAssertEqual(wideMetrics.itemSpacing, 8.0)
        XCTAssertEqual(wideMetrics.groupDividerPadding, 11.0)
        XCTAssertTrue(wideMetrics.showSearchShortcutBadge)
        XCTAssertEqual(wideMetrics.estimatedActionsBarWidth, 552.0)
        XCTAssertEqual(wideMetrics.estimatedTotalControlsWidth, 792.0)
        XCTAssertEqual(wideMetrics.centerGap, 432.0)
        XCTAssertEqual(wideMetrics.totalBarWidth, 1224.0)
        XCTAssertLessThanOrEqual(wideMetrics.totalBarWidth, wideMetrics.usableWidth)

        // 6. Continuous sweep across all window widths and collapsed-sidebar states guarantees Search Box >= 126pt & zero NSToolbar overflow
        for collapsed in [false, true] {
            var width = 540.0
            while width <= 1600.0 {
                let metrics = MacToolbarLayoutMetrics.compute(detailWidth: width, isSidebarCollapsed: collapsed)
                XCTAssertGreaterThanOrEqual(metrics.searchWidth, 126.0)
                XCTAssertGreaterThanOrEqual(metrics.centerGap, 8.0)
                XCTAssertLessThanOrEqual(
                    metrics.totalBarWidth,
                    metrics.usableWidth,
                    "Unified toolbar overflowed at detailWidth=\(width), collapsed=\(collapsed)"
                )
                width += 10.0
            }
        }
    }

    func testQuickFindFiltersHideCompletedDatesDeadlinesChecklistsTagsAndScope() throws {
        var utcCal = Calendar(identifier: .gregorian)
        utcCal.timeZone = TimeZone(secondsFromGMT: 0)!
        let refDate = utcCal.date(from: DateComponents(year: 2026, month: 11, day: 15, hour: 12, minute: 0))!
        let startOfToday = Int64(utcCal.startOfDay(for: refDate).timeIntervalSince1970 * 1000.0)
        let dayMs: Int64 = 86_400_000

        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let urgentTag = try db.createOrUpdateTag(title: "Urgent", shortcut: "u")
        let homeTag = try db.createOrUpdateTag(title: "Home", shortcut: "h")

        var activeProj = try db.createProject(title: "Q4 Launch Sprint", notes: "Sprint roadmap")
        activeProj.deadline = startOfToday + 3 * dayMs
        activeProj.tagIds = [urgentTag.id]
        try db.updateProject(activeProj)

        var completedProj = try db.createProject(title: "Legacy Sprint Archive", notes: "Sprint retrospective")
        completedProj.status = .completed
        completedProj.completedAt = startOfToday - dayMs
        try db.updateProject(completedProj)

        let todayTask = try db.createTask(
            title: "Sprint keynote rehearsal",
            notes: "Main stage",
            scheduleState: .today,
            whenDate: startOfToday,
            deadline: startOfToday + 2 * dayMs,
            tagIds: [urgentTag.id],
            checklistTitles: ["Check slides", "Test mic"]
        )

        let overdueTask = try db.createTask(
            title: "Sprint tax filing",
            scheduleState: .upcoming,
            whenDate: startOfToday - 2 * dayMs,
            deadline: startOfToday - dayMs,
            tagIds: [homeTag.id]
        )

        let somedayNoDateTask = try db.createTask(
            title: "Sprint book idea",
            scheduleState: .someday
        )

        let completedTask = try db.createTask(
            title: "Sprint kickoff notes",
            scheduleState: .anytime
        )
        try db.setTaskStatus(taskId: completedTask.id, status: .completed)

        // 1. Default unfiltered search for "Sprint" returns both open & completed tasks and projects
        let unfiltered = try db.searchQuickFind(rawQuery: "Sprint", filter: QuickFindFilter(), referenceDate: refDate, calendar: utcCal)
        XCTAssertEqual(unfiltered.tasks.count, 4)
        XCTAssertEqual(unfiltered.projects.count, 2)
        XCTAssertFalse(QuickFindFilter().hasActiveFilters)
        XCTAssertTrue(QuickFindFilter().shouldShowSmartViewsAndAreas(forQuery: "Tod"))

        // 2. hideCompleted excludes completed tasks & completed projects
        let hideDoneFilter = QuickFindFilter(hideCompleted: true)
        XCTAssertTrue(hideDoneFilter.hasActiveFilters)
        XCTAssertFalse(hideDoneFilter.shouldShowSmartViewsAndAreas(forQuery: ""))
        XCTAssertTrue(hideDoneFilter.shouldShowSmartViewsAndAreas(forQuery: "Sprint"))
        let openOnly = try db.searchQuickFind(rawQuery: "Sprint", filter: hideDoneFilter, referenceDate: refDate, calendar: utcCal)
        XCTAssertEqual(Set(openOnly.tasks.map(\.id)), Set([todayTask.id, overdueTask.id, somedayNoDateTask.id]))
        XCTAssertEqual(openOnly.projects.map(\.id), [activeProj.id])

        // 3. Empty-query filter-only searches work across date, deadline, checklist, tag, and scope filters!
        let todayHits = try db.searchQuickFind(
            rawQuery: "",
            filter: QuickFindFilter(dateFilter: .today),
            referenceDate: refDate,
            calendar: utcCal
        )
        XCTAssertEqual(todayHits.tasks.map(\.id), [todayTask.id])
        XCTAssertTrue(todayHits.projects.isEmpty)

        let overdueHits = try db.searchQuickFind(
            rawQuery: "",
            filter: QuickFindFilter(dateFilter: .overdue),
            referenceDate: refDate,
            calendar: utcCal
        )
        XCTAssertEqual(overdueHits.tasks.map(\.id), [overdueTask.id])

        let next7Hits = try db.searchQuickFind(
            rawQuery: "",
            filter: QuickFindFilter(dateFilter: .next7Days),
            referenceDate: refDate,
            calendar: utcCal
        )
        XCTAssertEqual(next7Hits.tasks.map(\.id), [todayTask.id])
        XCTAssertEqual(next7Hits.projects.map(\.id), [activeProj.id])

        let thisMonthHits = try db.searchQuickFind(
            rawQuery: "",
            filter: QuickFindFilter(hideCompleted: true, dateFilter: .thisMonth),
            referenceDate: refDate,
            calendar: utcCal
        )
        XCTAssertEqual(Set(thisMonthHits.tasks.map(\.id)), Set([todayTask.id, overdueTask.id]))
        XCTAssertEqual(thisMonthHits.projects.map(\.id), [activeProj.id])

        let noDateHits = try db.searchQuickFind(
            rawQuery: "",
            filter: QuickFindFilter(hideCompleted: true, dateFilter: .noDate),
            referenceDate: refDate,
            calendar: utcCal
        )
        XCTAssertEqual(noDateHits.tasks.map(\.id), [somedayNoDateTask.id])
        XCTAssertTrue(noDateHits.projects.isEmpty)

        // 4. Deadline, Checklist, Tag, and Entity Scope filters
        let checklistHits = try db.searchQuickFind(
            rawQuery: "",
            filter: QuickFindFilter(hasChecklistOnly: true),
            referenceDate: refDate,
            calendar: utcCal
        )
        XCTAssertEqual(checklistHits.tasks.map(\.id), [todayTask.id])
        XCTAssertTrue(checklistHits.projects.isEmpty)

        let deadlineProjectsOnly = try db.searchQuickFind(
            rawQuery: "",
            filter: QuickFindFilter(hasDeadlineOnly: true, tagId: urgentTag.id, scope: .projects),
            referenceDate: refDate,
            calendar: utcCal
        )
        XCTAssertTrue(deadlineProjectsOnly.tasks.isEmpty)
        XCTAssertEqual(deadlineProjectsOnly.projects.map(\.id), [activeProj.id])

        // 5. All 14 search_filter_* keys exist in macOS ThingsStrings across EN, DE, FR, IT, ES
        let filterKeys = [
            "search_filter_hide_completed",
            "search_filter_date_all",
            "search_filter_date_today",
            "search_filter_date_overdue",
            "search_filter_date_next_7_days",
            "search_filter_date_this_month",
            "search_filter_date_none",
            "search_filter_has_deadline",
            "search_filter_has_checklist",
            "search_filter_all_tags",
            "search_filter_scope_all",
            "search_filter_scope_tasks",
            "search_filter_scope_projects",
            "search_filter_reset"
        ]
        for lang in ["en", "de", "fr", "it", "es"] {
            let appLang = AppLanguage(rawValue: lang) ?? .en
            for key in filterKeys {
                let localized = ThingsStrings.shared.text(key, lang: appLang)
                XCTAssertFalse(localized.isEmpty, "Missing \(key) in \(lang)")
                XCTAssertNotEqual(localized, key, "Unresolved key \(key) in \(lang)")
            }
        }
    }

    // MARK: - 17. v2.0.0-beta.10 Readable Action Chip Labels & Localized Hover Tooltips Across All 5 Languages

    func testActionButtonsAndTooltipsLocalizationV200Beta10() {
        let requiredKeys = [
            "action_when",
            "context_when_date_reminder",
            "action_repeat",
            "context_configure_repeat",
            "action_move",
            "move_dialog_title",
            "action_tags",
            "context_assign_tags",
            "action_deadline",
            "context_adjust_deadline",
            "v2_timeblock_title",
            "macos_tooltip_timeblock",
            "macos_tooltip_shift_15m",
            "macos_tooltip_project_options",
            "macos_tooltip_zoom_in",
            "macos_tooltip_zoom_out",
            "macos_tooltip_zoom_reset",
            "action_attach_file",
            "action_focus_timer",
            "action_duplicate_todo",
            "action_delete",
            "action_done"
        ]

        for lang in ["en", "de", "fr", "it", "es"] {
            let appLang = AppLanguage(rawValue: lang) ?? .en
            for key in requiredKeys {
                let localized = ThingsStrings.shared.text(key, lang: appLang)
                XCTAssertFalse(localized.isEmpty, "Missing \(key) in \(lang)")
                XCTAssertNotEqual(localized, key, "Unresolved key \(key) in \(lang)")
                XCTAssertFalse(localized.hasSuffix("..."), "Action/tooltip label \(key) in \(lang) must not be pre-truncated: \(localized)")
            }
        }

        // Verify exact full German labels for the 6 primary expanded task card buttons shown in user screenshot
        XCTAssertEqual(ThingsStrings.shared.text("action_when", lang: .de), "Wann")
        XCTAssertEqual(ThingsStrings.shared.text("action_repeat", lang: .de), "Wiederholen")
        XCTAssertEqual(ThingsStrings.shared.text("action_move", lang: .de), "Bewegen")
        XCTAssertEqual(ThingsStrings.shared.text("action_tags", lang: .de), "Kategorien")
        XCTAssertEqual(ThingsStrings.shared.text("action_deadline", lang: .de), "Frist")
        XCTAssertEqual(ThingsStrings.shared.text("v2_timeblock_title", lang: .de), "Zeitblock")
    }

    // MARK: - 18. v2.0.0-beta.11 Instant TooltipTimingCoordinator Warm Handoff & Live State Updates

    func testTooltipTimingCoordinatorWarmHandoffAndLiveUpdatesV200Beta11() {
        var coordinator = TooltipTimingCoordinator(
            initialDelayMs: 160,
            warmHandoffWindowMs: 450
        )

        // 1. Cold hover entry schedules 160ms initial delay
        let coldAction = coordinator.pointerEntered(
            anchorId: "quick-find",
            text: "Schnellsuche (⌘F)",
            nowMs: 1_000
        )
        XCTAssertEqual(
            coldAction,
            .scheduleShow(anchorId: "quick-find", text: "Schnellsuche (⌘F)", delayMs: 160)
        )
        XCTAssertNil(coordinator.visibleAnchorId)

        // Stale timer for another anchor must not fire
        XCTAssertEqual(coordinator.commitScheduledShow(anchorId: "other", nowMs: 1_160), .none)
        XCTAssertEqual(
            coordinator.commitScheduledShow(anchorId: "quick-find", nowMs: 1_160),
            .showImmediately(anchorId: "quick-find", text: "Schnellsuche (⌘F)")
        )
        XCTAssertEqual(coordinator.visibleAnchorId, "quick-find")
        XCTAssertEqual(coordinator.visibleText, "Schnellsuche (⌘F)")

        // 2. Sweeping directly onto an adjacent toolbar icon transitions in 0ms with zero stale tooltip lag
        let directHandoff = coordinator.pointerEntered(
            anchorId: "weekly-review",
            text: "Wochenrückblick (⇧⌘R)",
            nowMs: 1_280
        )
        XCTAssertEqual(
            directHandoff,
            .showImmediately(anchorId: "weekly-review", text: "Wochenrückblick (⇧⌘R)")
        )

        // Out-of-order pointerExited from the previous control must never clobber the active tooltip
        XCTAssertEqual(coordinator.pointerExited(anchorId: "quick-find", nowMs: 1_285), .none)
        XCTAssertEqual(coordinator.visibleAnchorId, "weekly-review")

        // 3. Dynamic state or language change while hovered updates visible tooltip text in 0ms
        XCTAssertEqual(
            coordinator.updateText(
                anchorId: "weekly-review",
                text: "Weekly Review (⇧⌘R)",
                nowMs: 1_320
            ),
            .showImmediately(anchorId: "weekly-review", text: "Weekly Review (⇧⌘R)")
        )

        // 4. Leaving starts 450ms warm window; entering next icon within 450ms shows in 0ms
        XCTAssertEqual(coordinator.pointerExited(anchorId: "weekly-review", nowMs: 1_400), .hideImmediately)
        XCTAssertEqual(
            coordinator.pointerEntered(
                anchorId: "templates",
                text: "Vorlagen",
                nowMs: 1_720
            ),
            .showImmediately(anchorId: "templates", text: "Vorlagen")
        )

        // 5. Waiting beyond 450ms resets to 160ms cold delay
        XCTAssertEqual(coordinator.pointerExited(anchorId: "templates", nowMs: 2_000), .hideImmediately)
        XCTAssertEqual(
            coordinator.pointerEntered(
                anchorId: "gemini",
                text: "Gemini KI (⌘G)",
                nowMs: 2_600
            ),
            .scheduleShow(anchorId: "gemini", text: "Gemini KI (⌘G)", delayMs: 160)
        )

        // 6. Click / scroll dismissAll(allowWarmHandoff: false) clears active & warm state immediately
        _ = coordinator.commitScheduledShow(anchorId: "gemini", nowMs: 2_760)
        XCTAssertEqual(
            coordinator.dismissAll(nowMs: 2_800, allowWarmHandoff: false),
            .hideImmediately
        )
        XCTAssertEqual(
            coordinator.pointerEntered(
                anchorId: "gemini",
                text: "Gemini KI (⌘G)",
                nowMs: 2_850
            ),
            .scheduleShow(anchorId: "gemini", text: "Gemini KI (⌘G)", delayMs: 160)
        )
    }

    // MARK: - 20. v2.0.0-beta.13 Recurring Task Sync Duplicate Prevention & Non-Poisoning Midnight Rollover

    func testRecurringTaskSyncDuplicatePreventionAndNonPoisoningRollover() throws {
        // 1. Exact cross-platform SHA-256 deterministic UUID vectors (must match Android & ChromeOS PWA)
        let derivedTaskId = ThingsSQLiteDatabase.deriveRecurringNextTaskId(
            completedTaskId: " task-daily-001 ",
            nextWhenYyyyMmDd: "2026-11-17"
        )
        XCTAssertEqual(derivedTaskId, "e2d2925b-820d-5122-a510-f8164e907815")

        let derivedChkId = ThingsSQLiteDatabase.deriveRecurringChecklistItemId(
            nextTaskId: derivedTaskId,
            sourceChecklistId: "chk-step-001"
        )
        XCTAssertEqual(derivedChkId, "55d4ccdc-7b54-53cb-a216-e0f79e8fb359")

        // 2. Overdue FIXED_SCHEDULE daily task completed Today advances strictly past Today to Tomorrow
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let nov15 = DateEpochConverter.startOfDayMillis(
            for: cal.date(from: DateComponents(year: 2026, month: 11, day: 15))!,
            calendar: cal
        )
        let nov16Noon = DateEpochConverter.startOfDayMillis(
            for: cal.date(from: DateComponents(year: 2026, month: 11, day: 16))!,
            calendar: cal
        ) + 12 * 3_600_000
        let nov17 = DateEpochConverter.startOfDayMillis(
            for: cal.date(from: DateComponents(year: 2026, month: 11, day: 17))!,
            calendar: cal
        )
        let dailyRule = RecurrenceRule(mode: .fixedSchedule, interval: 1, unit: .days)
        let nextDate = try XCTUnwrap(
            WhenScheduleResolver.computeNextOccurrenceDate(
                rule: dailyRule,
                completedWhenMillis: nov15,
                nowMillis: nov16Noon,
                calendar: cal
            )
        )
        XCTAssertEqual(DateEpochConverter.startOfDayMillis(for: nextDate, calendar: cal), nov17)

        // 3. Midnight rollover does NOT bump updatedAt or mark tasks isDirty, preventing stale device resurrection
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let nov16 = DateEpochConverter.startOfDayMillis(
            for: cal.date(from: DateComponents(year: 2026, month: 11, day: 16))!,
            calendar: cal
        )
        let initialRemoteRecurring = TaskItem(
            id: "rec-daily-today",
            title: "Morning Journal",
            notes: "5 minutes",
            status: .open,
            scheduleState: .upcoming,
            whenDate: nov16,
            recurrenceRule: dailyRule,
            sortOrder: 1000,
            createdAt: 1_760_000_000_000,
            updatedAt: 1_760_000_100_000
        )
        _ = try db.applyRemoteDeltas(RemoteDeltaBatch(tasks: [initialRemoteRecurring]))
        XCTAssertTrue(try db.fetchDirtyBatch().tasks.isEmpty)

        // Device B wakes up at 6:00 PM on Nov 16 and runs performMidnightRollover BEFORE pulling remote completion
        let nov16Evening = Date(timeIntervalSince1970: Double(nov16 + 18 * 3_600_000) / 1000.0)
        _ = try db.performMidnightRollover(referenceDate: nov16Evening, calendar: cal)
        XCTAssertTrue(
            try db.fetchDirtyBatch().tasks.isEmpty,
            "Local midnight rollover must NOT mark tasks dirty or poison LWW updatedAt"
        )
        XCTAssertEqual(try db.loadWorkspaceSnapshot().todayDaytimeTasks.map(\.id), ["rec-daily-today"])

        // Remote completion from Device A at 9:00 AM (earlier than 6:00 PM!) + Tomorrow's spawned task arrives
        var remoteCompletedToday = initialRemoteRecurring
        remoteCompletedToday.status = .completed
        remoteCompletedToday.completedAt = 1_760_000_200_000
        remoteCompletedToday.updatedAt = 1_760_000_200_000

        let spawnedTomorrow = TaskItem(
            id: ThingsSQLiteDatabase.deriveRecurringNextTaskId(
                completedTaskId: "rec-daily-today",
                nextWhenYyyyMmDd: "2026-11-17"
            ),
            title: "Morning Journal",
            notes: "5 minutes",
            status: .open,
            scheduleState: .upcoming,
            whenDate: nov17,
            recurrenceRule: dailyRule,
            sortOrder: 2000,
            createdAt: 1_760_000_200_000,
            updatedAt: 1_760_000_200_000
        )
        _ = try db.applyRemoteDeltas(RemoteDeltaBatch(tasks: [remoteCompletedToday, spawnedTomorrow]))
        let snapAfterSync = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(snapAfterSync.allOpenTasks.map(\.id), [spawnedTomorrow.id])
        XCTAssertEqual(snapAfterSync.logbookTasks.map(\.id), ["rec-daily-today"])

        // 4. Sync-time deduplicator silently tombstones duplicate OPEN recurring series (without cluttering Trash)
        //    including cross-platform project container parity (projectId="p1", areaId=nil vs areaId="a1")
        //    while preserving distinct non-recurring tasks with identical titles.
        let dupStale = TaskItem(
            id: "dup-stale-nov16",
            title: "Morning Journal",
            notes: "5 minutes",
            status: .open,
            scheduleState: .today,
            whenDate: nov16,
            projectId: "proj-1",
            areaId: "area-1",
            recurrenceRule: dailyRule,
            sortOrder: 1000,
            createdAt: 1_760_000_050_000,
            updatedAt: 1_760_000_300_000
        )
        let dupLatest = TaskItem(
            id: "dup-latest-nov17",
            title: "Morning Journal",
            notes: "5 minutes",
            status: .open,
            scheduleState: .upcoming,
            whenDate: nov17,
            projectId: "proj-1",
            areaId: nil, // ChromeOS sets areaId=nil when projectId != nil
            recurrenceRule: dailyRule,
            sortOrder: 2000,
            createdAt: 1_760_000_250_000,
            updatedAt: 1_760_000_300_000
        )
        let nonRecurringTwinA = TaskItem(
            id: "plain-a",
            title: "Buy Milk",
            status: .open,
            scheduleState: .today,
            whenDate: nov16,
            createdAt: 1_760_000_100_000,
            updatedAt: 1_760_000_300_000
        )
        let nonRecurringTwinB = TaskItem(
            id: "plain-b",
            title: "Buy Milk",
            status: .open,
            scheduleState: .today,
            whenDate: nov16,
            createdAt: 1_760_000_101_000,
            updatedAt: 1_760_000_300_000
        )
        XCTAssertEqual(
            ThingsSQLiteDatabase.selectRecurringDuplicateLoserIds([
                dupStale, dupLatest, nonRecurringTwinA, nonRecurringTwinB
            ]),
            ["dup-stale-nov16"]
        )

        let dbDedup = try ThingsSQLiteDatabase(path: ":memory:")
        _ = try dbDedup.applyRemoteDeltas(
            RemoteDeltaBatch(tasks: [dupStale, dupLatest, nonRecurringTwinA, nonRecurringTwinB])
        )
        let dedupSnap = try dbDedup.loadWorkspaceSnapshot()
        XCTAssertEqual(
            Set(dedupSnap.allOpenTasks.map(\.id)),
            Set(["dup-latest-nov17", "plain-a", "plain-b"])
        )
        XCTAssertTrue(dedupSnap.trashedTasks.isEmpty, "Silently tombstoned duplicate must NOT appear in Trash")
        let dirtyTombstones = try dbDedup.fetchDirtyBatch().tasks
        XCTAssertEqual(dirtyTombstones.map(\.id), ["dup-stale-nov16"])
        XCTAssertTrue(dirtyTombstones[0].isDeleted)
        XCTAssertEqual(dirtyTombstones[0].title, "")
    }

    func testBeta17ToolbarClampingMagicPlusDropAndCalendarGrid() throws {
        // 1. Initial-launch toolbar misalignment guard: unsplit full-window GeometryReader width is clamped when sidebar is open,
        //    while already-split detail widths (and hostile z-order sidebar inputs) are preserved untouched!
        let clampedColdLaunch = MacToolbarLayoutMetrics.clampedInitialOrLiveDetailWidth(
            rawDetailWidth: 1140.0,
            windowContentWidth: 1140.0,
            sidebarFrameWidth: 0.0,
            isSidebarCollapsed: false
        )
        XCTAssertEqual(clampedColdLaunch, 878.0, accuracy: 0.01)

        let clampedWideColdLaunch = MacToolbarLayoutMetrics.clampedInitialOrLiveDetailWidth(
            rawDetailWidth: 1286.0,
            windowContentWidth: 1286.0,
            sidebarFrameWidth: 262.0,
            isSidebarCollapsed: false
        )
        XCTAssertEqual(clampedWideColdLaunch, 1024.0, accuracy: 0.01)

        let liveSplitPreserved = MacToolbarLayoutMetrics.clampedInitialOrLiveDetailWidth(
            rawDetailWidth: 1024.0,
            windowContentWidth: 1286.0,
            sidebarFrameWidth: 262.0,
            isSidebarCollapsed: false
        )
        XCTAssertEqual(liveSplitPreserved, 1024.0, accuracy: 0.01)

        let zOrderSwappedSidebarImmune = MacToolbarLayoutMetrics.clampedInitialOrLiveDetailWidth(
            rawDetailWidth: 1024.0,
            windowContentWidth: 1286.0,
            sidebarFrameWidth: 1024.0,
            isSidebarCollapsed: false
        )
        XCTAssertEqual(zOrderSwappedSidebarImmune, 1024.0, accuracy: 0.01)

        let collapsedFullWidth = MacToolbarLayoutMetrics.clampedInitialOrLiveDetailWidth(
            rawDetailWidth: 1140.0,
            windowContentWidth: 1140.0,
            sidebarFrameWidth: 0.0,
            isSidebarCollapsed: true
        )
        XCTAssertEqual(collapsedFullWidth, 1140.0, accuracy: 0.01)

        // 2. MacMagicPlusDropResolver: To-Do midpoint, Heading gutter, Upcoming epochDayOverride & Evening
        let projectSlots: [MacMagicPlusSlotGeometry] = [
            MacMagicPlusSlotGeometry(
                id: "task-1",
                kind: .task(
                    taskId: "t1",
                    headingId: nil,
                    prevOrder: nil,
                    taskOrder: 1000.0,
                    nextOrder: 2000.0,
                    epochDayOverride: nil,
                    evening: false
                ),
                minX: 40.0,
                maxX: 680.0,
                minY: 100.0,
                maxY: 140.0
            ),
            MacMagicPlusSlotGeometry(
                id: "task-2",
                kind: .task(
                    taskId: "t2",
                    headingId: nil,
                    prevOrder: 1000.0,
                    taskOrder: 2000.0,
                    nextOrder: nil,
                    epochDayOverride: nil,
                    evening: false
                ),
                minX: 40.0,
                maxX: 680.0,
                minY: 148.0,
                maxY: 188.0
            ),
            MacMagicPlusSlotGeometry(
                id: "heading-1",
                kind: .heading(
                    headingId: "h1",
                    prevHeadingOrder: nil,
                    headingOrder: 1000.0,
                    firstTaskOrder: 500.0,
                    prevSectionId: nil,
                    prevSectionLastTaskOrder: 2000.0
                ),
                minX: 40.0,
                maxX: 680.0,
                minY: 210.0,
                maxY: 246.0
            )
        ]

        let midTaskDrop = MacMagicPlusDropResolver.resolve(
            pointerX: 300.0,
            pointerY: 155.0,
            isProjectView: true,
            slots: projectSlots
        )
        XCTAssertEqual(midTaskDrop.mode, .todo)
        XCTAssertEqual(midTaskDrop.indicatorSlotId, "task-2")
        XCTAssertEqual(midTaskDrop.indicatorEdge, .before)
        XCTAssertEqual(midTaskDrop.prevTaskOrder, 1000.0)
        XCTAssertEqual(midTaskDrop.nextTaskOrder, 2000.0)

        let headingGutterDrop = MacMagicPlusDropResolver.resolve(
            pointerX: 80.0,
            pointerY: 195.0,
            isProjectView: true,
            slots: projectSlots
        )
        XCTAssertEqual(headingGutterDrop.mode, .heading)
        XCTAssertEqual(headingGutterDrop.indicatorSlotId, "heading-1")
        XCTAssertEqual(headingGutterDrop.indicatorEdge, .before)
        XCTAssertNil(headingGutterDrop.prevHeadingOrder)
        XCTAssertEqual(headingGutterDrop.nextHeadingOrder, 1000.0)

        // Explicit sortOrder persistence in SQLite for both tasks and headings
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let proj = try db.createProject(title: "Roadmap")
        let createdMidTask = try db.createTask(
            title: "Dropped Task",
            projectId: proj.id,
            sortOrder: 1500.0
        )
        XCTAssertEqual(createdMidTask.sortOrder, 1500.0)
        let createdMidHeading = try db.createHeading(
            projectId: proj.id,
            title: "Dropped Heading",
            sortOrder: 750.0
        )
        XCTAssertEqual(createdMidHeading.sortOrder, 750.0)

        // 3. MacCalendarMonthGrid + DateEpochConverter round-trip (German Monday-first vs US Sunday-first)
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = .current
        let march15 = cal.date(from: DateComponents(year: 2026, month: 3, day: 15))!
        let march20 = cal.date(from: DateComponents(year: 2026, month: 3, day: 20))!

        let epochDay = DateEpochConverter.epochDay(for: march20, calendar: cal)
        let roundTripDate = DateEpochConverter.date(fromEpochDay: epochDay, calendar: cal)
        XCTAssertEqual(cal.startOfDay(for: roundTripDate), cal.startOfDay(for: march20))

        let deGrid = MacCalendarMonthGrid.build(
            displayedMonth: march15,
            selectedDate: march20,
            today: march15,
            locale: Locale(identifier: "de_DE")
        )
        XCTAssertTrue(deGrid.days.count == 35 || deGrid.days.count == 42)
        XCTAssertEqual(deGrid.weekdaySymbols.count, 7)
        XCTAssertEqual(deGrid.days.filter(\.isToday).map(\.dayNumber), [15])
        XCTAssertEqual(deGrid.days.filter(\.isSelected).map(\.dayNumber), [20])

        let usGrid = MacCalendarMonthGrid.build(
            displayedMonth: march15,
            selectedDate: nil,
            today: march15,
            locale: Locale(identifier: "en_US")
        )
        XCTAssertNotEqual(deGrid.days.first?.id, usGrid.days.first?.id, "de_DE Monday-first and en_US Sunday-first grids must align to their respective first weekday")
    }

    func testBeta19FilterActiveOrUpcomingCalendarEventsRemovesPastAppointments() {
        let todayDay = 20_530
        let yesterdayDay = todayDay - 1
        let tomorrowDay = todayDay + 1
        let nowMillis: Int64 = 1_773_855_000_000 // e.g., afternoon on todayDay

        func makeEvent(
            id: String,
            title: String,
            startMillis: Int64,
            endMillis: Int64,
            isAllDay: Bool = false,
            startDay: Int,
            endDay: Int
        ) -> CalendarEventItem {
            CalendarEventItem(
                id: id,
                eventIdentifier: id,
                calendarId: "cal-1",
                calendarDisplayName: "Work",
                title: title,
                startMillis: startMillis,
                endMillis: endMillis,
                isAllDay: isAllDay,
                colorHex: 0x2E7CF6,
                startDateEpochDay: startDay,
                endDateInclusiveEpochDay: endDay
            )
        }

        let yesterdayTimed = makeEvent(
            id: "ev-yesterday",
            title: "Yesterday Meeting",
            startMillis: nowMillis - 86_400_000 - 3_600_000,
            endMillis: nowMillis - 86_400_000,
            startDay: yesterdayDay,
            endDay: yesterdayDay
        )
        let endedMorningToday = makeEvent(
            id: "ev-ended-today",
            title: "Morning Standup",
            startMillis: nowMillis - 14_400_000,
            endMillis: nowMillis - 12_600_000,
            startDay: todayDay,
            endDay: todayDay
        )
        let endedExactBoundary = makeEvent(
            id: "ev-ended-boundary",
            title: "Ended Just Now",
            startMillis: nowMillis - 1_800_000,
            endMillis: nowMillis,
            startDay: todayDay,
            endDay: todayDay
        )
        let ongoingToday = makeEvent(
            id: "ev-ongoing-today",
            title: "Current Review",
            startMillis: nowMillis - 900_000,
            endMillis: nowMillis + 2_700_000,
            startDay: todayDay,
            endDay: todayDay
        )
        let futureEveningToday = makeEvent(
            id: "ev-future-today",
            title: "Evening Call",
            startMillis: nowMillis + 7_200_000,
            endMillis: nowMillis + 10_800_000,
            startDay: todayDay,
            endDay: todayDay
        )
        let allDayToday = makeEvent(
            id: "ev-allday-today",
            title: "All-Day Offsite",
            startMillis: nowMillis - 36_000_000,
            endMillis: nowMillis - 1_000, // Even if UTC midnight endMillis passed in local offset, isAllDay stays active for todayDay
            isAllDay: true,
            startDay: todayDay,
            endDay: todayDay
        )
        let tomorrowTimed = makeEvent(
            id: "ev-tomorrow",
            title: "Tomorrow Planning",
            startMillis: nowMillis + 86_400_000,
            endMillis: nowMillis + 90_000_000,
            startDay: tomorrowDay,
            endDay: tomorrowDay
        )

        let allEvents = [
            yesterdayTimed,
            endedMorningToday,
            endedExactBoundary,
            ongoingToday,
            futureEveningToday,
            allDayToday,
            tomorrowTimed
        ]

        // Today view (upcomingOnly: false) keeps only ongoing, future today, and today's all-day
        let todayFiltered = CalendarEventItem.filterActiveOrUpcoming(
            allEvents,
            forEpochDay: todayDay,
            todayEpochDay: todayDay,
            nowMillis: nowMillis,
            upcomingOnly: false
        )
        XCTAssertEqual(todayFiltered.map(\.id), ["ev-ongoing-today", "ev-future-today", "ev-allday-today"])

        // Past day query always returns empty
        XCTAssertEqual(
            CalendarEventItem.filterActiveOrUpcoming(
                allEvents,
                forEpochDay: yesterdayDay,
                todayEpochDay: todayDay,
                nowMillis: nowMillis,
                upcomingOnly: false
            ),
            []
        )

        // Planned / Upcoming view (upcomingOnly: true) strictly excludes yesterday and today, keeps tomorrow's future event
        XCTAssertEqual(
            CalendarEventItem.filterActiveOrUpcoming(
                allEvents,
                forEpochDay: yesterdayDay,
                todayEpochDay: todayDay,
                nowMillis: nowMillis,
                upcomingOnly: true
            ),
            []
        )
        XCTAssertEqual(
            CalendarEventItem.filterActiveOrUpcoming(
                allEvents,
                forEpochDay: todayDay,
                todayEpochDay: todayDay,
                nowMillis: nowMillis,
                upcomingOnly: true
            ),
            []
        )
        let tomorrowFiltered = CalendarEventItem.filterActiveOrUpcoming(
            allEvents,
            forEpochDay: tomorrowDay,
            todayEpochDay: todayDay,
            nowMillis: nowMillis,
            upcomingOnly: true
        )
        XCTAssertEqual(tomorrowFiltered.map(\.id), ["ev-tomorrow"])
    }

    func testTopbarTooltipLocalizationKeysV200Beta20() {
        let keys = [
            "topbar_tooltip_hide_sidebar",
            "topbar_tooltip_show_sidebar",
            "topbar_tooltip_quick_find",
            "topbar_tooltip_weekly_review",
            "topbar_tooltip_templates",
            "topbar_tooltip_quick_entry",
            "topbar_tooltip_voice_input",
            "topbar_tooltip_areas",
            "topbar_tooltip_tags",
            "topbar_tooltip_notifications",
            "topbar_tooltip_language",
            "topbar_tooltip_cloud_sync",
            "topbar_tooltip_settings"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in keys {
                let value = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertFalse(value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, "Blank \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(value, key, "Missing \(key) in \(lang.rawValue)")
                XCTAssertFalse(value.contains("("), "Top-bar tooltip \(key) in \(lang.rawValue) must not contain shortcut clutter")
                XCTAssertFalse(value.localizedCaseInsensitiveContains("Room"), "Top-bar tooltip \(key) must never expose Android Room DB text")
            }
        }
        XCTAssertEqual(ThingsStrings.shared.text("topbar_tooltip_quick_entry", lang: .en), "Quick Entry")
        XCTAssertEqual(ThingsStrings.shared.text("topbar_tooltip_quick_entry", lang: .de), "Schnelleingabe")
        XCTAssertEqual(ThingsStrings.shared.text("topbar_tooltip_cloud_sync", lang: .en), "Cloud Sync")
        XCTAssertEqual(ThingsStrings.shared.text("topbar_tooltip_cloud_sync", lang: .de), "Cloud-Sync")
        XCTAssertEqual(ThingsStrings.shared.text("topbar_tooltip_templates", lang: .en), "Templates & Import")
        XCTAssertEqual(ThingsStrings.shared.text("topbar_tooltip_areas", lang: .en), "Areas")
        XCTAssertEqual(ThingsStrings.shared.text("topbar_tooltip_tags", lang: .en), "Categories")
        XCTAssertEqual(ThingsStrings.shared.text("topbar_tooltip_tags", lang: .de), "Kategorien")
    }

    // MARK: - 21. v2.0.0-beta.22 Cross-Platform Sync Overhaul (Cialis Area-Split Dedup, v1.8 Guards, Tag Remap & Ntfy Chunks)

    func testCloudSyncV18ParityRecurringCialisDedupAndTagRemapV200Beta22() throws {
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let dailyRule = RecurrenceRule(mode: .fixedSchedule, interval: 1, unit: .days)
        let baseNow = Int64(Date().timeIntervalSince1970 * 1000)

        // 1. Deterministic duplicate-tag reconciliation ("Gesundheit" created independently on two devices)
        //    Older (createdAt, id) wins without violating SQLite partial unique index idx_tags_title_active,
        //    and task/project references remap cleanly to the winning tag ID.
        let tagWinner = Tag(
            id: "tag-gesundheit-older",
            title: "Gesundheit",
            sortOrder: 100.0,
            createdAt: baseNow - 500_000,
            updatedAt: baseNow - 400_000
        )
        let tagLoserNewer = Tag(
            id: "tag-gesundheit-newer",
            title: "Gesundheit",
            sortOrder: 200.0,
            createdAt: baseNow - 100_000,
            updatedAt: baseNow - 50_000
        )
        let areaPrivat = Area(
            id: "area-privat",
            title: "Privat",
            sortOrder: 100.0,
            createdAt: baseNow - 600_000,
            updatedAt: baseNow - 600_000
        )
        let projWithLoserTag = Project(
            id: "proj-health",
            areaId: areaPrivat.id,
            title: "Vorsorge",
            tagIds: [tagLoserNewer.id],
            createdAt: baseNow - 300_000,
            updatedAt: baseNow - 300_000
        )

        // Exact reproduction of screenshot 22828: two open daily "Cialis" tasks (one areaId = nil, one areaId = "area-privat")
        let cialisNoAreaNewer = TaskItem(
            id: "cialis-no-area",
            title: "Cialis",
            notes: "",
            status: .open,
            scheduleState: .today,
            whenDate: baseNow + 86_400_000,
            areaId: nil,
            recurrenceRule: dailyRule,
            sortOrder: 100.0,
            createdAt: baseNow - 80_000,
            updatedAt: baseNow - 20_000
        )
        let cialisPrivatOlder = TaskItem(
            id: "cialis-privat",
            title: "Cialis",
            notes: "5mg",
            status: .open,
            scheduleState: .today,
            whenDate: baseNow,
            scheduledStartMinutes: 480,
            durationMinutes: 15,
            areaId: areaPrivat.id,
            recurrenceRule: dailyRule,
            sortOrder: 200.0,
            createdAt: baseNow - 180_000,
            updatedAt: baseNow - 120_000
        )

        let plan = ThingsSQLiteDatabase.planRecurringDeduplication(
            [cialisNoAreaNewer, cialisPrivatOlder],
            overrideTagIdsByTaskId: [
                cialisNoAreaNewer.id: [tagLoserNewer.id],
                cialisPrivatOlder.id: [tagWinner.id]
            ]
        )
        XCTAssertEqual(plan.loserIds, ["cialis-privat"])
        let enrichment = try XCTUnwrap(plan.winnerEnrichments.first(where: { $0.winnerId == "cialis-no-area" }))
        XCTAssertEqual(enrichment.areaId, "area-privat")
        XCTAssertEqual(enrichment.notes, "5mg")
        XCTAssertEqual(enrichment.scheduledStartMinutes, 480)
        XCTAssertEqual(enrichment.durationMinutes, 15)

        // Apply via applyRemoteDeltas with tagLoserNewer arriving FIRST and tagWinner arriving SECOND to exercise Case A & Case B
        _ = try db.applyRemoteDeltas(
            RemoteDeltaBatch(
                tags: [tagLoserNewer, tagWinner],
                areas: [areaPrivat],
                projects: [projWithLoserTag],
                tasks: [cialisNoAreaNewer, cialisPrivatOlder],
                taskTagIdsByTaskId: [
                    cialisNoAreaNewer.id: [tagLoserNewer.id],
                    cialisPrivatOlder.id: [tagWinner.id]
                ]
            )
        )

        var snap = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(snap.tags.map(\.id), ["tag-gesundheit-older"], "Older same-title Tag must win deterministically")
        XCTAssertEqual(snap.projects.first(where: { $0.id == "proj-health" })?.tagIds, ["tag-gesundheit-older"])

        let openCialis = snap.allOpenTasks.filter { $0.title == "Cialis" }
        XCTAssertEqual(openCialis.count, 1, "Duplicate Cialis recurring task must be eliminated")
        let survivingCialis = try XCTUnwrap(openCialis.first)
        XCTAssertEqual(survivingCialis.id, "cialis-no-area")
        XCTAssertEqual(survivingCialis.areaId, "area-privat", "Surviving Cialis must inherit Privat Area")
        XCTAssertEqual(survivingCialis.notes, "5mg")
        XCTAssertEqual(survivingCialis.scheduledStartMinutes, 480)
        XCTAssertEqual(survivingCialis.durationMinutes, 15)
        XCTAssertEqual(survivingCialis.tags.map(\.id), ["tag-gesundheit-older"])
        XCTAssertFalse(snap.trashedTasks.contains { $0.id == "cialis-privat" }, "Purged duplicate loser must not appear in Trash")

        // 2. v1.8 omitted timeblock fields preserve local v2.0 timeblock, while explicit v2.0 null clears it
        let v18EditedCialis = TaskItem(
            id: survivingCialis.id,
            title: "Cialis",
            notes: "5mg täglich",
            status: .open,
            scheduleState: .today,
            whenDate: survivingCialis.whenDate,
            scheduledStartMinutes: nil,
            durationMinutes: nil,
            areaId: "area-privat",
            recurrenceRule: dailyRule,
            sortOrder: 100.0,
            createdAt: survivingCialis.createdAt,
            updatedAt: survivingCialis.updatedAt + 5_000
        )
        _ = try db.applyRemoteDeltas(
            RemoteDeltaBatch(
                tasks: [v18EditedCialis],
                taskTagIdsByTaskId: [survivingCialis.id: ["tag-gesundheit-older"]],
                taskOmittedTimeblockTaskIds: [survivingCialis.id]
            )
        )
        snap = try db.loadWorkspaceSnapshot()
        let afterV18Edit = try XCTUnwrap(snap.allOpenTasks.first(where: { $0.id == survivingCialis.id }))
        XCTAssertEqual(afterV18Edit.notes, "5mg täglich")
        XCTAssertEqual(afterV18Edit.scheduledStartMinutes, 480, "v1.8 payload omitting timeblock fields must not wipe v2.0 timeblock")
        XCTAssertEqual(afterV18Edit.durationMinutes, 15)

        var v20ExplicitClear = v18EditedCialis
        v20ExplicitClear.updatedAt = afterV18Edit.updatedAt + 5_000
        _ = try db.applyRemoteDeltas(
            RemoteDeltaBatch(
                tasks: [v20ExplicitClear],
                taskTagIdsByTaskId: [survivingCialis.id: ["tag-gesundheit-older"]],
                taskOmittedTimeblockTaskIds: []
            )
        )
        snap = try db.loadWorkspaceSnapshot()
        let afterV20Clear = try XCTUnwrap(snap.allOpenTasks.first(where: { $0.id == survivingCialis.id }))
        XCTAssertNil(afterV20Clear.scheduledStartMinutes, "Explicit v2.0 null timeblock must clear scheduledStartMinutes")
        XCTAssertNil(afterV20Clear.durationMinutes)

        // 3. v1.8 startup rollover resurrection guard: locally COMPLETED recurring parent stays COMPLETED when OPEN sibling exists
        let completedParent = TaskItem(
            id: "cialis-completed-yesterday",
            title: "Cialis",
            status: .completed,
            scheduleState: .today,
            whenDate: baseNow - 86_400_000,
            completedAt: baseNow - 70_000_000,
            areaId: "area-privat",
            recurrenceRule: dailyRule,
            createdAt: baseNow - 200_000_000,
            updatedAt: baseNow - 70_000_000
        )
        _ = try db.applyRemoteDeltas(RemoteDeltaBatch(tasks: [completedParent]))

        var resurrectedByV18Rollover = completedParent
        resurrectedByV18Rollover.status = .open
        resurrectedByV18Rollover.completedAt = nil
        resurrectedByV18Rollover.whenDate = baseNow + 86_400_000
        resurrectedByV18Rollover.updatedAt = afterV20Clear.updatedAt + 5_000
        _ = try db.applyRemoteDeltas(RemoteDeltaBatch(tasks: [resurrectedByV18Rollover]))

        snap = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(snap.allOpenTasks.filter { $0.title == "Cialis" }.map(\.id), [survivingCialis.id])
        XCTAssertTrue(snap.logbookTasks.contains { $0.id == "cialis-completed-yesterday" && $0.status == .completed })

        // 4. parseTaskWithHints (NSNull vs omitted), purgedFromTrash, makeEntityUpdateWrite updateMask, and buildLegacyCompatibleNtfyChunks
        let parsedOmitted = try XCTUnwrap(
            FirebaseCloudSyncService.parseTaskWithHints([
                "id": "t-v18",
                "title": "Legacy Task",
                "status": "OPEN",
                "scheduleState": "TODAY",
                "updatedAt": Int64(1_000)
            ])
        )
        XCTAssertTrue(parsedOmitted.omittedTimeblock)

        let parsedExplicitNull = try XCTUnwrap(
            FirebaseCloudSyncService.parseTaskWithHints([
                "id": "t-v20",
                "title": "Modern Task",
                "status": "OPEN",
                "scheduleState": "TODAY",
                "scheduledStartMinutes": NSNull(),
                "durationMinutes": NSNull(),
                "updatedAt": Int64(2_000)
            ])
        )
        XCTAssertFalse(parsedExplicitNull.omittedTimeblock)
        XCTAssertNil(parsedExplicitNull.task.scheduledStartMinutes)

        let parsedPurgedProj = try XCTUnwrap(
            FirebaseCloudSyncService.parseProject([
                "id": "p-purged",
                "title": "Old Title",
                "notes": "Old Notes",
                "isDeleted": true,
                "purgedFromTrash": true,
                "updatedAt": Int64(3_000)
            ])
        )
        XCTAssertTrue(parsedPurgedProj.isDeleted)
        XCTAssertEqual(parsedPurgedProj.title, "")
        XCTAssertEqual(parsedPurgedProj.notes, "")

        let entityWrite = FirebaseCloudSyncService.makeEntityUpdateWrite(
            path: "projects/thingsbook-prod/databases/(default)/documents/users/u1/tasks/t1",
            fields: ["title": ["stringValue": "Hello"], "updatedAt": ["integerValue": "100"]]
        )
        let maskPaths = (entityWrite["updateMask"] as? [String: Any])?["fieldPaths"] as? [String]
        XCTAssertEqual(maskPaths, ["title", "updatedAt"])

        let manyWireTasks: [[String: Any]] = (1...24).map { idx in
            [
                "id": "chunk-t-\(idx)",
                "title": "Sync Task #\(idx) " + String(repeating: "z", count: 120),
                "status": "OPEN",
                "scheduleState": "TODAY",
                "updatedAt": Int64(1_795_000_000_000 + idx)
            ]
        }
        let ntfyChunks = FirebaseCloudSyncService.buildLegacyCompatibleNtfyChunks(
            uid: "u1",
            deviceId: "mac-1",
            batchTimestamp: 1_795_000_999_000,
            collections: [(key: "tasks", items: manyWireTasks)]
        )
        XCTAssertFalse(ntfyChunks.isEmpty)
        for chunkData in ntfyChunks {
            XCTAssertLessThanOrEqual(chunkData.count, 3800)
            let obj = try XCTUnwrap(JSONSerialization.jsonObject(with: chunkData) as? [String: Any])
            XCTAssertNil(obj["wakeOnly"], "Legacy tb_sync_* chunk must never emit wakeOnly")
        }
    }

    // MARK: - 22. v2.0.0-beta.23 macOS Settings Icon+Label Toolbar Layout Metrics Across All 5 Languages

    func testMacSettingsTabLayoutMetricsNoTruncationAcrossAllFiveLanguagesV200Beta23() {
        let langs: [AppLanguage] = [.en, .de, .fr, .it, .es]
        for lang in langs {
            let titles = [
                ThingsStrings.shared.text("macos_tab_general", lang: lang),
                ThingsStrings.shared.text("macos_tab_sync", lang: lang),
                ThingsStrings.shared.text("macos_tab_calendar", lang: lang),
                ThingsStrings.shared.text("macos_tab_notifications", lang: lang),
                ThingsStrings.shared.text("macos_tab_backup", lang: lang),
                ThingsStrings.shared.text("macos_tab_updates", lang: lang)
            ]
            XCTAssertEqual(titles.count, 6)
            for title in titles {
                XCTAssertFalse(title.isEmpty)
                XCTAssertGreaterThanOrEqual(
                    MacSettingsTabLayoutMetrics.estimatedTabWidth(forTitle: title),
                    MacSettingsTabLayoutMetrics.minTabWidth
                )
            }
            let totalWidth = MacSettingsTabLayoutMetrics.totalTabBarWidth(forTitles: titles)
            XCTAssertTrue(
                MacSettingsTabLayoutMetrics.fitsWithoutTruncation(
                    titles: titles,
                    inWindowWidth: MacSettingsTabLayoutMetrics.recommendedWindowWidth
                ),
                "All 6 Settings tabs in [\(lang.rawValue)] (\(titles.joined(separator: " | "))) must fit without truncation inside \(MacSettingsTabLayoutMetrics.recommendedWindowWidth)pt (got \(totalWidth)pt)"
            )
        }
    }

    // MARK: - 23. v2.0.0-beta.24 Sample Tasks Detection (Logbook Exclusion, User-Renamed Preservation & Purged Cloud Tombstones)

    func testSampleTasksDetectionIgnoresLogbookCompletedAndPreservesRenamedAndEmitsPurgedCloudTombstonesV200Beta24() throws {
        // 1. Fresh seeded DB has 7 open sample tasks + 1 completed Logbook sample task (task-completed-008)
        let db1 = try ThingsSQLiteDatabase(path: ":memory:")
        try db1.seedIfEmpty(languageCode: "de")
        let initialSnap = try db1.loadWorkspaceSnapshot()
        XCTAssertTrue(initialSnap.hasSampleTasks)
        XCTAssertFalse(initialSnap.hasNonSampleUserTasks)
        XCTAssertTrue(initialSnap.logbookTasks.contains(where: { $0.id == "task-completed-008" }))

        // Completing or deleting the 7 open sample tasks leaves task-completed-008 in Logbook, which MUST NOT flag hasSampleTasks
        for openSample in initialSnap.allOpenTasks {
            try db1.setTaskStatus(taskId: openSample.id, status: .completed)
        }
        let allCompletedSnap = try db1.loadWorkspaceSnapshot()
        XCTAssertEqual(allCompletedSnap.logbookTasks.count, 8)
        XCTAssertFalse(
            allCompletedSnap.hasSampleTasks,
            "Completed Logbook sample tasks (including task-completed-008) must never trigger 'Beispielaufgaben noch vorhanden'"
        )

        // 2. Renaming task-inbox-001 into a personal task and purging sample data with emitCloudTombstones = true
        let db2 = try ThingsSQLiteDatabase(path: ":memory:")
        try db2.seedIfEmpty(languageCode: "de")
        let seededSnap2 = try db2.loadWorkspaceSnapshot()
        var renamedInbox = try XCTUnwrap(seededSnap2.allOpenTasks.first(where: { $0.id == "task-inbox-001" }))
        renamedInbox.title = "Steuererklärung 2026 einreichen"
        try db2.updateTask(renamedInbox)

        for other in seededSnap2.allOpenTasks where other.id != "task-inbox-001" {
            try db2.softDeleteTask(other.id)
        }
        let customizedSnap = try db2.loadWorkspaceSnapshot()
        XCTAssertFalse(customizedSnap.hasSampleTasks, "Renamed sample task must not trigger sample task warning")
        XCTAssertTrue(customizedSnap.hasNonSampleUserTasks, "Renamed sample task counts as a user task")

        // Reset db3 to verify cloud tombstone purge preserves renamed task while scrubbing unmodified sample tasks without cluttering Trash
        let db3 = try ThingsSQLiteDatabase(path: ":memory:")
        try db3.seedIfEmpty(languageCode: "de")
        var inboxToRename = try XCTUnwrap(try db3.loadWorkspaceSnapshot().allOpenTasks.first(where: { $0.id == "task-inbox-001" }))
        inboxToRename.title = "Steuererklärung 2026 einreichen"
        try db3.updateTask(inboxToRename)

        try db3.purgeSampleDataLocally(preserveUsedContainers: true, emitCloudTombstones: true)
        let afterPurgeSnap = try db3.loadWorkspaceSnapshot()
        XCTAssertFalse(afterPurgeSnap.hasSampleTasks)
        XCTAssertEqual(afterPurgeSnap.allOpenTasks.map(\.title), ["Steuererklärung 2026 einreichen"])
        XCTAssertTrue(afterPurgeSnap.logbookTasks.isEmpty, "Unmodified Logbook sample task-completed-008 must be purged")
        XCTAssertTrue(afterPurgeSnap.trashedTasks.isEmpty, "Purged cloud tombstones must never appear in Trash")

        let dirty = try db3.fetchDirtyBatch()
        let dirtyPurgedIds = Set(dirty.tasks.filter { $0.isDeleted && $0.title.isEmpty && $0.notes.isEmpty }.map(\.id))
        XCTAssertTrue(dirtyPurgedIds.contains("task-completed-008"))
        XCTAssertTrue(dirtyPurgedIds.contains("task-today-002"))
        XCTAssertFalse(dirtyPurgedIds.contains("task-inbox-001"))
    }

    // MARK: - 24. v2.0.0-beta.25 Task Card Focus Suppression & Today Evening Quick Pill

    func testTaskCardInteractionRulesNoAutoSelectAndTodayEveningQuickPillBeta25() {
        XCTAssertTrue(TaskCardInteractionRules.shouldAutoFocusTitleOnExpand(title: ""))
        XCTAssertTrue(TaskCardInteractionRules.shouldAutoFocusTitleOnExpand(title: "   \n "))
        XCTAssertFalse(TaskCardInteractionRules.shouldAutoFocusTitleOnExpand(title: "Steuererklärung prüfen"))

        XCTAssertFalse(TaskCardInteractionRules.shouldAutoEditNotesOnExpand(notes: ""))
        XCTAssertFalse(TaskCardInteractionRules.shouldAutoEditNotesOnExpand(notes: "Wichtige Notiz"))

        XCTAssertTrue(
            TaskCardInteractionRules.shouldShowQuickThisEveningPill(
                isTodayView: true,
                status: .open,
                scheduleState: .today
            )
        )
        XCTAssertFalse(
            TaskCardInteractionRules.shouldShowQuickThisEveningPill(
                isTodayView: true,
                status: .open,
                scheduleState: .thisEvening
            )
        )
        XCTAssertFalse(
            TaskCardInteractionRules.shouldShowQuickThisEveningPill(
                isTodayView: false,
                status: .open,
                scheduleState: .today
            )
        )
        XCTAssertFalse(
            TaskCardInteractionRules.shouldShowQuickThisEveningPill(
                isTodayView: true,
                status: .completed,
                scheduleState: .today
            )
        )
    }

    // MARK: - 25. v2.0.0-beta.26 Compact macOS Calendar Appointments (Android & ChromeOS Parity)

    func testMacCalendarAppointmentCompactStyleMetricsBeta26() {
        XCTAssertTrue(MacCalendarAppointmentStyleMetrics.isSubordinateToTaskRow)
        XCTAssertLessThan(
            MacCalendarAppointmentStyleMetrics.eventTitleFontSize,
            MacCalendarAppointmentStyleMetrics.taskTitleFontSize
        )
        XCTAssertLessThan(
            MacCalendarAppointmentStyleMetrics.eventTimeFontSize,
            MacCalendarAppointmentStyleMetrics.eventTitleFontSize
        )
        XCTAssertFalse(MacCalendarAppointmentStyleMetrics.showsInlineOpenInCalendarButton)
    }

    // MARK: - 26. v3.0.0-alpha.2 Project Phases (endDate) & 100% v2.x Compatibility

    func testProjectPhasesEndDateAndV2CompatibilityAlpha2() throws {
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let proj = try db.createProject(title: "Q4 Architecture", notes: "")
        let phase = try db.createHeading(projectId: proj.id, title: "Phase 1: Discovery")
        XCTAssertNil(phase.endDate)

        let milestoneMs: Int64 = DateEpochConverter.startOfDayMillis(
            for: Calendar.current.date(byAdding: .day, value: 5, to: Date()) ?? Date()
        )
        try db.updateHeadingEndDate(phase.id, endDate: milestoneMs)

        var snap = try db.loadWorkspaceSnapshot()
        let updatedPhase = try XCTUnwrap(snap.headingsByProject[proj.id]?.first(where: { $0.id == phase.id }))
        XCTAssertEqual(updatedPhase.endDate, milestoneMs)

        // 1. Save as template preserves endDateDayOffset and instantiates Phase endDate cleanly
        let savedTemplate = try db.saveProjectAsTemplate(projectId: proj.id, templateTitle: "Phased Playbook")
        XCTAssertEqual(savedTemplate.blueprint.headings.first?.endDateDayOffset, 5)
        let clonedProj = try db.instantiateProjectFromTemplate(savedTemplate, titleOverride: "Cloned Phased")
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertNotNil(snap.headingsByProject[clonedProj.id]?.first?.endDate)

        // JSON "phases" alias decodes into blueprint.headings while encoding keeps "headings" key
        let aliasJson = """
        {"phases":[{"title":"Launch Phase","endDateDayOffset":10,"tasks":[{"title":"Ship DMG","notes":"","checklistItems":[]}]}]}
        """
        let decodedAlias = try JSONDecoder().decode(ProjectTemplateBlueprint.self, from: Data(aliasJson.utf8))
        XCTAssertEqual(decodedAlias.headings.first?.title, "Launch Phase")
        XCTAssertEqual(decodedAlias.headings.first?.endDateDayOffset, 10)
        XCTAssertTrue(decodedAlias.toJSONString().contains("\"headings\""))

        // 2. .tbbackup roundtrip preserves Phase endDate, while v2.x heading without endDate decodes to nil
        let backupPayload = try db.buildBackupPayload(preferences: AppPreferences())
        let encodedBackup = try ThingsBackupCodec.encodeBackup(payload: backupPayload, password: nil)
        let decodedBackup = try ThingsBackupCodec.decodeBackup(rawBytes: encodedBackup, password: nil)
        XCTAssertEqual(decodedBackup.headings.first(where: { $0.id == phase.id })?.endDate, milestoneMs)

        // 3. Remote sync from a v2.x client omitting "endDate" preserves local Phase endDate!
        let v2RemoteParsed = try XCTUnwrap(FirebaseCloudSyncService.parseHeadingWithHints([
            "id": phase.id,
            "projectId": proj.id,
            "title": "Phase 1: Discovery (Renamed on v2.1)",
            "sortOrder": phase.sortOrder,
            "isArchived": false,
            "createdAt": phase.createdAt,
            "updatedAt": Int64(Date().timeIntervalSince1970 * 1000) + 5_000,
            "isDeleted": false
        ]))
        XCTAssertTrue(v2RemoteParsed.omittedEndDate)
        _ = try db.applyRemoteDeltas(
            RemoteDeltaBatch(
                headings: [v2RemoteParsed.heading],
                headingOmittedEndDateIds: [phase.id]
            )
        )
        snap = try db.loadWorkspaceSnapshot()
        let preservedPhase = try XCTUnwrap(snap.headingsByProject[proj.id]?.first(where: { $0.id == phase.id }))
        XCTAssertEqual(preservedPhase.title, "Phase 1: Discovery (Renamed on v2.1)")
        XCTAssertEqual(preservedPhase.endDate, milestoneMs)

        // Whereas an explicit v3.0 remote clear ("endDate": NSNull()) clears endDate!
        let v3ClearParsed = try XCTUnwrap(FirebaseCloudSyncService.parseHeadingWithHints([
            "id": phase.id,
            "projectId": proj.id,
            "title": "Phase 1: Discovery (Cleared on v3.0)",
            "endDate": NSNull(),
            "sortOrder": phase.sortOrder,
            "isArchived": false,
            "createdAt": phase.createdAt,
            "updatedAt": Int64(Date().timeIntervalSince1970 * 1000) + 10_000,
            "isDeleted": false
        ]))
        XCTAssertFalse(v3ClearParsed.omittedEndDate)
        _ = try db.applyRemoteDeltas(
            RemoteDeltaBatch(
                headings: [v3ClearParsed.heading],
                headingOmittedEndDateIds: []
            )
        )
        snap = try db.loadWorkspaceSnapshot()
        let clearedPhase = try XCTUnwrap(snap.headingsByProject[proj.id]?.first(where: { $0.id == phase.id }))
        XCTAssertEqual(clearedPhase.title, "Phase 1: Discovery (Cleared on v3.0)")
        XCTAssertNil(clearedPhase.endDate)
    }

    // MARK: - 27. v3.0.0-alpha.3 Option A Sidebar, Categories Terminology, Search Strings & CSV Header Compatibility

    func testCategoriesRenameSearchStringsAndCsvAliasesV300Alpha3() {
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in [
                "sidebar_review_short",
                "tags_search_or_create_placeholder",
                "tags_filter_placeholder",
                "tags_create_from_search",
                "tags_no_filter_matches"
            ] {
                let val = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertFalse(val.isEmpty, "Missing \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(val, key, "Unresolved \(key) in \(lang.rawValue)")
            }
            let formatted = ThingsStrings.shared.format("tags_create_from_search", lang: lang, "Focus")
            XCTAssertTrue(formatted.contains("Focus"), "Formatted tags_create_from_search in \(lang.rawValue) must embed query")
        }

        XCTAssertEqual(ThingsStrings.shared.text("sidebar_review_short", lang: .en), "Review")
        XCTAssertEqual(ThingsStrings.shared.text("sidebar_review_short", lang: .de), "Rückblick")
        XCTAssertEqual(ThingsStrings.shared.text("tags_manage_title", lang: .en), "Manage Categories")
        XCTAssertEqual(ThingsStrings.shared.text("tags_manage_title", lang: .de), "Kategorien verwalten")

        let csvEn = "Title,Categories\nPrepare Q4 Deck,Focus;Office\n"
        let rowsEn = CsvCodec.parseCsvTasks(csvContent: csvEn)
        XCTAssertEqual(rowsEn.count, 1)
        XCTAssertEqual(rowsEn.first?.tagTitles, ["Focus", "Office"])

        let csvDe = "Titel,Kategorien\nQuartalsbericht,Fokus;Büro\n"
        let rowsDe = CsvCodec.parseCsvTasks(csvContent: csvDe)
        XCTAssertEqual(rowsDe.count, 1)
        XCTAssertEqual(rowsDe.first?.tagTitles, ["Fokus", "Büro"])
    }
}
