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
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleExactReminder(taskId: String, taskTitle: String, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return

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

    fun cancelReminder(taskId: String) {
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
}

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ThingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: context.getString(R.string.entity_todo)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            ACTION_SHOW_REMINDER -> {
                ensureChannel(context, notificationManager)

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
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
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

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.action_reminder),
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "thingsbook_exact_reminders"
        const val ACTION_SHOW_REMINDER = "com.culturedcode.things3.ACTION_SHOW_REMINDER"
        const val ACTION_COMPLETE_TASK = "com.culturedcode.things3.ACTION_COMPLETE_TASK"
        const val ACTION_DEFER_THIS_EVENING = "com.culturedcode.things3.ACTION_DEFER_THIS_EVENING"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
    }
}
