package com.culturedcode.things3.ui.components

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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.culturedcode.things3.R
import com.culturedcode.things3.ui.theme.ThingsTheme
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

/**
 * Optional position-based drop handling (used in Project views): receives the FAB center in window
 * coordinates so the list can resolve the exact drop position (incl. the heading section).
 */
interface MagicPlusPositionalDropHandler {
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
 * - Dragging it across the list preserves 100% of the Things Magic Plus Drag & Drop behavior
 *   (`INSERT_TODO` row gap & `INSERT_HEADING` when dragged to the left inside a Project).
 */
@Composable
fun MagicPlusFab(
    itemCount: Int,
    contextMode: MagicPlusContextMode,
    canCreateArea: Boolean,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onCreateTodo: () -> Unit,
    onCreateProject: () -> Unit,
    onCreateHeading: () -> Unit,
    onCreateArea: () -> Unit,
    onDragStateChanged: (MagicPlusDragState) -> Unit,
    onDropCreateTodoAt: (Int) -> Unit,
    onDropCreateHeadingAt: (Int) -> Unit,
    modifier: Modifier = Modifier,
    positionalDropHandler: MagicPlusPositionalDropHandler? = null
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val allowHeadingDrop = contextMode == MagicPlusContextMode.PROJECT_DETAIL

    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var lastHapticSlot by remember { mutableIntStateOf(-1) }
    var lastDropMode by remember { mutableStateOf(MagicPlusDropMode.NONE) }
    val fabCoordsHolder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var dragStartCenter by remember { mutableStateOf(Offset.Zero) }
    val currentPositionalHandler by rememberUpdatedState(positionalDropHandler)

    val fabInteractionSource = remember { MutableInteractionSource() }
    val isFabPressed by fabInteractionSource.collectIsPressedAsState()

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
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 150, easing = SmoothPopoverOutEasing),
        label = "magicPlusScale"
    )

    val iconRotation by animateFloatAsState(
        targetValue = if (showMenu) 45f else 0f,
        animationSpec = tween(durationMillis = 200, easing = SmoothPopoverOutEasing),
        label = "magicPlusIconRotation"
    )

    val rowHeightPx = with(density) { 58.dp.toPx() }.coerceAtLeast(1f)
    val leftEdgeThresholdPx = with(density) { (-210).dp.toPx() }
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
                    canCreateArea = canCreateArea,
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
                    },
                    onSelectArea = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMenuExpandedChange(false)
                        onCreateArea()
                    }
                )
            }
        }

        // Draggable + Clickable Magic Plus Circle Button with zero ripple flash
        ThingsHoverTooltip(
            text = stringResource(R.string.magic_plus_fab_tooltip),
            enabled = !isDragging && !isMenuExpanded
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = dragOffset.x.takeIf { it.isFinite() }?.roundToInt() ?: 0,
                            y = dragOffset.y.takeIf { it.isFinite() }?.roundToInt() ?: 0
                        )
                    }
                    .onGloballyPositioned { fabCoordsHolder[0] = it }
                    .graphicsLayer {
                        val safeScale = if (fabScale.isFinite()) fabScale else 1f
                        scaleX = safeScale
                        scaleY = safeScale
                    }
                    .shadow(
                        elevation = if (isDragging) 14.dp else 8.dp,
                        shape = CircleShape,
                        ambientColor = colors.thingsBlue.copy(alpha = 0.38f),
                        spotColor = colors.thingsBlue.copy(alpha = 0.48f)
                    )
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(colors.thingsBlue)
                    .clickable(
                        interactionSource = fabInteractionSource,
                        indication = null,
                        enabled = !isDragging
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMenuExpandedChange(!isMenuExpanded)
                    }
                    .pointerInput(itemCount, allowHeadingDrop) {
                        detectDragGestures(
                            onDragStart = {
                                onMenuExpandedChange(false)
                                isDragging = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val initialIndex = itemCount.coerceAtLeast(0)
                                lastHapticSlot = initialIndex
                                lastDropMode = MagicPlusDropMode.INSERT_TODO
                                onDragStateChanged(
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
                                    ) - dragOffset
                                } else {
                                    Offset.Zero
                                }
                                currentPositionalHandler?.onDragStart(
                                    dragStartCenter + dragOffset,
                                    MagicPlusDropMode.INSERT_TODO
                                )
                            },
                            onDragEnd = {
                                val finalSlot = lastHapticSlot.coerceIn(0, itemCount.coerceAtLeast(0))
                                val finalMode = lastDropMode
                                isDragging = false
                                dragOffset = Offset.Zero
                                onDragStateChanged(MagicPlusDragState(isDragging = false))
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                                val positional = currentPositionalHandler
                                if (positional != null) {
                                    positional.onDragEnd(finalMode)
                                } else if (finalMode == MagicPlusDropMode.INSERT_HEADING && allowHeadingDrop) {
                                    onDropCreateHeadingAt(finalSlot)
                                } else {
                                    onDropCreateTodoAt(finalSlot)
                                }
                            },
                            onDragCancel = {
                                currentPositionalHandler?.onDragCancel()
                                isDragging = false
                                dragOffset = Offset.Zero
                                onDragStateChanged(MagicPlusDragState(isDragging = false))
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount.x.isFinite() && dragAmount.y.isFinite()) {
                                    dragOffset += dragAmount
                                }

                                val rawRows = if (dragOffset.y.isFinite() && rowHeightPx > 0f) {
                                    ((-dragOffset.y) / rowHeightPx).roundToInt()
                                } else 0
                                val rowsUp = rawRows.coerceAtLeast(0)
                                val computedSlot = (itemCount - rowsUp).coerceIn(0, itemCount.coerceAtLeast(0))

                                val mode = if (allowHeadingDrop && dragOffset.x <= leftEdgeThresholdPx) {
                                    MagicPlusDropMode.INSERT_HEADING
                                } else {
                                    MagicPlusDropMode.INSERT_TODO
                                }

                                val positional = currentPositionalHandler
                                if (positional != null) {
                                    positional.onDragMove(dragStartCenter + dragOffset, mode)
                                    if (mode != lastDropMode) {
                                        lastDropMode = mode
                                        onDragStateChanged(
                                            MagicPlusDragState(isDragging = true, dropIndex = computedSlot, dropMode = mode)
                                        )
                                    }
                                } else if (computedSlot != lastHapticSlot || mode != lastDropMode) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastHapticSlot = computedSlot
                                    lastDropMode = mode
                                    onDragStateChanged(
                                        MagicPlusDragState(
                                            isDragging = true,
                                            dropIndex = computedSlot,
                                            dropMode = mode
                                        )
                                    )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.entity_new_todo),
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
    canCreateArea: Boolean,
    onSelectTodo: () -> Unit,
    onSelectHeading: () -> Unit,
    onSelectProject: () -> Unit,
    onSelectArea: () -> Unit
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
            // 1. Aufgabe (Always available)
            MagicPlusOptionRow(
                icon = Icons.Outlined.CheckCircleOutline,
                iconTint = colors.thingsBlue,
                iconBg = colors.thingsBlueSoft,
                title = stringResource(R.string.entity_todo),
                subtitle = todoSubtitle,
                shortcutBadge = "Ctrl+N",
                onClick = onSelectTodo
            )

            // 2. Überschrift (Context-specific: inside a Project!)
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

            // 3. Projekt (Available in all views; pre-assigns Area when in Area/Project context)
            MagicPlusOptionRow(
                icon = Icons.Outlined.Folder,
                iconTint = colors.thingsBlue,
                iconBg = colors.thingsBlueSoft,
                title = stringResource(R.string.entity_project),
                subtitle = projectSubtitle,
                shortcutBadge = "Ctrl+Shift+N",
                onClick = onSelectProject
            )

            // 4. Bereich (Context-specific: available outside Project detail when < 8 Areas)
            if (contextMode != MagicPlusContextMode.PROJECT_DETAIL && canCreateArea) {
                MagicPlusOptionRow(
                    icon = Icons.Outlined.Inventory2,
                    iconTint = colors.anytimeTeal,
                    iconBg = colors.anytimeTeal.copy(alpha = 0.14f),
                    title = stringResource(R.string.entity_area),
                    subtitle = stringResource(R.string.magic_plus_area_sub),
                    onClick = onSelectArea
                )
            }
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
