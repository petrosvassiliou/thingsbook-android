package com.pv.thingsbook.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.pv.thingsbook.MainActivity
import com.pv.thingsbook.R

/**
 * Android System Quick Settings Tile ("New To-Do") for Thingsbook v1.8.0.
 * Allows 1-swipe instant task capture into Thingsbook from any app or screen.
 */
class ThingsbookQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        runCatching {
            val localizedCtx = ContextCompat.getContextForLanguage(this)
            val tile = qsTile ?: return
            tile.state = Tile.STATE_ACTIVE
            tile.label = localizedCtx.getString(R.string.qs_tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = localizedCtx.getString(R.string.app_name)
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        runCatching {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_shortcut_action", "NEW_TODO")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    901,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(launchIntent)
            }
        }
    }
}
