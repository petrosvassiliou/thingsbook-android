package com.culturedcode.things3.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.lang.ref.WeakReference
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culturedcode.things3.BuildConfig
import com.culturedcode.things3.R
import com.culturedcode.things3.data.ai.GeminiLogbookReviewResponse
import com.culturedcode.things3.data.ai.ThingsbookGeminiService
import com.culturedcode.things3.data.backup.BackupInspectionResult
import com.culturedcode.things3.data.backup.BackupPasswordRequiredException
import com.culturedcode.things3.data.backup.BackupPreferencesDto
import com.culturedcode.things3.data.backup.InvalidBackupPasswordException
import com.culturedcode.things3.data.calendar.DeviceCalendarRepository
import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.notifications.DailyDigestConfig
import com.culturedcode.things3.data.notifications.ReminderScheduler
import com.culturedcode.things3.data.repository.CloudSyncStatus
import com.culturedcode.things3.data.repository.SyncRepository
import com.culturedcode.things3.data.repository.ThingsRepository
import com.culturedcode.things3.data.update.AppReleaseInfo
import com.culturedcode.things3.data.update.AppUpdateManager
import com.culturedcode.things3.data.update.AppUpdateParser
import com.culturedcode.things3.data.update.AppUpdateUiState
import com.culturedcode.things3.domain.model.Area
import com.culturedcode.things3.domain.model.CalendarEventItem
import com.culturedcode.things3.domain.model.DeviceCalendarInfo
import com.culturedcode.things3.domain.model.Heading
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.domain.model.ProjectStatus
import com.culturedcode.things3.domain.model.ProjectTemplate
import com.culturedcode.things3.domain.model.QuickFindFilter
import com.culturedcode.things3.domain.model.RecurrenceRule
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.Tag
import com.culturedcode.things3.domain.model.TaskAttachment
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.domain.model.TaskStatus
import com.culturedcode.things3.domain.model.TimeblockScheduleUtils
import com.culturedcode.things3.domain.model.sortTagsForMode
import com.culturedcode.things3.domain.util.runCatchingCancellable
import com.culturedcode.things3.ui.components.FocusTimerUiState
import com.culturedcode.things3.ui.components.QuickShareDraft
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
import kotlinx.coroutines.delay
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
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

sealed interface ActiveNavigationTarget {
    data class SmartView(val type: SmartViewType) : ActiveNavigationTarget
    data class ProjectDetail(val projectId: String) : ActiveNavigationTarget
    data class AreaDetail(val areaId: String) : ActiveNavigationTarget

    fun scrollScopeKey(): String = when (this) {
        is SmartView -> "smart_${type.name}"
        is ProjectDetail -> "project_$projectId"
        is AreaDetail -> "area_$areaId"
    }

    fun topScrollAnchorKey(): String =
        "top_scroll_anchor_${scrollScopeKey()}"

    fun trailingMagicPlusGapKey(variant: String = "standard"): String =
        "trailing_magic_plus_gap_${scrollScopeKey()}_$variant"

    fun shouldResetScrollToTop(expandedTaskId: String?): Boolean =
        expandedTaskId.isNullOrBlank()
}

data class PendingProjectDeletePrompt(
    val projectId: String,
    val projectTitle: String,
    val associatedTaskCount: Int
)

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

    class SignInGoogle(val activityRef: WeakReference<Activity>) : PendingCloudAuthRequest {
        override val displayAccountLabel: String get() = ""
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
    private val reminderScheduler: ReminderScheduler,
    private val appUpdateManager: AppUpdateManager,
    private val deviceCalendarRepository: DeviceCalendarRepository
) : ViewModel() {

    private val _appLanguageTag = MutableStateFlow(
        runCatching { AppCompatDelegate.getApplicationLocales().toLanguageTags().trim() }.getOrDefault("")
    )
    val appLanguageTag: StateFlow<String> = _appLanguageTag.asStateFlow()

    @Volatile
    private var lastInAppLanguageSwitchMs: Long = 0L

    fun syncAppLanguageFromSystemIfIdle() {
        if (android.os.SystemClock.elapsedRealtime() - lastInAppLanguageSwitchMs < 600L) return
        val currentSysTag = runCatching {
            AppCompatDelegate.getApplicationLocales().toLanguageTags().trim()
        }.getOrDefault("")
        if (_appLanguageTag.value != currentSysTag) {
            _appLanguageTag.value = currentSysTag
        }
    }

    private fun getLocalizedString(@StringRes resId: Int, vararg args: Any): String {
        val localizedCtx = com.culturedcode.things3.ui.theme.createLocalizedContextForTag(
            appContext,
            _appLanguageTag.value
        )
        return localizedCtx.getString(resId, *args)
    }

    private fun activeLangCode(): String {
        val lang = com.culturedcode.things3.ui.theme.resolveEffectiveAppLocale(_appLanguageTag.value).language
        return (lang.takeIf { it.isNotBlank() } ?: Locale.getDefault().language)
            .lowercase(Locale.ROOT)
            .take(2)
    }

    private fun localizedFallbackError(
        en: String,
        de: String,
        fr: String,
        it: String,
        es: String
    ): String = when (activeLangCode()) {
        "de" -> de
        "fr" -> fr
        "it" -> it
        "es" -> es
        else -> en
    }

    private fun isLockedByBiometric(): Boolean =
        _biometricAppLockEnabled.value && !_isAppUnlocked.value

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
                        ?: localizedFallbackError(
                            en = "An unexpected error occurred.",
                            de = "Ein unerwarteter Fehler ist aufgetreten.",
                            fr = "Une erreur inattendue s'est produite.",
                            it = "Si è verificato un errore imprevisto.",
                            es = "Se produjo un error inesperado."
                        )
                }
            }
    )

    fun clearUiErrorMessage() {
        _uiErrorMessage.value = null
    }

    fun showUiMessage(message: String) {
        if (message.isNotBlank()) {
            _uiErrorMessage.value = message
        }
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

    private val settingsPrefs = appContext.getSharedPreferences("thingsbook_ui_prefs", Context.MODE_PRIVATE)
    private val coreSettingsPrefs = appContext.getSharedPreferences("thingsbook_settings_prefs", Context.MODE_PRIVATE)
    private val tagDecodeJson = Json { ignoreUnknownKeys = true }

    private fun normalizeTagSortMode(raw: String?): String = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        "most_used" -> "most_used"
        "manual" -> "manual"
        else -> "alpha"
    }

    private fun normalizeViewSortMode(raw: String?): String = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        "deadline" -> "deadline"
        "duration" -> "duration"
        "alpha" -> "alpha"
        else -> "manual"
    }

    private val _tagSortMode = MutableStateFlow(
        normalizeTagSortMode(coreSettingsPrefs.getString(PREF_TAG_SORT_MODE, "alpha"))
    )
    val tagSortMode: StateFlow<String> = _tagSortMode.asStateFlow()

    fun setTagSortMode(mode: String) {
        val normalized = normalizeTagSortMode(mode)
        _tagSortMode.value = normalized
        runCatching {
            coreSettingsPrefs.edit().putString(PREF_TAG_SORT_MODE, normalized).apply()
        }
    }

    private val _viewSortMode = MutableStateFlow(
        normalizeViewSortMode(coreSettingsPrefs.getString(PREF_VIEW_SORT_MODE, "manual"))
    )
    val viewSortMode: StateFlow<String> = _viewSortMode.asStateFlow()

    fun setViewSortMode(mode: String) {
        val normalized = normalizeViewSortMode(mode)
        _viewSortMode.value = normalized
        runCatching {
            coreSettingsPrefs.edit().putString(PREF_VIEW_SORT_MODE, normalized).apply()
        }
    }

    // Tag Filter & Tag Management Modal States
    private val _selectedFilterTagId = MutableStateFlow<String?>(null)
    val selectedFilterTagId: StateFlow<String?> = _selectedFilterTagId.asStateFlow()

    private val _isTagManagerOpen = MutableStateFlow(false)
    val isTagManagerOpen: StateFlow<Boolean> = _isTagManagerOpen.asStateFlow()

    // Area Manager Modal State
    private val _isAreaManagerOpen = MutableStateFlow(false)
    val isAreaManagerOpen: StateFlow<Boolean> = _isAreaManagerOpen.asStateFlow()

    // Project Deletion Confirmation Prompt State
    private val _pendingProjectDeletePrompt = MutableStateFlow<PendingProjectDeletePrompt?>(null)
    val pendingProjectDeletePrompt: StateFlow<PendingProjectDeletePrompt?> = _pendingProjectDeletePrompt.asStateFlow()

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

    private val _quickFindFilter = MutableStateFlow(QuickFindFilter())
    val quickFindFilter: StateFlow<QuickFindFilter> = _quickFindFilter.asStateFlow()

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
    val currentEpochDay: StateFlow<Long> = repository.currentEpochDay

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

    val deletedTasks: StateFlow<List<TaskItem>> = repository.observeDeletedTasks()
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedProjects: StateFlow<List<Project>> = repository.observeDeletedProjects()
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

    private fun decodeRawTagIds(tagIdsJson: String): List<String> {
        if (tagIdsJson.isBlank() || tagIdsJson == "[]") return emptyList()
        return runCatching { tagDecodeJson.decodeFromString<List<String>>(tagIdsJson) }
            .getOrDefault(emptyList())
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
    }

    val tagUsageCounts: StateFlow<Map<String, Int>> = combine(
        repository.observeAllTasksRaw(),
        repository.observeAllProjectsRaw()
    ) { allRawTasks, allRawProjects ->
        runCatching {
            val inactiveOrDeletedProjectIds = allRawProjects
                .asSequence()
                .filter { it.isDeleted || it.status != ProjectStatus.ACTIVE }
                .map { it.id }
                .toSet()
            val counts = HashMap<String, Int>()
            for (task in allRawTasks) {
                if (task.isDeleted || task.status != TaskStatus.OPEN) continue
                if (task.projectId != null && task.projectId in inactiveOrDeletedProjectIds) continue
                for (tagId in decodeRawTagIds(task.tagIdsJson)) {
                    counts[tagId] = (counts[tagId] ?: 0) + 1
                }
            }
            for (proj in allRawProjects) {
                if (proj.isDeleted || proj.status != ProjectStatus.ACTIVE) continue
                for (tagId in decodeRawTagIds(proj.tagIdsJson)) {
                    counts[tagId] = (counts[tagId] ?: 0) + 1
                }
            }
            counts.toMap()
        }.getOrDefault(emptyMap())
    }
        .catch { emit(emptyMap()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allTags: StateFlow<List<Tag>> = combine(
        repository.observeAllTags(),
        _tagSortMode,
        tagUsageCounts
    ) { tags, mode, counts ->
        sortTagsForMode(tags, mode, counts)
    }
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProjectTemplates: StateFlow<List<ProjectTemplate>> = repository.observeUserProjectTemplates()
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
                repository.observeTasksForProject(target.projectId)
            } else {
                flowOf(emptyList())
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProjectAttachments: StateFlow<List<TaskAttachment>> = _navigationTarget
        .flatMapLatest { target ->
            if (target is ActiveNavigationTarget.ProjectDetail) {
                repository.observeAttachmentsForProject(target.projectId)
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
                repository.observeTasksForArea(target.areaId)
            } else {
                flowOf(emptyList())
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real-time FTS5 Quick Find Results
    val quickFindTaskResults: StateFlow<List<TaskItem>> = combine(_quickFindQuery, _quickFindFilter) { q, f -> q to f }
        .flatMapLatest { (q, f) ->
            if (q.isBlank() && !f.hasActiveFilters) flowOf(emptyList()) else repository.searchTasksFts5(q, f)
        }
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickFindProjectResults: StateFlow<List<Project>> = combine(_quickFindQuery, _quickFindFilter) { q, f -> q to f }
        .flatMapLatest { (q, f) ->
            if (q.isBlank() && !f.hasActiveFilters) flowOf(emptyList()) else repository.searchProjectsFts5(q, f)
        }
        .catch { emit(emptyList()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectSmartView(viewType: SmartViewType) {
        _expandedTaskId.value = null
        clearTaskSelection()
        _navigationTarget.value = ActiveNavigationTarget.SmartView(viewType)
    }

    fun handleWidgetLaunchIntent(viewName: String?, taskId: String?, quickAdd: Boolean) {
        if (isLockedByBiometric()) {
            return
        }
        clearTaskSelection()
        if (viewName?.equals("VOICE_CAPTURE", ignoreCase = true) == true) {
            _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.INBOX)
            openVoiceQuickCapture()
            return
        }
        if (viewName != null && viewName.startsWith("PROJECT:", ignoreCase = true)) {
            val projectId = viewName.substringAfter(":").trim()
            if (projectId.isNotEmpty()) {
                _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(projectId)
                if (!taskId.isNullOrBlank()) {
                    _expandedTaskId.value = taskId
                } else if (quickAdd) {
                    createTaskAtPosition(emptyList(), 0, "")
                }
                return
            }
        }
        if (viewName == null && !taskId.isNullOrBlank()) {
            _expandedTaskId.value = taskId
            safeVmScope.launch {
                val entity = repository.getTaskEntityById(taskId)
                val resolvedView = when (entity?.scheduleState) {
                    ScheduleState.INBOX -> SmartViewType.INBOX
                    ScheduleState.UPCOMING -> SmartViewType.UPCOMING
                    ScheduleState.ANYTIME -> SmartViewType.ANYTIME
                    ScheduleState.SOMEDAY -> SmartViewType.SOMEDAY
                    else -> SmartViewType.TODAY
                }
                _navigationTarget.value = ActiveNavigationTarget.SmartView(resolvedView)
                _expandedTaskId.value = taskId
            }
            return
        }
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
        clearTaskSelection()
        _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(projectId)
    }

    fun selectArea(areaId: String) {
        _expandedTaskId.value = null
        clearTaskSelection()
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
        if (!open) {
            _quickFindQuery.value = ""
            _quickFindFilter.value = QuickFindFilter()
        }
    }

    fun updateQuickFindQuery(query: String) {
        _quickFindQuery.value = query
    }

    fun updateQuickFindFilter(filter: QuickFindFilter) {
        _quickFindFilter.value = filter
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
            val normalized = languageTag.trim()
            lastInAppLanguageSwitchMs = android.os.SystemClock.elapsedRealtime()
            _appLanguageTag.value = normalized
            Locale.setDefault(com.culturedcode.things3.ui.theme.resolveEffectiveAppLocale(normalized))
            val localeList = if (normalized.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(normalized)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
            com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.refreshAllWidgets(appContext)
        }.onFailure { err ->
            Log.w(TAG, "Failed switching language to '$languageTag'", err)
            _uiErrorMessage.value = err.localizedMessage ?: localizedFallbackError(
                en = "Could not switch language.",
                de = "Sprache konnte nicht geändert werden.",
                fr = "Impossible de changer la langue.",
                it = "Impossibile cambiare lingua.",
                es = "No se pudo cambiar el idioma."
            )
        }
    }

    fun updateTaskStatus(taskId: String, status: TaskStatus) {
        safeVmScope.launch {
            val prevEntity = repository.getTaskEntityById(taskId)
            repository.updateTaskStatus(taskId, status)
            if (status != TaskStatus.OPEN) {
                if (_expandedTaskId.value == taskId) {
                    _expandedTaskId.value = null
                }
                if (taskId in _selectedTaskIds.value) {
                    _selectedTaskIds.value = _selectedTaskIds.value - taskId
                }
                if (_focusTimerState.value?.taskId == taskId) {
                    stopFocusTimer()
                }
                if (prevEntity != null && prevEntity.status == TaskStatus.OPEN && status == TaskStatus.COMPLETED) {
                    val label = prevEntity.title.ifBlank { getLocalizedString(R.string.entity_todo) }
                    registerUndoAction(
                        message = getLocalizedString(R.string.undo_task_completed, label)
                    ) {
                        repository.updateTaskStatus(taskId, TaskStatus.OPEN)
                    }
                }
            }
        }
    }

    fun updateTaskTitleAndNotes(task: TaskItem, newTitle: String, newNotes: String) {
        safeVmScope.launch {
            repository.updateTaskTitleAndNotes(
                taskId = task.id,
                newTitle = newTitle,
                newNotes = newNotes
            )
        }
    }

    fun updateTaskSchedule(task: TaskItem, newState: ScheduleState, newWhenDate: Long?) {
        safeVmScope.launch {
            repository.updateTaskSchedule(
                taskId = task.id,
                newState = newState,
                newWhenDate = newWhenDate
            )
        }
    }

    fun updateTaskScheduleAndOptionalDeadline(
        task: TaskItem,
        newState: ScheduleState,
        newWhenDate: Long?,
        newDeadline: Long? = null
    ) {
        safeVmScope.launch {
            if (newDeadline != null) {
                repository.updateTaskScheduleAndDeadline(
                    taskId = task.id,
                    newState = newState,
                    newWhenDate = newWhenDate,
                    newDeadline = newDeadline
                )
            } else {
                repository.updateTaskSchedule(
                    taskId = task.id,
                    newState = newState,
                    newWhenDate = newWhenDate
                )
            }
        }
    }

    fun openTaskFromWeeklyReview(task: TaskItem) {
        _isWeeklyReviewModalOpen.value = false
        _selectedFilterTagId.value = null
        _navigationTarget.value = when {
            task.projectId != null -> ActiveNavigationTarget.ProjectDetail(task.projectId)
            task.areaId != null -> ActiveNavigationTarget.AreaDetail(task.areaId)
            task.scheduleState == ScheduleState.TODAY || task.scheduleState == ScheduleState.THIS_EVENING ->
                ActiveNavigationTarget.SmartView(SmartViewType.TODAY)
            task.scheduleState == ScheduleState.UPCOMING ->
                ActiveNavigationTarget.SmartView(SmartViewType.UPCOMING)
            task.scheduleState == ScheduleState.SOMEDAY ->
                ActiveNavigationTarget.SmartView(SmartViewType.SOMEDAY)
            task.scheduleState == ScheduleState.ANYTIME ->
                ActiveNavigationTarget.SmartView(SmartViewType.ANYTIME)
            else -> ActiveNavigationTarget.SmartView(SmartViewType.INBOX)
        }
        _expandedTaskId.value = task.id
    }

    fun quickMoveTaskToThisEveningAndCollapse(
        task: TaskItem,
        latestTitle: String,
        latestNotes: String
    ) {
        if (_expandedTaskId.value == task.id) {
            _expandedTaskId.value = null
        }
        safeVmScope.launch {
            if (latestTitle != task.title || latestNotes != task.notes) {
                repository.updateTaskTitleAndNotes(
                    taskId = task.id,
                    newTitle = latestTitle,
                    newNotes = latestNotes
                )
            }
            val startOfTodayMillis = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            repository.updateTaskSchedule(
                taskId = task.id,
                newState = ScheduleState.THIS_EVENING,
                newWhenDate = startOfTodayMillis
            )
        }
    }

    fun updateTaskDeadline(task: TaskItem, newDeadline: Long?) {
        safeVmScope.launch {
            repository.updateTaskDeadline(
                taskId = task.id,
                newDeadline = newDeadline
            )
        }
    }

    fun addChecklistItem(taskId: String, title: String) {
        safeVmScope.launch {
            repository.addChecklistItem(taskId, title)
        }
    }

    fun insertChecklistItemAfter(
        taskId: String,
        afterItemId: String?,
        newItemId: String,
        title: String = ""
    ) {
        safeVmScope.launch {
            repository.insertChecklistItemAfter(
                taskId = taskId,
                afterItemId = afterItemId,
                newItemId = newItemId,
                title = title
            )
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
            val prevEntity = repository.getTaskEntityById(taskId)
            if (_expandedTaskId.value == taskId) {
                _expandedTaskId.value = null
            }
            if (taskId in _selectedTaskIds.value) {
                _selectedTaskIds.value = _selectedTaskIds.value - taskId
            }
            if (_focusTimerState.value?.taskId == taskId) {
                stopFocusTimer()
            }
            repository.softDeleteTask(taskId)
            if (prevEntity != null) {
                val label = prevEntity.title.ifBlank { getLocalizedString(R.string.entity_todo) }
                registerUndoAction(
                    message = getLocalizedString(R.string.undo_task_deleted, label)
                ) {
                    repository.restoreDeletedTask(taskId)
                }
            }
        }
    }

    /**
     * Magic Plus Drop Logic: Inserts a new To-Do at exact `dropIndex` using LexoRank midpoint `sortOrder`
     * and immediately expands the newly created card inline!
     */
    fun createTaskAtPosition(
        currentList: List<TaskItem>,
        dropIndex: Int,
        defaultTitle: String,
        forceTopUngroupedInProject: Boolean = false
    ) {
        safeVmScope.launch {
            val prevOrder = currentList.getOrNull(dropIndex - 1)?.sortOrder
            val nextOrder = currentList.getOrNull(dropIndex)?.sortOrder
            val midpointOrder = repository.computeMidpointSortOrder(prevOrder, nextOrder)

            val zone = ZoneId.systemDefault()
            val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val tomorrowEpoch = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val target = _navigationTarget.value
            val (scheduleState, whenDate, projectId) = when (target) {
                is ActiveNavigationTarget.SmartView -> when (target.type) {
                    SmartViewType.INBOX -> Triple(ScheduleState.INBOX, null, null)
                    SmartViewType.TODAY -> Triple(ScheduleState.TODAY, todayEpoch, null)
                    SmartViewType.UPCOMING -> {
                        val neighbor = currentList.getOrNull(dropIndex) ?: currentList.getOrNull(dropIndex - 1)
                        val neighborEpoch = (neighbor?.whenDate ?: neighbor?.deadline)
                            ?.takeIf { it >= tomorrowEpoch }
                            ?: tomorrowEpoch
                        Triple(ScheduleState.UPCOMING, neighborEpoch, null)
                    }
                    SmartViewType.ANYTIME -> Triple(ScheduleState.ANYTIME, null, null)
                    SmartViewType.SOMEDAY -> Triple(ScheduleState.SOMEDAY, null, null)
                    SmartViewType.LOGBOOK, SmartViewType.TRASH -> Triple(ScheduleState.INBOX, null, null)
                }
                is ActiveNavigationTarget.ProjectDetail -> Triple(ScheduleState.ANYTIME, null, target.projectId)
                is ActiveNavigationTarget.AreaDetail -> Triple(ScheduleState.ANYTIME, null, null)
            }

            val areaId = when (target) {
                is ActiveNavigationTarget.AreaDetail -> target.areaId
                is ActiveNavigationTarget.ProjectDetail -> activeProjects.value.find { it.id == target.projectId }?.areaId
                else -> null
            }

            val neighboringHeadingId = if (forceTopUngroupedInProject && target is ActiveNavigationTarget.ProjectDetail) {
                null
            } else {
                currentList.getOrNull(dropIndex - 1)?.headingId
                    ?: currentList.getOrNull(dropIndex)?.headingId
            }
            if (neighboringHeadingId != null) {
                expandHeading(neighboringHeadingId)
            }

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

            if (
                target == ActiveNavigationTarget.SmartView(SmartViewType.LOGBOOK) ||
                target == ActiveNavigationTarget.SmartView(SmartViewType.TRASH)
            ) {
                _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.INBOX)
            }

            // Expand newly dropped card inline immediately
            _expandedTaskId.value = createdId
        }
    }

    /**
     * Creates a new To-Do directly inside a Project section (`headingId` = null → tasks without heading)
     * at `indexInSection` of `sectionTasks` (as displayed), expands the heading and opens the new card.
     */
    fun createTaskInProjectSection(
        headingId: String?,
        sectionTasks: List<TaskItem>,
        indexInSection: Int,
        defaultTitle: String = ""
    ) {
        val target = _navigationTarget.value as? ActiveNavigationTarget.ProjectDetail ?: return
        if (headingId != null) expandHeading(headingId)
        safeVmScope.launch {
            val idx = indexInSection.coerceIn(0, sectionTasks.size)
            val order = repository.computeMidpointSortOrder(
                sectionTasks.getOrNull(idx - 1)?.sortOrder,
                sectionTasks.getOrNull(idx)?.sortOrder
            )
            val areaId = activeProjects.value.find { it.id == target.projectId }?.areaId
            val createdId = repository.createOrUpdateTask(
                title = defaultTitle,
                notes = "",
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.ANYTIME,
                whenDate = null,
                projectId = target.projectId,
                headingId = headingId,
                areaId = areaId,
                sortOrder = order
            )
            _expandedTaskId.value = createdId
        }
    }

    /**
     * Drag & Drop inside a Project: moves `taskId` into the section `headingId` at `indexInSection`
     * of the displayed `sectionTasks` (which may still contain the dragged task itself).
     */
    fun moveTaskWithinProject(
        taskId: String,
        headingId: String?,
        sectionTasks: List<TaskItem>,
        indexInSection: Int
    ) {
        val currentIdx = sectionTasks.indexOfFirst { it.id == taskId }
        var idx = indexInSection.coerceIn(0, sectionTasks.size)
        if (currentIdx >= 0) {
            // Dropped onto its own position → nothing to do
            if (idx == currentIdx || idx == currentIdx + 1) return
            if (currentIdx < idx) idx -= 1
        }
        val others = sectionTasks.filter { it.id != taskId }
        idx = idx.coerceIn(0, others.size)
        if (headingId != null) expandHeading(headingId)
        safeVmScope.launch {
            val order = repository.computeMidpointSortOrder(
                others.getOrNull(idx - 1)?.sortOrder,
                others.getOrNull(idx)?.sortOrder
            )
            repository.repositionTaskInProject(taskId, headingId, order)
        }
    }

    fun expandHeading(headingId: String) {
        val current = _collapsedHeadingIds.value
        if (headingId in current) _collapsedHeadingIds.value = current - headingId
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
                _uiErrorMessage.value = err.localizedMessage ?: err.message ?: localizedFallbackError(
                    en = "Could not create area.",
                    de = "Bereich konnte nicht erstellt werden.",
                    fr = "Impossible de créer le domaine.",
                    it = "Impossibile creare l'area.",
                    es = "No se pudo crear el área."
                )
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
                    _uiErrorMessage.value = err.localizedMessage ?: err.message ?: localizedFallbackError(
                        en = "Could not create area.",
                        de = "Bereich konnte nicht erstellt werden.",
                        fr = "Impossible de créer le domaine.",
                        it = "Impossibile creare l'area.",
                        es = "No se pudo crear el área."
                    )
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
            val prevTitle = activeProjects.value.find { it.id == projectId }?.title
                ?: database.projectDao().getById(projectId)?.title
            val label = prevTitle?.trim()?.ifEmpty { null } ?: getLocalizedString(R.string.entity_project)
            val associatedTaskCount = database.taskDao().countNonDeletedTasksByProject(projectId)
            if (associatedTaskCount > 0) {
                _pendingProjectDeletePrompt.value = PendingProjectDeletePrompt(
                    projectId = projectId,
                    projectTitle = label,
                    associatedTaskCount = associatedTaskCount
                )
            } else {
                executeProjectDeletion(
                    projectId = projectId,
                    projectLabel = label,
                    deleteAssociatedTasks = true
                )
            }
        }
    }

    fun confirmDeleteProject(deleteAssociatedTasks: Boolean) {
        val prompt = _pendingProjectDeletePrompt.value ?: return
        _pendingProjectDeletePrompt.value = null
        safeVmScope.launch {
            executeProjectDeletion(
                projectId = prompt.projectId,
                projectLabel = prompt.projectTitle,
                deleteAssociatedTasks = deleteAssociatedTasks
            )
        }
    }

    fun dismissDeleteProjectPrompt() {
        _pendingProjectDeletePrompt.value = null
    }

    private suspend fun executeProjectDeletion(
        projectId: String,
        projectLabel: String,
        deleteAssociatedTasks: Boolean
    ) {
        val currentTarget = _navigationTarget.value
        if (currentTarget is ActiveNavigationTarget.ProjectDetail && currentTarget.projectId == projectId) {
            _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.TODAY)
        }
        if (deleteAssociatedTasks) {
            _expandedTaskId.value = null
            _selectedTaskIds.value = emptySet()
        }
        repository.softDeleteProject(projectId, deleteAssociatedTasks = deleteAssociatedTasks)
        registerUndoAction(
            message = getLocalizedString(R.string.undo_project_deleted, projectLabel)
        ) {
            repository.restoreDeletedProject(projectId)
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
                val msg = t.localizedMessage ?: t.message ?: localizedFallbackError(
                    en = "Could not generate AI subtasks.",
                    de = "KI-Teilaufgaben konnten nicht erstellt werden.",
                    fr = "Impossible de générer des sous-tâches IA.",
                    it = "Impossibile generare sotto-attività IA.",
                    es = "No se pudieron generar subtareas con IA."
                )
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
                val msg = t.localizedMessage ?: t.message ?: localizedFallbackError(
                    en = "Brain Dump analysis failed.",
                    de = "Brain-Dump-Analyse fehlgeschlagen.",
                    fr = "Échec de l'analyse Brain Dump.",
                    it = "Analisi Brain Dump non riuscita.",
                    es = "Error en el análisis de Brain Dump."
                )
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
                val msg = t.localizedMessage ?: t.message ?: localizedFallbackError(
                    en = "Project creation with Gemini failed.",
                    de = "Projekt-Erstellung mit Gemini fehlgeschlagen.",
                    fr = "Échec de la création du projet avec Gemini.",
                    it = "Creazione del progetto con Gemini non riuscita.",
                    es = "Error al crear el proyecto con Gemini."
                )
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
                val msg = t.localizedMessage ?: t.message ?: localizedFallbackError(
                    en = "Logbook reflection failed.",
                    de = "Logbuch-Reflexion fehlgeschlagen.",
                    fr = "Échec de la réflexion du journal.",
                    it = "Riflessione del registro non riuscita.",
                    es = "Error en la reflexión del registro."
                )
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
            _uiErrorMessage.value = getLocalizedString(R.string.settings_err_local_mode_sync)
            return
        }
        safeVmScope.launch {
            syncRepository.syncBidirectionalDeltas().onFailure { err ->
                Log.w(TAG, "Manual cloud sync failed", err)
                _uiErrorMessage.value = err.localizedMessage ?: err.message.orEmpty()
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
     * Whenever user-created local tasks exist, asks whether to merge local tasks with the cloud account
     * or replace local data with the cloud account's tasks.
     */
    fun registerWithEmailPassword(email: String, password: String) {
        safeVmScope.launch {
            if (email.isNotBlank() && password.isNotBlank() && repository.hasLocalUserCreatedTasks()) {
                _pendingMergeNonSampleCount.value = repository.countNonSampleTasks()
                _pendingMergeAuthRequest.value = PendingCloudAuthRequest.RegisterEmail(email.trim(), password)
                _isAuthModalOpen.value = false
                return@launch
            }
            executeCloudAuthRequestDirect(
                request = PendingCloudAuthRequest.RegisterEmail(email, password),
                purgeSamplesBeforeDirectAuth = true
            )
        }
    }

    /**
     * Multi-Device User Sign-In (Email & Password):
     * Whenever user-created local tasks exist, asks whether to merge local tasks with the cloud account
     * or replace local data with the cloud account's tasks.
     */
    fun signInWithEmailPassword(email: String, password: String) {
        safeVmScope.launch {
            if (email.isNotBlank() && password.isNotBlank() && repository.hasLocalUserCreatedTasks()) {
                _pendingMergeNonSampleCount.value = repository.countNonSampleTasks()
                _pendingMergeAuthRequest.value = PendingCloudAuthRequest.SignInEmail(email.trim(), password)
                _isAuthModalOpen.value = false
                return@launch
            }
            executeCloudAuthRequestDirect(
                request = PendingCloudAuthRequest.SignInEmail(email, password),
                purgeSamplesBeforeDirectAuth = true
            )
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
     * Cross-Platform Firebase Google OAuth Sign-In ("Sign in with Google").
     * Authenticates into the exact same Firebase `uid` as ChromeOS & macOS.
     */
    fun signInWithGoogleAccount(activity: Activity?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            _authErrorMessage.value = localizedFallbackError(
                en = "Unable to launch Google Sign-In window.",
                de = "Google-Anmeldefenster konnte nicht geöffnet werden.",
                fr = "Impossible d'ouvrir la fenêtre de connexion Google.",
                it = "Impossibile aprire la finestra di accesso Google.",
                es = "No se pudo abrir la ventana de inicio de sesión de Google."
            )
            return
        }
        val actRef = WeakReference(activity)
        safeVmScope.launch {
            if (repository.hasLocalUserCreatedTasks()) {
                _pendingMergeNonSampleCount.value = repository.countNonSampleTasks()
                _pendingMergeAuthRequest.value = PendingCloudAuthRequest.SignInGoogle(actRef)
                _isAuthModalOpen.value = false
                return@launch
            }
            executeCloudAuthRequestDirect(
                request = PendingCloudAuthRequest.SignInGoogle(actRef),
                purgeSamplesBeforeDirectAuth = true
            )
        }
    }

    private suspend fun executeCloudAuthRequestDirect(
        request: PendingCloudAuthRequest,
        reopenAuthModalOnError: Boolean = false,
        purgeSamplesBeforeDirectAuth: Boolean = false
    ) {
        _isSigningIn.value = true
        _authErrorMessage.value = null
        _authInfoMessage.value = null
        try {
            if (_isLocalOnlyDbMode.value) {
                _isLocalOnlyDbMode.value = false
                runCatching {
                    settingsPrefs.edit().putBoolean("local_only_db_mode", false).apply()
                    coreSettingsPrefs.edit().putBoolean("local_only_db_mode", false).apply()
                }
                syncRepository.setLocalOnlyDbModePreference(false)
            }
            if (purgeSamplesBeforeDirectAuth && repository.hasSampleTasks()) {
                runCatchingCancellable {
                    repository.purgeSampleDataLocally(markRemainingDirtyForMerge = false)
                }
            }
            val result = when (request) {
                is PendingCloudAuthRequest.RegisterEmail ->
                    syncRepository.registerAccount(request.email, request.password)
                is PendingCloudAuthRequest.SignInEmail ->
                    syncRepository.signInAccount(request.email, request.password)
                is PendingCloudAuthRequest.SignInGoogle -> {
                    val hostActivity = request.activityRef.get()
                    if (hostActivity == null || hostActivity.isFinishing || hostActivity.isDestroyed) {
                        Result.failure(
                            IllegalStateException(
                                localizedFallbackError(
                                    en = "Unable to launch Google Sign-In window.",
                                    de = "Google-Anmeldefenster konnte nicht geöffnet werden.",
                                    fr = "Impossible d'ouvrir la fenêtre de connexion Google.",
                                    it = "Impossibile aprire la finestra di accesso Google.",
                                    es = "No se pudo abrir la ventana de inicio de sesión de Google."
                                )
                            )
                        )
                    } else {
                        syncRepository.signInWithGoogleOAuth(hostActivity)
                    }
                }
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
            repository.updateTaskReminder(
                taskId = task.id,
                newReminderTime = triggerAtMillis
            )
        }
    }

    fun updateTaskScheduleWithReminder(
        task: TaskItem,
        newState: ScheduleState,
        newWhenDate: Long?,
        newReminderTime: Long?
    ) {
        safeVmScope.launch {
            repository.updateTaskScheduleWithReminder(
                taskId = task.id,
                newState = newState,
                newWhenDate = newWhenDate,
                newReminderTime = newReminderTime
            )
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
            val resolvedId = repository.createOrUpdateTag(
                id = tagId,
                title = newTitle,
                shortcut = shortcut,
                allowClearShortcut = true
            )
            if (resolvedId != tagId && _selectedFilterTagId.value == tagId) {
                _selectedFilterTagId.value = resolvedId
            }
        }
    }

    fun mergeTags(sourceTagId: String, targetTagId: String) {
        if (sourceTagId.isBlank() || targetTagId.isBlank() || sourceTagId == targetTagId) return
        safeVmScope.launch {
            val sourceTitle = allTags.value.firstOrNull { it.id == sourceTagId }?.title ?: sourceTagId
            val targetTitle = allTags.value.firstOrNull { it.id == targetTagId }?.title ?: targetTagId
            if (_selectedFilterTagId.value == sourceTagId) {
                _selectedFilterTagId.value = targetTagId
            }
            val merged = repository.mergeTags(sourceTagId = sourceTagId, targetTagId = targetTagId)
            if (merged) {
                showCelebrationToast(
                    getLocalizedString(R.string.v25_tags_merged_toast, sourceTitle, targetTitle)
                )
            }
        }
    }

    fun moveTag(tagId: String, moveUp: Boolean) {
        if (tagId.isBlank()) return
        safeVmScope.launch {
            repository.moveTag(tagId = tagId, moveUp = moveUp)
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
            repository.updateTaskTags(
                taskId = task.id,
                newTagIds = newTagIds
            )
        }
    }

    fun createAndAssignTagToTask(task: TaskItem, newTagTitle: String) {
        safeVmScope.launch {
            val newId = repository.createOrUpdateTag(title = newTagTitle)
            val updatedTagIds = (task.tags.map { it.id } + newId).distinct()
            repository.updateTaskTags(
                taskId = task.id,
                newTagIds = updatedTagIds
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
            _uiErrorMessage.value = err.localizedMessage ?: localizedFallbackError(
                en = "Could not save notification settings.",
                de = "Benachrichtigungen konnten nicht gespeichert werden.",
                fr = "Impossible d'enregistrer les notifications.",
                it = "Impossibile salvare le notifiche.",
                es = "No se pudieron guardar las notificaciones."
            )
        }
    }

    fun sendTestDailyDigestNotificationNow() {
        runCatching {
            reminderScheduler.triggerDailyDigestNotificationNow()
        }.onFailure { err ->
            Log.w(TAG, "Failed triggering test notification", err)
            _uiErrorMessage.value = err.localizedMessage ?: localizedFallbackError(
                en = "Test notification failed.",
                de = "Test-Benachrichtigung fehlgeschlagen.",
                fr = "Échec de la notification de test.",
                it = "Notifica di prova non riuscita.",
                es = "Error en la notificación de prueba."
            )
        }
    }

    // =========================================================================
    // v1.3.0 / v1.4.0: Settings Modal, Collapsible Sidebar, Local/Cloud DB Mode,
    // Live Task Statistics, and Off-Main-Thread CSV Import/Export/Share
    // =========================================================================

    private val _isSettingsModalOpen = MutableStateFlow(false)
    val isSettingsModalOpen: StateFlow<Boolean> = _isSettingsModalOpen.asStateFlow()

    private val _isSidebarVisible = MutableStateFlow(settingsPrefs.getBoolean("sidebar_visible", true))
    val isSidebarVisible: StateFlow<Boolean> = _isSidebarVisible.asStateFlow()

    private val _isLocalOnlyDbMode = MutableStateFlow(
        settingsPrefs.getBoolean("local_only_db_mode", false) ||
            coreSettingsPrefs.getBoolean("local_only_db_mode", false)
    )
    val isLocalOnlyDbMode: StateFlow<Boolean> = _isLocalOnlyDbMode.asStateFlow()

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
        repository.observeAllProjectsRaw(),
        areas,
        allTags,
        currentEpochDay
    ) { allRawTasks, allRawProjects, areasList, tagsList, _ ->
        runCatching {
            val inactiveOrDeletedProjectIds = allRawProjects
                .asSequence()
                .filter { it.isDeleted || it.status != ProjectStatus.ACTIVE }
                .map { it.id }
                .toSet()
            val activeProjectsCount = allRawProjects.count { !it.isDeleted && it.status == ProjectStatus.ACTIVE }

            val nonDeleted = allRawTasks.filter { !it.isDeleted }
            val deletedCount = allRawTasks.count {
                it.isDeleted && (it.title.isNotEmpty() || it.notes.isNotEmpty())
            }
            val openTasks = nonDeleted.filter { t ->
                t.status == TaskStatus.OPEN &&
                    (t.projectId == null || t.projectId !in inactiveOrDeletedProjectIds)
            }
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
                    (t.whenDate != null && t.whenDate <= todayEndMillis) ||
                    (t.deadline != null && t.deadline <= todayEndMillis)
            }
            val upcomingCount = openTasks.count { t ->
                val activeInToday = t.scheduleState == ScheduleState.TODAY ||
                    t.scheduleState == ScheduleState.THIS_EVENING ||
                    (t.whenDate != null && t.whenDate <= todayEndMillis) ||
                    (t.deadline != null && t.deadline <= todayEndMillis)
                !activeInToday && (
                    t.scheduleState == ScheduleState.UPCOMING ||
                        (t.whenDate != null && t.whenDate > todayEndMillis) ||
                        (t.deadline != null && t.deadline > todayEndMillis) ||
                        !t.recurrenceRuleJson.isNullOrBlank()
                    )
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
                totalRecordsCount = nonDeleted.size + deletedCount,
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
                activeProjectsCount = activeProjectsCount,
                areasCount = areasList.size,
                tagsCount = tagsList.size
            )
        }.getOrDefault(ThingsbookStatistics())
    }
        .catch { emit(ThingsbookStatistics()) }
        .stateIn(safeVmScope, SharingStarted.WhileSubscribed(5000), ThingsbookStatistics())

    fun setSettingsModalOpen(open: Boolean) {
        _isSettingsModalOpen.value = open
        if (open) {
            _hasCalendarPermission.value = deviceCalendarRepository.hasCalendarPermission()
            if (_calendarEventsEnabled.value && _hasCalendarPermission.value) {
                refreshCalendarsAndEvents(selectDefaultIfFirstTime = false)
            }
        }
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
                repository.purgeSampleDataLocally(
                    markRemainingDirtyForMerge = false,
                    emitCloudTombstonesIfSignedIn = true
                )
            }.onSuccess {
                dismissInitialSetupBanner()
                runCatching {
                    com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.refreshAllWidgets(appContext)
                }
            }.onFailure { err ->
                Log.w(TAG, "Failed purging sample tasks", err)
                _uiErrorMessage.value = err.localizedMessage ?: localizedFallbackError(
                    en = "Could not delete sample tasks.",
                    de = "Beispielaufgaben konnten nicht gelöscht werden.",
                    fr = "Impossible de supprimer les tâches d'exemple.",
                    it = "Impossibile eliminare le attività di esempio.",
                    es = "No se pudieron eliminar las tareas de ejemplo."
                )
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
        safeVmScope.launch {
            runCatchingCancellable {
                syncRepository.setLocalOnlyDbModePreference(localOnly)
                if (localOnly) {
                    syncRepository.signOutAccount()
                }
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
                val count = statistics.value.openTasksCount +
                    statistics.value.completedTasksCount +
                    statistics.value.canceledTasksCount
                _csvStatusMessage.value = getLocalizedString(R.string.csv_status_exported, count)
                onCsvReady(csv)
            }.onFailure { err ->
                val msg = getLocalizedString(
                    R.string.csv_status_export_failed,
                    err.localizedMessage ?: err.message.orEmpty()
                )
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
                        ?: throw IllegalStateException("Cannot open output stream for URI")
                    outputStream.use { out ->
                        out.write(csvContent.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
            }.onSuccess {
                val totalExported = statistics.value.openTasksCount +
                    statistics.value.completedTasksCount +
                    statistics.value.canceledTasksCount
                _csvStatusMessage.value = getLocalizedString(R.string.csv_status_exported, totalExported)
            }.onFailure { err ->
                Log.w(TAG, "CSV export to URI failed", err)
                val msg = getLocalizedString(
                    R.string.csv_status_export_failed,
                    err.localizedMessage ?: err.message.orEmpty()
                )
                _csvStatusMessage.value = msg
                _uiErrorMessage.value = msg
            }
        }
    }

    fun shareTasksCsv() {
        safeVmScope.launch {
            runCatchingCancellable {
                val csvContent = repository.exportTasksToCsv()
                val shareTitle = getLocalizedString(R.string.settings_csv_share)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Thingsbook CSV (${LocalDate.now()})")
                    putExtra(Intent.EXTRA_TEXT, csvContent)
                }
                val chooser = Intent.createChooser(sendIntent, shareTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(chooser)
            }.onSuccess {
                _csvStatusMessage.value = getLocalizedString(R.string.csv_status_shared)
            }.onFailure { err ->
                Log.w(TAG, "Sharing CSV failed", err)
                val msg = getLocalizedString(
                    R.string.csv_status_export_failed,
                    err.localizedMessage ?: err.message.orEmpty()
                )
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
                _csvStatusMessage.value = getLocalizedString(R.string.csv_status_imported, count)
            }.onFailure { err ->
                val msg = getLocalizedString(
                    R.string.csv_status_import_failed,
                    err.localizedMessage ?: err.message.orEmpty()
                )
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
                        ?: throw IllegalStateException("Cannot open input stream for CSV URI")
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
                                throw IllegalArgumentException("CSV > 10 MB")
                            }
                            sb.append(buffer, 0, read)
                        }
                        sb.toString()
                    }
                }
                repository.importTasksFromCsv(csvText)
            }.onSuccess { count ->
                _csvStatusMessage.value = getLocalizedString(R.string.csv_status_imported, count)
            }.onFailure { err ->
                Log.w(TAG, "CSV import from URI failed", err)
                val msg = getLocalizedString(
                    R.string.csv_status_import_failed,
                    err.localizedMessage ?: err.message.orEmpty()
                )
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
        val langTag = _appLanguageTag.value
        return BackupPreferencesDto(
            sidebarVisible = _isSidebarVisible.value,
            dailyDigestEnabled = digest?.enabled,
            dailyDigestHour = digest?.hour,
            dailyDigestMinute = digest?.minute,
            languageTag = langTag,
            calendarEventsEnabled = _calendarEventsEnabled.value,
            selectedCalendarIds = _selectedCalendarIds.value.sorted(),
            groupTodayByProjectOrArea = _groupTodayByProjectOrArea.value,
            lastWeeklyReviewAt = _lastWeeklyReviewAt.value,
            includeBetaUpdates = _includeBetaUpdates.value,
            tagSortMode = _tagSortMode.value,
            viewSortMode = _viewSortMode.value
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
                            ?: throw IllegalStateException("Cannot open output stream for backup URI")
                        outputStream.use { out ->
                            out.write(backupBytes)
                            out.flush()
                        }
                        summary
                    }
                }.onSuccess { summary ->
                    _isBackupCreateDialogOpen.value = false
                    val lockBadge = getLocalizedString(
                        if (summary.encrypted) R.string.backup_badge_encrypted else R.string.backup_badge_unencrypted
                    )
                    _backupStatusMessage.value = getLocalizedString(
                        R.string.backup_status_saved,
                        lockBadge,
                        summary.tasksCount,
                        summary.projectsCount,
                        summary.areasCount,
                        summary.tagsCount
                    )
                }.onFailure { err ->
                    Log.w(TAG, "Full backup export failed", err)
                    val msg = getLocalizedString(
                        R.string.backup_status_failed,
                        err.localizedMessage ?: err.message.orEmpty()
                    )
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
                    val shareTitle = getLocalizedString(R.string.backup_share_tooltip)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_SUBJECT, "Thingsbook Backup (${LocalDate.now()})")
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(sendIntent, shareTitle).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    appContext.startActivity(chooser)
                    summary
                }.onSuccess { summary ->
                    _isBackupCreateDialogOpen.value = false
                    val lockBadge = getLocalizedString(
                        if (summary.encrypted) R.string.backup_badge_encrypted else R.string.backup_badge_unencrypted
                    )
                    _backupStatusMessage.value = getLocalizedString(R.string.backup_status_shared, lockBadge)
                }.onFailure { err ->
                    Log.w(TAG, "Sharing full backup failed", err)
                    val msg = getLocalizedString(
                        R.string.backup_status_failed,
                        err.localizedMessage ?: err.message.orEmpty()
                    )
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
                            ?: throw IllegalStateException("Cannot open input stream for backup URI")
                        val rawBytes = inputStream.use { stream ->
                            val out = ByteArrayOutputStream()
                            val buf = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val read = stream.read(buf)
                                if (read == -1) break
                                total += read
                                if (total > MAX_BACKUP_FILE_BYTES) {
                                    throw IllegalArgumentException("Backup > 80 MB")
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
                    val msg = getLocalizedString(
                        R.string.backup_status_restore_failed,
                        err.localizedMessage ?: err.message.orEmpty()
                    )
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
                    restoredPrefs.languageTag?.let { switchAppLanguage(it) }
                    restoredPrefs.selectedCalendarIds?.let { ids ->
                        setSelectedCalendarIds(ids.toSet())
                    }
                    restoredPrefs.calendarEventsEnabled?.let { enabled ->
                        setCalendarEventsEnabled(enabled && deviceCalendarRepository.hasCalendarPermission())
                    }
                    restoredPrefs.groupTodayByProjectOrArea?.let { grouped ->
                        setGroupTodayByProjectOrArea(grouped)
                    }
                    restoredPrefs.lastWeeklyReviewAt?.let { reviewedAt ->
                        setLastWeeklyReviewAt(reviewedAt)
                    }
                    restoredPrefs.includeBetaUpdates?.let { betaEnabled ->
                        setIncludeBetaUpdates(betaEnabled, triggerCheck = false)
                    }
                    restoredPrefs.tagSortMode?.let { setTagSortMode(it) }
                    restoredPrefs.viewSortMode?.let { setViewSortMode(it) }
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

                    _backupStatusMessage.value = getLocalizedString(
                        R.string.backup_status_restored,
                        summary.tasksCount,
                        summary.projectsCount,
                        summary.areasCount,
                        summary.tagsCount
                    )
                }.onFailure { err ->
                    Log.w(TAG, "Full restore failed", err)
                    if (err is InvalidBackupPasswordException || err is BackupPasswordRequiredException) {
                        _restoreDialogError.value = getLocalizedString(R.string.backup_err_wrong_password)
                    } else {
                        val detail = err.localizedMessage ?: err.message.orEmpty()
                        _restoreDialogError.value = detail
                        _backupStatusMessage.value = getLocalizedString(R.string.backup_status_restore_failed, detail)
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
            _expandedTaskId.value = null
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

    fun batchTogglePinSelectedTasks() {
        val ids = _selectedTaskIds.value.toList()
        if (ids.isEmpty()) return
        safeVmScope.launch {
            val entities = ids.mapNotNull { repository.getTaskEntityById(it) }.filter { !it.isDeleted }
            if (entities.isEmpty()) {
                _selectedTaskIds.value = emptySet()
                return@launch
            }
            val targetPinned = entities.any { !it.isPinned }
            repository.setTasksPinned(ids, targetPinned)
            _selectedTaskIds.value = emptySet()
        }
    }

    fun batchDuplicateSelectedTasks() {
        val ids = _selectedTaskIds.value.toList()
        if (ids.isEmpty()) return
        safeVmScope.launch {
            val copySuffix = getLocalizedString(R.string.v25_copy_suffix)
            ids.forEach { id ->
                repository.duplicateTask(id, copySuffix = copySuffix)
            }
            _selectedTaskIds.value = emptySet()
        }
    }

    fun batchUpdateSelectedTasksTags(tagIdsToAdd: Set<String>, tagIdsToRemove: Set<String>) {
        val ids = _selectedTaskIds.value.toList()
        if (ids.isEmpty() || (tagIdsToAdd.isEmpty() && tagIdsToRemove.isEmpty())) return
        safeVmScope.launch {
            repository.batchUpdateTasksTags(ids, tagIdsToAdd, tagIdsToRemove)
        }
    }

    fun batchCreateAndAssignTagToSelectedTasks(newTagTitle: String) {
        val ids = _selectedTaskIds.value.toList()
        val cleanTitle = newTagTitle.trim()
        if (ids.isEmpty() || cleanTitle.isEmpty()) return
        safeVmScope.launch {
            val newId = repository.createOrUpdateTag(title = cleanTitle)
            repository.batchUpdateTasksTags(ids, setOf(newId), emptySet())
        }
    }

    fun dismissTopmostOverlay(): Boolean {
        if (isLockedByBiometric()) return true
        return when {
            _uiErrorMessage.value != null -> {
                _uiErrorMessage.value = null
                true
            }
            _pendingProjectDeletePrompt.value != null -> {
                dismissDeleteProjectPrompt()
                true
            }
            _quickShareDraft.value != null -> {
                _quickShareDraft.value = null
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
            _isWeeklyReviewModalOpen.value -> {
                _isWeeklyReviewModalOpen.value = false
                true
            }
            _isTemplatesModalOpen.value -> {
                _isTemplatesModalOpen.value = false
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

    // =========================================================================
    // App Auto-Update & Manual "Search for Updates" (GitHub Releases + APK Installer)
    // =========================================================================

    private val _autoCheckUpdatesEnabled = MutableStateFlow(
        coreSettingsPrefs.getBoolean("auto_check_updates_enabled", true)
    )
    val autoCheckUpdatesEnabled: StateFlow<Boolean> = _autoCheckUpdatesEnabled.asStateFlow()

    private val _includeBetaUpdates = MutableStateFlow(
        coreSettingsPrefs.getBoolean(PREF_INCLUDE_BETA_UPDATES, BuildConfig.VERSION_NAME.contains("-"))
    )
    val includeBetaUpdates: StateFlow<Boolean> = _includeBetaUpdates.asStateFlow()

    private val _appUpdateState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val appUpdateState: StateFlow<AppUpdateUiState> = _appUpdateState.asStateFlow()

    private var activeUpdateJob: Job? = null

    fun setAutoCheckUpdatesEnabled(enabled: Boolean) {
        _autoCheckUpdatesEnabled.value = enabled
        runCatching {
            coreSettingsPrefs.edit().putBoolean("auto_check_updates_enabled", enabled).apply()
        }
        if (enabled && _appUpdateState.value is AppUpdateUiState.Idle) {
            checkForAppUpdates(manual = false)
        }
    }

    fun setIncludeBetaUpdates(enabled: Boolean, triggerCheck: Boolean = true) {
        _includeBetaUpdates.value = enabled
        runCatching {
            coreSettingsPrefs.edit().putBoolean(PREF_INCLUDE_BETA_UPDATES, enabled).apply()
        }
        if (_appUpdateState.value is AppUpdateUiState.UpToDate || _appUpdateState.value is AppUpdateUiState.UpdateAvailable) {
            _appUpdateState.value = AppUpdateUiState.Idle
        }
        if (enabled && triggerCheck) {
            checkForAppUpdates(manual = false)
        }
    }

    fun checkForAppUpdates(manual: Boolean) {
        val current = _appUpdateState.value
        if (current is AppUpdateUiState.Checking || current is AppUpdateUiState.Downloading) return

        val effectiveIncludeBeta = _includeBetaUpdates.value || BuildConfig.VERSION_NAME.contains("-")
        activeUpdateJob?.cancel()
        activeUpdateJob = safeVmScope.launch {
            _appUpdateState.value = AppUpdateUiState.Checking(isManual = manual)
            runCatchingCancellable {
                appUpdateManager.checkForUpdate(includeBeta = effectiveIncludeBeta)
            }.onSuccess { release ->
                if (
                    AppUpdateParser.isNewerVersion(
                        currentVersion = BuildConfig.VERSION_NAME,
                        candidateVersion = release.versionName,
                        includeBeta = effectiveIncludeBeta
                    )
                ) {
                    _appUpdateState.value = AppUpdateUiState.UpdateAvailable(
                        release = release,
                        showPromptBanner = true
                    )
                } else if (manual) {
                    _appUpdateState.value = AppUpdateUiState.UpToDate(
                        currentVersion = BuildConfig.VERSION_NAME
                    )
                } else {
                    _appUpdateState.value = AppUpdateUiState.Idle
                }
            }.onFailure { err ->
                if (manual) {
                    val detail = err.localizedMessage ?: err.message ?: "Network error"
                    _appUpdateState.value = AppUpdateUiState.Error(
                        message = getLocalizedString(R.string.settings_updates_check_failed, detail),
                        fallbackReleaseUrl = AppUpdateParser.RELEASES_PAGE_URL
                    )
                } else {
                    Log.w(TAG, "Silent startup update check failed", err)
                    _appUpdateState.value = AppUpdateUiState.Idle
                }
            }
        }
    }

    fun startUpdateDownloadAndInstall(release: AppReleaseInfo) {
        appUpdateManager.openApkDownloadInBrowser(release).onSuccess {
            val current = _appUpdateState.value
            if (current is AppUpdateUiState.UpdateAvailable) {
                _appUpdateState.value = current.copy(showPromptBanner = false)
            }
        }.onFailure { err ->
            val detail = err.localizedMessage ?: err.message ?: "Browser error"
            _appUpdateState.value = AppUpdateUiState.Error(
                message = getLocalizedString(R.string.settings_updates_download_failed, detail),
                fallbackReleaseUrl = release.releasePageUrl
            )
        }
    }

    fun cancelUpdateDownload() {
        val current = _appUpdateState.value as? AppUpdateUiState.Downloading ?: return
        activeUpdateJob?.cancel()
        activeUpdateJob = null
        _appUpdateState.value = AppUpdateUiState.UpdateAvailable(
            release = current.release,
            showPromptBanner = false
        )
    }

    fun installDownloadedUpdate() {
        val ready = _appUpdateState.value as? AppUpdateUiState.ReadyToInstall ?: return
        startUpdateDownloadAndInstall(ready.release)
    }

    // =========================================================================
    // v1.6.21: Optional Google / Device Calendar Integration (OFF by default)
    // =========================================================================

    private val _calendarEventsEnabled = MutableStateFlow(
        coreSettingsPrefs.getBoolean(PREF_CALENDAR_EVENTS_ENABLED, false)
    )
    val calendarEventsEnabled: StateFlow<Boolean> = _calendarEventsEnabled.asStateFlow()

    private val _hasCalendarPermission = MutableStateFlow(
        deviceCalendarRepository.hasCalendarPermission()
    )
    val hasCalendarPermission: StateFlow<Boolean> = _hasCalendarPermission.asStateFlow()

    private val _selectedCalendarIds = MutableStateFlow<Set<String>>(
        coreSettingsPrefs.getStringSet(PREF_SELECTED_CALENDAR_IDS, emptySet())
            ?.toSet()
            .orEmpty()
    )
    val selectedCalendarIds: StateFlow<Set<String>> = _selectedCalendarIds.asStateFlow()

    private val _availableCalendars = MutableStateFlow<List<DeviceCalendarInfo>>(emptyList())
    val availableCalendars: StateFlow<List<DeviceCalendarInfo>> = _availableCalendars.asStateFlow()

    private val _todayCalendarEvents = MutableStateFlow<List<CalendarEventItem>>(emptyList())
    val todayCalendarEvents: StateFlow<List<CalendarEventItem>> = _todayCalendarEvents.asStateFlow()

    private val _upcomingCalendarEventsByDate = MutableStateFlow<Map<LocalDate, List<CalendarEventItem>>>(emptyMap())
    val upcomingCalendarEventsByDate: StateFlow<Map<LocalDate, List<CalendarEventItem>>> =
        _upcomingCalendarEventsByDate.asStateFlow()

    private var activeCalendarRefreshJob: Job? = null

    fun hasReadCalendarPermission(): Boolean {
        val granted = deviceCalendarRepository.hasCalendarPermission()
        _hasCalendarPermission.value = granted
        return granted
    }

    fun setCalendarEventsEnabled(enabled: Boolean) {
        _calendarEventsEnabled.value = enabled
        runCatching {
            coreSettingsPrefs.edit().putBoolean(PREF_CALENDAR_EVENTS_ENABLED, enabled).apply()
        }
        val granted = deviceCalendarRepository.hasCalendarPermission()
        _hasCalendarPermission.value = granted
        if (!enabled || !granted) {
            activeCalendarRefreshJob?.cancel()
            _todayCalendarEvents.value = emptyList()
            _upcomingCalendarEventsByDate.value = emptyMap()
            return
        }
        refreshCalendarsAndEvents(selectDefaultIfFirstTime = true)
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        _hasCalendarPermission.value = granted
        if (granted) {
            _calendarEventsEnabled.value = true
            runCatching {
                coreSettingsPrefs.edit().putBoolean(PREF_CALENDAR_EVENTS_ENABLED, true).apply()
            }
            refreshCalendarsAndEvents(selectDefaultIfFirstTime = true)
        } else {
            _calendarEventsEnabled.value = false
            runCatching {
                coreSettingsPrefs.edit().putBoolean(PREF_CALENDAR_EVENTS_ENABLED, false).apply()
            }
            _todayCalendarEvents.value = emptyList()
            _upcomingCalendarEventsByDate.value = emptyMap()
        }
    }

    fun toggleCalendarSelection(calendarId: String) {
        val current = _selectedCalendarIds.value
        val next = if (calendarId in current) current - calendarId else current + calendarId
        setSelectedCalendarIds(next)
    }

    fun setAllCalendarsSelected(selectAll: Boolean) {
        val next = if (selectAll) {
            _availableCalendars.value.mapTo(LinkedHashSet()) { it.id }
        } else {
            emptySet()
        }
        setSelectedCalendarIds(next)
    }

    fun setSelectedCalendarIds(ids: Set<String>) {
        val copy = ids.toSet()
        _selectedCalendarIds.value = copy
        runCatching {
            coreSettingsPrefs.edit()
                .putStringSet(PREF_SELECTED_CALENDAR_IDS, HashSet(copy))
                .putBoolean(PREF_CALENDAR_IDS_INITIALIZED, true)
                .apply()
        }
        if (_calendarEventsEnabled.value && deviceCalendarRepository.hasCalendarPermission()) {
            refreshCalendarEventsOnly()
        } else {
            _todayCalendarEvents.value = emptyList()
            _upcomingCalendarEventsByDate.value = emptyMap()
        }
    }

    fun refreshCalendarsAndEvents(selectDefaultIfFirstTime: Boolean = false) {
        val granted = deviceCalendarRepository.hasCalendarPermission()
        _hasCalendarPermission.value = granted
        if (!_calendarEventsEnabled.value || !granted) {
            _todayCalendarEvents.value = emptyList()
            _upcomingCalendarEventsByDate.value = emptyMap()
            return
        }
        activeCalendarRefreshJob?.cancel()
        activeCalendarRefreshJob = safeVmScope.launch {
            val calendars = runCatchingCancellable {
                deviceCalendarRepository.queryAvailableCalendars()
            }.getOrDefault(emptyList())
            _availableCalendars.value = calendars

            val wasInitialized = coreSettingsPrefs.getBoolean(PREF_CALENDAR_IDS_INITIALIZED, false)
            if (selectDefaultIfFirstTime && !wasInitialized && _selectedCalendarIds.value.isEmpty() && calendars.isNotEmpty()) {
                val primaryIds = calendars.filter { it.isPrimary }.mapTo(LinkedHashSet()) { it.id }
                val initialIds = if (primaryIds.isNotEmpty()) {
                    primaryIds
                } else {
                    calendars.mapTo(LinkedHashSet()) { it.id }
                }
                _selectedCalendarIds.value = initialIds
                runCatching {
                    coreSettingsPrefs.edit()
                        .putStringSet(PREF_SELECTED_CALENDAR_IDS, HashSet(initialIds))
                        .putBoolean(PREF_CALENDAR_IDS_INITIALIZED, true)
                        .apply()
                }
            }

            queryAndPublishCalendarEvents()
        }
    }

    fun refreshCalendarEventsOnly() {
        val granted = deviceCalendarRepository.hasCalendarPermission()
        _hasCalendarPermission.value = granted
        if (!_calendarEventsEnabled.value || !granted) {
            _todayCalendarEvents.value = emptyList()
            _upcomingCalendarEventsByDate.value = emptyMap()
            return
        }
        activeCalendarRefreshJob?.cancel()
        activeCalendarRefreshJob = safeVmScope.launch {
            queryAndPublishCalendarEvents()
        }
    }

    private suspend fun queryAndPublishCalendarEvents() {
        val selectedIds = _selectedCalendarIds.value
        if (!_calendarEventsEnabled.value || selectedIds.isEmpty() || !deviceCalendarRepository.hasCalendarPermission()) {
            _todayCalendarEvents.value = emptyList()
            _upcomingCalendarEventsByDate.value = emptyMap()
            return
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val maxUpcomingTaskDate = upcomingTasks.value.mapNotNull { t ->
            (t.whenDate ?: t.deadline)?.let { millis ->
                runCatching { Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() }.getOrNull()
            }
        }.maxOrNull()
        val defaultEndDate = today.plusDays(UPCOMING_CALENDAR_HORIZON_DAYS)
        val endDateInclusive = if (maxUpcomingTaskDate != null && maxUpcomingTaskDate.isAfter(defaultEndDate)) {
            maxUpcomingTaskDate.coerceAtMost(today.plusDays(180))
        } else {
            defaultEndDate
        }

        val eventsByDate = runCatchingCancellable {
            deviceCalendarRepository.queryEventsForDateRange(
                selectedCalendarIds = selectedIds,
                startDate = today,
                endDateInclusive = endDateInclusive,
                zone = zone
            )
        }.getOrDefault(emptyMap())

        val nowMillis = System.currentTimeMillis()
        _todayCalendarEvents.value = DeviceCalendarRepository.filterActiveOrUpcomingEvents(
            events = eventsByDate[today].orEmpty(),
            forDate = today,
            todayDate = today,
            nowMillis = nowMillis,
            upcomingOnly = false
        )
        _upcomingCalendarEventsByDate.value = eventsByDate.entries
            .mapNotNull { (date, list) ->
                if (!date.isAfter(today)) {
                    null
                } else {
                    DeviceCalendarRepository.filterActiveOrUpcomingEvents(
                        events = list,
                        forDate = date,
                        todayDate = today,
                        nowMillis = nowMillis,
                        upcomingOnly = true
                    ).takeIf { it.isNotEmpty() }?.let { date to it }
                }
            }
            .toMap()
    }

    private fun prunePastCalendarEventsInMemory() {
        val currentToday = _todayCalendarEvents.value
        val currentUpcoming = _upcomingCalendarEventsByDate.value
        if (currentToday.isEmpty() && currentUpcoming.isEmpty()) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nowMillis = System.currentTimeMillis()

        val prunedToday = DeviceCalendarRepository.filterActiveOrUpcomingEvents(
            events = currentToday,
            forDate = today,
            todayDate = today,
            nowMillis = nowMillis,
            upcomingOnly = false
        )
        if (prunedToday != currentToday) {
            _todayCalendarEvents.value = prunedToday
        }

        val prunedUpcoming = currentUpcoming.entries
            .mapNotNull { (date, list) ->
                if (!date.isAfter(today)) {
                    null
                } else {
                    DeviceCalendarRepository.filterActiveOrUpcomingEvents(
                        events = list,
                        forDate = date,
                        todayDate = today,
                        nowMillis = nowMillis,
                        upcomingOnly = true
                    ).takeIf { it.isNotEmpty() }?.let { date to it }
                }
            }
            .toMap()
        if (prunedUpcoming != currentUpcoming) {
            _upcomingCalendarEventsByDate.value = prunedUpcoming
        }
    }

    // =========================================================================
    // v1.7.0 Power Features: Trash, Undo, Duplicate, Focus Timer, Quick Share,
    // Group Today by Project/Area & Biometric App Lock
    // =========================================================================

    private val _undoSnackbarMessage = MutableStateFlow<String?>(null)
    val undoSnackbarMessage: StateFlow<String?> = _undoSnackbarMessage.asStateFlow()

    private var pendingUndoSuspendBlock: (suspend () -> Unit)? = null
    private var activeUndoDismissJob: Job? = null

    private fun registerUndoAction(message: String, block: suspend () -> Unit) {
        activeUndoDismissJob?.cancel()
        pendingUndoSuspendBlock = block
        _undoSnackbarMessage.value = message
        activeUndoDismissJob = safeVmScope.launch {
            delay(5_000L)
            _undoSnackbarMessage.value = null
            pendingUndoSuspendBlock = null
        }
    }

    fun performUndo() {
        val block = pendingUndoSuspendBlock ?: return
        activeUndoDismissJob?.cancel()
        pendingUndoSuspendBlock = null
        _undoSnackbarMessage.value = null
        safeVmScope.launch {
            runCatchingCancellable { block() }
        }
    }

    fun dismissUndoSnackbar() {
        activeUndoDismissJob?.cancel()
        pendingUndoSuspendBlock = null
        _undoSnackbarMessage.value = null
    }

    fun restoreDeletedTask(taskId: String) {
        safeVmScope.launch {
            repository.restoreDeletedTask(taskId)
        }
    }

    fun restoreDeletedProject(projectId: String) {
        safeVmScope.launch {
            repository.restoreDeletedProject(projectId)
        }
    }

    fun permanentlyDeleteTask(taskId: String) {
        safeVmScope.launch {
            repository.permanentlyDeleteTask(taskId)
        }
    }

    fun permanentlyDeleteProject(projectId: String) {
        safeVmScope.launch {
            repository.permanentlyDeleteProject(projectId)
        }
    }

    fun emptyTrash() {
        safeVmScope.launch {
            repository.emptyTrash()
        }
    }

    fun toggleTaskPinned(taskId: String) {
        if (taskId.isBlank()) return
        safeVmScope.launch {
            val entity = repository.getTaskEntityById(taskId) ?: return@launch
            if (entity.isDeleted) return@launch
            repository.setTasksPinned(listOf(taskId), !entity.isPinned)
        }
    }

    fun duplicateTask(taskId: String) {
        safeVmScope.launch {
            val copySuffix = getLocalizedString(R.string.v25_copy_suffix)
            val clonedId = repository.duplicateTask(taskId, copySuffix = copySuffix)
            if (clonedId != null) {
                _expandedTaskId.value = clonedId
            }
        }
    }

    fun duplicateProject(projectId: String) {
        safeVmScope.launch {
            val copySuffix = getLocalizedString(R.string.v25_copy_suffix)
            val clonedId = repository.duplicateProject(projectId, copySuffix = copySuffix)
            if (clonedId != null) {
                _expandedTaskId.value = null
                _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(clonedId)
            }
        }
    }

    // Focus / Pomodoro Timer (25m)
    private val _focusTimerState = MutableStateFlow<FocusTimerUiState?>(null)
    val focusTimerState: StateFlow<FocusTimerUiState?> = _focusTimerState.asStateFlow()
    private var focusTimerJob: Job? = null

    private fun findLoadedTaskDurationMinutes(taskId: String): Int? {
        return (todayDaytimeTasks.value.firstOrNull { it.id == taskId }
            ?: thisEveningTasks.value.firstOrNull { it.id == taskId }
            ?: activeProjectTasks.value.firstOrNull { it.id == taskId }
            ?: activeAreaTasks.value.firstOrNull { it.id == taskId }
            ?: inboxTasks.value.firstOrNull { it.id == taskId }
            ?: anytimeTasks.value.firstOrNull { it.id == taskId }
            ?: upcomingTasks.value.firstOrNull { it.id == taskId }
            ?: somedayTasks.value.firstOrNull { it.id == taskId })
            ?.durationMinutes
    }

    fun startFocusTimer(taskId: String, taskTitle: String, durationMinutes: Int? = null) {
        focusTimerJob?.cancel()
        val resolvedMins = (durationMinutes
            ?: findLoadedTaskDurationMinutes(taskId)
            ?: TimeblockScheduleUtils.DEFAULT_FOCUS_DURATION_MINUTES).coerceIn(1, 240)
        val totalSecs = resolvedMins * 60
        val initial = FocusTimerUiState(
            taskId = taskId,
            taskTitle = taskTitle,
            totalSeconds = totalSecs,
            remainingSeconds = totalSecs,
            isPaused = false
        )
        _focusTimerState.value = initial
        reminderScheduler.showOrUpdateFocusTimerNotification(taskId, taskTitle, totalSecs, false)
        startFocusTimerTicker()
    }

    private fun startFocusTimerTicker() {
        focusTimerJob?.cancel()
        focusTimerJob = safeVmScope.launch {
            while (true) {
                delay(1_000L)
                val current = _focusTimerState.value ?: break
                if (current.isPaused) continue
                val nextRemaining = current.remainingSeconds - 1
                if (nextRemaining <= 0) {
                    _focusTimerState.value = null
                    reminderScheduler.showFocusTimerFinishedNotification(current.taskId, current.taskTitle)
                    break
                } else {
                    val updated = current.copy(remainingSeconds = nextRemaining)
                    _focusTimerState.value = updated
                    if (nextRemaining % 5 == 0 || nextRemaining <= 10) {
                        reminderScheduler.showOrUpdateFocusTimerNotification(
                            updated.taskId,
                            updated.taskTitle,
                            updated.remainingSeconds,
                            updated.isPaused
                        )
                    }
                }
            }
        }
    }

    fun toggleFocusTimerPause() {
        val current = _focusTimerState.value ?: return
        val next = current.copy(isPaused = !current.isPaused)
        _focusTimerState.value = next
        reminderScheduler.showOrUpdateFocusTimerNotification(
            next.taskId,
            next.taskTitle,
            next.remainingSeconds,
            next.isPaused
        )
    }

    fun addFiveMinutesToFocusTimer() {
        val current = _focusTimerState.value ?: return
        val next = current.copy(
            totalSeconds = current.totalSeconds + 300,
            remainingSeconds = current.remainingSeconds + 300
        )
        _focusTimerState.value = next
        reminderScheduler.showOrUpdateFocusTimerNotification(
            next.taskId,
            next.taskTitle,
            next.remainingSeconds,
            next.isPaused
        )
    }

    fun completeFocusTimerTask() {
        val current = _focusTimerState.value ?: return
        stopFocusTimer()
        updateTaskStatus(current.taskId, TaskStatus.COMPLETED)
    }

    fun stopFocusTimer() {
        focusTimerJob?.cancel()
        focusTimerJob = null
        _focusTimerState.value = null
        reminderScheduler.cancelFocusTimerNotification()
    }

    // Quick Share ("Share to Thingsbook" Intent Receiver)
    private val _quickShareDraft = MutableStateFlow<QuickShareDraft?>(null)
    val quickShareDraft: StateFlow<QuickShareDraft?> = _quickShareDraft.asStateFlow()

    fun openQuickShareDraft(rawSubject: String?, rawText: String?) {
        if (isLockedByBiometric()) return
        val cleanSubject = rawSubject?.trim().orEmpty()
        val cleanText = rawText?.trim().orEmpty()
        if (cleanSubject.isEmpty() && cleanText.isEmpty()) return

        val (initialTitle, initialNotes) = when {
            cleanSubject.isNotEmpty() && cleanText.isNotEmpty() && cleanSubject != cleanText -> {
                cleanSubject to cleanText
            }
            cleanText.contains("\n") -> {
                val firstLine = cleanText.lineSequence().first().trim().take(120)
                val rest = cleanText.substringAfter("\n").trim()
                firstLine to rest
            }
            cleanText.startsWith("http://", ignoreCase = true) || cleanText.startsWith("https://", ignoreCase = true) -> {
                val hostLabel = cleanText
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .substringBefore("/")
                    .ifBlank { cleanText.take(60) }
                hostLabel to cleanText
            }
            else -> {
                cleanText.ifEmpty { cleanSubject }.take(120) to ""
            }
        }
        _quickShareDraft.value = QuickShareDraft(
            initialTitle = initialTitle,
            initialNotes = initialNotes
        )
    }

    fun dismissQuickShareDraft() {
        _quickShareDraft.value = null
    }

    fun saveQuickShareTask(
        title: String,
        notes: String,
        scheduleState: ScheduleState,
        projectId: String?,
        areaId: String?
    ) {
        if (isLockedByBiometric()) return
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        _quickShareDraft.value = null
        safeVmScope.launch {
            val zone = ZoneId.systemDefault()
            val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val whenDate = if (scheduleState == ScheduleState.TODAY || scheduleState == ScheduleState.THIS_EVENING) {
                todayEpoch
            } else null
            val createdId = repository.createOrUpdateTask(
                title = cleanTitle,
                notes = notes,
                status = TaskStatus.OPEN,
                scheduleState = scheduleState,
                whenDate = whenDate,
                projectId = projectId,
                areaId = areaId,
                sortOrder = 500.0
            )
            when {
                projectId != null -> _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(projectId)
                areaId != null -> _navigationTarget.value = ActiveNavigationTarget.AreaDetail(areaId)
                scheduleState == ScheduleState.INBOX -> _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.INBOX)
                scheduleState == ScheduleState.TODAY || scheduleState == ScheduleState.THIS_EVENING ->
                    _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.TODAY)
                scheduleState == ScheduleState.ANYTIME -> _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.ANYTIME)
                scheduleState == ScheduleState.SOMEDAY -> _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.SOMEDAY)
                else -> Unit
            }
            _expandedTaskId.value = createdId
        }
    }

    fun openVoiceQuickCapture() {
        if (isLockedByBiometric()) return
        _quickShareDraft.value = QuickShareDraft(
            initialTitle = "",
            initialNotes = "",
            autoLaunchVoice = true
        )
    }

    private val _isSmartParsingGemini = MutableStateFlow(false)
    val isSmartParsingGemini: StateFlow<Boolean> = _isSmartParsingGemini.asStateFlow()

    fun smartParseQuickCaptureWithGemini(
        spokenDump: String,
        fallbackSchedule: ScheduleState,
        projectId: String?,
        areaId: String?,
        locale: Locale = Locale.getDefault()
    ) {
        if (isLockedByBiometric()) return
        val cleanDump = spokenDump.trim()
        if (cleanDump.isEmpty() || _isSmartParsingGemini.value) return
        safeVmScope.launch {
            _isSmartParsingGemini.value = true
            try {
                val parsedTasks = geminiService.parseBrainDumpToTasks(cleanDump, locale)
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)

                var firstCreatedId: String? = null
                parsedTasks.forEachIndexed { idx, dto ->
                    val normalizedState = dto.scheduleState.trim().uppercase(Locale.ROOT)
                    val parsedState = runCatching { ScheduleState.valueOf(normalizedState) }
                        .getOrDefault(fallbackSchedule)
                    val effectiveState = if (parsedTasks.size == 1 && normalizedState == "INBOX") {
                        fallbackSchedule
                    } else {
                        parsedState
                    }
                    val whenEpoch = dto.whenDaysOffset?.let { offset ->
                        runCatching {
                            today.plusDays(offset.coerceIn(-365, 3650).toLong())
                                .atStartOfDay(zone)
                                .toInstant()
                                .toEpochMilli()
                        }.getOrNull()
                    } ?: if (effectiveState == ScheduleState.TODAY || effectiveState == ScheduleState.THIS_EVENING) {
                        today.atStartOfDay(zone).toInstant().toEpochMilli()
                    } else {
                        null
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
                        scheduleState = effectiveState,
                        whenDate = whenEpoch,
                        deadline = deadlineEpoch,
                        projectId = projectId,
                        areaId = areaId,
                        sortOrder = (idx + 1) * 500.0 + (System.currentTimeMillis() % 1000)
                    )
                    if (dto.checklist.isNotEmpty()) {
                        repository.addChecklistItemsBatch(taskId, dto.checklist)
                    }
                    if (idx == 0) firstCreatedId = taskId
                }

                _quickShareDraft.value = null
                when {
                    projectId != null -> _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(projectId)
                    areaId != null -> _navigationTarget.value = ActiveNavigationTarget.AreaDetail(areaId)
                    fallbackSchedule == ScheduleState.INBOX -> _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.INBOX)
                    else -> _navigationTarget.value = ActiveNavigationTarget.SmartView(SmartViewType.TODAY)
                }
                if (firstCreatedId != null) {
                    _expandedTaskId.value = firstCreatedId
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Smart Parse Quick Capture failed", t)
                _uiErrorMessage.value = t.localizedMessage ?: t.message ?: localizedFallbackError(
                    en = "Smart Parse failed.",
                    de = "Smart Parse fehlgeschlagen.",
                    fr = "Échec de Smart Parse.",
                    it = "Smart Parse non riuscito.",
                    es = "Error en Smart Parse."
                )
            } finally {
                _isSmartParsingGemini.value = false
            }
        }
    }

    // =========================================================================
    // v1.8.0 Attachments (Task & Project Photos, PDFs & Documents)
    // =========================================================================

    fun addTaskAttachment(
        taskId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ) {
        if (bytes.isEmpty()) return
        safeVmScope.launch {
            runCatchingCancellable {
                repository.addAttachmentFromBytes(
                    taskId = taskId,
                    projectId = null,
                    fileName = fileName,
                    mimeType = mimeType,
                    rawBytes = bytes
                )
            }.onFailure { err ->
                _uiErrorMessage.value = err.localizedMessage ?: localizedFallbackError(
                    en = "Could not save attachment.",
                    de = "Anhang konnte nicht gespeichert werden.",
                    fr = "Impossible d'enregistrer la pièce jointe.",
                    it = "Impossibile salvare l'allegato.",
                    es = "No se pudo guardar el archivo adjunto."
                )
            }
        }
    }

    fun addProjectAttachment(
        projectId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ) {
        if (bytes.isEmpty()) return
        safeVmScope.launch {
            runCatchingCancellable {
                repository.addAttachmentFromBytes(
                    taskId = null,
                    projectId = projectId,
                    fileName = fileName,
                    mimeType = mimeType,
                    rawBytes = bytes
                )
            }.onFailure { err ->
                _uiErrorMessage.value = err.localizedMessage ?: localizedFallbackError(
                    en = "Could not save attachment.",
                    de = "Anhang konnte nicht gespeichert werden.",
                    fr = "Impossible d'enregistrer la pièce jointe.",
                    it = "Impossibile salvare l'allegato.",
                    es = "No se pudo guardar el archivo adjunto."
                )
            }
        }
    }

    fun deleteAttachment(attachmentId: String) {
        if (attachmentId.isBlank()) return
        safeVmScope.launch {
            runCatchingCancellable {
                repository.softDeleteAttachment(attachmentId)
            }.onFailure { err ->
                _uiErrorMessage.value = err.localizedMessage ?: localizedFallbackError(
                    en = "Could not delete attachment.",
                    de = "Anhang konnte nicht gelöscht werden.",
                    fr = "Impossible de supprimer la pièce jointe.",
                    it = "Impossibile eliminare l'allegato.",
                    es = "No se pudo eliminar el archivo adjunto."
                )
            }
        }
    }

    // Group Today by Project / Area
    private val _groupTodayByProjectOrArea = MutableStateFlow(
        coreSettingsPrefs.getBoolean(PREF_GROUP_TODAY_BY_PROJECT_OR_AREA, false)
    )
    val groupTodayByProjectOrArea: StateFlow<Boolean> = _groupTodayByProjectOrArea.asStateFlow()

    fun setGroupTodayByProjectOrArea(enabled: Boolean) {
        _groupTodayByProjectOrArea.value = enabled
        runCatching {
            coreSettingsPrefs.edit().putBoolean(PREF_GROUP_TODAY_BY_PROJECT_OR_AREA, enabled).apply()
        }
    }

    // Biometric / PIN App Lock
    private val _biometricAppLockEnabled = MutableStateFlow(
        coreSettingsPrefs.getBoolean(PREF_BIOMETRIC_APP_LOCK_ENABLED, false)
    )
    val biometricAppLockEnabled: StateFlow<Boolean> = _biometricAppLockEnabled.asStateFlow()

    private val _isAppUnlocked = MutableStateFlow(
        !coreSettingsPrefs.getBoolean(PREF_BIOMETRIC_APP_LOCK_ENABLED, false)
    )
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    fun setBiometricAppLockEnabled(enabled: Boolean) {
        _biometricAppLockEnabled.value = enabled
        if (enabled) {
            // User just toggled it on while inside Settings -> keep current session unlocked until next stop/launch
            _isAppUnlocked.value = true
        } else {
            _isAppUnlocked.value = true
        }
        runCatching {
            coreSettingsPrefs.edit().putBoolean(PREF_BIOMETRIC_APP_LOCK_ENABLED, enabled).apply()
        }
    }

    fun markAppUnlocked() {
        _isAppUnlocked.value = true
    }

    fun onAppStopped() {
        if (_biometricAppLockEnabled.value) {
            _isAppUnlocked.value = false
        }
    }

    fun onAppResumed() {
        safeVmScope.launch {
            val changed = runCatchingCancellable {
                repository.refreshCurrentDayAndRollover()
            }.getOrDefault(false)
            if (changed) {
                runCatching {
                    com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.refreshAllWidgets(appContext)
                }
            }
            _hasCalendarPermission.value = deviceCalendarRepository.hasCalendarPermission()
            if (_calendarEventsEnabled.value && _hasCalendarPermission.value) {
                refreshCalendarsAndEvents(selectDefaultIfFirstTime = false)
            } else {
                _todayCalendarEvents.value = emptyList()
                _upcomingCalendarEventsByDate.value = emptyMap()
            }
        }
    }

    fun dismissUpdateBanner() {
        when (val current = _appUpdateState.value) {
            is AppUpdateUiState.UpdateAvailable -> {
                _appUpdateState.value = current.copy(showPromptBanner = false)
            }
            is AppUpdateUiState.UpToDate, is AppUpdateUiState.Error -> {
                _appUpdateState.value = AppUpdateUiState.Idle
            }
            else -> Unit
        }
    }

    fun openUpdateReleasePage(url: String) {
        appUpdateManager.openReleasePageInBrowser(url)
    }

    // =========================================================================
    // v2.0 Flagship Features: Weekly Review, Templates & Import, Timeblocking
    // =========================================================================

    private val _celebrationToastMessage = MutableStateFlow<String?>(null)
    val celebrationToastMessage: StateFlow<String?> = _celebrationToastMessage.asStateFlow()
    private var celebrationToastJob: Job? = null

    fun showCelebrationToast(message: String) {
        if (message.isBlank()) return
        celebrationToastJob?.cancel()
        _celebrationToastMessage.value = message
        celebrationToastJob = safeVmScope.launch {
            delay(4_500L)
            _celebrationToastMessage.value = null
        }
    }

    fun dismissCelebrationToast() {
        celebrationToastJob?.cancel()
        _celebrationToastMessage.value = null
    }

    // Pillar 1: Guided GTD Weekly Review + Stalled Project Detection
    private val _isWeeklyReviewModalOpen = MutableStateFlow(false)
    val isWeeklyReviewModalOpen: StateFlow<Boolean> = _isWeeklyReviewModalOpen.asStateFlow()

    private val _weeklyReviewInitialStep = MutableStateFlow(0)
    val weeklyReviewInitialStep: StateFlow<Int> = _weeklyReviewInitialStep.asStateFlow()

    private val _lastWeeklyReviewAt = MutableStateFlow<Long?>(
        coreSettingsPrefs.getLong(PREF_LAST_WEEKLY_REVIEW_AT, -1L).takeIf { it > 0L }
    )
    val lastWeeklyReviewAt: StateFlow<Long?> = combine(
        _lastWeeklyReviewAt,
        activeProjects
    ) { localPref, projects ->
        val maxProjectReview = projects.maxOfOrNull { it.lastReviewedAt ?: 0L }?.takeIf { it > 0L }
        listOfNotNull(localPref, maxProjectReview).maxOrNull()
    }.stateIn(
        safeVmScope,
        SharingStarted.WhileSubscribed(5000),
        _lastWeeklyReviewAt.value
    )

    fun openWeeklyReviewModal(initialStep: Int = 0) {
        _weeklyReviewInitialStep.value = initialStep.coerceIn(0, 3)
        _isWeeklyReviewModalOpen.value = true
    }

    fun closeWeeklyReviewModal() {
        _isWeeklyReviewModalOpen.value = false
    }

    private fun setLastWeeklyReviewAt(timestampMillis: Long) {
        if (timestampMillis <= 0L) return
        _lastWeeklyReviewAt.value = timestampMillis
        runCatching {
            coreSettingsPrefs.edit().putLong(PREF_LAST_WEEKLY_REVIEW_AT, timestampMillis).apply()
        }
    }

    fun completeWeeklyReview(toastMessage: String) {
        val now = System.currentTimeMillis()
        setLastWeeklyReviewAt(now)
        safeVmScope.launch {
            repository.markAllActiveProjectsReviewed(now)
        }
        _isWeeklyReviewModalOpen.value = false
        showCelebrationToast(toastMessage)
    }

    fun markProjectReviewed(projectId: String) {
        safeVmScope.launch {
            repository.markProjectReviewed(projectId)
        }
    }

    fun moveProjectToSomeday(projectId: String) {
        safeVmScope.launch {
            repository.moveProjectToSomeday(projectId)
        }
    }

    fun activateSomedayProjectToAnytime(projectId: String) {
        safeVmScope.launch {
            val proj = activeProjects.value.firstOrNull { it.id == projectId } ?: return@launch
            repository.createOrUpdateProject(
                id = proj.id,
                title = proj.title,
                notes = proj.notes,
                areaId = proj.areaId,
                status = proj.status,
                scheduleState = ScheduleState.ANYTIME,
                whenDate = null,
                deadline = proj.deadline,
                tagIds = proj.tagIds
            )
            repository.markProjectReviewed(projectId)
        }
    }

    fun addNextActionToProject(projectId: String, title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        safeVmScope.launch {
            val proj = activeProjects.value.firstOrNull { it.id == projectId }
            repository.createOrUpdateTask(
                title = cleanTitle,
                notes = "",
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.ANYTIME,
                whenDate = null,
                projectId = projectId,
                headingId = null,
                areaId = proj?.areaId,
                sortOrder = 500.0
            )
            repository.markProjectReviewed(projectId)
        }
    }

    // Pillar 2: Reusable Project Templates & Markdown / JSON Outline Importer
    private val _isTemplatesModalOpen = MutableStateFlow(false)
    val isTemplatesModalOpen: StateFlow<Boolean> = _isTemplatesModalOpen.asStateFlow()

    private val _templatesModalInitialTab = MutableStateFlow(0)
    val templatesModalInitialTab: StateFlow<Int> = _templatesModalInitialTab.asStateFlow()

    private val _templatesModalDefaultAreaId = MutableStateFlow<String?>(null)
    val templatesModalDefaultAreaId: StateFlow<String?> = _templatesModalDefaultAreaId.asStateFlow()

    fun openTemplatesModal(initialTab: Int = 0, defaultAreaId: String? = null) {
        _templatesModalInitialTab.value = initialTab.coerceIn(0, 1)
        _templatesModalDefaultAreaId.value = defaultAreaId
        _isTemplatesModalOpen.value = true
    }

    fun closeTemplatesModal() {
        _isTemplatesModalOpen.value = false
    }

    fun saveProjectAsTemplate(projectId: String, toastMessage: String) {
        safeVmScope.launch {
            val savedId = repository.saveProjectAsTemplate(projectId)
            if (savedId != null) {
                showCelebrationToast(toastMessage)
            }
        }
    }

    fun createProjectFromTemplate(template: ProjectTemplate, areaIdOverride: String? = null) {
        safeVmScope.launch {
            val createdProjectId = repository.createProjectFromTemplateBlueprint(
                blueprint = template.blueprint,
                areaIdOverride = areaIdOverride,
                defaultAreaId = template.defaultAreaId,
                templateTagIds = template.tagIds
            )
            _isTemplatesModalOpen.value = false
            _expandedTaskId.value = null
            _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(createdProjectId)
        }
    }

    fun deleteProjectTemplate(templateId: String) {
        if (templateId.isBlank()) return
        safeVmScope.launch {
            repository.deleteProjectTemplate(templateId)
        }
    }

    fun importProjectFromOutline(
        rawOutline: String,
        areaId: String? = null,
        fallbackTitle: String = "Imported Project"
    ) {
        if (rawOutline.isBlank()) return
        safeVmScope.launch {
            val createdProjectId = repository.importProjectFromOutline(
                rawOutline = rawOutline,
                areaId = areaId,
                fallbackTitle = fallbackTitle
            )
            _isTemplatesModalOpen.value = false
            _expandedTaskId.value = null
            _navigationTarget.value = ActiveNavigationTarget.ProjectDetail(createdProjectId)
        }
    }

    // Pillar 3: Interactive Timeblocking & Daily Timeline in Today
    private val _isTodayTimelineMode = MutableStateFlow(
        coreSettingsPrefs.getBoolean(PREF_TODAY_TIMELINE_MODE, false)
    )
    val isTodayTimelineMode: StateFlow<Boolean> = _isTodayTimelineMode.asStateFlow()

    fun setTodayTimelineMode(timelineMode: Boolean) {
        _isTodayTimelineMode.value = timelineMode
        runCatching {
            coreSettingsPrefs.edit().putBoolean(PREF_TODAY_TIMELINE_MODE, timelineMode).apply()
        }
    }

    fun updateTaskTimeblock(taskId: String, startMinutes: Int?, durationMinutes: Int?) {
        safeVmScope.launch {
            repository.updateTaskTimeblock(
                taskId = taskId,
                scheduledStartMinutes = startMinutes,
                durationMinutes = durationMinutes
            )
        }
    }

    fun shiftTaskTimeblock(taskId: String, deltaMinutes: Int) {
        safeVmScope.launch {
            val entity = repository.getTaskEntityById(taskId) ?: return@launch
            val currentStart = entity.scheduledStartMinutes ?: (9 * 60)
            val nextStart = (currentStart + deltaMinutes).coerceIn(0, 23 * 60 + 45)
            repository.updateTaskTimeblock(
                taskId = taskId,
                scheduledStartMinutes = nextStart,
                durationMinutes = entity.durationMinutes ?: TimeblockScheduleUtils.DEFAULT_TIMEBLOCK_DURATION_MINUTES
            )
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
                repository.refreshCurrentDayAndRollover()
            }.onFailure { err ->
                Log.w(TAG, "Failed initial day rollover", err)
            }
            runCatchingCancellable {
                repository.rescheduleAllOpenTaskReminders()
            }.onFailure { err ->
                Log.w(TAG, "Failed rescheduling open task reminders", err)
            }
            if (_calendarEventsEnabled.value && deviceCalendarRepository.hasCalendarPermission()) {
                refreshCalendarsAndEvents(selectDefaultIfFirstTime = false)
            }
        }
        // Automatic 00:00 Midnight Rollover Watcher while the app is running
        safeVmScope.launch {
            while (true) {
                val zone = ZoneId.systemDefault()
                val nowMillis = System.currentTimeMillis()
                val nextMidnightMillis = LocalDate.now(zone)
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli() + 50L
                val msUntilMidnight = (nextMidnightMillis - nowMillis).coerceAtLeast(1_000L)
                delay(minOf(msUntilMidnight, 15_000L))
                val changed = runCatchingCancellable {
                    repository.refreshCurrentDayAndRollover()
                }.getOrDefault(false)
                if (changed) {
                    runCatching {
                        com.culturedcode.things3.ui.widget.ThingsbookWidgetProvider.refreshAllWidgets(appContext)
                    }
                    if (_calendarEventsEnabled.value && deviceCalendarRepository.hasCalendarPermission()) {
                        refreshCalendarEventsOnly()
                    }
                } else if (_calendarEventsEnabled.value) {
                    prunePastCalendarEventsInMemory()
                }
            }
        }
        safeVmScope.launch {
            if (_autoCheckUpdatesEnabled.value) {
                delay(1_500L)
                if (_autoCheckUpdatesEnabled.value && _appUpdateState.value is AppUpdateUiState.Idle) {
                    checkForAppUpdates(manual = false)
                }
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
    }

    companion object {
        private const val TAG = "ThingsbookVM"
        private const val MAX_CSV_IMPORT_CHARS = 10 * 1024 * 1024
        private const val MAX_BACKUP_FILE_BYTES = 80 * 1024 * 1024
        private const val PREF_CALENDAR_EVENTS_ENABLED = "calendar_events_enabled"
        private const val PREF_SELECTED_CALENDAR_IDS = "selected_calendar_ids"
        private const val PREF_CALENDAR_IDS_INITIALIZED = "calendar_ids_initialized"
        private const val PREF_GROUP_TODAY_BY_PROJECT_OR_AREA = "group_today_by_project_or_area"
        private const val PREF_BIOMETRIC_APP_LOCK_ENABLED = "biometric_app_lock_enabled"
        private const val PREF_INCLUDE_BETA_UPDATES = "include_beta_updates"
        private const val PREF_LAST_WEEKLY_REVIEW_AT = "last_weekly_review_at"
        private const val PREF_TODAY_TIMELINE_MODE = "today_timeline_mode"
        private const val PREF_TAG_SORT_MODE = "tag_sort_mode"
        private const val PREF_VIEW_SORT_MODE = "view_sort_mode"
        private const val UPCOMING_CALENDAR_HORIZON_DAYS = 30L
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


