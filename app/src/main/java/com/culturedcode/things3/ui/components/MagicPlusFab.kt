package com.culturedcode.things3.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.culturedcode.things3.R
import com.culturedcode.things3.ui.theme.ThingsTheme
import kotlin.math.roundToInt

enum class MagicPlusDropMode {
    NONE,
    INSERT_TODO,
    INSERT_HEADING
}

enum class MagicPlusContextMode {
    SMART_VIEW,
    AREA_DETAIL,
    PROJECT_DETAIL,
    LOGBOOK
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
 * Subtle backdrop scrim rendered behind the expanded Magic Plus speed-dial menu so clicking
 * outside smoothly collapses the menu.
 */
@Composable
fun MagicPlusBackdropScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.14f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }
}

/**
 * The iconic Things 3 "Magic Plus" Draggable Floating Action Button + Context-Aware Expandable Menu:
 * - Clicking smoothly expands/collapses a spring-animated context menu offering:
 *   `Aufgabe`, `Projekt`, `Überschrift` (in Projects), and `Bereich` (when < max areas) depending on context!
 * - Dragging it across the list preserves 100% of the Things Magic Plus Drag & Drop behavior
 *   (`INSERT_TODO` row gap & `INSERT_HEADING` when dragged to the left inside a Project).
 */
@Composable
fun MagicPlusFab(
    itemCount: Int,
    contextMode: MagicPlusContextMode,
    canCreateArea: Boolean,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onCreateTodo: () -> Unit,
    onCreateProject: () -> Unit,
    onCreateHeading: () -> Unit,
    onCreateArea: () -> Unit,
    onDragStateChanged: (MagicPlusDragState) -> Unit,
    onDropCreateTodoAt: (Int) -> Unit,
    onDropCreateHeadingAt: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val allowHeadingDrop = contextMode == MagicPlusContextMode.PROJECT_DETAIL

    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var lastHapticSlot by remember { mutableIntStateOf(-1) }
    var lastDropMode by remember { mutableStateOf(MagicPlusDropMode.NONE) }

    val scale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.18f
            isMenuExpanded -> 1.05f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 420f),
        label = "magicPlusScale"
    )

    val iconRotation by animateFloatAsState(
        targetValue = if (isMenuExpanded && !isDragging) 135f else 0f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 420f),
        label = "magicPlusIconRotation"
    )

    val fabColor by animateColorAsState(
        targetValue = if (isMenuExpanded && !isDragging) Color(0xFF1E5FD9) else colors.thingsBlue,
        animationSpec = spring(stiffness = 450f),
        label = "magicPlusFabColor"
    )

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 18.dp else if (isMenuExpanded) 12.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "magicPlusElevation"
    )

    val rowHeightPx = with(density) { 58.dp.toPx() }.coerceAtLeast(1f)
    val leftEdgeThresholdPx = with(density) { (-210).dp.toPx() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Animated Context-Aware Speed-Dial Menu (fans open from bottom-right above the + button)
        AnimatedVisibility(
            visible = isMenuExpanded && !isDragging,
            enter = expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 430f)
            ) + scaleIn(
                initialScale = 0.76f,
                transformOrigin = TransformOrigin(0.92f, 1.0f),
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 430f)
            ) + fadeIn(animationSpec = spring(stiffness = 500f)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 500f)
            ) + scaleOut(
                targetScale = 0.80f,
                transformOrigin = TransformOrigin(0.92f, 1.0f),
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 500f)
            ) + fadeOut(animationSpec = spring(stiffness = 600f))
        ) {
            MagicPlusContextMenuCard(
                contextMode = contextMode,
                canCreateArea = canCreateArea,
                onSelectTodo = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onMenuExpandedChange(false)
                    onCreateTodo()
                },
                onSelectHeading = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onMenuExpandedChange(false)
                    onCreateHeading()
                },
                onSelectProject = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onMenuExpandedChange(false)
                    onCreateProject()
                },
                onSelectArea = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onMenuExpandedChange(false)
                    onCreateArea()
                }
            )
        }

        // Draggable + Clickable Magic Plus Circle Button with Mouse Hover Tooltip
        ThingsHoverTooltip(
            text = "Neu erstellen (Klicken für Menü • Ziehen zum Platzieren)",
            enabled = !isDragging && !isMenuExpanded
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = dragOffset.x.takeIf { it.isFinite() }?.roundToInt() ?: 0,
                            y = dragOffset.y.takeIf { it.isFinite() }?.roundToInt() ?: 0
                        )
                    }
                    .graphicsLayer {
                        val safeScale = if (scale.isFinite()) scale else 1f
                        scaleX = safeScale
                        scaleY = safeScale
                    }
                    .shadow(
                        elevation = elevation,
                        shape = CircleShape,
                        ambientColor = colors.thingsBlue.copy(alpha = 0.45f),
                        spotColor = colors.thingsBlue.copy(alpha = 0.55f)
                    )
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(fabColor)
                    .clickable(enabled = !isDragging) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMenuExpandedChange(!isMenuExpanded)
                    }
                    .pointerInput(itemCount, allowHeadingDrop) {
                        detectDragGestures(
                            onDragStart = {
                                onMenuExpandedChange(false)
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
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.entity_new_todo),
                    tint = Color.White,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            rotationZ = if (iconRotation.isFinite()) iconRotation else 0f
                        }
                )
            }
        }
    }
}

@Composable
private fun MagicPlusContextMenuCard(
    contextMode: MagicPlusContextMode,
    canCreateArea: Boolean,
    onSelectTodo: () -> Unit,
    onSelectHeading: () -> Unit,
    onSelectProject: () -> Unit,
    onSelectArea: () -> Unit
) {
    val colors = ThingsTheme.colors

    val todoSubtitle = when (contextMode) {
        MagicPlusContextMode.PROJECT_DETAIL -> "Im aktuellen Projekt anlegen"
        MagicPlusContextMode.AREA_DETAIL -> "Im aktuellen Bereich anlegen"
        MagicPlusContextMode.LOGBOOK -> "Im Eingang anlegen"
        MagicPlusContextMode.SMART_VIEW -> "In aktueller Ansicht anlegen"
    }

    val projectSubtitle = when (contextMode) {
        MagicPlusContextMode.AREA_DETAIL -> "Projekt in diesem Bereich"
        MagicPlusContextMode.PROJECT_DETAIL -> "Weiteres Projekt erstellen"
        else -> "Mit Schritten & Zieldatum"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.cardElevatedSurface,
        shadowElevation = 14.dp,
        tonalElevation = 6.dp,
        modifier = Modifier
            .widthIn(min = 236.dp, max = 280.dp)
            .border(
                width = 0.75.dp,
                color = colors.hairlineDivider,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 1. Aufgabe (Always available)
            MagicPlusOptionRow(
                icon = Icons.Outlined.CheckCircleOutline,
                iconTint = colors.thingsBlue,
                iconBg = colors.thingsBlueSoft,
                title = stringResource(R.string.entity_todo),
                subtitle = todoSubtitle,
                onClick = onSelectTodo
            )

            // 2. Überschrift (Context-specific: inside a Project!)
            if (contextMode == MagicPlusContextMode.PROJECT_DETAIL) {
                MagicPlusOptionRow(
                    icon = Icons.Outlined.Title,
                    iconTint = Color(0xFF5856D6),
                    iconBg = Color(0xFF5856D6).copy(alpha = 0.12f),
                    title = stringResource(R.string.entity_heading),
                    subtitle = "Abschnitt im Projekt einfügen",
                    onClick = onSelectHeading
                )
            }

            // 3. Projekt (Available in all views; pre-assigns Area when in Area/Project context)
            MagicPlusOptionRow(
                icon = Icons.Outlined.Folder,
                iconTint = colors.thingsBlue,
                iconBg = colors.thingsBlueSoft,
                title = stringResource(R.string.entity_project),
                subtitle = projectSubtitle,
                onClick = onSelectProject
            )

            // 4. Bereich (Context-specific: available outside Project detail or whenever < 8 Areas)
            if (contextMode != MagicPlusContextMode.PROJECT_DETAIL && canCreateArea) {
                MagicPlusOptionRow(
                    icon = Icons.Outlined.Inventory2,
                    iconTint = colors.anytimeTeal,
                    iconBg = colors.anytimeTeal.copy(alpha = 0.14f),
                    title = stringResource(R.string.entity_area),
                    subtitle = "Neuer Verantwortungsbereich",
                    onClick = onSelectArea
                )
            }
        }
    }
}

@Composable
private fun MagicPlusOptionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = ThingsTheme.colors
    var isHovered by remember { mutableStateOf(false) }

    val rowBg by animateColorAsState(
        targetValue = if (isHovered) colors.thingsBlueSoft.copy(alpha = 0.65f) else Color.Transparent,
        animationSpec = spring(stiffness = 600f),
        label = "magicPlusOptionHoverBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        when (ev.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                            else -> Unit
                        }
                    }
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.primaryText
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.secondaryText,
                maxLines = 1
            )
        }
    }
}
