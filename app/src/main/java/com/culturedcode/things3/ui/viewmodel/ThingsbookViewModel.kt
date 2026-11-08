package com.culturedcode.things3.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culturedcode.things3.BuildConfig
import com.culturedcode.things3.data.ai.GeminiLogbookReviewResponse
import com.culturedcode.things3.data.ai.ThingsbookGeminiService
import com.culturedcode.things3.data.backup.BackupInspectionResult
import com.culturedcode.things3.data.backup.BackupPasswordRequiredException
import com.culturedcode.things3.data.backup.BackupPreferencesDto
import com.culturedcode.things3.data.backup.InvalidBackupPasswordException
import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.notifications.AppIconBadgeUpdater
import com.culturedcode.things3.data.notifications.DailyDigestConfig
import com.culturedcode.things3.data.notifications.ReminderScheduler
import com.culturedcode.things3.data.repository.CloudSyncStatus
import com.culturedcode.things3.data.repository.SyncRepository
import com.culturedcode.things3.data.repository.ThingsRepository
import com.culturedcode.things3.domain.model.Area
import com.culturedcode.things3.domain.model.Heading
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.domain.model.ProjectStatus
import com.culturedcode.things3.domain.model.RecurrenceRule
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.Tag
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.domain.model.TaskStatus
import com.culturedcode.things3.domain.util.runCatchingCancellable
import com.culturedcode.things3.ui.components.SmartViewType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

sealed interface ActiveNavigationTarget {
    data class SmartView(val type: SmartViewType) : ActiveNavigationTarget
    data class ProjectDetail(val projectId: String) : ActiveNavigationTarget
    data class AreaDetail(val areaId: String) : ActiveNavigationTarget
}

class PendingRestoreSession(
    val rawBytes: ByteArray,
    val fileNameHint: String?,
    val inspection: BackupInspectionResult
)

sealed interface PendingCloudAuthRequest {
    val displayAccountLabel: String

    data class RegisterEmail(val email: String, val password: String) : PendingCloudAuthRequest {
        override val displayAccountLabel: String get() = email
    }

    data class SignInEmail(val email: String, val password: String) : PendingCloudAuthRequest {
        override val displayAccountLabel: String get() = email
    }

    data class SignInGoogle(val optionalEmail: String?) : PendingCloudAuthRequest {
        override val displayAccountLabel: String
            get() = optionalEmail?.takeIf { it.isNotBlank() } ?: "Google Cloud-Konto"
    }

    data object SignInAnonymous : PendingCloudAuthRequest {
        override val displayAccountLabel: String get() = "Thingsbook Cloud-Konto"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThingsbookViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ThingsRepository,
    private val database: ThingsDatabase,
    private val syncRepository: SyncRepository,
    private val geminiService: ThingsbookGeminiService,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _uiErrorMessage = MutableStateFlow<String?>(null)
    val uiErrorMessage: StateFlow<String?> = _uiErrorMessage.asStateFlow()

    private val _geminiErrorMessage = MutableStateFlow<String?>(null)
    val geminiErrorMessage: StateFlow<String?> = _geminiErrorMessage.asStateFlow()

    private val safeVmScope = CoroutineScope(
        viewModelScope.coroutineContext +
            SupervisorJob(viewModelScope.coroutineContext[Job]) +
            CoroutineExceptionHandler { _, throwable ->
                if (throwable !is CancellationException) {
                    Log.e(TAG, "Unhandled ViewModel coroutine error", throwable)
                    _uiErrorMessage.value = throwable.localizedMessage
                        ?: throwable.message
                        ?: "Ein unerwarteter Fehler ist aufgetreten."
                }
            }
    )

    fun clearUiErrorMessage() {
        _uiErrorMessage.value = null
    }

    fun clearGeminiErrorMessage() {
        _geminiErrorMessage.value = null
    }

    private val _navigationTarget = MutableStateFlow<ActiveNavigationTarget>(
        ActiveNavigationTarget.SmartView(SmartViewType.TODAY)
    )
    val navigationTarget: StateFlow<ActiveNavigationTarget> = _navigationTarget.asStateFlow()

    private val _expandedTaskId = MutableStateFlow<String?>(null)
    val expandedTaskId: StateFlow<String?> = _expandedTaskId.asStateFlow()

    private val _collapsedHeadingIds = MutableStateFlow<Set<String>>(emptySet())
    val collapsedHeadingIds: StateFlow<Set<String>> = _collapsedHeadingIds.asStateFlow()

    // Tag Filter & Tag Management Modal States
    private val _selectedFilterTagId = MutableStateFlow<String?>(null)
    val selectedFilterTagId: StateFlow<String?> = _selectedFilterTagId.asStateFlow()

    private val _isTagManagerOpen = MutableStateFlow(false)
    val isTagManagerOpen: StateFlow<Boolean> = _isTagManagerOpen.asStateFlow()

    // Area Manager Modal State
    private val _isAreaManagerOpen = MutableStateFlow(false)
    val isAreaManagerOpen: StateFlow<Boolean> = _isAreaManagerOpen.asStateFlow()

    // Daily Digest & Notification Settings Modal States
    private val _isNotificationSettingsOpen = MutableStateFlow(false)
    val isNotificationSettingsOpen: StateFlow<Boolean> = _isNotificationSettingsOpen.asStateFlow()

    private val _dailyDigestConfig = MutableStateFlow(
        runCatching { reminderScheduler.getDailyDigestConfig() }
            .getOrDefault(DailyDigestConfig(enabled = false, hour = 8, minute = 30))
    )
    val dailyDigestConfig: StateFlow<DailyDigestConfig> = _dailyDigestConfig.asStateFlow()

    // Quick Find (SQLite FTS5)
    private val _isQuickFindOpen = MutableStateFlow(false)
    val isQuickFindOpen: StateFlow<Boolean> = _isQuickFindOpen.asStateFlow()

    private val _quickFindQuery = MutableStateFlow("")
    val quickFindQuery: StateFlow<String> = _quickFindQuery.asStateFlow()

    // Gemini AI States
    private val _isGeminiModalOpen = MutableStateFlow(false)
    val isGeminiModalOpen: StateFlow<Boolean> = _isGeminiModalOpen.asStateFlow()

    private val _isGeminiBusy = MutableStateFlow(false)
    val isGeminiBusy: StateFlow<Boolean> = _isGeminiBusy.asStateFlow()

    private val _magicBreakdownTaskId = MutableStateFlow<String?>(null)
    val magicBreakdownTaskId: StateFlow<String?> = _magicBreakdownTaskId.asStateFlow()

    private val _logbookAiReview = MutableStateFlow<GeminiLogbookReviewResponse?>(null)
    val logbookAiReview: StateFlow<GeminiLogbookReviewResponse?> = _logbookAiReview.asStateFlow()

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    // Cloud Sync & Auth Modal States
    private val _isAuthModalOpen = MutableStateFlow(false)
    val isAuthModalOpen: StateFlow<Boolean> = _isAuthModalOpen.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    private val _authInfoMessage = MutableStateFlow<String?>(null)
    val authInfoMessage: StateFlow<String?> = _authInfoMessage.asStateFlow()

    val currentUserIdentifier: StateFlow<String?> = syncRepository.activeUserIdentifier

    // Core Data Streams from Room Single Source of Truth
    val inboxTasks: StateFlow<List<TaskItem>> = repository.observeInboxTasks()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayDaytimeTasks: StateFlow<List<TaskItem>> = repository.observeTodayDaytimeTasks()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val thisEveningTasks: StateFlow<List<TaskItem>> = repository.observeThisEveningTasks()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingTasks: StateFlow<List<TaskItem>> = repository.observeUpcomingTasks()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val anytimeTasks: StateFlow<List<TaskItem>> = repository.observeAnytimeTasks()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val somedayTasks: StateFlow<List<TaskItem>> = repository.observeSomedayTasks()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logbookTasks: StateFlow<List<TaskItem>> = repository.observeLogbookTasks()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProjects: StateFlow<List<Project>> = repository.observeActiveProjectsWithProgress()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val areas: StateFlow<List<Area>> = repository.observeAreas()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHeadings: StateFlow<List<Heading>> = repository.observeAllActiveHeadings()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<Tag>> = repository.observeAllTags()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncStatus: StateFlow<CloudSyncStatus> = syncRepository.syncStatus

    // Project headings & tasks when viewing a specific ProjectDetail
    val activeProjectHeadings: StateFlow<List<Heading>> = _navigationTarget
        .flatMapLatest { target ->
            if (target is ActiveNavigationTarget.ProjectDetail) {
                database.headingDao().observeHeadingsForProject(target.projectId)
                    .map { list ->
                        list.mapNotNull {
                            runCatching {
                                Heading(
                                    id = it.id,
                                    projectId = it.projectId,
                                    title = it.title,
                                    sortOrder = it.sortOrder,
                                    isArchived = it.isArchived,
                                    createdAt = it.createdAt,
                                    updatedAt = it.updatedAt,
                                    isDeleted = it.isDeleted
                                )
                            }.getOrNull()
                        }.distinctBy { it.id }
                    }
                    .catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProjectTasks: StateFlow<List<TaskItem>> = _navigationTarget
        .flatMapLatest { target ->
            if (target is ActiveNavigationTarget.ProjectDetail) {
                combine(
                    todayDaytimeTasks,
                    thisEveningTasks,
                    upcomingTasks,
                    anytimeTasks,
                    somedayTasks
                ) { today, evening, upcoming, anytime, someday ->
                    (today + evening + upcoming + anytime + someday)
                        .distinctBy { it.id }
                        .filter { it.projectId == target.projectId }
                        .sortedBy { it.sortOrder }
                }
            } else {
                flowOf(emptyList())
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Direct tasks belonging to an Area when viewing AreaDetail
    val activeAreaTasks: StateFlow<List<TaskItem>> = _navigationTarget
        .flatMapLatest { target ->
            if (target is ActiveNavigationTarget.AreaDetail) {
                combine(
                    todayDaytimeTasks,
                    thisEveningTasks,
                    upcomingTasks,
                    anytimeTasks,
                    somedayTasks
                ) { today, evening, upcoming, anytime, someday ->
                    (today + evening + upcoming + anytime + someday)
                        .distinctBy { it.id }
                        .filter { it.areaId == target.areaId && it.projectId == null }
                        .sortedBy { it.sortOrder }
                }
            } else {
                flowOf(emptyList())
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real-time FTS5 Quick Find Results
    val quickFindTaskResults: StateFlow<List<TaskItem>> = _quickFindQuery
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.searchTasksFts5(q)
        }
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickFindProjectResults: StateFlow<List<Project>> = _quickFindQuery
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.searchProjectsFts5(q)
        }
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectSmartView(viewType: SmartViewType) {
        _expandedTaskId.value = null
        _navigationTarget.value = ActiveNavigationTarget.SmartView(viewType)
    }

    fun handleWidgetLaunchIntent(viewName: String?, taskId: String?, quickAdd: Boolean) {
        val targetView = when (viewName?.uppercase(Locale.ROOT)) {
            "INBOX" -> SmartViewType.INBOX
            "UPCOMING" -> SmartViewType.UPCOMING
            "ANYTIME" -> SmartViewType.ANYTIME
            "SOMEDAY" -> SmartViewType.SOMEDAY
            else -> SmartViewType.TODAY
        }
        _navigationTarget.value = ActiveNavigationTarget.SmartView(targetView)
        if (!taskId.isNullOrBlank()) {
            _expandedTaskId.value = taskId
        } else if (quickAdd) {
            createTaskAtPosition(emptyList(), 0, "")
        }
    }

    fun selectProject(projectId: String) {
        _expandedTaskId.value = null
        _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(projectId)
    }

    fun selectArea(areaId: String) {
        _expandedTaskId.value = null
        _navigationTarget.value = ActiveNavigationTarget.AreaDetail(areaId)
    }

    fun toggleTaskExpansion(taskId: String?) {
        _expandedTaskId.value = if (_expandedTaskId.value == taskId) null else taskId
    }

    fun collapseExpandedTask() {
        _expandedTaskId.value = null
    }

    fun toggleHeadingCollapsed(headingId: String) {
        val current = _collapsedHeadingIds.value
        _collapsedHeadingIds.value = if (headingId in current) current - headingId else current + headingId
    }

    fun setQuickFindOpen(open: Boolean) {
        _isQuickFindOpen.value = open
        if (!open) _quickFindQuery.value = ""
    }

    fun updateQuickFindQuery(query: String) {
        _quickFindQuery.value = query
    }

    fun setGeminiModalOpen(open: Boolean) {
        if (!open) _geminiErrorMessage.value = null
        _isGeminiModalOpen.value = open
    }

    fun updateGeminiApiKey(key: String) {
        val trimmed = key.trim()
        _geminiApiKey.value = trimmed
        geminiService.customApiKey = trimmed
        runCatching {
            appContext.getSharedPreferences("thingsbook_settings_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("gemini_api_key", trimmed)
                .apply()
        }.onFailure { err ->
            Log.w(TAG, "Failed persisting Gemini API key", err)
        }
    }

    /**
     * Switches app language live via AndroidX `AppCompatDelegate.setApplicationLocales` (EN, DE, IT, FR, ES).
     */
    fun switchAppLanguage(languageTag: String) {
        runCatching {
            val localeList = if (languageTag.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }.onFailure { err ->
            Log.w(TAG, "Failed switching language to '$languageTag'", err)
            _uiErrorMessage.value = err.localizedMessage ?: "Sprache konnte nicht geändert werden."
        }
    }

    fun updateTaskStatus(taskId: String, status: TaskStatus) {
        safeVmScope.launch {
            repository.updateTaskStatus(taskId, status)
            if (status != TaskStatus.OPEN && _expandedTaskId.value == taskId) {
                _expandedTaskId.value = null
            }
        }
    }

    fun updateTaskTitleAndNotes(task: TaskItem, newTitle: String, newNotes: String) {
        safeVmScope.launch {
            repository.createOrUpdateTask(
                id = task.id,
                title = newTitle,
                notes = newNotes,
                status = task.status,
                scheduleState = task.scheduleState,
                whenDate = task.whenDate,
                deadline = task.deadline,
                reminderTime = task.reminderTime,
                projectId = task.projectId,
                headingId = task.headingId,
                areaId = task.areaId,
                recurrenceRule = task.recurrenceRule,
                sortOrder = task.sortOrder,
                tagIds = task.tags.map { it.id }
            )
        }
    }

    fun updateTaskSchedule(task: TaskItem, newState: ScheduleState, newWhenDate: Long?) {
        safeVmScope.launch {
            repository.createOrUpdateTask(
                id = task.id,
                title = task.title,
                notes = task.notes,
                status = task.status,
                scheduleState = newState,
                whenDate = newWhenDate,
                deadline = task.deadline,
                reminderTime = task.reminderTime,
                projectId = task.projectId,
                headingId = task.headingId,
                areaId = task.areaId,
                recurrenceRule = task.recurrenceRule,
                sortOrder = task.sortOrder,
                tagIds = task.tags.map { it.id }
            )
        }
    }

    fun updateTaskDeadline(task: TaskItem, newDeadline: Long?) {
        safeVmScope.launch {
            repository.createOrUpdateTask(
                id = task.id,
                title = task.title,
                notes = task.notes,
                status = task.status,
                scheduleState = task.scheduleState,
                whenDate = task.whenDate,
                deadline = newDeadline,
                reminderTime = task.reminderTime,
                projectId = task.projectId,
                headingId = task.headingId,
                areaId = task.areaId,
                recurrenceRule = task.recurrenceRule,
                sortOrder = task.sortOrder,
                tagIds = task.tags.map { it.id }
            )
        }
    }

    fun addChecklistItem(taskId: String, title: String) {
        safeVmScope.launch {
            repository.addChecklistItem(taskId, title)
        }
    }

    fun toggleChecklistItem(itemId: String, isCompleted: Boolean) {
        safeVmScope.launch {
            repository.toggleChecklistItem(itemId, isCompleted)
        }
    }

    fun renameChecklistItem(itemId: String, newTitle: String) {
        safeVmScope.launch {
            repository.updateChecklistItemTitle(itemId, newTitle)
        }
    }

    fun moveChecklistItem(taskId: String, itemId: String, moveUp: Boolean) {
        safeVmScope.launch {
            repository.moveChecklistItem(taskId, itemId, moveUp)
        }
    }

    fun deleteChecklistItem(itemId: String) {
        safeVmScope.launch {
            repository.softDeleteChecklistItem(itemId)
        }
    }

    fun updateTaskRecurrence(taskId: String, recurrenceRule: RecurrenceRule?) {
        safeVmScope.launch {
            repository.updateTaskRecurrence(taskId, recurrenceRule)
        }
    }

    fun moveTaskToContainer(
        taskId: String,
        projectId: String?,
        headingId: String?,
        areaId: String?
    ) {
        safeVmScope.launch {
            repository.moveTaskToContainer(taskId, projectId, headingId, areaId)
        }
    }

    fun deleteTask(taskId: String) {
        safeVmScope.launch {
            if (_expandedTaskId.value == taskId) {
                _expandedTaskId.value = null
            }
            repository.softDeleteTask(taskId)
        }
    }

    /**
     * Magic Plus Drop Logic: Inserts a new To-Do at exact `dropIndex` using LexoRank midpoint `sortOrder`
     * and immediately expands the newly created card inline!
     */
    fun createTaskAtPosition(
        currentList: List<TaskItem>,
        dropIndex: Int,
        defaultTitle: String
    ) {
        safeVmScope.launch {
            val prevOrder = currentList.getOrNull(dropIndex - 1)?.sortOrder
            val nextOrder = currentList.getOrNull(dropIndex)?.sortOrder
            val midpointOrder = repository.computeMidpointSortOrder(prevOrder, nextOrder)

            val zone = ZoneId.systemDefault()
            val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

            val target = _navigationTarget.value
            val (scheduleState, whenDate, projectId) = when (target) {
                is ActiveNavigationTarget.SmartView -> when (target.type) {
                    SmartViewType.INBOX -> Triple(ScheduleState.INBOX, null, null)
                    SmartViewType.TODAY -> Triple(ScheduleState.TODAY, todayEpoch, null)
                    SmartViewType.UPCOMING -> Triple(
                        ScheduleState.UPCOMING,
                        LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                        null
                    )
                    SmartViewType.ANYTIME -> Triple(ScheduleState.ANYTIME, null, null)
                    SmartViewType.SOMEDAY -> Triple(ScheduleState.SOMEDAY, null, null)
                    SmartViewType.LOGBOOK -> Triple(ScheduleState.TODAY, todayEpoch, null)
                }
                is ActiveNavigationTarget.ProjectDetail -> Triple(ScheduleState.ANYTIME, null, target.projectId)
                is ActiveNavigationTarget.AreaDetail -> Triple(ScheduleState.ANYTIME, null, null)
            }

            val areaId = when (target) {
                is ActiveNavigationTarget.AreaDetail -> target.areaId
                is ActiveNavigationTarget.ProjectDetail -> activeProjects.value.find { it.id == target.projectId }?.areaId
                else -> null
            }

            val neighboringHeadingId = currentList.getOrNull(dropIndex - 1)?.headingId
                ?: currentList.getOrNull(dropIndex)?.headingId

            val createdId = repository.createOrUpdateTask(
                title = defaultTitle,
                notes = "",
                status = TaskStatus.OPEN,
                scheduleState = scheduleState,
                whenDate = whenDate,
                projectId = projectId,
                headingId = neighboringHeadingId,
                areaId = areaId,
                sortOrder = midpointOrder
            )

            // Expand newly dropped card inline immediately
            _expandedTaskId.value = createdId
        }
    }

    fun createHeadingInCurrentProject(dropIndex: Int, defaultHeadingTitle: String) {
        val target = _navigationTarget.value as? ActiveNavigationTarget.ProjectDetail ?: return
        safeVmScope.launch {
            val headings = activeProjectHeadings.value
            val prevOrder = headings.getOrNull(dropIndex - 1)?.sortOrder
            val nextOrder = headings.getOrNull(dropIndex)?.sortOrder
            val order = repository.computeMidpointSortOrder(prevOrder, nextOrder)
            repository.createHeadingInProject(
                projectId = target.projectId,
                title = defaultHeadingTitle,
                sortOrder = order
            )
        }
    }

    fun renameHeading(headingId: String, newTitle: String) {
        safeVmScope.launch {
            repository.renameHeading(headingId, newTitle)
        }
    }

    fun deleteHeading(headingId: String) {
        safeVmScope.launch {
            repository.softDeleteHeading(headingId)
        }
    }

    // =========================================================================
    // Areas of Responsibility (Work, Home, up to 6 Areas — Create, Rename, Move, Delete)
    // =========================================================================

    fun setAreaManagerOpen(open: Boolean) {
        _isAreaManagerOpen.value = open
    }

    fun createArea(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        safeVmScope.launch {
            repository.createArea(trimmed).onFailure { err ->
                _uiErrorMessage.value = err.localizedMessage ?: err.message ?: "Bereich konnte nicht erstellt werden."
            }
        }
    }

    fun createAreaAndNavigate(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        safeVmScope.launch {
            repository.createArea(trimmed)
                .onSuccess { createdAreaId ->
                    _expandedTaskId.value = null
                    _navigationTarget.value = ActiveNavigationTarget.AreaDetail(createdAreaId)
                }
                .onFailure { err ->
                    _uiErrorMessage.value = err.localizedMessage ?: err.message ?: "Bereich konnte nicht erstellt werden."
                }
        }
    }

    fun renameArea(areaId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        safeVmScope.launch {
            repository.renameArea(areaId, trimmed)
        }
    }

    fun moveArea(areaId: String, moveUp: Boolean) {
        safeVmScope.launch {
            repository.moveArea(areaId, moveUp)
        }
    }

    fun deleteArea(areaId: String) {
        safeVmScope.launch {
            val currentTarget = _navigationTarget.value
            if (currentTarget is ActiveNavigationTarget.AreaDetail && currentTarget.areaId == areaId) {
                _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.TODAY)
            }
            repository.softDeleteArea(areaId)
        }
    }

    // =========================================================================
    // Projects (with Target Date / Deadline, Area Assignment, Move, Complete, Delete)
    // =========================================================================

    fun saveProject(
        id: String? = null,
        title: String,
        notes: String,
        areaId: String?,
        deadlineEpochMillis: Long?,
        navigateToProject: Boolean = true
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return
        safeVmScope.launch {
            val savedId = if (id != null) {
                repository.createOrUpdateProject(
                    id = id,
                    title = trimmedTitle,
                    notes = notes,
                    areaId = areaId,
                    deadline = deadlineEpochMillis
                )
            } else {
                repository.createOrUpdateProject(
                    title = trimmedTitle,
                    notes = notes,
                    areaId = areaId,
                    deadline = deadlineEpochMillis
                )
            }
            if (navigateToProject) {
                _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(savedId)
            }
        }
    }

    fun updateProjectDeadline(projectId: String, deadlineEpochMillis: Long?) {
        safeVmScope.launch {
            repository.updateProjectDeadline(projectId, deadlineEpochMillis)
        }
    }

    fun updateProjectArea(projectId: String, areaId: String?) {
        safeVmScope.launch {
            repository.updateProjectArea(projectId, areaId)
        }
    }

    fun completeProject(projectId: String) {
        safeVmScope.launch {
            repository.updateProjectStatus(projectId, ProjectStatus.COMPLETED)
            val currentTarget = _navigationTarget.value
            if (currentTarget is ActiveNavigationTarget.ProjectDetail && currentTarget.projectId == projectId) {
                _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.LOGBOOK)
            }
        }
    }

    fun moveProject(projectId: String, moveUp: Boolean) {
        safeVmScope.launch {
            repository.moveProject(projectId, moveUp)
        }
    }

    fun deleteProject(projectId: String) {
        safeVmScope.launch {
            val currentTarget = _navigationTarget.value
            if (currentTarget is ActiveNavigationTarget.ProjectDetail && currentTarget.projectId == projectId) {
                _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.TODAY)
            }
            repository.softDeleteProject(projectId)
        }
    }

    // =========================================================================
    // 4 Multilingual Gemini AI Features (EN, DE, IT, FR, ES)
    // =========================================================================

    /**
     * Feature 1: Gemini Magic Breakdown inside an expanded To-Do card.
     */
    fun triggerGeminiMagicBreakdown(task: TaskItem, locale: Locale = Locale.getDefault()) {
        safeVmScope.launch {
            _magicBreakdownTaskId.value = task.id
            _geminiErrorMessage.value = null
            try {
                val steps = geminiService.generateMagicChecklist(
                    taskTitle = task.title,
                    taskNotes = task.notes,
                    locale = locale
                )
                if (steps.isNotEmpty()) {
                    repository.addChecklistItemsBatch(task.id, steps)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Gemini Magic Breakdown failed", t)
                val msg = t.localizedMessage ?: t.message ?: "KI-Teilaufgaben konnten nicht erstellt werden."
                _geminiErrorMessage.value = msg
                _uiErrorMessage.value = msg
            } finally {
                _magicBreakdownTaskId.value = null
            }
        }
    }

    /**
     * Feature 2: Gemini Brain Dump -> Structured GTD Tasks with automatic ScheduleState, whenDate, deadline & checklist.
     */
    fun submitGeminiBrainDump(rawText: String, locale: Locale = Locale.getDefault()) {
        if (rawText.isBlank()) return
        safeVmScope.launch {
            _isGeminiBusy.value = true
            _geminiErrorMessage.value = null
            try {
                val parsedTasks = geminiService.parseBrainDumpToTasks(rawText, locale)
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)

                var lastCreatedId: String? = null
                parsedTasks.forEachIndexed { idx, dto ->
                    val normalizedState = dto.scheduleState.trim().uppercase(Locale.ROOT)
                    val state = runCatching { ScheduleState.valueOf(normalizedState) }
                        .getOrDefault(ScheduleState.TODAY)
                    val whenEpoch = dto.whenDaysOffset?.let { offset ->
                        runCatching {
                            today.plusDays(offset.coerceIn(-365, 3650).toLong())
                                .atStartOfDay(zone)
                                .toInstant()
                                .toEpochMilli()
                        }.getOrNull()
                    }
                    val deadlineEpoch = dto.deadlineDaysOffset?.let { offset ->
                        runCatching {
                            today.plusDays(offset.coerceIn(-365, 3650).toLong())
                                .atStartOfDay(zone)
                                .toInstant()
                                .toEpochMilli()
                        }.getOrNull()
                    }

                    val taskId = repository.createOrUpdateTask(
                        title = dto.title,
                        notes = dto.notes,
                        status = TaskStatus.OPEN,
                        scheduleState = state,
                        whenDate = whenEpoch,
                        deadline = deadlineEpoch,
                        sortOrder = (idx + 1) * 500.0 + (System.currentTimeMillis() % 1000)
                    )
                    if (dto.checklist.isNotEmpty()) {
                        repository.addChecklistItemsBatch(taskId, dto.checklist)
                    }
                    if (idx == 0) lastCreatedId = taskId
                }

                _isGeminiModalOpen.value = false
                lastCreatedId?.let { _expandedTaskId.value = it }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Gemini Brain Dump failed", t)
                val msg = t.localizedMessage ?: t.message ?: "Brain-Dump-Analyse fehlgeschlagen."
                _geminiErrorMessage.value = msg
                _uiErrorMessage.value = msg
            } finally {
                _isGeminiBusy.value = false
            }
        }
    }

    /**
     * Feature 3: Gemini AI Project Architect -> Generates Project + Headings + To-Dos and navigates to it.
     */
    fun submitGeminiProjectArchitect(goalPrompt: String, locale: Locale = Locale.getDefault()) {
        if (goalPrompt.isBlank()) return
        safeVmScope.launch {
            _isGeminiBusy.value = true
            _geminiErrorMessage.value = null
            try {
                val blueprint = geminiService.generateProjectBlueprint(goalPrompt, locale)
                val projectId = repository.createProjectFromBlueprint(
                    title = blueprint.projectTitle,
                    notes = blueprint.projectNotes,
                    headingsWithTasks = blueprint.headings.map { it.title to it.tasks }
                )
                _isGeminiModalOpen.value = false
                _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(projectId)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Gemini Project Architect failed", t)
                val msg = t.localizedMessage ?: t.message ?: "Projekt-Erstellung mit Gemini fehlgeschlagen."
                _geminiErrorMessage.value = msg
                _uiErrorMessage.value = msg
            } finally {
                _isGeminiBusy.value = false
            }
        }
    }

    /**
     * Feature 4: Gemini Logbook Reflection -> Summarizes completed tasks & offers 1-tap focus tasks for Today.
     */
    fun generateGeminiLogbookReflection(locale: Locale = Locale.getDefault()) {
        safeVmScope.launch {
            _isGeminiBusy.value = true
            _geminiErrorMessage.value = null
            try {
                val completedTitles = logbookTasks.value.map { it.title }
                _logbookAiReview.value = geminiService.generateLogbookReflection(completedTitles, locale)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Gemini Logbook Reflection failed", t)
                val msg = t.localizedMessage ?: t.message ?: "Logbuch-Reflexion fehlgeschlagen."
                _geminiErrorMessage.value = msg
                _uiErrorMessage.value = msg
            } finally {
                _isGeminiBusy.value = false
            }
        }
    }

    fun adoptSuggestedFocusTaskForToday(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        safeVmScope.launch {
            val zone = ZoneId.systemDefault()
            val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            repository.createOrUpdateTask(
                title = trimmed,
                scheduleState = ScheduleState.TODAY,
                whenDate = todayEpoch,
                sortOrder = 250.0
            )
        }
    }

    fun triggerManualCloudSync() {
        if (_isLocalOnlyDbMode.value) {
            _uiErrorMessage.value = "Lokaler Datenbank-Modus ist aktiv. Wechsle in den Einstellungen auf Cloud-Sync, um zu synchronisieren."
            return
        }
        safeVmScope.launch {
            syncRepository.syncBidirectionalDeltas().onFailure { err ->
                Log.w(TAG, "Manual cloud sync failed", err)
                _uiErrorMessage.value = err.localizedMessage ?: err.message ?: "Cloud-Synchronisierung fehlgeschlagen."
            }
        }
    }

    fun setAuthModalOpen(open: Boolean) {
        _authErrorMessage.value = null
        _authInfoMessage.value = null
        _isAuthModalOpen.value = open
    }

    /**
     * Multi-Device User Registration (Email & Password):
     * If initial sample tasks are still present, asks the user first whether to merge current tasks
     * (after deleting sample tasks) or load only account tasks.
     */
    fun registerWithEmailPassword(email: String, password: String) {
        safeVmScope.launch {
            if (email.isNotBlank() && password.isNotBlank() && repository.hasSampleTasks()) {
                _pendingMergeNonSampleCount.value = repository.countNonSampleTasks()
                _pendingMergeAuthRequest.value = PendingCloudAuthRequest.RegisterEmail(email.trim(), password)
                _isAuthModalOpen.value = false
                return@launch
            }
            executeCloudAuthRequestDirect(PendingCloudAuthRequest.RegisterEmail(email, password))
        }
    }

    /**
     * Multi-Device User Sign-In (Email & Password):
     * If initial sample tasks are still present, asks the user first whether to merge current tasks
     * (after deleting sample tasks) or load only account tasks.
     */
    fun signInWithEmailPassword(email: String, password: String) {
        safeVmScope.launch {
            if (email.isNotBlank() && password.isNotBlank() && repository.hasSampleTasks()) {
                _pendingMergeNonSampleCount.value = repository.countNonSampleTasks()
                _pendingMergeAuthRequest.value = PendingCloudAuthRequest.SignInEmail(email.trim(), password)
                _isAuthModalOpen.value = false
                return@launch
            }
            executeCloudAuthRequestDirect(PendingCloudAuthRequest.SignInEmail(email, password))
        }
    }

    /**
     * Sends a password reset email via Firebase Auth (`sendPasswordResetEmail`).
     */
    fun sendPasswordReset(email: String) {
        safeVmScope.launch {
            _isSigningIn.value = true
            _authErrorMessage.value = null
            _authInfoMessage.value = null
            try {
                syncRepository.sendPasswordResetEmail(email)
                    .onSuccess { msg ->
                        _authInfoMessage.value = msg
                    }
                    .onFailure { err ->
                        _authErrorMessage.value = err.localizedMessage ?: err.message
                    }
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    /**
     * One-Tap Google Cloud Sign-In ("Weiter mit Google").
     * If initial sample tasks are still present, asks the user first whether to merge current tasks
     * (after deleting sample tasks) or load only account tasks.
     */
    fun signInWithGoogleAccount(optionalEmail: String? = null) {
        safeVmScope.launch {
            if (repository.hasSampleTasks()) {
                _pendingMergeNonSampleCount.value = repository.countNonSampleTasks()
                _pendingMergeAuthRequest.value = PendingCloudAuthRequest.SignInGoogle(optionalEmail)
                _isAuthModalOpen.value = false
                return@launch
            }
            executeCloudAuthRequestDirect(PendingCloudAuthRequest.SignInGoogle(optionalEmail))
        }
    }

    fun signInAnonymouslyAndSync() {
        safeVmScope.launch {
            if (repository.hasSampleTasks()) {
                _pendingMergeNonSampleCount.value = repository.countNonSampleTasks()
                _pendingMergeAuthRequest.value = PendingCloudAuthRequest.SignInAnonymous
                _isAuthModalOpen.value = false
                return@launch
            }
            executeCloudAuthRequestDirect(PendingCloudAuthRequest.SignInAnonymous)
        }
    }

    private suspend fun executeCloudAuthRequestDirect(
        request: PendingCloudAuthRequest,
        reopenAuthModalOnError: Boolean = false
    ) {
        _isSigningIn.value = true
        _authErrorMessage.value = null
        _authInfoMessage.value = null
        try {
            if (_isLocalOnlyDbMode.value) setLocalOnlyDbMode(false)
            val result = when (request) {
                is PendingCloudAuthRequest.RegisterEmail ->
                    syncRepository.registerAccount(request.email, request.password)
                is PendingCloudAuthRequest.SignInEmail ->
                    syncRepository.signInAccount(request.email, request.password)
                is PendingCloudAuthRequest.SignInGoogle ->
                    syncRepository.signInInstantCloudAccount(request.optionalEmail)
                PendingCloudAuthRequest.SignInAnonymous ->
                    syncRepository.signInInstantCloudAccount(null)
            }
            result
                .onSuccess {
                    _isAuthModalOpen.value = false
                    dismissInitialSetupBanner()
                }
                .onFailure { err ->
                    _authErrorMessage.value = err.localizedMessage ?: err.message
                    if (reopenAuthModalOnError) {
                        _isAuthModalOpen.value = true
                    }
                }
        } finally {
            _isSigningIn.value = false
        }
    }

    fun signOutCloud() {
        safeVmScope.launch {
            syncRepository.signOutAccount()
        }
    }

    fun scheduleTaskReminder(task: TaskItem, triggerAtMillis: Long) {
        safeVmScope.launch {
            repository.createOrUpdateTask(
                id = task.id,
                title = task.title,
                notes = task.notes,
                status = task.status,
                scheduleState = task.scheduleState,
                whenDate = task.whenDate,
                deadline = task.deadline,
                reminderTime = triggerAtMillis,
                projectId = task.projectId,
                headingId = task.headingId,
                areaId = task.areaId,
                recurrenceRule = task.recurrenceRule,
                sortOrder = task.sortOrder,
                tagIds = task.tags.map { it.id }
            )
            reminderScheduler.scheduleExactReminder(task.id, task.title, triggerAtMillis)
        }
    }

    fun updateTaskScheduleWithReminder(
        task: TaskItem,
        newState: ScheduleState,
        newWhenDate: Long?,
        newReminderTime: Long?
    ) {
        safeVmScope.launch {
            repository.createOrUpdateTask(
                id = task.id,
                title = task.title,
                notes = task.notes,
                status = task.status,
                scheduleState = newState,
                whenDate = newWhenDate,
                deadline = task.deadline,
                reminderTime = newReminderTime,
                projectId = task.projectId,
                headingId = task.headingId,
                areaId = task.areaId,
                recurrenceRule = task.recurrenceRule,
                sortOrder = task.sortOrder,
                tagIds = task.tags.map { it.id }
            )
            if (newReminderTime != null) {
                reminderScheduler.scheduleExactReminder(task.id, task.title, newReminderTime)
            } else {
                reminderScheduler.cancelReminder(task.id)
            }
        }
    }

    // =========================================================================
    // Full Tag Management & Task Tag Assignment
    // =========================================================================

    fun setTagManagerOpen(open: Boolean) {
        _isTagManagerOpen.value = open
    }

    fun selectFilterTag(tagId: String?) {
        _selectedFilterTagId.value = if (_selectedFilterTagId.value == tagId) null else tagId
    }

    fun createTag(title: String, shortcut: String?) {
        safeVmScope.launch {
            repository.createOrUpdateTag(title = title, shortcut = shortcut)
        }
    }

    fun renameTag(tagId: String, newTitle: String, shortcut: String?) {
        safeVmScope.launch {
            repository.createOrUpdateTag(id = tagId, title = newTitle, shortcut = shortcut)
        }
    }

    fun deleteTag(tagId: String) {
        safeVmScope.launch {
            if (_selectedFilterTagId.value == tagId) {
                _selectedFilterTagId.value = null
            }
            repository.softDeleteTag(tagId)
        }
    }

    fun updateTaskTags(task: TaskItem, newTagIds: List<String>) {
        safeVmScope.launch {
            repository.createOrUpdateTask(
                id = task.id,
                title = task.title,
                notes = task.notes,
                status = task.status,
                scheduleState = task.scheduleState,
                whenDate = task.whenDate,
                deadline = task.deadline,
                reminderTime = task.reminderTime,
                projectId = task.projectId,
                headingId = task.headingId,
                areaId = task.areaId,
                recurrenceRule = task.recurrenceRule,
                sortOrder = task.sortOrder,
                tagIds = newTagIds
            )
        }
    }

    fun createAndAssignTagToTask(task: TaskItem, newTagTitle: String) {
        safeVmScope.launch {
            val newId = repository.createOrUpdateTag(title = newTagTitle)
            val updatedTagIds = (task.tags.map { it.id } + newId).distinct()
            repository.createOrUpdateTask(
                id = task.id,
                title = task.title,
                notes = task.notes,
                status = task.status,
                scheduleState = task.scheduleState,
                whenDate = task.whenDate,
                deadline = task.deadline,
                reminderTime = task.reminderTime,
                projectId = task.projectId,
                headingId = task.headingId,
                areaId = task.areaId,
                recurrenceRule = task.recurrenceRule,
                sortOrder = task.sortOrder,
                tagIds = updatedTagIds
            )
        }
    }

    // =========================================================================
    // Daily Active Tasks Notification Settings
    // =========================================================================

    fun setNotificationSettingsOpen(open: Boolean) {
        if (open) {
            runCatching {
                _dailyDigestConfig.value = reminderScheduler.getDailyDigestConfig()
            }.onFailure { err ->
                Log.w(TAG, "Failed loading daily digest config", err)
            }
        }
        _isNotificationSettingsOpen.value = open
    }

    fun updateDailyDigestConfig(enabled: Boolean, hour: Int, minute: Int) {
        runCatching {
            reminderScheduler.updateDailyDigestSchedule(enabled, hour, minute)
            _dailyDigestConfig.value = reminderScheduler.getDailyDigestConfig()
        }.onFailure { err ->
            Log.w(TAG, "Failed updating daily digest config", err)
            _uiErrorMessage.value = err.localizedMessage ?: "Benachrichtigungen konnten nicht gespeichert werden."
        }
    }

    fun sendTestDailyDigestNotificationNow() {
        runCatching {
            reminderScheduler.triggerDailyDigestNotificationNow()
        }.onFailure { err ->
            Log.w(TAG, "Failed triggering test notification", err)
            _uiErrorMessage.value = err.localizedMessage ?: "Test-Benachrichtigung fehlgeschlagen."
        }
    }

    // =========================================================================
    // v1.3.0 / v1.4.0: Settings Modal, Collapsible Sidebar, Local/Cloud DB Mode,
    // Live Task Statistics, and Off-Main-Thread CSV Import/Export/Share
    // =========================================================================

    private val settingsPrefs = appContext.getSharedPreferences("thingsbook_ui_prefs", Context.MODE_PRIVATE)
    private val coreSettingsPrefs = appContext.getSharedPreferences("thingsbook_settings_prefs", Context.MODE_PRIVATE)

    private val _isSettingsModalOpen = MutableStateFlow(false)
    val isSettingsModalOpen: StateFlow<Boolean> = _isSettingsModalOpen.asStateFlow()

    private val _isSidebarVisible = MutableStateFlow(settingsPrefs.getBoolean("sidebar_visible", true))
    val isSidebarVisible: StateFlow<Boolean> = _isSidebarVisible.asStateFlow()

    private val _isLocalOnlyDbMode = MutableStateFlow(
        settingsPrefs.getBoolean("local_only_db_mode", false) ||
            coreSettingsPrefs.getBoolean("local_only_db_mode", false)
    )
    val isLocalOnlyDbMode: StateFlow<Boolean> = _isLocalOnlyDbMode.asStateFlow()

    // v1.5.0: Configurable "Heute" count badge on Thingsbook symbol (in-app & launcher/shelf badge)
    private val _showTodayAppBadge = MutableStateFlow(settingsPrefs.getBoolean("show_today_app_badge", true))
    val showTodayAppBadge: StateFlow<Boolean> = _showTodayAppBadge.asStateFlow()

    // v1.5.0: Initial setup sample task detection & Cloud Account Merge prompt
    val hasSampleTasks: StateFlow<Boolean> = repository.observeHasSampleTasks()
        .catch { emit(false) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isInitialSetupBannerDismissed = MutableStateFlow(
        settingsPrefs.getBoolean("initial_setup_banner_dismissed", false)
    )
    val isInitialSetupBannerDismissed: StateFlow<Boolean> = _isInitialSetupBannerDismissed.asStateFlow()

    private val _pendingMergeAuthRequest = MutableStateFlow<PendingCloudAuthRequest?>(null)
    val pendingMergeAuthRequest: StateFlow<PendingCloudAuthRequest?> = _pendingMergeAuthRequest.asStateFlow()

    private val _pendingMergeNonSampleCount = MutableStateFlow(0)
    val pendingMergeNonSampleCount: StateFlow<Int> = _pendingMergeNonSampleCount.asStateFlow()

    // v1.5.0: Expandable Magic Plus (+) context menu & quick-create dialogs
    private val _isMagicPlusMenuOpen = MutableStateFlow(false)
    val isMagicPlusMenuOpen: StateFlow<Boolean> = _isMagicPlusMenuOpen.asStateFlow()

    private val _isQuickCreateAreaDialogOpen = MutableStateFlow(false)
    val isQuickCreateAreaDialogOpen: StateFlow<Boolean> = _isQuickCreateAreaDialogOpen.asStateFlow()

    private val _isMagicPlusCreateProjectOpen = MutableStateFlow(false)
    val isMagicPlusCreateProjectOpen: StateFlow<Boolean> = _isMagicPlusCreateProjectOpen.asStateFlow()

    private val _csvStatusMessage = MutableStateFlow<String?>(null)
    val csvStatusMessage: StateFlow<String?> = _csvStatusMessage.asStateFlow()

    val statistics: StateFlow<ThingsbookStatistics> = combine(
        repository.observeAllTasksRaw(),
        activeProjects,
        areas,
        allTags
    ) { allRawTasks, projectsList, areasList, tagsList ->
        runCatching {
            val nonDeleted = allRawTasks.filter { !it.isDeleted }
            val deletedCount = allRawTasks.count { it.isDeleted }
            val openTasks = nonDeleted.filter { it.status == TaskStatus.OPEN }
            val completedCount = nonDeleted.count { it.status == TaskStatus.COMPLETED }
            val canceledCount = nonDeleted.count { it.status == TaskStatus.CANCELED }

            val todayEndMillis = LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() - 1L

            val todayCount = openTasks.count { t ->
                t.scheduleState == ScheduleState.TODAY ||
                    t.scheduleState == ScheduleState.THIS_EVENING ||
                    (t.whenDate != null && t.whenDate <= todayEndMillis)
            }
            val upcomingCount = openTasks.count { t ->
                t.scheduleState == ScheduleState.UPCOMING ||
                    (t.whenDate != null && t.whenDate > todayEndMillis)
            }
            val inboxCount = openTasks.count { t ->
                t.scheduleState == ScheduleState.INBOX && t.projectId == null && t.areaId == null && t.whenDate == null
            }
            val anytimeCount = openTasks.count { t ->
                t.scheduleState == ScheduleState.ANYTIME && t.whenDate == null && t.recurrenceRuleJson.isNullOrBlank()
            }
            val somedayCount = openTasks.count { t ->
                t.scheduleState == ScheduleState.SOMEDAY
            }
            val recurringCount = openTasks.count { t ->
                !t.recurrenceRuleJson.isNullOrBlank()
            }

            ThingsbookStatistics(
                totalRecordsCount = allRawTasks.size,
                openTasksCount = openTasks.size,
                todayTasksCount = todayCount,
                upcomingTasksCount = upcomingCount,
                anytimeTasksCount = anytimeCount,
                inboxTasksCount = inboxCount,
                somedayTasksCount = somedayCount,
                recurringTasksCount = recurringCount,
                completedTasksCount = completedCount,
                canceledTasksCount = canceledCount,
                deletedTasksCount = deletedCount,
                activeProjectsCount = projectsList.size,
                areasCount = areasList.size,
                tagsCount = tagsList.size
            )
        }.getOrDefault(ThingsbookStatistics())
    }
        .catch { emit(ThingsbookStatistics()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), ThingsbookStatistics())

    fun setSettingsModalOpen(open: Boolean) {
        _isSettingsModalOpen.value = open
    }

    fun setShowTodayAppBadge(enabled: Boolean) {
        _showTodayAppBadge.value = enabled
        runCatching { settingsPrefs.edit().putBoolean("show_today_app_badge", enabled).apply() }
        val currentTodayCount = todayDaytimeTasks.value.size + thisEveningTasks.value.size
        AppIconBadgeUpdater.updateTodayAppBadge(appContext, currentTodayCount, enabled)
    }

    fun setMagicPlusMenuOpen(open: Boolean) {
        _isMagicPlusMenuOpen.value = open
    }

    fun setQuickCreateAreaDialogOpen(open: Boolean) {
        _isQuickCreateAreaDialogOpen.value = open
    }

    fun setMagicPlusCreateProjectOpen(open: Boolean) {
        _isMagicPlusCreateProjectOpen.value = open
    }

    fun dismissInitialSetupBanner() {
        _isInitialSetupBannerDismissed.value = true
        runCatching { settingsPrefs.edit().putBoolean("initial_setup_banner_dismissed", true).apply() }
    }

    fun deleteSampleTasksForLocalSetup() {
        safeVmScope.launch {
            runCatchingCancellable {
                repository.purgeSampleDataLocally(markRemainingDirtyForMerge = false)
            }.onSuccess {
                dismissInitialSetupBanner()
                runCatching {
                    com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.refreshAllWidgets(appContext)
                }
            }.onFailure { err ->
                Log.w(TAG, "Failed purging sample tasks", err)
                _uiErrorMessage.value = err.localizedMessage ?: "Beispielaufgaben konnten nicht gelöscht werden."
            }
        }
    }

    fun confirmAccountSetupMerge(mergeCurrentTasks: Boolean) {
        val pendingReq = _pendingMergeAuthRequest.value ?: return
        _pendingMergeAuthRequest.value = null
        safeVmScope.launch {
            runCatchingCancellable {
                if (mergeCurrentTasks) {
                    repository.purgeSampleDataLocally(markRemainingDirtyForMerge = true)
                } else {
                    repository.purgeAllLocalDataBeforeAccountPull()
                }
            }.onFailure { err ->
                Log.w(TAG, "Failed preparing local tasks before account merge", err)
            }
            executeCloudAuthRequestDirect(pendingReq, reopenAuthModalOnError = true)
        }
    }

    fun cancelPendingAccountSetupMerge() {
        _pendingMergeAuthRequest.value = null
        _isAuthModalOpen.value = true
    }

    fun toggleSidebarVisible() {
        val next = !_isSidebarVisible.value
        _isSidebarVisible.value = next
        runCatching { settingsPrefs.edit().putBoolean("sidebar_visible", next).apply() }
    }

    fun setSidebarVisible(visible: Boolean) {
        _isSidebarVisible.value = visible
        runCatching { settingsPrefs.edit().putBoolean("sidebar_visible", visible).apply() }
    }

    fun setLocalOnlyDbMode(localOnly: Boolean) {
        _isLocalOnlyDbMode.value = localOnly
        runCatching {
            settingsPrefs.edit().putBoolean("local_only_db_mode", localOnly).apply()
            coreSettingsPrefs.edit().putBoolean("local_only_db_mode", localOnly).apply()
        }
        if (localOnly) {
            safeVmScope.launch {
                runCatchingCancellable { syncRepository.signOutAccount() }
            }
        }
    }

    fun clearCsvStatusMessage() {
        _csvStatusMessage.value = null
    }

    fun exportTasksCsv(onCsvReady: (String) -> Unit) {
        safeVmScope.launch {
            runCatchingCancellable {
                repository.exportTasksToCsv()
            }.onSuccess { csv ->
                _csvStatusMessage.value = "✅ CSV-Export erfolgreich erstellt (${statistics.value.openTasksCount + statistics.value.completedTasksCount} Aufgaben)."
                onCsvReady(csv)
            }.onFailure { err ->
                val msg = "❌ CSV-Export fehlgeschlagen: ${err.localizedMessage ?: err.message}"
                _csvStatusMessage.value = msg
                _uiErrorMessage.value = msg
            }
        }
    }

    fun exportTasksCsvToUri(uri: Uri) {
        safeVmScope.launch {
            runCatchingCancellable {
                val csvContent = repository.exportTasksToCsv()
                withContext(Dispatchers.IO) {
                    val outputStream = runCatching {
                        appContext.contentResolver.openOutputStream(uri, "wt")
                    }.getOrNull() ?: appContext.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Zieldatei konnte nicht zum Schreiben geöffnet werden.")
                    outputStream.use { out ->
                        out.write(csvContent.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
            }.onSuccess {
                val totalExported = statistics.value.openTasksCount + statistics.value.completedTasksCount
                _csvStatusMessage.value = "✅ CSV-Datei erfolgreich gespeichert ($totalExported Aufgaben)."
            }.onFailure { err ->
                Log.w(TAG, "CSV export to URI failed", err)
                val msg = "❌ CSV-Export fehlgeschlagen: ${err.localizedMessage ?: err.message}"
                _csvStatusMessage.value = msg
                _uiErrorMessage.value = msg
            }
        }
    }

    fun shareTasksCsv() {
        safeVmScope.launch {
            runCatchingCancellable {
                val csvContent = repository.exportTasksToCsv()
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Thingsbook Aufgaben Export (${LocalDate.now()})")
                    putExtra(Intent.EXTRA_TEXT, csvContent)
                }
                val chooser = Intent.createChooser(sendIntent, "CSV teilen").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(chooser)
            }.onSuccess {
                _csvStatusMessage.value = "✅ CSV-Export zum Teilen geöffnet."
            }.onFailure { err ->
                Log.w(TAG, "Sharing CSV failed", err)
                val msg = "❌ Teilen fehlgeschlagen: ${err.localizedMessage ?: err.message}"
                _csvStatusMessage.value = msg
                _uiErrorMessage.value = msg
            }
        }
    }

    fun importTasksCsv(csvContent: String) {
        safeVmScope.launch {
            runCatchingCancellable {
                repository.importTasksFromCsv(csvContent)
            }.onSuccess { count ->
                _csvStatusMessage.value = "✅ $count Aufgaben erfolgreich aus der CSV-Datei importiert!"
            }.onFailure { err ->
                val msg = "❌ CSV-Import fehlgeschlagen: ${err.localizedMessage ?: err.message}"
                _csvStatusMessage.value = msg
                _uiErrorMessage.value = msg
            }
        }
    }

    fun importTasksCsvFromUri(uri: Uri) {
        safeVmScope.launch {
            runCatchingCancellable {
                val csvText = withContext(Dispatchers.IO) {
                    val inputStream = appContext.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Ausgewählte CSV-Datei konnte nicht geöffnet werden.")
                    inputStream.use { stream ->
                        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                        val sb = StringBuilder()
                        val buffer = CharArray(8192)
                        var totalChars = 0
                        while (true) {
                            val read = reader.read(buffer)
                            if (read == -1) break
                            totalChars += read
                            if (totalChars > MAX_CSV_IMPORT_CHARS) {
                                throw IllegalArgumentException("Die ausgewählte CSV-Datei ist zu groß (maximal 10 MB).")
                            }
                            sb.append(buffer, 0, read)
                        }
                        sb.toString()
                    }
                }
                repository.importTasksFromCsv(csvText)
            }.onSuccess { count ->
                _csvStatusMessage.value = "✅ $count Aufgaben erfolgreich aus der CSV-Datei importiert!"
            }.onFailure { err ->
                Log.w(TAG, "CSV import from URI failed", err)
                val msg = "❌ CSV-Import fehlgeschlagen: ${err.localizedMessage ?: err.message}"
                _csvStatusMessage.value = msg
                _uiErrorMessage.value = msg
            }
        }
    }

    // =========================================================================
    // v1.6.0: Full Backup & Restore (.tbbackup with optional AES-256-GCM Password)
    // =========================================================================

    private val _isBackupCreateDialogOpen = MutableStateFlow(false)
    val isBackupCreateDialogOpen: StateFlow<Boolean> = _isBackupCreateDialogOpen.asStateFlow()

    private val _pendingRestoreSession = MutableStateFlow<PendingRestoreSession?>(null)
    val pendingRestoreSession: StateFlow<PendingRestoreSession?> = _pendingRestoreSession.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val _restoreDialogError = MutableStateFlow<String?>(null)
    val restoreDialogError: StateFlow<String?> = _restoreDialogError.asStateFlow()

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    private var stagedBackupExportPassword: CharArray? = null

    fun setBackupCreateDialogOpen(open: Boolean) {
        if (!open) {
            clearStagedBackupPassword()
        }
        _isBackupCreateDialogOpen.value = open
    }

    fun clearBackupStatusMessage() {
        _backupStatusMessage.value = null
    }

    fun stagePasswordForBackupFileSave(password: String?) {
        clearStagedBackupPassword()
        stagedBackupExportPassword = password?.takeIf { it.isNotEmpty() }?.toCharArray()
    }

    fun clearStagedBackupPassword() {
        stagedBackupExportPassword?.fill('\u0000')
        stagedBackupExportPassword = null
    }

    private fun currentBackupPreferencesSnapshot(): BackupPreferencesDto {
        val digest = runCatching { reminderScheduler.getDailyDigestConfig() }.getOrNull()
        val langTag = runCatching { AppCompatDelegate.getApplicationLocales().toLanguageTags() }.getOrDefault("")
        return BackupPreferencesDto(
            sidebarVisible = _isSidebarVisible.value,
            showTodayAppBadge = _showTodayAppBadge.value,
            dailyDigestEnabled = digest?.enabled,
            dailyDigestHour = digest?.hour,
            dailyDigestMinute = digest?.minute,
            languageTag = langTag
        )
    }

    fun exportFullBackupToUri(uri: Uri) {
        val pwdChars = stagedBackupExportPassword
        stagedBackupExportPassword = null
        safeVmScope.launch {
            _isBackupBusy.value = true
            try {
                runCatchingCancellable {
                    val prefsSnapshot = currentBackupPreferencesSnapshot()
                    withContext(Dispatchers.IO) {
                        val (backupBytes, summary) = repository.createFullBackupBytes(
                            password = pwdChars,
                            appVersionName = BuildConfig.VERSION_NAME,
                            appVersionCode = BuildConfig.VERSION_CODE,
                            preferences = prefsSnapshot
                        )
                        val outputStream = runCatching {
                            appContext.contentResolver.openOutputStream(uri, "wt")
                        }.getOrNull() ?: appContext.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Zieldatei für das Backup konnte nicht geöffnet werden.")
                        outputStream.use { out ->
                            out.write(backupBytes)
                            out.flush()
                        }
                        summary
                    }
                }.onSuccess { summary ->
                    _isBackupCreateDialogOpen.value = false
                    val lockBadge = if (summary.encrypted) "🔒 Passwortgeschützt" else "Unverschlüsselt"
                    _backupStatusMessage.value = "✅ Backup gespeichert ($lockBadge): ${summary.tasksCount} Aufgaben, ${summary.projectsCount} Projekte, ${summary.areasCount} Bereiche & ${summary.tagsCount} Tags."
                }.onFailure { err ->
                    Log.w(TAG, "Full backup export failed", err)
                    val msg = "❌ Backup fehlgeschlagen: ${err.localizedMessage ?: err.message}"
                    _backupStatusMessage.value = msg
                    _uiErrorMessage.value = msg
                }
            } finally {
                pwdChars?.fill('\u0000')
                _isBackupBusy.value = false
            }
        }
    }

    fun shareFullBackup(password: String?) {
        val pwdChars = password?.takeIf { it.isNotEmpty() }?.toCharArray()
        safeVmScope.launch {
            _isBackupBusy.value = true
            try {
                runCatchingCancellable {
                    val prefsSnapshot = currentBackupPreferencesSnapshot()
                    val (shareUri, summary) = withContext(Dispatchers.IO) {
                        val (backupBytes, sum) = repository.createFullBackupBytes(
                            password = pwdChars,
                            appVersionName = BuildConfig.VERSION_NAME,
                            appVersionCode = BuildConfig.VERSION_CODE,
                            preferences = prefsSnapshot
                        )
                        val backupDir = File(appContext.cacheDir, "backups").apply { mkdirs() }
                        backupDir.listFiles()?.forEach { old -> runCatching { old.delete() } }
                        val suffix = if (sum.encrypted) "-encrypted" else ""
                        val outFile = File(backupDir, "thingsbook-backup-${LocalDate.now()}$suffix.tbbackup")
                        outFile.writeBytes(backupBytes)
                        val contentUri = FileProvider.getUriForFile(
                            appContext,
                            "${appContext.packageName}.fileprovider",
                            outFile
                        )
                        contentUri to sum
                    }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_SUBJECT, "Thingsbook Backup (${LocalDate.now()})")
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(sendIntent, "Thingsbook Backup teilen").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    appContext.startActivity(chooser)
                    summary
                }.onSuccess { summary ->
                    _isBackupCreateDialogOpen.value = false
                    val lockBadge = if (summary.encrypted) "🔒 passwortgeschützt" else "unverschlüsselt"
                    _backupStatusMessage.value = "✅ Backup ($lockBadge) zum Teilen geöffnet."
                }.onFailure { err ->
                    Log.w(TAG, "Sharing full backup failed", err)
                    val msg = "❌ Backup-Teilen fehlgeschlagen: ${err.localizedMessage ?: err.message}"
                    _backupStatusMessage.value = msg
                    _uiErrorMessage.value = msg
                }
            } finally {
                pwdChars?.fill('\u0000')
                _isBackupBusy.value = false
            }
        }
    }

    fun inspectBackupUriForRestore(uri: Uri) {
        safeVmScope.launch {
            _isBackupBusy.value = true
            _restoreDialogError.value = null
            try {
                runCatchingCancellable {
                    withContext(Dispatchers.IO) {
                        val fileNameHint = runCatching {
                            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { cursor ->
                                    if (cursor.moveToFirst()) cursor.getString(0) else null
                                }
                        }.getOrNull()

                        val inputStream = appContext.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Ausgewählte Backup-Datei konnte nicht geöffnet werden.")
                        val rawBytes = inputStream.use { stream ->
                            val out = ByteArrayOutputStream()
                            val buf = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val read = stream.read(buf)
                                if (read == -1) break
                                total += read
                                if (total > MAX_BACKUP_FILE_BYTES) {
                                    throw IllegalArgumentException("Die Backup-Datei ist zu groß (maximal 30 MB).")
                                }
                                out.write(buf, 0, read)
                            }
                            out.toByteArray()
                        }
                        val inspection = repository.inspectBackupBytes(rawBytes)
                        PendingRestoreSession(
                            rawBytes = rawBytes,
                            fileNameHint = fileNameHint,
                            inspection = inspection
                        )
                    }
                }.onSuccess { session ->
                    _pendingRestoreSession.value = session
                }.onFailure { err ->
                    Log.w(TAG, "Backup inspection failed", err)
                    val msg = "❌ Backup-Datei konnte nicht geladen werden: ${err.localizedMessage ?: err.message}"
                    _backupStatusMessage.value = msg
                    _uiErrorMessage.value = msg
                }
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun cancelPendingRestore() {
        if (_isBackupBusy.value) return
        _pendingRestoreSession.value = null
        _restoreDialogError.value = null
    }

    fun confirmRestoreFromPendingBackup(password: String?) {
        val session = _pendingRestoreSession.value ?: return
        val pwdChars = password?.takeIf { it.isNotEmpty() }?.toCharArray()
        safeVmScope.launch {
            _isBackupBusy.value = true
            _restoreDialogError.value = null
            try {
                runCatchingCancellable {
                    withContext(Dispatchers.IO) {
                        repository.restoreFullBackupFromBytes(
                            rawBytes = session.rawBytes,
                            password = pwdChars,
                            syncAfterRestore = !_isLocalOnlyDbMode.value
                        )
                    }
                }.onSuccess { (summary, restoredPrefs) ->
                    _pendingRestoreSession.value = null
                    _restoreDialogError.value = null
                    _expandedTaskId.value = null
                    _selectedTaskIds.value = emptySet()
                    _selectedFilterTagId.value = null
                    _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.TODAY)

                    restoredPrefs.sidebarVisible?.let { setSidebarVisible(it) }
                    restoredPrefs.showTodayAppBadge?.let { setShowTodayAppBadge(it) }
                    if (
                        restoredPrefs.dailyDigestEnabled != null &&
                        restoredPrefs.dailyDigestHour != null &&
                        restoredPrefs.dailyDigestMinute != null
                    ) {
                        updateDailyDigestConfig(
                            restoredPrefs.dailyDigestEnabled,
                            restoredPrefs.dailyDigestHour.coerceIn(0, 23),
                            restoredPrefs.dailyDigestMinute.coerceIn(0, 59)
                        )
                    }
                    if (!repository.hasSampleTasks()) {
                        dismissInitialSetupBanner()
                    }
                    runCatching {
                        com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.refreshAllWidgets(appContext)
                    }

                    _backupStatusMessage.value =
                        "✅ Komplettes Restore erfolgreich: ${summary.tasksCount} Aufgaben, ${summary.projectsCount} Projekte, ${summary.areasCount} Bereiche & ${summary.tagsCount} Tags wiederhergestellt!"
                }.onFailure { err ->
                    Log.w(TAG, "Full restore failed", err)
                    if (err is InvalidBackupPasswordException || err is BackupPasswordRequiredException) {
                        _restoreDialogError.value = err.localizedMessage ?: "Falsches Passwort."
                    } else {
                        val msg = err.localizedMessage ?: err.message ?: "Restore fehlgeschlagen."
                        _restoreDialogError.value = msg
                        _backupStatusMessage.value = "❌ Restore fehlgeschlagen: $msg"
                    }
                }
            } finally {
                pwdChars?.fill('\u0000')
                _isBackupBusy.value = false
            }
        }
    }

    // =========================================================================
    // Multi-Task Selection (Mouse Ctrl/Shift-Click & Things-for-iPhone Swipe)
    // =========================================================================

    private val _selectedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTaskIds: StateFlow<Set<String>> = _selectedTaskIds.asStateFlow()

    private var lastSelectedAnchorTaskId: String? = null

    fun toggleTaskSelection(taskId: String) {
        val current = _selectedTaskIds.value
        if (taskId in current) {
            _selectedTaskIds.value = current - taskId
        } else {
            _selectedTaskIds.value = current + taskId
            lastSelectedAnchorTaskId = taskId
        }
    }

    fun selectTaskRange(visibleTaskIds: List<String>, targetTaskId: String) {
        val anchor = lastSelectedAnchorTaskId
        if (anchor == null || anchor !in visibleTaskIds || targetTaskId !in visibleTaskIds) {
            toggleTaskSelection(targetTaskId)
            return
        }
        val idx1 = visibleTaskIds.indexOf(anchor)
        val idx2 = visibleTaskIds.indexOf(targetTaskId)
        val rangeIds = visibleTaskIds.subList(minOf(idx1, idx2), maxOf(idx1, idx2) + 1).toSet()
        _selectedTaskIds.value = _selectedTaskIds.value + rangeIds
        lastSelectedAnchorTaskId = targetTaskId
    }

    fun selectAllTasks(visibleTaskIds: List<String>) {
        _selectedTaskIds.value = visibleTaskIds.toSet()
    }

    fun clearTaskSelection() {
        _selectedTaskIds.value = emptySet()
        lastSelectedAnchorTaskId = null
    }

    fun batchCompleteSelectedTasks() {
        val ids = _selectedTaskIds.value.toList()
        if (ids.isEmpty()) return
        safeVmScope.launch {
            ids.forEach { id ->
                repository.updateTaskStatus(id, TaskStatus.COMPLETED)
            }
            _selectedTaskIds.value = emptySet()
        }
    }

    fun batchDeleteSelectedTasks() {
        val ids = _selectedTaskIds.value.toList()
        if (ids.isEmpty()) return
        safeVmScope.launch {
            ids.forEach { id ->
                repository.softDeleteTask(id)
            }
            _selectedTaskIds.value = emptySet()
        }
    }

    fun batchScheduleSelectedTasks(
        allTasksInView: List<TaskItem>,
        newState: ScheduleState,
        newWhenDate: Long?
    ) {
        val ids = _selectedTaskIds.value
        if (ids.isEmpty()) return
        val targets = allTasksInView.filter { it.id in ids }
        safeVmScope.launch {
            targets.forEach { task ->
                repository.createOrUpdateTask(
                    id = task.id,
                    title = task.title,
                    notes = task.notes,
                    status = task.status,
                    scheduleState = newState,
                    whenDate = newWhenDate,
                    deadline = task.deadline,
                    reminderTime = task.reminderTime,
                    projectId = task.projectId,
                    headingId = task.headingId,
                    areaId = task.areaId,
                    recurrenceRule = task.recurrenceRule,
                    sortOrder = task.sortOrder,
                    tagIds = task.tags.map { it.id }
                )
            }
            _selectedTaskIds.value = emptySet()
        }
    }

    fun batchMoveSelectedTasks(
        allTasksInView: List<TaskItem>,
        projectId: String?,
        headingId: String?,
        areaId: String?
    ) {
        val ids = _selectedTaskIds.value
        if (ids.isEmpty()) return
        val targets = allTasksInView.filter { it.id in ids }
        safeVmScope.launch {
            targets.forEach { task ->
                repository.moveTaskToContainer(task.id, projectId, headingId, areaId)
            }
            _selectedTaskIds.value = emptySet()
        }
    }

    fun dismissTopmostOverlay(): Boolean {
        return when {
            _uiErrorMessage.value != null -> {
                _uiErrorMessage.value = null
                true
            }
            _pendingRestoreSession.value != null && !_isBackupBusy.value -> {
                cancelPendingRestore()
                true
            }
            _isBackupCreateDialogOpen.value && !_isBackupBusy.value -> {
                setBackupCreateDialogOpen(false)
                true
            }
            _pendingMergeAuthRequest.value != null -> {
                cancelPendingAccountSetupMerge()
                true
            }
            _isMagicPlusMenuOpen.value -> {
                _isMagicPlusMenuOpen.value = false
                true
            }
            _isQuickCreateAreaDialogOpen.value -> {
                _isQuickCreateAreaDialogOpen.value = false
                true
            }
            _isMagicPlusCreateProjectOpen.value -> {
                _isMagicPlusCreateProjectOpen.value = false
                true
            }
            _selectedTaskIds.value.isNotEmpty() -> {
                _selectedTaskIds.value = emptySet()
                true
            }
            _isQuickFindOpen.value -> {
                _isQuickFindOpen.value = false
                true
            }
            _isSettingsModalOpen.value -> {
                _isSettingsModalOpen.value = false
                true
            }
            _isGeminiModalOpen.value -> {
                _isGeminiModalOpen.value = false
                true
            }
            _isAuthModalOpen.value -> {
                _isAuthModalOpen.value = false
                true
            }
            _isTagManagerOpen.value -> {
                _isTagManagerOpen.value = false
                true
            }
            _isAreaManagerOpen.value -> {
                _isAreaManagerOpen.value = false
                true
            }
            _isNotificationSettingsOpen.value -> {
                _isNotificationSettingsOpen.value = false
                true
            }
            _expandedTaskId.value != null -> {
                _expandedTaskId.value = null
                true
            }
            else -> false
        }
    }

    init {
        runCatching {
            val savedKey = coreSettingsPrefs.getString("gemini_api_key", "")?.trim().orEmpty()
            if (savedKey.isNotEmpty()) {
                _geminiApiKey.value = savedKey
                geminiService.customApiKey = savedKey
            }
        }
        safeVmScope.launch {
            runCatchingCancellable {
                repository.ensureDefaultWorkAndHomeAreas()
            }.onFailure { err ->
                Log.w(TAG, "Failed ensuring default areas", err)
            }
            runCatchingCancellable {
                repository.rescheduleAllOpenTaskReminders()
            }.onFailure { err ->
                Log.w(TAG, "Failed rescheduling open task reminders", err)
            }
        }
        safeVmScope.launch {
            runCatchingCancellable {
                combine(
                    inboxTasks,
                    todayDaytimeTasks,
                    thisEveningTasks,
                    upcomingTasks,
                    combine(anytimeTasks, somedayTasks) { a, s -> a.size + s.size }
                ) { a, b, c, d, e -> a.size + b.size + c.size + d.size + e }
                    .catch { emit(0) }
                    .collect {
                        runCatching {
                            com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.refreshAllWidgets(appContext)
                        }
                    }
            }
        }
        safeVmScope.launch {
            runCatchingCancellable {
                combine(
                    todayDaytimeTasks,
                    thisEveningTasks,
                    _showTodayAppBadge
                ) { daytime, evening, enabled ->
                    Pair(daytime.size + evening.size, enabled)
                }
                    .catch { emit(Pair(0, false)) }
                    .collect { (todayCount, enabled) ->
                        AppIconBadgeUpdater.updateTodayAppBadge(
                            context = appContext,
                            todayCount = todayCount,
                            enabled = enabled
                        )
                    }
            }
        }
    }

    companion object {
        private const val TAG = "ThingsbookVM"
        private const val MAX_CSV_IMPORT_CHARS = 10 * 1024 * 1024
        private const val MAX_BACKUP_FILE_BYTES = 30 * 1024 * 1024
    }
}

data class ThingsbookStatistics(
    val totalRecordsCount: Int = 0,
    val openTasksCount: Int = 0,
    val todayTasksCount: Int = 0,
    val upcomingTasksCount: Int = 0,
    val anytimeTasksCount: Int = 0,
    val inboxTasksCount: Int = 0,
    val somedayTasksCount: Int = 0,
    val recurringTasksCount: Int = 0,
    val completedTasksCount: Int = 0,
    val canceledTasksCount: Int = 0,
    val deletedTasksCount: Int = 0,
    val activeProjectsCount: Int = 0,
    val areasCount: Int = 0,
    val tagsCount: Int = 0
)


