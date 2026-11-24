package com.pv.thingsbook.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pv.thingsbook.domain.model.ProjectStatus
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.TaskStatus

@Entity(
    tableName = "areas",
    indices = [
        Index(value = ["sortOrder"]),
        Index(value = ["updatedAt"]),
        Index(value = ["isDirty"])
    ]
)
data class AreaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sortOrder: Double,
    val tagIdsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true
)

@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["areaId"]),
        Index(value = ["scheduleState"]),
        Index(value = ["status"]),
        Index(value = ["sortOrder"]),
        Index(value = ["updatedAt"]),
        Index(value = ["isDirty"])
    ]
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val areaId: String?,
    val title: String,
    val notes: String = "",
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val scheduleState: ScheduleState = ScheduleState.ANYTIME,
    val whenDate: Long? = null,
    val deadline: Long? = null,
    val completedAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val sortOrder: Double,
    val tagIdsJson: String = "[]",
    val workspaceId: String? = null,
    val viewMode: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true
)

@Entity(
    tableName = "headings",
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["sortOrder"]),
        Index(value = ["updatedAt"]),
        Index(value = ["isDirty"])
    ]
)
data class HeadingEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val sortOrder: Double,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true
)

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["scheduleState"]),
        Index(value = ["status"]),
        Index(value = ["projectId"]),
        Index(value = ["headingId"]),
        Index(value = ["areaId"]),
        Index(value = ["whenDate"]),
        Index(value = ["deadline"]),
        Index(value = ["sortOrder"]),
        Index(value = ["updatedAt"]),
        Index(value = ["isDirty"])
    ]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String = "",
    val status: TaskStatus = TaskStatus.OPEN,
    val scheduleState: ScheduleState = ScheduleState.INBOX,
    val whenDate: Long? = null,
    val deadline: Long? = null,
    val reminderTime: Long? = null,
    val scheduledStartMinutes: Int? = null,
    val durationMinutes: Int? = null,
    val actualMinutes: Int? = null,
    val completedAt: Long? = null,
    val projectId: String? = null,
    val headingId: String? = null,
    val areaId: String? = null,
    val recurrenceRuleJson: String? = null,
    val sortOrder: Double,
    val isPinned: Boolean = false,
    val isMilestone: Boolean = false,
    val isInProgress: Boolean = false,
    val effortLevel: String? = null,
    val blockedByJson: String = "[]",
    val workspaceId: String? = null,
    val assigneeId: String? = null,
    val tagIdsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true
)

@Entity(
    tableName = "checklist_items",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["sortOrder"]),
        Index(value = ["updatedAt"]),
        Index(value = ["isDirty"])
    ]
)
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val isCompleted: Boolean = false,
    val sortOrder: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true
)

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["parentTagId"]),
        Index(value = ["sortOrder"]),
        Index(value = ["updatedAt"]),
        Index(value = ["isDirty"])
    ]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val title: String,
    val parentTagId: String? = null,
    val shortcut: String? = null,
    val sortOrder: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true
)

@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["tagId"])
    ]
)
data class TaskTagCrossRef(
    val taskId: String,
    val tagId: String
)

@Entity(
    tableName = "project_tags",
    primaryKeys = ["projectId", "tagId"],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["tagId"])
    ]
)
data class ProjectTagCrossRef(
    val projectId: String,
    val tagId: String
)

@Entity(
    tableName = "attachments",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["projectId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["isDirty"])
    ]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val taskId: String? = null,
    val projectId: String? = null,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localRelativePath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true
)

@Entity(
    tableName = "project_templates",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["isDirty"])
    ]
)
data class ProjectTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String = "",
    val defaultAreaId: String? = null,
    val tagIdsJson: String = "[]",
    val blueprintJson: String = "{}",
    val sortOrder: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val isDirty: Boolean = true
)


