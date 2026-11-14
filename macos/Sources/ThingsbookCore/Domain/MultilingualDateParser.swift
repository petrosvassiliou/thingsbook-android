import Foundation

public struct ParsedNaturalDate: Equatable, Sendable {
    public let scheduleState: ScheduleState
    public let whenDateEpochMillis: Int64?
    public let formattedPreview: String
    public let matchedExpression: String

    public init(
        scheduleState: ScheduleState,
        whenDateEpochMillis: Int64?,
        formattedPreview: String,
        matchedExpression: String
    ) {
        self.scheduleState = scheduleState
        self.whenDateEpochMillis = whenDateEpochMillis
        self.formattedPreview = formattedPreview
        self.matchedExpression = matchedExpression
    }
}

/// Live 5-Language Natural Language Date Parser (`EN`, `DE`, `IT`, `FR`, `ES`) matching Android `MultilingualDateParser.kt`.
public enum MultilingualDateParser {
    /// Maps normalized weekday names across EN, DE, IT, FR, ES to ISO-8601 weekday (1 = Monday ... 7 = Sunday).
    private static let weekdayAliases: [String: Int] = [
        // English
        "monday": 1, "mon": 1,
        "tuesday": 2, "tue": 2,
        "wednesday": 3, "wed": 3,
        "thursday": 4, "thu": 4,
        "friday": 5, "fri": 5,
        "saturday": 6, "sat": 6,
        "sunday": 7, "sun": 7,
        // German
        "montag": 1,
        "dienstag": 2,
        "mittwoch": 3,
        "donnerstag": 4,
        "freitag": 5,
        "samstag": 6, "sonnabend": 6,
        "sonntag": 7,
        // Italian (accent-stripped)
        "lunedi": 1,
        "martedi": 2,
        "mercoledi": 3,
        "giovedi": 4,
        "venerdi": 5,
        "sabato": 6,
        "domenica": 7,
        // French
        "lundi": 1,
        "mardi": 2,
        "mercredi": 3,
        "jeudi": 4,
        "vendredi": 5,
        "samedi": 6,
        "dimanche": 7,
        // Spanish (accent-stripped)
        "lunes": 1,
        "martes": 2,
        "miercoles": 3,
        "jueves": 4,
        "viernes": 5,
        "sabado": 6,
        "domingo": 7
    ]

    private static let relativeDaysRegex = try! NSRegularExpression(
        pattern: #"^(?:in|tra|fra|dans|en)\s+(\d{1,3})\s*(?:days?|tag(?:en|e)?|giorn[oi]|jours?|dias?|d)$"#
    )
    private static let relativeWeeksRegex = try! NSRegularExpression(
        pattern: #"^(?:in|tra|fra|dans|en)\s+(\d{1,2})\s*(?:weeks?|wochen?|settiman[ae]|semaines?|semanas?|w)$"#
    )
    private static let isoDateRegex = try! NSRegularExpression(
        pattern: #"^(\d{4})-(\d{1,2})-(\d{1,2})$"#
    )
    private static let euroDateRegex = try! NSRegularExpression(
        pattern: #"^(\d{1,2})\.(\d{1,2})\.(?:(\d{4}|\d{2}))?$"#
    )
    private static let leadingNextRegex = try! NSRegularExpression(
        pattern: #"^(?:next|nachsten|nachster|nachste|naechsten|naechste|diesen|kommenden|am|prossimo|prossima|proximo|proxima|prochain|prochaine)\s+"#
    )
    private static let trailingNextRegex = try! NSRegularExpression(
        pattern: #"\s+(?:prossimo|prossima|prochain|prochaine|proximo|proxima)$"#
    )

    public static func parse(
        _ rawInput: String,
        locale: Locale = .current,
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) -> ParsedNaturalDate? {
        let trimmed = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        let normalized = normalizeDiacritics(trimmed.lowercased(with: locale))
        let refDay = calendar.startOfDay(for: referenceDate)

        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.calendar = calendar
        formatter.dateFormat = "EEEE, d MMM yyyy"

        func epochFor(_ date: Date) -> Int64 {
            DateEpochConverter.startOfDayMillis(for: date, calendar: calendar)
        }

        // 1. This Evening
        let eveningTokens: Set<String> = [
            "this evening", "tonight", "evening",
            "heute abend", "abend",
            "stasera", "questa sera",
            "ce soir", "soir",
            "esta noche", "noche"
        ]
        if eveningTokens.contains(normalized) {
            return ParsedNaturalDate(
                scheduleState: .thisEvening,
                whenDateEpochMillis: epochFor(refDay),
                formattedPreview: "\(formatter.string(from: refDay)) · 🌙",
                matchedExpression: trimmed
            )
        }

        // 2. Today
        let todayTokens: Set<String> = [
            "today", "tod", "heute", "oggi", "aujourd'hui", "aujourdhui", "hoy"
        ]
        if todayTokens.contains(normalized) {
            return ParsedNaturalDate(
                scheduleState: .today,
                whenDateEpochMillis: epochFor(refDay),
                formattedPreview: "\(formatter.string(from: refDay)) · ★",
                matchedExpression: trimmed
            )
        }

        // 3. Tomorrow
        let tomorrowTokens: Set<String> = [
            "tomorrow", "tmr", "tom", "morgen", "domani", "demain", "manana"
        ]
        if tomorrowTokens.contains(normalized),
           let target = calendar.date(byAdding: .day, value: 1, to: refDay) {
            return ParsedNaturalDate(
                scheduleState: .upcoming,
                whenDateEpochMillis: epochFor(target),
                formattedPreview: formatter.string(from: target),
                matchedExpression: trimmed
            )
        }

        // 4. Overmorrow (+2d)
        let overmorrowTokens: Set<String> = [
            "overmorrow", "ubermorgen", "uebermorgen", "dopodomani",
            "apres-demain", "apres demain", "pasado manana"
        ]
        if overmorrowTokens.contains(normalized),
           let target = calendar.date(byAdding: .day, value: 2, to: refDay) {
            return ParsedNaturalDate(
                scheduleState: .upcoming,
                whenDateEpochMillis: epochFor(target),
                formattedPreview: formatter.string(from: target),
                matchedExpression: trimmed
            )
        }

        // 5. Someday
        let somedayTokens: Set<String> = [
            "someday", "later", "irgendwann", "spater", "prima o poi", "un jour", "algun dia"
        ]
        if somedayTokens.contains(normalized) {
            return ParsedNaturalDate(
                scheduleState: .someday,
                whenDateEpochMillis: nil,
                formattedPreview: "📦",
                matchedExpression: trimmed
            )
        }

        // 6. Anytime
        let anytimeTokens: Set<String> = [
            "anytime", "jederzeit", "in qualsiasi momento", "a tout moment", "en cualquier momento"
        ]
        if anytimeTokens.contains(normalized) {
            return ParsedNaturalDate(
                scheduleState: .anytime,
                whenDateEpochMillis: nil,
                formattedPreview: "⚡",
                matchedExpression: trimmed
            )
        }

        // 7. Relative Days
        if let groups = matchGroups(relativeDaysRegex, in: normalized),
           groups.count >= 2,
           let days = Int(groups[1]),
           let target = calendar.date(byAdding: .day, value: days, to: refDay) {
            let state: ScheduleState = days == 0 ? .today : .upcoming
            return ParsedNaturalDate(
                scheduleState: state,
                whenDateEpochMillis: epochFor(target),
                formattedPreview: formatter.string(from: target),
                matchedExpression: trimmed
            )
        }

        // 8. Relative Weeks
        if let groups = matchGroups(relativeWeeksRegex, in: normalized),
           groups.count >= 2,
           let weeks = Int(groups[1]),
           let target = calendar.date(byAdding: .day, value: weeks * 7, to: refDay) {
            let state: ScheduleState = weeks == 0 ? .today : .upcoming
            return ParsedNaturalDate(
                scheduleState: state,
                whenDateEpochMillis: epochFor(target),
                formattedPreview: formatter.string(from: target),
                matchedExpression: trimmed
            )
        }

        // 9. Next Weekday or Standalone Weekday across EN, DE, IT, FR, ES
        var cleanedWeekday = replaceRegex(leadingNextRegex, in: normalized, with: "")
        cleanedWeekday = replaceRegex(trailingNextRegex, in: cleanedWeekday, with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        if let targetIsoDow = weekdayAliases[cleanedWeekday] {
            let currentIsoDow = DateEpochConverter.isoWeekday(for: refDay, calendar: calendar)
            let delta = targetIsoDow > currentIsoDow
                ? (targetIsoDow - currentIsoDow)
                : (7 - currentIsoDow + targetIsoDow)
            if let target = calendar.date(byAdding: .day, value: delta, to: refDay) {
                return ParsedNaturalDate(
                    scheduleState: .upcoming,
                    whenDateEpochMillis: epochFor(target),
                    formattedPreview: formatter.string(from: target),
                    matchedExpression: trimmed
                )
            }
        }

        // 10. Explicit ISO YYYY-MM-DD
        if let groups = matchGroups(isoDateRegex, in: normalized),
           groups.count >= 4,
           let y = Int(groups[1]), (1970...2100).contains(y),
           let m = Int(groups[2]), (1...12).contains(m),
           let d = Int(groups[3]), (1...31).contains(d),
           let date = makeValidLocalDate(year: y, month: m, day: d, calendar: calendar) {
            let state: ScheduleState = date <= refDay ? .today : .upcoming
            return ParsedNaturalDate(
                scheduleState: state,
                whenDateEpochMillis: epochFor(date),
                formattedPreview: formatter.string(from: date),
                matchedExpression: trimmed
            )
        }

        // 11. Explicit European DD.MM.YYYY / DD.MM.
        if let groups = matchGroups(euroDateRegex, in: normalized),
           groups.count >= 3,
           let d = Int(groups[1]), (1...31).contains(d),
           let m = Int(groups[2]), (1...12).contains(m) {
            let rawYear = groups.count >= 4 ? groups[3] : ""
            let currentYear = calendar.component(.year, from: refDay)
            let resolvedYear: Int?
            if rawYear.isEmpty {
                resolvedYear = currentYear
            } else if rawYear.count == 2, let shortY = Int(rawYear) {
                resolvedYear = 2000 + shortY
            } else if let fullY = Int(rawYear), (1970...2100).contains(fullY) {
                resolvedYear = fullY
            } else {
                resolvedYear = nil
            }

            if let y = resolvedYear, var date = makeValidLocalDate(year: y, month: m, day: d, calendar: calendar) {
                if rawYear.isEmpty && date < refDay,
                   let bumped = calendar.date(byAdding: .year, value: 1, to: date) {
                    date = bumped
                }
                let state: ScheduleState = date <= refDay ? .today : .upcoming
                return ParsedNaturalDate(
                    scheduleState: state,
                    whenDateEpochMillis: epochFor(date),
                    formattedPreview: formatter.string(from: date),
                    matchedExpression: trimmed
                )
            }
        }

        return nil
    }

    private static func makeValidLocalDate(year: Int, month: Int, day: Int, calendar: Calendar) -> Date? {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = day
        guard let candidate = calendar.date(from: comps) else { return nil }
        let check = calendar.dateComponents([.year, .month, .day], from: candidate)
        guard check.year == year && check.month == month && check.day == day else { return nil }
        return calendar.startOfDay(for: candidate)
    }

    private static func normalizeDiacritics(_ input: String) -> String {
        let unifiedQuotes = input
            .replacingOccurrences(of: "\u{2018}", with: "'")
            .replacingOccurrences(of: "\u{2019}", with: "'")
            .replacingOccurrences(of: "\u{02BC}", with: "'")
            .replacingOccurrences(of: "`", with: "'")
        return unifiedQuotes.folding(options: .diacriticInsensitive, locale: Locale(identifier: "en_US_POSIX"))
    }

    private static func matchGroups(_ regex: NSRegularExpression, in text: String) -> [String]? {
        let nsRange = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, options: [], range: nsRange) else { return nil }
        var out: [String] = []
        for idx in 0..<match.numberOfRanges {
            let r = match.range(at: idx)
            if r.location == NSNotFound {
                out.append("")
            } else if let swiftRange = Range(r, in: text) {
                out.append(String(text[swiftRange]))
            } else {
                out.append("")
            }
        }
        return out
    }

    private static func replaceRegex(_ regex: NSRegularExpression, in text: String, with template: String) -> String {
        let nsRange = NSRange(text.startIndex..<text.endIndex, in: text)
        return regex.stringByReplacingMatches(in: text, options: [], range: nsRange, withTemplate: template)
    }
}
