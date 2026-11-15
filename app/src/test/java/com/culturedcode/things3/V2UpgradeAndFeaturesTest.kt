package com.culturedcode.things3

import com.culturedcode.things3.data.backup.BackupAreaDto
import com.culturedcode.things3.data.backup.BackupChecklistItemDto
import com.culturedcode.things3.data.backup.BackupHeadingDto
import com.culturedcode.things3.data.backup.BackupInspectionResult
import com.culturedcode.things3.data.backup.BackupPreferencesDto
import com.culturedcode.things3.data.backup.BackupProjectDto
import com.culturedcode.things3.data.backup.BackupProjectTemplateDto
import com.culturedcode.things3.data.backup.BackupTaskDto
import com.culturedcode.things3.data.backup.ThingsBackupCodec
import com.culturedcode.things3.data.backup.ThingsbookBackupEnvelope
import com.culturedcode.things3.data.backup.ThingsbookBackupPayload
import com.culturedcode.things3.data.remote.dto.CloudDeltaBatchPayload
import com.culturedcode.things3.data.remote.dto.ProjectFirestoreDto
import com.culturedcode.things3.data.remote.dto.ProjectTemplateFirestoreDto
import com.culturedcode.things3.data.remote.dto.TaskFirestoreDto
import com.culturedcode.things3.domain.model.CalendarEventItem
import com.culturedcode.things3.domain.model.Project
import com.culturedcode.things3.domain.model.ProjectOutlineImporter
import com.culturedcode.things3.domain.model.ProjectStatus
import com.culturedcode.things3.domain.model.ProjectTemplateBlueprint
import com.culturedcode.things3.domain.model.ProjectTemplateCodec
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.StalledProjectDetector
import com.culturedcode.things3.domain.model.StalledProjectReason
import com.culturedcode.things3.domain.model.StarterProjectTemplates
import com.culturedcode.things3.domain.model.TaskItem
import com.culturedcode.things3.domain.model.TaskStatus
import com.culturedcode.things3.domain.model.TemplateHeadingBlueprint
import com.culturedcode.things3.domain.model.TemplateTaskBlueprint
import com.culturedcode.things3.domain.model.TimeblockScheduleUtils
import com.culturedcode.things3.domain.model.TodayTimelineEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class V2UpgradeAndFeaturesTest {

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `tbbackup from v1_8_1 without v2 fields imports with zero data loss and v2_0 backup roundtrips templates and timeblocks`() {
        // 1. Handcraft an exact v1.8.1 backup payload JSON (no scheduledStartMinutes, durationMinutes, lastReviewedAt, or projectTemplates)
        val rawV181PayloadJson = """
            {
              "schemaVersion": 1,
              "createdAt": 1730000000000,
              "appVersionName": "1.8.1",
              "areas": [
                {
                  "id": "area-work",
                  "title": "Work",
                  "sortOrder": 1.0,
                  "tagIdsJson": "[]",
                  "createdAt": 1720000000000,
                  "updatedAt": 1730000000000
                }
              ],
              "projects": [
                {
                  "id": "proj-q4",
                  "areaId": "area-work",
                  "title": "Q4 Roadmap",
                  "notes": "Ship smoothly",
                  "status": "ACTIVE",
                  "scheduleState": "ANYTIME",
                  "sortOrder": 1.0,
                  "tagIdsJson": "[\"tag-focus\"]",
                  "createdAt": 1720000000000,
                  "updatedAt": 1730000000000
                }
              ],
              "headings": [
                {
                  "id": "head-1",
                  "projectId": "proj-q4",
                  "title": "Phase 1",
                  "sortOrder": 1.0,
                  "isArchived": false,
                  "createdAt": 1720000000000,
                  "updatedAt": 1730000000000
                }
              ],
              "tasks": [
                {
                  "id": "task-legacy-1",
                  "title": "Review v1.8 architecture",
                  "notes": "Keep backward compatible",
                  "status": "OPEN",
                  "scheduleState": "TODAY",
                  "whenDate": 1730000000000,
                  "projectId": "proj-q4",
                  "headingId": "head-1",
                  "sortOrder": 1.0,
                  "tagIdsJson": "[\"tag-focus\"]",
                  "createdAt": 1720000000000,
                  "updatedAt": 1730000000000
                }
              ],
              "checklistItems": [
                {
                  "id": "chk-1",
                  "taskId": "task-legacy-1",
                  "title": "Verify Room migration",
                  "isCompleted": true,
                  "sortOrder": 1.0,
                  "createdAt": 1720000000000,
                  "updatedAt": 1730000000000
                }
              ],
              "tags": [],
              "taskTagRefs": [{"taskId": "task-legacy-1", "tagId": "tag-focus"}],
              "projectTagRefs": [{"projectId": "proj-q4", "tagId": "tag-focus"}],
              "attachments": [],
              "preferences": {
                "sidebarVisible": true,
                "dailyDigestEnabled": true,
                "dailyDigestHour": 8,
                "dailyDigestMinute": 30,
                "languageTag": "de"
              }
            }
        """.trimIndent()

        val gzipOut = ByteArrayOutputStream()
        GZIPOutputStream(gzipOut).use { it.write(rawV181PayloadJson.toByteArray(StandardCharsets.UTF_8)) }
        val v181Envelope = ThingsbookBackupEnvelope(
            magic = ThingsBackupCodec.BACKUP_MAGIC,
            formatVersion = ThingsBackupCodec.FORMAT_VERSION,
            appVersionName = "1.8.1",
            appVersionCode = 44,
            createdAt = 1_730_000_000_000L,
            encrypted = false,
            payloadGzipBase64 = Base64.getEncoder().encodeToString(gzipOut.toByteArray())
        )
        val v181ArchiveBytes = lenientJson.encodeToString(v181Envelope).toByteArray(StandardCharsets.UTF_8)

        val inspection = ThingsBackupCodec.inspectBackup(v181ArchiveBytes)
        assertTrue(inspection is BackupInspectionResult.UnencryptedPreview)
        val preview = inspection as BackupInspectionResult.UnencryptedPreview
        assertEquals("1.8.1", preview.appVersionName)
        assertEquals(1, preview.summary.projectsCount)
        assertEquals(1, preview.summary.tasksCount)
        assertEquals(0, preview.summary.projectTemplatesCount)

        val decodedLegacy = ThingsBackupCodec.decodeBackup(v181ArchiveBytes, password = null)
        assertEquals(1, decodedLegacy.areas.size)
        assertEquals("Q4 Roadmap", decodedLegacy.projects.single().title)
        assertNull(decodedLegacy.projects.single().lastReviewedAt)
        assertEquals("Review v1.8 architecture", decodedLegacy.tasks.single().title)
        assertNull(decodedLegacy.tasks.single().scheduledStartMinutes)
        assertNull(decodedLegacy.tasks.single().durationMinutes)
        assertTrue(decodedLegacy.projectTemplates.isEmpty())
        assertEquals("de", decodedLegacy.preferences.languageTag)

        // 2. Roundtrip a full v2.0.0-beta.1 encrypted & unencrypted backup with templates, timeblocks, and review timestamps
        val blueprint = ProjectTemplateBlueprint(
            title = "Release Sprint",
            notes = "Reusable sprint template",
            headings = listOf(
                TemplateHeadingBlueprint(
                    title = "QA",
                    tasks = listOf(
                        TemplateTaskBlueprint(
                            title = "Run upgrade suite",
                            checklistItems = listOf("v1.7 -> v2.0", "v1.8 -> v2.0"),
                            dayOffset = 0,
                            scheduledStartMinutes = 9 * 60 + 30,
                            durationMinutes = 45
                        )
                    )
                )
            )
        )
        val v2Payload = ThingsbookBackupPayload(
            schemaVersion = ThingsBackupCodec.FORMAT_VERSION,
            createdAt = 1_760_000_000_000L,
            appVersionName = "2.0.0-beta.1",
            areas = listOf(BackupAreaDto(id = "a1", title = "Engineering")),
            projects = listOf(
                BackupProjectDto(
                    id = "p1",
                    areaId = "a1",
                    title = "Thingsbook 2.0",
                    lastReviewedAt = 1_759_900_000_000L
                )
            ),
            headings = listOf(BackupHeadingDto(id = "h1", projectId = "p1", title = "Beta 1")),
            tasks = listOf(
                BackupTaskDto(
                    id = "t1",
                    title = "Ship Timeblocking",
                    scheduleState = ScheduleState.TODAY.name,
                    scheduledStartMinutes = 540,
                    durationMinutes = 60,
                    projectId = "p1",
                    headingId = "h1"
                )
            ),
            checklistItems = listOf(BackupChecklistItemDto(id = "c1", taskId = "t1", title = "Timeline conflict badge")),
            projectTemplates = listOf(
                BackupProjectTemplateDto(
                    id = "tpl-1",
                    title = "Release Sprint",
                    notes = "Reusable sprint template",
                    defaultAreaId = "a1",
                    blueprintJson = ProjectTemplateCodec.encodeBlueprint(blueprint),
                    createdAt = 1_760_000_000_000L,
                    updatedAt = 1_760_000_000_000L
                )
            ),
            preferences = BackupPreferencesDto(
                lastWeeklyReviewAt = 1_759_950_000_000L,
                includeBetaUpdates = true
            )
        )

        val encryptedBytes = ThingsBackupCodec.encodeBackup(
            payload = v2Payload,
            password = "Things2BetaPassword!".toCharArray(),
            appVersionName = "2.0.0-beta.1",
            appVersionCode = 45
        )
        val envelopeCheck = lenientJson.decodeFromString<ThingsbookBackupEnvelope>(
            String(encryptedBytes, StandardCharsets.UTF_8)
        )
        assertEquals("THINGSBOOK_BACKUP_V1", envelopeCheck.magic)
        assertEquals(1, envelopeCheck.formatVersion)

        val roundtripped = ThingsBackupCodec.decodeBackup(
            rawBytes = encryptedBytes,
            password = "Things2BetaPassword!".toCharArray()
        )
        assertEquals(540, roundtripped.tasks.single().scheduledStartMinutes)
        assertEquals(60, roundtripped.tasks.single().durationMinutes)
        assertEquals(1_759_900_000_000L, roundtripped.projects.single().lastReviewedAt)
        assertEquals(1, roundtripped.projectTemplates.size)
        val decodedBp = ProjectTemplateCodec.decodeBlueprint(roundtripped.projectTemplates.single().blueprintJson)
        assertEquals("Release Sprint", decodedBp.title)
        assertEquals(45, decodedBp.headings.single().tasks.single().durationMinutes)
        assertEquals(1_759_950_000_000L, roundtripped.preferences.lastWeeklyReviewAt)
        assertEquals(true, roundtripped.preferences.includeBetaUpdates)
    }

    @Test
    fun `FirestoreDtos maintain bidirectional 1_8 and 2_0 wire compatibility`() {
        // v1.8 Firestore map without v2 fields -> v2 DTO defaults cleanly to null
        val v18TaskMap: Map<String, Any?> = mapOf(
            "id" to "task-18",
            "title" to "Sync from 1.8.1 phone",
            "status" to "OPEN",
            "scheduleState" to "TODAY",
            "updatedAt" to 1735000000000L
        )
        val parsedTask18 = TaskFirestoreDto.fromFirestoreMap(v18TaskMap)
        assertNotNull(parsedTask18)
        assertNull(parsedTask18!!.scheduledStartMinutes)
        assertNull(parsedTask18.durationMinutes)
        val entity18 = parsedTask18.toEntity()
        assertNull(entity18.scheduledStartMinutes)
        assertNull(entity18.durationMinutes)

        val v18ProjectMap: Map<String, Any?> = mapOf(
            "id" to "proj-18",
            "title" to "Sync Project 1.8",
            "status" to "ACTIVE",
            "scheduleState" to "ANYTIME",
            "updatedAt" to 1735000000000L
        )
        val parsedProj18 = ProjectFirestoreDto.fromFirestoreMap(v18ProjectMap)
        assertNotNull(parsedProj18)
        assertNull(parsedProj18!!.lastReviewedAt)

        // v1.8 ntfy CloudDeltaBatchPayload JSON without projectTemplates or timeblock keys
        val v18DeltaJson = """
            {
              "userId": "u-123",
              "deviceId": "pixel-8",
              "batchTimestamp": 1735000000000,
              "tasks": [{"id": "t-delta", "title": "Quick capture", "status": "OPEN", "scheduleState": "INBOX"}],
              "projects": [{"id": "p-delta", "title": "Client Project"}]
            }
        """.trimIndent()
        val parsedBatch18 = lenientJson.decodeFromString<CloudDeltaBatchPayload>(v18DeltaJson)
        assertEquals(1, parsedBatch18.tasks.size)
        assertNull(parsedBatch18.tasks.single().scheduledStartMinutes)
        assertNull(parsedBatch18.projects.single().lastReviewedAt)
        assertTrue(parsedBatch18.projectTemplates.isEmpty())

        // v2.0 ProjectTemplateFirestoreDto + Task timeblock + Project lastReviewedAt roundtrip
        val tplDto = ProjectTemplateFirestoreDto(
            id = "tpl-cloud-1",
            title = "Client Onboarding",
            notes = "Standard onboarding checklist",
            defaultAreaId = "area-work",
            tagIds = listOf("tag-client"),
            blueprintJson = """{"title":"Client Onboarding","unheadedTasks":[{"title":"Kickoff call","durationMinutes":30}]}""",
            sortOrder = 2.5,
            createdAt = 1760000000000L,
            updatedAt = 1760000050000L,
            isDeleted = false
        )
        val tplMap = tplDto.toFirestoreMap()
        val tplFromMap = ProjectTemplateFirestoreDto.fromFirestoreMap(tplMap)
        assertNotNull(tplFromMap)
        val tplEntity = tplFromMap!!.toEntity()
        assertEquals("tpl-cloud-1", tplEntity.id)
        assertEquals("area-work", tplEntity.defaultAreaId)
        val tplBackToDto = ProjectTemplateFirestoreDto.fromEntity(tplEntity)
        assertEquals(listOf("tag-client"), tplBackToDto.tagIds)

        val v2TaskDto = TaskFirestoreDto(
            id = "task-20",
            title = "Deep Work Architecture",
            scheduleState = "TODAY",
            scheduledStartMinutes = 600,
            durationMinutes = 90
        )
        val v2TaskRoundtrip = TaskFirestoreDto.fromFirestoreMap(v2TaskDto.toFirestoreMap())!!
        assertEquals(600, v2TaskRoundtrip.scheduledStartMinutes)
        assertEquals(90, v2TaskRoundtrip.durationMinutes)
    }

    @Test
    fun `Markdown and JSON outline importer parses headings, tasks, checklists, and timeblocks`() {
        val markdown = """
            # Product Launch v2.0
             Coordinating Android, macOS, and PWA launch.
            - [ ] Confirm release tag @09:00 (15m)
              - [ ] Check versionCode 45
              - [ ] Check keystore signature
            ## Engineering Readiness
            - [ ] Run unit tests & assembleRelease (45m)
              - [ ] AppUpdateParserTest
              - [ ] V2UpgradeAndFeaturesTest
            - [x] Verify 5-language strings
            ## Announcement
            * Publish beta release notes @14:30 (30m)
        """.trimIndent()

        val blueprint = ProjectOutlineImporter.parseOutline(markdown)
        assertEquals("Product Launch v2.0", blueprint.title)
        assertTrue(blueprint.notes.contains("Coordinating Android"))
        assertEquals(1, blueprint.unheadedTasks.size)
        val topTask = blueprint.unheadedTasks.single()
        assertEquals("Confirm release tag", topTask.title)
        assertEquals(9 * 60, topTask.scheduledStartMinutes)
        assertEquals(15, topTask.durationMinutes)
        assertEquals(listOf("Check versionCode 45", "Check keystore signature"), topTask.checklistItems)

        assertEquals(2, blueprint.headings.size)
        assertEquals("Engineering Readiness", blueprint.headings[0].title)
        assertEquals(2, blueprint.headings[0].tasks.size)
        assertEquals("Run unit tests & assembleRelease", blueprint.headings[0].tasks[0].title)
        assertEquals(45, blueprint.headings[0].tasks[0].durationMinutes)
        assertEquals(listOf("AppUpdateParserTest", "V2UpgradeAndFeaturesTest"), blueprint.headings[0].tasks[0].checklistItems)

        assertEquals("Announcement", blueprint.headings[1].title)
        assertEquals("Publish beta release notes", blueprint.headings[1].tasks.single().title)
        assertEquals(14 * 60 + 30, blueprint.headings[1].tasks.single().scheduledStartMinutes)
        assertEquals(30, blueprint.headings[1].tasks.single().durationMinutes)

        // JSON outline import
        val jsonOutline = """
            {
              "title": "JSON Sprint",
              "headings": [
                {
                  "title": "Backlog",
                  "tasks": [
                    {"title": "Design review", "checklist": ["Mobile", "Tablet"], "durationMinutes": 60}
                  ]
                }
              ]
            }
        """.trimIndent()
        val parsedJsonBp = ProjectOutlineImporter.parseOutline(jsonOutline)
        assertEquals("JSON Sprint", parsedJsonBp.title)
        assertEquals("Backlog", parsedJsonBp.headings.single().title)
        assertEquals(listOf("Mobile", "Tablet"), parsedJsonBp.headings.single().tasks.single().checklistItems)
        assertEquals(60, parsedJsonBp.headings.single().tasks.single().durationMinutes)

        // Localized fallbackTitle when outline has no explicit project title
        assertEquals("Neues Projekt", ProjectOutlineImporter.parseOutline("", fallbackTitle = "Neues Projekt").title)
        assertEquals("Nouveau projet", ProjectOutlineImporter.parseOutline("[\"Tâche 1\"]", fallbackTitle = "Nouveau projet").title)

        // Starter GTD templates gallery has all 4 built-in templates with structured tasks
        assertEquals(4, StarterProjectTemplates.builtInTemplates.size)
        assertTrue(StarterProjectTemplates.builtInTemplates.all { it.isBuiltIn && it.blueprint.totalTaskCount >= 3 })
    }

    @Test
    fun `StarterProjectTemplates localizes all 4 templates including headings tasks and checklists in en de fr it es`() {
        val enTemplates = StarterProjectTemplates.getBuiltInTemplates("en")
        assertEquals(
            listOf("builtin_trip_packing", "builtin_product_launch", "builtin_client_onboarding", "builtin_weekly_sprint"),
            enTemplates.map { it.id }
        )

        val localizedCodes = listOf("de", "fr", "it", "es")
        for (lang in localizedCodes) {
            val localized = StarterProjectTemplates.getBuiltInTemplates(lang)
            assertEquals("Expected 4 templates for $lang", 4, localized.size)
            assertEquals(enTemplates.map { it.id }, localized.map { it.id })

            for (idx in enTemplates.indices) {
                val enTpl = enTemplates[idx]
                val locTpl = localized[idx]

                assertTrue(locTpl.isBuiltIn)
                assertNotEquals("Template title not localized for $lang (${enTpl.id})", enTpl.title, locTpl.title)
                assertNotEquals("Template notes not localized for $lang (${enTpl.id})", enTpl.notes, locTpl.notes)
                assertNotEquals("Blueprint title not localized for $lang (${enTpl.id})", enTpl.blueprint.title, locTpl.blueprint.title)
                assertNotEquals("Blueprint notes not localized for $lang (${enTpl.id})", enTpl.blueprint.notes, locTpl.blueprint.notes)
                assertEquals(enTpl.blueprint.deadlineDayOffset, locTpl.blueprint.deadlineDayOffset)
                assertEquals(enTpl.blueprint.headings.size, locTpl.blueprint.headings.size)
                assertEquals(enTpl.blueprint.totalTaskCount, locTpl.blueprint.totalTaskCount)

                for (hIdx in enTpl.blueprint.headings.indices) {
                    val enHeading = enTpl.blueprint.headings[hIdx]
                    val locHeading = locTpl.blueprint.headings[hIdx]
                    assertTrue(locHeading.title.isNotBlank())
                    assertNotEquals(
                        "Heading title not localized for $lang (${enTpl.id} / ${enHeading.title})",
                        enHeading.title,
                        locHeading.title
                    )
                    assertEquals(enHeading.tasks.size, locHeading.tasks.size)

                    for (tIdx in enHeading.tasks.indices) {
                        val enTask = enHeading.tasks[tIdx]
                        val locTask = locHeading.tasks[tIdx]
                        assertTrue(locTask.title.isNotBlank())
                        assertNotEquals(
                            "Task title not localized for $lang (${enTpl.id} / ${enTask.title})",
                            enTask.title,
                            locTask.title
                        )
                        assertEquals(enTask.dayOffset, locTask.dayOffset)
                        assertEquals(enTask.scheduledStartMinutes, locTask.scheduledStartMinutes)
                        assertEquals(enTask.durationMinutes, locTask.durationMinutes)
                        assertEquals(enTask.checklistItems.size, locTask.checklistItems.size)

                        for (cIdx in enTask.checklistItems.indices) {
                            val enChk = enTask.checklistItems[cIdx]
                            val locChk = locTask.checklistItems[cIdx]
                            assertTrue(locChk.isNotBlank())
                            assertNotEquals(
                                "Checklist item not localized for $lang (${enTpl.id} / $enChk)",
                                enChk,
                                locChk
                            )
                        }
                    }
                }
            }
        }

        // Verify language tag normalization (region tags and fallback to English)
        assertEquals(
            StarterProjectTemplates.getBuiltInTemplates("de").map { it.blueprint.title },
            StarterProjectTemplates.getBuiltInTemplates("de-AT").map { it.blueprint.title }
        )
        assertEquals(
            StarterProjectTemplates.getBuiltInTemplates("fr").map { it.blueprint.title },
            StarterProjectTemplates.getBuiltInTemplates("FR_CH").map { it.blueprint.title }
        )
        assertEquals(
            enTemplates.map { it.blueprint.title },
            StarterProjectTemplates.getBuiltInTemplates("ja").map { it.blueprint.title }
        )
    }

    @Test
    fun `StalledProjectDetector and TimeblockScheduleUtils detect stalled projects and timeline conflicts accurately`() {
        val now = 1_760_000_000_000L
        val fifteenDaysAgo = now - (15L * 24L * 60L * 60L * 1000L)
        val twoDaysAgo = now - (2L * 24L * 60L * 60L * 1000L)

        val healthyProject = Project(
            id = "p-healthy",
            areaId = null,
            title = "Healthy Active Project",
            notes = "",
            status = ProjectStatus.ACTIVE,
            scheduleState = ScheduleState.ANYTIME,
            whenDate = null,
            deadline = null,
            completedAt = null,
            lastReviewedAt = twoDaysAgo,
            sortOrder = 1.0,
            tagIds = emptyList(),
            totalTaskCount = 4,
            completedTaskCount = 1,
            createdAt = twoDaysAgo,
            updatedAt = twoDaysAgo
        )
        val noNextActionProject = healthyProject.copy(
            id = "p-empty",
            title = "Empty Project",
            totalTaskCount = 2,
            completedTaskCount = 2,
            lastReviewedAt = twoDaysAgo
        )
        val staleUnreviewedProject = healthyProject.copy(
            id = "p-stale",
            title = "Stale 15d Project",
            totalTaskCount = 3,
            completedTaskCount = 0,
            lastReviewedAt = fifteenDaysAgo,
            updatedAt = fifteenDaysAgo
        )
        val bothEmptyAndUnreviewedProject = healthyProject.copy(
            id = "p-both",
            title = "Empty and Unreviewed Project",
            totalTaskCount = 1,
            completedTaskCount = 1,
            lastReviewedAt = fifteenDaysAgo,
            updatedAt = fifteenDaysAgo
        )
        val somedayProject = noNextActionProject.copy(
            id = "p-someday",
            scheduleState = ScheduleState.SOMEDAY,
            lastReviewedAt = null
        )

        assertNull(StalledProjectDetector.evaluateProject(healthyProject, nowMillis = now))
        assertNull(StalledProjectDetector.evaluateProject(somedayProject, nowMillis = now))

        // Requires attention: hasNoNextAction == true, isUnreviewed == false
        val emptyEval = StalledProjectDetector.evaluateProject(noNextActionProject, nowMillis = now)
        assertNotNull(emptyEval)
        assertTrue(emptyEval!!.hasNoNextAction)
        assertFalse(emptyEval.isUnreviewed)
        assertEquals(StalledProjectReason.NO_NEXT_ACTION, emptyEval.reason)

        // Requires review only: hasNoNextAction == false, isUnreviewed == true
        val staleEval = StalledProjectDetector.evaluateProject(staleUnreviewedProject, nowMillis = now)
        assertNotNull(staleEval)
        assertFalse(staleEval!!.hasNoNextAction)
        assertTrue(staleEval.isUnreviewed)
        assertEquals(StalledProjectReason.UNREVIEWED, staleEval.reason)

        // Both missing next action and unreviewed: hasNoNextAction == true, isUnreviewed == true
        val bothEval = StalledProjectDetector.evaluateProject(bothEmptyAndUnreviewedProject, nowMillis = now)
        assertNotNull(bothEval)
        assertTrue(bothEval!!.hasNoNextAction)
        assertTrue(bothEval.isUnreviewed)
        assertEquals(StalledProjectReason.BOTH, bothEval.reason)

        val detected = StalledProjectDetector.detectStalledProjects(
            projects = listOf(healthyProject, noNextActionProject, staleUnreviewedProject, bothEmptyAndUnreviewedProject, somedayProject),
            nowMillis = now
        )
        assertEquals(listOf("p-empty", "p-stale", "p-both"), detected.map { it.project.id })

        // Timeblock formatting, Focus Timer duration resolution & Timeline overlap detection
        assertEquals("🕐 09:00–09:45 (45m)", TimeblockScheduleUtils.formatTimeblockBadge(9 * 60, 45))

        val baseTask = TaskItem(
            id = "t-1",
            title = "Write Design Doc",
            notes = "",
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.TODAY,
            whenDate = now,
            deadline = null,
            reminderTime = null,
            scheduledStartMinutes = 9 * 60, // 09:00–10:00
            durationMinutes = 60,
            completedAt = null,
            projectId = null,
            headingId = null,
            areaId = null,
            recurrenceRule = null,
            sortOrder = 1.0,
            tags = emptyList(),
            checklistItems = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        val nonOverlappingTask = baseTask.copy(
            id = "t-2",
            title = "Code Review",
            scheduledStartMinutes = 11 * 60, // 11:00–11:30
            durationMinutes = 30
        )
        val unscheduledTask = baseTask.copy(
            id = "t-3",
            title = "Reply to emails",
            scheduledStartMinutes = null,
            durationMinutes = null
        )

        assertEquals(60, TimeblockScheduleUtils.resolveFocusDurationMinutes(baseTask))
        assertEquals(25, TimeblockScheduleUtils.resolveFocusDurationMinutes(unscheduledTask))

        val zone = ZoneOffset.UTC
        val testDay = LocalDate.of(2026, 11, 15)
        val dayStartMillis = testDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val overlappingCalEvent = CalendarEventItem(
            id = "cal-standup",
            eventId = 101L,
            calendarId = "1",
            calendarDisplayName = "Work",
            title = "Team Standup",
            startMillis = dayStartMillis + (9L * 60L + 30L) * 60_000L, // 09:30–10:15 overlaps t-1!
            endMillis = dayStartMillis + (10L * 60L + 15L) * 60_000L,
            isAllDay = false,
            colorArgb = 0xFF1A73E8.toInt(),
            startDateEpochDay = testDay.toEpochDay(),
            endDateInclusiveEpochDay = testDay.toEpochDay()
        )

        val timeline = TimeblockScheduleUtils.buildTodayTimeline(
            todayTasks = listOf(nonOverlappingTask, baseTask, unscheduledTask),
            todayCalendarEvents = listOf(overlappingCalEvent),
            zoneId = zone
        )
        assertEquals(3, timeline.timedEntries.size)
        assertEquals(2, timeline.conflictCount)
        assertEquals(listOf("t-3"), timeline.unscheduledTasks.map { it.id })

        // Chronological order: 09:00 (t-1, conflict=true), 09:30 (cal-standup, conflict=true), 11:00 (t-2, conflict=false)
        val first = timeline.timedEntries[0] as TodayTimelineEntry.TaskBlock
        val second = timeline.timedEntries[1] as TodayTimelineEntry.CalendarBlock
        val third = timeline.timedEntries[2] as TodayTimelineEntry.TaskBlock
        assertEquals("t-1", first.task.id)
        assertTrue(first.hasConflict)
        assertEquals("cal-standup", second.event.id)
        assertTrue(second.hasConflict)
        assertEquals("t-2", third.task.id)
        assertFalse(third.hasConflict)

        // Next free 30m slot starting from 09:00 skips 09:00–10:15 and lands on 10:15 (615m)
        val suggested = TimeblockScheduleUtils.suggestNextFreeSlotMinutes(
            existingEntries = timeline.timedEntries,
            desiredDurationMinutes = 30,
            preferredStartMinutes = 9 * 60
        )
        assertEquals(10 * 60 + 15, suggested)
    }

    @Test
    fun `all 5 localized strings_xml files contain all 34 plain v2 keys with identical key sets`() {
        val expectedV2Keys = setOf(
            "v2_beta_badge",
            "v2_beta_channel_toggle",
            "v2_beta_channel_desc",
            "v2_weekly_review_title",
            "v2_weekly_review_subtitle",
            "v2_review_step_inbox",
            "v2_review_step_today",
            "v2_review_step_projects",
            "v2_review_step_someday",
            "v2_stalled_project_badge",
            "v2_requires_review_badge",
            "v2_stalled_no_next_action",
            "v2_stalled_unreviewed",
            "v2_add_next_action",
            "v2_mark_reviewed",
            "v2_complete_weekly_review",
            "v2_weekly_review_done_toast",
            "v2_templates_title",
            "v2_save_as_template",
            "v2_template_saved_toast",
            "v2_create_from_template",
            "v2_starter_templates",
            "v2_my_templates",
            "v2_import_markdown_title",
            "v2_import_markdown_hint",
            "v2_import_create_project",
            "v2_timeblock_title",
            "v2_timeblock_start",
            "v2_timeblock_duration",
            "v2_timeblock_clear",
            "v2_timeline_view",
            "v2_list_view",
            "v2_unscheduled_today",
            "v2_schedule_conflict"
        )
        assertEquals(34, expectedV2Keys.size)

        val resRoot = sequenceOf(
            File("src/main/res"),
            File("app/src/main/res")
        ).first { it.isDirectory }

        val folders = listOf("values", "values-de", "values-fr", "values-it", "values-es")
        var baselineKeySet: Set<String>? = null

        for (folder in folders) {
            val xmlFile = File(resRoot, "$folder/strings.xml")
            assertTrue("Missing $folder/strings.xml", xmlFile.isFile)
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("string")
            val keysInFile = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i)
                val name = elem.attributes?.getNamedItem("name")?.nodeValue ?: continue
                keysInFile[name] = elem.textContent.orEmpty()
            }
            if (baselineKeySet == null) {
                baselineKeySet = keysInFile.keys
            } else {
                assertEquals("Key mismatch in $folder/strings.xml", baselineKeySet, keysInFile.keys)
            }
            for (v2Key in expectedV2Keys) {
                val value = keysInFile[v2Key]
                assertNotNull("Missing $v2Key in $folder/strings.xml", value)
                assertTrue("Blank $v2Key in $folder/strings.xml", value!!.isNotBlank())
                assertFalse("Unexpected % format specifier in $folder/$v2Key: $value", value.contains("%"))
            }
            assertFalse(
                "Deprecated 'stalled' wording still in $folder/strings.xml",
                keysInFile["v2_stalled_project_badge"]!!.contains("stalled", ignoreCase = true) ||
                    keysInFile["v2_review_step_projects"]!!.contains("stalled", ignoreCase = true)
            )
        }
    }
}
