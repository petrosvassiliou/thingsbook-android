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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalOffer
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.culturedcode.things3.domain.model.Tag
import com.culturedcode.things3.ui.theme.ThingsTheme

/**
 * Full Tag Management Modal (`Tags verwalten`):
 * Create new tags, rename existing tags, assign optional keyboard shortcuts, or delete tags.
 */
@Composable
fun ThingsTagManagerModal(
    allTags: List<Tag>,
    onCreateTag: (title: String, shortcut: String?) -> Unit,
    onRenameTag: (tagId: String, newTitle: String, shortcut: String?) -> Unit,
    onDeleteTag: (tagId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    var newTagName by remember { mutableStateOf("") }
    var newTagShortcut by remember { mutableStateOf("") }
    var editingTagId by remember { mutableStateOf<String?>(null) }
    var editingTagTitle by remember { mutableStateOf("") }
    var editingTagShortcut by remember { mutableStateOf("") }

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
                            imageVector = Icons.Outlined.LocalOffer,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(21.dp)
                        )
                        Text(
                            text = stringResource(R.string.tags_manage_title),
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

                Spacer(modifier = Modifier.height(12.dp))

                // Create New Tag Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.tags_new_placeholder),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newTagName.isNotBlank()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCreateTag(newTagName.trim(), newTagShortcut.trim().ifEmpty { null })
                                    newTagName = ""
                                    newTagShortcut = ""
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = newTagShortcut,
                        onValueChange = { if (it.length <= 2) newTagShortcut = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.tags_shortcut_placeholder),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.width(78.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCreateTag(newTagName.trim(), newTagShortcut.trim().ifEmpty { null })
                            newTagName = ""
                            newTagShortcut = ""
                        }
                    },
                    enabled = newTagName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.tags_add_button),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                Spacer(modifier = Modifier.height(10.dp))

                if (allTags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tags_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(allTags, key = { it.id }) { tag ->
                            val isEditingThis = editingTagId == tag.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.tagPillBg.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isEditingThis) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = editingTagTitle,
                                            onValueChange = { editingTagTitle = it },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = editingTagShortcut,
                                            onValueChange = { if (it.length <= 2) editingTagShortcut = it },
                                            placeholder = { Text("Key") },
                                            singleLine = true,
                                            modifier = Modifier.width(64.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                if (editingTagTitle.isNotBlank()) {
                                                    onRenameTag(
                                                        tag.id,
                                                        editingTagTitle.trim(),
                                                        editingTagShortcut.trim().ifEmpty { null }
                                                    )
                                                }
                                                editingTagId = null
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = stringResource(R.string.action_save),
                                                tint = colors.thingsBlue
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.LocalOffer,
                                                contentDescription = null,
                                                tint = colors.thingsBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = tag.title,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                                color = colors.primaryText
                                            )
                                            if (!tag.shortcut.isNullOrBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(colors.thingsBlueSoft)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "⌘${tag.shortcut}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = colors.thingsBlue
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    editingTagId = tag.id
                                                    editingTagTitle = tag.title
                                                    editingTagShortcut = tag.shortcut ?: ""
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = stringResource(R.string.tags_edit_title),
                                                    tint = colors.secondaryText,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onDeleteTag(tag.id)
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.DeleteOutline,
                                                    contentDescription = stringResource(R.string.action_delete),
                                                    tint = colors.deadlineUrgentRed,
                                                    modifier = Modifier.size(17.dp)
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
        }
    }
}

/**
 * Interactive Tag Assignment Dialog (`Tags für diese Aufgabe`) inside any expanded Task Card.
 * Lets the user check/uncheck tags for a task or create a new tag on the fly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskTagPickerDialog(
    allTags: List<Tag>,
    selectedTagIds: List<String>,
    onUpdateSelectedTags: (List<String>) -> Unit,
    onCreateAndAssignTag: (String) -> Unit,
    onOpenFullTagManager: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    var currentSelection by remember(selectedTagIds) { mutableStateOf(selectedTagIds.toSet()) }
    var quickNewTagTitle by remember { mutableStateOf("") }

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
                            imageVector = Icons.Outlined.LocalOffer,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.tags_assign_title),
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

                // Quick Create & Assign Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quickNewTagTitle,
                        onValueChange = { quickNewTagTitle = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.tags_new_placeholder),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (quickNewTagTitle.isNotBlank()) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCreateAndAssignTag(quickNewTagTitle.trim())
                                quickNewTagTitle = ""
                            }
                        },
                        enabled = quickNewTagTitle.isNotBlank(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (quickNewTagTitle.isNotBlank()) colors.thingsBlue else colors.tagPillBg)
                            .size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.tags_add_button),
                            tint = if (quickNewTagTitle.isNotBlank()) Color.White else colors.secondaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allTags, key = { it.id }) { tag ->
                        val isChecked = tag.id in currentSelection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val updated = if (isChecked) currentSelection - tag.id else currentSelection + tag.id
                                    currentSelection = updated
                                    onUpdateSelectedTags(updated.toList())
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val updated = if (checked) currentSelection + tag.id else currentSelection - tag.id
                                        currentSelection = updated
                                        onUpdateSelectedTags(updated.toList())
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = colors.thingsBlue)
                                )
                                Text(
                                    text = tag.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.primaryText
                                )
                            }

                            if (!tag.shortcut.isNullOrBlank()) {
                                ThingsTagPill(title = "⌘${tag.shortcut}")
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
                    TextButton(
                        onClick = {
                            onDismiss()
                            onOpenFullTagManager()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.tags_manage_title),
                            color = colors.thingsBlue,
                            fontWeight = FontWeight.SemiBold
                        )
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
