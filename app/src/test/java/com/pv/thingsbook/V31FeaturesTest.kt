package com.pv.thingsbook

import com.pv.thingsbook.data.ai.GeminiDayTimeblockCandidate
import com.pv.thingsbook.data.ai.ThingsbookGeminiService
import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.CalendarEventItem
import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.ProjectRoadmapEngine
import com.pv.thingsbook.domain.model.ProjectStatus
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.model.computeMultiDayWorkloadForecast
import com.pv.thingsbook.domain.model.computeTodayCapacitySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class V31FeaturesTest {

    private val berlinZone: ZoneId = ZoneId.of("Europe/Berlin")
    private val baseDate: LocalDate = LocalDate.of(2026, 11, 23)

    private fun dayMillis(date: LocalDate): Long =
        date.atStartOfDay(berlinZone).toInstant().toEpochMilli()

    private fun makeTask(
        id: String,
        projectId: String? = "proj-1",
        whenDate: Long? = null,
        deadline: Long? = null,
        durationMinutes: Int? = 60,
        blockedByTaskIds: List<String> = emptyList(),
        isMilestone: Boolean = false,
        scheduleState: ScheduleState = ScheduleState.UPCOMING
    ) = TaskItem(
        id = id,
        title = id,
        notes = "",
        status = TaskStatus.OPEN,
        scheduleState = scheduleState,
        whenDate = whenDate,
        deadline = deadline,
        reminderTime = null,
        durationMinutes = durationMinutes,
        completedAt = null,
        projectId = projectId,
        headingId = null,
        areaId = "area-1",
        recurrenceRule = null,
        sortOrder = 1.0,
        blockedByTaskIds = blockedByTaskIds,
        isMilestone = isMilestone,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun `computeSmartDependencyCascade pushes overlapping downstream chain and preserves non-overlapping slack`() {
        val todayDay = baseDate.toEpochDay()
        // Task A: Day 0 (1 day), Task B (blocked by A): Day 1..2 (2 days = 960m), Task C (blocked by B): Day 3, Task D (blocked by A): Day 10 (has slack!)
        val taskA = makeTask("A", whenDate = dayMillis(baseDate), durationMinutes = 240)
        val taskB = makeTask("B", whenDate = dayMillis(baseDate.plusDays(1)), durationMinutes = 960, blockedByTaskIds = listOf("A"))
        val taskC = makeTask("C", whenDate = dayMillis(baseDate.plusDays(3)), durationMinutes = 120, blockedByTaskIds = listOf("B"))
        val taskD = makeTask("D", whenDate = dayMillis(baseDate.plusDays(10)), durationMinutes = 120, blockedByTaskIds = listOf("A"))

        val shifts = ProjectRoadmapEngine.computeSmartDependencyCascade(
            anchorTask = taskA,
            deltaDays = 2L,
            projectTasks = listOf(taskA, taskB, taskC, taskD),
            todayEpochDay = todayDay,
            zoneId = berlinZone
        )

        val deltaById = shifts.associate { it.task.id to it.deltaDays }
        // A shifts +2d -> Day 2. B was Day 1..2 -> pushed +2d to Day 3..4. C was Day 3 -> pushed +2d to Day 5. D at Day 10 is untouched!
        assertEquals(2L, deltaById["A"])
        assertEquals(2L, deltaById["B"])
        assertEquals(2L, deltaById["C"])
        assertFalse("Task D has ample slack and must not be shifted", "D" in deltaById)
    }

    @Test
    fun `computeSmartDependencyCascade backward shift never pulls downstream tasks earlier and handles cycles safely`() {
        val todayDay = baseDate.toEpochDay()
        val taskA = makeTask("A", whenDate = dayMillis(baseDate.plusDays(2)), blockedByTaskIds = listOf("B"))
        val taskB = makeTask("B", whenDate = dayMillis(baseDate.plusDays(3)), blockedByTaskIds = listOf("A"))

        val backwardShifts = ProjectRoadmapEngine.computeSmartDependencyCascade(
            anchorTask = taskA,
            deltaDays = -2L,
            projectTasks = listOf(taskA, taskB),
            todayEpochDay = todayDay,
            zoneId = berlinZone
        )
        assertEquals(1, backwardShifts.size)
        assertEquals("A", backwardShifts.single().task.id)
        assertEquals(-2L, backwardShifts.single().deltaDays)

        // Forward shift with cycle terminates cleanly
        val forwardCycleShifts = ProjectRoadmapEngine.computeSmartDependencyCascade(
            anchorTask = taskA,
            deltaDays = 3L,
            projectTasks = listOf(taskA, taskB),
            todayEpochDay = todayDay,
            zoneId = berlinZone
        )
        assertEquals(2, forwardCycleShifts.size)
    }

    @Test
    fun `buildAreaPortfolioRoadmapLayout groups scheduled projects with milestones and separates unscheduled projects`() {
        val area = Area(
            id = "area-1",
            title = "Work",
            sortOrder = 1.0,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val projScheduled = Project(
            id = "proj-1",
            areaId = "area-1",
            title = "Q4 Release",
            notes = "",
            status = ProjectStatus.ACTIVE,
            scheduleState = ScheduleState.TODAY,
            whenDate = dayMillis(baseDate),
            deadline = dayMillis(baseDate.plusDays(12)),
            completedAt = null,
            sortOrder = 1.0,
            tagIds = emptyList(),
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val projUnscheduled = projScheduled.copy(
            id = "proj-2",
            title = "Backlog",
            whenDate = null,
            deadline = null,
            sortOrder = 2.0
        )
        val heading = Heading(
            id = "h-1",
            projectId = "proj-1",
            title = "Phase 1",
            startDate = dayMillis(baseDate),
            endDate = dayMillis(baseDate.plusDays(5)),
            sortOrder = 1.0,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val milestone = makeTask(
            id = "m-1",
            projectId = "proj-1",
            whenDate = dayMillis(baseDate.plusDays(5)),
            deadline = dayMillis(baseDate.plusDays(5)),
            isMilestone = true
        )

        val portfolio = ProjectRoadmapEngine.buildAreaPortfolioRoadmapLayout(
            area = area,
            projectsInArea = listOf(projScheduled, projUnscheduled),
            allHeadings = listOf(heading),
            allTasks = listOf(milestone),
            todayEpochDay = baseDate.toEpochDay(),
            zoneId = berlinZone
        )

        assertEquals(1, portfolio.projectLanes.size)
        assertEquals("proj-1", portfolio.projectLanes.single().projectId)
        assertEquals(1, portfolio.projectLanes.single().milestoneBars.size)
        assertEquals(baseDate.toEpochDay(), portfolio.projectLanes.single().summaryStartEpochDay)
        assertEquals(baseDate.plusDays(12).toEpochDay(), portfolio.projectLanes.single().summaryEndEpochDay)
        assertEquals(listOf("proj-2"), portfolio.unscheduledProjects.map { it.id })

        // Verify toExportablePortfolioLayout maps collapsed milestones & expanded tasks for PDF/PNG export
        val (synProj, synHeadings, collapsedExport) = ProjectRoadmapEngine.toExportablePortfolioLayout(
            portfolioLayout = portfolio,
            expandedProjectIds = emptySet(),
            zoneId = berlinZone
        )
        assertEquals("Work", synProj.title)
        assertEquals(1, synHeadings.size)
        assertEquals(1, collapsedExport.taskBars.size)
        assertTrue(collapsedExport.taskBars.single().isMilestone)
    }

    @Test
    fun `configurable workday bounds and 7-day workload forecast detect daily overload accurately`() {
        val dayStart = dayMillis(baseDate)
        val dayEnd = dayMillis(baseDate.plusDays(1))
        val todayTask = makeTask(
            id = "t-today",
            whenDate = dayStart,
            durationMinutes = 300, // 5h
            scheduleState = ScheduleState.TODAY
        )
        val tomorrowStart = dayMillis(baseDate.plusDays(1))
        val tomorrowTask = makeTask(
            id = "t-tmrw",
            whenDate = tomorrowStart,
            durationMinutes = 420, // 7h
            scheduleState = ScheduleState.UPCOMING
        )
        val tomorrowMeeting = CalendarEventItem(
            id = "cal-1",
            eventId = 1L,
            calendarId = "primary",
            calendarDisplayName = "Work",
            title = "Workshop",
            startMillis = tomorrowStart + 10L * 60L * 60_000L, // 10:00
            endMillis = tomorrowStart + 13L * 60L * 60_000L,   // 13:00 (3h)
            isAllDay = false,
            colorArgb = 0xFF248BFD.toInt(),
            startDateEpochDay = baseDate.plusDays(1).toEpochDay(),
            endDateInclusiveEpochDay = baseDate.plusDays(1).toEpochDay()
        )

        // Custom 9-hour workday: 08:00 (480) to 17:00 (1020) = 540 mins
        val (plannedToday, freeToday) = computeTodayCapacitySummary(
            tasks = listOf(todayTask),
            calendarEvents = emptyList(),
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            workdayStartMinutes = 8 * 60,
            workdayEndMinutes = 17 * 60
        )
        assertEquals(300, plannedToday)
        assertEquals(240, freeToday) // 540 - 300 = 240

        val forecast = computeMultiDayWorkloadForecast(
            todayTasks = listOf(todayTask),
            allOpenTasks = listOf(todayTask, tomorrowTask),
            todayCalendarEvents = emptyList(),
            upcomingCalendarEventsByEpochDay = mapOf(
                baseDate.plusDays(1).toEpochDay() to listOf(tomorrowMeeting)
            ),
            todayEpochDay = baseDate.toEpochDay(),
            daysCount = 7,
            workdayStartMinutes = 8 * 60,
            workdayEndMinutes = 17 * 60,
            zoneId = berlinZone
        )

        assertEquals(7, forecast.size)
        assertFalse(forecast[0].isOverloaded)
        // Tomorrow: 420m tasks + 180m calendar = 600m > 540m workday -> overloaded by 60m!
        assertTrue(forecast[1].isOverloaded)
        assertEquals(60, forecast[1].overloadedByMinutes)
    }

    @Test
    fun `deterministic Plan My Day auto-timeblocker schedules around calendar busy blocks across all 5 locales`() {
        val service = ThingsbookGeminiService()
        val candidates = listOf(
            GeminiDayTimeblockCandidate(
                taskId = "t1",
                title = "Deep Architecture Spec",
                durationMinutes = 90,
                effortLevel = "DEEP_FOCUS",
                isPinned = true,
                hasTodayDeadline = false
            ),
            GeminiDayTimeblockCandidate(
                taskId = "t2",
                title = "Quick Inbox Reply",
                durationMinutes = 30,
                effortLevel = "QUICK_WIN",
                isPinned = false,
                hasTodayDeadline = true
            )
        )
        // Busy meeting 09:00..10:00 (540..600)
        val busy = listOf(540 to 600)

        for (lang in listOf("en", "de", "fr", "it", "es")) {
            val plan = service.buildDeterministicDayTimeblockPlan(
                candidates = candidates,
                busyIntervalsMinutes = busy,
                workdayStartMinutes = 540,
                workdayEndMinutes = 1020,
                earliestStartMinutes = 540,
                locale = Locale.forLanguageTag(lang)
            )
            assertTrue(plan.summary.isNotBlank())
            assertEquals(2, plan.blocks.size)
            for (b in plan.blocks) {
                val bEnd = b.startMinutes + b.durationMinutes
                // Must not overlap 540..600
                assertFalse("Block must not overlap 09:00-10:00 meeting", b.startMinutes < 600 && 540 < bEnd)
                assertTrue(b.reason.isNotBlank())
            }
        }
    }
}
