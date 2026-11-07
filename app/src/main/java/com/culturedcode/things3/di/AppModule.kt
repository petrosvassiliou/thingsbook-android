package com.culturedcode.things3.di

import android.content.Context
import androidx.room.Room
import com.culturedcode.things3.data.local.DatabaseSeeder
import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.local.dao.AreaDao
import com.culturedcode.things3.data.local.dao.ChecklistDao
import com.culturedcode.things3.data.local.dao.HeadingDao
import com.culturedcode.things3.data.local.dao.ProjectDao
import com.culturedcode.things3.data.local.dao.TagDao
import com.culturedcode.things3.data.local.dao.TaskDao
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
            .addCallback(DatabaseSeeder(databaseProvider))
            .fallbackToDestructiveMigration()
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
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance().apply {
            runCatching {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
            }
        }
    }
}
