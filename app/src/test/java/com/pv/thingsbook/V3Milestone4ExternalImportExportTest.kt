package com.pv.thingsbook

import com.pv.thingsbook.data.external.ExternalExportFormat
import com.pv.thingsbook.data.external.ExternalProjectCodec
import com.pv.thingsbook.domain.model.ChecklistItem
import com.pv.thingsbook.domain.model.EffortLevel
import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.ProjectStatus
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class V3Milestone4ExternalImportExportTest {

    private val berlinZone: ZoneId = ZoneId.of("Europe/Berlin")

    private fun dayMillis(date: LocalDate, zone: ZoneId = berlinZone): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun makeTag(id: String, title: String, sortOrder: Double = 1.0) = Tag(
        id = id,
        title = title,
        parentTagId = null,
        shortcut = null,
        sortOrder = sortOrder,
        createdAt = 1_760_000_000_000L,
        updatedAt = 1_760_000_000_000L
    )

    private fun makeChecklist(
        id: String,
        taskId: String,
        title: String,
        isCompleted: Boolean,
        sortOrder: Double
    ) = ChecklistItem(
        id = id,
        taskId = taskId,
        title = title,
        isCompleted = isCompleted,
        sortOrder = sortOrder,
        createdAt = 1_760_000_000_000L,
        updatedAt = 1_760_000_050_000L
    )

    private val tagBackend = makeTag("tag-backend", "Backend", 1.0)
    private val tagSecurity = makeTag("tag-sec", "Security", 2.0)
    private val tagPlatform = makeTag("tag-platform", "Platform", 3.0)

    private val sampleProject = Project(
        id = "proj-m4",
        areaId = "area-work",
        title = "Enterprise Q4 Migration",
        notes = "Cross-functional Asana & Planner rollout",
        status = ProjectStatus.ACTIVE,
        scheduleState = ScheduleState.ANYTIME,
        whenDate = null,
        deadline = dayMillis(LocalDate.of(2026, 12, 20)),
        completedAt = null,
        sortOrder = 1000.0,
        tagIds = listOf(tagPlatform.id),
        createdAt = 1_760_000_000_000L,
        updatedAt = 1_760_000_100_000L
    )

    private val phaseDiscovery = Heading(
        id = "h-discovery",
        projectId = sampleProject.id,
        title = "Discovery & Architecture",
        startDate = dayMillis(LocalDate.of(2026, 5, 4)),
        endDate = dayMillis(LocalDate.of(2026, 5, 15)),
        sortOrder = 1000.0,
        createdAt = 1_760_000_000_000L,
        updatedAt = 1_760_000_000_000L
    )

    private val phaseRollout = Heading(
        id = "h-rollout",
        projectId = sampleProject.id,
        title = "Production Rollout",
        startDate = dayMillis(LocalDate.of(2026, 5, 18)),
        endDate = dayMillis(LocalDate.of(2026, 5, 29)),
        sortOrder = 2000.0,
        createdAt = 1_760_000_000_000L,
        updatedAt = 1_760_000_000_000L
    )

    private val sampleTasks = listOf(
        TaskItem(
            id = "task-ungrouped",
            title = "Set up project charter",
            notes = "Align stakeholders & milestones",
            status = TaskStatus.COMPLETED,
            scheduleState = ScheduleState.TODAY,
            whenDate = dayMillis(LocalDate.of(2026, 5, 1)),
            deadline = dayMillis(LocalDate.of(2026, 5, 3)),
            reminderTime = null,
            completedAt = 1_760_010_000_000L,
            projectId = sampleProject.id,
            headingId = null,
            areaId = sampleProject.areaId,
            recurrenceRule = null,
            sortOrder = 100.0,
            isPinned = true,
            effortLevel = null,
            assigneeId = "alex@example.com",
            checklistItems = listOf(
                makeChecklist("chk-1", "task-ungrouped", "Draft scope doc", true, 100.0),
                makeChecklist("chk-2", "task-ungrouped", "Sign off budget", false, 200.0)
            ),
            tags = listOf(tagPlatform),
            createdAt = 1_760_000_000_000L,
            updatedAt = 1_760_010_000_000L
        ),
        TaskItem(
            id = "task-disc-1",
            title = "Audit OAuth scopes & secrets",
            notes = "Zero external dependencies required",
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.UPCOMING,
            whenDate = dayMillis(LocalDate.of(2026, 5, 6)),
            deadline = dayMillis(LocalDate.of(2026, 5, 12)),
            reminderTime = null,
            completedAt = null,
            projectId = sampleProject.id,
            headingId = phaseDiscovery.id,
            areaId = sampleProject.areaId,
            recurrenceRule = null,
            sortOrder = 1010.0,
            isPinned = false,
            effortLevel = EffortLevel.DEEP_FOCUS,
            assigneeId = "maria@example.com",
            checklistItems = listOf(
                makeChecklist("chk-3", "task-disc-1", "Inventory keys", true, 100.0),
                makeChecklist("chk-4", "task-disc-1", "Verify rotation policy", false, 200.0),
                makeChecklist("chk-5", "task-disc-1", "Update runbook", true, 300.0)
            ),
            tags = listOf(tagBackend, tagSecurity),
            createdAt = 1_760_000_000_000L,
            updatedAt = 1_760_000_500_000L
        ),
        TaskItem(
            id = "task-roll-1",
            title = "Publish customer changelog",
            notes = "",
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.ANYTIME,
            whenDate = null,
            deadline = dayMillis(LocalDate.of(2026, 5, 28)),
            reminderTime = null,
            completedAt = null,
            projectId = sampleProject.id,
            headingId = phaseRollout.id,
            areaId = sampleProject.areaId,
            recurrenceRule = null,
            sortOrder = 2010.0,
            isPinned = false,
            effortLevel = EffortLevel.QUICK_WIN,
            assigneeId = null,
            checklistItems = emptyList(),
            tags = emptyList(),
            createdAt = 1_760_000_000_000L,
            updatedAt = 1_760_000_500_000L
        )
    )

    @Test
    fun `Asana CSV export and import round-trips sections, subtasks, completion, dates, tags, notes and 2-pass parent order`() {
        val exportedBytes = ExternalProjectCodec.exportProject(
            project = sampleProject,
            headings = listOf(phaseDiscovery, phaseRollout),
            tasks = sampleTasks,
            projectTags = listOf(tagPlatform),
            format = ExternalExportFormat.ASANA_CSV,
            zoneId = berlinZone
        )
        val csvText = String(exportedBytes, StandardCharsets.UTF_8)
        assertTrue("Expected UTF-8 BOM for Excel compatibility", csvText.startsWith("\uFEFF"))
        assertTrue(csvText.contains("Task ID,Created At,Completed At,Last Modified,Name,Section/Column,Assignee,Start Date,Due Date,Tags,Notes,Projects,Parent task"))

        val reimported = ExternalProjectCodec.importAutoDetect(
            bytes = exportedBytes,
            fileNameHint = "q4-asana.csv",
            fallbackTitle = "Fallback",
            zoneId = berlinZone
        )

        assertEquals("Enterprise Q4 Migration", reimported.title)
        assertEquals(3, reimported.totalTaskCount)
        assertEquals(1, reimported.ungroupedTasks.size)
        assertEquals(2, reimported.phases.size)
        assertEquals(
            listOf("Discovery & Architecture", "Production Rollout"),
            reimported.phases.map { it.title }
        )

        val charter = reimported.ungroupedTasks.single()
        assertEquals("Set up project charter", charter.title)
        assertEquals("Align stakeholders & milestones", charter.notes)
        assertEquals(TaskStatus.COMPLETED, charter.status)
        assertNotNull(charter.completedAt)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 1)), charter.whenDate)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 3)), charter.deadline)
        assertEquals("alex@example.com", charter.assigneeId)
        assertEquals(listOf("Platform"), charter.tagNames)
        assertEquals(2, charter.checklistItems.size)
        assertEquals("Draft scope doc", charter.checklistItems[0].title)
        assertTrue(charter.checklistItems[0].isCompleted)
        assertEquals("Sign off budget", charter.checklistItems[1].title)
        assertFalse(charter.checklistItems[1].isCompleted)

        val audit = reimported.phases[0].tasks.single()
        assertEquals("Audit OAuth scopes & secrets", audit.title)
        assertEquals(TaskStatus.OPEN, audit.status)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 6)), audit.whenDate)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 12)), audit.deadline)
        assertEquals("maria@example.com", audit.assigneeId)
        assertEquals(listOf("Backend", "Security"), audit.tagNames)
        assertEquals(3, audit.checklistItems.size)
        assertEquals(listOf(true, false, true), audit.checklistItems.map { it.isCompleted })

        // Verify 2-pass out-of-order subtask resolution, section header rows ('Phase:'), and ignored Untitled section
        val outOfOrderCsv = """
            Task ID,Created At,Completed At,Last Modified,Name,Section/Column,Assignee,Start Date,Due Date,Tags,Notes,Projects,Parent task
            sub-early,2026-05-01T10:00:00Z,2026-05-02T10:00:00Z,2026-05-02T10:00:00Z,Subtask Before Parent,Backend,, , , , , ,parent-99
            sec-hdr,2026-05-01T10:00:00Z,,2026-05-01T10:00:00Z,QA Phase:,,,,,,,Rocket App,
            t-qa,2026-05-01T10:00:00Z,,2026-05-01T10:00:00Z,Run smoke suite,,qa@example.com,2026-05-10,2026-05-11,"QA; Smoke",Verify login,Rocket App,
            parent-99,2026-05-01T10:00:00Z,,2026-05-01T10:00:00Z,Build API,Untitled section,,2026-05-04,2026-05-08,API,,Rocket App,
            orphan-1,2026-05-01T10:00:00Z,,2026-05-01T10:00:00Z,Orphan Task,(no section),,,,, ,Rocket App,non-existent-parent
        """.trimIndent()

        val parsed2Pass = ExternalProjectCodec.importAsanaCsv(outOfOrderCsv, "Fallback", berlinZone)
        assertEquals("Rocket App", parsed2Pass.title)
        assertEquals(listOf("QA Phase"), parsed2Pass.phases.map { it.title })
        assertEquals("Run smoke suite", parsed2Pass.phases.single().tasks.single().title)
        assertEquals(2, parsed2Pass.ungroupedTasks.size)
        val buildApi = parsed2Pass.ungroupedTasks.first { it.title == "Build API" }
        assertEquals(1, buildApi.checklistItems.size)
        assertEquals("Subtask Before Parent", buildApi.checklistItems.single().title)
        assertTrue(buildApi.checklistItems.single().isCompleted)
    }

    @Test
    fun `Asana JSON export and import round-trips both Asana REST data format and structured sections format`() {
        val jsonBytes = ExternalProjectCodec.exportProject(
            project = sampleProject,
            headings = listOf(phaseDiscovery, phaseRollout),
            tasks = sampleTasks,
            projectTags = listOf(tagPlatform),
            format = ExternalExportFormat.ASANA_JSON,
            zoneId = berlinZone
        )
        val jsonText = String(jsonBytes, StandardCharsets.UTF_8)
        assertTrue(jsonText.contains("\"project\""))
        assertTrue(jsonText.contains("\"sections\""))
        assertTrue(jsonText.contains("\"data\""))

        val roundTripped = ExternalProjectCodec.importAutoDetect(
            bytes = jsonBytes,
            fileNameHint = "export.json",
            fallbackTitle = "Fallback",
            zoneId = berlinZone
        )

        assertEquals("Enterprise Q4 Migration", roundTripped.title)
        assertEquals("Cross-functional Asana & Planner rollout", roundTripped.notes)
        assertEquals(listOf("Platform"), roundTripped.tagNames)
        assertEquals(3, roundTripped.totalTaskCount)
        assertEquals(1, roundTripped.ungroupedTasks.size)
        assertEquals(2, roundTripped.phases.size)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 4)), roundTripped.phases[0].startDate)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 15)), roundTripped.phases[0].endDate)
        assertTrue(roundTripped.ungroupedTasks.single().isPinned)
        assertEquals(EffortLevel.DEEP_FOCUS, roundTripped.phases[0].tasks.single().effortLevel)
        assertEquals(EffortLevel.QUICK_WIN, roundTripped.phases[1].tasks.single().effortLevel)

        // Pure Asana REST API {"data": [...]} export with memberships & subtasks
        val rawAsanaRestJson = """
            {
              "data": [
                {
                  "gid": "120001",
                  "name": "Ship mobile beta",
                  "notes": "Test on Pixel & Tab",
                  "completed": false,
                  "start_on": "2026-06-01",
                  "due_on": "2026-06-10",
                  "assignee": { "gid": "u1", "name": "Elena" },
                  "tags": [{ "name": "Android" }, { "name": "Release" }],
                  "memberships": [
                    {
                      "project": { "gid": "p1", "name": "Mobile v3 Launch" },
                      "section": { "gid": "s1", "name": "Beta Track" }
                    }
                  ],
                  "subtasks": [
                    { "gid": "sub1", "name": "Build signed APK", "completed": true },
                    { "gid": "sub2", "name": "Verify upgrade path", "completed": false }
                  ]
                },
                {
                  "gid": "120002",
                  "name": "Kickoff retro",
                  "completed": true,
                  "completed_at": "2026-05-29T16:00:00Z",
                  "memberships": [
                    {
                      "project": { "gid": "p1", "name": "Mobile v3 Launch" },
                      "section": { "gid": "s0", "name": "Untitled section" }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val fromRest = ExternalProjectCodec.importAsanaJson(rawAsanaRestJson, "Fallback", berlinZone)
        assertEquals("Mobile v3 Launch", fromRest.title)
        assertEquals(1, fromRest.ungroupedTasks.size)
        assertEquals("Kickoff retro", fromRest.ungroupedTasks.single().title)
        assertEquals(TaskStatus.COMPLETED, fromRest.ungroupedTasks.single().status)
        assertEquals(listOf("Beta Track"), fromRest.phases.map { it.title })
        val betaTask = fromRest.phases.single().tasks.single()
        assertEquals("Ship mobile beta", betaTask.title)
        assertEquals("Elena", betaTask.assigneeId)
        assertEquals(listOf("Android", "Release"), betaTask.tagNames)
        assertEquals(2, betaTask.checklistItems.size)
        assertTrue(betaTask.checklistItems[0].isCompleted)
        assertFalse(betaTask.checklistItems[1].isCompleted)

        // Pure structured export JSON without "data" array
        val structuredJson = """
            {
              "project": { "name": "Structured Roadmap", "notes": "Offline JSON" },
              "sections": [
                {
                  "name": "Milestone 1",
                  "start_on": "2026-07-01",
                  "due_on": "2026-07-14",
                  "tasks": [
                    { "name": "Design schema", "due_on": "2026-07-05", "subtasks": ["Draft ERD"] }
                  ]
                }
              ],
              "tasks": [
                { "name": "Top-level prep", "completed": false }
              ]
            }
        """.trimIndent()

        val fromStructured = ExternalProjectCodec.importAsanaJson(structuredJson, "Fallback", berlinZone)
        assertEquals("Structured Roadmap", fromStructured.title)
        assertEquals("Offline JSON", fromStructured.notes)
        assertEquals(1, fromStructured.ungroupedTasks.size)
        assertEquals(1, fromStructured.phases.size)
        assertEquals(dayMillis(LocalDate.of(2026, 7, 1)), fromStructured.phases[0].startDate)
        assertEquals(dayMillis(LocalDate.of(2026, 7, 14)), fromStructured.phases[0].endDate)
        assertEquals("Draft ERD", fromStructured.phases[0].tasks.single().checklistItems.single().title)
    }

    @Test
    fun `Microsoft Planner xlsx binary OOXML export and import round-trips ZIP structure, sharedStrings, Excel serial dates, buckets, checklists, priority and labels`() {
        // Verify exact Excel epoch 1899-12-30 serial conversion (46154 -> 2026-05-12)
        assertEquals(LocalDate.of(2026, 5, 12), ExternalProjectCodec.excelSerialToLocalDate(46154.0))
        assertEquals(46154L, ExternalProjectCodec.localDateToExcelSerial(LocalDate.of(2026, 5, 12)))
        assertEquals(
            dayMillis(LocalDate.of(2026, 5, 12)),
            ExternalProjectCodec.parseExternalDate("46154", berlinZone)
        )
        assertEquals(
            dayMillis(LocalDate.of(2026, 5, 12)),
            ExternalProjectCodec.parseExternalDate("46154.0", berlinZone)
        )

        val xlsxBytes = ExternalProjectCodec.exportProject(
            project = sampleProject,
            headings = listOf(phaseDiscovery, phaseRollout),
            tasks = sampleTasks,
            projectTags = listOf(tagPlatform),
            format = ExternalExportFormat.PLANNER_XLSX,
            zoneId = berlinZone
        )

        // Verify PK\u0003\u0004 ZIP signature and required OOXML package entries
        assertTrue(xlsxBytes.size > 4)
        assertEquals(0x50.toByte(), xlsxBytes[0])
        assertEquals(0x4B.toByte(), xlsxBytes[1])

        val zipEntries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = ByteArrayOutputStream()
                zis.copyTo(out)
                zipEntries[entry.name] = out.toByteArray()
                zis.closeEntry()
            }
        }

        val requiredEntries = listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/styles.xml",
            "xl/sharedStrings.xml",
            "xl/worksheets/sheet1.xml"
        )
        for (req in requiredEntries) {
            assertTrue("Missing required OOXML entry: $req", req in zipEntries)
        }

        // Verify every XML entry in the .xlsx archive parses cleanly with zero XML errors
        val dbf = DocumentBuilderFactory.newInstance()
        for ((entryName, xmlBytes) in zipEntries) {
            if (entryName.endsWith(".xml") || entryName.endsWith(".rels")) {
                val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
                assertNotNull("Failed parsing XML in $entryName", doc.documentElement)
            }
        }

        val sheetXmlString = String(zipEntries.getValue("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8)
        assertTrue("Expected Excel serial 46154 in worksheet XML", sheetXmlString.contains("<v>46154</v>"))

        // Round-trip import via auto-detect
        val reimported = ExternalProjectCodec.importAutoDetect(
            bytes = xlsxBytes,
            fileNameHint = "export.xlsx",
            fallbackTitle = "Fallback",
            zoneId = berlinZone
        )

        assertEquals("Enterprise Q4 Migration", reimported.title)
        assertEquals("Cross-functional Asana & Planner rollout", reimported.notes)
        assertEquals(3, reimported.totalTaskCount)
        assertEquals(1, reimported.ungroupedTasks.size)
        assertEquals(
            listOf("Discovery & Architecture", "Production Rollout"),
            reimported.phases.map { it.title }
        )

        val ungroupedCharter = reimported.ungroupedTasks.single()
        assertEquals(TaskStatus.COMPLETED, ungroupedCharter.status)
        assertTrue("Urgent priority should round-trip to isPinned = true", ungroupedCharter.isPinned)
        assertEquals("alex@example.com", ungroupedCharter.assigneeId)
        assertEquals(listOf(true, false), ungroupedCharter.checklistItems.map { it.isCompleted })

        val discoveryAudit = reimported.phases[0].tasks.single()
        assertEquals(EffortLevel.DEEP_FOCUS, discoveryAudit.effortLevel)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 6)), discoveryAudit.whenDate)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 12)), discoveryAudit.deadline)
        assertEquals(listOf("Backend", "Security"), discoveryAudit.tagNames)
        assertEquals(listOf(true, false, true), discoveryAudit.checklistItems.map { it.isCompleted })

        val rolloutChangelog = reimported.phases[1].tasks.single()
        assertEquals(EffortLevel.QUICK_WIN, rolloutChangelog.effortLevel)
        assertNull(rolloutChangelog.whenDate)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 28)), rolloutChangelog.deadline)

        // Also test sparse cells, inlineStr, workbook sheet name fallback, localized "Erledigt", and "2/3" checklist ratio
        val customSparseXlsx = buildCustomSparsePlannerXlsx()
        val sparseImported = ExternalProjectCodec.importPlannerXlsx(customSparseXlsx, "Fallback", berlinZone)
        assertEquals("Q3 Marketing Plan", sparseImported.title)
        assertEquals(1, sparseImported.phases.size)
        assertEquals("Social Media", sparseImported.phases.single().title)
        val socialTask = sparseImported.phases.single().tasks.single()
        assertEquals("Launch teaser video", socialTask.title)
        assertEquals(TaskStatus.COMPLETED, socialTask.status)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 12)), socialTask.deadline)
        assertEquals(3, socialTask.checklistItems.size)
        assertEquals(listOf(true, true, false), socialTask.checklistItems.map { it.isCompleted })
    }

    @Test
    fun `Microsoft Planner CSV export and import supports both comma and semicolon delimiters and flexible dates`() {
        val plannerCsvBytes = ExternalProjectCodec.exportProject(
            project = sampleProject,
            headings = listOf(phaseDiscovery, phaseRollout),
            tasks = sampleTasks,
            format = ExternalExportFormat.PLANNER_CSV,
            zoneId = berlinZone
        )

        val roundTripped = ExternalProjectCodec.importAutoDetect(
            bytes = plannerCsvBytes,
            fileNameHint = "Enterprise Q4 Migration.csv",
            fallbackTitle = "Fallback",
            zoneId = berlinZone
        )
        assertEquals("Enterprise Q4 Migration", roundTripped.title)
        assertEquals(3, roundTripped.totalTaskCount)
        assertEquals(2, roundTripped.phases.size)

        // Semicolon-delimited European Planner CSV with MM/DD/YYYY, DD.MM.YYYY, and DD/MM/YYYY dates
        val semicolonPlannerCsv = """
            Task ID;Task Name;Bucket Name;Progress;Priority;Assigned To;Created By;Created Date;Start Date;Due Date;Late;Completed Date;Description;Completed Checklist Items;Checklist Items;Labels
            p-1;Prepare legal review;Compliance;Terminé;Urgent;legal@example.com;;05/01/2026;10.05.2026;24/05/2026;false;2026-05-20;GDPR sign-off;1/2;"Check DPA; Sign addendum";"Legal, GDPR"
            p-2;Triage backlog;;Not started;Low;;;;05/14/2026;;;;;;;"Triage"
        """.trimIndent()

        val semiImported = ExternalProjectCodec.importAutoDetect(
            bytes = semicolonPlannerCsv.toByteArray(StandardCharsets.UTF_8),
            fileNameHint = "European_Plan.csv",
            fallbackTitle = "Fallback",
            zoneId = berlinZone
        )
        assertEquals("European_Plan", semiImported.title)
        assertEquals(1, semiImported.ungroupedTasks.size)
        assertEquals(EffortLevel.QUICK_WIN, semiImported.ungroupedTasks.single().effortLevel)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 14)), semiImported.ungroupedTasks.single().whenDate)

        assertEquals(listOf("Compliance"), semiImported.phases.map { it.title })
        val legalTask = semiImported.phases.single().tasks.single()
        assertEquals(TaskStatus.COMPLETED, legalTask.status)
        assertTrue(legalTask.isPinned)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 10)), legalTask.whenDate)
        assertEquals(dayMillis(LocalDate.of(2026, 5, 24)), legalTask.deadline)
        assertEquals(listOf("Legal", "GDPR"), legalTask.tagNames)
        assertEquals(listOf(true, false), legalTask.checklistItems.map { it.isCompleted })
    }

    @Test
    fun `All 14 Milestone 4 external import and export localization keys exist across all 5 supported locales`() {
        val resRoot = sequenceOf(
            File("src/main/res"),
            File("app/src/main/res")
        ).first { it.isDirectory }

        val folders = listOf("values", "values-de", "values-fr", "values-it", "values-es")
        val milestone4Keys = listOf(
            "v30_external_import_export_tab",
            "v30_import_asana",
            "v30_export_asana_csv",
            "v30_export_asana_json",
            "v30_import_planner",
            "v30_export_planner_xlsx",
            "v30_export_planner_csv",
            "v30_external_import_success",
            "v30_external_import_section",
            "v30_external_import_desc",
            "v30_external_export_section",
            "v30_external_select_project",
            "v30_external_no_projects",
            "v30_external_export_success"
        )
        assertEquals(14, milestone4Keys.size)

        val dbf = DocumentBuilderFactory.newInstance()
        for (folder in folders) {
            val file = File(resRoot, "$folder/strings.xml")
            assertTrue("Missing $folder/strings.xml", file.isFile)
            val doc = dbf.newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            val keys = HashMap<String, String>(nodes.length)
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i) as Element
                keys[elem.getAttribute("name")] = elem.textContent.orEmpty()
            }
            for (key in milestone4Keys) {
                val value = keys[key]
                assertNotNull("Missing '$key' in $folder/strings.xml", value)
                assertTrue("Blank '$key' in $folder/strings.xml", value!!.isNotBlank())
            }
            assertTrue(keys.getValue("v30_external_import_success").contains("%1\$s"))
            assertTrue(keys.getValue("v30_external_import_success").contains("%2\$d"))
            assertTrue(keys.getValue("v30_external_export_success").contains("%1\$s"))
        }
    }

    private fun buildCustomSparsePlannerXlsx(): ByteArray {
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheets><sheet name="Q3 Marketing Plan" sheetId="1"/></sheets>
            </workbook>
        """.trimIndent()

        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="4" uniqueCount="4">
              <si><t>Task Name</t></si>
              <si><t>Bucket Name</t></si>
              <si><t>Progress</t></si>
              <si><r><t>Social </t></r><r><t>Media</t></r></si>
            </sst>
        """.trimIndent()

        // Sparse columns: B=Task Name, C=Bucket Name, D=Progress, J=Due Date, N=Completed Checklist Items, O=Checklist Items
        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="B1" t="s"><v>0</v></c>
                  <c r="C1" t="s"><v>1</v></c>
                  <c r="D1" t="s"><v>2</v></c>
                  <c r="J1" t="inlineStr"><is><t>Due Date</t></is></c>
                  <c r="N1" t="inlineStr"><is><t>Completed Checklist Items</t></is></c>
                  <c r="O1" t="inlineStr"><is><t>Checklist Items</t></is></c>
                </row>
                <row r="2">
                  <c r="B2" t="inlineStr"><is><t>Launch teaser video</t></is></c>
                  <c r="C2" t="s"><v>3</v></c>
                  <c r="D2" t="inlineStr"><is><t>Erledigt</t></is></c>
                  <c r="J2"><v>46154.0</v></c>
                  <c r="N2" t="inlineStr"><is><t>2/3</t></is></c>
                  <c r="O2" t="inlineStr"><is><t>Script; Edit; Publish</t></is></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            fun put(name: String, xml: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(xml.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
            put("xl/workbook.xml", workbookXml)
            put("xl/sharedStrings.xml", sharedStringsXml)
            put("xl/worksheets/sheet1.xml", sheetXml)
        }
        return out.toByteArray()
    }
}
