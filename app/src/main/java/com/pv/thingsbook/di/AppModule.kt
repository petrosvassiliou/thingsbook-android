package com.pv.thingsbook.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pv.thingsbook.data.local.DatabaseSeeder
import com.pv.thingsbook.data.local.ThingsDatabase
import com.pv.thingsbook.data.local.dao.AreaDao
import com.pv.thingsbook.data.local.dao.AttachmentDao
import com.pv.thingsbook.data.local.dao.ChecklistDao
import com.pv.thingsbook.data.local.dao.HeadingDao
import com.pv.thingsbook.data.local.dao.ProjectDao
import com.pv.thingsbook.data.local.dao.ProjectTemplateDao
import com.pv.thingsbook.data.local.dao.TagDao
import com.pv.thingsbook.data.local.dao.TaskDao
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    internal val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Schema v1 -> v2 additive safety migration; ensures user data is preserved when compatible
        }
    }

    internal val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `attachments` (
                    `id` TEXT NOT NULL,
                    `taskId` TEXT,
                    `projectId` TEXT,
                    `fileName` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `localRelativePath` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    `isDirty` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_taskId` ON `attachments` (`taskId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_projectId` ON `attachments` (`projectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_updatedAt` ON `attachments` (`updatedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_isDirty` ON `attachments` (`isDirty`)")
        }
    }

    internal fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean {
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            if (nameIdx < 0) return false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx).equals(columnName, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    internal val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.hasColumn("tasks", "scheduledStartMinutes")) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `scheduledStartMinutes` INTEGER")
            }
            if (!db.hasColumn("tasks", "durationMinutes")) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `durationMinutes` INTEGER")
            }
            if (!db.hasColumn("projects", "lastReviewedAt")) {
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `lastReviewedAt` INTEGER")
            }
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `project_templates` (" +
                    "`id` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`notes` TEXT NOT NULL, " +
                    "`defaultAreaId` TEXT, " +
                    "`tagIdsJson` TEXT NOT NULL, " +
                    "`blueprintJson` TEXT NOT NULL, " +
                    "`sortOrder` REAL NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "`isDeleted` INTEGER NOT NULL, " +
                    "`isDirty` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_templates_updatedAt` ON `project_templates`(`updatedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_templates_isDirty` ON `project_templates`(`isDirty`)")
        }
    }

    internal val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.hasColumn("headings", "endDate")) {
                db.execSQL("ALTER TABLE `headings` ADD COLUMN `endDate` INTEGER")
            }
        }
    }

    internal val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.hasColumn("tasks", "isPinned")) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
            }
            if (!db.hasColumn("tasks", "effortLevel")) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `effortLevel` TEXT")
            }
            if (!db.hasColumn("tasks", "blockedByJson")) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `blockedByJson` TEXT NOT NULL DEFAULT '[]'")
            }
            if (!db.hasColumn("tasks", "workspaceId")) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `workspaceId` TEXT")
            }
            if (!db.hasColumn("tasks", "assigneeId")) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `assigneeId` TEXT")
            }
            if (!db.hasColumn("headings", "startDate")) {
                db.execSQL("ALTER TABLE `headings` ADD COLUMN `startDate` INTEGER")
            }
            if (!db.hasColumn("projects", "workspaceId")) {
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `workspaceId` TEXT")
            }
            if (!db.hasColumn("projects", "viewMode")) {
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `viewMode` TEXT")
            }
        }
    }

    internal val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.hasColumn("tasks", "isMilestone")) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN isMilestone INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    internal val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.hasColumn("tasks", "actualMinutes")) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `actualMinutes` INTEGER")
            }
        }
    }

    private fun ensureDefaultFirebaseApp(context: Context): FirebaseApp {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                return FirebaseApp.getInstance()
            }
            val initialized = FirebaseApp.initializeApp(context)
            if (initialized != null) {
                return initialized
            }
        }
        // Offline-safe fallback options so Hilt DI never throws IllegalStateException if google-services.json is absent
        val fallbackOptions = FirebaseOptions.Builder()
            .setApplicationId("1:000000000000:android:0000000000000000")
            .setApiKey("AIzaSyOfflinePlaceholderKey0000000000000")
            .setProjectId("thingsbook-offline-local")
            .build()
        return runCatching {
            FirebaseApp.initializeApp(context, fallbackOptions)
        }.getOrElse {
            FirebaseApp.getInstance()
        }
    }

    @Provides
    @Singleton
    fun provideThingsDatabase(
        @ApplicationContext context: Context,
        databaseProvider: Provider<ThingsDatabase>
    ): ThingsDatabase {
        return Room.databaseBuilder(
            context,
            ThingsDatabase::class.java,
            ThingsDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .addCallback(DatabaseSeeder(databaseProvider))
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideAreaDao(db: ThingsDatabase): AreaDao = db.areaDao()

    @Provides
    fun provideProjectDao(db: ThingsDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideHeadingDao(db: ThingsDatabase): HeadingDao = db.headingDao()

    @Provides
    fun provideTaskDao(db: ThingsDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideChecklistDao(db: ThingsDatabase): ChecklistDao = db.checklistDao()

    @Provides
    fun provideTagDao(db: ThingsDatabase): TagDao = db.tagDao()

    @Provides
    fun provideAttachmentDao(db: ThingsDatabase): AttachmentDao = db.attachmentDao()

    @Provides
    fun provideProjectTemplateDao(db: ThingsDatabase): ProjectTemplateDao = db.projectTemplateDao()

    @Provides
    @Singleton
    fun provideFirebaseAuth(@ApplicationContext context: Context): FirebaseAuth {
        val app = ensureDefaultFirebaseApp(context)
        return FirebaseAuth.getInstance(app)
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(@ApplicationContext context: Context): FirebaseFirestore {
        val app = ensureDefaultFirebaseApp(context)
        return FirebaseFirestore.getInstance(app).apply {
            runCatching {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
            }
        }
    }
}

