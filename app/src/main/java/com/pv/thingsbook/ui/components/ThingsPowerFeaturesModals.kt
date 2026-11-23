package com.pv.thingsbook.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pv.thingsbook.R
import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.ChecklistItem
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.formatDurationCompact
import com.pv.thingsbook.domain.parser.ParsedSmartSyntax
import com.pv.thingsbook.domain.parser.SmartSyntaxParser
import com.pv.thingsbook.ui.theme.ThingsTheme
import java.util.Locale

data class QuickShareDraft(
    val initialTitle: String,
    val initialNotes: String,
    val autoLaunchVoice: Boolean = false
)

data class FocusTimerUiState(
    val taskId: String,
    val taskTitle: String,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isPaused: Boolean = false,
    val sessionFocusedSeconds: Int = 0,
    val isBreakMode: Boolean = false
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartSyntaxPreviewChipsRow(
    parsed: ParsedSmartSyntax,
    showDetectedPrefix: Boolean = true,
    onApply: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!parsed.hasAnyMatch) return
    val colors = ThingsTheme.colors

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (showDetectedPrefix) {
            Text(
                text = "${stringResource(R.string.v32_smart_syntax_detected)}:",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryText,
                modifier = Modifier.padding(top = 4.dp, end = 2.dp)
            )
        }

        if (parsed.scheduleState != null) {
            val whenText = parsed.whenLabel ?: when (parsed.scheduleState) {
                ScheduleState.TODAY -> stringResource(R.string.view_today)
                ScheduleState.THIS_EVENING -> stringResource(R.string.view_this_evening)
                ScheduleState.UPCOMING -> stringResource(R.string.view_upcoming)
                ScheduleState.ANYTIME -> stringResource(R.string.view_anytime)
                ScheduleState.SOMEDAY -> stringResource(R.string.view_someday)
                ScheduleState.INBOX -> stringResource(R.string.view_inbox)
            }
            ThingsTagPill(
                title = "📅 $whenText",
                containerColor = colors.thingsBlueSoft,
                contentColor = colors.thingsBlue,
                borderColor = colors.thingsBlue.copy(alpha = 0.35f)
            )
        }

        if (parsed.deadline != null) {
            ThingsTagPill(
                title = "🏁 ${parsed.deadlineLabel.orEmpty()}",
                containerColor = colors.deadlineSoftBg,
                contentColor = colors.deadlineUrgentRed,
                borderColor = colors.deadlineUrgentRed.copy(alpha = 0.35f)
            )
        }

        if (parsed.durationMinutes != null) {
            val durFmt = formatDurationCompact(parsed.durationMinutes) ?: "${parsed.durationMinutes}m"
            ThingsTagPill(
                title = "⏱ $durFmt",
                containerColor = colors.thingsBlueSoft,
                contentColor = colors.thingsBlue,
                borderColor = colors.thingsBlue.copy(alpha = 0.35f)
            )
        }

        if (parsed.isMilestone) {
            ThingsTagPill(
                title = "◆ ${stringResource(R.string.v30_milestone_label)}",
                containerColor = colors.milestoneVioletSoft,
                contentColor = colors.milestoneViolet,
                borderColor = colors.milestoneViolet.copy(alpha = 0.45f)
            )
        }

        parsed.matchedProject?.let { proj ->
            ThingsTagPill(
                title = "📁 ${proj.title}",
                containerColor = colors.thingsBlueSoft,
                contentColor = colors.thingsBlue,
                borderColor = colors.thingsBlue.copy(alpha = 0.35f)
            )
        }

        parsed.matchedArea?.let { area ->
            ThingsTagPill(
                title = "📦 ${area.title}",
                containerColor = colors.thingsBlueSoft,
                contentColor = colors.thingsBlue,
                borderColor = colors.thingsBlue.copy(alpha = 0.35f)
            )
        }

        parsed.matchedTags.forEach { tag ->
            ThingsTagPill(
                title = "#${tag.title}"
            )
        }

        if (onApply != null) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onApply),
                shape = RoundedCornerShape(8.dp),
                color = colors.thingsBlue,
                border = BorderStroke(0.75.dp, colors.thingsBlue)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.5.dp)
                    )
                    Text(
                        text = stringResource(R.string.v32_smart_syntax_apply),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ThingsQuickShareModal(
    draft: QuickShareDraft,
    projects: List<Project>,
    areas: List<Area>,
    tags: List<Tag> = emptyList(),
    locale: Locale = Locale.getDefault(),
    isSmartParsingGemini: Boolean = false,
    onDismiss: () -> Unit,
    onSmartParseWithGemini: ((spokenDump: String, scheduleState: ScheduleState, projectId: String?, areaId: String?) -> Unit)? = null,
    onSave: (title: String, notes: String, scheduleState: ScheduleState, projectId: String?, areaId: String?) -> Unit
) {
    val context = LocalContext.current
    val colors = ThingsTheme.colors
    var title by remember(draft) { mutableStateOf(draft.initialTitle) }
    var notes by remember(draft) { mutableStateOf(draft.initialNotes) }
    var selectedSchedule by remember { mutableStateOf(ScheduleState.INBOX) }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var selectedAreaId by remember { mutableStateOf<String?>(null) }
    val voicePrompt = stringResource(R.string.voice_capture_prompt)
    val voiceNotAvail = stringResource(R.string.voice_not_available)

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                if (title.isBlank()) {
                    title = spoken
                } else {
                    notes = if (notes.isBlank()) spoken else "$notes\n$spoken"
                }
            }
        }
    }

    val triggerVoiceCapture: () -> Unit = {
        val ok = runCatching {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
            }
            speechLauncher.launch(intent)
            true
        }.getOrDefault(false)
        if (!ok) {
            Toast.makeText(context, voiceNotAvail, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(draft) {
        if (draft.autoLaunchVoice) {
            triggerVoiceCapture()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        ThingsTooltipHostBox {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(18.dp),
            color = colors.cardSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.quick_share_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ThingsHoverTooltip(text = stringResource(R.string.voice_capture_btn)) {
                            IconButton(onClick = triggerVoiceCapture, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = stringResource(R.string.voice_capture_btn),
                                    tint = colors.thingsBlue
                                )
                            }
                        }
                        ThingsHoverTooltip(text = stringResource(R.string.action_cancel)) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    tint = colors.secondaryText
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.placeholder_todo_title)) },
                    singleLine = true,
                    trailingIcon = {
                        ThingsHoverTooltip(text = stringResource(R.string.voice_capture_btn)) {
                            IconButton(onClick = triggerVoiceCapture) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = stringResource(R.string.voice_capture_btn),
                                    tint = colors.thingsBlue
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                val parsedQuickShareSyntax = remember(title, tags, projects, areas, locale) {
                    SmartSyntaxParser.parse(
                        rawTitle = title,
                        tags = tags,
                        projects = projects,
                        areas = areas,
                        locale = locale
                    )
                }
                if (parsedQuickShareSyntax.hasAnyMatch) {
                    SmartSyntaxPreviewChipsRow(
                        parsed = parsedQuickShareSyntax,
                        showDetectedPrefix = true
                    )
                } else {
                    Text(
                        text = stringResource(R.string.v32_smart_syntax_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.placeholder_notes)) },
                    minLines = 3,
                    maxLines = 6,
                    visualTransformation = rememberThingsMarkdownVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                DetectedNotesLinksRow(notes = notes)

                Text(
                    text = stringResource(R.string.quick_share_destination_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.secondaryText
                )

                val scheduleOptions = listOf(
                    Triple(ScheduleState.INBOX, stringResource(R.string.view_inbox), Icons.Outlined.Inbox),
                    Triple(ScheduleState.TODAY, stringResource(R.string.view_today), Icons.Outlined.WbSunny),
                    Triple(ScheduleState.THIS_EVENING, stringResource(R.string.view_this_evening), Icons.Outlined.Bedtime),
                    Triple(ScheduleState.ANYTIME, stringResource(R.string.view_anytime), Icons.Outlined.Explore),
                    Triple(ScheduleState.SOMEDAY, stringResource(R.string.view_someday), Icons.Outlined.Cloud)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    scheduleOptions.forEach { (state, label, icon) ->
                        val isSelected = selectedSchedule == state
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) colors.thingsBlue.copy(alpha = 0.14f)
                                    else colors.tagPillBg
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) colors.thingsBlue else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedSchedule = state }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) colors.thingsBlue else colors.secondaryText,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isSelected) colors.thingsBlue else colors.primaryText
                            )
                        }
                    }
                }

                if (projects.isNotEmpty() || areas.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        projects.take(10).forEach { project ->
                            val isSelected = selectedProjectId == project.id
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) colors.thingsBlue.copy(alpha = 0.14f)
                                        else colors.tagPillBg
                                    )
                                    .clickable {
                                        if (isSelected) {
                                            selectedProjectId = null
                                        } else {
                                            selectedProjectId = project.id
                                            selectedAreaId = project.areaId
                                        }
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ProjectProgressRing(progress = project.progress, size = 12.dp)
                                Text(
                                    text = project.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) colors.thingsBlue else colors.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        areas.forEach { area ->
                            val isSelected = selectedAreaId == area.id && selectedProjectId == null
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) colors.anytimeTeal.copy(alpha = 0.15f)
                                        else colors.tagPillBg
                                    )
                                    .clickable {
                                        if (isSelected) {
                                            selectedAreaId = null
                                        } else {
                                            selectedAreaId = area.id
                                            selectedProjectId = null
                                        }
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = area.title,
                                    tint = colors.anytimeTeal,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = area.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) colors.anytimeTeal else colors.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (onSmartParseWithGemini != null && (title.isNotBlank() || notes.isNotBlank())) {
                    OutlinedButton(
                        onClick = {
                            val combined = listOf(title.trim(), notes.trim())
                                .filter { it.isNotEmpty() }
                                .joinToString("\n")
                            if (combined.isNotEmpty()) {
                                onSmartParseWithGemini(
                                    combined,
                                    selectedSchedule,
                                    selectedProjectId,
                                    selectedAreaId
                                )
                            }
                        },
                        enabled = !isSmartParsingGemini,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSmartParsingGemini) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.thingsBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = stringResource(R.string.voice_smart_parse_gemini),
                            color = colors.thingsBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel), color = colors.secondaryText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanTitle = title.trim().ifEmpty {
                                notes.lineSequence().firstOrNull()?.trim()?.take(80).orEmpty()
                            }
                            if (cleanTitle.isNotEmpty()) {
                                onSave(cleanTitle, notes.trim(), selectedSchedule, selectedProjectId, selectedAreaId)
                            }
                        },
                        enabled = !isSmartParsingGemini && (title.isNotBlank() || notes.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue)
                    ) {
                        Text(stringResource(R.string.action_save), color = Color.White)
                    }
                }
            }
        }
        }
    }
}

@Composable
fun ThingsFocusTimerBanner(
    timerState: FocusTimerUiState,
    onTogglePause: () -> Unit,
    onAddFiveMinutes: () -> Unit,
    onCompleteTask: () -> Unit,
    onStopTimer: () -> Unit,
    onClickTask: () -> Unit,
    onOpenZenMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val mins = (timerState.remainingSeconds.coerceAtLeast(0)) / 60
    val secs = (timerState.remainingSeconds.coerceAtLeast(0)) % 60
    val formattedTime = remember(mins, secs) {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
    val progress = if (timerState.totalSeconds > 0) {
        1f - (timerState.remainingSeconds.toFloat() / timerState.totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val bannerAccent = if (timerState.isBreakMode) colors.logbookGreen else colors.thingsBlue
    val activeModeLabel = stringResource(
        if (timerState.isBreakMode) R.string.v32_zen_break_active else R.string.focus_timer_active_label
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bannerAccent.copy(alpha = 0.12f))
            .border(1.dp, bannerAccent.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClickTask)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (timerState.isBreakMode) Icons.Outlined.Coffee else Icons.Outlined.Timer,
                    contentDescription = activeModeLabel,
                    tint = bannerAccent,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeModeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = bannerAccent
                    )
                    Text(
                        text = timerState.taskTitle.ifBlank { stringResource(R.string.entity_todo) },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = bannerAccent,
                    modifier = Modifier.padding(end = 4.dp)
                )

                if (onOpenZenMode != null) {
                    val zenTooltip = stringResource(R.string.v32_zen_expand_tooltip)
                    ThingsHoverTooltip(text = zenTooltip) {
                        IconButton(onClick = onOpenZenMode, modifier = Modifier.size(30.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInFull,
                                contentDescription = zenTooltip,
                                tint = bannerAccent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                val pauseResumeTooltip = if (timerState.isPaused) {
                    stringResource(R.string.focus_timer_resume)
                } else {
                    stringResource(R.string.focus_timer_pause)
                }
                ThingsHoverTooltip(text = pauseResumeTooltip) {
                    IconButton(onClick = onTogglePause, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = if (timerState.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = pauseResumeTooltip,
                            tint = colors.primaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                ThingsHoverTooltip(text = stringResource(R.string.focus_timer_plus_5m)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.tagPillBg)
                            .clickable(onClick = onAddFiveMinutes)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.focus_timer_plus_5m),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primaryText
                        )
                    }
                }

                ThingsHoverTooltip(text = stringResource(R.string.action_complete)) {
                    IconButton(onClick = onCompleteTask, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.action_complete),
                            tint = colors.logbookGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                ThingsHoverTooltip(text = stringResource(R.string.focus_timer_stop)) {
                    IconButton(onClick = onStopTimer, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.focus_timer_stop),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = bannerAccent,
            trackColor = bannerAccent.copy(alpha = 0.18f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThingsZenFocusOverlay(
    timerState: FocusTimerUiState,
    activeTask: TaskItem?,
    onMinimize: () -> Unit,
    onTogglePause: () -> Unit,
    onAddFiveMinutes: () -> Unit,
    onStartShortBreak: () -> Unit,
    onResumeFocusFromBreak: () -> Unit,
    onCompleteTask: () -> Unit,
    onStopTimer: () -> Unit,
    onToggleChecklistItem: (itemId: String, completed: Boolean) -> Unit,
    onAddChecklistItem: (title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val mins = (timerState.remainingSeconds.coerceAtLeast(0)) / 60
    val secs = (timerState.remainingSeconds.coerceAtLeast(0)) % 60
    val formattedTime = remember(mins, secs) {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
    val progress = if (timerState.totalSeconds > 0) {
        1f - (timerState.remainingSeconds.toFloat() / timerState.totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val accentColor = if (timerState.isBreakMode) colors.logbookGreen else colors.thingsBlue
    val accentSoftBg = if (timerState.isBreakMode) {
        colors.logbookGreen.copy(alpha = 0.14f)
    } else {
        colors.thingsBlueSoft
    }

    val liveFocusedMinutes = (activeTask?.actualMinutes ?: 0) +
        (if (!timerState.isBreakMode) timerState.sessionFocusedSeconds / 60 else 0)
    val estimatedMinutes = activeTask?.durationMinutes?.takeIf { it > 0 }
    val focusedPillText = when {
        estimatedMinutes != null -> stringResource(
            R.string.v32_actual_vs_estimated,
            formatDurationCompact(liveFocusedMinutes.coerceAtLeast(0)) ?: "${liveFocusedMinutes}m",
            formatDurationCompact(estimatedMinutes) ?: "${estimatedMinutes}m"
        )
        liveFocusedMinutes > 0 -> stringResource(
            R.string.v32_actual_focused_short,
            formatDurationCompact(liveFocusedMinutes) ?: "${liveFocusedMinutes}m"
        )
        else -> null
    }

    var newSubtaskDraft by remember(timerState.taskId) { mutableStateOf("") }
    val activeChecklistItems = remember(activeTask?.checklistItems) {
        activeTask?.checklistItems
            ?.filter { !it.isDeleted }
            ?.sortedWith(compareBy<ChecklistItem> { it.sortOrder }.thenBy { it.createdAt })
            .orEmpty()
    }
    val submitNewSubtask = {
        val clean = newSubtaskDraft.trim()
        if (clean.isNotEmpty()) {
            onAddChecklistItem(clean)
            newSubtaskDraft = ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground.copy(alpha = 0.98f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header Row: Mode Badge + Minimize / Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentSoftBg,
                    border = BorderStroke(0.75.dp, accentColor.copy(alpha = 0.40f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (timerState.isBreakMode) Icons.Outlined.Coffee else Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(
                                if (timerState.isBreakMode) R.string.v32_zen_break_active else R.string.v32_zen_focus_title
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val minimizeLabel = stringResource(R.string.v32_zen_minimize)
                    ThingsHoverTooltip(text = "$minimizeLabel (Esc)") {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onMinimize),
                            shape = RoundedCornerShape(10.dp),
                            color = colors.tagPillBg,
                            border = BorderStroke(0.75.dp, colors.hairlineDivider)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloseFullscreen,
                                    contentDescription = minimizeLabel,
                                    tint = colors.primaryText,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = minimizeLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.primaryText
                                )
                            }
                        }
                    }

                    val stopLabel = stringResource(R.string.focus_timer_stop)
                    ThingsHoverTooltip(text = stopLabel) {
                        IconButton(onClick = onStopTimer, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stopLabel,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Scrollable Zen Centerpiece + Task Context
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Circular Progress Ring + Big MM:SS Countdown
                Box(
                    modifier = Modifier.size(188.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor.copy(alpha = 0.14f),
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round
                    )
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 40.sp,
                                lineHeight = 44.sp
                            ),
                            color = colors.primaryText
                        )
                        if (timerState.isPaused) {
                            Text(
                                text = stringResource(R.string.focus_timer_pause),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.secondaryText
                            )
                        }
                    }
                }

                // Active Task Title + Live Focused Pill
                Text(
                    text = (activeTask?.title ?: timerState.taskTitle)
                        .ifBlank { stringResource(R.string.entity_todo) },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.primaryText,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                if (focusedPillText != null) {
                    ThingsTagPill(
                        title = "⏱ $focusedPillText",
                        containerColor = accentSoftBg,
                        contentColor = accentColor,
                        borderColor = accentColor.copy(alpha = 0.35f)
                    )
                }

                // Controls Bar: Pause/Resume, +5m, 5m Break / Back to Focus, ✓ Complete
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pauseResumeLabel = stringResource(
                        if (timerState.isPaused) R.string.focus_timer_resume else R.string.focus_timer_pause
                    )
                    Button(
                        onClick = onTogglePause,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (timerState.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = pauseResumeLabel,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onAddFiveMinutes,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.75.dp, colors.hairlineDivider)
                    ) {
                        Text(
                            text = stringResource(R.string.focus_timer_plus_5m),
                            color = colors.primaryText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (timerState.isBreakMode) {
                        OutlinedButton(
                            onClick = onResumeFocusFromBreak,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.75.dp, colors.thingsBlue.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.v32_zen_back_to_focus),
                                color = colors.thingsBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onStartShortBreak,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.75.dp, colors.hairlineDivider)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Coffee,
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.v32_zen_short_break),
                                color = colors.primaryText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = onCompleteTask,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.logbookGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.action_complete),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Optional Scrollable Notes Card
                if (!activeTask?.notes.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = colors.cardSurface,
                        border = BorderStroke(0.75.dp, colors.hairlineDivider)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.placeholder_notes),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.secondaryText
                            )
                            Text(
                                text = activeTask?.notes.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primaryText
                            )
                            DetectedNotesLinksRow(notes = activeTask?.notes.orEmpty())
                        }
                    }
                }

                // Live Interactive Checklist Soft Bento Card
                if (activeTask != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = colors.cardSurface,
                        border = BorderStroke(0.75.dp, colors.hairlineDivider)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                        imageVector = Icons.Outlined.Checklist,
                                        contentDescription = null,
                                        tint = colors.thingsBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.action_checklist),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.primaryText
                                    )
                                }
                                if (activeChecklistItems.isNotEmpty()) {
                                    val doneCount = activeChecklistItems.count { it.isCompleted }
                                    Text(
                                        text = "$doneCount/${activeChecklistItems.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (doneCount == activeChecklistItems.size) colors.logbookGreen else colors.secondaryText
                                    )
                                }
                            }

                            activeChecklistItems.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onToggleChecklistItem(item.id, !item.isCompleted) }
                                        .padding(horizontal = 4.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(if (item.isCompleted) colors.thingsBlue else Color.Transparent)
                                            .border(
                                                width = 1.5.dp,
                                                color = if (item.isCompleted) colors.thingsBlue else colors.checkboxBorder,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.isCompleted) {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        color = if (item.isCompleted) colors.secondaryText else colors.primaryText,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (activeChecklistItems.isNotEmpty()) {
                                HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                            }

                            // Quick + Add Checklist Item Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newSubtaskDraft,
                                    onValueChange = { newSubtaskDraft = it.replace("\r", "").replace("\n", "") },
                                    placeholder = {
                                        Text(
                                            text = stringResource(R.string.placeholder_checklist),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submitNewSubtask() }),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .onPreviewKeyEvent { ev ->
                                            if (ev.type == KeyEventType.KeyDown &&
                                                (ev.key == Key.Enter || ev.key == Key.NumPadEnter)
                                            ) {
                                                submitNewSubtask()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                )
                                IconButton(
                                    onClick = { submitNewSubtask() },
                                    enabled = newSubtaskDraft.isNotBlank(),
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (newSubtaskDraft.isNotBlank()) colors.thingsBlue
                                            else colors.tagPillBg
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = stringResource(R.string.action_checklist),
                                        tint = if (newSubtaskDraft.isNotBlank()) Color.White else colors.secondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThingsUndoSnackbarBanner(
    message: String?,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .widthIn(max = 420.dp)
                .shadow(10.dp, RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            color = colors.primaryText
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.cardSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.action_undo),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.todayStarYellow,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onUndo()
                            onDismiss()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ThingsTrashItemCard(
    title: String,
    subtitle: String?,
    isProject: Boolean,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isProject) {
                ProjectProgressRing(progress = 0f, size = 18.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .border(1.5.dp, colors.checkboxBorder, RoundedCornerShape(4.dp))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank {
                        if (isProject) stringResource(R.string.entity_project)
                        else stringResource(R.string.entity_todo)
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isProject) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onRestore, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Outlined.RestoreFromTrash,
                    contentDescription = stringResource(R.string.trash_restore_btn),
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(onClick = onDeleteForever, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Outlined.DeleteForever,
                    contentDescription = stringResource(R.string.trash_delete_forever_btn),
                    tint = colors.deadlineUrgentRed,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
fun ThingsEmptyTrashConfirmDialog(
    onDismiss: () -> Unit,
    onConfirmEmptyTrash: () -> Unit
) {
    val colors = ThingsTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardSurface,
        title = {
            Text(
                text = stringResource(R.string.trash_empty_btn),
                color = colors.primaryText,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.trash_delete_forever_btn),
                color = colors.secondaryText
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmEmptyTrash()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.deadlineUrgentRed)
            ) {
                Text(stringResource(R.string.trash_empty_btn), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = colors.secondaryText)
            }
        }
    )
}

@Composable
fun ThingsBiometricLockOverlay(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val colors = ThingsTheme.colors
    val promptTitle = stringResource(R.string.app_lock_title)
    val promptSubtitle = stringResource(R.string.app_lock_subtitle)
    val cancelText = stringResource(R.string.action_cancel)

    val credentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onUnlocked()
        }
    }

    val triggerUnlock: () -> Unit = {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager == null || !keyguardManager.isDeviceSecure) {
            onUnlocked()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val promptBuilder = BiometricPrompt.Builder(context)
                    .setTitle(promptTitle)
                    .setSubtitle(promptSubtitle)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    promptBuilder.setDeviceCredentialAllowed(true)
                } else {
                    promptBuilder.setNegativeButton(
                        cancelText,
                        context.mainExecutor
                    ) { _, _ -> }
                }
                val prompt = promptBuilder.build()
                prompt.authenticate(
                    CancellationSignal(),
                    context.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            onUnlocked()
                        }
                    }
                )
            }.onFailure {
                @Suppress("DEPRECATION")
                val intent = keyguardManager.createConfirmDeviceCredentialIntent(promptTitle, promptSubtitle)
                if (intent != null) {
                    credentialLauncher.launch(intent)
                } else {
                    onUnlocked()
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(promptTitle, promptSubtitle)
            if (intent != null) {
                credentialLauncher.launch(intent)
            } else {
                onUnlocked()
            }
        }
    }

    LaunchedEffect(Unit) {
        triggerUnlock()
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ThingsTooltipHostBox(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.canvasBackground)
                    .clickable(enabled = true, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.thingsBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = promptTitle,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = promptTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText
                    )
                    Text(
                        text = promptSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText
                    )
                    OutlinedButton(onClick = triggerUnlock) {
                        Text(
                            text = stringResource(R.string.app_lock_unlock_btn),
                            color = colors.thingsBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
