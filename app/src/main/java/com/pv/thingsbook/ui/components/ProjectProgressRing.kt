package com.pv.thingsbook.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pv.thingsbook.ui.theme.ThingsTheme

/**
 * Thingsbook 3.0 Horizontal Linear Progress Indicator:
 * - Replaces circular pie/ring indicators with a clean Sky Light Blue horizontal linear capsule bar,
 *   transitioning into a filled Sky Light Blue capsule badge with checkmark at 100%, plus an optional
 *   `completed/total` fraction label for Phases and Milestones.
 */
@Composable
fun ProjectProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    inProgressFraction: Float = 0f,
    size: Dp = 20.dp,
    tintColor: Color = ThingsTheme.colors.thingsBlue,
    showFraction: Boolean = false,
    completedCount: Int = 0,
    totalCount: Int = 0
) {
    val colors = ThingsTheme.colors
    val safeTarget = (if (progress.isFinite()) progress else 0f).coerceIn(0f, 1f)
    val safeInProgressTarget = (if (inProgressFraction.isFinite()) inProgressFraction else 0f)
        .coerceIn(0f, (1f - safeTarget).coerceAtLeast(0f))
    val animatedProgress by animateFloatAsState(
        targetValue = safeTarget,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 340f),
        label = "projectLinearProgress"
    )
    val animatedInProgress by animateFloatAsState(
        targetValue = safeInProgressTarget,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 340f),
        label = "projectLinearInProgress"
    )
    val barWidth = (size * 1.55f).coerceIn(22.dp, 42.dp)
    val barHeight = (size * 0.34f).coerceIn(5.dp, 8.dp)
    val badgeHeight = (size * 0.68f).coerceIn(11.dp, 16.dp)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        val clamped = (if (animatedProgress.isFinite()) animatedProgress else 0f).coerceIn(0f, 1f)
        if (clamped >= 0.999f) {
            Canvas(
                modifier = Modifier
                    .width(barWidth)
                    .height(badgeHeight)
            ) {
                val w = this.size.width
                val h = this.size.height
                if (w <= 0f || h <= 0f) return@Canvas
                val r = h / 2f
                drawRoundRect(
                    color = tintColor,
                    topLeft = Offset.Zero,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(r, r)
                )
                val cx = w / 2f
                val cy = h / 2f
                val s = h * 0.28f
                val checkPath = Path().apply {
                    moveTo(cx - s * 1.05f, cy + s * 0.05f)
                    lineTo(cx - s * 0.2f, cy + s * 0.85f)
                    lineTo(cx + s * 1.15f, cy - s * 0.75f)
                }
                drawPath(
                    path = checkPath,
                    color = Color.White,
                    style = Stroke(
                        width = (h * 0.14f).coerceAtLeast(1.5.dp.toPx()),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .width(barWidth)
                    .height(barHeight)
            ) {
                val w = this.size.width
                val h = this.size.height
                if (w <= 0f || h <= 0f) return@Canvas
                val r = h / 2f
                // Soft capsule background track
                drawRoundRect(
                    color = tintColor.copy(alpha = 0.20f),
                    topLeft = Offset.Zero,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(r, r)
                )
                val clampedInProgress = (if (animatedInProgress.isFinite()) animatedInProgress else 0f)
                    .coerceIn(0f, (1f - clamped).coerceAtLeast(0f))
                if (clampedInProgress > 0.005f) {
                    val wipW = (w * (clamped + clampedInProgress).coerceAtMost(1f)).coerceIn(h, w)
                    drawRoundRect(
                        color = colors.somedayWarmAmber.copy(alpha = 0.72f),
                        topLeft = Offset.Zero,
                        size = Size(wipW, h),
                        cornerRadius = CornerRadius(r, r)
                    )
                }
                // Sky Light Blue linear fill
                if (clamped > 0.01f) {
                    val fillW = (w * clamped).coerceIn(h, w)
                    drawRoundRect(
                        color = tintColor,
                        topLeft = Offset.Zero,
                        size = Size(fillW, h),
                        cornerRadius = CornerRadius(r, r)
                    )
                }
            }
        }

        if (showFraction && totalCount > 0) {
            Text(
                text = "$completedCount/$totalCount",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.secondaryText
            )
        }
    }
}

