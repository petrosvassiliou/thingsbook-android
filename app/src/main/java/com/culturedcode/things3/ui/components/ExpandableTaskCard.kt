package com.culturedcode.things3.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.culturedcode.things3.R
import com.culturedcode.things3.domain.model.ChecklistItem
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.Tag
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.domain.model.TaskStatus
import com.culturedcode.things3.ui.theme.ThingsTheme
import java.util.Locale

/**
 * The centerpiece of Things 3 UI/UX: Inline Card Expansion.
 *
 * - Tapping a collapsed row NEVER opens a new screen or bottom sheet.
 * - The row morphs in-place via spring physics (`spring(dampingRatio = 0.8f, stiffness = 400f)`)
 *   into a pure white card (`#FFFFFF` in Light / `#24262D` in Dark) with subtle elevation shadow,
 *   pushing neighboring rows smoothly apart.
 * - Includes integrated Calendar Date Picker, Task-Specific Time Reminder Picker, Deadline Calendar Picker,
 *   Tag Assignment Dialog, and **Gemini Magic Breakdown (✨)**.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandableTaskCard(
    task: TaskItem,
    allTags: List<Tag> = emptyList(),
    isExpanded: Boolean,
    isGeneratingMagicBreakdown: Boolean,
    onExpandToggle: () -> Unit,
    onStatusChange: (TaskStatus) -> Unit,
    onTitleNotesChange: (String, String) -> Unit,
    onScheduleChange: (ScheduleState, Long?) -> Unit,
    onScheduleWithReminderChange: ((ScheduleState, Long?, Long?) -> Unit)? = null,
    onDeadlineChange: (Long?) -> Unit,
    onUpdateTaskTags: ((List<String>) -> Unit)? = null,
    onCreateAndAssignTag: ((String) -> Unit)? = null,
    onOpenFullTagManager: (() -> Unit)? = null,
    onAddChecklistItem: (String) -> Unit,
    onToggleChecklistItem: (String, Boolean) -> Unit,
    onTriggerGeminiMagicBreakdown: () -> Unit,
    onDeleteTask: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault()
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    var editedTitle by remember(task.id, task.title) { mutableStateOf(task.title) }
    var editedNotes by remember(task.id, task.notes) { mutableStateOf(task.notes) }
    var newChecklistText by remember(task.id) { mutableStateOf("") }
    var showWhenDialog by remember(task.id) { mutableStateOf(false) }
    var showDeadlineDialog by remember(task.id) { mutableStateOf(false) }
    var showTagPickerDialog by remember(task.id) { mutableStateOf(false) }

    val horizontalPadding by animateDpAsState(
        targetValue = if (isExpanded) 12.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "cardHorizontalMargin"
    )

    val verticalPadding by animateDpAsState(
        targetValue = if (isExpanded) 8.dp else 1.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "cardVerticalMargin"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 14.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "cardCornerRadius"
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isExpanded) 10.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "cardShadowElevation"
    )

    val surfaceColor by animateColorAsState(
        targetValue = if (isExpanded) colors.cardElevatedSurface else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "cardSurfaceColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.14f),
                spotColor = Color.Black.copy(alpha = 0.18f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!isExpanded) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpandToggle()
                    }
                }
            ),
        color = surfaceColor,
        shape = RoundedCornerShape(cornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isExpanded) 16.dp else 8.dp,
                    vertical = if (isExpanded) 14.dp else 10.dp
                )
        ) {
            // Top Row: Animated Things Checkbox + Title + Inline Badges (when collapsed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThingsCheckbox(
                    status = task.status,
                    onStatusChange = onStatusChange
                )

                Spacer(modifier = Modifier.width(10.dp))

                if (isExpanded) {
                    BasicTextField(
                        value = editedTitle,
                        onValueChange = {
                            editedTitle = it
                            onTitleNotesChange(it, editedNotes)
                        },
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = colors.primaryText
                        ),
                        cursorBrush = SolidColor(colors.thingsBlue),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            Box {
                                if (editedTitle.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.placeholder_todo_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.tertiaryText
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                } else {
                    val isDoneOrCanceled = task.status != TaskStatus.OPEN
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (isDoneOrCanceled) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (isDoneOrCanceled) colors.tertiaryText else colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Collapsed inline metadata pills (Reminder time, Checklist progress, Tags, Deadline)
                if (!isExpanded) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        task.reminderTime?.let { rt ->
                            ThingsReminderBadge(reminderEpochMillis = rt)
                        }
                        if (task.checklistItems.isNotEmpty()) {
                            val doneCount = task.checklistItems.count { it.isCompleted }
                            ThingsTagPill(title = "$doneCount/${task.checklistItems.size}")
                        }
                        task.tags.take(2).forEach { tag ->
                            ThingsTagPill(title = tag.title)
                        }
                        task.deadline?.let { dl ->
                            ThingsDeadlineBadge(deadlineEpochMillis = dl, locale = locale)
                        }
                    }
                }
            }

            // Collapsed subtle notes preview if notes exist
            if (!isExpanded && task.notes.isNotBlank()) {
                Text(
                    text = task.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 38.dp, top = 2.dp)
                )
            }

            // Expanded Card Content (Notes, Checklist, Tags, When/Reminder, Deadline, Gemini Magic Breakdown)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                ) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 38.dp, top = 8.dp)
                ) {
                    // Markdown Notes Editor
                    BasicTextField(
                        value = editedNotes,
                        onValueChange = {
                            editedNotes = it
                            onTitleNotesChange(editedTitle, it)
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.secondaryText
                        ),
                        cursorBrush = SolidColor(colors.thingsBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (editedNotes.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.placeholder_notes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.tertiaryText
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Inline Checklist Items
                    if (task.checklistItems.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            task.checklistItems.forEach { item ->
                                InlineChecklistRow(
                                    item = item,
                                    onToggle = { checked ->
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleChecklistItem(item.id, checked)
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Add new checklist item inline field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Checklist,
                            contentDescription = null,
                            tint = colors.tertiaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = newChecklistText,
                            onValueChange = { newChecklistText = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.primaryText),
                            cursorBrush = SolidColor(colors.thingsBlue),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (newChecklistText.isNotBlank()) {
                                        onAddChecklistItem(newChecklistText.trim())
                                        newChecklistText = ""
                                    }
                                }
                            ),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                Box {
                                    if (newChecklistText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.placeholder_add_checklist_item),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.tertiaryText
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tags, When, Reminder & Deadline Badges Row
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ThingsWhenBadge(
                            scheduleState = task.scheduleState,
                            whenDateEpochMillis = task.whenDate,
                            locale = locale,
                            onClick = { showWhenDialog = true }
                        )

                        task.reminderTime?.let { rt ->
                            ThingsReminderBadge(
                                reminderEpochMillis = rt,
                                onClick = { showWhenDialog = true }
                            )
                        }

                        task.deadline?.let { dl ->
                            ThingsDeadlineBadge(
                                deadlineEpochMillis = dl,
                                locale = locale,
                                onClick = { showDeadlineDialog = true }
                            )
                        }

                        task.tags.forEach { tag ->
                            ThingsTagPill(
                                title = tag.title,
                                onClick = { showTagPickerDialog = true }
                            )
                        }

                        ThingsTagPill(
                            title = "+ " + stringResource(R.string.action_tags),
                            onClick = { showTagPickerDialog = true }
                        )
                    }

                    if (showWhenDialog) {
                        WhenNaturalDateDialog(
                            currentScheduleState = task.scheduleState,
                            currentWhenDate = task.whenDate,
                            currentReminderTime = task.reminderTime,
                            locale = locale,
                            onApplySchedule = { newState, newWhen ->
                                onScheduleChange(newState, newWhen)
                            },
                            onApplyScheduleWithReminder = { newState, newWhen, newReminder ->
                                if (onScheduleWithReminderChange != null) {
                                    onScheduleWithReminderChange(newState, newWhen, newReminder)
                                } else {
                                    onScheduleChange(newState, newWhen)
                                }
                            },
                            onDismiss = { showWhenDialog = false }
                        )
                    }

                    if (showDeadlineDialog) {
                        DeadlineDatePickerDialog(
                            currentDeadline = task.deadline,
                            locale = locale,
                            onApplyDeadline = { newDl -> onDeadlineChange(newDl) },
                            onDismiss = { showDeadlineDialog = false }
                        )
                    }

                    if (showTagPickerDialog) {
                        TaskTagPickerDialog(
                            allTags = allTags,
                            selectedTagIds = task.tags.map { it.id },
                            onUpdateSelectedTags = { updatedIds ->
                                onUpdateTaskTags?.invoke(updatedIds)
                            },
                            onCreateAndAssignTag = { newTagTitle ->
                                onCreateAndAssignTag?.invoke(newTagTitle)
                            },
                            onOpenFullTagManager = {
                                onOpenFullTagManager?.invoke()
                            },
                            onDismiss = { showTagPickerDialog = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom Card Action Bar: Gemini Magic Breakdown ✨, When Calendar, Tags, Deadline Calendar, Delete, Done
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Gemini Magic Breakdown Pill Button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.thingsBlueSoft)
                                .clickable(enabled = !isGeneratingMagicBreakdown) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTriggerGeminiMagicBreakdown()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isGeneratingMagicBreakdown) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.thingsBlue
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = stringResource(R.string.gemini_magic_breakdown),
                                    tint = colors.thingsBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.gemini_magic_breakdown),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.thingsBlue
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showWhenDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = stringResource(R.string.action_when),
                                    tint = if (task.whenDate != null || task.reminderTime != null) colors.thingsBlue else colors.secondaryText,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showTagPickerDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocalOffer,
                                    contentDescription = stringResource(R.string.action_tags),
                                    tint = if (task.tags.isNotEmpty()) colors.thingsBlue else colors.secondaryText,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showDeadlineDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Flag,
                                    contentDescription = stringResource(R.string.action_deadline),
                                    tint = if (task.deadline != null) colors.deadlineUrgentRed else colors.secondaryText,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDeleteTask()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onExpandToggle()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = stringResource(R.string.action_done),
                                    tint = colors.thingsBlue,
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

@Composable
private fun InlineChecklistRow(
    item: ChecklistItem,
    onToggle: (Boolean) -> Unit
) {
    val colors = ThingsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!item.isCompleted) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThingsCheckbox(
            status = if (item.isCompleted) TaskStatus.COMPLETED else TaskStatus.OPEN,
            onStatusChange = { status -> onToggle(status == TaskStatus.COMPLETED) },
            shape = ThingsCheckboxShape.CIRCLE,
            size = 16.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            ),
            color = if (item.isCompleted) colors.tertiaryText else colors.primaryText
        )
    }
}
