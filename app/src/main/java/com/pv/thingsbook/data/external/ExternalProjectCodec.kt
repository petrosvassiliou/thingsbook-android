package com.pv.thingsbook.data.external

import com.pv.thingsbook.domain.model.EffortLevel
import com.pv.thingsbook.domain.model.Heading
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.TaskItem
import com.pv.thingsbook.domain.model.TaskStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.floor

enum class ExternalExportFormat(
    val fileExtension: String,
    val mimeType: String
) {
    ASANA_CSV("csv", "text/csv"),
    ASANA_JSON("json", "application/json"),
    PLANNER_XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PLANNER_CSV("csv", "text/csv")
}

data class ExternalImportedChecklistItem(
    val title: String,
    val isCompleted: Boolean = false
)

data class ExternalImportedTask(
    val title: String,
    val notes: String = "",
    val status: TaskStatus = TaskStatus.OPEN,
    val completedAt: Long? = null,
    val whenDate: Long? = null,
    val deadline: Long? = null,
    val isPinned: Boolean = false,
    val effortLevel: String? = null,
    val tagNames: List<String> = emptyList(),
    val checklistItems: List<ExternalImportedChecklistItem> = emptyList(),
    val assigneeId: String? = null
)

data class ExternalImportedPhase(
    val title: String,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val tasks: List<ExternalImportedTask> = emptyList()
)

data class ExternalImportedProject(
    val title: String,
    val notes: String = "",
    val ungroupedTasks: List<ExternalImportedTask> = emptyList(),
    val phases: List<ExternalImportedPhase> = emptyList(),
    val tagNames: List<String> = emptyList()
) {
    val totalTaskCount: Int
        get() = ungroupedTasks.size + phases.sumOf { it.tasks.size }
}

object ExternalProjectCodec {

    private const val MAX_ZIP_ENTRIES = 512
    private const val MAX_SINGLE_ZIP_ENTRY_BYTES = 24 * 1024 * 1024
    private const val MAX_TOTAL_ZIP_BYTES = 48 * 1024 * 1024

    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val ASANA_CSV_HEADERS = listOf(
        "Task ID",
        "Created At",
        "Completed At",
        "Last Modified",
        "Name",
        "Section/Column",
        "Assignee",
        "Start Date",
        "Due Date",
        "Tags",
        "Notes",
        "Projects",
        "Parent task"
    )

    private val PLANNER_COLUMNS = listOf(
        "Task ID",
        "Task Name",
        "Bucket Name",
        "Progress",
        "Priority",
        "Assigned To",
        "Created By",
        "Created Date",
        "Start Date",
        "Due Date",
        "Late",
        "Completed Date",
        "Description",
        "Completed Checklist Items",
        "Checklist Items",
        "Labels"
    )

    private val IGNORED_ASANA_SECTIONS = setOf(
        "untitled section",
        "(no section)",
        "no section",
        "unbenannter abschnitt",
        "section sans titre",
        "sezione senza titolo",
        "sección sin título"
    )

    private val GENERIC_WORKBOOK_SHEET_NAMES = setOf(
        "tasks",
        "sheet1",
        "sheet 1",
        "tabelle1",
        "feuil1",
        "foglio1",
        "hoja1"
    )

    private val COMPLETED_PROGRESS_VALUES = setOf(
        "completed",
        "complete",
        "100%",
        "100",
        "erledigt",
        "abgeschlossen",
        "terminé",
        "termine",
        "completato",
        "completado"
    )

    private val RATIO_CHECKLIST_REGEX = Regex("""^(\d+)\s*/\s*(\d+)$""")

    fun buildSuggestedExportFileName(
        projectTitle: String,
        format: ExternalExportFormat
    ): String {
        val sanitizedBase = projectTitle.trim()
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]+"""), "-")
            .replace(Regex("""\s+"""), "-")
            .trim('-', '.', ' ')
            .take(64)
            .ifBlank { "thingsbook-project" }
        val suffix = when (format) {
            ExternalExportFormat.ASANA_CSV -> "asana.csv"
            ExternalExportFormat.ASANA_JSON -> "asana.json"
            ExternalExportFormat.PLANNER_XLSX -> "planner.xlsx"
            ExternalExportFormat.PLANNER_CSV -> "planner.csv"
        }
        return "$sanitizedBase-$suffix"
    }

    fun exportProject(
        project: Project,
        headings: List<Heading>,
        tasks: List<TaskItem>,
        projectTags: List<Tag> = emptyList(),
        format: ExternalExportFormat,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ByteArray = when (format) {
        ExternalExportFormat.ASANA_CSV -> exportAsanaCsv(project, headings, tasks, projectTags, zoneId)
        ExternalExportFormat.ASANA_JSON -> exportAsanaJson(project, headings, tasks, projectTags, zoneId)
        ExternalExportFormat.PLANNER_XLSX -> exportPlannerXlsx(project, headings, tasks, projectTags, zoneId)
        ExternalExportFormat.PLANNER_CSV -> exportPlannerCsv(project, headings, tasks, projectTags, zoneId)
    }

    // =========================================================================
    // Auto-Detection Entry Point
    // =========================================================================

    fun importAutoDetect(
        bytes: ByteArray,
        fileNameHint: String? = null,
        fallbackTitle: String = "Imported Project",
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ExternalImportedProject {
        val fileStem = extractFileNameStem(fileNameHint)
        val effectiveFallback = fileStem?.takeIf { it.isNotBlank() }
            ?: fallbackTitle.trim().ifBlank { "Imported Project" }

        if (isZipArchive(bytes)) {
            return importPlannerXlsx(
                bytes = bytes,
                fallbackTitle = effectiveFallback,
                zoneId = zoneId
            )
        }

        val text = decodeTextBytes(bytes)
        val trimmedLeading = text.trimStart()
        if (trimmedLeading.startsWith("{") || trimmedLeading.startsWith("[")) {
            return importAsanaJson(
                jsonText = text,
                fallbackTitle = effectiveFallback,
                zoneId = zoneId
            )
        }

        return if (looksLikePlannerCsv(text, fileNameHint)) {
            importPlannerCsv(
                csvText = text,
                fallbackTitle = effectiveFallback,
                zoneId = zoneId
            )
        } else {
            importAsanaCsv(
                csvText = text,
                fallbackTitle = effectiveFallback,
                zoneId = zoneId
            )
        }
    }

    private fun isZipArchive(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte())
    }

    private fun decodeTextBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() ->
                String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
            bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
            bytes.size >= 2 &&
                bytes[0] == 0xFE.toByte() &&
                bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
            else -> String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")
        }
    }

    private fun looksLikePlannerCsv(csvText: String, fileNameHint: String?): Boolean {
        val clean = csvText.removePrefix("\uFEFF").trim()
        if (clean.isEmpty()) {
            return fileNameHint?.lowercase(Locale.ROOT)?.contains("planner") == true
        }
        val delimiter = detectCsvDelimiter(clean)
        val firstRow = parseCsvRows(clean, delimiter).firstOrNull().orEmpty()
        val lowerHeaders = firstRow.map { it.trim().lowercase(Locale.ROOT) }.toSet()

        val hasStrongPlanner = lowerHeaders.any { h ->
            h in setOf(
                "bucket name",
                "bucket",
                "checklist items",
                "completed checklist items",
                "labels",
                "nom du compartiment",
                "nome bucket",
                "nombre del depósito"
            )
        }
        val hasStrongAsana = lowerHeaders.any { h ->
            h in setOf("section/column", "parent task", "projects")
        }
        if (hasStrongPlanner && !hasStrongAsana) return true
        if (hasStrongAsana && !hasStrongPlanner) return false
        if ("task name" in lowerHeaders && "progress" in lowerHeaders) return true
        return false
    }

    // =========================================================================
    // 1. Asana CSV Export & Import
    // =========================================================================

    fun exportAsanaCsv(
        project: Project,
        headings: List<Heading>,
        tasks: List<TaskItem>,
        projectTags: List<Tag> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ByteArray = exportAsanaCsvString(project, headings, tasks, projectTags, zoneId)
        .toByteArray(StandardCharsets.UTF_8)

    fun exportAsanaCsvString(
        project: Project,
        headings: List<Heading>,
        tasks: List<TaskItem>,
        @Suppress("UNUSED_PARAMETER") projectTags: List<Tag> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val activeHeadings = headings
            .filter { !it.isDeleted && !it.isArchived }
            .sortedBy { it.sortOrder }
        val headingIds = activeHeadings.mapTo(HashSet()) { it.id }
        val exportableTasks = tasks.filter { !it.isDeleted && it.status != TaskStatus.CANCELED }

        val titleCounts = exportableTasks
            .groupingBy { it.title.trim().lowercase(Locale.ROOT) }
            .eachCount()

        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine(ASANA_CSV_HEADERS.joinToString(",") { escapeCsvField(it) })

        fun emitTaskAndSubtasks(task: TaskItem, sectionTitle: String) {
            val createdIso = formatIsoInstant(task.createdAt)
            val completedIso = if (task.status == TaskStatus.COMPLETED) {
                formatIsoInstant(task.completedAt ?: task.updatedAt)
            } else {
                ""
            }
            val updatedIso = formatIsoInstant(task.updatedAt)
            val startStr = task.whenDate?.let { formatIsoDate(it, zoneId) }.orEmpty()
            val dueStr = task.deadline?.let { formatIsoDate(it, zoneId) }.orEmpty()
            val tagsStr = task.tags
                .filter { !it.isDeleted && it.title.isNotBlank() }
                .joinToString(", ") { it.title.trim() }

            val taskRow = listOf(
                task.id,
                createdIso,
                completedIso,
                updatedIso,
                task.title,
                sectionTitle,
                task.assigneeId.orEmpty(),
                startStr,
                dueStr,
                tagsStr,
                task.notes,
                project.title,
                ""
            )
            sb.appendLine(taskRow.joinToString(",") { escapeCsvField(it) })

            val cleanTitleKey = task.title.trim().lowercase(Locale.ROOT)
            val parentRef = if (cleanTitleKey.isNotEmpty() && (titleCounts[cleanTitleKey] ?: 0) == 1) {
                task.title.trim()
            } else {
                task.id
            }

            val subtasks = task.checklistItems
                .filter { !it.isDeleted && it.title.isNotBlank() }
                .sortedBy { it.sortOrder }

            for (sub in subtasks) {
                val subCompletedIso = if (sub.isCompleted) formatIsoInstant(sub.updatedAt) else ""
                val subRow = listOf(
                    sub.id,
                    formatIsoInstant(sub.createdAt),
                    subCompletedIso,
                    formatIsoInstant(sub.updatedAt),
                    sub.title,
                    sectionTitle,
                    "",
                    "",
                    "",
                    "",
                    "",
                    project.title,
                    parentRef
                )
                sb.appendLine(subRow.joinToString(",") { escapeCsvField(it) })
            }
        }

        val ungrouped = exportableTasks
            .filter { it.headingId == null || it.headingId !in headingIds }
            .sortedBy { it.sortOrder }
        for (task in ungrouped) {
            emitTaskAndSubtasks(task, sectionTitle = "")
        }

        for (heading in activeHeadings) {
            val sectionTasks = exportableTasks
                .filter { it.headingId == heading.id }
                .sortedBy { it.sortOrder }
            if (sectionTasks.isEmpty()) {
                val headerMarker = heading.title.trim().trimEnd(':') + ":"
                val emptySectionRow = listOf(
                    heading.id,
                    formatIsoInstant(heading.createdAt),
                    "",
                    formatIsoInstant(heading.updatedAt),
                    headerMarker,
                    heading.title.trim(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    project.title,
                    ""
                )
                sb.appendLine(emptySectionRow.joinToString(",") { escapeCsvField(it) })
            } else {
                for (task in sectionTasks) {
                    emitTaskAndSubtasks(task, sectionTitle = heading.title.trim())
                }
            }
        }

        return sb.toString()
    }

    fun importAsanaCsv(
        csvText: String,
        fallbackTitle: String = "Imported Project",
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ExternalImportedProject {
        val cleaned = csvText.removePrefix("\uFEFF").trim()
        val safeFallback = fallbackTitle.trim().ifBlank { "Imported Project" }
        if (cleaned.isEmpty()) {
            return ExternalImportedProject(title = safeFallback)
        }

        val delimiter = detectCsvDelimiter(cleaned)
        val rows = parseCsvRows(cleaned, delimiter)
        if (rows.isEmpty()) {
            return ExternalImportedProject(title = safeFallback)
        }

        val rawHeaders = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        val hasHeader = rawHeaders.any { h ->
            h in setOf("name", "task id", "section/column", "due date", "parent task", "title", "task name")
        }
        val dataRows = if (hasHeader) rows.drop(1) else rows

        fun findCol(vararg candidates: String, defaultIdx: Int = -1): Int {
            if (!hasHeader) return defaultIdx
            return rawHeaders.indexOfFirst { h -> candidates.any { c -> c == h } }
        }

        val idxTaskId = findCol("task id", "id", "gid", defaultIdx = 0)
        val idxCompletedAt = findCol("completed at", "completed date", defaultIdx = 2)
        val idxName = findCol("name", "task name", "title", defaultIdx = 4).let { if (it >= 0) it else 0 }
        val idxSection = findCol("section/column", "section", "column", defaultIdx = 5)
        val idxAssignee = findCol("assignee", "assignee email", "assigned to", defaultIdx = 6)
        val idxStartDate = findCol("start date", defaultIdx = 7)
        val idxDueDate = findCol("due date", "deadline", defaultIdx = 8)
        val idxTags = findCol("tags", "categories", defaultIdx = 9)
        val idxNotes = findCol("notes", "description", defaultIdx = 10)
        val idxProjects = findCol("projects", "project", defaultIdx = 11)
        val idxParent = findCol("parent task", "parent", "parent id", defaultIdx = 12)

        class MutableTaskEntry(
            val taskId: String,
            val title: String,
            val notes: String,
            val status: TaskStatus,
            val completedAt: Long?,
            val whenDate: Long?,
            val deadline: Long?,
            val tagNames: List<String>,
            val assigneeId: String?,
            val sectionName: String?,
            val parentRef: String,
            val checklist: MutableList<ExternalImportedChecklistItem> = mutableListOf()
        ) {
            fun toDomain(): ExternalImportedTask = ExternalImportedTask(
                title = title,
                notes = notes,
                status = status,
                completedAt = completedAt,
                whenDate = whenDate,
                deadline = deadline,
                tagNames = tagNames,
                checklistItems = checklist.toList(),
                assigneeId = assigneeId
            )
        }

        val orderedPhaseNames = LinkedHashSet<String>()
        val phaseTitleByLower = LinkedHashMap<String, String>()
        fun ensurePhase(rawSection: String): String? {
            val normalized = normalizeAsanaSectionName(rawSection) ?: return null
            val lower = normalized.lowercase(Locale.ROOT)
            val existing = phaseTitleByLower[lower]
            if (existing != null) return existing
            phaseTitleByLower[lower] = normalized
            orderedPhaseNames.add(normalized)
            return normalized
        }

        var detectedProjectTitle: String? = null
        var activeHeaderSection: String? = null
        val allParsedRows = mutableListOf<MutableTaskEntry>()

        // Pass 1: parse all rows, register section header rows and candidate tasks
        for (cols in dataRows) {
            fun col(idx: Int): String = if (idx >= 0) cols.getOrNull(idx)?.trim().orEmpty() else ""

            val rawName = col(idxName)
            if (rawName.isEmpty()) continue

            val rawTaskId = col(idxTaskId)
            val rawCompletedAt = col(idxCompletedAt)
            val rawSection = col(idxSection)
            val rawAssignee = col(idxAssignee)
            val rawStart = col(idxStartDate)
            val rawDue = col(idxDueDate)
            val rawTags = col(idxTags)
            val rawNotes = col(idxNotes)
            val rawProject = col(idxProjects)
            val rawParent = col(idxParent)

            if (detectedProjectTitle == null && rawProject.isNotEmpty()) {
                detectedProjectTitle = rawProject
            }

            // Asana section header rows end with ':' and have no parent, due date, start date, or notes
            if (rawName.endsWith(":") &&
                rawParent.isEmpty() &&
                rawDue.isEmpty() &&
                rawStart.isEmpty() &&
                rawNotes.isEmpty() &&
                rawName.length > 1
            ) {
                val headerPhase = ensurePhase(rawName.removeSuffix(":").trim())
                activeHeaderSection = headerPhase
                continue
            }

            val explicitSectionIgnored = rawSection.isNotEmpty() && normalizeAsanaSectionName(rawSection) == null
            if (explicitSectionIgnored) {
                activeHeaderSection = null
            }
            val resolvedSection = when {
                rawSection.isNotEmpty() -> ensurePhase(rawSection)
                else -> activeHeaderSection
            }

            val isCompleted = rawCompletedAt.isNotEmpty()
            val status = if (isCompleted) TaskStatus.COMPLETED else TaskStatus.OPEN
            val completedAtMillis = if (isCompleted) {
                parseExternalTimestamp(rawCompletedAt, zoneId) ?: System.currentTimeMillis()
            } else {
                null
            }

            val tagList = splitTagTokens(rawTags)
            val entry = MutableTaskEntry(
                taskId = rawTaskId,
                title = rawName,
                notes = rawNotes,
                status = status,
                completedAt = completedAtMillis,
                whenDate = parseExternalDate(rawStart, zoneId),
                deadline = parseExternalDate(rawDue, zoneId),
                tagNames = tagList,
                assigneeId = rawAssignee.takeIf { it.isNotEmpty() },
                sectionName = resolvedSection,
                parentRef = rawParent
            )
            allParsedRows.add(entry)
        }

        // Index top-level tasks first, then fallback to any row (2-pass resolution independent of row order)
        val parentById = LinkedHashMap<String, MutableTaskEntry>()
        val parentByName = LinkedHashMap<String, MutableTaskEntry>()
        for (entry in allParsedRows) {
            if (entry.parentRef.isEmpty()) {
                if (entry.taskId.isNotEmpty()) {
                    parentById.putIfAbsent(entry.taskId.lowercase(Locale.ROOT), entry)
                }
                parentByName.putIfAbsent(entry.title.lowercase(Locale.ROOT), entry)
            }
        }
        for (entry in allParsedRows) {
            if (entry.taskId.isNotEmpty()) {
                parentById.putIfAbsent(entry.taskId.lowercase(Locale.ROOT), entry)
            }
            parentByName.putIfAbsent(entry.title.lowercase(Locale.ROOT), entry)
        }

        // Pass 2: attach subtasks to parents or keep orphans as top-level tasks
        val topLevelEntries = mutableListOf<MutableTaskEntry>()
        for (entry in allParsedRows) {
            if (entry.parentRef.isEmpty()) {
                topLevelEntries.add(entry)
            } else {
                val refKey = entry.parentRef.lowercase(Locale.ROOT)
                val matchedParent = (parentById[refKey] ?: parentByName[refKey])
                    ?.takeIf { it !== entry }
                if (matchedParent != null) {
                    matchedParent.checklist.add(
                        ExternalImportedChecklistItem(
                            title = entry.title,
                            isCompleted = entry.status == TaskStatus.COMPLETED
                        )
                    )
                } else {
                    topLevelEntries.add(entry)
                }
            }
        }

        val ungroupedTasks = mutableListOf<ExternalImportedTask>()
        val tasksByPhase = LinkedHashMap<String, MutableList<ExternalImportedTask>>()
        for (phaseName in orderedPhaseNames) {
            tasksByPhase[phaseName] = mutableListOf()
        }

        for (entry in topLevelEntries) {
            val built = entry.toDomain()
            val sec = entry.sectionName
            if (sec != null) {
                tasksByPhase.getOrPut(sec) { mutableListOf() }.add(built)
            } else {
                ungroupedTasks.add(built)
            }
        }

        val phases = tasksByPhase.map { (phaseTitle, phaseTasks) ->
            ExternalImportedPhase(
                title = phaseTitle,
                tasks = phaseTasks
            )
        }

        return ExternalImportedProject(
            title = detectedProjectTitle?.takeIf { it.isNotBlank() } ?: safeFallback,
            ungroupedTasks = ungroupedTasks,
            phases = phases
        )
    }

    private fun normalizeAsanaSectionName(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.lowercase(Locale.ROOT) in IGNORED_ASANA_SECTIONS) return null
        return trimmed
    }

    // =========================================================================
    // 2. Asana JSON Export & Import
    // =========================================================================

    fun exportAsanaJson(
        project: Project,
        headings: List<Heading>,
        tasks: List<TaskItem>,
        projectTags: List<Tag> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ByteArray = exportAsanaJsonString(project, headings, tasks, projectTags, zoneId)
        .toByteArray(StandardCharsets.UTF_8)

    fun exportAsanaJsonString(
        project: Project,
        headings: List<Heading>,
        tasks: List<TaskItem>,
        projectTags: List<Tag> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val activeHeadings = headings
            .filter { !it.isDeleted && !it.isArchived }
            .sortedBy { it.sortOrder }
        val headingById = activeHeadings.associateBy { it.id }
        val exportableTasks = tasks.filter { !it.isDeleted && it.status != TaskStatus.CANCELED }

        fun buildAsanaTaskObject(task: TaskItem, heading: Heading?): JsonObject = buildJsonObject {
            put("gid", JsonPrimitive(task.id))
            put("resource_type", JsonPrimitive("task"))
            put("name", JsonPrimitive(task.title))
            put("notes", JsonPrimitive(task.notes))
            val isCompleted = task.status == TaskStatus.COMPLETED
            put("completed", JsonPrimitive(isCompleted))
            put(
                "completed_at",
                if (isCompleted) JsonPrimitive(formatIsoInstant(task.completedAt ?: task.updatedAt)) else JsonNull
            )
            put("start_on", task.whenDate?.let { JsonPrimitive(formatIsoDate(it, zoneId)) } ?: JsonNull)
            put("due_on", task.deadline?.let { JsonPrimitive(formatIsoDate(it, zoneId)) } ?: JsonNull)
            put("is_pinned", JsonPrimitive(task.isPinned))
            put("effort_level", task.effortLevel?.let { JsonPrimitive(it) } ?: JsonNull)
            put(
                "assignee",
                if (!task.assigneeId.isNullOrBlank()) {
                    buildJsonObject {
                        put("gid", JsonPrimitive(task.assigneeId))
                        put("name", JsonPrimitive(task.assigneeId))
                    }
                } else {
                    JsonNull
                }
            )
            put(
                "tags",
                buildJsonArray {
                    task.tags
                        .filter { !it.isDeleted && it.title.isNotBlank() }
                        .forEach { tg ->
                            add(
                                buildJsonObject {
                                    put("gid", JsonPrimitive(tg.id))
                                    put("name", JsonPrimitive(tg.title))
                                }
                            )
                        }
                }
            )
            put(
                "memberships",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put(
                                "project",
                                buildJsonObject {
                                    put("gid", JsonPrimitive(project.id))
                                    put("name", JsonPrimitive(project.title))
                                }
                            )
                            if (heading != null) {
                                put(
                                    "section",
                                    buildJsonObject {
                                        put("gid", JsonPrimitive(heading.id))
                                        put("name", JsonPrimitive(heading.title))
                                    }
                                )
                            } else {
                                put("section", JsonNull)
                            }
                        }
                    )
                }
            )
            put(
                "subtasks",
                buildJsonArray {
                    task.checklistItems
                        .filter { !it.isDeleted && it.title.isNotBlank() }
                        .sortedBy { it.sortOrder }
                        .forEach { sub ->
                            add(
                                buildJsonObject {
                                    put("gid", JsonPrimitive(sub.id))
                                    put("resource_type", JsonPrimitive("task"))
                                    put("name", JsonPrimitive(sub.title))
                                    put("completed", JsonPrimitive(sub.isCompleted))
                                }
                            )
                        }
                }
            )
        }

        val ungroupedTasks = exportableTasks
            .filter { it.headingId == null || it.headingId !in headingById }
            .sortedBy { it.sortOrder }

        val allDataObjects = mutableListOf<JsonObject>()
        val ungroupedJsonObjects = ungroupedTasks.map { t ->
            buildAsanaTaskObject(t, heading = null).also { allDataObjects.add(it) }
        }

        val sectionsJsonArray = buildJsonArray {
            for (heading in activeHeadings) {
                val sectionTasks = exportableTasks
                    .filter { it.headingId == heading.id }
                    .sortedBy { it.sortOrder }
                val sectionTaskObjects = sectionTasks.map { t ->
                    buildAsanaTaskObject(t, heading = heading).also { allDataObjects.add(it) }
                }
                add(
                    buildJsonObject {
                        put("gid", JsonPrimitive(heading.id))
                        put("resource_type", JsonPrimitive("section"))
                        put("name", JsonPrimitive(heading.title))
                        put("start_on", heading.startDate?.let { JsonPrimitive(formatIsoDate(it, zoneId)) } ?: JsonNull)
                        put("due_on", heading.endDate?.let { JsonPrimitive(formatIsoDate(it, zoneId)) } ?: JsonNull)
                        put("tasks", JsonArray(sectionTaskObjects))
                    }
                )
            }
        }

        val root = buildJsonObject {
            put(
                "project",
                buildJsonObject {
                    put("gid", JsonPrimitive(project.id))
                    put("resource_type", JsonPrimitive("project"))
                    put("name", JsonPrimitive(project.title))
                    put("notes", JsonPrimitive(project.notes))
                    put("due_on", project.deadline?.let { JsonPrimitive(formatIsoDate(it, zoneId)) } ?: JsonNull)
                    put(
                        "tags",
                        buildJsonArray {
                            projectTags
                                .filter { !it.isDeleted && it.title.isNotBlank() }
                                .forEach { tg ->
                                    add(
                                        buildJsonObject {
                                            put("gid", JsonPrimitive(tg.id))
                                            put("name", JsonPrimitive(tg.title))
                                        }
                                    )
                                }
                        }
                    )
                }
            )
            put("sections", sectionsJsonArray)
            put("tasks", JsonArray(ungroupedJsonObjects))
            put("data", JsonArray(allDataObjects))
        }

        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }

    fun importAsanaJson(
        jsonText: String,
        fallbackTitle: String = "Imported Project",
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ExternalImportedProject {
        val safeFallback = fallbackTitle.trim().ifBlank { "Imported Project" }
        val cleaned = jsonText.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) {
            return ExternalImportedProject(title = safeFallback)
        }

        val rootElement = lenientJson.parseToJsonElement(cleaned)
        if (rootElement is JsonArray) {
            return parseAsanaDataArrayProject(
                dataArray = rootElement,
                explicitProjectTitle = null,
                explicitProjectNotes = "",
                explicitProjectTags = emptyList(),
                predefinedSections = emptyList(),
                fallbackTitle = safeFallback,
                zoneId = zoneId
            )
        }

        val rootObj = rootElement as? JsonObject
            ?: return ExternalImportedProject(title = safeFallback)

        // Unwrap {"data": { "project": ..., "sections": ..., "tasks": ... }} if nested under object "data"
        val innerDataObj = rootObj["data"] as? JsonObject
        val effectiveRoot = if (innerDataObj != null &&
            ("tasks" in innerDataObj || "sections" in innerDataObj || "name" in innerDataObj)
        ) {
            innerDataObj
        } else {
            rootObj
        }

        val projectObj = (effectiveRoot["project"] as? JsonObject)
            ?: (rootObj["project"] as? JsonObject)

        val explicitProjTitle = projectObj?.stringProp("name", "title")
            ?: effectiveRoot.stringProp("name", "title")
        val explicitProjNotes = projectObj?.stringProp("notes", "description")
            ?: effectiveRoot.stringProp("notes", "description")
            ?: ""
        val explicitProjTags = extractJsonTagNames(projectObj?.get("tags") ?: effectiveRoot["tags"])

        data class SectionMeta(
            val gid: String?,
            val title: String,
            val startDate: Long?,
            val endDate: Long?,
            val embeddedTasks: JsonArray?
        )

        val rawSectionsArray = (effectiveRoot["sections"] as? JsonArray)
            ?: (effectiveRoot["phases"] as? JsonArray)
            ?: (effectiveRoot["headings"] as? JsonArray)

        val sectionMetas = rawSectionsArray?.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val rawTitle = obj.stringProp("name", "title") ?: return@mapNotNull null
            val cleanTitle = normalizeAsanaSectionName(rawTitle) ?: return@mapNotNull null
            val startMs = parseExternalDate(obj.stringProp("start_on", "startDate", "start_date"), zoneId)
            val endMs = parseExternalDate(obj.stringProp("due_on", "endDate", "end_date", "end_on"), zoneId)
            SectionMeta(
                gid = obj.stringProp("gid", "id"),
                title = cleanTitle,
                startDate = startMs,
                endDate = endMs,
                embeddedTasks = (obj["tasks"] as? JsonArray) ?: (obj["items"] as? JsonArray)
            )
        }.orEmpty()

        val dataArray = (rootObj["data"] as? JsonArray)
            ?: (effectiveRoot["data"] as? JsonArray)

        if (dataArray != null) {
            return parseAsanaDataArrayProject(
                dataArray = dataArray,
                explicitProjectTitle = explicitProjTitle,
                explicitProjectNotes = explicitProjNotes,
                explicitProjectTags = explicitProjTags,
                predefinedSections = sectionMetas.map { Triple(it.gid, it.title, it.startDate to it.endDate) },
                fallbackTitle = safeFallback,
                zoneId = zoneId
            )
        }

        // Structured JSON format: {"project": {...}, "sections": [{"name": ..., "tasks": [...]}], "tasks": [...]}
        val ungroupedArray = (effectiveRoot["tasks"] as? JsonArray)
            ?: (effectiveRoot["ungroupedTasks"] as? JsonArray)
            ?: (effectiveRoot["unheadedTasks"] as? JsonArray)

        val ungroupedTasks = ungroupedArray
            ?.mapNotNull { parseSingleAsanaJsonTask(it, zoneId)?.first }
            .orEmpty()

        val phases = sectionMetas.map { meta ->
            val phaseTasks = meta.embeddedTasks
                ?.mapNotNull { parseSingleAsanaJsonTask(it, zoneId)?.first }
                .orEmpty()
            ExternalImportedPhase(
                title = meta.title,
                startDate = meta.startDate,
                endDate = meta.endDate,
                tasks = phaseTasks
            )
        }

        return ExternalImportedProject(
            title = explicitProjTitle?.takeIf { it.isNotBlank() } ?: safeFallback,
            notes = explicitProjNotes,
            ungroupedTasks = ungroupedTasks,
            phases = phases,
            tagNames = explicitProjTags
        )
    }

    private fun parseAsanaDataArrayProject(
        dataArray: JsonArray,
        explicitProjectTitle: String?,
        explicitProjectNotes: String,
        explicitProjectTags: List<String>,
        predefinedSections: List<Triple<String?, String, Pair<Long?, Long?>>>,
        fallbackTitle: String,
        zoneId: ZoneId
    ): ExternalImportedProject {
        class PhaseBucket(
            val title: String,
            var startDate: Long? = null,
            var endDate: Long? = null,
            val tasks: MutableList<ExternalImportedTask> = mutableListOf()
        )

        val phaseBucketsByLowerTitle = LinkedHashMap<String, PhaseBucket>()
        val phaseBucketsByGid = HashMap<String, PhaseBucket>()

        for ((gid, title, dates) in predefinedSections) {
            val lower = title.lowercase(Locale.ROOT)
            val bucket = phaseBucketsByLowerTitle.getOrPut(lower) {
                PhaseBucket(title = title, startDate = dates.first, endDate = dates.second)
            }
            if (bucket.startDate == null && dates.first != null) bucket.startDate = dates.first
            if (bucket.endDate == null && dates.second != null) bucket.endDate = dates.second
            if (!gid.isNullOrBlank()) {
                phaseBucketsByGid[gid] = bucket
            }
        }

        class ParsedJsonTaskHolder(
            val gid: String?,
            val baseTask: ExternalImportedTask,
            val sectionGid: String?,
            val sectionName: String?,
            val projectName: String?,
            val parentRef: String?,
            val extraSubtasks: MutableList<ExternalImportedChecklistItem> = mutableListOf()
        ) {
            fun toFinalTask(): ExternalImportedTask {
                if (extraSubtasks.isEmpty()) return baseTask
                val existingTitles = baseTask.checklistItems.mapTo(HashSet()) { it.title.lowercase(Locale.ROOT) }
                val merged = baseTask.checklistItems + extraSubtasks.filter {
                    it.title.lowercase(Locale.ROOT) !in existingTitles
                }
                return baseTask.copy(checklistItems = merged)
            }
        }

        var inferredProjectTitle: String? = explicitProjectTitle?.takeIf { it.isNotBlank() }
        val holders = mutableListOf<ParsedJsonTaskHolder>()

        for (elem in dataArray) {
            val parsedPair = parseSingleAsanaJsonTask(elem, zoneId) ?: continue
            val (task, metaObj) = parsedPair
            val gid = metaObj?.stringProp("gid", "id")

            var sectionGid: String? = null
            var sectionName: String? = null
            var membershipProjectName: String? = null

            val memberships = metaObj?.get("memberships") as? JsonArray
            if (memberships != null) {
                for (mElem in memberships) {
                    val mObj = mElem as? JsonObject ?: continue
                    val pObj = mObj["project"] as? JsonObject
                    if (membershipProjectName == null) {
                        membershipProjectName = pObj?.stringProp("name", "title")
                    }
                    val sObj = mObj["section"] as? JsonObject
                    if (sObj != null) {
                        val candName = normalizeAsanaSectionName(sObj.stringProp("name", "title"))
                        val candGid = sObj.stringProp("gid", "id")
                        if (candName != null || candGid != null) {
                            sectionName = candName
                            sectionGid = candGid
                            break
                        }
                    }
                }
            }

            if (sectionName == null && metaObj != null) {
                val directSec = metaObj["section"] ?: metaObj["assignee_section"]
                when (directSec) {
                    is JsonPrimitive -> sectionName = normalizeAsanaSectionName(directSec.contentOrNull)
                    is JsonObject -> {
                        sectionName = normalizeAsanaSectionName(directSec.stringProp("name", "title"))
                        if (sectionGid == null) sectionGid = directSec.stringProp("gid", "id")
                    }
                    else -> Unit
                }
            }

            if (inferredProjectTitle == null) {
                if (!membershipProjectName.isNullOrBlank()) {
                    inferredProjectTitle = membershipProjectName
                } else {
                    val projectsArr = metaObj?.get("projects") as? JsonArray
                    val firstProjObj = projectsArr?.firstOrNull() as? JsonObject
                    val pName = firstProjObj?.stringProp("name", "title")
                    if (!pName.isNullOrBlank()) inferredProjectTitle = pName
                }
            }

            val parentRef = when (val parentElem = metaObj?.get("parent")) {
                is JsonPrimitive -> parentElem.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                is JsonObject -> parentElem.stringProp("gid", "id", "name")
                else -> null
            }

            holders.add(
                ParsedJsonTaskHolder(
                    gid = gid,
                    baseTask = task,
                    sectionGid = sectionGid,
                    sectionName = sectionName,
                    projectName = membershipProjectName,
                    parentRef = parentRef
                )
            )
        }

        val byGid = LinkedHashMap<String, ParsedJsonTaskHolder>()
        val byTitle = LinkedHashMap<String, ParsedJsonTaskHolder>()
        for (h in holders) {
            if (h.parentRef == null) {
                if (!h.gid.isNullOrBlank()) byGid.putIfAbsent(h.gid.lowercase(Locale.ROOT), h)
                byTitle.putIfAbsent(h.baseTask.title.lowercase(Locale.ROOT), h)
            }
        }
        for (h in holders) {
            if (!h.gid.isNullOrBlank()) byGid.putIfAbsent(h.gid.lowercase(Locale.ROOT), h)
            byTitle.putIfAbsent(h.baseTask.title.lowercase(Locale.ROOT), h)
        }

        val topHolders = mutableListOf<ParsedJsonTaskHolder>()
        for (h in holders) {
            if (h.parentRef == null) {
                topHolders.add(h)
            } else {
                val key = h.parentRef.lowercase(Locale.ROOT)
                val parent = (byGid[key] ?: byTitle[key])?.takeIf { it !== h }
                if (parent != null) {
                    parent.extraSubtasks.add(
                        ExternalImportedChecklistItem(
                            title = h.baseTask.title,
                            isCompleted = h.baseTask.status == TaskStatus.COMPLETED
                        )
                    )
                } else {
                    topHolders.add(h)
                }
            }
        }

        val ungroupedTasks = mutableListOf<ExternalImportedTask>()
        for (h in topHolders) {
            val builtTask = h.toFinalTask()
            val matchedBucket = when {
                !h.sectionGid.isNullOrBlank() && phaseBucketsByGid.containsKey(h.sectionGid) ->
                    phaseBucketsByGid[h.sectionGid]
                !h.sectionName.isNullOrBlank() -> {
                    val lower = h.sectionName.lowercase(Locale.ROOT)
                    phaseBucketsByLowerTitle.getOrPut(lower) { PhaseBucket(title = h.sectionName) }.also { b ->
                        if (!h.sectionGid.isNullOrBlank()) phaseBucketsByGid[h.sectionGid] = b
                    }
                }
                else -> null
            }
            if (matchedBucket != null) {
                matchedBucket.tasks.add(builtTask)
            } else {
                ungroupedTasks.add(builtTask)
            }
        }

        val phases = phaseBucketsByLowerTitle.values.map { bucket ->
            ExternalImportedPhase(
                title = bucket.title,
                startDate = bucket.startDate,
                endDate = bucket.endDate,
                tasks = bucket.tasks.toList()
            )
        }

        return ExternalImportedProject(
            title = inferredProjectTitle ?: fallbackTitle,
            notes = explicitProjectNotes,
            ungroupedTasks = ungroupedTasks,
            phases = phases,
            tagNames = explicitProjectTags
        )
    }

    private fun parseSingleAsanaJsonTask(
        element: JsonElement,
        zoneId: ZoneId
    ): Pair<ExternalImportedTask, JsonObject?>? {
        if (element is JsonPrimitive) {
            val title = element.contentOrNull?.trim().orEmpty()
            return if (title.isEmpty()) null else ExternalImportedTask(title = title) to null
        }
        val obj = element as? JsonObject ?: return null
        val title = obj.stringProp("name", "title")?.trim().orEmpty()
        if (title.isEmpty()) return null

        val notes = obj.stringProp("notes", "description").orEmpty()
        val completedFlag = (obj["completed"] as? JsonPrimitive)?.booleanOrNull ?: false
        val completedAtRaw = obj.stringProp("completed_at", "completedAt")
        val isCompleted = completedFlag || !completedAtRaw.isNullOrBlank()
        val status = if (isCompleted) TaskStatus.COMPLETED else TaskStatus.OPEN
        val completedAtMs = if (isCompleted) {
            parseExternalTimestamp(completedAtRaw, zoneId) ?: System.currentTimeMillis()
        } else {
            null
        }

        val startMs = parseExternalDate(obj.stringProp("start_on", "start_at", "startDate", "whenDate"), zoneId)
        val dueMs = parseExternalDate(obj.stringProp("due_on", "due_at", "dueDate", "deadline"), zoneId)

        val isPinned = (obj["is_pinned"] as? JsonPrimitive)?.booleanOrNull
            ?: (obj["isPinned"] as? JsonPrimitive)?.booleanOrNull
            ?: (obj["liked"] as? JsonPrimitive)?.booleanOrNull
            ?: (obj["hearted"] as? JsonPrimitive)?.booleanOrNull
            ?: false

        val effortLevel = EffortLevel.normalize(obj.stringProp("effort_level", "effortLevel"))
        val tagNames = extractJsonTagNames(obj["tags"])

        val assigneeId = when (val assigneeElem = obj["assignee"]) {
            is JsonPrimitive -> assigneeElem.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            is JsonObject -> assigneeElem.stringProp("name", "email", "gid")
            else -> obj.stringProp("assigneeId", "assignee_id")
        }

        val subtasksElem = (obj["subtasks"] as? JsonArray)
            ?: (obj["checklistItems"] as? JsonArray)
            ?: (obj["checklist"] as? JsonArray)

        val checklist = subtasksElem?.mapNotNull { sub ->
            when (sub) {
                is JsonPrimitive -> {
                    val sTitle = sub.contentOrNull?.trim().orEmpty()
                    if (sTitle.isEmpty()) null else ExternalImportedChecklistItem(title = sTitle)
                }
                is JsonObject -> {
                    val sTitle = sub.stringProp("name", "title")?.trim().orEmpty()
                    if (sTitle.isEmpty()) {
                        null
                    } else {
                        val sDone = (sub["completed"] as? JsonPrimitive)?.booleanOrNull
                            ?: (sub["isCompleted"] as? JsonPrimitive)?.booleanOrNull
                            ?: !sub.stringProp("completed_at").isNullOrBlank()
                        ExternalImportedChecklistItem(title = sTitle, isCompleted = sDone)
                    }
                }
                else -> null
            }
        }.orEmpty()

        val task = ExternalImportedTask(
            title = title,
            notes = notes,
            status = status,
            completedAt = completedAtMs,
            whenDate = startMs,
            deadline = dueMs,
            isPinned = isPinned,
            effortLevel = effortLevel,
            tagNames = tagNames,
            checklistItems = checklist,
            assigneeId = assigneeId
        )
        return task to obj
    }

    private fun JsonObject.stringProp(vararg keys: String): String? {
        for (key in keys) {
            val prim = this[key] as? JsonPrimitive ?: continue
            val content = prim.contentOrNull?.trim()
            if (!content.isNullOrEmpty() && content != "null") {
                return content
            }
        }
        return null
    }

    private fun extractJsonTagNames(element: JsonElement?): List<String> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                is JsonObject -> item.stringProp("name", "title")
                else -> null
            }
        }.distinct()
    }

    // =========================================================================
    // 3. Microsoft Planner (.xlsx & CSV) Export & Import
    // =========================================================================

    fun exportPlannerCsv(
        project: Project,
        headings: List<Heading>,
        tasks: List<TaskItem>,
        projectTags: List<Tag> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ByteArray = exportPlannerCsvString(project, headings, tasks, projectTags, zoneId)
        .toByteArray(StandardCharsets.UTF_8)

    fun exportPlannerCsvString(
        project: Project,
        headings: List<Heading>,
        tasks: List<TaskItem>,
        @Suppress("UNUSED_PARAMETER") projectTags: List<Tag> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val rows = buildPlannerExportRows(project, headings, tasks, zoneId)
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine(PLANNER_COLUMNS.joinToString(",") { escapeCsvField(it) })
        for (row in rows) {
            val stringCells = row.map { cell ->
                when (cell) {
                    is PlannerCellValue.Text -> cell.value
                    is PlannerCellValue.DateVal -> formatIsoDate(cell.epochMillis, zoneId)
                }
            }
            sb.appendLine(stringCells.joinToString(",") { escapeCsvField(it) })
        }
        return sb.toString()
    }

    fun exportPlannerXlsx(
        project: Project,
        headings: List<Heading>,
        tasks: List<TaskItem>,
        @Suppress("UNUSED_PARAMETER") projectTags: List<Tag> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ByteArray {
        val dataRows = buildPlannerExportRows(project, headings, tasks, zoneId)

        val sharedStringsIndex = LinkedHashMap<String, Int>()
        val sharedStringsList = mutableListOf<String>()
        var totalStringRefs = 0

        fun internSharedString(value: String): Int {
            totalStringRefs++
            return sharedStringsIndex.getOrPut(value) {
                val idx = sharedStringsList.size
                sharedStringsList.add(value)
                idx
            }
        }

        val sheetRowsXml = StringBuilder()

        // Row 1: Header row (style s="1" bold)
        sheetRowsXml.append("""    <row r="1">""").append('\n')
        PLANNER_COLUMNS.forEachIndexed { colIdx, colTitle ->
            val ref = "${columnIndexToLetters(colIdx)}1"
            val sstIdx = internSharedString(colTitle)
            sheetRowsXml.append("""      <c r="$ref" s="1" t="s"><v>$sstIdx</v></c>""").append('\n')
        }
        sheetRowsXml.append("""    </row>""").append('\n')

        // Data rows 2..N+1
        dataRows.forEachIndexed { rowOffset, cells ->
            val excelRowNum = rowOffset + 2
            sheetRowsXml.append("""    <row r="$excelRowNum">""").append('\n')
            cells.forEachIndexed { colIdx, cell ->
                val ref = "${columnIndexToLetters(colIdx)}$excelRowNum"
                when (cell) {
                    is PlannerCellValue.Text -> {
                        if (cell.value.isNotEmpty()) {
                            val sstIdx = internSharedString(cell.value)
                            sheetRowsXml.append("""      <c r="$ref" t="s"><v>$sstIdx</v></c>""").append('\n')
                        }
                    }
                    is PlannerCellValue.DateVal -> {
                        val localDate = Instant.ofEpochMilli(cell.epochMillis).atZone(zoneId).toLocalDate()
                        val serial = localDateToExcelSerial(localDate)
                        sheetRowsXml.append("""      <c r="$ref" s="2"><v>$serial</v></c>""").append('\n')
                    }
                }
            }
            sheetRowsXml.append("""    </row>""").append('\n')
        }

        val lastRowNum = dataRows.size + 1
        val dimensionRef = "A1:P$lastRowNum"
        val sheetName = sanitizeExcelSheetName(project.title)

        val contentTypesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
              <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
              <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
            </Types>
        """.trimIndent()

        val rootRelsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
            </Relationships>
        """.trimIndent()

        val corePropsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <dc:title>${escapeXml(project.title)}</dc:title>
              <dc:description>${escapeXml(project.notes)}</dc:description>
              <dc:creator>Thingsbook</dc:creator>
            </cp:coreProperties>
        """.trimIndent()

        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="${escapeXml(sheetName)}" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
        """.trimIndent()

        val workbookRelsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
              <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
            </Relationships>
        """.trimIndent()

        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <numFmts count="1">
                <numFmt numFmtId="164" formatCode="yyyy-mm-dd"/>
              </numFmts>
              <fonts count="2">
                <font><sz val="11"/><name val="Calibri"/></font>
                <font><b/><sz val="11"/><name val="Calibri"/></font>
              </fonts>
              <fills count="2">
                <fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill>
              </fills>
              <borders count="1">
                <border><left/><right/><top/><bottom/><diagonal/></border>
              </borders>
              <cellStyleXfs count="1">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
              </cellStyleXfs>
              <cellXfs count="3">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
                <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
              </cellXfs>
            </styleSheet>
        """.trimIndent()

        val sharedStringsXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append('\n')
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="$totalStringRefs" uniqueCount="${sharedStringsList.size}">""").append('\n')
            for (str in sharedStringsList) {
                append("""  <si><t xml:space="preserve">${escapeXml(str)}</t></si>""").append('\n')
            }
            append("""</sst>""")
        }

        val worksheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append('\n')
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""").append('\n')
            append("""  <dimension ref="$dimensionRef"/>""").append('\n')
            append("""  <sheetData>""").append('\n')
            append(sheetRowsXml)
            append("""  </sheetData>""").append('\n')
            append("""</worksheet>""")
        }

        val outBytes = ByteArrayOutputStream()
        ZipOutputStream(outBytes).use { zos ->
            writeUtf8ZipEntry(zos, "[Content_Types].xml", contentTypesXml)
            writeUtf8ZipEntry(zos, "_rels/.rels", rootRelsXml)
            writeUtf8ZipEntry(zos, "docProps/core.xml", corePropsXml)
            writeUtf8ZipEntry(zos, "xl/workbook.xml", workbookXml)
            writeUtf8ZipEntry(zos, "xl/_rels/workbook.xml.rels", workbookRelsXml)
            writeUtf8ZipEntry(zos, "xl/styles.xml", stylesXml)
            writeUtf8ZipEntry(zos, "xl/sharedStrings.xml", sharedStringsXml)
            writeUtf8ZipEntry(zos, "xl/worksheets/sheet1.xml", worksheetXml)
        }
        return outBytes.toByteArray()
    }

    private fun writeUtf8ZipEntry(zos: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private sealed interface PlannerCellValue {
        data class Text(val value: String) : PlannerCellValue
        data class DateVal(val epochMillis: Long) : PlannerCellValue
    }

    private fun buildPlannerExportRows(
        @Suppress("UNUSED_PARAMETER") project: Project,
        headings: List<Heading>,
        tasks: List<TaskItem>,
        zoneId: ZoneId
    ): List<List<PlannerCellValue>> {
        val activeHeadings = headings
            .filter { !it.isDeleted && !it.isArchived }
            .sortedBy { it.sortOrder }
        val headingById = activeHeadings.associateBy { it.id }
        val exportableTasks = tasks.filter { !it.isDeleted && it.status != TaskStatus.CANCELED }
        val todayEpoch = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val orderedPairs = mutableListOf<Pair<TaskItem, String>>()
        exportableTasks
            .filter { it.headingId == null || it.headingId !in headingById }
            .sortedBy { it.sortOrder }
            .forEach { orderedPairs.add(it to "") }

        for (heading in activeHeadings) {
            exportableTasks
                .filter { it.headingId == heading.id }
                .sortedBy { it.sortOrder }
                .forEach { orderedPairs.add(it to heading.title.trim()) }
        }

        return orderedPairs.map { (task, bucketName) ->
            val isCompleted = task.status == TaskStatus.COMPLETED
            val progressStr = if (isCompleted) "Completed" else "Not started"
            val priorityStr = when {
                task.isPinned -> "Urgent"
                EffortLevel.normalize(task.effortLevel) == EffortLevel.DEEP_FOCUS -> "Important"
                EffortLevel.normalize(task.effortLevel) == EffortLevel.QUICK_WIN -> "Low"
                else -> "Medium"
            }
            val isLate = !isCompleted && task.deadline != null && task.deadline < todayEpoch
            val activeChecklist = task.checklistItems
                .filter { !it.isDeleted && it.title.isNotBlank() }
                .sortedBy { it.sortOrder }
            val allChecklistStr = activeChecklist.joinToString("; ") { it.title.trim() }
            val completedChecklistStr = activeChecklist
                .filter { it.isCompleted }
                .joinToString("; ") { it.title.trim() }
            val labelsStr = task.tags
                .filter { !it.isDeleted && it.title.isNotBlank() }
                .joinToString("; ") { it.title.trim() }

            listOf(
                PlannerCellValue.Text(task.id),
                PlannerCellValue.Text(task.title),
                PlannerCellValue.Text(bucketName),
                PlannerCellValue.Text(progressStr),
                PlannerCellValue.Text(priorityStr),
                PlannerCellValue.Text(task.assigneeId.orEmpty()),
                PlannerCellValue.Text(""),
                if (task.createdAt > 0L) PlannerCellValue.DateVal(task.createdAt) else PlannerCellValue.Text(""),
                task.whenDate?.let { PlannerCellValue.DateVal(it) } ?: PlannerCellValue.Text(""),
                task.deadline?.let { PlannerCellValue.DateVal(it) } ?: PlannerCellValue.Text(""),
                PlannerCellValue.Text(if (isLate) "true" else "false"),
                if (isCompleted) {
                    PlannerCellValue.DateVal(task.completedAt ?: task.updatedAt)
                } else {
                    PlannerCellValue.Text("")
                },
                PlannerCellValue.Text(task.notes),
                PlannerCellValue.Text(completedChecklistStr),
                PlannerCellValue.Text(allChecklistStr),
                PlannerCellValue.Text(labelsStr)
            )
        }
    }

    fun importPlannerCsv(
        csvText: String,
        fallbackTitle: String = "Imported Project",
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ExternalImportedProject {
        val safeFallback = fallbackTitle.trim().ifBlank { "Imported Project" }
        val cleaned = csvText.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) {
            return ExternalImportedProject(title = safeFallback)
        }
        val delimiter = detectCsvDelimiter(cleaned)
        val rows = parseCsvRows(cleaned, delimiter)
        return mapPlannerTableRows(
            rows = rows,
            projectTitle = safeFallback,
            projectNotes = "",
            zoneId = zoneId
        )
    }

    fun importPlannerXlsx(
        bytes: ByteArray,
        fallbackTitle: String = "Imported Project",
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ExternalImportedProject {
        val safeFallback = fallbackTitle.trim().ifBlank { "Imported Project" }
        val entries = readSafeZipXmlEntries(bytes)

        val sharedStringsXml = entries.entries
            .firstOrNull { it.key.equals("xl/sharedStrings.xml", ignoreCase = true) }
            ?.value
        val sharedStrings = if (sharedStringsXml != null) {
            parseSharedStringsXml(sharedStringsXml)
        } else {
            emptyList()
        }

        val corePropsXml = entries.entries
            .firstOrNull { it.key.equals("docProps/core.xml", ignoreCase = true) }
            ?.value
        val (coreTitle, coreDescription) = if (corePropsXml != null) {
            parseCorePropertiesXml(corePropsXml)
        } else {
            null to ""
        }

        val workbookXml = entries.entries
            .firstOrNull { it.key.equals("xl/workbook.xml", ignoreCase = true) }
            ?.value
        val sheetNameTitle = workbookXml?.let { parseFirstWorkbookSheetName(it) }
            ?.takeIf { name ->
                name.isNotBlank() && name.lowercase(Locale.ROOT) !in GENERIC_WORKBOOK_SHEET_NAMES
            }

        val worksheetBytes = entries.entries
            .firstOrNull { it.key.equals("xl/worksheets/sheet1.xml", ignoreCase = true) }
            ?.value
            ?: entries.entries
                .filter {
                    val lower = it.key.lowercase(Locale.ROOT)
                    lower.startsWith("xl/worksheets/") && lower.endsWith(".xml")
                }
                .minByOrNull { it.key.lowercase(Locale.ROOT) }
                ?.value
            ?: throw IllegalArgumentException("Invalid .xlsx file: missing worksheet XML in xl/worksheets/")

        val rows = parseWorksheetRowsXml(worksheetBytes, sharedStrings)
        val resolvedTitle = coreTitle?.takeIf { it.isNotBlank() }
            ?: sheetNameTitle
            ?: safeFallback

        return mapPlannerTableRows(
            rows = rows,
            projectTitle = resolvedTitle,
            projectNotes = coreDescription,
            zoneId = zoneId
        )
    }

    private fun readSafeZipXmlEntries(bytes: ByteArray): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        var totalBytesRead = 0L
        var entryCount = 0

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw IllegalArgumentException(".xlsx archive contains too many entries")
                }

                val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                if (normalizedName.isEmpty() ||
                    normalizedName.contains("..") ||
                    entry.isDirectory
                ) {
                    zis.closeEntry()
                    continue
                }

                val lower = normalizedName.lowercase(Locale.ROOT)
                val isRelevantXml = lower == "xl/sharedstrings.xml" ||
                    lower == "xl/workbook.xml" ||
                    lower == "docprops/core.xml" ||
                    (lower.startsWith("xl/worksheets/") && lower.endsWith(".xml"))

                if (isRelevantXml) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var entryBytes = 0
                    while (true) {
                        val read = zis.read(buf)
                        if (read == -1) break
                        entryBytes += read
                        totalBytesRead += read
                        if (entryBytes > MAX_SINGLE_ZIP_ENTRY_BYTES || totalBytesRead > MAX_TOTAL_ZIP_BYTES) {
                            throw IllegalArgumentException(".xlsx XML entry exceeds maximum safe size")
                        }
                        out.write(buf, 0, read)
                    }
                    map[normalizedName] = out.toByteArray()
                }
                zis.closeEntry()
            }
        }
        return map
    }

    private fun newSafeDocumentBuilder(): DocumentBuilder {
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = false
        runCatching { dbf.isExpandEntityReferences = false }
        runCatching { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        return dbf.newDocumentBuilder()
    }

    private fun parseSharedStringsXml(xmlBytes: ByteArray): List<String> {
        val doc = newSafeDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val root = doc.documentElement ?: return emptyList()
        val result = mutableListOf<String>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.localOrTagName() == "si") {
                val sb = StringBuilder()
                collectSiTextNodes(node, sb)
                result.add(sb.toString())
            }
        }
        return result
    }

    private fun collectSiTextNodes(node: Node, out: StringBuilder) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val tag = node.localOrTagName()
            // Skip phonetic guide runs <rPh> in Excel shared strings
            if (tag == "rPh") return
            if (tag == "t") {
                out.append(node.textContent.orEmpty())
                return
            }
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            collectSiTextNodes(children.item(i), out)
        }
    }

    private fun parseFirstWorkbookSheetName(xmlBytes: ByteArray): String? {
        return runCatching {
            val doc = newSafeDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
            val nodes = doc.getElementsByTagName("*")
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i) as? Element ?: continue
                if (elem.localOrTagName() == "sheet") {
                    val name = elem.getAttribute("name").trim()
                    if (name.isNotEmpty()) return@runCatching name
                }
            }
            null
        }.getOrNull()
    }

    private fun parseCorePropertiesXml(xmlBytes: ByteArray): Pair<String?, String> {
        return runCatching {
            val doc = newSafeDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
            var title: String? = null
            var description = ""
            val nodes = doc.getElementsByTagName("*")
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i) as? Element ?: continue
                when (elem.localOrTagName()) {
                    "title" -> title = elem.textContent?.trim()?.takeIf { it.isNotEmpty() }
                    "description" -> description = elem.textContent?.trim().orEmpty()
                }
            }
            title to description
        }.getOrDefault(null to "")
    }

    private fun parseWorksheetRowsXml(
        xmlBytes: ByteArray,
        sharedStrings: List<String>
    ): List<List<String>> {
        val doc = newSafeDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val rows = mutableListOf<List<String>>()
        val allElements = doc.getElementsByTagName("*")

        for (i in 0 until allElements.length) {
            val rowElem = allElements.item(i) as? Element ?: continue
            if (rowElem.localOrTagName() != "row") continue

            val cellsByCol = LinkedHashMap<Int, String>()
            var maxCol = -1
            var nextFallbackCol = 0

            val childNodes = rowElem.childNodes
            for (cIdx in 0 until childNodes.length) {
                val cellElem = childNodes.item(cIdx) as? Element ?: continue
                if (cellElem.localOrTagName() != "c") continue

                val refAttr = cellElem.getAttribute("r").trim()
                val colIndex = parseColumnIndexFromCellRef(refAttr) ?: nextFallbackCol
                nextFallbackCol = colIndex + 1
                if (colIndex > maxCol) maxCol = colIndex

                val cellType = cellElem.getAttribute("t").trim()
                val resolvedValue = resolveWorksheetCellValue(cellElem, cellType, sharedStrings)
                cellsByCol[colIndex] = resolvedValue
            }

            if (maxCol >= 0) {
                val rowList = MutableList(maxCol + 1) { col -> cellsByCol[col].orEmpty() }
                if (rowList.any { it.isNotBlank() }) {
                    rows.add(rowList)
                }
            }
        }
        return rows
    }

    private fun resolveWorksheetCellValue(
        cellElem: Element,
        cellType: String,
        sharedStrings: List<String>
    ): String {
        if (cellType == "inlineStr") {
            val sb = StringBuilder()
            val children = cellElem.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE && child.localOrTagName() == "is") {
                    collectSiTextNodes(child, sb)
                } else if (child.nodeType == Node.ELEMENT_NODE && child.localOrTagName() == "t") {
                    sb.append(child.textContent.orEmpty())
                }
            }
            return sb.toString()
        }

        var rawV: String? = null
        val children = cellElem.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.localOrTagName() == "v") {
                rawV = child.textContent.orEmpty()
                break
            }
        }
        val valueText = rawV ?: return ""

        return when (cellType) {
            "s" -> {
                val sstIndex = valueText.trim().toIntOrNull()
                if (sstIndex != null && sstIndex in sharedStrings.indices) {
                    sharedStrings[sstIndex]
                } else {
                    ""
                }
            }
            "b" -> if (valueText.trim() == "1" || valueText.trim().equals("true", ignoreCase = true)) {
                "true"
            } else {
                "false"
            }
            else -> valueText
        }
    }

    private fun Node.localOrTagName(): String {
        val raw = localName ?: nodeName ?: ""
        return raw.substringAfterLast(':')
    }

    private fun mapPlannerTableRows(
        rows: List<List<String>>,
        projectTitle: String,
        projectNotes: String,
        zoneId: ZoneId
    ): ExternalImportedProject {
        if (rows.isEmpty()) {
            return ExternalImportedProject(title = projectTitle, notes = projectNotes)
        }

        val rawHeaders = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        val hasHeader = rawHeaders.any { h ->
            h in setOf(
                "task name",
                "bucket name",
                "progress",
                "priority",
                "checklist items",
                "labels",
                "aufgabenname",
                "nom de la tâche",
                "nome attività",
                "nombre de la tarea"
            )
        }
        val dataRows = if (hasHeader) rows.drop(1) else rows

        fun findCol(vararg candidates: String, defaultIdx: Int = -1): Int {
            if (!hasHeader) return defaultIdx
            return rawHeaders.indexOfFirst { h -> candidates.any { c -> c == h } }
        }

        val idxTaskName = findCol(
            "task name",
            "aufgabenname",
            "nom de la tâche",
            "nome attività",
            "nombre de la tarea",
            "title",
            "name",
            defaultIdx = 1
        ).let { if (it >= 0) it else 0 }
        val idxBucket = findCol(
            "bucket name",
            "bucketname",
            "bucket",
            "nom du compartiment",
            "nome bucket",
            "nombre del depósito",
            defaultIdx = 2
        )
        val idxProgress = findCol(
            "progress",
            "fortschritt",
            "progression",
            "stato",
            "progreso",
            defaultIdx = 3
        )
        val idxPriority = findCol(
            "priority",
            "priorität",
            "priorité",
            "priorità",
            "prioridad",
            defaultIdx = 4
        )
        val idxAssignedTo = findCol(
            "assigned to",
            "zugewiesen an",
            "attribué à",
            "assegnato a",
            "asignado a",
            defaultIdx = 5
        )
        val idxStartDate = findCol(
            "start date",
            "startdatum",
            "date de début",
            "data di inizio",
            "fecha de inicio",
            defaultIdx = 8
        )
        val idxDueDate = findCol(
            "due date",
            "fälligkeitsdatum",
            "échéance",
            "date d'échéance",
            "data di scadenza",
            "fecha de vencimiento",
            defaultIdx = 9
        )
        val idxCompletedDate = findCol(
            "completed date",
            "abschlussdatum",
            "date d'achèvement",
            "data di completamento",
            "fecha de finalización",
            defaultIdx = 11
        )
        val idxDescription = findCol(
            "description",
            "beschreibung",
            "descrizione",
            "descripción",
            "notes",
            defaultIdx = 12
        )
        val idxCompletedChecklist = findCol(
            "completed checklist items",
            "abgeschlossene checklistenelemente",
            "éléments de liste de contrôle terminés",
            "elementi elenco di controllo completati",
            "elementos de lista de comprobación completados",
            defaultIdx = 13
        )
        val idxChecklist = findCol(
            "checklist items",
            "checklistenelemente",
            "éléments de la liste de contrôle",
            "elementi elenco di controllo",
            "elementos de lista de comprobación",
            defaultIdx = 14
        )
        val idxLabels = findCol(
            "labels",
            "bezeichnungen",
            "étiquettes",
            "etichette",
            "etiquetas",
            "categories",
            defaultIdx = 15
        )

        val ungroupedTasks = mutableListOf<ExternalImportedTask>()
        val bucketsByLower = LinkedHashMap<String, Pair<String, MutableList<ExternalImportedTask>>>()

        for (cols in dataRows) {
            fun col(idx: Int): String = if (idx >= 0) cols.getOrNull(idx)?.trim().orEmpty() else ""

            val taskName = col(idxTaskName)
            if (taskName.isEmpty()) continue

            val bucketRaw = col(idxBucket)
            val progressRaw = col(idxProgress)
            val priorityRaw = col(idxPriority)
            val assignedRaw = col(idxAssignedTo)
            val startRaw = col(idxStartDate)
            val dueRaw = col(idxDueDate)
            val completedDateRaw = col(idxCompletedDate)
            val descriptionRaw = col(idxDescription)
            val completedChecklistRaw = col(idxCompletedChecklist)
            val checklistRaw = col(idxChecklist)
            val labelsRaw = col(idxLabels)

            val isCompleted = progressRaw.lowercase(Locale.ROOT) in COMPLETED_PROGRESS_VALUES
            val status = if (isCompleted) TaskStatus.COMPLETED else TaskStatus.OPEN
            val completedAtMs = if (isCompleted) {
                parseExternalTimestamp(completedDateRaw, zoneId) ?: System.currentTimeMillis()
            } else {
                null
            }

            val priorityLower = priorityRaw.lowercase(Locale.ROOT)
            val isPinned = priorityLower in setOf("urgent", "dringend", "urgente")
            val effortLevel = when (priorityLower) {
                "important", "wichtig", "importante" -> EffortLevel.DEEP_FOCUS
                "low", "niedrig", "faible", "bassa", "baja" -> EffortLevel.QUICK_WIN
                else -> null
            }

            val checklistItems = parsePlannerChecklistItems(
                checklistRaw = checklistRaw,
                completedChecklistRaw = completedChecklistRaw
            )

            val tagNames = splitTagTokens(labelsRaw)

            val importedTask = ExternalImportedTask(
                title = taskName,
                notes = descriptionRaw,
                status = status,
                completedAt = completedAtMs,
                whenDate = parseExternalDate(startRaw, zoneId),
                deadline = parseExternalDate(dueRaw, zoneId),
                isPinned = isPinned,
                effortLevel = effortLevel,
                tagNames = tagNames,
                checklistItems = checklistItems,
                assigneeId = assignedRaw.takeIf { it.isNotEmpty() }
            )

            if (bucketRaw.isNotEmpty()) {
                val lowerBucket = bucketRaw.lowercase(Locale.ROOT)
                val pair = bucketsByLower.getOrPut(lowerBucket) {
                    bucketRaw to mutableListOf()
                }
                pair.second.add(importedTask)
            } else {
                ungroupedTasks.add(importedTask)
            }
        }

        val phases = bucketsByLower.values.map { (bucketTitle, bucketTasks) ->
            ExternalImportedPhase(
                title = bucketTitle,
                tasks = bucketTasks.toList()
            )
        }

        return ExternalImportedProject(
            title = projectTitle,
            notes = projectNotes,
            ungroupedTasks = ungroupedTasks,
            phases = phases
        )
    }

    internal fun parsePlannerChecklistItems(
        checklistRaw: String,
        completedChecklistRaw: String
    ): List<ExternalImportedChecklistItem> {
        val rawItems = splitPlannerChecklistTokens(checklistRaw)
        val trimmedCompleted = completedChecklistRaw.trim()

        val ratioMatch = RATIO_CHECKLIST_REGEX.matchEntire(trimmedCompleted)
        if (ratioMatch != null) {
            val completedCount = ratioMatch.groupValues[1].toIntOrNull()?.coerceAtLeast(0) ?: 0
            return rawItems.mapIndexed { idx, rawTitle ->
                val (cleanTitle, explicitDone) = stripOptionalCheckboxPrefix(rawTitle)
                ExternalImportedChecklistItem(
                    title = cleanTitle,
                    isCompleted = explicitDone || idx < completedCount
                )
            }
        }

        val completedTokens = splitPlannerChecklistTokens(trimmedCompleted)
            .map { stripOptionalCheckboxPrefix(it).first }
            .filter { it.isNotEmpty() }

        if (rawItems.isEmpty()) {
            return completedTokens.map { ExternalImportedChecklistItem(title = it, isCompleted = true) }
        }

        val remainingCompletedCounts = LinkedHashMap<String, Int>()
        for (cTitle in completedTokens) {
            val key = cTitle.lowercase(Locale.ROOT)
            remainingCompletedCounts[key] = (remainingCompletedCounts[key] ?: 0) + 1
        }

        val built = mutableListOf<ExternalImportedChecklistItem>()
        for (rawItem in rawItems) {
            val (cleanTitle, explicitDone) = stripOptionalCheckboxPrefix(rawItem)
            if (cleanTitle.isEmpty()) continue
            val key = cleanTitle.lowercase(Locale.ROOT)
            val rem = remainingCompletedCounts[key] ?: 0
            val matchedDone = if (rem > 0) {
                if (rem == 1) remainingCompletedCounts.remove(key) else remainingCompletedCounts[key] = rem - 1
                true
            } else {
                false
            }
            built.add(
                ExternalImportedChecklistItem(
                    title = cleanTitle,
                    isCompleted = explicitDone || matchedDone
                )
            )
        }

        // If a tool placed completed items exclusively in `Completed Checklist Items` and open ones in `Checklist Items`
        for (cTitle in completedTokens) {
            val key = cTitle.lowercase(Locale.ROOT)
            val rem = remainingCompletedCounts[key] ?: 0
            if (rem > 0) {
                if (rem == 1) remainingCompletedCounts.remove(key) else remainingCompletedCounts[key] = rem - 1
                built.add(ExternalImportedChecklistItem(title = cTitle, isCompleted = true))
            }
        }

        return built
    }

    private fun splitPlannerChecklistTokens(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(Regex("""[;\r\n]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun stripOptionalCheckboxPrefix(raw: String): Pair<String, Boolean> {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("[x]", ignoreCase = true) -> trimmed.substring(3).trim() to true
            trimmed.startsWith("[ ]") -> trimmed.substring(3).trim() to false
            trimmed.startsWith("[]") -> trimmed.substring(2).trim() to false
            else -> trimmed to false
        }
    }

    // =========================================================================
    // Date, Excel Serial, XML, Column Ref & CSV Helpers
    // =========================================================================

    fun excelSerialToLocalDate(serial: Double): LocalDate {
        val wholeDays = floor(serial).toLong()
        return EXCEL_EPOCH.plusDays(wholeDays)
    }

    fun localDateToExcelSerial(date: LocalDate): Long {
        return ChronoUnit.DAYS.between(EXCEL_EPOCH, date)
    }

    fun parseExternalDate(raw: String?, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        // 1. ISO YYYY-MM-DD (or leading YYYY-MM-DD before space/'T' when no timezone offset)
        runCatching {
            val ld = LocalDate.parse(trimmed)
            if (ld.year in 1900..2200) {
                return ld.atStartOfDay(zoneId).toInstant().toEpochMilli()
            }
        }

        // 2. ISO Instant / OffsetDateTime (e.g. 2026-11-20T14:30:00Z or 2026-11-20T14:30:00+01:00)
        if (trimmed.contains('T')) {
            runCatching {
                val ld = Instant.parse(trimmed).atZone(zoneId).toLocalDate()
                if (ld.year in 1900..2200) {
                    return ld.atStartOfDay(zoneId).toInstant().toEpochMilli()
                }
            }
            runCatching {
                val ld = OffsetDateTime.parse(trimmed).atZoneSameInstant(zoneId).toLocalDate()
                if (ld.year in 1900..2200) {
                    return ld.atStartOfDay(zoneId).toInstant().toEpochMilli()
                }
            }
            val datePrefix = trimmed.substringBefore('T').trim()
            runCatching {
                val ld = LocalDate.parse(datePrefix)
                if (ld.year in 1900..2200) {
                    return ld.atStartOfDay(zoneId).toInstant().toEpochMilli()
                }
            }
        }

        // 3. Space-separated datetime prefix (e.g. "2026-05-12 00:00:00" or "05/12/2026 10:00 AM")
        if (trimmed.contains(' ')) {
            val firstToken = trimmed.substringBefore(' ').trim()
            if (firstToken != trimmed && (firstToken.contains('-') || firstToken.contains('/') || firstToken.contains('.'))) {
                parseExternalDate(firstToken, zoneId)?.let { return it }
            }
        }

        // 4. Slash-delimited: MM/DD/YYYY, DD/MM/YYYY, or YYYY/MM/DD
        if (trimmed.contains('/')) {
            runCatching {
                val parts = trimmed.split('/')
                if (parts.size == 3) {
                    val p0Str = parts[0].trim()
                    val p0 = p0Str.toInt()
                    val p1 = parts[1].trim().toInt()
                    val p2 = parts[2].trim().toInt()
                    val ld = if (p0Str.length == 4) {
                        val y = normalizeYear(p0) ?: return@runCatching
                        LocalDate.of(y, p1, p2)
                    } else {
                        val y = normalizeYear(p2) ?: return@runCatching
                        if (p0 > 12 && p1 in 1..12) {
                            // Unambiguously DD/MM/YYYY
                            LocalDate.of(y, p1, p0)
                        } else {
                            // Standard MM/DD/YYYY (Planner default)
                            LocalDate.of(y, p0, p1)
                        }
                    }
                    return ld.atStartOfDay(zoneId).toInstant().toEpochMilli()
                }
            }
        }

        // 5. Dot-delimited: DD.MM.YYYY (European) vs numeric Excel serial with decimal (e.g. 46154.0)
        if (trimmed.contains('.')) {
            val dotParts = trimmed.split('.')
            if (dotParts.size == 3) {
                runCatching {
                    val d = dotParts[0].trim().toInt()
                    val m = dotParts[1].trim().toInt()
                    val y = normalizeYear(dotParts[2].trim().toInt()) ?: return@runCatching
                    return LocalDate.of(y, m, d).atStartOfDay(zoneId).toInstant().toEpochMilli()
                }
            }
        }

        // 6. Excel serial day number (e.g. 46154 or 46154.0 or 46154.5, using epoch 1899-12-30)
        val numericVal = trimmed.toDoubleOrNull()
        if (numericVal != null && numericVal.isFinite()) {
            if (numericVal in 1.0..2958465.999) {
                runCatching {
                    val ld = excelSerialToLocalDate(numericVal)
                    if (ld.year in 1900..2200) {
                        return ld.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    }
                }
            } else if (numericVal > 86_400_000.0) {
                runCatching {
                    val ld = Instant.ofEpochMilli(numericVal.toLong()).atZone(zoneId).toLocalDate()
                    if (ld.year in 1970..2200) {
                        return ld.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    }
                }
            }
        }

        return null
    }

    internal fun parseExternalTimestamp(raw: String?, zoneId: ZoneId): Long? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        runCatching {
            return Instant.parse(trimmed).toEpochMilli()
        }
        runCatching {
            return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        }
        return parseExternalDate(trimmed, zoneId)
    }

    private fun normalizeYear(rawYear: Int): Int? {
        val y = if (rawYear in 0..99) 2000 + rawYear else rawYear
        return y.takeIf { it in 1900..2200 }
    }

    private fun formatIsoDate(epochMillis: Long, zoneId: ZoneId): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun formatIsoInstant(epochMillis: Long): String {
        val safeMs = if (epochMillis > 0L) epochMillis else System.currentTimeMillis()
        return Instant.ofEpochMilli(safeMs).toString()
    }

    internal fun columnIndexToLetters(colIndex: Int): String {
        var n = colIndex + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    internal fun parseColumnIndexFromCellRef(cellRef: String): Int? {
        if (cellRef.isEmpty()) return null
        var col = 0
        var hasLetters = false
        for (ch in cellRef) {
            val upper = ch.uppercaseChar()
            if (upper in 'A'..'Z') {
                hasLetters = true
                col = col * 26 + (upper.code - 'A'.code + 1)
            } else if (ch == '$') {
                continue
            } else {
                break
            }
        }
        return if (hasLetters && col > 0) col - 1 else null
    }

    private fun sanitizeExcelSheetName(rawTitle: String): String {
        val cleaned = rawTitle
            .replace(Regex("""[\\/?*:\[\]\u0000-\u001F]+"""), " ")
            .trim()
            .trim('\'')
            .take(31)
            .trim()
            .trim('\'')
        return cleaned.ifBlank { "Planner Tasks" }
    }

    private fun escapeXml(value: String): String {
        if (value.isEmpty()) return ""
        val sb = StringBuilder(value.length + 16)
        for (ch in value) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
                ch.code < 0x20 -> Unit // Strip invalid XML 1.0 control chars
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun extractFileNameStem(fileNameHint: String?): String? {
        val raw = fileNameHint?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val baseName = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        if (baseName.isEmpty()) return null
        val dotIdx = baseName.lastIndexOf('.')
        val stem = if (dotIdx > 0) baseName.substring(0, dotIdx) else baseName
        return stem.trim().takeIf { it.isNotEmpty() }
    }

    private fun splitTagTokens(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun escapeCsvField(value: String): String {
        if (value.contains(',') ||
            value.contains(';') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        ) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    private fun detectCsvDelimiter(csvText: String): Char {
        val firstLine = csvText.lineSequence().firstOrNull().orEmpty()
        var commas = 0
        var semicolons = 0
        var tabs = 0
        var inQuotes = false
        for (ch in firstLine) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == ',' -> commas++
                !inQuotes && ch == ';' -> semicolons++
                !inQuotes && ch == '\t' -> tabs++
            }
        }
        return when {
            semicolons > commas && semicolons >= tabs -> ';'
            tabs > commas && tabs > semicolons -> '\t'
            else -> ','
        }
    }

    private fun parseCsvRows(csv: String, delimiter: Char): List<List<String>> {
        val cleanCsv = csv.removePrefix("\uFEFF")
        val result = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < cleanCsv.length) {
            val ch = cleanCsv[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < cleanCsv.length && cleanCsv[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> {
                        if (currentField.isEmpty()) {
                            inQuotes = true
                        } else {
                            currentField.append(ch)
                        }
                    }
                    delimiter -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                    }
                    '\r' -> {
                        if (i + 1 < cleanCsv.length && cleanCsv[i + 1] == '\n') i++
                        currentRow.add(currentField.toString())
                        currentField.clear()
                        if (currentRow.any { it.isNotBlank() }) result.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                        if (currentRow.any { it.isNotBlank() }) result.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    else -> currentField.append(ch)
                }
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.any { it.isNotBlank() }) result.add(currentRow)
        }
        return result
    }
}
