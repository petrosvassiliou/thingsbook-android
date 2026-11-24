package com.pv.thingsbook.ui.components

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pv.thingsbook.R
import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.CalendarEventItem
import com.pv.thingsbook.domain.model.ChecklistItem
import com.pv.thingsbook.domain.model.EffortLevel
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.STANDALONE_DURATION_PRESETS
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.model.TimeblockScheduleUtils
import com.pv.thingsbook.domain.model.TodayTimelineEntry
import com.pv.thingsbook.domain.model.formatDurationCompact
import com.pv.thingsbook.ui.theme.ThingsTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

const val WORKSPACE_INSPECTOR_PANEL_WIDTH_DP = 352

internal fun resolveInspectorTargetMillis(
    task: TaskItem,
    todayStartMillis: Long
): Long? {
    return task.deadline
        ?: task.whenDate
        ?: if (task.scheduleState == ScheduleState.TODAY || task.scheduleState == ScheduleState.THIS_EVENING) {
            todayStartMillis
        } else {
            null
        }
}

internal fun computeUpcomingDeadlinesAndMilestones(
    allOpenTasks: List<TaskItem>,
    todayDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
    limit: Int = 8
): List<TaskItem> {
    if (limit <= 0 || allOpenTasks.isEmpty()) return emptyList()
    val todayStartMillis = todayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val horizonEndMillis = todayDate.plusDays(15).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L

    return allOpenTasks
        .asSequence()
        .filter { task ->
            !task.isDeleted &&
                task.status == TaskStatus.OPEN &&
                (task.isMilestone || task.deadline != null)
        }
        .mapNotNull { task ->
            val targetMs = resolveInspectorTargetMillis(task, todayStartMillis)
            if (targetMs != null && targetMs <= horizonEndMillis) {
                task to targetMs
            } else {
                null
            }
        }
        .sortedWith(
            compareBy<Pair<TaskItem, Long>> { it.second }
                .thenBy { !it.first.isMilestone }
                .thenBy { it.first.sortOrder }
                .thenBy { it.first.id }
        )
        .take(limit)
        .map { it.first }
        .toList()
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun WorkspaceInspectorPanel(
    selectedTask: TaskItem?,
    primaryTasks: List<TaskItem>,
    todayTasks: List<TaskItem>,
    eveningTasks: List<TaskItem>,
    allOpenTasks: List<TaskItem>,
    todayCalendarEvents: List<CalendarEventItem>,
    calendarEventsEnabled: Boolean,
    todayPlannedMinutes: Int,
    todayFreeMinutes: Int,
    completedTodayCount: Int,
    sevenDayWorkload: List<com.pv.thingsbook.domain.model.DayWorkloadSummary> = emptyList(),
    areas: List<Area>,
    projects: List<Project>,
    todayDate: LocalDate,
    locale: Locale,
    activeFocusTaskId: String?,
    onCloseInspector: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectTask: (String) -> Unit,
    onExpandTask: (String) -> Unit,
    onUpdateTaskStatus: (String, TaskStatus) -> Unit,
    onUpdateTaskTitle: (TaskItem, String) -> Unit,
    onUpdateTaskNotes: (TaskItem, String) -> Unit,
    onUpdateTaskSchedule: (TaskItem, ScheduleState, Long?) -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleMilestone: (String, Boolean) -> Unit,
    onToggleInProgress: (String) -> Unit = {},
    onUpdateDuration: (String, Int?) -> Unit,
    onUpdateEffort: (String, String?) -> Unit,
    onStartFocusTimer: (TaskItem) -> Unit,
    onResetActualMinutes: ((String) -> Unit)? = null,
    onAddChecklistItem: (String, String) -> Unit,
    onToggleChecklistItem: (String, Boolean) -> Unit,
    onDeleteChecklistItem: (String) -> Unit,
    onNavigateToProject: ((String) -> Unit)? = null,
    onNavigateToArea: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors

    Column(
        modifier = modifier
            .width(WORKSPACE_INSPECTOR_PANEL_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(colors.sidebarBackground.copy(alpha = 0.55f))
            // Consume stray clicks inside the Inspector surface so background canvas click doesn't deselect
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { }
            )
    ) {
        if (selectedTask != null) {
            SelectedTaskInspectorContent(
                selectedTask = selectedTask,
                areas = areas,
                projects = projects,
                todayDate = todayDate,
                locale = locale,
                activeFocusTaskId = activeFocusTaskId,
                onExpandTask = onExpandTask,
                onClearSelection = onClearSelection,
                onUpdateTaskStatus = onUpdateTaskStatus,
                onUpdateTaskTitle = onUpdateTaskTitle,
                onUpdateTaskNotes = onUpdateTaskNotes,
                onUpdateTaskSchedule = onUpdateTaskSchedule,
                onTogglePin = onTogglePin,
                onToggleMilestone = onToggleMilestone,
                onToggleInProgress = onToggleInProgress,
                onUpdateDuration = onUpdateDuration,
                onUpdateEffort = onUpdateEffort,
                onStartFocusTimer = onStartFocusTimer,
                onResetActualMinutes = onResetActualMinutes,
                onAddChecklistItem = onAddChecklistItem,
                onToggleChecklistItem = onToggleChecklistItem,
                onDeleteChecklistItem = onDeleteChecklistItem,
                onNavigateToProject = onNavigateToProject,
                onNavigateToArea = onNavigateToArea
            )
        } else {
            WorkspaceOverviewInspectorContent(
                primaryTasks = primaryTasks,
                todayTasks = todayTasks,
                eveningTasks = eveningTasks,
                allOpenTasks = allOpenTasks,
                todayCalendarEvents = todayCalendarEvents,
                calendarEventsEnabled = calendarEventsEnabled,
                todayPlannedMinutes = todayPlannedMinutes,
                todayFreeMinutes = todayFreeMinutes,
                completedTodayCount = completedTodayCount,
                sevenDayWorkload = sevenDayWorkload,
                todayDate = todayDate,
                locale = locale,
                onCloseInspector = onCloseInspector,
                onSelectTask = onSelectTask,
                onExpandTask = onExpandTask
            )
        }
    }
}

@Composable
private fun InspectorSoftBentoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ThingsTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.cardSurface,
        border = BorderStroke(0.75.dp, colors.hairlineDivider)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedTaskInspectorContent(
    selectedTask: TaskItem,
    areas: List<Area>,
    projects: List<Project>,
    todayDate: LocalDate,
    locale: Locale,
    activeFocusTaskId: String?,
    onExpandTask: (String) -> Unit,
    onClearSelection: () -> Unit,
    onUpdateTaskStatus: (String, TaskStatus) -> Unit,
    onUpdateTaskTitle: (TaskItem, String) -> Unit,
    onUpdateTaskNotes: (TaskItem, String) -> Unit,
    onUpdateTaskSchedule: (TaskItem, ScheduleState, Long?) -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleMilestone: (String, Boolean) -> Unit,
    onToggleInProgress: (String) -> Unit = {},
    onUpdateDuration: (String, Int?) -> Unit,
    onUpdateEffort: (String, String?) -> Unit,
    onStartFocusTimer: (TaskItem) -> Unit,
    onResetActualMinutes: ((String) -> Unit)? = null,
    onAddChecklistItem: (String, String) -> Unit,
    onToggleChecklistItem: (String, Boolean) -> Unit,
    onDeleteChecklistItem: (String) -> Unit,
    onNavigateToProject: ((String) -> Unit)?,
    onNavigateToArea: ((String) -> Unit)?
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()

    var titleDraft by remember(selectedTask.id) { mutableStateOf(selectedTask.title) }
    var isTitleFocused by remember(selectedTask.id) { mutableStateOf(false) }
    var notesDraft by remember(selectedTask.id) { mutableStateOf(selectedTask.notes) }
    var isNotesFocused by remember(selectedTask.id) { mutableStateOf(false) }
    var newChecklistDraft by remember(selectedTask.id) { mutableStateOf("") }
    var showDurationMenu by remember(selectedTask.id) { mutableStateOf(false) }
    var showEffortMenu by remember(selectedTask.id) { mutableStateOf(false) }

    val latestTask by rememberUpdatedState(selectedTask)
    val latestTitleDraft by rememberUpdatedState(titleDraft)
    val latestNotesDraft by rememberUpdatedState(notesDraft)
    val latestUpdateTitle by rememberUpdatedState(onUpdateTaskTitle)
    val latestUpdateNotes by rememberUpdatedState(onUpdateTaskNotes)

    LaunchedEffect(selectedTask.id, selectedTask.title, isTitleFocused) {
        if (!isTitleFocused && titleDraft != selectedTask.title) {
            titleDraft = selectedTask.title
        }
    }

    LaunchedEffect(selectedTask.id, selectedTask.notes, isNotesFocused) {
        if (!isNotesFocused && notesDraft != selectedTask.notes) {
            notesDraft = selectedTask.notes
        }
    }

    LaunchedEffect(selectedTask.id, titleDraft) {
        if (titleDraft != selectedTask.title) {
            delay(320L)
            if (titleDraft != latestTask.title) {
                latestUpdateTitle(latestTask.copy(notes = latestNotesDraft), titleDraft)
            }
        }
    }

    LaunchedEffect(selectedTask.id, notesDraft) {
        if (notesDraft != selectedTask.notes) {
            delay(360L)
            if (notesDraft != latestTask.notes) {
                latestUpdateNotes(latestTask.copy(title = latestTitleDraft), notesDraft)
            }
        }
    }

    DisposableEffect(selectedTask.id) {
        val taskSnapshot = selectedTask
        onDispose {
            if (latestTitleDraft != taskSnapshot.title) {
                latestUpdateTitle(latestTask.copy(notes = latestNotesDraft), latestTitleDraft)
            } else if (latestNotesDraft != taskSnapshot.notes) {
                latestUpdateNotes(latestTask.copy(title = latestTitleDraft), latestNotesDraft)
            }
        }
    }

    val todayStartMillis = remember(todayDate, zone) {
        todayDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val tomorrowDate = remember(todayDate) { todayDate.plusDays(1) }
    val tomorrowStartMillis = remember(tomorrowDate, zone) {
        tomorrowDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    val resolvedProject = remember(selectedTask.projectId, projects) {
        selectedTask.projectId?.let { pId -> projects.firstOrNull { it.id == pId && !it.isDeleted } }
    }
    val resolvedArea = remember(selectedTask.areaId, resolvedProject?.areaId, areas) {
        val targetAreaId = selectedTask.areaId ?: resolvedProject?.areaId
        targetAreaId?.let { aId -> areas.firstOrNull { it.id == aId && !it.isDeleted } }
    }

    // Mode A Header Bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (selectedTask.isMilestone) {
                ThingsMilestoneDiamondIcon(
                    filled = true,
                    tint = colors.milestoneViolet,
                    size = 13.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.VerticalSplit,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = stringResource(R.string.v30_inspector_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.primaryText,
                maxLines = 1
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val openDetailsLabel = stringResource(R.string.v30_open_details_action)
            ThingsHoverTooltip(text = openDetailsLabel) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpandTask(selectedTask.id)
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInFull,
                        contentDescription = openDetailsLabel,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            val deselectLabel = stringResource(R.string.context_deselect_task)
            ThingsHoverTooltip(text = deselectLabel) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClearSelection()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = deselectLabel,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }

    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Task Title & Toggles Soft Bento Card
        InspectorSoftBentoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThingsCheckbox(
                    status = selectedTask.status,
                    onStatusChange = { nextStatus ->
                        onUpdateTaskStatus(selectedTask.id, nextStatus)
                    },
                    isDashedSomeday = selectedTask.scheduleState == ScheduleState.SOMEDAY,
                    isMilestone = selectedTask.isMilestone,
                    isInProgress = selectedTask.isInProgress,
                    onToggleInProgress = if (selectedTask.status == TaskStatus.OPEN) {
                        { onToggleInProgress(selectedTask.id) }
                    } else {
                        null
                    },
                    size = 20.dp
                )

                BasicTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.5.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primaryText
                    ),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (titleDraft != selectedTask.title) {
                                onUpdateTaskTitle(selectedTask.copy(notes = notesDraft), titleDraft)
                            }
                        }
                    ),
                    cursorBrush = SolidColor(colors.thingsBlue),
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 3.dp)
                        .onFocusChanged { focusState ->
                            val wasFocused = isTitleFocused
                            isTitleFocused = focusState.isFocused
                            if (wasFocused && !focusState.isFocused && titleDraft != selectedTask.title) {
                                onUpdateTaskTitle(selectedTask.copy(notes = notesDraft), titleDraft)
                            }
                        },
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (titleDraft.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.placeholder_todo_title),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 15.5.sp,
                                        lineHeight = 21.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = colors.tertiaryText
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectedTask.status == TaskStatus.OPEN) {
                    val wipActive = selectedTask.isInProgress
                    val wipTooltip = stringResource(
                        if (wipActive) R.string.v33_clear_in_progress else R.string.v33_mark_in_progress
                    )
                    ThingsHoverTooltip(text = wipTooltip) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleInProgress(selectedTask.id)
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (wipActive) colors.somedayWarmAmber.copy(alpha = 0.16f) else colors.tagPillBg.copy(alpha = 0.72f),
                            border = BorderStroke(
                                0.75.dp,
                                if (wipActive) colors.somedayWarmAmber.copy(alpha = 0.55f) else colors.hairlineDivider
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.5.dp, vertical = 4.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "◐ ${stringResource(R.string.v33_in_progress_badge)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (wipActive) colors.somedayWarmAmber else colors.secondaryText
                                )
                            }
                        }
                    }
                }

                val pinTooltip = stringResource(
                    if (selectedTask.isPinned) R.string.v25_unpin_task else R.string.v25_pin_task
                )
                ThingsHoverTooltip(text = pinTooltip) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTogglePin(selectedTask.id)
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedTask.isPinned) colors.thingsBlueSoft else colors.tagPillBg.copy(alpha = 0.72f),
                        border = BorderStroke(
                            0.75.dp,
                            if (selectedTask.isPinned) colors.thingsBlue.copy(alpha = 0.45f) else colors.hairlineDivider
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.5.dp, vertical = 4.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = pinTooltip,
                                tint = if (selectedTask.isPinned) colors.thingsBlue else colors.secondaryText,
                                modifier = Modifier.size(13.5.dp)
                            )
                            Text(
                                text = stringResource(R.string.v25_batch_pin),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selectedTask.isPinned) colors.thingsBlue else colors.secondaryText
                            )
                        }
                    }
                }

                val milestoneTooltip = stringResource(
                    if (selectedTask.isMilestone) R.string.v30_remove_milestone else R.string.v30_mark_as_milestone
                )
                ThingsHoverTooltip(text = milestoneTooltip) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleMilestone(selectedTask.id, !selectedTask.isMilestone)
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedTask.isMilestone) colors.milestoneVioletSoft else colors.tagPillBg.copy(alpha = 0.72f),
                        border = BorderStroke(
                            0.75.dp,
                            if (selectedTask.isMilestone) colors.milestoneViolet.copy(alpha = 0.55f) else colors.hairlineDivider
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.5.dp, vertical = 4.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ThingsMilestoneDiamondIcon(
                                filled = selectedTask.isMilestone,
                                tint = if (selectedTask.isMilestone) colors.milestoneViolet else colors.secondaryText,
                                size = 12.5.dp
                            )
                            Text(
                                text = stringResource(R.string.v30_milestone_label),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selectedTask.isMilestone) colors.milestoneViolet else colors.secondaryText
                            )
                        }
                    }
                }
            }
        }

        // 2. Schedule & Properties Soft Bento Card
        InspectorSoftBentoCard {
            Text(
                text = stringResource(R.string.action_when),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.secondaryText
            )

            val isScheduledTomorrow = remember(selectedTask.scheduleState, selectedTask.whenDate, tomorrowDate, zone) {
                selectedTask.scheduleState == ScheduleState.UPCOMING &&
                    selectedTask.whenDate?.let { ms ->
                        runCatching { Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }.getOrNull() == tomorrowDate
                    } == true
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InspectorWhenQuickPill(
                    label = stringResource(R.string.view_today),
                    icon = Icons.Outlined.WbSunny,
                    isSelected = selectedTask.scheduleState == ScheduleState.TODAY,
                    activeTint = colors.thingsBlue,
                    activeBg = colors.thingsBlueSoft,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onUpdateTaskSchedule(selectedTask, ScheduleState.TODAY, todayStartMillis)
                    }
                )

                InspectorWhenQuickPill(
                    label = stringResource(R.string.view_this_evening),
                    icon = Icons.Outlined.Bedtime,
                    isSelected = selectedTask.scheduleState == ScheduleState.THIS_EVENING,
                    activeTint = colors.eveningMoonIndigo,
                    activeBg = colors.thingsBlueSoft,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onUpdateTaskSchedule(selectedTask, ScheduleState.THIS_EVENING, todayStartMillis)
                    }
                )

                InspectorWhenQuickPill(
                    label = stringResource(R.string.date_tomorrow),
                    icon = Icons.Outlined.CalendarMonth,
                    isSelected = isScheduledTomorrow,
                    activeTint = colors.upcomingCoralRed,
                    activeBg = colors.thingsBlueSoft,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onUpdateTaskSchedule(selectedTask, ScheduleState.UPCOMING, tomorrowStartMillis)
                    }
                )

                InspectorWhenQuickPill(
                    label = stringResource(R.string.view_someday),
                    icon = Icons.Outlined.Cloud,
                    isSelected = selectedTask.scheduleState == ScheduleState.SOMEDAY,
                    activeTint = colors.somedayWarmAmber,
                    activeBg = colors.somedayWarmAmber.copy(alpha = 0.16f),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onUpdateTaskSchedule(selectedTask, ScheduleState.SOMEDAY, null)
                    }
                )
            }

            // Active Deadline, Custom Future Date, Timeblock, Duration, Effort, Container, and Tag Pills
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectedTask.scheduleState == ScheduleState.UPCOMING && !isScheduledTomorrow && selectedTask.whenDate != null) {
                    ThingsWhenBadge(
                        scheduleState = selectedTask.scheduleState,
                        whenDateEpochMillis = selectedTask.whenDate,
                        locale = locale,
                        onClick = { onExpandTask(selectedTask.id) }
                    )
                }

                selectedTask.deadline?.let { deadlineMs ->
                    ThingsDeadlineBadge(
                        deadlineEpochMillis = deadlineMs,
                        locale = locale,
                        onClick = { onExpandTask(selectedTask.id) }
                    )
                }

                selectedTask.scheduledStartMinutes?.let { startMins ->
                    ThingsTagPill(
                        title = TimeblockScheduleUtils.formatTimeblockBadge(startMins, selectedTask.durationMinutes),
                        containerColor = colors.thingsBlueSoft,
                        contentColor = colors.thingsBlue,
                        borderColor = colors.thingsBlue.copy(alpha = 0.35f),
                        onClick = { onExpandTask(selectedTask.id) }
                    )
                }

                // Duration Pill + Preset Dropdown
                Box {
                    val formattedDur = formatDurationCompact(selectedTask.durationMinutes)
                    val durLabel = if (formattedDur != null) {
                        "⏱ $formattedDur"
                    } else {
                        "⏱ ${stringResource(R.string.v30_duration_label)}"
                    }
                    ThingsTagPill(
                        title = durLabel,
                        containerColor = if (formattedDur != null) colors.thingsBlueSoft else colors.tagPillBg,
                        contentColor = if (formattedDur != null) colors.thingsBlue else colors.secondaryText,
                        borderColor = if (formattedDur != null) colors.thingsBlue.copy(alpha = 0.32f) else colors.hairlineDivider,
                        onClick = { showDurationMenu = true }
                    )

                    DropdownMenu(
                        expanded = showDurationMenu,
                        onDismissRequest = { showDurationMenu = false }
                    ) {
                        STANDALONE_DURATION_PRESETS.forEach { presetMins ->
                            val label = formatDurationCompact(presetMins)
                            DropdownMenuItem(
                                text = { Text("⏱ $label") },
                                onClick = {
                                    showDurationMenu = false
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateDuration(selectedTask.id, presetMins)
                                }
                            )
                        }
                        if (selectedTask.durationMinutes != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.v30_duration_clear)) },
                                onClick = {
                                    showDurationMenu = false
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateDuration(selectedTask.id, null)
                                }
                            )
                        }
                    }
                }

                val inspectorActualMins = selectedTask.actualMinutes ?: 0
                if (inspectorActualMins > 0) {
                    val actualFmt = formatDurationCompact(inspectorActualMins) ?: "${inspectorActualMins}m"
                    val estMins = selectedTask.durationMinutes?.takeIf { it > 0 }
                    val focusedLabel = if (estMins != null) {
                        stringResource(
                            R.string.v32_actual_vs_estimated,
                            actualFmt,
                            formatDurationCompact(estMins) ?: "${estMins}m"
                        )
                    } else {
                        stringResource(R.string.v32_actual_focused_short, actualFmt)
                    }
                    ThingsTagPill(
                        title = "⏱ $focusedLabel",
                        containerColor = colors.logbookGreen.copy(alpha = 0.14f),
                        contentColor = colors.logbookGreen,
                        borderColor = colors.logbookGreen.copy(alpha = 0.35f)
                    )
                }
                if (selectedTask.actualMinutes != null && onResetActualMinutes != null) {
                    val resetActualText = stringResource(R.string.v32_reset_actual_time)
                    ThingsHoverTooltip(text = resetActualText) {
                        ThingsTagPill(
                            title = "↺ $resetActualText",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onResetActualMinutes(selectedTask.id)
                            }
                        )
                    }
                }

                // Effort Pill + Preset Dropdown
                Box {
                    val normalizedEffort = EffortLevel.normalize(selectedTask.effortLevel)
                    val effortLabel = when (normalizedEffort) {
                        EffortLevel.QUICK_WIN -> "⚡ ${stringResource(R.string.v30_effort_quick_win)}"
                        EffortLevel.MEDIUM -> "◐ ${stringResource(R.string.v30_effort_medium)}"
                        EffortLevel.DEEP_FOCUS -> "🧠 ${stringResource(R.string.v30_effort_deep_focus)}"
                        else -> "⚡ ${stringResource(R.string.v30_effort_label)}"
                    }
                    ThingsTagPill(
                        title = effortLabel,
                        containerColor = if (normalizedEffort != null) colors.thingsBlueSoft else colors.tagPillBg,
                        contentColor = if (normalizedEffort != null) colors.thingsBlue else colors.secondaryText,
                        borderColor = if (normalizedEffort != null) colors.thingsBlue.copy(alpha = 0.32f) else colors.hairlineDivider,
                        onClick = { showEffortMenu = true }
                    )

                    DropdownMenu(
                        expanded = showEffortMenu,
                        onDismissRequest = { showEffortMenu = false }
                    ) {
                        val effortOptions = listOf(
                            null to stringResource(R.string.v30_effort_none),
                            EffortLevel.QUICK_WIN to "⚡ ${stringResource(R.string.v30_effort_quick_win)}",
                            EffortLevel.MEDIUM to "◐ ${stringResource(R.string.v30_effort_medium)}",
                            EffortLevel.DEEP_FOCUS to "🧠 ${stringResource(R.string.v30_effort_deep_focus)}"
                        )
                        effortOptions.forEach { (effKey, effText) ->
                            DropdownMenuItem(
                                text = { Text(effText) },
                                onClick = {
                                    showEffortMenu = false
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateEffort(selectedTask.id, effKey)
                                }
                            )
                        }
                    }
                }
            }

            // Area › Project Container Badge
            if (resolvedArea != null || resolvedProject != null) {
                val containerLabel = when {
                    resolvedArea != null && resolvedProject != null -> "${resolvedArea.title} › ${resolvedProject.title}"
                    resolvedProject != null -> resolvedProject.title
                    else -> resolvedArea?.title.orEmpty()
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            when {
                                resolvedProject != null && onNavigateToProject != null ->
                                    onNavigateToProject(resolvedProject.id)
                                resolvedArea != null && onNavigateToArea != null ->
                                    onNavigateToArea(resolvedArea.id)
                                else -> onExpandTask(selectedTask.id)
                            }
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = colors.tagPillBg.copy(alpha = 0.85f),
                    border = BorderStroke(0.75.dp, colors.hairlineDivider)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = containerLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Assigned Category / Tag Pills
            val visibleTags = remember(selectedTask.tags) {
                selectedTask.tags.filter { !it.isDeleted }
            }
            if (visibleTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    visibleTags.forEach { tag ->
                        ThingsTagPill(
                            title = tag.title,
                            onClick = { onExpandTask(selectedTask.id) }
                        )
                    }
                }
            }

            // 1-Click Start Focus Timer Button
            val isFocusActiveForTask = activeFocusTaskId == selectedTask.id
            val focusDurationMins = TimeblockScheduleUtils.resolveFocusDurationMinutes(selectedTask)
            val focusBtnLabel = if (isFocusActiveForTask) {
                stringResource(R.string.focus_timer_active_label)
            } else {
                "${stringResource(R.string.focus_timer_active_label)} (${formatDurationCompact(focusDurationMins)})"
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onStartFocusTimer(selectedTask)
                    },
                shape = RoundedCornerShape(10.dp),
                color = colors.thingsBlueSoft,
                border = BorderStroke(0.75.dp, colors.thingsBlue.copy(alpha = 0.42f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = stringResource(R.string.action_focus_timer),
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = focusBtnLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.thingsBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Checklist Soft Bento Card
        val activeChecklistItems = remember(selectedTask.checklistItems) {
            selectedTask.checklistItems
                .filter { !it.isDeleted }
                .sortedWith(compareBy<ChecklistItem> { it.sortOrder }.thenBy { it.createdAt })
        }
        InspectorSoftBentoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Checklist,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.action_checklist),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.secondaryText
                    )
                }
                if (activeChecklistItems.isNotEmpty()) {
                    val doneCount = activeChecklistItems.count { it.isCompleted }
                    Text(
                        text = "$doneCount/${activeChecklistItems.size}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (doneCount == activeChecklistItems.size) colors.logbookGreen else colors.secondaryText
                    )
                }
            }

            activeChecklistItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleChecklistItem(item.id, !item.isCompleted)
                        }
                        .padding(vertical = 3.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(if (item.isCompleted) colors.thingsBlue else Color.Transparent)
                            .border(
                                width = 1.5.dp,
                                color = if (item.isCompleted) colors.thingsBlue else colors.checkboxBorder,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isCompleted) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (item.isCompleted) colors.secondaryText else colors.primaryText,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDeleteChecklistItem(item.id)
                        },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.subtask_delete),
                            tint = colors.tertiaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            val submitNewChecklistItem = {
                val trimmed = newChecklistDraft.trim()
                if (trimmed.isNotEmpty()) {
                    onAddChecklistItem(selectedTask.id, trimmed)
                    newChecklistDraft = ""
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.tagPillBg.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable { submitNewChecklistItem() }
                )
                BasicTextField(
                    value = newChecklistDraft,
                    onValueChange = { next ->
                        if ('\n' in next) {
                            val clean = next.replace("\n", "").trim()
                            if (clean.isNotEmpty()) {
                                onAddChecklistItem(selectedTask.id, clean)
                            }
                            newChecklistDraft = ""
                        } else {
                            newChecklistDraft = next
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = colors.primaryText
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitNewChecklistItem() }),
                    cursorBrush = SolidColor(colors.thingsBlue),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (newChecklistDraft.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.placeholder_add_checklist_item),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = colors.tertiaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerField()
                        }
                    }
                )
            }
        }

        // 4. Notes Soft Bento Card
        InspectorSoftBentoCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.placeholder_notes),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.secondaryText
                )
            }

            BasicTextField(
                value = notesDraft,
                onValueChange = { notesDraft = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    color = colors.primaryText
                ),
                cursorBrush = SolidColor(colors.thingsBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .onFocusChanged { focusState ->
                        val wasFocused = isNotesFocused
                        isNotesFocused = focusState.isFocused
                        if (wasFocused && !focusState.isFocused && notesDraft != selectedTask.notes) {
                            onUpdateTaskNotes(selectedTask.copy(title = titleDraft), notesDraft)
                        }
                    },
                decorationBox = { innerNotesField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (notesDraft.isEmpty()) {
                            Text(
                                text = stringResource(R.string.placeholder_notes),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp
                                ),
                                color = colors.tertiaryText
                            )
                        }
                        innerNotesField()
                    }
                }
            )

            DetectedNotesLinksRow(notes = notesDraft)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InspectorWhenQuickPill(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeTint: Color,
    activeBg: Color,
    onClick: () -> Unit
) {
    val colors = ThingsTheme.colors
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (isSelected) activeBg else colors.tagPillBg,
        border = BorderStroke(
            width = 0.75.dp,
            color = if (isSelected) activeTint.copy(alpha = 0.48f) else colors.hairlineDivider
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) activeTint else colors.secondaryText,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) activeTint else colors.primaryText,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceOverviewInspectorContent(
    primaryTasks: List<TaskItem>,
    todayTasks: List<TaskItem>,
    eveningTasks: List<TaskItem>,
    allOpenTasks: List<TaskItem>,
    todayCalendarEvents: List<CalendarEventItem>,
    calendarEventsEnabled: Boolean,
    todayPlannedMinutes: Int,
    todayFreeMinutes: Int,
    completedTodayCount: Int,
    sevenDayWorkload: List<com.pv.thingsbook.domain.model.DayWorkloadSummary> = emptyList(),
    todayDate: LocalDate,
    locale: Locale,
    onCloseInspector: () -> Unit,
    onSelectTask: (String) -> Unit,
    onExpandTask: (String) -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()

    val combinedTodayOpenTasks = remember(todayTasks, eveningTasks) {
        (todayTasks + eveningTasks)
            .distinctBy { it.id }
            .filter { !it.isDeleted && it.status == TaskStatus.OPEN }
    }
    val openTodayCount = combinedTodayOpenTasks.size
    val totalTodayTrackCount = openTodayCount + completedTodayCount
    val todayProgressFraction = if (totalTodayTrackCount <= 0) {
        0f
    } else {
        (completedTodayCount.toFloat() / totalTodayTrackCount.toFloat()).coerceIn(0f, 1f)
    }

    val activeCalendarEvents = remember(calendarEventsEnabled, todayCalendarEvents) {
        if (calendarEventsEnabled) todayCalendarEvents else emptyList()
    }
    val todaySchedule = remember(combinedTodayOpenTasks, activeCalendarEvents, zone) {
        TimeblockScheduleUtils.buildTodayTimeline(
            todayTasks = combinedTodayOpenTasks,
            todayCalendarEvents = activeCalendarEvents,
            zoneId = zone
        )
    }

    val upcomingDeadlinesAndMilestones = remember(allOpenTasks, todayDate, zone) {
        computeUpcomingDeadlinesAndMilestones(
            allOpenTasks = allOpenTasks,
            todayDate = todayDate,
            zoneId = zone,
            limit = 8
        )
    }

    val pinnedInCurrentView = remember(primaryTasks) {
        primaryTasks.filter { !it.isDeleted && it.isPinned }
    }

    // Mode B Header Bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.VerticalSplit,
                contentDescription = null,
                tint = colors.thingsBlue,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.v30_inspector_overview_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.primaryText,
                maxLines = 1
            )
        }

        val toggleLabel = stringResource(R.string.v30_inspector_toggle_tooltip)
        ThingsHoverTooltip(text = toggleLabel) {
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCloseInspector()
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = toggleLabel,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(16.5.dp)
                )
            }
        }
    }

    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Today Capacity & Progress Soft Bento Card
        InspectorSoftBentoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmartViewIcon(
                        viewType = SmartViewType.TODAY,
                        isSelected = true,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = stringResource(R.string.view_today),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText
                    )
                }

                val plannedFmt = if (todayPlannedMinutes > 0) formatDurationCompact(todayPlannedMinutes) else "0m"
                val freeFmt = if (todayFreeMinutes > 0) formatDurationCompact(todayFreeMinutes) else "0m"
                val capacityLabel = if (calendarEventsEnabled) {
                    stringResource(R.string.v30_today_capacity_summary, plannedFmt, freeFmt)
                } else {
                    stringResource(R.string.v30_today_planned_only, plannedFmt)
                }
                Text(
                    text = capacityLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Smooth rounded visual progress track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.tagPillBg)
            ) {
                if (todayProgressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(todayProgressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (todayProgressFraction >= 1f) colors.logbookGreen else colors.thingsBlue
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(9.dp),
                    color = colors.tagPillBg.copy(alpha = 0.72f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$openTodayCount",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.primaryText
                        )
                        Text(
                            text = stringResource(R.string.settings_stat_open),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(9.dp),
                    color = colors.tagPillBg.copy(alpha = 0.72f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$completedTodayCount",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (completedTodayCount > 0) colors.logbookGreen else colors.secondaryText
                        )
                        Text(
                            text = stringResource(R.string.settings_stat_completed),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (sevenDayWorkload.isNotEmpty()) {
            SevenDayWorkloadStripCard(
                forecast = sevenDayWorkload,
                locale = locale,
                compact = true
            )
        }

        // 2. Today's Schedule Mini-Timeline Soft Bento Card
        InspectorSoftBentoCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.v2_timeline_view),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.primaryText
                )
            }

            if (todaySchedule.allDayEvents.isEmpty() && todaySchedule.timedEntries.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_today),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                    color = colors.tertiaryText
                )
            } else {
                todaySchedule.allDayEvents.take(3).forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.tagPillBg.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Event,
                            contentDescription = null,
                            tint = colors.eveningMoonIndigo,
                            modifier = Modifier.size(13.5.dp)
                        )
                        Text(
                            text = event.title.ifBlank { stringResource(R.string.calendar_untitled_event) },
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.calendar_all_day),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )
                    }
                }

                todaySchedule.timedEntries.take(8).forEach { entry ->
                    when (entry) {
                        is TodayTimelineEntry.CalendarBlock -> {
                            val startStr = TimeblockScheduleUtils.formatMinutesAsHm(entry.startMinutes)
                            val endStr = TimeblockScheduleUtils.formatMinutesAsHm(entry.endMinutes)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (entry.hasConflict) colors.deadlineSoftBg else colors.tagPillBg.copy(alpha = 0.55f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text(
                                    text = startStr,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.secondaryText,
                                    modifier = Modifier.width(38.dp)
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Event,
                                    contentDescription = null,
                                    tint = colors.eveningMoonIndigo,
                                    modifier = Modifier.size(13.5.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.event.title.ifBlank { stringResource(R.string.calendar_untitled_event) },
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$startStr–$endStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.secondaryText
                                    )
                                }
                            }
                        }

                        is TodayTimelineEntry.TaskBlock -> {
                            var lastTapMs by remember(entry.task.id) { mutableLongStateOf(0L) }
                            val startStr = TimeblockScheduleUtils.formatMinutesAsHm(entry.startMinutes)
                            val endStr = TimeblockScheduleUtils.formatMinutesAsHm(entry.endMinutes)
                            val durStr = formatDurationCompact(entry.durationMinutes)
                            val defaultTodoTitle = stringResource(R.string.placeholder_todo_title)
                            val taskTooltip = remember(
                                entry.task.title,
                                entry.task.isMilestone,
                                startStr,
                                endStr,
                                durStr,
                                defaultTodoTitle
                            ) {
                                val cleanTitle = entry.task.title.ifBlank { defaultTodoTitle }
                                val titled = if (entry.task.isMilestone) "◆ $cleanTitle" else cleanTitle
                                "$titled · $startStr – $endStr · $durStr"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .thingsTimelineMouseHoverTooltip(text = taskTooltip)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (entry.hasConflict) colors.deadlineSoftBg else colors.thingsBlueSoft.copy(alpha = 0.55f)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            val now = SystemClock.uptimeMillis()
                                            if ((now - lastTapMs) <= 380L) {
                                                lastTapMs = 0L
                                                onExpandTask(entry.task.id)
                                            } else {
                                                lastTapMs = now
                                                onSelectTask(entry.task.id)
                                            }
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = startStr,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.thingsBlue,
                                    modifier = Modifier.width(38.dp)
                                )
                                if (entry.task.isMilestone) {
                                    ThingsMilestoneDiamondIcon(
                                        filled = true,
                                        tint = colors.milestoneViolet,
                                        size = 11.5.dp
                                    )
                                }
                                Text(
                                    text = entry.task.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = colors.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatDurationCompact(entry.durationMinutes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondaryText
                                )
                                IconButton(
                                    onClick = { onExpandTask(entry.task.id) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.OpenInFull,
                                        contentDescription = stringResource(R.string.v30_open_details_action),
                                        tint = colors.secondaryText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Upcoming Deadlines & Milestones Soft Bento Card
        val shortDateFormatter = remember(locale) {
            runCatching { DateTimeFormatter.ofPattern("d MMM", locale) }
                .getOrDefault(DateTimeFormatter.ISO_LOCAL_DATE)
        }
        val todayStartMs = remember(todayDate, zone) {
            todayDate.atStartOfDay(zone).toInstant().toEpochMilli()
        }

        InspectorSoftBentoCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThingsMilestoneDiamondIcon(
                    filled = true,
                    tint = colors.milestoneViolet,
                    size = 12.5.dp
                )
                Text(
                    text = stringResource(R.string.v30_inspector_upcoming_deadlines),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (upcomingDeadlinesAndMilestones.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_view),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                    color = colors.tertiaryText
                )
            } else {
                upcomingDeadlinesAndMilestones.forEach { itemTask ->
                    var lastTapMs by remember(itemTask.id) { mutableLongStateOf(0L) }
                    val targetMs = resolveInspectorTargetMillis(itemTask, todayStartMs) ?: todayStartMs
                    val targetDate = runCatching {
                        Instant.ofEpochMilli(targetMs).atZone(zone).toLocalDate()
                    }.getOrDefault(todayDate)
                    val daysDiff = runCatching {
                        ChronoUnit.DAYS.between(todayDate, targetDate).coerceIn(-9999L, 9999L).toInt()
                    }.getOrDefault(0)
                    val isUrgent = daysDiff <= 2
                    val badgeLabel = when {
                        daysDiff == 0 -> stringResource(R.string.deadline_today)
                        daysDiff < 0 -> stringResource(R.string.deadline_days_overdue, -daysDiff)
                        daysDiff <= 7 -> stringResource(R.string.deadline_days_left, daysDiff)
                        else -> runCatching { targetDate.format(shortDateFormatter) }.getOrDefault("${daysDiff}d")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.tagPillBg.copy(alpha = 0.52f))
                            .combinedClickable(
                                onClick = {
                                    val now = SystemClock.uptimeMillis()
                                    if ((now - lastTapMs) <= 380L) {
                                        lastTapMs = 0L
                                        onExpandTask(itemTask.id)
                                    } else {
                                        lastTapMs = now
                                        onSelectTask(itemTask.id)
                                    }
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (itemTask.isMilestone) {
                            ThingsMilestoneDiamondIcon(
                                filled = true,
                                tint = colors.milestoneViolet,
                                size = 12.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Flag,
                                contentDescription = null,
                                tint = if (isUrgent) colors.deadlineUrgentRed else colors.secondaryText,
                                modifier = Modifier.size(13.5.dp)
                            )
                        }

                        Text(
                            text = itemTask.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    when {
                                        isUrgent -> colors.deadlineSoftBg
                                        itemTask.isMilestone -> colors.milestoneVioletSoft
                                        else -> colors.tagPillBg
                                    }
                                )
                                .padding(horizontal = 6.5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = when {
                                    isUrgent -> colors.deadlineUrgentRed
                                    itemTask.isMilestone -> colors.milestoneViolet
                                    else -> colors.secondaryText
                                },
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { onExpandTask(itemTask.id) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInFull,
                                contentDescription = stringResource(R.string.v30_open_details_action),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Pinned Tasks Soft Bento Card (shown when current view has pinned tasks)
        if (pinnedInCurrentView.isNotEmpty()) {
            InspectorSoftBentoCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(14.5.dp)
                    )
                    Text(
                        text = stringResource(R.string.v25_pinned_badge),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText
                    )
                }

                pinnedInCurrentView.take(8).forEach { pinnedTask ->
                    var lastTapMs by remember(pinnedTask.id) { mutableLongStateOf(0L) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.thingsBlueSoft.copy(alpha = 0.42f))
                            .combinedClickable(
                                onClick = {
                                    val now = SystemClock.uptimeMillis()
                                    if ((now - lastTapMs) <= 380L) {
                                        lastTapMs = 0L
                                        onExpandTask(pinnedTask.id)
                                    } else {
                                        lastTapMs = now
                                        onSelectTask(pinnedTask.id)
                                    }
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (pinnedTask.isMilestone) {
                            ThingsMilestoneDiamondIcon(
                                filled = true,
                                tint = colors.milestoneViolet,
                                size = 11.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(12.5.dp)
                            )
                        }
                        Text(
                            text = pinnedTask.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onExpandTask(pinnedTask.id) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInFull,
                                contentDescription = stringResource(R.string.v30_open_details_action),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
