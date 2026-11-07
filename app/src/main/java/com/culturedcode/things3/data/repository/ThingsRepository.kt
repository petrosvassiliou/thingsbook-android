package com.culturedcode.things3.data.repository

import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.local.entity.AreaEntity
import com.culturedcode.things3.data.local.entity.AreaWithProjectsAndTasks
import com.culturedcode.things3.data.local.entity.ChecklistItemEntity
import com.culturedcode.things3.data.local.entity.HeadingEntity
import com.culturedcode.things3.data.local.entity.ProjectEntity
import com.culturedcode.things3.data.local.entity.ProjectWithDetails
import com.culturedcode.things3.data.local.entity.ProjectWithProgressTuple
import com.culturedcode.things3.data.local.entity.TagEntity
import com.culturedcode.things3.data.local.entity.TaskEntity
import com.culturedcode.things3.data.local.entity.TaskWithDetails
import com.culturedcode.things3.domain.model.Area
import com.culturedcode.things3.domain.model.ChecklistItem
import com.culturedcode.things3.domain.model.Heading
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.domain.model.ProjectStatus
import com.culturedcode.things3.domain.model.RecurrenceMode
import com.culturedcode.things3.domain.model.RecurrenceRule
import com.culturedcode.things3.domain.model.RecurrenceUnit
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.Tag
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThingsRepository @Inject constructor(
    private val database: ThingsDatabase,
    private val syncRepository: SyncRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun endOfTodayEpochMillis(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
    }

    fun observeInboxTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeInboxTasks().map { list -> list.map { it.toDomain() } }

    fun observeTodayDaytimeTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeTodayTasks(endOfTodayEpochMillis()).map { list -> list.map { it.toDomain() } }

    fun observeThisEveningTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeThisEveningTasks().map { list -> list.map { it.toDomain() } }

    fun observeUpcomingTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeUpcomingTasks(endOfTodayEpochMillis()).map { list -> list.map { it.toDomain() } }

    fun observeAnytimeTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeAnytimeTasks().map { list -> list.map { it.toDomain() } }

    fun observeSomedayTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeSomedayTasks().map { list -> list.map { it.toDomain() } }

    fun observeLogbookTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeLogbookTasks().map { list -> list.map { it.toDomain() } }

    fun observeActiveProjectsWithProgress(): Flow<List<Project>> =
        database.projectDao().observeActiveProjectsWithProgress().map { list -> list.map { it.toDomain() } }

    fun observeAreas(): Flow<List<Area>> =
        database.areaDao().observeAreas().map { list -> list.map { it.toDomain() } }

    fun observeAllTags(): Flow<List<Tag>> =
        database.tagDao().observeAllTags().map { list -> list.map { it.toDomain() } }

    companion object {
        const val MAX_AREAS = 6
    }

    fun observeAllActiveHeadings(): Flow<List<Heading>> =
        database.headingDao().observeAllActiveHeadings().map { list ->
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

    fun searchTasksFts5(rawQuery: String): Flow<List<TaskItem>> {
        val sql = ThingsDatabase.buildTasksFts5SqlQuery(rawQuery)
        return database.taskDao().searchTasksFts5(sql).map { list -> list.map { it.toDomain() } }
    }

    fun searchProjectsFts5(rawQuery: String): Flow<List<Project>> {
        val sql = ThingsDatabase.buildProjectsFts5SqlQuery(rawQuery)
        return database.taskDao().searchProjectsFts5(sql).map { list ->
            list.map { entity ->
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
                    sortOrder = entity.sortOrder,
                    tagIds = decodeStringList(entity.tagIdsJson),
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    isDeleted = entity.isDeleted
                )
            }
        }
    }

    /**
     * Computes a LexoRank-style midpoint `sortOrder` between two neighboring tasks so drag-and-drop
     * or Magic Plus insertion between two rows never requires re-indexing the entire table.
     */
    fun computeMidpointSortOrder(previousSortOrder: Double?, nextSortOrder: Double?): Double {
        return when {
            previousSortOrder == null && nextSortOrder == null -> 1000.0
            previousSortOrder == null -> nextSortOrder!! - 500.0
            nextSortOrder == null -> previousSortOrder + 1000.0
            else -> (previousSortOrder + nextSortOrder) / 2.0
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
        projectId: String? = null,
        headingId: String? = null,
        areaId: String? = null,
        recurrenceRule: RecurrenceRule? = null,
        sortOrder: Double = 1000.0,
        tagIds: List<String> = emptyList()
    ): String {
        val now = System.currentTimeMillis()
        val existing = database.taskDao().getById(id)
        val completedAt = when (status) {
            TaskStatus.COMPLETED, TaskStatus.CANCELED -> existing?.completedAt ?: now
            TaskStatus.OPEN -> null
        }

        val entity = TaskEntity(
            id = id,
            title = title,
            notes = notes,
            status = status,
            scheduleState = scheduleState,
            whenDate = whenDate,
            deadline = deadline,
            reminderTime = reminderTime,
            completedAt = completedAt,
            projectId = projectId,
            headingId = headingId,
            areaId = areaId,
            recurrenceRuleJson = recurrenceRule?.let { json.encodeToString(it) },
            sortOrder = sortOrder,
            tagIdsJson = json.encodeToString(tagIds),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            isDeleted = false,
            isDirty = true
        )

        database.taskDao().upsertTaskWithTags(entity, tagIds)

        // If a recurring task was just completed, spawn the next occurrence automatically
        if (existing?.status == TaskStatus.OPEN && status == TaskStatus.COMPLETED && recurrenceRule != null) {
            spawnNextRecurrenceInstance(entity, recurrenceRule, tagIds, now)
        }

        syncRepository.syncBidirectionalDeltas()
        return id
    }

    private suspend fun spawnNextRecurrenceInstance(
        completedTask: TaskEntity,
        rule: RecurrenceRule,
        tagIds: List<String>,
        now: Long
    ) {
        val zone = ZoneId.systemDefault()
        val safeInterval = rule.interval.coerceAtLeast(1).toLong()
        val baseEpoch = when (rule.mode) {
            RecurrenceMode.AFTER_COMPLETION -> now
            RecurrenceMode.FIXED_SCHEDULE -> completedTask.whenDate ?: now
        }
        val baseDate = Instant.ofEpochMilli(baseEpoch).atZone(zone).toLocalDate()

        val nextDate: LocalDate = when (rule.unit) {
            RecurrenceUnit.DAYS -> baseDate.plusDays(safeInterval)
            RecurrenceUnit.WEEKS -> {
                if (rule.mode == RecurrenceMode.FIXED_SCHEDULE && rule.daysOfWeek.isNotEmpty()) {
                    val sortedDays = rule.daysOfWeek.map { it.coerceIn(1, 7) }.distinct().sorted()
                    val currentDow = baseDate.dayOfWeek.value // 1 (Mon) .. 7 (Sun)
                    val nextDaySameWeek = sortedDays.firstOrNull { it > currentDow }
                    if (nextDaySameWeek != null) {
                        baseDate.plusDays((nextDaySameWeek - currentDow).toLong())
                    } else {
                        val firstDow = sortedDays.first()
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

        val nextWhenEpoch = nextDate.atStartOfDay(zone).toInstant().toEpochMilli()

        // Respect optional endDateEpochMillis ("Endet am")
        if (rule.endDateEpochMillis != null && nextWhenEpoch > rule.endDateEpochMillis) {
            return
        }

        // Shift deadline by the same number of days if the task had both whenDate and deadline
        val nextDeadlineEpoch = if (completedTask.deadline != null && completedTask.whenDate != null) {
            val deltaMillis = nextWhenEpoch - completedTask.whenDate
            completedTask.deadline + deltaMillis
        } else {
            completedTask.deadline
        }

        val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val nextScheduleState = if (nextWhenEpoch <= todayEpoch) ScheduleState.TODAY else ScheduleState.UPCOMING

        val nextTaskId = UUID.randomUUID().toString()
        val nextEntity = completedTask.copy(
            id = nextTaskId,
            status = TaskStatus.OPEN,
            scheduleState = nextScheduleState,
            whenDate = nextWhenEpoch,
            deadline = nextDeadlineEpoch,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
            isDirty = true
        )
        database.taskDao().upsertTaskWithTags(nextEntity, tagIds)

        // Clone subtasks (ChecklistItems) reset to uncompleted (isCompleted = false), just like Things for Mac!
        val existingSubtasks = database.checklistDao().getForTaskSync(completedTask.id)
        if (existingSubtasks.isNotEmpty()) {
            val clonedSubtasks = existingSubtasks.map { item ->
                item.copy(
                    id = UUID.randomUUID().toString(),
                    taskId = nextTaskId,
                    isCompleted = false,
                    createdAt = now,
                    updatedAt = now,
                    isDirty = true
                )
            }
            database.checklistDao().upsertAll(clonedSubtasks)
        }
    }

    suspend fun updateTaskStatus(taskId: String, newStatus: TaskStatus) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            status = newStatus,
            completedAt = if (newStatus == TaskStatus.OPEN) null else now,
            updatedAt = now,
            isDirty = true
        )
        database.taskDao().upsert(updated)

        // If an open recurring task was just checked off, spawn the next occurrence!
        if (existing.status == TaskStatus.OPEN && newStatus == TaskStatus.COMPLETED && existing.recurrenceRuleJson != null) {
            val rule = runCatching { json.decodeFromString<RecurrenceRule>(existing.recurrenceRuleJson) }.getOrNull()
            if (rule != null) {
                val tagIds = decodeStringList(existing.tagIdsJson)
                spawnNextRecurrenceInstance(updated, rule, tagIds, now)
            }
        }

        syncRepository.syncBidirectionalDeltas()
    }

    suspend fun updateTaskRecurrence(taskId: String, recurrenceRule: RecurrenceRule?) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val updatedWhen = if (recurrenceRule != null && existing.whenDate == null) todayEpoch else existing.whenDate
        val updatedState = if (recurrenceRule != null && existing.scheduleState == ScheduleState.INBOX) {
            ScheduleState.TODAY
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
        syncRepository.syncBidirectionalDeltas()
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
        val resolvedSchedule = if (existing.scheduleState == ScheduleState.INBOX && (projectId != null || resolvedAreaId != null)) {
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
        syncRepository.syncBidirectionalDeltas()
    }

    // =========================================================================
    // Areas of Responsibility (Bereiche — Work, Home, max 6, Rename, Move, Delete)
    // =========================================================================

    suspend fun ensureDefaultWorkAndHomeAreas() {
        val activeAreas = database.areaDao().getActiveAreasList()
        if (activeAreas.isEmpty()) {
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
                        isDirty = true
                    ),
                    AreaEntity(
                        id = "area-home-002",
                        title = "Home",
                        sortOrder = 2000.0,
                        tagIdsJson = "[]",
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        isDirty = true
                    )
                )
            )
        }
    }

    suspend fun createArea(title: String): Result<String> {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Bereichsname darf nicht leer sein."))
        val activeAreas = database.areaDao().getActiveAreasList()
        if (activeAreas.size >= MAX_AREAS) {
            return Result.failure(IllegalStateException("Maximal $MAX_AREAS Bereiche möglich."))
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
        syncRepository.syncBidirectionalDeltas()
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
        syncRepository.syncBidirectionalDeltas()
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
        syncRepository.syncBidirectionalDeltas()
    }

    suspend fun softDeleteArea(areaId: String) {
        val existing = database.areaDao().getById(areaId) ?: return
        val now = System.currentTimeMillis()
        database.projectDao().clearAreaFromProjects(areaId, now)
        database.taskDao().clearAreaFromTasks(areaId, now)
        database.areaDao().upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.syncBidirectionalDeltas()
    }

    // =========================================================================
    // Projects (with Target Date / Deadline, Area Assignment, Move, Complete, Delete)
    // =========================================================================

    suspend fun createOrUpdateProject(
        id: String = UUID.randomUUID().toString(),
        title: String,
        notes: String = "",
        areaId: String? = null,
        status: ProjectStatus = ProjectStatus.ACTIVE,
        scheduleState: ScheduleState = ScheduleState.ANYTIME,
        whenDate: Long? = null,
        deadline: Long? = null,
        tagIds: List<String> = emptyList()
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
            sortOrder = existing?.sortOrder ?: (maxSort + 1000.0),
            tagIdsJson = json.encodeToString(tagIds),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            isDeleted = false,
            isDirty = true
        )
        database.projectDao().upsertProjectWithTags(entity, tagIds)
        syncRepository.syncBidirectionalDeltas()
        return id
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
        syncRepository.syncBidirectionalDeltas()
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
        syncRepository.syncBidirectionalDeltas()
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
        syncRepository.syncBidirectionalDeltas()
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
        syncRepository.syncBidirectionalDeltas()
    }

    suspend fun softDeleteProject(projectId: String) {
        val existing = database.projectDao().getById(projectId) ?: return
        val now = System.currentTimeMillis()
        database.taskDao().clearProjectFromTasks(projectId, now)
        database.projectDao().upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.syncBidirectionalDeltas()
    }

    // =========================================================================
    // Project Headings (Abschnitte — Create, Rename, Move, Delete)
    // =========================================================================

    suspend fun createHeadingInProject(
        projectId: String,
        title: String,
        sortOrder: Double
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        database.headingDao().upsert(
            HeadingEntity(
                id = id,
                projectId = projectId,
                title = title,
                sortOrder = sortOrder,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                isDirty = true
            )
        )
        syncRepository.syncBidirectionalDeltas()
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
        syncRepository.syncBidirectionalDeltas()
    }

    suspend fun softDeleteHeading(headingId: String) {
        val existing = database.headingDao().getById(headingId) ?: return
        val now = System.currentTimeMillis()
        database.taskDao().clearHeadingFromTasks(headingId, now)
        database.headingDao().upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.syncBidirectionalDeltas()
    }

    // =========================================================================
    // Subtasks (Unteraufgaben / Checklist Items — Create, Toggle, Rename, Move, Delete)
    // =========================================================================

    suspend fun addChecklistItem(
        taskId: String,
        title: String,
        sortOrder: Double = System.currentTimeMillis().toDouble()
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        database.checklistDao().upsert(
            ChecklistItemEntity(
                id = id,
                taskId = taskId,
                title = title,
                isCompleted = false,
                sortOrder = sortOrder,
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                isDirty = true
            )
        )
        syncRepository.syncBidirectionalDeltas()
        return id
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
        syncRepository.syncBidirectionalDeltas()
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
        syncRepository.syncBidirectionalDeltas()
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
        syncRepository.syncBidirectionalDeltas()
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
        syncRepository.syncBidirectionalDeltas()
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
        syncRepository.syncBidirectionalDeltas()
    }

    suspend fun softDeleteTask(taskId: String) {
        val existing = database.taskDao().getById(taskId) ?: return
        val now = System.currentTimeMillis()
        database.taskDao().upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.syncBidirectionalDeltas()
    }

    suspend fun createOrUpdateTag(
        id: String = UUID.randomUUID().toString(),
        title: String,
        shortcut: String? = null,
        parentTagId: String? = null
    ): String {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return id
        val now = System.currentTimeMillis()
        val existing = database.tagDao().getById(id)
        val tagEntity = TagEntity(
            id = id,
            title = trimmedTitle,
            parentTagId = parentTagId,
            shortcut = shortcut?.trim()?.takeIf { it.isNotEmpty() },
            sortOrder = existing?.sortOrder ?: (now % 100_000).toDouble(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            isDeleted = false,
            isDirty = true
        )
        database.tagDao().upsert(tagEntity)
        syncRepository.syncBidirectionalDeltas()
        return id
    }

    suspend fun softDeleteTag(tagId: String) {
        val existing = database.tagDao().getById(tagId) ?: return
        val now = System.currentTimeMillis()
        database.tagDao().deleteTaskTagCrossRefsForTag(tagId)
        database.tagDao().deleteProjectTagCrossRefsForTag(tagId)
        database.tagDao().upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.syncBidirectionalDeltas()
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
        val now = System.currentTimeMillis()
        val projectId = UUID.randomUUID().toString()
        val todayEpoch = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val projectEntity = ProjectEntity(
            id = projectId,
            areaId = areaId,
            title = title,
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
                    title = headingTitle,
                    sortOrder = (hIndex + 1) * 1000.0,
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false,
                    isDirty = true
                )
            )
            val tasks = taskTitles.mapIndexed { tIndex, taskTitle ->
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
            database.taskDao().upsertAll(tasks)
        }

        syncRepository.syncBidirectionalDeltas()
        return projectId
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
        createdAt = task.createdAt,
        updatedAt = task.updatedAt,
        isDeleted = task.isDeleted
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
        sortOrder = project.sortOrder,
        tagIds = decodeStringList(project.tagIdsJson),
        totalTaskCount = totalTaskCount,
        completedTaskCount = completedTaskCount,
        createdAt = project.createdAt,
        updatedAt = project.updatedAt,
        isDeleted = project.isDeleted
    )
}
