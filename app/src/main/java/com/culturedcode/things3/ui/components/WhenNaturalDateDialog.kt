package com.culturedcode.things3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.ui.theme.ThingsTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Compact Thingsbook "When?" Calendar & Reminder Popover Dialog.
 * Designed to fit cleanly on any screen without scrolling:
 * - 300dp max width
 * - 28dp compact day cells
 * - Single-row quick presets & single-row reminder chips
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhenNaturalDateDialog(
    currentScheduleState: ScheduleState,
    currentWhenDate: Long?,
    currentReminderTime: Long? = null,
    locale: Locale = Locale.getDefault(),
    onApplySchedule: (ScheduleState, Long?) -> Unit,
    onApplyScheduleWithReminder: ((ScheduleState, Long?, Long?) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayEpoch = today.atStartOfDay(zone).toInstant().toEpochMilli()

    val initialSelectedDate = remember(currentWhenDate) {
        currentWhenDate?.let { epoch ->
            runCatching { Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate() }.getOrNull()
        } ?: today
    }

    var selectedDate by remember { mutableStateOf(initialSelectedDate) }
    var selectedScheduleState by remember {
        mutableStateOf(
            if (currentScheduleState == ScheduleState.INBOX || currentScheduleState == ScheduleState.ANYTIME) {
                ScheduleState.TODAY
            } else {
                currentScheduleState
            }
        )
    }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(initialSelectedDate)) }

    val initialTime = remember(currentReminderTime) {
        currentReminderTime?.let { epoch ->
            runCatching { Instant.ofEpochMilli(epoch).atZone(zone).toLocalTime() }.getOrNull()
        }
    }
    var reminderEnabled by remember { mutableStateOf(initialTime != null) }
    var reminderHour by remember { mutableStateOf((initialTime?.hour ?: 9).coerceIn(0, 23)) }
    var reminderMinute by remember { mutableStateOf((initialTime?.minute ?: 0).coerceIn(0, 59)) }
    var showCustomTimePicker by remember { mutableStateOf(false) }

    fun computeReminderEpoch(targetDate: LocalDate?): Long? {
        return com.culturedcode.things3.domain.model.WhenScheduleResolver.computeReminderEpoch(
            targetDate = targetDate,
            zone = zone,
            reminderEnabled = reminderEnabled,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute
        )
    }

    fun applySelection(state: ScheduleState, dateEpoch: Long?, reminderEpoch: Long?) {
        if (onApplyScheduleWithReminder != null) {
            onApplyScheduleWithReminder(state, dateEpoch, reminderEpoch)
        } else {
            onApplySchedule(state, dateEpoch)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 308.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Compact Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.when_pick_date_title),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Compact Single-Line Quick Presets (Scrollable horizontally if needed)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactPresetChip(
                        icon = Icons.Outlined.Star,
                        tint = colors.todayStarYellow,
                        label = stringResource(R.string.view_today),
                        selected = currentScheduleState == ScheduleState.TODAY && selectedDate == today,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val resolved = com.culturedcode.things3.domain.model.WhenScheduleResolver.resolvePresetSelection(
                                preset = ScheduleState.TODAY,
                                todayDate = today,
                                zone = zone,
                                reminderEnabled = reminderEnabled,
                                reminderHour = reminderHour,
                                reminderMinute = reminderMinute
                            )
                            selectedDate = today
                            selectedScheduleState = resolved.scheduleState
                            visibleMonth = YearMonth.from(today)
                            applySelection(
                                resolved.scheduleState,
                                resolved.whenDateEpochMillis,
                                resolved.reminderEpochMillis
                            )
                            onDismiss()
                        }
                    )
                    CompactPresetChip(
                        icon = Icons.Outlined.Bedtime,
                        tint = colors.eveningMoonIndigo,
                        label = stringResource(R.string.view_this_evening),
                        selected = currentScheduleState == ScheduleState.THIS_EVENING,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val resolved = com.culturedcode.things3.domain.model.WhenScheduleResolver.resolvePresetSelection(
                                preset = ScheduleState.THIS_EVENING,
                                todayDate = today,
                                zone = zone,
                                reminderEnabled = reminderEnabled,
                                reminderHour = reminderHour,
                                reminderMinute = reminderMinute
                            )
                            selectedDate = today
                            selectedScheduleState = resolved.scheduleState
                            visibleMonth = YearMonth.from(today)
                            applySelection(
                                resolved.scheduleState,
                                resolved.whenDateEpochMillis,
                                resolved.reminderEpochMillis
                            )
                            onDismiss()
                        }
                    )
                    CompactPresetChip(
                        icon = Icons.Outlined.CalendarMonth,
                        tint = colors.upcomingCoralRed,
                        label = stringResource(R.string.date_tomorrow),
                        selected = currentScheduleState == ScheduleState.UPCOMING && selectedDate == today.plusDays(1),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val tmw = today.plusDays(1)
                            val resolved = com.culturedcode.things3.domain.model.WhenScheduleResolver.resolvePresetSelection(
                                preset = ScheduleState.UPCOMING,
                                todayDate = today,
                                zone = zone,
                                reminderEnabled = reminderEnabled,
                                reminderHour = reminderHour,
                                reminderMinute = reminderMinute
                            )
                            selectedDate = tmw
                            selectedScheduleState = resolved.scheduleState
                            visibleMonth = YearMonth.from(tmw)
                            applySelection(
                                resolved.scheduleState,
                                resolved.whenDateEpochMillis,
                                resolved.reminderEpochMillis
                            )
                            onDismiss()
                        }
                    )
                    CompactPresetChip(
                        icon = Icons.Outlined.Layers,
                        tint = colors.anytimeTeal,
                        label = stringResource(R.string.view_anytime),
                        selected = currentScheduleState == ScheduleState.ANYTIME,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val resolved = com.culturedcode.things3.domain.model.WhenScheduleResolver.resolvePresetSelection(
                                preset = ScheduleState.ANYTIME,
                                todayDate = today,
                                zone = zone,
                                reminderEnabled = reminderEnabled,
                                reminderHour = reminderHour,
                                reminderMinute = reminderMinute
                            )
                            applySelection(
                                resolved.scheduleState,
                                resolved.whenDateEpochMillis,
                                resolved.reminderEpochMillis
                            )
                            onDismiss()
                        }
                    )
                    CompactPresetChip(
                        icon = Icons.Outlined.Archive,
                        tint = colors.somedayWarmAmber,
                        label = stringResource(R.string.view_someday),
                        selected = currentScheduleState == ScheduleState.SOMEDAY,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val resolved = com.culturedcode.things3.domain.model.WhenScheduleResolver.resolvePresetSelection(
                                preset = ScheduleState.SOMEDAY,
                                todayDate = today,
                                zone = zone,
                                reminderEnabled = reminderEnabled,
                                reminderHour = reminderHour,
                                reminderMinute = reminderMinute
                            )
                            applySelection(
                                resolved.scheduleState,
                                resolved.whenDateEpochMillis,
                                resolved.reminderEpochMillis
                            )
                            onDismiss()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = colors.hairlineDivider, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(4.dp))

                // Compact Interactive Month Calendar Grid
                ThingsInteractiveMonthCalendar(
                    visibleMonth = visibleMonth,
                    selectedDate = selectedDate,
                    today = today,
                    locale = locale,
                    onPreviousMonth = {
                        runCatching { visibleMonth.minusMonths(1) }.onSuccess { visibleMonth = it }
                    },
                    onNextMonth = {
                        runCatching { visibleMonth.plusMonths(1) }.onSuccess { visibleMonth = it }
                    },
                    onSelectDate = { pickedDate ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedDate = pickedDate
                        selectedScheduleState = if (pickedDate <= today) {
                            ScheduleState.TODAY
                        } else {
                            ScheduleState.UPCOMING
                        }
                        if (!reminderEnabled) {
                            val epoch = runCatching {
                                pickedDate.atStartOfDay(zone).toInstant().toEpochMilli()
                            }.getOrNull()
                            if (epoch != null) {
                                applySelection(selectedScheduleState, epoch, null)
                                onDismiss()
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = colors.hairlineDivider, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))

                // Compact Single-Line Reminder Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        tint = if (reminderEnabled) colors.thingsBlue else colors.secondaryText,
                        modifier = Modifier.size(15.dp)
                    )

                    val noneSelected = !reminderEnabled
                    CompactTimeChip(
                        text = "—",
                        selected = noneSelected,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            reminderEnabled = false
                            showCustomTimePicker = false
                        }
                    )

                    listOf(8, 9, 12, 15, 18, 20).forEach { hr ->
                        val isSelected = reminderEnabled && reminderHour == hr && reminderMinute == 0 && !showCustomTimePicker
                        CompactTimeChip(
                            text = String.format(Locale.ROOT, "%02d:00", hr),
                            selected = isSelected,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                reminderEnabled = true
                                reminderHour = hr
                                reminderMinute = 0
                                showCustomTimePicker = false
                            }
                        )
                    }

                    CompactTimeChip(
                        text = if (showCustomTimePicker) {
                            String.format(Locale.ROOT, "%02d:%02d", reminderHour, reminderMinute)
                        } else {
                            "HH:MM"
                        },
                        selected = showCustomTimePicker,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            reminderEnabled = true
                            showCustomTimePicker = !showCustomTimePicker
                        }
                    )
                }

                if (showCustomTimePicker && reminderEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val timePickerState = rememberTimePickerState(
                        initialHour = reminderHour.coerceIn(0, 23),
                        initialMinute = reminderMinute.coerceIn(0, 59),
                        is24Hour = true
                    )
                    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
                        reminderHour = timePickerState.hour.coerceIn(0, 23)
                        reminderMinute = timePickerState.minute.coerceIn(0, 59)
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TimeInput(state = timePickerState)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Compact Single-Line Footer Actions (Clear on left, Apply on right)
                val formattedSelectedDate = remember(selectedDate, locale) {
                    runCatching { selectedDate.format(DateTimeFormatter.ofPattern("d. MMM", locale)) }
                        .getOrElse { selectedDate.toString() }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentWhenDate != null || currentReminderTime != null || currentScheduleState != ScheduleState.ANYTIME) {
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                applySelection(ScheduleState.ANYTIME, null, null)
                                onDismiss()
                            },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_clear),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.secondaryText
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val targetEpoch = runCatching {
                                selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
                            }.getOrElse { todayEpoch }
                            val finalState = when {
                                selectedScheduleState == ScheduleState.THIS_EVENING && selectedDate == today ->
                                    ScheduleState.THIS_EVENING
                                selectedDate <= today -> ScheduleState.TODAY
                                else -> ScheduleState.UPCOMING
                            }
                            val reminderEpoch = computeReminderEpoch(selectedDate)
                            applySelection(finalState, targetEpoch, reminderEpoch)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        val reminderSuffix = if (reminderEnabled) {
                            String.format(Locale.ROOT, " • 🔔 %02d:%02d", reminderHour, reminderMinute)
                        } else {
                            ""
                        }
                        val baseLabel = if (selectedScheduleState == ScheduleState.THIS_EVENING && selectedDate == today) {
                            "🌙 ${stringResource(R.string.view_this_evening)} ($formattedSelectedDate)"
                        } else {
                            formattedSelectedDate
                        }
                        Text(
                            text = "$baseLabel$reminderSuffix",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactPresetChip(
    icon: ImageVector,
    tint: Color,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val colors = ThingsTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) colors.thingsBlueSoft else colors.tagPillBg)
            .then(
                if (selected) {
                    Modifier.border(1.dp, colors.thingsBlue, RoundedCornerShape(7.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.thingsBlue else colors.primaryText
        )
    }
}

@Composable
private fun CompactTimeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ThingsTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) colors.thingsBlue else colors.tagPillBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else colors.primaryText
        )
    }
}

/**
 * Compact Calendar Date Picker for Task Deadlines (`Frist`).
 */
@Composable
fun DeadlineDatePickerDialog(
    currentDeadline: Long?,
    locale: Locale = Locale.getDefault(),
    onApplyDeadline: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    val initialDate = remember(currentDeadline) {
        currentDeadline?.let { epoch ->
            runCatching { Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate() }.getOrNull()
        } ?: today.plusDays(3)
    }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 308.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = null,
                            tint = colors.deadlineUrgentRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.deadline_picker_title),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                ThingsInteractiveMonthCalendar(
                    visibleMonth = visibleMonth,
                    selectedDate = selectedDate,
                    today = today,
                    locale = locale,
                    onPreviousMonth = { visibleMonth = visibleMonth.minusMonths(1) },
                    onNextMonth = { visibleMonth = visibleMonth.plusMonths(1) },
                    onSelectDate = { picked ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedDate = picked
                        val epoch = picked.atStartOfDay(zone).toInstant().toEpochMilli()
                        onApplyDeadline(epoch)
                        onDismiss()
                    }
                )

                if (currentDeadline != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onApplyDeadline(null)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.deadline_clear_button),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.deadlineUrgentRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThingsInteractiveMonthCalendar(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    locale: Locale,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    val colors = ThingsTheme.colors
    val monthTitleFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("MMM yyyy", locale)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Compact Month Navigation Header (< Nov. 2026 >)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = null,
                    tint = colors.primaryText,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = visibleMonth.atDay(1).format(monthTitleFormatter)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )

            IconButton(onClick = onNextMonth, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.primaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Compact Monday-first Weekday Headers (Mo, Di, Mi, Do, Fr, Sa, So)
        val daysOfWeek = remember {
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { dow ->
                Text(
                    text = dow.getDisplayName(TextStyle.SHORT, locale).take(2),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        val firstDayOfMonth = visibleMonth.atDay(1)
        val leadingEmptyCells = (firstDayOfMonth.dayOfWeek.value - 1)
        val daysInMonth = visibleMonth.lengthOfMonth()
        val totalCells = leadingEmptyCells + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - leadingEmptyCells + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = visibleMonth.atDay(dayNumber)
                        val isSelected = date == selectedDate
                        val isToday = date == today

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> colors.thingsBlue
                                            isToday -> colors.thingsBlueSoft
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (isToday && !isSelected) {
                                            Modifier.border(1.dp, colors.thingsBlue, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onSelectDate(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNumber.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> Color.White
                                        isToday -> colors.thingsBlue
                                        else -> colors.primaryText
                                    }
                                )
                            }
                        }
                    } else {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                        )
                    }
                }
            }
        }
    }
}
