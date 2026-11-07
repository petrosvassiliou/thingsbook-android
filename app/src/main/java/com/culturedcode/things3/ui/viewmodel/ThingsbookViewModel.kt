package com.culturedcode.things3.ui.viewmodel

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culturedcode.things3.data.ai.GeminiLogbookReviewResponse
import com.culturedcode.things3.data.ai.ThingsbookGeminiService
import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.notifications.ReminderScheduler
import com.culturedcode.things3.data.repository.CloudSyncStatus
import com.culturedcode.things3.data.repository.SyncRepository
import com.culturedcode.things3.data.repository.ThingsRepository
import com.culturedcode.things3.domain.model.Area
import com.culturedcode.things3.domain.model.Heading
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.Tag
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.domain.model.TaskStatus
import com.culturedcode.things3.ui.components.SmartViewType
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

sealed interface ActiveNavigationTarget {
    data class SmartView(val type: SmartViewType) : ActiveNavigationTarget
    data class ProjectDetail(val projectId: String) : ActiveNavigationTarget
    data class AreaDetail(val areaId: String) : ActiveNavigationTarget
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThingsbookViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ThingsRepository,
    private val database: ThingsDatabase,
    private val syncRepository: SyncRepository,
    private val geminiService: ThingsbookGeminiService,
    private val firebaseAuth: FirebaseAuth,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    @Volatile
    private var activeAuth: FirebaseAuth = firebaseAuth

    private val _navigationTarget = MutableStateFlow<ActiveNavigationTarget>(
        ActiveNavigationTarget.SmartView(SmartViewType.TODAY)
    )
    val navigationTarget: StateFlow<ActiveNavigationTarget> = _navigationTarget.asStateFlow()

    private val _expandedTaskId = MutableStateFlow<String?>(null)
    val expandedTaskId: StateFlow<String?> = _expandedTaskId.asStateFlow()

    private val _collapsedHeadingIds = MutableStateFlow<Set<String>>(emptySet())
    val collapsedHeadingIds: StateFlow<Set<String>> = _collapsedHeadingIds.asStateFlow()

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

    val currentUserIdentifier: StateFlow<String?> = syncRepository.activeUserIdentifier

    // Core Data Streams from Room Single Source of Truth
    val inboxTasks: StateFlow<List<TaskItem>> = repository.observeInboxTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayDaytimeTasks: StateFlow<List<TaskItem>> = repository.observeTodayDaytimeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val thisEveningTasks: StateFlow<List<TaskItem>> = repository.observeThisEveningTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingTasks: StateFlow<List<TaskItem>> = repository.observeUpcomingTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val anytimeTasks: StateFlow<List<TaskItem>> = repository.observeAnytimeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val somedayTasks: StateFlow<List<TaskItem>> = repository.observeSomedayTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logbookTasks: StateFlow<List<TaskItem>> = repository.observeLogbookTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProjects: StateFlow<List<Project>> = repository.observeActiveProjectsWithProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val areas: StateFlow<List<Area>> = repository.observeAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<Tag>> = repository.observeAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncStatus: StateFlow<CloudSyncStatus> = syncRepository.syncStatus

    // Project headings & tasks when viewing a specific ProjectDetail
    val activeProjectHeadings: StateFlow<List<Heading>> = _navigationTarget
        .flatMapLatest { target ->
            if (target is ActiveNavigationTarget.ProjectDetail) {
                database.headingDao().observeHeadingsForProject(target.projectId).map { list ->
                    list.map {
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
                    }
                }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real-time FTS5 Quick Find Results
    val quickFindTaskResults: StateFlow<List<TaskItem>> = _quickFindQuery
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.searchTasksFts5(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickFindProjectResults: StateFlow<List<Project>> = _quickFindQuery
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.searchProjectsFts5(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectSmartView(viewType: SmartViewType) {
        _expandedTaskId.value = null
        _navigationTarget.value = ActiveNavigationTarget.SmartView(viewType)
    }

    fun selectProject(projectId: String) {
        _expandedTaskId.value = null
        _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(projectId)
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
        _isGeminiModalOpen.value = open
    }

    fun updateGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        geminiService.customApiKey = key
    }

    /**
     * Switches app language live via AndroidX `AppCompatDelegate.setApplicationLocales` (EN, DE, IT, FR, ES).
     */
    fun switchAppLanguage(languageTag: String) {
        val localeList = if (languageTag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun updateTaskStatus(taskId: String, status: TaskStatus) {
        viewModelScope.launch {
            repository.updateTaskStatus(taskId, status)
            if (status != TaskStatus.OPEN && _expandedTaskId.value == taskId) {
                _expandedTaskId.value = null
            }
        }
    }

    fun updateTaskTitleAndNotes(task: TaskItem, newTitle: String, newNotes: String) {
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            repository.addChecklistItem(taskId, title)
        }
    }

    fun toggleChecklistItem(itemId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleChecklistItem(itemId, isCompleted)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
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
        viewModelScope.launch {
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
                sortOrder = midpointOrder
            )

            // Expand newly dropped card inline immediately
            _expandedTaskId.value = createdId
        }
    }

    fun createHeadingInCurrentProject(dropIndex: Int, defaultHeadingTitle: String) {
        val target = _navigationTarget.value as? ActiveNavigationTarget.ProjectDetail ?: return
        viewModelScope.launch {
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

    // =========================================================================
    // 4 Multilingual Gemini AI Features (EN, DE, IT, FR, ES)
    // =========================================================================

    /**
     * Feature 1: Gemini Magic Breakdown inside an expanded To-Do card.
     */
    fun triggerGeminiMagicBreakdown(task: TaskItem, locale: Locale = Locale.getDefault()) {
        viewModelScope.launch {
            _magicBreakdownTaskId.value = task.id
            val steps = geminiService.generateMagicChecklist(
                taskTitle = task.title,
                taskNotes = task.notes,
                locale = locale
            )
            repository.addChecklistItemsBatch(task.id, steps)
            _magicBreakdownTaskId.value = null
        }
    }

    /**
     * Feature 2: Gemini Brain Dump -> Structured GTD Tasks with automatic ScheduleState, whenDate, deadline & checklist.
     */
    fun submitGeminiBrainDump(rawText: String, locale: Locale = Locale.getDefault()) {
        viewModelScope.launch {
            _isGeminiBusy.value = true
            val parsedTasks = geminiService.parseBrainDumpToTasks(rawText, locale)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)

            var lastCreatedId: String? = null
            parsedTasks.forEachIndexed { idx, dto ->
                val state = runCatching { ScheduleState.valueOf(dto.scheduleState) }
                    .getOrDefault(ScheduleState.TODAY)
                val whenEpoch = dto.whenDaysOffset?.let {
                    today.plusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
                }
                val deadlineEpoch = dto.deadlineDaysOffset?.let {
                    today.plusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
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

            _isGeminiBusy.value = false
            _isGeminiModalOpen.value = false
            lastCreatedId?.let { _expandedTaskId.value = it }
        }
    }

    /**
     * Feature 3: Gemini AI Project Architect -> Generates Project + Headings + To-Dos and navigates to it.
     */
    fun submitGeminiProjectArchitect(goalPrompt: String, locale: Locale = Locale.getDefault()) {
        viewModelScope.launch {
            _isGeminiBusy.value = true
            val blueprint = geminiService.generateProjectBlueprint(goalPrompt, locale)
            val projectId = repository.createProjectFromBlueprint(
                title = blueprint.projectTitle,
                notes = blueprint.projectNotes,
                headingsWithTasks = blueprint.headings.map { it.title to it.tasks }
            )
            _isGeminiBusy.value = false
            _isGeminiModalOpen.value = false
            _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(projectId)
        }
    }

    /**
     * Feature 4: Gemini Logbook Reflection -> Summarizes completed tasks & offers 1-tap focus tasks for Today.
     */
    fun generateGeminiLogbookReflection(locale: Locale = Locale.getDefault()) {
        viewModelScope.launch {
            _isGeminiBusy.value = true
            val completedTitles = logbookTasks.value.map { it.title }
            _logbookAiReview.value = geminiService.generateLogbookReflection(completedTitles, locale)
            _isGeminiBusy.value = false
        }
    }

    fun adoptSuggestedFocusTaskForToday(title: String) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            repository.createOrUpdateTask(
                title = title,
                scheduleState = ScheduleState.TODAY,
                whenDate = todayEpoch,
                sortOrder = 250.0
            )
        }
    }

    fun triggerManualCloudSync() {
        viewModelScope.launch {
            syncRepository.syncBidirectionalDeltas()
        }
    }

    fun setAuthModalOpen(open: Boolean) {
        _authErrorMessage.value = null
        _isAuthModalOpen.value = open
    }

    /**
     * Multi-Device User Registration (Email & Password):
     * Registers the user on the built-in Thingsbook Cloud Backend and immediately pushes all local records.
     */
    fun registerWithEmailPassword(email: String, password: String) {
        viewModelScope.launch {
            _isSigningIn.value = true
            _authErrorMessage.value = null
            syncRepository.registerAccount(email, password)
                .onSuccess {
                    _isAuthModalOpen.value = false
                }
                .onFailure { err ->
                    _authErrorMessage.value = err.localizedMessage ?: err.message
                }
            _isSigningIn.value = false
        }
    }

    /**
     * Multi-Device User Sign-In (Email & Password):
     * Signs in an existing user on a second/third device, pulls all cloud deltas
     * (`updatedAt > lastSyncTimestamp`), and attaches real-time multi-device listeners.
     */
    fun signInWithEmailPassword(email: String, password: String) {
        viewModelScope.launch {
            _isSigningIn.value = true
            _authErrorMessage.value = null
            syncRepository.signInAccount(email, password)
                .onSuccess {
                    _isAuthModalOpen.value = false
                }
                .onFailure { err ->
                    _authErrorMessage.value = err.localizedMessage ?: err.message
                }
            _isSigningIn.value = false
        }
    }

    fun signInAnonymouslyAndSync() {
        viewModelScope.launch {
            _isSigningIn.value = true
            _authErrorMessage.value = null
            syncRepository.signInInstantCloudAccount()
                .onSuccess {
                    _isAuthModalOpen.value = false
                }
                .onFailure { err ->
                    _authErrorMessage.value = err.localizedMessage ?: err.message
                }
            _isSigningIn.value = false
        }
    }

    fun signOutCloud() {
        viewModelScope.launch {
            syncRepository.signOutAccount()
        }
    }

    fun scheduleTaskReminder(task: TaskItem, triggerAtMillis: Long) {
        viewModelScope.launch {
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
}
