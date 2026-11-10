package com.culturedcode.things3.ui.components

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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import android.view.MotionEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.Area
import com.culturedcode.things3.domain.model.ChecklistItem
import com.culturedcode.things3.domain.model.Heading
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.domain.model.RecurrenceRule
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.Tag
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.domain.model.TaskStatus
import com.culturedcode.things3.ui.theme.ThingsTheme
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

    return com.culturedcode.things3.domain.model.RightClickGestureUtils.isRightClickEvent(
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandableTaskCard(
    task: TaskItem,
    allTags: List<Tag> = emptyList(),
    allAreas: List<Area> = emptyList(),
    allProjects: List<Project> = emptyList(),
    allHeadings: List<Heading> = emptyList(),
    isExpanded: Boolean,
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
    onScheduleWithReminderChange: ((ScheduleState, Long?, Long?) -> Unit)? = null,
    onDeadlineChange: (Long?) -> Unit,
    onRecurrenceChange: ((RecurrenceRule?) -> Unit)? = null,
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
    onTriggerGeminiMagicBreakdown: () -> Unit,
    onDeleteTask: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
    collapsedTrailingContent: (@Composable () -> Unit)? = null
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentOnStatusChange by rememberUpdatedState(onStatusChange)

    // Focus tracking across all text inputs inside this card so right-click NEVER hijacks native text editing
    var isTitleFocused by remember(task.id) { mutableStateOf(false) }
    var isNotesFocused by remember(task.id) { mutableStateOf(false) }
    var isNewChecklistFocused by remember(task.id) { mutableStateOf(false) }
    var focusedSubtaskCount by remember(task.id) { mutableIntStateOf(0) }

    val suppressCustomContextMenu = isExpanded ||
        suppressContextMenuExternal ||
        isTitleFocused ||
        isNotesFocused ||
        isNewChecklistFocused ||
        focusedSubtaskCount > 0

    var editedTitleField by remember(task.id) {
        mutableStateOf(
            TextFieldValue(
                text = task.title,
                selection = if (task.title.isEmpty()) TextRange(0) else TextRange(0, task.title.length)
            )
        )
    }
    var editedNotesField by remember(task.id) {
        mutableStateOf(
            TextFieldValue(
                text = task.notes,
                selection = if (task.notes.isEmpty()) TextRange(0) else TextRange(0, task.notes.length)
            )
        )
    }

    LaunchedEffect(task.id, task.title) {
        if (!isTitleFocused && editedTitleField.text != task.title) {
            editedTitleField = TextFieldValue(
                text = task.title,
                selection = if (task.title.isEmpty()) TextRange(0) else TextRange(0, task.title.length)
            )
        }
    }

    LaunchedEffect(task.id, task.notes) {
        if (!isNotesFocused && editedNotesField.text != task.notes) {
            editedNotesField = TextFieldValue(
                text = task.notes,
                selection = if (task.notes.isEmpty()) TextRange(0) else TextRange(0, task.notes.length)
            )
        }
    }

    var showWhenDialog by remember(task.id) { mutableStateOf(false) }
    var showDeadlineDialog by remember(task.id) { mutableStateOf(false) }
    var showRepeatDialog by remember(task.id) { mutableStateOf(false) }
    var showMoveDialog by remember(task.id) { mutableStateOf(false) }
    var showTagPickerDialog by remember(task.id) { mutableStateOf(false) }
    var showContextMenu by remember(task.id) { mutableStateOf(false) }
    var contextMenuOffsetDp by remember(task.id) { mutableStateOf(DpOffset(24.dp, 0.dp)) }

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
                onExpandToggle()
            }
            isCompletingAnim = true
        } else if (isCompletingAnim && requestedStatus == TaskStatus.OPEN) {
            // Allow instant undo if user clicks checkbox again before glide-out finishes
            isCompletingAnim = false
            isCompletedPendingCommit = false
        } else {
            onStatusChange(requestedStatus)
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
                selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
            )
            delay(60)
            runCatching { titleFocusRequester.requestFocus() }
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

    val horizontalPadding by animateDpAsState(
        targetValue = if (isExpanded) 12.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "cardHorizontalMargin"
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (isExpanded) 8.dp else 1.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "cardVerticalMargin"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 14.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "cardCornerRadius"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isExpanded) 10.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "cardShadowElevation"
    )

    val strikeFrac = if (isCompletedPendingCommit) 1f else completionStrikeProgress.value.coerceIn(0f, 1f)
    val exitFrac = if (isCompletedPendingCommit && task.status == TaskStatus.OPEN) {
        1f
    } else {
        completionExitProgress.value.coerceIn(0f, 1f)
    }

    val isContextMenuTargeted = (!isExpanded && showContextMenu && !suppressCustomContextMenu) ||
        (!isExpanded && (showWhenDialog || showDeadlineDialog || showRepeatDialog || showMoveDialog || showTagPickerDialog))

    val surfaceColor by animateColorAsState(
        targetValue = when {
            isExpanded -> colors.cardElevatedSurface
            isContextMenuTargeted -> colors.thingsBlue.copy(alpha = 0.18f)
            isSelected -> colors.thingsBlue.copy(alpha = 0.14f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "cardSurfaceColor"
    )

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
        if (!isExpanded && !isCompletingAnim && !isCompletedPendingCommit && kotlin.math.abs(animatedSwipeOffsetPx) > 6f) {
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
                    ambientColor = Color.Black.copy(alpha = 0.14f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                )
                .clip(RoundedCornerShape(cornerRadius))
                .then(
                    if ((isSelected || isContextMenuTargeted) && !isExpanded) {
                        Modifier.border(
                            width = if (isContextMenuTargeted) 1.25.dp else 1.dp,
                            color = colors.thingsBlue.copy(alpha = if (isContextMenuTargeted) 0.72f else 0.42f),
                            shape = RoundedCornerShape(cornerRadius)
                        )
                    } else {
                        Modifier
                    }
                )
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                )
                .pointerInput(task.id, isExpanded, isCompletingAnim) {
                    if (!isExpanded && !isCompletingAnim) {
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
                        if (isCompletingAnim) return@combinedClickable
                        if (isSelectionModeActive && !isExpanded && onToggleSelection != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleSelection()
                        } else if (!isExpanded) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExpandToggle()
                        }
                    },
                    onLongClick = if (suppressCustomContextMenu) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isExpanded) 16.dp else 8.dp,
                        vertical = if (isExpanded) 14.dp else 10.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = if (isExpanded) Alignment.CenterVertically else Alignment.Top
                ) {
                    ThingsCheckbox(
                        status = effectiveVisualStatus,
                        onStatusChange = handleStatusRequest,
                        isDashedSomeday = task.scheduleState == ScheduleState.SOMEDAY
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    if (isExpanded) {
                        BasicTextField(
                            value = editedTitleField,
                            onValueChange = { newVal ->
                                val textChanged = newVal.text != editedTitleField.text
                                editedTitleField = newVal
                                if (textChanged) {
                                    onTitleNotesChange(newVal.text, editedNotesField.text)
                                }
                            },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = colors.primaryText
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.thingsBlue),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(titleFocusRequester)
                                .onFocusChanged { focusState ->
                                    isTitleFocused = focusState.isFocused
                                    if (focusState.isFocused) {
                                        val txt = editedTitleField.text
                                        editedTitleField = editedTitleField.copy(
                                            selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                        )
                                    }
                                }
                        )
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
                    }

                    if (!isExpanded && collapsedTrailingContent != null && !isCompletingAnim) {
                        Spacer(modifier = Modifier.width(4.dp))
                        collapsedTrailingContent()
                    }
                }

                if (!isExpanded && task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 40.dp, top = 2.dp)
                    )
                }

                if (!isExpanded && hasCollapsedTaskBadges(task)) {
                    CollapsedTaskInlineBadges(
                        task = task,
                        locale = locale,
                        modifier = Modifier.padding(start = 40.dp, top = 6.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                    ) + fadeIn(),
                    exit = shrinkVertically(
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                    ) + fadeOut()
                ) {
                    ExpandedTaskCardBody(
                        task = task,
                        assignedProject = assignedProject,
                        assignedArea = assignedArea,
                        editedTitle = editedTitleField.text,
                        editedNotesField = editedNotesField,
                        onNotesFieldChange = { newVal ->
                            val textChanged = newVal.text != editedNotesField.text
                            editedNotesField = newVal
                            if (textChanged) {
                                onTitleNotesChange(editedTitleField.text, newVal.text)
                            }
                        },
                        onNotesFocusChanged = { focused -> isNotesFocused = focused },
                        onNewChecklistFocusChanged = { focused -> isNewChecklistFocused = focused },
                        onSubtaskFocusDelta = { delta ->
                            focusedSubtaskCount = (focusedSubtaskCount + delta).coerceAtLeast(0)
                        },
                        isGeneratingMagicBreakdown = isGeneratingMagicBreakdown,
                        locale = locale,
                        onAddChecklistItem = onAddChecklistItem,
                        onInsertChecklistItemAfter = onInsertChecklistItemAfter,
                        onToggleChecklistItem = onToggleChecklistItem,
                        onRenameChecklistItem = onRenameChecklistItem,
                        onMoveChecklistItem = onMoveChecklistItem,
                        onDeleteChecklistItem = onDeleteChecklistItem,
                        onTriggerGeminiMagicBreakdown = onTriggerGeminiMagicBreakdown,
                        onOpenWhenDialog = { showWhenDialog = true },
                        onOpenDeadlineDialog = { showDeadlineDialog = true },
                        onOpenRepeatDialog = { showRepeatDialog = true },
                        onOpenMoveDialog = { showMoveDialog = true },
                        onOpenTagPickerDialog = { showTagPickerDialog = true },
                        onDeleteTask = onDeleteTask,
                        onDoneCollapse = onExpandToggle
                    )
                }
            }
        }

        if (showContextMenu && !suppressCustomContextMenu) {
            TaskContextDropdownMenu(
                task = task,
                isSelected = isSelected,
                offset = contextMenuOffsetDp,
                onToggleSelection = onToggleSelection,
                onStatusChange = handleStatusRequest,
                onOpenWhenDialog = { showWhenDialog = true },
                onOpenDeadlineDialog = { showDeadlineDialog = true },
                onOpenRepeatDialog = { showRepeatDialog = true },
                onOpenMoveDialog = { showMoveDialog = true },
                onOpenTagPickerDialog = { showTagPickerDialog = true },
                onTriggerGeminiMagicBreakdown = onTriggerGeminiMagicBreakdown,
                onDeleteTask = onDeleteTask,
                onDismiss = { showContextMenu = false }
            )
        }
    }

    TaskCardDialogsHost(
        task = task,
        allTags = allTags,
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

private fun hasCollapsedTaskBadges(task: TaskItem): Boolean {
    return task.scheduleState == ScheduleState.THIS_EVENING ||
        task.recurrenceRule != null ||
        task.reminderTime != null ||
        task.checklistItems.isNotEmpty() ||
        task.tags.isNotEmpty() ||
        task.deadline != null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollapsedTaskInlineBadges(
    task: TaskItem,
    locale: Locale,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (task.scheduleState == ScheduleState.THIS_EVENING) {
            ThingsWhenBadge(
                scheduleState = task.scheduleState,
                whenDateEpochMillis = task.whenDate,
                locale = locale
            )
        }
        task.recurrenceRule?.let { rr ->
            ThingsRepeatBadge(rule = rr)
        }
        task.reminderTime?.let { rt ->
            ThingsReminderBadge(reminderEpochMillis = rt)
        }
        if (task.checklistItems.isNotEmpty()) {
            val doneCount = task.checklistItems.count { it.isCompleted }
            ThingsTagPill(title = "☑ $doneCount/${task.checklistItems.size}")
        }
        task.tags.forEach { tag ->
            ThingsTagPill(title = tag.title)
        }
        task.deadline?.let { dl ->
            ThingsDeadlineBadge(deadlineEpochMillis = dl, locale = locale)
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
                    contentDescription = "Wann?",
                    tint = Color(0xFF1A1C1E),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Wann?",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF1A1C1E)
                )
            } else if (isDeepSwipeLeftForDelete) {
                Text(
                    text = "Löschen",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Löschen",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            } else {
                Text(
                    text = if (isSelected) "Abwählen" else "Auswählen",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "Auswählen",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedTaskCardBody(
    task: TaskItem,
    assignedProject: Project?,
    assignedArea: Area?,
    editedTitle: String,
    editedNotesField: TextFieldValue,
    onNotesFieldChange: (TextFieldValue) -> Unit,
    onNotesFocusChanged: (Boolean) -> Unit,
    onNewChecklistFocusChanged: (Boolean) -> Unit,
    onSubtaskFocusDelta: (Int) -> Unit,
    isGeneratingMagicBreakdown: Boolean,
    locale: Locale,
    onAddChecklistItem: (String) -> Unit,
    onInsertChecklistItemAfter: ((afterItemId: String?, newItemId: String, title: String) -> Unit)?,
    onToggleChecklistItem: (String, Boolean) -> Unit,
    onRenameChecklistItem: ((String, String) -> Unit)?,
    onMoveChecklistItem: ((String, Boolean) -> Unit)?,
    onDeleteChecklistItem: ((String) -> Unit)?,
    onTriggerGeminiMagicBreakdown: () -> Unit,
    onOpenWhenDialog: () -> Unit,
    onOpenDeadlineDialog: () -> Unit,
    onOpenRepeatDialog: () -> Unit,
    onOpenMoveDialog: () -> Unit,
    onOpenTagPickerDialog: () -> Unit,
    onDeleteTask: () -> Unit,
    onDoneCollapse: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val newChecklistFocusRequester = remember(task.id) { FocusRequester() }
    var pendingAutoFocusSubtaskId by remember(task.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 8.dp)
    ) {
        // Markdown Notes Editor
        BasicTextField(
            value = editedNotesField,
            onValueChange = onNotesFieldChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = colors.secondaryText
            ),
            cursorBrush = SolidColor(colors.thingsBlue),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .onFocusChanged { focusState ->
                    onNotesFocusChanged(focusState.isFocused)
                    if (focusState.isFocused) {
                        val txt = editedNotesField.text
                        onNotesFieldChange(
                            editedNotesField.copy(
                                selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                            )
                        )
                    }
                },
            decorationBox = { innerTextField ->
                Box {
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

        Spacer(modifier = Modifier.height(8.dp))

        // Subtasks (Unteraufgaben / Checklist) Section
        if (task.checklistItems.isNotEmpty()) {
            val doneCount = task.checklistItems.count { it.isCompleted }
            Text(
                text = "${stringResource(R.string.placeholder_checklist)} ($doneCount/${task.checklistItems.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = colors.thingsBlue
            )
            Spacer(modifier = Modifier.height(4.dp))
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
                            onMoveDown = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMoveChecklistItem?.invoke(item.id, false)
                            },
                            onDelete = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteChecklistItem?.invoke(item.id)
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Add new subtask inline field + explicit '+' Button
        key("new_checklist_input_row_${task.id}") {
            var newChecklistField by remember(task.id) {
                mutableStateOf(TextFieldValue(text = "", selection = TextRange(0)))
            }
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.tagPillBg.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Checklist,
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

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Metadata Badges Row: When, Repeat, Deadline, Project/Area, Tags
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ThingsWhenBadge(
                scheduleState = task.scheduleState,
                whenDateEpochMillis = task.whenDate,
                locale = locale,
                onClick = onOpenWhenDialog
            )

            task.reminderTime?.let { rt ->
                ThingsReminderBadge(
                    reminderEpochMillis = rt,
                    onClick = onOpenWhenDialog
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

            val containerLabel = when {
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

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
        Spacer(modifier = Modifier.height(6.dp))

        // Bottom Card Action Bar with Hover Tooltips
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ThingsHoverTooltip(text = "Aufgabe automatisch mit Gemini KI in konkrete Schritte zerlegen") {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.thingsBlueSoft)
                        .clickable(enabled = !isGeneratingMagicBreakdown) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTriggerGeminiMagicBreakdown()
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isGeneratingMagicBreakdown) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colors.thingsBlue
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = stringResource(R.string.gemini_magic_breakdown),
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.gemini_magic_breakdown),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.thingsBlue
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ThingsHoverTooltip(text = "Wann? (Datum & Erinnerung)") {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenWhenDialog()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = stringResource(R.string.action_when),
                            tint = if (task.whenDate != null || task.reminderTime != null) colors.thingsBlue else colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                ThingsHoverTooltip(text = "Wiederholung konfigurieren") {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenRepeatDialog()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Repeat,
                            contentDescription = "Wiederholen",
                            tint = if (task.recurrenceRule != null) colors.thingsBlue else colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                ThingsHoverTooltip(text = "Projekt / Bereich zuweisen") {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenMoveDialog()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = "Projekt / Bereich zuweisen",
                            tint = if (task.projectId != null || task.areaId != null) colors.thingsBlue else colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                ThingsHoverTooltip(text = stringResource(R.string.action_tags)) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenTagPickerDialog()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalOffer,
                            contentDescription = stringResource(R.string.action_tags),
                            tint = if (task.tags.isNotEmpty()) colors.thingsBlue else colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                ThingsHoverTooltip(text = stringResource(R.string.action_deadline)) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenDeadlineDialog()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = stringResource(R.string.action_deadline),
                            tint = if (task.deadline != null) colors.deadlineUrgentRed else colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                ThingsHoverTooltip(text = stringResource(R.string.action_delete)) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeleteTask()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                ThingsHoverTooltip(text = stringResource(R.string.action_done)) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDoneCollapse()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.action_done),
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(17.dp)
                        )
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
    onToggleSelection: (() -> Unit)?,
    onStatusChange: (TaskStatus) -> Unit,
    onOpenWhenDialog: () -> Unit,
    onOpenDeadlineDialog: () -> Unit,
    onOpenRepeatDialog: () -> Unit,
    onOpenMoveDialog: () -> Unit,
    onOpenTagPickerDialog: () -> Unit,
    onTriggerGeminiMagicBreakdown: () -> Unit,
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
                    .clip( RoundedCornerShape(50))
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

        if (onToggleSelection != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isSelected) "Auswahl aufheben" else "Aufgabe auswählen (Mehrfachauswahl)",
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
        val isCompleted = task.status == TaskStatus.COMPLETED
        DropdownMenuItem(
            text = {
                Text(
                    text = if (isCompleted) "Als offen markieren" else "Als erledigt markieren",
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
                    text = "Wann? (Datum & Erinnerung)",
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
        DropdownMenuItem(
            text = {
                Text(
                    text = "Fälligkeit / Deadline anpassen",
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
                    text = "Wiederholung konfigurieren",
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
                    text = "Verschieben (Bereich / Projekt)",
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
                    text = "Tags zuweisen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.LocalOffer,
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
        DropdownMenuItem(
            text = {
                Text(
                    text = "In Teilaufgaben zerlegen (Gemini ✨)",
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
                    text = "Als abgebrochen markieren",
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
                    text = "Aufgabe löschen",
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
