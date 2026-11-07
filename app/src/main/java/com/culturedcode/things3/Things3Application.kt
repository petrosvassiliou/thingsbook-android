package com.culturedcode.things3

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.culturedcode.things3.data.notifications.ReminderScheduler
import com.culturedcode.things3.data.sync.SyncWorker
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class Things3Application : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        runCatching {
            // Remove legacy databases from earlier builds so the new sample projects load cleanly
            deleteDatabase("things3_offline_first.db")
            deleteDatabase("thingsbook_v3_clean.db")
        }
        runCatching {
            FirebaseApp.initializeApp(this)
        }
        runCatching {
            SyncWorker.schedulePeriodicDeltaSync(this)
        }
        runCatching {
            reminderScheduler.ensureDailyDigestScheduledIfEnabled()
        }
    }
}
