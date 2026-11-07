package com.culturedcode.things3.domain.model

data class Area(
    val id: String,
    val title: String,
    val sortOrder: Double,
    val tagIds: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class Project(
    val id: String,
    val areaId: String?,
    val title: String,
    val notes: String,
    val status: ProjectStatus,
    val scheduleState: ScheduleState,
    val whenDate: Long?,
    val deadline: Long?,
    val completedAt: Long?,
    val sortOrder: Double,
    val tagIds: List<String>,
    val totalTaskCount: Int = 0,
    val completedTaskCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
) {
    /** Live progress fraction [0.0f .. 1.0f] for the Things 3 Pie-Chart Progress Ring */
    val progress: Float
        get() = if (totalTaskCount <= 0) 0f else (completedTaskCount.toFloat() / totalTaskCount.toFloat()).coerceIn(0f, 1f)
}

data class Heading(
    val id: String,
    val projectId: String,
    val title: String,
    val sortOrder: Double,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class ChecklistItem(
    val id: String,
    val taskId: String,
    val title: String,
    val isCompleted: Boolean,
    val sortOrder: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class Tag(
    val id: String,
    val title: String,
    val parentTagId: String?,
    val shortcut: String?,
    val sortOrder: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class TaskItem(
    val id: String,
    val title: String,
    val notes: String,
    val status: TaskStatus,
    val scheduleState: ScheduleState,
    val whenDate: Long?,
    val deadline: Long?,
    val reminderTime: Long?,
    val completedAt: Long?,
    val projectId: String?,
    val headingId: String?,
    val areaId: String?,
    val recurrenceRule: RecurrenceRule?,
    val sortOrder: Double,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)
