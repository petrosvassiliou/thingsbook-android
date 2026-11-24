package com.pv.thingsbook

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pv.thingsbook.data.backup.BackupTaskDto
import com.pv.thingsbook.data.backup.ThingsBackupCodec
import com.pv.thingsbook.data.backup.ThingsbookBackupPayload
import com.pv.thingsbook.data.external.ExternalExportFormat
import com.pv.thingsbook.data.external.ExternalProjectCodec
import com.pv.thingsbook.data.local.entity.TaskEntity
import com.pv.thingsbook.data.remote.dto.CloudDeltaBatchPayload
import com.pv.thingsbook.data.remote.dto.TaskFirestoreDto
import com.pv.thingsbook.data.repository.SyncRepository
import com.pv.thingsbook.di.AppModule
import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.EffortLevel
import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.ProjectStatus
import com.pv.thingsbook.domain.model.QuickFindFilter
import com.pv.thingsbook.domain.model.SavedPerspective
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.StalledProjectDetector
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import com.pv.thingsbook.domain.model.filterTasksForPerspective
import com.pv.thingsbook.domain.model.sortTasksForView
import com.pv.thingsbook.domain.parser.SmartSyntaxParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class V33InProgressIndicatorTest {

    private val refDate: LocalDate = LocalDate.of(2026, 11, 24)

    private fun sampleTaskItem(
        id: String,
        title: String = id,
        status: TaskStatus = TaskStatus.OPEN,
        scheduleState: ScheduleState = ScheduleState.TODAY,
        sortOrder: Double = 100.0,
        isPinned: Boolean = false,
        isMilestone: Boolean = false,
        isInProgress: Boolean = false,
        deadline: Long? = null,
        durationMinutes: Int? = null,
        effortLevel: String? = null,
        isDeleted: Boolean = false
    ): TaskItem = TaskItem(
        id = id,
        title = title,
        notes = "",
        status = status,
        scheduleState = scheduleState,
        whenDate = null,
        deadline = deadline,
        reminderTime = null,
        durationMinutes = durationMinutes,
        completedAt = if (status == TaskStatus.OPEN) null else 1_760_000_000_000L,
        projectId = null,
        headingId = null,
        areaId = null,
        recurrenceRule = null,
        sortOrder = sortOrder,
        isPinned = isPinned,
        isMilestone = isMilestone,
        isInProgress = isInProgress,
        effortLevel = effortLevel,
        createdAt = 1_000L,
        updatedAt = 2_000L,
        isDeleted = isDeleted
    )

    private fun sampleProject(
        id: String = "p-1",
        title: String = "v3.3 Launch",
        status: ProjectStatus = ProjectStatus.ACTIVE,
        scheduleState: ScheduleState = ScheduleState.ANYTIME,
        totalTaskCount: Int = 10,
        completedTaskCount: Int = 4,
        inProgressTaskCount: Int = 0,
        lastReviewedAt: Long? = null,
        createdAt: Long = 1_000L,
        updatedAt: Long = 2_000L,
        isDeleted: Boolean = false
    ): Project = Project(
        id = id,
        areaId = null,
        title = title,
        notes = "",
        status = status,
        scheduleState = scheduleState,
        whenDate = null,
        deadline = null,
        completedAt = null,
        lastReviewedAt = lastReviewedAt,
        sortOrder = 1.0,
        tagIds = emptyList(),
        totalTaskCount = totalTaskCount,
        completedTaskCount = completedTaskCount,
        inProgressTaskCount = inProgressTaskCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun createPragmaTrackingDb(
        existingColumns: Set<String>,
        executedSqlOut: MutableList<String>
    ): SupportSQLiteDatabase {
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            when (method.name) {
                "query" -> {
                    val cols = existingColumns.toList()
                    var rowIdx = -1
                    Proxy.newProxyInstance(
                        Cursor::class.java.classLoader,
                        arrayOf(Cursor::class.java)
                    ) { _, cMethod, cArgs ->
                        when (cMethod.name) {
                            "getColumnIndex" -> if (cArgs?.firstOrNull() == "name") 1 else -1
                            "moveToNext" -> {
                                rowIdx++
                                rowIdx < cols.size
                            }
                            "getString" -> cols[rowIdx]
                            "close" -> Unit
                            else -> when (cMethod.returnType) {
                                java.lang.Boolean.TYPE -> false
                                java.lang.Integer.TYPE -> 0
                                java.lang.Long.TYPE -> 0L
                                else -> null
                            }
                        }
                    }
                }
                "execSQL" -> {
                    val sql = args?.firstOrNull() as? String
                    if (sql != null) executedSqlOut += sql
                    Unit
                }
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        } as SupportSQLiteDatabase
    }

    @Test
    fun `SmartSyntaxParser recognizes standalone half_circle and multilingual wip tokens across EN DE FR IT ES`() {
        val tokensAndLocales = listOf(
            "!wip" to Locale.US,
            "!WIP" to Locale.US,
            "!inprogress" to Locale.US,
            "!in-progress" to Locale.US,
            "!doing" to Locale.US,
            "!\"in progress\"" to Locale.US,
            "!inarbeit" to Locale.GERMAN,
            "!in-arbeit" to Locale.GERMAN,
            "!\"in arbeit\"" to Locale.GERMAN,
            "!encours" to Locale.FRENCH,
            "!en-cours" to Locale.FRENCH,
            "!\"en cours\"" to Locale.FRENCH,
            "!incorso" to Locale.ITALIAN,
            "!in-corso" to Locale.ITALIAN,
            "!\"in corso\"" to Locale.ITALIAN,
            "!encurso" to Locale("es"),
            "!en-curso" to Locale("es"),
            "!\"en curso\"" to Locale("es"),
            "◐" to Locale.US
        )

        for ((token, locale) in tokensAndLocales) {
            val parsed = SmartSyntaxParser.parse(
                rawTitle = "Ship Android sync $token",
                locale = locale,
                referenceDate = refDate
            )
            assertTrue("Expected isInProgress=true for token '$token'", parsed.isInProgress)
            assertTrue("Expected hasAnyMatch=true for token '$token'", parsed.hasAnyMatch)
            assertEquals("Failed cleanTitle for token '$token'", "Ship Android sync", parsed.cleanTitle)
            assertNull("WIP token '$token' must not set deadline", parsed.deadline)
        }

        // Combined with milestone, duration, tag, and project
        val tags = listOf(Tag(id = "t1", title = "Urgent", parentTagId = null, shortcut = null, sortOrder = 1.0, createdAt = 1L, updatedAt = 1L))
        val areas = listOf(Area(id = "a1", title = "Engineering", sortOrder = 1.0, createdAt = 1L, updatedAt = 1L))
        val projects = listOf(sampleProject(id = "p1", title = "Mobile App"))

        val combined = SmartSyntaxParser.parse(
            rawTitle = "◐ Refactor repository !milestone ~45m #Urgent /Mobile-App",
            tags = tags,
            projects = projects,
            areas = areas,
            locale = Locale.US,
            referenceDate = refDate
        )
        assertEquals("Refactor repository", combined.cleanTitle)
        assertTrue(combined.isInProgress)
        assertTrue(combined.isMilestone)
        assertEquals(45, combined.durationMinutes)
        assertEquals(listOf("t1"), combined.matchedTagIds)
        assertEquals("p1", combined.matchedProject?.id)

        // Mid-word symbols or URLs must remain untouched
        val untouched = SmartSyntaxParser.parse(
            rawTitle = "Check https://example.com/wip!doing status",
            locale = Locale.US,
            referenceDate = refDate
        )
        assertFalse(untouched.isInProgress)
        assertFalse(untouched.hasAnyMatch)
    }

    @Test
    fun `Project inProgressProgress computes stacked fraction and clamps cleanly against completed progress`() {
        val emptyProj = sampleProject(totalTaskCount = 0, completedTaskCount = 0, inProgressTaskCount = 3)
        assertEquals(0f, emptyProj.progress, 0.0001f)
        assertEquals(0f, emptyProj.inProgressProgress, 0.0001f)

        val balanced = sampleProject(totalTaskCount = 10, completedTaskCount = 4, inProgressTaskCount = 3)
        assertEquals(0.4f, balanced.progress, 0.0001f)
        assertEquals(0.3f, balanced.inProgressProgress, 0.0001f)

        // Overflow guard: completed (8/10 = 0.8) + inProgress (5/10 = 0.5) must clamp inProgressProgress to 0.2
        val overflow = sampleProject(totalTaskCount = 10, completedTaskCount = 8, inProgressTaskCount = 5)
        assertEquals(0.8f, overflow.progress, 0.0001f)
        assertEquals(0.2f, overflow.inProgressProgress, 0.0001f)

        // 100% completed project clamps inProgressProgress to 0f
        val full = sampleProject(totalTaskCount = 5, completedTaskCount = 5, inProgressTaskCount = 2)
        assertEquals(1.0f, full.progress, 0.0001f)
        assertEquals(0.0f, full.inProgressProgress, 0.0001f)
    }

    @Test
    fun `StalledProjectDetector suppresses stalled status whenever project has active tasks in progress`() {
        val now = 1_760_000_000_000L
        val thirtyDaysAgo = now - 30L * StalledProjectDetector.ONE_DAY_MILLIS

        val unreviewedIdleProject = sampleProject(
            id = "p-idle",
            totalTaskCount = 5,
            completedTaskCount = 1,
            inProgressTaskCount = 0,
            lastReviewedAt = thirtyDaysAgo,
            createdAt = thirtyDaysAgo,
            updatedAt = thirtyDaysAgo
        )
        val idleEval = StalledProjectDetector.evaluateProject(unreviewedIdleProject, nowMillis = now)
        assertNotNull("Idle unreviewed project should be flagged for review", idleEval)
        assertTrue(idleEval!!.isUnreviewed)

        val activeWipProject = unreviewedIdleProject.copy(
            id = "p-wip",
            inProgressTaskCount = 1
        )
        assertNull(
            "Project with inProgressTaskCount > 0 must NOT be marked stalled",
            StalledProjectDetector.evaluateProject(activeWipProject, nowMillis = now)
        )

        val detected = StalledProjectDetector.detectStalledProjects(
            projects = listOf(unreviewedIdleProject, activeWipProject),
            nowMillis = now
        )
        assertEquals(listOf("p-idle"), detected.map { it.project.id })
    }

    @Test
    fun `sortTasksForView ranks Pinned above Open InProgress above normal Open across sort modes`() {
        val normalEarly = sampleTaskItem(
            id = "t-normal-1",
            title = "Alpha Normal",
            sortOrder = 10.0,
            deadline = 1_000L,
            durationMinutes = 5,
            effortLevel = EffortLevel.QUICK_WIN
        )
        val wipLater = sampleTaskItem(
            id = "t-wip",
            title = "Zulu WIP",
            sortOrder = 500.0,
            isInProgress = true,
            deadline = 9_000L,
            durationMinutes = 120,
            effortLevel = EffortLevel.DEEP_FOCUS
        )
        val pinnedLast = sampleTaskItem(
            id = "t-pinned",
            title = "Omega Pinned",
            sortOrder = 900.0,
            isPinned = true
        )
        val completedStaleWip = sampleTaskItem(
            id = "t-done-stale",
            title = "AAA Completed",
            status = TaskStatus.COMPLETED,
            sortOrder = 1.0,
            isInProgress = true
        )

        for (mode in listOf("manual", "deadline", "duration", "effort", "alpha")) {
            val sorted = sortTasksForView(
                tasks = listOf(normalEarly, completedStaleWip, wipLater, pinnedLast),
                sortMode = mode
            )
            assertEquals("Pinned must rank #1 in mode=$mode", "t-pinned", sorted[0].id)
            assertEquals("Open WIP must rank #2 in mode=$mode", "t-wip", sorted[1].id)
        }
    }

    @Test
    fun `QuickFindFilter and SavedPerspective filter inProgressOnly tasks and projects accurately`() {
        val openWip = sampleTaskItem(id = "t-open-wip", isInProgress = true)
        val openIdle = sampleTaskItem(id = "t-open-idle", isInProgress = false)
        val doneWip = sampleTaskItem(id = "t-done-wip", status = TaskStatus.COMPLETED, isInProgress = true)

        val qf = QuickFindFilter(inProgressOnly = true)
        assertTrue(qf.hasActiveFilters)
        assertFalse(qf.shouldShowSmartViewsAndAreas(""))
        assertFalse(qf.shouldShowSmartViewsAndAreas("today"))
        assertTrue(qf.matchesTask(openWip))
        assertFalse(qf.matchesTask(openIdle))
        assertFalse(qf.matchesTask(doneWip))

        val projWithWip = sampleProject(id = "p-wip", inProgressTaskCount = 2)
        val projWithoutWip = sampleProject(id = "p-idle", inProgressTaskCount = 0)
        val completedProj = sampleProject(id = "p-done", status = ProjectStatus.COMPLETED, inProgressTaskCount = 1)
        assertTrue(qf.matchesProject(projWithWip))
        assertFalse(qf.matchesProject(projWithoutWip))
        assertFalse(qf.matchesProject(completedProj))

        val perspective = SavedPerspective(
            id = "persp-wip",
            title = "Active Work",
            inProgressOnly = true
        )
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val decodedPersp = json.decodeFromString<SavedPerspective>(json.encodeToString(perspective))
        assertTrue(decodedPersp.inProgressOnly)

        val filtered = filterTasksForPerspective(
            tasks = listOf(openWip, openIdle, doneWip),
            perspective = decodedPersp,
            openTaskIds = setOf("t-open-wip", "t-open-idle"),
            todayEpochMillis = 1_760_000_000_000L
        )
        assertEquals(listOf("t-open-wip"), filtered.map { it.id })
    }

    @Test
    fun `tbbackup and MS Planner CSV_XLSX codecs roundtrip isInProgress cleanly`() {
        // 1. .tbbackup roundtrip (encrypted)
        val payload = ThingsbookBackupPayload(
            createdAt = 1_760_000_000_000L,
            appVersionName = "3.3.0",
            tasks = listOf(
                BackupTaskDto(
                    id = "task-wip-1",
                    title = "In Flight Feature",
                    status = TaskStatus.OPEN.name,
                    scheduleState = ScheduleState.TODAY.name,
                    isInProgress = true
                ),
                BackupTaskDto(
                    id = "task-idle-2",
                    title = "Backlog Item",
                    status = TaskStatus.OPEN.name,
                    scheduleState = ScheduleState.ANYTIME.name,
                    isInProgress = false
                )
            )
        )
        val password = "v33-secret-password".toCharArray()
        val encoded = ThingsBackupCodec.encodeBackup(
            payload = payload,
            password = password,
            appVersionName = "3.3.0",
            appVersionCode = 119
        )
        val restored = ThingsBackupCodec.decodeBackup(encoded, password)
        assertEquals(true, restored.tasks.first { it.id == "task-wip-1" }.isInProgress)
        assertEquals(false, restored.tasks.first { it.id == "task-idle-2" }.isInProgress)

        // 2. MS Planner CSV & XLSX export/import roundtrip + localized progress values
        val project = sampleProject(id = "p-ext", title = "Planner Sync", totalTaskCount = 3, completedTaskCount = 1, inProgressTaskCount = 1)
        val heading = Heading(id = "h-1", projectId = "p-ext", title = "Sprint 1", sortOrder = 1.0, createdAt = 1L, updatedAt = 1L)
        val exportTasks = listOf(
            sampleTaskItem(id = "e-wip", title = "Build UI", isInProgress = true).copy(projectId = "p-ext", headingId = "h-1"),
            sampleTaskItem(id = "e-open", title = "Write Docs", isInProgress = false).copy(projectId = "p-ext", headingId = "h-1"),
            sampleTaskItem(id = "e-done", title = "Design Spec", status = TaskStatus.COMPLETED, isInProgress = true).copy(projectId = "p-ext", headingId = "h-1")
        )

        val plannerCsvBytes = ExternalProjectCodec.exportProject(
            project = project,
            headings = listOf(heading),
            tasks = exportTasks,
            format = ExternalExportFormat.PLANNER_CSV
        )
        val csvString = plannerCsvBytes.toString(Charsets.UTF_8)
        assertTrue("Planner CSV export must emit 'In progress'", csvString.contains("In progress"))

        val reimportedCsv = ExternalProjectCodec.importPlannerCsv(csvString, fallbackTitle = "Planner Sync")
        val csvTasksByTitle = reimportedCsv.phases.flatMap { it.tasks }.associateBy { it.title }
        assertEquals(true, csvTasksByTitle.getValue("Build UI").isInProgress)
        assertEquals(TaskStatus.OPEN, csvTasksByTitle.getValue("Build UI").status)
        assertEquals(false, csvTasksByTitle.getValue("Write Docs").isInProgress)
        assertEquals(false, csvTasksByTitle.getValue("Design Spec").isInProgress)
        assertEquals(TaskStatus.COMPLETED, csvTasksByTitle.getValue("Design Spec").status)

        val plannerXlsxBytes = ExternalProjectCodec.exportProject(
            project = project,
            headings = listOf(heading),
            tasks = exportTasks,
            format = ExternalExportFormat.PLANNER_XLSX
        )
        val reimportedXlsx = ExternalProjectCodec.importPlannerXlsx(plannerXlsxBytes, fallbackTitle = "Planner Sync")
        val xlsxTasksByTitle = reimportedXlsx.phases.flatMap { it.tasks }.associateBy { it.title }
        assertEquals(true, xlsxTasksByTitle.getValue("Build UI").isInProgress)
        assertEquals(false, xlsxTasksByTitle.getValue("Write Docs").isInProgress)
        assertEquals(false, xlsxTasksByTitle.getValue("Design Spec").isInProgress)

        // Multilingual MS Planner Progress values
        val multilingualCsv = """
            Task Name,Bucket Name,Progress
            Task EN Percent,Dev,50%
            Task DE,Dev,In Arbeit
            Task FR,Dev,En cours
            Task IT,Dev,In corso
            Task ES,Dev,En curso
            Task Done,Dev,100%
        """.trimIndent()
        val mlImported = ExternalProjectCodec.importPlannerCsv(multilingualCsv, fallbackTitle = "ML")
        val mlByTitle = mlImported.phases.flatMap { it.tasks }.associateBy { it.title }
        assertTrue(mlByTitle.getValue("Task EN Percent").isInProgress)
        assertTrue(mlByTitle.getValue("Task DE").isInProgress)
        assertTrue(mlByTitle.getValue("Task FR").isInProgress)
        assertTrue(mlByTitle.getValue("Task IT").isInProgress)
        assertTrue(mlByTitle.getValue("Task ES").isInProgress)
        assertFalse(mlByTitle.getValue("Task Done").isInProgress)
        assertEquals(TaskStatus.COMPLETED, mlByTitle.getValue("Task Done").status)
    }

    @Test
    fun `TaskFirestoreDto preserves local isInProgress when omittedInProgressField is true and clears on completion or purge`() {
        val localWipEntity = TaskEntity(
            id = "task-sync-wip",
            title = "Sync WIP",
            notes = "Local notes",
            status = TaskStatus.OPEN,
            scheduleState = ScheduleState.TODAY,
            sortOrder = 10.0,
            isInProgress = true,
            createdAt = 1_000L,
            updatedAt = 2_000L,
            isDeleted = false,
            isDirty = false
        )

        // Full roundtrip
        val dto = TaskFirestoreDto.fromEntity(localWipEntity)
        assertTrue(dto.isInProgress)
        val fsMap = dto.toFirestoreMap()
        assertEquals(true, fsMap["isInProgress"])
        val parsedDto = TaskFirestoreDto.fromFirestoreMap(fsMap)!!
        assertFalse(parsedDto.omittedInProgressField)
        assertTrue(parsedDto.toEntity(null).isInProgress)

        // Legacy client payload omitting "isInProgress" in Firestore map
        val legacyMap = mapOf(
            "id" to "task-sync-wip",
            "title" to "Sync WIP Edited On Legacy Device",
            "status" to "OPEN",
            "scheduleState" to "TODAY",
            "updatedAt" to 3_000L
        )
        val legacyFromMap = TaskFirestoreDto.fromFirestoreMap(legacyMap)!!
        assertTrue(legacyFromMap.omittedInProgressField)
        assertTrue(
            "Local isInProgress must be preserved when legacy map omits field",
            legacyFromMap.toEntity(localWipEntity).isInProgress
        )

        // Legacy client Completing task still clears isInProgress even when field omitted!
        val legacyCompletedMap = legacyMap + ("status" to "COMPLETED")
        val legacyCompletedDto = TaskFirestoreDto.fromFirestoreMap(legacyCompletedMap)!!
        assertFalse(
            "Completed status must always clear isInProgress to false",
            legacyCompletedDto.toEntity(localWipEntity).isInProgress
        )

        // CloudDeltaBatchPayload.decodeWithHints omittedInProgressIds preservation
        val deltaJson = """
            {
              "userId": "u1",
              "deviceId": "d-legacy",
              "batchTimestamp": 5000,
              "tasks": [
                {
                  "id": "task-sync-wip",
                  "title": "Legacy Ntfy Update",
                  "status": "OPEN",
                  "scheduleState": "TODAY",
                  "updatedAt": 5000
                },
                {
                  "id": "task-explicit-clear",
                  "title": "Modern Clear",
                  "status": "OPEN",
                  "scheduleState": "TODAY",
                  "isInProgress": false,
                  "updatedAt": 5000
                }
              ]
            }
        """.trimIndent()
        val batch = CloudDeltaBatchPayload.decodeWithHints(deltaJson)
        val legacyDeltaDto = batch.tasks.first { it.id == "task-sync-wip" }
        val explicitClearDto = batch.tasks.first { it.id == "task-explicit-clear" }
        assertTrue(legacyDeltaDto.omittedInProgressField)
        assertTrue(legacyDeltaDto.toEntity(localWipEntity).isInProgress)
        assertFalse(explicitClearDto.omittedInProgressField)
        assertFalse(explicitClearDto.toEntity(localWipEntity.copy(id = "task-explicit-clear")).isInProgress)

        // Purged tombstone clears isInProgress
        val purgedEntity = localWipEntity.copy(title = "", notes = "", isDeleted = true)
        assertFalse(TaskFirestoreDto.fromEntity(purgedEntity).isInProgress)
        assertEquals(false, TaskFirestoreDto.fromEntity(purgedEntity).toFirestoreMap()["isInProgress"])

        // Recurring deduplication enriches winner with donor isInProgress
        val recRule = """{"unit":"DAYS","interval":1,"mode":"FIXED_SCHEDULE"}"""
        val winner = localWipEntity.copy(
            id = "rec-1",
            title = "Daily Standup",
            recurrenceRuleJson = recRule,
            whenDate = 1_760_000_000_000L,
            isInProgress = false,
            updatedAt = 5_000L
        )
        val donorLoser = winner.copy(
            id = "rec-2",
            isInProgress = true,
            updatedAt = 4_000L
        )
        val dedupPlan = SyncRepository.planRecurringDeduplication(listOf(winner, donorLoser))
        assertEquals(listOf("rec-2"), dedupPlan.loserIds)
        assertEquals(true, dedupPlan.winnerEnrichments.single().isInProgress)
    }

    @Test
    fun `Room v9 MIGRATION_8_9, v3_3_0 Build 119, all 6 v33 localization keys, and UI contracts are wired cleanly`() {
        // 1. Execute AppModule.MIGRATION_8_9 against tracking SQLite proxy (missing column vs already present)
        val sqlWhenMissing = mutableListOf<String>()
        val dbMissingCol = createPragmaTrackingDb(
            existingColumns = setOf("id", "title", "status", "isPinned", "isMilestone", "actualMinutes"),
            executedSqlOut = sqlWhenMissing
        )
        AppModule.MIGRATION_8_9.migrate(dbMissingCol)
        assertEquals(
            listOf("ALTER TABLE `tasks` ADD COLUMN `isInProgress` INTEGER NOT NULL DEFAULT 0"),
            sqlWhenMissing
        )

        val sqlWhenPresent = mutableListOf<String>()
        val dbWithCol = createPragmaTrackingDb(
            existingColumns = setOf("id", "title", "status", "isInProgress"),
            executedSqlOut = sqlWhenPresent
        )
        AppModule.MIGRATION_8_9.migrate(dbWithCol)
        assertTrue("MIGRATION_8_9 must be idempotent when column already exists", sqlWhenPresent.isEmpty())

        // 2. Verify Room version = 9, build.gradle.kts version 3.3.0 (119), and key UI wiring
        val appRoot = sequenceOf(File("."), File("app")).first { File(it, "src/main").isDirectory }
        val appSrcMain = File(appRoot, "src/main")
        val buildGradleKt = File(appRoot, "build.gradle.kts").readText()
        assertTrue(buildGradleKt.contains("versionCode = 120"))
        assertTrue(buildGradleKt.contains("versionName = \"3.3.0-beta.2\""))

        val dbKt = File(appSrcMain, "java/com/pv/thingsbook/data/local/ThingsDatabase.kt").readText()
        val appModuleKt = File(appSrcMain, "java/com/pv/thingsbook/di/AppModule.kt").readText()
        val checkboxKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ThingsCheckbox.kt").readText()
        val ringKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ProjectProgressRing.kt").readText()
        val cardKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ExpandableTaskCard.kt").readText()
        val inspectorKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/WorkspaceInspectorPanel.kt").readText()
        val perspModalsKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ThingsV3PerspectivesAndDependenciesModals.kt").readText()
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()
        val viewModelKt = File(appSrcMain, "java/com/pv/thingsbook/ui/viewmodel/ThingsbookViewModel.kt").readText()

        assertTrue(dbKt.contains("version = 9"))
        assertTrue(appModuleKt.contains("MIGRATION_8_9 = object : Migration(8, 9)"))
        assertTrue(appModuleKt.contains("MIGRATION_7_8, MIGRATION_8_9"))
        assertTrue(checkboxKt.contains("isInProgress: Boolean = false"))
        assertTrue(checkboxKt.contains("onToggleInProgress: (() -> Unit)? = null"))
        assertTrue(ringKt.contains("inProgressFraction: Float = 0f"))
        assertTrue(ringKt.contains("colors.somedayWarmAmber.copy(alpha = 0.72f)"))
        assertTrue(cardKt.contains("R.string.v33_in_progress_badge"))
        assertTrue(cardKt.contains("R.string.v33_mark_in_progress"))
        assertTrue(cardKt.contains("R.string.v33_clear_in_progress"))
        assertTrue(inspectorKt.contains("R.string.v33_in_progress_badge"))
        assertTrue(perspModalsKt.contains("R.string.v33_filter_in_progress_only"))
        assertTrue(smartViewKt.contains("R.string.v33_cmd_toggle_in_progress"))
        assertTrue(smartViewKt.contains("R.string.v33_project_in_progress_count"))
        assertTrue(viewModelKt.contains("repository.setTaskInProgress(taskId, true)"))

        // 3. Verify all 6 v33_* localization keys exist and are non-blank in all 5 strings.xml files
        val resRoot = File(appSrcMain, "res")
        val requiredKeys = listOf(
            "v33_in_progress_badge",
            "v33_mark_in_progress",
            "v33_clear_in_progress",
            "v33_filter_in_progress_only",
            "v33_project_in_progress_count",
            "v33_cmd_toggle_in_progress"
        )
        val folders = listOf("values", "values-de", "values-fr", "values-it", "values-es")
        for (folder in folders) {
            val xmlFile = File(resRoot, "$folder/strings.xml")
            assertTrue("Missing $folder/strings.xml", xmlFile.isFile)
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
            val nodes = doc.getElementsByTagName("string")
            val map = HashMap<String, String>()
            for (i in 0 until nodes.length) {
                val item = nodes.item(i)
                val name = item.attributes?.getNamedItem("name")?.nodeValue ?: continue
                map[name] = item.textContent.orEmpty()
            }
            for (key in requiredKeys) {
                assertNotNull("Missing $key in $folder/strings.xml", map[key])
                assertTrue("Blank $key in $folder/strings.xml", map[key]!!.isNotBlank())
            }
        }
    }

    @Test
    fun `v3_3_0_beta_2 Build 120 WorkspaceInspectorPanel achieves 1_to_1 feature parity with ExpandedTaskCardBody`() {
        val appRoot = sequenceOf(File("."), File("app")).first { File(it, "src/main").isDirectory }
        val appSrcMain = File(appRoot, "src/main")

        val cardKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/ExpandableTaskCard.kt").readText()
        val inspectorKt = File(appSrcMain, "java/com/pv/thingsbook/ui/components/WorkspaceInspectorPanel.kt").readText()
        val smartViewKt = File(appSrcMain, "java/com/pv/thingsbook/ui/screens/ThingsSmartViewScreen.kt").readText()

        // 1. Shared helpers exposed as internal in ExpandableTaskCard.kt with zero duplication
        assertTrue(cardKt.contains("internal fun ThingsCustomDurationDialog("))
        assertTrue(cardKt.contains("internal fun formatTaskMarkdownForClipboard("))

        // 2. SelectedTaskInspectorContent wires all reusable dialogs, rich editors, AI, voice, and attachments directly
        val selectedInspectorChunk = inspectorKt
            .substringAfter("private fun SelectedTaskInspectorContent(")
            .substringBefore("private fun InspectorEditableChecklistRow(")

        val requiredInspectorComponents = listOf(
            "TaskDependenciesPickerDialog(",
            "ThingsRepeatDialog(",
            "ThingsTimeblockPickerDialog(",
            "DeadlineDatePickerDialog(",
            "WhenNaturalDateDialog(",
            "MoveTaskToProjectOrAreaDialog(",
            "TaskTagPickerDialog(",
            "ThingsCustomDurationDialog(",
            "TaskAttachmentsStrip(",
            "SmartSyntaxPreviewChipsRow(",
            "rememberThingsMarkdownVisualTransformation()",
            "onTriggerGeminiMagicBreakdown",
            "formatTaskMarkdownForClipboard(",
            "RecognizerIntent.ACTION_RECOGNIZE_SPEECH",
            "ActivityResultContracts.OpenDocument()",
            "ActivityResultContracts.TakePicturePreview()",
            "isTaskActivelyBlocked("
        )
        for (token in requiredInspectorComponents) {
            assertTrue("Missing $token in SelectedTaskInspectorContent", selectedInspectorChunk.contains(token))
        }

        // Only the Header Bar's Open Full Modal button invokes onExpandTask inside SelectedTaskInspectorContent
        assertEquals(
            "Badges in SelectedTaskInspectorContent must open inline dialogs instead of bouncing to onExpandTask",
            1,
            Regex("""onExpandTask\(""").findAll(selectedInspectorChunk).count()
        )

        // 3. ThingsSmartViewScreen wires all state flows and callbacks into WorkspaceInspectorPanel
        val inspectorCallSiteChunk = smartViewKt
            .substringAfter("val showInspectorPane = canFitInspector && isInspectorVisible")
            .substringBefore("// App Version Badge at bottom-left")

        val requiredCallSiteTokens = listOf(
            "viewModel.tagSortMode.collectAsState()",
            "viewModel.tagUsageCounts.collectAsState()",
            "viewModel.magicBreakdownTaskId.collectAsState()",
            "allHeadings = allHeadingsTop",
            "allTags = allTags",
            "openTaskIds = openTaskIds",
            "onUpdateTaskScheduleWithReminder =",
            "onUpdateTaskDeadline =",
            "onUpdateTaskRecurrence =",
            "onUpdateTimeblock =",
            "onUpdateBlockedBy =",
            "onMoveTaskToContainer =",
            "onUpdateTaskTags =",
            "onCreateAndAssignTag =",
            "onOpenFullTagManager =",
            "onInsertChecklistItemAfter =",
            "onRenameChecklistItem =",
            "onMoveChecklistItem =",
            "onTriggerGeminiMagicBreakdown =",
            "onApplySmartSyntax =",
            "onAddTaskAttachment =",
            "onDeleteAttachment =",
            "onDuplicateTask =",
            "onDeleteTask ="
        )
        for (token in requiredCallSiteTokens) {
            assertTrue("Missing $token at WorkspaceInspectorPanel call site", inspectorCallSiteChunk.contains(token))
        }
    }
}
