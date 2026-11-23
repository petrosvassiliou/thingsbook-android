package com.pv.thingsbook.data.ai

import com.pv.thingsbook.domain.util.runCatchingCancellable
import com.google.firebase.Firebase
import com.google.firebase.vertexai.type.generationConfig
import com.google.firebase.vertexai.vertexAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

class GeminiApiException(message: String, val httpCode: Int? = null) : Exception(message)

@Serializable
private data class GeminiRestPartDto(val text: String = "")

@Serializable
private data class GeminiRestContentDto(val parts: List<GeminiRestPartDto> = emptyList())

@Serializable
private data class GeminiRestGenerationConfigDto(
    val responseMimeType: String = "application/json",
    val temperature: Float = 0.35f
)

@Serializable
private data class GeminiRestRequestDto(
    val contents: List<GeminiRestContentDto>,
    val generationConfig: GeminiRestGenerationConfigDto = GeminiRestGenerationConfigDto()
)

@Serializable
private data class GeminiRestCandidateDto(
    val content: GeminiRestContentDto? = null
)

@Serializable
private data class GeminiRestResponseDto(
    val candidates: List<GeminiRestCandidateDto> = emptyList()
)

@Serializable
data class GeminiChecklistResponse(
    val items: List<String> = emptyList()
)

@Serializable
data class GeminiBrainDumpTaskDto(
    val title: String,
    val notes: String = "",
    val scheduleState: String = "INBOX",
    val whenDaysOffset: Int? = null,
    val deadlineDaysOffset: Int? = null,
    val checklist: List<String> = emptyList()
)

@Serializable
data class GeminiBrainDumpResponse(
    val tasks: List<GeminiBrainDumpTaskDto> = emptyList()
)

@Serializable
data class GeminiRoadmapTaskDto(
    val key: String = "",
    val title: String,
    val notes: String = "",
    val dayOffset: Int = 0,
    val durationMinutes: Int = 60,
    val effortLevel: String? = "MEDIUM",
    val isMilestone: Boolean = false,
    val dependsOnKeys: List<String> = emptyList(),
    val checklist: List<String> = emptyList()
)

@Serializable
data class GeminiProjectHeadingDto(
    val title: String,
    val tasks: List<String> = emptyList(),
    val startDayOffset: Int? = null,
    val endDayOffset: Int? = null,
    val richTasks: List<GeminiRoadmapTaskDto> = emptyList()
)

@Serializable
data class GeminiProjectBlueprintResponse(
    val projectTitle: String,
    val projectNotes: String = "",
    val headings: List<GeminiProjectHeadingDto> = emptyList()
)

data class GeminiDayTimeblockCandidate(
    val taskId: String,
    val title: String,
    val durationMinutes: Int,
    val effortLevel: String?,
    val isPinned: Boolean,
    val hasTodayDeadline: Boolean,
    val existingStartMinutes: Int? = null
)

@Serializable
data class GeminiDayTimeblockItemDto(
    val taskId: String,
    val startMinutes: Int,
    val durationMinutes: Int,
    val reason: String = ""
)

@Serializable
data class GeminiDayTimeblockPlanResponse(
    val summary: String = "",
    val blocks: List<GeminiDayTimeblockItemDto> = emptyList()
)

@Serializable
data class GeminiLogbookReviewResponse(
    val summaryParagraph: String,
    val suggestedFocusTasks: List<String> = emptyList()
)

/**
 * Multilingual Gemini AI Service for Thingsbook (`EN`, `DE`, `IT`, `FR`, `ES`).
 *
 * Supports:
 * 1. Official Firebase Vertex AI (`Firebase.vertexAI.generativeModel("gemini-2.5-flash")`)
 * 2. Optional direct Gemini Developer API Key override (`generativelanguage.googleapis.com`)
 * 3. Instant Offline-First Multilingual GTD Heuristic Fallback in all 5 languages (`EN`, `DE`, `IT`, `FR`, `ES`)
 *    so every Gemini feature responds immediately and localized even when offline.
 */
@Singleton
class ThingsbookGeminiService @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Volatile
    var customApiKey: String = ""

    private fun languageDisplayName(locale: Locale): String = when (locale.language.lowercase(Locale.ROOT)) {
        "de" -> "German (Deutsch)"
        "it" -> "Italian (Italiano)"
        "fr" -> "French (Français)"
        "es" -> "Spanish (Español)"
        else -> "English"
    }

    /**
     * 1. Magic Breakdown: Generates 4–6 actionable GTD checklist steps for an expanded task in the active Locale.
     */
    suspend fun generateMagicChecklist(
        taskTitle: String,
        taskNotes: String,
        locale: Locale = Locale.getDefault()
    ): List<String> = withContext(Dispatchers.IO) {
        val safeTitle = taskTitle.trim().take(500)
        val safeNotes = taskNotes.trim().take(2000)
        val langName = languageDisplayName(locale)
        val prompt = """
            You are the calm, precise GTD assistant inside Thingsbook (Things 3 style).
            Respond strictly in $langName (Locale: ${locale.language}).
            Break down the following To-Do into 4 to 5 concise, actionable checklist sub-steps.
            To-Do Title: "$safeTitle"
            To-Do Notes: "$safeNotes"
            Return ONLY valid JSON matching: {"items": ["Step 1", "Step 2", "Step 3", "Step 4"]}
        """.trimIndent()

        val rawJson = callGeminiJson(prompt, locale)
        if (!rawJson.isNullOrBlank()) {
            val parsed = runCatching {
                json.decodeFromString<GeminiChecklistResponse>(extractJsonObject(rawJson))
            }.getOrNull()
            val validItems = parsed?.items?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            if (validItems.isNotEmpty()) {
                return@withContext validItems
            }
        }

        // Offline-First Localized Fallback in EN, DE, IT, FR, ES
        offlineLocalizedBreakdown(safeTitle, locale)
    }

    /**
     * 2. Brain Dump -> Structured GTD Tasks: Extracts multiple scheduled tasks from freeform text in EN, DE, IT, FR, ES.
     */
    suspend fun parseBrainDumpToTasks(
        rawBrainDump: String,
        locale: Locale = Locale.getDefault()
    ): List<GeminiBrainDumpTaskDto> = withContext(Dispatchers.IO) {
        val safeDump = rawBrainDump.trim().take(4000)
        val langName = languageDisplayName(locale)
        val prompt = """
            You are the Thingsbook GTD Brain Dump parser.
            Respond strictly in $langName (Locale: ${locale.language}).
            Extract individual actionable To-Dos from the user's brain dump text:
            "$safeDump"
            For each task, determine:
            - title (concise action in $langName)
            - notes (optional context in $langName)
            - scheduleState: one of INBOX, TODAY, THIS_EVENING, UPCOMING, ANYTIME, SOMEDAY
            - whenDaysOffset: integer days from today (0 for today/this evening, 1 for tomorrow, etc., or null)
            - deadlineDaysOffset: integer days from today if a hard deadline is mentioned, or null
            - checklist: 0 to 3 sub-steps in $langName
            Return ONLY valid JSON matching:
            {"tasks": [{"title": "...", "notes": "...", "scheduleState": "TODAY", "whenDaysOffset": 0, "deadlineDaysOffset": null, "checklist": []}]}
        """.trimIndent()

        val rawJson = callGeminiJson(prompt, locale)
        if (!rawJson.isNullOrBlank()) {
            val parsed = runCatching {
                json.decodeFromString<GeminiBrainDumpResponse>(extractJsonObject(rawJson))
            }.getOrNull()
            val validTasks = parsed?.tasks?.filter { it.title.isNotBlank() }.orEmpty()
            if (validTasks.isNotEmpty()) {
                return@withContext validTasks
            }
        }

        offlineLocalizedBrainDumpParser(safeDump, locale)
    }

    /**
     * 3. v3.1 AI Roadmap Architect: Generates a complete Thingsbook v3 Project with dated Phases,
     *    Milestones (◆), Effort, Durations, Checklists, and Waiting-On Dependencies in EN, DE, IT, FR, ES.
     */
    suspend fun generateProjectBlueprint(
        goalPrompt: String,
        locale: Locale = Locale.getDefault()
    ): GeminiProjectBlueprintResponse = withContext(Dispatchers.IO) {
        val safeGoal = goalPrompt.trim().take(1500)
        val langName = languageDisplayName(locale)
        val prompt = """
            You are the Thingsbook v3.1 Roadmap Architect.
            Respond strictly in $langName (Locale: ${locale.language}).
            Create a structured GTD + Gantt project roadmap for the goal: "$safeGoal".
            Include:
            - projectTitle (clear outcome title in $langName)
            - projectNotes (1–2 inspiring sentences in $langName)
            - 3 logical phases (`headings`) spanning a realistic 2 to 4 week schedule starting at dayOffset 0:
              - title (phase title in $langName)
              - startDayOffset (integer >= 0)
              - endDayOffset (integer >= startDayOffset)
              - tasks: list of plain task titles (mirroring richTasks titles for compatibility)
              - richTasks: 3 to 4 structured items per phase, including at least 1 milestone (`isMilestone: true`) per project:
                - key: unique short id like "t1", "t2", ...
                - title: concise action or milestone name in $langName
                - notes: brief context in $langName (optional)
                - dayOffset: integer >= phase startDayOffset
                - durationMinutes: realistic estimate (30, 60, 120, 240, or 480; 30 for milestones)
                - effortLevel: one of "QUICK_WIN", "MEDIUM", "DEEP_FOCUS"
                - isMilestone: boolean (true for key phase deliverables ◆)
                - dependsOnKeys: array of upstream task `key` strings when strictly sequential (e.g. ["t1"])
                - checklist: 0 to 3 sub-steps in $langName
            Return ONLY valid JSON matching:
            {
              "projectTitle": "...",
              "projectNotes": "...",
              "headings": [
                {
                  "title": "...",
                  "startDayOffset": 0,
                  "endDayOffset": 4,
                  "tasks": ["..."],
                  "richTasks": [
                    {
                      "key": "t1",
                      "title": "...",
                      "notes": "",
                      "dayOffset": 0,
                      "durationMinutes": 120,
                      "effortLevel": "DEEP_FOCUS",
                      "isMilestone": false,
                      "dependsOnKeys": [],
                      "checklist": ["...", "..."]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val rawJson = callGeminiJson(prompt, locale)
        if (!rawJson.isNullOrBlank()) {
            val parsed = runCatching {
                json.decodeFromString<GeminiProjectBlueprintResponse>(extractJsonObject(rawJson))
            }.getOrNull()
            if (parsed != null && parsed.projectTitle.isNotBlank() && parsed.headings.isNotEmpty()) {
                val normalizedHeadings = parsed.headings.mapIndexed { hIdx, h ->
                    val fallbackTasks = if (h.tasks.isNotEmpty()) {
                        h.tasks
                    } else {
                        h.richTasks.map { it.title }.filter { it.isNotBlank() }
                    }
                    val enrichedRich = if (h.richTasks.isNotEmpty()) {
                        h.richTasks.filter { it.title.isNotBlank() }
                    } else {
                        fallbackTasks.mapIndexed { tIdx, tTitle ->
                            GeminiRoadmapTaskDto(
                                key = "h${hIdx}_t$tIdx",
                                title = tTitle,
                                dayOffset = hIdx * 4 + tIdx,
                                durationMinutes = if (tIdx == fallbackTasks.lastIndex && hIdx == parsed.headings.lastIndex) 30 else 120,
                                effortLevel = if (tIdx == 0) "DEEP_FOCUS" else "MEDIUM",
                                isMilestone = (tIdx == fallbackTasks.lastIndex && hIdx == parsed.headings.lastIndex)
                            )
                        }
                    }
                    h.copy(
                        tasks = fallbackTasks,
                        startDayOffset = h.startDayOffset ?: (enrichedRich.minOfOrNull { it.dayOffset } ?: (hIdx * 4)),
                        endDayOffset = h.endDayOffset ?: (enrichedRich.maxOfOrNull { it.dayOffset + maxOf(0, (it.durationMinutes - 1) / 480) } ?: (hIdx * 4 + 3)),
                        richTasks = enrichedRich
                    )
                }
                return@withContext parsed.copy(headings = normalizedHeadings)
            }
        }

        offlineLocalizedProjectBlueprint(safeGoal, locale)
    }

    /**
     * 5. v3.1 "Plan My Day" Smart Auto-Timeblocker: Proposes conflict-free Today timeblocks around
     *    calendar busy intervals and user workday bounds in EN, DE, IT, FR, ES.
     */
    suspend fun generateDailyTimeblockPlan(
        candidates: List<GeminiDayTimeblockCandidate>,
        busyIntervalsMinutes: List<Pair<Int, Int>>,
        workdayStartMinutes: Int = 9 * 60,
        workdayEndMinutes: Int = 17 * 60,
        earliestStartMinutes: Int = workdayStartMinutes,
        locale: Locale = Locale.getDefault()
    ): GeminiDayTimeblockPlanResponse = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) {
            return@withContext GeminiDayTimeblockPlanResponse(summary = "", blocks = emptyList())
        }
        val safeWorkStart = workdayStartMinutes.coerceIn(0, 22 * 60)
        val safeWorkEnd = workdayEndMinutes.coerceIn(safeWorkStart + 60, 24 * 60)
        val effectiveStart = maxOf(safeWorkStart, earliestStartMinutes).coerceAtMost(23 * 60)

        val deterministicFallback = buildDeterministicDayTimeblockPlan(
            candidates = candidates,
            busyIntervalsMinutes = busyIntervalsMinutes,
            workdayStartMinutes = safeWorkStart,
            workdayEndMinutes = safeWorkEnd,
            earliestStartMinutes = effectiveStart,
            locale = locale
        )

        val langName = languageDisplayName(locale)
        val busyDesc = if (busyIntervalsMinutes.isEmpty()) {
            "none"
        } else {
            busyIntervalsMinutes.joinToString(", ") { (s, e) -> "${formatClock(s)}-${formatClock(e)}" }
        }
        val tasksDesc = candidates.take(20).joinToString("\n") { c ->
            """- id="${c.taskId}", title="${c.title.take(120)}", durationMin=${c.durationMinutes}, effort=${c.effortLevel ?: "MEDIUM"}, pinned=${c.isPinned}, dueToday=${c.hasTodayDeadline}"""
        }
        val prompt = """
            You are the Thingsbook "Plan My Day" Focus Timeblocker.
            Respond strictly in $langName (Locale: ${locale.language}).
            Workday window: ${formatClock(effectiveStart)} (${effectiveStart}m) to ${formatClock(safeWorkEnd)} (${safeWorkEnd}m).
            Busy calendar/locked intervals (DO NOT overlap): $busyDesc.
            Open Today tasks to schedule:
            $tasksDesc
            Rules:
            1. Place tasks in 15-minute aligned startMinutes (0..1425) without overlapping busy intervals or each other.
            2. Prioritize pinned and dueToday tasks first, DEEP_FOCUS in longer uninterrupted morning/early blocks, and QUICK_WIN in short gaps.
            3. Keep durationMinutes realistic (15..240). Include a short 3-8 word `reason` in $langName.
            Return ONLY valid JSON matching:
            {"summary": "...", "blocks": [{"taskId": "...", "startMinutes": 540, "durationMinutes": 60, "reason": "..."}]}
        """.trimIndent()

        val rawJson = callGeminiJson(prompt, locale)
        if (!rawJson.isNullOrBlank()) {
            val parsed = runCatching {
                json.decodeFromString<GeminiDayTimeblockPlanResponse>(extractJsonObject(rawJson))
            }.getOrNull()
            val validCandidateIds = candidates.mapTo(HashSet()) { it.taskId }
            val sanitized = sanitizeProposedTimeblocks(
                proposed = parsed?.blocks.orEmpty().filter { it.taskId in validCandidateIds },
                busyIntervalsMinutes = busyIntervalsMinutes,
                earliestStartMinutes = effectiveStart
            )
            if (sanitized.isNotEmpty()) {
                return@withContext GeminiDayTimeblockPlanResponse(
                    summary = parsed?.summary?.takeIf { it.isNotBlank() } ?: deterministicFallback.summary,
                    blocks = sanitized
                )
            }
        }

        deterministicFallback
    }

    private fun formatClock(minutesFromMidnight: Int): String {
        val clamped = minutesFromMidnight.coerceIn(0, 1439)
        return "%02d:%02d".format(Locale.ROOT, clamped / 60, clamped % 60)
    }

    private fun sanitizeProposedTimeblocks(
        proposed: List<GeminiDayTimeblockItemDto>,
        busyIntervalsMinutes: List<Pair<Int, Int>>,
        earliestStartMinutes: Int
    ): List<GeminiDayTimeblockItemDto> {
        if (proposed.isEmpty()) return emptyList()
        val occupied = busyIntervalsMinutes
            .filter { it.second > it.first }
            .toMutableList()
        val result = mutableListOf<GeminiDayTimeblockItemDto>()
        val seenIds = HashSet<String>()

        for (item in proposed.sortedBy { it.startMinutes }) {
            if (!seenIds.add(item.taskId)) continue
            val dur = ((item.durationMinutes.coerceIn(15, 480) + 4) / 5) * 5
            var start = ((maxOf(earliestStartMinutes, item.startMinutes).coerceIn(0, 1425) + 7) / 15) * 15
            var guard = 0
            while (guard < 96 && start + dur <= 24 * 60) {
                val end = start + dur
                val conflict = occupied.firstOrNull { (bs, be) -> start < be && bs < end }
                if (conflict == null) break
                start = ((conflict.second + 14) / 15) * 15
                guard++
            }
            if (start + dur <= 24 * 60) {
                occupied += start to (start + dur)
                result += item.copy(startMinutes = start, durationMinutes = dur)
            }
        }
        return result.sortedBy { it.startMinutes }
    }

    fun buildDeterministicDayTimeblockPlan(
        candidates: List<GeminiDayTimeblockCandidate>,
        busyIntervalsMinutes: List<Pair<Int, Int>>,
        workdayStartMinutes: Int,
        workdayEndMinutes: Int,
        earliestStartMinutes: Int,
        locale: Locale
    ): GeminiDayTimeblockPlanResponse {
        val lang = locale.language.lowercase(Locale.ROOT)
        val sortedCandidates = candidates.sortedWith(
            compareByDescending<GeminiDayTimeblockCandidate> { it.isPinned }
                .thenByDescending { it.hasTodayDeadline }
                .thenBy {
                    when (it.effortLevel?.uppercase(Locale.ROOT)) {
                        "DEEP_FOCUS" -> 0
                        "MEDIUM" -> 1
                        "QUICK_WIN" -> 2
                        else -> 1
                    }
                }
                .thenByDescending { it.durationMinutes }
        )

        val occupied = busyIntervalsMinutes
            .filter { it.second > it.first }
            .sortedBy { it.first }
            .toMutableList()

        val blocks = mutableListOf<GeminiDayTimeblockItemDto>()
        val cursorFloor = ((earliestStartMinutes.coerceIn(0, 23 * 60) + 14) / 15) * 15

        for (cand in sortedCandidates) {
            val dur = cand.durationMinutes.coerceIn(15, 240)
            var slotStart = cursorFloor
            var placed = false
            var guard = 0
            while (guard < 96 && slotStart + dur <= 24 * 60) {
                val slotEnd = slotStart + dur
                val conflict = occupied.firstOrNull { (bs, be) -> slotStart < be && bs < slotEnd }
                if (conflict == null) {
                    placed = true
                    break
                }
                slotStart = ((conflict.second + 14) / 15) * 15
                guard++
            }
            if (placed) {
                // Add 5-15m breathing buffer by snapping next slot cleanly on 15m grid
                occupied += slotStart to (slotStart + dur)
                occupied.sortBy { it.first }
                val reason = localizedTimeblockReason(cand, slotStart < workdayEndMinutes, lang)
                blocks += GeminiDayTimeblockItemDto(
                    taskId = cand.taskId,
                    startMinutes = slotStart,
                    durationMinutes = dur,
                    reason = reason
                )
            }
        }

        val sortedBlocks = blocks.sortedBy { it.startMinutes }
        val summary = when (lang) {
            "de" -> "${sortedBlocks.size} fokussierte Zeitblöcke konfliktfrei um deine Termine eingeplant."
            "fr" -> "${sortedBlocks.size} créneaux planifiés sans conflit autour de votre agenda."
            "it" -> "${sortedBlocks.size} blocchi orari pianificati senza conflitti attorno ai tuoi impegni."
            "es" -> "${sortedBlocks.size} bloques de enfoque programados sin conflictos alrededor de tu agenda."
            else -> "Scheduled ${sortedBlocks.size} conflict-free focus blocks around your calendar."
        }
        return GeminiDayTimeblockPlanResponse(summary = summary, blocks = sortedBlocks)
    }

    private fun localizedTimeblockReason(
        cand: GeminiDayTimeblockCandidate,
        withinWorkday: Boolean,
        lang: String
    ): String {
        val effort = cand.effortLevel?.uppercase(Locale.ROOT)
        return when (lang) {
            "de" -> when {
                cand.isPinned || cand.hasTodayDeadline -> "Top-Priorität im freien Fokusfenster"
                effort == "DEEP_FOCUS" -> "Ungestörter Fokusblock"
                effort == "QUICK_WIN" -> "Kompakter Quick-Win zwischen Terminen"
                withinWorkday -> "Passendes freies Arbeitszeitfenster"
                else -> "Abendblock nach den Kernzeiten"
            }
            "fr" -> when {
                cand.isPinned || cand.hasTodayDeadline -> "Priorité placée sur le premier créneau libre"
                effort == "DEEP_FOCUS" -> "Créneau de concentration profonde"
                effort == "QUICK_WIN" -> "Action rapide entre deux rendez-vous"
                withinWorkday -> "Créneau libre dans vos horaires"
                else -> "Créneau de fin de journée"
            }
            "it" -> when {
                cand.isPinned || cand.hasTodayDeadline -> "Priorità alta nel primo spazio libero"
                effort == "DEEP_FOCUS" -> "Blocco di concentrazione profonda"
                effort == "QUICK_WIN" -> "Attività rapida tra gli impegni"
                withinWorkday -> "Finestra libera nell'orario di lavoro"
                else -> "Blocco serale fuori orario"
            }
            "es" -> when {
                cand.isPinned || cand.hasTodayDeadline -> "Prioridad clave en el primer hueco libre"
                effort == "DEEP_FOCUS" -> "Bloque de concentración profunda"
                effort == "QUICK_WIN" -> "Tarea rápida entre reuniones"
                withinWorkday -> "Hueco libre dentro de tu jornada"
                else -> "Bloque vespertino adicional"
            }
            else -> when {
                cand.isPinned || cand.hasTodayDeadline -> "Priority slot in your earliest free window"
                effort == "DEEP_FOCUS" -> "Uninterrupted deep-focus window"
                effort == "QUICK_WIN" -> "Quick win fitted cleanly into free gap"
                withinWorkday -> "Balanced slot within workday hours"
                else -> "Overflow slot after standard workday"
            }
        }
    }

    /**
     * 4. Logbook / Thingsbook AI Reflection: Summarizes completed tasks and proposes 3 calm focus items in EN, DE, IT, FR, ES.
     */
    suspend fun generateLogbookReflection(
        completedTaskTitles: List<String>,
        locale: Locale = Locale.getDefault()
    ): GeminiLogbookReviewResponse = withContext(Dispatchers.IO) {
        val langName = languageDisplayName(locale)
        val joinedTitles = completedTaskTitles.take(25).joinToString("; ") { it.take(160) }
        val prompt = """
            You are the calm Thingsbook Logbook reflection assistant.
            Respond strictly in $langName (Locale: ${locale.language}).
            Completed tasks in Logbook: [$joinedTitles].
            Write a calm, encouraging 2-sentence reflection summary in $langName and suggest 3 next high-leverage focus tasks for Today.
            Return ONLY valid JSON matching:
            {"summaryParagraph": "...", "suggestedFocusTasks": ["...", "...", "..."]}
        """.trimIndent()

        val rawJson = callGeminiJson(prompt, locale)
        if (!rawJson.isNullOrBlank()) {
            val parsed = runCatching {
                json.decodeFromString<GeminiLogbookReviewResponse>(extractJsonObject(rawJson))
            }.getOrNull()
            if (parsed != null && parsed.summaryParagraph.isNotBlank()) {
                return@withContext parsed
            }
        }

        offlineLocalizedLogbookReflection(completedTaskTitles, locale)
    }

    private suspend fun callGeminiJson(prompt: String, locale: Locale = Locale.getDefault()): String? {
        // 1. If user supplied a direct Gemini Developer API key in Settings, call REST endpoint
        val key = customApiKey.trim()
        if (key.isNotEmpty()) {
            val restResult = runCatchingCancellable { callGeminiRestApi(key, prompt, locale) }
                .getOrElse { err ->
                    if (err is GeminiApiException) throw err
                    null
                }
            if (!restResult.isNullOrBlank()) return restResult
        }

        // 2. Otherwise call Firebase Vertex AI Gemini 2.5 Flash
        return runCatchingCancellable {
            val model = Firebase.vertexAI.generativeModel(
                modelName = "gemini-2.5-flash",
                generationConfig = generationConfig {
                    temperature = 0.35f
                    responseMimeType = "application/json"
                }
            )
            model.generateContent(prompt).text
        }.getOrNull()
    }

    private fun localizedGeminiHttpError(code: Int, locale: Locale): String {
        val lang = locale.language.lowercase(Locale.ROOT).take(2)
        return if (code == 429) {
            when (lang) {
                "de" -> "Gemini API Rate-Limit erreicht (HTTP 429)."
                "fr" -> "Limite de requêtes de l'API Gemini atteinte (HTTP 429)."
                "it" -> "Limite di richieste API Gemini raggiunto (HTTP 429)."
                "es" -> "Límite de solicitudes de la API de Gemini alcanzado (HTTP 429)."
                else -> "Gemini API rate limit reached (HTTP 429)."
            }
        } else {
            when (lang) {
                "de" -> "Gemini API-Schlüssel ungültig oder abgelehnt (HTTP $code)."
                "fr" -> "Clé API Gemini invalide ou refusée (HTTP $code)."
                "it" -> "Chiave API Gemini non valida o rifiutata (HTTP $code)."
                "es" -> "Clave de API de Gemini no válida o rechazada (HTTP $code)."
                else -> "Gemini API key invalid or rejected (HTTP $code)."
            }
        }
    }

    private fun callGeminiRestApi(apiKey: String, prompt: String, locale: Locale): String? {
        val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            val payload = json.encodeToString(
                GeminiRestRequestDto(
                    contents = listOf(
                        GeminiRestContentDto(parts = listOf(GeminiRestPartDto(text = prompt)))
                    )
                )
            )

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            if (code !in 200..299) {
                if (code == 400 || code == 401 || code == 403 || code == 429) {
                    throw GeminiApiException(localizedGeminiHttpError(code, locale), code)
                }
                return null
            }

            val responseBody = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                .use { it.readText() }
            val responseDto = json.decodeFromString<GeminiRestResponseDto>(responseBody)
            return responseDto.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull { it.text.isNotBlank() }
                ?.text
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun extractJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
    }

    private fun offlineLocalizedBreakdown(title: String, locale: Locale): List<String> {
        val clean = title.trim().ifEmpty { "To-Do" }
        return when (locale.language.lowercase(Locale.ROOT)) {
            "de" -> listOf(
                "Ziel & Rahmenbedingungen für „$clean“ klären",
                "Wichtigste Unterlagen und Notizen zusammenstellen",
                "Ersten konkreten Umsetzungsschritt (15 Min. Fokus) starten",
                "Zwischenstand prüfen & offene Punkte abstimmen",
                "Abschluss dokumentieren und im Logbuch abhaken"
            )
            "it" -> listOf(
                "Definire l'obiettivo principale per «$clean»",
                "Raccogliere documenti, link e note necessarie",
                "Completare il primo passo operativo (15 min di focus)",
                "Verificare i dettagli e confermare le scadenze",
                "Concludere e archiviare nel Registro"
            )
            "fr" -> listOf(
                "Clarifier l'objectif principal pour « $clean »",
                "Rassembler les documents et notes essentiels",
                "Lancer la première étape concrète (15 min de concentration)",
                "Vérifier l'avancement et ajuster les détails",
                "Finaliser et valider dans le Journal"
            )
            "es" -> listOf(
                "Definir el objetivo clave para «$clean»",
                "Reunir notas, enlaces y materiales necesarios",
                "Completar el primer paso práctico (15 min de enfoque)",
                "Revisar avances y confirmar fechas límite",
                "Finalizar y registrar en el Registro"
            )
            else -> listOf(
                "Clarify desired outcome and scope for \"$clean\"",
                "Gather key notes, links, and required materials",
                "Execute the first 15-minute focused action step",
                "Review progress and align any dependencies",
                "Finalize details and mark complete in Logbook"
            )
        }
    }

    private fun offlineLocalizedBrainDumpParser(raw: String, locale: Locale): List<GeminiBrainDumpTaskDto> {
        val segments = raw
            .split(Regex("[,;\\n]+|\\b(?:und|and|et|e|y|sowie|plus)\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.length >= 3 }

        if (segments.isEmpty()) return emptyList()

        return segments.map { segment ->
            val lower = segment.lowercase(locale)
            val (state, whenOffset) = when {
                lower.contains("heute abend") || lower.contains("this evening") ||
                    lower.contains("tonight") || lower.contains("stasera") ||
                    lower.contains("ce soir") || lower.contains("esta noche") -> "THIS_EVENING" to 0
                lower.contains("morgen") || lower.contains("tomorrow") ||
                    lower.contains("domani") || lower.contains("demain") ||
                    lower.contains("mañana") -> "UPCOMING" to 1
                lower.contains("heute") || lower.contains("today") ||
                    lower.contains("oggi") || lower.contains("aujourd") ||
                    lower.contains("hoy") -> "TODAY" to 0
                lower.contains("irgendwann") || lower.contains("someday") ||
                    lower.contains("prima o poi") || lower.contains("un jour") ||
                    lower.contains("algún día") -> "SOMEDAY" to null
                else -> "TODAY" to 0
            }

            val deadlineOffset = if (
                lower.contains("frist") || lower.contains("bis ") ||
                lower.contains("deadline") || lower.contains("by ") ||
                lower.contains("entro") || lower.contains("avant") ||
                lower.contains("para el")
            ) 3 else null

            GeminiBrainDumpTaskDto(
                title = segment.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                notes = "",
                scheduleState = state,
                whenDaysOffset = whenOffset,
                deadlineDaysOffset = deadlineOffset,
                checklist = emptyList()
            )
        }
    }

    private fun offlineLocalizedProjectBlueprint(goal: String, locale: Locale): GeminiProjectBlueprintResponse {
        val cleanGoal = goal.trim().ifEmpty { "Thingsbook Project" }
        return when (locale.language.lowercase(Locale.ROOT)) {
            "de" -> GeminiProjectBlueprintResponse(
                projectTitle = cleanGoal,
                projectNotes = "Von Thingsbook Gemini strukturierter GTD-Projektplan.",
                headings = listOf(
                    GeminiProjectHeadingDto(
                        title = "1. Konzeption & Vorbereitung",
                        tasks = listOf(
                            "Ziele und Erfolgskriterien für „$cleanGoal“ festlegen",
                            "Zeitplan, Budget und Meilensteine skizzieren",
                            "Wichtigste Ressourcen und Ansprechpartner sammeln"
                        )
                    ),
                    GeminiProjectHeadingDto(
                        title = "2. Fokus-Umsetzung",
                        tasks = listOf(
                            "Kernaufgaben mit höchster Hebelwirkung bearbeiten",
                            "Ersten Prototyp oder Entwurf fertigstellen",
                            "Zwischenergebnisse prüfen und feinschleifen"
                        )
                    ),
                    GeminiProjectHeadingDto(
                        title = "3. Abschluss & Review",
                        tasks = listOf(
                            "Finale Qualitätskontrolle durchführen",
                            "Projekt live schalten / übergeben",
                            "Learnings im Thingsbook Logbuch festhalten"
                        )
                    )
                )
            )
            "it" -> GeminiProjectBlueprintResponse(
                projectTitle = cleanGoal,
                projectNotes = "Piano di progetto GTD strutturato da Thingsbook Gemini.",
                headings = listOf(
                    GeminiProjectHeadingDto(
                        title = "1. Pianificazione & Preparazione",
                        tasks = listOf(
                            "Definire obiettivi e priorità per «$cleanGoal»",
                            "Raccogliere risorse, budget e tempistiche",
                            "Impostare le scadenze principali"
                        )
                    ),
                    GeminiProjectHeadingDto(
                        title = "2. Esecuzione Operativa",
                        tasks = listOf(
                            "Completare le attività ad alto impatto",
                            "Verificare lo stato di avanzamento intermedio",
                            "Rifinitura dei dettagli chiave"
                        )
                    ),
                    GeminiProjectHeadingDto(
                        title = "3. Revisione & Chiusura",
                        tasks = listOf(
                            "Controllo qualità finale",
                            "Consegna e rilascio definitivo",
                            "Archiviazione nel Registro Thingsbook"
                        )
                    )
                )
            )
            "fr" -> GeminiProjectBlueprintResponse(
                projectTitle = cleanGoal,
                projectNotes = "Plan de projet GTD structuré par Thingsbook Gemini.",
                headings = listOf(
                    GeminiProjectHeadingDto(
                        title = "1. Préparation & Cadrage",
                        tasks = listOf(
                            "Définir les objectifs clés pour « $cleanGoal »",
                            "Rassembler les ressources et fixer le calendrier",
                            "Identifier les prochaines actions prioritaires"
                        )
                    ),
                    GeminiProjectHeadingDto(
                        title = "2. Réalisation & Focus",
                        tasks = listOf(
                            "Exécuter les tâches principales à fort impact",
                            "Faire un point d'étape et ajuster le plan",
                            "Soigner les finitions et les détails"
                        )
                    ),
                    GeminiProjectHeadingDto(
                        title = "3. Finalisation & Bilan",
                        tasks = listOf(
                            "Vérification finale de la qualité",
                            "Validation et lancement",
                            "Archiver les réussites dans le Journal"
                        )
                    )
                )
            )
            "es" -> GeminiProjectBlueprintResponse(
                projectTitle = cleanGoal,
                projectNotes = "Plan de proyecto GTD estructurado por Thingsbook Gemini.",
                headings = listOf(
                    GeminiProjectHeadingDto(
                        title = "1. Preparación y Enfoque",
                        tasks = listOf(
                            "Definir objetivos y alcance para «$cleanGoal»",
                            "Reunir recursos y establecer fechas clave",
                            "Priorizar las primeras acciones concretas"
                        )
                    ),
                    GeminiProjectHeadingDto(
                        title = "2. Ejecución Principal",
                        tasks = listOf(
                            "Avanzar en las tareas de mayor impacto",
                            "Revisar el progreso intermedio",
                            "Pulir detalles importantes"
                        )
                    ),
                    GeminiProjectHeadingDto(
                        title = "3. Cierre y Revisión",
                        tasks = listOf(
                            "Realizar revisión final de calidad",
                            "Entrega y cierre del proyecto",
                            "Guardar aprendizajes en el Registro"
                        )
                    )
                )
            )
            else -> GeminiProjectBlueprintResponse(
                projectTitle = cleanGoal,
                projectNotes = "Calm GTD project blueprint structured by Thingsbook Gemini.",
                headings = listOf(
                    GeminiProjectHeadingDto(
                        title = "1. Discovery & Preparation",
                        tasks = listOf(
                            "Define clear success criteria for \"$cleanGoal\"",
                            "Gather essential resources, timeline, and notes",
                            "Schedule the first high-leverage milestone"
                        )
                    ),
                    GeminiProjectHeadingDto(
                        title = "2. Deep Execution",
                        tasks = listOf(
                            "Complete core deliverables during focus blocks",
                            "Review mid-project progress and refine scope",
                            "Polish key details and edge cases"
                        )
                    ),
                    GeminiProjectHeadingDto(
                        title = "3. Final Review & Launch",
                        tasks = listOf(
                            "Run final quality checklist",
                            "Deliver outcome and celebrate completion",
                            "Archive completed project in Thingsbook Logbook"
                        )
                    )
                )
            )
        }.let { base -> enrichOfflineBlueprintWithRoadmapMetadata(base) }
    }

    private fun enrichOfflineBlueprintWithRoadmapMetadata(
        base: GeminiProjectBlueprintResponse
    ): GeminiProjectBlueprintResponse {
        var runningDay = 0
        var prevMilestoneKey: String? = null
        val enrichedHeadings = base.headings.mapIndexed { hIdx, h ->
            val phaseStart = runningDay
            val rich = h.tasks.mapIndexed { tIdx, title ->
                val key = "p${hIdx + 1}_t${tIdx + 1}"
                val isLastInPhase = tIdx == h.tasks.lastIndex
                val isMilestone = isLastInPhase && (hIdx == 0 || hIdx == base.headings.lastIndex)
                val dur = when {
                    isMilestone -> 30
                    tIdx == 0 -> 240
                    else -> 120
                }
                val effort = when {
                    isMilestone -> "QUICK_WIN"
                    tIdx == 0 -> "DEEP_FOCUS"
                    else -> "MEDIUM"
                }
                val deps = buildList {
                    if (tIdx > 0) add("p${hIdx + 1}_t$tIdx")
                    else if (prevMilestoneKey != null) add(prevMilestoneKey!!)
                }
                val itemDay = runningDay
                runningDay += if (isMilestone) 1 else 2
                if (isMilestone) {
                    prevMilestoneKey = key
                }
                GeminiRoadmapTaskDto(
                    key = key,
                    title = title,
                    dayOffset = itemDay,
                    durationMinutes = dur,
                    effortLevel = effort,
                    isMilestone = isMilestone,
                    dependsOnKeys = deps
                )
            }
            val phaseEnd = maxOf(phaseStart, runningDay - 1)
            h.copy(
                startDayOffset = phaseStart,
                endDayOffset = phaseEnd,
                richTasks = rich
            )
        }
        return base.copy(headings = enrichedHeadings)
    }

    private fun offlineLocalizedLogbookReflection(completed: List<String>, locale: Locale): GeminiLogbookReviewResponse {
        val count = completed.size
        return when (locale.language.lowercase(Locale.ROOT)) {
            "de" -> GeminiLogbookReviewResponse(
                summaryParagraph = "Du hast $count Aufgaben ruhig und fokussiert abgeschlossen. Dein Thingsbook-Workflow zeigt klare Struktur zwischen Architektur, Design und täglichen Prioritäten.",
                suggestedFocusTasks = listOf(
                    "Wichtigste offene Aufgabe in „Heute“ als Erstes abschließen",
                    "Eingang (Inbox) in 2 Minuten auf null bringen",
                    "Einen entspannten Punkt für „Heute Abend“ einplanen"
                )
            )
            "it" -> GeminiLogbookReviewResponse(
                summaryParagraph = "Hai completato $count attività con chiarezza e calma. Il tuo Registro mostra un ottimo equilibrio tra lavoro profondo e organizzazione personale.",
                suggestedFocusTasks = listOf(
                    "Completare l'attività principale di Oggi",
                    "Svuotare l'Entrata in 2 minuti",
                    "Pianificare un momento rilassante per Stasera"
                )
            )
            "fr" -> GeminiLogbookReviewResponse(
                summaryParagraph = "Vous avez terminé $count tâches avec sérénité et précision. Votre Journal reflète une excellente maîtrise de vos priorités GTD.",
                suggestedFocusTasks = listOf(
                    "Accomplir la tâche prioritaire d'Aujourd'hui",
                    "Trier la Boîte de réception en 2 minutes",
                    "Prévoir une tâche apaisante pour Ce soir"
                )
            )
            "es" -> GeminiLogbookReviewResponse(
                summaryParagraph = "Has completado $count tareas con calma y claridad. Tu Registro muestra un progreso constante en tus proyectos clave.",
                suggestedFocusTasks = listOf(
                    "Completar la tarea más importante de Hoy",
                    "Dejar la Entrada limpia en 2 minutos",
                    "Programar una lectura tranquila para Esta noche"
                )
            )
            else -> GeminiLogbookReviewResponse(
                summaryParagraph = "You have calmly completed $count To-Dos in your Logbook. Your momentum across core projects and daily focus blocks is steady and well-balanced.",
                suggestedFocusTasks = listOf(
                    "Complete your #1 high-leverage To-Do in Today",
                    "Process any remaining Inbox items to zero",
                    "Set aside one calm reflection item for This Evening"
                )
            )
        }
    }
}
