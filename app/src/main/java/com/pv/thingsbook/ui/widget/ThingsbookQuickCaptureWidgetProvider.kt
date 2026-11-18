package com.pv.thingsbook.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.pv.thingsbook.MainActivity
import com.pv.thingsbook.R
import com.pv.thingsbook.domain.util.runCatchingCancellable
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Compact 4x1 Home Screen Quick Capture Bar Widget (`v1.8.0`):
 * Provides 1-tap access to Today (with open count badge), Quick Find (`🔍`),
 * Voice Dictation (`🎙️`), and `+ New To-Do`.
 */
class ThingsbookQuickCaptureWidgetProvider : AppWidgetProvider() {

    private val widgetScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.w(TAG, "Uncaught quick capture widget error", throwable)
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
                withTimeoutOrNull(6_000L) {
                    for (widgetId in ids) {
                        runCatchingCancellable {
                            updateSingleQuickCaptureWidget(context, mgr, widgetId)
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

    companion object {
        private const val TAG = "ThingsQuickCaptureWgt"

        suspend fun refreshAllQuickCaptureWidgetsSync(context: Context) {
            runCatchingCancellable {
                val mgr = AppWidgetManager.getInstance(context) ?: return
                val ids = mgr.getAppWidgetIds(
                    ComponentName(context, ThingsbookQuickCaptureWidgetProvider::class.java)
                ) ?: return
                for (id in ids) {
                    runCatchingCancellable {
                        updateSingleQuickCaptureWidget(context, mgr, id)
                    }
                }
            }
        }

        suspend fun updateSingleQuickCaptureWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val localizedContext = ContextCompat.getContextForLanguage(context)
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ThingsWidgetEntryPoint::class.java
            )
            val todayCount = runCatchingCancellable {
                entryPoint.thingsRepository().getTasksForWidgetView(ThingsbookWidgetProvider.VIEW_TODAY).size
            }.getOrDefault(0)

            val views = RemoteViews(context.packageName, R.layout.widget_thingsbook_quick_capture)
            views.setTextViewText(R.id.qc_label_today, localizedContext.getString(R.string.view_today))
            views.setTextViewText(R.id.qc_today_count, todayCount.toString())
            views.setTextViewText(
                R.id.qc_btn_new_todo,
                "+ ${localizedContext.getString(R.string.shortcut_new_todo_short)}"
            )

            // 1. Today Button
            val todayIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(ThingsbookWidgetProvider.EXTRA_WIDGET_SMART_VIEW, ThingsbookWidgetProvider.VIEW_TODAY)
                data = Uri.parse("thingsbook://qcwidget/today/$appWidgetId")
            }
            views.setOnClickPendingIntent(
                R.id.qc_btn_today,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 100 + 11,
                    todayIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // 2. Quick Find Button
            val searchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_shortcut_action", "QUICK_FIND")
                data = Uri.parse("thingsbook://qcwidget/search/$appWidgetId")
            }
            views.setOnClickPendingIntent(
                R.id.qc_btn_search,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 100 + 12,
                    searchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // 3. Voice Capture Button
            val voiceIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_shortcut_action", "VOICE_CAPTURE")
                data = Uri.parse("thingsbook://qcwidget/voice/$appWidgetId")
            }
            views.setOnClickPendingIntent(
                R.id.qc_btn_voice,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 100 + 13,
                    voiceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // 4. + New To-Do Button
            val newTodoIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(ThingsbookWidgetProvider.EXTRA_WIDGET_SMART_VIEW, ThingsbookWidgetProvider.VIEW_TODAY)
                putExtra(ThingsbookWidgetProvider.EXTRA_WIDGET_QUICK_ADD, true)
                data = Uri.parse("thingsbook://qcwidget/newtodo/$appWidgetId")
            }
            views.setOnClickPendingIntent(
                R.id.qc_btn_new_todo,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 100 + 14,
                    newTodoIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
