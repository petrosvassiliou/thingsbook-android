package com.pv.thingsbook.domain.model

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
    val lastReviewedAt: Long? = null,
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

    val openTaskCount: Int
        get() = (totalTaskCount - completedTaskCount).coerceAtLeast(0)
}

data class Heading(
    val id: String,
    val projectId: String,
    val title: String,
    val endDate: Long? = null,
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

data class TaskAttachment(
    val id: String,
    val taskId: String?,
    val projectId: String?,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localRelativePath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/", ignoreCase = true) ||
            fileName.endsWith(".jpg", ignoreCase = true) ||
            fileName.endsWith(".jpeg", ignoreCase = true) ||
            fileName.endsWith(".png", ignoreCase = true) ||
            fileName.endsWith(".webp", ignoreCase = true)

    val isPdf: Boolean
        get() = mimeType.equals("application/pdf", ignoreCase = true) ||
            fileName.endsWith(".pdf", ignoreCase = true)
}

data class TaskItem(
    val id: String,
    val title: String,
    val notes: String,
    val status: TaskStatus,
    val scheduleState: ScheduleState,
    val whenDate: Long?,
    val deadline: Long?,
    val reminderTime: Long?,
    val scheduledStartMinutes: Int? = null,
    val durationMinutes: Int? = null,
    val completedAt: Long?,
    val projectId: String?,
    val headingId: String?,
    val areaId: String?,
    val recurrenceRule: RecurrenceRule?,
    val sortOrder: Double,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val attachments: List<TaskAttachment> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class DeviceCalendarInfo(
    val id: String,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val colorArgb: Int,
    val isPrimary: Boolean = false
)

data class CalendarEventItem(
    val id: String,
    val eventId: Long,
    val calendarId: String,
    val calendarDisplayName: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val colorArgb: Int,
    val startDateEpochDay: Long,
    val endDateInclusiveEpochDay: Long
)

object TaskCardInteractionRules {
    /**
     * Only auto-focus the title input when expanding a brand-new untitled task — never auto-focus or select existing titles.
     */
    fun shouldAutoFocusTitleOnExpand(title: String): Boolean {
        return title.trim().isEmpty()
    }

    /**
     * Show the 1-click "This Evening" + immediate-close pill exclusively in the Today view for open tasks not yet in This Evening.
     */
    fun shouldShowQuickThisEveningPill(
        isTodayView: Boolean,
        status: TaskStatus,
        scheduleState: ScheduleState
    ): Boolean {
        return isTodayView && status == TaskStatus.OPEN && scheduleState != ScheduleState.THIS_EVENING
    }
}

