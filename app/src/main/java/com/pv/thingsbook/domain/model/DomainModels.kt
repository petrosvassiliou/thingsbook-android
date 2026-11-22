package com.pv.thingsbook.domain.model

import kotlinx.serialization.Serializable
import java.util.Locale

object EffortLevel {
    const val QUICK_WIN = "QUICK_WIN"
    const val MEDIUM = "MEDIUM"
    const val DEEP_FOCUS = "DEEP_FOCUS"

    val ALL: List<String> = listOf(QUICK_WIN, MEDIUM, DEEP_FOCUS)

    fun normalize(raw: String?): String? = when (raw?.trim()?.uppercase(Locale.ROOT)) {
        QUICK_WIN -> QUICK_WIN
        MEDIUM -> MEDIUM
        DEEP_FOCUS -> DEEP_FOCUS
        else -> null
    }

    fun sortRank(raw: String?): Int = when (normalize(raw)) {
        QUICK_WIN -> 0
        MEDIUM -> 1
        DEEP_FOCUS -> 2
        else -> 3
    }
}

@Serializable
data class SavedPerspective(
    val id: String,
    val title: String,
    val categoryIds: List<String> = emptyList(),
    val effortLevel: String? = null,
    val maxDurationMinutes: Int? = null,
    val dueWindow: String = "ANY",
    val areaId: String? = null,
    val actionableOnly: Boolean = false,
    val sortOrder: Double = 0.0
)

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
    val workspaceId: String? = null,
    val viewMode: String? = null,
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
    val startDate: Long? = null,
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
    val isPinned: Boolean = false,
    val isMilestone: Boolean = false,
    val effortLevel: String? = null,
    val blockedByTaskIds: List<String> = emptyList(),
    val workspaceId: String? = null,
    val assigneeId: String? = null,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val attachments: List<TaskAttachment> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

const val MAX_TASK_DURATION_MINUTES = 48_000

val STANDALONE_DURATION_PRESETS: List<Int> = listOf(5, 15, 30, 60, 120, 240, 480)

fun formatDurationCompact(minutes: Int): String {
    val safeMins = minutes.coerceAtLeast(1)
    if (safeMins > 480) {
        val days = safeMins / 480
        val rem = safeMins % 480
        val hours = rem / 60
        val mins = rem % 60
        return buildList {
            add("${days}d")
            if (hours > 0) add("${hours}h")
            if (mins > 0) add("${mins}m")
        }.joinToString(" ")
    }
    val hours = safeMins / 60
    val rem = safeMins % 60
    return when {
        hours == 0 -> "${rem}m"
        rem == 0 -> "${hours}h"
        else -> "${hours}h ${rem}m"
    }
}

fun formatDurationCompact(minutes: Int?): String? =
    minutes?.takeIf { it > 0 }?.let { formatDurationCompact(it) }

private val CUSTOM_DURATION_DAY_UNITS = setOf(
    "d", "day", "days", "t", "tag", "tage", "j", "jour", "jours", "g", "giorno", "giorni", "dia", "dias", "día", "días"
)
private val CUSTOM_DURATION_HOUR_UNITS = setOf(
    "h", "hr", "hrs", "hour", "hours", "std", "stunde", "stunden", "heure", "heures", "ora", "ore", "hora", "horas"
)
private val CUSTOM_DURATION_MIN_UNITS = setOf(
    "m", "min", "mins", "minute", "minutes", "minuten", "minuto", "minuti", "minutos"
)
private val CUSTOM_DURATION_PLAIN_NUMBER_REGEX = Regex("""^\d+(?:\.\d+)?$""")
private val CUSTOM_DURATION_TOKEN_REGEX = Regex("""(\d+(?:\.\d+)?)\s*([a-zA-Zäöüßáéíóúàèìòù]+)""")

fun parseCustomDurationMinutes(rawInput: String): Int? {
    val normalized = rawInput.trim().lowercase().replace(',', '.')
    if (normalized.isEmpty()) return null

    val colonParts = normalized.split(':')
    if (colonParts.size == 2) {
        val hh = colonParts[0].trim().toIntOrNull()
        val mm = colonParts[1].trim().toIntOrNull()
        if (hh != null && mm != null && hh >= 0 && mm in 0..59) {
            val total = hh * 60 + mm
            return if (total > 0) total.coerceAtMost(MAX_TASK_DURATION_MINUTES) else null
        }
        return null
    }

    if (CUSTOM_DURATION_PLAIN_NUMBER_REGEX.matches(normalized)) {
        val plain = normalized.toDoubleOrNull()
        if (plain != null && plain.isFinite()) {
            val rounded = kotlin.math.round(plain).toInt()
            return if (rounded > 0) rounded.coerceAtMost(MAX_TASK_DURATION_MINUTES) else null
        }
    }

    val matches = CUSTOM_DURATION_TOKEN_REGEX.findAll(normalized).toList()
    if (matches.isEmpty()) return null

    val stripped = normalized
        .replace(CUSTOM_DURATION_TOKEN_REGEX, "")
        .replace("+", "")
        .trim()
    if (stripped.isNotEmpty()) return null

    var totalMinutes = 0.0
    for (match in matches) {
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        totalMinutes += when {
            unit in CUSTOM_DURATION_DAY_UNITS -> value * 480.0
            unit in CUSTOM_DURATION_HOUR_UNITS -> value * 60.0
            unit in CUSTOM_DURATION_MIN_UNITS -> value
            else -> return null
        }
    }

    val rounded = kotlin.math.round(totalMinutes).toInt()
    return if (rounded > 0) rounded.coerceAtMost(MAX_TASK_DURATION_MINUTES) else null
}

fun wouldCreateDependencyCycle(
    taskId: String,
    candidateBlockerId: String,
    blockedByMap: Map<String, List<String>>
): Boolean {
    val cleanTaskId = taskId.trim()
    val cleanCandidateId = candidateBlockerId.trim()
    if (cleanTaskId.isEmpty() || cleanCandidateId.isEmpty()) return true
    if (cleanTaskId == cleanCandidateId) return true

    val visited = HashSet<String>()
    val queue = ArrayDeque<String>()
    queue.addLast(cleanCandidateId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current == cleanTaskId) return true
        if (!visited.add(current)) continue
        val upstreamBlockers = blockedByMap[current].orEmpty()
        for (next in upstreamBlockers) {
            val trimmedNext = next.trim()
            if (trimmedNext.isEmpty()) continue
            if (trimmedNext == cleanTaskId) return true
            if (trimmedNext !in visited) {
                queue.addLast(trimmedNext)
            }
        }
    }
    return false
}

fun isTaskActivelyBlocked(task: TaskItem, openTaskIds: Set<String>): Boolean {
    return task.blockedByTaskIds.any { it in openTaskIds }
}

fun sortTasksForView(
    tasks: List<TaskItem>,
    sortMode: String,
    openTaskIds: Set<String> = emptySet()
): List<TaskItem> {
    if (tasks.size <= 1) return tasks
    val normalizedMode = sortMode.trim().lowercase(Locale.ROOT)
    return tasks.sortedWith { a, b ->
        if (a.isPinned != b.isPinned) {
            return@sortedWith if (a.isPinned) -1 else 1
        }
        val modeCmp = when (normalizedMode) {
            "deadline" -> {
                val aDl = a.deadline
                val bDl = b.deadline
                when {
                    aDl != null && bDl != null -> {
                        val dlCmp = aDl.compareTo(bDl)
                        if (dlCmp != 0) dlCmp else a.sortOrder.compareTo(b.sortOrder)
                    }
                    aDl != null -> -1
                    bDl != null -> 1
                    else -> a.sortOrder.compareTo(b.sortOrder)
                }
            }
            "duration" -> {
                val aDur = a.durationMinutes?.takeIf { it > 0 }
                val bDur = b.durationMinutes?.takeIf { it > 0 }
                when {
                    aDur != null && bDur != null -> {
                        val durCmp = aDur.compareTo(bDur)
                        if (durCmp != 0) durCmp else a.sortOrder.compareTo(b.sortOrder)
                    }
                    aDur != null -> -1
                    bDur != null -> 1
                    else -> a.sortOrder.compareTo(b.sortOrder)
                }
            }
            "effort" -> {
                val rankA = EffortLevel.sortRank(a.effortLevel)
                val rankB = EffortLevel.sortRank(b.effortLevel)
                val effortCmp = rankA.compareTo(rankB)
                if (effortCmp != 0) effortCmp else a.sortOrder.compareTo(b.sortOrder)
            }
            "alpha" -> {
                val alphaCmp = a.title.lowercase(Locale.ROOT)
                    .compareTo(b.title.lowercase(Locale.ROOT))
                if (alphaCmp != 0) alphaCmp else a.sortOrder.compareTo(b.sortOrder)
            }
            else -> {
                val orderCmp = a.sortOrder.compareTo(b.sortOrder)
                if (orderCmp != 0) orderCmp else a.createdAt.compareTo(b.createdAt)
            }
        }
        if (modeCmp != 0) {
            modeCmp
        } else {
            val createdCmp = a.createdAt.compareTo(b.createdAt)
            if (createdCmp != 0) createdCmp else a.id.compareTo(b.id)
        }
    }
}

fun sortTagsForMode(
    tags: List<Tag>,
    mode: String,
    usageCounts: Map<String, Int> = emptyMap()
): List<Tag> {
    if (tags.size <= 1) return tags
    val normalizedMode = mode.trim().lowercase(Locale.ROOT)
    return tags.sortedWith { a, b ->
        val cmp = when (normalizedMode) {
            "most_used", "usage" -> {
                val countA = usageCounts[a.id] ?: 0
                val countB = usageCounts[b.id] ?: 0
                val countCmp = countB.compareTo(countA)
                if (countCmp != 0) {
                    countCmp
                } else {
                    a.title.lowercase(Locale.ROOT)
                        .compareTo(b.title.lowercase(Locale.ROOT))
                }
            }
            "manual", "custom" -> {
                val orderCmp = a.sortOrder.compareTo(b.sortOrder)
                if (orderCmp != 0) {
                    orderCmp
                } else {
                    val createdCmp = a.createdAt.compareTo(b.createdAt)
                    if (createdCmp != 0) {
                        createdCmp
                    } else {
                        a.title.lowercase(Locale.ROOT)
                            .compareTo(b.title.lowercase(Locale.ROOT))
                    }
                }
            }
            else -> {
                val alphaCmp = a.title.lowercase(Locale.ROOT)
                    .compareTo(b.title.lowercase(Locale.ROOT))
                if (alphaCmp != 0) alphaCmp else a.sortOrder.compareTo(b.sortOrder)
            }
        }
        if (cmp != 0) cmp else a.id.compareTo(b.id)
    }
}

fun filterTasksForPerspective(
    tasks: List<TaskItem>,
    perspective: SavedPerspective,
    openTaskIds: Set<String>,
    todayEpochMillis: Long
): List<TaskItem> {
    val endOfToday = todayEpochMillis + 86_400_000L - 1L
    val endOfNext7Days = todayEpochMillis + 8L * 86_400_000L - 1L
    val requiredCategoryIds = perspective.categoryIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val requiredEffort = EffortLevel.normalize(perspective.effortLevel)
    val maxDur = perspective.maxDurationMinutes?.takeIf { it > 0 }
    val requiredAreaId = perspective.areaId?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedWindow = perspective.dueWindow.trim().uppercase(Locale.ROOT)

    return tasks.filter { task ->
        if (task.isDeleted || task.status != TaskStatus.OPEN) return@filter false
        if (perspective.actionableOnly && isTaskActivelyBlocked(task, openTaskIds)) return@filter false
        if (requiredAreaId != null && task.areaId != requiredAreaId) return@filter false
        if (requiredEffort != null && EffortLevel.normalize(task.effortLevel) != requiredEffort) return@filter false
        if (maxDur != null) {
            val dur = task.durationMinutes
            if (dur == null || dur <= 0 || dur > maxDur) return@filter false
        }
        if (requiredCategoryIds.isNotEmpty()) {
            val hasMatchingCategory = task.tags.any { it.id in requiredCategoryIds }
            if (!hasMatchingCategory) return@filter false
        }
        when (normalizedWindow) {
            "", "ANY", "ALL" -> true
            "TODAY" -> {
                task.scheduleState == ScheduleState.TODAY ||
                    task.scheduleState == ScheduleState.THIS_EVENING ||
                    (task.whenDate != null && task.whenDate <= endOfToday) ||
                    (task.deadline != null && task.deadline <= endOfToday)
            }
            "OVERDUE" -> {
                (task.deadline != null && task.deadline < todayEpochMillis) ||
                    (task.whenDate != null && task.whenDate < todayEpochMillis)
            }
            "NEXT_7_DAYS", "WEEK", "UPCOMING_7_DAYS" -> {
                val effectiveDate = task.deadline ?: task.whenDate
                task.scheduleState == ScheduleState.TODAY ||
                    task.scheduleState == ScheduleState.THIS_EVENING ||
                    (effectiveDate != null && effectiveDate <= endOfNext7Days)
            }
            "HAS_DEADLINE", "DEADLINE" -> task.deadline != null
            "NONE", "NO_DATE", "UNSCHEDULED" -> {
                task.whenDate == null &&
                    task.deadline == null &&
                    task.scheduleState != ScheduleState.TODAY &&
                    task.scheduleState != ScheduleState.THIS_EVENING
            }
            else -> true
        }
    }
}

fun computeTodayCapacitySummary(
    tasks: List<TaskItem>,
    calendarEvents: List<CalendarEventItem>,
    dayStartMillis: Long,
    dayEndMillis: Long
): Pair<Int, Int> {
    val plannedTaskMinutes = tasks
        .asSequence()
        .filter { !it.isDeleted && it.status == TaskStatus.OPEN }
        .sumOf { task ->
            val explicitDur = task.durationMinutes?.takeIf { it > 0 }
            when {
                explicitDur != null -> explicitDur
                task.scheduledStartMinutes != null -> 30
                else -> 0
            }
        }
        .coerceAtLeast(0)

    val workdayStartMs = dayStartMillis + 9L * 60L * 60_000L
    val rawWorkdayEndMs = dayStartMillis + 17L * 60L * 60_000L
    val workdayEndMs = if (dayEndMillis > workdayStartMs) {
        minOf(dayEndMillis, rawWorkdayEndMs)
    } else {
        rawWorkdayEndMs
    }
    val totalWorkdayMinutes = ((workdayEndMs - workdayStartMs) / 60_000L).toInt().coerceIn(0, 480)

    val clippedIntervals = calendarEvents
        .asSequence()
        .filter { !it.isAllDay && it.endMillis > it.startMillis }
        .mapNotNull { ev ->
            val s = maxOf(ev.startMillis, workdayStartMs)
            val e = minOf(ev.endMillis, workdayEndMs)
            if (e > s) s to e else null
        }
        .sortedBy { it.first }
        .toList()

    var busyCalendarMillis = 0L
    if (clippedIntervals.isNotEmpty()) {
        var curStart = clippedIntervals[0].first
        var curEnd = clippedIntervals[0].second
        for (i in 1 until clippedIntervals.size) {
            val (nextStart, nextEnd) = clippedIntervals[i]
            if (nextStart <= curEnd) {
                curEnd = maxOf(curEnd, nextEnd)
            } else {
                busyCalendarMillis += (curEnd - curStart)
                curStart = nextStart
                curEnd = nextEnd
            }
        }
        busyCalendarMillis += (curEnd - curStart)
    }
    val busyCalendarMinutes = (busyCalendarMillis / 60_000L).toInt().coerceAtLeast(0)
    val freeWorkdayMinutes = (totalWorkdayMinutes - busyCalendarMinutes - plannedTaskMinutes).coerceAtLeast(0)
    return plannedTaskMinutes to freeWorkdayMinutes
}

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

