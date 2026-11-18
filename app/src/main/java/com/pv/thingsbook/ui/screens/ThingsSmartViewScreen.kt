package com.pv.thingsbook.ui.screens

import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import com.pv.thingsbook.ui.components.MagicPlusDropMode
import com.pv.thingsbook.ui.components.MagicPlusPositionalDropHandler
import com.pv.thingsbook.ui.components.ProjectDragDropController
import com.pv.thingsbook.ui.components.ProjectDragKind
import com.pv.thingsbook.ui.components.ProjectDragSession
import com.pv.thingsbook.ui.components.ProjectListEntry
import com.pv.thingsbook.ui.components.ProjectTaskDragHandle
import com.pv.thingsbook.ui.components.ProjectDropIndicatorOverlay
import com.pv.thingsbook.ui.components.ProjectDragAutoScrollEffect
import com.pv.thingsbook.ui.components.projectDropSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdateAlt
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
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
import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.QuickFindDateFilter
import com.pv.thingsbook.domain.model.QuickFindEntityScope
import com.pv.thingsbook.domain.model.QuickFindFilter
import com.pv.thingsbook.domain.model.RecurrenceMode
import com.pv.thingsbook.domain.model.RecurrenceUnit
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.StalledProjectDetector
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.model.TimeblockScheduleUtils
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
import com.pv.thingsbook.ui.components.ProjectProgressRing
import com.pv.thingsbook.ui.components.QuickCreateAreaDialog
import com.pv.thingsbook.ui.components.SampleTasksAccountMergeDialog
import com.pv.thingsbook.ui.components.SmartViewIcon
import com.pv.thingsbook.ui.components.SmartViewType
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
import com.pv.thingsbook.ui.components.processCapturedPhotoBitmap
import com.pv.thingsbook.ui.components.processPickedAttachmentUri
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TreeSet

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
                if ((appCtx as? android.app.Activity)?.isChangingConfigurations != true) {
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
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsState()

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

    var magicDragState by remember { mutableStateOf(MagicPlusDragState()) }
    val projectDragController = remember { ProjectDragDropController() }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var projectDialogPreselectedAreaId by remember { mutableStateOf<String?>(null) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showEmptyTrashConfirmDialog by remember { mutableStateOf(false) }
    var externalDialogTriggerToken by remember { mutableIntStateOf(0) }
    var externalDialogTriggerType by remember { mutableStateOf<String?>(null) }

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
    var isMobileDrawerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isWideScreen) {
        if (isWideScreen && isMobileDrawerOpen) {
            isMobileDrawerOpen = false
        }
    }

    BackHandler(enabled = !isWideScreen && isMobileDrawerOpen) {
        isMobileDrawerOpen = false
    }

    val smartViewCounts = mapOf(
        SmartViewType.INBOX to inboxTasks.size,
        SmartViewType.TODAY to (todayTasks.size + eveningTasks.size),
        SmartViewType.UPCOMING to upcomingTasks.size,
        SmartViewType.ANYTIME to anytimeTasks.size,
        SmartViewType.SOMEDAY to somedayTasks.size,
        SmartViewType.LOGBOOK to logbookTasks.size,
        SmartViewType.TRASH to (deletedTasks.size + deletedProjects.size)
    )

    val triggerCreateTaskShortcut by rememberUpdatedState {
        showCreateProjectDialog = false
        editingProject = null
        showBatchMoveDialog = false
        isMobileDrawerOpen = false
        viewModel.setMagicPlusMenuOpen(false)
        viewModel.createTaskAtPosition(primaryTasks, 0, "", forceTopUngroupedInProject = true)
    }

    val triggerCreateProjectShortcut by rememberUpdatedState {
        showBatchMoveDialog = false
        isMobileDrawerOpen = false
        viewModel.setMagicPlusMenuOpen(false)
        projectDialogPreselectedAreaId = currentArea?.id ?: currentProject?.areaId
        editingProject = null
        showCreateProjectDialog = true
    }

    val context = LocalContext.current
    val currentHardwareShortcutHandler by rememberUpdatedState<(android.view.KeyEvent) -> Boolean> { nativeEvent ->
        if (biometricAppLockEnabled && !isAppUnlocked) return@rememberUpdatedState false
        val ctrlOrMeta = nativeEvent.isCtrlPressed || nativeEvent.isMetaPressed
        when {
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
                expandedTaskId == null && !showCreateProjectDialog && !showBatchMoveDialog -> {
                viewModel.selectAllTasks(allVisibleTaskIds)
                true
            }
            nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE -> when {
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
        val activity = context as? MainActivity
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
                    val ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed
                    when {
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
                        event.key == Key.Escape -> when {
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
            // Tap outside an expanded card collapses it smoothly
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!(biometricAppLockEnabled && !isAppUnlocked) && expandedTaskId != null) {
                        viewModel.collapseExpandedTask()
                    }
                }
            )
            // Swipe down gesture on any list opens Quick Find inline immediately
            .pointerInput(biometricAppLockEnabled, isAppUnlocked) {
                if (biometricAppLockEnabled && !isAppUnlocked) return@pointerInput
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
                    },
                    onOpenWeeklyReview = { viewModel.openWeeklyReviewModal() },
                    onOpenTemplatesAndImport = {
                        viewModel.openTemplatesModal(
                            initialTab = 0,
                            defaultAreaId = currentArea?.id ?: currentProject?.areaId
                        )
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
                        isMobileDrawerOpen = isMobileDrawerOpen,
                        onToggleMobileDrawer = { isMobileDrawerOpen = !isMobileDrawerOpen },
                        isQuickFindOpen = isQuickFindOpen,
                        areas = areas,
                        activeProjects = activeProjects,
                        activeLocale = activeLocale
                    )

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

                    // On Foldables & Googlebook (>= 700dp), show horizontal strip when the permanent sidebar is collapsed
                    if (isWideScreen && !showSidebar) {
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

                    // View Title Header (Smart View, Project with Target Date / Deadline, or Area like Work / Home)
                    ViewTitleHeader(
                        navTarget = navTarget,
                        currentProject = currentProject,
                        currentArea = currentArea,
                        areas = areas,
                        projects = activeProjects,
                        projectAttachments = projectAttachments,
                        locale = activeLocale,
                        trashItemCount = deletedTasks.size + deletedProjects.size,
                        isTodayTimelineMode = isTodayTimelineMode,
                        onToggleTodayTimelineMode = { viewModel.setTodayTimelineMode(it) },
                        onSaveProjectAsTemplate = { pId, toastMsg ->
                            viewModel.saveProjectAsTemplate(pId, toastMsg)
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
        }

        // App Version Badge at bottom-left when Sidebar is hidden or on phones (hidden when soft keyboard is open)
        val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        if (!showSidebar && !isKeyboardOpen) {
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

        // 5-Second Undo Snackbar Banner (also triggered by Ctrl+Z)
        ThingsUndoSnackbarBanner(
            message = undoSnackbarMessage,
            onUndo = { viewModel.performUndo() },
            onDismiss = { viewModel.dismissUndoSnackbar() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (selectedTaskIds.isNotEmpty()) 76.dp else 18.dp)
        )

        // v2 Celebration Toast / Snackbar (Weekly Review completed, Template saved, etc.)
        ThingsCelebrationSnackbarBanner(
            message = celebrationToastMessage,
            onDismiss = { viewModel.dismissCelebrationToast() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = when {
                        !undoSnackbarMessage.isNullOrBlank() && selectedTaskIds.isNotEmpty() -> 132.dp
                        !undoSnackbarMessage.isNullOrBlank() || selectedTaskIds.isNotEmpty() -> 76.dp
                        else -> 22.dp
                    }
                )
        )

        // Expandable Context-Sensitive "Magic Plus" Menu + Drag & Drop FAB (bottom-right)
        MagicPlusOverlaySection(
            viewModel = viewModel,
            navTarget = navTarget,
            currentArea = currentArea,
            primaryTasks = primaryTasks,
            projectHeadings = projectHeadings,
            areasCount = areas.size,
            projectDragController = projectDragController,
            onDragStateChanged = { state -> magicDragState = state },
            onRequestCreateProject = { areaId ->
                projectDialogPreselectedAreaId = areaId
                editingProject = null
                showCreateProjectDialog = true
            }
        )

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
                        onOpenAreaManager = {
                            isMobileDrawerOpen = false
                            viewModel.setAreaManagerOpen(true)
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

@Composable
private fun MagicPlusOverlaySection(
    viewModel: ThingsbookViewModel,
    navTarget: ActiveNavigationTarget,
    currentArea: Area?,
    primaryTasks: List<TaskItem>,
    projectHeadings: List<Heading>,
    areasCount: Int,
    projectDragController: ProjectDragDropController,
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
                .padding(end = 24.dp, bottom = 28.dp),
            positionalDropHandler = positionalHandler
        )
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
            val taskId = session.draggedTaskId ?: return
            viewModel.moveTaskWithinProject(taskId, target.headingId, section, target.indexInSection)
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
    isWideScreen: Boolean,
    isSidebarVisible: Boolean,
    isMobileDrawerOpen: Boolean,
    onToggleMobileDrawer: () -> Unit,
    isQuickFindOpen: Boolean,
    areas: List<Area>,
    activeProjects: List<Project>,
    activeLocale: Locale
) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val dailyDigestConfig by viewModel.dailyDigestConfig.collectAsState()
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
    var showLanguageMenu by remember { mutableStateOf(false) }

    val showSidebar = isWideScreen && isSidebarVisible

    // Top Utility & Navigation Bar (Sidebar/Hamburger Toggle, Thingsbook Symbol when sidebar hidden, Quick Find, Voice 🎙️, Weekly Review, Gemini AI ✨, Areas 📦, Tags 🏷️, Notifications 🔔, Language, Cloud Sync, Settings ⚙️)
    TopThingsbookBar(
        isWideScreen = isWideScreen,
        isSidebarVisible = if (isWideScreen) isSidebarVisible else isMobileDrawerOpen,
        showBrandSymbol = !showSidebar,
        isQuickFindOpen = isQuickFindOpen,
        syncStatus = syncStatus,
        dailyDigestEnabled = dailyDigestConfig.enabled,
        showLanguageMenu = showLanguageMenu,
        onToggleSidebar = {
            if (isWideScreen) {
                viewModel.toggleSidebarVisible()
            } else {
                onToggleMobileDrawer()
            }
        },
        onSelectTodayView = { viewModel.selectSmartView(SmartViewType.TODAY) },
        onToggleLanguageMenu = { showLanguageMenu = it },
        onSelectLanguage = { tag ->
            showLanguageMenu = false
            viewModel.switchAppLanguage(tag)
        },
        onOpenQuickFind = { viewModel.setQuickFindOpen(!isQuickFindOpen) },
        onOpenVoiceCapture = { viewModel.openVoiceQuickCapture() },
        onOpenWeeklyReview = { viewModel.openWeeklyReviewModal() },
        onOpenGeminiHub = { viewModel.setGeminiModalOpen(true) },
        onOpenAreaManager = { viewModel.setAreaManagerOpen(true) },
        onOpenTagManager = { viewModel.setTagManagerOpen(true) },
        onOpenNotificationSettings = { viewModel.setNotificationSettingsOpen(true) },
        onManualSync = { viewModel.setAuthModalOpen(true) },
        onOpenSettings = { viewModel.setSettingsModalOpen(true) }
    )

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
    activeLocale: Locale,
    externalDialogTriggerToken: Int = 0,
    externalDialogTriggerType: String? = null,
    onRequestEnsureVisible: ((Boolean) -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentEpochDay by viewModel.currentEpochDay.collectAsState()
    val navTarget by viewModel.navigationTarget.collectAsState()
    val isTodayView = navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY)
    androidx.compose.runtime.key(currentEpochDay) {
        ExpandableTaskCard(
            modifier = modifier,
            collapsedTrailingContent = dragHandle,
            onRequestEnsureVisible = onRequestEnsureVisible,
            task = task,
            allTags = allTags,
            allAreas = areas,
            allProjects = activeProjects,
            allHeadings = allHeadings,
            isExpanded = expandedTaskId == task.id,
            isTodayView = isTodayView,
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
            onQuickThisEveningAndClose = { latestTitle, latestNotes ->
                viewModel.quickMoveTaskToThisEveningAndCollapse(task, latestTitle, latestNotes)
            },
            onScheduleWithReminderChange = { s, w, r ->
                viewModel.updateTaskScheduleWithReminder(task, s, w, r)
            },
            onDeadlineChange = { dl -> viewModel.updateTaskDeadline(task, dl) },
            onRecurrenceChange = { rule -> viewModel.updateTaskRecurrence(task.id, rule) },
            onUpdateTimeblock = { s, d -> viewModel.updateTaskTimeblock(task.id, s, d) },
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
            externalDialogTriggerToken = if (expandedTaskId == task.id) externalDialogTriggerToken else 0,
            externalDialogTriggerType = if (expandedTaskId == task.id) externalDialogTriggerType else null,
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
    val collapsedHeadingIds by viewModel.collapsedHeadingIds.collectAsState()
    val allHeadings by viewModel.allHeadings.collectAsState()
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

    if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY) &&
        isTodayTimelineMode &&
        !isQuickFindOpen
    ) {
        val combinedTodayTasks = remember(primaryTasks, filteredEveningTasks) {
            (primaryTasks + filteredEveningTasks).distinctBy { it.id }
        }
        ThingsTodayTimelineSection(
            todayTasks = combinedTodayTasks,
            todayCalendarEvents = if (calendarEventsEnabled) todayCalendarEvents else emptyList(),
            onCompleteTask = { taskId -> viewModel.updateTaskStatus(taskId, TaskStatus.COMPLETED) },
            onUpdateTimeblock = { taskId, startMins, durMins ->
                viewModel.updateTaskTimeblock(taskId, startMins, durMins)
            },
            onShiftTimeblock = { taskId, deltaMins ->
                viewModel.shiftTaskTimeblock(taskId, deltaMins)
            },
            onStartFocusTimer = { targetTask ->
                viewModel.startFocusTimer(targetTask.id, targetTask.title, targetTask.durationMinutes)
            },
            onOpenTask = { taskId ->
                viewModel.setTodayTimelineMode(false)
                viewModel.toggleTaskExpansion(taskId)
            },
            modifier = modifier.imePadding()
        )
        return
    }

    val navScopeKey = navTarget.scrollScopeKey()
    val listState = remember(navScopeKey) {
        LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
    }
    var focusScrollRequestTick by remember { mutableIntStateOf(0) }
    var preferBottomForExpandedTask by remember(expandedTaskId) { mutableStateOf(false) }
    val lastKnownTaskIndices = remember(navScopeKey) { HashMap<String, Int>() }
    val lastKnownTaskBounds = remember(navScopeKey) { HashMap<String, Pair<Int, Int>>() }

    LaunchedEffect(navScopeKey) {
        if (navTarget.shouldResetScrollToTop(expandedTaskId)) {
            runCatching { listState.scrollToItem(0, 0) }
        }
    }

    val handleEnsureVisibleRequest: (Boolean) -> Unit = remember {
        { preferBottom ->
            preferBottomForExpandedTask = preferBottom
            focusScrollRequestTick++
        }
    }

    // Project sections as displayed: tasks without (valid) heading first, then one section per heading
    val projectSections: Map<String?, List<TaskItem>> = remember(isProjectView, primaryTasks, projectHeadings) {
        if (!isProjectView) {
            emptyMap()
        } else {
            val headingIdSet = projectHeadings.mapTo(HashSet()) { it.id }
            val result = LinkedHashMap<String?, List<TaskItem>>()
            result[null] = primaryTasks.filter { it.headingId == null || it.headingId !in headingIdSet }
            projectHeadings.forEach { h -> result[h.id] = primaryTasks.filter { it.headingId == h.id } }
            result
        }
    }

    // Register list geometry semantics for position-based Drag & Drop (Magic Plus + task handles)
    // and track latest visible task item indices/bounds for keyboard-aware auto-scrolling
    SideEffect {
        listState.layoutInfo.visibleItemsInfo.forEach { info ->
            val keyStr = info.key?.toString() ?: return@forEach
            if (
                keyStr.startsWith("today_calendar_") ||
                keyStr.startsWith("upcoming_cal_events_") ||
                keyStr.startsWith("upcoming_day_header_") ||
                keyStr.startsWith("top_scroll_anchor_") ||
                keyStr.startsWith("trailing_magic_plus_gap")
            ) return@forEach
            val resolvedId = when {
                keyStr.startsWith("ungrouped_") -> keyStr.removePrefix("ungrouped_")
                keyStr.startsWith("upcoming_") -> keyStr.substringAfter("_").substringAfter("_")
                else -> keyStr
            }
            lastKnownTaskIndices[resolvedId] = info.index
            lastKnownTaskBounds[resolvedId] = info.offset to (info.offset + info.size)
        }
        projectDragController.isProjectListActive = isProjectView
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
            projectDragController.cancel()
            projectDragController.isProjectListActive = false
        }
    }
    LaunchedEffect(isProjectView) {
        if (!isProjectView) projectDragController.cancel()
    }

    suspend fun ensureExpandedTaskVisibleAboveKeyboard(targetId: String, animate: Boolean) {
        val topMarginPx = with(density) { 12.dp.roundToPx() }
        val bottomMarginPx = with(density) { 20.dp.roundToPx() }

        var itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            matchesTaskItemKey(it.key, targetId)
        }

        if (itemInfo == null) {
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            val safeBottom = (viewportEnd - bottomMarginPx).coerceAtLeast(topMarginPx + 1)
            val lastBounds = lastKnownTaskBounds[targetId]
            val knownIdx = lastKnownTaskIndices[targetId]
            if (lastBounds != null && lastBounds.second > safeBottom) {
                val scrollNeeded = (lastBounds.second - safeBottom).toFloat().coerceAtLeast(1f)
                runCatching { listState.scrollBy(scrollNeeded) }
            } else if (knownIdx != null) {
                runCatching { listState.scrollToItem(knownIdx) }
            } else if (primaryTasks.firstOrNull()?.id == targetId) {
                runCatching { listState.scrollToItem(0) }
            }
            itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                matchesTaskItemKey(it.key, targetId)
            } ?: return
        }

        val viewportStart = listState.layoutInfo.viewportStartOffset
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val safeTop = viewportStart + topMarginPx
        val safeBottom = (viewportEnd - bottomMarginPx).coerceAtLeast(safeTop + 1)
        val itemTop = itemInfo.offset
        val itemBottom = itemInfo.offset + itemInfo.size

        val scrollDelta = when {
            itemBottom > safeBottom -> {
                val neededDown = (itemBottom - safeBottom).toFloat()
                if (preferBottomForExpandedTask) {
                    neededDown
                } else {
                    val maxWithoutHidingTitle = (itemTop - safeTop).toFloat().coerceAtLeast(0f)
                    neededDown.coerceAtMost(maxWithoutHidingTitle)
                }
            }
            itemTop < safeTop -> (itemTop - safeTop).toFloat()
            else -> 0f
        }

        if (kotlin.math.abs(scrollDelta) > 1.5f) {
            runCatching {
                if (animate) {
                    listState.animateScrollBy(
                        value = scrollDelta,
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f)
                    )
                } else {
                    listState.scrollBy(scrollDelta)
                }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                visibleItems.forEach { info ->
                    val keyStr = info.key?.toString() ?: return@forEach
                    if (
                        keyStr.startsWith("today_calendar_") ||
                        keyStr.startsWith("upcoming_cal_events_") ||
                        keyStr.startsWith("upcoming_day_header_") ||
                        keyStr.startsWith("top_scroll_anchor_") ||
                        keyStr.startsWith("trailing_magic_plus_gap")
                    ) return@forEach
                    val resolvedId = when {
                        keyStr.startsWith("ungrouped_") -> keyStr.removePrefix("ungrouped_")
                        keyStr.startsWith("upcoming_") -> keyStr.substringAfter("_").substringAfter("_")
                        else -> keyStr
                    }
                    lastKnownTaskIndices[resolvedId] = info.index
                    lastKnownTaskBounds[resolvedId] = info.offset to (info.offset + info.size)
                }
            }
    }

    LaunchedEffect(expandedTaskId, focusScrollRequestTick, primaryTasks.size) {
        val targetId = expandedTaskId ?: return@LaunchedEffect
        kotlinx.coroutines.delay(16)
        ensureExpandedTaskVisibleAboveKeyboard(targetId, animate = true)
        kotlinx.coroutines.delay(110)
        ensureExpandedTaskVisibleAboveKeyboard(targetId, animate = true)
        kotlinx.coroutines.delay(150)
        ensureExpandedTaskVisibleAboveKeyboard(targetId, animate = true)
        kotlinx.coroutines.delay(140)
        ensureExpandedTaskVisibleAboveKeyboard(targetId, animate = true)
    }

    LaunchedEffect(expandedTaskId) {
        val targetId = expandedTaskId ?: return@LaunchedEffect
        var prevImeBottom = 0
        var prevViewportEnd = Int.MAX_VALUE
        var prevItemSize = 0
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val imePx = imeInsets.getBottom(density)
            val viewportEnd = layoutInfo.viewportEndOffset
            val curSize = layoutInfo.visibleItemsInfo
                .firstOrNull { matchesTaskItemKey(it.key, targetId) }
                ?.size ?: 0
            Triple(imePx, viewportEnd, curSize)
        }.collect { (imePx, viewportEnd, curSize) ->
            val imeGrew = imePx > prevImeBottom
            val viewportShrunk = viewportEnd in 1 until prevViewportEnd
            val sizeGrew = curSize > prevItemSize && prevItemSize > 0
            prevImeBottom = imePx
            if (viewportEnd > 0) prevViewportEnd = viewportEnd
            if (curSize > 0) prevItemSize = curSize
            if ((imeGrew || viewportShrunk || sizeGrew) && !listState.isScrollInProgress) {
                ensureExpandedTaskVisibleAboveKeyboard(targetId, animate = false)
            }
        }
    }

    ProjectDragAutoScrollEffect(controller = projectDragController, listState = listState)

    val draggedTaskId = projectDragController.session?.draggedTaskId
    val taskDragHandleFor: (TaskItem) -> (@Composable () -> Unit)? = { task ->
        if (isProjectView && task.status == TaskStatus.OPEN && selectedTaskIds.isEmpty()) {
            { ProjectTaskDragHandle(task = task, controller = projectDragController) }
        } else {
            null
        }
    }

    Box(
        modifier = modifier.then(
            if (isProjectView) {
                Modifier.projectDropSurface(projectDragController) { session ->
                    applyProjectDrop(viewModel, session, projectDragController)
                }
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
        contentPadding = PaddingValues(bottom = if (isImeVisible) 28.dp else 110.dp)
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
                            .padding(horizontal = 24.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.trash_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText
                        )
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
                    expandedTaskId = expandedTaskId,
                    isQuickFindOpen = isQuickFindOpen,
                    selectedTaskIds = selectedTaskIds,
                    allVisibleTaskIds = allVisibleTaskIds,
                    magicBreakdownTaskId = magicBreakdownTaskId,
                    activeLocale = activeLocale,
                    externalDialogTriggerToken = externalDialogTriggerToken,
                    externalDialogTriggerType = externalDialogTriggerType,
                    onRequestEnsureVisible = handleEnsureVisibleRequest,
                    dragHandle = taskDragHandleFor(task),
                    modifier = Modifier.alpha(if (task.id == draggedTaskId) 0.35f else 1f)
                )
            }

            projectHeadings.forEach { heading ->
                val tasksInHeading = projectSections[heading.id].orEmpty()
                item(key = "heading_${heading.id}") {
                    ProjectHeadingSectionHeader(
                        heading = heading,
                        isCollapsed = heading.id in collapsedHeadingIds,
                        onToggleCollapse = { viewModel.toggleHeadingCollapsed(heading.id) },
                        onRenameHeading = { newTitle -> viewModel.renameHeading(heading.id, newTitle) },
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
                            expandedTaskId = expandedTaskId,
                            isQuickFindOpen = isQuickFindOpen,
                            selectedTaskIds = selectedTaskIds,
                            allVisibleTaskIds = allVisibleTaskIds,
                            magicBreakdownTaskId = magicBreakdownTaskId,
                            activeLocale = activeLocale,
                            externalDialogTriggerToken = externalDialogTriggerToken,
                            externalDialogTriggerType = externalDialogTriggerType,
                            onRequestEnsureVisible = handleEnsureVisibleRequest,
                            dragHandle = taskDragHandleFor(task),
                            modifier = Modifier.alpha(if (task.id == draggedTaskId) 0.35f else 1f)
                        )
                    }
                }
            }
        } else if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.UPCOMING)) {
            // Upcoming Smart View ("Geplant"): Group tasks chronologically by day and only show calendar events on dates that have tasks
            val zone = ZoneId.systemDefault()
            val todayDate = LocalDate.ofEpochDay(currentEpochDay)
            val groupedUpcoming = primaryTasks
                .groupBy { task -> computeUpcomingTargetDate(task, todayDate, zone) }
                .toSortedMap()

            var cumulativeTaskOffset = 0
            groupedUpcoming.entries.forEachIndexed { groupIdx, (targetDate, dayTasks) ->
                val dayEvents = if (calendarEventsEnabled && dayTasks.isNotEmpty()) {
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
                        expandedTaskId = expandedTaskId,
                        isQuickFindOpen = isQuickFindOpen,
                        selectedTaskIds = selectedTaskIds,
                        allVisibleTaskIds = allVisibleTaskIds,
                        magicBreakdownTaskId = magicBreakdownTaskId,
                        activeLocale = activeLocale,
                        externalDialogTriggerToken = externalDialogTriggerToken,
                        externalDialogTriggerType = externalDialogTriggerType,
                        onRequestEnsureVisible = handleEnsureVisibleRequest
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
                    expandedTaskId = expandedTaskId,
                    isQuickFindOpen = isQuickFindOpen,
                    selectedTaskIds = selectedTaskIds,
                    allVisibleTaskIds = allVisibleTaskIds,
                    magicBreakdownTaskId = magicBreakdownTaskId,
                    activeLocale = activeLocale,
                    externalDialogTriggerToken = externalDialogTriggerToken,
                    externalDialogTriggerType = externalDialogTriggerType,
                    onRequestEnsureVisible = handleEnsureVisibleRequest
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
                            expandedTaskId = expandedTaskId,
                            isQuickFindOpen = isQuickFindOpen,
                            selectedTaskIds = selectedTaskIds,
                            allVisibleTaskIds = allVisibleTaskIds,
                            magicBreakdownTaskId = magicBreakdownTaskId,
                            activeLocale = activeLocale,
                            externalDialogTriggerToken = externalDialogTriggerToken,
                            externalDialogTriggerType = externalDialogTriggerType,
                            onRequestEnsureVisible = handleEnsureVisibleRequest
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
                                imageVector = Icons.Outlined.Inventory2,
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
                            expandedTaskId = expandedTaskId,
                            isQuickFindOpen = isQuickFindOpen,
                            selectedTaskIds = selectedTaskIds,
                            allVisibleTaskIds = allVisibleTaskIds,
                            magicBreakdownTaskId = magicBreakdownTaskId,
                            activeLocale = activeLocale,
                            externalDialogTriggerToken = externalDialogTriggerToken,
                            externalDialogTriggerType = externalDialogTriggerType,
                            onRequestEnsureVisible = handleEnsureVisibleRequest
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
                    expandedTaskId = expandedTaskId,
                    isQuickFindOpen = isQuickFindOpen,
                    selectedTaskIds = selectedTaskIds,
                    allVisibleTaskIds = allVisibleTaskIds,
                    magicBreakdownTaskId = magicBreakdownTaskId,
                    activeLocale = activeLocale,
                    externalDialogTriggerToken = externalDialogTriggerToken,
                    externalDialogTriggerType = externalDialogTriggerType,
                    onRequestEnsureVisible = handleEnsureVisibleRequest
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
                    expandedTaskId = expandedTaskId,
                    isQuickFindOpen = isQuickFindOpen,
                    selectedTaskIds = selectedTaskIds,
                    allVisibleTaskIds = allVisibleTaskIds,
                    magicBreakdownTaskId = magicBreakdownTaskId,
                    activeLocale = activeLocale,
                    externalDialogTriggerToken = externalDialogTriggerToken,
                    externalDialogTriggerType = externalDialogTriggerType,
                    onRequestEnsureVisible = handleEnsureVisibleRequest
                )
            }
        }
    }
    if (isProjectView) {
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
    showCreateProjectDialog: Boolean,
    editingProject: Project?,
    projectDialogPreselectedAreaId: String?,
    onDismissCreateProjectDialog: () -> Unit,
    showEmptyTrashConfirmDialog: Boolean = false,
    onDismissEmptyTrashConfirmDialog: () -> Unit = {}
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

    // Create or Edit Project Dialog (with Target Date / Deadline, Area Assignment & v2 Templates/Import trigger)
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
            onOpenTemplatesAndImport = { preAreaId ->
                viewModel.openTemplatesModal(initialTab = 0, defaultAreaId = preAreaId)
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

    // v2 Pillar 2: Reusable Project Templates & Markdown / JSON Outline Importer Modal
    if (isTemplatesModalOpen) {
        val fallbackProjectTitle = stringResource(R.string.entity_new_project)
        ThingsTemplatesAndImportModal(
            initialTab = templatesModalInitialTab,
            defaultAreaId = templatesModalDefaultAreaId,
            areas = areas,
            userTemplates = userProjectTemplates,
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

            ThingsHoverTooltip(text = stringResource(R.string.move_dialog_title)) {
                ThingsTagPill(
                    title = "📁 ${stringResource(R.string.action_move)}",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenBatchMove()
                    }
                )
            }

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
    onOpenVoiceCapture: () -> Unit,
    onOpenWeeklyReview: () -> Unit = {},
    onOpenGeminiHub: () -> Unit,
    onOpenAreaManager: () -> Unit,
    onOpenTagManager: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onManualSync: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = ThingsTheme.colors
    val iconButtonSize = if (isWideScreen) 34.dp else 29.dp
    val iconGlyphSize = if (isWideScreen) 19.dp else 18.dp

    ThingsTopBarTooltipScope {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isWideScreen) 16.dp else 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isWideScreen) 8.dp else 4.dp)
            ) {
                val sidebarTooltip = if (isSidebarVisible) {
                    stringResource(R.string.topbar_tooltip_hide_sidebar)
                } else {
                    stringResource(R.string.topbar_tooltip_show_sidebar)
                }
                ThingsHoverTooltip(
                    text = sidebarTooltip
                ) {
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

                if (showBrandSymbol) {
                    ThingsHoverTooltip(text = stringResource(R.string.view_today)) {
                        ThingsbookBrandSymbol(
                            size = if (isWideScreen) 28.dp else 25.dp,
                            onClick = onSelectTodayView
                        )
                    }
                }

                // Quick Find Trigger (tooltip only when rendered as a compact icon on phones; wide screen shows visible label)
                ThingsHoverTooltip(
                    text = stringResource(R.string.topbar_tooltip_quick_find),
                    enabled = !isWideScreen
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isQuickFindOpen) colors.thingsBlueSoft else colors.tagPillBg)
                            .clickable(onClick = onOpenQuickFind)
                            .padding(
                                horizontal = if (isWideScreen) 12.dp else 9.dp,
                                vertical = if (isWideScreen) 7.dp else 6.dp
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
                        if (isWideScreen) {
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

            Spacer(modifier = Modifier.width(if (isWideScreen) 8.dp else 4.dp))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isWideScreen) 2.dp else 1.dp)
                ) {
                    // Voice Quick Capture Button 🎙️
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

                    // Guided GTD Weekly Review Button
                    ThingsHoverTooltip(text = stringResource(R.string.topbar_tooltip_weekly_review)) {
                        IconButton(
                            onClick = onOpenWeeklyReview,
                            modifier = Modifier.size(iconButtonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = stringResource(R.string.topbar_tooltip_weekly_review),
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        }
                    }

                    // Gemini AI Pill ✨ (displays visible "Gemini" label — no tooltip)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.thingsBlueSoft)
                            .clickable(onClick = onOpenGeminiHub)
                            .padding(
                                horizontal = if (isWideScreen) 10.dp else 8.dp,
                                vertical = if (isWideScreen) 6.dp else 5.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = stringResource(R.string.gemini_hub_title),
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(if (isWideScreen) 16.dp else 15.dp)
                        )
                        Text(
                            text = "Gemini",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.thingsBlue,
                            maxLines = 1
                        )
                    }

                    // Areas Manager Button 📦 (Work, Home, max 8)
                    ThingsHoverTooltip(text = stringResource(R.string.topbar_tooltip_areas)) {
                        IconButton(
                            onClick = onOpenAreaManager,
                            modifier = Modifier.size(iconButtonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Inventory2,
                                contentDescription = stringResource(R.string.topbar_tooltip_areas),
                                tint = colors.anytimeTeal,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        }
                    }

                    // Tag Management Button 🏷️
                    ThingsHoverTooltip(text = stringResource(R.string.topbar_tooltip_tags)) {
                        IconButton(
                            onClick = onOpenTagManager,
                            modifier = Modifier.size(iconButtonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocalOffer,
                                contentDescription = stringResource(R.string.topbar_tooltip_tags),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        }
                    }

                    // Daily & Task Notifications Button 🔔
                    ThingsHoverTooltip(text = stringResource(R.string.topbar_tooltip_notifications)) {
                        IconButton(
                            onClick = onOpenNotificationSettings,
                            modifier = Modifier.size(iconButtonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = stringResource(R.string.topbar_tooltip_notifications),
                                tint = if (dailyDigestEnabled) colors.thingsBlue else colors.secondaryText,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        }
                    }

                    // In-App Language Switcher (EN, DE, IT, FR, ES)
                    Box {
                        ThingsHoverTooltip(text = stringResource(R.string.topbar_tooltip_language)) {
                            IconButton(
                                onClick = { onToggleLanguageMenu(true) },
                                modifier = Modifier.size(iconButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = stringResource(R.string.topbar_tooltip_language),
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(iconGlyphSize)
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
                }

                // Pinned Settings Button ⚙️ at the far top-right so it is always visible on phones and tablets
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
    onSelectSmartView: (SmartViewType) -> Unit,
    onSelectArea: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onOpenAreaManager: () -> Unit,
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
        // 1. Smart Views
        items(SmartViewType.entries) { viewType ->
            val selected = navTarget == ActiveNavigationTarget.SmartView(viewType)
            val viewTitle = stringResource(viewType.titleRes)
            ThingsHoverTooltip(text = viewTitle) {
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
                        text = viewTitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) colors.primaryText else colors.secondaryText
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Transparent)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenWeeklyReview()
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Checklist,
                        contentDescription = reviewTitle,
                        tint = colors.thingsBlue,
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Transparent)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenTemplatesAndImport()
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = templatesTitle,
                        tint = colors.anytimeTeal,
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

        // 2. Areas (Work, Home, ... up to 8)
        items(areas, key = { "nav_area_${it.id}" }) { area ->
            val selected = navTarget == ActiveNavigationTarget.AreaDetail(area.id)
            ThingsHoverTooltip(text = "${stringResource(R.string.entity_area)}: ${area.title}") {
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
        }

        // 3. Projects (with Progress Ring & Target Date Flag)
        items(projects, key = { "nav_proj_${it.id}" }) { project ->
            val selected = navTarget == ActiveNavigationTarget.ProjectDetail(project.id)
            ThingsHoverTooltip(text = "$projectLabel: ${project.title}") {
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
        }

        // 4. "+ Project" Quick Trigger
        item(key = "nav_add_project") {
            ThingsHoverTooltip(text = "${stringResource(R.string.entity_new_project)} (Ctrl+Shift+N)") {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewTitleHeader(
    navTarget: ActiveNavigationTarget,
    currentProject: Project?,
    currentArea: Area?,
    areas: List<Area>,
    projects: List<Project>,
    projectAttachments: List<TaskAttachment> = emptyList(),
    locale: Locale,
    trashItemCount: Int = 0,
    isTodayTimelineMode: Boolean = false,
    onToggleTodayTimelineMode: (Boolean) -> Unit = {},
    onSaveProjectAsTemplate: (String, String) -> Unit = { _, _ -> },
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
    val coroutineScope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    var showProjectDeadlineDialog by remember(currentProject?.id) { mutableStateOf(false) }
    var showProjectAreaMenu by remember(currentProject?.id) { mutableStateOf(false) }
    var showProjectAttachMenu by remember(currentProject?.id) { mutableStateOf(false) }
    val tooLargeMsg = stringResource(R.string.attachment_too_large)
    val attachmentImportErrorMsg = stringResource(R.string.attachment_import_error)
    val templateSavedToastMsg = stringResource(R.string.v2_template_saved_toast)
    val stalledStatus = remember(currentProject) {
        currentProject?.let { StalledProjectDetector.evaluateProject(it) }
    }

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
                            color = colors.primaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                    is ActiveNavigationTarget.AreaDetail -> {
                        Icon(
                            imageVector = Icons.Outlined.Inventory2,
                            contentDescription = null,
                            tint = colors.anytimeTeal,
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
                            Text(
                                text = currentArea?.title ?: stringResource(R.string.entity_area),
                                style = MaterialTheme.typography.headlineLarge,
                                color = colors.primaryText
                            )
                        }
                    }
                }
            }

            // Quick Actions for Today (List | Timeline toggle), Project, Area, or Trash in Header
            if (navTarget == ActiveNavigationTarget.SmartView(SmartViewType.TODAY)) {
                ThingsTodayModeSegmentedToggle(
                    isTimelineMode = isTodayTimelineMode,
                    onModeChange = onToggleTodayTimelineMode
                )
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
                            color = colors.deadlineUrgentRed
                        )
                    }
                }
            } else if (currentProject != null) {
                val projIdx = projects.indexOfFirst { it.id == currentProject.id }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (projIdx > 0) {
                        ThingsHoverTooltip(text = stringResource(R.string.project_move_up)) {
                            IconButton(
                                onClick = { onMoveProject(currentProject.id, true) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowUpward,
                                    contentDescription = stringResource(R.string.project_move_up),
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    if (projIdx != -1 && projIdx < projects.lastIndex) {
                        ThingsHoverTooltip(text = stringResource(R.string.project_move_down)) {
                            IconButton(
                                onClick = { onMoveProject(currentProject.id, false) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDownward,
                                    contentDescription = stringResource(R.string.project_move_down),
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    ThingsHoverTooltip(text = "${stringResource(R.string.action_duplicate_project)} (Ctrl+D)") {
                        IconButton(
                            onClick = { onDuplicateProject(currentProject.id) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.action_duplicate_project),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
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
                    ThingsHoverTooltip(text = stringResource(R.string.project_complete_tooltip)) {
                        IconButton(
                            onClick = { onCompleteProject(currentProject.id) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircleOutline,
                                contentDescription = stringResource(R.string.project_complete_tooltip),
                                tint = colors.logbookGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    ThingsHoverTooltip(text = stringResource(R.string.project_delete_tooltip)) {
                        IconButton(
                            onClick = { onDeleteProject(currentProject.id) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.project_delete_tooltip),
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
                        ThingsHoverTooltip(text = stringResource(R.string.area_move_up)) {
                            IconButton(
                                onClick = { onMoveArea(currentArea.id, true) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowUpward,
                                    contentDescription = stringResource(R.string.area_move_up),
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    if (areaIdx != -1 && areaIdx < areas.lastIndex) {
                        ThingsHoverTooltip(text = stringResource(R.string.area_move_down)) {
                            IconButton(
                                onClick = { onMoveArea(currentArea.id, false) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDownward,
                                    contentDescription = stringResource(R.string.area_move_down),
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
                    ThingsHoverTooltip(text = stringResource(R.string.area_delete_tooltip)) {
                        IconButton(
                            onClick = { onDeleteArea(currentArea.id) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.area_delete_tooltip),
                                tint = colors.deadlineUrgentRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Project Notes, Target Date (Zieldatum / Deadline), Area Assignment, Attachments & Quick Buttons
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

                // v2 Pillar 2: "Save as Template"
                ThingsHoverTooltip(text = stringResource(R.string.v2_save_as_template)) {
                    ThingsTagPill(
                        title = "📋 ${stringResource(R.string.v2_save_as_template)}",
                        onClick = {
                            onSaveProjectAsTemplate(currentProject.id, templateSavedToastMsg)
                        }
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

@Composable
private fun ProjectHeadingSectionHeader(
    heading: Heading,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onRenameHeading: (String) -> Unit,
    onDeleteHeading: () -> Unit,
    onAddTaskUnderHeading: () -> Unit
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Mini "+": create a To-Do directly under this heading
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
                            icon = Icons.Outlined.LocalOffer,
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
    onCreateNewProject: (String?) -> Unit,
    onOpenWeeklyReview: () -> Unit = {},
    onOpenTemplatesAndImport: () -> Unit = {},
    onCloseDrawer: (() -> Unit)? = null
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val areasLabel = stringResource(R.string.entity_areas)
    val stalledStatuses = remember(projects) {
        StalledProjectDetector.detectStalledProjects(projects)
    }
    val stalledCount = stalledStatuses.size

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

        Spacer(modifier = Modifier.height(2.dp))

        SmartViewType.entries.forEachIndexed { idx, viewType ->
            val selected = navTarget == ActiveNavigationTarget.SmartView(viewType)
            val count = smartViewCounts[viewType] ?: 0
            val viewLabel = stringResource(viewType.titleRes)
            val shortcutHint = if (idx < 7) " (Ctrl+${idx + 1})" else ""
            ThingsHoverTooltip(
                text = "$viewLabel$shortcutHint",
                modifier = Modifier.fillMaxWidth()
            ) {
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
                            text = viewLabel,
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
        }

        // v2 GTD Weekly Review & Project Templates / Markdown Importer quick rows
        ThingsHoverTooltip(
            text = stringResource(R.string.v2_weekly_review_title),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.Transparent)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenWeeklyReview()
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Checklist,
                        contentDescription = stringResource(R.string.v2_weekly_review_title),
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.v2_weekly_review_title),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Normal,
                            color = colors.primaryText
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (stalledCount > 0) {
                    Text(
                        text = stalledCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.secondaryText
                    )
                }
            }
        }

        ThingsHoverTooltip(
            text = stringResource(R.string.v2_templates_title),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.Transparent)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenTemplatesAndImport()
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.v2_templates_title),
                    tint = colors.anytimeTeal,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.v2_templates_title),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal,
                        color = colors.primaryText
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = colors.hairlineDivider, thickness = 0.75.dp)
        Spacer(modifier = Modifier.height(4.dp))

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
                    }

                    // Indented Projects inside this Area
                    areaProjects.forEach { project ->
                        val selected = navTarget == ActiveNavigationTarget.ProjectDetail(project.id)
                        val projStatus = StalledProjectDetector.evaluateProject(project)
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                            }
                        }
                    }
                }
            }

            // Unassigned Projects (not in any Area)
            val unassignedProjects = projects.filter { it.areaId == null }
            items(unassignedProjects, key = { "sidebar_proj_unassigned_${it.id}" }) { project ->
                val selected = navTarget == ActiveNavigationTarget.ProjectDetail(project.id)
                val projStatus = StalledProjectDetector.evaluateProject(project)
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    }
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
            ThingsHoverTooltip(text = "${stringResource(R.string.entity_new_project)} (Ctrl+Shift+N)") {
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

            ThingsHoverTooltip(
                text = stringResource(
                    R.string.areas_manage_count_tooltip,
                    areas.size,
                    ThingsRepository.MAX_AREAS
                )
            ) {
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

        // App Version displayed at the bottom-left of the Sidebar (with BETA badge when running pre-release)
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
                    shape = RoundedCornerShape(4.dp),
                    color = colors.todayStarYellow.copy(alpha = 0.24f)
                ) {
                    Text(
                        text = stringResource(R.string.v2_beta_badge),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = colors.somedayWarmAmber,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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



