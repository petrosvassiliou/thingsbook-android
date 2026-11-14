import Foundation
import EventKit
import AppKit

public final class AppleCalendarService: @unchecked Sendable {
    public static let shared = AppleCalendarService()

    private let eventStore = EKEventStore()

    private init() {}

    public var authorizationGranted: Bool {
        let status = EKEventStore.authorizationStatus(for: .event)
        if #available(macOS 14.0, *) {
            return status == .fullAccess
        } else {
            return status == .authorized
        }
    }

    public func requestAccess() async -> Bool {
        do {
            if #available(macOS 14.0, *) {
                return try await eventStore.requestFullAccessToEvents()
            } else {
                return try await eventStore.requestAccess(to: .event)
            }
        } catch {
            return false
        }
    }

    public func listCalendars() -> [MacCalendarInfo] {
        guard authorizationGranted else { return [] }
        let ekCals = eventStore.calendars(for: .event)
        let defaultId = eventStore.defaultCalendarForNewEvents?.calendarIdentifier
        return ekCals.map { cal in
            MacCalendarInfo(
                id: cal.calendarIdentifier,
                title: cal.title,
                sourceTitle: cal.source?.title ?? "macOS",
                colorHex: Self.hexFromCGColor(cal.cgColor),
                isPrimary: cal.calendarIdentifier == defaultId
            )
        }.sorted {
            if $0.isPrimary != $1.isPrimary { return $0.isPrimary }
            return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
        }
    }

    public func queryEvents(
        selectedCalendarIds: [String],
        startDate: Date,
        endDate: Date,
        calendar: Calendar = .current
    ) -> [CalendarEventItem] {
        guard authorizationGranted else { return [] }
        let allCals = eventStore.calendars(for: .event)
        let targetCals: [EKCalendar]
        if selectedCalendarIds.isEmpty {
            targetCals = allCals
        } else {
            let set = Set(selectedCalendarIds)
            targetCals = allCals.filter { set.contains($0.calendarIdentifier) }
            if targetCals.isEmpty { return [] }
        }

        let predicate = eventStore.predicateForEvents(withStart: startDate, end: endDate, calendars: targetCals)
        let events = eventStore.events(matching: predicate)

        return events.map { ev in
            let startMillis = Int64(ev.startDate.timeIntervalSince1970 * 1000.0)
            let rawEndMillis = Int64(ev.endDate.timeIntervalSince1970 * 1000.0)
            let startEpochDay = DateEpochConverter.epochDay(for: ev.startDate, calendar: calendar)

            // For all-day or midnight-ending events, subtract 1 second when computing inclusive end day
            let adjustedEnd = ev.endDate.timeIntervalSince(ev.startDate) >= 60
                ? ev.endDate.addingTimeInterval(-1)
                : ev.endDate
            let endEpochDay = max(startEpochDay, DateEpochConverter.epochDay(for: adjustedEnd, calendar: calendar))

            return CalendarEventItem(
                id: "\(ev.eventIdentifier ?? UUID().uuidString)_\(startMillis)",
                eventIdentifier: ev.eventIdentifier ?? "",
                calendarId: ev.calendar.calendarIdentifier,
                calendarDisplayName: ev.calendar.title,
                title: (ev.title ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
                startMillis: startMillis,
                endMillis: max(startMillis, rawEndMillis),
                isAllDay: ev.isAllDay,
                colorHex: Self.hexFromCGColor(ev.calendar.cgColor),
                startDateEpochDay: startEpochDay,
                endDateInclusiveEpochDay: endEpochDay
            )
        }.sorted {
            if $0.isAllDay != $1.isAllDay { return $0.isAllDay }
            return $0.startMillis < $1.startMillis
        }
    }

    public func openEventInAppleCalendar(_ event: CalendarEventItem) {
        let date = DateEpochConverter.date(fromMillis: event.startMillis)
        let secondsSince2001 = Int(date.timeIntervalSinceReferenceDate)
        if let url = URL(string: "calshow:\(secondsSince2001)") {
            NSWorkspace.shared.open(url)
        }
    }

    private static func hexFromCGColor(_ cgColor: CGColor?) -> UInt32 {
        guard let cgColor,
              let nsColor = NSColor(cgColor: cgColor)?.usingColorSpace(.sRGB) else {
            return 0xFF1A73E8
        }
        let r = UInt32(min(255, max(0, Int(round(nsColor.redComponent * 255)))))
        let g = UInt32(min(255, max(0, Int(round(nsColor.greenComponent * 255)))))
        let b = UInt32(min(255, max(0, Int(round(nsColor.blueComponent * 255)))))
        return (0xFF << 24) | (r << 16) | (g << 8) | b
    }
}
