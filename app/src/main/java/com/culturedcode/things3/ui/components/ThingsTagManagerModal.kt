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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
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

    var newTagNameField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var newTagShortcutField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var editingTagId by remember { mutableStateOf<String?>(null) }
    var editingTagTitleField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var editingTagShortcutField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }

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
                        value = newTagNameField,
                        onValueChange = { newTagNameField = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.tags_new_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newTagNameField.text.isNotBlank()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCreateTag(
                                        newTagNameField.text.trim(),
                                        newTagShortcutField.text.trim().ifEmpty { null }
                                    )
                                    newTagNameField = TextFieldValue(text = "", selection = TextRange(0))
                                    newTagShortcutField = TextFieldValue(text = "", selection = TextRange(0))
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    val txt = newTagNameField.text
                                    newTagNameField = newTagNameField.copy(
                                        selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                    )
                                }
                            }
                    )

                    OutlinedTextField(
                        value = newTagShortcutField,
                        onValueChange = { if (it.text.length <= 2) newTagShortcutField = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.tags_shortcut_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .width(82.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    val txt = newTagShortcutField.text
                                    newTagShortcutField = newTagShortcutField.copy(
                                        selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                    )
                                }
                            }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (newTagNameField.text.isNotBlank()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCreateTag(
                                newTagNameField.text.trim(),
                                newTagShortcutField.text.trim().ifEmpty { null }
                            )
                            newTagNameField = TextFieldValue(text = "", selection = TextRange(0))
                            newTagShortcutField = TextFieldValue(text = "", selection = TextRange(0))
                        }
                    },
                    enabled = newTagNameField.text.isNotBlank(),
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
                                            value = editingTagTitleField,
                                            onValueChange = { editingTagTitleField = it },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .onFocusChanged { focusState ->
                                                    if (focusState.isFocused) {
                                                        val txt = editingTagTitleField.text
                                                        editingTagTitleField = editingTagTitleField.copy(
                                                            selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                                        )
                                                    }
                                                }
                                        )
                                        OutlinedTextField(
                                            value = editingTagShortcutField,
                                            onValueChange = { if (it.text.length <= 2) editingTagShortcutField = it },
                                            placeholder = {
                                                Text(
                                                    text = stringResource(R.string.tags_shortcut_placeholder),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            singleLine = true,
                                            modifier = Modifier
                                                .width(68.dp)
                                                .onFocusChanged { focusState ->
                                                    if (focusState.isFocused) {
                                                        val txt = editingTagShortcutField.text
                                                        editingTagShortcutField = editingTagShortcutField.copy(
                                                            selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                                        )
                                                    }
                                                }
                                        )
                                        IconButton(
                                            onClick = {
                                                if (editingTagTitleField.text.isNotBlank()) {
                                                    onRenameTag(
                                                        tag.id,
                                                        editingTagTitleField.text.trim(),
                                                        editingTagShortcutField.text.trim().ifEmpty { null }
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
                                                        text = "Ctrl+${tag.shortcut.uppercase(java.util.Locale.ROOT)}",
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
                                                    editingTagTitleField = TextFieldValue(
                                                        text = tag.title,
                                                        selection = TextRange(0, tag.title.length)
                                                    )
                                                    val sc = tag.shortcut ?: ""
                                                    editingTagShortcutField = TextFieldValue(
                                                        text = sc,
                                                        selection = if (sc.isEmpty()) TextRange(0) else TextRange(0, sc.length)
                                                    )
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
    var quickNewTagField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }

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
                        value = quickNewTagField,
                        onValueChange = { quickNewTagField = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.tags_new_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (quickNewTagField.text.isNotBlank()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCreateAndAssignTag(quickNewTagField.text.trim())
                                    quickNewTagField = TextFieldValue(text = "", selection = TextRange(0))
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    val txt = quickNewTagField.text
                                    quickNewTagField = quickNewTagField.copy(
                                        selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                    )
                                }
                            }
                    )

                    IconButton(
                        onClick = {
                            if (quickNewTagField.text.isNotBlank()) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCreateAndAssignTag(quickNewTagField.text.trim())
                                quickNewTagField = TextFieldValue(text = "", selection = TextRange(0))
                            }
                        },
                        enabled = quickNewTagField.text.isNotBlank(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (quickNewTagField.text.isNotBlank()) colors.thingsBlue else colors.tagPillBg)
                            .size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.tags_add_button),
                            tint = if (quickNewTagField.text.isNotBlank()) Color.White else colors.secondaryText
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
                                ThingsTagPill(title = "Ctrl+${tag.shortcut.uppercase(java.util.Locale.ROOT)}")
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

