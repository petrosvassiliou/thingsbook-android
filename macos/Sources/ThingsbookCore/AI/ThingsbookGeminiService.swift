import Foundation

public struct BrainDumpTaskDraft: Identifiable, Equatable, Sendable {
    public let id = UUID()
    public var title: String
    public var notes: String
    public var scheduleState: ScheduleState
    public var whenDaysOffset: Int?
    public var deadlineDaysOffset: Int?
    public var checklist: [String]

    public init(
        title: String,
        notes: String = "",
        scheduleState: ScheduleState = .today,
        whenDaysOffset: Int? = 0,
        deadlineDaysOffset: Int? = nil,
        checklist: [String] = []
    ) {
        self.title = title
        self.notes = notes
        self.scheduleState = scheduleState
        self.whenDaysOffset = whenDaysOffset
        self.deadlineDaysOffset = deadlineDaysOffset
        self.checklist = checklist
    }
}

public struct ProjectBlueprintHeading: Identifiable, Equatable, Sendable {
    public let id = UUID()
    public var title: String
    public var tasks: [String]

    public init(title: String, tasks: [String]) {
        self.title = title
        self.tasks = tasks
    }
}

public struct ProjectBlueprintResponse: Equatable, Sendable {
    public var projectTitle: String
    public var projectNotes: String
    public var headings: [ProjectBlueprintHeading]

    public init(projectTitle: String, projectNotes: String, headings: [ProjectBlueprintHeading]) {
        self.projectTitle = projectTitle
        self.projectNotes = projectNotes
        self.headings = headings
    }
}

public struct LogbookReflectionResponse: Equatable, Sendable {
    public var summaryParagraph: String
    public var suggestedFocusTasks: [String]

    public init(summaryParagraph: String, suggestedFocusTasks: [String]) {
        self.summaryParagraph = summaryParagraph
        self.suggestedFocusTasks = suggestedFocusTasks
    }
}

public enum GeminiServiceError: LocalizedError {
    case apiRejected(String)

    public var errorDescription: String? {
        switch self {
        case .apiRejected(let msg): return msg
        }
    }
}

public enum ThingsbookGeminiService {
    private static let apiKeyAccount = "gemini_api_key_v1"

    public static func storedApiKey() -> String {
        KeychainStore.loadString(account: apiKeyAccount) ?? ""
    }

    public static func saveApiKey(_ key: String) {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            KeychainStore.delete(account: apiKeyAccount)
        } else {
            KeychainStore.saveString(trimmed, account: apiKeyAccount)
        }
    }

    public static func generateMagicChecklist(
        taskTitle: String,
        taskNotes: String,
        lang: AppLanguage
    ) async throws -> [String] {
        let safeTitle = String(taskTitle.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500))
        let safeNotes = String(taskNotes.trimmingCharacters(in: .whitespacesAndNewlines).prefix(2000))
        let code = lang.resolvedCode
        let langName = languageDisplayName(code)
        let apiKey = storedApiKey()

        if !apiKey.isEmpty {
            let prompt = """
            You are the calm, precise GTD assistant inside Thingsbook (Things 3 style).
            Respond strictly in \(langName) (Locale: \(code)).
            Break down the following To-Do into 4 to 5 concise, actionable checklist sub-steps.
            To-Do Title: "\(safeTitle)"
            To-Do Notes: "\(safeNotes)"
            Return ONLY valid JSON matching: {"items": ["Step 1", "Step 2", "Step 3", "Step 4"]}
            """
            if let raw = try await callGeminiDirectRest(apiKey: apiKey, prompt: prompt),
               let jsonObj = parseJsonDictionary(raw),
               let items = jsonObj["items"] as? [Any] {
                let clean = items.compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
                if !clean.isEmpty { return clean }
            }
        }

        return offlineLocalizedBreakdown(title: safeTitle, langCode: code)
    }

    public static func parseBrainDumpToTasks(
        rawBrainDump: String,
        lang: AppLanguage
    ) async throws -> [BrainDumpTaskDraft] {
        let safeDump = String(rawBrainDump.trimmingCharacters(in: .whitespacesAndNewlines).prefix(4000))
        let code = lang.resolvedCode
        let langName = languageDisplayName(code)
        let apiKey = storedApiKey()

        if !apiKey.isEmpty {
            let prompt = """
            You are the Thingsbook GTD Brain Dump parser.
            Respond strictly in \(langName) (Locale: \(code)).
            Extract individual actionable To-Dos from the user's brain dump text:
            "\(safeDump)"
            For each task, determine:
            - title (concise action in \(langName))
            - notes (optional context in \(langName))
            - scheduleState: one of INBOX, TODAY, THIS_EVENING, UPCOMING, ANYTIME, SOMEDAY
            - whenDaysOffset: integer days from today (0 for today/this evening, 1 for tomorrow, etc., or null)
            - deadlineDaysOffset: integer days from today if a hard deadline is mentioned, or null
            - checklist: 0 to 3 sub-steps in \(langName)
            Return ONLY valid JSON matching:
            {"tasks": [{"title": "...", "notes": "...", "scheduleState": "TODAY", "whenDaysOffset": 0, "deadlineDaysOffset": null, "checklist": []}]}
            """
            if let raw = try await callGeminiDirectRest(apiKey: apiKey, prompt: prompt),
               let jsonObj = parseJsonDictionary(raw),
               let rawTasks = jsonObj["tasks"] as? [[String: Any]] {
                let mapped: [BrainDumpTaskDraft] = rawTasks.compactMap { t in
                    guard let title = (t["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
                          !title.isEmpty else { return nil }
                    let notes = (t["notes"] as? String) ?? ""
                    let sched = ScheduleState.normalized(t["scheduleState"] as? String, fallback: .today)
                    let whenOff = (t["whenDaysOffset"] as? NSNumber).map { min(3650, max(-365, $0.intValue)) }
                    let deadOff = (t["deadlineDaysOffset"] as? NSNumber).map { min(3650, max(-365, $0.intValue)) }
                    let chk = (t["checklist"] as? [Any] ?? [])
                        .compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }
                        .filter { !$0.isEmpty }
                    return BrainDumpTaskDraft(
                        title: title,
                        notes: notes,
                        scheduleState: sched,
                        whenDaysOffset: whenOff,
                        deadlineDaysOffset: deadOff,
                        checklist: chk
                    )
                }
                if !mapped.isEmpty { return mapped }
            }
        }

        return offlineLocalizedBrainDumpParser(raw: safeDump)
    }

    public static func generateProjectBlueprint(
        goalPrompt: String,
        lang: AppLanguage
    ) async throws -> ProjectBlueprintResponse {
        let safeGoal = String(goalPrompt.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1500))
        let code = lang.resolvedCode
        let langName = languageDisplayName(code)
        let apiKey = storedApiKey()

        if !apiKey.isEmpty {
            let prompt = """
            You are the Thingsbook Project Architect.
            Respond strictly in \(langName) (Locale: \(code)).
            Create a structured Things 3 GTD project for the goal: "\(safeGoal)".
            Include a clear projectTitle, brief inspiring projectNotes, and 3 logical headings, each with 3 concrete tasks.
            Return ONLY valid JSON matching:
            {"projectTitle": "...", "projectNotes": "...", "headings": [{"title": "...", "tasks": ["...", "...", "..."]}]}
            """
            if let raw = try await callGeminiDirectRest(apiKey: apiKey, prompt: prompt),
               let jsonObj = parseJsonDictionary(raw),
               let projTitle = (jsonObj["projectTitle"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
               !projTitle.isEmpty,
               let rawHeadings = jsonObj["headings"] as? [[String: Any]],
               !rawHeadings.isEmpty {
                let headings = rawHeadings.map { h in
                    ProjectBlueprintHeading(
                        title: (h["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "",
                        tasks: (h["tasks"] as? [Any] ?? [])
                            .compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }
                            .filter { !$0.isEmpty }
                    )
                }
                return ProjectBlueprintResponse(
                    projectTitle: projTitle,
                    projectNotes: (jsonObj["projectNotes"] as? String) ?? "",
                    headings: headings
                )
            }
        }

        return offlineLocalizedProjectBlueprint(goal: safeGoal, langCode: code)
    }

    public static func generateLogbookReflection(
        completedTaskTitles: [String],
        lang: AppLanguage
    ) async throws -> LogbookReflectionResponse {
        let code = lang.resolvedCode
        let langName = languageDisplayName(code)
        let apiKey = storedApiKey()

        if !apiKey.isEmpty {
            let joined = completedTaskTitles.prefix(25).map { String($0.prefix(160)) }.joined(separator: "; ")
            let prompt = """
            You are the calm Thingsbook Logbook reflection assistant.
            Respond strictly in \(langName) (Locale: \(code)).
            Completed tasks in Logbook: [\(joined)].
            Write a calm, encouraging 2-sentence reflection summary in \(langName) and suggest 3 next high-leverage focus tasks for Today.
            Return ONLY valid JSON matching:
            {"summaryParagraph": "...", "suggestedFocusTasks": ["...", "...", "..."]}
            """
            if let raw = try await callGeminiDirectRest(apiKey: apiKey, prompt: prompt),
               let jsonObj = parseJsonDictionary(raw),
               let summary = (jsonObj["summaryParagraph"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
               !summary.isEmpty {
                let suggested = (jsonObj["suggestedFocusTasks"] as? [Any] ?? [])
                    .compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
                return LogbookReflectionResponse(summaryParagraph: summary, suggestedFocusTasks: suggested)
            }
        }

        return offlineLocalizedLogbookReflection(count: completedTaskTitles.count, langCode: code)
    }

    // MARK: - REST & Offline Fallbacks

    private static func callGeminiDirectRest(apiKey: String, prompt: String) async throws -> String? {
        let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")!
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        req.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")
        req.httpBody = try JSONSerialization.data(withJSONObject: [
            "contents": [["parts": [["text": prompt]]]],
            "generationConfig": [
                "responseMimeType": "application/json",
                "temperature": 0.35
            ]
        ])

        let (data, res) = try await URLSession.shared.data(for: req)
        if let http = res as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            if [400, 401, 403, 429].contains(http.statusCode) {
                throw GeminiServiceError.apiRejected(
                    http.statusCode == 429
                        ? "Gemini API rate limit reached (HTTP 429)."
                        : "Gemini API key rejected (HTTP \(http.statusCode))."
                )
            }
            return nil
        }

        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let candidates = root["candidates"] as? [[String: Any]],
              let content = candidates.first?["content"] as? [String: Any],
              let parts = content["parts"] as? [[String: Any]] else {
            return nil
        }
        return parts.compactMap { $0["text"] as? String }.first { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    private static func parseJsonDictionary(_ raw: String) -> [String: Any]? {
        let start = raw.firstIndex(of: "{")
        let end = raw.lastIndex(of: "}")
        let slice: String
        if let start, let end, start < end {
            slice = String(raw[start...end])
        } else {
            slice = raw
        }
        guard let data = slice.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    private static func languageDisplayName(_ code: String) -> String {
        switch code {
        case "de": return "German (Deutsch)"
        case "it": return "Italian (Italiano)"
        case "fr": return "French (Français)"
        case "es": return "Spanish (Español)"
        default:   return "English"
        }
    }

    private static func offlineLocalizedBreakdown(title: String, langCode: String) -> [String] {
        let clean = title.isEmpty ? "To-Do" : title
        switch langCode {
        case "de":
            return [
                "Ziel & Rahmenbedingungen für „\(clean)“ klären",
                "Wichtigste Unterlagen und Notizen zusammenstellen",
                "Ersten konkreten Umsetzungsschritt (15 Min. Fokus) starten",
                "Zwischenstand prüfen & offene Punkte abstimmen",
                "Abschluss dokumentieren und im Logbuch abhaken"
            ]
        case "it":
            return [
                "Definire l'obiettivo principale per «\(clean)»",
                "Raccogliere documenti, link e note necessarie",
                "Completare il primo passo operativo (15 min di focus)",
                "Verificare i dettagli e confermare le scadenze",
                "Concludere e archiviare nel Registro"
            ]
        case "fr":
            return [
                "Clarifier l'objectif principal pour « \(clean) »",
                "Rassembler les documents et notes essentiels",
                "Lancer la première étape concrète (15 min de concentration)",
                "Vérifier l'avancement et ajuster les détails",
                "Finaliser et valider dans le Journal"
            ]
        case "es":
            return [
                "Definir el objetivo clave para «\(clean)»",
                "Reunir notas, enlaces y materiales necesarios",
                "Completar el primer paso práctico (15 min de enfoque)",
                "Revisar avances y confirmar fechas límite",
                "Finalizar y registrar en el Registro"
            ]
        default:
            return [
                "Clarify desired outcome and scope for \"\(clean)\"",
                "Gather key notes, links, and required materials",
                "Execute the first 15-minute focused action step",
                "Review progress and align any dependencies",
                "Finalize details and mark complete in Logbook"
            ]
        }
    }

    private static func offlineLocalizedBrainDumpParser(raw: String) -> [BrainDumpTaskDraft] {
        let replaced = raw.replacingOccurrences(
            of: #"\b(und|and|et|e|y|sowie|plus)\b"#,
            with: ",",
            options: [.regularExpression, .caseInsensitive]
        )
        let segments = replaced
            .components(separatedBy: CharacterSet(charactersIn: ",;\n"))
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.count >= 3 }

        return segments.map { seg in
            let lower = seg.lowercased()
            var sched: ScheduleState = .today
            var whenOffset: Int? = 0

            if lower.range(of: #"heute abend|this evening|tonight|stasera|ce soir|esta noche"#, options: .regularExpression) != nil {
                sched = .thisEvening
                whenOffset = 0
            } else if lower.range(of: #"morgen|tomorrow|domani|demain|mañana|manana"#, options: .regularExpression) != nil {
                sched = .upcoming
                whenOffset = 1
            } else if lower.range(of: #"irgendwann|someday|prima o poi|un jour|algún día|algun dia"#, options: .regularExpression) != nil {
                sched = .someday
                whenOffset = nil
            }
            let deadOffset: Int? = lower.range(of: #"frist|bis |deadline|by |entro|avant|para el"#, options: .regularExpression) != nil ? 3 : nil
            let capitalized = seg.prefix(1).uppercased() + seg.dropFirst()
            return BrainDumpTaskDraft(
                title: capitalized,
                notes: "",
                scheduleState: sched,
                whenDaysOffset: whenOffset,
                deadlineDaysOffset: deadOffset,
                checklist: []
            )
        }
    }

    private static func offlineLocalizedProjectBlueprint(goal: String, langCode: String) -> ProjectBlueprintResponse {
        let cleanGoal = goal.isEmpty ? "Thingsbook Project" : goal
        if langCode == "de" {
            return ProjectBlueprintResponse(
                projectTitle: cleanGoal,
                projectNotes: "Von Thingsbook Gemini strukturierter GTD-Projektplan.",
                headings: [
                    ProjectBlueprintHeading(
                        title: "1. Konzeption & Vorbereitung",
                        tasks: [
                            "Ziele und Erfolgskriterien für „\(cleanGoal)“ festlegen",
                            "Zeitplan, Budget und Meilensteine skizzieren",
                            "Wichtigste Ressourcen und Ansprechpartner sammeln"
                        ]
                    ),
                    ProjectBlueprintHeading(
                        title: "2. Fokus-Umsetzung",
                        tasks: [
                            "Kernaufgaben mit höchster Hebelwirkung bearbeiten",
                            "Ersten Prototyp oder Entwurf fertigstellen",
                            "Zwischenergebnisse prüfen und feinschleifen"
                        ]
                    ),
                    ProjectBlueprintHeading(
                        title: "3. Abschluss & Review",
                        tasks: [
                            "Finale Qualitätskontrolle durchführen",
                            "Projekt live schalten / übergeben",
                            "Learnings im Thingsbook Logbuch festhalten"
                        ]
                    )
                ]
            )
        }
        return ProjectBlueprintResponse(
            projectTitle: cleanGoal,
            projectNotes: "Calm GTD project blueprint structured by Thingsbook Intelligence.",
            headings: [
                ProjectBlueprintHeading(
                    title: "1. Discovery & Preparation",
                    tasks: [
                        "Define clear success criteria for \"\(cleanGoal)\"",
                        "Gather essential resources, timeline, and notes",
                        "Schedule the first high-leverage milestone"
                    ]
                ),
                ProjectBlueprintHeading(
                    title: "2. Deep Execution",
                    tasks: [
                        "Complete core deliverables during focus blocks",
                        "Review mid-project progress and refine scope",
                        "Polish key details and edge cases"
                    ]
                ),
                ProjectBlueprintHeading(
                    title: "3. Final Review & Launch",
                    tasks: [
                        "Run final quality checklist",
                        "Deliver outcome and celebrate completion",
                        "Archive completed project in Thingsbook Logbook"
                    ]
                )
            ]
        )
    }

    private static func offlineLocalizedLogbookReflection(count: Int, langCode: String) -> LogbookReflectionResponse {
        if langCode == "de" {
            return LogbookReflectionResponse(
                summaryParagraph: "Du hast \(count) Aufgaben ruhig und fokussiert abgeschlossen. Dein Thingsbook-Workflow zeigt klare Struktur zwischen Projekten und täglichen Prioritäten.",
                suggestedFocusTasks: [
                    "Wichtigste offene Aufgabe in „Heute“ als Erstes abschließen",
                    "Eingang (Inbox) in 2 Minuten auf null bringen",
                    "Einen entspannten Punkt für „Heute Abend“ einplanen"
                ]
            )
        }
        return LogbookReflectionResponse(
            summaryParagraph: "You have calmly completed \(count) To-Dos in your Logbook. Your momentum across core projects and daily focus blocks is steady and well-balanced.",
            suggestedFocusTasks: [
                "Complete your #1 high-leverage To-Do in Today",
                "Process any remaining Inbox items to zero",
                "Set aside one calm reflection item for This Evening"
            ]
        )
    }
}
