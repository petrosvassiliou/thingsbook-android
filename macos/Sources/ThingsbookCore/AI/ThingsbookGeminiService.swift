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

public struct RoadmapTaskBlueprintDTO: Identifiable, Codable, Equatable, Hashable, Sendable {
    public var id: String { key.isEmpty ? title : key }
    public var key: String
    public var title: String
    public var notes: String
    public var dayOffset: Int
    public var durationMinutes: Int
    public var effortLevel: String?
    public var isMilestone: Bool
    public var dependsOnKeys: [String]
    public var checklist: [String]

    public init(
        key: String = "",
        title: String,
        notes: String = "",
        dayOffset: Int = 0,
        durationMinutes: Int = 60,
        effortLevel: String? = "MEDIUM",
        isMilestone: Bool = false,
        dependsOnKeys: [String] = [],
        checklist: [String] = []
    ) {
        self.key = key
        self.title = title
        self.notes = notes
        self.dayOffset = dayOffset
        self.durationMinutes = durationMinutes
        self.effortLevel = effortLevel
        self.isMilestone = isMilestone
        self.dependsOnKeys = dependsOnKeys
        self.checklist = checklist
    }
}

public struct ProjectBlueprintHeading: Identifiable, Codable, Equatable, Sendable {
    public let id: UUID
    public var title: String
    public var tasks: [String]
    public var startDayOffset: Int?
    public var endDayOffset: Int?
    public var richTasks: [RoadmapTaskBlueprintDTO]

    public init(
        id: UUID = UUID(),
        title: String,
        tasks: [String],
        startDayOffset: Int? = nil,
        endDayOffset: Int? = nil,
        richTasks: [RoadmapTaskBlueprintDTO] = []
    ) {
        self.id = id
        self.title = title
        self.tasks = tasks
        self.startDayOffset = startDayOffset
        self.endDayOffset = endDayOffset
        self.richTasks = richTasks
    }

    private enum CodingKeys: String, CodingKey {
        case title, tasks, startDayOffset, endDayOffset, richTasks
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = UUID()
        self.title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        self.tasks = try c.decodeIfPresent([String].self, forKey: .tasks) ?? []
        self.startDayOffset = try c.decodeIfPresent(Int.self, forKey: .startDayOffset)
        self.endDayOffset = try c.decodeIfPresent(Int.self, forKey: .endDayOffset)
        self.richTasks = try c.decodeIfPresent([RoadmapTaskBlueprintDTO].self, forKey: .richTasks) ?? []
    }
}

public typealias BlueprintHeadingDTO = ProjectBlueprintHeading

public struct ProjectBlueprintResponse: Codable, Equatable, Sendable {
    public var projectTitle: String
    public var projectNotes: String
    public var headings: [ProjectBlueprintHeading]

    public init(projectTitle: String, projectNotes: String, headings: [ProjectBlueprintHeading]) {
        self.projectTitle = projectTitle
        self.projectNotes = projectNotes
        self.headings = headings
    }
}

public struct DayTimeblockCandidate: Equatable, Sendable {
    public var taskId: String
    public var title: String
    public var durationMinutes: Int
    public var effortLevel: String?
    public var isPinned: Bool
    public var hasTodayDeadline: Bool
    public var existingStartMinutes: Int?

    public init(
        taskId: String,
        title: String,
        durationMinutes: Int,
        effortLevel: String? = nil,
        isPinned: Bool = false,
        hasTodayDeadline: Bool = false,
        existingStartMinutes: Int? = nil
    ) {
        self.taskId = taskId
        self.title = title
        self.durationMinutes = durationMinutes
        self.effortLevel = effortLevel
        self.isPinned = isPinned
        self.hasTodayDeadline = hasTodayDeadline
        self.existingStartMinutes = existingStartMinutes
    }
}

public struct DayTimeblockItemDTO: Identifiable, Codable, Equatable, Hashable, Sendable {
    public var id: String { taskId }
    public var taskId: String
    public var startMinutes: Int
    public var durationMinutes: Int
    public var reason: String

    public init(
        taskId: String,
        startMinutes: Int,
        durationMinutes: Int,
        reason: String = ""
    ) {
        self.taskId = taskId
        self.startMinutes = startMinutes
        self.durationMinutes = durationMinutes
        self.reason = reason
    }
}

public struct DayTimeblockPlanResponse: Codable, Equatable, Sendable {
    public var summary: String
    public var blocks: [DayTimeblockItemDTO]

    public init(summary: String = "", blocks: [DayTimeblockItemDTO] = []) {
        self.summary = summary
        self.blocks = blocks
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
            You are the Thingsbook v3.1 Roadmap Architect.
            Respond strictly in \(langName) (Locale: \(code)).
            Create a structured GTD + Gantt project roadmap for the goal: "\(safeGoal)".
            Include:
            - projectTitle (clear outcome title in \(langName))
            - projectNotes (1–2 inspiring sentences in \(langName))
            - 3 logical phases (`headings`) spanning a realistic 2 to 4 week schedule starting at dayOffset 0:
              - title (phase title in \(langName))
              - startDayOffset (integer >= 0)
              - endDayOffset (integer >= startDayOffset)
              - tasks: list of plain task titles (mirroring richTasks titles for compatibility)
              - richTasks: 3 to 4 structured items per phase, including at least 1 milestone (`isMilestone: true`) per project:
                - key: unique short id like "t1", "t2", ...
                - title: concise action or milestone name in \(langName)
                - notes: brief context in \(langName) (optional)
                - dayOffset: integer >= phase startDayOffset
                - durationMinutes: realistic estimate (30, 60, 120, 240, or 480; 30 for milestones)
                - effortLevel: one of "QUICK_WIN", "MEDIUM", "DEEP_FOCUS"
                - isMilestone: boolean (true for key phase deliverables ◆)
                - dependsOnKeys: array of upstream task `key` strings when strictly sequential (e.g. ["t1"])
                - checklist: 0 to 3 sub-steps in \(langName)
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
            """
            if let raw = try await callGeminiDirectRest(apiKey: apiKey, prompt: prompt),
               let jsonObj = parseJsonDictionary(raw),
               let projTitle = (jsonObj["projectTitle"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
               !projTitle.isEmpty,
               let rawHeadings = jsonObj["headings"] as? [[String: Any]],
               !rawHeadings.isEmpty {
                let headings: [ProjectBlueprintHeading] = rawHeadings.enumerated().map { hIdx, h in
                    let headingTitle = (h["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                    let plainTasks = (h["tasks"] as? [Any] ?? [])
                        .compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }
                        .filter { !$0.isEmpty }
                    let parsedRich: [RoadmapTaskBlueprintDTO] = (h["richTasks"] as? [[String: Any]] ?? []).compactMap { rt in
                        guard let rtTitle = (rt["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
                              !rtTitle.isEmpty else { return nil }
                        let key = (rt["key"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                        let notes = (rt["notes"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                        let dayOff = (rt["dayOffset"] as? NSNumber)?.intValue ?? (hIdx * 4)
                        let dur = (rt["durationMinutes"] as? NSNumber)?.intValue ?? 60
                        let effort = (rt["effortLevel"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
                        let isMs = (rt["isMilestone"] as? Bool) ?? ((rt["isMilestone"] as? NSNumber)?.boolValue ?? false)
                        let deps = (rt["dependsOnKeys"] as? [Any] ?? [])
                            .compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }
                            .filter { !$0.isEmpty }
                        let chk = (rt["checklist"] as? [Any] ?? [])
                            .compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }
                            .filter { !$0.isEmpty }
                        return RoadmapTaskBlueprintDTO(
                            key: key,
                            title: rtTitle,
                            notes: notes,
                            dayOffset: max(0, dayOff),
                            durationMinutes: max(15, dur),
                            effortLevel: effort,
                            isMilestone: isMs,
                            dependsOnKeys: deps,
                            checklist: chk
                        )
                    }

                    let resolvedTasks = !plainTasks.isEmpty ? plainTasks : parsedRich.map(\.title)
                    let resolvedRich: [RoadmapTaskBlueprintDTO]
                    if !parsedRich.isEmpty {
                        resolvedRich = parsedRich
                    } else {
                        resolvedRich = resolvedTasks.enumerated().map { tIdx, tTitle in
                            let isMs = (hIdx == rawHeadings.count - 1 && tIdx == resolvedTasks.count - 1)
                            return RoadmapTaskBlueprintDTO(
                                key: "h\(hIdx)_t\(tIdx)",
                                title: tTitle,
                                dayOffset: hIdx * 4 + tIdx,
                                durationMinutes: isMs ? 30 : 120,
                                effortLevel: tIdx == 0 ? "DEEP_FOCUS" : "MEDIUM",
                                isMilestone: isMs
                            )
                        }
                    }

                    let inferredStart = resolvedRich.map(\.dayOffset).min() ?? (hIdx * 4)
                    let inferredEnd = resolvedRich.map { $0.dayOffset + max(0, ($0.durationMinutes - 1) / 480) }.max() ?? (inferredStart + 3)
                    let startOff = (h["startDayOffset"] as? NSNumber)?.intValue ?? inferredStart
                    let endOff = max(startOff, (h["endDayOffset"] as? NSNumber)?.intValue ?? inferredEnd)

                    return ProjectBlueprintHeading(
                        title: headingTitle,
                        tasks: resolvedTasks,
                        startDayOffset: startOff,
                        endDayOffset: endOff,
                        richTasks: resolvedRich
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

    public static func generateDailyTimeblockPlan(
        candidates: [DayTimeblockCandidate],
        busyIntervalsMinutes: [(Int, Int)],
        workdayStartMinutes: Int = 540,
        workdayEndMinutes: Int = 1020,
        earliestStartMinutes: Int? = nil,
        lang: AppLanguage = .system
    ) async throws -> DayTimeblockPlanResponse {
        guard !candidates.isEmpty else {
            return DayTimeblockPlanResponse(summary: "", blocks: [])
        }
        let code = lang.resolvedCode
        let langName = languageDisplayName(code)
        let safeWorkStart = min(22 * 60, max(0, workdayStartMinutes))
        let safeWorkEnd = min(24 * 60, max(safeWorkStart + 60, workdayEndMinutes))
        let effectiveStart = min(23 * 60, max(safeWorkStart, earliestStartMinutes ?? safeWorkStart))

        let fallback = buildDeterministicDayTimeblockPlan(
            candidates: candidates,
            busyIntervalsMinutes: busyIntervalsMinutes,
            workdayStartMinutes: safeWorkStart,
            workdayEndMinutes: safeWorkEnd,
            earliestStartMinutes: effectiveStart,
            langCode: code
        )

        let apiKey = storedApiKey()
        if !apiKey.isEmpty {
            let busyDesc: String = busyIntervalsMinutes.isEmpty
                ? "none"
                : busyIntervalsMinutes.map { "\(formatClock($0.0))-\(formatClock($0.1))" }.joined(separator: ", ")
            let tasksDesc = candidates.prefix(20).map { c in
                "- id=\"\(c.taskId)\", title=\"\(String(c.title.prefix(120)))\", durationMin=\(c.durationMinutes), effort=\(c.effortLevel ?? "MEDIUM"), pinned=\(c.isPinned), dueToday=\(c.hasTodayDeadline)"
            }.joined(separator: "\n")

            let prompt = """
            You are the Thingsbook "Plan My Day" Focus Timeblocker.
            Respond strictly in \(langName) (Locale: \(code)).
            Workday window: \(formatClock(effectiveStart)) (\(effectiveStart)m) to \(formatClock(safeWorkEnd)) (\(safeWorkEnd)m).
            Busy calendar/locked intervals (DO NOT overlap): \(busyDesc).
            Open Today tasks to schedule:
            \(tasksDesc)
            Rules:
            1. Place tasks in 15-minute aligned startMinutes (0..1425) without overlapping busy intervals or each other.
            2. Prioritize pinned and dueToday tasks first, DEEP_FOCUS in longer uninterrupted morning/early blocks, and QUICK_WIN in short gaps.
            3. Keep durationMinutes realistic (15..240). Include a short 3-8 word `reason` in \(langName).
            Return ONLY valid JSON matching:
            {"summary": "...", "blocks": [{"taskId": "...", "startMinutes": 540, "durationMinutes": 60, "reason": "..."}]}
            """
            if let raw = try await callGeminiDirectRest(apiKey: apiKey, prompt: prompt),
               let jsonObj = parseJsonDictionary(raw),
               let rawBlocks = jsonObj["blocks"] as? [[String: Any]] {
                let validIds = Set(candidates.map(\.taskId))
                let parsedBlocks: [DayTimeblockItemDTO] = rawBlocks.compactMap { b in
                    guard let tid = (b["taskId"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
                          validIds.contains(tid),
                          let startNum = b["startMinutes"] as? NSNumber else {
                        return nil
                    }
                    let durNum = (b["durationMinutes"] as? NSNumber)?.intValue ?? 30
                    let reason = (b["reason"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                    return DayTimeblockItemDTO(
                        taskId: tid,
                        startMinutes: startNum.intValue,
                        durationMinutes: durNum,
                        reason: reason
                    )
                }
                let sanitized = sanitizeProposedTimeblocks(
                    proposed: parsedBlocks,
                    busyIntervalsMinutes: busyIntervalsMinutes,
                    earliestStartMinutes: effectiveStart
                )
                if !sanitized.isEmpty {
                    let rawSummary = (jsonObj["summary"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
                    return DayTimeblockPlanResponse(
                        summary: (rawSummary?.isEmpty == false) ? rawSummary! : fallback.summary,
                        blocks: sanitized
                    )
                }
            }
        }

        return fallback
    }

    public static func buildDeterministicDayTimeblockPlan(
        candidates: [DayTimeblockCandidate],
        busyIntervalsMinutes: [(Int, Int)],
        workdayStartMinutes: Int = 540,
        workdayEndMinutes: Int = 1020,
        earliestStartMinutes: Int = 540,
        langCode: String = "en"
    ) -> DayTimeblockPlanResponse {
        guard !candidates.isEmpty else {
            return DayTimeblockPlanResponse(summary: "", blocks: [])
        }
        let sortedCandidates = candidates.sorted { lhs, rhs in
            if lhs.isPinned != rhs.isPinned { return lhs.isPinned && !rhs.isPinned }
            if lhs.hasTodayDeadline != rhs.hasTodayDeadline { return lhs.hasTodayDeadline && !rhs.hasTodayDeadline }
            let lEffort = effortSortRank(lhs.effortLevel)
            let rEffort = effortSortRank(rhs.effortLevel)
            if lEffort != rEffort { return lEffort < rEffort }
            if lhs.durationMinutes != rhs.durationMinutes { return lhs.durationMinutes > rhs.durationMinutes }
            return lhs.taskId < rhs.taskId
        }

        var occupied = busyIntervalsMinutes
            .filter { $0.1 > $0.0 }
            .sorted { $0.0 < $1.0 }

        var blocks: [DayTimeblockItemDTO] = []
        let clampedEarliest = min(23 * 60, max(0, max(workdayStartMinutes, earliestStartMinutes)))
        let cursorFloor = ((clampedEarliest + 14) / 15) * 15

        for cand in sortedCandidates {
            let dur = min(240, max(15, cand.durationMinutes))
            var slotStart = cursorFloor
            var placed = false
            var guardCount = 0
            while guardCount < 96 && slotStart + dur <= 24 * 60 {
                let slotEnd = slotStart + dur
                if let conflict = occupied.first(where: { slotStart < $0.1 && $0.0 < slotEnd }) {
                    slotStart = ((conflict.1 + 14) / 15) * 15
                    guardCount += 1
                } else {
                    placed = true
                    break
                }
            }
            if placed {
                occupied.append((slotStart, slotStart + dur))
                occupied.sort { $0.0 < $1.0 }
                let reason = localizedTimeblockReason(
                    candidate: cand,
                    withinWorkday: slotStart < workdayEndMinutes,
                    langCode: langCode
                )
                blocks.append(
                    DayTimeblockItemDTO(
                        taskId: cand.taskId,
                        startMinutes: slotStart,
                        durationMinutes: dur,
                        reason: reason
                    )
                )
            }
        }

        let sortedBlocks = blocks.sorted { $0.startMinutes < $1.startMinutes }
        let summary: String
        switch langCode {
        case "de":
            summary = "\(sortedBlocks.count) fokussierte Zeitblöcke konfliktfrei um deine Termine eingeplant."
        case "fr":
            summary = "\(sortedBlocks.count) créneaux planifiés sans conflit autour de votre agenda."
        case "it":
            summary = "\(sortedBlocks.count) blocchi orari pianificati senza conflitti attorno ai tuoi impegni."
        case "es":
            summary = "\(sortedBlocks.count) bloques de enfoque programados sin conflictos alrededor de tu agenda."
        default:
            summary = "Scheduled \(sortedBlocks.count) conflict-free focus blocks around your calendar."
        }
        return DayTimeblockPlanResponse(summary: summary, blocks: sortedBlocks)
    }

    public static func sanitizeProposedTimeblocks(
        proposed: [DayTimeblockItemDTO],
        busyIntervalsMinutes: [(Int, Int)],
        earliestStartMinutes: Int
    ) -> [DayTimeblockItemDTO] {
        guard !proposed.isEmpty else { return [] }
        var occupied = busyIntervalsMinutes.filter { $0.1 > $0.0 }
        var result: [DayTimeblockItemDTO] = []
        var seenIds = Set<String>()

        for item in proposed.sorted(by: { $0.startMinutes < $1.startMinutes }) {
            guard seenIds.insert(item.taskId).inserted else { continue }
            let dur = ((min(480, max(15, item.durationMinutes)) + 4) / 5) * 5
            let baseStart = min(1425, max(0, max(earliestStartMinutes, item.startMinutes)))
            var start = ((baseStart + 7) / 15) * 15
            var guardCount = 0
            while guardCount < 96 && start + dur <= 24 * 60 {
                let end = start + dur
                if let conflict = occupied.first(where: { start < $0.1 && $0.0 < end }) {
                    start = ((conflict.1 + 14) / 15) * 15
                    guardCount += 1
                } else {
                    break
                }
            }
            if start + dur <= 24 * 60 {
                occupied.append((start, start + dur))
                result.append(
                    DayTimeblockItemDTO(
                        taskId: item.taskId,
                        startMinutes: start,
                        durationMinutes: dur,
                        reason: item.reason
                    )
                )
            }
        }
        return result.sorted { $0.startMinutes < $1.startMinutes }
    }

    private static func effortSortRank(_ effortLevel: String?) -> Int {
        switch effortLevel?.uppercased() {
        case "DEEP_FOCUS": return 0
        case "MEDIUM": return 1
        case "QUICK_WIN": return 2
        default: return 1
        }
    }

    private static func formatClock(_ minutesFromMidnight: Int) -> String {
        let clamped = min(1440, max(0, minutesFromMidnight))
        if clamped >= 1440 { return "24:00" }
        return String(format: "%02d:%02d", clamped / 60, clamped % 60)
    }

    private static func localizedTimeblockReason(
        candidate: DayTimeblockCandidate,
        withinWorkday: Bool,
        langCode: String
    ) -> String {
        let effort = candidate.effortLevel?.uppercased()
        switch langCode {
        case "de":
            if candidate.isPinned || candidate.hasTodayDeadline { return "Top-Priorität im freien Fokusfenster" }
            if effort == "DEEP_FOCUS" { return "Ungestörter Fokusblock" }
            if effort == "QUICK_WIN" { return "Kompakter Quick-Win zwischen Terminen" }
            return withinWorkday ? "Passendes freies Arbeitszeitfenster" : "Abendblock nach den Kernzeiten"
        case "fr":
            if candidate.isPinned || candidate.hasTodayDeadline { return "Priorité placée sur le premier créneau libre" }
            if effort == "DEEP_FOCUS" { return "Créneau de concentration profonde" }
            if effort == "QUICK_WIN" { return "Action rapide entre deux rendez-vous" }
            return withinWorkday ? "Créneau libre dans vos horaires" : "Créneau de fin de journée"
        case "it":
            if candidate.isPinned || candidate.hasTodayDeadline { return "Priorità alta nel primo spazio libero" }
            if effort == "DEEP_FOCUS" { return "Blocco di concentrazione profonda" }
            if effort == "QUICK_WIN" { return "Attività rapida tra gli impegni" }
            return withinWorkday ? "Finestra libera nell'orario di lavoro" : "Blocco serale fuori orario"
        case "es":
            if candidate.isPinned || candidate.hasTodayDeadline { return "Prioridad clave en el primer hueco libre" }
            if effort == "DEEP_FOCUS" { return "Bloque de concentración profunda" }
            if effort == "QUICK_WIN" { return "Tarea rápida entre reuniones" }
            return withinWorkday ? "Hueco libre dentro de tu jornada" : "Bloque vespertino adicional"
        default:
            if candidate.isPinned || candidate.hasTodayDeadline { return "Priority slot in your earliest free window" }
            if effort == "DEEP_FOCUS" { return "Uninterrupted deep-focus window" }
            if effort == "QUICK_WIN" { return "Quick win fitted cleanly into free gap" }
            return withinWorkday ? "Balanced slot within workday hours" : "Overflow slot after standard workday"
        }
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

    public static func offlineLocalizedProjectBlueprint(goal: String, langCode: String) -> ProjectBlueprintResponse {
        let cleanGoal = goal.isEmpty ? "Thingsbook Project" : goal
        let base: ProjectBlueprintResponse
        switch langCode {
        case "de":
            base = ProjectBlueprintResponse(
                projectTitle: cleanGoal,
                projectNotes: "Von Thingsbook Gemini strukturierter v3-Roadmap-Projektplan.",
                headings: [
                    ProjectBlueprintHeading(
                        title: "1. Konzeption & Vorbereitung",
                        tasks: [
                            "Ziele und Erfolgskriterien für „\(cleanGoal)“ festlegen",
                            "Zeitplan, Budget und Ressourcen abstimmen",
                            "Konzept-Meilenstein freigeben"
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
                        title: "3. Abschluss & Launch",
                        tasks: [
                            "Finale Qualitätskontrolle durchführen",
                            "Übergabe- und Kommunikationsplan abschließen",
                            "Projekt erfolgreich live schalten"
                        ]
                    )
                ]
            )
        case "fr":
            base = ProjectBlueprintResponse(
                projectTitle: cleanGoal,
                projectNotes: "Feuille de route GTD structurée avec jalons et phases planifiées.",
                headings: [
                    ProjectBlueprintHeading(
                        title: "1. Cadrage & Préparation",
                        tasks: [
                            "Définir les critères de réussite pour « \(cleanGoal) »",
                            "Rassembler les ressources, notes et échéances clés",
                            "Valider le jalon d'architecture initiale"
                        ]
                    ),
                    ProjectBlueprintHeading(
                        title: "2. Exécution Approfondie",
                        tasks: [
                            "Réaliser les livrables principaux en blocs de concentration",
                            "Examiner l'avancement à mi-parcours et ajuster le périmètre",
                            "Soigner les finitions et cas limites"
                        ]
                    ),
                    ProjectBlueprintHeading(
                        title: "3. Revue Finale & Lancement",
                        tasks: [
                            "Exécuter la liste finale de contrôle qualité",
                            "Préparer l'annonce et la documentation de livraison",
                            "Lancer le projet et célébrer l'étape"
                        ]
                    )
                ]
            )
        case "it":
            base = ProjectBlueprintResponse(
                projectTitle: cleanGoal,
                projectNotes: "Roadmap di progetto GTD strutturata con fasi datate e traguardi.",
                headings: [
                    ProjectBlueprintHeading(
                        title: "1. Analisi & Preparazione",
                        tasks: [
                            "Definire i criteri di successo per «\(cleanGoal)»",
                            "Raccogliere risorse essenziali, note e tempistiche",
                            "Approvare il traguardo di impostazione iniziale"
                        ]
                    ),
                    ProjectBlueprintHeading(
                        title: "2. Esecuzione Concentrata",
                        tasks: [
                            "Completare i risultati principali in blocchi di focus",
                            "Verificare i progressi intermedi e affinare l'ambito",
                            "Rifinire i dettagli chiave e i casi limite"
                        ]
                    ),
                    ProjectBlueprintHeading(
                        title: "3. Revisione Finale & Lancio",
                        tasks: [
                            "Eseguire la checklist finale di qualità",
                            "Preparare il rilascio e le note conclusive",
                            "Consegnare il risultato finale"
                        ]
                    )
                ]
            )
        case "es":
            base = ProjectBlueprintResponse(
                projectTitle: cleanGoal,
                projectNotes: "Hoja de ruta GTD estructurada con fases fechadas, hitos y dependencias.",
                headings: [
                    ProjectBlueprintHeading(
                        title: "1. Definición y Preparación",
                        tasks: [
                            "Definir criterios claros de éxito para «\(cleanGoal)»",
                            "Reunir recursos esenciales, cronograma y notas",
                            "Aprobar el hito de diseño inicial"
                        ]
                    ),
                    ProjectBlueprintHeading(
                        title: "2. Ejecución Profunda",
                        tasks: [
                            "Completar entregables clave en bloques de enfoque",
                            "Revisar avances intermedios y ajustar el alcance",
                            "Pulir detalles principales y casos borde"
                        ]
                    ),
                    ProjectBlueprintHeading(
                        title: "3. Revisión Final y Lanzamiento",
                        tasks: [
                            "Ejecutar lista final de control de calidad",
                            "Preparar entrega y comunicación de cierre",
                            "Publicar resultado y cerrar el hito final"
                        ]
                    )
                ]
            )
        default:
            base = ProjectBlueprintResponse(
                projectTitle: cleanGoal,
                projectNotes: "Calm GTD project roadmap structured by Thingsbook Intelligence.",
                headings: [
                    ProjectBlueprintHeading(
                        title: "1. Discovery & Preparation",
                        tasks: [
                            "Define clear success criteria for \"\(cleanGoal)\"",
                            "Gather essential resources, timeline, and notes",
                            "Approve initial scope milestone"
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
                            "Prepare launch handoff and notes",
                            "Deliver outcome and celebrate launch milestone"
                        ]
                    )
                ]
            )
        }
        return enrichOfflineBlueprintWithRoadmapMetadata(base, langCode: langCode)
    }

    private static func enrichOfflineBlueprintWithRoadmapMetadata(
        _ base: ProjectBlueprintResponse,
        langCode: String
    ) -> ProjectBlueprintResponse {
        var runningDay = 0
        var prevMilestoneKey: String? = nil

        let defaultSubsteps: [String] = {
            switch langCode {
            case "de": return ["Anforderungen prüfen", "Ergebnis dokumentieren"]
            case "fr": return ["Vérifier les prérequis", "Documenter le résultat"]
            case "it": return ["Verificare i requisiti", "Documentare il risultato"]
            case "es": return ["Verificar requisitos", "Documentar el resultado"]
            default:   return ["Verify key requirements", "Document outcome"]
            }
        }()

        let enrichedHeadings = base.headings.enumerated().map { hIdx, h in
            let phaseStart = runningDay
            let rich: [RoadmapTaskBlueprintDTO] = h.tasks.enumerated().map { tIdx, title in
                let key = "p\(hIdx + 1)_t\(tIdx + 1)"
                let isLastInPhase = (tIdx == h.tasks.count - 1)
                let isMilestone = isLastInPhase && (hIdx == 0 || hIdx == base.headings.count - 1)
                let dur: Int = isMilestone ? 30 : (tIdx == 0 ? 240 : 120)
                let effort: String = isMilestone ? "QUICK_WIN" : (tIdx == 0 ? "DEEP_FOCUS" : "MEDIUM")
                var deps: [String] = []
                if tIdx > 0 {
                    deps.append("p\(hIdx + 1)_t\(tIdx)")
                } else if let prevMs = prevMilestoneKey {
                    deps.append(prevMs)
                }
                let itemDay = runningDay
                runningDay += isMilestone ? 1 : 2
                if isMilestone {
                    prevMilestoneKey = key
                }
                return RoadmapTaskBlueprintDTO(
                    key: key,
                    title: title,
                    notes: "",
                    dayOffset: itemDay,
                    durationMinutes: dur,
                    effortLevel: effort,
                    isMilestone: isMilestone,
                    dependsOnKeys: deps,
                    checklist: (tIdx == 0 && !isMilestone) ? defaultSubsteps : []
                )
            }
            let phaseEnd = max(phaseStart, runningDay - 1)
            return ProjectBlueprintHeading(
                id: h.id,
                title: h.title,
                tasks: h.tasks,
                startDayOffset: phaseStart,
                endDayOffset: phaseEnd,
                richTasks: rich
            )
        }
        return ProjectBlueprintResponse(
            projectTitle: base.projectTitle,
            projectNotes: base.projectNotes,
            headings: enrichedHeadings
        )
    }

    public static func offlineLocalizedLogbookReflection(count: Int, langCode: String) -> LogbookReflectionResponse {
        switch langCode {
        case "de":
            return LogbookReflectionResponse(
                summaryParagraph: "Du hast \(count) Aufgaben ruhig und fokussiert abgeschlossen. Dein Thingsbook-Workflow zeigt klare Struktur zwischen Projekten und täglichen Prioritäten.",
                suggestedFocusTasks: [
                    "Wichtigste offene Aufgabe in „Heute“ als Erstes abschließen",
                    "Eingang (Inbox) in 2 Minuten auf null bringen",
                    "Einen entspannten Punkt für „Heute Abend“ einplanen"
                ]
            )
        case "fr":
            return LogbookReflectionResponse(
                summaryParagraph: "Vous avez terminé sereinement \(count) tâches dans votre Journal. Votre rythme entre projets de fond et priorités quotidiennes reste fluide et équilibré.",
                suggestedFocusTasks: [
                    "Terminer votre tâche prioritaire n° 1 dans Aujourd'hui",
                    "Traiter les éléments restants de la Boîte de réception",
                    "Prévoir une tâche calme pour Ce soir"
                ]
            )
        case "it":
            return LogbookReflectionResponse(
                summaryParagraph: "Hai completato con calma \(count) attività nel tuo Registro. Il ritmo tra progetti principali e blocchi di concentrazione giornalieri è solido ed equilibrato.",
                suggestedFocusTasks: [
                    "Completare l'attività prioritaria n. 1 in Oggi",
                    "Azzerare gli elementi rimasti in Entrata",
                    "Pianificare un'attività serena per Stasera"
                ]
            )
        case "es":
            return LogbookReflectionResponse(
                summaryParagraph: "Has completado con calma \(count) tareas en tu Registro. Tu ritmo entre proyectos principales y bloques de enfoque diarios se mantiene constante y equilibrado.",
                suggestedFocusTasks: [
                    "Completar tu tarea prioritaria n.º 1 en Hoy",
                    "Procesar a cero los elementos pendientes en Entrada",
                    "Reservar una tarea tranquila para Esta noche"
                ]
            )
        default:
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
}
