package com.culturedcode.things3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.culturedcode.things3.R
import com.culturedcode.things3.data.repository.CloudSyncStatus
import com.culturedcode.things3.data.repository.ThingsRepository
import com.culturedcode.things3.domain.model.Area
import com.culturedcode.things3.domain.model.Heading
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.domain.model.RecurrenceMode
import com.culturedcode.things3.domain.model.RecurrenceUnit
import com.culturedcode.things3.domain.model.Tag
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.ui.components.CreateOrEditProjectDialog
import com.culturedcode.things3.ui.components.DeadlineDatePickerDialog
import com.culturedcode.things3.ui.components.ExpandableTaskCard
import com.culturedcode.things3.ui.components.GeminiAssistantModal
import com.culturedcode.things3.ui.components.GeminiLogbookReflectionCard
import com.culturedcode.things3.ui.components.MagicPlusDragState
import com.culturedcode.things3.ui.components.MagicPlusFab
import com.culturedcode.things3.ui.components.MagicPlusInsertionGap
import com.culturedcode.things3.ui.components.ProjectProgressRing
import com.culturedcode.things3.ui.components.SmartViewIcon
import com.culturedcode.things3.ui.components.SmartViewType
import com.culturedcode.things3.ui.components.ThingsAreaManagerModal
import com.culturedcode.things3.ui.components.ThingsAuthAndSyncModal
import com.culturedcode.things3.ui.components.ThingsDeadlineBadge
import com.culturedcode.things3.ui.components.ThingsNotificationSettingsModal
import com.culturedcode.things3.ui.components.ThingsTagManagerModal
import com.culturedcode.things3.ui.components.ThingsTagPill
import com.culturedcode.things3.ui.components.ThisEveningHeaderBadge
import com.culturedcode.things3.ui.theme.ThingsTheme
import com.culturedcode.things3.ui.viewmodel.ActiveNavigationTarget
import com.culturedcode.things3.ui.viewmodel.ThingsbookViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

    val areas by viewModel.areas.collectAsState()
    val activeProjects by viewModel.activeProjects.collectAsState()
    val allHeadings by viewModel.allHeadings.collectAsState()
    val projectHeadings by viewModel.activeProjectHeadings.collectAsState()
    val projectTasks by viewModel.activeProjectTasks.collectAsState()
    val areaTasks by viewModel.activeAreaTasks.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedFilterTagId by viewModel.selectedFilterTagId.collectAsState()
    val isTagManagerOpen by viewModel.isTagManagerOpen.collectAsState()
    val isAreaManagerOpen by viewModel.isAreaManagerOpen.collectAsState()
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
    val authInfoMessage by viewModel.authInfoMessage.collectAsState()
    val currentUserIdentifier by viewModel.currentUserIdentifier.collectAsState()

    var magicDragState by remember { mutableStateOf(MagicPlusDragState()) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var projectDialogPreselectedAreaId by remember { mutableStateOf<String?>(null) }
    var editingProject by remember { mutableStateOf<Project?>(null) }

    val defaultNewTodoTitle = ""
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
        is ActiveNavigationTarget.AreaDetail -> areaTasks
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
    val isAreaView = navTarget is ActiveNavigationTarget.AreaDetail
    val currentProject: Project? = (navTarget as? ActiveNavigationTarget.ProjectDetail)?.let { detail ->
        activeProjects.find { it.id == detail.projectId }
    }
    val currentArea: Area? = (navTarget as? ActiveNavigationTarget.AreaDetail)?.let { detail ->
        areas.find { it.id == detail.areaId }
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

    @Composable
    fun RenderTaskCard(task: TaskItem) {
        ExpandableTaskCard(
            task = task,
            allTags = allTags,
            allAreas = areas,
            allProjects = activeProjects,
            allHeadings = allHeadings,
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
            onRecurrenceChange = { rule -> viewModel.updateTaskRecurrence(task.id, rule) },
            onMoveTaskToContainer = { pId, hId, aId ->
                viewModel.moveTaskToContainer(task.id, pId, hId, aId)
            },
            onUpdateTaskTags = { tagIds -> viewModel.updateTaskTags(task, tagIds) },
            onCreateAndAssignTag = { newTag -> viewModel.createAndAssignTagToTask(task, newTag) },
            onOpenFullTagManager = { viewModel.setTagManagerOpen(true) },
            onAddChecklistItem = { itemTitle -> viewModel.addChecklistItem(task.id, itemTitle) },
            onToggleChecklistItem = { itemId, done -> viewModel.toggleChecklistItem(itemId, done) },
            onRenameChecklistItem = { itemId, newTitle -> viewModel.renameChecklistItem(itemId, newTitle) },
            onMoveChecklistItem = { itemId, moveUp -> viewModel.moveChecklistItem(task.id, itemId, moveUp) },
            onDeleteChecklistItem = { itemId -> viewModel.deleteChecklistItem(itemId) },
            onTriggerGeminiMagicBreakdown = {
                viewModel.triggerGeminiMagicBreakdown(task, activeLocale)
            },
            onDeleteTask = { viewModel.deleteTask(task.id) },
            locale = activeLocale
        )
    }

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
                                isAreaManagerOpen -> {
                                    viewModel.setAreaManagerOpen(false)
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
                    areas = areas,
                    projects = activeProjects,
                    onSelectSmartView = { viewModel.selectSmartView(it) },
                    onSelectArea = { viewModel.selectArea(it) },
                    onSelectProject = { viewModel.selectProject(it) },
                    onOpenAreaManager = { viewModel.setAreaManagerOpen(true) },
                    onCreateNewProject = { areaId ->
                        projectDialogPreselectedAreaId = areaId
                        editingProject = null
                        showCreateProjectDialog = true
                    }
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
                    // Top Utility & Navigation Bar (Quick Find, Gemini AI ✨, Areas 📦, Tags 🏷️, Notifications 🔔, Language, Cloud Sync)
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
                        onOpenAreaManager = { viewModel.setAreaManagerOpen(true) },
                        onOpenTagManager = { viewModel.setTagManagerOpen(true) },
                        onOpenNotificationSettings = { viewModel.setNotificationSettingsOpen(true) },
                        onManualSync = { viewModel.setAuthModalOpen(true) }
                    )

                    // On Android Phones (< 700dp), display the horizontal Smart Views, Areas (Work, Home) & Projects Strip
                    if (!isWideScreen) {
                        SmartViewNavigationStrip(
                            navTarget = navTarget,
                            areas = areas,
                            projects = activeProjects,
                            onSelectSmartView = { viewModel.selectSmartView(it) },
                            onSelectArea = { viewModel.selectArea(it) },
                            onSelectProject = { viewModel.selectProject(it) },
                            onOpenAreaManager = { viewModel.setAreaManagerOpen(true) },
                            onCreateNewProject = {
                                projectDialogPreselectedAreaId = currentArea?.id
                                editingProject = null
                                showCreateProjectDialog = true
                            }
                        )
                    }

                    // View Title Header (Smart View, Project with Target Date / Deadline, or Area like Work / Home)
                    ViewTitleHeader(
                        navTarget = navTarget,
                        currentProject = currentProject,
                        currentArea = currentArea,
                        areas = areas,
                        projects = activeProjects,
                        locale = activeLocale,
                        onUpdateProjectDeadline = { pId, dl -> viewModel.updateProjectDeadline(pId, dl) },
                        onUpdateProjectArea = { pId, aId -> viewModel.updateProjectArea(pId, aId) },
                        onEditProject = { proj ->
                            editingProject = proj
                            showCreateProjectDialog = true
                        },
                        onCompleteProject = { pId -> viewModel.completeProject(pId) },
                        onMoveProject = { pId, up -> viewModel.moveProject(pId, up) },
                        onDeleteProject = { pId -> viewModel.deleteProject(pId) },
                        onAddTaskInCurrentView = {
                            viewModel.createTaskAtPosition(primaryTasks, 0, defaultNewTodoTitle)
                        },
                        onAddHeadingInProject = {
                            viewModel.createHeadingInCurrentProject(projectHeadings.size, defaultNewHeadingTitle)
                        },
                        onRenameArea = { aId, newName -> viewModel.renameArea(aId, newName) },
                        onMoveArea = { aId, up -> viewModel.moveArea(aId, up) },
                        onDeleteArea = { aId -> viewModel.deleteArea(aId) },
                        onCreateProjectInArea = { areaId ->
                            projectDialogPreselectedAreaId = areaId
                            editingProject = null
                            showCreateProjectDialog = true
                        },
                        onOpenAreaManager = { viewModel.setAreaManagerOpen(true) }
                    )

                    // Interactive Tag Filter Strip + Tag Management Trigger
                    TagFilterAndManageStrip(
                        allTags = allTags,
                        selectedTagId = selectedFilterTagId,
                        onSelectTag = { viewModel.selectFilterTag(it) },
                        onOpenTagManager = { viewModel.setTagManagerOpen(true) }
                    )

                    // Main Task, Area Projects & Heading List with Inline Card Expansion + Magic Plus Insertion Gaps
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

                        // If viewing an Area (e.g. Work or Home), show the Area's Projects section first!
                        if (isAreaView && currentArea != null) {
                            val areaProjects = activeProjects.filter { it.areaId == currentArea.id }
                            item(key = "area_projects_header_${currentArea.id}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${stringResource(R.string.entity_projects)} • ${currentArea.title} (${areaProjects.size})",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = colors.thingsBlue,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.thingsBlueSoft)
                                            .clickable {
                                                projectDialogPreselectedAreaId = currentArea.id
                                                editingProject = null
                                                showCreateProjectDialog = true
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = null,
                                            tint = colors.thingsBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.entity_new_project),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.thingsBlue
                                        )
                                    }
                                }
                            }

                            itemsIndexed(
                                items = areaProjects,
                                key = { _, p -> "area_proj_card_${p.id}" }
                            ) { idx, proj ->
                                AreaProjectOverviewRow(
                                    project = proj,
                                    canMoveUp = idx > 0,
                                    canMoveDown = idx < areaProjects.lastIndex,
                                    locale = activeLocale,
                                    onOpenProject = { viewModel.selectProject(proj.id) },
                                    onEditProject = {
                                        editingProject = proj
                                        showCreateProjectDialog = true
                                    },
                                    onUpdateDeadline = { dl -> viewModel.updateProjectDeadline(proj.id, dl) },
                                    onMoveUp = { viewModel.moveProject(proj.id, true) },
                                    onMoveDown = { viewModel.moveProject(proj.id, false) }
                                )
                            }

                            item(key = "area_tasks_divider_${currentArea.id}") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${stringResource(R.string.entity_todo)} • ${currentArea.title} (${primaryTasks.size})",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = colors.secondaryText,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }

                        // If viewing a Project, render ungrouped tasks first, then collapsible Headings!
                        if (isProjectView && projectHeadings.isNotEmpty()) {
                            val ungroupedTasks = primaryTasks.filter { it.headingId == null }
                            itemsIndexed(
                                items = ungroupedTasks,
                                key = { _, task -> "ungrouped_${task.id}" }
                            ) { index, task ->
                                MagicPlusInsertionGap(
                                    visible = magicDragState.isDragging && magicDragState.dropIndex == index,
                                    dropMode = magicDragState.dropMode
                                )
                                RenderTaskCard(task = task)
                            }

                            projectHeadings.forEachIndexed { hIdx, heading ->
                                item(key = "heading_${heading.id}") {
                                    ProjectHeadingSectionHeader(
                                        heading = heading,
                                        isCollapsed = heading.id in collapsedHeadingIds,
                                        onToggleCollapse = { viewModel.toggleHeadingCollapsed(heading.id) },
                                        onRenameHeading = { newTitle -> viewModel.renameHeading(heading.id, newTitle) },
                                        onDeleteHeading = { viewModel.deleteHeading(heading.id) }
                                    )
                                }

                                if (heading.id !in collapsedHeadingIds) {
                                    val tasksInHeading = primaryTasks.filter { it.headingId == heading.id }
                                    itemsIndexed(
                                        items = tasksInHeading,
                                        key = { _, task -> task.id }
                                    ) { index, task ->
                                        MagicPlusInsertionGap(
                                            visible = magicDragState.isDragging && magicDragState.dropIndex == index && hIdx == 0 && ungroupedTasks.isEmpty(),
                                            dropMode = magicDragState.dropMode
                                        )
                                        RenderTaskCard(task = task)
                                    }
                                }
                            }
                        } else if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.UPCOMING)) {
                            // Upcoming Smart View ("Geplant"): Group tasks chronologically by day ("Gruppierung pro Tag")
                            val zone = ZoneId.systemDefault()
                            val todayDate = LocalDate.now(zone)
                            val groupedUpcoming = primaryTasks
                                .groupBy { task -> computeUpcomingTargetDate(task, todayDate, zone) }
                                .toSortedMap()

                            groupedUpcoming.entries.forEachIndexed { groupIdx, (targetDate, dayTasks) ->
                                item(key = "upcoming_day_header_${targetDate.toEpochDay()}") {
                                    UpcomingDayGroupHeader(
                                        date = targetDate,
                                        todayDate = todayDate,
                                        locale = activeLocale,
                                        isFirstGroup = groupIdx == 0
                                    )
                                }
                                itemsIndexed(
                                    items = dayTasks,
                                    key = { _, task -> "upcoming_${targetDate.toEpochDay()}_${task.id}" }
                                ) { index, task ->
                                    MagicPlusInsertionGap(
                                        visible = magicDragState.isDragging && magicDragState.dropIndex == index && groupIdx == 0,
                                        dropMode = magicDragState.dropMode
                                    )
                                    RenderTaskCard(task = task)
                                }
                            }

                            // Trailing Magic Plus drop slot at the end of the list
                            item(key = "trailing_magic_plus_gap") {
                                MagicPlusInsertionGap(
                                    visible = magicDragState.isDragging && magicDragState.dropIndex >= primaryTasks.size,
                                    dropMode = magicDragState.dropMode
                                )
                            }
                        } else {
                            // Standard Smart View / Area Task List with Magic Plus drop gaps between every item
                            itemsIndexed(
                                items = primaryTasks,
                                key = { _, task -> task.id }
                            ) { index, task ->
                                MagicPlusInsertionGap(
                                    visible = magicDragState.isDragging && magicDragState.dropIndex == index,
                                    dropMode = magicDragState.dropMode
                                )
                                RenderTaskCard(task = task)
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
                                RenderTaskCard(task = eveningTask)
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
                authInfoMessage = authInfoMessage,
                isSigningIn = isSigningIn,
                onRegisterEmailPassword = { email, pw -> viewModel.registerWithEmailPassword(email, pw) },
                onSignInEmailPassword = { email, pw -> viewModel.signInWithEmailPassword(email, pw) },
                onResetPassword = { email -> viewModel.sendPasswordReset(email) },
                onSignInWithGoogle = { enteredEmail -> viewModel.signInWithGoogleAccount(enteredEmail) },
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

        // Areas of Responsibility Manager Modal (Work, Home, up to 6 Areas)
        if (isAreaManagerOpen) {
            ThingsAreaManagerModal(
                areas = areas,
                projects = activeProjects,
                onCreateArea = { title -> viewModel.createArea(title) },
                onRenameArea = { id, newTitle -> viewModel.renameArea(id, newTitle) },
                onMoveArea = { id, moveUp -> viewModel.moveArea(id, moveUp) },
                onDeleteArea = { id -> viewModel.deleteArea(id) },
                onOpenArea = { id -> viewModel.selectArea(id) },
                onDismiss = { viewModel.setAreaManagerOpen(false) }
            )
        }

        // Create or Edit Project Dialog (with Target Date / Deadline & Area Assignment)
        if (showCreateProjectDialog) {
            CreateOrEditProjectDialog(
                existingProject = editingProject,
                areas = areas,
                preselectedAreaId = projectDialogPreselectedAreaId,
                locale = activeLocale,
                onSaveProject = { title, notes, areaId, deadline ->
                    viewModel.saveProject(
                        id = editingProject?.id,
                        title = title,
                        notes = notes,
                        areaId = areaId,
                        deadlineEpochMillis = deadline,
                        navigateToProject = editingProject == null
                    )
                },
                onDismiss = {
                    showCreateProjectDialog = false
                    editingProject = null
                }
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
private fun AreaProjectOverviewRow(
    project: Project,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    locale: Locale,
    onOpenProject: () -> Unit,
    onEditProject: () -> Unit,
    onUpdateDeadline: (Long?) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val colors = ThingsTheme.colors
    var showDeadlinePicker by remember(project.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenProject),
        color = colors.cardElevatedSurface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProjectProgressRing(progress = project.progress, size = 20.dp)
                Column {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText
                    )
                    Text(
                        text = "${project.completedTaskCount}/${project.totalTaskCount} • ${stringResource(R.string.entity_todo)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (project.deadline != null) {
                    ThingsDeadlineBadge(
                        deadlineEpochMillis = project.deadline,
                        locale = locale,
                        onClick = { showDeadlinePicker = true }
                    )
                } else {
                    ThingsTagPill(
                        title = "🏁 ${stringResource(R.string.project_target_date_label)}",
                        onClick = { showDeadlinePicker = true }
                    )
                }

                if (canMoveUp) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowUpward,
                            contentDescription = stringResource(R.string.action_move),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (canMoveDown) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDownward,
                            contentDescription = stringResource(R.string.action_move),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                IconButton(onClick = onEditProject, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.project_edit_title),
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }

    if (showDeadlinePicker) {
        DeadlineDatePickerDialog(
            currentDeadline = project.deadline,
            locale = locale,
            onApplyDeadline = onUpdateDeadline,
            onDismiss = { showDeadlinePicker = false }
        )
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
    onOpenAreaManager: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(2.dp)
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

            // Areas Manager Button 📦 (Work, Home, max 6)
            IconButton(
                onClick = onOpenAreaManager,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = stringResource(R.string.areas_manage_title),
                    tint = colors.anytimeTeal,
                    modifier = Modifier.size(19.dp)
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
            .padding(top = 6.dp, bottom = 8.dp),
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
    areas: List<Area>,
    projects: List<Project>,
    onSelectSmartView: (SmartViewType) -> Unit,
    onSelectArea: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onOpenAreaManager: () -> Unit,
    onCreateNewProject: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val areasLabel = stringResource(R.string.entity_areas)
    val projectLabel = stringResource(R.string.entity_project)

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // 1. Smart Views
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

        // 2. Areas (Work, Home, ... up to 6)
        items(areas, key = { "nav_area_${it.id}" }) { area ->
            val selected = navTarget == ActiveNavigationTarget.AreaDetail(area.id)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) colors.cardSurface else colors.tagPillBg.copy(alpha = 0.6f))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectArea(area.id)
                    }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = area.title,
                    tint = colors.anytimeTeal,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = area.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = if (selected) colors.primaryText else colors.secondaryText
                )
            }
        }

        // 3. Projects (with Progress Ring & Target Date Flag)
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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProjectProgressRing(progress = project.progress, size = 16.dp)
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selected) colors.primaryText else colors.secondaryText
                )
                if (project.deadline != null) {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = null,
                        tint = colors.deadlineUrgentRed,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // 4. "+ Project" Quick Trigger
        item(key = "nav_add_project") {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.thingsBlueSoft)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCreateNewProject()
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = projectLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue
                )
            }
        }

        // 5. "Areas / Bereiche / Aree / Domaines / Áreas (X/6)" Quick Trigger
        item(key = "nav_manage_areas") {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.tagPillBg)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenAreaManager()
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = colors.anytimeTeal,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$areasLabel (${areas.size}/${ThingsRepository.MAX_AREAS})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.primaryText
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewTitleHeader(
    navTarget: ActiveNavigationTarget,
    currentProject: Project?,
    currentArea: Area?,
    areas: List<Area>,
    projects: List<Project>,
    locale: Locale,
    onUpdateProjectDeadline: (String, Long?) -> Unit,
    onUpdateProjectArea: (String, String?) -> Unit,
    onEditProject: (Project) -> Unit,
    onCompleteProject: (String) -> Unit,
    onMoveProject: (String, Boolean) -> Unit,
    onDeleteProject: (String) -> Unit,
    onAddTaskInCurrentView: () -> Unit,
    onAddHeadingInProject: () -> Unit,
    onRenameArea: (String, String) -> Unit,
    onMoveArea: (String, Boolean) -> Unit,
    onDeleteArea: (String) -> Unit,
    onCreateProjectInArea: (String) -> Unit,
    onOpenAreaManager: () -> Unit
) {
    val colors = ThingsTheme.colors
    val zone = ZoneId.systemDefault()
    var showProjectDeadlineDialog by remember(currentProject?.id) { mutableStateOf(false) }
    var showProjectAreaMenu by remember(currentProject?.id) { mutableStateOf(false) }
    var isRenamingAreaInline by remember(currentArea?.id) { mutableStateOf(false) }
    var inlineAreaTitle by remember(currentArea?.id, currentArea?.title) {
        mutableStateOf(currentArea?.title ?: "")
    }

    val areaWord = stringResource(R.string.entity_area)
    val areasWord = stringResource(R.string.entity_areas)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
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
                        Icon(
                            imageVector = Icons.Outlined.Inventory2,
                            contentDescription = null,
                            tint = colors.anytimeTeal,
                            modifier = Modifier.size(26.dp)
                        )
                        if (isRenamingAreaInline && currentArea != null) {
                            OutlinedTextField(
                                value = inlineAreaTitle,
                                onValueChange = { inlineAreaTitle = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (inlineAreaTitle.isNotBlank()) {
                                        onRenameArea(currentArea.id, inlineAreaTitle.trim())
                                        isRenamingAreaInline = false
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = stringResource(R.string.action_save),
                                    tint = colors.thingsBlue
                                )
                            }
                            IconButton(onClick = { isRenamingAreaInline = false }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    tint = colors.secondaryText
                                )
                            }
                        } else {
                            Text(
                                text = currentArea?.title ?: stringResource(R.string.entity_area),
                                style = MaterialTheme.typography.headlineLarge,
                                color = colors.primaryText
                            )
                        }
                    }
                }
            }

            // Quick Actions for Project or Area in Header
            if (currentProject != null) {
                val projIdx = projects.indexOfFirst { it.id == currentProject.id }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (projIdx > 0) {
                        IconButton(
                            onClick = { onMoveProject(currentProject.id, true) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = stringResource(R.string.action_move),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (projIdx != -1 && projIdx < projects.lastIndex) {
                        IconButton(
                            onClick = { onMoveProject(currentProject.id, false) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDownward,
                                contentDescription = stringResource(R.string.action_move),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onEditProject(currentProject) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.project_edit_title),
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = { onCompleteProject(currentProject.id) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircleOutline,
                            contentDescription = stringResource(R.string.action_complete),
                            tint = colors.logbookGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDeleteProject(currentProject.id) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = colors.deadlineUrgentRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else if (currentArea != null && !isRenamingAreaInline) {
                val areaIdx = areas.indexOfFirst { it.id == currentArea.id }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (areaIdx > 0) {
                        IconButton(
                            onClick = { onMoveArea(currentArea.id, true) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = stringResource(R.string.action_move),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (areaIdx != -1 && areaIdx < areas.lastIndex) {
                        IconButton(
                            onClick = { onMoveArea(currentArea.id, false) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDownward,
                                contentDescription = stringResource(R.string.action_move),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            inlineAreaTitle = currentArea.title
                            isRenamingAreaInline = true
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.areas_rename_label),
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDeleteArea(currentArea.id) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = colors.deadlineUrgentRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Project Notes, Target Date (Zieldatum / Deadline), Area Assignment & Quick Buttons
        if (currentProject != null) {
            if (currentProject.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentProject.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Project Target Date (Zieldatum) Badge — opens compact calendar picker on tap!
                if (currentProject.deadline != null) {
                    val formattedTargetDate = Instant.ofEpochMilli(currentProject.deadline)
                        .atZone(zone)
                        .toLocalDate()
                        .format(DateTimeFormatter.ofPattern("d. MMM yyyy", locale))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ThingsDeadlineBadge(
                            deadlineEpochMillis = currentProject.deadline,
                            locale = locale,
                            onClick = { showProjectDeadlineDialog = true }
                        )
                        ThingsTagPill(
                            title = "${stringResource(R.string.project_target_date_label)}: $formattedTargetDate",
                            onClick = { showProjectDeadlineDialog = true }
                        )
                    }
                } else {
                    ThingsTagPill(
                        title = "🏁 + ${stringResource(R.string.project_set_target_date)}",
                        onClick = { showProjectDeadlineDialog = true }
                    )
                }

                // Project Area Assignment Chip ("📦 Work" / "📦 Home")
                val projectArea = areas.find { it.id == currentProject.areaId }
                Box {
                    ThingsTagPill(
                        title = if (projectArea != null) {
                            "📦 $areaWord: ${projectArea.title}"
                        } else {
                            "📦 ${stringResource(R.string.areas_assign_chip)}"
                        },
                        onClick = { showProjectAreaMenu = true }
                    )
                    DropdownMenu(
                        expanded = showProjectAreaMenu,
                        onDismissRequest = { showProjectAreaMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.areas_none)) },
                            onClick = {
                                showProjectAreaMenu = false
                                onUpdateProjectArea(currentProject.id, null)
                            }
                        )
                        areas.forEach { area ->
                            DropdownMenuItem(
                                text = { Text("📦 ${area.title}") },
                                onClick = {
                                    showProjectAreaMenu = false
                                    onUpdateProjectArea(currentProject.id, area.id)
                                }
                            )
                        }
                    }
                }

                // "+ To-Do / Aufgabe" in Project
                ThingsTagPill(
                    title = "+ ${stringResource(R.string.entity_todo)}",
                    onClick = onAddTaskInCurrentView
                )

                // "+ Heading / Überschrift" in Project
                ThingsTagPill(
                    title = "+ ${stringResource(R.string.entity_heading)}",
                    onClick = onAddHeadingInProject
                )
            }

            if (showProjectDeadlineDialog) {
                DeadlineDatePickerDialog(
                    currentDeadline = currentProject.deadline,
                    locale = locale,
                    onApplyDeadline = { newDl ->
                        onUpdateProjectDeadline(currentProject.id, newDl)
                    },
                    onDismiss = { showProjectDeadlineDialog = false }
                )
            }
        }

        // Area Quick Action Bar when viewing an Area (e.g. Work or Home)
        if (currentArea != null) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThingsTagPill(
                    title = "+ ${stringResource(R.string.entity_new_project)} (${currentArea.title})",
                    onClick = { onCreateProjectInArea(currentArea.id) }
                )
                ThingsTagPill(
                    title = "+ ${stringResource(R.string.entity_new_todo)} (${currentArea.title})",
                    onClick = onAddTaskInCurrentView
                )
                ThingsTagPill(
                    title = "⚙️ $areasWord (${areas.size}/${ThingsRepository.MAX_AREAS})",
                    onClick = onOpenAreaManager
                )
            }
        }
    }
}

@Composable
private fun ProjectHeadingSectionHeader(
    heading: Heading,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onRenameHeading: (String) -> Unit,
    onDeleteHeading: () -> Unit
) {
    val colors = ThingsTheme.colors
    var isEditing by remember(heading.id) { mutableStateOf(false) }
    var editedTitle by remember(heading.id, heading.title) { mutableStateOf(heading.title) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (isEditing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (editedTitle.isNotBlank()) {
                            onRenameHeading(editedTitle.trim())
                            isEditing = false
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = stringResource(R.string.action_save),
                        tint = colors.thingsBlue
                    )
                }
                IconButton(onClick = { isEditing = false }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = colors.secondaryText
                    )
                }
            }
        } else {
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
                    ),
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            editedTitle = heading.title
                            isEditing = true
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.entity_heading),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteHeading,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        contentDescription = stringResource(R.string.action_collapse_heading),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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
    areas: List<Area>,
    projects: List<Project>,
    onSelectSmartView: (SmartViewType) -> Unit,
    onSelectArea: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onOpenAreaManager: () -> Unit,
    onCreateNewProject: (String?) -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val areasLabel = stringResource(R.string.entity_areas)

    Column(
        modifier = Modifier
            .width(264.dp)
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

        Spacer(modifier = Modifier.height(4.dp))

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
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
        Spacer(modifier = Modifier.height(6.dp))

        // Areas & Projects Hierarchy (Things for Mac Sidebar style)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(areas, key = { "sidebar_area_${it.id}" }) { area ->
                val isAreaSelected = navTarget == ActiveNavigationTarget.AreaDetail(area.id)
                val areaProjects = projects.filter { it.areaId == area.id }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isAreaSelected) colors.thingsBlue.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectArea(area.id)
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Inventory2,
                                contentDescription = area.title,
                                tint = colors.anytimeTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = area.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAreaSelected) colors.thingsBlue else colors.primaryText
                                )
                            )
                        }
                        IconButton(
                            onClick = { onCreateNewProject(area.id) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.entity_new_project),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Indented Projects inside this Area
                    areaProjects.forEach { project ->
                        val selected = navTarget == ActiveNavigationTarget.ProjectDetail(project.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) colors.thingsBlue.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectProject(project.id)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProjectProgressRing(progress = project.progress, size = 15.dp)
                                Text(
                                    text = project.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) colors.thingsBlue else colors.primaryText
                                    ),
                                    maxLines = 1
                                )
                            }
                            if (project.deadline != null) {
                                Icon(
                                    imageVector = Icons.Outlined.Flag,
                                    contentDescription = null,
                                    tint = colors.deadlineUrgentRed,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Unassigned Projects (not in any Area)
            val unassignedProjects = projects.filter { it.areaId == null }
            items(unassignedProjects, key = { "sidebar_proj_unassigned_${it.id}" }) { project ->
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
                        .padding(horizontal = 10.dp, vertical = 7.dp),
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

        // Bottom Sidebar Actions: "+ New Project" & "Areas / Bereiche / Aree / Domaines / Áreas (X/6)"
        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCreateNewProject(null) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.entity_new_project),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenAreaManager)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = colors.anytimeTeal,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$areasLabel (${areas.size}/${ThingsRepository.MAX_AREAS})",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
            }
        }
    }
}

private fun computeUpcomingTargetDate(
    task: TaskItem,
    todayDate: LocalDate,
    zone: ZoneId
): LocalDate {
    val whenLocal = task.whenDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val deadlineLocal = task.deadline?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    if (whenLocal != null && whenLocal.isAfter(todayDate)) {
        return whenLocal
    }

    val rule = task.recurrenceRule
    if (rule != null) {
        val safeInterval = rule.interval.coerceAtLeast(1).toLong()
        val baseDate = if (whenLocal != null && !whenLocal.isAfter(todayDate)) todayDate else (whenLocal ?: todayDate)
        return when (rule.unit) {
            RecurrenceUnit.DAYS -> baseDate.plusDays(safeInterval)
            RecurrenceUnit.WEEKS -> {
                if (rule.mode == RecurrenceMode.FIXED_SCHEDULE && rule.daysOfWeek.isNotEmpty()) {
                    val sortedDays = rule.daysOfWeek.map { it.coerceIn(1, 7) }.distinct().sorted()
                    val currentDow = baseDate.dayOfWeek.value
                    val nextInSameWeek = sortedDays.firstOrNull { it > currentDow }
                    if (nextInSameWeek != null) {
                        baseDate.plusDays((nextInSameWeek - currentDow).toLong())
                    } else {
                        val firstDow = sortedDays.first()
                        baseDate
                            .minusDays((currentDow - 1).toLong())
                            .plusWeeks(safeInterval)
                            .plusDays((firstDow - 1).toLong())
                    }
                } else {
                    baseDate.plusWeeks(safeInterval)
                }
            }
            RecurrenceUnit.MONTHS -> {
                val nextMonth = baseDate.plusMonths(safeInterval)
                if (rule.mode == RecurrenceMode.FIXED_SCHEDULE && rule.dayOfMonth != null) {
                    val maxDom = nextMonth.lengthOfMonth()
                    val targetDom = if (rule.dayOfMonth == -1) maxDom else rule.dayOfMonth.coerceIn(1, maxDom)
                    nextMonth.withDayOfMonth(targetDom)
                } else {
                    nextMonth
                }
            }
            RecurrenceUnit.YEARS -> baseDate.plusYears(safeInterval)
        }
    }

    if (deadlineLocal != null && deadlineLocal.isAfter(todayDate)) {
        return deadlineLocal
    }

    return whenLocal ?: deadlineLocal ?: todayDate.plusDays(1)
}

@Composable
private fun UpcomingDayGroupHeader(
    date: LocalDate,
    todayDate: LocalDate,
    locale: Locale,
    isFirstGroup: Boolean
) {
    val colors = ThingsTheme.colors
    val tomorrowLabel = stringResource(R.string.date_tomorrow)
    val todayLabel = stringResource(R.string.view_today)

    val dayMonthText = remember(date, locale) {
        val pattern = if (locale.language == "de") "d. MMM" else "MMM d"
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
    val weekdayText = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern("EEEE", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    val subtitleText = when (date) {
        todayDate -> "$todayLabel • $weekdayText"
        todayDate.plusDays(1) -> "$tomorrowLabel • $weekdayText"
        else -> weekdayText
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = if (isFirstGroup) 6.dp else 18.dp,
                bottom = 6.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = dayMonthText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            )
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (date == todayDate.plusDays(1)) colors.thingsBlue else colors.secondaryText
                ),
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
    }
}

