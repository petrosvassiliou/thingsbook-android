package com.culturedcode.things3.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.culturedcode.things3.ui.theme.ThingsTheme
import kotlinx.coroutines.delay

/**
 * Non-intrusive Things-for-Mac style mouse/stylus hover tooltip.
 *
 * - Observes `PointerEventType.Enter`, `Exit`, and `Press` on `PointerEventPass.Initial` WITHOUT
 *   consuming pointer events, so clicks, long-presses, and Drag & Drop gestures (such as MagicPlusFab)
 *   work completely undisturbed.
 * - Positions smartly above the hovered symbol (or flips below if near the top edge of the window).
 */
@Composable
fun ThingsHoverTooltip(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    delayMillis: Long = 240L,
    content: @Composable () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(isHovered, enabled, text) {
        if (isHovered && enabled && text.isNotBlank()) {
            delay(delayMillis)
            isVisible = true
        } else {
            isVisible = false
        }
    }

    val verticalGapPx = with(density) { 8.dp.toPx().toInt() }
    val screenMarginPx = with(density) { 8.dp.toPx().toInt() }

    val positionProvider = remember(verticalGapPx, screenMarginPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val centeredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                val maxX = (windowSize.width - popupContentSize.width - screenMarginPx).coerceAtLeast(screenMarginPx)
                val clampedX = centeredX.coerceIn(screenMarginPx, maxX)

                val aboveY = anchorBounds.top - popupContentSize.height - verticalGapPx
                val belowY = anchorBounds.bottom + verticalGapPx
                val finalY = if (aboveY >= screenMarginPx) {
                    aboveY
                } else {
                    belowY.coerceAtMost((windowSize.height - popupContentSize.height - screenMarginPx).coerceAtLeast(0))
                }
                return IntOffset(clampedX, finalY)
            }
        }
    }

    Box(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    when (event.type) {
                        PointerEventType.Enter -> isHovered = true
                        PointerEventType.Exit -> {
                            isHovered = false
                            isVisible = false
                        }
                        PointerEventType.Press -> {
                            isHovered = false
                            isVisible = false
                        }
                        else -> Unit
                    }
                }
            }
        }
    ) {
        content()

        if (isVisible && enabled && text.isNotBlank()) {
            Popup(
                popupPositionProvider = positionProvider,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    clippingEnabled = true
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = Color(0xF01C2026),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .border(
                            width = 0.75.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(7.dp)
                        )
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/**
 * The Thingsbook App Symbol (rounded macOS-style cobalt squircle with white checkmark)
 * with an optional animated Things-Red numeric badge showing the open "Today" (`Heute`) task count.
 */
@Composable
fun ThingsbookBrandSymbol(
    todayCount: Int,
    showBadge: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    onClick: (() -> Unit)? = null
) {
    val colors = ThingsTheme.colors
    val shouldShowCount = showBadge && todayCount > 0
    val badgeScale by animateFloatAsState(
        targetValue = if (shouldShowCount) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thingsbookSymbolBadgeScale"
    )

    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(onClick = onClick)
                        .padding(2.dp)
                } else {
                    Modifier.padding(2.dp)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(size * 0.27f),
                    ambientColor = colors.thingsBlue.copy(alpha = 0.3f),
                    spotColor = colors.thingsBlue.copy(alpha = 0.4f)
                )
        ) {
            val w = this.size.width
            val h = this.size.height
            if (w <= 0f || h <= 0f) return@Canvas
            val corner = w * 0.27f

            // Outer white/card rim + Things cobalt gradient inner squircle
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF428BFF), Color(0xFF1E6BF1))
                ),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(corner, corner)
            )

            // Crisp white checkmark
            val checkPath = Path().apply {
                moveTo(w * 0.27f, h * 0.53f)
                lineTo(w * 0.44f, h * 0.70f)
                lineTo(w * 0.75f, h * 0.33f)
            }
            drawPath(
                path = checkPath,
                color = Color.White,
                style = Stroke(
                    width = (w * 0.105f).coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        AnimatedVisibility(
            visible = shouldShowCount,
            enter = scaleIn(spring(dampingRatio = 0.65f, stiffness = 450f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-5).dp)
        ) {
            val badgeText = if (todayCount > 99) "99+" else todayCount.toString()
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val s = if (badgeScale.isFinite()) badgeScale.coerceAtLeast(0f) else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(CircleShape)
                    .background(colors.deadlineUrgentRed)
                    .border(1.25.dp, colors.cardSurface, CircleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 10.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}
