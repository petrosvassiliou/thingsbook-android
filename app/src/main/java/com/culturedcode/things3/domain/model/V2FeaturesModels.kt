package com.culturedcode.things3.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Serializable
data class TemplateTaskBlueprint(
    val title: String,
    val notes: String = "",
    val checklistItems: List<String> = emptyList(),
    val dayOffset: Int? = null,
    val deadlineDayOffset: Int? = null,
    val scheduledStartMinutes: Int? = null,
    val durationMinutes: Int? = null,
    val isEvening: Boolean = false,
    val tagIds: List<String> = emptyList()
)

@Serializable
data class TemplateHeadingBlueprint(
    val title: String,
    val tasks: List<TemplateTaskBlueprint> = emptyList()
)

@Serializable
data class ProjectTemplateBlueprint(
    val title: String = "",
    val notes: String = "",
    val deadlineDayOffset: Int? = null,
    val unheadedTasks: List<TemplateTaskBlueprint> = emptyList(),
    val headings: List<TemplateHeadingBlueprint> = emptyList(),
    val tagIds: List<String> = emptyList()
) {
    val totalTaskCount: Int
        get() = unheadedTasks.size + headings.sumOf { it.tasks.size }
}

data class ProjectTemplate(
    val id: String,
    val title: String,
    val notes: String,
    val defaultAreaId: String?,
    val tagIds: List<String>,
    val blueprint: ProjectTemplateBlueprint,
    val sortOrder: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val isBuiltIn: Boolean = false,
    val isDeleted: Boolean = false
)

object ProjectTemplateCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encodeBlueprint(blueprint: ProjectTemplateBlueprint): String {
        return json.encodeToString(blueprint)
    }

    fun decodeBlueprint(rawJson: String, fallbackTitle: String = "", fallbackNotes: String = ""): ProjectTemplateBlueprint {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || trimmed == "{}") {
            return ProjectTemplateBlueprint(title = fallbackTitle, notes = fallbackNotes)
        }
        runCatching {
            val element = json.parseToJsonElement(trimmed)
            return parseJsonElementToBlueprint(element, fallbackTitle, fallbackNotes)
        }
        return runCatching {
            val decoded = json.decodeFromString<ProjectTemplateBlueprint>(trimmed)
            decoded.copy(
                title = decoded.title.ifBlank { fallbackTitle },
                notes = decoded.notes.ifBlank { fallbackNotes }
            )
        }.getOrDefault(ProjectTemplateBlueprint(title = fallbackTitle, notes = fallbackNotes))
    }

    internal fun parseJsonElementToBlueprint(
        element: JsonElement,
        fallbackTitle: String = "",
        fallbackNotes: String = ""
    ): ProjectTemplateBlueprint {
        return when (element) {
            is JsonArray -> {
                val tasks = element.mapNotNull { parseTaskElement(it) }
                ProjectTemplateBlueprint(
                    title = fallbackTitle.ifBlank { "Imported Project" },
                    notes = fallbackNotes,
                    unheadedTasks = tasks
                )
            }
            is JsonObject -> {
                val title = (element["title"] as? JsonPrimitive)?.contentOrNull
                    ?: (element["name"] as? JsonPrimitive)?.contentOrNull
                    ?: fallbackTitle
                val notes = (element["notes"] as? JsonPrimitive)?.contentOrNull
                    ?: (element["description"] as? JsonPrimitive)?.contentOrNull
                    ?: fallbackNotes
                val deadlineDayOffset = (element["deadlineDayOffset"] as? JsonPrimitive)?.intOrNull

                val unheadedArray = (element["unheadedTasks"] as? JsonArray)
                    ?: (element["tasks"] as? JsonArray)
                    ?: (element["items"] as? JsonArray)
                val unheadedTasks = unheadedArray?.mapNotNull { parseTaskElement(it) }.orEmpty()

                val headingsArray = (element["headings"] as? JsonArray)
                    ?: (element["sections"] as? JsonArray)
                val headings = headingsArray?.mapNotNull { headingElem ->
                    val obj = headingElem as? JsonObject ?: return@mapNotNull null
                    val hTitle = (obj["title"] as? JsonPrimitive)?.contentOrNull
                        ?: (obj["name"] as? JsonPrimitive)?.contentOrNull
                        ?: return@mapNotNull null
                    if (hTitle.isBlank()) return@mapNotNull null
                    val hTasksArray = (obj["tasks"] as? JsonArray) ?: (obj["items"] as? JsonArray)
                    val hTasks = hTasksArray?.mapNotNull { parseTaskElement(it) }.orEmpty()
                    TemplateHeadingBlueprint(title = hTitle.trim(), tasks = hTasks)
                }.orEmpty()

                val tagIds = (element["tagIds"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
                    .orEmpty()

                ProjectTemplateBlueprint(
                    title = title.trim().ifBlank { fallbackTitle },
                    notes = notes.trim(),
                    deadlineDayOffset = deadlineDayOffset,
                    unheadedTasks = unheadedTasks,
                    headings = headings,
                    tagIds = tagIds
                )
            }
            else -> ProjectTemplateBlueprint(title = fallbackTitle, notes = fallbackNotes)
        }
    }

    private fun parseTaskElement(element: JsonElement): TemplateTaskBlueprint? {
        return when (element) {
            is JsonPrimitive -> {
                val t = element.contentOrNull?.trim().orEmpty()
                if (t.isEmpty()) null else TemplateTaskBlueprint(title = t)
            }
            is JsonObject -> {
                val title = ((element["title"] as? JsonPrimitive)?.contentOrNull
                    ?: (element["name"] as? JsonPrimitive)?.contentOrNull)
                    ?.trim()
                    .orEmpty()
                if (title.isEmpty()) return null
                val notes = (element["notes"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val checklistRaw = (element["checklistItems"] as? JsonArray)
                    ?: (element["checklist"] as? JsonArray)
                    ?: (element["subtasks"] as? JsonArray)
                val checklist = checklistRaw?.mapNotNull { sub ->
                    when (sub) {
                        is JsonPrimitive -> sub.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                        is JsonObject -> ((sub["title"] as? JsonPrimitive)?.contentOrNull
                            ?: (sub["name"] as? JsonPrimitive)?.contentOrNull)
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                        else -> null
                    }
                }.orEmpty()
                val dayOffset = (element["dayOffset"] as? JsonPrimitive)?.intOrNull
                    ?: (element["whenDayOffset"] as? JsonPrimitive)?.intOrNull
                val deadlineOffset = (element["deadlineDayOffset"] as? JsonPrimitive)?.intOrNull
                val startMins = (element["scheduledStartMinutes"] as? JsonPrimitive)?.intOrNull
                val durationMins = (element["durationMinutes"] as? JsonPrimitive)?.intOrNull
                val isEvening = (element["isEvening"] as? JsonPrimitive)?.booleanOrNull ?: false
                val tagIds = (element["tagIds"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
                    .orEmpty()

                TemplateTaskBlueprint(
                    title = title,
                    notes = notes,
                    checklistItems = checklist,
                    dayOffset = dayOffset,
                    deadlineDayOffset = deadlineOffset,
                    scheduledStartMinutes = startMins,
                    durationMinutes = durationMins,
                    isEvening = isEvening,
                    tagIds = tagIds
                )
            }
            else -> null
        }
    }
}

object StarterProjectTemplates {
    val builtInTemplates: List<ProjectTemplate> by lazy {
        listOf(
            ProjectTemplate(
                id = "builtin_trip_packing",
                title = "✈️ Trip & Packing",
                notes = "Stress-free travel prep, booking verification, and departure checklist.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 1.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Trip & Packing",
                    notes = "Travel itinerary, documents, and packing checklist.",
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Bookings & Documents",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Confirm flights, hotel & local transit",
                                    checklistItems = listOf("Check-in reminder set", "Download offline boarding passes", "Save hotel address offline"),
                                    dayOffset = 0,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Verify passport, ID & travel insurance",
                                    checklistItems = listOf("Passport validity > 6 months", "Insurance card saved"),
                                    dayOffset = 0,
                                    durationMinutes = 15
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Packing Bag",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Pack clothing & essentials",
                                    checklistItems = listOf("Jackets & weather layers", "Comfortable shoes", "Toiletries kit", "Sunglasses"),
                                    dayOffset = 1,
                                    durationMinutes = 45
                                ),
                                TemplateTaskBlueprint(
                                    title = "Pack tech & chargers",
                                    checklistItems = listOf("Phone + USB-C cable", "Power bank charged", "Travel adapter", "Headphones"),
                                    dayOffset = 1,
                                    durationMinutes = 15
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Before Leaving Home",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Home departure sweep",
                                    checklistItems = listOf("Empty trash & perishables", "Unplug appliances", "Lock windows & doors"),
                                    dayOffset = 2,
                                    durationMinutes = 15
                                )
                            )
                        )
                    )
                )
            ),
            ProjectTemplate(
                id = "builtin_product_launch",
                title = "🚀 Product Launch",
                notes = "End-to-end GTD launch playbook from QA freeze to announcement.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 2.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Product Launch",
                    notes = "End-to-end launch checklist across product, QA, and release.",
                    deadlineDayOffset = 7,
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Readiness & QA",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Run release regression & upgrade tests",
                                    checklistItems = listOf("Clean install smoke test", "In-place upgrade migration test", "Cloud sync sanity check"),
                                    dayOffset = 0,
                                    durationMinutes = 60
                                ),
                                TemplateTaskBlueprint(
                                    title = "Finalize release notes & screenshots",
                                    checklistItems = listOf("Highlight top 3 features", "Localize changelog"),
                                    dayOffset = 1,
                                    durationMinutes = 45
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Distribution & Comms",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Tag release & upload signed artifacts",
                                    checklistItems = listOf("Verify SHA-256 checksums", "Publish GitHub release"),
                                    dayOffset = 2,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Monitor post-launch metrics & feedback",
                                    checklistItems = listOf("Triage early feedback", "Plan hotfix window if needed"),
                                    dayOffset = 3,
                                    durationMinutes = 30
                                )
                            )
                        )
                    )
                )
            ),
            ProjectTemplate(
                id = "builtin_client_onboarding",
                title = "🤝 Client Onboarding",
                notes = "Structured kickoff workflow for new client engagements.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 3.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Client Onboarding",
                    notes = "Kickoff milestones, access setup, and first deliverable plan.",
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Agreements & Access",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Counter-sign agreement & setup billing",
                                    checklistItems = listOf("Signed MSA / SOW archived", "Primary billing contact confirmed"),
                                    dayOffset = 0,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Provision shared workspace & credentials",
                                    checklistItems = listOf("Shared folder created", "Communication channel invite sent"),
                                    dayOffset = 1,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Kickoff & Roadmap",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Host kickoff call & align on 30-day goals",
                                    checklistItems = listOf("Confirm stakeholders", "Agree weekly check-in cadence", "Send meeting recap"),
                                    dayOffset = 2,
                                    durationMinutes = 60
                                )
                            )
                        )
                    )
                )
            ),
            ProjectTemplate(
                id = "builtin_weekly_sprint",
                title = "⚡ Weekly Sprint",
                notes = "Focused 5-day execution sprint with clear outcomes and retro.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 4.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Weekly Sprint",
                    notes = "Top outcomes, deep-work blocks, and Friday retrospective.",
                    deadlineDayOffset = 5,
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Sprint Planning",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Define top 3 sprint outcomes",
                                    checklistItems = listOf("Outcome #1", "Outcome #2", "Outcome #3"),
                                    dayOffset = 0,
                                    scheduledStartMinutes = 9 * 60,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Deep Work Execution",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Ship primary deliverable",
                                    dayOffset = 1,
                                    scheduledStartMinutes = 10 * 60,
                                    durationMinutes = 90
                                ),
                                TemplateTaskBlueprint(
                                    title = "Clear blockers & review mid-week progress",
                                    dayOffset = 2,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Demo & Retro",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Friday wrap-up & sprint retrospective",
                                    checklistItems = listOf("What went well", "What to improve next week"),
                                    dayOffset = 4,
                                    durationMinutes = 30
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}

object ProjectOutlineImporter {
    private val bulletRegex = Regex("""^([-*+]|\d+\.)\s+(?:\[[ xX]?]\s+)?(.+)$""")
    private val durationSuffixRegex = Regex("""(?:\s+|\()(\d{1,3})\s*m(?:in)?\)?$""", RegexOption.IGNORE_CASE)
    private val startTimeTokenRegex = Regex("""(?:^|\s)@(\d{1,2}):(\d{2})(?=\s|$)""")

    fun parseOutline(rawInput: String): ProjectTemplateBlueprint {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ProjectTemplateBlueprint(title = "Imported Project")
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val parsedJson = runCatching {
                val element = ProjectTemplateCodec.json.parseToJsonElement(trimmed)
                ProjectTemplateCodec.parseJsonElementToBlueprint(element, fallbackTitle = "Imported Project")
            }.getOrNull()
            if (parsedJson != null && (parsedJson.title.isNotBlank() || parsedJson.totalTaskCount > 0 || parsedJson.headings.isNotEmpty())) {
                return parsedJson.copy(title = parsedJson.title.ifBlank { "Imported Project" })
            }
        }

        var projectTitle = ""
        val projectNotesLines = mutableListOf<String>()
        val unheadedTasks = mutableListOf<MutableTaskBuilder>()
        val headings = mutableListOf<MutableHeadingBuilder>()
        var currentHeading: MutableHeadingBuilder? = null
        var lastTask: MutableTaskBuilder? = null

        for (rawLine in trimmed.lines()) {
            val expandedLine = rawLine.replace("\t", "    ")
            val indentSpaces = expandedLine.takeWhile { it == ' ' }.length
            val line = expandedLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("# ") && !line.startsWith("## ") -> {
                    val headingText = line.removePrefix("# ").trim()
                    if (projectTitle.isEmpty()) {
                        projectTitle = headingText
                    } else {
                        val h = MutableHeadingBuilder(headingText)
                        headings.add(h)
                        currentHeading = h
                    }
                    lastTask = null
                }
                line.startsWith("## ") || line.startsWith("### ") -> {
                    val headingText = line.trimStart('#').trim()
                    if (headingText.isNotEmpty()) {
                        val h = MutableHeadingBuilder(headingText)
                        headings.add(h)
                        currentHeading = h
                        lastTask = null
                    }
                }
                else -> {
                    val bulletMatch = bulletRegex.find(line)
                    if (bulletMatch != null) {
                        val content = bulletMatch.groupValues[2].trim()
                        if (indentSpaces >= 2 && lastTask != null) {
                            lastTask.checklistItems.add(content)
                        } else {
                            val parsedMeta = extractTaskInlineMetadata(content)
                            val taskBuilder = MutableTaskBuilder(
                                title = parsedMeta.cleanTitle,
                                scheduledStartMinutes = parsedMeta.scheduledStartMinutes,
                                durationMinutes = parsedMeta.durationMinutes
                            )
                            if (currentHeading != null) {
                                currentHeading.tasks.add(taskBuilder)
                            } else {
                                unheadedTasks.add(taskBuilder)
                            }
                            lastTask = taskBuilder
                        }
                    } else if (projectTitle.isEmpty() && headings.isEmpty() && unheadedTasks.isEmpty()) {
                        projectTitle = line
                    } else if (headings.isEmpty() && unheadedTasks.isEmpty()) {
                        projectNotesLines.add(line)
                    } else {
                        val parsedMeta = extractTaskInlineMetadata(line)
                        val taskBuilder = MutableTaskBuilder(
                            title = parsedMeta.cleanTitle,
                            scheduledStartMinutes = parsedMeta.scheduledStartMinutes,
                            durationMinutes = parsedMeta.durationMinutes
                        )
                        if (currentHeading != null) {
                            currentHeading.tasks.add(taskBuilder)
                        } else {
                            unheadedTasks.add(taskBuilder)
                        }
                        lastTask = taskBuilder
                    }
                }
            }
        }

        return ProjectTemplateBlueprint(
            title = projectTitle.ifBlank { "Imported Project" },
            notes = projectNotesLines.joinToString("\n").trim(),
            unheadedTasks = unheadedTasks.map { it.build() },
            headings = headings.map { it.build() }
        )
    }

    private data class InlineTaskMeta(
        val cleanTitle: String,
        val scheduledStartMinutes: Int?,
        val durationMinutes: Int?
    )

    private fun extractTaskInlineMetadata(rawTitle: String): InlineTaskMeta {
        var working = rawTitle.trim()
        var startMinutes: Int? = null
        var durationMinutes: Int? = null

        val timeMatch = startTimeTokenRegex.find(working)
        if (timeMatch != null) {
            val h = timeMatch.groupValues[1].toIntOrNull()
            val m = timeMatch.groupValues[2].toIntOrNull()
            if (h != null && m != null && h in 0..23 && m in 0..59) {
                startMinutes = h * 60 + m
                working = (working.substring(0, timeMatch.range.first) + " " + working.substring(timeMatch.range.last + 1)).trim()
            }
        }

        val durMatch = durationSuffixRegex.find(working)
        if (durMatch != null && rawTitle.endsWith(")")) {
            val d = durMatch.groupValues[1].toIntOrNull()
            if (d != null && d in 5..720) {
                durationMinutes = d
                working = working.substring(0, durMatch.range.first).trimEnd('(', ' ').trim()
            }
        }

        return InlineTaskMeta(
            cleanTitle = working.ifBlank { rawTitle.trim() },
            scheduledStartMinutes = startMinutes,
            durationMinutes = durationMinutes
        )
    }

    private class MutableTaskBuilder(
        val title: String,
        val scheduledStartMinutes: Int? = null,
        val durationMinutes: Int? = null,
        val checklistItems: MutableList<String> = mutableListOf()
    ) {
        fun build(): TemplateTaskBlueprint = TemplateTaskBlueprint(
            title = title,
            checklistItems = checklistItems.toList(),
            scheduledStartMinutes = scheduledStartMinutes,
            durationMinutes = durationMinutes
        )
    }

    private class MutableHeadingBuilder(
        val title: String,
        val tasks: MutableList<MutableTaskBuilder> = mutableListOf()
    ) {
        fun build(): TemplateHeadingBlueprint = TemplateHeadingBlueprint(
            title = title,
            tasks = tasks.map { it.build() }
        )
    }
}

enum class StalledProjectReason {
    NO_NEXT_ACTION,
    UNREVIEWED,
    BOTH
}

data class StalledProjectStatus(
    val project: Project,
    val openNextActionCount: Int,
    val hasNoNextAction: Boolean,
    val isUnreviewed: Boolean
) {
    val isStalled: Boolean
        get() = hasNoNextAction || isUnreviewed

    val reason: StalledProjectReason?
        get() = when {
            hasNoNextAction && isUnreviewed -> StalledProjectReason.BOTH
            hasNoNextAction -> StalledProjectReason.NO_NEXT_ACTION
            isUnreviewed -> StalledProjectReason.UNREVIEWED
            else -> null
        }
}

object StalledProjectDetector {
    const val FOURTEEN_DAYS_MILLIS: Long = 14L * 24L * 60L * 60L * 1000L

    fun evaluateProject(
        project: Project,
        openNextActionCount: Int = project.openTaskCount,
        nowMillis: Long = System.currentTimeMillis()
    ): StalledProjectStatus? {
        if (project.isDeleted ||
            project.status != ProjectStatus.ACTIVE ||
            project.scheduleState == ScheduleState.SOMEDAY
        ) {
            return null
        }
        val hasNoNextAction = openNextActionCount <= 0
        val lastReviewed = project.lastReviewedAt
        val isUnreviewed = lastReviewed == null ||
            (nowMillis - maxOf(project.updatedAt, lastReviewed)) >= FOURTEEN_DAYS_MILLIS

        if (!hasNoNextAction && !isUnreviewed) return null
        return StalledProjectStatus(
            project = project,
            openNextActionCount = openNextActionCount.coerceAtLeast(0),
            hasNoNextAction = hasNoNextAction,
            isUnreviewed = isUnreviewed
        )
    }

    fun detectStalledProjects(
        projects: List<Project>,
        openTaskCountByProjectId: Map<String, Int>? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): List<StalledProjectStatus> {
        return projects.mapNotNull { project ->
            val openCount = openTaskCountByProjectId?.get(project.id) ?: project.openTaskCount
            evaluateProject(project = project, openNextActionCount = openCount, nowMillis = nowMillis)
        }
    }
}

sealed interface TodayTimelineEntry {
    val entryId: String
    val startMinutes: Int
    val endMinutes: Int
    val hasConflict: Boolean

    data class TaskBlock(
        val task: TaskItem,
        override val startMinutes: Int,
        override val endMinutes: Int,
        val durationMinutes: Int,
        override val hasConflict: Boolean
    ) : TodayTimelineEntry {
        override val entryId: String get() = "task:${task.id}"
    }

    data class CalendarBlock(
        val event: CalendarEventItem,
        override val startMinutes: Int,
        override val endMinutes: Int,
        override val hasConflict: Boolean
    ) : TodayTimelineEntry {
        override val entryId: String get() = "cal:${event.id}"
    }
}

data class TodayTimelineSchedule(
    val allDayEvents: List<CalendarEventItem>,
    val timedEntries: List<TodayTimelineEntry>,
    val unscheduledTasks: List<TaskItem>,
    val conflictCount: Int
)

object TimeblockScheduleUtils {
    const val DEFAULT_TIMEBLOCK_DURATION_MINUTES = 30
    const val DEFAULT_FOCUS_DURATION_MINUTES = 25

    val START_TIME_PRESETS_MINUTES: List<Int> = listOf(
        8 * 60,
        9 * 60,
        10 * 60,
        11 * 60,
        13 * 60,
        14 * 60,
        15 * 60,
        16 * 60,
        17 * 60
    )

    val DURATION_PRESETS_MINUTES: List<Int> = listOf(15, 30, 45, 60, 90, 120)

    fun formatMinutesAsHm(minutesFromMidnight: Int): String {
        val clamped = ((minutesFromMidnight % 1440) + 1440) % 1440
        val h = clamped / 60
        val m = clamped % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun formatTimeblockRange(startMinutes: Int, durationMinutes: Int?): String {
        val dur = (durationMinutes ?: DEFAULT_TIMEBLOCK_DURATION_MINUTES).coerceIn(5, 720)
        val startStr = formatMinutesAsHm(startMinutes)
        val endStr = formatMinutesAsHm((startMinutes + dur).coerceAtMost(24 * 60 - 1))
        return "$startStr–$endStr (${dur}m)"
    }

    fun formatTimeblockBadge(startMinutes: Int, durationMinutes: Int?): String {
        return "🕐 ${formatTimeblockRange(startMinutes, durationMinutes)}"
    }

    fun parseHmToMinutes(raw: String): Int? {
        val parts = raw.trim().split(':')
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun intervalsOverlap(startA: Int, endA: Int, startB: Int, endB: Int): Boolean {
        return startA < endB && startB < endA
    }

    fun resolveFocusDurationMinutes(task: TaskItem?): Int {
        return (task?.durationMinutes ?: DEFAULT_FOCUS_DURATION_MINUTES).coerceIn(5, 240)
    }

    fun suggestNextFreeSlotMinutes(
        existingEntries: List<TodayTimelineEntry>,
        desiredDurationMinutes: Int = DEFAULT_TIMEBLOCK_DURATION_MINUTES,
        preferredStartMinutes: Int = 9 * 60
    ): Int {
        val dur = desiredDurationMinutes.coerceIn(15, 240)
        var candidate = preferredStartMinutes.coerceIn(0, 23 * 60)
        while (candidate + dur <= 23 * 60 + 45) {
            val overlaps = existingEntries.any { entry ->
                intervalsOverlap(candidate, candidate + dur, entry.startMinutes, entry.endMinutes)
            }
            if (!overlaps) return candidate
            candidate += 15
        }
        return preferredStartMinutes.coerceIn(0, 23 * 60)
    }

    fun buildTodayTimeline(
        todayTasks: List<TaskItem>,
        todayCalendarEvents: List<CalendarEventItem>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): TodayTimelineSchedule {
        val openTasks = todayTasks.filter { !it.isDeleted && it.status == TaskStatus.OPEN }
        val scheduledTasks = openTasks.filter { it.scheduledStartMinutes != null }
        val unscheduledTasks = openTasks.filter { it.scheduledStartMinutes == null }

        val allDayEvents = todayCalendarEvents.filter { it.isAllDay }
        val timedEvents = todayCalendarEvents.filter { !it.isAllDay }

        data class RawSlot(
            val id: String,
            val startMinutes: Int,
            val endMinutes: Int,
            val task: TaskItem? = null,
            val durationMinutes: Int = 0,
            val event: CalendarEventItem? = null
        )

        val rawSlots = mutableListOf<RawSlot>()

        for (task in scheduledTasks) {
            val start = (task.scheduledStartMinutes ?: continue).coerceIn(0, 1439)
            val dur = (task.durationMinutes ?: DEFAULT_TIMEBLOCK_DURATION_MINUTES).coerceIn(5, 720)
            val end = (start + dur).coerceAtMost(1440)
            rawSlots.add(
                RawSlot(
                    id = "task:${task.id}",
                    startMinutes = start,
                    endMinutes = end,
                    task = task,
                    durationMinutes = dur
                )
            )
        }

        for (event in timedEvents) {
            val startZdt = Instant.ofEpochMilli(event.startMillis).atZone(zoneId)
            val endZdt = Instant.ofEpochMilli(event.endMillis.coerceAtLeast(event.startMillis + 15 * 60_000L)).atZone(zoneId)
            val startMins = (startZdt.hour * 60 + startZdt.minute).coerceIn(0, 1439)
            val rawEndMins = if (endZdt.toLocalDate().isAfter(startZdt.toLocalDate())) {
                1440
            } else {
                (endZdt.hour * 60 + endZdt.minute).coerceIn(startMins + 5, 1440)
            }
            rawSlots.add(
                RawSlot(
                    id = "cal:${event.id}",
                    startMinutes = startMins,
                    endMinutes = rawEndMins,
                    event = event
                )
            )
        }

        rawSlots.sortWith(compareBy<RawSlot>({ it.startMinutes }, { it.endMinutes }, { it.id }))

        val conflictingIds = mutableSetOf<String>()
        for (i in rawSlots.indices) {
            for (j in i + 1 until rawSlots.size) {
                val a = rawSlots[i]
                val b = rawSlots[j]
                if (intervalsOverlap(a.startMinutes, a.endMinutes, b.startMinutes, b.endMinutes)) {
                    conflictingIds.add(a.id)
                    conflictingIds.add(b.id)
                }
            }
        }

        val entries = rawSlots.mapNotNull { slot ->
            val conflict = slot.id in conflictingIds
            when {
                slot.task != null -> TodayTimelineEntry.TaskBlock(
                    task = slot.task,
                    startMinutes = slot.startMinutes,
                    endMinutes = slot.endMinutes,
                    durationMinutes = slot.durationMinutes,
                    hasConflict = conflict
                )
                slot.event != null -> TodayTimelineEntry.CalendarBlock(
                    event = slot.event,
                    startMinutes = slot.startMinutes,
                    endMinutes = slot.endMinutes,
                    hasConflict = conflict
                )
                else -> null
            }
        }

        return TodayTimelineSchedule(
            allDayEvents = allDayEvents,
            timedEntries = entries,
            unscheduledTasks = unscheduledTasks,
            conflictCount = conflictingIds.size
        )
    }
}
