import Foundation
import zlib

public typealias EffortLevel = TaskEffortLevel

public enum ExternalProjectCodecError: LocalizedError, Equatable, Sendable {
    case emptyInput
    case unsupportedFormat(String)
    case invalidCsv(String)
    case invalidJson(String)
    case invalidXlsx(String)

    public var errorDescription: String? {
        switch self {
        case .emptyInput:
            return "The selected project file is empty."
        case .unsupportedFormat(let detail):
            return "Unsupported project file format: \(detail)"
        case .invalidCsv(let detail):
            return "Invalid CSV project file: \(detail)"
        case .invalidJson(let detail):
            return "Invalid Asana JSON project file: \(detail)"
        case .invalidXlsx(let detail):
            return "Invalid Microsoft Planner (.xlsx) workbook: \(detail)"
        }
    }
}

public enum ExternalExportFormat: String, CaseIterable, Equatable, Sendable {
    case asanaCsv
    case asanaJson
    case plannerXlsx
    case plannerCsv

    public var fileExtension: String {
        switch self {
        case .asanaCsv, .plannerCsv:
            return "csv"
        case .asanaJson:
            return "json"
        case .plannerXlsx:
            return "xlsx"
        }
    }

    public var defaultFileSuffix: String {
        switch self {
        case .asanaCsv:
            return "-asana.csv"
        case .asanaJson:
            return "-asana.json"
        case .plannerXlsx:
            return "-planner.xlsx"
        case .plannerCsv:
            return "-planner.csv"
        }
    }
}

public struct ExternalImportedChecklistItem: Equatable, Sendable {
    public var title: String
    public var isCompleted: Bool

    public init(title: String, isCompleted: Bool = false) {
        self.title = title
        self.isCompleted = isCompleted
    }
}

public struct ExternalImportedTask: Equatable, Sendable {
    public var title: String
    public var notes: String
    public var status: TaskStatus
    public var completedAt: Int64?
    public var whenDate: Int64?
    public var deadline: Int64?
    public var isPinned: Bool
    public var effortLevel: EffortLevel?
    public var tagNames: [String]
    public var checklistItems: [ExternalImportedChecklistItem]
    public var assigneeId: String?
    public var workspaceId: String?

    public init(
        title: String,
        notes: String = "",
        status: TaskStatus = .open,
        completedAt: Int64? = nil,
        whenDate: Int64? = nil,
        deadline: Int64? = nil,
        isPinned: Bool = false,
        effortLevel: EffortLevel? = nil,
        tagNames: [String] = [],
        checklistItems: [ExternalImportedChecklistItem] = [],
        assigneeId: String? = nil,
        workspaceId: String? = nil
    ) {
        self.title = title
        self.notes = notes
        self.status = status
        self.completedAt = completedAt
        self.whenDate = whenDate
        self.deadline = deadline
        self.isPinned = isPinned
        self.effortLevel = effortLevel
        self.tagNames = tagNames
        self.checklistItems = checklistItems
        self.assigneeId = assigneeId
        self.workspaceId = workspaceId
    }
}

public struct ExternalImportedPhase: Equatable, Sendable {
    public var title: String
    public var startDate: Int64?
    public var endDate: Int64?
    public var tasks: [ExternalImportedTask]

    public init(
        title: String,
        startDate: Int64? = nil,
        endDate: Int64? = nil,
        tasks: [ExternalImportedTask] = []
    ) {
        self.title = title
        self.startDate = startDate
        self.endDate = endDate
        self.tasks = tasks
    }
}

public struct ExternalImportedProject: Equatable, Sendable {
    public var title: String
    public var notes: String
    public var ungroupedTasks: [ExternalImportedTask]
    public var phases: [ExternalImportedPhase]
    public var tagNames: [String]
    public var workspaceId: String?

    public var totalTaskCount: Int {
        ungroupedTasks.count + phases.reduce(0) { $0 + $1.tasks.count }
    }

    public init(
        title: String,
        notes: String = "",
        ungroupedTasks: [ExternalImportedTask] = [],
        phases: [ExternalImportedPhase] = [],
        tagNames: [String] = [],
        workspaceId: String? = nil
    ) {
        self.title = title
        self.notes = notes
        self.ungroupedTasks = ungroupedTasks
        self.phases = phases
        self.tagNames = tagNames
        self.workspaceId = workspaceId
    }
}

public struct ExternalExportedFile: Equatable, Sendable {
    public let suggestedFileName: String
    public let data: Data
    public let projectTitle: String

    public init(suggestedFileName: String, data: Data, projectTitle: String) {
        self.suggestedFileName = suggestedFileName
        self.data = data
        self.projectTitle = projectTitle
    }
}

// MARK: - Asana & Microsoft Planner Offline Codec

public enum ExternalProjectCodec {
    public static let asanaCsvColumns: [String] = [
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
    ]

    public static let plannerColumns: [String] = [
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
    ]

    private static let maxDecompressedEntryBytes = 64 * 1024 * 1024

    // MARK: - Auto-Detection Entry Point

    public static func importAutoDetect(
        data: Data,
        fileNameHint: String? = nil,
        fallbackTitle: String = "Imported Project",
        calendar: Calendar = .current
    ) throws -> ExternalImportedProject {
        guard !data.isEmpty else {
            throw ExternalProjectCodecError.emptyInput
        }

        let resolvedFallback = deriveFallbackTitle(fileNameHint: fileNameHint, fallbackTitle: fallbackTitle)
        let lowerHint = fileNameHint?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""

        // 1. OOXML ZIP (.xlsx) magic header PK\x03\x04 or PK\x05\x06 (empty zip)
        if isZipArchive(data) || lowerHint.hasSuffix(".xlsx") {
            return try importPlannerXlsx(data, fallbackTitle: resolvedFallback, calendar: calendar)
        }

        // 2. Decode text (UTF-8, UTF-16 BOM, Windows-1252, ISO-Latin-1)
        guard let decodedText = decodeTextData(data) else {
            throw ExternalProjectCodecError.unsupportedFormat("Unrecognized binary encoding.")
        }

        let cleanText = stripUTF8BOM(decodedText).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanText.isEmpty else {
            throw ExternalProjectCodecError.emptyInput
        }

        // 3. JSON (.json or leading '{' / '[')
        if cleanText.first == "{" || cleanText.first == "[" || lowerHint.hasSuffix(".json") {
            return try importAsanaJson(Data(cleanText.utf8), fallbackTitle: resolvedFallback, calendar: calendar)
        }

        // 4. CSV (distinguish Microsoft Planner CSV vs Asana CSV via header tokens)
        let firstLine = cleanText.components(separatedBy: .newlines).first ?? ""
        let delimiter = detectCsvDelimiter(headerLine: firstLine)
        let headerRow = CsvCodec.parseCsvRows(text: firstLine, delimiter: delimiter).first ?? []
        let normalizedHeaders = Set(headerRow.map(normalizeHeaderKey))

        if looksLikePlannerHeaders(normalizedHeaders) {
            return try importPlannerCsv(cleanText, fallbackTitle: resolvedFallback, calendar: calendar)
        }
        return try importAsanaCsv(cleanText, fallbackTitle: resolvedFallback, calendar: calendar)
    }

    // MARK: - Unified Export Entry Point

    public static func exportProject(
        project: Project,
        headings: [Heading],
        tasks: [TaskItem],
        format: ExternalExportFormat,
        tagsById: [String: Tag] = [:],
        calendar: Calendar = .current
    ) throws -> ExternalExportedFile {
        let cleanTitle = project.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Project"
            : project.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let fileName = sanitizedFileStem(cleanTitle) + format.defaultFileSuffix

        let payload: Data
        switch format {
        case .asanaCsv:
            payload = exportAsanaCsv(project: project, headings: headings, tasks: tasks, calendar: calendar)
        case .asanaJson:
            payload = try exportAsanaJson(
                project: project,
                headings: headings,
                tasks: tasks,
                tagsById: tagsById,
                calendar: calendar
            )
        case .plannerXlsx:
            payload = try exportPlannerXlsx(
                project: project,
                headings: headings,
                tasks: tasks,
                compressEntries: true,
                calendar: calendar
            )
        case .plannerCsv:
            payload = exportPlannerCsv(project: project, headings: headings, tasks: tasks, calendar: calendar)
        }

        return ExternalExportedFile(
            suggestedFileName: fileName,
            data: payload,
            projectTitle: cleanTitle
        )
    }

    // MARK: - 1. Asana CSV Import & Export

    public static func importAsanaCsv(
        _ csvText: String,
        fallbackTitle: String = "Imported Project",
        calendar: Calendar = .current
    ) throws -> ExternalImportedProject {
        let cleaned = stripUTF8BOM(csvText).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else {
            throw ExternalProjectCodecError.emptyInput
        }

        let firstLine = cleaned.components(separatedBy: .newlines).first ?? ""
        let delimiter = detectCsvDelimiter(headerLine: firstLine)
        let rows = CsvCodec.parseCsvRows(text: cleaned, delimiter: delimiter)
        guard !rows.isEmpty else {
            throw ExternalProjectCodecError.emptyInput
        }

        let normalizedHeader = rows[0].map(normalizeHeaderKey)
        let colIdx: ([String]) -> Int = { candidates in
            normalizedHeader.firstIndex(where: { candidates.contains($0) }) ?? -1
        }

        let idxId = colIdx(["taskid", "id", "gid"])
        let idxCompletedAt = colIdx(["completedat", "completed", "completeddate", "erledigtam"])
        let idxName = colIdx(["name", "taskname", "title", "task", "aufgabe", "nom", "nombre", "nome"])
        let idxSection = colIdx(["sectioncolumn", "section", "column", "abschnitt", "phase", "spalte"])
        let idxAssignee = colIdx(["assignee", "assigneeemail", "assignedto", "verantwortlich"])
        let idxStart = colIdx(["startdate", "starton", "start", "whendate"])
        let idxDue = colIdx(["duedate", "dueon", "due", "deadline", "faelligkeitsdatum"])
        let idxTags = colIdx(["tags", "tag", "categories", "labels", "kategorien"])
        let idxNotes = colIdx(["notes", "description", "details", "notizen", "beschreibung"])
        let idxProjects = colIdx(["projects", "project", "projekt", "projekte"])
        let idxParent = colIdx(["parenttask", "parent", "parentid", "parenttaskid", "uebergeordneteaufgabe"])

        guard idxName >= 0 else {
            throw ExternalProjectCodecError.invalidCsv("Missing 'Name' column in Asana CSV.")
        }

        struct RawAsanaRow {
            let rowNumber: Int
            let taskId: String
            let name: String
            let completedAtRaw: String
            let section: String
            let assignee: String
            let startRaw: String
            let dueRaw: String
            let tagsRaw: String
            let notes: String
            let projectRaw: String
            let parentRaw: String
        }

        var rawRows: [RawAsanaRow] = []
        for r in 1..<rows.count {
            let cols = rows[r]
            let cell: (Int) -> String = { idx in
                guard idx >= 0 && idx < cols.count else { return "" }
                return cols[idx].trimmingCharacters(in: .whitespacesAndNewlines)
            }
            let name = cell(idxName)
            if name.isEmpty { continue }
            rawRows.append(
                RawAsanaRow(
                    rowNumber: r,
                    taskId: cell(idxId),
                    name: name,
                    completedAtRaw: cell(idxCompletedAt),
                    section: cell(idxSection),
                    assignee: cell(idxAssignee),
                    startRaw: cell(idxStart),
                    dueRaw: cell(idxDue),
                    tagsRaw: cell(idxTags),
                    notes: cell(idxNotes),
                    projectRaw: cell(idxProjects),
                    parentRaw: cell(idxParent)
                )
            )
        }

        var detectedProjectTitle: String? = nil
        for row in rawRows where !row.projectRaw.isEmpty {
            let firstProj = row.projectRaw
                .components(separatedBy: CharacterSet(charactersIn: ",;"))
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .first(where: { !$0.isEmpty })
            if let firstProj, !firstProj.isEmpty {
                detectedProjectTitle = firstProj
                break
            }
        }

        // Pass 1: Build root tasks and lookup tables by Task ID and Name
        struct MutableParentEntry {
            var task: ExternalImportedTask
            let section: String
            let taskId: String
        }

        var parentEntries: [MutableParentEntry] = []
        var indexByIdExact: [String: Int] = [:]
        var indexByIdLower: [String: Int] = [:]
        var indexByNameExact: [String: Int] = [:]
        var indexByNameLower: [String: Int] = [:]
        var deferredSubtasks: [RawAsanaRow] = []

        for row in rawRows {
            if !row.parentRaw.isEmpty {
                deferredSubtasks.append(row)
                continue
            }
            let isDone = isCompletedFieldTruthy(row.completedAtRaw)
            let compAt = isDone ? parseExternalTimestampOrDate(row.completedAtRaw, calendar: calendar) : nil
            let whenMs = parseExternalDate(row.startRaw, calendar: calendar)
            let dueMs = parseExternalDate(row.dueRaw, calendar: calendar)
            let tags = splitDeduplicatedTags(row.tagsRaw)

            let task = ExternalImportedTask(
                title: row.name,
                notes: row.notes,
                status: isDone ? .completed : .open,
                completedAt: compAt,
                whenDate: whenMs,
                deadline: dueMs,
                isPinned: false,
                effortLevel: nil,
                tagNames: tags,
                checklistItems: [],
                assigneeId: row.assignee.isEmpty ? nil : row.assignee
            )

            let idx = parentEntries.count
            parentEntries.append(MutableParentEntry(task: task, section: row.section, taskId: row.taskId))
            if !row.taskId.isEmpty {
                if indexByIdExact[row.taskId] == nil { indexByIdExact[row.taskId] = idx }
                let lowId = row.taskId.lowercased()
                if indexByIdLower[lowId] == nil { indexByIdLower[lowId] = idx }
            }
            if indexByNameExact[row.name] == nil { indexByNameExact[row.name] = idx }
            let lowName = row.name.lowercased()
            if indexByNameLower[lowName] == nil { indexByNameLower[lowName] = idx }
        }

        // Pass 2: Attach subtasks as checklist items (or promote orphans so no task is lost)
        for sub in deferredSubtasks {
            let parentKey = sub.parentRaw
            let matchedIdx = indexByIdExact[parentKey]
                ?? indexByIdLower[parentKey.lowercased()]
                ?? indexByNameExact[parentKey]
                ?? indexByNameLower[parentKey.lowercased()]

            let subDone = isCompletedFieldTruthy(sub.completedAtRaw)
            if let matchedIdx {
                parentEntries[matchedIdx].task.checklistItems.append(
                    ExternalImportedChecklistItem(title: sub.name, isCompleted: subDone)
                )
            } else {
                let compAt = subDone ? parseExternalTimestampOrDate(sub.completedAtRaw, calendar: calendar) : nil
                let promoted = ExternalImportedTask(
                    title: sub.name,
                    notes: sub.notes,
                    status: subDone ? .completed : .open,
                    completedAt: compAt,
                    whenDate: parseExternalDate(sub.startRaw, calendar: calendar),
                    deadline: parseExternalDate(sub.dueRaw, calendar: calendar),
                    tagNames: splitDeduplicatedTags(sub.tagsRaw),
                    assigneeId: sub.assignee.isEmpty ? nil : sub.assignee
                )
                parentEntries.append(MutableParentEntry(task: promoted, section: sub.section, taskId: sub.taskId))
            }
        }

        var ungrouped: [ExternalImportedTask] = []
        var phaseOrder: [String] = []
        var phaseTasksByKey: [String: (title: String, tasks: [ExternalImportedTask])] = [:]

        for entry in parentEntries {
            if isUngroupedSectionName(entry.section) {
                ungrouped.append(entry.task)
            } else {
                let secTitle = entry.section.trimmingCharacters(in: .whitespacesAndNewlines)
                let key = secTitle.lowercased()
                if phaseTasksByKey[key] == nil {
                    phaseOrder.append(key)
                    phaseTasksByKey[key] = (title: secTitle, tasks: [entry.task])
                } else {
                    phaseTasksByKey[key]?.tasks.append(entry.task)
                }
            }
        }

        let phases = phaseOrder.compactMap { key -> ExternalImportedPhase? in
            guard let bucket = phaseTasksByKey[key] else { return nil }
            return ExternalImportedPhase(title: bucket.title, tasks: bucket.tasks)
        }

        let resolvedTitle = nonEmptyOrFallback(detectedProjectTitle, fallback: fallbackTitle)
        return ExternalImportedProject(
            title: resolvedTitle,
            notes: "",
            ungroupedTasks: ungrouped,
            phases: phases,
            tagNames: []
        )
    }

    public static func exportAsanaCsv(
        project: Project,
        headings: [Heading],
        tasks: [TaskItem],
        calendar: Calendar = .current
    ) -> Data {
        let csvString = exportAsanaCsvString(
            project: project,
            headings: headings,
            tasks: tasks,
            calendar: calendar
        )
        return Data(csvString.utf8)
    }

    public static func exportAsanaCsvString(
        project: Project,
        headings: [Heading],
        tasks: [TaskItem],
        calendar: Calendar = .current
    ) -> String {
        let iso = makeIsoDateFormatter(calendar: calendar)
        let formatMillis: (Int64?) -> String = { ms in
            guard let ms, ms > 0 else { return "" }
            return iso.string(from: DateEpochConverter.date(fromMillis: ms))
        }
        let fallbackDoneDate = iso.string(from: Date())

        let orderedGroups = groupTasksForExport(headings: headings, tasks: tasks)
        var out = "\u{FEFF}" + asanaCsvColumns.map(escapeCsvCell).joined(separator: ",") + "\n"

        func appendTaskWithSubtasks(_ task: TaskItem, sectionName: String) {
            let completedStr: String
            if task.status == .completed {
                let candidate = formatMillis(task.completedAt ?? task.updatedAt)
                completedStr = candidate.isEmpty ? fallbackDoneDate : candidate
            } else {
                completedStr = ""
            }

            let rowCols: [String] = [
                task.id,
                formatMillis(task.createdAt),
                completedStr,
                formatMillis(task.updatedAt),
                task.title,
                sectionName,
                task.assigneeId ?? "",
                formatMillis(task.whenDate),
                formatMillis(task.deadline),
                task.tags.map(\.title).joined(separator: ", "),
                task.notes,
                project.title,
                ""
            ]
            out += rowCols.map(escapeCsvCell).joined(separator: ",") + "\n"

            let activeChecklists = task.checklistItems
                .filter { !$0.isDeleted }
                .sorted {
                    if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                    return $0.createdAt < $1.createdAt
                }

            for chk in activeChecklists {
                let subDoneStr: String
                if chk.isCompleted {
                    let candidate = formatMillis(chk.updatedAt > 0 ? chk.updatedAt : task.completedAt)
                    subDoneStr = candidate.isEmpty ? fallbackDoneDate : candidate
                } else {
                    subDoneStr = ""
                }
                let subCols: [String] = [
                    chk.id,
                    formatMillis(chk.createdAt),
                    subDoneStr,
                    formatMillis(chk.updatedAt),
                    chk.title,
                    sectionName,
                    "",
                    "",
                    "",
                    "",
                    "",
                    project.title,
                    task.title
                ]
                out += subCols.map(escapeCsvCell).joined(separator: ",") + "\n"
            }
        }

        for task in orderedGroups.ungrouped {
            appendTaskWithSubtasks(task, sectionName: "")
        }
        for phase in orderedGroups.phases {
            for task in phase.tasks {
                appendTaskWithSubtasks(task, sectionName: phase.heading.title)
            }
        }

        return out
    }

    // MARK: - 2. Asana JSON Import & Export (REST API + Structured JSON)

    public static func importAsanaJson(
        _ data: Data,
        fallbackTitle: String = "Imported Project",
        calendar: Calendar = .current
    ) throws -> ExternalImportedProject {
        guard !data.isEmpty else {
            throw ExternalProjectCodecError.emptyInput
        }

        let jsonObj: Any
        do {
            jsonObj = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        } catch {
            throw ExternalProjectCodecError.invalidJson(error.localizedDescription)
        }

        var detectedTitle: String? = nil
        var detectedNotes: String = ""
        var projectTags: [String] = []
        var projectWorkspaceId: String? = nil

        struct PhaseBucket {
            var gid: String?
            var title: String
            var startDate: Int64?
            var endDate: Int64?
            var tasks: [ExternalImportedTask]
        }

        var phaseOrder: [String] = []
        var phasesByLowerTitle: [String: PhaseBucket] = [:]
        var phaseLowerTitleByGid: [String: String] = [:]
        var ungroupedTasks: [ExternalImportedTask] = []

        func ensurePhase(
            title: String,
            gid: String? = nil,
            startDate: Int64? = nil,
            endDate: Int64? = nil
        ) -> String? {
            let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
            if isUngroupedSectionName(trimmed) { return nil }
            let key = trimmed.lowercased()
            if var existing = phasesByLowerTitle[key] {
                if existing.gid == nil, let gid, !gid.isEmpty { existing.gid = gid }
                if existing.startDate == nil, let startDate { existing.startDate = startDate }
                if existing.endDate == nil, let endDate { existing.endDate = endDate }
                phasesByLowerTitle[key] = existing
            } else {
                phaseOrder.append(key)
                phasesByLowerTitle[key] = PhaseBucket(
                    gid: gid,
                    title: trimmed,
                    startDate: startDate,
                    endDate: endDate,
                    tasks: []
                )
            }
            if let gid, !gid.isEmpty {
                phaseLowerTitleByGid[gid] = key
            }
            return key
        }

        func parseTaskDict(_ dict: [String: Any]) -> (
            gid: String,
            task: ExternalImportedTask,
            sectionKey: String?,
            parentRef: String?,
            projectTitleHint: String?
        )? {
            let subtype = (dict["resource_subtype"] as? String)?.lowercased() ?? ""
            let isSeparator = (dict["is_rendered_as_separator"] as? Bool) == true
            let rawName = ((dict["name"] as? String) ?? (dict["title"] as? String) ?? "")
                .trimmingCharacters(in: .whitespacesAndNewlines)

            if subtype == "section" || isSeparator {
                let secName = rawName.hasSuffix(":") ? String(rawName.dropLast()) : rawName
                _ = ensurePhase(title: secName, gid: stringValue(dict["gid"] ?? dict["id"]))
                return nil
            }
            guard !rawName.isEmpty else { return nil }

            let gid = stringValue(dict["gid"] ?? dict["id"]) ?? ""
            let notes = (dict["notes"] as? String) ?? (dict["description"] as? String) ?? ""

            let completedFlag = boolValue(dict["completed"]) ?? boolValue(dict["isCompleted"])
            let statusStr = (dict["status"] as? String)?.uppercased()
            let completedAtRaw = stringValue(dict["completed_at"] ?? dict["completedAt"])
            let isCompleted: Bool = {
                if let completedFlag { return completedFlag }
                if statusStr == "COMPLETED" || statusStr == "DONE" { return true }
                if let completedAtRaw, isCompletedFieldTruthy(completedAtRaw) { return true }
                return false
            }()
            let completedAtMs: Int64? = isCompleted
                ? (completedAtRaw.flatMap { parseExternalTimestampOrDate($0, calendar: calendar) } ?? int64Value(dict["completed_at"] ?? dict["completedAt"]))
                : nil

            let whenMs = parseJsonDateField(dict["start_on"] ?? dict["start_at"] ?? dict["startDate"] ?? dict["whenDate"], calendar: calendar)
            let dueMs = parseJsonDateField(dict["due_on"] ?? dict["due_at"] ?? dict["dueDate"] ?? dict["deadline"], calendar: calendar)

            let isPinned = boolValue(dict["is_pinned"] ?? dict["isPinned"] ?? dict["hearted"] ?? dict["liked"]) ?? false
            let effort = EffortLevel.normalized(
                (dict["effort_level"] as? String) ?? (dict["effortLevel"] as? String) ?? parsePriorityToEffort(dict["priority"] as? String)?.rawValue
            )
            let pinnedFromPriority = isPriorityUrgent(dict["priority"] as? String)

            let tags = extractJsonTagNames(dict["tags"] ?? dict["tagNames"] ?? dict["labels"])
            let assignee = extractJsonPersonIdentifier(dict["assignee"] ?? dict["assigneeId"] ?? dict["assignee_id"])
            let workspace = extractJsonEntityIdentifier(dict["workspace"] ?? dict["workspaceId"] ?? dict["workspace_id"])

            // Checklist / subtasks
            var checklists: [ExternalImportedChecklistItem] = []
            let rawSubs = (dict["subtasks"] as? [Any])
                ?? (dict["checklistItems"] as? [Any])
                ?? (dict["checklist"] as? [Any])
                ?? []
            for subAny in rawSubs {
                if let subStr = subAny as? String {
                    let cleanSub = subStr.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !cleanSub.isEmpty {
                        checklists.append(ExternalImportedChecklistItem(title: cleanSub, isCompleted: false))
                    }
                } else if let subDict = subAny as? [String: Any] {
                    let subTitle = ((subDict["name"] as? String) ?? (subDict["title"] as? String) ?? "")
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !subTitle.isEmpty else { continue }
                    let subDone = boolValue(subDict["completed"] ?? subDict["isCompleted"])
                        ?? (stringValue(subDict["completed_at"]).map(isCompletedFieldTruthy) ?? false)
                    checklists.append(ExternalImportedChecklistItem(title: subTitle, isCompleted: subDone))
                }
            }

            // Section & Project membership resolution
            var resolvedSectionKey: String? = nil
            var membershipProjectTitle: String? = nil

            if let memberships = dict["memberships"] as? [[String: Any]] {
                for mem in memberships {
                    if let projObj = mem["project"] as? [String: Any] {
                        let pName = ((projObj["name"] as? String) ?? (projObj["title"] as? String) ?? "")
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                        if !pName.isEmpty && membershipProjectTitle == nil {
                            membershipProjectTitle = pName
                        }
                    } else if let pStr = mem["project"] as? String, !pStr.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        if membershipProjectTitle == nil { membershipProjectTitle = pStr.trimmingCharacters(in: .whitespacesAndNewlines) }
                    }

                    if let secObj = mem["section"] as? [String: Any] {
                        let sName = ((secObj["name"] as? String) ?? (secObj["title"] as? String) ?? "")
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                        let sGid = stringValue(secObj["gid"] ?? secObj["id"])
                        let sStart = parseJsonDateField(secObj["start_on"] ?? secObj["startDate"], calendar: calendar)
                        let sEnd = parseJsonDateField(secObj["due_on"] ?? secObj["endDate"], calendar: calendar)
                        if !sName.isEmpty, let key = ensurePhase(title: sName, gid: sGid, startDate: sStart, endDate: sEnd) {
                            resolvedSectionKey = key
                        } else if let sGid, let mapped = phaseLowerTitleByGid[sGid] {
                            resolvedSectionKey = mapped
                        }
                    } else if let secStr = mem["section"] as? String {
                        if let key = ensurePhase(title: secStr) {
                            resolvedSectionKey = key
                        }
                    }
                }
            }

            if resolvedSectionKey == nil {
                if let secStr = dict["section"] as? String {
                    resolvedSectionKey = ensurePhase(title: secStr)
                } else if let secObj = dict["section"] as? [String: Any] {
                    let sName = ((secObj["name"] as? String) ?? (secObj["title"] as? String) ?? "")
                    let sGid = stringValue(secObj["gid"] ?? secObj["id"])
                    if !sName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        resolvedSectionKey = ensurePhase(title: sName, gid: sGid)
                    } else if let sGid, let mapped = phaseLowerTitleByGid[sGid] {
                        resolvedSectionKey = mapped
                    }
                } else if let secId = stringValue(dict["section_id"] ?? dict["sectionId"]),
                          let mapped = phaseLowerTitleByGid[secId] {
                    resolvedSectionKey = mapped
                }
            }

            // Parent task reference (when subtasks appear as top-level items in `data`)
            var parentRef: String? = nil
            if let parentStr = dict["parent"] as? String {
                let clean = parentStr.trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty { parentRef = clean }
            } else if let parentObj = dict["parent"] as? [String: Any] {
                parentRef = stringValue(parentObj["gid"] ?? parentObj["id"])
                    ?? ((parentObj["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines))
            } else if let parentId = stringValue(dict["parent_id"] ?? dict["parentId"]) {
                if !parentId.isEmpty { parentRef = parentId }
            }

            let task = ExternalImportedTask(
                title: rawName,
                notes: notes,
                status: isCompleted ? .completed : .open,
                completedAt: completedAtMs,
                whenDate: whenMs,
                deadline: dueMs,
                isPinned: isPinned || pinnedFromPriority,
                effortLevel: effort,
                tagNames: tags,
                checklistItems: checklists,
                assigneeId: assignee,
                workspaceId: workspace
            )

            return (gid, task, resolvedSectionKey, parentRef, membershipProjectTitle)
        }

        func ingestFlatTaskArray(_ rawTasks: [[String: Any]], forcedSectionKey: String? = nil) {
            struct PendingItem {
                let gid: String
                var task: ExternalImportedTask
                let sectionKey: String?
                let parentRef: String?
            }

            var roots: [PendingItem] = []
            var rootIdxByGid: [String: Int] = [:]
            var rootIdxByNameLower: [String: Int] = [:]
            var pendingSubs: [PendingItem] = []

            for raw in rawTasks {
                guard let parsed = parseTaskDict(raw) else { continue }
                if detectedTitle == nil, let hint = parsed.projectTitleHint, !hint.isEmpty {
                    detectedTitle = hint
                }
                let item = PendingItem(
                    gid: parsed.gid,
                    task: parsed.task,
                    sectionKey: forcedSectionKey ?? parsed.sectionKey,
                    parentRef: parsed.parentRef
                )
                if let pRef = item.parentRef, !pRef.isEmpty {
                    pendingSubs.append(item)
                } else {
                    let idx = roots.count
                    roots.append(item)
                    if !item.gid.isEmpty && rootIdxByGid[item.gid] == nil {
                        rootIdxByGid[item.gid] = idx
                    }
                    let low = item.task.title.lowercased()
                    if rootIdxByNameLower[low] == nil {
                        rootIdxByNameLower[low] = idx
                    }
                }
            }

            for sub in pendingSubs {
                let ref = sub.parentRef ?? ""
                if let pIdx = rootIdxByGid[ref] ?? rootIdxByNameLower[ref.lowercased()] {
                    roots[pIdx].task.checklistItems.append(
                        ExternalImportedChecklistItem(
                            title: sub.task.title,
                            isCompleted: sub.task.status == .completed
                        )
                    )
                } else {
                    roots.append(sub)
                }
            }

            for root in roots {
                if let secKey = root.sectionKey, phasesByLowerTitle[secKey] != nil {
                    phasesByLowerTitle[secKey]?.tasks.append(root.task)
                } else {
                    ungroupedTasks.append(root.task)
                }
            }
        }

        if let rootArray = jsonObj as? [[String: Any]] {
            ingestFlatTaskArray(rootArray)
        } else if let rootDict = jsonObj as? [String: Any] {
            // 1. Project metadata (`project` can be String or Dictionary, or top-level `title` / `name`)
            if let projStr = rootDict["project"] as? String {
                let clean = projStr.trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty { detectedTitle = clean }
            } else if let projDict = rootDict["project"] as? [String: Any] {
                let pTitle = ((projDict["name"] as? String) ?? (projDict["title"] as? String) ?? "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !pTitle.isEmpty { detectedTitle = pTitle }
                if let pNotes = (projDict["notes"] as? String) ?? (projDict["description"] as? String), !pNotes.isEmpty {
                    detectedNotes = pNotes
                }
                projectTags.append(contentsOf: extractJsonTagNames(projDict["tags"] ?? projDict["tagNames"]))
                projectWorkspaceId = extractJsonEntityIdentifier(projDict["workspace"] ?? projDict["workspaceId"] ?? projDict["workspace_id"])
            }

            if detectedTitle == nil {
                let topTitle = ((rootDict["title"] as? String) ?? (rootDict["name"] as? String) ?? "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !topTitle.isEmpty { detectedTitle = topTitle }
            }
            if detectedNotes.isEmpty, let topNotes = (rootDict["notes"] as? String) ?? (rootDict["description"] as? String) {
                detectedNotes = topNotes
            }
            projectTags.append(contentsOf: extractJsonTagNames(rootDict["tags"] ?? rootDict["tagNames"]))
            if projectWorkspaceId == nil {
                projectWorkspaceId = extractJsonEntityIdentifier(rootDict["workspace"] ?? rootDict["workspaceId"] ?? rootDict["workspace_id"])
            }

            // 2. Structured `sections` / `phases`
            let rawSections = (rootDict["sections"] as? [Any]) ?? (rootDict["phases"] as? [Any]) ?? []
            for secAny in rawSections {
                if let secStr = secAny as? String {
                    _ = ensurePhase(title: secStr)
                } else if let secDict = secAny as? [String: Any] {
                    let sTitle = ((secDict["name"] as? String) ?? (secDict["title"] as? String) ?? "")
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                    let sGid = stringValue(secDict["gid"] ?? secDict["id"])
                    let sStart = parseJsonDateField(secDict["start_on"] ?? secDict["startDate"], calendar: calendar)
                    let sEnd = parseJsonDateField(secDict["due_on"] ?? secDict["endDate"], calendar: calendar)
                    let secKey = ensurePhase(title: sTitle, gid: sGid, startDate: sStart, endDate: sEnd)
                    if let nestedTasks = secDict["tasks"] as? [[String: Any]], !nestedTasks.isEmpty {
                        ingestFlatTaskArray(nestedTasks, forcedSectionKey: secKey)
                    }
                }
            }

            // 3. Ungrouped / root tasks (`ungroupedTasks`, `tasks`, `data`)
            if let explicitUngrouped = rootDict["ungroupedTasks"] as? [[String: Any]] {
                ingestFlatTaskArray(explicitUngrouped, forcedSectionKey: nil)
            }
            if let structuredTasks = rootDict["tasks"] as? [[String: Any]] {
                ingestFlatTaskArray(structuredTasks)
            }
            if let restDataTasks = rootDict["data"] as? [[String: Any]] {
                ingestFlatTaskArray(restDataTasks)
            } else if let singleDataObj = rootDict["data"] as? [String: Any] {
                // Asana single-project or wrapper `{ "data": { "name": ..., "tasks": [...] } }`
                if detectedTitle == nil {
                    let dName = ((singleDataObj["name"] as? String) ?? (singleDataObj["title"] as? String) ?? "")
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                    if !dName.isEmpty { detectedTitle = dName }
                }
                if detectedNotes.isEmpty, let dNotes = singleDataObj["notes"] as? String {
                    detectedNotes = dNotes
                }
                if let nested = singleDataObj["tasks"] as? [[String: Any]] {
                    ingestFlatTaskArray(nested)
                }
            }
        } else {
            throw ExternalProjectCodecError.invalidJson("Root JSON element must be an object or array.")
        }

        let phases = phaseOrder.compactMap { key -> ExternalImportedPhase? in
            guard let bucket = phasesByLowerTitle[key] else { return nil }
            return ExternalImportedPhase(
                title: bucket.title,
                startDate: bucket.startDate,
                endDate: bucket.endDate,
                tasks: bucket.tasks
            )
        }

        let dedupedProjectTags = deduplicateStringsCaseInsensitive(projectTags)
        return ExternalImportedProject(
            title: nonEmptyOrFallback(detectedTitle, fallback: fallbackTitle),
            notes: detectedNotes,
            ungroupedTasks: ungroupedTasks,
            phases: phases,
            tagNames: dedupedProjectTags,
            workspaceId: projectWorkspaceId
        )
    }

    public static func exportAsanaJson(
        project: Project,
        headings: [Heading],
        tasks: [TaskItem],
        tagsById: [String: Tag] = [:],
        calendar: Calendar = .current
    ) throws -> Data {
        let isoDay = makeIsoDateFormatter(calendar: calendar)
        let isoTimestamp = ISO8601DateFormatter()
        isoTimestamp.formatOptions = [.withInternetDateTime]

        let formatDay: (Int64?) -> Any = { ms in
            guard let ms, ms > 0 else { return NSNull() }
            return isoDay.string(from: DateEpochConverter.date(fromMillis: ms))
        }

        let orderedGroups = groupTasksForExport(headings: headings, tasks: tasks)
        let resolvedProjectTags: [[String: String]] = project.tagIds.compactMap { tid in
            guard let tag = tagsById[tid], !tag.isDeleted else { return nil }
            return ["gid": tag.id, "name": tag.title]
        }

        var projectObj: [String: Any] = [
            "gid": project.id,
            "name": project.title,
            "notes": project.notes,
            "tags": resolvedProjectTags
        ]
        if let ws = project.workspaceId, !ws.isEmpty {
            projectObj["workspace"] = ["gid": ws, "name": ws]
        }

        let sectionsJson: [[String: Any]] = orderedGroups.phases.map { phase in
            var secObj: [String: Any] = [
                "gid": phase.heading.id,
                "name": phase.heading.title,
                "start_on": formatDay(phase.heading.startDate),
                "due_on": formatDay(phase.heading.endDate)
            ]
            if phase.heading.startDate == nil { secObj.removeValue(forKey: "start_on") }
            if phase.heading.endDate == nil { secObj.removeValue(forKey: "due_on") }
            return secObj
        }

        func serializeTask(_ task: TaskItem, heading: Heading?) -> [String: Any] {
            let isDone = task.status == .completed
            let completedAtValue: Any
            if isDone {
                let ms = task.completedAt ?? (task.updatedAt > 0 ? task.updatedAt : Int64(Date().timeIntervalSince1970 * 1000))
                completedAtValue = isoTimestamp.string(from: DateEpochConverter.date(fromMillis: ms))
            } else {
                completedAtValue = NSNull()
            }

            let subtasksJson: [[String: Any]] = task.checklistItems
                .filter { !$0.isDeleted }
                .sorted {
                    if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                    return $0.createdAt < $1.createdAt
                }
                .map { chk in
                    [
                        "gid": chk.id,
                        "name": chk.title,
                        "completed": chk.isCompleted
                    ]
                }

            var membershipEntry: [String: Any] = [
                "project": [
                    "gid": project.id,
                    "name": project.title
                ]
            ]
            if let heading {
                membershipEntry["section"] = [
                    "gid": heading.id,
                    "name": heading.title
                ]
            } else {
                membershipEntry["section"] = NSNull()
            }

            var obj: [String: Any] = [
                "gid": task.id,
                "name": task.title,
                "notes": task.notes,
                "completed": isDone,
                "completed_at": completedAtValue,
                "start_on": formatDay(task.whenDate),
                "due_on": formatDay(task.deadline),
                "hearted": task.isPinned,
                "tags": task.tags.map { ["gid": $0.id, "name": $0.title] },
                "memberships": [membershipEntry],
                "subtasks": subtasksJson
            ]

            if let effort = TaskEffortLevel.normalized(task.effortLevel)?.rawValue {
                obj["effort_level"] = effort
            }
            if let assignee = task.assigneeId, !assignee.isEmpty {
                obj["assignee"] = ["gid": assignee, "name": assignee]
            } else {
                obj["assignee"] = NSNull()
            }
            if let ws = task.workspaceId, !ws.isEmpty {
                obj["workspace"] = ["gid": ws, "name": ws]
            }
            return obj
        }

        var dataArray: [[String: Any]] = []
        for task in orderedGroups.ungrouped {
            dataArray.append(serializeTask(task, heading: nil))
        }
        for phase in orderedGroups.phases {
            for task in phase.tasks {
                dataArray.append(serializeTask(task, heading: phase.heading))
            }
        }

        let root: [String: Any] = [
            "project": projectObj,
            "sections": sectionsJson,
            "data": dataArray
        ]

        return try JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys])
    }

    // MARK: - 3. Microsoft Planner CSV & .xlsx Import / Export

    public static func importPlannerCsv(
        _ csvText: String,
        fallbackTitle: String = "Imported Project",
        calendar: Calendar = .current
    ) throws -> ExternalImportedProject {
        let cleaned = stripUTF8BOM(csvText).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else {
            throw ExternalProjectCodecError.emptyInput
        }
        let firstLine = cleaned.components(separatedBy: .newlines).first ?? ""
        let delimiter = detectCsvDelimiter(headerLine: firstLine)
        let rows = CsvCodec.parseCsvRows(text: cleaned, delimiter: delimiter)
        return try parsePlannerTableRows(rows, projectTitle: fallbackTitle, calendar: calendar)
    }

    public static func exportPlannerCsv(
        project: Project,
        headings: [Heading],
        tasks: [TaskItem],
        calendar: Calendar = .current
    ) -> Data {
        let csvString = exportPlannerCsvString(
            project: project,
            headings: headings,
            tasks: tasks,
            calendar: calendar
        )
        return Data(csvString.utf8)
    }

    public static func exportPlannerCsvString(
        project: Project,
        headings: [Heading],
        tasks: [TaskItem],
        calendar: Calendar = .current
    ) -> String {
        let iso = makeIsoDateFormatter(calendar: calendar)
        let formatMillis: (Int64?) -> String = { ms in
            guard let ms, ms > 0 else { return "" }
            return iso.string(from: DateEpochConverter.date(fromMillis: ms))
        }
        let todayEpoch = DateEpochConverter.startOfDayMillis(for: Date(), calendar: calendar)
        let tableRows = buildPlannerExportRows(
            headings: headings,
            tasks: tasks,
            todayEpoch: todayEpoch,
            formatDate: formatMillis
        )

        var out = "\u{FEFF}" + plannerColumns.map(escapeCsvCell).joined(separator: ",") + "\n"
        for row in tableRows {
            out += row.map(escapeCsvCell).joined(separator: ",") + "\n"
        }
        return out
    }

    public static func importPlannerXlsx(
        _ data: Data,
        fallbackTitle: String = "Imported Project",
        calendar: Calendar = .current
    ) throws -> ExternalImportedProject {
        let entries = try XlsxZipArchive.readEntries(from: data, maxEntryBytes: maxDecompressedEntryBytes)

        let sharedStrings: [String]
        if let sstData = findZipEntry(entries, path: "xl/sharedStrings.xml") {
            sharedStrings = try XlsxXMLReader.parseSharedStrings(sstData)
        } else {
            sharedStrings = []
        }

        var workbookSheetTitle: String? = nil
        var primarySheetRelId: String? = nil
        if let wbData = findZipEntry(entries, path: "xl/workbook.xml") {
            let wbInfo = XlsxXMLReader.parseWorkbookInfo(wbData)
            workbookSheetTitle = wbInfo.firstSheetName
            primarySheetRelId = wbInfo.firstSheetRelId
        }

        var resolvedWorksheetPath = "xl/worksheets/sheet1.xml"
        if let relId = primarySheetRelId,
           let relsData = findZipEntry(entries, path: "xl/_rels/workbook.xml.rels"),
           let target = XlsxXMLReader.parseWorkbookRelationshipTarget(relsData, relId: relId) {
            let cleanTarget = target.hasPrefix("/")
                ? String(target.dropFirst())
                : (target.hasPrefix("xl/") ? target : "xl/\(target)")
            if findZipEntry(entries, path: cleanTarget) != nil {
                resolvedWorksheetPath = cleanTarget
            }
        }

        let sheetData: Data
        if let exact = findZipEntry(entries, path: resolvedWorksheetPath) {
            sheetData = exact
        } else if let fallbackSheet = entries.keys.sorted().first(where: {
            $0.lowercased().hasPrefix("xl/worksheets/") && $0.lowercased().hasSuffix(".xml")
        }), let found = entries[fallbackSheet] {
            sheetData = found
        } else {
            throw ExternalProjectCodecError.invalidXlsx("Missing worksheet XML in .xlsx archive.")
        }

        let rows = try XlsxXMLReader.parseWorksheetRows(sheetData, sharedStrings: sharedStrings)
        let resolvedTitle = resolveXlsxProjectTitle(sheetName: workbookSheetTitle, fallback: fallbackTitle)
        return try parsePlannerTableRows(rows, projectTitle: resolvedTitle, calendar: calendar)
    }

    public static func exportPlannerXlsx(
        project: Project,
        headings: [Heading],
        tasks: [TaskItem],
        compressEntries: Bool = true,
        calendar: Calendar = .current
    ) throws -> Data {
        let todayEpoch = DateEpochConverter.startOfDayMillis(for: Date(), calendar: calendar)
        let orderedGroups = groupTasksForExport(headings: headings, tasks: tasks)

        enum CellSpec {
            case text(String)
            case excelDateSerial(Int)
        }

        var sheetRows: [[CellSpec]] = [
            plannerColumns.map { .text($0) }
        ]

        func makeTaskRow(_ task: TaskItem, bucketName: String) -> [CellSpec] {
            let isDone = task.status == .completed
            let progress = isDone ? "Completed" : "Not started"
            let priority = formatTaskToPlannerPriority(task)
            let isLate: String = {
                guard !isDone, let dl = task.deadline, dl < todayEpoch else { return "false" }
                return "true"
            }()

            let activeChecklists = task.checklistItems
                .filter { !$0.isDeleted }
                .sorted {
                    if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                    return $0.createdAt < $1.createdAt
                }
            let allChecklistsStr = activeChecklists.map { sanitizeSemicolonToken($0.title) }.joined(separator: ";")
            let doneChecklistsStr = activeChecklists
                .filter(\.isCompleted)
                .map { sanitizeSemicolonToken($0.title) }
                .joined(separator: ";")
            let labelsStr = task.tags.map { sanitizeSemicolonToken($0.title) }.joined(separator: ";")

            let dateCell: (Int64?) -> CellSpec = { ms in
                guard let ms, ms > 0,
                      let serial = epochMillisToExcelSerialDay(ms, calendar: calendar) else {
                    return .text("")
                }
                return .excelDateSerial(serial)
            }

            return [
                .text(task.id),
                .text(task.title),
                .text(bucketName),
                .text(progress),
                .text(priority),
                .text(task.assigneeId ?? ""),
                .text(""),
                dateCell(task.createdAt),
                dateCell(task.whenDate),
                dateCell(task.deadline),
                .text(isLate),
                isDone ? dateCell(task.completedAt ?? task.updatedAt) : .text(""),
                .text(task.notes),
                .text(doneChecklistsStr),
                .text(allChecklistsStr),
                .text(labelsStr)
            ]
        }

        for task in orderedGroups.ungrouped {
            sheetRows.append(makeTaskRow(task, bucketName: ""))
        }
        for phase in orderedGroups.phases {
            for task in phase.tasks {
                sheetRows.append(makeTaskRow(task, bucketName: phase.heading.title))
            }
        }

        // Build deduplicated sharedStrings table
        var uniqueStrings: [String] = []
        var sharedIndexByString: [String: Int] = [:]
        var totalStringRefs = 0

        func internString(_ raw: String) -> Int {
            let safe = stripInvalidXmlCharacters(raw)
            totalStringRefs += 1
            if let existing = sharedIndexByString[safe] {
                return existing
            }
            let idx = uniqueStrings.count
            uniqueStrings.append(safe)
            sharedIndexByString[safe] = idx
            return idx
        }

        var worksheetBody = ""
        for (rZero, cols) in sheetRows.enumerated() {
            let rOne = rZero + 1
            worksheetBody += "<row r=\"\(rOne)\">"
            for (cZero, cell) in cols.enumerated() {
                let ref = "\(excelColumnLetters(for: cZero))\(rOne)"
                let isHeader = rZero == 0
                switch cell {
                case .text(let str):
                    if str.isEmpty && !isHeader {
                        continue
                    }
                    let sstIdx = internString(str)
                    let styleAttr = isHeader ? " s=\"1\"" : ""
                    worksheetBody += "<c r=\"\(ref)\" t=\"s\"\(styleAttr)><v>\(sstIdx)</v></c>"
                case .excelDateSerial(let serial):
                    worksheetBody += "<c r=\"\(ref)\" s=\"2\"><v>\(serial)</v></c>"
                }
            }
            worksheetBody += "</row>"
        }

        let sheetTitle = sanitizeExcelSheetName(project.title)

        let contentTypesXML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
          <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
        </Types>
        """

        let rootRelsXML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
        """

        let workbookXML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="\(escapeXmlAttr(sheetTitle))" sheetId="1" r:id="rId1"/>
          </sheets>
        </workbook>
        """

        let workbookRelsXML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
        </Relationships>
        """

        let stylesXML = """
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
        """

        var sstItemsXML = ""
        for s in uniqueStrings {
            sstItemsXML += "<si><t xml:space=\"preserve\">\(escapeXmlText(s))</t></si>"
        }
        let sharedStringsXML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="\(totalStringRefs)" uniqueCount="\(uniqueStrings.count)">\(sstItemsXML)</sst>
        """

        let worksheetXML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>\(worksheetBody)</sheetData>
        </worksheet>
        """

        let zipInputs: [(path: String, data: Data, compress: Bool)] = [
            ("[Content_Types].xml", Data(contentTypesXML.utf8), compressEntries),
            ("_rels/.rels", Data(rootRelsXML.utf8), compressEntries),
            ("xl/workbook.xml", Data(workbookXML.utf8), compressEntries),
            ("xl/_rels/workbook.xml.rels", Data(workbookRelsXML.utf8), compressEntries),
            ("xl/styles.xml", Data(stylesXML.utf8), compressEntries),
            ("xl/sharedStrings.xml", Data(sharedStringsXML.utf8), compressEntries),
            ("xl/worksheets/sheet1.xml", Data(worksheetXML.utf8), compressEntries)
        ]

        return try XlsxZipArchive.writeEntries(zipInputs)
    }

    // MARK: - Planner Row Parser & Builder

    private static func parsePlannerTableRows(
        _ rows: [[String]],
        projectTitle: String,
        calendar: Calendar
    ) throws -> ExternalImportedProject {
        guard !rows.isEmpty else {
            throw ExternalProjectCodecError.emptyInput
        }

        // Locate header row (some Excel exports include a blank or title row above headers)
        var headerRowIndex = 0
        for r in 0..<min(rows.count, 5) {
            let norm = Set(rows[r].map(normalizeHeaderKey))
            if looksLikePlannerHeaders(norm) || norm.contains("taskname") || norm.contains("name") {
                headerRowIndex = r
                break
            }
        }

        let normalizedHeader = rows[headerRowIndex].map(normalizeHeaderKey)
        let colIdx: ([String]) -> Int = { candidates in
            normalizedHeader.firstIndex(where: { candidates.contains($0) }) ?? -1
        }

        let idxName = colIdx([
            "taskname", "aufgabenname", "nomdelatache", "nombredelatarea", "nomeattivita",
            "name", "title", "task", "aufgabe"
        ])
        let idxBucket = colIdx([
            "bucketname", "bucket", "nomducompartiment", "nombredeldeposito", "nomebucket",
            "sectioncolumn", "section", "phase"
        ])
        let idxProgress = colIdx([
            "progress", "fortschritt", "progression", "progreso", "avanzamento", "status"
        ])
        let idxPriority = colIdx([
            "priority", "prioritaet", "prioritat", "priorite", "prioridad", "priorita"
        ])
        let idxAssignedTo = colIdx([
            "assignedto", "zugewiesenzu", "zugewiesenan", "attribuea", "asignadoa", "assegnatoa", "assignee"
        ])
        let idxStart = colIdx([
            "startdate", "startdatum", "beginndatum", "datededebut", "fechadeinicio", "datainizio", "start"
        ])
        let idxDue = colIdx([
            "duedate", "faelligkeitsdatum", "falligkeitsdatum", "datedecheance", "fechadevencimiento", "datascadenza", "deadline"
        ])
        let idxCompletedDate = colIdx([
            "completeddate", "abschlussdatum", "erledigungsdatum", "datedachevement", "fechadefinalizacion", "datacompletamento", "completedat"
        ])
        let idxDesc = colIdx([
            "description", "beschreibung", "descripcion", "descrizione", "notes", "notizen"
        ])
        let idxCompletedChecklist = colIdx([
            "completedchecklistitems",
            "abgeschlossenechecklistenelemente",
            "erledigtechecklistenelemente",
            "elementsdelistedecontroletermines",
            "elementosdelistadecomprobacioncompletados",
            "elementidielencodicontrollocompletati"
        ])
        let idxChecklist = colIdx([
            "checklistitems",
            "checklistenelemente",
            "elementsdelistedecontrole",
            "elementosdelistadecomprobacion",
            "elementidielencodicontrollo",
            "checklist"
        ])
        let idxLabels = colIdx([
            "labels", "bezeichnungen", "kennzeichnungen", "etiquettes", "etiquetas", "etichette", "tags", "categories"
        ])

        guard idxName >= 0 else {
            throw ExternalProjectCodecError.invalidCsv("Missing 'Task Name' column in Microsoft Planner table.")
        }

        var ungrouped: [ExternalImportedTask] = []
        var phaseOrder: [String] = []
        var phasesByKey: [String: (title: String, tasks: [ExternalImportedTask])] = [:]

        for r in (headerRowIndex + 1)..<rows.count {
            let cols = rows[r]
            let cell: (Int) -> String = { idx in
                guard idx >= 0 && idx < cols.count else { return "" }
                return cols[idx].trimmingCharacters(in: .whitespacesAndNewlines)
            }

            let title = cell(idxName)
            if title.isEmpty { continue }

            let bucketRaw = cell(idxBucket)
            let progressRaw = cell(idxProgress)
            let priorityRaw = cell(idxPriority)
            let assignedRaw = cell(idxAssignedTo)
            let startRaw = cell(idxStart)
            let dueRaw = cell(idxDue)
            let completedDateRaw = cell(idxCompletedDate)
            let descRaw = cell(idxDesc)
            let completedChkRaw = cell(idxCompletedChecklist)
            let chkRaw = cell(idxChecklist)
            let labelsRaw = cell(idxLabels)

            let isDone = isPlannerProgressCompleted(progress: progressRaw, completedDate: completedDateRaw)
            let completedAtMs = isDone ? parseExternalTimestampOrDate(completedDateRaw, calendar: calendar) : nil
            let whenMs = parseExternalDate(startRaw, calendar: calendar)
            let dueMs = parseExternalDate(dueRaw, calendar: calendar)

            let isPinned = isPriorityUrgent(priorityRaw)
            let effort = parsePriorityToEffort(priorityRaw)
            let checklists = parsePlannerChecklistItems(
                allChecklistRaw: chkRaw,
                completedChecklistRaw: completedChkRaw
            )
            let tags = splitDeduplicatedTags(labelsRaw)

            let task = ExternalImportedTask(
                title: title,
                notes: descRaw,
                status: isDone ? .completed : .open,
                completedAt: completedAtMs,
                whenDate: whenMs,
                deadline: dueMs,
                isPinned: isPinned,
                effortLevel: effort,
                tagNames: tags,
                checklistItems: checklists,
                assigneeId: assignedRaw.isEmpty ? nil : assignedRaw
            )

            if isUngroupedSectionName(bucketRaw) {
                ungrouped.append(task)
            } else {
                let key = bucketRaw.lowercased()
                if phasesByKey[key] == nil {
                    phaseOrder.append(key)
                    phasesByKey[key] = (title: bucketRaw, tasks: [task])
                } else {
                    phasesByKey[key]?.tasks.append(task)
                }
            }
        }

        let phases = phaseOrder.compactMap { key -> ExternalImportedPhase? in
            guard let entry = phasesByKey[key] else { return nil }
            return ExternalImportedPhase(title: entry.title, tasks: entry.tasks)
        }

        return ExternalImportedProject(
            title: nonEmptyOrFallback(projectTitle, fallback: "Imported Project"),
            notes: "",
            ungroupedTasks: ungrouped,
            phases: phases,
            tagNames: []
        )
    }

    private static func buildPlannerExportRows(
        headings: [Heading],
        tasks: [TaskItem],
        todayEpoch: Int64,
        formatDate: (Int64?) -> String
    ) -> [[String]] {
        let orderedGroups = groupTasksForExport(headings: headings, tasks: tasks)
        var rows: [[String]] = []

        func encodeRow(_ task: TaskItem, bucketName: String) -> [String] {
            let isDone = task.status == .completed
            let progress = isDone ? "Completed" : "Not started"
            let priority = formatTaskToPlannerPriority(task)
            let isLate = (!isDone && (task.deadline.map { $0 < todayEpoch } == true)) ? "true" : "false"

            let activeChecklists = task.checklistItems
                .filter { !$0.isDeleted }
                .sorted {
                    if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                    return $0.createdAt < $1.createdAt
                }
            let allChecklistsStr = activeChecklists.map { sanitizeSemicolonToken($0.title) }.joined(separator: ";")
            let doneChecklistsStr = activeChecklists
                .filter(\.isCompleted)
                .map { sanitizeSemicolonToken($0.title) }
                .joined(separator: ";")
            let labelsStr = task.tags.map { sanitizeSemicolonToken($0.title) }.joined(separator: ";")

            return [
                task.id,
                task.title,
                bucketName,
                progress,
                priority,
                task.assigneeId ?? "",
                "",
                formatDate(task.createdAt),
                formatDate(task.whenDate),
                formatDate(task.deadline),
                isLate,
                isDone ? formatDate(task.completedAt ?? task.updatedAt) : "",
                task.notes,
                doneChecklistsStr,
                allChecklistsStr,
                labelsStr
            ]
        }

        for task in orderedGroups.ungrouped {
            rows.append(encodeRow(task, bucketName: ""))
        }
        for phase in orderedGroups.phases {
            for task in phase.tasks {
                rows.append(encodeRow(task, bucketName: phase.heading.title))
            }
        }
        return rows
    }

    // MARK: - Date, Priority & Checklist Parsing Helpers

    public static func parseExternalDate(_ raw: String, calendar: Calendar = .current) -> Int64? {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return nil }

        // 1. Excel serial date numbers (e.g. "46154" or "46154.0" or "46154.5") using Excel 1900 base 1899-12-30
        if value.range(of: #"^\d+(\.\d+)?$"#, options: .regularExpression) != nil,
           let serial = Double(value),
           serial.isFinite,
           serial >= 1.0 && serial <= 200_000.0 {
            let wholeDays = Int(floor(serial))
            if let baseDate = calendar.date(from: DateComponents(year: 1899, month: 12, day: 30)),
               let targetDate = calendar.date(byAdding: .day, value: wholeDays, to: baseDate) {
                return DateEpochConverter.startOfDayMillis(for: targetDate, calendar: calendar)
            }
        }

        // 2. Standard CSV / ISO / European / US date parsing
        if let parsed = CsvCodec.parseFlexibleDateToEpoch(raw: value, calendar: calendar) {
            return parsed
        }

        // 3. Additional short-year & slash formats exported by localized Excel / Planner
        let extraFormats = ["MM/dd/yy", "M/d/yy", "dd/MM/yyyy", "d/M/yyyy", "yyyy-M-d"]
        let df = DateFormatter()
        df.calendar = calendar
        df.locale = Locale(identifier: "en_US_POSIX")
        let dateHead = value.components(separatedBy: " ").first ?? value
        for fmt in extraFormats {
            df.dateFormat = fmt
            if let d = df.date(from: dateHead) {
                return DateEpochConverter.startOfDayMillis(for: d, calendar: calendar)
            }
        }

        return nil
    }

    private static func parseExternalTimestampOrDate(_ raw: String, calendar: Calendar) -> Int64? {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return nil }

        let isoFrac = ISO8601DateFormatter()
        isoFrac.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = isoFrac.date(from: value) {
            return Int64(d.timeIntervalSince1970 * 1000.0)
        }

        let isoPlain = ISO8601DateFormatter()
        isoPlain.formatOptions = [.withInternetDateTime]
        if let d = isoPlain.date(from: value) {
            return Int64(d.timeIntervalSince1970 * 1000.0)
        }

        return parseExternalDate(value, calendar: calendar)
    }

    private static func epochMillisToExcelSerialDay(_ millis: Int64, calendar: Calendar) -> Int? {
        guard let rawBase = calendar.date(from: DateComponents(year: 1899, month: 12, day: 30)) else {
            return nil
        }
        let baseStart = calendar.startOfDay(for: rawBase)
        let targetStart = calendar.startOfDay(for: DateEpochConverter.date(fromMillis: millis))
        guard let days = calendar.dateComponents([.day], from: baseStart, to: targetStart).day,
              days >= 1 else {
            return nil
        }
        return days
    }

    private static func parseJsonDateField(_ raw: Any?, calendar: Calendar) -> Int64? {
        guard let raw, !(raw is NSNull) else { return nil }
        if let s = raw as? String {
            return parseExternalDate(s, calendar: calendar)
        }
        if let num = int64Value(raw) {
            return parseExternalDate(String(num), calendar: calendar)
        }
        if let dbl = raw as? Double, dbl.isFinite {
            return parseExternalDate(String(dbl), calendar: calendar)
        }
        return nil
    }

    private static func parsePlannerChecklistItems(
        allChecklistRaw: String,
        completedChecklistRaw: String
    ) -> [ExternalImportedChecklistItem] {
        let splitTokens: (String) -> [String] = { input in
            let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return [] }
            var out: [String] = []
            var buf = ""
            let chars = Array(trimmed)
            var i = 0
            while i < chars.count {
                let ch = chars[i]
                if ch == "\\" && i + 1 < chars.count && (chars[i + 1] == ";" || chars[i + 1] == "\\") {
                    buf.append(chars[i + 1])
                    i += 2
                    continue
                }
                if ch == ";" || ch == "\n" || ch == "\r" {
                    if ch == "\r" && i + 1 < chars.count && chars[i + 1] == "\n" {
                        i += 1
                    }
                    let token = buf.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !token.isEmpty {
                        out.append(token)
                    }
                    buf = ""
                } else {
                    buf.append(ch)
                }
                i += 1
            }
            let tail = buf.trimmingCharacters(in: .whitespacesAndNewlines)
            if !tail.isEmpty {
                out.append(tail)
            }
            return out
        }

        let stripCheckboxPrefix: (String) -> (title: String, explicitChecked: Bool?) = { rawItem in
            let lower = rawItem.lowercased()
            if lower.hasPrefix("[x]") || rawItem.hasPrefix("✓") || rawItem.hasPrefix("☑") {
                let cleaned = rawItem.replacingOccurrences(
                    of: #"^(\[[xX]\]|[✓☑])\s*"#,
                    with: "",
                    options: .regularExpression
                ).trimmingCharacters(in: .whitespacesAndNewlines)
                return (cleaned, true)
            }
            if lower.hasPrefix("[ ]") || rawItem.hasPrefix("☐") {
                let cleaned = rawItem.replacingOccurrences(
                    of: #"^(\[ \]|☐)\s*"#,
                    with: "",
                    options: .regularExpression
                ).trimmingCharacters(in: .whitespacesAndNewlines)
                return (cleaned, false)
            }
            return (rawItem, nil)
        }

        let allTokens = splitTokens(allChecklistRaw)
        let trimmedCompleted = completedChecklistRaw.trimmingCharacters(in: .whitespacesAndNewlines)

        // Case 1: Completed column is empty
        if trimmedCompleted.isEmpty {
            return allTokens.compactMap { tok in
                let parsed = stripCheckboxPrefix(tok)
                guard !parsed.title.isEmpty else { return nil }
                return ExternalImportedChecklistItem(
                    title: parsed.title,
                    isCompleted: parsed.explicitChecked ?? false
                )
            }
        }

        // Case 2: Completed column is numeric ratio "N/M" (e.g. "2/3")
        if let slashIdx = trimmedCompleted.firstIndex(of: "/") {
            let leftStr = trimmedCompleted[..<slashIdx].trimmingCharacters(in: .whitespaces)
            let rightStr = trimmedCompleted[trimmedCompleted.index(after: slashIdx)...].trimmingCharacters(in: .whitespaces)
            if let doneCount = Int(leftStr), Int(rightStr) != nil, doneCount >= 0 {
                var remainingToCheck = doneCount
                var items: [ExternalImportedChecklistItem] = []
                for tok in allTokens {
                    let parsed = stripCheckboxPrefix(tok)
                    guard !parsed.title.isEmpty else { continue }
                    let isChecked: Bool
                    if let explicit = parsed.explicitChecked {
                        isChecked = explicit
                        if explicit && remainingToCheck > 0 { remainingToCheck -= 1 }
                    } else if remainingToCheck > 0 {
                        isChecked = true
                        remainingToCheck -= 1
                    } else {
                        isChecked = false
                    }
                    items.append(ExternalImportedChecklistItem(title: parsed.title, isCompleted: isChecked))
                }
                return items
            }
        }

        // Case 3: Completed column is ';' or '\n'-separated list of completed item titles
        let completedTokens = splitTokens(trimmedCompleted).compactMap { tok -> String? in
            let parsed = stripCheckboxPrefix(tok)
            return parsed.title.isEmpty ? nil : parsed.title
        }

        var remainingCompletedPool = completedTokens
        var result: [ExternalImportedChecklistItem] = []

        for tok in allTokens {
            let parsed = stripCheckboxPrefix(tok)
            guard !parsed.title.isEmpty else { continue }
            if let matchIdx = remainingCompletedPool.firstIndex(where: {
                $0.caseInsensitiveCompare(parsed.title) == .orderedSame
            }) {
                remainingCompletedPool.remove(at: matchIdx)
                result.append(ExternalImportedChecklistItem(title: parsed.title, isCompleted: true))
            } else {
                result.append(
                    ExternalImportedChecklistItem(
                        title: parsed.title,
                        isCompleted: parsed.explicitChecked ?? false
                    )
                )
            }
        }

        // Append any completed items that were omitted from the open Checklist Items column
        for extraDone in remainingCompletedPool {
            result.append(ExternalImportedChecklistItem(title: extraDone, isCompleted: true))
        }

        return result
    }

    private static func isPriorityUrgent(_ raw: String?) -> Bool {
        guard let norm = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !norm.isEmpty else { return false }
        return ["urgent", "dringend", "urgente", "1", "critical", "kritisch"].contains(norm)
    }

    private static func parsePriorityToEffort(_ raw: String?) -> EffortLevel? {
        guard let norm = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !norm.isEmpty else { return nil }
        if ["important", "wichtig", "importante", "high", "hoch", "élevée", "elevee", "alta"].contains(norm) {
            return .deepFocus
        }
        if ["low", "niedrig", "faible", "basse", "baja", "bassa"].contains(norm) {
            return .quickWin
        }
        return EffortLevel.normalized(norm)
    }

    private static func formatTaskToPlannerPriority(_ task: TaskItem) -> String {
        if task.isPinned {
            return "Urgent"
        }
        switch task.resolvedEffort {
        case .deepFocus:
            return "Important"
        case .quickWin:
            return "Low"
        case .medium, .none:
            return "Medium"
        }
    }

    private static func isPlannerProgressCompleted(progress: String, completedDate: String) -> Bool {
        let norm = progress.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let completedTokens: Set<String> = [
            "completed", "complete", "100%", "100", "done",
            "erledigt", "abgeschlossen",
            "terminé", "termine", "terminée", "terminee", "achevé",
            "completado", "completada", "finalizado",
            "completato", "completata"
        ]
        if completedTokens.contains(norm) {
            return true
        }
        let openTokens: Set<String> = [
            "not started", "in progress", "0%", "50%", "nicht begonnen", "in bearbeitung",
            "non démarré", "en cours", "no iniciado", "en curso", "non iniziata", "in corso"
        ]
        if openTokens.contains(norm) {
            return false
        }
        return isCompletedFieldTruthy(completedDate)
    }

    private static func isCompletedFieldTruthy(_ raw: String) -> Bool {
        let norm = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !norm.isEmpty else { return false }
        return !["false", "0", "no", "nein", "non", "open"].contains(norm)
    }

    // MARK: - Formatting, Grouping & String Utilities

    private static func groupTasksForExport(
        headings: [Heading],
        tasks: [TaskItem]
    ) -> (ungrouped: [TaskItem], phases: [(heading: Heading, tasks: [TaskItem])]) {
        let activeHeadings = headings
            .filter { !$0.isDeleted && !$0.isArchived }
            .sorted {
                if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                return $0.id < $1.id
            }
        let validHeadingIds = Set(activeHeadings.map(\.id))

        let exportableTasks = tasks
            .filter { !$0.isDeleted && $0.status != .canceled }
            .sorted {
                if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                if $0.createdAt != $1.createdAt { return $0.createdAt < $1.createdAt }
                return $0.id < $1.id
            }

        let ungrouped = exportableTasks.filter { task in
            guard let hid = task.headingId else { return true }
            return !validHeadingIds.contains(hid)
        }

        let phases: [(heading: Heading, tasks: [TaskItem])] = activeHeadings.map { heading in
            let secTasks = exportableTasks.filter { $0.headingId == heading.id }
            return (heading: heading, tasks: secTasks)
        }

        return (ungrouped, phases)
    }

    private static func makeIsoDateFormatter(calendar: Calendar) -> DateFormatter {
        let df = DateFormatter()
        df.calendar = calendar
        df.locale = Locale(identifier: "en_US_POSIX")
        df.dateFormat = "yyyy-MM-dd"
        return df
    }

    private static func escapeCsvCell(_ value: String) -> String {
        if value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r") || value.contains(";") {
            return "\"" + value.replacingOccurrences(of: "\"", with: "\"\"") + "\""
        }
        return value
    }

    private static func sanitizeSemicolonToken(_ value: String) -> String {
        value
            .replacingOccurrences(of: ";", with: ",")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func normalizeHeaderKey(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            .replacingOccurrences(of: #"[^a-z0-9]"#, with: "", options: .regularExpression)
    }

    private static func looksLikePlannerHeaders(_ normalizedHeaders: Set<String>) -> Bool {
        let plannerSpecific: Set<String> = [
            "bucketname", "bucket", "completedchecklistitems", "checklistitems",
            "assignedto", "fortschritt", "checklistenelemente", "nomducompartiment",
            "nombredeldeposito", "nomebucket"
        ]
        if !normalizedHeaders.isDisjoint(with: plannerSpecific) {
            return true
        }
        return normalizedHeaders.contains("taskname") && normalizedHeaders.contains("progress")
    }

    private static func detectCsvDelimiter(headerLine: String) -> Character {
        var inQuotes = false
        var commas = 0
        var semicolons = 0
        var tabs = 0
        for ch in headerLine {
            if ch == "\"" {
                inQuotes.toggle()
            } else if !inQuotes {
                if ch == "," { commas += 1 }
                else if ch == ";" { semicolons += 1 }
                else if ch == "\t" { tabs += 1 }
            }
        }
        if tabs > commas && tabs > semicolons { return "\t" }
        return semicolons > commas ? ";" : ","
    }

    private static func isUngroupedSectionName(_ raw: String) -> Bool {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if clean.isEmpty { return true }
        return ["untitled section", "(no section)", "no section", "unassigned"].contains(clean)
    }

    private static func splitDeduplicatedTags(_ raw: String) -> [String] {
        let parts = raw
            .components(separatedBy: CharacterSet(charactersIn: ";,|"))
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return deduplicateStringsCaseInsensitive(parts)
    }

    private static func deduplicateStringsCaseInsensitive(_ items: [String]) -> [String] {
        var seen = Set<String>()
        var out: [String] = []
        for item in items {
            let trimmed = item.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { continue }
            if seen.insert(trimmed.lowercased()).inserted {
                out.append(trimmed)
            }
        }
        return out
    }

    private static func extractJsonTagNames(_ raw: Any?) -> [String] {
        guard let arr = raw as? [Any] else {
            if let s = raw as? String { return splitDeduplicatedTags(s) }
            return []
        }
        var names: [String] = []
        for el in arr {
            if let s = el as? String {
                names.append(s)
            } else if let d = el as? [String: Any],
                      let n = (d["name"] as? String) ?? (d["title"] as? String) {
                names.append(n)
            }
        }
        return deduplicateStringsCaseInsensitive(names)
    }

    private static func extractJsonPersonIdentifier(_ raw: Any?) -> String? {
        guard let raw, !(raw is NSNull) else { return nil }
        if let s = raw as? String {
            let clean = s.trimmingCharacters(in: .whitespacesAndNewlines)
            return clean.isEmpty ? nil : clean
        }
        if let d = raw as? [String: Any] {
            for key in ["name", "email", "gid", "id"] {
                if let val = stringValue(d[key]), !val.isEmpty {
                    return val
                }
            }
        }
        return nil
    }

    private static func extractJsonEntityIdentifier(_ raw: Any?) -> String? {
        guard let raw, !(raw is NSNull) else { return nil }
        if let s = raw as? String {
            let clean = s.trimmingCharacters(in: .whitespacesAndNewlines)
            return clean.isEmpty ? nil : clean
        }
        if let d = raw as? [String: Any] {
            for key in ["gid", "id", "name"] {
                if let val = stringValue(d[key]), !val.isEmpty {
                    return val
                }
            }
        }
        return nil
    }

    private static func stringValue(_ raw: Any?) -> String? {
        guard let raw, !(raw is NSNull) else { return nil }
        if let s = raw as? String {
            return s.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let n = raw as? NSNumber {
            return n.stringValue
        }
        return nil
    }

    private static func boolValue(_ raw: Any?) -> Bool? {
        guard let raw, !(raw is NSNull) else { return nil }
        if let b = raw as? Bool { return b }
        if let n = raw as? NSNumber { return n.boolValue }
        if let s = (raw as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
            if ["true", "1", "yes"].contains(s) { return true }
            if ["false", "0", "no"].contains(s) { return false }
        }
        return nil
    }

    private static func int64Value(_ raw: Any?) -> Int64? {
        guard let raw, !(raw is NSNull) else { return nil }
        if let i = raw as? Int64 { return i }
        if let i = raw as? Int { return Int64(i) }
        if let n = raw as? NSNumber { return n.int64Value }
        if let s = raw as? String, let i = Int64(s.trimmingCharacters(in: .whitespacesAndNewlines)) {
            return i
        }
        return nil
    }

    private static func deriveFallbackTitle(fileNameHint: String?, fallbackTitle: String) -> String {
        guard let hint = fileNameHint?.trimmingCharacters(in: .whitespacesAndNewlines),
              !hint.isEmpty else {
            return fallbackTitle
        }
        let base = ((hint as NSString).lastPathComponent as NSString).deletingPathExtension
            .replacingOccurrences(of: #"(?i)[-_](asana|planner)$"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return base.isEmpty ? fallbackTitle : base
    }

    private static func resolveXlsxProjectTitle(sheetName: String?, fallback: String) -> String {
        if let s = sheetName?.trimmingCharacters(in: .whitespacesAndNewlines),
           !s.isEmpty {
            let genericNames: Set<String> = ["sheet1", "tabelle1", "feuil1", "foglio1", "hoja1", "tasks", "aufgaben"]
            if !genericNames.contains(s.lowercased()) {
                return s
            }
        }
        return fallback
    }

    private static func nonEmptyOrFallback(_ candidate: String?, fallback: String) -> String {
        if let clean = candidate?.trimmingCharacters(in: .whitespacesAndNewlines), !clean.isEmpty {
            return clean
        }
        let cleanFallback = fallback.trimmingCharacters(in: .whitespacesAndNewlines)
        return cleanFallback.isEmpty ? "Imported Project" : cleanFallback
    }

    private static func sanitizedFileStem(_ title: String) -> String {
        let cleaned = title
            .replacingOccurrences(of: #"[\\/:*?"<>|\r\n\t]+"#, with: "-", options: .regularExpression)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"-{2,}"#, with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-. "))
        return cleaned.isEmpty ? "project" : String(cleaned.prefix(80))
    }

    private static func sanitizeExcelSheetName(_ raw: String) -> String {
        let cleaned = stripInvalidXmlCharacters(raw)
            .replacingOccurrences(of: #"[:\\/?*\[\]\r\n\t]+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "' "))
        if cleaned.isEmpty { return "Project" }
        return String(cleaned.prefix(31))
    }

    private static func stripInvalidXmlCharacters(_ input: String) -> String {
        var scalars = String.UnicodeScalarView()
        scalars.reserveCapacity(input.unicodeScalars.count)
        for s in input.unicodeScalars {
            let v = s.value
            if v == 0x09 || v == 0x0A || v == 0x0D
                || (v >= 0x20 && v <= 0xD7FF)
                || (v >= 0xE000 && v <= 0xFFFD)
                || (v >= 0x10000 && v <= 0x10FFFF) {
                scalars.append(s)
            }
        }
        return String(scalars)
    }

    private static func escapeXmlText(_ raw: String) -> String {
        stripInvalidXmlCharacters(raw)
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
    }

    private static func escapeXmlAttr(_ raw: String) -> String {
        escapeXmlText(raw)
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&apos;")
    }

    private static func stripUTF8BOM(_ text: String) -> String {
        if text.hasPrefix("\u{FEFF}") {
            return String(text.dropFirst())
        }
        return text
    }

    private static func decodeTextData(_ data: Data) -> String? {
        if let utf8 = String(data: data, encoding: .utf8) { return utf8 }
        if let utf16 = String(data: data, encoding: .utf16) { return utf16 }
        if let cp1252 = String(data: data, encoding: .windowsCP1252) { return cp1252 }
        return String(data: data, encoding: .isoLatin1)
    }

    private static func isZipArchive(_ data: Data) -> Bool {
        guard data.count >= 4 else { return false }
        let b0 = data[data.startIndex]
        let b1 = data[data.startIndex + 1]
        let b2 = data[data.startIndex + 2]
        let b3 = data[data.startIndex + 3]
        return b0 == 0x50 && b1 == 0x4B && ((b2 == 0x03 && b3 == 0x04) || (b2 == 0x05 && b3 == 0x06))
    }

    private static func findZipEntry(_ entries: [String: Data], path: String) -> Data? {
        if let exact = entries[path] { return exact }
        let target = path.lowercased()
        return entries.first(where: { $0.key.lowercased() == target })?.value
    }

    internal static func excelColumnLetters(for zeroBasedIndex: Int) -> String {
        var n = max(0, zeroBasedIndex) + 1
        var chars: [Character] = []
        while n > 0 {
            let rem = (n - 1) % 26
            if let scalar = UnicodeScalar(65 + rem) {
                chars.append(Character(scalar))
            }
            n = (n - 1) / 26
        }
        return String(chars.reversed())
    }

    internal static func excelColumnIndex(fromCellRef cellRef: String) -> Int? {
        var col = 0
        var sawLetter = false
        for scalar in cellRef.uppercased().unicodeScalars {
            if scalar.value >= 65 && scalar.value <= 90 {
                sawLetter = true
                col = col * 26 + Int(scalar.value - 65) + 1
            } else {
                break
            }
        }
        return sawLetter ? (col - 1) : nil
    }
}

// MARK: - Pure-Swift OOXML ZIP Reader & Writer (EOCD + Central Directory + Local Headers + zlib Raw Deflate)

internal enum XlsxZipArchive {
    private static let eocdSignature: UInt32 = 0x0605_4b50
    private static let centralDirSignature: UInt32 = 0x0201_4b50
    private static let localFileSignature: UInt32 = 0x0403_4b50

    internal static func readEntries(from archive: Data, maxEntryBytes: Int) throws -> [String: Data] {
        let bytes = [UInt8](archive)
        guard bytes.count >= 22 else {
            throw ExternalProjectCodecError.invalidXlsx("File is too small to be a valid .xlsx ZIP archive.")
        }

        // Locate End of Central Directory (0x06054b50) scanning backwards over max 64 KB comment window
        let minEocdOffset = max(0, bytes.count - 65_557)
        var foundEocdOffset: Int? = nil
        var scan = bytes.count - 22
        while scan >= minEocdOffset {
            if readUInt32LE(bytes, at: scan) == eocdSignature {
                let commentLen = Int(readUInt16LE(bytes, at: scan + 20))
                if scan + 22 + commentLen <= bytes.count {
                    foundEocdOffset = scan
                    break
                }
            }
            scan -= 1
        }

        guard let eocdOffset = foundEocdOffset else {
            throw ExternalProjectCodecError.invalidXlsx("ZIP End of Central Directory record not found.")
        }

        let totalEntries = Int(readUInt16LE(bytes, at: eocdOffset + 10))
        let cdOffset = Int(readUInt32LE(bytes, at: eocdOffset + 16))
        guard cdOffset >= 0 && cdOffset <= bytes.count else {
            throw ExternalProjectCodecError.invalidXlsx("ZIP Central Directory offset is out of bounds.")
        }

        var entries: [String: Data] = [:]
        var cursor = cdOffset
        var parsedCount = 0

        while parsedCount < totalEntries && cursor + 46 <= bytes.count {
            let sig = readUInt32LE(bytes, at: cursor)
            guard sig == centralDirSignature else {
                throw ExternalProjectCodecError.invalidXlsx("Corrupted ZIP Central Directory entry signature.")
            }

            let compressionMethod = readUInt16LE(bytes, at: cursor + 10)
            let expectedCrc = readUInt32LE(bytes, at: cursor + 16)
            let compressedSize = Int(readUInt32LE(bytes, at: cursor + 20))
            let uncompressedSize = Int(readUInt32LE(bytes, at: cursor + 24))
            let nameLen = Int(readUInt16LE(bytes, at: cursor + 28))
            let extraLen = Int(readUInt16LE(bytes, at: cursor + 30))
            let commentLen = Int(readUInt16LE(bytes, at: cursor + 32))
            let localHeaderOffset = Int(readUInt32LE(bytes, at: cursor + 42))

            let nameStart = cursor + 46
            let nameEnd = nameStart + nameLen
            let nextCursor = nameEnd + extraLen + commentLen
            guard nextCursor <= bytes.count else {
                throw ExternalProjectCodecError.invalidXlsx("Truncated ZIP Central Directory entry.")
            }

            let rawName = String(decoding: bytes[nameStart..<nameEnd], as: UTF8.self)
            let normalizedName = rawName
                .replacingOccurrences(of: "\\", with: "/")
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))

            cursor = nextCursor
            parsedCount += 1

            // Skip directory entries
            if rawName.hasSuffix("/") || normalizedName.isEmpty {
                continue
            }

            guard uncompressedSize <= maxEntryBytes else {
                throw ExternalProjectCodecError.invalidXlsx("ZIP entry '\(normalizedName)' exceeds safety limit.")
            }
            guard localHeaderOffset >= 0 && localHeaderOffset + 30 <= bytes.count else {
                throw ExternalProjectCodecError.invalidXlsx("Invalid local header offset for '\(normalizedName)'.")
            }
            guard readUInt32LE(bytes, at: localHeaderOffset) == localFileSignature else {
                throw ExternalProjectCodecError.invalidXlsx("Missing local file signature for '\(normalizedName)'.")
            }

            let localNameLen = Int(readUInt16LE(bytes, at: localHeaderOffset + 26))
            let localExtraLen = Int(readUInt16LE(bytes, at: localHeaderOffset + 28))
            let dataStart = localHeaderOffset + 30 + localNameLen + localExtraLen
            let dataEnd = dataStart + compressedSize
            guard dataStart >= 0 && dataEnd <= bytes.count else {
                throw ExternalProjectCodecError.invalidXlsx("Compressed payload out of bounds for '\(normalizedName)'.")
            }

            let compressedSlice = Data(bytes[dataStart..<dataEnd])
            let decompressed: Data
            switch compressionMethod {
            case 0:
                decompressed = compressedSlice
            case 8:
                if uncompressedSize == 0 && compressedSize == 0 {
                    decompressed = Data()
                } else {
                    decompressed = try rawDeflateDecompressBounded(compressedSlice, maxBytes: maxEntryBytes)
                }
            default:
                throw ExternalProjectCodecError.invalidXlsx("Unsupported ZIP compression method \(compressionMethod).")
            }

            let actualCrc = computeCRC32(decompressed)
            if expectedCrc != 0 && actualCrc != expectedCrc {
                throw ExternalProjectCodecError.invalidXlsx("CRC-32 mismatch in '\(normalizedName)'.")
            }

            entries[normalizedName] = decompressed
        }

        return entries
    }

    internal static func writeEntries(_ inputs: [(path: String, data: Data, compress: Bool)]) throws -> Data {
        struct WrittenCentralRecord {
            let nameBytes: [UInt8]
            let method: UInt16
            let crc: UInt32
            let compressedSize: UInt32
            let uncompressedSize: UInt32
            let localHeaderOffset: UInt32
        }

        var archive = Data()
        var centralRecords: [WrittenCentralRecord] = []
        let dosTime: UInt16 = 0x0000
        let dosDate: UInt16 = 0x5821 // 2024-01-01
        let utf8Flag: UInt16 = 0x0800

        for item in inputs {
            let cleanPath = item.path
                .replacingOccurrences(of: "\\", with: "/")
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            let nameBytes = [UInt8](cleanPath.utf8)
            let crc = computeCRC32(item.data)

            let payload: Data
            let method: UInt16
            if item.compress && !item.data.isEmpty {
                payload = try rawDeflateCompress(item.data)
                method = 8
            } else {
                payload = item.data
                method = 0
            }

            let localOffset = UInt32(archive.count)

            // Local File Header (30 bytes + filename)
            appendUInt32LE(&archive, localFileSignature)
            appendUInt16LE(&archive, 20)
            appendUInt16LE(&archive, utf8Flag)
            appendUInt16LE(&archive, method)
            appendUInt16LE(&archive, dosTime)
            appendUInt16LE(&archive, dosDate)
            appendUInt32LE(&archive, crc)
            appendUInt32LE(&archive, UInt32(payload.count))
            appendUInt32LE(&archive, UInt32(item.data.count))
            appendUInt16LE(&archive, UInt16(nameBytes.count))
            appendUInt16LE(&archive, 0)
            archive.append(contentsOf: nameBytes)
            archive.append(payload)

            centralRecords.append(
                WrittenCentralRecord(
                    nameBytes: nameBytes,
                    method: method,
                    crc: crc,
                    compressedSize: UInt32(payload.count),
                    uncompressedSize: UInt32(item.data.count),
                    localHeaderOffset: localOffset
                )
            )
        }

        let cdOffset = UInt32(archive.count)
        for rec in centralRecords {
            appendUInt32LE(&archive, centralDirSignature)
            appendUInt16LE(&archive, 20) // version made by
            appendUInt16LE(&archive, 20) // version needed
            appendUInt16LE(&archive, utf8Flag)
            appendUInt16LE(&archive, rec.method)
            appendUInt16LE(&archive, dosTime)
            appendUInt16LE(&archive, dosDate)
            appendUInt32LE(&archive, rec.crc)
            appendUInt32LE(&archive, rec.compressedSize)
            appendUInt32LE(&archive, rec.uncompressedSize)
            appendUInt16LE(&archive, UInt16(rec.nameBytes.count))
            appendUInt16LE(&archive, 0) // extra length
            appendUInt16LE(&archive, 0) // comment length
            appendUInt16LE(&archive, 0) // disk number start
            appendUInt16LE(&archive, 0) // internal attrs
            appendUInt32LE(&archive, 0) // external attrs
            appendUInt32LE(&archive, rec.localHeaderOffset)
            archive.append(contentsOf: rec.nameBytes)
        }

        let cdSize = UInt32(archive.count) - cdOffset
        appendUInt32LE(&archive, eocdSignature)
        appendUInt16LE(&archive, 0)
        appendUInt16LE(&archive, 0)
        appendUInt16LE(&archive, UInt16(centralRecords.count))
        appendUInt16LE(&archive, UInt16(centralRecords.count))
        appendUInt32LE(&archive, cdSize)
        appendUInt32LE(&archive, cdOffset)
        appendUInt16LE(&archive, 0)

        return archive
    }

    // MARK: - zlib CRC-32 & Raw Deflate (-MAX_WBITS)

    internal static func computeCRC32(_ data: Data) -> UInt32 {
        guard !data.isEmpty else { return 0 }
        return data.withUnsafeBytes { rawBuffer -> UInt32 in
            guard let basePtr = rawBuffer.bindMemory(to: Bytef.self).baseAddress else { return 0 }
            var crc = zlib.crc32(0, nil, 0)
            var offset = 0
            let total = data.count
            while offset < total {
                let chunk = min(total - offset, 1_048_576)
                crc = zlib.crc32(crc, basePtr.advanced(by: offset), uInt(chunk))
                offset += chunk
            }
            return UInt32(truncatingIfNeeded: crc)
        }
    }

    internal static func rawDeflateCompress(_ input: Data) throws -> Data {
        var stream = z_stream()
        let initStatus = deflateInit2_(
            &stream,
            Z_DEFAULT_COMPRESSION,
            Z_DEFLATED,
            -MAX_WBITS,
            8,
            Z_DEFAULT_STRATEGY,
            ZLIB_VERSION,
            Int32(MemoryLayout<z_stream>.size)
        )
        guard initStatus == Z_OK else {
            throw ExternalProjectCodecError.invalidXlsx("zlib raw deflate initialization failed.")
        }
        defer { deflateEnd(&stream) }

        var output = Data()
        let chunkSize = 16_384
        var buffer = [UInt8](repeating: 0, count: chunkSize)
        let safeInput = input.isEmpty ? Data([0]) : input

        try safeInput.withUnsafeBytes { rawInput in
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: rawInput.bindMemory(to: Bytef.self).baseAddress)
            stream.avail_in = uInt(input.count)

            repeat {
                let status: Int32 = buffer.withUnsafeMutableBytes { rawOut in
                    stream.next_out = rawOut.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(chunkSize)
                    return deflate(&stream, Z_FINISH)
                }
                if status == Z_STREAM_ERROR {
                    throw ExternalProjectCodecError.invalidXlsx("zlib raw deflate stream error.")
                }
                let produced = chunkSize - Int(stream.avail_out)
                if produced > 0 {
                    output.append(buffer, count: produced)
                }
            } while stream.avail_out == 0
        }

        return output
    }

    internal static func rawDeflateDecompressBounded(_ compressed: Data, maxBytes: Int) throws -> Data {
        guard !compressed.isEmpty else {
            return Data()
        }
        var stream = z_stream()
        let initStatus = inflateInit2_(
            &stream,
            -MAX_WBITS,
            ZLIB_VERSION,
            Int32(MemoryLayout<z_stream>.size)
        )
        guard initStatus == Z_OK else {
            throw ExternalProjectCodecError.invalidXlsx("zlib raw inflate initialization failed.")
        }
        defer { inflateEnd(&stream) }

        // Append 1 dummy byte as recommended by zlib PKZIP raw inflate so streams ending on byte boundary flush cleanly
        var paddedInput = compressed
        paddedInput.append(0)

        var output = Data()
        let chunkSize = 16_384
        var buffer = [UInt8](repeating: 0, count: chunkSize)

        try paddedInput.withUnsafeBytes { rawInput in
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: rawInput.bindMemory(to: Bytef.self).baseAddress)
            stream.avail_in = uInt(paddedInput.count)

            var finished = false
            while !finished {
                let status: Int32 = buffer.withUnsafeMutableBytes { rawOut in
                    stream.next_out = rawOut.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(chunkSize)
                    return inflate(&stream, Z_NO_FLUSH)
                }
                if status == Z_NEED_DICT || status == Z_DATA_ERROR || status == Z_MEM_ERROR || status == Z_STREAM_ERROR {
                    throw ExternalProjectCodecError.invalidXlsx("Corrupted deflate stream in .xlsx entry.")
                }
                let produced = chunkSize - Int(stream.avail_out)
                if produced > 0 {
                    if output.count + produced > maxBytes {
                        throw ExternalProjectCodecError.invalidXlsx("Decompressed .xlsx entry exceeds safety limit.")
                    }
                    output.append(buffer, count: produced)
                }
                if status == Z_STREAM_END {
                    finished = true
                } else if produced == 0 && stream.avail_in == 0 {
                    finished = true
                }
            }
        }

        return output
    }

    // MARK: - Little-Endian Integer Helpers

    private static func readUInt16LE(_ bytes: [UInt8], at offset: Int) -> UInt16 {
        UInt16(bytes[offset]) | (UInt16(bytes[offset + 1]) << 8)
    }

    private static func readUInt32LE(_ bytes: [UInt8], at offset: Int) -> UInt32 {
        UInt32(bytes[offset])
            | (UInt32(bytes[offset + 1]) << 8)
            | (UInt32(bytes[offset + 2]) << 16)
            | (UInt32(bytes[offset + 3]) << 24)
    }

    private static func appendUInt16LE(_ data: inout Data, _ value: UInt16) {
        let le = value.littleEndian
        withUnsafeBytes(of: le) { data.append(contentsOf: $0) }
    }

    private static func appendUInt32LE(_ data: inout Data, _ value: UInt32) {
        let le = value.littleEndian
        withUnsafeBytes(of: le) { data.append(contentsOf: $0) }
    }
}

// MARK: - Foundation XMLParser Delegates for OOXML SharedStrings, Workbook & Worksheet

internal enum XlsxXMLReader {
    internal static func parseSharedStrings(_ data: Data) throws -> [String] {
        let delegate = SharedStringsDelegate()
        let parser = XMLParser(data: data)
        parser.shouldProcessNamespaces = false
        parser.delegate = delegate
        guard parser.parse() else {
            throw ExternalProjectCodecError.invalidXlsx("Failed to parse xl/sharedStrings.xml.")
        }
        return delegate.strings
    }

    internal static func parseWorkbookInfo(_ data: Data) -> (firstSheetName: String?, firstSheetRelId: String?) {
        let delegate = WorkbookInfoDelegate()
        let parser = XMLParser(data: data)
        parser.shouldProcessNamespaces = false
        parser.delegate = delegate
        _ = parser.parse()
        return (delegate.firstSheetName, delegate.firstSheetRelId)
    }

    internal static func parseWorkbookRelationshipTarget(_ data: Data, relId: String) -> String? {
        let delegate = WorkbookRelsDelegate(targetRelId: relId)
        let parser = XMLParser(data: data)
        parser.shouldProcessNamespaces = false
        parser.delegate = delegate
        _ = parser.parse()
        return delegate.matchedTarget
    }

    internal static func parseWorksheetRows(_ data: Data, sharedStrings: [String]) throws -> [[String]] {
        let delegate = WorksheetDelegate(sharedStrings: sharedStrings)
        let parser = XMLParser(data: data)
        parser.shouldProcessNamespaces = false
        parser.delegate = delegate
        guard parser.parse() else {
            throw ExternalProjectCodecError.invalidXlsx("Failed to parse worksheet XML.")
        }
        return delegate.rows
    }

    fileprivate static func localTagName(_ raw: String) -> String {
        if let colon = raw.lastIndex(of: ":") {
            return String(raw[raw.index(after: colon)...]).lowercased()
        }
        return raw.lowercased()
    }
}

private final class SharedStringsDelegate: NSObject, XMLParserDelegate {
    var strings: [String] = []
    private var insideSi = false
    private var insideT = false
    private var phoneticDepth = 0
    private var currentSiAccumulator = ""
    private var currentTBuffer = ""

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        let tag = XlsxXMLReader.localTagName(elementName)
        if tag == "si" {
            insideSi = true
            currentSiAccumulator = ""
        } else if tag == "rph" {
            phoneticDepth += 1
        } else if tag == "t" && insideSi && phoneticDepth == 0 {
            insideT = true
            currentTBuffer = ""
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if insideT && phoneticDepth == 0 {
            currentTBuffer += string
        }
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        let tag = XlsxXMLReader.localTagName(elementName)
        if tag == "t" && insideT {
            currentSiAccumulator += currentTBuffer
            currentTBuffer = ""
            insideT = false
        } else if tag == "rph" && phoneticDepth > 0 {
            phoneticDepth -= 1
        } else if tag == "si" && insideSi {
            strings.append(currentSiAccumulator)
            currentSiAccumulator = ""
            insideSi = false
        }
    }
}

private final class WorkbookInfoDelegate: NSObject, XMLParserDelegate {
    var firstSheetName: String?
    var firstSheetRelId: String?

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        guard firstSheetName == nil, XlsxXMLReader.localTagName(elementName) == "sheet" else { return }
        firstSheetName = attributeDict["name"]
        firstSheetRelId = attributeDict["r:id"]
            ?? attributeDict.first(where: { XlsxXMLReader.localTagName($0.key) == "id" && $0.key.contains(":") })?.value
    }
}

private final class WorkbookRelsDelegate: NSObject, XMLParserDelegate {
    let targetRelId: String
    var matchedTarget: String?

    init(targetRelId: String) {
        self.targetRelId = targetRelId
    }

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        guard matchedTarget == nil, XlsxXMLReader.localTagName(elementName) == "relationship" else { return }
        if attributeDict["Id"] == targetRelId || attributeDict["id"] == targetRelId {
            matchedTarget = attributeDict["Target"] ?? attributeDict["target"]
        }
    }
}

private final class WorksheetDelegate: NSObject, XMLParserDelegate {
    let sharedStrings: [String]
    var rows: [[String]] = []

    private var insideRow = false
    private var currentRowCells: [Int: String] = [:]
    private var maxColInRow = -1
    private var nextSequentialCol = 0

    private var insideCell = false
    private var currentCellCol = 0
    private var currentCellType: String = ""
    private var insideV = false
    private var insideIs = false
    private var insideInlineT = false
    private var phoneticDepth = 0
    private var vBuffer = ""
    private var inlineAccumulator = ""

    init(sharedStrings: [String]) {
        self.sharedStrings = sharedStrings
    }

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        let tag = XlsxXMLReader.localTagName(elementName)
        switch tag {
        case "row":
            insideRow = true
            currentRowCells = [:]
            maxColInRow = -1
            nextSequentialCol = 0
        case "c":
            guard insideRow else { return }
            insideCell = true
            currentCellType = (attributeDict["t"] ?? "").lowercased()
            if let ref = attributeDict["r"], let parsedCol = ExternalProjectCodec.excelColumnIndex(fromCellRef: ref) {
                currentCellCol = parsedCol
                nextSequentialCol = parsedCol + 1
            } else {
                currentCellCol = nextSequentialCol
                nextSequentialCol += 1
            }
            vBuffer = ""
            inlineAccumulator = ""
        case "v":
            if insideCell {
                insideV = true
                vBuffer = ""
            }
        case "is":
            if insideCell {
                insideIs = true
                inlineAccumulator = ""
            }
        case "rph":
            if insideIs {
                phoneticDepth += 1
            }
        case "t":
            if insideIs && phoneticDepth == 0 {
                insideInlineT = true
            }
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if insideV {
            vBuffer += string
        } else if insideInlineT && phoneticDepth == 0 {
            inlineAccumulator += string
        }
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        let tag = XlsxXMLReader.localTagName(elementName)
        switch tag {
        case "v":
            insideV = false
        case "t":
            insideInlineT = false
        case "rph":
            if phoneticDepth > 0 { phoneticDepth -= 1 }
        case "is":
            insideIs = false
        case "c":
            guard insideCell else { return }
            let resolved: String
            switch currentCellType {
            case "s":
                if let idx = Int(vBuffer.trimmingCharacters(in: .whitespacesAndNewlines)),
                   idx >= 0 && idx < sharedStrings.count {
                    resolved = sharedStrings[idx]
                } else {
                    resolved = ""
                }
            case "inlinestr":
                resolved = inlineAccumulator
            case "b":
                let trimmed = vBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
                resolved = (trimmed == "1" || trimmed.lowercased() == "true") ? "TRUE" : "FALSE"
            default:
                // Covers t="str" (formula string), t="n" / omitted (numeric & Excel serial dates), t="e"
                resolved = vBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
            }

            currentRowCells[currentCellCol] = resolved
            if currentCellCol > maxColInRow {
                maxColInRow = currentCellCol
            }
            insideCell = false
        case "row":
            guard insideRow else { return }
            insideRow = false
            if maxColInRow >= 0 {
                var rowArray = [String](repeating: "", count: maxColInRow + 1)
                var hasNonEmpty = false
                for (col, val) in currentRowCells where col >= 0 && col <= maxColInRow {
                    rowArray[col] = val
                    if !val.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        hasNonEmpty = true
                    }
                }
                if hasNonEmpty {
                    rows.append(rowArray)
                }
            }
        default:
            break
        }
    }
}
