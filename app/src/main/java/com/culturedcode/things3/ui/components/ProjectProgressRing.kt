package com.culturedcode.things3.ui.components

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.culturedcode.things3.ui.theme.ThingsTheme

/**
 * Authentic Things 3 Project Pie-Chart Progress Ring:
 * - Draws an outer crisp circular ring and an inner pie wedge that fills clockwise from 12 o'clock (-90°)
 *   live as child To-Dos are completed.
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
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 360f),
        label = "projectPieProgress"
    )

    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 1.65.dp.toPx()
        val outerRadius = (this.size.minDimension - strokeWidth) / 2f
        if (outerRadius <= 0f) return@Canvas

        // Outer circular border
        drawCircle(
            color = tintColor,
            radius = outerRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // Inner Pie-Chart fill with subtle breathing gap from outer ring
        val clampedProgress = (if (animatedProgress.isFinite()) animatedProgress else 0f).coerceIn(0f, 1f)
        if (clampedProgress > 0.005f) {
            val piePadding = strokeWidth * 1.9f
            val pieDiameter = this.size.minDimension - (piePadding * 2f)
            if (pieDiameter > 0f) {
                drawArc(
                    color = tintColor,
                    startAngle = -90f,
                    sweepAngle = clampedProgress * 360f,
                    useCenter = true,
                    topLeft = Offset(piePadding, piePadding),
                    size = Size(pieDiameter, pieDiameter)
                )
            }
        }
    }
}
