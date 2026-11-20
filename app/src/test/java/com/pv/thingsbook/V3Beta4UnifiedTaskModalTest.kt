package com.pv.thingsbook

import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.ui.components.SOFT_BENTO_MODAL_WIDE_BREAKPOINT_DP
import com.pv.thingsbook.ui.components.isWideSoftBentoTaskModal
import com.pv.thingsbook.ui.screens.resolveUnifiedModalTask
import com.pv.thingsbook.ui.viewmodel.ActiveNavigationTarget
import com.pv.thingsbook.ui.components.SmartViewType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V3Beta4UnifiedTaskModalTest {

    private fun sampleTask(
        id: String,
        title: String,
        status: TaskStatus = TaskStatus.OPEN,
        scheduleState: ScheduleState = ScheduleState.INBOX,
        isDeleted: Boolean = false
    ): TaskItem = TaskItem(
        id = id,
        title = title,
        notes = "Markdown **notes**",
        status = status,
        scheduleState = scheduleState,
        whenDate = null,
        deadline = null,
        reminderTime = null,
        completedAt = if (status == TaskStatus.COMPLETED) 1_770_000_000_000L else null,
        projectId = null,
        headingId = null,
        areaId = null,
        recurrenceRule = null,
        sortOrder = 1.0,
        createdAt = 1_760_000_000_000L,
        updatedAt = 1_760_000_000_000L,
        isDeleted = isDeleted
    )

    @Test
    fun `resolveUnifiedModalTask resolves open, someday, evening, and completed logbook tasks while excluding deleted tasks`() {
        val inboxTask = sampleTask("t-inbox", "Triage email", ScheduleState.INBOX.let { TaskStatus.OPEN }, ScheduleState.INBOX)
        val eveningTask = sampleTask("t-evening", "Read chapter", TaskStatus.OPEN, ScheduleState.THIS_EVENING)
        val somedayTask = sampleTask("t-someday", "Learn sailing", TaskStatus.OPEN, ScheduleState.SOMEDAY)
        val logbookCompleted = sampleTask("t-logbook", "Shipped v3.0 beta", TaskStatus.COMPLETED, ScheduleState.TODAY)
        val deletedTrashTask = sampleTask("t-trash", "Discarded draft", TaskStatus.OPEN, ScheduleState.INBOX, isDeleted = true)

        assertNull(
            resolveUnifiedModalTask(
                expandedTaskId = null,
                primaryTasks = listOf(inboxTask),
                allOpenTasks = listOf(inboxTask),
                logbookTasks = listOf(logbookCompleted),
                somedayTasks = listOf(somedayTask),
                inboxTasks = listOf(inboxTask),
                todayTasks = emptyList(),
                eveningTasks = listOf(eveningTask),
                upcomingTasks = emptyList(),
                anytimeTasks = emptyList()
            )
        )

        // Completed Logbook task resolves cleanly even when primaryTasks only holds open items
        val resolvedLogbook = resolveUnifiedModalTask(
            expandedTaskId = "t-logbook",
            primaryTasks = listOf(inboxTask),
            allOpenTasks = listOf(inboxTask),
            logbookTasks = listOf(logbookCompleted),
            somedayTasks = listOf(somedayTask),
            inboxTasks = listOf(inboxTask),
            todayTasks = emptyList(),
            eveningTasks = listOf(eveningTask),
            upcomingTasks = emptyList(),
            anytimeTasks = emptyList()
        )
        assertNotNull(resolvedLogbook)
        assertEquals("Shipped v3.0 beta", resolvedLogbook?.title)
        assertEquals(TaskStatus.COMPLETED, resolvedLogbook?.status)

        // Rescheduled Someday task resolves even after leaving Inbox primaryTasks
        val resolvedSomeday = resolveUnifiedModalTask(
            expandedTaskId = "t-someday",
            primaryTasks = emptyList(),
            allOpenTasks = emptyList(),
            logbookTasks = listOf(logbookCompleted),
            somedayTasks = listOf(somedayTask),
            inboxTasks = emptyList(),
            todayTasks = emptyList(),
            eveningTasks = listOf(eveningTask),
            upcomingTasks = emptyList(),
            anytimeTasks = emptyList()
        )
        assertEquals("t-someday", resolvedSomeday?.id)

        // Evening task in Today view resolves when primaryTasks holds daytime tasks
        val resolvedEvening = resolveUnifiedModalTask(
            expandedTaskId = "t-evening",
            primaryTasks = listOf(inboxTask),
            allOpenTasks = emptyList(),
            logbookTasks = emptyList(),
            somedayTasks = emptyList(),
            inboxTasks = listOf(inboxTask),
            todayTasks = emptyList(),
            eveningTasks = listOf(eveningTask),
            upcomingTasks = emptyList(),
            anytimeTasks = emptyList()
        )
        assertEquals("t-evening", resolvedEvening?.id)

        // Deleted tasks never open in the Unified Soft Bento Task Modal
        assertNull(
            resolveUnifiedModalTask(
                expandedTaskId = "t-trash",
                primaryTasks = listOf(deletedTrashTask),
                allOpenTasks = emptyList(),
                logbookTasks = emptyList(),
                somedayTasks = emptyList(),
                inboxTasks = emptyList(),
                todayTasks = emptyList(),
                eveningTasks = emptyList(),
                upcomingTasks = emptyList(),
                anytimeTasks = emptyList(),
                deletedTaskIds = setOf("t-trash")
            )
        )
        assertNull(
            resolveUnifiedModalTask(
                expandedTaskId = "t-inbox",
                primaryTasks = listOf(inboxTask),
                allOpenTasks = listOf(inboxTask),
                logbookTasks = emptyList(),
                somedayTasks = emptyList(),
                inboxTasks = listOf(inboxTask),
                todayTasks = emptyList(),
                eveningTasks = emptyList(),
                upcomingTasks = emptyList(),
                anytimeTasks = emptyList(),
                deletedTaskIds = setOf("t-inbox")
            )
        )
    }

    @Test
    fun `ThingsSmartViewScreen hosts Unified Soft Bento Task Modal across all views and keeps list rows fixed`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()

        // Modal Dialog is hosted whenever expandedTaskId != null (never gated on isExpandedProjectWorkspace)
        assertTrue(
            smartViewKt.contains("if (!(biometricAppLockEnabled && !isAppUnlocked) && expandedTaskId != null)")
        )
        assertFalse(
            smartViewKt.contains("isExpandedProjectWorkspace &&\n            expandedTaskId != null")
        )

        // Spacious Soft Bento modal geometry & multi-pool task resolution
        assertTrue(smartViewKt.contains(".widthIn(max = 780.dp)"))
        assertTrue(smartViewKt.contains(".fillMaxWidth(0.95f)"))
        assertTrue(smartViewKt.contains(".heightIn(max = 680.dp)"))
        assertTrue(smartViewKt.contains("RoundedCornerShape(22.dp)"))
        assertTrue(smartViewKt.contains("resolveUnifiedModalTask("))
        assertTrue(smartViewKt.contains("logbookTasks = logbookTasks"))
        assertTrue(smartViewKt.contains("somedayTasks = somedayTasks"))

        // All 8 list-view ThingsTaskRowItem call sites pass expandedTaskId = null; only the modal passes expandedTaskId = expandedTaskId
        val rowCallMatches = Regex("""ThingsTaskRowItem\(\s*task\s*=[\s\S]*?expandedTaskId\s*=\s*(expandedTaskId|null)\s*,""")
            .findAll(smartViewKt)
            .map { it.groupValues[1] }
            .toList()
        assertEquals("Expected 9 ThingsTaskRowItem call sites (1 modal + 8 list rows)", 9, rowCallMatches.size)
        assertEquals("Only the modal Dialog should pass expandedTaskId = expandedTaskId", 1, rowCallMatches.count { it == "expandedTaskId" })
        assertEquals("All 8 list-view ThingsTaskRowItem call sites must pass expandedTaskId = null", 8, rowCallMatches.count { it == "null" })

        // Today Timeline opens the modal directly without forcing setTodayTimelineMode(false)
        val timelineCallChunk = smartViewKt
            .substringAfter("ThingsTodayTimelineSection(")
            .substringBefore("return")
        assertTrue(timelineCallChunk.contains("viewModel.expandTask(taskId)"))
        assertFalse(timelineCallChunk.contains("setTodayTimelineMode(false)"))

        // Hardware keyboard Enter / NumPadEnter / Spacebar opens selected single task
        assertTrue(smartViewKt.contains("Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar"))
        assertTrue(smartViewKt.contains("expandedTaskId == null && selectedTaskIds.size == 1"))
        assertTrue(smartViewKt.contains("viewModel.expandTask(selectedTaskIds.first())"))

        // Preserve TopThingsbookBar & ActiveNavigationTarget.shouldResetScrollToTop invariants
        val topBarChunk = smartViewKt.substringAfter("private fun TopThingsbookBar(").substringBefore("private fun ")
        assertTrue(topBarChunk.contains("BoxWithConstraints("))
        assertFalse(topBarChunk.contains("horizontalScroll("))
        assertTrue(ActiveNavigationTarget.SmartView(SmartViewType.INBOX).shouldResetScrollToTop(null))
        assertFalse(ActiveNavigationTarget.SmartView(SmartViewType.INBOX).shouldResetScrollToTop("task-1"))
    }

    @Test
    fun `ExpandableTaskCard implements responsive 2-zone Soft Bento modal layout and preserves all v2_1 invariants`() {
        assertEquals(600, SOFT_BENTO_MODAL_WIDE_BREAKPOINT_DP)
        assertFalse(isWideSoftBentoTaskModal(360f))
        assertFalse(isWideSoftBentoTaskModal(599.9f))
        assertTrue(isWideSoftBentoTaskModal(600f))
        assertTrue(isWideSoftBentoTaskModal(780f))

        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val cardSource = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ExpandableTaskCard.kt").readText()

        // Responsive 2-Zone Soft Bento Modal layout
        assertTrue(cardSource.contains("BoxWithConstraints(modifier = Modifier.fillMaxWidth())"))
        assertTrue(cardSource.contains("maxWidth >= 600.dp"))
        assertTrue(cardSource.contains("renderContentZone(Modifier.weight(1.35f))"))
        assertTrue(cardSource.contains("renderPropertiesZone(Modifier.weight(1f))"))
        assertTrue(cardSource.contains("SoftBentoPropertyGroup("))

        // Critical V2UpgradeAndFeaturesTest invariants
        assertTrue(cardSource.contains("v2.1 Unified Single Footer Row"))
        assertTrue(cardSource.contains("R.string.action_more_options"))
        assertTrue(cardSource.contains("if (task.attachments.isNotEmpty())"))
        assertFalse(cardSource.contains("private fun BottomActionIcon("))
    }
}
