package com.culturedcode.things3.data.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.culturedcode.things3.MainActivity
import com.culturedcode.things3.R
import com.culturedcode.things3.data.repository.ThingsRepository
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.TaskStatus
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class DailyDigestConfig(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int
)

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDailyDigestConfig(): DailyDigestConfig {
        return DailyDigestConfig(
            enabled = prefs.getBoolean(KEY_DAILY_ENABLED, true),
            hour = prefs.getInt(KEY_DAILY_HOUR, 8),
            minute = prefs.getInt(KEY_DAILY_MINUTE, 30)
        )
    }

    fun updateDailyDigestSchedule(enabled: Boolean, hour: Int, minute: Int) {
        prefs.edit()
            .putBoolean(KEY_DAILY_ENABLED, enabled)
            .putInt(KEY_DAILY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_DAILY_MINUTE, minute.coerceIn(0, 59))
            .apply()

        if (enabled) {
            scheduleNextDailyDigestAlarm(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        } else {
            cancelDailyDigestAlarm()
        }
    }

    fun ensureDailyDigestScheduledIfEnabled() {
        val cfg = getDailyDigestConfig()
        if (cfg.enabled) {
            scheduleNextDailyDigestAlarm(cfg.hour, cfg.minute)
        }
    }

    fun scheduleNextDailyDigestAlarm(hour: Int, minute: Int) = runCatching {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var nextRun = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1)
        }
        val triggerAtMillis = nextRun.atZone(zone).toInstant().toEpochMilli()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW_DAILY_DIGEST
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_DIGEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelDailyDigestAlarm() = runCatching {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW_DAILY_DIGEST
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_DIGEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun triggerDailyDigestNotificationNow() = runCatching {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW_DAILY_DIGEST
            putExtra(ReminderReceiver.EXTRA_IS_MANUAL_TEST, true)
        }
        context.sendBroadcast(intent)
    }

    fun scheduleExactReminder(taskId: String, taskTitle: String, triggerAtMillis: Long) = runCatching {
        if (triggerAtMillis <= System.currentTimeMillis()) return@runCatching

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW_REMINDER
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_TASK_TITLE, taskTitle)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelReminder(taskId: String) = runCatching {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    companion object {
        private const val PREFS_NAME = "thingsbook_notification_prefs"
        private const val KEY_DAILY_ENABLED = "daily_digest_enabled"
        private const val KEY_DAILY_HOUR = "daily_digest_hour"
        private const val KEY_DAILY_MINUTE = "daily_digest_minute"
        private const val DAILY_DIGEST_REQUEST_CODE = 777001
    }
}

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ThingsRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                reminderScheduler.ensureDailyDigestScheduledIfEnabled()
            }

            ACTION_SHOW_DAILY_DIGEST -> {
                val isManualTest = intent.getBooleanExtra(EXTRA_IS_MANUAL_TEST, false)
                if (!isManualTest) {
                    reminderScheduler.ensureDailyDigestScheduledIfEnabled()
                }
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        ensureChannels(context, notificationManager)
                        val activeTitles = runCatching { repository.getOpenActiveTasksForToday() }
                            .getOrDefault(emptyList())

                        val openAppIntent = PendingIntent.getActivity(
                            context,
                            DAILY_NOTIFICATION_ID,
                            Intent(context, MainActivity::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val title = if (activeTitles.isNotEmpty()) {
                            context.getString(R.string.notifications_daily_digest_header, activeTitles.size)
                        } else {
                            context.getString(R.string.notifications_daily_digest_channel)
                        }

                        val bodySummary = if (activeTitles.isNotEmpty()) {
                            activeTitles.joinToString(separator = "\n") { "• $it" }
                        } else {
                            context.getString(R.string.notifications_daily_digest_empty)
                        }

                        val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(title)
                        if (activeTitles.isEmpty()) {
                            inboxStyle.addLine(bodySummary)
                        } else {
                            activeTitles.take(7).forEach { inboxStyle.addLine("• $it") }
                        }

                        val notification = NotificationCompat.Builder(context, DAILY_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notification_thingsbook)
                            .setContentTitle(title)
                            .setContentText(
                                activeTitles.firstOrNull()
                                    ?: context.getString(R.string.notifications_daily_digest_empty)
                            )
                            .setStyle(inboxStyle)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .setContentIntent(openAppIntent)
                            .build()

                        notificationManager.notify(DAILY_NOTIFICATION_ID, notification)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_SHOW_REMINDER -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: context.getString(R.string.entity_todo)
                ensureChannels(context, notificationManager)

                val openAppIntent = PendingIntent.getActivity(
                    context,
                    taskId.hashCode(),
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val completeIntent = PendingIntent.getBroadcast(
                    context,
                    (taskId + "_complete").hashCode(),
                    Intent(context, ReminderReceiver::class.java).apply {
                        action = ACTION_COMPLETE_TASK
                        putExtra(EXTRA_TASK_ID, taskId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val eveningIntent = PendingIntent.getBroadcast(
                    context,
                    (taskId + "_evening").hashCode(),
                    Intent(context, ReminderReceiver::class.java).apply {
                        action = ACTION_DEFER_THIS_EVENING
                        putExtra(EXTRA_TASK_ID, taskId)
                        putExtra(EXTRA_TASK_TITLE, taskTitle)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_thingsbook)
                    .setContentTitle(taskTitle)
                    .setContentText(context.getString(R.string.action_reminder))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent)
                    .addAction(
                        android.R.drawable.checkbox_on_background,
                        context.getString(R.string.action_complete),
                        completeIntent
                    )
                    .addAction(
                        android.R.drawable.ic_menu_recent_history,
                        context.getString(R.string.view_this_evening),
                        eveningIntent
                    )
                    .build()

                notificationManager.notify(taskId.hashCode(), notification)
            }

            ACTION_COMPLETE_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                notificationManager.cancel(taskId.hashCode())
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        repository.updateTaskStatus(taskId, TaskStatus.COMPLETED)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_DEFER_THIS_EVENING -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: context.getString(R.string.entity_todo)
                notificationManager.cancel(taskId.hashCode())
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val todayEpoch = LocalDate.now(ZoneId.systemDefault())
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        repository.createOrUpdateTask(
                            id = taskId,
                            title = taskTitle,
                            scheduleState = ScheduleState.THIS_EVENING,
                            whenDate = todayEpoch
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun ensureChannels(context: Context, manager: NotificationManager) {
        val exactChannel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.action_reminder),
            NotificationManager.IMPORTANCE_HIGH
        )
        val dailyChannel = NotificationChannel(
            DAILY_CHANNEL_ID,
            context.getString(R.string.notifications_daily_digest_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(exactChannel)
        manager.createNotificationChannel(dailyChannel)
    }

    companion object {
        const val CHANNEL_ID = "thingsbook_exact_reminders"
        const val DAILY_CHANNEL_ID = "thingsbook_daily_digest"
        const val DAILY_NOTIFICATION_ID = 888001
        const val ACTION_SHOW_REMINDER = "com.culturedcode.things3.ACTION_SHOW_REMINDER"
        const val ACTION_SHOW_DAILY_DIGEST = "com.culturedcode.things3.ACTION_SHOW_DAILY_DIGEST"
        const val ACTION_COMPLETE_TASK = "com.culturedcode.things3.ACTION_COMPLETE_TASK"
        const val ACTION_DEFER_THIS_EVENING = "com.culturedcode.things3.ACTION_DEFER_THIS_EVENING"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_IS_MANUAL_TEST = "extra_is_manual_test"
    }
}
