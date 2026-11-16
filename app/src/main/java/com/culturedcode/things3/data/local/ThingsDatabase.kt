package com.culturedcode.things3.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.culturedcode.things3.data.local.dao.AreaDao
import com.culturedcode.things3.data.local.dao.AttachmentDao
import com.culturedcode.things3.data.local.dao.ChecklistDao
import com.culturedcode.things3.data.local.dao.HeadingDao
import com.culturedcode.things3.data.local.dao.ProjectDao
import com.culturedcode.things3.data.local.dao.ProjectTemplateDao
import com.culturedcode.things3.data.local.dao.TagDao
import com.culturedcode.things3.data.local.dao.TaskDao
import com.culturedcode.things3.data.local.entity.AreaEntity
import com.culturedcode.things3.data.local.entity.AttachmentEntity
import com.culturedcode.things3.data.local.entity.ChecklistItemEntity
import com.culturedcode.things3.data.local.entity.HeadingEntity
import com.culturedcode.things3.data.local.entity.ProjectEntity
import com.culturedcode.things3.data.local.entity.ProjectTagCrossRef
import com.culturedcode.things3.data.local.entity.ProjectTemplateEntity
import com.culturedcode.things3.data.local.entity.TagEntity
import com.culturedcode.things3.data.local.entity.TaskEntity
import com.culturedcode.things3.data.local.entity.TaskTagCrossRef

@Database(
    entities = [
        AreaEntity::class,
        ProjectEntity::class,
        HeadingEntity::class,
        TaskEntity::class,
        ChecklistItemEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
        ProjectTagCrossRef::class,
        AttachmentEntity::class,
        ProjectTemplateEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(ThingsTypeConverters::class)
abstract class ThingsDatabase : RoomDatabase() {
    abstract fun areaDao(): AreaDao
    abstract fun projectDao(): ProjectDao
    abstract fun headingDao(): HeadingDao
    abstract fun taskDao(): TaskDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun tagDao(): TagDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun projectTemplateDao(): ProjectTemplateDao

    companion object {
        const val DATABASE_NAME = "thingsbook_v5_mac_edition.db"

        @Volatile
        private var fts5Available: Boolean = false

        /**
         * Safely initializes SQLite FTS5 virtual tables (when supported by the device's SQLite build)
         * and never throws an uncaught SQLiteException on Android devices whose system `libsqlite.so`
         * is compiled without `SQLITE_ENABLE_FTS5`.
         */
        fun installFts5Triggers(db: SupportSQLiteDatabase) {
            runCatching {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS tasks_fts USING fts5(
                        task_id UNINDEXED,
                        title,
                        notes,
                        tokenize = 'unicode61 remove_diacritics 2'
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS tasks_fts_ai AFTER INSERT ON tasks BEGIN
                        INSERT INTO tasks_fts(task_id, title, notes)
                        VALUES (new.id, new.title, new.notes);
                    END
                    """.trimIndent()
                )

                db.execSQL("DROP TRIGGER IF EXISTS tasks_fts_au")
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS tasks_fts_au AFTER UPDATE OF id, title, notes ON tasks BEGIN
                        DELETE FROM tasks_fts WHERE task_id = old.id;
                        INSERT INTO tasks_fts(task_id, title, notes)
                        VALUES (new.id, new.title, new.notes);
                    END
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS tasks_fts_ad AFTER DELETE ON tasks BEGIN
                        DELETE FROM tasks_fts WHERE task_id = old.id;
                    END
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS projects_fts USING fts5(
                        project_id UNINDEXED,
                        title,
                        notes,
                        tokenize = 'unicode61 remove_diacritics 2'
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS projects_fts_ai AFTER INSERT ON projects BEGIN
                        INSERT INTO projects_fts(project_id, title, notes)
                        VALUES (new.id, new.title, new.notes);
                    END
                    """.trimIndent()
                )

                db.execSQL("DROP TRIGGER IF EXISTS projects_fts_au")
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS projects_fts_au AFTER UPDATE OF id, title, notes ON projects BEGIN
                        DELETE FROM projects_fts WHERE project_id = old.id;
                        INSERT INTO projects_fts(project_id, title, notes)
                        VALUES (new.id, new.title, new.notes);
                    END
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS projects_fts_ad AFTER DELETE ON projects BEGIN
                        DELETE FROM projects_fts WHERE project_id = old.id;
                    END
                    """.trimIndent()
                )

                // Ensure any pre-existing tasks or projects are indexed in FTS5
                db.execSQL(
                    """
                    INSERT INTO tasks_fts(task_id, title, notes)
                    SELECT t.id, t.title, t.notes FROM tasks t
                    WHERE NOT EXISTS (SELECT 1 FROM tasks_fts f WHERE f.task_id = t.id)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO projects_fts(project_id, title, notes)
                    SELECT p.id, p.title, p.notes FROM projects p
                    WHERE NOT EXISTS (SELECT 1 FROM projects_fts f WHERE f.project_id = p.id)
                    """.trimIndent()
                )

                fts5Available = true
            }.onFailure {
                fts5Available = false
            }
        }

        private val ftsTokenRegex = Regex("[\\p{L}\\p{N}_]+")

        /**
         * Strictly extracts Unicode alphanumeric/underscore tokens for FTS5 prefix matching (`"token"*`),
         * eliminating any risk of FTS5 syntax errors on quotes, operators (`AND`, `OR`, `NOT`, `NEAR`),
         * NUL bytes, or punctuation-only queries (e.g. `"`, `*`, `C++`, `#tag`).
         */
        fun sanitizeFts5Query(rawQuery: String): String {
            val cleaned = rawQuery.replace("\u0000", "").trim()
            if (cleaned.isEmpty()) return ""
            return ftsTokenRegex.findAll(cleaned)
                .map { it.value }
                .filter { it.isNotBlank() }
                .take(16)
                .joinToString(" ") { "\"$it\"*" }
        }

        fun escapeSqlLikePattern(raw: String): String {
            return raw
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
        }

        fun buildTasksFts5SqlQuery(rawQuery: String, hideCompleted: Boolean = false): SupportSQLiteQuery {
            val cleaned = rawQuery.replace("\u0000", "").trim()
            val statusClauseT = if (hideCompleted) "AND t.status = 'OPEN'" else ""
            val statusClausePlain = if (hideCompleted) "AND status = 'OPEN'" else ""

            if (cleaned.isBlank()) {
                return SimpleSQLiteQuery(
                    """
                    SELECT * FROM tasks
                    WHERE isDeleted = 0 $statusClausePlain
                    ORDER BY CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END ASC, updatedAt DESC
                    LIMIT 250
                    """.trimIndent()
                )
            }

            val likePattern = "%${escapeSqlLikePattern(cleaned)}%"
            val ftsMatch = if (fts5Available) sanitizeFts5Query(cleaned) else ""
            if (fts5Available && ftsMatch.isNotBlank()) {
                return SimpleSQLiteQuery(
                    """
                    SELECT t.* FROM tasks t
                    WHERE t.isDeleted = 0 $statusClauseT AND (
                        t.id IN (SELECT task_id FROM tasks_fts WHERE tasks_fts MATCH ?)
                        OR EXISTS (
                            SELECT 1 FROM attachments a
                            WHERE a.taskId = t.id
                              AND a.isDeleted = 0
                              AND (a.fileName COLLATE NOCASE) LIKE ? ESCAPE '\'
                        )
                    )
                    ORDER BY CASE WHEN t.status = 'OPEN' THEN 0 ELSE 1 END ASC, t.sortOrder ASC
                    LIMIT 250
                    """.trimIndent(),
                    arrayOf(ftsMatch, likePattern)
                )
            }
            return SimpleSQLiteQuery(
                """
                SELECT * FROM tasks
                WHERE isDeleted = 0 $statusClausePlain AND (
                    (title COLLATE NOCASE) LIKE ? ESCAPE '\'
                    OR (notes COLLATE NOCASE) LIKE ? ESCAPE '\'
                    OR EXISTS (
                        SELECT 1 FROM attachments a
                        WHERE a.taskId = tasks.id
                          AND a.isDeleted = 0
                          AND (a.fileName COLLATE NOCASE) LIKE ? ESCAPE '\'
                    )
                )
                ORDER BY CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END ASC, sortOrder ASC
                LIMIT 250
                """.trimIndent(),
                arrayOf(likePattern, likePattern, likePattern)
            )
        }

        fun buildProjectsFts5SqlQuery(rawQuery: String, hideCompleted: Boolean = false): SupportSQLiteQuery {
            val cleaned = rawQuery.replace("\u0000", "").trim()
            val statusClauseP = if (hideCompleted) "AND p.status = 'ACTIVE'" else ""
            val statusClausePlain = if (hideCompleted) "AND status = 'ACTIVE'" else ""

            if (cleaned.isBlank()) {
                return SimpleSQLiteQuery(
                    """
                    SELECT * FROM projects
                    WHERE isDeleted = 0 $statusClausePlain
                    ORDER BY sortOrder ASC
                    LIMIT 100
                    """.trimIndent()
                )
            }

            val ftsMatch = if (fts5Available) sanitizeFts5Query(cleaned) else ""
            if (fts5Available && ftsMatch.isNotBlank()) {
                return SimpleSQLiteQuery(
                    """
                    SELECT p.* FROM projects p
                    INNER JOIN projects_fts fts ON p.id = fts.project_id
                    WHERE p.isDeleted = 0 $statusClauseP AND projects_fts MATCH ?
                    ORDER BY p.sortOrder ASC
                    LIMIT 100
                    """.trimIndent(),
                    arrayOf(ftsMatch)
                )
            }
            val likePattern = "%${escapeSqlLikePattern(cleaned)}%"
            return SimpleSQLiteQuery(
                """
                SELECT * FROM projects
                WHERE isDeleted = 0 $statusClausePlain AND ((title COLLATE NOCASE) LIKE ? ESCAPE '\' OR (notes COLLATE NOCASE) LIKE ? ESCAPE '\')
                ORDER BY sortOrder ASC
                LIMIT 100
                """.trimIndent(),
                arrayOf(likePattern, likePattern)
            )
        }
    }
}
