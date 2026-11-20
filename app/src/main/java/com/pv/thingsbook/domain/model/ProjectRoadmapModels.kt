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
    val isActivelyBlocked: Boolean = false
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
                isActivelyBlocked = blocked
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
}
