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
    fun formatShortLabel(locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val safeInterval = interval.coerceIn(1, 999)
        val lang = locale.language.lowercase(java.util.Locale.ROOT)
        val unitLabel = when (unit) {
            RecurrenceUnit.DAYS -> when (lang) {
                "de" -> if (safeInterval == 1) "Täglich" else "Alle $safeInterval Tg."
                "it" -> if (safeInterval == 1) "Ogni giorno" else "Ogni $safeInterval gg."
                "fr" -> if (safeInterval == 1) "Quotidien" else "Tous les $safeInterval j."
                "es" -> if (safeInterval == 1) "Diario" else "Cada $safeInterval d."
                else -> if (safeInterval == 1) "Daily" else "Every $safeInterval d."
            }
            RecurrenceUnit.WEEKS -> {
                val validDays = daysOfWeek.filter { it in 1..7 }.distinct().sorted()
                val daysStr = if (validDays.isNotEmpty()) {
                    val dayNames = validDays.map { dow ->
                        runCatching {
                            java.time.DayOfWeek.of(dow)
                                .getDisplayName(java.time.format.TextStyle.SHORT, locale)
                                .removeSuffix(".")
                        }.getOrDefault(dow.toString())
                    }
                    " (" + dayNames.joinToString(",") + ")"
                } else ""
                when (lang) {
                    "de" -> if (safeInterval == 1) "Wöchentlich$daysStr" else "Alle $safeInterval Wo.$daysStr"
                    "it" -> if (safeInterval == 1) "Ogni sett.$daysStr" else "Ogni $safeInterval sett.$daysStr"
                    "fr" -> if (safeInterval == 1) "Hebdo.$daysStr" else "Toutes les $safeInterval sem.$daysStr"
                    "es" -> if (safeInterval == 1) "Semanal$daysStr" else "Cada $safeInterval sem.$daysStr"
                    else -> if (safeInterval == 1) "Weekly$daysStr" else "Every $safeInterval wk.$daysStr"
                }
            }
            RecurrenceUnit.MONTHS -> {
                val lastDayLabel = when (lang) {
                    "de" -> "Letzter Tag"
                    "it" -> "Ultimo giorno"
                    "fr" -> "Dernier jour"
                    "es" -> "Último día"
                    else -> "Last day"
                }
                val domStr = when {
                    dayOfMonth == null -> ""
                    dayOfMonth == -1 -> " ($lastDayLabel)"
                    dayOfMonth in 1..31 -> " ($dayOfMonth.)"
                    else -> ""
                }
                when (lang) {
                    "de" -> if (safeInterval == 1) "Monatlich$domStr" else "Alle $safeInterval Mon.$domStr"
                    "it" -> if (safeInterval == 1) "Ogni mese$domStr" else "Ogni $safeInterval mesi$domStr"
                    "fr" -> if (safeInterval == 1) "Mensuel$domStr" else "Tous les $safeInterval mois$domStr"
                    "es" -> if (safeInterval == 1) "Mensual$domStr" else "Cada $safeInterval mes.$domStr"
                    else -> if (safeInterval == 1) "Monthly$domStr" else "Every $safeInterval mo.$domStr"
                }
            }
            RecurrenceUnit.YEARS -> when (lang) {
                "de" -> if (safeInterval == 1) "Jährlich" else "Alle $safeInterval J."
                "it" -> if (safeInterval == 1) "Ogni anno" else "Ogni $safeInterval anni"
                "fr" -> if (safeInterval == 1) "Annuel" else "Tous les $safeInterval ans"
                "es" -> if (safeInterval == 1) "Anual" else "Cada $safeInterval a."
                else -> if (safeInterval == 1) "Yearly" else "Every $safeInterval yr."
            }
        }
        return if (mode == RecurrenceMode.AFTER_COMPLETION) {
            val suffix = when (lang) {
                "de" -> "n. Erledigung"
                "it" -> "dopo compl."
                "fr" -> "après achèv."
                "es" -> "tras compl."
                else -> "after compl."
            }
            "$unitLabel $suffix"
        } else {
            unitLabel
        }
    }
}


