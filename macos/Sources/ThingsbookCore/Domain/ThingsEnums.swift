import Foundation

public enum ScheduleState: String, Codable, CaseIterable, Sendable {
    case inbox = "INBOX"
    case today = "TODAY"
    case thisEvening = "THIS_EVENING"
    case upcoming = "UPCOMING"
    case anytime = "ANYTIME"
    case someday = "SOMEDAY"

    public static func normalized(_ raw: String?, fallback: ScheduleState = .inbox) -> ScheduleState {
        guard let up = raw?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
              let match = ScheduleState(rawValue: up) else {
            return fallback
        }
        return match
    }
}

public enum TaskStatus: String, Codable, CaseIterable, Sendable {
    case open = "OPEN"
    case completed = "COMPLETED"
    case canceled = "CANCELED"

    public static func normalized(_ raw: String?) -> TaskStatus {
        guard let up = raw?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
              let match = TaskStatus(rawValue: up) else {
            return .open
        }
        return match
    }
}

public enum ProjectStatus: String, Codable, CaseIterable, Sendable {
    case active = "ACTIVE"
    case completed = "COMPLETED"
    case canceled = "CANCELED"

    public static func normalized(_ raw: String?) -> ProjectStatus {
        guard let up = raw?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
              let match = ProjectStatus(rawValue: up) else {
            return .active
        }
        return match
    }
}

public enum RecurrenceMode: String, Codable, CaseIterable, Sendable {
    case fixedSchedule = "FIXED_SCHEDULE"
    case afterCompletion = "AFTER_COMPLETION"
}

public enum RecurrenceUnit: String, Codable, CaseIterable, Sendable {
    case days = "DAYS"
    case weeks = "WEEKS"
    case months = "MONTHS"
    case years = "YEARS"
}

public struct RecurrenceRule: Codable, Equatable, Hashable, Sendable {
    public var mode: RecurrenceMode
    public var interval: Int
    public var unit: RecurrenceUnit
    /// ISO-8601 weekday numbers: 1 (Monday) ... 7 (Sunday)
    public var daysOfWeek: [Int]
    /// 1...31 or -1 for last day of month
    public var dayOfMonth: Int?
    public var endDateEpochMillis: Int64?

    public init(
        mode: RecurrenceMode = .fixedSchedule,
        interval: Int = 1,
        unit: RecurrenceUnit = .days,
        daysOfWeek: [Int] = [],
        dayOfMonth: Int? = nil,
        endDateEpochMillis: Int64? = nil
    ) {
        self.mode = mode
        self.interval = max(1, interval)
        self.unit = unit
        self.daysOfWeek = daysOfWeek
        self.dayOfMonth = dayOfMonth
        self.endDateEpochMillis = endDateEpochMillis
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let rawMode = try c.decodeIfPresent(String.self, forKey: .mode)
        self.mode = rawMode == "AFTER_COMPLETION" ? .afterCompletion : .fixedSchedule
        self.interval = max(1, (try? c.decodeIfPresent(Int.self, forKey: .interval)) ?? 1)
        let rawUnit = (try? c.decodeIfPresent(String.self, forKey: .unit)) ?? "DAYS"
        self.unit = RecurrenceUnit(rawValue: rawUnit.uppercased()) ?? .days
        let rawDays = (try? c.decodeIfPresent([Int].self, forKey: .daysOfWeek)) ?? []
        self.daysOfWeek = rawDays.filter { (1...7).contains($0) }
        self.dayOfMonth = try? c.decodeIfPresent(Int.self, forKey: .dayOfMonth)
        self.endDateEpochMillis = try? c.decodeIfPresent(Int64.self, forKey: .endDateEpochMillis)
    }

    public func formatShortLabel(locale: Locale = .current) -> String {
        let safeInterval = min(999, max(1, interval))
        let lang = String((locale.language.languageCode?.identifier ?? "en").prefix(2)).lowercased()

        let unitLabel: String
        switch unit {
        case .days:
            switch lang {
            case "de": unitLabel = safeInterval == 1 ? "Täglich" : "Alle \(safeInterval) Tg."
            case "it": unitLabel = safeInterval == 1 ? "Ogni giorno" : "Ogni \(safeInterval) gg."
            case "fr": unitLabel = safeInterval == 1 ? "Quotidien" : "Tous les \(safeInterval) j."
            case "es": unitLabel = safeInterval == 1 ? "Diario" : "Cada \(safeInterval) d."
            default:   unitLabel = safeInterval == 1 ? "Daily" : "Every \(safeInterval) d."
            }
        case .weeks:
            let validDays = Array(Set(daysOfWeek.filter { (1...7).contains($0) })).sorted()
            var daysStr = ""
            if !validDays.isEmpty {
                let df = DateFormatter()
                df.locale = locale
                let symbols = df.shortWeekdaySymbols ?? ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
                let names = validDays.map { isoDow -> String in
                    let appleIdx = isoDow == 7 ? 0 : isoDow
                    let raw = symbols[appleIdx % symbols.count]
                    return raw.hasSuffix(".") ? String(raw.dropLast()) : raw
                }
                daysStr = " (" + names.joined(separator: ",") + ")"
            }
            switch lang {
            case "de": unitLabel = safeInterval == 1 ? "Wöchentlich\(daysStr)" : "Alle \(safeInterval) Wo.\(daysStr)"
            case "it": unitLabel = safeInterval == 1 ? "Ogni sett.\(daysStr)" : "Ogni \(safeInterval) sett.\(daysStr)"
            case "fr": unitLabel = safeInterval == 1 ? "Hebdo.\(daysStr)" : "Toutes les \(safeInterval) sem.\(daysStr)"
            case "es": unitLabel = safeInterval == 1 ? "Semanal\(daysStr)" : "Cada \(safeInterval) sem.\(daysStr)"
            default:   unitLabel = safeInterval == 1 ? "Weekly\(daysStr)" : "Every \(safeInterval) wk.\(daysStr)"
            }
        case .months:
            let lastDayLabel: String
            switch lang {
            case "de": lastDayLabel = "Letzter Tag"
            case "it": lastDayLabel = "Ultimo giorno"
            case "fr": lastDayLabel = "Dernier jour"
            case "es": lastDayLabel = "Último día"
            default:   lastDayLabel = "Last day"
            }
            let domStr: String
            if let dom = dayOfMonth {
                if dom == -1 {
                    domStr = " (\(lastDayLabel))"
                } else if (1...31).contains(dom) {
                    domStr = " (\(dom).)"
                } else {
                    domStr = ""
                }
            } else {
                domStr = ""
            }
            switch lang {
            case "de": unitLabel = safeInterval == 1 ? "Monatlich\(domStr)" : "Alle \(safeInterval) Mon.\(domStr)"
            case "it": unitLabel = safeInterval == 1 ? "Ogni mese\(domStr)" : "Ogni \(safeInterval) mesi\(domStr)"
            case "fr": unitLabel = safeInterval == 1 ? "Mensuel\(domStr)" : "Tous les \(safeInterval) mois\(domStr)"
            case "es": unitLabel = safeInterval == 1 ? "Mensual\(domStr)" : "Cada \(safeInterval) mes.\(domStr)"
            default:   unitLabel = safeInterval == 1 ? "Monthly\(domStr)" : "Every \(safeInterval) mo.\(domStr)"
            }
        case .years:
            switch lang {
            case "de": unitLabel = safeInterval == 1 ? "Jährlich" : "Alle \(safeInterval) J."
            case "it": unitLabel = safeInterval == 1 ? "Ogni anno" : "Ogni \(safeInterval) anni"
            case "fr": unitLabel = safeInterval == 1 ? "Annuel" : "Tous les \(safeInterval) ans"
            case "es": unitLabel = safeInterval == 1 ? "Anual" : "Cada \(safeInterval) a."
            default:   unitLabel = safeInterval == 1 ? "Yearly" : "Every \(safeInterval) yr."
            }
        }

        if mode == .afterCompletion {
            let suffix: String
            switch lang {
            case "de": suffix = "n. Erledigung"
            case "it": suffix = "dopo compl."
            case "fr": suffix = "après achèv."
            case "es": suffix = "tras compl."
            default:   suffix = "after compl."
            }
            return "\(unitLabel) \(suffix)"
        }
        return unitLabel
    }
}

public enum SmartView: String, CaseIterable, Hashable, Codable, Sendable, Identifiable {
    case inbox
    case today
    case upcoming
    case anytime
    case someday
    case logbook
    case trash

    public var id: String { rawValue }

    public var titleKey: String {
        switch self {
        case .inbox: return "view_inbox"
        case .today: return "view_today"
        case .upcoming: return "view_upcoming"
        case .anytime: return "view_anytime"
        case .someday: return "view_someday"
        case .logbook: return "view_logbook"
        case .trash: return "view_trash"
        }
    }

    public var sfSymbol: String {
        switch self {
        case .inbox: return "tray.fill"
        case .today: return "star.fill"
        case .upcoming: return "calendar"
        case .anytime: return "square.stack.3d.up.fill"
        case .someday: return "archivebox.fill"
        case .logbook: return "checkmark.square.fill"
        case .trash: return "trash.fill"
        }
    }
}

public enum NavigationDestination: Hashable, Codable, Sendable {
    case smartView(SmartView)
    case area(String)
    case project(String)
}

public enum QuickShareDestination: Hashable, Sendable {
    case inbox
    case today
    case thisEvening
    case anytime
    case someday
    case area(String)
    case project(String)
}

public enum CloudSyncStatus: String, Sendable {
    case offline = "OFFLINE"
    case syncing = "SYNCING"
    case synced = "SYNCED"
}
