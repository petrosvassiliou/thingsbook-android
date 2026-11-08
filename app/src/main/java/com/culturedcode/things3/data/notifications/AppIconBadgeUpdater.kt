package com.culturedcode.things3.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.culturedcode.things3.MainActivity
import com.culturedcode.things3.R
import com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider

/**
 * Synchronizes the live count of open "Today" tasks (`Heute` + `Heute Abend`) with:
 * 1. Android 8.0+ / ChromeOS Shelf App Icon Notification Badges (`setNumber(count)` + `setShowBadge(true)`).
 * 2. Common Android / OEM Launcher badge count broadcasts (`BADGE_COUNT_UPDATE`, Sony/Samsung/Apex/Nova).
 *
 * Fully non-fatal: every system call is wrapped in `runCatching` and respects user toggle `enabled`.
 */
object AppIconBadgeUpdater {

    private const val TAG = "ThingsAppBadge"
    private const val BADGE_CHANNEL_ID = "thingsbook_today_icon_badge_v1"
    private const val BADGE_NOTIFICATION_ID = 90421

    private var lastAppliedCount: Int = -1
    private var lastAppliedEnabled: Boolean? = null

    fun updateTodayAppBadge(context: Context, todayCount: Int, enabled: Boolean) {
        val safeCount = todayCount.coerceAtLeast(0)
        if (lastAppliedCount == safeCount && lastAppliedEnabled == enabled) return
        lastAppliedCount = safeCount
        lastAppliedEnabled = enabled

        val effectiveCount = if (enabled) safeCount else 0
        dispatchLauncherBadgeBroadcasts(context, effectiveCount)
        updateAndroidSystemBadgeNotification(context, effectiveCount, enabled)
    }

    private fun updateAndroidSystemBadgeNotification(
        context: Context,
        effectiveCount: Int,
        enabled: Boolean
    ) {
        runCatching {
            val nm = NotificationManagerCompat.from(context)
            if (!enabled || effectiveCount <= 0) {
                nm.cancel(BADGE_NOTIFICATION_ID)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val sysNm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (sysNm != null && sysNm.getNotificationChannel(BADGE_CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        BADGE_CHANNEL_ID,
                        "Heute-Kennzeichen am App-Symbol",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Zeigt die Anzahl offener Heute-Aufgaben am Thingsbook-Symbol"
                        setShowBadge(true)
                        setSound(null, null)
                        enableVibration(false)
                    }
                    sysNm.createNotificationChannel(channel)
                }
            }

            val openTodayIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(ThingsbookWidgetProvider.EXTRA_WIDGET_SMART_VIEW, ThingsbookWidgetProvider.VIEW_TODAY)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                BADGE_NOTIFICATION_ID,
                openTodayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val label = if (effectiveCount == 1) {
                "1 Aufgabe für Heute offen"
            } else {
                "$effectiveCount Aufgaben für Heute offen"
            }

            val notification = NotificationCompat.Builder(context, BADGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_thingsbook)
                .setContentTitle(context.getString(R.string.view_today))
                .setContentText(label)
                .setNumber(effectiveCount)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(BADGE_NOTIFICATION_ID, notification)
        }.onFailure { err ->
            Log.w(TAG, "System app icon badge update skipped", err)
        }
    }

    private fun dispatchLauncherBadgeBroadcasts(context: Context, count: Int) {
        runCatching {
            val component = ComponentName(context.packageName, MainActivity::class.java.name)

            // Standard / Samsung / Apex / Lawnchair / Nova badge broadcast
            val badgeIntent = Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
                putExtra("badge_count", count)
                putExtra("badge_count_package_name", component.packageName)
                putExtra("badge_count_class_name", component.className)
            }
            context.sendBroadcast(badgeIntent)
        }.onFailure {
            // Silently ignore on launchers without broadcast receivers
        }
    }
}
