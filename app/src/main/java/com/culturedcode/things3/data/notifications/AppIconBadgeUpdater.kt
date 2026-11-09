package com.culturedcode.things3.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.culturedcode.things3.MainActivity
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.TaskbarBadgeResolver
import com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Synchronizes the live count of open "Today" tasks (`Heute` + `Heute Abend`) with:
 * 1. Running window `ActivityManager.TaskDescription` pre-rendered `Bitmap` icon & title (`"Thingsbook (3)"`).
 * 2. Taskbar right-click / long-press dynamic shortcut (`ShortcutManagerCompat`) with pre-rendered `Bitmap` badge icon.
 * 3. Android 8.0+ / ChromeOS Shelf / OEM Launcher notification badge (`setNumber(count)` + `setShowBadge(true)` + `largeIcon`).
 *
 * IMPORTANT: Never disables or toggles `<activity-alias>` components via `PackageManager.setComponentEnabledSetting`,
 * because Android's `system_server` (`RootWindowContainer.finishDisabledPackageActivities`) forcibly terminates
 * the running task whenever its launch alias is disabled.
 */
object AppIconBadgeUpdater {

    private const val TAG = "ThingsAppBadge"
    private const val BADGE_CHANNEL_ID = "thingsbook_today_icon_badge_v2"
    private const val BADGE_NOTIFICATION_ID = 90421
    private const val TODAY_SHORTCUT_ID = "thingsbook_today_taskbar_count"

    private val badgeIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastKnownTodayCount: Int = 0

    @Volatile
    private var lastKnownEnabled: Boolean = true

    @Volatile
    private var lastAppliedCount: Int = -1

    @Volatile
    private var lastAppliedEnabled: Boolean? = null

    @DrawableRes
    fun getTaskbarIconDrawableRes(todayCount: Int, enabled: Boolean): Int {
        return when (TaskbarBadgeResolver.resolveBadgeKey(todayCount, enabled)) {
            "1" -> R.drawable.ic_taskbar_icon_badge_1
            "2" -> R.drawable.ic_taskbar_icon_badge_2
            "3" -> R.drawable.ic_taskbar_icon_badge_3
            "4" -> R.drawable.ic_taskbar_icon_badge_4
            "5" -> R.drawable.ic_taskbar_icon_badge_5
            "6" -> R.drawable.ic_taskbar_icon_badge_6
            "7" -> R.drawable.ic_taskbar_icon_badge_7
            "8" -> R.drawable.ic_taskbar_icon_badge_8
            "9" -> R.drawable.ic_taskbar_icon_badge_9
            "10" -> R.drawable.ic_taskbar_icon_badge_10
            "11" -> R.drawable.ic_taskbar_icon_badge_11
            "12" -> R.drawable.ic_taskbar_icon_badge_12
            "13" -> R.drawable.ic_taskbar_icon_badge_13
            "14" -> R.drawable.ic_taskbar_icon_badge_14
            "15" -> R.drawable.ic_taskbar_icon_badge_15
            "16" -> R.drawable.ic_taskbar_icon_badge_16
            "17" -> R.drawable.ic_taskbar_icon_badge_17
            "18" -> R.drawable.ic_taskbar_icon_badge_18
            "19" -> R.drawable.ic_taskbar_icon_badge_19
            "20" -> R.drawable.ic_taskbar_icon_badge_20
            "20plus" -> R.drawable.ic_taskbar_icon_badge_20plus
            else -> R.drawable.ic_taskbar_icon_badge_0
        }
    }

    fun renderTaskbarIconBitmap(
        context: Context,
        todayCount: Int,
        enabled: Boolean,
        sizePx: Int = 144
    ): Bitmap? {
        return runCatching {
            val resId = getTaskbarIconDrawableRes(todayCount, enabled)
            val drawable = AppCompatResources.getDrawable(context, resId)?.mutate() ?: return null
            val safeSize = sizePx.coerceIn(48, 256)
            val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, safeSize, safeSize)
            drawable.draw(canvas)
            bitmap
        }.getOrNull()
    }

    fun updateTodayAppBadge(context: Context, todayCount: Int, enabled: Boolean) {
        val safeCount = todayCount.coerceAtLeast(0)
        lastKnownTodayCount = safeCount
        lastKnownEnabled = enabled

        val effectiveCount = TaskbarBadgeResolver.resolveEffectiveCount(safeCount, enabled)
        if (lastAppliedCount == effectiveCount && lastAppliedEnabled == enabled) return
        lastAppliedCount = effectiveCount
        lastAppliedEnabled = enabled

        // 1. Live window TaskDescription on active MainActivity (uses pre-rendered Bitmap safely)
        MainActivity.applyActiveWindowTaskbarBadge(safeCount, enabled)

        // 2. Perform all system/launcher IPC updates asynchronously off the Main UI thread
        val appCtx = context.applicationContext
        badgeIoScope.launch {
            val badgeBitmap = renderTaskbarIconBitmap(appCtx, effectiveCount, enabled, 144)
            updateDynamicTaskbarShortcut(appCtx, effectiveCount, enabled, badgeBitmap)
            dispatchLauncherBadgeBroadcasts(appCtx, effectiveCount)
            updateAndroidSystemBadgeNotification(appCtx, effectiveCount, enabled, badgeBitmap)
        }
    }

    fun refreshAfterPermissionGranted(context: Context) {
        lastAppliedCount = -1
        lastAppliedEnabled = null
        updateTodayAppBadge(context, lastKnownTodayCount, lastKnownEnabled)
    }

    fun currentTodayCount(): Int = lastKnownTodayCount

    fun isBadgeEnabled(): Boolean = lastKnownEnabled

    private fun updateDynamicTaskbarShortcut(
        context: Context,
        effectiveCount: Int,
        enabled: Boolean,
        badgeBitmap: Bitmap?
    ) {
        runCatching {
            if (!enabled || effectiveCount <= 0) {
                ShortcutManagerCompat.removeDynamicShortcuts(
                    context,
                    listOf(TODAY_SHORTCUT_ID)
                )
                return
            }

            val openTodayIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(
                    ThingsbookWidgetProvider.EXTRA_WIDGET_SMART_VIEW,
                    ThingsbookWidgetProvider.VIEW_TODAY
                )
            }

            val shortLabel = "Heute ($effectiveCount)"
            val longLabel = if (effectiveCount == 1) {
                "Heute • 1 offene Aufgabe"
            } else {
                "Heute • $effectiveCount offene Aufgaben"
            }

            val iconCompat = if (badgeBitmap != null) {
                IconCompat.createWithBitmap(badgeBitmap)
            } else {
                IconCompat.createWithResource(context, R.mipmap.ic_launcher)
            }

            val shortcut = ShortcutInfoCompat.Builder(context, TODAY_SHORTCUT_ID)
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(iconCompat)
                .setIntent(openTodayIntent)
                .setRank(0)
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }.onFailure { err ->
            Log.w(TAG, "Dynamic taskbar shortcut update skipped", err)
        }
    }

    private fun updateAndroidSystemBadgeNotification(
        context: Context,
        effectiveCount: Int,
        enabled: Boolean,
        badgeBitmap: Bitmap?
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
                if (!granted) {
                    // Reset cached count so once permission is granted it will re-post
                    lastAppliedCount = -1
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val sysNm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (sysNm != null && sysNm.getNotificationChannel(BADGE_CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        BADGE_CHANNEL_ID,
                        "Heute-Kennzeichen am Taskbar-Symbol",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Zeigt die Anzahl offener Heute-Aufgaben am Taskbar-Symbol"
                        setShowBadge(true)
                        setSound(null, null)
                        enableVibration(false)
                    }
                    sysNm.createNotificationChannel(channel)
                }
            }

            val openTodayIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(
                    ThingsbookWidgetProvider.EXTRA_WIDGET_SMART_VIEW,
                    ThingsbookWidgetProvider.VIEW_TODAY
                )
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

            val builder = NotificationCompat.Builder(context, BADGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_thingsbook)
                .setContentTitle("${context.getString(R.string.view_today)} ($effectiveCount)")
                .setContentText(label)
                .setNumber(effectiveCount)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)

            if (badgeBitmap != null) {
                builder.setLargeIcon(badgeBitmap)
            }

            nm.notify(BADGE_NOTIFICATION_ID, builder.build())
        }.onFailure { err ->
            Log.w(TAG, "System app icon badge update skipped", err)
        }
    }

    private fun dispatchLauncherBadgeBroadcasts(context: Context, count: Int) {
        runCatching {
            val component = ComponentName(context.packageName, MainActivity::class.java.name)
            val badgeIntent = Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
                putExtra("badge_count", count)
                putExtra("badge_count_package_name", component.packageName)
                putExtra("badge_count_class_name", component.className)
            }
            context.sendBroadcast(badgeIntent)
        }
    }
}
