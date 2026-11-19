import SwiftUI
import AppKit
import ThingsbookCore

// MARK: - Full-Width Things-for-Mac Month Calendar Grid

private struct ThingsMacCalendarGridView: View {
    @Binding var displayedMonth: Date
    let selectedDate: Date?
    let locale: Locale
    var accentColor: Color = ThingsMacPalette.accentBlue
    let onSelectDate: (Date) -> Void

    @State private var hoveredDayId: Int? = nil
    @State private var hoveredNavButton: String? = nil

    private var grid: MacCalendarMonthGrid {
        MacCalendarMonthGrid.build(
            displayedMonth: displayedMonth,
            selectedDate: selectedDate,
            today: Date(),
            locale: locale
        )
    }

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 3), count: 7)

    var body: some View {
        let currentGrid = grid

        VStack(spacing: 8) {
            // Month title + < ● > navigation
            HStack(alignment: .center, spacing: 4) {
                Text(currentGrid.monthTitle)
                    .font(.system(size: 13.5, weight: .semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer()

                calendarNavButton(id: "prev", systemImage: "chevron.left", size: 10.5) {
                    shiftMonth(by: -1)
                }
                calendarNavButton(id: "today", systemImage: "circle.fill", size: 5.5) {
                    withAnimation(.easeOut(duration: 0.14)) {
                        displayedMonth = Date()
                    }
                }
                calendarNavButton(id: "next", systemImage: "chevron.right", size: 10.5) {
                    shiftMonth(by: 1)
                }
            }
            .padding(.horizontal, 2)

            // Weekday symbols row
            LazyVGrid(columns: columns, spacing: 2) {
                ForEach(Array(currentGrid.weekdaySymbols.enumerated()), id: \.offset) { _, symbol in
                    Text(symbol)
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 18)
                }
            }

            // Day cells grid (7 equal columns spanning 100% width)
            LazyVGrid(columns: columns, spacing: 3) {
                ForEach(currentGrid.days) { cell in
                    let isHovered = hoveredDayId == cell.id
                    Button {
                        if !cell.isInDisplayedMonth {
                            displayedMonth = cell.date
                        }
                        onSelectDate(cell.date)
                    } label: {
                        Text("\(cell.dayNumber)")
                            .font(.system(
                                size: 12.5,
                                weight: (cell.isSelected || cell.isToday) ? .semibold : .regular,
                                design: .rounded
                            ))
                            .foregroundStyle(dayForeground(for: cell))
                            .frame(maxWidth: .infinity)
                            .frame(height: 31)
                            .background(
                                RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                    .fill(dayBackground(for: cell, isHovered: isHovered))
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                    .strokeBorder(
                                        (cell.isToday && !cell.isSelected)
                                            ? accentColor.opacity(0.45)
                                            : Color.clear,
                                        lineWidth: 1.25
                                    )
                            )
                            .contentShape(RoundedRectangle(cornerRadius: 7.5, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .onHover { hovering in
                        hoveredDayId = hovering ? cell.id : (hoveredDayId == cell.id ? nil : hoveredDayId)
                        if hovering {
                            NSCursor.pointingHand.set()
                        } else {
                            NSCursor.arrow.set()
                        }
                    }
                }
            }
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer)
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.06), lineWidth: 0.75)
                )
        )
    }

    private func calendarNavButton(
        id: String,
        systemImage: String,
        size: CGFloat,
        action: @escaping () -> Void
    ) -> some View {
        let isHovered = hoveredNavButton == id
        return Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: size, weight: .bold))
                .foregroundStyle(isHovered ? Color.primary : Color.secondary)
                .frame(width: 24, height: 24)
                .background(
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(isHovered ? Color.primary.opacity(0.08) : Color.clear)
                )
                .contentShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            hoveredNavButton = hovering ? id : (hoveredNavButton == id ? nil : hoveredNavButton)
            if hovering {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }

    private func shiftMonth(by delta: Int) {
        if let shifted = Calendar.current.date(byAdding: .month, value: delta, to: displayedMonth) {
            withAnimation(.easeOut(duration: 0.14)) {
                displayedMonth = shifted
            }
        }
    }

    private func dayForeground(for cell: MacCalendarDayCell) -> Color {
        if cell.isSelected {
            return .white
        }
        if cell.isToday {
            return accentColor
        }
        if cell.isInDisplayedMonth {
            return .primary
        }
        return Color.secondary.opacity(0.42)
    }

    private func dayBackground(for cell: MacCalendarDayCell, isHovered: Bool) -> Color {
        if cell.isSelected {
            return accentColor
        }
        if isHovered {
            return Color.primary.opacity(0.08)
        }
        return .clear
    }
}

private struct SchedulingHoverPillButton: View {
    let title: String
    var systemImage: String? = nil
    var tint: Color = .secondary
    var hoverTint: Color = .primary
    var fillTint: Color = .primary
    let action: () -> Void

    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4.5) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 11, weight: .semibold))
                }
                Text(title)
                    .font(.system(size: 11.5, weight: .semibold))
                    .lineLimit(1)
            }
            .foregroundStyle(isHovered ? hoverTint : tint)
            .padding(.horizontal, 9)
            .padding(.vertical, 4.5)
            .background(
                Capsule(style: .continuous)
                    .fill(fillTint.opacity(isHovered ? 0.13 : 0.065))
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            isHovered = hovering
            if hovering {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }
}

// MARK: - When Scheduling Popover (with 5-Language Natural Date Parser + Reminder)

public struct WhenPopoverView: View {
    @ObservedObject var store: ThingsbookStore
    let currentSchedule: ScheduleState
    let currentWhenMillis: Int64?
    let currentReminderMillis: Int64?
    let onApply: (ResolvedWhenSelection) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var naturalInput = ""
    @State private var pickerDate: Date
    @State private var displayedMonth: Date
    @State private var hasExplicitDateSelection: Bool
    @State private var reminderEnabled: Bool
    @State private var reminderTimeDate: Date
    @FocusState private var isSearchFocused: Bool

    public init(
        store: ThingsbookStore,
        currentSchedule: ScheduleState,
        currentWhenMillis: Int64?,
        currentReminderMillis: Int64?,
        onApply: @escaping (ResolvedWhenSelection) -> Void
    ) {
        self.store = store
        self.currentSchedule = currentSchedule
        self.currentWhenMillis = currentWhenMillis
        self.currentReminderMillis = currentReminderMillis
        self.onApply = onApply

        let initialDate = currentWhenMillis.map { DateEpochConverter.date(fromMillis: $0) } ?? Date()
        _pickerDate = State(initialValue: initialDate)
        _displayedMonth = State(initialValue: initialDate)
        _hasExplicitDateSelection = State(initialValue: currentWhenMillis != nil)
        _reminderEnabled = State(initialValue: currentReminderMillis != nil)
        if let rem = currentReminderMillis {
            _reminderTimeDate = State(initialValue: DateEpochConverter.date(fromMillis: rem))
        } else {
            var comps = Calendar.current.dateComponents([.year, .month, .day], from: initialDate)
            comps.hour = 9
            comps.minute = 0
            _reminderTimeDate = State(initialValue: Calendar.current.date(from: comps) ?? Date())
        }
    }

    private var parsedNatural: ParsedNaturalDate? {
        MultilingualDateParser.parse(naturalInput, locale: store.preferences.language.locale)
    }

    private var reminderHourMinute: (Int, Int) {
        let comps = Calendar.current.dateComponents([.hour, .minute], from: reminderTimeDate)
        return (comps.hour ?? 9, comps.minute ?? 0)
    }

    private var canClearSelection: Bool {
        currentWhenMillis != nil || currentReminderMillis != nil || (currentSchedule != .inbox && currentSchedule != .anytime)
    }

    private var highlightedCalendarDate: Date? {
        if hasExplicitDateSelection || reminderEnabled {
            return pickerDate
        }
        return nil
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header + sleek Clear pill
            HStack(alignment: .center) {
                Text(store.tr("action_when"))
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(.secondary)

                Spacer()

                if canClearSelection {
                    SchedulingHoverPillButton(
                        title: store.tr("action_clear"),
                        systemImage: "xmark.circle.fill",
                        tint: .secondary,
                        hoverTint: ThingsMacPalette.upcomingRed,
                        fillTint: .primary
                    ) {
                        onApply(
                            ResolvedWhenSelection(
                                scheduleState: .anytime,
                                whenDateEpochMillis: nil,
                                reminderEpochMillis: nil
                            )
                        )
                        dismiss()
                    }
                }
            }

            // Natural language input (EN, DE, FR, IT, ES)
            HStack(spacing: 7) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(isSearchFocused ? ThingsMacPalette.accentBlue : .secondary)

                TextField(store.tr("when_natural_input_hint"), text: $naturalInput)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12))
                    .lineLimit(1)
                    .focused($isSearchFocused)
                    .onSubmit {
                        if let parsed = parsedNatural {
                            applyParsedNatural(parsed)
                        }
                    }

                if !naturalInput.isEmpty {
                    Button {
                        naturalInput = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(.tertiary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .strokeBorder(
                                isSearchFocused
                                    ? ThingsMacPalette.accentBlue.opacity(0.45)
                                    : Color.primary.opacity(0.09),
                                lineWidth: 1
                            )
                    )
            )

            if let parsed = parsedNatural {
                Button {
                    applyParsedNatural(parsed)
                } label: {
                    HStack(spacing: 7) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                        Text(parsed.formattedPreview)
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        Spacer()
                        Image(systemName: "return")
                            .font(.system(size: 10.5, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6.5)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(ThingsMacPalette.accentBlue.opacity(0.12))
                    )
                }
                .buttonStyle(.plain)
            }

            // Quick Presets
            VStack(spacing: 2) {
                WhenPresetRow(
                    icon: "star.fill",
                    color: ThingsMacPalette.todayYellow,
                    title: store.tr("view_today"),
                    shortcut: "⌘T",
                    isActive: currentSchedule == .today
                ) {
                    applyPreset(.today)
                }
                WhenPresetRow(
                    icon: "moon.stars.fill",
                    color: ThingsMacPalette.eveningPurple,
                    title: store.tr("view_this_evening"),
                    shortcut: "⌘E",
                    isActive: currentSchedule == .thisEvening
                ) {
                    applyPreset(.thisEvening)
                }
                WhenPresetRow(
                    icon: "sun.horizon.fill",
                    color: ThingsMacPalette.upcomingRed,
                    title: store.tr("date_tomorrow"),
                    shortcut: nil,
                    isActive: false
                ) {
                    applyPreset(.upcoming)
                }
                WhenPresetRow(
                    icon: "square.stack.3d.up.fill",
                    color: ThingsMacPalette.anytimeTeal,
                    title: store.tr("view_anytime"),
                    shortcut: nil,
                    isActive: currentSchedule == .anytime
                ) {
                    applyPreset(.anytime)
                }
                WhenPresetRow(
                    icon: "archivebox.fill",
                    color: ThingsMacPalette.somedayAmber,
                    title: store.tr("view_someday"),
                    shortcut: "⌘O",
                    isActive: currentSchedule == .someday
                ) {
                    applyPreset(.someday)
                }
            }

            // Full-Width Custom Month Calendar Grid
            ThingsMacCalendarGridView(
                displayedMonth: $displayedMonth,
                selectedDate: highlightedCalendarDate,
                locale: store.preferences.language.locale,
                accentColor: ThingsMacPalette.accentBlue
            ) { picked in
                pickerDate = picked
                hasExplicitDateSelection = true
                if !reminderEnabled {
                    applyPickedDate(picked)
                }
            }

            // Clean Single-Line Reminder Card
            VStack(alignment: .leading, spacing: 8) {
                Button {
                    withAnimation(.easeOut(duration: 0.15)) {
                        reminderEnabled.toggle()
                        if reminderEnabled {
                            hasExplicitDateSelection = true
                        }
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: reminderEnabled ? "bell.badge.fill" : "bell")
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(reminderEnabled ? ThingsMacPalette.accentBlue : .secondary)
                            .frame(width: 18)

                        Text(store.tr("when_task_reminder_title"))
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(.primary)
                            .lineLimit(1)

                        Spacer(minLength: 6)

                        Image(systemName: reminderEnabled ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(reminderEnabled ? ThingsMacPalette.accentBlue : Color.secondary.opacity(0.45))
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if reminderEnabled {
                    Divider()
                        .opacity(0.6)

                    HStack(spacing: 8) {
                        DatePicker("", selection: $reminderTimeDate, displayedComponents: [.hourAndMinute])
                            .labelsHidden()
                            .controlSize(.small)
                            .fixedSize()

                        Spacer()

                        Button {
                            applyPickedDate(pickerDate)
                        } label: {
                            Text(store.tr("when_apply_date_button"))
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 5)
                                .background(
                                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                                        .fill(ThingsMacPalette.accentBlue)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8.5)
            .background(
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
                    .overlay(
                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                            .strokeBorder(
                                reminderEnabled
                                    ? ThingsMacPalette.accentBlue.opacity(0.28)
                                    : Color.primary.opacity(0.06),
                                lineWidth: 0.85
                            )
                    )
            )
        }
        .padding(14)
        .frame(width: 328)
    }

    private func applyPickedDate(_ dateVal: Date) {
        let sod = Calendar.current.startOfDay(for: dateVal)
        let todaySod = Calendar.current.startOfDay(for: Date())
        let sched: ScheduleState = sod <= todaySod ? (currentSchedule == .thisEvening ? .thisEvening : .today) : .upcoming
        let (h, m) = reminderHourMinute
        let rem = WhenScheduleResolver.computeReminderEpoch(
            targetDate: sod,
            reminderEnabled: reminderEnabled,
            reminderHour: h,
            reminderMinute: m
        )
        onApply(
            ResolvedWhenSelection(
                scheduleState: sched,
                whenDateEpochMillis: DateEpochConverter.startOfDayMillis(for: sod),
                reminderEpochMillis: rem
            )
        )
        dismiss()
    }

    private func applyPreset(_ preset: ScheduleState) {
        let (h, m) = reminderHourMinute
        let resolved = WhenScheduleResolver.resolvePresetSelection(
            preset: preset,
            reminderEnabled: reminderEnabled,
            reminderHour: h,
            reminderMinute: m
        )
        onApply(resolved)
        dismiss()
    }

    private func applyParsedNatural(_ parsed: ParsedNaturalDate) {
        let (h, m) = reminderHourMinute
        let targetDate = parsed.whenDateEpochMillis.map { DateEpochConverter.date(fromMillis: $0) }
        let rem = WhenScheduleResolver.computeReminderEpoch(
            targetDate: targetDate,
            reminderEnabled: reminderEnabled,
            reminderHour: h,
            reminderMinute: m
        )
        onApply(
            ResolvedWhenSelection(
                scheduleState: parsed.scheduleState,
                whenDateEpochMillis: parsed.whenDateEpochMillis,
                reminderEpochMillis: rem
            )
        )
        dismiss()
    }
}

private struct WhenPresetRow: View {
    let icon: String
    let color: Color
    let title: String
    let shortcut: String?
    let isActive: Bool
    let action: () -> Void

    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 9) {
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(color)
                    .frame(width: 20, alignment: .center)

                Text(title)
                    .font(.system(size: 13, weight: isActive ? .semibold : .regular))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer()

                if isActive {
                    Image(systemName: "checkmark")
                        .font(.system(size: 10.5, weight: .bold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                }

                if let shortcut {
                    Text(shortcut)
                        .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 5.5)
                        .padding(.vertical, 1.5)
                        .background(
                            RoundedRectangle(cornerRadius: 4.5, style: .continuous)
                                .fill(Color.primary.opacity(0.065))
                        )
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5.5)
            .background(
                RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                    .fill(
                        isHovered
                            ? ThingsMacPalette.accentBlue.opacity(0.09)
                            : (isActive ? Color.primary.opacity(0.045) : Color.clear)
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 7.5, style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            isHovered = hovering
            if hovering {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }
}

// MARK: - Deadline Popover

public struct DeadlinePopoverView: View {
    @ObservedObject var store: ThingsbookStore
    let currentDeadlineMillis: Int64?
    let onSelectDeadline: (Int64?) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var displayedMonth: Date

    public init(store: ThingsbookStore, currentDeadlineMillis: Int64?, onSelectDeadline: @escaping (Int64?) -> Void) {
        self.store = store
        self.currentDeadlineMillis = currentDeadlineMillis
        self.onSelectDeadline = onSelectDeadline
        _displayedMonth = State(initialValue: currentDeadlineMillis.map { DateEpochConverter.date(fromMillis: $0) } ?? Date())
    }

    private var selectedDeadlineDate: Date? {
        currentDeadlineMillis.map { DateEpochConverter.date(fromMillis: $0) }
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center) {
                Label(store.tr("deadline_picker_title"), systemImage: "flag.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.deadlineCrimson)

                Spacer()

                if currentDeadlineMillis != nil {
                    SchedulingHoverPillButton(
                        title: store.tr("deadline_clear_button"),
                        systemImage: "xmark.circle.fill",
                        tint: .secondary,
                        hoverTint: ThingsMacPalette.deadlineCrimson,
                        fillTint: .primary
                    ) {
                        onSelectDeadline(nil)
                        dismiss()
                    }
                }
            }

            HStack(spacing: 6) {
                deadlineQuickChip(title: store.tr("view_today")) {
                    onSelectDeadline(DateEpochConverter.startOfDayMillis(for: Date()))
                    dismiss()
                }
                deadlineQuickChip(title: store.tr("date_tomorrow")) {
                    let tmr = Calendar.current.date(byAdding: .day, value: 1, to: Date()) ?? Date()
                    onSelectDeadline(DateEpochConverter.startOfDayMillis(for: tmr))
                    dismiss()
                }
                deadlineQuickChip(title: store.tr("macos_deadline_plus_7d")) {
                    let wk = Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()
                    onSelectDeadline(DateEpochConverter.startOfDayMillis(for: wk))
                    dismiss()
                }
            }

            ThingsMacCalendarGridView(
                displayedMonth: $displayedMonth,
                selectedDate: selectedDeadlineDate,
                locale: store.preferences.language.locale,
                accentColor: ThingsMacPalette.deadlineCrimson
            ) { picked in
                onSelectDeadline(DateEpochConverter.startOfDayMillis(for: picked))
                dismiss()
            }
        }
        .padding(14)
        .frame(width: 320)
    }

    private func deadlineQuickChip(title: String, action: @escaping () -> Void) -> some View {
        DeadlineQuickChipButton(title: title, action: action)
    }
}

private struct DeadlineQuickChipButton: View {
    let title: String
    let action: () -> Void

    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundStyle(isHovered ? ThingsMacPalette.deadlineCrimson : .primary)
                .lineLimit(1)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 5.5)
                .background(
                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                        .fill(
                            isHovered
                                ? ThingsMacPalette.deadlineCrimson.opacity(0.12)
                                : ThingsMacPalette.subtleContainer
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                .strokeBorder(
                                    isHovered
                                        ? ThingsMacPalette.deadlineCrimson.opacity(0.32)
                                        : Color.primary.opacity(0.08),
                                    lineWidth: 0.8
                                )
                        )
                )
                .contentShape(RoundedRectangle(cornerRadius: 7.5, style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            isHovered = hovering
            if hovering {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }
}

// MARK: - Repeat / Recurrence Dialog

public struct RepeatEditorPopoverView: View {
    @ObservedObject var store: ThingsbookStore
    let currentRule: RecurrenceRule?
    let onSave: (RecurrenceRule?) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var mode: RecurrenceMode
    @State private var interval: Int
    @State private var unit: RecurrenceUnit
    @State private var daysOfWeek: Set<Int>
    @State private var useLastDayOfMonth: Bool
    @State private var hasEndDate: Bool
    @State private var endDate: Date

    public init(store: ThingsbookStore, currentRule: RecurrenceRule?, onSave: @escaping (RecurrenceRule?) -> Void) {
        self.store = store
        self.currentRule = currentRule
        self.onSave = onSave

        _mode = State(initialValue: currentRule?.mode ?? .fixedSchedule)
        _interval = State(initialValue: currentRule?.interval ?? 1)
        _unit = State(initialValue: currentRule?.unit ?? .weeks)
        _daysOfWeek = State(initialValue: Set(currentRule?.daysOfWeek ?? [1]))
        _useLastDayOfMonth = State(initialValue: currentRule?.dayOfMonth == -1)
        _hasEndDate = State(initialValue: currentRule?.endDateEpochMillis != nil)
        _endDate = State(
            initialValue: currentRule?.endDateEpochMillis.map { DateEpochConverter.date(fromMillis: $0) }
                ?? Date().addingTimeInterval(30 * 86_400)
        )
    }

    private var localizedIsoWeekdays: [(Int, String)] {
        var cal = Calendar(identifier: .gregorian)
        cal.locale = store.preferences.language.locale
        let symbols = cal.veryShortStandaloneWeekdaySymbols // [Sun, Mon, Tue, Wed, Thu, Fri, Sat]
        guard symbols.count == 7 else {
            return [(1, "M"), (2, "T"), (3, "W"), (4, "T"), (5, "F"), (6, "S"), (7, "S")]
        }
        return [
            (1, symbols[1]),
            (2, symbols[2]),
            (3, symbols[3]),
            (4, symbols[4]),
            (5, symbols[5]),
            (6, symbols[6]),
            (7, symbols[0]),
        ]
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(store.tr("repeat_dialog_title"))
                .font(.system(size: 13.5, weight: .semibold))

            Picker("", selection: $mode) {
                Text(store.tr("repeat_mode_fixed")).tag(RecurrenceMode.fixedSchedule)
                Text(store.tr("repeat_mode_after")).tag(RecurrenceMode.afterCompletion)
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            Text(mode == .fixedSchedule ? store.tr("repeat_mode_fixed_desc") : store.tr("repeat_mode_after_desc"))
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 8) {
                Text(store.tr("macos_repeat_every"))
                    .font(.system(size: 12.5))
                Stepper(value: $interval, in: 1...365) {
                    Text("\(interval)")
                        .font(.system(size: 13, weight: .semibold))
                        .frame(minWidth: 24)
                }
                Picker("", selection: $unit) {
                    Text(store.tr("repeat_unit_days")).tag(RecurrenceUnit.days)
                    Text(store.tr("repeat_unit_weeks")).tag(RecurrenceUnit.weeks)
                    Text(store.tr("repeat_unit_months")).tag(RecurrenceUnit.months)
                    Text(store.tr("repeat_unit_years")).tag(RecurrenceUnit.years)
                }
                .labelsHidden()
                .frame(width: 116)
            }

            if mode == .fixedSchedule && unit == .weeks {
                VStack(alignment: .leading, spacing: 6) {
                    Text(store.tr("repeat_weekdays_label"))
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)

                    HStack(spacing: 5) {
                        ForEach(localizedIsoWeekdays, id: \.0) { iso, shortLabel in
                            let selected = daysOfWeek.contains(iso)
                            Button {
                                if selected && daysOfWeek.count > 1 {
                                    daysOfWeek.remove(iso)
                                } else {
                                    daysOfWeek.insert(iso)
                                }
                            } label: {
                                Text(shortLabel)
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .foregroundStyle(selected ? .white : .primary)
                                    .frame(width: 28, height: 28)
                                    .background(
                                        Circle().fill(selected ? ThingsMacPalette.accentBlue : Color.primary.opacity(0.07))
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            if mode == .fixedSchedule && unit == .months {
                Toggle(store.tr("repeat_last_day"), isOn: $useLastDayOfMonth)
                    .font(.system(size: 12))
            }

            HStack {
                Toggle(store.tr("repeat_ends_label"), isOn: $hasEndDate)
                    .font(.system(size: 12))
                if hasEndDate {
                    DatePicker("", selection: $endDate, displayedComponents: [.date])
                        .environment(\.locale, store.preferences.language.locale)
                        .labelsHidden()
                }
            }

            Divider()

            HStack {
                if currentRule != nil {
                    Button(role: .destructive) {
                        onSave(nil)
                        dismiss()
                    } label: {
                        Text(store.tr("repeat_off_btn"))
                    }
                }
                Spacer()
                Button(store.tr("action_cancel")) {
                    dismiss()
                }
                Button(store.tr("action_save")) {
                    let rule = RecurrenceRule(
                        mode: mode,
                        interval: interval,
                        unit: unit,
                        daysOfWeek: unit == .weeks ? Array(daysOfWeek).sorted() : [],
                        dayOfMonth: (unit == .months && useLastDayOfMonth) ? -1 : nil,
                        endDateEpochMillis: hasEndDate ? DateEpochConverter.endOfDayMillis(for: endDate) : nil
                    )
                    onSave(rule)
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(14)
        .frame(width: 320)
    }
}

// MARK: - Move Destination Popover

public struct MoveDestinationPopoverView: View {
    @ObservedObject var store: ThingsbookStore
    let task: TaskItem
    let onMove: (_ projectId: String?, _ headingId: String?, _ areaId: String?, _ scheduleOverride: ScheduleState?) -> Void
    @Environment(\.dismiss) private var dismiss

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(store.tr("move_dialog_title"))
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 6)

            ScrollView {
                VStack(alignment: .leading, spacing: 3) {
                    moveRow(icon: "tray.fill", tint: ThingsMacPalette.inboxBlue, title: store.tr("move_none_option")) {
                        onMove(nil, nil, nil, .inbox)
                        dismiss()
                    }

                    if !store.standaloneActiveProjects.isEmpty {
                        Divider().padding(.vertical, 3)
                        ForEach(store.standaloneActiveProjects) { proj in
                            moveRow(icon: "circle.inset.filled", tint: ThingsMacPalette.accentBlue, title: proj.title) {
                                onMove(proj.id, nil, nil, task.scheduleState == .inbox ? .anytime : nil)
                                dismiss()
                            }
                        }
                    }

                    ForEach(store.areas) { area in
                        Divider().padding(.vertical, 3)
                        moveRow(icon: "cube.fill", tint: ThingsMacPalette.areaGreen, title: area.title) {
                            onMove(nil, nil, area.id, task.scheduleState == .inbox ? .anytime : nil)
                            dismiss()
                        }
                        ForEach(store.projectsForArea(area.id)) { proj in
                            moveRow(icon: "circle.inset.filled", tint: ThingsMacPalette.accentBlue, title: "   \(proj.title)") {
                                onMove(proj.id, nil, area.id, task.scheduleState == .inbox ? .anytime : nil)
                                dismiss()
                            }
                        }
                    }
                }
            }
            .frame(maxHeight: 280)
        }
        .padding(10)
        .frame(width: 260)
    }

    private func moveRow(icon: String, tint: Color, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 12))
                    .foregroundStyle(tint)
                    .frame(width: 16)
                Text(title)
                    .font(.system(size: 12.5))
                    .lineLimit(1)
                Spacer()
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Tag Assignment Popover

public struct TagPickerPopoverView: View {
    @ObservedObject var store: ThingsbookStore
    let selectedTagIds: Set<String>
    let partialTagIds: Set<String>
    let onToggleTag: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var newTagTitle = ""
    @State private var newTagShortcut = ""

    public init(
        store: ThingsbookStore,
        selectedTagIds: Set<String>,
        partialTagIds: Set<String> = [],
        onToggleTag: @escaping (String) -> Void
    ) {
        self.store = store
        self.selectedTagIds = selectedTagIds
        self.partialTagIds = partialTagIds
        self.onToggleTag = onToggleTag
    }

    private var trimmedQuery: String {
        newTagTitle.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var filteredTags: [Tag] {
        let base = store.sortedTags
        guard !trimmedQuery.isEmpty else { return base }
        return base.filter { tag in
            tag.title.localizedCaseInsensitiveContains(trimmedQuery)
                || (tag.shortcut?.localizedCaseInsensitiveContains(trimmedQuery) == true)
        }
    }

    public var body: some View {
        let visibleTags = filteredTags

        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(store.tr("tags_assign_title"))
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(.secondary)
                Spacer()
                Button {
                    dismiss()
                    store.isTagManagerPresented = true
                } label: {
                    Image(systemName: "slider.horizontal.3")
                        .font(.system(size: 11.5))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("tags_manage_title"))
            }

            HStack(spacing: 6) {
                TextField(store.tr("v25_tags_assign_search_placeholder"), text: $newTagTitle)
                    .textFieldStyle(.roundedBorder)
                    .font(.system(size: 12.5))
                    .onSubmit(createNewOrAssignTag)

                TextField("⌃", text: $newTagShortcut)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 38)
                    .font(.system(size: 12))
                    .thingsTooltip(store.tr("tags_shortcut_placeholder"))
                    .onSubmit(createNewOrAssignTag)

                Button {
                    createNewOrAssignTag()
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                }
                .buttonStyle(.plain)
                .disabled(trimmedQuery.isEmpty)
            }

            // Constant-height locked list viewport (never resizes on filter)
            ZStack {
                if store.tags.isEmpty {
                    Text(store.tr("tags_empty_hint"))
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                } else if visibleTags.isEmpty {
                    Text(store.tr("v25_tags_no_matches"))
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(visibleTags) { tag in
                                let isChecked = selectedTagIds.contains(tag.id)
                                let isPartial = !isChecked && partialTagIds.contains(tag.id)
                                HStack {
                                    Button {
                                        onToggleTag(tag.id)
                                    } label: {
                                        HStack(spacing: 8) {
                                            Image(
                                                systemName: isChecked
                                                    ? "checkmark.circle.fill"
                                                    : (isPartial ? "minus.circle.fill" : "circle")
                                            )
                                            .foregroundStyle((isChecked || isPartial) ? ThingsMacPalette.accentBlue : .secondary)

                                            Text(tag.title)
                                                .font(.system(size: 13))
                                                .lineLimit(1)

                                            Spacer()

                                            if let sc = tag.shortcut, !sc.isEmpty {
                                                Text("⌃\(sc.uppercased())")
                                                    .font(.system(size: 10.5, weight: .medium, design: .monospaced))
                                                    .foregroundStyle(.secondary)
                                                    .padding(.horizontal, 5)
                                                    .padding(.vertical, 1)
                                                    .background(Capsule().fill(Color.primary.opacity(0.06)))
                                            }
                                        }
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)

                                    Button {
                                        if store.selectedFilterTagId == tag.id {
                                            store.selectedFilterTagId = nil
                                        }
                                        try? store.database.deleteTag(tag.id)
                                    } label: {
                                        Image(systemName: "trash")
                                            .font(.system(size: 11))
                                            .foregroundStyle(.tertiary)
                                    }
                                    .buttonStyle(.plain)
                                    .thingsTooltip(store.tr("action_delete"))
                                }
                                .padding(.vertical, 3)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                }
            }
            .frame(height: 220)
        }
        .padding(12)
        .frame(width: 310)
    }

    private func createNewOrAssignTag() {
        let clean = trimmedQuery
        guard !clean.isEmpty else { return }
        let shortcutArg = newTagShortcut.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : newTagShortcut
        if let existing = store.tags.first(where: { $0.title.caseInsensitiveCompare(clean) == .orderedSame }) {
            if let shortcutArg {
                _ = try? store.database.createOrUpdateTag(
                    id: existing.id,
                    title: existing.title,
                    parentTagId: existing.parentTagId,
                    shortcut: shortcutArg
                )
            }
            if !selectedTagIds.contains(existing.id) {
                onToggleTag(existing.id)
            }
        } else if let created = try? store.database.createOrUpdateTag(title: clean, shortcut: shortcutArg) {
            if !selectedTagIds.contains(created.id) {
                onToggleTag(created.id)
            }
        }
        newTagTitle = ""
        newTagShortcut = ""
    }
}

// MARK: - Project & Area Editor Sheets

public struct ProjectEditorSheetView: View {
    @ObservedObject var store: ThingsbookStore
    let existingProject: Project?
    let defaultAreaId: String?
    @Environment(\.dismiss) private var dismiss

    @State private var title: String
    @State private var notes: String
    @State private var areaId: String?
    @State private var hasDeadline: Bool
    @State private var deadlineDate: Date

    public init(store: ThingsbookStore, existingProject: Project? = nil, defaultAreaId: String? = nil) {
        self.store = store
        self.existingProject = existingProject
        self.defaultAreaId = defaultAreaId
        _title = State(initialValue: existingProject?.title ?? "")
        _notes = State(initialValue: existingProject?.notes ?? "")
        _areaId = State(initialValue: existingProject?.areaId ?? defaultAreaId)
        _hasDeadline = State(initialValue: existingProject?.deadline != nil)
        _deadlineDate = State(
            initialValue: existingProject?.deadline.map { DateEpochConverter.date(fromMillis: $0) }
                ?? Date().addingTimeInterval(7 * 86_400)
        )
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(existingProject == nil ? store.tr("entity_new_project") : store.tr("project_edit_title"))
                .font(.system(size: 16, weight: .semibold))

            TextField(store.tr("entity_project"), text: $title)
                .textFieldStyle(.roundedBorder)

            VStack(alignment: .leading, spacing: 4) {
                Text(store.tr("placeholder_notes"))
                    .font(.system(size: 11.5))
                    .foregroundStyle(.secondary)
                TextEditor(text: $notes)
                    .font(.system(size: 13))
                    .frame(height: 82)
                    .padding(4)
                    .background(RoundedRectangle(cornerRadius: 7).strokeBorder(Color.primary.opacity(0.14)))
            }

            Picker(store.tr("entity_area"), selection: $areaId) {
                Text(store.tr("areas_none")).tag(String?.none)
                ForEach(store.areas) { area in
                    Text(area.title).tag(Optional(area.id))
                }
            }

            HStack {
                Toggle(store.tr("project_target_date_label"), isOn: $hasDeadline)
                if hasDeadline {
                    DatePicker("", selection: $deadlineDate, displayedComponents: [.date])
                        .environment(\.locale, store.preferences.language.locale)
                        .labelsHidden()
                }
            }

            if let existing = existingProject {
                let projAttachments = store.projectAttachments[existing.id] ?? []
                if projAttachments.isEmpty {
                    Button {
                        store.pickAndAttachFiles(projectId: existing.id)
                    } label: {
                        Label(store.tr("action_attach_file"), systemImage: "paperclip")
                            .font(.system(size: 12.5, weight: .medium))
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                } else {
                    AttachmentsStripView(store: store, attachments: projAttachments) {
                        store.pickAndAttachFiles(projectId: existing.id)
                    }
                }
            }

            HStack {
                Spacer()
                Button(store.tr("action_cancel")) { dismiss() }
                Button(existingProject == nil ? store.tr("macos_action_create") : store.tr("action_save")) {
                    let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !cleanTitle.isEmpty else { return }
                    let dl = hasDeadline ? DateEpochConverter.startOfDayMillis(for: deadlineDate) : nil
                    if var proj = existingProject {
                        proj.title = cleanTitle
                        proj.notes = notes
                        proj.areaId = areaId
                        proj.deadline = dl
                        try? store.database.updateProject(proj)
                    } else if let created = try? store.database.createProject(
                        title: cleanTitle,
                        notes: notes,
                        areaId: areaId,
                        deadline: dl
                    ) {
                        store.selectedDestination = .project(created.id)
                    }
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
                .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 450)
    }
}

public struct AreaEditorSheetView: View {
    @ObservedObject var store: ThingsbookStore
    let existingArea: Area?
    @Environment(\.dismiss) private var dismiss

    @State private var title: String
    @State private var errorMessage: String?

    public init(store: ThingsbookStore, existingArea: Area? = nil) {
        self.store = store
        self.existingArea = existingArea
        _title = State(initialValue: existingArea?.title ?? "")
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(existingArea == nil ? store.tr("entity_new_area") : store.tr("areas_rename_label"))
                .font(.system(size: 16, weight: .semibold))

            TextField(store.tr("entity_area"), text: $title)
                .textFieldStyle(.roundedBorder)
                .onSubmit(saveArea)

            if let errorMessage {
                Text(errorMessage)
                    .font(.system(size: 12))
                    .foregroundStyle(.red)
            }

            HStack {
                Spacer()
                Button(store.tr("action_cancel")) { dismiss() }
                Button(existingArea == nil ? store.tr("macos_action_create") : store.tr("action_save")) {
                    saveArea()
                }
                .buttonStyle(.borderedProminent)
                .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 370)
    }

    private func saveArea() {
        let clean = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        do {
            if var area = existingArea {
                area.title = clean
                try store.database.updateArea(area)
            } else {
                let created = try store.database.createArea(title: clean)
                store.selectedDestination = .area(created.id)
            }
            dismiss()
        } catch {
            errorMessage = store.trf("areas_max_reached", ThingsSQLiteDatabase.maxAreas)
        }
    }
}

// MARK: - Manage Areas Sheet (Full Parity with Android ManageAreasDialog)

public struct ManageAreasSheetView: View {
    @ObservedObject var store: ThingsbookStore
    @Environment(\.dismiss) private var dismiss

    @State private var newAreaTitle = ""
    @State private var renamingAreaId: String?
    @State private var renameDraft = ""
    @State private var errorMessage: String?

    public init(store: ThingsbookStore) {
        self.store = store
    }

    private var remainingSlots: Int {
        max(0, ThingsSQLiteDatabase.maxAreas - store.areas.count)
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label(store.tr("areas_manage_title"), systemImage: "cube.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.primary)
                Spacer()
                Text(store.trf("areas_active_count", store.areas.count, ThingsSQLiteDatabase.maxAreas))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.secondary)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(store.areas.enumerated()), id: \.element.id) { index, area in
                        HStack(spacing: 10) {
                            Image(systemName: "cube.fill")
                                .foregroundStyle(ThingsMacPalette.areaGreen)
                                .font(.system(size: 13))

                            if renamingAreaId == area.id {
                                TextField(store.tr("entity_area"), text: $renameDraft)
                                    .textFieldStyle(.roundedBorder)
                                    .font(.system(size: 13))
                                    .onSubmit {
                                        commitAreaRename(area)
                                    }

                                Button(store.tr("action_save")) {
                                    commitAreaRename(area)
                                }
                                .buttonStyle(.borderedProminent)
                                .controlSize(.small)

                                Button(store.tr("action_cancel")) {
                                    renamingAreaId = nil
                                }
                                .controlSize(.small)
                            } else {
                                Button {
                                    store.selectedDestination = .area(area.id)
                                    dismiss()
                                } label: {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(area.title)
                                            .font(.system(size: 13.5, weight: .semibold))
                                            .foregroundStyle(.primary)
                                        Text(store.trf("areas_projects_count_hint", store.projectsForArea(area.id).count))
                                            .font(.system(size: 11))
                                            .foregroundStyle(.secondary)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)

                                Button {
                                    store.moveAreaOrder(area, direction: -1)
                                } label: {
                                    Image(systemName: "chevron.up")
                                        .font(.system(size: 11, weight: .semibold))
                                }
                                .buttonStyle(.borderless)
                                .disabled(index == 0)
                                .thingsTooltip(store.tr("area_move_up"))

                                Button {
                                    store.moveAreaOrder(area, direction: 1)
                                } label: {
                                    Image(systemName: "chevron.down")
                                        .font(.system(size: 11, weight: .semibold))
                                }
                                .buttonStyle(.borderless)
                                .disabled(index == store.areas.count - 1)
                                .thingsTooltip(store.tr("area_move_down"))

                                Button {
                                    renamingAreaId = area.id
                                    renameDraft = area.title
                                } label: {
                                    Image(systemName: "pencil")
                                        .font(.system(size: 12))
                                }
                                .buttonStyle(.borderless)
                                .thingsTooltip(store.tr("areas_rename_label"))

                                Button(role: .destructive) {
                                    if store.selectedDestination == .area(area.id) {
                                        store.selectedDestination = .smartView(.today)
                                    }
                                    try? store.database.deleteArea(area.id)
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.system(size: 12))
                                        .foregroundStyle(ThingsMacPalette.upcomingRed)
                                }
                                .buttonStyle(.borderless)
                                .thingsTooltip(store.tr("action_delete"))
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 9)
                        .thingsCardSurface(cornerRadius: 9)
                    }
                }
            }
            .frame(maxHeight: 270)

            Divider()

            if remainingSlots > 0 {
                VStack(alignment: .leading, spacing: 6) {
                    Text(store.trf("areas_add_new_hint", remainingSlots))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(.secondary)

                    HStack(spacing: 8) {
                        TextField(store.tr("entity_new_area"), text: $newAreaTitle)
                            .textFieldStyle(.roundedBorder)
                            .onSubmit(addNewArea)

                        Button(store.tr("macos_action_add")) {
                            addNewArea()
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(newAreaTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            } else {
                Text(store.trf("areas_max_reached", ThingsSQLiteDatabase.maxAreas))
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(.system(size: 12))
                    .foregroundStyle(.red)
            }

            HStack {
                Spacer()
                Button(store.tr("action_done")) {
                    dismiss()
                }
            }
        }
        .padding(20)
        .frame(width: 480)
    }

    private func commitAreaRename(_ area: Area) {
        let clean = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        var updated = area
        updated.title = clean
        try? store.database.updateArea(updated)
        renamingAreaId = nil
    }

    private func addNewArea() {
        let clean = newAreaTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        do {
            _ = try store.database.createArea(title: clean)
            newAreaTitle = ""
            errorMessage = nil
        } catch {
            errorMessage = store.trf("areas_max_reached", ThingsSQLiteDatabase.maxAreas)
        }
    }
}

// MARK: - Manage Tags Sheet (Full Parity with Android ManageTagsDialog)

public struct ManageTagsSheetView: View {
    @ObservedObject var store: ThingsbookStore
    @Environment(\.dismiss) private var dismiss

    @State private var searchQuery = ""
    @State private var newTagTitle = ""
    @State private var newTagShortcut = ""
    @State private var editingTagId: String?
    @State private var editTitleDraft = ""
    @State private var editShortcutDraft = ""

    public init(store: ThingsbookStore) {
        self.store = store
    }

    private var trimmedSearch: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var usageCounts: [String: Int] {
        store.tagUsageCounts
    }

    private var sortedAllTags: [Tag] {
        TagListSorter.sort(
            store.tags,
            mode: store.preferences.tagSortMode,
            usageCounts: usageCounts
        )
    }

    private var filteredTags: [Tag] {
        let base = sortedAllTags
        guard !trimmedSearch.isEmpty else { return base }
        return base.filter { tag in
            tag.title.localizedCaseInsensitiveContains(trimmedSearch)
                || (tag.shortcut?.localizedCaseInsensitiveContains(trimmedSearch) == true)
        }
    }

    public var body: some View {
        let counts = usageCounts
        let visibleTags = filteredTags
        let manualOrderedIds = TagListSorter.sort(store.tags, mode: "manual", usageCounts: [:]).map(\.id)

        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(store.tr("tags_manage_title"), systemImage: "tag.fill")
                    .font(.system(size: 16, weight: .semibold))
                Spacer()
                Text(store.trf("settings_stat_tags_count", store.tags.count))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.secondary)
            }

            // Search + Sort Bar
            HStack(spacing: 10) {
                HStack(spacing: 6) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 11.5, weight: .medium))
                        .foregroundStyle(.secondary)

                    TextField(store.tr("v25_tags_search_placeholder"), text: $searchQuery)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12.5))

                    if !searchQuery.isEmpty {
                        Button {
                            searchQuery = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 11.5))
                                .foregroundStyle(.tertiary)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 5.5)
                .background(
                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                        .fill(ThingsMacPalette.subtleContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                .strokeBorder(Color.primary.opacity(0.09), lineWidth: 0.85)
                        )
                )

                Picker("", selection: $store.preferences.tagSortMode) {
                    Text(store.tr("v25_tags_sort_alpha")).tag("alpha")
                    Text(store.tr("v25_tags_sort_most_used")).tag("most_used")
                    Text(store.tr("v25_tags_sort_manual")).tag("manual")
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .fixedSize()
            }

            // Constant-height locked list viewport (never resizes on filter)
            ZStack {
                if store.tags.isEmpty {
                    Text(store.tr("tags_empty_hint"))
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                } else if visibleTags.isEmpty {
                    Text(store.tr("v25_tags_no_matches"))
                        .font(.system(size: 13))
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 7) {
                            ForEach(visibleTags) { tag in
                                let manualIdx = manualOrderedIds.firstIndex(of: tag.id)
                                let count = counts[tag.id] ?? 0
                                HStack(spacing: 9) {
                                    Image(systemName: "tag.fill")
                                        .font(.system(size: 12))
                                        .foregroundStyle(ThingsMacPalette.accentBlue)

                                    if editingTagId == tag.id {
                                        TextField(store.tr("entity_tag"), text: $editTitleDraft)
                                            .textFieldStyle(.roundedBorder)
                                            .onSubmit {
                                                commitTagEdit(tag)
                                            }

                                        TextField("⌃", text: $editShortcutDraft)
                                            .textFieldStyle(.roundedBorder)
                                            .frame(width: 44)
                                            .onSubmit {
                                                commitTagEdit(tag)
                                            }

                                        Button(store.tr("action_save")) {
                                            commitTagEdit(tag)
                                        }
                                        .buttonStyle(.borderedProminent)
                                        .controlSize(.small)

                                        Button(store.tr("action_cancel")) {
                                            editingTagId = nil
                                        }
                                        .controlSize(.small)
                                    } else {
                                        Text(tag.title)
                                            .font(.system(size: 13.5, weight: .medium))
                                            .lineLimit(1)

                                        if let sc = tag.shortcut, !sc.isEmpty {
                                            Text("⌃\(sc.uppercased())")
                                                .font(.system(size: 10.5, weight: .semibold, design: .monospaced))
                                                .foregroundStyle(.secondary)
                                                .padding(.horizontal, 6)
                                                .padding(.vertical, 2)
                                                .background(Capsule().fill(Color.primary.opacity(0.07)))
                                        }

                                        Text("\(count)")
                                            .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                                            .foregroundStyle(.secondary)
                                            .padding(.horizontal, 6.5)
                                            .padding(.vertical, 2)
                                            .background(Capsule().fill(Color.primary.opacity(0.06)))

                                        Spacer()

                                        if store.preferences.tagSortMode == "manual" {
                                            Button {
                                                store.moveTagOrder(tag.id, moveUp: true)
                                            } label: {
                                                Image(systemName: "arrow.up")
                                                    .font(.system(size: 11, weight: .semibold))
                                            }
                                            .buttonStyle(.borderless)
                                            .disabled(!trimmedSearch.isEmpty || manualIdx == nil || manualIdx == 0)

                                            Button {
                                                store.moveTagOrder(tag.id, moveUp: false)
                                            } label: {
                                                Image(systemName: "arrow.down")
                                                    .font(.system(size: 11, weight: .semibold))
                                            }
                                            .buttonStyle(.borderless)
                                            .disabled(!trimmedSearch.isEmpty || manualIdx == nil || manualIdx == manualOrderedIds.count - 1)
                                        }

                                        Button {
                                            editingTagId = tag.id
                                            editTitleDraft = tag.title
                                            editShortcutDraft = tag.shortcut ?? ""
                                        } label: {
                                            Image(systemName: "pencil")
                                                .font(.system(size: 12))
                                        }
                                        .buttonStyle(.borderless)
                                        .thingsTooltip(store.tr("tags_edit_title"))

                                        if store.tags.count >= 2 {
                                            let mergeTargets = sortedAllTags.filter { $0.id != tag.id }
                                            Menu {
                                                Section(store.trf("v25_tags_merge_target_prompt", tag.title)) {
                                                    ForEach(mergeTargets) { target in
                                                        Button("#\(target.title)") {
                                                            store.mergeTag(sourceTag: tag, into: target)
                                                        }
                                                    }
                                                }
                                            } label: {
                                                Image(systemName: "arrow.triangle.merge")
                                                    .font(.system(size: 12))
                                            }
                                            .menuStyle(.borderlessButton)
                                            .fixedSize()
                                            .thingsTooltip(store.tr("v25_tags_merge_action"))
                                        }

                                        Button(role: .destructive) {
                                            if store.selectedFilterTagId == tag.id {
                                                store.selectedFilterTagId = nil
                                            }
                                            try? store.database.deleteTag(tag.id)
                                        } label: {
                                            Image(systemName: "trash")
                                                .font(.system(size: 12))
                                                .foregroundStyle(ThingsMacPalette.upcomingRed)
                                        }
                                        .buttonStyle(.borderless)
                                        .thingsTooltip(store.tr("action_delete"))
                                    }
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .thingsCardSurface(cornerRadius: 9)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                }
            }
            .frame(height: 280)

            Divider()

            HStack(spacing: 8) {
                TextField(store.tr("tags_new_placeholder"), text: $newTagTitle)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit(addTag)

                TextField(store.tr("tags_shortcut_placeholder"), text: $newTagShortcut)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 78)
                    .onSubmit(addTag)

                Button(store.tr("tags_add_button")) {
                    addTag()
                }
                .buttonStyle(.borderedProminent)
                .disabled(newTagTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }

            HStack {
                Spacer()
                Button(store.tr("action_done")) {
                    dismiss()
                }
            }
        }
        .padding(20)
        .frame(width: 520)
    }

    private func addTag() {
        let clean = newTagTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        let shortcutArg = newTagShortcut.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : newTagShortcut
        _ = try? store.database.createOrUpdateTag(title: clean, shortcut: shortcutArg)
        newTagTitle = ""
        newTagShortcut = ""
    }

    private func commitTagEdit(_ tag: Tag) {
        let clean = editTitleDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        let shortcutArg = editShortcutDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : editShortcutDraft
        _ = try? store.database.createOrUpdateTag(
            id: tag.id,
            title: clean,
            parentTagId: tag.parentTagId,
            shortcut: shortcutArg
        )
        editingTagId = nil
    }
}

// MARK: - v2.0 Interactive Timeblock Popover

public struct TimeblockPopoverView: View {
    @ObservedObject var store: ThingsbookStore
    let currentStartMinutes: Int?
    let currentDurationMinutes: Int?
    let onApply: (_ startMinutes: Int?, _ durationMinutes: Int?) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var selectedStartMinutes: Int
    @State private var selectedDurationMinutes: Int
    @State private var customTimeDate: Date

    private static let presetStartHours: [Int] = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18]
    private static let presetDurations: [Int] = [15, 30, 45, 60, 90, 120]

    public init(
        store: ThingsbookStore,
        currentStartMinutes: Int?,
        currentDurationMinutes: Int?,
        onApply: @escaping (_ startMinutes: Int?, _ durationMinutes: Int?) -> Void
    ) {
        self.store = store
        self.currentStartMinutes = currentStartMinutes
        self.currentDurationMinutes = currentDurationMinutes
        self.onApply = onApply

        let initialStart = currentStartMinutes ?? (9 * 60)
        let initialDuration = currentDurationMinutes ?? 30
        _selectedStartMinutes = State(initialValue: initialStart)
        _selectedDurationMinutes = State(initialValue: initialDuration)

        var comps = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        comps.hour = (initialStart / 60) % 24
        comps.minute = initialStart % 60
        _customTimeDate = State(initialValue: Calendar.current.date(from: comps) ?? Date())
    }

    private var formattedRangePreview: String {
        let endMins = (selectedStartMinutes + selectedDurationMinutes) % 1440
        return "\(TaskItem.formatMinutesOfDay(selectedStartMinutes))–\(TaskItem.formatMinutesOfDay(endMins)) · \(selectedDurationMinutes)m"
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 11) {
            HStack {
                Label(store.tr("v2_timeblock_title"), systemImage: "clock.badge.checkmark.fill")
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                Spacer()

                if currentStartMinutes != nil || currentDurationMinutes != nil {
                    Button(store.tr("v2_timeblock_clear")) {
                        onApply(nil, nil)
                        dismiss()
                    }
                    .font(.system(size: 11.5))
                    .foregroundStyle(ThingsMacPalette.upcomingRed)
                    .buttonStyle(.plain)
                }
            }

            Divider()

            HStack {
                Text(store.tr("v2_timeblock_start"))
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(.secondary)
                Spacer()
                DatePicker("", selection: $customTimeDate, displayedComponents: [.hourAndMinute])
                    .labelsHidden()
                    .frame(width: 88)
                    .onChange(of: customTimeDate) { _, newDate in
                        let comps = Calendar.current.dateComponents([.hour, .minute], from: newDate)
                        selectedStartMinutes = ((comps.hour ?? 9) * 60) + (comps.minute ?? 0)
                    }
            }

            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 5), count: 4), spacing: 5) {
                ForEach(Self.presetStartHours, id: \.self) { hour in
                    let mins = hour * 60
                    let isSelected = selectedStartMinutes == mins
                    Button {
                        selectedStartMinutes = mins
                        syncCustomDatePicker(to: mins)
                    } label: {
                        Text(String(format: "%02d:00", hour))
                            .font(.system(size: 11, weight: isSelected ? .semibold : .medium, design: .monospaced))
                            .foregroundStyle(isSelected ? Color.white : Color.primary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 4.5)
                            .background(
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(isSelected ? ThingsMacPalette.accentBlue : Color.primary.opacity(0.06))
                            )
                    }
                    .buttonStyle(.plain)
                }
            }

            Text(store.tr("v2_timeblock_duration"))
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundStyle(.secondary)

            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 5), count: 3), spacing: 5) {
                ForEach(Self.presetDurations, id: \.self) { dur in
                    let isSelected = selectedDurationMinutes == dur
                    Button {
                        selectedDurationMinutes = dur
                    } label: {
                        Text("\(dur)m")
                            .font(.system(size: 11.5, weight: isSelected ? .semibold : .medium, design: .rounded))
                            .foregroundStyle(isSelected ? Color.white : Color.primary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 4.5)
                            .background(
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(isSelected ? ThingsMacPalette.accentBlue : Color.primary.opacity(0.06))
                            )
                    }
                    .buttonStyle(.plain)
                }
            }

            Divider()

            HStack {
                Text(formattedRangePreview)
                    .font(.system(size: 11.5, weight: .semibold, design: .monospaced))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                Spacer()

                Button(store.tr("action_save")) {
                    onApply(selectedStartMinutes, selectedDurationMinutes)
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
            }
        }
        .padding(13)
        .frame(width: 288)
    }

    private func syncCustomDatePicker(to minutesOfDay: Int) {
        var comps = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        comps.hour = (minutesOfDay / 60) % 24
        comps.minute = minutesOfDay % 60
        if let updated = Calendar.current.date(from: comps) {
            customTimeDate = updated
        }
    }
}
