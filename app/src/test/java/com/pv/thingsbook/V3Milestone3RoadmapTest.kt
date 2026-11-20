package com.pv.thingsbook

import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.PdfPageOrientation
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.ProjectRoadmapEngine
import com.pv.thingsbook.domain.model.ProjectStatus
import com.pv.thingsbook.domain.model.ProjectWorkspaceMode
import com.pv.thingsbook.domain.model.RoadmapPdfExportConfig
import com.pv.thingsbook.domain.model.RoadmapPdfTimelinePreset
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.xml.parsers.DocumentBuilderFactory

class V3Milestone3RoadmapTest {

    private val berlinZone: ZoneId = ZoneId.of("Europe/Berlin")
    private val sampleProject = Project(
        id = "proj-m3",
        areaId = "area-work",
        title = "Thingsbook 3.0 Launch",
        notes = "",
        status = ProjectStatus.ACTIVE,
        scheduleState = ScheduleState.ANYTIME,
        whenDate = null,
        deadline = null,
        completedAt = null,
        sortOrder = 1.0,
        tagIds = emptyList(),
        viewMode = "roadmap",
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private fun dayMillis(date: LocalDate, zone: ZoneId = berlinZone): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun makeHeading(
        id: String,
        title: String,
        sortOrder: Double = 1.0,
        startDate: Long? = null,
        endDate: Long? = null
    ) = Heading(
        id = id,
        projectId = sampleProject.id,
        title = title,
        startDate = startDate,
        endDate = endDate,
        sortOrder = sortOrder,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private fun makeTask(
        id: String,
        title: String = id,
        headingId: String? = null,
        status: TaskStatus = TaskStatus.OPEN,
        scheduleState: ScheduleState = ScheduleState.ANYTIME,
        whenDate: Long? = null,
        deadline: Long? = null,
        durationMinutes: Int? = null,
        blockedByTaskIds: List<String> = emptyList(),
        sortOrder: Double = 1.0
    ) = TaskItem(
        id = id,
        title = title,
        notes = "",
        status = status,
        scheduleState = scheduleState,
        whenDate = whenDate,
        deadline = deadline,
        reminderTime = null,
        durationMinutes = durationMinutes,
        completedAt = if (status == TaskStatus.COMPLETED) 2000L else null,
        projectId = sampleProject.id,
        headingId = headingId,
        areaId = null,
        recurrenceRule = null,
        sortOrder = sortOrder,
        blockedByTaskIds = blockedByTaskIds,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun `ProjectWorkspaceMode fromRaw parses list, board, roadmap and gantt aliases safely`() {
        assertEquals(ProjectWorkspaceMode.LIST, ProjectWorkspaceMode.fromRaw(null))
        assertEquals(ProjectWorkspaceMode.LIST, ProjectWorkspaceMode.fromRaw(""))
        assertEquals(ProjectWorkspaceMode.LIST, ProjectWorkspaceMode.fromRaw("list"))
        assertEquals(ProjectWorkspaceMode.LIST, ProjectWorkspaceMode.fromRaw("unknown_mode"))
        assertEquals(ProjectWorkspaceMode.BOARD, ProjectWorkspaceMode.fromRaw("board"))
        assertEquals(ProjectWorkspaceMode.BOARD, ProjectWorkspaceMode.fromRaw("  BOARD  "))
        assertEquals(ProjectWorkspaceMode.BOARD, ProjectWorkspaceMode.fromRaw("kanban"))
        assertEquals(ProjectWorkspaceMode.ROADMAP, ProjectWorkspaceMode.fromRaw("roadmap"))
        assertEquals(ProjectWorkspaceMode.ROADMAP, ProjectWorkspaceMode.fromRaw("Gantt"))
        assertEquals("list", ProjectWorkspaceMode.LIST.storageKey)
        assertEquals("board", ProjectWorkspaceMode.BOARD.storageKey)
        assertEquals("roadmap", ProjectWorkspaceMode.ROADMAP.storageKey)
    }

    @Test
    fun `buildRoadmapLayout enforces 21-day minimum window and covers today minus 3 through maxScheduled plus 7`() {
        val today = LocalDate.of(2026, 6, 15)
        val todayEpoch = today.toEpochDay()

        val emptyLayout = ProjectRoadmapEngine.buildRoadmapLayout(
            project = sampleProject,
            headings = emptyList(),
            tasks = emptyList(),
            todayEpochDay = todayEpoch,
            zoneId = berlinZone
        )
        assertTrue(emptyLayout.totalDays >= 21)
        assertEquals(todayEpoch - 3L, emptyLayout.startEpochDay)
        assertEquals(todayEpoch - 3L + 20L, emptyLayout.endEpochDay)

        val farFutureDay = today.plusDays(30)
        val farTask = makeTask(
            id = "t-far",
            scheduleState = ScheduleState.UPCOMING,
            whenDate = dayMillis(farFutureDay)
        )
        val expandedLayout = ProjectRoadmapEngine.buildRoadmapLayout(
            project = sampleProject,
            headings = emptyList(),
            tasks = listOf(farTask),
            todayEpochDay = todayEpoch,
            zoneId = berlinZone
        )
        assertEquals(todayEpoch - 3L, expandedLayout.startEpochDay)
        assertEquals(farFutureDay.toEpochDay() + 7L, expandedLayout.endEpochDay)
        assertTrue(expandedLayout.totalDays >= 21)
    }

    @Test
    fun `buildRoadmapLayout computes Phase bars for explicit, clamped, single-bound, child-inferred and unscheduled headings`() {
        val today = LocalDate.of(2026, 7, 10)
        val todayEpoch = today.toEpochDay()

        val hExplicitClamped = makeHeading(
            id = "h-clamped",
            title = "Inverted Bounds Phase",
            sortOrder = 1.0,
            startDate = dayMillis(today.plusDays(5)),
            endDate = dayMillis(today.plusDays(2)) // Inverted -> clamped to startDay
        )
        val hSingleStart = makeHeading(
            id = "h-single",
            title = "Single Start Phase",
            sortOrder = 2.0,
            startDate = dayMillis(today.plusDays(1)),
            endDate = null
        )
        val hInferred = makeHeading(
            id = "h-inferred",
            title = "Inferred From Children",
            sortOrder = 3.0,
            startDate = null,
            endDate = null
        )
        val hUnscheduled = makeHeading(
            id = "h-unscheduled",
            title = "Backlog Phase",
            sortOrder = 4.0,
            startDate = null,
            endDate = null
        )

        val child1 = makeTask(
            id = "c1",
            headingId = "h-inferred",
            scheduleState = ScheduleState.UPCOMING,
            whenDate = dayMillis(today.plusDays(3))
        )
        val child2 = makeTask(
            id = "c2",
            headingId = "h-inferred",
            deadline = dayMillis(today.plusDays(8))
        )
        val unscheduledChild = makeTask(
            id = "c-unsched",
            headingId = "h-unscheduled"
        )

        val layout = ProjectRoadmapEngine.buildRoadmapLayout(
            project = sampleProject,
            headings = listOf(hExplicitClamped, hSingleStart, hInferred, hUnscheduled),
            tasks = listOf(child1, child2, unscheduledChild),
            todayEpochDay = todayEpoch,
            zoneId = berlinZone
        )

        val clampedBar = layout.lanes.first { it.heading?.id == "h-clamped" }.phaseBar!!
        assertTrue(clampedBar.hasExplicitDates)
        assertEquals(today.plusDays(5).toEpochDay(), clampedBar.startEpochDay)
        assertEquals(today.plusDays(5).toEpochDay(), clampedBar.endEpochDay)

        val singleBar = layout.lanes.first { it.heading?.id == "h-single" }.phaseBar!!
        assertTrue(singleBar.hasExplicitDates)
        assertEquals(today.plusDays(1).toEpochDay(), singleBar.startEpochDay)
        assertEquals(today.plusDays(1).toEpochDay(), singleBar.endEpochDay)

        val inferredBar = layout.lanes.first { it.heading?.id == "h-inferred" }.phaseBar!!
        assertFalse(inferredBar.hasExplicitDates)
        assertEquals(today.plusDays(3).toEpochDay(), inferredBar.startEpochDay)
        assertEquals(today.plusDays(8).toEpochDay(), inferredBar.endEpochDay)

        assertNull(layout.lanes.firstOrNull { it.heading?.id == "h-unscheduled" })
        assertEquals(listOf("h-unscheduled"), layout.unscheduledHeadings.map { it.id })
        assertEquals(listOf("c-unsched"), layout.unscheduledTasks.map { it.id })
    }

    @Test
    fun `buildRoadmapLayout computes all 4 Task bar When and Deadline duration combinations plus dependency edges`() {
        val today = LocalDate.of(2026, 8, 1)
        val todayEpoch = today.toEpochDay()

        // 1. Both When & Deadline -> [whenDay, max(whenDay, deadlineDay)]
        val tBoth = makeTask(
            id = "t-both",
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.TODAY,
            whenDate = dayMillis(today),
            deadline = dayMillis(today.plusDays(4)),
            sortOrder = 1.0
        )
        // 2. When only with 960m (2 workdays = 960 / 480) -> [whenDay, whenDay + 1]
        val tWhenOnly = makeTask(
            id = "t-when",
            status = TaskStatus.COMPLETED,
            scheduleState = ScheduleState.UPCOMING,
            whenDate = dayMillis(today.plusDays(5)),
            durationMinutes = 960,
            blockedByTaskIds = listOf("t-both"),
            sortOrder = 2.0
        )
        // 3. Deadline only with 961m (3 workdays = (961 + 479) / 480 = 3) -> [deadlineDay - 2, deadlineDay]
        val tDeadlineOnly = makeTask(
            id = "t-deadline",
            status = TaskStatus.OPEN,
            deadline = dayMillis(today.plusDays(10)),
            durationMinutes = 961,
            blockedByTaskIds = listOf("t-when", "t-unscheduled"),
            sortOrder = 3.0
        )
        // 4. Neither -> unscheduledTasks
        val tUnscheduled = makeTask(
            id = "t-unscheduled",
            status = TaskStatus.OPEN,
            sortOrder = 4.0
        )

        val layout = ProjectRoadmapEngine.buildRoadmapLayout(
            project = sampleProject,
            headings = emptyList(),
            tasks = listOf(tBoth, tWhenOnly, tDeadlineOnly, tUnscheduled),
            todayEpochDay = todayEpoch,
            zoneId = berlinZone
        )

        val barsById = layout.lanes.single().taskBars.associateBy { it.task.id }
        assertEquals(3, barsById.size)

        val barBoth = barsById.getValue("t-both")
        assertEquals(todayEpoch, barBoth.startEpochDay)
        assertEquals(todayEpoch + 4L, barBoth.endEpochDay)
        assertEquals(5, barBoth.spanDays)

        val barWhen = barsById.getValue("t-when")
        assertEquals(todayEpoch + 5L, barWhen.startEpochDay)
        assertEquals(todayEpoch + 6L, barWhen.endEpochDay)
        assertEquals(2, barWhen.spanDays)

        val barDeadline = barsById.getValue("t-deadline")
        assertEquals(todayEpoch + 8L, barDeadline.startEpochDay)
        assertEquals(todayEpoch + 10L, barDeadline.endEpochDay)
        assertEquals(3, barDeadline.spanDays)

        assertEquals(listOf("t-unscheduled"), layout.unscheduledTasks.map { it.id })

        // Dependency edges: t-both (OPEN) -> t-when (actively blocking = true),
        // t-when (COMPLETED) -> t-deadline (actively blocking = false),
        // t-unscheduled -> t-deadline ignored because t-unscheduled is not plotted!
        assertEquals(2, layout.dependencyEdges.size)
        val edge1 = layout.dependencyEdges.first { it.fromTaskId == "t-both" && it.toTaskId == "t-when" }
        assertTrue(edge1.isActivelyBlocking)
        val edge2 = layout.dependencyEdges.first { it.fromTaskId == "t-when" && it.toTaskId == "t-deadline" }
        assertFalse(edge2.isActivelyBlocking)
    }

    @Test
    fun `shiftHeadingDatesByDays and shiftTaskDatesByDays are DST-safe and preserve THIS_EVENING on today`() {
        // 2026-03-29 is Europe/Berlin Spring DST transition day (23-hour day)
        val beforeDst = LocalDate.of(2026, 3, 28)
        val afterDst = LocalDate.of(2026, 3, 30)

        val heading = makeHeading(
            id = "h-dst",
            title = "DST Phase",
            sortOrder = 1.0,
            startDate = dayMillis(beforeDst),
            endDate = dayMillis(beforeDst.plusDays(1))
        )
        val (shiftedStart, shiftedEnd) = ProjectRoadmapEngine.shiftHeadingDatesByDays(
            heading = heading,
            deltaDays = 2L,
            zoneId = berlinZone
        )
        assertEquals(afterDst.toEpochDay(), ProjectRoadmapEngine.epochMillisToEpochDay(shiftedStart!!, berlinZone))
        assertEquals(afterDst.plusDays(1).toEpochDay(), ProjectRoadmapEngine.epochMillisToEpochDay(shiftedEnd!!, berlinZone))

        // Task shifting onto today preserves THIS_EVENING if already THIS_EVENING, else TODAY; future -> UPCOMING
        val todayEpoch = afterDst.toEpochDay()
        val eveningTask = makeTask(
            id = "t-eve",
            scheduleState = ScheduleState.THIS_EVENING,
            whenDate = dayMillis(afterDst.plusDays(1)),
            deadline = dayMillis(afterDst.plusDays(3))
        )
        val (stateOnToday, whenOnToday, dlOnToday) = ProjectRoadmapEngine.shiftTaskDatesByDays(
            task = eveningTask,
            deltaDays = -1L,
            todayEpochDay = todayEpoch,
            zoneId = berlinZone
        )
        assertEquals(ScheduleState.THIS_EVENING, stateOnToday)
        assertEquals(todayEpoch, ProjectRoadmapEngine.epochMillisToEpochDay(whenOnToday!!, berlinZone))
        assertEquals(todayEpoch + 2L, ProjectRoadmapEngine.epochMillisToEpochDay(dlOnToday!!, berlinZone))

        val (stateFuture, whenFuture, _) = ProjectRoadmapEngine.shiftTaskDatesByDays(
            task = eveningTask,
            deltaDays = 2L,
            todayEpochDay = todayEpoch,
            zoneId = berlinZone
        )
        assertEquals(ScheduleState.UPCOMING, stateFuture)
        assertEquals(todayEpoch + 3L, ProjectRoadmapEngine.epochMillisToEpochDay(whenFuture!!, berlinZone))

        // Unscheduled task initializes whenDate to today + deltaDays
        val unscheduled = makeTask(id = "t-init", scheduleState = ScheduleState.ANYTIME)
        val (initState, initWhen, initDl) = ProjectRoadmapEngine.shiftTaskDatesByDays(
            task = unscheduled,
            deltaDays = 0L,
            todayEpochDay = todayEpoch,
            zoneId = berlinZone
        )
        assertEquals(ScheduleState.TODAY, initState)
        assertEquals(todayEpoch, ProjectRoadmapEngine.epochMillisToEpochDay(initWhen!!, berlinZone))
        assertNull(initDl)
    }

    @Test
    fun `buildRoadmapLayout caps endEpochDay at at most 1 month after lastTaskEndDay even when project deadline and phase endDate are months away`() {
        val today = LocalDate.of(2026, 11, 20)
        val todayEpoch = today.toEpochDay()

        val projectWithFarDeadline = sampleProject.copy(
            deadline = dayMillis(LocalDate.of(2027, 4, 30))
        )
        val phase1 = makeHeading(
            id = "h-p1",
            title = "Technische Umsetzung",
            sortOrder = 1.0,
            startDate = dayMillis(LocalDate.of(2026, 11, 20)),
            endDate = dayMillis(LocalDate.of(2026, 12, 5))
        )
        val phase4 = makeHeading(
            id = "h-p4",
            title = "Betriebsübergabe und Langzeitbegleitung",
            sortOrder = 2.0,
            startDate = dayMillis(LocalDate.of(2026, 12, 10)),
            endDate = dayMillis(LocalDate.of(2027, 2, 15))
        )
        val task1 = makeTask(
            id = "t-1",
            headingId = "h-p1",
            scheduleState = ScheduleState.TODAY,
            whenDate = dayMillis(LocalDate.of(2026, 11, 20)),
            deadline = dayMillis(LocalDate.of(2026, 11, 28)),
            sortOrder = 1.0
        )
        val lastTask = makeTask(
            id = "t-last",
            headingId = "h-p4",
            scheduleState = ScheduleState.UPCOMING,
            whenDate = dayMillis(LocalDate.of(2026, 12, 12)),
            deadline = dayMillis(LocalDate.of(2026, 12, 18)),
            sortOrder = 2.0
        )

        val layout = ProjectRoadmapEngine.buildRoadmapLayout(
            project = projectWithFarDeadline,
            headings = listOf(phase1, phase4),
            tasks = listOf(task1, lastTask),
            todayEpochDay = todayEpoch,
            zoneId = berlinZone
        )

        // Last task due date = 2026-12-18 -> 1-month cap is strictly 2027-01-18 (not Feb 2027 or Apr/May 2027!)
        val expectedOneMonthCap = LocalDate.of(2027, 1, 18).toEpochDay()
        assertEquals(todayEpoch - 3L, layout.startEpochDay)
        assertEquals(expectedOneMonthCap, layout.endEpochDay)
        assertTrue(layout.endEpochDay <= expectedOneMonthCap)

        // When project has zero scheduled tasks, far-future phase endDate and project deadline are capped at today + 1 month
        val tasksEmptyLayout = ProjectRoadmapEngine.buildRoadmapLayout(
            project = projectWithFarDeadline,
            headings = listOf(phase4),
            tasks = emptyList(),
            todayEpochDay = todayEpoch,
            zoneId = berlinZone
        )
        assertEquals(today.plusMonths(1).toEpochDay(), tasksEmptyLayout.endEpochDay)
    }

    @Test
    fun `filterLayoutForExport filters selected phases, clamps timeline bounds, prunes excluded dependency edges, and verifies A4 PdfPageOrientation`() {
        assertEquals(842, PdfPageOrientation.LANDSCAPE.pageWidthPt)
        assertEquals(595, PdfPageOrientation.LANDSCAPE.pageHeightPt)
        assertEquals(595, PdfPageOrientation.PORTRAIT.pageWidthPt)
        assertEquals(842, PdfPageOrientation.PORTRAIT.pageHeightPt)
        assertEquals(
            listOf(
                RoadmapPdfTimelinePreset.FULL_PROJECT,
                RoadmapPdfTimelinePreset.FROM_TODAY,
                RoadmapPdfTimelinePreset.NEXT_30_DAYS,
                RoadmapPdfTimelinePreset.CUSTOM
            ),
            RoadmapPdfTimelinePreset.entries
        )

        val today = LocalDate.of(2026, 9, 10)
        val todayEpoch = today.toEpochDay()

        val headingA = makeHeading(
            id = "h-a",
            title = "Technische Umsetzung",
            sortOrder = 1.0,
            startDate = dayMillis(today.minusDays(2)),
            endDate = dayMillis(today.plusDays(25))
        )
        val headingB = makeHeading(
            id = "h-b",
            title = "Betriebsübergabe",
            sortOrder = 2.0,
            startDate = dayMillis(today.plusDays(4)),
            endDate = dayMillis(today.plusDays(18))
        )

        val tGen = makeTask(
            id = "t-gen",
            headingId = null,
            scheduleState = ScheduleState.TODAY,
            whenDate = dayMillis(today)
        )
        val tA1 = makeTask(
            id = "t-a1",
            headingId = "h-a",
            scheduleState = ScheduleState.TODAY,
            whenDate = dayMillis(today.minusDays(1)),
            deadline = dayMillis(today.plusDays(5)),
            sortOrder = 1.0
        )
        val tA2 = makeTask(
            id = "t-a2",
            headingId = "h-a",
            scheduleState = ScheduleState.UPCOMING,
            whenDate = dayMillis(today.plusDays(6)),
            deadline = dayMillis(today.plusDays(12)),
            blockedByTaskIds = listOf("t-a1"),
            sortOrder = 2.0
        )
        val tAOutside = makeTask(
            id = "t-a-outside",
            headingId = "h-a",
            scheduleState = ScheduleState.UPCOMING,
            whenDate = dayMillis(today.plusDays(20)),
            deadline = dayMillis(today.plusDays(22)),
            blockedByTaskIds = listOf("t-a2"),
            sortOrder = 3.0
        )
        val tB1 = makeTask(
            id = "t-b1",
            headingId = "h-b",
            scheduleState = ScheduleState.UPCOMING,
            whenDate = dayMillis(today.plusDays(5)),
            deadline = dayMillis(today.plusDays(9)),
            blockedByTaskIds = listOf("t-a1"),
            sortOrder = 1.0
        )

        val fullLayout = ProjectRoadmapEngine.buildRoadmapLayout(
            project = sampleProject,
            headings = listOf(headingA, headingB),
            tasks = listOf(tGen, tA1, tA2, tAOutside, tB1),
            todayEpochDay = todayEpoch,
            zoneId = berlinZone
        )
        assertEquals(3, fullLayout.dependencyEdges.size)

        val customStart = todayEpoch + 1L
        val customEnd = todayEpoch + 10L
        val config = RoadmapPdfExportConfig(
            selectedLaneKeys = setOf("h-a"),
            startEpochDay = customStart,
            endEpochDay = customEnd,
            orientation = PdfPageOrientation.PORTRAIT
        )
        val filtered = ProjectRoadmapEngine.filterLayoutForExport(fullLayout, config)

        assertEquals(customStart, filtered.startEpochDay)
        assertEquals(customEnd, filtered.endEpochDay)
        assertEquals(10, filtered.totalDays)
        assertEquals(listOf("h-a"), filtered.lanes.map { it.laneKey })

        val clampedPhase = filtered.lanes.single().phaseBar!!
        assertEquals(customStart, clampedPhase.startEpochDay)
        assertEquals(customEnd, clampedPhase.endEpochDay)
        assertEquals(10, clampedPhase.spanDays)

        val keptBarsById = filtered.taskBars.associateBy { it.taskId }
        assertEquals(setOf("t-a1", "t-a2"), keptBarsById.keys)
        assertEquals(customStart, keptBarsById.getValue("t-a1").startEpochDay)
        assertEquals(todayEpoch + 5L, keptBarsById.getValue("t-a1").endEpochDay)
        assertEquals(todayEpoch + 6L, keptBarsById.getValue("t-a2").startEpochDay)
        assertEquals(customEnd, keptBarsById.getValue("t-a2").endEpochDay)

        // Only intra-selected-window edge t-a1 -> t-a2 remains; t-a2 -> t-a-outside and t-a1 -> t-b1 are pruned!
        assertEquals(1, filtered.dependencyEdges.size)
        assertEquals("t-a1", filtered.dependencyEdges.single().fromTaskId)
        assertEquals("t-a2", filtered.dependencyEdges.single().toTaskId)
    }

    @Test
    fun `Milestone 3 and v3_0_0_alpha_13 PDF export strings_xml keys exist across all 5 supported locales`() {
        val resRoot = sequenceOf(
            File("src/main/res"),
            File("app/src/main/res")
        ).first { it.isDirectory }

        val folders = listOf("values", "values-de", "values-fr", "values-it", "values-es")
        val milestone3Keys = listOf(
            "v30_project_mode_list",
            "v30_project_mode_board",
            "v30_project_mode_roadmap",
            "v30_board_ungrouped_column",
            "v30_board_add_phase",
            "v30_phase_set_start_date",
            "v30_phase_clear_start_date",
            "v30_phase_starts_on",
            "phase_set_end_date",
            "phase_clear_end_date",
            "phase_ends_on",
            "v30_roadmap_export",
            "v30_roadmap_export_pdf",
            "v30_roadmap_export_png",
            "v30_roadmap_jump_today",
            "v30_roadmap_unscheduled_section",
            "v30_roadmap_unscheduled_hint",
            "v30_roadmap_shift_earlier",
            "v30_roadmap_shift_later",
            "v30_drag_multi_count",
            "v30_drop_moved_feedback",
            "v30_pdf_export_dialog_title",
            "v30_pdf_phases_label",
            "v30_pdf_select_all_phases",
            "v30_pdf_timeline_label",
            "v30_pdf_timeline_auto",
            "v30_pdf_timeline_from_today",
            "v30_pdf_timeline_next_30d",
            "v30_pdf_timeline_custom",
            "v30_pdf_orientation_label",
            "v30_pdf_orientation_landscape",
            "v30_pdf_orientation_portrait"
        )

        val dbf = DocumentBuilderFactory.newInstance()
        for (folder in folders) {
            val doc = dbf.newDocumentBuilder().parse(File(resRoot, "$folder/strings.xml"))
            val nodes = doc.getElementsByTagName("string")
            val keys = HashMap<String, String>(nodes.length)
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i) as Element
                keys[elem.getAttribute("name")] = elem.textContent.orEmpty()
            }
            for (k in milestone3Keys) {
                val v = keys[k]
                assertNotNull("Missing $k in $folder/strings.xml", v)
                assertTrue("Blank $k in $folder/strings.xml", v!!.isNotBlank())
            }
        }
    }
}
