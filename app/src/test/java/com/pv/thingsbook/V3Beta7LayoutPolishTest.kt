package com.pv.thingsbook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V3Beta7LayoutPolishTest {

    @Test
    fun `v3_0_0_beta_7 workspace aligns cleanly to leading edge next to sidebar and sidebar footer uses collision_free 2_row dock across Android, ChromeOS PWA, and macOS`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val repoRoot = if (appSrcMain.path.startsWith("app/")) File(".") else File("..")

        // 1. Android: leading-aligned TopStart main workspace with tight 4.dp sidebar gutter + 2-row sidebar footer dock
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()
        assertTrue(smartViewKt.contains("contentAlignment = Alignment.TopStart"))
        assertTrue(smartViewKt.contains("start = if (showSidebar) 4.dp else 0.dp"))
        assertTrue(smartViewKt.contains("// 2-Row Bottom Sidebar Dock: Row 1 = Full-Width \"+ New Project ▾\", Row 2 = Spaced Utility Dock"))

        // 2. ChromeOS PWA: left-aligned .workspace-container (no margin: 0 auto) & 2-row .sidebar-footer
        val chromeCss = File(repoRoot, "chromeos-pwa/src/index.css").readText()
        val workspaceBlock = Regex("""\.workspace-container\s*\{([^}]*)\}""").find(chromeCss)?.groupValues?.get(1).orEmpty()
        assertTrue(workspaceBlock.contains("margin: 0;"))
        assertFalse(workspaceBlock.contains("auto"))
        val sidebarFooterBlock = Regex("""\.sidebar-footer\s*\{([^}]*)\}""").find(chromeCss)?.groupValues?.get(1).orEmpty()
        assertTrue(sidebarFooterBlock.contains("flex-direction: column;"))

        // 3. macOS: SmartViewCanvas outer frame uses alignment: .leading (26pt gutter) & SidebarView uses 2-row VStack footer
        val macCanvasSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/SmartViewCanvas.swift").readText()
        assertTrue(macCanvasSwift.contains(".padding(.horizontal, isAnyTimelineFullScreen ? 20 : 26)"))
        assertTrue(macCanvasSwift.contains(".frame(maxWidth: .infinity, alignment: .leading)"))
        val macSidebarSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/SidebarView.swift").readText()
        assertTrue(macSidebarSwift.contains("// 2-Row Bottom Sidebar Dock: Row 1 = Full-Width \"+ New Project ▾\" Menu, Row 2 = Spaced Utility Dock"))
    }

    @Test
    fun `v3_0_0_beta_8 sidebar clearly separates Perspectives Soft Bento card from Areas and Unassigned Projects across Android, ChromeOS PWA, and macOS`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val repoRoot = if (appSrcMain.path.startsWith("app/")) File(".") else File("..")

        // 1. Android: Soft Bento Perspectives card + unconditional divider + explicit Areas (BEREICHE) & Unassigned Projects headers
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()
        val perspCardIdx = smartViewKt.indexOf("item(key = \"sidebar_perspectives_card\")")
        val dividerIdx = smartViewKt.indexOf("item(key = \"sidebar_perspectives_divider\")")
        val areasHeaderIdx = smartViewKt.indexOf("item(key = \"sidebar_areas_header\")")
        val areaItemsIdx = smartViewKt.indexOf("items(areas, key = { \"sidebar_area_\${it.id}\" })")
        assertTrue("Perspectives must be enclosed in sidebar_perspectives_card", perspCardIdx > 0)
        assertTrue("Unconditional divider must follow sidebar_perspectives_card", dividerIdx > perspCardIdx)
        assertTrue("Explicit sidebar_areas_header must follow divider", areasHeaderIdx > dividerIdx)
        assertTrue("Area rows must follow sidebar_areas_header", areaItemsIdx > areasHeaderIdx)
        assertFalse(
            "Perspectives divider must NOT be gated on savedPerspectives.isNotEmpty()",
            smartViewKt.contains("if (savedPerspectives.isNotEmpty()) {\n                item(key = \"sidebar_perspectives_divider\")")
        )
        assertTrue(smartViewKt.contains("item(key = \"sidebar_unassigned_projects_header\")"))
        assertTrue(smartViewKt.contains("item(key = \"nav_perspectives_areas_divider\")"))

        // 2. ChromeOS PWA: Soft Bento .sidebar-perspectives-section + unconditional divider + explicit sidebar-areas-header
        val chromeSidebar = File(repoRoot, "chromeos-pwa/src/components/Sidebar.tsx").readText()
        assertTrue(chromeSidebar.contains("data-testid=\"sidebar-perspectives-areas-divider\""))
        assertTrue(chromeSidebar.contains("data-testid=\"sidebar-areas-header\""))
        assertTrue(chromeSidebar.contains("data-testid=\"sidebar-unassigned-projects-header\""))

        // 3. macOS: Soft Bento perspectivesSection + unconditional Divider + areasSectionHeader & standaloneProjectsSectionHeader
        val macSidebarSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/SidebarView.swift").readText()
        assertTrue(macSidebarSwift.contains("private var areasSectionHeader: some View"))
        assertTrue(macSidebarSwift.contains("private var standaloneProjectsSectionHeader: some View"))
        assertTrue(macSidebarSwift.contains("store.tr(\"entity_areas\").uppercased()"))
    }

    @Test
    fun `v3_0_0_beta_9 section frames and task rows never change width or height when hovering over tasks`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val repoRoot = if (appSrcMain.path.startsWith("app/")) File(".") else File("..")

        val macTaskRowSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/TaskRowCardView.swift").readText()
        assertFalse(
            "TaskRowCardView must NOT conditionally mount/unmount hover buttons on isHoveringRow",
            macTaskRowSwift.contains("if (isHoveringRow")
        )
        assertTrue(macTaskRowSwift.contains(".opacity(showHoverActions ? 1.0 : 0.0)"))
        assertTrue(macTaskRowSwift.contains(".allowsHitTesting(showHoverActions)"))
        assertTrue(macTaskRowSwift.contains(".frame(maxWidth: .infinity, minHeight: 22, alignment: .leading)"))

        val macBoardSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/ProjectBoardAndRoadmapView.swift").readText()
        assertFalse(
            "BoardTaskCard must NOT conditionally mount/unmount its hover action button",
            macBoardSwift.contains("if isHovering {\n                    Button(action: onOpenDetail)")
        )
        assertTrue(macBoardSwift.contains(".opacity(isHovering ? 1.0 : 0.0)"))
        assertTrue(macBoardSwift.contains(".allowsHitTesting(isHovering)"))

        val macCanvasSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/SmartViewCanvas.swift").readText()
        assertTrue(macCanvasSwift.contains(".frame(maxWidth: .infinity, alignment: .leading)"))
    }

    @Test
    fun `v3_0_0_beta_10 sidebar main Bento tiles, mini_cards, and navigation buttons provide subtle zero_shift hover highlights across macOS, ChromeOS PWA, and Android`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val repoRoot = if (appSrcMain.path.startsWith("app/")) File(".") else File("..")

        val macSidebarSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/SidebarView.swift").readText()
        assertTrue(macSidebarSwift.contains("@State private var hoveredSmartView: SmartView? = nil"))
        assertTrue(macSidebarSwift.contains("@State private var isHoveringWeeklyReview = false"))
        assertTrue(macSidebarSwift.contains("@State private var isHoveringNewProjectMenu = false"))

        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()
        assertTrue(smartViewKt.contains("bentoHoverSource.collectIsHoveredAsState()"))

        val chromeCss = File(repoRoot, "chromeos-pwa/src/index.css").readText()
        val bentoHover = Regex("""\.bento-tile:hover\s*\{([^}]*)\}""").find(chromeCss)?.groupValues?.get(1).orEmpty()
        assertTrue(bentoHover.contains("border-color: rgba(56, 152, 236, 0.36);"))
    }

    @Test
    fun `v3_0_0_beta_11 task modal uses balanced 3_plus_3 Soft Bento layout, content_hugging height, slim empty attachments, and single_row footer across platforms`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val repoRoot = if (appSrcMain.path.startsWith("app/")) File(".") else File("..")

        // 1. macOS: Exact measured body height (ModalBodyHeightPreferenceKey + .frame(height: effectiveBodyHeight), never ViewThatFits vertical-centering gap), 3 right-column cards, no duplicate header breadcrumb or footer attach button
        val macModalSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/TaskBentoModalView.swift").readText()
        assertTrue(macModalSwift.contains("ModalBodyHeightPreferenceKey"))
        assertTrue(macModalSwift.contains(".frame(height: effectiveBodyHeight)"))
        assertTrue(macModalSwift.contains(".scrollDisabled(!needsVerticalScroll)"))
        assertFalse(macModalSwift.contains("ViewThatFits(in: .vertical)"))
        assertFalse(macModalSwift.contains(".frame(maxHeight: resolvedMaxModalHeight)"))
        assertTrue(macModalSwift.contains("scheduleBentoCard"))
        assertTrue(macModalSwift.contains("focusAndDependenciesBentoCard"))
        assertTrue(macModalSwift.contains("organizationBentoCard"))
        assertFalse(macModalSwift.contains("isHeaderMovePopoverOpen = true"))
        val footerChunk = macModalSwift.substringAfter("private var modalFooterBar: some View")
        assertFalse("Footer must not duplicate action_attach_file", footerChunk.contains("action_attach_file"))

        // 2. ChromeOS PWA: 3 .task-bento-prop-tile cards + .attachments-empty slim 1-row bar
        val chromeTaskItem = File(repoRoot, "chromeos-pwa/src/components/TaskItem.tsx").readText()
        val propTileCount = Regex("""className="task-bento-prop-tile"""").findAll(chromeTaskItem).count()
        assertTrue("ChromeOS TaskItem must render 3 balanced property cards", propTileCount == 3)

        // 3. Android: 3 consolidated SoftBentoPropertyGroup sections in ExpandableTaskCard
        val androidCard = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ExpandableTaskCard.kt").readText()
        val groupCalls = Regex("""SoftBentoPropertyGroup\(""").findAll(androidCard).count()
        // 1 declaration + 3 invocations = 4 occurrences
        assertTrue("ExpandableTaskCard must consolidate properties into 3 SoftBentoPropertyGroup sections", groupCalls == 4)
    }

    @Test
    fun `v3_0_0_beta_13 supports 1_day and custom durations, subtle pill hover, instant macOS task click selection, and full_presence app icons across platforms`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val repoRoot = if (appSrcMain.path.startsWith("app/")) File(".") else File("..")

        // 1. Duration formatting & custom duration parser (480m = 1d workday)
        assertTrue(com.pv.thingsbook.domain.model.STANDALONE_DURATION_PRESETS.contains(480))
        org.junit.Assert.assertEquals("1d", com.pv.thingsbook.domain.model.formatDurationCompact(480))
        org.junit.Assert.assertEquals("1d 4h", com.pv.thingsbook.domain.model.formatDurationCompact(720))
        org.junit.Assert.assertEquals("2d", com.pv.thingsbook.domain.model.formatDurationCompact(960))
        org.junit.Assert.assertEquals(480, com.pv.thingsbook.domain.model.parseCustomDurationMinutes("1d"))
        org.junit.Assert.assertEquals(1200, com.pv.thingsbook.domain.model.parseCustomDurationMinutes("2,5d"))
        org.junit.Assert.assertEquals(720, com.pv.thingsbook.domain.model.parseCustomDurationMinutes("1d 4h"))
        org.junit.Assert.assertEquals(1440, com.pv.thingsbook.domain.model.parseCustomDurationMinutes("3t"))
        org.junit.Assert.assertEquals(90, com.pv.thingsbook.domain.model.parseCustomDurationMinutes("1:30"))
        org.junit.Assert.assertEquals(45, com.pv.thingsbook.domain.model.parseCustomDurationMinutes("45"))
        org.junit.Assert.assertNull(com.pv.thingsbook.domain.model.parseCustomDurationMinutes("invalid"))

        // 2. Subtle hover on Android ThingsTagPill & macOS BentoMenuPill / BentoWrappingChip
        val badgesKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ThingsBadges.kt").readText()
        assertTrue(badgesKt.contains("collectIsHoveredAsState"))
        val macModalSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/TaskBentoModalView.swift").readText()
        assertTrue(macModalSwift.contains("private struct BentoMenuPill"))
        assertTrue(macModalSwift.contains("private struct BentoWrappingChip"))
        assertTrue(macModalSwift.contains("CustomDurationPopoverView"))

        // 3. Zero TapGesture(count: 2) disambiguation delay on macOS task selection
        val macRowSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/TaskRowCardView.swift").readText()
        assertTrue(macRowSwift.contains(".thingsInstantTaskTap("))
        assertFalse(macRowSwift.contains("TapGesture(count: 2)"))

        // 4. Full-presence app icon sizing on macOS, ChromeOS PWA, and Android
        val macIconSvg = File(repoRoot, "macos/Resources/Assets/AppIcon.svg").readText()
        val pwaFaviconSvg = File(repoRoot, "chromeos-pwa/public/favicon.svg").readText()
        val androidFgXml = File(appSrcMain, "res/drawable/ic_launcher_foreground.xml").readText()
        assertTrue(macIconSvg.contains("""width="864" height="864""""))
        assertTrue(pwaFaviconSvg.contains("""width="504" height="504""""))
        assertTrue(androidFgXml.contains("""android:scaleX="1.18""""))
    }
}
