package com.culturedcode.things3.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.Area
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.ui.theme.ThingsTheme
import java.util.Locale

data class QuickShareDraft(
    val initialTitle: String,
    val initialNotes: String
)

data class FocusTimerUiState(
    val taskId: String,
    val taskTitle: String,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isPaused: Boolean = false
)

@Composable
fun ThingsQuickShareModal(
    draft: QuickShareDraft,
    projects: List<Project>,
    areas: List<Area>,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String, scheduleState: ScheduleState, projectId: String?, areaId: String?) -> Unit
) {
    val colors = ThingsTheme.colors
    var title by remember(draft) { mutableStateOf(draft.initialTitle) }
    var notes by remember(draft) { mutableStateOf(draft.initialNotes) }
    var selectedSchedule by remember { mutableStateOf(ScheduleState.INBOX) }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var selectedAreaId by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
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
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.placeholder_todo_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                    Triple(ScheduleState.TODAY, stringResource(R.string.view_today), Icons.Outlined.Star),
                    Triple(ScheduleState.THIS_EVENING, stringResource(R.string.view_this_evening), Icons.Outlined.Bedtime),
                    Triple(ScheduleState.ANYTIME, stringResource(R.string.view_anytime), Icons.Outlined.Layers),
                    Triple(ScheduleState.SOMEDAY, stringResource(R.string.view_someday), Icons.Outlined.Archive)
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
                        enabled = title.isNotBlank() || notes.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue)
                    ) {
                        Text(stringResource(R.string.action_save), color = Color.White)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.thingsBlue.copy(alpha = 0.12f))
            .border(1.dp, colors.thingsBlue.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
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
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = stringResource(R.string.focus_timer_active_label),
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.focus_timer_active_label),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.thingsBlue
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
                    color = colors.thingsBlue,
                    modifier = Modifier.padding(end = 4.dp)
                )

                IconButton(onClick = onTogglePause, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = if (timerState.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = if (timerState.isPaused) {
                            stringResource(R.string.focus_timer_resume)
                        } else {
                            stringResource(R.string.focus_timer_pause)
                        },
                        tint = colors.primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }

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

                IconButton(onClick = onCompleteTask, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = stringResource(R.string.action_complete),
                        tint = colors.logbookGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

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

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = colors.thingsBlue,
            trackColor = colors.thingsBlue.copy(alpha = 0.18f)
        )
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
