import SwiftUI
import ThingsbookCore

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
    @State private var reminderEnabled: Bool
    @State private var reminderTimeDate: Date

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

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(store.tr("action_when"))
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.secondary)

            // Natural language input (EN, DE, FR, IT, ES)
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                TextField(store.tr("when_natural_input_hint"), text: $naturalInput)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12.5))
                    .onSubmit {
                        if let parsed = parsedNatural {
                            applyParsedNatural(parsed)
                        }
                    }
            }
            .padding(.horizontal, 9)
            .padding(.vertical, 6)
            .background(
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .fill(Color.primary.opacity(0.06))
            )

            if let parsed = parsedNatural {
                Button {
                    applyParsedNatural(parsed)
                } label: {
                    HStack {
                        Image(systemName: "sparkles")
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                        Text(parsed.formattedPreview)
                            .font(.system(size: 12.5, weight: .medium))
                        Spacer()
                        Image(systemName: "return")
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(ThingsMacPalette.accentBlue.opacity(0.12))
                    )
                }
                .buttonStyle(.plain)
            }

            // Quick Presets
            VStack(spacing: 2) {
                presetRow(
                    icon: "star.fill",
                    color: ThingsMacPalette.todayYellow,
                    title: store.tr("view_today"),
                    shortcut: "⌘T"
                ) {
                    applyPreset(.today)
                }
                presetRow(
                    icon: "moon.stars.fill",
                    color: ThingsMacPalette.eveningPurple,
                    title: store.tr("view_this_evening"),
                    shortcut: "⌘E"
                ) {
                    applyPreset(.thisEvening)
                }
                presetRow(
                    icon: "sun.horizon.fill",
                    color: ThingsMacPalette.upcomingRed,
                    title: store.tr("date_tomorrow"),
                    shortcut: nil
                ) {
                    applyPreset(.upcoming)
                }
                presetRow(
                    icon: "square.stack.3d.up.fill",
                    color: ThingsMacPalette.anytimeTeal,
                    title: store.tr("view_anytime"),
                    shortcut: nil
                ) {
                    applyPreset(.anytime)
                }
                presetRow(
                    icon: "archivebox.fill",
                    color: ThingsMacPalette.somedayAmber,
                    title: store.tr("view_someday"),
                    shortcut: "⌘O"
                ) {
                    applyPreset(.someday)
                }
            }

            Divider()

            DatePicker(
                "",
                selection: $pickerDate,
                displayedComponents: [.date]
            )
            .environment(\.locale, store.preferences.language.locale)
            .datePickerStyle(.graphical)
            .labelsHidden()
            .onChange(of: pickerDate) { _, newValue in
                if !reminderEnabled {
                    applyPickedDate(newValue)
                }
            }

            Divider()

            // Optional Reminder Time
            HStack {
                Toggle(isOn: $reminderEnabled) {
                    Label(store.tr("when_task_reminder_title"), systemImage: "bell.fill")
                        .font(.system(size: 12))
                }
                .toggleStyle(.checkbox)

                Spacer()

                if reminderEnabled {
                    DatePicker("", selection: $reminderTimeDate, displayedComponents: [.hourAndMinute])
                        .labelsHidden()
                        .frame(width: 88)
                }
            }

            HStack {
                if currentWhenMillis != nil || currentReminderMillis != nil || currentSchedule != .inbox {
                    Button(store.tr("action_clear")) {
                        onApply(
                            ResolvedWhenSelection(
                                scheduleState: .anytime,
                                whenDateEpochMillis: nil,
                                reminderEpochMillis: nil
                            )
                        )
                        dismiss()
                    }
                    .font(.system(size: 12))
                }

                Spacer()

                if reminderEnabled {
                    Button(store.tr("when_apply_date_button")) {
                        applyPickedDate(pickerDate)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                }
            }
        }
        .padding(12)
        .frame(width: 296)
    }

    private func presetRow(
        icon: String,
        color: Color,
        title: String,
        shortcut: String?,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(color)
                    .frame(width: 18)
                Text(title)
                    .font(.system(size: 13))
                Spacer()
                if let shortcut {
                    Text(shortcut)
                        .font(.system(size: 11))
                        .foregroundStyle(.tertiary)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
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

// MARK: - Deadline Popover

public struct DeadlinePopoverView: View {
    @ObservedObject var store: ThingsbookStore
    let currentDeadlineMillis: Int64?
    let onSelectDeadline: (Int64?) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var selectedDate: Date

    public init(store: ThingsbookStore, currentDeadlineMillis: Int64?, onSelectDeadline: @escaping (Int64?) -> Void) {
        self.store = store
        self.currentDeadlineMillis = currentDeadlineMillis
        self.onSelectDeadline = onSelectDeadline
        _selectedDate = State(initialValue: currentDeadlineMillis.map { DateEpochConverter.date(fromMillis: $0) } ?? Date())
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label(store.tr("deadline_picker_title"), systemImage: "flag.fill")
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                Spacer()
                if currentDeadlineMillis != nil {
                    Button(store.tr("deadline_clear_button")) {
                        onSelectDeadline(nil)
                        dismiss()
                    }
                    .font(.system(size: 11.5))
                }
            }

            HStack(spacing: 6) {
                Button(store.tr("view_today")) {
                    onSelectDeadline(DateEpochConverter.startOfDayMillis(for: Date()))
                    dismiss()
                }
                .controlSize(.small)

                Button(store.tr("date_tomorrow")) {
                    let tmr = Calendar.current.date(byAdding: .day, value: 1, to: Date()) ?? Date()
                    onSelectDeadline(DateEpochConverter.startOfDayMillis(for: tmr))
                    dismiss()
                }
                .controlSize(.small)

                Button(store.tr("macos_deadline_plus_7d")) {
                    let wk = Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()
                    onSelectDeadline(DateEpochConverter.startOfDayMillis(for: wk))
                    dismiss()
                }
                .controlSize(.small)
            }

            DatePicker("", selection: $selectedDate, displayedComponents: [.date])
                .environment(\.locale, store.preferences.language.locale)
                .datePickerStyle(.graphical)
                .labelsHidden()
                .onChange(of: selectedDate) { _, newValue in
                    onSelectDeadline(DateEpochConverter.startOfDayMillis(for: newValue))
                    dismiss()
                }
        }
        .padding(12)
        .frame(width: 284)
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
    let onToggleTag: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var newTagTitle = ""
    @State private var newTagShortcut = ""

    public var body: some View {
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
                .help(store.tr("tags_manage_title"))
            }

            HStack(spacing: 6) {
                TextField(store.tr("tags_new_placeholder"), text: $newTagTitle)
                    .textFieldStyle(.roundedBorder)
                    .font(.system(size: 12.5))
                    .onSubmit(createNewTag)

                TextField("⌃", text: $newTagShortcut)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 38)
                    .font(.system(size: 12))
                    .help(store.tr("tags_shortcut_placeholder"))

                Button {
                    createNewTag()
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                }
                .buttonStyle(.plain)
                .disabled(newTagTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }

            if store.tags.isEmpty {
                Text(store.tr("tags_empty_hint"))
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 8)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(store.tags) { tag in
                            let isChecked = selectedTagIds.contains(tag.id)
                            HStack {
                                Button {
                                    onToggleTag(tag.id)
                                } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: isChecked ? "checkmark.circle.fill" : "circle")
                                            .foregroundStyle(isChecked ? ThingsMacPalette.accentBlue : .secondary)
                                        Text(tag.title)
                                            .font(.system(size: 13))
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
                                    try? store.database.deleteTag(tag.id)
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.system(size: 11))
                                        .foregroundStyle(.tertiary)
                                }
                                .buttonStyle(.plain)
                                .help(store.tr("action_delete"))
                            }
                            .padding(.vertical, 3)
                        }
                    }
                }
                .frame(maxHeight: 220)
            }
        }
        .padding(12)
        .frame(width: 280)
    }

    private func createNewTag() {
        let clean = newTagTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        if let created = try? store.database.createOrUpdateTag(title: clean, shortcut: newTagShortcut) {
            onToggleTag(created.id)
            newTagTitle = ""
            newTagShortcut = ""
        }
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
        .frame(width: 430)
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

                                Button {
                                    store.moveAreaOrder(area, direction: 1)
                                } label: {
                                    Image(systemName: "chevron.down")
                                        .font(.system(size: 11, weight: .semibold))
                                }
                                .buttonStyle(.borderless)
                                .disabled(index == store.areas.count - 1)

                                Button {
                                    renamingAreaId = area.id
                                    renameDraft = area.title
                                } label: {
                                    Image(systemName: "pencil")
                                        .font(.system(size: 12))
                                }
                                .buttonStyle(.borderless)
                                .help(store.tr("areas_rename_label"))

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
                                .help(store.tr("action_delete"))
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
                .keyboardShortcut(.defaultAction)
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

    @State private var newTagTitle = ""
    @State private var newTagShortcut = ""
    @State private var editingTagId: String?
    @State private var editTitleDraft = ""
    @State private var editShortcutDraft = ""

    public init(store: ThingsbookStore) {
        self.store = store
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label(store.tr("tags_manage_title"), systemImage: "tag.fill")
                    .font(.system(size: 16, weight: .semibold))
                Spacer()
                Text(store.trf("settings_stat_tags_count", store.tags.count))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.secondary)
            }

            HStack(spacing: 8) {
                TextField(store.tr("tags_new_placeholder"), text: $newTagTitle)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit(addTag)

                TextField(store.tr("tags_shortcut_placeholder"), text: $newTagShortcut)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 78)

                Button(store.tr("tags_add_button")) {
                    addTag()
                }
                .buttonStyle(.borderedProminent)
                .disabled(newTagTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }

            if store.tags.isEmpty {
                Text(store.tr("tags_empty_hint"))
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 28)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 7) {
                        ForEach(store.tags) { tag in
                            HStack(spacing: 10) {
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

                                    if let sc = tag.shortcut, !sc.isEmpty {
                                        Text("⌃\(sc.uppercased())")
                                            .font(.system(size: 10.5, weight: .semibold, design: .monospaced))
                                            .foregroundStyle(.secondary)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Capsule().fill(Color.primary.opacity(0.07)))
                                    }

                                    Spacer()

                                    Button {
                                        editingTagId = tag.id
                                        editTitleDraft = tag.title
                                        editShortcutDraft = tag.shortcut ?? ""
                                    } label: {
                                        Image(systemName: "pencil")
                                            .font(.system(size: 12))
                                    }
                                    .buttonStyle(.borderless)
                                    .help(store.tr("tags_edit_title"))

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
                                    .help(store.tr("action_delete"))
                                }
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .thingsCardSurface(cornerRadius: 9)
                        }
                    }
                }
                .frame(maxHeight: 280)
            }

            HStack {
                Spacer()
                Button(store.tr("action_done")) {
                    dismiss()
                }
                .keyboardShortcut(.defaultAction)
            }
        }
        .padding(20)
        .frame(width: 480)
    }

    private func addTag() {
        let clean = newTagTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        _ = try? store.database.createOrUpdateTag(title: clean, shortcut: newTagShortcut)
        newTagTitle = ""
        newTagShortcut = ""
    }

    private func commitTagEdit(_ tag: Tag) {
        let clean = editTitleDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        _ = try? store.database.createOrUpdateTag(
            id: tag.id,
            title: clean,
            parentTagId: tag.parentTagId,
            shortcut: editShortcutDraft
        )
        editingTagId = nil
    }
}
