package com.culturedcode.things3.ui.components

import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.Area
import com.culturedcode.things3.domain.model.CalendarEventItem
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.domain.model.ProjectOutlineImporter
import com.culturedcode.things3.domain.model.ProjectStatus
import com.culturedcode.things3.domain.model.ProjectTemplate
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.StalledProjectDetector
import com.culturedcode.things3.domain.model.StarterProjectTemplates
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.domain.model.TaskStatus
import com.culturedcode.things3.domain.model.TimeblockScheduleUtils
import com.culturedcode.things3.domain.model.TodayTimelineEntry
import com.culturedcode.things3.ui.theme.ThingsTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ThingsCelebrationSnackbarBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.cardElevatedSurface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .padding(horizontal = 16.dp)
                .border(1.dp, colors.logbookGreen.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.logbookGreen.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.logbookGreen,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primaryText,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// Pillar 1: Guided 4-Step GTD Weekly Review + Stalled Project Detection
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThingsWeeklyReviewModal(
    initialStep: Int = 0,
    inboxTasks: List<TaskItem>,
    todayTasks: List<TaskItem>,
    upcomingTasks: List<TaskItem>,
    anytimeTasks: List<TaskItem>,
    somedayTasks: List<TaskItem>,
    logbookTasks: List<TaskItem>,
    activeProjects: List<Project>,
    lastWeeklyReviewAt: Long?,
    locale: Locale = Locale.getDefault(),
    onScheduleTask: (TaskItem, ScheduleState, Long?) -> Unit,
    onCompleteTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onAddNextActionToProject: (projectId: String, title: String) -> Unit,
    onMarkProjectReviewed: (String) -> Unit,
    onMoveProjectToSomeday: (String) -> Unit,
    onActivateSomedayProject: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onCompleteWeeklyReview: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val todayDate = remember { LocalDate.now(zone) }
    val todayStartMillis = remember(todayDate) { todayDate.atStartOfDay(zone).toInstant().toEpochMilli() }
    val tomorrowStartMillis = remember(todayDate) { todayDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val threeDaysAheadEndMillis = remember(todayDate) { todayDate.plusDays(4).atStartOfDay(zone).toInstant().toEpochMilli() - 1L }

    var currentStep by remember(initialStep) { mutableIntStateOf(initialStep.coerceIn(0, 3)) }
    val nextActionDrafts = remember { mutableStateMapOf<String, String>() }

    val dueAndOverdueTasks = remember(todayTasks, upcomingTasks, anytimeTasks, inboxTasks, threeDaysAheadEndMillis) {
        (todayTasks + upcomingTasks + anytimeTasks + inboxTasks)
            .distinctBy { it.id }
            .filter { task ->
                !task.isDeleted && task.status == TaskStatus.OPEN && (
                    task.scheduleState == ScheduleState.TODAY ||
                        task.scheduleState == ScheduleState.THIS_EVENING ||
                        (task.deadline != null && task.deadline <= threeDaysAheadEndMillis)
                    )
            }
            .sortedWith(
                compareBy<TaskItem>(
                    { it.deadline ?: Long.MAX_VALUE },
                    { it.whenDate ?: Long.MAX_VALUE },
                    { it.sortOrder }
                )
            )
    }

    val stalledProjects = remember(activeProjects) {
        StalledProjectDetector.detectStalledProjects(activeProjects)
    }

    val somedayProjects = remember(activeProjects) {
        activeProjects.filter { !it.isDeleted && it.status == ProjectStatus.ACTIVE && it.scheduleState == ScheduleState.SOMEDAY }
    }

    val completedLast7DaysCount = remember(logbookTasks) {
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        logbookTasks.count { task ->
            task.status == TaskStatus.COMPLETED && (task.completedAt ?: task.updatedAt) >= sevenDaysAgo
        }
    }

    val stepTitles = listOf(
        stringResource(R.string.v2_review_step_inbox),
        stringResource(R.string.v2_review_step_today),
        stringResource(R.string.v2_review_step_projects),
        stringResource(R.string.v2_review_step_someday)
    )
    val stepBadges = listOf(
        inboxTasks.size,
        dueAndOverdueTasks.size,
        stalledProjects.size,
        somedayTasks.size + somedayProjects.size
    )
    val doneToastMsg = stringResource(R.string.v2_weekly_review_done_toast)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ThingsTooltipHostBox {
        Surface(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth(0.95f)
                .heightIn(max = 700.dp)
                .clip(RoundedCornerShape(22.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.thingsBlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = stringResource(R.string.v2_weekly_review_title),
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.v2_weekly_review_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.primaryText
                            )
                            val subtitleText = buildString {
                                append(stringResource(R.string.v2_weekly_review_subtitle))
                                if (lastWeeklyReviewAt != null && lastWeeklyReviewAt > 0L) {
                                    val formatted = runCatching {
                                        Instant.ofEpochMilli(lastWeeklyReviewAt)
                                            .atZone(zone)
                                            .toLocalDate()
                                            .format(DateTimeFormatter.ofPattern("d MMM", locale))
                                    }.getOrNull()
                                    if (!formatted.isNullOrBlank()) {
                                        append(" • ✓ ")
                                        append(formatted)
                                    }
                                }
                            }
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }

                    ThingsHoverTooltip(text = stringResource(R.string.action_cancel)) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
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

                // 4-Step Pill Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    stepTitles.forEachIndexed { index, label ->
                        val selected = currentStep == index
                        val count = stepBadges[index]
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) colors.thingsBlue else colors.tagPillBg,
                            modifier = Modifier.clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentStep = index
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) Color.White else colors.primaryText
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) Color.White.copy(alpha = 0.22f) else colors.cardSurface
                                ) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (selected) Color.White else colors.secondaryText,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.hairlineDivider)
                Spacer(modifier = Modifier.height(10.dp))

                // Step Content Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 240.dp, max = 440.dp)
                ) {
                    when (currentStep) {
                        0 -> {
                            if (inboxTasks.isEmpty()) {
                                ReviewEmptyStepPlaceholder(
                                    emoji = "✨",
                                    title = "Inbox Zero!",
                                    subtitle = stringResource(R.string.v2_review_step_inbox)
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 8.dp)
                                ) {
                                    items(inboxTasks, key = { it.id }) { task ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = colors.tagPillBg.copy(alpha = 0.55f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = task.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = colors.primaryText
                                                )
                                                if (task.notes.isNotBlank()) {
                                                    Text(
                                                        text = task.notes,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = colors.secondaryText,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    QuickReviewChip("⭐ ${stringResource(R.string.view_today)}", colors.thingsBlueSoft, colors.thingsBlue) {
                                                        onScheduleTask(task, ScheduleState.TODAY, todayStartMillis)
                                                    }
                                                    QuickReviewChip("🌙 ${stringResource(R.string.view_this_evening)}", colors.tagPillBg, colors.eveningMoonIndigo) {
                                                        onScheduleTask(task, ScheduleState.THIS_EVENING, todayStartMillis)
                                                    }
                                                    QuickReviewChip("📅 ${stringResource(R.string.date_tomorrow)}", colors.tagPillBg, colors.upcomingCoralRed) {
                                                        onScheduleTask(task, ScheduleState.UPCOMING, tomorrowStartMillis)
                                                    }
                                                    QuickReviewChip("📦 ${stringResource(R.string.view_someday)}", colors.tagPillBg, colors.somedayWarmAmber) {
                                                        onScheduleTask(task, ScheduleState.SOMEDAY, null)
                                                    }
                                                    QuickReviewChip("✓ ${stringResource(R.string.action_complete)}", colors.logbookGreen.copy(alpha = 0.16f), colors.logbookGreen) {
                                                        onCompleteTask(task.id)
                                                    }
                                                    QuickReviewChip("🗑 ${stringResource(R.string.action_delete)}", colors.deadlineSoftBg, colors.deadlineUrgentRed) {
                                                        onDeleteTask(task.id)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            if (dueAndOverdueTasks.isEmpty()) {
                                ReviewEmptyStepPlaceholder(
                                    emoji = "🎯",
                                    title = stringResource(R.string.v2_review_step_today),
                                    subtitle = "0"
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 8.dp)
                                ) {
                                    items(dueAndOverdueTasks, key = { it.id }) { task ->
                                        val isOverdue = task.deadline != null && task.deadline < todayStartMillis
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isOverdue) colors.deadlineSoftBg.copy(alpha = 0.7f) else colors.tagPillBg.copy(alpha = 0.55f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = task.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = colors.primaryText,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    task.deadline?.let { dl ->
                                                        ThingsDeadlineBadge(deadlineEpochMillis = dl, locale = locale)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    QuickReviewChip("⭐ ${stringResource(R.string.view_today)}", colors.thingsBlueSoft, colors.thingsBlue) {
                                                        onScheduleTask(task, ScheduleState.TODAY, todayStartMillis)
                                                    }
                                                    QuickReviewChip("📅 ${stringResource(R.string.date_tomorrow)}", colors.tagPillBg, colors.upcomingCoralRed) {
                                                        onScheduleTask(task, ScheduleState.UPCOMING, tomorrowStartMillis)
                                                    }
                                                    QuickReviewChip("📦 ${stringResource(R.string.view_someday)}", colors.tagPillBg, colors.somedayWarmAmber) {
                                                        onScheduleTask(task, ScheduleState.SOMEDAY, null)
                                                    }
                                                    QuickReviewChip("✓ ${stringResource(R.string.action_complete)}", colors.logbookGreen.copy(alpha = 0.16f), colors.logbookGreen) {
                                                        onCompleteTask(task.id)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            if (stalledProjects.isEmpty()) {
                                ReviewEmptyStepPlaceholder(
                                    emoji = "🚀",
                                    title = stringResource(R.string.v2_review_step_projects),
                                    subtitle = "✓"
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 8.dp)
                                ) {
                                    items(stalledProjects, key = { it.project.id }) { status ->
                                        val proj = status.project
                                        val draftText = nextActionDrafts[proj.id].orEmpty()
                                        Surface(
                                            shape = RoundedCornerShape(13.dp),
                                            color = colors.tagPillBg.copy(alpha = 0.62f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(
                                                    width = 1.dp,
                                                    color = if (status.hasNoNextAction) {
                                                        colors.somedayWarmAmber.copy(alpha = 0.45f)
                                                    } else {
                                                        colors.thingsBlue.copy(alpha = 0.35f)
                                                    },
                                                    shape = RoundedCornerShape(13.dp)
                                                )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                onDismiss()
                                                                onOpenProject(proj.id)
                                                            }
                                                    ) {
                                                        ProjectProgressRing(progress = proj.progress, size = 18.dp)
                                                        Text(
                                                            text = proj.title,
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = colors.primaryText,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (status.hasNoNextAction) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = colors.todayStarYellow.copy(alpha = 0.22f)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.WarningAmber,
                                                                    contentDescription = null,
                                                                    tint = colors.somedayWarmAmber,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Text(
                                                                    text = stringResource(R.string.v2_stalled_project_badge),
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                    color = colors.somedayWarmAmber
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = colors.thingsBlueSoft
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.Schedule,
                                                                    contentDescription = null,
                                                                    tint = colors.thingsBlue,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Text(
                                                                    text = stringResource(R.string.v2_requires_review_badge),
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                    color = colors.thingsBlue
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(5.dp))

                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (status.hasNoNextAction) {
                                                        Text(
                                                            text = "• ${stringResource(R.string.v2_stalled_no_next_action)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = colors.deadlineUrgentRed
                                                        )
                                                    }
                                                    if (status.isUnreviewed) {
                                                        Text(
                                                            text = "• ${stringResource(R.string.v2_stalled_unreviewed)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = colors.secondaryText
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                // Inline "Add next action..." field
                                                val submitNextAction = {
                                                    val clean = nextActionDrafts[proj.id]?.trim().orEmpty()
                                                    if (clean.isNotEmpty()) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onAddNextActionToProject(proj.id, clean)
                                                        nextActionDrafts[proj.id] = ""
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = draftText,
                                                        onValueChange = { nextActionDrafts[proj.id] = it },
                                                        placeholder = {
                                                            Text(
                                                                text = stringResource(R.string.v2_add_next_action),
                                                                style = MaterialTheme.typography.bodySmall
                                                            )
                                                        },
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                        keyboardActions = KeyboardActions(onDone = { submitNextAction() }),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(
                                                        onClick = submitNextAction,
                                                        enabled = draftText.isNotBlank(),
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(
                                                                if (draftText.isNotBlank()) colors.thingsBlue else colors.tagPillBg
                                                            )
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Add,
                                                            contentDescription = stringResource(R.string.v2_add_next_action),
                                                            tint = if (draftText.isNotBlank()) Color.White else colors.secondaryText,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    QuickReviewChip(
                                                        label = "✓ ${stringResource(R.string.v2_mark_reviewed)}",
                                                        bg = colors.thingsBlueSoft,
                                                        fg = colors.thingsBlue
                                                    ) {
                                                        onMarkProjectReviewed(proj.id)
                                                    }
                                                    QuickReviewChip(
                                                        label = "📦 ${stringResource(R.string.view_someday)}",
                                                        bg = colors.cardSurface,
                                                        fg = colors.somedayWarmAmber
                                                    ) {
                                                        onMoveProjectToSomeday(proj.id)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = colors.thingsBlueSoft,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(text = "🏆", fontSize = 26.sp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${stringResource(R.string.view_logbook)}: $completedLast7DaysCount ✓",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                    color = colors.thingsBlue
                                                )
                                                Text(
                                                    text = "${stringResource(R.string.view_inbox)}: ${inboxTasks.size} • ${stringResource(R.string.v2_stalled_project_badge)}: ${stalledProjects.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.secondaryText
                                                )
                                            }
                                        }
                                    }
                                }

                                if (somedayProjects.isNotEmpty()) {
                                    items(somedayProjects, key = { "sp_${it.id}" }) { proj ->
                                        Surface(
                                            shape = RoundedCornerShape(11.dp),
                                            color = colors.tagPillBg.copy(alpha = 0.55f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "📦 ${proj.title}",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = colors.primaryText,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                QuickReviewChip(
                                                    label = "🗂 ${stringResource(R.string.view_anytime)}",
                                                    bg = colors.thingsBlueSoft,
                                                    fg = colors.thingsBlue
                                                ) {
                                                    onActivateSomedayProject(proj.id)
                                                }
                                            }
                                        }
                                    }
                                }

                                items(somedayTasks, key = { "st_${it.id}" }) { task ->
                                    Surface(
                                        shape = RoundedCornerShape(11.dp),
                                        color = colors.tagPillBg.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = task.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.primaryText,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                QuickReviewChip(
                                                    label = "⭐ ${stringResource(R.string.view_today)}",
                                                    bg = colors.thingsBlueSoft,
                                                    fg = colors.thingsBlue
                                                ) {
                                                    onScheduleTask(task, ScheduleState.TODAY, todayStartMillis)
                                                }
                                                QuickReviewChip(
                                                    label = "🗂",
                                                    bg = colors.cardSurface,
                                                    fg = colors.anytimeTeal
                                                ) {
                                                    onScheduleTask(task, ScheduleState.ANYTIME, null)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.hairlineDivider)
                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Step Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentStep > 0) {
                        TextButton(onClick = { currentStep -= 1 }) {
                            Text("← ${stepTitles[currentStep - 1]}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (currentStep < 3) {
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentStep += 1
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("${stepTitles[currentStep + 1]} →", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCompleteWeeklyReview(doneToastMsg)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.logbookGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircleOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.v2_complete_weekly_review),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ReviewEmptyStepPlaceholder(
    emoji: String,
    title: String,
    subtitle: String
) {
    val colors = ThingsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = emoji, fontSize = 32.sp)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.primaryText
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = colors.secondaryText
        )
    }
}

@Composable
private fun QuickReviewChip(
    label: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier.clickable {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

// =============================================================================
// Pillar 2: Reusable Project Templates & Markdown / JSON Outline Importer
// =============================================================================

@Composable
fun ThingsTemplatesAndImportModal(
    initialTab: Int = 0,
    defaultAreaId: String? = null,
    areas: List<Area>,
    userTemplates: List<ProjectTemplate>,
    locale: Locale = Locale.getDefault(),
    onCreateFromTemplate: (template: ProjectTemplate, areaIdOverride: String?) -> Unit,
    onDeleteUserTemplate: (String) -> Unit,
    onImportOutline: (rawOutline: String, areaId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val resolvedLanguageCode = remember(configuration, locale) {
        val appCompatLang = runCatching {
            AppCompatDelegate.getApplicationLocales().get(0)?.language
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val configLang = configuration.locales[0]?.language?.takeIf { it.isNotBlank() }
        StarterProjectTemplates.normalizeLanguageCode(
            appCompatLang ?: configLang ?: locale.language
        )
    }
    val localizedStarterTemplates = remember(resolvedLanguageCode) {
        StarterProjectTemplates.getBuiltInTemplates(resolvedLanguageCode)
    }
    val fallbackProjectTitle = stringResource(R.string.entity_new_project)

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var selectedAreaId by remember(defaultAreaId) { mutableStateOf(defaultAreaId) }
    var rawOutline by remember(resolvedLanguageCode) {
        mutableStateOf(StarterProjectTemplates.getSampleOutlineMarkdown(resolvedLanguageCode))
    }

    val parsedPreview = remember(rawOutline, fallbackProjectTitle) {
        ProjectOutlineImporter.parseOutline(rawOutline, fallbackTitle = fallbackProjectTitle)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ThingsTooltipHostBox {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.95f)
                .heightIn(max = 700.dp)
                .clip(RoundedCornerShape(22.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.thingsBlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "📋", fontSize = 18.sp)
                        }
                        Text(
                            text = stringResource(R.string.v2_templates_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                    }
                    ThingsHoverTooltip(text = stringResource(R.string.action_cancel)) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
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

                // 2-Tab Segmented Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.tagPillBg)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        "📋 ${stringResource(R.string.v2_starter_templates)}",
                        "📝 ${stringResource(R.string.v2_import_markdown_title)}"
                    )
                    tabs.forEachIndexed { idx, title ->
                        val active = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) colors.cardSurface else Color.Transparent)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTab = idx
                                }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (active) colors.thingsBlue else colors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Optional Target Area Selector
                if (areas.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.entity_area)}:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.secondaryText
                        )
                        QuickReviewChip(
                            label = "—",
                            bg = if (selectedAreaId == null) colors.thingsBlueSoft else colors.tagPillBg,
                            fg = if (selectedAreaId == null) colors.thingsBlue else colors.secondaryText
                        ) {
                            selectedAreaId = null
                        }
                        areas.forEach { area ->
                            val isSel = selectedAreaId == area.id
                            QuickReviewChip(
                                label = "📦 ${area.title}",
                                bg = if (isSel) colors.thingsBlueSoft else colors.tagPillBg,
                                fg = if (isSel) colors.thingsBlue else colors.primaryText
                            ) {
                                selectedAreaId = area.id
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.hairlineDivider)
                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(min = 240.dp, max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        if (userTemplates.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.v2_my_templates),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.thingsBlue
                                )
                            }
                            items(userTemplates, key = { "usr_${it.id}" }) { tpl ->
                                TemplateGalleryCard(
                                    template = tpl,
                                    onCreate = { onCreateFromTemplate(tpl, selectedAreaId) },
                                    onDelete = { onDeleteUserTemplate(tpl.id) }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        item {
                            Text(
                                text = stringResource(R.string.v2_starter_templates),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.secondaryText
                            )
                        }
                        items(localizedStarterTemplates, key = { "${resolvedLanguageCode}_${it.id}" }) { tpl ->
                            TemplateGalleryCard(
                                template = tpl,
                                onCreate = { onCreateFromTemplate(tpl, selectedAreaId) },
                                onDelete = null
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = rawOutline,
                            onValueChange = { rawOutline = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.v2_import_markdown_hint),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            minLines = 7,
                            maxLines = 11,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.thingsBlueSoft.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "📁 ${parsedPreview.title}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.thingsBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${parsedPreview.headings.size} § • ${parsedPreview.totalTaskCount} ☑",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.primaryText
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (rawOutline.isNotBlank()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onImportOutline(rawOutline, selectedAreaId)
                                }
                            },
                            enabled = rawOutline.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.v2_import_create_project),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun TemplateGalleryCard(
    template: ProjectTemplate,
    onCreate: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = colors.tagPillBg.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText
                    )
                    if (template.notes.isNotBlank()) {
                        Text(
                            text = template.notes,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                if (onDelete != null) {
                    ThingsHoverTooltip(text = stringResource(R.string.action_delete)) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDelete()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = colors.deadlineUrgentRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${template.blueprint.headings.size} § • ${template.blueprint.totalTaskCount} ☑",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.secondaryText
                )

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCreate()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.v2_create_from_template),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

// =============================================================================
// Pillar 3: Interactive Timeblocking Picker + Today List | Timeline Toggle & View
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThingsTimeblockPickerDialog(
    initialStartMinutes: Int?,
    initialDurationMinutes: Int?,
    onSaveTimeblock: (startMinutes: Int?, durationMinutes: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var selectedStart by remember(initialStartMinutes) {
        mutableIntStateOf(initialStartMinutes ?: (9 * 60))
    }
    var selectedDuration by remember(initialDurationMinutes) {
        mutableIntStateOf(initialDurationMinutes ?: TimeblockScheduleUtils.DEFAULT_TIMEBLOCK_DURATION_MINUTES)
    }
    var customHmInput by remember(initialStartMinutes) {
        mutableStateOf(TimeblockScheduleUtils.formatMinutesAsHm(initialStartMinutes ?: (9 * 60)))
    }

    Dialog(onDismissRequest = onDismiss) {
        ThingsTooltipHostBox {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.cardSurface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.v2_timeblock_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.thingsBlueSoft
                    ) {
                        Text(
                            text = TimeblockScheduleUtils.formatTimeblockBadge(selectedStart, selectedDuration),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.thingsBlue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.v2_timeblock_start),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.secondaryText
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TimeblockScheduleUtils.START_TIME_PRESETS_MINUTES.forEach { presetMins ->
                        val label = TimeblockScheduleUtils.formatMinutesAsHm(presetMins)
                        val active = selectedStart == presetMins
                        QuickReviewChip(
                            label = label,
                            bg = if (active) colors.thingsBlue else colors.tagPillBg,
                            fg = if (active) Color.White else colors.primaryText
                        ) {
                            selectedStart = presetMins
                            customHmInput = label
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customHmInput,
                        onValueChange = { next ->
                            customHmInput = next
                            TimeblockScheduleUtils.parseHmToMinutes(next)?.let { parsed ->
                                selectedStart = parsed
                            }
                        },
                        singleLine = true,
                        label = { Text("HH:mm") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val initH = (selectedStart / 60).coerceIn(0, 23)
                            val initM = (selectedStart % 60).coerceIn(0, 59)
                            runCatching {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        val mins = h * 60 + m
                                        selectedStart = mins
                                        customHmInput = TimeblockScheduleUtils.formatMinutesAsHm(mins)
                                    },
                                    initH,
                                    initM,
                                    true
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.v2_timeblock_duration),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.secondaryText
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TimeblockScheduleUtils.DURATION_PRESETS_MINUTES.forEach { durMins ->
                        val active = selectedDuration == durMins
                        QuickReviewChip(
                            label = "${durMins}m",
                            bg = if (active) colors.thingsBlue else colors.tagPillBg,
                            fg = if (active) Color.White else colors.primaryText
                        ) {
                            selectedDuration = durMins
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.hairlineDivider)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (initialStartMinutes != null || initialDurationMinutes != null) {
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSaveTimeblock(null, null)
                                onDismiss()
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.v2_timeblock_clear),
                                color = colors.deadlineUrgentRed
                            )
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel), color = colors.secondaryText)
                        }
                    }

                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val finalStart = TimeblockScheduleUtils.parseHmToMinutes(customHmInput) ?: selectedStart
                            onSaveTimeblock(finalStart, selectedDuration)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                        shape = RoundedCornerShape(10.dp)
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
    }
}

@Composable
fun ThingsTodayModeSegmentedToggle(
    isTimelineMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.tagPillBg)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val listSelected = !isTimelineMode
        ThingsHoverTooltip(text = stringResource(R.string.v2_list_view)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (listSelected) colors.cardSurface else Color.Transparent)
                    .clickable {
                        if (isTimelineMode) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onModeChange(false)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📋 ${stringResource(R.string.v2_list_view)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (listSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (listSelected) colors.thingsBlue else colors.secondaryText
                )
            }
        }

        ThingsHoverTooltip(text = stringResource(R.string.v2_timeline_view)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isTimelineMode) colors.cardSurface else Color.Transparent)
                    .clickable {
                        if (!isTimelineMode) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onModeChange(true)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🗓️ ${stringResource(R.string.v2_timeline_view)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isTimelineMode) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isTimelineMode) colors.thingsBlue else colors.secondaryText
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThingsTodayTimelineSection(
    todayTasks: List<TaskItem>,
    todayCalendarEvents: List<CalendarEventItem>,
    onCompleteTask: (String) -> Unit,
    onUpdateTimeblock: (taskId: String, startMinutes: Int?, durationMinutes: Int?) -> Unit,
    onShiftTimeblock: (taskId: String, deltaMinutes: Int) -> Unit,
    onStartFocusTimer: (task: TaskItem) -> Unit,
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val schedule = remember(todayTasks, todayCalendarEvents) {
        TimeblockScheduleUtils.buildTodayTimeline(
            todayTasks = todayTasks,
            todayCalendarEvents = todayCalendarEvents
        )
    }
    var editingTimeblockTask by remember { mutableStateOf<TaskItem?>(null) }

    val suggestedNextSlot = remember(schedule.timedEntries) {
        TimeblockScheduleUtils.suggestNextFreeSlotMinutes(schedule.timedEntries)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (schedule.conflictCount > 0) {
            item(key = "timeline_conflict_banner") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.deadlineSoftBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colors.deadlineUrgentRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "⚠️ ${stringResource(R.string.v2_schedule_conflict)} (${schedule.conflictCount})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.deadlineUrgentRed
                        )
                    }
                }
            }
        }

        if (schedule.allDayEvents.isNotEmpty()) {
            items(schedule.allDayEvents, key = { "allday_${it.id}" }) { event ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.tagPillBg.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Event,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primaryText
                        )
                    }
                }
            }
        }

        items(schedule.timedEntries, key = { it.entryId }) { entry ->
            when (entry) {
                is TodayTimelineEntry.CalendarBlock -> {
                    val startLabel = TimeblockScheduleUtils.formatMinutesAsHm(entry.startMinutes)
                    val endLabel = TimeblockScheduleUtils.formatMinutesAsHm(entry.endMinutes)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = startLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.secondaryText,
                            modifier = Modifier.width(46.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (entry.hasConflict) colors.deadlineSoftBg else colors.tagPillBg.copy(alpha = 0.72f),
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = if (entry.hasConflict) colors.deadlineUrgentRed.copy(alpha = 0.55f) else colors.hairlineDivider,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Event,
                                    contentDescription = null,
                                    tint = colors.eveningMoonIndigo,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.event.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = "$startLabel–$endLabel" +
                                            (entry.event.calendarDisplayName.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.secondaryText
                                    )
                                }
                                if (entry.hasConflict) {
                                    Text(
                                        text = "⚠️",
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                is TodayTimelineEntry.TaskBlock -> {
                    val task = entry.task
                    val startLabel = TimeblockScheduleUtils.formatMinutesAsHm(entry.startMinutes)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = startLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (entry.hasConflict) colors.deadlineUrgentRed else colors.thingsBlue,
                            modifier = Modifier.width(46.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = if (entry.hasConflict) colors.deadlineSoftBg.copy(alpha = 0.78f) else colors.cardSurface,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = if (entry.hasConflict) {
                                        colors.deadlineUrgentRed.copy(alpha = 0.65f)
                                    } else {
                                        colors.thingsBlue.copy(alpha = 0.35f)
                                    },
                                    shape = RoundedCornerShape(13.dp)
                                )
                                .clickable { editingTimeblockTask = task }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ThingsCheckbox(
                                        status = task.status,
                                        onStatusChange = { onCompleteTask(task.id) },
                                        isDashedSomeday = task.scheduleState == ScheduleState.SOMEDAY
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onOpenTask(task.id) }
                                    ) {
                                        Text(
                                            text = task.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.primaryText,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = TimeblockScheduleUtils.formatTimeblockBadge(
                                                    entry.startMinutes,
                                                    entry.durationMinutes
                                                ),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.thingsBlue
                                            )
                                            if (entry.hasConflict) {
                                                Text(
                                                    text = "⚠️ ${stringResource(R.string.v2_schedule_conflict)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = colors.deadlineUrgentRed
                                                )
                                            }
                                        }
                                    }

                                    // 1-Tap Start Focus Timer (defaults to task.durationMinutes ?: 25)
                                    ThingsHoverTooltip(text = stringResource(R.string.action_focus_timer)) {
                                        IconButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onStartFocusTimer(task)
                                            },
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colors.thingsBlueSoft)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.PlayArrow,
                                                contentDescription = stringResource(R.string.action_focus_timer),
                                                tint = colors.thingsBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    QuickReviewChip(
                                        label = "+15m",
                                        bg = colors.tagPillBg,
                                        fg = colors.primaryText
                                    ) {
                                        onShiftTimeblock(task.id, 15)
                                    }
                                    QuickReviewChip(
                                        label = "⏱ ${entry.durationMinutes}m",
                                        bg = colors.thingsBlueSoft,
                                        fg = colors.thingsBlue
                                    ) {
                                        editingTimeblockTask = task
                                    }
                                    QuickReviewChip(
                                        label = "✕ ${stringResource(R.string.v2_timeblock_clear)}",
                                        bg = colors.tagPillBg,
                                        fg = colors.secondaryText
                                    ) {
                                        onUpdateTimeblock(task.id, null, null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Unscheduled Today Section
        if (schedule.unscheduledTasks.isNotEmpty()) {
            item(key = "unscheduled_today_header") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📌 ${stringResource(R.string.v2_unscheduled_today)} (${schedule.unscheduledTasks.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.secondaryText
                )
            }

            items(schedule.unscheduledTasks, key = { "unsched_${it.id}" }) { task ->
                val defaultDur = task.durationMinutes ?: TimeblockScheduleUtils.DEFAULT_TIMEBLOCK_DURATION_MINUTES
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.cardSurface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.hairlineDivider, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThingsCheckbox(
                                status = task.status,
                                onStatusChange = { onCompleteTask(task.id) },
                                isDashedSomeday = task.scheduleState == ScheduleState.SOMEDAY
                            )
                            Text(
                                text = task.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = colors.primaryText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenTask(task.id) }
                            )
                            ThingsHoverTooltip(text = stringResource(R.string.action_focus_timer)) {
                                IconButton(
                                    onClick = { onStartFocusTimer(task) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Timer,
                                        contentDescription = stringResource(R.string.action_focus_timer),
                                        tint = colors.secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val suggestedLabel = TimeblockScheduleUtils.formatMinutesAsHm(suggestedNextSlot)
                            QuickReviewChip(
                                label = "⚡ $suggestedLabel (${defaultDur}m)",
                                bg = colors.thingsBlueSoft,
                                fg = colors.thingsBlue
                            ) {
                                onUpdateTimeblock(task.id, suggestedNextSlot, defaultDur)
                            }
                            listOf(9 * 60, 13 * 60, 16 * 60)
                                .filter { it != suggestedNextSlot }
                                .forEach { slotMins ->
                                    val slotLabel = TimeblockScheduleUtils.formatMinutesAsHm(slotMins)
                                    QuickReviewChip(
                                        label = "+ $slotLabel",
                                        bg = colors.tagPillBg,
                                        fg = colors.primaryText
                                    ) {
                                        onUpdateTimeblock(task.id, slotMins, defaultDur)
                                    }
                                }
                            QuickReviewChip(
                                label = "⏱️ ${stringResource(R.string.v2_timeblock_title)}…",
                                bg = colors.tagPillBg,
                                fg = colors.thingsBlue
                            ) {
                                editingTimeblockTask = task
                            }
                        }
                    }
                }
            }
        }
    }

    editingTimeblockTask?.let { targetTask ->
        ThingsTimeblockPickerDialog(
            initialStartMinutes = targetTask.scheduledStartMinutes,
            initialDurationMinutes = targetTask.durationMinutes,
            onSaveTimeblock = { startMins, durMins ->
                onUpdateTimeblock(targetTask.id, startMins, durMins)
            },
            onDismiss = { editingTimeblockTask = null }
        )
    }
}
