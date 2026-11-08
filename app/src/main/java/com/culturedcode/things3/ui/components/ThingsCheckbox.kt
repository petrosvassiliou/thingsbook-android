package com.culturedcode.things3.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.TaskStatus
import com.culturedcode.things3.ui.theme.ThingsTheme

enum class ThingsCheckboxShape {
    ROUNDED_SQUARE,
    CIRCLE
}

/**
 * Authentic Things 3 120Hz Micro-Animated Checkbox:
 * - Single tap toggles `OPEN` <-> `COMPLETED` with spring bounce & haptic click.
 * - Long-press triggers `HapticFeedbackType.LongPress` and opens the Things 3 context menu
 *   to mark the To-Do as `CANCELED` (renders crisp `X` icon) or `COMPLETED`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThingsCheckbox(
    status: TaskStatus,
    onStatusChange: (TaskStatus) -> Unit,
    modifier: Modifier = Modifier,
    shape: ThingsCheckboxShape = ThingsCheckboxShape.ROUNDED_SQUARE,
    isDashedSomeday: Boolean = false,
    size: Dp = 20.dp
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    var showCancelMenu by remember { mutableStateOf(false) }

    val isActive = status == TaskStatus.COMPLETED || status == TaskStatus.CANCELED

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "checkboxScale"
    )

    val fillFraction by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "checkboxFillFraction"
    )

    val fillColor by animateColorAsState(
        targetValue = if (isActive) colors.checkboxCheckedFill else Color.Transparent,
        animationSpec = spring(stiffness = 500f),
        label = "checkboxFillColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isActive) colors.checkboxCheckedFill else colors.checkboxBorder,
        animationSpec = spring(stiffness = 500f),
        label = "checkboxBorderColor"
    )

    Box(
        modifier = modifier
            .size(size + 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val nextStatus = if (status == TaskStatus.OPEN) {
                        TaskStatus.COMPLETED
                    } else {
                        TaskStatus.OPEN
                    }
                    onStatusChange(nextStatus)
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showCancelMenu = true
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = 1.75.dp.toPx()
            val rectWidth = (this.size.width - strokePx).coerceAtLeast(0f)
            val rectHeight = (this.size.height - strokePx).coerceAtLeast(0f)
            if (rectWidth <= 0f || rectHeight <= 0f) return@Canvas

            val cornerPx = if (shape == ThingsCheckboxShape.CIRCLE) {
                this.size.minDimension / 2f
            } else {
                5.5.dp.toPx()
            }

            val borderStroke = if (isDashedSomeday && status == TaskStatus.OPEN) {
                Stroke(
                    width = strokePx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
                )
            } else {
                Stroke(width = strokePx)
            }

            // Outer border
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(strokePx / 2f, strokePx / 2f),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = borderStroke
            )

            // Animated fill (clamped against spring overshoot > 1f or < 0f)
            val clampedAlpha = (if (fillFraction.isFinite()) fillFraction else 0f).coerceIn(0f, 1f)
            if (clampedAlpha > 0.01f) {
                drawRoundRect(
                    color = fillColor.copy(alpha = clampedAlpha),
                    topLeft = Offset.Zero,
                    size = this.size,
                    cornerRadius = CornerRadius(cornerPx, cornerPx)
                )

                val iconStroke = 2.dp.toPx()
                val w = this.size.width
                val h = this.size.height

                if (status == TaskStatus.COMPLETED) {
                    // Draw Things 3 checkmark
                    val checkPath = Path().apply {
                        moveTo(w * 0.26f, h * 0.52f)
                        lineTo(w * 0.44f, h * 0.70f)
                        lineTo(w * 0.75f, h * 0.33f)
                    }
                    drawPath(
                        path = checkPath,
                        color = Color.White.copy(alpha = clampedAlpha),
                        style = Stroke(
                            width = iconStroke,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else if (status == TaskStatus.CANCELED) {
                    // Draw Things 3 Canceled 'X' icon
                    val pad = w * 0.30f
                    drawLine(
                        color = Color.White.copy(alpha = clampedAlpha),
                        start = Offset(pad, pad),
                        end = Offset(w - pad, h - pad),
                        strokeWidth = iconStroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White.copy(alpha = clampedAlpha),
                        start = Offset(w - pad, pad),
                        end = Offset(pad, h - pad),
                        strokeWidth = iconStroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showCancelMenu,
            onDismissRequest = { showCancelMenu = false }
        ) {
            if (status != TaskStatus.COMPLETED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_complete)) },
                    onClick = {
                        showCancelMenu = false
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onStatusChange(TaskStatus.COMPLETED)
                    }
                )
            }
            if (status != TaskStatus.CANCELED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_cancel_todo)) },
                    onClick = {
                        showCancelMenu = false
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStatusChange(TaskStatus.CANCELED)
                    }
                )
            }
            if (status != TaskStatus.OPEN) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_reopen_todo)) },
                    onClick = {
                        showCancelMenu = false
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onStatusChange(TaskStatus.OPEN)
                    }
                )
            }
        }
    }
}
