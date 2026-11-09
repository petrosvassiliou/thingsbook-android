package com.culturedcode.things3.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import com.culturedcode.things3.data.notifications.AppIconBadgeUpdater
import com.culturedcode.things3.ui.theme.ThingsTheme
import kotlinx.coroutines.delay

/**
 * Pure in-tree GPU Tooltip Coordinator:
 * Avoids Android `WindowManager` `Popup` windows completely, eliminating:
 * 1. The ChromeOS / Android hover-stealing strobe loop (`Enter -> Popup -> Exit -> Destroy -> Enter`)
 * 2. Wayland / SurfaceFlinger child-window creation flashes.
 * Because the rendered tooltip layer has zero pointer modifiers, hit-testing passes 100% through it.
 */
@Stable
class ThingsTooltipHostState {
    var activeOwnerId by mutableStateOf<Any?>(null)
        private set
    var activeText by mutableStateOf("")
        private set
    var activeAnchorBounds by mutableStateOf(IntRect.Zero)
        private set

    fun show(ownerId: Any, text: String, anchorBounds: IntRect) {
        if (text.isBlank() || anchorBounds.width <= 0 || anchorBounds.height <= 0) return
        activeOwnerId = ownerId
        activeText = text
        activeAnchorBounds = anchorBounds
    }

    fun hide(ownerId: Any) {
        if (activeOwnerId === ownerId) {
            activeOwnerId = null
        }
    }
}

val LocalThingsTooltipHostState = staticCompositionLocalOf<ThingsTooltipHostState?> { null }

private class BoundsRef(var rect: IntRect = IntRect.Zero)

/**
 * Root container that hosts the hit-test-transparent GPU tooltip layer above its content.
 */
@Composable
fun ThingsTooltipHostBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hostState = remember { ThingsTooltipHostState() }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var rootOffsetInWindow by remember { mutableStateOf(IntRect.Zero) }

    CompositionLocalProvider(LocalThingsTooltipHostState provides hostState) {
        Box(
            modifier = modifier
                .onSizeChanged { hostSize = it }
                .onGloballyPositioned { coords ->
                    rootOffsetInWindow = coords.boundsInRoot().roundToIntRect()
                }
        ) {
            content()
            ThingsTooltipOverlayLayer(
                hostState = hostState,
                hostSize = hostSize,
                hostBoundsInRoot = rootOffsetInWindow
            )
        }
    }
}

@Composable
private fun ThingsTooltipOverlayLayer(
    hostState: ThingsTooltipHostState,
    hostSize: IntSize,
    hostBoundsInRoot: IntRect
) {
    val density = LocalDensity.current
    val isShowing = hostState.activeOwnerId != null && hostState.activeText.isNotBlank()

    var displayedText by remember { mutableStateOf("") }
    var displayedBounds by remember { mutableStateOf(IntRect.Zero) }

    if (isShowing) {
        displayedText = hostState.activeText
        displayedBounds = hostState.activeAnchorBounds
    }

    val alpha by animateFloatAsState(
        targetValue = if (isShowing) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isShowing) 150 else 110,
            easing = FastOutSlowInEasing
        ),
        label = "thingsTooltipAlpha"
    )

    if (alpha <= 0.005f || displayedText.isBlank() || hostSize.width <= 0 || hostSize.height <= 0) {
        return
    }

    val verticalGapPx = with(density) { 8.dp.roundToPx() }
    val screenMarginPx = with(density) { 8.dp.roundToPx() }
    val slideOffsetPx = with(density) { 3.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val maxTooltipW = (hostSize.width - screenMarginPx * 2).coerceAtLeast(60)
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = 0,
                            maxWidth = maxTooltipW.coerceAtMost(constraints.maxWidth),
                            minHeight = 0
                        )
                    )
                    val localLeft = displayedBounds.left - hostBoundsInRoot.left
                    val localTop = displayedBounds.top - hostBoundsInRoot.top
                    val localBottom = displayedBounds.bottom - hostBoundsInRoot.top

                    val centeredX = localLeft + (displayedBounds.width - placeable.width) / 2
                    val maxX = (hostSize.width - placeable.width - screenMarginPx).coerceAtLeast(screenMarginPx)
                    val finalX = centeredX.coerceIn(screenMarginPx, maxX)

                    val aboveY = localTop - placeable.height - verticalGapPx
                    val belowY = localBottom + verticalGapPx
                    val maxY = (hostSize.height - placeable.height - screenMarginPx).coerceAtLeast(0)
                    val finalY = if (aboveY >= screenMarginPx) {
                        aboveY
                    } else {
                        belowY.coerceIn(0, maxY)
                    }

                    layout(placeable.width, placeable.height) {
                        placeable.place(finalX, finalY)
                    }
                }
                .graphicsLayer {
                    val safeAlpha = if (alpha.isFinite()) alpha.coerceIn(0f, 1f) else 0f
                    this.alpha = safeAlpha
                    this.translationY = (1f - safeAlpha) * slideOffsetPx
                }
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xEB1D2128))
                .border(
                    width = 0.75.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(7.dp)
                )
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text(
                text = displayedText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White,
                maxLines = 2
            )
        }
    }
}

/**
 * Non-intrusive Things-for-Mac style mouse/stylus hover tooltip.
 *
 * - Uses pure in-tree GPU rendering via [LocalThingsTooltipHostState] — never spawns Android `PopupWindow`s,
 *   completely preventing hover-exit oscillation/flashing on Chromebooks, tablets, and desktop mice.
 * - Ignores touch finger taps (`PointerType.Touch`), activating strictly on mouse/stylus hover.
 */
@Composable
fun ThingsHoverTooltip(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    delayMillis: Long = 420L,
    content: @Composable () -> Unit
) {
    val hostState = LocalThingsTooltipHostState.current
    val ownerId = remember { Any() }
    val boundsRef = remember { BoundsRef() }
    var isMouseHovered by remember { mutableStateOf(false) }

    DisposableEffect(ownerId, hostState) {
        onDispose {
            hostState?.hide(ownerId)
        }
    }

    LaunchedEffect(isMouseHovered, enabled, text, hostState) {
        if (hostState == null || !enabled || !isMouseHovered || text.isBlank()) {
            hostState?.hide(ownerId)
        } else {
            delay(delayMillis)
            hostState.show(ownerId, text, boundsRef.rect)
            // Calm auto-hide after 3.5s so a parked mouse cursor never blocks the view indefinitely
            delay(3500L)
            hostState.hide(ownerId)
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                boundsRef.rect = coords.boundsInRoot().roundToIntRect()
            }
            .pointerInput(enabled, hostState != null) {
                if (!enabled || hostState == null) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointerType = event.changes.firstOrNull()?.type
                        val isHoverPointer = pointerType == PointerType.Mouse || pointerType == PointerType.Stylus
                        when (event.type) {
                            PointerEventType.Enter -> {
                                if (isHoverPointer && event.changes.none { it.pressed }) {
                                    isMouseHovered = true
                                }
                            }
                            PointerEventType.Exit, PointerEventType.Press -> {
                                if (isMouseHovered) {
                                    isMouseHovered = false
                                }
                                hostState.hide(ownerId)
                            }
                            else -> Unit
                        }
                    }
                }
            }
    ) {
        content()
    }
}

/**
 * The Thingsbook in-app Brand Symbol (rounded macOS-style cobalt squircle with white checkmark).
 * Intentionally never renders a count badge inside the app UI.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun ThingsbookBrandSymbol(
    todayCount: Int = 0,
    showBadge: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    onClick: (() -> Unit)? = null
) {
    val colors = ThingsTheme.colors

    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
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
                    ambientColor = colors.thingsBlue.copy(alpha = 0.28f),
                    spotColor = colors.thingsBlue.copy(alpha = 0.36f)
                )
        ) {
            val w = this.size.width
            val h = this.size.height
            if (w <= 0f || h <= 0f) return@Canvas
            val corner = w * 0.27f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF428BFF), Color(0xFF1E6BF1))
                ),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(corner, corner)
            )

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
    }
}

/**
 * Preview of the circular Chromebook Taskbar / Android Launcher Thingsbook icon
 * (`ic_taskbar_icon_badge_0` .. `ic_taskbar_icon_badge_20plus`) with the subtle
 * Taskbar active-app bar underneath.
 */
@Composable
fun ThingsbookTaskbarSymbolPreview(
    todayCount: Int,
    showBadge: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val previewCount = if (todayCount > 0) todayCount else 3
    val iconRes = AppIconBadgeUpdater.getTaskbarIconDrawableRes(
        todayCount = previewCount,
        enabled = showBadge
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(size)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(size * 0.42f)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF9EA8FF))
        )
    }
}
