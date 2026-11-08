package com.culturedcode.things3.data.repository

import androidx.room.withTransaction
import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.local.entity.AreaEntity
import com.culturedcode.things3.data.local.entity.ChecklistItemEntity
import com.culturedcode.things3.data.local.entity.HeadingEntity
import com.culturedcode.things3.data.local.entity.ProjectEntity
import com.culturedcode.things3.data.local.entity.ProjectWithProgressTuple
import com.culturedcode.things3.data.local.entity.TagEntity
import com.culturedcode.things3.data.local.entity.TaskEntity
import com.culturedcode.things3.data.local.entity.TaskWithDetails
import com.culturedcode.things3.data.notifications.ReminderScheduler
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
import com.culturedcode.things3.domain.util.runCatchingCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
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

    private fun endOfTodayEpochMillis(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
    }

    fun observeInboxTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeInboxTasks()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeTodayDaytimeTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeTodayTasks(endOfTodayEpochMillis())
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeThisEveningTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeThisEveningTasks()
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }

    fun observeUpcomingTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeUpcomingTasks(endOfTodayEpochMillis())
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

    fun observeLogbookTasks(): Flow<List<TaskItem>> =
        database.taskDao().observeLogbookTasks()
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

    suspend fun getTasksForWidgetView(viewName: String): List<TaskItem> {
        return runCatchingCancellable {
            when (viewName.trim().uppercase(Locale.ROOT)) {
                "INBOX" -> observeInboxTasks().first()
                "UPCOMING" -> observeUpcomingTasks().first()
                "ANYTIME" -> observeAnytimeTasks().first()
                "SOMEDAY" -> observeSomedayTasks().first()
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

    fun searchTasksFts5(rawQuery: String): Flow<List<TaskItem>> {
        val sql = ThingsDatabase.buildTasksFts5SqlQuery(rawQuery)
        return database.taskDao().searchTasksFts5(sql)
            .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.distinctBy { it.id } }
            .catch { emit(emptyList()) }
    }

    fun searchProjectsFts5(rawQuery: String): Flow<List<Project>> {
        val sql = ThingsDatabase.buildProjectsFts5SqlQuery(rawQuery)
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
                            sortOrder = entity.sortOrder,
                            tagIds = decodeStringList(entity.tagIdsJson),
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt,
                            isDeleted = entity.isDeleted
                        )
                    }.getOrNull()
                }.distinctBy { it.id }
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
            sortOrder = if (sortOrder.isFinite()) sortOrder else 1000.0,
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
            spawnNextRecurrenceInstance(entity, recurrenceRule, tagIds, now)
        }

        syncRepository.triggerBackgroundSync()
        return id
    }

    private suspend fun spawnNextRecurrenceInstance(
        completedTask: TaskEntity,
        rule: RecurrenceRule,
        tagIds: List<String>,
        now: Long
    ) {
        val zone = ZoneId.systemDefault()
        val safeInterval = rule.interval.coerceIn(1, 3650).toLong()
        val baseEpoch = when (rule.mode) {
            RecurrenceMode.AFTER_COMPLETION -> now
            RecurrenceMode.FIXED_SCHEDULE -> completedTask.whenDate ?: now
        }
        val baseDate = runCatching { Instant.ofEpochMilli(baseEpoch).atZone(zone).toLocalDate() }
            .getOrElse { LocalDate.now(zone) }

        val nextDate: LocalDate = when (rule.unit) {
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

        val nextWhenEpoch = nextDate.atStartOfDay(zone).toInstant().toEpochMilli()

        if (rule.endDateEpochMillis != null && nextWhenEpoch > rule.endDateEpochMillis) {
            return
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

        val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val nextScheduleState = if (nextWhenEpoch <= todayEpoch) ScheduleState.TODAY else ScheduleState.UPCOMING

        val nextTaskId = UUID.randomUUID().toString()
        val nextEntity = completedTask.copy(
            id = nextTaskId,
            status = TaskStatus.OPEN,
            scheduleState = nextScheduleState,
            whenDate = nextWhenEpoch,
            deadline = nextDeadlineEpoch,
            reminderTime = nextReminderTime,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
            isDirty = true
        )
        database.taskDao().upsertTaskWithTags(nextEntity, tagIds)

        if (nextReminderTime != null && nextReminderTime > now) {
            reminderScheduler.scheduleExactReminder(nextTaskId, nextEntity.title, nextReminderTime)
        }

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

        if (newStatus != TaskStatus.OPEN) {
            reminderScheduler.cancelReminder(taskId)
        } else if (existing.reminderTime != null && existing.reminderTime > now) {
            reminderScheduler.scheduleExactReminder(taskId, existing.title, existing.reminderTime)
        }

        if (existing.status == TaskStatus.OPEN && newStatus == TaskStatus.COMPLETED && existing.recurrenceRuleJson != null) {
            val rule = runCatching { json.decodeFromString<RecurrenceRule>(existing.recurrenceRuleJson) }.getOrNull()
            if (rule != null) {
                val tagIds = decodeStringList(existing.tagIdsJson)
                spawnNextRecurrenceInstance(updated, rule, tagIds, now)
            }
        }

        syncRepository.triggerBackgroundSync()
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

        val misclassified = database.taskDao().getMisclassifiedAnytimeTasks()
        if (misclassified.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val todayEpoch = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfToday = endOfTodayEpochMillis()
            val fixed = misclassified.map { task ->
                val resolvedWhen = task.whenDate ?: todayEpoch
                val resolvedState = if (resolvedWhen > endOfToday) ScheduleState.UPCOMING else ScheduleState.TODAY
                task.copy(
                    whenDate = resolvedWhen,
                    scheduleState = resolvedState,
                    updatedAt = now,
                    isDirty = true
                )
            }
            database.taskDao().upsertAll(fixed)
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

    suspend fun softDeleteArea(areaId: String) {
        val existing = database.areaDao().getById(areaId) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.projectDao().clearAreaFromProjects(areaId, now)
            database.taskDao().clearAreaFromTasks(areaId, now)
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
        syncRepository.triggerBackgroundSync()
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

    suspend fun softDeleteProject(projectId: String) {
        val existing = database.projectDao().getById(projectId) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.taskDao().clearProjectFromTasks(projectId, now)
            database.projectDao().upsert(
                existing.copy(
                    isDeleted = true,
                    updatedAt = now,
                    isDirty = true
                )
            )
        }
        syncRepository.triggerBackgroundSync()
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
                sortOrder = if (sortOrder.isFinite()) sortOrder else now.toDouble(),
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
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
        database.taskDao().upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = now,
                isDirty = true
            )
        )
        syncRepository.triggerBackgroundSync()
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
        syncRepository.triggerBackgroundSync()
        return id
    }

    suspend fun softDeleteTag(tagId: String) {
        val existing = database.tagDao().getById(tagId) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.tagDao().deleteTaskTagCrossRefsForTag(tagId)
            database.tagDao().deleteProjectTagCrossRefsForTag(tagId)
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
        val trimmedTitle = title.trim().ifEmpty { "Neues Projekt" }
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
                        title = headingTitle.trim().ifEmpty { "Abschnitt ${hIndex + 1}" },
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
            val tagsStr = item.tags.filter { !it.isDeleted }.joinToString("; ") { it.title }
            val checklistStr = item.checklistItems
                .filter { !it.isDeleted }
                .sortedBy { it.sortOrder }
                .joinToString("; ") { sub -> (if (sub.isCompleted) "[x] " else "[ ] ") + sub.title }

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
        val idxTags = colIndex("tags", "tag", defaultIdx = 8)
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
                    val tagNames = rawTags.split(";", "|").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
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
                    val subParts = rawChecklist.split(";").map { it.trim() }.filter { it.isNotEmpty() }
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

internal object CsvParserUtils {
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

        // 3. DD/MM/YYYY or YYYY/MM/DD
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
                        LocalDate.of(y, p1, p0)
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
