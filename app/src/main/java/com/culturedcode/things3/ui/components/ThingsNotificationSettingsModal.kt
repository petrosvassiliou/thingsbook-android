package com.culturedcode.things3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.culturedcode.things3.R
import com.culturedcode.things3.data.notifications.DailyDigestConfig
import com.culturedcode.things3.ui.theme.ThingsTheme
import java.util.Locale

/**
 * Modal for configuring the Daily Active Tasks Digest Notification (fires every day at a chosen time
 * for all tasks active on that day) and testing notifications immediately.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThingsNotificationSettingsModal(
    config: DailyDigestConfig,
    onSaveDailyDigestConfig: (enabled: Boolean, hour: Int, minute: Int) -> Unit,
    onSendTestNotificationNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var selectedHour by remember(config) { mutableStateOf(config.hour) }
    var selectedMinute by remember(config) { mutableStateOf(config.minute) }
    var showCustomTimeInput by remember { mutableStateOf(false) }

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
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.notifications_title),
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

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Row for Daily Active Tasks Notification
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.notifications_daily_digest_title),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primaryText
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.notifications_daily_digest_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            enabled = it
                            onSaveDailyDigestConfig(it, selectedHour, selectedMinute)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.thingsBlue)
                    )
                }

                if (enabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.notifications_daily_time_label),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primaryText
                        )
                        Text(
                            text = String.format(Locale.ROOT, "%02d:%02d", selectedHour, selectedMinute),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.thingsBlue
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val presets = listOf(
                        7 to 0,
                        8 to 0,
                        8 to 30,
                        9 to 0,
                        12 to 0,
                        18 to 0
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { (hr, min) ->
                            val isSelected = selectedHour == hr && selectedMinute == min && !showCustomTimeInput
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSelected) colors.thingsBlue else colors.tagPillBg)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedHour = hr
                                        selectedMinute = min
                                        showCustomTimeInput = false
                                        onSaveDailyDigestConfig(enabled, hr, min)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.ROOT, "%02d:%02d", hr, min),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) Color.White else colors.primaryText
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (showCustomTimeInput) colors.thingsBlueSoft else colors.tagPillBg)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showCustomTimeInput = !showCustomTimeInput
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
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

                    if (showCustomTimeInput) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val timeState = rememberTimePickerState(
                            initialHour = selectedHour,
                            initialMinute = selectedMinute,
                            is24Hour = true
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TimeInput(state = timeState)
                        }
                        selectedHour = timeState.hour
                        selectedMinute = timeState.minute
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.notifications_task_specific_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Test Notification Button
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSaveDailyDigestConfig(enabled, selectedHour, selectedMinute)
                        onSendTestNotificationNow()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlueSoft),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.notifications_test_now),
                        color = colors.thingsBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSaveDailyDigestConfig(enabled, selectedHour, selectedMinute)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
