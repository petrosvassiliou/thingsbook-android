package com.culturedcode.things3.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.culturedcode.things3.data.local.dao.AreaDao
import com.culturedcode.things3.data.local.dao.ChecklistDao
import com.culturedcode.things3.data.local.dao.HeadingDao
import com.culturedcode.things3.data.local.dao.ProjectDao
import com.culturedcode.things3.data.local.dao.TagDao
import com.culturedcode.things3.data.local.dao.TaskDao
import com.culturedcode.things3.data.local.entity.AreaEntity
import com.culturedcode.things3.data.local.entity.ChecklistItemEntity
import com.culturedcode.things3.data.local.entity.HeadingEntity
import com.culturedcode.things3.data.local.entity.ProjectEntity
import com.culturedcode.things3.data.local.entity.ProjectTagCrossRef
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
        ProjectTagCrossRef::class
    ],
    version = 2,
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

    companion object {
        const val DATABASE_NAME = "thingsbook_v3_clean.db"

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

                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS tasks_fts_au AFTER UPDATE ON tasks BEGIN
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

                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS projects_fts_au AFTER UPDATE ON projects BEGIN
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

                fts5Available = true
            }.onFailure {
                fts5Available = false
            }
        }

        fun sanitizeFts5Query(rawQuery: String): String {
            return rawQuery
                .trim()
                .replace(Regex("[\"'*():^\\-]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { "\"$it\"*" }
        }

        fun buildTasksFts5SqlQuery(rawQuery: String): SupportSQLiteQuery {
            val cleaned = rawQuery.trim()
            if (fts5Available && cleaned.isNotBlank()) {
                val ftsMatch = sanitizeFts5Query(cleaned)
                return SimpleSQLiteQuery(
                    """
                    SELECT t.* FROM tasks t
                    INNER JOIN tasks_fts fts ON t.id = fts.task_id
                    WHERE t.isDeleted = 0 AND tasks_fts MATCH ?
                    ORDER BY t.sortOrder ASC
                    LIMIT 50
                    """.trimIndent(),
                    arrayOf(ftsMatch)
                )
            }
            val pattern = "%$cleaned%"
            return SimpleSQLiteQuery(
                """
                SELECT * FROM tasks
                WHERE isDeleted = 0 AND (title LIKE ? COLLATE NOCASE OR notes LIKE ? COLLATE NOCASE)
                ORDER BY sortOrder ASC
                LIMIT 50
                """.trimIndent(),
                arrayOf(pattern, pattern)
            )
        }

        fun buildProjectsFts5SqlQuery(rawQuery: String): SupportSQLiteQuery {
            val cleaned = rawQuery.trim()
            if (fts5Available && cleaned.isNotBlank()) {
                val ftsMatch = sanitizeFts5Query(cleaned)
                return SimpleSQLiteQuery(
                    """
                    SELECT p.* FROM projects p
                    INNER JOIN projects_fts fts ON p.id = fts.project_id
                    WHERE p.isDeleted = 0 AND projects_fts MATCH ?
                    ORDER BY p.sortOrder ASC
                    LIMIT 25
                    """.trimIndent(),
                    arrayOf(ftsMatch)
                )
            }
            val pattern = "%$cleaned%"
            return SimpleSQLiteQuery(
                """
                SELECT * FROM projects
                WHERE isDeleted = 0 AND (title LIKE ? COLLATE NOCASE OR notes LIKE ? COLLATE NOCASE)
                ORDER BY sortOrder ASC
                LIMIT 25
                """.trimIndent(),
                arrayOf(pattern, pattern)
            )
        }
    }
}
