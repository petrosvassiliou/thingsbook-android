package com.pv.thingsbook

import com.pv.thingsbook.ui.screens.shouldLockLandscapeForTimelineFullScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V3Beta6TimelineFullScreenTest {

    private fun resolveAppSrcMain(): File =
        sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    @Test
    fun `shouldLockLandscapeForTimelineFullScreen locks landscape strictly on Android phones and leaves ChromeOS Googlebooks tablets and foldables untouched`() {
        // Android phones (< 600dp smallestScreenWidthDp, non-ChromeOS/PC hardware) in Full Screen -> lock landscape
        assertTrue(
            shouldLockLandscapeForTimelineFullScreen(
                isEffectiveTimelineFullScreen = true,
                smallestScreenWidthDp = 360,
                isChromeOsOrPcHardware = false
            )
        )
        assertTrue(
            shouldLockLandscapeForTimelineFullScreen(
                isEffectiveTimelineFullScreen = true,
                smallestScreenWidthDp = 412,
                isChromeOsOrPcHardware = false
            )
        )
        assertTrue(
            shouldLockLandscapeForTimelineFullScreen(
                isEffectiveTimelineFullScreen = true,
                smallestScreenWidthDp = 599,
                isChromeOsOrPcHardware = false
            )
        )

        // Android phones when NOT in Full Screen -> never lock landscape
        assertFalse(
            shouldLockLandscapeForTimelineFullScreen(
                isEffectiveTimelineFullScreen = false,
                smallestScreenWidthDp = 360,
                isChromeOsOrPcHardware = false
            )
        )
        assertFalse(
            shouldLockLandscapeForTimelineFullScreen(
                isEffectiveTimelineFullScreen = false,
                smallestScreenWidthDp = 412,
                isChromeOsOrPcHardware = false
            )
        )

        // ChromeOS ARC / Googlebook resizable phone-sized window (412dp) -> never touch orientation
        assertFalse(
            shouldLockLandscapeForTimelineFullScreen(
                isEffectiveTimelineFullScreen = true,
                smallestScreenWidthDp = 360,
                isChromeOsOrPcHardware = true
            )
        )
        assertFalse(
            shouldLockLandscapeForTimelineFullScreen(
                isEffectiveTimelineFullScreen = true,
                smallestScreenWidthDp = 412,
                isChromeOsOrPcHardware = true
            )
        )

        // Tablets, Googlebooks, and Unfolded Foldables (>= 600dp) -> never touch orientation
        assertFalse(
            shouldLockLandscapeForTimelineFullScreen(
                isEffectiveTimelineFullScreen = true,
                smallestScreenWidthDp = 600,
                isChromeOsOrPcHardware = false
            )
        )
        assertFalse(
            shouldLockLandscapeForTimelineFullScreen(
                isEffectiveTimelineFullScreen = true,
                smallestScreenWidthDp = 840,
                isChromeOsOrPcHardware = false
            )
        )
        assertFalse(
            shouldLockLandscapeForTimelineFullScreen(
                isEffectiveTimelineFullScreen = true,
                smallestScreenWidthDp = 840,
                isChromeOsOrPcHardware = true
            )
        )
    }

    @Test
    fun `Phone landscape DisposableEffect and ViewModel full-screen entry state reset are wired cleanly`() {
        val appSrcMain = resolveAppSrcMain()
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()
        val viewModelKt = File(appSrcMain, "java/com/pv/thingsbook/ui/viewmodel/ThingsbookViewModel.kt").readText()

        // ChromeOS / PC hardware detection & orientation lock DisposableEffect
        assertTrue(smartViewKt.contains("pm.hasSystemFeature(\"org.chromium.arc\")"))
        assertTrue(smartViewKt.contains("pm.hasSystemFeature(\"org.chromium.arc.device_management\")"))
        assertTrue(smartViewKt.contains("pm.hasSystemFeature(PackageManager.FEATURE_PC)"))
        assertTrue(smartViewKt.contains("DisposableEffect(lockPhoneLandscape, appCtx)"))
        assertTrue(smartViewKt.contains("ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE"))
        assertTrue(smartViewKt.contains("ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED"))

        // ViewModel collapses expanded task modal, clears multi-selection, and closes Magic Plus menu on full-screen entry
        val vmFsChunk = viewModelKt
            .substringAfter("fun setTimelineFullScreen(enabled: Boolean)")
            .substringBefore("fun setTodayTimelineMode(")
        assertTrue(vmFsChunk.contains("_expandedTaskId.value = null"))
        assertTrue(vmFsChunk.contains("_selectedTaskIds.value = emptySet()"))
        assertTrue(vmFsChunk.contains("_isMagicPlusMenuOpen.value = false"))
        assertTrue(vmFsChunk.contains("setTimelineFullScreen(!_isTimelineFullScreen.value)"))
    }

    @Test
    fun `Full Screen mode applies strictly to the Timeline view and disables or hides all non-timeline buttons and options`() {
        val appSrcMain = resolveAppSrcMain()
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()
        val modalsKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ThingsV2FeaturesModals.kt").readText()
        val workspaceViewsKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ProjectWorkspaceViews.kt").readText()

        // 1. ThingsSmartViewScreen: hides Magic Plus FAB, removes segmented toggle from Today Full Screen header, and blocks shortcuts
        assertTrue(
            smartViewKt.contains("if (!isEffectiveTimelineFullScreen) {\n            MagicPlusOverlaySection(")
        )
        val todayFsHeaderChunk = smartViewKt
            .substringAfter("if (isEffectiveTimelineFullScreen && isTodayTimelineActive) {")
            .substringBefore("if (!isEffectiveTimelineFullScreen) {\n                        // On Foldables")
        assertFalse(
            "Today Full Screen header must not render Liste | Zeitplan segmented toggle",
            todayFsHeaderChunk.contains("ThingsTodayModeSegmentedToggle(")
        )
        assertTrue(todayFsHeaderChunk.contains("Icons.Outlined.CloseFullscreen"))

        val previewKeyChunk = smartViewKt
            .substringAfter(".onPreviewKeyEvent { event ->")
            .substringBefore(".onKeyEvent { event ->")
        assertTrue(previewKeyChunk.contains("if (isEffectiveTimelineFullScreen)"))
        assertTrue(previewKeyChunk.contains("viewModel.setTimelineFullScreen(false)"))
        assertEquals(
            "Today Timeline, Project Roadmap, and Area Portfolio Roadmap must receive isFullScreen = isEffectiveTimelineFullScreen",
            3,
            Regex("""isFullScreen\s*=\s*isEffectiveTimelineFullScreen""").findAll(smartViewKt).count()
        )

        // 2. ThingsTodayTimelineSection: pure timeline schedule in Full Screen (no unscheduled tasks, no edit/focus buttons, 12.dp bottom padding)
        val todayTimelineChunk = modalsKt.substringAfter("fun ThingsTodayTimelineSection(")
        assertTrue(todayTimelineChunk.contains("isFullScreen: Boolean = false"))
        assertTrue(todayTimelineChunk.contains("bottom = if (isFullScreen) 12.dp else 96.dp"))
        assertTrue(todayTimelineChunk.contains("if (!isFullScreen && schedule.unscheduledTasks.isNotEmpty())"))
        assertTrue(todayTimelineChunk.contains("onStatusChange = { if (!isFullScreen) onCompleteTask(task.id) }"))

        // 3. ProjectRoadmapWorkspace & RoadmapSynchronizedLanesBody: 460.dp title pill, hidden Export & Unscheduled, and readOnly Gantt lanes/bars
        val roadmapChunk = workspaceViewsKt
            .substringAfter("fun ProjectRoadmapWorkspace(")
            .substringBefore("private fun RoadmapDayHeaderStrip(")
        assertTrue(roadmapChunk.contains(".widthIn(max = 460.dp)"))
        assertFalse(
            "Project Title pill in Full Screen must not truncate at 220.dp",
            roadmapChunk.contains("else 220.dp")
        )
        assertTrue(roadmapChunk.contains("if (!isFullScreen) {\n                    RoadmapExportMenuPill("))
        assertTrue(roadmapChunk.contains(".padding(bottom = if (isFullScreen) 12.dp else 96.dp)"))
        assertTrue(roadmapChunk.contains("if (!isFullScreen && (layout.unscheduledHeadings.isNotEmpty() || layout.unscheduledTasks.isNotEmpty()))"))
        assertTrue(roadmapChunk.contains("readOnly = isFullScreen"))

        val lanesChunk = workspaceViewsKt
            .substringAfter("private fun RoadmapSynchronizedLanesBody(")
            .substringBefore("fun RoadmapPdfExportDialog(")
        assertTrue(lanesChunk.contains("readOnly: Boolean = false"))
        assertTrue(lanesChunk.contains("if (!readOnly && row.heading != null && row.phaseBar != null)"))
        assertTrue(lanesChunk.contains("onStatusChange = { next -> if (!readOnly) onUpdateTaskStatus(task.id, next) }"))
        assertEquals(
            "Both RoadmapDraggablePhaseBar and RoadmapDraggableTaskBar must receive readOnly = readOnly",
            2,
            Regex("""readOnly\s*=\s*readOnly""").findAll(lanesChunk).count()
        )

        assertEquals("3.3.0-beta.2", BuildConfig.VERSION_NAME)
        assertTrue("3.3.0-beta.2 pre-release must contain -beta", BuildConfig.VERSION_NAME.contains("-beta"))
        assertEquals(120, BuildConfig.VERSION_CODE)
    }
}
