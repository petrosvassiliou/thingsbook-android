package com.pv.thingsbook.domain.model

import java.time.Instant
import java.time.ZoneId

enum class QuickFindDateFilter {
    ALL,
    TODAY,
    OVERDUE,
    NEXT_7_DAYS,
    THIS_MONTH,
    NO_DATE
}

enum class QuickFindEntityScope {
    ALL,
    TASKS,
    PROJECTS
}

data class QuickFindFilter(
    val hideCompleted: Boolean = false,
    val dateFilter: QuickFindDateFilter = QuickFindDateFilter.ALL,
    val hasDeadlineOnly: Boolean = false,
    val hasChecklistOnly: Boolean = false,
    val tagId: String? = null,
    val scope: QuickFindEntityScope = QuickFindEntityScope.ALL
) {
    val hasActiveFilters: Boolean
        get() = hideCompleted ||
            dateFilter != QuickFindDateFilter.ALL ||
            hasDeadlineOnly ||
            hasChecklistOnly ||
            tagId != null ||
            scope != QuickFindEntityScope.ALL

    fun shouldShowSmartViewsAndAreas(query: String): Boolean {
        if (
            scope != QuickFindEntityScope.ALL ||
            dateFilter != QuickFindDateFilter.ALL ||
            hasDeadlineOnly ||
            hasChecklistOnly ||
            tagId != null
        ) {
            return false
        }
        if (hideCompleted && query.isBlank()) {
            return false
        }
        return true
    }

    fun matchesTask(
        task: TaskItem,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (task.isDeleted) return false
        if (scope == QuickFindEntityScope.PROJECTS) return false
        if (hideCompleted && task.status != TaskStatus.OPEN) return false
        if (hasDeadlineOnly && task.deadline == null) return false
        if (hasChecklistOnly && task.checklistItems.isEmpty()) return false
        if (tagId != null && task.tags.none { it.id == tagId }) return false

        return matchesDatePredicate(
            dateFilter = dateFilter,
            whenDate = task.whenDate,
            deadline = task.deadline,
            scheduleState = task.scheduleState,
            isOpenOrActive = task.status == TaskStatus.OPEN,
            nowMillis = nowMillis,
            zoneId = zoneId
        )
    }

    fun matchesProject(
        project: Project,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (project.isDeleted) return false
        if (scope == QuickFindEntityScope.TASKS) return false
        if (hasChecklistOnly) return false
        if (hideCompleted && project.status != ProjectStatus.ACTIVE) return false
        if (hasDeadlineOnly && project.deadline == null) return false
        if (tagId != null && tagId !in project.tagIds) return false

        return matchesDatePredicate(
            dateFilter = dateFilter,
            whenDate = project.whenDate,
            deadline = project.deadline,
            scheduleState = project.scheduleState,
            isOpenOrActive = project.status == ProjectStatus.ACTIVE,
            nowMillis = nowMillis,
            zoneId = zoneId
        )
    }

    companion object {
        private fun matchesDatePredicate(
            dateFilter: QuickFindDateFilter,
            whenDate: Long?,
            deadline: Long?,
            scheduleState: ScheduleState,
            isOpenOrActive: Boolean,
            nowMillis: Long,
            zoneId: ZoneId
        ): Boolean {
            if (dateFilter == QuickFindDateFilter.ALL) return true

            val todayLocal = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
            val todayStart = todayLocal.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val tomorrowStart = todayLocal.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val next7EndExclusive = todayLocal.plusDays(8).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val monthStartLocal = todayLocal.withDayOfMonth(1)
            val monthStart = monthStartLocal.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val nextMonthStart = monthStartLocal.plusMonths(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            val isTodaySchedule = scheduleState == ScheduleState.TODAY || scheduleState == ScheduleState.THIS_EVENING

            return when (dateFilter) {
                QuickFindDateFilter.ALL -> true
                QuickFindDateFilter.TODAY -> {
                    val whenToday = whenDate != null && whenDate in todayStart until tomorrowStart
                    val deadlineToday = deadline != null && deadline in todayStart until tomorrowStart
                    (isOpenOrActive && isTodaySchedule) || whenToday || deadlineToday
                }
                QuickFindDateFilter.OVERDUE -> {
                    isOpenOrActive && (
                        (deadline != null && deadline < todayStart) ||
                            (whenDate != null && whenDate < todayStart)
                        )
                }
                QuickFindDateFilter.NEXT_7_DAYS -> {
                    val whenIn7 = whenDate != null && whenDate in todayStart until next7EndExclusive
                    val deadlineIn7 = deadline != null && deadline in todayStart until next7EndExclusive
                    (isOpenOrActive && isTodaySchedule) || whenIn7 || deadlineIn7
                }
                QuickFindDateFilter.THIS_MONTH -> {
                    val whenInMonth = whenDate != null && whenDate in monthStart until nextMonthStart
                    val deadlineInMonth = deadline != null && deadline in monthStart until nextMonthStart
                    (isOpenOrActive && isTodaySchedule) || whenInMonth || deadlineInMonth
                }
                QuickFindDateFilter.NO_DATE -> {
                    whenDate == null &&
                        deadline == null &&
                        scheduleState != ScheduleState.TODAY &&
                        scheduleState != ScheduleState.THIS_EVENING &&
                        scheduleState != ScheduleState.UPCOMING
                }
            }
        }
    }
}
