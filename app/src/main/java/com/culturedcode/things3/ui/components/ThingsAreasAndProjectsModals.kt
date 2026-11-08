package com.culturedcode.things3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.culturedcode.things3.R
import com.culturedcode.things3.data.repository.ThingsRepository
import com.culturedcode.things3.domain.model.Area
import com.culturedcode.things3.domain.model.Heading
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.ui.theme.ThingsTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Things-for-Mac Areas of Responsibility Manager Modal:
 * - Preconfigured with "Work" and "Home"
 * - Supports up to 8 Areas (ThingsRepository.MAX_AREAS = 8)
 * - Supports Rename, Move Up/Down (Reorder), Open Area, and Delete.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThingsAreaManagerModal(
    areas: List<Area>,
    projects: List<Project>,
    onCreateArea: (String) -> Unit,
    onRenameArea: (String, String) -> Unit,
    onMoveArea: (String, Boolean) -> Unit,
    onDeleteArea: (String) -> Unit,
    onOpenArea: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    var newAreaField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var editingAreaId by remember { mutableStateOf<String?>(null) }
    var editingAreaField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }

    val canAddMore = areas.size < ThingsRepository.MAX_AREAS

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(16.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
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
                            imageVector = Icons.Outlined.Inventory2,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.entity_areas),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.primaryText
                            )
                            Text(
                                text = stringResource(
                                    R.string.areas_active_count,
                                    areas.size,
                                    ThingsRepository.MAX_AREAS
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
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

                // Areas List (Rename, Move Up/Down, Delete, Open)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(areas, key = { _, a -> a.id }) { index, area ->
                        val isEditing = editingAreaId == area.id
                        val projectCount = projects.count { it.areaId == area.id }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.tagPillBg)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (isEditing) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = editingAreaField,
                                        onValueChange = { editingAreaField = it },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    val txt = editingAreaField.text
                                                    editingAreaField = editingAreaField.copy(
                                                        selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                                    )
                                                }
                                            },
                                        label = { Text(stringResource(R.string.areas_rename_label)) }
                                    )
                                    IconButton(
                                        onClick = {
                                            if (editingAreaField.text.isNotBlank()) {
                                                onRenameArea(area.id, editingAreaField.text.trim())
                                                editingAreaId = null
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = stringResource(R.string.action_save),
                                            tint = colors.thingsBlue
                                        )
                                    }
                                    IconButton(onClick = { editingAreaId = null }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.action_cancel),
                                            tint = colors.secondaryText
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onOpenArea(area.id)
                                                onDismiss()
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Inventory2,
                                            contentDescription = null,
                                            tint = colors.anytimeTeal,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = area.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = colors.primaryText
                                            )
                                            Text(
                                                text = stringResource(R.string.areas_projects_count_hint, projectCount),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                                color = colors.secondaryText
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        // Move Up
                                        IconButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onMoveArea(area.id, true)
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ArrowUpward,
                                                contentDescription = stringResource(R.string.action_move),
                                                tint = if (index > 0) colors.primaryText else colors.tertiaryText,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        // Move Down
                                        IconButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onMoveArea(area.id, false)
                                            },
                                            enabled = index < areas.lastIndex,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ArrowDownward,
                                                contentDescription = stringResource(R.string.action_move),
                                                tint = if (index < areas.lastIndex) colors.primaryText else colors.tertiaryText,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        // Rename
                                        IconButton(
                                            onClick = {
                                                editingAreaId = area.id
                                                editingAreaField = TextFieldValue(
                                                    text = area.title,
                                                    selection = TextRange(0, area.title.length)
                                                )
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = stringResource(R.string.areas_rename_label),
                                                tint = colors.thingsBlue,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        // Delete
                                        IconButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onDeleteArea(area.id)
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
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                Spacer(modifier = Modifier.height(10.dp))

                if (canAddMore) {
                    Text(
                        text = stringResource(
                            R.string.areas_add_new_hint,
                            ThingsRepository.MAX_AREAS - areas.size
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.secondaryText
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newAreaField,
                            onValueChange = { newAreaField = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.entity_new_area),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        val txt = newAreaField.text
                                        newAreaField = newAreaField.copy(
                                            selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                        )
                                    }
                                }
                        )
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.thingsBlue)
                                .clickable {
                                    if (newAreaField.text.isNotBlank()) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCreateArea(newAreaField.text.trim())
                                        newAreaField = TextFieldValue(text = "", selection = TextRange(0))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.entity_new_area),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Quick 1-tap Area suggestions if not yet present (up to 8 Areas)
                    val existingNamesLower = areas.map { it.title.lowercase(Locale.ROOT) }.toSet()
                    val suggestions = listOf("Work", "Home", "Finance", "Health", "Family", "Study", "Sport", "Hobby")
                        .filter { it.lowercase(Locale.ROOT) !in existingNamesLower }
                    if (suggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestions.forEach { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(colors.thingsBlueSoft)
                                        .clickable {
                                            if (areas.size < ThingsRepository.MAX_AREAS) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onCreateArea(suggestion)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "+ $suggestion",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.thingsBlue
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.thingsBlueSoft)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.areas_max_reached, ThingsRepository.MAX_AREAS),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.thingsBlue
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog to Create a New Project or Edit an Existing Project:
 * - Project Title & Notes
 * - Area Assignment (Work, Home, or any of the up to 8 Areas)
 * - Project Target Date / Deadline (Zieldatum) via our compact calendar picker!
 */
@Composable
fun CreateOrEditProjectDialog(
    existingProject: Project? = null,
    areas: List<Area>,
    preselectedAreaId: String? = null,
    locale: Locale = Locale.getDefault(),
    onSaveProject: (title: String, notes: String, areaId: String?, deadlineEpochMillis: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()

    var titleField by remember(existingProject) {
        val initialTitle = existingProject?.title ?: ""
        mutableStateOf(
            TextFieldValue(
                text = initialTitle,
                selection = if (initialTitle.isEmpty()) TextRange(0) else TextRange(0, initialTitle.length)
            )
        )
    }
    var notesField by remember(existingProject) {
        val initialNotes = existingProject?.notes ?: ""
        mutableStateOf(
            TextFieldValue(
                text = initialNotes,
                selection = if (initialNotes.isEmpty()) TextRange(0) else TextRange(0, initialNotes.length)
            )
        )
    }
    var selectedAreaId by remember(existingProject, preselectedAreaId) {
        mutableStateOf(existingProject?.areaId ?: preselectedAreaId)
    }
    var deadlineEpochMillis by remember(existingProject) {
        mutableStateOf(existingProject?.deadline)
    }
    var showDeadlineCalendar by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 340.dp)
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
                        ProjectProgressRing(progress = existingProject?.progress ?: 0f, size = 20.dp)
                        Text(
                            text = if (existingProject == null) {
                                stringResource(R.string.entity_new_project)
                            } else {
                                stringResource(R.string.project_edit_title)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = titleField,
                    onValueChange = { titleField = it },
                    label = { Text(stringResource(R.string.entity_project)) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.entity_new_project),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                val txt = titleField.text
                                titleField = titleField.copy(
                                    selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                )
                            }
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notesField,
                    onValueChange = { notesField = it },
                    label = { Text(stringResource(R.string.placeholder_notes)) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                val txt = notesField.text
                                notesField = notesField.copy(
                                    selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                )
                            }
                        }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Area Picker Strip
                Text(
                    text = "${stringResource(R.string.entity_area)}:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.secondaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val noAreaSelected = selectedAreaId == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (noAreaSelected) colors.thingsBlue else colors.tagPillBg)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedAreaId = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.areas_none),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (noAreaSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (noAreaSelected) Color.White else colors.primaryText
                        )
                    }

                    areas.forEach { area ->
                        val isSelected = selectedAreaId == area.id
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.thingsBlue else colors.tagPillBg)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedAreaId = area.id
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Inventory2,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else colors.anytimeTeal,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = area.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) Color.White else colors.primaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Project Target Date (Zieldatum / Deadline) Picker Button
                Text(
                    text = "${stringResource(R.string.project_target_date_label)}:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.secondaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val formattedTargetDate = deadlineEpochMillis?.let { epoch ->
                        runCatching {
                            Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
                                .format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", locale))
                        }.getOrNull()
                    } ?: stringResource(R.string.project_set_target_date)

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (deadlineEpochMillis != null) colors.deadlineSoftBg else colors.tagPillBg)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showDeadlineCalendar = true
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = null,
                            tint = if (deadlineEpochMillis != null) colors.deadlineUrgentRed else colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = formattedTargetDate,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (deadlineEpochMillis != null) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 11.5.sp
                            ),
                            color = if (deadlineEpochMillis != null) colors.deadlineUrgentRed else colors.primaryText
                        )
                    }

                    if (deadlineEpochMillis != null) {
                        IconButton(
                            onClick = { deadlineEpochMillis = null },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.deadline_clear_button),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Save / Cancel Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.tagPillBg)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.secondaryText
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.thingsBlue)
                            .clickable {
                                if (titleField.text.isNotBlank()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSaveProject(
                                        titleField.text.trim(),
                                        notesField.text.trim(),
                                        selectedAreaId,
                                        deadlineEpochMillis
                                    )
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.action_save),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }


    if (showDeadlineCalendar) {
        DeadlineDatePickerDialog(
            currentDeadline = deadlineEpochMillis,
            locale = locale,
            onApplyDeadline = { chosen -> deadlineEpochMillis = chosen },
            onDismiss = { showDeadlineCalendar = false }
        )
    }
}

/**
 * Compact Popover Dialog to Move/Assign a Task to a Project (and optional Heading) or Area (Work, Home, etc.).
 */
@Composable
fun MoveTaskToProjectOrAreaDialog(
    currentProjectId: String?,
    currentHeadingId: String?,
    currentAreaId: String?,
    areas: List<Area>,
    projects: List<Project>,
    headings: List<Heading>,
    onMoveTask: (projectId: String?, headingId: String?, areaId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val areaLabel = stringResource(R.string.entity_area)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
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
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = stringResource(R.string.move_dialog_title),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = colors.primaryText
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Option 1: Unassigned (No Project / No Area)
                    item(key = "move_none") {
                        val isNone = currentProjectId == null && currentAreaId == null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isNone) colors.thingsBlueSoft else colors.tagPillBg)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onMoveTask(null, null, null)
                                    onDismiss()
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.move_none_option),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.primaryText
                            )
                            if (isNone) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = colors.thingsBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // Option 2: Areas (Work, Home, ...) and their Projects
                    items(areas, key = { "move_area_${it.id}" }) { area ->
                        val isAreaDirectlySelected = currentProjectId == null && currentAreaId == area.id
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isAreaDirectlySelected) colors.thingsBlueSoft else colors.tagPillBg)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onMoveTask(null, null, area.id)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Inventory2,
                                        contentDescription = null,
                                        tint = colors.anytimeTeal,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "$areaLabel: ${area.title}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.primaryText
                                    )
                                }
                                if (isAreaDirectlySelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = colors.thingsBlue,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            // Projects belonging to this Area
                            val areaProjects = projects.filter { it.areaId == area.id }
                            areaProjects.forEach { proj ->
                                ProjectMoveRowItem(
                                    project = proj,
                                    headings = headings.filter { it.projectId == proj.id },
                                    currentProjectId = currentProjectId,
                                    currentHeadingId = currentHeadingId,
                                    onSelect = { pId, hId ->
                                        onMoveTask(pId, hId, area.id)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }

                    // Projects without an Area
                    val unassignedProjects = projects.filter { it.areaId == null }
                    if (unassignedProjects.isNotEmpty()) {
                        items(unassignedProjects, key = { "move_proj_unassigned_${it.id}" }) { proj ->
                            ProjectMoveRowItem(
                                project = proj,
                                headings = headings.filter { it.projectId == proj.id },
                                currentProjectId = currentProjectId,
                                currentHeadingId = currentHeadingId,
                                onSelect = { pId, hId ->
                                    onMoveTask(pId, hId, null)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectMoveRowItem(
    project: Project,
    headings: List<Heading>,
    currentProjectId: String?,
    currentHeadingId: String?,
    onSelect: (projectId: String, headingId: String?) -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val isProjectSelected = currentProjectId == project.id && currentHeadingId == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isProjectSelected) colors.thingsBlueSoft else colors.cardElevatedSurface)
                .border(
                    width = 0.5.dp,
                    color = colors.hairlineDivider,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(project.id, null)
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ProjectProgressRing(progress = project.progress, size = 14.dp)
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primaryText
                )
            }
            if (isProjectSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        headings.forEach { heading ->
            val isHeadingSelected = currentProjectId == project.id && currentHeadingId == heading.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isHeadingSelected) colors.thingsBlueSoft else Color.Transparent)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(project.id, heading.id)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "↳ ${heading.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.thingsBlue
                )
                if (isHeadingSelected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
