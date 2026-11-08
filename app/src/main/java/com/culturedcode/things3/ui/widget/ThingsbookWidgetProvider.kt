package com.culturedcode.things3.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import com.culturedcode.things3.MainActivity
import com.culturedcode.things3.R
import com.culturedcode.things3.data.repository.ThingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ThingsWidgetEntryPoint {
    fun thingsRepository(): ThingsRepository
}

class ThingsbookWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for (widgetId in appWidgetIds) {
                    updateSingleWidget(context, appWidgetManager, widgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_SWITCH_VIEW -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                val targetView = intent.getStringExtra(EXTRA_TARGET_VIEW) ?: VIEW_TODAY
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    setSelectedViewForWidget(context, widgetId, targetView)
                    val pendingResult = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            val mgr = AppWidgetManager.getInstance(context)
                            mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_task_list)
                            updateSingleWidget(context, mgr, widgetId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            ACTION_ITEM_INTERACTION -> {
                val interactionType = intent.getStringExtra(EXTRA_INTERACTION_TYPE)
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                val viewName = intent.getStringExtra(EXTRA_TARGET_VIEW) ?: VIEW_TODAY

                if (interactionType == INTERACTION_COMPLETE_TASK && !taskId.isNullOrBlank()) {
                    val pendingResult = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            val entryPoint = EntryPointAccessors.fromApplication(
                                context.applicationContext,
                                ThingsWidgetEntryPoint::class.java
                            )
                            entryPoint.thingsRepository().updateTaskStatus(
                                taskId,
                                com.culturedcode.things3.domain.model.TaskStatus.COMPLETED
                            )
                            refreshAllWidgetsSync(context)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                } else {
                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(EXTRA_WIDGET_SMART_VIEW, viewName)
                        if (!taskId.isNullOrBlank()) {
                            putExtra(EXTRA_TASK_ID, taskId)
                        }
                    }
                    context.startActivity(openIntent)
                }
            }

            ACTION_REFRESH_ALL -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        refreshAllWidgetsSync(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (id in appWidgetIds) {
            prefs.remove(prefKeyForWidget(id))
        }
        prefs.apply()
    }

    companion object {
        const val PREFS_NAME = "thingsbook_widget_prefs"
        const val ACTION_SWITCH_VIEW = "com.culturedcode.things3.widget.ACTION_SWITCH_VIEW"
        const val ACTION_ITEM_INTERACTION = "com.culturedcode.things3.widget.ACTION_ITEM_INTERACTION"
        const val ACTION_REFRESH_ALL = "com.culturedcode.things3.widget.ACTION_REFRESH_ALL"

        const val EXTRA_TARGET_VIEW = "extra_target_view"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_INTERACTION_TYPE = "extra_interaction_type"
        const val EXTRA_WIDGET_SMART_VIEW = "extra_widget_smart_view"
        const val EXTRA_WIDGET_QUICK_ADD = "extra_widget_quick_add"

        const val INTERACTION_COMPLETE_TASK = "complete_task"
        const val INTERACTION_OPEN_TASK = "open_task"

        const val VIEW_TODAY = "TODAY"
        const val VIEW_INBOX = "INBOX"
        const val VIEW_UPCOMING = "UPCOMING"
        const val VIEW_ANYTIME = "ANYTIME"
        const val VIEW_SOMEDAY = "SOMEDAY"

        private fun prefKeyForWidget(widgetId: Int) = "selected_view_$widgetId"

        fun getSelectedViewForWidget(context: Context, widgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(prefKeyForWidget(widgetId), VIEW_TODAY) ?: VIEW_TODAY
        }

        fun setSelectedViewForWidget(context: Context, widgetId: Int, viewName: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(prefKeyForWidget(widgetId), viewName)
                .apply()
        }

        fun refreshAllWidgets(context: Context) {
            val intent = Intent(context, ThingsbookWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_ALL
            }
            context.sendBroadcast(intent)
        }

        suspend fun refreshAllWidgetsSync(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ThingsbookWidgetProvider::class.java))
            if (ids.isEmpty()) return
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_task_list)
            for (id in ids) {
                updateSingleWidget(context, mgr, id)
            }
        }

        suspend fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val selectedView = getSelectedViewForWidget(context, appWidgetId)
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ThingsWidgetEntryPoint::class.java
            )
            val tasks = runCatching {
                entryPoint.thingsRepository().getTasksForWidgetView(selectedView)
            }.getOrDefault(emptyList())

            val views = RemoteViews(context.packageName, R.layout.widget_thingsbook)

            // 1. Configure Header Icon, Color, Title & Task Count Badge
            val (iconText, iconColorHex, titleRes) = when (selectedView) {
                VIEW_INBOX -> Triple("📥", "#2B7BF6", R.string.view_inbox)
                VIEW_UPCOMING -> Triple("📅", "#FF3B30", R.string.view_upcoming)
                VIEW_ANYTIME -> Triple("🗂️", "#19A096", R.string.view_anytime)
                VIEW_SOMEDAY -> Triple("📦", "#B89A52", R.string.view_someday)
                else -> Triple("★", "#FFC312", R.string.view_today)
            }

            views.setTextViewText(R.id.widget_view_icon, iconText)
            views.setTextColor(R.id.widget_view_icon, Color.parseColor(iconColorHex))
            views.setTextViewText(R.id.widget_view_title, context.getString(titleRes))
            views.setTextViewText(R.id.widget_task_count, tasks.size.toString())

            // 2. Highlight Active View Tab in Segmented Bar & Attach Click PendingIntents
            val tabs = listOf(
                VIEW_TODAY to R.id.widget_tab_today,
                VIEW_INBOX to R.id.widget_tab_inbox,
                VIEW_UPCOMING to R.id.widget_tab_upcoming,
                VIEW_ANYTIME to R.id.widget_tab_anytime,
                VIEW_SOMEDAY to R.id.widget_tab_someday
            )
            for ((viewCode, tabViewId) in tabs) {
                val isActive = viewCode == selectedView
                views.setInt(
                    tabViewId,
                    "setBackgroundResource",
                    if (isActive) R.drawable.bg_things_widget_tab_active else R.drawable.bg_things_widget_tab_inactive
                )
                views.setTextColor(
                    tabViewId,
                    if (isActive) Color.WHITE else Color.parseColor("#5D6573")
                )

                val switchIntent = Intent(context, ThingsbookWidgetProvider::class.java).apply {
                    action = ACTION_SWITCH_VIEW
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(EXTRA_TARGET_VIEW, viewCode)
                    data = Uri.parse("thingsbook://widget/switch/$appWidgetId/$viewCode")
                }
                val switchPending = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + viewCode.hashCode(),
                    switchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(tabViewId, switchPending)
            }

            // 3. Header Click -> Open App in Selected Smart View
            val openViewIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_WIDGET_SMART_VIEW, selectedView)
                data = Uri.parse("thingsbook://widget/open/$appWidgetId/$selectedView")
            }
            val openViewPending = PendingIntent.getActivity(
                context,
                appWidgetId * 100 + 1,
                openViewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header_open_app, openViewPending)

            // 4. "+" Button Click -> Open App in Selected Smart View & Immediately Create + Focus New To-Do
            val quickAddIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_WIDGET_SMART_VIEW, selectedView)
                putExtra(EXTRA_WIDGET_QUICK_ADD, true)
                data = Uri.parse("thingsbook://widget/quickadd/$appWidgetId/$selectedView")
            }
            val quickAddPending = PendingIntent.getActivity(
                context,
                appWidgetId * 100 + 2,
                quickAddIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_add_task, quickAddPending)

            // 5. Bind RemoteViewsService Adapter for Scrollable Task List
            val serviceIntent = Intent(context, ThingsbookWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_task_list, serviceIntent)
            views.setEmptyView(R.id.widget_task_list, R.id.widget_empty_view)

            // 6. PendingIntent Template for Task Row Click & Direct Checkbox Completion
            val itemTemplateIntent = Intent(context, ThingsbookWidgetProvider::class.java).apply {
                action = ACTION_ITEM_INTERACTION
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("thingsbook://widget/item/$appWidgetId")
            }
            val itemTemplatePending = PendingIntent.getBroadcast(
                context,
                appWidgetId * 100 + 3,
                itemTemplateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_task_list, itemTemplatePending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
