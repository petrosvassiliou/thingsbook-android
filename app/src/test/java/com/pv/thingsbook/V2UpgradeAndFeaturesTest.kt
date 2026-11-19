package com.pv.thingsbook

import com.pv.thingsbook.data.backup.BackupAreaDto
import com.pv.thingsbook.data.backup.BackupChecklistItemDto
import com.pv.thingsbook.data.backup.BackupHeadingDto
import com.pv.thingsbook.data.backup.BackupInspectionResult
import com.pv.thingsbook.data.backup.BackupPreferencesDto
import com.pv.thingsbook.data.backup.BackupProjectDto
import com.pv.thingsbook.data.backup.BackupProjectTemplateDto
import com.pv.thingsbook.data.backup.BackupTaskDto
import com.pv.thingsbook.data.backup.ThingsBackupCodec
import com.pv.thingsbook.data.backup.ThingsbookBackupEnvelope
import com.pv.thingsbook.data.backup.ThingsbookBackupPayload
import com.pv.thingsbook.data.local.DatabaseSeeder
import com.pv.thingsbook.data.local.ThingsDatabase
import com.pv.thingsbook.data.local.entity.ProjectEntity
import com.pv.thingsbook.data.local.entity.TaskEntity
import com.pv.thingsbook.data.remote.dto.CloudDeltaBatchPayload
import com.pv.thingsbook.data.remote.dto.ProjectFirestoreDto
import com.pv.thingsbook.data.remote.dto.ProjectTemplateFirestoreDto
import com.pv.thingsbook.data.remote.dto.TaskFirestoreDto
import com.pv.thingsbook.data.repository.SyncRepository
import com.pv.thingsbook.domain.model.CalendarEventItem
import com.pv.thingsbook.domain.model.ChecklistItem
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.ProjectOutlineImporter
import com.pv.thingsbook.domain.model.ProjectStatus
import com.pv.thingsbook.domain.model.ProjectTemplateBlueprint
import com.pv.thingsbook.domain.model.ProjectTemplateCodec
import com.pv.thingsbook.domain.model.QuickFindDateFilter
import com.pv.thingsbook.domain.model.QuickFindEntityScope
import com.pv.thingsbook.domain.model.QuickFindFilter
import com.pv.thingsbook.domain.model.RecurrenceMode
import com.pv.thingsbook.domain.model.RecurrenceRule
import com.pv.thingsbook.domain.model.RecurrenceUnit
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.StalledProjectDetector
import com.pv.thingsbook.domain.model.StalledProjectReason
import com.pv.thingsbook.domain.model.StarterProjectTemplates
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.model.TemplateHeadingBlueprint
import com.pv.thingsbook.domain.model.TemplateTaskBlueprint
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.pv.thingsbook.domain.model.TimeblockScheduleUtils
import com.pv.thingsbook.domain.model.TodayTimelineEntry
import com.pv.thingsbook.ui.components.MagicPlusDropMode
import com.pv.thingsbook.ui.components.ThingsTooltipHostState
import com.pv.thingsbook.ui.components.TooltipTimingPolicy
import com.pv.thingsbook.ui.components.computeMagicPlusDropSlot
import com.pv.thingsbook.ui.components.resolveMagicPlusDropMode
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

    @Test
    fun `v2_0_0 BuildConfig and all 5 localized strings_xml files verify final non_beta version, project deletion prompt and topbar_tooltip keys`() {
        assertEquals(82, BuildConfig.VERSION_CODE)
        assertEquals("3.0.0-alpha.8", BuildConfig.VERSION_NAME)
        assertTrue("3.0.0-alpha.8 pre-release contains -alpha", BuildConfig.VERSION_NAME.contains("-alpha"))

        val resRoot = sequenceOf(
            File("src/main/res"),
            File("app/src/main/res")
        ).first { it.isDirectory }

        val folders = listOf("values", "values-de", "values-fr", "values-it", "values-es")
        val requiredTopbarTooltipKeys = listOf(
            "topbar_tooltip_hide_sidebar",
            "topbar_tooltip_show_sidebar",
            "topbar_tooltip_quick_find",
            "topbar_tooltip_weekly_review",
            "topbar_tooltip_templates",
            "topbar_tooltip_quick_entry",
            "topbar_tooltip_voice_input",
            "topbar_tooltip_areas",
            "topbar_tooltip_tags",
            "topbar_tooltip_notifications",
            "topbar_tooltip_language",
            "topbar_tooltip_cloud_sync",
            "topbar_tooltip_settings"
        )
        var enMap: Map<String, String>? = null

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

            val titleTemplate = keysInFile["project_delete_prompt_title"]
            val bodyTemplate = keysInFile["project_delete_prompt_tasks_body"]
            val deleteWithTasksBtn = keysInFile["project_delete_with_tasks_btn"]
            val keepTasksBtn = keysInFile["project_delete_keep_tasks_btn"]

            assertNotNull("Missing project_delete_prompt_title in $folder/strings.xml", titleTemplate)
            assertNotNull("Missing project_delete_prompt_tasks_body in $folder/strings.xml", bodyTemplate)
            assertNotNull("Missing project_delete_with_tasks_btn in $folder/strings.xml", deleteWithTasksBtn)
            assertNotNull("Missing project_delete_keep_tasks_btn in $folder/strings.xml", keepTasksBtn)

            assertTrue(titleTemplate!!.contains("%1\$s"))
            assertTrue(String.format(titleTemplate, "Roadmap").contains("Roadmap"))

            assertTrue(bodyTemplate!!.contains("%1\$d"))
            assertTrue(String.format(bodyTemplate, 5).contains("5"))

            assertTrue(deleteWithTasksBtn!!.isNotBlank())
            assertFalse(deleteWithTasksBtn.contains("%"))

            assertTrue(keepTasksBtn!!.isNotBlank())
            assertFalse(keepTasksBtn.contains("%"))

            assertFalse(
                "v2_beta_channel_desc in $folder/strings.xml must not hardcode version 2.0",
                keysInFile["v2_beta_channel_desc"].orEmpty().contains("2.0")
            )

            for (tooltipKey in requiredTopbarTooltipKeys) {
                val tooltipVal = keysInFile[tooltipKey]
                assertNotNull("Missing $tooltipKey in $folder/strings.xml", tooltipVal)
                assertTrue("Blank $tooltipKey in $folder/strings.xml", tooltipVal!!.isNotBlank())
                assertFalse("Top-bar tooltip $tooltipKey must not include shortcut clutter", tooltipVal.contains("("))
                assertFalse("Top-bar tooltip $tooltipKey must not mention Room DB", tooltipVal.contains("Room", ignoreCase = true))
            }

            for (alpha3Key in listOf(
                "sidebar_review_short",
                "tags_search_or_create_placeholder",
                "tags_filter_placeholder",
                "tags_create_from_search",
                "tags_no_filter_matches"
            )) {
                val v = keysInFile[alpha3Key]
                assertNotNull("Missing $alpha3Key in $folder/strings.xml", v)
                assertTrue("Blank $alpha3Key in $folder/strings.xml", v!!.isNotBlank())
            }
            assertTrue(keysInFile["tags_create_from_search"]!!.contains("%1\$s"))

            if (folder == "values") {
                enMap = keysInFile
                assertEquals("Quick Entry", keysInFile["topbar_tooltip_quick_entry"])
                assertEquals("Cloud Sync", keysInFile["topbar_tooltip_cloud_sync"])
                assertEquals("Templates & Import", keysInFile["topbar_tooltip_templates"])
                assertEquals("Areas", keysInFile["topbar_tooltip_areas"])
                assertEquals("Categories", keysInFile["topbar_tooltip_tags"])
                assertEquals("Review", keysInFile["sidebar_review_short"])
            } else {
                val base = enMap!!
                assertNotEquals(base["project_delete_prompt_title"], titleTemplate)
                assertNotEquals(base["project_delete_prompt_tasks_body"], bodyTemplate)
                assertNotEquals(base["project_delete_with_tasks_btn"], deleteWithTasksBtn)
                assertNotEquals(base["project_delete_keep_tasks_btn"], keepTasksBtn)
                if (folder == "values-de") {
                    assertEquals("Kategorien", keysInFile["topbar_tooltip_tags"])
                    assertEquals("Rückblick", keysInFile["sidebar_review_short"])
                }
            }
        }
    }

    @Test
    fun `v2_0_0_beta_7 cross-device project review sync clears review prompt and merges lastReviewedAt monotonically`() {
        val dayMs = 24L * 60L * 60L * 1000L
        val now = 1_785_000_000_000L

        // 1. Freshly created project from macOS/ChromeOS or v1.8 with null lastReviewedAt (< 14 days old)
        //    must NOT falsely prompt "Requires review" on Android.
        val freshNullReviewProject = Project(
            id = "p-fresh-peer",
            areaId = null,
            title = "Created Yesterday on Another Device",
            notes = "",
            status = ProjectStatus.ACTIVE,
            scheduleState = ScheduleState.ANYTIME,
            whenDate = null,
            deadline = null,
            completedAt = null,
            lastReviewedAt = null,
            sortOrder = 100.0,
            tagIds = emptyList(),
            totalTaskCount = 2,
            completedTaskCount = 0,
            createdAt = now - 1L * dayMs,
            updatedAt = now - 1L * dayMs
        )
        assertNull(StalledProjectDetector.evaluateProject(freshNullReviewProject, nowMillis = now))

        // 2. Legacy 20-day-old project with null lastReviewedAt requires review initially on Device A & Device B
        val legacyStaleProject = freshNullReviewProject.copy(
            id = "p-sync-review",
            title = "Cross-Device Roadmap",
            createdAt = now - 20L * dayMs,
            updatedAt = now - 16L * dayMs,
            lastReviewedAt = null
        )
        val beforeReview = StalledProjectDetector.evaluateProject(legacyStaleProject, nowMillis = now)
        assertNotNull(beforeReview)
        assertTrue(beforeReview!!.isUnreviewed)

        // 3. Device A marks the project reviewed and pushes via Firestore + ntfy delta payload
        val reviewedEntityOnDeviceA = ProjectEntity(
            id = legacyStaleProject.id,
            areaId = null,
            title = legacyStaleProject.title,
            notes = "",
            status = ProjectStatus.ACTIVE,
            scheduleState = ScheduleState.ANYTIME,
            whenDate = null,
            deadline = null,
            completedAt = null,
            lastReviewedAt = now,
            sortOrder = 100.0,
            tagIdsJson = "[]",
            createdAt = legacyStaleProject.createdAt,
            updatedAt = now,
            isDeleted = false,
            isDirty = true
        )
        val firestoreMap = ProjectFirestoreDto.fromEntity(reviewedEntityOnDeviceA).toFirestoreMap()
        val parsedOnDeviceB = ProjectFirestoreDto.fromFirestoreMap(firestoreMap)
        assertNotNull(parsedOnDeviceB)
        assertEquals(now, parsedOnDeviceB!!.lastReviewedAt)

        // 4. Monotonic merge preserves the newer review timestamp even against older/null edits
        assertEquals(
            now,
            SyncRepository.mergeMonotonicReviewTimestamp(localLastReviewedAt = null, remoteLastReviewedAt = now)
        )
        assertEquals(
            now,
            SyncRepository.mergeMonotonicReviewTimestamp(
                localLastReviewedAt = now,
                remoteLastReviewedAt = now - 15L * dayMs
            )
        )
        assertEquals(
            now,
            SyncRepository.mergeMonotonicReviewTimestamp(localLastReviewedAt = now, remoteLastReviewedAt = null)
        )

        // 5. Device B evaluates the synced project -> no longer prompts to review!
        val syncedEntityOnDeviceB = parsedOnDeviceB.toEntity()
        val syncedDomainOnDeviceB = legacyStaleProject.copy(
            lastReviewedAt = syncedEntityOnDeviceB.lastReviewedAt,
            updatedAt = syncedEntityOnDeviceB.updatedAt
        )
        assertNull(StalledProjectDetector.evaluateProject(syncedDomainOnDeviceB, nowMillis = now + 5_000L))
    }

    @Test
    fun `v2_0_0_beta_9 QuickFindFilter predicates FTS5 SQL builders and 5-language search_filter keys`() {
        val zone = ZoneOffset.UTC
        val refDay = LocalDate.of(2026, 11, 15)
        val todayStart = refDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val refNoon = todayStart + 12L * 3_600_000L
        val dayMs = 86_400_000L

        val urgentTag = Tag(
            id = "tag-urgent",
            title = "Urgent",
            parentTagId = null,
            shortcut = "u",
            sortOrder = 1.0,
            createdAt = todayStart,
            updatedAt = todayStart
        )
        val homeTag = urgentTag.copy(id = "tag-home", title = "Home", shortcut = "h")

        val todayTask = TaskItem(
            id = "t-today",
            title = "Sprint keynote",
            notes = "",
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.TODAY,
            whenDate = todayStart,
            deadline = todayStart + 2L * dayMs,
            reminderTime = null,
            completedAt = null,
            projectId = null,
            headingId = null,
            areaId = null,
            recurrenceRule = null,
            sortOrder = 1.0,
            tags = listOf(urgentTag),
            checklistItems = listOf(
                ChecklistItem(
                    id = "c-1",
                    taskId = "t-today",
                    title = "Check slides",
                    isCompleted = false,
                    sortOrder = 1.0,
                    createdAt = todayStart,
                    updatedAt = todayStart
                )
            ),
            createdAt = todayStart,
            updatedAt = todayStart
        )
        val overdueTask = todayTask.copy(
            id = "t-overdue",
            scheduleState = ScheduleState.UPCOMING,
            whenDate = todayStart - 2L * dayMs,
            deadline = todayStart - dayMs,
            tags = listOf(homeTag),
            checklistItems = emptyList()
        )
        val noDateTask = todayTask.copy(
            id = "t-nodate",
            scheduleState = ScheduleState.SOMEDAY,
            whenDate = null,
            deadline = null,
            tags = emptyList(),
            checklistItems = emptyList()
        )
        val completedTask = todayTask.copy(
            id = "t-done",
            status = TaskStatus.COMPLETED,
            scheduleState = ScheduleState.ANYTIME,
            whenDate = null,
            deadline = null,
            checklistItems = emptyList()
        )

        val activeProject = Project(
            id = "p-active",
            areaId = null,
            title = "Q4 Sprint",
            notes = "",
            status = ProjectStatus.ACTIVE,
            scheduleState = ScheduleState.ANYTIME,
            whenDate = null,
            deadline = todayStart + 4L * dayMs,
            completedAt = null,
            lastReviewedAt = todayStart,
            sortOrder = 1.0,
            tagIds = listOf(urgentTag.id),
            createdAt = todayStart,
            updatedAt = todayStart
        )
        val completedProject = activeProject.copy(
            id = "p-done",
            status = ProjectStatus.COMPLETED,
            deadline = null,
            tagIds = emptyList()
        )

        val defaultFilter = QuickFindFilter()
        assertFalse(defaultFilter.hasActiveFilters)
        assertTrue(defaultFilter.shouldShowSmartViewsAndAreas("Today"))
        assertFalse(QuickFindFilter(hideCompleted = true).shouldShowSmartViewsAndAreas(""))
        assertTrue(QuickFindFilter(hideCompleted = true).shouldShowSmartViewsAndAreas("Tod"))
        assertFalse(QuickFindFilter(scope = QuickFindEntityScope.TASKS).shouldShowSmartViewsAndAreas("Tod"))

        // Hide completed
        val hideDone = QuickFindFilter(hideCompleted = true)
        assertTrue(hideDone.matchesTask(todayTask, refNoon, zone))
        assertFalse(hideDone.matchesTask(completedTask, refNoon, zone))
        assertTrue(hideDone.matchesProject(activeProject, refNoon, zone))
        assertFalse(hideDone.matchesProject(completedProject, refNoon, zone))

        // Date filters
        assertTrue(QuickFindFilter(dateFilter = QuickFindDateFilter.TODAY).matchesTask(todayTask, refNoon, zone))
        assertFalse(QuickFindFilter(dateFilter = QuickFindDateFilter.TODAY).matchesTask(overdueTask, refNoon, zone))
        assertTrue(QuickFindFilter(dateFilter = QuickFindDateFilter.OVERDUE).matchesTask(overdueTask, refNoon, zone))
        assertTrue(QuickFindFilter(dateFilter = QuickFindDateFilter.NEXT_7_DAYS).matchesProject(activeProject, refNoon, zone))
        assertTrue(QuickFindFilter(dateFilter = QuickFindDateFilter.THIS_MONTH).matchesTask(overdueTask, refNoon, zone))
        assertTrue(QuickFindFilter(dateFilter = QuickFindDateFilter.NO_DATE).matchesTask(noDateTask, refNoon, zone))
        assertFalse(QuickFindFilter(dateFilter = QuickFindDateFilter.NO_DATE).matchesTask(todayTask, refNoon, zone))

        // Checklist, Deadline, Tag, Scope
        assertTrue(QuickFindFilter(hasChecklistOnly = true).matchesTask(todayTask, refNoon, zone))
        assertFalse(QuickFindFilter(hasChecklistOnly = true).matchesTask(overdueTask, refNoon, zone))
        assertFalse(QuickFindFilter(hasChecklistOnly = true).matchesProject(activeProject, refNoon, zone))
        assertTrue(
            QuickFindFilter(
                hasDeadlineOnly = true,
                tagId = urgentTag.id,
                scope = QuickFindEntityScope.PROJECTS
            ).matchesProject(activeProject, refNoon, zone)
        )
        assertFalse(QuickFindFilter(scope = QuickFindEntityScope.PROJECTS).matchesTask(todayTask, refNoon, zone))

        // SQLite query builders support empty-query filter-only and hideCompleted SQL pushdown
        val openTasksFilterSql = ThingsDatabase.buildTasksFts5SqlQuery("", hideCompleted = true).sql
        assertTrue(openTasksFilterSql.contains("status = 'OPEN'"))
        assertTrue(openTasksFilterSql.contains("LIMIT 250"))

        val activeProjFtsSql = ThingsDatabase.buildProjectsFts5SqlQuery("Sprint", hideCompleted = true).sql
        assertTrue(activeProjFtsSql.contains("status = 'ACTIVE'"))
        assertTrue(activeProjFtsSql.contains("LIMIT 100"))

        // Verify all 14 search_filter_* keys across all 5 localized strings.xml files
        val expectedFilterKeys = setOf(
            "search_filter_hide_completed",
            "search_filter_date_all",
            "search_filter_date_today",
            "search_filter_date_overdue",
            "search_filter_date_next_7_days",
            "search_filter_date_this_month",
            "search_filter_date_none",
            "search_filter_has_deadline",
            "search_filter_has_checklist",
            "search_filter_all_tags",
            "search_filter_scope_all",
            "search_filter_scope_tasks",
            "search_filter_scope_projects",
            "search_filter_reset"
        )
        assertEquals(14, expectedFilterKeys.size)

        val resRoot = sequenceOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        for (folder in listOf("values", "values-de", "values-fr", "values-it", "values-es")) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resRoot, "$folder/strings.xml"))
            val nodes = doc.getElementsByTagName("string")
            val keysInFile = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i)
                val name = elem.attributes?.getNamedItem("name")?.nodeValue ?: continue
                keysInFile[name] = elem.textContent.orEmpty()
            }
            for (k in expectedFilterKeys) {
                val v = keysInFile[k]
                assertNotNull("Missing $k in $folder/strings.xml", v)
                assertTrue("Blank $k in $folder/strings.xml", v!!.isNotBlank())
            }
        }
    }

    @Test
    fun `v2_0_0_beta_11 instant tooltip timing policy and host state warm handoff and nested anchors`() {
        var fakeNowMs = 1_000L
        val host = ThingsTooltipHostState(clockMs = { fakeNowMs })
        val outerCard = Any()
        val innerDeleteBtn = Any()
        val adjacentToolbarBtn = Any()

        // 1. Cold hover delay is 160ms
        assertEquals(TooltipTimingPolicy.DEFAULT_INITIAL_DELAY_MS, host.resolveHoverDelayMs())
        assertEquals(160L, host.resolveHoverDelayMs())

        // 2. Showing outer attachment card activates tooltip & sets warm delay to 0ms
        host.show(outerCard, "floorplan.pdf", IntRect(0, 0, 200, 56))
        assertTrue(host.isShowing)
        assertEquals("floorplan.pdf", host.activeText)
        assertEquals(0L, host.resolveHoverDelayMs())

        // 3. Hovering nested inner delete button (smaller area) overrides outer card in 0ms
        host.show(innerDeleteBtn, "Delete", IntRect(170, 8, 194, 32))
        assertEquals("Delete", host.activeText)

        // 4. Live state / language change while hovered updates active text in 0ms
        host.updateIfActive(innerDeleteBtn, "Löschen", IntRect(170, 8, 194, 32))
        assertEquals("Löschen", host.activeText)

        // 5. Leaving inner delete button restores outer attachment card tooltip immediately
        host.hide(innerDeleteBtn)
        assertTrue(host.isShowing)
        assertEquals("floorplan.pdf", host.activeText)

        // 6. Leaving outer card starts 450ms warm handoff window -> next control enters in 0ms
        fakeNowMs = 2_000L
        host.hide(outerCard)
        assertFalse(host.isShowing)

        fakeNowMs = 2_320L // 320ms <= 450ms warm window
        assertEquals(0L, host.resolveHoverDelayMs())

        // 7. After 450ms cooldown expires, cold 160ms delay is restored
        fakeNowMs = 2_600L // 600ms > 450ms warm window
        assertEquals(160L, host.resolveHoverDelayMs())

        // 8. Explicit click / scroll dismissAll(allowWarmHandoff = false) clears warm handoff immediately
        host.show(adjacentToolbarBtn, "Quick Find (Ctrl+F)", IntRect(10, 10, 130, 44))
        assertTrue(host.isShowing)
        fakeNowMs = 3_000L
        host.dismissAll(allowWarmHandoff = false)
        assertFalse(host.isShowing)
        fakeNowMs = 3_050L
        assertEquals(160L, host.resolveHoverDelayMs())
    }

    @Test
    fun `v2_0_0_beta_13 fast sync UID wake topic matches ChromeOS and macOS vector and wakeOnly payload roundtrips`() {
        assertEquals(35L, SyncRepository.FAST_PUSH_COALESCE_MS)
        assertEquals(
            "tb_uid_9d8ecb07e35952112a013d4819b4",
            SyncRepository.deriveUidWakeChannelTopic(" user-abc-123 ")
        )

        val jsonCodec = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val wakeEnvelope = CloudDeltaBatchPayload(
            userId = "user-abc-123",
            deviceId = "dev-android",
            batchTimestamp = 1_763_000_000_789L,
            wakeOnly = true
        )
        val encoded = jsonCodec.encodeToString(wakeEnvelope)
        assertTrue(encoded.toByteArray(StandardCharsets.UTF_8).size < SyncRepository.MAX_NTFY_CHUNK_BYTES)
        val decoded = jsonCodec.decodeFromString<CloudDeltaBatchPayload>(encoded)
        assertTrue(decoded.wakeOnly)
        assertEquals("user-abc-123", decoded.userId)
        assertEquals("dev-android", decoded.deviceId)
        assertEquals(1_763_000_000_789L, decoded.batchTimestamp)

        // Legacy peers omitting wakeOnly deserialize cleanly with wakeOnly = false
        val legacyDecoded = jsonCodec.decodeFromString<CloudDeltaBatchPayload>(
            """{"userId":"user-abc-123","deviceId":"dev-cros","batchTimestamp":1763000000123}"""
        )
        assertFalse(legacyDecoded.wakeOnly)
    }

    @Test
    fun `v2_0_0_beta_13 recurring task deterministic UUID vectors and canonical series deduplication match ChromeOS and macOS`() {
        // 1. Exact cross-platform SHA-256 deterministic UUID vectors
        val derivedTaskId = SyncRepository.deriveRecurringNextTaskId(" task-daily-001 ", "2026-11-17")
        assertEquals("e2d2925b-820d-5122-a510-f8164e907815", derivedTaskId)

        val derivedChkId = SyncRepository.deriveRecurringChecklistItemId(derivedTaskId, "chk-step-001")
        assertEquals("55d4ccdc-7b54-53cb-a216-e0f79e8fb359", derivedChkId)

        val jsonCodec = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val dailyRuleJson = jsonCodec.encodeToString(
            RecurrenceRule(
                mode = RecurrenceMode.FIXED_SCHEDULE,
                interval = 1,
                unit = RecurrenceUnit.DAYS
            )
        )
        val weeklyRuleJson = jsonCodec.encodeToString(
            RecurrenceRule(
                mode = RecurrenceMode.FIXED_SCHEDULE,
                interval = 1,
                unit = RecurrenceUnit.WEEKS
            )
        )

        val staleToday = TaskEntity(
            id = "dup-stale-today",
            title = "Morning Journal",
            notes = "5 minutes",
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.TODAY,
            whenDate = 1_763_251_200_000L, // 2026-11-16
            scheduledStartMinutes = 540,
            durationMinutes = 15,
            projectId = "proj-1",
            headingId = "head-1",
            areaId = "area-1",
            recurrenceRuleJson = dailyRuleJson,
            sortOrder = 1000.0,
            tagIdsJson = """["tag-b","tag-a"]""",
            createdAt = 1_760_000_050_000L,
            updatedAt = 1_760_000_900_000L,
            isDeleted = false,
            isDirty = false
        )
        val latestTomorrow = staleToday.copy(
            id = "dup-latest-tomorrow",
            scheduleState = ScheduleState.UPCOMING,
            whenDate = 1_763_337_600_000L, // 2026-11-17
            areaId = null, // ChromeOS sets areaId=null when projectId != null
            tagIdsJson = """["tag-a","tag-b"]""",
            createdAt = 1_760_000_200_000L,
            updatedAt = 1_760_000_200_000L
        )
        val sameDateLaterCreated = latestTomorrow.copy(
            id = "dup-tomorrow-second",
            createdAt = 1_760_000_250_000L
        )
        val nonRecurringA = staleToday.copy(
            id = "plain-1",
            title = "Buy Coffee",
            recurrenceRuleJson = null
        )
        val nonRecurringB = nonRecurringA.copy(id = "plain-2")
        val differentRuleTask = latestTomorrow.copy(
            id = "weekly-journal",
            recurrenceRuleJson = weeklyRuleJson
        )

        assertEquals(
            SyncRepository.buildRecurringSeriesKey(staleToday),
            SyncRepository.buildRecurringSeriesKey(latestTomorrow)
        )
        assertNull(SyncRepository.buildRecurringSeriesKey(nonRecurringA))

        val losers = SyncRepository.selectRecurringDuplicateLoserIds(
            listOf(
                staleToday,
                latestTomorrow,
                sameDateLaterCreated,
                nonRecurringA,
                nonRecurringB,
                differentRuleTask
            )
        )
        assertEquals(setOf("dup-stale-today", "dup-tomorrow-second"), losers.toSet())
    }

    @Test
    fun `v2_0_0_beta_14 Android tooltip unpressed hover detection safe inset placement and touch preview linger`() {
        // 1. Unpressed Enter/Move events trigger hover regardless of PointerType (ARCVM / Android trackpads),
        //    while pressed, just-released, Press, or Exit events never falsely re-trigger hover.
        assertTrue(
            TooltipTimingPolicy.isUnpressedHoverEvent(
                eventType = PointerEventType.Enter,
                hasChanges = true,
                anyPressed = false,
                anyPreviousPressed = false
            )
        )
        assertTrue(
            TooltipTimingPolicy.isUnpressedHoverEvent(
                eventType = PointerEventType.Move,
                hasChanges = true,
                anyPressed = false,
                anyPreviousPressed = false
            )
        )
        assertFalse(
            TooltipTimingPolicy.isUnpressedHoverEvent(
                eventType = PointerEventType.Move,
                hasChanges = true,
                anyPressed = true,
                anyPreviousPressed = false
            )
        )
        assertFalse(
            TooltipTimingPolicy.isUnpressedHoverEvent(
                eventType = PointerEventType.Move,
                hasChanges = true,
                anyPressed = false,
                anyPreviousPressed = true
            )
        )
        assertFalse(
            TooltipTimingPolicy.isUnpressedHoverEvent(
                eventType = PointerEventType.Exit,
                hasChanges = true,
                anyPressed = false,
                anyPreviousPressed = false
            )
        )
        assertFalse(
            TooltipTimingPolicy.isUnpressedHoverEvent(
                eventType = PointerEventType.Press,
                hasChanges = true,
                anyPressed = false,
                anyPreviousPressed = false
            )
        )

        // 2. Edge-to-edge Android status bar inset clearance:
        //    A TopThingsbookBar icon right below a 108px status bar flips cleanly BELOW the button
        //    instead of rendering behind the status bar / camera cutout.
        val hostBounds = IntRect(0, 0, 1080, 2400)
        val hostSize = IntSize(1080, 2400)
        val tooltipSize = IntSize(220, 76)
        val topBarIconBounds = IntRect(960, 120, 1056, 216) // 96x96 icon right below 108px status bar
        val topPlacement = TooltipTimingPolicy.computeTooltipPlacement(
            anchorBounds = topBarIconBounds,
            hostBoundsInRoot = hostBounds,
            hostSize = hostSize,
            tooltipSize = tooltipSize,
            rootSafeTopPx = 108,
            rootSafeBottomPx = 64,
            verticalGapPx = 18,
            screenMarginPx = 24
        )
        assertEquals(IntOffset(836, 234), topPlacement) // Clamped right edge (1080-220-24=836) & flipped below (216+18=234)

        //    A bottom/body button with ample space above renders cleanly ABOVE the button.
        val fabAnchorBounds = IntRect(490, 2050, 590, 2150)
        val bottomPlacement = TooltipTimingPolicy.computeTooltipPlacement(
            anchorBounds = fabAnchorBounds,
            hostBoundsInRoot = hostBounds,
            hostSize = hostSize,
            tooltipSize = tooltipSize,
            rootSafeTopPx = 108,
            rootSafeBottomPx = 64,
            verticalGapPx = 18,
            screenMarginPx = 24
        )
        assertEquals(IntOffset(430, 1956), bottomPlacement) // Centered X (490 + (100-220)/2 = 430) & above (2050-76-18 = 1956)

        // 3. Touchscreen long-press preview lingers after finger release (1.8s) until dismissed or next tap.
        var fakeNowMs = 5_000L
        val host = ThingsTooltipHostState(clockMs = { fakeNowMs })
        val syncBtn = Any()
        host.show(syncBtn, "Sync now", topBarIconBounds, isTouchPreview = true)
        assertTrue(host.isShowing)
        assertTrue(host.activeIsTouchPreview)
        assertFalse(host.hasReleasedTouchPreview())

        val prevTick = host.releasedTouchTick
        host.releaseTouchPreview(syncBtn)
        assertEquals(prevTick + 1, host.releasedTouchTick)
        assertTrue(host.isShowing)
        assertTrue(host.hasReleasedTouchPreview())

        // Hover-exit cleanup with preserveIfReleasedTouch = true must NOT wipe out the post-release touch tooltip
        host.hide(syncBtn, preserveIfReleasedTouch = true)
        assertTrue(host.isShowing)
        assertEquals("Sync now", host.activeText)

        // Subsequent tap / scroll or 1800ms timeout clears released touch previews cleanly
        assertEquals(1800L, TooltipTimingPolicy.DEFAULT_TOUCH_RELEASE_HIDE_MS)
        host.dismissReleasedTouchPreviews()
        assertFalse(host.isShowing)
        assertFalse(host.hasReleasedTouchPreview())

        // 4. v2.0.0-beta.15+ discreet Light & Dark tooltip surfaces (warm-pearl in Light, elevated slate in Dark — never black)
        val lightStyle = TooltipTimingPolicy.resolveBubbleStyle(isDark = false)
        assertTrue(lightStyle.backgroundColor.red > 0.94f && lightStyle.backgroundColor.green > 0.94f && lightStyle.backgroundColor.blue > 0.96f)
        assertTrue(lightStyle.textColor.red < 0.35f && lightStyle.textColor.blue < 0.40f)
        assertTrue(lightStyle.shadowSpotAlpha <= 0.12f)

        val darkStyle = TooltipTimingPolicy.resolveBubbleStyle(isDark = true)
        assertTrue(darkStyle.backgroundColor.red > 0.16f && darkStyle.backgroundColor.blue > 0.21f) // elevated slate (#2E323D), not black
        assertTrue(darkStyle.textColor.red > 0.80f && darkStyle.textColor.red < 0.92f) // muted silver (#D4D9E2), not glaring white

        // 5. v2.0.0-beta.16: Tooltips strictly restricted to top-bar icon scope — zero tooltip attachment anywhere else
        assertTrue(
            TooltipTimingPolicy.shouldAttachTooltip(
                isTopBarIconScope = true,
                enabled = true,
                hasHost = true,
                text = "Settings"
            )
        )
        assertFalse(
            TooltipTimingPolicy.shouldAttachTooltip(
                isTopBarIconScope = false,
                enabled = true,
                hasHost = true,
                text = "Non-topbar control"
            )
        )
        assertFalse(
            TooltipTimingPolicy.shouldAttachTooltip(
                isTopBarIconScope = true,
                enabled = false,
                hasHost = true,
                text = "Wide-screen labeled Quick Find"
            )
        )
        assertFalse(
            TooltipTimingPolicy.shouldAttachTooltip(
                isTopBarIconScope = true,
                enabled = true,
                hasHost = true,
                text = "   "
            )
        )

        // 6. v2.0.0-beta.16: MagicPlusFab drag-to-insert To-Do slot & Project Heading drop mode
        assertEquals(4, computeMagicPlusDropSlot(itemCount = 4, dragOffsetY = 0f, rowHeightPx = 116f))
        assertEquals(2, computeMagicPlusDropSlot(itemCount = 4, dragOffsetY = -232f, rowHeightPx = 116f))
        assertEquals(0, computeMagicPlusDropSlot(itemCount = 4, dragOffsetY = -900f, rowHeightPx = 116f))
        assertEquals(4, computeMagicPlusDropSlot(itemCount = 4, dragOffsetY = 120f, rowHeightPx = 116f))

        assertEquals(
            MagicPlusDropMode.INSERT_TODO,
            resolveMagicPlusDropMode(allowHeadingDrop = true, dragOffsetX = -80f, leftEdgeThresholdPx = -360f)
        )
        assertEquals(
            MagicPlusDropMode.INSERT_HEADING,
            resolveMagicPlusDropMode(allowHeadingDrop = true, dragOffsetX = -380f, leftEdgeThresholdPx = -360f)
        )
        assertEquals(
            MagicPlusDropMode.INSERT_TODO,
            resolveMagicPlusDropMode(allowHeadingDrop = false, dragOffsetX = -500f, leftEdgeThresholdPx = -360f)
        )
        // v2.0.0-beta.17: Wide tablet / Chromebook window-coordinate gutter resolution
        assertEquals(
            MagicPlusDropMode.INSERT_TODO,
            resolveMagicPlusDropMode(
                allowHeadingDrop = true,
                dragOffsetX = -620f,
                leftEdgeThresholdPx = -360f,
                pointerWindowX = 680f,
                listWindowLeftPx = 320f,
                headingGutterWidthPx = 96f
            )
        )
        assertEquals(
            MagicPlusDropMode.INSERT_HEADING,
            resolveMagicPlusDropMode(
                allowHeadingDrop = true,
                dragOffsetX = -860f,
                leftEdgeThresholdPx = -360f,
                pointerWindowX = 390f,
                listWindowLeftPx = 320f,
                headingGutterWidthPx = 96f
            )
        )
    }

    @Test
    fun `v2_0_0_beta_21 SyncRepository preserves Firestore document path ID and prevents partial listeners from advancing pull cursor`() {
        assertNull(SyncRepository.normalizeFirestoreMapWithId("proj-doc-null", null))

        // Missing or blank id inside document fields falls back to Firestore document path ID
        val missingIdDoc = SyncRepository.normalizeFirestoreMapWithId(
            "proj-from-doc-path",
            mapOf("title" to "Recovered Googlebook Project", "status" to "ACTIVE", "updatedAt" to 1_795_000_000_000L)
        )
        val parsedMissing = ProjectFirestoreDto.fromFirestoreMap(missingIdDoc)
        assertNotNull(parsedMissing)
        assertEquals("proj-from-doc-path", parsedMissing!!.id)
        assertEquals("Recovered Googlebook Project", parsedMissing.title)

        val blankIdDoc = SyncRepository.normalizeFirestoreMapWithId(
            "proj-blank-override",
            mapOf("id" to "   ", "title" to "Blank Field Project", "updatedAt" to 1_795_000_000_000L)
        )
        val parsedBlank = ProjectFirestoreDto.fromFirestoreMap(blankIdDoc)
        assertNotNull(parsedBlank)
        assertEquals("proj-blank-override", parsedBlank!!.id)

        // Explicit non-blank id is preserved untouched
        val explicitIdDoc = SyncRepository.normalizeFirestoreMapWithId(
            "doc-path-id",
            mapOf("id" to "explicit-proj-id", "title" to "Explicit ID Project", "updatedAt" to 1_795_000_000_000L)
        )
        assertEquals("explicit-proj-id", ProjectFirestoreDto.fromFirestoreMap(explicitIdDoc)!!.id)

        // Source invariant: attachRealtimeDeltaListeners must never call saveLastSyncTimestamp
        val syncRepoFile = sequenceOf(
            File("src/main/java/com/pv/thingsbook/data/repository/SyncRepository.kt"),
            File("app/src/main/java/com/pv/thingsbook/data/repository/SyncRepository.kt")
        ).first { it.isFile }
        val syncRepoSrc = syncRepoFile.readText()
        val attachStart = syncRepoSrc.indexOf("suspend fun attachRealtimeDeltaListeners(")
        val attachEnd = syncRepoSrc.indexOf("private fun scheduleListenerReconnect(", attachStart)
        assertTrue(attachStart > 0 && attachEnd > attachStart)
        val attachBody = syncRepoSrc.substring(attachStart, attachEnd)
        assertFalse(
            "Single-collection listeners must never advance saveLastSyncTimestamp",
            attachBody.contains("saveLastSyncTimestamp(")
        )
    }

    @Test
    fun `v2_0_0_beta_22 Cialis area-split recurring dedup enrichment legacy ntfy chunking and v1_8 timeblock preservation`() {
        val jsonCodec = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val dailyRuleJson = jsonCodec.encodeToString(
            RecurrenceRule(
                mode = RecurrenceMode.FIXED_SCHEDULE,
                interval = 1,
                unit = RecurrenceUnit.DAYS
            )
        )

        // 1. Exact reproduction of screenshot 22828: two open daily "Cialis" tasks (one areaId=null, one areaId="area-privat")
        val cialisNoAreaNewer = TaskEntity(
            id = "cialis-no-area",
            title = "Cialis",
            notes = "",
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.TODAY,
            whenDate = 1_763_424_000_000L,
            projectId = null,
            headingId = null,
            areaId = null,
            recurrenceRuleJson = dailyRuleJson,
            sortOrder = 100.0,
            tagIdsJson = """["tag-gesundheit"]""",
            createdAt = 1_763_300_000_000L,
            updatedAt = 1_763_300_500_000L,
            isDeleted = false,
            isDirty = false
        )
        val cialisPrivatOlder = cialisNoAreaNewer.copy(
            id = "cialis-privat",
            notes = "5mg",
            whenDate = 1_763_337_600_000L,
            areaId = "area-privat",
            scheduledStartMinutes = 480,
            durationMinutes = 10,
            tagIdsJson = """["tag-gesundheit","tag-daily"]""",
            createdAt = 1_763_200_000_000L,
            updatedAt = 1_763_200_000_000L
        )

        val plan = SyncRepository.planRecurringDeduplication(listOf(cialisNoAreaNewer, cialisPrivatOlder))
        assertEquals(listOf("cialis-privat"), plan.loserIds)
        val enrichment = plan.winnerEnrichments.firstOrNull { it.winnerId == "cialis-no-area" }
        assertNotNull("Winner missing areaId must inherit Privat area from duplicate", enrichment)
        assertEquals("area-privat", enrichment!!.areaId)
        assertEquals("5mg", enrichment.notes)
        assertEquals(480, enrichment.scheduledStartMinutes)
        assertEquals(10, enrichment.durationMinutes)
        assertEquals(setOf("tag-gesundheit", "tag-daily"), enrichment.tagIds.orEmpty().toSet())

        // 2. Legacy tb_sync_* ntfy chunks never emit wakeOnly=true and split oversized payloads cleanly under MAX_NTFY_CHUNK_BYTES
        val manyTasks = (1..24).map { idx ->
            TaskFirestoreDto(
                id = "task-chunk-$idx",
                title = "Cross-Platform Sync Item #$idx " + "x".repeat(120),
                notes = "Detailed sync body #$idx",
                status = "OPEN",
                scheduleState = "TODAY",
                updatedAt = 1_795_000_000_000L + idx
            )
        }
        val oversizedBatch = CloudDeltaBatchPayload(
            userId = "user-abc-123",
            deviceId = "dev-android",
            batchTimestamp = 1_795_000_999_000L,
            wakeOnly = true,
            tasks = manyTasks
        )
        val legacyChunks = SyncRepository.buildLegacyCompatibleNtfyChunks(oversizedBatch, jsonCodec, maxChunks = 6)
        assertTrue(legacyChunks.isNotEmpty())
        for (chunk in legacyChunks) {
            assertTrue(chunk.toByteArray(StandardCharsets.UTF_8).size < SyncRepository.MAX_NTFY_CHUNK_BYTES)
            val parsedChunk = jsonCodec.decodeFromString<CloudDeltaBatchPayload>(chunk)
            assertFalse("Legacy tb_sync_* chunk must never carry wakeOnly=true", parsedChunk.wakeOnly)
            assertTrue(parsedChunk.tasks.isNotEmpty())
        }

        // 3. v1.8 omitted timeblock fields vs v2.x explicit null timeblock fields + purgedFromTrash tombstone parity
        val mixedWireJson = """
            {
              "userId": "u1",
              "deviceId": "d1",
              "batchTimestamp": 1795001000000,
              "tasks": [
                {"id": "v18-omitted", "title": "Edited on v1.8", "status": "OPEN", "scheduleState": "TODAY", "updatedAt": 1795001000000},
                {"id": "v20-cleared", "title": "Cleared on v2.0", "status": "OPEN", "scheduleState": "TODAY", "scheduledStartMinutes": null, "durationMinutes": null, "updatedAt": 1795001000000},
                {"id": "v20-purged", "title": "Should Be Scrubbed", "notes": "Secret", "status": "OPEN", "scheduleState": "INBOX", "isDeleted": true, "purgedFromTrash": true, "updatedAt": 1795001000000}
              ]
            }
        """.trimIndent()
        val decodedHints = CloudDeltaBatchPayload.decodeWithHints(mixedWireJson, jsonCodec)
        val v18Dto = decodedHints.tasks.first { it.id == "v18-omitted" }
        val v20ClearedDto = decodedHints.tasks.first { it.id == "v20-cleared" }
        val v20PurgedEntity = decodedHints.tasks.first { it.id == "v20-purged" }.toEntity()

        assertTrue(v18Dto.omittedTimeblockFields)
        assertFalse(v20ClearedDto.omittedTimeblockFields)
        assertTrue(v20PurgedEntity.isDeleted)
        assertEquals("", v20PurgedEntity.title)
        assertEquals("", v20PurgedEntity.notes)
    }

    @Test
    fun `v2_0_0_beta_24 sample task detection ignores Logbook completed items and preserves user_renamed tasks across all 5 languages`() {
        // 8 distinct localized demo titles per language (40 total across EN/DE/FR/IT/ES)
        assertEquals(40, DatabaseSeeder.KNOWN_SAMPLE_TASK_TITLES.size)

        val openGermanSample = TaskEntity(
            id = "task-inbox-001",
            title = "Zahnarzt-Vorsorgetermin für nächsten Monat vereinbaren",
            notes = "",
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.INBOX,
            sortOrder = 100.0,
            createdAt = 1_795_000_000_000L,
            updatedAt = 1_795_000_000_000L
        )
        assertTrue(DatabaseSeeder.isActiveSampleTask(openGermanSample))
        assertTrue(DatabaseSeeder.isPurgeableSampleTask(openGermanSample))

        // Logbook pre-seeded completed task-completed-008 MUST NOT trigger "Beispielaufgaben noch vorhanden"
        val logbookCompletedSample = TaskEntity(
            id = "task-completed-008",
            title = "Schreibtisch aufgeräumt & Wochenfokus festgelegt",
            notes = "",
            status = TaskStatus.COMPLETED,
            scheduleState = ScheduleState.TODAY,
            sortOrder = 800.0,
            createdAt = 1_795_000_000_000L,
            updatedAt = 1_795_000_000_000L
        )
        assertFalse(DatabaseSeeder.isActiveSampleTask(logbookCompletedSample))
        assertTrue(DatabaseSeeder.isPurgeableSampleTask(logbookCompletedSample))

        // Deleted sample task is inactive
        assertFalse(DatabaseSeeder.isActiveSampleTask(openGermanSample.copy(isDeleted = true)))

        // User-renamed sample task is neither flagged as active demo task nor purgeable during sample cleanup
        val userRenamedTask = openGermanSample.copy(title = "Steuererklärung für 2026 abgeben")
        assertFalse(DatabaseSeeder.isActiveSampleTask(userRenamedTask))
        assertFalse(DatabaseSeeder.isPurgeableSampleTask(userRenamedTask))

        // Regular user-created UUID task with coincidentally similar or custom title is never a sample task
        val normalUserTask = openGermanSample.copy(id = "user-uuid-999")
        assertFalse(DatabaseSeeder.isActiveSampleTask(normalUserTask))
        assertFalse(DatabaseSeeder.isPurgeableSampleTask(normalUserTask))
    }

    @Test
    fun `v2_0_0_beta_25 TaskCardInteractionRules suppresses auto-focus on existing tasks and scopes quick This Evening pill strictly to Today view`() {
        assertTrue(com.pv.thingsbook.domain.model.TaskCardInteractionRules.shouldAutoFocusTitleOnExpand(""))
        assertTrue(com.pv.thingsbook.domain.model.TaskCardInteractionRules.shouldAutoFocusTitleOnExpand("   "))
        assertFalse(com.pv.thingsbook.domain.model.TaskCardInteractionRules.shouldAutoFocusTitleOnExpand("Review Q4 Plan"))

        assertTrue(
            com.pv.thingsbook.domain.model.TaskCardInteractionRules.shouldShowQuickThisEveningPill(
                isTodayView = true,
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.TODAY
            )
        )
        assertFalse(
            com.pv.thingsbook.domain.model.TaskCardInteractionRules.shouldShowQuickThisEveningPill(
                isTodayView = true,
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.THIS_EVENING
            )
        )
        assertFalse(
            com.pv.thingsbook.domain.model.TaskCardInteractionRules.shouldShowQuickThisEveningPill(
                isTodayView = false,
                status = TaskStatus.OPEN,
                scheduleState = ScheduleState.TODAY
            )
        )
        assertFalse(
            com.pv.thingsbook.domain.model.TaskCardInteractionRules.shouldShowQuickThisEveningPill(
                isTodayView = true,
                status = TaskStatus.COMPLETED,
                scheduleState = ScheduleState.TODAY
            )
        )
    }

    @Test
    fun `v2_0_1 Planned UPCOMING navigation isolates LazyColumn scroll scope and trailing gap keys so switching views always starts at top`() {
        val todayNav = com.pv.thingsbook.ui.viewmodel.ActiveNavigationTarget.SmartView(
            com.pv.thingsbook.ui.components.SmartViewType.TODAY
        )
        val inboxNav = com.pv.thingsbook.ui.viewmodel.ActiveNavigationTarget.SmartView(
            com.pv.thingsbook.ui.components.SmartViewType.INBOX
        )
        val upcomingNav = com.pv.thingsbook.ui.viewmodel.ActiveNavigationTarget.SmartView(
            com.pv.thingsbook.ui.components.SmartViewType.UPCOMING
        )
        val anytimeNav = com.pv.thingsbook.ui.viewmodel.ActiveNavigationTarget.SmartView(
            com.pv.thingsbook.ui.components.SmartViewType.ANYTIME
        )
        val areaNav = com.pv.thingsbook.ui.viewmodel.ActiveNavigationTarget.AreaDetail("area-work")
        val projectNav = com.pv.thingsbook.ui.viewmodel.ActiveNavigationTarget.ProjectDetail("proj-q4")

        val allTargets = listOf(todayNav, inboxNav, upcomingNav, anytimeNav, areaNav, projectNav)

        // Every destination has a distinct scrollScopeKey, topScrollAnchorKey, and trailingMagicPlusGapKey
        assertEquals(allTargets.size, allTargets.map { it.scrollScopeKey() }.toSet().size)
        assertEquals(allTargets.size, allTargets.map { it.topScrollAnchorKey() }.toSet().size)
        assertEquals(allTargets.size, allTargets.map { it.trailingMagicPlusGapKey() }.toSet().size)

        // Switching from Today/Inbox to Planned (UPCOMING) without an expanded task resets scroll to top (index 0)
        assertEquals("smart_UPCOMING", upcomingNav.scrollScopeKey())
        assertTrue(upcomingNav.shouldResetScrollToTop(expandedTaskId = null))
        assertTrue(upcomingNav.shouldResetScrollToTop(expandedTaskId = ""))
        assertFalse(upcomingNav.shouldResetScrollToTop(expandedTaskId = "task-deep-link-42"))
    }

    @Test
    fun `v2_1_0 Option B unified inline editor localizes action_more_options across all 5 languages and removes duplicate bottom toolbar`() {
        val resRoot = sequenceOf(
            File("src/main/res"),
            File("app/src/main/res")
        ).first { it.isDirectory }

        val folders = listOf("values", "values-de", "values-fr", "values-it", "values-es")
        for (folder in folders) {
            val xmlFile = File(resRoot, "$folder/strings.xml")
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("string")
            val keysInFile = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i)
                val name = elem.attributes?.getNamedItem("name")?.nodeValue ?: continue
                keysInFile[name] = elem.textContent.orEmpty()
            }
            val moreLabel = keysInFile["action_more_options"]
            assertNotNull("Missing action_more_options in $folder/strings.xml", moreLabel)
            assertTrue("Blank action_more_options in $folder/strings.xml", moreLabel!!.isNotBlank())
        }

        val cardSource = sequenceOf(
            File("src/main/java/com/pv/thingsbook/ui/components/ExpandableTaskCard.kt"),
            File("app/src/main/java/com/pv/thingsbook/ui/components/ExpandableTaskCard.kt")
        ).first { it.isFile }.readText()

        assertTrue(cardSource.contains("v2.1 Unified Single Footer Row"))
        assertTrue(cardSource.contains("R.string.action_more_options"))
        assertTrue(cardSource.contains("if (task.attachments.isNotEmpty())"))
        assertFalse("Removed legacy BottomActionIcon helper", cardSource.contains("private fun BottomActionIcon("))
    }

    @Test
    fun `v3_0_0_alpha_1 Material 3 Expressive Crisp White and Neutral Dark palettes with Sky Light Blue accent and ALPHA badges`() {
        val light = com.pv.thingsbook.ui.theme.LightThingsPalette
        val dark = com.pv.thingsbook.ui.theme.DarkThingsPalette

        // Crisp pure white Light surface & Sky Light Blue accent
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFFFFF), light.canvasBackground)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF4F5F8), light.sidebarBackground)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF3898EC), light.thingsBlue)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFE5F3FF), light.thingsBlueSoft)

        // Deep neutral charcoal Dark surface & luminous Sky Light Blue accent
        assertEquals(androidx.compose.ui.graphics.Color(0xFF121417), dark.canvasBackground)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF171A1E), dark.sidebarBackground)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF6BC0FF), dark.thingsBlue)

        // Pre-release badge localized as ALPHA across all 5 languages
        val resRoot = sequenceOf(
            File("src/main/res"),
            File("app/src/main/res")
        ).first { it.isDirectory }
        val folders = listOf("values", "values-de", "values-fr", "values-it", "values-es")
        for (folder in folders) {
            val xmlFile = File(resRoot, "$folder/strings.xml")
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("string")
            var badgeText: String? = null
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i)
                if (elem.attributes?.getNamedItem("name")?.nodeValue == "v2_beta_badge") {
                    badgeText = elem.textContent
                    break
                }
            }
            assertNotNull("Missing v2_beta_badge in $folder/strings.xml", badgeText)
            assertTrue("Expected ALPHA in $folder/strings.xml v2_beta_badge, got '$badgeText'", badgeText!!.contains("ALPHA"))
        }
    }

    @Test
    fun `v3_0_0_alpha_2 Project Phases endDate preserves 100 percent v2_x backup sync and template compatibility`() {
        val phaseEndMs = 1_780_000_000_000L

        // 1. v2.x backup JSON without endDate on headings decodes cleanly to endDate = null
        val v2HeadingJson = """
            {
              "schemaVersion": 1,
              "createdAt": 1770000000000,
              "appVersionName": "2.1.0",
              "headings": [
                {
                  "id": "phase-v2",
                  "projectId": "proj-1",
                  "title": "Design",
                  "sortOrder": 1.0,
                  "isArchived": false,
                  "createdAt": 1770000000000,
                  "updatedAt": 1770000000000
                }
              ]
            }
        """.trimIndent()
        val gzipOut = ByteArrayOutputStream()
        GZIPOutputStream(gzipOut).use { it.write(v2HeadingJson.toByteArray(StandardCharsets.UTF_8)) }
        val v2Env = ThingsbookBackupEnvelope(
            magic = ThingsBackupCodec.BACKUP_MAGIC,
            formatVersion = ThingsBackupCodec.FORMAT_VERSION,
            appVersionName = "2.1.0",
            appVersionCode = 73,
            createdAt = 1_770_000_000_000L,
            encrypted = false,
            payloadGzipBase64 = Base64.getEncoder().encodeToString(gzipOut.toByteArray())
        )
        val decodedV2 = ThingsBackupCodec.decodeBackup(
            lenientJson.encodeToString(v2Env).toByteArray(StandardCharsets.UTF_8),
            password = null
        )
        assertEquals(1, decodedV2.headings.size)
        assertEquals("Design", decodedV2.headings.single().title)
        assertNull(decodedV2.headings.single().endDate)

        // 2. v3.0.0-alpha.2 backup roundtrips Phase endDate under unchanged "headings" key
        val v3Payload = ThingsbookBackupPayload(
            schemaVersion = ThingsBackupCodec.FORMAT_VERSION,
            createdAt = 1_775_000_000_000L,
            appVersionName = "3.0.0-alpha.2",
            headings = listOf(
                BackupHeadingDto(
                    id = "phase-v3",
                    projectId = "proj-1",
                    title = "Alpha Milestone",
                    endDate = phaseEndMs,
                    sortOrder = 2.0
                )
            )
        )
        val encodedV3 = ThingsBackupCodec.encodeBackup(
            payload = v3Payload,
            password = null,
            appVersionName = "3.0.0-alpha.2",
            appVersionCode = 75
        )
        val decodedV3 = ThingsBackupCodec.decodeBackup(encodedV3, password = null)
        assertEquals(phaseEndMs, decodedV3.headings.single().endDate)

        // 3. Firestore wire DTO: v2.x map omitting endDate flags omittedEndDate = true; explicit null clears it
        val v2FirestoreHeading = com.pv.thingsbook.data.remote.dto.HeadingFirestoreDto.fromFirestoreMap(
            mapOf(
                "id" to "phase-sync-1",
                "projectId" to "proj-1",
                "title" to "Renamed on v2.1",
                "sortOrder" to 3.0,
                "updatedAt" to 1_776_000_000_000L
            )
        )!!
        assertTrue(v2FirestoreHeading.omittedEndDate)
        assertNull(v2FirestoreHeading.endDate)

        val v3ClearedHeading = com.pv.thingsbook.data.remote.dto.HeadingFirestoreDto.fromFirestoreMap(
            mapOf(
                "id" to "phase-sync-1",
                "projectId" to "proj-1",
                "title" to "Cleared on v3.0",
                "endDate" to null,
                "sortOrder" to 3.0,
                "updatedAt" to 1_776_000_001_000L
            )
        )!!
        assertFalse(v3ClearedHeading.omittedEndDate)
        assertNull(v3ClearedHeading.endDate)

        val v3MilestoneHeading = com.pv.thingsbook.data.remote.dto.HeadingFirestoreDto(
            id = "phase-sync-2",
            projectId = "proj-1",
            title = "Beta Freeze",
            endDate = phaseEndMs,
            sortOrder = 4.0,
            updatedAt = 1_776_000_002_000L
        )
        val roundtripHeadingDto = com.pv.thingsbook.data.remote.dto.HeadingFirestoreDto.fromFirestoreMap(
            v3MilestoneHeading.toFirestoreMap()
        )!!
        assertFalse(roundtripHeadingDto.omittedEndDate)
        assertEquals(phaseEndMs, roundtripHeadingDto.endDate)

        // 4. ProjectTemplateCodec preserves endDateDayOffset and accepts "phases" JSON alias
        val bpWithPhaseOffset = ProjectTemplateBlueprint(
            title = "Phased Launch",
            headings = listOf(
                TemplateHeadingBlueprint(
                    title = "Discovery Phase",
                    endDateDayOffset = 7,
                    tasks = listOf(TemplateTaskBlueprint(title = "Interview users"))
                )
            )
        )
        val encodedBp = ProjectTemplateCodec.encodeBlueprint(bpWithPhaseOffset)
        assertTrue("Encoded JSON keeps headings key for v2.x compatibility", encodedBp.contains("\"headings\""))
        val decodedBp = ProjectTemplateCodec.decodeBlueprint(encodedBp)
        assertEquals(7, decodedBp.headings.single().endDateDayOffset)

        val phasesAliasJson = """
            {
              "title": "Alias Project",
              "phases": [
                {
                  "title": "Phase One",
                  "endDateDayOffset": 14,
                  "tasks": [{"title": "Kickoff"}]
                }
              ]
            }
        """.trimIndent()
        val decodedAlias = ProjectTemplateCodec.decodeBlueprint(phasesAliasJson)
        assertEquals("Phase One", decodedAlias.headings.single().title)
        assertEquals(14, decodedAlias.headings.single().endDateDayOffset)

        // 5. Verify all 5 languages include the new phase_* keys and keep classic GTD Smart View labels
        val resRoot = sequenceOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val phaseKeys = listOf("phase_set_end_date", "phase_clear_end_date", "phase_ends_on", "phase_milestone_badge")
        for (folder in listOf("values", "values-de", "values-fr", "values-it", "values-es")) {
            val xmlFile = File(resRoot, "$folder/strings.xml")
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("string")
            val map = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i)
                val name = elem.attributes?.getNamedItem("name")?.nodeValue ?: continue
                map[name] = elem.textContent ?: ""
            }
            for (pk in phaseKeys) {
                assertTrue("Missing or blank $pk in $folder/strings.xml", !map[pk].isNullOrBlank())
            }
        }
    }

    @Test
    fun `v3_0_0_alpha_7 Home Screen widgets and macOS bundle use Glacier Book 3_0 design with zero legacy Things symbols`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val repoRoot = if (appSrcMain.path.startsWith("app/")) File(".") else File("..")

        val mainWidgetXml = File(appSrcMain, "res/layout/widget_thingsbook.xml").readText()
        val qcWidgetXml = File(appSrcMain, "res/layout/widget_thingsbook_quick_capture.xml").readText()
        val itemWidgetXml = File(appSrcMain, "res/layout/widget_thingsbook_task_item.xml").readText()
        val widgetProviderKt = File(appSrcMain, "java/com/pv/thingsbook/ui/widget/ThingsbookWidgetProvider.kt").readText()

        assertTrue(mainWidgetXml.contains("@drawable/ic_widget_brand_book"))
        assertTrue(mainWidgetXml.contains("@drawable/ic_widget_sun"))
        assertTrue(mainWidgetXml.contains("@drawable/ic_widget_compass"))
        assertTrue(mainWidgetXml.contains("@drawable/ic_widget_cloud"))
        assertTrue(mainWidgetXml.contains("@drawable/ic_widget_folder"))
        assertTrue(qcWidgetXml.contains("@drawable/ic_widget_brand_book"))
        assertTrue(qcWidgetXml.contains("@drawable/ic_widget_sun"))
        assertTrue(itemWidgetXml.contains("@drawable/bg_things_widget_task_row"))
        assertTrue(File(appSrcMain, "res/values/colors.xml").isFile)
        assertTrue(File(appSrcMain, "res/values-night/colors.xml").isFile)

        val forbiddenLegacyTokens = listOf("★", "#FFC312", "#2B7BF6", "🗂️", "📦")
        for (legacy in forbiddenLegacyTokens) {
            assertFalse("Legacy token $legacy found in widget_thingsbook.xml", mainWidgetXml.contains(legacy))
            assertFalse("Legacy token $legacy found in widget_thingsbook_quick_capture.xml", qcWidgetXml.contains(legacy))
            assertFalse("Legacy token $legacy found in widget_thingsbook_task_item.xml", itemWidgetXml.contains(legacy))
            assertFalse("Legacy token $legacy found in ThingsbookWidgetProvider.kt", widgetProviderKt.contains(legacy))
        }

        val infoPlist = File(repoRoot, "macos/Resources/Info.plist").readText()
        assertTrue(infoPlist.contains("<string>AppIconGlacierV3</string>"))
        assertTrue(infoPlist.contains("<string>com.thingsbook.macos</string>"))
    }

    @Test
    fun `v3_0_0_alpha_8 Category Assign and Manage dialogs keep constant viewport height across Android, ChromeOS PWA, and macOS`() {
        val appSrcMain = sequenceOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val repoRoot = if (appSrcMain.path.startsWith("app/")) File(".") else File("..")

        val androidModalKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ThingsTagManagerModal.kt").readText()
        assertFalse(
            "ThingsTagManagerModal.kt must not use dynamic heightIn() that resizes dialog on search",
            androidModalKt.contains("heightIn(")
        )
        assertTrue(androidModalKt.contains(".height(manageListHeight)"))
        assertTrue(androidModalKt.contains(".height(pickerListHeight)"))

        val chromeosModalsTsx = File(repoRoot, "chromeos-pwa/src/components/EntityModals.tsx").readText()
        assertTrue(chromeosModalsTsx.contains("height: 'min(250px, 38vh)'"))
        assertTrue(chromeosModalsTsx.contains("height: 'min(240px, 40vh)'"))

        val macosPopoversSwift = File(repoRoot, "macos/Sources/ThingsbookMac/Views/SchedulingPopovers.swift").readText()
        assertTrue(macosPopoversSwift.contains(".frame(height: 220)"))
        assertTrue(macosPopoversSwift.contains(".frame(height: 260)"))
    }
}
