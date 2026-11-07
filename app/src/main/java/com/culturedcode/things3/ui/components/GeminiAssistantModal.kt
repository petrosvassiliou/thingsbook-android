package com.culturedcode.things3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.culturedcode.things3.R
import com.culturedcode.things3.data.ai.GeminiLogbookReviewResponse
import com.culturedcode.things3.ui.theme.ThingsTheme

/**
 * Calm Things 3 styled modal for:
 * 1. Brain Dump -> Structured GTD Tasks (in EN, DE, IT, FR, ES)
 * 2. AI Project Architect -> Generates Project + Headings + To-Dos (in EN, DE, IT, FR, ES)
 * 3. Optional Gemini API Key override (in addition to Firebase Vertex AI default)
 */
@Composable
fun GeminiAssistantModal(
    isProcessing: Boolean,
    currentApiKey: String,
    onUpdateApiKey: (String) -> Unit,
    onSubmitBrainDump: (String) -> Unit,
    onSubmitProjectArchitect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Brain Dump, 1 = Project Architect
    var brainDumpInput by remember { mutableStateOf("") }
    var projectGoalInput by remember { mutableStateOf("") }
    var apiKeyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var showApiKeyField by remember { mutableStateOf(false) }

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
                    .padding(20.dp)
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
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.gemini_hub_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showApiKeyField = !showApiKeyField },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = stringResource(R.string.gemini_api_key_label),
                                tint = if (apiKeyInput.isNotBlank()) colors.thingsBlue else colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Switcher Pills (Brain Dump vs Project Architect)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.tagPillBg)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        stringResource(R.string.gemini_brain_dump_title) to Icons.Outlined.Psychology,
                        stringResource(R.string.gemini_project_architect_title) to Icons.Outlined.RocketLaunch
                    )
                    tabs.forEachIndexed { idx, (label, icon) ->
                        val active = selectedTab == idx
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) colors.cardSurface else Color.Transparent)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTab = idx
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (active) colors.thingsBlue else colors.secondaryText,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (active) colors.primaryText else colors.secondaryText,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (showApiKeyField) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            onUpdateApiKey(it)
                        },
                        label = { Text(stringResource(R.string.gemini_api_key_label)) },
                        placeholder = { Text(stringResource(R.string.gemini_api_key_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = brainDumpInput,
                        onValueChange = { brainDumpInput = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.gemini_brain_dump_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.tertiaryText
                            )
                        },
                        minLines = 4,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (brainDumpInput.isNotBlank()) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSubmitBrainDump(brainDumpInput.trim())
                            }
                        },
                        enabled = !isProcessing && brainDumpInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.gemini_generating), color = Color.White)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.gemini_brain_dump_action), color = Color.White)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = projectGoalInput,
                        onValueChange = { projectGoalInput = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.gemini_project_architect_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.tertiaryText
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (projectGoalInput.isNotBlank()) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSubmitProjectArchitect(projectGoalInput.trim())
                            }
                        },
                        enabled = !isProcessing && projectGoalInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.gemini_generating), color = Color.White)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.RocketLaunch,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.gemini_project_architect_action), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Calm Gemini AI Logbook Reflection card displayed at the top of the Logbook view.
 */
@Composable
fun GeminiLogbookReflectionCard(
    review: GeminiLogbookReviewResponse?,
    isGenerating: Boolean,
    onGenerateReflection: () -> Unit,
    onAdoptSuggestedFocusTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = colors.cardSurface,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = colors.logbookGreen,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = stringResource(R.string.gemini_logbook_review_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primaryText
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.thingsBlueSoft)
                        .clickable(enabled = !isGenerating) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onGenerateReflection()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colors.thingsBlue
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.gemini_logbook_review_action),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.thingsBlue
                        )
                    }
                }
            }

            if (review != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = review.summaryParagraph,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText
                )

                if (review.suggestedFocusTasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    review.suggestedFocusTasks.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onAdoptSuggestedFocusTask(suggestion)
                                }
                                .padding(vertical = 5.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = colors.todayStarYellow,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primaryText,
                                modifier = Modifier.weight(1f)
                            )
                            ThingsTagPill(title = "+ " + stringResource(R.string.view_today))
                        }
                    }
                }
            }
        }
    }
}
