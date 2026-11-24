import CryptoKit
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

public struct RemoteAttachmentDelta: Equatable, Sendable {
    public var attachment: TaskAttachment
    public var dataBase64: String

    public init(attachment: TaskAttachment, dataBase64: String = "") {
        self.attachment = attachment
        self.dataBase64 = dataBase64
    }
}

public struct SyncDirtyBatch: Sendable {
    public var tags: [Tag]
    public var areas: [Area]
    public var projects: [Project]
    public var headings: [Heading]
    public var tasks: [TaskItem]
    public var checklistItems: [ChecklistItem]
    public var attachments: [TaskAttachment]
    public var projectTemplates: [ProjectTemplate]

    public init(
        tags: [Tag] = [],
        areas: [Area] = [],
        projects: [Project] = [],
        headings: [Heading] = [],
        tasks: [TaskItem] = [],
        checklistItems: [ChecklistItem] = [],
        attachments: [TaskAttachment] = [],
        projectTemplates: [ProjectTemplate] = []
    ) {
        self.tags = tags
        self.areas = areas
        self.projects = projects
        self.headings = headings
        self.tasks = tasks
        self.checklistItems = checklistItems
        self.attachments = attachments
        self.projectTemplates = projectTemplates
    }
}

public struct RemoteDeltaBatch: Sendable {
    public var tags: [Tag]
    public var areas: [Area]
    public var projects: [Project]
    public var projectOmittedV30Ids: Set<String>
    public var headings: [Heading]
    public var headingOmittedStartDateIds: Set<String>
    public var headingOmittedEndDateIds: Set<String>
    public var tasks: [TaskItem]
    public var taskTagIdsByTaskId: [String: [String]]
    public var taskOmittedTimeblockTaskIds: Set<String>
    public var taskOmittedPinTaskIds: Set<String>
    public var taskOmittedMilestoneTaskIds: Set<String>
    public var taskOmittedV30TaskIds: Set<String>
    public var taskOmittedActualMinutesIds: Set<String>
    public var taskOmittedInProgressIds: Set<String>
    public var checklistItems: [ChecklistItem]
    public var attachments: [RemoteAttachmentDelta]
    public var projectTemplates: [ProjectTemplate]

    public init(
        tags: [Tag] = [],
        areas: [Area] = [],
        projects: [Project] = [],
        projectOmittedV30Ids: Set<String> = [],
        headings: [Heading] = [],
        headingOmittedStartDateIds: Set<String> = [],
        headingOmittedEndDateIds: Set<String> = [],
        tasks: [TaskItem] = [],
        taskTagIdsByTaskId: [String: [String]] = [:],
        taskOmittedTimeblockTaskIds: Set<String> = [],
        taskOmittedPinTaskIds: Set<String> = [],
        taskOmittedMilestoneTaskIds: Set<String> = [],
        taskOmittedV30TaskIds: Set<String> = [],
        taskOmittedActualMinutesIds: Set<String> = [],
        taskOmittedInProgressIds: Set<String> = [],
        checklistItems: [ChecklistItem] = [],
        attachments: [RemoteAttachmentDelta] = [],
        projectTemplates: [ProjectTemplate] = []
    ) {
        self.tags = tags
        self.areas = areas
        self.projects = projects
        self.projectOmittedV30Ids = projectOmittedV30Ids
        self.headings = headings
        self.headingOmittedStartDateIds = headingOmittedStartDateIds
        self.headingOmittedEndDateIds = headingOmittedEndDateIds
        self.tasks = tasks
        self.taskTagIdsByTaskId = taskTagIdsByTaskId
        self.taskOmittedTimeblockTaskIds = taskOmittedTimeblockTaskIds
        self.taskOmittedPinTaskIds = taskOmittedPinTaskIds
        self.taskOmittedMilestoneTaskIds = taskOmittedMilestoneTaskIds
        self.taskOmittedV30TaskIds = taskOmittedV30TaskIds
        self.taskOmittedActualMinutesIds = taskOmittedActualMinutesIds
        self.taskOmittedInProgressIds = taskOmittedInProgressIds
        self.checklistItems = checklistItems
        self.attachments = attachments
        self.projectTemplates = projectTemplates
    }
}

public struct WorkspaceSnapshot: Sendable {
    public var areas: [Area]
    public var projects: [Project]
    public var headingsByProject: [String: [Heading]]
    public var projectAttachments: [String: [TaskAttachment]]
    public var projectTemplates: [ProjectTemplate]
    public var tags: [Tag]
    public var inboxTasks: [TaskItem]
    public var todayDaytimeTasks: [TaskItem]
    public var thisEveningTasks: [TaskItem]
    public var upcomingTasks: [TaskItem]
    public var anytimeTasks: [TaskItem]
    public var somedayTasks: [TaskItem]
    public var logbookTasks: [TaskItem]
    public var trashedTasks: [TaskItem]
    public var trashedProjects: [Project]
    public var allOpenTasks: [TaskItem]
    public var statistics: DatabaseStatistics
    public var hasSampleTasks: Bool
    public var hasNonSampleUserTasks: Bool

    public init(
        areas: [Area],
        projects: [Project],
        headingsByProject: [String: [Heading]],
        projectAttachments: [String: [TaskAttachment]],
        projectTemplates: [ProjectTemplate] = [],
        tags: [Tag],
        inboxTasks: [TaskItem],
        todayDaytimeTasks: [TaskItem],
        thisEveningTasks: [TaskItem],
        upcomingTasks: [TaskItem],
        anytimeTasks: [TaskItem],
        somedayTasks: [TaskItem],
        logbookTasks: [TaskItem],
        trashedTasks: [TaskItem],
        trashedProjects: [Project] = [],
        allOpenTasks: [TaskItem],
        statistics: DatabaseStatistics,
        hasSampleTasks: Bool,
        hasNonSampleUserTasks: Bool
    ) {
        self.areas = areas
        self.projects = projects
        self.headingsByProject = headingsByProject
        self.projectAttachments = projectAttachments
        self.projectTemplates = projectTemplates
        self.tags = tags
        self.inboxTasks = inboxTasks
        self.todayDaytimeTasks = todayDaytimeTasks
        self.thisEveningTasks = thisEveningTasks
        self.upcomingTasks = upcomingTasks
        self.anytimeTasks = anytimeTasks
        self.somedayTasks = somedayTasks
        self.logbookTasks = logbookTasks
        self.trashedTasks = trashedTasks
        self.trashedProjects = trashedProjects
        self.allOpenTasks = allOpenTasks
        self.statistics = statistics
        self.hasSampleTasks = hasSampleTasks
        self.hasNonSampleUserTasks = hasNonSampleUserTasks
    }
}

public final class ThingsSQLiteDatabase: @unchecked Sendable {
    public static let maxAreas = 8

    private var db: OpaquePointer?
    private let queue = DispatchQueue(label: "com.thingsbook.macos.sqlite", qos: .userInitiated)
    private let dbPath: String
    private let baseSupportDirURL: URL
    private let ownsTempAttachmentsDir: Bool
    public var onMutation: (@Sendable () -> Void)?
    public var onRemoteSyncApplied: (@Sendable () -> Void)?
    public private(set) var lastRemoteAppliedCount: Int = 0

    public static func defaultDatabaseURL() -> URL {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        let folder = appSupport.appendingPathComponent("Thingsbook", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder.appendingPathComponent("thingsbook.sqlite3")
    }

    public init(path: String? = nil) throws {
        let resolvedPath = path ?? Self.defaultDatabaseURL().path
        self.dbPath = resolvedPath
        if resolvedPath == ":memory:" {
            let tempDir = FileManager.default.temporaryDirectory
                .appendingPathComponent("thingsbook-mem-\(UUID().uuidString)", isDirectory: true)
            try? FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
            self.baseSupportDirURL = tempDir
            self.ownsTempAttachmentsDir = true
        } else {
            self.baseSupportDirURL = URL(fileURLWithPath: resolvedPath).deletingLastPathComponent()
            self.ownsTempAttachmentsDir = false
        }
        try? FileManager.default.createDirectory(
            at: self.baseSupportDirURL.appendingPathComponent("attachments", isDirectory: true),
            withIntermediateDirectories: true
        )
        try queue.sync {
            try openAndMigrateLocked()
        }
    }

    deinit {
        let cleanupTempDir = ownsTempAttachmentsDir ? baseSupportDirURL : nil
        queue.sync {
            if let db {
                sqlite3_close(db)
                self.db = nil
            }
            if let cleanupTempDir {
                try? FileManager.default.removeItem(at: cleanupTempDir)
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
            last_reviewed_at INTEGER DEFAULT NULL,
            sort_order REAL NOT NULL DEFAULT 0.0,
            tag_ids_json TEXT NOT NULL DEFAULT '[]',
            workspace_id TEXT DEFAULT NULL,
            view_mode TEXT DEFAULT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            is_deleted INTEGER NOT NULL DEFAULT 0,
            is_purged INTEGER NOT NULL DEFAULT 0,
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
            start_date INTEGER DEFAULT NULL,
            end_date INTEGER DEFAULT NULL,
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
            scheduled_start_minutes INTEGER DEFAULT NULL,
            duration_minutes INTEGER DEFAULT NULL,
            actual_minutes INTEGER DEFAULT NULL,
            completed_at INTEGER,
            project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
            heading_id TEXT REFERENCES headings(id) ON DELETE SET NULL,
            area_id TEXT REFERENCES areas(id) ON DELETE SET NULL,
            recurrence_rule TEXT,
            sort_order REAL NOT NULL DEFAULT 0.0,
            is_pinned INTEGER NOT NULL DEFAULT 0,
            is_milestone INTEGER NOT NULL DEFAULT 0,
            is_in_progress INTEGER NOT NULL DEFAULT 0,
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
        DROP INDEX IF EXISTS idx_tags_title;
        CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_title_active ON tags(title) WHERE is_deleted = 0;

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

        CREATE TABLE IF NOT EXISTS attachments (
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
        CREATE INDEX IF NOT EXISTS idx_attachments_task ON attachments(task_id);
        CREATE INDEX IF NOT EXISTS idx_attachments_project ON attachments(project_id);
        CREATE INDEX IF NOT EXISTS idx_attachments_updated ON attachments(updated_at);
        CREATE INDEX IF NOT EXISTS idx_attachments_dirty ON attachments(is_dirty);

        CREATE TABLE IF NOT EXISTS project_templates (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL DEFAULT '',
            notes TEXT NOT NULL DEFAULT '',
            default_area_id TEXT,
            tag_ids_json TEXT NOT NULL DEFAULT '[]',
            blueprint_json TEXT NOT NULL DEFAULT '{}',
            sort_order REAL NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL DEFAULT 0,
            is_deleted INTEGER NOT NULL DEFAULT 0,
            is_dirty INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS idx_project_templates_sort ON project_templates(is_deleted, sort_order);
        CREATE INDEX IF NOT EXISTS idx_project_templates_dirty ON project_templates(is_dirty);

        CREATE TABLE IF NOT EXISTS detached_project_tasks (
            project_id TEXT NOT NULL,
            task_id TEXT NOT NULL,
            heading_id TEXT,
            PRIMARY KEY (project_id, task_id)
        );
        """)

        // Safe v1.8.1 column migrations for pre-existing databases
        try? execLocked("ALTER TABLE tasks ADD COLUMN is_purged INTEGER NOT NULL DEFAULT 0;")
        try? execLocked("ALTER TABLE projects ADD COLUMN is_purged INTEGER NOT NULL DEFAULT 0;")

        // Safe non-destructive v2.0.0 column migrations for pre-existing v1.7 / v1.8 databases
        try? execLocked("ALTER TABLE tasks ADD COLUMN scheduled_start_minutes INTEGER DEFAULT NULL;")
        try? execLocked("ALTER TABLE tasks ADD COLUMN duration_minutes INTEGER DEFAULT NULL;")
        try? execLocked("ALTER TABLE projects ADD COLUMN last_reviewed_at INTEGER DEFAULT NULL;")

        // Safe non-destructive v2.5.0 & v3.0.0 column migrations
        try? execLocked("ALTER TABLE tasks ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0;")
        try? execLocked("ALTER TABLE tasks ADD COLUMN is_milestone INTEGER NOT NULL DEFAULT 0;")
        try? execLocked("ALTER TABLE tasks ADD COLUMN effort_level TEXT DEFAULT NULL;")
        try? execLocked("ALTER TABLE tasks ADD COLUMN blocked_by_json TEXT NOT NULL DEFAULT '[]';")
        try? execLocked("ALTER TABLE tasks ADD COLUMN workspace_id TEXT DEFAULT NULL;")
        try? execLocked("ALTER TABLE tasks ADD COLUMN assignee_id TEXT DEFAULT NULL;")
        try? execLocked("ALTER TABLE headings ADD COLUMN start_date INTEGER DEFAULT NULL;")
        try? execLocked("ALTER TABLE headings ADD COLUMN end_date INTEGER DEFAULT NULL;")
        try? execLocked("ALTER TABLE projects ADD COLUMN workspace_id TEXT DEFAULT NULL;")
        try? execLocked("ALTER TABLE projects ADD COLUMN view_mode TEXT DEFAULT NULL;")

        // Safe non-destructive v3.2.0 column migration
        try? execLocked("ALTER TABLE tasks ADD COLUMN actual_minutes INTEGER DEFAULT NULL;")

        // Safe non-destructive v3.3.0 column migration
        try? execLocked("ALTER TABLE tasks ADD COLUMN is_in_progress INTEGER NOT NULL DEFAULT 0;")

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
        rebuildFtsLocked()
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
            let tagsById = Dictionary(tags.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            let projects = try fetchActiveProjectsLocked()
            let trashedProjects = try fetchProjectsWhereLocked("p.is_deleted = 1 AND p.is_purged = 0")
                .sorted { $0.updatedAt > $1.updatedAt }
            let inactiveOrDeletedProjectIds = try fetchInactiveOrDeletedProjectIdsLocked()
            let headings = try fetchAllActiveHeadingsLocked()
            let headingsByProject = Dictionary(grouping: headings, by: \.projectId)

            let checklistsByTask = try fetchAllActiveChecklistItemsByTaskLocked()
            let (attachmentsByTask, projectAttachments) = try fetchAllActiveAttachmentsGroupedLocked()
            let projectTemplates = try fetchActiveProjectTemplatesLocked()
            let tagIdsByTask = try fetchAllTaskTagIdsLocked()

            let endOfToday = DateEpochConverter.endOfDayMillis(for: referenceDate, calendar: calendar)
            let startOfTomorrow: Int64 = {
                let sod = calendar.startOfDay(for: referenceDate)
                let tmr = calendar.date(byAdding: .day, value: 1, to: sod) ?? sod.addingTimeInterval(86_400)
                return DateEpochConverter.startOfDayMillis(for: tmr, calendar: calendar)
            }()

            let allTasks = try fetchTasksWhereLocked(
                whereClause: "is_purged = 0",
                bind: { _ in },
                orderBy: "sort_order ASC, created_at ASC",
                tagsById: tagsById,
                tagIdsByTask: tagIdsByTask,
                checklistsByTask: checklistsByTask,
                attachmentsByTask: attachmentsByTask
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

                let belongsToInactiveOrTrashedProject = t.projectId.map { inactiveOrDeletedProjectIds.contains($0) } ?? false

                if t.scheduleState == .inbox {
                    if !belongsToInactiveOrTrashedProject {
                        inbox.append(t)
                    }
                } else if !belongsToInactiveOrTrashedProject {
                    if t.scheduleState == .thisEvening {
                        thisEvening.append(t)
                    } else if t.scheduleState == .today || (t.whenDate != nil && t.whenDate! <= endOfToday) {
                        todayDaytime.append(t)
                    }
                    if (t.scheduleState == .upcoming && (t.whenDate == nil || t.whenDate! >= startOfTomorrow))
                        || (t.whenDate != nil && t.whenDate! >= startOfTomorrow)
                        || (t.isMilestone && t.whenDate == nil && t.deadline != nil && t.deadline! >= startOfTomorrow) {
                        upcoming.append(t)
                    }
                    if t.scheduleState == .anytime || t.scheduleState == .today || t.scheduleState == .thisEvening {
                        anytime.append(t)
                    }
                    if t.scheduleState == .someday {
                        someday.append(t)
                    }
                }
            }

            inbox = TaskViewSorter.sort(inbox, mode: "manual")
            todayDaytime.sort {
                if $0.isPinned != $1.isPinned { return $0.isPinned && !$1.isPinned }
                let ip0 = $0.status == .open && $0.isInProgress
                let ip1 = $1.status == .open && $1.isInProgress
                if ip0 != ip1 { return ip0 && !ip1 }
                let d0 = $0.deadline == nil ? 1 : 0
                let d1 = $1.deadline == nil ? 1 : 0
                if d0 != d1 { return d0 < d1 }
                if let dl0 = $0.deadline, let dl1 = $1.deadline, dl0 != dl1 { return dl0 < dl1 }
                return $0.sortOrder < $1.sortOrder
            }
            thisEvening = TaskViewSorter.sort(thisEvening, mode: "manual")
            upcoming.sort {
                let w0 = $0.whenDate ?? ($0.isMilestone ? $0.deadline : nil) ?? Int64.max
                let w1 = $1.whenDate ?? ($1.isMilestone ? $1.deadline : nil) ?? Int64.max
                if w0 != w1 { return w0 < w1 }
                if $0.isPinned != $1.isPinned { return $0.isPinned && !$1.isPinned }
                let ip0 = $0.status == .open && $0.isInProgress
                let ip1 = $1.status == .open && $1.isInProgress
                if ip0 != ip1 { return ip0 && !ip1 }
                return $0.sortOrder < $1.sortOrder
            }
            anytime = TaskViewSorter.sort(anytime, mode: "manual")
            someday = TaskViewSorter.sort(someday, mode: "manual")
            logbook.sort { ($0.completedAt ?? $0.updatedAt) > ($1.completedAt ?? $1.updatedAt) }
            trashed.sort { $0.updatedAt > $1.updatedAt }

            let activeTasksNonDeleted = allTasks.filter { !$0.isDeleted }
            let hasSample = activeTasksNonDeleted.contains { DatabaseSeeder.isActiveSampleTask($0) }
            let hasNonSample = activeTasksNonDeleted.contains { !DatabaseSeeder.isPurgeableSampleTask($0) }

            let stats = try computeStatisticsLocked(endOfToday: endOfToday)

            return WorkspaceSnapshot(
                areas: areas,
                projects: projects,
                headingsByProject: headingsByProject,
                projectAttachments: projectAttachments,
                projectTemplates: projectTemplates,
                tags: tags,
                inboxTasks: inbox,
                todayDaytimeTasks: todayDaytime,
                thisEveningTasks: thisEvening,
                upcomingTasks: upcoming,
                anytimeTasks: anytime,
                somedayTasks: someday,
                logbookTasks: logbook,
                trashedTasks: trashed,
                trashedProjects: trashedProjects,
                allOpenTasks: allOpen,
                statistics: stats,
                hasSampleTasks: hasSample,
                hasNonSampleUserTasks: hasNonSample
            )
        }
    }

    public func searchQuickFind(
        rawQuery: String,
        filter: QuickFindFilter = QuickFindFilter(),
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) throws -> (tasks: [TaskItem], projects: [Project]) {
        let sanitized = Self.buildSafeFtsQuery(rawQuery)
        guard !sanitized.isEmpty || filter.hasActiveFilters else { return ([], []) }

        return try queue.sync {
            let tags = try fetchActiveTagsLocked()
            let tagsById = Dictionary(tags.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            let activeTagIds = Set(tags.map(\.id))
            let checklistsByTask = try fetchAllActiveChecklistItemsByTaskLocked()
            let (attachmentsByTask, _) = try fetchAllActiveAttachmentsGroupedLocked()
            let tagIdsByTask = try fetchAllTaskTagIdsLocked()
            let projectTagIdsByProject = try fetchAllProjectTagIdsLocked()

            var matchedTasks: [TaskItem] = []
            if filter.scope != .projects {
                let statusClause = (filter.hideCompleted || filter.inProgressOnly) ? " AND t.status = 'OPEN'" : ""
                let inProgClause = filter.inProgressOnly ? " AND t.is_in_progress = 1" : ""
                let taskSql: String
                if !sanitized.isEmpty {
                    taskSql = """
                        SELECT t.id, t.title, t.notes, t.status, t.schedule_state, t.when_date, t.deadline,
                               t.reminder_time, t.completed_at, t.project_id, t.heading_id, t.area_id,
                               t.recurrence_rule, t.sort_order, t.tag_ids_json, t.created_at, t.updated_at, t.is_deleted,
                               t.scheduled_start_minutes, t.duration_minutes,
                               t.is_pinned, t.effort_level, t.blocked_by_json, t.workspace_id, t.assignee_id, t.is_milestone,
                               t.actual_minutes, t.is_in_progress
                        FROM tasks t
                        JOIN tasks_fts fts ON t.rowid = fts.rowid
                        WHERE tasks_fts MATCH ? AND t.is_deleted = 0\(statusClause)\(inProgClause)
                        ORDER BY bm25(tasks_fts), t.updated_at DESC
                        LIMIT 250;
                    """
                } else {
                    taskSql = """
                        SELECT t.id, t.title, t.notes, t.status, t.schedule_state, t.when_date, t.deadline,
                               t.reminder_time, t.completed_at, t.project_id, t.heading_id, t.area_id,
                               t.recurrence_rule, t.sort_order, t.tag_ids_json, t.created_at, t.updated_at, t.is_deleted,
                               t.scheduled_start_minutes, t.duration_minutes,
                               t.is_pinned, t.effort_level, t.blocked_by_json, t.workspace_id, t.assignee_id, t.is_milestone,
                               t.actual_minutes, t.is_in_progress
                        FROM tasks t
                        WHERE t.is_deleted = 0\(statusClause)\(inProgClause)
                        ORDER BY CASE WHEN t.status = 'OPEN' THEN 0 ELSE 1 END ASC, t.updated_at DESC
                        LIMIT 300;
                    """
                }
                let taskStmt = try prepareLocked(taskSql)
                defer { sqlite3_finalize(taskStmt) }
                if !sanitized.isEmpty {
                    bindText(taskStmt, 1, sanitized)
                }

                while sqlite3_step(taskStmt) == SQLITE_ROW {
                    let item = readTaskRowLocked(
                        taskStmt,
                        tagsById: tagsById,
                        tagIdsByTask: tagIdsByTask,
                        checklistsByTask: checklistsByTask,
                        attachmentsByTask: attachmentsByTask
                    )
                    if filter.matchesTask(item, referenceDate: referenceDate, calendar: calendar) {
                        matchedTasks.append(item)
                        if matchedTasks.count >= 50 {
                            break
                        }
                    }
                }
            }

            var matchedProjects: [Project] = []
            if filter.scope != .tasks && !filter.hasChecklistOnly {
                let projStatusClause = filter.hideCompleted ? " AND p.status = 'ACTIVE'" : ""
                let projSql: String
                if !sanitized.isEmpty {
                    projSql = """
                        SELECT p.id, p.area_id, p.title, p.notes, p.status, p.schedule_state, p.when_date,
                               p.deadline, p.completed_at, p.sort_order, p.tag_ids_json, p.created_at, p.updated_at, p.is_deleted,
                               (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status != 'CANCELED') AS total_cnt,
                               (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status = 'COMPLETED') AS comp_cnt,
                               p.last_reviewed_at, p.workspace_id, p.view_mode,
                               (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status = 'OPEN' AND t.is_in_progress = 1) AS in_prog_cnt
                        FROM projects p
                        JOIN projects_fts fts ON p.rowid = fts.rowid
                        WHERE projects_fts MATCH ? AND p.is_deleted = 0\(projStatusClause)
                        ORDER BY bm25(projects_fts), p.sort_order ASC
                        LIMIT 100;
                    """
                } else {
                    projSql = """
                        SELECT p.id, p.area_id, p.title, p.notes, p.status, p.schedule_state, p.when_date,
                               p.deadline, p.completed_at, p.sort_order, p.tag_ids_json, p.created_at, p.updated_at, p.is_deleted,
                               (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status != 'CANCELED') AS total_cnt,
                               (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status = 'COMPLETED') AS comp_cnt,
                               p.last_reviewed_at, p.workspace_id, p.view_mode,
                               (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status = 'OPEN' AND t.is_in_progress = 1) AS in_prog_cnt
                        FROM projects p
                        WHERE p.is_deleted = 0\(projStatusClause)
                        ORDER BY p.sort_order ASC
                        LIMIT 100;
                    """
                }
                let projStmt = try prepareLocked(projSql)
                defer { sqlite3_finalize(projStmt) }
                if !sanitized.isEmpty {
                    bindText(projStmt, 1, sanitized)
                }

                while sqlite3_step(projStmt) == SQLITE_ROW {
                    let proj = readProjectRowLocked(
                        projStmt,
                        activeTagIds: activeTagIds,
                        projectTagIdsByProject: projectTagIdsByProject
                    )
                    if filter.matchesProject(proj, referenceDate: referenceDate, calendar: calendar) {
                        matchedProjects.append(proj)
                        if matchedProjects.count >= 25 {
                            break
                        }
                    }
                }
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
        var promotedCount = 0
        var dedupedCount = 0
        try queue.sync {
            let endOfToday = DateEpochConverter.endOfDayMillis(for: referenceDate, calendar: calendar)
            let sodToday = DateEpochConverter.startOfDayMillis(for: referenceDate, calendar: calendar)
            let now = nowMillis()

            try inTransactionLocked {
                var total = 0
                let stmt1 = try prepareLocked("""
                    UPDATE tasks
                    SET schedule_state = 'TODAY'
                    WHERE status = 'OPEN' AND is_deleted = 0
                      AND schedule_state = 'UPCOMING'
                      AND when_date IS NOT NULL AND when_date <= ?;
                """)
                defer { sqlite3_finalize(stmt1) }
                bindInt64(stmt1, 1, endOfToday)
                if sqlite3_step(stmt1) == SQLITE_DONE {
                    total += Int(sqlite3_changes(db))
                }

                let stmt2 = try prepareLocked("""
                    UPDATE tasks
                    SET schedule_state = 'TODAY', when_date = ?
                    WHERE status = 'OPEN' AND is_deleted = 0
                      AND schedule_state = 'THIS_EVENING'
                      AND ((when_date IS NOT NULL AND when_date < ?) OR (when_date IS NULL AND updated_at < ?));
                """)
                defer { sqlite3_finalize(stmt2) }
                bindInt64(stmt2, 1, sodToday)
                bindInt64(stmt2, 2, sodToday)
                bindInt64(stmt2, 3, sodToday)
                if sqlite3_step(stmt2) == SQLITE_DONE {
                    total += Int(sqlite3_changes(db))
                }

                let stmt3 = try prepareLocked("""
                    UPDATE projects
                    SET schedule_state = 'TODAY'
                    WHERE status = 'ACTIVE' AND is_deleted = 0
                      AND schedule_state = 'UPCOMING'
                      AND when_date IS NOT NULL AND when_date <= ?;
                """)
                defer { sqlite3_finalize(stmt3) }
                bindInt64(stmt3, 1, endOfToday)
                if sqlite3_step(stmt3) == SQLITE_DONE {
                    total += Int(sqlite3_changes(db))
                }

                let stmt4 = try prepareLocked("""
                    UPDATE projects
                    SET schedule_state = 'TODAY', when_date = ?
                    WHERE status = 'ACTIVE' AND is_deleted = 0
                      AND schedule_state = 'THIS_EVENING'
                      AND ((when_date IS NOT NULL AND when_date < ?) OR (when_date IS NULL AND updated_at < ?));
                """)
                defer { sqlite3_finalize(stmt4) }
                bindInt64(stmt4, 1, sodToday)
                bindInt64(stmt4, 2, sodToday)
                bindInt64(stmt4, 3, sodToday)
                if sqlite3_step(stmt4) == SQLITE_DONE {
                    total += Int(sqlite3_changes(db))
                }

                promotedCount = total
                dedupedCount = try deduplicateOpenRecurringTasksLocked(nowMillis: now)
            }
        }
        if dedupedCount > 0 {
            notifyMutation()
        } else if promotedCount > 0 {
            if let onRemoteSyncApplied {
                onRemoteSyncApplied()
            } else {
                notifyMutation()
            }
        }
        return promotedCount + dedupedCount
    }

    @discardableResult
    public func deduplicateOpenRecurringTasks() throws -> Int {
        let deduped: Int = try queue.sync {
            let now = nowMillis()
            return try inTransactionLocked {
                try deduplicateOpenRecurringTasksLocked(nowMillis: now)
            }
        }
        if deduped > 0 {
            notifyMutation()
        }
        return deduped
    }

    private func fetchOpenRecurringTasksWithRawTagIdsLocked() throws -> (tasks: [TaskItem], rawTagIdsByTask: [String: [String]]) {
        let tags = try fetchActiveTagsLocked()
        let tagsById = Dictionary(tags.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let tagIds = try fetchAllTaskTagIdsLocked()
        let tasks = try fetchTasksWhereLocked(
            whereClause: "is_deleted = 0 AND status = 'OPEN' AND recurrence_rule IS NOT NULL",
            bind: { _ in },
            orderBy: "sort_order ASC",
            tagsById: tagsById,
            tagIdsByTask: tagIds,
            checklistsByTask: [:]
        )
        return (tasks, tagIds)
    }

    private func deduplicateOpenRecurringTasksLocked(nowMillis: Int64) throws -> Int {
        let (openRecurring, rawTagIdsByTask) = try fetchOpenRecurringTasksWithRawTagIdsLocked()
        let plan = Self.planRecurringDeduplication(
            openRecurring,
            overrideTagIdsByTaskId: rawTagIdsByTask
        )
        guard !plan.loserIds.isEmpty || !plan.winnerEnrichments.isEmpty else { return 0 }
        let openById = Dictionary(openRecurring.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })

        for patch in plan.winnerEnrichments {
            guard var winner = openById[patch.winnerId] else { continue }
            if let donorArea = patch.areaId { winner.areaId = donorArea }
            if let donorHeading = patch.headingId { winner.headingId = donorHeading }
            if let donorNotes = patch.notes { winner.notes = donorNotes }
            if let donorStart = patch.scheduledStartMinutes { winner.scheduledStartMinutes = donorStart }
            if let donorDuration = patch.durationMinutes { winner.durationMinutes = donorDuration }
            if let donorDeadline = patch.deadline { winner.deadline = donorDeadline }
            if let donorReminder = patch.reminderTime { winner.reminderTime = donorReminder }
            winner.updatedAt = max(nowMillis, patch.minUpdatedAt)
            let mergedTags = patch.tagIds ?? (rawTagIdsByTask[winner.id] ?? winner.tagIds)
            try upsertTaskLocked(winner, tagIds: mergedTags, isDirty: true)
        }

        for loserId in plan.loserIds {
            let prevUpdatedAt = openById[loserId]?.updatedAt ?? 0
            let tombstoneAt = max(nowMillis, prevUpdatedAt + 1)
            try purgeTaskToTombstoneLocked(taskId: loserId, now: tombstoneAt)
        }
        return plan.loserIds.count + plan.winnerEnrichments.count
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
        scheduledStartMinutes: Int? = nil,
        durationMinutes: Int? = nil,
        projectId: String? = nil,
        headingId: String? = nil,
        areaId: String? = nil,
        recurrenceRule: RecurrenceRule? = nil,
        tagIds: [String] = [],
        checklistTitles: [String] = [],
        sortOrder: Double? = nil,
        isMilestone: Bool = false,
        isInProgress: Bool = false
    ) throws -> TaskItem {
        let created = try queue.sync { () -> TaskItem in
            let now = nowMillis()
            return try inTransactionLocked {
                let resolvedSortOrder: Double
                if let explicitOrder = sortOrder, explicitOrder.isFinite {
                    resolvedSortOrder = explicitOrder
                } else {
                    resolvedSortOrder = (try maxSortOrderLocked(table: "tasks")) + 1000.0
                }
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
                    scheduledStartMinutes: scheduledStartMinutes,
                    durationMinutes: durationMinutes,
                    projectId: projectId,
                    headingId: headingId,
                    areaId: resolvedAreaId,
                    recurrenceRule: recurrenceRule,
                    sortOrder: resolvedSortOrder,
                    isMilestone: isMilestone,
                    checklistItems: items,
                    tags: tags,
                    createdAt: now,
                    updatedAt: now,
                    isInProgress: isInProgress
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

    public func setTaskTimeblock(
        taskId: String,
        scheduledStartMinutes: Int?,
        durationMinutes: Int?
    ) throws {
        try queue.sync {
            let now = nowMillis()
            let normalizedStart = scheduledStartMinutes.map { (($0 % 1440) + 1440) % 1440 }
            if normalizedStart == nil && durationMinutes == nil {
                let stmt = try prepareLocked("""
                    UPDATE tasks
                    SET scheduled_start_minutes = NULL,
                        updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END,
                        is_dirty = 1
                    WHERE id = ?;
                """)
                defer { sqlite3_finalize(stmt) }
                bindInt64(stmt, 1, now)
                bindInt64(stmt, 2, now)
                bindText(stmt, 3, taskId)
                if sqlite3_step(stmt) != SQLITE_DONE {
                    throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
                }
            } else {
                let normalizedDuration: Int? = {
                    if let d = durationMinutes, d > 0 { return min(d, 48_000) }
                    if normalizedStart != nil { return 30 }
                    return nil
                }()
                let stmt = try prepareLocked("""
                    UPDATE tasks
                    SET scheduled_start_minutes = ?,
                        duration_minutes = ?,
                        updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END,
                        is_dirty = 1
                    WHERE id = ?;
                """)
                defer { sqlite3_finalize(stmt) }
                bindInt64(stmt, 1, normalizedStart.map(Int64.init))
                bindInt64(stmt, 2, normalizedDuration.map(Int64.init))
                bindInt64(stmt, 3, now)
                bindInt64(stmt, 4, now)
                bindText(stmt, 5, taskId)
                if sqlite3_step(stmt) != SQLITE_DONE {
                    throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
                }
            }
        }
        notifyMutation()
    }

    public func setTasksPinned(_ taskIds: [String], isPinned: Bool) throws {
        guard !taskIds.isEmpty else { return }
        let didMutate: Bool = try queue.sync {
            let now = nowMillis()
            var changed = false
            try inTransactionLocked {
                let stmt = try prepareLocked("""
                    UPDATE tasks
                    SET is_pinned = ?,
                        updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END,
                        is_dirty = 1
                    WHERE id = ? AND is_deleted = 0;
                """)
                defer { sqlite3_finalize(stmt) }
                for tid in Set(taskIds) {
                    sqlite3_reset(stmt)
                    sqlite3_clear_bindings(stmt)
                    bindBool(stmt, 1, isPinned)
                    bindInt64(stmt, 2, now)
                    bindInt64(stmt, 3, now)
                    bindText(stmt, 4, tid)
                    if sqlite3_step(stmt) == SQLITE_DONE, sqlite3_changes(db) > 0 {
                        changed = true
                    }
                }
            }
            return changed
        }
        if didMutate { notifyMutation() }
    }

    public func setTaskMilestone(taskId: String, isMilestone: Bool) throws {
        try setTasksMilestone([taskId], isMilestone: isMilestone)
    }

    public func setTasksMilestone(_ taskIds: [String], isMilestone: Bool) throws {
        guard !taskIds.isEmpty else { return }
        let didMutate: Bool = try queue.sync {
            let now = nowMillis()
            var changed = false
            try inTransactionLocked {
                let stmt = try prepareLocked("""
                    UPDATE tasks
                    SET is_milestone = ?,
                        updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END,
                        is_dirty = 1
                    WHERE id = ? AND is_deleted = 0;
                """)
                defer { sqlite3_finalize(stmt) }
                for tid in Set(taskIds) {
                    sqlite3_reset(stmt)
                    sqlite3_clear_bindings(stmt)
                    bindBool(stmt, 1, isMilestone)
                    bindInt64(stmt, 2, now)
                    bindInt64(stmt, 3, now)
                    bindText(stmt, 4, tid)
                    if sqlite3_step(stmt) == SQLITE_DONE, sqlite3_changes(db) > 0 {
                        changed = true
                    }
                }
            }
            return changed
        }
        if didMutate { notifyMutation() }
    }

    public func setTaskInProgress(_ id: String, isInProgress: Bool) throws {
        try setTasksInProgress([id], isInProgress: isInProgress)
    }

    public func setTasksInProgress(_ ids: [String], isInProgress: Bool) throws {
        guard !ids.isEmpty else { return }
        let didMutate: Bool = try queue.sync {
            let now = nowMillis()
            var changed = false
            try inTransactionLocked {
                let stmt = try prepareLocked("""
                    UPDATE tasks
                    SET is_in_progress = CASE WHEN status = 'OPEN' THEN ? ELSE 0 END,
                        updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END,
                        is_dirty = 1
                    WHERE id = ? AND is_deleted = 0;
                """)
                defer { sqlite3_finalize(stmt) }
                for tid in Set(ids) {
                    sqlite3_reset(stmt)
                    sqlite3_clear_bindings(stmt)
                    bindBool(stmt, 1, isInProgress)
                    bindInt64(stmt, 2, now)
                    bindInt64(stmt, 3, now)
                    bindText(stmt, 4, tid)
                    if sqlite3_step(stmt) == SQLITE_DONE, sqlite3_changes(db) > 0 {
                        changed = true
                    }
                }
            }
            return changed
        }
        if didMutate { notifyMutation() }
    }

    public func setTaskEffortAndDuration(
        taskId: String,
        effortLevel: String?,
        durationMinutes: Int?
    ) throws {
        let didMutate: Bool = try queue.sync {
            let now = nowMillis()
            let normalizedEffort = TaskEffortLevel.normalized(effortLevel)?.rawValue
            let normalizedDuration: Int? = {
                if let d = durationMinutes, d > 0 { return min(d, 48_000) }
                return nil
            }()
            let stmt = try prepareLocked("""
                UPDATE tasks
                SET effort_level = ?,
                    duration_minutes = ?,
                    updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END,
                    is_dirty = 1
                WHERE id = ? AND is_deleted = 0;
            """)
            defer { sqlite3_finalize(stmt) }
            bindText(stmt, 1, normalizedEffort)
            bindInt64(stmt, 2, normalizedDuration.map(Int64.init))
            bindInt64(stmt, 3, now)
            bindInt64(stmt, 4, now)
            bindText(stmt, 5, taskId)
            if sqlite3_step(stmt) != SQLITE_DONE {
                throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
            }
            return sqlite3_changes(db) > 0
        }
        if didMutate { notifyMutation() }
    }

    public func setTaskActualMinutes(taskId: String, actualMinutes: Int?) throws {
        let didMutate: Bool = try queue.sync {
            let now = nowMillis()
            let normalizedActual: Int64? = {
                if let m = actualMinutes, m > 0 {
                    return Int64(min(48_000, max(1, m)))
                }
                return nil
            }()
            let stmt = try prepareLocked("""
                UPDATE tasks
                SET actual_minutes = ?,
                    updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END,
                    is_dirty = 1
                WHERE id = ? AND is_deleted = 0;
            """)
            defer { sqlite3_finalize(stmt) }
            bindInt64(stmt, 1, normalizedActual)
            bindInt64(stmt, 2, now)
            bindInt64(stmt, 3, now)
            bindText(stmt, 4, taskId)
            if sqlite3_step(stmt) != SQLITE_DONE {
                throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
            }
            return sqlite3_changes(db) > 0
        }
        if didMutate { notifyMutation() }
    }

    public func addTaskActualMinutes(taskId: String, deltaMinutes: Int) throws {
        guard deltaMinutes > 0 else { return }
        let didMutate: Bool = try queue.sync {
            let now = nowMillis()
            return try inTransactionLocked {
                let readStmt = try prepareLocked("SELECT COALESCE(actual_minutes, 0) FROM tasks WHERE id = ? AND is_deleted = 0 LIMIT 1;")
                defer { sqlite3_finalize(readStmt) }
                bindText(readStmt, 1, taskId)
                guard sqlite3_step(readStmt) == SQLITE_ROW else { return false }
                let current = max(0, Int(sqlite3_column_int64(readStmt, 0)))
                let clamped = min(48_000, max(1, current + deltaMinutes))

                let stmt = try prepareLocked("""
                    UPDATE tasks
                    SET actual_minutes = ?,
                        is_in_progress = CASE WHEN status = 'OPEN' THEN 1 ELSE 0 END,
                        updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END,
                        is_dirty = 1
                    WHERE id = ? AND is_deleted = 0;
                """)
                defer { sqlite3_finalize(stmt) }
                bindInt64(stmt, 1, Int64(clamped))
                bindInt64(stmt, 2, now)
                bindInt64(stmt, 3, now)
                bindText(stmt, 4, taskId)
                if sqlite3_step(stmt) != SQLITE_DONE {
                    throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
                }
                return sqlite3_changes(db) > 0
            }
        }
        if didMutate { notifyMutation() }
    }

    @discardableResult
    public func setTaskBlockedBy(
        taskId: String,
        blockedByTaskIds: [String]
    ) throws -> Bool {
        let cleanTaskId = taskId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTaskId.isEmpty else { return false }

        let applied: Bool = try queue.sync {
            var blockedByMap: [String: [String]] = [:]
            var activeIds = Set<String>()
            let mapStmt = try prepareLocked("SELECT id, blocked_by_json FROM tasks WHERE is_deleted = 0;")
            defer { sqlite3_finalize(mapStmt) }
            while sqlite3_step(mapStmt) == SQLITE_ROW {
                if let rowId = colString(mapStmt, 0) {
                    activeIds.insert(rowId)
                    blockedByMap[rowId] = decodeStringArray(colString(mapStmt, 1))
                }
            }
            guard activeIds.contains(cleanTaskId) else { return false }

            var requested: [String] = []
            var seen = Set<String>()
            for rawId in blockedByTaskIds {
                let cid = rawId.trimmingCharacters(in: .whitespacesAndNewlines)
                if !cid.isEmpty, seen.insert(cid).inserted {
                    requested.append(cid)
                }
            }

            blockedByMap[cleanTaskId] = []
            var accepted: [String] = []
            for cid in requested {
                if TaskDependencyGraph.wouldCreateCycle(
                    taskId: cleanTaskId,
                    candidateBlockerId: cid,
                    blockedByMap: blockedByMap
                ) {
                    return false
                }
                accepted.append(cid)
                blockedByMap[cleanTaskId] = accepted
            }

            let now = nowMillis()
            let stmt = try prepareLocked("""
                UPDATE tasks
                SET blocked_by_json = ?,
                    updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END,
                    is_dirty = 1
                WHERE id = ? AND is_deleted = 0;
            """)
            defer { sqlite3_finalize(stmt) }
            bindText(stmt, 1, encodeStringArray(accepted))
            bindInt64(stmt, 2, now)
            bindInt64(stmt, 3, now)
            bindText(stmt, 4, cleanTaskId)
            if sqlite3_step(stmt) != SQLITE_DONE {
                throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
            }
            return true
        }
        if applied { notifyMutation() }
        return applied
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
        var didMutate = false
        let spawnedId: String? = try queue.sync {
            guard var existing = try fetchTaskByIdLocked(taskId) else { return nil }
            let previousStatus = existing.status
            guard previousStatus != status else { return nil }
            let rawTagIds = (try? fetchAllTaskTagIdsLocked()[taskId]) ?? existing.tagIds
            let now = max(Int64(referenceDate.timeIntervalSince1970 * 1000.0), existing.updatedAt + 1)
            existing.status = status
            if status != .open {
                existing.isInProgress = false
            }
            existing.completedAt = (status == .completed || status == .canceled) ? now : nil
            existing.updatedAt = now
            didMutate = true

            return try inTransactionLocked {
                try upsertTaskLocked(existing, tagIds: rawTagIds, isDirty: true)

                if status == .completed, previousStatus == .open, let rule = existing.recurrenceRule {
                    return try spawnRecurringOccurrenceLocked(
                        from: existing,
                        rawTagIds: rawTagIds,
                        rule: rule,
                        nowMillis: now,
                        referenceDate: referenceDate,
                        calendar: calendar
                    )
                } else if status == .open, previousStatus != .open, existing.recurrenceRule != nil {
                    if let seriesKey = Self.buildRecurringSeriesKey(existing, overrideTagIds: rawTagIds) {
                        let (openRecurring, rawTagsByTask) = try fetchOpenRecurringTasksWithRawTagIdsLocked()
                        for candidate in openRecurring where candidate.id != existing.id {
                            if Self.buildRecurringSeriesKey(
                                candidate,
                                overrideTagIds: rawTagsByTask[candidate.id]
                            ) == seriesKey {
                                try purgeTaskToTombstoneLocked(
                                    taskId: candidate.id,
                                    now: max(now, candidate.updatedAt + 1)
                                )
                            }
                        }
                    }
                }
                return nil
            }
        }
        if didMutate { notifyMutation() }
        return spawnedId
    }

    private func spawnRecurringOccurrenceLocked(
        from completedTask: TaskItem,
        rawTagIds: [String],
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

        let dayComps = calendar.dateComponents([.year, .month, .day], from: nextDate)
        let dateKey = String(
            format: "%04d-%02d-%02d",
            dayComps.year ?? 1970,
            dayComps.month ?? 1,
            dayComps.day ?? 1
        )
        let newTaskId = Self.deriveRecurringNextTaskId(
            completedTaskId: completedTask.id,
            nextWhenYyyyMmDd: dateKey
        )
        let existingById = try fetchTaskByIdLocked(newTaskId)
        let nextUpdatedAt = existingById.map { max(nowMillis, $0.updatedAt + 1) } ?? nowMillis
        let maxOrder = try maxSortOrderLocked(table: "tasks")

        let escapedNewId = newTaskId.replacingOccurrences(of: "'", with: "''")
        let priorChecklistsById = Dictionary(
            ((try? fetchChecklistsWhereLocked("task_id = '\(escapedNewId)'")) ?? [])
                .map { ($0.id, $0) },
            uniquingKeysWith: { first, _ in first }
        )
        let clonedChecklists = completedTask.checklistItems.map { chk -> ChecklistItem in
            let nextChkId = Self.deriveRecurringChecklistItemId(
                nextTaskId: newTaskId,
                sourceChecklistId: chk.id
            )
            let existingChk = priorChecklistsById[nextChkId]
            return ChecklistItem(
                id: nextChkId,
                taskId: newTaskId,
                title: chk.title,
                isCompleted: false,
                sortOrder: chk.sortOrder,
                createdAt: existingChk?.createdAt ?? nowMillis,
                updatedAt: existingChk.map { max(nowMillis, $0.updatedAt + 1) } ?? nowMillis
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
            scheduledStartMinutes: completedTask.scheduledStartMinutes,
            durationMinutes: completedTask.durationMinutes,
            completedAt: nil,
            projectId: completedTask.projectId,
            headingId: completedTask.headingId,
            areaId: completedTask.areaId,
            recurrenceRule: rule,
            sortOrder: maxOrder + 1000.0,
            isPinned: false,
            isMilestone: completedTask.isMilestone,
            effortLevel: completedTask.effortLevel,
            blockedByTaskIds: [],
            workspaceId: completedTask.workspaceId,
            assigneeId: completedTask.assigneeId,
            checklistItems: clonedChecklists,
            tags: completedTask.tags,
            createdAt: existingById?.createdAt ?? nowMillis,
            updatedAt: nextUpdatedAt,
            isInProgress: false
        )

        if Self.buildRecurringSeriesKey(spawned, overrideTagIds: rawTagIds) != nil {
            let (openRecurring, rawTagsByTask) = try fetchOpenRecurringTasksWithRawTagIdsLocked()
            let openSiblings = openRecurring
                .filter { $0.id != completedTask.id && Self.areSameRecurringSeries(spawned, $0) }
                .sorted(by: Self.compareRecurringTaskPriority)
            if !openSiblings.isEmpty {
                var primary = openSiblings[0]
                if openSiblings.count > 1 {
                    for extra in openSiblings.dropFirst() {
                        try purgeTaskToTombstoneLocked(
                            taskId: extra.id,
                            now: max(nowMillis, extra.updatedAt + 1)
                        )
                    }
                }
                let primaryTags = rawTagsByTask[primary.id] ?? primary.tagIds
                var mergedTags = primaryTags
                for tId in rawTagIds where !mergedTags.contains(tId) {
                    mergedTags.append(tId)
                }
                let needsAdvance = (primary.whenDate ?? 0) < nextWhenEpoch
                var needsEnrich = mergedTags.count != primaryTags.count
                if (primary.projectId ?? "").isEmpty && (primary.areaId ?? "").isEmpty, let sArea = spawned.areaId, !sArea.isEmpty {
                    primary.areaId = sArea
                    needsEnrich = true
                }
                if !(primary.projectId ?? "").isEmpty && (primary.headingId ?? "").isEmpty, let sHead = spawned.headingId, !sHead.isEmpty {
                    primary.headingId = sHead
                    needsEnrich = true
                }
                if primary.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !spawned.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    primary.notes = spawned.notes
                    needsEnrich = true
                }
                if primary.scheduledStartMinutes == nil, let sStart = spawned.scheduledStartMinutes {
                    primary.scheduledStartMinutes = sStart
                    needsEnrich = true
                }
                if primary.durationMinutes == nil, let sDur = spawned.durationMinutes {
                    primary.durationMinutes = sDur
                    needsEnrich = true
                }
                if primary.effortLevel == nil, let sEffort = spawned.effortLevel {
                    primary.effortLevel = sEffort
                    needsEnrich = true
                }
                if needsAdvance {
                    primary.whenDate = nextWhenEpoch
                    primary.scheduleState = nextSchedule
                    primary.deadline = nextDeadline
                    primary.reminderTime = nextReminder
                    primary.isInProgress = false
                } else {
                    if primary.deadline == nil, let sDead = nextDeadline {
                        primary.deadline = sDead
                        needsEnrich = true
                    }
                    if primary.reminderTime == nil, let sRem = nextReminder {
                        primary.reminderTime = sRem
                        needsEnrich = true
                    }
                }
                if needsAdvance || needsEnrich {
                    primary.updatedAt = max(nowMillis, primary.updatedAt + 1)
                    try upsertTaskLocked(
                        primary,
                        tagIds: mergedTags,
                        isDirty: true
                    )
                }
                let escapedPrimaryId = primary.id.replacingOccurrences(of: "'", with: "''")
                let existingPrimaryChecklists = (try? fetchChecklistsWhereLocked("task_id = '\(escapedPrimaryId)' AND is_deleted = 0 AND is_completed = 1")) ?? []
                for var completedChk in existingPrimaryChecklists {
                    completedChk.isCompleted = false
                    completedChk.updatedAt = max(nowMillis, completedChk.updatedAt + 1)
                    try upsertChecklistItemLocked(completedChk, isDirty: true)
                }
                return primary.id
            }
        }

        try upsertTaskLocked(spawned, tagIds: rawTagIds, isDirty: true)
        for chk in clonedChecklists {
            try upsertChecklistItemLocked(chk, isDirty: true)
        }
        return newTaskId
    }

    public func softDeleteTask(_ taskId: String) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                let escaped = taskId.replacingOccurrences(of: "'", with: "''")
                let atts = (try? fetchAttachmentsWhereLocked("task_id = '\(escaped)' AND is_deleted = 0")) ?? []
                for var att in atts {
                    deleteAttachmentFileLocked(relativePath: att.localRelativePath)
                    att.localRelativePath = ""
                    att.isDeleted = true
                    att.updatedAt = now
                    try upsertAttachmentLocked(att, isDirty: true)
                }

                let chkStmt = try prepareLocked("UPDATE checklist_items SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE task_id = ? AND is_deleted = 0;")
                defer { sqlite3_finalize(chkStmt) }
                bindInt64(chkStmt, 1, now)
                bindText(chkStmt, 2, taskId)
                _ = sqlite3_step(chkStmt)

                let stmt = try prepareLocked("UPDATE tasks SET is_deleted = 1, is_purged = 0, is_in_progress = 0, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(stmt) }
                bindInt64(stmt, 1, now)
                bindText(stmt, 2, taskId)
                _ = sqlite3_step(stmt)
            }
        }
        notifyMutation()
    }

    public func restoreTask(_ taskId: String) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                let stmt = try prepareLocked("UPDATE tasks SET is_deleted = 0, is_purged = 0, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(stmt) }
                bindInt64(stmt, 1, now)
                bindText(stmt, 2, taskId)
                _ = sqlite3_step(stmt)

                let detachProjectStmt = try prepareLocked("""
                    UPDATE tasks
                    SET project_id = NULL, heading_id = NULL
                    WHERE id = ?
                      AND project_id IS NOT NULL
                      AND NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = tasks.project_id AND p.is_deleted = 0);
                """)
                defer { sqlite3_finalize(detachProjectStmt) }
                bindText(detachProjectStmt, 1, taskId)
                _ = sqlite3_step(detachProjectStmt)

                let detachHeadingStmt = try prepareLocked("""
                    UPDATE tasks
                    SET heading_id = NULL
                    WHERE id = ?
                      AND heading_id IS NOT NULL
                      AND NOT EXISTS (SELECT 1 FROM headings h WHERE h.id = tasks.heading_id AND h.is_deleted = 0);
                """)
                defer { sqlite3_finalize(detachHeadingStmt) }
                bindText(detachHeadingStmt, 1, taskId)
                _ = sqlite3_step(detachHeadingStmt)

                let chkStmt = try prepareLocked("UPDATE checklist_items SET is_deleted = 0, updated_at = ?, is_dirty = 1 WHERE task_id = ? AND is_deleted = 1 AND title != '';")
                defer { sqlite3_finalize(chkStmt) }
                bindInt64(chkStmt, 1, now)
                bindText(chkStmt, 2, taskId)
                _ = sqlite3_step(chkStmt)
            }
        }
        notifyMutation()
    }

    public func permanentlyDeleteTask(_ taskId: String) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                try purgeTaskToTombstoneLocked(taskId: taskId, now: now)
            }
        }
        notifyMutation()
    }

    public func permanentlyDeleteProject(_ projectId: String) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                try purgeProjectToTombstoneLocked(projectId: projectId, now: now)
            }
        }
        notifyMutation()
    }

    public func emptyTrash() throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                var taskIds: [String] = []
                let tq = try prepareLocked("SELECT id FROM tasks WHERE is_deleted = 1 AND is_purged = 0;")
                defer { sqlite3_finalize(tq) }
                while sqlite3_step(tq) == SQLITE_ROW {
                    if let tid = colString(tq, 0) { taskIds.append(tid) }
                }
                for tid in taskIds {
                    try purgeTaskToTombstoneLocked(taskId: tid, now: now)
                }

                var projIds: [String] = []
                let pq = try prepareLocked("SELECT id FROM projects WHERE is_deleted = 1 AND is_purged = 0;")
                defer { sqlite3_finalize(pq) }
                while sqlite3_step(pq) == SQLITE_ROW {
                    if let pid = colString(pq, 0) { projIds.append(pid) }
                }
                for pid in projIds {
                    try purgeProjectToTombstoneLocked(projectId: pid, now: now)
                }
            }
        }
        notifyMutation()
    }

    private func purgeTaskToTombstoneLocked(taskId: String, now: Int64) throws {
        let escaped = taskId.replacingOccurrences(of: "'", with: "''")
        let atts = (try? fetchAttachmentsWhereLocked("task_id = '\(escaped)'")) ?? []
        for var att in atts {
            deleteAttachmentFileLocked(relativePath: att.localRelativePath)
            att.localRelativePath = ""
            att.isDeleted = true
            att.updatedAt = now
            try upsertAttachmentLocked(att, isDirty: true)
        }

        let chkStmt = try prepareLocked("UPDATE checklist_items SET title = '', is_deleted = 1, updated_at = MAX(updated_at + 1, ?), is_dirty = 1 WHERE task_id = ?;")
        defer { sqlite3_finalize(chkStmt) }
        bindInt64(chkStmt, 1, now)
        bindText(chkStmt, 2, taskId)
        _ = sqlite3_step(chkStmt)

        let ttStmt = try prepareLocked("DELETE FROM task_tags WHERE task_id = ?;")
        defer { sqlite3_finalize(ttStmt) }
        bindText(ttStmt, 1, taskId)
        _ = sqlite3_step(ttStmt)

        let stmt = try prepareLocked("UPDATE tasks SET is_deleted = 1, is_purged = 1, is_dirty = 1, is_pinned = 0, is_milestone = 0, is_in_progress = 0, effort_level = NULL, blocked_by_json = '[]', workspace_id = NULL, assignee_id = NULL, title = '', notes = '', recurrence_rule = NULL, tag_ids_json = '[]', scheduled_start_minutes = NULL, duration_minutes = NULL, actual_minutes = NULL, updated_at = MAX(updated_at + 1, ?) WHERE id = ?;")
        defer { sqlite3_finalize(stmt) }
        bindInt64(stmt, 1, now)
        bindText(stmt, 2, taskId)
        _ = sqlite3_step(stmt)
    }

    private func purgeProjectToTombstoneLocked(projectId: String, now: Int64) throws {
        let escaped = projectId.replacingOccurrences(of: "'", with: "''")
        let atts = (try? fetchAttachmentsWhereLocked("project_id = '\(escaped)'")) ?? []
        for var att in atts {
            deleteAttachmentFileLocked(relativePath: att.localRelativePath)
            att.localRelativePath = ""
            att.isDeleted = true
            att.updatedAt = now
            try upsertAttachmentLocked(att, isDirty: true)
        }

        var deletedChildTaskIds: [String] = []
        let childTaskStmt = try prepareLocked("SELECT id FROM tasks WHERE project_id = ? AND is_deleted = 1 AND is_purged = 0;")
        defer { sqlite3_finalize(childTaskStmt) }
        bindText(childTaskStmt, 1, projectId)
        while sqlite3_step(childTaskStmt) == SQLITE_ROW {
            if let tid = colString(childTaskStmt, 0) {
                deletedChildTaskIds.append(tid)
            }
        }
        for tid in deletedChildTaskIds {
            try purgeTaskToTombstoneLocked(taskId: tid, now: now)
        }

        let detachStmt = try prepareLocked("UPDATE tasks SET project_id = NULL, heading_id = NULL, updated_at = ?, is_dirty = 1 WHERE project_id = ? AND is_deleted = 0;")
        defer { sqlite3_finalize(detachStmt) }
        bindInt64(detachStmt, 1, now)
        bindText(detachStmt, 2, projectId)
        _ = sqlite3_step(detachStmt)

        let hdgStmt = try prepareLocked("UPDATE headings SET title = '', is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE project_id = ?;")
        defer { sqlite3_finalize(hdgStmt) }
        bindInt64(hdgStmt, 1, now)
        bindText(hdgStmt, 2, projectId)
        _ = sqlite3_step(hdgStmt)

        let ptStmt = try prepareLocked("DELETE FROM project_tags WHERE project_id = ?;")
        defer { sqlite3_finalize(ptStmt) }
        bindText(ptStmt, 1, projectId)
        _ = sqlite3_step(ptStmt)

        let dpStmt = try prepareLocked("DELETE FROM detached_project_tasks WHERE project_id = ?;")
        defer { sqlite3_finalize(dpStmt) }
        bindText(dpStmt, 1, projectId)
        _ = sqlite3_step(dpStmt)

        let stmt = try prepareLocked("UPDATE projects SET is_deleted = 1, is_purged = 1, is_dirty = 1, title = '', notes = '', tag_ids_json = '[]', workspace_id = NULL, view_mode = NULL, updated_at = ? WHERE id = ?;")
        defer { sqlite3_finalize(stmt) }
        bindInt64(stmt, 1, now)
        bindText(stmt, 2, projectId)
        _ = sqlite3_step(stmt)
    }

    private func duplicateSingleTaskLocked(
        source: TaskItem,
        rawTagIds: [String],
        copySuffix: String,
        sortOrderOffset: Double,
        now: Int64
    ) throws -> TaskItem {
        let newId = UUID().uuidString
        let trimmedSuffix = copySuffix.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedTitle = source.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedTitle: String
        if trimmedTitle.isEmpty || trimmedSuffix.isEmpty {
            resolvedTitle = source.title
        } else {
            resolvedTitle = "\(source.title) (\(trimmedSuffix))"
        }
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
            title: resolvedTitle,
            notes: source.notes,
            status: .open,
            scheduleState: source.scheduleState,
            whenDate: source.whenDate,
            deadline: source.deadline,
            reminderTime: source.reminderTime,
            scheduledStartMinutes: source.scheduledStartMinutes,
            durationMinutes: source.durationMinutes,
            completedAt: nil,
            projectId: source.projectId,
            headingId: source.headingId,
            areaId: source.areaId,
            recurrenceRule: source.recurrenceRule,
            sortOrder: source.sortOrder + sortOrderOffset,
            isPinned: source.isPinned,
            isMilestone: source.isMilestone,
            effortLevel: source.effortLevel,
            blockedByTaskIds: source.blockedByTaskIds,
            workspaceId: source.workspaceId,
            assigneeId: source.assigneeId,
            checklistItems: clonedChecklists,
            tags: source.tags,
            createdAt: now,
            updatedAt: now,
            isInProgress: false
        )
        try upsertTaskLocked(copy, tagIds: rawTagIds, isDirty: true)
        for chk in clonedChecklists {
            try upsertChecklistItemLocked(chk, isDirty: true)
        }
        return copy
    }

    @discardableResult
    public func duplicateTask(_ taskId: String, copySuffix: String = "Copy") throws -> TaskItem? {
        let duplicated: TaskItem? = try queue.sync {
            guard let source = try fetchTaskByIdLocked(taskId) else { return nil }
            let rawTagIds = (try? fetchAllTaskTagIdsLocked()[taskId]) ?? source.tagIds
            let now = nowMillis()
            return try inTransactionLocked {
                try duplicateSingleTaskLocked(
                    source: source,
                    rawTagIds: rawTagIds,
                    copySuffix: copySuffix,
                    sortOrderOffset: 0.5,
                    now: now
                )
            }
        }
        if duplicated != nil { notifyMutation() }
        return duplicated
    }

    @discardableResult
    public func duplicateTasks(_ taskIds: [String], copySuffix: String = "Copy") throws -> [TaskItem] {
        var uniqueOrderedIds: [String] = []
        var seen = Set<String>()
        for id in taskIds where seen.insert(id).inserted {
            uniqueOrderedIds.append(id)
        }
        guard !uniqueOrderedIds.isEmpty else { return [] }

        let copies: [TaskItem] = try queue.sync {
            let now = nowMillis()
            let allRawTagIds = (try? fetchAllTaskTagIdsLocked()) ?? [:]
            return try inTransactionLocked {
                var created: [TaskItem] = []
                for (index, tid) in uniqueOrderedIds.enumerated() {
                    guard let source = try fetchTaskByIdLocked(tid), !source.isDeleted else { continue }
                    let rawTagIds = allRawTagIds[tid] ?? source.tagIds
                    let copy = try duplicateSingleTaskLocked(
                        source: source,
                        rawTagIds: rawTagIds,
                        copySuffix: copySuffix,
                        sortOrderOffset: 0.5 + Double(index) * 0.001,
                        now: now + Int64(index)
                    )
                    created.append(copy)
                }
                return created
            }
        }
        if !copies.isEmpty { notifyMutation() }
        return copies
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

    @discardableResult
    public func reorderMultipleTasks(
        _ taskIds: [String],
        prevOrder: Double?,
        nextOrder: Double?,
        newHeadingId: String?? = nil
    ) throws -> [TaskPlacementSnapshot] {
        let orderedIds = TaskDragPayloadCodec.decode(taskIds)
        guard !orderedIds.isEmpty else { return [] }

        let snapshots: [TaskPlacementSnapshot] = try queue.sync {
            let now = nowMillis()
            let allRawTagIds = (try? fetchAllTaskTagIdsLocked()) ?? [:]
            return try inTransactionLocked {
                var validTasks: [(TaskItem, [String])] = []
                for tid in orderedIds {
                    guard let existing = try fetchTaskByIdLocked(tid), !existing.isDeleted else { continue }
                    validTasks.append((existing, allRawTagIds[tid] ?? existing.tagIds))
                }
                guard !validTasks.isEmpty else { return [] }

                let orders = Self.computeSequentialOrders(
                    count: validTasks.count,
                    prevOrder: prevOrder,
                    nextOrder: nextOrder
                )
                var captured: [TaskPlacementSnapshot] = []
                for (idx, (existing, rawTags)) in validTasks.enumerated() {
                    captured.append(TaskPlacementSnapshot(task: existing, tagIds: rawTags, wasDeleted: existing.isDeleted))
                    var updated = existing
                    updated.sortOrder = orders[idx]
                    if let headingOverride = newHeadingId {
                        updated.headingId = headingOverride
                    }
                    updated.updatedAt = max(now + Int64(idx), existing.updatedAt + 1)
                    try upsertTaskLocked(updated, tagIds: rawTags, isDirty: true)
                }
                return captured
            }
        }
        if !snapshots.isEmpty { notifyMutation() }
        return snapshots
    }

    private static func computeSequentialOrders(
        count: Int,
        prevOrder: Double?,
        nextOrder: Double?
    ) -> [Double] {
        guard count > 0 else { return [] }
        switch (prevOrder, nextOrder) {
        case (nil, nil):
            return (0..<count).map { Double($0 + 1) * 1000.0 }
        case (nil, let next?):
            let start = next - Double(count) * 500.0
            return (0..<count).map { start + Double($0) * 500.0 }
        case (let prev?, nil):
            return (0..<count).map { prev + Double($0 + 1) * 1000.0 }
        case (let prev?, let next?):
            if next > prev {
                let step = (next - prev) / Double(count + 1)
                return (0..<count).map { prev + step * Double($0 + 1) }
            }
            return (0..<count).map { prev + Double($0 + 1) * 0.01 }
        }
    }

    private func restoreDeletedTaskChecklistAndValidateContainersLocked(task: inout TaskItem, now: Int64) throws {
        if task.isDeleted {
            task.isDeleted = false
            let chkStmt = try prepareLocked("UPDATE checklist_items SET is_deleted = 0, updated_at = ?, is_dirty = 1 WHERE task_id = ? AND is_deleted = 1 AND title != '';")
            defer { sqlite3_finalize(chkStmt) }
            bindInt64(chkStmt, 1, now)
            bindText(chkStmt, 2, task.id)
            _ = sqlite3_step(chkStmt)
        }
        if let pid = task.projectId {
            let activeProj = try fetchProjectByIdLocked(pid)
            if activeProj == nil || activeProj?.isDeleted == true {
                task.projectId = nil
                task.headingId = nil
            }
        }
        if let hid = task.headingId {
            let safeHid = hid.replacingOccurrences(of: "'", with: "''")
            let hdgExists = !(try fetchHeadingsWhereLocked("id = '\(safeHid)' AND is_deleted = 0")).isEmpty
            if !hdgExists {
                task.headingId = nil
            }
        }
    }

    @discardableResult
    public func moveTasksToDestination(
        _ taskIds: [String],
        destination: NavigationDestination,
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) throws -> (snapshots: [TaskPlacementSnapshot], spawnedRecurringTaskIds: [String]) {
        let orderedIds = TaskDragPayloadCodec.decode(taskIds)
        guard !orderedIds.isEmpty else { return ([], []) }
        if case .perspective = destination {
            return ([], [])
        }

        let result: ([TaskPlacementSnapshot], [String]) = try queue.sync {
            let now = max(nowMillis(), Int64(referenceDate.timeIntervalSince1970 * 1000.0))
            let sodToday = calendar.startOfDay(for: referenceDate)
            let todayStartMillis = DateEpochConverter.startOfDayMillis(for: sodToday, calendar: calendar)
            let tmrDate = calendar.date(byAdding: .day, value: 1, to: sodToday) ?? sodToday.addingTimeInterval(86_400)
            let tomorrowStartMillis = DateEpochConverter.startOfDayMillis(for: tmrDate, calendar: calendar)
            let allRawTagIds = (try? fetchAllTaskTagIdsLocked()) ?? [:]

            return try inTransactionLocked {
                var snapshots: [TaskPlacementSnapshot] = []
                var spawnedIds: [String] = []
                var baseMaxOrder = (try? maxSortOrderLocked(table: "tasks")) ?? 0.0

                for (idx, tid) in orderedIds.enumerated() {
                    guard var existing = try fetchTaskByIdLocked(tid) else { continue }
                    let rawTags = allRawTagIds[tid] ?? existing.tagIds

                    if case .smartView(.trash) = destination {
                        guard !existing.isDeleted else { continue }
                        snapshots.append(TaskPlacementSnapshot(task: existing, tagIds: rawTags, wasDeleted: false))
                        let chkStmt = try prepareLocked("UPDATE checklist_items SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE task_id = ? AND is_deleted = 0;")
                        defer { sqlite3_finalize(chkStmt) }
                        bindInt64(chkStmt, 1, now + Int64(idx))
                        bindText(chkStmt, 2, tid)
                        _ = sqlite3_step(chkStmt)

                        let delStmt = try prepareLocked("UPDATE tasks SET is_deleted = 1, is_purged = 0, is_in_progress = 0, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                        defer { sqlite3_finalize(delStmt) }
                        bindInt64(delStmt, 1, max(now + Int64(idx), existing.updatedAt + 1))
                        bindText(delStmt, 2, tid)
                        _ = sqlite3_step(delStmt)
                        continue
                    }

                    let wasDeleted = existing.isDeleted
                    snapshots.append(TaskPlacementSnapshot(task: existing, tagIds: rawTags, wasDeleted: wasDeleted))

                    let stepNow = max(now + Int64(idx), existing.updatedAt + 1)
                    try restoreDeletedTaskChecklistAndValidateContainersLocked(task: &existing, now: stepNow)

                    switch destination {
                    case .smartView(let sv):
                        switch sv {
                        case .inbox:
                            existing.status = .open
                            existing.completedAt = nil
                            existing.scheduleState = .inbox
                            existing.whenDate = nil
                            existing.scheduledStartMinutes = nil
                            existing.projectId = nil
                            existing.headingId = nil
                            existing.areaId = nil
                            baseMaxOrder += 1000.0
                            existing.sortOrder = baseMaxOrder
                            existing.updatedAt = stepNow
                            try upsertTaskLocked(existing, tagIds: rawTags, isDirty: true)

                        case .today:
                            existing.status = .open
                            existing.completedAt = nil
                            existing.scheduleState = .today
                            existing.whenDate = todayStartMillis
                            existing.updatedAt = stepNow
                            try upsertTaskLocked(existing, tagIds: rawTags, isDirty: true)

                        case .upcoming:
                            existing.status = .open
                            existing.completedAt = nil
                            existing.scheduleState = .upcoming
                            if let currentWhen = existing.whenDate, currentWhen > todayStartMillis {
                                // Preserve existing future whenDate
                            } else {
                                existing.whenDate = tomorrowStartMillis
                            }
                            existing.updatedAt = stepNow
                            try upsertTaskLocked(existing, tagIds: rawTags, isDirty: true)

                        case .anytime:
                            existing.status = .open
                            existing.completedAt = nil
                            existing.scheduleState = .anytime
                            existing.whenDate = nil
                            existing.scheduledStartMinutes = nil
                            existing.updatedAt = stepNow
                            try upsertTaskLocked(existing, tagIds: rawTags, isDirty: true)

                        case .someday:
                            existing.status = .open
                            existing.completedAt = nil
                            existing.scheduleState = .someday
                            existing.whenDate = nil
                            existing.scheduledStartMinutes = nil
                            existing.updatedAt = stepNow
                            try upsertTaskLocked(existing, tagIds: rawTags, isDirty: true)

                        case .logbook:
                            let prevStatus = existing.status
                            existing.status = .completed
                            existing.isInProgress = false
                            existing.completedAt = existing.completedAt ?? stepNow
                            existing.updatedAt = stepNow
                            try upsertTaskLocked(existing, tagIds: rawTags, isDirty: true)
                            if prevStatus == .open, let rule = existing.recurrenceRule {
                                if let spawned = try spawnRecurringOccurrenceLocked(
                                    from: existing,
                                    rawTagIds: rawTags,
                                    rule: rule,
                                    nowMillis: stepNow,
                                    referenceDate: referenceDate,
                                    calendar: calendar
                                ) {
                                    spawnedIds.append(spawned)
                                }
                            }

                        case .trash:
                            break
                        }

                    case .area(let targetAreaId):
                        let activeAreas = try fetchActiveAreasLocked()
                        guard activeAreas.contains(where: { $0.id == targetAreaId }) else { continue }
                        existing.status = .open
                        existing.completedAt = nil
                        existing.areaId = targetAreaId
                        existing.projectId = nil
                        existing.headingId = nil
                        if existing.scheduleState == .inbox {
                            existing.scheduleState = .anytime
                        }
                        baseMaxOrder += 1000.0
                        existing.sortOrder = baseMaxOrder
                        existing.updatedAt = stepNow
                        try upsertTaskLocked(existing, tagIds: rawTags, isDirty: true)

                    case .project(let targetProjectId):
                        guard let targetProject = try fetchProjectByIdLocked(targetProjectId),
                              !targetProject.isDeleted else { continue }
                        existing.status = .open
                        existing.completedAt = nil
                        existing.projectId = targetProjectId
                        existing.headingId = nil
                        existing.areaId = targetProject.areaId
                        if existing.scheduleState == .inbox {
                            existing.scheduleState = .anytime
                        }
                        baseMaxOrder += 1000.0
                        existing.sortOrder = baseMaxOrder
                        existing.updatedAt = stepNow
                        try upsertTaskLocked(existing, tagIds: rawTags, isDirty: true)

                    case .perspective:
                        break
                    }
                }

                return (snapshots, spawnedIds)
            }
        }

        if !result.0.isEmpty { notifyMutation() }
        return result
    }

    @discardableResult
    public func moveTasksIntoProjectSection(
        _ taskIds: [String],
        projectId: String,
        headingId: String?,
        beforeTaskId: String? = nil
    ) throws -> [TaskPlacementSnapshot] {
        let orderedIds = TaskDragPayloadCodec.decode(taskIds)
        guard !orderedIds.isEmpty else { return [] }
        if orderedIds.count == 1, orderedIds[0] == beforeTaskId {
            return []
        }

        let snapshots: [TaskPlacementSnapshot] = try queue.sync {
            let now = nowMillis()
            guard let targetProject = try fetchProjectByIdLocked(projectId), !targetProject.isDeleted else {
                return []
            }
            let resolvedHeadingId: String? = try {
                guard let hid = headingId, !hid.isEmpty else { return nil }
                let safeHid = hid.replacingOccurrences(of: "'", with: "''")
                let found = try fetchHeadingsWhereLocked("id = '\(safeHid)' AND project_id = '\(projectId.replacingOccurrences(of: "'", with: "''"))' AND is_deleted = 0")
                return found.first?.id
            }()

            let allRawTagIds = (try? fetchAllTaskTagIdsLocked()) ?? [:]
            return try inTransactionLocked {
                var movingTasks: [(TaskItem, [String])] = []
                for tid in orderedIds {
                    guard let existing = try fetchTaskByIdLocked(tid) else { continue }
                    movingTasks.append((existing, allRawTagIds[tid] ?? existing.tagIds))
                }
                guard !movingTasks.isEmpty else { return [] }
                let movingIdSet = Set(movingTasks.map { $0.0.id })

                // Load existing open non-deleted tasks in the target section to compute clean sequential sortOrders
                let sectionStmt: OpaquePointer
                if let resolvedHeadingId {
                    sectionStmt = try prepareLocked("""
                        SELECT id, sort_order FROM tasks
                        WHERE project_id = ? AND heading_id = ? AND is_deleted = 0 AND status = 'OPEN'
                        ORDER BY sort_order ASC, created_at ASC, id ASC;
                    """)
                    bindText(sectionStmt, 1, projectId)
                    bindText(sectionStmt, 2, resolvedHeadingId)
                } else {
                    sectionStmt = try prepareLocked("""
                        SELECT id, sort_order FROM tasks
                        WHERE project_id = ? AND heading_id IS NULL AND is_deleted = 0 AND status = 'OPEN'
                        ORDER BY sort_order ASC, created_at ASC, id ASC;
                    """)
                    bindText(sectionStmt, 1, projectId)
                }
                defer { sqlite3_finalize(sectionStmt) }

                var allSectionEntries: [(id: String, order: Double)] = []
                while sqlite3_step(sectionStmt) == SQLITE_ROW {
                    if let rowId = colString(sectionStmt, 0) {
                        allSectionEntries.append((rowId, sqlite3_column_double(sectionStmt, 1)))
                    }
                }
                let remainingEntries = allSectionEntries.filter { !movingIdSet.contains($0.id) }

                let resolvedBeforeOrder: (prev: Double?, next: Double?) = {
                    guard let beforeId = beforeTaskId else {
                        return (remainingEntries.last?.order, nil)
                    }
                    if let remIdx = remainingEntries.firstIndex(where: { $0.id == beforeId }) {
                        let prev = remIdx > 0 ? remainingEntries[remIdx - 1].order : nil
                        let next = remainingEntries[remIdx].order
                        return (prev, next)
                    }
                    if let fullIdx = allSectionEntries.firstIndex(where: { $0.id == beforeId }) {
                        let afterSlice = allSectionEntries[(fullIdx + 1)...]
                        if let nextRemaining = afterSlice.first(where: { !movingIdSet.contains($0.id) }),
                           let remIdx = remainingEntries.firstIndex(where: { $0.id == nextRemaining.id }) {
                            let prev = remIdx > 0 ? remainingEntries[remIdx - 1].order : nil
                            return (prev, remainingEntries[remIdx].order)
                        }
                    }
                    return (remainingEntries.last?.order, nil)
                }()

                let computedOrders = Self.computeSequentialOrders(
                    count: movingTasks.count,
                    prevOrder: resolvedBeforeOrder.prev,
                    nextOrder: resolvedBeforeOrder.next
                )

                var captured: [TaskPlacementSnapshot] = []
                for (idx, (originalTask, rawTags)) in movingTasks.enumerated() {
                    var updated = originalTask
                    let stepNow = max(now + Int64(idx), updated.updatedAt + 1)
                    captured.append(TaskPlacementSnapshot(task: originalTask, tagIds: rawTags, wasDeleted: originalTask.isDeleted))

                    if updated.isDeleted {
                        updated.isDeleted = false
                        let chkStmt = try prepareLocked("UPDATE checklist_items SET is_deleted = 0, updated_at = ?, is_dirty = 1 WHERE task_id = ? AND is_deleted = 1 AND title != '';")
                        defer { sqlite3_finalize(chkStmt) }
                        bindInt64(chkStmt, 1, stepNow)
                        bindText(chkStmt, 2, updated.id)
                        _ = sqlite3_step(chkStmt)
                    }
                    if updated.status != .open {
                        updated.status = .open
                        updated.completedAt = nil
                    }
                    updated.projectId = projectId
                    updated.headingId = resolvedHeadingId
                    updated.areaId = targetProject.areaId
                    if updated.scheduleState == .inbox {
                        updated.scheduleState = .anytime
                    }
                    updated.sortOrder = computedOrders[idx]
                    updated.updatedAt = stepNow
                    try upsertTaskLocked(updated, tagIds: rawTags, isDirty: true)
                }
                return captured
            }
        }

        if !snapshots.isEmpty { notifyMutation() }
        return snapshots
    }

    public func restoreTaskPlacements(
        _ snapshots: [TaskPlacementSnapshot],
        spawnedRecurringTaskIds: [String] = []
    ) throws {
        guard !snapshots.isEmpty || !spawnedRecurringTaskIds.isEmpty else { return }
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                for spawnedId in spawnedRecurringTaskIds {
                    let delStmt = try prepareLocked("UPDATE tasks SET is_deleted = 1, is_purged = 0, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                    defer { sqlite3_finalize(delStmt) }
                    bindInt64(delStmt, 1, now)
                    bindText(delStmt, 2, spawnedId)
                    _ = sqlite3_step(delStmt)
                }

                for (idx, snap) in snapshots.enumerated() {
                    let current = try fetchTaskByIdLocked(snap.task.id)
                    let stepNow = max(now + Int64(idx), (current?.updatedAt ?? snap.task.updatedAt) + 1)
                    var restored = snap.task
                    restored.isDeleted = snap.wasDeleted
                    restored.updatedAt = stepNow
                    try upsertTaskLocked(restored, tagIds: snap.tagIds, isDirty: true)

                    if snap.wasDeleted {
                        let chkStmt = try prepareLocked("UPDATE checklist_items SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE task_id = ? AND is_deleted = 0;")
                        defer { sqlite3_finalize(chkStmt) }
                        bindInt64(chkStmt, 1, stepNow)
                        bindText(chkStmt, 2, restored.id)
                        _ = sqlite3_step(chkStmt)
                    } else {
                        let chkStmt = try prepareLocked("UPDATE checklist_items SET is_deleted = 0, updated_at = ?, is_dirty = 1 WHERE task_id = ? AND is_deleted = 1 AND title != '';")
                        defer { sqlite3_finalize(chkStmt) }
                        bindInt64(chkStmt, 1, stepNow)
                        bindText(chkStmt, 2, restored.id)
                        _ = sqlite3_step(chkStmt)
                    }
                }
            }
        }
        notifyMutation()
    }

    @discardableResult
    public func applyCascadedTaskShifts(
        _ shifts: [CascadedTaskShift]
    ) throws -> [TaskPlacementSnapshot] {
        guard !shifts.isEmpty else { return [] }
        let snapshots = try queue.sync { () -> [TaskPlacementSnapshot] in
            let now = nowMillis()
            return try inTransactionLocked {
                let tagIdsByTask = try fetchAllTaskTagIdsLocked()
                var captured: [TaskPlacementSnapshot] = []
                for (idx, shift) in shifts.enumerated() {
                    guard let existing = try fetchTaskByIdLocked(shift.task.id) else { continue }
                    let rawTags = tagIdsByTask[existing.id] ?? existing.tagIds
                    captured.append(
                        TaskPlacementSnapshot(
                            task: existing,
                            tagIds: rawTags,
                            wasDeleted: existing.isDeleted
                        )
                    )
                    var updated = existing
                    updated.scheduleState = shift.nextScheduleState
                    updated.whenDate = shift.nextWhenMillis
                    updated.deadline = shift.nextDeadlineMillis
                    updated.updatedAt = max(now + Int64(idx), existing.updatedAt + 1)
                    try upsertTaskLocked(updated, tagIds: rawTags, isDirty: true)
                }
                return captured
            }
        }
        if !snapshots.isEmpty { notifyMutation() }
        return snapshots
    }

    @discardableResult
    public func applyBatchTaskTimeblocks(
        _ updates: [(taskId: String, startMinutes: Int?, durationMinutes: Int?)],
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) throws -> [TaskPlacementSnapshot] {
        guard !updates.isEmpty else { return [] }
        let todayEpoch = DateEpochConverter.startOfDayMillis(for: referenceDate, calendar: calendar)
        let snapshots = try queue.sync { () -> [TaskPlacementSnapshot] in
            let now = nowMillis()
            return try inTransactionLocked {
                let tagIdsByTask = try fetchAllTaskTagIdsLocked()
                var captured: [TaskPlacementSnapshot] = []
                for (idx, item) in updates.enumerated() {
                    guard let existing = try fetchTaskByIdLocked(item.taskId), !existing.isDeleted else { continue }
                    let rawTags = tagIdsByTask[existing.id] ?? existing.tagIds
                    captured.append(
                        TaskPlacementSnapshot(
                            task: existing,
                            tagIds: rawTags,
                            wasDeleted: existing.isDeleted
                        )
                    )
                    let cleanStart = item.startMinutes.map { min(1439, max(0, $0)) }
                    let cleanDur = item.durationMinutes.flatMap { $0 > 0 ? min(48_000, max(5, $0)) : nil }
                    var updated = existing
                    if cleanStart != nil && (existing.scheduleState == .inbox || existing.scheduleState == .anytime || existing.scheduleState == .someday) {
                        updated.scheduleState = .today
                    }
                    if cleanStart != nil && existing.whenDate == nil {
                        updated.whenDate = todayEpoch
                    }
                    updated.scheduledStartMinutes = cleanStart
                    updated.durationMinutes = cleanDur
                    updated.updatedAt = max(now + Int64(idx), existing.updatedAt + 1)
                    try upsertTaskLocked(updated, tagIds: rawTags, isDirty: true)
                }
                return captured
            }
        }
        if !snapshots.isEmpty { notifyMutation() }
        return snapshots
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
                if copy.isCompleted && !copy.isDeleted {
                    let ipStmt = try prepareLocked("UPDATE tasks SET is_in_progress = 1, updated_at = ?, is_dirty = 1 WHERE id = ? AND is_deleted = 0 AND status = 'OPEN' AND is_in_progress = 0;")
                    defer { sqlite3_finalize(ipStmt) }
                    bindInt64(ipStmt, 1, now)
                    bindText(ipStmt, 2, copy.taskId)
                    _ = sqlite3_step(ipStmt)
                }
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

    private func touchProjectUpdatedLocked(projectId: String, now: Int64) throws {
        let stmt = try prepareLocked("UPDATE projects SET updated_at = ?, is_dirty = 1 WHERE id = ?;")
        defer { sqlite3_finalize(stmt) }
        bindInt64(stmt, 1, now)
        bindText(stmt, 2, projectId)
        _ = sqlite3_step(stmt)
    }

    // MARK: - Attachment Mutations & File Storage (v1.8)

    public func resolveAttachmentURL(_ attachment: TaskAttachment) -> URL? {
        guard !attachment.localRelativePath.isEmpty else { return nil }
        let url = baseSupportDirURL.appendingPathComponent(attachment.localRelativePath)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        return url
    }

    public func readAttachmentBase64(_ attachment: TaskAttachment, maxRawBytes: Int? = nil) -> String {
        queue.sync {
            readAttachmentBase64Locked(attachment, maxRawBytes: maxRawBytes)
        }
    }

    private func readAttachmentBase64Locked(_ attachment: TaskAttachment, maxRawBytes: Int?) -> String {
        guard !attachment.localRelativePath.isEmpty else { return "" }
        let url = baseSupportDirURL.appendingPathComponent(attachment.localRelativePath)
        guard let data = try? Data(contentsOf: url) else { return "" }
        if let maxRawBytes, data.count > maxRawBytes {
            return ""
        }
        return data.base64EncodedString()
    }

    private func writeAttachmentDataLocked(id: String, fileName: String, data: Data) throws -> String {
        let attachmentsDir = baseSupportDirURL.appendingPathComponent("attachments", isDirectory: true)
        try FileManager.default.createDirectory(at: attachmentsDir, withIntermediateDirectories: true)
        let sanitized = fileName
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "\\", with: "_")
            .replacingOccurrences(of: ":", with: "_")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let safeLeaf = sanitized.isEmpty ? "attachment" : String(sanitized.suffix(96))
        let diskLeaf = "\(id)_\(safeLeaf)"
        let destURL = attachmentsDir.appendingPathComponent(diskLeaf)
        try data.write(to: destURL, options: .atomic)
        return "attachments/\(diskLeaf)"
    }

    private func deleteAttachmentFileLocked(relativePath: String) {
        guard !relativePath.isEmpty else { return }
        let url = baseSupportDirURL.appendingPathComponent(relativePath)
        try? FileManager.default.removeItem(at: url)
    }

    @discardableResult
    public func addAttachment(
        id: String = UUID().uuidString,
        taskId: String? = nil,
        projectId: String? = nil,
        fileName: String,
        mimeType: String,
        data: Data
    ) throws -> TaskAttachment {
        let created = try queue.sync { () -> TaskAttachment in
            let now = nowMillis()
            return try inTransactionLocked {
                let relPath = try writeAttachmentDataLocked(id: id, fileName: fileName, data: data)
                let att = TaskAttachment(
                    id: id,
                    taskId: taskId,
                    projectId: projectId,
                    fileName: fileName,
                    mimeType: mimeType,
                    sizeBytes: Int64(data.count),
                    localRelativePath: relPath,
                    createdAt: now,
                    updatedAt: now,
                    isDeleted: false
                )
                try upsertAttachmentLocked(att, isDirty: true)
                if let taskId {
                    try touchTaskUpdatedLocked(taskId: taskId, now: now)
                } else if let projectId {
                    try touchProjectUpdatedLocked(projectId: projectId, now: now)
                }
                return att
            }
        }
        notifyMutation()
        return created
    }

    public func deleteAttachment(_ attachmentId: String) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                let escaped = attachmentId.replacingOccurrences(of: "'", with: "''")
                guard var existing = try fetchAttachmentsWhereLocked("id = '\(escaped)'").first else { return }
                deleteAttachmentFileLocked(relativePath: existing.localRelativePath)
                existing.localRelativePath = ""
                existing.isDeleted = true
                existing.updatedAt = now
                try upsertAttachmentLocked(existing, isDirty: true)
                if let tid = existing.taskId {
                    try touchTaskUpdatedLocked(taskId: tid, now: now)
                } else if let pid = existing.projectId {
                    try touchProjectUpdatedLocked(projectId: pid, now: now)
                }
            }
        }
        notifyMutation()
    }

    // MARK: - Areas, Projects, Headings, Tags

    @discardableResult
    public func createArea(title: String, tagIds: [String] = []) throws -> Area {
        let area = try queue.sync { () -> Area in
            let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
            let activeAreas = try fetchActiveAreasLocked()
            if var existing = activeAreas.first(where: { $0.title.caseInsensitiveCompare(cleanTitle) == .orderedSame }) {
                let mergedTags = Array(Set(existing.tagIds + tagIds))
                if mergedTags.count != existing.tagIds.count {
                    existing.tagIds = mergedTags
                    existing.updatedAt = nowMillis()
                    try upsertAreaLocked(existing, isDirty: true)
                }
                return existing
            }
            if activeAreas.count >= Self.maxAreas {
                throw ThingsDatabaseError.maxAreasReached(Self.maxAreas)
            }
            let now = nowMillis()
            let maxOrder = activeAreas.map(\.sortOrder).max() ?? 0.0
            let newArea = Area(
                id: UUID().uuidString,
                title: cleanTitle,
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

                let sTmpl = try prepareLocked("UPDATE project_templates SET default_area_id = NULL, updated_at = ?, is_dirty = 1 WHERE default_area_id = ?;")
                defer { sqlite3_finalize(sTmpl) }
                bindInt64(sTmpl, 1, now)
                bindText(sTmpl, 2, areaId)
                _ = sqlite3_step(sTmpl)

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
                lastReviewedAt: now,
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

    @discardableResult
    public func createProjectFromBlueprint(
        _ blueprint: ProjectBlueprintResponse,
        areaId: String? = nil,
        languageCode: String = "en",
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) throws -> Project {
        let defaultProjTitle: String = {
            switch languageCode {
            case "de": return "Neues Projekt"
            case "fr": return "Nouveau projet"
            case "it": return "Nuovo progetto"
            case "es": return "Nuevo proyecto"
            default:   return "New Project"
            }
        }()
        let sectionPrefix: String = {
            switch languageCode {
            case "de": return "Abschnitt"
            case "fr": return "Section"
            case "it": return "Sezione"
            case "es": return "Sección"
            default:   return "Section"
            }
        }()

        let trimmedTitle = blueprint.projectTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedTitle = trimmedTitle.isEmpty ? defaultProjTitle : trimmedTitle
        let sodToday = calendar.startOfDay(for: referenceDate)
        let todayEpoch = DateEpochConverter.startOfDayMillis(for: sodToday, calendar: calendar)

        func dayOffsetMillis(_ offsetDays: Int) -> Int64 {
            let clamped = min(365, max(0, offsetDays))
            let targetDate = calendar.date(byAdding: .day, value: clamped, to: sodToday)
                ?? sodToday.addingTimeInterval(Double(clamped) * 86_400.0)
            return DateEpochConverter.startOfDayMillis(for: targetDate, calendar: calendar)
        }

        let maxEndOffset: Int = blueprint.headings.compactMap { h in
            h.endDayOffset ?? h.richTasks.map(\.dayOffset).max()
        }.max() ?? 10

        let createdProject = try queue.sync { () -> Project in
            let now = nowMillis()
            let maxOrd = try maxSortOrderLocked(table: "projects")
            let project = Project(
                id: UUID().uuidString,
                areaId: areaId,
                title: resolvedTitle,
                notes: blueprint.projectNotes,
                status: .active,
                scheduleState: .today,
                whenDate: todayEpoch,
                deadline: dayOffsetMillis(max(1, maxEndOffset)),
                lastReviewedAt: now,
                sortOrder: maxOrd + 1000.0,
                tagIds: [],
                viewMode: "roadmap",
                createdAt: now,
                updatedAt: now
            )

            try inTransactionLocked {
                try upsertProjectLocked(project, isDirty: true)

                var taskIdByKey: [String: String] = [:]
                struct PendingBlueprintTask {
                    let id: String
                    let headingId: String
                    let hIndex: Int
                    let tIndex: Int
                    let dto: RoadmapTaskBlueprintDTO
                }
                var pendingTasks: [PendingBlueprintTask] = []

                for (hIndex, headingDto) in blueprint.headings.enumerated() {
                    let headingId = UUID().uuidString
                    let nonBlankRich = headingDto.richTasks.filter {
                        !$0.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    }
                    let richList: [RoadmapTaskBlueprintDTO]
                    if !nonBlankRich.isEmpty {
                        richList = nonBlankRich
                    } else {
                        let cleanPlain = headingDto.tasks
                            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                            .filter { !$0.isEmpty }
                        richList = cleanPlain.enumerated().map { tIdx, tTitle in
                            RoadmapTaskBlueprintDTO(
                                key: "h\(hIndex)_t\(tIdx)",
                                title: tTitle,
                                dayOffset: hIndex * 4 + tIdx,
                                durationMinutes: 120,
                                effortLevel: "MEDIUM",
                                isMilestone: (hIndex == blueprint.headings.count - 1 && tIdx == cleanPlain.count - 1)
                            )
                        }
                    }

                    let startOff = headingDto.startDayOffset ?? richList.map(\.dayOffset).min() ?? (hIndex * 4)
                    let endOff = max(startOff, headingDto.endDayOffset ?? richList.map(\.dayOffset).max() ?? (startOff + 3))
                    let cleanHeadingTitle = headingDto.title.trimmingCharacters(in: .whitespacesAndNewlines)
                    let heading = Heading(
                        id: headingId,
                        projectId: project.id,
                        title: cleanHeadingTitle.isEmpty ? "\(sectionPrefix) \(hIndex + 1)" : cleanHeadingTitle,
                        sortOrder: Double(hIndex + 1) * 1000.0,
                        isArchived: false,
                        startDate: dayOffsetMillis(startOff),
                        endDate: dayOffsetMillis(endOff),
                        createdAt: now,
                        updatedAt: now
                    )
                    try upsertHeadingLocked(heading, isDirty: true)

                    for (tIndex, rTask) in richList.enumerated() {
                        let newTaskId = UUID().uuidString
                        let cleanKey = rTask.key.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !cleanKey.isEmpty {
                            taskIdByKey[cleanKey] = newTaskId
                        }
                        pendingTasks.append(
                            PendingBlueprintTask(
                                id: newTaskId,
                                headingId: headingId,
                                hIndex: hIndex,
                                tIndex: tIndex,
                                dto: rTask
                            )
                        )
                    }
                }

                for pt in pendingTasks {
                    let r = pt.dto
                    let safeOffset = min(365, max(0, r.dayOffset))
                    let itemMillis = dayOffsetMillis(safeOffset)
                    let schedState: ScheduleState = (safeOffset == 0) ? .today : .upcoming
                    var seenBlockers = Set<String>()
                    var resolvedBlockers: [String] = []
                    for depKey in r.dependsOnKeys {
                        let trimmedKey = depKey.trimmingCharacters(in: .whitespacesAndNewlines)
                        if let bid = taskIdByKey[trimmedKey], bid != pt.id, seenBlockers.insert(bid).inserted {
                            resolvedBlockers.append(bid)
                        }
                    }
                    let normEffort = TaskEffortLevel.normalized(r.effortLevel)?.rawValue ?? "MEDIUM"
                    let safeDur = min(48_000, max(15, r.durationMinutes))

                    let taskItem = TaskItem(
                        id: pt.id,
                        title: r.title.trimmingCharacters(in: .whitespacesAndNewlines),
                        notes: r.notes.trimmingCharacters(in: .whitespacesAndNewlines),
                        status: .open,
                        scheduleState: schedState,
                        whenDate: itemMillis,
                        deadline: r.isMilestone ? itemMillis : nil,
                        durationMinutes: safeDur,
                        projectId: project.id,
                        headingId: pt.headingId,
                        areaId: areaId,
                        sortOrder: Double(pt.hIndex + 1) * 1000.0 + Double(pt.tIndex + 1) * 100.0,
                        isPinned: false,
                        isMilestone: r.isMilestone,
                        effortLevel: normEffort,
                        blockedByTaskIds: resolvedBlockers,
                        createdAt: now,
                        updatedAt: now
                    )
                    try upsertTaskLocked(taskItem, tagIds: [], isDirty: true)

                    let cleanChecks = r.checklist
                        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                        .filter { !$0.isEmpty }
                    for (cIdx, cTitle) in cleanChecks.enumerated() {
                        let chk = ChecklistItem(
                            id: UUID().uuidString,
                            taskId: pt.id,
                            title: cTitle,
                            isCompleted: false,
                            sortOrder: Double(cIdx + 1) * 100.0,
                            createdAt: now,
                            updatedAt: now
                        )
                        try upsertChecklistItemLocked(chk, isDirty: true)
                    }
                }
            }
            return project
        }
        notifyMutation()
        return createdProject
    }

    public func updateProject(_ project: Project) throws {
        try queue.sync {
            var copy = project
            let now = nowMillis()
            copy.updatedAt = max(copy.updatedAt + 1, now)
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

    public func setProjectViewMode(projectId: String, viewMode: String?) throws {
        var didMutate = false
        try queue.sync {
            guard var p = try fetchProjectByIdLocked(projectId), !p.isDeleted else { return }
            let normalized: String?
            if let raw = viewMode?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty {
                normalized = ProjectViewMode.normalized(raw).rawValue
            } else {
                normalized = nil
            }
            guard p.viewMode != normalized else { return }
            let now = nowMillis()
            p.viewMode = normalized
            p.updatedAt = max(now, p.updatedAt + 1)
            try inTransactionLocked {
                try upsertProjectLocked(p, isDirty: true)
            }
            didMutate = true
        }
        if didMutate { notifyMutation() }
    }

    public func countNonDeletedTasksInProject(_ projectId: String) throws -> Int {
        try queue.sync {
            let stmt = try prepareLocked("SELECT COUNT(*) FROM tasks WHERE project_id = ? AND is_deleted = 0;")
            defer { sqlite3_finalize(stmt) }
            bindText(stmt, 1, projectId)
            if sqlite3_step(stmt) == SQLITE_ROW {
                return Int(sqlite3_column_int(stmt, 0))
            }
            return 0
        }
    }

    public func countOpenTasksInProject(_ projectId: String) throws -> Int {
        try queue.sync {
            let stmt = try prepareLocked("SELECT COUNT(*) FROM tasks WHERE project_id = ? AND is_deleted = 0 AND status = 'OPEN';")
            defer { sqlite3_finalize(stmt) }
            bindText(stmt, 1, projectId)
            if sqlite3_step(stmt) == SQLITE_ROW {
                return Int(sqlite3_column_int(stmt, 0))
            }
            return 0
        }
    }

    @discardableResult
    public func completeProjectWithUndo(projectId: String, completeOpenTasks: Bool) throws -> [String] {
        var didMutate = false
        let completedTaskIds: [String] = try queue.sync {
            guard var proj = try fetchProjectByIdLocked(projectId), !proj.isDeleted else { return [] }
            let now = nowMillis()
            let rawTagsByTask = (try? fetchAllTaskTagIdsLocked()) ?? [:]
            return try inTransactionLocked {
                var closedIds: [String] = []
                if completeOpenTasks {
                    var openIds: [String] = []
                    let q = try prepareLocked("SELECT id FROM tasks WHERE project_id = ? AND is_deleted = 0 AND status = 'OPEN';")
                    defer { sqlite3_finalize(q) }
                    bindText(q, 1, projectId)
                    while sqlite3_step(q) == SQLITE_ROW {
                        if let tid = colString(q, 0) {
                            openIds.append(tid)
                        }
                    }
                    for tid in openIds {
                        if var tk = try fetchTaskByIdLocked(tid) {
                            let tags = rawTagsByTask[tid] ?? tk.tagIds
                            let stamp = max(now, tk.updatedAt + 1)
                            tk.status = .completed
                            tk.isInProgress = false
                            tk.completedAt = stamp
                            tk.updatedAt = stamp
                            try upsertTaskLocked(tk, tagIds: tags, isDirty: true)
                            closedIds.append(tid)
                        }
                    }
                }
                let pStamp = max(now, proj.updatedAt + 1)
                proj.status = .completed
                proj.completedAt = pStamp
                proj.updatedAt = pStamp
                try upsertProjectLocked(proj, isDirty: true)
                didMutate = true
                return closedIds
            }
        }
        if didMutate { notifyMutation() }
        return completedTaskIds
    }

    public func restoreCompletedProject(projectId: String, reopenedTaskIds: [String]) throws {
        var didMutate = false
        try queue.sync {
            guard var proj = try fetchProjectByIdLocked(projectId) else { return }
            let now = nowMillis()
            let rawTagsByTask = (try? fetchAllTaskTagIdsLocked()) ?? [:]
            try inTransactionLocked {
                let pStamp = max(now, proj.updatedAt + 1)
                proj.status = .active
                proj.completedAt = nil
                proj.updatedAt = pStamp
                try upsertProjectLocked(proj, isDirty: true)
                for tid in reopenedTaskIds {
                    if var tk = try fetchTaskByIdLocked(tid) {
                        let tags = rawTagsByTask[tid] ?? tk.tagIds
                        let stamp = max(now, tk.updatedAt + 1)
                        tk.status = .open
                        tk.completedAt = nil
                        tk.updatedAt = stamp
                        try upsertTaskLocked(tk, tagIds: tags, isDirty: true)
                    }
                }
                didMutate = true
            }
        }
        if didMutate { notifyMutation() }
    }

    /// Soft-deletes project, cascades to headings/attachments, and either cascade-deletes or detaches its tasks; returns affected task map for Undo.
    @discardableResult
    public func softDeleteProject(_ projectId: String, deleteAssociatedTasks: Bool = false) throws -> [String: DetachedTaskPlacement] {
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

                let clearDetachedStmt = try prepareLocked("DELETE FROM detached_project_tasks WHERE project_id = ?;")
                defer { sqlite3_finalize(clearDetachedStmt) }
                bindText(clearDetachedStmt, 1, projectId)
                _ = sqlite3_step(clearDetachedStmt)

                if deleteAssociatedTasks {
                    for tid in previousMap.keys {
                        let escapedTid = tid.replacingOccurrences(of: "'", with: "''")
                        let taskAtts = (try? fetchAttachmentsWhereLocked("task_id = '\(escapedTid)' AND is_deleted = 0")) ?? []
                        for var att in taskAtts {
                            deleteAttachmentFileLocked(relativePath: att.localRelativePath)
                            att.localRelativePath = ""
                            att.isDeleted = true
                            att.updatedAt = now
                            try upsertAttachmentLocked(att, isDirty: true)
                        }
                    }

                    let delTasksStmt = try prepareLocked("UPDATE tasks SET is_deleted = 1, is_purged = 0, is_in_progress = 0, updated_at = ?, is_dirty = 1 WHERE project_id = ? AND is_deleted = 0;")
                    defer { sqlite3_finalize(delTasksStmt) }
                    bindInt64(delTasksStmt, 1, now)
                    bindText(delTasksStmt, 2, projectId)
                    _ = sqlite3_step(delTasksStmt)
                } else {
                    for (tid, placement) in previousMap {
                        let saveStmt = try prepareLocked("""
                            INSERT OR REPLACE INTO detached_project_tasks (project_id, task_id, heading_id)
                            VALUES (?, ?, ?);
                        """)
                        defer { sqlite3_finalize(saveStmt) }
                        bindText(saveStmt, 1, projectId)
                        bindText(saveStmt, 2, tid)
                        bindText(saveStmt, 3, placement.headingId)
                        _ = sqlite3_step(saveStmt)
                    }

                    let detachStmt = try prepareLocked("UPDATE tasks SET project_id = NULL, heading_id = NULL, updated_at = ?, is_dirty = 1 WHERE project_id = ? AND is_deleted = 0;")
                    defer { sqlite3_finalize(detachStmt) }
                    bindInt64(detachStmt, 1, now)
                    bindText(detachStmt, 2, projectId)
                    _ = sqlite3_step(detachStmt)
                }

                let hdgStmt = try prepareLocked("UPDATE headings SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE project_id = ? AND is_deleted = 0;")
                defer { sqlite3_finalize(hdgStmt) }
                bindInt64(hdgStmt, 1, now)
                bindText(hdgStmt, 2, projectId)
                _ = sqlite3_step(hdgStmt)

                let escaped = projectId.replacingOccurrences(of: "'", with: "''")
                let atts = (try? fetchAttachmentsWhereLocked("project_id = '\(escaped)' AND is_deleted = 0")) ?? []
                for var att in atts {
                    deleteAttachmentFileLocked(relativePath: att.localRelativePath)
                    att.localRelativePath = ""
                    att.isDeleted = true
                    att.updatedAt = now
                    try upsertAttachmentLocked(att, isDirty: true)
                }

                let delStmt = try prepareLocked("UPDATE projects SET is_deleted = 1, is_purged = 0, updated_at = ?, is_dirty = 1 WHERE id = ?;")
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

    public func restoreProject(_ projectId: String) throws {
        try restoreDeletedProject(
            projectId: projectId,
            previousTasks: [:],
            restoredDeletedTaskIds: [],
            restoreAllProjectDeletedTasks: true
        )
    }

    public func restoreDeletedProject(
        projectId: String,
        previousTasks: [String: DetachedTaskPlacement] = [:],
        restoredDeletedTaskIds: [String] = [],
        restoreAllProjectDeletedTasks: Bool = false
    ) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                let s1 = try prepareLocked("UPDATE projects SET is_deleted = 0, is_purged = 0, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(s1) }
                bindInt64(s1, 1, now)
                bindText(s1, 2, projectId)
                _ = sqlite3_step(s1)

                let hdgStmt = try prepareLocked("UPDATE headings SET is_deleted = 0, updated_at = ?, is_dirty = 1 WHERE project_id = ? AND is_deleted = 1 AND title != '';")
                defer { sqlite3_finalize(hdgStmt) }
                bindInt64(hdgStmt, 1, now)
                bindText(hdgStmt, 2, projectId)
                _ = sqlite3_step(hdgStmt)

                if !restoredDeletedTaskIds.isEmpty {
                    for tid in restoredDeletedTaskIds {
                        let rtStmt = try prepareLocked("UPDATE tasks SET is_deleted = 0, is_purged = 0, updated_at = ?, is_dirty = 1 WHERE id = ? AND is_purged = 0;")
                        defer { sqlite3_finalize(rtStmt) }
                        bindInt64(rtStmt, 1, now)
                        bindText(rtStmt, 2, tid)
                        _ = sqlite3_step(rtStmt)

                        let chkStmt = try prepareLocked("UPDATE checklist_items SET is_deleted = 0, updated_at = ?, is_dirty = 1 WHERE task_id = ? AND is_deleted = 1 AND title != '';")
                        defer { sqlite3_finalize(chkStmt) }
                        bindInt64(chkStmt, 1, now)
                        bindText(chkStmt, 2, tid)
                        _ = sqlite3_step(chkStmt)
                    }
                } else if restoreAllProjectDeletedTasks {
                    let rtAllStmt = try prepareLocked("UPDATE tasks SET is_deleted = 0, is_purged = 0, updated_at = ?, is_dirty = 1 WHERE project_id = ? AND is_deleted = 1 AND is_purged = 0;")
                    defer { sqlite3_finalize(rtAllStmt) }
                    bindInt64(rtAllStmt, 1, now)
                    bindText(rtAllStmt, 2, projectId)
                    _ = sqlite3_step(rtAllStmt)

                    let chkAllStmt = try prepareLocked("""
                        UPDATE checklist_items
                        SET is_deleted = 0, updated_at = ?, is_dirty = 1
                        WHERE is_deleted = 1 AND title != ''
                          AND task_id IN (SELECT id FROM tasks WHERE project_id = ? AND is_deleted = 0);
                    """)
                    defer { sqlite3_finalize(chkAllStmt) }
                    bindInt64(chkAllStmt, 1, now)
                    bindText(chkAllStmt, 2, projectId)
                    _ = sqlite3_step(chkAllStmt)
                }

                var mergedPlacements: [String: DetachedTaskPlacement] = [:]
                let q = try prepareLocked("SELECT task_id, heading_id FROM detached_project_tasks WHERE project_id = ?;")
                defer { sqlite3_finalize(q) }
                bindText(q, 1, projectId)
                while sqlite3_step(q) == SQLITE_ROW {
                    if let tid = colString(q, 0) {
                        mergedPlacements[tid] = DetachedTaskPlacement(projectId: projectId, headingId: colString(q, 1))
                    }
                }
                for (tid, refs) in previousTasks {
                    mergedPlacements[tid] = refs
                }

                for (tid, refs) in mergedPlacements {
                    let s2 = try prepareLocked("UPDATE tasks SET project_id = ?, heading_id = ?, updated_at = ?, is_dirty = 1 WHERE id = ? AND is_deleted = 0 AND project_id IS NULL;")
                    defer { sqlite3_finalize(s2) }
                    bindText(s2, 1, refs.projectId ?? projectId)
                    bindText(s2, 2, refs.headingId)
                    bindInt64(s2, 3, now)
                    bindText(s2, 4, tid)
                    _ = sqlite3_step(s2)
                }

                let clearDetachedStmt = try prepareLocked("DELETE FROM detached_project_tasks WHERE project_id = ?;")
                defer { sqlite3_finalize(clearDetachedStmt) }
                bindText(clearDetachedStmt, 1, projectId)
                _ = sqlite3_step(clearDetachedStmt)
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
                    workspaceId: source.workspaceId,
                    viewMode: source.viewMode,
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
                        startDate: h.startDate,
                        endDate: h.endDate,
                        createdAt: now,
                        updatedAt: now
                    )
                    try upsertHeadingLocked(clonedHeading, isDirty: true)
                }

                let tags = try fetchActiveTagsLocked()
                let tagsById = Dictionary(tags.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
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

                var taskIdMap: [String: String] = [:]
                for t in sourceTasks {
                    taskIdMap[t.id] = UUID().uuidString
                }

                for t in sourceTasks {
                    let newTid = taskIdMap[t.id] ?? UUID().uuidString
                    let mappedHid = t.headingId.flatMap { headingIdMap[$0] }
                    let remappedBlockers = t.blockedByTaskIds.map { taskIdMap[$0] ?? $0 }
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
                        scheduledStartMinutes: t.scheduledStartMinutes,
                        durationMinutes: t.durationMinutes,
                        projectId: newProjId,
                        headingId: mappedHid,
                        areaId: newProject.areaId,
                        recurrenceRule: t.recurrenceRule,
                        sortOrder: t.sortOrder,
                        isPinned: t.isPinned,
                        isMilestone: t.isMilestone,
                        effortLevel: t.effortLevel,
                        blockedByTaskIds: remappedBlockers,
                        workspaceId: t.workspaceId ?? newProject.workspaceId,
                        assigneeId: t.assigneeId,
                        checklistItems: clonedChecklists,
                        tags: t.tags,
                        createdAt: now,
                        updatedAt: now,
                        isInProgress: false
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

    // MARK: - v2.0 Project Review, Templates & Outline Importer

    public func markProjectReviewed(projectId: String, reviewedAt: Int64? = nil) throws {
        try queue.sync {
            guard var p = try fetchProjectByIdLocked(projectId) else { return }
            let now = nowMillis()
            let nextReview = reviewedAt ?? max(p.lastReviewedAt ?? 0, now)
            p.lastReviewedAt = nextReview
            p.updatedAt = max(p.updatedAt + 1, now, nextReview)
            try inTransactionLocked {
                try upsertProjectLocked(p, isDirty: true)
            }
        }
        notifyMutation()
    }

    public func markMultipleProjectsReviewed(_ projectIds: [String], reviewedAt: Int64? = nil) throws {
        guard !projectIds.isEmpty else { return }
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                for pid in Set(projectIds) {
                    guard var p = try fetchProjectByIdLocked(pid) else { continue }
                    let nextReview = reviewedAt ?? max(p.lastReviewedAt ?? 0, now)
                    p.lastReviewedAt = nextReview
                    p.updatedAt = max(p.updatedAt + 1, now, nextReview)
                    try upsertProjectLocked(p, isDirty: true)
                }
            }
        }
        notifyMutation()
    }

    @discardableResult
    public func createOrUpdateProjectTemplate(_ template: ProjectTemplate) throws -> ProjectTemplate {
        let saved = try queue.sync { () -> ProjectTemplate in
            let now = nowMillis()
            var copy = template
            copy.title = copy.title.trimmingCharacters(in: .whitespacesAndNewlines)
            if copy.title.isEmpty { copy.title = "Project Template" }
            if copy.createdAt <= 0 { copy.createdAt = now }
            if copy.sortOrder == 0 {
                let maxOrd = (try? maxSortOrderLocked(table: "project_templates")) ?? 0
                copy.sortOrder = maxOrd + 1000.0
            }
            copy.updatedAt = now
            copy.isDeleted = false
            copy.isDirty = true
            try inTransactionLocked {
                try upsertProjectTemplateLocked(copy, isDirty: true)
            }
            return copy
        }
        notifyMutation()
        return saved
    }

    public func deleteProjectTemplate(_ templateId: String) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                let stmt = try prepareLocked("UPDATE project_templates SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(stmt) }
                bindInt64(stmt, 1, now)
                bindText(stmt, 2, templateId)
                _ = sqlite3_step(stmt)
            }
        }
        notifyMutation()
    }

    @discardableResult
    public func saveProjectAsTemplate(projectId: String, templateTitle: String? = nil) throws -> ProjectTemplate {
        let created = try queue.sync { () -> ProjectTemplate in
            guard let source = try fetchProjectByIdLocked(projectId) else {
                throw ThingsDatabaseError.notFound("Project \(projectId) not found.")
            }
            let now = nowMillis()
            let allHeadings = try fetchAllActiveHeadingsLocked()
                .filter { $0.projectId == projectId && !$0.isArchived }
                .sorted { $0.sortOrder < $1.sortOrder }

            let tags = try fetchActiveTagsLocked()
            let tagsById = Dictionary(tags.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            let checklistsByTask = try fetchAllActiveChecklistItemsByTaskLocked()
            let tagIdsByTask = try fetchAllTaskTagIdsLocked()

            let openTasks = try fetchTasksWhereLocked(
                whereClause: "project_id = ? AND is_deleted = 0 AND status = 'OPEN'",
                bind: { stmt in self.bindText(stmt, 1, projectId) },
                orderBy: "sort_order ASC",
                tagsById: tagsById,
                tagIdsByTask: tagIdsByTask,
                checklistsByTask: checklistsByTask
            )

            let validHeadingIds = Set(allHeadings.map(\.id))
            func toSpec(_ t: TaskItem) -> TemplateTaskSpec {
                TemplateTaskSpec(
                    title: t.title,
                    notes: t.notes,
                    checklistItems: t.checklistItems.sorted { $0.sortOrder < $1.sortOrder }.map(\.title),
                    scheduledStartMinutes: t.scheduledStartMinutes,
                    durationMinutes: t.durationMinutes,
                    isMilestone: t.isMilestone
                )
            }

            let unheaded = openTasks
                .filter { task in
                    guard let hid = task.headingId else { return true }
                    return !validHeadingIds.contains(hid)
                }
                .map(toSpec)

            let todayDay = DateEpochConverter.epochDay(forMillis: now)
            let headingSpecs = allHeadings.map { h in
                let offset = h.endDate.map { max(0, Int(DateEpochConverter.epochDay(forMillis: $0) - todayDay)) }
                return TemplateHeadingSpec(
                    title: h.title,
                    endDateDayOffset: offset,
                    tasks: openTasks.filter { $0.headingId == h.id }.map(toSpec)
                )
            }

            let blueprint = ProjectTemplateBlueprint(
                unheadedTasks: unheaded,
                headings: headingSpecs
            )
            let maxOrd = (try? maxSortOrderLocked(table: "project_templates")) ?? 0
            let cleanOverride = templateTitle?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let finalTitle = cleanOverride.isEmpty ? source.title : cleanOverride

            let tmpl = ProjectTemplate(
                id: UUID().uuidString,
                title: finalTitle.isEmpty ? "Project Template" : finalTitle,
                notes: source.notes,
                defaultAreaId: source.areaId,
                tagIds: source.tagIds,
                blueprintJson: blueprint.toJSONString(),
                sortOrder: maxOrd + 1000.0,
                createdAt: now,
                updatedAt: now,
                isDeleted: false,
                isDirty: true
            )
            try inTransactionLocked {
                try upsertProjectTemplateLocked(tmpl, isDirty: true)
            }
            return tmpl
        }
        notifyMutation()
        return created
    }

    @discardableResult
    public func createProjectFromTemplate(
        _ template: ProjectTemplate,
        titleOverride: String? = nil,
        areaIdOverride: String? = nil
    ) throws -> Project {
        try instantiateProjectFromTemplate(
            template,
            titleOverride: titleOverride,
            areaIdOverride: areaIdOverride
        )
    }

    @discardableResult
    public func instantiateProjectFromTemplate(
        _ template: ProjectTemplate,
        titleOverride: String? = nil,
        areaIdOverride: String? = nil
    ) throws -> Project {
        let created = try queue.sync { () -> Project in
            let now = nowMillis()
            let blueprint = template.blueprint
            let cleanTitle = (titleOverride?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty ? nil : $0 }
                ?? (template.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "New Project" : template.title)
            let validAreas = Set((try fetchActiveAreasLocked()).map(\.id))
            let candidateArea = areaIdOverride ?? template.defaultAreaId
            let resolvedAreaId = candidateArea.flatMap { validAreas.contains($0) ? $0 : nil }
            let validTags = Set((try fetchActiveTagsLocked()).map(\.id))
            let resolvedTagIds = template.tagIds.filter { validTags.contains($0) }

            return try instantiateBlueprintLocked(
                title: cleanTitle,
                notes: template.notes,
                areaId: resolvedAreaId,
                tagIds: resolvedTagIds,
                blueprint: blueprint,
                now: now
            )
        }
        notifyMutation()
        return created
    }

    @discardableResult
    public func importProjectFromMarkdownOrJson(
        _ rawInput: String,
        defaultTitle: String = "Imported Project",
        areaId: String? = nil
    ) throws -> Project {
        let parsed = Self.parseOutlineToBlueprint(rawInput, defaultTitle: defaultTitle)
        let created = try queue.sync { () -> Project in
            let now = nowMillis()
            let validAreas = Set((try fetchActiveAreasLocked()).map(\.id))
            let resolvedAreaId = areaId.flatMap { validAreas.contains($0) ? $0 : nil }
            return try instantiateBlueprintLocked(
                title: parsed.title,
                notes: parsed.notes,
                areaId: resolvedAreaId,
                tagIds: [],
                blueprint: parsed.blueprint,
                now: now
            )
        }
        notifyMutation()
        return created
    }

    private func instantiateBlueprintLocked(
        title: String,
        notes: String,
        areaId: String?,
        tagIds: [String],
        blueprint: ProjectTemplateBlueprint,
        now: Int64
    ) throws -> Project {
        try inTransactionLocked {
            let newProjId = UUID().uuidString
            let maxProjOrd = try maxSortOrderLocked(table: "projects")
            let project = Project(
                id: newProjId,
                areaId: areaId,
                title: title,
                notes: notes,
                status: .active,
                scheduleState: .anytime,
                lastReviewedAt: now,
                sortOrder: maxProjOrd + 1000.0,
                tagIds: tagIds,
                createdAt: now,
                updatedAt: now
            )
            try upsertProjectLocked(project, isDirty: true)

            var nextTaskOrder = (try maxSortOrderLocked(table: "tasks")) + 1000.0

            func insertTaskSpec(_ spec: TemplateTaskSpec, headingId: String?) throws {
                let cleanTaskTitle = spec.title.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !cleanTaskTitle.isEmpty else { return }
                let tid = UUID().uuidString
                let checklists = spec.checklistItems.enumerated().compactMap { (idx, itemTitle) -> ChecklistItem? in
                    let trimmed = itemTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !trimmed.isEmpty else { return nil }
                    return ChecklistItem(
                        id: UUID().uuidString,
                        taskId: tid,
                        title: trimmed,
                        isCompleted: false,
                        sortOrder: Double((idx + 1) * 100),
                        createdAt: now,
                        updatedAt: now
                    )
                }
                let task = TaskItem(
                    id: tid,
                    title: cleanTaskTitle,
                    notes: spec.notes,
                    status: .open,
                    scheduleState: .anytime,
                    scheduledStartMinutes: spec.scheduledStartMinutes,
                    durationMinutes: spec.durationMinutes,
                    projectId: newProjId,
                    headingId: headingId,
                    areaId: areaId,
                    sortOrder: nextTaskOrder,
                    isMilestone: spec.isMilestone,
                    checklistItems: checklists,
                    createdAt: now,
                    updatedAt: now
                )
                nextTaskOrder += 1000.0
                try upsertTaskLocked(task, tagIds: [], isDirty: true)
                for chk in checklists {
                    try upsertChecklistItemLocked(chk, isDirty: true)
                }
            }

            for spec in blueprint.unheadedTasks {
                try insertTaskSpec(spec, headingId: nil)
            }

            let baseSod = Calendar.current.startOfDay(for: Date(timeIntervalSince1970: Double(now) / 1000.0))
            for (hIdx, hSpec) in blueprint.headings.enumerated() {
                let cleanHTitle = hSpec.title.trimmingCharacters(in: .whitespacesAndNewlines)
                let hid: String?
                if cleanHTitle.isEmpty {
                    hid = nil
                } else {
                    let newHid = UUID().uuidString
                    let phaseEndDate: Int64? = hSpec.endDateDayOffset.flatMap { offset in
                        Calendar.current.date(byAdding: .day, value: max(0, offset), to: baseSod)
                            .map { DateEpochConverter.startOfDayMillis(for: $0) }
                    }
                    let heading = Heading(
                        id: newHid,
                        projectId: newProjId,
                        title: cleanHTitle,
                        sortOrder: Double((hIdx + 1) * 1000),
                        endDate: phaseEndDate,
                        createdAt: now,
                        updatedAt: now
                    )
                    try upsertHeadingLocked(heading, isDirty: true)
                    hid = newHid
                }
                for tSpec in hSpec.tasks {
                    try insertTaskSpec(tSpec, headingId: hid)
                }
            }

            return try fetchProjectByIdLocked(newProjId) ?? project
        }
    }

    public static func parseOutlineToBlueprint(
        _ rawInput: String,
        defaultTitle: String = "Imported Project"
    ) -> (title: String, notes: String, blueprint: ProjectTemplateBlueprint) {
        let trimmed = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return (defaultTitle, "", ProjectTemplateBlueprint())
        }

        // 1. JSON outline / template format support
        if (trimmed.hasPrefix("{") || trimmed.hasPrefix("[")),
           let data = trimmed.data(using: .utf8),
           let jsonObj = try? JSONSerialization.jsonObject(with: data) {
            if let dict = jsonObj as? [String: Any] {
                let title = ((dict["title"] as? String) ?? (dict["name"] as? String) ?? "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                let notes = (dict["notes"] as? String) ?? (dict["description"] as? String) ?? ""
                if let bpStr = dict["blueprintJson"] as? String {
                    let bp = ProjectTemplateBlueprint.decode(from: bpStr)
                    return (title.isEmpty ? defaultTitle : title, notes, bp)
                }
                if let bpDict = dict["blueprint"] as? [String: Any],
                   let bpData = try? JSONSerialization.data(withJSONObject: bpDict),
                   let bp = try? JSONDecoder().decode(ProjectTemplateBlueprint.self, from: bpData) {
                    return (title.isEmpty ? defaultTitle : title, notes, bp)
                }
                if let bp = try? JSONDecoder().decode(ProjectTemplateBlueprint.self, from: data) {
                    return (title.isEmpty ? defaultTitle : title, notes, bp)
                }
            } else if let arr = jsonObj as? [Any] {
                var tasks: [TemplateTaskSpec] = []
                for elem in arr {
                    if let s = elem as? String {
                        let clean = s.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !clean.isEmpty { tasks.append(TemplateTaskSpec(title: clean)) }
                    } else if let d = elem as? [String: Any],
                              let dData = try? JSONSerialization.data(withJSONObject: d),
                              let spec = try? JSONDecoder().decode(TemplateTaskSpec.self, from: dData) {
                        if !spec.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            tasks.append(spec)
                        }
                    }
                }
                return (defaultTitle, "", ProjectTemplateBlueprint(unheadedTasks: tasks))
            }
        }

        // 2. Markdown / indented outline parser (`# Project`, `## Heading`, `- [ ] Task`, `  - [ ] Subtask`)
        var projectTitle: String? = nil
        var projectNotesLines: [String] = []
        var unheadedTasks: [TemplateTaskSpec] = []
        var headings: [TemplateHeadingSpec] = []
        var currentHeadingIndex: Int? = nil

        func stripBulletMarker(_ line: String) -> String? {
            let s = line.trimmingCharacters(in: .whitespaces)
            let checkboxPrefixes = ["- [ ] ", "- [x] ", "- [X] ", "* [ ] ", "* [x] ", "* [X] ", "+ [ ] ", "+ [x] ", "+ [X] "]
            for p in checkboxPrefixes where s.hasPrefix(p) {
                return String(s.dropFirst(p.count)).trimmingCharacters(in: .whitespaces)
            }
            let simplePrefixes = ["- ", "* ", "+ ", "• "]
            for p in simplePrefixes where s.hasPrefix(p) {
                return String(s.dropFirst(p.count)).trimmingCharacters(in: .whitespaces)
            }
            if let dotIdx = s.firstIndex(of: "."), dotIdx > s.startIndex {
                let prefix = s[..<dotIdx]
                if prefix.allSatisfy(\.isNumber) {
                    let rest = s[s.index(after: dotIdx)...]
                    if rest.hasPrefix(" ") {
                        return rest.trimmingCharacters(in: .whitespaces)
                    }
                }
            }
            return nil
        }

        func appendSubtaskOrNote(subtask: String?, note: String?) -> Bool {
            if let hIdx = currentHeadingIndex {
                guard !headings[hIdx].tasks.isEmpty else { return false }
                let tIdx = headings[hIdx].tasks.count - 1
                if let sub = subtask, !sub.isEmpty {
                    headings[hIdx].tasks[tIdx].checklistItems.append(sub)
                    return true
                }
                if let n = note, !n.isEmpty {
                    let prev = headings[hIdx].tasks[tIdx].notes
                    headings[hIdx].tasks[tIdx].notes = prev.isEmpty ? n : "\(prev)\n\(n)"
                    return true
                }
            } else {
                guard !unheadedTasks.isEmpty else { return false }
                let tIdx = unheadedTasks.count - 1
                if let sub = subtask, !sub.isEmpty {
                    unheadedTasks[tIdx].checklistItems.append(sub)
                    return true
                }
                if let n = note, !n.isEmpty {
                    let prev = unheadedTasks[tIdx].notes
                    unheadedTasks[tIdx].notes = prev.isEmpty ? n : "\(prev)\n\(n)"
                    return true
                }
            }
            return false
        }

        let rawLines = trimmed.components(separatedBy: .newlines)
        for rawLine in rawLines {
            let lineWithoutCR = rawLine.replacingOccurrences(of: "\r", with: "")
            let cleanLine = lineWithoutCR.trimmingCharacters(in: .whitespaces)
            if cleanLine.isEmpty { continue }

            var leadingIndentColumns = 0
            for ch in lineWithoutCR {
                if ch == " " { leadingIndentColumns += 1 }
                else if ch == "\t" { leadingIndentColumns += 4 }
                else { break }
            }
            let isIndented = leadingIndentColumns >= 2

            if !isIndented && cleanLine.hasPrefix("# ") {
                let h1 = String(cleanLine.dropFirst(2)).trimmingCharacters(in: .whitespaces)
                if !h1.isEmpty {
                    if projectTitle == nil {
                        projectTitle = h1
                    } else {
                        headings.append(TemplateHeadingSpec(title: h1))
                        currentHeadingIndex = headings.count - 1
                    }
                }
                continue
            }

            if !isIndented && (cleanLine.hasPrefix("## ") || cleanLine.hasPrefix("### ")) {
                let dropCount = cleanLine.hasPrefix("### ") ? 4 : 3
                let hTitle = String(cleanLine.dropFirst(dropCount)).trimmingCharacters(in: .whitespaces)
                if !hTitle.isEmpty {
                    headings.append(TemplateHeadingSpec(title: hTitle))
                    currentHeadingIndex = headings.count - 1
                }
                continue
            }

            if let itemText = stripBulletMarker(cleanLine) {
                guard !itemText.isEmpty else { continue }
                if isIndented && appendSubtaskOrNote(subtask: itemText, note: nil) {
                    continue
                }
                let meta = extractOutlineTaskInlineMetadata(itemText)
                let spec = TemplateTaskSpec(
                    title: meta.cleanTitle,
                    scheduledStartMinutes: meta.scheduledStartMinutes,
                    durationMinutes: meta.durationMinutes,
                    isMilestone: meta.isMilestone
                )
                if let hIdx = currentHeadingIndex {
                    headings[hIdx].tasks.append(spec)
                } else {
                    unheadedTasks.append(spec)
                }
                continue
            }

            if isIndented && appendSubtaskOrNote(subtask: nil, note: cleanLine) {
                continue
            }

            if projectTitle != nil && headings.isEmpty && unheadedTasks.isEmpty {
                projectNotesLines.append(cleanLine)
            } else {
                let meta = extractOutlineTaskInlineMetadata(cleanLine)
                let spec = TemplateTaskSpec(
                    title: meta.cleanTitle,
                    scheduledStartMinutes: meta.scheduledStartMinutes,
                    durationMinutes: meta.durationMinutes,
                    isMilestone: meta.isMilestone
                )
                if let hIdx = currentHeadingIndex {
                    headings[hIdx].tasks.append(spec)
                } else {
                    unheadedTasks.append(spec)
                }
            }
        }

        let resolvedTitle = (projectTitle?.isEmpty == false) ? projectTitle! : defaultTitle
        let resolvedNotes = projectNotesLines.joined(separator: "\n")
        return (resolvedTitle, resolvedNotes, ProjectTemplateBlueprint(unheadedTasks: unheadedTasks, headings: headings))
    }

    private static func extractOutlineTaskInlineMetadata(
        _ rawTitle: String
    ) -> (cleanTitle: String, scheduledStartMinutes: Int?, durationMinutes: Int?, isMilestone: Bool) {
        var working = rawTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        var startMinutes: Int? = nil
        var durationMinutes: Int? = nil
        var isMilestone = false

        // 0. Inline @milestone token (case-insensitive anywhere as whitespace-delimited token)
        if let milestoneRegex = try? NSRegularExpression(pattern: #"(?:^|\s)@milestone(?=\s|$)"#, options: [.caseInsensitive]) {
            while true {
                let nsRange = NSRange(working.startIndex..<working.endIndex, in: working)
                guard let match = milestoneRegex.firstMatch(in: working, range: nsRange),
                      let fullRange = Range(match.range(at: 0), in: working) else {
                    break
                }
                isMilestone = true
                let prefix = String(working[..<fullRange.lowerBound]).trimmingCharacters(in: .whitespaces)
                let suffix = String(working[fullRange.upperBound...]).trimmingCharacters(in: .whitespaces)
                working = [prefix, suffix].filter { !$0.isEmpty }.joined(separator: " ")
            }
        }

        // 1. Trailing @<duration> token (e.g. "@45m", "@1d", "@2.5d", "@1h30m"), requiring a unit letter so "@123" is untouched
        if let atDurRegex = try? NSRegularExpression(pattern: #"\s+@([^\s]+)$"#) {
            let nsRange = NSRange(working.startIndex..<working.endIndex, in: working)
            if let match = atDurRegex.firstMatch(in: working, range: nsRange),
               let tokenRange = Range(match.range(at: 1), in: working),
               let fullRange = Range(match.range(at: 0), in: working) {
                let token = String(working[tokenRange])
                let isClockStart = token.range(of: #"^\d{1,2}:\d{2}$"#, options: .regularExpression) != nil
                let hasUnitLetter = token.range(of: #"[a-zA-Zäöüßáéíóúàèìòù]"#, options: .regularExpression) != nil
                if !isClockStart && hasUnitLetter, let parsedDur = TaskItem.parseCustomDurationMinutes(token) {
                    durationMinutes = parsedDur
                    working = String(working[..<fullRange.lowerBound]).trimmingCharacters(in: .whitespaces)
                }
            }
        }

        // 2. Inline @HH:MM start time token (e.g. "@09:00", "@14:30")
        if let timeRegex = try? NSRegularExpression(pattern: #"(?:^|\s)@(\d{1,2}):(\d{2})(?=\s|$)"#) {
            let nsRange = NSRange(working.startIndex..<working.endIndex, in: working)
            if let match = timeRegex.firstMatch(in: working, range: nsRange),
               let hRange = Range(match.range(at: 1), in: working),
               let mRange = Range(match.range(at: 2), in: working),
               let fullRange = Range(match.range(at: 0), in: working),
               let h = Int(working[hRange]),
               let m = Int(working[mRange]),
               (0...23).contains(h), (0...59).contains(m) {
                startMinutes = h * 60 + m
                let prefix = String(working[..<fullRange.lowerBound]).trimmingCharacters(in: .whitespaces)
                let suffix = String(working[fullRange.upperBound...]).trimmingCharacters(in: .whitespaces)
                working = [prefix, suffix].filter { !$0.isEmpty }.joined(separator: " ")
            }
        }

        // 3. Re-check trailing @<duration> or (<duration>) in case @HH:MM came after @<duration> or title ended with "(45m)" / "(1d)"
        if durationMinutes == nil {
            if let atDurRegex = try? NSRegularExpression(pattern: #"\s+@([^\s]+)$"#) {
                let nsRange = NSRange(working.startIndex..<working.endIndex, in: working)
                if let match = atDurRegex.firstMatch(in: working, range: nsRange),
                   let tokenRange = Range(match.range(at: 1), in: working),
                   let fullRange = Range(match.range(at: 0), in: working) {
                    let token = String(working[tokenRange])
                    let hasUnitLetter = token.range(of: #"[a-zA-Zäöüßáéíóúàèìòù]"#, options: .regularExpression) != nil
                    if hasUnitLetter, let parsedDur = TaskItem.parseCustomDurationMinutes(token) {
                        durationMinutes = parsedDur
                        working = String(working[..<fullRange.lowerBound]).trimmingCharacters(in: .whitespaces)
                    }
                }
            }
        }

        if durationMinutes == nil {
            if let parenRegex = try? NSRegularExpression(pattern: #"\s+\(([^()]+)\)$"#) {
                let nsRange = NSRange(working.startIndex..<working.endIndex, in: working)
                if let match = parenRegex.firstMatch(in: working, range: nsRange),
                   let tokenRange = Range(match.range(at: 1), in: working),
                   let fullRange = Range(match.range(at: 0), in: working) {
                    let token = String(working[tokenRange])
                    let hasUnitLetter = token.range(of: #"[a-zA-Zäöüßáéíóúàèìòù]"#, options: .regularExpression) != nil
                    if hasUnitLetter, let parsedDur = TaskItem.parseCustomDurationMinutes(token) {
                        durationMinutes = parsedDur
                        working = String(working[..<fullRange.lowerBound]).trimmingCharacters(in: .whitespaces)
                    }
                }
            }
        }

        let clean = working.trimmingCharacters(in: .whitespacesAndNewlines)
        return (clean.isEmpty ? rawTitle.trimmingCharacters(in: .whitespacesAndNewlines) : clean, startMinutes, durationMinutes, isMilestone)
    }

    @discardableResult
    public func createHeading(
        projectId: String,
        title: String,
        sortOrder: Double? = nil,
        startDate: Int64? = nil,
        endDate: Int64? = nil
    ) throws -> Heading {
        let heading = try queue.sync { () -> Heading in
            let now = nowMillis()
            let resolvedOrder: Double
            if let explicitOrder = sortOrder, explicitOrder.isFinite {
                resolvedOrder = explicitOrder
            } else {
                let existing = try fetchAllActiveHeadingsLocked().filter { $0.projectId == projectId }
                let maxOrd = existing.map(\.sortOrder).max() ?? 0.0
                resolvedOrder = maxOrd + 1000.0
            }
            let h = Heading(
                id: UUID().uuidString,
                projectId: projectId,
                title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                sortOrder: resolvedOrder,
                startDate: startDate,
                endDate: endDate,
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

    public func updateHeadingStartDate(_ headingId: String, startDate: Int64?) throws {
        var didMutate = false
        try queue.sync {
            guard var existing = try fetchHeadingsWhereLocked("id = '\(headingId.replacingOccurrences(of: "'", with: "''"))' AND is_deleted = 0").first else { return }
            existing.startDate = startDate
            existing.updatedAt = nowMillis()
            try upsertHeadingLocked(existing, isDirty: true)
            didMutate = true
        }
        if didMutate {
            notifyMutation()
        }
    }

    public func updateHeadingEndDate(_ headingId: String, endDate: Int64?) throws {
        var didMutate = false
        try queue.sync {
            guard var existing = try fetchHeadingsWhereLocked("id = '\(headingId.replacingOccurrences(of: "'", with: "''"))' AND is_deleted = 0").first else { return }
            existing.endDate = endDate
            existing.updatedAt = nowMillis()
            try upsertHeadingLocked(existing, isDirty: true)
            didMutate = true
        }
        if didMutate {
            notifyMutation()
        }
    }

    public func updateHeadingDates(_ headingId: String, startDate: Int64?, endDate: Int64?) throws {
        var didMutate = false
        try queue.sync {
            let safeId = headingId.replacingOccurrences(of: "'", with: "''")
            guard var existing = try fetchHeadingsWhereLocked("id = '\(safeId)' AND is_deleted = 0").first else { return }
            let resolvedEnd: Int64?
            if let s = startDate, let e = endDate, e < s {
                resolvedEnd = s
            } else {
                resolvedEnd = endDate
            }
            existing.startDate = startDate
            existing.endDate = resolvedEnd
            existing.updatedAt = max(nowMillis(), existing.updatedAt + 1)
            try inTransactionLocked {
                try upsertHeadingLocked(existing, isDirty: true)
            }
            didMutate = true
        }
        if didMutate {
            notifyMutation()
        }
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
    public func promoteUngroupedTasksToHeading(
        projectId: String,
        title: String
    ) throws -> Heading? {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty else { return nil }

        var createdHeading: Heading? = nil
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                let activeHeadings = try fetchAllActiveHeadingsLocked()
                    .filter { $0.projectId == projectId && !$0.isArchived }
                    .sorted {
                        if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                        return $0.id < $1.id
                    }
                let validHeadingIds = Set(activeHeadings.map(\.id))
                let prependedSortOrder = (activeHeadings.first?.sortOrder ?? 0.0) - 1000.0

                let heading = Heading(
                    id: UUID().uuidString,
                    projectId: projectId,
                    title: cleanTitle,
                    sortOrder: prependedSortOrder,
                    isArchived: false,
                    startDate: nil,
                    endDate: nil,
                    createdAt: now,
                    updatedAt: now
                )
                try upsertHeadingLocked(heading, isDirty: true)

                var targetTaskIds: [String] = []
                let qStmt = try prepareLocked("""
                    SELECT id, heading_id FROM tasks
                    WHERE project_id = ? AND is_deleted = 0 AND status = 'OPEN';
                """)
                defer { sqlite3_finalize(qStmt) }
                bindText(qStmt, 1, projectId)
                while sqlite3_step(qStmt) == SQLITE_ROW {
                    guard let tid = colString(qStmt, 0) else { continue }
                    let hid = colString(qStmt, 1)
                    if hid == nil || !validHeadingIds.contains(hid!) {
                        targetTaskIds.append(tid)
                    }
                }

                if !targetTaskIds.isEmpty {
                    let uStmt = try prepareLocked("""
                        UPDATE tasks
                        SET heading_id = ?, updated_at = ?, is_dirty = 1
                        WHERE id = ?;
                    """)
                    defer { sqlite3_finalize(uStmt) }
                    for tid in targetTaskIds {
                        sqlite3_reset(uStmt)
                        sqlite3_clear_bindings(uStmt)
                        bindText(uStmt, 1, heading.id)
                        bindInt64(uStmt, 2, now)
                        bindText(uStmt, 3, tid)
                        _ = sqlite3_step(uStmt)
                    }
                }

                createdHeading = heading
            }
        }
        if createdHeading != nil {
            notifyMutation()
        }
        return createdHeading
    }

    @discardableResult
    public func createOrUpdateTag(id: String? = nil, title: String, parentTagId: String? = nil, shortcut: String? = nil) throws -> Tag {
        var didMutate = false
        let tag = try queue.sync { () -> Tag in
            let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
            let existingTags = try fetchActiveTagsLocked()
            let cleanShortcut = shortcut?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().first.map(String.init)
            if id == nil, let match = existingTags.first(where: { $0.title.caseInsensitiveCompare(cleanTitle) == .orderedSame }) {
                if let explicitShortcut = cleanShortcut, match.shortcut != explicitShortcut {
                    let now = max(nowMillis(), match.updatedAt + 1)
                    var updated = match
                    updated.shortcut = explicitShortcut
                    updated.updatedAt = now
                    try inTransactionLocked {
                        try upsertTagLocked(updated, isDirty: true)
                    }
                    didMutate = true
                    return updated
                }
                return match
            }
            let existingById = id.flatMap { targetId in existingTags.first(where: { $0.id == targetId }) }
            let now = max(nowMillis(), (existingById?.updatedAt ?? 0) + 1)
            let maxOrd = existingTags.map(\.sortOrder).max() ?? 0.0
            let t = Tag(
                id: id ?? UUID().uuidString,
                title: cleanTitle,
                parentTagId: parentTagId ?? existingById?.parentTagId,
                shortcut: cleanShortcut,
                sortOrder: existingById?.sortOrder ?? (maxOrd + 100.0),
                createdAt: existingById?.createdAt ?? now,
                updatedAt: now
            )
            try inTransactionLocked {
                try upsertTagLocked(t, isDirty: true)
            }
            didMutate = true
            return t
        }
        if didMutate { notifyMutation() }
        return tag
    }

    @discardableResult
    public func mergeTags(sourceTagId: String, into targetTagId: String) throws -> Bool {
        let cleanSource = sourceTagId.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanTarget = targetTagId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanSource.isEmpty, !cleanTarget.isEmpty, cleanSource != cleanTarget else {
            return false
        }
        let merged: Bool = try queue.sync {
            let activeTags = try fetchActiveTagsLocked()
            guard activeTags.contains(where: { $0.id == cleanSource }),
                  let targetTag = activeTags.first(where: { $0.id == cleanTarget }) else {
                return false
            }
            let now = nowMillis()
            try inTransactionLocked {
                if targetTag.parentTagId == cleanSource {
                    let fixTargetParent = try prepareLocked("""
                        UPDATE tags
                        SET parent_tag_id = NULL, updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END, is_dirty = 1
                        WHERE id = ?;
                    """)
                    defer { sqlite3_finalize(fixTargetParent) }
                    bindInt64(fixTargetParent, 1, now)
                    bindInt64(fixTargetParent, 2, now)
                    bindText(fixTargetParent, 3, cleanTarget)
                    _ = sqlite3_step(fixTargetParent)
                }

                try remapTagReferencesLocked(
                    from: cleanSource,
                    to: cleanTarget,
                    now: now,
                    markAffectedDirty: true
                )

                let tombstoneStmt = try prepareLocked("""
                    UPDATE tags
                    SET is_deleted = 1, parent_tag_id = NULL, shortcut = NULL,
                        updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END,
                        is_dirty = 1
                    WHERE id = ?;
                """)
                defer { sqlite3_finalize(tombstoneStmt) }
                bindInt64(tombstoneStmt, 1, now)
                bindInt64(tombstoneStmt, 2, now)
                bindText(tombstoneStmt, 3, cleanSource)
                _ = sqlite3_step(tombstoneStmt)
            }
            return true
        }
        if merged { notifyMutation() }
        return merged
    }

    public func moveTagOrder(_ tagId: String, moveUp: Bool) throws {
        let didMove: Bool = try queue.sync {
            let active = try fetchActiveTagsLocked()
            var ordered = TagListSorter.sort(active, mode: "manual", usageCounts: [:])
            guard let idx = ordered.firstIndex(where: { $0.id == tagId }) else { return false }
            let targetIdx = moveUp ? (idx - 1) : (idx + 1)
            guard ordered.indices.contains(targetIdx) else { return false }

            ordered.swapAt(idx, targetIdx)
            let now = nowMillis()
            try inTransactionLocked {
                for (position, var tag) in ordered.enumerated() {
                    let desiredOrder = Double((position + 1) * 100)
                    if abs(tag.sortOrder - desiredOrder) > 0.0001 {
                        tag.sortOrder = desiredOrder
                        tag.updatedAt = max(now, tag.updatedAt + 1)
                        try writeTagRowLocked(tag, isDirty: true)
                    }
                }
            }
            return true
        }
        if didMove { notifyMutation() }
    }

    public func batchUpdateTasksTags(
        _ taskIds: [String],
        addTagIds: Set<String>,
        removeTagIds: Set<String>
    ) throws {
        guard !taskIds.isEmpty, (!addTagIds.isEmpty || !removeTagIds.isEmpty) else { return }
        let didMutate: Bool = try queue.sync {
            let validTagIds = Set((try fetchActiveTagsLocked()).map(\.id))
            let tagIdsByTask = try fetchAllTaskTagIdsLocked()
            let now = nowMillis()
            var changed = false
            try inTransactionLocked {
                for tid in Set(taskIds) {
                    guard var task = try fetchTaskByIdLocked(tid), !task.isDeleted else { continue }
                    let currentIds = tagIdsByTask[tid] ?? task.tagIds
                    var nextIds = currentIds.filter { !removeTagIds.contains($0) && validTagIds.contains($0) }
                    for addId in addTagIds.sorted() where validTagIds.contains(addId) && !nextIds.contains(addId) {
                        nextIds.append(addId)
                    }
                    if Set(nextIds) != Set(currentIds) {
                        task.updatedAt = max(now, task.updatedAt + 1)
                        try upsertTaskLocked(task, tagIds: nextIds, isDirty: true)
                        changed = true
                    }
                }
            }
            return changed
        }
        if didMutate { notifyMutation() }
    }

    public func deleteTag(_ tagId: String) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                try cleanDeletedTagReferencesLocked(tagId: tagId, now: now, markParentsDirty: true)

                let s3 = try prepareLocked("UPDATE tags SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(s3) }
                bindInt64(s3, 1, now)
                bindText(s3, 2, tagId)
                _ = sqlite3_step(s3)
            }
        }
        notifyMutation()
    }

    private func cleanDeletedTagReferencesLocked(tagId: String, now: Int64, markParentsDirty: Bool) throws {
        let s1 = try prepareLocked("DELETE FROM task_tags WHERE tag_id = ?;")
        defer { sqlite3_finalize(s1) }
        bindText(s1, 1, tagId)
        _ = sqlite3_step(s1)

        let s2 = try prepareLocked("DELETE FROM project_tags WHERE tag_id = ?;")
        defer { sqlite3_finalize(s2) }
        bindText(s2, 1, tagId)
        _ = sqlite3_step(s2)

        if markParentsDirty {
            let childStmt = try prepareLocked("UPDATE tags SET parent_tag_id = NULL, updated_at = ?, is_dirty = 1 WHERE parent_tag_id = ? AND is_deleted = 0;")
            defer { sqlite3_finalize(childStmt) }
            bindInt64(childStmt, 1, now)
            bindText(childStmt, 2, tagId)
            _ = sqlite3_step(childStmt)
        } else {
            let childStmt = try prepareLocked("UPDATE tags SET parent_tag_id = NULL WHERE parent_tag_id = ?;")
            defer { sqlite3_finalize(childStmt) }
            bindText(childStmt, 1, tagId)
            _ = sqlite3_step(childStmt)
        }

        for table in ["tasks", "projects", "areas", "project_templates"] {
            try removeTagIdFromJsonColumnLocked(table: table, tagId: tagId, now: now, markDirty: markParentsDirty)
        }
    }

    private func removeTagIdFromJsonColumnLocked(table: String, tagId: String, now: Int64, markDirty: Bool) throws {
        let selectStmt = try prepareLocked("SELECT id, tag_ids_json FROM \(table) WHERE tag_ids_json LIKE ?;")
        defer { sqlite3_finalize(selectStmt) }
        bindText(selectStmt, 1, "%\(tagId)%")

        var updates: [(id: String, json: String)] = []
        while sqlite3_step(selectStmt) == SQLITE_ROW {
            guard let rowId = colString(selectStmt, 0) else { continue }
            let existing = decodeStringArray(colString(selectStmt, 1))
            let filtered = existing.filter { $0 != tagId }
            if filtered.count != existing.count {
                updates.append((rowId, encodeStringArray(filtered)))
            }
        }

        for item in updates {
            if markDirty {
                let upd = try prepareLocked("UPDATE \(table) SET tag_ids_json = ?, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(upd) }
                bindText(upd, 1, item.json)
                bindInt64(upd, 2, now)
                bindText(upd, 3, item.id)
                _ = sqlite3_step(upd)
            } else {
                let upd = try prepareLocked("UPDATE \(table) SET tag_ids_json = ? WHERE id = ?;")
                defer { sqlite3_finalize(upd) }
                bindText(upd, 1, item.json)
                bindText(upd, 2, item.id)
                _ = sqlite3_step(upd)
            }
        }
    }

    // MARK: - Sample Data Purge & Account Prep

    public func purgeSampleDataLocally(
        preserveUsedContainers: Bool = true,
        emitCloudTombstones: Bool = false
    ) throws {
        try queue.sync {
            let now = nowMillis()
            try inTransactionLocked {
                var preservedSampleTaskIds = Set<String>()
                var purgeableTaskIds: [String] = []
                for id in DatabaseSeeder.sampleTaskIds {
                    if let existing = try fetchTaskByIdLocked(id) {
                        if DatabaseSeeder.isPurgeableSampleTask(existing) {
                            purgeableTaskIds.append(id)
                        } else {
                            preservedSampleTaskIds.insert(id)
                        }
                    }
                }

                for id in DatabaseSeeder.sampleChecklistIds {
                    let parentTaskId: String? = {
                        guard let stmt = try? prepareLocked("SELECT task_id FROM checklist_items WHERE id = ?;") else { return nil }
                        defer { sqlite3_finalize(stmt) }
                        bindText(stmt, 1, id)
                        return sqlite3_step(stmt) == SQLITE_ROW ? colString(stmt, 0) : nil
                    }()
                    if let parentTaskId, preservedSampleTaskIds.contains(parentTaskId) {
                        continue
                    }
                    if emitCloudTombstones {
                        let chkStmt = try prepareLocked("UPDATE checklist_items SET title = '', is_deleted = 1, updated_at = MAX(updated_at + 1, ?), is_dirty = 1 WHERE id = ?;")
                        defer { sqlite3_finalize(chkStmt) }
                        bindInt64(chkStmt, 1, now)
                        bindText(chkStmt, 2, id)
                        _ = sqlite3_step(chkStmt)
                    } else {
                        try deleteByIdLocked(table: "checklist_items", id: id)
                    }
                }

                for id in purgeableTaskIds {
                    if emitCloudTombstones {
                        try purgeTaskToTombstoneLocked(taskId: id, now: now)
                    } else {
                        try deleteByIdLocked(table: "tasks", id: id)
                    }
                }

                for id in DatabaseSeeder.sampleHeadingIds {
                    if preserveUsedContainers && countForeignRefsLocked("SELECT COUNT(*) FROM tasks WHERE heading_id = ? AND is_deleted = 0;", id) > 0 {
                        continue
                    }
                    if emitCloudTombstones {
                        let hdgStmt = try prepareLocked("UPDATE headings SET title = '', is_deleted = 1, updated_at = MAX(updated_at + 1, ?), is_dirty = 1 WHERE id = ?;")
                        defer { sqlite3_finalize(hdgStmt) }
                        bindInt64(hdgStmt, 1, now)
                        bindText(hdgStmt, 2, id)
                        _ = sqlite3_step(hdgStmt)
                    } else {
                        try deleteByIdLocked(table: "headings", id: id)
                    }
                }

                for id in DatabaseSeeder.sampleProjectIds {
                    if preserveUsedContainers && countForeignRefsLocked("SELECT COUNT(*) FROM tasks WHERE project_id = ? AND is_deleted = 0;", id) > 0 {
                        continue
                    }
                    if emitCloudTombstones {
                        try purgeProjectToTombstoneLocked(projectId: id, now: now)
                    } else {
                        try deleteByIdLocked(table: "projects", id: id)
                    }
                }

                for id in DatabaseSeeder.sampleAreaIds {
                    if preserveUsedContainers && (
                        countForeignRefsLocked("SELECT COUNT(*) FROM tasks WHERE area_id = ? AND is_deleted = 0;", id) > 0 ||
                        countForeignRefsLocked("SELECT COUNT(*) FROM projects WHERE area_id = ? AND is_deleted = 0;", id) > 0
                    ) {
                        continue
                    }
                    if !emitCloudTombstones {
                        try deleteByIdLocked(table: "areas", id: id)
                    }
                }

                for id in DatabaseSeeder.sampleTagIds {
                    if preserveUsedContainers && (
                        countForeignRefsLocked("SELECT COUNT(*) FROM task_tags tt JOIN tasks t ON t.id = tt.task_id WHERE tt.tag_id = ? AND t.is_deleted = 0;", id) > 0 ||
                        countForeignRefsLocked("SELECT COUNT(*) FROM project_tags pt JOIN projects p ON p.id = pt.project_id WHERE pt.tag_id = ? AND p.is_deleted = 0;", id) > 0
                    ) {
                        continue
                    }
                    if emitCloudTombstones {
                        let tagStmt = try prepareLocked("UPDATE tags SET title = '', is_deleted = 1, updated_at = MAX(updated_at + 1, ?), is_dirty = 1 WHERE id = ?;")
                        defer { sqlite3_finalize(tagStmt) }
                        bindInt64(tagStmt, 1, now)
                        bindText(tagStmt, 2, id)
                        _ = sqlite3_step(tagStmt)
                    } else {
                        try deleteByIdLocked(table: "tags", id: id)
                    }
                }
            }
        }
        notifyMutation()
    }

    public func purgeAllLocalDataBeforeAccountPull() throws {
        try queue.sync {
            try execLocked("PRAGMA foreign_keys = OFF;")
            defer { try? execLocked("PRAGMA foreign_keys = ON;") }
            try inTransactionLocked {
                let existingAtts = (try? fetchAttachmentsWhereLocked("1 = 1")) ?? []
                for att in existingAtts {
                    deleteAttachmentFileLocked(relativePath: att.localRelativePath)
                }
                try execLocked("""
                    DELETE FROM detached_project_tasks;
                    DELETE FROM attachments;
                    DELETE FROM checklist_items;
                    DELETE FROM task_tags;
                    DELETE FROM project_tags;
                    DELETE FROM project_templates;
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
        let areasById = Dictionary(snap.areas.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let projectsById = Dictionary(snap.projects.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
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

    // MARK: - v3.0 Asana & Microsoft Planner External Project Import / Export

    @discardableResult
    public func importExternalProject(
        _ parsed: ExternalImportedProject,
        areaId: String? = nil,
        workspaceId: String? = nil,
        calendar: Calendar = .current
    ) throws -> (project: Project, importedTaskCount: Int) {
        let result = try queue.sync { () -> (Project, Int) in
            let now = nowMillis()
            let todayEpoch = DateEpochConverter.startOfDayMillis(for: Date(), calendar: calendar)

            return try inTransactionLocked {
                let activeAreas = try fetchActiveAreasLocked()
                let validAreaIds = Set(activeAreas.map(\.id))
                let validatedAreaId = areaId.flatMap { validAreaIds.contains($0) ? $0 : nil }

                var tags = try fetchActiveTagsLocked()
                func resolveTagIds(_ rawNames: [String]) throws -> [String] {
                    var ids: [String] = []
                    var seen = Set<String>()
                    for raw in rawNames {
                        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !clean.isEmpty else { continue }
                        let tagId: String
                        if let existing = tags.first(where: { $0.title.caseInsensitiveCompare(clean) == .orderedSame }) {
                            tagId = existing.id
                        } else {
                            let nextSort = (tags.map(\.sortOrder).max() ?? 0.0) + 100.0
                            let newTag = Tag(
                                id: UUID().uuidString,
                                title: clean,
                                sortOrder: nextSort,
                                createdAt: now,
                                updatedAt: now
                            )
                            try upsertTagLocked(newTag, isDirty: true)
                            tags.append(newTag)
                            tagId = newTag.id
                        }
                        if seen.insert(tagId).inserted {
                            ids.append(tagId)
                        }
                    }
                    return ids
                }

                let cleanProjTitle = parsed.title.trimmingCharacters(in: .whitespacesAndNewlines)
                let resolvedProjTitle = cleanProjTitle.isEmpty ? "Imported Project" : cleanProjTitle
                let projectTagIds = try resolveTagIds(parsed.tagNames)
                let effectiveProjWorkspace = (workspaceId ?? parsed.workspaceId)?
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                let normalizedProjWorkspace = (effectiveProjWorkspace?.isEmpty == false) ? effectiveProjWorkspace : nil

                let newProjId = UUID().uuidString
                let maxProjOrd = try maxSortOrderLocked(table: "projects")
                let project = Project(
                    id: newProjId,
                    areaId: validatedAreaId,
                    title: resolvedProjTitle,
                    notes: parsed.notes,
                    status: .active,
                    scheduleState: .anytime,
                    lastReviewedAt: now,
                    sortOrder: maxProjOrd + 1000.0,
                    tagIds: projectTagIds,
                    workspaceId: normalizedProjWorkspace,
                    createdAt: now,
                    updatedAt: now
                )
                try upsertProjectLocked(project, isDirty: true)

                var nextTaskOrder = (try maxSortOrderLocked(table: "tasks")) + 1000.0
                var importedTaskCount = 0

                func insertImportedTask(_ spec: ExternalImportedTask, headingId: String?) throws {
                    let cleanTitle = spec.title.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !cleanTitle.isEmpty else { return }

                    let resolvedTagIds = try resolveTagIds(spec.tagNames)
                    let scheduleState: ScheduleState
                    if spec.status == .open, let w = spec.whenDate {
                        scheduleState = w <= todayEpoch ? .today : .upcoming
                    } else {
                        scheduleState = .anytime
                    }

                    let resolvedCompletedAt: Int64?
                    if spec.status == .completed || spec.status == .canceled {
                        resolvedCompletedAt = spec.completedAt ?? now
                    } else {
                        resolvedCompletedAt = nil
                    }

                    let tid = UUID().uuidString
                    let checklists: [ChecklistItem] = spec.checklistItems.enumerated().compactMap { (cIdx, chkSpec) in
                        let cleanChkTitle = chkSpec.title.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !cleanChkTitle.isEmpty else { return nil }
                        return ChecklistItem(
                            id: UUID().uuidString,
                            taskId: tid,
                            title: cleanChkTitle,
                            isCompleted: chkSpec.isCompleted,
                            sortOrder: Double((cIdx + 1) * 100),
                            createdAt: now,
                            updatedAt: now
                        )
                    }

                    let rawAssignee = spec.assigneeId?.trimmingCharacters(in: .whitespacesAndNewlines)
                    let normalizedAssignee = (rawAssignee?.isEmpty == false) ? rawAssignee : nil
                    let rawTaskWorkspace = (spec.workspaceId ?? normalizedProjWorkspace)?
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                    let normalizedTaskWorkspace = (rawTaskWorkspace?.isEmpty == false) ? rawTaskWorkspace : nil

                    let task = TaskItem(
                        id: tid,
                        title: cleanTitle,
                        notes: spec.notes,
                        status: spec.status,
                        scheduleState: scheduleState,
                        whenDate: spec.whenDate,
                        deadline: spec.deadline,
                        completedAt: resolvedCompletedAt,
                        projectId: newProjId,
                        headingId: headingId,
                        areaId: validatedAreaId,
                        sortOrder: nextTaskOrder,
                        isPinned: spec.isPinned,
                        isMilestone: spec.isMilestone,
                        effortLevel: spec.effortLevel?.rawValue,
                        workspaceId: normalizedTaskWorkspace,
                        assigneeId: normalizedAssignee,
                        checklistItems: checklists,
                        createdAt: now,
                        updatedAt: now
                    )
                    nextTaskOrder += 1000.0
                    try upsertTaskLocked(task, tagIds: resolvedTagIds, isDirty: true)

                    for chk in checklists {
                        try upsertChecklistItemLocked(chk, isDirty: true)
                    }
                    importedTaskCount += 1
                }

                for spec in parsed.ungroupedTasks {
                    try insertImportedTask(spec, headingId: nil)
                }

                for (idx, phase) in parsed.phases.enumerated() {
                    let cleanPhaseTitle = phase.title.trimmingCharacters(in: .whitespacesAndNewlines)
                    let isPlaceholderPhase = ExternalProjectCodec.isUngroupedSectionName(cleanPhaseTitle)
                    if cleanPhaseTitle.isEmpty || isPlaceholderPhase {
                        for spec in phase.tasks {
                            try insertImportedTask(spec, headingId: nil)
                        }
                    } else {
                        let heading = Heading(
                            id: UUID().uuidString,
                            projectId: newProjId,
                            title: cleanPhaseTitle,
                            sortOrder: Double((idx + 1) * 1000),
                            isArchived: false,
                            startDate: phase.startDate,
                            endDate: phase.endDate,
                            createdAt: now,
                            updatedAt: now
                        )
                        try upsertHeadingLocked(heading, isDirty: true)
                        for spec in phase.tasks {
                            try insertImportedTask(spec, headingId: heading.id)
                        }
                    }
                }

                let persistedProject = try fetchProjectByIdLocked(newProjId) ?? project
                return (persistedProject, importedTaskCount)
            }
        }
        notifyMutation()
        return (project: result.0, importedTaskCount: result.1)
    }

    public func exportProjectExternal(
        projectId: String,
        format: ExternalExportFormat,
        calendar: Calendar = .current
    ) throws -> ExternalExportedFile {
        try queue.sync {
            guard let project = try fetchProjectByIdLocked(projectId), !project.isDeleted else {
                throw ThingsDatabaseError.notFound(projectId)
            }

            let escapedPid = projectId.replacingOccurrences(of: "'", with: "''")
            let headings = try fetchHeadingsWhereLocked(
                "project_id = '\(escapedPid)' AND is_deleted = 0 AND is_archived = 0"
            )
            let activeTags = try fetchActiveTagsLocked()
            let tagsById = Dictionary(activeTags.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            let checklistsByTask = try fetchAllActiveChecklistItemsByTaskLocked()
            let tagIdsByTask = try fetchAllTaskTagIdsLocked()

            let tasks = try fetchTasksWhereLocked(
                whereClause: "project_id = ? AND is_deleted = 0",
                bind: { stmt in self.bindText(stmt, 1, projectId) },
                orderBy: "sort_order ASC, created_at ASC",
                tagsById: tagsById,
                tagIdsByTask: tagIdsByTask,
                checklistsByTask: checklistsByTask
            )

            return try ExternalProjectCodec.exportProject(
                project: project,
                headings: headings,
                tasks: tasks,
                format: format,
                tagsById: tagsById,
                calendar: calendar
            )
        }
    }

    // MARK: - Full `.tbbackup` Export & Atomic Restore

    public func buildBackupPayload(preferences: AppPreferences, appVersionName: String = AppUpdateService.currentVersion) throws -> ThingsbookBackupPayload {
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
                    lastReviewedAt: $0.lastReviewedAt,
                    sortOrder: $0.sortOrder,
                    tagIdsJson: encodeStringArray($0.tagIds),
                    workspaceId: $0.workspaceId,
                    viewMode: $0.viewMode,
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
                    startDate: $0.startDate,
                    endDate: $0.endDate,
                    createdAt: $0.createdAt,
                    updatedAt: $0.updatedAt
                )
            }

            let tags = try fetchActiveTagsLocked()
            let tagsById = Dictionary(tags.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
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
                    scheduledStartMinutes: t.scheduledStartMinutes,
                    durationMinutes: t.durationMinutes,
                    actualMinutes: t.actualMinutes,
                    completedAt: t.completedAt,
                    projectId: t.projectId,
                    headingId: t.headingId,
                    areaId: t.areaId,
                    recurrenceRuleJson: encodeRecurrence(t.recurrenceRule),
                    sortOrder: t.sortOrder,
                    isPinned: t.isPinned,
                    isMilestone: t.isMilestone,
                    effortLevel: t.effortLevel,
                    blockedByJson: encodeStringArray(t.blockedByTaskIds),
                    workspaceId: t.workspaceId,
                    assigneeId: t.assigneeId,
                    tagIdsJson: encodeStringArray(t.tagIds),
                    createdAt: t.createdAt,
                    updatedAt: t.updatedAt,
                    isInProgress: t.status == .open && t.isInProgress
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

            let activeAttachments = try fetchAttachmentsWhereLocked("is_deleted = 0")
            let attachmentDtos = activeAttachments.map { att in
                BackupAttachmentDto(
                    id: att.id,
                    taskId: att.taskId,
                    projectId: att.projectId,
                    fileName: att.fileName,
                    mimeType: att.mimeType,
                    sizeBytes: att.sizeBytes,
                    dataBase64: readAttachmentBase64Locked(att, maxRawBytes: nil),
                    createdAt: att.createdAt,
                    updatedAt: att.updatedAt
                )
            }

            let templateDtos = try fetchActiveProjectTemplatesLocked().map { tmpl in
                BackupProjectTemplateDto(
                    id: tmpl.id,
                    title: tmpl.title,
                    notes: tmpl.notes,
                    defaultAreaId: tmpl.defaultAreaId,
                    tagIdsJson: encodeStringArray(tmpl.tagIds),
                    blueprintJson: tmpl.blueprintJson,
                    sortOrder: tmpl.sortOrder,
                    createdAt: tmpl.createdAt,
                    updatedAt: tmpl.updatedAt
                )
            }

            let encodedPerspectivesJson: String? = {
                let enc = JSONEncoder()
                enc.outputFormatting = [.sortedKeys]
                guard let data = try? enc.encode(preferences.savedPerspectives),
                      let s = String(data: data, encoding: .utf8) else {
                    return nil
                }
                return s
            }()

            let prefsDto = BackupPreferencesDto(
                sidebarVisible: preferences.sidebarVisible,
                dailyDigestEnabled: preferences.dailyDigestEnabled,
                dailyDigestHour: preferences.dailyDigestHour,
                dailyDigestMinute: preferences.dailyDigestMinute,
                languageTag: preferences.language == .system ? "" : preferences.language.rawValue,
                calendarEventsEnabled: preferences.calendarEventsEnabled,
                selectedCalendarIds: preferences.selectedCalendarIds,
                groupTodayByProjectOrArea: preferences.groupTodayByProjectOrArea,
                includeBetaUpdates: preferences.includeBetaUpdates,
                tagSortMode: preferences.tagSortMode,
                viewSortMode: preferences.viewSortMode,
                savedPerspectivesJson: encodedPerspectivesJson,
                workdayStartMinutes: preferences.workdayStartMinutes,
                workdayEndMinutes: preferences.workdayEndMinutes
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
                attachments: attachmentDtos,
                projectTemplates: templateDtos,
                preferences: prefsDto
            )
        }
    }

    public func importSnapshotReplacingAll(_ payload: ThingsbookBackupPayload) throws {
        try restoreBackupPayload(payload)
    }

    public func restoreBackupPayload(_ payload: ThingsbookBackupPayload) throws {
        try queue.sync {
            // SQLite ignores PRAGMA foreign_keys = OFF inside an active transaction — must execute BEFORE BEGIN.
            try execLocked("PRAGMA foreign_keys = OFF;")
            defer { try? execLocked("PRAGMA foreign_keys = ON;") }

            try inTransactionLocked {
                let existingAtts = (try? fetchAttachmentsWhereLocked("1 = 1")) ?? []
                for att in existingAtts {
                    deleteAttachmentFileLocked(relativePath: att.localRelativePath)
                }

                try execLocked("""
                    DELETE FROM detached_project_tasks;
                    DELETE FROM attachments;
                    DELETE FROM checklist_items;
                    DELETE FROM task_tags;
                    DELETE FROM project_tags;
                    DELETE FROM project_templates;
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

                let orderedTags = Self.sortParentFirst(payload.tags, idOf: \.id, parentIdOf: \.parentTagId)
                for dto in orderedTags {
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
                        lastReviewedAt: dto.lastReviewedAt,
                        sortOrder: dto.sortOrder,
                        tagIds: mergedTags,
                        workspaceId: dto.workspaceId,
                        viewMode: dto.viewMode,
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
                        startDate: dto.startDate,
                        endDate: dto.endDate,
                        createdAt: dto.createdAt,
                        updatedAt: dto.updatedAt
                    )
                    try upsertHeadingLocked(heading, isDirty: true)
                }

                var taskRefsByTask = Dictionary(grouping: payload.taskTagRefs, by: \.taskId)
                for dto in payload.tasks {
                    let explicitRefs = (taskRefsByTask.removeValue(forKey: dto.id) ?? []).map(\.tagId)
                    let mergedTags = Array(Set(explicitRefs + decodeStringArray(dto.tagIdsJson))).filter { validTagIds.contains($0) }
                    let restoredBlockers = decodeStringArray(dto.blockedByJson).filter { $0 != dto.id && validTaskIds.contains($0) }
                    let task = TaskItem(
                        id: dto.id,
                        title: dto.title,
                        notes: dto.notes,
                        status: TaskStatus.normalized(dto.status),
                        scheduleState: ScheduleState.normalized(dto.scheduleState, fallback: .inbox),
                        whenDate: dto.whenDate,
                        deadline: dto.deadline,
                        reminderTime: dto.reminderTime,
                        scheduledStartMinutes: dto.scheduledStartMinutes,
                        durationMinutes: dto.durationMinutes,
                        actualMinutes: dto.actualMinutes.flatMap { $0 > 0 ? min(48_000, $0) : nil },
                        completedAt: dto.completedAt,
                        projectId: dto.projectId.flatMap { validProjectIds.contains($0) ? $0 : nil },
                        headingId: dto.headingId.flatMap { validHeadingIds.contains($0) ? $0 : nil },
                        areaId: dto.areaId.flatMap { validAreaIds.contains($0) ? $0 : nil },
                        recurrenceRule: decodeRecurrence(dto.recurrenceRuleJson),
                        sortOrder: dto.sortOrder,
                        isPinned: dto.isPinned,
                        isMilestone: dto.isMilestone,
                        effortLevel: TaskEffortLevel.normalized(dto.effortLevel)?.rawValue,
                        blockedByTaskIds: restoredBlockers,
                        workspaceId: dto.workspaceId,
                        assigneeId: dto.assigneeId,
                        createdAt: dto.createdAt,
                        updatedAt: dto.updatedAt,
                        isInProgress: TaskStatus.normalized(dto.status) == .open && dto.isInProgress
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

                for dto in payload.attachments {
                    let validTask = dto.taskId.flatMap { validTaskIds.contains($0) ? $0 : nil }
                    let validProj = dto.projectId.flatMap { validProjectIds.contains($0) ? $0 : nil }
                    guard validTask != nil || validProj != nil else { continue }
                    var relPath = ""
                    if !dto.dataBase64.isEmpty, let decodedData = Data(base64Encoded: dto.dataBase64, options: .ignoreUnknownCharacters) {
                        relPath = (try? writeAttachmentDataLocked(id: dto.id, fileName: dto.fileName, data: decodedData)) ?? ""
                    }
                    let att = TaskAttachment(
                        id: dto.id,
                        taskId: validTask,
                        projectId: validProj,
                        fileName: dto.fileName,
                        mimeType: dto.mimeType,
                        sizeBytes: dto.sizeBytes,
                        localRelativePath: relPath,
                        createdAt: dto.createdAt,
                        updatedAt: dto.updatedAt,
                        isDeleted: false
                    )
                    try upsertAttachmentLocked(att, isDirty: true)
                }

                for dto in payload.projectTemplates {
                    let tmpl = ProjectTemplate(
                        id: dto.id,
                        title: dto.title,
                        notes: dto.notes,
                        defaultAreaId: dto.defaultAreaId.flatMap { validAreaIds.contains($0) ? $0 : nil },
                        tagIds: decodeStringArray(dto.tagIdsJson).filter { validTagIds.contains($0) },
                        blueprintJson: dto.blueprintJson,
                        sortOrder: dto.sortOrder,
                        createdAt: dto.createdAt,
                        updatedAt: dto.updatedAt,
                        isDeleted: false,
                        isDirty: true
                    )
                    try upsertProjectTemplateLocked(tmpl, isDirty: true)
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
            let tagsById = Dictionary(allTags.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
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
            let attachments = try fetchAttachmentsWhereLocked("is_dirty = 1")
            let projectTemplates = try fetchProjectTemplatesWhereLocked("is_dirty = 1")
            return SyncDirtyBatch(
                tags: tags,
                areas: areas,
                projects: projects,
                headings: headings,
                tasks: tasks,
                checklistItems: checklistItems,
                attachments: attachments,
                projectTemplates: projectTemplates
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
                for att in batch.attachments { try clearDirtyIfUnchangedLocked(table: "attachments", id: att.id, maxUpdatedAt: att.updatedAt) }
                for tmpl in batch.projectTemplates { try clearDirtyIfUnchangedLocked(table: "project_templates", id: tmpl.id, maxUpdatedAt: tmpl.updatedAt) }
            }
        }
    }

    private static let syncTableNames: [String] = [
        "tags",
        "areas",
        "projects",
        "headings",
        "tasks",
        "checklist_items",
        "attachments",
        "project_templates"
    ]

    private func clearDirtyIfUnchangedLocked(table: String, id: String, maxUpdatedAt: Int64) throws {
        let stmt = try prepareLocked("UPDATE \(table) SET is_dirty = 0 WHERE id = ? AND updated_at <= ?;")
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, id)
        bindInt64(stmt, 2, maxUpdatedAt)
        _ = sqlite3_step(stmt)
    }

    public func hasStaleOfflineDirtyRecords(olderThanMillis: Int64 = 30_000) throws -> Bool {
        try queue.sync {
            let cutoff = nowMillis() - max(1_000, olderThanMillis)
            for table in Self.syncTableNames {
                let stmt = try prepareLocked("SELECT 1 FROM \(table) WHERE is_dirty = 1 AND updated_at < ? LIMIT 1;")
                defer { sqlite3_finalize(stmt) }
                bindInt64(stmt, 1, cutoff)
                if sqlite3_step(stmt) == SQLITE_ROW {
                    return true
                }
            }
            return false
        }
    }

    @discardableResult
    public func restampStaleOfflineDirtyRecords(
        olderThanMillis: Int64 = 30_000,
        restampTimestamp: Int64? = nil
    ) throws -> Int {
        try queue.sync {
            let pushNow = restampTimestamp ?? nowMillis()
            let cutoff = pushNow - max(1_000, olderThanMillis)
            var updatedRows = 0
            try inTransactionLocked {
                for table in Self.syncTableNames {
                    let stmt = try prepareLocked("UPDATE \(table) SET updated_at = ? WHERE is_dirty = 1 AND updated_at < ?;")
                    defer { sqlite3_finalize(stmt) }
                    bindInt64(stmt, 1, pushNow)
                    bindInt64(stmt, 2, cutoff)
                    if sqlite3_step(stmt) == SQLITE_DONE {
                        updatedRows += Int(sqlite3_changes(db))
                    }
                }
            }
            return updatedRows
        }
    }

    public func missingContainerFlags() throws -> (hasOrphanProject: Bool, hasOrphanArea: Bool, hasOrphanHeading: Bool) {
        try queue.sync {
            func exists(_ sql: String) -> Bool {
                guard let stmt = try? prepareLocked(sql) else { return false }
                defer { sqlite3_finalize(stmt) }
                return sqlite3_step(stmt) == SQLITE_ROW
            }
            let orphanProject = exists("""
                SELECT 1 FROM tasks
                WHERE is_deleted = 0 AND project_id IS NOT NULL AND project_id != ''
                  AND project_id NOT IN (SELECT id FROM projects)
                LIMIT 1;
            """) || exists("""
                SELECT 1 FROM headings
                WHERE is_deleted = 0 AND project_id != ''
                  AND project_id NOT IN (SELECT id FROM projects)
                LIMIT 1;
            """)
            let orphanArea = exists("""
                SELECT 1 FROM projects
                WHERE is_deleted = 0 AND area_id IS NOT NULL AND area_id != ''
                  AND area_id NOT IN (SELECT id FROM areas)
                LIMIT 1;
            """) || exists("""
                SELECT 1 FROM tasks
                WHERE is_deleted = 0 AND area_id IS NOT NULL AND area_id != ''
                  AND area_id NOT IN (SELECT id FROM areas)
                LIMIT 1;
            """)
            let orphanHeading = exists("""
                SELECT 1 FROM tasks
                WHERE is_deleted = 0 AND heading_id IS NOT NULL AND heading_id != ''
                  AND heading_id NOT IN (SELECT id FROM headings)
                LIMIT 1;
            """)
            return (orphanProject, orphanArea, orphanHeading)
        }
    }

    @discardableResult
    public func applyRemoteDeltas(_ deltas: RemoteDeltaBatch) throws -> Int64 {
        var maxTimestamp: Int64 = 0
        var appliedCount = 0
        var didDeduplicate = false
        var clampedMax: Int64 = 0

        try queue.sync {
            // SQLite ignores PRAGMA foreign_keys = OFF inside an active transaction — must execute BEFORE BEGIN.
            try execLocked("PRAGMA foreign_keys = OFF;")
            defer { try? execLocked("PRAGMA foreign_keys = ON;") }

            try inTransactionLocked {
                let batchNow = self.nowMillis()
                let maxAllowed = batchNow + 60_000
                var tagRemap: [String: String] = [:]

                let orderedTags = Self.sortParentFirst(deltas.tags, idOf: \.id, parentIdOf: \.parentTagId)
                for remote in orderedTags {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    if shouldApplyRemoteLocked(table: "tags", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        var toSave = remote
                        toSave.updatedAt = min(remote.updatedAt, maxAllowed)
                        if let pid = toSave.parentTagId {
                            let mappedPid = Self.resolveRemappedTagId(pid, using: tagRemap)
                            toSave.parentTagId = (mappedPid == toSave.id) ? nil : mappedPid
                        }
                        let mergedCollision = try upsertTagLocked(toSave, isDirty: false, tagRemap: &tagRemap)
                        if mergedCollision {
                            didDeduplicate = true
                        }
                        if toSave.isDeleted && tagRemap[toSave.id] == nil {
                            try cleanDeletedTagReferencesLocked(tagId: toSave.id, now: toSave.updatedAt, markParentsDirty: false)
                        }
                        appliedCount += 1
                    }
                }

                for remote in deltas.areas {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    if shouldApplyRemoteLocked(table: "areas", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        var toSave = remote
                        toSave.updatedAt = min(remote.updatedAt, maxAllowed)
                        let remappedTags = Self.remapTagIdList(toSave.tagIds, using: tagRemap)
                        toSave.tagIds = remappedTags.ids
                        let markDirty = remappedTags.didRemap && !toSave.isDeleted
                        if markDirty {
                            toSave.updatedAt = max(batchNow, toSave.updatedAt + 1)
                            didDeduplicate = true
                        }
                        try upsertAreaLocked(toSave, isDirty: markDirty)
                        appliedCount += 1
                        if toSave.isDeleted {
                            appliedCount += try detachLocalChildrenForDeletedAreaLocked(areaId: toSave.id)
                        }
                    } else if remote.isDeleted {
                        appliedCount += try detachLocalChildrenForDeletedAreaLocked(areaId: remote.id)
                    }
                }

                for remote in deltas.projects {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    let existingLocal = try? fetchProjectByIdLocked(remote.id)
                    let mergedLastReviewed: Int64? = remote.isDeleted
                        ? remote.lastReviewedAt
                        : Self.mergeMonotonicReviewTimestamp(existingLocal?.lastReviewedAt, remote.lastReviewedAt)
                    if shouldApplyRemoteLocked(table: "projects", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        var toSave = remote
                        toSave.updatedAt = min(remote.updatedAt, maxAllowed)
                        toSave.lastReviewedAt = mergedLastReviewed
                        if let existingLocal, !toSave.isDeleted, deltas.projectOmittedV30Ids.contains(remote.id) {
                            if toSave.workspaceId == nil { toSave.workspaceId = existingLocal.workspaceId }
                            if toSave.viewMode == nil { toSave.viewMode = existingLocal.viewMode }
                        }
                        let remappedTags = Self.remapTagIdList(toSave.tagIds, using: tagRemap)
                        toSave.tagIds = remappedTags.ids
                        let markDirty = remappedTags.didRemap && !toSave.isDeleted
                        if markDirty {
                            toSave.updatedAt = max(batchNow, toSave.updatedAt + 1)
                            didDeduplicate = true
                        }
                        try upsertProjectLocked(toSave, isDirty: markDirty)
                        appliedCount += 1
                        if toSave.isDeleted {
                            appliedCount += try detachLocalChildrenForDeletedProjectLocked(projectId: toSave.id)
                        }
                    } else if remote.isDeleted {
                        appliedCount += try detachLocalChildrenForDeletedProjectLocked(projectId: remote.id)
                    } else if let existingLocal,
                              !existingLocal.isDeleted,
                              let mergedLastReviewed,
                              mergedLastReviewed > (existingLocal.lastReviewedAt ?? 0) {
                        let stmt = try prepareLocked("UPDATE projects SET last_reviewed_at = ? WHERE id = ?;")
                        defer { sqlite3_finalize(stmt) }
                        bindInt64(stmt, 1, mergedLastReviewed)
                        bindText(stmt, 2, remote.id)
                        if sqlite3_step(stmt) == SQLITE_DONE {
                            appliedCount += Int(sqlite3_changes(db))
                        }
                    }
                }

                for remote in deltas.headings {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    if shouldApplyRemoteLocked(table: "headings", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        var toSave = remote
                        toSave.updatedAt = min(remote.updatedAt, maxAllowed)
                        if !toSave.isDeleted && (deltas.headingOmittedStartDateIds.contains(remote.id) || deltas.headingOmittedEndDateIds.contains(remote.id)) {
                            let safeId = remote.id.replacingOccurrences(of: "'", with: "''")
                            if let existingLocal = try? fetchHeadingsWhereLocked("id = '\(safeId)'").first,
                               !existingLocal.isDeleted {
                                if deltas.headingOmittedStartDateIds.contains(remote.id) {
                                    toSave.startDate = existingLocal.startDate
                                }
                                if deltas.headingOmittedEndDateIds.contains(remote.id) {
                                    toSave.endDate = existingLocal.endDate
                                }
                            }
                        }
                        try upsertHeadingLocked(toSave, isDirty: false)
                        appliedCount += 1
                        if toSave.isDeleted {
                            appliedCount += try detachLocalChildrenForDeletedHeadingLocked(headingId: toSave.id)
                        }
                    } else if remote.isDeleted {
                        appliedCount += try detachLocalChildrenForDeletedHeadingLocked(headingId: remote.id)
                    }
                }

                if !deltas.tasks.isEmpty {
                    let (localOpenRecurring, _) = (try? fetchOpenRecurringTasksWithRawTagIdsLocked()) ?? ([], [:])
                    let allRawTaskTagIds = (try? fetchAllTaskTagIdsLocked()) ?? [:]

                    for remote in deltas.tasks {
                        maxTimestamp = max(maxTimestamp, remote.updatedAt)
                        let clampedRemote = min(remote.updatedAt, maxAllowed)
                        let existingLocal = try? fetchTaskByIdLocked(remote.id)

                        // Guard against v1.8 startup rollover bumping updatedAt on a recurring parent that was
                        // already COMPLETED on v2.x (which spawned a separate OPEN occurrence in the series).
                        if let existingLocal,
                           !existingLocal.isDeleted,
                           existingLocal.status == .completed,
                           existingLocal.recurrenceRule != nil,
                           !remote.isDeleted,
                           remote.status == .open {
                            let hasLocalOpenSibling = localOpenRecurring.contains {
                                $0.id != remote.id && Self.areSameRecurringSeries(remote, $0)
                            }
                            let hasBatchOpenSibling = deltas.tasks.contains {
                                $0.id != remote.id && !$0.isDeleted && $0.status == .open && Self.areSameRecurringSeries(remote, $0)
                            }
                            if hasLocalOpenSibling || hasBatchOpenSibling {
                                if clampedRemote >= existingLocal.updatedAt {
                                    var healed = existingLocal
                                    healed.updatedAt = max(batchNow, clampedRemote + 1, existingLocal.updatedAt + 1)
                                    let existingTags = Self.remapTagIdList(
                                        allRawTaskTagIds[healed.id] ?? healed.tagIds,
                                        using: tagRemap
                                    ).ids
                                    try upsertTaskLocked(healed, tagIds: existingTags, isDirty: true)
                                    appliedCount += 1
                                    didDeduplicate = true
                                }
                                continue
                            }
                        }

                        if shouldApplyRemoteLocked(table: "tasks", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                            var toSave = remote
                            toSave.updatedAt = clampedRemote
                            if let existingLocal,
                               !toSave.isDeleted,
                               deltas.taskOmittedTimeblockTaskIds.contains(remote.id) {
                                if toSave.scheduledStartMinutes == nil,
                                   (toSave.scheduleState == .today || toSave.scheduleState == .thisEvening || toSave.scheduleState == .upcoming) {
                                    toSave.scheduledStartMinutes = existingLocal.scheduledStartMinutes
                                }
                                if toSave.durationMinutes == nil {
                                    toSave.durationMinutes = existingLocal.durationMinutes
                                }
                            }
                            if let existingLocal,
                               !toSave.isDeleted,
                               deltas.taskOmittedPinTaskIds.contains(remote.id) {
                                toSave.isPinned = existingLocal.isPinned
                            }
                            if let existingLocal,
                               !toSave.isDeleted,
                               deltas.taskOmittedMilestoneTaskIds.contains(remote.id) {
                                toSave.isMilestone = existingLocal.isMilestone
                            }
                            if let existingLocal,
                               !toSave.isDeleted,
                               deltas.taskOmittedV30TaskIds.contains(remote.id) {
                                if toSave.effortLevel == nil {
                                    toSave.effortLevel = existingLocal.effortLevel
                                }
                                if toSave.blockedByTaskIds.isEmpty {
                                    toSave.blockedByTaskIds = existingLocal.blockedByTaskIds
                                }
                                if toSave.workspaceId == nil {
                                    toSave.workspaceId = existingLocal.workspaceId
                                }
                                if toSave.assigneeId == nil {
                                    toSave.assigneeId = existingLocal.assigneeId
                                }
                            }
                            if let existingLocal,
                               !toSave.isDeleted,
                               deltas.taskOmittedActualMinutesIds.contains(remote.id) {
                                if toSave.actualMinutes == nil {
                                    toSave.actualMinutes = existingLocal.actualMinutes
                                }
                            }
                            if let existingLocal,
                               !toSave.isDeleted,
                               toSave.status == .open,
                               deltas.taskOmittedInProgressIds.contains(remote.id) {
                                toSave.isInProgress = existingLocal.isInProgress
                            }
                            if toSave.status != .open || toSave.isDeleted {
                                toSave.isInProgress = false
                            }
                            let rawTags = deltas.taskTagIdsByTaskId[remote.id] ?? remote.tagIds
                            let remappedTags = Self.remapTagIdList(rawTags, using: tagRemap)
                            let markDirty = remappedTags.didRemap && !toSave.isDeleted
                            if markDirty {
                                toSave.updatedAt = max(batchNow, toSave.updatedAt + 1)
                                didDeduplicate = true
                            }
                            try upsertTaskLocked(toSave, tagIds: remappedTags.ids, isDirty: markDirty)
                            appliedCount += 1
                        }
                    }
                }

                for remote in deltas.projectTemplates {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    if shouldApplyRemoteLocked(table: "project_templates", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        var toSave = remote
                        toSave.updatedAt = min(remote.updatedAt, maxAllowed)
                        let remappedTags = Self.remapTagIdList(toSave.tagIds, using: tagRemap)
                        toSave.tagIds = remappedTags.ids
                        let markDirty = remappedTags.didRemap && !toSave.isDeleted
                        if markDirty {
                            toSave.updatedAt = max(batchNow, toSave.updatedAt + 1)
                            didDeduplicate = true
                        }
                        try upsertProjectTemplateLocked(toSave, isDirty: markDirty)
                        appliedCount += 1
                    }
                }

                // Also detach any newly-arrived tasks/templates referencing containers tombstoned locally or in this batch
                appliedCount += try detachChildrenOfDeletedContainersLocked()

                for remote in deltas.checklistItems {
                    maxTimestamp = max(maxTimestamp, remote.updatedAt)
                    if shouldApplyRemoteLocked(table: "checklist_items", id: remote.id, remoteUpdatedAt: remote.updatedAt) {
                        var toSave = remote
                        toSave.updatedAt = min(remote.updatedAt, maxAllowed)
                        try upsertChecklistItemLocked(toSave, isDirty: false)
                        appliedCount += 1
                    }
                }

                for remoteDelta in deltas.attachments {
                    var att = remoteDelta.attachment
                    maxTimestamp = max(maxTimestamp, att.updatedAt)
                    let existingPath = (try? fetchAttachmentsWhereLocked("id = '\(att.id.replacingOccurrences(of: "'", with: "''"))'").first?.localRelativePath) ?? ""
                    let localBytesMissing = !att.isDeleted
                        && !remoteDelta.dataBase64.isEmpty
                        && (existingPath.isEmpty || !FileManager.default.fileExists(atPath: baseSupportDirURL.appendingPathComponent(existingPath).path))
                    if shouldApplyRemoteLocked(table: "attachments", id: att.id, remoteUpdatedAt: att.updatedAt) || localBytesMissing {
                        att.updatedAt = min(att.updatedAt, maxAllowed)
                        if att.isDeleted {
                            deleteAttachmentFileLocked(relativePath: existingPath)
                            att.localRelativePath = ""
                        } else if !remoteDelta.dataBase64.isEmpty,
                                  let decoded = Data(base64Encoded: remoteDelta.dataBase64, options: .ignoreUnknownCharacters) {
                            if !existingPath.isEmpty {
                                deleteAttachmentFileLocked(relativePath: existingPath)
                            }
                            att.localRelativePath = (try? writeAttachmentDataLocked(id: att.id, fileName: att.fileName, data: decoded)) ?? ""
                        } else if !existingPath.isEmpty {
                            att.localRelativePath = existingPath
                        }
                        try upsertAttachmentLocked(att, isDirty: false)
                        appliedCount += 1
                    }
                }

                if !deltas.tasks.isEmpty {
                    let deduped = try deduplicateOpenRecurringTasksLocked(nowMillis: self.nowMillis())
                    if deduped > 0 {
                        appliedCount += deduped
                        didDeduplicate = true
                    }
                }
            }
            if appliedCount > 0 {
                rebuildFtsLocked()
            }
            self.lastRemoteAppliedCount = appliedCount
            let maxAllowed = nowMillis() + 60_000
            clampedMax = maxTimestamp > 0 ? min(maxTimestamp, maxAllowed) : 0
        }

        if didDeduplicate {
            notifyMutation()
        } else if appliedCount > 0 {
            if let onRemoteSyncApplied {
                onRemoteSyncApplied()
            } else {
                notifyMutation()
            }
        }
        return clampedMax
    }

    public static func mergeMonotonicReviewTimestamp(_ local: Int64?, _ remote: Int64?) -> Int64? {
        let l = (local ?? 0) > 0 ? local : nil
        let r = (remote ?? 0) > 0 ? remote : nil
        if let l, let r { return max(l, r) }
        return l ?? r
    }

    private static func sha256LowerHex(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    public static func formatDeterministicUuidFromSha256Hex(_ sha256HexDigest: String) -> String {
        let h = Array(sha256HexDigest.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())
        guard h.count >= 32 else { return UUID().uuidString.lowercased() }
        let p1 = String(h[0..<8])
        let p2 = String(h[8..<12])
        let p3 = "5" + String(h[13..<16])
        let p4 = "a" + String(h[17..<20])
        let p5 = String(h[20..<32])
        return "\(p1)-\(p2)-\(p3)-\(p4)-\(p5)"
    }

    public static func deriveRecurringNextTaskId(completedTaskId: String, nextWhenYyyyMmDd: String) -> String {
        let seed = "thingsbook-rec-next-v1:\(completedTaskId.trimmingCharacters(in: .whitespacesAndNewlines)):\(nextWhenYyyyMmDd.trimmingCharacters(in: .whitespacesAndNewlines))"
        return formatDeterministicUuidFromSha256Hex(sha256LowerHex(seed))
    }

    public static func deriveRecurringChecklistItemId(nextTaskId: String, sourceChecklistId: String) -> String {
        let seed = "thingsbook-rec-chk-v1:\(nextTaskId.trimmingCharacters(in: .whitespacesAndNewlines)):\(sourceChecklistId.trimmingCharacters(in: .whitespacesAndNewlines))"
        return formatDeterministicUuidFromSha256Hex(sha256LowerHex(seed))
    }

    public struct RecurringWinnerEnrichment: Sendable, Equatable {
        public let winnerId: String
        public var areaId: String?
        public var headingId: String?
        public var notes: String?
        public var tagIds: [String]?
        public var scheduledStartMinutes: Int?
        public var durationMinutes: Int?
        public var deadline: Int64?
        public var reminderTime: Int64?
        public let minUpdatedAt: Int64

        public init(
            winnerId: String,
            areaId: String? = nil,
            headingId: String? = nil,
            notes: String? = nil,
            tagIds: [String]? = nil,
            scheduledStartMinutes: Int? = nil,
            durationMinutes: Int? = nil,
            deadline: Int64? = nil,
            reminderTime: Int64? = nil,
            minUpdatedAt: Int64
        ) {
            self.winnerId = winnerId
            self.areaId = areaId
            self.headingId = headingId
            self.notes = notes
            self.tagIds = tagIds
            self.scheduledStartMinutes = scheduledStartMinutes
            self.durationMinutes = durationMinutes
            self.deadline = deadline
            self.reminderTime = reminderTime
            self.minUpdatedAt = minUpdatedAt
        }
    }

    public struct RecurringDeduplicationPlan: Sendable, Equatable {
        public let loserIds: [String]
        public let winnerEnrichments: [RecurringWinnerEnrichment]

        public init(loserIds: [String], winnerEnrichments: [RecurringWinnerEnrichment]) {
            self.loserIds = loserIds
            self.winnerEnrichments = winnerEnrichments
        }
    }

    public static func normalizeRecurringTitleKey(_ title: String) -> String {
        title
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(whereSeparator: \.isWhitespace)
            .joined(separator: " ")
            .lowercased()
    }

    public static func canonicalizeRecurrenceRuleKey(_ rule: RecurrenceRule?) -> String? {
        guard let rule else { return nil }
        let interval = min(3650, max(1, rule.interval))
        let validDays = Array(Set(rule.daysOfWeek.filter { (1...7).contains($0) }))
            .sorted()
            .map(String.init)
            .joined(separator: ",")
        let dom = rule.dayOfMonth.map(String.init) ?? ""
        let endMs = rule.endDateEpochMillis.map(String.init) ?? ""
        return "\(rule.mode.rawValue)|\(rule.unit.rawValue)|\(interval)|\(validDays)|\(dom)|\(endMs)"
    }

    public static func areSameRecurringSeries(_ a: TaskItem, _ b: TaskItem) -> Bool {
        let titleA = normalizeRecurringTitleKey(a.title)
        let titleB = normalizeRecurringTitleKey(b.title)
        guard !titleA.isEmpty && titleA == titleB else { return false }
        guard let ruleA = canonicalizeRecurrenceRuleKey(a.recurrenceRule),
              let ruleB = canonicalizeRecurrenceRuleKey(b.recurrenceRule),
              ruleA == ruleB else {
            return false
        }
        let projA = (a.projectId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let projB = (b.projectId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if projA != projB { return false }
        if !projA.isEmpty { return true }
        let areaA = (a.areaId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let areaB = (b.areaId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if areaA.isEmpty || areaB.isEmpty { return true }
        return areaA == areaB
    }

    public static func buildRecurringSeriesKey(
        _ task: TaskItem,
        overrideTagIds: [String]? = nil
    ) -> String? {
        let _ = overrideTagIds
        guard !task.isDeleted && task.status == .open else { return nil }
        let normTitle = normalizeRecurringTitleKey(task.title)
        guard !normTitle.isEmpty else { return nil }
        guard let ruleKey = canonicalizeRecurrenceRuleKey(task.recurrenceRule) else { return nil }
        let proj = (task.projectId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let area = (task.areaId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let normContainer = !proj.isEmpty ? "P:\(proj)" : "A:\(area)"
        return "\(normTitle)\u{001F}\(normContainer)\u{001F}\(ruleKey)"
    }

    public static func compareRecurringTaskPriority(_ a: TaskItem, _ b: TaskItem) -> Bool {
        let aWhen = a.whenDate ?? -1
        let bWhen = b.whenDate ?? -1
        if aWhen != bWhen {
            return aWhen > bWhen
        }
        let aCreated = a.createdAt > 0 ? a.createdAt : Int64.max
        let bCreated = b.createdAt > 0 ? b.createdAt : Int64.max
        if aCreated != bCreated {
            return aCreated < bCreated
        }
        let aHasArea = !(a.areaId ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let bHasArea = !(b.areaId ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        if aHasArea != bHasArea {
            return aHasArea
        }
        let aHasTb = a.scheduledStartMinutes != nil
        let bHasTb = b.scheduledStartMinutes != nil
        if aHasTb != bHasTb {
            return aHasTb
        }
        return a.id < b.id
    }

    public static func planRecurringDeduplication(
        _ tasks: [TaskItem],
        overrideTagIdsByTaskId: [String: [String]] = [:]
    ) -> RecurringDeduplicationPlan {
        var orderedBaseKeys: [String] = []
        var baseBuckets: [String: [TaskItem]] = [:]

        for task in tasks {
            guard !task.isDeleted && task.status == .open else { continue }
            let normTitle = normalizeRecurringTitleKey(task.title)
            guard !normTitle.isEmpty else { continue }
            guard let ruleKey = canonicalizeRecurrenceRuleKey(task.recurrenceRule) else { continue }
            let proj = (task.projectId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let baseKey = "\(normTitle)\u{001F}\(ruleKey)\u{001F}\(proj)"
            if baseBuckets[baseKey] == nil {
                orderedBaseKeys.append(baseKey)
                baseBuckets[baseKey] = [task]
            } else {
                baseBuckets[baseKey]?.append(task)
            }
        }

        var finalGroups: [[TaskItem]] = []
        for baseKey in orderedBaseKeys {
            guard let bucket = baseBuckets[baseKey], bucket.count >= 2 else { continue }
            let proj = (bucket[0].projectId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if !proj.isEmpty {
                finalGroups.append(bucket)
                continue
            }
            let distinctExplicitAreas = Set(
                bucket.map { ($0.areaId ?? "").trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
            )
            if distinctExplicitAreas.count <= 1 {
                finalGroups.append(bucket)
            } else {
                var orderedAreaKeys: [String] = []
                var byArea: [String: [TaskItem]] = [:]
                for item in bucket {
                    let areaKey = (item.areaId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                    if byArea[areaKey] == nil {
                        orderedAreaKeys.append(areaKey)
                        byArea[areaKey] = [item]
                    } else {
                        byArea[areaKey]?.append(item)
                    }
                }
                for areaKey in orderedAreaKeys {
                    if let sub = byArea[areaKey], sub.count >= 2 {
                        finalGroups.append(sub)
                    }
                }
            }
        }

        var loserIds: [String] = []
        var winnerEnrichments: [RecurringWinnerEnrichment] = []

        for group in finalGroups where group.count >= 2 {
            let sorted = group.sorted(by: compareRecurringTaskPriority)
            let winner = sorted[0]
            let losers = Array(sorted.dropFirst())
            for loser in losers {
                loserIds.append(loser.id)
            }

            let maxUpdated = group.map(\.updatedAt).max() ?? 0
            var patch = RecurringWinnerEnrichment(
                winnerId: winner.id,
                minUpdatedAt: maxUpdated + 1
            )
            var changed = false

            let winProj = (winner.projectId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let winArea = (winner.areaId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if winProj.isEmpty && winArea.isEmpty {
                if let donorArea = losers.lazy
                    .map({ ($0.areaId ?? "").trimmingCharacters(in: .whitespacesAndNewlines) })
                    .first(where: { !$0.isEmpty }) {
                    patch.areaId = donorArea
                    changed = true
                }
            }
            let winHead = (winner.headingId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if !winProj.isEmpty && winHead.isEmpty {
                if let donorHead = losers.lazy
                    .map({ ($0.headingId ?? "").trimmingCharacters(in: .whitespacesAndNewlines) })
                    .first(where: { !$0.isEmpty }) {
                    patch.headingId = donorHead
                    changed = true
                }
            }
            if winner.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                if let donorNotes = losers.lazy
                    .map({ $0.notes.trimmingCharacters(in: .whitespacesAndNewlines) })
                    .first(where: { !$0.isEmpty }) {
                    patch.notes = donorNotes
                    changed = true
                }
            }

            func normalizedTags(for t: TaskItem) -> [String] {
                let src = overrideTagIdsByTaskId[t.id] ?? t.tagIds
                var seen = Set<String>()
                var out: [String] = []
                for raw in src {
                    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty && seen.insert(trimmed).inserted {
                        out.append(trimmed)
                    }
                }
                return out
            }

            let winTags = normalizedTags(for: winner)
            var unionSeen = Set(winTags)
            var unionTags = winTags
            for item in losers {
                for tId in normalizedTags(for: item) where unionSeen.insert(tId).inserted {
                    unionTags.append(tId)
                }
            }
            if unionTags.count > winTags.count {
                patch.tagIds = unionTags
                changed = true
            }

            if winner.scheduledStartMinutes == nil {
                if let donorTb = losers.first(where: { $0.scheduledStartMinutes != nil }),
                   let startMins = donorTb.scheduledStartMinutes {
                    patch.scheduledStartMinutes = startMins
                    patch.durationMinutes = winner.durationMinutes ?? donorTb.durationMinutes
                    changed = true
                }
            }
            if winner.deadline == nil {
                if let donorDl = losers.compactMap(\.deadline).first {
                    patch.deadline = donorDl
                    changed = true
                }
            }
            if winner.reminderTime == nil {
                if let donorRem = losers.compactMap(\.reminderTime).first {
                    patch.reminderTime = donorRem
                    changed = true
                }
            }

            if changed {
                winnerEnrichments.append(patch)
            }
        }

        return RecurringDeduplicationPlan(
            loserIds: loserIds,
            winnerEnrichments: winnerEnrichments
        )
    }

    public static func selectRecurringDuplicateLoserIds(
        _ tasks: [TaskItem],
        overrideTagIdsByTaskId: [String: [String]] = [:]
    ) -> [String] {
        planRecurringDeduplication(
            tasks,
            overrideTagIdsByTaskId: overrideTagIdsByTaskId
        ).loserIds
    }

    private static func sortParentFirst<T>(
        _ items: [T],
        idOf: (T) -> String,
        parentIdOf: (T) -> String?
    ) -> [T] {
        guard items.count > 1 else { return items }
        let batchIds = Set(items.map(idOf))
        var emittedIds = Set<String>()
        var remaining = items
        var ordered: [T] = []
        ordered.reserveCapacity(items.count)

        while !remaining.isEmpty {
            var nextRemaining: [T] = []
            var progressed = false
            for item in remaining {
                let parentId = parentIdOf(item)
                if parentId == nil || !batchIds.contains(parentId!) || emittedIds.contains(parentId!) {
                    ordered.append(item)
                    emittedIds.insert(idOf(item))
                    progressed = true
                } else {
                    nextRemaining.append(item)
                }
            }
            if !progressed {
                ordered.append(contentsOf: nextRemaining)
                break
            }
            remaining = nextRemaining
        }
        return ordered
    }

    private func detachLocalChildrenForDeletedAreaLocked(areaId: String) throws -> Int {
        var changes = 0
        let s1 = try prepareLocked("UPDATE projects SET area_id = NULL WHERE area_id = ?;")
        defer { sqlite3_finalize(s1) }
        bindText(s1, 1, areaId)
        if sqlite3_step(s1) == SQLITE_DONE {
            changes += Int(sqlite3_changes(db))
        }

        let s2 = try prepareLocked("UPDATE tasks SET area_id = NULL WHERE area_id = ?;")
        defer { sqlite3_finalize(s2) }
        bindText(s2, 1, areaId)
        if sqlite3_step(s2) == SQLITE_DONE {
            changes += Int(sqlite3_changes(db))
        }

        let s3 = try prepareLocked("UPDATE project_templates SET default_area_id = NULL WHERE default_area_id = ?;")
        defer { sqlite3_finalize(s3) }
        bindText(s3, 1, areaId)
        if sqlite3_step(s3) == SQLITE_DONE {
            changes += Int(sqlite3_changes(db))
        }
        return changes
    }

    private func detachLocalChildrenForDeletedProjectLocked(projectId: String) throws -> Int {
        let s = try prepareLocked("UPDATE tasks SET project_id = NULL, heading_id = NULL WHERE project_id = ? AND is_deleted = 0;")
        defer { sqlite3_finalize(s) }
        bindText(s, 1, projectId)
        if sqlite3_step(s) == SQLITE_DONE {
            return Int(sqlite3_changes(db))
        }
        return 0
    }

    private func detachLocalChildrenForDeletedHeadingLocked(headingId: String) throws -> Int {
        let s = try prepareLocked("UPDATE tasks SET heading_id = NULL WHERE heading_id = ? AND is_deleted = 0;")
        defer { sqlite3_finalize(s) }
        bindText(s, 1, headingId)
        if sqlite3_step(s) == SQLITE_DONE {
            return Int(sqlite3_changes(db))
        }
        return 0
    }

    private func detachChildrenOfDeletedContainersLocked() throws -> Int {
        var changes = 0
        let queries = [
            "UPDATE projects SET area_id = NULL WHERE area_id IS NOT NULL AND area_id IN (SELECT id FROM areas WHERE is_deleted = 1);",
            "UPDATE tasks SET area_id = NULL WHERE area_id IS NOT NULL AND area_id IN (SELECT id FROM areas WHERE is_deleted = 1);",
            "UPDATE project_templates SET default_area_id = NULL WHERE default_area_id IS NOT NULL AND default_area_id IN (SELECT id FROM areas WHERE is_deleted = 1);",
            "UPDATE tasks SET project_id = NULL, heading_id = NULL WHERE is_deleted = 0 AND project_id IS NOT NULL AND project_id IN (SELECT id FROM projects WHERE is_deleted = 1);",
            "UPDATE tasks SET heading_id = NULL WHERE is_deleted = 0 AND heading_id IS NOT NULL AND heading_id IN (SELECT id FROM headings WHERE is_deleted = 1);"
        ]
        for sql in queries {
            let stmt = try prepareLocked(sql)
            defer { sqlite3_finalize(stmt) }
            if sqlite3_step(stmt) == SQLITE_DONE {
                changes += Int(sqlite3_changes(db))
            }
        }
        return changes
    }

    private func shouldApplyRemoteLocked(table: String, id: String, remoteUpdatedAt: Int64) -> Bool {
        let clampedRemote = min(remoteUpdatedAt, nowMillis() + 60_000)
        guard let stmt = try? prepareLocked("SELECT updated_at, is_dirty FROM \(table) WHERE id = ?;") else { return true }
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, id)
        if sqlite3_step(stmt) == SQLITE_ROW {
            let localUpdated = sqlite3_column_int64(stmt, 0)
            let localIsDirty = sqlite3_column_int(stmt, 1) != 0
            if localIsDirty {
                return clampedRemote > localUpdated
            }
            return clampedRemote > localUpdated
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

    private static func compareDeterministicTagPriority(_ a: Tag, _ b: Tag) -> Bool {
        let aCreated = a.createdAt > 0 ? a.createdAt : Int64.max
        let bCreated = b.createdAt > 0 ? b.createdAt : Int64.max
        if aCreated != bCreated {
            return aCreated < bCreated
        }
        return a.id < b.id
    }

    private static func resolveRemappedTagId(_ id: String, using remap: [String: String]) -> String {
        guard !remap.isEmpty else { return id }
        var cur = id
        var hops = 0
        while let next = remap[cur], next != cur, hops < 8 {
            cur = next
            hops += 1
        }
        return cur
    }

    private static func remapTagIdList(
        _ tagIds: [String],
        using remap: [String: String]
    ) -> (ids: [String], didRemap: Bool) {
        guard !remap.isEmpty && !tagIds.isEmpty else { return (tagIds, false) }
        var didRemap = false
        var seen = Set<String>()
        var out: [String] = []
        for raw in tagIds {
            let mapped = resolveRemappedTagId(raw, using: remap)
            if mapped != raw {
                didRemap = true
            }
            if seen.insert(mapped).inserted {
                out.append(mapped)
            }
        }
        return (out, didRemap)
    }

    private func remapTagReferencesLocked(
        from loserId: String,
        to winnerId: String,
        now: Int64,
        markAffectedDirty: Bool
    ) throws {
        guard loserId != winnerId else { return }

        let insTaskTags = try prepareLocked("""
            INSERT OR IGNORE INTO task_tags (task_id, tag_id)
            SELECT task_id, ? FROM task_tags WHERE tag_id = ?;
        """)
        defer { sqlite3_finalize(insTaskTags) }
        bindText(insTaskTags, 1, winnerId)
        bindText(insTaskTags, 2, loserId)
        _ = sqlite3_step(insTaskTags)

        let delTaskTags = try prepareLocked("DELETE FROM task_tags WHERE tag_id = ?;")
        defer { sqlite3_finalize(delTaskTags) }
        bindText(delTaskTags, 1, loserId)
        _ = sqlite3_step(delTaskTags)

        let insProjTags = try prepareLocked("""
            INSERT OR IGNORE INTO project_tags (project_id, tag_id)
            SELECT project_id, ? FROM project_tags WHERE tag_id = ?;
        """)
        defer { sqlite3_finalize(insProjTags) }
        bindText(insProjTags, 1, winnerId)
        bindText(insProjTags, 2, loserId)
        _ = sqlite3_step(insProjTags)

        let delProjTags = try prepareLocked("DELETE FROM project_tags WHERE tag_id = ?;")
        defer { sqlite3_finalize(delProjTags) }
        bindText(delProjTags, 1, loserId)
        _ = sqlite3_step(delProjTags)

        if markAffectedDirty {
            let childStmt = try prepareLocked("""
                UPDATE tags SET parent_tag_id = ?, updated_at = CASE WHEN updated_at >= ? THEN updated_at + 1 ELSE ? END, is_dirty = 1
                WHERE parent_tag_id = ? AND id != ? AND is_deleted = 0;
            """)
            defer { sqlite3_finalize(childStmt) }
            bindText(childStmt, 1, winnerId)
            bindInt64(childStmt, 2, now)
            bindInt64(childStmt, 3, now)
            bindText(childStmt, 4, loserId)
            bindText(childStmt, 5, winnerId)
            _ = sqlite3_step(childStmt)
        } else {
            let childStmt = try prepareLocked("UPDATE tags SET parent_tag_id = ? WHERE parent_tag_id = ? AND id != ?;")
            defer { sqlite3_finalize(childStmt) }
            bindText(childStmt, 1, winnerId)
            bindText(childStmt, 2, loserId)
            bindText(childStmt, 3, winnerId)
            _ = sqlite3_step(childStmt)
        }

        for table in ["tasks", "projects", "areas", "project_templates"] {
            let selectStmt = try prepareLocked("SELECT id, tag_ids_json, updated_at FROM \(table) WHERE tag_ids_json LIKE ?;")
            defer { sqlite3_finalize(selectStmt) }
            bindText(selectStmt, 1, "%\(loserId)%")

            var updates: [(id: String, json: String, nextUpdated: Int64)] = []
            while sqlite3_step(selectStmt) == SQLITE_ROW {
                guard let rowId = colString(selectStmt, 0) else { continue }
                let existing = decodeStringArray(colString(selectStmt, 1))
                let prevUpdated = sqlite3_column_int64(selectStmt, 2)
                guard existing.contains(loserId) else { continue }
                var seen = Set<String>()
                var replaced: [String] = []
                for tId in existing {
                    let mapped = (tId == loserId) ? winnerId : tId
                    if seen.insert(mapped).inserted {
                        replaced.append(mapped)
                    }
                }
                updates.append((rowId, encodeStringArray(replaced), max(now, prevUpdated + 1)))
            }

            for item in updates {
                if markAffectedDirty {
                    let upd = try prepareLocked("UPDATE \(table) SET tag_ids_json = ?, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                    defer { sqlite3_finalize(upd) }
                    bindText(upd, 1, item.json)
                    bindInt64(upd, 2, item.nextUpdated)
                    bindText(upd, 3, item.id)
                    _ = sqlite3_step(upd)
                } else {
                    let upd = try prepareLocked("UPDATE \(table) SET tag_ids_json = ? WHERE id = ?;")
                    defer { sqlite3_finalize(upd) }
                    bindText(upd, 1, item.json)
                    bindText(upd, 2, item.id)
                    _ = sqlite3_step(upd)
                }
            }
        }
    }

    private func writeTagRowLocked(_ tag: Tag, isDirty: Bool) throws {
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

    @discardableResult
    private func upsertTagLocked(
        _ tag: Tag,
        isDirty: Bool,
        tagRemap: inout [String: String]
    ) throws -> Bool {
        if tag.isDeleted {
            try writeTagRowLocked(tag, isDirty: isDirty)
            return false
        }

        // Deterministically resolve same-title collisions among active (is_deleted = 0) tags while preserving soft-deleted tombstones!
        let conflictStmt = try prepareLocked("""
            SELECT id, title, parent_tag_id, shortcut, sort_order, created_at, updated_at, is_deleted
            FROM tags
            WHERE LOWER(TRIM(title)) = LOWER(TRIM(?)) AND id != ? AND is_deleted = 0;
        """)
        defer { sqlite3_finalize(conflictStmt) }
        bindText(conflictStmt, 1, tag.title)
        bindText(conflictStmt, 2, tag.id)

        var conflicts: [Tag] = []
        while sqlite3_step(conflictStmt) == SQLITE_ROW {
            conflicts.append(
                Tag(
                    id: colString(conflictStmt, 0) ?? "",
                    title: colString(conflictStmt, 1) ?? "",
                    parentTagId: colString(conflictStmt, 2),
                    shortcut: colString(conflictStmt, 3),
                    sortOrder: sqlite3_column_double(conflictStmt, 4),
                    createdAt: sqlite3_column_int64(conflictStmt, 5),
                    updatedAt: sqlite3_column_int64(conflictStmt, 6),
                    isDeleted: colBool(conflictStmt, 7)
                )
            )
        }

        guard !conflicts.isEmpty else {
            try writeTagRowLocked(tag, isDirty: isDirty)
            return false
        }

        let now = max(nowMillis(), tag.updatedAt)
        let candidates = ([tag] + conflicts).sorted(by: Self.compareDeterministicTagPriority)
        var winner = candidates[0]
        let losers = Array(candidates.dropFirst())
        let maxCandidateUpdated = candidates.map(\.updatedAt).max() ?? now

        var winnerEnriched = false
        for donor in losers {
            if (winner.shortcut ?? "").isEmpty, let s = donor.shortcut, !s.isEmpty {
                winner.shortcut = s
                winnerEnriched = true
            }
            if (winner.parentTagId ?? "").isEmpty, let p = donor.parentTagId, !p.isEmpty, p != winner.id {
                winner.parentTagId = p
                winnerEnriched = true
            }
        }

        if winner.id == tag.id {
            // Incoming tag wins: tombstone existing conflicts FIRST to satisfy idx_tags_title_active, then write winner, then remap references.
            for loser in conflicts {
                let tombstoneAt = max(now, maxCandidateUpdated + 1, loser.updatedAt + 1)
                let markDeletedStmt = try prepareLocked("UPDATE tags SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(markDeletedStmt) }
                bindInt64(markDeletedStmt, 1, tombstoneAt)
                bindText(markDeletedStmt, 2, loser.id)
                _ = sqlite3_step(markDeletedStmt)
            }
            if winnerEnriched {
                winner.updatedAt = max(now, maxCandidateUpdated + 1)
            }
            try writeTagRowLocked(winner, isDirty: isDirty || winnerEnriched)
            for loser in conflicts {
                let tombstoneAt = max(now, maxCandidateUpdated + 1, loser.updatedAt + 1)
                tagRemap[loser.id] = winner.id
                try remapTagReferencesLocked(from: loser.id, to: winner.id, now: tombstoneAt, markAffectedDirty: true)
            }
        } else {
            // Existing local tag wins: tombstone any extra conflicts, enrich existing winner if needed, write incoming tag directly as tombstone, and remap references!
            for extraLoser in conflicts where extraLoser.id != winner.id {
                let tombstoneAt = max(now, maxCandidateUpdated + 1, extraLoser.updatedAt + 1)
                let markDeletedStmt = try prepareLocked("UPDATE tags SET is_deleted = 1, updated_at = ?, is_dirty = 1 WHERE id = ?;")
                defer { sqlite3_finalize(markDeletedStmt) }
                bindInt64(markDeletedStmt, 1, tombstoneAt)
                bindText(markDeletedStmt, 2, extraLoser.id)
                _ = sqlite3_step(markDeletedStmt)
                tagRemap[extraLoser.id] = winner.id
                try remapTagReferencesLocked(from: extraLoser.id, to: winner.id, now: tombstoneAt, markAffectedDirty: true)
            }
            if winnerEnriched {
                winner.updatedAt = max(now, maxCandidateUpdated + 1)
                try writeTagRowLocked(winner, isDirty: true)
            }
            var incomingTombstone = tag
            incomingTombstone.isDeleted = true
            incomingTombstone.updatedAt = max(now, maxCandidateUpdated + 1, tag.updatedAt + 1)
            try writeTagRowLocked(incomingTombstone, isDirty: true)
            tagRemap[tag.id] = winner.id
            try remapTagReferencesLocked(
                from: tag.id,
                to: winner.id,
                now: incomingTombstone.updatedAt,
                markAffectedDirty: true
            )
        }
        return true
    }

    private func upsertTagLocked(_ tag: Tag, isDirty: Bool) throws {
        var dummyRemap: [String: String] = [:]
        _ = try upsertTagLocked(tag, isDirty: isDirty, tagRemap: &dummyRemap)
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
        let uniqueTags = Array(Set(project.tagIds))
        let isPurgedTombstone = project.isDeleted && project.title.isEmpty && project.notes.isEmpty
        let stmt = try prepareLocked("""
            INSERT INTO projects (
                id, area_id, title, notes, status, schedule_state, when_date, deadline,
                completed_at, last_reviewed_at, sort_order, tag_ids_json, workspace_id, view_mode,
                created_at, updated_at, is_deleted, is_purged, is_dirty
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                area_id = excluded.area_id,
                title = excluded.title,
                notes = excluded.notes,
                status = excluded.status,
                schedule_state = excluded.schedule_state,
                when_date = excluded.when_date,
                deadline = excluded.deadline,
                completed_at = excluded.completed_at,
                last_reviewed_at = excluded.last_reviewed_at,
                sort_order = excluded.sort_order,
                tag_ids_json = excluded.tag_ids_json,
                workspace_id = excluded.workspace_id,
                view_mode = excluded.view_mode,
                updated_at = excluded.updated_at,
                is_deleted = excluded.is_deleted,
                is_purged = CASE
                    WHEN excluded.is_deleted = 0 THEN 0
                    WHEN excluded.is_purged = 1 THEN 1
                    ELSE projects.is_purged
                END,
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
        bindInt64(stmt, 10, project.lastReviewedAt)
        sqlite3_bind_double(stmt, 11, project.sortOrder)
        bindText(stmt, 12, encodeStringArray(uniqueTags))
        bindText(stmt, 13, isPurgedTombstone ? nil : project.workspaceId)
        bindText(stmt, 14, isPurgedTombstone ? nil : project.viewMode)
        bindInt64(stmt, 15, project.createdAt)
        bindInt64(stmt, 16, project.updatedAt)
        bindBool(stmt, 17, project.isDeleted)
        bindBool(stmt, 18, isPurgedTombstone)
        bindBool(stmt, 19, isDirty)
        if sqlite3_step(stmt) != SQLITE_DONE {
            throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
        }

        let clearStmt = try prepareLocked("DELETE FROM project_tags WHERE project_id = ?;")
        defer { sqlite3_finalize(clearStmt) }
        bindText(clearStmt, 1, project.id)
        _ = sqlite3_step(clearStmt)

        if !project.isDeleted {
            for tagId in uniqueTags {
                let refStmt = try prepareLocked("INSERT OR IGNORE INTO project_tags (project_id, tag_id) VALUES (?, ?);")
                defer { sqlite3_finalize(refStmt) }
                bindText(refStmt, 1, project.id)
                bindText(refStmt, 2, tagId)
                _ = sqlite3_step(refStmt)
            }
        }
    }

    private func upsertProjectTemplateLocked(_ template: ProjectTemplate, isDirty: Bool) throws {
        let uniqueTags = template.isDeleted ? [] : Array(Set(template.tagIds))
        let stmt = try prepareLocked("""
            INSERT INTO project_templates (
                id, title, notes, default_area_id, tag_ids_json, blueprint_json,
                sort_order, created_at, updated_at, is_deleted, is_dirty
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                notes = excluded.notes,
                default_area_id = excluded.default_area_id,
                tag_ids_json = excluded.tag_ids_json,
                blueprint_json = excluded.blueprint_json,
                sort_order = excluded.sort_order,
                updated_at = excluded.updated_at,
                is_deleted = excluded.is_deleted,
                is_dirty = excluded.is_dirty;
        """)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, template.id)
        bindText(stmt, 2, template.title)
        bindText(stmt, 3, template.notes)
        bindText(stmt, 4, template.defaultAreaId)
        bindText(stmt, 5, encodeStringArray(uniqueTags))
        bindText(stmt, 6, template.blueprintJson)
        sqlite3_bind_double(stmt, 7, template.sortOrder)
        bindInt64(stmt, 8, template.createdAt)
        bindInt64(stmt, 9, template.updatedAt)
        bindBool(stmt, 10, template.isDeleted)
        bindBool(stmt, 11, isDirty)
        if sqlite3_step(stmt) != SQLITE_DONE {
            throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
        }
    }

    private func upsertHeadingLocked(_ heading: Heading, isDirty: Bool) throws {
        let stmt = try prepareLocked("""
            INSERT INTO headings (id, project_id, title, sort_order, is_archived, start_date, end_date, created_at, updated_at, is_deleted, is_dirty)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                project_id = excluded.project_id,
                title = excluded.title,
                sort_order = excluded.sort_order,
                is_archived = excluded.is_archived,
                start_date = excluded.start_date,
                end_date = excluded.end_date,
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
        bindInt64(stmt, 6, heading.isDeleted ? nil : heading.startDate)
        bindInt64(stmt, 7, heading.isDeleted ? nil : heading.endDate)
        bindInt64(stmt, 8, heading.createdAt)
        bindInt64(stmt, 9, heading.updatedAt)
        bindBool(stmt, 10, heading.isDeleted)
        bindBool(stmt, 11, isDirty)
        if sqlite3_step(stmt) != SQLITE_DONE {
            throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
        }
    }

    private func upsertTaskLocked(_ task: TaskItem, tagIds: [String], isDirty: Bool) throws {
        let uniqueTags = task.isDeleted ? [] : Array(Set(tagIds))
        let isPurgedTombstone = task.isDeleted && task.title.isEmpty && task.notes.isEmpty
        let effectivePinned = !task.isDeleted && task.isPinned
        let effectiveMilestone = !task.isDeleted && task.isMilestone
        let effectiveEffort = task.isDeleted ? nil : TaskEffortLevel.normalized(task.effortLevel)?.rawValue
        let effectiveBlockers: [String] = {
            guard !task.isDeleted else { return [] }
            var seen = Set<String>()
            var out: [String] = []
            for bid in task.blockedByTaskIds where bid != task.id && !bid.isEmpty {
                if seen.insert(bid).inserted {
                    out.append(bid)
                }
            }
            return out
        }()
        let effectiveActualMinutes: Int64? = {
            guard !task.isDeleted, let m = task.actualMinutes, m > 0 else { return nil }
            return Int64(min(48_000, m))
        }()
        let effectiveInProgress = !task.isDeleted && task.status == .open && task.isInProgress
        let stmt = try prepareLocked("""
            INSERT INTO tasks (
                id, title, notes, status, schedule_state, when_date, deadline, reminder_time,
                scheduled_start_minutes, duration_minutes,
                completed_at, project_id, heading_id, area_id, recurrence_rule, sort_order,
                is_pinned, is_milestone, effort_level, blocked_by_json, workspace_id, assignee_id,
                tag_ids_json, created_at, updated_at, is_deleted, is_purged, is_dirty,
                actual_minutes, is_in_progress
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                notes = excluded.notes,
                status = excluded.status,
                schedule_state = excluded.schedule_state,
                when_date = excluded.when_date,
                deadline = excluded.deadline,
                reminder_time = excluded.reminder_time,
                scheduled_start_minutes = excluded.scheduled_start_minutes,
                duration_minutes = excluded.duration_minutes,
                actual_minutes = excluded.actual_minutes,
                completed_at = excluded.completed_at,
                project_id = excluded.project_id,
                heading_id = excluded.heading_id,
                area_id = excluded.area_id,
                recurrence_rule = excluded.recurrence_rule,
                sort_order = excluded.sort_order,
                is_pinned = excluded.is_pinned,
                is_milestone = excluded.is_milestone,
                is_in_progress = excluded.is_in_progress,
                effort_level = excluded.effort_level,
                blocked_by_json = excluded.blocked_by_json,
                workspace_id = excluded.workspace_id,
                assignee_id = excluded.assignee_id,
                tag_ids_json = excluded.tag_ids_json,
                updated_at = excluded.updated_at,
                is_deleted = excluded.is_deleted,
                is_purged = CASE
                    WHEN excluded.is_deleted = 0 THEN 0
                    WHEN excluded.is_purged = 1 THEN 1
                    ELSE tasks.is_purged
                END,
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
        bindInt64(stmt, 9, task.scheduledStartMinutes.map(Int64.init))
        bindInt64(stmt, 10, task.durationMinutes.map(Int64.init))
        bindInt64(stmt, 11, task.completedAt)
        bindText(stmt, 12, task.projectId)
        bindText(stmt, 13, task.headingId)
        bindText(stmt, 14, task.areaId)
        bindText(stmt, 15, encodeRecurrence(task.recurrenceRule))
        sqlite3_bind_double(stmt, 16, task.sortOrder)
        bindBool(stmt, 17, effectivePinned)
        bindBool(stmt, 18, effectiveMilestone)
        bindText(stmt, 19, effectiveEffort)
        bindText(stmt, 20, encodeStringArray(effectiveBlockers))
        bindText(stmt, 21, task.isDeleted ? nil : task.workspaceId)
        bindText(stmt, 22, task.isDeleted ? nil : task.assigneeId)
        bindText(stmt, 23, encodeStringArray(uniqueTags))
        bindInt64(stmt, 24, task.createdAt)
        bindInt64(stmt, 25, task.updatedAt)
        bindBool(stmt, 26, task.isDeleted)
        bindBool(stmt, 27, isPurgedTombstone)
        bindBool(stmt, 28, isDirty)
        bindInt64(stmt, 29, effectiveActualMinutes)
        bindBool(stmt, 30, effectiveInProgress)
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
        let activeTagIds = Set((try fetchActiveTagsLocked()).map(\.id))
        let stmt = try prepareLocked("""
            SELECT id, title, sort_order, tag_ids_json, created_at, updated_at, is_deleted
            FROM areas WHERE \(whereClause) ORDER BY sort_order ASC;
        """)
        defer { sqlite3_finalize(stmt) }
        var out: [Area] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            let isDeleted = colBool(stmt, 6)
            let decodedTags = decodeStringArray(colString(stmt, 3)).filter { activeTagIds.contains($0) }
            out.append(
                Area(
                    id: colString(stmt, 0) ?? "",
                    title: colString(stmt, 1) ?? "",
                    sortOrder: sqlite3_column_double(stmt, 2),
                    tagIds: isDeleted ? [] : decodedTags,
                    createdAt: sqlite3_column_int64(stmt, 4),
                    updatedAt: sqlite3_column_int64(stmt, 5),
                    isDeleted: isDeleted
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

    private func fetchInactiveOrDeletedProjectIdsLocked() throws -> Set<String> {
        let stmt = try prepareLocked("SELECT id FROM projects WHERE is_deleted = 1 OR status != 'ACTIVE';")
        defer { sqlite3_finalize(stmt) }
        var ids = Set<String>()
        while sqlite3_step(stmt) == SQLITE_ROW {
            if let pid = colString(stmt, 0) {
                ids.insert(pid)
            }
        }
        return ids
    }

    private func fetchProjectByIdLocked(_ id: String) throws -> Project? {
        let activeTagIds = Set((try fetchActiveTagsLocked()).map(\.id))
        let projectTagIdsByProject = try fetchAllProjectTagIdsLocked()
        let stmt = try prepareLocked("""
            SELECT p.id, p.area_id, p.title, p.notes, p.status, p.schedule_state, p.when_date,
                   p.deadline, p.completed_at, p.sort_order, p.tag_ids_json, p.created_at, p.updated_at, p.is_deleted,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status != 'CANCELED') AS total_cnt,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status = 'COMPLETED') AS comp_cnt,
                   p.last_reviewed_at, p.workspace_id, p.view_mode,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status = 'OPEN' AND t.is_in_progress = 1) AS in_prog_cnt
            FROM projects p WHERE p.id = ? LIMIT 1;
        """)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, id)
        if sqlite3_step(stmt) == SQLITE_ROW {
            return readProjectRowLocked(
                stmt,
                activeTagIds: activeTagIds,
                projectTagIdsByProject: projectTagIdsByProject
            )
        }
        return nil
    }

    private func fetchProjectsWhereLocked(_ whereClause: String) throws -> [Project] {
        let activeTagIds = Set((try fetchActiveTagsLocked()).map(\.id))
        let projectTagIdsByProject = try fetchAllProjectTagIdsLocked()
        let stmt = try prepareLocked("""
            SELECT p.id, p.area_id, p.title, p.notes, p.status, p.schedule_state, p.when_date,
                   p.deadline, p.completed_at, p.sort_order, p.tag_ids_json, p.created_at, p.updated_at, p.is_deleted,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status != 'CANCELED') AS total_cnt,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status = 'COMPLETED') AS comp_cnt,
                   p.last_reviewed_at, p.workspace_id, p.view_mode,
                   (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.is_deleted = 0 AND t.status = 'OPEN' AND t.is_in_progress = 1) AS in_prog_cnt
            FROM projects p WHERE \(whereClause) ORDER BY p.sort_order ASC;
        """)
        defer { sqlite3_finalize(stmt) }
        var out: [Project] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            out.append(
                readProjectRowLocked(
                    stmt,
                    activeTagIds: activeTagIds,
                    projectTagIdsByProject: projectTagIdsByProject
                )
            )
        }
        return out
    }

    private func readProjectRowLocked(
        _ stmt: OpaquePointer,
        activeTagIds: Set<String>? = nil,
        projectTagIdsByProject: [String: [String]]? = nil
    ) -> Project {
        let id = colString(stmt, 0) ?? ""
        let isDeleted = colBool(stmt, 13)
        let refTagIds = projectTagIdsByProject?[id] ?? []
        let resolvedTagIds: [String]
        if isDeleted {
            resolvedTagIds = []
        } else if let activeTagIds {
            resolvedTagIds = refTagIds.filter { activeTagIds.contains($0) }
        } else {
            resolvedTagIds = refTagIds
        }

        return Project(
            id: id,
            areaId: colString(stmt, 1),
            title: colString(stmt, 2) ?? "",
            notes: colString(stmt, 3) ?? "",
            status: ProjectStatus.normalized(colString(stmt, 4)),
            scheduleState: ScheduleState.normalized(colString(stmt, 5), fallback: .anytime),
            whenDate: colInt64(stmt, 6),
            deadline: colInt64(stmt, 7),
            completedAt: colInt64(stmt, 8),
            lastReviewedAt: colInt64(stmt, 16),
            sortOrder: sqlite3_column_double(stmt, 9),
            tagIds: resolvedTagIds,
            workspaceId: isDeleted ? nil : colString(stmt, 17),
            viewMode: isDeleted ? nil : colString(stmt, 18),
            totalTaskCount: Int(sqlite3_column_int(stmt, 14)),
            completedTaskCount: Int(sqlite3_column_int(stmt, 15)),
            createdAt: sqlite3_column_int64(stmt, 11),
            updatedAt: sqlite3_column_int64(stmt, 12),
            isDeleted: isDeleted,
            inProgressTaskCount: isDeleted ? 0 : Int(colInt64(stmt, 19) ?? 0)
        )
    }

    private func fetchActiveProjectTemplatesLocked() throws -> [ProjectTemplate] {
        try fetchProjectTemplatesWhereLocked("is_deleted = 0")
    }

    private func fetchProjectTemplatesWhereLocked(_ whereClause: String) throws -> [ProjectTemplate] {
        let activeTagIds = Set((try fetchActiveTagsLocked()).map(\.id))
        let stmt = try prepareLocked("""
            SELECT id, title, notes, default_area_id, tag_ids_json, blueprint_json,
                   sort_order, created_at, updated_at, is_deleted, is_dirty
            FROM project_templates WHERE \(whereClause) ORDER BY sort_order ASC, created_at ASC;
        """)
        defer { sqlite3_finalize(stmt) }
        var out: [ProjectTemplate] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            let isDeleted = colBool(stmt, 9)
            let decodedTags = decodeStringArray(colString(stmt, 4)).filter { activeTagIds.contains($0) }
            out.append(
                ProjectTemplate(
                    id: colString(stmt, 0) ?? "",
                    title: colString(stmt, 1) ?? "",
                    notes: colString(stmt, 2) ?? "",
                    defaultAreaId: colString(stmt, 3),
                    tagIds: isDeleted ? [] : decodedTags,
                    blueprintJson: colString(stmt, 5) ?? "{}",
                    sortOrder: sqlite3_column_double(stmt, 6),
                    createdAt: sqlite3_column_int64(stmt, 7),
                    updatedAt: sqlite3_column_int64(stmt, 8),
                    isDeleted: isDeleted,
                    isDirty: colBool(stmt, 10)
                )
            )
        }
        return out
    }

    private func fetchAllActiveHeadingsLocked() throws -> [Heading] {
        try fetchHeadingsWhereLocked("is_deleted = 0")
    }

    private func fetchHeadingsWhereLocked(_ whereClause: String) throws -> [Heading] {
        let stmt = try prepareLocked("""
            SELECT id, project_id, title, sort_order, is_archived, created_at, updated_at, is_deleted, end_date, start_date
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
                    startDate: colInt64(stmt, 9),
                    endDate: colInt64(stmt, 8),
                    createdAt: sqlite3_column_int64(stmt, 5),
                    updatedAt: sqlite3_column_int64(stmt, 6),
                    isDeleted: colBool(stmt, 7)
                )
            )
        }
        return out
    }

    private func upsertAttachmentLocked(_ attachment: TaskAttachment, isDirty: Bool) throws {
        let stmt = try prepareLocked("""
            INSERT INTO attachments (
                id, task_id, project_id, file_name, mime_type, size_bytes,
                local_relative_path, created_at, updated_at, is_deleted, is_dirty
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                task_id = excluded.task_id,
                project_id = excluded.project_id,
                file_name = excluded.file_name,
                mime_type = excluded.mime_type,
                size_bytes = excluded.size_bytes,
                local_relative_path = excluded.local_relative_path,
                updated_at = excluded.updated_at,
                is_deleted = excluded.is_deleted,
                is_dirty = excluded.is_dirty;
        """)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, 1, attachment.id)
        bindText(stmt, 2, attachment.taskId)
        bindText(stmt, 3, attachment.projectId)
        bindText(stmt, 4, attachment.fileName)
        bindText(stmt, 5, attachment.mimeType)
        bindInt64(stmt, 6, attachment.sizeBytes)
        bindText(stmt, 7, attachment.localRelativePath)
        bindInt64(stmt, 8, attachment.createdAt)
        bindInt64(stmt, 9, attachment.updatedAt)
        bindBool(stmt, 10, attachment.isDeleted)
        bindBool(stmt, 11, isDirty)
        if sqlite3_step(stmt) != SQLITE_DONE {
            throw ThingsDatabaseError.sqlError(currentErrorMessageLocked())
        }
    }

    private func fetchAllActiveAttachmentsGroupedLocked() throws -> (byTask: [String: [TaskAttachment]], byProject: [String: [TaskAttachment]]) {
        let active = try fetchAttachmentsWhereLocked("is_deleted = 0")
        var byTask: [String: [TaskAttachment]] = [:]
        var byProject: [String: [TaskAttachment]] = [:]
        for att in active {
            if let tid = att.taskId, !tid.isEmpty {
                byTask[tid, default: []].append(att)
            } else if let pid = att.projectId, !pid.isEmpty {
                byProject[pid, default: []].append(att)
            }
        }
        return (byTask, byProject)
    }

    private func fetchAttachmentsWhereLocked(_ whereClause: String) throws -> [TaskAttachment] {
        let stmt = try prepareLocked("""
            SELECT id, task_id, project_id, file_name, mime_type, size_bytes,
                   local_relative_path, created_at, updated_at, is_deleted
            FROM attachments WHERE \(whereClause) ORDER BY created_at ASC;
        """)
        defer { sqlite3_finalize(stmt) }
        var out: [TaskAttachment] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            out.append(
                TaskAttachment(
                    id: colString(stmt, 0) ?? "",
                    taskId: colString(stmt, 1),
                    projectId: colString(stmt, 2),
                    fileName: colString(stmt, 3) ?? "",
                    mimeType: colString(stmt, 4) ?? "application/octet-stream",
                    sizeBytes: sqlite3_column_int64(stmt, 5),
                    localRelativePath: colString(stmt, 6) ?? "",
                    createdAt: sqlite3_column_int64(stmt, 7),
                    updatedAt: sqlite3_column_int64(stmt, 8),
                    isDeleted: colBool(stmt, 9)
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

    private func fetchAllProjectTagIdsLocked() throws -> [String: [String]] {
        let stmt = try prepareLocked("SELECT project_id, tag_id FROM project_tags;")
        defer { sqlite3_finalize(stmt) }
        var map: [String: [String]] = [:]
        while sqlite3_step(stmt) == SQLITE_ROW {
            if let pid = colString(stmt, 0), let tgid = colString(stmt, 1) {
                map[pid, default: []].append(tgid)
            }
        }
        return map
    }

    private func fetchTaskByIdLocked(_ id: String) throws -> TaskItem? {
        let escapedId = id.replacingOccurrences(of: "'", with: "''")
        let tags = try fetchActiveTagsLocked()
        let tagsById = Dictionary(tags.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let checklists = try fetchChecklistsWhereLocked("task_id = '\(escapedId)' AND is_deleted = 0")
        let attachments = try fetchAttachmentsWhereLocked("task_id = '\(escapedId)' AND is_deleted = 0")
        let tagIds = try fetchAllTaskTagIdsLocked()

        let found = try fetchTasksWhereLocked(
            whereClause: "id = ?",
            bind: { stmt in self.bindText(stmt, 1, id) },
            orderBy: "sort_order ASC",
            tagsById: tagsById,
            tagIdsByTask: tagIds,
            checklistsByTask: [id: checklists],
            attachmentsByTask: [id: attachments]
        )
        return found.first
    }

    private func fetchTasksWhereLocked(
        whereClause: String,
        bind: (OpaquePointer) -> Void,
        orderBy: String,
        tagsById: [String: Tag],
        tagIdsByTask: [String: [String]],
        checklistsByTask: [String: [ChecklistItem]],
        attachmentsByTask: [String: [TaskAttachment]] = [:]
    ) throws -> [TaskItem] {
        let stmt = try prepareLocked("""
            SELECT id, title, notes, status, schedule_state, when_date, deadline, reminder_time,
                   completed_at, project_id, heading_id, area_id, recurrence_rule, sort_order,
                   tag_ids_json, created_at, updated_at, is_deleted,
                   scheduled_start_minutes, duration_minutes,
                   is_pinned, effort_level, blocked_by_json, workspace_id, assignee_id, is_milestone,
                   actual_minutes, is_in_progress
            FROM tasks WHERE \(whereClause) ORDER BY \(orderBy);
        """)
        defer { sqlite3_finalize(stmt) }
        bind(stmt)

        var out: [TaskItem] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            out.append(
                readTaskRowLocked(
                    stmt,
                    tagsById: tagsById,
                    tagIdsByTask: tagIdsByTask,
                    checklistsByTask: checklistsByTask,
                    attachmentsByTask: attachmentsByTask
                )
            )
        }
        return out
    }

    private func readTaskRowLocked(
        _ stmt: OpaquePointer,
        tagsById: [String: Tag],
        tagIdsByTask: [String: [String]],
        checklistsByTask: [String: [ChecklistItem]],
        attachmentsByTask: [String: [TaskAttachment]] = [:]
    ) -> TaskItem {
        let id = colString(stmt, 0) ?? ""
        let isDeleted = colBool(stmt, 17)
        let status = TaskStatus.normalized(colString(stmt, 3))
        let refTagIds = isDeleted ? [] : (tagIdsByTask[id] ?? [])
        let resolvedTags = refTagIds.compactMap { tagsById[$0] }.sorted { $0.sortOrder < $1.sortOrder }
        let isPinned = !isDeleted && colBool(stmt, 20)
        let effortLevel = isDeleted ? nil : TaskEffortLevel.normalized(colString(stmt, 21))?.rawValue
        let blockedByTaskIds = isDeleted ? [] : decodeStringArray(colString(stmt, 22)).filter { $0 != id && !$0.isEmpty }
        let workspaceId = isDeleted ? nil : colString(stmt, 23)
        let assigneeId = isDeleted ? nil : colString(stmt, 24)
        let isMilestone = !isDeleted && colBool(stmt, 25)
        let actualMinutes = isDeleted ? nil : colInt64(stmt, 26).map(Int.init)
        let isInProgress = !isDeleted && status == .open && colBool(stmt, 27)

        return TaskItem(
            id: id,
            title: colString(stmt, 1) ?? "",
            notes: colString(stmt, 2) ?? "",
            status: status,
            scheduleState: ScheduleState.normalized(colString(stmt, 4), fallback: .inbox),
            whenDate: colInt64(stmt, 5),
            deadline: colInt64(stmt, 6),
            reminderTime: colInt64(stmt, 7),
            scheduledStartMinutes: colInt64(stmt, 18).map(Int.init),
            durationMinutes: colInt64(stmt, 19).map(Int.init),
            actualMinutes: actualMinutes,
            completedAt: colInt64(stmt, 8),
            projectId: colString(stmt, 9),
            headingId: colString(stmt, 10),
            areaId: colString(stmt, 11),
            recurrenceRule: decodeRecurrence(colString(stmt, 12)),
            sortOrder: sqlite3_column_double(stmt, 13),
            isPinned: isPinned,
            isMilestone: isMilestone,
            effortLevel: effortLevel,
            blockedByTaskIds: blockedByTaskIds,
            workspaceId: workspaceId,
            assigneeId: assigneeId,
            checklistItems: checklistsByTask[id] ?? [],
            tags: resolvedTags,
            attachments: attachmentsByTask[id] ?? [],
            createdAt: sqlite3_column_int64(stmt, 15),
            updatedAt: sqlite3_column_int64(stmt, 16),
            isDeleted: isDeleted,
            isInProgress: isInProgress
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
        let deletedTasks = scalar("SELECT COUNT(*) FROM tasks WHERE is_deleted = 1 AND is_purged = 0;")
        let canceledTasks = scalar("SELECT COUNT(*) FROM tasks WHERE status = 'CANCELED' AND is_deleted = 0;")
        let todayActive = scalar("""
            SELECT COUNT(*) FROM tasks
            WHERE status = 'OPEN' AND is_deleted = 0
              AND (project_id IS NULL OR project_id NOT IN (SELECT id FROM projects WHERE is_deleted = 1 OR status != 'ACTIVE'))
              AND (schedule_state IN ('TODAY', 'THIS_EVENING') OR (when_date IS NOT NULL AND when_date <= ?));
        """, endOfToday)
        let recurring = scalar("SELECT COUNT(*) FROM tasks WHERE status = 'OPEN' AND is_deleted = 0 AND recurrence_rule IS NOT NULL AND recurrence_rule != '';")
        let activeProjects = scalar("SELECT COUNT(*) FROM projects WHERE status = 'ACTIVE' AND is_deleted = 0;")
        let savedTags = scalar("SELECT COUNT(*) FROM tags WHERE is_deleted = 0;")
        let totalRecords =
            scalar("SELECT COUNT(*) FROM tasks WHERE is_purged = 0;") +
            scalar("SELECT COUNT(*) FROM projects WHERE is_purged = 0;") +
            scalar("SELECT COUNT(*) FROM areas;") +
            scalar("SELECT COUNT(*) FROM headings;") +
            scalar("SELECT COUNT(*) FROM checklist_items;") +
            scalar("SELECT COUNT(*) FROM tags;") +
            scalar("SELECT COUNT(*) FROM attachments;") +
            scalar("SELECT COUNT(*) FROM project_templates;")

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
