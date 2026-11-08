package com.culturedcode.things3.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.PathMeasure
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
import kotlinx.coroutines.launch

enum class ThingsCheckboxShape {
    ROUNDED_SQUARE,
    CIRCLE
}

/**
 * Authentic Things 3 120Hz Micro-Animated Checkbox:
 * - Single tap toggles `OPEN` <-> `COMPLETED` with tactile spring pop, expanding luminous halo ring,
 *   and progressive stroke-drawn checkmark (`PathMeasure`).
 * - Long-press triggers `HapticFeedbackType.LongPress` and opens the Things 3 context menu
 *   to mark the To-Do as `CANCELED` (renders crisp `X` icon) or `COMPLETED`.
 * - Includes mouse-hover tooltip support (`ThingsHoverTooltip`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThingsCheckbox(
    status: TaskStatus,
    onStatusChange: (TaskStatus) -> Unit,
    modifier: Modifier = Modifier,
    shape: ThingsCheckboxShape = ThingsCheckboxShape.ROUNDED_SQUARE,
    isDashedSomeday: Boolean = false,
    size: Dp = 20.dp,
    showHoverTooltip: Boolean = true,
    tooltipText: String? = null
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    var showCancelMenu by remember { mutableStateOf(false) }

    val isActive = status == TaskStatus.COMPLETED || status == TaskStatus.CANCELED

    var prevStatus by remember { mutableStateOf(status) }
    val popScaleAnim = remember { Animatable(1f) }
    val haloProgressAnim = remember { Animatable(0f) }

    LaunchedEffect(status) {
        if (prevStatus == TaskStatus.OPEN && status == TaskStatus.COMPLETED) {
            launch {
                popScaleAnim.snapTo(0.86f)
                popScaleAnim.animateTo(
                    targetValue = 1.18f,
                    animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
                )
                popScaleAnim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                haloProgressAnim.snapTo(0f)
                haloProgressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing)
                )
            }
        } else {
            popScaleAnim.snapTo(1f)
            haloProgressAnim.snapTo(0f)
        }
        prevStatus = status
    }

    val baseScale by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "checkboxScale"
    )

    val fillFraction by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
        label = "checkboxFillFraction"
    )

    val checkDrawProgress by animateFloatAsState(
        targetValue = if (status == TaskStatus.COMPLETED) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (status == TaskStatus.COMPLETED) 250 else 120,
            delayMillis = if (status == TaskStatus.COMPLETED) 35 else 0,
            easing = FastOutSlowInEasing
        ),
        label = "checkDrawProgress"
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

    val resolvedTooltip = tooltipText ?: when (status) {
        TaskStatus.OPEN -> stringResource(R.string.action_complete)
        TaskStatus.COMPLETED -> stringResource(R.string.action_reopen_todo)
        TaskStatus.CANCELED -> stringResource(R.string.action_reopen_todo)
    }

    val checkboxCore: @Composable () -> Unit = {
        Box(
            modifier = modifier
                .size(size + 10.dp)
                .graphicsLayer {
                    val combinedScale = (baseScale * popScaleAnim.value).coerceIn(0.6f, 1.4f)
                    scaleX = combinedScale
                    scaleY = combinedScale
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
            Canvas(modifier = Modifier.size(size + 10.dp)) {
                val canvasW = this.size.width
                val canvasH = this.size.height
                val boxSizePx = size.toPx().coerceAtMost(minOf(canvasW, canvasH))
                if (boxSizePx <= 0f) return@Canvas

                val insetX = (canvasW - boxSizePx) / 2f
                val insetY = (canvasH - boxSizePx) / 2f
                val strokePx = 1.75.dp.toPx()
                val rectWidth = (boxSizePx - strokePx).coerceAtLeast(0f)
                val rectHeight = (boxSizePx - strokePx).coerceAtLeast(0f)
                if (rectWidth <= 0f || rectHeight <= 0f) return@Canvas

                val cornerPx = if (shape == ThingsCheckboxShape.CIRCLE) {
                    boxSizePx / 2f
                } else {
                    5.5.dp.toPx()
                }

                // Expanding luminous completion halo ring
                val haloP = haloProgressAnim.value.coerceIn(0f, 1f)
                if (haloP > 0.01f && haloP < 0.99f) {
                    val haloExpand = haloP * 5.dp.toPx()
                    val haloAlpha = ((1f - haloP) * 0.46f).coerceIn(0f, 1f)
                    val haloSize = boxSizePx + haloExpand * 2f
                    val haloCorner = cornerPx + haloExpand
                    drawRoundRect(
                        color = colors.checkboxCheckedFill.copy(alpha = haloAlpha),
                        topLeft = Offset(insetX - haloExpand, insetY - haloExpand),
                        size = Size(haloSize, haloSize),
                        cornerRadius = CornerRadius(haloCorner, haloCorner),
                        style = Stroke(width = (1.8.dp.toPx() * (1f - haloP * 0.45f)).coerceAtLeast(0.6f))
                    )
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
                    topLeft = Offset(insetX + strokePx / 2f, insetY + strokePx / 2f),
                    size = Size(rectWidth, rectHeight),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = borderStroke
                )

                // Animated fill (clamped against spring overshoot > 1f or < 0f)
                val clampedAlpha = (if (fillFraction.isFinite()) fillFraction else 0f).coerceIn(0f, 1f)
                if (clampedAlpha > 0.01f) {
                    drawRoundRect(
                        color = fillColor.copy(alpha = clampedAlpha),
                        topLeft = Offset(insetX, insetY),
                        size = Size(boxSizePx, boxSizePx),
                        cornerRadius = CornerRadius(cornerPx, cornerPx)
                    )

                    val iconStroke = 2.dp.toPx()
                    val w = boxSizePx
                    val h = boxSizePx

                    if (status == TaskStatus.COMPLETED) {
                        val clampedProgress = checkDrawProgress.coerceIn(0f, 1f)
                        if (clampedProgress > 0.01f) {
                            val fullCheckPath = Path().apply {
                                moveTo(insetX + w * 0.25f, insetY + h * 0.52f)
                                lineTo(insetX + w * 0.44f, insetY + h * 0.70f)
                                lineTo(insetX + w * 0.76f, insetY + h * 0.33f)
                            }
                            val segmentPath = Path()
                            val pathMeasure = PathMeasure()
                            pathMeasure.setPath(fullCheckPath, false)
                            pathMeasure.getSegment(
                                startDistance = 0f,
                                stopDistance = pathMeasure.length * clampedProgress,
                                destination = segmentPath,
                                startWithMoveTo = true
                            )
                            drawPath(
                                path = segmentPath,
                                color = Color.White.copy(alpha = clampedAlpha),
                                style = Stroke(
                                    width = iconStroke,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    } else if (status == TaskStatus.CANCELED) {
                        val pad = w * 0.30f
                        drawLine(
                            color = Color.White.copy(alpha = clampedAlpha),
                            start = Offset(insetX + pad, insetY + pad),
                            end = Offset(insetX + w - pad, insetY + h - pad),
                            strokeWidth = iconStroke,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White.copy(alpha = clampedAlpha),
                            start = Offset(insetX + w - pad, insetY + pad),
                            end = Offset(insetX + pad, insetY + h - pad),
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

    if (showHoverTooltip && !showCancelMenu) {
        ThingsHoverTooltip(text = resolvedTooltip) {
            checkboxCore()
        }
    } else {
        checkboxCore()
    }
}
