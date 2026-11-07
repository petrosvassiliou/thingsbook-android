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
    version = 1,
    exportSchema = true
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
        const val DATABASE_NAME = "things3_offline_first.db"

        /**
         * Initializes SQLite FTS5 virtual tables with the `unicode61 remove_diacritics 2` tokenizer
         * for diacritic-aware full-text search across German umlauts (ä, ö, ü, ß) and French/Spanish/Italian accents.
         */
        fun installFts5Triggers(db: SupportSQLiteDatabase) {
            // 1. Tasks FTS5 Virtual Table
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

            // 2. Projects FTS5 Virtual Table
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

            // 3. Areas FTS5 Virtual Table
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS areas_fts USING fts5(
                    area_id UNINDEXED,
                    title,
                    tokenize = 'unicode61 remove_diacritics 2'
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS areas_fts_ai AFTER INSERT ON areas BEGIN
                    INSERT INTO areas_fts(area_id, title)
                    VALUES (new.id, new.title);
                END
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS areas_fts_au AFTER UPDATE ON areas BEGIN
                    DELETE FROM areas_fts WHERE area_id = old.id;
                    INSERT INTO areas_fts(area_id, title)
                    VALUES (new.id, new.title);
                END
                """.trimIndent()
            )

            // 4. Tags FTS5 Virtual Table
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS tags_fts USING fts5(
                    tag_id UNINDEXED,
                    title,
                    tokenize = 'unicode61 remove_diacritics 2'
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS tags_fts_ai AFTER INSERT ON tags BEGIN
                    INSERT INTO tags_fts(tag_id, title)
                    VALUES (new.id, new.title);
                END
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS tags_fts_au AFTER UPDATE ON tags BEGIN
                    DELETE FROM tags_fts WHERE tag_id = old.id;
                    INSERT INTO tags_fts(tag_id, title)
                    VALUES (new.id, new.title);
                END
                """.trimIndent()
            )
        }

        /**
         * Builds a safe FTS5 prefix match query (`term1* term2*`) for real-time Quick Find.
         */
        fun sanitizeFts5Query(rawQuery: String): String {
            return rawQuery
                .trim()
                .replace(Regex("[\"'*():^\\-]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { "\"$it\"*" }
        }

        fun buildTasksFts5SqlQuery(rawQuery: String): SupportSQLiteQuery {
            val ftsMatch = sanitizeFts5Query(rawQuery)
            return SimpleSQLiteQuery(
                """
                SELECT t.* FROM tasks t
                INNER JOIN tasks_fts fts ON t.id = fts.task_id
                WHERE t.isDeleted = 0 AND tasks_fts MATCH ?
                ORDER BY bm25(tasks_fts), t.sortOrder ASC
                LIMIT 50
                """.trimIndent(),
                arrayOf(ftsMatch)
            )
        }

        fun buildProjectsFts5SqlQuery(rawQuery: String): SupportSQLiteQuery {
            val ftsMatch = sanitizeFts5Query(rawQuery)
            return SimpleSQLiteQuery(
                """
                SELECT p.* FROM projects p
                INNER JOIN projects_fts fts ON p.id = fts.project_id
                WHERE p.isDeleted = 0 AND projects_fts MATCH ?
                ORDER BY bm25(projects_fts), p.sortOrder ASC
                LIMIT 25
                """.trimIndent(),
                arrayOf(ftsMatch)
            )
        }

        fun buildAreasFts5SqlQuery(rawQuery: String): SupportSQLiteQuery {
            val ftsMatch = sanitizeFts5Query(rawQuery)
            return SimpleSQLiteQuery(
                """
                SELECT a.* FROM areas a
                INNER JOIN areas_fts fts ON a.id = fts.area_id
                WHERE a.isDeleted = 0 AND areas_fts MATCH ?
                ORDER BY bm25(areas_fts), a.sortOrder ASC
                LIMIT 15
                """.trimIndent(),
                arrayOf(ftsMatch)
            )
        }

        fun buildTagsFts5SqlQuery(rawQuery: String): SupportSQLiteQuery {
            val ftsMatch = sanitizeFts5Query(rawQuery)
            return SimpleSQLiteQuery(
                """
                SELECT tg.* FROM tags tg
                INNER JOIN tags_fts fts ON tg.id = fts.tag_id
                WHERE tg.isDeleted = 0 AND tags_fts MATCH ?
                ORDER BY bm25(tags_fts), tg.sortOrder ASC
                LIMIT 20
                """.trimIndent(),
                arrayOf(ftsMatch)
            )
        }
    }
}
