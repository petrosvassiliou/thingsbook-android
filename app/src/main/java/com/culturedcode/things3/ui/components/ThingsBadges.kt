package com.culturedcode.things3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.ui.theme.ThingsTheme
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
    TODAY(R.string.view_today, Icons.Outlined.Star),
    UPCOMING(R.string.view_upcoming, Icons.Outlined.CalendarMonth),
    ANYTIME(R.string.view_anytime, Icons.Outlined.Layers),
    SOMEDAY(R.string.view_someday, Icons.Outlined.Archive),
    LOGBOOK(R.string.view_logbook, Icons.Outlined.CheckCircleOutline),
    TRASH(R.string.view_trash, Icons.Outlined.DeleteOutline)
}

@Composable
fun SmartViewIcon(
    viewType: SmartViewType,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val tint = when (viewType) {
        SmartViewType.INBOX -> colors.inboxBlue
        SmartViewType.TODAY -> colors.todayStarYellow
        SmartViewType.UPCOMING -> colors.upcomingCoralRed
        SmartViewType.ANYTIME -> colors.anytimeTeal
        SmartViewType.SOMEDAY -> colors.somedayWarmAmber
        SmartViewType.LOGBOOK -> colors.logbookGreen
        SmartViewType.TRASH -> colors.secondaryText
    }

    Icon(
        imageVector = viewType.icon,
        contentDescription = stringResource(viewType.titleRes),
        tint = tint,
        modifier = modifier.size(22.dp)
    )
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
    onClick: (() -> Unit)? = null
) {
    val colors = ThingsTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.tagPillBg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = colors.tagPillText
        )
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

    val label = when {
        daysDiff == 0 -> stringResource(R.string.deadline_today)
        daysDiff > 0 -> stringResource(R.string.deadline_days_left, daysDiff)
        else -> stringResource(R.string.deadline_days_overdue, -daysDiff)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp),
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
            Icons.Outlined.Star,
            colors.todayStarYellow,
            stringResource(R.string.view_today)
        )
        ScheduleState.THIS_EVENING -> Triple(
            Icons.Outlined.Bedtime,
            colors.eveningMoonIndigo,
            stringResource(R.string.view_this_evening)
        )
        ScheduleState.UPCOMING -> {
            val formattedDate = whenDateEpochMillis?.let { millis ->
                runCatching {
                    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(formatter)
                }.getOrNull()
            } ?: stringResource(R.string.view_upcoming)
            Triple(Icons.Outlined.CalendarMonth, colors.upcomingCoralRed, formattedDate)
        }
        ScheduleState.SOMEDAY -> Triple(
            Icons.Outlined.Archive,
            colors.somedayWarmAmber,
            stringResource(R.string.view_someday)
        )
        ScheduleState.ANYTIME -> Triple(
            Icons.Outlined.Layers,
            colors.anytimeTeal,
            stringResource(R.string.view_anytime)
        )
        ScheduleState.INBOX -> Triple(
            Icons.Outlined.Inbox,
            colors.inboxBlue,
            stringResource(R.string.action_when)
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.tagPillBg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 5.dp),
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
            style = MaterialTheme.typography.labelMedium,
            color = colors.primaryText
        )
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

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.thingsBlueSoft)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp),
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

