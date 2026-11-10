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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.RecurrenceMode
import com.culturedcode.things3.domain.model.RecurrenceRule
import com.culturedcode.things3.domain.model.RecurrenceUnit
import com.culturedcode.things3.ui.theme.ThingsTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ThingsRepeatBadge(
    rule: RecurrenceRule,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = ThingsTheme.colors
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.thingsBlueSoft)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Repeat,
            contentDescription = stringResource(R.string.action_repeat),
            tint = colors.thingsBlue,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = rule.formatShortLabel(locale),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.thingsBlue
        )
    }
}

/**
 * Things-for-Mac style Repeating To-Do Popover Dialog:
 * Supports both Things 3 recurrence modes:
 * 1. "Regelmäßig / Nach Zeitplan" (FIXED_SCHEDULE): Daily, Weekly (with multi-weekday picker Mon-Sun),
 *    Monthly (with day of month or Last Day), Yearly.
 * 2. "Nach Erledigung" (AFTER_COMPLETION): Spawns the next copy N days/weeks/months/years after completion.
 */
@Composable
fun ThingsRepeatDialog(
    currentRule: RecurrenceRule?,
    locale: Locale = Locale.getDefault(),
    onApplyRecurrence: (RecurrenceRule?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()

    var mode by remember(currentRule) {
        mutableStateOf(currentRule?.mode ?: RecurrenceMode.FIXED_SCHEDULE)
    }
    var interval by remember(currentRule) {
        mutableIntStateOf((currentRule?.interval ?: 1).coerceIn(1, 99))
    }
    var unit by remember(currentRule) {
        mutableStateOf(currentRule?.unit ?: RecurrenceUnit.WEEKS)
    }
    var selectedDaysOfWeek by remember(currentRule) {
        mutableStateOf(
            currentRule?.daysOfWeek?.filter { it in 1..7 }?.takeIf { it.isNotEmpty() }?.toSet()
                ?: setOf(LocalDate.now(zone).dayOfWeek.value)
        )
    }
    var selectedDayOfMonth by remember(currentRule) {
        val rawDom = currentRule?.dayOfMonth ?: LocalDate.now(zone).dayOfMonth
        mutableStateOf(if (rawDom == -1) -1 else rawDom.coerceIn(1, 31))
    }
    var endDateEpochMillis by remember(currentRule) {
        mutableStateOf(currentRule?.endDateEpochMillis)
    }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val previewRule = RecurrenceRule(
        mode = mode,
        interval = interval,
        unit = unit,
        daysOfWeek = if (mode == RecurrenceMode.FIXED_SCHEDULE && unit == RecurrenceUnit.WEEKS) {
            selectedDaysOfWeek.sorted()
        } else {
            emptyList()
        },
        dayOfMonth = if (mode == RecurrenceMode.FIXED_SCHEDULE && unit == RecurrenceUnit.MONTHS) {
            selectedDayOfMonth
        } else {
            null
        },
        endDateEpochMillis = endDateEpochMillis
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 316.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Header
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
                            imageVector = Icons.Outlined.Repeat,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.repeat_dialog_title),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = colors.primaryText
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mode Switcher: "Regelmäßig (Zeitplan)" vs. "Nach Erledigung"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.tagPillBg)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isFixed = mode == RecurrenceMode.FIXED_SCHEDULE
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isFixed) colors.thingsBlue else Color.Transparent)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                mode = RecurrenceMode.FIXED_SCHEDULE
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.repeat_mode_fixed),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = if (isFixed) Color.White else colors.primaryText
                        )
                    }

                    val isAfter = mode == RecurrenceMode.AFTER_COMPLETION
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isAfter) colors.thingsBlue else Color.Transparent)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                mode = RecurrenceMode.AFTER_COMPLETION
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.repeat_mode_after),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = if (isAfter) Color.White else colors.primaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (mode == RecurrenceMode.FIXED_SCHEDULE) {
                        stringResource(R.string.repeat_mode_fixed_desc)
                    } else {
                        stringResource(R.string.repeat_mode_after_desc)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = colors.secondaryText
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Unit Selector Row: Tage, Wochen, Monate, Jahre
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val units = listOf(
                        RecurrenceUnit.DAYS to stringResource(R.string.repeat_unit_days),
                        RecurrenceUnit.WEEKS to stringResource(R.string.repeat_unit_weeks),
                        RecurrenceUnit.MONTHS to stringResource(R.string.repeat_unit_months),
                        RecurrenceUnit.YEARS to stringResource(R.string.repeat_unit_years)
                    )
                    units.forEach { (u, label) ->
                        val selected = unit == u
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (selected) colors.thingsBlueSoft else colors.tagPillBg)
                                .border(
                                    width = if (selected) 1.dp else 0.dp,
                                    color = if (selected) colors.thingsBlue else Color.Transparent,
                                    shape = RoundedCornerShape(7.dp)
                                )
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    unit = u
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = if (selected) colors.thingsBlue else colors.primaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Interval Stepper ("Alle X Tage / Wochen / Monate / Jahre")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.tagPillBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = previewRule.formatShortLabel(locale),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = colors.primaryText
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colors.cardSurface)
                                .clickable {
                                    if (interval > 1) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        interval -= 1
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Remove,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = interval.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.thingsBlue
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colors.cardSurface)
                                .clickable {
                                    if (interval < 99) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        interval += 1
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Weekday Picker for FIXED_SCHEDULE + WEEKS (Mo..So)
                if (mode == RecurrenceMode.FIXED_SCHEDULE && unit == RecurrenceUnit.WEEKS) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.repeat_weekdays_label),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.secondaryText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val weekdays = (1..7).map { dow ->
                            val shortName = runCatching {
                                DayOfWeek.of(dow)
                                    .getDisplayName(TextStyle.SHORT, locale)
                                    .removeSuffix(".")
                                    .take(2)
                            }.getOrDefault(dow.toString())
                            dow to shortName
                        }
                        weekdays.forEach { (dow, label) ->
                            val isSelected = dow in selectedDaysOfWeek
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) colors.thingsBlue else colors.tagPillBg)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedDaysOfWeek = if (isSelected && selectedDaysOfWeek.size > 1) {
                                            selectedDaysOfWeek - dow
                                        } else {
                                            selectedDaysOfWeek + dow
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) Color.White else colors.primaryText
                                )
                            }
                        }
                    }
                }

                // Day-of-Month Picker for FIXED_SCHEDULE + MONTHS
                if (mode == RecurrenceMode.FIXED_SCHEDULE && unit == RecurrenceUnit.MONTHS) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.repeat_month_day_label),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.secondaryText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val lastDayText = stringResource(R.string.repeat_last_day)
                        val monthDays = listOf(1, 5, 10, 15, 20, 25, -1)
                        monthDays.forEach { dom ->
                            val isSelected = selectedDayOfMonth == dom
                            val label = if (dom == -1) lastDayText else "$dom."
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (isSelected) colors.thingsBlue else colors.tagPillBg)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedDayOfMonth = dom
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) Color.White else colors.primaryText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Optional End Date Row ("Endet: Nie" vs "Am Datum")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.repeat_ends_label),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.secondaryText
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val neverEnds = endDateEpochMillis == null
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (neverEnds) colors.thingsBlue else colors.tagPillBg)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    endDateEpochMillis = null
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.repeat_ends_never),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = if (neverEnds) Color.White else colors.primaryText
                            )
                        }

                        val endDateText = endDateEpochMillis?.let { epoch ->
                            runCatching {
                                Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
                                    .format(DateTimeFormatter.ofPattern("d. MMM yyyy", locale))
                            }.getOrNull()
                        } ?: stringResource(R.string.repeat_ends_pick_date)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!neverEnds) colors.thingsBlue else colors.tagPillBg)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showEndDatePicker = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = endDateText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = if (!neverEnds) Color.White else colors.primaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.hairlineDivider, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Preview & Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentRule != null) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.tagPillBg)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onApplyRecurrence(null)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.repeat_off_btn),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = colors.deadlineUrgentRed
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.thingsBlue)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onApplyRecurrence(previewRule)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${stringResource(R.string.action_save)} (${previewRule.formatShortLabel(locale)})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            ),
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    if (showEndDatePicker) {
        DeadlineDatePickerDialog(
            currentDeadline = endDateEpochMillis,
            locale = locale,
            onApplyDeadline = { chosen ->
                endDateEpochMillis = chosen
            },
            onDismiss = { showEndDatePicker = false }
        )
    }
}

