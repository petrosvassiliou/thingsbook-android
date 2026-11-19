package com.culturedcode.things3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.Tag
import com.culturedcode.things3.domain.model.sortTagsForMode
import com.culturedcode.things3.ui.theme.ThingsTheme
import java.util.Locale

/**
 * Full Tag Management Modal (`Manage Tags`):
 * Constant-height locked viewport, live search, sort modes (A–Z, Most Used, Custom),
 * usage count badges, manual reorder arrows, inline rename + shortcut clear, and tag merging.
 */
@Composable
fun ThingsTagManagerModal(
    allTags: List<Tag>,
    tagSortMode: String = "alpha",
    tagUsageCounts: Map<String, Int> = emptyMap(),
    onSetTagSortMode: (String) -> Unit = {},
    onCreateTag: (title: String, shortcut: String?) -> Unit,
    onRenameTag: (tagId: String, newTitle: String, shortcut: String?) -> Unit,
    onMoveTag: (tagId: String, moveUp: Boolean) -> Unit = { _, _ -> },
    onMergeTags: (sourceTagId: String, targetTagId: String) -> Unit = { _, _ -> },
    onDeleteTag: (tagId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val manageListHeight = minOf(260.dp, (screenHeightDp * 0.38f).dp).coerceAtLeast(160.dp)

    var newTagNameField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var newTagShortcutField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var searchField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var editingTagId by remember { mutableStateOf<String?>(null) }
    var editingTagTitleField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var editingTagShortcutField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var mergeMenuForTagId by remember { mutableStateOf<String?>(null) }

    val sortedTags = remember(allTags, tagSortMode, tagUsageCounts) {
        sortTagsForMode(allTags, tagSortMode, tagUsageCounts)
    }
    val searchQuery = searchField.text.trim()
    val filteredTags = remember(sortedTags, searchQuery) {
        if (searchQuery.isEmpty()) {
            sortedTags
        } else {
            sortedTags.filter { tag ->
                tag.title.contains(searchQuery, ignoreCase = true) ||
                    (tag.shortcut?.contains(searchQuery, ignoreCase = true) == true)
            }
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

                        ThingsHoverTooltip(text = stringResource(R.string.action_cancel)) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val submitCreateTag = {
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

                    // Compact Create New Tag Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            keyboardActions = KeyboardActions(onDone = { submitCreateTag() }),
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown &&
                                        (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                    ) {
                                        submitCreateTag()
                                        true
                                    } else {
                                        false
                                    }
                                }
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
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitCreateTag() }),
                            modifier = Modifier
                                .width(76.dp)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown &&
                                        (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                    ) {
                                        submitCreateTag()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        val txt = newTagShortcutField.text
                                        newTagShortcutField = newTagShortcutField.copy(
                                            selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                        )
                                    }
                                }
                        )

                        IconButton(
                            onClick = { submitCreateTag() },
                            enabled = newTagNameField.text.isNotBlank(),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (newTagNameField.text.isNotBlank()) colors.thingsBlue else colors.tagPillBg)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.tags_add_button),
                                tint = if (newTagNameField.text.isNotBlank()) Color.White else colors.secondaryText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Search Input
                    OutlinedTextField(
                        value = searchField,
                        onValueChange = { searchField = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.v25_tags_search_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (searchField.text.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = {
                                        searchField = TextFieldValue(text = "", selection = TextRange(0))
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.action_clear),
                                        tint = colors.secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sort Mode Segmented Pills (A–Z | Most Used | Custom)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sortOptions = listOf(
                            "alpha" to stringResource(R.string.v25_tags_sort_alpha),
                            "most_used" to stringResource(R.string.v25_tags_sort_most_used),
                            "manual" to stringResource(R.string.v25_tags_sort_manual)
                        )
                        val normalizedActiveMode = when (tagSortMode.trim().lowercase(Locale.ROOT)) {
                            "most_used" -> "most_used"
                            "manual" -> "manual"
                            else -> "alpha"
                        }
                        sortOptions.forEach { (modeKey, label) ->
                            val isSelected = normalizedActiveMode == modeKey
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colors.thingsBlue else colors.tagPillBg.copy(alpha = 0.7f),
                                border = if (isSelected) null else BorderStroke(0.75.dp, colors.hairlineDivider),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSetTagSortMode(modeKey)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) Color.White else colors.primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Constant-Height Locked List Viewport
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(manageListHeight)
                    ) {
                        if (allTags.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.tags_empty_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.secondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (filteredTags.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.v25_tags_no_matches),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.secondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            val isManualOrderActive = tagSortMode.trim().equals("manual", ignoreCase = true) &&
                                searchQuery.isEmpty()
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(filteredTags, key = { _, it -> it.id }) { idx, tag ->
                                    val isEditingThis = editingTagId == tag.id
                                    val usageCount = tagUsageCounts[tag.id] ?: 0
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.tagPillBg.copy(alpha = 0.65f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isEditingThis) {
                                            val commitRenameTag = {
                                                if (editingTagTitleField.text.isNotBlank()) {
                                                    onRenameTag(
                                                        tag.id,
                                                        editingTagTitleField.text.trim(),
                                                        editingTagShortcutField.text.trim().ifEmpty { null }
                                                    )
                                                }
                                                editingTagId = null
                                            }
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
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = { commitRenameTag() }),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .onPreviewKeyEvent { keyEvent ->
                                                            if (keyEvent.type == KeyEventType.KeyDown &&
                                                                (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                                            ) {
                                                                commitRenameTag()
                                                                true
                                                            } else {
                                                                false
                                                            }
                                                        }
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
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = { commitRenameTag() }),
                                                    modifier = Modifier
                                                        .width(68.dp)
                                                        .onPreviewKeyEvent { keyEvent ->
                                                            if (keyEvent.type == KeyEventType.KeyDown &&
                                                                (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                                            ) {
                                                                commitRenameTag()
                                                                true
                                                            } else {
                                                                false
                                                            }
                                                        }
                                                        .onFocusChanged { focusState ->
                                                            if (focusState.isFocused) {
                                                                val txt = editingTagShortcutField.text
                                                                editingTagShortcutField = editingTagShortcutField.copy(
                                                                    selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                                                )
                                                            }
                                                        }
                                                )
                                                IconButton(onClick = commitRenameTag, modifier = Modifier.size(30.dp)) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Check,
                                                        contentDescription = stringResource(R.string.action_save),
                                                        tint = colors.thingsBlue,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { editingTagId = null },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Close,
                                                        contentDescription = stringResource(R.string.action_cancel),
                                                        tint = colors.secondaryText,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.LocalOffer,
                                                        contentDescription = null,
                                                        tint = colors.thingsBlue,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = tag.title,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                        color = colors.primaryText,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(colors.cardSurface.copy(alpha = 0.85f))
                                                            .padding(horizontal = 6.dp, vertical = 1.5.dp)
                                                    ) {
                                                        Text(
                                                            text = usageCount.toString(),
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            ),
                                                            color = colors.secondaryText
                                                        )
                                                    }
                                                    if (!tag.shortcut.isNullOrBlank()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(5.dp))
                                                                .background(colors.thingsBlueSoft)
                                                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                        ) {
                                                            Text(
                                                                text = "Ctrl+${tag.shortcut.uppercase(Locale.ROOT)}",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                                color = colors.thingsBlue
                                                            )
                                                        }
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                                                ) {
                                                    if (isManualOrderActive) {
                                                        IconButton(
                                                            onClick = {
                                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                onMoveTag(tag.id, true)
                                                            },
                                                            enabled = idx > 0,
                                                            modifier = Modifier.size(26.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.ArrowUpward,
                                                                contentDescription = null,
                                                                tint = if (idx > 0) colors.secondaryText else colors.tertiaryText,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                onMoveTag(tag.id, false)
                                                            },
                                                            enabled = idx < filteredTags.lastIndex,
                                                            modifier = Modifier.size(26.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.ArrowDownward,
                                                                contentDescription = null,
                                                                tint = if (idx < filteredTags.lastIndex) colors.secondaryText else colors.tertiaryText,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                    }

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
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Edit,
                                                            contentDescription = stringResource(R.string.tags_edit_title),
                                                            tint = colors.secondaryText,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }

                                                    if (allTags.size >= 2) {
                                                        Box {
                                                            IconButton(
                                                                onClick = { mergeMenuForTagId = tag.id },
                                                                modifier = Modifier.size(28.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Outlined.MergeType,
                                                                    contentDescription = stringResource(R.string.v25_tags_merge_action),
                                                                    tint = colors.thingsBlue,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            DropdownMenu(
                                                                expanded = mergeMenuForTagId == tag.id,
                                                                onDismissRequest = { mergeMenuForTagId = null },
                                                                modifier = Modifier.background(colors.cardElevatedSurface)
                                                            ) {
                                                                Text(
                                                                    text = stringResource(R.string.v25_tags_merge_target_prompt, tag.title),
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                    color = colors.secondaryText,
                                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                                )
                                                                HorizontalDivider(color = colors.hairlineDivider)
                                                                sortedTags.filter { it.id != tag.id }.forEach { targetTag ->
                                                                    DropdownMenuItem(
                                                                        text = {
                                                                            Text(
                                                                                text = "#${targetTag.title}",
                                                                                style = MaterialTheme.typography.bodyMedium,
                                                                                color = colors.primaryText
                                                                            )
                                                                        },
                                                                        leadingIcon = {
                                                                            Icon(
                                                                                imageVector = Icons.Outlined.LocalOffer,
                                                                                contentDescription = null,
                                                                                tint = colors.thingsBlue,
                                                                                modifier = Modifier.size(16.dp)
                                                                            )
                                                                        },
                                                                        onClick = {
                                                                            mergeMenuForTagId = null
                                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                            onMergeTags(tag.id, targetTag.id)
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            onDeleteTag(tag.id)
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
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Tag Assignment Dialog (`Assign Tags to To-Do`) inside any expanded Task Card or Multi-Selection Bar.
 * Combined Search or Create Tag input + constant-height locked viewport.
 */
@Composable
fun TaskTagPickerDialog(
    allTags: List<Tag>,
    selectedTagIds: List<String>,
    tagSortMode: String = "alpha",
    tagUsageCounts: Map<String, Int> = emptyMap(),
    onUpdateSelectedTags: (List<String>) -> Unit,
    onCreateAndAssignTag: (String) -> Unit,
    onOpenFullTagManager: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val pickerListHeight = minOf(240.dp, (screenHeightDp * 0.36f).dp).coerceAtLeast(150.dp)

    var currentSelection by remember(selectedTagIds) { mutableStateOf(selectedTagIds.toSet()) }
    var searchOrCreateField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }

    val sortedTags = remember(allTags, tagSortMode, tagUsageCounts) {
        sortTagsForMode(allTags, tagSortMode, tagUsageCounts)
    }
    val query = searchOrCreateField.text.trim()
    val filteredTags = remember(sortedTags, query) {
        if (query.isEmpty()) {
            sortedTags
        } else {
            sortedTags.filter { tag ->
                tag.title.contains(query, ignoreCase = true) ||
                    (tag.shortcut?.contains(query, ignoreCase = true) == true)
            }
        }
    }

    val submitSearchOrCreate = {
        val trimmed = searchOrCreateField.text.trim()
        if (trimmed.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val exactMatch = sortedTags.firstOrNull { it.title.trim().equals(trimmed, ignoreCase = true) }
            if (exactMatch != null) {
                if (exactMatch.id !in currentSelection) {
                    val updated = currentSelection + exactMatch.id
                    currentSelection = updated
                    onUpdateSelectedTags(updated.toList())
                }
            } else {
                onCreateAndAssignTag(trimmed)
            }
            searchOrCreateField = TextFieldValue(text = "", selection = TextRange(0))
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
                        ThingsHoverTooltip(text = stringResource(R.string.action_cancel)) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Combined Search or Create Tag Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchOrCreateField,
                            onValueChange = { searchOrCreateField = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.v25_tags_assign_search_placeholder),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = if (searchOrCreateField.text.isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = {
                                            searchOrCreateField = TextFieldValue(text = "", selection = TextRange(0))
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.action_clear),
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitSearchOrCreate() }),
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown &&
                                        (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                    ) {
                                        submitSearchOrCreate()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        val txt = searchOrCreateField.text
                                        searchOrCreateField = searchOrCreateField.copy(
                                            selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                        )
                                    }
                                }
                        )

                        IconButton(
                            onClick = { submitSearchOrCreate() },
                            enabled = searchOrCreateField.text.isNotBlank(),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (searchOrCreateField.text.isNotBlank()) colors.thingsBlue else colors.tagPillBg)
                                .size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.tags_add_button),
                                tint = if (searchOrCreateField.text.isNotBlank()) Color.White else colors.secondaryText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Constant-Height Locked List Viewport
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pickerListHeight)
                    ) {
                        if (filteredTags.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(
                                        if (allTags.isEmpty() && query.isEmpty()) {
                                            R.string.tags_empty_hint
                                        } else {
                                            R.string.v25_tags_no_matches
                                        }
                                    ),
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
                                items(filteredTags, key = { it.id }) { tag ->
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
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
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
                                                color = colors.primaryText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (!tag.shortcut.isNullOrBlank()) {
                                            ThingsTagPill(title = "Ctrl+${tag.shortcut.uppercase(Locale.ROOT)}")
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
}
