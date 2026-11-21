package com.pv.thingsbook.data.repository

import androidx.room.withTransaction
import com.pv.thingsbook.data.backup.BackupAreaDto
import com.pv.thingsbook.data.backup.BackupAttachmentDto
import com.pv.thingsbook.data.backup.BackupChecklistItemDto
import com.pv.thingsbook.data.backup.BackupHeadingDto
import com.pv.thingsbook.data.backup.BackupInspectionResult
import com.pv.thingsbook.data.backup.BackupPreferencesDto
import com.pv.thingsbook.data.backup.BackupProjectDto
import com.pv.thingsbook.data.backup.BackupProjectTagRefDto
import com.pv.thingsbook.data.backup.BackupProjectTemplateDto
import com.pv.thingsbook.data.backup.BackupSummary
import com.pv.thingsbook.data.backup.BackupTagDto
import com.pv.thingsbook.data.backup.BackupTaskDto
import com.pv.thingsbook.data.backup.BackupTaskTagRefDto
import com.pv.thingsbook.data.backup.ThingsBackupCodec
import com.pv.thingsbook.data.backup.ThingsbookBackupPayload
import com.pv.thingsbook.data.external.ExternalExportFormat
import com.pv.thingsbook.data.external.ExternalImportedProject
import com.pv.thingsbook.data.external.ExternalImportedTask
import com.pv.thingsbook.data.external.ExternalProjectCodec
import com.pv.thingsbook.data.local.DatabaseSeeder
import com.pv.thingsbook.data.local.ThingsDatabase
import com.pv.thingsbook.data.local.entity.AreaEntity
import com.pv.thingsbook.data.local.entity.AttachmentEntity
import com.pv.thingsbook.data.local.entity.ChecklistItemEntity
import com.pv.thingsbook.data.local.entity.HeadingEntity
import com.pv.thingsbook.data.local.entity.ProjectEntity
import com.pv.thingsbook.data.local.entity.ProjectTagCrossRef
import com.pv.thingsbook.data.local.entity.ProjectTemplateEntity
import com.pv.thingsbook.data.local.entity.ProjectWithProgressTuple
import com.pv.thingsbook.data.local.entity.TagEntity
import com.pv.thingsbook.data.local.entity.TaskEntity
import com.pv.thingsbook.data.local.entity.TaskTagCrossRef
import com.pv.thingsbook.data.local.entity.TaskWithDetails
import com.pv.thingsbook.data.notifications.ReminderScheduler
import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.ChecklistItem
import com.pv.thingsbook.domain.model.EffortLevel
import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.MAX_TASK_DURATION_MINUTES
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.ProjectOutlineImporter
import com.pv.thingsbook.domain.model.ProjectStatus
import com.pv.thingsbook.domain.model.ProjectTemplate
import com.pv.thingsbook.domain.model.ProjectTemplateBlueprint
import com.pv.thingsbook.domain.model.ProjectTemplateCodec
import com.pv.thingsbook.domain.model.QuickFindEntityScope
import com.pv.thingsbook.domain.model.QuickFindFilter
import com.pv.thingsbook.domain.model.RecurrenceMode
import com.pv.thingsbook.domain.model.RecurrenceRule
import com.pv.thingsbook.domain.model.RecurrenceUnit
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskAttachment
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.model.TemplateHeadingBlueprint
import com.pv.thingsbook.domain.model.TemplateTaskBlueprint
import com.pv.thingsbook.domain.model.TimeblockScheduleUtils
import com.pv.thingsbook.domain.model.wouldCreateDependencyCycle
import com.pv.thingsbook.domain.util.runCatchingCancellable
import com.pv.thingsbook.ui.components.SmartViewType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ThingsRepository @Inject constructor(
    private val database: ThingsDatabase,
    private val syncRepository: SyncRepository,
    private val reminderScheduler: ReminderScheduler
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private data class DetachedProjectTaskSnapshot(
        val taskId: String,
        val projectId: String,
        val headingId: String?
    )

    private val statusMutationMutex = Mutex()
    private val lastSpawnedRecurringTaskByCompletedId = ConcurrentHashMap<String, String>()
    private val detachedTasksByDeletedProjectId = ConcurrentHashMap<String, List<DetachedProjectTaskSnapshot>>()
    private val deletedTaskIdsByDeletedProjectId = ConcurrentHashMap<String, List<String>>()
    private val cascadeDeletedChecklistsByTaskId = ConcurrentHashMap<String, List<String>>()

    private fun tr(en: String, de: String, fr: String, it: String, es: String): String {
        return when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "de" -> de
            "fr" -> fr
            "it" -> it
            "es" -> es
            else -> en
        }
    }

    private fun endOfTodayEpochMillis(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
    }

    private val _endOfTodayMillis = MutableStateFlow(endOfTodayEpochMillis())
    private val _currentEpochDay = MutableStateFlow(LocalDate.now(ZoneId.systemDefault()).toEpochDay())
    val currentEpochDay: StateFlow<Long> = _currentEpochDay.asStateFlow()

    /**
     * Re-evaluates the current local day boundaries (e.g. when crossing 00:00 midnight, resuming the app,
     * or changing timezone) and rolls over any due UPCOMING tasks or yesterday's THIS_EVENING tasks to TODAY
     * without bumping updatedAt or dirtying rows, then deduplicates any duplicate open recurring tasks.
     */
    suspend fun refreshCurrentDayAndRollover(): Boolean {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val epochDay = today.toEpochDay()

        val dayChanged = _endOfTodayMillis.value != endOfToday || _currentEpochDay.value != epochDay
        _endOfTodayMillis.value = endOfToday
        _currentEpochDay.value = epochDay

        val promotedUpcoming = runCatchingCancellable {
            database.taskDao().rolloverDueUpcomingTasksToToday(endOfToday)
        }.getOrDefault(0)
        val promotedEvening = runCatchingCancellable {
            database.taskDao().rolloverYesterdayEveningTasksToToday(startOfToday)
        }.getOrDefault(0)
        val deduped = runCatchingCancellable {
            syncRepository.deduplicateOpenRecurringTasks()
        }.getOrDefault(0)

        if (deduped > 0) {
            syncRepository.triggerBackgroundSync()
        }
        return dayChanged || promotedUpcoming > 0 || promotedEvening > 0 || deduped > 0
    }

    fun observeInboxTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeInboxTasks()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayDaytimeTasks(): Flow<List<TaskItem>> =
        _endOfTodayMillis
            .flatMapLatest { endOfToday ->
                database.taskDao().observeTodayTasks(endOfToday)
            }
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeThisEveningTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeThisEveningTasks()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUpcomingTasks(): Flow<List<TaskItem>> =
        _endOfTodayMillis
            .flatMapLatest { endOfToday ->
                database.taskDao().observeUpcomingTasks(endOfToday)
            }
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeAnytimeTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeAnytimeTasks()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeSomedayTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeSomedayTasks()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeAllOpenTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeAllOpenActiveTasks()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeLogbookTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeLogbookTasks()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeDeletedTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeDeletedTasks()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeDeletedProjects(): Flow<List<Project>> =
        database.projectDao().observeDeletedProjects()
            .map { list ->
                list.mapNotNull { entity ->
                    runCatching {
                        Project(
                            id = entity.id,
                            areaId = entity.areaId,
                            title = entity.title,
                            notes = entity.notes,
                            status = entity.status,
                            scheduleState = entity.scheduleState,
                            whenDate = entity.whenDate,
                            deadline = entity.deadline,
                            completedAt = entity.completedAt,
                            lastReviewedAt = entity.lastReviewedAt,
                            sortOrder = entity.sortOrder,
                            tagIds = decodeStringList(entity.tagIdsJson),
                            workspaceId = entity.workspaceId,
                            viewMode = entity.viewMode,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt,
                            isDeleted = entity.isDeleted
                        )
                    }.getOrNull()
                }.distinctBy { it.id }
            }
            .catch { emit(emptyList()) }

    fun observeUserProjectTemplates(): Flow<List<ProjectTemplate>> =
        database.projectTemplateDao().observeActiveTemplates()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeTasksForProject(projectId: String): Flow<List<TaskItem>> =
        database.taskDao().observeTasksForProject(projectId)
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeAttachmentsForProject(projectId: String): Flow<List<TaskAttachment>> =
        database.attachmentDao().observeForProject(projectId)
            .map { list -> list.filter { !it.isDeleted }.map { it.toDomain() } }
            .catch { emit(emptyList()) }

    fun observeTasksForArea(areaId: String): Flow<List<TaskItem>> =
        database.taskDao().observeTasksForArea(areaId)
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeActiveProjectsWithProgress(): Flow<List<Project>> =
        database.projectDao().observeActiveProjectsWithProgress()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeAreas(): Flow<List<Area>> =
        database.areaDao().observeAreas()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeAllTags(): Flow<List<Tag>> =
        database.tagDao().observeAllTags()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeAllTasksRaw(): Flow<List<TaskEntity>> =
        database.taskDao().observeAllTasksRaw()
            .catch { emit(emptyList()) }

    fun observeAllProjectsRaw(): Flow<List<ProjectEntity>> =
        database.projectDao().observeAllProjectsRaw()
            .catch { emit(emptyList()) }

    suspend fun getTaskEntityById(taskId: String): TaskEntity? =
        runCatchingCancellable { database.taskDao().getById(taskId) }.getOrNull()

    suspend fun getActiveProjectsForWidget(): List<Project> =
        runCatchingCancellable { observeActiveProjectsWithProgress().first() }.getOrDefault(emptyList())

    suspend fun getTasksForWidgetProject(projectId: String): List<TaskItem> =
        runCatchingCancellable {
            observeTasksForProject(projectId).first().filter { it.status == TaskStatus.OPEN }
        }.getOrDefault(emptyList())

    suspend fun getTasksForWidgetView(viewName: String): List<TaskItem> {
        return runCatchingCancellable {
            refreshCurrentDayAndRollover()
            val normalized = viewName.trim()
            if (normalized.startsWith("PROJECT:", ignoreCase = true)) {
                val pId = normalized.substringAfter(':').trim()
                val activeProjects = getActiveProjectsForWidget()
                val targetProject = activeProjects.firstOrNull { it.id == pId } ?: activeProjects.firstOrNull()
                if (targetProject != null) {
                    return@runCatchingCancellable getTasksForWidgetProject(targetProject.id)
                }
                return@runCatchingCancellable emptyList()
            }
            when (normalized.uppercase(Locale.ROOT)) {
                "INBOX" -> observeInboxTasks().first()
                "UPCOMING" -> observeUpcomingTasks().first()
                "ANYTIME" -> observeAnytimeTasks().first()
                "SOMEDAY" -> observeSomedayTasks().first()
                "PROJECT" -> {
                    val firstProj = getActiveProjectsForWidget().firstOrNull()
                    if (firstProj != null) getTasksForWidgetProject(firstProj.id) else emptyList()
                }
                else -> (observeTodayDaytimeTasks().first() + observeThisEveningTasks().first()).distinctBy { it.id }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val MAX_AREAS = 8
    }

    fun observeAllActiveHeadings(): Flow<List<Heading>> =
        database.headingDao().observeAllActiveHeadings()
            .map { list ->
                list.mapNotNull {
                    runCatching {
                        Heading(
                            id = it.id,
                            projectId = it.projectId,
                            title = it.title,
                            startDate = it.startDate,
                            endDate = it.endDate,
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

    fun searchTasksFts5(rawQuery: String, filter: QuickFindFilter = QuickFindFilter()): Flow<List<TaskItem>> {
        if ((rawQuery.isBlank() && !filter.hasActiveFilters) || filter.scope == QuickFindEntityScope.PROJECTS) {
            return flowOf(emptyList())
        }
        val sql = ThingsDatabase.buildTasksFts5SqlQuery(rawQuery, filter.hideCompleted)
        return database.taskDao().searchTasksFts5(sql)
            .map { list ->
                list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }
                    .distinctBy { it.id }
                    .filter { filter.matchesTask(it) }
                    .take(50)
            }
            .catch { emit(emptyList()) }
    }

    fun searchProjectsFts5(rawQuery: String, filter: QuickFindFilter = QuickFindFilter()): Flow<List<Project>> {
        if (
            (rawQuery.isBlank() && !filter.hasActiveFilters) ||
            filter.scope == QuickFindEntityScope.TASKS ||
            filter.hasChecklistOnly
        ) {
            return flowOf(emptyList())
        }
        val sql = ThingsDatabase.buildProjectsFts5SqlQuery(rawQuery, filter.hideCompleted)
        return database.taskDao().searchProjectsFts5(sql)
            .map { list ->
                list.mapNotNull { entity ->
                    runCatching {
                        Project(
                            id = entity.id,
                            areaId = entity.areaId,
                            title = entity.title,
                            notes = entity.notes,
                            status = entity.status,
                            scheduleState = entity.scheduleState,
                            whenDate = entity.whenDate,
                            deadline = entity.deadline,
                            completedAt = entity.completedAt,
                            lastReviewedAt = entity.lastReviewedAt,
                            sortOrder = entity.sortOrder,
                            tagIds = decodeStringList(entity.tagIdsJson),
                            workspaceId = entity.workspaceId,
                            viewMode = entity.viewMode,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt,
                            isDeleted = entity.isDeleted
                        )
                    }.getOrNull()
                }
                    .distinctBy { it.id }
                    .filter { filter.matchesProject(it) }
                    .take(25)
            }
            .catch { emit(emptyList()) }
    }

    fun computeMidpointSortOrder(previousSortOrder: Double?, nextSortOrder: Double?): Double {
        val prev = previousSortOrder?.takeIf { it.isFinite() }
        val next = nextSortOrder?.takeIf { it.isFinite() }
        return when {
            prev == null && next == null -> 1000.0
            prev == null -> next!! - 500.0
            next == null -> prev + 1000.0
            abs(next - prev) < 1e-6 -> prev + 0.001
            else -> (prev + next) / 2.0
        }
    }

    suspend fun createOrUpdateTask(
        id: String = UUID.randomUUID().toString(),
        title: String,
        notes: String = "",
        status: TaskStatus = TaskStatus.OPEN,
        scheduleState: ScheduleState = ScheduleState.INBOX,
        whenDate: Long? = null,
        deadline: Long? = null,
        reminderTime: Long? = null,
        scheduledStartMinutes: Int? = null,
        durationMinutes: Int? = null,
        preserveExistingTimeblock: Boolean = true,
        projectId: String? = null,
        headingId: String? = null,
        areaId: String? = null,
        recurrenceRule: RecurrenceRule? = null,
        sortOrder: Double = 1000.0,
        isPinned: Boolean? = null,
        isMilestone: Boolean? = null,
        effortLevel: String? = null,
        blockedByTaskIds: List<String>? = null,
        workspaceId: String? = null,
        assigneeId: String? = null,
        tagIds: List<String> = emptyList()
    ): String {
        val now = System.currentTimeMillis()
        val existing = database.taskDao().getById(id)
        val completedAt = when (status) {
            TaskStatus.COMPLETED, TaskStatus.CANCELED -> existing?.completedAt ?: now
            TaskStatus.OPEN -> null
        }

        val normalizedSchedule = if (
            scheduleState == ScheduleState.INBOX &&
            whenDate == null &&
            recurrenceRule == null &&
            (projectId != null || areaId != null)
        ) {
            ScheduleState.ANYTIME
        } else {
            scheduleState
        }

        val resolvedStartMins = scheduledStartMinutes ?: if (preserveExistingTimeblock) existing?.scheduledStartMinutes else null
        val resolvedDurationMins = durationMinutes ?: if (preserveExistingTimeblock) existing?.durationMinutes else null
        val resolvedIsPinned = isPinned ?: existing?.isPinned ?: false
        val resolvedIsMilestone = isMilestone ?: existing?.isMilestone ?: false
        val resolvedEffortLevel = if (effortLevel != null) {
            EffortLevel.normalize(effortLevel)
        } else {
            existing?.effortLevel
        }
        val resolvedBlockedByJson = if (blockedByTaskIds != null) {
            val cleanBlockers = blockedByTaskIds.map { it.trim() }.filter { it.isNotEmpty() && it != id }.distinct()
            json.encodeToString(cleanBlockers)
        } else {
            existing?.blockedByJson ?: "[]"
        }
        val resolvedWorkspaceId = workspaceId ?: existing?.workspaceId
        val resolvedAssigneeId = assigneeId ?: existing?.assigneeId

        val entity = TaskEntity(
            id = id,
            title = title,
            notes = notes,
            status = status,
            scheduleState = normalizedSchedule,
            whenDate = whenDate,
            deadline = deadline,
            reminderTime = reminderTime,
            scheduledStartMinutes = resolvedStartMins?.takeIf { it in 0..1439 },
            durationMinutes = resolvedDurationMins?.takeIf { it in 1..MAX_TASK_DURATION_MINUTES },
            completedAt = completedAt,
            projectId = projectId,
            headingId = headingId,
            areaId = areaId,
            recurrenceRuleJson = recurrenceRule?.let { json.encodeToString(it) },
            sortOrder = if (sortOrder.isFinite()) sortOrder else 1000.0,
            isPinned = resolvedIsPinned,
            isMilestone = resolvedIsMilestone,
            effortLevel = resolvedEffortLevel,
            blockedByJson = resolvedBlockedByJson,
            workspaceId = resolvedWorkspaceId,
            assigneeId = resolvedAssigneeId,
            tagIdsJson = json.encodeToString(tagIds),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            isDeleted = false,
            isDirty = true
        )

        database.taskDao().upsertTaskWithTags(entity, tagIds)

        if (status == TaskStatus.OPEN && reminderTime != null && reminderTime > now) {
            reminderScheduler.scheduleExactReminder(id, title, reminderTime)
        } else {
            reminderScheduler.cancelReminder(id)
        }

        if (existing?.status == TaskStatus.OPEN && status == TaskStatus.COMPLETED && recurrenceRule != null) {
            spawnNextRecurrenceInstance(entity, recurrenceRule, tagIds, now)?.let { nextId ->
                lastSpawnedRecurringTaskByCompletedId[id] = nextId
            }
        } else if (existing != null && existing.status != TaskStatus.OPEN && status == TaskStatus.OPEN) {
            softDeleteSpawnedRecurrenceIfAny(id, entity, now)
        }

        syncRepository.triggerBackgroundSync()
        return id
    }

    suspend fun setTaskMilestone(taskId: String, isMilestone: Boolean) {
        val cleanId = taskId.trim()
        if (cleanId.isEmpty()) return
        val existing = database.taskDao().getById(cleanId) ?: return
        if (existing.isDeleted || existing.isMilestone == isMilestone) return
        val updatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt + 1L)
        database.taskDao().updateTaskMilestone(cleanId, isMilestone, updatedAt)
        syncRepository.triggerBackgroundSync()
    }

    suspend fun toggleTaskMilestone(taskId: String): Boolean {
        val cleanId = taskId.trim()
        if (cleanId.isEmpty()) return false
        val existing = database.taskDao().getById(cleanId) ?: return false
        if (existing.isDeleted) return false
        val next = !existing.isMilestone
        val updatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt + 1L)
        database.taskDao().updateTaskMilestone(cleanId, next, updatedAt)
        syncRepository.triggerBackgroundSync()
        return next
    }

    suspend fun setTasksPinned(taskIds: Collection<String>, isPinned: Boolean) {
        val distinctIds = taskIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinctIds.isEmpty()) return
        val now = System.currentTimeMillis()
        var mutated = false
        database.withTransaction {
            val existingTasks = distinctIds.chunked(500).flatMap { chunk ->
                database.taskDao().getByIds(chunk)
            }
            val updated = existingTasks.mapNotNull { task ->
                if (task.isDeleted || task.isPinned == isPinned) {
                    null
                } else {
                    task.copy(
                        isPinned = isPinned,
                        updatedAt = maxOf(now, task.updatedAt + 1L),
                        isDirty = true
                    )
                }
            }
            if (updated.isNotEmpty()) {
                database.taskDao().upsertAll(updated)
                mutated = true
            }
        }
        if (mutated) {
            syncRepository.triggerBackgroundSync()
        }
    }

    suspend fun updateTaskEffortAndDuration(
        taskId: String,
        effortLevel: String? = null,
        durationMinutes: Int? = null,
        updateEffort: Boolean = true,
        updateDuration: Boolean = true
    ) {
        val existing = database.taskDao().getById(taskId) ?: return
        if (existing.isDeleted) return
        val nextEffort = if (updateEffort) EffortLevel.normalize(effortLevel) else existing.effortLevel
        val nextDuration = if (updateDuration) durationMinutes?.takeIf { it > 0 }?.coerceIn(1, MAX_TASK_DURATION_MINUTES) else existing.durationMinutes
        if (nextEffort == existing.effortLevel && nextDuration == existing.durationMinutes) return
        val now = System.currentTimeMillis()
        database.taskDao().upsert(
            existing.copy(
                effortLevel = nextEffort,
                durationMinutes = nextDuration,
                updatedAt = maxOf(now, existing.updatedAt + 1L),
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateTaskBlockedBy(taskId: String, blockerIds: List<String>): Boolean {
        val cleanTaskId = taskId.trim()
        if (cleanTaskId.isEmpty()) return false
        val now = System.currentTimeMillis()
        var hadCycle = false
        var mutated = false

        database.withTransaction {
            val existing = database.taskDao().getById(cleanTaskId) ?: return@withTransaction
            if (existing.isDeleted) return@withTransaction

            val allNonDeleted = database.taskDao().getAllNonDeletedTasksList()
            val validTaskIds = allNonDeleted.mapTo(HashSet()) { it.id }
            val blockedByMap = HashMap<String, List<String>>(allNonDeleted.size)
            for (t in allNonDeleted) {
                if (t.id != cleanTaskId) {
                    blockedByMap[t.id] = decodeStringList(t.blockedByJson)
                }
            }

            val accepted = mutableListOf<String>()
            blockedByMap[cleanTaskId] = accepted

            for (rawCandidate in blockerIds) {
                val cand = rawCandidate.trim()
                if (cand.isEmpty()) continue
                if (cand !in validTaskIds && cand != cleanTaskId) continue
                if (wouldCreateDependencyCycle(cleanTaskId, cand, blockedByMap)) {
                    hadCycle = true
                } else if (cand !in accepted) {
                    accepted += cand
                }
            }

            val encoded = json.encodeToString(accepted)
            if (encoded != existing.blockedByJson) {
                database.taskDao().upsert(
                    existing.copy(
                        blockedByJson = encoded,
                        updatedAt = maxOf(now, existing.updatedAt + 1L),
                        isDirty = true
                    )
                )
                mutated = true
            }
        }

        if (mutated) {
            syncRepository.triggerBackgroundSync()
        }
        return !hadCycle
    }

    private suspend fun silentlyPurgeTaskEntities(targets: List<TaskEntity>, now: Long) {
        if (targets.isEmpty()) return
        for (target in targets) {
            reminderScheduler.cancelReminder(target.id)
        }
        database.withTransaction {
            for (target in targets) {
                if (target.isDeleted) continue
                val tombstoneAt = maxOf(now, target.updatedAt + 1L)
                database.checklistDao().softDeleteByTaskId(target.id, tombstoneAt)
                database.attachmentDao().softDeleteByTaskId(target.id, tombstoneAt)
                database.taskDao().upsertTaskWithTags(
                    target.copy(
                        title = "",
                        notes = "",
                        recurrenceRuleJson = null,
                        tagIdsJson = "[]",
                        scheduledStartMinutes = null,
                        durationMinutes = null,
                        isPinned = false,
                        isMilestone = false,
                        effortLevel = null,
                        blockedByJson = "[]",
                        workspaceId = null,
                        assigneeId = null,
                        isDeleted = true,
                        updatedAt = tombstoneAt,
                        isDirty = true
                    ),
                    emptyList()
                )
            }
        }
    }

    private suspend fun softDeleteSpawnedRecurrenceIfAny(
        completedTaskId: String,
        reopenedTask: TaskEntity,
        now: Long
    ) {
        val toPurgeById = LinkedHashMap<String, TaskEntity>()
        val spawnedNextId = lastSpawnedRecurringTaskByCompletedId.remove(completedTaskId)
        if (spawnedNextId != null && spawnedNextId != completedTaskId) {
            val spawnedTask = database.taskDao().getById(spawnedNextId)
            if (spawnedTask != null && !spawnedTask.isDeleted) {
                toPurgeById[spawnedTask.id] = spawnedTask
            }
        }
        val seriesKey = SyncRepository.buildRecurringSeriesKey(reopenedTask)
        if (seriesKey != null) {
            val openRecurring = database.taskDao().getOpenRecurringTasks()
            for (candidate in openRecurring) {
                if (candidate.id != completedTaskId &&
                    SyncRepository.buildRecurringSeriesKey(candidate) == seriesKey
                ) {
                    toPurgeById[candidate.id] = candidate
                }
            }
        }
        if (toPurgeById.isNotEmpty()) {
            silentlyPurgeTaskEntities(toPurgeById.values.toList(), now)
        }
    }

    private fun stepRecurrenceDateOnce(
        baseDate: LocalDate,
        rule: RecurrenceRule,
        safeInterval: Long
    ): LocalDate = when (rule.unit) {
        RecurrenceUnit.DAYS -> baseDate.plusDays(safeInterval)
        RecurrenceUnit.WEEKS -> {
            val validDays = rule.daysOfWeek.filter { it in 1..7 }.distinct().sorted()
            if (rule.mode == RecurrenceMode.FIXED_SCHEDULE && validDays.isNotEmpty()) {
                val currentDow = baseDate.dayOfWeek.value
                val nextDaySameWeek = validDays.firstOrNull { it > currentDow }
                if (nextDaySameWeek != null) {
                    baseDate.plusDays((nextDaySameWeek - currentDow).toLong())
                } else {
                    val firstDow = validDays.first()
                    val startOfTargetWeek = baseDate
                        .minusDays((currentDow - 1).toLong())
                        .plusWeeks(safeInterval)
                    startOfTargetWeek.plusDays((firstDow - 1).toLong())
                }
            } else {
                baseDate.plusWeeks(safeInterval)
            }
        }
        RecurrenceUnit.MONTHS -> {
            val targetMonthDate = baseDate.plusMonths(safeInterval)
            if (rule.mode == RecurrenceMode.FIXED_SCHEDULE && rule.dayOfMonth != null) {
                val maxDom = targetMonthDate.lengthOfMonth()
                val targetDom = if (rule.dayOfMonth == -1) maxDom else rule.dayOfMonth.coerceIn(1, maxDom)
                targetMonthDate.withDayOfMonth(targetDom)
            } else {
                targetMonthDate
            }
        }
        RecurrenceUnit.YEARS -> baseDate.plusYears(safeInterval)
    }

    private suspend fun spawnNextRecurrenceInstance(
        completedTask: TaskEntity,
        rule: RecurrenceRule,
        tagIds: List<String>,
        now: Long
    ): String? {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val safeInterval = rule.interval.coerceIn(1, 3650).toLong()
        val baseEpoch = when (rule.mode) {
            RecurrenceMode.AFTER_COMPLETION -> now
            RecurrenceMode.FIXED_SCHEDULE -> completedTask.whenDate ?: now
        }
        val baseDate = runCatching { Instant.ofEpochMilli(baseEpoch).atZone(zone).toLocalDate() }
            .getOrElse { today }

        var nextDate: LocalDate = stepRecurrenceDateOnce(baseDate, rule, safeInterval)
        if (rule.mode == RecurrenceMode.FIXED_SCHEDULE && !nextDate.isAfter(today)) {
            var guard = 0
            while (!nextDate.isAfter(today) && guard < 500) {
                val candidate = stepRecurrenceDateOnce(nextDate, rule, safeInterval)
                if (!candidate.isAfter(nextDate)) break
                nextDate = candidate
                guard++
            }
            if (!nextDate.isAfter(today)) {
                nextDate = stepRecurrenceDateOnce(today, rule, safeInterval)
            }
        }

        val nextWhenEpoch = nextDate.atStartOfDay(zone).toInstant().toEpochMilli()

        if (rule.endDateEpochMillis != null && nextWhenEpoch > rule.endDateEpochMillis) {
            return null
        }

        val nextDeadlineEpoch = if (completedTask.deadline != null && completedTask.whenDate != null) {
            val deltaMillis = nextWhenEpoch - completedTask.whenDate
            completedTask.deadline + deltaMillis
        } else {
            completedTask.deadline
        }

        val nextReminderTime = completedTask.reminderTime?.let { prevReminder ->
            runCatching {
                val prevLocalTime = Instant.ofEpochMilli(prevReminder).atZone(zone).toLocalTime()
                LocalDateTime.of(nextDate, prevLocalTime).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }

        val todayEpoch = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextScheduleState = when {
            nextWhenEpoch <= todayEpoch && completedTask.scheduleState == ScheduleState.THIS_EVENING ->
                ScheduleState.THIS_EVENING
            nextWhenEpoch <= todayEpoch -> ScheduleState.TODAY
            else -> ScheduleState.UPCOMING
        }

        val dateKey = String.format(
            Locale.US,
            "%04d-%02d-%02d",
            nextDate.year,
            nextDate.monthValue,
            nextDate.dayOfMonth
        )
        val nextTaskId = SyncRepository.deriveRecurringNextTaskId(completedTask.id, dateKey)
        val existingById = database.taskDao().getById(nextTaskId)
        val nextEntity = completedTask.copy(
            id = nextTaskId,
            status = TaskStatus.OPEN,
            scheduleState = nextScheduleState,
            whenDate = nextWhenEpoch,
            deadline = nextDeadlineEpoch,
            reminderTime = nextReminderTime,
            completedAt = null,
            isPinned = false,
            blockedByJson = "[]",
            createdAt = existingById?.createdAt ?: now,
            updatedAt = if (existingById != null) maxOf(now, existingById.updatedAt + 1L) else now,
            isDeleted = false,
            isDirty = true
        )

        if (SyncRepository.buildRecurringSeriesKey(nextEntity) != null) {
            val openSiblings = database.taskDao().getOpenRecurringTasks()
                .filter { it.id != completedTask.id && SyncRepository.areSameRecurringSeries(nextEntity, it) }
                .sortedWith(SyncRepository::compareRecurringTaskPriority)
            if (openSiblings.isNotEmpty()) {
                val primary = openSiblings.first()
                if (openSiblings.size > 1) {
                    silentlyPurgeTaskEntities(openSiblings.drop(1), now)
                }
                val primaryTags = decodeStringList(primary.tagIdsJson)
                val mergedTags = LinkedHashSet<String>(primaryTags).apply { addAll(tagIds) }.toList()
                val needsAdvance = (primary.whenDate ?: 0L) < nextWhenEpoch
                val enrichedArea = if (primary.projectId.isNullOrBlank() && primary.areaId.isNullOrBlank()) {
                    nextEntity.areaId
                } else {
                    primary.areaId
                }
                val enrichedHeading = if (!primary.projectId.isNullOrBlank() && primary.headingId.isNullOrBlank()) {
                    nextEntity.headingId
                } else {
                    primary.headingId
                }
                val enrichedNotes = primary.notes.ifBlank { nextEntity.notes }
                val enrichedStart = primary.scheduledStartMinutes ?: nextEntity.scheduledStartMinutes
                val enrichedDuration = primary.durationMinutes ?: nextEntity.durationMinutes
                val needsEnrich = enrichedArea != primary.areaId ||
                    enrichedHeading != primary.headingId ||
                    enrichedNotes != primary.notes ||
                    enrichedStart != primary.scheduledStartMinutes ||
                    enrichedDuration != primary.durationMinutes ||
                    mergedTags.size != primaryTags.size

                if (needsAdvance || needsEnrich) {
                    val effectiveReminder = if (needsAdvance) nextReminderTime else (primary.reminderTime ?: nextReminderTime)
                    val advanced = primary.copy(
                        whenDate = if (needsAdvance) nextWhenEpoch else primary.whenDate,
                        scheduleState = if (needsAdvance) nextScheduleState else primary.scheduleState,
                        deadline = if (needsAdvance) nextDeadlineEpoch else (primary.deadline ?: nextDeadlineEpoch),
                        reminderTime = effectiveReminder,
                        areaId = enrichedArea,
                        headingId = enrichedHeading,
                        notes = enrichedNotes,
                        scheduledStartMinutes = enrichedStart,
                        durationMinutes = enrichedDuration,
                        tagIdsJson = json.encodeToString(mergedTags),
                        updatedAt = maxOf(now, primary.updatedAt + 1L),
                        isDirty = true
                    )
                    database.taskDao().upsertTaskWithTags(advanced, mergedTags)
                    if (effectiveReminder != null && effectiveReminder > now) {
                        reminderScheduler.scheduleExactReminder(primary.id, advanced.title, effectiveReminder)
                    }
                }
                val siblingSubtasks = database.checklistDao().getForTaskSync(primary.id)
                val completedSubtasks = siblingSubtasks.filter { !it.isDeleted && it.isCompleted }
                if (completedSubtasks.isNotEmpty()) {
                    database.checklistDao().upsertAll(
                        completedSubtasks.map { chk ->
                            chk.copy(
                                isCompleted = false,
                                updatedAt = maxOf(now, chk.updatedAt + 1L),
                                isDirty = true
                            )
                        }
                    )
                }
                return primary.id
            }
        }

        database.taskDao().upsertTaskWithTags(nextEntity, tagIds)

        if (nextReminderTime != null && nextReminderTime > now) {
            reminderScheduler.scheduleExactReminder(nextTaskId, nextEntity.title, nextReminderTime)
        }

        val existingSubtasks = database.checklistDao().getForTaskSync(completedTask.id)
        if (existingSubtasks.isNotEmpty()) {
            val clonedSubtasks = existingSubtasks.map { item ->
                val nextChkId = SyncRepository.deriveRecurringChecklistItemId(nextTaskId, item.id)
                val existingChk = database.checklistDao().getById(nextChkId)
                item.copy(
                    id = nextChkId,
                    taskId = nextTaskId,
                    isCompleted = false,
                    createdAt = existingChk?.createdAt ?: now,
                    updatedAt = if (existingChk != null) maxOf(now, existingChk.updatedAt + 1L) else now,
                    isDeleted = false,
                    isDirty = true
                )
            }
            database.checklistDao().upsertAll(clonedSubtasks)
        }
        return nextTaskId
    }

    suspend fun updateTaskTitleAndNotes(taskId: String, newTitle: String, newNotes: String) {
        val existing = database.taskDao().getById(taskId) ?: return
        if (existing.title == newTitle && existing.notes == newNotes) return
        val now = System.currentTimeMillis()
        val titleChanged = existing.title != newTitle
        database.taskDao().upsert(
            existing.copy(
                title = newTitle,
                notes = newNotes,
                updatedAt = now,
                isDirty = true
            )
        )
        if (titleChanged && !existing.isDeleted && existing.status == TaskStatus.OPEN &&
            existing.reminderTime != null && existing.reminderTime > now
        ) {
            reminderScheduler.scheduleExactReminder(taskId, newTitle, existing.reminderTime)
        }
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateTaskSchedule(taskId: String, newState: ScheduleState, newWhenDate: Long?) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        database.taskDao().upsert(
            existing.copy(
                scheduleState = newState,
                whenDate = newWhenDate,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateTaskScheduleAndDeadline(
        taskId: String,
        newState: ScheduleState,
        newWhenDate: Long?,
        newDeadline: Long?
    ) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        database.taskDao().upsert(
            existing.copy(
                scheduleState = newState,
                whenDate = newWhenDate,
                deadline = newDeadline,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateTaskScheduleWithReminder(
        taskId: String,
        newState: ScheduleState,
        newWhenDate: Long?,
        newReminderTime: Long?
    ) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            scheduleState = newState,
            whenDate = newWhenDate,
            reminderTime = newReminderTime,
            updatedAt = now,
            isDirty = true
        )
        database.taskDao().upsert(updated)
        if (updated.status == TaskStatus.OPEN && newReminderTime != null && newReminderTime > now) {
            reminderScheduler.scheduleExactReminder(taskId, updated.title, newReminderTime)
        } else {
            reminderScheduler.cancelReminder(taskId)
        }
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateTaskDeadline(taskId: String, newDeadline: Long?) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        database.taskDao().upsert(
            existing.copy(
                deadline = newDeadline,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateTaskReminder(taskId: String, newReminderTime: Long?) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            reminderTime = newReminderTime,
            updatedAt = now,
            isDirty = true
        )
        database.taskDao().upsert(updated)
        if (updated.status == TaskStatus.OPEN && newReminderTime != null && newReminderTime > now) {
            reminderScheduler.scheduleExactReminder(taskId, updated.title, newReminderTime)
        } else {
            reminderScheduler.cancelReminder(taskId)
        }
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateTaskTags(taskId: String, newTagIds: List<String>) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            tagIdsJson = json.encodeToString(newTagIds),
            updatedAt = now,
            isDirty = true
        )
        database.taskDao().upsertTaskWithTags(updated, newTagIds)
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateTaskStatus(taskId: String, newStatus: TaskStatus) {
        val didMutate = statusMutationMutex.withLock {
            val existing = database.taskDao().getById(taskId) ?: return@withLock false
            if (existing.status == newStatus) return@withLock false
            val now = System.currentTimeMillis()
            val updated = existing.copy(
                status = newStatus,
                completedAt = if (newStatus == TaskStatus.OPEN) null else now,
                updatedAt = maxOf(now, existing.updatedAt + 1L),
                isDirty = true
            )
            database.taskDao().upsert(updated)

            if (newStatus != TaskStatus.OPEN) {
                reminderScheduler.cancelReminder(taskId)
            } else if (existing.reminderTime != null && existing.reminderTime > now) {
                reminderScheduler.scheduleExactReminder(taskId, existing.title, existing.reminderTime)
            }

            if (existing.status == TaskStatus.OPEN && newStatus == TaskStatus.COMPLETED && existing.recurrenceRuleJson != null) {
                val rule = runCatching { json.decodeFromString<RecurrenceRule>(existing.recurrenceRuleJson) }.getOrNull()
                if (rule != null) {
                    val tagIds = decodeStringList(existing.tagIdsJson)
                    spawnNextRecurrenceInstance(updated, rule, tagIds, now)?.let { nextId ->
                        lastSpawnedRecurringTaskByCompletedId[taskId] = nextId
                    }
                }
            } else if (existing.status != TaskStatus.OPEN && newStatus == TaskStatus.OPEN) {
                softDeleteSpawnedRecurrenceIfAny(taskId, updated, now)
            }
            true
        }
        if (didMutate) {
            syncRepository.triggerBackgroundSync()
        }
    }

    suspend fun deferTaskToThisEvening(taskId: String) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        reminderScheduler.cancelReminder(taskId)
        database.taskDao().upsert(
            existing.copy(
                scheduleState = ScheduleState.THIS_EVENING,
                whenDate = todayEpoch,
                reminderTime = null,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun snoozeTaskReminder(taskId: String, minutes: Int = 60) {
        val existing = database.taskDao().getById(taskId) ?: return
        if (existing.isDeleted || existing.status != TaskStatus.OPEN) return
        val now = System.currentTimeMillis()
        val safeMinutes = minutes.coerceIn(5, 1440)
        val snoozedMillis = now + safeMinutes * 60_000L
        val updated = existing.copy(
            reminderTime = snoozedMillis,
            updatedAt = now,
            isDirty = true
        )
        database.taskDao().upsert(updated)
        reminderScheduler.scheduleExactReminder(taskId, updated.title, snoozedMillis)
        syncRepository.triggerBackgroundSync()
    }

    suspend fun deferTaskToTomorrow(taskId: String) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val tomorrowDate = LocalDate.now(zone).plusDays(1)
        val tomorrowEpoch = tomorrowDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextReminder = existing.reminderTime?.let { prevReminder ->
            runCatching {
                val prevTime = Instant.ofEpochMilli(prevReminder).atZone(zone).toLocalTime()
                LocalDateTime.of(tomorrowDate, prevTime).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }
        val updated = existing.copy(
            scheduleState = ScheduleState.UPCOMING,
            whenDate = tomorrowEpoch,
            reminderTime = nextReminder,
            updatedAt = now,
            isDirty = true
        )
        database.taskDao().upsert(updated)
        if (updated.status == TaskStatus.OPEN && nextReminder != null && nextReminder > now) {
            reminderScheduler.scheduleExactReminder(taskId, updated.title, nextReminder)
        } else {
            reminderScheduler.cancelReminder(taskId)
        }
        syncRepository.triggerBackgroundSync()
    }

    suspend fun rescheduleAllOpenTaskReminders() {
        val now = System.currentTimeMillis()
        val tasks = database.taskDao().getOpenTasksWithFutureReminders(now)
        for (task in tasks) {
            val triggerAt = task.reminderTime ?: continue
            if (triggerAt > now) {
                reminderScheduler.scheduleExactReminder(task.id, task.title, triggerAt)
            }
        }
    }

    suspend fun updateTaskRecurrence(taskId: String, recurrenceRule: RecurrenceRule?) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val todayDate = LocalDate.now(zone)
        val todayEpoch = todayDate.atStartOfDay(zone).toInstant().toEpochMilli()

        val updatedWhen = if (recurrenceRule != null && existing.whenDate == null) {
            val validDays = recurrenceRule.daysOfWeek.filter { it in 1..7 }.distinct().sorted()
            if (recurrenceRule.mode == RecurrenceMode.FIXED_SCHEDULE &&
                recurrenceRule.unit == RecurrenceUnit.WEEKS &&
                validDays.isNotEmpty()
            ) {
                val currentDow = todayDate.dayOfWeek.value
                val nextInWeek = validDays.firstOrNull { it >= currentDow }
                val targetDate = if (nextInWeek != null) {
                    todayDate.plusDays((nextInWeek - currentDow).toLong())
                } else {
                    todayDate.minusDays((currentDow - 1).toLong()).plusWeeks(1).plusDays((validDays.first() - 1).toLong())
                }
                targetDate.atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                todayEpoch
            }
        } else {
            existing.whenDate
        }

        val updatedState = if (recurrenceRule != null) {
            if (updatedWhen != null && updatedWhen > endOfTodayEpochMillis()) {
                ScheduleState.UPCOMING
            } else if (existing.scheduleState == ScheduleState.INBOX || existing.scheduleState == ScheduleState.ANYTIME) {
                ScheduleState.TODAY
            } else {
                existing.scheduleState
            }
        } else {
            existing.scheduleState
        }

        database.taskDao().upsert(
            existing.copy(
                scheduleState = updatedState,
                whenDate = updatedWhen,
                recurrenceRuleJson = recurrenceRule?.let { json.encodeToString(it) },
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun moveTaskToContainer(
        taskId: String,
        projectId: String?,
        headingId: String?,
        areaId: String?
    ) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        val resolvedAreaId = if (projectId != null) {
            database.projectDao().getById(projectId)?.areaId ?: areaId
        } else {
            areaId
        }
        val resolvedSchedule = if (
            existing.scheduleState == ScheduleState.INBOX &&
            existing.whenDate == null &&
            existing.recurrenceRuleJson == null &&
            (projectId != null || resolvedAreaId != null)
        ) {
            ScheduleState.ANYTIME
        } else {
            existing.scheduleState
        }
        database.taskDao().upsert(
            existing.copy(
                projectId = projectId,
                headingId = if (projectId != null) headingId else null,
                areaId = resolvedAreaId,
                scheduleState = resolvedSchedule,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    /** Drag & Drop inside a Project: assigns the task to `headingId` (null = no heading) with a new `sortOrder`. */
    suspend fun repositionTaskInProject(taskId: String, headingId: String?, sortOrder: Double) {
        val existing = database.taskDao().getById(taskId) ?: return
        if (existing.projectId == null) return
        val now = System.currentTimeMillis()
        database.taskDao().upsert(
            existing.copy(
                headingId = headingId,
                sortOrder = if (sortOrder.isFinite()) sortOrder else existing.sortOrder,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    fun computeSequentialSortOrders(
        count: Int,
        beforeSortOrder: Double?,
        afterSortOrder: Double?,
        fallbackMaxSortOrder: Double? = null
    ): List<Double> {
        val safeCount = count.coerceAtLeast(0)
        if (safeCount == 0) return emptyList()
        val prev = beforeSortOrder?.takeIf { it.isFinite() }
        val next = afterSortOrder?.takeIf { it.isFinite() }
        return when {
            prev == null && next == null -> {
                val base = fallbackMaxSortOrder?.takeIf { it.isFinite() } ?: 0.0
                (0 until safeCount).map { idx -> base + (idx + 1) * 1000.0 }
            }
            prev == null && next != null -> {
                (0 until safeCount).map { idx -> next - (safeCount - idx) * 500.0 }
            }
            prev != null && next == null -> {
                (0 until safeCount).map { idx -> prev + (idx + 1) * 1000.0 }
            }
            else -> {
                val p = prev!!
                val n = next!!
                val low = minOf(p, n)
                val high = maxOf(p, n)
                val span = high - low
                if (span < 1e-5 * (safeCount + 1)) {
                    (0 until safeCount).map { idx -> low + (idx + 1) * 0.0001 }
                } else {
                    val step = span / (safeCount + 1).toDouble()
                    (0 until safeCount).map { idx -> low + step * (idx + 1) }
                }
            }
        }
    }

    suspend fun captureTaskDropSnapshots(taskIds: List<String>): List<TaskEntity> {
        val cleanIds = taskIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanIds.isEmpty()) return emptyList()
        return runCatchingCancellable {
            val byId = cleanIds.chunked(500).flatMap { chunk ->
                database.taskDao().getByIds(chunk)
            }.associateBy { it.id }
            cleanIds.mapNotNull { byId[it] }
        }.getOrDefault(emptyList())
    }

    suspend fun restoreTaskDropSnapshots(snapshots: List<TaskEntity>) {
        if (snapshots.isEmpty()) return
        val now = System.currentTimeMillis()
        val remindersToSchedule = mutableListOf<TaskEntity>()
        val remindersToCancel = mutableListOf<String>()
        database.withTransaction {
            for (snap in snapshots) {
                val current = database.taskDao().getById(snap.id)
                val restoredUpdatedAt = maxOf(now, (current?.updatedAt ?: snap.updatedAt) + 1L)
                val restoredEntity = snap.copy(
                    updatedAt = restoredUpdatedAt,
                    isDirty = true
                )
                if (current != null && current.isDeleted && !snap.isDeleted) {
                    removeTaskFromDeletedProjectTracking(snap.id)
                    val checklistIds = cascadeDeletedChecklistsByTaskId.remove(snap.id)
                    if (checklistIds != null) {
                        if (checklistIds.isNotEmpty()) {
                            database.checklistDao().restoreChecklistItemsByIds(checklistIds, restoredUpdatedAt)
                        }
                    } else {
                        database.checklistDao().restoreNonPurgedByTaskId(snap.id, restoredUpdatedAt)
                    }
                    database.attachmentDao().getAllByTaskId(snap.id)
                        .filter { it.isDeleted && it.fileName.isNotEmpty() }
                        .forEach { att ->
                            database.attachmentDao().upsert(
                                att.copy(isDeleted = false, updatedAt = restoredUpdatedAt, isDirty = true)
                            )
                        }
                } else if (current != null && !current.isDeleted && snap.isDeleted) {
                    database.checklistDao().softDeleteByTaskId(snap.id, restoredUpdatedAt)
                    database.attachmentDao().softDeleteByTaskId(snap.id, restoredUpdatedAt)
                }
                if (current != null && current.status != TaskStatus.OPEN && snap.status == TaskStatus.OPEN) {
                    softDeleteSpawnedRecurrenceIfAny(snap.id, restoredEntity, restoredUpdatedAt)
                }
                database.taskDao().upsert(restoredEntity)
                if (!restoredEntity.isDeleted &&
                    restoredEntity.status == TaskStatus.OPEN &&
                    restoredEntity.reminderTime != null &&
                    restoredEntity.reminderTime > now
                ) {
                    remindersToSchedule += restoredEntity
                } else {
                    remindersToCancel += restoredEntity.id
                }
            }
        }
        remindersToCancel.forEach { reminderScheduler.cancelReminder(it) }
        remindersToSchedule.forEach { t ->
            t.reminderTime?.let { rt -> reminderScheduler.scheduleExactReminder(t.id, t.title, rt) }
        }
        syncRepository.triggerBackgroundSync()
    }

    private suspend fun restoreTaskFromTrashInTx(task: TaskEntity, now: Long): TaskEntity {
        if (!task.isDeleted) return task
        removeTaskFromDeletedProjectTracking(task.id)
        val activeProject = task.projectId?.let { pId ->
            database.projectDao().getById(pId)?.takeIf { !it.isDeleted }
        }
        val activeArea = task.areaId?.let { aId ->
            database.areaDao().getById(aId)?.takeIf { !it.isDeleted }
        }
        val activeHeading = task.headingId?.let { hId ->
            if (activeProject != null) {
                database.headingDao().getById(hId)?.takeIf { !it.isDeleted && it.projectId == activeProject.id }
            } else null
        }
        val checklistIdsToRestore = cascadeDeletedChecklistsByTaskId.remove(task.id)
        if (checklistIdsToRestore != null) {
            if (checklistIdsToRestore.isNotEmpty()) {
                database.checklistDao().restoreChecklistItemsByIds(checklistIdsToRestore, now)
            }
        } else {
            database.checklistDao().restoreNonPurgedByTaskId(task.id, now)
        }
        database.attachmentDao().getAllByTaskId(task.id)
            .filter { it.isDeleted && it.fileName.isNotEmpty() }
            .forEach { att ->
                database.attachmentDao().upsert(
                    att.copy(isDeleted = false, updatedAt = now, isDirty = true)
                )
            }
        return task.copy(
            projectId = activeProject?.id,
            headingId = activeHeading?.id,
            areaId = activeArea?.id,
            isDeleted = false,
            updatedAt = maxOf(now, task.updatedAt + 1L),
            isDirty = true
        )
    }

    suspend fun dropTasksOntoSmartView(
        taskIds: List<String>,
        targetView: SmartViewType,
        todayMillis: Long,
        tomorrowMillis: Long
    ) {
        val orderedIds = taskIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (orderedIds.isEmpty()) return
        val now = System.currentTimeMillis()
        val remindersToCancel = mutableListOf<String>()
        val remindersToSchedule = mutableListOf<TaskEntity>()
        var mutated = false

        statusMutationMutex.withLock {
            database.withTransaction {
                val existingById = orderedIds.chunked(500).flatMap { chunk ->
                    database.taskDao().getByIds(chunk)
                }.associateBy { it.id }

                val tasksInOrder = orderedIds.mapNotNull { existingById[it] }
                if (tasksInOrder.isEmpty()) return@withTransaction

                for (rawTask in tasksInOrder) {
                    val stamp = maxOf(now, rawTask.updatedAt + 1L)
                    when (targetView) {
                        SmartViewType.TRASH -> {
                            if (!rawTask.isDeleted) {
                                cascadeDeletedChecklistsByTaskId[rawTask.id] =
                                    database.checklistDao().getActiveChecklistIdsByTaskId(rawTask.id)
                                database.checklistDao().softDeleteByTaskId(rawTask.id, stamp)
                                database.attachmentDao().softDeleteByTaskId(rawTask.id, stamp)
                                database.taskDao().upsert(
                                    rawTask.copy(
                                        isDeleted = true,
                                        updatedAt = stamp,
                                        isDirty = true
                                    )
                                )
                                remindersToCancel += rawTask.id
                                mutated = true
                            }
                        }

                        SmartViewType.LOGBOOK -> {
                            val base = restoreTaskFromTrashInTx(rawTask, stamp)
                            if (base.status != TaskStatus.COMPLETED) {
                                val updated = base.copy(
                                    status = TaskStatus.COMPLETED,
                                    completedAt = now,
                                    isDeleted = false,
                                    updatedAt = stamp,
                                    isDirty = true
                                )
                                database.taskDao().upsert(updated)
                                remindersToCancel += updated.id
                                if (base.status == TaskStatus.OPEN && base.recurrenceRuleJson != null) {
                                    val rule = runCatching {
                                        json.decodeFromString<RecurrenceRule>(base.recurrenceRuleJson)
                                    }.getOrNull()
                                    if (rule != null) {
                                        val tagIds = decodeStringList(base.tagIdsJson)
                                        spawnNextRecurrenceInstance(updated, rule, tagIds, now)?.let { nextId ->
                                            lastSpawnedRecurringTaskByCompletedId[updated.id] = nextId
                                        }
                                    }
                                }
                                mutated = true
                            } else if (rawTask.isDeleted) {
                                database.taskDao().upsert(base)
                                mutated = true
                            }
                        }

                        SmartViewType.INBOX -> {
                            val base = restoreTaskFromTrashInTx(rawTask, stamp)
                            val wasClosed = base.status != TaskStatus.OPEN
                            val updated = base.copy(
                                status = TaskStatus.OPEN,
                                completedAt = null,
                                scheduleState = ScheduleState.INBOX,
                                whenDate = null,
                                scheduledStartMinutes = null,
                                projectId = null,
                                headingId = null,
                                areaId = null,
                                isDeleted = false,
                                updatedAt = stamp,
                                isDirty = true
                            )
                            database.taskDao().upsert(updated)
                            if (wasClosed) {
                                softDeleteSpawnedRecurrenceIfAny(updated.id, updated, stamp)
                            }
                            if (updated.reminderTime != null && updated.reminderTime > now) {
                                remindersToSchedule += updated
                            }
                            mutated = true
                        }

                        SmartViewType.TODAY -> {
                            val base = restoreTaskFromTrashInTx(rawTask, stamp)
                            val wasClosed = base.status != TaskStatus.OPEN
                            val nextSchedule = if (base.scheduleState == ScheduleState.THIS_EVENING) {
                                ScheduleState.THIS_EVENING
                            } else {
                                ScheduleState.TODAY
                            }
                            val updated = base.copy(
                                status = TaskStatus.OPEN,
                                completedAt = null,
                                scheduleState = nextSchedule,
                                whenDate = todayMillis,
                                isDeleted = false,
                                updatedAt = stamp,
                                isDirty = true
                            )
                            database.taskDao().upsert(updated)
                            if (wasClosed) {
                                softDeleteSpawnedRecurrenceIfAny(updated.id, updated, stamp)
                            }
                            if (updated.reminderTime != null && updated.reminderTime > now) {
                                remindersToSchedule += updated
                            }
                            mutated = true
                        }

                        SmartViewType.UPCOMING -> {
                            val base = restoreTaskFromTrashInTx(rawTask, stamp)
                            val wasClosed = base.status != TaskStatus.OPEN
                            val resolvedWhen = if (base.whenDate != null && base.whenDate >= tomorrowMillis) {
                                base.whenDate
                            } else {
                                tomorrowMillis
                            }
                            val updated = base.copy(
                                status = TaskStatus.OPEN,
                                completedAt = null,
                                scheduleState = ScheduleState.UPCOMING,
                                whenDate = resolvedWhen,
                                isDeleted = false,
                                updatedAt = stamp,
                                isDirty = true
                            )
                            database.taskDao().upsert(updated)
                            if (wasClosed) {
                                softDeleteSpawnedRecurrenceIfAny(updated.id, updated, stamp)
                            }
                            if (updated.reminderTime != null && updated.reminderTime > now) {
                                remindersToSchedule += updated
                            }
                            mutated = true
                        }

                        SmartViewType.ANYTIME -> {
                            val base = restoreTaskFromTrashInTx(rawTask, stamp)
                            val wasClosed = base.status != TaskStatus.OPEN
                            val updated = base.copy(
                                status = TaskStatus.OPEN,
                                completedAt = null,
                                scheduleState = ScheduleState.ANYTIME,
                                whenDate = null,
                                scheduledStartMinutes = null,
                                isDeleted = false,
                                updatedAt = stamp,
                                isDirty = true
                            )
                            database.taskDao().upsert(updated)
                            if (wasClosed) {
                                softDeleteSpawnedRecurrenceIfAny(updated.id, updated, stamp)
                            }
                            if (updated.reminderTime != null && updated.reminderTime > now) {
                                remindersToSchedule += updated
                            }
                            mutated = true
                        }

                        SmartViewType.SOMEDAY -> {
                            val base = restoreTaskFromTrashInTx(rawTask, stamp)
                            val wasClosed = base.status != TaskStatus.OPEN
                            val updated = base.copy(
                                status = TaskStatus.OPEN,
                                completedAt = null,
                                scheduleState = ScheduleState.SOMEDAY,
                                whenDate = null,
                                scheduledStartMinutes = null,
                                isDeleted = false,
                                updatedAt = stamp,
                                isDirty = true
                            )
                            database.taskDao().upsert(updated)
                            if (wasClosed) {
                                softDeleteSpawnedRecurrenceIfAny(updated.id, updated, stamp)
                            }
                            if (updated.reminderTime != null && updated.reminderTime > now) {
                                remindersToSchedule += updated
                            }
                            mutated = true
                        }
                    }
                }
            }
        }

        remindersToCancel.forEach { reminderScheduler.cancelReminder(it) }
        remindersToSchedule.forEach { t ->
            t.reminderTime?.let { rt -> reminderScheduler.scheduleExactReminder(t.id, t.title, rt) }
        }
        if (mutated) {
            syncRepository.triggerBackgroundSync()
        }
    }

    suspend fun dropTasksOntoArea(taskIds: List<String>, areaId: String) {
        val orderedIds = taskIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (orderedIds.isEmpty() || areaId.isBlank()) return
        val now = System.currentTimeMillis()
        val remindersToSchedule = mutableListOf<TaskEntity>()
        var mutated = false

        database.withTransaction {
            val targetArea = database.areaDao().getById(areaId)?.takeIf { !it.isDeleted } ?: return@withTransaction
            val existingById = orderedIds.chunked(500).flatMap { chunk ->
                database.taskDao().getByIds(chunk)
            }.associateBy { it.id }
            val tasksInOrder = orderedIds.mapNotNull { existingById[it] }
            if (tasksInOrder.isEmpty()) return@withTransaction

            val draggedSet = orderedIds.toHashSet()
            val maxExistingSort = database.taskDao().getAllNonDeletedTasksList()
                .asSequence()
                .filter { it.areaId == targetArea.id && it.projectId == null && it.id !in draggedSet }
                .maxOfOrNull { it.sortOrder.takeIf(Double::isFinite) ?: 0.0 }
            val nextSorts = computeSequentialSortOrders(
                count = tasksInOrder.size,
                beforeSortOrder = null,
                afterSortOrder = null,
                fallbackMaxSortOrder = maxExistingSort
            )

            tasksInOrder.forEachIndexed { idx, rawTask ->
                val stamp = maxOf(now, rawTask.updatedAt + 1L)
                val base = restoreTaskFromTrashInTx(rawTask, stamp)
                val wasClosed = base.status != TaskStatus.OPEN
                val resolvedSchedule = if (
                    base.scheduleState == ScheduleState.INBOX &&
                    base.whenDate == null &&
                    base.recurrenceRuleJson == null
                ) {
                    ScheduleState.ANYTIME
                } else {
                    base.scheduleState
                }
                val updated = base.copy(
                    status = TaskStatus.OPEN,
                    completedAt = null,
                    projectId = null,
                    headingId = null,
                    areaId = targetArea.id,
                    scheduleState = resolvedSchedule,
                    sortOrder = nextSorts[idx],
                    isDeleted = false,
                    updatedAt = stamp,
                    isDirty = true
                )
                database.taskDao().upsert(updated)
                if (wasClosed) {
                    softDeleteSpawnedRecurrenceIfAny(updated.id, updated, stamp)
                }
                if (updated.reminderTime != null && updated.reminderTime > now) {
                    remindersToSchedule += updated
                }
                mutated = true
            }
        }

        remindersToSchedule.forEach { t ->
            t.reminderTime?.let { rt -> reminderScheduler.scheduleExactReminder(t.id, t.title, rt) }
        }
        if (mutated) {
            syncRepository.triggerBackgroundSync()
        }
    }

    suspend fun dropTasksOntoProject(
        taskIds: List<String>,
        projectId: String,
        headingId: String? = null,
        beforeSortOrder: Double? = null,
        afterSortOrder: Double? = null
    ) {
        val orderedIds = taskIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (orderedIds.isEmpty() || projectId.isBlank()) return
        val now = System.currentTimeMillis()
        val remindersToSchedule = mutableListOf<TaskEntity>()
        var mutated = false

        database.withTransaction {
            val targetProject = database.projectDao().getById(projectId)?.takeIf { !it.isDeleted } ?: return@withTransaction
            val resolvedHeadingId = headingId?.takeIf { it.isNotBlank() }?.let { hId ->
                database.headingDao().getById(hId)?.takeIf { !it.isDeleted && it.projectId == projectId }?.id
            }
            val existingById = orderedIds.chunked(500).flatMap { chunk ->
                database.taskDao().getByIds(chunk)
            }.associateBy { it.id }
            val tasksInOrder = orderedIds.mapNotNull { existingById[it] }
            if (tasksInOrder.isEmpty()) return@withTransaction

            val draggedSet = orderedIds.toHashSet()
            val fallbackMax = if (beforeSortOrder == null && afterSortOrder == null) {
                database.taskDao().getOpenTasksByProject(projectId)
                    .asSequence()
                    .filter { it.headingId == resolvedHeadingId && it.id !in draggedSet }
                    .maxOfOrNull { it.sortOrder.takeIf(Double::isFinite) ?: 0.0 }
            } else {
                null
            }
            val sequentialOrders = computeSequentialSortOrders(
                count = tasksInOrder.size,
                beforeSortOrder = beforeSortOrder,
                afterSortOrder = afterSortOrder,
                fallbackMaxSortOrder = fallbackMax
            )

            tasksInOrder.forEachIndexed { idx, rawTask ->
                val stamp = maxOf(now, rawTask.updatedAt + 1L)
                val base = restoreTaskFromTrashInTx(rawTask, stamp)
                val wasClosed = base.status != TaskStatus.OPEN
                val resolvedSchedule = if (
                    base.scheduleState == ScheduleState.INBOX &&
                    base.whenDate == null &&
                    base.recurrenceRuleJson == null
                ) {
                    ScheduleState.ANYTIME
                } else {
                    base.scheduleState
                }
                val updated = base.copy(
                    status = TaskStatus.OPEN,
                    completedAt = null,
                    projectId = projectId,
                    headingId = resolvedHeadingId,
                    areaId = targetProject.areaId,
                    scheduleState = resolvedSchedule,
                    sortOrder = sequentialOrders[idx],
                    isDeleted = false,
                    updatedAt = stamp,
                    isDirty = true
                )
                database.taskDao().upsert(updated)
                if (wasClosed) {
                    softDeleteSpawnedRecurrenceIfAny(updated.id, updated, stamp)
                }
                if (updated.reminderTime != null && updated.reminderTime > now) {
                    remindersToSchedule += updated
                }
                mutated = true
            }
        }

        remindersToSchedule.forEach { t ->
            t.reminderTime?.let { rt -> reminderScheduler.scheduleExactReminder(t.id, t.title, rt) }
        }
        if (mutated) {
            syncRepository.triggerBackgroundSync()
        }
    }

    suspend fun reorderTasksInList(
        taskIds: List<String>,
        beforeSortOrder: Double?,
        afterSortOrder: Double?
    ) {
        val orderedIds = taskIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (orderedIds.isEmpty()) return
        val now = System.currentTimeMillis()
        var mutated = false

        database.withTransaction {
            val existingById = orderedIds.chunked(500).flatMap { chunk ->
                database.taskDao().getByIds(chunk)
            }.associateBy { it.id }
            val tasksInOrder = orderedIds.mapNotNull { existingById[it]?.takeIf { e -> !e.isDeleted } }
            if (tasksInOrder.isEmpty()) return@withTransaction

            val sequentialOrders = computeSequentialSortOrders(
                count = tasksInOrder.size,
                beforeSortOrder = beforeSortOrder,
                afterSortOrder = afterSortOrder
            )
            val updatedEntities = tasksInOrder.mapIndexed { idx, existing ->
                existing.copy(
                    sortOrder = sequentialOrders[idx],
                    updatedAt = maxOf(now, existing.updatedAt + 1L),
                    isDirty = true
                )
            }
            if (updatedEntities.isNotEmpty()) {
                database.taskDao().upsertAll(updatedEntities)
                mutated = true
            }
        }

        if (mutated) {
            syncRepository.triggerBackgroundSync()
        }
    }

    suspend fun ensureDefaultWorkAndHomeAreas() {
        val activeAreas = database.areaDao().getActiveAreasList()
        val existingWork = database.areaDao().getById("area-work-001")
        val existingHome = database.areaDao().getById("area-home-002")
        if (activeAreas.isEmpty() && existingWork == null && existingHome == null) {
            val now = System.currentTimeMillis()
            database.areaDao().upsertAll(
                listOf(
                    AreaEntity(
                        id = "area-work-001",
                        title = "Work",
                        sortOrder = 1000.0,
                        tagIdsJson = "[]",
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        isDirty = false
                    ),
                    AreaEntity(
                        id = "area-home-002",
                        title = "Home",
                        sortOrder = 2000.0,
                        tagIdsJson = "[]",
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        isDirty = false
                    )
                )
            )
        }

        val misclassified = database.taskDao().getMisclassifiedAnytimeTasks()
        if (misclassified.isNotEmpty()) {
            val zone = ZoneId.systemDefault()
            val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfToday = endOfTodayEpochMillis()
            val fixed = misclassified.map { task ->
                val resolvedWhen = task.whenDate ?: todayEpoch
                val resolvedState = if (resolvedWhen > endOfToday) ScheduleState.UPCOMING else ScheduleState.TODAY
                task.copy(
                    whenDate = resolvedWhen,
                    scheduleState = resolvedState
                )
            }
            database.taskDao().upsertAll(fixed)
        }
    }

    suspend fun createArea(title: String): Result<String> {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    tr(
                        en = "Area name cannot be empty.",
                        de = "Bereichsname darf nicht leer sein.",
                        fr = "Le nom du domaine ne peut pas être vide.",
                        it = "Il nome dell'area non può essere vuoto.",
                        es = "El nombre del área no puede estar vacío."
                    )
                )
            )
        }
        val activeAreas = database.areaDao().getActiveAreasList()
        activeAreas.firstOrNull { it.title.trim().equals(trimmed, ignoreCase = true) }?.let { existingArea ->
            return Result.success(existingArea.id)
        }
        if (activeAreas.size >= MAX_AREAS) {
            return Result.failure(
                IllegalStateException(
                    tr(
                        en = "Maximum of $MAX_AREAS areas allowed.",
                        de = "Maximal $MAX_AREAS Bereiche möglich.",
                        fr = "Maximum de $MAX_AREAS domaines autorisés.",
                        it = "Massimo $MAX_AREAS aree consentite.",
                        es = "Máximo de $MAX_AREAS áreas permitidas."
                    )
                )
            )
        }
        val now = System.currentTimeMillis()
        val maxSort = activeAreas.maxOfOrNull { it.sortOrder } ?: 0.0
        val id = UUID.randomUUID().toString()
        database.areaDao().upsert(
            AreaEntity(
                id = id,
                title = trimmed,
                sortOrder = maxSort + 1000.0,
                tagIdsJson = "[]",
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
        return Result.success(id)
    }

    suspend fun renameArea(areaId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        val existing = database.areaDao().getById(areaId) ?: return
        val now = System.currentTimeMillis()
        database.areaDao().upsert(
            existing.copy(
                title = trimmed,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun moveArea(areaId: String, moveUp: Boolean) {
        val areas = database.areaDao().getActiveAreasList().sortedBy { it.sortOrder }
        val index = areas.indexOfFirst { it.id == areaId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in areas.indices) return

        val now = System.currentTimeMillis()
        val current = areas[index]
        val neighbor = areas[targetIndex]
        val currentSort = current.sortOrder
        val neighborSort = if (neighbor.sortOrder == currentSort) {
            if (moveUp) currentSort - 500.0 else currentSort + 500.0
        } else {
            neighbor.sortOrder
        }

        database.areaDao().upsertAll(
            listOf(
                current.copy(sortOrder = neighborSort, updatedAt = now, isDirty = true),
                neighbor.copy(sortOrder = currentSort, updatedAt = now, isDirty = true)
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateTaskTimeblock(
        taskId: String,
        scheduledStartMinutes: Int?,
        durationMinutes: Int?
    ) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        val cleanStart = scheduledStartMinutes?.takeIf { it in 0..1439 }
        val resolvedDuration = when {
            cleanStart == null && durationMinutes == null -> existing.durationMinutes
            cleanStart == null -> durationMinutes?.coerceIn(1, MAX_TASK_DURATION_MINUTES)
            else -> durationMinutes?.coerceIn(1, MAX_TASK_DURATION_MINUTES)
                ?: existing.durationMinutes?.coerceIn(1, MAX_TASK_DURATION_MINUTES)
                ?: TimeblockScheduleUtils.DEFAULT_TIMEBLOCK_DURATION_MINUTES
        }
        val zone = ZoneId.systemDefault()
        val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val updatedSchedule = if (
            cleanStart != null &&
            (existing.scheduleState == ScheduleState.INBOX || existing.scheduleState == ScheduleState.ANYTIME || existing.scheduleState == ScheduleState.SOMEDAY)
        ) {
            ScheduleState.TODAY
        } else {
            existing.scheduleState
        }
        val updatedWhen = if (cleanStart != null && existing.whenDate == null && updatedSchedule == ScheduleState.TODAY) {
            todayEpoch
        } else {
            existing.whenDate
        }
        database.taskDao().upsert(
            existing.copy(
                scheduleState = updatedSchedule,
                whenDate = updatedWhen,
                scheduledStartMinutes = cleanStart,
                durationMinutes = resolvedDuration,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun softDeleteArea(areaId: String) {
        val existing = database.areaDao().getById(areaId) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.projectDao().clearAreaFromProjects(areaId, now)
            database.taskDao().clearAreaFromTasks(areaId, now)
            database.projectTemplateDao().clearAreaFromTemplates(areaId, now)
            database.areaDao().upsert(
                existing.copy(
                    isDeleted = true,
                    updatedAt = now,
                    isDirty = true
                )
            )
        }
        syncRepository.triggerBackgroundSync()
    }

    suspend fun createOrUpdateProject(
        id: String = UUID.randomUUID().toString(),
        title: String,
        notes: String = "",
        areaId: String? = null,
        status: ProjectStatus = ProjectStatus.ACTIVE,
        scheduleState: ScheduleState = ScheduleState.ANYTIME,
        whenDate: Long? = null,
        deadline: Long? = null,
        tagIds: List<String> = emptyList(),
        workspaceId: String? = null,
        viewMode: String? = null
    ): String {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return id
        val now = System.currentTimeMillis()
        val existing = database.projectDao().getById(id)
        val maxSort = database.projectDao().getActiveProjectsList().maxOfOrNull { it.sortOrder } ?: 0.0
        val completedAt = when (status) {
            ProjectStatus.COMPLETED, ProjectStatus.CANCELED -> existing?.completedAt ?: now
            ProjectStatus.ACTIVE -> null
        }

        val entity = ProjectEntity(
            id = id,
            areaId = areaId,
            title = trimmedTitle,
            notes = notes,
            status = status,
            scheduleState = scheduleState,
            whenDate = whenDate,
            deadline = deadline,
            completedAt = completedAt,
            lastReviewedAt = if (existing != null) existing.lastReviewedAt else now,
            sortOrder = existing?.sortOrder ?: (maxSort + 1000.0),
            tagIdsJson = json.encodeToString(tagIds),
            workspaceId = workspaceId ?: existing?.workspaceId,
            viewMode = viewMode ?: existing?.viewMode,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            isDeleted = false,
            isDirty = true
        )
        database.projectDao().upsertProjectWithTags(entity, tagIds)
        syncRepository.triggerBackgroundSync()
        return id
    }

    suspend fun markProjectReviewed(projectId: String, reviewedAtMillis: Long = System.currentTimeMillis()) {
        val existing = database.projectDao().getById(projectId) ?: return
        val nextReviewedAt = maxOf(existing.lastReviewedAt ?: 0L, reviewedAtMillis)
        val nextUpdatedAt = maxOf(existing.updatedAt + 1L, nextReviewedAt)
        database.projectDao().upsert(
            existing.copy(
                lastReviewedAt = nextReviewedAt,
                updatedAt = nextUpdatedAt,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun markAllActiveProjectsReviewed(reviewedAtMillis: Long = System.currentTimeMillis()) {
        val activeProjects = database.projectDao().getActiveProjectsList()
        if (activeProjects.isEmpty()) return
        database.projectDao().upsertAll(
            activeProjects.map { existing ->
                val nextReviewedAt = maxOf(existing.lastReviewedAt ?: 0L, reviewedAtMillis)
                val nextUpdatedAt = maxOf(existing.updatedAt + 1L, nextReviewedAt)
                existing.copy(
                    lastReviewedAt = nextReviewedAt,
                    updatedAt = nextUpdatedAt,
                    isDirty = true
                )
            }
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun moveProjectToSomeday(projectId: String) {
        val existing = database.projectDao().getById(projectId) ?: return
        val now = System.currentTimeMillis()
        val nextReviewedAt = maxOf(existing.lastReviewedAt ?: 0L, now)
        val nextUpdatedAt = maxOf(existing.updatedAt + 1L, nextReviewedAt)
        database.projectDao().upsert(
            existing.copy(
                scheduleState = ScheduleState.SOMEDAY,
                whenDate = null,
                lastReviewedAt = nextReviewedAt,
                updatedAt = nextUpdatedAt,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateProjectDeadline(projectId: String, deadline: Long?) {
        val existing = database.projectDao().getById(projectId) ?: return
        val now = System.currentTimeMillis()
        database.projectDao().upsert(
            existing.copy(
                deadline = deadline,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateProjectArea(projectId: String, areaId: String?) {
        val existing = database.projectDao().getById(projectId) ?: return
        val now = System.currentTimeMillis()
        database.projectDao().upsert(
            existing.copy(
                areaId = areaId,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateProjectStatus(projectId: String, status: ProjectStatus) {
        val existing = database.projectDao().getById(projectId) ?: return
        val now = System.currentTimeMillis()
        database.projectDao().upsert(
            existing.copy(
                status = status,
                completedAt = if (status == ProjectStatus.ACTIVE) null else now,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateProjectViewMode(projectId: String, viewMode: String?) {
        val existing = database.projectDao().getById(projectId) ?: return
        val normalized = viewMode?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        if (existing.viewMode == normalized) return
        val now = System.currentTimeMillis()
        database.projectDao().upsert(
            existing.copy(
                viewMode = normalized,
                updatedAt = maxOf(now, existing.updatedAt + 1L),
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun moveProject(projectId: String, moveUp: Boolean) {
        val projects = database.projectDao().getActiveProjectsList().sortedBy { it.sortOrder }
        val index = projects.indexOfFirst { it.id == projectId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in projects.indices) return

        val now = System.currentTimeMillis()
        val current = projects[index]
        val neighbor = projects[targetIndex]
        val currentSort = current.sortOrder
        val neighborSort = if (neighbor.sortOrder == currentSort) {
            if (moveUp) currentSort - 500.0 else currentSort + 500.0
        } else {
            neighbor.sortOrder
        }

        database.projectDao().upsertAll(
            listOf(
                current.copy(sortOrder = neighborSort, updatedAt = now, isDirty = true),
                neighbor.copy(sortOrder = currentSort, updatedAt = now, isDirty = true)
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun countNonDeletedTasksByProject(projectId: String): Int =
        database.taskDao().countNonDeletedTasksByProject(projectId)

    suspend fun softDeleteProject(projectId: String, deleteAssociatedTasks: Boolean = true) {
        val existing = database.projectDao().getById(projectId) ?: return
        val now = System.currentTimeMillis()
        val projAttachments = database.attachmentDao().getAllByProjectId(projectId)
        projAttachments.forEach { syncRepository.deleteLocalAttachmentFile(it.localRelativePath) }

        if (deleteAssociatedTasks) {
            detachedTasksByDeletedProjectId.remove(projectId)
            var sideEffectTasks: List<TaskEntity> = emptyList()
            var sideEffectTaskAttachments: List<AttachmentEntity> = emptyList()

            database.withTransaction {
                val projectTasks = database.taskDao().getNonDeletedTasksByProject(projectId)
                sideEffectTasks = projectTasks
                deletedTaskIdsByDeletedProjectId[projectId] = projectTasks.map { it.id }

                if (projectTasks.isNotEmpty()) {
                    val attachmentsToClean = mutableListOf<AttachmentEntity>()
                    for (task in projectTasks) {
                        cascadeDeletedChecklistsByTaskId[task.id] =
                            database.checklistDao().getActiveChecklistIdsByTaskId(task.id)
                        database.checklistDao().softDeleteByTaskId(task.id, now)
                        attachmentsToClean += database.attachmentDao().getAllByTaskId(task.id)
                        database.attachmentDao().softDeleteByTaskId(task.id, now)
                    }
                    sideEffectTaskAttachments = attachmentsToClean
                    database.taskDao().upsertAll(
                        projectTasks.map { task ->
                            task.copy(
                                isDeleted = true,
                                updatedAt = now,
                                isDirty = true
                            )
                        }
                    )
                }

                database.headingDao().softDeleteHeadingsForProject(projectId, now)
                database.attachmentDao().softDeleteByProjectId(projectId, now)
                database.projectDao().upsert(
                    existing.copy(
                        isDeleted = true,
                        updatedAt = now,
                        isDirty = true
                    )
                )
            }

            sideEffectTasks.forEach { reminderScheduler.cancelReminder(it.id) }
            sideEffectTaskAttachments.forEach { syncRepository.deleteLocalAttachmentFile(it.localRelativePath) }
        } else {
            deletedTaskIdsByDeletedProjectId.remove(projectId)
            database.withTransaction {
                val nonDeletedTasks = database.taskDao().getNonDeletedTasksByProject(projectId)
                detachedTasksByDeletedProjectId[projectId] = nonDeletedTasks.map { task ->
                    DetachedProjectTaskSnapshot(
                        taskId = task.id,
                        projectId = projectId,
                        headingId = task.headingId
                    )
                }
                database.taskDao().clearProjectFromNonDeletedTasks(projectId, now)
                database.headingDao().softDeleteHeadingsForProject(projectId, now)
                database.attachmentDao().softDeleteByProjectId(projectId, now)
                database.projectDao().upsert(
                    existing.copy(
                        isDeleted = true,
                        updatedAt = now,
                        isDirty = true
                    )
                )
            }
        }
        syncRepository.triggerBackgroundSync()
    }

    suspend fun createHeadingInProject(
        projectId: String,
        title: String,
        sortOrder: Double,
        startDate: Long? = null,
        endDate: Long? = null
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        database.headingDao().upsert(
            HeadingEntity(
                id = id,
                projectId = projectId,
                title = title,
                startDate = startDate,
                endDate = endDate,
                sortOrder = if (sortOrder.isFinite()) sortOrder else 1000.0,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
        return id
    }

    suspend fun renameHeading(headingId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        val existing = database.headingDao().getById(headingId) ?: return
        val now = System.currentTimeMillis()
        database.headingDao().upsert(
            existing.copy(
                title = trimmed,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateHeadingStartDate(headingId: String, startDate: Long?) {
        val existing = database.headingDao().getById(headingId) ?: return
        val adjustedEnd = if (startDate != null && existing.endDate != null && existing.endDate < startDate) {
            startDate
        } else {
            existing.endDate
        }
        val now = System.currentTimeMillis()
        database.headingDao().upsert(
            existing.copy(
                startDate = startDate,
                endDate = adjustedEnd,
                updatedAt = maxOf(now, existing.updatedAt + 1L),
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateHeadingEndDate(headingId: String, endDate: Long?) {
        val existing = database.headingDao().getById(headingId) ?: return
        val adjustedStart = if (endDate != null && existing.startDate != null && endDate < existing.startDate) {
            endDate
        } else {
            existing.startDate
        }
        val now = System.currentTimeMillis()
        database.headingDao().upsert(
            existing.copy(
                startDate = adjustedStart,
                endDate = endDate,
                updatedAt = maxOf(now, existing.updatedAt + 1L),
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateHeadingDates(headingId: String, startDate: Long?, endDate: Long?) {
        val existing = database.headingDao().getById(headingId) ?: return
        val clampedEnd = if (startDate != null && endDate != null && endDate < startDate) {
            startDate
        } else {
            endDate
        }
        if (existing.startDate == startDate && existing.endDate == clampedEnd) return
        val now = System.currentTimeMillis()
        database.headingDao().upsert(
            existing.copy(
                startDate = startDate,
                endDate = clampedEnd,
                updatedAt = maxOf(now, existing.updatedAt + 1L),
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun softDeleteHeading(headingId: String) {
        val existing = database.headingDao().getById(headingId) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.taskDao().clearHeadingFromTasks(headingId, now)
            database.headingDao().upsert(
                existing.copy(
                    isDeleted = true,
                    updatedAt = now,
                    isDirty = true
                )
            )
        }
        syncRepository.triggerBackgroundSync()
    }

    suspend fun addChecklistItem(
        taskId: String,
        title: String,
        sortOrder: Double? = null,
        itemId: String = UUID.randomUUID().toString()
    ): String {
        val now = System.currentTimeMillis()
        val resolvedSortOrder = if (sortOrder != null && sortOrder.isFinite()) {
            sortOrder
        } else {
            val existingMax = database.checklistDao().getForTaskSync(taskId).maxOfOrNull { it.sortOrder }
            if (existingMax != null && existingMax.isFinite()) existingMax + 1000.0 else 1000.0
        }
        database.checklistDao().upsert(
            ChecklistItemEntity(
                id = itemId,
                taskId = taskId,
                title = title,
                isCompleted = false,
                sortOrder = resolvedSortOrder,
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
        return itemId
    }

    suspend fun insertChecklistItemAfter(
        taskId: String,
        afterItemId: String?,
        newItemId: String = UUID.randomUUID().toString(),
        title: String = ""
    ): String {
        val now = System.currentTimeMillis()
        val items = database.checklistDao().getForTaskSync(taskId).sortedBy { it.sortOrder }
        if (items.isEmpty()) {
            return addChecklistItem(taskId = taskId, title = title, sortOrder = 1000.0, itemId = newItemId)
        }

        val targetIndex = if (afterItemId != null) {
            items.indexOfFirst { it.id == afterItemId }
        } else {
            -1
        }

        if (targetIndex == -1 || targetIndex == items.lastIndex) {
            val nextOrder = items.last().sortOrder + 1000.0
            return addChecklistItem(taskId = taskId, title = title, sortOrder = nextOrder, itemId = newItemId)
        }

        val currentOrder = items[targetIndex].sortOrder
        val followingOrder = items[targetIndex + 1].sortOrder
        if (followingOrder - currentOrder > 0.0001) {
            val midOrder = (currentOrder + followingOrder) / 2.0
            return addChecklistItem(taskId = taskId, title = title, sortOrder = midOrder, itemId = newItemId)
        }

        val updated = mutableListOf<ChecklistItemEntity>()
        var orderCursor = 1000.0
        items.forEachIndexed { idx, existing ->
            updated.add(existing.copy(sortOrder = orderCursor, updatedAt = now, isDirty = true))
            orderCursor += 1000.0
            if (idx == targetIndex) {
                updated.add(
                    ChecklistItemEntity(
                        id = newItemId,
                        taskId = taskId,
                        title = title,
                        isCompleted = false,
                        sortOrder = orderCursor,
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        isDirty = true
                    )
                )
                orderCursor += 1000.0
            }
        }
        database.checklistDao().upsertAll(updated)
        syncRepository.triggerBackgroundSync()
        return newItemId
    }

    suspend fun addChecklistItemsBatch(taskId: String, titles: List<String>) {
        if (titles.isEmpty()) return
        val now = System.currentTimeMillis()
        val entities = titles.mapIndexed { index, stepTitle ->
            ChecklistItemEntity(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                title = stepTitle,
                isCompleted = false,
                sortOrder = (index + 1) * 100.0 + (now % 1000),
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                isDirty = true
            )
        }
        database.checklistDao().upsertAll(entities)
        syncRepository.triggerBackgroundSync()
    }

    suspend fun toggleChecklistItem(itemId: String, isCompleted: Boolean) {
        val existing = database.checklistDao().getById(itemId) ?: return
        val now = System.currentTimeMillis()
        database.checklistDao().upsert(
            existing.copy(
                isCompleted = isCompleted,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun updateChecklistItemTitle(itemId: String, newTitle: String) {
        val existing = database.checklistDao().getById(itemId) ?: return
        val now = System.currentTimeMillis()
        database.checklistDao().upsert(
            existing.copy(
                title = newTitle,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun moveChecklistItem(taskId: String, itemId: String, moveUp: Boolean) {
        val items = database.checklistDao().getForTaskSync(taskId).sortedBy { it.sortOrder }
        val index = items.indexOfFirst { it.id == itemId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in items.indices) return

        val now = System.currentTimeMillis()
        val current = items[index]
        val neighbor = items[targetIndex]
        val currentSort = current.sortOrder
        val neighborSort = if (neighbor.sortOrder == currentSort) {
            if (moveUp) currentSort - 50.0 else currentSort + 50.0
        } else {
            neighbor.sortOrder
        }

        database.checklistDao().upsertAll(
            listOf(
                current.copy(sortOrder = neighborSort, updatedAt = now, isDirty = true),
                neighbor.copy(sortOrder = currentSort, updatedAt = now, isDirty = true)
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun softDeleteChecklistItem(itemId: String) {
        val existing = database.checklistDao().getById(itemId) ?: return
        val now = System.currentTimeMillis()
        database.checklistDao().upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
    }

    suspend fun softDeleteTask(taskId: String) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        reminderScheduler.cancelReminder(taskId)
        val taskAttachments = database.attachmentDao().getAllByTaskId(taskId)
        taskAttachments.forEach { syncRepository.deleteLocalAttachmentFile(it.localRelativePath) }

        database.withTransaction {
            cascadeDeletedChecklistsByTaskId[taskId] = database.checklistDao().getActiveChecklistIdsByTaskId(taskId)
            database.checklistDao().softDeleteByTaskId(taskId, now)
            database.attachmentDao().softDeleteByTaskId(taskId, now)
            database.taskDao().upsert(
                existing.copy(
                    isDeleted = true,
                    updatedAt = now,
                    isDirty = true
                )
            )
        }
        syncRepository.triggerBackgroundSync()
    }

    private fun removeTaskFromDeletedProjectTracking(taskId: String) {
        for ((projId, taskIds) in deletedTaskIdsByDeletedProjectId.entries) {
            if (taskId in taskIds) {
                deletedTaskIdsByDeletedProjectId[projId] = taskIds.filterNot { it == taskId }
            }
        }
    }

    suspend fun restoreDeletedTask(taskId: String) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        removeTaskFromDeletedProjectTracking(taskId)
        val activeProject = existing.projectId?.let { pId ->
            database.projectDao().getById(pId)?.takeIf { !it.isDeleted }
        }
        val activeArea = existing.areaId?.let { aId ->
            database.areaDao().getById(aId)?.takeIf { !it.isDeleted }
        }
        val activeHeading = existing.headingId?.let { hId ->
            if (activeProject != null) {
                database.headingDao().getById(hId)?.takeIf { !it.isDeleted && it.projectId == activeProject.id }
            } else null
        }
        val restored = existing.copy(
            projectId = activeProject?.id,
            headingId = activeHeading?.id,
            areaId = activeArea?.id,
            isDeleted = false,
            updatedAt = now,
            isDirty = true
        )
        database.withTransaction {
            database.taskDao().upsert(restored)
            val checklistIdsToRestore = cascadeDeletedChecklistsByTaskId.remove(taskId)
            if (checklistIdsToRestore != null) {
                if (checklistIdsToRestore.isNotEmpty()) {
                    database.checklistDao().restoreChecklistItemsByIds(checklistIdsToRestore, now)
                }
            } else {
                database.checklistDao().restoreNonPurgedByTaskId(taskId, now)
            }
        }
        if (restored.status == TaskStatus.OPEN && restored.reminderTime != null && restored.reminderTime > now) {
            reminderScheduler.scheduleExactReminder(taskId, restored.title, restored.reminderTime)
        }
        syncRepository.triggerBackgroundSync()
    }

    suspend fun restoreDeletedProject(projectId: String) {
        val existing = database.projectDao().getById(projectId) ?: return
        val now = System.currentTimeMillis()
        val activeArea = existing.areaId?.let { aId ->
            database.areaDao().getById(aId)?.takeIf { !it.isDeleted }
        }
        val cascadeDeletedTaskIds = deletedTaskIdsByDeletedProjectId.remove(projectId)
        val snapshots = detachedTasksByDeletedProjectId.remove(projectId)
        val restoredCascadeTasks = mutableListOf<TaskEntity>()

        database.withTransaction {
            database.headingDao().restoreHeadingsForProject(projectId, now)
            database.projectDao().upsert(
                existing.copy(
                    areaId = activeArea?.id,
                    isDeleted = false,
                    updatedAt = now,
                    isDirty = true
                )
            )

            val nonDeletedHeadings = database.headingDao()
                .getNonDeletedHeadingsForProjectList(projectId)
                .mapTo(HashSet()) { it.id }

            val tasksToRestore: List<TaskEntity> = when {
                !cascadeDeletedTaskIds.isNullOrEmpty() -> {
                    cascadeDeletedTaskIds.mapNotNull { tId ->
                        database.taskDao().getById(tId)?.takeIf { it.isDeleted && it.projectId == projectId }
                    }
                }
                cascadeDeletedTaskIds == null && snapshots == null -> {
                    database.taskDao().getNonPurgedDeletedTasksByProject(projectId)
                }
                else -> emptyList()
            }

            for (deletedTask in tasksToRestore) {
                val validTaskArea = deletedTask.areaId?.let { aId ->
                    database.areaDao().getById(aId)?.takeIf { !it.isDeleted }?.id
                } ?: activeArea?.id
                val restoredHeadingId = deletedTask.headingId?.takeIf { it in nonDeletedHeadings }
                val restoredTask = deletedTask.copy(
                    projectId = projectId,
                    headingId = restoredHeadingId,
                    areaId = validTaskArea,
                    isDeleted = false,
                    updatedAt = now,
                    isDirty = true
                )
                database.taskDao().upsert(restoredTask)
                val checklistIdsToRestore = cascadeDeletedChecklistsByTaskId.remove(deletedTask.id)
                if (checklistIdsToRestore != null) {
                    if (checklistIdsToRestore.isNotEmpty()) {
                        database.checklistDao().restoreChecklistItemsByIds(checklistIdsToRestore, now)
                    }
                } else {
                    database.checklistDao().restoreNonPurgedByTaskId(deletedTask.id, now)
                }
                restoredCascadeTasks += restoredTask
            }

            if (!snapshots.isNullOrEmpty()) {
                for (snap in snapshots) {
                    val currentTask = database.taskDao().getById(snap.taskId) ?: continue
                    if (!currentTask.isDeleted && currentTask.projectId == null) {
                        val restoredHeadingId = snap.headingId?.takeIf { it in nonDeletedHeadings }
                        val restoredSchedule = if (
                            currentTask.status == TaskStatus.OPEN &&
                            currentTask.scheduleState == ScheduleState.INBOX &&
                            currentTask.whenDate == null &&
                            currentTask.recurrenceRuleJson == null
                        ) {
                            ScheduleState.ANYTIME
                        } else {
                            currentTask.scheduleState
                        }
                        database.taskDao().upsert(
                            currentTask.copy(
                                projectId = projectId,
                                headingId = restoredHeadingId,
                                areaId = activeArea?.id ?: currentTask.areaId,
                                scheduleState = restoredSchedule,
                                updatedAt = now,
                                isDirty = true
                            )
                        )
                    }
                }
            }
        }

        for (restoredTask in restoredCascadeTasks) {
            if (restoredTask.status == TaskStatus.OPEN &&
                restoredTask.reminderTime != null &&
                restoredTask.reminderTime > now
            ) {
                reminderScheduler.scheduleExactReminder(
                    restoredTask.id,
                    restoredTask.title,
                    restoredTask.reminderTime
                )
            }
        }
        syncRepository.triggerBackgroundSync()
    }

    fun resolveAttachmentFile(localRelativePath: String): java.io.File =
        syncRepository.resolveAttachmentFile(localRelativePath)

    suspend fun addAttachmentFromBytes(
        taskId: String? = null,
        projectId: String? = null,
        fileName: String,
        mimeType: String,
        rawBytes: ByteArray
    ): TaskAttachment? {
        if (rawBytes.isEmpty()) return null
        if (taskId.isNullOrBlank() && projectId.isNullOrBlank()) return null
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val localRelativePath = syncRepository.buildSafeLocalAttachmentPath(id, fileName)
        val file = runCatching { syncRepository.resolveAttachmentFile(localRelativePath) }.getOrNull() ?: return null
        runCatching {
            file.writeBytes(rawBytes)
        }.onFailure {
            return null
        }

        val entity = AttachmentEntity(
            id = id,
            taskId = taskId?.takeIf { it.isNotBlank() },
            projectId = projectId?.takeIf { it.isNotBlank() },
            fileName = fileName.trim().ifEmpty { file.name },
            mimeType = mimeType.trim().ifEmpty { "application/octet-stream" },
            sizeBytes = rawBytes.size.toLong(),
            localRelativePath = localRelativePath,
            createdAt = now,
            updatedAt = now,
            isDeleted = false,
            isDirty = true
        )
        database.attachmentDao().upsert(entity)
        syncRepository.triggerBackgroundSync()
        return entity.toDomain()
    }

    suspend fun softDeleteAttachment(attachmentId: String) {
        val existing = database.attachmentDao().getById(attachmentId) ?: return
        val now = System.currentTimeMillis()
        syncRepository.deleteLocalAttachmentFile(existing.localRelativePath)
        database.attachmentDao().softDeleteById(attachmentId, now)
        syncRepository.triggerBackgroundSync()
    }

    private suspend fun purgeOrCompactTaskInternal(
        task: TaskEntity,
        keepSyncTombstones: Boolean,
        now: Long
    ) {
        reminderScheduler.cancelReminder(task.id)
        cascadeDeletedChecklistsByTaskId.remove(task.id)
        removeTaskFromDeletedProjectTracking(task.id)
        val taskAttachments = database.attachmentDao().getAllByTaskId(task.id)
        taskAttachments.forEach { syncRepository.deleteLocalAttachmentFile(it.localRelativePath) }

        if (keepSyncTombstones) {
            taskAttachments.forEach { database.attachmentDao().compactTombstoneById(it.id, now) }
            database.checklistDao().compactTombstonesByTaskId(task.id, now)
            database.taskDao().hardDeleteTaskTagsByTaskIds(listOf(task.id))
            database.taskDao().upsert(
                task.copy(
                    title = "",
                    notes = "",
                    projectId = null,
                    headingId = null,
                    areaId = null,
                    tagIdsJson = "[]",
                    reminderTime = null,
                    scheduledStartMinutes = null,
                    durationMinutes = null,
                    isPinned = false,
                    isMilestone = false,
                    effortLevel = null,
                    blockedByJson = "[]",
                    workspaceId = null,
                    assigneeId = null,
                    isDeleted = true,
                    updatedAt = now,
                    isDirty = true
                )
            )
        } else {
            database.attachmentDao().hardDeleteByTaskIds(listOf(task.id))
            database.checklistDao().hardDeleteByTaskIds(listOf(task.id))
            database.taskDao().hardDeleteTaskTagsByTaskIds(listOf(task.id))
            database.taskDao().hardDeleteByIds(listOf(task.id))
        }
    }

    private suspend fun purgeOrCompactProjectInternal(
        project: ProjectEntity,
        keepSyncTombstones: Boolean,
        now: Long
    ) {
        detachedTasksByDeletedProjectId.remove(project.id)
        deletedTaskIdsByDeletedProjectId.remove(project.id)
        val deletedProjectTasks = database.taskDao().getDeletedTasksByProject(project.id)
        for (task in deletedProjectTasks) {
            purgeOrCompactTaskInternal(task, keepSyncTombstones, now)
        }

        val projAttachments = database.attachmentDao().getAllByProjectId(project.id)
        projAttachments.forEach { syncRepository.deleteLocalAttachmentFile(it.localRelativePath) }

        database.taskDao().clearProjectFromNonDeletedTasks(project.id, now)
        if (keepSyncTombstones) {
            projAttachments.forEach { database.attachmentDao().compactTombstoneById(it.id, now) }
            database.headingDao().compactTombstonesByProjectId(project.id, now)
            database.projectDao().hardDeleteProjectTagsByIds(listOf(project.id))
            database.projectDao().upsert(
                project.copy(
                    title = "",
                    notes = "",
                    tagIdsJson = "[]",
                    workspaceId = null,
                    viewMode = null,
                    isDeleted = true,
                    updatedAt = now,
                    isDirty = true
                )
            )
        } else {
            database.taskDao().clearProjectFromTasks(project.id, now)
            database.attachmentDao().hardDeleteByProjectIds(listOf(project.id))
            database.headingDao().hardDeleteHeadingsForProjects(listOf(project.id))
            database.projectDao().hardDeleteProjectTagsByIds(listOf(project.id))
            database.projectDao().hardDeleteByIds(listOf(project.id))
        }
    }

    suspend fun permanentlyDeleteTask(taskId: String) {
        val existing = database.taskDao().getById(taskId)
        if (existing == null) {
            reminderScheduler.cancelReminder(taskId)
            return
        }
        val now = System.currentTimeMillis()
        val keepSyncTombstones = !syncRepository.isLocalOnlyDbModeEnabled() && syncRepository.hasActiveCloudAccount()
        database.withTransaction {
            purgeOrCompactTaskInternal(existing, keepSyncTombstones, now)
        }
        if (keepSyncTombstones) {
            syncRepository.triggerBackgroundSync()
        }
    }

    suspend fun permanentlyDeleteProject(projectId: String) {
        val existing = database.projectDao().getById(projectId) ?: return
        val now = System.currentTimeMillis()
        val keepSyncTombstones = !syncRepository.isLocalOnlyDbModeEnabled() && syncRepository.hasActiveCloudAccount()
        database.withTransaction {
            purgeOrCompactProjectInternal(existing, keepSyncTombstones, now)
        }
        if (keepSyncTombstones) {
            syncRepository.triggerBackgroundSync()
        }
    }

    suspend fun emptyTrash() {
        val now = System.currentTimeMillis()
        val keepSyncTombstones = !syncRepository.isLocalOnlyDbModeEnabled() && syncRepository.hasActiveCloudAccount()
        var changed = false
        database.withTransaction {
            val deletedTasks = database.taskDao().getDeletedTasksList()
            if (deletedTasks.isNotEmpty()) {
                changed = true
                for (task in deletedTasks) {
                    purgeOrCompactTaskInternal(task, keepSyncTombstones, now)
                }
            }
            val deletedProjects = database.projectDao().getDeletedProjectsList()
            if (deletedProjects.isNotEmpty()) {
                changed = true
                for (project in deletedProjects) {
                    purgeOrCompactProjectInternal(project, keepSyncTombstones, now)
                }
            }
        }
        if (changed && keepSyncTombstones) {
            syncRepository.triggerBackgroundSync()
        }
    }

    suspend fun duplicateTask(taskId: String, copySuffix: String = "Copy"): String? {
        val existing = database.taskDao().getById(taskId) ?: return null
        if (existing.isDeleted) return null
        val now = System.currentTimeMillis()
        val newTaskId = UUID.randomUUID().toString()
        val tagIds = decodeStringList(existing.tagIdsJson)
        val trimmedTitle = existing.title.trim()
        val newTitle = if (trimmedTitle.isNotEmpty()) "$trimmedTitle ($copySuffix)" else existing.title
        val clonedTask = existing.copy(
            id = newTaskId,
            title = newTitle,
            status = TaskStatus.OPEN,
            completedAt = null,
            reminderTime = existing.reminderTime?.takeIf { it > now },
            isPinned = false,
            isMilestone = existing.isMilestone,
            blockedByJson = "[]",
            sortOrder = existing.sortOrder + 10.0,
            createdAt = now,
            updatedAt = now,
            isDeleted = false,
            isDirty = true
        )

        database.withTransaction {
            database.taskDao().upsertTaskWithTags(clonedTask, tagIds)
            val subtasks = database.checklistDao().getForTaskSync(taskId)
            if (subtasks.isNotEmpty()) {
                val clonedSubtasks = subtasks.map { sub ->
                    sub.copy(
                        id = UUID.randomUUID().toString(),
                        taskId = newTaskId,
                        isCompleted = false,
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        isDirty = true
                    )
                }
                database.checklistDao().upsertAll(clonedSubtasks)
            }
        }

        if (clonedTask.reminderTime != null && clonedTask.reminderTime > now) {
            reminderScheduler.scheduleExactReminder(newTaskId, clonedTask.title, clonedTask.reminderTime)
        }
        syncRepository.triggerBackgroundSync()
        return newTaskId
    }

    suspend fun duplicateProject(projectId: String, copySuffix: String = "Copy"): String? {
        val existingProject = database.projectDao().getById(projectId) ?: return null
        if (existingProject.isDeleted) return null
        val now = System.currentTimeMillis()
        val newProjectId = UUID.randomUUID().toString()
        val projectTagIds = decodeStringList(existingProject.tagIdsJson)
        val trimmedTitle = existingProject.title.trim()
        val newTitle = if (trimmedTitle.isNotEmpty()) "$trimmedTitle ($copySuffix)" else existingProject.title

        database.withTransaction {
            val clonedProject = existingProject.copy(
                id = newProjectId,
                title = newTitle,
                status = ProjectStatus.ACTIVE,
                completedAt = null,
                sortOrder = existingProject.sortOrder + 100.0,
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                isDirty = true
            )
            database.projectDao().upsertProjectWithTags(clonedProject, projectTagIds)

            val existingHeadings = database.headingDao().getHeadingsForProjectList(projectId)
            val headingIdMap = mutableMapOf<String, String>()
            if (existingHeadings.isNotEmpty()) {
                val clonedHeadings = existingHeadings.map { h ->
                    val newHeadingId = UUID.randomUUID().toString()
                    headingIdMap[h.id] = newHeadingId
                    h.copy(
                        id = newHeadingId,
                        projectId = newProjectId,
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        isDirty = true
                    )
                }
                database.headingDao().upsertAll(clonedHeadings)
            }

            val existingTasks = database.taskDao().getAllNonDeletedTasksList()
                .filter { it.projectId == projectId && it.status == TaskStatus.OPEN }
                .sortedBy { it.sortOrder }
            val taskIdMap = existingTasks.associate { it.id to UUID.randomUUID().toString() }

            for (task in existingTasks) {
                val newTaskId = taskIdMap.getValue(task.id)
                val taskTagIds = decodeStringList(task.tagIdsJson)
                val mappedHeadingId = task.headingId?.let { headingIdMap[it] }
                val remappedBlockers = decodeStringList(task.blockedByJson)
                    .mapNotNull { taskIdMap[it] }
                    .filter { it != newTaskId }
                    .distinct()
                val clonedTask = task.copy(
                    id = newTaskId,
                    projectId = newProjectId,
                    headingId = mappedHeadingId,
                    status = TaskStatus.OPEN,
                    completedAt = null,
                    reminderTime = null,
                    isPinned = false,
                    blockedByJson = json.encodeToString(remappedBlockers),
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false,
                    isDirty = true
                )
                database.taskDao().upsertTaskWithTags(clonedTask, taskTagIds)

                val subtasks = database.checklistDao().getForTaskSync(task.id)
                if (subtasks.isNotEmpty()) {
                    val clonedSubtasks = subtasks.map { sub ->
                        sub.copy(
                            id = UUID.randomUUID().toString(),
                            taskId = newTaskId,
                            isCompleted = false,
                            createdAt = now,
                            updatedAt = now,
                            isDeleted = false,
                            isDirty = true
                        )
                    }
                    database.checklistDao().upsertAll(clonedSubtasks)
                }
            }
        }

        syncRepository.triggerBackgroundSync()
        return newProjectId
    }

    suspend fun createOrUpdateTag(
        id: String = UUID.randomUUID().toString(),
        title: String,
        shortcut: String? = null,
        parentTagId: String? = null,
        allowClearShortcut: Boolean = false
    ): String {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return id
        val now = System.currentTimeMillis()
        val activeTags = database.tagDao().getAllNonDeletedTagsList()
        val existingById = database.tagDao().getById(id)?.takeIf { !it.isDeleted }
        val collidingActive = activeTags.firstOrNull {
            it.id != id && it.title.trim().equals(trimmedTitle, ignoreCase = true)
        }

        if (existingById != null && collidingActive != null) {
            val cleanShortcut = shortcut?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanShortcut != null && collidingActive.shortcut.isNullOrBlank()) {
                database.tagDao().upsert(
                    collidingActive.copy(
                        shortcut = cleanShortcut,
                        updatedAt = maxOf(now, collidingActive.updatedAt + 1L),
                        isDirty = true
                    )
                )
            }
            mergeTags(sourceTagId = existingById.id, targetTagId = collidingActive.id)
            return collidingActive.id
        }

        val existing = existingById ?: collidingActive
        val resolvedId = existing?.id ?: id
        val cleanShortcut = shortcut?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedShortcut = when {
            allowClearShortcut -> cleanShortcut
            cleanShortcut != null -> cleanShortcut
            else -> existing?.shortcut
        }
        val nextSortOrder = (activeTags.maxOfOrNull { it.sortOrder.takeIf(Double::isFinite) ?: 0.0 } ?: 0.0) + 1000.0
        val tagEntity = TagEntity(
            id = resolvedId,
            title = if (existingById == null && existing != null) existing.title else trimmedTitle,
            parentTagId = parentTagId ?: existing?.parentTagId,
            shortcut = resolvedShortcut,
            sortOrder = existing?.sortOrder?.takeIf(Double::isFinite) ?: nextSortOrder,
            createdAt = existing?.createdAt ?: now,
            updatedAt = maxOf(now, (existing?.updatedAt ?: 0L) + 1L),
            isDeleted = false,
            isDirty = true
        )
        database.tagDao().upsert(tagEntity)
        syncRepository.triggerBackgroundSync()
        return resolvedId
    }

    suspend fun mergeTags(sourceTagId: String, targetTagId: String): Boolean {
        val srcId = sourceTagId.trim()
        val dstId = targetTagId.trim()
        if (srcId.isEmpty() || dstId.isEmpty() || srcId == dstId) return false
        val source = database.tagDao().getById(srcId)?.takeIf { !it.isDeleted } ?: return false
        val target = database.tagDao().getById(dstId)?.takeIf { !it.isDeleted } ?: return false
        val now = System.currentTimeMillis()

        fun remapIds(ids: Collection<String>): Pair<List<String>, Boolean> {
            var changed = false
            val out = mutableListOf<String>()
            for (raw in ids) {
                val mapped = if (raw == srcId) dstId else raw
                if (mapped != raw) changed = true
                if (mapped !in out) {
                    out += mapped
                } else {
                    changed = true
                }
            }
            return out to changed
        }

        database.withTransaction {
            val taskCrossRefsByTask = database.taskDao().getAllTaskTagCrossRefs()
                .groupBy({ it.taskId }, { it.tagId })
            val projCrossRefsByProject = database.projectDao().getAllProjectTagCrossRefs()
                .groupBy({ it.projectId }, { it.tagId })

            val candidateTasks = (database.taskDao().getAllNonDeletedTasksList() +
                database.taskDao().getDeletedTasksList()).distinctBy { it.id }
            for (task in candidateTasks) {
                val jsonTags = decodeStringList(task.tagIdsJson)
                val crossRefTags = taskCrossRefsByTask[task.id].orEmpty()
                if (srcId in jsonTags || srcId in crossRefTags) {
                    val combined = LinkedHashSet<String>(jsonTags).apply { addAll(crossRefTags) }
                    val (nextTags, _) = remapIds(combined)
                    database.taskDao().upsertTaskWithTags(
                        task.copy(
                            tagIdsJson = json.encodeToString(nextTags),
                            updatedAt = maxOf(now, task.updatedAt + 1L),
                            isDirty = true
                        ),
                        nextTags
                    )
                }
            }

            val candidateProjects = (database.projectDao().getAllNonDeletedProjectsList() +
                database.projectDao().getDeletedProjectsList()).distinctBy { it.id }
            for (proj in candidateProjects) {
                val jsonTags = decodeStringList(proj.tagIdsJson)
                val crossRefTags = projCrossRefsByProject[proj.id].orEmpty()
                if (srcId in jsonTags || srcId in crossRefTags) {
                    val combined = LinkedHashSet<String>(jsonTags).apply { addAll(crossRefTags) }
                    val (nextTags, _) = remapIds(combined)
                    database.projectDao().upsertProjectWithTags(
                        proj.copy(
                            tagIdsJson = json.encodeToString(nextTags),
                            updatedAt = maxOf(now, proj.updatedAt + 1L),
                            isDirty = true
                        ),
                        nextTags
                    )
                }
            }

            val updatedAreas = database.areaDao().getActiveAreasList().mapNotNull { area ->
                val (nextTags, changed) = remapIds(decodeStringList(area.tagIdsJson))
                if (changed) {
                    area.copy(
                        tagIdsJson = json.encodeToString(nextTags),
                        updatedAt = maxOf(now, area.updatedAt + 1L),
                        isDirty = true
                    )
                } else null
            }
            if (updatedAreas.isNotEmpty()) {
                database.areaDao().upsertAll(updatedAreas)
            }

            val updatedTemplates = database.projectTemplateDao().getAllNonDeletedTemplatesList().mapNotNull { tpl ->
                val (nextTplTags, tplTagsChanged) = remapIds(decodeStringList(tpl.tagIdsJson))
                val blueprintMayContainSrc = tpl.blueprintJson.contains(srcId)
                var nextBlueprintJson = tpl.blueprintJson
                var bpChanged = false
                if (blueprintMayContainSrc) {
                    val bp = ProjectTemplateCodec.decodeBlueprint(tpl.blueprintJson, tpl.title, tpl.notes)
                    val (bpTags, bpRootChanged) = remapIds(bp.tagIds)
                    val nextUnheaded = bp.unheadedTasks.map { tb ->
                        val (tTags, tc) = remapIds(tb.tagIds)
                        if (tc) {
                            bpChanged = true
                            tb.copy(tagIds = tTags)
                        } else tb
                    }
                    val nextHeadings = bp.headings.map { hb ->
                        hb.copy(
                            tasks = hb.tasks.map { tb ->
                                val (tTags, tc) = remapIds(tb.tagIds)
                                if (tc) {
                                    bpChanged = true
                                    tb.copy(tagIds = tTags)
                                } else tb
                            }
                        )
                    }
                    if (bpRootChanged || bpChanged) {
                        bpChanged = true
                        nextBlueprintJson = ProjectTemplateCodec.encodeBlueprint(
                            bp.copy(
                                tagIds = bpTags,
                                unheadedTasks = nextUnheaded,
                                headings = nextHeadings
                            )
                        )
                    }
                }
                if (tplTagsChanged || bpChanged) {
                    tpl.copy(
                        tagIdsJson = json.encodeToString(nextTplTags),
                        blueprintJson = nextBlueprintJson,
                        updatedAt = maxOf(now, tpl.updatedAt + 1L),
                        isDirty = true
                    )
                } else null
            }
            if (updatedTemplates.isNotEmpty()) {
                database.projectTemplateDao().upsertAll(updatedTemplates)
            }

            val childTags = database.tagDao().getAllNonDeletedTagsList().mapNotNull { child ->
                if (child.id != srcId && child.parentTagId == srcId) {
                    child.copy(
                        parentTagId = if (child.id != target.id) target.id else null,
                        updatedAt = maxOf(now, child.updatedAt + 1L),
                        isDirty = true
                    )
                } else null
            }
            if (childTags.isNotEmpty()) {
                database.tagDao().upsertAll(childTags)
            }

            database.tagDao().deleteTaskTagCrossRefsForTag(srcId)
            database.tagDao().deleteProjectTagCrossRefsForTag(srcId)

            if (target.shortcut.isNullOrBlank() && !source.shortcut.isNullOrBlank()) {
                database.tagDao().upsert(
                    target.copy(
                        shortcut = source.shortcut,
                        updatedAt = maxOf(now, target.updatedAt + 1L),
                        isDirty = true
                    )
                )
            }

            database.tagDao().upsert(
                source.copy(
                    shortcut = null,
                    isDeleted = true,
                    updatedAt = maxOf(now, source.updatedAt + 1L),
                    isDirty = true
                )
            )
        }
        syncRepository.triggerBackgroundSync()
        return true
    }

    suspend fun moveTag(tagId: String, moveUp: Boolean) {
        val tags = database.tagDao().getAllNonDeletedTagsList().sortedWith(
            compareBy<TagEntity> { it.sortOrder }
                .thenBy { it.createdAt }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        val index = tags.indexOfFirst { it.id == tagId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in tags.indices) return

        val now = System.currentTimeMillis()
        val strictlyOrdered = tags.zipWithNext().all { (a, b) -> b.sortOrder > a.sortOrder }
        if (strictlyOrdered) {
            val current = tags[index]
            val neighbor = tags[targetIndex]
            database.tagDao().upsertAll(
                listOf(
                    current.copy(
                        sortOrder = neighbor.sortOrder,
                        updatedAt = maxOf(now, current.updatedAt + 1L),
                        isDirty = true
                    ),
                    neighbor.copy(
                        sortOrder = current.sortOrder,
                        updatedAt = maxOf(now, neighbor.updatedAt + 1L),
                        isDirty = true
                    )
                )
            )
        } else {
            val reordered = tags.toMutableList()
            val moved = reordered.removeAt(index)
            reordered.add(targetIndex, moved)
            val updates = reordered.mapIndexedNotNull { idx, tag ->
                val desiredOrder = (idx + 1) * 1000.0
                if (tag.sortOrder != desiredOrder) {
                    tag.copy(
                        sortOrder = desiredOrder,
                        updatedAt = maxOf(now, tag.updatedAt + 1L),
                        isDirty = true
                    )
                } else null
            }
            if (updates.isNotEmpty()) {
                database.tagDao().upsertAll(updates)
            }
        }
        syncRepository.triggerBackgroundSync()
    }

    suspend fun batchUpdateTasksTags(
        taskIds: Collection<String>,
        tagIdsToAdd: Set<String>,
        tagIdsToRemove: Set<String>
    ) {
        val distinctTaskIds = taskIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinctTaskIds.isEmpty() || (tagIdsToAdd.isEmpty() && tagIdsToRemove.isEmpty())) return
        val now = System.currentTimeMillis()
        var mutated = false
        database.withTransaction {
            val validTagIds = database.tagDao().getAllNonDeletedTagsList().mapTo(HashSet()) { it.id }
            val cleanAddIds = tagIdsToAdd.filter { it in validTagIds }
            val tasks = distinctTaskIds.chunked(500).flatMap { chunk ->
                database.taskDao().getByIds(chunk)
            }
            for (task in tasks) {
                if (task.isDeleted) continue
                val currentTags = decodeStringList(task.tagIdsJson)
                val nextTags = (currentTags.filterNot { it in tagIdsToRemove } + cleanAddIds)
                    .filter { it in validTagIds }
                    .distinct()
                if (nextTags != currentTags) {
                    database.taskDao().upsertTaskWithTags(
                        task.copy(
                            tagIdsJson = json.encodeToString(nextTags),
                            updatedAt = maxOf(now, task.updatedAt + 1L),
                            isDirty = true
                        ),
                        nextTags
                    )
                    mutated = true
                }
            }
        }
        if (mutated) {
            syncRepository.triggerBackgroundSync()
        }
    }

    suspend fun softDeleteTag(tagId: String) {
        val existing = database.tagDao().getById(tagId) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.tagDao().deleteTaskTagCrossRefsForTag(tagId)
            database.tagDao().deleteProjectTagCrossRefsForTag(tagId)

            val affectedTasks = database.taskDao().getAllNonDeletedTasksList().mapNotNull { t ->
                val list = decodeStringList(t.tagIdsJson)
                if (tagId in list) {
                    t.copy(tagIdsJson = json.encodeToString(list - tagId), updatedAt = now, isDirty = true)
                } else null
            }
            if (affectedTasks.isNotEmpty()) database.taskDao().upsertAll(affectedTasks)

            val affectedProjects = database.projectDao().getAllNonDeletedProjectsList().mapNotNull { p ->
                val list = decodeStringList(p.tagIdsJson)
                if (tagId in list) {
                    p.copy(tagIdsJson = json.encodeToString(list - tagId), updatedAt = now, isDirty = true)
                } else null
            }
            if (affectedProjects.isNotEmpty()) database.projectDao().upsertAll(affectedProjects)

            val affectedAreas = database.areaDao().getActiveAreasList().mapNotNull { a ->
                val list = decodeStringList(a.tagIdsJson)
                if (tagId in list) {
                    a.copy(tagIdsJson = json.encodeToString(list - tagId), updatedAt = now, isDirty = true)
                } else null
            }
            if (affectedAreas.isNotEmpty()) database.areaDao().upsertAll(affectedAreas)

            database.tagDao().upsert(
                existing.copy(
                    isDeleted = true,
                    updatedAt = now,
                    isDirty = true
                )
            )
        }
        syncRepository.triggerBackgroundSync()
    }

    suspend fun getOpenActiveTasksForToday(): List<String> {
        return database.taskDao()
            .getOpenActiveTasksForToday(endOfTodayEpochMillis())
            .map { it.title }
    }

    suspend fun createProjectFromBlueprint(
        title: String,
        notes: String,
        areaId: String? = null,
        headingsWithTasks: List<Pair<String, List<String>>>
    ): String {
        val defaultProjTitle = tr(
            en = "New Project",
            de = "Neues Projekt",
            fr = "Nouveau projet",
            it = "Nuovo progetto",
            es = "Nuevo proyecto"
        )
        val sectionPrefix = tr(
            en = "Section",
            de = "Abschnitt",
            fr = "Section",
            it = "Sezione",
            es = "Sección"
        )
        val trimmedTitle = title.trim().ifEmpty { defaultProjTitle }
        val now = System.currentTimeMillis()
        val projectId = UUID.randomUUID().toString()
        val todayEpoch = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        database.withTransaction {
            val projectEntity = ProjectEntity(
                id = projectId,
                areaId = areaId,
                title = trimmedTitle,
                notes = notes,
                status = ProjectStatus.ACTIVE,
                scheduleState = ScheduleState.TODAY,
                whenDate = todayEpoch,
                deadline = null,
                completedAt = null,
                sortOrder = now.toDouble(),
                tagIdsJson = "[]",
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                isDirty = true
            )
            database.projectDao().upsert(projectEntity)

            headingsWithTasks.forEachIndexed { hIndex, (headingTitle, taskTitles) ->
                val headingId = UUID.randomUUID().toString()
                database.headingDao().upsert(
                    HeadingEntity(
                        id = headingId,
                        projectId = projectId,
                        title = headingTitle.trim().ifEmpty { "$sectionPrefix ${hIndex + 1}" },
                        sortOrder = (hIndex + 1) * 1000.0,
                        isArchived = false,
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        isDirty = true
                    )
                )
                val tasks = taskTitles.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
                    .mapIndexed { tIndex, taskTitle ->
                        TaskEntity(
                            id = UUID.randomUUID().toString(),
                            title = taskTitle,
                            notes = "",
                            status = TaskStatus.OPEN,
                            scheduleState = if (hIndex == 0 && tIndex == 0) ScheduleState.TODAY else ScheduleState.ANYTIME,
                            whenDate = if (hIndex == 0 && tIndex == 0) todayEpoch else null,
                            deadline = null,
                            reminderTime = null,
                            completedAt = null,
                            projectId = projectId,
                            headingId = headingId,
                            areaId = areaId,
                            recurrenceRuleJson = null,
                            sortOrder = (hIndex + 1) * 1000.0 + (tIndex + 1) * 100.0,
                            tagIdsJson = "[]",
                            createdAt = now,
                            updatedAt = now,
                            isDeleted = false,
                            isDirty = true
                        )
                    }
                if (tasks.isNotEmpty()) {
                    database.taskDao().upsertAll(tasks)
                }
            }
        }

        syncRepository.triggerBackgroundSync()
        return projectId
    }

    // =========================================================================
    // v2.0 Project Templates & Markdown / JSON Outline Importer
    // =========================================================================

    suspend fun saveProjectAsTemplate(projectId: String, customTitle: String? = null): String? {
        val project = database.projectDao().getById(projectId) ?: return null
        if (project.isDeleted) return null
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val anchorDate = project.whenDate?.let {
            runCatching { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.getOrNull()
        } ?: LocalDate.now(zone)

        val headings = database.headingDao().getHeadingsForProjectList(projectId).sortedBy { it.sortOrder }
        val openTasks = database.taskDao().getAllNonDeletedTasksList()
            .filter { it.projectId == projectId && it.status == TaskStatus.OPEN }
            .sortedBy { it.sortOrder }

        suspend fun TaskEntity.toTaskBlueprint(): TemplateTaskBlueprint {
            val checklists = database.checklistDao().getForTaskSync(id)
                .filter { !it.isDeleted }
                .sortedBy { it.sortOrder }
                .map { it.title }
            val dayOffset = whenDate?.let { epoch ->
                runCatching {
                    val taskDate = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
                    java.time.temporal.ChronoUnit.DAYS.between(anchorDate, taskDate).toInt().coerceIn(-365, 3650)
                }.getOrNull()
            }
            val deadlineOffset = deadline?.let { epoch ->
                runCatching {
                    val dlDate = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
                    java.time.temporal.ChronoUnit.DAYS.between(anchorDate, dlDate).toInt().coerceIn(0, 3650)
                }.getOrNull()
            }
            return TemplateTaskBlueprint(
                title = title,
                notes = notes,
                checklistItems = checklists,
                dayOffset = dayOffset,
                deadlineDayOffset = deadlineOffset,
                scheduledStartMinutes = scheduledStartMinutes,
                durationMinutes = durationMinutes,
                isEvening = scheduleState == ScheduleState.THIS_EVENING,
                isMilestone = isMilestone,
                tagIds = decodeStringList(tagIdsJson)
            )
        }

        val validHeadingIds = headings.mapTo(HashSet()) { it.id }
        val unheadedBlueprints = openTasks
            .filter { it.headingId == null || it.headingId !in validHeadingIds }
            .map { it.toTaskBlueprint() }

        val headingBlueprints = headings.map { heading ->
            val hTasks = openTasks.filter { it.headingId == heading.id }.map { it.toTaskBlueprint() }
            val hEndOffset = heading.endDate?.let { epoch ->
                runCatching {
                    val endDateLocal = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
                    java.time.temporal.ChronoUnit.DAYS.between(anchorDate, endDateLocal).toInt().coerceIn(0, 3650)
                }.getOrNull()
            }
            TemplateHeadingBlueprint(
                title = heading.title,
                endDateDayOffset = hEndOffset,
                tasks = hTasks
            )
        }

        val projDeadlineOffset = project.deadline?.let { epoch ->
            runCatching {
                val dlDate = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
                java.time.temporal.ChronoUnit.DAYS.between(anchorDate, dlDate).toInt().coerceIn(0, 3650)
            }.getOrNull()
        }

        val projTagIds = decodeStringList(project.tagIdsJson)
        val templateTitle = customTitle?.trim()?.takeIf { it.isNotEmpty() } ?: project.title
        val blueprint = ProjectTemplateBlueprint(
            title = templateTitle,
            notes = project.notes,
            deadlineDayOffset = projDeadlineOffset,
            unheadedTasks = unheadedBlueprints,
            headings = headingBlueprints,
            tagIds = projTagIds
        )

        val templateId = UUID.randomUUID().toString()
        val existingTemplates = database.projectTemplateDao().getAllNonDeletedTemplatesList()
        val nextSortOrder = (existingTemplates.maxOfOrNull { it.sortOrder } ?: 0.0) + 1000.0
        val templateEntity = ProjectTemplateEntity(
            id = templateId,
            title = templateTitle,
            notes = project.notes,
            defaultAreaId = project.areaId,
            tagIdsJson = json.encodeToString(projTagIds),
            blueprintJson = ProjectTemplateCodec.encodeBlueprint(blueprint),
            sortOrder = nextSortOrder,
            createdAt = now,
            updatedAt = now,
            isDeleted = false,
            isDirty = true
        )

        database.projectTemplateDao().upsert(templateEntity)
        syncRepository.triggerBackgroundSync()
        return templateId
    }

    suspend fun createProjectFromTemplateBlueprint(
        blueprint: ProjectTemplateBlueprint,
        areaIdOverride: String? = null,
        defaultAreaId: String? = null,
        templateTagIds: List<String> = emptyList(),
        anchorDate: LocalDate = LocalDate.now(ZoneId.systemDefault())
    ): String {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val todayDate = LocalDate.now(zone)
        val endOfToday = todayDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val anchorEpoch = anchorDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val projectId = UUID.randomUUID().toString()

        database.withTransaction {
            val validAreaIds = database.areaDao().getActiveAreasList().mapTo(HashSet()) { it.id }
            val validTagIds = database.tagDao().getAllNonDeletedTagsList().mapTo(HashSet()) { it.id }
            val resolvedAreaId = (areaIdOverride ?: defaultAreaId)?.takeIf { it in validAreaIds }
            val resolvedProjTags = (blueprint.tagIds + templateTagIds).filter { it in validTagIds }.distinct()
            val projDeadline = blueprint.deadlineDayOffset?.let { offset ->
                anchorDate.plusDays(offset.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            }
            val maxProjSort = database.projectDao().getActiveProjectsList().maxOfOrNull { it.sortOrder } ?: 0.0

            val projectEntity = ProjectEntity(
                id = projectId,
                areaId = resolvedAreaId,
                title = blueprint.title.trim().ifEmpty { "New Project" },
                notes = blueprint.notes,
                status = ProjectStatus.ACTIVE,
                scheduleState = if (anchorEpoch <= endOfToday) ScheduleState.TODAY else ScheduleState.UPCOMING,
                whenDate = anchorEpoch,
                deadline = projDeadline,
                completedAt = null,
                lastReviewedAt = now,
                sortOrder = maxProjSort + 1000.0,
                tagIdsJson = json.encodeToString(resolvedProjTags),
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                isDirty = true
            )
            database.projectDao().upsertProjectWithTags(projectEntity, resolvedProjTags)

            suspend fun insertBlueprintTask(
                taskBp: TemplateTaskBlueprint,
                headingId: String?,
                sortOrder: Double
            ) {
                val cleanTitle = taskBp.title.trim()
                if (cleanTitle.isEmpty()) return
                val taskId = UUID.randomUUID().toString()
                val taskWhen = taskBp.dayOffset?.let { offset ->
                    anchorDate.plusDays(offset.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
                }
                val taskDeadline = taskBp.deadlineDayOffset?.let { offset ->
                    anchorDate.plusDays(offset.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
                }
                val taskSchedule = when {
                    taskWhen != null && taskWhen > endOfToday -> ScheduleState.UPCOMING
                    taskBp.isEvening -> ScheduleState.THIS_EVENING
                    taskWhen != null || taskBp.scheduledStartMinutes != null -> ScheduleState.TODAY
                    else -> ScheduleState.ANYTIME
                }
                val resolvedWhen = if (taskSchedule == ScheduleState.TODAY && taskWhen == null) {
                    anchorEpoch
                } else {
                    taskWhen
                }
                val validTaskTags = taskBp.tagIds.filter { it in validTagIds }.distinct()
                val taskEntity = TaskEntity(
                    id = taskId,
                    title = cleanTitle,
                    notes = taskBp.notes,
                    status = TaskStatus.OPEN,
                    scheduleState = taskSchedule,
                    whenDate = resolvedWhen,
                    deadline = taskDeadline,
                    reminderTime = null,
                    scheduledStartMinutes = taskBp.scheduledStartMinutes?.takeIf { it in 0..1439 },
                    durationMinutes = taskBp.durationMinutes?.takeIf { it in 1..MAX_TASK_DURATION_MINUTES },
                    completedAt = null,
                    projectId = projectId,
                    headingId = headingId,
                    areaId = resolvedAreaId,
                    recurrenceRuleJson = null,
                    sortOrder = sortOrder,
                    isMilestone = taskBp.isMilestone,
                    tagIdsJson = json.encodeToString(validTaskTags),
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false,
                    isDirty = true
                )
                database.taskDao().upsertTaskWithTags(taskEntity, validTaskTags)

                val subtasks = taskBp.checklistItems
                    .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                    .mapIndexed { idx, subTitle ->
                        ChecklistItemEntity(
                            id = UUID.randomUUID().toString(),
                            taskId = taskId,
                            title = subTitle,
                            isCompleted = false,
                            sortOrder = (idx + 1) * 100.0,
                            createdAt = now,
                            updatedAt = now,
                            isDeleted = false,
                            isDirty = true
                        )
                    }
                if (subtasks.isNotEmpty()) {
                    database.checklistDao().upsertAll(subtasks)
                }
            }

            blueprint.unheadedTasks.forEachIndexed { idx, taskBp ->
                insertBlueprintTask(taskBp, headingId = null, sortOrder = (idx + 1) * 100.0)
            }

            blueprint.headings.forEachIndexed { hIdx, headingBp ->
                val headingId = UUID.randomUUID().toString()
                val hSort = (hIdx + 1) * 1000.0 + blueprint.unheadedTasks.size * 100.0
                val phaseEndDate = headingBp.endDateDayOffset?.let { offset ->
                    anchorDate.plusDays(offset.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
                }
                database.headingDao().upsert(
                    HeadingEntity(
                        id = headingId,
                        projectId = projectId,
                        title = headingBp.title.trim().ifEmpty { "Phase ${hIdx + 1}" },
                        endDate = phaseEndDate,
                        sortOrder = hSort,
                        isArchived = false,
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        isDirty = true
                    )
                )
                headingBp.tasks.forEachIndexed { tIdx, taskBp ->
                    insertBlueprintTask(taskBp, headingId = headingId, sortOrder = hSort + (tIdx + 1) * 100.0)
                }
            }
        }

        syncRepository.triggerBackgroundSync()
        return projectId
    }

    suspend fun deleteProjectTemplate(templateId: String) {
        val existing = database.projectTemplateDao().getById(templateId) ?: return
        val now = System.currentTimeMillis()
        val keepSyncTombstones = !syncRepository.isLocalOnlyDbModeEnabled() && syncRepository.hasActiveCloudAccount()
        if (keepSyncTombstones) {
            database.projectTemplateDao().softDeleteById(existing.id, now)
            syncRepository.triggerBackgroundSync()
        } else {
            database.projectTemplateDao().hardDeleteByIds(listOf(existing.id))
        }
    }

    suspend fun importProjectFromOutline(
        rawOutline: String,
        areaId: String? = null,
        fallbackTitle: String = "Imported Project"
    ): String {
        val blueprint = ProjectOutlineImporter.parseOutline(rawOutline, fallbackTitle = fallbackTitle)
        return createProjectFromTemplateBlueprint(
            blueprint = blueprint,
            areaIdOverride = areaId
        )
    }

    // =========================================================================
    // v3.0 Milestone 4: Asana & Microsoft Planner Project Import / Export
    // =========================================================================

    suspend fun importExternalProject(
        parsed: ExternalImportedProject,
        areaId: String? = null
    ): Pair<Project, Int> {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val todayDate = LocalDate.now(zone)
        val endOfToday = todayDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val projectId = UUID.randomUUID().toString()

        var createdProject: Project? = null
        var insertedCount = 0

        database.withTransaction {
            val validAreaIds = database.areaDao().getActiveAreasList().mapTo(HashSet()) { it.id }
            val resolvedAreaId = areaId?.takeIf { it in validAreaIds }
            val existingTags = database.tagDao().getAllNonDeletedTagsList().toMutableList()

            suspend fun resolveOrCreateTagIds(tagNames: List<String>): List<String> {
                if (tagNames.isEmpty()) return emptyList()
                val resolvedIds = mutableListOf<String>()
                for (rawName in tagNames) {
                    val cleanName = rawName.trim()
                    if (cleanName.isEmpty()) continue
                    val existing = existingTags.firstOrNull { it.title.equals(cleanName, ignoreCase = true) }
                    if (existing != null) {
                        resolvedIds.add(existing.id)
                    } else {
                        val newTagId = UUID.randomUUID().toString()
                        val maxTagSort = existingTags.maxOfOrNull {
                            it.sortOrder.takeIf(Double::isFinite) ?: 0.0
                        } ?: 0.0
                        val newTag = TagEntity(
                            id = newTagId,
                            title = cleanName,
                            parentTagId = null,
                            shortcut = null,
                            sortOrder = maxTagSort + 1000.0,
                            createdAt = now,
                            updatedAt = now,
                            isDeleted = false,
                            isDirty = true
                        )
                        database.tagDao().upsert(newTag)
                        existingTags.add(newTag)
                        resolvedIds.add(newTagId)
                    }
                }
                return resolvedIds.distinct()
            }

            val projectTagIds = resolveOrCreateTagIds(parsed.tagNames)
            val maxProjSort = database.projectDao().getActiveProjectsList()
                .maxOfOrNull { it.sortOrder.takeIf(Double::isFinite) ?: 0.0 } ?: 0.0
            val cleanProjectTitle = parsed.title.trim().ifEmpty { "Imported Project" }

            val projectEntity = ProjectEntity(
                id = projectId,
                areaId = resolvedAreaId,
                title = cleanProjectTitle,
                notes = parsed.notes,
                status = ProjectStatus.ACTIVE,
                scheduleState = ScheduleState.ANYTIME,
                whenDate = null,
                deadline = null,
                completedAt = null,
                lastReviewedAt = now,
                sortOrder = maxProjSort + 1000.0,
                tagIdsJson = json.encodeToString(projectTagIds),
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                isDirty = true
            )
            database.projectDao().upsertProjectWithTags(projectEntity, projectTagIds)

            var completedInsertedCount = 0

            suspend fun insertExternalTask(
                extTask: ExternalImportedTask,
                headingId: String?,
                sortOrder: Double
            ) {
                val cleanTitle = extTask.title.trim()
                if (cleanTitle.isEmpty()) return
                val taskId = UUID.randomUUID().toString()
                val taskWhen = extTask.whenDate
                val taskSchedule = when {
                    taskWhen != null && taskWhen > endOfToday -> ScheduleState.UPCOMING
                    taskWhen != null -> ScheduleState.TODAY
                    else -> ScheduleState.ANYTIME
                }
                val taskStatus = extTask.status
                val resolvedCompletedAt = when (taskStatus) {
                    TaskStatus.COMPLETED, TaskStatus.CANCELED -> extTask.completedAt ?: now
                    TaskStatus.OPEN -> null
                }
                val taskTagIds = resolveOrCreateTagIds(extTask.tagNames)

                val taskEntity = TaskEntity(
                    id = taskId,
                    title = cleanTitle,
                    notes = extTask.notes,
                    status = taskStatus,
                    scheduleState = taskSchedule,
                    whenDate = taskWhen,
                    deadline = extTask.deadline,
                    reminderTime = null,
                    completedAt = resolvedCompletedAt,
                    projectId = projectId,
                    headingId = headingId,
                    areaId = resolvedAreaId,
                    recurrenceRuleJson = null,
                    sortOrder = sortOrder,
                    isPinned = extTask.isPinned,
                    isMilestone = extTask.isMilestone,
                    effortLevel = EffortLevel.normalize(extTask.effortLevel),
                    assigneeId = extTask.assigneeId?.trim()?.takeIf { it.isNotEmpty() },
                    tagIdsJson = json.encodeToString(taskTagIds),
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false,
                    isDirty = true
                )
                database.taskDao().upsertTaskWithTags(taskEntity, taskTagIds)

                val checklistEntities = extTask.checklistItems
                    .mapNotNull { chk ->
                        chk.title.trim().takeIf(String::isNotEmpty)?.let { it to chk.isCompleted }
                    }
                    .mapIndexed { cIdx, (cTitle, cDone) ->
                        ChecklistItemEntity(
                            id = UUID.randomUUID().toString(),
                            taskId = taskId,
                            title = cTitle,
                            isCompleted = cDone,
                            sortOrder = (cIdx + 1) * 100.0,
                            createdAt = now,
                            updatedAt = now,
                            isDeleted = false,
                            isDirty = true
                        )
                    }
                if (checklistEntities.isNotEmpty()) {
                    database.checklistDao().upsertAll(checklistEntities)
                }

                insertedCount++
                if (taskStatus == TaskStatus.COMPLETED) {
                    completedInsertedCount++
                }
            }

            parsed.ungroupedTasks.forEachIndexed { idx, extTask ->
                insertExternalTask(extTask, headingId = null, sortOrder = (idx + 1) * 10.0)
            }

            parsed.phases.forEachIndexed { idx, phase ->
                val headingId = UUID.randomUUID().toString()
                val hSort = (idx + 1) * 1000.0
                val clampedEnd = if (phase.startDate != null && phase.endDate != null && phase.endDate < phase.startDate) {
                    phase.startDate
                } else {
                    phase.endDate
                }
                database.headingDao().upsert(
                    HeadingEntity(
                        id = headingId,
                        projectId = projectId,
                        title = phase.title.trim().ifEmpty { "Phase ${idx + 1}" },
                        startDate = phase.startDate,
                        endDate = clampedEnd,
                        sortOrder = hSort,
                        isArchived = false,
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        isDirty = true
                    )
                )
                phase.tasks.forEachIndexed { tIdx, extTask ->
                    insertExternalTask(
                        extTask = extTask,
                        headingId = headingId,
                        sortOrder = hSort + (tIdx + 1) * 1.0
                    )
                }
            }

            createdProject = Project(
                id = projectId,
                areaId = resolvedAreaId,
                title = cleanProjectTitle,
                notes = parsed.notes,
                status = ProjectStatus.ACTIVE,
                scheduleState = ScheduleState.ANYTIME,
                whenDate = null,
                deadline = null,
                completedAt = null,
                lastReviewedAt = now,
                sortOrder = projectEntity.sortOrder,
                tagIds = projectTagIds,
                totalTaskCount = insertedCount,
                completedTaskCount = completedInsertedCount,
                createdAt = now,
                updatedAt = now,
                isDeleted = false
            )
        }

        syncRepository.triggerBackgroundSync()
        return checkNotNull(createdProject) to insertedCount
    }

    suspend fun exportProjectExternal(
        projectId: String,
        format: ExternalExportFormat
    ): Triple<String, String, ByteArray> {
        val projectEntity = database.projectDao().getById(projectId)?.takeIf { !it.isDeleted }
            ?: throw IllegalArgumentException("Project not found: $projectId")
        val headingEntities = database.headingDao().getHeadingsForProjectList(projectId)
        val taskDetails = database.taskDao().getAllActiveTasksWithDetails()
            .filter { it.task.projectId == projectId }
        val projTagIds = decodeStringList(projectEntity.tagIdsJson)
        val allTagsById = database.tagDao().getAllNonDeletedTagsList().associateBy { it.id }
        val projectTags = projTagIds.mapNotNull { allTagsById[it]?.toDomain() }

        val domainHeadings = headingEntities.map { h ->
            Heading(
                id = h.id,
                projectId = h.projectId,
                title = h.title,
                startDate = h.startDate,
                endDate = h.endDate,
                sortOrder = h.sortOrder,
                isArchived = h.isArchived,
                createdAt = h.createdAt,
                updatedAt = h.updatedAt,
                isDeleted = h.isDeleted
            )
        }
        val domainTasks = taskDetails.map { it.toDomain() }
        val domainProject = Project(
            id = projectEntity.id,
            areaId = projectEntity.areaId,
            title = projectEntity.title,
            notes = projectEntity.notes,
            status = projectEntity.status,
            scheduleState = projectEntity.scheduleState,
            whenDate = projectEntity.whenDate,
            deadline = projectEntity.deadline,
            completedAt = projectEntity.completedAt,
            lastReviewedAt = projectEntity.lastReviewedAt,
            sortOrder = projectEntity.sortOrder,
            tagIds = projTagIds,
            workspaceId = projectEntity.workspaceId,
            viewMode = projectEntity.viewMode,
            totalTaskCount = domainTasks.count { it.status != TaskStatus.CANCELED },
            completedTaskCount = domainTasks.count { it.status == TaskStatus.COMPLETED },
            createdAt = projectEntity.createdAt,
            updatedAt = projectEntity.updatedAt,
            isDeleted = projectEntity.isDeleted
        )

        val bytes = ExternalProjectCodec.exportProject(
            project = domainProject,
            headings = domainHeadings,
            tasks = domainTasks,
            projectTags = projectTags,
            format = format
        )
        val suggestedFileName = ExternalProjectCodec.buildSuggestedExportFileName(domainProject.title, format)
        return Triple(suggestedFileName, format.mimeType, bytes)
    }

    // =========================================================================
    // CSV Export & Import (Tasks, Areas, Projects, Tags, Dates, Checklist)
    // =========================================================================

    suspend fun exportTasksToCsv(): String {
        val tasksWithDetails = database.taskDao().getAllActiveTasksWithDetails()
        val areasById = database.areaDao().getActiveAreasList().associateBy { it.id }
        val projectsById = database.projectDao().getAllNonDeletedProjectsList().associateBy { it.id }
        val zone = ZoneId.systemDefault()

        val sb = StringBuilder()
        sb.appendLine("Title,Notes,Status,ScheduleState,WhenDate,Deadline,Area,Project,Tags,Checklist")

        for (item in tasksWithDetails) {
            val t = item.task
            val proj = t.projectId?.let { projectsById[it] }
            val areaId = proj?.areaId ?: t.areaId
            val area = areaId?.let { areasById[it] }
            val whenStr = t.whenDate?.let { epoch ->
                runCatching { Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate().toString() }.getOrDefault("")
            } ?: ""
            val deadlineStr = t.deadline?.let { epoch ->
                runCatching { Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate().toString() }.getOrDefault("")
            } ?: ""
            val tagsStr = CsvParserUtils.encodeSemicolonList(
                item.tags.filter { !it.isDeleted }.map { it.title }
            )
            val checklistStr = CsvParserUtils.encodeSemicolonList(
                item.checklistItems
                    .filter { !it.isDeleted }
                    .sortedBy { it.sortOrder }
                    .map { sub -> (if (sub.isCompleted) "[x] " else "[ ] ") + sub.title }
            )

            val row = listOf(
                CsvParserUtils.escapeCsvField(t.title),
                CsvParserUtils.escapeCsvField(t.notes),
                CsvParserUtils.escapeCsvField(t.status.name),
                CsvParserUtils.escapeCsvField(t.scheduleState.name),
                CsvParserUtils.escapeCsvField(whenStr),
                CsvParserUtils.escapeCsvField(deadlineStr),
                CsvParserUtils.escapeCsvField(area?.title ?: ""),
                CsvParserUtils.escapeCsvField(proj?.title ?: ""),
                CsvParserUtils.escapeCsvField(tagsStr),
                CsvParserUtils.escapeCsvField(checklistStr)
            ).joinToString(",")
            sb.appendLine(row)
        }
        return sb.toString()
    }

    suspend fun importTasksFromCsv(csvContent: String): Int {
        val cleaned = csvContent.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) return 0

        val firstLine = cleaned.lineSequence().firstOrNull() ?: return 0
        val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' } && !firstLine.contains("Title,")) ';' else ','
        val rows = CsvParserUtils.parseCsvRows(cleaned, delimiter)
        if (rows.isEmpty()) return 0

        val header = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        val hasHeader = header.any { it in setOf("title", "titel", "aufgabe", "task", "name") }
        val dataRows = (if (hasHeader) rows.drop(1) else rows).take(10_000)
        if (dataRows.isEmpty()) return 0

        fun colIndex(vararg names: String, defaultIdx: Int): Int {
            if (!hasHeader) return defaultIdx
            return header.indexOfFirst { h -> names.any { h == it } }
        }

        val idxTitle = colIndex("title", "titel", "aufgabe", "task", "name", defaultIdx = 0).let { if (it >= 0) it else 0 }
        val idxNotes = colIndex("notes", "notizen", "beschreibung", "description", defaultIdx = 1)
        val idxStatus = colIndex("status", defaultIdx = 2)
        val idxSchedule = colIndex("schedulestate", "schedule", "when", "ansicht", "view", defaultIdx = 3)
        val idxWhenDate = colIndex("whendate", "startdatum", "date", "datum", defaultIdx = 4)
        val idxDeadline = colIndex("deadline", "fälligkeit", "due", "duedate", "zieldatum", defaultIdx = 5)
        val idxArea = colIndex("area", "bereich", defaultIdx = 6)
        val idxProject = colIndex("project", "projekt", defaultIdx = 7)
        val idxTags = colIndex("tags", "tag", "categories", "category", "kategorien", "kategorie", "catégories", "categorie", "categorías", defaultIdx = 8)
        val idxChecklist = colIndex("checklist", "subtasks", "unteraufgaben", defaultIdx = 9)

        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val endOfToday = endOfTodayEpochMillis()
        var importedCount = 0

        database.withTransaction {
            val existingAreas = database.areaDao().getActiveAreasList().toMutableList()
            val existingProjects = database.projectDao().getAllNonDeletedProjectsList().toMutableList()
            val existingTags = database.tagDao().observeAllTags().first().toMutableList()

            for ((rowIdx, cols) in dataRows.withIndex()) {
                fun colVal(idx: Int): String = if (idx >= 0) cols.getOrNull(idx)?.trim().orEmpty() else ""

                val rawTitle = colVal(idxTitle)
                if (rawTitle.isEmpty()) continue

                val rawNotes = colVal(idxNotes)
                val rawStatus = colVal(idxStatus).uppercase(Locale.ROOT).ifEmpty { "OPEN" }
                val rawSchedule = colVal(idxSchedule).uppercase(Locale.ROOT)
                val rawWhen = colVal(idxWhenDate)
                val rawDeadline = colVal(idxDeadline)
                val rawArea = colVal(idxArea)
                val rawProject = colVal(idxProject)
                val rawTags = colVal(idxTags)
                val rawChecklist = colVal(idxChecklist)

                val status = runCatching { TaskStatus.valueOf(rawStatus) }.getOrDefault(TaskStatus.OPEN)
                val parsedWhen = CsvParserUtils.parseFlexibleCsvDate(rawWhen, zone)
                val parsedDeadline = CsvParserUtils.parseFlexibleCsvDate(rawDeadline, zone)

                val scheduleState = if (rawSchedule.isNotEmpty()) {
                    runCatching { ScheduleState.valueOf(rawSchedule) }.getOrElse {
                        when {
                            parsedWhen != null && parsedWhen > endOfToday -> ScheduleState.UPCOMING
                            parsedWhen != null -> ScheduleState.TODAY
                            else -> ScheduleState.INBOX
                        }
                    }
                } else {
                    when {
                        parsedWhen != null && parsedWhen > endOfToday -> ScheduleState.UPCOMING
                        parsedWhen != null -> ScheduleState.TODAY
                        else -> ScheduleState.INBOX
                    }
                }

                // Match or create Area (up to MAX_AREAS = 8)
                var resolvedAreaId: String? = null
                if (rawArea.isNotEmpty()) {
                    val matchArea = existingAreas.firstOrNull { it.title.equals(rawArea, ignoreCase = true) }
                    if (matchArea != null) {
                        resolvedAreaId = matchArea.id
                    } else if (existingAreas.size < MAX_AREAS) {
                        val newAreaId = UUID.randomUUID().toString()
                        val newArea = AreaEntity(
                            id = newAreaId,
                            title = rawArea,
                            sortOrder = (existingAreas.size + 1) * 1000.0,
                            tagIdsJson = "[]",
                            createdAt = now,
                            updatedAt = now,
                            isDeleted = false,
                            isDirty = true
                        )
                        database.areaDao().upsert(newArea)
                        existingAreas.add(newArea)
                        resolvedAreaId = newAreaId
                    }
                }

                // Match or create Project
                var resolvedProjectId: String? = null
                if (rawProject.isNotEmpty()) {
                    val matchProj = existingProjects.firstOrNull { it.title.equals(rawProject, ignoreCase = true) }
                    if (matchProj != null) {
                        resolvedProjectId = matchProj.id
                        if (resolvedAreaId == null) resolvedAreaId = matchProj.areaId
                    } else {
                        val newProjId = UUID.randomUUID().toString()
                        val newProj = ProjectEntity(
                            id = newProjId,
                            areaId = resolvedAreaId,
                            title = rawProject,
                            notes = "",
                            status = ProjectStatus.ACTIVE,
                            scheduleState = ScheduleState.ANYTIME,
                            whenDate = null,
                            deadline = null,
                            completedAt = null,
                            sortOrder = (existingProjects.size + 1) * 1000.0,
                            tagIdsJson = "[]",
                            createdAt = now,
                            updatedAt = now,
                            isDeleted = false,
                            isDirty = true
                        )
                        database.projectDao().upsert(newProj)
                        existingProjects.add(newProj)
                        resolvedProjectId = newProjId
                    }
                }

                // Match or create Tags inside transaction without triggering per-tag network sync
                val tagIds = mutableListOf<String>()
                if (rawTags.isNotEmpty()) {
                    val tagNames = CsvParserUtils.decodeSemicolonList(rawTags, allowPipeSeparator = true).distinct()
                    for (tName in tagNames) {
                        val matchTag = existingTags.firstOrNull { it.title.equals(tName, ignoreCase = true) }
                        if (matchTag != null) {
                            tagIds.add(matchTag.id)
                        } else {
                            val newTagId = UUID.randomUUID().toString()
                            val createdTag = TagEntity(
                                id = newTagId,
                                title = tName,
                                parentTagId = null,
                                shortcut = null,
                                sortOrder = (now % 100_000).toDouble() + existingTags.size,
                                createdAt = now,
                                updatedAt = now,
                                isDeleted = false,
                                isDirty = true
                            )
                            database.tagDao().upsert(createdTag)
                            existingTags.add(createdTag)
                            tagIds.add(newTagId)
                        }
                    }
                }

                val taskId = UUID.randomUUID().toString()
                val resolvedTaskSchedule = if (
                    scheduleState == ScheduleState.INBOX &&
                    parsedWhen == null &&
                    (resolvedProjectId != null || resolvedAreaId != null)
                ) {
                    ScheduleState.ANYTIME
                } else {
                    scheduleState
                }

                val taskEntity = TaskEntity(
                    id = taskId,
                    title = rawTitle,
                    notes = rawNotes,
                    status = status,
                    scheduleState = resolvedTaskSchedule,
                    whenDate = parsedWhen,
                    deadline = parsedDeadline,
                    reminderTime = null,
                    completedAt = if (status == TaskStatus.COMPLETED || status == TaskStatus.CANCELED) now else null,
                    projectId = resolvedProjectId,
                    headingId = null,
                    areaId = resolvedAreaId,
                    recurrenceRuleJson = null,
                    sortOrder = (now % 100_000) + rowIdx * 50.0,
                    tagIdsJson = json.encodeToString(tagIds),
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false,
                    isDirty = true
                )
                database.taskDao().upsertTaskWithTags(taskEntity, tagIds)

                if (rawChecklist.isNotEmpty()) {
                    val subParts = CsvParserUtils.decodeSemicolonList(rawChecklist)
                    val subEntities = subParts.mapIndexed { sIdx, part ->
                        val completed = part.startsWith("[x]", ignoreCase = true)
                        val cleanSubTitle = part
                            .removePrefix("[x]")
                            .removePrefix("[X]")
                            .removePrefix("[ ]")
                            .removePrefix("[]")
                            .trim()
                        ChecklistItemEntity(
                            id = UUID.randomUUID().toString(),
                            taskId = taskId,
                            title = cleanSubTitle.ifEmpty { part },
                            isCompleted = completed,
                            sortOrder = (sIdx + 1) * 100.0,
                            createdAt = now,
                            updatedAt = now,
                            isDeleted = false,
                            isDirty = true
                        )
                    }
                    if (subEntities.isNotEmpty()) {
                        database.checklistDao().upsertAll(subEntities)
                    }
                }
                importedCount++
            }
        }

        if (importedCount > 0) {
            syncRepository.triggerBackgroundSync()
        }
        return importedCount
    }

    // =========================================================================
    // Initial App Setup: Sample Data Detection & Local Cleanup / Cloud Merge
    // =========================================================================

    fun observeHasSampleTasks(): Flow<Boolean> =
        database.taskDao()
            .observeCandidateSampleTasks(DatabaseSeeder.SAMPLE_TASK_IDS.toList())
            .map { tasks -> tasks.any { DatabaseSeeder.isActiveSampleTask(it) } }
            .distinctUntilChanged()
            .catch { emit(false) }

    suspend fun hasSampleTasks(): Boolean = runCatchingCancellable {
        database.taskDao()
            .getCandidateSampleTasks(DatabaseSeeder.SAMPLE_TASK_IDS.toList())
            .any { DatabaseSeeder.isActiveSampleTask(it) }
    }.getOrDefault(false)

    suspend fun countNonSampleTasks(): Int = runCatchingCancellable {
        val sampleIds = DatabaseSeeder.SAMPLE_TASK_IDS.toList()
        val nonSampleIdCount = database.taskDao().countActiveNonSampleTasks(sampleIds)
        val customizedSampleCount = database.taskDao()
            .getCandidateSampleTasks(sampleIds)
            .count { !DatabaseSeeder.isPurgeableSampleTask(it) }
        nonSampleIdCount + customizedSampleCount
    }.getOrDefault(0)

    suspend fun hasLocalUserCreatedTasks(): Boolean = countNonSampleTasks() > 0

    /**
     * Deletes seeded initial sample tasks (and any sample checklists, headings, projects, or tags
     * that do not contain user-created tasks) while preserving any sample task whose title was
     * customized by the user.
     *
     * When [markRemainingDirtyForMerge] is true, marks remaining user-created items `isDirty = true` so they
     * merge cleanly with the user's cloud account without untouched seeded areas clobbering cloud areas.
     * When [emitCloudTombstonesIfSignedIn] is true and Cloud Sync is active, emits compacted purged tombstones
     * (`isDeleted = true`, empty title/notes, `isDirty = true`) so sample tasks previously synced to Firestore
     * are permanently purged without appearing in Trash or resurrecting on full pull.
     */
    suspend fun purgeSampleDataLocally(
        markRemainingDirtyForMerge: Boolean = false,
        emitCloudTombstonesIfSignedIn: Boolean = false
    ) {
        val sampleTaskIds = DatabaseSeeder.SAMPLE_TASK_IDS.toList()
        val existingSampleTasks = database.taskDao().getByIds(sampleTaskIds)
        val preservedSampleTaskIds = existingSampleTasks
            .filterNot { DatabaseSeeder.isPurgeableSampleTask(it) }
            .mapTo(HashSet()) { it.id }
        val purgeableTasks = existingSampleTasks.filter { DatabaseSeeder.isPurgeableSampleTask(it) }
        val purgeableTaskIds = sampleTaskIds.filterNot { it in preservedSampleTaskIds }
        purgeableTaskIds.forEach { id -> reminderScheduler.cancelReminder(id) }

        val keepSyncTombstones = emitCloudTombstonesIfSignedIn &&
            !markRemainingDirtyForMerge &&
            !syncRepository.isLocalOnlyDbModeEnabled() &&
            syncRepository.hasActiveCloudAccount()
        val now = System.currentTimeMillis()

        database.withTransaction {
            if (keepSyncTombstones) {
                for (task in purgeableTasks) {
                    purgeOrCompactTaskInternal(task, keepSyncTombstones = true, now = now)
                }
                for (chkId in DatabaseSeeder.SAMPLE_CHECKLIST_IDS) {
                    val chk = database.checklistDao().getById(chkId)
                    if (chk != null && chk.taskId !in preservedSampleTaskIds && (!chk.isDeleted || chk.title.isNotEmpty())) {
                        database.checklistDao().upsert(
                            chk.copy(
                                title = "",
                                isDeleted = true,
                                isDirty = true,
                                updatedAt = now
                            )
                        )
                    }
                }
            } else if (purgeableTaskIds.isNotEmpty()) {
                val orphanChecklistIds = DatabaseSeeder.SAMPLE_CHECKLIST_IDS.filter { chkId ->
                    val chk = database.checklistDao().getById(chkId)
                    chk == null || chk.taskId !in preservedSampleTaskIds
                }
                database.checklistDao().hardDeleteForSampleTasks(
                    taskIds = purgeableTaskIds,
                    itemIds = orphanChecklistIds
                )
                database.taskDao().hardDeleteTaskTagsByTaskIds(purgeableTaskIds)
                database.taskDao().hardDeleteByIds(purgeableTaskIds)
            }

            val emptySampleHeadings = DatabaseSeeder.SAMPLE_HEADING_IDS.filter { hId ->
                database.headingDao().countActiveTasksInHeading(hId) == 0
            }
            if (emptySampleHeadings.isNotEmpty()) {
                if (keepSyncTombstones) {
                    for (hId in emptySampleHeadings) {
                        val heading = database.headingDao().getById(hId)
                        if (heading != null && (!heading.isDeleted || heading.title.isNotEmpty())) {
                            database.headingDao().upsert(
                                heading.copy(
                                    title = "",
                                    isDeleted = true,
                                    isDirty = true,
                                    updatedAt = now
                                )
                            )
                        }
                    }
                } else {
                    database.headingDao().hardDeleteByIds(emptySampleHeadings)
                }
            }

            val emptySampleProjects = DatabaseSeeder.SAMPLE_PROJECT_IDS.filter { pId ->
                database.projectDao().countActiveTasksInProject(pId) == 0
            }
            if (emptySampleProjects.isNotEmpty()) {
                if (keepSyncTombstones) {
                    for (pId in emptySampleProjects) {
                        val proj = database.projectDao().getById(pId)
                        if (proj != null) {
                            purgeOrCompactProjectInternal(proj, keepSyncTombstones = true, now = now)
                        }
                    }
                } else {
                    database.projectDao().hardDeleteProjectTagsByIds(emptySampleProjects)
                    database.projectDao().hardDeleteByIds(emptySampleProjects)
                }
            }

            val unusedSampleTags = DatabaseSeeder.SAMPLE_TAG_IDS.filter { tId ->
                database.tagDao().countTasksUsingTag(tId) == 0 &&
                    database.tagDao().countProjectsUsingTag(tId) == 0
            }
            if (unusedSampleTags.isNotEmpty()) {
                val purgedTagSet = unusedSampleTags.toHashSet()
                val cleanedAreas = database.areaDao().getActiveAreasList().mapNotNull { area ->
                    val existingTagIds = decodeStringList(area.tagIdsJson)
                    val filteredTagIds = existingTagIds.filterNot { it in purgedTagSet }
                    if (filteredTagIds.size != existingTagIds.size) {
                        area.copy(
                            tagIdsJson = json.encodeToString(filteredTagIds),
                            updatedAt = if (keepSyncTombstones) now else area.updatedAt,
                            isDirty = if (keepSyncTombstones) true else area.isDirty
                        )
                    } else {
                        null
                    }
                }
                if (cleanedAreas.isNotEmpty()) {
                    database.areaDao().upsertAll(cleanedAreas)
                }
                if (keepSyncTombstones) {
                    for (tId in unusedSampleTags) {
                        val tag = database.tagDao().getById(tId)
                        if (tag != null && (!tag.isDeleted || tag.title.isNotEmpty())) {
                            database.tagDao().upsert(
                                tag.copy(
                                    title = "",
                                    shortcut = null,
                                    parentTagId = null,
                                    isDeleted = true,
                                    isDirty = true,
                                    updatedAt = now
                                )
                            )
                        }
                    }
                } else {
                    database.tagDao().hardDeleteByIds(unusedSampleTags)
                }
            }

            if (markRemainingDirtyForMerge) {
                val activeAreasSnapshot = database.areaDao().getActiveAreasList()
                val emptyUntouchedSampleAreaIds = mutableListOf<String>()
                val areasToMarkDirty = mutableListOf<AreaEntity>()
                for (area in activeAreasSnapshot) {
                    val isSampleArea = area.id in DatabaseSeeder.SAMPLE_AREA_IDS
                    val defaultTitle = when (area.id) {
                        "area-work-001" -> "Work"
                        "area-home-002" -> "Home"
                        else -> null
                    }
                    val areaTags = decodeStringList(area.tagIdsJson)
                    val modifiedByUser = !isSampleArea ||
                        area.isDirty ||
                        (defaultTitle != null && area.title != defaultTitle) ||
                        areaTags.any { it !in DatabaseSeeder.SAMPLE_TAG_IDS }
                    if (!modifiedByUser &&
                        database.areaDao().countActiveTasksInArea(area.id) == 0 &&
                        database.areaDao().countActiveProjectsInArea(area.id) == 0
                    ) {
                        emptyUntouchedSampleAreaIds += area.id
                    } else if (modifiedByUser && !area.isDirty) {
                        areasToMarkDirty += area.copy(isDirty = true)
                    }
                }
                if (emptyUntouchedSampleAreaIds.isNotEmpty()) {
                    database.areaDao().hardDeleteByIds(emptyUntouchedSampleAreaIds)
                }
                if (areasToMarkDirty.isNotEmpty()) {
                    database.areaDao().upsertAll(areasToMarkDirty)
                }
                database.projectDao().markAllActiveDirty()
                database.headingDao().markAllActiveDirty()
                database.taskDao().markAllActiveDirty()
                database.checklistDao().markAllActiveDirty()
                database.tagDao().markAllActiveDirty()
                database.attachmentDao().markAllActiveDirty()
                database.projectTemplateDao().markAllActiveDirty()
            }
        }
        if (keepSyncTombstones) {
            syncRepository.triggerBackgroundSync()
        }
    }

    /**
     * Hard-clears all local tasks, checklists, headings, projects, areas, and tags locally in Room
     * without generating tombstones so that subsequent account sign-in loads exclusively the user's
     * cloud account tasks.
     */
    suspend fun purgeAllLocalDataBeforeAccountPull() {
        runCatchingCancellable {
            val futureReminders = database.taskDao().getOpenTasksWithFutureReminders(0L)
            futureReminders.forEach { task -> reminderScheduler.cancelReminder(task.id) }
        }
        DatabaseSeeder.SAMPLE_TASK_IDS.forEach { id -> reminderScheduler.cancelReminder(id) }

        val existingAttachments = database.attachmentDao().getAllNonDeletedAttachmentsList()
        existingAttachments.forEach { syncRepository.deleteLocalAttachmentFile(it.localRelativePath) }
        syncRepository.clearAllLocalAttachmentFiles()

        database.withTransaction {
            database.attachmentDao().hardDeleteAll()
            database.projectTemplateDao().hardDeleteAll()
            database.checklistDao().hardDeleteAll()
            database.taskDao().hardDeleteAllTaskTags()
            database.taskDao().hardDeleteAll()
            database.headingDao().hardDeleteAll()
            database.projectDao().hardDeleteAllProjectTags()
            database.projectDao().hardDeleteAll()
            database.areaDao().hardDeleteAll()
            database.tagDao().hardDeleteAll()
        }
    }

    // =========================================================================
    // Full Backup & Restore (.tbbackup — Optional AES-256-GCM Password Protection)
    // =========================================================================

    suspend fun createFullBackupBytes(
        password: CharArray?,
        appVersionName: String,
        appVersionCode: Int,
        preferences: BackupPreferencesDto
    ): Pair<ByteArray, BackupSummary> {
        val now = System.currentTimeMillis()
        val payload = database.withTransaction {
            val areas = database.areaDao().getActiveAreasList()
            val projects = database.projectDao().getAllNonDeletedProjectsList()
            val headings = database.headingDao().getAllNonDeletedHeadingsList()
            val tasks = database.taskDao().getAllNonDeletedTasksList()
            val checklistItems = database.checklistDao().getAllNonDeletedChecklistItemsList()
            val tags = database.tagDao().getAllNonDeletedTagsList()
            val attachments = database.attachmentDao().getAllNonDeletedAttachmentsList()
            val projectTemplates = database.projectTemplateDao().getAllNonDeletedTemplatesList()

            val activeTaskIds = tasks.mapTo(HashSet()) { it.id }
            val activeProjectIds = projects.mapTo(HashSet()) { it.id }
            val activeTagIds = tags.mapTo(HashSet()) { it.id }

            val taskTagRefs = database.taskDao().getAllTaskTagCrossRefs()
                .filter { it.taskId in activeTaskIds && it.tagId in activeTagIds }
            val projectTagRefs = database.projectDao().getAllProjectTagCrossRefs()
                .filter { it.projectId in activeProjectIds && it.tagId in activeTagIds }

            val validAttachments = attachments.filter { att ->
                (att.taskId != null && att.taskId in activeTaskIds) ||
                    (att.projectId != null && att.projectId in activeProjectIds)
            }

            ThingsbookBackupPayload(
                schemaVersion = ThingsBackupCodec.FORMAT_VERSION,
                createdAt = now,
                appVersionName = appVersionName,
                areas = areas.map { a ->
                    BackupAreaDto(
                        id = a.id,
                        title = a.title,
                        sortOrder = if (a.sortOrder.isFinite()) a.sortOrder else 0.0,
                        tagIdsJson = a.tagIdsJson,
                        createdAt = a.createdAt,
                        updatedAt = a.updatedAt
                    )
                },
                projects = projects.map { p ->
                    BackupProjectDto(
                        id = p.id,
                        areaId = p.areaId,
                        title = p.title,
                        notes = p.notes,
                        status = p.status.name,
                        scheduleState = p.scheduleState.name,
                        whenDate = p.whenDate,
                        deadline = p.deadline,
                        completedAt = p.completedAt,
                        lastReviewedAt = p.lastReviewedAt,
                        sortOrder = if (p.sortOrder.isFinite()) p.sortOrder else 0.0,
                        tagIdsJson = p.tagIdsJson,
                        workspaceId = p.workspaceId,
                        viewMode = p.viewMode,
                        createdAt = p.createdAt,
                        updatedAt = p.updatedAt
                    )
                },
                headings = headings.map { h ->
                    BackupHeadingDto(
                        id = h.id,
                        projectId = h.projectId,
                        title = h.title,
                        startDate = h.startDate,
                        endDate = h.endDate,
                        sortOrder = if (h.sortOrder.isFinite()) h.sortOrder else 0.0,
                        isArchived = h.isArchived,
                        createdAt = h.createdAt,
                        updatedAt = h.updatedAt
                    )
                },
                tasks = tasks.map { t ->
                    BackupTaskDto(
                        id = t.id,
                        title = t.title,
                        notes = t.notes,
                        status = t.status.name,
                        scheduleState = t.scheduleState.name,
                        whenDate = t.whenDate,
                        deadline = t.deadline,
                        reminderTime = t.reminderTime,
                        scheduledStartMinutes = t.scheduledStartMinutes,
                        durationMinutes = t.durationMinutes,
                        isPinned = t.isPinned,
                        isMilestone = t.isMilestone,
                        effortLevel = t.effortLevel,
                        blockedByJson = t.blockedByJson,
                        workspaceId = t.workspaceId,
                        assigneeId = t.assigneeId,
                        completedAt = t.completedAt,
                        projectId = t.projectId,
                        headingId = t.headingId,
                        areaId = t.areaId,
                        recurrenceRuleJson = t.recurrenceRuleJson,
                        sortOrder = if (t.sortOrder.isFinite()) t.sortOrder else 0.0,
                        tagIdsJson = t.tagIdsJson,
                        createdAt = t.createdAt,
                        updatedAt = t.updatedAt
                    )
                },
                checklistItems = checklistItems.map { c ->
                    BackupChecklistItemDto(
                        id = c.id,
                        taskId = c.taskId,
                        title = c.title,
                        isCompleted = c.isCompleted,
                        sortOrder = if (c.sortOrder.isFinite()) c.sortOrder else 0.0,
                        createdAt = c.createdAt,
                        updatedAt = c.updatedAt
                    )
                },
                tags = tags.map { tg ->
                    BackupTagDto(
                        id = tg.id,
                        title = tg.title,
                        parentTagId = tg.parentTagId,
                        shortcut = tg.shortcut,
                        sortOrder = if (tg.sortOrder.isFinite()) tg.sortOrder else 0.0,
                        createdAt = tg.createdAt,
                        updatedAt = tg.updatedAt
                    )
                },
                taskTagRefs = taskTagRefs.map { BackupTaskTagRefDto(it.taskId, it.tagId) },
                projectTagRefs = projectTagRefs.map { BackupProjectTagRefDto(it.projectId, it.tagId) },
                attachments = validAttachments.map { att ->
                    BackupAttachmentDto(
                        id = att.id,
                        taskId = att.taskId,
                        projectId = att.projectId,
                        fileName = att.fileName,
                        mimeType = att.mimeType,
                        sizeBytes = att.sizeBytes,
                        localRelativePath = att.localRelativePath,
                        dataBase64 = syncRepository.readAttachmentBase64(att.localRelativePath),
                        createdAt = att.createdAt,
                        updatedAt = att.updatedAt
                    )
                },
                projectTemplates = projectTemplates.map { tpl ->
                    BackupProjectTemplateDto(
                        id = tpl.id,
                        title = tpl.title,
                        notes = tpl.notes,
                        defaultAreaId = tpl.defaultAreaId,
                        tagIdsJson = tpl.tagIdsJson,
                        blueprintJson = tpl.blueprintJson,
                        sortOrder = if (tpl.sortOrder.isFinite()) tpl.sortOrder else 0.0,
                        createdAt = tpl.createdAt,
                        updatedAt = tpl.updatedAt
                    )
                },
                preferences = preferences
            )
        }

        val encrypted = password != null && password.isNotEmpty()
        val encodedBytes = ThingsBackupCodec.encodeBackup(
            payload = payload,
            password = password,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode
        )
        val summary = ThingsBackupCodec.summarizePayload(payload, encrypted = encrypted)
        return encodedBytes to summary
    }

    fun inspectBackupBytes(rawBytes: ByteArray): BackupInspectionResult =
        ThingsBackupCodec.inspectBackup(rawBytes)

    /**
     * Decrypts (if password-protected), validates, and atomically replaces all local entities
     * (`areas`, `tags`, `projects`, `project_tags`, `headings`, `tasks`, `task_tags`, `checklist_items`, `attachments`, `project_templates`).
     * If password verification or format parsing fails, throws BEFORE touching the database.
     */
    suspend fun restoreFullBackupFromBytes(
        rawBytes: ByteArray,
        password: CharArray?,
        syncAfterRestore: Boolean
    ): Pair<BackupSummary, BackupPreferencesDto> {
        // 1. Authenticate, decrypt, decompress, and parse BEFORE any DB write!
        val payload = ThingsBackupCodec.decodeBackup(rawBytes, password)
        val now = System.currentTimeMillis()

        // 2. Normalize and enforce referential integrity across all tables
        val restoredTagsRaw = payload.tags
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }
        val validTagIds = restoredTagsRaw.mapTo(HashSet()) { it.id }

        val restoredTags = restoredTagsRaw.map { tg ->
            TagEntity(
                id = tg.id,
                title = tg.title.trim(),
                parentTagId = tg.parentTagId?.takeIf { it in validTagIds && it != tg.id },
                shortcut = tg.shortcut?.trim()?.takeIf { it.isNotEmpty() },
                sortOrder = if (tg.sortOrder.isFinite()) tg.sortOrder else 0.0,
                createdAt = tg.createdAt.takeIf { it > 0L } ?: now,
                updatedAt = tg.updatedAt.takeIf { it > 0L } ?: now,
                isDeleted = false,
                isDirty = syncAfterRestore
            )
        }

        val restoredAreas = payload.areas
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }
            .take(MAX_AREAS)
            .map { a ->
                val cleanAreaTags = decodeStringList(a.tagIdsJson).filter { it in validTagIds }.distinct()
                AreaEntity(
                    id = a.id,
                    title = a.title.trim(),
                    sortOrder = if (a.sortOrder.isFinite()) a.sortOrder else 0.0,
                    tagIdsJson = json.encodeToString(cleanAreaTags),
                    createdAt = a.createdAt.takeIf { it > 0L } ?: now,
                    updatedAt = a.updatedAt.takeIf { it > 0L } ?: now,
                    isDeleted = false,
                    isDirty = syncAfterRestore
                )
            }
        val validAreaIds = restoredAreas.mapTo(HashSet()) { it.id }

        val explicitProjTagsByProject = payload.projectTagRefs
            .filter { it.tagId in validTagIds }
            .groupBy({ it.projectId }, { it.tagId })

        val restoredProjects = payload.projects
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .map { p ->
                val projStatus = runCatching { ProjectStatus.valueOf(p.status) }.getOrDefault(ProjectStatus.ACTIVE)
                val projSchedule = runCatching { ScheduleState.valueOf(p.scheduleState) }.getOrDefault(ScheduleState.ANYTIME)
                val combinedTagIds = (decodeStringList(p.tagIdsJson) + explicitProjTagsByProject[p.id].orEmpty())
                    .filter { it in validTagIds }
                    .distinct()
                ProjectEntity(
                    id = p.id,
                    areaId = p.areaId?.takeIf { it in validAreaIds },
                    title = p.title,
                    notes = p.notes,
                    status = projStatus,
                    scheduleState = projSchedule,
                    whenDate = p.whenDate,
                    deadline = p.deadline,
                    completedAt = p.completedAt,
                    lastReviewedAt = p.lastReviewedAt,
                    sortOrder = if (p.sortOrder.isFinite()) p.sortOrder else 0.0,
                    tagIdsJson = json.encodeToString(combinedTagIds),
                    workspaceId = p.workspaceId?.trim()?.takeIf { it.isNotEmpty() },
                    viewMode = p.viewMode?.trim()?.takeIf { it.isNotEmpty() },
                    createdAt = p.createdAt.takeIf { it > 0L } ?: now,
                    updatedAt = p.updatedAt.takeIf { it > 0L } ?: now,
                    isDeleted = false,
                    isDirty = syncAfterRestore
                )
            }
        val validProjectIds = restoredProjects.mapTo(HashSet()) { it.id }

        val restoredProjectTagRefs = restoredProjects.flatMap { proj ->
            decodeStringList(proj.tagIdsJson).map { tagId ->
                ProjectTagCrossRef(projectId = proj.id, tagId = tagId)
            }
        }

        val restoredHeadings = payload.headings
            .filter { it.id.isNotBlank() && it.projectId in validProjectIds }
            .distinctBy { it.id }
            .map { h ->
                HeadingEntity(
                    id = h.id,
                    projectId = h.projectId,
                    title = h.title,
                    startDate = h.startDate,
                    endDate = h.endDate,
                    sortOrder = if (h.sortOrder.isFinite()) h.sortOrder else 0.0,
                    isArchived = h.isArchived,
                    createdAt = h.createdAt.takeIf { it > 0L } ?: now,
                    updatedAt = h.updatedAt.takeIf { it > 0L } ?: now,
                    isDeleted = false,
                    isDirty = syncAfterRestore
                )
            }
        val headingProjectMap = restoredHeadings.associate { it.id to it.projectId }

        val explicitTaskTagsByTask = payload.taskTagRefs
            .filter { it.tagId in validTagIds }
            .groupBy({ it.taskId }, { it.tagId })

        val validPayloadTaskIds = payload.tasks
            .filter { it.id.isNotBlank() }
            .mapTo(HashSet()) { it.id }

        val restoredTasks = payload.tasks
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .map { t ->
                val taskStatus = runCatching { TaskStatus.valueOf(t.status) }.getOrDefault(TaskStatus.OPEN)
                val rawTaskSchedule = runCatching { ScheduleState.valueOf(t.scheduleState) }.getOrDefault(ScheduleState.INBOX)
                val cleanProjectId = t.projectId?.takeIf { it in validProjectIds }
                val cleanHeadingId = t.headingId?.takeIf { hId ->
                    cleanProjectId != null && headingProjectMap[hId] == cleanProjectId
                }
                val cleanAreaId = t.areaId?.takeIf { it in validAreaIds }
                val combinedTagIds = (decodeStringList(t.tagIdsJson) + explicitTaskTagsByTask[t.id].orEmpty())
                    .filter { it in validTagIds }
                    .distinct()
                val cleanBlockedByIds = decodeStringList(t.blockedByJson)
                    .filter { it in validPayloadTaskIds && it != t.id }
                    .distinct()
                val validRecurrenceJson = t.recurrenceRuleJson?.takeIf { raw ->
                    raw.isNotBlank() && runCatching { json.decodeFromString<RecurrenceRule>(raw) }.isSuccess
                }
                val taskSchedule = if (
                    rawTaskSchedule == ScheduleState.INBOX &&
                    t.whenDate == null &&
                    validRecurrenceJson == null &&
                    (cleanProjectId != null || cleanAreaId != null)
                ) {
                    ScheduleState.ANYTIME
                } else {
                    rawTaskSchedule
                }

                TaskEntity(
                    id = t.id,
                    title = t.title,
                    notes = t.notes,
                    status = taskStatus,
                    scheduleState = taskSchedule,
                    whenDate = t.whenDate,
                    deadline = t.deadline,
                    reminderTime = t.reminderTime,
                    scheduledStartMinutes = t.scheduledStartMinutes?.takeIf { it in 0..1439 },
                    durationMinutes = t.durationMinutes?.takeIf { it in 1..MAX_TASK_DURATION_MINUTES },
                    isPinned = t.isPinned,
                    isMilestone = t.isMilestone,
                    effortLevel = EffortLevel.normalize(t.effortLevel),
                    blockedByJson = json.encodeToString(cleanBlockedByIds),
                    workspaceId = t.workspaceId?.trim()?.takeIf { it.isNotEmpty() },
                    assigneeId = t.assigneeId?.trim()?.takeIf { it.isNotEmpty() },
                    completedAt = t.completedAt,
                    projectId = cleanProjectId,
                    headingId = cleanHeadingId,
                    areaId = cleanAreaId,
                    recurrenceRuleJson = validRecurrenceJson,
                    sortOrder = if (t.sortOrder.isFinite()) t.sortOrder else 0.0,
                    tagIdsJson = json.encodeToString(combinedTagIds),
                    createdAt = t.createdAt.takeIf { it > 0L } ?: now,
                    updatedAt = t.updatedAt.takeIf { it > 0L } ?: now,
                    isDeleted = false,
                    isDirty = syncAfterRestore
                )
            }
        val validTaskIds = restoredTasks.mapTo(HashSet()) { it.id }

        val restoredTaskTagRefs = restoredTasks.flatMap { task ->
            decodeStringList(task.tagIdsJson).map { tagId ->
                TaskTagCrossRef(taskId = task.id, tagId = tagId)
            }
        }

        val restoredChecklistItems = payload.checklistItems
            .filter { it.id.isNotBlank() && it.taskId in validTaskIds }
            .distinctBy { it.id }
            .map { c ->
                ChecklistItemEntity(
                    id = c.id,
                    taskId = c.taskId,
                    title = c.title,
                    isCompleted = c.isCompleted,
                    sortOrder = if (c.sortOrder.isFinite()) c.sortOrder else 0.0,
                    createdAt = c.createdAt.takeIf { it > 0L } ?: now,
                    updatedAt = c.updatedAt.takeIf { it > 0L } ?: now,
                    isDeleted = false,
                    isDirty = syncAfterRestore
                )
            }

        val restoredProjectTemplates = payload.projectTemplates
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }
            .map { tpl ->
                val cleanTplTags = decodeStringList(tpl.tagIdsJson).filter { it in validTagIds }.distinct()
                ProjectTemplateEntity(
                    id = tpl.id,
                    title = tpl.title.trim(),
                    notes = tpl.notes,
                    defaultAreaId = tpl.defaultAreaId?.takeIf { it in validAreaIds },
                    tagIdsJson = json.encodeToString(cleanTplTags),
                    blueprintJson = tpl.blueprintJson.ifBlank { "{}" },
                    sortOrder = if (tpl.sortOrder.isFinite()) tpl.sortOrder else 0.0,
                    createdAt = tpl.createdAt.takeIf { it > 0L } ?: now,
                    updatedAt = tpl.updatedAt.takeIf { it > 0L } ?: now,
                    isDeleted = false,
                    isDirty = syncAfterRestore
                )
            }

        // Clear existing local attachment files before writing restored backup attachments
        syncRepository.clearAllLocalAttachmentFiles()

        val restoredAttachments = payload.attachments
            .filter { att ->
                att.id.isNotBlank() &&
                    ((att.taskId != null && att.taskId in validTaskIds) ||
                        (att.projectId != null && att.projectId in validProjectIds))
            }
            .distinctBy { it.id }
            .map { att ->
                val relPath = syncRepository.buildSafeLocalAttachmentPath(att.id, att.fileName)
                val writtenBytes = if (att.dataBase64.isNotBlank()) {
                    syncRepository.writeAttachmentBase64(relPath, att.dataBase64)
                } else {
                    0L
                }
                AttachmentEntity(
                    id = att.id,
                    taskId = att.taskId?.takeIf { it in validTaskIds },
                    projectId = att.projectId?.takeIf { it in validProjectIds },
                    fileName = att.fileName,
                    mimeType = att.mimeType,
                    sizeBytes = if (writtenBytes > 0L) writtenBytes else att.sizeBytes.coerceAtLeast(0L),
                    localRelativePath = relPath,
                    createdAt = att.createdAt.takeIf { it > 0L } ?: now,
                    updatedAt = att.updatedAt.takeIf { it > 0L } ?: now,
                    isDeleted = false,
                    isDirty = syncAfterRestore
                )
            }

        // 3. Cancel previous task reminders before replacing the database
        runCatchingCancellable {
            val previousReminders = database.taskDao().getOpenTasksWithFutureReminders(0L)
            previousReminders.forEach { reminderScheduler.cancelReminder(it.id) }
        }
        DatabaseSeeder.SAMPLE_TASK_IDS.forEach { id -> reminderScheduler.cancelReminder(id) }

        // 4. Atomic full swap inside a single Room SQLite transaction
        database.withTransaction {
            database.attachmentDao().hardDeleteAll()
            database.projectTemplateDao().hardDeleteAll()
            database.checklistDao().hardDeleteAll()
            database.taskDao().hardDeleteAllTaskTags()
            database.taskDao().hardDeleteAll()
            database.headingDao().hardDeleteAll()
            database.projectDao().hardDeleteAllProjectTags()
            database.projectDao().hardDeleteAll()
            database.areaDao().hardDeleteAll()
            database.tagDao().hardDeleteAll()

            if (restoredAreas.isNotEmpty()) database.areaDao().upsertAll(restoredAreas)
            if (restoredTags.isNotEmpty()) database.tagDao().upsertAll(restoredTags)
            if (restoredProjects.isNotEmpty()) database.projectDao().upsertAll(restoredProjects)
            if (restoredProjectTagRefs.isNotEmpty()) database.projectDao().insertProjectTagCrossRefs(restoredProjectTagRefs)
            if (restoredHeadings.isNotEmpty()) database.headingDao().upsertAll(restoredHeadings)
            if (restoredTasks.isNotEmpty()) database.taskDao().upsertAll(restoredTasks)
            if (restoredTaskTagRefs.isNotEmpty()) database.taskDao().insertTaskTagCrossRefs(restoredTaskTagRefs)
            if (restoredChecklistItems.isNotEmpty()) database.checklistDao().upsertAll(restoredChecklistItems)
            if (restoredAttachments.isNotEmpty()) database.attachmentDao().upsertAll(restoredAttachments)
            if (restoredProjectTemplates.isNotEmpty()) database.projectTemplateDao().upsertAll(restoredProjectTemplates)
        }

        // 5. Reschedule future reminders for restored open tasks & trigger cloud sync if active
        runCatchingCancellable { rescheduleAllOpenTaskReminders() }
        if (syncAfterRestore) {
            syncRepository.triggerBackgroundSync()
        }

        val summary = BackupSummary(
            createdAt = payload.createdAt,
            appVersionName = payload.appVersionName,
            encrypted = password != null && password.isNotEmpty(),
            areasCount = restoredAreas.size,
            projectsCount = restoredProjects.size,
            headingsCount = restoredHeadings.size,
            tasksCount = restoredTasks.size,
            checklistItemsCount = restoredChecklistItems.size,
            tagsCount = restoredTags.size,
            attachmentsCount = restoredAttachments.size,
            projectTemplatesCount = restoredProjectTemplates.size
        )
        return summary to payload.preferences
    }

    private fun decodeStringList(raw: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    private fun TaskWithDetails.toDomain(): TaskItem = TaskItem(
        id = task.id,
        title = task.title,
        notes = task.notes,
        status = task.status,
        scheduleState = task.scheduleState,
        whenDate = task.whenDate,
        deadline = task.deadline,
        reminderTime = task.reminderTime,
        scheduledStartMinutes = task.scheduledStartMinutes,
        durationMinutes = task.durationMinutes,
        isPinned = task.isPinned,
        isMilestone = task.isMilestone,
        effortLevel = task.effortLevel,
        blockedByTaskIds = decodeStringList(task.blockedByJson),
        workspaceId = task.workspaceId,
        assigneeId = task.assigneeId,
        completedAt = task.completedAt,
        projectId = task.projectId,
        headingId = task.headingId,
        areaId = task.areaId,
        recurrenceRule = task.recurrenceRuleJson?.let {
            runCatching { json.decodeFromString<RecurrenceRule>(it) }.getOrNull()
        },
        sortOrder = task.sortOrder,
        checklistItems = checklistItems
            .filter { !it.isDeleted }
            .sortedBy { it.sortOrder }
            .map { it.toDomain() },
        tags = tags
            .filter { !it.isDeleted }
            .sortedBy { it.sortOrder }
            .map { it.toDomain() },
        attachments = attachments
            .filter { !it.isDeleted }
            .sortedBy { it.createdAt }
            .map { it.toDomain() },
        createdAt = task.createdAt,
        updatedAt = task.updatedAt,
        isDeleted = task.isDeleted
    )

    private fun AttachmentEntity.toDomain(): TaskAttachment = TaskAttachment(
        id = id,
        taskId = taskId,
        projectId = projectId,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        localRelativePath = localRelativePath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun ChecklistItemEntity.toDomain(): ChecklistItem = ChecklistItem(
        id = id,
        taskId = taskId,
        title = title,
        isCompleted = isCompleted,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun TagEntity.toDomain(): Tag = Tag(
        id = id,
        title = title,
        parentTagId = parentTagId,
        shortcut = shortcut,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun AreaEntity.toDomain(): Area = Area(
        id = id,
        title = title,
        sortOrder = sortOrder,
        tagIds = decodeStringList(tagIdsJson),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun ProjectWithProgressTuple.toDomain(): Project = Project(
        id = project.id,
        areaId = project.areaId,
        title = project.title,
        notes = project.notes,
        status = project.status,
        scheduleState = project.scheduleState,
        whenDate = project.whenDate,
        deadline = project.deadline,
        completedAt = project.completedAt,
        lastReviewedAt = project.lastReviewedAt,
        sortOrder = project.sortOrder,
        tagIds = decodeStringList(project.tagIdsJson),
        workspaceId = project.workspaceId,
        viewMode = project.viewMode,
        totalTaskCount = totalTaskCount,
        completedTaskCount = completedTaskCount,
        createdAt = project.createdAt,
        updatedAt = project.updatedAt,
        isDeleted = project.isDeleted
    )

    private fun ProjectTemplateEntity.toDomain(): ProjectTemplate = ProjectTemplate(
        id = id,
        title = title,
        notes = notes,
        defaultAreaId = defaultAreaId,
        tagIds = decodeStringList(tagIdsJson),
        blueprint = ProjectTemplateCodec.decodeBlueprint(blueprintJson, fallbackTitle = title, fallbackNotes = notes),
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isBuiltIn = false,
        isDeleted = isDeleted
    )
}

internal object CsvParserUtils {
    fun encodeSemicolonList(items: List<String>): String {
        return items.joinToString("; ") { item ->
            item.replace("\\", "\\\\").replace(";", "\\;")
        }
    }

    fun decodeSemicolonList(raw: String, allowPipeSeparator: Boolean = false): List<String> {
        if (raw.isBlank()) return emptyList()
        val items = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                val next = raw[i + 1]
                if (next == ';' || next == '\\' || (allowPipeSeparator && next == '|')) {
                    sb.append(next)
                    i += 2
                    continue
                }
            }
            if (ch == ';' || (allowPipeSeparator && ch == '|')) {
                val token = sb.toString().trim()
                if (token.isNotEmpty()) items.add(token)
                sb.clear()
            } else {
                sb.append(ch)
            }
            i++
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) items.add(tail)
        return items
    }

    fun escapeCsvField(value: String): String {
        if (
            value.contains(",") ||
            value.contains(";") ||
            value.contains("\"") ||
            value.contains("\n") ||
            value.contains("\r")
        ) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    fun parseCsvRows(csv: String, delimiter: Char): List<List<String>> {
        val cleanCsv = csv.removePrefix("\uFEFF")
        val result = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < cleanCsv.length) {
            val ch = cleanCsv[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < cleanCsv.length && cleanCsv[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> {
                        if (currentField.isEmpty()) {
                            inQuotes = true
                        } else {
                            currentField.append(ch)
                        }
                    }
                    delimiter -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                    }
                    '\r' -> {
                        if (i + 1 < cleanCsv.length && cleanCsv[i + 1] == '\n') i++
                        currentRow.add(currentField.toString())
                        currentField.clear()
                        if (currentRow.any { it.isNotBlank() }) result.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                        if (currentRow.any { it.isNotBlank() }) result.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    else -> currentField.append(ch)
                }
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.any { it.isNotBlank() }) result.add(currentRow)
        }
        return result
    }

    private fun normalizeCsvYear(rawYear: Int): Int? {
        val normalized = if (rawYear in 0..99) 2000 + rawYear else rawYear
        return normalized.takeIf { it in 1970..2100 }
    }

    fun parseFlexibleCsvDate(raw: String, zone: ZoneId): Long? {
        if (raw.isBlank()) return null
        val trimmed = raw.trim()

        // 1. ISO LocalDate (YYYY-MM-DD)
        runCatching {
            val ld = LocalDate.parse(trimmed)
            if (ld.year in 1970..2100) {
                return ld.atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }

        // 2. DD.MM.YYYY or DD.MM.YY
        if (trimmed.contains(".")) {
            runCatching {
                val parts = trimmed.split(".")
                if (parts.size == 3) {
                    val d = parts[0].trim().toInt()
                    val m = parts[1].trim().toInt()
                    val y = normalizeCsvYear(parts[2].trim().toInt()) ?: return@runCatching
                    return LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
                }
            }
        }

        // 3. DD/MM/YYYY, MM/DD/YYYY (when middle token > 12), or YYYY/MM/DD
        if (trimmed.contains("/")) {
            runCatching {
                val parts = trimmed.split("/")
                if (parts.size == 3) {
                    val p0 = parts[0].trim().toInt()
                    val p1 = parts[1].trim().toInt()
                    val p2 = parts[2].trim().toInt()
                    val ld = if (parts[0].trim().length == 4) {
                        val y = normalizeCsvYear(p0) ?: return@runCatching
                        LocalDate.of(y, p1, p2)
                    } else {
                        val y = normalizeCsvYear(p2) ?: return@runCatching
                        if (p1 > 12 && p0 in 1..12) {
                            LocalDate.of(y, p0, p1)
                        } else {
                            LocalDate.of(y, p1, p0)
                        }
                    }
                    return ld.atStartOfDay(zone).toInstant().toEpochMilli()
                }
            }
        }

        // 4. ISO Instant (e.g. 2026-11-08T00:00:00Z)
        runCatching {
            val inst = Instant.parse(trimmed)
            val ld = inst.atZone(zone).toLocalDate()
            if (ld.year in 1970..2100) {
                return ld.atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }

        // 5. Numeric epoch millis
        val asLong = trimmed.toLongOrNull()
        if (asLong != null && asLong > 86_400_000L) {
            runCatching {
                val ld = Instant.ofEpochMilli(asLong).atZone(zone).toLocalDate()
                if (ld.year in 1970..2100) {
                    return ld.atStartOfDay(zone).toInstant().toEpochMilli()
                }
            }
        }

        return null
    }
}
