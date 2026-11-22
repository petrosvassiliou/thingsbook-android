package com.pv.thingsbook.domain.model

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
    val isMilestone: Boolean = false,
    val tagIds: List<String> = emptyList()
)

typealias BlueprintTask = TemplateTaskBlueprint

@Serializable
data class TemplateHeadingBlueprint(
    val title: String,
    val endDateDayOffset: Int? = null,
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
                    ?: (element["phases"] as? JsonArray)
                    ?: (element["sections"] as? JsonArray)
                val headings = headingsArray?.mapNotNull { headingElem ->
                    val obj = headingElem as? JsonObject ?: return@mapNotNull null
                    val hTitle = (obj["title"] as? JsonPrimitive)?.contentOrNull
                        ?: (obj["name"] as? JsonPrimitive)?.contentOrNull
                        ?: return@mapNotNull null
                    if (hTitle.isBlank()) return@mapNotNull null
                    val hEndDateOffset = (obj["endDateDayOffset"] as? JsonPrimitive)?.intOrNull
                    val hTasksArray = (obj["tasks"] as? JsonArray) ?: (obj["items"] as? JsonArray)
                    val hTasks = hTasksArray?.mapNotNull { parseTaskElement(it) }.orEmpty()
                    TemplateHeadingBlueprint(title = hTitle.trim(), endDateDayOffset = hEndDateOffset, tasks = hTasks)
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
                val isMilestone = (element["isMilestone"] as? JsonPrimitive)?.booleanOrNull
                    ?: (element["is_milestone"] as? JsonPrimitive)?.booleanOrNull
                    ?: ((element["resource_subtype"] as? JsonPrimitive)?.contentOrNull?.equals("milestone", ignoreCase = true) == true)
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
                    isMilestone = isMilestone,
                    tagIds = tagIds
                )
            }
            else -> null
        }
    }
}

object StarterProjectTemplates {
    val builtInTemplates: List<ProjectTemplate>
        get() = getBuiltInTemplates(Locale.getDefault().language)

    fun normalizeLanguageCode(languageCode: String): String {
        return when (languageCode.trim().lowercase(Locale.ROOT).take(2)) {
            "de" -> "de"
            "fr" -> "fr"
            "it" -> "it"
            "es" -> "es"
            else -> "en"
        }
    }

    fun getSampleOutlineMarkdown(languageCode: String): String {
        return when (normalizeLanguageCode(languageCode)) {
            "de" -> "# Q3-Launch-Plan\n## Vorbereitung\n- [ ] QA-Checkliste abschließen (45m)\n  - [ ] Neuinstallations-Schnelltest\n  - [ ] Upgrade-Migrationstest\n- [ ] Ankündigung formulieren @10:00 (30m)\n## Veröffentlichung\n- [ ] Signiertes Release veröffentlichen"
            "fr" -> "# Plan de lancement T3\n## Préparation\n- [ ] Finaliser la liste qualité (45m)\n  - [ ] Test d'installation propre\n  - [ ] Test de migration\n- [ ] Rédiger l'annonce @10:00 (30m)\n## Distribution\n- [ ] Publier la version signée"
            "it" -> "# Piano di lancio Q3\n## Preparazione\n- [ ] Completare lista qualità (45m)\n  - [ ] Test di nuova installazione\n  - [ ] Test di migrazione\n- [ ] Scrivere annuncio @10:00 (30m)\n## Distribuzione\n- [ ] Pubblicare versione firmata"
            "es" -> "# Plan de lanzamiento T3\n## Preparación\n- [ ] Completar lista de calidad (45m)\n  - [ ] Prueba de instalación limpia\n  - [ ] Prueba de migración\n- [ ] Redactar anuncio @10:00 (30m)\n## Distribución\n- [ ] Publicar versión firmada"
            else -> "# Q3 Launch Plan\n## Readiness\n- [ ] Finalize QA checklist (45m)\n  - [ ] Clean install smoke test\n  - [ ] Upgrade migration test\n- [ ] Draft release announcement @10:00 (30m)\n## Distribution\n- [ ] Publish signed release"
        }
    }

    fun getBuiltInTemplates(languageCode: String): List<ProjectTemplate> {
        return when (normalizeLanguageCode(languageCode)) {
            "de" -> germanTemplates
            "fr" -> frenchTemplates
            "it" -> italianTemplates
            "es" -> spanishTemplates
            else -> englishTemplates
        }
    }

    private val englishTemplates: List<ProjectTemplate> by lazy {
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

    private val germanTemplates: List<ProjectTemplate> by lazy {
        listOf(
            ProjectTemplate(
                id = "builtin_trip_packing",
                title = "✈️ Reise & Packliste",
                notes = "Stressfreie Reisevorbereitung, Buchungscheck und Abreise-Checkliste.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 1.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Reise & Packliste",
                    notes = "Reiseplan, Dokumente und Packliste.",
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Buchungen & Dokumente",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Flüge, Hotel & Nahverkehr bestätigen",
                                    checklistItems = listOf("Check-in-Erinnerung gesetzt", "Bordkarten offline speichern", "Hoteladresse offline sichern"),
                                    dayOffset = 0,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Reisepass, Ausweis & Reiseversicherung prüfen",
                                    checklistItems = listOf("Reisepass > 6 Monate gültig", "Versicherungsnachweis gespeichert"),
                                    dayOffset = 0,
                                    durationMinutes = 15
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Koffer packen",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Kleidung & Basics einpacken",
                                    checklistItems = listOf("Jacke & wetterfeste Kleidung", "Bequeme Schuhe", "Kulturbeutel", "Sonnenbrille"),
                                    dayOffset = 1,
                                    durationMinutes = 45
                                ),
                                TemplateTaskBlueprint(
                                    title = "Technik & Ladegeräte einpacken",
                                    checklistItems = listOf("Smartphone + USB-C-Kabel", "Powerbank geladen", "Reiseadapter", "Kopfhörer"),
                                    dayOffset = 1,
                                    durationMinutes = 15
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Vor der Abreise",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Wohnungscheck vor Abreise",
                                    checklistItems = listOf("Müll & Verderbliches entsorgen", "Geräte vom Strom trennen", "Fenster & Türen verschließen"),
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
                title = "🚀 Produkt-Launch",
                notes = "Durchgängiger GTD-Launch-Plan vom QA-Abschluss bis zur Ankündigung.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 2.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Produkt-Launch",
                    notes = "Vollständige Launch-Checkliste für Produkt, Qualitätssicherung und Release.",
                    deadlineDayOffset = 7,
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Vorbereitung & Qualitätssicherung",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Regressions- und Upgrade-Tests durchführen",
                                    checklistItems = listOf("Neuinstallations-Schnelltest", "Upgrade-Migrationstest im Bestand", "Cloud-Synchronisation prüfen"),
                                    dayOffset = 0,
                                    durationMinutes = 60
                                ),
                                TemplateTaskBlueprint(
                                    title = "Versionshinweise & Bildschirmfotos fertigstellen",
                                    checklistItems = listOf("Top-3-Funktionen hervorheben", "Änderungsprotokoll übersetzen"),
                                    dayOffset = 1,
                                    durationMinutes = 45
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Veröffentlichung & Kommunikation",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Version taggen & signierte Pakete hochladen",
                                    checklistItems = listOf("SHA-256-Prüfsummen verifizieren", "GitHub-Version veröffentlichen"),
                                    dayOffset = 2,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Kennzahlen & Rückmeldungen nach dem Start beobachten",
                                    checklistItems = listOf("Erstes Feedback sichten", "Hotfix-Zeitfenster bei Bedarf einplanen"),
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
                title = "🤝 Kunden-Onboarding",
                notes = "Strukturierter Startablauf für neue Kundenprojekte.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 3.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Kunden-Onboarding",
                    notes = "Auftakt-Meilensteine, Zugänge einrichten und Plan für das erste Ergebnis.",
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Verträge & Zugänge",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Vertrag gegenzeichnen & Abrechnung einrichten",
                                    checklistItems = listOf("Unterzeichneten Vertrag archiviert", "Rechnungskontakt bestätigt"),
                                    dayOffset = 0,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Gemeinsamen Arbeitsbereich & Zugangsdaten bereitstellen",
                                    checklistItems = listOf("Gemeinsamer Ordner erstellt", "Einladung zum Kommunikationskanal gesendet"),
                                    dayOffset = 1,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Auftakt & Fahrplan",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Kickoff-Gespräch führen & 30-Tage-Ziele abstimmen",
                                    checklistItems = listOf("Ansprechpartner bestätigen", "Wöchentlichen Abstimmungsrhythmus vereinbaren", "Gesprächsprotokoll versenden"),
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
                title = "⚡ Wochensprint",
                notes = "Fokussierter 5-Tage-Umsetzungssprint mit klaren Zielen und Rückblick.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 4.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Wochensprint",
                    notes = "Wichtigste Ergebnisse, Fokusblöcke und Freitags-Retrospektive.",
                    deadlineDayOffset = 5,
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Sprint-Planung",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Top-3-Ergebnisse der Woche festlegen",
                                    checklistItems = listOf("Ergebnis Nr. 1", "Ergebnis Nr. 2", "Ergebnis Nr. 3"),
                                    dayOffset = 0,
                                    scheduledStartMinutes = 9 * 60,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Fokussierte Umsetzung",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Hauptergebnis fertigstellen",
                                    dayOffset = 1,
                                    scheduledStartMinutes = 10 * 60,
                                    durationMinutes = 90
                                ),
                                TemplateTaskBlueprint(
                                    title = "Hindernisse lösen & Wochenmitte prüfen",
                                    dayOffset = 2,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Abschluss & Rückblick",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Freitagsabschluss & Sprint-Retrospektive",
                                    checklistItems = listOf("Was lief gut", "Was nächste Woche verbessern"),
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

    private val frenchTemplates: List<ProjectTemplate> by lazy {
        listOf(
            ProjectTemplate(
                id = "builtin_trip_packing",
                title = "✈️ Voyage et bagages",
                notes = "Préparation de voyage sereine, vérification des réservations et check-list de départ.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 1.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Voyage et bagages",
                    notes = "Itinéraire de voyage, documents et liste de bagages.",
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Réservations et documents",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Confirmer les vols, l'hôtel et les transports locaux",
                                    checklistItems = listOf("Rappel d'enregistrement programmé", "Télécharger les cartes d'embarquement hors ligne", "Enregistrer l'adresse de l'hôtel hors ligne"),
                                    dayOffset = 0,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Vérifier le passeport, la pièce d'identité et l'assurance voyage",
                                    checklistItems = listOf("Passeport valide > 6 mois", "Attestation d'assurance enregistrée"),
                                    dayOffset = 0,
                                    durationMinutes = 15
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Préparer la valise",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Préparer les vêtements et les essentiels",
                                    checklistItems = listOf("Vestes et tenues selon la météo", "Chaussures confortables", "Trousse de toilette", "Lunettes de soleil"),
                                    dayOffset = 1,
                                    durationMinutes = 45
                                ),
                                TemplateTaskBlueprint(
                                    title = "Préparer les appareils et chargeurs",
                                    checklistItems = listOf("Téléphone + câble USB-C", "Batterie externe chargée", "Adaptateur de voyage", "Écouteurs"),
                                    dayOffset = 1,
                                    durationMinutes = 15
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Avant de quitter la maison",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Tour de vérification avant le départ",
                                    checklistItems = listOf("Vider les poubelles et denrées périssables", "Débrancher les appareils", "Fermer fenêtres et portes à clé"),
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
                title = "🚀 Lancement de produit",
                notes = "Plan de lancement GTD complet, de la validation qualité à l'annonce.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 2.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Lancement de produit",
                    notes = "Liste complète de lancement couvrant produit, qualité et publication.",
                    deadlineDayOffset = 7,
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Préparation et validation qualité",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Exécuter les tests de non-régression et de mise à niveau",
                                    checklistItems = listOf("Test rapide d'installation propre", "Test de migration lors de la mise à jour", "Vérification de la synchronisation cloud"),
                                    dayOffset = 0,
                                    durationMinutes = 60
                                ),
                                TemplateTaskBlueprint(
                                    title = "Finaliser les notes de version et captures d'écran",
                                    checklistItems = listOf("Mettre en avant les 3 nouveautés clés", "Traduire le journal des modifications"),
                                    dayOffset = 1,
                                    durationMinutes = 45
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Distribution et communication",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Étiqueter la version et publier les paquets signés",
                                    checklistItems = listOf("Vérifier les empreintes SHA-256", "Publier la version sur GitHub"),
                                    dayOffset = 2,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Suivre les indicateurs et retours après lancement",
                                    checklistItems = listOf("Trier les premiers retours", "Prévoir un créneau de correctif si nécessaire"),
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
                title = "🤝 Intégration client",
                notes = "Processus de démarrage structuré pour les nouvelles missions clients.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 3.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Intégration client",
                    notes = "Jalons de lancement, configuration des accès et plan du premier livrable.",
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Contrats et accès",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Contresigner le contrat et configurer la facturation",
                                    checklistItems = listOf("Contrat signé archivé", "Contact principal de facturation confirmé"),
                                    dayOffset = 0,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Préparer l'espace de travail partagé et les identifiants",
                                    checklistItems = listOf("Dossier partagé créé", "Invitation au canal de communication envoyée"),
                                    dayOffset = 1,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Lancement et feuille de route",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Animer la réunion de lancement et aligner les objectifs à 30 jours",
                                    checklistItems = listOf("Confirmer les parties prenantes", "Définir le rythme de suivi hebdomadaire", "Envoyer le compte rendu"),
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
                title = "⚡ Sprint hebdomadaire",
                notes = "Sprint d'exécution ciblé sur 5 jours avec objectifs clairs et bilan.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 4.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Sprint hebdomadaire",
                    notes = "Résultats clés, créneaux de concentration et rétrospective du vendredi.",
                    deadlineDayOffset = 5,
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Planification du sprint",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Définir les 3 résultats prioritaires de la semaine",
                                    checklistItems = listOf("Résultat n° 1", "Résultat n° 2", "Résultat n° 3"),
                                    dayOffset = 0,
                                    scheduledStartMinutes = 9 * 60,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Travail en profondeur",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Livrer le livrable principal",
                                    dayOffset = 1,
                                    scheduledStartMinutes = 10 * 60,
                                    durationMinutes = 90
                                ),
                                TemplateTaskBlueprint(
                                    title = "Lever les blocages et faire le point à mi-semaine",
                                    dayOffset = 2,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Bilan et rétrospective",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Clôture du vendredi et rétrospective",
                                    checklistItems = listOf("Ce qui a bien fonctionné", "Ce qu'il faut améliorer la semaine prochaine"),
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

    private val italianTemplates: List<ProjectTemplate> by lazy {
        listOf(
            ProjectTemplate(
                id = "builtin_trip_packing",
                title = "✈️ Viaggio e valigia",
                notes = "Preparazione del viaggio senza stress, verifica prenotazioni e lista prima della partenza.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 1.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Viaggio e valigia",
                    notes = "Itinerario di viaggio, documenti e lista per la valigia.",
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Prenotazioni e documenti",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Confermare voli, hotel e trasporti locali",
                                    checklistItems = listOf("Promemoria check-in impostato", "Scaricare carte d'imbarco offline", "Salvare indirizzo dell'hotel offline"),
                                    dayOffset = 0,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Verificare passaporto, documento d'identità e assicurazione",
                                    checklistItems = listOf("Passaporto valido > 6 mesi", "Tessera assicurativa salvata"),
                                    dayOffset = 0,
                                    durationMinutes = 15
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Preparare il bagaglio",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Mettere in valigia abiti ed essenziali",
                                    checklistItems = listOf("Giacche e capi per il meteo", "Scarpe comode", "Beauty case", "Occhiali da sole"),
                                    dayOffset = 1,
                                    durationMinutes = 45
                                ),
                                TemplateTaskBlueprint(
                                    title = "Mettere in valigia dispositivi e caricabatterie",
                                    checklistItems = listOf("Telefono + cavo USB-C", "Power bank carica", "Adattatore da viaggio", "Cuffie"),
                                    dayOffset = 1,
                                    durationMinutes = 15
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Prima di uscire di casa",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Controllo finale di casa prima della partenza",
                                    checklistItems = listOf("Svuotare rifiuti e cibi deperibili", "Scollegare gli elettrodomestici", "Chiudere finestre e porte"),
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
                title = "🚀 Lancio prodotto",
                notes = "Piano operativo GTD completo dal blocco qualità fino all'annuncio.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 2.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Lancio prodotto",
                    notes = "Lista completa di lancio tra prodotto, verifica qualità e rilascio.",
                    deadlineDayOffset = 7,
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Preparazione e verifica qualità",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Eseguire test di regressione e di aggiornamento",
                                    checklistItems = listOf("Test rapido di nuova installazione", "Test di migrazione durante l'aggiornamento", "Controllo sincronizzazione cloud"),
                                    dayOffset = 0,
                                    durationMinutes = 60
                                ),
                                TemplateTaskBlueprint(
                                    title = "Finalizzare note di rilascio e schermate",
                                    checklistItems = listOf("Evidenziare le 3 novità principali", "Tradurre il registro delle modifiche"),
                                    dayOffset = 1,
                                    durationMinutes = 45
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Distribuzione e comunicazione",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Creare tag di versione e caricare i pacchetti firmati",
                                    checklistItems = listOf("Verificare impronte SHA-256", "Pubblicare versione su GitHub"),
                                    dayOffset = 2,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Monitorare metriche e riscontri post-lancio",
                                    checklistItems = listOf("Valutare i primi riscontri", "Pianificare finestra di correzione se necessario"),
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
                title = "🤝 Accoglienza cliente",
                notes = "Flusso di avvio strutturato per nuove collaborazioni con i clienti.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 3.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Accoglienza cliente",
                    notes = "Tappe iniziali, configurazione degli accessi e piano della prima consegna.",
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Accordi e accessi",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Controfirmare il contratto e impostare la fatturazione",
                                    checklistItems = listOf("Contratto firmato archiviato", "Referente amministrativo confermato"),
                                    dayOffset = 0,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Configurare spazio condiviso e credenziali",
                                    checklistItems = listOf("Cartella condivisa creata", "Invito al canale di comunicazione inviato"),
                                    dayOffset = 1,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Avvio e tabella di marcia",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Tenere la riunione iniziale e allineare gli obiettivi a 30 giorni",
                                    checklistItems = listOf("Confermare i referenti del progetto", "Concordare cadenza di aggiornamento settimanale", "Inviare riepilogo dell'incontro"),
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
                title = "⚡ Sprint settimanale",
                notes = "Ciclo operativo mirato di 5 giorni con obiettivi chiari e retrospettiva.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 4.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Sprint settimanale",
                    notes = "Risultati chiave, blocchi di concentrazione e retrospettiva del venerdì.",
                    deadlineDayOffset = 5,
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Pianificazione settimanale",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Definire i 3 risultati principali della settimana",
                                    checklistItems = listOf("Risultato n. 1", "Risultato n. 2", "Risultato n. 3"),
                                    dayOffset = 0,
                                    scheduledStartMinutes = 9 * 60,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Lavoro concentrato",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Completare il risultato principale",
                                    dayOffset = 1,
                                    scheduledStartMinutes = 10 * 60,
                                    durationMinutes = 90
                                ),
                                TemplateTaskBlueprint(
                                    title = "Rimuovere gli ostacoli e verificare i progressi di metà settimana",
                                    dayOffset = 2,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Riepilogo e retrospettiva",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Chiusura del venerdì e retrospettiva",
                                    checklistItems = listOf("Cosa ha funzionato bene", "Cosa migliorare la prossima settimana"),
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

    private val spanishTemplates: List<ProjectTemplate> by lazy {
        listOf(
            ProjectTemplate(
                id = "builtin_trip_packing",
                title = "✈️ Viaje y equipaje",
                notes = "Preparación de viaje sin estrés, verificación de reservas y lista antes de salir.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 1.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Viaje y equipaje",
                    notes = "Itinerario de viaje, documentos y lista de equipaje.",
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Reservas y documentos",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Confirmar vuelos, hotel y transporte local",
                                    checklistItems = listOf("Recordatorio de check-in activado", "Descargar tarjetas de embarque sin conexión", "Guardar dirección del hotel sin conexión"),
                                    dayOffset = 0,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Verificar pasaporte, documento de identidad y seguro de viaje",
                                    checklistItems = listOf("Pasaporte con validez > 6 meses", "Tarjeta del seguro guardada"),
                                    dayOffset = 0,
                                    durationMinutes = 15
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Hacer la maleta",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Empacar ropa y artículos esenciales",
                                    checklistItems = listOf("Chaquetas y capas para el clima", "Calzado cómodo", "Neceser de aseo", "Gafas de sol"),
                                    dayOffset = 1,
                                    durationMinutes = 45
                                ),
                                TemplateTaskBlueprint(
                                    title = "Empacar tecnología y cargadores",
                                    checklistItems = listOf("Teléfono + cable USB-C", "Batería externa cargada", "Adaptador de viaje", "Auriculares"),
                                    dayOffset = 1,
                                    durationMinutes = 15
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Antes de salir de casa",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Revisión final de casa antes de salir",
                                    checklistItems = listOf("Sacar basura y alimentos perecederos", "Desenchufar electrodomésticos", "Cerrar ventanas y puertas con llave"),
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
                title = "🚀 Lanzamiento de producto",
                notes = "Guía GTD integral de lanzamiento desde el cierre de pruebas hasta el anuncio.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 2.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Lanzamiento de producto",
                    notes = "Lista integral de lanzamiento para producto, pruebas y publicación.",
                    deadlineDayOffset = 7,
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Preparación y control de calidad",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Ejecutar pruebas de regresión y actualización",
                                    checklistItems = listOf("Prueba rápida de instalación limpia", "Prueba de migración al actualizar", "Revisión de sincronización en la nube"),
                                    dayOffset = 0,
                                    durationMinutes = 60
                                ),
                                TemplateTaskBlueprint(
                                    title = "Finalizar notas de la versión y capturas de pantalla",
                                    checklistItems = listOf("Destacar las 3 funciones principales", "Traducir el registro de cambios"),
                                    dayOffset = 1,
                                    durationMinutes = 45
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Distribución y comunicación",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Etiquetar versión y subir paquetes firmados",
                                    checklistItems = listOf("Verificar sumas SHA-256", "Publicar versión en GitHub"),
                                    dayOffset = 2,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Supervisar métricas y comentarios tras el lanzamiento",
                                    checklistItems = listOf("Clasificar primeros comentarios", "Planificar ventana de corrección si hace falta"),
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
                title = "🤝 Incorporación de cliente",
                notes = "Flujo estructurado de inicio para nuevos proyectos con clientes.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 3.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Incorporación de cliente",
                    notes = "Hitos de arranque, configuración de accesos y plan del primer entregable.",
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Acuerdos y accesos",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Firmar contrato y configurar la facturación",
                                    checklistItems = listOf("Contrato firmado archivado", "Contacto principal de facturación confirmado"),
                                    dayOffset = 0,
                                    durationMinutes = 30
                                ),
                                TemplateTaskBlueprint(
                                    title = "Preparar espacio de trabajo compartido y credenciales",
                                    checklistItems = listOf("Carpeta compartida creada", "Invitación al canal de comunicación enviada"),
                                    dayOffset = 1,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Reunión inicial y hoja de ruta",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Realizar llamada inicial y alinear objetivos de 30 días",
                                    checklistItems = listOf("Confirmar responsables", "Acordar frecuencia de seguimiento semanal", "Enviar resumen de la reunión"),
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
                title = "⚡ Sprint semanal",
                notes = "Ciclo enfocado de ejecución de 5 días con objetivos claros y retrospectiva.",
                defaultAreaId = null,
                tagIds = emptyList(),
                sortOrder = 4.0,
                createdAt = 0L,
                updatedAt = 0L,
                isBuiltIn = true,
                blueprint = ProjectTemplateBlueprint(
                    title = "Sprint semanal",
                    notes = "Resultados clave, bloques de concentración y retrospectiva del viernes.",
                    deadlineDayOffset = 5,
                    headings = listOf(
                        TemplateHeadingBlueprint(
                            title = "Planificación semanal",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Definir los 3 resultados clave de la semana",
                                    checklistItems = listOf("Resultado n.º 1", "Resultado n.º 2", "Resultado n.º 3"),
                                    dayOffset = 0,
                                    scheduledStartMinutes = 9 * 60,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Ejecución enfocada",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Entregar el resultado principal",
                                    dayOffset = 1,
                                    scheduledStartMinutes = 10 * 60,
                                    durationMinutes = 90
                                ),
                                TemplateTaskBlueprint(
                                    title = "Resolver bloqueos y revisar el avance de mitad de semana",
                                    dayOffset = 2,
                                    durationMinutes = 30
                                )
                            )
                        ),
                        TemplateHeadingBlueprint(
                            title = "Cierre y retrospectiva",
                            tasks = listOf(
                                TemplateTaskBlueprint(
                                    title = "Cierre del viernes y retrospectiva semanal",
                                    checklistItems = listOf("Qué salió bien", "Qué mejorar la próxima semana"),
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
    private val milestoneTokenRegex = Regex("""(?:^|\s)@milestone(?=\s|$)""", RegexOption.IGNORE_CASE)
    private val parenDurationSuffixRegex = Regex("""\(([^()]+)\)\s*$""")
    private val atDurationSuffixRegex = Regex("""(?:^|\s)@([^\s@]+(?:\s+\d+(?:[.,]\d+)?[a-zA-Zäöüßáéíóúàèìòù]+)*)\s*$""")
    private val plainMinSuffixRegex = Regex("""\s+(\d{1,4})\s*m(?:in)?$""", RegexOption.IGNORE_CASE)
    private val startTimeTokenRegex = Regex("""(?:^|\s)@(\d{1,2}):(\d{2})(?=\s|$)""")

    fun parseOutline(rawInput: String, fallbackTitle: String = "Imported Project"): ProjectTemplateBlueprint {
        val resolvedFallback = fallbackTitle.trim().ifBlank { "Imported Project" }
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ProjectTemplateBlueprint(title = resolvedFallback)
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val parsedJson = runCatching {
                val element = ProjectTemplateCodec.json.parseToJsonElement(trimmed)
                ProjectTemplateCodec.parseJsonElementToBlueprint(element, fallbackTitle = resolvedFallback)
            }.getOrNull()
            if (parsedJson != null && (parsedJson.title.isNotBlank() || parsedJson.totalTaskCount > 0 || parsedJson.headings.isNotEmpty())) {
                return parsedJson.copy(title = parsedJson.title.ifBlank { resolvedFallback })
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
                                durationMinutes = parsedMeta.durationMinutes,
                                isMilestone = parsedMeta.isMilestone
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
                            durationMinutes = parsedMeta.durationMinutes,
                            isMilestone = parsedMeta.isMilestone
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
            title = projectTitle.ifBlank { resolvedFallback },
            notes = projectNotesLines.joinToString("\n").trim(),
            unheadedTasks = unheadedTasks.map { it.build() },
            headings = headings.map { it.build() }
        )
    }

    private data class InlineTaskMeta(
        val cleanTitle: String,
        val scheduledStartMinutes: Int?,
        val durationMinutes: Int?,
        val isMilestone: Boolean = false
    )

    private fun extractTaskInlineMetadata(rawTitle: String): InlineTaskMeta {
        var working = rawTitle.trim()
        var startMinutes: Int? = null
        var durationMinutes: Int? = null
        var isMilestone = false

        if (milestoneTokenRegex.containsMatchIn(working)) {
            isMilestone = true
            working = working.replace(milestoneTokenRegex, " ").replace(Regex("""\s+"""), " ").trim()
        }

        val timeMatch = startTimeTokenRegex.find(working)
        if (timeMatch != null) {
            val h = timeMatch.groupValues[1].toIntOrNull()
            val m = timeMatch.groupValues[2].toIntOrNull()
            if (h != null && m != null && h in 0..23 && m in 0..59) {
                startMinutes = h * 60 + m
                working = (working.substring(0, timeMatch.range.first) + " " + working.substring(timeMatch.range.last + 1)).trim()
            }
        }

        val atDurMatch = atDurationSuffixRegex.find(working)
        if (atDurMatch != null) {
            val token = atDurMatch.groupValues[1].trim()
            val parsed = if (token.any { it.isLetter() }) parseCustomDurationMinutes(token) else null
            if (parsed != null && parsed in 1..MAX_TASK_DURATION_MINUTES) {
                durationMinutes = parsed
                working = working.substring(0, atDurMatch.range.first).trim()
            }
        }

        if (durationMinutes == null) {
            val parenMatch = parenDurationSuffixRegex.find(working)
            if (parenMatch != null) {
                val inner = parenMatch.groupValues[1].trim()
                val parsed = if (inner.any { it.isLetter() }) parseCustomDurationMinutes(inner) else null
                if (parsed != null && parsed in 1..MAX_TASK_DURATION_MINUTES) {
                    durationMinutes = parsed
                    working = working.substring(0, parenMatch.range.first).trim()
                }
            }
        }

        if (durationMinutes == null) {
            val plainMatch = plainMinSuffixRegex.find(working)
            if (plainMatch != null) {
                val d = plainMatch.groupValues[1].toIntOrNull()
                if (d != null && d in 1..MAX_TASK_DURATION_MINUTES) {
                    durationMinutes = d
                    working = working.substring(0, plainMatch.range.first).trim()
                }
            }
        }

        return InlineTaskMeta(
            cleanTitle = working.ifBlank { rawTitle.trim() },
            scheduledStartMinutes = startMinutes,
            durationMinutes = durationMinutes,
            isMilestone = isMilestone
        )
    }

    private class MutableTaskBuilder(
        val title: String,
        val scheduledStartMinutes: Int? = null,
        val durationMinutes: Int? = null,
        val isMilestone: Boolean = false,
        val checklistItems: MutableList<String> = mutableListOf()
    ) {
        fun build(): TemplateTaskBlueprint = TemplateTaskBlueprint(
            title = title,
            checklistItems = checklistItems.toList(),
            scheduledStartMinutes = scheduledStartMinutes,
            durationMinutes = durationMinutes,
            isMilestone = isMilestone
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
        val reviewAnchor = if (lastReviewed != null && lastReviewed > 0L) {
            lastReviewed
        } else {
            maxOf(project.updatedAt, project.createdAt)
        }
        val isUnreviewed = reviewAnchor <= 0L ||
            (nowMillis - reviewAnchor) >= FOURTEEN_DAYS_MILLIS

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

    val DURATION_PRESETS_MINUTES: List<Int> = listOf(15, 30, 45, 60, 90, 120, 240, 480)

    fun formatMinutesAsHm(minutesFromMidnight: Int): String {
        val clamped = ((minutesFromMidnight % 1440) + 1440) % 1440
        val h = clamped / 60
        val m = clamped % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun formatTimeblockRange(startMinutes: Int, durationMinutes: Int?): String {
        val dur = (durationMinutes ?: DEFAULT_TIMEBLOCK_DURATION_MINUTES).coerceIn(1, MAX_TASK_DURATION_MINUTES)
        val startStr = formatMinutesAsHm(startMinutes)
        val compactDur = formatDurationCompact(dur)
        if (dur > 480) {
            return "$startStr ($compactDur)"
        }
        val endTotal = startMinutes + dur
        val dayRollover = endTotal / 1440
        val endStr = formatMinutesAsHm(endTotal)
        return if (dayRollover > 0) {
            "$startStr–$endStr +${dayRollover}d ($compactDur)"
        } else {
            "$startStr–$endStr ($compactDur)"
        }
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
            val dur = (task.durationMinutes ?: DEFAULT_TIMEBLOCK_DURATION_MINUTES).coerceIn(1, MAX_TASK_DURATION_MINUTES)
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
