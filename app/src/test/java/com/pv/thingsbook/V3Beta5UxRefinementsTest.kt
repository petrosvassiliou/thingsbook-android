package com.pv.thingsbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class V3Beta5UxRefinementsTest {

    private fun resolveAppSrcMain(): File =
        sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    @Test
    fun `Unified Soft Bento Task Modal uses flash-free in-window overlay bounded by maxModalHeight with 3-zone non-clipped footer`() {
        val appSrcMain = resolveAppSrcMain()
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()
        val cardKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ExpandableTaskCard.kt").readText()

        // 1. Flash-free in-window AnimatedVisibility + scaleIn/scaleOut overlay (no OS Dialog window flash on phones/foldables)
        val modalChunk = smartViewKt
            .substringAfter("val isModalVisible = !(biometricAppLockEnabled && !isAppUnlocked) && modalTask != null")
            .substringBefore("ThingsModalsOverlay(")
        assertTrue(modalChunk.contains("val maxModalHeight = (configuration.screenHeightDp.dp - 40.dp).coerceIn(300.dp, 680.dp)"))
        assertTrue(modalChunk.contains("AnimatedVisibility("))
        assertTrue(modalChunk.contains(".zIndex(35f)"))
        assertTrue(modalChunk.contains("scaleIn("))
        assertTrue(modalChunk.contains("scaleOut("))
        assertTrue(modalChunk.contains(".heightIn(max = maxModalHeight)"))
        assertTrue(modalChunk.contains("forceModalExpanded = true"))
        assertFalse("Modal wrapper must not wrap ThingsTaskRowItem in an outer verticalScroll that clips footer buttons", modalChunk.contains("verticalScroll("))
        assertFalse(
            "Task modal must not spawn a separate Android OS Dialog window that flashes on OLED phones/foldables",
            Regex("""\bDialog\(""").containsMatchIn(modalChunk)
        )

        // 2. ExpandableTaskCard suppresses animateContentSize when expanded inside modal, and pins header + footer around scrollable middle body
        assertTrue(cardKt.contains("if (!isExpanded)"))
        val expandedBodyChunk = cardKt.substringAfter("private fun ExpandedTaskCardBody(")
        assertTrue(expandedBodyChunk.contains("val bodyScrollState = rememberScrollState()"))
        assertTrue(expandedBodyChunk.contains("renderPinnedHeaderRow()"))
        assertTrue(expandedBodyChunk.contains(".weight(1f, fill = false)"))
        assertTrue(expandedBodyChunk.contains(".verticalScroll(bodyScrollState)"))
        assertTrue(expandedBodyChunk.contains("// v2.1 Unified Single Footer Row"))
        assertTrue(expandedBodyChunk.contains(".padding(top = 10.dp, bottom = 6.dp)"))
    }

    @Test
    fun `Collapsed task rows support inline Quick Edit title rename, quick-action strip, and interactive clickable badges without opening modal`() {
        val appSrcMain = resolveAppSrcMain()
        val cardKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ExpandableTaskCard.kt").readText()

        // Inline Quick Edit local state, trigger button, and quick-action strip
        assertTrue(cardKt.contains("var isInlineQuickEditing by remember(task.id) { mutableStateOf(false) }"))
        assertTrue(cardKt.contains("R.string.v30_quick_edit_action"))
        assertTrue(cardKt.contains("R.string.v30_open_details_action"))
        assertTrue(cardKt.contains("startInlineQuickEdit"))

        // CollapsedTaskInlineBadges wires interactive pickers & dropdowns directly on collapsed rows
        val badgesChunk = cardKt.substringAfter("private fun CollapsedTaskInlineBadges(").substringBefore("private fun ExpandedTaskCardBody(")
        assertTrue(badgesChunk.contains("onOpenWhenDialog"))
        assertTrue(badgesChunk.contains("onOpenDeadlineDialog"))
        assertTrue(badgesChunk.contains("onOpenTimeblockDialog"))
        assertTrue(badgesChunk.contains("onOpenRepeatDialog"))
        assertTrue(badgesChunk.contains("onOpenDependenciesDialog"))
        assertTrue(badgesChunk.contains("onOpenTagPickerDialog"))
        assertTrue(badgesChunk.contains("showInlineDurationMenu"))
        assertTrue(badgesChunk.contains("showInlineEffortMenu"))
    }

    @Test
    fun `TopThingsbookBar is decluttered to a single non-scrolling row while Categories Manager lives in Sidebar dock and Strip`() {
        val appSrcMain = resolveAppSrcMain()
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()

        val topBarChunk = smartViewKt.substringAfter("private fun TopThingsbookBar(").substringBefore("private fun ")
        assertTrue(topBarChunk.contains("BoxWithConstraints("))
        assertFalse(topBarChunk.contains("horizontalScroll("))
        assertFalse("Brand book icon belongs in Sidebar, not TopThingsbookBar", topBarChunk.contains("ThingsbookBrandSymbol("))
        assertFalse("Weekly Review belongs in Sidebar, not TopThingsbookBar", topBarChunk.contains("topbar_tooltip_weekly_review"))
        assertFalse("Templates belong in Sidebar, not TopThingsbookBar", topBarChunk.contains("topbar_tooltip_templates"))
        assertFalse("Areas Manager belongs in Sidebar/Strip, not TopThingsbookBar", topBarChunk.contains("topbar_tooltip_areas"))
        assertFalse("Categories Manager belongs in Sidebar/Strip, not TopThingsbookBar", topBarChunk.contains("topbar_tooltip_tags"))
        assertFalse("Notifications belong in Settings, not TopThingsbookBar", topBarChunk.contains("topbar_tooltip_notifications"))
        assertFalse("Language selector belongs in Settings, not TopThingsbookBar", topBarChunk.contains("topbar_tooltip_language"))

        // Quiet Categories Manager entry in both SmartViewNavigationStrip and ThingsChromebookSidebar
        val stripChunk = smartViewKt.substringAfter("private fun SmartViewNavigationStrip(").substringBefore("private fun ViewSortMenuPill(")
        assertTrue(stripChunk.contains("item(key = \"nav_manage_tags\")"))
        assertTrue(stripChunk.contains("R.string.topbar_tooltip_tags"))

        val sidebarChunk = smartViewKt.substringAfter("private fun ThingsChromebookSidebar(")
        assertTrue(sidebarChunk.contains("onOpenTagManager: () -> Unit"))
        assertTrue(sidebarChunk.contains("R.string.topbar_tooltip_tags"))
    }

    @Test
    fun `ViewTitleHeader prevents mid-word Smart View wrapping, declutters Project Detail Row 1, removes duplicate Roadmap export, and allows 2-line Sidebar project titles`() {
        val appSrcMain = resolveAppSrcMain()
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()

        val headerChunk = smartViewKt.substringAfter("private fun ViewTitleHeader(").substringBefore("private fun ProjectHeadingSectionHeader(")
        assertTrue(headerChunk.contains("val isCompactSmartViewHeader = maxWidth < 600.dp && navTarget is ActiveNavigationTarget.SmartView"))
        assertTrue(headerChunk.contains("softWrap = false"))
        assertTrue(headerChunk.contains("showProjectMoreMenu"))
        assertTrue(headerChunk.contains("Icons.Outlined.MoreHoriz"))
        assertFalse("ViewTitleHeader must not render a duplicate RoadmapExportMenuPill above ProjectRoadmapWorkspace", headerChunk.contains("RoadmapExportMenuPill("))

        val sidebarChunk = smartViewKt.substringAfter("private fun ThingsChromebookSidebar(")
        val twoLineProjectMatches = Regex("""text\s*=\s*project\.title[\s\S]*?maxLines\s*=\s*2""").findAll(sidebarChunk).count()
        assertTrue("Both nested area projects and unassigned projects in Sidebar must allow maxLines = 2", twoLineProjectMatches >= 2)
    }

    @Test
    fun `Full Screen Timeline mode is wired across ViewModel, Today Timeline, Project Roadmap, and all 5 localized strings_xml files`() {
        val appSrcMain = resolveAppSrcMain()
        val viewModelKt = File(appSrcMain, "java/com/pv/thingsbook/ui/viewmodel/ThingsbookViewModel.kt").readText()
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()
        val workspaceViewsKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ProjectWorkspaceViews.kt").readText()

        assertTrue(viewModelKt.contains("val isTimelineFullScreen: StateFlow<Boolean>"))
        assertTrue(viewModelKt.contains("fun setTimelineFullScreen(enabled: Boolean)"))
        assertTrue(viewModelKt.contains("fun toggleTimelineFullScreen()"))

        assertTrue(smartViewKt.contains("isEffectiveTimelineFullScreen"))
        assertTrue(smartViewKt.contains("R.string.v30_timeline_fullscreen"))
        assertTrue(smartViewKt.contains("R.string.v30_timeline_exit_fullscreen"))

        val roadmapChunk = workspaceViewsKt.substringAfter("fun ProjectRoadmapWorkspace(")
        assertTrue(roadmapChunk.contains("isFullScreen: Boolean = false"))
        assertTrue(roadmapChunk.contains("onToggleFullScreen: (() -> Unit)? = null"))
        assertTrue(roadmapChunk.contains("R.string.v30_timeline_fullscreen"))
        assertTrue(roadmapChunk.contains("R.string.v30_timeline_exit_fullscreen"))

        // Verify all 4 new v3.0.0-beta.5 string keys exist and are non-blank in all 5 locales
        val resRoot = File(appSrcMain, "res")
        val requiredKeys = listOf(
            "v30_timeline_fullscreen",
            "v30_timeline_exit_fullscreen",
            "v30_quick_edit_action",
            "v30_open_details_action"
        )
        for (folder in listOf("values", "values-de", "values-fr", "values-it", "values-es")) {
            val xmlFile = File(resRoot, "$folder/strings.xml")
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("string")
            val keys = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i)
                val name = elem.attributes?.getNamedItem("name")?.nodeValue ?: continue
                keys[name] = elem.textContent.orEmpty()
            }
            for (key in requiredKeys) {
                assertNotNull("Missing $key in $folder/strings.xml", keys[key])
                assertTrue("Blank $key in $folder/strings.xml", keys[key]!!.isNotBlank())
            }
        }
        assertEquals(119, BuildConfig.VERSION_CODE)
        assertEquals("3.3.0-beta.1", BuildConfig.VERSION_NAME)
        assertTrue("3.3.0-beta.1 pre-release must contain -beta", BuildConfig.VERSION_NAME.contains("-beta"))
    }
}
