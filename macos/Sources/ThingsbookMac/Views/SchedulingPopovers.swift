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
        MultilingualDateParser.parse(naturalInput)
    }

    private var reminderHourMinute: (Int, Int) {
        let comps = Calendar.current.dateComponents([.hour, .minute], from: reminderTimeDate)
        return (comps.hour ?? 9, comps.minute ?? 0)
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(store.tr("when_title"))
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.secondary)

            // Natural language input
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                TextField(store.tr("when_natural_placeholder"), text: $naturalInput)
                    .textFieldStyle(.plain)
                    .font(.system(size: 13))
                    .onSubmit {
                        if let parsed = parsedNatural {
                            applyParsedNatural(parsed)
                        }
                    }
            }
            .padding(.horizontal, 9)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 7, style: .continuous).fill(Color.primary.opacity(0.06)))

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
                    .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(ThingsMacPalette.accentBlue.opacity(0.12)))
                }
                .buttonStyle(.plain)
            }

            // Quick Presets
            VStack(spacing: 3) {
                presetRow(icon: "star.fill", color: ThingsMacPalette.todayYellow, title: store.tr("when_today"), shortcut: "⌘T") {
                    applyPreset(.today)
                }
                presetRow(icon: "moon.stars.fill", color: ThingsMacPalette.eveningPurple, title: store.tr("when_this_evening"), shortcut: "⌘E") {
                    applyPreset(.thisEvening)
                }
                presetRow(icon: "sun.horizon.fill", color: ThingsMacPalette.upcomingRed, title: store.tr("when_tomorrow"), shortcut: nil) {
                    applyPreset(.upcoming)
                }
                presetRow(icon: "square.stack.3d.up.fill", color: ThingsMacPalette.anytimeTeal, title: store.tr("when_anytime"), shortcut: nil) {
                    applyPreset(.anytime)
                }
                presetRow(icon: "archivebox.fill", color: ThingsMacPalette.somedayAmber, title: store.tr("when_someday"), shortcut: "⌘O") {
                    applyPreset(.someday)
                }
            }

            Divider()

            DatePicker(
                "",
                selection: $pickerDate,
                displayedComponents: [.date]
            )
            .datePickerStyle(.graphical)
            .labelsHidden()
            .onChange(of: pickerDate) { _, newValue in
                let sod = Calendar.current.startOfDay(for: newValue)
                let todaySod = Calendar.current.startOfDay(for: Date())
                let sched: ScheduleState = sod <= todaySod ? .today : .upcoming
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

            Divider()

            // Optional Reminder Time
            HStack {
                Toggle(isOn: $reminderEnabled) {
                    Label(store.tr("todo_add_reminder"), systemImage: "bell.fill")
                        .font(.system(size: 12))
                }
                .toggleStyle(.checkbox)

                Spacer()

                if reminderEnabled {
                    DatePicker("", selection: $reminderTimeDate, displayedComponents: [.hourAndMinute])
                        .labelsHidden()
                        .frame(width: 90)
                }
            }

            if currentWhenMillis != nil || currentReminderMillis != nil || currentSchedule != .inbox {
                HStack {
                    Button(store.tr("when_clear")) {
                        onApply(ResolvedWhenSelection(scheduleState: .anytime, whenDateEpochMillis: nil, reminderEpochMillis: nil))
                        dismiss()
                    }
                    .font(.system(size: 12))

                    Spacer()

                    if reminderEnabled {
                        Button(store.tr("action_save")) {
                            let target = currentWhenMillis.map { DateEpochConverter.date(fromMillis: $0) } ?? pickerDate
                            let sod = Calendar.current.startOfDay(for: target)
                            let todaySod = Calendar.current.startOfDay(for: Date())
                            let sched: ScheduleState = sod <= todaySod ? (currentSchedule == .thisEvening ? .thisEvening : .today) : .upcoming
                            let (h, m) = reminderHourMinute
                            let rem = WhenScheduleResolver.computeReminderEpoch(
                                targetDate: sod,
                                reminderEnabled: true,
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
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                    }
                }
            }
        }
        .padding(12)
        .frame(width: 288)
    }

    private func presetRow(icon: String, color: Color, title: String, shortcut: String?, action: @escaping () -> Void) -> some View {
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
                Label(store.tr("deadline_title"), systemImage: "flag.fill")
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                Spacer()
                if currentDeadlineMillis != nil {
                    Button(store.tr("when_clear")) {
                        onSelectDeadline(nil)
                        dismiss()
                    }
                    .font(.system(size: 11.5))
                }
            }

            HStack(spacing: 6) {
                Button(store.tr("when_today")) {
                    onSelectDeadline(DateEpochConverter.startOfDayMillis(for: Date()))
                    dismiss()
                }
                .controlSize(.small)

                Button(store.tr("when_tomorrow")) {
                    let tmr = Calendar.current.date(byAdding: .day, value: 1, to: Date()) ?? Date()
                    onSelectDeadline(DateEpochConverter.startOfDayMillis(for: tmr))
                    dismiss()
                }
                .controlSize(.small)

                Button("+7d") {
                    let wk = Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()
                    onSelectDeadline(DateEpochConverter.startOfDayMillis(for: wk))
                    dismiss()
                }
                .controlSize(.small)
            }

            DatePicker("", selection: $selectedDate, displayedComponents: [.date])
                .datePickerStyle(.graphical)
                .labelsHidden()
                .onChange(of: selectedDate) { _, newValue in
                    onSelectDeadline(DateEpochConverter.startOfDayMillis(for: newValue))
                    dismiss()
                }
        }
        .padding(12)
        .frame(width: 278)
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
        _endDate = State(initialValue: currentRule?.endDateEpochMillis.map { DateEpochConverter.date(fromMillis: $0) } ?? Date().addingTimeInterval(30 * 86_400))
    }

    private let isoWeekdays: [(Int, String)] = [
        (1, "M"), (2, "T"), (3, "W"), (4, "T"), (5, "F"), (6, "S"), (7, "S")
    ]

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(store.tr("repeat_dialog_title"))
                .font(.system(size: 13.5, weight: .semibold))

            Picker("", selection: $mode) {
                Text(store.tr("repeat_mode_fixed")).tag(RecurrenceMode.fixedSchedule)
                Text(store.tr("repeat_mode_after_completion")).tag(RecurrenceMode.afterCompletion)
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            HStack(spacing: 8) {
                Text(store.tr("repeat_every_label"))
                    .font(.system(size: 12.5))
                Stepper(value: $interval, in: 1...365) {
                    Text("\(interval)")
                        .font(.system(size: 13, weight: .semibold))
                        .frame(minWidth: 24)
                }
                Picker("", selection: $unit) {
                    Text(store.tr("repeat_unit_day")).tag(RecurrenceUnit.days)
                    Text(store.tr("repeat_unit_week")).tag(RecurrenceUnit.weeks)
                    Text(store.tr("repeat_unit_month")).tag(RecurrenceUnit.months)
                    Text(store.tr("repeat_unit_year")).tag(RecurrenceUnit.years)
                }
                .labelsHidden()
                .frame(width: 110)
            }

            if mode == .fixedSchedule && unit == .weeks {
                HStack(spacing: 5) {
                    ForEach(isoWeekdays, id: \.0) { iso, shortLabel in
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

            if mode == .fixedSchedule && unit == .months {
                Toggle(store.tr("repeat_last_day_of_month"), isOn: $useLastDayOfMonth)
                    .font(.system(size: 12))
            }

            Toggle(store.tr("repeat_ends_on"), isOn: $hasEndDate)
                .font(.system(size: 12))
            if hasEndDate {
                DatePicker("", selection: $endDate, displayedComponents: [.date])
                    .labelsHidden()
            }

            Divider()

            HStack {
                if currentRule != nil {
                    Button(role: .destructive) {
                        onSave(nil)
                        dismiss()
                    } label: {
                        Text(store.tr("when_clear"))
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
        .frame(width: 310)
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
                    moveRow(icon: "tray.fill", tint: ThingsMacPalette.inboxBlue, title: store.tr("move_no_project_inbox")) {
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
        .frame(width: 250)
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

    @State private var newTagTitle = ""
    @State private var newTagShortcut = ""

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(store.tr("tag_dialog_title"))
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(.secondary)

            HStack(spacing: 6) {
                TextField(store.tr("tag_new_label"), text: $newTagTitle)
                    .textFieldStyle(.roundedBorder)
                    .font(.system(size: 12.5))
                    .onSubmit(createNewTag)

                TextField("⌃", text: $newTagShortcut)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 36)
                    .font(.system(size: 12))
                    .help(store.tr("tag_shortcut_hint"))

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
                Text(store.tr("tag_empty_hint"))
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
                            }
                            .padding(.vertical, 3)
                        }
                    }
                }
                .frame(maxHeight: 220)
            }
        }
        .padding(12)
        .frame(width: 265)
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
        _deadlineDate = State(initialValue: existingProject?.deadline.map { DateEpochConverter.date(fromMillis: $0) } ?? Date().addingTimeInterval(7 * 86_400))
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(existingProject == nil ? store.tr("dialog_new_project_title") : store.tr("project_edit_dialog_title"))
                .font(.system(size: 16, weight: .semibold))

            TextField(store.tr("dialog_project_name_label"), text: $title)
                .textFieldStyle(.roundedBorder)

            VStack(alignment: .leading, spacing: 4) {
                Text(store.tr("project_notes_placeholder"))
                    .font(.system(size: 11.5))
                    .foregroundStyle(.secondary)
                TextEditor(text: $notes)
                    .font(.system(size: 13))
                    .frame(height: 80)
                    .padding(4)
                    .background(RoundedRectangle(cornerRadius: 7).strokeBorder(Color.primary.opacity(0.14)))
            }

            Picker(store.tr("dialog_area_name_label"), selection: $areaId) {
                Text(store.tr("project_Standalone_label")).tag(String?.none)
                ForEach(store.areas) { area in
                    Text(area.title).tag(Optional(area.id))
                }
            }

            Toggle(store.tr("deadline_title"), isOn: $hasDeadline)
            if hasDeadline {
                DatePicker("", selection: $deadlineDate, displayedComponents: [.date])
                    .labelsHidden()
            }

            HStack {
                Spacer()
                Button(store.tr("action_cancel")) { dismiss() }
                Button(existingProject == nil ? store.tr("action_create") : store.tr("action_save")) {
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
        .frame(width: 420)
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
            Text(existingArea == nil ? store.tr("dialog_new_area_title") : store.tr("area_rename_title"))
                .font(.system(size: 16, weight: .semibold))

            TextField(store.tr("dialog_area_name_label"), text: $title)
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
                Button(existingArea == nil ? store.tr("action_create") : store.tr("action_save")) {
                    saveArea()
                }
                .buttonStyle(.borderedProminent)
                .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 360)
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
            errorMessage = store.trf("area_max_reached", ThingsSQLiteDatabase.maxAreas)
        }
    }
}
