package com.culturedcode.things3.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.culturedcode.things3.data.local.DatabaseSeeder
import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.local.dao.AreaDao
import com.culturedcode.things3.data.local.dao.AttachmentDao
import com.culturedcode.things3.data.local.dao.ChecklistDao
import com.culturedcode.things3.data.local.dao.HeadingDao
import com.culturedcode.things3.data.local.dao.ProjectDao
import com.culturedcode.things3.data.local.dao.ProjectTemplateDao
import com.culturedcode.things3.data.local.dao.TagDao
import com.culturedcode.things3.data.local.dao.TaskDao
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

    internal val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN scheduledStartMinutes INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE tasks ADD COLUMN durationMinutes INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE projects ADD COLUMN lastReviewedAt INTEGER DEFAULT NULL")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS project_templates (" +
                    "id TEXT NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "notes TEXT NOT NULL, " +
                    "defaultAreaId TEXT, " +
                    "tagIdsJson TEXT NOT NULL, " +
                    "blueprintJson TEXT NOT NULL, " +
                    "sortOrder REAL NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "isDeleted INTEGER NOT NULL, " +
                    "isDirty INTEGER NOT NULL, " +
                    "PRIMARY KEY(id))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_project_templates_updatedAt ON project_templates(updatedAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_project_templates_isDirty ON project_templates(isDirty)")
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

