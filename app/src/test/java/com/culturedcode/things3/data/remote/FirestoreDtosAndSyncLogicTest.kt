package com.culturedcode.things3.data.remote

import com.culturedcode.things3.data.backup.BackupAreaDto
import com.culturedcode.things3.data.backup.BackupInspectionResult
import com.culturedcode.things3.data.backup.BackupTaskDto
import com.culturedcode.things3.data.backup.ThingsBackupCodec
import com.culturedcode.things3.data.backup.ThingsbookBackupPayload
import com.culturedcode.things3.data.local.ThingsDatabase
import com.culturedcode.things3.data.local.entity.AreaEntity
import com.culturedcode.things3.data.local.entity.AttachmentEntity
import com.culturedcode.things3.data.local.entity.ChecklistItemEntity
import com.culturedcode.things3.data.local.entity.HeadingEntity
import com.culturedcode.things3.data.local.entity.ProjectEntity
import com.culturedcode.things3.data.local.entity.TagEntity
import com.culturedcode.things3.data.local.entity.TaskEntity
import com.culturedcode.things3.data.remote.dto.AreaFirestoreDto
import com.culturedcode.things3.data.remote.dto.AttachmentFirestoreDto
import com.culturedcode.things3.data.remote.dto.ChecklistItemFirestoreDto
import com.culturedcode.things3.data.remote.dto.HeadingFirestoreDto
import com.culturedcode.things3.data.remote.dto.ProjectFirestoreDto
import com.culturedcode.things3.data.remote.dto.TagFirestoreDto
import com.culturedcode.things3.data.remote.dto.TaskFirestoreDto
import com.culturedcode.things3.data.repository.CsvParserUtils
import com.culturedcode.things3.data.repository.SyncRepository
import com.culturedcode.things3.domain.model.ProjectStatus
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import kotlin.io.path.createTempDirectory

class FirestoreDtosAndSyncLogicTest {

    @Test
    fun `all 7 Firestore DTOs round-trip cleanly between Room entities and Firestore maps`() {
        val now = 1763200000000L

        // 1. Tag
        val tagEntity = TagEntity(
            id = "tag-1",
            title = "Deep Work",
            parentTagId = null,
            shortcut = "d",
            sortOrder = 10.0,
            createdAt = now,
            updatedAt = now + 50,
            isDeleted = false,
            isDirty = true
        )
        val tagMap = TagFirestoreDto.fromEntity(tagEntity).toFirestoreMap()
        val tagRoundTrip = TagFirestoreDto.fromFirestoreMap(tagMap)!!.toEntity()
        assertEquals(tagEntity.copy(isDirty = false), tagRoundTrip)

        // 2. Area
        val areaEntity = AreaEntity(
            id = "area-1",
            title = "Engineering",
            sortOrder = 20.0,
            tagIdsJson = "[\"tag-1\"]",
            createdAt = now,
            updatedAt = now + 100,
            isDeleted = false,
            isDirty = true
        )
        val areaMap = AreaFirestoreDto.fromEntity(areaEntity).toFirestoreMap()
        val areaRoundTrip = AreaFirestoreDto.fromFirestoreMap(areaMap)!!.toEntity()
        assertEquals(areaEntity.copy(isDirty = false), areaRoundTrip)

        // 3. Project
        val projEntity = ProjectEntity(
            id = "proj-1",
            areaId = "area-1",
            title = "v1.8.1 Release",
            notes = "Cross-platform sync parity",
            status = ProjectStatus.ACTIVE,
            scheduleState = ScheduleState.TODAY,
            whenDate = now,
            deadline = now + 86_400_000L,
            completedAt = null,
            sortOrder = 30.0,
            tagIdsJson = "[\"tag-1\"]",
            createdAt = now,
            updatedAt = now + 200,
            isDeleted = false,
            isDirty = true
        )
        val projMap = ProjectFirestoreDto.fromEntity(projEntity).toFirestoreMap()
        val projRoundTrip = ProjectFirestoreDto.fromFirestoreMap(projMap)!!.toEntity()
        assertEquals(projEntity.copy(isDirty = false), projRoundTrip)

        // 4. Heading
        val headingEntity = HeadingEntity(
            id = "head-1",
            projectId = "proj-1",
            title = "QA Verification",
            sortOrder = 40.0,
            isArchived = false,
            createdAt = now,
            updatedAt = now + 300,
            isDeleted = false,
            isDirty = true
        )
        val headingMap = HeadingFirestoreDto.fromEntity(headingEntity).toFirestoreMap()
        val headingRoundTrip = HeadingFirestoreDto.fromFirestoreMap(headingMap)!!.toEntity()
        assertEquals(headingEntity.copy(isDirty = false), headingRoundTrip)

        // 5. Task
        val taskEntity = TaskEntity(
            id = "task-1",
            title = "Verify Google OAuth UID parity",
            notes = "Same uid across Android, ChromeOS & macOS",
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.TODAY,
            whenDate = now,
            deadline = now + 86_400_000L,
            reminderTime = now + 3_600_000L,
            completedAt = null,
            projectId = "proj-1",
            headingId = "head-1",
            areaId = "area-1",
            recurrenceRuleJson = null,
            sortOrder = 50.0,
            tagIdsJson = "[\"tag-1\"]",
            createdAt = now,
            updatedAt = now + 400,
            isDeleted = false,
            isDirty = true
        )
        val taskMap = TaskFirestoreDto.fromEntity(taskEntity).toFirestoreMap()
        val taskRoundTrip = TaskFirestoreDto.fromFirestoreMap(taskMap)!!.toEntity()
        assertEquals(taskEntity.copy(isDirty = false), taskRoundTrip)

        // 6. ChecklistItem
        val checkEntity = ChecklistItemEntity(
            id = "chk-1",
            taskId = "task-1",
            title = "Run unit test suite",
            isCompleted = true,
            sortOrder = 60.0,
            createdAt = now,
            updatedAt = now + 500,
            isDeleted = false,
            isDirty = true
        )
        val checkMap = ChecklistItemFirestoreDto.fromEntity(checkEntity).toFirestoreMap()
        val checkRoundTrip = ChecklistItemFirestoreDto.fromFirestoreMap(checkMap)!!.toEntity()
        assertEquals(checkEntity.copy(isDirty = false), checkRoundTrip)

        // 7. Attachment (sortOrder defaults to createdAt.toDouble(), localRelativePath stripped to "")
        val attEntity = AttachmentEntity(
            id = "att-1",
            taskId = "task-1",
            projectId = null,
            fileName = "architecture.pdf",
            mimeType = "application/pdf",
            sizeBytes = 2048L,
            localRelativePath = "attachments/att-1_architecture.pdf",
            createdAt = now,
            updatedAt = now + 600,
            isDeleted = false,
            isDirty = true
        )
        val attDto = AttachmentFirestoreDto.fromEntity(attEntity, dataBase64 = "JVBERi0xLjQK")
        assertEquals(now.toDouble(), attDto.sortOrder, 0.001)
        assertEquals("", attDto.localRelativePath)

        val attMap = attDto.toFirestoreMap()
        assertEquals("", attMap["localRelativePath"])
        assertEquals(now.toDouble(), (attMap["sortOrder"] as Number).toDouble(), 0.001)

        val remoteMapWithUntrustedPath = attMap.toMutableMap().apply {
            put("localRelativePath", "../../databases/thingsbook_v5_mac_edition.db")
        }
        val parsedAttDto = AttachmentFirestoreDto.fromFirestoreMap(remoteMapWithUntrustedPath)
        assertNotNull(parsedAttDto)
        assertEquals("", parsedAttDto!!.localRelativePath)
        val safePath = SyncRepository.buildSafeLocalAttachmentPath(parsedAttDto.id, parsedAttDto.fileName)
        assertEquals("attachments/att-1_architecture.pdf", safePath)
        val attRoundTrip = parsedAttDto.toEntity(resolvedRelativePath = safePath)
        assertEquals(attEntity.copy(isDirty = false), attRoundTrip)
    }

    @Test
    fun `attachment path canonicalization blocks path traversal and allows safe files`() {
        val tempFilesDir = createTempDirectory("thingsbook_att_test").toFile()
        try {
            val secretDbDir = File(tempFilesDir.parentFile, "databases").apply { mkdirs() }
            val secretDbFile = File(secretDbDir, "things3_database").apply { writeText("TOP_SECRET") }
            val siblingOutsideAttachments = File(tempFilesDir, "prefs.xml").apply { writeText("PREFS") }

            val maliciousPaths = listOf(
                "../databases/things3_database",
                "attachments/../../databases/things3_database",
                "/etc/passwd",
                "prefs.xml",
                "attachments/../prefs.xml",
                ""
            )

            for (badPath in maliciousPaths) {
                assertThrows("Expected SecurityException for '$badPath'", SecurityException::class.java) {
                    SyncRepository.resolveVerifiedAttachmentFile(tempFilesDir, badPath)
                }
                assertEquals("", SyncRepository.readAttachmentBase64InRoot(tempFilesDir, badPath))
                assertEquals(0L, SyncRepository.writeAttachmentBase64InRoot(tempFilesDir, badPath, "SGVsbG8="))
                SyncRepository.deleteAttachmentFileInRoot(tempFilesDir, badPath)
            }

            // Ensure outside files were never touched or deleted
            assertTrue(secretDbFile.exists())
            assertTrue(siblingOutsideAttachments.exists())

            // Valid attachment write/read/delete inside attachments/
            assertEquals(700_000L, SyncRepository.MAX_INLINE_SYNC_BYTES)
            val safeRelPath = SyncRepository.buildSafeLocalAttachmentPath("../att:1", "../../photo?.jpg")
            assertTrue(safeRelPath.startsWith("attachments/"))
            assertFalse(safeRelPath.contains(".."))

            val payload = "Hello Thingsbook v1.8.1!".encodeToByteArray()
            val b64 = Base64.getEncoder().encodeToString(payload)
            val written = SyncRepository.writeAttachmentBase64InRoot(tempFilesDir, "attachments/att1_photo.jpg", b64)
            assertEquals(payload.size.toLong(), written)

            val readBack = SyncRepository.readAttachmentBase64InRoot(tempFilesDir, "attachments/att1_photo.jpg")
            assertEquals(b64, readBack)

            SyncRepository.deleteAttachmentFileInRoot(tempFilesDir, "attachments/att1_photo.jpg")
            assertEquals("", SyncRepository.readAttachmentBase64InRoot(tempFilesDir, "attachments/att1_photo.jpg"))
        } finally {
            tempFilesDir.deleteRecursively()
        }
    }

    @Test
    fun `delta sync 120s clock-skew floor and 60s future-timestamp clamp behave accurately`() {
        assertEquals(0L, SyncRepository.computeDeltaQueryFloor(0L))
        assertEquals(0L, SyncRepository.computeDeltaQueryFloor(60_000L))
        assertEquals(80_000L, SyncRepository.computeDeltaQueryFloor(200_000L))

        val fixedNow = 1_763_000_000_000L
        assertEquals(fixedNow - 5_000L, SyncRepository.clampRemoteTimestamp(fixedNow - 5_000L, fixedNow))
        assertEquals(fixedNow + 30_000L, SyncRepository.clampRemoteTimestamp(fixedNow + 30_000L, fixedNow))
        // Far-future rogue timestamp (e.g. +1 year) is clamped to now + 60s so delta cursor never stalls
        assertEquals(
            fixedNow + 60_000L,
            SyncRepository.clampRemoteTimestamp(fixedNow + 31_536_000_000L, fixedNow)
        )
    }

    @Test
    fun `backup codec supports 120 MB decompressed cap and round-trips plain and encrypted backups`() {
        assertEquals(120 * 1024 * 1024, ThingsBackupCodec.MAX_DECOMPRESSED_BYTES)

        val payload = ThingsbookBackupPayload(
            createdAt = 1_763_200_000_000L,
            appVersionName = "1.8.1",
            areas = listOf(BackupAreaDto(id = "a1", title = "Work")),
            tasks = listOf(BackupTaskDto(id = "t1", title = "Ship v1.8.1", areaId = "a1"))
        )

        val plainBytes = ThingsBackupCodec.encodeBackup(payload, null, "1.8.1", 44)
        val plainInspected = ThingsBackupCodec.inspectBackup(plainBytes)
        assertTrue(plainInspected is BackupInspectionResult.UnencryptedPreview)
        val plainDecoded = ThingsBackupCodec.decodeBackup(plainBytes, null)
        assertEquals("Ship v1.8.1", plainDecoded.tasks.single().title)

        val encBytes = ThingsBackupCodec.encodeBackup(payload, "correct-horse".toCharArray(), "1.8.1", 44)
        val encInspected = ThingsBackupCodec.inspectBackup(encBytes)
        assertTrue(encInspected is BackupInspectionResult.PasswordProtected)
        val encDecoded = ThingsBackupCodec.decodeBackup(encBytes, "correct-horse".toCharArray())
        assertEquals("Work", encDecoded.areas.single().title)
    }

    @Test
    fun `CSV flexible date parser semicolon escaping and SQL LIKE escaping handle edge cases`() {
        val zone = ZoneId.of("UTC")
        val expectedNov15 = LocalDate.of(2026, 11, 15).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(expectedNov15, CsvParserUtils.parseFlexibleCsvDate("2026-11-15", zone))
        assertEquals(expectedNov15, CsvParserUtils.parseFlexibleCsvDate("15.11.2026", zone))
        assertEquals(expectedNov15, CsvParserUtils.parseFlexibleCsvDate("15/11/2026", zone))
        // US MM/DD/YYYY fallback when middle token > 12
        assertEquals(expectedNov15, CsvParserUtils.parseFlexibleCsvDate("11/15/2026", zone))

        // Semicolon & backslash escaping in CSV tags / checklist items
        val itemsWithSemicolons = listOf("Buy milk; oat", "Path C:\\temp", "Plain item")
        val encoded = CsvParserUtils.encodeSemicolonList(itemsWithSemicolons)
        val decoded = CsvParserUtils.decodeSemicolonList(encoded)
        assertEquals(itemsWithSemicolons, decoded)

        // SQL LIKE pattern escaping for % _ and \
        assertEquals("100\\%\\_done\\\\now", ThingsDatabase.escapeSqlLikePattern("100%_done\\now"))
    }
}
