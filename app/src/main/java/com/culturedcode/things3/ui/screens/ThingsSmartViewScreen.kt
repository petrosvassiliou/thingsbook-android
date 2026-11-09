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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.culturedcode.things3.BuildConfig
import com.culturedcode.things3.R
import com.culturedcode.things3.data.repository.CloudSyncStatus
import com.culturedcode.things3.data.repository.ThingsRepository
import com.culturedcode.things3.domain.model.Area
import com.culturedcode.things3.domain.model.Heading
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.domain.model.RecurrenceMode
import com.culturedcode.things3.domain.model.RecurrenceUnit
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.Tag
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.domain.model.TaskStatus
import com.culturedcode.things3.ui.components.CreateOrEditProjectDialog
import com.culturedcode.things3.ui.components.DeadlineDatePickerDialog
import com.culturedcode.things3.ui.components.ExpandableTaskCard
import com.culturedcode.things3.ui.components.GeminiAssistantModal
import com.culturedcode.things3.ui.components.GeminiLogbookReflectionCard
import com.culturedcode.things3.ui.components.MagicPlusBackdropScrim
import com.culturedcode.things3.ui.components.MagicPlusContextMode
import com.culturedcode.things3.ui.components.MagicPlusDragState
import com.culturedcode.things3.ui.components.MagicPlusFab
import com.culturedcode.things3.ui.components.MagicPlusInsertionGap
import com.culturedcode.things3.ui.components.MoveTaskToProjectOrAreaDialog
import com.culturedcode.things3.ui.components.ProjectProgressRing
import com.culturedcode.things3.ui.components.QuickCreateAreaDialog
import com.culturedcode.things3.ui.components.SampleTasksAccountMergeDialog
import com.culturedcode.things3.ui.components.SmartViewIcon
import com.culturedcode.things3.ui.components.SmartViewType
import com.culturedcode.things3.ui.components.ThingsAreaManagerModal
import com.culturedcode.things3.ui.components.ThingsAuthAndSyncModal
import com.culturedcode.things3.ui.components.ThingsBackupCreateDialog
import com.culturedcode.things3.ui.components.ThingsBackupRestoreDialog
import com.culturedcode.things3.ui.components.ThingsDeadlineBadge
import com.culturedcode.things3.ui.components.ThingsHoverTooltip
import com.culturedcode.things3.ui.components.ThingsInitialSetupBanner
import com.culturedcode.things3.ui.components.ThingsNotificationSettingsModal
import com.culturedcode.things3.ui.components.ThingsReminderBadge
import com.culturedcode.things3.ui.components.ThingsRepeatBadge
import com.culturedcode.things3.ui.components.ThingsSettingsModal
import com.culturedcode.things3.ui.components.ThingsTagManagerModal
import com.culturedcode.things3.ui.components.ThingsTagPill
import com.culturedcode.things3.ui.components.ThingsWhenBadge
import com.culturedcode.things3.ui.components.ThingsbookBrandSymbol
import com.culturedcode.things3.ui.components.ThisEveningHeaderBadge
import com.culturedcode.things3.ui.theme.ThingsTheme
import com.culturedcode.things3.ui.viewmodel.ActiveNavigationTarget
import com.culturedcode.things3.ui.viewmodel.ThingsbookStatistics
import com.culturedcode.things3.ui.viewmodel.ThingsbookViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
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
    val selectedFilterTagId by viewModel.selectedFilterTagId.collectAsState()
    val isQuickFindOpen by viewModel.isQuickFindOpen.collectAsState()
    val isSidebarVisible by viewModel.isSidebarVisible.collectAsState()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsState()

    val inboxTasks by viewModel.inboxTasks.collectAsState()
    val todayTasks by viewModel.todayDaytimeTasks.collectAsState()
    val eveningTasks by viewModel.thisEveningTasks.collectAsState()
    val upcomingTasks by viewModel.upcomingTasks.collectAsState()
    val anytimeTasks by viewModel.anytimeTasks.collectAsState()
    val somedayTasks by viewModel.somedayTasks.collectAsState()
    val logbookTasks by viewModel.logbookTasks.collectAsState()

    val areas by viewModel.areas.collectAsState()
    val activeProjects by viewModel.activeProjects.collectAsState()
    val projectHeadings by viewModel.activeProjectHeadings.collectAsState()
    val projectTasks by viewModel.activeProjectTasks.collectAsState()
    val areaTasks by viewModel.activeAreaTasks.collectAsState()
    val allTags by viewModel.allTags.collectAsState()

    var magicDragState by remember { mutableStateOf(MagicPlusDragState()) }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var projectDialogPreselectedAreaId by remember { mutableStateOf<String?>(null) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }

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

    val allVisibleViewTasks: List<TaskItem> = remember(primaryTasks, filteredEveningTasks, navTarget) {
        if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY)) {
            primaryTasks + filteredEveningTasks
        } else {
            primaryTasks
        }
    }
    val allVisibleTaskIds: List<String> = remember(allVisibleViewTasks) {
        allVisibleViewTasks.map { it.id }
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
    val showSidebar = isWideScreen && isSidebarVisible
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
                            viewModel.setQuickFindOpen(!isQuickFindOpen)
                            true
                        }
                        ctrlOrMeta && event.key == Key.A && expandedTaskId == null -> {
                            viewModel.selectAllTasks(allVisibleTaskIds)
                            true
                        }
                        ctrlOrMeta && event.key == Key.N -> {
                            viewModel.createTaskAtPosition(primaryTasks, 0, "")
                            true
                        }
                        ctrlOrMeta && event.key == Key.G -> {
                            viewModel.setGeminiModalOpen(true)
                            true
                        }
                        event.key == Key.Escape -> viewModel.dismissTopmostOverlay()
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
            // Swipe down gesture on any list opens Quick Find inline immediately
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
            if (showSidebar) {
                ThingsChromebookSidebar(
                    viewModel = viewModel,
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
                contentAlignment = Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier
                        .padding(
                            start = if (showSidebar) 12.dp else 0.dp,
                            end = if (showSidebar) 20.dp else 0.dp
                        )
                        .widthIn(max = if (isWideScreen) 1040.dp else 2000.dp)
                        .fillMaxSize()
                ) {
                    ThingsTopHeaderAndQuickFindSection(
                        viewModel = viewModel,
                        isWideScreen = isWideScreen,
                        isSidebarVisible = isSidebarVisible,
                        isQuickFindOpen = isQuickFindOpen,
                        areas = areas,
                        activeProjects = activeProjects,
                        activeLocale = activeLocale
                    )

                    // Show Horizontal View Switcher Strip on Phones (< 700dp) OR whenever the Sidebar is hidden!
                    if (!showSidebar) {
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
                            viewModel.createTaskAtPosition(primaryTasks, 0, "")
                        },
                        onAddHeadingInProject = {
                            viewModel.createHeadingInCurrentProject(projectHeadings.size, "")
                        },
                        onRenameArea = { aId, newName -> viewModel.renameArea(aId, newName) },
                        onMoveArea = { aId, up -> viewModel.moveArea(aId, up) },
                        onDeleteArea = { aId -> viewModel.deleteArea(aId) }
                    )

                    // Interactive Tag Filter Strip (displays ONLY tags used by tasks in the current view)
                    TagFilterAndManageStrip(
                        allTags = allTags,
                        rawPrimaryTasks = rawPrimaryTasks,
                        eveningTasks = eveningTasks,
                        navTarget = navTarget,
                        selectedTagId = selectedFilterTagId,
                        onSelectTag = { viewModel.selectFilterTag(it) }
                    )

                    ThingsMainTaskLazyList(
                        viewModel = viewModel,
                        navTarget = navTarget,
                        isProjectView = isProjectView,
                        isAreaView = isAreaView,
                        isQuickFindOpen = isQuickFindOpen,
                        currentArea = currentArea,
                        primaryTasks = primaryTasks,
                        filteredEveningTasks = filteredEveningTasks,
                        allVisibleTaskIds = allVisibleTaskIds,
                        areas = areas,
                        activeProjects = activeProjects,
                        projectHeadings = projectHeadings,
                        allTags = allTags,
                        expandedTaskId = expandedTaskId,
                        selectedTaskIds = selectedTaskIds,
                        magicDragState = magicDragState,
                        activeLocale = activeLocale,
                        onRequestCreateProjectInArea = { areaId ->
                            projectDialogPreselectedAreaId = areaId
                            editingProject = null
                            showCreateProjectDialog = true
                        },
                        onRequestEditProject = { proj ->
                            editingProject = proj
                            showCreateProjectDialog = true
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                }
            }
        }

        // App Version Badge at bottom-left when Sidebar is hidden or on phones
        if (!showSidebar) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.tagPillBg.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Thingsbook v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = colors.secondaryText
                )
            }
        }

        // Floating Multi-Task Selection Action Bar (Mouse Ctrl/Shift-Click & Things-for-iPhone Swipe)
        if (selectedTaskIds.isNotEmpty()) {
            ThingsMultiSelectionBar(
                selectedCount = selectedTaskIds.size,
                totalVisibleCount = allVisibleTaskIds.size,
                onSelectAll = { viewModel.selectAllTasks(allVisibleTaskIds) },
                onScheduleToday = {
                    val todayStartMillis = LocalDate.now()
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    viewModel.batchScheduleSelectedTasks(allVisibleViewTasks, ScheduleState.TODAY, todayStartMillis)
                },
                onScheduleTomorrow = {
                    val tomorrowStartMillis = LocalDate.now()
                        .plusDays(1)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    viewModel.batchScheduleSelectedTasks(allVisibleViewTasks, ScheduleState.UPCOMING, tomorrowStartMillis)
                },
                onOpenBatchMove = { showBatchMoveDialog = true },
                onBatchComplete = { viewModel.batchCompleteSelectedTasks() },
                onBatchDelete = { viewModel.batchDeleteSelectedTasks() },
                onClearSelection = { viewModel.clearTaskSelection() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 22.dp)
            )
        }

        // Expandable Context-Sensitive "Magic Plus" Menu + Drag & Drop FAB (bottom-right)
        MagicPlusOverlaySection(
            viewModel = viewModel,
            navTarget = navTarget,
            currentArea = currentArea,
            primaryTasks = primaryTasks,
            projectHeadings = projectHeadings,
            areasCount = areas.size,
            onDragStateChanged = { state -> magicDragState = state },
            onRequestCreateProject = { areaId ->
                projectDialogPreselectedAreaId = areaId
                editingProject = null
                showCreateProjectDialog = true
            }
        )

        ThingsModalsOverlay(
            viewModel = viewModel,
            areas = areas,
            activeProjects = activeProjects,
            allTags = allTags,
            allVisibleViewTasks = allVisibleViewTasks,
            activeLocale = activeLocale,
            showBatchMoveDialog = showBatchMoveDialog,
            onDismissBatchMoveDialog = { showBatchMoveDialog = false },
            showCreateProjectDialog = showCreateProjectDialog,
            editingProject = editingProject,
            projectDialogPreselectedAreaId = projectDialogPreselectedAreaId,
            onDismissCreateProjectDialog = {
                showCreateProjectDialog = false
                editingProject = null
            }
        )
    }
}

@Composable
private fun MagicPlusOverlaySection(
    viewModel: ThingsbookViewModel,
    navTarget: ActiveNavigationTarget,
    currentArea: Area?,
    primaryTasks: List<TaskItem>,
    projectHeadings: List<Heading>,
    areasCount: Int,
    onDragStateChanged: (MagicPlusDragState) -> Unit,
    onRequestCreateProject: (String?) -> Unit
) {
    val isMagicPlusMenuOpen by viewModel.isMagicPlusMenuOpen.collectAsState()
    val contextMode = when (navTarget) {
        is ActiveNavigationTarget.ProjectDetail -> MagicPlusContextMode.PROJECT_DETAIL
        is ActiveNavigationTarget.AreaDetail -> MagicPlusContextMode.AREA_DETAIL
        is ActiveNavigationTarget.SmartView -> {
            if (navTarget.type == SmartViewType.LOGBOOK) {
                MagicPlusContextMode.LOGBOOK
            } else {
                MagicPlusContextMode.SMART_VIEW
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MagicPlusBackdropScrim(
            visible = isMagicPlusMenuOpen,
            onDismiss = { viewModel.setMagicPlusMenuOpen(false) },
            modifier = Modifier.fillMaxSize()
        )

        MagicPlusFab(
            itemCount = primaryTasks.size,
            contextMode = contextMode,
            canCreateArea = areasCount < ThingsRepository.MAX_AREAS,
            isMenuExpanded = isMagicPlusMenuOpen,
            onMenuExpandedChange = { viewModel.setMagicPlusMenuOpen(it) },
            onCreateTodo = {
                viewModel.createTaskAtPosition(primaryTasks, 0, "")
            },
            onCreateProject = {
                onRequestCreateProject(currentArea?.id)
            },
            onCreateHeading = {
                viewModel.createHeadingInCurrentProject(projectHeadings.size, "")
            },
            onCreateArea = {
                viewModel.setQuickCreateAreaDialogOpen(true)
            },
            onDragStateChanged = onDragStateChanged,
            onDropCreateTodoAt = { dropIdx ->
                viewModel.createTaskAtPosition(primaryTasks, dropIdx, "")
            },
            onDropCreateHeadingAt = { dropIdx ->
                viewModel.createHeadingInCurrentProject(dropIdx.coerceIn(0, projectHeadings.size), "")
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 28.dp)
        )
    }
}

@Composable
private fun ThingsErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp)),
        color = colors.deadlineUrgentRed.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = colors.deadlineUrgentRed,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.deadlineUrgentRed,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = colors.deadlineUrgentRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ThingsTopHeaderAndQuickFindSection(
    viewModel: ThingsbookViewModel,
    isWideScreen: Boolean,
    isSidebarVisible: Boolean,
    isQuickFindOpen: Boolean,
    areas: List<Area>,
    activeProjects: List<Project>,
    activeLocale: Locale
) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val dailyDigestConfig by viewModel.dailyDigestConfig.collectAsState()
    val quickFindQuery by viewModel.quickFindQuery.collectAsState()
    val quickFindTasks by viewModel.quickFindTaskResults.collectAsState()
    val quickFindProjects by viewModel.quickFindProjectResults.collectAsState()
    val uiErrorMessage by viewModel.uiErrorMessage.collectAsState()
    val hasSampleTasks by viewModel.hasSampleTasks.collectAsState()
    val isInitialSetupBannerDismissed by viewModel.isInitialSetupBannerDismissed.collectAsState()
    val currentUserIdentifier by viewModel.currentUserIdentifier.collectAsState()
    var showLanguageMenu by remember { mutableStateOf(false) }

    val showSidebar = isWideScreen && isSidebarVisible

    // Top Utility & Navigation Bar (Sidebar Toggle, Thingsbook Symbol when sidebar hidden, Quick Find, Gemini AI ✨, Areas 📦, Tags 🏷️, Notifications 🔔, Language, Cloud Sync, Settings ⚙️)
    TopThingsbookBar(
        isWideScreen = isWideScreen,
        isSidebarVisible = isSidebarVisible,
        showBrandSymbol = !showSidebar,
        isQuickFindOpen = isQuickFindOpen,
        syncStatus = syncStatus,
        dailyDigestEnabled = dailyDigestConfig.enabled,
        showLanguageMenu = showLanguageMenu,
        onToggleSidebar = { viewModel.toggleSidebarVisible() },
        onSelectTodayView = { viewModel.selectSmartView(SmartViewType.TODAY) },
        onToggleLanguageMenu = { showLanguageMenu = it },
        onSelectLanguage = { tag ->
            showLanguageMenu = false
            viewModel.switchAppLanguage(tag)
        },
        onOpenQuickFind = { viewModel.setQuickFindOpen(!isQuickFindOpen) },
        onOpenGeminiHub = { viewModel.setGeminiModalOpen(true) },
        onOpenAreaManager = { viewModel.setAreaManagerOpen(true) },
        onOpenTagManager = { viewModel.setTagManagerOpen(true) },
        onOpenNotificationSettings = { viewModel.setNotificationSettingsOpen(true) },
        onManualSync = { viewModel.setAuthModalOpen(true) },
        onOpenSettings = { viewModel.setSettingsModalOpen(true) }
    )

    AnimatedVisibility(
        visible = !uiErrorMessage.isNullOrBlank(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        ThingsErrorBanner(
            message = uiErrorMessage.orEmpty(),
            onDismiss = { viewModel.clearUiErrorMessage() }
        )
    }

    // Initial Setup Banner (shown only while seeded sample tasks still exist and user has not signed in or dismissed)
    AnimatedVisibility(
        visible = hasSampleTasks && !isInitialSetupBannerDismissed && currentUserIdentifier.isNullOrBlank(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        ThingsInitialSetupBanner(
            onConnectAccount = { viewModel.setAuthModalOpen(true) },
            onDeleteSampleTasksNow = { viewModel.deleteSampleTasksForLocalSetup() },
            onDismissBanner = { viewModel.dismissInitialSetupBanner() }
        )
    }

    // Inline Quick Find Panel (expands directly below Top Bar with rich task details!)
    AnimatedVisibility(
        visible = isQuickFindOpen,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        InlineQuickFindSection(
            query = quickFindQuery,
            taskResults = quickFindTasks,
            projectResults = quickFindProjects,
            areas = areas,
            allProjects = activeProjects,
            locale = activeLocale,
            onQueryChange = { viewModel.updateQuickFindQuery(it) },
            onSelectSmartView = {
                viewModel.setQuickFindOpen(false)
                viewModel.selectSmartView(it)
            },
            onSelectArea = {
                viewModel.setQuickFindOpen(false)
                viewModel.selectArea(it)
            },
            onSelectProject = {
                viewModel.setQuickFindOpen(false)
                viewModel.selectProject(it)
            },
            onSelectTask = { task ->
                viewModel.setQuickFindOpen(false)
                when {
                    task.projectId != null -> viewModel.selectProject(task.projectId)
                    task.areaId != null && task.scheduleState == ScheduleState.ANYTIME -> viewModel.selectArea(task.areaId)
                    task.scheduleState == ScheduleState.INBOX -> viewModel.selectSmartView(SmartViewType.INBOX)
                    task.scheduleState == ScheduleState.TODAY || task.scheduleState == ScheduleState.THIS_EVENING ->
                        viewModel.selectSmartView(SmartViewType.TODAY)
                    task.scheduleState == ScheduleState.UPCOMING -> viewModel.selectSmartView(SmartViewType.UPCOMING)
                    task.scheduleState == ScheduleState.SOMEDAY -> viewModel.selectSmartView(SmartViewType.SOMEDAY)
                    else -> viewModel.selectSmartView(SmartViewType.ANYTIME)
                }
                viewModel.toggleTaskExpansion(task.id)
            },
            onClose = { viewModel.setQuickFindOpen(false) }
        )
    }
}

@Composable
private fun ThingsTaskRowItem(
    task: TaskItem,
    viewModel: ThingsbookViewModel,
    allTags: List<Tag>,
    areas: List<Area>,
    activeProjects: List<Project>,
    allHeadings: List<Heading>,
    expandedTaskId: String?,
    isQuickFindOpen: Boolean,
    selectedTaskIds: Set<String>,
    allVisibleTaskIds: List<String>,
    magicBreakdownTaskId: String?,
    activeLocale: Locale
) {
    ExpandableTaskCard(
        task = task,
        allTags = allTags,
        allAreas = areas,
        allProjects = activeProjects,
        allHeadings = allHeadings,
        isExpanded = expandedTaskId == task.id,
        isSelected = task.id in selectedTaskIds,
        isSelectionModeActive = selectedTaskIds.isNotEmpty(),
        suppressContextMenuExternal = expandedTaskId != null || isQuickFindOpen,
        onToggleSelection = { viewModel.toggleTaskSelection(task.id) },
        onRangeSelect = { viewModel.selectTaskRange(allVisibleTaskIds, task.id) },
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
        onInsertChecklistItemAfter = { afterId, newId, initialTitle ->
            viewModel.insertChecklistItemAfter(task.id, afterId, newId, initialTitle)
        },
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

@Composable
private fun ThingsMainTaskLazyList(
    viewModel: ThingsbookViewModel,
    navTarget: ActiveNavigationTarget,
    isProjectView: Boolean,
    isAreaView: Boolean,
    isQuickFindOpen: Boolean,
    currentArea: Area?,
    primaryTasks: List<TaskItem>,
    filteredEveningTasks: List<TaskItem>,
    allVisibleTaskIds: List<String>,
    areas: List<Area>,
    activeProjects: List<Project>,
    projectHeadings: List<Heading>,
    allTags: List<Tag>,
    expandedTaskId: String?,
    selectedTaskIds: Set<String>,
    magicDragState: MagicPlusDragState,
    activeLocale: Locale,
    onRequestCreateProjectInArea: (String) -> Unit,
    onRequestEditProject: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val collapsedHeadingIds by viewModel.collapsedHeadingIds.collectAsState()
    val allHeadings by viewModel.allHeadings.collectAsState()
    val magicBreakdownTaskId by viewModel.magicBreakdownTaskId.collectAsState()
    val logbookAiReview by viewModel.logbookAiReview.collectAsState()
    val isGeminiBusy by viewModel.isGeminiBusy.collectAsState()

    LazyColumn(
        modifier = modifier,
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

        // If viewing an Area (e.g. Work or Home), show the Area's Projects section only when projects exist (Things 3 style)
        if (isAreaView && currentArea != null) {
            val areaProjects = activeProjects.filter { it.areaId == currentArea.id }
            if (areaProjects.isNotEmpty()) {
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
                                color = colors.secondaryText,
                                fontWeight = FontWeight.Normal
                            )
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.thingsBlueSoft)
                                .clickable { onRequestCreateProjectInArea(currentArea.id) }
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
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
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
                        onEditProject = { onRequestEditProject(proj) },
                        onUpdateDeadline = { dl -> viewModel.updateProjectDeadline(proj.id, dl) },
                        onMoveUp = { viewModel.moveProject(proj.id, true) },
                        onMoveDown = { viewModel.moveProject(proj.id, false) }
                    )
                }
            }

            item(key = "area_tasks_divider_${currentArea.id}") {
                val tasksSectionWord = if (primaryTasks.size == 1) {
                    stringResource(R.string.entity_todo)
                } else {
                    stringResource(R.string.entity_todos)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = if (areaProjects.isNotEmpty()) 10.dp else 4.dp)
                ) {
                    if (areaProjects.isNotEmpty()) {
                        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = "$tasksSectionWord • ${currentArea.title} (${primaryTasks.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = colors.secondaryText,
                            fontWeight = FontWeight.Normal
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
                ThingsTaskRowItem(
                    task = task,
                    viewModel = viewModel,
                    allTags = allTags,
                    areas = areas,
                    activeProjects = activeProjects,
                    allHeadings = allHeadings,
                    expandedTaskId = expandedTaskId,
                    isQuickFindOpen = isQuickFindOpen,
                    selectedTaskIds = selectedTaskIds,
                    allVisibleTaskIds = allVisibleTaskIds,
                    magicBreakdownTaskId = magicBreakdownTaskId,
                    activeLocale = activeLocale
                )
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
                        ThingsTaskRowItem(
                            task = task,
                            viewModel = viewModel,
                            allTags = allTags,
                            areas = areas,
                            activeProjects = activeProjects,
                            allHeadings = allHeadings,
                            expandedTaskId = expandedTaskId,
                            isQuickFindOpen = isQuickFindOpen,
                            selectedTaskIds = selectedTaskIds,
                            allVisibleTaskIds = allVisibleTaskIds,
                            magicBreakdownTaskId = magicBreakdownTaskId,
                            activeLocale = activeLocale
                        )
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
                    ThingsTaskRowItem(
                        task = task,
                        viewModel = viewModel,
                        allTags = allTags,
                        areas = areas,
                        activeProjects = activeProjects,
                        allHeadings = allHeadings,
                        expandedTaskId = expandedTaskId,
                        isQuickFindOpen = isQuickFindOpen,
                        selectedTaskIds = selectedTaskIds,
                        allVisibleTaskIds = allVisibleTaskIds,
                        magicBreakdownTaskId = magicBreakdownTaskId,
                        activeLocale = activeLocale
                    )
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
                ThingsTaskRowItem(
                    task = task,
                    viewModel = viewModel,
                    allTags = allTags,
                    areas = areas,
                    activeProjects = activeProjects,
                    allHeadings = allHeadings,
                    expandedTaskId = expandedTaskId,
                    isQuickFindOpen = isQuickFindOpen,
                    selectedTaskIds = selectedTaskIds,
                    allVisibleTaskIds = allVisibleTaskIds,
                    magicBreakdownTaskId = magicBreakdownTaskId,
                    activeLocale = activeLocale
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
        val safeEveningTasks = if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY)) {
            val primaryIds = primaryTasks.mapTo(HashSet()) { it.id }
            filteredEveningTasks.filter { it.id !in primaryIds }
        } else {
            emptyList()
        }
        if (safeEveningTasks.isNotEmpty()) {
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
                items = safeEveningTasks,
                key = { it.id }
            ) { eveningTask ->
                ThingsTaskRowItem(
                    task = eveningTask,
                    viewModel = viewModel,
                    allTags = allTags,
                    areas = areas,
                    activeProjects = activeProjects,
                    allHeadings = allHeadings,
                    expandedTaskId = expandedTaskId,
                    isQuickFindOpen = isQuickFindOpen,
                    selectedTaskIds = selectedTaskIds,
                    allVisibleTaskIds = allVisibleTaskIds,
                    magicBreakdownTaskId = magicBreakdownTaskId,
                    activeLocale = activeLocale
                )
            }
        }
    }
}

@Composable
private fun ThingsModalsOverlay(
    viewModel: ThingsbookViewModel,
    areas: List<Area>,
    activeProjects: List<Project>,
    allTags: List<Tag>,
    allVisibleViewTasks: List<TaskItem>,
    activeLocale: Locale,
    showBatchMoveDialog: Boolean,
    onDismissBatchMoveDialog: () -> Unit,
    showCreateProjectDialog: Boolean,
    editingProject: Project?,
    projectDialogPreselectedAreaId: String?,
    onDismissCreateProjectDialog: () -> Unit
) {
    val allHeadings by viewModel.allHeadings.collectAsState()
    val isSettingsModalOpen by viewModel.isSettingsModalOpen.collectAsState()
    val isLocalOnlyDbMode by viewModel.isLocalOnlyDbMode.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val currentUserIdentifier by viewModel.currentUserIdentifier.collectAsState()
    val statistics by viewModel.statistics.collectAsState()
    val csvStatusMessage by viewModel.csvStatusMessage.collectAsState()
    val hasSampleTasks by viewModel.hasSampleTasks.collectAsState()
    val pendingMergeAuthRequest by viewModel.pendingMergeAuthRequest.collectAsState()
    val pendingMergeNonSampleCount by viewModel.pendingMergeNonSampleCount.collectAsState()
    val isQuickCreateAreaDialogOpen by viewModel.isQuickCreateAreaDialogOpen.collectAsState()

    val isGeminiModalOpen by viewModel.isGeminiModalOpen.collectAsState()
    val isGeminiBusy by viewModel.isGeminiBusy.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val geminiErrorMessage by viewModel.geminiErrorMessage.collectAsState()

    val isAuthModalOpen by viewModel.isAuthModalOpen.collectAsState()
    val isSigningIn by viewModel.isSigningIn.collectAsState()
    val authErrorMessage by viewModel.authErrorMessage.collectAsState()
    val authInfoMessage by viewModel.authInfoMessage.collectAsState()

    val isTagManagerOpen by viewModel.isTagManagerOpen.collectAsState()
    val isAreaManagerOpen by viewModel.isAreaManagerOpen.collectAsState()
    val isNotificationSettingsOpen by viewModel.isNotificationSettingsOpen.collectAsState()
    val dailyDigestConfig by viewModel.dailyDigestConfig.collectAsState()

    // Batch Move Dialog for Multi-Selection
    if (showBatchMoveDialog) {
        MoveTaskToProjectOrAreaDialog(
            currentProjectId = null,
            currentHeadingId = null,
            currentAreaId = null,
            areas = areas,
            projects = activeProjects,
            headings = allHeadings,
            onMoveTask = { pId, hId, aId ->
                viewModel.batchMoveSelectedTasks(allVisibleViewTasks, pId, hId, aId)
            },
            onDismiss = onDismissBatchMoveDialog
        )
    }

    // Comprehensive Settings Modal (⚙️ top-right: Local DB vs Cloud Sync, Account, Language, Statistics, Backup & Restore, CSV)
    if (isSettingsModalOpen) {
        val isBackupBusy by viewModel.isBackupBusy.collectAsState()
        val backupStatusMessage by viewModel.backupStatusMessage.collectAsState()
        ThingsSettingsModal(
            isLocalOnlyDbMode = isLocalOnlyDbMode,
            syncStatus = syncStatus,
            currentUserIdentifier = currentUserIdentifier,
            statistics = statistics,
            csvStatusMessage = csvStatusMessage,
            isBackupBusy = isBackupBusy,
            backupStatusMessage = backupStatusMessage,
            hasSampleTasks = hasSampleTasks,
            onDeleteSampleTasksNow = { viewModel.deleteSampleTasksForLocalSetup() },
            onSetLocalOnlyDbMode = { viewModel.setLocalOnlyDbMode(it) },
            onOpenAuthAndSyncModal = {
                viewModel.setSettingsModalOpen(false)
                viewModel.setAuthModalOpen(true)
            },
            onOpenNotificationSettings = {
                viewModel.setSettingsModalOpen(false)
                viewModel.setNotificationSettingsOpen(true)
            },
            onTriggerImmediateSync = { viewModel.triggerManualCloudSync() },
            onSignOutCloud = { viewModel.signOutCloud() },
            onSelectLanguage = { viewModel.switchAppLanguage(it) },
            onOpenCreateBackupDialog = { viewModel.setBackupCreateDialogOpen(true) },
            onInspectBackupFromUri = { uri -> viewModel.inspectBackupUriForRestore(uri) },
            onClearBackupStatusMessage = { viewModel.clearBackupStatusMessage() },
            onExportCsvToUri = { uri -> viewModel.exportTasksCsvToUri(uri) },
            onShareCsv = { viewModel.shareTasksCsv() },
            onImportCsvFromUri = { uri -> viewModel.importTasksCsvFromUri(uri) },
            onClearCsvMessage = { viewModel.clearCsvStatusMessage() },
            onDismiss = { viewModel.setSettingsModalOpen(false) }
        )
    }

    ThingsBackupDialogsHost(
        viewModel = viewModel,
        statistics = statistics
    )

    // Initial Setup Account Merge Prompt (shown BEFORE syncing whenever sample tasks still exist!)
    pendingMergeAuthRequest?.let { pendingReq ->
        SampleTasksAccountMergeDialog(
            accountLabel = pendingReq.displayAccountLabel,
            nonSampleTaskCount = pendingMergeNonSampleCount,
            onConfirmMergeWithAccount = {
                viewModel.confirmAccountSetupMerge(mergeCurrentTasks = true)
            },
            onConfirmReplaceWithAccountOnly = {
                viewModel.confirmAccountSetupMerge(mergeCurrentTasks = false)
            },
            onCancel = {
                viewModel.cancelPendingAccountSetupMerge()
            }
        )
    }

    // Quick Create Area Dialog (triggered from the animated Magic Plus '+' context menu)
    if (isQuickCreateAreaDialogOpen) {
        QuickCreateAreaDialog(
            currentAreaCount = areas.size,
            maxAreas = ThingsRepository.MAX_AREAS,
            onCreateArea = { newTitle ->
                viewModel.setQuickCreateAreaDialogOpen(false)
                viewModel.createAreaAndNavigate(newTitle)
            },
            onDismiss = { viewModel.setQuickCreateAreaDialogOpen(false) }
        )
    }

    // Gemini AI Hub Modal (Brain Dump + Project Architect in EN, DE, IT, FR, ES)
    if (isGeminiModalOpen) {
        GeminiAssistantModal(
            isProcessing = isGeminiBusy,
            currentApiKey = geminiApiKey,
            errorMessage = geminiErrorMessage,
            onClearError = { viewModel.clearGeminiErrorMessage() },
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

    // Areas of Responsibility Manager Modal (Work, Home, up to 8 Areas)
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
            onDismiss = onDismissCreateProjectDialog
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

@Composable
private fun ThingsBackupDialogsHost(
    viewModel: ThingsbookViewModel,
    statistics: ThingsbookStatistics
) {
    val isBackupCreateDialogOpen by viewModel.isBackupCreateDialogOpen.collectAsState()
    val pendingRestoreSession by viewModel.pendingRestoreSession.collectAsState()
    val isBackupBusy by viewModel.isBackupBusy.collectAsState()
    val restoreDialogError by viewModel.restoreDialogError.collectAsState()

    val createBackupDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportFullBackupToUri(uri)
        } else {
            viewModel.clearStagedBackupPassword()
        }
    }

    if (isBackupCreateDialogOpen) {
        ThingsBackupCreateDialog(
            statistics = statistics,
            isBusy = isBackupBusy,
            onSaveToFile = { pwd ->
                viewModel.stagePasswordForBackupFileSave(pwd)
                val suffix = if (!pwd.isNullOrEmpty()) "-encrypted" else ""
                val fileName = "thingsbook-backup-${LocalDate.now()}$suffix.tbbackup"
                runCatching { createBackupDocumentLauncher.launch(fileName) }
            },
            onShareBackupFile = { pwd ->
                viewModel.shareFullBackup(pwd)
            },
            onDismiss = { viewModel.setBackupCreateDialogOpen(false) }
        )
    }

    pendingRestoreSession?.let { session ->
        ThingsBackupRestoreDialog(
            inspection = session.inspection,
            fileNameHint = session.fileNameHint,
            isBusy = isBackupBusy,
            errorMessage = restoreDialogError,
            onConfirmRestore = { pwd ->
                viewModel.confirmRestoreFromPendingBackup(pwd)
            },
            onCancel = {
                viewModel.cancelPendingRestore()
            }
        )
    }
}

@Composable
private fun ThingsMultiSelectionBar(
    selectedCount: Int,
    totalVisibleCount: Int,
    onSelectAll: () -> Unit,
    onScheduleToday: () -> Unit,
    onScheduleTomorrow: () -> Unit,
    onOpenBatchMove: () -> Unit,
    onBatchComplete: () -> Unit,
    onBatchDelete: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        color = colors.cardElevatedSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 10.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.thingsBlue)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$selectedCount ausgewählt",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            if (selectedCount < totalVisibleCount) {
                ThingsHoverTooltip(text = "Alle Aufgaben in dieser Ansicht auswählen (Strg+A)") {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectAll()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SelectAll,
                            contentDescription = "Alle auswählen",
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            ThingsTagPill(
                title = "★ Heute",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onScheduleToday()
                }
            )

            ThingsTagPill(
                title = "📅 Morgen",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onScheduleTomorrow()
                }
            )

            ThingsTagPill(
                title = "📁 Verschieben",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenBatchMove()
                }
            )

            ThingsTagPill(
                title = "✓ Erledigen",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBatchComplete()
                }
            )

            ThingsHoverTooltip(text = "Ausgewählte Aufgaben löschen") {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBatchDelete()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Ausgewählte löschen",
                        tint = colors.deadlineUrgentRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            ThingsHoverTooltip(text = "Auswahl aufheben (Esc)") {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClearSelection()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Auswahl aufheben",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.primaryText
                    )
                    Text(
                        text = "${project.completedTaskCount}/${project.totalTaskCount} • ${if (project.totalTaskCount == 1) stringResource(R.string.entity_todo) else stringResource(R.string.entity_todos)}",
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
                    ThingsHoverTooltip(text = "Projekt nach oben verschieben") {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(26.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = stringResource(R.string.action_move),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                if (canMoveDown) {
                    ThingsHoverTooltip(text = "Projekt nach unten verschieben") {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(26.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDownward,
                                contentDescription = stringResource(R.string.action_move),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                ThingsHoverTooltip(text = stringResource(R.string.project_edit_title)) {
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
    isWideScreen: Boolean,
    isSidebarVisible: Boolean,
    showBrandSymbol: Boolean,
    isQuickFindOpen: Boolean,
    syncStatus: CloudSyncStatus,
    dailyDigestEnabled: Boolean,
    showLanguageMenu: Boolean,
    onToggleSidebar: () -> Unit,
    onSelectTodayView: () -> Unit,
    onToggleLanguageMenu: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onOpenQuickFind: () -> Unit,
    onOpenGeminiHub: () -> Unit,
    onOpenAreaManager: () -> Unit,
    onOpenTagManager: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onManualSync: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = ThingsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isWideScreen) {
                ThingsHoverTooltip(
                    text = if (isSidebarVisible) "Seitenleiste ausblenden" else "Seitenleiste einblenden"
                ) {
                    IconButton(
                        onClick = onToggleSidebar,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isSidebarVisible) Icons.AutoMirrored.Outlined.MenuOpen else Icons.Outlined.Menu,
                            contentDescription = if (isSidebarVisible) "Seitenleiste ausblenden" else "Seitenleiste einblenden",
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (showBrandSymbol) {
                ThingsHoverTooltip(text = "Thingsbook • ${stringResource(R.string.view_today)}") {
                    ThingsbookBrandSymbol(
                        size = 28.dp,
                        onClick = onSelectTodayView
                    )
                }
            }

            // Quick Find Trigger Pill (opens inline!)
            ThingsHoverTooltip(text = "${stringResource(R.string.quick_find_title)} (Ctrl+F)") {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isQuickFindOpen) colors.thingsBlueSoft else colors.tagPillBg)
                        .clickable(onClick = onOpenQuickFind)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.quick_find_title),
                        tint = if (isQuickFindOpen) colors.thingsBlue else colors.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.quick_find_title),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isQuickFindOpen) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isQuickFindOpen) colors.thingsBlue else colors.secondaryText
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Gemini AI Button ✨
            ThingsHoverTooltip(text = "${stringResource(R.string.gemini_hub_title)} (Ctrl+G)") {
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
            }

            // Areas Manager Button 📦 (Work, Home, max 8)
            ThingsHoverTooltip(text = stringResource(R.string.areas_manage_title)) {
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
            }

            // Tag Management Button 🏷️
            ThingsHoverTooltip(text = stringResource(R.string.tags_manage_title)) {
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
            }

            // Daily & Task Notifications Button 🔔
            ThingsHoverTooltip(text = stringResource(R.string.notifications_title)) {
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
            }

            // In-App Language Switcher (EN, DE, IT, FR, ES)
            Box {
                ThingsHoverTooltip(text = stringResource(R.string.settings_language)) {
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

            // Cloud Delta-Sync Status Indicator ☁️
            ThingsHoverTooltip(text = stringResource(R.string.sync_title)) {
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

            // Additional Settings Button ⚙️ at the far top-right!
            ThingsHoverTooltip(text = stringResource(R.string.settings_title)) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = colors.primaryText,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TagFilterAndManageStrip(
    allTags: List<Tag>,
    rawPrimaryTasks: List<TaskItem>,
    eveningTasks: List<TaskItem>,
    navTarget: ActiveNavigationTarget,
    selectedTagId: String?,
    onSelectTag: (String?) -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val isTodaySmartView = navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY)

    val usedTags = remember(allTags, rawPrimaryTasks, eveningTasks, isTodaySmartView) {
        com.culturedcode.things3.domain.model.ViewTagFilterUtils.resolveUsedTagsForView(
            allTags = allTags,
            rawPrimaryTasks = rawPrimaryTasks,
            eveningTasks = eveningTasks,
            isTodaySmartView = isTodaySmartView
        )
    }

    // Automatically reset filter if the previously selected tag is not used by any task in this view
    LaunchedEffect(selectedTagId, usedTags) {
        if (selectedTagId != null && usedTags.none { it.id == selectedTagId }) {
            onSelectTag(null)
        }
    }

    // Authentic Things 3 behavior: only show the tag filter bar when tasks in the view actually use tags
    if (usedTags.isEmpty()) {
        return
    }

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

        items(usedTags, key = { "tag_chip_${it.id}" }) { tag ->
            val isSelected = selectedTagId == tag.id
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colors.thingsBlue else colors.tagPillBg)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTag(if (isSelected) null else tag.id)
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

        // 2. Areas (Work, Home, ... up to 8)
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

        // 5. "Areas / Bereiche / Aree / Domaines / Áreas (X/8)" Quick Trigger
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
    onDeleteArea: (String) -> Unit
) {
    val colors = ThingsTheme.colors
    val zone = ZoneId.systemDefault()
    var showProjectDeadlineDialog by remember(currentProject?.id) { mutableStateOf(false) }
    var showProjectAreaMenu by remember(currentProject?.id) { mutableStateOf(false) }
    var isRenamingAreaInline by remember(currentArea?.id) { mutableStateOf(false) }
    var inlineAreaField by remember(currentArea?.id, currentArea?.title) {
        val initial = currentArea?.title ?: ""
        mutableStateOf(
            TextFieldValue(
                text = initial,
                selection = if (initial.isEmpty()) TextRange(0) else TextRange(0, initial.length)
            )
        )
    }

    val areaWord = stringResource(R.string.entity_area)

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
                                value = inlineAreaField,
                                onValueChange = { inlineAreaField = it },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            val txt = inlineAreaField.text
                                            inlineAreaField = inlineAreaField.copy(
                                                selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                            )
                                        }
                                    }
                            )
                            IconButton(
                                onClick = {
                                    if (inlineAreaField.text.isNotBlank()) {
                                        onRenameArea(currentArea.id, inlineAreaField.text.trim())
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
                        ThingsHoverTooltip(text = "Projekt nach oben verschieben") {
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
                    }
                    if (projIdx != -1 && projIdx < projects.lastIndex) {
                        ThingsHoverTooltip(text = "Projekt nach unten verschieben") {
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
                    }
                    ThingsHoverTooltip(text = stringResource(R.string.project_edit_title)) {
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
                    }
                    ThingsHoverTooltip(text = "Projekt abschließen") {
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
                    }
                    ThingsHoverTooltip(text = "Projekt löschen") {
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
                }
            } else if (currentArea != null && !isRenamingAreaInline) {
                val areaIdx = areas.indexOfFirst { it.id == currentArea.id }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (areaIdx > 0) {
                        ThingsHoverTooltip(text = "Bereich nach oben verschieben") {
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
                    }
                    if (areaIdx != -1 && areaIdx < areas.lastIndex) {
                        ThingsHoverTooltip(text = "Bereich nach unten verschieben") {
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
                    }
                    ThingsHoverTooltip(text = stringResource(R.string.areas_rename_label)) {
                        IconButton(
                            onClick = {
                                inlineAreaField = TextFieldValue(
                                    text = currentArea.title,
                                    selection = TextRange(0, currentArea.title.length)
                                )
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
                    }
                    ThingsHoverTooltip(text = "Bereich löschen") {
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
                    val formattedTargetDate = runCatching {
                        Instant.ofEpochMilli(currentProject.deadline)
                            .atZone(zone)
                            .toLocalDate()
                            .format(DateTimeFormatter.ofPattern("d. MMM yyyy", locale))
                    }.getOrDefault("—")
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
    var isEditing by remember(heading.id) { mutableStateOf(heading.title.isEmpty()) }
    var editedTitleField by remember(heading.id) {
        mutableStateOf(
            TextFieldValue(
                text = heading.title,
                selection = if (heading.title.isEmpty()) TextRange(0) else TextRange(0, heading.title.length)
            )
        )
    }
    val headingFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            delay(50)
            runCatching { headingFocusRequester.requestFocus() }
        }
    }

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
                    value = editedTitleField,
                    onValueChange = { editedTitleField = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.entity_new_heading),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(headingFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                val txt = editedTitleField.text
                                editedTitleField = editedTitleField.copy(
                                    selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                )
                            }
                        }
                )
                IconButton(
                    onClick = {
                        if (editedTitleField.text.isNotBlank()) {
                            onRenameHeading(editedTitleField.text.trim())
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
                IconButton(
                    onClick = {
                        if (heading.title.isEmpty() && editedTitleField.text.isBlank()) {
                            onDeleteHeading()
                        } else {
                            isEditing = false
                        }
                    }
                ) {
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
                            editedTitleField = TextFieldValue(
                                text = heading.title,
                                selection = if (heading.title.isEmpty()) TextRange(0) else TextRange(0, heading.title.length)
                            )
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

/**
 * Inline Quick Find Section (`Schnellsuche`):
 * Opens inline right under the Top Bar (no popup dialog) and displays rich details for every result:
 * - Smart Views, Areas, Projects (with Area breadcrumb, progress, target date)
 * - Tasks with Status/Schedule State badge, Area › Project breadcrumb, When Date, Reminder time,
 *   Recurrence rule, Deadline, Subtasks count (`☑ X/Y`), Tags, and Notes preview!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineQuickFindSection(
    query: String,
    taskResults: List<TaskItem>,
    projectResults: List<Project>,
    areas: List<Area>,
    allProjects: List<Project>,
    locale: Locale,
    onQueryChange: (String) -> Unit,
    onSelectSmartView: (SmartViewType) -> Unit,
    onSelectArea: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onSelectTask: (TaskItem) -> Unit,
    onClose: () -> Unit
) {
    val colors = ThingsTheme.colors
    val searchFocusRequester = remember { FocusRequester() }
    var searchField by remember {
        mutableStateOf(
            TextFieldValue(
                text = query,
                selection = if (query.isEmpty()) TextRange(0) else TextRange(0, query.length)
            )
        )
    }

    LaunchedEffect(Unit) {
        delay(40)
        runCatching { searchFocusRequester.requestFocus() }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = colors.cardElevatedSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchField,
                    onValueChange = {
                        searchField = it
                        onQueryChange(it.text)
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.quick_find_placeholder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = colors.thingsBlue
                        )
                    },
                    trailingIcon = {
                        if (searchField.text.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchField = TextFieldValue(text = "", selection = TextRange(0))
                                    onQueryChange("")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Suche leeren",
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                val txt = searchField.text
                                searchField = searchField.copy(
                                    selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                )
                            }
                        }
                )

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = colors.secondaryText
                    )
                }
            }

            // Matching Smart Views & Areas
            val matchingViews = SmartViewType.entries.filter { viewType ->
                val localizedName = stringResource(viewType.titleRes)
                query.isNotBlank() && localizedName.contains(query, ignoreCase = true)
            }
            val matchingAreas = remember(query, areas) {
                if (query.isBlank()) emptyList()
                else areas.filter { it.title.contains(query, ignoreCase = true) }
            }

            if (query.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tippe einen Suchbegriff ein, um Aufgaben (inkl. Notizen, Datum, Bereich, Projekt, Unteraufgaben & Tags), Projekte, Bereiche oder Ansichten sofort inline zu durchsuchen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                val totalHits = matchingViews.size + matchingAreas.size + projectResults.size + taskResults.size
                Text(
                    text = "$totalHits Ergebnisse für „$query“",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Smart Views
                    items(matchingViews, key = { "qf_view_${it.name}" }) { viewType ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.tagPillBg.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectSmartView(viewType) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SmartViewIcon(viewType = viewType, modifier = Modifier.size(20.dp))
                                Text(
                                    text = stringResource(viewType.titleRes),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primaryText
                                )
                                ThingsTagPill(title = "Ansicht")
                            }
                        }
                    }

                    // 2. Areas
                    items(matchingAreas, key = { "qf_area_${it.id}" }) { area ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.tagPillBg.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectArea(area.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Inventory2,
                                    contentDescription = null,
                                    tint = colors.anytimeTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = area.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primaryText
                                )
                                ThingsTagPill(title = stringResource(R.string.entity_area))
                            }
                        }
                    }

                    // 3. Projects (with Area, Progress & Target Date)
                    items(projectResults, key = { "qf_proj_${it.id}" }) { proj ->
                        val projArea = areas.find { it.id == proj.areaId }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.tagPillBg.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectProject(proj.id) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ProjectProgressRing(progress = proj.progress, size = 18.dp)
                                    Text(
                                        text = proj.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.primaryText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ThingsTagPill(title = "📁 ${proj.completedTaskCount}/${proj.totalTaskCount}")
                                    proj.deadline?.let { dl ->
                                        ThingsDeadlineBadge(deadlineEpochMillis = dl, locale = locale)
                                    }
                                }
                                if (projArea != null || proj.notes.isNotBlank()) {
                                    Text(
                                        text = listOfNotNull(
                                            projArea?.let { "📦 ${it.title}" },
                                            proj.notes.takeIf { it.isNotBlank() }
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // 4. Tasks with FULL Details (Status/Schedule, Area › Project, WhenDate, Reminder, Repeat, Deadline, Subtasks, Tags, Notes)
                    items(taskResults, key = { "qf_task_${it.id}" }) { task ->
                        val taskProject = allProjects.find { it.id == task.projectId }
                        val taskArea = areas.find { it.id == (taskProject?.areaId ?: task.areaId) }
                        val scheduleLabel = when {
                            task.status == TaskStatus.COMPLETED -> "✅ Erledigt"
                            task.status == TaskStatus.CANCELED -> "✕ Abgebrochen"
                            task.scheduleState == ScheduleState.TODAY -> "★ Heute"
                            task.scheduleState == ScheduleState.THIS_EVENING -> "🌙 Heute Abend"
                            task.scheduleState == ScheduleState.UPCOMING -> "📅 Geplant"
                            task.scheduleState == ScheduleState.INBOX -> "📥 Eingang"
                            task.scheduleState == ScheduleState.SOMEDAY -> "📦 Irgendwann"
                            else -> "🗂 Jederzeit"
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.tagPillBg.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectTask(task) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                // Title + Status/SmartView Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = task.title.ifBlank { "(Ohne Titel)" },
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.primaryText,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ThingsTagPill(title = scheduleLabel)
                                }

                                // Rich Metadata Row: Area › Project, WhenDate, Reminder, Repeat, Deadline, Subtasks, Tags
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (taskArea != null || taskProject != null) {
                                        val containerText = when {
                                            taskArea != null && taskProject != null -> "📦 ${taskArea.title} › 📁 ${taskProject.title}"
                                            taskProject != null -> "📁 ${taskProject.title}"
                                            taskArea != null -> "📦 ${taskArea.title}"
                                            else -> ""
                                        }
                                        if (containerText.isNotEmpty()) {
                                            ThingsTagPill(title = containerText)
                                        }
                                    }

                                    if (task.whenDate != null || task.scheduleState != ScheduleState.ANYTIME) {
                                        ThingsWhenBadge(
                                            scheduleState = task.scheduleState,
                                            whenDateEpochMillis = task.whenDate,
                                            locale = locale
                                        )
                                    }

                                    task.reminderTime?.let { rt ->
                                        ThingsReminderBadge(reminderEpochMillis = rt)
                                    }

                                    task.recurrenceRule?.let { rr ->
                                        ThingsRepeatBadge(rule = rr)
                                    }

                                    task.deadline?.let { dl ->
                                        ThingsDeadlineBadge(deadlineEpochMillis = dl, locale = locale)
                                    }

                                    if (task.checklistItems.isNotEmpty()) {
                                        val doneCount = task.checklistItems.count { it.isCompleted }
                                        ThingsTagPill(title = "☑ $doneCount/${task.checklistItems.size} Unteraufgaben")
                                    }

                                    task.tags.forEach { tag ->
                                        ThingsTagPill(title = "🏷️ ${tag.title}")
                                    }
                                }

                                // Notes Preview
                                if (task.notes.isNotBlank()) {
                                    Text(
                                        text = task.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
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

@Composable
private fun ThingsChromebookSidebar(
    viewModel: ThingsbookViewModel,
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
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Clean Sidebar Brand Header (no Today badge here — Heute count is shown in the Heute row below)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThingsHoverTooltip(text = "Thingsbook") {
                ThingsbookBrandSymbol(
                    size = 28.dp,
                    onClick = { onSelectSmartView(SmartViewType.TODAY) }
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.primaryText
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

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
                        ThingsHoverTooltip(text = "${stringResource(R.string.entity_new_project)} in ${area.title}") {
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

        // Bottom Sidebar Actions: "+ New Project" & "Areas / Bereiche (X/8)" + App Version at Bottom-Left!
        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ThingsHoverTooltip(text = "Neues Projekt anlegen") {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCreateNewProject(null) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
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
            }

            ThingsHoverTooltip(text = "Bereiche verwalten (${areas.size}/${ThingsRepository.MAX_AREAS})") {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenAreaManager)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
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

        // App Version displayed at the bottom-left of the Sidebar
        Text(
            text = "Thingsbook v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.tertiaryText,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
        )
    }
}

private fun computeUpcomingTargetDate(
    task: TaskItem,
    todayDate: LocalDate,
    zone: ZoneId
): LocalDate = runCatching {
    val whenLocal = task.whenDate?.let {
        runCatching { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.getOrNull()
    }
    val deadlineLocal = task.deadline?.let {
        runCatching { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.getOrNull()
    }

    if (task.scheduleState == ScheduleState.TODAY || task.scheduleState == ScheduleState.THIS_EVENING) {
        return@runCatching if (whenLocal != null && whenLocal.isAfter(todayDate)) whenLocal else todayDate
    }

    if (whenLocal != null && !whenLocal.isBefore(todayDate)) {
        return@runCatching whenLocal
    }

    val rule = task.recurrenceRule
    if (rule != null) {
        val safeInterval = rule.interval.coerceIn(1, 3650).toLong()
        val baseDate = if (whenLocal != null && !whenLocal.isAfter(todayDate)) todayDate else (whenLocal ?: todayDate)
        return@runCatching when (rule.unit) {
            RecurrenceUnit.DAYS -> baseDate.plusDays(safeInterval)
            RecurrenceUnit.WEEKS -> {
                val sortedDays = rule.daysOfWeek.filter { it in 1..7 }.distinct().sorted()
                if (rule.mode == RecurrenceMode.FIXED_SCHEDULE && sortedDays.isNotEmpty()) {
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
            RecurrenceUnit.YEARS -> baseDate.plusYears(safeInterval.coerceAtMost(100L))
        }
    }

    if (deadlineLocal != null && deadlineLocal.isAfter(todayDate)) {
        return@runCatching deadlineLocal
    }

    whenLocal ?: deadlineLocal ?: todayDate.plusDays(1)
}.getOrElse {
    todayDate.plusDays(1)
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
                    fontWeight = FontWeight.Normal,
                    color = colors.secondaryText
                ),
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
    }
}

