package com.pv.thingsbook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pv.thingsbook.R
import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.ui.theme.ThingsTheme
import kotlin.math.roundToInt

/** What is being dragged across a Project or Workspace view. */
enum class ProjectDragKind {
    /** Magic Plus (+) dragged into the list → creates a new To-Do at the drop position. */
    NEW_TODO,

    /** Magic Plus (+) dragged to the left edge → creates a new Heading at the drop position. */
    NEW_HEADING,

    /** One or more existing To-Dos dragged via handle → moves/reorders across containers or sections. */
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

/**
 * Cross-container and Kanban / intra-list drop destinations across the Thingsbook 3.0 Workspace.
 */
sealed interface WorkspaceDragDropDestination {
    data class SmartView(
        val viewType: SmartViewType,
        val displayTitle: String = ""
    ) : WorkspaceDragDropDestination

    data class AreaTarget(
        val areaId: String,
        val areaTitle: String = ""
    ) : WorkspaceDragDropDestination

    data class ProjectTarget(
        val projectId: String,
        val projectTitle: String = "",
        val headingId: String? = null,
        val headingTitle: String? = null
    ) : WorkspaceDragDropDestination

    data class BoardColumn(
        val projectId: String,
        val headingId: String?,
        val headingTitle: String? = null,
        val indexInColumn: Int = 0
    ) : WorkspaceDragDropDestination

    data class CurrentListReorder(
        val dropIndex: Int
    ) : WorkspaceDragDropDestination
}

data class RegisteredDropZone(
    val key: String,
    val boundsWindow: IntRect,
    val destination: WorkspaceDragDropDestination,
    val priority: Int = 10
)

data class BoardColumnGeometry(
    val headingId: String?,
    val headingTitle: String?,
    val boundsWindow: IntRect
)

data class ProjectDragSession(
    val kind: ProjectDragKind,
    val pointerWindow: Offset,
    val draggedTaskId: String? = null,
    val draggedTaskTitle: String? = null,
    val draggedTaskIds: List<String> = emptyList(),
    val target: ProjectDropTarget? = null,
    val externalDestination: WorkspaceDragDropDestination? = null,
    val hoveredZoneKey: String? = null,
    val boardIndicatorWindowY: Float? = null,
    val listReorderIndicatorY: Float? = null
) {
    val effectiveTaskIds: List<String>
        get() = if (draggedTaskIds.isNotEmpty()) draggedTaskIds else listOfNotNull(draggedTaskId)
}

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
 * Shared 2D workspace drag-and-drop controller coordinating:
 * - Cross-container drops onto Chromebook Sidebar, Top Strip, and Compact Mobile Drop Dock
 * - Project List phase/section drops + Magic Plus positional drops
 * - Project Board (Kanban) cross-column & intra-column single/multi-task drops
 * - Intra-list manual reordering in Smart Views, Areas, and Perspectives
 */
@Stable
class ProjectDragDropController {
    var session by mutableStateOf<ProjectDragSession?>(null)
        private set

    internal var rootWindowOrigin: Offset = Offset.Zero
    internal var rootSize: IntSize = IntSize.Zero

    // External drop zones registered in window coordinates (Sidebar, Strip, Mobile Dock, Phase headers)
    private val externalDropZones = LinkedHashMap<String, RegisteredDropZone>()

    // Geometry / content registered by the active LazyColumn
    internal var listState: LazyListState? = null
    internal var listWindowLeft: Float = 0f
    internal var listWindowTop: Float = 0f
    internal var listWidth: Float = 0f
    internal var listHeight: Float = 0f
    internal var entries: Map<Any, ProjectListEntry> = emptyMap()
    internal var headings: List<Heading> = emptyList()
    var sectionTasks: Map<String?, List<TaskItem>> = emptyMap()
        internal set

    /** True while a Project List is composed and registered as drop surface. */
    var isProjectListActive: Boolean = false
        internal set

    /** True while a non-Project manual-sorted list is composed and registered for intra-list reordering. */
    var isGeneralListReorderActive: Boolean = false
        internal set
    var generalListTasks: List<TaskItem> = emptyList()
        internal set

    /** True while Project Board (Kanban) is composed and registered as drop surface. */
    var isBoardActive: Boolean = false
        internal set
    var boardProjectId: String? = null
        internal set
    var boardSectionTasks: Map<String?, List<TaskItem>> = emptyMap()
        internal set
    private val boardColumns = LinkedHashMap<String?, BoardColumnGeometry>()
    private val boardCardWindowY = HashMap<String, Pair<Float, Float>>()

    val activeListWindowLeft: Float?
        get() = if (isProjectListActive && listWidth > 0f) listWindowLeft else null

    fun registerDropZone(
        key: String,
        boundsWindow: IntRect,
        destination: WorkspaceDragDropDestination,
        priority: Int = 10
    ) {
        if (boundsWindow.width <= 0 || boundsWindow.height <= 0) {
            externalDropZones.remove(key)
        } else {
            externalDropZones[key] = RegisteredDropZone(key, boundsWindow, destination, priority)
        }
    }

    fun unregisterDropZone(key: String) {
        externalDropZones.remove(key)
    }

    fun registerBoardColumn(headingId: String?, headingTitle: String?, boundsWindow: IntRect) {
        if (boundsWindow.width <= 0 || boundsWindow.height <= 0) {
            boardColumns.remove(headingId)
        } else {
            boardColumns[headingId] = BoardColumnGeometry(headingId, headingTitle, boundsWindow)
        }
    }

    fun unregisterBoardColumn(headingId: String?) {
        boardColumns.remove(headingId)
    }

    fun registerBoardCardBounds(taskId: String, windowTop: Float, windowBottom: Float) {
        boardCardWindowY[taskId] = windowTop to windowBottom
    }

    fun unregisterBoardCardBounds(taskId: String) {
        boardCardWindowY.remove(taskId)
    }

    fun clearBoardGeometry() {
        isBoardActive = false
        boardProjectId = null
        boardColumns.clear()
        boardCardWindowY.clear()
        boardSectionTasks = emptyMap()
    }

    fun start(
        kind: ProjectDragKind,
        pointerWindow: Offset,
        taskId: String? = null,
        taskTitle: String? = null,
        taskIds: List<String> = emptyList()
    ) {
        val cleanIds = taskIds.filter { it.isNotBlank() }.distinct()
            .ifEmpty { listOfNotNull(taskId?.takeIf { it.isNotBlank() }) }
        val base = ProjectDragSession(
            kind = kind,
            pointerWindow = pointerWindow,
            draggedTaskId = taskId,
            draggedTaskTitle = taskTitle,
            draggedTaskIds = cleanIds
        )
        session = resolveSession(base)
    }

    fun move(pointerWindow: Offset, kind: ProjectDragKind? = null) {
        val current = session ?: return
        val updated = current.copy(
            pointerWindow = pointerWindow,
            kind = kind ?: current.kind
        )
        session = resolveSession(updated)
    }

    fun refresh() {
        val current = session ?: return
        session = resolveSession(current)
    }

    fun finish(): ProjectDragSession? {
        val s = session ?: return null
        val resolved = resolveSession(s)
        session = null
        return resolved
    }

    fun cancel() {
        session = null
    }

    private fun isPointInListBounds(pointerWindow: Offset, marginPx: Float = 24f): Boolean {
        if (listWidth <= 0f || listHeight <= 0f) return false
        return pointerWindow.x >= listWindowLeft - marginPx &&
            pointerWindow.x <= listWindowLeft + listWidth + marginPx &&
            pointerWindow.y >= listWindowTop - marginPx &&
            pointerWindow.y <= listWindowTop + listHeight + marginPx
    }

    private fun resolveSession(s: ProjectDragSession): ProjectDragSession {
        if (!s.pointerWindow.x.isFinite() || !s.pointerWindow.y.isFinite()) {
            return s
        }

        if (s.kind != ProjectDragKind.MOVE_TASK) {
            return s.copy(
                target = resolveProjectList(s),
                externalDestination = null,
                hoveredZoneKey = null,
                boardIndicatorWindowY = null,
                listReorderIndicatorY = null
            )
        }

        val px = s.pointerWindow.x.roundToInt()
        val py = s.pointerWindow.y.roundToInt()

        // 1. Check high-priority external targets (Sidebar, Top Strip, Compact Mobile Dock, explicit Phase Headers)
        val matchedZone = externalDropZones.values
            .asSequence()
            .filter { z ->
                px >= z.boundsWindow.left &&
                    px <= z.boundsWindow.right &&
                    py >= z.boundsWindow.top &&
                    py <= z.boundsWindow.bottom
            }
            .maxByOrNull { it.priority }

        if (matchedZone != null) {
            return s.copy(
                target = null,
                externalDestination = matchedZone.destination,
                hoveredZoneKey = matchedZone.key,
                boardIndicatorWindowY = null,
                listReorderIndicatorY = null
            )
        }

        // 2. Check Kanban Board columns if Project Board workspace is active
        val activeBoardProjId = boardProjectId
        if (isBoardActive && !activeBoardProjId.isNullOrBlank()) {
            val matchedCol = boardColumns.values.firstOrNull { col ->
                px >= col.boundsWindow.left &&
                    px <= col.boundsWindow.right &&
                    py >= col.boundsWindow.top &&
                    py <= col.boundsWindow.bottom
            }
            if (matchedCol != null) {
                val colTasks = boardSectionTasks[matchedCol.headingId].orEmpty()
                var insertIdx = colTasks.size
                var indicatorWinY: Float? = null
                for ((idx, t) in colTasks.withIndex()) {
                    val bounds = boardCardWindowY[t.id] ?: continue
                    val midY = (bounds.first + bounds.second) / 2f
                    if (s.pointerWindow.y < midY) {
                        insertIdx = idx
                        indicatorWinY = bounds.first
                        break
                    }
                }
                if (indicatorWinY == null && colTasks.isNotEmpty()) {
                    indicatorWinY = boardCardWindowY[colTasks.last().id]?.second
                }
                return s.copy(
                    target = null,
                    externalDestination = WorkspaceDragDropDestination.BoardColumn(
                        projectId = activeBoardProjId,
                        headingId = matchedCol.headingId,
                        headingTitle = matchedCol.headingTitle,
                        indexInColumn = insertIdx
                    ),
                    hoveredZoneKey = "board_col_${matchedCol.headingId ?: "general"}",
                    boardIndicatorWindowY = indicatorWinY,
                    listReorderIndicatorY = null
                )
            }
        }

        // 3. Check Project List drop target when pointer is inside the Project list pane
        if (isProjectListActive && isPointInListBounds(s.pointerWindow)) {
            val projTarget = resolveProjectList(s)
            return s.copy(
                target = projTarget,
                externalDestination = null,
                hoveredZoneKey = projTarget?.let { "proj_section_${it.headingId ?: "general"}" },
                boardIndicatorWindowY = null,
                listReorderIndicatorY = null
            )
        }

        // 4. Check Intra-List Manual Reorder in Smart Views / Areas / Perspectives
        if (isGeneralListReorderActive && isPointInListBounds(s.pointerWindow)) {
            val reorder = resolveGeneralListReorder(s.pointerWindow.y - listWindowTop)
            if (reorder != null) {
                return s.copy(
                    target = null,
                    externalDestination = WorkspaceDragDropDestination.CurrentListReorder(reorder.first),
                    hoveredZoneKey = null,
                    boardIndicatorWindowY = null,
                    listReorderIndicatorY = reorder.second
                )
            }
        }

        return s.copy(
            target = null,
            externalDestination = null,
            hoveredZoneKey = null,
            boardIndicatorWindowY = null,
            listReorderIndicatorY = null
        )
    }

    private fun resolveProjectList(s: ProjectDragSession): ProjectDropTarget? {
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

    private fun resolveGeneralListReorder(localPointerY: Float): Pair<Int, Float>? {
        val state = listState ?: return null
        if (generalListTasks.isEmpty()) return 0 to 0f
        val taskIdxById = HashMap<String, Int>(generalListTasks.size)
        generalListTasks.forEachIndexed { idx, t -> taskIdxById[t.id] = idx }

        val visibleTaskRows = state.layoutInfo.visibleItemsInfo
            .mapNotNull { info ->
                val k = info.key?.toString() ?: return@mapNotNull null
                val idx = taskIdxById[k] ?: return@mapNotNull null
                Triple(idx, info.offset.toFloat(), (info.offset + info.size).toFloat())
            }
            .sortedBy { it.second }

        if (visibleTaskRows.isEmpty()) return null
        for ((taskIdx, top, bottom) in visibleTaskRows) {
            if (localPointerY < (top + bottom) / 2f) {
                return taskIdx to top
            }
        }
        val last = visibleTaskRows.last()
        return (last.first + 1) to last.third
    }
}

/**
 * Root-level 2D pointer tracker attached to the root Box of `ThingsSmartViewScreen`:
 * tracks full `(windowX, windowY)` across Sidebar, Top Strip, Kanban Board, and Task Lists!
 */
fun Modifier.workspaceDragRootSurface(
    controller: ProjectDragDropController,
    onTaskDrop: (ProjectDragSession) -> Unit
): Modifier = this
    .onGloballyPositioned { coords ->
        controller.rootWindowOrigin = coords.localToWindowSafe(Offset.Zero)
        controller.rootSize = coords.size
    }
    .pointerInput(controller) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val s = controller.session ?: continue
                if (s.kind != ProjectDragKind.MOVE_TASK) continue
                val change = event.changes.firstOrNull() ?: continue
                val windowPos = controller.rootWindowOrigin + change.position
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

/**
 * Modifier for the Box wrapping the main LazyColumn:
 * registers its window position and viewport bounds for Project & intra-list drop resolution + auto-scroll.
 */
fun Modifier.projectDropSurface(
    controller: ProjectDragDropController,
    @Suppress("UNUSED_PARAMETER") onTaskDrop: (ProjectDragSession) -> Unit = {}
): Modifier = this
    .onGloballyPositioned { coords ->
        val winOrigin = coords.localToWindowSafe(Offset.Zero)
        controller.listWindowLeft = winOrigin.x
        controller.listWindowTop = winOrigin.y
        controller.listWidth = coords.size.width.toFloat()
        controller.listHeight = coords.size.height.toFloat()
    }

/**
 * Registers any composable (Sidebar Smart View Bento card, Area row, Project row, Top Strip pill,
 * Compact Mobile Drop pill, or Phase Header) as an active 2D drop zone with Sky-Blue hover highlight!
 */
fun Modifier.workspaceDropTarget(
    controller: ProjectDragDropController,
    key: String,
    destination: WorkspaceDragDropDestination,
    cornerRadius: Dp = 12.dp,
    priority: Int = 10,
    enabled: Boolean = true
): Modifier = composed {
    val colors = ThingsTheme.colors
    val currentDest by rememberUpdatedState(destination)
    val currentPriority by rememberUpdatedState(priority)

    DisposableEffect(controller, key, enabled) {
        if (!enabled) {
            controller.unregisterDropZone(key)
        }
        onDispose {
            controller.unregisterDropZone(key)
        }
    }

    val isHovered = enabled &&
        controller.session?.kind == ProjectDragKind.MOVE_TASK &&
        controller.session?.hoveredZoneKey == key

    this
        .onGloballyPositioned { coords ->
            if (!enabled || !coords.isAttached) {
                controller.unregisterDropZone(key)
            } else {
                val origin = coords.localToWindowSafe(Offset.Zero)
                val rect = IntRect(
                    left = origin.x.roundToInt(),
                    top = origin.y.roundToInt(),
                    right = (origin.x + coords.size.width).roundToInt(),
                    bottom = (origin.y + coords.size.height).roundToInt()
                )
                controller.registerDropZone(key, rect, currentDest, currentPriority)
            }
        }
        .drawWithContent {
            drawContent()
            if (isHovered) {
                val r = cornerRadius.toPx()
                drawRoundRect(
                    color = colors.thingsBlue.copy(alpha = 0.16f),
                    cornerRadius = CornerRadius(r, r)
                )
                drawRoundRect(
                    color = colors.thingsBlue,
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
}

internal fun LayoutCoordinates.localToWindowSafe(local: Offset): Offset =
    if (isAttached) runCatching { localToWindow(local) }.getOrDefault(Offset.Zero) else Offset.Zero

/**
 * Auto-scrolls the LazyColumn while something is dragged close to its top/bottom edge.
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
            if (s.externalDestination != null && s.externalDestination !is WorkspaceDragDropDestination.CurrentListReorder) {
                continue
            }
            val h = controller.listHeight
            if (h <= 0f) continue
            val localY = s.pointerWindow.y - controller.listWindowTop
            val localX = s.pointerWindow.x - controller.listWindowLeft
            if (localX < -24f || localX > controller.listWidth + 24f) continue
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
 * Insertion line + label drawn on top of the active LazyColumn (Project sections or Intra-List Reorder).
 * Rendered as an overlay so the list layout never jumps while dragging.
 */
@Composable
fun BoxScope.ProjectDropIndicatorOverlay(controller: ProjectDragDropController) {
    val session = controller.session ?: return
    val target = session.target
    val reorderY = session.listReorderIndicatorY
    val colors = ThingsTheme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(
        target?.headingId,
        target?.indexInSection,
        target?.headingInsertIndex,
        (session.externalDestination as? WorkspaceDragDropDestination.CurrentListReorder)?.dropIndex,
        session.kind
    ) {
        if (target != null || reorderY != null) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val indicatorYPx: Float? = target?.indicatorY ?: reorderY
    if (indicatorYPx != null) {
        val isHeading = session.kind == ProjectDragKind.NEW_HEADING
        val headingTitle = target?.headingId?.let { id -> controller.headings.find { it.id == id }?.title }
        val generalTitle = stringResource(R.string.v30_board_ungrouped_column)
        val baseLabel = when (session.kind) {
            ProjectDragKind.NEW_TODO -> stringResource(R.string.magic_plus_insertion_label)
            ProjectDragKind.NEW_HEADING -> stringResource(R.string.drop_heading_here)
            ProjectDragKind.MOVE_TASK -> stringResource(R.string.drop_move_here)
        }
        val sectionSuffix = when {
            isHeading -> null
            !headingTitle.isNullOrBlank() -> headingTitle
            target != null && controller.headings.isNotEmpty() -> generalTitle
            else -> null
        }
        val label = if (sectionSuffix != null) "$baseLabel · $sectionSuffix" else baseLabel
        val lineHalfPx = with(density) { 12.dp.toPx() }
        val y = (indicatorYPx - lineHalfPx).roundToInt().coerceAtLeast(0)

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
}

/**
 * Full-window Soft Bento Floating Drag Ghost Card rendered at the root Box of `ThingsSmartViewScreen`.
 * Follows `(windowX, windowY)` across Sidebar, Top Strip, Kanban columns, and Task Lists,
 * displaying the `v30_drag_multi_count` badge when dragging multi-selected tasks!
 */
@Composable
fun BoxScope.WorkspaceDragFloatingOverlay(controller: ProjectDragDropController) {
    val session = controller.session ?: return
    if (session.kind != ProjectDragKind.MOVE_TASK) return

    val colors = ThingsTheme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(session.hoveredZoneKey) {
        if (session.hoveredZoneKey != null) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val draggedCount = session.effectiveTaskIds.size
    val localPointer = session.pointerWindow - controller.rootWindowOrigin
    val offsetXPx = with(density) { 14.dp.toPx() }
    val offsetYPx = with(density) { 22.dp.toPx() }
    val maxGhostX = (controller.rootSize.width - with(density) { 260.dp.roundToPx() }).coerceAtLeast(12)
    val maxGhostY = (controller.rootSize.height - with(density) { 64.dp.roundToPx() }).coerceAtLeast(12)
    val ghostX = (localPointer.x + offsetXPx).roundToInt().coerceIn(8, maxGhostX)
    val ghostY = (localPointer.y - offsetYPx).roundToInt().coerceIn(8, maxGhostY)

    val destBadgeText: String? = when (val dest = session.externalDestination) {
        is WorkspaceDragDropDestination.SmartView -> dest.displayTitle.ifBlank { stringResource(dest.viewType.titleRes) }
        is WorkspaceDragDropDestination.AreaTarget -> dest.areaTitle
        is WorkspaceDragDropDestination.ProjectTarget -> {
            if (!dest.headingTitle.isNullOrBlank()) "${dest.projectTitle} › ${dest.headingTitle}"
            else dest.projectTitle
        }
        is WorkspaceDragDropDestination.BoardColumn -> {
            dest.headingTitle ?: stringResource(R.string.v30_board_ungrouped_column)
        }
        else -> null
    }

    Surface(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(ghostX, ghostY) }
            .widthIn(max = 340.dp)
            .shadow(14.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = colors.cardElevatedSurface,
        border = BorderStroke(
            width = 1.25.dp,
            color = if (destBadgeText != null) colors.thingsBlue else colors.thingsBlue.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
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
                    text = session.draggedTaskTitle?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.entity_new_todo),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (draggedCount > 1) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.thingsBlue
                    ) {
                        Text(
                            text = stringResource(R.string.v30_drag_multi_count, draggedCount),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (!destBadgeText.isNullOrBlank()) {
                Text(
                    text = "→ $destBadgeText",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.thingsBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Drag handle (⋮⋮) shown on open To-Do rows across all views (`Inbox`, `Today`, `Planned`,
 * `Anytime`, `Someday`, `Area`, `Project`, `Perspective`) — supporting both single-task
 * and multi-selected task dragging!
 */
@Composable
fun ProjectTaskDragHandle(
    task: TaskItem,
    controller: ProjectDragDropController,
    modifier: Modifier = Modifier,
    selectedTaskIds: Set<String> = emptySet(),
    orderedVisibleTaskIds: List<String> = emptyList()
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val currentTask by rememberUpdatedState(task)
    val currentSelectedIds by rememberUpdatedState(selectedTaskIds)
    val currentOrderedVisibleIds by rememberUpdatedState(orderedVisibleTaskIds)
    val coordsHolder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .size(width = 26.dp, height = 30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHovered) colors.thingsBlueSoft else Color.Transparent)
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
                    val sel = currentSelectedIds
                    val orderedIds = if (currentTask.id in sel && sel.size > 1) {
                        val visibleOrdered = currentOrderedVisibleIds.filter { it in sel }
                        val visibleSet = visibleOrdered.toHashSet()
                        val remaining = sel.filterNot { it in visibleSet }
                        (visibleOrdered + remaining).distinct().ifEmpty { listOf(currentTask.id) }
                    } else {
                        listOf(currentTask.id)
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    controller.start(
                        kind = ProjectDragKind.MOVE_TASK,
                        pointerWindow = windowPos,
                        taskId = currentTask.id,
                        taskTitle = currentTask.title,
                        taskIds = orderedIds
                    )
                    // From here on, workspaceDragRootSurface tracks full 2D pointer motion in Initial pass.
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
