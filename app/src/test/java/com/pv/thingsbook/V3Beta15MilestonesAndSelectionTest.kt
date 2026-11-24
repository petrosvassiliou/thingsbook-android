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

        // 1. Room v9 + MIGRATION_6_7 / MIGRATION_7_8 / MIGRATION_8_9
        val dbKt = File(appSrcMain, "java/com/pv/thingsbook/data/local/ThingsDatabase.kt").readText()
        assertTrue(dbKt.contains("version = 9"))
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

    @Test
    fun `v3_0_0_beta_17 inspector strings in all 5 locales, inspector visibility state, upcoming milestones helper, and wide-screen layout are wired cleanly`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val resRoot = File(appSrcMain, "res")

        // 1. Verify all 4 v30_inspector_* localization keys exist and are non-blank in all 5 values* folders
        val expectedInspectorStrings = mapOf(
            "values" to mapOf(
                "v30_inspector_title" to "Inspector",
                "v30_inspector_overview_title" to "Overview",
                "v30_inspector_upcoming_deadlines" to "Upcoming Deadlines & Milestones",
                "v30_inspector_toggle_tooltip" to "Toggle Inspector Panel"
            ),
            "values-de" to mapOf(
                "v30_inspector_title" to "Inspektor",
                "v30_inspector_overview_title" to "Übersicht",
                "v30_inspector_upcoming_deadlines" to "Anstehende Fristen & Meilensteine",
                "v30_inspector_toggle_tooltip" to "Inspektor-Seitenleiste ein-/ausblenden"
            ),
            "values-fr" to mapOf(
                "v30_inspector_title" to "Inspecteur",
                "v30_inspector_overview_title" to "Aperçu",
                "v30_inspector_upcoming_deadlines" to "Échéances & jalons à venir",
                "v30_inspector_toggle_tooltip" to "Afficher/masquer le panneau Inspecteur"
            ),
            "values-it" to mapOf(
                "v30_inspector_title" to "Ispettore",
                "v30_inspector_overview_title" to "Panoramica",
                "v30_inspector_upcoming_deadlines" to "Scadenze e traguardi imminenti",
                "v30_inspector_toggle_tooltip" to "Mostra/nascondi pannello Ispettore"
            ),
            "values-es" to mapOf(
                "v30_inspector_title" to "Inspector",
                "v30_inspector_overview_title" to "Resumen",
                "v30_inspector_upcoming_deadlines" to "Próximas fechas límite e hitos",
                "v30_inspector_toggle_tooltip" to "Mostrar/ocultar panel Inspector"
            )
        )

        val dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        for ((folder, expectedKeys) in expectedInspectorStrings) {
            val xmlFile = File(resRoot, "$folder/strings.xml")
            assertTrue("Missing $folder/strings.xml", xmlFile.isFile)
            val doc = dbf.newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("string")
            val parsed = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i)
                val name = elem.attributes?.getNamedItem("name")?.nodeValue ?: continue
                parsed[name] = elem.textContent.orEmpty()
            }
            for ((key, expectedVal) in expectedKeys) {
                val actualVal = parsed[key]
                assertTrue("Missing '$key' in $folder/strings.xml", actualVal != null && actualVal.isNotBlank())
                assertEquals("Mismatch for '$key' in $folder/strings.xml", expectedVal, actualVal)
            }
        }

        // 2. Verify isInspectorVisible / toggleInspectorVisible() / setInspectorVisible() on selection controller & ViewModel
        val selection = TaskSelectionStateHolder()
        assertTrue("Inspector should default to visible", selection.isInspectorVisible.value)
        assertFalse(selection.toggleInspectorVisible())
        assertFalse(selection.isInspectorVisible.value)
        assertTrue(selection.toggleInspectorVisible())
        assertTrue(selection.isInspectorVisible.value)
        selection.setInspectorVisible(false)
        assertFalse(selection.isInspectorVisible.value)
        selection.setInspectorVisible(true)
        assertTrue(selection.isInspectorVisible.value)

        val viewModelKt = File(appSrcMain, "java/com/pv/thingsbook/ui/viewmodel/ThingsbookViewModel.kt").readText()
        assertTrue(viewModelKt.contains("val isInspectorVisible: StateFlow<Boolean>"))
        assertTrue(viewModelKt.contains("fun toggleInspectorVisible()"))
        assertTrue(viewModelKt.contains("fun setInspectorVisible(visible: Boolean)"))

        // 3. Verify computeUpcomingDeadlinesAndMilestones filters overdue + 14-day window & sorts chronologically
        val zone = java.time.ZoneOffset.UTC
        val baseDate = java.time.LocalDate.of(2026, 11, 21)
        fun dayMs(plusDays: Long): Long = baseDate.plusDays(plusDays).atStartOfDay(zone).toInstant().toEpochMilli()
        fun makeTask(
            id: String,
            deadline: Long? = null,
            whenDate: Long? = null,
            isMilestone: Boolean = false,
            status: com.pv.thingsbook.domain.model.TaskStatus = com.pv.thingsbook.domain.model.TaskStatus.OPEN
        ) = com.pv.thingsbook.domain.model.TaskItem(
            id = id,
            title = id,
            notes = "",
            status = status,
            scheduleState = com.pv.thingsbook.domain.model.ScheduleState.UPCOMING,
            whenDate = whenDate,
            deadline = deadline,
            reminderTime = null,
            completedAt = null,
            projectId = null,
            headingId = null,
            areaId = null,
            recurrenceRule = null,
            sortOrder = 1.0,
            isMilestone = isMilestone,
            createdAt = 1L,
            updatedAt = 1L
        )

        val upcoming = com.pv.thingsbook.ui.components.computeUpcomingDeadlinesAndMilestones(
            allOpenTasks = listOf(
                makeTask("due-in-20d", deadline = dayMs(20)),
                makeTask("due-in-5d", deadline = dayMs(5)),
                makeTask("ms-in-5d", whenDate = dayMs(5), isMilestone = true),
                makeTask("overdue-2d", deadline = dayMs(-2)),
                makeTask("done-ms", deadline = dayMs(1), isMilestone = true, status = com.pv.thingsbook.domain.model.TaskStatus.COMPLETED),
                makeTask("plain-no-dl", whenDate = dayMs(2), isMilestone = false)
            ),
            todayDate = baseDate,
            zoneId = zone,
            limit = 8
        )
        assertEquals(listOf("overdue-2d", "ms-in-5d", "due-in-5d"), upcoming.map { it.id })

        // 4. Verify WorkspaceInspectorPanel and wide-screen layout wiring in ThingsSmartViewScreen
        val inspectorKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/WorkspaceInspectorPanel.kt").readText()
        assertTrue(inspectorKt.contains("WORKSPACE_INSPECTOR_PANEL_WIDTH_DP = 352"))
        assertTrue(inspectorKt.contains("R.string.v30_inspector_title"))
        assertTrue(inspectorKt.contains("R.string.v30_inspector_overview_title"))
        assertTrue(inspectorKt.contains("R.string.v30_inspector_upcoming_deadlines"))

        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()
        assertTrue(smartViewKt.contains("maxWidth >= 940.dp &&"))
        assertTrue(smartViewKt.contains("val showInspectorPane = canFitInspector && isInspectorVisible"))
        assertTrue(smartViewKt.contains("WorkspaceInspectorPanel("))
        assertTrue(smartViewKt.contains("isWideScreen -> 1440.dp"))
        assertTrue(smartViewKt.contains("end = if (showInspectorPane) 24.dp + 353.dp else 24.dp"))
        assertTrue(smartViewKt.contains("R.string.v30_inspector_toggle_tooltip"))
    }

    @Test
    fun `v3_0_0_beta_18 perspectives sidebar setting strings in all 5 locales, hidden_by_default toggle wiring, and 4h_8h effort durations work cleanly`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val resRoot = File(appSrcMain, "res")

        // 1. Verify v30_settings_show_perspectives and v30_settings_show_perspectives_desc across all 5 values* folders
        val expectedPerspectivesSettingStrings = mapOf(
            "values" to mapOf(
                "v30_settings_show_perspectives" to "Show Custom Perspectives in Sidebar",
                "v30_settings_show_perspectives_desc" to "Adds a saved-filters section to the sidebar for custom multi-criteria views"
            ),
            "values-de" to mapOf(
                "v30_settings_show_perspectives" to "Eigene Perspektiven in der Seitenleiste anzeigen",
                "v30_settings_show_perspectives_desc" to "Fügt der Seitenleiste einen Bereich für gespeicherte Mehrfachfilter hinzu"
            ),
            "values-fr" to mapOf(
                "v30_settings_show_perspectives" to "Afficher les perspectives personnalisées dans la barre latérale",
                "v30_settings_show_perspectives_desc" to "Ajoute une section de filtres enregistrés à la barre latérale"
            ),
            "values-it" to mapOf(
                "v30_settings_show_perspectives" to "Mostra prospettive personalizzate nella barra laterale",
                "v30_settings_show_perspectives_desc" to "Aggiunge una sezione di filtri salvati alla barra laterale"
            ),
            "values-es" to mapOf(
                "v30_settings_show_perspectives" to "Mostrar perspectivas personalizadas en la barra lateral",
                "v30_settings_show_perspectives_desc" to "Añade una sección de filtros guardados a la barra lateral"
            )
        )

        val dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        for ((folder, expectedKeys) in expectedPerspectivesSettingStrings) {
            val xmlFile = File(resRoot, "$folder/strings.xml")
            assertTrue("Missing $folder/strings.xml", xmlFile.isFile)
            val doc = dbf.newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("string")
            val parsed = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i)
                val name = elem.attributes?.getNamedItem("name")?.nodeValue ?: continue
                parsed[name] = elem.textContent.orEmpty()
            }
            for ((key, expectedVal) in expectedKeys) {
                val actualVal = parsed[key]
                assertTrue("Missing '$key' in $folder/strings.xml", actualVal != null && actualVal.isNotBlank())
                assertEquals("Mismatch for '$key' in $folder/strings.xml", expectedVal, actualVal)
            }
        }

        // 2. Verify STANDALONE_DURATION_PRESETS contains 240 & 480 and formats 240 -> "4h" and 480 -> "8h"
        val presets = com.pv.thingsbook.domain.model.STANDALONE_DURATION_PRESETS
        assertEquals(listOf(5, 15, 30, 60, 120, 240, 480), presets)
        assertEquals("4h", com.pv.thingsbook.domain.model.formatDurationCompact(240))
        assertEquals("8h", com.pv.thingsbook.domain.model.formatDurationCompact(480))
        assertEquals("1d 4h", com.pv.thingsbook.domain.model.formatDurationCompact(720))
        assertEquals("5d", com.pv.thingsbook.domain.model.formatDurationCompact(2400))

        // 3. Verify ViewModel, Settings Modal, and Sidebar Perspectives gating
        val viewModelKt = File(appSrcMain, "java/com/pv/thingsbook/ui/viewmodel/ThingsbookViewModel.kt").readText()
        assertTrue(viewModelKt.contains("val showPerspectivesInSidebar: StateFlow<Boolean>"))
        assertTrue(viewModelKt.contains("fun setShowPerspectivesInSidebar(enabled: Boolean)"))
        assertTrue(viewModelKt.contains("settingsPrefs.contains(\"show_perspectives_in_sidebar\")"))

        val settingsKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ThingsSettingsModal.kt").readText()
        assertTrue(settingsKt.contains("R.string.v30_settings_show_perspectives"))
        assertTrue(settingsKt.contains("R.string.v30_settings_show_perspectives_desc"))
    }
}
