package com.pv.thingsbook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pv.thingsbook.R
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.ui.theme.ThingsTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class SmartViewType(
    val titleRes: Int,
    val icon: ImageVector
) {
    INBOX(R.string.view_inbox, Icons.Outlined.Inbox),
    TODAY(R.string.view_today, Icons.Outlined.WbSunny),
    UPCOMING(R.string.view_upcoming, Icons.Outlined.CalendarMonth),
    ANYTIME(R.string.view_anytime, Icons.Outlined.Explore),
    SOMEDAY(R.string.view_someday, Icons.Outlined.Cloud),
    LOGBOOK(R.string.view_logbook, Icons.Outlined.History),
    TRASH(R.string.view_trash, Icons.Outlined.DeleteOutline)
}

@Composable
fun SmartViewIcon(
    viewType: SmartViewType,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val colors = ThingsTheme.colors
    val tint = if (isSelected) colors.thingsBlue else colors.secondaryText

    Icon(
        imageVector = viewType.icon,
        contentDescription = stringResource(viewType.titleRes),
        tint = tint,
        modifier = modifier.size(22.dp)
    )
}

@Composable
fun ThingsMilestoneDiamondIcon(
    filled: Boolean,
    tint: Color,
    size: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = 45f }
    ) {
        val side = this.size.minDimension * 0.72f
        val insetX = (this.size.width - side) / 2f
        val insetY = (this.size.height - side) / 2f
        val cornerPx = 2.2.dp.toPx().coerceAtMost(side * 0.28f)
        if (filled) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(insetX, insetY),
                size = Size(side, side),
                cornerRadius = CornerRadius(cornerPx, cornerPx)
            )
        } else {
            val strokePx = 1.65.dp.toPx()
            drawRoundRect(
                color = tint,
                topLeft = Offset(insetX + strokePx / 2f, insetY + strokePx / 2f),
                size = Size((side - strokePx).coerceAtLeast(1f), (side - strokePx).coerceAtLeast(1f)),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokePx)
            )
        }
    }
}

@Composable
fun ThingsInProgressIcon(
    active: Boolean,
    tint: Color,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val strokeWidth = (size.value * 0.115f).coerceAtLeast(1.5f).dp
    Canvas(modifier = modifier.size(size)) {
        val dim = this.size.minDimension
        val strokePx = strokeWidth.toPx().coerceAtMost(dim * 0.25f)
        val outerDiameter = (dim - strokePx).coerceAtLeast(1f)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        if (active) {
            drawCircle(
                color = tint.copy(alpha = (tint.alpha * 0.14f).coerceIn(0f, 1f)),
                radius = outerDiameter / 2f,
                center = center
            )
        }
        drawCircle(
            color = tint,
            radius = outerDiameter / 2f,
            center = center,
            style = Stroke(width = strokePx)
        )
        val arcDiameter = (dim - strokePx * 1.15f).coerceAtLeast(1f)
        drawArc(
            color = tint,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(
                (this.size.width - arcDiameter) / 2f,
                (this.size.height - arcDiameter) / 2f
            ),
            size = Size(arcDiameter, arcDiameter)
        )
    }
}

@Composable
fun ThisEveningHeaderBadge(
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Bedtime,
            contentDescription = stringResource(R.string.view_this_evening),
            tint = colors.eveningMoonIndigo,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(R.string.view_this_evening),
            style = MaterialTheme.typography.titleMedium,
            color = colors.primaryText
        )
    }
}

@Composable
fun ThingsTagPill(
    title: String,
    modifier: Modifier = Modifier,
    tooltipText: String? = null,
    isGhost: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = ThingsTheme.colors
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val interactiveHover = isHovered && onClick != null
    val shape = RoundedCornerShape(50)
    val ghostFg = colors.secondaryText.copy(alpha = 0.78f)

    val resolvedBg = when {
        containerColor != null -> containerColor
        interactiveHover -> colors.thingsBlueSoft
        isGhost -> Color.Transparent
        else -> colors.tagPillBg
    }
    val resolvedFg = when {
        contentColor != null -> contentColor
        interactiveHover -> colors.thingsBlue
        isGhost -> ghostFg
        else -> colors.tagPillText
    }
    val resolvedBorder = when {
        borderColor != null -> borderColor
        interactiveHover -> colors.thingsBlue.copy(alpha = 0.42f)
        isGhost -> Color.Transparent
        else -> Color.Transparent
    }
    val resolvedFontWeight = when {
        contentColor != null -> FontWeight.Bold
        isGhost && !interactiveHover -> FontWeight.Medium
        else -> FontWeight.SemiBold
    }

    ThingsHoverTooltip(
        text = tooltipText ?: title,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(resolvedBg)
                .border(
                    width = 0.75.dp,
                    color = resolvedBorder,
                    shape = shape
                )
                .hoverable(interactionSource = hoverSource, enabled = onClick != null)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 2.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = resolvedFontWeight
                ),
                color = resolvedFg
            )
        }
    }
}

@Composable
fun ThingsDeadlineBadge(
    deadlineEpochMillis: Long,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
    onClick: (() -> Unit)? = null
) {
    val colors = ThingsTheme.colors
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val deadlineDate = runCatching {
        Instant.ofEpochMilli(deadlineEpochMillis).atZone(zone).toLocalDate()
    }.getOrDefault(today)
    val daysDiff = runCatching {
        ChronoUnit.DAYS.between(today, deadlineDate).coerceIn(-9999L, 9999L).toInt()
    }.getOrDefault(0)

    val isUrgentOrOverdue = daysDiff <= 2
    val textColor = if (isUrgentOrOverdue) colors.deadlineUrgentRed else colors.secondaryText
    val bgColor = if (isUrgentOrOverdue) colors.deadlineSoftBg else colors.tagPillBg
    val borderColor = if (isUrgentOrOverdue) {
        colors.deadlineUrgentRed.copy(alpha = 0.35f)
    } else {
        colors.hairlineDivider
    }
    val shape = RoundedCornerShape(50)

    val label = when {
        daysDiff == 0 -> stringResource(R.string.deadline_today)
        daysDiff > 0 -> stringResource(R.string.deadline_days_left, daysDiff)
        else -> stringResource(R.string.deadline_days_overdue, -daysDiff)
    }

    ThingsHoverTooltip(
        text = "${stringResource(R.string.action_deadline)}: $label",
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(bgColor)
                .border(width = 0.75.dp, color = borderColor, shape = shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Flag,
                contentDescription = stringResource(R.string.action_deadline),
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = textColor
            )
        }
    }
}

@Composable
fun ThingsWhenBadge(
    scheduleState: ScheduleState,
    whenDateEpochMillis: Long?,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
    onClick: (() -> Unit)? = null
) {
    val colors = ThingsTheme.colors
    val zone = ZoneId.systemDefault()
    val formatter = remember(locale) {
        runCatching { DateTimeFormatter.ofPattern("EEE, d MMM", locale) }
            .getOrDefault(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    val (icon, tint, label) = when (scheduleState) {
        ScheduleState.TODAY -> Triple(
            Icons.Outlined.WbSunny,
            colors.todayStarYellow,
            stringResource(R.string.view_today)
        )
        ScheduleState.THIS_EVENING -> Triple(
            Icons.Outlined.Bedtime,
            colors.eveningMoonIndigo,
            stringResource(R.string.view_this_evening)
        )
        ScheduleState.UPCOMING -> {
            val today = LocalDate.now(zone)
            val targetDate = whenDateEpochMillis?.let { millis ->
                runCatching {
                    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                }.getOrNull()
            }
            val formattedDate = when {
                targetDate == today.plusDays(1) -> stringResource(R.string.date_tomorrow)
                targetDate != null -> runCatching { targetDate.format(formatter) }.getOrNull()
                    ?: stringResource(R.string.view_upcoming)
                else -> stringResource(R.string.view_upcoming)
            }
            Triple(Icons.Outlined.CalendarMonth, colors.upcomingCoralRed, formattedDate)
        }
        ScheduleState.SOMEDAY -> Triple(
            Icons.Outlined.Cloud,
            colors.somedayWarmAmber,
            stringResource(R.string.view_someday)
        )
        ScheduleState.ANYTIME -> Triple(
            Icons.Outlined.Explore,
            colors.anytimeTeal,
            stringResource(R.string.view_anytime)
        )
        ScheduleState.INBOX -> Triple(
            Icons.Outlined.Inbox,
            colors.inboxBlue,
            stringResource(R.string.action_when)
        )
    }

    val shape = RoundedCornerShape(50)
    val bgColor = when (scheduleState) {
        ScheduleState.SOMEDAY -> colors.somedayWarmAmber.copy(alpha = 0.16f)
        else -> colors.tagPillBg
    }
    val borderColor = when (scheduleState) {
        ScheduleState.SOMEDAY -> colors.somedayWarmAmber.copy(alpha = 0.48f)
        ScheduleState.UPCOMING -> colors.upcomingCoralRed.copy(alpha = 0.38f)
        else -> colors.hairlineDivider
    }
    val textColor = when (scheduleState) {
        ScheduleState.SOMEDAY -> colors.somedayWarmAmber
        else -> colors.primaryText
    }

    ThingsHoverTooltip(
        text = "${stringResource(R.string.action_when)}: $label",
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(bgColor)
                .border(width = 0.75.dp, color = borderColor, shape = shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (scheduleState == ScheduleState.SOMEDAY || scheduleState == ScheduleState.UPCOMING) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Medium
                    }
                ),
                color = textColor
            )
        }
    }
}

@Composable
fun ThingsReminderBadge(
    reminderEpochMillis: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = ThingsTheme.colors
    val zone = ZoneId.systemDefault()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT) }
    val formattedTime = runCatching {
        Instant.ofEpochMilli(reminderEpochMillis)
            .atZone(zone)
            .toLocalTime()
            .format(timeFormatter)
    }.getOrDefault("--:--")

    ThingsHoverTooltip(
        text = "${stringResource(R.string.action_reminder)}: $formattedTime",
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.thingsBlueSoft)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "🔔 $formattedTime",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.thingsBlue
            )
        }
    }
}

