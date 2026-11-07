package com.culturedcode.things3.data.remote.dto

import com.culturedcode.things3.data.local.entity.AreaEntity
import com.culturedcode.things3.data.local.entity.ChecklistItemEntity
import com.culturedcode.things3.data.local.entity.HeadingEntity
import com.culturedcode.things3.data.local.entity.ProjectEntity
import com.culturedcode.things3.data.local.entity.TagEntity
import com.culturedcode.things3.data.local.entity.TaskEntity
import com.culturedcode.things3.domain.model.ProjectStatus
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.TaskStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val firestoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class CloudUserAuthEnvelope(
    val userId: String = "",
    val email: String = "",
    val passwordVerifierHash: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class CloudDeltaBatchPayload(
    val userId: String = "",
    val deviceId: String = "",
    val batchTimestamp: Long = 0L,
    val tags: List<TagFirestoreDto> = emptyList(),
    val areas: List<AreaFirestoreDto> = emptyList(),
    val projects: List<ProjectFirestoreDto> = emptyList(),
    val headings: List<HeadingFirestoreDto> = emptyList(),
    val tasks: List<TaskFirestoreDto> = emptyList(),
    val checklistItems: List<ChecklistItemFirestoreDto> = emptyList()
)

@Serializable
data class AreaFirestoreDto(
    val id: String = "",
    val title: String = "",
    val sortOrder: Double = 0.0,
    val tagIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toEntity(): AreaEntity = AreaEntity(
        id = id,
        title = title,
        sortOrder = sortOrder,
        tagIdsJson = firestoreJson.encodeToString(tagIds),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    companion object {
        fun fromEntity(entity: AreaEntity): AreaFirestoreDto = AreaFirestoreDto(
            id = entity.id,
            title = entity.title,
            sortOrder = entity.sortOrder,
            tagIds = runCatching { firestoreJson.decodeFromString<List<String>>(entity.tagIdsJson) }.getOrDefault(emptyList()),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )
    }
}

@Serializable
data class ProjectFirestoreDto(
    val id: String = "",
    val areaId: String? = null,
    val title: String = "",
    val notes: String = "",
    val status: String = ProjectStatus.ACTIVE.name,
    val scheduleState: String = ScheduleState.ANYTIME.name,
    val whenDate: Long? = null,
    val deadline: Long? = null,
    val completedAt: Long? = null,
    val sortOrder: Double = 0.0,
    val tagIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toEntity(): ProjectEntity = ProjectEntity(
        id = id,
        areaId = areaId,
        title = title,
        notes = notes,
        status = runCatching { ProjectStatus.valueOf(status) }.getOrDefault(ProjectStatus.ACTIVE),
        scheduleState = runCatching { ScheduleState.valueOf(scheduleState) }.getOrDefault(ScheduleState.ANYTIME),
        whenDate = whenDate,
        deadline = deadline,
        completedAt = completedAt,
        sortOrder = sortOrder,
        tagIdsJson = firestoreJson.encodeToString(tagIds),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    companion object {
        fun fromEntity(entity: ProjectEntity): ProjectFirestoreDto = ProjectFirestoreDto(
            id = entity.id,
            areaId = entity.areaId,
            title = entity.title,
            notes = entity.notes,
            status = entity.status.name,
            scheduleState = entity.scheduleState.name,
            whenDate = entity.whenDate,
            deadline = entity.deadline,
            completedAt = entity.completedAt,
            sortOrder = entity.sortOrder,
            tagIds = runCatching { firestoreJson.decodeFromString<List<String>>(entity.tagIdsJson) }.getOrDefault(emptyList()),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )
    }
}

@Serializable
data class HeadingFirestoreDto(
    val id: String = "",
    val projectId: String = "",
    val title: String = "",
    val sortOrder: Double = 0.0,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toEntity(): HeadingEntity = HeadingEntity(
        id = id,
        projectId = projectId,
        title = title,
        sortOrder = sortOrder,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    companion object {
        fun fromEntity(entity: HeadingEntity): HeadingFirestoreDto = HeadingFirestoreDto(
            id = entity.id,
            projectId = entity.projectId,
            title = entity.title,
            sortOrder = entity.sortOrder,
            isArchived = entity.isArchived,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )
    }
}

@Serializable
data class TaskFirestoreDto(
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    val status: String = TaskStatus.OPEN.name,
    val scheduleState: String = ScheduleState.INBOX.name,
    val whenDate: Long? = null,
    val deadline: Long? = null,
    val reminderTime: Long? = null,
    val completedAt: Long? = null,
    val projectId: String? = null,
    val headingId: String? = null,
    val areaId: String? = null,
    val recurrenceRule: String? = null,
    val sortOrder: Double = 0.0,
    val tagIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toEntity(): TaskEntity = TaskEntity(
        id = id,
        title = title,
        notes = notes,
        status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.OPEN),
        scheduleState = runCatching { ScheduleState.valueOf(scheduleState) }.getOrDefault(ScheduleState.INBOX),
        whenDate = whenDate,
        deadline = deadline,
        reminderTime = reminderTime,
        completedAt = completedAt,
        projectId = projectId,
        headingId = headingId,
        areaId = areaId,
        recurrenceRuleJson = recurrenceRule,
        sortOrder = sortOrder,
        tagIdsJson = firestoreJson.encodeToString(tagIds),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    companion object {
        fun fromEntity(entity: TaskEntity): TaskFirestoreDto = TaskFirestoreDto(
            id = entity.id,
            title = entity.title,
            notes = entity.notes,
            status = entity.status.name,
            scheduleState = entity.scheduleState.name,
            whenDate = entity.whenDate,
            deadline = entity.deadline,
            reminderTime = entity.reminderTime,
            completedAt = entity.completedAt,
            projectId = entity.projectId,
            headingId = entity.headingId,
            areaId = entity.areaId,
            recurrenceRule = entity.recurrenceRuleJson,
            sortOrder = entity.sortOrder,
            tagIds = runCatching { firestoreJson.decodeFromString<List<String>>(entity.tagIdsJson) }.getOrDefault(emptyList()),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )
    }
}

@Serializable
data class ChecklistItemFirestoreDto(
    val id: String = "",
    val taskId: String = "",
    val title: String = "",
    val isCompleted: Boolean = false,
    val sortOrder: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toEntity(): ChecklistItemEntity = ChecklistItemEntity(
        id = id,
        taskId = taskId,
        title = title,
        isCompleted = isCompleted,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    companion object {
        fun fromEntity(entity: ChecklistItemEntity): ChecklistItemFirestoreDto = ChecklistItemFirestoreDto(
            id = entity.id,
            taskId = entity.taskId,
            title = entity.title,
            isCompleted = entity.isCompleted,
            sortOrder = entity.sortOrder,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )
    }
}

@Serializable
data class TagFirestoreDto(
    val id: String = "",
    val title: String = "",
    val parentTagId: String? = null,
    val shortcut: String? = null,
    val sortOrder: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toEntity(): TagEntity = TagEntity(
        id = id,
        title = title,
        parentTagId = parentTagId,
        shortcut = shortcut,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    companion object {
        fun fromEntity(entity: TagEntity): TagFirestoreDto = TagFirestoreDto(
            id = entity.id,
            title = entity.title,
            parentTagId = entity.parentTagId,
            shortcut = entity.shortcut,
            sortOrder = entity.sortOrder,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )
    }
}
