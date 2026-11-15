import XCTest
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
        XCTAssertEqual(encInspected, .passwordProtected(createdAt: payload.createdAt, appVersionName: "1.8.0"))

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

        // 2. Verify semantic update check upgrades 1.7.0 & 1.8.0 -> 1.8.0+1 without looping on 1.8.0+1
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "1.8.0+1", current: "1.7.0"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "1.8.0+1", current: "1.8.0"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "1.8.0+1", current: "1.8.0+1"))

        // 3. Verify Firestore mixed integerValue / doubleValue / string coercion
        let intSortWrapped: [String: Any] = ["integerValue": "4"]
        let doubleSortWrapped: [String: Any] = ["doubleValue": NSNumber(value: 2.5)]
        XCTAssertEqual(FirebaseCloudSyncService.asDouble(FirebaseCloudSyncService.unwrapFirestoreValue(intSortWrapped)), 4.0)
        XCTAssertEqual(FirebaseCloudSyncService.asDouble(FirebaseCloudSyncService.unwrapFirestoreValue(doubleSortWrapped)), 2.5)
        XCTAssertEqual(FirebaseCloudSyncService.asInt64(NSNumber(value: 1_794_736_150_463.0)), 1_794_736_150_463)

        let parsedTask = FirebaseCloudSyncService.parseTask([
            "id": "remote-sync-task-1",
            "title": "Synced From ChromeOS",
            "notes": "Realtime delta verification",
            "status": "OPEN",
            "scheduleState": "TODAY",
            "whenDate": Int64(1_794_700_800_000),
            "sortOrder": Int64(7),
            "createdAt": Int64(1_794_736_150_000),
            "updatedAt": Int64(1_794_736_150_463),
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
            let applied = try db.applyRemoteDeltas(
                RemoteDeltaBatch(
                    tasks: [remoteItem],
                    taskTagIdsByTaskId: [remoteItem.id: remoteTags]
                )
            )
            XCTAssertTrue(applied)
        }
        XCTAssertEqual(box.mutations, 0, "Remote delta apply must not mark local DB mutated or bounce sync")
        XCTAssertEqual(box.remoteApplies, 1, "Remote delta apply must notify UI observer immediately")
        XCTAssertTrue(try db.fetchDirtyBatch().tasks.isEmpty, "Remote delta records must have syncDirty = 0")

        let searchHits = try db.searchQuickFind(rawQuery: "ChromeOS")
        XCTAssertEqual(searchHits.tasks.map(\.id), ["remote-sync-task-1"], "FTS5 must index remotely synced tasks")
    }
}
