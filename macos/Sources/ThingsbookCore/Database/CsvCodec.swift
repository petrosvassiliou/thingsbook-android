import Foundation

public struct ParsedCsvTaskRow: Equatable, Sendable {
    public let title: String
    public let notes: String
    public let status: TaskStatus
    public let scheduleState: ScheduleState
    public let whenDate: Int64?
    public let deadline: Int64?
    public let areaTitle: String?
    public let projectTitle: String?
    public let tagTitles: [String]
    public let checklistEntries: [(title: String, isCompleted: Bool)]

    public static func == (lhs: ParsedCsvTaskRow, rhs: ParsedCsvTaskRow) -> Bool {
        lhs.title == rhs.title &&
        lhs.notes == rhs.notes &&
        lhs.status == rhs.status &&
        lhs.scheduleState == rhs.scheduleState &&
        lhs.whenDate == rhs.whenDate &&
        lhs.deadline == rhs.deadline &&
        lhs.areaTitle == rhs.areaTitle &&
        lhs.projectTitle == rhs.projectTitle &&
        lhs.tagTitles == rhs.tagTitles &&
        lhs.checklistEntries.count == rhs.checklistEntries.count &&
        zip(lhs.checklistEntries, rhs.checklistEntries).allSatisfy { $0.title == $1.title && $0.isCompleted == $1.isCompleted }
    }
}

public enum CsvCodec {
    public static func exportTasksToCsv(
        tasks: [TaskItem],
        areasById: [String: Area],
        projectsById: [String: Project],
        calendar: Calendar = .current
    ) -> String {
        let isoFormatter = DateFormatter()
        isoFormatter.calendar = calendar
        isoFormatter.locale = Locale(identifier: "en_US_POSIX")
        isoFormatter.dateFormat = "yyyy-MM-dd"

        var out = "Title,Notes,Status,ScheduleState,WhenDate,Deadline,Area,Project,Tags,Checklist\n"
        for task in tasks {
            let whenStr = task.whenDate.map { isoFormatter.string(from: DateEpochConverter.date(fromMillis: $0)) } ?? ""
            let deadStr = task.deadline.map { isoFormatter.string(from: DateEpochConverter.date(fromMillis: $0)) } ?? ""
            let areaName = task.areaId.flatMap { areasById[$0]?.title } ?? ""
            let projectName = task.projectId.flatMap { projectsById[$0]?.title } ?? ""
            let tagStr = task.tags.map(\.title).joined(separator: ";")
            let chkStr = task.checklistItems
                .sorted(by: { $0.sortOrder < $1.sortOrder })
                .map { "\($0.isCompleted ? "[x]" : "[ ]") \($0.title)" }
                .joined(separator: " | ")

            let cols = [
                task.title,
                task.notes,
                task.status.rawValue,
                task.scheduleState.rawValue,
                whenStr,
                deadStr,
                areaName,
                projectName,
                tagStr,
                chkStr
            ]
            out += cols.map(escapeCsvCell).joined(separator: ",") + "\n"
        }
        return out
    }

    public static func parseCsvTasks(
        csvContent: String,
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) -> [ParsedCsvTaskRow] {
        var cleaned = csvContent
        if cleaned.hasPrefix("\u{FEFF}") {
            cleaned.removeFirst()
        }
        cleaned = cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return [] }

        let firstLine = cleaned.components(separatedBy: .newlines).first ?? ""
        let delimiter = detectCsvDelimiter(headerLine: firstLine)
        let rows = parseCsvRows(text: cleaned, delimiter: delimiter)
        guard !rows.isEmpty else { return [] }

        let normalizedFirst = rows[0].map(normalizeHeaderKey)
        let knownHeaderNames: Set<String> = [
            "title", "titel", "task", "aufgabe", "todo", "name", "subject", "betreff",
            "notes", "notizen", "beschreibung", "description", "status", "schedulestate",
            "whendate", "deadline", "duedate", "project", "projekt", "area", "bereich"
        ]
        let hasHeader = normalizedFirst.contains(where: { knownHeaderNames.contains($0) })

        let colIndex: ([String]) -> Int = { candidates in
            guard hasHeader else { return -1 }
            return normalizedFirst.firstIndex(where: { candidates.contains($0) }) ?? -1
        }

        let idxTitle = hasHeader ? max(0, colIndex(["title", "titel", "task", "aufgabe", "todo", "name", "subject", "betreff"])) : 0
        let idxNotes = hasHeader ? colIndex(["notes", "notizen", "note", "notiz", "description", "beschreibung", "body", "content"]) : 1
        let idxStatus = hasHeader ? colIndex(["status", "state", "zustand", "completed", "erledigt"]) : 2
        let idxSchedule = hasHeader ? colIndex(["schedulestate", "schedule", "list", "liste", "smartview", "view", "ansicht"]) : 3
        let idxWhen = hasHeader ? colIndex(["whendate", "when", "wann", "startdate", "start", "date", "datum"]) : 4
        let idxDeadline = hasHeader ? colIndex(["deadline", "duedate", "due", "frist", "faellig", "fallig", "enddate"]) : 5
        let idxArea = hasHeader ? colIndex(["area", "bereich", "category", "kategorie", "folder"]) : 6
        let idxProject = hasHeader ? colIndex(["project", "projekt", "listname"]) : 7
        let idxTags = hasHeader ? colIndex(["tags", "tag", "labels", "label", "schlagworte", "stichworte"]) : 8
        let idxChecklist = hasHeader ? colIndex(["checklist", "checkliste", "subtasks", "unteraufgaben", "steps"]) : 9

        let startRow = hasHeader ? 1 : 0
        guard startRow < rows.count else { return [] }

        let sodToday = calendar.startOfDay(for: referenceDate)
        let todayEpoch = DateEpochConverter.startOfDayMillis(for: sodToday, calendar: calendar)
        var parsed: [ParsedCsvTaskRow] = []

        for r in startRow..<rows.count {
            let cols = rows[r]
            let cell: (Int) -> String = { idx in
                guard idx >= 0 && idx < cols.count else { return "" }
                return cols[idx].trimmingCharacters(in: .whitespacesAndNewlines)
            }

            let title = cell(idxTitle)
            if title.isEmpty { continue }

            let notes = cell(idxNotes)
            let rawStatus = cell(idxStatus).uppercased()
            let status: TaskStatus
            if ["COMPLETED", "DONE", "TRUE", "1", "ERLEDIGT", "JA", "YES"].contains(rawStatus) {
                status = .completed
            } else if ["CANCELED", "CANCELLED", "ABGEBROCHEN"].contains(rawStatus) {
                status = .canceled
            } else {
                status = .open
            }

            let whenEpoch = parseFlexibleDateToEpoch(raw: cell(idxWhen), calendar: calendar)
            let deadlineEpoch = parseFlexibleDateToEpoch(raw: cell(idxDeadline), calendar: calendar)

            let rawSchedule = cell(idxSchedule).uppercased().replacingOccurrences(of: " ", with: "_")
            let scheduleState: ScheduleState
            switch rawSchedule {
            case "TODAY", "HEUTE":
                scheduleState = .today
            case "THIS_EVENING", "EVENING", "HEUTE_ABEND", "ABEND":
                scheduleState = .thisEvening
            case "UPCOMING", "GEPLANT", "DEMNAECHST":
                scheduleState = .upcoming
            case "ANYTIME", "JEDERZEIT":
                scheduleState = .anytime
            case "SOMEDAY", "IRGENDWANN":
                scheduleState = .someday
            case "INBOX", "EINGANG":
                scheduleState = .inbox
            default:
                if let w = whenEpoch {
                    scheduleState = w <= todayEpoch ? .today : .upcoming
                } else {
                    scheduleState = .inbox
                }
            }

            let normalizedWhen: Int64?
            switch scheduleState {
            case .today, .thisEvening:
                normalizedWhen = whenEpoch ?? todayEpoch
            case .upcoming:
                if let w = whenEpoch {
                    normalizedWhen = w
                } else {
                    let tmr = calendar.date(byAdding: .day, value: 1, to: sodToday) ?? sodToday.addingTimeInterval(86_400)
                    normalizedWhen = DateEpochConverter.startOfDayMillis(for: tmr, calendar: calendar)
                }
            default:
                normalizedWhen = whenEpoch
            }

            let rawArea = cell(idxArea)
            let rawProject = cell(idxProject)
            let rawTags = cell(idxTags)
            let tagTitles = rawTags
                .components(separatedBy: CharacterSet(charactersIn: ";,|"))
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }

            let rawChecklist = cell(idxChecklist)
            var checklistEntries: [(title: String, isCompleted: Bool)] = []
            if !rawChecklist.isEmpty {
                let parts = rawChecklist.contains("|")
                    ? rawChecklist.components(separatedBy: "|")
                    : rawChecklist.components(separatedBy: "\n")
                for part in parts {
                    let trimmed = part.trimmingCharacters(in: .whitespacesAndNewlines)
                    if trimmed.isEmpty { continue }
                    let lower = trimmed.lowercased()
                    let isChecked = lower.hasPrefix("[x]") || trimmed.hasPrefix("✓") || trimmed.hasPrefix("☑")
                    let cleanTitle = trimmed.replacingOccurrences(
                        of: #"^(\[[xX ]\]|[✓☑☐\-*•])\s*"#,
                        with: "",
                        options: .regularExpression
                    ).trimmingCharacters(in: .whitespacesAndNewlines)
                    if !cleanTitle.isEmpty {
                        checklistEntries.append((title: cleanTitle, isCompleted: isChecked))
                    }
                }
            }

            parsed.append(
                ParsedCsvTaskRow(
                    title: title,
                    notes: notes,
                    status: status,
                    scheduleState: scheduleState,
                    whenDate: normalizedWhen,
                    deadline: deadlineEpoch,
                    areaTitle: rawArea.isEmpty ? nil : rawArea,
                    projectTitle: rawProject.isEmpty ? nil : rawProject,
                    tagTitles: tagTitles,
                    checklistEntries: checklistEntries
                )
            )
        }

        return parsed
    }

    private static func escapeCsvCell(_ value: String) -> String {
        if value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r") {
            return "\"" + value.replacingOccurrences(of: "\"", with: "\"\"") + "\""
        }
        return value
    }

    private static func normalizeHeaderKey(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: "ä", with: "ae")
            .replacingOccurrences(of: "ö", with: "oe")
            .replacingOccurrences(of: "ü", with: "ue")
            .replacingOccurrences(of: #"[^a-z0-9]"#, with: "", options: .regularExpression)
    }

    private static func detectCsvDelimiter(headerLine: String) -> Character {
        var inQuotes = false
        var commas = 0
        var semicolons = 0
        for ch in headerLine {
            if ch == "\"" {
                inQuotes.toggle()
            } else if !inQuotes {
                if ch == "," { commas += 1 }
                else if ch == ";" { semicolons += 1 }
            }
        }
        return semicolons > commas ? ";" : ","
    }

    public static func parseCsvRows(text: String, delimiter: Character = ",") -> [[String]] {
        var rows: [[String]] = []
        var currentRow: [String] = []
        var currentField = ""
        var inQuotes = false

        let chars = Array(text)
        var i = 0
        while i < chars.count {
            let c = chars[i]
            if inQuotes {
                if c == "\"" {
                    if i + 1 < chars.count && chars[i + 1] == "\"" {
                        currentField.append("\"")
                        i += 1
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                if c == "\"" {
                    inQuotes = true
                } else if c == delimiter {
                    currentRow.append(currentField)
                    currentField = ""
                } else if c == "\r" || c == "\n" {
                    if c == "\r" && i + 1 < chars.count && chars[i + 1] == "\n" {
                        i += 1
                    }
                    currentRow.append(currentField)
                    currentField = ""
                    if currentRow.contains(where: { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) {
                        rows.append(currentRow)
                    }
                    currentRow = []
                } else {
                    currentField.append(c)
                }
            }
            i += 1
        }

        if !currentField.isEmpty || !currentRow.isEmpty {
            currentRow.append(currentField)
            if currentRow.contains(where: { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) {
                rows.append(currentRow)
            }
        }
        return rows
    }

    public static func parseFlexibleDateToEpoch(raw: String, calendar: Calendar = .current) -> Int64? {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return nil }

        if let numeric = Int64(value) {
            let millis = numeric > 100_000_000_000 ? numeric : numeric * 1000
            let d = DateEpochConverter.date(fromMillis: millis)
            return DateEpochConverter.startOfDayMillis(for: d, calendar: calendar)
        }

        let head10 = String(value.prefix(10))
        let isoParts = head10.split(separator: "-")
        if isoParts.count == 3,
           let y = Int(isoParts[0]),
           let m = Int(isoParts[1]),
           let d = Int(isoParts[2]),
           let date = calendar.date(from: DateComponents(year: y, month: m, day: d)) {
            return DateEpochConverter.startOfDayMillis(for: date, calendar: calendar)
        }

        let formats = ["dd.MM.yyyy", "d.M.yyyy", "dd.MM.yy", "MM/dd/yyyy", "M/d/yyyy", "yyyy/MM/dd"]
        let df = DateFormatter()
        df.calendar = calendar
        df.locale = Locale(identifier: "en_US_POSIX")
        for fmt in formats {
            df.dateFormat = fmt
            if let parsed = df.date(from: value) {
                return DateEpochConverter.startOfDayMillis(for: parsed, calendar: calendar)
            }
        }
        return nil
    }
}
