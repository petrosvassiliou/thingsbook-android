package com.culturedcode.things3.data.ai

import com.culturedcode.things3.domain.util.runCatchingCancellable
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
data class GeminiProjectHeadingDto(
    val title: String,
    val tasks: List<String> = emptyList()
)

@Serializable
data class GeminiProjectBlueprintResponse(
    val projectTitle: String,
    val projectNotes: String = "",
    val headings: List<GeminiProjectHeadingDto> = emptyList()
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

        val rawJson = callGeminiJson(prompt)
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

        val rawJson = callGeminiJson(prompt)
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
     * 3. AI Project Architect: Generates a complete Things 3 Project with Headings and To-Dos in EN, DE, IT, FR, ES.
     */
    suspend fun generateProjectBlueprint(
        goalPrompt: String,
        locale: Locale = Locale.getDefault()
    ): GeminiProjectBlueprintResponse = withContext(Dispatchers.IO) {
        val safeGoal = goalPrompt.trim().take(1500)
        val langName = languageDisplayName(locale)
        val prompt = """
            You are the Thingsbook Project Architect.
            Respond strictly in $langName (Locale: ${locale.language}).
            Create a structured Things 3 GTD project for the goal: "$safeGoal".
            Include a clear projectTitle, brief inspiring projectNotes, and 3 logical headings, each with 3 concrete tasks.
            Return ONLY valid JSON matching:
            {
              "projectTitle": "...",
              "projectNotes": "...",
              "headings": [
                {"title": "...", "tasks": ["...", "...", "..."]}
              ]
            }
        """.trimIndent()

        val rawJson = callGeminiJson(prompt)
        if (!rawJson.isNullOrBlank()) {
            val parsed = runCatching {
                json.decodeFromString<GeminiProjectBlueprintResponse>(extractJsonObject(rawJson))
            }.getOrNull()
            if (parsed != null && parsed.projectTitle.isNotBlank() && parsed.headings.isNotEmpty()) {
                return@withContext parsed
            }
        }

        offlineLocalizedProjectBlueprint(safeGoal, locale)
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

        val rawJson = callGeminiJson(prompt)
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

    private suspend fun callGeminiJson(prompt: String): String? {
        // 1. If user supplied a direct Gemini Developer API key in Settings, call REST endpoint
        val key = customApiKey.trim()
        if (key.isNotEmpty()) {
            val restResult = runCatchingCancellable { callGeminiRestApi(key, prompt) }
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

    private fun callGeminiRestApi(apiKey: String, prompt: String): String? {
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
                    val hint = if (code == 429) {
                        "Gemini API Rate-Limit erreicht (HTTP 429)."
                    } else {
                        "Gemini API-Schlüssel ungültig oder abgelehnt (HTTP $code)."
                    }
                    throw GeminiApiException(hint, code)
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
        }
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
