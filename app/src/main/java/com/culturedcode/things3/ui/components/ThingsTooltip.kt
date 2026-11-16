package com.culturedcode.things3.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import com.culturedcode.things3.ui.theme.ThingsTheme
import kotlinx.coroutines.delay

/**
 * Pure deterministic timing policy shared across Android hover & touch-preview tooltips:
 * - Cold pointer entry waits [DEFAULT_INITIAL_DELAY_MS] (160ms) so casual cursor sweeps never flicker.
 * - Moving between adjacent controls while a tooltip is visible or within [DEFAULT_WARM_HANDOFF_WINDOW_MS] (450ms)
 *   switches in `0ms` so tooltips never lag behind the cursor or show stale labels.
 * - Stationary touch long-press previews appear after [DEFAULT_TOUCH_LONG_PRESS_MS] (380ms) without consuming
 *   any pointer events, keeping taps, drags, and list scrolling 100% instant.
 */
object TooltipTimingPolicy {
    const val DEFAULT_INITIAL_DELAY_MS = 160L
    const val DEFAULT_WARM_HANDOFF_WINDOW_MS = 450L
    const val DEFAULT_TOUCH_LONG_PRESS_MS = 380L
    const val DEFAULT_AUTO_HIDE_MS = 4000L

    fun effectiveHoverDelayMs(
        isCurrentlyShowingAny: Boolean,
        lastHiddenAtMs: Long,
        nowMs: Long,
        coldDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        warmWindowMs: Long = DEFAULT_WARM_HANDOFF_WINDOW_MS
    ): Long {
        if (isCurrentlyShowingAny) return 0L
        if (lastHiddenAtMs > 0L && nowMs >= lastHiddenAtMs && (nowMs - lastHiddenAtMs) <= warmWindowMs) {
            return 0L
        }
        return coldDelayMs.coerceAtLeast(0L)
    }
}

/**
 * Pure in-tree GPU Tooltip Coordinator:
 * Avoids Android `WindowManager` `Popup` windows completely, eliminating:
 * 1. The ChromeOS / Android hover-stealing strobe loop (`Enter -> Popup -> Exit -> Destroy -> Enter`)
 * 2. Wayland / SurfaceFlinger child-window creation flashes.
 * Because the rendered tooltip layer has zero pointer modifiers, hit-testing passes 100% through it.
 */
@Stable
class ThingsTooltipHostState(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class ActiveEntry(
        val ownerId: Any,
        var text: String,
        var anchorBounds: IntRect
    )

    private val activeStack = mutableListOf<ActiveEntry>()

    var activeOwnerId by mutableStateOf<Any?>(null)
        private set
    var activeText by mutableStateOf("")
        private set
    var activeAnchorBounds by mutableStateOf(IntRect.Zero)
        private set

    private var lastHiddenAtMs: Long = 0L

    val isShowing: Boolean
        get() = activeOwnerId != null && activeText.isNotBlank()

    fun resolveHoverDelayMs(
        coldDelayMs: Long = TooltipTimingPolicy.DEFAULT_INITIAL_DELAY_MS
    ): Long {
        return TooltipTimingPolicy.effectiveHoverDelayMs(
            isCurrentlyShowingAny = isShowing,
            lastHiddenAtMs = lastHiddenAtMs,
            nowMs = clockMs(),
            coldDelayMs = coldDelayMs
        )
    }

    private fun syncActiveFromStack() {
        if (activeStack.isEmpty()) {
            activeOwnerId = null
            activeText = ""
            return
        }
        // Pick the innermost (smallest-area) active anchor; break ties in favor of the latest entry
        var best = activeStack.last()
        var bestArea = best.anchorBounds.width.toLong() * best.anchorBounds.height.toLong()
        for (i in activeStack.lastIndex - 1 downTo 0) {
            val candidate = activeStack[i]
            val area = candidate.anchorBounds.width.toLong() * candidate.anchorBounds.height.toLong()
            if (area < bestArea) {
                best = candidate
                bestArea = area
            }
        }
        activeOwnerId = best.ownerId
        activeText = best.text
        activeAnchorBounds = best.anchorBounds
    }

    fun show(ownerId: Any, text: String, anchorBounds: IntRect) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || anchorBounds.width <= 0 || anchorBounds.height <= 0) {
            hide(ownerId, allowWarmHandoff = false)
            return
        }
        activeStack.removeAll { it.ownerId === ownerId }
        activeStack.add(ActiveEntry(ownerId = ownerId, text = trimmed, anchorBounds = anchorBounds))
        syncActiveFromStack()
    }

    fun updateIfActive(ownerId: Any, text: String, anchorBounds: IntRect) {
        val existing = activeStack.firstOrNull { it.ownerId === ownerId } ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            hide(ownerId, allowWarmHandoff = true)
            return
        }
        existing.text = trimmed
        if (anchorBounds.width > 0 && anchorBounds.height > 0) {
            existing.anchorBounds = anchorBounds
        }
        syncActiveFromStack()
    }

    fun hide(ownerId: Any, allowWarmHandoff: Boolean = true) {
        val wasShowing = isShowing
        val removed = activeStack.removeAll { it.ownerId === ownerId }
        if (!removed) return
        syncActiveFromStack()
        if (wasShowing && !isShowing) {
            if (allowWarmHandoff) {
                lastHiddenAtMs = clockMs()
            } else {
                lastHiddenAtMs = 0L
            }
        }
    }

    fun dismissAll(allowWarmHandoff: Boolean = false) {
        if (allowWarmHandoff && isShowing) {
            lastHiddenAtMs = clockMs()
        } else if (!allowWarmHandoff) {
            lastHiddenAtMs = 0L
        }
        activeStack.clear()
        activeOwnerId = null
        activeText = ""
    }
}

val LocalThingsTooltipHostState = staticCompositionLocalOf<ThingsTooltipHostState?> { null }

private class BoundsRef(var rect: IntRect = IntRect.Zero)

/**
 * Root or Dialog container that hosts the hit-test-transparent GPU tooltip layer above its content.
 * Safe to wrap both full-screen Activity roots and wrap-content `Dialog` surfaces (`matchParentSize`).
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
private fun BoxScope.ThingsTooltipOverlayLayer(
    hostState: ThingsTooltipHostState,
    hostSize: IntSize,
    hostBoundsInRoot: IntRect
) {
    val density = LocalDensity.current
    val text = hostState.activeText
    val anchorBounds = hostState.activeAnchorBounds
    val isShowing = hostState.activeOwnerId != null && text.isNotBlank()

    if (!isShowing || hostSize.width <= 0 || hostSize.height <= 0) {
        return
    }

    val verticalGapPx = with(density) { 8.dp.roundToPx() }
    val screenMarginPx = with(density) { 8.dp.roundToPx() }

    Box(modifier = Modifier.matchParentSize()) {
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
                    val localLeft = anchorBounds.left - hostBoundsInRoot.left
                    val localTop = anchorBounds.top - hostBoundsInRoot.top
                    val localBottom = anchorBounds.bottom - hostBoundsInRoot.top

                    val centeredX = localLeft + (anchorBounds.width - placeable.width) / 2
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
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xF21B1F2A))
                .border(
                    width = 0.75.dp,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(7.dp)
                )
                .padding(horizontal = 9.5.dp, vertical = 5.dp)
        ) {
            Text(
                text = text,
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
 * Responsive Things-style tooltip supporting:
 * 1. Mouse / trackpad / stylus hover (`160ms` cold delay, `0ms` warm handoff across adjacent buttons,
 *    and `0ms` live text/position updates when state or language changes while hovered).
 * 2. Optional passive stationary touch long-press preview (`380ms` within `touchSlop` on `PointerEventPass.Initial`
 *    without consuming pointer changes, so taps and scrolling remain 100% immediate).
 */
@Composable
fun ThingsHoverTooltip(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    delayMillis: Long = TooltipTimingPolicy.DEFAULT_INITIAL_DELAY_MS,
    enableTouchLongPress: Boolean = true,
    content: @Composable () -> Unit
) {
    val hostState = LocalThingsTooltipHostState.current
    val ownerId = remember { Any() }
    val boundsRef = remember { BoundsRef() }
    val latestText by rememberUpdatedState(text)

    var isMouseHovered by remember { mutableStateOf(false) }
    var isTouchDown by remember { mutableStateOf(false) }
    var touchSessionId by remember { mutableIntStateOf(0) }

    DisposableEffect(ownerId, hostState) {
        onDispose {
            hostState?.hide(ownerId, allowWarmHandoff = false)
        }
    }

    // Live 0ms text & bounds sync while this control already owns the visible tooltip
    LaunchedEffect(text, enabled, hostState) {
        if (hostState == null) return@LaunchedEffect
        if (!enabled || text.isBlank()) {
            hostState.hide(ownerId, allowWarmHandoff = true)
        } else {
            hostState.updateIfActive(ownerId, text, boundsRef.rect)
        }
    }

    // Mouse / stylus hover lifecycle (with 0ms warm handoff across adjacent controls)
    LaunchedEffect(isMouseHovered, enabled, hostState) {
        if (hostState == null || !enabled || !isMouseHovered) {
            if (!isTouchDown) {
                hostState?.hide(ownerId, allowWarmHandoff = true)
            }
        } else {
            val waitMs = hostState.resolveHoverDelayMs(delayMillis)
            if (waitMs > 0L) {
                delay(waitMs)
            }
            val resolved = latestText
            if (resolved.isNotBlank()) {
                hostState.show(ownerId, resolved, boundsRef.rect)
                delay(TooltipTimingPolicy.DEFAULT_AUTO_HIDE_MS)
                hostState.hide(ownerId, allowWarmHandoff = false)
            }
        }
    }

    // Stationary finger long-press tooltip preview on touchscreens
    LaunchedEffect(touchSessionId, isTouchDown, enabled, enableTouchLongPress, hostState) {
        if (hostState == null || !enabled || !enableTouchLongPress || !isTouchDown) {
            if (!isMouseHovered && !isTouchDown) {
                hostState?.hide(ownerId, allowWarmHandoff = true)
            }
        } else {
            delay(TooltipTimingPolicy.DEFAULT_TOUCH_LONG_PRESS_MS)
            val resolved = latestText
            if (isTouchDown && resolved.isNotBlank()) {
                hostState.show(ownerId, resolved, boundsRef.rect)
                delay(TooltipTimingPolicy.DEFAULT_AUTO_HIDE_MS)
                hostState.hide(ownerId, allowWarmHandoff = false)
            }
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val updated = coords.boundsInRoot().roundToIntRect()
                boundsRef.rect = updated
                hostState?.updateIfActive(ownerId, latestText, updated)
            }
            .pointerInput(enabled, enableTouchLongPress, hostState != null) {
                if (!enabled || hostState == null) return@pointerInput
                val touchSlopPx = viewConfiguration.touchSlop
                var touchOrigin = Offset.Zero
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val primary = event.changes.firstOrNull()
                        val pointerType = primary?.type
                        val isHoverPointer = pointerType == PointerType.Mouse || pointerType == PointerType.Stylus

                        if (isHoverPointer) {
                            when (event.type) {
                                PointerEventType.Enter, PointerEventType.Move -> {
                                    if (event.changes.none { it.pressed }) {
                                        if (!isMouseHovered) {
                                            isMouseHovered = true
                                        }
                                    } else {
                                        if (isMouseHovered) {
                                            isMouseHovered = false
                                        }
                                        hostState.hide(ownerId, allowWarmHandoff = false)
                                    }
                                }
                                PointerEventType.Exit, PointerEventType.Press, PointerEventType.Scroll -> {
                                    if (isMouseHovered) {
                                        isMouseHovered = false
                                    }
                                    hostState.hide(
                                        ownerId,
                                        allowWarmHandoff = event.type == PointerEventType.Exit
                                    )
                                }
                                else -> Unit
                            }
                        } else if (enableTouchLongPress && pointerType == PointerType.Touch) {
                            val pressedChanges = event.changes.filter { it.pressed }
                            when {
                                pressedChanges.size == 1 && !primary.previousPressed -> {
                                    touchOrigin = primary.position
                                    touchSessionId += 1
                                    isTouchDown = true
                                }
                                pressedChanges.size == 1 && isTouchDown -> {
                                    if ((primary.position - touchOrigin).getDistance() > touchSlopPx) {
                                        isTouchDown = false
                                        hostState.hide(ownerId, allowWarmHandoff = false)
                                    }
                                }
                                pressedChanges.size != 1 -> {
                                    if (isTouchDown) {
                                        isTouchDown = false
                                    }
                                    hostState.hide(ownerId, allowWarmHandoff = true)
                                }
                            }
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
 */
@Composable
fun ThingsbookBrandSymbol(
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
