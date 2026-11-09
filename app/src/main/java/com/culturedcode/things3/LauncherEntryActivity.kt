package com.culturedcode.things3

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Lightweight `Theme.NoDisplay` entry trampoline targeted by `<activity-alias>` launcher icons
 * (`LauncherBadge0` .. `LauncherBadge20Plus`).
 *
 * Because this trampoline finishes immediately in [onCreate] after forwarding to [MainActivity],
 * the active window's `ActivityRecord` always belongs to [MainActivity] (which stays permanently
 * enabled). This allows [com.culturedcode.things3.data.notifications.AppIconBadgeUpdater] to
 * switch the enabled Taskbar / Shelf `<activity-alias>` icon dynamically in the background
 * without ever closing or interrupting [MainActivity].
 */
class LauncherEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            val forwardIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                intent?.extras?.let { putExtras(it) }
                intent?.data?.let { data = it }
            }
            startActivity(forwardIntent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
