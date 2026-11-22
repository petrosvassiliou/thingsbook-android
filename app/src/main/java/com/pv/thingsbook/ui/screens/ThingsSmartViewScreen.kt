package com.pv.thingsbook.ui.screens

import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import com.pv.thingsbook.domain.model.ProjectRoadmapEngine
import com.pv.thingsbook.domain.model.ProjectWorkspaceMode
import com.pv.thingsbook.ui.components.CompactMobileDragDropDock
import com.pv.thingsbook.ui.components.MagicPlusDropMode
import com.pv.thingsbook.ui.components.MagicPlusPositionalDropHandler
import com.pv.thingsbook.ui.components.ProjectBoardWorkspace
import com.pv.thingsbook.ui.components.ProjectDragDropController
import com.pv.thingsbook.ui.components.ProjectDragKind
import com.pv.thingsbook.ui.components.ProjectDragSession
import com.pv.thingsbook.ui.components.ProjectListEntry
import com.pv.thingsbook.ui.components.ProjectRoadmapWorkspace
import com.pv.thingsbook.ui.components.ProjectTaskDragHandle
import com.pv.thingsbook.ui.components.ProjectWorkspaceSegmentedSwitcher
import com.pv.thingsbook.ui.components.ProjectDropIndicatorOverlay
import com.pv.thingsbook.ui.components.ProjectDragAutoScrollEffect
import com.pv.thingsbook.ui.components.RoadmapExporter
import com.pv.thingsbook.ui.components.RoadmapPdfExportDialog
import com.pv.thingsbook.ui.components.WorkspaceDragDropDestination
import com.pv.thingsbook.ui.components.WorkspaceDragFloatingOverlay
import com.pv.thingsbook.ui.components.projectDropSurface
import com.pv.thingsbook.ui.components.workspaceDragRootSurface
import com.pv.thingsbook.ui.components.workspaceDropTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pv.thingsbook.data.update.AppUpdateUiState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pv.thingsbook.BuildConfig
import com.pv.thingsbook.MainActivity
import com.pv.thingsbook.R
import com.pv.thingsbook.data.calendar.DeviceCalendarRepository
import com.pv.thingsbook.data.repository.CloudSyncStatus
import com.pv.thingsbook.data.repository.ThingsRepository
import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.CalendarEventItem
import com.pv.thingsbook.domain.model.EffortLevel
import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.QuickFindDateFilter
import com.pv.thingsbook.domain.model.QuickFindEntityScope
import com.pv.thingsbook.domain.model.QuickFindFilter
import com.pv.thingsbook.domain.model.RecurrenceMode
import com.pv.thingsbook.domain.model.RecurrenceUnit
import com.pv.thingsbook.domain.model.SavedPerspective
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.StalledProjectDetector
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.model.TimeblockScheduleUtils
import com.pv.thingsbook.domain.model.computeTodayCapacitySummary
import com.pv.thingsbook.domain.model.filterTasksForPerspective
import com.pv.thingsbook.domain.model.formatDurationCompact
import com.pv.thingsbook.domain.model.sortTasksForView
import com.pv.thingsbook.ui.components.AttachmentImportOutcome
import com.pv.thingsbook.ui.components.CreateOrEditProjectDialog
import com.pv.thingsbook.ui.components.DeadlineDatePickerDialog
import com.pv.thingsbook.ui.components.DetectedNotesLinksRow
import com.pv.thingsbook.ui.components.ExpandableTaskCard
import com.pv.thingsbook.ui.components.GeminiAssistantModal
import com.pv.thingsbook.ui.components.GeminiLogbookReflectionCard
import com.pv.thingsbook.ui.components.MagicPlusBackdropScrim
import com.pv.thingsbook.ui.components.MagicPlusContextMode
import com.pv.thingsbook.ui.components.MagicPlusDragState
import com.pv.thingsbook.ui.components.MagicPlusFab
import com.pv.thingsbook.ui.components.MagicPlusInsertionGap
import com.pv.thingsbook.ui.components.MoveTaskToProjectOrAreaDialog
import com.pv.thingsbook.ui.components.PerspectiveEditorDialog
import com.pv.thingsbook.ui.components.ProjectProgressRing
import com.pv.thingsbook.ui.components.QuickCreateAreaDialog
import com.pv.thingsbook.ui.components.SampleTasksAccountMergeDialog
import com.pv.thingsbook.ui.components.SmartViewIcon
import com.pv.thingsbook.ui.components.SmartViewType
import com.pv.thingsbook.ui.components.TaskTagPickerDialog
import com.pv.thingsbook.ui.components.ThingsAreaManagerModal
import com.pv.thingsbook.ui.components.ThingsAuthAndSyncModal
import com.pv.thingsbook.ui.components.ThingsBackupCreateDialog
import com.pv.thingsbook.ui.components.ThingsBackupRestoreDialog
import com.pv.thingsbook.ui.components.ThingsBiometricLockOverlay
import com.pv.thingsbook.ui.components.ThingsCelebrationSnackbarBanner
import com.pv.thingsbook.ui.components.ThingsDeadlineBadge
import com.pv.thingsbook.ui.components.ThingsEmptyTrashConfirmDialog
import com.pv.thingsbook.ui.components.ThingsFocusTimerBanner
import com.pv.thingsbook.ui.components.ThingsHoverTooltip
import com.pv.thingsbook.ui.components.ThingsTopBarTooltipScope
import com.pv.thingsbook.ui.components.ThingsInitialSetupBanner
import com.pv.thingsbook.ui.components.ThingsNotificationSettingsModal
import com.pv.thingsbook.ui.components.ThingsQuickShareModal
import com.pv.thingsbook.ui.components.ThingsReminderBadge
import com.pv.thingsbook.ui.components.ThingsRepeatBadge
import com.pv.thingsbook.ui.components.ThingsSettingsModal
import com.pv.thingsbook.ui.components.ThingsTagManagerModal
import com.pv.thingsbook.ui.components.ThingsTagPill
import com.pv.thingsbook.ui.components.ThingsTemplatesAndImportModal
import com.pv.thingsbook.ui.components.ThingsTodayModeSegmentedToggle
import com.pv.thingsbook.ui.components.ThingsTodayTimelineSection
import com.pv.thingsbook.ui.components.ThingsTrashItemCard
import com.pv.thingsbook.ui.components.ThingsUndoSnackbarBanner
import com.pv.thingsbook.ui.components.ThingsWeeklyReviewModal
import com.pv.thingsbook.ui.components.ThingsWhenBadge
import com.pv.thingsbook.ui.components.ThingsbookBrandSymbol
import com.pv.thingsbook.ui.components.ThisEveningHeaderBadge
import com.pv.thingsbook.ui.components.WorkspaceInspectorPanel
import com.pv.thingsbook.ui.components.rememberThingsMarkdownVisualTransformation
import com.pv.thingsbook.ui.theme.ThingsTheme
import com.pv.thingsbook.ui.viewmodel.ActiveNavigationTarget
import com.pv.thingsbook.ui.viewmodel.ThingsbookStatistics
import com.pv.thingsbook.ui.viewmodel.ThingsbookViewModel
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.PickVisualMediaRequest
import com.pv.thingsbook.domain.model.TaskAttachment
import com.pv.thingsbook.ui.components.TaskAttachmentsStrip
import com.pv.thingsbook.ui.components.findHostActivity
import com.pv.thingsbook.ui.components.processCapturedPhotoBitmap
import com.pv.thingsbook.ui.components.processPickedAttachmentUri
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TreeSet

internal fun shouldLockLandscapeForTimelineFullScreen(
    isEffectiveTimelineFullScreen: Boolean,
    smallestScreenWidthDp: Int,
    isChromeOsOrPcHardware: Boolean
): Boolean = isEffectiveTimelineFullScreen && smallestScreenWidthDp < 600 && !isChromeOsOrPcHardware

@OptIn(ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ThingsSmartViewScreen(
    viewModel: ThingsbookViewModel,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val activeLocale = configuration.locales[0] ?: Locale.getDefault()
    val lifecycleOwner = LocalLifecycleOwner.current
    val appCtx = LocalContext.current

    DisposableEffect(lifecycleOwner, appCtx, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            } else if (event == Lifecycle.Event.ON_STOP) {
                if (appCtx.findHostActivity()?.isChangingConfigurations != true) {
                    viewModel.onAppStopped()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val timeReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                viewModel.onAppResumed()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_TICK)
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                appCtx,
                timeReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { appCtx.unregisterReceiver(timeReceiver) }
        }
    }

    val navTarget by viewModel.navigationTarget.collectAsState()
    val expandedTaskId by viewModel.expandedTaskId.collectAsState()
    val selectedFilterTagId by viewModel.selectedFilterTagId.collectAsState()
    val isQuickFindOpen by viewModel.isQuickFindOpen.collectAsState()
    val isSidebarVisible by viewModel.isSidebarVisible.collectAsState()
    val isInspectorVisible by viewModel.isInspectorVisible.collectAsState()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val showMultiSelectionBar = selectedTaskIds.size > 1 || (isMultiSelectMode && selectedTaskIds.isNotEmpty())

    val inboxTasks by viewModel.inboxTasks.collectAsState()
    val todayTasks by viewModel.todayDaytimeTasks.collectAsState()
    val eveningTasks by viewModel.thisEveningTasks.collectAsState()
    val upcomingTasks by viewModel.upcomingTasks.collectAsState()
    val anytimeTasks by viewModel.anytimeTasks.collectAsState()
    val somedayTasks by viewModel.somedayTasks.collectAsState()
    val logbookTasks by viewModel.logbookTasks.collectAsState()
    val deletedTasks by viewModel.deletedTasks.collectAsState()
    val deletedProjects by viewModel.deletedProjects.collectAsState()
    val undoSnackbarMessage by viewModel.undoSnackbarMessage.collectAsState()
    val celebrationToastMessage by viewModel.celebrationToastMessage.collectAsState()
    val isTodayTimelineMode by viewModel.isTodayTimelineMode.collectAsState()
    val isTimelineFullScreen by viewModel.isTimelineFullScreen.collectAsState()
    val focusTimerState by viewModel.focusTimerState.collectAsState()
    val biometricAppLockEnabled by viewModel.biometricAppLockEnabled.collectAsState()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()

    val areas by viewModel.areas.collectAsState()
    val activeProjects by viewModel.activeProjects.collectAsState()
    val projectHeadings by viewModel.activeProjectHeadings.collectAsState()
    val projectTasks by viewModel.activeProjectTasks.collectAsState()
    val projectAttachments by viewModel.activeProjectAttachments.collectAsState()
    val areaTasks by viewModel.activeAreaTasks.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val viewSortMode by viewModel.viewSortMode.collectAsState()
    val savedPerspectives by viewModel.savedPerspectives.collectAsState()
    val showPerspectivesInSidebar by viewModel.showPerspectivesInSidebar.collectAsState()
    val allOpenTasks by viewModel.allOpenTasks.collectAsState()
    val openTaskIds by viewModel.openTaskIds.collectAsState()
    val calendarEventsEnabledTop by viewModel.calendarEventsEnabled.collectAsState()
    val rawTodayCalendarEventsTop by viewModel.todayCalendarEvents.collectAsState()
    val allHeadingsTop by viewModel.allHeadings.collectAsState()
    val currentEpochDayTop by viewModel.currentEpochDay.collectAsState()

    var magicDragState by remember { mutableStateOf(MagicPlusDragState()) }
    val projectDragController = remember { ProjectDragDropController() }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var projectDialogPreselectedAreaId by remember { mutableStateOf<String?>(null) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showBatchTagPickerDialog by remember { mutableStateOf(false) }
    var showPerspectiveEditorDialog by remember { mutableStateOf(false) }
    var editingPerspective by remember { mutableStateOf<SavedPerspective?>(null) }
    var showEmptyTrashConfirmDialog by remember { mutableStateOf(false) }
    var externalDialogTriggerToken by remember { mutableIntStateOf(0) }
    var externalDialogTriggerType by remember { mutableStateOf<String?>(null) }

    val zoneTop = ZoneId.systemDefault()
    val todayDateTop = remember(currentEpochDayTop) { LocalDate.ofEpochDay(currentEpochDayTop) }
    val todayDayStartMillis = remember(todayDateTop, zoneTop) {
        todayDateTop.atStartOfDay(zoneTop).toInstant().toEpochMilli()
    }
    val todayDayEndMillis = remember(todayDateTop, zoneTop) {
        todayDateTop.plusDays(1).atStartOfDay(zoneTop).toInstant().toEpochMilli()
    }

    val isProjectView = navTarget is ActiveNavigationTarget.ProjectDetail
    val isAreaView = navTarget is ActiveNavigationTarget.AreaDetail
    val currentProject: Project? = (navTarget as? ActiveNavigationTarget.ProjectDetail)?.let { detail ->
        activeProjects.find { it.id == detail.projectId }
    }
    val currentProjectWorkspaceMode: ProjectWorkspaceMode = remember(currentProject?.viewMode) {
        ProjectWorkspaceMode.fromRaw(currentProject?.viewMode)
    }
    val headingTaskCountsTop by viewModel.headingTaskCounts.collectAsState()
    val topCoroutineScope = rememberCoroutineScope()
    val exportRoadmapChooserTitle = stringResource(R.string.v30_roadmap_export)
    val currentArea: Area? = (navTarget as? ActiveNavigationTarget.AreaDetail)?.let { detail ->
        areas.find { it.id == detail.areaId }
    }
    val currentPerspective: SavedPerspective? = (navTarget as? ActiveNavigationTarget.PerspectiveDetail)?.let { detail ->
        savedPerspectives.find { it.id == detail.perspectiveId }
    }

    // Determine primary task list for current view, filtered by selected tag if active
    val rawPrimaryTasks: List<TaskItem> = when (val target = navTarget) {
        is ActiveNavigationTarget.SmartView -> when (target.type) {
            SmartViewType.INBOX -> inboxTasks
            SmartViewType.TODAY -> todayTasks
            SmartViewType.UPCOMING -> upcomingTasks
            SmartViewType.ANYTIME -> anytimeTasks
            SmartViewType.SOMEDAY -> somedayTasks
            SmartViewType.LOGBOOK -> logbookTasks
            SmartViewType.TRASH -> deletedTasks
        }
        is ActiveNavigationTarget.ProjectDetail -> projectTasks
        is ActiveNavigationTarget.AreaDetail -> areaTasks
        is ActiveNavigationTarget.PerspectiveDetail -> remember(
            target.perspectiveId,
            savedPerspectives,
            allOpenTasks,
            openTaskIds,
            todayDayStartMillis
        ) {
            val persp = savedPerspectives.firstOrNull { it.id == target.perspectiveId }
            if (persp != null) {
                filterTasksForPerspective(allOpenTasks, persp, openTaskIds, todayDayStartMillis)
            } else {
                emptyList()
            }
        }
    }

    val shouldDirectSortPrimary = !isProjectView &&
        navTarget != ActiveNavigationTarget.SmartView(SmartViewType.LOGBOOK) &&
        navTarget != ActiveNavigationTarget.SmartView(SmartViewType.TRASH) &&
        navTarget != ActiveNavigationTarget.SmartView(SmartViewType.UPCOMING)

    val primaryTasks: List<TaskItem> = remember(
        rawPrimaryTasks,
        selectedFilterTagId,
        viewSortMode,
        openTaskIds,
        shouldDirectSortPrimary
    ) {
        val tagId = selectedFilterTagId
        val tagFiltered = if (tagId == null) rawPrimaryTasks else rawPrimaryTasks.filter { task -> task.tags.any { it.id == tagId } }
        if (shouldDirectSortPrimary) {
            sortTasksForView(tagFiltered, viewSortMode, openTaskIds)
        } else {
            tagFiltered
        }
    }

    val filteredEveningTasks: List<TaskItem> = remember(eveningTasks, selectedFilterTagId, viewSortMode, openTaskIds) {
        val tagId = selectedFilterTagId
        val tagFiltered = if (tagId == null) eveningTasks else eveningTasks.filter { task -> task.tags.any { it.id == tagId } }
        sortTasksForView(tagFiltered, viewSortMode, openTaskIds)
    }

    val todayCapacitySummary: Pair<Int, Int> = remember(
        todayTasks,
        eveningTasks,
        rawTodayCalendarEventsTop,
        calendarEventsEnabledTop,
        todayDayStartMillis,
        todayDayEndMillis
    ) {
        val combinedToday = (todayTasks + eveningTasks).distinctBy { it.id }
        val activeCalEvents = if (calendarEventsEnabledTop) rawTodayCalendarEventsTop else emptyList()
        computeTodayCapacitySummary(
            tasks = combinedToday,
            calendarEvents = activeCalEvents,
            dayStartMillis = todayDayStartMillis,
            dayEndMillis = todayDayEndMillis
        )
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

    // Adaptive Layout Detection: Tablets & Foldables (>= 700dp) vs. Android Phones (< 700dp)
    val isTodayTimelineActive = navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY) &&
        isTodayTimelineMode &&
        !isQuickFindOpen
    val isProjectRoadmapActive = isProjectView &&
        currentProject != null &&
        currentProjectWorkspaceMode == ProjectWorkspaceMode.ROADMAP &&
        !isQuickFindOpen
    val isEffectiveTimelineFullScreen = isTimelineFullScreen && (isTodayTimelineActive || isProjectRoadmapActive)

    val isChromeOsOrPcHardware = remember(appCtx) {
        val pm = appCtx.packageManager
        pm.hasSystemFeature("org.chromium.arc") ||
            pm.hasSystemFeature("org.chromium.arc.device_management") ||
            pm.hasSystemFeature(PackageManager.FEATURE_PC)
    }
    val lockPhoneLandscape = shouldLockLandscapeForTimelineFullScreen(
        isEffectiveTimelineFullScreen = isEffectiveTimelineFullScreen,
        smallestScreenWidthDp = configuration.smallestScreenWidthDp,
        isChromeOsOrPcHardware = isChromeOsOrPcHardware
    )

    DisposableEffect(lockPhoneLandscape, appCtx) {
        if (lockPhoneLandscape) {
            appCtx.findHostActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose {
                appCtx.findHostActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } else {
            onDispose { }
        }
    }

    val isWideScreen = configuration.screenWidthDp >= 700
    val showSidebar = isWideScreen && isSidebarVisible && !isEffectiveTimelineFullScreen
    var isMobileDrawerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isWideScreen, isEffectiveTimelineFullScreen) {
        if ((isWideScreen || isEffectiveTimelineFullScreen) && isMobileDrawerOpen) {
            isMobileDrawerOpen = false
        }
    }

    BackHandler(enabled = !isWideScreen && isMobileDrawerOpen) {
        isMobileDrawerOpen = false
    }

    BackHandler(enabled = isEffectiveTimelineFullScreen && expandedTaskId == null && !isMobileDrawerOpen) {
        viewModel.setTimelineFullScreen(false)
    }

    val upcomingPhaseMilestoneCount = remember(allHeadingsTop, activeProjects, currentEpochDayTop) {
        val zone = ZoneId.systemDefault()
        val todayDate = LocalDate.ofEpochDay(currentEpochDayTop)
        val activeProjectIds = activeProjects.mapTo(HashSet()) { it.id }
        allHeadingsTop.count { h ->
            !h.isDeleted && !h.isArchived && h.projectId in activeProjectIds && h.endDate?.let { ms ->
                val d = runCatching { Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }.getOrNull()
                d != null && !d.isBefore(todayDate)
            } == true
        }
    }

    val smartViewCounts = mapOf(
        SmartViewType.INBOX to inboxTasks.size,
        SmartViewType.TODAY to (todayTasks.size + eveningTasks.size),
        SmartViewType.UPCOMING to (upcomingTasks.size + upcomingPhaseMilestoneCount),
        SmartViewType.ANYTIME to anytimeTasks.size,
        SmartViewType.SOMEDAY to somedayTasks.size,
        SmartViewType.LOGBOOK to logbookTasks.size,
        SmartViewType.TRASH to (deletedTasks.size + deletedProjects.size)
    )

    val triggerCreateTaskShortcut by rememberUpdatedState {
        showCreateProjectDialog = false
        editingProject = null
        showBatchMoveDialog = false
        showBatchTagPickerDialog = false
        showPerspectiveEditorDialog = false
        isMobileDrawerOpen = false
        viewModel.setMagicPlusMenuOpen(false)
        viewModel.createTaskAtPosition(primaryTasks, 0, "", forceTopUngroupedInProject = true)
    }

    val triggerCreateProjectShortcut by rememberUpdatedState {
        showBatchMoveDialog = false
        showBatchTagPickerDialog = false
        showPerspectiveEditorDialog = false
        isMobileDrawerOpen = false
        viewModel.setMagicPlusMenuOpen(false)
        projectDialogPreselectedAreaId = currentArea?.id ?: currentProject?.areaId
        editingProject = null
        showCreateProjectDialog = true
    }

    val context = LocalContext.current
    val resolveSmartViewForKeyCode: (Int) -> SmartViewType? = { keyCode ->
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_1, android.view.KeyEvent.KEYCODE_NUMPAD_1 -> SmartViewType.INBOX
            android.view.KeyEvent.KEYCODE_2, android.view.KeyEvent.KEYCODE_NUMPAD_2 -> SmartViewType.TODAY
            android.view.KeyEvent.KEYCODE_3, android.view.KeyEvent.KEYCODE_NUMPAD_3 -> SmartViewType.UPCOMING
            android.view.KeyEvent.KEYCODE_4, android.view.KeyEvent.KEYCODE_NUMPAD_4 -> SmartViewType.ANYTIME
            android.view.KeyEvent.KEYCODE_5, android.view.KeyEvent.KEYCODE_NUMPAD_5 -> SmartViewType.SOMEDAY
            android.view.KeyEvent.KEYCODE_6, android.view.KeyEvent.KEYCODE_NUMPAD_6 -> SmartViewType.LOGBOOK
            android.view.KeyEvent.KEYCODE_7, android.view.KeyEvent.KEYCODE_NUMPAD_7 -> SmartViewType.TRASH
            else -> null
        }
    }
    val tryTriggerTagShortcut: (android.view.KeyEvent) -> Boolean = { nativeEv ->
        val targetIds = if (expandedTaskId != null) {
            listOf(expandedTaskId!!)
        } else {
            selectedTaskIds.toList()
        }
        if (targetIds.isEmpty()) {
            false
        } else {
            val isTextEditorFocused = context.findHostActivity()?.currentFocus?.onCheckIsTextEditor() == true
            val keyChar: String? = when (nativeEv.keyCode) {
                in android.view.KeyEvent.KEYCODE_A..android.view.KeyEvent.KEYCODE_Z ->
                    ('a' + (nativeEv.keyCode - android.view.KeyEvent.KEYCODE_A)).toString()
                in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 ->
                    ('0' + (nativeEv.keyCode - android.view.KeyEvent.KEYCODE_0)).toString()
                else -> nativeEv.displayLabel
                    .takeIf { it != '\u0000' && !it.isWhitespace() }
                    ?.lowercaseChar()
                    ?.toString()
            }
            if (keyChar == null || (isTextEditorFocused && keyChar in setOf("c", "v", "x", "a", "z"))) {
                false
            } else {
                val matchedTag = allTags.firstOrNull { tag ->
                    !tag.isDeleted && tag.shortcut?.trim()?.equals(keyChar, ignoreCase = true) == true
                }
                if (matchedTag != null) {
                    viewModel.toggleTagOnTaskIds(matchedTag.id, targetIds)
                    true
                } else {
                    false
                }
            }
        }
    }

    val currentHardwareShortcutHandler by rememberUpdatedState<(android.view.KeyEvent) -> Boolean> { nativeEvent ->
        if (biometricAppLockEnabled && !isAppUnlocked) return@rememberUpdatedState false
        if (isEffectiveTimelineFullScreen) {
            if (nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {
                viewModel.setTimelineFullScreen(false)
                return@rememberUpdatedState true
            }
            val ctrlOrMetaFs = nativeEvent.isCtrlPressed || nativeEvent.isMetaPressed
            return@rememberUpdatedState ctrlOrMetaFs ||
                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_SPACE
        }
        val ctrlOrMeta = nativeEvent.isCtrlPressed || nativeEvent.isMetaPressed
        val smartViewShortcut = if (ctrlOrMeta && !nativeEvent.isShiftPressed && !nativeEvent.isAltPressed) {
            resolveSmartViewForKeyCode(nativeEvent.keyCode)
        } else {
            null
        }
        when {
            smartViewShortcut != null -> {
                isMobileDrawerOpen = false
                viewModel.selectSmartView(smartViewShortcut)
                true
            }
            ctrlOrMeta && nativeEvent.isShiftPressed && !nativeEvent.isAltPressed &&
                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_R -> {
                isMobileDrawerOpen = false
                viewModel.openWeeklyReviewModal()
                true
            }
            ctrlOrMeta && nativeEvent.isShiftPressed && nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_N -> {
                triggerCreateProjectShortcut()
                true
            }
            ctrlOrMeta && !nativeEvent.isShiftPressed && nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_N -> {
                triggerCreateTaskShortcut()
                true
            }
            ctrlOrMeta && !nativeEvent.isShiftPressed && nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_F -> {
                viewModel.setQuickFindOpen(!isQuickFindOpen)
                true
            }
            ctrlOrMeta && !nativeEvent.isShiftPressed && nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_G -> {
                viewModel.setGeminiModalOpen(true)
                true
            }
            ctrlOrMeta && !nativeEvent.isShiftPressed && nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_Z -> {
                viewModel.performUndo()
                true
            }
            ctrlOrMeta && !nativeEvent.isShiftPressed && nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_D -> {
                when {
                    selectedTaskIds.isNotEmpty() -> {
                        viewModel.batchDuplicateSelectedTasks()
                        true
                    }
                    expandedTaskId != null -> {
                        viewModel.duplicateTask(expandedTaskId!!)
                        true
                    }
                    currentProject != null -> {
                        viewModel.duplicateProject(currentProject.id)
                        true
                    }
                    else -> false
                }
            }
            ctrlOrMeta && !nativeEvent.isShiftPressed && nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_S -> {
                if (expandedTaskId != null) {
                    externalDialogTriggerType = "WHEN"
                    externalDialogTriggerToken++
                    true
                } else {
                    false
                }
            }
            ctrlOrMeta && nativeEvent.isShiftPressed && nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_D -> {
                if (expandedTaskId != null) {
                    externalDialogTriggerType = "DEADLINE"
                    externalDialogTriggerToken++
                    true
                } else {
                    false
                }
            }
            ctrlOrMeta && nativeEvent.isShiftPressed && nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_M -> {
                if (expandedTaskId != null) {
                    externalDialogTriggerType = "MOVE"
                    externalDialogTriggerToken++
                    true
                } else {
                    false
                }
            }
            ctrlOrMeta && (nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DEL ||
                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_FORWARD_DEL) -> {
                if (expandedTaskId != null) {
                    viewModel.deleteTask(expandedTaskId!!)
                    true
                } else {
                    false
                }
            }
            ctrlOrMeta && !nativeEvent.isShiftPressed && nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_A &&
                expandedTaskId == null && !showCreateProjectDialog && !showBatchMoveDialog && !showBatchTagPickerDialog && !showPerspectiveEditorDialog -> {
                viewModel.selectAllTasks(allVisibleTaskIds)
                true
            }
            ctrlOrMeta && !nativeEvent.isShiftPressed && !nativeEvent.isAltPressed &&
                tryTriggerTagShortcut(nativeEvent) -> true
            !ctrlOrMeta && !nativeEvent.isShiftPressed && !nativeEvent.isAltPressed &&
                (nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_SPACE) &&
                expandedTaskId == null && selectedTaskIds.size == 1 &&
                !showCreateProjectDialog && !showBatchMoveDialog && !showBatchTagPickerDialog &&
                !showPerspectiveEditorDialog && !showEmptyTrashConfirmDialog && !isMobileDrawerOpen &&
                !viewModel.hasOpenModalOrOverlay() &&
                context.findHostActivity()?.currentFocus?.onCheckIsTextEditor() != true -> {
                viewModel.expandTask(selectedTaskIds.first())
                true
            }
            nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE -> when {
                showPerspectiveEditorDialog -> {
                    showPerspectiveEditorDialog = false
                    editingPerspective = null
                    true
                }
                showBatchTagPickerDialog -> {
                    showBatchTagPickerDialog = false
                    true
                }
                showCreateProjectDialog -> {
                    showCreateProjectDialog = false
                    editingProject = null
                    true
                }
                showBatchMoveDialog -> {
                    showBatchMoveDialog = false
                    true
                }
                isMobileDrawerOpen -> {
                    isMobileDrawerOpen = false
                    true
                }
                else -> viewModel.dismissTopmostOverlay()
            }
            else -> false
        }
    }

    DisposableEffect(context) {
        val activity = context.findHostActivity() as? MainActivity
        val delegate: (android.view.KeyEvent) -> Boolean = { ev -> currentHardwareShortcutHandler(ev) }
        activity?.hardwareKeyShortcutHandler = delegate
        onDispose {
            if (activity?.hardwareKeyShortcutHandler === delegate) {
                activity.hardwareKeyShortcutHandler = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .onPreviewKeyEvent { event ->
                if (biometricAppLockEnabled && !isAppUnlocked) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    if (isEffectiveTimelineFullScreen) {
                        if (event.key == Key.Escape) {
                            viewModel.setTimelineFullScreen(false)
                            return@onPreviewKeyEvent true
                        }
                        val ctrlOrMetaFs = event.isCtrlPressed || event.isMetaPressed
                        if (ctrlOrMetaFs ||
                            event.key == Key.Enter ||
                            event.key == Key.NumPadEnter ||
                            event.key == Key.Spacebar
                        ) {
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }
                    val ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed
                    val smartViewPreviewTarget = if (ctrlOrMeta && !event.isShiftPressed && !event.isAltPressed) {
                        resolveSmartViewForKeyCode(event.nativeKeyEvent.keyCode)
                    } else {
                        null
                    }
                    when {
                        smartViewPreviewTarget != null -> {
                            isMobileDrawerOpen = false
                            viewModel.selectSmartView(smartViewPreviewTarget)
                            true
                        }
                        ctrlOrMeta && event.isShiftPressed && !event.isAltPressed && event.key == Key.R -> {
                            isMobileDrawerOpen = false
                            viewModel.openWeeklyReviewModal()
                            true
                        }
                        ctrlOrMeta && event.isShiftPressed && event.key == Key.N -> {
                            triggerCreateProjectShortcut()
                            true
                        }
                        ctrlOrMeta && !event.isShiftPressed && event.key == Key.N -> {
                            triggerCreateTaskShortcut()
                            true
                        }
                        ctrlOrMeta && !event.isShiftPressed && event.key == Key.F -> {
                            viewModel.setQuickFindOpen(!isQuickFindOpen)
                            true
                        }
                        ctrlOrMeta && !event.isShiftPressed && event.key == Key.Z -> {
                            viewModel.performUndo()
                            true
                        }
                        ctrlOrMeta && !event.isShiftPressed && event.key == Key.D -> {
                            when {
                                selectedTaskIds.isNotEmpty() -> {
                                    viewModel.batchDuplicateSelectedTasks()
                                    true
                                }
                                expandedTaskId != null -> {
                                    viewModel.duplicateTask(expandedTaskId!!)
                                    true
                                }
                                currentProject != null -> {
                                    viewModel.duplicateProject(currentProject.id)
                                    true
                                }
                                else -> false
                            }
                        }
                        ctrlOrMeta && !event.isShiftPressed && event.key == Key.S -> {
                            if (expandedTaskId != null) {
                                externalDialogTriggerType = "WHEN"
                                externalDialogTriggerToken++
                                true
                            } else {
                                false
                            }
                        }
                        ctrlOrMeta && event.isShiftPressed && event.key == Key.D -> {
                            if (expandedTaskId != null) {
                                externalDialogTriggerType = "DEADLINE"
                                externalDialogTriggerToken++
                                true
                            } else {
                                false
                            }
                        }
                        ctrlOrMeta && event.isShiftPressed && event.key == Key.M -> {
                            if (expandedTaskId != null) {
                                externalDialogTriggerType = "MOVE"
                                externalDialogTriggerToken++
                                true
                            } else {
                                false
                            }
                        }
                        ctrlOrMeta && (event.key == Key.Backspace || event.key == Key.Delete) -> {
                            if (expandedTaskId != null) {
                                viewModel.deleteTask(expandedTaskId!!)
                                true
                            } else {
                                false
                            }
                        }
                        ctrlOrMeta && !event.isShiftPressed && event.key == Key.A && expandedTaskId == null -> {
                            viewModel.selectAllTasks(allVisibleTaskIds)
                            true
                        }
                        ctrlOrMeta && !event.isShiftPressed && event.key == Key.G -> {
                            viewModel.setGeminiModalOpen(true)
                            true
                        }
                        ctrlOrMeta && !event.isShiftPressed && !event.isAltPressed &&
                            tryTriggerTagShortcut(event.nativeKeyEvent) -> true
                        event.key == Key.Escape -> when {
                            showPerspectiveEditorDialog -> {
                                showPerspectiveEditorDialog = false
                                editingPerspective = null
                                true
                            }
                            showBatchTagPickerDialog -> {
                                showBatchTagPickerDialog = false
                                true
                            }
                            showCreateProjectDialog -> {
                                showCreateProjectDialog = false
                                editingProject = null
                                true
                            }
                            showBatchMoveDialog -> {
                                showBatchMoveDialog = false
                                true
                            }
                            isMobileDrawerOpen -> {
                                isMobileDrawerOpen = false
                                true
                            }
                            else -> viewModel.dismissTopmostOverlay()
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .onKeyEvent { event ->
                if (biometricAppLockEnabled && !isAppUnlocked) return@onKeyEvent false
                if (event.type == KeyEventType.KeyDown &&
                    !isEffectiveTimelineFullScreen &&
                    !event.isCtrlPressed &&
                    !event.isMetaPressed &&
                    !event.isAltPressed &&
                    !event.isShiftPressed &&
                    (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar) &&
                    expandedTaskId == null && selectedTaskIds.size == 1 &&
                    !showCreateProjectDialog && !showBatchMoveDialog && !showBatchTagPickerDialog &&
                    !showPerspectiveEditorDialog && !showEmptyTrashConfirmDialog && !isMobileDrawerOpen &&
                    !viewModel.hasOpenModalOrOverlay()
                ) {
                    viewModel.expandTask(selectedTaskIds.first())
                    true
                } else {
                    false
                }
            }
            // Tap outside an expanded card collapses it smoothly; tap empty space clears single/multi task selection
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!(biometricAppLockEnabled && !isAppUnlocked)) {
                        if (expandedTaskId != null) {
                            viewModel.collapseExpandedTask()
                        } else if (selectedTaskIds.isNotEmpty()) {
                            viewModel.clearTaskSelection()
                        }
                    }
                }
            )
            // Swipe down gesture on any list opens Quick Find inline immediately (disabled in Full Screen Timeline)
            .pointerInput(biometricAppLockEnabled, isAppUnlocked, isEffectiveTimelineFullScreen) {
                if ((biometricAppLockEnabled && !isAppUnlocked) || isEffectiveTimelineFullScreen) return@pointerInput
                var accumulatedPull = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulatedPull = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (projectDragController.session != null) {
                            accumulatedPull = 0f
                            return@detectVerticalDragGestures
                        }
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
            .workspaceDragRootSurface(projectDragController) { finishedSession ->
                applyWorkspaceSessionDrop(
                    viewModel = viewModel,
                    session = finishedSession,
                    controller = projectDragController,
                    currentViewTasks = primaryTasks
                )
            }
    ) {
        val isExpandedProjectWorkspace = isProjectView && !isQuickFindOpen &&
            (currentProjectWorkspaceMode == ProjectWorkspaceMode.BOARD ||
                currentProjectWorkspaceMode == ProjectWorkspaceMode.ROADMAP)

        val deletedTaskIdsForInspector = remember(deletedTasks) {
            deletedTasks.mapTo(HashSet()) { it.id }
        }
        val selectedInspectorTask: TaskItem? = remember(
            selectedTaskIds,
            primaryTasks,
            allOpenTasks,
            logbookTasks,
            somedayTasks,
            inboxTasks,
            todayTasks,
            eveningTasks,
            upcomingTasks,
            anytimeTasks,
            projectTasks,
            areaTasks,
            deletedTaskIdsForInspector
        ) {
            if (selectedTaskIds.size != 1) {
                null
            } else {
                resolveUnifiedModalTask(
                    expandedTaskId = selectedTaskIds.first(),
                    primaryTasks = primaryTasks,
                    allOpenTasks = allOpenTasks,
                    logbookTasks = logbookTasks,
                    somedayTasks = somedayTasks,
                    inboxTasks = inboxTasks,
                    todayTasks = todayTasks,
                    eveningTasks = eveningTasks,
                    upcomingTasks = upcomingTasks,
                    anytimeTasks = anytimeTasks,
                    projectTasks = projectTasks,
                    areaTasks = areaTasks,
                    deletedTaskIds = deletedTaskIdsForInspector
                )
            }
        }
        val completedTodayCount = remember(logbookTasks, todayDayStartMillis, todayDayEndMillis) {
            logbookTasks.count { task ->
                !task.isDeleted &&
                    task.status == TaskStatus.COMPLETED &&
                    task.completedAt != null &&
                    task.completedAt in todayDayStartMillis until todayDayEndMillis
            }
        }
        var measuredDetailPaneWidthDp by remember { mutableStateOf<Dp?>(null) }
        val estimatedDetailPaneWidthDp = (configuration.screenWidthDp.dp - if (showSidebar) 273.dp else 0.dp)
            .coerceAtLeast(0.dp)
        val effectiveDetailPaneWidthDp = measuredDetailPaneWidthDp ?: estimatedDetailPaneWidthDp
        val outerShowInspectorPane = effectiveDetailPaneWidthDp >= 940.dp &&
            !isEffectiveTimelineFullScreen &&
            !isExpandedProjectWorkspace &&
            isInspectorVisible

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
                    savedPerspectives = savedPerspectives,
                    showPerspectivesInSidebar = showPerspectivesInSidebar,
                    allOpenTasks = allOpenTasks,
                    openTaskIds = openTaskIds,
                    todayDayStartMillis = todayDayStartMillis,
                    dragController = projectDragController,
                    onSelectSmartView = { viewModel.selectSmartView(it) },
                    onSelectArea = { viewModel.selectArea(it) },
                    onSelectProject = { viewModel.selectProject(it) },
                    onSelectPerspective = { viewModel.selectPerspective(it) },
                    onCreateNewPerspective = {
                        editingPerspective = null
                        showPerspectiveEditorDialog = true
                    },
                    onEditPerspective = { persp ->
                        editingPerspective = persp
                        showPerspectiveEditorDialog = true
                    },
                    onOpenAreaManager = { viewModel.setAreaManagerOpen(true) },
                    onOpenTagManager = { viewModel.setTagManagerOpen(true) },
                    onCreateNewProject = { areaId ->
                        projectDialogPreselectedAreaId = areaId
                        editingProject = null
                        showCreateProjectDialog = true
                    },
                    onOpenWeeklyReview = { viewModel.openWeeklyReviewModal() },
                    onOpenTemplatesAndImport = {
                        viewModel.openTemplatesModal(
                            initialTab = 0,
                            defaultAreaId = currentArea?.id ?: currentProject?.areaId
                        )
                    },
                    onOpenExternalImportExport = { areaId ->
                        viewModel.openTemplatesModal(
                            initialTab = 2,
                            defaultAreaId = areaId ?: currentArea?.id ?: currentProject?.areaId
                        )
                    }
                )
                VerticalDivider(
                    color = colors.hairlineDivider,
                    thickness = 1.dp,
                    modifier = Modifier.fillMaxHeight()
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopStart
            ) {
                val detailPaneWidth = maxWidth
                SideEffect {
                    if (measuredDetailPaneWidthDp != detailPaneWidth) {
                        measuredDetailPaneWidthDp = detailPaneWidth
                    }
                }
                val canShowInspectorToggle = detailPaneWidth >= 940.dp &&
                    !isEffectiveTimelineFullScreen &&
                    !isExpandedProjectWorkspace

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!isEffectiveTimelineFullScreen) {
                        val topSyncStatus by viewModel.syncStatus.collectAsState()
                        TopThingsbookBar(
                            isWideScreen = isWideScreen,
                            isSidebarVisible = if (isWideScreen) isSidebarVisible else isMobileDrawerOpen,
                            isQuickFindOpen = isQuickFindOpen,
                            syncStatus = topSyncStatus,
                            canShowInspectorToggle = canShowInspectorToggle,
                            isInspectorVisible = isInspectorVisible,
                            onToggleSidebar = {
                                if (isWideScreen) {
                                    viewModel.toggleSidebarVisible()
                                } else {
                                    isMobileDrawerOpen = !isMobileDrawerOpen
                                }
                            },
                            onToggleInspector = { viewModel.toggleInspectorVisible() },
                            onOpenQuickFind = { viewModel.setQuickFindOpen(!isQuickFindOpen) },
                            onOpenVoiceCapture = { viewModel.openVoiceQuickCapture() },
                            onOpenGeminiHub = { viewModel.setGeminiModalOpen(true) },
                            onManualSync = { viewModel.setAuthModalOpen(true) },
                            onOpenSettings = { viewModel.setSettingsModalOpen(true) }
                        )
                        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val canFitInspector = maxWidth >= 940.dp &&
                            !isEffectiveTimelineFullScreen &&
                            !isExpandedProjectWorkspace
                        val showInspectorPane = canFitInspector && isInspectorVisible

                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.TopStart
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(
                                            start = if (showSidebar) 4.dp else 0.dp,
                                            end = when {
                                                showInspectorPane -> 8.dp
                                                showSidebar -> 16.dp
                                                else -> 0.dp
                                            }
                                        )
                                        .then(
                                            if (showInspectorPane) {
                                                Modifier
                                            } else {
                                                Modifier.widthIn(
                                                    max = when {
                                                        isEffectiveTimelineFullScreen -> 4000.dp
                                                        isExpandedProjectWorkspace -> 2400.dp
                                                        isWideScreen -> 1440.dp
                                                        else -> 2000.dp
                                                    }
                                                )
                                            }
                                        )
                                        .fillMaxSize()
                                ) {
                        if (!isEffectiveTimelineFullScreen) {
                            ThingsTopHeaderAndQuickFindSection(
                                viewModel = viewModel,
                                isQuickFindOpen = isQuickFindOpen,
                                areas = areas,
                                activeProjects = activeProjects,
                                activeLocale = activeLocale
                            )
                        }

                    if (!isEffectiveTimelineFullScreen) {
                        focusTimerState?.let { timerState ->
                            ThingsFocusTimerBanner(
                                timerState = timerState,
                                onTogglePause = { viewModel.toggleFocusTimerPause() },
                                onAddFiveMinutes = { viewModel.addFiveMinutesToFocusTimer() },
                                onCompleteTask = { viewModel.completeFocusTimerTask() },
                                onStopTimer = { viewModel.stopFocusTimer() },
                                onClickTask = { viewModel.toggleTaskExpansion(timerState.taskId) }
                            )
                        }
                    }

                    if (isEffectiveTimelineFullScreen && isTodayTimelineActive) {
                        // Clean Single-Row Full Screen Today Timeline Header Bar
                        val plannedFmt = formatDurationCompact(todayCapacitySummary.first) ?: "${todayCapacitySummary.first}m"
                        val freeFmt = formatDurationCompact(todayCapacitySummary.second) ?: "0m"
                        val exitFsLabel = stringResource(R.string.v30_timeline_exit_fullscreen)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 3.dp)
                                ) {
                                    SmartViewIcon(
                                        viewType = SmartViewType.TODAY,
                                        modifier = Modifier.size(20.dp),
                                        isSelected = true
                                    )
                                    Text(
                                        text = stringResource(SmartViewType.TODAY.titleRes),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.primaryText,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (todayCapacitySummary.first > 0) {
                                    val capacityText = if (calendarEventsEnabledTop) {
                                        stringResource(R.string.v30_today_capacity_summary, plannedFmt, freeFmt)
                                    } else {
                                        stringResource(R.string.v30_today_planned_only, plannedFmt)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.thingsBlueSoft,
                                        border = BorderStroke(0.75.dp, colors.thingsBlue.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Schedule,
                                                contentDescription = null,
                                                tint = colors.thingsBlue,
                                                modifier = Modifier.size(13.5.dp)
                                            )
                                            Text(
                                                text = capacityText,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.thingsBlue,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            ThingsHoverTooltip(text = exitFsLabel) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.setTimelineFullScreen(false)
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.thingsBlueSoft,
                                    border = BorderStroke(0.75.dp, colors.thingsBlue.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CloseFullscreen,
                                            contentDescription = exitFsLabel,
                                            tint = colors.thingsBlue,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = exitFsLabel,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.thingsBlue,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                    }

                    if (!isEffectiveTimelineFullScreen) {
                        // On Foldables & Googlebook (>= 700dp), show horizontal strip when the permanent sidebar is collapsed
                        if (isWideScreen && !showSidebar) {
                            SmartViewNavigationStrip(
                                navTarget = navTarget,
                                areas = areas,
                                projects = activeProjects,
                                savedPerspectives = savedPerspectives,
                                showPerspectivesInSidebar = showPerspectivesInSidebar,
                                dragController = projectDragController,
                                onSelectSmartView = { viewModel.selectSmartView(it) },
                                onSelectArea = { viewModel.selectArea(it) },
                                onSelectProject = { viewModel.selectProject(it) },
                                onSelectPerspective = { viewModel.selectPerspective(it) },
                                onCreateNewPerspective = {
                                    editingPerspective = null
                                    showPerspectiveEditorDialog = true
                                },
                                onOpenAreaManager = { viewModel.setAreaManagerOpen(true) },
                                onOpenTagManager = { viewModel.setTagManagerOpen(true) },
                                onCreateNewProject = {
                                    projectDialogPreselectedAreaId = currentArea?.id
                                    editingProject = null
                                    showCreateProjectDialog = true
                                },
                                onOpenWeeklyReview = { viewModel.openWeeklyReviewModal() },
                                onOpenTemplatesAndImport = {
                                    viewModel.openTemplatesModal(
                                        initialTab = 0,
                                        defaultAreaId = currentArea?.id ?: currentProject?.areaId
                                    )
                                }
                            )
                        }

                        // View Title Header (Smart View, Project with 3-Mode Workspace Switcher & Target Date, Area, or Custom Perspective)
                        ViewTitleHeader(
                            navTarget = navTarget,
                            currentProject = currentProject,
                            projectWorkspaceMode = currentProjectWorkspaceMode,
                            onSelectProjectWorkspaceMode = { nextMode ->
                                currentProject?.let { proj -> viewModel.updateProjectViewMode(proj.id, nextMode) }
                            },
                            currentArea = currentArea,
                            currentPerspective = currentPerspective,
                            areas = areas,
                            projects = activeProjects,
                            projectAttachments = projectAttachments,
                            locale = activeLocale,
                            trashItemCount = deletedTasks.size + deletedProjects.size,
                            isTodayTimelineMode = isTodayTimelineMode,
                            onToggleTodayTimelineMode = { viewModel.setTodayTimelineMode(it) },
                            onToggleTimelineFullScreen = { viewModel.toggleTimelineFullScreen() },
                            todayPlannedMinutes = todayCapacitySummary.first,
                            todayFreeMinutes = todayCapacitySummary.second,
                            calendarEventsEnabled = calendarEventsEnabledTop,
                            viewSortMode = viewSortMode,
                            onSelectViewSortMode = { viewModel.setViewSortMode(it) },
                            onEditPerspective = { persp ->
                                editingPerspective = persp
                                showPerspectiveEditorDialog = true
                            },
                            onDeletePerspective = { pId ->
                                viewModel.deletePerspective(pId)
                            },
                            onSaveProjectAsTemplate = { pId, toastMsg ->
                                viewModel.saveProjectAsTemplate(pId, toastMsg)
                            },
                            onOpenExternalImportExport = { areaId ->
                                viewModel.openTemplatesModal(
                                    initialTab = 2,
                                    defaultAreaId = areaId ?: currentArea?.id ?: currentProject?.areaId
                                )
                            },
                            onOpenStalledProjectReview = {
                                viewModel.openWeeklyReviewModal(initialStep = 2)
                            },
                            onMarkProjectReviewed = { pId ->
                                viewModel.markProjectReviewed(pId)
                            },
                            onUpdateProjectDeadline = { pId, dl -> viewModel.updateProjectDeadline(pId, dl) },
                            onUpdateProjectArea = { pId, aId -> viewModel.updateProjectArea(pId, aId) },
                            onEditProject = { proj ->
                                editingProject = proj
                                showCreateProjectDialog = true
                            },
                            onDuplicateProject = { pId -> viewModel.duplicateProject(pId) },
                            onCompleteProject = { pId -> viewModel.completeProject(pId) },
                            onMoveProject = { pId, up -> viewModel.moveProject(pId, up) },
                            onDeleteProject = { pId -> viewModel.deleteProject(pId) },
                            onAddProjectAttachment = { pId, fName, mime, bytes ->
                                viewModel.addProjectAttachment(pId, fName, mime, bytes)
                            },
                            onDeleteProjectAttachment = { attId -> viewModel.deleteAttachment(attId) },
                            onShowMessage = { msg -> viewModel.showUiMessage(msg) },
                            onRequestEmptyTrash = { showEmptyTrashConfirmDialog = true },
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
                    }

                    ThingsMainTaskLazyList(
                        viewModel = viewModel,
                        navTarget = navTarget,
                        isProjectView = isProjectView,
                        isAreaView = isAreaView,
                        isQuickFindOpen = isQuickFindOpen,
                        isEffectiveTimelineFullScreen = isEffectiveTimelineFullScreen,
                        currentProject = currentProject,
                        projectWorkspaceMode = currentProjectWorkspaceMode,
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
                        projectDragController = projectDragController,
                        activeLocale = activeLocale,
                        externalDialogTriggerToken = externalDialogTriggerToken,
                        externalDialogTriggerType = externalDialogTriggerType,
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

                            if (showInspectorPane) {
                                VerticalDivider(
                                    color = colors.hairlineDivider,
                                    thickness = 1.dp,
                                    modifier = Modifier.fillMaxHeight()
                                )
                                WorkspaceInspectorPanel(
                                    selectedTask = selectedInspectorTask,
                                    primaryTasks = allVisibleViewTasks,
                                    todayTasks = todayTasks,
                                    eveningTasks = eveningTasks,
                                    allOpenTasks = allOpenTasks,
                                    todayCalendarEvents = rawTodayCalendarEventsTop,
                                    calendarEventsEnabled = calendarEventsEnabledTop,
                                    todayPlannedMinutes = todayCapacitySummary.first,
                                    todayFreeMinutes = todayCapacitySummary.second,
                                    completedTodayCount = completedTodayCount,
                                    areas = areas,
                                    projects = activeProjects,
                                    todayDate = todayDateTop,
                                    locale = activeLocale,
                                    activeFocusTaskId = focusTimerState?.taskId,
                                    onCloseInspector = { viewModel.setInspectorVisible(false) },
                                    onClearSelection = { viewModel.clearTaskSelection() },
                                    onSelectTask = { taskId -> viewModel.selectSingleTask(taskId) },
                                    onExpandTask = { taskId -> viewModel.expandTask(taskId) },
                                    onUpdateTaskStatus = { taskId, status ->
                                        viewModel.updateTaskStatus(taskId, status)
                                    },
                                    onUpdateTaskTitle = { task, newTitle ->
                                        viewModel.updateTaskTitleAndNotes(task, newTitle, task.notes)
                                    },
                                    onUpdateTaskNotes = { task, newNotes ->
                                        viewModel.updateTaskTitleAndNotes(task, task.title, newNotes)
                                    },
                                    onUpdateTaskSchedule = { task, state, whenDate ->
                                        viewModel.updateTaskSchedule(task, state, whenDate)
                                    },
                                    onTogglePin = { taskId -> viewModel.toggleTaskPinned(taskId) },
                                    onToggleMilestone = { taskId, isMilestone ->
                                        viewModel.setTaskMilestone(taskId, isMilestone)
                                    },
                                    onUpdateDuration = { taskId, mins ->
                                        viewModel.setStandaloneTaskDuration(taskId, mins)
                                    },
                                    onUpdateEffort = { taskId, effort ->
                                        viewModel.setTaskEffortLevel(taskId, effort)
                                    },
                                    onStartFocusTimer = { task ->
                                        viewModel.startFocusTimer(task.id, task.title, task.durationMinutes)
                                    },
                                    onAddChecklistItem = { taskId, title ->
                                        viewModel.addChecklistItem(taskId, title)
                                    },
                                    onToggleChecklistItem = { itemId, completed ->
                                        viewModel.toggleChecklistItem(itemId, completed)
                                    },
                                    onDeleteChecklistItem = { itemId ->
                                        viewModel.deleteChecklistItem(itemId)
                                    },
                                    onNavigateToProject = { projectId -> viewModel.selectProject(projectId) },
                                    onNavigateToArea = { areaId -> viewModel.selectArea(areaId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // App Version Badge at bottom-left when Sidebar is hidden or on phones (hidden when soft keyboard, selection bar, or full-screen timeline is open)
        val overlayDensity = LocalDensity.current
        val isKeyboardOpen = WindowInsets.ime.getBottom(overlayDensity) > 0
        var multiSelectBarHeightPx by remember { mutableIntStateOf(0) }
        LaunchedEffect(showMultiSelectionBar, isEffectiveTimelineFullScreen) {
            if (!showMultiSelectionBar || isEffectiveTimelineFullScreen) {
                multiSelectBarHeightPx = 0
            }
        }
        val activeSelectionBarClearanceDp = if (!isEffectiveTimelineFullScreen && showMultiSelectionBar) {
            with(overlayDensity) { multiSelectBarHeightPx.toDp() }.coerceAtLeast(64.dp) + 8.dp
        } else {
            0.dp
        }

        if (!showSidebar && !isKeyboardOpen && !isEffectiveTimelineFullScreen && !showMultiSelectionBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
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

        // Floating Multi-Task Selection Action Bar (shown ONLY in explicit multi-select mode or when >= 2 tasks selected)
        if (!isEffectiveTimelineFullScreen && showMultiSelectionBar) {
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
                onScheduleSomeday = {
                    viewModel.batchScheduleSelectedTasks(allVisibleViewTasks, ScheduleState.SOMEDAY, null)
                },
                onOpenBatchTags = { showBatchTagPickerDialog = true },
                onBatchPin = { viewModel.batchTogglePinSelectedTasks() },
                onBatchDuplicate = { viewModel.batchDuplicateSelectedTasks() },
                onOpenBatchMove = { showBatchMoveDialog = true },
                onBatchComplete = { viewModel.batchCompleteSelectedTasks() },
                onBatchDelete = { viewModel.batchDeleteSelectedTasks() },
                onClearSelection = { viewModel.clearTaskSelection() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .onSizeChanged { size -> multiSelectBarHeightPx = size.height }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }

        // 5-Second Undo Snackbar Banner (also triggered by Ctrl+Z)
        ThingsUndoSnackbarBanner(
            message = undoSnackbarMessage,
            onUndo = { viewModel.performUndo() },
            onDismiss = { viewModel.dismissUndoSnackbar() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (showMultiSelectionBar) activeSelectionBarClearanceDp + 6.dp else 18.dp)
        )

        // v2 Celebration Toast / Snackbar (Weekly Review completed, Template saved, etc.)
        ThingsCelebrationSnackbarBanner(
            message = celebrationToastMessage,
            onDismiss = { viewModel.dismissCelebrationToast() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    bottom = when {
                        !undoSnackbarMessage.isNullOrBlank() && showMultiSelectionBar -> activeSelectionBarClearanceDp + 62.dp
                        showMultiSelectionBar -> activeSelectionBarClearanceDp + 6.dp
                        !undoSnackbarMessage.isNullOrBlank() -> 76.dp
                        else -> 22.dp
                    }
                )
        )

        // Expandable Context-Sensitive "Magic Plus" Menu + Drag & Drop FAB (bottom-right, lifted cleanly above Selection Bar when active)
        if (!isEffectiveTimelineFullScreen) {
            MagicPlusOverlaySection(
                viewModel = viewModel,
                navTarget = navTarget,
                currentArea = currentArea,
                primaryTasks = primaryTasks,
                projectHeadings = projectHeadings,
                areasCount = areas.size,
                projectDragController = projectDragController,
                showInspectorPane = outerShowInspectorPane,
                extraBottomClearance = activeSelectionBarClearanceDp,
                onDragStateChanged = { state -> magicDragState = state },
                onRequestCreateProject = { areaId ->
                    projectDialogPreselectedAreaId = areaId
                    editingProject = null
                    showCreateProjectDialog = true
                }
            )
        }

        // Phone-Only (< 700dp) Slide-Over Hamburger Navigation Drawer
        if (!isWideScreen) {
            AnimatedVisibility(
                visible = isMobileDrawerOpen,
                enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(animationSpec = tween(durationMillis = 180))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.44f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isMobileDrawerOpen = false }
                        )
                )
            }

            AnimatedVisibility(
                visible = isMobileDrawerOpen,
                enter = slideInHorizontally(animationSpec = tween(durationMillis = 220)) { -it } +
                    fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = slideOutHorizontally(animationSpec = tween(durationMillis = 200)) { -it } +
                    fadeOut(animationSpec = tween(durationMillis = 160))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .shadow(16.dp)
                ) {
                    ThingsChromebookSidebar(
                        viewModel = viewModel,
                        navTarget = navTarget,
                        smartViewCounts = smartViewCounts,
                        areas = areas,
                        projects = activeProjects,
                        savedPerspectives = savedPerspectives,
                        showPerspectivesInSidebar = showPerspectivesInSidebar,
                        allOpenTasks = allOpenTasks,
                        openTaskIds = openTaskIds,
                        todayDayStartMillis = todayDayStartMillis,
                        dragController = projectDragController,
                        onSelectSmartView = {
                            isMobileDrawerOpen = false
                            viewModel.selectSmartView(it)
                        },
                        onSelectArea = {
                            isMobileDrawerOpen = false
                            viewModel.selectArea(it)
                        },
                        onSelectProject = {
                            isMobileDrawerOpen = false
                            viewModel.selectProject(it)
                        },
                        onSelectPerspective = {
                            isMobileDrawerOpen = false
                            viewModel.selectPerspective(it)
                        },
                        onCreateNewPerspective = {
                            isMobileDrawerOpen = false
                            editingPerspective = null
                            showPerspectiveEditorDialog = true
                        },
                        onEditPerspective = { persp ->
                            isMobileDrawerOpen = false
                            editingPerspective = persp
                            showPerspectiveEditorDialog = true
                        },
                        onOpenAreaManager = {
                            isMobileDrawerOpen = false
                            viewModel.setAreaManagerOpen(true)
                        },
                        onOpenTagManager = {
                            isMobileDrawerOpen = false
                            viewModel.setTagManagerOpen(true)
                        },
                        onCreateNewProject = { areaId ->
                            isMobileDrawerOpen = false
                            projectDialogPreselectedAreaId = areaId
                            editingProject = null
                            showCreateProjectDialog = true
                        },
                        onOpenWeeklyReview = {
                            isMobileDrawerOpen = false
                            viewModel.openWeeklyReviewModal()
                        },
                        onOpenTemplatesAndImport = {
                            isMobileDrawerOpen = false
                            viewModel.openTemplatesModal(
                                initialTab = 0,
                                defaultAreaId = currentArea?.id ?: currentProject?.areaId
                            )
                        },
                        onOpenExternalImportExport = { areaId ->
                            isMobileDrawerOpen = false
                            viewModel.openTemplatesModal(
                                initialTab = 2,
                                defaultAreaId = areaId ?: currentArea?.id ?: currentProject?.areaId
                            )
                        },
                        onCloseDrawer = { isMobileDrawerOpen = false }
                    )
                    VerticalDivider(
                        color = colors.hairlineDivider,
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
        }

        // Compact Top Drop Dock on narrower screens (< 700dp) while dragging To-Dos
        if (!isWideScreen && projectDragController.session?.kind == ProjectDragKind.MOVE_TASK) {
            CompactMobileDragDropDock(
                controller = projectDragController,
                areas = areas,
                projects = activeProjects,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            )
        }

        // Full-window Soft Bento Floating Drag Ghost Card (with "%1$d To-Dos" multi-select badge)
        WorkspaceDragFloatingOverlay(controller = projectDragController)

        // Unified Soft Bento Task Modal across ALL views (v3.0.0-beta.5 flash-free in-window Compose overlay)
        val deletedTaskIdsSet = remember(deletedTasks) { deletedTasks.mapTo(HashSet()) { it.id } }
        val modalTask = remember(
            expandedTaskId,
            primaryTasks,
            allOpenTasks,
            logbookTasks,
            somedayTasks,
            inboxTasks,
            todayTasks,
            eveningTasks,
            upcomingTasks,
            anytimeTasks,
            projectTasks,
            areaTasks,
            deletedTaskIdsSet
        ) {
            resolveUnifiedModalTask(
                expandedTaskId = expandedTaskId,
                primaryTasks = primaryTasks,
                allOpenTasks = allOpenTasks,
                logbookTasks = logbookTasks,
                somedayTasks = somedayTasks,
                inboxTasks = inboxTasks,
                todayTasks = todayTasks,
                eveningTasks = eveningTasks,
                upcomingTasks = upcomingTasks,
                anytimeTasks = anytimeTasks,
                projectTasks = projectTasks,
                areaTasks = areaTasks,
                deletedTaskIds = deletedTaskIdsSet
            )
        }
        var lastResolvedModalTask by remember { mutableStateOf<TaskItem?>(null) }
        if (modalTask != null) {
            lastResolvedModalTask = modalTask
        }
        val isModalVisible = !(biometricAppLockEnabled && !isAppUnlocked) && modalTask != null
        val maxModalHeight = (configuration.screenHeightDp.dp - 40.dp).coerceIn(300.dp, 680.dp)

        AnimatedVisibility(
            visible = isModalVisible,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(35f),
            enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing))
        ) {
            val activeModalTask = modalTask ?: lastResolvedModalTask
            if (activeModalTask != null) {
                val magicBreakdownTaskIdTop by viewModel.magicBreakdownTaskId.collectAsState()
                BackHandler(enabled = isModalVisible) {
                    viewModel.collapseExpandedTask()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(35f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.44f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.collapseExpandedTask() }
                            )
                    )
                    Surface(
                        modifier = Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .animateEnterExit(
                                enter = scaleIn(
                                    initialScale = 0.96f,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                ),
                                exit = scaleOut(
                                    targetScale = 0.97f,
                                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                                )
                            )
                            .widthIn(max = 780.dp)
                            .fillMaxWidth(0.95f)
                            .heightIn(max = maxModalHeight)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                            .onPreviewKeyEvent { modalKeyEvent ->
                                if (modalKeyEvent.type == KeyEventType.KeyDown) {
                                    val ctrlOrMeta = modalKeyEvent.isCtrlPressed || modalKeyEvent.isMetaPressed
                                    when {
                                        modalKeyEvent.key == Key.Escape -> {
                                            viewModel.collapseExpandedTask()
                                            true
                                        }
                                        ctrlOrMeta && (modalKeyEvent.key == Key.Enter || modalKeyEvent.key == Key.NumPadEnter) -> {
                                            viewModel.collapseExpandedTask()
                                            true
                                        }
                                        ctrlOrMeta && !modalKeyEvent.isShiftPressed && modalKeyEvent.key == Key.S -> {
                                            externalDialogTriggerType = "WHEN"
                                            externalDialogTriggerToken++
                                            true
                                        }
                                        ctrlOrMeta && modalKeyEvent.isShiftPressed && modalKeyEvent.key == Key.D -> {
                                            externalDialogTriggerType = "DEADLINE"
                                            externalDialogTriggerToken++
                                            true
                                        }
                                        ctrlOrMeta && modalKeyEvent.isShiftPressed && modalKeyEvent.key == Key.M -> {
                                            externalDialogTriggerType = "MOVE"
                                            externalDialogTriggerToken++
                                            true
                                        }
                                        ctrlOrMeta && !modalKeyEvent.isShiftPressed && modalKeyEvent.key == Key.D -> {
                                            viewModel.duplicateTask(activeModalTask.id)
                                            true
                                        }
                                        ctrlOrMeta && (modalKeyEvent.key == Key.Backspace || modalKeyEvent.key == Key.Delete) -> {
                                            viewModel.deleteTask(activeModalTask.id)
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            },
                        shape = RoundedCornerShape(22.dp),
                        color = colors.cardSurface,
                        border = BorderStroke(width = 1.dp, color = colors.hairlineDivider),
                        tonalElevation = 8.dp,
                        shadowElevation = 16.dp
                    ) {
                        ThingsTaskRowItem(
                            task = activeModalTask,
                            viewModel = viewModel,
                            allTags = allTags,
                            areas = areas,
                            activeProjects = activeProjects,
                            allHeadings = allHeadingsTop,
                            expandedTaskId = expandedTaskId,
                            forceModalExpanded = true,
                            isQuickFindOpen = isQuickFindOpen,
                            selectedTaskIds = selectedTaskIds,
                            allVisibleTaskIds = allVisibleTaskIds,
                            magicBreakdownTaskId = magicBreakdownTaskIdTop,
                            activeLocale = activeLocale,
                            externalDialogTriggerToken = externalDialogTriggerToken,
                            externalDialogTriggerType = externalDialogTriggerType
                        )
                    }
                }
            }
        }

        if (!(biometricAppLockEnabled && !isAppUnlocked)) {
            ThingsModalsOverlay(
                viewModel = viewModel,
                areas = areas,
                activeProjects = activeProjects,
                allTags = allTags,
                allVisibleViewTasks = allVisibleViewTasks,
                inboxTasks = inboxTasks,
                todayTasks = todayTasks,
                eveningTasks = eveningTasks,
                upcomingTasks = upcomingTasks,
                anytimeTasks = anytimeTasks,
                somedayTasks = somedayTasks,
                logbookTasks = logbookTasks,
                activeLocale = activeLocale,
                showBatchMoveDialog = showBatchMoveDialog,
                onDismissBatchMoveDialog = { showBatchMoveDialog = false },
                showBatchTagPickerDialog = showBatchTagPickerDialog,
                onDismissBatchTagPickerDialog = { showBatchTagPickerDialog = false },
                showPerspectiveEditorDialog = showPerspectiveEditorDialog,
                editingPerspective = editingPerspective,
                savedPerspectivesCount = savedPerspectives.size,
                onDismissPerspectiveEditorDialog = {
                    showPerspectiveEditorDialog = false
                    editingPerspective = null
                },
                showCreateProjectDialog = showCreateProjectDialog,
                editingProject = editingProject,
                projectDialogPreselectedAreaId = projectDialogPreselectedAreaId,
                onDismissCreateProjectDialog = {
                    showCreateProjectDialog = false
                    editingProject = null
                },
                showEmptyTrashConfirmDialog = showEmptyTrashConfirmDialog,
                onDismissEmptyTrashConfirmDialog = { showEmptyTrashConfirmDialog = false }
            )
        }

        if (biometricAppLockEnabled && !isAppUnlocked) {
            ThingsBiometricLockOverlay(
                onUnlocked = { viewModel.markAppUnlocked() }
            )
        }
    }
}

internal fun resolveUnifiedModalTask(
    expandedTaskId: String?,
    primaryTasks: List<TaskItem>,
    allOpenTasks: List<TaskItem>,
    logbookTasks: List<TaskItem>,
    somedayTasks: List<TaskItem>,
    inboxTasks: List<TaskItem>,
    todayTasks: List<TaskItem>,
    eveningTasks: List<TaskItem>,
    upcomingTasks: List<TaskItem>,
    anytimeTasks: List<TaskItem>,
    projectTasks: List<TaskItem> = emptyList(),
    areaTasks: List<TaskItem> = emptyList(),
    deletedTaskIds: Set<String> = emptySet()
): TaskItem? {
    val targetId = expandedTaskId?.takeIf { it.isNotBlank() } ?: return null
    if (targetId in deletedTaskIds) return null
    val pools = listOf(
        primaryTasks,
        allOpenTasks,
        logbookTasks,
        somedayTasks,
        inboxTasks,
        todayTasks,
        eveningTasks,
        upcomingTasks,
        anytimeTasks,
        projectTasks,
        areaTasks
    )
    for (pool in pools) {
        val match = pool.firstOrNull { it.id == targetId && !it.isDeleted }
        if (match != null) return match
    }
    return null
}

@Composable
private fun MagicPlusOverlaySection(
    viewModel: ThingsbookViewModel,
    navTarget: ActiveNavigationTarget,
    currentArea: Area?,
    primaryTasks: List<TaskItem>,
    projectHeadings: List<Heading>,
    areasCount: Int,
    projectDragController: ProjectDragDropController,
    showInspectorPane: Boolean = false,
    extraBottomClearance: Dp = 0.dp,
    onDragStateChanged: (MagicPlusDragState) -> Unit,
    onRequestCreateProject: (String?) -> Unit
) {
    val isMagicPlusMenuOpen by viewModel.isMagicPlusMenuOpen.collectAsState()
    val isProjectView = navTarget is ActiveNavigationTarget.ProjectDetail
    val positionalHandler = remember(isProjectView, projectDragController, viewModel) {
        if (!isProjectView) {
            null
        } else {
            object : MagicPlusPositionalDropHandler {
                override val listLeftWindowX: Float?
                    get() = projectDragController.activeListWindowLeft

                private fun kindFor(mode: MagicPlusDropMode) =
                    if (mode == MagicPlusDropMode.INSERT_HEADING) ProjectDragKind.NEW_HEADING else ProjectDragKind.NEW_TODO

                override fun onDragStart(windowCenter: Offset, mode: MagicPlusDropMode) {
                    projectDragController.start(kindFor(mode), windowCenter)
                }

                override fun onDragMove(windowCenter: Offset, mode: MagicPlusDropMode) {
                    projectDragController.move(windowCenter, kindFor(mode))
                }

                override fun onDragEnd(mode: MagicPlusDropMode) {
                    val session = projectDragController.finish()
                    if (session?.target != null) {
                        applyProjectDrop(viewModel, session, projectDragController)
                    } else if (mode == MagicPlusDropMode.INSERT_HEADING) {
                        viewModel.createHeadingInCurrentProject(projectDragController.headings.size, "")
                    } else {
                        val ungrouped = projectDragController.sectionTasks[null].orEmpty()
                        viewModel.createTaskInProjectSection(null, ungrouped, ungrouped.size)
                    }
                }

                override fun onDragCancel() {
                    projectDragController.cancel()
                }
            }
        }
    }
    val contextMode = when (navTarget) {
        is ActiveNavigationTarget.ProjectDetail -> MagicPlusContextMode.PROJECT_DETAIL
        is ActiveNavigationTarget.AreaDetail -> MagicPlusContextMode.AREA_DETAIL
        is ActiveNavigationTarget.PerspectiveDetail -> MagicPlusContextMode.SMART_VIEW
        is ActiveNavigationTarget.SmartView -> {
            if (navTarget.type == SmartViewType.LOGBOOK || navTarget.type == SmartViewType.TRASH) {
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
                viewModel.createTaskAtPosition(primaryTasks, 0, "", forceTopUngroupedInProject = true)
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
                .navigationBarsPadding()
                .padding(
                    end = if (showInspectorPane) 24.dp + 353.dp else 24.dp,
                    bottom = 24.dp + extraBottomClearance
                ),
            positionalDropHandler = positionalHandler
        )
    }
}

/** Applies a finished 2D Workspace drag session (external container, Kanban column, intra-list reorder, or Project section). */
private fun applyWorkspaceSessionDrop(
    viewModel: ThingsbookViewModel,
    session: ProjectDragSession,
    controller: ProjectDragDropController,
    currentViewTasks: List<TaskItem>
) {
    if (session.kind != ProjectDragKind.MOVE_TASK) {
        applyProjectDrop(viewModel, session, controller)
        return
    }
    val draggedIds = session.effectiveTaskIds
    if (draggedIds.isEmpty()) return

    val externalDest = session.externalDestination
    if (externalDest != null) {
        val boardOrProjSections = if (controller.isBoardActive) {
            controller.boardSectionTasks
        } else {
            controller.sectionTasks
        }
        viewModel.handleWorkspaceDrop(
            taskIds = draggedIds,
            destination = externalDest,
            currentViewTasks = currentViewTasks,
            projectSectionTasks = boardOrProjSections
        )
        return
    }

    if (session.target != null) {
        applyProjectDrop(viewModel, session, controller)
    }
}

/** Applies a finished Project drag session (Magic Plus or task handle) to the data layer. */
private fun applyProjectDrop(
    viewModel: ThingsbookViewModel,
    session: ProjectDragSession,
    controller: ProjectDragDropController
) {
    val target = session.target ?: return
    val section = controller.sectionTasks[target.headingId].orEmpty()
    when (session.kind) {
        ProjectDragKind.NEW_TODO ->
            viewModel.createTaskInProjectSection(target.headingId, section, target.indexInSection)
        ProjectDragKind.NEW_HEADING ->
            viewModel.createHeadingInCurrentProject(target.headingInsertIndex, "")
        ProjectDragKind.MOVE_TASK -> {
            val ids = session.effectiveTaskIds
            val projectId = (viewModel.navigationTarget.value as? ActiveNavigationTarget.ProjectDetail)?.projectId
            if (ids.isNotEmpty() && !projectId.isNullOrBlank()) {
                viewModel.moveTasksWithinProjectSection(
                    taskIds = ids,
                    projectId = projectId,
                    headingId = target.headingId,
                    sectionTasks = section,
                    indexInSection = target.indexInSection
                )
            }
        }
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
private fun ThingsAutoUpdateBanner(
    updateState: AppUpdateUiState,
    onUpdateNow: () -> Unit,
    onInstallNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.thingsBlue.copy(alpha = 0.38f), RoundedCornerShape(12.dp)),
        color = colors.thingsBlueSoft,
        shape = RoundedCornerShape(12.dp)
    ) {
        when (updateState) {
            is AppUpdateUiState.UpdateAvailable -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdateAlt,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_banner_title, updateState.release.versionName),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.thingsBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (updateState.release.releaseNotes.isNotBlank()) {
                            Text(
                                text = updateState.release.releaseNotes.lineSequence().firstOrNull().orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Button(
                        onClick = onUpdateNow,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.update_banner_action_btn),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            is AppUpdateUiState.Downloading -> {
                val pct = updateState.progressPercent
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (pct != null) {
                                stringResource(
                                    R.string.settings_updates_downloading,
                                    updateState.release.versionName,
                                    pct
                                )
                            } else {
                                stringResource(
                                    R.string.settings_updates_downloading_indeterminate,
                                    updateState.release.versionName
                                )
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.thingsBlue
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
                    Spacer(modifier = Modifier.height(5.dp))
                    if (pct != null) {
                        LinearProgressIndicator(
                            progress = { (pct / 100f).coerceIn(0f, 1f) },
                            color = colors.thingsBlue,
                            trackColor = colors.hairlineDivider,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            color = colors.thingsBlue,
                            trackColor = colors.hairlineDivider,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            is AppUpdateUiState.ReadyToInstall -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdateAlt,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_updates_ready_title, updateState.release.versionName),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.thingsBlue,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = onInstallNow,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_updates_install_now_btn),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun ThingsTopHeaderAndQuickFindSection(
    viewModel: ThingsbookViewModel,
    isQuickFindOpen: Boolean,
    areas: List<Area>,
    activeProjects: List<Project>,
    activeLocale: Locale
) {
    val quickFindQuery by viewModel.quickFindQuery.collectAsState()
    val quickFindFilter by viewModel.quickFindFilter.collectAsState()
    val quickFindTasks by viewModel.quickFindTaskResults.collectAsState()
    val quickFindProjects by viewModel.quickFindProjectResults.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val uiErrorMessage by viewModel.uiErrorMessage.collectAsState()
    val hasSampleTasks by viewModel.hasSampleTasks.collectAsState()
    val isInitialSetupBannerDismissed by viewModel.isInitialSetupBannerDismissed.collectAsState()
    val currentUserIdentifier by viewModel.currentUserIdentifier.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()

    val showAutoUpdateBanner = when (val st = appUpdateState) {
        is AppUpdateUiState.UpdateAvailable -> st.showPromptBanner
        is AppUpdateUiState.Downloading, is AppUpdateUiState.ReadyToInstall -> true
        else -> false
    }
    AnimatedVisibility(
        visible = showAutoUpdateBanner,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        ThingsAutoUpdateBanner(
            updateState = appUpdateState,
            onUpdateNow = {
                val avail = appUpdateState as? AppUpdateUiState.UpdateAvailable ?: return@ThingsAutoUpdateBanner
                viewModel.startUpdateDownloadAndInstall(avail.release)
            },
            onInstallNow = { viewModel.installDownloadedUpdate() },
            onDismiss = {
                if (appUpdateState is AppUpdateUiState.Downloading) {
                    viewModel.cancelUpdateDownload()
                } else {
                    viewModel.dismissUpdateBanner()
                }
            }
        )
    }

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
            filter = quickFindFilter,
            taskResults = quickFindTasks,
            projectResults = quickFindProjects,
            areas = areas,
            allProjects = activeProjects,
            allTags = allTags,
            locale = activeLocale,
            onQueryChange = { viewModel.updateQuickFindQuery(it) },
            onFilterChange = { viewModel.updateQuickFindFilter(it) },
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
                    task.status != TaskStatus.OPEN -> viewModel.selectSmartView(SmartViewType.LOGBOOK)
                    task.areaId != null && task.scheduleState == ScheduleState.ANYTIME -> viewModel.selectArea(task.areaId)
                    task.scheduleState == ScheduleState.INBOX -> viewModel.selectSmartView(SmartViewType.INBOX)
                    task.scheduleState == ScheduleState.TODAY || task.scheduleState == ScheduleState.THIS_EVENING ->
                        viewModel.selectSmartView(SmartViewType.TODAY)
                    task.scheduleState == ScheduleState.UPCOMING -> viewModel.selectSmartView(SmartViewType.UPCOMING)
                    task.scheduleState == ScheduleState.SOMEDAY -> viewModel.selectSmartView(SmartViewType.SOMEDAY)
                    else -> viewModel.selectSmartView(SmartViewType.ANYTIME)
                }
                viewModel.expandTask(task.id)
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
    forceModalExpanded: Boolean = false,
    isQuickFindOpen: Boolean,
    selectedTaskIds: Set<String>,
    allVisibleTaskIds: List<String>,
    magicBreakdownTaskId: String?,
    activeLocale: Locale,
    externalDialogTriggerToken: Int = 0,
    externalDialogTriggerType: String? = null,
    onRequestEnsureVisible: ((Boolean) -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentEpochDay by viewModel.currentEpochDay.collectAsState()
    val navTarget by viewModel.navigationTarget.collectAsState()
    val activeModalTaskId by viewModel.expandedTaskId.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val tagSortMode by viewModel.tagSortMode.collectAsState()
    val tagUsageCounts by viewModel.tagUsageCounts.collectAsState()
    val allOpenTasks by viewModel.allOpenTasks.collectAsState()
    val openTaskIds by viewModel.openTaskIds.collectAsState()
    val isTodayView = navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY)
    val isModalExpanded = forceModalExpanded || expandedTaskId == task.id
    androidx.compose.runtime.key(currentEpochDay) {
        ExpandableTaskCard(
            modifier = modifier,
            collapsedTrailingContent = dragHandle,
            onRequestEnsureVisible = onRequestEnsureVisible,
            task = task,
            allTags = allTags,
            tagSortMode = tagSortMode,
            tagUsageCounts = tagUsageCounts,
            allAreas = areas,
            allProjects = activeProjects,
            allHeadings = allHeadings,
            allOpenTasks = allOpenTasks,
            openTaskIds = openTaskIds,
            isExpanded = isModalExpanded,
            isTodayView = isTodayView,
            isSelected = task.id in selectedTaskIds,
            isSelectionModeActive = isMultiSelectMode || selectedTaskIds.size > 1,
            suppressContextMenuExternal = activeModalTaskId != null || isQuickFindOpen,
            onSelectSingle = { viewModel.selectSingleTask(task.id) },
            onToggleSelection = { viewModel.toggleTaskSelection(task.id) },
            onRangeSelect = { viewModel.selectTaskRange(allVisibleTaskIds, task.id) },
            isGeneratingMagicBreakdown = magicBreakdownTaskId == task.id,
            onExpandToggle = {
                if (isModalExpanded) {
                    viewModel.collapseExpandedTask()
                } else {
                    viewModel.expandTask(task.id)
                }
            },
            onStatusChange = { viewModel.updateTaskStatus(task.id, it) },
            onTitleNotesChange = { t, n -> viewModel.updateTaskTitleAndNotes(task, t, n) },
            onScheduleChange = { s, w -> viewModel.updateTaskSchedule(task, s, w) },
            onQuickThisEveningAndClose = { latestTitle, latestNotes ->
                viewModel.quickMoveTaskToThisEveningAndCollapse(task, latestTitle, latestNotes)
            },
            onScheduleWithReminderChange = { s, w, r ->
                viewModel.updateTaskScheduleWithReminder(task, s, w, r)
            },
            onDeadlineChange = { dl -> viewModel.updateTaskDeadline(task, dl) },
            onRecurrenceChange = { rule -> viewModel.updateTaskRecurrence(task.id, rule) },
            onUpdateTimeblock = { s, d -> viewModel.updateTaskTimeblock(task.id, s, d) },
            onTogglePin = { viewModel.toggleTaskPinned(task.id) },
            onToggleMilestone = { viewModel.toggleTaskMilestone(task.id) },
            onUpdateEffortLevel = { eff -> viewModel.setTaskEffortLevel(task.id, eff) },
            onUpdateStandaloneDuration = { mins -> viewModel.setStandaloneTaskDuration(task.id, mins) },
            onUpdateBlockedBy = { blockers -> viewModel.setTaskBlockedBy(task.id, blockers) },
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
            onDuplicateTask = { viewModel.duplicateTask(task.id) },
            onStartFocusTimer = { viewModel.startFocusTimer(task.id, task.title, task.durationMinutes) },
            onAddTaskAttachment = { fName, mime, bytes ->
                viewModel.addTaskAttachment(task.id, fName, mime, bytes)
            },
            onDeleteTaskAttachment = { attId ->
                viewModel.deleteAttachment(attId)
            },
            onShowMessage = { msg ->
                viewModel.showUiMessage(msg)
            },
            externalDialogTriggerToken = if (isModalExpanded) externalDialogTriggerToken else 0,
            externalDialogTriggerType = if (isModalExpanded) externalDialogTriggerType else null,
            onDeleteTask = { viewModel.deleteTask(task.id) },
            locale = activeLocale
        )
    }
}

private fun matchesTaskItemKey(key: Any?, taskId: String): Boolean {
    val k = key?.toString() ?: return false
    return k == taskId || k == "ungrouped_$taskId" || k.endsWith("_$taskId")
}

@Composable
private fun ThingsMainTaskLazyList(
    viewModel: ThingsbookViewModel,
    navTarget: ActiveNavigationTarget,
    isProjectView: Boolean,
    isAreaView: Boolean,
    isQuickFindOpen: Boolean,
    isEffectiveTimelineFullScreen: Boolean = false,
    currentProject: Project? = null,
    projectWorkspaceMode: ProjectWorkspaceMode = ProjectWorkspaceMode.LIST,
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
    projectDragController: ProjectDragDropController,
    activeLocale: Locale,
    externalDialogTriggerToken: Int = 0,
    externalDialogTriggerType: String? = null,
    onRequestCreateProjectInArea: (String) -> Unit,
    onRequestEditProject: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeBottomPx = imeInsets.getBottom(density)
    val isImeVisible = imeBottomPx > 0
    val currentEpochDay by viewModel.currentEpochDay.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val isSelectionModeActive = isMultiSelectMode || selectedTaskIds.size > 1
    val collapsedHeadingIds by viewModel.collapsedHeadingIds.collectAsState()
    val allHeadings by viewModel.allHeadings.collectAsState()
    val headingTaskCounts by viewModel.headingTaskCounts.collectAsState()
    val selectedFilterTagId by viewModel.selectedFilterTagId.collectAsState()
    val viewSortMode by viewModel.viewSortMode.collectAsState()
    val allOpenTasks by viewModel.allOpenTasks.collectAsState()
    val openTaskIds by viewModel.openTaskIds.collectAsState()
    val magicBreakdownTaskId by viewModel.magicBreakdownTaskId.collectAsState()
    val logbookAiReview by viewModel.logbookAiReview.collectAsState()
    val isGeminiBusy by viewModel.isGeminiBusy.collectAsState()
    val geminiErrorMessage by viewModel.geminiErrorMessage.collectAsState()
    val calendarEventsEnabled by viewModel.calendarEventsEnabled.collectAsState()
    val rawTodayCalendarEvents by viewModel.todayCalendarEvents.collectAsState()
    val upcomingCalendarEventsByDate by viewModel.upcomingCalendarEventsByDate.collectAsState()
    val todayCalendarEvents = remember(rawTodayCalendarEvents, currentEpochDay, calendarEventsEnabled) {
        if (!calendarEventsEnabled || rawTodayCalendarEvents.isEmpty()) {
            emptyList()
        } else {
            val todayDate = LocalDate.ofEpochDay(currentEpochDay)
            DeviceCalendarRepository.filterActiveOrUpcomingEvents(
                events = rawTodayCalendarEvents,
                forDate = todayDate,
                todayDate = todayDate,
                nowMillis = System.currentTimeMillis(),
                upcomingOnly = false
            )
        }
    }
    val deletedTasks by viewModel.deletedTasks.collectAsState()
    val deletedProjects by viewModel.deletedProjects.collectAsState()
    val groupTodayByProjectOrArea by viewModel.groupTodayByProjectOrArea.collectAsState()
    val isTodayTimelineMode by viewModel.isTodayTimelineMode.collectAsState()

    // Project sections as displayed: tasks without (valid) heading first, then one section per heading
    val projectSections: Map<String?, List<TaskItem>> = remember(
        isProjectView,
        primaryTasks,
        projectHeadings,
        viewSortMode,
        openTaskIds
    ) {
        if (!isProjectView) {
            emptyMap()
        } else {
            val headingIdSet = projectHeadings.mapTo(HashSet()) { it.id }
            val result = LinkedHashMap<String?, List<TaskItem>>()
            result[null] = sortTasksForView(
                primaryTasks.filter { it.headingId == null || it.headingId !in headingIdSet },
                viewSortMode,
                openTaskIds
            )
            projectHeadings.forEach { h ->
                result[h.id] = sortTasksForView(
                    primaryTasks.filter { it.headingId == h.id },
                    viewSortMode,
                    openTaskIds
                )
            }
            result
        }
    }

    if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY) &&
        isTodayTimelineMode &&
        !isQuickFindOpen
    ) {
        SideEffect {
            projectDragController.isProjectListActive = false
            projectDragController.isGeneralListReorderActive = false
        }
        val combinedTodayTasks = remember(primaryTasks, filteredEveningTasks) {
            (primaryTasks + filteredEveningTasks).distinctBy { it.id }
        }
        ThingsTodayTimelineSection(
            todayTasks = combinedTodayTasks,
            todayCalendarEvents = if (calendarEventsEnabled) todayCalendarEvents else emptyList(),
            onCompleteTask = { taskId ->
                if (!isEffectiveTimelineFullScreen) {
                    viewModel.updateTaskStatus(taskId, TaskStatus.COMPLETED)
                }
            },
            onUpdateTimeblock = { taskId, startMins, durMins ->
                if (!isEffectiveTimelineFullScreen) {
                    viewModel.updateTaskTimeblock(taskId, startMins, durMins)
                }
            },
            onShiftTimeblock = { taskId, deltaMins ->
                if (!isEffectiveTimelineFullScreen) {
                    viewModel.shiftTaskTimeblock(taskId, deltaMins)
                }
            },
            onStartFocusTimer = { targetTask ->
                if (!isEffectiveTimelineFullScreen) {
                    viewModel.startFocusTimer(targetTask.id, targetTask.title, targetTask.durationMinutes)
                }
            },
            onOpenTask = { taskId ->
                if (!isEffectiveTimelineFullScreen) {
                    viewModel.expandTask(taskId)
                }
            },
            isFullScreen = isEffectiveTimelineFullScreen,
            modifier = modifier.imePadding()
        )
        return
    }

    // Milestone 3: Project Board (Kanban) & Project Roadmap (Gantt) Workspaces
    if (isProjectView && currentProject != null && !isQuickFindOpen) {
        if (projectWorkspaceMode == ProjectWorkspaceMode.BOARD) {
            SideEffect {
                projectDragController.isProjectListActive = false
                projectDragController.isGeneralListReorderActive = false
            }
            ProjectBoardWorkspace(
                project = currentProject,
                projectHeadings = projectHeadings,
                projectSections = projectSections,
                headingTaskCounts = headingTaskCounts,
                allOpenTasks = allOpenTasks,
                openTaskIds = openTaskIds,
                selectedTaskIds = selectedTaskIds,
                isSelectionModeActive = isSelectionModeActive,
                allVisibleTaskIds = allVisibleTaskIds,
                controller = projectDragController,
                locale = activeLocale,
                onCreateTaskInSection = { headingId, sectionTasks, indexInSection ->
                    viewModel.createTaskInProjectSection(headingId, sectionTasks, indexInSection)
                },
                onCreatePhase = { title ->
                    viewModel.createHeadingInCurrentProject(projectHeadings.size, title)
                },
                onRenameHeading = { headingId, newTitle ->
                    viewModel.renameHeading(headingId, newTitle)
                },
                onUpdateHeadingStartDate = { headingId, startDate ->
                    viewModel.updateHeadingStartDate(headingId, startDate)
                },
                onUpdateHeadingEndDate = { headingId, endDate ->
                    viewModel.updateHeadingEndDate(headingId, endDate)
                },
                onDeleteHeading = { headingId ->
                    viewModel.deleteHeading(headingId)
                },
                onUpdateTaskStatus = { taskId, status ->
                    viewModel.updateTaskStatus(taskId, status)
                },
                onToggleTaskPin = { taskId ->
                    viewModel.toggleTaskPinned(taskId)
                },
                onToggleTaskMilestone = { taskId ->
                    viewModel.toggleTaskMilestone(taskId)
                },
                onOpenTaskEditor = { taskId ->
                    viewModel.expandTask(taskId)
                },
                onSelectSingleTask = { taskId ->
                    viewModel.selectSingleTask(taskId)
                },
                onToggleTaskSelection = { taskId ->
                    viewModel.toggleTaskSelection(taskId)
                },
                onRangeSelectTask = { taskId ->
                    viewModel.selectTaskRange(allVisibleTaskIds, taskId)
                },
                modifier = modifier.imePadding()
            )
            return
        } else if (projectWorkspaceMode == ProjectWorkspaceMode.ROADMAP) {
            SideEffect {
                projectDragController.isProjectListActive = false
                projectDragController.isGeneralListReorderActive = false
            }
            ProjectRoadmapWorkspace(
                project = currentProject,
                projectHeadings = projectHeadings,
                primaryTasks = primaryTasks,
                headingTaskCounts = headingTaskCounts,
                currentEpochDay = currentEpochDay,
                locale = activeLocale,
                onShiftHeadingDates = { heading, deltaDays, fallbackRange ->
                    if (!isEffectiveTimelineFullScreen) {
                        viewModel.shiftHeadingRoadmapDates(heading, deltaDays, fallbackRange)
                    }
                },
                onShiftTaskDates = { task, deltaDays ->
                    if (!isEffectiveTimelineFullScreen) {
                        viewModel.shiftTaskRoadmapDates(task, deltaDays)
                    }
                },
                onUpdateHeadingStartDate = { headingId, startDate ->
                    if (!isEffectiveTimelineFullScreen) {
                        viewModel.updateHeadingStartDate(headingId, startDate)
                    }
                },
                onUpdateTaskDeadline = { task, deadline ->
                    if (!isEffectiveTimelineFullScreen) {
                        viewModel.updateTaskDeadline(task, deadline)
                    }
                },
                onScheduleTaskToday = { task ->
                    if (!isEffectiveTimelineFullScreen) {
                        val todayStartMillis = LocalDate.now()
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        viewModel.updateTaskSchedule(task, ScheduleState.TODAY, todayStartMillis)
                    }
                },
                onUpdateTaskStatus = { taskId, status ->
                    if (!isEffectiveTimelineFullScreen) {
                        viewModel.updateTaskStatus(taskId, status)
                    }
                },
                onOpenTaskEditor = { taskId ->
                    if (!isEffectiveTimelineFullScreen) {
                        viewModel.expandTask(taskId)
                    }
                },
                onShowError = { msg ->
                    viewModel.showUiMessage(msg)
                },
                selectedTaskIds = selectedTaskIds,
                isSelectionModeActive = isSelectionModeActive,
                onSelectSingleTask = { taskId ->
                    if (!isEffectiveTimelineFullScreen) {
                        viewModel.selectSingleTask(taskId)
                    }
                },
                onToggleTaskSelection = { taskId ->
                    if (!isEffectiveTimelineFullScreen) {
                        viewModel.toggleTaskSelection(taskId)
                    }
                },
                isFullScreen = isEffectiveTimelineFullScreen,
                onToggleFullScreen = { viewModel.toggleTimelineFullScreen() },
                projectTitle = currentProject.title,
                modifier = modifier.imePadding()
            )
            return
        }
    }

    val navScopeKey = navTarget.scrollScopeKey()
    val listState = remember(navScopeKey) {
        LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
    }
    val handleEnsureVisibleRequest: (Boolean) -> Unit = remember { { _ -> } }

    LaunchedEffect(navScopeKey) {
        if (navTarget.shouldResetScrollToTop(expandedTaskId)) {
            runCatching { listState.scrollToItem(0, 0) }
        }
    }

    val isGeneralManualFlatList = !isProjectView &&
        viewSortMode == "manual" &&
        navTarget != ActiveNavigationTarget.SmartView(SmartViewType.UPCOMING) &&
        navTarget != ActiveNavigationTarget.SmartView(SmartViewType.LOGBOOK) &&
        navTarget != ActiveNavigationTarget.SmartView(SmartViewType.TRASH) &&
        !(navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY) && groupTodayByProjectOrArea)

    // Register list geometry semantics for position-based Drag & Drop (Magic Plus + task handles)
    SideEffect {
        projectDragController.isProjectListActive = isProjectView
        projectDragController.isGeneralListReorderActive = isGeneralManualFlatList
        projectDragController.generalListTasks = if (isGeneralManualFlatList) primaryTasks else emptyList()
        projectDragController.listState = listState
        projectDragController.headings = if (isProjectView) projectHeadings else emptyList()
        projectDragController.sectionTasks = projectSections
        projectDragController.entries = if (!isProjectView) {
            emptyMap()
        } else {
            val map = HashMap<Any, ProjectListEntry>()
            projectSections[null].orEmpty().forEachIndexed { i, t ->
                map["ungrouped_${t.id}"] = ProjectListEntry.TaskRow(t.id, null, i)
            }
            projectHeadings.forEachIndexed { hIdx, h ->
                map["heading_${h.id}"] = ProjectListEntry.HeadingRow(h.id, hIdx)
                if (h.id !in collapsedHeadingIds) {
                    projectSections[h.id].orEmpty().forEachIndexed { i, t ->
                        map[t.id] = ProjectListEntry.TaskRow(t.id, h.id, i)
                    }
                }
            }
            map
        }
    }
    DisposableEffect(projectDragController) {
        onDispose {
            projectDragController.isProjectListActive = false
            projectDragController.isGeneralListReorderActive = false
            projectDragController.generalListTasks = emptyList()
        }
    }
    LaunchedEffect(isProjectView) {
        if (!isProjectView && projectDragController.session?.kind != ProjectDragKind.MOVE_TASK) {
            projectDragController.cancel()
        }
    }

    ProjectDragAutoScrollEffect(controller = projectDragController, listState = listState)

    val activeSession = projectDragController.session
    val draggedIdsSet = remember(activeSession?.draggedTaskIds, activeSession?.draggedTaskId) {
        activeSession?.effectiveTaskIds?.toHashSet().orEmpty()
    }
    val supportsCrossContainerTaskDrag = navTarget != ActiveNavigationTarget.SmartView(SmartViewType.LOGBOOK) &&
        navTarget != ActiveNavigationTarget.SmartView(SmartViewType.TRASH)
    val taskDragHandleFor: (TaskItem) -> (@Composable () -> Unit)? = { task ->
        if (supportsCrossContainerTaskDrag && task.status == TaskStatus.OPEN) {
            {
                ProjectTaskDragHandle(
                    task = task,
                    controller = projectDragController,
                    selectedTaskIds = selectedTaskIds,
                    orderedVisibleTaskIds = allVisibleTaskIds
                )
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier.then(
            if (isProjectView || isGeneralManualFlatList) {
                Modifier.projectDropSurface(projectDragController)
            } else {
                Modifier
            }
        )
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(
            bottom = when {
                isImeVisible -> 28.dp
                isSelectionModeActive -> 196.dp
                else -> 110.dp
            }
        )
    ) {
        item(key = navTarget.topScrollAnchorKey()) {
            Spacer(modifier = Modifier.height(0.dp))
        }

        // If viewing Today and Google/Device Calendar Events are enabled, render today's events at the top only when Today has tasks
        val hasTodayTasks = primaryTasks.isNotEmpty() || filteredEveningTasks.isNotEmpty()
        if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY) &&
            calendarEventsEnabled &&
            hasTodayTasks &&
            todayCalendarEvents.isNotEmpty()
        ) {
            item(key = "today_calendar_events_block") {
                ThingsCalendarEventsBlock(
                    events = todayCalendarEvents,
                    locale = activeLocale,
                    compactTopPadding = false
                )
            }
        }

        // If viewing Logbook, show the Gemini Logbook Reflection Card at the top
        if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.LOGBOOK)) {
            item(key = "gemini_logbook_card") {
                GeminiLogbookReflectionCard(
                    review = logbookAiReview,
                    isGenerating = isGeminiBusy,
                    errorMessage = geminiErrorMessage,
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

        // If viewing Trash (Papierkorb), render soft-deleted projects and tasks with Restore / Delete Forever actions
        if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TRASH)) {
            if (deletedProjects.isEmpty() && primaryTasks.isEmpty()) {
                item(key = "trash_empty_placeholder") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Surface(
                            modifier = Modifier
                                .widthIn(max = 520.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.cardSurface,
                            border = BorderStroke(0.75.dp, colors.hairlineDivider)
                        ) {
                            Text(
                                text = stringResource(R.string.trash_empty_state),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryText,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp)
                            )
                        }
                    }
                }
            } else {
                items(
                    items = deletedProjects,
                    key = { proj -> "trash_proj_${proj.id}" }
                ) { proj ->
                    ThingsTrashItemCard(
                        title = proj.title,
                        subtitle = stringResource(R.string.entity_project),
                        isProject = true,
                        onRestore = { viewModel.restoreDeletedProject(proj.id) },
                        onDeleteForever = { viewModel.permanentlyDeleteProject(proj.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(
                    items = primaryTasks,
                    key = { task -> "trash_task_${task.id}" }
                ) { task ->
                    ThingsTrashItemCard(
                        title = task.title,
                        subtitle = task.notes.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() },
                        isProject = false,
                        onRestore = { viewModel.restoreDeletedTask(task.id) },
                        onDeleteForever = { viewModel.permanentlyDeleteTask(task.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        } else if (isProjectView) {
            val ungroupedTasks = projectSections[null].orEmpty()
            val showUngroupedDropHeader = projectHeadings.isNotEmpty() &&
                (ungroupedTasks.isNotEmpty() || activeSession?.kind == ProjectDragKind.MOVE_TASK)
            if (showUngroupedDropHeader && currentProject != null) {
                item(key = "proj_ungrouped_drop_header_${currentProject.id}") {
                    val generalTitle = stringResource(R.string.v30_board_ungrouped_column)
                    val isGeneralHighlighted = activeSession?.kind == ProjectDragKind.MOVE_TASK &&
                        (activeSession.hoveredZoneKey == "proj_section_general" ||
                            activeSession.hoveredZoneKey == "proj_ungrouped_drop_${currentProject.id}")
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                            .workspaceDropTarget(
                                controller = projectDragController,
                                key = "proj_ungrouped_drop_${currentProject.id}",
                                destination = WorkspaceDragDropDestination.BoardColumn(
                                    projectId = currentProject.id,
                                    headingId = null,
                                    headingTitle = generalTitle,
                                    indexInColumn = ungroupedTasks.size
                                ),
                                cornerRadius = 12.dp,
                                priority = 15
                            ),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isGeneralHighlighted) {
                            colors.thingsBlueSoft.copy(alpha = 0.65f)
                        } else {
                            colors.tagPillBg.copy(alpha = 0.42f)
                        },
                        border = BorderStroke(
                            width = if (isGeneralHighlighted) 1.5.dp else 0.75.dp,
                            color = if (isGeneralHighlighted) colors.thingsBlue else colors.hairlineDivider
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.5.dp)
                                        .height(15.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(colors.thingsBlue.copy(alpha = 0.75f))
                                )
                                Text(
                                    text = generalTitle,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isGeneralHighlighted) colors.thingsBlue else colors.secondaryText
                                )
                            }
                            Text(
                                text = ungroupedTasks.size.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isGeneralHighlighted) colors.thingsBlue else colors.secondaryText
                            )
                        }
                    }
                }
            }

            items(
                items = ungroupedTasks,
                key = { task -> "ungrouped_${task.id}" }
            ) { task ->
                ThingsTaskRowItem(
                    task = task,
                    viewModel = viewModel,
                    allTags = allTags,
                    areas = areas,
                    activeProjects = activeProjects,
                    allHeadings = allHeadings,
                    expandedTaskId = null,
                    isQuickFindOpen = isQuickFindOpen,
                    selectedTaskIds = selectedTaskIds,
                    allVisibleTaskIds = allVisibleTaskIds,
                    magicBreakdownTaskId = magicBreakdownTaskId,
                    activeLocale = activeLocale,
                    externalDialogTriggerToken = externalDialogTriggerToken,
                    externalDialogTriggerType = externalDialogTriggerType,
                    onRequestEnsureVisible = handleEnsureVisibleRequest,
                    dragHandle = taskDragHandleFor(task),
                    modifier = Modifier.alpha(if (task.id in draggedIdsSet) 0.35f else 1f)
                )
            }

            projectHeadings.forEach { heading ->
                val tasksInHeading = projectSections[heading.id].orEmpty()
                val counts = headingTaskCounts[heading.id]
                val completedInPhase = counts?.first ?: 0
                val totalInPhase = counts?.second ?: tasksInHeading.size
                val isSectionHighlighted = activeSession?.kind == ProjectDragKind.MOVE_TASK &&
                    (activeSession.hoveredZoneKey == "proj_section_${heading.id}" ||
                        activeSession.hoveredZoneKey == "proj_heading_drop_${heading.id}")
                item(key = "heading_${heading.id}") {
                    ProjectHeadingSectionHeader(
                        heading = heading,
                        isCollapsed = heading.id in collapsedHeadingIds,
                        completedTaskCount = completedInPhase,
                        totalTaskCount = totalInPhase,
                        locale = activeLocale,
                        isDropHighlighted = isSectionHighlighted,
                        dragController = projectDragController,
                        projectIdForDrop = currentProject?.id,
                        dropIndexInSection = tasksInHeading.size,
                        onToggleCollapse = { viewModel.toggleHeadingCollapsed(heading.id) },
                        onRenameHeading = { newTitle -> viewModel.renameHeading(heading.id, newTitle) },
                        onUpdateStartDate = { newStartDate -> viewModel.updateHeadingStartDate(heading.id, newStartDate) },
                        onUpdateEndDate = { newEndDate -> viewModel.updateHeadingEndDate(heading.id, newEndDate) },
                        onDeleteHeading = { viewModel.deleteHeading(heading.id) },
                        onAddTaskUnderHeading = {
                            viewModel.createTaskInProjectSection(heading.id, tasksInHeading, tasksInHeading.size)
                        }
                    )
                }

                if (heading.id !in collapsedHeadingIds) {
                    items(
                        items = tasksInHeading,
                        key = { task -> task.id }
                    ) { task ->
                        ThingsTaskRowItem(
                            task = task,
                            viewModel = viewModel,
                            allTags = allTags,
                            areas = areas,
                            activeProjects = activeProjects,
                            allHeadings = allHeadings,
                            expandedTaskId = null,
                            isQuickFindOpen = isQuickFindOpen,
                            selectedTaskIds = selectedTaskIds,
                            allVisibleTaskIds = allVisibleTaskIds,
                            magicBreakdownTaskId = magicBreakdownTaskId,
                            activeLocale = activeLocale,
                            externalDialogTriggerToken = externalDialogTriggerToken,
                            externalDialogTriggerType = externalDialogTriggerType,
                            onRequestEnsureVisible = handleEnsureVisibleRequest,
                            dragHandle = taskDragHandleFor(task),
                            modifier = Modifier.alpha(if (task.id in draggedIdsSet) 0.35f else 1f)
                        )
                    }
                }
            }
        } else if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.UPCOMING)) {
            // Upcoming Smart View: Group tasks & Phase Milestones chronologically by day
            val zone = ZoneId.systemDefault()
            val todayDate = LocalDate.ofEpochDay(currentEpochDay)
            val groupedUpcoming = primaryTasks
                .groupBy { task -> computeUpcomingTargetDate(task, todayDate, zone) }
            val activeProjectById = activeProjects.associateBy { it.id }
            val upcomingMilestonesByDate = allHeadings
                .mapNotNull { heading ->
                    if (heading.isDeleted || heading.isArchived || heading.title.isBlank()) return@mapNotNull null
                    val endMillis = heading.endDate ?: return@mapNotNull null
                    val project = activeProjectById[heading.projectId] ?: return@mapNotNull null
                    val filterTag = selectedFilterTagId
                    if (filterTag != null && filterTag !in project.tagIds) return@mapNotNull null
                    val endLocalDate = runCatching {
                        Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
                    }.getOrNull() ?: return@mapNotNull null
                    if (endLocalDate.isBefore(todayDate)) return@mapNotNull null
                    Triple(endLocalDate, heading, project)
                }
                .groupBy({ it.first }, { it.second to it.third })

            val allUpcomingDates = (groupedUpcoming.keys + upcomingMilestonesByDate.keys).toSortedSet()

            var cumulativeTaskOffset = 0
            allUpcomingDates.forEachIndexed { groupIdx, targetDate ->
                val dayTasks = sortTasksForView(groupedUpcoming[targetDate].orEmpty(), viewSortMode, openTaskIds)
                val dayMilestones = upcomingMilestonesByDate[targetDate].orEmpty()
                val dayEvents = if (calendarEventsEnabled && (dayTasks.isNotEmpty() || dayMilestones.isNotEmpty())) {
                    DeviceCalendarRepository.filterActiveOrUpcomingEvents(
                        events = upcomingCalendarEventsByDate[targetDate].orEmpty(),
                        forDate = targetDate,
                        todayDate = todayDate,
                        nowMillis = System.currentTimeMillis(),
                        upcomingOnly = true
                    )
                } else {
                    emptyList()
                }
                val groupBaseIndex = cumulativeTaskOffset
                cumulativeTaskOffset += dayTasks.size
                item(key = "upcoming_day_header_${targetDate.toEpochDay()}") {
                    UpcomingDayGroupHeader(
                        date = targetDate,
                        todayDate = todayDate,
                        locale = activeLocale,
                        isFirstGroup = groupIdx == 0
                    )
                }
                if (dayEvents.isNotEmpty()) {
                    item(key = "upcoming_cal_events_${targetDate.toEpochDay()}") {
                        ThingsCalendarEventsBlock(
                            events = dayEvents,
                            locale = activeLocale,
                            compactTopPadding = true
                        )
                    }
                }
                items(
                    items = dayMilestones,
                    key = { (heading, _) -> "upcoming_phase_milestone_${targetDate.toEpochDay()}_${heading.id}" }
                ) { (heading, project) ->
                    val counts = headingTaskCounts[heading.id]
                    val completedInPhase = counts?.first ?: 0
                    val totalInPhase = counts?.second ?: 0
                    PhaseMilestoneCard(
                        heading = heading,
                        project = project,
                        completedCount = completedInPhase,
                        totalCount = totalInPhase,
                        onOpenProject = { viewModel.selectProject(project.id) }
                    )
                }
                itemsIndexed(
                    items = dayTasks,
                    key = { _, task -> "upcoming_${targetDate.toEpochDay()}_${task.id}" }
                ) { index, task ->
                    val globalIndex = groupBaseIndex + index
                    MagicPlusInsertionGap(
                        visible = magicDragState.isDragging && magicDragState.dropIndex == globalIndex,
                        dropMode = magicDragState.dropMode
                    )
                    ThingsTaskRowItem(
                        task = task,
                        viewModel = viewModel,
                        allTags = allTags,
                        areas = areas,
                        activeProjects = activeProjects,
                        allHeadings = allHeadings,
                        expandedTaskId = null,
                        isQuickFindOpen = isQuickFindOpen,
                        selectedTaskIds = selectedTaskIds,
                        allVisibleTaskIds = allVisibleTaskIds,
                        magicBreakdownTaskId = magicBreakdownTaskId,
                        activeLocale = activeLocale,
                        externalDialogTriggerToken = externalDialogTriggerToken,
                        externalDialogTriggerType = externalDialogTriggerType,
                        onRequestEnsureVisible = handleEnsureVisibleRequest,
                        dragHandle = taskDragHandleFor(task),
                        modifier = Modifier.alpha(if (task.id in draggedIdsSet) 0.35f else 1f)
                    )
                }
            }

            // Trailing Magic Plus drop slot at the end of the list
            item(key = navTarget.trailingMagicPlusGapKey()) {
                MagicPlusInsertionGap(
                    visible = magicDragState.isDragging && magicDragState.dropIndex >= primaryTasks.size,
                    dropMode = magicDragState.dropMode
                )
            }
        } else if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY) && groupTodayByProjectOrArea) {
            // Optional Things 3 "Group Today by Project or Area" mode
            val projectMap = activeProjects.associateBy { it.id }
            val areaMap = areas.associateBy { it.id }
            val unassignedTodayTasks = primaryTasks.filter {
                (it.projectId == null || it.projectId !in projectMap) &&
                    (it.areaId == null || it.areaId !in areaMap)
            }
            itemsIndexed(
                items = unassignedTodayTasks,
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
                    expandedTaskId = null,
                    isQuickFindOpen = isQuickFindOpen,
                    selectedTaskIds = selectedTaskIds,
                    allVisibleTaskIds = allVisibleTaskIds,
                    magicBreakdownTaskId = magicBreakdownTaskId,
                    activeLocale = activeLocale,
                    externalDialogTriggerToken = externalDialogTriggerToken,
                    externalDialogTriggerType = externalDialogTriggerType,
                    onRequestEnsureVisible = handleEnsureVisibleRequest,
                    dragHandle = taskDragHandleFor(task),
                    modifier = Modifier.alpha(if (task.id in draggedIdsSet) 0.35f else 1f)
                )
            }

            activeProjects.forEach { proj ->
                val projTodayTasks = primaryTasks.filter { it.projectId == proj.id }
                if (projTodayTasks.isNotEmpty()) {
                    item(key = "today_group_proj_${proj.id}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.selectProject(proj.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProjectProgressRing(progress = proj.progress, size = 16.dp)
                            Text(
                                text = proj.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = colors.thingsBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    items(
                        items = projTodayTasks,
                        key = { task -> task.id }
                    ) { task ->
                        ThingsTaskRowItem(
                            task = task,
                            viewModel = viewModel,
                            allTags = allTags,
                            areas = areas,
                            activeProjects = activeProjects,
                            allHeadings = allHeadings,
                            expandedTaskId = null,
                            isQuickFindOpen = isQuickFindOpen,
                            selectedTaskIds = selectedTaskIds,
                            allVisibleTaskIds = allVisibleTaskIds,
                            magicBreakdownTaskId = magicBreakdownTaskId,
                            activeLocale = activeLocale,
                            externalDialogTriggerToken = externalDialogTriggerToken,
                            externalDialogTriggerType = externalDialogTriggerType,
                            onRequestEnsureVisible = handleEnsureVisibleRequest,
                            dragHandle = taskDragHandleFor(task),
                            modifier = Modifier.alpha(if (task.id in draggedIdsSet) 0.35f else 1f)
                        )
                    }
                }
            }

            areas.forEach { area ->
                val areaTodayTasks = primaryTasks.filter {
                    (it.projectId == null || it.projectId !in projectMap) && it.areaId == area.id
                }
                if (areaTodayTasks.isNotEmpty()) {
                    item(key = "today_group_area_${area.id}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.selectArea(area.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = area.title,
                                tint = colors.anytimeTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = area.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = colors.anytimeTeal,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    items(
                        items = areaTodayTasks,
                        key = { task -> task.id }
                    ) { task ->
                        ThingsTaskRowItem(
                            task = task,
                            viewModel = viewModel,
                            allTags = allTags,
                            areas = areas,
                            activeProjects = activeProjects,
                            allHeadings = allHeadings,
                            expandedTaskId = null,
                            isQuickFindOpen = isQuickFindOpen,
                            selectedTaskIds = selectedTaskIds,
                            allVisibleTaskIds = allVisibleTaskIds,
                            magicBreakdownTaskId = magicBreakdownTaskId,
                            activeLocale = activeLocale,
                            externalDialogTriggerToken = externalDialogTriggerToken,
                            externalDialogTriggerType = externalDialogTriggerType,
                            onRequestEnsureVisible = handleEnsureVisibleRequest,
                            dragHandle = taskDragHandleFor(task),
                            modifier = Modifier.alpha(if (task.id in draggedIdsSet) 0.35f else 1f)
                        )
                    }
                }
            }

            item(key = navTarget.trailingMagicPlusGapKey()) {
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
                    expandedTaskId = null,
                    isQuickFindOpen = isQuickFindOpen,
                    selectedTaskIds = selectedTaskIds,
                    allVisibleTaskIds = allVisibleTaskIds,
                    magicBreakdownTaskId = magicBreakdownTaskId,
                    activeLocale = activeLocale,
                    externalDialogTriggerToken = externalDialogTriggerToken,
                    externalDialogTriggerType = externalDialogTriggerType,
                    onRequestEnsureVisible = handleEnsureVisibleRequest,
                    dragHandle = taskDragHandleFor(task),
                    modifier = Modifier.alpha(if (task.id in draggedIdsSet) 0.35f else 1f)
                )
            }

            // Trailing Magic Plus drop slot at the end of the list
            item(key = navTarget.trailingMagicPlusGapKey()) {
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
                    expandedTaskId = null,
                    isQuickFindOpen = isQuickFindOpen,
                    selectedTaskIds = selectedTaskIds,
                    allVisibleTaskIds = allVisibleTaskIds,
                    magicBreakdownTaskId = magicBreakdownTaskId,
                    activeLocale = activeLocale,
                    externalDialogTriggerToken = externalDialogTriggerToken,
                    externalDialogTriggerType = externalDialogTriggerType,
                    onRequestEnsureVisible = handleEnsureVisibleRequest,
                    dragHandle = taskDragHandleFor(eveningTask),
                    modifier = Modifier.alpha(if (eveningTask.id in draggedIdsSet) 0.35f else 1f)
                )
            }
        }
    }
    if (isProjectView || isGeneralManualFlatList) {
        ProjectDropIndicatorOverlay(controller = projectDragController)
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
    inboxTasks: List<TaskItem> = emptyList(),
    todayTasks: List<TaskItem> = emptyList(),
    eveningTasks: List<TaskItem> = emptyList(),
    upcomingTasks: List<TaskItem> = emptyList(),
    anytimeTasks: List<TaskItem> = emptyList(),
    somedayTasks: List<TaskItem> = emptyList(),
    logbookTasks: List<TaskItem> = emptyList(),
    activeLocale: Locale,
    showBatchMoveDialog: Boolean,
    onDismissBatchMoveDialog: () -> Unit,
    showBatchTagPickerDialog: Boolean = false,
    onDismissBatchTagPickerDialog: () -> Unit = {},
    showPerspectiveEditorDialog: Boolean = false,
    editingPerspective: SavedPerspective? = null,
    savedPerspectivesCount: Int = 0,
    onDismissPerspectiveEditorDialog: () -> Unit = {},
    showCreateProjectDialog: Boolean,
    editingProject: Project?,
    projectDialogPreselectedAreaId: String?,
    onDismissCreateProjectDialog: () -> Unit,
    showEmptyTrashConfirmDialog: Boolean = false,
    onDismissEmptyTrashConfirmDialog: () -> Unit = {}
) {
    val allHeadings by viewModel.allHeadings.collectAsState()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsState()
    val tagSortMode by viewModel.tagSortMode.collectAsState()
    val tagUsageCounts by viewModel.tagUsageCounts.collectAsState()
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
    val quickShareDraft by viewModel.quickShareDraft.collectAsState()

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

    // v2 Modals State
    val isWeeklyReviewModalOpen by viewModel.isWeeklyReviewModalOpen.collectAsState()
    val weeklyReviewInitialStep by viewModel.weeklyReviewInitialStep.collectAsState()
    val lastWeeklyReviewAt by viewModel.lastWeeklyReviewAt.collectAsState()
    val isTemplatesModalOpen by viewModel.isTemplatesModalOpen.collectAsState()
    val templatesModalInitialTab by viewModel.templatesModalInitialTab.collectAsState()
    val templatesModalDefaultAreaId by viewModel.templatesModalDefaultAreaId.collectAsState()
    val userProjectTemplates by viewModel.userProjectTemplates.collectAsState()

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

    // Batch Categories Picker Dialog for Multi-Selection (v2.5 / v3.0)
    if (showBatchTagPickerDialog) {
        val selectedTasksList = remember(allVisibleViewTasks, selectedTaskIds) {
            allVisibleViewTasks.filter { it.id in selectedTaskIds }
        }
        val commonTagIds = remember(selectedTasksList) {
            if (selectedTasksList.isEmpty()) {
                emptySet()
            } else {
                selectedTasksList
                    .map { t -> t.tags.mapTo(HashSet()) { it.id } as Set<String> }
                    .reduce { acc, next -> acc.intersect(next) }
            }
        }
        TaskTagPickerDialog(
            allTags = allTags,
            selectedTagIds = commonTagIds.toList(),
            tagSortMode = tagSortMode,
            tagUsageCounts = tagUsageCounts,
            onUpdateSelectedTags = { updatedList ->
                val nextSet = updatedList.toSet()
                viewModel.batchUpdateSelectedTasksTags(
                    tagIdsToAdd = nextSet - commonTagIds,
                    tagIdsToRemove = commonTagIds - nextSet
                )
            },
            onCreateAndAssignTag = { newTagTitle ->
                viewModel.batchCreateAndAssignTagToSelectedTasks(newTagTitle)
            },
            onOpenFullTagManager = {
                onDismissBatchTagPickerDialog()
                viewModel.setTagManagerOpen(true)
            },
            onDismiss = onDismissBatchTagPickerDialog
        )
    }

    // v3.0 Custom Saved Perspective Editor Dialog
    if (showPerspectiveEditorDialog) {
        PerspectiveEditorDialog(
            initialPerspective = editingPerspective,
            areas = areas,
            allTags = allTags,
            onSave = { saved ->
                viewModel.savePerspective(
                    if (editingPerspective == null && saved.sortOrder <= 0.0) {
                        saved.copy(sortOrder = (savedPerspectivesCount + 1).toDouble())
                    } else {
                        saved
                    }
                )
                onDismissPerspectiveEditorDialog()
            },
            onDelete = if (editingPerspective != null) {
                { pId ->
                    viewModel.deletePerspective(pId)
                    onDismissPerspectiveEditorDialog()
                }
            } else {
                null
            },
            onDismiss = onDismissPerspectiveEditorDialog
        )
    }

    // Comprehensive Settings Modal (⚙️ top-right: Local DB vs Cloud Sync, Account, Language, Calendar Events, App Updates, Statistics, Backup & Restore, CSV)
    if (isSettingsModalOpen) {
        val isBackupBusy by viewModel.isBackupBusy.collectAsState()
        val backupStatusMessage by viewModel.backupStatusMessage.collectAsState()
        val calendarEventsEnabled by viewModel.calendarEventsEnabled.collectAsState()
        val hasCalendarPermission by viewModel.hasCalendarPermission.collectAsState()
        val availableCalendars by viewModel.availableCalendars.collectAsState()
        val selectedCalendarIds by viewModel.selectedCalendarIds.collectAsState()
        val autoCheckUpdatesEnabled by viewModel.autoCheckUpdatesEnabled.collectAsState()
        val includeBetaUpdates by viewModel.includeBetaUpdates.collectAsState()
        val appUpdateState by viewModel.appUpdateState.collectAsState()
        val groupTodayByProjectOrArea by viewModel.groupTodayByProjectOrArea.collectAsState()
        val showPerspectivesInSidebar by viewModel.showPerspectivesInSidebar.collectAsState()
        val biometricAppLockEnabled by viewModel.biometricAppLockEnabled.collectAsState()
        ThingsSettingsModal(
            isLocalOnlyDbMode = isLocalOnlyDbMode,
            syncStatus = syncStatus,
            currentUserIdentifier = currentUserIdentifier,
            statistics = statistics,
            csvStatusMessage = csvStatusMessage,
            isBackupBusy = isBackupBusy,
            backupStatusMessage = backupStatusMessage,
            hasSampleTasks = hasSampleTasks,
            calendarEventsEnabled = calendarEventsEnabled,
            hasCalendarPermission = hasCalendarPermission,
            availableCalendars = availableCalendars,
            selectedCalendarIds = selectedCalendarIds,
            onSetCalendarEventsEnabled = { viewModel.setCalendarEventsEnabled(it) },
            onCalendarPermissionResult = { viewModel.onCalendarPermissionResult(it) },
            onToggleCalendarSelection = { viewModel.toggleCalendarSelection(it) },
            onSetAllCalendarsSelected = { viewModel.setAllCalendarsSelected(it) },
            onRefreshCalendars = { viewModel.refreshCalendarsAndEvents(selectDefaultIfFirstTime = false) },
            autoCheckUpdatesEnabled = autoCheckUpdatesEnabled,
            includeBetaUpdates = includeBetaUpdates,
            appUpdateState = appUpdateState,
            onSetAutoCheckUpdatesEnabled = { viewModel.setAutoCheckUpdatesEnabled(it) },
            onSetIncludeBetaUpdates = { viewModel.setIncludeBetaUpdates(it) },
            onSearchForUpdates = { viewModel.checkForAppUpdates(manual = true) },
            onDownloadAndInstallUpdate = { release -> viewModel.startUpdateDownloadAndInstall(release) },
            onInstallDownloadedUpdate = { viewModel.installDownloadedUpdate() },
            onCancelUpdateDownload = { viewModel.cancelUpdateDownload() },
            onOpenUpdateReleasePage = { url -> viewModel.openUpdateReleasePage(url) },
            onDeleteSampleTasksNow = { viewModel.deleteSampleTasksForLocalSetup() },
            groupTodayByProjectOrArea = groupTodayByProjectOrArea,
            onSetGroupTodayByProjectOrArea = { viewModel.setGroupTodayByProjectOrArea(it) },
            showPerspectivesInSidebar = showPerspectivesInSidebar,
            onSetShowPerspectivesInSidebar = viewModel::setShowPerspectivesInSidebar,
            biometricAppLockEnabled = biometricAppLockEnabled,
            onSetBiometricAppLockEnabled = { viewModel.setBiometricAppLockEnabled(it) },
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

    // Initial Setup Account Merge Prompt (shown BEFORE syncing whenever local user tasks exist!)
    pendingMergeAuthRequest?.let { pendingReq ->
        SampleTasksAccountMergeDialog(
            accountLabel = pendingReq.displayAccountLabel,
            nonSampleTaskCount = pendingMergeNonSampleCount,
            hasSampleTasks = hasSampleTasks,
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
            onSignInWithGoogle = { activity -> viewModel.signInWithGoogleAccount(activity) },
            onSignOut = { viewModel.signOutCloud() },
            onTriggerDeltaSyncNow = { viewModel.triggerManualCloudSync() },
            onDismiss = { viewModel.setAuthModalOpen(false) }
        )
    }

    // Full Tag Management Modal (Categories Power Suite)
    if (isTagManagerOpen) {
        ThingsTagManagerModal(
            allTags = allTags,
            tagSortMode = tagSortMode,
            tagUsageCounts = tagUsageCounts,
            onSetTagSortMode = { mode -> viewModel.setTagSortMode(mode) },
            onMoveTag = { tagId, moveUp -> viewModel.moveTag(tagId, moveUp) },
            onMergeTags = { sourceId, targetId -> viewModel.mergeTags(sourceId, targetId) },
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

    val modalNavTarget by viewModel.navigationTarget.collectAsState()
    val contextualNavAreaId = when (val t = modalNavTarget) {
        is ActiveNavigationTarget.AreaDetail -> t.areaId
        is ActiveNavigationTarget.ProjectDetail -> activeProjects.firstOrNull { it.id == t.projectId }?.areaId
        else -> null
    }

    // Create or Edit Project Dialog (with Target Date / Deadline, Area Assignment & v2 Templates/Import trigger)
    if (showCreateProjectDialog) {
        CreateOrEditProjectDialog(
            existingProject = editingProject,
            areas = areas,
            preselectedAreaId = projectDialogPreselectedAreaId ?: contextualNavAreaId,
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
            onOpenTemplatesAndImport = { preAreaId ->
                viewModel.openTemplatesModal(
                    initialTab = 0,
                    defaultAreaId = preAreaId ?: projectDialogPreselectedAreaId ?: contextualNavAreaId
                )
            },
            onDismiss = onDismissCreateProjectDialog
        )
    }

    // v2 Pillar 1: Guided 4-Step GTD Weekly Review + Stalled Project Detection Modal
    if (isWeeklyReviewModalOpen) {
        val combinedTodayTasks = remember(todayTasks, eveningTasks) {
            (todayTasks + eveningTasks).distinctBy { it.id }
        }
        ThingsWeeklyReviewModal(
            initialStep = weeklyReviewInitialStep,
            inboxTasks = inboxTasks,
            todayTasks = combinedTodayTasks,
            upcomingTasks = upcomingTasks,
            anytimeTasks = anytimeTasks,
            somedayTasks = somedayTasks,
            logbookTasks = logbookTasks,
            activeProjects = activeProjects,
            lastWeeklyReviewAt = lastWeeklyReviewAt,
            locale = activeLocale,
            onScheduleTask = { task, schedule, whenDate ->
                viewModel.updateTaskSchedule(task, schedule, whenDate)
            },
            onScheduleTaskWithOptionalDeadline = { task, schedule, whenDate, newDeadline ->
                viewModel.updateTaskScheduleAndOptionalDeadline(task, schedule, whenDate, newDeadline)
            },
            onOpenTask = { task ->
                viewModel.openTaskFromWeeklyReview(task)
            },
            onCompleteTask = { taskId ->
                viewModel.updateTaskStatus(taskId, TaskStatus.COMPLETED)
            },
            onDeleteTask = { taskId ->
                viewModel.deleteTask(taskId)
            },
            onAddNextActionToProject = { projectId, title ->
                viewModel.addNextActionToProject(projectId, title)
            },
            onMarkProjectReviewed = { projectId ->
                viewModel.markProjectReviewed(projectId)
            },
            onMoveProjectToSomeday = { projectId ->
                viewModel.moveProjectToSomeday(projectId)
            },
            onActivateSomedayProject = { projectId ->
                viewModel.activateSomedayProjectToAnytime(projectId)
            },
            onOpenProject = { projectId ->
                viewModel.selectProject(projectId)
            },
            onCompleteWeeklyReview = { toastMsg ->
                viewModel.completeWeeklyReview(toastMsg)
            },
            onDismiss = { viewModel.closeWeeklyReviewModal() }
        )
    }

    // v2 Pillar 2 + v3 Milestone 4: Reusable Project Templates, Markdown Outline & Asana / Planner Import-Export Modal
    if (isTemplatesModalOpen) {
        val activeModalProjectId = (modalNavTarget as? ActiveNavigationTarget.ProjectDetail)?.projectId
        val fallbackProjectTitle = stringResource(R.string.entity_new_project)
        ThingsTemplatesAndImportModal(
            initialTab = templatesModalInitialTab,
            defaultAreaId = templatesModalDefaultAreaId ?: contextualNavAreaId,
            areas = areas,
            userTemplates = userProjectTemplates,
            projects = activeProjects,
            currentProjectId = activeModalProjectId,
            locale = activeLocale,
            onCreateFromTemplate = { template, areaOverride ->
                viewModel.createProjectFromTemplate(template, areaOverride)
            },
            onDeleteUserTemplate = { templateId ->
                viewModel.deleteProjectTemplate(templateId)
            },
            onImportOutline = { rawOutline, areaId ->
                viewModel.importProjectFromOutline(rawOutline, areaId, fallbackProjectTitle)
            },
            onImportExternalUri = { uri, areaId ->
                viewModel.importExternalProjectFromUri(uri, areaId)
            },
            onExportExternalProject = { projectId, format, onPrepared ->
                viewModel.prepareExternalProjectExport(projectId, format, onPrepared)
            },
            onWriteExportBytes = { uri, bytes, projectTitle ->
                viewModel.writeExportBytesToUri(uri, bytes, projectTitle)
            },
            onDismiss = { viewModel.closeTemplatesModal() }
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

    // Quick Share to Thingsbook Modal (ACTION_SEND receiver & Voice Quick Capture)
    val isSmartParsingGemini by viewModel.isSmartParsingGemini.collectAsState()
    quickShareDraft?.let { draft ->
        ThingsQuickShareModal(
            draft = draft,
            projects = activeProjects,
            areas = areas,
            isSmartParsingGemini = isSmartParsingGemini,
            onDismiss = { viewModel.dismissQuickShareDraft() },
            onSave = { title, notes, schedule, projId, areaId ->
                viewModel.saveQuickShareTask(title, notes, schedule, projId, areaId)
            },
            onSmartParseWithGemini = { spokenDump, schedule, projId, areaId ->
                viewModel.smartParseQuickCaptureWithGemini(spokenDump, schedule, projId, areaId, activeLocale)
            }
        )
    }

    // Project Deletion Confirmation Prompt (when deleting a project with associated non-deleted to-dos)
    val pendingProjectDeletePrompt by viewModel.pendingProjectDeletePrompt.collectAsState()
    pendingProjectDeletePrompt?.let { prompt ->
        ThingsDeleteProjectConfirmDialog(
            projectTitle = prompt.projectTitle,
            associatedTaskCount = prompt.associatedTaskCount,
            onDeleteProjectAndTasks = { viewModel.confirmDeleteProject(deleteAssociatedTasks = true) },
            onKeepTasksDeleteProjectOnly = { viewModel.confirmDeleteProject(deleteAssociatedTasks = false) },
            onCancel = { viewModel.dismissDeleteProjectPrompt() }
        )
    }

    // Empty Trash Confirmation Dialog
    if (showEmptyTrashConfirmDialog) {
        ThingsEmptyTrashConfirmDialog(
            onDismiss = onDismissEmptyTrashConfirmDialog,
            onConfirmEmptyTrash = { viewModel.emptyTrash() }
        )
    }
}

@Composable
private fun ThingsDeleteProjectConfirmDialog(
    projectTitle: String,
    associatedTaskCount: Int,
    onDeleteProjectAndTasks: () -> Unit,
    onKeepTasksDeleteProjectOnly: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = ThingsTheme.colors
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(18.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.deadlineSoftBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = colors.deadlineUrgentRed,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.project_delete_prompt_title, projectTitle),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = stringResource(R.string.project_delete_prompt_tasks_body, associatedTaskCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText
                )

                Spacer(modifier = Modifier.height(2.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDeleteProjectAndTasks,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.deadlineUrgentRed,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.project_delete_with_tasks_btn),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = onKeepTasksDeleteProjectOnly,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.hairlineDivider),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.tagPillBg,
                            contentColor = colors.primaryText
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.project_delete_keep_tasks_btn),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = colors.primaryText
                        )
                    }

                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.secondaryText
                        )
                    }
                }
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThingsMultiSelectionBar(
    selectedCount: Int,
    totalVisibleCount: Int,
    onSelectAll: () -> Unit,
    onScheduleToday: () -> Unit,
    onScheduleTomorrow: () -> Unit,
    onScheduleSomeday: () -> Unit = {},
    onOpenBatchTags: () -> Unit = {},
    onBatchPin: () -> Unit = {},
    onBatchDuplicate: () -> Unit = {},
    onOpenBatchMove: () -> Unit,
    onBatchComplete: () -> Unit,
    onBatchDelete: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    @Composable
    fun SelectionCountAndSelectAllCluster() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.thingsBlue)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.multiselect_count, selectedCount),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            if (selectedCount < totalVisibleCount) {
                ThingsHoverTooltip(text = stringResource(R.string.multiselect_select_all)) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectAll()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SelectAll,
                            contentDescription = stringResource(R.string.multiselect_select_all),
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun TrailingPrimaryActionsCluster() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ThingsHoverTooltip(text = stringResource(R.string.action_complete)) {
                ThingsTagPill(
                    title = "✓ ${stringResource(R.string.action_complete)}",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBatchComplete()
                    }
                )
            }

            ThingsHoverTooltip(text = stringResource(R.string.action_delete)) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBatchDelete()
                    },
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

            ThingsHoverTooltip(text = stringResource(R.string.multiselect_clear)) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClearSelection()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.multiselect_clear),
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun BatchPropertyPillsContent() {
        ThingsHoverTooltip(text = stringResource(R.string.view_today)) {
            ThingsTagPill(
                title = "★ ${stringResource(R.string.view_today)}",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onScheduleToday()
                }
            )
        }

        ThingsHoverTooltip(text = stringResource(R.string.date_tomorrow)) {
            ThingsTagPill(
                title = "📅 ${stringResource(R.string.date_tomorrow)}",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onScheduleTomorrow()
                }
            )
        }

        ThingsHoverTooltip(text = stringResource(R.string.v25_batch_someday)) {
            ThingsTagPill(
                title = "📦 ${stringResource(R.string.v25_batch_someday)}",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onScheduleSomeday()
                }
            )
        }

        ThingsHoverTooltip(text = stringResource(R.string.v25_batch_tags)) {
            ThingsTagPill(
                title = "🏷️ ${stringResource(R.string.v25_batch_tags)}",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenBatchTags()
                }
            )
        }

        ThingsHoverTooltip(text = stringResource(R.string.v25_batch_pin)) {
            ThingsTagPill(
                title = "📌 ${stringResource(R.string.v25_batch_pin)}",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBatchPin()
                }
            )
        }

        ThingsHoverTooltip(text = "${stringResource(R.string.v25_batch_duplicate)} (Ctrl+D)") {
            ThingsTagPill(
                title = "⧉ ${stringResource(R.string.v25_batch_duplicate)}",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBatchDuplicate()
                }
            )
        }

        ThingsHoverTooltip(text = stringResource(R.string.move_dialog_title)) {
            ThingsTagPill(
                title = "📁 ${stringResource(R.string.action_move)}",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenBatchMove()
                }
            )
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val isWideSingleBar = maxWidth >= 980.dp
        Surface(
            modifier = Modifier
                .then(if (isWideSingleBar) Modifier.widthIn(max = 1120.dp) else Modifier.fillMaxWidth())
                .clip(RoundedCornerShape(18.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, colors.thingsBlue.copy(alpha = 0.28f)),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            if (isWideSingleBar) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SelectionCountAndSelectAllCluster()
                    BatchPropertyPillsContent()
                    TrailingPrimaryActionsCluster()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SelectionCountAndSelectAllCluster()
                        TrailingPrimaryActionsCluster()
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BatchPropertyPillsContent()
                    }
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
    val stalledStatus = remember(project) { StalledProjectDetector.evaluateProject(project) }

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = project.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.primaryText
                        )
                        if (stalledStatus?.hasNoNextAction == true) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.todayStarYellow.copy(alpha = 0.22f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.WarningAmber,
                                        contentDescription = null,
                                        tint = colors.somedayWarmAmber,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.v2_stalled_project_badge),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = colors.somedayWarmAmber
                                    )
                                }
                            }
                        } else if (stalledStatus?.isUnreviewed == true) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.thingsBlueSoft
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = colors.thingsBlue,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.v2_requires_review_badge),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = colors.thingsBlue
                                    )
                                }
                            }
                        }
                    }
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
                    ThingsHoverTooltip(text = stringResource(R.string.action_move)) {
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
                    ThingsHoverTooltip(text = stringResource(R.string.action_move)) {
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
    isQuickFindOpen: Boolean,
    syncStatus: CloudSyncStatus,
    canShowInspectorToggle: Boolean = false,
    isInspectorVisible: Boolean = true,
    onToggleSidebar: () -> Unit,
    onToggleInspector: () -> Unit = {},
    onOpenQuickFind: () -> Unit,
    onOpenVoiceCapture: () -> Unit,
    onOpenGeminiHub: () -> Unit,
    onManualSync: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = ThingsTheme.colors

    ThingsTopBarTooltipScope {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val barWidth = maxWidth
            val showSearchText = barWidth >= 460.dp
            val showVoiceButton = barWidth >= 420.dp
            val showGeminiText = barWidth >= 400.dp
            val iconButtonSize = if (barWidth >= 520.dp) 34.dp else 32.dp
            val iconGlyphSize = if (barWidth >= 520.dp) 19.dp else 18.dp

            val sidebarTooltip = if (isSidebarVisible) {
                stringResource(R.string.topbar_tooltip_hide_sidebar)
            } else {
                stringResource(R.string.topbar_tooltip_show_sidebar)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (barWidth >= 600.dp) 16.dp else 10.dp,
                        vertical = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left cluster: ☰ Sidebar Toggle + 🔍 Quick Find pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (barWidth >= 520.dp) 8.dp else 6.dp)
                ) {
                    ThingsHoverTooltip(text = sidebarTooltip) {
                        IconButton(
                            onClick = onToggleSidebar,
                            modifier = Modifier.size(iconButtonSize)
                        ) {
                            Icon(
                                imageVector = if (isSidebarVisible) Icons.AutoMirrored.Outlined.MenuOpen else Icons.Outlined.Menu,
                                contentDescription = sidebarTooltip,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(if (isWideScreen) 20.dp else 19.dp)
                            )
                        }
                    }

                    ThingsHoverTooltip(
                        text = stringResource(R.string.topbar_tooltip_quick_find),
                        enabled = !showSearchText
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isQuickFindOpen) colors.thingsBlueSoft else colors.tagPillBg)
                                .clickable(onClick = onOpenQuickFind)
                                .padding(
                                    horizontal = if (showSearchText) 11.dp else 8.5.dp,
                                    vertical = 6.5.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.topbar_tooltip_quick_find),
                                tint = if (isQuickFindOpen) colors.thingsBlue else colors.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                            if (showSearchText) {
                                Text(
                                    text = stringResource(R.string.quick_find_title),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isQuickFindOpen) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isQuickFindOpen) colors.thingsBlue else colors.secondaryText,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Right cluster: 🎙️ Voice (>= 420dp) + ✨ Gemini + ☁️ Cloud Sync + ◫ Inspector Toggle + ⚙️ Settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (barWidth >= 520.dp) 4.dp else 2.dp)
                ) {
                    if (showVoiceButton) {
                        ThingsHoverTooltip(text = stringResource(R.string.topbar_tooltip_voice_input)) {
                            IconButton(
                                onClick = onOpenVoiceCapture,
                                modifier = Modifier.size(iconButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = stringResource(R.string.topbar_tooltip_voice_input),
                                    tint = colors.thingsBlue,
                                    modifier = Modifier.size(iconGlyphSize)
                                )
                            }
                        }
                    }

                    ThingsHoverTooltip(
                        text = stringResource(R.string.gemini_hub_title),
                        enabled = !showGeminiText
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.thingsBlueSoft)
                                .clickable(onClick = onOpenGeminiHub)
                                .padding(
                                    horizontal = if (showGeminiText) 10.dp else 8.5.dp,
                                    vertical = 6.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = stringResource(R.string.gemini_hub_title),
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(15.5.dp)
                            )
                            if (showGeminiText) {
                                Text(
                                    text = "Gemini",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.thingsBlue,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    ThingsHoverTooltip(text = stringResource(R.string.topbar_tooltip_cloud_sync)) {
                        IconButton(
                            onClick = onManualSync,
                            modifier = Modifier.size(iconButtonSize)
                        ) {
                            val (syncIcon, syncTint) = when (syncStatus) {
                                CloudSyncStatus.SYNCED -> Icons.Outlined.CloudDone to colors.logbookGreen
                                CloudSyncStatus.SYNCING -> Icons.Outlined.CloudSync to colors.thingsBlue
                                CloudSyncStatus.OFFLINE -> Icons.Outlined.CloudOff to colors.secondaryText
                            }
                            Icon(
                                imageVector = syncIcon,
                                contentDescription = stringResource(R.string.topbar_tooltip_cloud_sync),
                                tint = syncTint,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        }
                    }

                    if (canShowInspectorToggle) {
                        val inspectorTooltip = stringResource(R.string.v30_inspector_toggle_tooltip)
                        ThingsHoverTooltip(text = inspectorTooltip) {
                            IconButton(
                                onClick = onToggleInspector,
                                modifier = Modifier.size(iconButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VerticalSplit,
                                    contentDescription = inspectorTooltip,
                                    tint = if (isInspectorVisible) colors.thingsBlue else colors.secondaryText,
                                    modifier = Modifier.size(iconGlyphSize)
                                )
                            }
                        }
                    }

                    ThingsHoverTooltip(text = stringResource(R.string.topbar_tooltip_settings)) {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(iconButtonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.topbar_tooltip_settings),
                                tint = colors.primaryText,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        }
                    }
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
        com.pv.thingsbook.domain.model.ViewTagFilterUtils.resolveUsedTagsForView(
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
            val allTagsLabel = stringResource(R.string.tags_filter_all)
            ThingsHoverTooltip(text = allTagsLabel) {
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
                        text = allTagsLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isAllSelected) Color.White else colors.tagPillText
                    )
                }
            }
        }

        items(usedTags, key = { "tag_chip_${it.id}" }) { tag ->
            val isSelected = selectedTagId == tag.id
            ThingsHoverTooltip(text = "#${tag.title}") {
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
}

@Composable
private fun SmartViewNavigationStrip(
    navTarget: ActiveNavigationTarget,
    areas: List<Area>,
    projects: List<Project>,
    savedPerspectives: List<SavedPerspective> = emptyList(),
    showPerspectivesInSidebar: Boolean = false,
    dragController: ProjectDragDropController? = null,
    onSelectSmartView: (SmartViewType) -> Unit,
    onSelectArea: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onSelectPerspective: (String) -> Unit = {},
    onCreateNewPerspective: () -> Unit = {},
    onOpenAreaManager: () -> Unit,
    onOpenTagManager: () -> Unit = {},
    onCreateNewProject: () -> Unit,
    onOpenWeeklyReview: () -> Unit = {},
    onOpenTemplatesAndImport: () -> Unit = {}
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
        // 1. Smart Views (M3 Expressive capsule pills + monochrome-to-Sky-Blue icons)
        items(SmartViewType.entries) { viewType ->
            val selected = navTarget == ActiveNavigationTarget.SmartView(viewType)
            val viewTitle = stringResource(viewType.titleRes)
            ThingsHoverTooltip(text = viewTitle) {
                Row(
                    modifier = Modifier
                        .then(
                            if (dragController != null) {
                                Modifier.workspaceDropTarget(
                                    controller = dragController,
                                    key = "strip_smart_${viewType.name}",
                                    destination = WorkspaceDragDropDestination.SmartView(viewType, viewTitle),
                                    cornerRadius = 50.dp,
                                    priority = 22
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colors.thingsBlueSoft else Color.Transparent)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectSmartView(viewType)
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmartViewIcon(viewType = viewType, modifier = Modifier.size(18.dp), isSelected = selected)
                    Text(
                        text = viewTitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) colors.thingsBlue else colors.secondaryText
                    )
                }
            }
        }

        // 1b. Weekly Review & Templates Quick Triggers
        item(key = "nav_weekly_review") {
            val reviewTitle = stringResource(R.string.v2_weekly_review_title)
            ThingsHoverTooltip(text = reviewTitle) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Transparent)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenWeeklyReview()
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Checklist,
                        contentDescription = reviewTitle,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = reviewTitle,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.secondaryText
                    )
                }
            }
        }

        item(key = "nav_templates_import") {
            val templatesTitle = stringResource(R.string.v2_templates_title)
            ThingsHoverTooltip(text = templatesTitle) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Transparent)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenTemplatesAndImport()
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = templatesTitle,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = templatesTitle,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.secondaryText
                    )
                }
            }
        }

        // 1c. Custom Saved Perspectives (v3.0)
        if (showPerspectivesInSidebar) {
            items(savedPerspectives, key = { "nav_persp_${it.id}" }) { persp ->
                val selected = navTarget == ActiveNavigationTarget.PerspectiveDetail(persp.id)
                ThingsHoverTooltip(text = "${stringResource(R.string.v30_perspectives_section)}: ${persp.title}") {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.thingsBlueSoft else colors.tagPillBg.copy(alpha = 0.72f))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectPerspective(persp.id)
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesomeMosaic,
                            contentDescription = persp.title,
                            tint = if (selected) colors.thingsBlue else colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = persp.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            color = if (selected) colors.thingsBlue else colors.primaryText
                        )
                    }
                }
            }

            item(key = "nav_add_perspective") {
                val newPerspLabel = stringResource(R.string.v30_perspective_new)
                ThingsHoverTooltip(text = newPerspLabel) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.thingsBlueSoft.copy(alpha = 0.75f))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCreateNewPerspective()
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesomeMosaic,
                            contentDescription = newPerspLabel,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "+ ${stringResource(R.string.v30_perspectives_section)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.thingsBlue
                        )
                    }
                }
            }
        }

        if (areas.isNotEmpty()) {
            item(key = "nav_perspectives_areas_divider") {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .width(1.dp)
                        .height(20.dp)
                        .background(colors.hairlineDivider)
                )
            }
        }

        // 2. Areas -> Nested Projects Hierarchy (Projects live inside Areas in Thingsbook 3.0)
        areas.forEach { area ->
            item(key = "nav_area_${area.id}") {
                val selected = navTarget == ActiveNavigationTarget.AreaDetail(area.id)
                ThingsHoverTooltip(text = "${stringResource(R.string.entity_area)}: ${area.title}") {
                    Row(
                        modifier = Modifier
                            .then(
                                if (dragController != null) {
                                    Modifier.workspaceDropTarget(
                                        controller = dragController,
                                        key = "strip_area_${area.id}",
                                        destination = WorkspaceDragDropDestination.AreaTarget(area.id, area.title),
                                        cornerRadius = 50.dp,
                                        priority = 22
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.thingsBlueSoft else colors.tagPillBg.copy(alpha = 0.75f))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectArea(area.id)
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = area.title,
                            tint = if (selected) colors.thingsBlue else colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = area.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            color = if (selected) colors.thingsBlue else colors.primaryText
                        )
                    }
                }
            }

            val areaProjects = projects.filter { it.areaId == area.id }
            items(areaProjects, key = { "nav_proj_${it.id}" }) { project ->
                val selected = navTarget == ActiveNavigationTarget.ProjectDetail(project.id)
                ThingsHoverTooltip(text = "${area.title} › $projectLabel: ${project.title}") {
                    Row(
                        modifier = Modifier
                            .then(
                                if (dragController != null) {
                                    Modifier.workspaceDropTarget(
                                        controller = dragController,
                                        key = "strip_proj_${project.id}",
                                        destination = WorkspaceDragDropDestination.ProjectTarget(project.id, project.title),
                                        cornerRadius = 50.dp,
                                        priority = 22
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.thingsBlueSoft else Color.Transparent)
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
                            color = if (selected) colors.thingsBlue else colors.secondaryText
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
            }
        }

        // 3. Legacy Unassigned Projects (preserving 2.x upgraded standalone projects)
        val unassignedStripProjects = projects.filter { proj ->
            proj.areaId == null || areas.none { it.id == proj.areaId }
        }
        items(unassignedStripProjects, key = { "nav_proj_unassigned_${it.id}" }) { project ->
            val selected = navTarget == ActiveNavigationTarget.ProjectDetail(project.id)
            ThingsHoverTooltip(text = "$projectLabel: ${project.title}") {
                Row(
                    modifier = Modifier
                        .then(
                            if (dragController != null) {
                                Modifier.workspaceDropTarget(
                                    controller = dragController,
                                    key = "strip_proj_unassigned_${project.id}",
                                    destination = WorkspaceDragDropDestination.ProjectTarget(project.id, project.title),
                                    cornerRadius = 50.dp,
                                    priority = 22
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colors.thingsBlueSoft else Color.Transparent)
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
                        color = if (selected) colors.thingsBlue else colors.secondaryText
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
        }

        // 4. "+ Project" Quick Trigger
        item(key = "nav_add_project") {
            ThingsHoverTooltip(text = "${stringResource(R.string.entity_new_project)} (Ctrl+Shift+N)") {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.thingsBlueSoft)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCreateNewProject()
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
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
        }

        // 5. "Areas / Bereiche / Aree / Domaines / Áreas (X/8)" Quick Trigger
        item(key = "nav_manage_areas") {
            ThingsHoverTooltip(
                text = stringResource(
                    R.string.areas_manage_count_tooltip,
                    areas.size,
                    ThingsRepository.MAX_AREAS
                )
            ) {
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
                        imageVector = Icons.Outlined.Folder,
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

        // 6. Quiet Categories Manager Quick Trigger
        item(key = "nav_manage_tags") {
            val categoriesLabel = stringResource(R.string.topbar_tooltip_tags)
            ThingsHoverTooltip(text = categoriesLabel) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.tagPillBg)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenTagManager()
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GridView,
                        contentDescription = categoriesLabel,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = categoriesLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.primaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewSortMenuPill(
    viewSortMode: String,
    onSelectViewSortMode: (String) -> Unit
) {
    val colors = ThingsTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val activeSortLabel = when (viewSortMode) {
        "deadline" -> stringResource(R.string.v25_view_sort_deadline)
        "duration" -> stringResource(R.string.v25_view_sort_duration)
        "effort" -> stringResource(R.string.v30_view_sort_effort)
        "alpha" -> stringResource(R.string.v25_view_sort_alpha)
        else -> stringResource(R.string.v25_view_sort_manual)
    }
    val isNonDefaultSort = viewSortMode != "manual"

    Box {
        ThingsHoverTooltip(text = "${stringResource(R.string.v25_view_sort_label)}: $activeSortLabel") {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = true },
                shape = RoundedCornerShape(10.dp),
                color = if (isNonDefaultSort) colors.thingsBlueSoft else colors.tagPillBg,
                border = BorderStroke(
                    width = 0.75.dp,
                    color = if (isNonDefaultSort) colors.thingsBlue.copy(alpha = 0.42f) else colors.hairlineDivider
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = stringResource(R.string.v25_view_sort_label),
                        tint = if (isNonDefaultSort) colors.thingsBlue else colors.secondaryText,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isNonDefaultSort) activeSortLabel else stringResource(R.string.v25_view_sort_label),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isNonDefaultSort) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isNonDefaultSort) colors.thingsBlue else colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val options = listOf(
                "manual" to stringResource(R.string.v25_view_sort_manual),
                "deadline" to stringResource(R.string.v25_view_sort_deadline),
                "duration" to stringResource(R.string.v25_view_sort_duration),
                "effort" to stringResource(R.string.v30_view_sort_effort),
                "alpha" to stringResource(R.string.v25_view_sort_alpha)
            )
            options.forEach { (modeKey, modeLabel) ->
                val selected = viewSortMode == modeKey
                DropdownMenuItem(
                    leadingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(16.dp))
                        }
                    },
                    text = {
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selected) colors.thingsBlue else colors.primaryText
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectViewSortMode(modeKey)
                    }
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
    projectWorkspaceMode: ProjectWorkspaceMode = ProjectWorkspaceMode.LIST,
    onSelectProjectWorkspaceMode: (ProjectWorkspaceMode) -> Unit = {},
    currentArea: Area?,
    currentPerspective: SavedPerspective? = null,
    areas: List<Area>,
    projects: List<Project>,
    projectAttachments: List<TaskAttachment> = emptyList(),
    locale: Locale,
    trashItemCount: Int = 0,
    isTodayTimelineMode: Boolean = false,
    onToggleTodayTimelineMode: (Boolean) -> Unit = {},
    onToggleTimelineFullScreen: (() -> Unit)? = null,
    todayPlannedMinutes: Int = 0,
    todayFreeMinutes: Int = 480,
    calendarEventsEnabled: Boolean = false,
    viewSortMode: String = "manual",
    onSelectViewSortMode: (String) -> Unit = {},
    onEditPerspective: (SavedPerspective) -> Unit = {},
    onDeletePerspective: (String) -> Unit = {},
    onSaveProjectAsTemplate: (String, String) -> Unit = { _, _ -> },
    onOpenExternalImportExport: (String?) -> Unit = {},
    onOpenStalledProjectReview: () -> Unit = {},
    onMarkProjectReviewed: (String) -> Unit = {},
    onUpdateProjectDeadline: (String, Long?) -> Unit,
    onUpdateProjectArea: (String, String?) -> Unit,
    onEditProject: (Project) -> Unit,
    onDuplicateProject: (String) -> Unit = {},
    onCompleteProject: (String) -> Unit,
    onMoveProject: (String, Boolean) -> Unit,
    onDeleteProject: (String) -> Unit,
    onAddProjectAttachment: (String, String, String, ByteArray) -> Unit = { _, _, _, _ -> },
    onDeleteProjectAttachment: (String) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onRequestEmptyTrash: () -> Unit = {},
    onAddTaskInCurrentView: () -> Unit,
    onAddHeadingInProject: () -> Unit,
    onRenameArea: (String, String) -> Unit,
    onMoveArea: (String, Boolean) -> Unit,
    onDeleteArea: (String) -> Unit
) {
    val colors = ThingsTheme.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    var showProjectDeadlineDialog by remember(currentProject?.id) { mutableStateOf(false) }
    var showProjectAreaMenu by remember(currentProject?.id) { mutableStateOf(false) }
    var showProjectAttachMenu by remember(currentProject?.id) { mutableStateOf(false) }
    var showProjectMoreMenu by remember(currentProject?.id) { mutableStateOf(false) }
    var showAreaMoreMenu by remember(currentArea?.id) { mutableStateOf(false) }
    val tooLargeMsg = stringResource(R.string.attachment_too_large)
    val attachmentImportErrorMsg = stringResource(R.string.attachment_import_error)
    val templateSavedToastMsg = stringResource(R.string.v2_template_saved_toast)
    val stalledStatus = remember(currentProject) {
        currentProject?.let { StalledProjectDetector.evaluateProject(it) }
    }
    val supportsViewSorting = navTarget != ActiveNavigationTarget.SmartView(SmartViewType.LOGBOOK) &&
        navTarget != ActiveNavigationTarget.SmartView(SmartViewType.TRASH)

    val projectFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val proj = currentProject ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            coroutineScope.launch {
                when (val outcome = processPickedAttachmentUri(context, uri)) {
                    is AttachmentImportOutcome.Success -> {
                        onAddProjectAttachment(
                            proj.id,
                            outcome.result.fileName,
                            outcome.result.mimeType,
                            outcome.result.bytes
                        )
                    }
                    AttachmentImportOutcome.TooLarge -> onShowMessage(tooLargeMsg)
                    AttachmentImportOutcome.Error -> onShowMessage(attachmentImportErrorMsg)
                }
            }
        }
    }

    val projectPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val proj = currentProject ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            coroutineScope.launch {
                when (val outcome = processPickedAttachmentUri(context, uri)) {
                    is AttachmentImportOutcome.Success -> {
                        onAddProjectAttachment(
                            proj.id,
                            outcome.result.fileName,
                            outcome.result.mimeType,
                            outcome.result.bytes
                        )
                    }
                    AttachmentImportOutcome.TooLarge -> onShowMessage(tooLargeMsg)
                    AttachmentImportOutcome.Error -> onShowMessage(attachmentImportErrorMsg)
                }
            }
        }
    }

    val projectCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val proj = currentProject ?: return@rememberLauncherForActivityResult
        if (bitmap != null) {
            val processed = processCapturedPhotoBitmap(bitmap)
            onAddProjectAttachment(
                proj.id,
                processed.fileName,
                processed.mimeType,
                processed.bytes
            )
        }
    }

    val launchProjectCamera: () -> Unit = {
        val launchedCamera = runCatching {
            projectCameraLauncher.launch(null)
            true
        }.getOrDefault(false)
        if (!launchedCamera) {
            runCatching {
                projectPhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }
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
    val hasSmartViewControls = navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY) ||
        (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TRASH) && trashItemCount > 0) ||
        (navTarget is ActiveNavigationTarget.SmartView && supportsViewSorting)

    val renderSmartViewControls: @Composable () -> Unit = {
        if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY)) {
            ViewSortMenuPill(
                viewSortMode = viewSortMode,
                onSelectViewSortMode = onSelectViewSortMode
            )
            ThingsTodayModeSegmentedToggle(
                isTimelineMode = isTodayTimelineMode,
                onModeChange = onToggleTodayTimelineMode
            )
            if (isTodayTimelineMode && onToggleTimelineFullScreen != null) {
                val fullscreenLabel = stringResource(R.string.v30_timeline_fullscreen)
                ThingsHoverTooltip(text = fullscreenLabel) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleTimelineFullScreen()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.tagPillBg,
                        border = BorderStroke(0.75.dp, colors.hairlineDivider)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInFull,
                                contentDescription = fullscreenLabel,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = fullscreenLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.thingsBlue,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TRASH) && trashItemCount > 0) {
            ThingsHoverTooltip(text = stringResource(R.string.trash_empty_btn)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.deadlineSoftBg)
                        .clickable(onClick = onRequestEmptyTrash)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.trash_empty_btn),
                        tint = colors.deadlineUrgentRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.trash_empty_btn),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.deadlineUrgentRed,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else if (navTarget is ActiveNavigationTarget.SmartView && supportsViewSorting) {
            ViewSortMenuPill(
                viewSortMode = viewSortMode,
                onSelectViewSortMode = onSelectViewSortMode
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompactSmartViewHeader = maxWidth < 600.dp && navTarget is ActiveNavigationTarget.SmartView

            if (isCompactSmartViewHeader && navTarget is ActiveNavigationTarget.SmartView) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SmartViewIcon(
                            viewType = navTarget.type,
                            modifier = Modifier.size(28.dp),
                            isSelected = true
                        )
                        Text(
                            text = stringResource(navTarget.type.titleRes),
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.primaryText,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (hasSmartViewControls) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            renderSmartViewControls()
                        }
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
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (navTarget) {
                            is ActiveNavigationTarget.SmartView -> {
                                SmartViewIcon(
                                    viewType = navTarget.type,
                                    modifier = Modifier.size(28.dp),
                                    isSelected = true
                                )
                                Text(
                                    text = stringResource(navTarget.type.titleRes),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = colors.primaryText,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            is ActiveNavigationTarget.PerspectiveDetail -> {
                                val perspTitle = currentPerspective?.title ?: stringResource(R.string.v30_perspectives_section)
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesomeMosaic,
                                    contentDescription = null,
                                    tint = colors.thingsBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                                ThingsHoverTooltip(
                                    text = perspTitle,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = perspTitle,
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = colors.primaryText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            is ActiveNavigationTarget.ProjectDetail -> {
                                val projTitle = currentProject?.title ?: stringResource(R.string.entity_project)
                                ProjectProgressRing(
                                    progress = currentProject?.progress ?: 0f,
                                    size = 26.dp
                                )
                                ThingsHoverTooltip(
                                    text = projTitle,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = projTitle,
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = colors.primaryText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            is ActiveNavigationTarget.AreaDetail -> {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = colors.thingsBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                                if (isRenamingAreaInline && currentArea != null) {
                                    val commitAreaRename = {
                                        if (inlineAreaField.text.isNotBlank()) {
                                            onRenameArea(currentArea.id, inlineAreaField.text.trim())
                                            isRenamingAreaInline = false
                                        }
                                    }
                                    OutlinedTextField(
                                        value = inlineAreaField,
                                        onValueChange = { inlineAreaField = it },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { commitAreaRename() }),
                                        modifier = Modifier
                                            .weight(1f)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown &&
                                                    (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                                ) {
                                                    commitAreaRename()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    val txt = inlineAreaField.text
                                                    inlineAreaField = inlineAreaField.copy(
                                                        selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                                    )
                                                }
                                            }
                                    )
                                    ThingsHoverTooltip(text = stringResource(R.string.action_save)) {
                                        IconButton(onClick = commitAreaRename) {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = stringResource(R.string.action_save),
                                                tint = colors.thingsBlue
                                            )
                                        }
                                    }
                                    ThingsHoverTooltip(text = stringResource(R.string.action_cancel)) {
                                        IconButton(onClick = { isRenamingAreaInline = false }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = stringResource(R.string.action_cancel),
                                                tint = colors.secondaryText
                                            )
                                        }
                                    }
                                } else {
                                    val areaTitle = currentArea?.title ?: stringResource(R.string.entity_area)
                                    ThingsHoverTooltip(
                                        text = areaTitle,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = areaTitle,
                                            style = MaterialTheme.typography.headlineLarge,
                                            color = colors.primaryText,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Compact Row 1 Trailing Actions (never crowding the title!)
                    if (navTarget is ActiveNavigationTarget.SmartView) {
                        if (hasSmartViewControls) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                renderSmartViewControls()
                            }
                        }
                    } else if (currentPerspective != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            ThingsHoverTooltip(text = stringResource(R.string.v30_perspective_edit)) {
                                IconButton(
                                    onClick = { onEditPerspective(currentPerspective) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.v30_perspective_edit),
                                        tint = colors.thingsBlue,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                            ThingsHoverTooltip(text = stringResource(R.string.v30_perspective_delete)) {
                                IconButton(
                                    onClick = { onDeletePerspective(currentPerspective.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.v30_perspective_delete),
                                        tint = colors.deadlineUrgentRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else if (currentProject != null) {
                        val projIdx = projects.indexOfFirst { it.id == currentProject.id }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
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
                            Box {
                                ThingsHoverTooltip(text = stringResource(R.string.action_more_options)) {
                                    IconButton(
                                        onClick = { showProjectMoreMenu = true },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreHoriz,
                                            contentDescription = stringResource(R.string.action_more_options),
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showProjectMoreMenu,
                                    onDismissRequest = { showProjectMoreMenu = false }
                                ) {
                                    if (projIdx > 0) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.ArrowUpward,
                                                    contentDescription = null,
                                                    tint = colors.secondaryText,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            },
                                            text = { Text(stringResource(R.string.project_move_up)) },
                                            onClick = {
                                                showProjectMoreMenu = false
                                                onMoveProject(currentProject.id, true)
                                            }
                                        )
                                    }
                                    if (projIdx != -1 && projIdx < projects.lastIndex) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.ArrowDownward,
                                                    contentDescription = null,
                                                    tint = colors.secondaryText,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            },
                                            text = { Text(stringResource(R.string.project_move_down)) },
                                            onClick = {
                                                showProjectMoreMenu = false
                                                onMoveProject(currentProject.id, false)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = null,
                                                tint = colors.secondaryText,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        },
                                        text = { Text(stringResource(R.string.action_duplicate_project)) },
                                        onClick = {
                                            showProjectMoreMenu = false
                                            onDuplicateProject(currentProject.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Checklist,
                                                contentDescription = null,
                                                tint = colors.thingsBlue,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        },
                                        text = { Text(stringResource(R.string.v2_save_as_template)) },
                                        onClick = {
                                            showProjectMoreMenu = false
                                            onSaveProjectAsTemplate(currentProject.id, templateSavedToastMsg)
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.SystemUpdateAlt,
                                                contentDescription = null,
                                                tint = colors.thingsBlue,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        },
                                        text = { Text(stringResource(R.string.v30_external_import_export_tab)) },
                                        onClick = {
                                            showProjectMoreMenu = false
                                            onOpenExternalImportExport(currentProject.areaId)
                                        }
                                    )
                                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircleOutline,
                                                contentDescription = null,
                                                tint = colors.logbookGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        text = { Text(stringResource(R.string.project_complete_tooltip)) },
                                        onClick = {
                                            showProjectMoreMenu = false
                                            onCompleteProject(currentProject.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = null,
                                                tint = colors.deadlineUrgentRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = stringResource(R.string.project_delete_tooltip),
                                                color = colors.deadlineUrgentRed
                                            )
                                        },
                                        onClick = {
                                            showProjectMoreMenu = false
                                            onDeleteProject(currentProject.id)
                                        }
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
                            Box {
                                ThingsHoverTooltip(text = stringResource(R.string.action_more_options)) {
                                    IconButton(
                                        onClick = { showAreaMoreMenu = true },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreHoriz,
                                            contentDescription = stringResource(R.string.action_more_options),
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showAreaMoreMenu,
                                    onDismissRequest = { showAreaMoreMenu = false }
                                ) {
                                    if (areaIdx > 0) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.ArrowUpward,
                                                    contentDescription = null,
                                                    tint = colors.secondaryText,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            },
                                            text = { Text(stringResource(R.string.area_move_up)) },
                                            onClick = {
                                                showAreaMoreMenu = false
                                                onMoveArea(currentArea.id, true)
                                            }
                                        )
                                    }
                                    if (areaIdx != -1 && areaIdx < areas.lastIndex) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.ArrowDownward,
                                                    contentDescription = null,
                                                    tint = colors.secondaryText,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            },
                                            text = { Text(stringResource(R.string.area_move_down)) },
                                            onClick = {
                                                showAreaMoreMenu = false
                                                onMoveArea(currentArea.id, false)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.SystemUpdateAlt,
                                                contentDescription = null,
                                                tint = colors.thingsBlue,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        },
                                        text = { Text(stringResource(R.string.v30_external_import_export_tab)) },
                                        onClick = {
                                            showAreaMoreMenu = false
                                            onOpenExternalImportExport(currentArea.id)
                                        }
                                    )
                                    HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = null,
                                                tint = colors.deadlineUrgentRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = stringResource(R.string.area_delete_tooltip),
                                                color = colors.deadlineUrgentRed
                                            )
                                        },
                                        onClick = {
                                            showAreaMoreMenu = false
                                            onDeleteArea(currentArea.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Row 2 Sort & Quick Import Controls for Area & Perspective Detail views
        if ((currentPerspective != null || (currentArea != null && !isRenamingAreaInline)) && supportsViewSorting) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ViewSortMenuPill(
                    viewSortMode = viewSortMode,
                    onSelectViewSortMode = onSelectViewSortMode
                )
                if (currentArea != null) {
                    ThingsHoverTooltip(text = stringResource(R.string.v30_external_import_export_tab)) {
                        ThingsTagPill(
                            title = "📥 ${stringResource(R.string.v30_external_import_export_tab)}",
                            onClick = { onOpenExternalImportExport(currentArea.id) }
                        )
                    }
                }
            }
        }

        // v3.0 Today Capacity Summary Pill (Glacier-Blue Soft Bento pill shown whenever plannedMinutes > 0)
        if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY) && todayPlannedMinutes > 0) {
            val plannedFmt = formatDurationCompact(todayPlannedMinutes) ?: "${todayPlannedMinutes}m"
            val freeFmt = formatDurationCompact(todayFreeMinutes) ?: "0m"
            val capacityText = if (calendarEventsEnabled) {
                stringResource(R.string.v30_today_capacity_summary, plannedFmt, freeFmt)
            } else {
                stringResource(R.string.v30_today_planned_only, plannedFmt)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.thingsBlueSoft,
                border = BorderStroke(0.75.dp, colors.thingsBlue.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = capacityText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.thingsBlue
                    )
                }
            }
        }

        // Project Notes, Row 2 (3-Mode Workspace Switcher + Sort + Review Status), Target Date, Area, Attachments & Quick Buttons
        if (currentProject != null) {
            if (currentProject.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentProject.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText
                )
                DetectedNotesLinksRow(notes = currentProject.notes)
            }

            if (projectAttachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                TaskAttachmentsStrip(
                    attachments = projectAttachments,
                    onDeleteAttachment = onDeleteProjectAttachment,
                    onAttachFileClick = {
                        runCatching { projectFilePickerLauncher.launch(arrayOf("*/*")) }
                    },
                    onTakePhotoClick = launchProjectCamera
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProjectWorkspaceSegmentedSwitcher(
                    currentMode = projectWorkspaceMode,
                    onSelectMode = onSelectProjectWorkspaceMode
                )
                ViewSortMenuPill(
                    viewSortMode = viewSortMode,
                    onSelectViewSortMode = onSelectViewSortMode
                )
                if (stalledStatus?.hasNoNextAction == true) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.todayStarYellow.copy(alpha = 0.24f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenStalledProjectReview)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = colors.somedayWarmAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(R.string.v2_stalled_project_badge),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.somedayWarmAmber
                            )
                        }
                    }
                } else if (stalledStatus?.isUnreviewed == true) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.thingsBlueSoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenStalledProjectReview)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(13.dp)
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
                    ThingsHoverTooltip(text = "${stringResource(R.string.project_target_date_label)}: $formattedTargetDate") {
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
                    }
                } else {
                    ThingsHoverTooltip(text = stringResource(R.string.project_set_target_date)) {
                        ThingsTagPill(
                            title = "🏁 + ${stringResource(R.string.project_set_target_date)}",
                            onClick = { showProjectDeadlineDialog = true }
                        )
                    }
                }

                // Project Area Assignment Chip ("📦 Work" / "📦 Home")
                val projectArea = areas.find { it.id == currentProject.areaId }
                Box {
                    val areaTooltip = if (projectArea != null) {
                        "$areaWord: ${projectArea.title}"
                    } else {
                        stringResource(R.string.areas_assign_chip)
                    }
                    ThingsHoverTooltip(text = areaTooltip) {
                        ThingsTagPill(
                            title = if (projectArea != null) {
                                "📦 $areaWord: ${projectArea.title}"
                            } else {
                                "📦 ${stringResource(R.string.areas_assign_chip)}"
                            },
                            onClick = { showProjectAreaMenu = true }
                        )
                    }
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

                // "+ Attach File / Photo" in Project
                Box {
                    ThingsHoverTooltip(text = stringResource(R.string.action_attach_file)) {
                        ThingsTagPill(
                            title = "📎 + ${stringResource(R.string.action_attach_file)}",
                            onClick = { showProjectAttachMenu = true }
                        )
                    }
                    DropdownMenu(
                        expanded = showProjectAttachMenu,
                        onDismissRequest = { showProjectAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = colors.thingsBlue
                                )
                            },
                            text = { Text(stringResource(R.string.action_take_photo)) },
                            onClick = {
                                showProjectAttachMenu = false
                                launchProjectCamera()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = null,
                                    tint = colors.thingsBlue
                                )
                            },
                            text = { Text(stringResource(R.string.action_attach_file)) },
                            onClick = {
                                showProjectAttachMenu = false
                                runCatching {
                                    projectFilePickerLauncher.launch(arrayOf("*/*"))
                                }
                            }
                        )
                    }
                }

                // "+ To-Do / Aufgabe" in Project
                ThingsHoverTooltip(text = "${stringResource(R.string.entity_new_todo)} (Ctrl+N)") {
                    ThingsTagPill(
                        title = "+ ${stringResource(R.string.entity_todo)}",
                        onClick = onAddTaskInCurrentView
                    )
                }

                // "+ Heading / Überschrift" in Project
                ThingsHoverTooltip(text = stringResource(R.string.entity_heading)) {
                    ThingsTagPill(
                        title = "+ ${stringResource(R.string.entity_heading)}",
                        onClick = onAddHeadingInProject
                    )
                }

                // Project Header "Asana & Planner" Quick Button
                ThingsHoverTooltip(text = stringResource(R.string.v30_external_import_export_tab)) {
                    ThingsTagPill(
                        title = "📥 ${stringResource(R.string.v30_external_import_export_tab)}",
                        onClick = { onOpenExternalImportExport(currentProject.areaId) }
                    )
                }

                // v2 Pillar 1: Quick "Mark Reviewed ✓" pill when project is unreviewed/stalled
                if (stalledStatus?.isStalled == true) {
                    ThingsHoverTooltip(text = stringResource(R.string.v2_mark_reviewed)) {
                        ThingsTagPill(
                            title = "✓ ${stringResource(R.string.v2_mark_reviewed)}",
                            onClick = {
                                onMarkProjectReviewed(currentProject.id)
                            }
                        )
                    }
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectHeadingSectionHeader(
    heading: Heading,
    isCollapsed: Boolean,
    completedTaskCount: Int = 0,
    totalTaskCount: Int = 0,
    locale: Locale = Locale.getDefault(),
    isDropHighlighted: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dragController: ProjectDragDropController? = null,
    @Suppress("UNUSED_PARAMETER") projectIdForDrop: String? = null,
    @Suppress("UNUSED_PARAMETER") dropIndexInSection: Int = 0,
    onToggleCollapse: () -> Unit,
    onRenameHeading: (String) -> Unit,
    onUpdateStartDate: (Long?) -> Unit = {},
    onUpdateEndDate: (Long?) -> Unit = {},
    onDeleteHeading: () -> Unit,
    onAddTaskUnderHeading: () -> Unit
) {
    val colors = ThingsTheme.colors
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var showStartDatePicker by remember(heading.id) { mutableStateOf(false) }
    var showEndDatePicker by remember(heading.id) { mutableStateOf(false) }
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

    val startLocalDate = remember(heading.startDate, zone) {
        heading.startDate?.let { ms ->
            runCatching { Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }.getOrNull()
        }
    }
    val formattedStartDate = remember(startLocalDate, locale) {
        startLocalDate?.let { d ->
            val pattern = if (locale.language == "de") "d. MMM" else "MMM d"
            runCatching { d.format(DateTimeFormatter.ofPattern(pattern, locale)) }.getOrNull()
        }
    }
    val endLocalDate = remember(heading.endDate, zone) {
        heading.endDate?.let { ms ->
            runCatching { Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }.getOrNull()
        }
    }
    val formattedEndDate = remember(endLocalDate, locale) {
        endLocalDate?.let { d ->
            val pattern = if (locale.language == "de") "d. MMM" else "MMM d"
            runCatching { d.format(DateTimeFormatter.ofPattern(pattern, locale)) }.getOrNull()
        }
    }
    val openCount = (totalTaskCount - completedTaskCount).coerceAtLeast(0)
    val isOverduePhase = endLocalDate != null && endLocalDate.isBefore(today) && openCount > 0
    val phaseProgress = if (totalTaskCount > 0) completedTaskCount.toFloat() / totalTaskCount.toFloat() else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isDropHighlighted) {
            colors.thingsBlueSoft.copy(alpha = 0.65f)
        } else {
            colors.tagPillBg.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            width = if (isDropHighlighted) 1.6.dp else 0.75.dp,
            color = if (isDropHighlighted) colors.thingsBlue else colors.thingsBlue.copy(alpha = 0.20f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            if (isEditing) {
                val commitHeadingRename = {
                    if (editedTitleField.text.isNotBlank()) {
                        onRenameHeading(editedTitleField.text.trim())
                        isEditing = false
                    }
                }
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitHeadingRename() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(headingFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                ) {
                                    commitHeadingRename()
                                    true
                                } else {
                                    false
                                }
                            }
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    val txt = editedTitleField.text
                                    editedTitleField = editedTitleField.copy(
                                        selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                    )
                                }
                            }
                    )
                    IconButton(onClick = commitHeadingRename) {
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
                        .clickable(onClick = onToggleCollapse),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .width(3.5.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(50))
                                .background(colors.thingsBlue)
                        )
                        Text(
                            text = heading.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = colors.primaryText,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = Int.MAX_VALUE,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(top = 1.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ThingsHoverTooltip(text = stringResource(R.string.heading_add_task)) {
                            IconButton(
                                onClick = onAddTaskUnderHeading,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = stringResource(R.string.heading_add_task),
                                    tint = colors.thingsBlue,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
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
                        IconButton(
                            onClick = onToggleCollapse,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                contentDescription = stringResource(R.string.action_collapse_heading),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 11.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (totalTaskCount > 0) {
                        ProjectProgressRing(
                            progress = phaseProgress,
                            size = 16.dp,
                            showFraction = true,
                            completedCount = completedTaskCount,
                            totalCount = totalTaskCount,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }

                    // Phase Start Date Pill
                    val startPillLabel = if (formattedStartDate != null) {
                        stringResource(R.string.v30_phase_starts_on, formattedStartDate)
                    } else {
                        "+ ${stringResource(R.string.v30_phase_set_start_date)}"
                    }
                    val startPillBg = if (formattedStartDate != null) {
                        colors.thingsBlueSoft
                    } else {
                        colors.cardSurface.copy(alpha = 0.72f)
                    }
                    val startPillFg = if (formattedStartDate != null) {
                        colors.thingsBlue
                    } else {
                        colors.secondaryText
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(startPillBg)
                            .clickable { showStartDatePicker = true }
                            .padding(horizontal = 9.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = startPillFg,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = startPillLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (formattedStartDate != null) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = startPillFg
                        )
                    }

                    // Phase End Date Pill
                    val pillLabel = if (formattedEndDate != null) {
                        stringResource(R.string.phase_ends_on, formattedEndDate)
                    } else {
                        "+ ${stringResource(R.string.phase_set_end_date)}"
                    }
                    val pillBg = when {
                        isOverduePhase -> colors.deadlineSoftBg
                        formattedEndDate != null -> colors.thingsBlueSoft
                        else -> colors.cardSurface.copy(alpha = 0.72f)
                    }
                    val pillFg = when {
                        isOverduePhase -> colors.deadlineUrgentRed
                        formattedEndDate != null -> colors.thingsBlue
                        else -> colors.secondaryText
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pillBg)
                            .clickable { showEndDatePicker = true }
                            .padding(horizontal = 9.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = null,
                            tint = pillFg,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = pillLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (formattedEndDate != null) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = pillFg
                        )
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {
        DeadlineDatePickerDialog(
            currentDeadline = heading.startDate,
            locale = locale,
            onApplyDeadline = onUpdateStartDate,
            onDismiss = { showStartDatePicker = false },
            titleText = stringResource(R.string.v30_phase_set_start_date),
            clearButtonText = stringResource(R.string.v30_phase_clear_start_date)
        )
    }

    if (showEndDatePicker) {
        DeadlineDatePickerDialog(
            currentDeadline = heading.endDate,
            locale = locale,
            onApplyDeadline = onUpdateEndDate,
            onDismiss = { showEndDatePicker = false },
            titleText = stringResource(R.string.phase_set_end_date),
            clearButtonText = stringResource(R.string.phase_clear_end_date)
        )
    }
}

@Composable
private fun PhaseMilestoneCard(
    heading: Heading,
    project: Project,
    completedCount: Int,
    totalCount: Int,
    onOpenProject: () -> Unit
) {
    val colors = ThingsTheme.colors
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else project.progress

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenProject),
        shape = RoundedCornerShape(14.dp),
        color = colors.thingsBlueSoft.copy(alpha = 0.52f),
        border = BorderStroke(0.75.dp, colors.thingsBlue.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.thingsBlue)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = colors.thingsBlue.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = stringResource(R.string.phase_milestone_badge),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.thingsBlue,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                            )
                        }
                        Text(
                            text = project.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = heading.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ProjectProgressRing(
                progress = progress,
                size = 16.dp,
                showFraction = totalCount > 0,
                completedCount = completedCount,
                totalCount = totalCount
            )
        }
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
    filter: QuickFindFilter = QuickFindFilter(),
    taskResults: List<TaskItem>,
    projectResults: List<Project>,
    areas: List<Area>,
    allProjects: List<Project>,
    allTags: List<Tag> = emptyList(),
    locale: Locale,
    onQueryChange: (String) -> Unit,
    onFilterChange: (QuickFindFilter) -> Unit = {},
    onSelectSmartView: (SmartViewType) -> Unit,
    onSelectArea: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onSelectTask: (TaskItem) -> Unit,
    onClose: () -> Unit
) {
    val colors = ThingsTheme.colors
    val searchFocusRequester = remember { FocusRequester() }
    var showDateMenu by remember { mutableStateOf(false) }
    var showTagMenu by remember { mutableStateOf(false) }
    var showScopeMenu by remember { mutableStateOf(false) }
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

    val dateFilterLabelRes = when (filter.dateFilter) {
        QuickFindDateFilter.ALL -> R.string.search_filter_date_all
        QuickFindDateFilter.TODAY -> R.string.search_filter_date_today
        QuickFindDateFilter.OVERDUE -> R.string.search_filter_date_overdue
        QuickFindDateFilter.NEXT_7_DAYS -> R.string.search_filter_date_next_7_days
        QuickFindDateFilter.THIS_MONTH -> R.string.search_filter_date_this_month
        QuickFindDateFilter.NO_DATE -> R.string.search_filter_date_none
    }
    val scopeFilterLabelRes = when (filter.scope) {
        QuickFindEntityScope.ALL -> R.string.search_filter_scope_all
        QuickFindEntityScope.TASKS -> R.string.search_filter_scope_tasks
        QuickFindEntityScope.PROJECTS -> R.string.search_filter_scope_projects
    }
    val selectedTagTitle = remember(filter.tagId, allTags) {
        filter.tagId?.let { id -> allTags.find { it.id == id }?.title }
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
                            ThingsHoverTooltip(text = stringResource(R.string.quick_find_clear)) {
                                IconButton(
                                    onClick = {
                                        searchField = TextFieldValue(text = "", selection = TextRange(0))
                                        onQueryChange("")
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.quick_find_clear),
                                        tint = colors.secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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

                ThingsHoverTooltip(text = "${stringResource(R.string.action_cancel)} (Esc)") {
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText
                        )
                    }
                }
            }

            // Interactive Search Filter Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickFindFilterChip(
                    label = stringResource(R.string.search_filter_hide_completed),
                    icon = Icons.Outlined.CheckCircleOutline,
                    active = filter.hideCompleted,
                    onClick = { onFilterChange(filter.copy(hideCompleted = !filter.hideCompleted)) }
                )

                Box {
                    QuickFindFilterChip(
                        label = stringResource(dateFilterLabelRes),
                        icon = Icons.Outlined.CalendarToday,
                        active = filter.dateFilter != QuickFindDateFilter.ALL,
                        hasDropdown = true,
                        onClick = { showDateMenu = true }
                    )
                    DropdownMenu(
                        expanded = showDateMenu,
                        onDismissRequest = { showDateMenu = false }
                    ) {
                        QuickFindDateFilter.entries.forEach { option ->
                            val labelRes = when (option) {
                                QuickFindDateFilter.ALL -> R.string.search_filter_date_all
                                QuickFindDateFilter.TODAY -> R.string.search_filter_date_today
                                QuickFindDateFilter.OVERDUE -> R.string.search_filter_date_overdue
                                QuickFindDateFilter.NEXT_7_DAYS -> R.string.search_filter_date_next_7_days
                                QuickFindDateFilter.THIS_MONTH -> R.string.search_filter_date_this_month
                                QuickFindDateFilter.NO_DATE -> R.string.search_filter_date_none
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                trailingIcon = if (filter.dateFilter == option) {
                                    { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                onClick = {
                                    showDateMenu = false
                                    onFilterChange(filter.copy(dateFilter = option))
                                }
                            )
                        }
                    }
                }

                QuickFindFilterChip(
                    label = stringResource(R.string.search_filter_has_deadline),
                    icon = Icons.Outlined.Flag,
                    active = filter.hasDeadlineOnly,
                    onClick = { onFilterChange(filter.copy(hasDeadlineOnly = !filter.hasDeadlineOnly)) }
                )

                QuickFindFilterChip(
                    label = stringResource(R.string.search_filter_has_checklist),
                    icon = Icons.Outlined.Checklist,
                    active = filter.hasChecklistOnly,
                    onClick = { onFilterChange(filter.copy(hasChecklistOnly = !filter.hasChecklistOnly)) }
                )

                if (allTags.isNotEmpty()) {
                    Box {
                        QuickFindFilterChip(
                            label = selectedTagTitle?.let { "#$it" } ?: stringResource(R.string.search_filter_all_tags),
                            icon = Icons.Outlined.GridView,
                            active = filter.tagId != null,
                            hasDropdown = true,
                            onClick = { showTagMenu = true }
                        )
                        DropdownMenu(
                            expanded = showTagMenu,
                            onDismissRequest = { showTagMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.search_filter_all_tags)) },
                                trailingIcon = if (filter.tagId == null) {
                                    { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                onClick = {
                                    showTagMenu = false
                                    onFilterChange(filter.copy(tagId = null))
                                }
                            )
                            allTags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text("#${tag.title}") },
                                    trailingIcon = if (filter.tagId == tag.id) {
                                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    onClick = {
                                        showTagMenu = false
                                        onFilterChange(filter.copy(tagId = tag.id))
                                    }
                                )
                            }
                        }
                    }
                }

                Box {
                    QuickFindFilterChip(
                        label = stringResource(scopeFilterLabelRes),
                        icon = Icons.Outlined.Folder,
                        active = filter.scope != QuickFindEntityScope.ALL,
                        hasDropdown = true,
                        onClick = { showScopeMenu = true }
                    )
                    DropdownMenu(
                        expanded = showScopeMenu,
                        onDismissRequest = { showScopeMenu = false }
                    ) {
                        QuickFindEntityScope.entries.forEach { option ->
                            val labelRes = when (option) {
                                QuickFindEntityScope.ALL -> R.string.search_filter_scope_all
                                QuickFindEntityScope.TASKS -> R.string.search_filter_scope_tasks
                                QuickFindEntityScope.PROJECTS -> R.string.search_filter_scope_projects
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                trailingIcon = if (filter.scope == option) {
                                    { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                onClick = {
                                    showScopeMenu = false
                                    onFilterChange(filter.copy(scope = option))
                                }
                            )
                        }
                    }
                }

                if (filter.hasActiveFilters) {
                    QuickFindFilterChip(
                        label = stringResource(R.string.search_filter_reset),
                        icon = Icons.Outlined.Close,
                        active = false,
                        isReset = true,
                        onClick = { onFilterChange(QuickFindFilter()) }
                    )
                }
            }

            // Matching Smart Views & Areas
            val showViewsAndAreas = filter.shouldShowSmartViewsAndAreas(query)
            val matchingViews = if (!showViewsAndAreas) {
                emptyList()
            } else {
                SmartViewType.entries.filter { viewType ->
                    val localizedName = stringResource(viewType.titleRes)
                    query.isNotBlank() && localizedName.contains(query, ignoreCase = true)
                }
            }
            val matchingAreas = remember(query, areas, showViewsAndAreas) {
                if (!showViewsAndAreas || query.isBlank()) emptyList()
                else areas.filter { it.title.contains(query, ignoreCase = true) }
            }

            if (query.isBlank() && !filter.hasActiveFilters) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.quick_find_empty_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                val totalHits = matchingViews.size + matchingAreas.size + projectResults.size + taskResults.size
                Text(
                    text = if (query.isNotBlank()) {
                        stringResource(R.string.quick_find_results_for, totalHits, query)
                    } else {
                        stringResource(R.string.settings_stat_records_count, totalHits)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (totalHits == 0) {
                    Text(
                        text = stringResource(R.string.quick_find_no_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
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
                                ThingsTagPill(title = stringResource(R.string.quick_find_badge_view))
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
                                    imageVector = Icons.Outlined.Folder,
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
                            task.status == TaskStatus.COMPLETED -> "✅ ${stringResource(R.string.settings_stat_completed)}"
                            task.status == TaskStatus.CANCELED -> "✕ ${stringResource(R.string.settings_stat_canceled)}"
                            task.scheduleState == ScheduleState.TODAY -> "★ ${stringResource(R.string.view_today)}"
                            task.scheduleState == ScheduleState.THIS_EVENING -> "🌙 ${stringResource(R.string.view_this_evening)}"
                            task.scheduleState == ScheduleState.UPCOMING -> "📅 ${stringResource(R.string.view_upcoming)}"
                            task.scheduleState == ScheduleState.INBOX -> "📥 ${stringResource(R.string.view_inbox)}"
                            task.scheduleState == ScheduleState.SOMEDAY -> "📦 ${stringResource(R.string.view_someday)}"
                            else -> "🗂 ${stringResource(R.string.view_anytime)}"
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
                                        text = task.title.ifBlank { stringResource(R.string.calendar_untitled_event) },
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
                                        ThingsTagPill(
                                            title = stringResource(
                                                R.string.quick_find_subtasks_count,
                                                doneCount,
                                                task.checklistItems.size
                                            )
                                        )
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
}

@Composable
private fun QuickFindFilterChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    hasDropdown: Boolean = false,
    isReset: Boolean = false,
    onClick: () -> Unit
) {
    val colors = ThingsTheme.colors
    val containerColor = when {
        isReset -> colors.deadlineUrgentRed.copy(alpha = 0.12f)
        active -> colors.thingsBlue.copy(alpha = 0.16f)
        else -> colors.tagPillBg.copy(alpha = 0.55f)
    }
    val contentColor = when {
        isReset -> colors.deadlineUrgentRed
        active -> colors.thingsBlue
        else -> colors.secondaryText
    }
    val borderColor = when {
        isReset -> colors.deadlineUrgentRed.copy(alpha = 0.35f)
        active -> colors.thingsBlue.copy(alpha = 0.45f)
        else -> colors.hairlineDivider
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (active || isReset) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (active) colors.thingsBlue else if (isReset) colors.deadlineUrgentRed else colors.primaryText,
                maxLines = 1
            )
            if (hasDropdown) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ThingsChromebookSidebar(
    viewModel: ThingsbookViewModel,
    navTarget: ActiveNavigationTarget,
    smartViewCounts: Map<SmartViewType, Int>,
    areas: List<Area>,
    projects: List<Project>,
    savedPerspectives: List<SavedPerspective> = emptyList(),
    showPerspectivesInSidebar: Boolean = false,
    allOpenTasks: List<TaskItem> = emptyList(),
    openTaskIds: Set<String> = emptySet(),
    todayDayStartMillis: Long = 0L,
    dragController: ProjectDragDropController? = null,
    onSelectSmartView: (SmartViewType) -> Unit,
    onSelectArea: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onSelectPerspective: (String) -> Unit = {},
    onCreateNewPerspective: () -> Unit = {},
    onEditPerspective: (SavedPerspective) -> Unit = {},
    onOpenAreaManager: () -> Unit,
    onOpenTagManager: () -> Unit = {},
    onCreateNewProject: (String?) -> Unit,
    onOpenWeeklyReview: () -> Unit = {},
    onOpenTemplatesAndImport: () -> Unit = {},
    onOpenExternalImportExport: (String?) -> Unit = {},
    onCloseDrawer: (() -> Unit)? = null
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val areasLabel = stringResource(R.string.entity_areas)
    val sidebarKeyPrefix = if (onCloseDrawer != null) "drawer" else "sidebar"
    val stalledStatuses = remember(projects) {
        StalledProjectDetector.detectStalledProjects(projects)
    }
    val stalledCount = stalledStatuses.size
    val perspectiveCounts = remember(savedPerspectives, allOpenTasks, openTaskIds, todayDayStartMillis) {
        savedPerspectives.associate { p ->
            p.id to filterTasksForPerspective(allOpenTasks, p, openTaskIds, todayDayStartMillis).size
        }
    }
    val collapsedAreaIds = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val defaultNewProjectAreaId = when (navTarget) {
        is ActiveNavigationTarget.AreaDetail -> navTarget.areaId
        is ActiveNavigationTarget.ProjectDetail -> projects.firstOrNull { it.id == navTarget.projectId }?.areaId ?: areas.firstOrNull()?.id
        else -> areas.firstOrNull()?.id
    }

    Column(
        modifier = Modifier
            .width(276.dp)
            .fillMaxHeight()
            .background(colors.sidebarBackground)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Clean Sidebar Brand Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
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
            if (onCloseDrawer != null) {
                ThingsHoverTooltip(text = stringResource(R.string.action_cancel)) {
                    IconButton(
                        onClick = onCloseDrawer,
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
            }
        }

        // 1. 2x2 Bento Focus Grid (Inbox, Today, Upcoming, Anytime)
        val bentoRows = listOf(
            listOf(SmartViewType.INBOX to 1, SmartViewType.TODAY to 2),
            listOf(SmartViewType.UPCOMING to 3, SmartViewType.ANYTIME to 4)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            bentoRows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    rowItems.forEach { (viewType, shortcutNum) ->
                        val selected = navTarget == ActiveNavigationTarget.SmartView(viewType)
                        val count = smartViewCounts[viewType] ?: 0
                        val viewLabel = stringResource(viewType.titleRes)
                        val bentoHoverSource = remember(viewType) { MutableInteractionSource() }
                        val isBentoHovered by bentoHoverSource.collectIsHoveredAsState()
                        ThingsHoverTooltip(
                            text = "$viewLabel (Ctrl+$shortcutNum)",
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (dragController != null) {
                                            Modifier.workspaceDropTarget(
                                                controller = dragController,
                                                key = "${sidebarKeyPrefix}_bento_${viewType.name}",
                                                destination = WorkspaceDragDropDestination.SmartView(viewType, viewLabel),
                                                cornerRadius = 14.dp,
                                                priority = 24
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                                    .hoverable(interactionSource = bentoHoverSource)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectSmartView(viewType)
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    selected -> colors.thingsBlueSoft
                                    isBentoHovered -> colors.cardSurface
                                    else -> colors.cardSurface.copy(alpha = 0.86f)
                                },
                                border = BorderStroke(
                                    width = if (selected) 1.2.dp else 0.75.dp,
                                    color = when {
                                        selected -> colors.thingsBlue.copy(alpha = if (isBentoHovered) 0.65f else 0.48f)
                                        isBentoHovered -> colors.thingsBlue.copy(alpha = 0.36f)
                                        else -> colors.hairlineDivider
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        SmartViewIcon(
                                            viewType = viewType,
                                            modifier = Modifier.size(19.dp),
                                            isSelected = selected || isBentoHovered
                                        )
                                        Text(
                                            text = if (count > 0) count.toString() else "",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (selected) colors.thingsBlue else colors.primaryText
                                        )
                                    }
                                    Text(
                                        text = viewLabel,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = if (viewLabel.length > 14) 11.2.sp else 12.5.sp,
                                            lineHeight = 14.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (selected) colors.thingsBlue else colors.primaryText
                                        ),
                                        maxLines = 2,
                                        softWrap = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Option A Compact 2-Row Micro-Bento Cards (Someday, Weekly Review — full-width labels, zero truncation)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            val somedaySelected = navTarget == ActiveNavigationTarget.SmartView(SmartViewType.SOMEDAY)
            val somedayCount = smartViewCounts[SmartViewType.SOMEDAY] ?: 0
            val somedayLabel = stringResource(SmartViewType.SOMEDAY.titleRes)
            val somedayHoverSource = remember { MutableInteractionSource() }
            val isSomedayHovered by somedayHoverSource.collectIsHoveredAsState()
            ThingsHoverTooltip(
                text = "$somedayLabel (Ctrl+5)",
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (dragController != null) {
                                Modifier.workspaceDropTarget(
                                    controller = dragController,
                                    key = "${sidebarKeyPrefix}_bento_SOMEDAY",
                                    destination = WorkspaceDragDropDestination.SmartView(SmartViewType.SOMEDAY, somedayLabel),
                                    cornerRadius = 12.dp,
                                    priority = 24
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .hoverable(interactionSource = somedayHoverSource)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectSmartView(SmartViewType.SOMEDAY)
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        somedaySelected -> colors.thingsBlueSoft
                        isSomedayHovered -> colors.cardSurface
                        else -> colors.cardSurface.copy(alpha = 0.78f)
                    },
                    border = BorderStroke(
                        width = if (somedaySelected) 1.1.dp else 0.75.dp,
                        color = when {
                            somedaySelected -> colors.thingsBlue.copy(alpha = if (isSomedayHovered) 0.62f else 0.45f)
                            isSomedayHovered -> colors.thingsBlue.copy(alpha = 0.36f)
                            else -> colors.hairlineDivider
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SmartViewIcon(
                                viewType = SmartViewType.SOMEDAY,
                                modifier = Modifier.size(15.dp),
                                isSelected = somedaySelected || isSomedayHovered
                            )
                            Text(
                                text = if (somedayCount > 0) somedayCount.toString() else "",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (somedaySelected) colors.thingsBlue else colors.secondaryText
                            )
                        }
                        Text(
                            text = somedayLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (somedayLabel.length > 13) 11.sp else 12.sp,
                                lineHeight = 13.5.sp,
                                fontWeight = if (somedaySelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (somedaySelected) colors.thingsBlue else colors.primaryText
                            ),
                            maxLines = 2,
                            softWrap = true
                        )
                    }
                }
            }

            val reviewFullTitle = stringResource(R.string.v2_weekly_review_title)
            val reviewShortLabel = stringResource(R.string.sidebar_review_short)
            val reviewHoverSource = remember { MutableInteractionSource() }
            val isReviewHovered by reviewHoverSource.collectIsHoveredAsState()
            ThingsHoverTooltip(
                text = "$reviewFullTitle (Ctrl+Shift+R)",
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .hoverable(interactionSource = reviewHoverSource)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenWeeklyReview()
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isReviewHovered) colors.cardSurface else colors.cardSurface.copy(alpha = 0.78f),
                    border = BorderStroke(
                        width = 0.75.dp,
                        color = if (isReviewHovered) colors.thingsBlue.copy(alpha = 0.36f) else colors.hairlineDivider
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = reviewFullTitle,
                                tint = if (stalledCount > 0) colors.thingsBlue else colors.secondaryText,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (stalledCount > 0) stalledCount.toString() else "",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (stalledCount > 0) colors.thingsBlue else colors.secondaryText
                            )
                        }
                        Text(
                            text = reviewShortLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (reviewShortLabel.length > 13) 11.sp else 12.sp,
                                lineHeight = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primaryText
                            ),
                            maxLines = 2,
                            softWrap = true
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
        Spacer(modifier = Modifier.height(2.dp))

        // Perspectives Soft Bento Card + Explicit Areas -> Projects Hierarchy
        val validAreaIds = remember(areas) { areas.mapTo(HashSet()) { it.id } }
        val unassignedProjects = projects.filter { proj -> proj.areaId == null || proj.areaId !in validAreaIds }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showPerspectivesInSidebar) {
                // 1. Self-Contained Soft Bento Perspectives Card (Dynamic Filter Views — never confused with Areas)
                item(key = "sidebar_perspectives_card") {
                    val newPerspTitle = stringResource(R.string.v30_perspective_new)
                    val perspectivesSectionLabel = stringResource(R.string.v30_perspectives_section).uppercase()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.cardSurface.copy(alpha = 0.78f),
                        border = BorderStroke(0.75.dp, colors.hairlineDivider)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (savedPerspectives.isEmpty()) {
                                            Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onCreateNewPerspective()
                                                }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(start = 4.dp, end = 2.dp, top = 1.dp, bottom = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesomeMosaic,
                                        contentDescription = null,
                                        tint = colors.eveningMoonIndigo,
                                        modifier = Modifier.size(13.5.dp)
                                    )
                                    Text(
                                        text = perspectivesSectionLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp,
                                            color = colors.secondaryText
                                        )
                                    )
                                    if (savedPerspectives.isNotEmpty()) {
                                        Text(
                                            text = savedPerspectives.size.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.tertiaryText
                                            )
                                        )
                                    }
                                }
                                ThingsHoverTooltip(text = newPerspTitle) {
                                    IconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onCreateNewPerspective()
                                        },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = newPerspTitle,
                                            tint = colors.thingsBlue,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            savedPerspectives.forEach { persp ->
                                val isPerspSelected = navTarget == ActiveNavigationTarget.PerspectiveDetail(persp.id)
                                val pCount = perspectiveCounts[persp.id] ?: 0
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isPerspSelected) colors.thingsBlueSoft else Color.Transparent)
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelectPerspective(persp.id)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesomeMosaic,
                                            contentDescription = persp.title,
                                            tint = if (isPerspSelected) colors.thingsBlue else colors.eveningMoonIndigo,
                                            modifier = Modifier.size(14.5.dp)
                                        )
                                        Text(
                                            text = persp.title,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isPerspSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (isPerspSelected) colors.thingsBlue else colors.primaryText
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (pCount > 0) {
                                            Text(
                                                text = pCount.toString(),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isPerspSelected) colors.thingsBlue else colors.secondaryText
                                            )
                                        }
                                        if (isPerspSelected) {
                                            IconButton(
                                                onClick = { onEditPerspective(persp) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = stringResource(R.string.v30_perspective_edit),
                                                    tint = colors.thingsBlue,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Unconditional structural divider separating dynamic Perspectives from Workspace Areas
                item(key = "sidebar_perspectives_divider") {
                    HorizontalDivider(
                        color = colors.hairlineDivider,
                        thickness = 0.75.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // 2. Explicit Areas (BEREICHE) Section Header
            item(key = "sidebar_areas_header") {
                val manageAreasTitle = stringResource(R.string.areas_manage_title)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
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
                            tint = colors.secondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = areasLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                color = colors.secondaryText
                            )
                        )
                        Text(
                            text = "${areas.size}/${ThingsRepository.MAX_AREAS}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = colors.tertiaryText
                            )
                        )
                    }
                    ThingsHoverTooltip(text = manageAreasTitle) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenAreaManager()
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = manageAreasTitle,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(14.5.dp)
                            )
                        }
                    }
                }
            }

            items(areas, key = { "sidebar_area_${it.id}" }) { area ->
                val isAreaSelected = navTarget == ActiveNavigationTarget.AreaDetail(area.id)
                val areaProjects = projects.filter { it.areaId == area.id }
                val isCollapsed = collapsedAreaIds[area.id] == true
                var showSidebarAreaMenu by remember(area.id) { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (dragController != null) {
                                        Modifier.workspaceDropTarget(
                                            controller = dragController,
                                            key = "${sidebarKeyPrefix}_area_${area.id}",
                                            destination = WorkspaceDragDropDestination.AreaTarget(area.id, area.title),
                                            cornerRadius = 50.dp,
                                            priority = 24
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .pointerInput(area.id) {
                                    awaitPointerEventScope {
                                        var wasSecondary = false
                                        while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                                            val rightClick = (ev.type == PointerEventType.Press && ev.buttons.isSecondaryPressed) ||
                                                (wasSecondary && !ev.buttons.isSecondaryPressed)
                                            if (rightClick) {
                                                wasSecondary = false
                                                ev.changes.forEach { it.consume() }
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showSidebarAreaMenu = true
                                                continue
                                            }
                                            wasSecondary = ev.buttons.isSecondaryPressed
                                        }
                                    }
                                }
                                .clip(RoundedCornerShape(50))
                                .background(if (isAreaSelected) colors.thingsBlueSoft else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectArea(area.id)
                                    },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showSidebarAreaMenu = true
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { collapsedAreaIds[area.id] = !isCollapsed },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                        contentDescription = area.title,
                                        tint = colors.tertiaryText,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = area.title,
                                    tint = if (isAreaSelected) colors.thingsBlue else colors.secondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = area.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isAreaSelected) colors.thingsBlue else colors.primaryText
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                ThingsHoverTooltip(
                                    text = stringResource(R.string.sidebar_new_project_in_area, area.title)
                                ) {
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
                                ThingsHoverTooltip(text = stringResource(R.string.action_more_options)) {
                                    IconButton(
                                        onClick = { showSidebarAreaMenu = true },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreHoriz,
                                            contentDescription = stringResource(R.string.action_more_options),
                                            tint = colors.tertiaryText,
                                            modifier = Modifier.size(13.5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showSidebarAreaMenu,
                            onDismissRequest = { showSidebarAreaMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sidebar_new_project_in_area, area.title)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = null,
                                        tint = colors.thingsBlue,
                                        modifier = Modifier.size(17.dp)
                                    )
                                },
                                onClick = {
                                    showSidebarAreaMenu = false
                                    onCreateNewProject(area.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.v30_external_import_export_tab)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.SystemUpdateAlt,
                                        contentDescription = null,
                                        tint = colors.thingsBlue,
                                        modifier = Modifier.size(17.dp)
                                    )
                                },
                                onClick = {
                                    showSidebarAreaMenu = false
                                    onOpenExternalImportExport(area.id)
                                }
                            )
                        }
                    }

                    // Nested Projects inside this Area
                    if (!isCollapsed) {
                        areaProjects.forEach { project ->
                            val selected = navTarget == ActiveNavigationTarget.ProjectDetail(project.id)
                            val projStatus = StalledProjectDetector.evaluateProject(project)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp)
                                    .then(
                                        if (dragController != null) {
                                            Modifier.workspaceDropTarget(
                                                controller = dragController,
                                                key = "${sidebarKeyPrefix}_proj_${project.id}",
                                                destination = WorkspaceDragDropDestination.ProjectTarget(project.id, project.title),
                                                cornerRadius = 12.dp,
                                                priority = 24
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) colors.thingsBlueSoft else Color.Transparent)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectProject(project.id)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ThingsHoverTooltip(
                                    text = project.title,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = project.title,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selected) colors.thingsBlue else colors.primaryText
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (projStatus?.hasNoNextAction == true) {
                                        Icon(
                                            imageVector = Icons.Outlined.WarningAmber,
                                            contentDescription = stringResource(R.string.v2_stalled_project_badge),
                                            tint = colors.somedayWarmAmber,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    } else if (projStatus?.isUnreviewed == true) {
                                        Icon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = stringResource(R.string.v2_requires_review_badge),
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(12.dp)
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
                                    ProjectProgressRing(progress = project.progress, size = 16.dp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Legacy / Unassigned Standalone Projects with Explicit Section Header
            if (unassignedProjects.isNotEmpty()) {
                item(key = "sidebar_unassigned_projects_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HorizontalDivider(
                            color = colors.hairlineDivider,
                            thickness = 0.75.dp,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.entity_projects).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    color = colors.secondaryText
                                )
                            )
                            Text(
                                text = unassignedProjects.size.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = colors.tertiaryText
                                )
                            )
                        }
                    }
                }
            }
            items(unassignedProjects, key = { "sidebar_proj_unassigned_${it.id}" }) { project ->
                val selected = navTarget == ActiveNavigationTarget.ProjectDetail(project.id)
                val projStatus = StalledProjectDetector.evaluateProject(project)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (dragController != null) {
                                Modifier.workspaceDropTarget(
                                    controller = dragController,
                                    key = "${sidebarKeyPrefix}_proj_unassigned_${project.id}",
                                    destination = WorkspaceDragDropDestination.ProjectTarget(project.id, project.title),
                                    cornerRadius = 12.dp,
                                    priority = 24
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) colors.thingsBlueSoft else Color.Transparent)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectProject(project.id)
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ThingsHoverTooltip(
                        text = project.title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = project.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) colors.thingsBlue else colors.primaryText
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (projStatus?.hasNoNextAction == true) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = stringResource(R.string.v2_stalled_project_badge),
                                tint = colors.somedayWarmAmber,
                                modifier = Modifier.size(12.dp)
                            )
                        } else if (projStatus?.isUnreviewed == true) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = stringResource(R.string.v2_requires_review_badge),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(12.dp)
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
                        ProjectProgressRing(progress = project.progress, size = 16.dp)
                    }
                }
            }
        }

        // 2-Row Bottom Sidebar Dock: Row 1 = Full-Width "+ New Project ▾", Row 2 = Spaced Utility Dock
        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
        var showNewProjectFooterMenu by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val newProjectBtnHoverSource = remember { MutableInteractionSource() }
            val isNewProjectBtnHovered by newProjectBtnHoverSource.collectIsHoveredAsState()
            Box(modifier = Modifier.fillMaxWidth()) {
                ThingsHoverTooltip(text = "${stringResource(R.string.entity_new_project)} (Ctrl+Shift+N)") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .hoverable(interactionSource = newProjectBtnHoverSource)
                            .clickable { showNewProjectFooterMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.thingsBlueSoft.copy(alpha = if (isNewProjectBtnHovered) 0.98f else 0.78f),
                        border = BorderStroke(
                            0.75.dp,
                            colors.thingsBlue.copy(alpha = if (isNewProjectBtnHovered) 0.48f else 0.26f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = colors.thingsBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = stringResource(R.string.entity_new_project),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.thingsBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = colors.thingsBlue.copy(alpha = 0.85f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = showNewProjectFooterMenu,
                    onDismissRequest = { showNewProjectFooterMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.entity_new_project)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showNewProjectFooterMenu = false
                            onCreateNewProject(defaultNewProjectAreaId)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.v2_templates_title)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        onClick = {
                            showNewProjectFooterMenu = false
                            onOpenTemplatesAndImport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.v30_external_import_export_tab)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.SystemUpdateAlt,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        onClick = {
                            showNewProjectFooterMenu = false
                            onOpenExternalImportExport(defaultNewProjectAreaId)
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val logbookSelected = navTarget == ActiveNavigationTarget.SmartView(SmartViewType.LOGBOOK)
                val logbookLabel = stringResource(SmartViewType.LOGBOOK.titleRes)
                ThingsHoverTooltip(text = "$logbookLabel (Ctrl+6)") {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectSmartView(SmartViewType.LOGBOOK)
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .then(
                                if (dragController != null) {
                                    Modifier.workspaceDropTarget(
                                        controller = dragController,
                                        key = "${sidebarKeyPrefix}_dock_LOGBOOK",
                                        destination = WorkspaceDragDropDestination.SmartView(SmartViewType.LOGBOOK, logbookLabel),
                                        cornerRadius = 8.dp,
                                        priority = 24
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (logbookSelected) colors.thingsBlueSoft else Color.Transparent)
                    ) {
                        SmartViewIcon(
                            viewType = SmartViewType.LOGBOOK,
                            modifier = Modifier.size(15.dp),
                            isSelected = logbookSelected
                        )
                    }
                }

                val trashSelected = navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TRASH)
                val trashLabel = stringResource(SmartViewType.TRASH.titleRes)
                ThingsHoverTooltip(text = "$trashLabel (Ctrl+7)") {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectSmartView(SmartViewType.TRASH)
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .then(
                                if (dragController != null) {
                                    Modifier.workspaceDropTarget(
                                        controller = dragController,
                                        key = "${sidebarKeyPrefix}_dock_TRASH",
                                        destination = WorkspaceDragDropDestination.SmartView(SmartViewType.TRASH, trashLabel),
                                        cornerRadius = 8.dp,
                                        priority = 24
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (trashSelected) colors.thingsBlueSoft else Color.Transparent)
                    ) {
                        SmartViewIcon(
                            viewType = SmartViewType.TRASH,
                            modifier = Modifier.size(15.dp),
                            isSelected = trashSelected
                        )
                    }
                }

                ThingsHoverTooltip(
                    text = stringResource(
                        R.string.areas_manage_count_tooltip,
                        areas.size,
                        ThingsRepository.MAX_AREAS
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpenAreaManager)
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = areasLabel,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${areas.size}/${ThingsRepository.MAX_AREAS}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondaryText
                        )
                    }
                }

                val categoriesLabel = stringResource(R.string.topbar_tooltip_tags)
                ThingsHoverTooltip(text = categoriesLabel) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenTagManager()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GridView,
                            contentDescription = categoriesLabel,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // App Version displayed at the bottom-left of the Sidebar (with Sky Light Blue 3.0 ALPHA badge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
        ) {
            Text(
                text = "Thingsbook v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = colors.tertiaryText
            )
            if (BuildConfig.VERSION_NAME.contains("-")) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.thingsBlueSoft
                ) {
                    Text(
                        text = stringResource(R.string.v2_beta_badge),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = colors.thingsBlue,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                    )
                }
            }
        }
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

    (whenLocal?.takeIf { !it.isBefore(todayDate) })
        ?: (deadlineLocal?.takeIf { !it.isBefore(todayDate) })
        ?: todayDate.plusDays(1)
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

@Composable
private fun ThingsCalendarEventsBlock(
    events: List<CalendarEventItem>,
    locale: Locale,
    compactTopPadding: Boolean = false
) {
    if (events.isEmpty()) return
    val colors = ThingsTheme.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val allDayText = stringResource(R.string.calendar_all_day)
    val untitledText = stringResource(R.string.calendar_untitled_event)
    val zone = ZoneId.systemDefault()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = if (compactTopPadding) 2.dp else 4.dp,
                bottom = 8.dp
            )
            .border(
                width = 0.75.dp,
                color = colors.hairlineDivider.copy(alpha = 0.85f),
                shape = RoundedCornerShape(11.dp)
            ),
        shape = RoundedCornerShape(11.dp),
        color = colors.tagPillBg.copy(alpha = 0.52f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            events.forEach { event ->
                val calColor = Color(event.colorArgb)
                val timeLabel = remember(event.startMillis, event.endMillis, event.isAllDay, locale, allDayText) {
                    if (event.isAllDay) {
                        allDayText
                    } else {
                        formatCalendarEventTimeRange(
                            startMillis = event.startMillis,
                            endMillis = event.endMillis,
                            zone = zone,
                            locale = locale
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            openCalendarEventInDefaultApp(context, event)
                        }
                        .padding(horizontal = 6.dp, vertical = 3.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Calendar Color Indicator Bar (Things 3 style)
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(15.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(calColor)
                    )

                    // Time or All-Day Badge
                    if (event.isAllDay) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(calColor.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                text = timeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.secondaryText,
                                maxLines = 1
                            )
                        }
                    } else {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colors.secondaryText,
                            maxLines = 1
                        )
                    }

                    // Event Title (tapping opens in default Calendar app)
                    Text(
                        text = event.title.ifBlank { untitledText },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (event.calendarDisplayName.isNotBlank()) {
                        Text(
                            text = event.calendarDisplayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = colors.tertiaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 110.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun openCalendarEventInDefaultApp(
    context: android.content.Context,
    event: CalendarEventItem
) {
    val openedSpecificEvent = if (event.eventId > 0L) {
        runCatching {
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.CalendarContract.Events.CONTENT_URI,
                event.eventId
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = uri
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    } else {
        false
    }

    if (!openedSpecificEvent) {
        runCatching {
            val builder = android.provider.CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
            android.content.ContentUris.appendId(builder, event.startMillis)
            val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = builder.build()
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        }
    }
}

private fun formatCalendarEventTimeRange(
    startMillis: Long,
    endMillis: Long,
    zone: ZoneId,
    locale: Locale
): String = runCatching {
    val formatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    val startText = Instant.ofEpochMilli(startMillis).atZone(zone).format(formatter)
    if (endMillis > startMillis) {
        val endText = Instant.ofEpochMilli(endMillis).atZone(zone).format(formatter)
        if (endText != startText) "$startText–$endText" else startText
    } else {
        startText
    }
}.getOrDefault("")



