import Foundation

public struct ResolvedWhenSelection: Equatable, Sendable {
    public let scheduleState: ScheduleState
    public let whenDateEpochMillis: Int64?
    public let reminderEpochMillis: Int64?

    public init(scheduleState: ScheduleState, whenDateEpochMillis: Int64?, reminderEpochMillis: Int64?) {
        self.scheduleState = scheduleState
        self.whenDateEpochMillis = whenDateEpochMillis
        self.reminderEpochMillis = reminderEpochMillis
    }
}

public enum DateEpochConverter {
    public static func startOfDayMillis(for date: Date, calendar: Calendar = .current) -> Int64 {
        let sod = calendar.startOfDay(for: date)
        return Int64(sod.timeIntervalSince1970 * 1000.0)
    }

    public static func endOfDayMillis(for date: Date, calendar: Calendar = .current) -> Int64 {
        let sod = calendar.startOfDay(for: date)
        let nextDay = calendar.date(byAdding: .day, value: 1, to: sod) ?? sod.addingTimeInterval(86_400)
        return Int64(nextDay.timeIntervalSince1970 * 1000.0) - 1
    }

    public static func epochDay(forMillis millis: Int64, calendar: Calendar = .current) -> Int {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        return epochDay(for: date, calendar: calendar)
    }

    public static func epochDay(for date: Date, calendar: Calendar = .current) -> Int {
        var utcCal = Calendar(identifier: .gregorian)
        utcCal.timeZone = TimeZone(secondsFromGMT: 0)!
        let comps = calendar.dateComponents([.year, .month, .day], from: date)
        let utcDate = utcCal.date(from: DateComponents(year: comps.year, month: comps.month, day: comps.day)) ?? date
        return Int(floor(utcDate.timeIntervalSince1970 / 86_400.0))
    }

    public static func date(fromMillis millis: Int64) -> Date {
        Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
    }

    /// ISO-8601 weekday: 1 = Monday ... 7 = Sunday
    public static func isoWeekday(for date: Date, calendar: Calendar = .current) -> Int {
        let appleWeekday = calendar.component(.weekday, from: date) // 1 = Sun ... 7 = Sat
        return appleWeekday == 1 ? 7 : (appleWeekday - 1)
    }
}

public enum WhenScheduleResolver {
    public static func computeReminderEpoch(
        targetDate: Date?,
        calendar: Calendar = .current,
        reminderEnabled: Bool,
        reminderHour: Int,
        reminderMinute: Int
    ) -> Int64? {
        guard reminderEnabled, let targetDate else { return nil }
        var comps = calendar.dateComponents([.year, .month, .day], from: targetDate)
        comps.hour = min(23, max(0, reminderHour))
        comps.minute = min(59, max(0, reminderMinute))
        comps.second = 0
        guard let combined = calendar.date(from: comps) else { return nil }
        return Int64(combined.timeIntervalSince1970 * 1000.0)
    }

    public static func resolvePresetSelection(
        preset: ScheduleState,
        todayDate: Date = Date(),
        calendar: Calendar = .current,
        reminderEnabled: Bool,
        reminderHour: Int,
        reminderMinute: Int
    ) -> ResolvedWhenSelection {
        let sodToday = calendar.startOfDay(for: todayDate)
        let todayEpoch = DateEpochConverter.startOfDayMillis(for: sodToday, calendar: calendar)
        let tomorrowDate = calendar.date(byAdding: .day, value: 1, to: sodToday) ?? sodToday.addingTimeInterval(86_400)
        let tomorrowEpoch = DateEpochConverter.startOfDayMillis(for: tomorrowDate, calendar: calendar)

        switch preset {
        case .today:
            return ResolvedWhenSelection(
                scheduleState: .today,
                whenDateEpochMillis: todayEpoch,
                reminderEpochMillis: computeReminderEpoch(
                    targetDate: sodToday,
                    calendar: calendar,
                    reminderEnabled: reminderEnabled,
                    reminderHour: reminderHour,
                    reminderMinute: reminderMinute
                )
            )
        case .thisEvening:
            return ResolvedWhenSelection(
                scheduleState: .thisEvening,
                whenDateEpochMillis: todayEpoch,
                reminderEpochMillis: computeReminderEpoch(
                    targetDate: sodToday,
                    calendar: calendar,
                    reminderEnabled: reminderEnabled,
                    reminderHour: reminderHour,
                    reminderMinute: reminderMinute
                )
            )
        case .upcoming:
            return ResolvedWhenSelection(
                scheduleState: .upcoming,
                whenDateEpochMillis: tomorrowEpoch,
                reminderEpochMillis: computeReminderEpoch(
                    targetDate: tomorrowDate,
                    calendar: calendar,
                    reminderEnabled: reminderEnabled,
                    reminderHour: reminderHour,
                    reminderMinute: reminderMinute
                )
            )
        case .anytime:
            return ResolvedWhenSelection(scheduleState: .anytime, whenDateEpochMillis: nil, reminderEpochMillis: nil)
        case .someday:
            return ResolvedWhenSelection(scheduleState: .someday, whenDateEpochMillis: nil, reminderEpochMillis: nil)
        case .inbox:
            return ResolvedWhenSelection(scheduleState: .inbox, whenDateEpochMillis: nil, reminderEpochMillis: nil)
        }
    }

    /// Computes the next occurrence date matching `ThingsRepository.spawnNextRecurrenceInstance`.
    public static func computeNextOccurrenceDate(
        rule: RecurrenceRule,
        completedWhenMillis: Int64?,
        nowMillis: Int64,
        calendar: Calendar = .current
    ) -> Date? {
        let safeInterval = min(3650, max(1, rule.interval))
        let baseMillis: Int64 = (rule.mode == .afterCompletion) ? nowMillis : (completedWhenMillis ?? nowMillis)
        let baseDate = calendar.startOfDay(for: DateEpochConverter.date(fromMillis: baseMillis))

        let nextDate: Date
        switch rule.unit {
        case .days:
            nextDate = calendar.date(byAdding: .day, value: safeInterval, to: baseDate) ?? baseDate
        case .weeks:
            let validDays = Array(Set(rule.daysOfWeek.filter { (1...7).contains($0) })).sorted()
            if rule.mode == .fixedSchedule && !validDays.isEmpty {
                let currentDow = DateEpochConverter.isoWeekday(for: baseDate, calendar: calendar)
                if let nextSameWeek = validDays.first(where: { $0 > currentDow }) {
                    nextDate = calendar.date(byAdding: .day, value: nextSameWeek - currentDow, to: baseDate) ?? baseDate
                } else {
                    let firstDow = validDays[0]
                    let mondayOfCurrent = calendar.date(byAdding: .day, value: -(currentDow - 1), to: baseDate) ?? baseDate
                    let mondayOfTarget = calendar.date(byAdding: .day, value: safeInterval * 7, to: mondayOfCurrent) ?? mondayOfCurrent
                    nextDate = calendar.date(byAdding: .day, value: firstDow - 1, to: mondayOfTarget) ?? mondayOfTarget
                }
            } else {
                nextDate = calendar.date(byAdding: .day, value: safeInterval * 7, to: baseDate) ?? baseDate
            }
        case .months:
            let monthAdvanced = calendar.date(byAdding: .month, value: safeInterval, to: baseDate) ?? baseDate
            if rule.mode == .fixedSchedule, let dom = rule.dayOfMonth {
                let range = calendar.range(of: .day, in: .month, for: monthAdvanced) ?? 1..<29
                let maxDom = range.upperBound - 1
                let targetDom = dom == -1 ? maxDom : min(maxDom, max(1, dom))
                var comps = calendar.dateComponents([.year, .month], from: monthAdvanced)
                comps.day = targetDom
                nextDate = calendar.date(from: comps) ?? monthAdvanced
            } else {
                nextDate = monthAdvanced
            }
        case .years:
            nextDate = calendar.date(byAdding: .year, value: safeInterval, to: baseDate) ?? baseDate
        }

        let nextEpoch = DateEpochConverter.startOfDayMillis(for: nextDate, calendar: calendar)
        if let endLimit = rule.endDateEpochMillis, nextEpoch > endLimit {
            return nil
        }
        return calendar.startOfDay(for: nextDate)
    }
}
