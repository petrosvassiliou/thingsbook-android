package com.culturedcode.things3.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.culturedcode.things3.ui.theme.ThingsTheme
import kotlinx.coroutines.delay

/**
 * Pure deterministic timing & placement policy shared across Android hover & touch-preview tooltips:
 * - Cold pointer entry waits [DEFAULT_INITIAL_DELAY_MS] (160ms) so casual cursor sweeps never flicker.
 * - Moving between adjacent controls while a tooltip is visible or within [DEFAULT_WARM_HANDOFF_WINDOW_MS] (450ms)
 *   switches in `0ms` so tooltips never lag behind the cursor or show stale labels.
 * - Stationary touch long-press previews appear after [DEFAULT_TOUCH_LONG_PRESS_MS] (380ms) with haptic feedback,
 *   suppress accidental button `onClick` on finger lift, and remain readable for [DEFAULT_TOUCH_RELEASE_HIDE_MS]
 *   (1800ms) after the finger moves out of the way (or until the next tap/scroll anywhere on screen).
 */
object TooltipTimingPolicy {
    const val DEFAULT_INITIAL_DELAY_MS = 160L
    const val DEFAULT_WARM_HANDOFF_WINDOW_MS = 450L
    const val DEFAULT_TOUCH_LONG_PRESS_MS = 380L
    const val DEFAULT_TOUCH_RELEASE_HIDE_MS = 1800L
    const val DEFAULT_TOUCH_MAX_VISIBLE_MS = 4000L

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

    /**
     * Returns true when a pointer event represents an unpressed hover cursor/stylus/trackpad signal,
     * independent of `PointerType` (ChromeOS ARCVM & Android multi-touch trackpads frequently emit
     * `ACTION_HOVER_ENTER` / `ACTION_HOVER_MOVE` with `PointerType.Touch` or `PointerType.Unknown`).
     */
    fun isUnpressedHoverEvent(
        eventType: PointerEventType,
        hasChanges: Boolean,
        anyPressed: Boolean,
        anyPreviousPressed: Boolean
    ): Boolean {
        if (!hasChanges || anyPressed || anyPreviousPressed) return false
        return eventType == PointerEventType.Enter || eventType == PointerEventType.Move
    }

    /**
     * Computes clamped window-local `(x, y)` coordinates for a tooltip bubble while respecting
     * edge-to-edge system bar insets (`rootSafeTopPx` / `rootSafeBottomPx`) so top-bar tooltips
     * cleanly flip below the toolbar instead of colliding with the Android status bar or camera cutout.
     */
    fun computeTooltipPlacement(
        anchorBounds: IntRect,
        hostBoundsInRoot: IntRect,
        hostSize: IntSize,
        tooltipSize: IntSize,
        rootSafeTopPx: Int,
        rootSafeBottomPx: Int,
        verticalGapPx: Int,
        screenMarginPx: Int
    ): IntOffset {
        val localLeft = anchorBounds.left - hostBoundsInRoot.left
        val localTop = anchorBounds.top - hostBoundsInRoot.top
        val localBottom = anchorBounds.bottom - hostBoundsInRoot.top

        val minX = screenMarginPx
        val maxX = (hostSize.width - tooltipSize.width - screenMarginPx).coerceAtLeast(minX)
        val centeredX = localLeft + (anchorBounds.width - tooltipSize.width) / 2
        val finalX = centeredX.coerceIn(minX, maxX)

        val localSafeTop = (rootSafeTopPx - hostBoundsInRoot.top).coerceAtLeast(0)
        val localSafeBottom = rootSafeBottomPx.coerceAtLeast(0)

        val minY = localSafeTop + screenMarginPx
        val maxY = (hostSize.height - localSafeBottom - tooltipSize.height - screenMarginPx).coerceAtLeast(minY)

        val aboveY = localTop - tooltipSize.height - verticalGapPx
        val belowY = localBottom + verticalGapPx

        val finalY = if (aboveY >= minY) {
            aboveY.coerceIn(minY, maxY)
        } else {
            belowY.coerceIn(minY, maxY)
        }

        return IntOffset(finalX, finalY)
    }
    data class TooltipBubbleStyle(
        val backgroundColor: Color,
        val borderColor: Color,
        val textColor: Color,
        val shadowAmbientAlpha: Float,
        val shadowSpotAlpha: Float
    )

    fun resolveBubbleStyle(isDark: Boolean): TooltipBubbleStyle {
        return if (isDark) {
            // Soft elevated slate surface matching DarkThingsPalette — never harsh pitch black
            TooltipBubbleStyle(
                backgroundColor = Color(0xF22E323D),
                borderColor = Color(0x1CFFFFFF),
                textColor = Color(0xFFD4D9E2),
                shadowAmbientAlpha = 0.20f,
                shadowSpotAlpha = 0.26f
            )
        } else {
            // Discreet warm-pearl surface with hairline border & muted slate text
            TooltipBubbleStyle(
                backgroundColor = Color(0xF2F7F8FB),
                borderColor = Color(0x1F1C1E24),
                textColor = Color(0xFF424854),
                shadowAmbientAlpha = 0.07f,
                shadowSpotAlpha = 0.11f
            )
        }
    }
    fun shouldAttachTooltip(
        isTopBarIconScope: Boolean,
        enabled: Boolean,
        hasHost: Boolean,
        text: String
    ): Boolean = isTopBarIconScope && enabled && hasHost && text.isNotBlank()
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
        var anchorBounds: IntRect,
        var isTouchPreview: Boolean,
        var isReleasedTouch: Boolean
    )

    private val activeStack = mutableListOf<ActiveEntry>()

    var activeOwnerId by mutableStateOf<Any?>(null)
        private set
    var activeText by mutableStateOf("")
        private set
    var activeAnchorBounds by mutableStateOf(IntRect.Zero)
        private set
    var activeIsTouchPreview by mutableStateOf(false)
        private set
    var releasedTouchTick by mutableIntStateOf(0)
        private set

    private var lastHiddenAtMs: Long = 0L

    val isShowing: Boolean
        get() = activeOwnerId != null && activeText.isNotBlank()

    fun hasReleasedTouchPreview(): Boolean =
        activeStack.any { it.isReleasedTouch }

    fun resolveHoverDelayMs(
        coldDelayMs: Long = TooltipTimingPolicy.DEFAULT_INITIAL_DELAY_MS
    ): Long {
        val activelyHoveredShowing = isShowing && !hasReleasedTouchPreview()
        return TooltipTimingPolicy.effectiveHoverDelayMs(
            isCurrentlyShowingAny = activelyHoveredShowing,
            lastHiddenAtMs = lastHiddenAtMs,
            nowMs = clockMs(),
            coldDelayMs = coldDelayMs
        )
    }

    private fun syncActiveFromStack() {
        if (activeStack.isEmpty()) {
            activeOwnerId = null
            activeText = ""
            activeIsTouchPreview = false
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
        activeIsTouchPreview = best.isTouchPreview
    }

    fun show(
        ownerId: Any,
        text: String,
        anchorBounds: IntRect,
        isTouchPreview: Boolean = false
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || anchorBounds.width <= 0 || anchorBounds.height <= 0) {
            hide(ownerId, allowWarmHandoff = false)
            return
        }
        // Clear any lingering post-release touch preview from a previous control
        activeStack.removeAll { it.isReleasedTouch || it.ownerId === ownerId }
        activeStack.add(
            ActiveEntry(
                ownerId = ownerId,
                text = trimmed,
                anchorBounds = anchorBounds,
                isTouchPreview = isTouchPreview,
                isReleasedTouch = false
            )
        )
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

    /**
     * Transitions an active touch-long-press tooltip into post-release lingering mode so the user
     * can read the label for [TooltipTimingPolicy.DEFAULT_TOUCH_RELEASE_HIDE_MS] once their finger lifts.
     */
    fun releaseTouchPreview(ownerId: Any) {
        val existing = activeStack.firstOrNull { it.ownerId === ownerId && it.isTouchPreview } ?: return
        existing.isReleasedTouch = true
        releasedTouchTick += 1
        syncActiveFromStack()
    }

    fun dismissReleasedTouchPreviews(exceptOwnerId: Any? = null) {
        val removed = activeStack.removeAll { it.isReleasedTouch && it.ownerId !== exceptOwnerId }
        if (removed) {
            syncActiveFromStack()
        }
    }

    fun hide(ownerId: Any, allowWarmHandoff: Boolean = true, preserveIfReleasedTouch: Boolean = false) {
        if (preserveIfReleasedTouch) {
            val existing = activeStack.firstOrNull { it.ownerId === ownerId }
            if (existing?.isReleasedTouch == true) return
        }
        val wasShowing = isShowing
        val removed = activeStack.removeAll { it.ownerId === ownerId }
        if (!removed) return
        syncActiveFromStack()
        if (wasShowing && !isShowing) {
            lastHiddenAtMs = if (allowWarmHandoff) clockMs() else 0L
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
        activeIsTouchPreview = false
    }
}

val LocalThingsTooltipHostState = staticCompositionLocalOf<ThingsTooltipHostState?> { null }

/**
 * Gates floating tooltips strictly to the icon buttons inside `TopThingsbookBar`.
 * Defaults to `false` everywhere else so non-top-bar UI renders with zero tooltip wrappers or listeners.
 */
val LocalThingsTopBarTooltipScope = staticCompositionLocalOf { false }

@Composable
fun ThingsTopBarTooltipScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalThingsTopBarTooltipScope provides true, content = content)
}

private class BoundsRef(var rect: IntRect = IntRect.Zero)

/**
 * Root container that hosts the hit-test-transparent GPU tooltip layer above its content.
 * Nested invocations inside dialogs/modals pass straight through with zero wrapper overhead.
 */
@Composable
fun ThingsTooltipHostBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (LocalThingsTooltipHostState.current != null) {
        if (modifier === Modifier) {
            content()
        } else {
            Box(modifier = modifier) {
                content()
            }
        }
        return
    }

    val hostState = remember { ThingsTooltipHostState() }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var rootOffsetInWindow by remember { mutableStateOf(IntRect.Zero) }
    val hasReleasedTouch = hostState.releasedTouchTick > 0 && hostState.hasReleasedTouchPreview()

    // Auto-expire post-release touch tooltips after 1.8s so they stay readable once finger lifts
    LaunchedEffect(hostState.releasedTouchTick) {
        if (hostState.hasReleasedTouchPreview()) {
            delay(TooltipTimingPolicy.DEFAULT_TOUCH_RELEASE_HIDE_MS)
            hostState.dismissReleasedTouchPreviews()
        }
    }

    CompositionLocalProvider(LocalThingsTooltipHostState provides hostState) {
        Box(
            modifier = modifier
                .onSizeChanged { hostSize = it }
                .onGloballyPositioned { coords ->
                    rootOffsetInWindow = coords.boundsInRoot().roundToIntRect()
                }
                .then(
                    if (hasReleasedTouch) {
                        Modifier.pointerInput(hostState) {
                            awaitPointerEventScope {
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    if (ev.type == PointerEventType.Press || ev.type == PointerEventType.Scroll) {
                                        hostState.dismissReleasedTouchPreviews()
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
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
    val colors = ThingsTheme.colors
    val bubbleStyle = TooltipTimingPolicy.resolveBubbleStyle(isDark = colors.isDark)
    val text = hostState.activeText
    val anchorBounds = hostState.activeAnchorBounds
    val isTouchPreview = hostState.activeIsTouchPreview
    val isShowing = hostState.activeOwnerId != null && text.isNotBlank()

    if (!isShowing || hostSize.width <= 0 || hostSize.height <= 0) {
        return
    }

    val safeInsets = WindowInsets.safeDrawing
    val rootSafeTopPx = safeInsets.getTop(density)
    val rootSafeBottomPx = safeInsets.getBottom(density)
    val verticalGapPx = with(density) { (if (isTouchPreview) 13.dp else 7.dp).roundToPx() }
    val screenMarginPx = with(density) { 8.dp.roundToPx() }
    val tooltipShape = RoundedCornerShape(6.dp)

    Layout(
        modifier = Modifier
            .matchParentSize()
            .zIndex(10_000f)
            .semantics {
                paneTitle = text
                liveRegion = LiveRegionMode.Polite
            },
        content = {
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 2.dp,
                        shape = tooltipShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = bubbleStyle.shadowAmbientAlpha),
                        spotColor = Color.Black.copy(alpha = bubbleStyle.shadowSpotAlpha)
                    )
                    .clip(tooltipShape)
                    .background(bubbleStyle.backgroundColor)
                    .border(
                        width = 0.75.dp,
                        color = bubbleStyle.borderColor,
                        shape = tooltipShape
                    )
                    .padding(horizontal = 8.5.dp, vertical = 4.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.1.sp
                    ),
                    color = bubbleStyle.textColor,
                    maxLines = 2
                )
            }
        }
    ) { measurables, constraints ->
        val maxTooltipW = (hostSize.width - screenMarginPx * 2).coerceAtLeast(60)
        val placeable = measurables.first().measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = maxTooltipW.coerceAtMost(constraints.maxWidth),
                minHeight = 0
            )
        )
        val placement = TooltipTimingPolicy.computeTooltipPlacement(
            anchorBounds = anchorBounds,
            hostBoundsInRoot = hostBoundsInRoot,
            hostSize = hostSize,
            tooltipSize = IntSize(placeable.width, placeable.height),
            rootSafeTopPx = rootSafeTopPx,
            rootSafeBottomPx = rootSafeBottomPx,
            verticalGapPx = verticalGapPx,
            screenMarginPx = screenMarginPx
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(placement.x, placement.y)
        }
    }
}

/**
 * Discreet Things-style tooltip strictly active inside [ThingsTopBarTooltipScope] (top-bar icons only):
 * 1. Outside [ThingsTopBarTooltipScope], renders [content] directly with zero layout or pointer overhead.
 * 2. Inside [ThingsTopBarTooltipScope], supports mouse/trackpad/stylus hover (`160ms` cold delay, `0ms` warm
 *    handoff) and stationary touch long-press preview (`380ms` with `1.8s` post-release readability).
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
    val isTopBarScope = LocalThingsTopBarTooltipScope.current
    if (hostState == null || !TooltipTimingPolicy.shouldAttachTooltip(
            isTopBarIconScope = isTopBarScope,
            enabled = enabled,
            hasHost = true,
            text = text
        )
    ) {
        if (modifier === Modifier) {
            content()
        } else {
            Box(modifier = modifier) {
                content()
            }
        }
        return
    }
    val haptics = LocalHapticFeedback.current
    val ownerId = remember { Any() }
    val boundsRef = remember { BoundsRef() }
    val latestText by rememberUpdatedState(text)

    val hoverInteractionSource = remember { MutableInteractionSource() }
    val isInteractionHovered by hoverInteractionSource.collectIsHoveredAsState()
    var isPointerHovered by remember { mutableStateOf(false) }
    var isAnyMousePressed by remember { mutableStateOf(false) }

    var isTouchDown by remember { mutableStateOf(false) }
    var touchSessionId by remember { mutableIntStateOf(0) }
    var touchTriggeredSession by remember { mutableIntStateOf(-1) }

    val effectiveHovered = (isInteractionHovered || isPointerHovered) && !isTouchDown && !isAnyMousePressed

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

    // Mouse / trackpad / stylus hover lifecycle (with 0ms warm handoff across adjacent controls)
    LaunchedEffect(effectiveHovered, enabled, hostState) {
        if (hostState == null || !enabled || !effectiveHovered) {
            if (!isTouchDown) {
                hostState?.hide(
                    ownerId,
                    allowWarmHandoff = true,
                    preserveIfReleasedTouch = true
                )
            }
        } else {
            val waitMs = hostState.resolveHoverDelayMs(delayMillis)
            if (waitMs > 0L) {
                delay(waitMs)
            }
            val resolved = latestText
            if (resolved.isNotBlank()) {
                hostState.show(ownerId, resolved, boundsRef.rect, isTouchPreview = false)
            }
        }
    }

    // Stationary finger long-press tooltip preview on touchscreens
    LaunchedEffect(touchSessionId, isTouchDown, enabled, enableTouchLongPress, hostState) {
        if (hostState == null || !enabled || !enableTouchLongPress || !isTouchDown) {
            if (!effectiveHovered && !isTouchDown) {
                hostState?.hide(
                    ownerId,
                    allowWarmHandoff = true,
                    preserveIfReleasedTouch = true
                )
            }
        } else {
            val activeSession = touchSessionId
            delay(TooltipTimingPolicy.DEFAULT_TOUCH_LONG_PRESS_MS)
            val resolved = latestText
            if (isTouchDown && touchSessionId == activeSession && resolved.isNotBlank()) {
                touchTriggeredSession = activeSession
                runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                hostState.show(ownerId, resolved, boundsRef.rect, isTouchPreview = true)
                delay(TooltipTimingPolicy.DEFAULT_TOUCH_MAX_VISIBLE_MS)
                if (isTouchDown && touchSessionId == activeSession) {
                    hostState.hide(ownerId, allowWarmHandoff = false)
                }
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
            .hoverable(interactionSource = hoverInteractionSource, enabled = enabled)
            .pointerInput(enabled, enableTouchLongPress, hostState != null) {
                if (!enabled || hostState == null) return@pointerInput
                val touchSlopPx = viewConfiguration.touchSlop
                var touchOrigin = Offset.Zero
                var touchExceededSlop = false
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val changes = event.changes
                        val primary = changes.firstOrNull()
                        val pointerType = primary?.type
                        val anyPressed = changes.any { it.pressed }
                        val anyPreviousPressed = changes.any { it.previousPressed }

                        if (TooltipTimingPolicy.isUnpressedHoverEvent(
                                eventType = event.type,
                                hasChanges = changes.isNotEmpty(),
                                anyPressed = anyPressed,
                                anyPreviousPressed = anyPreviousPressed
                            )
                        ) {
                            isAnyMousePressed = false
                            if (!isPointerHovered) {
                                isPointerHovered = true
                            }
                            continue
                        }

                        if (event.type == PointerEventType.Exit) {
                            if (isPointerHovered) {
                                isPointerHovered = false
                            }
                            isAnyMousePressed = false
                            if (!isTouchDown) {
                                hostState.hide(
                                    ownerId,
                                    allowWarmHandoff = true,
                                    preserveIfReleasedTouch = true
                                )
                            }
                            continue
                        }

                        if (event.type == PointerEventType.Scroll) {
                            isPointerHovered = false
                            isTouchDown = false
                            touchTriggeredSession = -1
                            hostState.hide(ownerId, allowWarmHandoff = false)
                            continue
                        }

                        val isDirectTouchGesture =
                            pointerType != PointerType.Mouse && (anyPressed || anyPreviousPressed)

                        if (isDirectTouchGesture) {
                            if (!enableTouchLongPress) {
                                continue
                            }
                            val pressedChanges = changes.filter { it.pressed }
                            when {
                                pressedChanges.size == 1 && primary != null && !primary.previousPressed -> {
                                    hostState.dismissReleasedTouchPreviews(exceptOwnerId = ownerId)
                                    touchOrigin = primary.position
                                    touchExceededSlop = false
                                    touchTriggeredSession = -1
                                    touchSessionId += 1
                                    isTouchDown = true
                                }
                                pressedChanges.size == 1 && primary != null && isTouchDown -> {
                                    if ((primary.position - touchOrigin).getDistance() > touchSlopPx) {
                                        touchExceededSlop = true
                                        isTouchDown = false
                                        touchTriggeredSession = -1
                                        hostState.hide(ownerId, allowWarmHandoff = false)
                                    }
                                }
                                pressedChanges.isEmpty() -> {
                                    val shouldConsumeReleaseForTooltip =
                                        isTouchDown &&
                                            !touchExceededSlop &&
                                            touchTriggeredSession == touchSessionId
                                    isTouchDown = false
                                    touchTriggeredSession = -1
                                    if (shouldConsumeReleaseForTooltip) {
                                        // Prevent IconButton / clickable from firing onClick after long-press tooltip
                                        changes.forEach { change ->
                                            if (!change.pressed && change.previousPressed) {
                                                change.consume()
                                            }
                                        }
                                        hostState.releaseTouchPreview(ownerId)
                                    } else {
                                        hostState.hide(ownerId, allowWarmHandoff = false)
                                    }
                                }
                                pressedChanges.size > 1 -> {
                                    touchExceededSlop = true
                                    isTouchDown = false
                                    touchTriggeredSession = -1
                                    hostState.hide(ownerId, allowWarmHandoff = false)
                                }
                            }
                        } else {
                            // Mouse button click press/release
                            isAnyMousePressed = anyPressed
                            if (anyPressed) {
                                isPointerHovered = false
                                hostState.hide(ownerId, allowWarmHandoff = false)
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
