package com.culturedcode.things3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.Heading
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.ui.theme.ThingsTheme
import kotlin.math.roundToInt

/** What is being dragged across a Project list. */
enum class ProjectDragKind {
    /** Magic Plus (+) dragged into the list → creates a new To-Do at the drop position. */
    NEW_TODO,

    /** Magic Plus (+) dragged to the left edge → creates a new Heading at the drop position. */
    NEW_HEADING,

    /** An existing To-Do dragged via its handle → moves it (also between headings). */
    MOVE_TASK
}

/** Semantic meaning of a LazyColumn item inside a Project list (keyed by the LazyColumn item key). */
sealed interface ProjectListEntry {
    data class TaskRow(val taskId: String, val headingId: String?, val indexInSection: Int) : ProjectListEntry
    data class HeadingRow(val headingId: String, val headingIndex: Int) : ProjectListEntry
}

/** Vertical geometry of a visible LazyColumn item, relative to the list viewport top (px). */
data class ListItemGeometry(val key: Any, val top: Float, val bottom: Float)

/**
 * Resolved drop position inside a Project:
 * - [headingId] / [indexInSection]: target section (null = tasks without heading) and insertion index in it.
 * - [headingInsertIndex]: insertion index among the project's headings (for [ProjectDragKind.NEW_HEADING]).
 * - [indicatorY]: y position (px, relative to list viewport top) where the insertion line is drawn.
 */
data class ProjectDropTarget(
    val headingId: String?,
    val indexInSection: Int,
    val headingInsertIndex: Int,
    val indicatorY: Float
)

data class ProjectDragSession(
    val kind: ProjectDragKind,
    val pointerWindow: Offset,
    val draggedTaskId: String? = null,
    val draggedTaskTitle: String? = null,
    val target: ProjectDropTarget? = null
)

/**
 * Pure drop-target resolution from the real on-screen geometry of the visible list items.
 *
 * Task modes: dropping on the upper half of a task inserts before it, on the lower half after it.
 * Dropping on the upper part of a heading appends to the previous section, on its lower part
 * inserts at the top of that heading's section (works for empty and collapsed headings too).
 *
 * Heading mode: headings can only be inserted at section boundaries (before an existing heading or at the end).
 */
fun resolveProjectDropTarget(
    pointerY: Float,
    items: List<ListItemGeometry>,
    entries: Map<Any, ProjectListEntry>,
    headingIds: List<String>,
    sectionSizes: Map<String?, Int>,
    headingMode: Boolean
): ProjectDropTarget? {
    if (!pointerY.isFinite()) return null
    val relevant = items
        .mapNotNull { g -> entries[g.key]?.let { g to it } }
        .sortedBy { it.first.top }

    fun sectionSize(id: String?): Int = sectionSizes[id] ?: 0
    fun sectionBeforeHeading(headingIndex: Int): String? =
        if (headingIndex <= 0) null else headingIds.getOrNull(headingIndex - 1)
    fun headingIndexOf(id: String?): Int = if (id == null) -1 else headingIds.indexOf(id)

    if (relevant.isEmpty()) {
        val last = headingIds.lastOrNull()
        return ProjectDropTarget(
            headingId = last,
            indexInSection = sectionSize(last),
            headingInsertIndex = headingIds.size,
            indicatorY = items.lastOrNull()?.bottom ?: 0f
        )
    }

    if (headingMode) {
        for ((g, e) in relevant) {
            if (e is ProjectListEntry.HeadingRow && pointerY < (g.top + g.bottom) / 2f) {
                val prev = sectionBeforeHeading(e.headingIndex)
                return ProjectDropTarget(prev, sectionSize(prev), e.headingIndex, g.top)
            }
        }
        val (lastGeom, lastEntry) = relevant.last()
        val sectionOfLast = when (lastEntry) {
            is ProjectListEntry.TaskRow -> lastEntry.headingId
            is ProjectListEntry.HeadingRow -> lastEntry.headingId
        }
        val insertIdx = (headingIndexOf(sectionOfLast) + 1).coerceIn(0, headingIds.size)
        return ProjectDropTarget(sectionOfLast, sectionSize(sectionOfLast), insertIdx, lastGeom.bottom)
    }

    for ((g, e) in relevant) {
        when (e) {
            is ProjectListEntry.TaskRow -> {
                if (pointerY < (g.top + g.bottom) / 2f) {
                    return ProjectDropTarget(
                        headingId = e.headingId,
                        indexInSection = e.indexInSection,
                        headingInsertIndex = headingIndexOf(e.headingId) + 1,
                        indicatorY = g.top
                    )
                }
            }
            is ProjectListEntry.HeadingRow -> {
                val split = g.top + (g.bottom - g.top) * 0.4f
                if (pointerY < split) {
                    val prev = sectionBeforeHeading(e.headingIndex)
                    return ProjectDropTarget(prev, sectionSize(prev), e.headingIndex, g.top)
                }
                if (pointerY < g.bottom) {
                    return ProjectDropTarget(e.headingId, 0, e.headingIndex + 1, g.bottom)
                }
            }
        }
    }

    val (lastGeom, lastEntry) = relevant.last()
    return when (lastEntry) {
        is ProjectListEntry.TaskRow -> ProjectDropTarget(
            headingId = lastEntry.headingId,
            indexInSection = lastEntry.indexInSection + 1,
            headingInsertIndex = headingIndexOf(lastEntry.headingId) + 1,
            indicatorY = lastGeom.bottom
        )
        is ProjectListEntry.HeadingRow -> ProjectDropTarget(
            headingId = lastEntry.headingId,
            indexInSection = 0,
            headingInsertIndex = lastEntry.headingIndex + 1,
            indicatorY = lastGeom.bottom
        )
    }
}

/**
 * Shared state between the Project LazyColumn (geometry provider + indicator renderer),
 * the Magic Plus FAB and the per-task drag handles.
 */
@Stable
class ProjectDragDropController {
    var session by mutableStateOf<ProjectDragSession?>(null)
        private set

    // Geometry / content registered by the Project list (plain fields: only read while dragging)
    internal var listState: LazyListState? = null
    internal var listWindowTop: Float = 0f
    internal var listHeight: Float = 0f
    internal var entries: Map<Any, ProjectListEntry> = emptyMap()
    internal var headings: List<Heading> = emptyList()
    var sectionTasks: Map<String?, List<TaskItem>> = emptyMap()
        internal set

    /** True while a Project list is composed and registered as drop surface. */
    var isProjectListActive: Boolean = false
        internal set

    fun start(kind: ProjectDragKind, pointerWindow: Offset, taskId: String? = null, taskTitle: String? = null) {
        val s = ProjectDragSession(kind, pointerWindow, taskId, taskTitle)
        session = s.copy(target = resolve(s))
    }

    fun move(pointerWindow: Offset, kind: ProjectDragKind? = null) {
        val current = session ?: return
        val updated = current.copy(pointerWindow = pointerWindow, kind = kind ?: current.kind)
        session = updated.copy(target = resolve(updated))
    }

    fun refresh() {
        val current = session ?: return
        session = current.copy(target = resolve(current))
    }

    fun finish(): ProjectDragSession? {
        val s = session ?: return null
        val resolved = s.copy(target = resolve(s) ?: s.target)
        session = null
        return resolved
    }

    fun cancel() {
        session = null
    }

    private fun resolve(s: ProjectDragSession): ProjectDropTarget? {
        val state = listState ?: return null
        if (!isProjectListActive) return null
        val items = state.layoutInfo.visibleItemsInfo.map {
            ListItemGeometry(it.key, it.offset.toFloat(), (it.offset + it.size).toFloat())
        }
        return resolveProjectDropTarget(
            pointerY = s.pointerWindow.y - listWindowTop,
            items = items,
            entries = entries,
            headingIds = headings.map { it.id },
            sectionSizes = sectionTasks.mapValues { it.value.size },
            headingMode = s.kind == ProjectDragKind.NEW_HEADING
        )
    }
}

/**
 * Modifier for the Box wrapping the Project LazyColumn:
 * registers its window position and takes over pointer tracking of task drags
 * (so a drag keeps working even when auto-scroll moves the source row off screen).
 */
fun Modifier.projectDropSurface(
    controller: ProjectDragDropController,
    onTaskDrop: (ProjectDragSession) -> Unit
): Modifier = this
    .onGloballyPositioned { coords ->
        controller.listWindowTop = coords.localToWindowSafe(Offset.Zero).y
        controller.listHeight = coords.size.height.toFloat()
    }
    .pointerInput(controller) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val s = controller.session ?: continue
                if (s.kind != ProjectDragKind.MOVE_TASK) continue
                val change = event.changes.firstOrNull() ?: continue
                val windowPos = Offset(
                    x = s.pointerWindow.x,
                    y = controller.listWindowTop + change.position.y
                )
                change.consume()
                if (change.pressed) {
                    controller.move(windowPos)
                } else {
                    controller.move(windowPos)
                    controller.finish()?.let(onTaskDrop)
                }
            }
        }
    }

private fun LayoutCoordinates.localToWindowSafe(local: Offset): Offset =
    if (isAttached) runCatching { localToWindow(local) }.getOrDefault(Offset.Zero) else Offset.Zero

/**
 * Auto-scrolls the Project list while something is dragged close to its top/bottom edge.
 * Only arms once the pointer has been outside the edge zones (the FAB starts inside the bottom zone).
 */
@Composable
fun ProjectDragAutoScrollEffect(controller: ProjectDragDropController, listState: LazyListState) {
    val density = LocalDensity.current
    val edgePx = with(density) { 72.dp.toPx() }
    val maxSpeedPx = with(density) { 14.dp.toPx() }
    val isDragging = controller.session != null
    LaunchedEffect(isDragging, listState) {
        if (!isDragging) return@LaunchedEffect
        var armed = false
        while (true) {
            withFrameNanos { }
            val s = controller.session ?: break
            val h = controller.listHeight
            if (h <= 0f) continue
            val localY = s.pointerWindow.y - controller.listWindowTop
            val inTop = localY < edgePx
            val inBottom = localY > h - edgePx
            if (!inTop && !inBottom) armed = true
            if (!armed) continue
            val delta = when {
                inTop -> -maxSpeedPx * ((edgePx - localY) / edgePx).coerceIn(0.15f, 1f)
                inBottom -> maxSpeedPx * ((localY - (h - edgePx)) / edgePx).coerceIn(0.15f, 1f)
                else -> 0f
            }
            if (delta != 0f) {
                val consumed = runCatching { listState.scrollBy(delta) }.getOrDefault(0f)
                if (consumed != 0f) controller.refresh()
            }
        }
    }
}

/**
 * Insertion line + label (and floating ghost of the dragged To-Do) drawn on top of the Project list.
 * Rendered as an overlay so the list layout never jumps while dragging.
 */
@Composable
fun BoxScope.ProjectDropIndicatorOverlay(controller: ProjectDragDropController) {
    val session = controller.session ?: return
    val target = session.target
    val colors = ThingsTheme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(target?.headingId, target?.indexInSection, target?.headingInsertIndex, session.kind) {
        if (target != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    if (target != null) {
        val isHeading = session.kind == ProjectDragKind.NEW_HEADING
        val headingTitle = target.headingId?.let { id -> controller.headings.find { it.id == id }?.title }
        val baseLabel = when (session.kind) {
            ProjectDragKind.NEW_TODO -> stringResource(R.string.magic_plus_insertion_label)
            ProjectDragKind.NEW_HEADING -> stringResource(R.string.drop_heading_here)
            ProjectDragKind.MOVE_TASK -> stringResource(R.string.drop_move_here)
        }
        val label = if (!isHeading && !headingTitle.isNullOrBlank()) "$baseLabel · $headingTitle" else baseLabel
        val lineHalfPx = with(density) { 12.dp.toPx() }
        val y = (target.indicatorY - lineHalfPx).roundToInt().coerceAtLeast(0)

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, y) }
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.thingsBlue)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(colors.thingsBlue)
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(with(density) { 36.dp.roundToPx() }, y - with(density) { 22.dp.roundToPx() }) }
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(8.dp),
            color = colors.thingsBlueSoft,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isHeading) {
                    Icon(
                        imageVector = Icons.Outlined.Title,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // Floating ghost of the dragged To-Do following the pointer
    if (session.kind == ProjectDragKind.MOVE_TASK) {
        val ghostY = (session.pointerWindow.y - controller.listWindowTop - with(density) { 18.dp.toPx() })
            .roundToInt()
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(with(density) { 48.dp.roundToPx() }, ghostY) }
                .widthIn(max = 360.dp)
                .shadow(10.dp, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = colors.cardElevatedSurface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DragIndicator,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = session.draggedTaskTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.entity_new_todo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Drag handle (⋮⋮) shown on collapsed To-Do rows in Project views.
 * Press + move starts a [ProjectDragKind.MOVE_TASK] session; the list surface then tracks the pointer.
 */
@Composable
fun ProjectTaskDragHandle(
    task: TaskItem,
    controller: ProjectDragDropController,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val currentTask by rememberUpdatedState(task)
    val coordsHolder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    ThingsHoverTooltip(text = stringResource(R.string.task_drag_handle)) {
        Box(
            modifier = modifier
                .size(width = 26.dp, height = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isHovered) colors.thingsBlueSoft else androidx.compose.ui.graphics.Color.Transparent)
                .hoverable(hoverSource)
                .pointerHoverIcon(PointerIcon.Hand)
                .onGloballyPositioned { coordsHolder[0] = it }
                .pointerInput(controller) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val slopChange = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        } ?: return@awaitEachGesture
                        val coords = coordsHolder[0]
                        val windowPos = if (coords != null && coords.isAttached) {
                            coords.localToWindow(slopChange.position)
                        } else {
                            return@awaitEachGesture
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.start(
                            kind = ProjectDragKind.MOVE_TASK,
                            pointerWindow = windowPos,
                            taskId = currentTask.id,
                            taskTitle = currentTask.title
                        )
                        // From here on the list surface (projectDropSurface) tracks the pointer in the Initial pass.
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.DragIndicator,
                contentDescription = stringResource(R.string.task_drag_handle),
                tint = if (isHovered) colors.thingsBlue else colors.secondaryText.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
