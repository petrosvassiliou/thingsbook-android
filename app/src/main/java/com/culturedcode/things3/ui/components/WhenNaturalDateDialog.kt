package com.culturedcode.things3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.parser.MultilingualDateParser
import com.culturedcode.things3.ui.theme.ThingsTheme
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Interactive Things 3 "When?" popover dialog with live 5-language Natural Language Date Parsing
 * (`EN`, `DE`, `IT`, `FR`, `ES`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhenNaturalDateDialog(
    currentScheduleState: ScheduleState,
    currentWhenDate: Long?,
    locale: Locale = Locale.getDefault(),
    onApplySchedule: (ScheduleState, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    var rawInput by remember { mutableStateOf("") }
    val liveParsed = remember(rawInput, locale) {
        MultilingualDateParser.parse(rawInput, locale)
    }

    val zone = ZoneId.systemDefault()
    val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    val tomorrowEpoch = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 8.dp
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
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.action_when),
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

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.when_natural_input_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.tertiaryText
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (liveParsed != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onApplySchedule(liveParsed.scheduleState, liveParsed.whenDateEpochMillis)
                                onDismiss()
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Recognized Date Match Banner
                if (liveParsed != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.thingsBlueSoft)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onApplySchedule(liveParsed.scheduleState, liveParsed.whenDateEpochMillis)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = liveParsed.formattedPreview,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.thingsBlue
                            )
                        }

                        ThingsWhenBadge(
                            scheduleState = liveParsed.scheduleState,
                            whenDateEpochMillis = liveParsed.whenDateEpochMillis,
                            locale = locale
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Quick Preset Grid (Today, This Evening, Tomorrow, Anytime, Someday)
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
                            onApplySchedule(ScheduleState.TODAY, todayEpoch)
                            onDismiss()
                        }
                    )
                    ThingsWhenBadge(
                        scheduleState = ScheduleState.THIS_EVENING,
                        whenDateEpochMillis = todayEpoch,
                        locale = locale,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onApplySchedule(ScheduleState.THIS_EVENING, todayEpoch)
                            onDismiss()
                        }
                    )
                    ThingsWhenBadge(
                        scheduleState = ScheduleState.UPCOMING,
                        whenDateEpochMillis = tomorrowEpoch,
                        locale = locale,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onApplySchedule(ScheduleState.UPCOMING, tomorrowEpoch)
                            onDismiss()
                        }
                    )
                    ThingsWhenBadge(
                        scheduleState = ScheduleState.ANYTIME,
                        whenDateEpochMillis = null,
                        locale = locale,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onApplySchedule(ScheduleState.ANYTIME, null)
                            onDismiss()
                        }
                    )
                    ThingsWhenBadge(
                        scheduleState = ScheduleState.SOMEDAY,
                        whenDateEpochMillis = null,
                        locale = locale,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onApplySchedule(ScheduleState.SOMEDAY, null)
                            onDismiss()
                        }
                    )
                }

                if (currentWhenDate != null || currentScheduleState != ScheduleState.ANYTIME) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onApplySchedule(ScheduleState.ANYTIME, null)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.tagPillBg),
                        shape = RoundedCornerShape(10.dp),
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
