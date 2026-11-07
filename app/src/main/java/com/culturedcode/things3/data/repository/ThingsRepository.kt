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
        val baseEpoch = when (rule.mode) {
            RecurrenceMode.AFTER_COMPLETION -> now
            RecurrenceMode.FIXED_SCHEDULE -> completedTask.whenDate ?: now
        }
        val baseDate = Instant.ofEpochMilli(baseEpoch).atZone(zone).toLocalDate()
        val nextDate = when (rule.unit) {
            RecurrenceUnit.DAYS -> baseDate.plusDays(rule.interval.toLong())
            RecurrenceUnit.WEEKS -> baseDate.plusWeeks(rule.interval.toLong())
            RecurrenceUnit.MONTHS -> baseDate.plusMonths(rule.interval.toLong())
            RecurrenceUnit.YEARS -> baseDate.plusYears(rule.interval.toLong())
        }
        val nextWhenEpoch = nextDate.atStartOfDay(zone).toInstant().toEpochMilli()

        val nextEntity = completedTask.copy(
            id = UUID.randomUUID().toString(),
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.UPCOMING,
            whenDate = nextWhenEpoch,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
            isDirty = true
        )
        database.taskDao().upsertTaskWithTags(nextEntity, tagIds)
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
        syncRepository.syncBidirectionalDeltas()
    }

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
