package com.pv.thingsbook.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pv.thingsbook.ui.theme.ThingsTheme

/**
 * Thingsbook 3.0 Material 3 Expressive Project Progress Indicator:
 * - Replaces legacy pie-chart wedges with an open-track rounded Sky Light Blue progress ring
 *   with a breathing gap and crisp rounded stroke caps, transitioning into a filled Sky Light Blue
 *   badge with checkmark at 100%.
 */
@Composable
fun ProjectProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tintColor: Color = ThingsTheme.colors.thingsBlue
) {
    val safeTarget = (if (progress.isFinite()) progress else 0f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = safeTarget,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 340f),
        label = "projectExpressiveProgress"
    )

    Canvas(modifier = modifier.size(size)) {
        val minDim = this.size.minDimension
        if (minDim <= 0f) return@Canvas
        val clamped = (if (animatedProgress.isFinite()) animatedProgress else 0f).coerceIn(0f, 1f)

        if (clamped >= 0.999f) {
            drawCircle(
                color = tintColor,
                radius = minDim / 2f,
                center = center
            )
            val checkPath = Path().apply {
                moveTo(minDim * 0.28f, minDim * 0.53f)
                lineTo(minDim * 0.45f, minDim * 0.69f)
                lineTo(minDim * 0.74f, minDim * 0.34f)
            }
            drawPath(
                path = checkPath,
                color = Color.White,
                style = Stroke(
                    width = (minDim * 0.115f).coerceAtLeast(1.6.dp.toPx()),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            return@Canvas
        }

        val strokeWidth = (minDim * 0.145f).coerceIn(2.0.dp.toPx(), 3.2.dp.toPx())
        val inset = strokeWidth / 2f
        val arcDiameter = (minDim - strokeWidth).coerceAtLeast(0f)
        if (arcDiameter <= 0f) return@Canvas

        // Soft background track
        drawArc(
            color = tintColor.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(arcDiameter, arcDiameter),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Expressive Sky Light Blue rounded-cap progress sweep
        if (clamped > 0.01f) {
            drawArc(
                color = tintColor,
                startAngle = -90f,
                sweepAngle = (clamped * 340f).coerceAtLeast(14f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(arcDiameter, arcDiameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
