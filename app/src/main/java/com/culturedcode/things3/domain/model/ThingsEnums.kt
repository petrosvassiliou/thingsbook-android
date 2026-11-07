package com.culturedcode.things3.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ScheduleState {
    INBOX,
    TODAY,
    THIS_EVENING,
    UPCOMING,
    ANYTIME,
    SOMEDAY
}

@Serializable
enum class TaskStatus {
    OPEN,
    COMPLETED,
    CANCELED
}

@Serializable
enum class ProjectStatus {
    ACTIVE,
    COMPLETED,
    CANCELED
}

@Serializable
enum class RecurrenceMode {
    FIXED_SCHEDULE,
    AFTER_COMPLETION
}

@Serializable
enum class RecurrenceUnit {
    DAYS,
    WEEKS,
    MONTHS,
    YEARS
}

@Serializable
data class RecurrenceRule(
    val mode: RecurrenceMode = RecurrenceMode.FIXED_SCHEDULE,
    val interval: Int = 1,
    val unit: RecurrenceUnit = RecurrenceUnit.DAYS,
    val daysOfWeek: List<Int> = emptyList() // ISO-8601 1 (Mon) .. 7 (Sun)
)
