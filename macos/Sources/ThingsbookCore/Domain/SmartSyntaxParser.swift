import Foundation

public struct ParsedSmartSyntax: Equatable, Sendable {
    public let cleanTitle: String
    public let scheduleState: ScheduleState?
    public let whenDate: Int64?
    public let whenLabel: String?
    public let deadline: Int64?
    public let deadlineLabel: String?
    public let durationMinutes: Int?
    public let matchedTagIds: [String]
    public let matchedTags: [Tag]
    public let matchedProject: Project?
    public let matchedArea: Area?
    public let isMilestone: Bool
    public let hasAnyMatch: Bool

    public init(
        cleanTitle: String,
        scheduleState: ScheduleState? = nil,
        whenDate: Int64? = nil,
        whenLabel: String? = nil,
        deadline: Int64? = nil,
        deadlineLabel: String? = nil,
        durationMinutes: Int? = nil,
        matchedTagIds: [String] = [],
        matchedTags: [Tag] = [],
        matchedProject: Project? = nil,
        matchedArea: Area? = nil,
        isMilestone: Bool = false,
        hasAnyMatch: Bool = false
    ) {
        self.cleanTitle = cleanTitle
        self.scheduleState = scheduleState
        self.whenDate = whenDate
        self.whenLabel = whenLabel
        self.deadline = deadline
        self.deadlineLabel = deadlineLabel
        self.durationMinutes = durationMinutes
        self.matchedTagIds = matchedTagIds
        self.matchedTags = matchedTags
        self.matchedProject = matchedProject
        self.matchedArea = matchedArea
        self.isMilestone = isMilestone
        self.hasAnyMatch = hasAnyMatch
    }
}

/// Inline GTD Smart-Syntax Parser supporting `@when`, `!deadline` / `!milestone` / `◆`, `~duration`, `#tag`, and `/project` or `/area`
/// across EN, DE, FR, IT, ES without touching URLs (`https://a.com/b`), fractions (`3/4`), or emails (`a@b.com`).
public enum SmartSyntaxParser {
    private struct CandidateSpan {
        let range: Range<String.Index>
        let prefix: Character
        let value: String
        let isQuoted: Bool
    }

    public static func parse(
        _ rawTitle: String,
        tags: [Tag],
        projects: [Project],
        areas: [Area],
        locale: Locale = .current,
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) -> ParsedSmartSyntax {
        let trimmedRaw = rawTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedRaw.isEmpty else {
            return ParsedSmartSyntax(cleanTitle: "")
        }

        let activeTags = tags.filter { !$0.isDeleted }
        let activeProjects = projects.filter { !$0.isDeleted && $0.status == .active }
        let activeAreas = areas.filter { !$0.isDeleted }

        let candidates = scanCandidates(in: rawTitle)
        guard !candidates.isEmpty else {
            return ParsedSmartSyntax(cleanTitle: trimmedRaw)
        }

        var resolvedScheduleState: ScheduleState? = nil
        var resolvedWhenDate: Int64? = nil
        var resolvedWhenLabel: String? = nil
        var resolvedDeadline: Int64? = nil
        var resolvedDeadlineLabel: String? = nil
        var resolvedDurationMinutes: Int? = nil
        var resolvedTags: [Tag] = []
        var resolvedTagIds: [String] = []
        var seenTagIds = Set<String>()
        var resolvedProject: Project? = nil
        var resolvedArea: Area? = nil
        var resolvedIsMilestone = false
        var matchedRanges: [Range<String.Index>] = []

        for span in candidates {
            switch span.prefix {
            case "◆":
                resolvedIsMilestone = true
                matchedRanges.append(span.range)

            case "@":
                if let parsedDate = parseNaturalDateToken(
                    span.value,
                    isQuoted: span.isQuoted,
                    locale: locale,
                    referenceDate: referenceDate,
                    calendar: calendar
                ) {
                    resolvedScheduleState = parsedDate.scheduleState
                    resolvedWhenDate = parsedDate.whenDateEpochMillis
                    resolvedWhenLabel = parsedDate.formattedPreview
                    matchedRanges.append(span.range)
                }

            case "!":
                let cleanVal = span.value.trimmingCharacters(in: .whitespacesAndNewlines)
                if cleanVal.caseInsensitiveCompare("milestone") == .orderedSame {
                    resolvedIsMilestone = true
                    matchedRanges.append(span.range)
                } else if let parsedDl = parseNaturalDateToken(
                    cleanVal,
                    isQuoted: span.isQuoted,
                    locale: locale,
                    referenceDate: referenceDate,
                    calendar: calendar
                ), let dlMillis = parsedDl.whenDateEpochMillis {
                    resolvedDeadline = dlMillis
                    resolvedDeadlineLabel = cleanDeadlinePreview(parsedDl.formattedPreview)
                    matchedRanges.append(span.range)
                }

            case "~":
                if let mins = parseCompactDurationToken(span.value) {
                    resolvedDurationMinutes = mins
                    matchedRanges.append(span.range)
                }

            case "#":
                if let tag = matchTag(span.value, isQuoted: span.isQuoted, in: activeTags) {
                    if seenTagIds.insert(tag.id).inserted {
                        resolvedTags.append(tag)
                        resolvedTagIds.append(tag.id)
                    }
                    matchedRanges.append(span.range)
                }

            case "/":
                if let proj = matchProject(span.value, isQuoted: span.isQuoted, in: activeProjects) {
                    resolvedProject = proj
                    resolvedArea = nil
                    matchedRanges.append(span.range)
                } else if let area = matchArea(span.value, isQuoted: span.isQuoted, in: activeAreas) {
                    resolvedArea = area
                    resolvedProject = nil
                    matchedRanges.append(span.range)
                }

            default:
                break
            }
        }

        let hasAny = !matchedRanges.isEmpty
        guard hasAny else {
            return ParsedSmartSyntax(cleanTitle: trimmedRaw)
        }

        let strippedTitle = buildCleanTitle(from: rawTitle, removing: matchedRanges)
        let finalTitle = strippedTitle.isEmpty ? trimmedRaw : strippedTitle

        return ParsedSmartSyntax(
            cleanTitle: finalTitle,
            scheduleState: resolvedScheduleState,
            whenDate: resolvedWhenDate,
            whenLabel: resolvedWhenLabel,
            deadline: resolvedDeadline,
            deadlineLabel: resolvedDeadlineLabel,
            durationMinutes: resolvedDurationMinutes,
            matchedTagIds: resolvedTagIds,
            matchedTags: resolvedTags,
            matchedProject: resolvedProject,
            matchedArea: resolvedArea,
            isMilestone: resolvedIsMilestone,
            hasAnyMatch: true
        )
    }

    // MARK: - Token Scanning

    private static func scanCandidates(in text: String) -> [CandidateSpan] {
        var spans: [CandidateSpan] = []
        var idx = text.startIndex

        while idx < text.endIndex {
            let isAtBoundary: Bool = {
                if idx == text.startIndex { return true }
                let prev = text[text.index(before: idx)]
                return prev.isWhitespace
            }()

            guard isAtBoundary else {
                idx = text.index(after: idx)
                continue
            }

            let ch = text[idx]
            if ch == "◆" {
                let nextIdx = text.index(after: idx)
                if nextIdx == text.endIndex || text[nextIdx].isWhitespace {
                    spans.append(CandidateSpan(range: idx..<nextIdx, prefix: "◆", value: "◆", isQuoted: false))
                    idx = nextIdx
                    continue
                }
            } else if ch == "@" || ch == "!" || ch == "~" || ch == "#" || ch == "/" {
                let afterPrefix = text.index(after: idx)
                if afterPrefix < text.endIndex {
                    let firstChar = text[afterPrefix]
                    if firstChar == "\"" || firstChar == "\u{201C}" {
                        let contentStart = text.index(after: afterPrefix)
                        var cursor = contentStart
                        var closingQuoteIdx: String.Index? = nil
                        while cursor < text.endIndex {
                            let c = text[cursor]
                            if c == "\"" || c == "\u{201D}" {
                                closingQuoteIdx = cursor
                                break
                            }
                            cursor = text.index(after: cursor)
                        }
                        if let closeIdx = closingQuoteIdx {
                            let afterQuote = text.index(after: closeIdx)
                            if afterQuote == text.endIndex || text[afterQuote].isWhitespace {
                                let inner = String(text[contentStart..<closeIdx]).trimmingCharacters(in: .whitespacesAndNewlines)
                                if !inner.isEmpty {
                                    spans.append(CandidateSpan(range: idx..<afterQuote, prefix: ch, value: inner, isQuoted: true))
                                }
                                idx = afterQuote
                                continue
                            }
                        }
                    } else if !firstChar.isWhitespace {
                        var cursor = afterPrefix
                        while cursor < text.endIndex && !text[cursor].isWhitespace {
                            cursor = text.index(after: cursor)
                        }
                        let rawToken = String(text[afterPrefix..<cursor])
                        if !rawToken.isEmpty {
                            spans.append(CandidateSpan(range: idx..<cursor, prefix: ch, value: rawToken, isQuoted: false))
                        }
                        idx = cursor
                        continue
                    }
                }
            }

            idx = text.index(after: idx)
        }

        return spans
    }

    // MARK: - Token Resolvers

    private static func parseNaturalDateToken(
        _ rawToken: String,
        isQuoted: Bool,
        locale: Locale,
        referenceDate: Date,
        calendar: Calendar
    ) -> ParsedNaturalDate? {
        let trimmed = rawToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        if let direct = MultilingualDateParser.parse(
            trimmed,
            locale: locale,
            referenceDate: referenceDate,
            calendar: calendar
        ) {
            return direct
        }

        if !isQuoted && trimmed.contains("-") {
            let hyphenNormalized = trimmed
                .replacingOccurrences(of: "-", with: " ")
                .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !hyphenNormalized.isEmpty {
                return MultilingualDateParser.parse(
                    hyphenNormalized,
                    locale: locale,
                    referenceDate: referenceDate,
                    calendar: calendar
                )
            }
        }
        return nil
    }

    private static func cleanDeadlinePreview(_ preview: String) -> String {
        preview
            .replacingOccurrences(of: " · ★", with: "")
            .replacingOccurrences(of: " · 🌙", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func parseCompactDurationToken(_ rawToken: String) -> Int? {
        let trimmed = rawToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        // Require explicit clock colon (`1:30`) or alphabetic unit (`15m`, `45min`, `1.5h`, `2h`, `1d`)
        // so approximate prose numbers like `~50` are never misparsed as durations.
        let hasUnitOrColon = trimmed.contains(":") || trimmed.unicodeScalars.contains(where: { CharacterSet.letters.contains($0) })
        guard hasUnitOrColon else { return nil }
        guard let parsed = TaskItem.parseCustomDurationMinutes(trimmed), parsed > 0 else { return nil }
        return min(48_000, max(1, parsed))
    }

    private static func normalizeLookup(_ text: String) -> String {
        text
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "en_US_POSIX"))
    }

    private static func matchTag(_ rawToken: String, isQuoted: Bool, in tags: [Tag]) -> Tag? {
        let qExact = normalizeLookup(rawToken)
        guard !qExact.isEmpty else { return nil }
        let qSpaced = isQuoted ? qExact : normalizeLookup(rawToken.replacingOccurrences(of: "-", with: " "))

        if let exact = tags.first(where: { normalizeLookup($0.title) == qExact }) {
            return exact
        }
        if qSpaced != qExact, let spaced = tags.first(where: { normalizeLookup($0.title) == qSpaced }) {
            return spaced
        }
        return nil
    }

    private static func matchProject(_ rawToken: String, isQuoted: Bool, in projects: [Project]) -> Project? {
        let qExact = normalizeLookup(rawToken)
        guard !qExact.isEmpty else { return nil }
        let qSpaced = isQuoted ? qExact : normalizeLookup(rawToken.replacingOccurrences(of: "-", with: " "))

        if let exact = projects.first(where: { normalizeLookup($0.title) == qExact }) {
            return exact
        }
        if qSpaced != qExact, let spaced = projects.first(where: { normalizeLookup($0.title) == qSpaced }) {
            return spaced
        }
        if qExact.count >= 3 {
            let prefixMatches = projects.filter { proj in
                let normTitle = normalizeLookup(proj.title)
                return normTitle.hasPrefix(qExact) || (qSpaced != qExact && normTitle.hasPrefix(qSpaced))
            }
            if prefixMatches.count == 1 {
                return prefixMatches[0]
            }
        }
        return nil
    }

    private static func matchArea(_ rawToken: String, isQuoted: Bool, in areas: [Area]) -> Area? {
        let qExact = normalizeLookup(rawToken)
        guard !qExact.isEmpty else { return nil }
        let qSpaced = isQuoted ? qExact : normalizeLookup(rawToken.replacingOccurrences(of: "-", with: " "))

        if let exact = areas.first(where: { normalizeLookup($0.title) == qExact }) {
            return exact
        }
        if qSpaced != qExact, let spaced = areas.first(where: { normalizeLookup($0.title) == qSpaced }) {
            return spaced
        }
        if qExact.count >= 3 {
            let prefixMatches = areas.filter { area in
                let normTitle = normalizeLookup(area.title)
                return normTitle.hasPrefix(qExact) || (qSpaced != qExact && normTitle.hasPrefix(qSpaced))
            }
            if prefixMatches.count == 1 {
                return prefixMatches[0]
            }
        }
        return nil
    }

    private static func buildCleanTitle(from rawTitle: String, removing ranges: [Range<String.Index>]) -> String {
        guard !ranges.isEmpty else {
            return rawTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let sortedRanges = ranges.sorted { $0.lowerBound < $1.lowerBound }
        var output = ""
        var cursor = rawTitle.startIndex
        for r in sortedRanges {
            if cursor < r.lowerBound {
                output.append(contentsOf: rawTitle[cursor..<r.lowerBound])
            }
            if r.upperBound > cursor {
                cursor = r.upperBound
            }
        }
        if cursor < rawTitle.endIndex {
            output.append(contentsOf: rawTitle[cursor..<rawTitle.endIndex])
        }
        return output
            .replacingOccurrences(of: #"[ \t]{2,}"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
