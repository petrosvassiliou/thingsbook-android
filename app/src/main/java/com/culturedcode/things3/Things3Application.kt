package com.culturedcode.things3

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.culturedcode.things3.data.notifications.ReminderScheduler
import com.culturedcode.things3.data.sync.SyncWorker
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Global Crash Shield ("CrashGuardian"):
 * - Swallows uncaught exceptions on all background threads (Coroutines, Room I/O, WorkManager,
 *   Widget updates, Firestore callbacks, UDP sockets) so they can NEVER kill the app process.
 * - Catches uncaught exceptions on the Android Main UI Thread by re-entering `Looper.loop()`
 *   and notifying `MainActivity` to recover or show a diagnostic repair banner instead of
 *   terminating with "App keeps stopping / wurde beendet".
 */
object CrashGuardian {
    private const val TAG = "ThingsbookCrashGuardian"
    private const val PREFS_NAME = "thingsbook_crash_guardian"
    private const val KEY_LAST_CRASH = "last_crash_trace"

    @Volatile
    private var installed = false

    @Volatile
    private var storedAppContext: Context? = null

    @Volatile
    var lastCaughtCrashTrace: String? = null
        private set

    fun install(context: Context?) {
        val candidateCtx = context?.applicationContext ?: context
        if (candidateCtx != null && (storedAppContext == null || context?.applicationContext != null)) {
            storedAppContext = candidateCtx
            runCatching {
                val prefs = candidateCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (lastCaughtCrashTrace == null) {
                    lastCaughtCrashTrace = prefs.getString(KEY_LAST_CRASH, null)
                }
            }
        }

        if (installed) return
        installed = true
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val appCtx = storedAppContext
            recordCrash(appCtx, thread, throwable)

            // Never attempt to spin Looper on unrecoverable JVM VirtualMachineError (OutOfMemoryError, StackOverflowError)
            if (throwable is VirtualMachineError) {
                previousHandler?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            val isMainThread = runCatching {
                Looper.getMainLooper()?.thread === thread
            }.getOrDefault(false)

            if (!isMainThread) {
                // Background thread exception: log and swallow so the app process stays alive!
                Log.e(TAG, "Swallowed uncaught exception on background thread [${thread.name}]", throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            // Main UI Thread exception: notify MainActivity and keep Looper alive with ANR circuit breaker!
            Log.e(TAG, "Caught Main UI Thread exception — keeping Looper alive", throwable)
            MainActivity.notifyMainThreadCrash(throwable)

            var rapidCrashCount = 0
            var windowStartMs = System.currentTimeMillis()
            while (true) {
                try {
                    Looper.loop()
                } catch (loopErr: Throwable) {
                    recordCrash(storedAppContext, Thread.currentThread(), loopErr)
                    if (loopErr is VirtualMachineError) {
                        previousHandler?.uncaughtException(thread, loopErr)
                        break
                    }
                    val now = System.currentTimeMillis()
                    if (now - windowStartMs < 2000L) {
                        rapidCrashCount += 1
                    } else {
                        rapidCrashCount = 1
                        windowStartMs = now
                    }
                    Log.e(TAG, "Caught subsequent Main UI Thread exception (#$rapidCrashCount) inside Looper guard", loopErr)
                    MainActivity.notifyMainThreadCrash(loopErr)
                    if (rapidCrashCount > 5) {
                        Log.e(TAG, "Rapid Main UI Thread crash loop detected — delegating to system handler to avoid ANR")
                        previousHandler?.uncaughtException(thread, loopErr)
                        break
                    }
                }
            }
        }
    }

    fun recordCrash(context: Context?, thread: Thread, throwable: Throwable) {
        runCatching {
            val targetCtx = context ?: storedAppContext
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val fullReport = "[$timestamp] Thread: ${thread.name}\n" +
                "${throwable::class.java.name}: ${throwable.message}\n$sw"
            lastCaughtCrashTrace = fullReport
            targetCtx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(KEY_LAST_CRASH, fullReport)
                ?.apply()
        }
    }

    fun clearCrashLog(context: Context?) {
        lastCaughtCrashTrace = null
        runCatching {
            val targetCtx = context ?: storedAppContext
            targetCtx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.remove(KEY_LAST_CRASH)
                ?.apply()
        }
    }
}

@HiltAndroidApp
class Things3Application : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun attachBaseContext(base: Context?) {
        CrashGuardian.install(base)
        super.attachBaseContext(base)
        CrashGuardian.install(this)
    }

    override val workManagerConfiguration: Configuration
        get() = runCatching {
            if (::workerFactory.isInitialized) {
                Configuration.Builder()
                    .setWorkerFactory(workerFactory)
                    .build()
            } else {
                Configuration.Builder().build()
            }
        }.getOrElse {
            Configuration.Builder().build()
        }

    override fun onCreate() {
        CrashGuardian.install(this)
        runCatching {
            FirebaseApp.initializeApp(this)
        }
        super.onCreate()
        runCatching {
            // Remove legacy databases from earlier builds so the new sample projects load cleanly
            deleteDatabase("things3_offline_first.db")
            deleteDatabase("thingsbook_v3_clean.db")
        }
        // Defer non-critical background schedulers slightly so startup UI renders with zero contention
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                // Ensure any legacy desktop/taskbar badge artifacts from pre-1.6.9 installs are cleared
                NotificationManagerCompat.from(this).cancel(90421)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val sysNm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    sysNm?.deleteNotificationChannel("thingsbook_today_icon_badge_v2")
                }
                ShortcutManagerCompat.removeDynamicShortcuts(
                    this,
                    listOf("thingsbook_today_taskbar_count")
                )
            }
            runCatching {
                SyncWorker.schedulePeriodicDeltaSync(this)
            }
            runCatching {
                if (::reminderScheduler.isInitialized) {
                    reminderScheduler.ensureDailyDigestScheduledIfEnabled()
                }
            }
        }, 1500L)
    }
}

