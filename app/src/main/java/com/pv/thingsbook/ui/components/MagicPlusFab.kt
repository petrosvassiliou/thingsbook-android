package com.pv.thingsbook.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pv.thingsbook.R
import com.pv.thingsbook.ui.theme.ThingsTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

private val SmoothPopoverOutEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
private val SmoothPopoverInEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

enum class MagicPlusDropMode {
    NONE,
    INSERT_TODO,
    INSERT_HEADING
}

enum class MagicPlusContextMode {
    SMART_VIEW,
    AREA_DETAIL,
    PROJECT_DETAIL,
    LOGBOOK
}

fun computeMagicPlusDropSlot(
    itemCount: Int,
    dragOffsetY: Float,
    rowHeightPx: Float
): Int {
    val safeCount = itemCount.coerceAtLeast(0)
    val rawRows = if (dragOffsetY.isFinite() && rowHeightPx > 0f) {
        ((-dragOffsetY) / rowHeightPx).roundToInt()
    } else {
        0
    }
    val rowsUp = rawRows.coerceAtLeast(0)
    return (safeCount - rowsUp).coerceIn(0, safeCount)
}

fun resolveMagicPlusDropMode(
    allowHeadingDrop: Boolean,
    dragOffsetX: Float,
    leftEdgeThresholdPx: Float,
    pointerWindowX: Float = Float.NaN,
    listWindowLeftPx: Float = Float.NaN,
    headingGutterWidthPx: Float = Float.NaN
): MagicPlusDropMode {
    if (!allowHeadingDrop) {
        return MagicPlusDropMode.INSERT_TODO
    }
    if (
        pointerWindowX.isFinite() &&
        listWindowLeftPx.isFinite() &&
        headingGutterWidthPx.isFinite() &&
        headingGutterWidthPx > 0f
    ) {
        return if (pointerWindowX <= listWindowLeftPx + headingGutterWidthPx) {
            MagicPlusDropMode.INSERT_HEADING
        } else {
            MagicPlusDropMode.INSERT_TODO
        }
    }
    return if (dragOffsetX.isFinite() && dragOffsetX <= leftEdgeThresholdPx) {
        MagicPlusDropMode.INSERT_HEADING
    } else {
        MagicPlusDropMode.INSERT_TODO
    }
}

/**
 * Optional position-based drop handling (used in Project views): receives the FAB center in window
 * coordinates so the list can resolve the exact drop position (incl. the heading section).
 */
interface MagicPlusPositionalDropHandler {
    val listLeftWindowX: Float?
        get() = null

    fun onDragStart(windowCenter: Offset, mode: MagicPlusDropMode)
    fun onDragMove(windowCenter: Offset, mode: MagicPlusDropMode)
    fun onDragEnd(mode: MagicPlusDropMode)
    fun onDragCancel()
}

data class MagicPlusDragState(
    val isDragging: Boolean = false,
    val dropIndex: Int = 0,
    val dropMode: MagicPlusDropMode = MagicPlusDropMode.NONE
)

/**
 * Animated gap and glowing blue placeholder dot rendered between two To-Do rows
 * while the user drags the Magic Plus (+) button across the list.
 */
@Composable
fun MagicPlusInsertionGap(
    visible: Boolean,
    dropMode: MagicPlusDropMode,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(durationMillis = 170, easing = SmoothPopoverOutEasing)) +
            fadeIn(animationSpec = tween(durationMillis = 140)),
        exit = shrinkVertically(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)) +
            fadeOut(animationSpec = tween(durationMillis = 110)),
        modifier = modifier
    ) {
        val isHeading = dropMode == MagicPlusDropMode.INSERT_HEADING
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.thingsBlueSoft)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isHeading) {
                Icon(
                    imageVector = Icons.Outlined.Title,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.magic_plus_heading_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(colors.thingsBlue)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.magic_plus_insertion_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue
                )
            }
        }
    }
}

/**
 * Transparent click-outside catcher behind the expanded Magic Plus popover menu.
 * Intentionally uses zero dark tint so opening or closing the '+' menu NEVER flashes the workspace grey.
 */
@Composable
fun MagicPlusBackdropScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (visible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }
}

/**
 * The iconic Things 3 "Magic Plus" Draggable Floating Action Button + Context-Aware Popover Menu:
 * - Clicking smoothly opens/closes a pure-GPU (`graphicsLayer`) popover menu without layout reflows,
 *   shadow-clipping pops, ripple flashes, or dark screen flashes.
 * - Dragging it across the list uses a stationary `pointerInput` anchor box while translating the
 *   visual circle via `graphicsLayer`, avoiding coordinate-frame oscillation mid-drag.
 */
@Composable
fun MagicPlusFab(
    itemCount: Int,
    contextMode: MagicPlusContextMode,
    canCreateArea: Boolean = false,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onCreateTodo: () -> Unit,
    onCreateProject: () -> Unit,
    onCreateHeading: () -> Unit,
    onCreateArea: () -> Unit = {},
    onDragStateChanged: (MagicPlusDragState) -> Unit,
    onDropCreateTodoAt: (Int) -> Unit,
    onDropCreateHeadingAt: (Int) -> Unit,
    modifier: Modifier = Modifier,
    positionalDropHandler: MagicPlusPositionalDropHandler? = null
) {
    @Suppress("UNUSED_PARAMETER")
    val ignoredCanCreateArea = canCreateArea
    @Suppress("UNUSED_PARAMETER")
    val ignoredOnCreateArea = onCreateArea

    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val allowHeadingDrop = contextMode == MagicPlusContextMode.PROJECT_DETAIL
    val newTodoLabel = stringResource(R.string.entity_new_todo)

    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var isFabPressed by remember { mutableStateOf(false) }
    var lastHapticSlot by remember { mutableIntStateOf(-1) }
    var lastDropMode by remember { mutableStateOf(MagicPlusDropMode.NONE) }
    val fabCoordsHolder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var dragStartCenter by remember { mutableStateOf(Offset.Zero) }

    val currentItemCount by rememberUpdatedState(itemCount)
    val currentAllowHeadingDrop by rememberUpdatedState(allowHeadingDrop)
    val currentIsMenuExpanded by rememberUpdatedState(isMenuExpanded)
    val currentOnMenuExpandedChange by rememberUpdatedState(onMenuExpandedChange)
    val currentOnDragStateChanged by rememberUpdatedState(onDragStateChanged)
    val currentOnDropCreateTodoAt by rememberUpdatedState(onDropCreateTodoAt)
    val currentOnDropCreateHeadingAt by rememberUpdatedState(onDropCreateHeadingAt)
    val currentPositionalHandler by rememberUpdatedState(positionalDropHandler)

    val fabInteractionSource = remember { MutableInteractionSource() }
    val isInteractionHovered by fabInteractionSource.collectIsHoveredAsState()
    val isFabHovered = isInteractionHovered && !isDragging && !isFabPressed

    val showMenu = isMenuExpanded && !isDragging
    val menuTransition = updateTransition(targetState = showMenu, label = "magicPlusMenuTransition")

    val menuAlpha by menuTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = 165, easing = FastOutSlowInEasing)
            } else {
                tween(durationMillis = 120, easing = SmoothPopoverInEasing)
            }
        },
        label = "magicPlusMenuAlpha"
    ) { expanded -> if (expanded) 1f else 0f }

    val menuMorph by menuTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = 210, easing = SmoothPopoverOutEasing)
            } else {
                tween(durationMillis = 140, easing = SmoothPopoverInEasing)
            }
        },
        label = "magicPlusMenuMorph"
    ) { expanded -> if (expanded) 1f else 0f }

    val fabScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.14f
            isFabPressed -> 0.94f
            isFabHovered -> 1.06f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 180, easing = SmoothPopoverOutEasing),
        label = "magicPlusScale"
    )

    val hoverLiftTargetPx = with(density) { (-2).dp.toPx() }
    val fabHoverLiftPx by animateFloatAsState(
        targetValue = if (isFabHovered && !showMenu) hoverLiftTargetPx else 0f,
        animationSpec = tween(durationMillis = 180, easing = SmoothPopoverOutEasing),
        label = "magicPlusHoverLift"
    )

    val fabHoverBlue = if (colors.isDark) Color(0xFF58ADFF) else Color(0xFF1479EC)
    val fabBgColor by animateColorAsState(
        targetValue = if (isFabHovered || isFabPressed || isDragging) fabHoverBlue else colors.thingsBlue,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "magicPlusBgColor"
    )

    val fabRingAlpha by animateFloatAsState(
        targetValue = if (isFabHovered || isDragging) 0.28f else 0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "magicPlusRingAlpha"
    )

    val iconRotation by animateFloatAsState(
        targetValue = if (showMenu) 45f else 0f,
        animationSpec = tween(durationMillis = 200, easing = SmoothPopoverOutEasing),
        label = "magicPlusIconRotation"
    )

    val rowHeightPx by rememberUpdatedState(with(density) { 58.dp.toPx() }.coerceAtLeast(1f))
    val leftEdgeThresholdPx by rememberUpdatedState(with(density) { (-180).dp.toPx() })
    val headingGutterWidthPx by rememberUpdatedState(with(density) { 96.dp.toPx() })
    val menuFloatOffsetPx = with(density) { 10.dp.toPx() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        // Pure GPU-Animated Context Menu positioned cleanly above the '+' button with unclipped shadow margin
        if (menuTransition.currentState || menuTransition.targetState) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 56.dp)
                    .graphicsLayer {
                        val a = if (menuAlpha.isFinite()) menuAlpha.coerceIn(0f, 1f) else 0f
                        val m = if (menuMorph.isFinite()) menuMorph.coerceIn(0f, 1f) else 0f
                        alpha = a
                        val s = 0.90f + 0.10f * m
                        scaleX = s
                        scaleY = s
                        translationY = (1f - m) * menuFloatOffsetPx
                        transformOrigin = TransformOrigin(0.92f, 1.0f)
                    }
                    // Breathing room around Surface so soft shadow never clips during transition
                    .padding(start = 16.dp, top = 16.dp, end = 2.dp, bottom = 14.dp)
            ) {
                MagicPlusContextMenuCard(
                    contextMode = contextMode,
                    onSelectTodo = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMenuExpandedChange(false)
                        onCreateTodo()
                    },
                    onSelectHeading = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMenuExpandedChange(false)
                        onCreateHeading()
                    },
                    onSelectProject = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMenuExpandedChange(false)
                        onCreateProject()
                    }
                )
            }
        }

        // Stationary 58dp gesture anchor box; only the inner visual token translates during drag
        Box(
            modifier = Modifier
                .size(58.dp)
                .onGloballyPositioned { fabCoordsHolder[0] = it }
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interactionSource = fabInteractionSource)
                .semantics {
                    role = Role.Button
                    contentDescription = newTodoLabel
                    onClick {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnMenuExpandedChange(!currentIsMenuExpanded)
                        true
                    }
                }
                .pointerInput(Unit) {
                    fun updateFromTotalOffset(totalOffset: Offset) {
                        val safeX = if (totalOffset.x.isFinite()) totalOffset.x else 0f
                        val safeY = if (totalOffset.y.isFinite()) totalOffset.y else 0f
                        dragOffset = Offset(safeX, safeY)

                        val computedSlot = computeMagicPlusDropSlot(
                            itemCount = currentItemCount,
                            dragOffsetY = dragOffset.y,
                            rowHeightPx = rowHeightPx
                        )
                        val positional = currentPositionalHandler
                        val windowCenter = dragStartCenter + dragOffset
                        val listLeftX = positional?.listLeftWindowX ?: Float.NaN

                        val mode = resolveMagicPlusDropMode(
                            allowHeadingDrop = currentAllowHeadingDrop,
                            dragOffsetX = dragOffset.x,
                            leftEdgeThresholdPx = leftEdgeThresholdPx,
                            pointerWindowX = windowCenter.x,
                            listWindowLeftPx = listLeftX,
                            headingGutterWidthPx = headingGutterWidthPx
                        )

                        if (positional != null) {
                            positional.onDragMove(windowCenter, mode)
                            lastHapticSlot = computedSlot
                            if (mode != lastDropMode) {
                                lastDropMode = mode
                                currentOnDragStateChanged(
                                    MagicPlusDragState(
                                        isDragging = true,
                                        dropIndex = computedSlot,
                                        dropMode = mode
                                    )
                                )
                            }
                        } else if (computedSlot != lastHapticSlot || mode != lastDropMode) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticSlot = computedSlot
                            lastDropMode = mode
                            currentOnDragStateChanged(
                                MagicPlusDragState(
                                    isDragging = true,
                                    dropIndex = computedSlot,
                                    dropMode = mode
                                )
                            )
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downOrigin = down.position
                        down.consume()
                        isFabPressed = true
                        var dragSessionStarted = false

                        try {
                            var initialOverSlop = Offset.Zero
                            val dragStart = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                initialOverSlop = over
                            }

                            if (dragStart == null) {
                                isFabPressed = false
                                val upChange = currentEvent.changes.firstOrNull { it.id == down.id }
                                if (upChange != null && !upChange.pressed && upChange.previousPressed && !upChange.isConsumed) {
                                    upChange.consume()
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentOnMenuExpandedChange(!currentIsMenuExpanded)
                                }
                            } else {
                                isFabPressed = false
                                dragSessionStarted = true
                                currentOnMenuExpandedChange(false)
                                isDragging = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                                val initialIndex = currentItemCount.coerceAtLeast(0)
                                lastHapticSlot = initialIndex
                                lastDropMode = MagicPlusDropMode.INSERT_TODO
                                currentOnDragStateChanged(
                                    MagicPlusDragState(
                                        isDragging = true,
                                        dropIndex = initialIndex,
                                        dropMode = MagicPlusDropMode.INSERT_TODO
                                    )
                                )

                                val coords = fabCoordsHolder[0]
                                dragStartCenter = if (coords != null && coords.isAttached) {
                                    coords.localToWindow(
                                        Offset(coords.size.width / 2f, coords.size.height / 2f)
                                    )
                                } else {
                                    Offset.Zero
                                }
                                currentPositionalHandler?.onDragStart(
                                    dragStartCenter,
                                    MagicPlusDropMode.INSERT_TODO
                                )

                                val startOffset = (dragStart.position - downOrigin).let { raw ->
                                    if (raw.getDistanceSquared() > 0.5f) raw else initialOverSlop
                                }
                                updateFromTotalOffset(startOffset)

                                val completedCleanly = drag(dragStart.id) { change ->
                                    change.consume()
                                    updateFromTotalOffset(change.position - downOrigin)
                                }

                                dragSessionStarted = false
                                if (completedCleanly) {
                                    val finalSlot = lastHapticSlot.coerceIn(0, currentItemCount.coerceAtLeast(0))
                                    val finalMode = lastDropMode
                                    isDragging = false
                                    dragOffset = Offset.Zero
                                    currentOnDragStateChanged(MagicPlusDragState(isDragging = false))
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                                    val positional = currentPositionalHandler
                                    if (positional != null) {
                                        positional.onDragEnd(finalMode)
                                    } else if (finalMode == MagicPlusDropMode.INSERT_HEADING && currentAllowHeadingDrop) {
                                        currentOnDropCreateHeadingAt(finalSlot)
                                    } else {
                                        currentOnDropCreateTodoAt(finalSlot)
                                    }
                                } else {
                                    currentPositionalHandler?.onDragCancel()
                                    isDragging = false
                                    dragOffset = Offset.Zero
                                    currentOnDragStateChanged(MagicPlusDragState(isDragging = false))
                                }
                            }
                        } catch (c: CancellationException) {
                            isFabPressed = false
                            if (dragSessionStarted || isDragging) {
                                currentPositionalHandler?.onDragCancel()
                                isDragging = false
                                dragOffset = Offset.Zero
                                currentOnDragStateChanged(MagicPlusDragState(isDragging = false))
                            }
                            throw c
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isDragging) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(colors.thingsBlue.copy(alpha = 0.08f))
                        .border(
                            width = 1.5.dp,
                            color = colors.thingsBlue.copy(alpha = 0.30f),
                            shape = CircleShape
                        )
                )
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val safeScale = if (fabScale.isFinite()) fabScale else 1f
                        val safeLift = if (fabHoverLiftPx.isFinite()) fabHoverLiftPx else 0f
                        val dx = if (dragOffset.x.isFinite()) dragOffset.x else 0f
                        val dy = if (dragOffset.y.isFinite()) dragOffset.y else 0f
                        translationX = dx
                        translationY = dy + safeLift
                        scaleX = safeScale
                        scaleY = safeScale
                    }
                    .shadow(
                        elevation = when {
                            isDragging -> 14.dp
                            isFabHovered -> 12.dp
                            else -> 8.dp
                        },
                        shape = CircleShape,
                        ambientColor = colors.thingsBlue.copy(alpha = if (isFabHovered || isDragging) 0.46f else 0.38f),
                        spotColor = colors.thingsBlue.copy(alpha = if (isFabHovered || isDragging) 0.58f else 0.48f)
                    )
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(fabBgColor)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = if (fabRingAlpha.isFinite()) fabRingAlpha.coerceIn(0f, 1f) else 0f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            rotationZ = if (iconRotation.isFinite()) iconRotation else 0f
                        }
                )
            }
        }
    }
}

@Composable
private fun MagicPlusContextMenuCard(
    contextMode: MagicPlusContextMode,
    onSelectTodo: () -> Unit,
    onSelectHeading: () -> Unit,
    onSelectProject: () -> Unit
) {
    val colors = ThingsTheme.colors

    val todoSubtitle = when (contextMode) {
        MagicPlusContextMode.PROJECT_DETAIL -> stringResource(R.string.magic_plus_todo_sub_project)
        MagicPlusContextMode.AREA_DETAIL -> stringResource(R.string.magic_plus_todo_sub_area)
        MagicPlusContextMode.LOGBOOK -> stringResource(R.string.magic_plus_todo_sub_inbox)
        MagicPlusContextMode.SMART_VIEW -> stringResource(R.string.magic_plus_todo_sub_view)
    }

    val projectSubtitle = when (contextMode) {
        MagicPlusContextMode.AREA_DETAIL -> stringResource(R.string.magic_plus_project_sub_area)
        MagicPlusContextMode.PROJECT_DETAIL -> stringResource(R.string.magic_plus_project_sub_another)
        else -> stringResource(R.string.magic_plus_project_sub_default)
    }

    // Pure crisp white/dark elevated card (`tonalElevation = 0.dp`) so icon badges never wash out
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.cardElevatedSurface,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
            .widthIn(min = 240.dp, max = 282.dp)
            .border(
                width = 0.75.dp,
                color = colors.hairlineDivider,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 1. To-Do (Always available)
            MagicPlusOptionRow(
                icon = Icons.Outlined.CheckCircleOutline,
                iconTint = colors.thingsBlue,
                iconBg = colors.thingsBlueSoft,
                title = stringResource(R.string.entity_todo),
                subtitle = todoSubtitle,
                shortcutBadge = "Ctrl+N",
                onClick = onSelectTodo
            )

            // 2. Heading (Context-specific: inside a Project)
            if (contextMode == MagicPlusContextMode.PROJECT_DETAIL) {
                MagicPlusOptionRow(
                    icon = Icons.Outlined.Title,
                    iconTint = Color(0xFF5856D6),
                    iconBg = Color(0xFF5856D6).copy(alpha = 0.12f),
                    title = stringResource(R.string.entity_heading),
                    subtitle = stringResource(R.string.magic_plus_heading_sub),
                    onClick = onSelectHeading
                )
            }

            // 3. Project (Available in all views; pre-assigns Area when in Area/Project context)
            MagicPlusOptionRow(
                icon = Icons.Outlined.Folder,
                iconTint = colors.thingsBlue,
                iconBg = colors.thingsBlueSoft,
                title = stringResource(R.string.entity_project),
                subtitle = projectSubtitle,
                shortcutBadge = "Ctrl+Shift+N",
                onClick = onSelectProject
            )
        }
    }
}

@Composable
private fun MagicPlusOptionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    shortcutBadge: String? = null,
    onClick: () -> Unit
) {
    val colors = ThingsTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val rowBg by animateColorAsState(
        targetValue = when {
            isPressed -> colors.thingsBlue.copy(alpha = 0.13f)
            isHovered -> colors.thingsBlue.copy(alpha = 0.07f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "magicPlusOptionHoverBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primaryText
                )
                if (!shortcutBadge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(colors.tagPillBg)
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = shortcutBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = colors.secondaryText
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.secondaryText,
                maxLines = 1
            )
        }
    }
}
