package com.culturedcode.things3.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.culturedcode.things3.R
import com.culturedcode.things3.data.repository.CloudSyncStatus
import com.culturedcode.things3.domain.model.Heading
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.domain.model.Tag
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.ui.components.ExpandableTaskCard
import com.culturedcode.things3.ui.components.GeminiAssistantModal
import com.culturedcode.things3.ui.components.GeminiLogbookReflectionCard
import com.culturedcode.things3.ui.components.MagicPlusDragState
import com.culturedcode.things3.ui.components.MagicPlusFab
import com.culturedcode.things3.ui.components.MagicPlusInsertionGap
import com.culturedcode.things3.ui.components.ProjectProgressRing
import com.culturedcode.things3.ui.components.SmartViewIcon
import com.culturedcode.things3.ui.components.SmartViewType
import com.culturedcode.things3.ui.components.ThingsAuthAndSyncModal
import com.culturedcode.things3.ui.components.ThingsNotificationSettingsModal
import com.culturedcode.things3.ui.components.ThingsTagManagerModal
import com.culturedcode.things3.ui.components.ThisEveningHeaderBadge
import com.culturedcode.things3.ui.theme.ThingsTheme
import com.culturedcode.things3.ui.viewmodel.ActiveNavigationTarget
import com.culturedcode.things3.ui.viewmodel.ThingsbookViewModel
import java.util.Locale

@Composable
fun ThingsSmartViewScreen(
    viewModel: ThingsbookViewModel,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val activeLocale = configuration.locales[0] ?: Locale.getDefault()

    val navTarget by viewModel.navigationTarget.collectAsState()
    val expandedTaskId by viewModel.expandedTaskId.collectAsState()
    val collapsedHeadingIds by viewModel.collapsedHeadingIds.collectAsState()

    val inboxTasks by viewModel.inboxTasks.collectAsState()
    val todayTasks by viewModel.todayDaytimeTasks.collectAsState()
    val eveningTasks by viewModel.thisEveningTasks.collectAsState()
    val upcomingTasks by viewModel.upcomingTasks.collectAsState()
    val anytimeTasks by viewModel.anytimeTasks.collectAsState()
    val somedayTasks by viewModel.somedayTasks.collectAsState()
    val logbookTasks by viewModel.logbookTasks.collectAsState()

    val activeProjects by viewModel.activeProjects.collectAsState()
    val projectHeadings by viewModel.activeProjectHeadings.collectAsState()
    val projectTasks by viewModel.activeProjectTasks.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedFilterTagId by viewModel.selectedFilterTagId.collectAsState()
    val isTagManagerOpen by viewModel.isTagManagerOpen.collectAsState()
    val isNotificationSettingsOpen by viewModel.isNotificationSettingsOpen.collectAsState()
    val dailyDigestConfig by viewModel.dailyDigestConfig.collectAsState()

    val isQuickFindOpen by viewModel.isQuickFindOpen.collectAsState()
    val quickFindQuery by viewModel.quickFindQuery.collectAsState()
    val quickFindTasks by viewModel.quickFindTaskResults.collectAsState()
    val quickFindProjects by viewModel.quickFindProjectResults.collectAsState()

    val isGeminiModalOpen by viewModel.isGeminiModalOpen.collectAsState()
    val isGeminiBusy by viewModel.isGeminiBusy.collectAsState()
    val magicBreakdownTaskId by viewModel.magicBreakdownTaskId.collectAsState()
    val logbookAiReview by viewModel.logbookAiReview.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isAuthModalOpen by viewModel.isAuthModalOpen.collectAsState()
    val isSigningIn by viewModel.isSigningIn.collectAsState()
    val authErrorMessage by viewModel.authErrorMessage.collectAsState()
    val currentUserIdentifier by viewModel.currentUserIdentifier.collectAsState()

    var magicDragState by remember { mutableStateOf(MagicPlusDragState()) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    val defaultNewTodoTitle = stringResource(R.string.entity_new_todo)
    val defaultNewHeadingTitle = stringResource(R.string.entity_new_heading)

    // Determine primary task list for current view, filtered by selected tag if active
    val rawPrimaryTasks: List<TaskItem> = when (val target = navTarget) {
        is ActiveNavigationTarget.SmartView -> when (target.type) {
            SmartViewType.INBOX -> inboxTasks
            SmartViewType.TODAY -> todayTasks
            SmartViewType.UPCOMING -> upcomingTasks
            SmartViewType.ANYTIME -> anytimeTasks
            SmartViewType.SOMEDAY -> somedayTasks
            SmartViewType.LOGBOOK -> logbookTasks
        }
        is ActiveNavigationTarget.ProjectDetail -> projectTasks
        is ActiveNavigationTarget.AreaDetail -> anytimeTasks
    }

    val primaryTasks: List<TaskItem> = remember(rawPrimaryTasks, selectedFilterTagId) {
        val tagId = selectedFilterTagId
        if (tagId == null) rawPrimaryTasks
        else rawPrimaryTasks.filter { task -> task.tags.any { it.id == tagId } }
    }

    val filteredEveningTasks: List<TaskItem> = remember(eveningTasks, selectedFilterTagId) {
        val tagId = selectedFilterTagId
        if (tagId == null) eveningTasks
        else eveningTasks.filter { task -> task.tags.any { it.id == tagId } }
    }

    val isProjectView = navTarget is ActiveNavigationTarget.ProjectDetail
    val currentProject: Project? = (navTarget as? ActiveNavigationTarget.ProjectDetail)?.let { detail ->
        activeProjects.find { it.id == detail.projectId }
    }

    // Adaptive Layout Detection: Tablets & Foldables (>= 700dp) vs. Android Phones (< 700dp)
    val isWideScreen = configuration.screenWidthDp >= 700
    val smartViewCounts = mapOf(
        SmartViewType.INBOX to inboxTasks.size,
        SmartViewType.TODAY to (todayTasks.size + eveningTasks.size),
        SmartViewType.UPCOMING to upcomingTasks.size,
        SmartViewType.ANYTIME to anytimeTasks.size,
        SmartViewType.SOMEDAY to somedayTasks.size,
        SmartViewType.LOGBOOK to logbookTasks.size
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed
                    when {
                        ctrlOrMeta && event.key == Key.F -> {
                            viewModel.setQuickFindOpen(true)
                            true
                        }
                        ctrlOrMeta && event.key == Key.N -> {
                            viewModel.createTaskAtPosition(primaryTasks, 0, defaultNewTodoTitle)
                            true
                        }
                        ctrlOrMeta && event.key == Key.G -> {
                            viewModel.setGeminiModalOpen(true)
                            true
                        }
                        event.key == Key.Escape -> {
                            when {
                                isQuickFindOpen -> {
                                    viewModel.setQuickFindOpen(false)
                                    true
                                }
                                isGeminiModalOpen -> {
                                    viewModel.setGeminiModalOpen(false)
                                    true
                                }
                                isAuthModalOpen -> {
                                    viewModel.setAuthModalOpen(false)
                                    true
                                }
                                isTagManagerOpen -> {
                                    viewModel.setTagManagerOpen(false)
                                    true
                                }
                                isNotificationSettingsOpen -> {
                                    viewModel.setNotificationSettingsOpen(false)
                                    true
                                }
                                expandedTaskId != null -> {
                                    viewModel.collapseExpandedTask()
                                    true
                                }
                                else -> false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            // Tap outside an expanded card collapses it smoothly
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (expandedTaskId != null) {
                        viewModel.collapseExpandedTask()
                    }
                }
            )
            // Swipe down gesture on any list opens Quick Find immediately
            .pointerInput(Unit) {
                var accumulatedPull = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulatedPull = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0) {
                            accumulatedPull += dragAmount
                            if (accumulatedPull > 110f && !isQuickFindOpen) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setQuickFindOpen(true)
                                accumulatedPull = 0f
                            }
                        }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (isWideScreen) {
                ThingsChromebookSidebar(
                    navTarget = navTarget,
                    smartViewCounts = smartViewCounts,
                    projects = activeProjects,
                    onSelectSmartView = { viewModel.selectSmartView(it) },
                    onSelectProject = { viewModel.selectProject(it) }
                )
                VerticalDivider(
                    color = colors.hairlineDivider,
                    thickness = 1.dp,
                    modifier = Modifier.fillMaxHeight()
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = if (isWideScreen) 840.dp else 2000.dp)
                        .fillMaxSize()
                ) {
                    // Top Utility & Navigation Bar (Quick Find, Gemini AI ✨, Tags 🏷️, Notifications 🔔, Language, Cloud Sync)
                    TopThingsbookBar(
                        syncStatus = syncStatus,
                        dailyDigestEnabled = dailyDigestConfig.enabled,
                        showLanguageMenu = showLanguageMenu,
                        onToggleLanguageMenu = { showLanguageMenu = it },
                        onSelectLanguage = { tag ->
                            showLanguageMenu = false
                            viewModel.switchAppLanguage(tag)
                        },
                        onOpenQuickFind = { viewModel.setQuickFindOpen(true) },
                        onOpenGeminiHub = { viewModel.setGeminiModalOpen(true) },
                        onOpenTagManager = { viewModel.setTagManagerOpen(true) },
                        onOpenNotificationSettings = { viewModel.setNotificationSettingsOpen(true) },
                        onManualSync = { viewModel.setAuthModalOpen(true) }
                    )

                    // On Android Phones (< 700dp), display the horizontal Smart Views & Projects Pill Strip
                    if (!isWideScreen) {
                        SmartViewNavigationStrip(
                            navTarget = navTarget,
                            projects = activeProjects,
                            onSelectSmartView = { viewModel.selectSmartView(it) },
                            onSelectProject = { viewModel.selectProject(it) }
                        )
                    }

                    // View Title Header (with Star / Pie-Chart Progress Ring)
                    ViewTitleHeader(
                        navTarget = navTarget,
                        currentProject = currentProject
                    )

                    // Interactive Tag Filter Strip + Tag Management Trigger
                    TagFilterAndManageStrip(
                        allTags = allTags,
                        selectedTagId = selectedFilterTagId,
                        onSelectTag = { viewModel.selectFilterTag(it) },
                        onOpenTagManager = { viewModel.setTagManagerOpen(true) }
                    )

                    // Main Task & Heading List with Inline Card Expansion + Magic Plus Insertion Gaps
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 110.dp)
                    ) {
                        // If viewing Logbook, show the Gemini Logbook Reflection Card at the top
                        if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.LOGBOOK)) {
                            item(key = "gemini_logbook_card") {
                                GeminiLogbookReflectionCard(
                                    review = logbookAiReview,
                                    isGenerating = isGeminiBusy,
                                    onGenerateReflection = {
                                        viewModel.generateGeminiLogbookReflection(activeLocale)
                                    },
                                    onAdoptSuggestedFocusTask = { title ->
                                        viewModel.adoptSuggestedFocusTaskForToday(title)
                                    }
                                )
                            }
                        }

                        // If viewing a Project, group tasks under collapsible Headings
                        if (isProjectView && projectHeadings.isNotEmpty()) {
                            projectHeadings.forEachIndexed { hIdx, heading ->
                                item(key = "heading_${heading.id}") {
                                    ProjectHeadingSectionHeader(
                                        heading = heading,
                                        isCollapsed = heading.id in collapsedHeadingIds,
                                        onToggleCollapse = { viewModel.toggleHeadingCollapsed(heading.id) }
                                    )
                                }

                                if (heading.id !in collapsedHeadingIds) {
                                    val tasksInHeading = primaryTasks.filter { it.headingId == heading.id }
                                    itemsIndexed(
                                        items = tasksInHeading,
                                        key = { _, task -> task.id }
                                    ) { index, task ->
                                        MagicPlusInsertionGap(
                                            visible = magicDragState.isDragging && magicDragState.dropIndex == index && hIdx == 0,
                                            dropMode = magicDragState.dropMode
                                        )
                                        ExpandableTaskCard(
                                            task = task,
                                            allTags = allTags,
                                            isExpanded = expandedTaskId == task.id,
                                            isGeneratingMagicBreakdown = magicBreakdownTaskId == task.id,
                                            onExpandToggle = { viewModel.toggleTaskExpansion(task.id) },
                                            onStatusChange = { viewModel.updateTaskStatus(task.id, it) },
                                            onTitleNotesChange = { t, n -> viewModel.updateTaskTitleAndNotes(task, t, n) },
                                            onScheduleChange = { s, w -> viewModel.updateTaskSchedule(task, s, w) },
                                            onScheduleWithReminderChange = { s, w, r ->
                                                viewModel.updateTaskScheduleWithReminder(task, s, w, r)
                                            },
                                            onDeadlineChange = { dl -> viewModel.updateTaskDeadline(task, dl) },
                                            onUpdateTaskTags = { tagIds -> viewModel.updateTaskTags(task, tagIds) },
                                            onCreateAndAssignTag = { newTag -> viewModel.createAndAssignTagToTask(task, newTag) },
                                            onOpenFullTagManager = { viewModel.setTagManagerOpen(true) },
                                            onAddChecklistItem = { itemTitle -> viewModel.addChecklistItem(task.id, itemTitle) },
                                            onToggleChecklistItem = { itemId, done -> viewModel.toggleChecklistItem(itemId, done) },
                                            onTriggerGeminiMagicBreakdown = {
                                                viewModel.triggerGeminiMagicBreakdown(task, activeLocale)
                                            },
                                            onDeleteTask = { viewModel.deleteTask(task.id) },
                                            locale = activeLocale
                                        )
                                    }
                                }
                            }
                        } else {
                            // Standard Smart View List with Magic Plus drop gaps between every item
                            itemsIndexed(
                                items = primaryTasks,
                                key = { _, task -> task.id }
                            ) { index, task ->
                                MagicPlusInsertionGap(
                                    visible = magicDragState.isDragging && magicDragState.dropIndex == index,
                                    dropMode = magicDragState.dropMode
                                )

                                ExpandableTaskCard(
                                    task = task,
                                    allTags = allTags,
                                    isExpanded = expandedTaskId == task.id,
                                    isGeneratingMagicBreakdown = magicBreakdownTaskId == task.id,
                                    onExpandToggle = { viewModel.toggleTaskExpansion(task.id) },
                                    onStatusChange = { viewModel.updateTaskStatus(task.id, it) },
                                    onTitleNotesChange = { t, n -> viewModel.updateTaskTitleAndNotes(task, t, n) },
                                    onScheduleChange = { s, w -> viewModel.updateTaskSchedule(task, s, w) },
                                    onScheduleWithReminderChange = { s, w, r ->
                                        viewModel.updateTaskScheduleWithReminder(task, s, w, r)
                                    },
                                    onDeadlineChange = { dl -> viewModel.updateTaskDeadline(task, dl) },
                                    onUpdateTaskTags = { tagIds -> viewModel.updateTaskTags(task, tagIds) },
                                    onCreateAndAssignTag = { newTag -> viewModel.createAndAssignTagToTask(task, newTag) },
                                    onOpenFullTagManager = { viewModel.setTagManagerOpen(true) },
                                    onAddChecklistItem = { itemTitle -> viewModel.addChecklistItem(task.id, itemTitle) },
                                    onToggleChecklistItem = { itemId, done -> viewModel.toggleChecklistItem(itemId, done) },
                                    onTriggerGeminiMagicBreakdown = {
                                        viewModel.triggerGeminiMagicBreakdown(task, activeLocale)
                                    },
                                    onDeleteTask = { viewModel.deleteTask(task.id) },
                                    locale = activeLocale
                                )
                            }

                            // Trailing Magic Plus drop slot at the end of the list
                            item(key = "trailing_magic_plus_gap") {
                                MagicPlusInsertionGap(
                                    visible = magicDragState.isDragging && magicDragState.dropIndex >= primaryTasks.size,
                                    dropMode = magicDragState.dropMode
                                )
                            }
                        }

                        // Today Smart View: "This Evening / Heute Abend / Stasera / Ce soir / Esta noche" Section
                        if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY) && filteredEveningTasks.isNotEmpty()) {
                            item(key = "this_evening_header") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                ) {
                                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    ThisEveningHeaderBadge()
                                }
                            }

                            items(
                                items = filteredEveningTasks,
                                key = { "evening_${it.id}" }
                            ) { eveningTask ->
                                ExpandableTaskCard(
                                    task = eveningTask,
                                    allTags = allTags,
                                    isExpanded = expandedTaskId == eveningTask.id,
                                    isGeneratingMagicBreakdown = magicBreakdownTaskId == eveningTask.id,
                                    onExpandToggle = { viewModel.toggleTaskExpansion(eveningTask.id) },
                                    onStatusChange = { viewModel.updateTaskStatus(eveningTask.id, it) },
                                    onTitleNotesChange = { t, n -> viewModel.updateTaskTitleAndNotes(eveningTask, t, n) },
                                    onScheduleChange = { s, w -> viewModel.updateTaskSchedule(eveningTask, s, w) },
                                    onScheduleWithReminderChange = { s, w, r ->
                                        viewModel.updateTaskScheduleWithReminder(eveningTask, s, w, r)
                                    },
                                    onDeadlineChange = { dl -> viewModel.updateTaskDeadline(eveningTask, dl) },
                                    onUpdateTaskTags = { tagIds -> viewModel.updateTaskTags(eveningTask, tagIds) },
                                    onCreateAndAssignTag = { newTag -> viewModel.createAndAssignTagToTask(eveningTask, newTag) },
                                    onOpenFullTagManager = { viewModel.setTagManagerOpen(true) },
                                    onAddChecklistItem = { itemTitle -> viewModel.addChecklistItem(eveningTask.id, itemTitle) },
                                    onToggleChecklistItem = { itemId, done -> viewModel.toggleChecklistItem(itemId, done) },
                                    onTriggerGeminiMagicBreakdown = {
                                        viewModel.triggerGeminiMagicBreakdown(eveningTask, activeLocale)
                                    },
                                    onDeleteTask = { viewModel.deleteTask(eveningTask.id) },
                                    locale = activeLocale
                                )
                            }
                        }
                    }
                }
            }
        }

        // Draggable "Magic Plus" Floating Action Button (bottom-right)
        MagicPlusFab(
            itemCount = primaryTasks.size,
            allowHeadingDrop = isProjectView,
            onQuickTapCreateTodo = {
                viewModel.createTaskAtPosition(primaryTasks, 0, defaultNewTodoTitle)
            },
            onDragStateChanged = { state -> magicDragState = state },
            onDropCreateTodoAt = { dropIdx ->
                viewModel.createTaskAtPosition(primaryTasks, dropIdx, defaultNewTodoTitle)
            },
            onDropCreateHeadingAt = { dropIdx ->
                viewModel.createHeadingInCurrentProject(dropIdx, defaultNewHeadingTitle)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 28.dp)
        )

        // Quick Find Modal (SQLite FTS5 unicode61 real-time search)
        if (isQuickFindOpen) {
            QuickFindDialog(
                query = quickFindQuery,
                taskResults = quickFindTasks,
                projectResults = quickFindProjects,
                onQueryChange = { viewModel.updateQuickFindQuery(it) },
                onSelectSmartView = {
                    viewModel.setQuickFindOpen(false)
                    viewModel.selectSmartView(it)
                },
                onSelectProject = {
                    viewModel.setQuickFindOpen(false)
                    viewModel.selectProject(it)
                },
                onSelectTask = { task ->
                    viewModel.setQuickFindOpen(false)
                    viewModel.toggleTaskExpansion(task.id)
                },
                onDismiss = { viewModel.setQuickFindOpen(false) }
            )
        }

        // Gemini AI Hub Modal (Brain Dump + Project Architect in EN, DE, IT, FR, ES)
        if (isGeminiModalOpen) {
            GeminiAssistantModal(
                isProcessing = isGeminiBusy,
                currentApiKey = geminiApiKey,
                onUpdateApiKey = { viewModel.updateGeminiApiKey(it) },
                onSubmitBrainDump = { raw -> viewModel.submitGeminiBrainDump(raw, activeLocale) },
                onSubmitProjectArchitect = { goal -> viewModel.submitGeminiProjectArchitect(goal, activeLocale) },
                onDismiss = { viewModel.setGeminiModalOpen(false) }
            )
        }

        // Built-In Thingsbook Cloud Sync & Multi-Device Auth Modal
        if (isAuthModalOpen) {
            ThingsAuthAndSyncModal(
                syncStatus = syncStatus,
                currentUserIdentifier = currentUserIdentifier,
                authErrorMessage = authErrorMessage,
                isSigningIn = isSigningIn,
                onRegisterEmailPassword = { email, pw -> viewModel.registerWithEmailPassword(email, pw) },
                onSignInEmailPassword = { email, pw -> viewModel.signInWithEmailPassword(email, pw) },
                onSignInWithGoogle = { viewModel.signInAnonymouslyAndSync() },
                onSignInAnonymously = { viewModel.signInAnonymouslyAndSync() },
                onSignOut = { viewModel.signOutCloud() },
                onTriggerDeltaSyncNow = { viewModel.triggerManualCloudSync() },
                onDismiss = { viewModel.setAuthModalOpen(false) }
            )
        }

        // Full Tag Management Modal
        if (isTagManagerOpen) {
            ThingsTagManagerModal(
                allTags = allTags,
                onCreateTag = { title, shortcut -> viewModel.createTag(title, shortcut) },
                onRenameTag = { id, title, shortcut -> viewModel.renameTag(id, title, shortcut) },
                onDeleteTag = { id -> viewModel.deleteTag(id) },
                onDismiss = { viewModel.setTagManagerOpen(false) }
            )
        }

        // Daily Active Tasks Notification & Reminder Settings Modal
        if (isNotificationSettingsOpen) {
            ThingsNotificationSettingsModal(
                config = dailyDigestConfig,
                onSaveDailyDigestConfig = { enabled, hr, min ->
                    viewModel.updateDailyDigestConfig(enabled, hr, min)
                },
                onSendTestNotificationNow = { viewModel.sendTestDailyDigestNotificationNow() },
                onDismiss = { viewModel.setNotificationSettingsOpen(false) }
            )
        }
    }
}

@Composable
private fun TopThingsbookBar(
    syncStatus: CloudSyncStatus,
    dailyDigestEnabled: Boolean,
    showLanguageMenu: Boolean,
    onToggleLanguageMenu: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onOpenQuickFind: () -> Unit,
    onOpenGeminiHub: () -> Unit,
    onOpenTagManager: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onManualSync: () -> Unit
) {
    val colors = ThingsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Quick Find Trigger Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.tagPillBg)
                .clickable(onClick = onOpenQuickFind)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.quick_find_title),
                tint = colors.secondaryText,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.quick_find_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryText
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Gemini AI Button ✨
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.thingsBlueSoft)
                    .clickable(onClick = onOpenGeminiHub)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = stringResource(R.string.gemini_hub_title),
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Gemini",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue
                )
            }

            // Tag Management Button 🏷️
            IconButton(
                onClick = onOpenTagManager,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalOffer,
                    contentDescription = stringResource(R.string.tags_manage_title),
                    tint = colors.secondaryText,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Daily & Task Notifications Button 🔔
            IconButton(
                onClick = onOpenNotificationSettings,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = stringResource(R.string.notifications_title),
                    tint = if (dailyDigestEnabled) colors.thingsBlue else colors.secondaryText,
                    modifier = Modifier.size(19.dp)
                )
            }

            // In-App Language Switcher (EN, DE, IT, FR, ES)
            Box {
                IconButton(
                    onClick = { onToggleLanguageMenu(true) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = stringResource(R.string.settings_language),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(19.dp)
                    )
                }

                DropdownMenu(
                    expanded = showLanguageMenu,
                    onDismissRequest = { onToggleLanguageMenu(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_system_default)) },
                        onClick = { onSelectLanguage("") }
                    )
                    DropdownMenuItem(
                        text = { Text("🇬🇧 " + stringResource(R.string.lang_en)) },
                        onClick = { onSelectLanguage("en") }
                    )
                    DropdownMenuItem(
                        text = { Text("🇩🇪 " + stringResource(R.string.lang_de)) },
                        onClick = { onSelectLanguage("de") }
                    )
                    DropdownMenuItem(
                        text = { Text("🇮🇹 " + stringResource(R.string.lang_it)) },
                        onClick = { onSelectLanguage("it") }
                    )
                    DropdownMenuItem(
                        text = { Text("🇫🇷 " + stringResource(R.string.lang_fr)) },
                        onClick = { onSelectLanguage("fr") }
                    )
                    DropdownMenuItem(
                        text = { Text("🇪🇸 " + stringResource(R.string.lang_es)) },
                        onClick = { onSelectLanguage("es") }
                    )
                }
            }

            // Cloud Delta-Sync Status Indicator
            IconButton(
                onClick = onManualSync,
                modifier = Modifier.size(34.dp)
            ) {
                val (syncIcon, syncTint) = when (syncStatus) {
                    CloudSyncStatus.SYNCED -> Icons.Outlined.CloudDone to colors.logbookGreen
                    CloudSyncStatus.SYNCING -> Icons.Outlined.CloudSync to colors.thingsBlue
                    CloudSyncStatus.OFFLINE -> Icons.Outlined.CloudOff to colors.secondaryText
                }
                Icon(
                    imageVector = syncIcon,
                    contentDescription = stringResource(R.string.sync_title),
                    tint = syncTint,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun TagFilterAndManageStrip(
    allTags: List<Tag>,
    selectedTagId: String?,
    onSelectTag: (String?) -> Unit,
    onOpenTagManager: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // "All" Chip
        item(key = "tag_filter_all") {
            val isAllSelected = selectedTagId == null
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isAllSelected) colors.thingsBlue else colors.tagPillBg)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!isAllSelected) onSelectTag(null)
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = stringResource(R.string.tags_filter_all),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isAllSelected) Color.White else colors.tagPillText
                )
            }
        }

        items(allTags, key = { "tag_chip_${it.id}" }) { tag ->
            val isSelected = selectedTagId == tag.id
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colors.thingsBlue else colors.tagPillBg)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTag(tag.id)
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = tag.title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) Color.White else colors.tagPillText
                )
            }
        }

        // Manage Tags Action Chip
        item(key = "tag_manage_trigger") {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.thingsBlueSoft)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenTagManager()
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalOffer,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = stringResource(R.string.tags_manage_title),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue
                )
            }
        }
    }
}

@Composable
private fun SmartViewNavigationStrip(
    navTarget: ActiveNavigationTarget,
    projects: List<Project>,
    onSelectSmartView: (SmartViewType) -> Unit,
    onSelectProject: (String) -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SmartViewType.entries) { viewType ->
            val selected = navTarget == ActiveNavigationTarget.SmartView(viewType)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) colors.cardSurface else Color.Transparent)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectSmartView(viewType)
                    }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmartViewIcon(viewType = viewType, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(viewType.titleRes),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selected) colors.primaryText else colors.secondaryText
                )
            }
        }

        items(projects, key = { "nav_proj_${it.id}" }) { project ->
            val selected = navTarget == ActiveNavigationTarget.ProjectDetail(project.id)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) colors.cardSurface else Color.Transparent)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectProject(project.id)
                    }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ProjectProgressRing(progress = project.progress, size = 16.dp)
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selected) colors.primaryText else colors.secondaryText
                )
            }
        }
    }
}

@Composable
private fun ViewTitleHeader(
    navTarget: ActiveNavigationTarget,
    currentProject: Project?
) {
    val colors = ThingsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (navTarget) {
                is ActiveNavigationTarget.SmartView -> {
                    SmartViewIcon(viewType = navTarget.type, modifier = Modifier.size(28.dp))
                    Text(
                        text = stringResource(navTarget.type.titleRes),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primaryText
                    )
                }
                is ActiveNavigationTarget.ProjectDetail -> {
                    ProjectProgressRing(
                        progress = currentProject?.progress ?: 0f,
                        size = 26.dp
                    )
                    Text(
                        text = currentProject?.title ?: stringResource(R.string.entity_project),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primaryText
                    )
                }
                is ActiveNavigationTarget.AreaDetail -> {
                    Text(
                        text = stringResource(R.string.entity_area),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primaryText
                    )
                }
            }
        }

        if (currentProject != null && currentProject.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentProject.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText
            )
        }
    }
}

@Composable
private fun ProjectHeadingSectionHeader(
    heading: Heading,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit
) {
    val colors = ThingsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleCollapse)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = heading.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.thingsBlue,
                    fontWeight = FontWeight.Bold
                )
            )
            Icon(
                imageVector = if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = stringResource(R.string.action_collapse_heading),
                tint = colors.secondaryText,
                modifier = Modifier.size(18.dp)
            )
        }
        HorizontalDivider(color = colors.thingsBlue.copy(alpha = 0.28f), thickness = 1.dp)
    }
}

@Composable
private fun QuickFindDialog(
    query: String,
    taskResults: List<TaskItem>,
    projectResults: List<Project>,
    onQueryChange: (String) -> Unit,
    onSelectSmartView: (SmartViewType) -> Unit,
    onSelectProject: (String) -> Unit,
    onSelectTask: (TaskItem) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.quick_find_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = colors.thingsBlue
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter localized Smart View names if query matches
                val matchingViews = SmartViewType.entries.filter { viewType ->
                    val localizedName = stringResource(viewType.titleRes)
                    query.isNotBlank() && localizedName.contains(query, ignoreCase = true)
                }

                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(matchingViews) { viewType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSmartView(viewType) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SmartViewIcon(viewType = viewType, modifier = Modifier.size(20.dp))
                            Text(
                                text = stringResource(viewType.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.primaryText
                            )
                        }
                    }

                    items(projectResults, key = { "qf_proj_${it.id}" }) { proj ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProject(proj.id) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProjectProgressRing(progress = proj.progress, size = 18.dp)
                            Text(
                                text = proj.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.primaryText
                            )
                        }
                    }

                    items(taskResults, key = { "qf_task_${it.id}" }) { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTask(task) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.primaryText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThingsChromebookSidebar(
    navTarget: ActiveNavigationTarget,
    smartViewCounts: Map<SmartViewType, Int>,
    projects: List<Project>,
    onSelectSmartView: (SmartViewType) -> Unit,
    onSelectProject: (String) -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .width(256.dp)
            .fillMaxHeight()
            .background(colors.sidebarBackground)
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.primaryText,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        SmartViewType.entries.forEach { viewType ->
            val selected = navTarget == ActiveNavigationTarget.SmartView(viewType)
            val count = smartViewCounts[viewType] ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) colors.thingsBlue.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectSmartView(viewType)
                    }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmartViewIcon(viewType = viewType, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(viewType.titleRes),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) colors.thingsBlue else colors.primaryText
                        )
                    )
                }
                if (count > 0 && viewType != SmartViewType.LOGBOOK) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.thingsBlue else colors.secondaryText
                    )
                }
            }
        }

        if (projects.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(projects, key = { "sidebar_proj_${it.id}" }) { project ->
                    val selected = navTarget == ActiveNavigationTarget.ProjectDetail(project.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) colors.thingsBlue.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectProject(project.id)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProjectProgressRing(progress = project.progress, size = 16.dp)
                        Text(
                            text = project.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) colors.thingsBlue else colors.primaryText
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

