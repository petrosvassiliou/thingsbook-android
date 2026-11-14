import Foundation
import SQLite3

private let SQLITE_TRANSIENT_DESTRUCTOR = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

public enum ThingsDatabaseError: LocalizedError {
    case openFailed(String)
    case sqlError(String)
    case maxAreasReached(Int)
    case notFound(String)

    public var errorDescription: String? {
        switch self {
        case .openFailed(let msg): return "Failed to open Thingsbook SQLite database: \(msg)"
        case .sqlError(let msg): return "SQLite error: \(msg)"
        case .maxAreasReached(let max): return "You can create up to \(max) Areas."
        case .notFound(let id): return "Record not found (\(id))."
        }
    }
}

public struct SyncDirtyBatch: Sendable {
    public var tags: [Tag]
    public var areas: [Area]
    public var projects: [Project]
    public var headings: [Heading]
    public var tasks: [TaskItem]
    public var checklistItems: [ChecklistItem]
}

public struct RemoteDeltaBatch: Sendable {
    public var tags: [Tag]
    public var areas: [Area]
    public var projects: [Project]
    public var headings: [Heading]
    public var tasks: [TaskItem]
    public var taskTagIdsByTaskId: [String: [String]]
    public var checklistItems: [ChecklistItem]

    public init(
        tags: [Tag] = [],
        areas: [Area] = [],
        projects: [Project] = [],
        headings: [Heading] = [],
        tasks: [TaskItem] = [],
        taskTagIdsByTaskId: [String: [String]] = [:],
        checklistItems: [ChecklistItem] = []
    ) {
        self.tags = tags
        self.areas = areas
        self.projects = projects
        self.headings = headings
        self.tasks = tasks
        self.taskTagIdsByTaskId = taskTagIdsByTaskId
        self.checklistItems = checklistItems
    }
}

public struct WorkspaceSnapshot: Sendable {
    public var areas: [Area]
    public var projects: [Project]
    public var headingsByProject: [String: [Heading]]
    public var tags: [Tag]
    public var inboxTasks: [TaskItem]
    public var todayDaytimeTasks: [TaskItem]
    public var thisEveningTasks: [TaskItem]
    public var upcomingTasks: [TaskItem]
    public var anytimeTasks: [TaskItem]
    public var somedayTasks: [TaskItem]
    public var logbookTasks: [TaskItem]
    public var trashedTasks: [TaskItem]
    public var allOpenTasks: [TaskItem]
    public var statistics: DatabaseStatistics
    public var hasSampleTasks: Bool
    public var hasNonSampleUserTasks: Bool
}

public final class ThingsSQLiteDatabase: @unchecked Sendable {
    public static let maxAreas = 8

    private var db: OpaquePointer?
    private let queue = DispatchQueue(label: "com.thingsbook.macos.sqlite", qos: .userInitiated)
    private let dbPath: String
    public var onMutation: (@Sendable () -> Void)?

    public static func defaultDatabaseURL() -> URL {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        let folder = appSupport.appendingPathComponent("Thingsbook", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder.appendingPathComponent("thingsbook.sqlite3")
    }

    public init(path: String? = nil) throws {
        self.dbPath = path ?? Self.defaultDatabaseURL().path
        try queue.sync {
            try openAndMigrateLocked()
        }
    }

    deinit {
        queue.sync {
            if let db {
                sqlite3_close(db)
                self.db = nil
            }
        }
    }

    // MARK: - Schema & FTS5 Setup

    private func openAndMigrateLocked() throws {
        var handle: OpaquePointer?
        let flags = SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE | SQLITE_OPEN_FULLMUTEX
        if sqlite3_open_v2(dbPath, &handle, flags, nil) != SQLITE_OK {
            let msg = handle.flatMap { String(cString: sqlite3_errmsg($0)) } ?? "Unknown error"
            if let handle { sqlite3_close(handle) }
            throw ThingsDatabaseError.openFailed(msg)
        }
        self.db = handle

        try execLocked("PRAGMA journal_mode = WAL;")
        try execLocked("PRAGMA synchronous = NORMAL;")
        try execLocked("PRAGMA foreign_keys = ON;")

        try execLocked("""
        CREATE TABLE IF NOT EXISTS areas (
            id TEXT PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            sort_order REAL NOT NULL DEFAULT 0.0,
            tag_ids_json TEXT NOT NULL DEFAULT '[]',
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            is_deleted INTEGER NOT NULL DEFAULT 0,
            is_dirty INTEGER NOT NULL DEFAULT 1
        );

        CREATE TABLE IF NOT EXISTS projects (
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
            is_dirty INTEGER NOT NULL DEFAULT 1
        );
        CREATE INDEX IF NOT EXISTS idx_projects_area ON projects(area_id);
        CREATE INDEX IF NOT EXISTS idx_projects_status_sched ON projects(status, schedule_state);

        CREATE TABLE IF NOT EXISTS headings (
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
        CREATE INDEX IF NOT EXISTS idx_headings_project ON headings(project_id);

        CREATE TABLE IF NOT EXISTS tasks (
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
            is_dirty INTEGER NOT NULL DEFAULT 1
        );
        CREATE INDEX IF NOT EXISTS idx_tasks_sched_status ON tasks(schedule_state, status, sort_order);
        CREATE INDEX IF NOT EXISTS idx_tasks_when ON tasks(when_date);
        CREATE INDEX IF NOT EXISTS idx_tasks_deadline ON tasks(deadline);
        CREATE INDEX IF NOT EXISTS idx_tasks_project ON tasks(project_id);
        CREATE INDEX IF NOT EXISTS idx_tasks_heading ON tasks(heading_id);
        CREATE INDEX IF NOT EXISTS idx_tasks_area ON tasks(area_id);

        CREATE TABLE IF NOT EXISTS checklist_items (
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
        CREATE INDEX IF NOT EXISTS idx_chk_task ON checklist_items(task_id, sort_order);

        CREATE TABLE IF NOT EXISTS tags (
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
        CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_title ON tags(title);

        CREATE TABLE IF NOT EXISTS task_tags (
            task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
            tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
            PRIMARY KEY (task_id, tag_id)
        );
        CREATE INDEX IF NOT EXISTS idx_task_tags_tag ON task_tags(tag_id);

        CREATE TABLE IF NOT EXISTS project_tags (
            project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
            tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
            PRIMARY KEY (project_id, tag_id)
        );
        CREATE INDEX IF NOT EXISTS idx_proj_tags_tag ON project_tags(tag_id);
        """)

        try installFts5Locked()
    }

    private func installFts5Locked() throws {
        try execLocked("""
        CREATE VIRTUAL TABLE IF NOT EXISTS tasks_fts USING fts5(
            title,
            notes,
            content='tasks',
            content_rowid='rowid',
            tokenize = 'unicode61 remove_diacritics 2'
        );

        CREATE TRIGGER IF NOT EXISTS tasks_ai AFTER INSERT ON tasks BEGIN
            INSERT INTO tasks_fts(rowid, title, notes) VALUES (new.rowid, new.title, new.notes);
        END;
        CREATE TRIGGER IF NOT EXISTS tasks_ad AFTER DELETE ON tasks BEGIN
            INSERT INTO tasks_fts(tasks_fts, rowid, title, notes) VALUES ('delete', old.rowid, old.title, old.notes);
        END;
        CREATE TRIGGER IF NOT EXISTS tasks_au AFTER UPDATE ON tasks BEGIN
            INSERT INTO tasks_fts(tasks_fts, rowid, title, notes) VALUES ('delete', old.rowid, old.title, old.notes);
            INSERT INTO tasks_fts(rowid, title, notes) VALUES (new.rowid, new.title, new.notes);
        END;

        CREATE VIRTUAL TABLE IF NOT EXISTS projects_fts USING fts5(
            title,
            notes,
            content='projects',
            content_rowid='rowid',
            tokenize = 'unicode61 remove_diacritics 2'
        );

        CREATE TRIGGER IF NOT EXISTS projects_ai AFTER INSERT ON projects BEGIN
            INSERT INTO projects_fts(rowid, title, notes) VALUES (new.rowid, new.title, new.notes);
        END;
        CREATE TRIGGER IF NOT EXISTS projects_ad AFTER DELETE ON projects BEGIN
            INSERT INTO projects_fts(projects_fts, rowid, title, notes) VALUES ('delete', old.rowid, old.title, old.notes);
        END;
        CREATE TRIGGER IF NOT EXISTS projects_au AFTER UPDATE ON projects BEGIN
            INSERT INTO projects_fts(projects_fts, rowid, title, notes) VALUES ('delete', old.rowid, old.title, old.notes);
            INSERT INTO projects_fts(rowid, title, notes) VALUES (new.rowid, new.title, new.notes);
        END;
        """)
    }

    private func rebuildFtsLocked() {
        try? execLocked("INSERT INTO tasks_fts(tasks_fts) VALUES('rebuild');")
        try? execLocked("INSERT INTO projects_fts(projects_fts) VALUES('rebuild');")
    }

    // MARK: - Low-Level Helpers

    private func execLocked(_ sql: String) throws {
        var errMsg: UnsafeMutablePointer<CChar>?
        if sqlite3_exec(db, sql, nil, nil, &errMsg) != SQLITE_OK {
            let message = errMsg.map { String(cString: $0) } ?? currentErrorMessageLocked()
            if let errMsg { sqlite3_free(errMsg) }
            throw ThingsDatabaseError.sqlError(message)
        }
    }

    private func prepareLocked(_ sql: String) throws -> OpaquePointer {
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) != SQLITE_OK || stmt == nil {
            throw ThingsDatabaseError.sqlError("\(currentErrorMessageLocked()) [SQL: \(sql)]")
        }
        return stmt!
    }

    private func currentErrorMessageLocked() -> String {
        guard let db, let cStr = sqlite3_errmsg(db) else { return "Unknown SQLite error" }
        return String(cString: cStr)
    }

    private func inTransactionLocked<T>(_ block: () throws -> T) throws -> T {
        try execLocked("BEGIN IMMEDIATE TRANSACTION;")
        do {
            let result = try block()
            try execLocked("COMMIT;")
            return result
        } catch {
            try? execLocked("ROLLBACK;")
            throw error
        }
    }

    private func bindText(_ stmt: OpaquePointer, _ index: Int32, _ value: String?) {
        if let value {
            sqlite3_bind_text(stmt, index, value, -1, SQLITE_TRANSIENT_DESTRUCTOR)
        } else {
            sqlite3_bind_null(stmt, index)
        }
    }

    private func bindInt64(_ stmt: OpaquePointer, _ index: Int32, _ value: Int64?) {
        if let value {
            sqlite3_bind_int64(stmt, index, value)
        } else {
            sqlite3_bind_null(stmt, index)
        }
    }

    private func bindBool(_ stmt: OpaquePointer, _ index: Int32, _ value: Bool) {
        sqlite3_bind_int(stmt, index, value ? 1 : 0)
    }

    private func colString(_ stmt: OpaquePointer, _ index: Int32) -> String? {
        guard sqlite3_column_type(stmt, index) != SQLITE_NULL,
              let ptr = sqlite3_column_text(stmt, index) else { return nil }
        return String(cString: ptr)
    }

    private func colInt64(_ stmt: OpaquePointer, _ index: Int32) -> Int64? {
        guard sqlite3_column_type(stmt, index) != SQLITE_NULL else { return nil }
        return sqlite3_column_int64(stmt, index)
    }

    private func colBool(_ stmt: OpaquePointer, _ index: Int32) -> Bool {
        sqlite3_column_int(stmt, index) != 0
    }

    private func nowMillis() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000.0)
    }

    private func notifyMutation() {
        onMutation?()
    }

    private func encodeStringArray(_ items: [String]) -> String {
        guard let data = try? JSONEncoder().encode(items),
              let str = String(data: data, encoding: .utf8) else { return "[]" }
        return str
    }

    private func decodeStringArray(_ json: String?) -> [String] {
        guard let json, let data = json.data(using: .utf8),
              let arr = try? JSONDecoder().decode([String].self, from: data) else { return [] }
        return arr
    }

    private func encodeRecurrence(_ rule: RecurrenceRule?) -> String? {
        guard let rule,
              let data = try? JSONEncoder().encode(rule),
              let str = String(data: data, encoding: .utf8) else { return nil }
        return str
    }

    private func decodeRecurrence(_ raw: String?) -> RecurrenceRule? {
        guard let raw, !raw.isEmpty, let data = raw.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(RecurrenceRule.self, from: data)
    }

    public static func computeMidpointOrder(prevOrder: Double?, nextOrder: Double?) -> Double {
        switch (prevOrder, nextOrder) {
        case (nil, nil): return 1000.0
        case (nil, let next?): return next - 500.0
        case (let prev?, nil): return prev + 1000.0
        case (let prev?, let next?): return (prev + next) / 2.0
        }
    }

    // MARK: - Seed Onboarding Data

    public func seedIfEmpty(languageCode: String, referenceDate: Date = Date(), calendar: Calendar = .current) throws {
        let didSeed: Bool = try queue.sync {
            let countStmt = try prepareLocked("SELECT COUNT(*) FROM tasks;")
            defer { sqlite3_finalize(countStmt) }
            var totalTasks = 0
            if sqlite3_step(countStmt) == SQLITE_ROW {
                totalTasks = Int(sqlite3_column_int(countStmt, 0))
            }
            guard totalTasks == 0 else { return false }

            let bundle = DatabaseSeeder.buildBundle(languageCode: languageCode, referenceDate: referenceDate, calendar: calendar)
            try inTransactionLocked {
                for tag in bundle.tags {
                    try upsertTagLocked(tag, isDirty: false)
                }
                for area in bundle.areas {
                    try upsertAreaLocked(area, isDirty: false)
                }
                for project in bundle.projects {
                    try upsertProjectLocked(project, isDirty: false)
                }
                for heading in bundle.headings {
                    try upsertHeadingLocked(heading, isDirty: false)
                }
                for task in bundle.tasks {
                    try upsertTaskLocked(task, tagIds: task.tagIds, isDirty: false)
                }
                for chk in bundle.checklistItems {
                    try upsertChecklistItemLocked(chk, isDirty: false)
                }
            }
            return true
        }
        if didSeed { notifyMutation() }
    }

    // MARK: - Snapshot & Queries

    public func loadWorkspaceSnapshot(referenceDate: Date = Date(), calendar: Calendar = .current) throws -> WorkspaceSnapshot {
        try queue.sync {
            let areas = try fetchActiveAreasLocked()
            let tags = try fetchActiveTagsLocked()
            let tagsById = Dictionary(uniqueKeysWithValues: tags.map { ($0.id, $0) })
            let projects = try fetchActiveProjectsLocked()
            let headings = try fetchAllActiveHeadingsLocked()
            let headingsByProject = Dictionary(grouping: headings, by: \.projectId)

            let checklistsByTask = try fetchAllActiveChecklistItemsByTaskLocked()
            let tagIdsByTask = try fetchAllTaskTagIdsLocked()

            let endOfToday = DateEpochConverter.endOfDayMillis(for: referenceDate, calendar: calendar)
            let startOfTomorrow: Int64 = {
                let sod = calendar.startOfDay(for: referenceDate)
                let tmr = calendar.date(byAdding: .day, value: 1, to: sod) ?? sod.addingTimeInterval(86_400)
                return DateEpochConverter.startOfDayMillis(for: tmr, calendar: calendar)
            }()

            let allTasks = try fetchTasksWhereLocked(
                whereClause: "1 = 1",
                bind: { _ in },
                orderBy: "sort_order ASC, created_at ASC",
                tagsById: tagsById,
                tagIdsByTask: tagIdsByTask,
                checklistsByTask: checklistsByTask
            )

            var inbox: [TaskItem] = []
            var todayDaytime: [TaskItem] = []
            var thisEvening: [TaskItem] = []
            var upcoming: [TaskItem] = []
            var anytime: [TaskItem] = []
            var someday: [TaskItem] = []
            var logbook: [TaskItem] = []
            var trashed: [TaskItem] = []
            var allOpen: [TaskItem] = []

            for t in allTasks {
                if t.isDeleted {
                    trashed.append(t)
                    continue
                }
                if t.status == .completed || t.status == .canceled {
                    logbook.append(t)
                    continue
                }
                allOpen.append(t)

                if t.scheduleState == .inbox {
                    inbox.append(t)
                }
                if t.scheduleState == .thisEvening {
                    thisEvening.append(t)
                } else if t.scheduleState == .today || (t.whenDate != nil && t.whenDate! <= endOfToday) {
                    todayDaytime.append(t)
                }
                if t.scheduleState == .upcoming || (t.whenDate != nil && t.whenDate! >= startOfTomorrow) {
                    upcoming.append(t)
                }
                if t.scheduleState == .anytime || t.scheduleState == .today || t.scheduleState == .thisEvening {
                    anytime.append(t)
                }
                if t.scheduleState == .someday {
                    someday.append(t)
                }
            }

            todayDaytime.sort {
                let d0 = $0.deadline == nil ? 1 : 0
                let d1 = $1.deadline == nil ? 1 : 0
                if d0 != d1 { return d0 < d1 }
                if let dl0 = $0.deadline, let dl1 = $1.deadline, dl0 != dl1 { return dl0 < dl1 }
                return $0.sortOrder < $1.sortOrder
            }
            upcoming.sort {
                let w0 = $0.whenDate ?? Int64.max
                let w1 = $1.whenDate ?? Int64.max
                if w0 != w1 { return w0 < w1 }
                return $0.sortOrder < $1.sortOrder
            }
            logbook.sort { ($0.completedAt ?? $0.updatedAt) > ($1.completedAt ?? $1.updatedAt) }
            trashed.sort { $0.updatedAt > $1.updatedAt }

            let activeTasksNonDeleted = allTasks.filter { !$0.isDeleted }
            let hasSample = activeTasksNonDeleted.contains { DatabaseSeeder.sampleTaskIds.contains($0.id) }
            let hasNonSample = activeTasksNonDeleted.contains { !DatabaseSeeder.sampleTaskIds.contains($0.id) }

            let stats = try computeStatisticsLocked(endOfToday: endOfToday)

            return WorkspaceSnapshot(
                areas: areas,
                projects: projects,
                headingsByProject: headingsByProject,
                tags: tags,
                inboxTasks: inbox,
                todayDaytimeTasks: todayDaytime,
                thisEveningTasks: thisEvening,
                upcomingTasks: upcoming,
                anytimeTasks: anytime,
                somedayTasks: someday,
                logbookTasks: logbook,
                trashedTasks: trashed,
                allOpenTasks: allOpen,
                statistics: stats,
                hasSampleTasks: hasSample,
                hasNonSampleUserTasks: hasNonSample
            )
        }
    }

    public func searchQuickFind(rawQuery: String) throws -> (tasks: [TaskItem], projects: [Project]) {
        let sanitized = Self.buildSafeFtsQuery(rawQuery)
        guard !sanitized.isEmpty else { return ([], []) }

        return try queue.sync {
            let tags = try fetchActiveTagsLocked()
            let tagsById = Dictionary(uniqueKeysWithValues: tags.map { ($0.id, $0) })
            let checklistsByTask = try fetchAllActiveChecklistItemsByTaskLocked()
            let tagIdsByTask = try fetchAllTaskTagIdsLocked()

            let taskStmt = try prepareLocked("""
                SELECT t.id, t.title, t.notes, t.status, t.schedule_state, t.when_date, t.deadline,
                       t.reminder_time, t.completed_at, t.project_id, t.heading_id, t.area_id,
                       t.recurrence_rule, t.sort_order, t.tag_ids_json, t.created_at, t.updated_at, t.is_deleted
                FROM tasks t
                JOIN tasks_fts fts ON t.rowid = fts.rowid
                WHERE tasks_fts MATCH ? AND t.is_deleted = 0
                ORDER BY bm25(tasks_fts), t.updated_at DESC
                LIMIT 50;
            """)
            defer { sqlite3_finalize(taskStmt) }
            bindText(taskStmt, 1, sanitized)

            var matchedTasks: [TaskItem] = []
            while sqlite3_step(taskStmt) == SQLITE_ROW {
                matchedTasks.append(readTaskRowLocked(taskStmt, tagsById: tagsById, tagIdsByTask: tagIdsByTask, checklistsByTask: checklistsByTask))
            }

            let projStmt = try prepareLocked("""
                SELECT p.id, p.area_id, p.title, p.notes, p.status, p.schedule_state, p.when_date,
                       p.deadline, p.completed_at, p.sort_order, p.tag_ids_json, p.created_at, p.updated_at, p.is_deleted,
                       (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status != 'CANCELED') AS total_cnt,
                       (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status = 'COMPLETED') AS comp_cnt
                FROM projects p
                JOIN projects_fts fts ON p.rowid = fts.rowid
                WHERE projects_fts MATCH ? AND p.is_deleted = 0
                ORDER BY bm25(projects_fts), p.sort_order ASC
                LIMIT 25;
            """)
            defer { sqlite3_finalize(projStmt) }
            bindText(projStmt, 1, sanitized)

            var matchedProjects: [Project] = []
            while sqlite3_step(projStmt) == SQLITE_ROW {
                matchedProjects.append(readProjectRowLocked(projStmt))
            }

            return (matchedTasks, matchedProjects)
        }
    }

    public static func buildSafeFtsQuery(_ rawInput: String) -> String {
        let tokens = rawInput
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"[^\p{L}\p{N}\s]"#, with: " ", options: .regularExpression)
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
        guard !tokens.isEmpty else { return "" }
        return tokens.map { "\"\($0)\"*" }.joined(separator: " AND ")
    }

    // MARK: - Midnight Rollover

    @discardableResult
    public func performMidnightRollover(referenceDate: Date = Date(), calendar: Calendar = .current) throws -> Int {
        let changedCount: Int = try queue.sync {
            let endOfToday = DateEpochConverter.endOfDayMillis(for: referenceDate, calendar: calendar)
            let sodToday = DateEpochConverter.startOfDayMillis(for: referenceDate, calendar: calendar)
            let now = nowMillis()

            return try inTransactionLocked {
                var total = 0
                let stmt1 = try prepareLocked("""
                    UPDATE tasks
                    SET schedule_state = 'TODAY', updated_at = ?, is_dirty = 1
                    WHERE status = 'OPEN' AND is_deleted = 0
                      AND schedule_state = 'UPCOMING'
                      AND when_date IS NOT NULL AND when_date <= ?;
                """)
                defer { sqlite3_finalize(stmt1) }
                bindInt64(stmt1, 1, now)
                bindInt64(stmt1, 2, endOfToday)
                if sqlite3_step(stmt1) == SQLITE_DONE {
                    total += Int(sqlite3_changes(db))
                }

                let stmt2 = try prepareLocked("""
                    UPDATE tasks
                    SET schedule_state = 'TODAY', when_date = ?, updated_at = ?, is_dirty = 1
                    WHERE status = 'OPEN' AND is_deleted = 0
                      AND schedule_state = 'THIS_EVENING'
                      AND when_date IS NOT NULL AND when_date < ?;
                """)
                defer { sqlite3_finalize(stmt2) }
                bindInt64(stmt2, 1, sodToday)
                bindInt64(stmt2, 2, now)
                bindInt64(stmt2, 3, sodToday)
                if sqlite3_step(stmt2) == SQLITE_DONE {
                    total += Int(sqlite3_changes(db))
                }
                return total
            }
        }
        if changedCount > 0 { notifyMutation() }
        return changedCount
    }

    // MARK: - Task Mutations

    @discardableResult
    public func createTask(
        id: String = UUID().uuidString,
        title: String,
        notes: String = "",
        scheduleState: ScheduleState = .inbox,
        whenDate: Int64? = nil,
        deadline: Int64? = nil,
        reminderTime: Int64? = nil,
        projectId: String? = nil,
        headingId: String? = nil,
        areaId: String? = nil,
        recurrenceRule: RecurrenceRule? = nil,
        tagIds: [String] = [],
        checklistTitles: [String] = []
    ) throws -> TaskItem {
        let created = try queue.sync { () -> TaskItem in
            let now = nowMillis()
            return try inTransactionLocked {
                let maxOrder = try maxSortOrderLocked(table: "tasks")
                var resolvedAreaId = areaId
                if resolvedAreaId == nil, let projectId, let proj = try fetchProjectByIdLocked(projectId) {
                    resolvedAreaId = proj.areaId
                }

                var items: [ChecklistItem] = []
                for (idx, rawTitle) in checklistTitles.enumerated() {
                    let clean = rawTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !clean.isEmpty else { continue }
                    items.append(
                        ChecklistItem(
                            id: UUID().uuidString,
                            taskId: id,
                            title: clean,
                            isCompleted: false,
                            sortOrder: Double((idx + 1) * 100),
                            createdAt: now,
                            updatedAt: now
                        )
                    )
                }

                let tags = try fetchTagsByIdsLocked(tagIds)
                let task = TaskItem(
                    id: id,
                    title: title,
                    notes: notes,
                    status: .open,
                    scheduleState: scheduleState,
                    whenDate: whenDate,
                    deadline: deadline,
                    reminderTime: reminderTime,
                    projectId: projectId,
                    headingId: headingId,
                    areaId: resolvedAreaId,
                    recurrenceRule: recurrenceRule,
                    sortOrder: maxOrder + 1000.0,
                    checklistItems: items,
                    tags: tags,
                    createdAt: now,
                    updatedAt: now
                )
                try upsertTaskLocked(task, tagIds: tagIds, isDirty: true)
                for item in items {
                    try upsertChecklistItemLocked(item, isDirty: true)
                }
                return task
            }
        }
        notifyMutation()
        return created
    }

    public func updateTask(_ task: TaskItem, tagIds: [String]? = nil) throws {
        try queue.sync {
            var updated = task
            updated.updatedAt = nowMillis()
            let finalTagIds = tagIds ?? task.tagIds
            try inTransactionLocked {
                try upsertTaskLocked(updated, tagIds: finalTagIds, isDirty: true)
            }
        }
        notifyMutation()
    }

    /// Marks task completed/open/canceled. If completing an open recurring task, spawns the next occurrence and returns its ID.
    @discardableResult
    public func setTaskStatus(
        taskId: String,
        status: TaskStatus,
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) throws -> String? {
        let spawnedId: String? = try queue.sync {
            guard var existing = try fetchTaskByIdLocked(taskId) else { return nil }
            let previousStatus = existing.status
            let now = Int64(referenceDate.timeIntervalSince1970 * 1000.0)
            existing.status = status
            existing.completedAt = (status == .completed || status == .canceled) ? now : nil
            existing.updatedAt = now

            return try inTransactionLocked {
                try upsertTaskLocked(existing, tagIds: existing.tagIds, isDirty: true)

                if status == .completed, previousStatus == .open, let rule = existing.recurrenceRule {
                    return try spawnRecurringOccurrenceLocked(from: existing, rule: rule, nowMillis: now, referenceDate: referenceDate, calendar: calendar)
                }
                return nil
            }
        }
        notifyMutation()
        return spawnedId
    }

    private func spawnRecurringOccurrenceLocked(
        from completedTask: TaskItem,
        rule: RecurrenceRule,
        nowMillis: Int64,
        referenceDate: Date,
        calendar: Calendar
    ) throws -> String? {
        guard let nextDate = WhenScheduleResolver.computeNextOccurrenceDate(
            rule: rule,
            completedWhenMillis: completedTask.whenDate,
            nowMillis: nowMillis,
            calendar: calendar
        ) else {
            return nil
        }

        let nextWhenEpoch = DateEpochConverter.startOfDayMillis(for: nextDate, calendar: calendar)
        let todayEpoch = DateEpochConverter.startOfDayMillis(for: referenceDate, calendar: calendar)

        let nextSchedule: ScheduleState
        if nextWhenEpoch <= todayEpoch {
            nextSchedule = completedTask.scheduleState == .thisEvening ? .thisEvening : .today
        } else {
            nextSchedule = .upcoming
        }

        var nextDeadline: Int64? = nil
        if let oldDeadline = completedTask.deadline {
            let baseStart = completedTask.whenDate ?? todayEpoch
            let delta = oldDeadline - baseStart
            nextDeadline = nextWhenEpoch + max(0, delta)
        }

        var nextReminder: Int64? = nil
        if let oldReminder = completedTask.reminderTime {
            let oldRemDate = DateEpochConverter.date(fromMillis: oldReminder)
            let comps = calendar.dateComponents([.hour, .minute], from: oldRemDate)
            nextReminder = WhenScheduleResolver.computeReminderEpoch(
                targetDate: nextDate,
                calendar: calendar,
                reminderEnabled: true,
                reminderHour: comps.hour ?? 9,
                reminderMinute: comps.minute ?? 0
            )
        }

        let newTaskId = UUID().uuidString
        let maxOrder = try maxSortOrderLocked(table: "tasks")

        let clonedChecklists = completedTask.checklistItems.map { chk in
            ChecklistItem(
                id: UUID().uuidString,
                taskId: newTaskId,
                title: chk.title,
                isCompleted: false,
                sortOrder: chk.sortOrder,
                createdAt: nowMillis,
                updatedAt: nowMillis
            )
        }

        let spawned = TaskItem(
            id: newTaskId,
            title: completedTask.title,
            notes: completedTask.notes,
            status: .open,
            scheduleState: nextSchedule,
            whenDate: nextWhenEpoch,
            deadline: nextDeadline,
            reminderTime: nextReminder,
            completedAt: nil,
            projectId: completedTask.projectId,
            headingId: completedTask.headingId,
            areaId: completedTask.areaId,
            recurrenceRule: rule,
            sortOrder: maxOrder + 1000.0,
            checklistItems: clonedChecklists,
            tags: completedTask.tags,
            createdAt: nowMillis,
            updatedAt: nowMillis
        )

        try upsertTaskLocked(spawned, tagIds: completedTask.tagIds, isDirty: true)
        for chk in clonedChecklists {
            try upsertChecklistItemLocked(chk, isDirty: true)
        }
        return newTaskId
    }

    public func softDeleteTask(_ taskId: String) throws {
        try queue.sync {
            let now = nowMillis()
            let stmt = try prepareLocked("UPDATE tasks SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE id = ?;")
            defer { sqlite3_finalize(stmt) }
            bindInt64(stmt, 1, now)
            bindText(stmt, 2, taskId)
            _ = sqlite3_step(stmt)
        }
        notifyMutation()
    }

    public func restoreTask(_ taskId: String) throws {
        try queue.sync {
            let now = nowMillis()
            let stmt = try prepareLocked("UPDATE tasks SET is_deleted = 0, updated_at = ?, is_dirty = 1 WHERE id = ?;")
            defer { sqlite3_finalize(stmt) }
            bindInt64(stmt, 1, now)
            bindText(stmt, 2, taskId)
            _ = sqlite3_step(stmt)
        }
        notifyMutation()
    }

    public func permanentlyDeleteTask(_ taskId: String) throws {
        try queue.sync {
            let stmt = try prepareLocked("DELETE FROM tasks WHERE id = ?;")
            defer { sqlite3_finalize(stmt) }
            bindText(stmt, 1, taskId)
            _ = sqlite3_step(stmt)
        }
        notifyMutation()
    }

    public func emptyTrash() throws {
        try queue.sync {
            try execLocked("DELETE FROM tasks WHERE is_deleted = 1;")
        }
        notifyMutation()
    }

    @discardableResult
    public func duplicateTask(_ taskId: String) throws -> TaskItem? {
        let duplicated: TaskItem? = try queue.sync {
            guard let source = try fetchTaskByIdLocked(taskId) else { return nil }
            let now = nowMillis()
            let newId = UUID().uuidString
            return try inTransactionLocked {
                let clonedChecklists = source.checklistItems.map { chk in
                    ChecklistItem(
                        id: UUID().uuidString,
                        taskId: newId,
                        title: chk.title,
                        isCompleted: chk.isCompleted,
                        sortOrder: chk.sortOrder,
                        createdAt: now,
                        updatedAt: now
                    )
                }
                let copy = TaskItem(
                    id: newId,
                    title: source.title,
                    notes: source.notes,
                    status: .open,
                    scheduleState: source.scheduleState,
                    whenDate: source.whenDate,
                    deadline: source.deadline,
                    reminderTime: source.reminderTime,
                    completedAt: nil,
                    projectId: source.projectId,
                    headingId: source.headingId,
                    areaId: source.areaId,
                    recurrenceRule: source.recurrenceRule,
                    sortOrder: source.sortOrder + 0.5,
                    checklistItems: clonedChecklists,
                    tags: source.tags,
                    createdAt: now,
                    updatedAt: now
                )
                try upsertTaskLocked(copy, tagIds: source.tagIds, isDirty: true)
                for chk in clonedChecklists {
                    try upsertChecklistItemLocked(chk, isDirty: true)
                }
                return copy
            }
        }
        if duplicated != nil { notifyMutation() }
        return duplicated
    }

    public func reorderTask(taskId: String, prevOrder: Double?, nextOrder: Double?, newHeadingId: String?? = nil) throws {
        try queue.sync {
            let newOrder = Self.computeMidpointOrder(prevOrder: prevOrder, nextOrder: nextOrder)
            let now = nowMillis()
            if let headingOverride = newHeadingId {
                let stmt = try prepareLocked("UPDATE tasks SET sort_order = ?, heading_id = ?, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(stmt) }
                sqlite3_bind_double(stmt, 1, newOrder)
                bindText(stmt, 2, headingOverride)
                bindInt64(stmt, 3, now)
                bindText(stmt, 4, taskId)
                _ = sqlite3_step(stmt)
            } else {
                let stmt = try prepareLocked("UPDATE tasks SET sort_order = ?, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(stmt) }
                sqlite3_bind_double(stmt, 1, newOrder)
                bindInt64(stmt, 2, now)
                bindText(stmt, 3, taskId)
                _ = sqlite3_step(stmt)
            }
        }
        notifyMutation()
    }

    // MARK: - Checklist Mutations

    @discardableResult
    public func addChecklistItem(taskId: String, title: String) throws -> ChecklistItem {
        let item = try queue.sync { () -> ChecklistItem in
            let now = nowMillis()
            let stmt = try prepareLocked("SELECT COALESCE(MAX(sort_order), 0.0) FROM checklist_items WHERE task_id = ? AND is_deleted = 0;")
            defer { sqlite3_finalize(stmt) }
            bindText(stmt, 1, taskId)
            var maxOrd = 0.0
            if sqlite3_step(stmt) == SQLITE_ROW {
                maxOrd = sqlite3_column_double(stmt, 0)
            }
            let created = ChecklistItem(
                id: UUID().uuidString,
                taskId: taskId,
                title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                isCompleted: false,
                sortOrder: maxOrd + 100.0,
                createdAt: now,
                updatedAt: now
            )
            try inTransactionLocked {
                try upsertChecklistItemLocked(created, isDirty: true)
                try touchTaskUpdatedLocked(taskId: taskId, now: now)
            }
            return created
        }
        notifyMutation()
        return item
    }

    public func updateChecklistItem(_ item: ChecklistItem) throws {
        try queue.sync {
            var copy = item
            let now = nowMillis()
            copy.updatedAt = now
            try inTransactionLocked {
                try upsertChecklistItemLocked(copy, isDirty: true)
                try touchTaskUpdatedLocked(taskId: copy.taskId, now: now)
            }
        }
        notifyMutation()
    }

    public func deleteChecklistItem(id: String, taskId: String) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                let stmt = try prepareLocked("UPDATE checklist_items SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(stmt) }
                bindInt64(stmt, 1, now)
                bindText(stmt, 2, id)
                _ = sqlite3_step(stmt)
                try touchTaskUpdatedLocked(taskId: taskId, now: now)
            }
        }
        notifyMutation()
    }

    private func touchTaskUpdatedLocked(taskId: String, now: Int64) throws {
        let stmt = try prepareLocked("UPDATE tasks SET updated_at = ?, is_dirty = 1 WHERE id = ?;")
        defer { sqlite3_finalize(stmt) }
        bindInt64(stmt, 1, now)
        bindText(stmt, 2, taskId)
        _ = sqlite3_step(stmt)
    }

    // MARK: - Areas, Projects, Headings, Tags

    @discardableResult
    public func createArea(title: String, tagIds: [String] = []) throws -> Area {
        let area = try queue.sync { () -> Area in
            let activeAreas = try fetchActiveAreasLocked()
            if activeAreas.count >= Self.maxAreas {
                throw ThingsDatabaseError.maxAreasReached(Self.maxAreas)
            }
            let now = nowMillis()
            let maxOrder = activeAreas.map(\.sortOrder).max() ?? 0.0
            let newArea = Area(
                id: UUID().uuidString,
                title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                sortOrder: maxOrder + 1000.0,
                tagIds: tagIds,
                createdAt: now,
                updatedAt: now
            )
            try upsertAreaLocked(newArea, isDirty: true)
            return newArea
        }
        notifyMutation()
        return area
    }

    public func updateArea(_ area: Area) throws {
        try queue.sync {
            var copy = area
            copy.updatedAt = nowMillis()
            try upsertAreaLocked(copy, isDirty: true)
        }
        notifyMutation()
    }

    public func deleteArea(_ areaId: String) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                let s1 = try prepareLocked("UPDATE projects SET area_id = NULL, updated_at = ?, is_dirty = 1 WHERE area_id = ?;")
                defer { sqlite3_finalize(s1) }
                bindInt64(s1, 1, now)
                bindText(s1, 2, areaId)
                _ = sqlite3_step(s1)

                let s2 = try prepareLocked("UPDATE tasks SET area_id = NULL, updated_at = ?, is_dirty = 1 WHERE area_id = ?;")
                defer { sqlite3_finalize(s2) }
                bindInt64(s2, 1, now)
                bindText(s2, 2, areaId)
                _ = sqlite3_step(s2)

                let s3 = try prepareLocked("UPDATE areas SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(s3) }
                bindInt64(s3, 1, now)
                bindText(s3, 2, areaId)
                _ = sqlite3_step(s3)
            }
        }
        notifyMutation()
    }

    @discardableResult
    public func createProject(
        title: String,
        notes: String = "",
        areaId: String? = nil,
        scheduleState: ScheduleState = .anytime,
        whenDate: Int64? = nil,
        deadline: Int64? = nil,
        tagIds: [String] = []
    ) throws -> Project {
        let proj = try queue.sync { () -> Project in
            let now = nowMillis()
            let maxOrd = try maxSortOrderLocked(table: "projects")
            let p = Project(
                id: UUID().uuidString,
                areaId: areaId,
                title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                notes: notes,
                status: .active,
                scheduleState: scheduleState,
                whenDate: whenDate,
                deadline: deadline,
                sortOrder: maxOrd + 1000.0,
                tagIds: tagIds,
                createdAt: now,
                updatedAt: now
            )
            try inTransactionLocked {
                try upsertProjectLocked(p, isDirty: true)
            }
            return p
        }
        notifyMutation()
        return proj
    }

    public func updateProject(_ project: Project) throws {
        try queue.sync {
            var copy = project
            copy.updatedAt = nowMillis()
            try inTransactionLocked {
                try upsertProjectLocked(copy, isDirty: true)
            }
        }
        notifyMutation()
    }

    public func setProjectStatus(projectId: String, status: ProjectStatus) throws {
        try queue.sync {
            guard var p = try fetchProjectByIdLocked(projectId) else { return }
            let now = nowMillis()
            p.status = status
            p.completedAt = (status == .completed || status == .canceled) ? now : nil
            p.updatedAt = now
            try inTransactionLocked {
                try upsertProjectLocked(p, isDirty: true)
            }
        }
        notifyMutation()
    }

    /// Soft-deletes project and detaches its open tasks so they aren't lost; returns detached task map for Undo.
    public func softDeleteProject(_ projectId: String) throws -> [String: DetachedTaskPlacement] {
        let detached = try queue.sync { () -> [String: DetachedTaskPlacement] in
            let now = nowMillis()
            return try inTransactionLocked {
                var previousMap: [String: DetachedTaskPlacement] = [:]
                let q = try prepareLocked("SELECT id, project_id, heading_id FROM tasks WHERE project_id = ? AND is_deleted = 0;")
                defer { sqlite3_finalize(q) }
                bindText(q, 1, projectId)
                while sqlite3_step(q) == SQLITE_ROW {
                    if let tid = colString(q, 0) {
                        previousMap[tid] = DetachedTaskPlacement(projectId: colString(q, 1), headingId: colString(q, 2))
                    }
                }

                let detachStmt = try prepareLocked("UPDATE tasks SET project_id = NULL, heading_id = NULL, updated_at = ?, is_dirty = 1 WHERE project_id = ?;")
                defer { sqlite3_finalize(detachStmt) }
                bindInt64(detachStmt, 1, now)
                bindText(detachStmt, 2, projectId)
                _ = sqlite3_step(detachStmt)

                let delStmt = try prepareLocked("UPDATE projects SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(delStmt) }
                bindInt64(delStmt, 1, now)
                bindText(delStmt, 2, projectId)
                _ = sqlite3_step(delStmt)

                return previousMap
            }
        }
        notifyMutation()
        return detached
    }

    public func restoreDeletedProject(projectId: String, previousTasks: [String: DetachedTaskPlacement]) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                let s1 = try prepareLocked("UPDATE projects SET is_deleted = 0, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(s1) }
                bindInt64(s1, 1, now)
                bindText(s1, 2, projectId)
                _ = sqlite3_step(s1)

                for (tid, refs) in previousTasks {
                    let s2 = try prepareLocked("UPDATE tasks SET project_id = ?, heading_id = ?, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                    defer { sqlite3_finalize(s2) }
                    bindText(s2, 1, refs.projectId)
                    bindText(s2, 2, refs.headingId)
                    bindInt64(s2, 3, now)
                    bindText(s2, 4, tid)
                    _ = sqlite3_step(s2)
                }
            }
        }
        notifyMutation()
    }

    @discardableResult
    public func duplicateProject(_ projectId: String, copySuffix: String = "Copy") throws -> Project? {
        let duplicated: Project? = try queue.sync {
            guard let source = try fetchProjectByIdLocked(projectId) else { return nil }
            let now = nowMillis()
            let newProjId = UUID().uuidString

            return try inTransactionLocked {
                let newProject = Project(
                    id: newProjId,
                    areaId: source.areaId,
                    title: "\(source.title) (\(copySuffix))",
                    notes: source.notes,
                    status: .active,
                    scheduleState: source.scheduleState,
                    whenDate: source.whenDate,
                    deadline: source.deadline,
                    sortOrder: source.sortOrder + 0.5,
                    tagIds: source.tagIds,
                    createdAt: now,
                    updatedAt: now
                )
                try upsertProjectLocked(newProject, isDirty: true)

                let allHeadings = try fetchAllActiveHeadingsLocked().filter { $0.projectId == projectId }
                var headingIdMap: [String: String] = [:]
                for h in allHeadings {
                    let newHid = UUID().uuidString
                    headingIdMap[h.id] = newHid
                    let clonedHeading = Heading(
                        id: newHid,
                        projectId: newProjId,
                        title: h.title,
                        sortOrder: h.sortOrder,
                        isArchived: h.isArchived,
                        createdAt: now,
                        updatedAt: now
                    )
                    try upsertHeadingLocked(clonedHeading, isDirty: true)
                }

                let tags = try fetchActiveTagsLocked()
                let tagsById = Dictionary(uniqueKeysWithValues: tags.map { ($0.id, $0) })
                let checklistsByTask = try fetchAllActiveChecklistItemsByTaskLocked()
                let tagIdsByTask = try fetchAllTaskTagIdsLocked()

                let sourceTasks = try fetchTasksWhereLocked(
                    whereClause: "project_id = ? AND is_deleted = 0 AND status = 'OPEN'",
                    bind: { stmt in self.bindText(stmt, 1, projectId) },
                    orderBy: "sort_order ASC",
                    tagsById: tagsById,
                    tagIdsByTask: tagIdsByTask,
                    checklistsByTask: checklistsByTask
                )

                for t in sourceTasks {
                    let newTid = UUID().uuidString
                    let mappedHid = t.headingId.flatMap { headingIdMap[$0] }
                    let clonedChecklists = t.checklistItems.map { chk in
                        ChecklistItem(
                            id: UUID().uuidString,
                            taskId: newTid,
                            title: chk.title,
                            isCompleted: false,
                            sortOrder: chk.sortOrder,
                            createdAt: now,
                            updatedAt: now
                        )
                    }
                    let clonedTask = TaskItem(
                        id: newTid,
                        title: t.title,
                        notes: t.notes,
                        status: .open,
                        scheduleState: t.scheduleState,
                        whenDate: t.whenDate,
                        deadline: t.deadline,
                        reminderTime: t.reminderTime,
                        projectId: newProjId,
                        headingId: mappedHid,
                        areaId: newProject.areaId,
                        recurrenceRule: t.recurrenceRule,
                        sortOrder: t.sortOrder,
                        checklistItems: clonedChecklists,
                        tags: t.tags,
                        createdAt: now,
                        updatedAt: now
                    )
                    try upsertTaskLocked(clonedTask, tagIds: t.tagIds, isDirty: true)
                    for chk in clonedChecklists {
                        try upsertChecklistItemLocked(chk, isDirty: true)
                    }
                }

                return try fetchProjectByIdLocked(newProjId) ?? newProject
            }
        }
        if duplicated != nil { notifyMutation() }
        return duplicated
    }

    @discardableResult
    public func createHeading(projectId: String, title: String) throws -> Heading {
        let heading = try queue.sync { () -> Heading in
            let now = nowMillis()
            let existing = try fetchAllActiveHeadingsLocked().filter { $0.projectId == projectId }
            let maxOrd = existing.map(\.sortOrder).max() ?? 0.0
            let h = Heading(
                id: UUID().uuidString,
                projectId: projectId,
                title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                sortOrder: maxOrd + 1000.0,
                createdAt: now,
                updatedAt: now
            )
            try upsertHeadingLocked(h, isDirty: true)
            return h
        }
        notifyMutation()
        return heading
    }

    public func updateHeading(_ heading: Heading) throws {
        try queue.sync {
            var copy = heading
            copy.updatedAt = nowMillis()
            try upsertHeadingLocked(copy, isDirty: true)
        }
        notifyMutation()
    }

    public func deleteHeading(_ headingId: String) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                let s1 = try prepareLocked("UPDATE tasks SET heading_id = NULL, updated_at = ?, is_dirty = 1 WHERE heading_id = ?;")
                defer { sqlite3_finalize(s1) }
                bindInt64(s1, 1, now)
                bindText(s1, 2, headingId)
                _ = sqlite3_step(s1)

                let s2 = try prepareLocked("UPDATE headings SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(s2) }
                bindInt64(s2, 1, now)
                bindText(s2, 2, headingId)
                _ = sqlite3_step(s2)
            }
        }
        notifyMutation()
    }

    @discardableResult
    public func createOrUpdateTag(id: String? = nil, title: String, parentTagId: String? = nil, shortcut: String? = nil) throws -> Tag {
        let tag = try queue.sync { () -> Tag in
            let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
            let existingTags = try fetchActiveTagsLocked()
            if id == nil, let match = existingTags.first(where: { $0.title.caseInsensitiveCompare(cleanTitle) == .orderedSame }) {
                return match
            }
            let now = nowMillis()
            let maxOrd = existingTags.map(\.sortOrder).max() ?? 0.0
            let t = Tag(
                id: id ?? UUID().uuidString,
                title: cleanTitle,
                parentTagId: parentTagId,
                shortcut: shortcut?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().first.map(String.init),
                sortOrder: maxOrd + 100.0,
                createdAt: now,
                updatedAt: now
            )
            try upsertTagLocked(t, isDirty: true)
            return t
        }
        notifyMutation()
        return tag
    }

    public func deleteTag(_ tagId: String) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                let s1 = try prepareLocked("DELETE FROM task_tags WHERE tag_id = ?;")
                defer { sqlite3_finalize(s1) }
                bindText(s1, 1, tagId)
                _ = sqlite3_step(s1)

                let s2 = try prepareLocked("DELETE FROM project_tags WHERE tag_id = ?;")
                defer { sqlite3_finalize(s2) }
                bindText(s2, 1, tagId)
                _ = sqlite3_step(s2)

                let s3 = try prepareLocked("UPDATE tags SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(s3) }
                bindInt64(s3, 1, now)
                bindText(s3, 2, tagId)
                _ = sqlite3_step(s3)
            }
        }
        notifyMutation()
    }

    // MARK: - Sample Data Purge & Account Prep

    public func purgeSampleDataLocally(preserveUsedContainers: Bool = true) throws {
        try queue.sync {
            try inTransactionLocked {
                for id in DatabaseSeeder.sampleChecklistIds {
                    try deleteByIdLocked(table: "checklist_items", id: id)
                }
                for id in DatabaseSeeder.sampleTaskIds {
                    try deleteByIdLocked(table: "tasks", id: id)
                }
                for id in DatabaseSeeder.sampleHeadingIds {
                    if preserveUsedContainers && countForeignRefsLocked("SELECT COUNT(*) FROM tasks WHERE heading_id = ?;", id) > 0 {
                        continue
                    }
                    try deleteByIdLocked(table: "headings", id: id)
                }
                for id in DatabaseSeeder.sampleProjectIds {
                    if preserveUsedContainers && countForeignRefsLocked("SELECT COUNT(*) FROM tasks WHERE project_id = ?;", id) > 0 {
                        continue
                    }
                    try deleteByIdLocked(table: "projects", id: id)
                }
                for id in DatabaseSeeder.sampleAreaIds {
                    if preserveUsedContainers && (
                        countForeignRefsLocked("SELECT COUNT(*) FROM tasks WHERE area_id = ?;", id) > 0 ||
                        countForeignRefsLocked("SELECT COUNT(*) FROM projects WHERE area_id = ?;", id) > 0
                    ) {
                        continue
                    }
                    try deleteByIdLocked(table: "areas", id: id)
                }
                for id in DatabaseSeeder.sampleTagIds {
                    if preserveUsedContainers && (
                        countForeignRefsLocked("SELECT COUNT(*) FROM task_tags WHERE tag_id = ?;", id) > 0 ||
                        countForeignRefsLocked("SELECT COUNT(*) FROM project_tags WHERE tag_id = ?;", id) > 0
                    ) {
                        continue
                    }
                    try deleteByIdLocked(table: "tags", id: id)
                }
            }
        }
        notifyMutation()
    }

    public func purgeAllLocalDataBeforeAccountPull() throws {
        try queue.sync {
            try inTransactionLocked {
                try execLocked("""
                    DELETE FROM checklist_items;
                    DELETE FROM task_tags;
                    DELETE FROM project_tags;
                    DELETE FROM tasks;
                    DELETE FROM headings;
                    DELETE FROM projects;
                    DELETE FROM areas;
                    DELETE FROM tags;
                """)
            }
            rebuildFtsLocked()
        }
        notifyMutation()
    }

    private func deleteByIdLocked(table: String, id: String) throws {
        let stmt = try prepareLocked("DELETE FROM \(table) WHERE id = ?;")
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, id)
        _ = sqlite3_step(stmt)
    }

    private func countForeignRefsLocked(_ sql: String, _ id: String) -> Int {
        guard let stmt = try? prepareLocked(sql) else { return 0 }
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, id)
        if sqlite3_step(stmt) == SQLITE_ROW {
            return Int(sqlite3_column_int(stmt, 0))
        }
        return 0
    }

    // MARK: - CSV Export & Import

    public func exportCsv(calendar: Calendar = .current) throws -> String {
        let snap = try loadWorkspaceSnapshot(calendar: calendar)
        let areasById = Dictionary(uniqueKeysWithValues: snap.areas.map { ($0.id, $0) })
        let projectsById = Dictionary(uniqueKeysWithValues: snap.projects.map { ($0.id, $0) })
        let allActiveTasks = (snap.allOpenTasks + snap.logbookTasks).sorted { $0.createdAt < $1.createdAt }
        return CsvCodec.exportTasksToCsv(tasks: allActiveTasks, areasById: areasById, projectsById: projectsById, calendar: calendar)
    }

    @discardableResult
    public func importCsv(_ csvContent: String, referenceDate: Date = Date(), calendar: Calendar = .current) throws -> Int {
        let rows = CsvCodec.parseCsvTasks(csvContent: csvContent, referenceDate: referenceDate, calendar: calendar)
        guard !rows.isEmpty else { return 0 }

        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                var areas = try fetchActiveAreasLocked()
                var projects = try fetchActiveProjectsLocked()
                var tags = try fetchActiveTagsLocked()
                var nextTaskOrder = (try maxSortOrderLocked(table: "tasks")) + 1000.0

                for row in rows {
                    var resolvedAreaId: String? = nil
                    if let areaTitle = row.areaTitle {
                        if let found = areas.first(where: { $0.title.caseInsensitiveCompare(areaTitle) == .orderedSame }) {
                            resolvedAreaId = found.id
                        } else if areas.count < Self.maxAreas {
                            let newArea = Area(
                                id: UUID().uuidString,
                                title: areaTitle,
                                sortOrder: (areas.map(\.sortOrder).max() ?? 0.0) + 1000.0,
                                createdAt: now,
                                updatedAt: now
                            )
                            try upsertAreaLocked(newArea, isDirty: true)
                            areas.append(newArea)
                            resolvedAreaId = newArea.id
                        }
                    }

                    var resolvedProjectId: String? = nil
                    if let projTitle = row.projectTitle {
                        if let found = projects.first(where: { $0.title.caseInsensitiveCompare(projTitle) == .orderedSame }) {
                            resolvedProjectId = found.id
                            if resolvedAreaId == nil { resolvedAreaId = found.areaId }
                        } else {
                            let newProj = Project(
                                id: UUID().uuidString,
                                areaId: resolvedAreaId,
                                title: projTitle,
                                sortOrder: (projects.map(\.sortOrder).max() ?? 0.0) + 1000.0,
                                createdAt: now,
                                updatedAt: now
                            )
                            try upsertProjectLocked(newProj, isDirty: true)
                            projects.append(newProj)
                            resolvedProjectId = newProj.id
                        }
                    }

                    var resolvedTagIds: [String] = []
                    for tagTitle in row.tagTitles {
                        if let found = tags.first(where: { $0.title.caseInsensitiveCompare(tagTitle) == .orderedSame }) {
                            resolvedTagIds.append(found.id)
                        } else {
                            let newTag = Tag(
                                id: UUID().uuidString,
                                title: tagTitle,
                                sortOrder: (tags.map(\.sortOrder).max() ?? 0.0) + 100.0,
                                createdAt: now,
                                updatedAt: now
                            )
                            try upsertTagLocked(newTag, isDirty: true)
                            tags.append(newTag)
                            resolvedTagIds.append(newTag.id)
                        }
                    }

                    let taskId = UUID().uuidString
                    let task = TaskItem(
                        id: taskId,
                        title: row.title,
                        notes: row.notes,
                        status: row.status,
                        scheduleState: row.scheduleState,
                        whenDate: row.whenDate,
                        deadline: row.deadline,
                        completedAt: (row.status == .completed || row.status == .canceled) ? now : nil,
                        projectId: resolvedProjectId,
                        areaId: resolvedAreaId,
                        sortOrder: nextTaskOrder,
                        createdAt: now,
                        updatedAt: now
                    )
                    nextTaskOrder += 1000.0
                    try upsertTaskLocked(task, tagIds: resolvedTagIds, isDirty: true)

                    for (idx, chkEntry) in row.checklistEntries.enumerated() {
                        let chk = ChecklistItem(
                            id: UUID().uuidString,
                            taskId: taskId,
                            title: chkEntry.title,
                            isCompleted: chkEntry.isCompleted,
                            sortOrder: Double((idx + 1) * 100),
                            createdAt: now,
                            updatedAt: now
                        )
                        try upsertChecklistItemLocked(chk, isDirty: true)
                    }
                }
            }
        }
        notifyMutation()
        return rows.count
    }

    // MARK: - Full `.tbbackup` Export & Atomic Restore

    public func buildBackupPayload(preferences: AppPreferences, appVersionName: String = "1.7.0") throws -> ThingsbookBackupPayload {
        try queue.sync {
            let now = nowMillis()
            let areas = try fetchActiveAreasLocked().map {
                BackupAreaDto(
                    id: $0.id,
                    title: $0.title,
                    sortOrder: $0.sortOrder,
                    tagIdsJson: encodeStringArray($0.tagIds),
                    createdAt: $0.createdAt,
                    updatedAt: $0.updatedAt
                )
            }

            let projects = try fetchActiveProjectsLocked().map {
                BackupProjectDto(
                    id: $0.id,
                    areaId: $0.areaId,
                    title: $0.title,
                    notes: $0.notes,
                    status: $0.status.rawValue,
                    scheduleState: $0.scheduleState.rawValue,
                    whenDate: $0.whenDate,
                    deadline: $0.deadline,
                    completedAt: $0.completedAt,
                    sortOrder: $0.sortOrder,
                    tagIdsJson: encodeStringArray($0.tagIds),
                    createdAt: $0.createdAt,
                    updatedAt: $0.updatedAt
                )
            }

            let headings = try fetchAllActiveHeadingsLocked().map {
                BackupHeadingDto(
                    id: $0.id,
                    projectId: $0.projectId,
                    title: $0.title,
                    sortOrder: $0.sortOrder,
                    isArchived: $0.isArchived,
                    createdAt: $0.createdAt,
                    updatedAt: $0.updatedAt
                )
            }

            let tags = try fetchActiveTagsLocked()
            let tagsById = Dictionary(uniqueKeysWithValues: tags.map { ($0.id, $0) })
            let checklistsByTask = try fetchAllActiveChecklistItemsByTaskLocked()
            let tagIdsByTask = try fetchAllTaskTagIdsLocked()

            let activeTasks = try fetchTasksWhereLocked(
                whereClause: "is_deleted = 0",
                bind: { _ in },
                orderBy: "created_at ASC",
                tagsById: tagsById,
                tagIdsByTask: tagIdsByTask,
                checklistsByTask: checklistsByTask
            )

            let taskDtos = activeTasks.map { t in
                BackupTaskDto(
                    id: t.id,
                    title: t.title,
                    notes: t.notes,
                    status: t.status.rawValue,
                    scheduleState: t.scheduleState.rawValue,
                    whenDate: t.whenDate,
                    deadline: t.deadline,
                    reminderTime: t.reminderTime,
                    completedAt: t.completedAt,
                    projectId: t.projectId,
                    headingId: t.headingId,
                    areaId: t.areaId,
                    recurrenceRuleJson: encodeRecurrence(t.recurrenceRule),
                    sortOrder: t.sortOrder,
                    tagIdsJson: encodeStringArray(t.tagIds),
                    createdAt: t.createdAt,
                    updatedAt: t.updatedAt
                )
            }

            var checklistDtos: [BackupChecklistItemDto] = []
            for items in checklistsByTask.values {
                for chk in items {
                    checklistDtos.append(
                        BackupChecklistItemDto(
                            id: chk.id,
                            taskId: chk.taskId,
                            title: chk.title,
                            isCompleted: chk.isCompleted,
                            sortOrder: chk.sortOrder,
                            createdAt: chk.createdAt,
                            updatedAt: chk.updatedAt
                        )
                    )
                }
            }

            let tagDtos = tags.map {
                BackupTagDto(
                    id: $0.id,
                    title: $0.title,
                    parentTagId: $0.parentTagId,
                    shortcut: $0.shortcut,
                    sortOrder: $0.sortOrder,
                    createdAt: $0.createdAt,
                    updatedAt: $0.updatedAt
                )
            }

            var taskTagRefs: [BackupTaskTagRefDto] = []
            for (tid, tids) in tagIdsByTask {
                for tg in tids {
                    taskTagRefs.append(BackupTaskTagRefDto(taskId: tid, tagId: tg))
                }
            }

            var projectTagRefs: [BackupProjectTagRefDto] = []
            for p in projects {
                for tg in decodeStringArray(p.tagIdsJson) {
                    projectTagRefs.append(BackupProjectTagRefDto(projectId: p.id, tagId: tg))
                }
            }

            let prefsDto = BackupPreferencesDto(
                sidebarVisible: preferences.sidebarVisible,
                dailyDigestEnabled: preferences.dailyDigestEnabled,
                dailyDigestHour: preferences.dailyDigestHour,
                dailyDigestMinute: preferences.dailyDigestMinute,
                languageTag: preferences.language == .system ? "" : preferences.language.rawValue,
                calendarEventsEnabled: preferences.calendarEventsEnabled,
                selectedCalendarIds: preferences.selectedCalendarIds,
                groupTodayByProjectOrArea: preferences.groupTodayByProjectOrArea
            )

            return ThingsbookBackupPayload(
                schemaVersion: ThingsBackupCodec.formatVersion,
                createdAt: now,
                appVersionName: appVersionName,
                areas: areas,
                projects: projects,
                headings: headings,
                tasks: taskDtos,
                checklistItems: checklistDtos,
                tags: tagDtos,
                taskTagRefs: taskTagRefs,
                projectTagRefs: projectTagRefs,
                preferences: prefsDto
            )
        }
    }

    public func restoreBackupPayload(_ payload: ThingsbookBackupPayload) throws {
        try queue.sync {
            try inTransactionLocked {
                try execLocked("PRAGMA foreign_keys = OFF;")
                defer { try? execLocked("PRAGMA foreign_keys = ON;") }

                try execLocked("""
                    DELETE FROM checklist_items;
                    DELETE FROM task_tags;
                    DELETE FROM project_tags;
                    DELETE FROM tasks;
                    DELETE FROM headings;
                    DELETE FROM projects;
                    DELETE FROM areas;
                    DELETE FROM tags;
                """)

                let validTagIds = Set(payload.tags.map(\.id))
                let validAreaIds = Set(payload.areas.map(\.id))
                let validProjectIds = Set(payload.projects.map(\.id))
                let validHeadingIds = Set(payload.headings.map(\.id))
                let validTaskIds = Set(payload.tasks.map(\.id))

                for dto in payload.tags {
                    let parent = dto.parentTagId.flatMap { validTagIds.contains($0) ? $0 : nil }
                    let tag = Tag(
                        id: dto.id,
                        title: dto.title,
                        parentTagId: parent,
                        shortcut: dto.shortcut,
                        sortOrder: dto.sortOrder,
                        createdAt: dto.createdAt,
                        updatedAt: dto.updatedAt
                    )
                    try upsertTagLocked(tag, isDirty: true)
                }

                for dto in payload.areas {
                    let area = Area(
                        id: dto.id,
                        title: dto.title,
                        sortOrder: dto.sortOrder,
                        tagIds: decodeStringArray(dto.tagIdsJson).filter { validTagIds.contains($0) },
                        createdAt: dto.createdAt,
                        updatedAt: dto.updatedAt
                    )
                    try upsertAreaLocked(area, isDirty: true)
                }

                var projRefsByProj = Dictionary(grouping: payload.projectTagRefs, by: \.projectId)
                for dto in payload.projects {
                    let explicitRefs = (projRefsByProj.removeValue(forKey: dto.id) ?? []).map(\.tagId)
                    let mergedTags = Array(Set(explicitRefs + decodeStringArray(dto.tagIdsJson))).filter { validTagIds.contains($0) }
                    let proj = Project(
                        id: dto.id,
                        areaId: dto.areaId.flatMap { validAreaIds.contains($0) ? $0 : nil },
                        title: dto.title,
                        notes: dto.notes,
                        status: ProjectStatus.normalized(dto.status),
                        scheduleState: ScheduleState.normalized(dto.scheduleState, fallback: .anytime),
                        whenDate: dto.whenDate,
                        deadline: dto.deadline,
                        completedAt: dto.completedAt,
                        sortOrder: dto.sortOrder,
                        tagIds: mergedTags,
                        createdAt: dto.createdAt,
                        updatedAt: dto.updatedAt
                    )
                    try upsertProjectLocked(proj, isDirty: true)
                }

                for dto in payload.headings where validProjectIds.contains(dto.projectId) {
                    let heading = Heading(
                        id: dto.id,
                        projectId: dto.projectId,
                        title: dto.title,
                        sortOrder: dto.sortOrder,
                        isArchived: dto.isArchived,
                        createdAt: dto.createdAt,
                        updatedAt: dto.updatedAt
                    )
                    try upsertHeadingLocked(heading, isDirty: true)
                }

                var taskRefsByTask = Dictionary(grouping: payload.taskTagRefs, by: \.taskId)
                for dto in payload.tasks {
                    let explicitRefs = (taskRefsByTask.removeValue(forKey: dto.id) ?? []).map(\.tagId)
                    let mergedTags = Array(Set(explicitRefs + decodeStringArray(dto.tagIdsJson))).filter { validTagIds.contains($0) }
                    let task = TaskItem(
                        id: dto.id,
                        title: dto.title,
                        notes: dto.notes,
                        status: TaskStatus.normalized(dto.status),
                        scheduleState: ScheduleState.normalized(dto.scheduleState, fallback: .inbox),
                        whenDate: dto.whenDate,
                        deadline: dto.deadline,
                        reminderTime: dto.reminderTime,
                        completedAt: dto.completedAt,
                        projectId: dto.projectId.flatMap { validProjectIds.contains($0) ? $0 : nil },
                        headingId: dto.headingId.flatMap { validHeadingIds.contains($0) ? $0 : nil },
                        areaId: dto.areaId.flatMap { validAreaIds.contains($0) ? $0 : nil },
                        recurrenceRule: decodeRecurrence(dto.recurrenceRuleJson),
                        sortOrder: dto.sortOrder,
                        createdAt: dto.createdAt,
                        updatedAt: dto.updatedAt
                    )
                    try upsertTaskLocked(task, tagIds: mergedTags, isDirty: true)
                }

                for dto in payload.checklistItems where validTaskIds.contains(dto.taskId) {
                    let chk = ChecklistItem(
                        id: dto.id,
                        taskId: dto.taskId,
                        title: dto.title,
                        isCompleted: dto.isCompleted,
                        sortOrder: dto.sortOrder,
                        createdAt: dto.createdAt,
                        updatedAt: dto.updatedAt
                    )
                    try upsertChecklistItemLocked(chk, isDirty: true)
                }
            }
            rebuildFtsLocked()
        }
        notifyMutation()
    }

    // MARK: - Cloud Sync Dirty Tracking & LWW Remote Delta Merge

    public func fetchDirtyBatch() throws -> SyncDirtyBatch {
        try queue.sync {
            let tags = try fetchTagsWhereLocked("is_dirty = 1")
            let areas = try fetchAreasWhereLocked("is_dirty = 1")
            let projects = try fetchProjectsWhereLocked("p.is_dirty = 1")
            let headings = try fetchHeadingsWhereLocked("is_dirty = 1")
            let allTags = try fetchActiveTagsLocked()
            let tagsById = Dictionary(uniqueKeysWithValues: allTags.map { ($0.id, $0) })
            let tagIdsByTask = try fetchAllTaskTagIdsLocked()
            let tasks = try fetchTasksWhereLocked(
                whereClause: "is_dirty = 1",
                bind: { _ in },
                orderBy: "updated_at ASC",
                tagsById: tagsById,
                tagIdsByTask: tagIdsByTask,
                checklistsByTask: [:]
            )
            let checklistItems = try fetchChecklistsWhereLocked("is_dirty = 1")
            return SyncDirtyBatch(
                tags: tags,
                areas: areas,
                projects: projects,
                headings: headings,
                tasks: tasks,
                checklistItems: checklistItems
            )
        }
    }

    public func markDirtyBatchSynced(_ batch: SyncDirtyBatch) throws {
        try queue.sync {
            try inTransactionLocked {
                for t in batch.tags { try clearDirtyIfUnchangedLocked(table: "tags", id: t.id, maxUpdatedAt: t.updatedAt) }
                for a in batch.areas { try clearDirtyIfUnchangedLocked(table: "areas", id: a.id, maxUpdatedAt: a.updatedAt) }
                for p in batch.projects { try clearDirtyIfUnchangedLocked(table: "projects", id: p.id, maxUpdatedAt: p.updatedAt) }
                for h in batch.headings { try clearDirtyIfUnchangedLocked(table: "headings", id: h.id, maxUpdatedAt: h.updatedAt) }
                for t in batch.tasks { try clearDirtyIfUnchangedLocked(table: "tasks", id: t.id, maxUpdatedAt: t.updatedAt) }
                for c in batch.checklistItems { try clearDirtyIfUnchangedLocked(table: "checklist_items", id: c.id, maxUpdatedAt: c.updatedAt) }
            }
        }
    }

    private func clearDirtyIfUnchangedLocked(table: String, id: String, maxUpdatedAt: Int64) throws {
        let stmt = try prepareLocked("UPDATE \(table) SET is_dirty = 0 WHERE id = ? AND updated_at <= ?;")
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, id)
        bindInt64(stmt, 2, maxUpdatedAt)
        _ = sqlite3_step(stmt)
    }

    @discardableResult
    public func applyRemoteDeltas(_ deltas: RemoteDeltaBatch) throws -> Int64 {
        var maxTimestamp: Int64 = 0
        var appliedAny = false

        try queue.sync {
            try inTransactionLocked {
                try execLocked("PRAGMA foreign_keys = OFF;")
                defer { try? execLocked("PRAGMA foreign_keys = ON;") }

                for remote in deltas.tags {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    if shouldApplyRemoteLocked(table: "tags", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        try upsertTagLocked(remote, isDirty: false)
                        appliedAny = true
                    }
                }
                for remote in deltas.areas {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    if shouldApplyRemoteLocked(table: "areas", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        try upsertAreaLocked(remote, isDirty: false)
                        appliedAny = true
                    }
                }
                for remote in deltas.projects {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    if shouldApplyRemoteLocked(table: "projects", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        try upsertProjectLocked(remote, isDirty: false)
                        appliedAny = true
                    }
                }
                for remote in deltas.headings {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    if shouldApplyRemoteLocked(table: "headings", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        try upsertHeadingLocked(remote, isDirty: false)
                        appliedAny = true
                    }
                }
                for remote in deltas.tasks {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    if shouldApplyRemoteLocked(table: "tasks", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        let tagIds = deltas.taskTagIdsByTaskId[remote.id] ?? remote.tagIds
                        try upsertTaskLocked(remote, tagIds: tagIds, isDirty: false)
                        appliedAny = true
                    }
                }
                for remote in deltas.checklistItems {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    if shouldApplyRemoteLocked(table: "checklist_items", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        try upsertChecklistItemLocked(remote, isDirty: false)
                        appliedAny = true
                    }
                }
            }
        }

        if appliedAny { notifyMutation() }
        return maxTimestamp
    }

    private func shouldApplyRemoteLocked(table: String, id: String, remoteUpdatedAt: Int64) -> Bool {
        guard let stmt = try? prepareLocked("SELECT updated_at FROM \(table) WHERE id = ?;") else { return true }
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, id)
        if sqlite3_step(stmt) == SQLITE_ROW {
            let localUpdated = sqlite3_column_int64(stmt, 0)
            return remoteUpdatedAt >= localUpdated
        }
        return true
    }

    // MARK: - Internal Upserts & Row Readers

    private func maxSortOrderLocked(table: String) throws -> Double {
        let stmt = try prepareLocked("SELECT COALESCE(MAX(sort_order), 0.0) FROM \(table) WHERE is_deleted = 0;")
        defer { sqlite3_finalize(stmt) }
        if sqlite3_step(stmt) == SQLITE_ROW {
            return sqlite3_column_double(stmt, 0)
        }
        return 0.0
    }

    private func upsertTagLocked(_ tag: Tag, isDirty: Bool) throws {
        // Resolve unique title collisions peacefully
        let conflictStmt = try prepareLocked("SELECT id FROM tags WHERE title = ? AND id != ?;")
        defer { sqlite3_finalize(conflictStmt) }
        bindText(conflictStmt, 1, tag.title)
        bindText(conflictStmt, 2, tag.id)
        if sqlite3_step(conflictStmt) == SQLITE_ROW, let oldId = colString(conflictStmt, 0) {
            try deleteByIdLocked(table: "tags", id: oldId)
        }

        let stmt = try prepareLocked("""
            INSERT INTO tags (id, title, parent_tag_id, shortcut, sort_order, created_at, updated_at, is_deleted, is_dirty)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                parent_tag_id = excluded.parent_tag_id,
                shortcut = excluded.shortcut,
                sort_order = excluded.sort_order,
                updated_at = excluded.updated_at,
                is_deleted = excluded.is_deleted,
                is_dirty = excluded.is_dirty;
        """)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, tag.id)
        bindText(stmt, 2, tag.title)
        bindText(stmt, 3, tag.parentTagId)
        bindText(stmt, 4, tag.shortcut)
        sqlite3_bind_double(stmt, 5, tag.sortOrder)
        bindInt64(stmt, 6, tag.createdAt)
        bindInt64(stmt, 7, tag.updatedAt)
        bindBool(stmt, 8, tag.isDeleted)
        bindBool(stmt, 9, isDirty)
        if sqlite3_step(stmt) != SQLITE_DONE {
            throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
        }
    }

    private func upsertAreaLocked(_ area: Area, isDirty: Bool) throws {
        let stmt = try prepareLocked("""
            INSERT INTO areas (id, title, sort_order, tag_ids_json, created_at, updated_at, is_deleted, is_dirty)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                sort_order = excluded.sort_order,
                tag_ids_json = excluded.tag_ids_json,
                updated_at = excluded.updated_at,
                is_deleted = excluded.is_deleted,
                is_dirty = excluded.is_dirty;
        """)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, area.id)
        bindText(stmt, 2, area.title)
        sqlite3_bind_double(stmt, 3, area.sortOrder)
        bindText(stmt, 4, encodeStringArray(area.tagIds))
        bindInt64(stmt, 5, area.createdAt)
        bindInt64(stmt, 6, area.updatedAt)
        bindBool(stmt, 7, area.isDeleted)
        bindBool(stmt, 8, isDirty)
        if sqlite3_step(stmt) != SQLITE_DONE {
            throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
        }
    }

    private func upsertProjectLocked(_ project: Project, isDirty: Bool) throws {
        let stmt = try prepareLocked("""
            INSERT INTO projects (
                id, area_id, title, notes, status, schedule_state, when_date, deadline,
                completed_at, sort_order, tag_ids_json, created_at, updated_at, is_deleted, is_dirty
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                area_id = excluded.area_id,
                title = excluded.title,
                notes = excluded.notes,
                status = excluded.status,
                schedule_state = excluded.schedule_state,
                when_date = excluded.when_date,
                deadline = excluded.deadline,
                completed_at = excluded.completed_at,
                sort_order = excluded.sort_order,
                tag_ids_json = excluded.tag_ids_json,
                updated_at = excluded.updated_at,
                is_deleted = excluded.is_deleted,
                is_dirty = excluded.is_dirty;
        """)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, project.id)
        bindText(stmt, 2, project.areaId)
        bindText(stmt, 3, project.title)
        bindText(stmt, 4, project.notes)
        bindText(stmt, 5, project.status.rawValue)
        bindText(stmt, 6, project.scheduleState.rawValue)
        bindInt64(stmt, 7, project.whenDate)
        bindInt64(stmt, 8, project.deadline)
        bindInt64(stmt, 9, project.completedAt)
        sqlite3_bind_double(stmt, 10, project.sortOrder)
        bindText(stmt, 11, encodeStringArray(project.tagIds))
        bindInt64(stmt, 12, project.createdAt)
        bindInt64(stmt, 13, project.updatedAt)
        bindBool(stmt, 14, project.isDeleted)
        bindBool(stmt, 15, isDirty)
        if sqlite3_step(stmt) != SQLITE_DONE {
            throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
        }

        let clearStmt = try prepareLocked("DELETE FROM project_tags WHERE project_id = ?;")
        defer { sqlite3_finalize(clearStmt) }
        bindText(clearStmt, 1, project.id)
        _ = sqlite3_step(clearStmt)

        for tagId in Set(project.tagIds) {
            let refStmt = try prepareLocked("INSERT OR IGNORE INTO project_tags (project_id, tag_id) VALUES (?, ?);")
            defer { sqlite3_finalize(refStmt) }
            bindText(refStmt, 1, project.id)
            bindText(refStmt, 2, tagId)
            _ = sqlite3_step(refStmt)
        }
    }

    private func upsertHeadingLocked(_ heading: Heading, isDirty: Bool) throws {
        let stmt = try prepareLocked("""
            INSERT INTO headings (id, project_id, title, sort_order, is_archived, created_at, updated_at, is_deleted, is_dirty)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                project_id = excluded.project_id,
                title = excluded.title,
                sort_order = excluded.sort_order,
                is_archived = excluded.is_archived,
                updated_at = excluded.updated_at,
                is_deleted = excluded.is_deleted,
                is_dirty = excluded.is_dirty;
        """)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, heading.id)
        bindText(stmt, 2, heading.projectId)
        bindText(stmt, 3, heading.title)
        sqlite3_bind_double(stmt, 4, heading.sortOrder)
        bindBool(stmt, 5, heading.isArchived)
        bindInt64(stmt, 6, heading.createdAt)
        bindInt64(stmt, 7, heading.updatedAt)
        bindBool(stmt, 8, heading.isDeleted)
        bindBool(stmt, 9, isDirty)
        if sqlite3_step(stmt) != SQLITE_DONE {
            throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
        }
    }

    private func upsertTaskLocked(_ task: TaskItem, tagIds: [String], isDirty: Bool) throws {
        let uniqueTags = Array(Set(tagIds))
        let stmt = try prepareLocked("""
            INSERT INTO tasks (
                id, title, notes, status, schedule_state, when_date, deadline, reminder_time,
                completed_at, project_id, heading_id, area_id, recurrence_rule, sort_order,
                tag_ids_json, created_at, updated_at, is_deleted, is_dirty
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                notes = excluded.notes,
                status = excluded.status,
                schedule_state = excluded.schedule_state,
                when_date = excluded.when_date,
                deadline = excluded.deadline,
                reminder_time = excluded.reminder_time,
                completed_at = excluded.completed_at,
                project_id = excluded.project_id,
                heading_id = excluded.heading_id,
                area_id = excluded.area_id,
                recurrence_rule = excluded.recurrence_rule,
                sort_order = excluded.sort_order,
                tag_ids_json = excluded.tag_ids_json,
                updated_at = excluded.updated_at,
                is_deleted = excluded.is_deleted,
                is_dirty = excluded.is_dirty;
        """)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, task.id)
        bindText(stmt, 2, task.title)
        bindText(stmt, 3, task.notes)
        bindText(stmt, 4, task.status.rawValue)
        bindText(stmt, 5, task.scheduleState.rawValue)
        bindInt64(stmt, 6, task.whenDate)
        bindInt64(stmt, 7, task.deadline)
        bindInt64(stmt, 8, task.reminderTime)
        bindInt64(stmt, 9, task.completedAt)
        bindText(stmt, 10, task.projectId)
        bindText(stmt, 11, task.headingId)
        bindText(stmt, 12, task.areaId)
        bindText(stmt, 13, encodeRecurrence(task.recurrenceRule))
        sqlite3_bind_double(stmt, 14, task.sortOrder)
        bindText(stmt, 15, encodeStringArray(uniqueTags))
        bindInt64(stmt, 16, task.createdAt)
        bindInt64(stmt, 17, task.updatedAt)
        bindBool(stmt, 18, task.isDeleted)
        bindBool(stmt, 19, isDirty)
        if sqlite3_step(stmt) != SQLITE_DONE {
            throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
        }

        let clearStmt = try prepareLocked("DELETE FROM task_tags WHERE task_id = ?;")
        defer { sqlite3_finalize(clearStmt) }
        bindText(clearStmt, 1, task.id)
        _ = sqlite3_step(clearStmt)

        for tagId in uniqueTags {
            let refStmt = try prepareLocked("INSERT OR IGNORE INTO task_tags (task_id, tag_id) VALUES (?, ?);")
            defer { sqlite3_finalize(refStmt) }
            bindText(refStmt, 1, task.id)
            bindText(refStmt, 2, tagId)
            _ = sqlite3_step(refStmt)
        }
    }

    private func upsertChecklistItemLocked(_ item: ChecklistItem, isDirty: Bool) throws {
        let stmt = try prepareLocked("""
            INSERT INTO checklist_items (id, task_id, title, is_completed, sort_order, created_at, updated_at, is_deleted, is_dirty)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                task_id = excluded.task_id,
                title = excluded.title,
                is_completed = excluded.is_completed,
                sort_order = excluded.sort_order,
                updated_at = excluded.updated_at,
                is_deleted = excluded.is_deleted,
                is_dirty = excluded.is_dirty;
        """)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, item.id)
        bindText(stmt, 2, item.taskId)
        bindText(stmt, 3, item.title)
        bindBool(stmt, 4, item.isCompleted)
        sqlite3_bind_double(stmt, 5, item.sortOrder)
        bindInt64(stmt, 6, item.createdAt)
        bindInt64(stmt, 7, item.updatedAt)
        bindBool(stmt, 8, item.isDeleted)
        bindBool(stmt, 9, isDirty)
        if sqlite3_step(stmt) != SQLITE_DONE {
            throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
        }
    }

    private func fetchActiveAreasLocked() throws -> [Area] {
        try fetchAreasWhereLocked("is_deleted = 0")
    }

    private func fetchAreasWhereLocked(_ whereClause: String) throws -> [Area] {
        let stmt = try prepareLocked("""
            SELECT id, title, sort_order, tag_ids_json, created_at, updated_at, is_deleted
            FROM areas WHERE \(whereClause) ORDER BY sort_order ASC;
        """)
        defer { sqlite3_finalize(stmt) }
        var out: [Area] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            out.append(
                Area(
                    id: colString(stmt, 0) ?? "",
                    title: colString(stmt, 1) ?? "",
                    sortOrder: sqlite3_column_double(stmt, 2),
                    tagIds: decodeStringArray(colString(stmt, 3)),
                    createdAt: sqlite3_column_int64(stmt, 4),
                    updatedAt: sqlite3_column_int64(stmt, 5),
                    isDeleted: colBool(stmt, 6)
                )
            )
        }
        return out
    }

    private func fetchActiveTagsLocked() throws -> [Tag] {
        try fetchTagsWhereLocked("is_deleted = 0")
    }

    private func fetchTagsByIdsLocked(_ ids: [String]) throws -> [Tag] {
        let set = Set(ids)
        guard !set.isEmpty else { return [] }
        return try fetchActiveTagsLocked().filter { set.contains($0.id) }
    }

    private func fetchTagsWhereLocked(_ whereClause: String) throws -> [Tag] {
        let stmt = try prepareLocked("""
            SELECT id, title, parent_tag_id, shortcut, sort_order, created_at, updated_at, is_deleted
            FROM tags WHERE \(whereClause) ORDER BY sort_order ASC, title ASC;
        """)
        defer { sqlite3_finalize(stmt) }
        var out: [Tag] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            out.append(
                Tag(
                    id: colString(stmt, 0) ?? "",
                    title: colString(stmt, 1) ?? "",
                    parentTagId: colString(stmt, 2),
                    shortcut: colString(stmt, 3),
                    sortOrder: sqlite3_column_double(stmt, 4),
                    createdAt: sqlite3_column_int64(stmt, 5),
                    updatedAt: sqlite3_column_int64(stmt, 6),
                    isDeleted: colBool(stmt, 7)
                )
            )
        }
        return out
    }

    private func fetchActiveProjectsLocked() throws -> [Project] {
        try fetchProjectsWhereLocked("p.is_deleted = 0")
    }

    private func fetchProjectByIdLocked(_ id: String) throws -> Project? {
        let stmt = try prepareLocked("""
            SELECT p.id, p.area_id, p.title, p.notes, p.status, p.schedule_state, p.when_date,
                   p.deadline, p.completed_at, p.sort_order, p.tag_ids_json, p.created_at, p.updated_at, p.is_deleted,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status != 'CANCELED') AS total_cnt,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status = 'COMPLETED') AS comp_cnt
            FROM projects p WHERE p.id = ? LIMIT 1;
        """)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, id)
        if sqlite3_step(stmt) == SQLITE_ROW {
            return readProjectRowLocked(stmt)
        }
        return nil
    }

    private func fetchProjectsWhereLocked(_ whereClause: String) throws -> [Project] {
        let stmt = try prepareLocked("""
            SELECT p.id, p.area_id, p.title, p.notes, p.status, p.schedule_state, p.when_date,
                   p.deadline, p.completed_at, p.sort_order, p.tag_ids_json, p.created_at, p.updated_at, p.is_deleted,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status != 'CANCELED') AS total_cnt,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status = 'COMPLETED') AS comp_cnt
            FROM projects p WHERE \(whereClause) ORDER BY p.sort_order ASC;
        """)
        defer { sqlite3_finalize(stmt) }
        var out: [Project] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            out.append(readProjectRowLocked(stmt))
        }
        return out
    }

    private func readProjectRowLocked(_ stmt: OpaquePointer) -> Project {
        Project(
            id: colString(stmt, 0) ?? "",
            areaId: colString(stmt, 1),
            title: colString(stmt, 2) ?? "",
            notes: colString(stmt, 3) ?? "",
            status: ProjectStatus.normalized(colString(stmt, 4)),
            scheduleState: ScheduleState.normalized(colString(stmt, 5), fallback: .anytime),
            whenDate: colInt64(stmt, 6),
            deadline: colInt64(stmt, 7),
            completedAt: colInt64(stmt, 8),
            sortOrder: sqlite3_column_double(stmt, 9),
            tagIds: decodeStringArray(colString(stmt, 10)),
            totalTaskCount: Int(sqlite3_column_int(stmt, 14)),
            completedTaskCount: Int(sqlite3_column_int(stmt, 15)),
            createdAt: sqlite3_column_int64(stmt, 11),
            updatedAt: sqlite3_column_int64(stmt, 12),
            isDeleted: colBool(stmt, 13)
        )
    }

    private func fetchAllActiveHeadingsLocked() throws -> [Heading] {
        try fetchHeadingsWhereLocked("is_deleted = 0")
    }

    private func fetchHeadingsWhereLocked(_ whereClause: String) throws -> [Heading] {
        let stmt = try prepareLocked("""
            SELECT id, project_id, title, sort_order, is_archived, created_at, updated_at, is_deleted
            FROM headings WHERE \(whereClause) ORDER BY sort_order ASC;
        """)
        defer { sqlite3_finalize(stmt) }
        var out: [Heading] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            out.append(
                Heading(
                    id: colString(stmt, 0) ?? "",
                    projectId: colString(stmt, 1) ?? "",
                    title: colString(stmt, 2) ?? "",
                    sortOrder: sqlite3_column_double(stmt, 3),
                    isArchived: colBool(stmt, 4),
                    createdAt: sqlite3_column_int64(stmt, 5),
                    updatedAt: sqlite3_column_int64(stmt, 6),
                    isDeleted: colBool(stmt, 7)
                )
            )
        }
        return out
    }

    private func fetchAllActiveChecklistItemsByTaskLocked() throws -> [String: [ChecklistItem]] {
        let items = try fetchChecklistsWhereLocked("is_deleted = 0")
        return Dictionary(grouping: items, by: \.taskId)
    }

    private func fetchChecklistsWhereLocked(_ whereClause: String) throws -> [ChecklistItem] {
        let stmt = try prepareLocked("""
            SELECT id, task_id, title, is_completed, sort_order, created_at, updated_at, is_deleted
            FROM checklist_items WHERE \(whereClause) ORDER BY sort_order ASC;
        """)
        defer { sqlite3_finalize(stmt) }
        var out: [ChecklistItem] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            out.append(
                ChecklistItem(
                    id: colString(stmt, 0) ?? "",
                    taskId: colString(stmt, 1) ?? "",
                    title: colString(stmt, 2) ?? "",
                    isCompleted: colBool(stmt, 3),
                    sortOrder: sqlite3_column_double(stmt, 4),
                    createdAt: sqlite3_column_int64(stmt, 5),
                    updatedAt: sqlite3_column_int64(stmt, 6),
                    isDeleted: colBool(stmt, 7)
                )
            )
        }
        return out
    }

    private func fetchAllTaskTagIdsLocked() throws -> [String: [String]] {
        let stmt = try prepareLocked("SELECT task_id, tag_id FROM task_tags;")
        defer { sqlite3_finalize(stmt) }
        var map: [String: [String]] = [:]
        while sqlite3_step(stmt) == SQLITE_ROW {
            if let tid = colString(stmt, 0), let tgid = colString(stmt, 1) {
                map[tid, default: []].append(tgid)
            }
        }
        return map
    }

    private func fetchTaskByIdLocked(_ id: String) throws -> TaskItem? {
        let tags = try fetchActiveTagsLocked()
        let tagsById = Dictionary(uniqueKeysWithValues: tags.map { ($0.id, $0) })
        let checklists = try fetchChecklistsWhereLocked("task_id = '\(id.replacingOccurrences(of: "'", with: "''"))' AND is_deleted = 0")
        let tagIds = try fetchAllTaskTagIdsLocked()

        let found = try fetchTasksWhereLocked(
            whereClause: "id = ?",
            bind: { stmt in self.bindText(stmt, 1, id) },
            orderBy: "sort_order ASC",
            tagsById: tagsById,
            tagIdsByTask: tagIds,
            checklistsByTask: [id: checklists]
        )
        return found.first
    }

    private func fetchTasksWhereLocked(
        whereClause: String,
        bind: (OpaquePointer) -> Void,
        orderBy: String,
        tagsById: [String: Tag],
        tagIdsByTask: [String: [String]],
        checklistsByTask: [String: [ChecklistItem]]
    ) throws -> [TaskItem] {
        let stmt = try prepareLocked("""
            SELECT id, title, notes, status, schedule_state, when_date, deadline, reminder_time,
                   completed_at, project_id, heading_id, area_id, recurrence_rule, sort_order,
                   tag_ids_json, created_at, updated_at, is_deleted
            FROM tasks WHERE \(whereClause) ORDER BY \(orderBy);
        """)
        defer { sqlite3_finalize(stmt) }
        bind(stmt)

        var out: [TaskItem] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            out.append(readTaskRowLocked(stmt, tagsById: tagsById, tagIdsByTask: tagIdsByTask, checklistsByTask: checklistsByTask))
        }
        return out
    }

    private func readTaskRowLocked(
        _ stmt: OpaquePointer,
        tagsById: [String: Tag],
        tagIdsByTask: [String: [String]],
        checklistsByTask: [String: [ChecklistItem]]
    ) -> TaskItem {
        let id = colString(stmt, 0) ?? ""
        let jsonTagIds = decodeStringArray(colString(stmt, 14))
        let refTagIds = tagIdsByTask[id] ?? []
        let combinedIds = refTagIds.isEmpty ? jsonTagIds : Array(Set(refTagIds + jsonTagIds))
        let resolvedTags = combinedIds.compactMap { tagsById[$0] }.sorted { $0.sortOrder < $1.sortOrder }

        return TaskItem(
            id: id,
            title: colString(stmt, 1) ?? "",
            notes: colString(stmt, 2) ?? "",
            status: TaskStatus.normalized(colString(stmt, 3)),
            scheduleState: ScheduleState.normalized(colString(stmt, 4), fallback: .inbox),
            whenDate: colInt64(stmt, 5),
            deadline: colInt64(stmt, 6),
            reminderTime: colInt64(stmt, 7),
            completedAt: colInt64(stmt, 8),
            projectId: colString(stmt, 9),
            headingId: colString(stmt, 10),
            areaId: colString(stmt, 11),
            recurrenceRule: decodeRecurrence(colString(stmt, 12)),
            sortOrder: sqlite3_column_double(stmt, 13),
            checklistItems: checklistsByTask[id] ?? [],
            tags: resolvedTags,
            createdAt: sqlite3_column_int64(stmt, 15),
            updatedAt: sqlite3_column_int64(stmt, 16),
            isDeleted: colBool(stmt, 17)
        )
    }

    private func computeStatisticsLocked(endOfToday: Int64) throws -> DatabaseStatistics {
        func scalar(_ sql: String, _ param: Int64? = nil) -> Int {
            guard let stmt = try? prepareLocked(sql) else { return 0 }
            defer { sqlite3_finalize(stmt) }
            if let param { bindInt64(stmt, 1, param) }
            if sqlite3_step(stmt) == SQLITE_ROW {
                return Int(sqlite3_column_int(stmt, 0))
            }
            return 0
        }

        let openTasks = scalar("SELECT COUNT(*) FROM tasks WHERE status = 'OPEN' AND is_deleted = 0;")
        let completedTasks = scalar("SELECT COUNT(*) FROM tasks WHERE status = 'COMPLETED' AND is_deleted = 0;")
        let deletedTasks = scalar("SELECT COUNT(*) FROM tasks WHERE is_deleted = 1;")
        let canceledTasks = scalar("SELECT COUNT(*) FROM tasks WHERE status = 'CANCELED' AND is_deleted = 0;")
        let todayActive = scalar("""
            SELECT COUNT(*) FROM tasks
            WHERE status = 'OPEN' AND is_deleted = 0
              AND (schedule_state IN ('TODAY', 'THIS_EVENING') OR (when_date IS NOT NULL AND when_date <= ?));
        """, endOfToday)
        let recurring = scalar("SELECT COUNT(*) FROM tasks WHERE status = 'OPEN' AND is_deleted = 0 AND recurrence_rule IS NOT NULL AND recurrence_rule != '';")
        let activeProjects = scalar("SELECT COUNT(*) FROM projects WHERE status = 'ACTIVE' AND is_deleted = 0;")
        let savedTags = scalar("SELECT COUNT(*) FROM tags WHERE is_deleted = 0;")
        let totalRecords =
            scalar("SELECT COUNT(*) FROM tasks;") +
            scalar("SELECT COUNT(*) FROM projects;") +
            scalar("SELECT COUNT(*) FROM areas;") +
            scalar("SELECT COUNT(*) FROM headings;") +
            scalar("SELECT COUNT(*) FROM checklist_items;") +
            scalar("SELECT COUNT(*) FROM tags;")

        return DatabaseStatistics(
            openTasks: openTasks,
            completedTasks: completedTasks,
            deletedTasks: deletedTasks,
            canceledTasks: canceledTasks,
            todayActiveTasks: todayActive,
            recurringTasks: recurring,
            activeProjects: activeProjects,
            savedTags: savedTags,
            totalRecords: totalRecords
        )
    }
}
