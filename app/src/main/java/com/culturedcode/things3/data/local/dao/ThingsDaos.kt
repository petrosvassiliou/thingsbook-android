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
import com.culturedcode.things3.data.local.entity.ChecklistItemEntity
import com.culturedcode.things3.data.local.entity.HeadingEntity
import com.culturedcode.things3.data.local.entity.ProjectEntity
import com.culturedcode.things3.data.local.entity.ProjectTagCrossRef
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

    @Query("UPDATE areas SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
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

    @Query("UPDATE projects SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}

@Dao
interface HeadingDao {
    @Query("SELECT * FROM headings WHERE projectId = :projectId AND isDeleted = 0 AND isArchived = 0 ORDER BY sortOrder ASC")
    fun observeHeadingsForProject(projectId: String): Flow<List<HeadingEntity>>

    @Query("SELECT * FROM headings WHERE isDirty = 1")
    suspend fun getDirtyHeadings(): List<HeadingEntity>

    @Query("SELECT * FROM headings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HeadingEntity?

    @Upsert
    suspend fun upsert(heading: HeadingEntity)

    @Upsert
    suspend fun upsertAll(headings: List<HeadingEntity>)

    @Query("UPDATE headings SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
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
     * or with deadline <= endOfToday, excluding THIS_EVENING.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND scheduleState != 'THIS_EVENING'
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
        ORDER BY sortOrder ASC
        """
    )
    fun observeThisEveningTasks(): Flow<List<TaskWithDetails>>

    /**
     * Upcoming Smart View: Chronological timeline of future scheduled tasks, deadlines, and recurring templates.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
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
     * Anytime Smart View: Active tasks without a future start date, excluding SOMEDAY and INBOX.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND status = 'OPEN'
          AND scheduleState IN ('ANYTIME', 'TODAY', 'THIS_EVENING')
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
    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND isDeleted = 0 ORDER BY sortOrder ASC")
    fun observeTasksForProject(projectId: String): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE areaId = :areaId AND projectId IS NULL AND isDeleted = 0 ORDER BY sortOrder ASC")
    fun observeTasksForArea(areaId: String): Flow<List<TaskWithDetails>>

    /**
     * Full-Text Search (SQLite FTS5 with unicode61 tokenizer) for Quick Find.
     */
    @Transaction
    @RawQuery(observedEntities = [TaskEntity::class, ChecklistItemEntity::class, TagEntity::class])
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

    @Query("UPDATE tasks SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE taskId = :taskId AND isDeleted = 0 ORDER BY sortOrder ASC")
    fun observeForTask(taskId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE isDirty = 1")
    suspend fun getDirtyChecklistItems(): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChecklistItemEntity?

    @Upsert
    suspend fun upsert(item: ChecklistItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<ChecklistItemEntity>)

    @Query("UPDATE checklist_items SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE isDeleted = 0 ORDER BY sortOrder ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE isDirty = 1")
    suspend fun getDirtyTags(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TagEntity?

    @Upsert
    suspend fun upsert(tag: TagEntity)

    @Upsert
    suspend fun upsertAll(tags: List<TagEntity>)

    @Query("UPDATE tags SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
