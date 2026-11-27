package com.pv.thingsbook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pv.thingsbook.R
import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.EffortLevel
import com.pv.thingsbook.domain.model.SavedPerspective
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.formatDurationCompact
import com.pv.thingsbook.domain.model.wouldCreateDependencyCycle
import com.pv.thingsbook.ui.theme.ThingsTheme
import java.util.Locale
import java.util.UUID

/**
 * Lightweight "Waiting On…" Dependency Picker Dialog (`v30_blocked_by_dialog_title`).
 * Lets users pick one or more open tasks that must be completed before `task` becomes actionable,
 * while preventing circular dependencies via `wouldCreateDependencyCycle`.
 */
@Composable
fun TaskDependenciesPickerDialog(
    task: TaskItem,
    allOpenTasks: List<TaskItem>,
    onUpdateBlockedBy: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val listHeight = remember(screenHeightDp) {
        (screenHeightDp.dp * 0.36f).coerceIn(180.dp, 260.dp)
    }

    val openIdSet = remember(allOpenTasks) { allOpenTasks.mapTo(HashSet()) { it.id } }
    var selectedBlockerIds by remember(task.id, task.blockedByTaskIds) {
        mutableStateOf(
            task.blockedByTaskIds
                .filter { it != task.id && it in openIdSet }
                .distinct()
        )
    }
    var searchField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var cycleWarningVisible by remember { mutableStateOf(false) }

    val candidateTasks = remember(allOpenTasks, task.id) {
        allOpenTasks.filter { !it.isDeleted && it.id != task.id }
    }
    val blockedByMap = remember(allOpenTasks, task.id, selectedBlockerIds) {
        val map = HashMap<String, List<String>>(allOpenTasks.size + 1)
        for (t in allOpenTasks) {
            map[t.id] = t.blockedByTaskIds
        }
        map[task.id] = selectedBlockerIds
        map
    }

    val normalizedQuery = searchField.text.trim().lowercase(Locale.ROOT)
    val filteredCandidates = remember(candidateTasks, normalizedQuery, selectedBlockerIds) {
        val base = if (normalizedQuery.isEmpty()) {
            candidateTasks
        } else {
            candidateTasks.filter { candidate ->
                candidate.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    candidate.notes.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
        }
        base.sortedWith(
            compareByDescending<TaskItem> { it.id in selectedBlockerIds }
                .thenByDescending { it.isPinned }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        ThingsTooltipHostBox {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HourglassTop,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.v30_blocked_by_dialog_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

                    if (task.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchField,
                        onValueChange = {
                            searchField = it
                            cycleWarningVisible = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchField.text.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchField = TextFieldValue(text = "", selection = TextRange(0)) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.action_cancel),
                                        tint = colors.secondaryText,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.v30_blocked_by_search_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (cycleWarningVisible) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.deadlineUrgentRed.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.v30_blocked_by_cycle_warning),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.deadlineUrgentRed,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(listHeight)
                    ) {
                        if (filteredCandidates.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.v30_blocked_by_none_available),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.secondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredCandidates, key = { it.id }) { candidate ->
                                    val isChecked = candidate.id in selectedBlockerIds
                                    val wouldCycle = !isChecked && wouldCreateDependencyCycle(
                                        taskId = task.id,
                                        candidateBlockerId = candidate.id,
                                        blockedByMap = blockedByMap
                                    )
                                    val toggleCandidate = {
                                        if (isChecked) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val next = selectedBlockerIds.filterNot { it == candidate.id }
                                            selectedBlockerIds = next
                                            cycleWarningVisible = false
                                            onUpdateBlockedBy(next)
                                        } else if (wouldCycle) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            cycleWarningVisible = true
                                        } else {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val next = (selectedBlockerIds + candidate.id).distinct()
                                            selectedBlockerIds = next
                                            cycleWarningVisible = false
                                            onUpdateBlockedBy(next)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isChecked) colors.thingsBlueSoft.copy(alpha = 0.55f)
                                                else Color.Transparent
                                            )
                                            .alpha(if (wouldCycle) 0.5f else 1f)
                                            .clickable { toggleCandidate() }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { toggleCandidate() },
                                                enabled = !wouldCycle,
                                                colors = CheckboxDefaults.colors(checkedColor = colors.thingsBlue)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = candidate.title.ifBlank { stringResource(R.string.placeholder_todo_title) },
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                                    ),
                                                    color = colors.primaryText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (wouldCycle) {
                                                    Text(
                                                        text = stringResource(R.string.v30_blocked_by_cycle_warning),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = colors.deadlineUrgentRed,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        val durText = formatDurationCompact(candidate.durationMinutes)
                                        if (durText != null) {
                                            ThingsTagPill(title = durText)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedBlockerIds.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedBlockerIds = emptyList()
                                    onUpdateBlockedBy(emptyList())
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.action_clear),
                                    color = colors.secondaryText,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_done),
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

/**
 * Custom Perspective Editor Dialog (`New Perspective` / `Edit Perspective`).
 * Persists compound filter views combining Categories, Effort, Max Duration, Due Window, Area,
 * and `Actionable only (hide blocked)`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerspectiveEditorDialog(
    initialPerspective: SavedPerspective?,
    areas: List<Area>,
    allTags: List<Tag>,
    onSave: (SavedPerspective) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val scrollBodyHeight = remember(screenHeightDp) {
        (screenHeightDp.dp * 0.54f).coerceIn(240.dp, 400.dp)
    }

    val isEditing = initialPerspective != null
    var titleField by remember(initialPerspective?.id) {
        val t = initialPerspective?.title.orEmpty()
        mutableStateOf(TextFieldValue(text = t, selection = TextRange(t.length)))
    }
    var selectedEffort by remember(initialPerspective?.id) {
        mutableStateOf(EffortLevel.normalize(initialPerspective?.effortLevel))
    }
    var selectedMaxDuration by remember(initialPerspective?.id) {
        mutableStateOf(initialPerspective?.maxDurationMinutes)
    }
    var selectedDueWindow by remember(initialPerspective?.id) {
        mutableStateOf(initialPerspective?.dueWindow?.uppercase(Locale.ROOT) ?: "ANY")
    }
    var selectedAreaId by remember(initialPerspective?.id) {
        mutableStateOf(initialPerspective?.areaId)
    }
    var selectedCategoryIds by remember(initialPerspective?.id) {
        mutableStateOf(initialPerspective?.categoryIds?.toSet().orEmpty())
    }
    var actionableOnly by remember(initialPerspective?.id) {
        mutableStateOf(initialPerspective?.actionableOnly ?: false)
    }
    var inProgressOnly by remember(initialPerspective?.id) {
        mutableStateOf(initialPerspective?.inProgressOnly ?: false)
    }

    val commitSave = {
        val cleanTitle = titleField.text.trim()
        if (cleanTitle.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onSave(
                SavedPerspective(
                    id = initialPerspective?.id ?: UUID.randomUUID().toString(),
                    title = cleanTitle,
                    categoryIds = selectedCategoryIds.toList(),
                    effortLevel = selectedEffort,
                    maxDurationMinutes = selectedMaxDuration,
                    dueWindow = selectedDueWindow,
                    areaId = selectedAreaId,
                    actionableOnly = actionableOnly,
                    inProgressOnly = inProgressOnly,
                    sortOrder = initialPerspective?.sortOrder ?: 0.0
                )
            )
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        ThingsTooltipHostBox {
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
                                imageVector = Icons.Outlined.AutoAwesomeMosaic,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (isEditing) R.string.v30_perspective_edit else R.string.v30_perspective_new
                                ),
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

                    OutlinedTextField(
                        value = titleField,
                        onValueChange = { titleField = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.v30_perspective_title_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitSave() }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(scrollBodyHeight)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Effort Level
                        PerspectiveSectionHeader(text = stringResource(R.string.v30_effort_label))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val effortChoices: List<Pair<String?, String>> = listOf(
                                null to stringResource(R.string.v30_effort_none),
                                EffortLevel.QUICK_WIN to "⚡ ${stringResource(R.string.v30_effort_quick_win)}",
                                EffortLevel.MEDIUM to "◐ ${stringResource(R.string.v30_effort_medium)}",
                                EffortLevel.DEEP_FOCUS to "🧠 ${stringResource(R.string.v30_effort_deep_focus)}"
                            )
                            effortChoices.forEach { (key, label) ->
                                PerspectiveFilterChip(
                                    label = label,
                                    selected = selectedEffort == key,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedEffort = key
                                    }
                                )
                            }
                        }

                        // 2. Max Duration
                        PerspectiveSectionHeader(text = stringResource(R.string.v30_perspective_max_duration))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val durationChoices: List<Pair<Int?, String>> = listOf(
                                null to stringResource(R.string.v30_perspective_any_duration),
                                15 to "≤ 15m",
                                30 to "≤ 30m",
                                60 to "≤ 1h",
                                120 to "≤ 2h",
                                240 to "≤ 4h",
                                480 to "≤ 8h"
                            )
                            durationChoices.forEach { (mins, label) ->
                                PerspectiveFilterChip(
                                    label = label,
                                    selected = selectedMaxDuration == mins,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedMaxDuration = mins
                                    }
                                )
                            }
                        }

                        // 3. Due Window
                        PerspectiveSectionHeader(text = stringResource(R.string.view_upcoming))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val dueChoices: List<Pair<String, String>> = listOf(
                                "ANY" to stringResource(R.string.search_filter_date_all),
                                "TODAY" to stringResource(R.string.search_filter_date_today),
                                "OVERDUE" to stringResource(R.string.search_filter_date_overdue),
                                "NEXT_7_DAYS" to stringResource(R.string.search_filter_date_next_7_days),
                                "HAS_DEADLINE" to stringResource(R.string.search_filter_has_deadline),
                                "NO_DATE" to stringResource(R.string.search_filter_date_none)
                            )
                            dueChoices.forEach { (key, label) ->
                                PerspectiveFilterChip(
                                    label = label,
                                    selected = selectedDueWindow == key,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedDueWindow = key
                                    }
                                )
                            }
                        }

                        // 4. Area Filter
                        if (areas.isNotEmpty()) {
                            PerspectiveSectionHeader(text = stringResource(R.string.topbar_tooltip_areas))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PerspectiveFilterChip(
                                    label = stringResource(R.string.tags_filter_all),
                                    selected = selectedAreaId == null,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedAreaId = null
                                    }
                                )
                                areas.forEach { area ->
                                    PerspectiveFilterChip(
                                        label = area.title,
                                        selected = selectedAreaId == area.id,
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedAreaId = if (selectedAreaId == area.id) null else area.id
                                        }
                                    )
                                }
                            }
                        }

                        // 5. Categories Multi-Select
                        if (allTags.isNotEmpty()) {
                            PerspectiveSectionHeader(text = stringResource(R.string.topbar_tooltip_tags))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                allTags.forEach { tag ->
                                    val checked = tag.id in selectedCategoryIds
                                    PerspectiveFilterChip(
                                        label = tag.title,
                                        selected = checked,
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedCategoryIds = if (checked) {
                                                selectedCategoryIds - tag.id
                                            } else {
                                                selectedCategoryIds + tag.id
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // 6. Actionable Only Toggle
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.tagPillBg.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    actionableOnly = !actionableOnly
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.v30_perspective_actionable_only),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = colors.primaryText,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = actionableOnly,
                                    onCheckedChange = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        actionableOnly = it
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = colors.thingsBlue)
                                )
                            }
                        }

                        // 7. In Progress Only Toggle
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.tagPillBg.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    inProgressOnly = !inProgressOnly
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "◐ ${stringResource(R.string.v33_filter_in_progress_only)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = colors.primaryText,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = inProgressOnly,
                                    onCheckedChange = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        inProgressOnly = it
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = colors.somedayWarmAmber)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (initialPerspective != null && onDelete != null) {
                            TextButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDelete(initialPerspective.id)
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = colors.deadlineUrgentRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.v30_perspective_delete),
                                    color = colors.deadlineUrgentRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            TextButton(onClick = onDismiss) {
                                Text(
                                    text = stringResource(R.string.action_cancel),
                                    color = colors.secondaryText,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Button(
                            onClick = { commitSave() },
                            enabled = titleField.text.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.v30_perspective_save),
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
private fun PerspectiveSectionHeader(text: String) {
    val colors = ThingsTheme.colors
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        ),
        color = colors.secondaryText
    )
}

@Composable
private fun PerspectiveFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ThingsTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.thingsBlue else colors.tagPillBg.copy(alpha = 0.7f),
        border = if (selected) null else BorderStroke(0.75.dp, colors.hairlineDivider),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) Color.White else colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
