package com.pv.thingsbook.ui.components

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.pv.thingsbook.R
import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.EffortLevel
import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.PdfPageOrientation
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.ProjectRoadmapEngine
import com.pv.thingsbook.domain.model.ProjectRoadmapLayout
import com.pv.thingsbook.domain.model.ProjectWorkspaceMode
import com.pv.thingsbook.domain.model.RoadmapPdfExportConfig
import com.pv.thingsbook.domain.model.RoadmapPdfTimelinePreset
import com.pv.thingsbook.domain.model.RoadmapPhaseBar
import com.pv.thingsbook.domain.model.RoadmapTaskBar
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.model.TimeblockScheduleUtils
import com.pv.thingsbook.domain.model.formatDurationCompact
import com.pv.thingsbook.domain.model.isTaskActivelyBlocked
import com.pv.thingsbook.ui.theme.ThingsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val GlacierGradientStart = Color(0xFF74C4FF)
private val GlacierGradientEnd = Color(0xFF3898EC)

/**
 * Soft Bento 3-Way Segmented Switcher (`List | Board | Roadmap`) rendered inside `ViewTitleHeader`
 * whenever an active Project is open.
 */
@Composable
fun ProjectWorkspaceSegmentedSwitcher(
    currentMode: ProjectWorkspaceMode,
    onSelectMode: (ProjectWorkspaceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    val items: List<Triple<ProjectWorkspaceMode, Int, ImageVector>> = listOf(
        Triple(ProjectWorkspaceMode.LIST, R.string.v30_project_mode_list, Icons.Outlined.Checklist),
        Triple(ProjectWorkspaceMode.BOARD, R.string.v30_project_mode_board, Icons.Outlined.GridView),
        Triple(ProjectWorkspaceMode.ROADMAP, R.string.v30_project_mode_roadmap, Icons.Outlined.CalendarMonth)
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.tagPillBg)
            .border(0.75.dp, colors.hairlineDivider, RoundedCornerShape(11.dp))
            .padding(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEach { (mode, labelRes, icon) ->
            val selected = currentMode == mode
            val label = stringResource(labelRes)
            ThingsHoverTooltip(text = label) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.5.dp))
                        .background(if (selected) colors.cardSurface else Color.Transparent)
                        .then(
                            if (selected) {
                                Modifier.border(
                                    0.75.dp,
                                    colors.thingsBlue.copy(alpha = 0.35f),
                                    RoundedCornerShape(8.5.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            if (!selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectMode(mode)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) colors.thingsBlue else colors.secondaryText,
                        modifier = Modifier.size(14.5.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) colors.thingsBlue else colors.secondaryText,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Reusable `Export Roadmap` (`PDF` / `PNG`) dropdown pill used in both `ViewTitleHeader` and `ProjectRoadmapWorkspace`.
 */
@Composable
fun RoadmapExportMenuPill(
    onExportPdf: () -> Unit,
    onExportPng: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val exportTitle = stringResource(R.string.v30_roadmap_export)

    Box(modifier = modifier) {
        ThingsHoverTooltip(text = exportTitle) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { expanded = true },
                shape = RoundedCornerShape(10.dp),
                color = colors.thingsBlueSoft,
                border = BorderStroke(0.75.dp, colors.thingsBlue.copy(alpha = 0.42f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = exportTitle,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = exportTitle,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.thingsBlue,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(17.dp)
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.v30_roadmap_export_pdf),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                onClick = {
                    expanded = false
                    onExportPdf()
                }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(17.dp)
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.v30_roadmap_export_png),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                onClick = {
                    expanded = false
                    onExportPng()
                }
            )
        }
    }
}

/**
 * Compact Top Drop Dock shown automatically on narrower screens (`< 700dp`) while dragging To-Dos,
 * exposing Smart Views, Areas, Projects, Logbook, and Trash as Sky-Blue cross-container drop zones!
 */
@Composable
fun CompactMobileDragDropDock(
    controller: ProjectDragDropController,
    areas: List<Area>,
    projects: List<Project>,
    modifier: Modifier = Modifier
) {
    val session = controller.session
    if (session?.kind != ProjectDragKind.MOVE_TASK) return
    val colors = ThingsTheme.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .shadow(10.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = colors.cardElevatedSurface,
        border = BorderStroke(1.dp, colors.thingsBlue.copy(alpha = 0.38f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Row 1: Smart Views (Inbox, Today, Planned, Anytime, Someday, Logbook, Trash)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(SmartViewType.entries, key = { "mob_drop_sv_${it.name}" }) { viewType ->
                    val label = stringResource(viewType.titleRes)
                    Row(
                        modifier = Modifier
                            .workspaceDropTarget(
                                controller = controller,
                                key = "mobile_dock_smart_${viewType.name}",
                                destination = WorkspaceDragDropDestination.SmartView(viewType, label),
                                cornerRadius = 50.dp,
                                priority = 25
                            )
                            .clip(RoundedCornerShape(50))
                            .background(colors.tagPillBg)
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SmartViewIcon(viewType = viewType, modifier = Modifier.size(14.dp), isSelected = true)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primaryText,
                            maxLines = 1
                        )
                    }
                }
            }

            // Row 2: Areas & Active Projects
            if (areas.isNotEmpty() || projects.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(areas, key = { "mob_drop_area_${it.id}" }) { area ->
                        Row(
                            modifier = Modifier
                                .workspaceDropTarget(
                                    controller = controller,
                                    key = "mobile_dock_area_${area.id}",
                                    destination = WorkspaceDragDropDestination.AreaTarget(area.id, area.title),
                                    cornerRadius = 50.dp,
                                    priority = 25
                                )
                                .clip(RoundedCornerShape(50))
                                .background(colors.thingsBlueSoft.copy(alpha = 0.65f))
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = area.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.primaryText,
                                maxLines = 1
                            )
                        }
                    }

                    items(projects, key = { "mob_drop_proj_${it.id}" }) { proj ->
                        Row(
                            modifier = Modifier
                                .workspaceDropTarget(
                                    controller = controller,
                                    key = "mobile_dock_proj_${proj.id}",
                                    destination = WorkspaceDragDropDestination.ProjectTarget(proj.id, proj.title),
                                    cornerRadius = 50.dp,
                                    priority = 25
                                )
                                .clip(RoundedCornerShape(50))
                                .background(colors.tagPillBg)
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ProjectProgressRing(progress = proj.progress, size = 13.dp)
                            Text(
                                text = proj.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.primaryText,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Mode 2: Project Board Workspace (Calm Soft Bento Kanban)
// =============================================================================

@Composable
fun ProjectBoardWorkspace(
    project: Project,
    projectHeadings: List<Heading>,
    projectSections: Map<String?, List<TaskItem>>,
    headingTaskCounts: Map<String, Pair<Int, Int>>,
    allOpenTasks: List<TaskItem>,
    openTaskIds: Set<String>,
    selectedTaskIds: Set<String>,
    isSelectionModeActive: Boolean = selectedTaskIds.size > 1,
    allVisibleTaskIds: List<String>,
    controller: ProjectDragDropController,
    locale: Locale,
    onCreateTaskInSection: (headingId: String?, sectionTasks: List<TaskItem>, indexInSection: Int) -> Unit,
    onCreatePhase: (title: String) -> Unit,
    onRenameHeading: (headingId: String, newTitle: String) -> Unit,
    onUpdateHeadingStartDate: (headingId: String, startDate: Long?) -> Unit,
    onUpdateHeadingEndDate: (headingId: String, endDate: Long?) -> Unit,
    onDeleteHeading: (headingId: String) -> Unit,
    onUpdateTaskStatus: (taskId: String, status: TaskStatus) -> Unit,
    onToggleTaskPin: (taskId: String) -> Unit,
    onToggleTaskMilestone: (taskId: String) -> Unit = {},
    onOpenTaskEditor: (taskId: String) -> Unit,
    onSelectSingleTask: ((taskId: String) -> Unit)? = null,
    onToggleTaskSelection: (taskId: String) -> Unit,
    onRangeSelectTask: (taskId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val density = LocalDensity.current
    val horizontalScrollState = rememberScrollState()
    var boardLeftWinX by remember { mutableFloatStateOf(0f) }
    var boardWidthPx by remember { mutableFloatStateOf(0f) }

    val ungroupedTasks = projectSections[null].orEmpty()
    val isDraggingTask = controller.session?.kind == ProjectDragKind.MOVE_TASK
    val showGeneralColumn = ungroupedTasks.isNotEmpty() || projectHeadings.isEmpty() || isDraggingTask

    SideEffect {
        controller.isBoardActive = true
        controller.boardProjectId = project.id
        controller.boardSectionTasks = projectSections
    }
    DisposableEffect(controller, project.id) {
        onDispose {
            controller.clearBoardGeometry()
        }
    }

    // Horizontal auto-scroll when dragging near left/right edge of the Kanban board
    val edgePx = with(density) { 80.dp.toPx() }
    val maxSpeedPx = with(density) { 14.dp.toPx() }
    LaunchedEffect(isDraggingTask, horizontalScrollState) {
        if (!isDraggingTask) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val s = controller.session ?: break
            if (boardWidthPx <= 0f) continue
            val localX = s.pointerWindow.x - boardLeftWinX
            if (localX < -20f || localX > boardWidthPx + 20f) continue
            val delta = when {
                localX < edgePx -> -maxSpeedPx * ((edgePx - localX) / edgePx).coerceIn(0.2f, 1f)
                localX > boardWidthPx - edgePx -> maxSpeedPx * ((localX - (boardWidthPx - edgePx)) / edgePx).coerceIn(0.2f, 1f)
                else -> 0f
            }
            if (delta != 0f) {
                val consumed = runCatching { horizontalScrollState.scrollBy(delta) }.getOrDefault(0f)
                if (consumed != 0f) controller.refresh()
            }
        }
    }

    var isCreatingNewPhase by remember { mutableStateOf(false) }
    var newPhaseField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    val newPhaseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isCreatingNewPhase) {
        if (isCreatingNewPhase) {
            delay(50)
            runCatching { newPhaseFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val win = coords.localToWindowSafe(Offset.Zero)
                boardLeftWinX = win.x
                boardWidthPx = coords.size.width.toFloat()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState)
                .padding(start = 16.dp, end = 96.dp, top = 6.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (showGeneralColumn) {
                val generalLabel = stringResource(R.string.v30_board_ungrouped_column)
                ProjectBoardPhaseColumn(
                    heading = null,
                    columnTitle = generalLabel,
                    tasks = ungroupedTasks,
                    completedCount = 0,
                    totalCount = ungroupedTasks.size,
                    allOpenTasks = allOpenTasks,
                    openTaskIds = openTaskIds,
                    selectedTaskIds = selectedTaskIds,
                    isSelectionModeActive = isSelectionModeActive,
                    allVisibleTaskIds = allVisibleTaskIds,
                    controller = controller,
                    locale = locale,
                    onAddTopTask = { onCreateTaskInSection(null, ungroupedTasks, 0) },
                    onAddBottomTask = { onCreateTaskInSection(null, ungroupedTasks, ungroupedTasks.size) },
                    onRenameHeading = null,
                    onUpdateStartDate = null,
                    onUpdateEndDate = null,
                    onDeleteHeading = null,
                    onUpdateTaskStatus = onUpdateTaskStatus,
                    onToggleTaskPin = onToggleTaskPin,
                    onToggleTaskMilestone = onToggleTaskMilestone,
                    onOpenTaskEditor = onOpenTaskEditor,
                    onSelectSingleTask = onSelectSingleTask,
                    onToggleTaskSelection = onToggleTaskSelection,
                    onRangeSelectTask = onRangeSelectTask
                )
            }

            projectHeadings.forEach { heading ->
                val colTasks = projectSections[heading.id].orEmpty()
                val counts = headingTaskCounts[heading.id]
                val completedInPhase = counts?.first ?: 0
                val totalInPhase = counts?.second ?: colTasks.size
                ProjectBoardPhaseColumn(
                    heading = heading,
                    columnTitle = heading.title.ifBlank { stringResource(R.string.entity_heading) },
                    tasks = colTasks,
                    completedCount = completedInPhase,
                    totalCount = totalInPhase,
                    allOpenTasks = allOpenTasks,
                    openTaskIds = openTaskIds,
                    selectedTaskIds = selectedTaskIds,
                    isSelectionModeActive = isSelectionModeActive,
                    allVisibleTaskIds = allVisibleTaskIds,
                    controller = controller,
                    locale = locale,
                    onAddTopTask = { onCreateTaskInSection(heading.id, colTasks, 0) },
                    onAddBottomTask = { onCreateTaskInSection(heading.id, colTasks, colTasks.size) },
                    onRenameHeading = { newName -> onRenameHeading(heading.id, newName) },
                    onUpdateStartDate = { s -> onUpdateHeadingStartDate(heading.id, s) },
                    onUpdateEndDate = { e -> onUpdateHeadingEndDate(heading.id, e) },
                    onDeleteHeading = { onDeleteHeading(heading.id) },
                    onUpdateTaskStatus = onUpdateTaskStatus,
                    onToggleTaskPin = onToggleTaskPin,
                    onToggleTaskMilestone = onToggleTaskMilestone,
                    onOpenTaskEditor = onOpenTaskEditor,
                    onSelectSingleTask = onSelectSingleTask,
                    onToggleTaskSelection = onToggleTaskSelection,
                    onRangeSelectTask = onRangeSelectTask
                )
            }

            // Trailing "+ New Phase" Soft Bento Card
            Surface(
                modifier = Modifier
                    .width(272.dp)
                    .clip(RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = colors.tagPillBg.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, colors.thingsBlue.copy(alpha = 0.25f))
            ) {
                if (isCreatingNewPhase) {
                    val commitPhase = {
                        val trimmed = newPhaseField.text.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreatePhase(trimmed)
                            newPhaseField = TextFieldValue(text = "", selection = TextRange(0))
                            isCreatingNewPhase = false
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newPhaseField,
                            onValueChange = { newPhaseField = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.entity_new_heading),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commitPhase() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(newPhaseFocusRequester)
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown &&
                                        (ev.key == Key.Enter || ev.key == Key.NumPadEnter)
                                    ) {
                                        commitPhase()
                                        true
                                    } else if (ev.type == KeyEventType.KeyDown && ev.key == Key.Escape) {
                                        isCreatingNewPhase = false
                                        true
                                    } else {
                                        false
                                    }
                                }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    newPhaseField = TextFieldValue(text = "", selection = TextRange(0))
                                    isCreatingNewPhase = false
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = commitPhase,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = stringResource(R.string.action_save),
                                    tint = colors.thingsBlue,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { isCreatingNewPhase = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.thingsBlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.v30_board_add_phase),
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.v30_board_add_phase),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.thingsBlue
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectBoardPhaseColumn(
    heading: Heading?,
    columnTitle: String,
    tasks: List<TaskItem>,
    completedCount: Int,
    totalCount: Int,
    allOpenTasks: List<TaskItem>,
    openTaskIds: Set<String>,
    selectedTaskIds: Set<String>,
    isSelectionModeActive: Boolean,
    allVisibleTaskIds: List<String>,
    controller: ProjectDragDropController,
    locale: Locale,
    onAddTopTask: () -> Unit,
    onAddBottomTask: () -> Unit,
    onRenameHeading: ((String) -> Unit)?,
    onUpdateStartDate: ((Long?) -> Unit)?,
    onUpdateEndDate: ((Long?) -> Unit)?,
    onDeleteHeading: (() -> Unit)?,
    onUpdateTaskStatus: (String, TaskStatus) -> Unit,
    onToggleTaskPin: (String) -> Unit,
    onToggleTaskMilestone: (String) -> Unit,
    onOpenTaskEditor: (String) -> Unit,
    onSelectSingleTask: ((String) -> Unit)? = null,
    onToggleTaskSelection: (String) -> Unit,
    onRangeSelectTask: (String) -> Unit
) {
    val colors = ThingsTheme.colors
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val headingKey = heading?.id
    val zoneHoverKey = "board_col_${headingKey ?: "general"}"
    val session = controller.session
    val isColumnHovered = session?.kind == ProjectDragKind.MOVE_TASK && session.hoveredZoneKey == zoneHoverKey
    val draggedIdsSet = remember(session?.draggedTaskIds, session?.draggedTaskId) {
        session?.effectiveTaskIds?.toHashSet().orEmpty()
    }

    var colWindowTop by remember { mutableFloatStateOf(0f) }
    var showStartPicker by remember(headingKey) { mutableStateOf(false) }
    var showEndPicker by remember(headingKey) { mutableStateOf(false) }
    var isEditingTitle by remember(headingKey) { mutableStateOf(heading != null && heading.title.isEmpty()) }
    var editedTitle by remember(headingKey, heading?.title) {
        val init = heading?.title.orEmpty()
        mutableStateOf(TextFieldValue(text = init, selection = TextRange(0, init.length)))
    }

    DisposableEffect(controller, headingKey) {
        onDispose {
            controller.unregisterBoardColumn(headingKey)
        }
    }

    val startLocal = remember(heading?.startDate, zone) {
        heading?.startDate?.let { ms ->
            runCatching { Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }.getOrNull()
        }
    }
    val endLocal = remember(heading?.endDate, zone) {
        heading?.endDate?.let { ms ->
            runCatching { Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }.getOrNull()
        }
    }
    val datePattern = if (locale.language == "de") "d. MMM" else "MMM d"
    val formattedStart = remember(startLocal, locale) {
        startLocal?.let { runCatching { it.format(DateTimeFormatter.ofPattern(datePattern, locale)) }.getOrNull() }
    }
    val formattedEnd = remember(endLocal, locale) {
        endLocal?.let { runCatching { it.format(DateTimeFormatter.ofPattern(datePattern, locale)) }.getOrNull() }
    }
    val isOverduePhase = endLocal != null && endLocal.isBefore(today) && tasks.isNotEmpty()
    val phaseProgress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    Surface(
        modifier = Modifier
            .width(296.dp)
            .fillMaxHeight()
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    val win = coords.localToWindowSafe(Offset.Zero)
                    colWindowTop = win.y
                    controller.registerBoardColumn(
                        headingId = headingKey,
                        headingTitle = columnTitle,
                        boundsWindow = IntRect(
                            left = win.x.roundToInt(),
                            top = win.y.roundToInt(),
                            right = (win.x + coords.size.width).roundToInt(),
                            bottom = (win.y + coords.size.height).roundToInt()
                        )
                    )
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = if (isColumnHovered) {
            colors.thingsBlueSoft.copy(alpha = 0.55f)
        } else {
            colors.sidebarBackground.copy(alpha = if (colors.isDark) 0.92f else 0.85f)
        },
        border = BorderStroke(
            width = if (isColumnHovered) 1.75.dp else 0.85.dp,
            color = if (isColumnHovered) colors.thingsBlue else colors.hairlineDivider
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(11.dp)
            ) {
                // Column Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isEditingTitle && onRenameHeading != null) {
                        val commitRename = {
                            val clean = editedTitle.text.trim()
                            if (clean.isNotEmpty()) {
                                onRenameHeading(clean)
                                isEditingTitle = false
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = editedTitle,
                                onValueChange = { editedTitle = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { commitRename() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown &&
                                            (ev.key == Key.Enter || ev.key == Key.NumPadEnter)
                                        ) {
                                            commitRename()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                            )
                            IconButton(onClick = commitRename, modifier = Modifier.size(26.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = stringResource(R.string.action_save),
                                    tint = colors.thingsBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (heading?.title.isNullOrEmpty() && editedTitle.text.isBlank()) {
                                        onDeleteHeading?.invoke()
                                    } else {
                                        isEditingTitle = false
                                    }
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .width(4.dp)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(GlacierGradientStart, GlacierGradientEnd)
                                            )
                                        )
                                )
                                Text(
                                    text = columnTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primaryText,
                                    maxLines = Int.MAX_VALUE,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(top = 2.dp)
                                )
                                Surface(
                                    modifier = Modifier.padding(top = 2.dp),
                                    shape = RoundedCornerShape(50),
                                    color = colors.cardSurface
                                ) {
                                    Text(
                                        text = tasks.size.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.secondaryText,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(onClick = onAddTopTask, modifier = Modifier.size(25.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = stringResource(R.string.entity_new_todo),
                                        tint = colors.thingsBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (onRenameHeading != null) {
                                    IconButton(
                                        onClick = {
                                            editedTitle = TextFieldValue(
                                                text = heading?.title.orEmpty(),
                                                selection = TextRange(0, heading?.title?.length ?: 0)
                                            )
                                            isEditingTitle = true
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = stringResource(R.string.entity_heading),
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(13.5.dp)
                                        )
                                    }
                                }
                                if (onDeleteHeading != null) {
                                    IconButton(onClick = onDeleteHeading, modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Progress ring + Phase Start/End Date Badges
                    if (totalCount > 0 || heading != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (totalCount > 0) {
                                ProjectProgressRing(
                                    progress = phaseProgress,
                                    size = 14.dp,
                                    showFraction = true,
                                    completedCount = completedCount,
                                    totalCount = totalCount,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                            if (heading != null && onUpdateStartDate != null) {
                                val startLabel = if (formattedStart != null) {
                                    stringResource(R.string.v30_phase_starts_on, formattedStart)
                                } else {
                                    "+ ${stringResource(R.string.v30_phase_set_start_date)}"
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (formattedStart != null) colors.thingsBlueSoft else colors.cardSurface.copy(alpha = 0.72f))
                                        .clickable { showStartPicker = true }
                                        .padding(horizontal = 7.5.dp, vertical = 2.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        tint = if (formattedStart != null) colors.thingsBlue else colors.secondaryText,
                                        modifier = Modifier.size(10.5.dp)
                                    )
                                    Text(
                                        text = startLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (formattedStart != null) colors.thingsBlue else colors.secondaryText
                                    )
                                }
                            }
                            if (heading != null && onUpdateEndDate != null) {
                                val endLabel = if (formattedEnd != null) {
                                    stringResource(R.string.phase_ends_on, formattedEnd)
                                } else {
                                    "+ ${stringResource(R.string.phase_set_end_date)}"
                                }
                                val endBg = when {
                                    isOverduePhase -> colors.deadlineSoftBg
                                    formattedEnd != null -> colors.thingsBlueSoft
                                    else -> colors.cardSurface.copy(alpha = 0.72f)
                                }
                                val endFg = when {
                                    isOverduePhase -> colors.deadlineUrgentRed
                                    formattedEnd != null -> colors.thingsBlue
                                    else -> colors.secondaryText
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(endBg)
                                        .clickable { showEndPicker = true }
                                        .padding(horizontal = 7.5.dp, vertical = 2.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Flag,
                                        contentDescription = null,
                                        tint = endFg,
                                        modifier = Modifier.size(10.5.dp)
                                    )
                                    Text(
                                        text = endLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = endFg
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Task Cards List
                val colScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(colScrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tasks.forEach { task ->
                        KanbanTaskCard(
                            task = task,
                            isSelected = task.id in selectedTaskIds,
                            isSelectionModeActive = isSelectionModeActive,
                            isBeingDragged = task.id in draggedIdsSet,
                            allOpenTasks = allOpenTasks,
                            openTaskIds = openTaskIds,
                            selectedTaskIds = selectedTaskIds,
                            allVisibleTaskIds = allVisibleTaskIds,
                            controller = controller,
                            locale = locale,
                            onStatusChange = { next -> onUpdateTaskStatus(task.id, next) },
                            onTogglePin = { onToggleTaskPin(task.id) },
                            onToggleMilestone = { onToggleTaskMilestone(task.id) },
                            onOpenEditor = { onOpenTaskEditor(task.id) },
                            onSelectSingle = onSelectSingleTask?.let { fn -> { fn(task.id) } },
                            onToggleSelection = { onToggleTaskSelection(task.id) },
                            onRangeSelect = { onRangeSelectTask(task.id) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Column Footer: "+ New To-Do"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.cardSurface.copy(alpha = 0.65f))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onAddBottomTask)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.entity_new_todo),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.thingsBlue
                    )
                }
            }

            // Sky-Blue Intra-Column Insertion Line when dragging over this column
            val indicatorWinY = session?.boardIndicatorWindowY
            if (isColumnHovered && indicatorWinY != null) {
                val localIndicatorY = (indicatorWinY - colWindowTop).roundToInt().coerceAtLeast(44)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(0, localIndicatorY) }
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.thingsBlue)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.5.dp)
                            .background(colors.thingsBlue)
                    )
                }
            }
        }
    }

    if (showStartPicker && heading != null && onUpdateStartDate != null) {
        DeadlineDatePickerDialog(
            currentDeadline = heading.startDate,
            locale = locale,
            onApplyDeadline = onUpdateStartDate,
            onDismiss = { showStartPicker = false },
            titleText = stringResource(R.string.v30_phase_set_start_date),
            clearButtonText = stringResource(R.string.v30_phase_clear_start_date)
        )
    }
    if (showEndPicker && heading != null && onUpdateEndDate != null) {
        DeadlineDatePickerDialog(
            currentDeadline = heading.endDate,
            locale = locale,
            onApplyDeadline = onUpdateEndDate,
            onDismiss = { showEndPicker = false },
            titleText = stringResource(R.string.phase_set_end_date),
            clearButtonText = stringResource(R.string.phase_clear_end_date)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun KanbanTaskCard(
    task: TaskItem,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    isBeingDragged: Boolean,
    allOpenTasks: List<TaskItem>,
    openTaskIds: Set<String>,
    selectedTaskIds: Set<String>,
    allVisibleTaskIds: List<String>,
    controller: ProjectDragDropController,
    locale: Locale,
    onStatusChange: (TaskStatus) -> Unit,
    onTogglePin: () -> Unit,
    onToggleMilestone: (() -> Unit)? = null,
    onOpenEditor: () -> Unit,
    onSelectSingle: (() -> Unit)? = null,
    onToggleSelection: () -> Unit,
    onRangeSelect: () -> Unit
) {
    val colors = ThingsTheme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val isBlocked = remember(task.blockedByTaskIds, openTaskIds) {
        isTaskActivelyBlocked(task, openTaskIds)
    }
    val activeOpenBlockerIds = remember(task.blockedByTaskIds, openTaskIds) {
        task.blockedByTaskIds.filter { it in openTaskIds }.distinct()
    }

    DisposableEffect(controller, task.id) {
        onDispose {
            controller.unregisterBoardCardBounds(task.id)
        }
    }

    var modifierHeldShift by remember { mutableStateOf(false) }
    var modifierHeldCtrlOrMeta by remember { mutableStateOf(false) }
    var lastPlainTapUptimeMs by remember(task.id) { mutableLongStateOf(0L) }
    var showKanbanContextMenu by remember(task.id) { mutableStateOf(false) }
    var kanbanContextMenuOffsetDp by remember(task.id) { mutableStateOf(DpOffset(12.dp, 0.dp)) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isBeingDragged) 0.35f else 1f)
                .onGloballyPositioned { coords ->
                    if (coords.isAttached) {
                        val win = coords.localToWindowSafe(Offset.Zero)
                        controller.registerBoardCardBounds(
                            taskId = task.id,
                            windowTop = win.y,
                            windowBottom = win.y + coords.size.height
                        )
                    }
                }
                .pointerInput(task.id) {
                    awaitPointerEventScope {
                        var wasSecondaryPressed = false
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val isRightClick = (ev.type == PointerEventType.Press && ev.buttons.isSecondaryPressed) ||
                                (wasSecondaryPressed && !ev.buttons.isSecondaryPressed)
                            if (isRightClick) {
                                wasSecondaryPressed = false
                                val pos = ev.changes.firstOrNull()?.position
                                if (pos != null && pos.x.isFinite()) {
                                    kanbanContextMenuOffsetDp = with(density) {
                                        DpOffset(pos.x.toDp().coerceAtLeast(8.dp), 0.dp)
                                    }
                                }
                                ev.changes.forEach { it.consume() }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                showKanbanContextMenu = true
                                continue
                            }
                            wasSecondaryPressed = ev.buttons.isSecondaryPressed
                            if (ev.type == PointerEventType.Press) {
                                modifierHeldShift = ev.keyboardModifiers.isShiftPressed
                                modifierHeldCtrlOrMeta = ev.keyboardModifiers.isCtrlPressed || ev.keyboardModifiers.isMetaPressed
                            }
                        }
                    }
                }
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        when {
                            modifierHeldShift -> {
                                lastPlainTapUptimeMs = 0L
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRangeSelect()
                            }
                            modifierHeldCtrlOrMeta -> {
                                lastPlainTapUptimeMs = 0L
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleSelection()
                            }
                            else -> {
                                val now = SystemClock.uptimeMillis()
                                if (lastPlainTapUptimeMs > 0L && (now - lastPlainTapUptimeMs) <= 380L) {
                                    lastPlainTapUptimeMs = 0L
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenEditor()
                                } else {
                                    lastPlainTapUptimeMs = now
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isSelectionModeActive) {
                                        onToggleSelection()
                                    } else if (onSelectSingle != null) {
                                        onSelectSingle()
                                    } else {
                                        onToggleSelection()
                                    }
                                }
                            }
                        }
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleSelection()
                    }
                ),
            shape = RoundedCornerShape(14.dp),
            color = if (isSelected) colors.thingsBlueSoft else colors.cardSurface,
            shadowElevation = if (colors.isDark) 2.dp else 1.5.dp,
            border = BorderStroke(
                width = when {
                    isSelected -> 1.5.dp
                    task.isMilestone -> 1.25.dp
                    else -> 0.75.dp
                },
                color = when {
                    isSelected -> colors.thingsBlue
                    task.isMilestone -> colors.milestoneViolet.copy(alpha = 0.58f)
                    isBlocked -> colors.somedayWarmAmber.copy(alpha = 0.45f)
                    else -> colors.hairlineDivider
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isBlocked) 0.72f else 1f)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    ThingsCheckbox(
                        status = task.status,
                        onStatusChange = onStatusChange,
                        isDashedSomeday = task.scheduleState == ScheduleState.SOMEDAY,
                        isMilestone = task.isMilestone,
                        size = 18.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = task.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (task.status != TaskStatus.OPEN) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (task.title.isBlank()) colors.tertiaryText else colors.primaryText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 3.dp)
                    )
                    if (task.isPinned) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = stringResource(R.string.v25_pinned_badge),
                            tint = colors.thingsBlue,
                            modifier = Modifier
                                .padding(top = 5.dp, start = 4.dp)
                                .size(13.5.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onTogglePin)
                        )
                    }
                    val openDetailsLabel = stringResource(R.string.v30_open_details_action)
                    ThingsHoverTooltip(
                        text = openDetailsLabel,
                        enableTouchLongPress = false
                    ) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenEditor()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInFull,
                                contentDescription = openDetailsLabel,
                                tint = colors.secondaryText.copy(alpha = 0.72f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    ThingsHoverTooltip(
                        text = stringResource(R.string.action_more_options),
                        enableTouchLongPress = false
                    ) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                kanbanContextMenuOffsetDp = DpOffset(12.dp, 0.dp)
                                showKanbanContextMenu = true
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreHoriz,
                                contentDescription = stringResource(R.string.action_more_options),
                                tint = colors.secondaryText.copy(alpha = 0.72f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    if (task.status == TaskStatus.OPEN) {
                        ProjectTaskDragHandle(
                            task = task,
                            controller = controller,
                            selectedTaskIds = selectedTaskIds,
                            orderedVisibleTaskIds = allVisibleTaskIds
                        )
                    }
                }

                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 30.dp)
                    )
                }

                val hasBadges = task.isMilestone ||
                    activeOpenBlockerIds.isNotEmpty() ||
                    EffortLevel.normalize(task.effortLevel) != null ||
                    task.scheduledStartMinutes != null ||
                    task.durationMinutes != null ||
                    task.whenDate != null ||
                    task.scheduleState == ScheduleState.TODAY ||
                    task.scheduleState == ScheduleState.THIS_EVENING ||
                    task.scheduleState == ScheduleState.SOMEDAY ||
                    task.deadline != null ||
                    task.checklistItems.isNotEmpty() ||
                    task.tags.isNotEmpty()

                if (hasBadges) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 30.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (task.isMilestone) {
                            ThingsTagPill(
                                title = "◆ ${stringResource(R.string.v30_milestone_label)}",
                                containerColor = colors.milestoneVioletSoft,
                                contentColor = colors.milestoneViolet,
                                borderColor = colors.milestoneViolet.copy(alpha = 0.45f),
                                onClick = onToggleMilestone
                            )
                        }

                        if (activeOpenBlockerIds.isNotEmpty()) {
                            val firstBlockerTitle = if (activeOpenBlockerIds.size == 1) {
                                allOpenTasks.firstOrNull { it.id == activeOpenBlockerIds.first() }?.title?.trim()?.takeIf { it.isNotEmpty() }
                            } else null
                            val blockedLabel = if (firstBlockerTitle != null) {
                                stringResource(R.string.v30_blocked_by_badge, firstBlockerTitle)
                            } else {
                                stringResource(R.string.v30_blocked_by_count_badge, activeOpenBlockerIds.size)
                            }
                            ThingsTagPill(title = "⏳ $blockedLabel")
                        }

                        when (EffortLevel.normalize(task.effortLevel)) {
                            EffortLevel.QUICK_WIN -> ThingsTagPill(title = "⚡ ${stringResource(R.string.v30_effort_quick_win)}")
                            EffortLevel.MEDIUM -> ThingsTagPill(title = "◐ ${stringResource(R.string.v30_effort_medium)}")
                            EffortLevel.DEEP_FOCUS -> ThingsTagPill(title = "🧠 ${stringResource(R.string.v30_effort_deep_focus)}")
                        }

                        if (task.scheduledStartMinutes != null) {
                            ThingsTagPill(
                                title = TimeblockScheduleUtils.formatTimeblockBadge(
                                    task.scheduledStartMinutes,
                                    task.durationMinutes
                                )
                            )
                        } else if (task.durationMinutes != null) {
                            val durFmt = formatDurationCompact(task.durationMinutes) ?: "${task.durationMinutes}m"
                            ThingsTagPill(title = "⏱ $durFmt")
                        }

                        if (task.whenDate != null ||
                            task.scheduleState == ScheduleState.TODAY ||
                            task.scheduleState == ScheduleState.THIS_EVENING ||
                            task.scheduleState == ScheduleState.SOMEDAY
                        ) {
                            ThingsWhenBadge(
                                scheduleState = task.scheduleState,
                                whenDateEpochMillis = task.whenDate,
                                locale = locale
                            )
                        }

                        task.deadline?.let { dl ->
                            ThingsDeadlineBadge(deadlineEpochMillis = dl, locale = locale)
                        }

                        if (task.checklistItems.isNotEmpty()) {
                            val done = task.checklistItems.count { it.isCompleted }
                            ThingsTagPill(title = "☑ $done/${task.checklistItems.size}")
                        }

                        task.tags.forEach { tag ->
                            ThingsTagPill(title = tag.title)
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showKanbanContextMenu,
            onDismissRequest = { showKanbanContextMenu = false },
            offset = kanbanContextMenuOffsetDp,
            modifier = Modifier.background(colors.cardElevatedSurface)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.v30_open_details_action),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.thingsBlue
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.OpenInFull,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showKanbanContextMenu = false
                    onOpenEditor()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isSelected) stringResource(R.string.context_deselect_task) else stringResource(R.string.context_select_task),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.thingsBlue
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircleOutline,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showKanbanContextMenu = false
                    onToggleSelection()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(if (task.isPinned) R.string.v25_unpin_task else R.string.v25_pin_task),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primaryText
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showKanbanContextMenu = false
                    onTogglePin()
                }
            )
            if (onToggleMilestone != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(
                                if (task.isMilestone) R.string.v30_remove_milestone else R.string.v30_mark_as_milestone
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (task.isMilestone) colors.milestoneViolet else colors.primaryText
                        )
                    },
                    leadingIcon = {
                        ThingsMilestoneDiamondIcon(
                            filled = task.isMilestone,
                            tint = colors.milestoneViolet,
                            size = 16.dp
                        )
                    },
                    onClick = {
                        showKanbanContextMenu = false
                        onToggleMilestone()
                    }
                )
            }
            val isClosed = task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELED
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isClosed) stringResource(R.string.action_reopen_todo) else stringResource(R.string.action_complete),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primaryText
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showKanbanContextMenu = false
                    onStatusChange(if (isClosed) TaskStatus.OPEN else TaskStatus.COMPLETED)
                }
            )
        }
    }
}

// =============================================================================
// Mode 3: Project Roadmap Workspace (Calm Bento Gantt + 1-Click PDF/PNG Export)
// =============================================================================

private sealed interface RoadmapDisplayRow {
    data class PhaseRow(
        val heading: Heading?,
        val title: String,
        val phaseBar: RoadmapPhaseBar?,
        val completedCount: Int,
        val totalCount: Int
    ) : RoadmapDisplayRow

    data class TaskRow(
        val taskBar: RoadmapTaskBar
    ) : RoadmapDisplayRow
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectRoadmapWorkspace(
    project: Project,
    projectHeadings: List<Heading>,
    primaryTasks: List<TaskItem>,
    headingTaskCounts: Map<String, Pair<Int, Int>>,
    currentEpochDay: Long,
    locale: Locale,
    onShiftHeadingDates: (heading: Heading, deltaDays: Long, fallbackRange: Pair<Long, Long>?) -> Unit,
    onShiftTaskDates: (task: TaskItem, deltaDays: Long) -> Unit,
    onUpdateHeadingStartDate: (headingId: String, startDate: Long?) -> Unit,
    onUpdateTaskDeadline: (task: TaskItem, deadline: Long?) -> Unit,
    onScheduleTaskToday: (task: TaskItem) -> Unit,
    onUpdateTaskStatus: (taskId: String, status: TaskStatus) -> Unit,
    onOpenTaskEditor: (taskId: String) -> Unit,
    onShowError: (String) -> Unit,
    selectedTaskIds: Set<String> = emptySet(),
    isSelectionModeActive: Boolean = selectedTaskIds.size > 1,
    onSelectSingleTask: ((taskId: String) -> Unit)? = null,
    onToggleTaskSelection: ((taskId: String) -> Unit)? = null,
    isFullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null,
    projectTitle: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 700
    val zone = ZoneId.systemDefault()

    val layout = remember(project, projectHeadings, primaryTasks, currentEpochDay, zone) {
        ProjectRoadmapEngine.buildRoadmapLayout(
            project = project,
            headings = projectHeadings,
            tasks = primaryTasks,
            todayEpochDay = currentEpochDay,
            zoneId = zone
        )
    }

    val zoomLevelsDp = listOf(32.dp, 44.dp, 62.dp)
    var zoomIndex by remember { mutableIntStateOf(1) }
    val dayWidthDp: Dp = zoomLevelsDp[zoomIndex.coerceIn(0, zoomLevelsDp.lastIndex)]
    val dayWidthPx = with(density) { dayWidthDp.toPx() }
    val labelColWidth: Dp = if (isWideScreen) 256.dp else 188.dp

    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    val jumpToToday: () -> Unit = {
        val targetOffsetDays = (layout.todayEpochDay - layout.startEpochDay - 2L).coerceAtLeast(0L)
        val targetPx = (targetOffsetDays * dayWidthPx).roundToInt()
        coroutineScope.launch {
            runCatching { horizontalScrollState.animateScrollTo(targetPx) }
        }
    }

    LaunchedEffect(project.id) {
        val targetOffsetDays = (layout.todayEpochDay - layout.startEpochDay - 2L).coerceAtLeast(0L)
        val targetPx = (targetOffsetDays * dayWidthPx).roundToInt()
        runCatching { horizontalScrollState.scrollTo(targetPx) }
    }

    var showPdfExportDialog by remember { mutableStateOf(false) }
    val exportChooserTitle = stringResource(R.string.v30_roadmap_export)
    val triggerPngExport: () -> Unit = {
        coroutineScope.launch {
            runCatching {
                RoadmapExporter.exportAndShareRoadmap(
                    context = context,
                    layout = layout,
                    headingCounts = headingTaskCounts,
                    locale = locale,
                    isDark = colors.isDark,
                    asPdf = false,
                    exportChooserTitle = exportChooserTitle
                )
            }.onFailure { err ->
                onShowError(err.localizedMessage ?: err.message ?: "Export failed")
            }
        }
    }

    val generalTitle = stringResource(R.string.v30_board_ungrouped_column)
    val displayRows: List<RoadmapDisplayRow> = remember(layout, headingTaskCounts, generalTitle) {
        val rows = mutableListOf<RoadmapDisplayRow>()
        for (lane in layout.lanes) {
            val h = lane.heading
            val counts = h?.let { headingTaskCounts[it.id] }
            val completed = counts?.first ?: 0
            val total = counts?.second ?: (lane.taskBars.size + lane.unscheduledTasks.size)
            rows += RoadmapDisplayRow.PhaseRow(
                heading = h,
                title = h?.title?.ifBlank { generalTitle } ?: generalTitle,
                phaseBar = lane.phaseBar,
                completedCount = completed,
                totalCount = total
            )
            for (tb in lane.taskBars) {
                rows += RoadmapDisplayRow.TaskRow(tb)
            }
        }
        rows
    }

    // Live drag delta-days preview state keyed by "phase_<id>" or "task_<id>"
    val livePreviewDeltaDays = remember { mutableStateMapOf<String, Long>() }

    val windowRangeLabel = remember(layout.startEpochDay, layout.endEpochDay, locale) {
        val fmt = DateTimeFormatter.ofPattern(if (locale.language == "de") "d. MMM" else "MMM d", locale)
        val yearFmt = DateTimeFormatter.ofPattern(if (locale.language == "de") "d. MMM yyyy" else "MMM d, yyyy", locale)
        val s = LocalDate.ofEpochDay(layout.startEpochDay)
        val e = LocalDate.ofEpochDay(layout.endEpochDay)
        "${s.format(fmt)} – ${e.format(yearFmt)}"
    }

    var unscheduledHeadingPickerTarget by remember { mutableStateOf<Heading?>(null) }
    var unscheduledTaskDeadlineTarget by remember { mutableStateOf<TaskItem?>(null) }

    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            showPdfExportDialog = false
            unscheduledHeadingPickerTarget = null
            unscheduledTaskDeadlineTarget = null
            livePreviewDeltaDays.clear()
        }
    }

    val renderToolbarLeftControls: @Composable () -> Unit = {
        if (isFullScreen && !projectTitle.isNullOrBlank()) {
            ThingsHoverTooltip(text = projectTitle) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.cardSurface,
                    border = BorderStroke(0.75.dp, colors.hairlineDivider)
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 460.dp)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        ProjectProgressRing(progress = project.progress, size = 16.dp)
                        Text(
                            text = projectTitle,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.tagPillBg
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(14.5.dp)
                )
                Text(
                    text = windowRangeLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primaryText,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Jump to Today Chip
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    jumpToToday()
                },
            shape = RoundedCornerShape(10.dp),
            color = colors.thingsBlueSoft,
            border = BorderStroke(0.75.dp, colors.thingsBlue.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.v30_roadmap_jump_today),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Zoom Toggle (- / +)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.tagPillBg,
            border = BorderStroke(0.75.dp, colors.hairlineDivider)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = { if (zoomIndex > 0) zoomIndex-- },
                    enabled = zoomIndex > 0,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = "-",
                        tint = if (zoomIndex > 0) colors.primaryText else colors.tertiaryText,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "${zoomIndex + 1}x",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.secondaryText,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
                IconButton(
                    onClick = { if (zoomIndex < zoomLevelsDp.lastIndex) zoomIndex++ },
                    enabled = zoomIndex < zoomLevelsDp.lastIndex,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "+",
                        tint = if (zoomIndex < zoomLevelsDp.lastIndex) colors.primaryText else colors.tertiaryText,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = if (isFullScreen) 10.dp else 4.dp)
    ) {
        // 1. Roadmap Toolbar (Single-row pure timeline controls in Full Screen; Export menu only outside Full Screen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isFullScreen) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    renderToolbarLeftControls()
                }
            } else {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    renderToolbarLeftControls()
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isFullScreen) {
                    RoadmapExportMenuPill(
                        onExportPdf = { showPdfExportDialog = true },
                        onExportPng = { triggerPngExport() }
                    )
                }

                if (onToggleFullScreen != null) {
                    val fsLabel = stringResource(
                        if (isFullScreen) R.string.v30_timeline_exit_fullscreen else R.string.v30_timeline_fullscreen
                    )
                    ThingsHoverTooltip(text = fsLabel) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleFullScreen()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isFullScreen) colors.thingsBlueSoft else colors.tagPillBg,
                            border = BorderStroke(
                                width = 0.75.dp,
                                color = if (isFullScreen) colors.thingsBlue.copy(alpha = 0.45f) else colors.hairlineDivider
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullScreen) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                                    contentDescription = fsLabel,
                                    tint = if (isFullScreen) colors.thingsBlue else colors.primaryText,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = fsLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isFullScreen) colors.thingsBlue else colors.primaryText,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Scrollable Gantt Card (+ Unscheduled Section outside Full Screen)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(verticalScrollState)
                .padding(bottom = if (isFullScreen) 12.dp else 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = colors.cardSurface,
                border = BorderStroke(0.85.dp, colors.hairlineDivider),
                shadowElevation = if (colors.isDark) 2.dp else 1.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Synchronized Day-Column Header Row
                    RoadmapDayHeaderStrip(
                        startEpochDay = layout.startEpochDay,
                        totalDays = layout.totalDays,
                        todayEpochDay = layout.todayEpochDay,
                        dayWidthDp = dayWidthDp,
                        labelColWidth = labelColWidth,
                        horizontalScrollState = horizontalScrollState,
                        locale = locale
                    )

                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.85.dp)

                    if (displayRows.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.v30_roadmap_unscheduled_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryText
                            )
                        }
                    } else {
                        RoadmapSynchronizedLanesBody(
                            layout = layout,
                            displayRows = displayRows,
                            dayWidthDp = dayWidthDp,
                            dayWidthPx = dayWidthPx,
                            labelColWidth = labelColWidth,
                            locale = locale,
                            horizontalScrollState = horizontalScrollState,
                            livePreviewDeltaDays = livePreviewDeltaDays,
                            selectedTaskIds = selectedTaskIds,
                            isSelectionModeActive = isSelectionModeActive,
                            onSelectSingleTask = onSelectSingleTask,
                            onToggleTaskSelection = onToggleTaskSelection,
                            onShiftHeadingDates = onShiftHeadingDates,
                            onShiftTaskDates = onShiftTaskDates,
                            onUpdateTaskStatus = onUpdateTaskStatus,
                            onOpenTaskEditor = onOpenTaskEditor,
                            readOnly = isFullScreen
                        )
                    }
                }
            }

            // 3. Unscheduled Section Card (hidden in pure Full Screen Timeline mode)
            if (!isFullScreen && (layout.unscheduledHeadings.isNotEmpty() || layout.unscheduledTasks.isNotEmpty())) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.sidebarBackground.copy(alpha = 0.72f),
                    border = BorderStroke(0.85.dp, colors.hairlineDivider)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.v30_roadmap_unscheduled_section),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.primaryText
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = colors.thingsBlueSoft
                            ) {
                                Text(
                                    text = (layout.unscheduledHeadings.size + layout.unscheduledTasks.size).toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.thingsBlue,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.v30_roadmap_unscheduled_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )

                        layout.unscheduledHeadings.forEach { heading ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp),
                                color = colors.cardSurface,
                                border = BorderStroke(0.75.dp, colors.hairlineDivider)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 3.dp)
                                                .width(3.5.dp)
                                                .height(16.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(colors.thingsBlue)
                                        )
                                        Text(
                                            text = heading.title.ifBlank { stringResource(R.string.entity_heading) },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.primaryText,
                                            maxLines = Int.MAX_VALUE,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ThingsTagPill(
                                            title = "★ ${stringResource(R.string.view_today)}",
                                            onClick = {
                                                val todayMs = ProjectRoadmapEngine.epochDayToStartOfDayMillis(currentEpochDay, zone)
                                                onUpdateHeadingStartDate(heading.id, todayMs)
                                            }
                                        )
                                        ThingsTagPill(
                                            title = "+ ${stringResource(R.string.v30_phase_set_start_date)}",
                                            onClick = { unscheduledHeadingPickerTarget = heading }
                                        )
                                    }
                                }
                            }
                        }

                        layout.unscheduledTasks.forEach { task ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(11.dp))
                                    .clickable { onOpenTaskEditor(task.id) },
                                shape = RoundedCornerShape(11.dp),
                                color = colors.cardSurface,
                                border = BorderStroke(
                                    width = if (task.isMilestone) 1.15.dp else 0.75.dp,
                                    color = if (task.isMilestone) colors.milestoneViolet.copy(alpha = 0.55f) else colors.hairlineDivider
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ThingsCheckbox(
                                            status = task.status,
                                            onStatusChange = { next -> onUpdateTaskStatus(task.id, next) },
                                            isMilestone = task.isMilestone,
                                            size = 18.dp
                                        )
                                        Text(
                                            text = task.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.primaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (task.isMilestone) {
                                            ThingsTagPill(
                                                title = "◆ ${stringResource(R.string.v30_milestone_label)}",
                                                containerColor = colors.milestoneVioletSoft,
                                                contentColor = colors.milestoneViolet,
                                                borderColor = colors.milestoneViolet.copy(alpha = 0.45f)
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ThingsTagPill(
                                            title = "★ ${stringResource(R.string.view_today)}",
                                            onClick = { onScheduleTaskToday(task) }
                                        )
                                        ThingsTagPill(
                                            title = "+ ${stringResource(R.string.action_deadline)}",
                                            onClick = { unscheduledTaskDeadlineTarget = task }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (!isFullScreen && showPdfExportDialog) {
        RoadmapPdfExportDialog(
            project = project,
            projectHeadings = projectHeadings,
            primaryTasks = primaryTasks,
            layout = layout,
            locale = locale,
            onDismiss = { showPdfExportDialog = false },
            onConfirmExport = { config ->
                showPdfExportDialog = false
                coroutineScope.launch {
                    runCatching {
                        RoadmapExporter.exportPdf(
                            context = context,
                            project = project,
                            layout = layout,
                            config = config,
                            headingCounts = headingTaskCounts,
                            locale = locale,
                            isDark = colors.isDark,
                            exportChooserTitle = exportChooserTitle
                        )
                    }.onFailure { err ->
                        onShowError(err.localizedMessage ?: err.message ?: "Export failed")
                    }
                }
            }
        )
    }

    if (!isFullScreen) {
        unscheduledHeadingPickerTarget?.let { targetHeading ->
            DeadlineDatePickerDialog(
                currentDeadline = targetHeading.startDate,
                locale = locale,
                onApplyDeadline = { picked -> onUpdateHeadingStartDate(targetHeading.id, picked) },
                onDismiss = { unscheduledHeadingPickerTarget = null },
                titleText = stringResource(R.string.v30_phase_set_start_date),
                clearButtonText = stringResource(R.string.v30_phase_clear_start_date)
            )
        }

        unscheduledTaskDeadlineTarget?.let { targetTask ->
            DeadlineDatePickerDialog(
                currentDeadline = targetTask.deadline,
                locale = locale,
                onApplyDeadline = { picked -> onUpdateTaskDeadline(targetTask, picked) },
                onDismiss = { unscheduledTaskDeadlineTarget = null }
            )
        }
    }
}

@Composable
private fun RoadmapDayHeaderStrip(
    startEpochDay: Long,
    totalDays: Int,
    todayEpochDay: Long,
    dayWidthDp: Dp,
    labelColWidth: Dp,
    horizontalScrollState: ScrollState,
    locale: Locale
) {
    val colors = ThingsTheme.colors
    val dowFmt = remember(locale) { DateTimeFormatter.ofPattern("EE", locale) }
    val monthFmt = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.sidebarBackground.copy(alpha = 0.6f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(labelColWidth)
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "${stringResource(R.string.entity_heading)} / ${stringResource(R.string.entity_todos)}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        VerticalDivider(color = colors.hairlineDivider, thickness = 0.85.dp)

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState)
        ) {
            for (i in 0 until totalDays) {
                val epochDay = startEpochDay + i
                val date = LocalDate.ofEpochDay(epochDay)
                val isToday = epochDay == todayEpochDay
                val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                val showMonthPrefix = i == 0 || date.dayOfMonth == 1

                Box(
                    modifier = Modifier
                        .width(dayWidthDp)
                        .fillMaxHeight()
                        .background(
                            when {
                                isToday -> colors.thingsBlueSoft
                                isWeekend -> colors.tagPillBg.copy(alpha = 0.45f)
                                else -> Color.Transparent
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (showMonthPrefix) {
                                date.format(monthFmt).uppercase(locale)
                            } else {
                                date.format(dowFmt).take(2)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = if (isToday || showMonthPrefix) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isToday) colors.thingsBlue else colors.tertiaryText,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isToday) colors.thingsBlue else Color.Transparent)
                                .padding(horizontal = if (isToday) 6.dp else 2.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold
                                ),
                                color = if (isToday) Color.White else colors.primaryText,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private val MIN_PHASE_ROW_HEIGHT = 56.dp
private val TASK_ROW_HEIGHT = 38.dp

private fun estimateWrappedLineCount(text: String, charsPerLine: Int): Int {
    if (text.isBlank()) return 1
    val safeLimit = charsPerLine.coerceAtLeast(10)
    var lines = 1
    var currentLen = 0
    for (word in text.trim().split(Regex("\\s+"))) {
        if (word.isEmpty()) continue
        if (currentLen == 0) {
            if (word.length <= safeLimit) {
                currentLen = word.length
            } else {
                lines += (word.length - 1) / safeLimit
                currentLen = ((word.length - 1) % safeLimit) + 1
            }
        } else if (currentLen + 1 + word.length <= safeLimit) {
            currentLen += 1 + word.length
        } else {
            lines++
            if (word.length <= safeLimit) {
                currentLen = word.length
            } else {
                lines += (word.length - 1) / safeLimit
                currentLen = ((word.length - 1) % safeLimit) + 1
            }
        }
    }
    return lines.coerceAtLeast(1)
}

private fun computePhaseRowHeightDp(title: String, labelColWidth: Dp): Dp {
    val approxCharsPerLine = ((labelColWidth.value - 38f) / 6.7f).toInt().coerceAtLeast(14)
    val wrappedLines = estimateWrappedLineCount(title, approxCharsPerLine)
    return (MIN_PHASE_ROW_HEIGHT.value.toInt() + (wrappedLines - 1).coerceAtLeast(0) * 16).dp
}

@Composable
private fun RoadmapSynchronizedLanesBody(
    layout: ProjectRoadmapLayout,
    displayRows: List<RoadmapDisplayRow>,
    dayWidthDp: Dp,
    dayWidthPx: Float,
    labelColWidth: Dp,
    locale: Locale,
    horizontalScrollState: ScrollState,
    livePreviewDeltaDays: MutableMap<String, Long>,
    selectedTaskIds: Set<String> = emptySet(),
    isSelectionModeActive: Boolean = false,
    onSelectSingleTask: ((taskId: String) -> Unit)? = null,
    onToggleTaskSelection: ((taskId: String) -> Unit)? = null,
    onShiftHeadingDates: (heading: Heading, deltaDays: Long, fallbackRange: Pair<Long, Long>?) -> Unit,
    onShiftTaskDates: (task: TaskItem, deltaDays: Long) -> Unit,
    onUpdateTaskStatus: (taskId: String, status: TaskStatus) -> Unit,
    onOpenTaskEditor: (taskId: String) -> Unit,
    readOnly: Boolean = false
) {
    val colors = ThingsTheme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val shortDateFmt = remember(locale) {
        DateTimeFormatter.ofPattern(if (locale.language == "de") "d. MMM" else "MMM d", locale)
    }

    val rowMetrics = remember(displayRows, labelColWidth) {
        val heights = ArrayList<Dp>(displayRows.size)
        val tops = ArrayList<Dp>(displayRows.size)
        var cursor = 0.dp
        for (row in displayRows) {
            tops += cursor
            val h = when (row) {
                is RoadmapDisplayRow.PhaseRow -> computePhaseRowHeightDp(row.title, labelColWidth)
                is RoadmapDisplayRow.TaskRow -> TASK_ROW_HEIGHT
            }
            heights += h
            cursor += h
        }
        Triple(heights, tops, cursor)
    }
    val rowHeightsDp = rowMetrics.first
    val rowTopsDp = rowMetrics.second
    val totalHeightDp = rowMetrics.third.coerceAtLeast(MIN_PHASE_ROW_HEIGHT)
    val totalTimelineWidthDp = dayWidthDp * layout.totalDays

    // Map taskId -> (rowTopPx, rowHeightPx, taskBar) synchronized with dynamic Phase row heights
    val taskRowGeometry = remember(displayRows, rowHeightsDp, density) {
        val map = HashMap<String, Triple<Float, Float, RoadmapTaskBar>>()
        var yPx = 0f
        displayRows.forEachIndexed { idx, row ->
            val hPx = with(density) { rowHeightsDp[idx].toPx() }
            if (row is RoadmapDisplayRow.TaskRow) {
                map[row.taskBar.taskId] = Triple(yPx, hPx, row.taskBar)
            }
            yPx += hPx
        }
        map
    }

    val shiftEarlierLabel = stringResource(R.string.v30_roadmap_shift_earlier)
    val shiftLaterLabel = stringResource(R.string.v30_roadmap_shift_later)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeightDp)
    ) {
        // Left Fixed Labels Column
        Column(
            modifier = Modifier
                .width(labelColWidth)
                .fillMaxHeight()
                .background(colors.sidebarBackground.copy(alpha = 0.36f))
        ) {
            displayRows.forEachIndexed { idx, row ->
                val rowHeightDp = rowHeightsDp[idx]
                when (row) {
                    is RoadmapDisplayRow.PhaseRow -> {
                        val phaseProgress = if (row.totalCount > 0) {
                            row.completedCount.toFloat() / row.totalCount.toFloat()
                        } else 0f
                        val phaseDateText = row.phaseBar?.let { pb ->
                            val s = LocalDate.ofEpochDay(pb.startEpochDay).format(shortDateFmt)
                            val e = LocalDate.ofEpochDay(pb.endEpochDay).format(shortDateFmt)
                            if (pb.startEpochDay == pb.endEpochDay) s else "$s – $e"
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeightDp)
                                .background(colors.thingsBlueSoft.copy(alpha = 0.35f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top Row: Full-width multi-line Phase Title (zero truncation!)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .width(3.5.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(colors.thingsBlue)
                                )
                                Text(
                                    text = row.title,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 15.5.sp
                                    ),
                                    color = colors.primaryText,
                                    maxLines = Int.MAX_VALUE,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Sub-Row Underneath: Progress Ring + Compact Start/End Date Chip + Shift Controls
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 9.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (row.totalCount > 0) {
                                        ProjectProgressRing(progress = phaseProgress, size = 12.5.dp)
                                    }
                                    if (phaseDateText != null) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(colors.cardSurface.copy(alpha = 0.78f))
                                                .padding(horizontal = 6.5.dp, vertical = 1.5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CalendarToday,
                                                contentDescription = null,
                                                tint = colors.thingsBlue,
                                                modifier = Modifier.size(9.5.dp)
                                            )
                                            Text(
                                                text = phaseDateText,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = colors.secondaryText,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                if (!readOnly && row.heading != null && row.phaseBar != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                        ThingsHoverTooltip(text = shiftEarlierLabel) {
                                            IconButton(
                                                onClick = {
                                                    onShiftHeadingDates(
                                                        row.heading,
                                                        -1L,
                                                        row.phaseBar.startEpochDay to row.phaseBar.endEpochDay
                                                    )
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ChevronLeft,
                                                    contentDescription = shiftEarlierLabel,
                                                    tint = colors.thingsBlue,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        ThingsHoverTooltip(text = shiftLaterLabel) {
                                            IconButton(
                                                onClick = {
                                                    onShiftHeadingDates(
                                                        row.heading,
                                                        1L,
                                                        row.phaseBar.startEpochDay to row.phaseBar.endEpochDay
                                                    )
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ChevronRight,
                                                    contentDescription = shiftLaterLabel,
                                                    tint = colors.thingsBlue,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is RoadmapDisplayRow.TaskRow -> {
                        val task = row.taskBar.task
                        val isRowSelected = task.id in selectedTaskIds
                        var lastLabelTapMs by remember(task.id) { mutableLongStateOf(0L) }
                        var labelCtrlHeld by remember(task.id) { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TASK_ROW_HEIGHT)
                                .background(
                                    when {
                                        isRowSelected -> colors.thingsBlueSoft.copy(alpha = 0.55f)
                                        task.isMilestone -> colors.milestoneVioletSoft.copy(alpha = 0.30f)
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (!readOnly) {
                                        Modifier
                                            .pointerInput(task.id, "label_mod") {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                                        if (ev.type == PointerEventType.Press) {
                                                            labelCtrlHeld = ev.keyboardModifiers.isCtrlPressed || ev.keyboardModifiers.isMetaPressed
                                                        }
                                                    }
                                                }
                                            }
                                            .clickable {
                                                val now = SystemClock.uptimeMillis()
                                                if (labelCtrlHeld && onToggleTaskSelection != null) {
                                                    lastLabelTapMs = 0L
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onToggleTaskSelection(task.id)
                                                } else if (lastLabelTapMs > 0L && (now - lastLabelTapMs) <= 380L) {
                                                    lastLabelTapMs = 0L
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onOpenTaskEditor(task.id)
                                                } else {
                                                    lastLabelTapMs = now
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    if (isSelectionModeActive && onToggleTaskSelection != null) {
                                                        onToggleTaskSelection(task.id)
                                                    } else if (onSelectSingleTask != null) {
                                                        onSelectSingleTask(task.id)
                                                    } else {
                                                        onOpenTaskEditor(task.id)
                                                    }
                                                }
                                            }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(start = 12.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ThingsCheckbox(
                                    status = task.status,
                                    onStatusChange = { next -> if (!readOnly) onUpdateTaskStatus(task.id, next) },
                                    isMilestone = task.isMilestone,
                                    size = 16.dp,
                                    showHoverTooltip = !readOnly,
                                    modifier = if (readOnly) {
                                        Modifier.pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                                    ev.changes.forEach { change ->
                                                        if (change.pressed != change.previousPressed) {
                                                            change.consume()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                val baseTitle = task.title.ifBlank { stringResource(R.string.placeholder_todo_title) }
                                Text(
                                    text = if (task.isMilestone) "◆ $baseTitle" else baseTitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (task.isMilestone) FontWeight.SemiBold else FontWeight.Medium,
                                        textDecoration = if (task.status != TaskStatus.OPEN) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = when {
                                        row.taskBar.isActivelyBlocked -> colors.secondaryText
                                        task.isMilestone && task.status == TaskStatus.OPEN -> colors.milestoneViolet
                                        else -> colors.primaryText
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!readOnly) {
                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                    ThingsHoverTooltip(text = shiftEarlierLabel) {
                                        IconButton(
                                            onClick = { onShiftTaskDates(task, -1L) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ChevronLeft,
                                                contentDescription = shiftEarlierLabel,
                                                tint = if (task.isMilestone) colors.milestoneViolet else colors.secondaryText,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    ThingsHoverTooltip(text = shiftLaterLabel) {
                                        IconButton(
                                            onClick = { onShiftTaskDates(task, 1L) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ChevronRight,
                                                contentDescription = shiftLaterLabel,
                                                tint = if (task.isMilestone) colors.milestoneViolet else colors.secondaryText,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider(color = colors.hairlineDivider, thickness = 0.85.dp)

        // Right Synchronized Horizontal Timeline Grid + Bars + Dependency Bézier Curves
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState)
        ) {
            Box(
                modifier = Modifier
                    .width(totalTimelineWidthDp)
                    .height(totalHeightDp)
            ) {
                // Grid Background + Today Accent Line
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val h = size.height
                    for (i in 0 until layout.totalDays) {
                        val day = layout.startEpochDay + i
                        val x = i * dayWidthPx
                        val dow = LocalDate.ofEpochDay(day).dayOfWeek
                        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                            drawRect(
                                color = colors.tagPillBg.copy(alpha = 0.35f),
                                topLeft = Offset(x, 0f),
                                size = androidx.compose.ui.geometry.Size(dayWidthPx, h)
                            )
                        }
                        drawLine(
                            color = colors.hairlineDivider.copy(alpha = 0.55f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                    }

                    // Horizontal row separators synchronized with rowHeightsDp
                    var rowY = 0f
                    rowHeightsDp.forEach { rowH ->
                        rowY += rowH.toPx()
                        drawLine(
                            color = colors.hairlineDivider.copy(alpha = 0.45f),
                            start = Offset(0f, rowY),
                            end = Offset(size.width, rowY),
                            strokeWidth = 1f
                        )
                    }

                    // Vertical Today Accent Line
                    if (layout.todayEpochDay in layout.startEpochDay..layout.endEpochDay) {
                        val todayX = (layout.todayEpochDay - layout.startEpochDay) * dayWidthPx + dayWidthPx * 0.5f
                        drawLine(
                            color = colors.thingsBlue.copy(alpha = 0.72f),
                            start = Offset(todayX, 0f),
                            end = Offset(todayX, h),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Phase Bars & Task Bars with Horizontal Drag-to-Reschedule (read-only in Full Screen)
                displayRows.forEachIndexed { idx, row ->
                    val topDp = rowTopsDp[idx]
                    val rowHeightDp = rowHeightsDp[idx]
                    when (row) {
                        is RoadmapDisplayRow.PhaseRow -> {
                            val pb = row.phaseBar
                            val heading = row.heading
                            if (pb != null && heading != null) {
                                RoadmapDraggablePhaseBar(
                                    phaseBar = pb,
                                    startEpochDayWindow = layout.startEpochDay,
                                    endEpochDayWindow = layout.endEpochDay,
                                    topDp = topDp,
                                    rowHeightDp = rowHeightDp,
                                    totalTimelineWidthDp = totalTimelineWidthDp,
                                    dayWidthDp = dayWidthDp,
                                    dayWidthPx = dayWidthPx,
                                    previewDelta = livePreviewDeltaDays["phase_${heading.id}"] ?: 0L,
                                    onPreviewDeltaChanged = { delta ->
                                        if (delta == 0L) livePreviewDeltaDays.remove("phase_${heading.id}")
                                        else livePreviewDeltaDays["phase_${heading.id}"] = delta
                                    },
                                    onCommitShift = { delta ->
                                        onShiftHeadingDates(heading, delta, pb.startEpochDay to pb.endEpochDay)
                                    },
                                    readOnly = readOnly
                                )
                            }
                        }

                        is RoadmapDisplayRow.TaskRow -> {
                            val tb = row.taskBar
                            RoadmapDraggableTaskBar(
                                taskBar = tb,
                                startEpochDayWindow = layout.startEpochDay,
                                endEpochDayWindow = layout.endEpochDay,
                                todayEpochDay = layout.todayEpochDay,
                                topDp = topDp,
                                totalTimelineWidthDp = totalTimelineWidthDp,
                                dayWidthDp = dayWidthDp,
                                dayWidthPx = dayWidthPx,
                                previewDelta = livePreviewDeltaDays["task_${tb.taskId}"] ?: 0L,
                                isSelected = tb.taskId in selectedTaskIds,
                                isSelectionModeActive = isSelectionModeActive,
                                onPreviewDeltaChanged = { delta ->
                                    if (delta == 0L) livePreviewDeltaDays.remove("task_${tb.taskId}")
                                    else livePreviewDeltaDays["task_${tb.taskId}"] = delta
                                },
                                onCommitShift = { delta ->
                                    onShiftTaskDates(tb.task, delta)
                                },
                                onSelectSingleTask = onSelectSingleTask?.let { fn -> { fn(tb.taskId) } },
                                onToggleTaskSelection = onToggleTaskSelection?.let { fn -> { fn(tb.taskId) } },
                                onClickTask = { onOpenTaskEditor(tb.taskId) },
                                readOnly = readOnly
                            )
                        }
                    }
                }

                // Dependency Connector Overlay (Smooth Cubic-Bézier S-Curves + Arrowheads)
                if (layout.dependencyEdges.isNotEmpty()) {
                    val activeEdgeColor = colors.somedayWarmAmber
                    val resolvedEdgeColor = colors.thingsBlue.copy(alpha = 0.55f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val hPad = 3.dp.toPx()
                        val diamondVertexOffset = 12.5.dp.toPx()
                        val strokeW = 1.85.dp.toPx()
                        val arrowSize = 6.dp.toPx()

                        for (edge in layout.dependencyEdges) {
                            val fromInfo = taskRowGeometry[edge.fromTaskId] ?: continue
                            val toInfo = taskRowGeometry[edge.toTaskId] ?: continue

                            val fromDelta = livePreviewDeltaDays["task_${edge.fromTaskId}"] ?: 0L
                            val toDelta = livePreviewDeltaDays["task_${edge.toTaskId}"] ?: 0L

                            val clampedFromEnd = (fromInfo.third.endEpochDay + fromDelta)
                                .coerceIn(layout.startEpochDay, layout.endEpochDay)
                            val clampedToStart = (toInfo.third.startEpochDay + toDelta)
                                .coerceIn(layout.startEpochDay, layout.endEpochDay)

                            val startX = if (fromInfo.third.isMilestone) {
                                (clampedFromEnd - layout.startEpochDay + 0.5f) * dayWidthPx + diamondVertexOffset
                            } else {
                                (clampedFromEnd - layout.startEpochDay + 1L) * dayWidthPx - hPad
                            }
                            val startY = fromInfo.first + fromInfo.second * 0.5f
                            val endX = if (toInfo.third.isMilestone) {
                                (clampedToStart - layout.startEpochDay + 0.5f) * dayWidthPx - diamondVertexOffset
                            } else {
                                (clampedToStart - layout.startEpochDay) * dayWidthPx + hPad
                            }
                            val endY = toInfo.first + toInfo.second * 0.5f

                            val dx = kotlin.math.abs(endX - startX)
                            val ctrlOffset = maxOf(dx * 0.48f, 18.dp.toPx())
                            val c1X = startX + ctrlOffset
                            val c1Y = startY
                            val c2X = endX - ctrlOffset
                            val c2Y = endY

                            val curveColor = if (edge.isActivelyBlocking) activeEdgeColor else resolvedEdgeColor
                            val path = Path().apply {
                                moveTo(startX, startY)
                                cubicTo(c1X, c1Y, c2X, c2Y, endX, endY)
                            }
                            drawPath(
                                path = path,
                                color = curveColor,
                                style = Stroke(
                                    width = strokeW,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            val angle = atan2(endY - c2Y, endX - c2X)
                            val arrowPath = Path().apply {
                                moveTo(endX, endY)
                                lineTo(
                                    endX - arrowSize * cos(angle - 0.52f),
                                    endY - arrowSize * sin(angle - 0.52f)
                                )
                                lineTo(
                                    endX - arrowSize * cos(angle + 0.52f),
                                    endY - arrowSize * sin(angle + 0.52f)
                                )
                                close()
                            }
                            drawPath(path = arrowPath, color = curveColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoadmapDraggablePhaseBar(
    phaseBar: RoadmapPhaseBar,
    startEpochDayWindow: Long,
    endEpochDayWindow: Long,
    topDp: Dp,
    rowHeightDp: Dp,
    totalTimelineWidthDp: Dp,
    dayWidthDp: Dp,
    dayWidthPx: Float,
    previewDelta: Long,
    onPreviewDeltaChanged: (Long) -> Unit,
    onCommitShift: (Long) -> Unit,
    readOnly: Boolean = false
) {
    val colors = ThingsTheme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentDayWidthPx by rememberUpdatedState(dayWidthPx)
    val currentOnPreview by rememberUpdatedState(onPreviewDeltaChanged)
    val currentOnCommit by rememberUpdatedState(onCommitShift)

    var isArmedForDrag by remember(phaseBar.headingId) { mutableStateOf(false) }

    val baseStartDay = phaseBar.startEpochDay
    val baseEndDay = phaseBar.endEpochDay
    val rawStartDay = baseStartDay + previewDelta
    val rawEndDay = baseEndDay + previewDelta
    if (rawEndDay < startEpochDayWindow || rawStartDay > endEpochDayWindow) return

    val baseVisibleStartDay = baseStartDay.coerceIn(startEpochDayWindow, endEpochDayWindow)
    val baseVisibleEndDay = baseEndDay.coerceIn(baseVisibleStartDay, endEpochDayWindow)
    val visibleStartDay = rawStartDay.coerceIn(startEpochDayWindow, endEpochDayWindow)
    val visibleEndDay = rawEndDay.coerceIn(visibleStartDay, endEpochDayWindow)
    val visibleSpanDays = (visibleEndDay - visibleStartDay + 1L).coerceAtLeast(1L).toInt()

    val baseXOffsetDp = dayWidthDp * (baseVisibleStartDay - startEpochDayWindow).toInt()
    val xOffsetDp = dayWidthDp * (visibleStartDay - startEpochDayWindow).toInt()
    val barWidthDp = (dayWidthDp * visibleSpanDays).coerceAtLeast(dayWidthDp)
    val barHeightDp = 28.dp
    val barTopDp = topDp + (rowHeightDp - barHeightDp) / 2

    val labelText = remember(phaseBar.title, previewDelta) {
        buildString {
            append(phaseBar.title)
            if (previewDelta != 0L) {
                append(if (previewDelta > 0) " (+${previewDelta}d)" else " (${previewDelta}d)")
            }
        }
    }
    val measuredLabelPx = remember(labelText, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = with(density) { 11.sp.toPx() }
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }.measureText(labelText)
    }
    val barWidthPx = with(density) { barWidthDp.toPx() }
    val xOffsetPx = with(density) { xOffsetDp.toPx() }
    val baseXOffsetPx = with(density) { baseXOffsetDp.toPx() }
    val totalTimelineWidthPx = with(density) { totalTimelineWidthDp.toPx() }
    val innerPadPx = with(density) { 26.dp.toPx() }
    val fitsInsideBar = barWidthPx >= measuredLabelPx + innerPadPx

    val gradientBrush = Brush.horizontalGradient(
        colors = if (phaseBar.hasExplicitDates) {
            listOf(GlacierGradientStart, GlacierGradientEnd)
        } else {
            listOf(GlacierGradientStart.copy(alpha = 0.78f), GlacierGradientEnd.copy(alpha = 0.78f))
        }
    )

    Box(
        modifier = Modifier
            .offset(x = baseXOffsetDp, y = barTopDp)
            .graphicsLayer {
                translationX = xOffsetPx - baseXOffsetPx
                val scale = if (isArmedForDrag) 1.02f else 1f
                scaleX = scale
                scaleY = scale
            }
            .width(barWidthDp)
            .height(barHeightDp)
            .padding(horizontal = 3.dp)
            .shadow(if (isArmedForDrag || previewDelta != 0L) 6.dp else 2.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(gradientBrush)
            .then(
                if (!readOnly) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .pointerInput(phaseBar.headingId) {
                            var rawDragPx = 0f
                            var lastDelta = 0L
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    rawDragPx = 0f
                                    lastDelta = 0L
                                    isArmedForDrag = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    rawDragPx += dragAmount.x
                                    val snapped = if (currentDayWidthPx > 1f) {
                                        (rawDragPx / currentDayWidthPx).roundToInt().toLong()
                                    } else 0L
                                    if (snapped != lastDelta) {
                                        lastDelta = snapped
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentOnPreview(snapped)
                                    }
                                },
                                onDragEnd = {
                                    val finalDelta = lastDelta
                                    isArmedForDrag = false
                                    currentOnPreview(0L)
                                    if (finalDelta != 0L) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentOnCommit(finalDelta)
                                    }
                                },
                                onDragCancel = {
                                    isArmedForDrag = false
                                    currentOnPreview(0L)
                                }
                            )
                        }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (fitsInsideBar) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                softWrap = false,
                maxLines = 1
            )
        }
    }

    if (!fitsInsideBar && labelText.isNotBlank()) {
        val spaceRightPx = totalTimelineWidthPx - (xOffsetPx + barWidthPx)
        val spaceLeftPx = xOffsetPx
        val placeOnRight = spaceRightPx >= measuredLabelPx + with(density) { 12.dp.toPx() } ||
            spaceRightPx >= spaceLeftPx

        if (placeOnRight) {
            Box(
                modifier = Modifier
                    .offset(x = xOffsetDp + barWidthDp + 4.dp, y = barTopDp)
                    .height(barHeightDp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.thingsBlue,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.cardSurface.copy(alpha = 0.88f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = barTopDp)
                    .width((xOffsetDp - 4.dp).coerceAtLeast(0.dp))
                    .height(barHeightDp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.thingsBlue,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.cardSurface.copy(alpha = 0.88f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun RoadmapDraggableTaskBar(
    taskBar: RoadmapTaskBar,
    startEpochDayWindow: Long,
    endEpochDayWindow: Long,
    todayEpochDay: Long,
    topDp: Dp,
    totalTimelineWidthDp: Dp,
    dayWidthDp: Dp,
    dayWidthPx: Float,
    previewDelta: Long,
    isSelected: Boolean = false,
    isSelectionModeActive: Boolean = false,
    onPreviewDeltaChanged: (Long) -> Unit,
    onCommitShift: (Long) -> Unit,
    onSelectSingleTask: (() -> Unit)? = null,
    onToggleTaskSelection: (() -> Unit)? = null,
    onClickTask: () -> Unit,
    readOnly: Boolean = false
) {
    val colors = ThingsTheme.colors
    val density = androidx.compose.ui.platform.LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentDayWidthPx by rememberUpdatedState(dayWidthPx)
    val currentOnPreview by rememberUpdatedState(onPreviewDeltaChanged)
    val currentOnCommit by rememberUpdatedState(onCommitShift)
    var isArmedForDrag by remember(taskBar.taskId) { mutableStateOf(false) }
    var lastTapUptimeMs by remember(taskBar.taskId) { mutableLongStateOf(0L) }
    var modifierHeldCtrlOrMeta by remember(taskBar.taskId) { mutableStateOf(false) }

    val baseStartDay = taskBar.startEpochDay
    val baseEndDay = taskBar.endEpochDay
    val effectiveStartDay = baseStartDay + previewDelta
    val effectiveEndDay = baseEndDay + previewDelta
    if (effectiveEndDay < startEpochDayWindow || effectiveStartDay > endEpochDayWindow) return

    val baseVisibleStartDay = baseStartDay.coerceIn(startEpochDayWindow, endEpochDayWindow)
    val visibleStartDay = effectiveStartDay.coerceIn(startEpochDayWindow, endEpochDayWindow)
    val visibleEndDay = effectiveEndDay.coerceIn(visibleStartDay, endEpochDayWindow)
    val spanDays = (visibleEndDay - visibleStartDay + 1L).coerceAtLeast(1L).toInt()
    val baseXOffsetDp = dayWidthDp * (baseVisibleStartDay - startEpochDayWindow).toInt()
    val xOffsetDp = dayWidthDp * (visibleStartDay - startEpochDayWindow).toInt()
    val baseXOffsetPx = with(density) { baseXOffsetDp.toPx() }
    val xOffsetPx = with(density) { xOffsetDp.toPx() }
    val barWidthDp = (dayWidthDp * spanDays).coerceAtLeast(dayWidthDp)

    val isOverdue = taskBar.task.status == TaskStatus.OPEN &&
        taskBar.task.deadline != null &&
        effectiveEndDay < todayEpochDay

    val handleBarTap: () -> Unit = {
        val now = SystemClock.uptimeMillis()
        if (modifierHeldCtrlOrMeta && onToggleTaskSelection != null) {
            lastTapUptimeMs = 0L
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggleTaskSelection()
        } else if (lastTapUptimeMs > 0L && (now - lastTapUptimeMs) <= 380L) {
            lastTapUptimeMs = 0L
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClickTask()
        } else {
            lastTapUptimeMs = now
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (isSelectionModeActive && onToggleTaskSelection != null) {
                onToggleTaskSelection()
            } else if (onSelectSingleTask != null) {
                onSelectSingleTask()
            } else {
                onClickTask()
            }
        }
    }

    val interactiveGestureModifier: Modifier = if (!readOnly) {
        Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(taskBar.taskId, "mod_track") {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.type == PointerEventType.Press) {
                            modifierHeldCtrlOrMeta = ev.keyboardModifiers.isCtrlPressed || ev.keyboardModifiers.isMetaPressed
                        }
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = handleBarTap
            )
            .pointerInput(taskBar.taskId) {
                var rawDragPx = 0f
                var lastDelta = 0L
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        rawDragPx = 0f
                        lastDelta = 0L
                        isArmedForDrag = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        rawDragPx += dragAmount.x
                        val snapped = if (currentDayWidthPx > 1f) {
                            (rawDragPx / currentDayWidthPx).roundToInt().toLong()
                        } else 0L
                        if (snapped != lastDelta) {
                            lastDelta = snapped
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnPreview(snapped)
                        }
                    },
                    onDragEnd = {
                        val finalDelta = lastDelta
                        isArmedForDrag = false
                        currentOnPreview(0L)
                        if (finalDelta != 0L) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnCommit(finalDelta)
                        }
                    },
                    onDragCancel = {
                        isArmedForDrag = false
                        currentOnPreview(0L)
                    }
                )
            }
    } else {
        Modifier
    }

    val defaultTodoLabel = stringResource(R.string.entity_todo)

    if (taskBar.isMilestone) {
        val diamondFill = when {
            taskBar.task.status == TaskStatus.COMPLETED -> Color(0xFF10B981)
            taskBar.task.status == TaskStatus.CANCELED -> colors.secondaryText
            isOverdue -> colors.deadlineUrgentRed
            else -> colors.milestoneViolet
        }
        val calloutText = remember(taskBar.title, taskBar.isActivelyBlocked, previewDelta, defaultTodoLabel) {
            buildString {
                append("◆ ")
                if (taskBar.isActivelyBlocked) append("⏳ ")
                append(taskBar.title.ifBlank { defaultTodoLabel })
                if (previewDelta != 0L) {
                    append(if (previewDelta > 0) " (+${previewDelta}d)" else " (${previewDelta}d)")
                }
            }
        }
        val measuredCalloutPx = remember(calloutText, density) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = with(density) { 10.5.sp.toPx() }
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }.measureText(calloutText)
        }
        val totalTimelineWidthPx = with(density) { totalTimelineWidthDp.toPx() }
        val centerColPx = xOffsetPx + currentDayWidthPx * 0.5f
        val spaceRightPx = totalTimelineWidthPx - (centerColPx + with(density) { 16.dp.toPx() })
        val spaceLeftPx = centerColPx - with(density) { 16.dp.toPx() }
        val placeLabelRight = spaceRightPx >= measuredCalloutPx + with(density) { 16.dp.toPx() } ||
            spaceRightPx >= spaceLeftPx

        // 1. Centered 45°-rotated 18.dp Milestone Violet Diamond with white inner rim & soft violet glow
        Box(
            modifier = Modifier
                .offset(x = baseXOffsetDp, y = topDp)
                .graphicsLayer {
                    translationX = xOffsetPx - baseXOffsetPx
                    val scale = if (isArmedForDrag) 1.12f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .width(dayWidthDp)
                .height(TASK_ROW_HEIGHT)
                .then(interactiveGestureModifier),
            contentAlignment = Alignment.Center
        ) {
            // Soft Violet Glow Halo (+ Sky-Blue selection ring when selected)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        colors.milestoneViolet.copy(
                            alpha = if (isArmedForDrag || isSelected) 0.28f else 0.17f
                        )
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(1.4.dp, colors.thingsBlue, CircleShape)
                        } else {
                            Modifier
                        }
                    )
            )
            // Bold 18.dp 45°-Rotated Milestone Violet Diamond with Crisp White Inner Rim
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = 45f }
                    .shadow(
                        elevation = if (isArmedForDrag || previewDelta != 0L || isSelected) 5.dp else 2.5.dp,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .background(diamondFill)
                    .border(
                        width = if (isSelected) 1.5.dp else 0.9.dp,
                        color = if (isSelected) colors.thingsBlue else diamondFill,
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // White Inner Rim inside the 45° Diamond
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .border(
                            width = 1.35.dp,
                            color = Color.White.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(2.5.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (taskBar.task.status == TaskStatus.COMPLETED) {
                        Canvas(
                            modifier = Modifier
                                .size(8.dp)
                                .graphicsLayer { rotationZ = -45f }
                        ) {
                            val w = size.width
                            val h = size.height
                            val checkPath = Path().apply {
                                moveTo(w * 0.16f, h * 0.54f)
                                lineTo(w * 0.42f, h * 0.80f)
                                lineTo(w * 0.86f, h * 0.22f)
                            }
                            drawPath(
                                path = checkPath,
                                color = Color.White,
                                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
            }
        }

        // 2. Floating Non-Clipped Milestone Callout Label ("◆ ${task.title}") immediately beside the diamond
        if (placeLabelRight) {
            Box(
                modifier = Modifier
                    .offset(x = baseXOffsetDp + dayWidthDp / 2 + 14.dp, y = topDp + 6.dp)
                    .graphicsLayer {
                        translationX = xOffsetPx - baseXOffsetPx
                    }
                    .height(TASK_ROW_HEIGHT - 12.dp)
                    .then(interactiveGestureModifier),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = calloutText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (taskBar.task.status != TaskStatus.OPEN) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = when {
                        taskBar.task.status != TaskStatus.OPEN -> colors.secondaryText
                        isOverdue -> colors.deadlineUrgentRed
                        else -> colors.milestoneViolet
                    },
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (isSelected) colors.thingsBlueSoft else colors.milestoneVioletSoft.copy(alpha = 0.94f)
                        )
                        .border(
                            width = if (isSelected) 1.3.dp else 0.85.dp,
                            color = if (isSelected) colors.thingsBlue else colors.milestoneViolet.copy(alpha = 0.48f),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        } else {
            val leftAvailWidth = (xOffsetDp + dayWidthDp / 2 - 14.dp).coerceAtLeast(0.dp)
            if (leftAvailWidth > 20.dp) {
                Box(
                    modifier = Modifier
                        .offset(x = 0.dp, y = topDp + 6.dp)
                        .width(leftAvailWidth)
                        .height(TASK_ROW_HEIGHT - 12.dp)
                        .then(interactiveGestureModifier),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = calloutText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (taskBar.task.status != TaskStatus.OPEN) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = when {
                            taskBar.task.status != TaskStatus.OPEN -> colors.secondaryText
                            isOverdue -> colors.deadlineUrgentRed
                            else -> colors.milestoneViolet
                        },
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                if (isSelected) colors.thingsBlueSoft else colors.milestoneVioletSoft.copy(alpha = 0.94f)
                            )
                            .border(
                                width = if (isSelected) 1.3.dp else 0.85.dp,
                                color = if (isSelected) colors.thingsBlue else colors.milestoneViolet.copy(alpha = 0.48f),
                                shape = RoundedCornerShape(7.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
        return
    }

    val barFill = when {
        isSelected -> colors.thingsBlueSoft
        taskBar.task.status != TaskStatus.OPEN -> colors.tagPillBg
        isOverdue -> colors.deadlineSoftBg
        taskBar.isActivelyBlocked -> colors.somedayWarmAmber.copy(alpha = 0.16f)
        else -> colors.thingsBlueSoft
    }
    val barBorder = when {
        isArmedForDrag || isSelected -> colors.thingsBlue
        taskBar.task.status != TaskStatus.OPEN -> colors.hairlineDivider
        isOverdue -> colors.deadlineUrgentRed.copy(alpha = 0.65f)
        taskBar.isActivelyBlocked -> colors.somedayWarmAmber.copy(alpha = 0.72f)
        else -> colors.thingsBlue.copy(alpha = 0.58f)
    }
    val barTextColor = when {
        taskBar.task.status != TaskStatus.OPEN -> colors.secondaryText
        isOverdue -> colors.deadlineUrgentRed
        taskBar.isActivelyBlocked -> colors.somedayWarmAmber
        else -> colors.primaryText
    }

    Box(
        modifier = Modifier
            .offset(x = baseXOffsetDp, y = topDp + 6.dp)
            .graphicsLayer {
                translationX = xOffsetPx - baseXOffsetPx
                val scale = if (isArmedForDrag) 1.02f else 1f
                scaleX = scale
                scaleY = scale
            }
            .width(barWidthDp)
            .height(TASK_ROW_HEIGHT - 12.dp)
            .padding(horizontal = 3.dp)
            .shadow(if (isArmedForDrag || previewDelta != 0L || isSelected) 5.dp else 1.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(barFill)
            .border(if (isArmedForDrag || isSelected) 1.6.dp else 1.1.dp, barBorder, RoundedCornerShape(8.dp))
            .then(interactiveGestureModifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = buildString {
                if (taskBar.isActivelyBlocked) append("⏳ ")
                append(taskBar.title.ifBlank { defaultTodoLabel })
                if (previewDelta != 0L) {
                    append(if (previewDelta > 0) " (+${previewDelta}d)" else " (${previewDelta}d)")
                }
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = barTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =============================================================================
// Interactive PDF Export Configuration Dialog (`RoadmapPdfExportDialog`)
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoadmapPdfExportDialog(
    project: Project,
    projectHeadings: List<Heading>,
    primaryTasks: List<TaskItem>,
    layout: ProjectRoadmapLayout,
    locale: Locale,
    onDismiss: () -> Unit,
    onConfirmExport: (RoadmapPdfExportConfig) -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val generalTitle = stringResource(R.string.v30_board_ungrouped_column)
    val fallbackHeadingTitle = stringResource(R.string.entity_heading)

    val phaseOptions = remember(projectHeadings, primaryTasks, layout, generalTitle, fallbackHeadingTitle) {
        val activeHeadings = projectHeadings
            .filter { !it.isDeleted && !it.isArchived }
            .sortedWith(compareBy<Heading> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
        val validIds = activeHeadings.mapTo(HashSet()) { it.id }
        val hasGeneral = layout.lanes.any { it.laneKey == "general" } ||
            primaryTasks.any {
                !it.isDeleted && it.status != TaskStatus.CANCELED &&
                    (it.headingId == null || it.headingId !in validIds)
            } ||
            activeHeadings.isEmpty()

        buildList {
            if (hasGeneral) {
                add("general" to generalTitle)
            }
            for (h in activeHeadings) {
                add(h.id to h.title.ifBlank { fallbackHeadingTitle })
            }
        }
    }

    var selectedKeys by remember(phaseOptions) {
        mutableStateOf(phaseOptions.mapTo(linkedSetOf()) { it.first })
    }
    var timelinePreset by remember {
        mutableStateOf(RoadmapPdfTimelinePreset.FULL_PROJECT)
    }
    var customStartDay by remember(layout.startEpochDay) {
        mutableLongStateOf(layout.startEpochDay)
    }
    var customEndDay by remember(layout.endEpochDay) {
        mutableLongStateOf(layout.endEpochDay)
    }
    var orientation by remember {
        mutableStateOf(PdfPageOrientation.LANDSCAPE)
    }

    var showCustomStartPicker by remember { mutableStateOf(false) }
    var showCustomEndPicker by remember { mutableStateOf(false) }

    val resolvedRange = remember(timelinePreset, customStartDay, customEndDay, layout) {
        when (timelinePreset) {
            RoadmapPdfTimelinePreset.FULL_PROJECT -> layout.startEpochDay to layout.endEpochDay
            RoadmapPdfTimelinePreset.FROM_TODAY -> {
                val end = maxOf(layout.todayEpochDay, layout.endEpochDay)
                layout.todayEpochDay to end
            }
            RoadmapPdfTimelinePreset.NEXT_30_DAYS -> {
                layout.todayEpochDay to (layout.todayEpochDay + 30L)
            }
            RoadmapPdfTimelinePreset.CUSTOM -> {
                minOf(customStartDay, customEndDay) to maxOf(customStartDay, customEndDay)
            }
        }
    }

    val dateFmt = remember(locale) {
        DateTimeFormatter.ofPattern(if (locale.language == "de") "d. MMM yyyy" else "MMM d, yyyy", locale)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ThingsTooltipHostBox {
            Surface(
                modifier = Modifier
                    .widthIn(max = 468.dp)
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 680.dp)
                    .clip(RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = colors.cardElevatedSurface,
                border = BorderStroke(1.dp, colors.hairlineDivider),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(colors.thingsBlueSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = colors.thingsBlue,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.v30_pdf_export_dialog_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primaryText
                                )
                                Text(
                                    text = project.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.8.dp)

                    // Scrollable Body
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Project Phases Multi-Select
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val allSelected = phaseOptions.isNotEmpty() && selectedKeys.size == phaseOptions.size
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.v30_pdf_phases_label),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primaryText
                                )
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedKeys = if (allSelected) {
                                                linkedSetOf()
                                            } else {
                                                phaseOptions.mapTo(linkedSetOf()) { it.first }
                                            }
                                        },
                                    shape = RoundedCornerShape(50),
                                    color = if (allSelected) colors.thingsBlueSoft else colors.tagPillBg,
                                    border = BorderStroke(
                                        0.75.dp,
                                        if (allSelected) colors.thingsBlue.copy(alpha = 0.45f) else colors.hairlineDivider
                                    )
                                ) {
                                    Text(
                                        text = stringResource(R.string.v30_pdf_select_all_phases),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (allSelected) colors.thingsBlue else colors.secondaryText,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                phaseOptions.forEach { (laneKey, phaseTitle) ->
                                    val checked = laneKey in selectedKeys
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val next = LinkedHashSet(selectedKeys)
                                                if (!next.add(laneKey)) next.remove(laneKey)
                                                selectedKeys = next
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (checked) colors.thingsBlueSoft.copy(alpha = 0.42f) else colors.cardSurface,
                                        border = BorderStroke(
                                            0.85.dp,
                                            if (checked) colors.thingsBlue.copy(alpha = 0.45f) else colors.hairlineDivider
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { wantChecked ->
                                                    val next = LinkedHashSet(selectedKeys)
                                                    if (wantChecked) next.add(laneKey) else next.remove(laneKey)
                                                    selectedKeys = next
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = colors.thingsBlue,
                                                    uncheckedColor = colors.secondaryText
                                                ),
                                                modifier = Modifier.size(30.dp)
                                            )
                                            Text(
                                                text = phaseTitle,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.primaryText,
                                                maxLines = Int.MAX_VALUE,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Timeline Range Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.v30_pdf_timeline_label),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = colors.primaryText
                            )

                            val presetItems = listOf(
                                RoadmapPdfTimelinePreset.FULL_PROJECT to stringResource(R.string.v30_pdf_timeline_auto),
                                RoadmapPdfTimelinePreset.FROM_TODAY to stringResource(R.string.v30_pdf_timeline_from_today),
                                RoadmapPdfTimelinePreset.NEXT_30_DAYS to stringResource(R.string.v30_pdf_timeline_next_30d),
                                RoadmapPdfTimelinePreset.CUSTOM to stringResource(R.string.v30_pdf_timeline_custom)
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                presetItems.forEach { (preset, label) ->
                                    val isSelected = timelinePreset == preset
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                timelinePreset = preset
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) colors.thingsBlueSoft else colors.cardSurface,
                                        border = BorderStroke(
                                            if (isSelected) 1.25.dp else 0.75.dp,
                                            if (isSelected) colors.thingsBlue else colors.hairlineDivider
                                        )
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) colors.thingsBlue else colors.primaryText,
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.5.dp)
                                        )
                                    }
                                }
                            }

                            if (timelinePreset == RoadmapPdfTimelinePreset.CUSTOM) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(11.dp))
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable { showCustomStartPicker = true },
                                        shape = RoundedCornerShape(11.dp),
                                        color = colors.cardSurface,
                                        border = BorderStroke(0.85.dp, colors.thingsBlue.copy(alpha = 0.42f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CalendarToday,
                                                contentDescription = null,
                                                tint = colors.thingsBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.v30_phase_set_start_date),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = colors.secondaryText
                                                )
                                                Text(
                                                    text = LocalDate.ofEpochDay(customStartDay).format(dateFmt),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = colors.primaryText
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(11.dp))
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable { showCustomEndPicker = true },
                                        shape = RoundedCornerShape(11.dp),
                                        color = colors.cardSurface,
                                        border = BorderStroke(0.85.dp, colors.thingsBlue.copy(alpha = 0.42f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Flag,
                                                contentDescription = null,
                                                tint = colors.thingsBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.phase_set_end_date),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = colors.secondaryText
                                                )
                                                Text(
                                                    text = LocalDate.ofEpochDay(customEndDay).format(dateFmt),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = colors.primaryText
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "${LocalDate.ofEpochDay(resolvedRange.first).format(dateFmt)} – ${LocalDate.ofEpochDay(resolvedRange.second).format(dateFmt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryText
                                )
                            }
                        }

                        // 3. Page Orientation 2-Card Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.v30_pdf_orientation_label),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = colors.primaryText
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val orientationCards = listOf(
                                    PdfPageOrientation.LANDSCAPE to stringResource(R.string.v30_pdf_orientation_landscape),
                                    PdfPageOrientation.PORTRAIT to stringResource(R.string.v30_pdf_orientation_portrait)
                                )
                                orientationCards.forEach { (option, label) ->
                                    val selected = orientation == option
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(13.dp))
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                orientation = option
                                            },
                                        shape = RoundedCornerShape(13.dp),
                                        color = if (selected) colors.thingsBlueSoft else colors.cardSurface,
                                        border = BorderStroke(
                                            if (selected) 1.5.dp else 0.85.dp,
                                            if (selected) colors.thingsBlue else colors.hairlineDivider
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(if (option == PdfPageOrientation.LANDSCAPE) 22.dp else 15.dp)
                                                    .height(if (option == PdfPageOrientation.LANDSCAPE) 15.dp else 22.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(
                                                        if (selected) colors.thingsBlue.copy(alpha = 0.22f)
                                                        else colors.tagPillBg
                                                    )
                                                    .border(
                                                        1.25.dp,
                                                        if (selected) colors.thingsBlue else colors.secondaryText,
                                                        RoundedCornerShape(3.dp)
                                                    )
                                            )
                                            Column {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = if (selected) colors.thingsBlue else colors.primaryText
                                                )
                                                Text(
                                                    text = "A4 • ${option.pageWidthPt} × ${option.pageHeightPt}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = colors.secondaryText
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Footer Buttons
                    val canExport = selectedKeys.isNotEmpty() || phaseOptions.isEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                color = colors.secondaryText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onConfirmExport(
                                    RoadmapPdfExportConfig(
                                        selectedLaneKeys = selectedKeys,
                                        startEpochDay = resolvedRange.first,
                                        endEpochDay = resolvedRange.second,
                                        orientation = orientation
                                    )
                                )
                            },
                            enabled = canExport,
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.thingsBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.v30_roadmap_export_pdf),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomStartPicker) {
        DeadlineDatePickerDialog(
            currentDeadline = ProjectRoadmapEngine.epochDayToStartOfDayMillis(customStartDay, zone),
            locale = locale,
            onApplyDeadline = { pickedMs ->
                val pickedDay = pickedMs?.let { ProjectRoadmapEngine.epochMillisToEpochDay(it, zone) }
                    ?: layout.startEpochDay
                customStartDay = pickedDay
                if (customEndDay < pickedDay) {
                    customEndDay = pickedDay
                }
            },
            onDismiss = { showCustomStartPicker = false },
            titleText = stringResource(R.string.v30_phase_set_start_date)
        )
    }

    if (showCustomEndPicker) {
        DeadlineDatePickerDialog(
            currentDeadline = ProjectRoadmapEngine.epochDayToStartOfDayMillis(customEndDay, zone),
            locale = locale,
            onApplyDeadline = { pickedMs ->
                val pickedDay = pickedMs?.let { ProjectRoadmapEngine.epochMillisToEpochDay(it, zone) }
                    ?: layout.endEpochDay
                customEndDay = pickedDay
                if (customStartDay > pickedDay) {
                    customStartDay = pickedDay
                }
            },
            onDismiss = { showCustomEndPicker = false },
            titleText = stringResource(R.string.phase_set_end_date)
        )
    }
}

// =============================================================================
// Multi-Page A4 PDF & 1-Click PNG Roadmap Exporter (`android.graphics.Canvas`)
// =============================================================================

object RoadmapExporter {

    private const val EXPORT_HEADER_H = 76f
    private const val EXPORT_DAY_HEADER_H = 44f
    private const val EXPORT_LABEL_W = 256f
    private const val EXPORT_DAY_W = 42f
    private const val EXPORT_PHASE_MIN_H = 48f
    private const val EXPORT_TASK_H = 34f
    private const val EXPORT_PADDING = 24f

    private const val PDF_MARGIN_PT = 24f
    private const val PDF_HEADER_H_PT = 52f
    private const val PDF_DAY_HEADER_H_PT = 36f

    private data class MeasuredExportRow(
        val row: RoadmapDisplayRow,
        val wrappedLines: List<String>,
        val subtext: String?,
        val heightPt: Float
    )

    suspend fun exportPdf(
        context: Context,
        project: Project,
        layout: ProjectRoadmapLayout,
        config: RoadmapPdfExportConfig,
        headingCounts: Map<String, Pair<Int, Int>> = emptyMap(),
        locale: Locale = Locale.getDefault(),
        isDark: Boolean = false,
        exportChooserTitle: String = context.getString(R.string.v30_roadmap_export)
    ): File {
        val generalLabel = context.getString(R.string.v30_board_ungrouped_column)
        val todoFallbackLabel = context.getString(R.string.entity_todo)
        val filteredLayout = ProjectRoadmapEngine.filterLayoutForExport(
            layout = layout.copy(project = project),
            config = config
        )

        val outFile = withContext(Dispatchers.IO) {
            val pageWidth = config.orientation.pageWidthPt
            val pageHeight = config.orientation.pageHeightPt
            val labelColW = if (config.orientation == PdfPageOrientation.LANDSCAPE) 210f else 168f

            val rawRows = buildExportRows(filteredLayout, headingCounts, generalLabel)
            val phaseMeasurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val shortDateFmt = DateTimeFormatter.ofPattern(
                if (locale.language == "de") "d. MMM" else "MMM d",
                locale
            )

            val measuredRows = rawRows.map { r ->
                when (r) {
                    is RoadmapDisplayRow.PhaseRow -> {
                        val lines = wrapTextLines(r.title, phaseMeasurePaint, labelColW - 22f)
                        val dateSub = r.phaseBar?.let { pb ->
                            val s = LocalDate.ofEpochDay(pb.startEpochDay).format(shortDateFmt)
                            val e = LocalDate.ofEpochDay(pb.endEpochDay).format(shortDateFmt)
                            if (pb.startEpochDay == pb.endEpochDay) s else "$s – $e"
                        }
                        val h = maxOf(
                            38f,
                            12f + lines.size * 13f + (if (dateSub != null) 12f else 2f)
                        )
                        MeasuredExportRow(r, lines, dateSub, h)
                    }
                    is RoadmapDisplayRow.TaskRow -> {
                        MeasuredExportRow(
                            row = r,
                            wrappedLines = listOf(r.taskBar.title.ifBlank { todoFallbackLabel }),
                            subtext = null,
                            heightPt = 28f
                        )
                    }
                }
            }

            val availableBodyH = (pageHeight - PDF_MARGIN_PT * 2f - PDF_HEADER_H_PT - PDF_DAY_HEADER_H_PT)
                .coerceAtLeast(140f)
            val pages = paginateRowsForA4(measuredRows, availableBodyH)

            val backupsDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val safeProjectSlug = project.title
                .replace(Regex("[^A-Za-z0-9_-]+"), "-")
                .trim('-')
                .take(40)
                .ifEmpty { "Project" }
            val dateStamp = LocalDate.now().toString()
            val targetFile = File(backupsDir, "Thingsbook-Roadmap-$safeProjectSlug-$dateStamp.pdf")

            val pdfDoc = PdfDocument()
            try {
                val totalPageCount = pages.size.coerceAtLeast(1)
                if (pages.isEmpty()) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                    val page = pdfDoc.startPage(pageInfo)
                    drawA4PdfPage(
                        canvas = page.canvas,
                        layout = filteredLayout,
                        pageRows = emptyList(),
                        pageNumber = 1,
                        totalPages = 1,
                        pageWidth = pageWidth.toFloat(),
                        pageHeight = pageHeight.toFloat(),
                        labelColW = labelColW,
                        locale = locale,
                        isDark = isDark,
                        todoFallbackLabel = todoFallbackLabel
                    )
                    pdfDoc.finishPage(page)
                } else {
                    pages.forEachIndexed { idx, pageRows ->
                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
                        val page = pdfDoc.startPage(pageInfo)
                        drawA4PdfPage(
                            canvas = page.canvas,
                            layout = filteredLayout,
                            pageRows = pageRows,
                            pageNumber = idx + 1,
                            totalPages = totalPageCount,
                            pageWidth = pageWidth.toFloat(),
                            pageHeight = pageHeight.toFloat(),
                            labelColW = labelColW,
                            locale = locale,
                            isDark = isDark,
                            todoFallbackLabel = todoFallbackLabel
                        )
                        pdfDoc.finishPage(page)
                    }
                }
                FileOutputStream(targetFile).use { out ->
                    pdfDoc.writeTo(out)
                    out.flush()
                }
            } finally {
                pdfDoc.close()
            }
            targetFile
        }

        shareExportedFile(
            context = context,
            outFile = outFile,
            projectTitle = project.title,
            mimeType = "application/pdf",
            exportChooserTitle = exportChooserTitle
        )
        return outFile
    }

    private fun paginateRowsForA4(
        rows: List<MeasuredExportRow>,
        availableBodyH: Float
    ): List<List<MeasuredExportRow>> {
        if (rows.isEmpty()) return emptyList()
        val pages = mutableListOf<List<MeasuredExportRow>>()
        var current = mutableListOf<MeasuredExportRow>()
        var usedH = 0f

        rows.forEachIndexed { idx, item ->
            val nextItem = rows.getOrNull(idx + 1)
            val wouldOrphanPhaseHeader = item.row is RoadmapDisplayRow.PhaseRow &&
                nextItem?.row is RoadmapDisplayRow.TaskRow &&
                current.isNotEmpty() &&
                usedH + item.heightPt + nextItem.heightPt > availableBodyH

            if ((current.isNotEmpty() && usedH + item.heightPt > availableBodyH) || wouldOrphanPhaseHeader) {
                pages += current
                current = mutableListOf()
                usedH = 0f
            }
            current += item
            usedH += item.heightPt
        }
        if (current.isNotEmpty()) {
            pages += current
        }
        return pages
    }

    suspend fun exportAndShareRoadmap(
        context: Context,
        layout: ProjectRoadmapLayout,
        headingCounts: Map<String, Pair<Int, Int>>,
        locale: Locale,
        isDark: Boolean,
        asPdf: Boolean,
        exportChooserTitle: String
    ) {
        if (asPdf) {
            val defaultConfig = RoadmapPdfExportConfig(
                selectedLaneKeys = layout.lanes.mapTo(linkedSetOf()) { it.laneKey },
                startEpochDay = layout.startEpochDay,
                endEpochDay = layout.endEpochDay,
                orientation = PdfPageOrientation.LANDSCAPE
            )
            exportPdf(
                context = context,
                project = layout.project,
                layout = layout,
                config = defaultConfig,
                headingCounts = headingCounts,
                locale = locale,
                isDark = isDark,
                exportChooserTitle = exportChooserTitle
            )
            return
        }

        val generalLabel = context.getString(R.string.v30_board_ungrouped_column)
        val todoFallbackLabel = context.getString(R.string.entity_todo)
        val outFile = withContext(Dispatchers.IO) {
            val rows = buildExportRows(layout, headingCounts, generalLabel)
            val measureBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 12.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val rowsHeight = rows.sumOf { row ->
                when (row) {
                    is RoadmapDisplayRow.PhaseRow -> {
                        val lines = wrapTextLines(row.title, measureBoldPaint, EXPORT_LABEL_W - 24f)
                        maxOf(EXPORT_PHASE_MIN_H, 20f + lines.size * 15f + (if (row.phaseBar != null) 13f else 2f)).toDouble()
                    }
                    is RoadmapDisplayRow.TaskRow -> EXPORT_TASK_H.toDouble()
                }
            }.toFloat().coerceAtLeast(72f)

            val totalWidth = (EXPORT_PADDING * 2f + EXPORT_LABEL_W + layout.totalDays * EXPORT_DAY_W).roundToInt()
            val totalHeight = (EXPORT_PADDING * 2f + EXPORT_HEADER_H + EXPORT_DAY_HEADER_H + rowsHeight).roundToInt()

            val backupsDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val safeProjectSlug = layout.project.title
                .replace(Regex("[^A-Za-z0-9_-]+"), "-")
                .trim('-')
                .take(40)
                .ifEmpty { "Project" }
            val dateStamp = LocalDate.now().toString()
            val targetFile = File(backupsDir, "Thingsbook-Roadmap-$safeProjectSlug-$dateStamp.png")

            val maxBitmapDim = 4096f
            val scale = minOf(1f, maxBitmapDim / maxOf(totalWidth.toFloat(), totalHeight.toFloat()))
            val bmpW = (totalWidth * scale).roundToInt().coerceAtLeast(1)
            val bmpH = (totalHeight * scale).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            try {
                val bmpCanvas = android.graphics.Canvas(bitmap)
                bmpCanvas.scale(scale, scale)
                drawRoadmapToAndroidCanvas(
                    canvas = bmpCanvas,
                    layout = layout,
                    rows = rows,
                    totalWidth = totalWidth.toFloat(),
                    totalHeight = totalHeight.toFloat(),
                    locale = locale,
                    isDark = isDark,
                    todoFallbackLabel = todoFallbackLabel
                )
                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
            } finally {
                bitmap.recycle()
            }
            targetFile
        }

        shareExportedFile(
            context = context,
            outFile = outFile,
            projectTitle = layout.project.title,
            mimeType = "image/png",
            exportChooserTitle = exportChooserTitle
        )
    }

    private fun shareExportedFile(
        context: Context,
        outFile: File,
        projectTitle: String,
        mimeType: String,
        exportChooserTitle: String
    ) {
        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, "$projectTitle — Roadmap")
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, exportChooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    private fun buildExportRows(
        layout: ProjectRoadmapLayout,
        headingCounts: Map<String, Pair<Int, Int>>,
        generalLabel: String
    ): List<RoadmapDisplayRow> {
        val rows = mutableListOf<RoadmapDisplayRow>()
        for (lane in layout.lanes) {
            val h = lane.heading
            val counts = h?.let { headingCounts[it.id] }
            val completed = counts?.first ?: 0
            val total = counts?.second ?: (lane.taskBars.size + lane.unscheduledTasks.size)
            rows += RoadmapDisplayRow.PhaseRow(
                heading = h,
                title = h?.title?.ifBlank { generalLabel } ?: generalLabel,
                phaseBar = lane.phaseBar,
                completedCount = completed,
                totalCount = total
            )
            for (tb in lane.taskBars) {
                rows += RoadmapDisplayRow.TaskRow(tb)
            }
        }
        return rows
    }

    private fun defaultUngroupedLabelForLocale(locale: Locale): String =
        when (locale.language.lowercase(Locale.ROOT)) {
            "de" -> "Allgemein"
            "fr" -> "Général"
            "it" -> "Generale"
            else -> "General"
        }

    private fun defaultTodoLabelForLocale(locale: Locale): String =
        when (locale.language.lowercase(Locale.ROOT)) {
            "de" -> "Aufgabe"
            "fr" -> "Tâche"
            "it" -> "Attività"
            "es" -> "Tarea"
            else -> "To-Do"
        }

    private fun drawA4PdfPage(
        canvas: android.graphics.Canvas,
        layout: ProjectRoadmapLayout,
        pageRows: List<MeasuredExportRow>,
        pageNumber: Int,
        totalPages: Int,
        pageWidth: Float,
        pageHeight: Float,
        labelColW: Float,
        locale: Locale,
        isDark: Boolean,
        todoFallbackLabel: String = defaultTodoLabelForLocale(locale)
    ) {
        val bgArgb = if (isDark) 0xFF121417.toInt() else 0xFFFFFFFF.toInt()
        val cardArgb = if (isDark) 0xFF1B1F25.toInt() else 0xFFF9FAFC.toInt()
        val sidebarArgb = if (isDark) 0xFF171A1E.toInt() else 0xFFF3F5F9.toInt()
        val dividerArgb = if (isDark) 0xFF2B313B.toInt() else 0xFFE4E8F0.toInt()
        val primaryTextArgb = if (isDark) 0xFFF3F5F8.toInt() else 0xFF15181E.toInt()
        val secondaryTextArgb = if (isDark) 0xFF9AA3B2.toInt() else 0xFF5F6775.toInt()
        val blueArgb = if (isDark) 0xFF6BC0FF.toInt() else 0xFF3898EC.toInt()
        val blueSoftArgb = if (isDark) 0xFF1A3047.toInt() else 0xFFE5F3FF.toInt()
        val amberArgb = if (isDark) 0xFFFBBF24.toInt() else 0xFFD69F3A.toInt()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.85f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextArgb
            textSize = 16.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextArgb
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val pageNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextArgb
            textSize = 10f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val phaseTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextArgb
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val phaseSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextArgb
            textSize = 8.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val taskLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextArgb
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        // Page background
        fillPaint.color = bgArgb
        canvas.drawRect(0f, 0f, pageWidth, pageHeight, fillPaint)

        val tableLeft = PDF_MARGIN_PT
        val tableRight = pageWidth - PDF_MARGIN_PT
        val tableTop = PDF_MARGIN_PT + PDF_HEADER_H_PT
        val bodyTop = tableTop + PDF_DAY_HEADER_H_PT
        val rowsTotalH = pageRows.sumOf { it.heightPt.toDouble() }.toFloat().coerceAtLeast(64f)
        val tableBottom = minOf(pageHeight - PDF_MARGIN_PT, bodyTop + rowsTotalH)
        val timelineLeft = tableLeft + labelColW
        val timelineWidth = (tableRight - timelineLeft).coerceAtLeast(100f)
        val totalDays = layout.totalDays.coerceAtLeast(1)
        val dayW = timelineWidth / totalDays.toFloat()

        // Header Title + Date Range + Page Number
        val fmt = DateTimeFormatter.ofPattern(if (locale.language == "de") "d. MMM yyyy" else "MMM d, yyyy", locale)
        val rangeText = "${LocalDate.ofEpochDay(layout.startEpochDay).format(fmt)} – ${LocalDate.ofEpochDay(layout.endEpochDay).format(fmt)}"
        canvas.drawText(
            ellipsizeText(layout.project.title, titlePaint, tableRight - tableLeft - 80f),
            tableLeft,
            PDF_MARGIN_PT + 19f,
            titlePaint
        )
        canvas.drawText(
            "Thingsbook Roadmap  •  $rangeText  •  ${layout.project.completedTaskCount}/${layout.project.totalTaskCount}",
            tableLeft,
            PDF_MARGIN_PT + 36f,
            subtitlePaint
        )
        canvas.drawText(
            "$pageNumber / $totalPages",
            tableRight,
            PDF_MARGIN_PT + 19f,
            pageNumPaint
        )

        // Table surface + Sidebar shading
        fillPaint.color = cardArgb
        canvas.drawRoundRect(RectF(tableLeft, tableTop, tableRight, tableBottom), 12f, 12f, fillPaint)
        fillPaint.color = sidebarArgb
        canvas.drawRect(tableLeft, tableTop, timelineLeft, tableBottom, fillPaint)
        canvas.drawRect(tableLeft, tableTop, tableRight, bodyTop, fillPaint)

        // Adaptive Day / Week / Month Header Ticks (prevents overlapping numbers on wide ranges)
        val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blueArgb
            textSize = 8.5f
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val dayTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val monthShortFmt = DateTimeFormatter.ofPattern("MMM", locale)
        var lastMonthLabelRight = timelineLeft - 8f
        var lastDayTickRight = timelineLeft - 8f

        for (i in 0 until totalDays) {
            val day = layout.startEpochDay + i
            val d = LocalDate.ofEpochDay(day)
            val colLeft = timelineLeft + i * dayW
            val colCenter = colLeft + dayW * 0.5f
            val isToday = day == layout.todayEpochDay
            val isWeekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            val isMonday = d.dayOfWeek == DayOfWeek.MONDAY

            if (isToday) {
                fillPaint.color = blueSoftArgb
                canvas.drawRect(colLeft, tableTop, colLeft + maxOf(dayW, 1.5f), tableBottom, fillPaint)
            } else if (isWeekend && dayW >= 5f) {
                fillPaint.color = if (isDark) 0x22FFFFFF else 0x0A000000
                canvas.drawRect(colLeft, bodyTop, colLeft + dayW, tableBottom, fillPaint)
            }

            if (i == 0 || d.dayOfMonth == 1) {
                val mLabel = if (d.dayOfMonth == 1 && d.monthValue == 1) {
                    "${d.format(monthShortFmt).uppercase(locale)} '${(d.year % 100)}"
                } else {
                    d.format(monthShortFmt).uppercase(locale)
                }
                val mWidth = monthPaint.measureText(mLabel)
                val mX = (colLeft + 2f).coerceAtMost(tableRight - mWidth - 2f)
                if (mX >= lastMonthLabelRight + 5f && mX >= timelineLeft) {
                    canvas.drawText(mLabel, mX, tableTop + 12.5f, monthPaint)
                    lastMonthLabelRight = mX + mWidth
                }
            }

            val wantsDayTick = when {
                isToday -> true
                dayW >= 16f -> true
                dayW >= 9.5f -> i % 2 == 0
                dayW >= 4.5f -> isMonday || d.dayOfMonth == 1
                dayW >= 2.2f -> (isMonday && (i / 7) % 2 == 0) || d.dayOfMonth == 1
                else -> d.dayOfMonth == 1
            }
            if (wantsDayTick) {
                val numStr = d.dayOfMonth.toString()
                val halfW = dayTickPaint.measureText(numStr) * 0.5f + 2f
                val clampedCenter = colCenter.coerceIn(timelineLeft + halfW, tableRight - halfW)
                if (clampedCenter - halfW >= lastDayTickRight + 2f || isToday) {
                    dayTickPaint.color = if (isToday) blueArgb else secondaryTextArgb
                    canvas.drawText(numStr, clampedCenter, tableTop + 28f, dayTickPaint)
                    lastDayTickRight = maxOf(lastDayTickRight, clampedCenter + halfW)
                }
            }

            if (dayW >= 8f || isMonday || d.dayOfMonth == 1) {
                strokePaint.color = dividerArgb
                strokePaint.strokeWidth = if (d.dayOfMonth == 1) 1f else 0.65f
                canvas.drawLine(colLeft, tableTop, colLeft, tableBottom, strokePaint)
            }
        }

        // Rows, Wrapped Full Phase Titles, Clamped Phase Bars + Unclipped Labels, & Task Bars
        val taskGeometry = HashMap<String, Triple<Float, Float, RoadmapTaskBar>>()
        var curY = bodyTop
        for (item in pageRows) {
            val rowH = item.heightPt
            when (val r = item.row) {
                is RoadmapDisplayRow.PhaseRow -> {
                    fillPaint.color = if (isDark) 0x223898EC else 0x143898EC
                    canvas.drawRect(tableLeft, curY, timelineLeft, curY + rowH, fillPaint)

                    // Accent bar + full multi-line Phase title in left column (zero ellipsis!)
                    fillPaint.color = blueArgb
                    canvas.drawRoundRect(
                        RectF(tableLeft + 7f, curY + 7f, tableLeft + 10f, curY + 19f),
                        2f,
                        2f,
                        fillPaint
                    )
                    var lineY = curY + 16f
                    for (line in item.wrappedLines) {
                        canvas.drawText(line, tableLeft + 14f, lineY, phaseTitlePaint)
                        lineY += 13f
                    }
                    if (item.subtext != null) {
                        canvas.drawText(item.subtext, tableLeft + 14f, lineY - 1f, phaseSubPaint)
                    }

                    val pb = r.phaseBar
                    if (pb != null && pb.endEpochDay >= layout.startEpochDay && pb.startEpochDay <= layout.endEpochDay) {
                        val visStart = pb.startEpochDay.coerceIn(layout.startEpochDay, layout.endEpochDay)
                        val visEnd = pb.endEpochDay.coerceIn(visStart, layout.endEpochDay)
                        val barLeft = timelineLeft + (visStart - layout.startEpochDay) * dayW + 1.5f
                        val rawRight = timelineLeft + (visEnd - layout.startEpochDay + 1L) * dayW - 1.5f
                        val barRight = maxOf(barLeft + minOf(10f, dayW * 0.85f), minOf(rawRight, tableRight - 1.5f))
                        val barH = 18f
                        val barTop = curY + (rowH - barH) * 0.5f
                        val barRect = RectF(barLeft, barTop, barRight, barTop + barH)

                        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                            shader = LinearGradient(
                                barRect.left,
                                barRect.top,
                                barRect.right,
                                barRect.top,
                                0xFF74C4FF.toInt(),
                                0xFF3898EC.toInt(),
                                Shader.TileMode.CLAMP
                            )
                        }
                        canvas.drawRoundRect(barRect, 6f, 6f, gradPaint)

                        drawPhaseTimelineTitle(
                            canvas = canvas,
                            title = pb.title,
                            barRect = barRect,
                            timelineLeft = timelineLeft,
                            tableRight = tableRight,
                            blueArgb = blueArgb,
                            cardArgb = cardArgb,
                            textSize = 9.2f
                        )
                    }
                }

                is RoadmapDisplayRow.TaskRow -> {
                    val tb = r.taskBar
                    taskGeometry[tb.taskId] = Triple(curY, rowH, tb)
                    val milestoneVioletArgb = if (isDark) 0xFFA78BFA.toInt() else 0xFF7C3AED.toInt()
                    val leftRowPaint = if (tb.isMilestone) {
                        Paint(taskLabelPaint).apply {
                            color = milestoneVioletArgb
                            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        }
                    } else {
                        taskLabelPaint
                    }
                    val prefix = if (tb.isMilestone) "◆ " else "• "
                    canvas.drawText(
                        ellipsizeText("$prefix${tb.title.ifBlank { todoFallbackLabel }}", leftRowPaint, labelColW - 22f),
                        tableLeft + 12f,
                        curY + rowH * 0.5f + 3.5f,
                        leftRowPaint
                    )

                    if (tb.endEpochDay >= layout.startEpochDay && tb.startEpochDay <= layout.endEpochDay) {
                        val visStart = tb.startEpochDay.coerceIn(layout.startEpochDay, layout.endEpochDay)
                        val visEnd = tb.endEpochDay.coerceIn(visStart, layout.endEpochDay)
                        if (tb.isMilestone) {
                            val centerX = timelineLeft + (visStart - layout.startEpochDay + 0.5f) * dayW
                            val centerY = curY + rowH * 0.5f
                            drawMilestoneTimelineDiamondAndLabel(
                                canvas = canvas,
                                title = tb.title.ifBlank { todoFallbackLabel },
                                centerX = centerX,
                                centerY = centerY,
                                diamondRadius = 6.8f,
                                timelineLeft = timelineLeft,
                                tableRight = tableRight,
                                isDark = isDark,
                                textSize = 8.8f
                            )
                        } else {
                            val barLeft = timelineLeft + (visStart - layout.startEpochDay) * dayW + 1.5f
                            val rawRight = timelineLeft + (visEnd - layout.startEpochDay + 1L) * dayW - 1.5f
                            val barRight = maxOf(barLeft + minOf(8f, dayW * 0.8f), minOf(rawRight, tableRight - 1.5f))
                            val barH = 16f
                            val barTop = curY + (rowH - barH) * 0.5f
                            val barRect = RectF(barLeft, barTop, barRight, barTop + barH)

                            fillPaint.color = if (tb.isActivelyBlocked) (if (isDark) 0x33FBBF24 else 0x26D69F3A) else blueSoftArgb
                            canvas.drawRoundRect(barRect, 5f, 5f, fillPaint)

                            strokePaint.color = if (tb.isActivelyBlocked) amberArgb else blueArgb
                            strokePaint.strokeWidth = 1f
                            canvas.drawRoundRect(barRect, 5f, 5f, strokePaint)

                            val taskBarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = if (tb.isActivelyBlocked) amberArgb else primaryTextArgb
                                textSize = 8.5f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                            }
                            if (barRect.width() >= 22f) {
                                canvas.drawText(
                                    ellipsizeText(tb.title.ifBlank { todoFallbackLabel }, taskBarTextPaint, barRect.width() - 8f),
                                    barRect.left + 4f,
                                    barRect.centerY() + 3f,
                                    taskBarTextPaint
                                )
                            }
                        }
                    }
                }
            }
            curY += rowH
            strokePaint.color = dividerArgb
            strokePaint.strokeWidth = 0.75f
            canvas.drawLine(tableLeft, curY, tableRight, curY, strokePaint)
        }

        // Today vertical dashed line
        if (layout.todayEpochDay in layout.startEpochDay..layout.endEpochDay) {
            val todayX = timelineLeft + (layout.todayEpochDay - layout.startEpochDay) * dayW + dayW * 0.5f
            val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = blueArgb
                style = Paint.Style.STROKE
                strokeWidth = 1.6f
                pathEffect = DashPathEffect(floatArrayOf(5f, 3.5f), 0f)
            }
            canvas.drawLine(todayX, bodyTop, todayX, tableBottom, todayPaint)
        }

        // Dependency Cubic-Bézier Curves + Arrowheads among tasks on this page
        for (edge in layout.dependencyEdges) {
            val fromGeom = taskGeometry[edge.fromTaskId] ?: continue
            val toGeom = taskGeometry[edge.toTaskId] ?: continue
            val visFromEnd = fromGeom.third.endEpochDay.coerceIn(layout.startEpochDay, layout.endEpochDay)
            val visToStart = toGeom.third.startEpochDay.coerceIn(layout.startEpochDay, layout.endEpochDay)
            val sx = if (fromGeom.third.isMilestone) {
                timelineLeft + (visFromEnd - layout.startEpochDay + 0.5f) * dayW + 7.2f
            } else {
                timelineLeft + (visFromEnd - layout.startEpochDay + 1L) * dayW - 1.5f
            }
            val sy = fromGeom.first + fromGeom.second * 0.5f
            val ex = if (toGeom.third.isMilestone) {
                timelineLeft + (visToStart - layout.startEpochDay + 0.5f) * dayW - 7.2f
            } else {
                timelineLeft + (visToStart - layout.startEpochDay) * dayW + 1.5f
            }
            val ey = toGeom.first + toGeom.second * 0.5f
            val ctrl = maxOf(kotlin.math.abs(ex - sx) * 0.48f, 12f)
            val c1x = sx + ctrl
            val c2x = ex - ctrl

            val edgeColor = if (edge.isActivelyBlocking) amberArgb else blueArgb
            val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = edgeColor
                style = Paint.Style.STROKE
                strokeWidth = 1.35f
                strokeCap = Paint.Cap.ROUND
            }
            val path = android.graphics.Path().apply {
                moveTo(sx, sy)
                cubicTo(c1x, sy, c2x, ey, ex, ey)
            }
            canvas.drawPath(path, curvePaint)

            val angle = atan2(ey - ey, ex - c2x)
            val arrowLen = 4.8f
            val arrowPath = android.graphics.Path().apply {
                moveTo(ex, ey)
                lineTo(ex - arrowLen * cos(angle - 0.52f), ey - arrowLen * sin(angle - 0.52f))
                lineTo(ex - arrowLen * cos(angle + 0.52f), ey - arrowLen * sin(angle + 0.52f))
                close()
            }
            fillPaint.color = edgeColor
            canvas.drawPath(arrowPath, fillPaint)
        }

        // Outer table frame & label divider
        strokePaint.color = dividerArgb
        strokePaint.strokeWidth = 1f
        canvas.drawLine(timelineLeft, tableTop, timelineLeft, tableBottom, strokePaint)
        canvas.drawLine(tableLeft, bodyTop, tableRight, bodyTop, strokePaint)
        canvas.drawRoundRect(RectF(tableLeft, tableTop, tableRight, tableBottom), 12f, 12f, strokePaint)
    }

    fun drawRoadmapToAndroidCanvas(
        canvas: android.graphics.Canvas,
        layout: ProjectRoadmapLayout,
        headingCounts: Map<String, Pair<Int, Int>> = emptyMap(),
        locale: Locale = Locale.getDefault(),
        isDark: Boolean = false,
        generalLabel: String = defaultUngroupedLabelForLocale(locale),
        todoFallbackLabel: String = defaultTodoLabelForLocale(locale)
    ) {
        val rows = buildExportRows(layout, headingCounts, generalLabel)
        val measureBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val rowsHeight = rows.sumOf { row ->
            when (row) {
                is RoadmapDisplayRow.PhaseRow -> {
                    val lines = wrapTextLines(row.title, measureBoldPaint, EXPORT_LABEL_W - 24f)
                    maxOf(EXPORT_PHASE_MIN_H, 20f + lines.size * 15f + (if (row.phaseBar != null) 13f else 2f)).toDouble()
                }
                is RoadmapDisplayRow.TaskRow -> EXPORT_TASK_H.toDouble()
            }
        }.toFloat().coerceAtLeast(72f)
        val totalWidth = EXPORT_PADDING * 2f + EXPORT_LABEL_W + layout.totalDays * EXPORT_DAY_W
        val totalHeight = EXPORT_PADDING * 2f + EXPORT_HEADER_H + EXPORT_DAY_HEADER_H + rowsHeight
        drawRoadmapToAndroidCanvas(canvas, layout, rows, totalWidth, totalHeight, locale, isDark, todoFallbackLabel)
    }

    private fun drawRoadmapToAndroidCanvas(
        canvas: android.graphics.Canvas,
        layout: ProjectRoadmapLayout,
        rows: List<RoadmapDisplayRow>,
        totalWidth: Float,
        totalHeight: Float,
        locale: Locale,
        isDark: Boolean,
        todoFallbackLabel: String = defaultTodoLabelForLocale(locale)
    ) {
        val bgArgb = if (isDark) 0xFF121417.toInt() else 0xFFFFFFFF.toInt()
        val cardArgb = if (isDark) 0xFF1B1F25.toInt() else 0xFFF9FAFC.toInt()
        val sidebarArgb = if (isDark) 0xFF171A1E.toInt() else 0xFFF3F5F9.toInt()
        val dividerArgb = if (isDark) 0xFF2B313B.toInt() else 0xFFE4E8F0.toInt()
        val primaryTextArgb = if (isDark) 0xFFF3F5F8.toInt() else 0xFF15181E.toInt()
        val secondaryTextArgb = if (isDark) 0xFF9AA3B2.toInt() else 0xFF5F6775.toInt()
        val blueArgb = if (isDark) 0xFF6BC0FF.toInt() else 0xFF3898EC.toInt()
        val blueSoftArgb = if (isDark) 0xFF1A3047.toInt() else 0xFFE5F3FF.toInt()
        val amberArgb = if (isDark) 0xFFFBBF24.toInt() else 0xFFD69F3A.toInt()

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextArgb
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextArgb
            textSize = 12.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val rowBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextArgb
            textSize = 12.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val phaseSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextArgb
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val rowRegularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextArgb
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        // Background
        fillPaint.color = bgArgb
        canvas.drawRect(0f, 0f, totalWidth, totalHeight, fillPaint)

        // Header Title + Date Range
        val fmt = DateTimeFormatter.ofPattern(if (locale.language == "de") "d. MMM yyyy" else "MMM d, yyyy", locale)
        val shortFmt = DateTimeFormatter.ofPattern(if (locale.language == "de") "d. MMM" else "MMM d", locale)
        val rangeText = "${LocalDate.ofEpochDay(layout.startEpochDay).format(fmt)} – ${LocalDate.ofEpochDay(layout.endEpochDay).format(fmt)}"
        canvas.drawText(layout.project.title, EXPORT_PADDING, EXPORT_PADDING + 28f, titlePaint)
        canvas.drawText(
            "Thingsbook Roadmap  •  $rangeText  •  ${layout.project.completedTaskCount}/${layout.project.totalTaskCount}",
            EXPORT_PADDING,
            EXPORT_PADDING + 50f,
            subtitlePaint
        )

        val tableLeft = EXPORT_PADDING
        val tableTop = EXPORT_PADDING + EXPORT_HEADER_H
        val tableRight = totalWidth - EXPORT_PADDING
        val tableBottom = totalHeight - EXPORT_PADDING
        val timelineLeft = tableLeft + EXPORT_LABEL_W
        val bodyTop = tableTop + EXPORT_DAY_HEADER_H

        // Table surface
        fillPaint.color = cardArgb
        canvas.drawRoundRect(RectF(tableLeft, tableTop, tableRight, tableBottom), 16f, 16f, fillPaint)

        // Left column & header strip shading
        fillPaint.color = sidebarArgb
        canvas.drawRect(tableLeft, tableTop, timelineLeft, tableBottom, fillPaint)
        canvas.drawRect(tableLeft, tableTop, tableRight, bodyTop, fillPaint)

        // Day headers & vertical grid
        val dayNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val monthShortFmt = DateTimeFormatter.ofPattern("MMM", locale)
        for (i in 0 until layout.totalDays) {
            val day = layout.startEpochDay + i
            val d = LocalDate.ofEpochDay(day)
            val colLeft = timelineLeft + i * EXPORT_DAY_W
            val colCenter = colLeft + EXPORT_DAY_W * 0.5f
            val isToday = day == layout.todayEpochDay
            val isWeekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

            if (isToday) {
                fillPaint.color = blueSoftArgb
                canvas.drawRect(colLeft, tableTop, colLeft + EXPORT_DAY_W, tableBottom, fillPaint)
            } else if (isWeekend) {
                fillPaint.color = if (isDark) 0x22FFFFFF else 0x0A000000
                canvas.drawRect(colLeft, bodyTop, colLeft + EXPORT_DAY_W, tableBottom, fillPaint)
            }

            if (i == 0 || d.dayOfMonth == 1) {
                dayNumPaint.color = blueArgb
                canvas.drawText(d.format(monthShortFmt).uppercase(locale), colCenter, tableTop + 15f, dayNumPaint)
            }
            dayNumPaint.color = if (isToday) blueArgb else secondaryTextArgb
            canvas.drawText(d.dayOfMonth.toString(), colCenter, tableTop + 33f, dayNumPaint)

            strokePaint.color = dividerArgb
            strokePaint.strokeWidth = 1f
            canvas.drawLine(colLeft, tableTop, colLeft, tableBottom, strokePaint)
        }

        // Rows, Wrapped Full Phase Titles, Clamped Phase Glacier-Blue Bars, and Soft Bento Task Bars
        val taskGeometry = HashMap<String, Triple<Float, Float, RoadmapTaskBar>>()
        var curY = bodyTop
        for (row in rows) {
            when (row) {
                is RoadmapDisplayRow.PhaseRow -> {
                    val wrappedLines = wrapTextLines(row.title, rowBoldPaint, EXPORT_LABEL_W - 24f)
                    val dateSub = row.phaseBar?.let { pb ->
                        val s = LocalDate.ofEpochDay(pb.startEpochDay).format(shortFmt)
                        val e = LocalDate.ofEpochDay(pb.endEpochDay).format(shortFmt)
                        if (pb.startEpochDay == pb.endEpochDay) s else "$s – $e"
                    }
                    val phaseRowH = maxOf(
                        EXPORT_PHASE_MIN_H,
                        20f + wrappedLines.size * 15f + (if (dateSub != null) 13f else 2f)
                    )

                    fillPaint.color = if (isDark) 0x223898EC else 0x143898EC
                    canvas.drawRect(tableLeft, curY, timelineLeft, curY + phaseRowH, fillPaint)

                    var textY = curY + 20f
                    for (line in wrappedLines) {
                        canvas.drawText(line, tableLeft + 12f, textY, rowBoldPaint)
                        textY += 15f
                    }
                    if (dateSub != null) {
                        canvas.drawText(dateSub, tableLeft + 12f, textY - 1f, phaseSubPaint)
                    }

                    val pb = row.phaseBar
                    if (pb != null && pb.endEpochDay >= layout.startEpochDay && pb.startEpochDay <= layout.endEpochDay) {
                        val visStart = pb.startEpochDay.coerceIn(layout.startEpochDay, layout.endEpochDay)
                        val visEnd = pb.endEpochDay.coerceIn(visStart, layout.endEpochDay)
                        val barLeft = timelineLeft + (visStart - layout.startEpochDay) * EXPORT_DAY_W + 3f
                        val rawRight = timelineLeft + (visEnd - layout.startEpochDay + 1L) * EXPORT_DAY_W - 3f
                        val barRight = maxOf(barLeft + 16f, minOf(rawRight, tableRight - 3f))
                        val barH = 26f
                        val barTop = curY + (phaseRowH - barH) * 0.5f
                        val barRect = RectF(barLeft, barTop, barRight, barTop + barH)
                        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                            shader = LinearGradient(
                                barRect.left,
                                barRect.top,
                                barRect.right,
                                barRect.top,
                                0xFF74C4FF.toInt(),
                                0xFF3898EC.toInt(),
                                Shader.TileMode.CLAMP
                            )
                        }
                        canvas.drawRoundRect(barRect, 9f, 9f, gradPaint)
                        drawPhaseTimelineTitle(
                            canvas = canvas,
                            title = pb.title,
                            barRect = barRect,
                            timelineLeft = timelineLeft,
                            tableRight = tableRight,
                            blueArgb = blueArgb,
                            cardArgb = cardArgb,
                            textSize = 11f
                        )
                    }
                    curY += phaseRowH
                }

                is RoadmapDisplayRow.TaskRow -> {
                    val tb = row.taskBar
                    taskGeometry[tb.taskId] = Triple(curY, EXPORT_TASK_H, tb)
                    val milestoneVioletArgb = if (isDark) 0xFFA78BFA.toInt() else 0xFF7C3AED.toInt()
                    val leftPaint = if (tb.isMilestone) {
                        Paint(rowRegularPaint).apply {
                            color = milestoneVioletArgb
                            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        }
                    } else {
                        rowRegularPaint
                    }
                    val prefix = if (tb.isMilestone) "◆ " else "• "
                    canvas.drawText(
                        ellipsizeText("$prefix${tb.title.ifBlank { todoFallbackLabel }}", leftPaint, EXPORT_LABEL_W - 28f),
                        tableLeft + 16f,
                        curY + 21f,
                        leftPaint
                    )
                    if (tb.endEpochDay >= layout.startEpochDay && tb.startEpochDay <= layout.endEpochDay) {
                        val visStart = tb.startEpochDay.coerceIn(layout.startEpochDay, layout.endEpochDay)
                        val visEnd = tb.endEpochDay.coerceIn(visStart, layout.endEpochDay)
                        if (tb.isMilestone) {
                            val centerX = timelineLeft + (visStart - layout.startEpochDay + 0.5f) * EXPORT_DAY_W
                            val centerY = curY + EXPORT_TASK_H * 0.5f
                            drawMilestoneTimelineDiamondAndLabel(
                                canvas = canvas,
                                title = tb.title.ifBlank { todoFallbackLabel },
                                centerX = centerX,
                                centerY = centerY,
                                diamondRadius = 9.5f,
                                timelineLeft = timelineLeft,
                                tableRight = tableRight,
                                isDark = isDark,
                                textSize = 11f
                            )
                        } else {
                            val barLeft = timelineLeft + (visStart - layout.startEpochDay) * EXPORT_DAY_W + 3f
                            val rawRight = timelineLeft + (visEnd - layout.startEpochDay + 1L) * EXPORT_DAY_W - 3f
                            val barRight = maxOf(barLeft + 14f, minOf(rawRight, tableRight - 3f))
                            val barRect = RectF(barLeft, curY + 5f, barRight, curY + EXPORT_TASK_H - 5f)

                            fillPaint.color = if (tb.isActivelyBlocked) (if (isDark) 0x33FBBF24 else 0x26D69F3A) else blueSoftArgb
                            canvas.drawRoundRect(barRect, 7f, 7f, fillPaint)

                            strokePaint.color = if (tb.isActivelyBlocked) amberArgb else blueArgb
                            strokePaint.strokeWidth = 1.3f
                            canvas.drawRoundRect(barRect, 7f, 7f, strokePaint)

                            val taskBarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = if (tb.isActivelyBlocked) amberArgb else primaryTextArgb
                                textSize = 10.5f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                            }
                            canvas.drawText(
                                ellipsizeText(tb.title.ifBlank { todoFallbackLabel }, taskBarTextPaint, (barRect.width() - 12f).coerceAtLeast(0f)),
                                barRect.left + 6f,
                                barRect.centerY() + 3.8f,
                                taskBarTextPaint
                            )
                        }
                    }
                    curY += EXPORT_TASK_H
                }
            }
            strokePaint.color = dividerArgb
            strokePaint.strokeWidth = 1f
            canvas.drawLine(tableLeft, curY, tableRight, curY, strokePaint)
        }

        // Today vertical line
        if (layout.todayEpochDay in layout.startEpochDay..layout.endEpochDay) {
            val todayX = timelineLeft + (layout.todayEpochDay - layout.startEpochDay) * EXPORT_DAY_W + EXPORT_DAY_W * 0.5f
            val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = blueArgb
                style = Paint.Style.STROKE
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
            }
            canvas.drawLine(todayX, bodyTop, todayX, tableBottom, todayPaint)
        }

        // Dependency Cubic-Bézier Curves + Arrowheads
        for (edge in layout.dependencyEdges) {
            val fromGeom = taskGeometry[edge.fromTaskId] ?: continue
            val toGeom = taskGeometry[edge.toTaskId] ?: continue
            val visFromEnd = fromGeom.third.endEpochDay.coerceIn(layout.startEpochDay, layout.endEpochDay)
            val visToStart = toGeom.third.startEpochDay.coerceIn(layout.startEpochDay, layout.endEpochDay)
            val sx = if (fromGeom.third.isMilestone) {
                timelineLeft + (visFromEnd - layout.startEpochDay + 0.5f) * EXPORT_DAY_W + 10f
            } else {
                timelineLeft + (visFromEnd - layout.startEpochDay + 1L) * EXPORT_DAY_W - 3f
            }
            val sy = fromGeom.first + fromGeom.second * 0.5f
            val ex = if (toGeom.third.isMilestone) {
                timelineLeft + (visToStart - layout.startEpochDay + 0.5f) * EXPORT_DAY_W - 10f
            } else {
                timelineLeft + (visToStart - layout.startEpochDay) * EXPORT_DAY_W + 3f
            }
            val ey = toGeom.first + toGeom.second * 0.5f
            val ctrl = maxOf(kotlin.math.abs(ex - sx) * 0.48f, 18f)
            val c1x = sx + ctrl
            val c2x = ex - ctrl

            val edgeColor = if (edge.isActivelyBlocking) amberArgb else blueArgb
            val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = edgeColor
                style = Paint.Style.STROKE
                strokeWidth = 1.8f
                strokeCap = Paint.Cap.ROUND
            }
            val path = android.graphics.Path().apply {
                moveTo(sx, sy)
                cubicTo(c1x, sy, c2x, ey, ex, ey)
            }
            canvas.drawPath(path, curvePaint)

            val angle = atan2(ey - ey, ex - c2x)
            val arrowLen = 6f
            val arrowPath = android.graphics.Path().apply {
                moveTo(ex, ey)
                lineTo(ex - arrowLen * cos(angle - 0.52f), ey - arrowLen * sin(angle - 0.52f))
                lineTo(ex - arrowLen * cos(angle + 0.52f), ey - arrowLen * sin(angle + 0.52f))
                close()
            }
            fillPaint.color = edgeColor
            canvas.drawPath(arrowPath, fillPaint)
        }

        // Outer table frame & label divider
        strokePaint.color = dividerArgb
        strokePaint.strokeWidth = 1.2f
        canvas.drawLine(timelineLeft, tableTop, timelineLeft, tableBottom, strokePaint)
        canvas.drawLine(tableLeft, bodyTop, tableRight, bodyTop, strokePaint)
        canvas.drawRoundRect(RectF(tableLeft, tableTop, tableRight, tableBottom), 16f, 16f, strokePaint)
    }

    private fun drawMilestoneTimelineDiamondAndLabel(
        canvas: android.graphics.Canvas,
        title: String,
        centerX: Float,
        centerY: Float,
        diamondRadius: Float,
        timelineLeft: Float,
        tableRight: Float,
        isDark: Boolean,
        textSize: Float
    ) {
        val milestoneHexArgb = 0xFF7C3AED.toInt()
        val labelFgArgb = if (isDark) 0xFFA78BFA.toInt() else milestoneHexArgb
        val pillBgArgb = if (isDark) 0xEB2E1F47.toInt() else 0xF0F3E8FF.toInt()
        val glowArgb = if (isDark) 0x3DA78BFA else 0x2E7C3AED

        // Soft Milestone Violet Glow Halo
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = glowArgb
        }
        canvas.drawCircle(centerX, centerY, diamondRadius * 1.45f, glowPaint)

        // True 45° Vector Diamond Path in #7C3AED
        val diamondPath = android.graphics.Path().apply {
            moveTo(centerX, centerY - diamondRadius)
            lineTo(centerX + diamondRadius, centerY)
            lineTo(centerX, centerY + diamondRadius)
            lineTo(centerX - diamondRadius, centerY)
            close()
        }
        val diamondFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = milestoneHexArgb
        }
        canvas.drawPath(diamondPath, diamondFillPaint)

        // Crisp White Inner Rim inside the 45° Diamond
        val innerR = (diamondRadius * 0.68f).coerceAtLeast(2.2f)
        val innerRimPath = android.graphics.Path().apply {
            moveTo(centerX, centerY - innerR)
            lineTo(centerX + innerR, centerY)
            lineTo(centerX, centerY + innerR)
            lineTo(centerX - innerR, centerY)
            close()
        }
        val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (diamondRadius * 0.14f).coerceAtLeast(1f)
            color = 0xEBFFFFFF.toInt()
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(innerRimPath, innerRimPaint)

        // Bold Floating Callout Label ("◆ ${task.title}") immediately beside the diamond
        val calloutLabel = "◆ ${title.trim()}"
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelFgArgb
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val rawWidth = labelPaint.measureText(calloutLabel)
        val gap = diamondRadius + 5.5f
        val spaceRight = (tableRight - 5f) - (centerX + gap)
        val spaceLeft = (centerX - gap) - (timelineLeft + 5f)
        val placeOnRight = spaceRight >= rawWidth + 8f || spaceRight >= spaceLeft
        val maxAvail = (if (placeOnRight) spaceRight else spaceLeft).coerceAtLeast(24f)
        val finalText = ellipsizeText(calloutLabel, labelPaint, maxAvail)
        if (finalText.isEmpty()) return
        val finalWidth = labelPaint.measureText(finalText)
        val textX = if (placeOnRight) {
            (centerX + gap + 4f).coerceAtMost((tableRight - 4f - finalWidth).coerceAtLeast(timelineLeft + 4f))
        } else {
            (centerX - gap - 4f - finalWidth).coerceAtLeast(timelineLeft + 4f)
        }
        val pillRect = RectF(
            textX - 4.5f,
            centerY - textSize * 0.72f,
            (textX + finalWidth + 4.5f).coerceAtMost(tableRight - 2f),
            centerY + textSize * 0.62f
        )
        val pillFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = pillBgArgb
        }
        val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.85f
            color = if (isDark) 0x7AA78BFA else 0x667C3AED
        }
        canvas.drawRoundRect(pillRect, 5f, 5f, pillFill)
        canvas.drawRoundRect(pillRect, 5f, 5f, pillStroke)
        canvas.drawText(finalText, textX, centerY + textSize * 0.34f, labelPaint)
    }

    private fun drawPhaseTimelineTitle(
        canvas: android.graphics.Canvas,
        title: String,
        barRect: RectF,
        timelineLeft: Float,
        tableRight: Float,
        blueArgb: Int,
        cardArgb: Int,
        textSize: Float
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return

        val barTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val titleWidth = barTextPaint.measureText(cleanTitle)
        if (barRect.width() >= titleWidth + 14f) {
            canvas.drawText(
                cleanTitle,
                barRect.left + 7f,
                barRect.centerY() + textSize * 0.34f,
                barTextPaint
            )
            return
        }

        // Narrow bar: draw full untruncated Phase title right beside the bar (right if room, else left)
        val outsidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blueArgb
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val spaceRight = (tableRight - 6f) - (barRect.right + 5f)
        val spaceLeft = (barRect.left - 5f) - (timelineLeft + 6f)
        val textX = if (spaceRight >= titleWidth || spaceRight >= spaceLeft) {
            (barRect.right + 5f).coerceAtMost((tableRight - 4f - titleWidth).coerceAtLeast(timelineLeft + 4f))
        } else {
            (barRect.left - 5f - titleWidth).coerceAtLeast(timelineLeft + 4f)
        }
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = cardArgb
            alpha = 225
        }
        val pillRect = RectF(
            textX - 3f,
            barRect.centerY() - textSize * 0.68f,
            (textX + titleWidth + 3f).coerceAtMost(tableRight - 2f),
            barRect.centerY() + textSize * 0.56f
        )
        canvas.drawRoundRect(pillRect, 4f, 4f, pillBgPaint)
        canvas.drawText(cleanTitle, textX, barRect.centerY() + textSize * 0.34f, outsidePaint)
    }

    private fun wrapTextLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return listOf("")
        if (maxWidth <= 16f || paint.measureText(clean) <= maxWidth) return listOf(clean)

        val lines = mutableListOf<String>()
        var current = ""

        fun pushLongWord(word: String) {
            var start = 0
            while (start < word.length) {
                var end = start + 1
                while (end <= word.length && paint.measureText(word.substring(start, end)) <= maxWidth) {
                    end++
                }
                val takeEnd = (end - 1).coerceAtLeast(start + 1)
                val chunk = word.substring(start, takeEnd)
                if (takeEnd < word.length) {
                    lines += chunk
                } else {
                    current = chunk
                }
                start = takeEnd
            }
        }

        for (word in clean.split(Regex("\\s+"))) {
            if (word.isEmpty()) continue
            if (current.isEmpty()) {
                if (paint.measureText(word) <= maxWidth) {
                    current = word
                } else {
                    pushLongWord(word)
                }
            } else {
                val candidate = "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    lines += current
                    current = ""
                    if (paint.measureText(word) <= maxWidth) {
                        current = word
                    } else {
                        pushLongWord(word)
                    }
                }
            }
        }
        if (current.isNotEmpty()) {
            lines += current
        }
        return if (lines.isEmpty()) listOf(clean) else lines
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 8f || text.isEmpty()) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (paint.measureText(text.substring(0, mid) + ellipsis) <= maxWidth) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return if (low <= 0) "" else text.substring(0, low) + ellipsis
    }
}
