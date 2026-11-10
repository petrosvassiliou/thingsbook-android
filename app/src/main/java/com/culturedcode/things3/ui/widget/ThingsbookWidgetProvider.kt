package com.culturedcode.things3.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import com.culturedcode.things3.MainActivity
import com.culturedcode.things3.R
import com.culturedcode.things3.data.repository.ThingsRepository
import com.culturedcode.things3.domain.util.runCatchingCancellable
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ThingsWidgetEntryPoint {
    fun thingsRepository(): ThingsRepository
}

class ThingsbookWidgetProvider : AppWidgetProvider() {

    private val widgetScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.w(TAG, "Uncaught widget coroutine error", throwable)
        }
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        val mgr = appWidgetManager ?: runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
        val ids = appWidgetIds ?: return
        if (ids.isEmpty()) return

        val pendingResult = runCatching { goAsync() }.getOrNull()
        widgetScope.launch {
            try {
                withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                    for (widgetId in ids) {
                        runCatchingCancellable {
                            updateSingleWidget(context, mgr, widgetId)
                        }.onFailure { err ->
                            Log.w(TAG, "Failed updating widget $widgetId", err)
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "onUpdate failed", t)
            } finally {
                runCatching { pendingResult?.finish() }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        runCatching {
            super.onReceive(context, intent)
        }.onFailure { err ->
            Log.w(TAG, "super.onReceive failed for ${intent.action}", err)
        }
        when (intent.action) {
            ACTION_SWITCH_VIEW -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                val targetView = normalizeWidgetView(intent.getStringExtra(EXTRA_TARGET_VIEW))
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    setSelectedViewForWidget(context, widgetId, targetView)
                    val pendingResult = runCatching { goAsync() }.getOrNull()
                    widgetScope.launch {
                        try {
                            withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                                val mgr: AppWidgetManager? = runCatching { AppWidgetManager.getInstance(context) }.getOrNull()
                                if (mgr != null) {
                                    // Required for minSdk = 26..30 RemoteViewsService adapter refresh
                                    @Suppress("DEPRECATION")
                                    mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_task_list)
                                    updateSingleWidget(context, mgr, widgetId)
                                }
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            Log.w(TAG, "ACTION_SWITCH_VIEW failed", t)
                        } finally {
                            runCatching { pendingResult?.finish() }
                        }
                    }
                }
            }

            ACTION_ITEM_INTERACTION -> {
                val interactionType = intent.getStringExtra(EXTRA_INTERACTION_TYPE)
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                val viewName = normalizeWidgetView(intent.getStringExtra(EXTRA_TARGET_VIEW))

                if (interactionType == INTERACTION_COMPLETE_TASK && !taskId.isNullOrBlank()) {
                    val pendingResult = runCatching { goAsync() }.getOrNull()
                    widgetScope.launch {
                        try {
                            withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                                val entryPoint = EntryPointAccessors.fromApplication(
                                    context.applicationContext,
                                    ThingsWidgetEntryPoint::class.java
                                )
                                entryPoint.thingsRepository().updateTaskStatus(
                                    taskId,
                                    com.culturedcode.things3.domain.model.TaskStatus.COMPLETED
                                )
                                refreshAllWidgetsSync(context)
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            Log.w(TAG, "ACTION_ITEM_INTERACTION complete failed", t)
                        } finally {
                            runCatching { pendingResult?.finish() }
                        }
                    }
                } else {
                    runCatching {
                        val openIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(EXTRA_WIDGET_SMART_VIEW, viewName)
                            if (!taskId.isNullOrBlank()) {
                                putExtra(EXTRA_TASK_ID, taskId)
                            }
                        }
                        context.startActivity(openIntent)
                    }.onFailure { err ->
                        Log.w(TAG, "Failed launching MainActivity from widget", err)
                    }
                }
            }

            ACTION_REFRESH_ALL -> {
                val pendingResult = runCatching { goAsync() }.getOrNull()
                widgetScope.launch {
                    try {
                        withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                            refreshAllWidgetsSync(context)
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        Log.w(TAG, "ACTION_REFRESH_ALL failed", t)
                    } finally {
                        runCatching { pendingResult?.finish() }
                    }
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray?) {
        runCatching {
            super.onDeleted(context, appWidgetIds)
            if (appWidgetIds == null) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            for (id in appWidgetIds) {
                prefs.remove(prefKeyForWidget(id))
            }
            prefs.apply()
        }.onFailure { err ->
            Log.w(TAG, "onDeleted cleanup failed", err)
        }
    }

    companion object {
        private const val TAG = "ThingsWidget"
        private const val RECEIVER_TIMEOUT_MS = 8_000L

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

        private val VALID_VIEWS = setOf(VIEW_TODAY, VIEW_INBOX, VIEW_UPCOMING, VIEW_ANYTIME, VIEW_SOMEDAY)

        private fun normalizeWidgetView(raw: String?): String {
            val normalized = raw?.trim()?.uppercase(Locale.ROOT) ?: VIEW_TODAY
            return if (normalized in VALID_VIEWS) normalized else VIEW_TODAY
        }

        private fun prefKeyForWidget(widgetId: Int) = "selected_view_$widgetId"

        fun getSelectedViewForWidget(context: Context, widgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return normalizeWidgetView(prefs.getString(prefKeyForWidget(widgetId), VIEW_TODAY))
        }

        fun setSelectedViewForWidget(context: Context, widgetId: Int, viewName: String) {
            runCatching {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(prefKeyForWidget(widgetId), normalizeWidgetView(viewName))
                    .apply()
            }.onFailure { err ->
                Log.w(TAG, "Failed saving widget view preference", err)
            }
        }

        fun refreshAllWidgets(context: Context) {
            runCatching {
                val mgr: AppWidgetManager? = AppWidgetManager.getInstance(context)
                if (mgr == null) return
                val ids: IntArray? = mgr.getAppWidgetIds(ComponentName(context, ThingsbookWidgetProvider::class.java))
                if (ids == null || ids.isEmpty()) return

                val intent = Intent(context, ThingsbookWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_ALL
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
            }.onFailure { err ->
                Log.w(TAG, "refreshAllWidgets broadcast failed", err)
            }
        }

        suspend fun refreshAllWidgetsSync(context: Context) {
            runCatchingCancellable {
                val mgr: AppWidgetManager? = AppWidgetManager.getInstance(context)
                if (mgr == null) return
                val ids: IntArray? = mgr.getAppWidgetIds(ComponentName(context, ThingsbookWidgetProvider::class.java))
                if (ids == null || ids.isEmpty()) return
                @Suppress("DEPRECATION")
                mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_task_list)
                for (id in ids) {
                    runCatchingCancellable {
                        updateSingleWidget(context, mgr, id)
                    }.onFailure { err ->
                        Log.w(TAG, "updateSingleWidget failed for $id", err)
                    }
                }
            }.onFailure { err ->
                Log.w(TAG, "refreshAllWidgetsSync failed", err)
            }
        }

        suspend fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager?,
            appWidgetId: Int
        ) {
            val mgr = appWidgetManager ?: return
            val localizedContext = androidx.core.content.ContextCompat.getContextForLanguage(context)
            val selectedView = getSelectedViewForWidget(context, appWidgetId)
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ThingsWidgetEntryPoint::class.java
            )
            val tasks = runCatchingCancellable {
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
            views.setTextViewText(R.id.widget_view_title, localizedContext.getString(titleRes))
            views.setTextViewText(R.id.widget_task_count, tasks.size.toString())
            views.setTextViewText(R.id.widget_empty_view, localizedContext.getString(R.string.empty_view))

            // 2. Highlight Active View Tab in Segmented Bar & Attach Click PendingIntents
            val tabs = listOf(
                Triple(VIEW_TODAY, R.id.widget_tab_today, R.string.view_today),
                Triple(VIEW_INBOX, R.id.widget_tab_inbox, R.string.view_inbox),
                Triple(VIEW_UPCOMING, R.id.widget_tab_upcoming, R.string.view_upcoming),
                Triple(VIEW_ANYTIME, R.id.widget_tab_anytime, R.string.view_anytime),
                Triple(VIEW_SOMEDAY, R.id.widget_tab_someday, R.string.view_someday)
            )
            for ((viewCode, tabViewId, tabTitleRes) in tabs) {
                val isActive = viewCode == selectedView
                views.setTextViewText(tabViewId, localizedContext.getString(tabTitleRes))
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

            // 5. Bind RemoteViewsService Adapter for Scrollable Task List (minSdk = 26..35)
            val serviceIntent = Intent(context, ThingsbookWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_task_list, serviceIntent)
            views.setEmptyView(R.id.widget_task_list, R.id.widget_empty_view)

            // 6. PendingIntent Template for Task Row Click & Direct Checkbox Completion
            val itemTemplateIntent = Intent(context, ThingsbookWidgetProvider::class.java).apply {
                action = ACTION_ITEM_INTERACTION
                setPackage(context.packageName)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("thingsbook://widget/item/$appWidgetId")
            }
            val mutableFlag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val itemTemplatePending = PendingIntent.getBroadcast(
                context,
                appWidgetId * 100 + 3,
                itemTemplateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )
            views.setPendingIntentTemplate(R.id.widget_task_list, itemTemplatePending)

            mgr.updateAppWidget(appWidgetId, views)
        }
    }
}
