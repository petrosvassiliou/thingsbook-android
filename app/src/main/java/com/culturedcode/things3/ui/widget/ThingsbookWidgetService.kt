package com.culturedcode.things3.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.TaskItem
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class ThingsbookWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return ThingsbookRemoteViewsFactory(applicationContext, appWidgetId)
    }
}

private class ThingsbookRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<TaskItem> = emptyList()
    private var selectedView: String = ThingsbookWidgetProvider.VIEW_TODAY

    override fun onCreate() {
        // Do NOT call runBlocking on the Main UI Thread in onCreate()!
        // Android always invokes onDataSetChanged() on a background binder thread immediately after.
    }

    override fun onDataSetChanged() {
        loadTasksBlocking()
    }

    private fun loadTasksBlocking() {
        selectedView = runCatching {
            ThingsbookWidgetProvider.getSelectedViewForWidget(context, appWidgetId)
        }.getOrDefault(ThingsbookWidgetProvider.VIEW_TODAY)
        tasks = runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ThingsWidgetEntryPoint::class.java
            )
            runBlocking {
                withTimeoutOrNull(5_000L) {
                    entryPoint.thingsRepository().getTasksForWidgetView(selectedView)
                } ?: emptyList()
            }
        }.onFailure { err ->
            Log.w("ThingsWidgetSvc", "Failed loading widget tasks for view $selectedView", err)
        }.getOrDefault(emptyList())
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews? = runCatching {
        val task = tasks.getOrNull(position) ?: return@runCatching null
        val localizedContext = androidx.core.content.ContextCompat.getContextForLanguage(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_thingsbook_task_item)

        val displayTitle = task.title.ifBlank { localizedContext.getString(R.string.entity_new_todo) }
        rv.setTextViewText(R.id.widget_item_title, displayTitle)

        // Format compact right-side badge (Recurrence, Reminder, Date, or first Tag)
        val badgeText = buildTaskMetadataBadge(task, localizedContext)
        if (badgeText != null) {
            rv.setViewVisibility(R.id.widget_item_badge, View.VISIBLE)
            rv.setTextViewText(R.id.widget_item_badge, badgeText)
        } else {
            rv.setViewVisibility(R.id.widget_item_badge, View.GONE)
        }

        // 1. Clicking Checkbox completes the task directly from the Home Screen Widget!
        val completeFillIn = Intent().apply {
            putExtra(
                ThingsbookWidgetProvider.EXTRA_INTERACTION_TYPE,
                ThingsbookWidgetProvider.INTERACTION_COMPLETE_TASK
            )
            putExtra(ThingsbookWidgetProvider.EXTRA_TASK_ID, task.id)
            putExtra(ThingsbookWidgetProvider.EXTRA_TARGET_VIEW, selectedView)
        }
        rv.setOnClickFillInIntent(R.id.widget_item_checkbox_hit, completeFillIn)

        // 2. Clicking the Task Row opens the app in that view and expands the task
        val openFillIn = Intent().apply {
            putExtra(
                ThingsbookWidgetProvider.EXTRA_INTERACTION_TYPE,
                ThingsbookWidgetProvider.INTERACTION_OPEN_TASK
            )
            putExtra(ThingsbookWidgetProvider.EXTRA_TASK_ID, task.id)
            putExtra(ThingsbookWidgetProvider.EXTRA_TARGET_VIEW, selectedView)
        }
        rv.setOnClickFillInIntent(R.id.widget_item_row_root, openFillIn)

        rv
    }.onFailure { err ->
        Log.w("ThingsWidgetSvc", "Failed inflating RemoteViews at position $position", err)
    }.getOrNull()

    private fun buildTaskMetadataBadge(task: TaskItem, localizedContext: Context): String? = runCatching {
        val zone = ZoneId.systemDefault()
        val locale = localizedContext.resources.configuration.locales.get(0) ?: Locale.getDefault()

        if (task.reminderTime != null) {
            val timeStr = Instant.ofEpochMilli(task.reminderTime)
                .atZone(zone)
                .toLocalTime()
                .format(DateTimeFormatter.ofPattern("HH:mm", locale))
            val prefix = if (task.recurrenceRule != null) "↻ 🔔 " else "🔔 "
            return@runCatching "$prefix$timeStr"
        }

        if (task.recurrenceRule != null) {
            val firstTag = task.tags.firstOrNull()?.title
            return@runCatching if (firstTag != null) "↻ $firstTag" else "↻"
        }

        if (selectedView == ThingsbookWidgetProvider.VIEW_UPCOMING && task.whenDate != null) {
            val dateStr = Instant.ofEpochMilli(task.whenDate)
                .atZone(zone)
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("d. MMM", locale))
            return@runCatching dateStr
        }

        if (task.deadline != null) {
            val dlStr = Instant.ofEpochMilli(task.deadline)
                .atZone(zone)
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("d. MMM", locale))
            return@runCatching "🏁 $dlStr"
        }

        task.tags.firstOrNull()?.title
    }.getOrNull()

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        val id = tasks.getOrNull(position)?.id ?: return position.toLong()
        return runCatching {
            val uuid = UUID.fromString(id)
            uuid.mostSignificantBits xor uuid.leastSignificantBits
        }.getOrElse {
            id.hashCode().toLong()
        }
    }

    override fun hasStableIds(): Boolean = true
}
