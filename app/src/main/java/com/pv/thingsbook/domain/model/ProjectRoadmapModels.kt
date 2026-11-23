package com.pv.thingsbook.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class ProjectWorkspaceMode(val storageKey: String) {
    LIST("list"),
    BOARD("board"),
    ROADMAP("roadmap");

    companion object {
        fun fromRaw(raw: String?): ProjectWorkspaceMode = when (raw?.trim()?.lowercase(Locale.ROOT)) {
            "board", "kanban" -> BOARD
            "roadmap", "gantt" -> ROADMAP
            else -> LIST
        }
    }
}

data class RoadmapPhaseBar(
    val heading: Heading,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val hasExplicitDates: Boolean
) {
    val headingId: String get() = heading.id
    val title: String get() = heading.title
    val spanDays: Int get() = (endEpochDay - startEpochDay + 1L).coerceAtLeast(1L).toInt()
}

data class RoadmapTaskBar(
    val task: TaskItem,
    val headingId: String?,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val isActivelyBlocked: Boolean = false,
    val isMilestone: Boolean = task.isMilestone
) {
    val taskId: String get() = task.id
    val title: String get() = task.title
    val spanDays: Int get() = (endEpochDay - startEpochDay + 1L).coerceAtLeast(1L).toInt()
}

data class RoadmapDependencyEdge(
    val fromTaskId: String,
    val toTaskId: String,
    val isActivelyBlocking: Boolean
)

data class RoadmapLane(
    val heading: Heading?,
    val phaseBar: RoadmapPhaseBar?,
    val taskBars: List<RoadmapTaskBar>,
    val unscheduledTasks: List<TaskItem>
) {
    val laneKey: String get() = heading?.id ?: "general"
}

data class ProjectRoadmapLayout(
    val project: Project,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val todayEpochDay: Long,
    val lanes: List<RoadmapLane>,
    val phaseBars: List<RoadmapPhaseBar>,
    val taskBars: List<RoadmapTaskBar>,
    val dependencyEdges: List<RoadmapDependencyEdge>,
    val unscheduledHeadings: List<Heading>,
    val unscheduledTasks: List<TaskItem>
) {
    val totalDays: Int
        get() = (endEpochDay - startEpochDay + 1L).coerceAtLeast(1L).toInt()
}

enum class PdfPageOrientation(val pageWidthPt: Int, val pageHeightPt: Int) {
    LANDSCAPE(842, 595),
    PORTRAIT(595, 842)
}

enum class RoadmapPdfTimelinePreset {
    FULL_PROJECT,
    FROM_TODAY,
    NEXT_30_DAYS,
    CUSTOM
}

data class RoadmapPdfExportConfig(
    val selectedLaneKeys: Set<String>,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val orientation: PdfPageOrientation = PdfPageOrientation.LANDSCAPE
)

data class CascadedTaskShift(
    val task: TaskItem,
    val deltaDays: Long,
    val nextScheduleState: ScheduleState,
    val nextWhenMillis: Long?,
    val nextDeadlineMillis: Long?,
    val isAnchor: Boolean
)

data class AreaPortfolioProjectLane(
    val project: Project,
    val projectLayout: ProjectRoadmapLayout,
    val summaryStartEpochDay: Long,
    val summaryEndEpochDay: Long,
    val milestoneBars: List<RoadmapTaskBar>
) {
    val projectId: String get() = project.id
    val spanDays: Int get() = (summaryEndEpochDay - summaryStartEpochDay + 1L).coerceAtLeast(1L).toInt()
}

data class AreaPortfolioRoadmapLayout(
    val area: Area,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val todayEpochDay: Long,
    val projectLanes: List<AreaPortfolioProjectLane>,
    val unscheduledProjects: List<Project>
) {
    val totalDays: Int
        get() = (endEpochDay - startEpochDay + 1L).coerceAtLeast(1L).toInt()
}

object ProjectRoadmapEngine {

    const val MIN_TIMELINE_DAYS: Long = 21L
    private const val LEADING_BUFFER_DAYS: Long = 3L
    private const val TRAILING_BUFFER_DAYS: Long = 7L

    fun epochMillisToEpochDay(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate().toEpochDay()

    fun epochDayToStartOfDayMillis(
        epochDay: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long = LocalDate.ofEpochDay(epochDay).atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun durationMinutesToDays(durationMinutes: Int?): Long {
        val safeMinutes = (durationMinutes ?: 30).coerceAtLeast(0).toLong()
        return maxOf(1L, (safeMinutes + 479L) / 480L)
    }

    fun buildRoadmapLayout(
        project: Project,
        headings: List<Heading>,
        tasks: List<TaskItem>,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ProjectRoadmapLayout {
        val activeHeadings = headings
            .filter { !it.isDeleted && !it.isArchived }
            .sortedWith(compareBy<Heading> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
        val validHeadingIds = activeHeadings.mapTo(HashSet()) { it.id }

        val eligibleTasks = tasks
            .filter { !it.isDeleted && it.status != TaskStatus.CANCELED }
            .sortedWith(
                compareByDescending<TaskItem> { it.isPinned }
                    .thenBy { it.sortOrder }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )

        val openTaskIdsInProject = eligibleTasks
            .asSequence()
            .filter { it.status == TaskStatus.OPEN }
            .mapTo(HashSet()) { it.id }

        fun resolveEffectiveHeadingId(task: TaskItem): String? =
            task.headingId?.takeIf { it in validHeadingIds }

        fun buildTaskBarOrNull(task: TaskItem, effectiveHeadingId: String?): RoadmapTaskBar? {
            if (task.isMilestone) {
                val targetMillis = task.deadline ?: task.whenDate ?: return null
                val targetDay = epochMillisToEpochDay(targetMillis, zoneId)
                val blocked = task.blockedByTaskIds.any { it in openTaskIdsInProject }
                return RoadmapTaskBar(
                    task = task,
                    headingId = effectiveHeadingId,
                    startEpochDay = targetDay,
                    endEpochDay = targetDay,
                    isActivelyBlocked = blocked,
                    isMilestone = true
                )
            }
            val whenDay = task.whenDate?.let { epochMillisToEpochDay(it, zoneId) }
            val deadlineDay = task.deadline?.let { epochMillisToEpochDay(it, zoneId) }
            val (startDay, endDay) = when {
                whenDay != null && deadlineDay != null -> {
                    whenDay to maxOf(whenDay, deadlineDay)
                }
                whenDay != null && deadlineDay == null -> {
                    val durationDays = durationMinutesToDays(task.durationMinutes)
                    whenDay to (whenDay + durationDays - 1L)
                }
                whenDay == null && deadlineDay != null -> {
                    val durationDays = durationMinutesToDays(task.durationMinutes)
                    (deadlineDay - (durationDays - 1L)) to deadlineDay
                }
                else -> return null
            }
            val blocked = task.blockedByTaskIds.any { it in openTaskIdsInProject }
            return RoadmapTaskBar(
                task = task,
                headingId = effectiveHeadingId,
                startEpochDay = startDay,
                endEpochDay = maxOf(startDay, endDay),
                isActivelyBlocked = blocked,
                isMilestone = false
            )
        }

        val tasksByHeading = LinkedHashMap<String?, MutableList<TaskItem>>()
        tasksByHeading[null] = mutableListOf()
        for (h in activeHeadings) {
            tasksByHeading[h.id] = mutableListOf()
        }
        for (t in eligibleTasks) {
            val bucket = resolveEffectiveHeadingId(t)
            tasksByHeading.getOrPut(bucket) { mutableListOf() }.add(t)
        }

        val allPhaseBars = mutableListOf<RoadmapPhaseBar>()
        val allTaskBars = mutableListOf<RoadmapTaskBar>()
        val unscheduledHeadings = mutableListOf<Heading>()
        val unscheduledTasks = mutableListOf<TaskItem>()
        val lanes = mutableListOf<RoadmapLane>()

        // 1. General (ungrouped) lane when ungrouped tasks exist or project has no headings
        val generalTasks = tasksByHeading[null].orEmpty()
        val generalTaskBars = mutableListOf<RoadmapTaskBar>()
        val generalUnscheduled = mutableListOf<TaskItem>()
        for (t in generalTasks) {
            val bar = buildTaskBarOrNull(t, null)
            if (bar != null) {
                generalTaskBars += bar
                allTaskBars += bar
            } else {
                generalUnscheduled += t
                unscheduledTasks += t
            }
        }
        if (generalTaskBars.isNotEmpty() || (activeHeadings.isEmpty() && generalUnscheduled.isNotEmpty())) {
            lanes += RoadmapLane(
                heading = null,
                phaseBar = null,
                taskBars = generalTaskBars,
                unscheduledTasks = generalUnscheduled
            )
        }

        // 2. Phase (Heading) lanes in project order
        for (heading in activeHeadings) {
            val childTasks = tasksByHeading[heading.id].orEmpty()
            val childBars = mutableListOf<RoadmapTaskBar>()
            val childUnscheduled = mutableListOf<TaskItem>()
            for (t in childTasks) {
                val bar = buildTaskBarOrNull(t, heading.id)
                if (bar != null) {
                    childBars += bar
                    allTaskBars += bar
                } else {
                    childUnscheduled += t
                    unscheduledTasks += t
                }
            }

            val explicitStart = heading.startDate?.let { epochMillisToEpochDay(it, zoneId) }
            val explicitEnd = heading.endDate?.let { epochMillisToEpochDay(it, zoneId) }
            val minChildStart = childBars.minOfOrNull { it.startEpochDay }
            val maxChildEnd = childBars.maxOfOrNull { it.endEpochDay }

            val phaseBar: RoadmapPhaseBar? = when {
                explicitStart != null && explicitEnd != null -> {
                    RoadmapPhaseBar(
                        heading = heading,
                        startEpochDay = explicitStart,
                        endEpochDay = maxOf(explicitStart, explicitEnd),
                        hasExplicitDates = true
                    )
                }
                explicitStart != null && explicitEnd == null -> {
                    val inferredEnd = if (maxChildEnd != null && maxChildEnd >= explicitStart) {
                        maxChildEnd
                    } else {
                        explicitStart
                    }
                    RoadmapPhaseBar(
                        heading = heading,
                        startEpochDay = explicitStart,
                        endEpochDay = inferredEnd,
                        hasExplicitDates = true
                    )
                }
                explicitStart == null && explicitEnd != null -> {
                    val inferredStart = if (minChildStart != null && minChildStart <= explicitEnd) {
                        minChildStart
                    } else {
                        explicitEnd
                    }
                    RoadmapPhaseBar(
                        heading = heading,
                        startEpochDay = inferredStart,
                        endEpochDay = maxOf(inferredStart, explicitEnd),
                        hasExplicitDates = true
                    )
                }
                minChildStart != null && maxChildEnd != null -> {
                    RoadmapPhaseBar(
                        heading = heading,
                        startEpochDay = minChildStart,
                        endEpochDay = maxOf(minChildStart, maxChildEnd),
                        hasExplicitDates = false
                    )
                }
                else -> {
                    unscheduledHeadings += heading
                    null
                }
            }

            if (phaseBar != null) {
                allPhaseBars += phaseBar
            }
            if (phaseBar != null || childBars.isNotEmpty()) {
                lanes += RoadmapLane(
                    heading = heading,
                    phaseBar = phaseBar,
                    taskBars = childBars,
                    unscheduledTasks = childUnscheduled
                )
            }
        }

        // 3. Window [startEpochDay, endEpochDay] (totalDays >= 21, capped at <= 1 month after last task end date)
        val minScheduledDay = sequence {
            allPhaseBars.forEach { yield(it.startEpochDay) }
            allTaskBars.forEach { yield(it.startEpochDay) }
        }.minOrNull()

        val startEpochDay = if (minScheduledDay != null && minScheduledDay < todayEpochDay - LEADING_BUFFER_DAYS) {
            minScheduledDay - 1L
        } else {
            todayEpochDay - LEADING_BUFFER_DAYS
        }

        val endEpochDay = if (allTaskBars.isNotEmpty()) {
            val lastTaskEndDay = allTaskBars.maxOf { it.endEpochDay }
            val oneMonthCapDay = LocalDate.ofEpochDay(lastTaskEndDay).plusMonths(1).toEpochDay()
            val maxTaskOrPhaseEnd = maxOf(
                lastTaskEndDay,
                allPhaseBars.maxOfOrNull { it.endEpochDay } ?: lastTaskEndDay
            )
            val uncappedDesiredEnd = maxOf(
                startEpochDay + (MIN_TIMELINE_DAYS - 1L),
                maxTaskOrPhaseEnd + TRAILING_BUFFER_DAYS
            )
            if (oneMonthCapDay >= startEpochDay) {
                maxOf(lastTaskEndDay, minOf(uncappedDesiredEnd, oneMonthCapDay))
            } else {
                uncappedDesiredEnd
            }
        } else {
            minOf(
                maxOf(
                    startEpochDay + 20L,
                    (allPhaseBars.maxOfOrNull { it.endEpochDay } ?: todayEpochDay) + 7L
                ),
                LocalDate.ofEpochDay(todayEpochDay).plusMonths(1).toEpochDay().coerceAtLeast(startEpochDay + 20L)
            )
        }

        // 4. Dependency edges among plotted tasks in this project
        val plottedById = allTaskBars.associateBy { it.taskId }
        val dependencyEdges = mutableListOf<RoadmapDependencyEdge>()
        for (targetBar in allTaskBars) {
            val seenBlockers = HashSet<String>()
            for (rawBlockerId in targetBar.task.blockedByTaskIds) {
                val blockerId = rawBlockerId.trim()
                if (blockerId.isEmpty() || blockerId == targetBar.taskId) continue
                if (!seenBlockers.add(blockerId)) continue
                val blockerBar = plottedById[blockerId] ?: continue
                dependencyEdges += RoadmapDependencyEdge(
                    fromTaskId = blockerId,
                    toTaskId = targetBar.taskId,
                    isActivelyBlocking = blockerBar.task.status == TaskStatus.OPEN
                )
            }
        }

        return ProjectRoadmapLayout(
            project = project,
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay,
            todayEpochDay = todayEpochDay,
            lanes = lanes,
            phaseBars = allPhaseBars,
            taskBars = allTaskBars,
            dependencyEdges = dependencyEdges,
            unscheduledHeadings = unscheduledHeadings,
            unscheduledTasks = unscheduledTasks
        )
    }

    fun filterLayoutForExport(
        layout: ProjectRoadmapLayout,
        config: RoadmapPdfExportConfig
    ): ProjectRoadmapLayout {
        val safeStart = minOf(config.startEpochDay, config.endEpochDay)
        val safeEnd = maxOf(config.startEpochDay, config.endEpochDay)

        val filteredLanes = mutableListOf<RoadmapLane>()
        val filteredPhaseBars = mutableListOf<RoadmapPhaseBar>()
        val filteredTaskBars = mutableListOf<RoadmapTaskBar>()

        for (lane in layout.lanes) {
            if (lane.laneKey !in config.selectedLaneKeys) continue

            val pb = lane.phaseBar
            val clampedPhaseBar = if (pb != null && pb.endEpochDay >= safeStart && pb.startEpochDay <= safeEnd) {
                pb.copy(
                    startEpochDay = maxOf(pb.startEpochDay, safeStart),
                    endEpochDay = minOf(pb.endEpochDay, safeEnd)
                )
            } else {
                null
            }

            val clampedTaskBars = lane.taskBars.mapNotNull { tb ->
                if (tb.endEpochDay >= safeStart && tb.startEpochDay <= safeEnd) {
                    tb.copy(
                        startEpochDay = maxOf(tb.startEpochDay, safeStart),
                        endEpochDay = minOf(tb.endEpochDay, safeEnd)
                    )
                } else {
                    null
                }
            }

            val originallyEmptyScheduled = lane.phaseBar == null && lane.taskBars.isEmpty()
            if (clampedPhaseBar != null || clampedTaskBars.isNotEmpty() || originallyEmptyScheduled) {
                if (clampedPhaseBar != null) {
                    filteredPhaseBars += clampedPhaseBar
                }
                filteredTaskBars += clampedTaskBars
                filteredLanes += lane.copy(
                    phaseBar = clampedPhaseBar,
                    taskBars = clampedTaskBars
                )
            }
        }

        val keptTaskIds = filteredTaskBars.mapTo(HashSet()) { it.taskId }
        val filteredEdges = layout.dependencyEdges.filter { edge ->
            edge.fromTaskId in keptTaskIds && edge.toTaskId in keptTaskIds
        }
        val filteredUnscheduledHeadings = layout.unscheduledHeadings.filter { it.id in config.selectedLaneKeys }
        val filteredUnscheduledTasks = filteredLanes.flatMap { it.unscheduledTasks }

        return layout.copy(
            startEpochDay = safeStart,
            endEpochDay = safeEnd,
            lanes = filteredLanes,
            phaseBars = filteredPhaseBars,
            taskBars = filteredTaskBars,
            dependencyEdges = filteredEdges,
            unscheduledHeadings = filteredUnscheduledHeadings,
            unscheduledTasks = filteredUnscheduledTasks
        )
    }

    fun shiftHeadingDatesByDays(
        heading: Heading,
        deltaDays: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Long?, Long?> {
        if (deltaDays == 0L) return heading.startDate to heading.endDate
        val shiftedStart = heading.startDate?.let { ms ->
            epochDayToStartOfDayMillis(epochMillisToEpochDay(ms, zoneId) + deltaDays, zoneId)
        }
        val shiftedEnd = heading.endDate?.let { ms ->
            epochDayToStartOfDayMillis(epochMillisToEpochDay(ms, zoneId) + deltaDays, zoneId)
        }
        val clampedEnd = if (shiftedStart != null && shiftedEnd != null && shiftedEnd < shiftedStart) {
            shiftedStart
        } else {
            shiftedEnd
        }
        return shiftedStart to clampedEnd
    }

    fun shiftTaskDatesByDays(
        task: TaskItem,
        deltaDays: Long,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Triple<ScheduleState, Long?, Long?> {
        val origWhenDay = task.whenDate?.let { epochMillisToEpochDay(it, zoneId) }
        val origDeadlineDay = task.deadline?.let { epochMillisToEpochDay(it, zoneId) }

        if (origWhenDay == null && origDeadlineDay == null) {
            val newWhenDay = todayEpochDay + deltaDays
            val newWhenMillis = epochDayToStartOfDayMillis(newWhenDay, zoneId)
            val newState = resolveScheduleStateForWhenDay(task, newWhenDay, todayEpochDay)
            return Triple(newState, newWhenMillis, null)
        }

        val newWhenDay = origWhenDay?.plus(deltaDays)
        val newDeadlineDay = origDeadlineDay?.plus(deltaDays)
        val newWhenMillis = newWhenDay?.let { epochDayToStartOfDayMillis(it, zoneId) }
        val newDeadlineMillis = newDeadlineDay?.let { epochDayToStartOfDayMillis(it, zoneId) }

        val newState = if (newWhenDay != null) {
            resolveScheduleStateForWhenDay(task, newWhenDay, todayEpochDay)
        } else {
            // Deadline-only task: keep existing non-Today state when future, or promote to TODAY if due today/overdue
            val effectiveDay = newDeadlineDay ?: todayEpochDay
            when {
                effectiveDay == todayEpochDay && task.scheduleState == ScheduleState.THIS_EVENING ->
                    ScheduleState.THIS_EVENING
                effectiveDay <= todayEpochDay -> ScheduleState.TODAY
                task.scheduleState == ScheduleState.TODAY || task.scheduleState == ScheduleState.THIS_EVENING ->
                    if (task.projectId != null || task.areaId != null) ScheduleState.ANYTIME else ScheduleState.UPCOMING
                else -> task.scheduleState
            }
        }
        return Triple(newState, newWhenMillis, newDeadlineMillis)
    }

    private fun resolveScheduleStateForWhenDay(
        task: TaskItem,
        newWhenDay: Long,
        todayEpochDay: Long
    ): ScheduleState = when {
        newWhenDay == todayEpochDay && task.scheduleState == ScheduleState.THIS_EVENING ->
            ScheduleState.THIS_EVENING
        newWhenDay <= todayEpochDay -> ScheduleState.TODAY
        else -> ScheduleState.UPCOMING
    }

    private fun resolveRawTaskSpanEpochDays(
        task: TaskItem,
        zoneId: ZoneId
    ): Pair<Long, Long>? {
        if (task.isMilestone) {
            val targetMillis = task.deadline ?: task.whenDate ?: return null
            val targetDay = epochMillisToEpochDay(targetMillis, zoneId)
            return targetDay to targetDay
        }
        val whenDay = task.whenDate?.let { epochMillisToEpochDay(it, zoneId) }
        val deadlineDay = task.deadline?.let { epochMillisToEpochDay(it, zoneId) }
        return when {
            whenDay != null && deadlineDay != null -> whenDay to maxOf(whenDay, deadlineDay)
            whenDay != null && deadlineDay == null -> {
                val durDays = durationMinutesToDays(task.durationMinutes)
                whenDay to (whenDay + durDays - 1L)
            }
            whenDay == null && deadlineDay != null -> {
                val durDays = durationMinutesToDays(task.durationMinutes)
                (deadlineDay - (durDays - 1L)) to deadlineDay
            }
            else -> null
        }
    }

    /**
     * Smart Push-on-Conflict Dependency Cascade:
     * Shifts [anchorTask] by [deltaDays] and pushes open downstream tasks (whose `blockedByTaskIds`
     * reference an upstream shifted task) forward ONLY when the blocker's new end date overlaps or
     * passes the dependent task's start date (`blockerEndDay >= depStartDay`), preserving existing slack.
     */
    fun computeSmartDependencyCascade(
        anchorTask: TaskItem,
        deltaDays: Long,
        projectTasks: List<TaskItem>,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<CascadedTaskShift> {
        if (deltaDays == 0L) return emptyList()

        val taskById = LinkedHashMap<String, TaskItem>()
        taskById[anchorTask.id] = anchorTask
        for (t in projectTasks) {
            if (!t.isDeleted && t.status != TaskStatus.CANCELED) {
                taskById[t.id] = t
            }
        }

        val dependentsByBlocker = HashMap<String, MutableList<TaskItem>>()
        for (t in taskById.values) {
            if (t.status != TaskStatus.OPEN) continue
            for (blockerId in t.blockedByTaskIds) {
                val clean = blockerId.trim()
                if (clean.isNotEmpty() && clean != t.id) {
                    dependentsByBlocker.getOrPut(clean) { mutableListOf() }.add(t)
                }
            }
        }

        val appliedDeltaByTaskId = LinkedHashMap<String, Long>()
        appliedDeltaByTaskId[anchorTask.id] = deltaDays

        // Only forward shifts or newly scheduled anchors can push downstream tasks later
        val baseSpanByTaskId = HashMap<String, Pair<Long, Long>>()
        for (t in taskById.values) {
            val span = resolveRawTaskSpanEpochDays(t, zoneId)
            if (span != null) {
                baseSpanByTaskId[t.id] = span
            }
        }
        if (anchorTask.id !in baseSpanByTaskId) {
            val durDays = if (anchorTask.isMilestone) 1L else durationMinutesToDays(anchorTask.durationMinutes)
            baseSpanByTaskId[anchorTask.id] = todayEpochDay to (todayEpochDay + durDays - 1L)
        }

        val queue = ArrayDeque<String>()
        queue.addLast(anchorTask.id)
        var guard = 0
        val maxIterations = (taskById.size * 4).coerceAtLeast(16)

        while (queue.isNotEmpty() && guard < maxIterations) {
            guard++
            val currentId = queue.removeFirst()
            val curBase = baseSpanByTaskId[currentId] ?: continue
            val curDelta = appliedDeltaByTaskId[currentId] ?: 0L
            val curShiftedEnd = curBase.second + curDelta

            val dependents = dependentsByBlocker[currentId].orEmpty()
            for (dep in dependents) {
                if (dep.id == anchorTask.id) continue
                val depBase = baseSpanByTaskId[dep.id] ?: continue
                val existingDepDelta = appliedDeltaByTaskId[dep.id] ?: 0L
                val depEffectiveStart = depBase.first + existingDepDelta
                // Conflict occurs when blocker ends on or after dependent's start day
                if (curShiftedEnd >= depEffectiveStart) {
                    val pushNeeded = (curShiftedEnd + 1L) - depEffectiveStart
                    val nextDepDelta = existingDepDelta + pushNeeded
                    if (nextDepDelta > existingDepDelta) {
                        appliedDeltaByTaskId[dep.id] = nextDepDelta
                        queue.addLast(dep.id)
                    }
                }
            }
        }

        val results = mutableListOf<CascadedTaskShift>()
        for ((taskId, taskDelta) in appliedDeltaByTaskId) {
            if (taskDelta == 0L) continue
            val targetTask = taskById[taskId] ?: continue
            val (nextState, nextWhen, nextDeadline) = shiftTaskDatesByDays(
                task = targetTask,
                deltaDays = taskDelta,
                todayEpochDay = todayEpochDay,
                zoneId = zoneId
            )
            results += CascadedTaskShift(
                task = targetTask,
                deltaDays = taskDelta,
                nextScheduleState = nextState,
                nextWhenMillis = nextWhen,
                nextDeadlineMillis = nextDeadline,
                isAnchor = taskId == anchorTask.id
            )
        }
        return results
    }

    fun computeCascadeDeltaPreviewMap(
        anchorTask: TaskItem,
        deltaDays: Long,
        projectTasks: List<TaskItem>,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<String, Long> {
        if (deltaDays == 0L) return emptyMap()
        return computeSmartDependencyCascade(
            anchorTask = anchorTask,
            deltaDays = deltaDays,
            projectTasks = projectTasks,
            todayEpochDay = todayEpochDay,
            zoneId = zoneId
        ).associate { "task_${it.task.id}" to it.deltaDays }
    }

    fun buildAreaPortfolioRoadmapLayout(
        area: Area,
        projectsInArea: List<Project>,
        allHeadings: List<Heading>,
        allTasks: List<TaskItem>,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AreaPortfolioRoadmapLayout {
        val activeProjects = projectsInArea
            .filter { !it.isDeleted && it.status == ProjectStatus.ACTIVE && it.areaId == area.id }
            .sortedWith(compareBy<Project> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })

        val headingsByProject = allHeadings
            .filter { !it.isDeleted && !it.isArchived }
            .groupBy { it.projectId }
        val tasksByProject = allTasks
            .filter { !it.isDeleted && it.status != TaskStatus.CANCELED && it.projectId != null }
            .groupBy { it.projectId!! }

        val projectLanes = mutableListOf<AreaPortfolioProjectLane>()
        val unscheduledProjects = mutableListOf<Project>()

        for (proj in activeProjects) {
            val projHeadings = headingsByProject[proj.id].orEmpty()
            val projTasks = tasksByProject[proj.id].orEmpty()
            val subLayout = buildRoadmapLayout(
                project = proj,
                headings = projHeadings,
                tasks = projTasks,
                todayEpochDay = todayEpochDay,
                zoneId = zoneId
            )

            val candidateStarts = mutableListOf<Long>()
            val candidateEnds = mutableListOf<Long>()
            subLayout.phaseBars.forEach {
                candidateStarts += it.startEpochDay
                candidateEnds += it.endEpochDay
            }
            subLayout.taskBars.forEach {
                candidateStarts += it.startEpochDay
                candidateEnds += it.endEpochDay
            }
            proj.whenDate?.let { candidateStarts += epochMillisToEpochDay(it, zoneId) }
            proj.deadline?.let { candidateEnds += epochMillisToEpochDay(it, zoneId) }

            if (candidateStarts.isEmpty() && candidateEnds.isEmpty()) {
                unscheduledProjects += proj
            } else {
                val rawMin = (candidateStarts + candidateEnds).minOrNull() ?: todayEpochDay
                val rawMax = (candidateEnds + candidateStarts).maxOrNull() ?: rawMin
                val startDay = minOf(rawMin, rawMax)
                val endDay = maxOf(startDay, rawMax)
                val milestones = subLayout.taskBars.filter { it.isMilestone }
                projectLanes += AreaPortfolioProjectLane(
                    project = proj,
                    projectLayout = subLayout,
                    summaryStartEpochDay = startDay,
                    summaryEndEpochDay = endDay,
                    milestoneBars = milestones
                )
            }
        }

        val minScheduled = projectLanes.minOfOrNull { it.summaryStartEpochDay }
        val maxScheduled = projectLanes.maxOfOrNull { it.summaryEndEpochDay }
        val defaultStart = todayEpochDay - LEADING_BUFFER_DAYS
        val windowStart = if (minScheduled != null && minScheduled < defaultStart) {
            minScheduled - 1L
        } else {
            defaultStart
        }
        val minRequiredEnd = windowStart + MIN_TIMELINE_DAYS - 1L
        val rawEnd = if (maxScheduled != null) {
            maxOf(minRequiredEnd, maxScheduled + TRAILING_BUFFER_DAYS)
        } else {
            minRequiredEnd
        }
        val maxAllowedEnd = maxOf(
            minRequiredEnd,
            LocalDate.ofEpochDay(maxScheduled ?: todayEpochDay).plusMonths(2).toEpochDay()
        )
        val windowEnd = minOf(rawEnd, maxAllowedEnd)

        return AreaPortfolioRoadmapLayout(
            area = area,
            startEpochDay = windowStart,
            endEpochDay = windowEnd,
            todayEpochDay = todayEpochDay,
            projectLanes = projectLanes,
            unscheduledProjects = unscheduledProjects
        )
    }

    /**
     * Converts an [AreaPortfolioRoadmapLayout] into a standard [ProjectRoadmapLayout] so multi-page
     * Vector PDF and High-DPI PNG export (`RoadmapExporter`) render all scheduled Area projects,
     * milestones (◆), task spans, and dependencies seamlessly.
     */
    fun toExportablePortfolioLayout(
        portfolioLayout: AreaPortfolioRoadmapLayout,
        expandedProjectIds: Set<String> = emptySet(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Triple<Project, List<Heading>, ProjectRoadmapLayout> {
        val totalTasks = portfolioLayout.projectLanes.sumOf { it.project.totalTaskCount }
        val doneTasks = portfolioLayout.projectLanes.sumOf { it.project.completedTaskCount }
        val maxDeadline = portfolioLayout.projectLanes.mapNotNull { it.project.deadline }.maxOrNull()

        val syntheticProject = Project(
            id = "area_export_${portfolioLayout.area.id}",
            areaId = portfolioLayout.area.id,
            title = portfolioLayout.area.title,
            notes = "",
            status = ProjectStatus.ACTIVE,
            scheduleState = ScheduleState.ANYTIME,
            whenDate = epochDayToStartOfDayMillis(portfolioLayout.startEpochDay, zoneId),
            deadline = maxDeadline,
            completedAt = null,
            sortOrder = portfolioLayout.area.sortOrder,
            tagIds = portfolioLayout.area.tagIds,
            viewMode = "roadmap",
            totalTaskCount = totalTasks,
            completedTaskCount = doneTasks,
            createdAt = portfolioLayout.area.createdAt,
            updatedAt = portfolioLayout.area.updatedAt
        )

        val syntheticHeadings = ArrayList<Heading>(portfolioLayout.projectLanes.size)
        val lanes = ArrayList<RoadmapLane>(portfolioLayout.projectLanes.size)
        val phaseBars = ArrayList<RoadmapPhaseBar>(portfolioLayout.projectLanes.size)
        val taskBars = ArrayList<RoadmapTaskBar>()
        val depEdges = ArrayList<RoadmapDependencyEdge>()

        portfolioLayout.projectLanes.forEachIndexed { idx, pLane ->
            val hStartMs = epochDayToStartOfDayMillis(pLane.summaryStartEpochDay, zoneId)
            val hEndMs = epochDayToStartOfDayMillis(pLane.summaryEndEpochDay, zoneId)
            val synHeading = Heading(
                id = pLane.projectId,
                projectId = syntheticProject.id,
                title = pLane.project.title,
                startDate = hStartMs,
                endDate = hEndMs,
                sortOrder = (idx + 1) * 100.0,
                createdAt = pLane.project.createdAt,
                updatedAt = pLane.project.updatedAt
            )
            syntheticHeadings += synHeading

            val pb = RoadmapPhaseBar(
                heading = synHeading,
                startEpochDay = pLane.summaryStartEpochDay,
                endEpochDay = pLane.summaryEndEpochDay,
                hasExplicitDates = true
            )
            phaseBars += pb

            val isExpanded = pLane.projectId in expandedProjectIds
            val sourceBars = if (isExpanded) pLane.projectLayout.taskBars else pLane.milestoneBars
            val remappedBars = sourceBars.map { tb ->
                tb.copy(headingId = synHeading.id)
            }
            taskBars += remappedBars
            if (isExpanded) {
                depEdges += pLane.projectLayout.dependencyEdges
            }
            lanes += RoadmapLane(
                heading = synHeading,
                phaseBar = pb,
                taskBars = remappedBars,
                unscheduledTasks = emptyList()
            )
        }

        val exportLayout = ProjectRoadmapLayout(
            project = syntheticProject,
            startEpochDay = portfolioLayout.startEpochDay,
            endEpochDay = portfolioLayout.endEpochDay,
            todayEpochDay = portfolioLayout.todayEpochDay,
            lanes = lanes,
            phaseBars = phaseBars,
            taskBars = taskBars,
            dependencyEdges = depEdges,
            unscheduledHeadings = emptyList(),
            unscheduledTasks = emptyList()
        )
        return Triple(syntheticProject, syntheticHeadings, exportLayout)
    }
}
