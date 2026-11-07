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
    val daysOfWeek: List<Int> = emptyList(), // ISO-8601 1 (Mon) .. 7 (Sun)
    val dayOfMonth: Int? = null, // 1..31 or -1 for last day of month
    val endDateEpochMillis: Long? = null // Optional end date
) {
    fun formatShortLabel(): String {
        val safeInterval = interval.coerceAtLeast(1)
        val unitLabel = when (unit) {
            RecurrenceUnit.DAYS -> if (safeInterval == 1) "Täglich" else "Alle $safeInterval Tg."
            RecurrenceUnit.WEEKS -> {
                val daysStr = if (daysOfWeek.isNotEmpty()) {
                    val names = mapOf(1 to "Mo", 2 to "Di", 3 to "Mi", 4 to "Do", 5 to "Fr", 6 to "Sa", 7 to "So")
                    " (" + daysOfWeek.sorted().mapNotNull { names[it] }.joinToString(",") + ")"
                } else ""
                if (safeInterval == 1) "Wöchentlich$daysStr" else "Alle $safeInterval Wo.$daysStr"
            }
            RecurrenceUnit.MONTHS -> {
                val domStr = when (dayOfMonth) {
                    null -> ""
                    -1 -> " (Letzter Tag)"
                    else -> " ($dayOfMonth.)"
                }
                if (safeInterval == 1) "Monatlich$domStr" else "Alle $safeInterval Mon.$domStr"
            }
            RecurrenceUnit.YEARS -> if (safeInterval == 1) "Jährlich" else "Alle $safeInterval J."
        }
        return if (mode == RecurrenceMode.AFTER_COMPLETION) {
            "$unitLabel n. Erledigung"
        } else {
            unitLabel
        }
    }
}

