package com.culturedcode.things3.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.culturedcode.things3.R
import com.culturedcode.things3.ui.theme.ThingsTheme
import kotlin.math.roundToInt

enum class MagicPlusDropMode {
    NONE,
    INSERT_TODO,
    INSERT_HEADING
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
        enter = expandVertically(animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f)) + fadeIn(),
        exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)) + fadeOut(),
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
 * The iconic Things 3 "Magic Plus" Draggable Floating Action Button:
 * - Tapping creates a new To-Do at the top of the current view and expands it inline.
 * - Dragging it across the list computes the target row gap (`dropIndex`), triggers haptic ticks
 *   when crossing row boundaries, and switches to `INSERT_HEADING` when dragged to the left edge inside a Project.
 */
@Composable
fun MagicPlusFab(
    itemCount: Int,
    allowHeadingDrop: Boolean,
    onQuickTapCreateTodo: () -> Unit,
    onDragStateChanged: (MagicPlusDragState) -> Unit,
    onDropCreateTodoAt: (Int) -> Unit,
    onDropCreateHeadingAt: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var lastHapticSlot by remember { mutableIntStateOf(-1) }
    var lastDropMode by remember { mutableStateOf(MagicPlusDropMode.NONE) }

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "magicPlusScale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 18.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "magicPlusElevation"
    )

    val rowHeightPx = with(density) { 58.dp.toPx() }.coerceAtLeast(1f)
    val leftEdgeThresholdPx = with(density) { (-210).dp.toPx() }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = dragOffset.x.takeIf { it.isFinite() }?.roundToInt() ?: 0,
                    y = dragOffset.y.takeIf { it.isFinite() }?.roundToInt() ?: 0
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = CircleShape,
                ambientColor = colors.thingsBlue.copy(alpha = 0.45f),
                spotColor = colors.thingsBlue.copy(alpha = 0.55f)
            )
            .size(58.dp)
            .clip(CircleShape)
            .background(colors.thingsBlue)
            .clickable(enabled = !isDragging) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onQuickTapCreateTodo()
            }
            .pointerInput(itemCount, allowHeadingDrop) {
                detectDragGestures(
                    onDragStart = {
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
                    },
                    onDragEnd = {
                        val finalSlot = lastHapticSlot.coerceIn(0, itemCount.coerceAtLeast(0))
                        val finalMode = lastDropMode
                        isDragging = false
                        dragOffset = Offset.Zero
                        onDragStateChanged(MagicPlusDragState(isDragging = false))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        if (finalMode == MagicPlusDropMode.INSERT_HEADING && allowHeadingDrop) {
                            onDropCreateHeadingAt(finalSlot)
                        } else {
                            onDropCreateTodoAt(finalSlot)
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = Offset.Zero
                        onDragStateChanged(MagicPlusDragState(isDragging = false))
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount.x.isFinite() && dragAmount.y.isFinite()) {
                            dragOffset += dragAmount
                        }

                        // Calculate how many rows upward the user dragged from the bottom-right corner
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

                        if (computedSlot != lastHapticSlot || mode != lastDropMode) {
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
            imageVector = if (isDragging && lastDropMode == MagicPlusDropMode.INSERT_HEADING) {
                Icons.Outlined.Title
            } else {
                Icons.Outlined.Add
            },
            contentDescription = stringResource(R.string.entity_new_todo),
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}
