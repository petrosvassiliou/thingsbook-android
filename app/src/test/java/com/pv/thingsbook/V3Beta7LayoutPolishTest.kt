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
}
