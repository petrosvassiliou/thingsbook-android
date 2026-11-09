package com.culturedcode.things3.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectDropTargetResolverTest {

    // Layout (px): ungrouped t1 [0,50), t2 [50,100), heading A [100,140), a1 [140,190), heading B [190,230) (empty)
    private val items = listOf(
        ListItemGeometry("ungrouped_t1", 0f, 50f),
        ListItemGeometry("ungrouped_t2", 50f, 100f),
        ListItemGeometry("heading_A", 100f, 140f),
        ListItemGeometry("a1", 140f, 190f),
        ListItemGeometry("heading_B", 190f, 230f),
        ListItemGeometry("trailing", 230f, 340f)
    )
    private val entries: Map<Any, ProjectListEntry> = mapOf(
        "ungrouped_t1" to ProjectListEntry.TaskRow("t1", null, 0),
        "ungrouped_t2" to ProjectListEntry.TaskRow("t2", null, 1),
        "heading_A" to ProjectListEntry.HeadingRow("A", 0),
        "a1" to ProjectListEntry.TaskRow("a1", "A", 0),
        "heading_B" to ProjectListEntry.HeadingRow("B", 1)
    )
    private val headingIds = listOf("A", "B")
    private val sizes = mapOf<String?, Int>(null to 2, "A" to 1, "B" to 0)

    private fun resolve(y: Float, headingMode: Boolean = false) =
        resolveProjectDropTarget(y, items, entries, headingIds, sizes, headingMode)!!

    @Test
    fun upperHalfOfTaskInsertsBefore() {
        val t = resolve(10f)
        assertEquals(null, t.headingId)
        assertEquals(0, t.indexInSection)
        assertEquals(0f, t.indicatorY)
    }

    @Test
    fun lowerHalfOfLastUngroupedTaskAppendsToUngrouped() {
        val t = resolve(102f) // upper part of heading A
        assertEquals(null, t.headingId)
        assertEquals(2, t.indexInSection)
    }

    @Test
    fun lowerPartOfHeadingInsertsAtTopOfHeading() {
        val t = resolve(130f)
        assertEquals("A", t.headingId)
        assertEquals(0, t.indexInSection)
        assertEquals(140f, t.indicatorY)
    }

    @Test
    fun belowTaskInHeadingAppendsToThatHeading() {
        val t = resolve(180f)
        assertEquals("A", t.headingId)
        assertEquals(1, t.indexInSection)
    }

    @Test
    fun emptyHeadingAcceptsDrop() {
        val t = resolve(225f)
        assertEquals("B", t.headingId)
        assertEquals(0, t.indexInSection)
        val below = resolve(300f)
        assertEquals("B", below.headingId)
        assertEquals(0, below.indexInSection)
    }

    @Test
    fun headingModeSnapsToSectionBoundaries() {
        assertEquals(0, resolve(60f, headingMode = true).headingInsertIndex)
        assertEquals(1, resolve(170f, headingMode = true).headingInsertIndex)
        assertEquals(2, resolve(300f, headingMode = true).headingInsertIndex)
    }
}
