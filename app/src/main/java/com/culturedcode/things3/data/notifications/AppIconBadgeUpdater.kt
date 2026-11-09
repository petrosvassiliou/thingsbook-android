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
import androidx.annotation.DrawableRes
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

/**
 * Synchronizes the live count of open "Today" tasks (`Heute` + `Heute Abend`) with:
 * 1. Dynamic Taskbar & Launcher `<activity-alias>` icons (`LauncherBadge0` .. `LauncherBadge20Plus`)
 *    targeting [com.culturedcode.things3.LauncherEntryActivity] so ChromeOS Shelf / Taskbar and
 *    Android Launchers display the exact red Today count badge directly on the Thingsbook icon
 *    without ever closing or interrupting [MainActivity].
 * 2. Running window `ActivityManager.TaskDescription` icon & title (`"Thingsbook (3)"`).
 * 3. Taskbar right-click / long-press dynamic shortcut (`ShortcutManagerCompat`).
 * 4. Android 8.0+ / OEM Launcher notification badge (`setNumber(count)` + `setShowBadge(true)`).
 */
object AppIconBadgeUpdater {

    private const val TAG = "ThingsAppBadge"
    private const val BADGE_CHANNEL_ID = "thingsbook_today_icon_badge_v2"
    private const val BADGE_NOTIFICATION_ID = 90421
    private const val TODAY_SHORTCUT_ID = "thingsbook_today_taskbar_count"

    @Volatile
    private var lastKnownTodayCount: Int = 0

    @Volatile
    private var lastKnownEnabled: Boolean = true

    @Volatile
    private var lastAppliedAliasSuffix: String? = null

    @Volatile
    private var lastAppliedNotificationCount: Int = -1

    @Volatile
    private var lastAppliedNotificationEnabled: Boolean? = null

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

    fun updateTodayAppBadge(context: Context, todayCount: Int, enabled: Boolean) {
        val safeCount = todayCount.coerceAtLeast(0)
        lastKnownTodayCount = safeCount
        lastKnownEnabled = enabled

        val effectiveCount = TaskbarBadgeResolver.resolveEffectiveCount(safeCount, enabled)

        // 1. Live window TaskDescription on active MainActivity (Taskbar / Overview icon & title)
        MainActivity.applyActiveWindowTaskbarBadge(safeCount, enabled)

        // 2. Dynamic Taskbar / Shelf / Launcher <activity-alias> icon
        updateLauncherActivityAlias(context, safeCount, enabled)

        // 3. Taskbar right-click / long-press Dynamic Shortcut with badge icon
        updateDynamicTaskbarShortcut(context, effectiveCount, enabled)

        // 4. OEM & standard launcher badge broadcasts + system notification badge
        dispatchLauncherBadgeBroadcasts(context, effectiveCount)
        updateAndroidSystemBadgeNotification(context, effectiveCount, enabled)
    }

    fun refreshAfterPermissionGranted(context: Context) {
        lastAppliedNotificationCount = -1
        lastAppliedNotificationEnabled = null
        updateTodayAppBadge(context, lastKnownTodayCount, lastKnownEnabled)
    }

    fun currentTodayCount(): Int = lastKnownTodayCount

    fun isBadgeEnabled(): Boolean = lastKnownEnabled

    private fun updateLauncherActivityAlias(
        context: Context,
        todayCount: Int,
        enabled: Boolean
    ) {
        runCatching {
            val targetSuffix = TaskbarBadgeResolver.resolveAliasSuffix(todayCount, enabled)
            if (lastAppliedAliasSuffix == targetSuffix) return

            val pm = context.packageManager
            val pkg = context.packageName
            val allSuffixes = TaskbarBadgeResolver.allAliasSuffixes()
            val targetComponent = ComponentName(pkg, "$pkg.LauncherBadge$targetSuffix")

            // Enable the target alias FIRST so at least one LAUNCHER component is always active
            val currentTargetState = pm.getComponentEnabledSetting(targetComponent)
            val shouldEnableTarget = if (targetSuffix == "0") {
                currentTargetState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
                    currentTargetState != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            } else {
                currentTargetState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            if (shouldEnableTarget) {
                pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            // Disable any other alias that is currently enabled
            for (suffix in allSuffixes) {
                if (suffix == targetSuffix) continue
                val comp = ComponentName(pkg, "$pkg.LauncherBadge$suffix")
                val state = pm.getComponentEnabledSetting(comp)
                val isCurrentlyEnabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (suffix == "0" && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                if (isCurrentlyEnabled) {
                    pm.setComponentEnabledSetting(
                        comp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }

            lastAppliedAliasSuffix = targetSuffix
        }.onFailure { err ->
            Log.w(TAG, "Taskbar launcher alias update skipped", err)
        }
    }

    private fun updateDynamicTaskbarShortcut(
        context: Context,
        effectiveCount: Int,
        enabled: Boolean
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
            val iconRes = getTaskbarIconDrawableRes(effectiveCount, true)

            val shortcut = ShortcutInfoCompat.Builder(context, TODAY_SHORTCUT_ID)
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(IconCompat.createWithResource(context, iconRes))
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
        enabled: Boolean
    ) {
        runCatching {
            if (lastAppliedNotificationCount == effectiveCount &&
                lastAppliedNotificationEnabled == enabled
            ) {
                return
            }

            val nm = NotificationManagerCompat.from(context)
            if (!enabled || effectiveCount <= 0) {
                nm.cancel(BADGE_NOTIFICATION_ID)
                lastAppliedNotificationCount = 0
                lastAppliedNotificationEnabled = enabled
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

            val notification = NotificationCompat.Builder(context, BADGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_thingsbook)
                .setContentTitle("${context.getString(R.string.view_today)} ($effectiveCount)")
                .setContentText(label)
                .setNumber(effectiveCount)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(BADGE_NOTIFICATION_ID, notification)
            lastAppliedNotificationCount = effectiveCount
            lastAppliedNotificationEnabled = true
        }.onFailure { err ->
            Log.w(TAG, "System app icon badge update skipped", err)
        }
    }

    private fun dispatchLauncherBadgeBroadcasts(context: Context, count: Int) {
        runCatching {
            val pkg = context.packageName
            val activeSuffix = TaskbarBadgeResolver.resolveAliasSuffix(count, count > 0)
            val component = ComponentName(pkg, "$pkg.LauncherBadge$activeSuffix")

            val badgeIntent = Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
                putExtra("badge_count", count)
                putExtra("badge_count_package_name", component.packageName)
                putExtra("badge_count_class_name", component.className)
            }
            context.sendBroadcast(badgeIntent)
        }
    }
}
