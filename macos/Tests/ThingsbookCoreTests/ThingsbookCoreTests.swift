import XCTest
@testable import ThingsbookCore

final class ThingsbookCoreTests: XCTestCase {

    // MARK: - 1. Localization Parity & Android Positional Formatting

    func testLocalizationFiveLanguagesLoadedAndFormatted() {
        let strings = ThingsStrings.shared
        let langs = Set(strings.availableLanguages)
        XCTAssertEqual(langs, ["de", "en", "es", "fr", "it"])

        for lang in ["en", "de", "fr", "it", "es"] {
            XCTAssertGreaterThanOrEqual(strings.keys(for: lang).count, 350, "Expected >=350 Android string keys in \(lang)")
        }

        let formatted = ThingsStrings.formatPositional("Project \"%1$s\" has %2$d tasks (100%% parity)", args: ["macOS Port", 42])
        XCTAssertEqual(formatted, "Project \"macOS Port\" has 42 tasks (100% parity)")
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
        XCTAssertEqual(encInspected, .passwordProtected(createdAt: payload.createdAt, appVersionName: "1.7.0"))

        XCTAssertThrowsError(try ThingsBackupCodec.decodeBackup(rawBytes: encryptedBytes, password: nil))
        XCTAssertThrowsError(try ThingsBackupCodec.decodeBackup(rawBytes: encryptedBytes, password: "WrongPassword"))

        let decrypted = try ThingsBackupCodec.decodeBackup(rawBytes: encryptedBytes, password: "CorrectHorseBatteryStaple!")
        XCTAssertEqual(decrypted, payload)

        // Authentic Android / ChromeOS WebCrypto AES-256-GCM + GZIP test vector
        let androidVectorJson = """
        {"magic":"THINGSBOOK_BACKUP_V1","formatVersion":1,"appVersionName":"1.7.0","appVersionCode":41,"createdAt":1731571200000,"encrypted":true,"kdf":"PBKDF2WithHmacSHA256","kdfIterations":120000,"saltBase64":"AQIDBAUGBwgJCgsMDQ4PEA==","cipher":"AES/GCM/NoPadding","ivBase64":"oaKjpKWmp6ipqqus","gcmTagBits":128,"ciphertextBase64":"CSC233S0+7eSLIfV0yXB8qY0+1244/IZ0OY1B/lk1h7ywTKb49XbD1dHO3EpIgkmybthWy6+BwY/Eyx8Et/AJ7ywor113WLpR/aGUmJ0dMvignjksZ2LNUY8Aj8HZRe7cZBtGsP7/dHjA+03KQ3yoJspVHAlzqcdHq0qXaGprtvn2tvaKSI5O5RTuWbXH6uG989iRoVYhyNzzmKIBxA5LZk7TKIoHYoqG9GTb3chTKVh1IbWjnT/ldg8DXehlL345Eyjnx/Eh40SZlw9ogSzg5koHp2ZUah4yIPytoB69yVemu6buHx0fe+gO7lY9Yn+L2WNei/kzUl2Evsgh81U/XAnHeFHoLF0SAyo7vikaUJQzBJHGXVyp2664jSZWTFRkm4Vlyyo6sL+0C+EqOUy2IQbJR8zQ6pw6yLZ5JOzcPMacB7Z+duMrJdFMyWD2wwOq2ttYXvqyagRAcF3oV0OjnIxfaeF5BbSekRm8T9uVw=="}
        """
        let androidDecoded = try ThingsBackupCodec.decodeBackup(
            rawBytes: Data(androidVectorJson.utf8),
            password: "ThingsbookSecret2026!"
        )
        XCTAssertEqual(androidDecoded.areas.first?.title, "Android Synced Area")
        XCTAssertEqual(androidDecoded.tasks.first?.title, "Encrypted Cross-Platform Task")

        // Atomic restore into fresh DB
        let freshDb = try ThingsSQLiteDatabase(path: ":memory:")
        try freshDb.restoreBackupPayload(androidDecoded)
        let restoredSnap = try freshDb.loadWorkspaceSnapshot()
        XCTAssertEqual(restoredSnap.areas.map(\.title), ["Android Synced Area"])
        XCTAssertEqual(restoredSnap.allOpenTasks.map(\.title), ["Encrypted Cross-Platform Task"])
    }
}
