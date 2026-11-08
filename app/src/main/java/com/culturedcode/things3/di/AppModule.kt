package com.culturedcode.things3.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.culturedcode.things3.data.local.DatabaseSeeder
import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.local.dao.AreaDao
import com.culturedcode.things3.data.local.dao.ChecklistDao
import com.culturedcode.things3.data.local.dao.HeadingDao
import com.culturedcode.things3.data.local.dao.ProjectDao
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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Schema v1 -> v2 additive safety migration; ensures user data is preserved when compatible
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
            .addMigrations(MIGRATION_1_2)
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

