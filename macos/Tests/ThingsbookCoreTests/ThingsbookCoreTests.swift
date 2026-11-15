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
        XCTAssertEqual(encInspected, .passwordProtected(createdAt: payload.createdAt, appVersionName: "2.0.0-beta.3"))

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

    // MARK: - 7. v2.0.0-beta.3 AppUpdateService Beta Channel, Stable Isolation & Version Bump

    func testAppUpdateServiceSemanticVersioningAndBetaIsolationV181() {
        XCTAssertEqual(AppUpdateService.currentVersion, "2.0.0-beta.3")
        XCTAssertEqual(AppUpdateService.displayVersion, "2.0.0-beta.3")
        XCTAssertEqual(AppUpdateService.currentBuildNumber, 47)
        XCTAssertTrue(AppUpdateService.currentVersionIsPrerelease)

        // Core [major, minor, patch] + build metadata comparisons on stable channel (includeBeta inferred false for 1.8.1)
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "1.8.1", current: "1.8.0+1"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "1.8.0+1", current: "1.8.0"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.1", current: "1.8.1"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.1", current: "1.8.1", includeBeta: false))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "v2.0.0-alpha.1", current: "1.8.1", includeBeta: false))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "v2.0.0-rc.1+2", current: "1.8.1+1", includeBeta: false))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "1.8.1", current: "1.8.1"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "v1.8.2", current: "1.8.1"))

        // Opt-in beta channel from stable 1.8.1 accepts 2.0.0-beta.1
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.1", current: "1.8.1", includeBeta: true))

        // Pre-release channel users upgrade across numeric pre-release tokens and to final 2.0.0
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.1", current: "2.0.0-beta.1"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "1.8.2", current: "2.0.0-beta.1"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.2", current: "2.0.0-beta.1"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.3", current: "2.0.0-beta.2"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.10", current: "2.0.0-beta.3"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "2.0.0-beta.3", current: "2.0.0-beta.10"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "2.0.0", current: "2.0.0-beta.1"))
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

        // 3. Running 2.0.0-beta.1 upgrades to highest beta (2.0.0-beta.10 > 2.0.0-beta.2) and then final 2.0.0
        let betaProgressionFeed: [[String: Any]] = mockReleases + [
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
}




