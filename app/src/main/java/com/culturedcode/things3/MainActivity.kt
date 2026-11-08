package com.culturedcode.things3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.culturedcode.things3.ui.screens.ThingsSmartViewScreen
import com.culturedcode.things3.ui.theme.Things3Theme
import com.culturedcode.things3.ui.viewmodel.ThingsbookViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            Things3Theme {
                val viewModel: ThingsbookViewModel = hiltViewModel()
                androidx.compose.runtime.LaunchedEffect(intent) {
                    runCatching {
                        val widgetView = intent?.getStringExtra(
                            com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.EXTRA_WIDGET_SMART_VIEW
                        )
                        val widgetTaskId = intent?.getStringExtra(
                            com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.EXTRA_TASK_ID
                        )
                        val widgetQuickAdd = intent?.getBooleanExtra(
                            com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.EXTRA_WIDGET_QUICK_ADD,
                            false
                        ) ?: false

                        if (widgetView != null || widgetTaskId != null || widgetQuickAdd) {
                            viewModel.handleWidgetLaunchIntent(widgetView, widgetTaskId, widgetQuickAdd)
                            intent?.removeExtra(com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.EXTRA_WIDGET_SMART_VIEW)
                            intent?.removeExtra(com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.EXTRA_TASK_ID)
                            intent?.removeExtra(com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.EXTRA_WIDGET_QUICK_ADD)
                        }
                    }
                }
                ThingsSmartViewScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
