package com.pv.thingsbook

import com.pv.thingsbook.ui.viewmodel.TaskSelectionStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V3Beta15MilestonesAndSelectionTest {

    @Test
    fun `single-click selection replaces previous task without entering multi-select mode while explicit multi-select enables bar`() {
        val selection = TaskSelectionStateHolder()

        // 1. Plain single-click on t1 -> selects ONLY t1, isMultiSelectMode == false, no bottom multi-select bar
        selection.selectSingleTask("t1")
        assertEquals(setOf("t1"), selection.selectedTaskIds.value)
        assertFalse(selection.isMultiSelectMode.value)
        assertFalse(selection.showMultiSelectionBar)
        assertNull(selection.expandedTaskId.value)

        // 2. Plain single-click on t2 next -> replaces t1 with ONLY t2, isMultiSelectMode == false, no bottom bar
        selection.selectSingleTask("t2")
        assertEquals(setOf("t2"), selection.selectedTaskIds.value)
        assertFalse(selection.isMultiSelectMode.value)
        assertFalse(selection.showMultiSelectionBar)

        // 3. Ctrl/Cmd-Click or Long-Press toggleTaskSelection("t3") -> enters multi-select with {"t2", "t3"}
        selection.toggleTaskSelection("t3")
        assertEquals(setOf("t2", "t3"), selection.selectedTaskIds.value)
        assertTrue(selection.isMultiSelectMode.value)
        assertTrue(selection.showMultiSelectionBar)

        // 4. clearTaskSelection resets both selectedTaskIds and isMultiSelectMode
        selection.clearTaskSelection()
        assertEquals(emptySet<String>(), selection.selectedTaskIds.value)
        assertFalse(selection.isMultiSelectMode.value)
        assertFalse(selection.showMultiSelectionBar)

        // 5. Context Menu "Select" / Long-Press on single task enters explicit multi-select mode
        selection.toggleTaskSelection("t1")
        assertEquals(setOf("t1"), selection.selectedTaskIds.value)
        assertTrue(selection.isMultiSelectMode.value)
        assertTrue(selection.showMultiSelectionBar)

        // 6. Shift-Click range selection across visible tasks
        selection.selectSingleTask("t1")
        selection.selectTaskRange(listOf("t1", "t2", "t3", "t4"), "t3")
        assertEquals(setOf("t1", "t2", "t3"), selection.selectedTaskIds.value)
        assertTrue(selection.isMultiSelectMode.value)
        assertTrue(selection.showMultiSelectionBar)

        // 7. Ctrl+A selectAllTasks
        selection.selectAllTasks(listOf("t1", "t2", "t3", "t4"))
        assertEquals(setOf("t1", "t2", "t3", "t4"), selection.selectedTaskIds.value)
        assertTrue(selection.isMultiSelectMode.value)
    }

    @Test
    fun `Room v7 MIGRATION_6_7, Asana Target Area preselection, and Milestone UI components are wired cleanly`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

        // 1. Room v7 + MIGRATION_6_7
        val dbKt = File(appSrcMain, "java/com/pv/thingsbook/data/local/ThingsDatabase.kt").readText()
        assertTrue(dbKt.contains("version = 7"))
        val appModuleKt = File(appSrcMain, "java/com/pv/thingsbook/di/AppModule.kt").readText()
        assertTrue(appModuleKt.contains("val MIGRATION_6_7 = object : Migration(6, 7)"))
        assertTrue(appModuleKt.contains("ALTER TABLE tasks ADD COLUMN isMilestone INTEGER NOT NULL DEFAULT 0"))
        assertTrue(appModuleKt.contains("MIGRATION_5_6, MIGRATION_6_7"))

        // 2. Prominent Target Area Preselection in ThingsTemplatesAndImportModal (Tab 2 card + R.string.areas_none)
        val modalsKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ThingsV2FeaturesModals.kt").readText()
        assertFalse("Must not render raw em-dash for None Area pill", modalsKt.contains("label = \"—\""))
        assertTrue(modalsKt.contains("R.string.v30_external_import_target_area"))

        // 3. Context Area Preselection & Sidebar Area Asana/Planner Menu in ThingsSmartViewScreen
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()
        assertTrue(smartViewKt.contains("val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()"))
        assertTrue(smartViewKt.contains("val showMultiSelectionBar = selectedTaskIds.size > 1 || (isMultiSelectMode && selectedTaskIds.isNotEmpty())"))
        assertTrue(smartViewKt.contains("if (!isEffectiveTimelineFullScreen && showMultiSelectionBar)"))
        assertTrue(smartViewKt.contains("showSidebarAreaMenu"))
        assertTrue(smartViewKt.contains("onToggleMilestone = { viewModel.toggleTaskMilestone(task.id) }"))

        // 4. Diamond Checkbox, Soft Bento Modal Milestone controls, and Gantt Roadmap 18.dp Diamond + PDF/PNG #7C3AED Path
        val checkboxKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ThingsCheckbox.kt").readText()
        assertTrue(checkboxKt.contains("isMilestone: Boolean = false"))
        assertTrue(checkboxKt.contains("if (isMilestone) 45f else 0f"))
        assertTrue(checkboxKt.contains("if (isMilestone) -45f else 0f") || checkboxKt.contains("-45f"))

        val cardKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ExpandableTaskCard.kt").readText()
        assertTrue(cardKt.contains("R.string.v30_mark_as_milestone"))
        assertTrue(cardKt.contains("R.string.v30_remove_milestone"))
        assertTrue(cardKt.contains("R.string.v30_milestone_label"))

        val workspaceKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ProjectWorkspaceViews.kt").readText()
        assertTrue(workspaceKt.contains("if (taskBar.isMilestone)"))
        assertTrue(workspaceKt.contains("0xFF7C3AED.toInt()"))
        assertTrue(workspaceKt.contains("drawMilestoneTimelineDiamondAndLabel("))
    }
}
