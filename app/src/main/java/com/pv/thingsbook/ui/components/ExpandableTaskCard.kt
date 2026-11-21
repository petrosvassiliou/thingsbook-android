package com.pv.thingsbook.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Timer
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import android.view.MotionEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pv.thingsbook.R
import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.ChecklistItem
import com.pv.thingsbook.domain.model.EffortLevel
import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.RecurrenceRule
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskCardInteractionRules
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.model.TimeblockScheduleUtils
import com.pv.thingsbook.domain.model.formatDurationCompact
import com.pv.thingsbook.domain.model.isTaskActivelyBlocked
import com.pv.thingsbook.ui.theme.ThingsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private fun PointerEvent.isRightClickTrigger(previousButtonsSecondary: Boolean): Boolean {
    val me = runCatching {
        val prop = this::class.java.methods.firstOrNull {
            it.name == "getMotionEvent\$ui_release" || it.name == "getMotionEvent"
        }
        prop?.invoke(this) as? MotionEvent
    }.getOrNull()

    return com.pv.thingsbook.domain.model.RightClickGestureUtils.isRightClickEvent(
        isPressEvent = type == PointerEventType.Press,
        isReleaseEvent = type == PointerEventType.Release,
        isSecondaryButtonFlag = buttons.isSecondaryPressed,
        previousButtonsSecondary = previousButtonsSecondary,
        motionEventActionMasked = me?.actionMasked ?: -1,
        motionEventButtonState = me?.buttonState ?: 0,
        motionEventActionButton = me?.actionButton ?: 0
    )
}

/**
 * The centerpiece of Things 3 UI/UX:
 * - Inline Card Expansion
 * - Elegant 3-stage completion choreography (checkbox halo + sweeping strikethrough + luminous wash + smooth glide-out)
 * - Things-for-iPhone Swipe Gestures
 * - Mouse/Touch Multi-Task Selection
 * - Smart Right-Click Context Menu suppression whenever inside an expanded task or when any textbox has focus
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ExpandableTaskCard(
    task: TaskItem,
    allTags: List<Tag> = emptyList(),
    tagSortMode: String = "alpha",
    tagUsageCounts: Map<String, Int> = emptyMap(),
    allAreas: List<Area> = emptyList(),
    allProjects: List<Project> = emptyList(),
    allHeadings: List<Heading> = emptyList(),
    allOpenTasks: List<TaskItem> = emptyList(),
    openTaskIds: Set<String> = emptySet(),
    isExpanded: Boolean,
    isTodayView: Boolean = false,
    isSelected: Boolean = false,
    isSelectionModeActive: Boolean = false,
    suppressContextMenuExternal: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    onRangeSelect: (() -> Unit)? = null,
    isGeneratingMagicBreakdown: Boolean,
    onExpandToggle: () -> Unit,
    onStatusChange: (TaskStatus) -> Unit,
    onTitleNotesChange: (String, String) -> Unit,
    onScheduleChange: (ScheduleState, Long?) -> Unit,
    onQuickThisEveningAndClose: ((latestTitle: String, latestNotes: String) -> Unit)? = null,
    onScheduleWithReminderChange: ((ScheduleState, Long?, Long?) -> Unit)? = null,
    onDeadlineChange: (Long?) -> Unit,
    onRecurrenceChange: ((RecurrenceRule?) -> Unit)? = null,
    onUpdateTimeblock: ((startMinutes: Int?, durationMinutes: Int?) -> Unit)? = null,
    onUpdateEffortLevel: ((effortLevel: String?) -> Unit)? = null,
    onUpdateStandaloneDuration: ((durationMinutes: Int?) -> Unit)? = null,
    onUpdateBlockedBy: ((blockerIds: List<String>) -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onMoveTaskToContainer: ((projectId: String?, headingId: String?, areaId: String?) -> Unit)? = null,
    onUpdateTaskTags: ((List<String>) -> Unit)? = null,
    onCreateAndAssignTag: ((String) -> Unit)? = null,
    onOpenFullTagManager: (() -> Unit)? = null,
    onAddChecklistItem: (String) -> Unit,
    onInsertChecklistItemAfter: ((afterItemId: String?, newItemId: String, title: String) -> Unit)? = null,
    onToggleChecklistItem: (String, Boolean) -> Unit,
    onRenameChecklistItem: ((String, String) -> Unit)? = null,
    onMoveChecklistItem: ((String, Boolean) -> Unit)? = null,
    onDeleteChecklistItem: ((String) -> Unit)? = null,
    onAddTaskAttachment: ((fileName: String, mimeType: String, bytes: ByteArray) -> Unit)? = null,
    onDeleteTaskAttachment: ((attachmentId: String) -> Unit)? = null,
    onShowMessage: ((String) -> Unit)? = null,
    onTriggerGeminiMagicBreakdown: () -> Unit,
    onDeleteTask: () -> Unit,
    onDuplicateTask: (() -> Unit)? = null,
    onStartFocusTimer: (() -> Unit)? = null,
    externalDialogTriggerToken: Int = 0,
    externalDialogTriggerType: String? = null,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
    onRequestEnsureVisible: ((preferBottomVisible: Boolean) -> Unit)? = null,
    collapsedTrailingContent: (@Composable () -> Unit)? = null
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentOnStatusChange by rememberUpdatedState(onStatusChange)

    // Focus tracking across all text inputs inside this card so right-click NEVER hijacks native text editing
    var isInlineQuickEditing by remember(task.id) { mutableStateOf(false) }
    val inlineQuickTitleFocusRequester = remember(task.id) { FocusRequester() }
    var showQuickStripEffortMenu by remember(task.id) { mutableStateOf(false) }
    var isTitleFocused by remember(task.id) { mutableStateOf(false) }
    var isNotesFocused by remember(task.id) { mutableStateOf(false) }
    var isNewChecklistFocused by remember(task.id) { mutableStateOf(false) }
    var focusedSubtaskCount by remember(task.id) { mutableIntStateOf(0) }

    val suppressCustomContextMenu = isExpanded ||
        isInlineQuickEditing ||
        suppressContextMenuExternal ||
        isTitleFocused ||
        isNotesFocused ||
        isNewChecklistFocused ||
        focusedSubtaskCount > 0

    var editedTitleField by remember(task.id) {
        mutableStateOf(
            TextFieldValue(
                text = task.title,
                selection = TextRange(task.title.length)
            )
        )
    }
    var editedNotesField by remember(task.id) {
        mutableStateOf(
            TextFieldValue(
                text = task.notes,
                selection = TextRange(task.notes.length)
            )
        )
    }

    LaunchedEffect(task.id, task.title) {
        if (!isTitleFocused && !isInlineQuickEditing && editedTitleField.text != task.title) {
            editedTitleField = TextFieldValue(
                text = task.title,
                selection = TextRange(task.title.length)
            )
        }
    }

    LaunchedEffect(task.id, task.notes) {
        if (!isNotesFocused && editedNotesField.text != task.notes) {
            editedNotesField = TextFieldValue(
                text = task.notes,
                selection = TextRange(task.notes.length)
            )
        }
    }

    var showWhenDialog by remember(task.id) { mutableStateOf(false) }
    var showDeadlineDialog by remember(task.id) { mutableStateOf(false) }
    var showRepeatDialog by remember(task.id) { mutableStateOf(false) }
    var showTimeblockDialog by remember(task.id) { mutableStateOf(false) }
    var showDependenciesDialog by remember(task.id) { mutableStateOf(false) }
    var showMoveDialog by remember(task.id) { mutableStateOf(false) }
    var showTagPickerDialog by remember(task.id) { mutableStateOf(false) }
    var showContextMenu by remember(task.id) { mutableStateOf(false) }
    var contextMenuOffsetDp by remember(task.id) { mutableStateOf(DpOffset(24.dp, 0.dp)) }

    LaunchedEffect(externalDialogTriggerToken, isExpanded) {
        if (isExpanded && externalDialogTriggerToken > 0) {
            when (externalDialogTriggerType) {
                "WHEN" -> showWhenDialog = true
                "DEADLINE" -> showDeadlineDialog = true
                "TIMEBLOCK" -> showTimeblockDialog = true
                "MOVE" -> showMoveDialog = true
            }
        }
    }

    LaunchedEffect(suppressCustomContextMenu) {
        if (suppressCustomContextMenu && showContextMenu) {
            showContextMenu = false
        }
    }

    // =========================================================================
    // Calm, Zero-Flash Task Completion Choreography (Authentic Things 3 Style)
    // =========================================================================
    var isCompletingAnim by remember(task.id) { mutableStateOf(false) }
    var isCompletedPendingCommit by remember(task.id) { mutableStateOf(false) }
    val completionStrikeProgress = remember(task.id) { Animatable(0f) }
    val completionExitProgress = remember(task.id) { Animatable(0f) }

    LaunchedEffect(isInlineQuickEditing, isExpanded, isSelectionModeActive, isCompletingAnim) {
        if ((isExpanded || isSelectionModeActive || isCompletingAnim) && isInlineQuickEditing) {
            isInlineQuickEditing = false
        } else if (isInlineQuickEditing && !isExpanded) {
            delay(30)
            runCatching { inlineQuickTitleFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(task.status, task.whenDate) {
        if (isCompletedPendingCommit && task.status != TaskStatus.OPEN) {
            isCompletedPendingCommit = false
            completionStrikeProgress.snapTo(0f)
            completionExitProgress.snapTo(0f)
        }
    }

    DisposableEffect(task.id) {
        onDispose {
            // If user navigates away while completion animation is in flight, commit immediately
            if (isCompletingAnim) {
                isCompletingAnim = false
                currentOnStatusChange(TaskStatus.COMPLETED)
            }
        }
    }

    LaunchedEffect(isCompletingAnim) {
        if (isCompletingAnim) {
            isCompletedPendingCommit = false
            completionStrikeProgress.snapTo(0f)
            completionExitProgress.snapTo(0f)
            launch {
                delay(25)
                completionStrikeProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing)
                )
            }
            delay(310)
            completionExitProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
            // Keep row invisible while Room DB transaction commits so it never flashes back at alpha=1!
            isCompletedPendingCommit = true
            isCompletingAnim = false
            currentOnStatusChange(TaskStatus.COMPLETED)
        } else if (!isCompletedPendingCommit) {
            completionStrikeProgress.snapTo(0f)
            completionExitProgress.snapTo(0f)
        }
    }

    LaunchedEffect(isCompletedPendingCommit) {
        if (isCompletedPendingCommit) {
            // Safety fallback for recurring tasks that stay in the current Project view with a new date
            delay(350)
            if (isCompletedPendingCommit) {
                isCompletedPendingCommit = false
                completionStrikeProgress.snapTo(0f)
                completionExitProgress.snapTo(0f)
            }
        }
    }

    val handleStatusRequest: (TaskStatus) -> Unit = { requestedStatus ->
        if (task.status == TaskStatus.OPEN && requestedStatus == TaskStatus.COMPLETED && !isCompletingAnim && !isCompletedPendingCommit) {
            if (isExpanded) {
                onStatusChange(TaskStatus.COMPLETED)
                onExpandToggle()
            } else {
                isCompletingAnim = true
            }
        } else if (isCompletingAnim && requestedStatus == TaskStatus.OPEN) {
            // Allow instant undo if user clicks checkbox again before glide-out finishes
            isCompletingAnim = false
            isCompletedPendingCommit = false
        } else {
            onStatusChange(requestedStatus)
            if (isExpanded && requestedStatus == TaskStatus.CANCELED) {
                onExpandToggle()
            }
        }
    }

    val effectiveVisualStatus = if (isCompletingAnim || isCompletedPendingCommit) {
        TaskStatus.COMPLETED
    } else {
        task.status
    }

    // Things-for-iPhone Horizontal Swipe Gesture state (when collapsed)
    var rawSwipeOffsetPx by remember(task.id, isExpanded) { mutableFloatStateOf(0f) }
    val animatedSwipeOffsetPx by animateFloatAsState(
        targetValue = rawSwipeOffsetPx,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 450f),
        label = "swipeOffsetPx"
    )
    val whenSwipeThresholdPx = with(density) { 68.dp.toPx() }
    val selectSwipeThresholdPx = with(density) { -62.dp.toPx() }
    val deleteSwipeThresholdPx = with(density) { -160.dp.toPx() }

    val titleFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isExpanded, task.id) {
        if (isExpanded) {
            val txt = editedTitleField.text
            editedTitleField = editedTitleField.copy(
                selection = TextRange(txt.length)
            )
            editedNotesField = editedNotesField.copy(
                selection = TextRange(editedNotesField.text.length)
            )
            if (TaskCardInteractionRules.shouldAutoFocusTitleOnExpand(txt)) {
                delay(60)
                runCatching { titleFocusRequester.requestFocus() }
            }
        } else {
            isTitleFocused = false
            isNotesFocused = false
            isNewChecklistFocused = false
            focusedSubtaskCount = 0
        }
    }

    val assignedProject = remember(task.projectId, allProjects) {
        allProjects.find { it.id == task.projectId }
    }
    val assignedArea = remember(task.areaId, assignedProject, allAreas) {
        val targetAreaId = assignedProject?.areaId ?: task.areaId
        allAreas.find { it.id == targetAreaId }
    }
    val assignedHeading = remember(task.headingId, allHeadings) {
        allHeadings.firstOrNull { it.id == task.headingId }
    }

    val horizontalPadding = if (isExpanded) 0.dp else 16.dp
    val verticalPadding = if (isExpanded) 0.dp else 1.dp
    val cornerRadius = if (isExpanded) 22.dp else 12.dp
    val shadowElevation = 0.dp

    val strikeFrac = if (isCompletedPendingCommit) 1f else completionStrikeProgress.value.coerceIn(0f, 1f)
    val exitFrac = if (isCompletedPendingCommit && task.status == TaskStatus.OPEN) {
        1f
    } else {
        completionExitProgress.value.coerceIn(0f, 1f)
    }

    val isActivelyBlocked = remember(task.id, task.blockedByTaskIds, openTaskIds) {
        isTaskActivelyBlocked(task, openTaskIds)
    }

    val isContextMenuTargeted = (!isExpanded && isInlineQuickEditing) ||
        (!isExpanded && showContextMenu && !suppressCustomContextMenu) ||
        (!isExpanded && (showWhenDialog || showDeadlineDialog || showTimeblockDialog || showRepeatDialog || showDependenciesDialog || showMoveDialog || showTagPickerDialog))

    val surfaceColor by animateColorAsState(
        targetValue = when {
            isExpanded -> colors.cardSurface
            isInlineQuickEditing -> colors.cardElevatedSurface
            isContextMenuTargeted -> colors.thingsBlue.copy(alpha = 0.16f)
            isSelected -> colors.thingsBlueSoft
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "cardSurfaceColor"
    )

    val startInlineQuickEdit = {
        editedTitleField = TextFieldValue(
            text = task.title,
            selection = TextRange(task.title.length)
        )
        isInlineQuickEditing = true
    }

    val commitInlineQuickEdit = {
        val sanitized = editedTitleField.text.replace("\r", "").replace("\n", " ").trim()
        if (sanitized != task.title) {
            editedTitleField = TextFieldValue(text = sanitized, selection = TextRange(sanitized.length))
            onTitleNotesChange(sanitized, editedNotesField.text)
        }
        isInlineQuickEditing = false
        isTitleFocused = false
    }

    val cancelInlineQuickEdit = {
        editedTitleField = TextFieldValue(text = task.title, selection = TextRange(task.title.length))
        isInlineQuickEditing = false
        isTitleFocused = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                if ((isCompletingAnim || isCompletedPendingCommit) && exitFrac > 0f) {
                    translationX = exitFrac * 10.dp.toPx()
                    alpha = (1f - exitFrac).coerceIn(0f, 1f)
                }
            }
    ) {
        if (!isExpanded && !isInlineQuickEditing && !isCompletingAnim && !isCompletedPendingCommit && kotlin.math.abs(animatedSwipeOffsetPx) > 6f) {
            SwipeActionBackgroundLayer(
                animatedSwipeOffsetPx = animatedSwipeOffsetPx,
                deleteSwipeThresholdPx = deleteSwipeThresholdPx,
                isSelected = isSelected,
                horizontalPadding = horizontalPadding,
                verticalPadding = verticalPadding,
                cornerRadius = cornerRadius,
                modifier = Modifier.matchParentSize()
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    val safeX = if (animatedSwipeOffsetPx.isFinite()) animatedSwipeOffsetPx.roundToInt() else 0
                    IntOffset(safeX, 0)
                }
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .shadow(
                    elevation = shadowElevation,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.14f)
                )
                .clip(RoundedCornerShape(cornerRadius))
                .then(
                    when {
                        !isExpanded && (isSelected || isContextMenuTargeted) -> Modifier.border(
                            width = if (isContextMenuTargeted) 1.25.dp else 1.dp,
                            color = colors.thingsBlue.copy(alpha = if (isContextMenuTargeted) 0.72f else 0.42f),
                            shape = RoundedCornerShape(cornerRadius)
                        )
                        else -> Modifier
                    }
                )
                .then(
                    if (!isExpanded) {
                        Modifier.animateContentSize(
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                        )
                    } else {
                        Modifier
                    }
                )
                .pointerInput(task.id, isExpanded, isCompletingAnim, isInlineQuickEditing) {
                    if (!isExpanded && !isCompletingAnim && !isInlineQuickEditing) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    rawSwipeOffsetPx >= whenSwipeThresholdPx -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showWhenDialog = true
                                    }
                                    rawSwipeOffsetPx <= deleteSwipeThresholdPx -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDeleteTask()
                                    }
                                    rawSwipeOffsetPx <= selectSwipeThresholdPx -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleSelection?.invoke()
                                    }
                                }
                                rawSwipeOffsetPx = 0f
                            },
                            onDragCancel = {
                                rawSwipeOffsetPx = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                rawSwipeOffsetPx = (rawSwipeOffsetPx + dragAmount).coerceIn(
                                    -220f * density.density,
                                    140f * density.density
                                )
                            }
                        )
                    }
                }
                .pointerInput(task.id, suppressCustomContextMenu) {
                    awaitPointerEventScope {
                        var wasSecondaryPressed = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (!suppressCustomContextMenu && event.isRightClickTrigger(wasSecondaryPressed)) {
                                wasSecondaryPressed = false
                                val pos = event.changes.firstOrNull()?.position
                                if (pos != null && pos.x.isFinite() && pos.y.isFinite()) {
                                    contextMenuOffsetDp = with(density) {
                                        DpOffset(
                                            x = pos.x.toDp().coerceAtLeast(12.dp),
                                            y = 0.dp
                                        )
                                    }
                                }
                                event.changes.forEach { it.consume() }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                showContextMenu = true
                                continue
                            }

                            wasSecondaryPressed = event.buttons.isSecondaryPressed
                            if (event.type == PointerEventType.Press && !suppressCustomContextMenu) {
                                if (event.buttons.isTertiaryPressed ||
                                    (event.buttons.isPrimaryPressed && (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed))
                                ) {
                                    event.changes.forEach { it.consume() }
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleSelection?.invoke()
                                } else if (event.buttons.isPrimaryPressed && event.keyboardModifiers.isShiftPressed) {
                                    event.changes.forEach { it.consume() }
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (onRangeSelect != null) onRangeSelect() else onToggleSelection?.invoke()
                                }
                            }
                        }
                    }
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isCompletingAnim || isInlineQuickEditing) return@combinedClickable
                        if (isSelectionModeActive && !isExpanded && onToggleSelection != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleSelection()
                        } else if (!isExpanded) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExpandToggle()
                        }
                    },
                    onLongClick = if (isExpanded || isInlineQuickEditing) {
                        null
                    } else if (task.status == TaskStatus.OPEN && onToggleSelection != null) {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleSelection.invoke()
                        }
                    } else if (suppressCustomContextMenu) {
                        null
                    } else {
                        {
                            contextMenuOffsetDp = DpOffset(24.dp, 0.dp)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showContextMenu = true
                        }
                    }
                ),
            color = surfaceColor,
            shape = RoundedCornerShape(cornerRadius)
        ) {
            if (isExpanded) {
                ExpandedTaskCardBody(
                    task = task,
                    effectiveVisualStatus = effectiveVisualStatus,
                    openTaskIds = openTaskIds,
                    assignedProject = assignedProject,
                    assignedArea = assignedArea,
                    assignedHeading = assignedHeading,
                    showQuickThisEveningPill = TaskCardInteractionRules.shouldShowQuickThisEveningPill(
                        isTodayView = isTodayView,
                        status = task.status,
                        scheduleState = task.scheduleState
                    ),
                    titleFocusRequester = titleFocusRequester,
                    editedTitleField = editedTitleField,
                    onTitleFieldChange = { newVal ->
                        val sanitizedText = newVal.text.replace("\r", "").replace("\n", " ")
                        val sanitizedVal = if (sanitizedText == newVal.text) {
                            newVal
                        } else {
                            newVal.copy(
                                text = sanitizedText,
                                selection = TextRange(newVal.selection.start.coerceIn(0, sanitizedText.length))
                            )
                        }
                        val textChanged = sanitizedVal.text != editedTitleField.text
                        editedTitleField = sanitizedVal
                        if (textChanged) {
                            onTitleNotesChange(sanitizedVal.text, editedNotesField.text)
                        }
                    },
                    onTitleFocusChanged = { focused ->
                        isTitleFocused = focused
                        if (focused) onRequestEnsureVisible?.invoke(false)
                    },
                    editedNotesField = editedNotesField,
                    onNotesFieldChange = { newVal ->
                        val textChanged = newVal.text != editedNotesField.text
                        editedNotesField = newVal
                        if (textChanged) {
                            onTitleNotesChange(editedTitleField.text, newVal.text)
                        }
                    },
                    onNotesFocusChanged = { focused ->
                        isNotesFocused = focused
                        if (focused) onRequestEnsureVisible?.invoke(false)
                    },
                    onNewChecklistFocusChanged = { focused ->
                        isNewChecklistFocused = focused
                        if (focused) onRequestEnsureVisible?.invoke(true)
                    },
                    onSubtaskFocusDelta = { delta ->
                        focusedSubtaskCount = (focusedSubtaskCount + delta).coerceAtLeast(0)
                        if (delta > 0) onRequestEnsureVisible?.invoke(true)
                    },
                    onRequestEnsureVisible = onRequestEnsureVisible,
                    isGeneratingMagicBreakdown = isGeneratingMagicBreakdown,
                    locale = locale,
                    onStatusChange = handleStatusRequest,
                    onAddChecklistItem = onAddChecklistItem,
                    onInsertChecklistItemAfter = onInsertChecklistItemAfter,
                    onToggleChecklistItem = onToggleChecklistItem,
                    onRenameChecklistItem = onRenameChecklistItem,
                    onMoveChecklistItem = onMoveChecklistItem,
                    onDeleteChecklistItem = onDeleteChecklistItem,
                    onAddTaskAttachment = onAddTaskAttachment,
                    onDeleteTaskAttachment = onDeleteTaskAttachment,
                    onShowMessage = onShowMessage,
                    onDictateTitleOrNotes = { spokenText ->
                        val clean = spokenText.trim()
                        if (clean.isNotEmpty()) {
                            if (editedTitleField.text.isBlank()) {
                                editedTitleField = TextFieldValue(text = clean, selection = TextRange(clean.length))
                                onTitleNotesChange(clean, editedNotesField.text)
                            } else {
                                val newNotes = if (editedNotesField.text.isBlank()) clean else "${editedNotesField.text.trimEnd()}\n$clean"
                                editedNotesField = TextFieldValue(text = newNotes, selection = TextRange(newNotes.length))
                                onTitleNotesChange(editedTitleField.text, newNotes)
                            }
                        }
                    },
                    onTriggerGeminiMagicBreakdown = onTriggerGeminiMagicBreakdown,
                    onOpenWhenDialog = { showWhenDialog = true },
                    onQuickThisEveningAndClose = if (onQuickThisEveningAndClose != null) {
                        { onQuickThisEveningAndClose(editedTitleField.text, editedNotesField.text) }
                    } else null,
                    onOpenDeadlineDialog = { showDeadlineDialog = true },
                    onOpenRepeatDialog = { showRepeatDialog = true },
                    onOpenTimeblockDialog = if (onUpdateTimeblock != null) ({ showTimeblockDialog = true }) else null,
                    onUpdateEffortLevel = onUpdateEffortLevel,
                    onUpdateStandaloneDuration = onUpdateStandaloneDuration,
                    onOpenDependenciesDialog = if (onUpdateBlockedBy != null) ({ showDependenciesDialog = true }) else null,
                    onOpenMoveDialog = { showMoveDialog = true },
                    onOpenTagPickerDialog = { showTagPickerDialog = true },
                    onTogglePin = onTogglePin,
                    onDuplicateTask = onDuplicateTask,
                    onStartFocusTimer = onStartFocusTimer,
                    onDeleteTask = onDeleteTask,
                    onDoneCollapse = onExpandToggle
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isActivelyBlocked) 0.62f else 1f)
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = if (isInlineQuickEditing) Alignment.CenterVertically else Alignment.Top
                    ) {
                        val checkboxTooltip = if (effectiveVisualStatus == TaskStatus.OPEN) {
                            stringResource(R.string.action_complete)
                        } else {
                            stringResource(R.string.action_reopen_todo)
                        }
                        ThingsHoverTooltip(
                            text = checkboxTooltip,
                            enableTouchLongPress = false
                        ) {
                            ThingsCheckbox(
                                status = effectiveVisualStatus,
                                onStatusChange = handleStatusRequest,
                                isDashedSomeday = task.scheduleState == ScheduleState.SOMEDAY
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        if (isInlineQuickEditing) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(colors.cardSurface)
                                    .border(
                                        width = 1.dp,
                                        color = colors.thingsBlue.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(9.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = editedTitleField,
                                    onValueChange = { incoming ->
                                        val clean = incoming.text.replace("\r", "").replace("\n", "")
                                        editedTitleField = if (clean == incoming.text) {
                                            incoming
                                        } else {
                                            incoming.copy(
                                                text = clean,
                                                selection = TextRange(incoming.selection.start.coerceIn(0, clean.length))
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = colors.primaryText,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(colors.thingsBlue),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { commitInlineQuickEdit() }),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(inlineQuickTitleFocusRequester)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.key) {
                                                    Key.Enter, Key.NumPadEnter -> {
                                                        commitInlineQuickEdit()
                                                        true
                                                    }
                                                    Key.Escape -> {
                                                        cancelInlineQuickEdit()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                        .onFocusChanged { focusState ->
                                            isTitleFocused = focusState.isFocused
                                        },
                                    decorationBox = { inner ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            if (editedTitleField.text.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.placeholder_todo_title),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = colors.tertiaryText
                                                )
                                            }
                                            inner()
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            ThingsHoverTooltip(
                                text = stringResource(R.string.action_save),
                                enableTouchLongPress = false
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.thingsBlue)
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            commitInlineQuickEdit()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = stringResource(R.string.action_save),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            CollapsedTaskTitleWithStrike(
                                task = task,
                                assignedProject = assignedProject,
                                assignedArea = assignedArea,
                                isCompletingAnim = isCompletingAnim,
                                strikeProgress = strikeFrac,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 3.dp)
                            )

                            if (task.isPinned && !isCompletingAnim) {
                                Spacer(modifier = Modifier.width(6.dp))
                                val pinnedTooltip = if (onTogglePin != null) {
                                    stringResource(R.string.v25_unpin_task)
                                } else {
                                    stringResource(R.string.v25_pinned_badge)
                                }
                                ThingsHoverTooltip(
                                    text = pinnedTooltip,
                                    enableTouchLongPress = false
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PushPin,
                                        contentDescription = stringResource(R.string.v25_pinned_badge),
                                        tint = colors.thingsBlue.copy(alpha = 0.88f),
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .size(14.dp)
                                            .let { base ->
                                                if (onTogglePin != null) {
                                                    base
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .clickable {
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            onTogglePin()
                                                        }
                                                } else {
                                                    base
                                                }
                                            }
                                    )
                                }
                            }

                            if (!isCompletingAnim && !isSelected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                val quickEditLabel = stringResource(R.string.v30_quick_edit_action)
                                ThingsHoverTooltip(
                                    text = quickEditLabel,
                                    enableTouchLongPress = false
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            startInlineQuickEdit()
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = quickEditLabel,
                                            tint = colors.secondaryText.copy(alpha = 0.72f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            if (collapsedTrailingContent != null && !isCompletingAnim) {
                                Spacer(modifier = Modifier.width(4.dp))
                                collapsedTrailingContent()
                            }
                        }
                    }

                    if (isInlineQuickEditing) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 40.dp, top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ThingsTagPill(
                                title = "📅 ${stringResource(R.string.action_when)}",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showWhenDialog = true
                                }
                            )
                            ThingsTagPill(
                                title = "🏁 ${stringResource(R.string.action_deadline)}",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showDeadlineDialog = true
                                }
                            )
                            if (onUpdateTimeblock != null) {
                                ThingsTagPill(
                                    title = "⏱ ${stringResource(R.string.v2_timeblock_title)}",
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showTimeblockDialog = true
                                    }
                                )
                            }
                            if (onUpdateEffortLevel != null) {
                                val normalizedEffort = EffortLevel.normalize(task.effortLevel)
                                Box {
                                    ThingsTagPill(
                                        title = "⚡ ${stringResource(R.string.v30_effort_label)}",
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showQuickStripEffortMenu = true
                                        }
                                    )
                                    DropdownMenu(
                                        expanded = showQuickStripEffortMenu,
                                        onDismissRequest = { showQuickStripEffortMenu = false },
                                        modifier = Modifier.background(colors.cardElevatedSurface)
                                    ) {
                                        listOf(
                                            null to stringResource(R.string.v30_effort_none),
                                            EffortLevel.QUICK_WIN to "⚡ ${stringResource(R.string.v30_effort_quick_win)}",
                                            EffortLevel.MEDIUM to "◐ ${stringResource(R.string.v30_effort_medium)}",
                                            EffortLevel.DEEP_FOCUS to "🧠 ${stringResource(R.string.v30_effort_deep_focus)}"
                                        ).forEach { (effKey, effLabel) ->
                                            val effSelected = normalizedEffort == effKey
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = effLabel,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (effSelected) FontWeight.Bold else FontWeight.Normal
                                                        ),
                                                        color = if (effSelected) colors.thingsBlue else colors.primaryText
                                                    )
                                                },
                                                onClick = {
                                                    showQuickStripEffortMenu = false
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onUpdateEffortLevel(effKey)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (onUpdateTaskTags != null) {
                                ThingsTagPill(
                                    title = "# ${stringResource(R.string.action_tags)}",
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showTagPickerDialog = true
                                    }
                                )
                            }
                            ThingsTagPill(
                                title = "⤢ ${stringResource(R.string.v30_open_details_action)}",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    commitInlineQuickEdit()
                                    onExpandToggle()
                                }
                            )
                        }
                    }

                    if (task.notes.isNotBlank() && !isInlineQuickEditing) {
                        Text(
                            text = task.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 40.dp, top = 2.dp)
                        )
                    }

                    if (hasCollapsedTaskBadges(task, isActivelyBlocked)) {
                        CollapsedTaskInlineBadges(
                            task = task,
                            allOpenTasks = allOpenTasks,
                            openTaskIds = openTaskIds,
                            locale = locale,
                            onOpenWhenDialog = { showWhenDialog = true },
                            onOpenDeadlineDialog = { showDeadlineDialog = true },
                            onOpenTimeblockDialog = if (onUpdateTimeblock != null) ({ showTimeblockDialog = true }) else null,
                            onUpdateStandaloneDuration = onUpdateStandaloneDuration,
                            onUpdateEffortLevel = onUpdateEffortLevel,
                            onOpenRepeatDialog = if (onRecurrenceChange != null) ({ showRepeatDialog = true }) else null,
                            onOpenDependenciesDialog = if (onUpdateBlockedBy != null) ({ showDependenciesDialog = true }) else null,
                            onOpenTagPickerDialog = if (onUpdateTaskTags != null) ({ showTagPickerDialog = true }) else null,
                            modifier = Modifier.padding(start = 40.dp, top = 6.dp)
                        )
                    }
                }
            }
        }

        if (showContextMenu && !suppressCustomContextMenu) {
            TaskContextDropdownMenu(
                task = task,
                isSelected = isSelected,
                offset = contextMenuOffsetDp,
                onStartInlineQuickEdit = if (!isCompletingAnim) ({ startInlineQuickEdit() }) else null,
                onToggleSelection = onToggleSelection,
                onStatusChange = handleStatusRequest,
                onOpenWhenDialog = { showWhenDialog = true },
                onOpenDeadlineDialog = { showDeadlineDialog = true },
                onOpenRepeatDialog = { showRepeatDialog = true },
                onOpenTimeblockDialog = if (onUpdateTimeblock != null) ({ showTimeblockDialog = true }) else null,
                onUpdateEffortLevel = onUpdateEffortLevel,
                onUpdateStandaloneDuration = onUpdateStandaloneDuration,
                onOpenDependenciesDialog = if (onUpdateBlockedBy != null) ({ showDependenciesDialog = true }) else null,
                onOpenMoveDialog = { showMoveDialog = true },
                onOpenTagPickerDialog = { showTagPickerDialog = true },
                onTriggerGeminiMagicBreakdown = onTriggerGeminiMagicBreakdown,
                onStartFocusTimer = onStartFocusTimer,
                onTogglePin = onTogglePin,
                onDuplicateTask = onDuplicateTask,
                onDeleteTask = onDeleteTask,
                onDismiss = { showContextMenu = false }
            )
        }
    }

    if (showTimeblockDialog && onUpdateTimeblock != null) {
        ThingsTimeblockPickerDialog(
            initialStartMinutes = task.scheduledStartMinutes,
            initialDurationMinutes = task.durationMinutes,
            onSaveTimeblock = { startMins, durMins ->
                onUpdateTimeblock(startMins, durMins)
            },
            onDismiss = { showTimeblockDialog = false }
        )
    }

    if (showDependenciesDialog && onUpdateBlockedBy != null) {
        TaskDependenciesPickerDialog(
            task = task,
            allOpenTasks = allOpenTasks,
            onUpdateBlockedBy = onUpdateBlockedBy,
            onDismiss = { showDependenciesDialog = false }
        )
    }

    TaskCardDialogsHost(
        task = task,
        allTags = allTags,
        tagSortMode = tagSortMode,
        tagUsageCounts = tagUsageCounts,
        allAreas = allAreas,
        allProjects = allProjects,
        allHeadings = allHeadings,
        locale = locale,
        showWhenDialog = showWhenDialog,
        onDismissWhenDialog = { showWhenDialog = false },
        showDeadlineDialog = showDeadlineDialog,
        onDismissDeadlineDialog = { showDeadlineDialog = false },
        showRepeatDialog = showRepeatDialog,
        onDismissRepeatDialog = { showRepeatDialog = false },
        showMoveDialog = showMoveDialog,
        onDismissMoveDialog = { showMoveDialog = false },
        showTagPickerDialog = showTagPickerDialog,
        onDismissTagPickerDialog = { showTagPickerDialog = false },
        onScheduleChange = onScheduleChange,
        onScheduleWithReminderChange = onScheduleWithReminderChange,
        onDeadlineChange = onDeadlineChange,
        onRecurrenceChange = onRecurrenceChange,
        onMoveTaskToContainer = onMoveTaskToContainer,
        onUpdateTaskTags = onUpdateTaskTags,
        onCreateAndAssignTag = onCreateAndAssignTag,
        onOpenFullTagManager = onOpenFullTagManager
    )
}

private data class StrikeLineMetric(
    val left: Float,
    val right: Float,
    val centerY: Float
)

@Composable
private fun CollapsedTaskTitleWithStrike(
    task: TaskItem,
    assignedProject: Project?,
    assignedArea: Area?,
    isCompletingAnim: Boolean,
    strikeProgress: Float,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val isAlreadyDoneOrCanceled = task.status != TaskStatus.OPEN
    var measuredLines by remember(task.id, task.title) { mutableStateOf<List<StrikeLineMetric>>(emptyList()) }

    val animatedTitleColor by animateColorAsState(
        targetValue = when {
            isAlreadyDoneOrCanceled -> colors.tertiaryText
            isCompletingAnim -> colors.secondaryText
            else -> colors.primaryText
        },
        animationSpec = tween(durationMillis = 220),
        label = "collapsedTitleColor"
    )

    val strikeLineColor = colors.thingsBlue.copy(alpha = 0.85f)

    Column(modifier = modifier) {
        Text(
            text = task.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (isAlreadyDoneOrCanceled && !isCompletingAnim) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                }
            ),
            color = if (task.title.isBlank()) colors.tertiaryText else animatedTitleColor,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (result.lineCount > 0) {
                    measuredLines = (0 until result.lineCount).map { idx ->
                        val top = result.getLineTop(idx)
                        val bottom = result.getLineBottom(idx)
                        StrikeLineMetric(
                            left = result.getLineLeft(idx).coerceAtLeast(0f),
                            right = result.getLineRight(idx).coerceAtLeast(0f),
                            centerY = top + (bottom - top) * 0.53f
                        )
                    }
                }
            },
            modifier = Modifier.drawWithContent {
                drawContent()
                if (isCompletingAnim && strikeProgress > 0.01f) {
                    val progress = strikeProgress.coerceIn(0f, 1f)
                    if (measuredLines.isNotEmpty()) {
                        measuredLines.forEach { line ->
                            val lineWidth = (line.right - line.left).coerceAtLeast(0f).coerceAtMost(size.width)
                            val endX = line.left + (lineWidth * progress)
                            if (endX > line.left) {
                                drawLine(
                                    color = strikeLineColor,
                                    start = Offset(line.left, line.centerY),
                                    end = Offset(endX, line.centerY),
                                    strokeWidth = 1.65.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    } else {
                        val endX = (size.width * progress).coerceAtLeast(0f)
                        val centerY = size.height * 0.53f
                        drawLine(
                            color = strikeLineColor,
                            start = Offset(0f, centerY),
                            end = Offset(endX, centerY),
                            strokeWidth = 1.65.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        )

        val contextSubtitle = when {
            assignedProject != null && assignedArea != null -> "${assignedArea.title} › ${assignedProject.title}"
            assignedProject != null -> assignedProject.title
            assignedArea != null -> assignedArea.title
            else -> null
        }
        if (contextSubtitle != null) {
            Text(
                text = contextSubtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = colors.secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

private fun hasCollapsedTaskBadges(task: TaskItem, isActivelyBlocked: Boolean = false): Boolean {
    return task.scheduleState == ScheduleState.THIS_EVENING ||
        task.scheduleState == ScheduleState.UPCOMING ||
        task.scheduleState == ScheduleState.SOMEDAY ||
        task.scheduledStartMinutes != null ||
        task.durationMinutes != null ||
        EffortLevel.normalize(task.effortLevel) != null ||
        isActivelyBlocked ||
        task.recurrenceRule != null ||
        task.reminderTime != null ||
        task.checklistItems.isNotEmpty() ||
        task.attachments.isNotEmpty() ||
        task.tags.isNotEmpty() ||
        task.deadline != null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollapsedTaskInlineBadges(
    task: TaskItem,
    allOpenTasks: List<TaskItem> = emptyList(),
    openTaskIds: Set<String> = emptySet(),
    locale: Locale,
    onOpenWhenDialog: (() -> Unit)? = null,
    onOpenDeadlineDialog: (() -> Unit)? = null,
    onOpenTimeblockDialog: (() -> Unit)? = null,
    onUpdateStandaloneDuration: ((durationMinutes: Int?) -> Unit)? = null,
    onUpdateEffortLevel: ((effortLevel: String?) -> Unit)? = null,
    onOpenRepeatDialog: (() -> Unit)? = null,
    onOpenDependenciesDialog: (() -> Unit)? = null,
    onOpenTagPickerDialog: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    var showInlineEffortMenu by remember(task.id) { mutableStateOf(false) }
    var showInlineDurationMenu by remember(task.id) { mutableStateOf(false) }
    val activeOpenBlockerIds = remember(task.blockedByTaskIds, openTaskIds) {
        task.blockedByTaskIds.filter { it in openTaskIds }.distinct()
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (activeOpenBlockerIds.isNotEmpty()) {
            val firstBlockerTitle = remember(activeOpenBlockerIds, allOpenTasks) {
                if (activeOpenBlockerIds.size == 1) {
                    allOpenTasks.firstOrNull { it.id == activeOpenBlockerIds.first() }?.title?.trim()?.takeIf { it.isNotEmpty() }
                } else null
            }
            val blockedLabel = if (firstBlockerTitle != null) {
                stringResource(R.string.v30_blocked_by_badge, firstBlockerTitle)
            } else {
                stringResource(R.string.v30_blocked_by_count_badge, activeOpenBlockerIds.size)
            }
            ThingsHoverTooltip(
                text = blockedLabel,
                enableTouchLongPress = false
            ) {
                ThingsTagPill(
                    title = "⏳ $blockedLabel",
                    onClick = onOpenDependenciesDialog
                )
            }
        }
        val normalizedEffort = EffortLevel.normalize(task.effortLevel)
        if (normalizedEffort != null) {
            val (effortIcon, effortLabelRes) = when (normalizedEffort) {
                EffortLevel.QUICK_WIN -> "⚡" to R.string.v30_effort_quick_win
                EffortLevel.MEDIUM -> "◐" to R.string.v30_effort_medium
                else -> "🧠" to R.string.v30_effort_deep_focus
            }
            val effortLabel = stringResource(effortLabelRes)
            Box {
                ThingsHoverTooltip(text = effortLabel, enableTouchLongPress = false) {
                    ThingsTagPill(
                        title = "$effortIcon $effortLabel",
                        onClick = if (onUpdateEffortLevel != null) {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showInlineEffortMenu = true
                            }
                        } else null
                    )
                }
                if (onUpdateEffortLevel != null) {
                    DropdownMenu(
                        expanded = showInlineEffortMenu,
                        onDismissRequest = { showInlineEffortMenu = false },
                        modifier = Modifier.background(colors.cardElevatedSurface)
                    ) {
                        listOf(
                            null to stringResource(R.string.v30_effort_none),
                            EffortLevel.QUICK_WIN to "⚡ ${stringResource(R.string.v30_effort_quick_win)}",
                            EffortLevel.MEDIUM to "◐ ${stringResource(R.string.v30_effort_medium)}",
                            EffortLevel.DEEP_FOCUS to "🧠 ${stringResource(R.string.v30_effort_deep_focus)}"
                        ).forEach { (key, label) ->
                            val selected = normalizedEffort == key
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (selected) colors.thingsBlue else colors.primaryText
                                    )
                                },
                                onClick = {
                                    showInlineEffortMenu = false
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateEffortLevel(key)
                                }
                            )
                        }
                    }
                }
            }
        }
        if (task.scheduleState == ScheduleState.THIS_EVENING ||
            task.scheduleState == ScheduleState.UPCOMING ||
            task.scheduleState == ScheduleState.SOMEDAY
        ) {
            ThingsWhenBadge(
                scheduleState = task.scheduleState,
                whenDateEpochMillis = task.whenDate,
                locale = locale,
                onClick = onOpenWhenDialog
            )
        }
        if (task.scheduledStartMinutes != null) {
            val tbBadge = TimeblockScheduleUtils.formatTimeblockBadge(
                task.scheduledStartMinutes,
                task.durationMinutes
            )
            ThingsHoverTooltip(
                text = "${stringResource(R.string.v2_timeblock_title)}: $tbBadge",
                enableTouchLongPress = false
            ) {
                ThingsTagPill(
                    title = tbBadge,
                    onClick = onOpenTimeblockDialog
                )
            }
        } else if (task.durationMinutes != null) {
            val durFormatted = formatDurationCompact(task.durationMinutes) ?: "${task.durationMinutes}m"
            Box {
                ThingsHoverTooltip(
                    text = "${stringResource(R.string.v30_duration_label)}: $durFormatted",
                    enableTouchLongPress = false
                ) {
                    ThingsTagPill(
                        title = "⏱ $durFormatted",
                        onClick = if (onUpdateStandaloneDuration != null) {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showInlineDurationMenu = true
                            }
                        } else {
                            onOpenTimeblockDialog
                        }
                    )
                }
                if (onUpdateStandaloneDuration != null) {
                    DropdownMenu(
                        expanded = showInlineDurationMenu,
                        onDismissRequest = { showInlineDurationMenu = false },
                        modifier = Modifier.background(colors.cardElevatedSurface)
                    ) {
                        listOf(
                            5 to "5m",
                            15 to "15m",
                            30 to "30m",
                            60 to "1h",
                            120 to "2h",
                            240 to "4h"
                        ).forEach { (mins, label) ->
                            val selected = task.durationMinutes == mins
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (selected) colors.thingsBlue else colors.primaryText
                                    )
                                },
                                onClick = {
                                    showInlineDurationMenu = false
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onUpdateStandaloneDuration(mins)
                                }
                            )
                        }
                        HorizontalDivider(color = colors.hairlineDivider)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.v30_duration_clear),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.secondaryText
                                )
                            },
                            onClick = {
                                showInlineDurationMenu = false
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateStandaloneDuration(null)
                            }
                        )
                    }
                }
            }
        }
        task.recurrenceRule?.let { rr ->
            ThingsHoverTooltip(
                text = stringResource(R.string.context_configure_repeat),
                enableTouchLongPress = false
            ) {
                ThingsRepeatBadge(
                    rule = rr,
                    onClick = onOpenRepeatDialog
                )
            }
        }
        task.reminderTime?.let { rt ->
            ThingsReminderBadge(
                reminderEpochMillis = rt,
                onClick = onOpenWhenDialog
            )
        }
        if (task.checklistItems.isNotEmpty()) {
            val doneCount = task.checklistItems.count { it.isCompleted }
            ThingsHoverTooltip(
                text = "${stringResource(R.string.placeholder_checklist)}: $doneCount/${task.checklistItems.size}",
                enableTouchLongPress = false
            ) {
                ThingsTagPill(title = "☑ $doneCount/${task.checklistItems.size}")
            }
        }
        if (task.attachments.isNotEmpty()) {
            ThingsHoverTooltip(
                text = "${stringResource(R.string.attachments_section_title)}: ${task.attachments.size}",
                enableTouchLongPress = false
            ) {
                ThingsTagPill(title = "📎 ${task.attachments.size}")
            }
        }
        task.tags.forEach { tag ->
            ThingsHoverTooltip(
                text = "#${tag.title}",
                enableTouchLongPress = false
            ) {
                ThingsTagPill(
                    title = tag.title,
                    onClick = onOpenTagPickerDialog
                )
            }
        }
        task.deadline?.let { dl ->
            ThingsDeadlineBadge(
                deadlineEpochMillis = dl,
                locale = locale,
                onClick = onOpenDeadlineDialog
            )
        }
    }
}

@Composable
private fun SwipeActionBackgroundLayer(
    animatedSwipeOffsetPx: Float,
    deleteSwipeThresholdPx: Float,
    isSelected: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    verticalPadding: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val isSwipingRightForWhen = animatedSwipeOffsetPx > 0f
    val isDeepSwipeLeftForDelete = animatedSwipeOffsetPx <= deleteSwipeThresholdPx
    val bgHighlightColor = when {
        isSwipingRightForWhen -> colors.todayStarYellow.copy(alpha = 0.90f)
        isDeepSwipeLeftForDelete -> colors.deadlineUrgentRed.copy(alpha = 0.92f)
        else -> colors.thingsBlue.copy(alpha = 0.90f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgHighlightColor)
            .padding(horizontal = 14.dp),
        contentAlignment = if (isSwipingRightForWhen) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSwipingRightForWhen) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = stringResource(R.string.action_when),
                    tint = Color(0xFF1A1C1E),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${stringResource(R.string.action_when)}?",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF1A1C1E)
                )
            } else if (isDeepSwipeLeftForDelete) {
                Text(
                    text = stringResource(R.string.action_delete),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            } else {
                val selectLabel = if (isSelected) stringResource(R.string.swipe_deselect) else stringResource(R.string.swipe_select)
                Text(
                    text = selectLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = selectLabel,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

internal const val SOFT_BENTO_MODAL_WIDE_BREAKPOINT_DP = 600

internal fun isWideSoftBentoTaskModal(maxWidthDp: Float): Boolean =
    maxWidthDp >= SOFT_BENTO_MODAL_WIDE_BREAKPOINT_DP

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoftBentoPropertyGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = ThingsTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
            color = colors.secondaryText
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedTaskCardBody(
    task: TaskItem,
    effectiveVisualStatus: TaskStatus,
    openTaskIds: Set<String> = emptySet(),
    assignedProject: Project?,
    assignedArea: Area?,
    assignedHeading: Heading? = null,
    showQuickThisEveningPill: Boolean = false,
    titleFocusRequester: FocusRequester,
    editedTitleField: TextFieldValue,
    onTitleFieldChange: (TextFieldValue) -> Unit,
    onTitleFocusChanged: (Boolean) -> Unit,
    editedNotesField: TextFieldValue,
    onNotesFieldChange: (TextFieldValue) -> Unit,
    onNotesFocusChanged: (Boolean) -> Unit,
    onNewChecklistFocusChanged: (Boolean) -> Unit,
    onSubtaskFocusDelta: (Int) -> Unit,
    onRequestEnsureVisible: ((Boolean) -> Unit)? = null,
    isGeneratingMagicBreakdown: Boolean,
    locale: Locale,
    onStatusChange: (TaskStatus) -> Unit,
    onAddChecklistItem: (String) -> Unit,
    onInsertChecklistItemAfter: ((afterItemId: String?, newItemId: String, title: String) -> Unit)?,
    onToggleChecklistItem: (String, Boolean) -> Unit,
    onRenameChecklistItem: ((String, String) -> Unit)?,
    onMoveChecklistItem: ((String, Boolean) -> Unit)?,
    onDeleteChecklistItem: ((String) -> Unit)?,
    onAddTaskAttachment: ((fileName: String, mimeType: String, bytes: ByteArray) -> Unit)? = null,
    onDeleteTaskAttachment: ((attachmentId: String) -> Unit)? = null,
    onShowMessage: ((String) -> Unit)? = null,
    onDictateTitleOrNotes: ((String) -> Unit)? = null,
    onTriggerGeminiMagicBreakdown: () -> Unit,
    onOpenWhenDialog: () -> Unit,
    onQuickThisEveningAndClose: (() -> Unit)? = null,
    onOpenDeadlineDialog: () -> Unit,
    onOpenRepeatDialog: () -> Unit,
    onOpenTimeblockDialog: (() -> Unit)? = null,
    onUpdateEffortLevel: ((effortLevel: String?) -> Unit)? = null,
    onUpdateStandaloneDuration: ((durationMinutes: Int?) -> Unit)? = null,
    onOpenDependenciesDialog: (() -> Unit)? = null,
    onOpenMoveDialog: () -> Unit,
    onOpenTagPickerDialog: () -> Unit,
    onTogglePin: (() -> Unit)? = null,
    onDuplicateTask: (() -> Unit)? = null,
    onStartFocusTimer: (() -> Unit)? = null,
    onDeleteTask: () -> Unit,
    onDoneCollapse: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val newChecklistFocusRequester = remember(task.id) { FocusRequester() }
    var pendingAutoFocusSubtaskId by remember(task.id) { mutableStateOf<String?>(null) }
    var newChecklistField by remember(task.id) {
        mutableStateOf(TextFieldValue(text = "", selection = TextRange(0)))
    }
    val flushPendingChecklistIfNeeded = {
        val cleaned = newChecklistField.text.replace("\r", "").replace("\n", " ").trim()
        if (cleaned.isNotEmpty()) {
            onAddChecklistItem(cleaned)
            newChecklistField = TextFieldValue(text = "", selection = TextRange(0))
        }
    }
    val tooLargeMsg = stringResource(R.string.attachment_too_large)
    val importErrorMsg = stringResource(R.string.attachment_import_error)
    val voicePromptMsg = stringResource(R.string.voice_capture_prompt)
    val voiceUnavailableMsg = stringResource(R.string.voice_not_available)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && onAddTaskAttachment != null) {
            scope.launch {
                when (val outcome = processPickedAttachmentUri(context, uri)) {
                    is AttachmentImportOutcome.Success -> {
                        onAddTaskAttachment(
                            outcome.result.fileName,
                            outcome.result.mimeType,
                            outcome.result.bytes
                        )
                    }
                    AttachmentImportOutcome.TooLarge -> {
                        onShowMessage?.invoke(tooLargeMsg)
                    }
                    AttachmentImportOutcome.Error -> {
                        onShowMessage?.invoke(importErrorMsg)
                    }
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null && onAddTaskAttachment != null) {
            val processed = processCapturedPhotoBitmap(bitmap)
            onAddTaskAttachment(processed.fileName, processed.mimeType, processed.bytes)
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
                onDictateTitleOrNotes?.invoke(spoken)
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
            onShowMessage?.invoke(voiceUnavailableMsg)
        }
    }

    val bentoZoneFill = if (colors.isDark) {
        colors.cardElevatedSurface
    } else {
        colors.sidebarBackground.copy(alpha = 0.72f)
    }
    val bentoZoneBorder = BorderStroke(width = 0.75.dp, color = colors.hairlineDivider)

    val renderPinnedHeaderRow: @Composable () -> Unit = {
        // Pinned Top Header Row: Checkbox + Large Title BasicTextField + Pin + Close ✕
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val checkboxTooltip = if (effectiveVisualStatus == TaskStatus.OPEN) {
                stringResource(R.string.action_complete)
            } else {
                stringResource(R.string.action_reopen_todo)
            }
            ThingsHoverTooltip(
                text = checkboxTooltip,
                enableTouchLongPress = false
            ) {
                ThingsCheckbox(
                    status = effectiveVisualStatus,
                    onStatusChange = onStatusChange,
                    isDashedSomeday = task.scheduleState == ScheduleState.SOMEDAY,
                    size = 22.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            BasicTextField(
                value = editedTitleField,
                onValueChange = onTitleFieldChange,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 19.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                cursorBrush = SolidColor(colors.thingsBlue),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(titleFocusRequester)
                    .pointerInput(task.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                if (ev.type == PointerEventType.Press) {
                                    onRequestEnsureVisible?.invoke(false)
                                }
                            }
                        }
                    }
                    .onFocusChanged { focusState ->
                        onTitleFocusChanged(focusState.isFocused)
                    },
                decorationBox = { innerTitleField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (editedTitleField.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.placeholder_todo_title),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 19.sp,
                                    lineHeight = 25.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = colors.tertiaryText
                            )
                        }
                        innerTitleField()
                    }
                }
            )

            if (onTogglePin != null) {
                Spacer(modifier = Modifier.width(8.dp))
                val pinLabel = stringResource(
                    if (task.isPinned) R.string.v25_unpin_task else R.string.v25_pin_task
                )
                ThingsHoverTooltip(text = pinLabel) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (task.isPinned) colors.thingsBlueSoft else colors.tagPillBg.copy(alpha = 0.65f)
                            )
                            .border(
                                width = 0.75.dp,
                                color = if (task.isPinned) colors.thingsBlue.copy(alpha = 0.45f) else colors.hairlineDivider,
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTogglePin()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = pinLabel,
                            tint = if (task.isPinned) colors.thingsBlue else colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            ThingsHoverTooltip(text = stringResource(R.string.action_done)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.tagPillBg.copy(alpha = 0.65f))
                        .border(
                            width = 0.75.dp,
                            color = colors.hairlineDivider,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            flushPendingChecklistIfNeeded()
                            onDoneCollapse()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_done),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    val renderContentZone: @Composable (Modifier) -> Unit = { zoneModifier ->
        Column(
            modifier = zoneModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Soft Bento Notes Container (Markdown notes + detected URL / Email / Phone / Map chips)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = bentoZoneFill,
                border = bentoZoneBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BasicTextField(
                        value = editedNotesField,
                        onValueChange = onNotesFieldChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.primaryText,
                            lineHeight = 21.sp
                        ),
                        visualTransformation = rememberThingsMarkdownVisualTransformation(),
                        cursorBrush = SolidColor(colors.thingsBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .pointerInput(task.id) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                        if (ev.type == PointerEventType.Press) {
                                            onRequestEnsureVisible?.invoke(false)
                                        }
                                    }
                                }
                            }
                            .onFocusChanged { focusState ->
                                onNotesFocusChanged(focusState.isFocused)
                            },
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (editedNotesField.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.placeholder_notes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.tertiaryText
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    DetectedNotesLinksRow(notes = editedNotesField.text)
                }
            }

            // Soft Bento Checklist Container (progress count + ✨ Gemini Magic Breakdown + rows + add item field)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = bentoZoneFill,
                border = bentoZoneBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val doneCount = task.checklistItems.count { it.isCompleted }
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
                                text = if (task.checklistItems.isNotEmpty()) {
                                    "${stringResource(R.string.placeholder_checklist)} ($doneCount/${task.checklistItems.size})"
                                } else {
                                    stringResource(R.string.placeholder_checklist)
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (task.checklistItems.isNotEmpty()) colors.thingsBlue else colors.secondaryText
                            )
                        }

                        ThingsHoverTooltip(text = stringResource(R.string.gemini_magic_breakdown_desc)) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.thingsBlueSoft)
                                    .clickable(enabled = !isGeneratingMagicBreakdown) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTriggerGeminiMagicBreakdown()
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isGeneratingMagicBreakdown) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 1.8.dp,
                                        color = colors.thingsBlue
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = stringResource(R.string.gemini_magic_breakdown),
                                        tint = colors.thingsBlue,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.gemini_magic_breakdown),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.thingsBlue
                                )
                            }
                        }
                    }

                    if (task.checklistItems.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            task.checklistItems.forEachIndexed { idx, item ->
                                key(item.id) {
                                    InlineChecklistRow(
                                        item = item,
                                        canMoveUp = idx > 0,
                                        canMoveDown = idx < task.checklistItems.lastIndex,
                                        shouldAutoFocus = pendingAutoFocusSubtaskId == item.id,
                                        onAutoFocusConsumed = {
                                            if (pendingAutoFocusSubtaskId == item.id) {
                                                pendingAutoFocusSubtaskId = null
                                            }
                                        },
                                        onFocusDelta = onSubtaskFocusDelta,
                                        onToggle = { checked ->
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onToggleChecklistItem(item.id, checked)
                                        },
                                        onRename = { newSubtaskTitle ->
                                            onRenameChecklistItem?.invoke(item.id, newSubtaskTitle)
                                        },
                                        onReturnCreateSubtask = { remainingCurrentTitle, nextInitialTitle ->
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (remainingCurrentTitle != item.title) {
                                                onRenameChecklistItem?.invoke(item.id, remainingCurrentTitle)
                                            }
                                            val nextSubtaskId = UUID.randomUUID().toString()
                                            pendingAutoFocusSubtaskId = nextSubtaskId
                                            if (onInsertChecklistItemAfter != null) {
                                                onInsertChecklistItemAfter(item.id, nextSubtaskId, nextInitialTitle)
                                            } else {
                                                onAddChecklistItem(nextInitialTitle)
                                            }
                                        },
                                        onReturnOnEmptySubtask = {
                                            onDeleteChecklistItem?.invoke(item.id)
                                            runCatching { newChecklistFocusRequester.requestFocus() }
                                        },
                                        onMoveUp = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onMoveChecklistItem?.invoke(item.id, true)
                                        },
                                        onDelete = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDeleteChecklistItem?.invoke(item.id)
                                        },
                                        onMoveDown = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onMoveChecklistItem?.invoke(item.id, false)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Add new subtask inline field + explicit '+' Button
                    key("new_checklist_input_row_${task.id}") {
                        val submitNewChecklistEntry = {
                            val cleaned = newChecklistField.text.replace("\r", "").replace("\n", " ").trim()
                            if (cleaned.isNotEmpty()) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAddChecklistItem(cleaned)
                                newChecklistField = TextFieldValue(text = "", selection = TextRange(0))
                                runCatching { newChecklistFocusRequester.requestFocus() }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(colors.cardSurface)
                                .border(
                                    width = 0.75.dp,
                                    color = colors.hairlineDivider,
                                    shape = RoundedCornerShape(9.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = newChecklistField,
                                onValueChange = { incoming ->
                                    if ('\n' in incoming.text || '\r' in incoming.text) {
                                        val parts = incoming.text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
                                        var addedAny = false
                                        for (i in 0 until parts.lastIndex) {
                                            val line = parts[i].trim()
                                            if (line.isNotEmpty()) {
                                                onAddChecklistItem(line)
                                                addedAny = true
                                            }
                                        }
                                        if (addedAny) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        val trailing = parts.last()
                                        newChecklistField = TextFieldValue(
                                            text = trailing,
                                            selection = TextRange(trailing.length)
                                        )
                                    } else {
                                        newChecklistField = incoming
                                    }
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.primaryText),
                                cursorBrush = SolidColor(colors.thingsBlue),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = { submitNewChecklistEntry() },
                                    onDone = { submitNewChecklistEntry() }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(newChecklistFocusRequester)
                                    .pointerInput(task.id) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                                if (ev.type == PointerEventType.Press) {
                                                    onRequestEnsureVisible?.invoke(true)
                                                }
                                            }
                                        }
                                    }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) {
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                submitNewChecklistEntry()
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    .onFocusChanged { focusState ->
                                        onNewChecklistFocusChanged(focusState.isFocused)
                                    },
                                decorationBox = { inner ->
                                    Box {
                                        if (newChecklistField.text.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.placeholder_add_checklist_item),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.tertiaryText
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                            if (newChecklistField.text.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                ThingsHoverTooltip(text = stringResource(R.string.placeholder_add_checklist_item)) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.thingsBlue)
                                            .clickable { submitNewChecklistEntry() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = stringResource(R.string.placeholder_add_checklist_item),
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Soft Bento Attachments Strip
            if (task.attachments.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = bentoZoneFill,
                    border = bentoZoneBorder
                ) {
                    TaskAttachmentsStrip(
                        attachments = task.attachments,
                        onDeleteAttachment = { attId -> onDeleteTaskAttachment?.invoke(attId) },
                        onAttachFileClick = if (onAddTaskAttachment != null) {
                            { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) }
                        } else null,
                        onTakePhotoClick = if (onAddTaskAttachment != null) {
                            { cameraLauncher.launch(null) }
                        } else null,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    val renderPropertiesZone: @Composable (Modifier) -> Unit = { zoneModifier ->
        Surface(
            modifier = zoneModifier,
            shape = RoundedCornerShape(16.dp),
            color = bentoZoneFill,
            border = bentoZoneBorder
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Schedule: When + Today This Evening + Reminder + Timeblock + Deadline + Repeat
                SoftBentoPropertyGroup(
                    label = "${stringResource(R.string.action_when)} · ${stringResource(R.string.action_deadline)}"
                ) {
                    ThingsWhenBadge(
                        scheduleState = task.scheduleState,
                        whenDateEpochMillis = task.whenDate,
                        locale = locale,
                        onClick = onOpenWhenDialog
                    )

                    if (showQuickThisEveningPill && onQuickThisEveningAndClose != null) {
                        val eveningLabel = stringResource(R.string.view_this_evening)
                        ThingsHoverTooltip(text = eveningLabel) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.eveningMoonIndigo.copy(alpha = 0.14f))
                                    .border(
                                        width = 0.75.dp,
                                        color = colors.eveningMoonIndigo.copy(alpha = 0.30f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        flushPendingChecklistIfNeeded()
                                        onQuickThisEveningAndClose()
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Bedtime,
                                    contentDescription = eveningLabel,
                                    tint = colors.eveningMoonIndigo,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = eveningLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.eveningMoonIndigo
                                )
                            }
                        }
                    }

                    task.reminderTime?.let { rt ->
                        ThingsReminderBadge(
                            reminderEpochMillis = rt,
                            onClick = onOpenWhenDialog
                        )
                    }

                    if (task.scheduledStartMinutes != null) {
                        ThingsTagPill(
                            title = TimeblockScheduleUtils.formatTimeblockBadge(
                                task.scheduledStartMinutes,
                                task.durationMinutes
                            ),
                            onClick = onOpenTimeblockDialog
                        )
                    } else if (onOpenTimeblockDialog != null) {
                        ThingsTagPill(
                            title = "⏱️ ${stringResource(R.string.v2_timeblock_title)}",
                            onClick = onOpenTimeblockDialog
                        )
                    }

                    if (task.deadline != null) {
                        ThingsDeadlineBadge(
                            deadlineEpochMillis = task.deadline,
                            locale = locale,
                            onClick = onOpenDeadlineDialog
                        )
                    } else {
                        ThingsTagPill(
                            title = "🏁 ${stringResource(R.string.action_deadline)}",
                            onClick = onOpenDeadlineDialog
                        )
                    }

                    if (task.recurrenceRule != null) {
                        ThingsRepeatBadge(
                            rule = task.recurrenceRule,
                            onClick = onOpenRepeatDialog
                        )
                    } else {
                        ThingsTagPill(
                            title = "🔁 ${stringResource(R.string.action_repeat)}",
                            onClick = onOpenRepeatDialog
                        )
                    }
                }

                // 2. Focus & Dependencies: Duration, Effort & Waiting On
                if (onUpdateStandaloneDuration != null || onUpdateEffortLevel != null || onOpenDependenciesDialog != null) {
                    HorizontalDivider(color = colors.hairlineDivider.copy(alpha = 0.7f), thickness = 0.65.dp)
                    SoftBentoPropertyGroup(
                        label = "${stringResource(R.string.v30_duration_label)} · ${stringResource(R.string.v30_effort_label)}"
                    ) {
                        if (onUpdateStandaloneDuration != null) {
                            var showDurationPickerMenu by remember(task.id) { mutableStateOf(false) }
                            val durText = formatDurationCompact(task.durationMinutes)
                            Box {
                                ThingsTagPill(
                                    title = if (durText != null) {
                                        "⏱ $durText"
                                    } else {
                                        "⏱ ${stringResource(R.string.v30_duration_label)}"
                                    },
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showDurationPickerMenu = true
                                    }
                                )
                                DropdownMenu(
                                    expanded = showDurationPickerMenu,
                                    onDismissRequest = { showDurationPickerMenu = false },
                                    modifier = Modifier.background(colors.cardElevatedSurface)
                                ) {
                                    listOf(
                                        5 to "5m",
                                        15 to "15m",
                                        30 to "30m",
                                        60 to "1h",
                                        120 to "2h",
                                        240 to "4h"
                                    ).forEach { (mins, label) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = if (task.durationMinutes == mins) FontWeight.Bold else FontWeight.Normal
                                                    ),
                                                    color = if (task.durationMinutes == mins) colors.thingsBlue else colors.primaryText
                                                )
                                            },
                                            onClick = {
                                                showDurationPickerMenu = false
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onUpdateStandaloneDuration(mins)
                                            }
                                        )
                                    }
                                    if (task.durationMinutes != null) {
                                        HorizontalDivider(color = colors.hairlineDivider)
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(R.string.v30_duration_clear),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colors.secondaryText
                                                )
                                            },
                                            onClick = {
                                                showDurationPickerMenu = false
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onUpdateStandaloneDuration(null)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (onUpdateEffortLevel != null) {
                            var showEffortPickerMenu by remember(task.id) { mutableStateOf(false) }
                            val normalizedEffort = EffortLevel.normalize(task.effortLevel)
                            val effortPillTitle = when (normalizedEffort) {
                                EffortLevel.QUICK_WIN -> "⚡ ${stringResource(R.string.v30_effort_quick_win)}"
                                EffortLevel.MEDIUM -> "◐ ${stringResource(R.string.v30_effort_medium)}"
                                EffortLevel.DEEP_FOCUS -> "🧠 ${stringResource(R.string.v30_effort_deep_focus)}"
                                else -> "⚡ ${stringResource(R.string.v30_effort_label)}"
                            }
                            Box {
                                ThingsTagPill(
                                    title = effortPillTitle,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showEffortPickerMenu = true
                                    }
                                )
                                DropdownMenu(
                                    expanded = showEffortPickerMenu,
                                    onDismissRequest = { showEffortPickerMenu = false },
                                    modifier = Modifier.background(colors.cardElevatedSurface)
                                ) {
                                    val effortOptions: List<Pair<String?, String>> = listOf(
                                        null to stringResource(R.string.v30_effort_none),
                                        EffortLevel.QUICK_WIN to "⚡ ${stringResource(R.string.v30_effort_quick_win)}",
                                        EffortLevel.MEDIUM to "◐ ${stringResource(R.string.v30_effort_medium)}",
                                        EffortLevel.DEEP_FOCUS to "🧠 ${stringResource(R.string.v30_effort_deep_focus)}"
                                    )
                                    effortOptions.forEach { (key, label) ->
                                        val selected = normalizedEffort == key
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                                    ),
                                                    color = if (selected) colors.thingsBlue else colors.primaryText
                                                )
                                            },
                                            onClick = {
                                                showEffortPickerMenu = false
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onUpdateEffortLevel(key)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (onOpenDependenciesDialog != null) {
                            val activeBlockerCount = remember(task.blockedByTaskIds, openTaskIds) {
                                task.blockedByTaskIds.count { it in openTaskIds }
                            }
                            val depLabel = if (activeBlockerCount > 0) {
                                "⏳ " + stringResource(R.string.v30_blocked_by_count_badge, activeBlockerCount)
                            } else {
                                "⏳ " + stringResource(R.string.v30_blocked_by_action)
                            }
                            ThingsTagPill(
                                title = depLabel,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenDependenciesDialog()
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = colors.hairlineDivider.copy(alpha = 0.7f), thickness = 0.65.dp)

                // 3. Organization: Project / Area & Categories
                SoftBentoPropertyGroup(
                    label = "${stringResource(R.string.entity_project)} / ${stringResource(R.string.entity_area)} · ${stringResource(R.string.action_tags)}"
                ) {
                    val containerLabel = when {
                        assignedProject != null && assignedHeading != null && assignedHeading.title.isNotBlank() ->
                            "📁 ${assignedProject.title} › ${assignedHeading.title}"
                        assignedProject != null -> "📁 ${assignedProject.title}"
                        assignedArea != null -> "📦 ${assignedArea.title}"
                        else -> "📁 ${stringResource(R.string.entity_project)} / ${stringResource(R.string.entity_area)}"
                    }
                    ThingsTagPill(
                        title = containerLabel,
                        onClick = onOpenMoveDialog
                    )

                    task.tags.forEach { tag ->
                        ThingsTagPill(
                            title = tag.title,
                            onClick = onOpenTagPickerDialog
                        )
                    }
                    ThingsTagPill(
                        title = "+ " + stringResource(R.string.action_tags),
                        onClick = onOpenTagPickerDialog
                    )
                }
            }
        }
    }

    var showInlineMoreMenu by remember(task.id) { mutableStateOf(false) }
    val bodyScrollState = rememberScrollState()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isWideModal = maxWidth >= 600.dp && isWideSoftBentoTaskModal(maxWidth.value)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isWideModal) 22.dp else 16.dp,
                    end = if (isWideModal) 22.dp else 16.dp,
                    top = if (isWideModal) 18.dp else 14.dp,
                    bottom = if (isWideModal) 12.dp else 10.dp
                )
        ) {
            // Zone 1: Pinned Top Header Row
            renderPinnedHeaderRow()

            // Zone 2: Scrollable Middle Content & Properties Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(bodyScrollState)
            ) {
                if (isWideModal) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        renderContentZone(Modifier.weight(1.35f))
                        renderPropertiesZone(Modifier.weight(1f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        renderContentZone(Modifier.fillMaxWidth())
                        renderPropertiesZone(Modifier.fillMaxWidth())
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Zone 3: Pinned Non-Clipped Unified Footer
            HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)

            // v2.1 Unified Single Footer Row: Quick Utility Pills on Left + [Focus, ⋯ Menu, ✓ Done] on Right
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    if (onDictateTitleOrNotes != null) {
                        ThingsTagPill(
                            title = "🎙 ${stringResource(R.string.voice_capture_btn)}",
                            onClick = { launchVoiceDictation() }
                        )
                    }
                    if (onAddTaskAttachment != null) {
                        ThingsTagPill(
                            title = "📎 ${stringResource(R.string.action_attach_file)}",
                            onClick = { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onStartFocusTimer != null) {
                        ThingsHoverTooltip(text = stringResource(R.string.action_focus_timer)) {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.tagPillBg)
                                    .border(
                                        width = 0.75.dp,
                                        color = colors.hairlineDivider,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onStartFocusTimer()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = stringResource(R.string.action_focus_timer),
                                    tint = colors.primaryText,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    Box {
                        ThingsHoverTooltip(text = stringResource(R.string.action_more_options)) {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.tagPillBg)
                                    .border(
                                        width = 0.75.dp,
                                        color = colors.hairlineDivider,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showInlineMoreMenu = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    contentDescription = stringResource(R.string.action_more_options),
                                    tint = colors.primaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showInlineMoreMenu,
                            onDismissRequest = { showInlineMoreMenu = false },
                            modifier = Modifier.background(colors.cardElevatedSurface)
                        ) {
                            if (onDictateTitleOrNotes != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.voice_capture_btn),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.primaryText
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Mic,
                                            contentDescription = null,
                                            tint = colors.thingsBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showInlineMoreMenu = false
                                        launchVoiceDictation()
                                    }
                                )
                            }

                            if (onAddTaskAttachment != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.action_attach_file),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.primaryText
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.AttachFile,
                                            contentDescription = null,
                                            tint = colors.thingsBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showInlineMoreMenu = false
                                        filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.action_take_photo),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.primaryText
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.CameraAlt,
                                            contentDescription = null,
                                            tint = colors.thingsBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showInlineMoreMenu = false
                                        cameraLauncher.launch(null)
                                    }
                                )
                            }

                            if (onTogglePin != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(
                                                if (task.isPinned) R.string.v25_unpin_task else R.string.v25_pin_task
                                            ),
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
                                        showInlineMoreMenu = false
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onTogglePin()
                                    }
                                )
                            }

                            if (onOpenDependenciesDialog != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.v30_blocked_by_action),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.primaryText
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.HourglassTop,
                                            contentDescription = null,
                                            tint = colors.thingsBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showInlineMoreMenu = false
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onOpenDependenciesDialog()
                                    }
                                )
                            }

                            if (onDuplicateTask != null) {
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
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showInlineMoreMenu = false
                                        onDuplicateTask()
                                    }
                                )
                            }

                            HorizontalDivider(color = colors.hairlineDivider)

                            val isAlreadyCanceled = task.status == TaskStatus.CANCELED
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
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showInlineMoreMenu = false
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onStatusChange(if (isAlreadyCanceled) TaskStatus.OPEN else TaskStatus.CANCELED)
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
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showInlineMoreMenu = false
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDeleteTask()
                                }
                            )
                        }
                    }

                    ThingsHoverTooltip(text = stringResource(R.string.action_done)) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.thingsBlue)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    flushPendingChecklistIfNeeded()
                                    onDoneCollapse()
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = stringResource(R.string.action_done),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.action_done),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskContextDropdownMenu(
    task: TaskItem,
    isSelected: Boolean,
    offset: DpOffset,
    onStartInlineQuickEdit: (() -> Unit)? = null,
    onToggleSelection: (() -> Unit)?,
    onStatusChange: (TaskStatus) -> Unit,
    onOpenWhenDialog: () -> Unit,
    onOpenDeadlineDialog: () -> Unit,
    onOpenRepeatDialog: () -> Unit,
    onOpenTimeblockDialog: (() -> Unit)? = null,
    onUpdateEffortLevel: ((effortLevel: String?) -> Unit)? = null,
    onUpdateStandaloneDuration: ((durationMinutes: Int?) -> Unit)? = null,
    onOpenDependenciesDialog: (() -> Unit)? = null,
    onOpenMoveDialog: () -> Unit,
    onOpenTagPickerDialog: () -> Unit,
    onTriggerGeminiMagicBreakdown: () -> Unit,
    onStartFocusTimer: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onDuplicateTask: (() -> Unit)? = null,
    onDeleteTask: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val targetTaskTitle = task.title.ifBlank { stringResource(R.string.placeholder_todo_title) }
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier.background(colors.cardElevatedSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.thingsBlueSoft.copy(alpha = 0.65f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.thingsBlue)
            )
            Text(
                text = targetTaskTitle,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider(color = colors.hairlineDivider)

        if (onStartInlineQuickEdit != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.v30_quick_edit_action),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.thingsBlue
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onDismiss()
                    onStartInlineQuickEdit()
                }
            )
        }

        if (onToggleSelection != null) {
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
                    onDismiss()
                    onToggleSelection()
                }
            )
        }
        if (onTogglePin != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(
                            if (task.isPinned) R.string.v25_unpin_task else R.string.v25_pin_task
                        ),
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
                    onDismiss()
                    onTogglePin()
                }
            )
        }
        val isCompleted = task.status == TaskStatus.COMPLETED
        DropdownMenuItem(
            text = {
                Text(
                    text = if (isCompleted) stringResource(R.string.action_reopen_todo) else stringResource(R.string.action_complete),
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
                onDismiss()
                onStatusChange(if (isCompleted) TaskStatus.OPEN else TaskStatus.COMPLETED)
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.context_when_date_reminder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onOpenWhenDialog()
            }
        )
        if (onOpenTimeblockDialog != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.v2_timeblock_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primaryText
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onDismiss()
                    onOpenTimeblockDialog()
                }
            )
        }
        if (onUpdateStandaloneDuration != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.v30_duration_label) + ":",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
                listOf(15 to "15m", 30 to "30m", 60 to "1h").forEach { (mins, lbl) ->
                    ThingsTagPill(
                        title = lbl,
                        onClick = {
                            onDismiss()
                            onUpdateStandaloneDuration(if (task.durationMinutes == mins) null else mins)
                        }
                    )
                }
            }
        }
        if (onUpdateEffortLevel != null) {
            val currentEff = EffortLevel.normalize(task.effortLevel)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.v30_effort_label) + ":",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
                listOf(
                    EffortLevel.QUICK_WIN to "⚡",
                    EffortLevel.MEDIUM to "◐",
                    EffortLevel.DEEP_FOCUS to "🧠"
                ).forEach { (effKey, icon) ->
                    ThingsTagPill(
                        title = icon,
                        onClick = {
                            onDismiss()
                            onUpdateEffortLevel(if (currentEff == effKey) null else effKey)
                        }
                    )
                }
            }
        }
        if (onOpenDependenciesDialog != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.v30_blocked_by_action),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primaryText
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.HourglassTop,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onDismiss()
                    onOpenDependenciesDialog()
                }
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.context_adjust_deadline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    tint = colors.deadlineUrgentRed,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onOpenDeadlineDialog()
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.context_configure_repeat),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Repeat,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onOpenRepeatDialog()
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.move_dialog_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onOpenMoveDialog()
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.context_assign_tags),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onOpenTagPickerDialog()
            }
        )
        if (onStartFocusTimer != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_focus_timer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primaryText
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onDismiss()
                    onStartFocusTimer()
                }
            )
        }
        if (onDuplicateTask != null) {
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
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    onDismiss()
                    onDuplicateTask()
                }
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    text = "${stringResource(R.string.gemini_magic_breakdown)} (Gemini ✨)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.thingsBlue
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onTriggerGeminiMagicBreakdown()
            }
        )
        HorizontalDivider(color = colors.hairlineDivider)
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.action_cancel_todo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onStatusChange(TaskStatus.CANCELED)
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
                    modifier = Modifier.size(18.dp)
                )
            },
            onClick = {
                onDismiss()
                onDeleteTask()
            }
        )
    }
}

@Composable
private fun TaskCardDialogsHost(
    task: TaskItem,
    allTags: List<Tag>,
    tagSortMode: String = "alpha",
    tagUsageCounts: Map<String, Int> = emptyMap(),
    allAreas: List<Area>,
    allProjects: List<Project>,
    allHeadings: List<Heading>,
    locale: Locale,
    showWhenDialog: Boolean,
    onDismissWhenDialog: () -> Unit,
    showDeadlineDialog: Boolean,
    onDismissDeadlineDialog: () -> Unit,
    showRepeatDialog: Boolean,
    onDismissRepeatDialog: () -> Unit,
    showMoveDialog: Boolean,
    onDismissMoveDialog: () -> Unit,
    showTagPickerDialog: Boolean,
    onDismissTagPickerDialog: () -> Unit,
    onScheduleChange: (ScheduleState, Long?) -> Unit,
    onScheduleWithReminderChange: ((ScheduleState, Long?, Long?) -> Unit)?,
    onDeadlineChange: (Long?) -> Unit,
    onRecurrenceChange: ((RecurrenceRule?) -> Unit)?,
    onMoveTaskToContainer: ((projectId: String?, headingId: String?, areaId: String?) -> Unit)?,
    onUpdateTaskTags: ((List<String>) -> Unit)?,
    onCreateAndAssignTag: ((String) -> Unit)?,
    onOpenFullTagManager: (() -> Unit)?
) {
    if (showWhenDialog) {
        WhenNaturalDateDialog(
            currentScheduleState = task.scheduleState,
            currentWhenDate = task.whenDate,
            currentReminderTime = task.reminderTime,
            locale = locale,
            onApplySchedule = { newState, newWhen ->
                onScheduleChange(newState, newWhen)
            },
            onApplyScheduleWithReminder = { newState, newWhen, newReminder ->
                if (onScheduleWithReminderChange != null) {
                    onScheduleWithReminderChange(newState, newWhen, newReminder)
                } else {
                    onScheduleChange(newState, newWhen)
                }
            },
            onDismiss = onDismissWhenDialog
        )
    }

    if (showDeadlineDialog) {
        DeadlineDatePickerDialog(
            currentDeadline = task.deadline,
            locale = locale,
            onApplyDeadline = { newDl -> onDeadlineChange(newDl) },
            onDismiss = onDismissDeadlineDialog
        )
    }

    if (showRepeatDialog) {
        ThingsRepeatDialog(
            currentRule = task.recurrenceRule,
            locale = locale,
            onApplyRecurrence = { newRule ->
                onRecurrenceChange?.invoke(newRule)
            },
            onDismiss = onDismissRepeatDialog
        )
    }

    if (showMoveDialog) {
        MoveTaskToProjectOrAreaDialog(
            currentProjectId = task.projectId,
            currentHeadingId = task.headingId,
            currentAreaId = task.areaId,
            areas = allAreas,
            projects = allProjects,
            headings = allHeadings,
            onMoveTask = { pId, hId, aId ->
                onMoveTaskToContainer?.invoke(pId, hId, aId)
            },
            onDismiss = onDismissMoveDialog
        )
    }

    if (showTagPickerDialog) {
        TaskTagPickerDialog(
            allTags = allTags,
            selectedTagIds = task.tags.map { it.id },
            tagSortMode = tagSortMode,
            tagUsageCounts = tagUsageCounts,
            onUpdateSelectedTags = { updatedIds ->
                onUpdateTaskTags?.invoke(updatedIds)
            },
            onCreateAndAssignTag = { newTagTitle ->
                onCreateAndAssignTag?.invoke(newTagTitle)
            },
            onOpenFullTagManager = {
                onOpenFullTagManager?.invoke()
            },
            onDismiss = onDismissTagPickerDialog
        )
    }
}

@Composable
private fun InlineChecklistRow(
    item: ChecklistItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    shouldAutoFocus: Boolean = false,
    onAutoFocusConsumed: () -> Unit = {},
    onFocusDelta: (Int) -> Unit,
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

    DisposableEffect(item.id) {
        onDispose {
            if (isSubtaskFocused) {
                onFocusDelta(-1)
            }
        }
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
                    if (isSubtaskFocused != focusState.isFocused) {
                        isSubtaskFocused = focusState.isFocused
                        onFocusDelta(if (focusState.isFocused) 1 else -1)
                    }
                }
        )
        if (canMoveUp) {
            ThingsHoverTooltip(text = stringResource(R.string.subtask_move_up)) {
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.size(22.dp)
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
                    modifier = Modifier.size(22.dp)
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
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.subtask_delete),
                    tint = colors.secondaryText,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
