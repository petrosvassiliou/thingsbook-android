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

        // 1. macOS: ViewThatFits(in: .vertical) content-hugging height, 3 right-column cards, no duplicate header breadcrumb or footer attach button
        val macModalSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/TaskBentoModalView.swift").readText()
        assertTrue(macModalSwift.contains("ViewThatFits(in: .vertical)"))
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
}
