import XCTest
import SQLite3
import PDFKit
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
        XCTAssertEqual(AppUpdateService.currentVersion, "3.3.6")
        XCTAssertEqual(AppUpdateService.displayVersion, "3.3.6")
        XCTAssertEqual(AppUpdateService.currentBuildNumber, 127)
        XCTAssertFalse(AppUpdateService.currentVersionIsPrerelease)
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "3.3.6", current: "3.3.5"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "3.3.6", current: "3.3.4"))
        XCTAssertFalse(AppUpdateService.resolveEffectiveIncludeBeta(requested: false))
        XCTAssertTrue(AppUpdateService.resolveEffectiveIncludeBeta(requested: true))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "3.0.0-beta.19", current: "2.5.0", includeBeta: false))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "3.0.0-beta.19", current: "2.5.0", includeBeta: true))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "3.0.0-beta.19", current: "3.0.0-beta.18"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "3.0.0-rc.1", current: "3.0.0-beta.19"))
        XCTAssertTrue(AppUpdateService.isSemanticNewer(remote: "3.0.0", current: "3.0.0-beta.19"))
        XCTAssertFalse(AppUpdateService.isSemanticNewer(remote: "3.0.0-beta.19", current: "3.0.0", includeBeta: true))
        XCTAssertTrue(TaskItem.standaloneDurationPresets.contains(480))
        XCTAssertEqual(TaskItem.formatCompactDuration(240), "4h")
        XCTAssertEqual(TaskItem.formatCompactDuration(480), "8h")
        XCTAssertEqual(TaskItem.formatCompactDuration(720), "1d 4h")
        XCTAssertEqual(TaskItem.formatCompactDuration(960), "2d")
        XCTAssertEqual(TaskItem.parseCustomDurationMinutes("1d"), 480)
        XCTAssertEqual(TaskItem.parseCustomDurationMinutes("2,5d"), 1200)
        XCTAssertEqual(TaskItem.parseCustomDurationMinutes("1d 4h"), 720)
        XCTAssertEqual(TaskItem.parseCustomDurationMinutes("3t"), 1440)
        XCTAssertEqual(TaskItem.parseCustomDurationMinutes("1:30"), 90)
        XCTAssertEqual(TaskItem.parseCustomDurationMinutes("45"), 45)
        XCTAssertNil(TaskItem.parseCustomDurationMinutes("abc"))
        let ultraCompact = MacToolbarLayoutMetrics.compute(detailWidth: 506.0, isSidebarCollapsed: false)
        XCTAssertEqual(ultraCompact.usableWidth, 450.0)
        XCTAssertFalse(ultraCompact.showGeminiLabel)
        XCTAssertLessThanOrEqual(ultraCompact.totalBarWidth, ultraCompact.usableWidth)
        XCTAssertNotNil(ThingsResourceLoader.appIconImage(), "3.0 Glacier Liquid Glass Book AppIcon must be loadable at runtime")
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

    // MARK: - 28. v3.0.0-alpha.11 TaskDependencyGraph Cycle Detection & Auto-Unlocking

    func testV30TaskDependencyGraphCycleDetectionAndAutoUnlocking() {
        // A -> blocked by B, B -> blocked by C
        let blockedByMap: [String: [String]] = [
            "task-A": ["task-B"],
            "task-B": ["task-C"],
            "task-C": []
        ]

        // Self-cycle rejected
        XCTAssertTrue(
            TaskDependencyGraph.wouldCreateCycle(
                taskId: "task-A",
                candidateBlockerId: "task-A",
                blockedByMap: blockedByMap
            )
        )
        // Direct 2-node cycle (B blocked by A when A is already blocked by B) rejected
        XCTAssertTrue(
            TaskDependencyGraph.wouldCreateCycle(
                taskId: "task-B",
                candidateBlockerId: "task-A",
                blockedByMap: blockedByMap
            )
        )
        // Transitive 3-node cycle (C blocked by A when A -> B -> C) rejected
        XCTAssertTrue(
            TaskDependencyGraph.wouldCreateCycle(
                taskId: "task-C",
                candidateBlockerId: "task-A",
                blockedByMap: blockedByMap
            )
        )
        // Acyclic dependency (A also blocked by D, or D blocked by A) allowed
        XCTAssertFalse(
            TaskDependencyGraph.wouldCreateCycle(
                taskId: "task-A",
                candidateBlockerId: "task-D",
                blockedByMap: blockedByMap
            )
        )
        XCTAssertFalse(
            TaskDependencyGraph.wouldCreateCycle(
                taskId: "task-D",
                candidateBlockerId: "task-A",
                blockedByMap: blockedByMap
            )
        )

        // Auto-unlocking when blockers complete or are deleted
        let dependent = TaskItem(
            id: "dep-1",
            title: "Ship Release",
            blockedByTaskIds: ["blk-1", "blk-2"],
            createdAt: 100,
            updatedAt: 100
        )
        XCTAssertTrue(TaskDependencyGraph.isActivelyBlocked(dependent, openTaskIds: ["dep-1", "blk-1", "blk-2"]))
        XCTAssertEqual(TaskDependencyGraph.activeBlockerIds(for: dependent, openTaskIds: ["dep-1", "blk-1", "blk-2"]), ["blk-1", "blk-2"])
        XCTAssertTrue(TaskDependencyGraph.isActivelyBlocked(dependent, openTaskIds: ["dep-1", "blk-2"]))
        XCTAssertEqual(TaskDependencyGraph.activeBlockerIds(for: dependent, openTaskIds: ["dep-1", "blk-2"]), ["blk-2"])
        XCTAssertFalse(TaskDependencyGraph.isActivelyBlocked(dependent, openTaskIds: ["dep-1"]))
        XCTAssertTrue(TaskDependencyGraph.activeBlockerIds(for: dependent, openTaskIds: ["dep-1"]).isEmpty)
    }

    // MARK: - 29. v3.0.0-alpha.11 TaskViewSorter, TagListSorter, PerspectiveFilterEngine & TodayCapacityCalculator

    func testV30SortersPerspectiveFilterEngineAndTodayCapacityCalculator() {
        let dayMs: Int64 = 86_400_000
        let todayMs: Int64 = 1_795_046_400_000 // Midnight UTC anchor
        let tagWork = Tag(id: "cat-work", title: "Work", shortcut: "w", sortOrder: 20, createdAt: 1, updatedAt: 1)
        let tagQuick = Tag(id: "cat-quick", title: "Quick", shortcut: "q", sortOrder: 10, createdAt: 1, updatedAt: 1)
        let tagHome = Tag(id: "cat-home", title: "Home", shortcut: "h", sortOrder: 30, createdAt: 1, updatedAt: 1)

        // 1. TagListSorter: alpha, most_used, manual
        let tags = [tagWork, tagQuick, tagHome]
        XCTAssertEqual(TagListSorter.sort(tags, mode: "alpha").map(\.id), ["cat-home", "cat-quick", "cat-work"])
        XCTAssertEqual(TagListSorter.sort(tags, mode: "manual").map(\.id), ["cat-quick", "cat-work", "cat-home"])
        XCTAssertEqual(
            TagListSorter.sort(tags, mode: "most_used", usageCounts: ["cat-work": 5, "cat-home": 2, "cat-quick": 2]).map(\.id),
            ["cat-work", "cat-home", "cat-quick"]
        )

        // 2. TaskViewSorter: Pin-first + manual, deadline, duration, effort, alpha
        let t1 = TaskItem(
            id: "t1",
            title: "Charlie Deep",
            scheduleState: .today,
            whenDate: todayMs,
            deadline: todayMs + 3 * dayMs,
            durationMinutes: 90,
            areaId: "area-eng",
            sortOrder: 10,
            isPinned: false,
            effortLevel: TaskEffortLevel.deepFocus.rawValue,
            tags: [tagWork],
            createdAt: 1,
            updatedAt: 1
        )
        let t2 = TaskItem(
            id: "t2",
            title: "Alpha Quick",
            scheduleState: .today,
            whenDate: todayMs,
            deadline: todayMs + 1 * dayMs,
            durationMinutes: 15,
            areaId: "area-eng",
            sortOrder: 20,
            isPinned: false,
            effortLevel: TaskEffortLevel.quickWin.rawValue,
            tags: [tagWork, tagQuick],
            createdAt: 2,
            updatedAt: 2
        )
        let t3 = TaskItem(
            id: "t3",
            title: "Bravo Pinned Medium",
            scheduleState: .anytime,
            durationMinutes: 30,
            areaId: "area-eng",
            sortOrder: 30,
            isPinned: true,
            effortLevel: TaskEffortLevel.medium.rawValue,
            blockedByTaskIds: ["t2"],
            tags: [tagWork],
            createdAt: 3,
            updatedAt: 3
        )
        let t4 = TaskItem(
            id: "t4",
            title: "Delta Unestimated",
            scheduleState: .someday,
            sortOrder: 5,
            isPinned: false,
            tags: [tagHome],
            createdAt: 4,
            updatedAt: 4
        )
        let all = [t1, t2, t3, t4]

        XCTAssertEqual(TaskViewSorter.sort(all, mode: "manual").map(\.id), ["t3", "t4", "t1", "t2"])
        XCTAssertEqual(TaskViewSorter.sort(all, mode: "deadline").map(\.id), ["t3", "t2", "t1", "t4"])
        XCTAssertEqual(TaskViewSorter.sort(all, mode: "duration").map(\.id), ["t3", "t2", "t1", "t4"])
        XCTAssertEqual(TaskViewSorter.sort(all, mode: "effort").map(\.id), ["t3", "t2", "t1", "t4"])
        XCTAssertEqual(TaskViewSorter.sort(all, mode: "alpha").map(\.id), ["t3", "t2", "t1", "t4"])

        // Standalone duration pill vs timeblock pill
        XCTAssertEqual(t2.formattedStandaloneDurationPill, "15m")
        XCTAssertEqual(t1.formattedStandaloneDurationPill, "1h 30m")

        // 3. PerspectiveFilterEngine: category, effort, maxDuration, dueWindow, areaId, actionableOnly
        let quickActionablePerspective = SavedPerspective(
            id: "p-quick",
            title: "Quick Wins",
            categoryIds: ["cat-work"],
            effortLevel: nil,
            maxDurationMinutes: 30,
            dueWindow: "ANY",
            areaId: "area-eng",
            actionableOnly: true
        )
        // While t2 is open, t3 is actively blocked by t2 -> excluded when actionableOnly == true
        let filteredWhileBlocked = PerspectiveFilterEngine.filter(
            tasks: all,
            perspective: quickActionablePerspective,
            openTaskIds: ["t1", "t2", "t3", "t4"],
            todayEpochMillis: todayMs
        )
        XCTAssertEqual(filteredWhileBlocked.map(\.id), ["t2"])

        // Once t2 is completed (removed from openTaskIds), t3 auto-unlocks and matches!
        let filteredOnceUnlocked = PerspectiveFilterEngine.filter(
            tasks: all,
            perspective: quickActionablePerspective,
            openTaskIds: ["t1", "t3", "t4"],
            todayEpochMillis: todayMs
        )
        XCTAssertEqual(Set(filteredOnceUnlocked.map(\.id)), ["t2", "t3"])

        // 4. TodayCapacityCalculator: merges overlapping calendar intervals & subtracts planned task minutes
        let dayStart = todayMs + 9 * 3_600_000  // 09:00
        let dayEnd = todayMs + 17 * 3_600_000   // 17:00 (480 mins total window)
        let ev1 = CalendarEventItem(
            id: "ev1",
            eventIdentifier: "ev1",
            calendarId: "cal1",
            calendarDisplayName: "Work",
            title: "Standup",
            startMillis: todayMs + 10 * 3_600_000,         // 10:00
            endMillis: todayMs + 11 * 3_600_000,           // 11:00 (60m)
            isAllDay: false,
            colorHex: 0x2B79C2,
            startDateEpochDay: 20776,
            endDateInclusiveEpochDay: 20776
        )
        let evOverlap = CalendarEventItem(
            id: "ev2",
            eventIdentifier: "ev2",
            calendarId: "cal1",
            calendarDisplayName: "Work",
            title: "Design Sync",
            startMillis: todayMs + 10 * 3_600_000 + 1_800_000, // 10:30
            endMillis: todayMs + 11 * 3_600_000 + 1_800_000,   // 11:30 (merged 10:00-11:30 = 90m busy)
            isAllDay: false,
            colorHex: 0x2B79C2,
            startDateEpochDay: 20776,
            endDateInclusiveEpochDay: 20776
        )
        let allDayEv = CalendarEventItem(
            id: "ev3",
            eventIdentifier: "ev3",
            calendarId: "cal1",
            calendarDisplayName: "Holidays",
            title: "Conference",
            startMillis: todayMs,
            endMillis: todayMs + dayMs,
            isAllDay: true,
            colorHex: 0x2B79C2,
            startDateEpochDay: 20776,
            endDateInclusiveEpochDay: 20776
        )
        // t1 (90m) + t2 (15m) = 105m planned; 480m window - 90m busy - 105m planned = 285m free
        let cap = TodayCapacityCalculator.compute(
            tasks: [t1, t2],
            calendarEvents: [ev1, evOverlap, allDayEv],
            dayStartMillis: dayStart,
            dayEndMillis: dayEnd
        )
        XCTAssertEqual(cap.plannedMinutes, 105)
        XCTAssertEqual(cap.freeMinutes, 285)
        XCTAssertEqual(TodayCapacityCalculator.formatMinutes(cap.plannedMinutes), "1h 45m")
        XCTAssertEqual(TodayCapacityCalculator.formatMinutes(cap.freeMinutes), "4h 45m")
    }

    // MARK: - 30. v3.0.0-alpha.11 SQLite Persistence (isPinned, Effort, Duration, BlockedBy Cycle Guard, Phase startDate, MergeTags & Duplicate)

    func testV30DatabasePinEffortDurationDependenciesPhaseStartDateMergeTagsAndDuplicate() throws {
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let tagSrc = try db.createOrUpdateTag(title: "LegacyWIP", shortcut: "l")
        let tagDst = try db.createOrUpdateTag(title: "Active", shortcut: "a")
        let tagExtra = try db.createOrUpdateTag(title: "Urgent", shortcut: "u")

        // Clearing shortcut works via createOrUpdateTag
        let clearedTag = try db.createOrUpdateTag(id: tagDst.id, title: "Active", shortcut: nil)
        XCTAssertNil(clearedTag.shortcut)

        // Manual tag reorder swaps sort_order
        try db.moveTagOrder(tagExtra.id, moveUp: true)

        var proj = try db.createProject(title: "3.0 Milestone Project", notes: "")
        proj.workspaceId = "ws-team-alpha"
        proj.viewMode = "board"
        proj.tagIds = [tagSrc.id]
        try db.updateProject(proj)

        let phase = try db.createHeading(projectId: proj.id, title: "Phase A")
        let phaseStartMs: Int64 = 1_795_046_400_000
        let phaseEndMs: Int64 = phaseStartMs + 7 * 86_400_000
        try db.updateHeadingStartDate(phase.id, startDate: phaseStartMs)
        try db.updateHeadingEndDate(phase.id, endDate: phaseEndMs)

        let taskA = try db.createTask(
            title: "Design Schema",
            scheduleState: .today,
            projectId: proj.id,
            headingId: phase.id,
            tagIds: [tagSrc.id],
            checklistTitles: ["Add columns", "Write tests"]
        )
        let taskB = try db.createTask(
            title: "Ship macOS Build",
            scheduleState: .today,
            projectId: proj.id,
            headingId: phase.id,
            recurrenceRule: RecurrenceRule(mode: .fixedSchedule, interval: 1, unit: .days)
        )

        try db.setTasksPinned([taskA.id, taskB.id], isPinned: true)
        try db.setTaskEffortAndDuration(taskId: taskA.id, effortLevel: TaskEffortLevel.deepFocus.rawValue, durationMinutes: 60)
        try db.setTaskEffortAndDuration(taskId: taskB.id, effortLevel: TaskEffortLevel.quickWin.rawValue, durationMinutes: 15)

        // Dependency link: taskB blocked by taskA succeeds; reverse cycle taskA blocked by taskB is rejected!
        XCTAssertTrue(try db.setTaskBlockedBy(taskId: taskB.id, blockedByTaskIds: [taskA.id]))
        XCTAssertFalse(try db.setTaskBlockedBy(taskId: taskA.id, blockedByTaskIds: [taskB.id]))

        var snap = try db.loadWorkspaceSnapshot()
        let loadedPhase = try XCTUnwrap(snap.headingsByProject[proj.id]?.first(where: { $0.id == phase.id }))
        XCTAssertEqual(loadedPhase.startDate, phaseStartMs)
        XCTAssertEqual(loadedPhase.endDate, phaseEndMs)
        let loadedProj = try XCTUnwrap(snap.projects.first(where: { $0.id == proj.id }))
        XCTAssertEqual(loadedProj.workspaceId, "ws-team-alpha")
        XCTAssertEqual(loadedProj.viewMode, "board")

        let loadedA = try XCTUnwrap(snap.allOpenTasks.first(where: { $0.id == taskA.id }))
        let loadedB = try XCTUnwrap(snap.allOpenTasks.first(where: { $0.id == taskB.id }))
        XCTAssertTrue(loadedA.isPinned)
        XCTAssertEqual(loadedA.effortLevel, "DEEP_FOCUS")
        XCTAssertEqual(loadedA.durationMinutes, 60)
        XCTAssertEqual(loadedB.blockedByTaskIds, [taskA.id])

        // QuickFind search reads v2.5/v3.0 columns accurately
        let qf = try db.searchQuickFind(rawQuery: "Schema")
        XCTAssertEqual(qf.tasks.first?.effortLevel, "DEEP_FOCUS")
        XCTAssertEqual(qf.tasks.first?.isPinned, true)

        // Completing recurring taskB spawns an unpinned, unblocked next occurrence preserving effortLevel & durationMinutes
        let spawnedId = try XCTUnwrap(try db.setTaskStatus(taskId: taskB.id, status: .completed))
        snap = try db.loadWorkspaceSnapshot()
        let spawned = try XCTUnwrap(snap.allOpenTasks.first(where: { $0.id == spawnedId }))
        XCTAssertFalse(spawned.isPinned)
        XCTAssertTrue(spawned.blockedByTaskIds.isEmpty)
        XCTAssertEqual(spawned.effortLevel, "QUICK_WIN")
        XCTAssertEqual(spawned.durationMinutes, 15)

        // Merge tagSrc -> tagDst deduplicates refs and tombstones tagSrc
        XCTAssertTrue(try db.mergeTags(sourceTagId: tagSrc.id, into: tagDst.id))
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertFalse(snap.tags.contains(where: { $0.id == tagSrc.id }))
        XCTAssertEqual(snap.allOpenTasks.first(where: { $0.id == taskA.id })?.tagIds, [tagDst.id])
        XCTAssertEqual(snap.projects.first(where: { $0.id == proj.id })?.tagIds, [tagDst.id])

        // Batch tag update + duplicateTasks preserves effort, duration, pin, checklist, and tags (without copying stale blockedBy edges)
        try db.batchUpdateTasksTags([taskA.id, spawned.id], addTagIds: [tagExtra.id], removeTagIds: [tagDst.id])
        let copies = try db.duplicateTasks([taskA.id], copySuffix: "Copy")
        XCTAssertEqual(copies.count, 1)
        XCTAssertEqual(copies[0].title, "Design Schema (Copy)")
        XCTAssertTrue(copies[0].isPinned)
        XCTAssertEqual(copies[0].effortLevel, "DEEP_FOCUS")
        XCTAssertEqual(copies[0].durationMinutes, 60)
        XCTAssertEqual(copies[0].checklistItems.map(\.title), ["Add columns", "Write tests"])
        XCTAssertEqual(Set(copies[0].tagIds), [tagExtra.id])
    }

    // MARK: - 31. v3.0.0-alpha.11 .tbbackup Roundtrip, Legacy Sync Omission Preservation & 46 Localization Keys

    func testV30BackupRoundtripSyncOmissionPreservationAndLocalizationParity() throws {
        let sourceDb = try ThingsSQLiteDatabase(path: ":memory:")
        var proj = try sourceDb.createProject(title: "Backup 3.0 Project")
        proj.workspaceId = "ws-backup"
        proj.viewMode = "gantt"
        try sourceDb.updateProject(proj)

        let phase = try sourceDb.createHeading(projectId: proj.id, title: "Phase 1")
        try sourceDb.updateHeadingStartDate(phase.id, startDate: 1_795_000_000_000)
        try sourceDb.updateHeadingEndDate(phase.id, endDate: 1_795_600_000_000)

        let blocker = try sourceDb.createTask(title: "Upstream Task", scheduleState: .today, projectId: proj.id, headingId: phase.id)
        var blocked = try sourceDb.createTask(title: "Downstream Task", scheduleState: .today, projectId: proj.id, headingId: phase.id)
        blocked.isPinned = true
        blocked.effortLevel = TaskEffortLevel.medium.rawValue
        blocked.durationMinutes = 45
        blocked.blockedByTaskIds = [blocker.id]
        blocked.workspaceId = "ws-backup"
        blocked.assigneeId = "user-42"
        try sourceDb.updateTask(blocked)

        let savedPerspective = SavedPerspective(
            id: "persp-1",
            title: "Quick Focus",
            categoryIds: [],
            effortLevel: TaskEffortLevel.quickWin.rawValue,
            maxDurationMinutes: 15,
            dueWindow: "TODAY",
            areaId: nil,
            actionableOnly: true,
            sortOrder: 100.0
        )
        var sourcePrefs = AppPreferences()
        sourcePrefs.tagSortMode = "most_used"
        sourcePrefs.viewSortMode = "effort"
        sourcePrefs.savedPerspectives = [savedPerspective]

        let exportedPayload = try sourceDb.buildBackupPayload(preferences: sourcePrefs)
        let archiveBytes = try ThingsBackupCodec.encodeBackup(payload: exportedPayload, password: nil)
        let decodedPayload = try ThingsBackupCodec.decodeBackup(rawBytes: archiveBytes, password: nil)

        // BackupPreferencesDto is non-optional on ThingsbookBackupPayload
        XCTAssertEqual(decodedPayload.preferences.tagSortMode, "most_used")
        XCTAssertEqual(decodedPayload.preferences.viewSortMode, "effort")
        let decodedPerspectives = try JSONDecoder().decode(
            [SavedPerspective].self,
            from: Data((decodedPayload.preferences.savedPerspectivesJson ?? "[]").utf8)
        )
        XCTAssertEqual(decodedPerspectives, [savedPerspective])

        let targetDb = try ThingsSQLiteDatabase(path: ":memory:")
        try targetDb.restoreBackupPayload(decodedPayload)
        var restoredSnap = try targetDb.loadWorkspaceSnapshot()
        let restoredProj = try XCTUnwrap(restoredSnap.projects.first(where: { $0.id == proj.id }))
        XCTAssertEqual(restoredProj.workspaceId, "ws-backup")
        XCTAssertEqual(restoredProj.viewMode, "gantt")
        let restoredPhase = try XCTUnwrap(restoredSnap.headingsByProject[proj.id]?.first(where: { $0.id == phase.id }))
        XCTAssertEqual(restoredPhase.startDate, 1_795_000_000_000)
        XCTAssertEqual(restoredPhase.endDate, 1_795_600_000_000)
        let restoredBlocked = try XCTUnwrap(restoredSnap.allOpenTasks.first(where: { $0.id == blocked.id }))
        XCTAssertTrue(restoredBlocked.isPinned)
        XCTAssertEqual(restoredBlocked.effortLevel, "MEDIUM")
        XCTAssertEqual(restoredBlocked.durationMinutes, 45)
        XCTAssertEqual(restoredBlocked.blockedByTaskIds, [blocker.id])
        XCTAssertEqual(restoredBlocked.workspaceId, "ws-backup")
        XCTAssertEqual(restoredBlocked.assigneeId, "user-42")

        // Older v2.1/v2.5 remote sync payload omitting 3.0 fields must NOT wipe local effort/blockedBy/pin/startDate!
        let legacyTaskWire: [String: Any] = [
            "id": blocked.id,
            "title": "Downstream Task (Renamed on v2.1)",
            "status": "OPEN",
            "scheduleState": "TODAY",
            "projectId": proj.id,
            "headingId": phase.id,
            "sortOrder": restoredBlocked.sortOrder,
            "createdAt": restoredBlocked.createdAt,
            "updatedAt": restoredBlocked.updatedAt + 10_000,
            "isDeleted": false
        ]
        let taskHints = try XCTUnwrap(FirebaseCloudSyncService.parseTaskWithHints(legacyTaskWire))
        XCTAssertTrue(taskHints.omittedPin)
        XCTAssertTrue(taskHints.omittedV30Fields)
        _ = try targetDb.applyRemoteDeltas(
            RemoteDeltaBatch(
                tasks: [taskHints.task],
                taskOmittedTimeblockTaskIds: taskHints.omittedTimeblock ? [taskHints.task.id] : [],
                taskOmittedPinTaskIds: taskHints.omittedPin ? [taskHints.task.id] : [],
                taskOmittedV30TaskIds: taskHints.omittedV30Fields ? [taskHints.task.id] : []
            )
        )
        restoredSnap = try targetDb.loadWorkspaceSnapshot()
        let preservedAfterLegacySync = try XCTUnwrap(restoredSnap.allOpenTasks.first(where: { $0.id == blocked.id }))
        XCTAssertEqual(preservedAfterLegacySync.title, "Downstream Task (Renamed on v2.1)")
        XCTAssertTrue(preservedAfterLegacySync.isPinned)
        XCTAssertEqual(preservedAfterLegacySync.effortLevel, "MEDIUM")
        XCTAssertEqual(preservedAfterLegacySync.durationMinutes, 45)
        XCTAssertEqual(preservedAfterLegacySync.blockedByTaskIds, [blocker.id])

        // Verify all 46 v25_* and v30_* localization keys across all 5 languages
        let plainKeys = [
            "v25_pin_task", "v25_unpin_task", "v25_pinned_badge",
            "v25_tags_search_placeholder", "v25_tags_assign_search_placeholder", "v25_tags_no_matches",
            "v25_tags_sort_alpha", "v25_tags_sort_most_used", "v25_tags_sort_manual",
            "v25_tags_merge_action",
            "v25_view_sort_label", "v25_view_sort_manual", "v25_view_sort_deadline", "v25_view_sort_duration", "v25_view_sort_alpha",
            "v25_copy_suffix",
            "v25_batch_pin", "v25_batch_tags", "v25_batch_duplicate", "v25_batch_someday",
            "v30_duration_label", "v30_duration_clear",
            "v30_effort_label", "v30_effort_none", "v30_effort_quick_win", "v30_effort_medium", "v30_effort_deep_focus",
            "v30_view_sort_effort",
            "v30_blocked_by_label", "v30_blocked_by_action", "v30_blocked_by_dialog_title",
            "v30_blocked_by_search_placeholder", "v30_blocked_by_cycle_warning", "v30_blocked_by_none_available",
            "v30_perspectives_section", "v30_perspective_new", "v30_perspective_edit",
            "v30_perspective_title_placeholder", "v30_perspective_max_duration", "v30_perspective_any_duration",
            "v30_perspective_actionable_only", "v30_perspective_save", "v30_perspective_delete"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            let langKeys = ThingsStrings.shared.keys(for: lang.rawValue)
            for key in plainKeys {
                XCTAssertTrue(langKeys.contains(key), "Missing \(key) in \(lang.rawValue)")
                XCTAssertFalse(ThingsStrings.shared.text(key, lang: lang).isEmpty)
            }
            XCTAssertTrue(ThingsStrings.shared.format("v25_tags_merge_target_prompt", lang: lang, "WIP").contains("WIP"))
            XCTAssertTrue(ThingsStrings.shared.format("v25_tags_merged_toast", lang: lang, "WIP", "Active").contains("Active"))
            XCTAssertTrue(ThingsStrings.shared.format("v30_today_capacity_summary", lang: lang, "2h", "3h").contains("2h"))
            XCTAssertTrue(ThingsStrings.shared.format("v30_today_planned_only", lang: lang, "2h").contains("2h"))
            XCTAssertTrue(ThingsStrings.shared.format("v30_blocked_by_badge", lang: lang, "API").contains("API"))
            XCTAssertTrue(ThingsStrings.shared.format("v30_blocked_by_count_badge", lang: lang, 3).contains("3"))
        }
    }

    // MARK: - 32. v3.0.0-alpha.12 Milestone 3: ProjectRoadmapEngine Layout, Dependencies & Date Shifting

    func testMilestone3RoadmapLayoutAndDependencies() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let todayDate = cal.date(from: DateComponents(year: 2026, month: 9, day: 10))!
        let todayMs = DateEpochConverter.startOfDayMillis(for: todayDate, calendar: cal)
        let todayDay = DateEpochConverter.epochDay(forMillis: todayMs, calendar: cal)
        let dayMs: Int64 = 86_400_000

        // 1. ProjectViewMode normalization
        XCTAssertEqual(ProjectViewMode.normalized(nil), .list)
        XCTAssertEqual(ProjectViewMode.normalized(""), .list)
        XCTAssertEqual(ProjectViewMode.normalized("list"), .list)
        XCTAssertEqual(ProjectViewMode.normalized("BOARD"), .board)
        XCTAssertEqual(ProjectViewMode.normalized("roadmap"), .roadmap)
        XCTAssertEqual(ProjectViewMode.normalized("gantt"), .roadmap)

        // 2. Duration day-span formula: max(1, ((durationMinutes ?? 30) + 479) / 480)
        XCTAssertEqual(ProjectRoadmapEngine.durationSpanDays(for: nil), 1)
        XCTAssertEqual(ProjectRoadmapEngine.durationSpanDays(for: 30), 1)
        XCTAssertEqual(ProjectRoadmapEngine.durationSpanDays(for: 480), 1)
        XCTAssertEqual(ProjectRoadmapEngine.durationSpanDays(for: 481), 2)
        XCTAssertEqual(ProjectRoadmapEngine.durationSpanDays(for: 960), 2)
        XCTAssertEqual(ProjectRoadmapEngine.durationSpanDays(for: 1440), 3)

        let project = Project(
            id: "proj-roadmap-1",
            title: "macOS 3.0 Launch",
            viewMode: "roadmap",
            createdAt: todayMs,
            updatedAt: todayMs
        )
        XCTAssertEqual(project.resolvedViewMode, .roadmap)

        // Phase 1 has explicit dates (with inverted end < start to verify clamping endDay >= startDay)
        let phaseExplicit = Heading(
            id: "phase-explicit",
            projectId: project.id,
            title: "Phase 1: Architecture",
            sortOrder: 100,
            startDate: todayMs + 2 * dayMs,
            endDate: todayMs, // Earlier than start -> clamped to startDay
            createdAt: todayMs,
            updatedAt: todayMs
        )
        // Phase 2 has no explicit dates -> inferred from its scheduled child tasks
        let phaseInferred = Heading(
            id: "phase-inferred",
            projectId: project.id,
            title: "Phase 2: QA & Release",
            sortOrder: 200,
            startDate: nil,
            endDate: nil,
            createdAt: todayMs,
            updatedAt: todayMs
        )

        let generalTask = TaskItem(
            id: "task-general",
            title: "Kickoff Spec",
            status: .open,
            scheduleState: .today,
            whenDate: todayMs,
            durationMinutes: 960, // 2 days: [todayDay, todayDay + 1]
            projectId: project.id,
            headingId: nil,
            sortOrder: 10,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let blockerOpen = TaskItem(
            id: "task-blocker-open",
            title: "Build Gantt Engine",
            status: .open,
            scheduleState: .upcoming,
            whenDate: todayMs + 2 * dayMs,
            deadline: todayMs + 5 * dayMs, // [todayDay + 2, todayDay + 5]
            projectId: project.id,
            headingId: phaseExplicit.id,
            sortOrder: 20,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let blockerCompleted = TaskItem(
            id: "task-blocker-done",
            title: "Sync Strings",
            status: .completed,
            scheduleState: .today,
            whenDate: todayMs - 1 * dayMs,
            completedAt: todayMs,
            projectId: project.id,
            headingId: phaseExplicit.id,
            sortOrder: 15,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let dependentTask = TaskItem(
            id: "task-dependent",
            title: "Export Vector PDF",
            status: .open,
            scheduleState: .upcoming,
            whenDate: todayMs + 6 * dayMs,
            deadline: todayMs + 9 * dayMs,
            projectId: project.id,
            headingId: phaseInferred.id,
            sortOrder: 30,
            blockedByTaskIds: [blockerOpen.id, blockerCompleted.id],
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let unscheduledTask = TaskItem(
            id: "task-unscheduled",
            title: "Write Postmortem",
            status: .open,
            scheduleState: .anytime,
            whenDate: nil,
            deadline: nil,
            projectId: project.id,
            headingId: phaseInferred.id,
            sortOrder: 40,
            createdAt: todayMs,
            updatedAt: todayMs
        )

        let layout = ProjectRoadmapEngine.buildLayout(
            project: project,
            headings: [phaseExplicit, phaseInferred],
            tasks: [generalTask, blockerOpen, blockerCompleted, dependentTask, unscheduledTask],
            todayMillis: todayMs,
            calendar: cal
        )

        // Window covers todayDay - 3 through maxScheduledDay + 7 and totalDays >= 21
        XCTAssertLessThanOrEqual(layout.startEpochDay, todayDay - 3)
        XCTAssertGreaterThanOrEqual(layout.endEpochDay, (todayDay + 9) + 7)
        XCTAssertGreaterThanOrEqual(layout.totalDays, 21)

        // Explicit vs inferred phase bars
        let explicitBar = try? XCTUnwrap(layout.phaseBar(for: phaseExplicit.id))
        XCTAssertEqual(explicitBar?.hasExplicitDates, true)
        XCTAssertEqual(explicitBar?.startEpochDay, todayDay + 2)
        XCTAssertEqual(explicitBar?.endEpochDay, todayDay + 2)

        let inferredBar = try? XCTUnwrap(layout.phaseBar(for: phaseInferred.id))
        XCTAssertEqual(inferredBar?.hasExplicitDates, false)
        XCTAssertEqual(inferredBar?.startEpochDay, todayDay + 6)
        XCTAssertEqual(inferredBar?.endEpochDay, todayDay + 9)

        // General task bar spans 2 days from 960m duration
        XCTAssertEqual(layout.generalTaskBars.count, 1)
        XCTAssertEqual(layout.generalTaskBars.first?.startEpochDay, todayDay)
        XCTAssertEqual(layout.generalTaskBars.first?.endEpochDay, todayDay + 1)
        XCTAssertEqual(layout.generalTaskBars.first?.spanDays, 2)

        // Unscheduled partition
        XCTAssertEqual(layout.unscheduledTasks.map(\.id), [unscheduledTask.id])

        // Dependency edges: open blocker -> isActivelyBlocking == true; completed blocker -> isActivelyBlocking == false
        XCTAssertEqual(layout.dependencyEdges.count, 2)
        let activeEdge = layout.dependencyEdges.first { $0.fromTaskId == blockerOpen.id && $0.toTaskId == dependentTask.id }
        let doneEdge = layout.dependencyEdges.first { $0.fromTaskId == blockerCompleted.id && $0.toTaskId == dependentTask.id }
        XCTAssertEqual(activeEdge?.isActivelyBlocking, true)
        XCTAssertEqual(doneEdge?.isActivelyBlocking, false)

        // DST-safe shiftHeadingDates & shiftTaskDates
        let validPhase = Heading(
            id: "phase-shift",
            projectId: project.id,
            title: "Shiftable",
            sortOrder: 100,
            startDate: todayMs,
            endDate: todayMs + 4 * dayMs,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let shiftedPhase = ProjectRoadmapEngine.shiftHeadingDates(heading: validPhase, deltaDays: 3, calendar: cal)
        XCTAssertEqual(shiftedPhase.startDate, todayMs + 3 * dayMs)
        XCTAssertEqual(shiftedPhase.endDate, todayMs + 7 * dayMs)

        // Shift scheduled task back into Today vs forward into Upcoming
        let shiftedToToday = ProjectRoadmapEngine.shiftTaskDates(task: blockerOpen, deltaDays: -2, todayMillis: todayMs, calendar: cal)
        XCTAssertEqual(shiftedToToday.scheduleState, .today)
        XCTAssertEqual(shiftedToToday.whenDate, todayMs)
        XCTAssertEqual(shiftedToToday.deadline, todayMs + 3 * dayMs)

        // Shift dateless task sets whenDate = today + deltaDays
        let shiftedDateless = ProjectRoadmapEngine.shiftTaskDates(task: unscheduledTask, deltaDays: 5, todayMillis: todayMs, calendar: cal)
        XCTAssertEqual(shiftedDateless.scheduleState, .upcoming)
        XCTAssertEqual(shiftedDateless.whenDate, todayMs + 5 * dayMs)
        XCTAssertNil(shiftedDateless.deadline)
    }

    // MARK: - 33. v3.0.0-alpha.12 Milestone 3: Cross-Container & Phase Drag-and-Drop Moves + Undo

    func testMilestone3CrossContainerAndPhaseMoves() throws {
        // 1. Multi-task drag payload codec
        let singlePayload = TaskDragPayloadCodec.encode(
            primaryTaskId: "t2",
            selectedSet: ["t2"],
            orderedSelectedIds: ["t2"]
        )
        XCTAssertEqual(singlePayload, "t2")

        let multiPayload = TaskDragPayloadCodec.encode(
            primaryTaskId: "t2",
            selectedSet: ["t1", "t2", "t3"],
            orderedSelectedIds: ["t1", "t2", "t3"]
        )
        XCTAssertEqual(multiPayload, "t1\nt2\nt3")
        XCTAssertEqual(
            TaskDragPayloadCodec.decode([" t1 \n t2\n", "t2\nt3 ", ""]),
            ["t1", "t2", "t3"]
        )

        // 2. Database viewMode & heading date range persistence
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let area = try db.createArea(title: "Product")
        let projA = try db.createProject(title: "Alpha Project", areaId: area.id)
        let projB = try db.createProject(title: "Beta Project", areaId: nil)

        try db.setProjectViewMode(projectId: projA.id, viewMode: "roadmap")
        var snap = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(snap.projects.first(where: { $0.id == projA.id })?.viewMode, "roadmap")

        let phase1 = try db.createHeading(projectId: projA.id, title: "Design")
        let phase2 = try db.createHeading(projectId: projA.id, title: "Build")
        let todayDate = Date()
        let todayMs = DateEpochConverter.startOfDayMillis(for: todayDate)
        let futureMs = todayMs + 5 * 86_400_000
        try db.updateHeadingDates(phase1.id, startDate: futureMs, endDate: todayMs) // Clamps endDate >= startDate
        snap = try db.loadWorkspaceSnapshot()
        let updatedPhase1 = try XCTUnwrap(snap.headingsByProject[projA.id]?.first(where: { $0.id == phase1.id }))
        XCTAssertEqual(updatedPhase1.startDate, futureMs)
        XCTAssertEqual(updatedPhase1.endDate, futureMs)

        // 3. Multi-task cross-container moves across Inbox, Today, Upcoming, Anytime, Someday, Area, Project, Logbook, Trash
        let t1 = try db.createTask(title: "Move 1", scheduleState: .inbox, sortOrder: 100)
        let t2 = try db.createTask(title: "Move 2", scheduleState: .inbox, sortOrder: 200)
        let t3 = try db.createTask(title: "Move 3", scheduleState: .upcoming, whenDate: futureMs, sortOrder: 300)

        // Move [t1, t2] into Area -> promotes .inbox -> .anytime, sets areaId
        let areaMove = try db.moveTasksToDestination([t1.id, t2.id], destination: .area(area.id), referenceDate: todayDate)
        XCTAssertEqual(areaMove.snapshots.count, 2)
        snap = try db.loadWorkspaceSnapshot()
        for id in [t1.id, t2.id] {
            let loaded = try XCTUnwrap(snap.allOpenTasks.first(where: { $0.id == id }))
            XCTAssertEqual(loaded.areaId, area.id)
            XCTAssertNil(loaded.projectId)
            XCTAssertEqual(loaded.scheduleState, .anytime)
        }

        // Undo restores [t1, t2] back to Inbox with nil areaId
        try db.restoreTaskPlacements(areaMove.snapshots)
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertEqual(snap.inboxTasks.map(\.id), [t1.id, t2.id])

        // Move [t1, t3] to Upcoming -> t1 gets tomorrowStartMillis while t3 preserves its existing futureMs!
        _ = try db.moveTasksToDestination([t1.id, t3.id], destination: .smartView(.upcoming), referenceDate: todayDate)
        snap = try db.loadWorkspaceSnapshot()
        let upcomingT3 = try XCTUnwrap(snap.allOpenTasks.first(where: { $0.id == t3.id }))
        XCTAssertEqual(upcomingT3.whenDate, futureMs)

        // Soft-delete via .smartView(.trash), then drop trashed task onto .smartView(.today) -> auto-restores & schedules Today!
        _ = try db.moveTasksToDestination([t2.id], destination: .smartView(.trash), referenceDate: todayDate)
        XCTAssertTrue(try db.loadWorkspaceSnapshot().trashedTasks.contains(where: { $0.id == t2.id }))

        _ = try db.moveTasksToDestination([t2.id], destination: .smartView(.today), referenceDate: todayDate)
        snap = try db.loadWorkspaceSnapshot()
        XCTAssertFalse(snap.trashedTasks.contains(where: { $0.id == t2.id }))
        XCTAssertEqual(snap.allOpenTasks.first(where: { $0.id == t2.id })?.scheduleState, .today)

        // 4. Move multiple tasks across containers into a specific Project Phase preserving sequential order!
        let anchorInPhase2 = try db.createTask(
            title: "Existing Anchor",
            scheduleState: .anytime,
            projectId: projA.id,
            headingId: phase2.id,
            areaId: area.id,
            sortOrder: 1000
        )
        _ = try db.moveTasksIntoProjectSection(
            [t1.id, t2.id, t3.id],
            projectId: projA.id,
            headingId: phase2.id,
            beforeTaskId: anchorInPhase2.id
        )
        snap = try db.loadWorkspaceSnapshot()
        let phase2Tasks = snap.allOpenTasks
            .filter { $0.projectId == projA.id && $0.headingId == phase2.id }
            .sorted { $0.sortOrder < $1.sortOrder }
        XCTAssertEqual(phase2Tasks.map(\.id), [t1.id, t2.id, t3.id, anchorInPhase2.id])
        XCTAssertTrue(phase2Tasks.allSatisfy { $0.areaId == area.id })

        // Move [t2, t3] from Phase 2 to General (headingId == nil) in projB
        _ = try db.moveTasksIntoProjectSection(
            [t2.id, t3.id],
            projectId: projB.id,
            headingId: nil,
            beforeTaskId: nil
        )
        snap = try db.loadWorkspaceSnapshot()
        let projBGeneral = snap.allOpenTasks
            .filter { $0.projectId == projB.id && $0.headingId == nil }
            .sorted { $0.sortOrder < $1.sortOrder }
        XCTAssertEqual(projBGeneral.map(\.id), [t2.id, t3.id])
        XCTAssertTrue(projBGeneral.allSatisfy { $0.areaId == nil })

        // Complete via .smartView(.logbook)
        _ = try db.moveTasksToDestination([t1.id], destination: .smartView(.logbook), referenceDate: todayDate)
        XCTAssertTrue(try db.loadWorkspaceSnapshot().logbookTasks.contains(where: { $0.id == t1.id }))
    }

    // MARK: - 34. v3.0.0-alpha.12 Milestone 3: Vector PDF & 2x Retina PNG Export + 5-Language Keys

    @MainActor
    func testMilestone3RoadmapPdfAndPngExportData() throws {
        let todayMs: Int64 = 1_795_046_400_000
        let dayMs: Int64 = 86_400_000
        let project = Project(
            id: "proj-export-1",
            title: "Thingsbook 3.0 Roadmap",
            viewMode: "roadmap",
            totalTaskCount: 2,
            completedTaskCount: 0,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let phase = Heading(
            id: "hdg-export-1",
            projectId: project.id,
            title: "Milestone 3",
            sortOrder: 100,
            startDate: todayMs,
            endDate: todayMs + 10 * dayMs,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let t1 = TaskItem(
            id: "exp-t1",
            title: "Kanban Workspace",
            scheduleState: .today,
            whenDate: todayMs,
            deadline: todayMs + 3 * dayMs,
            projectId: project.id,
            headingId: phase.id,
            sortOrder: 10,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let t2 = TaskItem(
            id: "exp-t2",
            title: "Calm Bento Gantt",
            scheduleState: .upcoming,
            whenDate: todayMs + 4 * dayMs,
            deadline: todayMs + 9 * dayMs,
            projectId: project.id,
            headingId: phase.id,
            sortOrder: 20,
            blockedByTaskIds: [t1.id],
            createdAt: todayMs,
            updatedAt: todayMs
        )

        let layout = ProjectRoadmapEngine.buildLayout(
            project: project,
            headings: [phase],
            tasks: [t1, t2],
            todayMillis: todayMs
        )

        let pdfData = try XCTUnwrap(
            ProjectRoadmapExporter.exportRoadmapPDFData(
                project: project,
                headings: [phase],
                layout: layout
            )
        )
        XCTAssertTrue(pdfData.starts(with: Data("%PDF-".utf8)), "Exported Roadmap PDF must begin with %PDF-")
        XCTAssertGreaterThan(pdfData.count, 256)

        let pngData = try XCTUnwrap(
            ProjectRoadmapExporter.exportRoadmapPNGData(
                project: project,
                headings: [phase],
                layout: layout,
                scale: 2.0
            )
        )
        let pngMagic: [UInt8] = [0x89, 0x50, 0x4E, 0x47]
        XCTAssertTrue(pngData.starts(with: pngMagic), "Exported Roadmap PNG must begin with \\x89PNG")
        XCTAssertGreaterThan(pngData.count, 256)

        // Verify all Milestone 3 localization keys in EN, DE, FR, IT, ES
        let m3PlainKeys = [
            "v30_project_mode_list",
            "v30_project_mode_board",
            "v30_project_mode_roadmap",
            "v30_board_ungrouped_column",
            "v30_board_add_phase",
            "v30_phase_set_start_date",
            "v30_phase_clear_start_date",
            "phase_set_end_date",
            "phase_clear_end_date",
            "v30_roadmap_export",
            "v30_roadmap_export_pdf",
            "v30_roadmap_export_png",
            "v30_roadmap_jump_today",
            "v30_roadmap_unscheduled_section",
            "v30_roadmap_unscheduled_hint",
            "v30_roadmap_shift_earlier",
            "v30_roadmap_shift_later"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in m3PlainKeys {
                let text = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertFalse(text.isEmpty, "Missing \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(text, key, "Unresolved \(key) in \(lang.rawValue)")
            }
            XCTAssertTrue(ThingsStrings.shared.format("v30_phase_starts_on", lang: lang, "Sep 10").contains("Sep 10"))
            XCTAssertTrue(ThingsStrings.shared.format("phase_ends_on", lang: lang, "Sep 20").contains("Sep 20"))
            XCTAssertTrue(ThingsStrings.shared.format("v30_drag_multi_count", lang: lang, 4).contains("4"))
            XCTAssertTrue(ThingsStrings.shared.format("v30_drop_moved_feedback", lang: lang, 4).contains("4"))
        }
    }

    // MARK: - 35. v3.0.0-alpha.13: 1-Month Roadmap Horizon Cap, Filtered Export & A4 Portrait/Landscape PDF Bounds

    @MainActor
    func testAlpha13RoadmapHorizonCapFilteredExportAndA4PdfOrientations() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!

        func millis(_ year: Int, _ month: Int, _ day: Int) -> Int64 {
            let d = cal.date(from: DateComponents(year: year, month: month, day: day))!
            return DateEpochConverter.startOfDayMillis(for: d, calendar: cal)
        }
        func epochDay(_ year: Int, _ month: Int, _ day: Int) -> Int {
            DateEpochConverter.epochDay(forMillis: millis(year, month, day), calendar: cal)
        }

        // 1. Exact 1-month horizon cap past last task due date (ignoring distant project.deadline & distant heading.endDate!)
        let todayMs = millis(2026, 11, 20)
        let project = Project(
            id: "proj-alpha13",
            title: "Cloud Rollout 2027",
            whenDate: millis(2026, 11, 13),
            deadline: millis(2027, 4, 30), // Distant project deadline (April 30, 2027) must NOT stretch roadmap!
            viewMode: "roadmap",
            totalTaskCount: 5,
            completedTaskCount: 1,
            createdAt: todayMs,
            updatedAt: todayMs
        )

        let phase1 = Heading(
            id: "ph-1",
            projectId: project.id,
            title: "Konzeption & Architekturplanung",
            sortOrder: 100,
            startDate: millis(2026, 11, 13),
            endDate: millis(2026, 11, 28),
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let phase2 = Heading(
            id: "ph-2",
            projectId: project.id,
            title: "Technische Umsetzung & Systemintegration",
            sortOrder: 200,
            startDate: millis(2026, 11, 29),
            endDate: millis(2026, 12, 18),
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let phase4Distant = Heading(
            id: "ph-4",
            projectId: project.id,
            title: "Betriebsübergabe & Langzeitbegleitung",
            sortOrder: 400,
            startDate: millis(2026, 12, 15),
            endDate: millis(2027, 2, 15), // Ends Feb 15, 2027 -> must be capped at Jan 18, 2027!
            createdAt: todayMs,
            updatedAt: todayMs
        )

        let generalTask = TaskItem(
            id: "t-gen",
            title: "Stakeholder Kickoff",
            scheduleState: .today,
            whenDate: millis(2026, 11, 14),
            projectId: project.id,
            headingId: nil,
            sortOrder: 5,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let unrelatedLooseTask = TaskItem(
            id: "t-unrelated-inbox",
            title: "Personal Grocery Run",
            scheduleState: .upcoming,
            whenDate: millis(2027, 6, 1),
            deadline: millis(2027, 6, 10),
            projectId: nil, // Must be excluded because projectId != project.id!
            headingId: nil,
            sortOrder: 6,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let taskPhase1 = TaskItem(
            id: "t-p1",
            title: "API Spec Sign-Off",
            scheduleState: .upcoming,
            whenDate: millis(2026, 11, 25), // Inverted whenDate > deadline test -> [min, max]
            deadline: millis(2026, 11, 22),
            projectId: project.id,
            headingId: phase1.id,
            sortOrder: 10,
            blockedByTaskIds: [generalTask.id],
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let lastScheduledTask = TaskItem(
            id: "t-p2-last",
            title: "Production Cutover",
            scheduleState: .upcoming,
            whenDate: millis(2026, 12, 10),
            deadline: millis(2026, 12, 18), // Last task due date = 18.12.2026
            projectId: project.id,
            headingId: phase2.id,
            sortOrder: 20,
            blockedByTaskIds: [taskPhase1.id],
            createdAt: todayMs,
            updatedAt: todayMs
        )

        let layout = ProjectRoadmapEngine.buildLayout(
            project: project,
            headings: [phase1, phase2, phase4Distant],
            tasks: [generalTask, unrelatedLooseTask, taskPhase1, lastScheduledTask],
            todayMillis: todayMs,
            calendar: cal
        )

        let lastTaskEndDay = epochDay(2026, 12, 18)
        let expectedCapDay = ProjectRoadmapEngine.addOneMonth(toEpochDay: lastTaskEndDay, calendar: cal)
        XCTAssertEqual(expectedCapDay, epochDay(2027, 1, 18))
        XCTAssertEqual(layout.startEpochDay, epochDay(2026, 11, 13))
        XCTAssertEqual(layout.endEpochDay, epochDay(2027, 1, 18), "Horizon must cap strictly 1 month after last task due date (18.01.2027)")
        XCTAssertLessThanOrEqual(layout.endEpochDay, expectedCapDay)
        XCTAssertFalse(layout.allTaskBars.contains { $0.id == unrelatedLooseTask.id })

        // Verify inverted whenDate/deadline produced [min(wDay, dDay), max(wDay, dDay)]
        let p1Bar = try XCTUnwrap(layout.allTaskBars.first { $0.id == taskPhase1.id })
        XCTAssertEqual(p1Bar.startEpochDay, epochDay(2026, 11, 22))
        XCTAssertEqual(p1Bar.endEpochDay, epochDay(2026, 11, 25))

        // Verify empty-tasks project caps at 1 month from today even if a phase ends months later
        let tasklessLayout = ProjectRoadmapEngine.buildLayout(
            project: project,
            headings: [phase4Distant],
            tasks: [],
            todayMillis: todayMs,
            calendar: cal
        )
        XCTAssertEqual(tasklessLayout.endEpochDay, ProjectRoadmapEngine.addOneMonth(toEpochDay: epochDay(2026, 11, 20), calendar: cal))

        // 2. filteredLayoutForExport: phase selection, custom timeline clamping & dependency edge filtering
        let customStart = epochDay(2026, 11, 24)
        let customEnd = epochDay(2026, 12, 15)
        let exportOptions = RoadmapPDFExportOptions(
            selectedSectionIds: [phase1.id, phase2.id], // Exclude "__general__" and phase4Distant
            startEpochDay: customStart,
            endEpochDay: customEnd,
            orientation: .portrait
        )
        let filtered = ProjectRoadmapEngine.filteredLayoutForExport(
            layout: layout,
            headings: [phase1, phase2, phase4Distant],
            options: exportOptions
        )

        XCTAssertEqual(filtered.startEpochDay, customStart)
        XCTAssertEqual(filtered.endEpochDay, customEnd)
        XCTAssertTrue(filtered.generalTaskBars.isEmpty, "Deselected __general__ section must be excluded")
        XCTAssertNil(filtered.phaseBar(for: phase4Distant.id), "Deselected Phase 4 must be excluded")
        XCTAssertNotNil(filtered.phaseBar(for: phase1.id))
        XCTAssertNotNil(filtered.phaseBar(for: phase2.id))

        // Clamping checks: taskPhase1 [Nov 22..25] -> [Nov 24..25]; lastScheduledTask [Dec 10..18] -> [Dec 10..15]
        let clampedP1Task = try XCTUnwrap(filtered.allTaskBars.first { $0.id == taskPhase1.id })
        XCTAssertEqual(clampedP1Task.startEpochDay, customStart)
        XCTAssertEqual(clampedP1Task.endEpochDay, epochDay(2026, 11, 25))

        let clampedLastTask = try XCTUnwrap(filtered.allTaskBars.first { $0.id == lastScheduledTask.id })
        XCTAssertEqual(clampedLastTask.startEpochDay, epochDay(2026, 12, 10))
        XCTAssertEqual(clampedLastTask.endEpochDay, customEnd)

        // Dependency edge filtering: edge t-gen -> t-p1 dropped (t-gen excluded), edge t-p1 -> t-p2-last kept!
        XCTAssertEqual(filtered.dependencyEdges.map(\.id), ["\(taskPhase1.id)->\(lastScheduledTask.id)"])

        // 3. True A4 Portrait (595 × 842, width < height) vs Landscape (842 × 595, width > height) PDF bounds via PDFKit
        XCTAssertEqual(RoadmapPDFOrientation.landscape.pageSize, CGSize(width: 842, height: 595))
        XCTAssertEqual(RoadmapPDFOrientation.portrait.pageSize, CGSize(width: 595, height: 842))

        let portraitData = try XCTUnwrap(
            ProjectRoadmapExporter.exportRoadmapPDFData(
                project: project,
                headings: [phase1, phase2, phase4Distant],
                layout: layout,
                options: exportOptions
            )
        )
        XCTAssertTrue(portraitData.starts(with: Data("%PDF-".utf8)))
        let portraitDoc = try XCTUnwrap(PDFDocument(data: portraitData))
        XCTAssertGreaterThanOrEqual(portraitDoc.pageCount, 1)
        let portraitPageBounds = try XCTUnwrap(portraitDoc.page(at: 0)?.bounds(for: .mediaBox))
        XCTAssertEqual(portraitPageBounds.width, 595, accuracy: 1.0)
        XCTAssertEqual(portraitPageBounds.height, 842, accuracy: 1.0)
        XCTAssertLessThan(portraitPageBounds.width, portraitPageBounds.height)

        let landscapeOptions = RoadmapPDFExportOptions.defaultOptions(
            for: layout,
            headings: [phase1, phase2, phase4Distant],
            orientation: .landscape
        )
        let landscapeData = try XCTUnwrap(
            ProjectRoadmapExporter.exportRoadmapPDFData(
                project: project,
                headings: [phase1, phase2, phase4Distant],
                layout: layout,
                options: landscapeOptions
            )
        )
        XCTAssertTrue(landscapeData.starts(with: Data("%PDF-".utf8)))
        let landscapeDoc = try XCTUnwrap(PDFDocument(data: landscapeData))
        XCTAssertGreaterThanOrEqual(landscapeDoc.pageCount, 1)
        let landscapePageBounds = try XCTUnwrap(landscapeDoc.page(at: 0)?.bounds(for: .mediaBox))
        XCTAssertEqual(landscapePageBounds.width, 842, accuracy: 1.0)
        XCTAssertEqual(landscapePageBounds.height, 595, accuracy: 1.0)
        XCTAssertGreaterThan(landscapePageBounds.width, landscapePageBounds.height)

        // Multi-page pagination check when rows exceed 1 Landscape page
        let manyTasks: [TaskItem] = (0..<24).map { idx in
            TaskItem(
                id: "paged-task-\(idx)",
                title: "Deliverable Item #\(idx + 1)",
                scheduleState: .upcoming,
                whenDate: millis(2026, 11, 20) + Int64(idx % 15) * 86_400_000,
                projectId: project.id,
                headingId: phase2.id,
                sortOrder: Double(idx * 10),
                createdAt: todayMs,
                updatedAt: todayMs
            )
        }
        let multiPageLayout = ProjectRoadmapEngine.buildLayout(
            project: project,
            headings: [phase1, phase2],
            tasks: manyTasks,
            todayMillis: todayMs,
            calendar: cal
        )
        let multiPagePDF = try XCTUnwrap(
            ProjectRoadmapExporter.exportRoadmapPDFData(
                project: project,
                headings: [phase1, phase2],
                layout: multiPageLayout,
                options: RoadmapPDFExportOptions.defaultOptions(for: multiPageLayout, headings: [phase1, phase2], orientation: .landscape)
            )
        )
        let multiPageDoc = try XCTUnwrap(PDFDocument(data: multiPagePDF))
        XCTAssertGreaterThanOrEqual(multiPageDoc.pageCount, 2, "24+ rows must paginate cleanly across 2+ A4 Landscape pages")

        // 4. Verify all 11 new v30_pdf_* localization keys across EN, DE, FR, IT, ES
        let pdfDialogKeys = [
            "v30_pdf_export_dialog_title",
            "v30_pdf_phases_label",
            "v30_pdf_select_all_phases",
            "v30_pdf_timeline_label",
            "v30_pdf_timeline_auto",
            "v30_pdf_timeline_from_today",
            "v30_pdf_timeline_next_30d",
            "v30_pdf_timeline_custom",
            "v30_pdf_orientation_label",
            "v30_pdf_orientation_landscape",
            "v30_pdf_orientation_portrait"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in pdfDialogKeys {
                let val = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertFalse(val.isEmpty, "Missing \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(val, key, "Unresolved \(key) in \(lang.rawValue)")
            }
        }
    }

    // MARK: - 34. v3.0.0-beta.2 Milestone 4: Asana & Microsoft Planner Offline Import / Export

    func testMilestone4AsanaCsvRoundTripAndTwoPassSubtasks() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let millis: (Int, Int, Int) -> Int64 = { y, m, d in
            let date = cal.date(from: DateComponents(year: y, month: m, day: d))!
            return DateEpochConverter.startOfDayMillis(for: date, calendar: cal)
        }

        // Subtask row intentionally appears BEFORE its parent row in CSV to verify 2-pass resolution,
        // plus another subtask matching parent by Name instead of Task ID.
        let sampleAsanaCsv = """
        Task ID,Created At,Completed At,Last Modified,Name,Section/Column,Assignee,Start Date,Due Date,Tags,Notes,Projects,Parent task
        sub-early,2026-05-01,2026-05-03,2026-05-03,Verify OAuth scopes,Architecture,, , , , ,Q3 Platform Launch,task-100
        task-100,2026-05-01,,2026-05-04,Design Auth Gateway,Architecture,alice@company.com,2026-05-10,2026-05-18,"Security, API","Line 1\nLine 2 notes",Q3 Platform Launch,
        sub-by-name,2026-05-02,,2026-05-04,Draft threat model,Architecture,,,,,,Q3 Platform Launch,Design Auth Gateway
        task-101,2026-05-01,2026-05-09,2026-05-09,Publish RFC,Rollout,bob@company.com,2026-05-05,2026-05-09,Docs,Approved by team,Q3 Platform Launch,
        task-root,2026-05-01,,2026-05-02,Kickoff alignment,Untitled section,,,,,,Q3 Platform Launch,
        """

        let imported = try ExternalProjectCodec.importAutoDetect(
            data: Data(sampleAsanaCsv.utf8),
            fileNameHint: "export.csv",
            calendar: cal
        )

        XCTAssertEqual(imported.title, "Q3 Platform Launch")
        XCTAssertEqual(imported.ungroupedTasks.map(\.title), ["Kickoff alignment"])
        XCTAssertEqual(imported.phases.map(\.title), ["Architecture", "Rollout"])
        XCTAssertEqual(imported.totalTaskCount, 3)

        let archTask = try XCTUnwrap(imported.phases[0].tasks.first)
        XCTAssertEqual(archTask.title, "Design Auth Gateway")
        XCTAssertEqual(archTask.status, .open)
        XCTAssertEqual(archTask.assigneeId, "alice@company.com")
        XCTAssertEqual(archTask.whenDate, millis(2026, 5, 10))
        XCTAssertEqual(archTask.deadline, millis(2026, 5, 18))
        XCTAssertEqual(archTask.tagNames, ["Security", "API"])
        XCTAssertEqual(archTask.notes, "Line 1\nLine 2 notes")
        XCTAssertEqual(
            archTask.checklistItems,
            [
                ExternalImportedChecklistItem(title: "Verify OAuth scopes", isCompleted: true),
                ExternalImportedChecklistItem(title: "Draft threat model", isCompleted: false)
            ]
        )

        let doneTask = try XCTUnwrap(imported.phases[1].tasks.first)
        XCTAssertEqual(doneTask.status, .completed)
        XCTAssertNotNil(doneTask.completedAt)
        XCTAssertEqual(doneTask.assigneeId, "bob@company.com")

        // Round-trip through SQLite -> Asana CSV export -> re-import
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let persisted = try db.importExternalProject(imported, calendar: cal)
        XCTAssertEqual(persisted.importedTaskCount, 3)

        let exportedCsv = try db.exportProjectExternal(
            projectId: persisted.project.id,
            format: .asanaCsv,
            calendar: cal
        )
        XCTAssertTrue(exportedCsv.suggestedFileName.hasSuffix("-asana.csv"))

        let roundTripped = try ExternalProjectCodec.importAutoDetect(
            data: exportedCsv.data,
            fileNameHint: exportedCsv.suggestedFileName,
            calendar: cal
        )
        XCTAssertEqual(roundTripped.title, "Q3 Platform Launch")
        XCTAssertEqual(roundTripped.ungroupedTasks.map(\.title), ["Kickoff alignment"])
        XCTAssertEqual(roundTripped.phases.map(\.title), ["Architecture", "Rollout"])
        XCTAssertEqual(
            roundTripped.phases[0].tasks.first?.checklistItems,
            [
                ExternalImportedChecklistItem(title: "Verify OAuth scopes", isCompleted: true),
                ExternalImportedChecklistItem(title: "Draft threat model", isCompleted: false)
            ]
        )
        XCTAssertEqual(roundTripped.phases[1].tasks.first?.status, .completed)
    }

    func testMilestone4AsanaJsonRestAndStructuredRoundTrip() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let millis: (Int, Int, Int) -> Int64 = { y, m, d in
            let date = cal.date(from: DateComponents(year: y, month: m, day: d))!
            return DateEpochConverter.startOfDayMillis(for: date, calendar: cal)
        }

        // 1. Asana REST API JSON (`{"data": [...]}`)
        let restJson = """
        {
          "data": [
            {
              "gid": "12001",
              "name": "Build Sync Engine",
              "notes": "Delta cursor support",
              "completed": false,
              "completed_at": null,
              "start_on": "2026-06-01",
              "due_on": "2026-06-12",
              "hearted": true,
              "assignee": { "gid": "u-1", "name": "petros@thingsbook.app" },
              "tags": [{ "name": "Core" }, { "name": "Sync" }],
              "memberships": [
                {
                  "project": { "gid": "p-1", "name": "Mobile Sync 3.0" },
                  "section": { "gid": "s-1", "name": "Backend" }
                }
              ],
              "subtasks": [
                { "name": "Write migration", "completed": true },
                { "name": "Benchmark batching", "completed": false }
              ]
            },
            {
              "gid": "12002",
              "name": "QA Signoff",
              "notes": "",
              "completed": true,
              "completed_at": "2026-06-14T16:20:00.000Z",
              "due_on": "2026-06-14",
              "memberships": [
                {
                  "project": { "gid": "p-1", "name": "Mobile Sync 3.0" },
                  "section": null
                }
              ],
              "subtasks": []
            }
          ]
        }
        """

        let parsedRest = try ExternalProjectCodec.importAutoDetect(
            data: Data(restJson.utf8),
            fileNameHint: "asana-rest.json",
            calendar: cal
        )
        XCTAssertEqual(parsedRest.title, "Mobile Sync 3.0")
        XCTAssertEqual(parsedRest.ungroupedTasks.map(\.title), ["QA Signoff"])
        XCTAssertEqual(parsedRest.ungroupedTasks.first?.status, .completed)
        XCTAssertEqual(parsedRest.phases.map(\.title), ["Backend"])
        let backendTask = try XCTUnwrap(parsedRest.phases.first?.tasks.first)
        XCTAssertTrue(backendTask.isPinned)
        XCTAssertEqual(backendTask.whenDate, millis(2026, 6, 1))
        XCTAssertEqual(backendTask.deadline, millis(2026, 6, 12))
        XCTAssertEqual(backendTask.assigneeId, "petros@thingsbook.app")
        XCTAssertEqual(backendTask.tagNames, ["Core", "Sync"])
        XCTAssertEqual(
            backendTask.checklistItems,
            [
                ExternalImportedChecklistItem(title: "Write migration", isCompleted: true),
                ExternalImportedChecklistItem(title: "Benchmark batching", isCompleted: false)
            ]
        )

        // 2. Structured JSON (`{"project": ..., "sections": [...], "tasks": [...]}`)
        let structuredJson = """
        {
          "project": { "name": "Design System Refresh", "notes": "Soft Bento tokens", "tags": ["UI"] },
          "sections": [
            {
              "name": "Tokens",
              "start_on": "2026-07-01",
              "due_on": "2026-07-10",
              "tasks": [
                {
                  "name": "Audit contrast ratios",
                  "effort_level": "DEEP_FOCUS",
                  "tags": ["Accessibility"],
                  "subtasks": [{ "name": "Check DarkAqua", "completed": true }]
                }
              ]
            }
          ],
          "tasks": [
            { "name": "Prepare Figma handoff", "completed": false }
          ]
        }
        """

        let parsedStructured = try ExternalProjectCodec.importAsanaJson(
            Data(structuredJson.utf8),
            calendar: cal
        )
        XCTAssertEqual(parsedStructured.title, "Design System Refresh")
        XCTAssertEqual(parsedStructured.notes, "Soft Bento tokens")
        XCTAssertEqual(parsedStructured.tagNames, ["UI"])
        XCTAssertEqual(parsedStructured.ungroupedTasks.map(\.title), ["Prepare Figma handoff"])
        XCTAssertEqual(parsedStructured.phases.first?.startDate, millis(2026, 7, 1))
        XCTAssertEqual(parsedStructured.phases.first?.endDate, millis(2026, 7, 10))
        XCTAssertEqual(parsedStructured.phases.first?.tasks.first?.effortLevel, .deepFocus)
    }

    func testMilestone4PlannerXlsxDeflateAndStoredRoundTripAndExcelSerialDates() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let millis: (Int, Int, Int) -> Int64 = { y, m, d in
            let date = cal.date(from: DateComponents(year: y, month: m, day: d))!
            return DateEpochConverter.startOfDayMillis(for: date, calendar: cal)
        }

        // Excel serial 46154 from base 1899-12-30 -> 2026-05-12
        XCTAssertEqual(ExternalProjectCodec.parseExternalDate("46154", calendar: cal), millis(2026, 5, 12))
        XCTAssertEqual(ExternalProjectCodec.parseExternalDate("46154.0", calendar: cal), millis(2026, 5, 12))
        XCTAssertEqual(ExternalProjectCodec.parseExternalDate("46160.75", calendar: cal), millis(2026, 5, 18))

        // Build synthetic Planner .xlsx mixing Stored (method 0) & Deflate (method 8) ZIP entries,
        // rich-text sharedStrings, inlineStr, numeric Excel serial dates, and both checklist formats ("2/3" & title match)
        let sstXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="8" uniqueCount="8">
          <si><t>Task Name</t></si>
          <si><t>Bucket Name</t></si>
          <si><t>Progress</t></si>
          <si><t>Priority</t></si>
          <si><t>Start Date</t></si>
          <si><t>Due Date</t></si>
          <si><r><t>Migrate </t></r><r><t>Database</t></r><rPh><t>IGNORE</t></rPh></si>
          <si><t>Infrastructure</t></si>
        </sst>
        """
        let wbXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheets><sheet name="Cloud Migration Plan" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
        """
        let sheetXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>
            <row r="1">
              <c r="A1" t="s"><v>0</v></c>
              <c r="B1" t="s"><v>1</v></c>
              <c r="C1" t="s"><v>2</v></c>
              <c r="D1" t="s"><v>3</v></c>
              <c r="E1" t="s"><v>4</v></c>
              <c r="F1" t="s"><v>5</v></c>
              <c r="G1" t="inlineStr"><is><t>Completed Checklist Items</t></is></c>
              <c r="H1" t="inlineStr"><is><t>Checklist Items</t></is></c>
              <c r="I1" t="inlineStr"><is><t>Labels</t></is></c>
              <c r="J1" t="inlineStr"><is><t>Assigned To</t></is></c>
            </row>
            <row r="2">
              <c r="A2" t="s"><v>6</v></c>
              <c r="B2" t="s"><v>7</v></c>
              <c r="C2" t="inlineStr"><is><t>In progress</t></is></c>
              <c r="D2" t="inlineStr"><is><t>Urgent</t></is></c>
              <c r="E2"><v>46154</v></c>
              <c r="F2"><v>46160.0</v></c>
              <c r="G2" t="inlineStr"><is><t>2/3</t></is></c>
              <c r="H2" t="inlineStr"><is><t>Backup prod;Run schema DDL;Verify replicas</t></is></c>
              <c r="I2" t="inlineStr"><is><t>DBA;Critical</t></is></c>
              <c r="J2" t="inlineStr"><is><t>sre@company.com</t></is></c>
            </row>
            <row r="3">
              <c r="A3" t="inlineStr"><is><t>Decommission Legacy Host</t></is></c>
              <c r="B3" t="s"><v>7</v></c>
              <c r="C3" t="inlineStr"><is><t>Completed</t></is></c>
              <c r="D3" t="inlineStr"><is><t>Low</t></is></c>
              <c r="G3" t="inlineStr"><is><t>Archive logs</t></is></c>
              <c r="H3" t="inlineStr"><is><t>Notify stakeholders;Archive logs</t></is></c>
              <c r="I3" t="inlineStr"><is><t>Ops</t></is></c>
            </row>
          </sheetData>
        </worksheet>
        """

        let mixedZip = try XlsxZipArchive.writeEntries([
            ("xl/workbook.xml", Data(wbXml.utf8), false), // Stored (method 0)
            ("xl/sharedStrings.xml", Data(sstXml.utf8), true), // Deflate (method 8)
            ("xl/worksheets/sheet1.xml", Data(sheetXml.utf8), true) // Deflate (method 8)
        ])

        let parsed = try ExternalProjectCodec.importAutoDetect(
            data: mixedZip,
            fileNameHint: "plan.xlsx",
            calendar: cal
        )
        XCTAssertEqual(parsed.title, "Cloud Migration Plan")
        XCTAssertEqual(parsed.phases.map(\.title), ["Infrastructure"])
        XCTAssertEqual(parsed.phases[0].tasks.count, 2)

        let task1 = parsed.phases[0].tasks[0]
        XCTAssertEqual(task1.title, "Migrate Database")
        XCTAssertEqual(task1.status, .open)
        XCTAssertTrue(task1.isPinned)
        XCTAssertEqual(task1.whenDate, millis(2026, 5, 12))
        XCTAssertEqual(task1.deadline, millis(2026, 5, 18))
        XCTAssertEqual(task1.assigneeId, "sre@company.com")
        XCTAssertEqual(task1.tagNames, ["DBA", "Critical"])
        XCTAssertEqual(
            task1.checklistItems,
            [
                ExternalImportedChecklistItem(title: "Backup prod", isCompleted: true),
                ExternalImportedChecklistItem(title: "Run schema DDL", isCompleted: true),
                ExternalImportedChecklistItem(title: "Verify replicas", isCompleted: false)
            ]
        )

        let task2 = parsed.phases[0].tasks[1]
        XCTAssertEqual(task2.title, "Decommission Legacy Host")
        XCTAssertEqual(task2.status, .completed)
        XCTAssertEqual(task2.effortLevel, .quickWin)
        XCTAssertEqual(
            task2.checklistItems,
            [
                ExternalImportedChecklistItem(title: "Notify stakeholders", isCompleted: false),
                ExternalImportedChecklistItem(title: "Archive logs", isCompleted: true)
            ]
        )

        // Test exportPlannerXlsx in both Deflate and Stored modes
        let proj = Project(id: "p-xlsx", title: "Planner Binary Export", createdAt: millis(2026, 5, 1), updatedAt: millis(2026, 5, 1))
        let h1 = Heading(id: "h-xlsx", projectId: proj.id, title: "Sprint 1", sortOrder: 1000, createdAt: 1, updatedAt: 1)
        let tagImportant = Tag(id: "tg-1", title: "Platform", createdAt: 1, updatedAt: 1)
        let tOpen = TaskItem(
            id: "t-xlsx-1",
            title: "Implement OOXML Writer",
            notes: "Pure Swift + zlib",
            status: .open,
            scheduleState: .upcoming,
            whenDate: millis(2026, 5, 12),
            deadline: millis(2026, 5, 20),
            projectId: proj.id,
            headingId: h1.id,
            effortLevel: TaskEffortLevel.deepFocus.rawValue,
            assigneeId: "lead@company.com",
            checklistItems: [
                ChecklistItem(id: "c1", taskId: "t-xlsx-1", title: "Central directory", isCompleted: true, sortOrder: 100, createdAt: 1, updatedAt: 1),
                ChecklistItem(id: "c2", taskId: "t-xlsx-1", title: "Stylesheet", isCompleted: false, sortOrder: 200, createdAt: 1, updatedAt: 1)
            ],
            tags: [tagImportant],
            createdAt: millis(2026, 5, 1),
            updatedAt: millis(2026, 5, 2)
        )

        for compressFlag in [true, false] {
            let xlsxBytes = try ExternalProjectCodec.exportPlannerXlsx(
                project: proj,
                headings: [h1],
                tasks: [tOpen],
                compressEntries: compressFlag,
                calendar: cal
            )
            let reimported = try ExternalProjectCodec.importPlannerXlsx(xlsxBytes, calendar: cal)
            XCTAssertEqual(reimported.title, "Planner Binary Export")
            XCTAssertEqual(reimported.phases.map(\.title), ["Sprint 1"])
            let rtTask = try XCTUnwrap(reimported.phases.first?.tasks.first)
            XCTAssertEqual(rtTask.title, "Implement OOXML Writer")
            XCTAssertEqual(rtTask.notes, "Pure Swift + zlib")
            XCTAssertEqual(rtTask.whenDate, millis(2026, 5, 12))
            XCTAssertEqual(rtTask.deadline, millis(2026, 5, 20))
            XCTAssertEqual(rtTask.effortLevel, .deepFocus)
            XCTAssertEqual(rtTask.assigneeId, "lead@company.com")
            XCTAssertEqual(rtTask.tagNames, ["Platform"])
            XCTAssertEqual(
                rtTask.checklistItems,
                [
                    ExternalImportedChecklistItem(title: "Central directory", isCompleted: true),
                    ExternalImportedChecklistItem(title: "Stylesheet", isCompleted: false)
                ]
            )
        }
    }

    func testMilestone4PlannerCsvCommaAndSemicolonRoundTrip() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let millis: (Int, Int, Int) -> Int64 = { y, m, d in
            let date = cal.date(from: DateComponents(year: y, month: m, day: d))!
            return DateEpochConverter.startOfDayMillis(for: date, calendar: cal)
        }

        // Semicolon-delimited localized German Planner CSV
        let semicolonCsv = """
        Aufgaben-ID;Aufgabenname;Bucket;Fortschritt;Priorität;Zugewiesen an;Startdatum;Fälligkeitsdatum;Beschreibung;Abgeschlossene Checklistenelemente;Checklistenelemente;Bezeichnungen
        p-1;Release-Kandidat bauen;Auslieferung;100%;Wichtig;dev@firma.de;12.05.2026;19.05.2026;Signiertes DMG;Notarisierung prüfen;"Universal Binary kompilieren\nNotarisierung prüfen";macOS,Release
        p-2;Pressemitteilung; ;Nicht begonnen;Dringend;;15.05.2026;20.05.2026;;;;Marketing
        """

        let parsedSemi = try ExternalProjectCodec.importAutoDetect(
            data: Data(semicolonCsv.utf8),
            fileNameHint: "Quartalsplan-planner.csv",
            calendar: cal
        )
        XCTAssertEqual(parsedSemi.title, "Quartalsplan")
        XCTAssertEqual(parsedSemi.ungroupedTasks.map(\.title), ["Pressemitteilung"])
        XCTAssertEqual(parsedSemi.ungroupedTasks.first?.isPinned, true)
        XCTAssertEqual(parsedSemi.phases.map(\.title), ["Auslieferung"])

        let deTask = try XCTUnwrap(parsedSemi.phases.first?.tasks.first)
        XCTAssertEqual(deTask.status, .completed)
        XCTAssertEqual(deTask.effortLevel, .deepFocus)
        XCTAssertEqual(deTask.whenDate, millis(2026, 5, 12))
        XCTAssertEqual(deTask.deadline, millis(2026, 5, 19))
        XCTAssertEqual(deTask.assigneeId, "dev@firma.de")
        XCTAssertEqual(deTask.tagNames, ["macOS", "Release"])
        XCTAssertEqual(
            deTask.checklistItems,
            [
                ExternalImportedChecklistItem(title: "Universal Binary kompilieren", isCompleted: false),
                ExternalImportedChecklistItem(title: "Notarisierung prüfen", isCompleted: true)
            ]
        )

        // Comma-delimited Planner CSV export -> import round-trip
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let importedRes = try db.importExternalProject(parsedSemi, calendar: cal)
        let exportedPlannerCsv = try db.exportProjectExternal(
            projectId: importedRes.project.id,
            format: .plannerCsv,
            calendar: cal
        )
        let reparsedComma = try ExternalProjectCodec.importAutoDetect(
            data: exportedPlannerCsv.data,
            fileNameHint: exportedPlannerCsv.suggestedFileName,
            calendar: cal
        )
        XCTAssertEqual(reparsedComma.title, "Quartalsplan")
        XCTAssertEqual(reparsedComma.ungroupedTasks.count, 1)
        XCTAssertEqual(reparsedComma.phases.first?.tasks.first?.status, .completed)
        XCTAssertEqual(reparsedComma.phases.first?.tasks.first?.effortLevel, .deepFocus)
    }

    func testMilestone4DatabaseExternalImportExportEndToEndAndLocalizationParity() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let millis: (Int, Int, Int) -> Int64 = { y, m, d in
            let date = cal.date(from: DateComponents(year: y, month: m, day: d))!
            return DateEpochConverter.startOfDayMillis(for: date, calendar: cal)
        }

        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let workArea = try db.createArea(title: "Engineering")
        let existingDesignTag = try db.createOrUpdateTag(title: "Design")

        let externalSpec = ExternalImportedProject(
            title: "Enterprise Portal 3.0",
            notes: "Cross-functional launch plan",
            ungroupedTasks: [
                ExternalImportedTask(
                    title: "Executive Kickoff",
                    notes: "Deck ready",
                    status: .completed,
                    completedAt: millis(2026, 5, 2),
                    whenDate: millis(2026, 5, 1),
                    deadline: millis(2026, 5, 2),
                    tagNames: ["design", "Strategy"], // "design" must reuse existingDesignTag case-insensitively!
                    checklistItems: [
                        ExternalImportedChecklistItem(title: "Book room", isCompleted: true),
                        ExternalImportedChecklistItem(title: "Send invite", isCompleted: true)
                    ],
                    assigneeId: "pm@enterprise.io",
                    workspaceId: "ws-portal"
                )
            ],
            phases: [
                ExternalImportedPhase(
                    title: "Phase 1: Implementation",
                    startDate: millis(2026, 5, 4),
                    endDate: millis(2026, 5, 22),
                    tasks: [
                        ExternalImportedTask(
                            title: "Build GraphQL Gateway",
                            notes: "Strict authz",
                            status: .open,
                            whenDate: millis(2026, 5, 6),
                            deadline: millis(2026, 5, 18),
                            isPinned: true,
                            effortLevel: .deepFocus,
                            tagNames: ["Backend", "DESIGN"],
                            checklistItems: [
                                ExternalImportedChecklistItem(title: "Schema SDL", isCompleted: true),
                                ExternalImportedChecklistItem(title: "Rate limiter", isCompleted: false)
                            ],
                            assigneeId: "eng@enterprise.io",
                            workspaceId: "ws-portal"
                        )
                    ]
                )
            ],
            tagNames: ["design"],
            workspaceId: "ws-portal"
        )

        let created = try db.importExternalProject(
            externalSpec,
            areaId: workArea.id,
            calendar: cal
        )
        XCTAssertEqual(created.importedTaskCount, 2)
        XCTAssertEqual(created.project.areaId, workArea.id)
        XCTAssertEqual(created.project.workspaceId, "ws-portal")
        XCTAssertEqual(created.project.tagIds, [existingDesignTag.id])
        XCTAssertEqual(created.project.totalTaskCount, 2)
        XCTAssertEqual(created.project.completedTaskCount, 1)

        let snap = try db.loadWorkspaceSnapshot(calendar: cal)
        // Only 3 tags total: reused "Design" + newly created "Strategy" & "Backend"
        XCTAssertEqual(snap.tags.count, 3)
        XCTAssertEqual(snap.tags.filter { $0.title.lowercased() == "design" }.count, 1)

        let headings = try XCTUnwrap(snap.headingsByProject[created.project.id])
        XCTAssertEqual(headings.count, 1)
        XCTAssertEqual(headings[0].title, "Phase 1: Implementation")
        XCTAssertEqual(headings[0].startDate, millis(2026, 5, 4))
        XCTAssertEqual(headings[0].endDate, millis(2026, 5, 22))

        let openTask = try XCTUnwrap(snap.allOpenTasks.first { $0.projectId == created.project.id })
        XCTAssertEqual(openTask.title, "Build GraphQL Gateway")
        XCTAssertTrue(openTask.isPinned)
        XCTAssertEqual(openTask.effortLevel, TaskEffortLevel.deepFocus.rawValue)
        XCTAssertEqual(openTask.assigneeId, "eng@enterprise.io")
        XCTAssertEqual(openTask.workspaceId, "ws-portal")
        XCTAssertTrue(openTask.tagIds.contains(existingDesignTag.id))
        XCTAssertEqual(openTask.checklistItems.map(\.isCompleted), [true, false])

        let completedTask = try XCTUnwrap(snap.logbookTasks.first { $0.projectId == created.project.id })
        XCTAssertEqual(completedTask.title, "Executive Kickoff")
        XCTAssertEqual(completedTask.status, .completed)
        XCTAssertEqual(completedTask.assigneeId, "pm@enterprise.io")
        XCTAssertEqual(completedTask.workspaceId, "ws-portal")
        XCTAssertEqual(completedTask.checklistItems.map(\.isCompleted), [true, true])

        // Export across all 4 formats and verify both open & completed tasks + checklists round-trip
        for format in ExternalExportFormat.allCases {
            let exported = try db.exportProjectExternal(
                projectId: created.project.id,
                format: format,
                calendar: cal
            )
            XCTAssertFalse(exported.data.isEmpty)
            XCTAssertTrue(exported.suggestedFileName.hasSuffix(format.defaultFileSuffix))

            let reimported = try ExternalProjectCodec.importAutoDetect(
                data: exported.data,
                fileNameHint: exported.suggestedFileName,
                calendar: cal
            )
            XCTAssertEqual(reimported.title, "Enterprise Portal 3.0")
            XCTAssertEqual(reimported.totalTaskCount, 2, "Both open and completed tasks must round-trip for \(format)")
            XCTAssertEqual(reimported.ungroupedTasks.first?.status, .completed)
            XCTAssertEqual(reimported.ungroupedTasks.first?.assigneeId, "pm@enterprise.io")
            XCTAssertEqual(reimported.phases.first?.title, "Phase 1: Implementation")
            XCTAssertEqual(
                reimported.phases.first?.tasks.first?.checklistItems,
                [
                    ExternalImportedChecklistItem(title: "Schema SDL", isCompleted: true),
                    ExternalImportedChecklistItem(title: "Rate limiter", isCompleted: false)
                ]
            )
            if format == .asanaJson {
                XCTAssertEqual(reimported.workspaceId, "ws-portal")
                XCTAssertEqual(reimported.phases.first?.tasks.first?.workspaceId, "ws-portal")
            }
        }

        // Verify all 14 Milestone 4 localization keys across EN, DE, FR, IT, ES
        let plainExternalKeys = [
            "v30_external_import_export_tab",
            "v30_import_asana",
            "v30_export_asana_csv",
            "v30_export_asana_json",
            "v30_import_planner",
            "v30_export_planner_xlsx",
            "v30_export_planner_csv",
            "v30_external_import_section",
            "v30_external_import_desc",
            "v30_external_export_section",
            "v30_external_select_project",
            "v30_external_no_projects"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in plainExternalKeys {
                let resolved = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertFalse(resolved.isEmpty, "Empty \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(resolved, key, "Unresolved \(key) in \(lang.rawValue)")
            }
            let importMsg = ThingsStrings.shared.format("v30_external_import_success", lang: lang, "Roadmap", 12)
            XCTAssertTrue(importMsg.contains("Roadmap") && importMsg.contains("12"))
            let exportMsg = ThingsStrings.shared.format("v30_external_export_success", lang: lang, "Roadmap")
            XCTAssertTrue(exportMsg.contains("Roadmap"))
        }
    }

    func testV300Beta5UxRefinementKeysAndParity() {
        let beta5Keys = [
            "v30_timeline_fullscreen",
            "v30_timeline_exit_fullscreen",
            "v30_quick_edit_action",
            "v30_open_details_action"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in beta5Keys {
                let resolved = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertFalse(resolved.isEmpty, "Empty \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(resolved, key, "Unresolved \(key) in \(lang.rawValue)")
                XCTAssertFalse(resolved.contains("%"), "Expected 0 format placeholders for \(key) in \(lang.rawValue)")
            }
        }
    }

    func testMultiDayDurationNonDestructiveTimeblockClearAndPillFormatting() throws {
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let task = try db.createTask(
            title: "Architecture Migration",
            notes: "Phase 1 rollout\nPhase 2 verification",
            scheduleState: .today,
            whenDate: DateEpochConverter.startOfDayMillis(for: Date()),
            checklistTitles: ["Draft RFC", "Run load tests"]
        )

        // 1. Multi-day standalone duration (2400m = 5d) must persist without 720m clamping
        try db.setTaskEffortAndDuration(
            taskId: task.id,
            effortLevel: TaskEffortLevel.deepFocus.rawValue,
            durationMinutes: 2400
        )
        var snap = try db.loadWorkspaceSnapshot()
        var loaded = try XCTUnwrap(snap.allOpenTasks.first { $0.id == task.id })
        XCTAssertEqual(loaded.durationMinutes, 2400)
        XCTAssertEqual(loaded.formattedStandaloneDurationPill, "5d")
        XCTAssertEqual(loaded.formattedTimeblockPill, "5d")

        // 2. Timeblocking at 09:00 with 2400m preserves 2400m, and clearing timeblock with (nil, nil) keeps 2400m intact
        try db.setTaskTimeblock(
            taskId: task.id,
            scheduledStartMinutes: 9 * 60,
            durationMinutes: 2400
        )
        snap = try db.loadWorkspaceSnapshot()
        loaded = try XCTUnwrap(snap.allOpenTasks.first { $0.id == task.id })
        XCTAssertEqual(loaded.scheduledStartMinutes, 540)
        XCTAssertEqual(loaded.durationMinutes, 2400)
        XCTAssertEqual(loaded.formattedTimeblockPill, "09:00 · 5d")

        try db.setTaskTimeblock(
            taskId: task.id,
            scheduledStartMinutes: nil,
            durationMinutes: nil
        )
        snap = try db.loadWorkspaceSnapshot()
        loaded = try XCTUnwrap(snap.allOpenTasks.first { $0.id == task.id })
        XCTAssertNil(loaded.scheduledStartMinutes)
        XCTAssertEqual(loaded.durationMinutes, 2400, "Clearing timeblock must preserve standalone duration_minutes")

        // 3. Upper bound clamping at 48_000m (100 workdays)
        try db.setTaskEffortAndDuration(taskId: task.id, effortLevel: nil, durationMinutes: 60_000)
        snap = try db.loadWorkspaceSnapshot()
        loaded = try XCTUnwrap(snap.allOpenTasks.first { $0.id == task.id })
        XCTAssertEqual(loaded.durationMinutes, 48_000)

        // 4. formattedTimeblockPill across <60m, multi-hour, overnight rollover, 1d (480m), and 2d (960m)
        let pill45 = TaskItem(
            id: "p-45",
            title: "Sync",
            scheduledStartMinutes: 540,
            durationMinutes: 45,
            createdAt: 1,
            updatedAt: 1
        )
        XCTAssertEqual(pill45.formattedTimeblockPill, "09:00–09:45 · 45m")

        let pill90 = TaskItem(
            id: "p-90",
            title: "Design Review",
            scheduledStartMinutes: 540,
            durationMinutes: 90,
            createdAt: 1,
            updatedAt: 1
        )
        XCTAssertEqual(pill90.formattedTimeblockPill, "09:00–10:30 · 1h 30m")

        let pillOvernight = TaskItem(
            id: "p-overnight",
            title: "Late Deploy",
            scheduledStartMinutes: 23 * 60 + 30,
            durationMinutes: 60,
            createdAt: 1,
            updatedAt: 1
        )
        XCTAssertEqual(pillOvernight.formattedTimeblockPill, "23:30–00:30 +1d · 1h")

        let pill1d = TaskItem(
            id: "p-480",
            title: "Sprint Day",
            scheduledStartMinutes: 540,
            durationMinutes: 480,
            createdAt: 1,
            updatedAt: 1
        )
        XCTAssertEqual(pill1d.formattedTimeblockPill, "09:00 · 8h")

        let pill2d = TaskItem(
            id: "p-960",
            title: "Offsite",
            scheduledStartMinutes: 540,
            durationMinutes: 960,
            createdAt: 1,
            updatedAt: 1
        )
        XCTAssertEqual(pill2d.formattedTimeblockPill, "09:00 · 2d")

        // 5. FocusTimerState HH:MM:SS formatting for >= 1h
        let shortTimer = FocusTimerState(taskId: "t-short", taskTitle: "Quick", remainingSeconds: 750, totalSeconds: 1500)
        XCTAssertEqual(shortTimer.formattedCountdown, "12:30")

        let hourTimer = FocusTimerState(taskId: "t-hour", taskTitle: "Deep", remainingSeconds: 3600, totalSeconds: 3600)
        XCTAssertEqual(hourTimer.formattedCountdown, "01:00:00")

        let multiHourTimer = FocusTimerState(taskId: "t-long", taskTitle: "Workshop", remainingSeconds: 5432, totalSeconds: 7200)
        XCTAssertEqual(multiHourTimer.formattedCountdown, "01:30:32")

        // 6. Markdown Copy To-Do formatting & Outline @duration parsing
        let md = TaskItem.formatMarkdown(loaded)
        XCTAssertTrue(md.hasPrefix("- [ ] Architecture Migration\n"))
        XCTAssertTrue(md.contains("  Phase 1 rollout"))
        XCTAssertTrue(md.contains("  - [ ] Draft RFC"))

        let outline = """
        # Launch Plan
        ## Engineering
        - [ ] API audit @45m
        - [ ] Security hardening @09:30 @1d
        """
        let parsedOutline = ThingsSQLiteDatabase.parseOutlineToBlueprint(outline)
        XCTAssertEqual(parsedOutline.title, "Launch Plan")
        XCTAssertEqual(parsedOutline.blueprint.headings.count, 1)
        let importedTasks = parsedOutline.blueprint.headings[0].tasks
        XCTAssertEqual(importedTasks.count, 2)
        XCTAssertEqual(importedTasks[0].title, "API audit")
        XCTAssertEqual(importedTasks[0].durationMinutes, 45)
        XCTAssertEqual(importedTasks[1].title, "Security hardening")
        XCTAssertEqual(importedTasks[1].scheduledStartMinutes, 9 * 60 + 30)
        XCTAssertEqual(importedTasks[1].durationMinutes, 480)
    }

    func testFirebaseConfigTrimsWhitespaceAndNewlines() throws {
        let suiteName = "com.pv.thingsbook.tests.firebase.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let dirty = FirebaseConfig(
            projectId: "  my-custom-proj \n",
            apiKey: "\tAIzaCustomKey123  \r\n",
            appId: "  1:999:web:abc123 \n"
        )
        XCTAssertEqual(dirty.projectId, "my-custom-proj")
        XCTAssertEqual(dirty.apiKey, "AIzaCustomKey123")
        XCTAssertEqual(dirty.appId, "1:999:web:abc123")

        FirebaseConfig.saveCustom(dirty, defaults: defaults)
        let loaded = FirebaseConfig.loadActive(defaults: defaults)
        XCTAssertEqual(loaded, FirebaseConfig(projectId: "my-custom-proj", apiKey: "AIzaCustomKey123", appId: "1:999:web:abc123"))

        // Even legacy raw JSON in UserDefaults with untrimmed whitespace must normalize cleanly on loadActive
        let legacyRaw = #"{"projectId":"  legacy-proj\n","apiKey":" key-xyz ","appId":" app-1 "}"#
        defaults.set(Data(legacyRaw.utf8), forKey: "thingsbook_macos_custom_firebase_v1")
        let normalizedLegacy = FirebaseConfig.loadActive(defaults: defaults)
        XCTAssertEqual(normalizedLegacy.projectId, "legacy-proj")
        XCTAssertEqual(normalizedLegacy.apiKey, "key-xyz")
        XCTAssertEqual(normalizedLegacy.appId, "app-1")

        // Whitespace-only custom config falls back to defaultProduction
        FirebaseConfig.saveCustom(FirebaseConfig(projectId: "   ", apiKey: "\n", appId: ""), defaults: defaults)
        XCTAssertEqual(FirebaseConfig.loadActive(defaults: defaults), .defaultProduction)
    }

    // MARK: - 42. v3.0.0-beta.15 Task Milestones Across SQLite, Templates, Backup, Sync, Asana, Outline & Roadmap

    func testMilestonePersistenceTemplatesBackupSyncAndAsanaRoundTrip() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let millis: (Int, Int, Int) -> Int64 = { y, m, d in
            let date = cal.date(from: DateComponents(year: y, month: m, day: d))!
            return DateEpochConverter.startOfDayMillis(for: date, calendar: cal)
        }

        // 1. SQLite createTask + setTaskMilestone + setTasksMilestone + QuickFind FTS + Duplication + Project Templates
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let area = try db.createArea(title: "Product Engineering")
        let proj = try db.createProject(title: "Thingsbook 3.0 GA", areaId: area.id)
        let phase = try db.createHeading(projectId: proj.id, title: "Launch Readiness")

        let regularTask = try db.createTask(
            title: "Regression QA sweep",
            scheduleState: .upcoming,
            whenDate: millis(2026, 8, 10),
            projectId: proj.id,
            headingId: phase.id,
            areaId: area.id
        )
        let milestoneTask = try db.createTask(
            title: "App Store Submission Freeze",
            scheduleState: .upcoming,
            whenDate: millis(2026, 8, 12),
            deadline: millis(2026, 8, 15),
            projectId: proj.id,
            headingId: phase.id,
            areaId: area.id,
            isMilestone: true
        )
        XCTAssertFalse(regularTask.isMilestone)
        XCTAssertTrue(milestoneTask.isMilestone)

        try db.setTaskMilestone(taskId: regularTask.id, isMilestone: true)
        var snap = try db.loadWorkspaceSnapshot(calendar: cal)
        let promoted = try XCTUnwrap(snap.allOpenTasks.first { $0.id == regularTask.id })
        XCTAssertTrue(promoted.isMilestone)
        XCTAssertGreaterThanOrEqual(promoted.updatedAt, regularTask.updatedAt)

        try db.setTasksMilestone([regularTask.id, milestoneTask.id], isMilestone: false)
        snap = try db.loadWorkspaceSnapshot(calendar: cal)
        XCTAssertEqual(snap.allOpenTasks.filter(\.isMilestone).count, 0)

        try db.setTaskMilestone(taskId: milestoneTask.id, isMilestone: true)
        snap = try db.loadWorkspaceSnapshot(calendar: cal)
        XCTAssertTrue(snap.allOpenTasks.first(where: { $0.id == milestoneTask.id })?.isMilestone == true)
        XCTAssertTrue(snap.allOpenTasks.first(where: { $0.id == regularTask.id })?.isMilestone == false)

        // Verify QuickFind FTS5 queries preserve column index 25 (`is_milestone`)
        let ftsHits = try db.searchQuickFind(rawQuery: "Submission Freeze")
        let ftsMilestone = try XCTUnwrap(ftsHits.tasks.first { $0.id == milestoneTask.id })
        XCTAssertTrue(ftsMilestone.isMilestone)

        // Verify duplicateTasks & duplicateProject preserve isMilestone
        let taskCopies = try db.duplicateTasks([milestoneTask.id], copySuffix: "Copy")
        XCTAssertEqual(taskCopies.count, 1)
        XCTAssertTrue(taskCopies[0].isMilestone)

        let duplicatedProj = try XCTUnwrap(try db.duplicateProject(proj.id, copySuffix: "Copy"))
        snap = try db.loadWorkspaceSnapshot(calendar: cal)
        let dupProjTasks = snap.allOpenTasks.filter { $0.projectId == duplicatedProj.id }
        XCTAssertTrue(dupProjTasks.contains(where: { $0.title == "App Store Submission Freeze" && $0.isMilestone }))
        XCTAssertTrue(dupProjTasks.contains(where: { $0.title == "Regression QA sweep" && !$0.isMilestone }))

        // Verify saveProjectAsTemplate & createProjectFromTemplate preserve BlueprintTask.isMilestone
        let savedTemplate = try db.saveProjectAsTemplate(projectId: proj.id, templateTitle: "GA Blueprint")
        let headingBlueprintTasks: [BlueprintTask] = try XCTUnwrap(savedTemplate.blueprint.headings.first?.tasks)
        XCTAssertTrue(headingBlueprintTasks.contains(where: { $0.title == "App Store Submission Freeze" && $0.isMilestone }))
        XCTAssertTrue(headingBlueprintTasks.contains(where: { $0.title == "Regression QA sweep" && !$0.isMilestone }))

        let instantiatedProj = try db.createProjectFromTemplate(
            savedTemplate,
            titleOverride: "Q4 Enterprise Release",
            areaIdOverride: area.id
        )
        snap = try db.loadWorkspaceSnapshot(calendar: cal)
        let instantiatedTasks = snap.allOpenTasks.filter { $0.projectId == instantiatedProj.id }
        XCTAssertTrue(instantiatedTasks.contains(where: { $0.title == "App Store Submission Freeze" && $0.isMilestone }))
        XCTAssertTrue(instantiatedTasks.contains(where: { $0.title == "Regression QA sweep" && !$0.isMilestone }))

        // 2. .tbbackup encrypted & plaintext round-trip of isMilestone
        let backupPayload = try db.buildBackupPayload(preferences: AppPreferences())
        let encryptedBackup = try ThingsBackupCodec.encodeBackup(payload: backupPayload, password: "MilestoneVaultPassword!")
        let decodedBackup = try ThingsBackupCodec.decodeBackup(rawBytes: encryptedBackup, password: "MilestoneVaultPassword!")
        let dbRestored = try ThingsSQLiteDatabase(path: ":memory:")
        try dbRestored.restoreBackupPayload(decodedBackup)
        var restoredSnap = try dbRestored.loadWorkspaceSnapshot(calendar: cal)
        let restoredMilestone = try XCTUnwrap(restoredSnap.allOpenTasks.first { $0.id == milestoneTask.id })
        let restoredRegular = try XCTUnwrap(restoredSnap.allOpenTasks.first { $0.id == regularTask.id })
        XCTAssertTrue(restoredMilestone.isMilestone)
        XCTAssertFalse(restoredRegular.isMilestone)

        // 3. Firestore Sync: omitted isMilestone preserves local true; explicit isMilestone updates local state
        let legacyRemoteWire: [String: Any] = [
            "id": milestoneTask.id,
            "title": "App Store Submission Freeze (Edited on Legacy Client)",
            "status": "OPEN",
            "scheduleState": "UPCOMING",
            "whenDate": millis(2026, 8, 12),
            "deadline": millis(2026, 8, 15),
            "projectId": proj.id,
            "headingId": phase.id,
            "areaId": area.id,
            "sortOrder": restoredMilestone.sortOrder,
            "createdAt": restoredMilestone.createdAt,
            "updatedAt": restoredMilestone.updatedAt + 5_000,
            "isDeleted": false
        ]
        let legacyHints = try XCTUnwrap(FirebaseCloudSyncService.parseTaskWithHints(legacyRemoteWire))
        XCTAssertTrue(legacyHints.omittedMilestone)
        _ = try dbRestored.applyRemoteDeltas(
            RemoteDeltaBatch(
                tasks: [legacyHints.task],
                taskOmittedMilestoneTaskIds: legacyHints.omittedMilestone ? [legacyHints.task.id] : []
            )
        )
        restoredSnap = try dbRestored.loadWorkspaceSnapshot(calendar: cal)
        let afterLegacySync = try XCTUnwrap(restoredSnap.allOpenTasks.first { $0.id == milestoneTask.id })
        XCTAssertEqual(afterLegacySync.title, "App Store Submission Freeze (Edited on Legacy Client)")
        XCTAssertTrue(afterLegacySync.isMilestone, "Omitted remote isMilestone must preserve local milestone flag")

        var explicitRemoteWire = legacyRemoteWire
        explicitRemoteWire["isMilestone"] = false
        explicitRemoteWire["updatedAt"] = afterLegacySync.updatedAt + 5_000
        let explicitHints = try XCTUnwrap(FirebaseCloudSyncService.parseTaskWithHints(explicitRemoteWire))
        XCTAssertFalse(explicitHints.omittedMilestone)
        XCTAssertFalse(explicitHints.task.isMilestone)
        _ = try dbRestored.applyRemoteDeltas(RemoteDeltaBatch(tasks: [explicitHints.task]))
        restoredSnap = try dbRestored.loadWorkspaceSnapshot(calendar: cal)
        XCTAssertFalse(try XCTUnwrap(restoredSnap.allOpenTasks.first { $0.id == milestoneTask.id }).isMilestone)

        // 4. Asana JSON "resource_subtype": "milestone" & "is_milestone": true import + target Area + export
        let asanaJson = """
        {
          "data": {
            "name": "Asana Cloud Migration",
            "notes": "Imported with milestones",
            "sections": [
              {
                "name": "Cutover",
                "tasks": [
                  {
                    "name": "Zero-Downtime DNS Switchover",
                    "resource_subtype": "milestone",
                    "due_on": "2026-09-15",
                    "completed": false
                  },
                  {
                    "name": "Post-Cutover Signoff",
                    "is_milestone": true,
                    "due_on": "2026-09-18",
                    "completed": false
                  },
                  {
                    "name": "Update runbooks",
                    "resource_subtype": "default_task",
                    "start_on": "2026-09-10",
                    "due_on": "2026-09-14",
                    "completed": false
                  }
                ]
              }
            ]
          }
        }
        """
        let parsedAsana = try ExternalProjectCodec.importAsanaJson(Data(asanaJson.utf8), calendar: cal)
        let cutoverTasks = try XCTUnwrap(parsedAsana.phases.first?.tasks)
        XCTAssertEqual(cutoverTasks.count, 3)
        XCTAssertTrue(cutoverTasks[0].isMilestone)
        XCTAssertTrue(cutoverTasks[1].isMilestone)
        XCTAssertFalse(cutoverTasks[2].isMilestone)

        let dbAsana = try ThingsSQLiteDatabase(path: ":memory:")
        let targetArea = try dbAsana.createArea(title: "Infrastructure")
        let importedResult = try dbAsana.importExternalProject(
            parsedAsana,
            areaId: targetArea.id,
            calendar: cal
        )
        XCTAssertEqual(importedResult.project.areaId, targetArea.id)
        let asanaSnap = try dbAsana.loadWorkspaceSnapshot(calendar: cal)
        let persistedSwitchover = try XCTUnwrap(asanaSnap.allOpenTasks.first { $0.title == "Zero-Downtime DNS Switchover" })
        let persistedSignoff = try XCTUnwrap(asanaSnap.allOpenTasks.first { $0.title == "Post-Cutover Signoff" })
        let persistedRunbooks = try XCTUnwrap(asanaSnap.allOpenTasks.first { $0.title == "Update runbooks" })
        XCTAssertEqual(persistedSwitchover.areaId, targetArea.id)
        XCTAssertTrue(persistedSwitchover.isMilestone)
        XCTAssertTrue(persistedSignoff.isMilestone)
        XCTAssertFalse(persistedRunbooks.isMilestone)

        let exportedAsana = try dbAsana.exportProjectExternal(
            projectId: importedResult.project.id,
            format: .asanaJson,
            calendar: cal
        )
        let rawExportObj = try XCTUnwrap(JSONSerialization.jsonObject(with: exportedAsana.data) as? [String: Any])
        let exportedSectionTasks = try XCTUnwrap(rawExportObj["data"] as? [[String: Any]])
        let switchoverJson = try XCTUnwrap(exportedSectionTasks.first { ($0["name"] as? String) == "Zero-Downtime DNS Switchover" })
        let runbooksJson = try XCTUnwrap(exportedSectionTasks.first { ($0["name"] as? String) == "Update runbooks" })
        XCTAssertEqual(switchoverJson["resource_subtype"] as? String, "milestone")
        XCTAssertEqual(runbooksJson["resource_subtype"] as? String, "default_task")

        let reimportedAsana = try ExternalProjectCodec.importAsanaJson(exportedAsana.data, calendar: cal)
        let reimportedTasks = try XCTUnwrap(reimportedAsana.phases.first?.tasks)
        XCTAssertTrue(reimportedTasks.first(where: { $0.title == "Zero-Downtime DNS Switchover" })?.isMilestone == true)
        XCTAssertTrue(reimportedTasks.first(where: { $0.title == "Update runbooks" })?.isMilestone == false)

        // 5. @milestone inline token parsing in parseOutlineToBlueprint
        let outline = """
        # Release Schedule
        ## Milestones
        - [ ] Feature Complete @milestone
        - [ ] Go / No-Go Review @09:30 @1d @Milestone
        - [ ] Draft changelog @45m
        """
        let parsedOutline = ThingsSQLiteDatabase.parseOutlineToBlueprint(outline)
        let outlineTasks = try XCTUnwrap(parsedOutline.blueprint.headings.first?.tasks)
        XCTAssertEqual(outlineTasks.count, 3)
        XCTAssertEqual(outlineTasks[0].title, "Feature Complete")
        XCTAssertTrue(outlineTasks[0].isMilestone)
        XCTAssertEqual(outlineTasks[1].title, "Go / No-Go Review")
        XCTAssertTrue(outlineTasks[1].isMilestone)
        XCTAssertEqual(outlineTasks[1].scheduledStartMinutes, 9 * 60 + 30)
        XCTAssertEqual(outlineTasks[1].durationMinutes, 480)
        XCTAssertEqual(outlineTasks[2].title, "Draft changelog")
        XCTAssertFalse(outlineTasks[2].isMilestone)
    }

    @MainActor
    func testMilestoneRoadmapLayoutSingleDayAnchoringAndVectorExports() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let todayMs: Int64 = 1_795_046_400_000
        let dayMs: Int64 = 86_400_000
        let todayDay = DateEpochConverter.epochDay(forMillis: todayMs, calendar: cal)

        let project = Project(
            id: "proj-ms-roadmap",
            title: "Milestone Gantt Verification",
            viewMode: "roadmap",
            totalTaskCount: 5,
            completedTaskCount: 1,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        let phase = Heading(
            id: "hdg-ms-1",
            projectId: project.id,
            title: "Phase 1",
            sortOrder: 100,
            startDate: todayMs,
            endDate: todayMs + 12 * dayMs,
            createdAt: todayMs,
            updatedAt: todayMs
        )

        let regularSpanTask = TaskItem(
            id: "ms-regular",
            title: "Implementation Sprint",
            scheduleState: .today,
            whenDate: todayMs,
            deadline: todayMs + 4 * dayMs,
            projectId: project.id,
            headingId: phase.id,
            sortOrder: 10,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        // Has both earlier whenDate (+2d), multi-day duration, and deadline (+7d) -> must anchor as 1-day point on deadline (+7d)
        let deadlineAnchoredMilestone = TaskItem(
            id: "ms-both-dates",
            title: "Security Audit Sign-Off",
            scheduleState: .upcoming,
            whenDate: todayMs + 2 * dayMs,
            deadline: todayMs + 7 * dayMs,
            durationMinutes: 3 * 480,
            projectId: project.id,
            headingId: phase.id,
            sortOrder: 20,
            isMilestone: true,
            blockedByTaskIds: [regularSpanTask.id],
            createdAt: todayMs,
            updatedAt: todayMs
        )
        // Has only whenDate (+5d) -> must anchor as 1-day point on whenDate (+5d)
        let whenOnlyMilestone = TaskItem(
            id: "ms-when-only",
            title: "Internal Beta Tag",
            status: .completed,
            scheduleState: .upcoming,
            whenDate: todayMs + 5 * dayMs,
            completedAt: todayMs,
            projectId: project.id,
            headingId: phase.id,
            sortOrder: 30,
            isMilestone: true,
            createdAt: todayMs,
            updatedAt: todayMs
        )
        // Has only deadline (+10d) -> must anchor as 1-day point on deadline (+10d)
        let deadlineOnlyMilestone = TaskItem(
            id: "ms-deadline-only",
            title: "Public Launch Day",
            scheduleState: .anytime,
            deadline: todayMs + 10 * dayMs,
            projectId: project.id,
            headingId: phase.id,
            sortOrder: 40,
            isMilestone: true,
            blockedByTaskIds: [deadlineAnchoredMilestone.id],
            createdAt: todayMs,
            updatedAt: todayMs
        )
        // Unscheduled milestone -> stays in unscheduledTasks
        let unscheduledMilestone = TaskItem(
            id: "ms-unscheduled",
            title: "Post-Launch Retro",
            scheduleState: .anytime,
            projectId: project.id,
            headingId: phase.id,
            sortOrder: 50,
            isMilestone: true,
            createdAt: todayMs,
            updatedAt: todayMs
        )

        let layout = ProjectRoadmapEngine.buildLayout(
            project: project,
            headings: [phase],
            tasks: [
                regularSpanTask,
                deadlineAnchoredMilestone,
                whenOnlyMilestone,
                deadlineOnlyMilestone,
                unscheduledMilestone
            ],
            todayMillis: todayMs,
            calendar: cal
        )

        let barById = Dictionary(uniqueKeysWithValues: layout.allTaskBars.map { ($0.id, $0) })
        let regularBar = try XCTUnwrap(barById[regularSpanTask.id])
        XCTAssertFalse(regularBar.isMilestone)
        XCTAssertEqual(regularBar.spanDays, 5)

        let bothBar = try XCTUnwrap(barById[deadlineAnchoredMilestone.id])
        XCTAssertTrue(bothBar.isMilestone)
        XCTAssertEqual(bothBar.startEpochDay, todayDay + 7)
        XCTAssertEqual(bothBar.endEpochDay, todayDay + 7)
        XCTAssertEqual(bothBar.spanDays, 1)

        let whenBar = try XCTUnwrap(barById[whenOnlyMilestone.id])
        XCTAssertTrue(whenBar.isMilestone)
        XCTAssertEqual(whenBar.startEpochDay, todayDay + 5)
        XCTAssertEqual(whenBar.endEpochDay, todayDay + 5)
        XCTAssertEqual(whenBar.spanDays, 1)

        let dlBar = try XCTUnwrap(barById[deadlineOnlyMilestone.id])
        XCTAssertTrue(dlBar.isMilestone)
        XCTAssertEqual(dlBar.startEpochDay, todayDay + 10)
        XCTAssertEqual(dlBar.endEpochDay, todayDay + 10)
        XCTAssertEqual(dlBar.spanDays, 1)

        XCTAssertEqual(layout.unscheduledTasks.map(\.id), [unscheduledMilestone.id])
        XCTAssertTrue(layout.unscheduledTasks.first?.isMilestone == true)

        // Verify filteredLayoutForExport preserves isMilestone
        let exportOptions = RoadmapPDFExportOptions(
            selectedSectionIds: [phase.id],
            startEpochDay: todayDay,
            endEpochDay: todayDay + 14,
            orientation: .landscape
        )
        let filtered = ProjectRoadmapEngine.filteredLayoutForExport(
            layout: layout,
            headings: [phase],
            options: exportOptions
        )
        XCTAssertEqual(filtered.allTaskBars.filter(\.isMilestone).count, 3)

        // Verify PDF & PNG vector exports succeed with royal-violet Milestone diamonds + labels
        let pdfData = try XCTUnwrap(
            ProjectRoadmapExporter.exportRoadmapPDFData(
                project: project,
                headings: [phase],
                layout: layout,
                options: exportOptions
            )
        )
        XCTAssertTrue(pdfData.starts(with: Data("%PDF-".utf8)))
        XCTAssertGreaterThan(pdfData.count, 256)

        let pngData = try XCTUnwrap(
            ProjectRoadmapExporter.exportRoadmapPNGData(
                project: project,
                headings: [phase],
                layout: layout,
                scale: 2.0
            )
        )
        let pngMagic: [UInt8] = [0x89, 0x50, 0x4E, 0x47]
        XCTAssertTrue(pngData.starts(with: pngMagic))
        XCTAssertGreaterThan(pngData.count, 256)
    }

    // MARK: - v3.0.0-beta.17 Wide-Screen & Inspector Localization Parity

    func testV30Beta17InspectorLocalizationKeysAllLanguages() {
        let strings = ThingsStrings.shared
        let inspectorKeys = [
            "v30_inspector_title",
            "v30_inspector_overview_title",
            "v30_inspector_upcoming_deadlines",
            "v30_inspector_toggle_tooltip"
        ]

        let explicitLanguages: [AppLanguage] = [.en, .de, .fr, .it, .es]
        for lang in explicitLanguages {
            let langKeys = strings.keys(for: lang.rawValue)
            for key in inspectorKeys {
                XCTAssertTrue(langKeys.contains(key), "Missing \(key) in \(lang.rawValue) table")
                let resolved = strings.text(key, lang: lang)
                XCTAssertFalse(resolved.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, "Empty value for \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(resolved, key, "Key \(key) leaked raw identifier in \(lang.rawValue)")
            }
        }

        for lang in AppLanguage.allCases {
            for key in inspectorKeys {
                let resolved = strings.text(key, lang: lang)
                XCTAssertFalse(resolved.isEmpty, "Empty value for \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(resolved, key, "Key \(key) leaked raw identifier in \(lang.rawValue)")
            }
        }

        XCTAssertEqual(strings.text("v30_inspector_title", lang: .en), "Inspector")
        XCTAssertEqual(strings.text("v30_inspector_title", lang: .de), "Inspektor")
        XCTAssertEqual(strings.text("v30_inspector_title", lang: .fr), "Inspecteur")
        XCTAssertEqual(strings.text("v30_inspector_title", lang: .it), "Ispettore")
        XCTAssertEqual(strings.text("v30_inspector_title", lang: .es), "Inspector")

        XCTAssertEqual(strings.text("v30_inspector_overview_title", lang: .en), "Overview")
        XCTAssertEqual(strings.text("v30_inspector_overview_title", lang: .de), "Übersicht")
        XCTAssertEqual(strings.text("v30_inspector_overview_title", lang: .fr), "Aperçu")
        XCTAssertEqual(strings.text("v30_inspector_overview_title", lang: .it), "Panoramica")
        XCTAssertEqual(strings.text("v30_inspector_overview_title", lang: .es), "Resumen")
    }

    // MARK: - v3.0.0-beta.18 Sidebar Perspectives Settings & 4h / 8h Effort Durations

    func testV30Beta18PerspectivesSettingsLocalizationAndEffortDurations() {
        let strings = ThingsStrings.shared
        let perspectiveSettingsKeys = [
            "v30_settings_show_perspectives",
            "v30_settings_show_perspectives_desc"
        ]

        let explicitLanguages: [AppLanguage] = [.en, .de, .fr, .it, .es]
        for lang in explicitLanguages {
            let langKeys = strings.keys(for: lang.rawValue)
            for key in perspectiveSettingsKeys {
                XCTAssertTrue(langKeys.contains(key), "Missing \(key) in \(lang.rawValue) table")
                let resolved = strings.text(key, lang: lang)
                XCTAssertFalse(resolved.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, "Empty value for \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(resolved, key, "Key \(key) leaked raw identifier in \(lang.rawValue)")
            }
        }

        for lang in AppLanguage.allCases {
            for key in perspectiveSettingsKeys {
                let resolved = strings.text(key, lang: lang)
                XCTAssertFalse(resolved.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, "Empty value for \(key) in \(lang.rawValue)")
                XCTAssertNotEqual(resolved, key, "Key \(key) leaked raw identifier in \(lang.rawValue)")
            }
        }

        XCTAssertEqual(strings.text("v30_settings_show_perspectives", lang: .en), "Show Custom Perspectives in Sidebar")
        XCTAssertEqual(strings.text("v30_settings_show_perspectives", lang: .de), "Eigene Perspektiven in der Seitenleiste anzeigen")
        XCTAssertEqual(strings.text("v30_settings_show_perspectives", lang: .fr), "Afficher les perspectives personnalisées dans la barre latérale")
        XCTAssertEqual(strings.text("v30_settings_show_perspectives", lang: .it), "Mostra prospettive personalizzate nella barra laterale")
        XCTAssertEqual(strings.text("v30_settings_show_perspectives", lang: .es), "Mostrar perspectivas personalizadas en la barra lateral")

        XCTAssertEqual(strings.text("v30_settings_show_perspectives_desc", lang: .en), "Adds a saved-filters section to the sidebar for custom multi-criteria views")
        XCTAssertEqual(strings.text("v30_settings_show_perspectives_desc", lang: .de), "Fügt der Seitenleiste einen Bereich für gespeicherte Mehrfachfilter hinzu")
        XCTAssertEqual(strings.text("v30_settings_show_perspectives_desc", lang: .fr), "Ajoute une section de filtres enregistrés à la barre latérale")
        XCTAssertEqual(strings.text("v30_settings_show_perspectives_desc", lang: .it), "Aggiunge una sezione di filtri salvati alla barra laterale")
        XCTAssertEqual(strings.text("v30_settings_show_perspectives_desc", lang: .es), "Añade una sección de filtros guardados a la barra lateral")

        // Verify standalone duration presets & compact formatting for 4h (240m), 8h (480m), and multi-day (> 480m)
        XCTAssertEqual(TaskItem.standaloneDurationPresets, [5, 15, 30, 60, 120, 240, 480])
        XCTAssertEqual(TaskItem.formatCompactDuration(240), "4h")
        XCTAssertEqual(TaskItem.formatCompactDuration(480), "8h")
        XCTAssertEqual(TaskItem.formatCompactDuration(720), "1d 4h")
        XCTAssertEqual(TaskItem.formatCompactDuration(960), "2d")
        XCTAssertEqual(TaskItem.formatCompactDuration(2400), "5d")

        // Verify PerspectiveFilterEngine maxDurationMinutes supports <= 4h (240m) and <= 8h (480m)
        let tasks = [
            TaskItem(id: "t-2h", title: "2h Task", durationMinutes: 120, createdAt: 1, updatedAt: 1),
            TaskItem(id: "t-4h", title: "4h Task", durationMinutes: 240, createdAt: 1, updatedAt: 1),
            TaskItem(id: "t-8h", title: "8h Task", durationMinutes: 480, createdAt: 1, updatedAt: 1),
            TaskItem(id: "t-2d", title: "2d Task", durationMinutes: 960, createdAt: 1, updatedAt: 1)
        ]
        let p4h = SavedPerspective(id: "p-4h", title: "Half Day", maxDurationMinutes: 240)
        let p8h = SavedPerspective(id: "p-8h", title: "Full Day", maxDurationMinutes: 480)
        let matched4h = PerspectiveFilterEngine.filter(
            tasks: tasks,
            perspective: p4h,
            openTaskIds: Set(tasks.map(\.id)),
            todayEpochMillis: 1_795_046_400_000,
            projectsById: [:]
        )
        let matched8h = PerspectiveFilterEngine.filter(
            tasks: tasks,
            perspective: p8h,
            openTaskIds: Set(tasks.map(\.id)),
            todayEpochMillis: 1_795_046_400_000,
            projectsById: [:]
        )
        XCTAssertEqual(matched4h.map(\.id), ["t-2h", "t-4h"])
        XCTAssertEqual(matched8h.map(\.id), ["t-2h", "t-4h", "t-8h"])
    }

    // MARK: - v3.0.0 GA: Promote Ungrouped ("Allgemein" / "General") Tasks, Orphan Placeholder Import & Timeline Mouse Tooltips

    func testV300PromoteUngroupedTasksToHeadingAndSkipEmptyPlaceholderSections() throws {
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let project = try db.createProject(title: "Website Relaunch")
        let otherProject = try db.createProject(title: "Other Project")

        let phaseA = try db.createHeading(projectId: project.id, title: "Design", sortOrder: 1000.0)
        let phaseB = try db.createHeading(projectId: project.id, title: "Engineering", sortOrder: 2000.0)

        let ungrouped1 = try db.createTask(
            title: "Kickoff Brief",
            scheduleState: .anytime,
            projectId: project.id,
            headingId: nil
        )
        let ungrouped2 = try db.createTask(
            title: "Stakeholder Interview",
            scheduleState: .today,
            projectId: project.id,
            headingId: nil
        )
        let completedUngrouped = try db.createTask(
            title: "Old Completed Note",
            scheduleState: .anytime,
            projectId: project.id,
            headingId: nil
        )
        _ = try db.setTaskStatus(taskId: completedUngrouped.id, status: .completed)

        let deletedUngrouped = try db.createTask(
            title: "Trashed Draft",
            scheduleState: .anytime,
            projectId: project.id,
            headingId: nil
        )
        try db.softDeleteTask(deletedUngrouped.id)

        let headedTask = try db.createTask(
            title: "Figma Tokens",
            scheduleState: .anytime,
            projectId: project.id,
            headingId: phaseA.id
        )
        let otherProjTask = try db.createTask(
            title: "Unrelated Ungrouped",
            scheduleState: .anytime,
            projectId: otherProject.id,
            headingId: nil
        )

        // Blank / whitespace title is a safe no-op
        XCTAssertNil(try db.promoteUngroupedTasksToHeading(projectId: project.id, title: "   "))

        // Promoting ungrouped tasks creates a top-ordered Heading and moves only open ungrouped tasks in that project
        let promoted = try XCTUnwrap(
            db.promoteUngroupedTasksToHeading(projectId: project.id, title: "  Vorbereitung  ")
        )
        XCTAssertEqual(promoted.title, "Vorbereitung")
        XCTAssertEqual(promoted.projectId, project.id)
        XCTAssertEqual(promoted.sortOrder, phaseA.sortOrder - 1000.0)

        let snap = try db.loadWorkspaceSnapshot()
        let orderedHeadings = try XCTUnwrap(snap.headingsByProject[project.id])
        XCTAssertEqual(orderedHeadings.map(\.id), [promoted.id, phaseA.id, phaseB.id])

        let openInProject = snap.allOpenTasks.filter { $0.projectId == project.id }
        let remainingUngrouped = openInProject.filter { $0.headingId == nil }
        XCTAssertTrue(remainingUngrouped.isEmpty, "No open ungrouped tasks should remain after promotion")

        let promotedTasks = openInProject.filter { $0.headingId == promoted.id }
        XCTAssertEqual(Set(promotedTasks.map(\.id)), Set([ungrouped1.id, ungrouped2.id]))

        let untouchedHeaded = try XCTUnwrap(openInProject.first { $0.id == headedTask.id })
        XCTAssertEqual(untouchedHeaded.headingId, phaseA.id)

        let untouchedOtherProj = try XCTUnwrap(snap.allOpenTasks.first { $0.id == otherProjTask.id })
        XCTAssertNil(untouchedOtherProj.headingId)

        let loggedItem = try XCTUnwrap(snap.logbookTasks.first { $0.id == completedUngrouped.id })
        XCTAssertNil(loggedItem.headingId)

        // Verify Asana/Planner placeholder section filtering ("Untitled section", "Unnamed section", "(no section)")
        XCTAssertTrue(ExternalProjectCodec.isUngroupedSectionName("Untitled section"))
        XCTAssertTrue(ExternalProjectCodec.isUngroupedSectionName("UNNAMED SECTION"))
        XCTAssertTrue(ExternalProjectCodec.isUngroupedSectionName("Unnamed section:"))
        XCTAssertTrue(ExternalProjectCodec.isUngroupedSectionName("(no section)"))
        XCTAssertFalse(ExternalProjectCodec.isUngroupedSectionName("Phase 1"))

        let asanaJsonWithEmptyPlaceholders = """
        {
          "project": { "name": "Clean Asana Import" },
          "sections": [
            { "name": "Untitled section", "tasks": [] },
            { "name": "Unnamed section", "tasks": [] },
            { "name": "(no section)", "tasks": [] },
            {
              "name": "Delivery",
              "tasks": [
                { "name": "Ship v3.0", "completed": false }
              ]
            }
          ]
        }
        """
        let parsedAsana = try ExternalProjectCodec.importAsanaJson(Data(asanaJsonWithEmptyPlaceholders.utf8))
        XCTAssertEqual(parsedAsana.phases.map(\.title), ["Delivery"])
        XCTAssertTrue(parsedAsana.ungroupedTasks.isEmpty)

        // Also verify ThingsSQLiteDatabase.importExternalProject skips empty placeholder phases directly
        let syntheticImported = ExternalImportedProject(
            title: "Direct Import Check",
            ungroupedTasks: [],
            phases: [
                ExternalImportedPhase(title: "Unnamed section", tasks: []),
                ExternalImportedPhase(title: "Untitled section", tasks: []),
                ExternalImportedPhase(title: "(no section)", tasks: []),
                ExternalImportedPhase(
                    title: "Launch",
                    tasks: [ExternalImportedTask(title: "Publish Release Notes")]
                )
            ]
        )
        let persistedImport = try db.importExternalProject(syntheticImported)
        let snapAfterImport = try db.loadWorkspaceSnapshot()
        let importedHeadings = snapAfterImport.headingsByProject[persistedImport.project.id] ?? []
        XCTAssertEqual(importedHeadings.map(\.title), ["Launch"])
    }

    func testV300TimelineTaskTooltipFormatterAndMouseOnlyEligibility() {
        // Mouse-only & drag/button suppression guard
        XCTAssertTrue(
            TooltipTimingCoordinator.isEligibleMouseHover(
                isMousePointer: true,
                pressedMouseButtons: 0,
                isSuppressed: false
            )
        )
        XCTAssertFalse(
            TooltipTimingCoordinator.isEligibleMouseHover(
                isMousePointer: false,
                pressedMouseButtons: 0,
                isSuppressed: false
            ),
            "Touch / non-mouse pointer events must not trigger timeline hover tooltips"
        )
        XCTAssertFalse(
            TooltipTimingCoordinator.isEligibleMouseHover(
                isMousePointer: true,
                pressedMouseButtons: 1,
                isSuppressed: false
            ),
            "Pressed mouse buttons during click or drag must suppress tooltips"
        )
        XCTAssertFalse(
            TooltipTimingCoordinator.isEligibleMouseHover(
                isMousePointer: true,
                pressedMouseButtons: 0,
                isSuppressed: true
            ),
            "Explicit drag/popover suppression must suppress tooltips"
        )

        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let day10 = DateEpochConverter.epochDay(
            for: cal.date(from: DateComponents(year: 2026, month: 6, day: 10))!,
            calendar: cal
        )
        let day15 = DateEpochConverter.epochDay(
            for: cal.date(from: DateComponents(year: 2026, month: 6, day: 15))!,
            calendar: cal
        )
        let day10Ms = DateEpochConverter.startOfDayMillis(
            for: DateEpochConverter.date(fromEpochDay: day10, calendar: cal),
            calendar: cal
        )
        let day15Ms = DateEpochConverter.startOfDayMillis(
            for: DateEpochConverter.date(fromEpochDay: day15, calendar: cal),
            calendar: cal
        )

        let multiDayTask = TaskItem(
            id: "t-roadmap-1",
            title: "API Contract Freeze",
            status: .open,
            scheduleState: .upcoming,
            whenDate: day10Ms,
            deadline: day15Ms,
            durationMinutes: 480,
            isMilestone: false,
            effortLevel: TaskEffortLevel.deepFocus.rawValue,
            blockedByTaskIds: ["blocker-1"],
            checklistItems: [
                ChecklistItem(id: "c1", taskId: "t-roadmap-1", title: "Spec", isCompleted: true, createdAt: 1, updatedAt: 1),
                ChecklistItem(id: "c2", taskId: "t-roadmap-1", title: "Review", isCompleted: false, createdAt: 1, updatedAt: 1)
            ],
            createdAt: 1,
            updatedAt: 1
        )
        let bar = RoadmapTaskBar(
            task: multiDayTask,
            headingId: nil,
            startEpochDay: day10,
            endEpochDay: day15,
            isMilestone: false
        )
        let roadmapTipEn = TimelineTaskTooltipFormatter.formatRoadmapTask(
            taskBar: bar,
            isBlocked: true,
            language: .en
        )
        XCTAssertTrue(roadmapTipEn.hasPrefix("API Contract Freeze ⏳\n"))
        XCTAssertTrue(roadmapTipEn.contains("⚑"))
        XCTAssertTrue(roadmapTipEn.contains("8h"))
        XCTAssertTrue(roadmapTipEn.contains(ThingsStrings.shared.text("v30_effort_deep_focus", lang: .en)))
        XCTAssertTrue(roadmapTipEn.contains("1/2"))

        let milestoneTask = TaskItem(
            id: "t-ms-1",
            title: "GA Release",
            status: .completed,
            scheduleState: .today,
            deadline: day15Ms,
            isMilestone: true,
            createdAt: 1,
            updatedAt: 1
        )
        let msBar = RoadmapTaskBar(
            task: milestoneTask,
            headingId: nil,
            startEpochDay: day15,
            endEpochDay: day15,
            isMilestone: true
        )
        let msTipDe = TimelineTaskTooltipFormatter.formatRoadmapTask(
            taskBar: msBar,
            isBlocked: false,
            language: .de
        )
        XCTAssertTrue(msTipDe.hasPrefix("◆ GA Release ✓\n"))

        let todayTip = TimelineTaskTooltipFormatter.formatTodayTimelineTask(
            task: TaskItem(
                id: "t-today-1",
                title: "Deep Architecture Session",
                status: .open,
                scheduleState: .today,
                whenDate: day10Ms,
                scheduledStartMinutes: 9 * 60,
                durationMinutes: 240,
                effortLevel: TaskEffortLevel.medium.rawValue,
                createdAt: 1,
                updatedAt: 1
            ),
            startMinutes: 9 * 60,
            endMinutes: 13 * 60,
            projectTitle: "Platform 3.0",
            isBlocked: false,
            language: .en
        )
        XCTAssertTrue(todayTip.contains("Deep Architecture Session"))
        XCTAssertTrue(todayTip.contains("09:00–13:00"))
        XCTAssertTrue(todayTip.contains("4h"))
        XCTAssertTrue(todayTip.contains("Platform 3.0"))
    }

    // MARK: - 44. v3.1 Smart Push-on-Conflict Dependency Cascade & Undo Persistence

    func testV31SmartDependencyCascadeAndUndoPersistence() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!

        func millis(_ year: Int, _ month: Int, _ day: Int) -> Int64 {
            let d = cal.date(from: DateComponents(year: year, month: month, day: day))!
            return DateEpochConverter.startOfDayMillis(for: d, calendar: cal)
        }

        let todayMs = millis(2026, 11, 20)
        // t1: Nov 20..21 (2 days = 960m)
        // t2 (depends on t1): Nov 24 (1 day = 60m) -> 2 days slack (Nov 22, Nov 23)
        // t3 (milestone, depends on t2): Nov 25
        let t1 = TaskItem(
            id: "t1",
            title: "Core Schema",
            status: .open,
            scheduleState: .today,
            whenDate: millis(2026, 11, 20),
            durationMinutes: 960,
            projectId: "p1",
            createdAt: 1,
            updatedAt: 1
        )
        let t2 = TaskItem(
            id: "t2",
            title: "API Endpoints",
            status: .open,
            scheduleState: .upcoming,
            whenDate: millis(2026, 11, 24),
            durationMinutes: 60,
            projectId: "p1",
            blockedByTaskIds: ["t1"],
            createdAt: 1,
            updatedAt: 1
        )
        let t3 = TaskItem(
            id: "t3",
            title: "API Freeze Milestone",
            status: .open,
            scheduleState: .upcoming,
            whenDate: millis(2026, 11, 25),
            deadline: millis(2026, 11, 25),
            projectId: "p1",
            isMilestone: true,
            blockedByTaskIds: ["t2"],
            createdAt: 1,
            updatedAt: 1
        )

        // 1. Shift t1 by +1d -> ends Nov 22 < Nov 24 (slack preserved: t2 & t3 NOT pushed)
        let slackShifts = ProjectRoadmapEngine.computeSmartDependencyCascade(
            anchorTask: t1,
            deltaDays: 1,
            projectTasks: [t1, t2, t3],
            todayMillis: todayMs,
            calendar: cal
        )
        XCTAssertEqual(slackShifts.map(\.task.id), ["t1"])
        XCTAssertEqual(slackShifts.first?.isAnchor, true)

        // 2. Shift t1 by +4d -> t1 moves to Nov 24..25; overlaps t2 (Nov 24), pushing t2 by +2d to Nov 26,
        //    which in turn overlaps t3 (Nov 25), pushing t3 by +2d to Nov 27!
        let cascadeShifts = ProjectRoadmapEngine.computeSmartDependencyCascade(
            anchorTask: t1,
            deltaDays: 4,
            projectTasks: [t1, t2, t3],
            todayMillis: todayMs,
            calendar: cal
        )
        XCTAssertEqual(cascadeShifts.map(\.task.id), ["t1", "t2", "t3"])
        let shiftById = Dictionary(uniqueKeysWithValues: cascadeShifts.map { ($0.task.id, $0) })
        XCTAssertEqual(shiftById["t1"]?.deltaDays, 4)
        XCTAssertEqual(shiftById["t2"]?.deltaDays, 2)
        XCTAssertEqual(shiftById["t2"]?.nextWhenMillis, millis(2026, 11, 26))
        XCTAssertEqual(shiftById["t3"]?.deltaDays, 2)
        XCTAssertEqual(shiftById["t3"]?.nextDeadlineMillis, millis(2026, 11, 27))

        // 3. Shifting backward never pulls downstream tasks earlier
        let backwardShifts = ProjectRoadmapEngine.computeSmartDependencyCascade(
            anchorTask: t1,
            deltaDays: -2,
            projectTasks: [t1, t2, t3],
            todayMillis: todayMs,
            calendar: cal
        )
        XCTAssertEqual(backwardShifts.map(\.task.id), ["t1"])

        // 4. Cycle guard safety (corrupted mutual blockedBy)
        var cycA = t1
        cycA.blockedByTaskIds = ["t2"]
        let cycleSafe = ProjectRoadmapEngine.computeSmartDependencyCascade(
            anchorTask: cycA,
            deltaDays: 5,
            projectTasks: [cycA, t2],
            todayMillis: todayMs,
            calendar: cal
        )
        XCTAssertEqual(cycleSafe.count, 2)

        // 5. SQLite applyCascadedTaskShifts + restoreTaskPlacements round-trip
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let proj = try db.createProject(title: "Cascade Test")
        let dbT1 = try db.createTask(
            title: "DB Blocker",
            scheduleState: .today,
            whenDate: millis(2026, 11, 20),
            scheduledStartMinutes: 600,
            durationMinutes: 960,
            projectId: proj.id
        )
        let dbT2 = try db.createTask(
            title: "DB Dependent",
            scheduleState: .upcoming,
            whenDate: millis(2026, 11, 21),
            durationMinutes: 120,
            projectId: proj.id
        )
        try db.setTaskBlockedBy(taskId: dbT2.id, blockedByTaskIds: [dbT1.id])
        let snapBefore = try db.loadWorkspaceSnapshot()
        let shifts = ProjectRoadmapEngine.computeSmartDependencyCascade(
            anchorTask: try XCTUnwrap(snapBefore.allOpenTasks.first { $0.id == dbT1.id }),
            deltaDays: 2,
            projectTasks: snapBefore.allOpenTasks,
            todayMillis: todayMs,
            calendar: cal
        )
        XCTAssertEqual(shifts.count, 2)
        let undoSnapshots = try db.applyCascadedTaskShifts(shifts)
        XCTAssertEqual(undoSnapshots.count, 2)

        let snapShifted = try db.loadWorkspaceSnapshot()
        let shiftedDep = try XCTUnwrap(snapShifted.allOpenTasks.first { $0.id == dbT2.id })
        XCTAssertEqual(shiftedDep.whenDate, millis(2026, 11, 24))

        try db.restoreTaskPlacements(undoSnapshots)
        let snapRestored = try db.loadWorkspaceSnapshot()
        let restoredAnchor = try XCTUnwrap(snapRestored.allOpenTasks.first { $0.id == dbT1.id })
        let restoredDep = try XCTUnwrap(snapRestored.allOpenTasks.first { $0.id == dbT2.id })
        XCTAssertEqual(restoredAnchor.whenDate, millis(2026, 11, 20))
        XCTAssertEqual(restoredAnchor.scheduledStartMinutes, 600)
        XCTAssertEqual(restoredAnchor.durationMinutes, 960)
        XCTAssertEqual(restoredDep.whenDate, millis(2026, 11, 21))
    }

    // MARK: - 45. v3.1 Area Portfolio Timeline Layout & Backward-Compatible Preferences

    func testV31AreaPortfolioLayoutAndPreferencesCompatibility() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let todayDate = cal.date(from: DateComponents(year: 2026, month: 11, day: 20))!
        let todayMs = DateEpochConverter.startOfDayMillis(for: todayDate, calendar: cal)
        let todayDay = DateEpochConverter.epochDay(forMillis: todayMs, calendar: cal)

        let area = Area(id: "area-eng", title: "Engineering", sortOrder: 10, createdAt: 1, updatedAt: 1)
        let scheduledProj = Project(
            id: "proj-scheduled",
            areaId: area.id,
            title: "macOS v3.1",
            scheduleState: .today,
            whenDate: todayMs,
            deadline: todayMs + 10 * 86_400_000,
            sortOrder: 10,
            totalTaskCount: 2,
            completedTaskCount: 1,
            createdAt: 1,
            updatedAt: 1
        )
        let unscheduledProj = Project(
            id: "proj-unscheduled",
            areaId: area.id,
            title: "Backlog Ideas",
            scheduleState: .anytime,
            sortOrder: 20,
            createdAt: 2,
            updatedAt: 2
        )
        let phase = Heading(
            id: "h1",
            projectId: scheduledProj.id,
            title: "Phase 1",
            sortOrder: 100,
            startDate: todayMs,
            endDate: todayMs + 5 * 86_400_000,
            createdAt: 1,
            updatedAt: 1
        )
        let msTask = TaskItem(
            id: "ms-1",
            title: "Beta Freeze",
            status: .open,
            scheduleState: .upcoming,
            whenDate: todayMs + 5 * 86_400_000,
            deadline: todayMs + 5 * 86_400_000,
            projectId: scheduledProj.id,
            headingId: phase.id,
            isMilestone: true,
            createdAt: 1,
            updatedAt: 1
        )

        let portfolio = ProjectRoadmapEngine.buildAreaPortfolioLayout(
            area: area,
            projects: [scheduledProj, unscheduledProj],
            headings: [phase],
            tasks: [msTask],
            todayMillis: todayMs,
            calendar: cal
        )

        XCTAssertEqual(portfolio.projectLanes.map(\.projectId), [scheduledProj.id])
        XCTAssertEqual(portfolio.unscheduledProjects.map(\.id), [unscheduledProj.id])
        let lane = try XCTUnwrap(portfolio.projectLanes.first)
        XCTAssertEqual(lane.summaryStartEpochDay, todayDay)
        XCTAssertEqual(lane.summaryEndEpochDay, todayDay + 10)
        XCTAssertEqual(lane.spanDays, 11)
        XCTAssertEqual(lane.milestoneBars.map(\.id), [msTask.id])
        XCTAssertGreaterThanOrEqual(portfolio.totalDays, 21)

        // Legacy v3.0 AppPreferences JSON without areaViewModes / workdayStartMinutes decodes cleanly
        let legacyJson = #"{"localOnlyDbMode":false,"sidebarVisible":true}"#
        let decodedLegacy = try JSONDecoder().decode(AppPreferences.self, from: Data(legacyJson.utf8))
        XCTAssertEqual(decodedLegacy.areaViewModes, [:])
        XCTAssertEqual(decodedLegacy.workdayStartMinutes, 540)
        XCTAssertEqual(decodedLegacy.workdayEndMinutes, 1020)
    }

    // MARK: - 46. v3.1 Configurable Workday Hours, 7-Day Workload Forecast & Backup Roundtrip

    func testV31ConfigurableWorkdayHoursSevenDayForecastAndBackup() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let todayDate = cal.date(from: DateComponents(year: 2026, month: 11, day: 20))!
        let sodMs = DateEpochConverter.startOfDayMillis(for: todayDate, calendar: cal)
        let nextSodMs = sodMs + 86_400_000
        let todayDay = DateEpochConverter.epochDay(forMillis: sodMs, calendar: cal)

        // Custom workday: 08:00 (480) to 18:00 (1080) = 600m daily capacity
        // Calendar event 07:00..09:00 -> clipped to 08:00..09:00 = 60m busy
        let earlyMorningMeeting = CalendarEventItem(
            id: "ev-early",
            eventIdentifier: "ev-early",
            calendarId: "cal-work",
            calendarDisplayName: "Work",
            title: "Global Sync",
            startMillis: sodMs + 7 * 3_600_000,
            endMillis: sodMs + 9 * 3_600_000,
            isAllDay: false,
            colorHex: 0x2B79C2,
            startDateEpochDay: todayDay,
            endDateInclusiveEpochDay: todayDay
        )
        let todayTask = TaskItem(
            id: "t-today",
            title: "Feature Build",
            status: .open,
            scheduleState: .today,
            whenDate: sodMs,
            durationMinutes: 180,
            createdAt: 1,
            updatedAt: 1
        )
        let overloadedTomorrowTask = TaskItem(
            id: "t-tmrw",
            title: "Full Day Workshop",
            status: .open,
            scheduleState: .upcoming,
            whenDate: sodMs + 86_400_000,
            deadline: sodMs + 86_400_000,
            durationMinutes: 660, // 11h > 10h workday -> overloaded by 60m!
            createdAt: 1,
            updatedAt: 1
        )

        let cap = TodayCapacityCalculator.compute(
            tasks: [todayTask],
            calendarEvents: [earlyMorningMeeting],
            dayStartMillis: sodMs,
            dayEndMillis: nextSodMs,
            workdayStartMinutes: 480,
            workdayEndMinutes: 1080
        )
        XCTAssertEqual(cap.plannedMinutes, 180)
        XCTAssertEqual(cap.freeMinutes, 360) // 600 - 60 - 180

        let forecast = TodayCapacityCalculator.computeSevenDayForecast(
            todayTasks: [todayTask],
            allOpenTasks: [todayTask, overloadedTomorrowTask],
            calendarEvents: [earlyMorningMeeting],
            todayMillis: sodMs,
            daysCount: 7,
            workdayStartMinutes: 480,
            workdayEndMinutes: 1080,
            calendar: cal
        )
        XCTAssertEqual(forecast.count, 7)
        XCTAssertEqual(forecast[0].plannedTaskMinutes, 180)
        XCTAssertEqual(forecast[0].busyCalendarMinutes, 60)
        XCTAssertEqual(forecast[0].freeWorkdayMinutes, 360)
        XCTAssertFalse(forecast[0].isOverloaded)
        XCTAssertTrue(forecast[1].isOverloaded)
        XCTAssertEqual(forecast[1].overloadedByMinutes, 60)

        // BackupPreferencesDto roundtrip preserves workdayStartMinutes & workdayEndMinutes
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let customPrefs = AppPreferences(workdayStartMinutes: 510, workdayEndMinutes: 1050)
        let backupPayload = try db.buildBackupPayload(preferences: customPrefs)
        let encoded = try ThingsBackupCodec.encodeBackup(payload: backupPayload, password: nil)
        let decoded = try ThingsBackupCodec.decodeBackup(rawBytes: encoded, password: nil)
        XCTAssertEqual(decoded.preferences.workdayStartMinutes, 510)
        XCTAssertEqual(decoded.preferences.workdayEndMinutes, 1050)
    }

    // MARK: - 47. v3.1 Gemini Roadmap Architect, Deterministic Plan My Day & 5-Language Keys

    func testV31GeminiBlueprintEnrichmentAndPlanMyDayAutoTimeblocker() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let refDate = cal.date(from: DateComponents(year: 2026, month: 11, day: 20))!

        // 1. Offline localized blueprints across all 5 languages include dated phases, milestones & dependencies
        for lang in ["en", "de", "fr", "it", "es"] {
            let bp = ThingsbookGeminiService.offlineLocalizedProjectBlueprint(goal: "Ship v3.1", langCode: lang)
            XCTAssertEqual(bp.headings.count, 3)
            let allRich = bp.headings.flatMap(\.richTasks)
            XCTAssertGreaterThanOrEqual(allRich.count, 9)
            XCTAssertTrue(allRich.contains(where: \.isMilestone), "Expected milestone in \(lang) blueprint")
            XCTAssertTrue(allRich.contains { !$0.dependsOnKeys.isEmpty }, "Expected dependencies in \(lang) blueprint")
            let refl = ThingsbookGeminiService.offlineLocalizedLogbookReflection(count: 12, langCode: lang)
            XCTAssertEqual(refl.suggestedFocusTasks.count, 3)
        }

        // 2. createProjectFromBlueprint creates a roadmap project with dated headings, milestones, dependencies & checklists
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let blueprint = ThingsbookGeminiService.offlineLocalizedProjectBlueprint(goal: "Launch Portfolio", langCode: "en")
        let createdProj = try db.createProjectFromBlueprint(
            blueprint,
            languageCode: "en",
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertEqual(createdProj.viewMode, "roadmap")

        let snap = try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        let projHeadings = try XCTUnwrap(snap.headingsByProject[createdProj.id])
        XCTAssertEqual(projHeadings.count, 3)
        XCTAssertTrue(projHeadings.allSatisfy { $0.startDate != nil && $0.endDate != nil })

        let projTasks = snap.allOpenTasks.filter { $0.projectId == createdProj.id }
        XCTAssertEqual(projTasks.count, 9)
        XCTAssertTrue(projTasks.contains { $0.isMilestone && $0.deadline != nil })
        XCTAssertTrue(projTasks.contains { !$0.blockedByTaskIds.isEmpty })
        XCTAssertTrue(projTasks.contains { !$0.checklistItems.isEmpty })

        // 3. Deterministic "Plan My Day" scheduler avoids busy intervals & prioritizes pinned/deep-focus tasks
        let candidates = [
            DayTimeblockCandidate(taskId: "qw", title: "Reply to emails", durationMinutes: 30, effortLevel: "QUICK_WIN"),
            DayTimeblockCandidate(taskId: "pin", title: "Critical Review", durationMinutes: 60, effortLevel: "DEEP_FOCUS", isPinned: true),
            DayTimeblockCandidate(taskId: "med", title: "Spec Draft", durationMinutes: 45, effortLevel: "MEDIUM", hasTodayDeadline: true)
        ]
        // Busy meeting 09:00..10:00 (540..600)
        let busy = [(540, 600)]
        let plan = ThingsbookGeminiService.buildDeterministicDayTimeblockPlan(
            candidates: candidates,
            busyIntervalsMinutes: busy,
            workdayStartMinutes: 540,
            workdayEndMinutes: 1020,
            earliestStartMinutes: 540,
            langCode: "de"
        )
        XCTAssertEqual(plan.blocks.count, 3)
        XCTAssertFalse(plan.summary.isEmpty)
        for block in plan.blocks {
            let bEnd = block.startMinutes + block.durationMinutes
            XCTAssertFalse(block.startMinutes < 600 && 540 < bEnd, "Block overlapped busy calendar event")
            XCTAssertEqual(block.startMinutes % 15, 0, "Block start must align to 15m grid")
        }
        // First placed block after 10:00 should be the pinned task
        XCTAssertEqual(plan.blocks.first?.taskId, "pin")
        XCTAssertEqual(plan.blocks.first?.startMinutes, 600)

        // 4. applyBatchTaskTimeblocks + restoreTaskPlacements undo round-trip
        let taskForPlan = try XCTUnwrap(projTasks.first)
        let origStart = taskForPlan.scheduledStartMinutes
        let origDur = taskForPlan.durationMinutes
        let tbSnaps = try db.applyBatchTaskTimeblocks(
            [(taskId: taskForPlan.id, startMinutes: 630, durationMinutes: 45)],
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertEqual(tbSnaps.count, 1)
        let afterPlan = try XCTUnwrap(try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal).allOpenTasks.first { $0.id == taskForPlan.id })
        XCTAssertEqual(afterPlan.scheduledStartMinutes, 630)
        XCTAssertEqual(afterPlan.durationMinutes, 45)

        try db.restoreTaskPlacements(tbSnaps)
        let afterUndo = try XCTUnwrap(try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal).allOpenTasks.first { $0.id == taskForPlan.id })
        XCTAssertEqual(afterUndo.scheduledStartMinutes, origStart)
        XCTAssertEqual(afterUndo.durationMinutes, origDur)

        // 5. All 25 v31_* localization keys exist across all 5 languages
        let v31Keys = [
            "v31_area_portfolio_timeline", "v31_area_expand_all", "v31_area_collapse_all",
            "v31_area_open_project", "v31_area_unscheduled_projects", "v31_area_no_projects_timeline",
            "v31_roadmap_cascade_undo", "v31_roadmap_rescheduled_undo",
            "v31_settings_workday_hours", "v31_settings_workday_desc", "v31_settings_workday_start",
            "v31_settings_workday_end", "v31_settings_workday_daily_capacity",
            "v31_workload_7day_title", "v31_workload_overloaded_by", "v31_workload_overloaded_short",
            "v31_workload_balanced", "v31_gemini_architect_v3_hint", "v31_gemini_plan_day_tab",
            "v31_gemini_plan_day_desc", "v31_gemini_plan_day_generate", "v31_gemini_plan_day_apply",
            "v31_gemini_plan_day_empty", "v31_gemini_plan_day_applied_undo", "v31_gemini_plan_day_replan"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in v31Keys {
                let text = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertNotEqual(text, key, "Missing v31 key \(key) in \(lang.rawValue)")
                XCTAssertFalse(text.isEmpty)
            }
        }
    }

    // MARK: - 48. v3.2 SmartSyntaxParser (5 Languages + URL Guard) & actualMinutes Persistence / Sync / Backup

    func testV32SmartSyntaxParserMultilingualAndUrlPreservation() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let refDate = cal.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: 9))! // Monday
        let todayMs = DateEpochConverter.startOfDayMillis(for: refDate, calendar: cal)
        let dayMs: Int64 = 86_400_000

        let tags = [
            Tag(id: "tag-work", title: "Work", createdAt: 1, updatedAt: 1),
            Tag(id: "tag-deep", title: "Deep Work", createdAt: 1, updatedAt: 1),
            Tag(id: "tag-deleted", title: "Obsolete", createdAt: 1, updatedAt: 1, isDeleted: true)
        ]
        let areas = [
            Area(id: "area-eng", title: "Engineering", sortOrder: 10, createdAt: 1, updatedAt: 1),
            Area(id: "area-life", title: "Personal Life", sortOrder: 20, createdAt: 1, updatedAt: 1)
        ]
        let projects = [
            Project(id: "proj-launch", areaId: "area-eng", title: "Product Launch", status: .active, createdAt: 1, updatedAt: 1),
            Project(id: "proj-done", areaId: "area-eng", title: "Done Project", status: .completed, createdAt: 1, updatedAt: 1)
        ]

        // 1. EN: URL + fraction + @this-evening + !fri + ~1.5h + #deep-work + /Product + !milestone
        let en = SmartSyntaxParser.parse(
            "Review PR https://github.com/org/repo/pull/34 and 3/4 spec @this-evening !fri ~1.5h #deep-work /Product !milestone",
            tags: tags,
            projects: projects,
            areas: areas,
            locale: Locale(identifier: "en_US"),
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertTrue(en.hasAnyMatch)
        XCTAssertEqual(en.cleanTitle, "Review PR https://github.com/org/repo/pull/34 and 3/4 spec")
        XCTAssertEqual(en.scheduleState, .thisEvening)
        XCTAssertEqual(en.whenDate, todayMs)
        XCTAssertEqual(en.deadline, todayMs + 4 * dayMs) // Friday
        XCTAssertEqual(en.durationMinutes, 90)
        XCTAssertEqual(en.matchedTagIds, ["tag-deep"])
        XCTAssertEqual(en.matchedProject?.id, "proj-launch")
        XCTAssertNil(en.matchedArea)
        XCTAssertTrue(en.isMilestone)

        // 2. DE: @heute-abend + !morgen + ~45min + #"Deep Work" + /Personal-Life + ◆
        let de = SmartSyntaxParser.parse(
            "Quartalsbericht prüfen @heute-abend !morgen ~45min #\"Deep Work\" /Personal-Life ◆",
            tags: tags,
            projects: projects,
            areas: areas,
            locale: Locale(identifier: "de_DE"),
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertTrue(de.hasAnyMatch)
        XCTAssertEqual(de.cleanTitle, "Quartalsbericht prüfen")
        XCTAssertEqual(de.scheduleState, .thisEvening)
        XCTAssertEqual(de.whenDate, todayMs)
        XCTAssertEqual(de.deadline, todayMs + dayMs)
        XCTAssertEqual(de.durationMinutes, 45)
        XCTAssertEqual(de.matchedTagIds, ["tag-deep"])
        XCTAssertNil(de.matchedProject)
        XCTAssertEqual(de.matchedArea?.id, "area-life")
        XCTAssertTrue(de.isMilestone)

        // 3. FR, IT, ES natural dates + hh:mm duration + quoted phrase
        let fr = SmartSyntaxParser.parse(
            "Préparer démo @demain ~1:30 #work",
            tags: tags,
            projects: projects,
            areas: areas,
            locale: Locale(identifier: "fr_FR"),
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertEqual(fr.cleanTitle, "Préparer démo")
        XCTAssertEqual(fr.scheduleState, .upcoming)
        XCTAssertEqual(fr.whenDate, todayMs + dayMs)
        XCTAssertEqual(fr.durationMinutes, 90)
        XCTAssertEqual(fr.matchedTagIds, ["tag-work"])

        let it = SmartSyntaxParser.parse(
            "Chiamare cliente @stasera !domani ~15m",
            tags: tags,
            projects: projects,
            areas: areas,
            locale: Locale(identifier: "it_IT"),
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertEqual(it.cleanTitle, "Chiamare cliente")
        XCTAssertEqual(it.scheduleState, .thisEvening)
        XCTAssertEqual(it.deadline, todayMs + dayMs)
        XCTAssertEqual(it.durationMinutes, 15)

        let es = SmartSyntaxParser.parse(
            "Enviar propuesta @manana !\"en 3 dias\" ~2h",
            tags: tags,
            projects: projects,
            areas: areas,
            locale: Locale(identifier: "es_ES"),
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertEqual(es.cleanTitle, "Enviar propuesta")
        XCTAssertEqual(es.scheduleState, .upcoming)
        XCTAssertEqual(es.whenDate, todayMs + dayMs)
        XCTAssertEqual(es.deadline, todayMs + 3 * dayMs)
        XCTAssertEqual(es.durationMinutes, 120)

        // 4. Unmatched tokens, emails, URLs, approximate ~50, and completed/deleted containers are NEVER stripped
        let untouched = SmartSyntaxParser.parse(
            "Email dev@thingsbook.app about ~50 items at https://a.com/b #obsolete /Done-Project /unknown",
            tags: tags,
            projects: projects,
            areas: areas,
            locale: Locale(identifier: "en_US"),
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertFalse(untouched.hasAnyMatch)
        XCTAssertEqual(
            untouched.cleanTitle,
            "Email dev@thingsbook.app about ~50 items at https://a.com/b #obsolete /Done-Project /unknown"
        )

        // 5. Token-only input preserves trimmed rawTitle so cleanTitle is never empty
        let tokenOnly = SmartSyntaxParser.parse(
            "@tomorrow",
            tags: tags,
            projects: projects,
            areas: areas,
            locale: Locale(identifier: "en_US"),
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertTrue(tokenOnly.hasAnyMatch)
        XCTAssertEqual(tokenOnly.cleanTitle, "@tomorrow")
        XCTAssertEqual(tokenOnly.scheduleState, .upcoming)
    }

    func testV32ActualMinutesDatabaseQuickFindBackupAndSyncRoundTrip() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let refDate = cal.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: 12))!

        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let task = try db.createTask(
            title: "Deep Architecture Focus Session",
            notes: "Verify column index 26 in all SELECT queries",
            scheduleState: .today,
            whenDate: DateEpochConverter.startOfDayMillis(for: refDate, calendar: cal),
            durationMinutes: 60
        )
        XCTAssertNil(task.actualMinutes)

        // 1. addTaskActualMinutes accumulation & clamping
        try db.addTaskActualMinutes(taskId: task.id, deltaMinutes: 25)
        try db.addTaskActualMinutes(taskId: task.id, deltaMinutes: 15)

        var snap = try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        let loaded = try XCTUnwrap(snap.allOpenTasks.first { $0.id == task.id })
        XCTAssertEqual(loaded.actualMinutes, 40)
        XCTAssertEqual(loaded.durationMinutes, 60)

        // 2. Verify BOTH QuickFind FTS query AND non-FTS filter query read column 26 (`actual_minutes`)
        let ftsResult = try db.searchQuickFind(rawQuery: "Architecture", referenceDate: refDate, calendar: cal)
        let ftsTask = try XCTUnwrap(ftsResult.tasks.first { $0.id == task.id })
        XCTAssertEqual(ftsTask.actualMinutes, 40)

        var filterOnly = QuickFindFilter()
        filterOnly.dateFilter = .today
        let nonFtsResult = try db.searchQuickFind(rawQuery: "", filter: filterOnly, referenceDate: refDate, calendar: cal)
        let nonFtsTask = try XCTUnwrap(nonFtsResult.tasks.first { $0.id == task.id })
        XCTAssertEqual(nonFtsTask.actualMinutes, 40)

        // 3. Clamp upper bound 48_000 and reset to nil
        try db.setTaskActualMinutes(taskId: task.id, actualMinutes: 99_999)
        snap = try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        XCTAssertEqual(snap.allOpenTasks.first(where: { $0.id == task.id })?.actualMinutes, 48_000)

        try db.setTaskActualMinutes(taskId: task.id, actualMinutes: nil)
        snap = try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        XCTAssertNil(snap.allOpenTasks.first(where: { $0.id == task.id })?.actualMinutes)

        try db.setTaskActualMinutes(taskId: task.id, actualMinutes: 55)

        // 4. .tbbackup encrypted round-trip preserves actualMinutes
        let payload = try db.buildBackupPayload(preferences: AppPreferences())
        XCTAssertEqual(payload.tasks.first(where: { $0.id == task.id })?.actualMinutes, 55)
        let encoded = try ThingsBackupCodec.encodeBackup(payload: payload, password: "ZenFocusPassword32!")
        let decoded = try ThingsBackupCodec.decodeBackup(rawBytes: encoded, password: "ZenFocusPassword32!")

        let dbRestored = try ThingsSQLiteDatabase(path: ":memory:")
        try dbRestored.restoreBackupPayload(decoded)
        var restoredSnap = try dbRestored.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        let restoredTask = try XCTUnwrap(restoredSnap.allOpenTasks.first { $0.id == task.id })
        XCTAssertEqual(restoredTask.actualMinutes, 55)

        // 5. Cloud Sync: older peer omitting actualMinutes preserves local actualMinutes; explicit actualMinutes updates it
        let legacyPeerMap: [String: Any] = [
            "id": task.id,
            "title": "Deep Architecture Focus Session (Edited on v3.1)",
            "status": "OPEN",
            "scheduleState": "TODAY",
            "durationMinutes": 60,
            "sortOrder": restoredTask.sortOrder,
            "createdAt": restoredTask.createdAt,
            "updatedAt": restoredTask.updatedAt + 10_000,
            "isDeleted": false
        ]
        let legacyParsed = try XCTUnwrap(FirebaseCloudSyncService.parseTaskWithHints(legacyPeerMap))
        XCTAssertTrue(legacyParsed.omittedActualMinutes)
        _ = try dbRestored.applyRemoteDeltas(
            RemoteDeltaBatch(
                tasks: [legacyParsed.task],
                taskOmittedActualMinutesIds: legacyParsed.omittedActualMinutes ? [legacyParsed.task.id] : []
            )
        )
        restoredSnap = try dbRestored.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        let afterLegacyPeer = try XCTUnwrap(restoredSnap.allOpenTasks.first { $0.id == task.id })
        XCTAssertEqual(afterLegacyPeer.title, "Deep Architecture Focus Session (Edited on v3.1)")
        XCTAssertEqual(afterLegacyPeer.actualMinutes, 55)

        var v32PeerMap = legacyPeerMap
        v32PeerMap["actualMinutes"] = 85
        v32PeerMap["updatedAt"] = afterLegacyPeer.updatedAt + 10_000
        let v32Parsed = try XCTUnwrap(FirebaseCloudSyncService.parseTaskWithHints(v32PeerMap))
        XCTAssertFalse(v32Parsed.omittedActualMinutes)
        XCTAssertEqual(v32Parsed.task.actualMinutes, 85)
        _ = try dbRestored.applyRemoteDeltas(RemoteDeltaBatch(tasks: [v32Parsed.task]))
        restoredSnap = try dbRestored.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        XCTAssertEqual(restoredSnap.allOpenTasks.first(where: { $0.id == task.id })?.actualMinutes, 85)

        // 6. All 26 v32_* localization keys exist across all 5 languages
        let v32Keys = [
            "v32_cmd_palette_placeholder", "v32_cmd_section_actions", "v32_cmd_plan_my_day",
            "v32_cmd_ai_architect", "v32_cmd_weekly_review", "v32_cmd_open_templates",
            "v32_cmd_toggle_theme", "v32_cmd_open_settings", "v32_cmd_zen_focus",
            "v32_cmd_switch_list", "v32_cmd_switch_board", "v32_cmd_switch_roadmap",
            "v32_smart_syntax_detected", "v32_smart_syntax_apply", "v32_smart_syntax_hint",
            "v32_zen_focus_title", "v32_zen_expand_tooltip", "v32_zen_minimize",
            "v32_zen_short_break", "v32_zen_break_active", "v32_zen_back_to_focus",
            "v32_reset_actual_time", "v32_cmd_create_task", "v32_actual_focused_short",
            "v32_actual_vs_estimated", "v32_logbook_focused_7d"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in v32Keys {
                let text = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertNotEqual(text, key, "Missing v32 key \(key) in \(lang.rawValue)")
                XCTAssertFalse(text.isEmpty)
            }
        }
    }

    func testV322GeminiSheetLayoutAndZeroUniqueKeysWithValuesTraps() throws {
        let testFileURL = URL(fileURLWithPath: #filePath)
        let macosRoot = testFileURL
            .deletingLastPathComponent() // ThingsbookCoreTests
            .deletingLastPathComponent() // Tests
            .deletingLastPathComponent() // macos
        let sourcesRoot = macosRoot.appendingPathComponent("Sources")
        let geminiSheetURL = sourcesRoot
            .appendingPathComponent("ThingsbookMac")
            .appendingPathComponent("Views")
            .appendingPathComponent("GeminiAssistantSheet.swift")
        let geminiSource = try String(contentsOf: geminiSheetURL, encoding: .utf8)
        XCTAssertFalse(
            geminiSource.contains(".pickerStyle(.segmented)"),
            "GeminiAssistantSheet must use responsive geminiTabBar rather than AppKit NSSegmentedControl to prevent DE/FR/IT/ES horizontal clipping"
        )
        XCTAssertTrue(geminiSource.contains("private var geminiTabBar: some View"))
        XCTAssertTrue(geminiSource.contains("uniquingKeysWith:"))
        XCTAssertFalse(geminiSource.contains("uniqueKeysWithValues"))

        let enumerator = FileManager.default.enumerator(at: sourcesRoot, includingPropertiesForKeys: nil)
        while let fileURL = enumerator?.nextObject() as? URL {
            guard fileURL.pathExtension == "swift" else { continue }
            let src = try String(contentsOf: fileURL, encoding: .utf8)
            XCTAssertFalse(
                src.contains("uniqueKeysWithValues"),
                "Unsafe Dictionary(uniqueKeysWithValues:) found in \(fileURL.lastPathComponent); use uniquingKeysWith instead"
            )
        }
    }

    func testV33InProgressDatabaseAutoSignalsSmartSyntaxBackupAndSync() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let refDate = Date(timeIntervalSince1970: 1_795_540_000)
        let todayStart = DateEpochConverter.startOfDayMillis(for: refDate, calendar: cal)

        // 1. Non-destructive SQLite ALTER TABLE migration from a pre-v3.3 tasks schema
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thingsbook-v33-mig-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }
        let migDbPath = tempDir.appendingPathComponent("legacy_v32.sqlite3").path

        var rawHandle: OpaquePointer?
        XCTAssertEqual(sqlite3_open(migDbPath, &rawHandle), SQLITE_OK)
        let legacySql = """
        CREATE TABLE tasks (
            id TEXT PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            notes TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'OPEN',
            schedule_state TEXT NOT NULL DEFAULT 'TODAY',
            when_date INTEGER,
            deadline INTEGER,
            reminder_time INTEGER,
            scheduled_start_minutes INTEGER DEFAULT NULL,
            duration_minutes INTEGER DEFAULT NULL,
            actual_minutes INTEGER DEFAULT NULL,
            completed_at INTEGER,
            project_id TEXT,
            heading_id TEXT,
            area_id TEXT,
            recurrence_rule TEXT,
            sort_order REAL NOT NULL DEFAULT 0.0,
            is_pinned INTEGER NOT NULL DEFAULT 0,
            is_milestone INTEGER NOT NULL DEFAULT 0,
            effort_level TEXT DEFAULT NULL,
            blocked_by_json TEXT NOT NULL DEFAULT '[]',
            workspace_id TEXT DEFAULT NULL,
            assignee_id TEXT DEFAULT NULL,
            tag_ids_json TEXT NOT NULL DEFAULT '[]',
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            is_deleted INTEGER NOT NULL DEFAULT 0,
            is_purged INTEGER NOT NULL DEFAULT 0,
            is_dirty INTEGER NOT NULL DEFAULT 1
        );
        INSERT INTO tasks (id, title, status, schedule_state, when_date, sort_order, created_at, updated_at)
        VALUES ('legacy-v32-task', 'Legacy Task Before v3.3', 'OPEN', 'TODAY', \(todayStart), 1000.0, 1000, 1000);
        """
        XCTAssertEqual(sqlite3_exec(rawHandle, legacySql, nil, nil, nil), SQLITE_OK)
        sqlite3_close(rawHandle)

        let migratedDb = try ThingsSQLiteDatabase(path: migDbPath)
        var migSnap = try migratedDb.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        let migratedTask = try XCTUnwrap(migSnap.allOpenTasks.first { $0.id == "legacy-v32-task" })
        XCTAssertFalse(migratedTask.isInProgress)
        try migratedDb.setTaskInProgress("legacy-v32-task", isInProgress: true)
        migSnap = try migratedDb.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        XCTAssertTrue(migSnap.allOpenTasks.first(where: { $0.id == "legacy-v32-task" })?.isInProgress == true)

        // 2. CRUD, Project.inProgressTaskCount / inProgressProgress, FTS & Filter Search (column 27 & column 19)
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let proj = try db.createProject(title: "Glacier v3.3 Release")
        let tDone = try db.createTask(title: "Design Spec", scheduleState: .anytime, projectId: proj.id)
        _ = try db.setTaskStatus(taskId: tDone.id, status: .completed, referenceDate: refDate, calendar: cal)
        let tWip1 = try db.createTask(
            title: "Core SQLite Migration Architecture",
            scheduleState: .today,
            whenDate: todayStart,
            projectId: proj.id,
            isInProgress: true
        )
        let tChecklist = try db.createTask(
            title: "SwiftUI Progress Ring Polish",
            scheduleState: .today,
            whenDate: todayStart,
            projectId: proj.id,
            checklistTitles: ["Amber 2-tone bar", "Shift-click checkbox"]
        )
        let tTimer = try db.createTask(
            title: "Cloud Sync Hints Verification",
            scheduleState: .anytime,
            projectId: proj.id
        )

        // Auto-Signal A: Checking a checklist item on an OPEN task auto-sets isInProgress = true
        var snap = try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        let freshChecklistTask = try XCTUnwrap(snap.allOpenTasks.first { $0.id == tChecklist.id })
        XCTAssertFalse(freshChecklistTask.isInProgress)
        var firstChk = try XCTUnwrap(freshChecklistTask.checklistItems.first)
        firstChk.isCompleted = true
        try db.updateChecklistItem(firstChk)

        snap = try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        XCTAssertTrue(snap.allOpenTasks.first(where: { $0.id == tChecklist.id })?.isInProgress == true)

        // Project now has 4 non-canceled tasks: 1 completed (0.25), 2 open in-progress (0.50), 1 open idle
        let updatedProj = try XCTUnwrap(snap.projects.first { $0.id == proj.id })
        XCTAssertEqual(updatedProj.totalTaskCount, 4)
        XCTAssertEqual(updatedProj.completedTaskCount, 1)
        XCTAssertEqual(updatedProj.inProgressTaskCount, 2)
        XCTAssertEqual(updatedProj.progress, 0.25, accuracy: 0.0001)
        XCTAssertEqual(updatedProj.inProgressProgress, 0.50, accuracy: 0.0001)

        // Manual Clear In Progress override works even while 1/2 checklist items remain checked
        try db.setTaskInProgress(tChecklist.id, isInProgress: false)
        snap = try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        XCTAssertFalse(snap.allOpenTasks.first(where: { $0.id == tChecklist.id })?.isInProgress ?? true)
        XCTAssertEqual(snap.projects.first(where: { $0.id == proj.id })?.inProgressTaskCount, 1)

        // Auto-Signal B: Incrementing actual_minutes (Focus Timer) on an OPEN task auto-sets isInProgress = true
        try db.addTaskActualMinutes(taskId: tTimer.id, deltaMinutes: 20)
        snap = try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        let afterTimer = try XCTUnwrap(snap.allOpenTasks.first { $0.id == tTimer.id })
        XCTAssertEqual(afterTimer.actualMinutes, 20)
        XCTAssertTrue(afterTimer.isInProgress)

        // FTS search (column 27) & filter-only search (inProgressOnly) for both tasks & projects (column 19)
        let ftsHits = try db.searchQuickFind(rawQuery: "Architecture", referenceDate: refDate, calendar: cal)
        let ftsTask = try XCTUnwrap(ftsHits.tasks.first { $0.id == tWip1.id })
        XCTAssertTrue(ftsTask.isInProgress)

        let inProgFilter = QuickFindFilter(inProgressOnly: true)
        let filteredHits = try db.searchQuickFind(rawQuery: "", filter: inProgFilter, referenceDate: refDate, calendar: cal)
        XCTAssertEqual(Set(filteredHits.tasks.map(\.id)), Set([tWip1.id, tTimer.id]))
        XCTAssertEqual(filteredHits.projects.map(\.id), [proj.id])
        XCTAssertEqual(filteredHits.projects.first?.inProgressTaskCount, 2)

        let ftsProjHits = try db.searchQuickFind(rawQuery: "Glacier", filter: inProgFilter, referenceDate: refDate, calendar: cal)
        XCTAssertEqual(ftsProjHits.projects.map(\.id), [proj.id])

        // 3. TaskViewSorter & SavedPerspective.inProgressOnly
        let sortedOpen = TaskViewSorter.sort(snap.allOpenTasks, mode: "manual")
        XCTAssertEqual(Array(sortedOpen.prefix(2).map(\.id)), [tWip1.id, tTimer.id])
        let wipPerspective = SavedPerspective(id: "persp-wip", title: "Active WIP", inProgressOnly: true)
        let perspMatched = PerspectiveFilterEngine.filter(
            tasks: snap.allOpenTasks,
            perspective: wipPerspective,
            openTaskIds: Set(snap.allOpenTasks.map(\.id)),
            todayEpochMillis: todayStart
        )
        XCTAssertEqual(Set(perspMatched.map(\.id)), Set([tWip1.id, tTimer.id]))

        // 4. Resets on Duplication, Recurring Spawn, Completion, Logbook Move & Project Completion
        let dupTask = try XCTUnwrap(try db.duplicateTask(tWip1.id, copySuffix: "Copy"))
        XCTAssertFalse(dupTask.isInProgress, "Duplicated task must start with isInProgress = false")

        let dupProj = try XCTUnwrap(try db.duplicateProject(proj.id, copySuffix: "Copy"))
        XCTAssertEqual(dupProj.inProgressTaskCount, 0, "Duplicated project tasks must start with isInProgress = false")

        let recurringTask = try db.createTask(
            title: "Daily Standup Prep",
            scheduleState: .today,
            whenDate: todayStart,
            recurrenceRule: RecurrenceRule(mode: .fixedSchedule, interval: 1, unit: .days),
            isInProgress: true
        )
        let spawnedId = try XCTUnwrap(
            try db.setTaskStatus(taskId: recurringTask.id, status: .completed, referenceDate: refDate, calendar: cal)
        )
        snap = try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        let completedParent = try XCTUnwrap(snap.logbookTasks.first { $0.id == recurringTask.id })
        XCTAssertFalse(completedParent.isInProgress, "Completed task must reset isInProgress = false")
        let spawnedOccurrence = try XCTUnwrap(snap.allOpenTasks.first { $0.id == spawnedId })
        XCTAssertFalse(spawnedOccurrence.isInProgress, "Spawned recurring occurrence must start with isInProgress = false")

        _ = try db.moveTasksToDestination([tTimer.id], destination: .smartView(.logbook), referenceDate: refDate, calendar: cal)
        snap = try db.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        XCTAssertFalse(snap.logbookTasks.first(where: { $0.id == tTimer.id })?.isInProgress ?? true)

        // 5. SmartSyntaxParser across EN/DE/FR/IT/ES + ◐
        let syntaxTokens = [
            "Ship v3.3 release ◐",
            "Ship v3.3 release !wip",
            "Ship v3.3 release !inprogress",
            "Ship v3.3 release !in-progress",
            "Ship v3.3 release !doing",
            "Ship v3.3 release !\"in progress\"",
            "Ship v3.3 release !inarbeit",
            "Ship v3.3 release !in-arbeit",
            "Ship v3.3 release !\"in arbeit\"",
            "Ship v3.3 release !encours",
            "Ship v3.3 release !en-cours",
            "Ship v3.3 release !\"en cours\"",
            "Ship v3.3 release !incorso",
            "Ship v3.3 release !in-corso",
            "Ship v3.3 release !\"in corso\"",
            "Ship v3.3 release !encurso",
            "Ship v3.3 release !en-curso",
            "Ship v3.3 release !\"en curso\""
        ]
        for input in syntaxTokens {
            let parsed = SmartSyntaxParser.parse(input, tags: [], projects: [], areas: [], referenceDate: refDate, calendar: cal)
            XCTAssertTrue(parsed.isInProgress, "Expected isInProgress=true for '\(input)'")
            XCTAssertTrue(parsed.hasAnyMatch, "Expected hasAnyMatch=true for '\(input)'")
            XCTAssertEqual(parsed.cleanTitle, "Ship v3.3 release", "Expected cleanTitle for '\(input)'")
        }

        // 6. .tbbackup round-trip & Cloud Sync omittedInProgress preservation
        var prefs = AppPreferences()
        prefs.savedPerspectives = [wipPerspective]
        let backupPayload = try db.buildBackupPayload(preferences: prefs)
        XCTAssertEqual(backupPayload.tasks.first(where: { $0.id == tWip1.id })?.isInProgress, true)
        let encrypted = try ThingsBackupCodec.encodeBackup(payload: backupPayload, password: "InProgressBackup33!")
        let decoded = try ThingsBackupCodec.decodeBackup(rawBytes: encrypted, password: "InProgressBackup33!")

        let dbRestored = try ThingsSQLiteDatabase(path: ":memory:")
        try dbRestored.restoreBackupPayload(decoded)
        var restoredSnap = try dbRestored.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        let restoredWip = try XCTUnwrap(restoredSnap.allOpenTasks.first { $0.id == tWip1.id })
        XCTAssertTrue(restoredWip.isInProgress)

        let legacyPeerWire: [String: Any] = [
            "id": restoredWip.id,
            "title": "Core SQLite Migration Architecture (Edited on v3.2)",
            "status": "OPEN",
            "scheduleState": "TODAY",
            "whenDate": todayStart,
            "projectId": proj.id,
            "sortOrder": restoredWip.sortOrder,
            "createdAt": restoredWip.createdAt,
            "updatedAt": restoredWip.updatedAt + 10_000,
            "isDeleted": false
        ]
        let legacyHints = try XCTUnwrap(FirebaseCloudSyncService.parseTaskWithHints(legacyPeerWire))
        XCTAssertTrue(legacyHints.omittedInProgress)
        _ = try dbRestored.applyRemoteDeltas(
            RemoteDeltaBatch(
                tasks: [legacyHints.task],
                taskOmittedInProgressIds: legacyHints.omittedInProgress ? [legacyHints.task.id] : []
            )
        )
        restoredSnap = try dbRestored.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        let afterLegacySync = try XCTUnwrap(restoredSnap.allOpenTasks.first { $0.id == restoredWip.id })
        XCTAssertEqual(afterLegacySync.title, "Core SQLite Migration Architecture (Edited on v3.2)")
        XCTAssertTrue(afterLegacySync.isInProgress, "Legacy peer omitting isInProgress must preserve local isInProgress on OPEN task")

        var explicitClearWire = legacyPeerWire
        explicitClearWire["isInProgress"] = false
        explicitClearWire["updatedAt"] = afterLegacySync.updatedAt + 10_000
        let explicitHints = try XCTUnwrap(FirebaseCloudSyncService.parseTaskWithHints(explicitClearWire))
        XCTAssertFalse(explicitHints.omittedInProgress)
        XCTAssertFalse(explicitHints.task.isInProgress)
        _ = try dbRestored.applyRemoteDeltas(RemoteDeltaBatch(tasks: [explicitHints.task]))
        restoredSnap = try dbRestored.loadWorkspaceSnapshot(referenceDate: refDate, calendar: cal)
        XCTAssertFalse(restoredSnap.allOpenTasks.first(where: { $0.id == restoredWip.id })?.isInProgress ?? true)

        // 7. All 6 v33_* localization keys exist across EN/DE/FR/IT/ES
        let v33Keys = [
            "v33_in_progress_badge",
            "v33_mark_in_progress",
            "v33_clear_in_progress",
            "v33_filter_in_progress_only",
            "v33_project_in_progress_count",
            "v33_cmd_toggle_in_progress"
        ]
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            for key in v33Keys {
                let text = ThingsStrings.shared.text(key, lang: lang)
                XCTAssertNotEqual(text, key, "Missing v33 key \(key) in \(lang.rawValue)")
                XCTAssertFalse(text.isEmpty)
            }
        }
    }

    func testV330Beta2WorkspaceInspectorPanelParityContract() throws {
        let testFileURL = URL(fileURLWithPath: #filePath)
        let macosRoot = testFileURL
            .deletingLastPathComponent() // ThingsbookCoreTests
            .deletingLastPathComponent() // Tests
            .deletingLastPathComponent() // macos
        let inspectorURL = macosRoot
            .appendingPathComponent("Sources")
            .appendingPathComponent("ThingsbookMac")
            .appendingPathComponent("Views")
            .appendingPathComponent("WorkspaceInspectorPanelView.swift")
        let inspectorSource = try String(contentsOf: inspectorURL, encoding: .utf8)

        let requiredParityTokens = [
            "DependenciesPopoverView",
            "RepeatEditorPopoverView",
            "AttachmentsStripView",
            "SmartSyntaxPreviewPillsView",
            "MacVoiceDictationController",
            "generateMagicChecklist",
            "MarkdownNotesParser.renderAttributed",
            "ChecklistItemRowView",
            "handleFileDropProviders",
            "WhenPopoverView",
            "flag.checkered",
            "action_duplicate_todo",
            "action_copy_todo",
            "action_cancel_todo",
            "action_reopen_todo"
        ]
        for token in requiredParityTokens {
            XCTAssertTrue(
                inspectorSource.contains(token),
                "WorkspaceInspectorPanelView.swift must wire \(token) for 1:1 TaskBentoModalView parity"
            )
        }
    }

    func testV331MultiDayWorkloadSpreadsEffortAcrossDaysAndEnlargesInProgressIcons() throws {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        // 2026-04-13 is Monday; Thursday is +3d, Friday is +4d
        let mondayDate = try XCTUnwrap(cal.date(from: DateComponents(year: 2026, month: 4, day: 13)))
        let mondayMs = DateEpochConverter.startOfDayMillis(for: mondayDate, calendar: cal)
        let thuMs = mondayMs + 3 * 86_400_000
        let friMs = mondayMs + 4 * 86_400_000

        // 1. 2d (960m) task + 15m task scheduled on Thursday (deadline: nil, default 480m workday)
        let twoDayTask = TaskItem(
            id: "t-2d",
            title: "2-Day Implementation",
            status: .open,
            scheduleState: .upcoming,
            whenDate: thuMs,
            deadline: nil,
            durationMinutes: 960,
            createdAt: 1,
            updatedAt: 1
        )
        let quickTask = TaskItem(
            id: "t-15m",
            title: "Quick Sync",
            status: .open,
            scheduleState: .upcoming,
            whenDate: thuMs,
            deadline: nil,
            durationMinutes: 15,
            createdAt: 2,
            updatedAt: 2
        )

        let forecast1 = TodayCapacityCalculator.computeSevenDayForecast(
            todayTasks: [],
            allOpenTasks: [twoDayTask, quickTask],
            calendarEvents: [],
            todayMillis: mondayMs,
            daysCount: 7,
            calendar: cal
        )
        XCTAssertEqual(forecast1[3].plannedTaskMinutes, 495)
        XCTAssertEqual(forecast1[3].overloadedByMinutes, 15)
        XCTAssertTrue(forecast1[3].isOverloaded)
        XCTAssertEqual(forecast1[4].plannedTaskMinutes, 480)
        XCTAssertEqual(forecast1[4].overloadedByMinutes, 0)
        XCTAssertFalse(forecast1[4].isOverloaded)

        // 2. 2d (960m) task alone on Thursday (deadline: nil) -> Thu 480m, Fri 480m, neither overloaded
        let forecast2 = TodayCapacityCalculator.computeSevenDayForecast(
            todayTasks: [],
            allOpenTasks: [twoDayTask],
            calendarEvents: [],
            todayMillis: mondayMs,
            daysCount: 7,
            calendar: cal
        )
        XCTAssertEqual(forecast2[3].plannedTaskMinutes, 480)
        XCTAssertFalse(forecast2[3].isOverloaded)
        XCTAssertEqual(forecast2[4].plannedTaskMinutes, 480)
        XCTAssertFalse(forecast2[4].isOverloaded)

        let singleDayCap = TodayCapacityCalculator.compute(
            tasks: [twoDayTask],
            calendarEvents: [],
            dayStartMillis: thuMs,
            dayEndMillis: friMs,
            calendar: cal
        )
        XCTAssertEqual(singleDayCap.plannedMinutes, 480)
        XCTAssertEqual(singleDayCap.freeMinutes, 0)

        // 3. 2d (960m) task with deadline on Thursday -> compressed onto Thursday (960m, +480m overloaded)
        var dueThuTask = twoDayTask
        dueThuTask.deadline = thuMs
        let forecast3 = TodayCapacityCalculator.computeSevenDayForecast(
            todayTasks: [],
            allOpenTasks: [dueThuTask],
            calendarEvents: [],
            todayMillis: mondayMs,
            daysCount: 7,
            calendar: cal
        )
        XCTAssertEqual(forecast3[3].plannedTaskMinutes, 960)
        XCTAssertEqual(forecast3[3].overloadedByMinutes, 480)
        XCTAssertEqual(forecast3[4].plannedTaskMinutes, 0)

        // 4. 3d (1440m) task starting Thursday with deadline on Friday -> Thu 720m, Fri 720m
        let threeDayCompressedTask = TaskItem(
            id: "t-3d-fri",
            title: "3-Day Sprint Due Friday",
            status: .open,
            scheduleState: .upcoming,
            whenDate: thuMs,
            deadline: friMs,
            durationMinutes: 1440,
            createdAt: 3,
            updatedAt: 3
        )
        let forecast4 = TodayCapacityCalculator.computeSevenDayForecast(
            todayTasks: [],
            allOpenTasks: [threeDayCompressedTask],
            calendarEvents: [],
            todayMillis: mondayMs,
            daysCount: 7,
            calendar: cal
        )
        XCTAssertEqual(forecast4[3].plannedTaskMinutes, 720)
        XCTAssertEqual(forecast4[4].plannedTaskMinutes, 720)
        XCTAssertEqual(forecast4[5].plannedTaskMinutes, 0)

        // Verify enlarged In-Progress icon fonts in macOS UI views
        let macosRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let modalSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/TaskBentoModalView.swift"),
            encoding: .utf8
        )
        let themeSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Theme/ThingsMacTheme.swift"),
            encoding: .utf8
        )
        let inspectorSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/WorkspaceInspectorPanelView.swift"),
            encoding: .utf8
        )
        let workspaceSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/MainWorkspaceView.swift"),
            encoding: .utf8
        )

        XCTAssertTrue(modalSource.contains(".font(.system(size: 16, weight: .semibold))"))
        XCTAssertTrue(modalSource.contains(".frame(width: 30, height: 30)"))
        XCTAssertTrue(themeSource.contains(".font(.system(size: 14, weight: .semibold))"))
        XCTAssertTrue(inspectorSource.contains(".font(.system(size: 13.5, weight: .semibold))"))
        XCTAssertTrue(workspaceSource.contains(".font(.system(size: 13.5, weight: .semibold))"))
    }

    func testV332UnifiedTodayPresetsAndAlwaysVisibleDateButtonInDetailAndInspector() throws {
        let macosRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // ThingsbookCoreTests
            .deletingLastPathComponent() // Tests
            .deletingLastPathComponent() // macos
        let modalSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/TaskBentoModalView.swift"),
            encoding: .utf8
        )
        let inspectorSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/WorkspaceInspectorPanelView.swift"),
            encoding: .utf8
        )
        let themeSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Theme/ThingsMacTheme.swift"),
            encoding: .utf8
        )

        // 1. Streamlined 3-control When row ([Today] + [This Evening] + [Date…]) in BOTH Task Detail & Task Inspector
        let requiredWhenTokens = [
            "view_today",
            "view_this_evening",
            "repeat_ends_pick_date",
            "WhenPopoverView",
            "isWhenPopoverOpen = true",
            "to: .today)",
            "to: .thisEvening)",
            "whenDateButtonSpec"
        ]
        for token in requiredWhenTokens {
            XCTAssertTrue(
                modalSource.contains(token),
                "TaskBentoModalView.swift must include \(token) for streamlined 3-control When row"
            )
            XCTAssertTrue(
                inspectorSource.contains(token),
                "WorkspaceInspectorPanelView.swift must include \(token) for streamlined 3-control When row"
            )
        }

        // Inline Tomorrow / Anytime / Someday preset pill clutter removed (accessible via WhenPopoverView)
        let removedInlinePresetTokens = [
            "sunrise.fill",
            "safari.fill",
            "view_anytime",
            "to: .upcoming)",
            "to: .anytime)",
            "to: .someday)",
            "quickMoveToThisEveningAndClose"
        ]
        for forbidden in removedInlinePresetTokens {
            XCTAssertFalse(
                modalSource.contains(forbidden),
                "TaskBentoModalView.swift must not render inline preset pill clutter (\(forbidden))"
            )
            XCTAssertFalse(
                inspectorSource.contains(forbidden),
                "WorkspaceInspectorPanelView.swift must not render inline preset pill clutter (\(forbidden))"
            )
        }

        // Dedicated Date picker button shows Tomorrow / custom EEEdMMM date / Someday cloud pill when active, else Pick date… ghost button
        XCTAssertTrue(modalSource.contains("store.tr(\"date_tomorrow\")"))
        XCTAssertTrue(inspectorSource.contains("store.tr(\"date_tomorrow\")"))
        XCTAssertTrue(modalSource.contains("store.tr(\"view_someday\")"))
        XCTAssertTrue(inspectorSource.contains("store.tr(\"view_someday\")"))
        XCTAssertFalse(inspectorSource.contains("calendar.badge.clock"))

        // 2. TaskBentoModalView.scheduleBentoCard must NOT duplicate Milestone or In Progress pills (kept exclusively in modalHeaderBar)
        let scheduleCardStart = try XCTUnwrap(modalSource.range(of: "private var scheduleBentoCard: some View")?.lowerBound)
        let scheduleCardEnd = try XCTUnwrap(modalSource.range(of: "private var focusAndDependenciesBentoCard: some View")?.lowerBound)
        let scheduleCardSlice = String(modalSource[scheduleCardStart..<scheduleCardEnd])
        XCTAssertFalse(
            scheduleCardSlice.contains("v30_milestone_label"),
            "TaskBentoModalView.scheduleBentoCard must not duplicate Milestone pill"
        )
        XCTAssertFalse(
            scheduleCardSlice.contains("v33_mark_in_progress"),
            "TaskBentoModalView.scheduleBentoCard must not duplicate In Progress pill"
        )
        // Modal header bar retains Milestone & In Progress controls
        let headerStart = try XCTUnwrap(modalSource.range(of: "private var modalHeaderBar: some View")?.lowerBound)
        let headerEnd = try XCTUnwrap(modalSource.range(of: "private var leftPrimaryContentColumn: some View")?.lowerBound)
        let headerSlice = String(modalSource[headerStart..<headerEnd])
        XCTAssertTrue(headerSlice.contains("v33_mark_in_progress"))
        XCTAssertTrue(headerSlice.contains("v30_mark_as_milestone"))

        // 3. Quiet borderless ghost styling for unset optional properties in ThingsActionChipButton, Detail & Inspector
        XCTAssertTrue(themeSource.contains("public var quietWhenInactive: Bool"))
        XCTAssertTrue(themeSource.contains("Color.secondary.opacity(0.82)"))
        XCTAssertTrue(themeSource.contains("Color.primary.opacity(0.06)"))
        XCTAssertTrue(modalSource.contains("quietWhenInactive: true"))
        XCTAssertTrue(inspectorSource.contains("quietWhenInactive: true"))

        // Verify localization of repeat_ends_pick_date across all 5 languages
        for lang in [AppLanguage.en, .de, .fr, .it, .es] {
            let pickDateLabel = ThingsStrings.shared.text("repeat_ends_pick_date", lang: lang)
            XCTAssertFalse(pickDateLabel.isEmpty, "repeat_ends_pick_date must not be empty in \(lang.rawValue)")
            XCTAssertNotEqual(pickDateLabel, "repeat_ends_pick_date", "Unresolved repeat_ends_pick_date in \(lang.rawValue)")
        }

        // 4. v3.3.4: 7-Day Workload card lives exclusively in WorkspaceInspectorPanelView Overview (not duplicated in SmartViewCanvas upcomingCanvas)
        let smartCanvasURL = macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/SmartViewCanvas.swift")
        let smartCanvasSource = try String(contentsOf: smartCanvasURL, encoding: .utf8)
        XCTAssertFalse(
            smartCanvasSource.contains("SevenDayWorkloadStripCard"),
            "SmartViewCanvas.upcomingCanvas must not duplicate SevenDayWorkloadStripCard"
        )
        XCTAssertTrue(
            inspectorSource.contains("SevenDayWorkloadStripCard("),
            "WorkspaceInspectorPanelView Overview retains SevenDayWorkloadStripCard"
        )
    }

    func testV335InteractiveWorkloadShortcutScheduleUndoRolloverAndZeroWarnings() throws {
        // 1. performMidnightRollover preserves original whenDate on THIS_EVENING rollover via COALESCE(when_date, ?)
        let db = try ThingsSQLiteDatabase(path: ":memory:")
        let cal = Calendar.current
        let now = Date()
        let sodToday = DateEpochConverter.startOfDayMillis(for: now, calendar: cal)
        let twoDaysAgo = try XCTUnwrap(cal.date(byAdding: .day, value: -2, to: now))
        let twoDaysAgoSod = DateEpochConverter.startOfDayMillis(for: twoDaysAgo, calendar: cal)

        let eveningWithPastWhen = try db.createTask(
            title: "Evening Task Two Days Ago",
            scheduleState: .thisEvening,
            whenDate: twoDaysAgoSod
        )
        let eveningWithoutWhen = try db.createTask(
            title: "Evening Task Null When",
            scheduleState: .thisEvening,
            whenDate: nil
        )
        // Simulate stale updated_at before sodToday for null-whenDate evening task
        let nextDayRef = try XCTUnwrap(cal.date(byAdding: .day, value: 1, to: now))
        let nextDaySod = DateEpochConverter.startOfDayMillis(for: nextDayRef, calendar: cal)
        _ = eveningWithoutWhen // updated_at is < nextDaySod

        let promoted = try db.performMidnightRollover(referenceDate: nextDayRef, calendar: cal)
        XCTAssertGreaterThanOrEqual(promoted, 2)

        let snap = try db.loadWorkspaceSnapshot()
        let rolledWithWhen = try XCTUnwrap(snap.todayDaytimeTasks.first(where: { $0.id == eveningWithPastWhen.id }))
        XCTAssertEqual(rolledWithWhen.scheduleState, .today)
        XCTAssertEqual(
            rolledWithWhen.whenDate,
            twoDaysAgoSod,
            "COALESCE(when_date, ?) must preserve original when_date < sodToday for rolled-over evening tasks"
        )

        let rolledNullWhen = try XCTUnwrap(snap.todayDaytimeTasks.first(where: { $0.id == eveningWithoutWhen.id }))
        XCTAssertEqual(rolledNullWhen.scheduleState, .today)
        XCTAssertEqual(
            rolledNullWhen.whenDate,
            nextDaySod,
            "COALESCE(when_date, ?) must populate sodToday when original when_date was nil"
        )
        _ = sodToday

        // Verify schedule move snapshot restoration via restoreTaskPlacements
        var scheduledOriginal = try db.createTask(
            title: "Timeblocked Today Task",
            scheduleState: .today,
            whenDate: sodToday
        )
        scheduledOriginal.scheduledStartMinutes = 570
        scheduledOriginal.reminderTime = sodToday + 9 * 3_600_000
        try db.updateTask(scheduledOriginal)

        let preMoveSnapshot = TaskPlacementSnapshot(
            task: scheduledOriginal,
            tagIds: scheduledOriginal.tagIds,
            wasDeleted: false
        )
        var movedToSomeday = scheduledOriginal
        movedToSomeday.scheduleState = .someday
        movedToSomeday.whenDate = nil
        movedToSomeday.reminderTime = nil
        movedToSomeday.scheduledStartMinutes = nil
        try db.updateTask(movedToSomeday)

        try db.restoreTaskPlacements([preMoveSnapshot])
        let restoredSnap = try db.loadWorkspaceSnapshot()
        let restoredTask = try XCTUnwrap(restoredSnap.todayDaytimeTasks.first(where: { $0.id == scheduledOriginal.id }))
        XCTAssertEqual(restoredTask.scheduleState, .today)
        XCTAssertEqual(restoredTask.whenDate, sodToday)
        XCTAssertEqual(restoredTask.scheduledStartMinutes, 570)
        XCTAssertEqual(restoredTask.reminderTime, sodToday + 9 * 3_600_000)

        // 2. Source contracts for v3.3.5 interactive workload, ⇧⌘I shortcut, schedule undo, rollover pill, and 0-warning fixes
        let macosRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()

        let menusSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Menus/ThingsCommandMenus.swift"),
            encoding: .utf8
        )
        let settingsSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/SettingsWindowView.swift"),
            encoding: .utf8
        )
        let quickFindSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/QuickFindOverlay.swift"),
            encoding: .utf8
        )
        let storeSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/State/ThingsbookStore.swift"),
            encoding: .utf8
        )
        let boardSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/ProjectBoardAndRoadmapView.swift"),
            encoding: .utf8
        )
        let smartCanvasSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/SmartViewCanvas.swift"),
            encoding: .utf8
        )
        let rowSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/TaskRowCardView.swift"),
            encoding: .utf8
        )
        let modalSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/TaskBentoModalView.swift"),
            encoding: .utf8
        )
        let inspectorSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/WorkspaceInspectorPanelView.swift"),
            encoding: .utf8
        )
        let mainWorkspaceSource = try String(
            contentsOf: macosRoot.appendingPathComponent("Sources/ThingsbookMac/Views/MainWorkspaceView.swift"),
            encoding: .utf8
        )

        // Shortcut ⇧⌘I on In Progress & ⌥⇧⌘I on Restore Backup
        let restoreRange = try XCTUnwrap(menusSource.range(of: "backup_restore_button"))
        let undoGroupRange = try XCTUnwrap(menusSource.range(of: "CommandGroup(replacing: .undoRedo)"))
        let restoreSlice = String(menusSource[restoreRange.lowerBound..<undoGroupRange.lowerBound])
        XCTAssertTrue(restoreSlice.contains(".keyboardShortcut(\"i\", modifiers: [.command, .option, .shift])"))

        let inProgressMenuRange = try XCTUnwrap(menusSource.range(of: "toggleInProgressForSelectedOrExpandedTasks()"))
        let duplicateMenuRange = try XCTUnwrap(menusSource.range(of: "action_duplicate_todo"))
        let inProgressMenuSlice = String(menusSource[inProgressMenuRange.lowerBound..<duplicateMenuRange.lowerBound])
        XCTAssertTrue(inProgressMenuSlice.contains(".keyboardShortcut(\"i\", modifiers: [.command, .shift])"))

        XCTAssertTrue(settingsSource.contains("shortcutRow(title: store.tr(\"v33_mark_in_progress\"), keys: \"⇧⌘I\")"))
        XCTAssertTrue(quickFindSource.contains("shortcut: \"⇧⌘I\""))
        XCTAssertTrue(modalSource.contains("(⇧⌘I)"))
        XCTAssertTrue(inspectorSource.contains("(⇧⌘I)"))
        XCTAssertTrue(mainWorkspaceSource.contains("(⇧⌘I)"))

        // Interactive SevenDayWorkloadStripCard + ScrollViewReader jump + schedule undo
        let workloadCardStart = try XCTUnwrap(boardSource.range(of: "public struct SevenDayWorkloadStripCard: View")?.lowerBound)
        let areaPortfolioStart = try XCTUnwrap(boardSource.range(of: "public struct AreaPortfolioRoadmapWorkspaceView: View")?.lowerBound)
        let workloadCardSlice = String(boardSource[workloadCardStart..<areaPortfolioStart])
        XCTAssertTrue(workloadCardSlice.contains("jumpToWorkloadDay("))
        XCTAssertTrue(workloadCardSlice.contains("dropTasksOntoWorkloadDay("))
        XCTAssertTrue(workloadCardSlice.contains(".thingsDropTarget(cornerRadius: 6)"))

        XCTAssertTrue(storeSource.contains("pendingUpcomingScrollEpochDay"))
        XCTAssertTrue(storeSource.contains("public func jumpToWorkloadDay(epochDay: Int, isToday: Bool)"))
        XCTAssertTrue(storeSource.contains("public func dropTasksOntoWorkloadDay(_ payloads: [String], epochDay: Int, isToday: Bool) -> Bool"))
        XCTAssertTrue(storeSource.contains("recordScheduleUndo: Bool = true"))
        XCTAssertTrue(storeSource.contains("MainActor.assumeIsolated"))

        XCTAssertTrue(smartCanvasSource.contains("ScrollViewReader { scrollProxy in"))
        XCTAssertTrue(smartCanvasSource.contains("upcoming-day-\\(epochDay)"))
        XCTAssertFalse(
            smartCanvasSource.contains("SevenDayWorkloadStripCard"),
            "SmartViewCanvas.swift must never render or reference SevenDayWorkloadStripCard"
        )

        // Subtle rollover pill & 1-click Tomorrow in context menus
        XCTAssertTrue(rowSource.contains("rolloverDays"))
        XCTAssertTrue(rowSource.contains("arrow.counterclockwise"))
        XCTAssertTrue(rowSource.contains("↺ \\(rolloverDays)d"))
        XCTAssertTrue(rowSource.contains("store.scheduleTasks([task.id], to: .upcoming)"))
        XCTAssertTrue(rowSource.contains("Label(store.tr(\"date_tomorrow\"), systemImage: \"sun.horizon.fill\")"))
        XCTAssertTrue(rowSource.contains("store.scheduleSelectedTasks(.upcoming)"))
        XCTAssertTrue(boardSource.contains("Label(store.tr(\"date_tomorrow\"), systemImage: \"sun.horizon.fill\")"))

        // v3.3.6: Planned day headers & This Evening header act as active drop targets with Undo
        XCTAssertTrue(storeSource.contains("public func dropTasksOntoThisEvening(_ payloads: [String]) -> Bool"))
        XCTAssertTrue(smartCanvasSource.contains("store.dropTasksOntoThisEvening(payloads)"))
        XCTAssertTrue(smartCanvasSource.contains("store.dropTasksOntoWorkloadDay(payloads, epochDay: epochDay, isToday: false)"))
    }
}
