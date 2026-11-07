package com.culturedcode.things3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NotificationsActive
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.ui.theme.ThingsTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Interactive Thingsbook "When?" Calendar & Task Reminder Picker Dialog.
 * Replaces the old text input box with a real visual month calendar, quick GTD presets,
 * and an integrated task-specific notification time picker (`reminderTime`).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val tomorrowEpoch = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val initialSelectedDate = remember(currentWhenDate) {
        currentWhenDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today
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

    // Task-specific reminder time state (optional)
    val initialTime = remember(currentReminderTime) {
        currentReminderTime?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
    }
    var reminderEnabled by remember { mutableStateOf(initialTime != null) }
    var reminderHour by remember { mutableStateOf(initialTime?.hour ?: 9) }
    var reminderMinute by remember { mutableStateOf(initialTime?.minute ?: 0) }
    var showCustomTimePicker by remember { mutableStateOf(false) }

    fun computeReminderEpoch(targetDate: LocalDate?): Long? {
        if (!reminderEnabled || targetDate == null) return null
        return targetDate.atTime(LocalTime.of(reminderHour, reminderMinute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    fun applySelection(state: ScheduleState, dateEpoch: Long?, reminderEpoch: Long?) {
        if (onApplyScheduleWithReminder != null) {
            onApplyScheduleWithReminder(state, dateEpoch, reminderEpoch)
        } else {
            onApplySchedule(state, dateEpoch)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.when_pick_date_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Preset Row (Today, This Evening, Tomorrow, Anytime, Someday)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThingsWhenBadge(
                        scheduleState = ScheduleState.TODAY,
                        whenDateEpochMillis = todayEpoch,
                        locale = locale,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedDate = today
                            selectedScheduleState = ScheduleState.TODAY
                            visibleMonth = YearMonth.from(today)
                            if (!reminderEnabled) {
                                applySelection(ScheduleState.TODAY, todayEpoch, null)
                                onDismiss()
                            }
                        }
                    )
                    ThingsWhenBadge(
                        scheduleState = ScheduleState.THIS_EVENING,
                        whenDateEpochMillis = todayEpoch,
                        locale = locale,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedDate = today
                            selectedScheduleState = ScheduleState.THIS_EVENING
                            if (!reminderEnabled) {
                                applySelection(ScheduleState.THIS_EVENING, todayEpoch, null)
                                onDismiss()
                            }
                        }
                    )
                    ThingsWhenBadge(
                        scheduleState = ScheduleState.UPCOMING,
                        whenDateEpochMillis = tomorrowEpoch,
                        locale = locale,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val tmw = today.plusDays(1)
                            selectedDate = tmw
                            selectedScheduleState = ScheduleState.UPCOMING
                            visibleMonth = YearMonth.from(tmw)
                            if (!reminderEnabled) {
                                applySelection(ScheduleState.UPCOMING, tomorrowEpoch, null)
                                onDismiss()
                            }
                        }
                    )
                    ThingsWhenBadge(
                        scheduleState = ScheduleState.ANYTIME,
                        whenDateEpochMillis = null,
                        locale = locale,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            applySelection(ScheduleState.ANYTIME, null, null)
                            onDismiss()
                        }
                    )
                    ThingsWhenBadge(
                        scheduleState = ScheduleState.SOMEDAY,
                        whenDateEpochMillis = null,
                        locale = locale,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            applySelection(ScheduleState.SOMEDAY, null, null)
                            onDismiss()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Month Calendar Grid
                ThingsInteractiveMonthCalendar(
                    visibleMonth = visibleMonth,
                    selectedDate = selectedDate,
                    today = today,
                    locale = locale,
                    onPreviousMonth = { visibleMonth = visibleMonth.minusMonths(1) },
                    onNextMonth = { visibleMonth = visibleMonth.plusMonths(1) },
                    onSelectDate = { pickedDate ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedDate = pickedDate
                        selectedScheduleState = if (pickedDate <= today) {
                            ScheduleState.TODAY
                        } else {
                            ScheduleState.UPCOMING
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Task-Specific Reminder Time Picker Section
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
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = if (reminderEnabled) colors.thingsBlue else colors.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.when_task_reminder_title),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primaryText
                        )
                    }

                    if (reminderEnabled) {
                        Text(
                            text = String.format(Locale.ROOT, "%02d:%02d", reminderHour, reminderMinute),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.thingsBlue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.thingsBlueSoft)
                                .clickable { showCustomTimePicker = !showCustomTimePicker }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Reminder Time Chips
                val presetHours = listOf(8, 9, 12, 15, 18, 20)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // "None" chip
                    val noneSelected = !reminderEnabled
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (noneSelected) colors.thingsBlue else colors.tagPillBg)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                reminderEnabled = false
                                showCustomTimePicker = false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_clear),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (noneSelected) Color.White else colors.secondaryText
                        )
                    }

                    presetHours.forEach { hr ->
                        val isSelected = reminderEnabled && reminderHour == hr && reminderMinute == 0
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.thingsBlue else colors.tagPillBg)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    reminderEnabled = true
                                    reminderHour = hr
                                    reminderMinute = 0
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = String.format(Locale.ROOT, "%02d:00", hr),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) Color.White else colors.primaryText
                            )
                        }
                    }

                    // Custom Time Input toggle chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showCustomTimePicker) colors.thingsBlueSoft else colors.tagPillBg)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                reminderEnabled = true
                                showCustomTimePicker = !showCustomTimePicker
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccessTime,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "HH:MM",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.thingsBlue
                            )
                        }
                    }
                }

                if (showCustomTimePicker && reminderEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val timePickerState = rememberTimePickerState(
                        initialHour = reminderHour,
                        initialMinute = reminderMinute,
                        is24Hour = true
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TimeInput(state = timePickerState)
                    }
                    reminderHour = timePickerState.hour
                    reminderMinute = timePickerState.minute
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Apply Selected Date & Reminder Button
                val formattedSelectedDate = remember(selectedDate, locale) {
                    selectedDate.format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", locale))
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val targetEpoch = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val reminderSuffix = if (reminderEnabled) {
                        String.format(Locale.ROOT, " • 🔔 %02d:%02d", reminderHour, reminderMinute)
                    } else {
                        ""
                    }
                    Text(
                        text = "$formattedSelectedDate$reminderSuffix",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (currentWhenDate != null || currentReminderTime != null || currentScheduleState != ScheduleState.ANYTIME) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            applySelection(ScheduleState.ANYTIME, null, null)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.action_clear),
                            color = colors.secondaryText
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Calendar Date Picker for Task Deadlines (`Frist`).
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
        currentDeadline?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today.plusDays(3)
    }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = null,
                            tint = colors.deadlineUrgentRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.deadline_picker_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onApplyDeadline(null)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.deadlineSoftBg),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.deadline_clear_button),
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
        DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Month Navigation Header (< November 2026 >)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = null,
                    tint = colors.primaryText
                )
            }

            Text(
                text = visibleMonth.atDay(1).format(monthTitleFormatter)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.primaryText
            )

            IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.primaryText
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Monday-first Weekday Headers (Mo, Di, Mi, Do, Fr, Sa, So)
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
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val firstDayOfMonth = visibleMonth.atDay(1)
        val leadingEmptyCells = (firstDayOfMonth.dayOfWeek.value - 1) // Monday = 0 .. Sunday = 6
        val daysInMonth = visibleMonth.lengthOfMonth()
        val totalCells = leadingEmptyCells + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
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
                                .aspectRatio(1.15f)
                                .padding(2.dp)
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
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = when {
                                    isSelected -> Color.White
                                    isToday -> colors.thingsBlue
                                    else -> colors.primaryText
                                }
                            )
                        }
                    } else {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.15f)
                        )
                    }
                }
            }
        }
    }
}
