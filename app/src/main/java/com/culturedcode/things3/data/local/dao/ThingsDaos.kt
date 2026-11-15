package com.culturedcode.things3.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.culturedcode.things3.data.local.entity.AreaEntity
import com.culturedcode.things3.data.local.entity.AreaWithProjectsAndTasks
import com.culturedcode.things3.data.local.entity.AttachmentEntity
import com.culturedcode.things3.data.local.entity.ChecklistItemEntity
import com.culturedcode.things3.data.local.entity.HeadingEntity
import com.culturedcode.things3.data.local.entity.ProjectEntity
import com.culturedcode.things3.data.local.entity.ProjectTagCrossRef
import com.culturedcode.things3.data.local.entity.ProjectTemplateEntity
import com.culturedcode.things3.data.local.entity.ProjectWithDetails
import com.culturedcode.things3.data.local.entity.ProjectWithProgressTuple
import com.culturedcode.things3.data.local.entity.TagEntity
import com.culturedcode.things3.data.local.entity.TaskEntity
import com.culturedcode.things3.data.local.entity.TaskTagCrossRef
import com.culturedcode.things3.data.local.entity.TaskWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface AreaDao {
    @Query("SELECT * FROM areas WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    fun observeAreas(): Flow<List<AreaEntity>>

    @Query("SELECT * FROM areas WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    suspend fun getActiveAreasList(): List<AreaEntity>

    @Query("SELECT COUNT(*) FROM areas WHERE isDeleted = 0")
    suspend fun getActiveAreaCount(): Int

    @Transaction
    @Query("SELECT * FROM areas WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    fun observeAreasWithHierarchy(): Flow<List<AreaWithProjectsAndTasks>>

    @Query("SELECT * FROM areas WHERE isDirty = 1")
    suspend fun getDirtyAreas(): List<AreaEntity>

    @Query("SELECT * FROM areas WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AreaEntity?

    @Upsert
    suspend fun upsert(area: AreaEntity)

    @Upsert
    suspend fun upsertAll(areas: List<AreaEntity>)

    @Query("UPDATE areas SET isDirty = 0 WHERE id IN (:ids) AND updatedAt <= :maxUpdatedAt")
    suspend fun markSynced(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE)

    suspend fun markSyncedSafe(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE) {
        ids.chunked(500).forEach { chunk ->
            if (chunk.isNotEmpty()) markSynced(chunk, maxUpdatedAt)
        }
    }

    @Query("SELECT COUNT(*) FROM tasks WHERE areaId = :areaId AND isDeleted = 0")
    suspend fun countActiveTasksInArea(areaId: String): Int

    @Query("SELECT COUNT(*) FROM projects WHERE areaId = :areaId AND isDeleted = 0")
    suspend fun countActiveProjectsInArea(areaId: String): Int

    @Query("DELETE FROM areas WHERE id IN (:ids)")
    suspend fun hardDeleteByIds(ids: List<String>)

    @Query("DELETE FROM areas")
    suspend fun hardDeleteAll()

    @Query("UPDATE areas SET isDirty = 1 WHERE isDeleted = 0")
    suspend fun markAllActiveDirty()
}

@Dao
interface ProjectDao {
    @Query(
        """
        SELECT p.*,
            (SELECT COUNT(*) FROM tasks t WHERE t.projectId = p.id AND t.isDeleted = 0 AND t.status != 'CANCELED') AS totalTaskCount,
            (SELECT COUNT(*) FROM tasks t WHERE t.projectId = p.id AND t.isDeleted = 0 AND t.status = 'COMPLETED') AS completedTaskCount
        FROM projects p
        WHERE p.isDeleted = 0 AND p.status = 'ACTIVE'
        ORDER BY p.sortOrder ASC
        """
    )
    fun observeActiveProjectsWithProgress(): Flow<List<ProjectWithProgressTuple>>

    @Query("SELECT * FROM projects WHERE isDeleted = 0 AND status = 'ACTIVE' ORDER BY sortOrder ASC")
    suspend fun getActiveProjectsList(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    suspend fun getAllNonDeletedProjectsList(): List<ProjectEntity>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :projectId AND isDeleted = 0 LIMIT 1")
    fun observeProjectWithDetails(projectId: String): Flow<ProjectWithDetails?>

    @Query("SELECT * FROM projects WHERE isDirty = 1")
    suspend fun getDirtyProjects(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Upsert
    suspend fun upsertAll(projects: List<ProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectTagCrossRefs(refs: List<ProjectTagCrossRef>)

    @Query("DELETE FROM project_tags WHERE projectId = :projectId")
    suspend fun deleteProjectTags(projectId: String)

    @Transaction
    suspend fun upsertProjectWithTags(project: ProjectEntity, tagIds: List<String>) {
        upsert(project)
        deleteProjectTags(project.id)
        if (tagIds.isNotEmpty()) {
            insertProjectTagCrossRefs(tagIds.map { ProjectTagCrossRef(project.id, it) })
        }
    }

    @Query("UPDATE projects SET areaId = NULL, updatedAt = :now, isDirty = 1 WHERE areaId = :areaId")
    suspend fun clearAreaFromProjects(areaId: String, now: Long)

    @Query("UPDATE projects SET isDirty = 0 WHERE id IN (:ids) AND updatedAt <= :maxUpdatedAt")
    suspend fun markSynced(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE)

    suspend fun markSyncedSafe(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE) {
        ids.chunked(500).forEach { chunk ->
            if (chunk.isNotEmpty()) markSynced(chunk, maxUpdatedAt)
        }
    }

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun countActiveTasksInProject(projectId: String): Int

    @Query("DELETE FROM project_tags WHERE projectId IN (:projectIds)")
    suspend fun hardDeleteProjectTagsByIds(projectIds: List<String>)

    @Query("DELETE FROM project_tags")
    suspend fun hardDeleteAllProjectTags()

    @Query("DELETE FROM projects WHERE id IN (:ids)")
    suspend fun hardDeleteByIds(ids: List<String>)

    @Query("DELETE FROM projects")
    suspend fun hardDeleteAll()

    @Query("SELECT * FROM project_tags")
    suspend fun getAllProjectTagCrossRefs(): List<ProjectTagCrossRef>

    @Query("UPDATE projects SET isDirty = 1 WHERE isDeleted = 0")
    suspend fun markAllActiveDirty()

    @Query("SELECT * FROM projects")
    fun observeAllProjectsRaw(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE isDeleted = 1 AND (title != '' OR notes != '') ORDER BY updatedAt DESC")
    fun observeDeletedProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE isDeleted = 1 AND (title != '' OR notes != '') ORDER BY updatedAt DESC")
    suspend fun getDeletedProjectsList(): List<ProjectEntity>
}

@Dao
interface HeadingDao {
    @Query("SELECT * FROM headings WHERE projectId = :projectId AND isDeleted = 0 AND isArchived = 0 ORDER BY sortOrder ASC")
    fun observeHeadingsForProject(projectId: String): Flow<List<HeadingEntity>>

    @Query("SELECT * FROM headings WHERE isDeleted = 0 AND isArchived = 0 ORDER BY sortOrder ASC")
    fun observeAllActiveHeadings(): Flow<List<HeadingEntity>>

    @Query("SELECT * FROM headings WHERE projectId = :projectId AND isDeleted = 0 AND isArchived = 0 ORDER BY sortOrder ASC")
    suspend fun getHeadingsForProjectList(projectId: String): List<HeadingEntity>

    @Query("SELECT * FROM headings WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    suspend fun getAllNonDeletedHeadingsList(): List<HeadingEntity>

    @Query("SELECT * FROM headings WHERE isDirty = 1")
    suspend fun getDirtyHeadings(): List<HeadingEntity>

    @Query("SELECT * FROM headings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HeadingEntity?

    @Upsert
    suspend fun upsert(heading: HeadingEntity)

    @Upsert
    suspend fun upsertAll(headings: List<HeadingEntity>)

    @Query("UPDATE headings SET isDirty = 0 WHERE id IN (:ids) AND updatedAt <= :maxUpdatedAt")
    suspend fun markSynced(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE)

    suspend fun markSyncedSafe(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE) {
        ids.chunked(500).forEach { chunk ->
            if (chunk.isNotEmpty()) markSynced(chunk, maxUpdatedAt)
        }
    }

    @Query("UPDATE headings SET isDeleted = 1, updatedAt = :now, isDirty = 1 WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun softDeleteHeadingsForProject(projectId: String, now: Long)

    @Query("UPDATE headings SET isDeleted = 0, updatedAt = :now, isDirty = 1 WHERE projectId = :projectId AND isDeleted = 1 AND title != ''")
    suspend fun restoreHeadingsForProject(projectId: String, now: Long)

    @Query("UPDATE headings SET isDeleted = 1, isDirty = 1, title = '', updatedAt = :now WHERE projectId = :projectId")
    suspend fun compactTombstonesByProjectId(projectId: String, now: Long)

    @Query("DELETE FROM headings WHERE projectId IN (:projectIds)")
    suspend fun hardDeleteHeadingsForProjects(projectIds: List<String>)

    @Query("SELECT COUNT(*) FROM tasks WHERE headingId = :headingId AND isDeleted = 0")
    suspend fun countActiveTasksInHeading(headingId: String): Int

    @Query("DELETE FROM headings WHERE id IN (:ids)")
    suspend fun hardDeleteByIds(ids: List<String>)

    @Query("DELETE FROM headings")
    suspend fun hardDeleteAll()

    @Query("UPDATE headings SET isDirty = 1 WHERE isDeleted = 0")
    suspend fun markAllActiveDirty()
}

@Dao
interface TaskDao {
    /**
     * Inbox Smart View: Unsorted tasks in INBOX with no Project or Area assignment.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND scheduleState = 'INBOX'
          AND projectId IS NULL
          AND areaId IS NULL
        ORDER BY sortOrder ASC
        """
    )
    fun observeInboxTasks(): Flow<List<TaskWithDetails>>

    /**
     * Today Smart View (Daytime section): Open tasks scheduled for TODAY, or with whenDate <= endOfToday,
     * or with deadline <= endOfToday, excluding THIS_EVENING and tasks in completed/deleted projects.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND scheduleState != 'THIS_EVENING'
          AND (projectId IS NULL OR NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = tasks.projectId AND (p.isDeleted = 1 OR p.status != 'ACTIVE')))
          AND (
              scheduleState = 'TODAY'
              OR (whenDate IS NOT NULL AND whenDate <= :endOfTodayMillis)
              OR (deadline IS NOT NULL AND deadline <= :endOfTodayMillis)
          )
        ORDER BY sortOrder ASC
        """
    )
    fun observeTodayTasks(endOfTodayMillis: Long): Flow<List<TaskWithDetails>>

    /**
     * Today Smart View (This Evening / Heute Abend / Stasera / Ce soir / Esta noche section).
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND scheduleState = 'THIS_EVENING'
          AND (projectId IS NULL OR NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = tasks.projectId AND (p.isDeleted = 1 OR p.status != 'ACTIVE')))
        ORDER BY sortOrder ASC
        """
    )
    fun observeThisEveningTasks(): Flow<List<TaskWithDetails>>

    /**
     * Upcoming Smart View: Chronological timeline of future scheduled tasks, deadlines, and recurring templates,
     * excluding tasks already active in Today or This Evening.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND scheduleState NOT IN ('TODAY', 'THIS_EVENING')
          AND (whenDate IS NULL OR whenDate > :endOfTodayMillis)
          AND (deadline IS NULL OR deadline > :endOfTodayMillis)
          AND (projectId IS NULL OR NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = tasks.projectId AND (p.isDeleted = 1 OR p.status != 'ACTIVE')))
          AND (
              scheduleState = 'UPCOMING'
              OR (whenDate IS NOT NULL AND whenDate > :endOfTodayMillis)
              OR (deadline IS NOT NULL AND deadline > :endOfTodayMillis)
              OR recurrenceRuleJson IS NOT NULL
          )
        ORDER BY COALESCE(whenDate, deadline, 9223372036854775807) ASC, sortOrder ASC
        """
    )
    fun observeUpcomingTasks(endOfTodayMillis: Long): Flow<List<TaskWithDetails>>

    /**
     * Anytime Smart View: Active tasks without a scheduled date or recurrence rule, excluding SOMEDAY, INBOX, TODAY, and UPCOMING.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND scheduleState = 'ANYTIME'
          AND whenDate IS NULL
          AND recurrenceRuleJson IS NULL
          AND (projectId IS NULL OR NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = tasks.projectId AND (p.isDeleted = 1 OR p.status != 'ACTIVE')))
        ORDER BY sortOrder ASC
        """
    )
    fun observeAnytimeTasks(): Flow<List<TaskWithDetails>>

    /**
     * Someday Smart View: Deferred tasks.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND scheduleState = 'SOMEDAY'
          AND (projectId IS NULL OR NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = tasks.projectId AND (p.isDeleted = 1 OR p.status != 'ACTIVE')))
        ORDER BY sortOrder ASC
        """
    )
    fun observeSomedayTasks(): Flow<List<TaskWithDetails>>

    /**
     * Logbook Smart View: Completed and Canceled tasks ordered chronologically by completion date descending.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status IN ('COMPLETED', 'CANCELED')
        ORDER BY COALESCE(completedAt, updatedAt) DESC
        """
    )
    fun observeLogbookTasks(): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND isDeleted = 0 AND status = 'OPEN' ORDER BY sortOrder ASC")
    fun observeTasksForProject(projectId: String): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE areaId = :areaId AND projectId IS NULL AND isDeleted = 0 AND status = 'OPEN' ORDER BY sortOrder ASC")
    fun observeTasksForArea(areaId: String): Flow<List<TaskWithDetails>>

    /**
     * Full-Text Search (SQLite FTS5 with unicode61 tokenizer) for Quick Find.
     */
    @Transaction
    @RawQuery(observedEntities = [TaskEntity::class, ChecklistItemEntity::class, TagEntity::class, TaskTagCrossRef::class, AttachmentEntity::class])
    fun searchTasksFts5(query: SupportSQLiteQuery): Flow<List<TaskWithDetails>>

    @RawQuery(observedEntities = [ProjectEntity::class])
    fun searchProjectsFts5(query: SupportSQLiteQuery): Flow<List<ProjectEntity>>

    @RawQuery(observedEntities = [AreaEntity::class])
    fun searchAreasFts5(query: SupportSQLiteQuery): Flow<List<AreaEntity>>

    @RawQuery(observedEntities = [TagEntity::class])
    fun searchTagsFts5(query: SupportSQLiteQuery): Flow<List<TagEntity>>

    @Query("SELECT * FROM tasks WHERE isDirty = 1")
    suspend fun getDirtyTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTotalTaskCount(): Int

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskTagCrossRefs(refs: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    suspend fun deleteTaskTags(taskId: String)

    @Transaction
    suspend fun upsertTaskWithTags(task: TaskEntity, tagIds: List<String>) {
        upsert(task)
        deleteTaskTags(task.id)
        if (tagIds.isNotEmpty()) {
            insertTaskTagCrossRefs(tagIds.map { TaskTagCrossRef(task.id, it) })
        }
    }

    @Query("UPDATE tasks SET areaId = NULL, updatedAt = :now, isDirty = 1 WHERE areaId = :areaId")
    suspend fun clearAreaFromTasks(areaId: String, now: Long)

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND isDeleted = 0 AND status = 'OPEN'")
    suspend fun getOpenTasksByProject(projectId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId")
    suspend fun getTasksByProjectAll(projectId: String): List<TaskEntity>

    @Query("UPDATE tasks SET projectId = NULL, headingId = NULL, updatedAt = :now, isDirty = 1 WHERE projectId = :projectId")
    suspend fun clearProjectFromTasks(projectId: String, now: Long)

    @Query("UPDATE tasks SET projectId = NULL, headingId = NULL, updatedAt = :now, isDirty = 1 WHERE projectId = :projectId AND isDeleted = 0 AND status = 'OPEN'")
    suspend fun clearProjectFromOpenTasks(projectId: String, now: Long)

    @Query("UPDATE tasks SET headingId = NULL, updatedAt = :now, isDirty = 1 WHERE headingId = :headingId")
    suspend fun clearHeadingFromTasks(headingId: String, now: Long)

    @Query("UPDATE tasks SET isDirty = 0 WHERE id IN (:ids) AND updatedAt <= :maxUpdatedAt")
    suspend fun markSynced(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE)

    suspend fun markSyncedSafe(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE) {
        ids.chunked(500).forEach { chunk ->
            if (chunk.isNotEmpty()) markSynced(chunk, maxUpdatedAt)
        }
    }

    @Query(
        """
        UPDATE tasks
        SET scheduleState = 'TODAY', updatedAt = :now, isDirty = 1
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND scheduleState = 'UPCOMING'
          AND whenDate IS NOT NULL
          AND whenDate <= :endOfTodayMillis
        """
    )
    suspend fun rolloverDueUpcomingTasksToToday(endOfTodayMillis: Long, now: Long): Int

    @Query(
        """
        UPDATE tasks
        SET scheduleState = 'TODAY', whenDate = :startOfTodayMillis, updatedAt = :now, isDirty = 1
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND scheduleState = 'THIS_EVENING'
          AND (
              (whenDate IS NOT NULL AND whenDate < :startOfTodayMillis)
              OR (whenDate IS NULL AND updatedAt < :startOfTodayMillis)
          )
        """
    )
    suspend fun rolloverYesterdayEveningTasksToToday(startOfTodayMillis: Long, now: Long): Int

    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND (projectId IS NULL OR NOT EXISTS (SELECT 1 FROM projects p WHERE p.id = tasks.projectId AND (p.isDeleted = 1 OR p.status != 'ACTIVE')))
          AND (
              scheduleState IN ('TODAY', 'THIS_EVENING')
              OR (whenDate IS NOT NULL AND whenDate <= :endOfTodayMillis)
              OR (deadline IS NOT NULL AND deadline <= :endOfTodayMillis)
          )
        ORDER BY sortOrder ASC
        """
    )
    suspend fun getOpenActiveTasksForToday(endOfTodayMillis: Long): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND reminderTime IS NOT NULL
          AND reminderTime > :nowMillis
        """
    )
    suspend fun getOpenTasksWithFutureReminders(nowMillis: Long): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND scheduleState = 'ANYTIME'
          AND (whenDate IS NOT NULL OR recurrenceRuleJson IS NOT NULL)
        """
    )
    suspend fun getMisclassifiedAnytimeTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    fun observeAllTasksRaw(): Flow<List<TaskEntity>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    suspend fun getAllActiveTasksWithDetails(): List<TaskWithDetails>

    @Query("SELECT COUNT(*) FROM tasks WHERE id IN (:sampleIds) AND isDeleted = 0")
    fun observeActiveSampleTaskCount(sampleIds: List<String>): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE id IN (:sampleIds) AND isDeleted = 0")
    suspend fun countActiveSampleTasks(sampleIds: List<String>): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE id NOT IN (:sampleIds) AND isDeleted = 0")
    suspend fun countActiveNonSampleTasks(sampleIds: List<String>): Int

    @Query("DELETE FROM task_tags WHERE taskId IN (:taskIds)")
    suspend fun hardDeleteTaskTagsByTaskIds(taskIds: List<String>)

    @Query("DELETE FROM task_tags")
    suspend fun hardDeleteAllTaskTags()

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun hardDeleteByIds(ids: List<String>)

    @Query("DELETE FROM tasks")
    suspend fun hardDeleteAll()

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    suspend fun getAllNonDeletedTasksList(): List<TaskEntity>

    @Query("SELECT * FROM task_tags")
    suspend fun getAllTaskTagCrossRefs(): List<TaskTagCrossRef>

    @Query("UPDATE tasks SET isDirty = 1 WHERE isDeleted = 0")
    suspend fun markAllActiveDirty()

    @Transaction
    @Query("SELECT * FROM tasks WHERE isDeleted = 1 AND (title != '' OR notes != '') ORDER BY updatedAt DESC")
    fun observeDeletedTasks(): Flow<List<TaskWithDetails>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 1 AND (title != '' OR notes != '') ORDER BY updatedAt DESC")
    suspend fun getDeletedTasksList(): List<TaskEntity>
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE taskId = :taskId AND isDeleted = 0 ORDER BY sortOrder ASC")
    fun observeForTask(taskId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE taskId = :taskId AND isDeleted = 0 ORDER BY sortOrder ASC")
    suspend fun getForTaskSync(taskId: String): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    suspend fun getAllNonDeletedChecklistItemsList(): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE isDirty = 1")
    suspend fun getDirtyChecklistItems(): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChecklistItemEntity?

    @Upsert
    suspend fun upsert(item: ChecklistItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<ChecklistItemEntity>)

    @Query("UPDATE checklist_items SET isDirty = 0 WHERE id IN (:ids) AND updatedAt <= :maxUpdatedAt")
    suspend fun markSynced(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE)

    suspend fun markSyncedSafe(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE) {
        ids.chunked(500).forEach { chunk ->
            if (chunk.isNotEmpty()) markSynced(chunk, maxUpdatedAt)
        }
    }

    @Query("SELECT id FROM checklist_items WHERE taskId = :taskId AND isDeleted = 0")
    suspend fun getActiveChecklistIdsByTaskId(taskId: String): List<String>

    @Query("UPDATE checklist_items SET isDeleted = 1, isDirty = 1, updatedAt = :now WHERE taskId = :taskId AND isDeleted = 0")
    suspend fun softDeleteByTaskId(taskId: String, now: Long)

    @Query("UPDATE checklist_items SET isDeleted = 0, isDirty = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun restoreChecklistItemsByIds(ids: List<String>, now: Long)

    @Query("UPDATE checklist_items SET isDeleted = 1, isDirty = 1, title = '', updatedAt = :now WHERE taskId = :taskId")
    suspend fun compactTombstonesByTaskId(taskId: String, now: Long)

    @Query("DELETE FROM checklist_items WHERE taskId IN (:taskIds) OR id IN (:itemIds)")
    suspend fun hardDeleteForSampleTasks(taskIds: List<String>, itemIds: List<String>)

    @Query("DELETE FROM checklist_items WHERE taskId IN (:taskIds)")
    suspend fun hardDeleteByTaskIds(taskIds: List<String>)

    @Query("DELETE FROM checklist_items")
    suspend fun hardDeleteAll()

    @Query("UPDATE checklist_items SET isDirty = 1 WHERE isDeleted = 0")
    suspend fun markAllActiveDirty()
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    suspend fun getAllNonDeletedTagsList(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE isDirty = 1")
    suspend fun getDirtyTags(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TagEntity?

    @Upsert
    suspend fun upsert(tag: TagEntity)

    @Upsert
    suspend fun upsertAll(tags: List<TagEntity>)

    @Query("DELETE FROM task_tags WHERE tagId = :tagId")
    suspend fun deleteTaskTagCrossRefsForTag(tagId: String)

    @Query("DELETE FROM project_tags WHERE tagId = :tagId")
    suspend fun deleteProjectTagCrossRefsForTag(tagId: String)

    @Query("UPDATE tags SET isDirty = 0 WHERE id IN (:ids) AND updatedAt <= :maxUpdatedAt")
    suspend fun markSynced(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE)

    suspend fun markSyncedSafe(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE) {
        ids.chunked(500).forEach { chunk ->
            if (chunk.isNotEmpty()) markSynced(chunk, maxUpdatedAt)
        }
    }

    @Query("SELECT COUNT(*) FROM task_tags WHERE tagId = :tagId")
    suspend fun countTasksUsingTag(tagId: String): Int

    @Query("SELECT COUNT(*) FROM project_tags WHERE tagId = :tagId")
    suspend fun countProjectsUsingTag(tagId: String): Int

    @Query("DELETE FROM tags WHERE id IN (:ids)")
    suspend fun hardDeleteByIds(ids: List<String>)

    @Query("DELETE FROM tags")
    suspend fun hardDeleteAll()

    @Query("UPDATE tags SET isDirty = 1 WHERE isDeleted = 0")
    suspend fun markAllActiveDirty()
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun observeAllActiveAttachments(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE taskId = :taskId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun observeForTask(taskId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE projectId = :projectId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun observeForProject(projectId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE taskId = :taskId AND isDeleted = 0 ORDER BY createdAt ASC")
    suspend fun getForTaskSync(taskId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE projectId = :projectId AND isDeleted = 0 ORDER BY createdAt ASC")
    suspend fun getForProjectSync(projectId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE taskId = :taskId")
    suspend fun getAllByTaskId(taskId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE projectId = :projectId")
    suspend fun getAllByProjectId(projectId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE isDeleted = 0 ORDER BY createdAt ASC")
    suspend fun getAllNonDeletedAttachmentsList(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE isDirty = 1")
    suspend fun getDirtyAttachments(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AttachmentEntity?

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Upsert
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("UPDATE attachments SET isDirty = 0 WHERE id IN (:ids) AND updatedAt <= :maxUpdatedAt")
    suspend fun markSynced(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE)

    suspend fun markSyncedSafe(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE) {
        ids.chunked(500).forEach { chunk ->
            if (chunk.isNotEmpty()) markSynced(chunk, maxUpdatedAt)
        }
    }

    @Query("UPDATE attachments SET isDeleted = 1, updatedAt = :now, isDirty = 1 WHERE id = :id")
    suspend fun softDeleteById(id: String, now: Long)

    @Query("UPDATE attachments SET isDeleted = 1, isDirty = 1, updatedAt = :now WHERE taskId = :taskId AND isDeleted = 0")
    suspend fun softDeleteByTaskId(taskId: String, now: Long)

    @Query("UPDATE attachments SET isDeleted = 1, isDirty = 1, updatedAt = :now WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun softDeleteByProjectId(projectId: String, now: Long)

    @Query("UPDATE attachments SET isDeleted = 1, isDirty = 1, fileName = '', sizeBytes = 0, localRelativePath = '', updatedAt = :now WHERE id = :id")
    suspend fun compactTombstoneById(id: String, now: Long)

    @Query("DELETE FROM attachments WHERE taskId IN (:taskIds)")
    suspend fun hardDeleteByTaskIds(taskIds: List<String>)

    @Query("DELETE FROM attachments WHERE projectId IN (:projectIds)")
    suspend fun hardDeleteByProjectIds(projectIds: List<String>)

    @Query("DELETE FROM attachments WHERE id IN (:ids)")
    suspend fun hardDeleteByIds(ids: List<String>)

    @Query("DELETE FROM attachments")
    suspend fun hardDeleteAll()

    @Query("UPDATE attachments SET isDirty = 1 WHERE isDeleted = 0")
    suspend fun markAllActiveDirty()
}

@Dao
interface ProjectTemplateDao {
    @Query("SELECT * FROM project_templates WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt DESC")
    fun observeActiveTemplates(): Flow<List<ProjectTemplateEntity>>

    @Query("SELECT * FROM project_templates WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getAllNonDeletedTemplatesList(): List<ProjectTemplateEntity>

    @Query("SELECT * FROM project_templates WHERE isDirty = 1")
    suspend fun getDirtyTemplates(): List<ProjectTemplateEntity>

    @Query("SELECT * FROM project_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectTemplateEntity?

    @Upsert
    suspend fun upsert(template: ProjectTemplateEntity)

    @Upsert
    suspend fun upsertAll(templates: List<ProjectTemplateEntity>)

    @Query("UPDATE project_templates SET isDirty = 0 WHERE id IN (:ids) AND updatedAt <= :maxUpdatedAt")
    suspend fun markSynced(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE)

    suspend fun markSyncedSafe(ids: List<String>, maxUpdatedAt: Long = Long.MAX_VALUE) {
        ids.chunked(500).forEach { chunk ->
            if (chunk.isNotEmpty()) markSynced(chunk, maxUpdatedAt)
        }
    }

    @Query("UPDATE project_templates SET isDeleted = 1, title = '', notes = '', blueprintJson = '{}', tagIdsJson = '[]', defaultAreaId = NULL, updatedAt = :now, isDirty = 1 WHERE id = :id")
    suspend fun softDeleteById(id: String, now: Long)

    @Query("UPDATE project_templates SET defaultAreaId = NULL, updatedAt = :now, isDirty = 1 WHERE defaultAreaId = :areaId")
    suspend fun clearAreaFromTemplates(areaId: String, now: Long)

    @Query("DELETE FROM project_templates WHERE id IN (:ids)")
    suspend fun hardDeleteByIds(ids: List<String>)

    @Query("DELETE FROM project_templates")
    suspend fun hardDeleteAll()

    @Query("UPDATE project_templates SET isDirty = 1 WHERE isDeleted = 0")
    suspend fun markAllActiveDirty()
}


