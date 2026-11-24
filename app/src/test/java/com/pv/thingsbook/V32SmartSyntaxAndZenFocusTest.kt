package com.pv.thingsbook

import com.pv.thingsbook.data.backup.BackupTaskDto
import com.pv.thingsbook.data.backup.ThingsBackupCodec
import com.pv.thingsbook.data.backup.ThingsbookBackupPayload
import com.pv.thingsbook.data.local.entity.TaskEntity
import com.pv.thingsbook.data.remote.dto.CloudDeltaBatchPayload
import com.pv.thingsbook.data.remote.dto.TaskFirestoreDto
import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.ProjectStatus
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.parser.SmartSyntaxParser
import com.pv.thingsbook.ui.components.FocusTimerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class V32SmartSyntaxAndZenFocusTest {

    private val refDate: LocalDate = LocalDate.of(2026, 11, 23) // Monday

    private fun epochFor(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val sampleTags = listOf(
        Tag(id = "t-deep", title = "Deep Work", parentTagId = null, shortcut = "d", sortOrder = 1.0, createdAt = 1L, updatedAt = 1L),
        Tag(id = "t-vip", title = "VIP", parentTagId = null, shortcut = "v", sortOrder = 2.0, createdAt = 1L, updatedAt = 1L),
        Tag(id = "t-errands", title = "Errands", parentTagId = null, shortcut = null, sortOrder = 3.0, createdAt = 1L, updatedAt = 1L)
    )

    private val sampleAreas = listOf(
        Area(id = "a-work", title = "Work", sortOrder = 1.0, createdAt = 1L, updatedAt = 1L),
        Area(id = "a-family", title = "Family & Home", sortOrder = 2.0, createdAt = 1L, updatedAt = 1L)
    )

    private val sampleProjects = listOf(
        Project(
            id = "p-launch",
            areaId = "a-work",
            title = "Platform Launch",
            notes = "",
            status = ProjectStatus.ACTIVE,
            scheduleState = ScheduleState.ANYTIME,
            whenDate = null,
            deadline = null,
            completedAt = null,
            sortOrder = 1.0,
            tagIds = emptyList(),
            createdAt = 1L,
            updatedAt = 1L
        ),
        Project(
            id = "p-archived",
            areaId = "a-work",
            title = "Legacy Migration",
            notes = "",
            status = ProjectStatus.COMPLETED,
            scheduleState = ScheduleState.ANYTIME,
            whenDate = null,
            deadline = null,
            completedAt = 100L,
            sortOrder = 2.0,
            tagIds = emptyList(),
            createdAt = 1L,
            updatedAt = 1L
        )
    )

    @Test
    fun `SmartSyntaxParser resolves multilingual dates, deadlines, durations, tags, projects, areas, and milestones`() {
        // English full smart capture
        val en = SmartSyntaxParser.parse(
            rawTitle = "Cut v3.2 release candidate @this-evening !friday ~1.5h #deep-work #VIP /plat !milestone",
            tags = sampleTags,
            projects = sampleProjects,
            areas = sampleAreas,
            locale = Locale.ENGLISH,
            referenceDate = refDate
        )
        assertTrue(en.hasAnyMatch)
        assertEquals("Cut v3.2 release candidate", en.cleanTitle)
        assertEquals(ScheduleState.THIS_EVENING, en.scheduleState)
        assertEquals(epochFor(refDate), en.whenDate)
        assertEquals(epochFor(refDate.plusDays(4)), en.deadline) // Friday Nov 27
        assertEquals(90, en.durationMinutes)
        assertEquals(listOf("t-deep", "t-vip"), en.matchedTagIds)
        assertEquals("p-launch", en.matchedProject?.id)
        assertNull(en.matchedArea)
        assertTrue(en.isMilestone)

        // German (@heute-abend, !morgen, ~45m, standalone ◆, /Work area)
        val de = SmartSyntaxParser.parse(
            rawTitle = "Quartalsbericht prüfen @heute-abend !morgen ~45m ◆ /Work",
            tags = sampleTags,
            projects = sampleProjects,
            areas = sampleAreas,
            locale = Locale.GERMAN,
            referenceDate = refDate
        )
        assertTrue(de.hasAnyMatch)
        assertEquals("Quartalsbericht prüfen", de.cleanTitle)
        assertEquals(ScheduleState.THIS_EVENING, de.scheduleState)
        assertEquals(epochFor(refDate.plusDays(1)), de.deadline)
        assertEquals(45, de.durationMinutes)
        assertTrue(de.isMilestone)
        assertEquals("a-work", de.matchedArea?.id)

        // French (@demain, !2026-12-01, ~2j, /"Family & Home")
        val fr = SmartSyntaxParser.parse(
            rawTitle = "Préparer les vacances @demain !2026-12-01 ~2j /\"Family & Home\"",
            tags = sampleTags,
            projects = sampleProjects,
            areas = sampleAreas,
            locale = Locale.FRENCH,
            referenceDate = refDate
        )
        assertTrue(fr.hasAnyMatch)
        assertEquals("Préparer les vacances", fr.cleanTitle)
        assertEquals(ScheduleState.UPCOMING, fr.scheduleState)
        assertEquals(epochFor(refDate.plusDays(1)), fr.whenDate)
        assertEquals(epochFor(LocalDate.of(2026, 12, 1)), fr.deadline)
        assertEquals(960, fr.durationMinutes)
        assertEquals("a-family", fr.matchedArea?.id)

        // Italian (@stasera) & Spanish (@esta-noche)
        val itParsed = SmartSyntaxParser.parse(
            rawTitle = "Chiamare il cliente @stasera #errands",
            tags = sampleTags,
            projects = sampleProjects,
            areas = sampleAreas,
            locale = Locale.ITALIAN,
            referenceDate = refDate
        )
        assertEquals("Chiamare il cliente", itParsed.cleanTitle)
        assertEquals(ScheduleState.THIS_EVENING, itParsed.scheduleState)
        assertEquals(listOf("t-errands"), itParsed.matchedTagIds)

        val esParsed = SmartSyntaxParser.parse(
            rawTitle = "Revisar presupuesto @esta-noche ~30m",
            tags = sampleTags,
            projects = sampleProjects,
            areas = sampleAreas,
            locale = Locale.forLanguageTag("es"),
            referenceDate = refDate
        )
        assertEquals("Revisar presupuesto", esParsed.cleanTitle)
        assertEquals(ScheduleState.THIS_EVENING, esParsed.scheduleState)
        assertEquals(30, esParsed.durationMinutes)
    }

    @Test
    fun `SmartSyntaxParser preserves URLs, emails, fractions, unmatched tokens, and non-blank cleanTitle`() {
        val urlInput = "Review https://github.com/pv/thingsbook/pull/42#issue-9 and ping dev@company.io for 3/4 rollout @tomorrow"
        val parsedUrl = SmartSyntaxParser.parse(
            rawTitle = urlInput,
            tags = sampleTags,
            projects = sampleProjects,
            areas = sampleAreas,
            locale = Locale.ENGLISH,
            referenceDate = refDate
        )
        assertTrue(parsedUrl.hasAnyMatch)
        assertEquals(
            "Review https://github.com/pv/thingsbook/pull/42#issue-9 and ping dev@company.io for 3/4 rollout",
            parsedUrl.cleanTitle
        )
        assertEquals(epochFor(refDate.plusDays(1)), parsedUrl.whenDate)
        assertNull(parsedUrl.matchedProject)
        assertNull(parsedUrl.matchedArea)
        assertTrue(parsedUrl.matchedTagIds.isEmpty())

        // Completed/inactive projects and unknown tokens are never stripped
        val unmatched = SmartSyntaxParser.parse(
            rawTitle = "Check #unknown /Legacy-Migration !someday ~invalid",
            tags = sampleTags,
            projects = sampleProjects,
            areas = sampleAreas,
            locale = Locale.ENGLISH,
            referenceDate = refDate
        )
        assertFalse(unmatched.hasAnyMatch)
        assertEquals("Check #unknown /Legacy-Migration !someday ~invalid", unmatched.cleanTitle)

        // If stripping all matched tokens would leave cleanTitle blank, keep trimmed rawTitle
        val onlyTokens = SmartSyntaxParser.parse(
            rawTitle = "  @tomorrow ~30m  ",
            tags = sampleTags,
            projects = sampleProjects,
            areas = sampleAreas,
            locale = Locale.ENGLISH,
            referenceDate = refDate
        )
        assertTrue(onlyTokens.hasAnyMatch)
        assertEquals("@tomorrow ~30m", onlyTokens.cleanTitle)
        assertEquals(30, onlyTokens.durationMinutes)
    }

    @Test
    fun `actualMinutes persists across BackupTaskDto and FirestoreDtos while preserving legacy peer omissions`() {
        assertEquals(1, ThingsBackupCodec.FORMAT_VERSION)
        val backupPayload = ThingsbookBackupPayload(
            createdAt = 1_760_000_000_000L,
            appVersionName = "3.2.0",
            tasks = listOf(
                BackupTaskDto(
                    id = "t-focus-1",
                    title = "Deep focus architecture doc",
                    durationMinutes = 60,
                    actualMinutes = 47
                )
            )
        )
        val bytes = ThingsBackupCodec.encodeBackup(
            payload = backupPayload,
            password = null,
            appVersionName = "3.2.0",
            appVersionCode = 116
        )
        val decodedBackup = ThingsBackupCodec.decodeBackup(bytes, password = null)
        assertEquals(47, decodedBackup.tasks.single().actualMinutes)

        val localWithActual = TaskEntity(
            id = "t-sync-focus",
            title = "Write spec",
            notes = "",
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.TODAY,
            durationMinutes = 45,
            actualMinutes = 35,
            sortOrder = 1.0,
            createdAt = 1000L,
            updatedAt = 2000L,
            isDirty = false
        )

        // Full Firestore map roundtrip preserves actualMinutes
        val dtoMap = TaskFirestoreDto.fromEntity(localWithActual).toFirestoreMap()
        assertEquals(35, dtoMap["actualMinutes"])
        val roundTripEntity = TaskFirestoreDto.fromFirestoreMap(dtoMap)!!.toEntity()
        assertEquals(35, roundTripEntity.actualMinutes)

        // Legacy peer payload omitting actualMinutes preserves local actualMinutes
        val legacyMap = dtoMap.toMutableMap().apply { remove("actualMinutes") }
        val legacyDto = TaskFirestoreDto.fromFirestoreMap(legacyMap)!!
        assertTrue(legacyDto.omittedActualMinutesField)
        assertEquals(35, legacyDto.toEntity(existingLocal = localWithActual).actualMinutes)

        // CloudDeltaBatchPayload.decodeWithHints marks omittedActualMinutesField when key is absent
        val legacyBatchJson = """{"userId":"u1","deviceId":"d1","batchTimestamp":3000,"tasks":[{"id":"t-sync-focus","title":"Write spec","status":"OPEN","scheduleState":"TODAY","updatedAt":3000}]}"""
        val decodedBatch = CloudDeltaBatchPayload.decodeWithHints(legacyBatchJson)
        val batchTaskDto = decodedBatch.tasks.single()
        assertTrue(batchTaskDto.omittedActualMinutesField)
        assertEquals(35, batchTaskDto.toEntity(existingLocal = localWithActual).actualMinutes)

        // Purged tombstone clears actualMinutes
        val purgedTombstoneDto = batchTaskDto.copy(title = "", notes = "", isDeleted = true, purgedFromTrash = true)
        assertNull(purgedTombstoneDto.toEntity(existingLocal = localWithActual).actualMinutes)

        // FocusTimerUiState defaults
        val timerUi = FocusTimerUiState(taskId = "t-1", taskTitle = "Focus")
        assertEquals(0, timerUi.sessionFocusedSeconds)
        assertFalse(timerUi.isBreakMode)
    }

    @Test
    fun `v3_2 Room version 8, MIGRATION_7_8, all 26 v32 localization keys, Ctrl_K, and Zen Focus Mode are wired cleanly`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val dbKt = File(appSrcMain, "java/com/pv/thingsbook/data/local/ThingsDatabase.kt").readText()
        val appModuleKt = File(appSrcMain, "java/com/pv/thingsbook/di/AppModule.kt").readText()
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()
        val powerModalsKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ThingsPowerFeaturesModals.kt").readText()
        val viewModelKt = File(appSrcMain, "java/com/pv/thingsbook/ui/viewmodel/ThingsbookViewModel.kt").readText()

        assertTrue(dbKt.contains("version = 9"))
        assertTrue(appModuleKt.contains("MIGRATION_7_8 = object : Migration(7, 8)"))
        assertTrue(appModuleKt.contains("ALTER TABLE `tasks` ADD COLUMN `actualMinutes` INTEGER"))
        assertTrue(appModuleKt.contains("MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9"))

        assertTrue(smartViewKt.contains("KEYCODE_K"))
        assertTrue(smartViewKt.contains("Key.K"))
        assertTrue(smartViewKt.contains("Ctrl+K"))
        assertTrue(smartViewKt.contains("ThingsZenFocusOverlay("))
        assertTrue(powerModalsKt.contains("fun ThingsZenFocusOverlay("))
        assertTrue(viewModelKt.contains("fun createTaskFromSmartSyntax("))
        assertTrue(viewModelKt.contains("fun startZenShortBreak()"))
        assertTrue(viewModelKt.contains("fun flushActiveFocusMinutes(") || viewModelKt.contains("private fun flushActiveFocusMinutes("))

        val requiredV32Keys = listOf(
            "v32_cmd_palette_placeholder",
            "v32_cmd_section_actions",
            "v32_cmd_create_task",
            "v32_cmd_plan_my_day",
            "v32_cmd_ai_architect",
            "v32_cmd_weekly_review",
            "v32_cmd_open_templates",
            "v32_cmd_toggle_theme",
            "v32_cmd_open_settings",
            "v32_cmd_zen_focus",
            "v32_cmd_switch_list",
            "v32_cmd_switch_board",
            "v32_cmd_switch_roadmap",
            "v32_smart_syntax_detected",
            "v32_smart_syntax_apply",
            "v32_smart_syntax_hint",
            "v32_zen_focus_title",
            "v32_zen_expand_tooltip",
            "v32_zen_minimize",
            "v32_zen_short_break",
            "v32_zen_break_active",
            "v32_zen_back_to_focus",
            "v32_actual_focused_short",
            "v32_actual_vs_estimated",
            "v32_logbook_focused_7d",
            "v32_reset_actual_time"
        )
        assertEquals(26, requiredV32Keys.size)

        val resRoot = File(appSrcMain, "res")
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
            for (key in requiredV32Keys) {
                assertNotNull("Missing $key in $folder/strings.xml", keys[key])
                assertTrue("Blank $key in $folder/strings.xml", keys[key]!!.isNotBlank())
            }
        }
    }
}
