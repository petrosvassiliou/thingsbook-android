package com.pv.thingsbook.ui.components

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pv.thingsbook.R
import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.CalendarEventItem
import com.pv.thingsbook.domain.model.ChecklistItem
import com.pv.thingsbook.domain.model.EffortLevel
import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.RecurrenceRule
import com.pv.thingsbook.domain.model.STANDALONE_DURATION_PRESETS
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.model.TimeblockScheduleUtils
import com.pv.thingsbook.domain.model.TodayTimelineEntry
import com.pv.thingsbook.domain.model.formatDurationCompact
import com.pv.thingsbook.domain.model.isTaskActivelyBlocked
import com.pv.thingsbook.domain.parser.ParsedSmartSyntax
import com.pv.thingsbook.domain.parser.SmartSyntaxParser
import com.pv.thingsbook.ui.theme.ThingsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

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
    dragController: ProjectDragDropController? = null,
    onJumpToWorkloadDay: ((index: Int, day: com.pv.thingsbook.domain.model.DayWorkloadSummary) -> Unit)? = null,
    areas: List<Area>,
    projects: List<Project>,
    allHeadings: List<Heading> = emptyList(),
    allTags: List<Tag> = emptyList(),
    tagSortMode: String = "alpha",
    tagUsageCounts: Map<String, Int> = emptyMap(),
    openTaskIds: Set<String> = emptySet(),
    todayDate: LocalDate,
    locale: Locale,
    activeFocusTaskId: String?,
    isGeneratingMagicBreakdown: Boolean = false,
    onCloseInspector: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectTask: (String) -> Unit,
    onExpandTask: (String) -> Unit,
    onUpdateTaskStatus: (String, TaskStatus) -> Unit,
    onUpdateTaskTitle: (TaskItem, String) -> Unit,
    onUpdateTaskNotes: (TaskItem, String) -> Unit,
    onUpdateTaskSchedule: (TaskItem, ScheduleState, Long?) -> Unit,
    onUpdateTaskScheduleWithReminder: ((TaskItem, ScheduleState, Long?, Long?) -> Unit)? = null,
    onUpdateTaskDeadline: (TaskItem, Long?) -> Unit = { _, _ -> },
    onUpdateTaskRecurrence: (String, RecurrenceRule?) -> Unit = { _, _ -> },
    onUpdateTimeblock: (String, Int?, Int?) -> Unit = { _, _, _ -> },
    onTogglePin: (String) -> Unit,
    onToggleMilestone: (String, Boolean) -> Unit,
    onToggleInProgress: (String) -> Unit = {},
    onUpdateDuration: (String, Int?) -> Unit,
    onUpdateEffort: (String, String?) -> Unit,
    onUpdateBlockedBy: (String, List<String>) -> Unit = { _, _ -> },
    onMoveTaskToContainer: (String, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onUpdateTaskTags: (TaskItem, List<String>) -> Unit = { _, _ -> },
    onCreateAndAssignTag: (TaskItem, String) -> Unit = { _, _ -> },
    onOpenFullTagManager: () -> Unit = {},
    onStartFocusTimer: (TaskItem) -> Unit,
    onResetActualMinutes: ((String) -> Unit)? = null,
    onAddChecklistItem: (String, String) -> Unit,
    onInsertChecklistItemAfter: ((taskId: String, afterItemId: String?, newItemId: String, title: String) -> Unit)? = null,
    onToggleChecklistItem: (String, Boolean) -> Unit,
    onRenameChecklistItem: (String, String) -> Unit = { _, _ -> },
    onMoveChecklistItem: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onDeleteChecklistItem: (String) -> Unit,
    onTriggerGeminiMagicBreakdown: (TaskItem) -> Unit = {},
    onApplySmartSyntax: ((taskId: String, parsed: ParsedSmartSyntax, latestNotes: String?) -> Unit)? = null,
    onAddTaskAttachment: ((taskId: String, fileName: String, mimeType: String, bytes: ByteArray) -> Unit)? = null,
    onDeleteAttachment: (String) -> Unit = {},
    onDuplicateTask: (String) -> Unit = {},
    onDeleteTask: (String) -> Unit = {},
    onShowMessage: ((String) -> Unit)? = null,
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
                allOpenTasks = allOpenTasks,
                openTaskIds = openTaskIds,
                areas = areas,
                projects = projects,
                allHeadings = allHeadings,
                allTags = allTags,
                tagSortMode = tagSortMode,
                tagUsageCounts = tagUsageCounts,
                todayDate = todayDate,
                locale = locale,
                activeFocusTaskId = activeFocusTaskId,
                isGeneratingMagicBreakdown = isGeneratingMagicBreakdown,
                onExpandTask = onExpandTask,
                onClearSelection = onClearSelection,
                onUpdateTaskStatus = onUpdateTaskStatus,
                onUpdateTaskTitle = onUpdateTaskTitle,
                onUpdateTaskNotes = onUpdateTaskNotes,
                onUpdateTaskSchedule = onUpdateTaskSchedule,
                onUpdateTaskScheduleWithReminder = onUpdateTaskScheduleWithReminder,
                onUpdateTaskDeadline = onUpdateTaskDeadline,
                onUpdateTaskRecurrence = onUpdateTaskRecurrence,
                onUpdateTimeblock = onUpdateTimeblock,
                onTogglePin = onTogglePin,
                onToggleMilestone = onToggleMilestone,
                onToggleInProgress = onToggleInProgress,
                onUpdateDuration = onUpdateDuration,
                onUpdateEffort = onUpdateEffort,
                onUpdateBlockedBy = onUpdateBlockedBy,
                onMoveTaskToContainer = onMoveTaskToContainer,
                onUpdateTaskTags = onUpdateTaskTags,
                onCreateAndAssignTag = onCreateAndAssignTag,
                onOpenFullTagManager = onOpenFullTagManager,
                onStartFocusTimer = onStartFocusTimer,
                onResetActualMinutes = onResetActualMinutes,
                onAddChecklistItem = onAddChecklistItem,
                onInsertChecklistItemAfter = onInsertChecklistItemAfter,
                onToggleChecklistItem = onToggleChecklistItem,
                onRenameChecklistItem = onRenameChecklistItem,
                onMoveChecklistItem = onMoveChecklistItem,
                onDeleteChecklistItem = onDeleteChecklistItem,
                onTriggerGeminiMagicBreakdown = onTriggerGeminiMagicBreakdown,
                onApplySmartSyntax = onApplySmartSyntax,
                onAddTaskAttachment = onAddTaskAttachment,
                onDeleteAttachment = onDeleteAttachment,
                onDuplicateTask = onDuplicateTask,
                onDeleteTask = onDeleteTask,
                onShowMessage = onShowMessage,
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
                dragController = dragController,
                onJumpToWorkloadDay = onJumpToWorkloadDay,
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
    allOpenTasks: List<TaskItem>,
    openTaskIds: Set<String>,
    areas: List<Area>,
    projects: List<Project>,
    allHeadings: List<Heading>,
    allTags: List<Tag>,
    tagSortMode: String,
    tagUsageCounts: Map<String, Int>,
    todayDate: LocalDate,
    locale: Locale,
    activeFocusTaskId: String?,
    isGeneratingMagicBreakdown: Boolean,
    onExpandTask: (String) -> Unit,
    onClearSelection: () -> Unit,
    onUpdateTaskStatus: (String, TaskStatus) -> Unit,
    onUpdateTaskTitle: (TaskItem, String) -> Unit,
    onUpdateTaskNotes: (TaskItem, String) -> Unit,
    onUpdateTaskSchedule: (TaskItem, ScheduleState, Long?) -> Unit,
    onUpdateTaskScheduleWithReminder: ((TaskItem, ScheduleState, Long?, Long?) -> Unit)?,
    onUpdateTaskDeadline: (TaskItem, Long?) -> Unit,
    onUpdateTaskRecurrence: (String, RecurrenceRule?) -> Unit,
    onUpdateTimeblock: (String, Int?, Int?) -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleMilestone: (String, Boolean) -> Unit,
    onToggleInProgress: (String) -> Unit = {},
    onUpdateDuration: (String, Int?) -> Unit,
    onUpdateEffort: (String, String?) -> Unit,
    onUpdateBlockedBy: (String, List<String>) -> Unit,
    onMoveTaskToContainer: (String, String?, String?, String?) -> Unit,
    onUpdateTaskTags: (TaskItem, List<String>) -> Unit,
    onCreateAndAssignTag: (TaskItem, String) -> Unit,
    onOpenFullTagManager: () -> Unit,
    onStartFocusTimer: (TaskItem) -> Unit,
    onResetActualMinutes: ((String) -> Unit)? = null,
    onAddChecklistItem: (String, String) -> Unit,
    onInsertChecklistItemAfter: ((taskId: String, afterItemId: String?, newItemId: String, title: String) -> Unit)?,
    onToggleChecklistItem: (String, Boolean) -> Unit,
    onRenameChecklistItem: (String, String) -> Unit,
    onMoveChecklistItem: (String, String, Boolean) -> Unit,
    onDeleteChecklistItem: (String) -> Unit,
    onTriggerGeminiMagicBreakdown: (TaskItem) -> Unit,
    onApplySmartSyntax: ((taskId: String, parsed: ParsedSmartSyntax, latestNotes: String?) -> Unit)?,
    onAddTaskAttachment: ((taskId: String, fileName: String, mimeType: String, bytes: ByteArray) -> Unit)?,
    onDeleteAttachment: (String) -> Unit,
    onDuplicateTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onShowMessage: ((String) -> Unit)?,
    onNavigateToProject: ((String) -> Unit)?,
    onNavigateToArea: ((String) -> Unit)?
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()

    var titleDraft by remember(selectedTask.id) { mutableStateOf(selectedTask.title) }
    var lastAppliedSmartTitle by remember(selectedTask.id) { mutableStateOf<String?>(null) }
    var isTitleFocused by remember(selectedTask.id) { mutableStateOf(false) }
    var notesDraft by remember(selectedTask.id) { mutableStateOf(selectedTask.notes) }
    var isNotesFocused by remember(selectedTask.id) { mutableStateOf(false) }
    var newChecklistField by remember(selectedTask.id) {
        mutableStateOf(TextFieldValue(text = "", selection = TextRange(0)))
    }
    val newChecklistFocusRequester = remember(selectedTask.id) { FocusRequester() }
    var pendingAutoFocusSubtaskId by remember(selectedTask.id) { mutableStateOf<String?>(null) }

    var showMoreActionsMenu by remember(selectedTask.id) { mutableStateOf(false) }
    var showDurationMenu by remember(selectedTask.id) { mutableStateOf(false) }
    var showCustomDurationDialog by remember(selectedTask.id) { mutableStateOf(false) }
    var showEffortMenu by remember(selectedTask.id) { mutableStateOf(false) }
    var showWhenDialog by remember(selectedTask.id) { mutableStateOf(false) }
    var showDeadlineDialog by remember(selectedTask.id) { mutableStateOf(false) }
    var showRepeatDialog by remember(selectedTask.id) { mutableStateOf(false) }
    var showTimeblockDialog by remember(selectedTask.id) { mutableStateOf(false) }
    var showDependenciesDialog by remember(selectedTask.id) { mutableStateOf(false) }
    var showMoveDialog by remember(selectedTask.id) { mutableStateOf(false) }
    var showTagPickerDialog by remember(selectedTask.id) { mutableStateOf(false) }

    val latestTask by rememberUpdatedState(selectedTask)
    val latestTitleDraft by rememberUpdatedState(titleDraft)
    val latestAppliedSmartTitle by rememberUpdatedState(lastAppliedSmartTitle)
    val latestNotesDraft by rememberUpdatedState(notesDraft)
    val latestUpdateTitle by rememberUpdatedState(onUpdateTaskTitle)
    val latestUpdateNotes by rememberUpdatedState(onUpdateTaskNotes)

    val notifyInspectorMessage: (String) -> Unit = { msg ->
        if (onShowMessage != null) {
            onShowMessage(msg)
        } else {
            runCatching { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    val tooLargeMsg = stringResource(R.string.attachment_too_large)
    val importErrorMsg = stringResource(R.string.attachment_import_error)
    val voicePromptMsg = stringResource(R.string.voice_capture_prompt)
    val voiceUnavailableMsg = stringResource(R.string.voice_not_available)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val taskIdSnapshot = latestTask.id
        if (uri != null && onAddTaskAttachment != null) {
            scope.launch {
                when (val outcome = processPickedAttachmentUri(context, uri)) {
                    is AttachmentImportOutcome.Success -> {
                        onAddTaskAttachment(
                            taskIdSnapshot,
                            outcome.result.fileName,
                            outcome.result.mimeType,
                            outcome.result.bytes
                        )
                    }
                    AttachmentImportOutcome.TooLarge -> notifyInspectorMessage(tooLargeMsg)
                    AttachmentImportOutcome.Error -> notifyInspectorMessage(importErrorMsg)
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val taskIdSnapshot = latestTask.id
        if (bitmap != null && onAddTaskAttachment != null) {
            val processed = processCapturedPhotoBitmap(bitmap)
            onAddTaskAttachment(taskIdSnapshot, processed.fileName, processed.mimeType, processed.bytes)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                if (latestTitleDraft.isBlank()) {
                    val sanitizedSpoken = spoken.replace("\r", "").replace("\n", " ").trim()
                    titleDraft = sanitizedSpoken
                    latestUpdateTitle(latestTask.copy(notes = latestNotesDraft), sanitizedSpoken)
                } else {
                    val updatedNotes = if (latestNotesDraft.isBlank()) {
                        spoken
                    } else {
                        "${latestNotesDraft.trimEnd()}\n$spoken"
                    }
                    notesDraft = updatedNotes
                    latestUpdateNotes(latestTask.copy(title = latestTitleDraft), updatedNotes)
                }
            }
        }
    }

    val launchVoiceDictation = {
        val launched = runCatching {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PROMPT, voicePromptMsg)
            }
            speechLauncher.launch(intent)
            true
        }.getOrDefault(false)
        if (!launched) {
            notifyInspectorMessage(voiceUnavailableMsg)
        }
    }

    LaunchedEffect(selectedTask.id, selectedTask.title, isTitleFocused) {
        if (!isTitleFocused && titleDraft != selectedTask.title) {
            titleDraft = selectedTask.title
        }
        if (lastAppliedSmartTitle != null && selectedTask.title == lastAppliedSmartTitle) {
            lastAppliedSmartTitle = null
        }
    }

    LaunchedEffect(selectedTask.id, selectedTask.notes, isNotesFocused) {
        if (!isNotesFocused && notesDraft != selectedTask.notes) {
            notesDraft = selectedTask.notes
        }
    }

    LaunchedEffect(selectedTask.id, titleDraft) {
        if (titleDraft != selectedTask.title && titleDraft != lastAppliedSmartTitle) {
            delay(320L)
            if (titleDraft != latestTask.title && titleDraft != latestAppliedSmartTitle) {
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
            if (latestTitleDraft != taskSnapshot.title && latestTitleDraft != latestAppliedSmartTitle) {
                latestUpdateTitle(latestTask.copy(notes = latestNotesDraft), latestTitleDraft)
            } else if (latestNotesDraft != taskSnapshot.notes) {
                latestUpdateNotes(latestTask.copy(title = latestTitleDraft), latestNotesDraft)
            }
        }
    }

    val todayStartMillis = remember(todayDate, zone) {
        todayDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    val resolvedProject = remember(selectedTask.projectId, projects) {
        selectedTask.projectId?.let { pId -> projects.firstOrNull { it.id == pId && !it.isDeleted } }
    }
    val resolvedArea = remember(selectedTask.areaId, resolvedProject?.areaId, areas) {
        val targetAreaId = selectedTask.areaId ?: resolvedProject?.areaId
        targetAreaId?.let { aId -> areas.firstOrNull { it.id == aId && !it.isDeleted } }
    }
    val resolvedHeading = remember(selectedTask.headingId, selectedTask.projectId, allHeadings) {
        selectedTask.headingId?.let { hId ->
            allHeadings.firstOrNull { h ->
                h.id == hId && !h.isDeleted && (selectedTask.projectId == null || h.projectId == selectedTask.projectId)
            }
        }
    }

    val parsedInspectorSmartSyntax = remember(titleDraft, allTags, projects, areas, locale) {
        SmartSyntaxParser.parse(
            rawTitle = titleDraft,
            tags = allTags,
            projects = projects,
            areas = areas,
            locale = locale
        )
    }

    val applyInspectorSmartSyntaxNow: (ParsedSmartSyntax) -> Unit = { parsed ->
        val nextTitle = parsed.cleanTitle.ifBlank { titleDraft.trim() }
        lastAppliedSmartTitle = nextTitle
        titleDraft = nextTitle
        if (onApplySmartSyntax != null) {
            onApplySmartSyntax(selectedTask.id, parsed, notesDraft)
        } else {
            onUpdateTaskTitle(selectedTask.copy(notes = notesDraft), nextTitle)
            if (parsed.scheduleState != null) {
                onUpdateTaskSchedule(selectedTask, parsed.scheduleState, parsed.whenDate)
            }
            if (parsed.deadline != null) {
                onUpdateTaskDeadline(selectedTask, parsed.deadline)
            }
            if (parsed.durationMinutes != null) {
                onUpdateDuration(selectedTask.id, parsed.durationMinutes)
            }
            if (parsed.isMilestone && !selectedTask.isMilestone) {
                onToggleMilestone(selectedTask.id, true)
            }
            if (parsed.isInProgress && !selectedTask.isInProgress) {
                onToggleInProgress(selectedTask.id)
            }
            if (parsed.matchedProject != null || parsed.matchedArea != null) {
                val pId = parsed.matchedProject?.id ?: selectedTask.projectId
                val hId = if (pId == selectedTask.projectId) selectedTask.headingId else null
                val aId = parsed.matchedArea?.id ?: parsed.matchedProject?.areaId ?: selectedTask.areaId
                onMoveTaskToContainer(selectedTask.id, pId, hId, aId)
            }
            if (parsed.matchedTagIds.isNotEmpty()) {
                val mergedTagIds = (selectedTask.tags.map { it.id } + parsed.matchedTagIds).distinct()
                onUpdateTaskTags(selectedTask, mergedTagIds)
            }
        }
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
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f)
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val voiceLabel = stringResource(R.string.voice_capture_btn)
            ThingsHoverTooltip(text = voiceLabel) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        launchVoiceDictation()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = voiceLabel,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

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

            Box {
                val moreLabel = stringResource(R.string.action_more_options)
                ThingsHoverTooltip(text = moreLabel) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showMoreActionsMenu = true
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = moreLabel,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMoreActionsMenu,
                    onDismissRequest = { showMoreActionsMenu = false },
                    modifier = Modifier.background(colors.cardElevatedSurface)
                ) {
                    val fallbackCopyTitle = stringResource(R.string.placeholder_todo_title)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_copy_todo),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primaryText
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        onClick = {
                            showMoreActionsMenu = false
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val latestSnapshot = selectedTask.copy(
                                title = titleDraft,
                                notes = notesDraft
                            )
                            clipboardManager.setText(
                                AnnotatedString(
                                    formatTaskMarkdownForClipboard(latestSnapshot, fallbackCopyTitle)
                                )
                            )
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_duplicate_todo),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primaryText
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        onClick = {
                            showMoreActionsMenu = false
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDuplicateTask(selectedTask.id)
                        }
                    )

                    HorizontalDivider(color = colors.hairlineDivider)

                    val isAlreadyCanceled = selectedTask.status == TaskStatus.CANCELED
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(
                                    if (isAlreadyCanceled) R.string.action_reopen_todo else R.string.action_cancel_todo
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryText
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isAlreadyCanceled) Icons.Outlined.Check else Icons.Outlined.Close,
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        onClick = {
                            showMoreActionsMenu = false
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUpdateTaskStatus(
                                selectedTask.id,
                                if (isAlreadyCanceled) TaskStatus.OPEN else TaskStatus.CANCELED
                            )
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_delete),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.deadlineUrgentRed
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = colors.deadlineUrgentRed,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        onClick = {
                            showMoreActionsMenu = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val taskIdToDelete = selectedTask.id
                            onClearSelection()
                            onDeleteTask(taskIdToDelete)
                        }
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
                    onValueChange = { incoming ->
                        lastAppliedSmartTitle = null
                        titleDraft = incoming.replace("\r", "").replace("\n", " ")
                    },
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
                            if (wasFocused && !focusState.isFocused && titleDraft != selectedTask.title && titleDraft != lastAppliedSmartTitle) {
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

            if (parsedInspectorSmartSyntax.hasAnyMatch) {
                SmartSyntaxPreviewChipsRow(
                    parsed = parsedInspectorSmartSyntax,
                    showDetectedPrefix = true,
                    onApply = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        applyInspectorSmartSyntaxNow(parsedInspectorSmartSyntax)
                    }
                )
            }

            val ghostFlagText = colors.secondaryText.copy(alpha = 0.78f)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectedTask.status == TaskStatus.OPEN) {
                    val wipActive = selectedTask.isInProgress
                    val wipTooltip = "${stringResource(
                        if (wipActive) R.string.v33_clear_in_progress else R.string.v33_mark_in_progress
                    )} (Ctrl+Shift+I)"
                    ThingsHoverTooltip(text = wipTooltip) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleInProgress(selectedTask.id)
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (wipActive) colors.somedayWarmAmber.copy(alpha = 0.16f) else Color.Transparent,
                            border = BorderStroke(
                                0.75.dp,
                                if (wipActive) colors.somedayWarmAmber.copy(alpha = 0.55f) else Color.Transparent
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.5.dp, vertical = 4.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                ThingsInProgressIcon(
                                    active = wipActive,
                                    tint = if (wipActive) colors.somedayWarmAmber else ghostFlagText,
                                    size = 14.dp
                                )
                                Text(
                                    text = stringResource(
                                        if (wipActive) R.string.v33_in_progress_badge else R.string.v33_mark_in_progress
                                    ),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (wipActive) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (wipActive) colors.somedayWarmAmber else ghostFlagText
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
                        color = if (selectedTask.isPinned) colors.thingsBlueSoft else Color.Transparent,
                        border = BorderStroke(
                            0.75.dp,
                            if (selectedTask.isPinned) colors.thingsBlue.copy(alpha = 0.45f) else Color.Transparent
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
                                tint = if (selectedTask.isPinned) colors.thingsBlue else ghostFlagText,
                                modifier = Modifier.size(13.5.dp)
                            )
                            Text(
                                text = stringResource(R.string.v25_batch_pin),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selectedTask.isPinned) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selectedTask.isPinned) colors.thingsBlue else ghostFlagText
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
                        color = if (selectedTask.isMilestone) colors.milestoneVioletSoft else Color.Transparent,
                        border = BorderStroke(
                            0.75.dp,
                            if (selectedTask.isMilestone) colors.milestoneViolet.copy(alpha = 0.55f) else Color.Transparent
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.5.dp, vertical = 4.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ThingsMilestoneDiamondIcon(
                                filled = selectedTask.isMilestone,
                                tint = if (selectedTask.isMilestone) colors.milestoneViolet else ghostFlagText,
                                size = 12.5.dp
                            )
                            Text(
                                text = stringResource(R.string.v30_milestone_label),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selectedTask.isMilestone) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selectedTask.isMilestone) colors.milestoneViolet else ghostFlagText
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

                when {
                    selectedTask.scheduleState == ScheduleState.UPCOMING && selectedTask.whenDate != null -> {
                        ThingsWhenBadge(
                            scheduleState = selectedTask.scheduleState,
                            whenDateEpochMillis = selectedTask.whenDate,
                            locale = locale,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showWhenDialog = true
                            }
                        )
                    }
                    selectedTask.scheduleState == ScheduleState.SOMEDAY -> {
                        ThingsWhenBadge(
                            scheduleState = ScheduleState.SOMEDAY,
                            whenDateEpochMillis = null,
                            locale = locale,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showWhenDialog = true
                            }
                        )
                    }
                    else -> {
                        val pickDateLabel = stringResource(R.string.repeat_ends_pick_date)
                        val ghostMuted = colors.secondaryText.copy(alpha = 0.78f)
                        ThingsHoverTooltip(text = stringResource(R.string.context_when_date_reminder)) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showWhenDialog = true
                                    },
                                shape = RoundedCornerShape(50),
                                color = Color.Transparent,
                                border = BorderStroke(width = 0.75.dp, color = Color.Transparent)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = pickDateLabel,
                                        tint = ghostMuted,
                                        modifier = Modifier.size(13.5.dp)
                                    )
                                    Text(
                                        text = pickDateLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = ghostMuted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Reminder, Deadline, Timeblock, Repeat, Duration, Effort & Dependencies Pills
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedTask.reminderTime?.let { rt ->
                    ThingsReminderBadge(
                        reminderEpochMillis = rt,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showWhenDialog = true
                        }
                    )
                }

                if (selectedTask.deadline != null) {
                    ThingsDeadlineBadge(
                        deadlineEpochMillis = selectedTask.deadline,
                        locale = locale,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showDeadlineDialog = true
                        }
                    )
                } else {
                    ThingsTagPill(
                        title = "🏁 ${stringResource(R.string.action_deadline)}",
                        isGhost = true,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showDeadlineDialog = true
                        }
                    )
                }

                if (selectedTask.scheduledStartMinutes != null) {
                    ThingsTagPill(
                        title = TimeblockScheduleUtils.formatTimeblockBadge(
                            selectedTask.scheduledStartMinutes,
                            selectedTask.durationMinutes
                        ),
                        containerColor = colors.thingsBlueSoft,
                        contentColor = colors.thingsBlue,
                        borderColor = colors.thingsBlue.copy(alpha = 0.35f),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showTimeblockDialog = true
                        }
                    )
                } else {
                    ThingsTagPill(
                        title = "⏱️ ${stringResource(R.string.v2_timeblock_title)}",
                        isGhost = true,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showTimeblockDialog = true
                        }
                    )
                }

                if (selectedTask.recurrenceRule != null) {
                    ThingsRepeatBadge(
                        rule = selectedTask.recurrenceRule,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showRepeatDialog = true
                        }
                    )
                } else {
                    ThingsTagPill(
                        title = "🔁 ${stringResource(R.string.action_repeat)}",
                        isGhost = true,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showRepeatDialog = true
                        }
                    )
                }

                // Duration Pill + Preset Dropdown + Custom Duration Dialog
                Box {
                    val formattedDur = formatDurationCompact(selectedTask.durationMinutes)
                    val durLabel = if (formattedDur != null) {
                        "⏱ $formattedDur"
                    } else {
                        "⏱ ${stringResource(R.string.v30_duration_label)}"
                    }
                    ThingsTagPill(
                        title = durLabel,
                        isGhost = formattedDur == null,
                        containerColor = if (formattedDur != null) colors.thingsBlueSoft else null,
                        contentColor = if (formattedDur != null) colors.thingsBlue else null,
                        borderColor = if (formattedDur != null) colors.thingsBlue.copy(alpha = 0.35f) else null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showDurationMenu = true
                        }
                    )

                    DropdownMenu(
                        expanded = showDurationMenu,
                        onDismissRequest = { showDurationMenu = false },
                        modifier = Modifier.background(colors.cardElevatedSurface)
                    ) {
                        STANDALONE_DURATION_PRESETS.forEach { presetMins ->
                            val label = formatDurationCompact(presetMins)
                            val isSelectedDur = selectedTask.durationMinutes == presetMins
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "⏱ $label",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelectedDur) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelectedDur) colors.thingsBlue else colors.primaryText
                                    )
                                },
                                onClick = {
                                    showDurationMenu = false
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateDuration(selectedTask.id, presetMins)
                                }
                            )
                        }
                        HorizontalDivider(color = colors.hairlineDivider)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.v30_duration_custom),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.primaryText
                                )
                            },
                            onClick = {
                                showDurationMenu = false
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showCustomDurationDialog = true
                            }
                        )
                        if (selectedTask.durationMinutes != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.v30_duration_clear),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.secondaryText
                                    )
                                },
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
                    val actualFmt = formatDurationCompact(inspectorActualMins)
                    val estMins = selectedTask.durationMinutes?.takeIf { it > 0 }
                    val focusedLabel = if (estMins != null) {
                        stringResource(
                            R.string.v32_actual_vs_estimated,
                            actualFmt,
                            formatDurationCompact(estMins)
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
                            isGhost = true,
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
                        isGhost = normalizedEffort == null,
                        containerColor = if (normalizedEffort != null) colors.thingsBlueSoft else null,
                        contentColor = if (normalizedEffort != null) colors.thingsBlue else null,
                        borderColor = if (normalizedEffort != null) colors.thingsBlue.copy(alpha = 0.35f) else null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showEffortMenu = true
                        }
                    )

                    DropdownMenu(
                        expanded = showEffortMenu,
                        onDismissRequest = { showEffortMenu = false },
                        modifier = Modifier.background(colors.cardElevatedSurface)
                    ) {
                        val effortOptions = listOf(
                            null to stringResource(R.string.v30_effort_none),
                            EffortLevel.QUICK_WIN to "⚡ ${stringResource(R.string.v30_effort_quick_win)}",
                            EffortLevel.MEDIUM to "◐ ${stringResource(R.string.v30_effort_medium)}",
                            EffortLevel.DEEP_FOCUS to "🧠 ${stringResource(R.string.v30_effort_deep_focus)}"
                        )
                        effortOptions.forEach { (effKey, effText) ->
                            val isSelectedEff = normalizedEffort == effKey
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = effText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelectedEff) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelectedEff) colors.thingsBlue else colors.primaryText
                                    )
                                },
                                onClick = {
                                    showEffortMenu = false
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateEffort(selectedTask.id, effKey)
                                }
                            )
                        }
                    }
                }

                // Dependencies / Waiting On Pill
                val activelyBlocked = remember(selectedTask, openTaskIds) {
                    isTaskActivelyBlocked(selectedTask, openTaskIds)
                }
                val activeBlockerCount = remember(selectedTask.blockedByTaskIds, openTaskIds) {
                    selectedTask.blockedByTaskIds.count { it in openTaskIds }
                }
                val hasActiveBlockers = activelyBlocked || activeBlockerCount > 0
                val dependencyLabel = if (activeBlockerCount > 0) {
                    "⏳ ${stringResource(R.string.v30_blocked_by_count_badge, activeBlockerCount)}"
                } else {
                    "⏳ ${stringResource(R.string.v30_blocked_by_action)}"
                }
                ThingsTagPill(
                    title = dependencyLabel,
                    isGhost = !hasActiveBlockers,
                    containerColor = if (hasActiveBlockers) colors.somedayWarmAmber.copy(alpha = 0.16f) else null,
                    contentColor = if (hasActiveBlockers) colors.somedayWarmAmber else null,
                    borderColor = if (hasActiveBlockers) colors.somedayWarmAmber.copy(alpha = 0.45f) else null,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showDependenciesDialog = true
                    }
                )
            }

            // Area › Project › Heading (Phase) Container Breadcrumb + Move Dialog + Jump Button (↗)
            if (resolvedArea != null || resolvedProject != null) {
                val headingTitle = resolvedHeading?.title?.trim()?.takeIf { it.isNotEmpty() }
                val containerLabel = buildList {
                    if (resolvedArea != null) add(resolvedArea.title)
                    if (resolvedProject != null) add(resolvedProject.title)
                    if (resolvedProject != null && headingTitle != null) add(headingTitle)
                }.joinToString(" › ")

                val canJumpToContainer = (resolvedProject != null && onNavigateToProject != null) ||
                    (resolvedArea != null && onNavigateToArea != null)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showMoveDialog = true
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
                                contentDescription = stringResource(R.string.move_dialog_title),
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

                    if (canJumpToContainer) {
                        val jumpTooltip = if (resolvedProject != null) {
                            stringResource(R.string.v31_area_open_project)
                        } else {
                            resolvedArea?.title ?: stringResource(R.string.entity_area)
                        }
                        ThingsHoverTooltip(text = jumpTooltip) {
                            Surface(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        when {
                                            resolvedProject != null && onNavigateToProject != null ->
                                                onNavigateToProject(resolvedProject.id)
                                            resolvedArea != null && onNavigateToArea != null ->
                                                onNavigateToArea(resolvedArea.id)
                                        }
                                    },
                                shape = RoundedCornerShape(7.dp),
                                color = colors.thingsBlueSoft,
                                border = BorderStroke(0.75.dp, colors.thingsBlue.copy(alpha = 0.35f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "↗",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.thingsBlue
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                ThingsTagPill(
                    title = "📁 ${stringResource(R.string.move_dialog_title)}",
                    isGhost = true,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showMoveDialog = true
                    }
                )
            }

            // Assigned Category / Tag Pills + Always-Visible "+ Categories" Pill
            val visibleTags = remember(selectedTask.tags) {
                selectedTask.tags.filter { !it.isDeleted }
            }
            val categoriesLabel = stringResource(R.string.entity_tags)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                visibleTags.forEach { tag ->
                    ThingsTagPill(
                        title = tag.title,
                        tooltipText = "$categoriesLabel: ${tag.title}",
                        borderColor = colors.hairlineDivider,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showTagPickerDialog = true
                        }
                    )
                }
                ThingsTagPill(
                    title = "+ $categoriesLabel",
                    isGhost = true,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showTagPickerDialog = true
                    }
                )
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
            val doneCount = activeChecklistItems.count { it.isCompleted }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Checklist,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (activeChecklistItems.isNotEmpty()) {
                            "${stringResource(R.string.action_checklist)} ($doneCount/${activeChecklistItems.size})"
                        } else {
                            stringResource(R.string.action_checklist)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (activeChecklistItems.isNotEmpty() && doneCount == activeChecklistItems.size) {
                            colors.logbookGreen
                        } else {
                            colors.secondaryText
                        }
                    )
                }

                ThingsHoverTooltip(text = stringResource(R.string.gemini_magic_breakdown_desc)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.thingsBlueSoft)
                            .clickable(enabled = !isGeneratingMagicBreakdown) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTriggerGeminiMagicBreakdown(selectedTask)
                            }
                            .padding(horizontal = 8.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.5.dp)
                    ) {
                        if (isGeneratingMagicBreakdown) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.8.dp,
                                color = colors.thingsBlue
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = stringResource(R.string.gemini_magic_breakdown),
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(12.5.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.gemini_magic_breakdown),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.thingsBlue,
                            maxLines = 1
                        )
                    }
                }
            }

            if (activeChecklistItems.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    activeChecklistItems.forEachIndexed { idx, item ->
                        key(item.id) {
                            InspectorEditableChecklistRow(
                                item = item,
                                canMoveUp = idx > 0,
                                canMoveDown = idx < activeChecklistItems.lastIndex,
                                shouldAutoFocus = pendingAutoFocusSubtaskId == item.id,
                                onAutoFocusConsumed = {
                                    if (pendingAutoFocusSubtaskId == item.id) {
                                        pendingAutoFocusSubtaskId = null
                                    }
                                },
                                onToggle = { checked ->
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleChecklistItem(item.id, checked)
                                },
                                onRename = { newSubtaskTitle ->
                                    onRenameChecklistItem(item.id, newSubtaskTitle)
                                },
                                onReturnCreateSubtask = { remainingCurrentTitle, nextInitialTitle ->
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (remainingCurrentTitle != item.title) {
                                        onRenameChecklistItem(item.id, remainingCurrentTitle)
                                    }
                                    val nextSubtaskId = UUID.randomUUID().toString()
                                    pendingAutoFocusSubtaskId = nextSubtaskId
                                    if (onInsertChecklistItemAfter != null) {
                                        onInsertChecklistItemAfter(selectedTask.id, item.id, nextSubtaskId, nextInitialTitle)
                                    } else {
                                        onAddChecklistItem(selectedTask.id, nextInitialTitle)
                                    }
                                },
                                onReturnOnEmptySubtask = {
                                    onDeleteChecklistItem(item.id)
                                    runCatching { newChecklistFocusRequester.requestFocus() }
                                },
                                onMoveUp = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onMoveChecklistItem(selectedTask.id, item.id, true)
                                },
                                onMoveDown = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onMoveChecklistItem(selectedTask.id, item.id, false)
                                },
                                onDelete = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDeleteChecklistItem(item.id)
                                }
                            )
                        }
                    }
                }
            }

            val submitNewChecklistItem = {
                val trimmed = newChecklistField.text.replace("\r", "").replace("\n", " ").trim()
                if (trimmed.isNotEmpty()) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAddChecklistItem(selectedTask.id, trimmed)
                    newChecklistField = TextFieldValue(text = "", selection = TextRange(0))
                    runCatching { newChecklistFocusRequester.requestFocus() }
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
                    value = newChecklistField,
                    onValueChange = { incoming ->
                        if ('\n' in incoming.text || '\r' in incoming.text) {
                            val parts = incoming.text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
                            var addedAny = false
                            parts.forEach { rawLine ->
                                val line = rawLine.trim()
                                if (line.isNotEmpty()) {
                                    onAddChecklistItem(selectedTask.id, line)
                                    addedAny = true
                                }
                            }
                            if (addedAny) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            newChecklistField = TextFieldValue(text = "", selection = TextRange(0))
                        } else {
                            newChecklistField = incoming
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = colors.primaryText
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { submitNewChecklistItem() },
                        onDone = { submitNewChecklistItem() }
                    ),
                    cursorBrush = SolidColor(colors.thingsBlue),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(newChecklistFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) {
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    submitNewChecklistItem()
                                }
                                true
                            } else {
                                false
                            }
                        },
                    decorationBox = { innerField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (newChecklistField.text.isEmpty()) {
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

        // 4. Notes Soft Bento Card (with Live Markdown Styling + Detected Link Chips)
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
                visualTransformation = rememberThingsMarkdownVisualTransformation(),
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

        // 5. Attachments Soft Bento Card (Preview, Share, Delete, Attach File & Take Photo)
        if (selectedTask.attachments.isNotEmpty()) {
            InspectorSoftBentoCard {
                TaskAttachmentsStrip(
                    attachments = selectedTask.attachments,
                    onDeleteAttachment = onDeleteAttachment,
                    onAttachFileClick = if (onAddTaskAttachment != null) {
                        { filePickerLauncher.launch(arrayOf("*/*")) }
                    } else null,
                    onTakePhotoClick = if (onAddTaskAttachment != null) {
                        { cameraLauncher.launch(null) }
                    } else null
                )
            }
        } else if (onAddTaskAttachment != null) {
            InspectorSoftBentoCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AttachFile,
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.attachments_section_title),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.secondaryText
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = colors.tagPillBg,
                        border = BorderStroke(0.75.dp, colors.hairlineDivider)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = stringResource(R.string.action_attach_file),
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.action_attach_file),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.thingsBlue
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                cameraLauncher.launch(null)
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = colors.tagPillBg,
                        border = BorderStroke(0.75.dp, colors.hairlineDivider)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = stringResource(R.string.action_take_photo),
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.action_take_photo),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.thingsBlue
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Reusable 1:1 Task Inspector Dialogs
    if (showWhenDialog) {
        WhenNaturalDateDialog(
            currentScheduleState = selectedTask.scheduleState,
            currentWhenDate = selectedTask.whenDate,
            currentReminderTime = selectedTask.reminderTime,
            locale = locale,
            onApplySchedule = { newState, newWhen ->
                onUpdateTaskSchedule(selectedTask, newState, newWhen)
            },
            onApplyScheduleWithReminder = { newState, newWhen, newReminder ->
                if (onUpdateTaskScheduleWithReminder != null) {
                    onUpdateTaskScheduleWithReminder(selectedTask, newState, newWhen, newReminder)
                } else {
                    onUpdateTaskSchedule(selectedTask, newState, newWhen)
                }
            },
            onDismiss = { showWhenDialog = false }
        )
    }

    if (showDeadlineDialog) {
        DeadlineDatePickerDialog(
            currentDeadline = selectedTask.deadline,
            locale = locale,
            onApplyDeadline = { newDl -> onUpdateTaskDeadline(selectedTask, newDl) },
            onDismiss = { showDeadlineDialog = false }
        )
    }

    if (showRepeatDialog) {
        ThingsRepeatDialog(
            currentRule = selectedTask.recurrenceRule,
            locale = locale,
            onApplyRecurrence = { newRule -> onUpdateTaskRecurrence(selectedTask.id, newRule) },
            onDismiss = { showRepeatDialog = false }
        )
    }

    if (showTimeblockDialog) {
        ThingsTimeblockPickerDialog(
            initialStartMinutes = selectedTask.scheduledStartMinutes,
            initialDurationMinutes = selectedTask.durationMinutes,
            onSaveTimeblock = { startMins, durMins ->
                onUpdateTimeblock(selectedTask.id, startMins, durMins)
            },
            onDismiss = { showTimeblockDialog = false }
        )
    }

    if (showCustomDurationDialog) {
        ThingsCustomDurationDialog(
            currentDurationMinutes = selectedTask.durationMinutes,
            onApply = { customMins ->
                showCustomDurationDialog = false
                onUpdateDuration(selectedTask.id, customMins)
            },
            onDismiss = { showCustomDurationDialog = false }
        )
    }

    if (showDependenciesDialog) {
        TaskDependenciesPickerDialog(
            task = selectedTask,
            allOpenTasks = allOpenTasks,
            onUpdateBlockedBy = { blockerIds -> onUpdateBlockedBy(selectedTask.id, blockerIds) },
            onDismiss = { showDependenciesDialog = false }
        )
    }

    if (showMoveDialog) {
        MoveTaskToProjectOrAreaDialog(
            currentProjectId = selectedTask.projectId,
            currentHeadingId = selectedTask.headingId,
            currentAreaId = selectedTask.areaId,
            areas = areas,
            projects = projects,
            headings = allHeadings,
            onMoveTask = { pId, hId, aId ->
                onMoveTaskToContainer(selectedTask.id, pId, hId, aId)
            },
            onDismiss = { showMoveDialog = false }
        )
    }

    if (showTagPickerDialog) {
        TaskTagPickerDialog(
            allTags = allTags,
            selectedTagIds = selectedTask.tags.map { it.id },
            tagSortMode = tagSortMode,
            tagUsageCounts = tagUsageCounts,
            onUpdateSelectedTags = { updatedIds ->
                onUpdateTaskTags(selectedTask, updatedIds)
            },
            onCreateAndAssignTag = { newTagTitle ->
                onCreateAndAssignTag(selectedTask, newTagTitle)
            },
            onOpenFullTagManager = onOpenFullTagManager,
            onDismiss = { showTagPickerDialog = false }
        )
    }
}

@Composable
private fun InspectorEditableChecklistRow(
    item: ChecklistItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    shouldAutoFocus: Boolean = false,
    onAutoFocusConsumed: () -> Unit = {},
    onToggle: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onReturnCreateSubtask: (remainingCurrentTitle: String, nextInitialTitle: String) -> Unit,
    onReturnOnEmptySubtask: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ThingsTheme.colors
    val subtaskFocusRequester = remember(item.id) { FocusRequester() }
    var isSubtaskFocused by remember(item.id) { mutableStateOf(false) }
    var editedSubtaskField by remember(item.id) {
        val cleanTitle = item.title.replace("\r", "").replace("\n", " ")
        mutableStateOf(
            TextFieldValue(
                text = cleanTitle,
                selection = TextRange(cleanTitle.length)
            )
        )
    }

    LaunchedEffect(item.id, shouldAutoFocus) {
        if (shouldAutoFocus) {
            editedSubtaskField = editedSubtaskField.copy(
                selection = TextRange(editedSubtaskField.text.length)
            )
            runCatching { subtaskFocusRequester.requestFocus() }
            onAutoFocusConsumed()
        }
    }

    LaunchedEffect(item.id, item.title) {
        val sanitizedTitle = item.title.replace("\r", "").replace("\n", " ")
        if (!isSubtaskFocused && editedSubtaskField.text != sanitizedTitle) {
            editedSubtaskField = TextFieldValue(
                text = sanitizedTitle,
                selection = TextRange(sanitizedTitle.length)
            )
        }
    }

    val handleSubtaskReturnKey: () -> Unit = {
        val currentText = editedSubtaskField.text.replace("\r", "").replace("\n", " ")
        if (currentText.isBlank()) {
            onReturnOnEmptySubtask()
        } else {
            val sel = editedSubtaskField.selection
            val splitAtMiddle = sel.collapsed && sel.start in 1 until currentText.length
            val remainingCurrent = if (splitAtMiddle) {
                currentText.substring(0, sel.start).trimEnd()
            } else {
                currentText.trim()
            }
            val nextInitial = if (splitAtMiddle && remainingCurrent.isNotBlank()) {
                currentText.substring(sel.start).trimStart()
            } else {
                ""
            }
            val finalCurrent = if (remainingCurrent.isBlank()) currentText.trim() else remainingCurrent
            editedSubtaskField = TextFieldValue(
                text = finalCurrent,
                selection = TextRange(finalCurrent.length)
            )
            if (finalCurrent != item.title) {
                onRename(finalCurrent)
            }
            onReturnCreateSubtask(finalCurrent, nextInitial)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThingsCheckbox(
            status = if (item.isCompleted) TaskStatus.COMPLETED else TaskStatus.OPEN,
            onStatusChange = { status -> onToggle(status == TaskStatus.COMPLETED) },
            shape = ThingsCheckboxShape.CIRCLE,
            size = 16.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
            value = editedSubtaskField,
            onValueChange = { incoming ->
                if ('\n' in incoming.text || '\r' in incoming.text) {
                    val normalized = incoming.text.replace("\r\n", "\n").replace('\r', '\n')
                    val newlineIdx = normalized.indexOf('\n')
                    val before = normalized.substring(0, newlineIdx).trim()
                    val after = normalized.substring(newlineIdx + 1).replace("\n", " ").trim()
                    if (before.isEmpty() && after.isEmpty()) {
                        onReturnOnEmptySubtask()
                    } else {
                        val finalCurrent = if (before.isNotEmpty()) before else after
                        val nextInitial = if (before.isNotEmpty()) after else ""
                        editedSubtaskField = TextFieldValue(
                            text = finalCurrent,
                            selection = TextRange(finalCurrent.length)
                        )
                        onRename(finalCurrent)
                        onReturnCreateSubtask(finalCurrent, nextInitial)
                    }
                } else {
                    editedSubtaskField = incoming
                    onRename(incoming.text)
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
                color = if (item.isCompleted) colors.tertiaryText else colors.primaryText,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            ),
            cursorBrush = SolidColor(colors.thingsBlue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { handleSubtaskReturnKey() },
                onDone = { handleSubtaskReturnKey() }
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(subtaskFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            handleSubtaskReturnKey()
                        }
                        true
                    } else {
                        false
                    }
                }
                .onFocusChanged { focusState ->
                    isSubtaskFocused = focusState.isFocused
                }
        )
        if (canMoveUp) {
            ThingsHoverTooltip(text = stringResource(R.string.subtask_move_up)) {
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.size(21.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowUpward,
                        contentDescription = stringResource(R.string.subtask_move_up),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        if (canMoveDown) {
            ThingsHoverTooltip(text = stringResource(R.string.subtask_move_down)) {
                IconButton(
                    onClick = onMoveDown,
                    modifier = Modifier.size(21.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowDownward,
                        contentDescription = stringResource(R.string.subtask_move_down),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        ThingsHoverTooltip(text = stringResource(R.string.subtask_delete)) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(21.dp)
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
}

@Composable
internal fun InspectorWhenQuickPill(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeTint: Color,
    activeBg: Color,
    onClick: () -> Unit
) {
    val colors = ThingsTheme.colors
    val ghostMuted = colors.secondaryText.copy(alpha = 0.78f)
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (isSelected) activeBg else Color.Transparent,
        border = BorderStroke(
            width = 0.75.dp,
            color = if (isSelected) activeTint.copy(alpha = 0.48f) else Color.Transparent
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
                tint = if (isSelected) activeTint else ghostMuted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) activeTint else ghostMuted,
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
    dragController: ProjectDragDropController? = null,
    onJumpToWorkloadDay: ((index: Int, day: com.pv.thingsbook.domain.model.DayWorkloadSummary) -> Unit)? = null,
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
                compact = true,
                dragController = dragController,
                onDayClick = onJumpToWorkloadDay
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
