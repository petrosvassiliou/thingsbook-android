import SwiftUI
import AppKit
import ThingsbookCore

public struct SmartSyntaxPreviewPillsView: View {
    @ObservedObject var store: ThingsbookStore
    public let parsed: ParsedSmartSyntax
    public var showDetectedLabel: Bool = true
    public var onApply: (() -> Void)? = nil

    public init(
        store: ThingsbookStore,
        parsed: ParsedSmartSyntax,
        showDetectedLabel: Bool = true,
        onApply: (() -> Void)? = nil
    ) {
        self.store = store
        self.parsed = parsed
        self.showDetectedLabel = showDetectedLabel
        self.onApply = onApply
    }

    public var body: some View {
        HStack(alignment: .center, spacing: 8) {
            ThingsFlowLayout(horizontalSpacing: 5, verticalSpacing: 5) {
                if showDetectedLabel {
                    HStack(spacing: 4) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 10, weight: .semibold))
                        Text(store.tr("v32_smart_syntax_detected"))
                            .font(.system(size: 10.5, weight: .bold))
                            .lineLimit(1)
                    }
                    .foregroundStyle(ThingsMacPalette.geminiPurple)
                    .padding(.vertical, 2.5)
                }

                if let whenLabel = parsed.whenLabel, let sched = parsed.scheduleState {
                    smartPill(
                        icon: whenIcon(for: sched),
                        text: whenLabel,
                        tint: whenTint(for: sched)
                    )
                }

                if let dlLabel = parsed.deadlineLabel {
                    smartPill(
                        icon: "flag.fill",
                        text: dlLabel,
                        tint: ThingsMacPalette.deadlineCrimson
                    )
                }

                if let dur = parsed.durationMinutes, dur > 0 {
                    smartPill(
                        icon: "timer",
                        text: TaskItem.formatCompactDuration(dur),
                        tint: ThingsMacPalette.anytimeTeal
                    )
                }

                if parsed.isMilestone {
                    smartPill(
                        icon: "diamond.fill",
                        text: store.tr("v30_milestone_label"),
                        tint: ThingsMacPalette.milestoneViolet
                    )
                }

                if let proj = parsed.matchedProject {
                    smartPill(
                        icon: "folder.fill",
                        text: proj.title,
                        tint: ThingsMacPalette.accentBlue
                    )
                } else if let area = parsed.matchedArea {
                    smartPill(
                        icon: "folder",
                        text: area.title,
                        tint: ThingsMacPalette.accentBlue
                    )
                }

                ForEach(parsed.matchedTags) { tag in
                    smartPill(
                        icon: "number",
                        text: tag.title,
                        tint: ThingsMacPalette.accentBlue
                    )
                }
            }

            if let onApply {
                Spacer(minLength: 4)
                Button(action: onApply) {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 10, weight: .bold))
                        Text(store.tr("v32_smart_syntax_apply"))
                            .font(.system(size: 11, weight: .bold))
                            .lineLimit(1)
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        Capsule(style: .continuous)
                            .fill(ThingsMacPalette.accentBlue)
                    )
                }
                .buttonStyle(.plain)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip(store.tr("v32_smart_syntax_apply"))
            }
        }
    }

    private func whenIcon(for sched: ScheduleState) -> String {
        switch sched {
        case .today: return "sun.max.fill"
        case .thisEvening: return "moon.stars.fill"
        case .someday: return "cloud.fill"
        default: return "calendar"
        }
    }

    private func whenTint(for sched: ScheduleState) -> Color {
        switch sched {
        case .today: return ThingsMacPalette.todayYellow
        case .thisEvening: return ThingsMacPalette.eveningPurple
        case .someday: return ThingsMacPalette.somedayAmber
        default: return ThingsMacPalette.accentBlue
        }
    }

    private func smartPill(icon: String, text: String, tint: Color) -> some View {
        HStack(spacing: 3.5) {
            Image(systemName: icon)
                .font(.system(size: 9.5, weight: .semibold))
            Text(text)
                .font(.system(size: 11, weight: .semibold))
                .lineLimit(1)
        }
        .fixedSize(horizontal: true, vertical: false)
        .foregroundStyle(tint)
        .padding(.horizontal, 7.5)
        .padding(.vertical, 2.5)
        .background(
            Capsule(style: .continuous)
                .fill(tint.opacity(0.13))
        )
        .overlay(
            Capsule(style: .continuous)
                .strokeBorder(tint.opacity(0.28), lineWidth: 0.75)
        )
    }
}

private struct PaletteCommandItem: Identifiable {
    let id: String
    let title: String
    let icon: String
    let tint: Color
    let shortcut: String?
    let action: () -> Void
}

public struct QuickFindOverlay: View {
    @ObservedObject var store: ThingsbookStore
    @Environment(\.dismiss) private var dismiss

    @State private var query = ""
    @State private var filter = QuickFindFilter()
    @State private var matchedTasks: [TaskItem] = []
    @State private var matchedProjects: [Project] = []
    @State private var selectedEntryId: String? = nil
    @State private var keyMonitor: Any? = nil
    @FocusState private var isSearchFocused: Bool

    public init(store: ThingsbookStore) {
        self.store = store
    }

    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isCommandOnlyMode: Bool {
        trimmedQuery.hasPrefix(">")
    }

    private var parsedSyntax: ParsedSmartSyntax {
        store.parseSmartSyntax(query)
    }

    private var shouldShowCreateTaskRow: Bool {
        !trimmedQuery.isEmpty && !isCommandOnlyMode
    }

    private var allAvailableCommands: [PaletteCommandItem] {
        var items: [PaletteCommandItem] = [
            PaletteCommandItem(
                id: "cmd:plan_my_day",
                title: store.tr("v32_cmd_plan_my_day"),
                icon: "sparkles",
                tint: ThingsMacPalette.geminiPurple,
                shortcut: nil
            ) {
                dismiss()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                    store.openGeminiPlanMyDay()
                }
            },
            PaletteCommandItem(
                id: "cmd:ai_architect",
                title: store.tr("v32_cmd_ai_architect"),
                icon: "wand.and.stars",
                tint: ThingsMacPalette.geminiPurple,
                shortcut: "⌘G"
            ) {
                dismiss()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                    store.geminiInitialTab = .brainDump
                    store.isGeminiSheetPresented = true
                }
            },
            PaletteCommandItem(
                id: "cmd:weekly_review",
                title: store.tr("v32_cmd_weekly_review"),
                icon: "checkmark.seal.fill",
                tint: ThingsMacPalette.accentBlue,
                shortcut: "⇧⌘R"
            ) {
                dismiss()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                    store.isWeeklyReviewPresented = true
                }
            }
        ]

        if case .project(let pid) = store.selectedDestination,
           let proj = store.project(byId: pid),
           proj.status == .active,
           !proj.isDeleted {
            items.append(
                PaletteCommandItem(
                    id: "cmd:complete_project",
                    title: store.tr("macos_complete_project"),
                    icon: "checkmark.circle.fill",
                    tint: ThingsMacPalette.logbookGreen,
                    shortcut: nil
                ) {
                    dismiss()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) {
                        store.requestCompleteProject(proj)
                    }
                }
            )
            items.append(
                PaletteCommandItem(
                    id: "cmd:switch_list",
                    title: store.tr("v32_cmd_switch_list"),
                    icon: "list.bullet",
                    tint: ThingsMacPalette.accentBlue,
                    shortcut: "⌃⌘1"
                ) {
                    store.setProjectViewMode(pid, mode: .list)
                    dismiss()
                }
            )
            items.append(
                PaletteCommandItem(
                    id: "cmd:switch_board",
                    title: store.tr("v32_cmd_switch_board"),
                    icon: "rectangle.split.3x1",
                    tint: ThingsMacPalette.accentBlue,
                    shortcut: "⌃⌘2"
                ) {
                    store.setProjectViewMode(pid, mode: .board)
                    dismiss()
                }
            )
            items.append(
                PaletteCommandItem(
                    id: "cmd:switch_roadmap",
                    title: store.tr("v32_cmd_switch_roadmap"),
                    icon: "chart.bar.xaxis",
                    tint: ThingsMacPalette.accentBlue,
                    shortcut: "⌃⌘3"
                ) {
                    store.setProjectViewMode(pid, mode: .roadmap)
                    dismiss()
                }
            )
        } else if case .area(let aid) = store.selectedDestination,
                  store.area(byId: aid) != nil {
            items.append(
                PaletteCommandItem(
                    id: "cmd:switch_list",
                    title: store.tr("v32_cmd_switch_list"),
                    icon: "list.bullet",
                    tint: ThingsMacPalette.accentBlue,
                    shortcut: "⌃⌘1"
                ) {
                    store.setAreaViewMode(aid, mode: .list)
                    dismiss()
                }
            )
            items.append(
                PaletteCommandItem(
                    id: "cmd:switch_roadmap",
                    title: store.tr("v32_cmd_switch_roadmap"),
                    icon: "chart.bar.xaxis",
                    tint: ThingsMacPalette.accentBlue,
                    shortcut: "⌃⌘2"
                ) {
                    store.setAreaViewMode(aid, mode: .roadmap)
                    dismiss()
                }
            )
        }

        if store.focusTimer != nil {
            items.append(
                PaletteCommandItem(
                    id: "cmd:zen_focus",
                    title: store.tr("v32_cmd_zen_focus"),
                    icon: "timer",
                    tint: ThingsMacPalette.accentBlue,
                    shortcut: nil
                ) {
                    dismiss()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.04) {
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                            store.isZenFocusPresented = true
                        }
                    }
                }
            )
        }

        items.append(contentsOf: [
            PaletteCommandItem(
                id: "cmd:open_templates",
                title: store.tr("v32_cmd_open_templates"),
                icon: "square.on.square.dashed",
                tint: ThingsMacPalette.anytimeTeal,
                shortcut: nil
            ) {
                dismiss()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                    store.templatesInitialTab = .gallery
                    store.isTemplatesSheetPresented = true
                }
            },
            PaletteCommandItem(
                id: "cmd:toggle_theme",
                title: store.tr("v32_cmd_toggle_theme"),
                icon: "circle.lefthalf.filled",
                tint: ThingsMacPalette.eveningPurple,
                shortcut: nil
            ) {
                store.cycleThemeMode()
                dismiss()
            },
            PaletteCommandItem(
                id: "cmd:open_settings",
                title: store.tr("v32_cmd_open_settings"),
                icon: "gearshape.fill",
                tint: .secondary,
                shortcut: "⌘,"
            ) {
                dismiss()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                    store.settingsTab = .general
                    store.isSettingsPresented = true
                }
            }
        ])

        return items
    }

    private var matchingCommands: [PaletteCommandItem] {
        let cmds = allAvailableCommands
        if trimmedQuery.isEmpty {
            return filter.hasActiveFilters ? [] : cmds
        }
        if isCommandOnlyMode {
            let sub = String(trimmedQuery.dropFirst()).trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            guard !sub.isEmpty else { return cmds }
            return cmds.filter { $0.title.lowercased().contains(sub) }
        }
        let q = trimmedQuery.lowercased()
        return cmds.filter { $0.title.lowercased().contains(q) }
    }

    private var matchingSmartViews: [SmartView] {
        guard !isCommandOnlyMode, filter.shouldShowSmartViewsAndAreas(forQuery: query) else { return [] }
        let q = trimmedQuery.lowercased()
        guard !q.isEmpty else { return SmartView.allCases }
        return SmartView.allCases.filter { store.tr($0.titleKey).lowercased().contains(q) }
    }

    private var matchingAreas: [Area] {
        guard !isCommandOnlyMode, filter.shouldShowSmartViewsAndAreas(forQuery: query) else { return [] }
        let q = trimmedQuery.lowercased()
        guard !q.isEmpty else { return store.areas }
        return store.areas.filter { $0.title.lowercased().contains(q) }
    }

    private var orderedNavigableEntryIds: [String] {
        var ids: [String] = []
        let createTop = shouldShowCreateTaskRow && parsedSyntax.hasAnyMatch
        if createTop {
            ids.append("create_task")
        }
        ids.append(contentsOf: matchingCommands.map(\.id))
        if shouldShowCreateTaskRow && !createTop {
            ids.append("create_task")
        }
        ids.append(contentsOf: matchingSmartViews.map { "view:\($0.id)" })
        ids.append(contentsOf: matchingAreas.map { "area:\($0.id)" })
        ids.append(contentsOf: matchedProjects.map { "proj:\($0.id)" })
        ids.append(contentsOf: matchedTasks.map { "task:\($0.id)" })
        return ids
    }

    private var selectedTagTitle: String {
        if let tagId = filter.tagId, let tag = store.tags.first(where: { $0.id == tagId }) {
            return "#\(tag.title)"
        }
        return store.tr("search_filter_all_tags")
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: isCommandOnlyMode ? "terminal.fill" : "magnifyingglass")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(isCommandOnlyMode ? ThingsMacPalette.geminiPurple : ThingsMacPalette.accentBlue)

                TextField(store.tr("v32_cmd_palette_placeholder"), text: $query)
                    .textFieldStyle(.plain)
                    .font(.system(size: 16))
                    .focused($isSearchFocused)
                    .onChange(of: query) { _, _ in
                        runSearch()
                        syncDefaultSelection()
                    }
                    .onSubmit {
                        executeSelectedOrFirstResult()
                    }

                if !query.isEmpty {
                    Button {
                        query = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(store.tr("quick_find_clear"))
                }

                Button("ESC") {
                    dismiss()
                }
                .font(.system(size: 10.5, weight: .semibold, design: .monospaced))
                .buttonStyle(.bordered)
                .controlSize(.mini)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip("\(store.tr("action_done")) (Esc)")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 13)

            Divider()

            // Search Filters Strip
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 7) {
                    filterTogglePill(
                        icon: "eye.slash",
                        title: store.tr("search_filter_hide_completed"),
                        isActive: filter.hideCompleted,
                        helpText: store.tr("search_filter_hide_completed")
                    ) {
                        filter.hideCompleted.toggle()
                    }

                    Menu {
                        ForEach(QuickFindDateFilter.allCases) { df in
                            Button {
                                filter.dateFilter = df
                            } label: {
                                HStack {
                                    Text(store.tr(df.titleKey))
                                    if filter.dateFilter == df {
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                        }
                    } label: {
                        filterMenuLabel(
                            icon: "calendar",
                            title: store.tr(filter.dateFilter.titleKey),
                            isActive: filter.dateFilter != .all
                        )
                    }
                    .menuStyle(.borderlessButton)
                    .fixedSize()
                    .thingsTooltip("\(store.tr("action_when")): \(store.tr(filter.dateFilter.titleKey))")

                    filterTogglePill(
                        icon: "flag",
                        title: store.tr("search_filter_has_deadline"),
                        isActive: filter.hasDeadlineOnly,
                        helpText: store.tr("search_filter_has_deadline")
                    ) {
                        filter.hasDeadlineOnly.toggle()
                    }

                    filterTogglePill(
                        icon: "checklist",
                        title: store.tr("search_filter_has_checklist"),
                        isActive: filter.hasChecklistOnly,
                        helpText: store.tr("search_filter_has_checklist")
                    ) {
                        filter.hasChecklistOnly.toggle()
                    }

                    if !store.tags.isEmpty {
                        Menu {
                            Button {
                                filter.tagId = nil
                            } label: {
                                HStack {
                                    Text(store.tr("search_filter_all_tags"))
                                    if filter.tagId == nil {
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                            Divider()
                            ForEach(store.tags) { tag in
                                Button {
                                    filter.tagId = tag.id
                                } label: {
                                    HStack {
                                        Text("#\(tag.title)")
                                        if filter.tagId == tag.id {
                                            Image(systemName: "checkmark")
                                        }
                                    }
                                }
                            }
                        } label: {
                            filterMenuLabel(
                                icon: "square.grid.2x2",
                                title: selectedTagTitle,
                                isActive: filter.tagId != nil
                            )
                        }
                        .menuStyle(.borderlessButton)
                        .fixedSize()
                        .thingsTooltip("\(store.tr("action_tags")): \(selectedTagTitle)")
                    }

                    Menu {
                        ForEach(QuickFindEntityScope.allCases) { sc in
                            Button {
                                filter.scope = sc
                            } label: {
                                HStack {
                                    Text(store.tr(sc.titleKey))
                                    if filter.scope == sc {
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                        }
                    } label: {
                        filterMenuLabel(
                            icon: "line.3.horizontal.decrease.circle",
                            title: store.tr(filter.scope.titleKey),
                            isActive: filter.scope != .all
                        )
                    }
                    .menuStyle(.borderlessButton)
                    .fixedSize()
                    .thingsTooltip(store.tr(filter.scope.titleKey))

                    if filter.hasActiveFilters {
                        Button {
                            filter = QuickFindFilter()
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "arrow.counterclockwise")
                                    .font(.system(size: 10.5, weight: .semibold))
                                Text(store.tr("search_filter_reset"))
                                    .font(.system(size: 11.5, weight: .medium))
                                    .lineLimit(1)
                            }
                            .fixedSize(horizontal: true, vertical: false)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 5)
                            .background(
                                Capsule(style: .continuous)
                                    .strokeBorder(Color.primary.opacity(0.18), lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                        .fixedSize(horizontal: true, vertical: false)
                        .thingsTooltip(store.tr("search_filter_reset"))
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
            }
            .onChange(of: filter) { _, _ in
                runSearch()
                syncDefaultSelection()
            }

            Divider()

            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        if shouldShowCreateTaskRow && parsedSyntax.hasAnyMatch {
                            createTaskPaletteRow
                                .id("create_task")
                        }

                        if !matchingCommands.isEmpty {
                            sectionHeader(store.tr("v32_cmd_section_actions"))
                            ForEach(matchingCommands) { cmd in
                                commandPaletteRow(cmd)
                                    .id(cmd.id)
                            }
                        }

                        if shouldShowCreateTaskRow && !parsedSyntax.hasAnyMatch {
                            createTaskPaletteRow
                                .id("create_task")
                        }

                        if !matchingSmartViews.isEmpty {
                            smartViewsSection
                        }

                        if !matchingAreas.isEmpty {
                            areasSection
                        }

                        if !matchedProjects.isEmpty {
                            projectsSection
                        }

                        tasksOrEmptySection
                    }
                    .padding(12)
                }
                .frame(maxHeight: 450)
                .onChange(of: selectedEntryId, initial: false) { _, newId in
                    if let newId {
                        withAnimation(.easeOut(duration: 0.12)) {
                            proxy.scrollTo(newId)
                        }
                    }
                }
            }
        }
        .frame(width: 650)
        .background(ThingsMacPalette.cardSurface)
        .onAppear {
            isSearchFocused = true
            runSearch()
            syncDefaultSelection()
            installArrowKeyMonitor()
        }
        .onDisappear {
            removeArrowKeyMonitor()
        }
    }

    @ViewBuilder
    private var smartViewsSection: some View {
        sectionHeader(store.tr("macos_lists_header"))
        ForEach(matchingSmartViews) { sv in
            let entryId = "view:\(sv.id)"
            Button {
                store.selectedDestination = .smartView(sv)
                dismiss()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: sv.sfSymbol)
                        .foregroundStyle(ThingsMacPalette.color(for: sv))
                        .frame(width: 20)
                    Text(store.tr(sv.titleKey))
                        .font(.system(size: 13.5, weight: .medium))
                    Spacer()
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(paletteRowSelectionBackground(entryId))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .id(entryId)
        }
    }

    @ViewBuilder
    private var areasSection: some View {
        sectionHeader(store.tr("entity_areas"))
        ForEach(matchingAreas) { area in
            let entryId = "area:\(area.id)"
            Button {
                store.selectedDestination = .area(area.id)
                dismiss()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "folder.fill")
                        .foregroundStyle(ThingsMacPalette.areaGreen)
                        .frame(width: 20)
                    Text(area.title)
                        .font(.system(size: 13.5, weight: .medium))
                    Spacer()
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(paletteRowSelectionBackground(entryId))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .id(entryId)
        }
    }

    @ViewBuilder
    private var projectsSection: some View {
        sectionHeader(store.tr("entity_projects"))
        ForEach(matchedProjects) { proj in
            let entryId = "proj:\(proj.id)"
            Button {
                store.selectedDestination = .project(proj.id)
                dismiss()
            } label: {
                HStack(spacing: 10) {
                    ProjectProgressRing(
                        progress: proj.progress,
                        size: 15,
                        isCompleted: proj.status == .completed
                    )
                    .frame(width: 20)
                    Text(proj.title)
                        .font(.system(size: 13.5, weight: .medium))
                        .strikethrough(proj.status != .active)
                        .foregroundStyle(proj.status != .active ? .secondary : .primary)
                    Spacer()
                    if let deadline = proj.deadline {
                        HStack(spacing: 3) {
                            Image(systemName: "flag.fill")
                                .font(.system(size: 9.5))
                            Text(shortDateString(deadline))
                                .font(.system(size: 11, weight: .medium))
                        }
                        .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(paletteRowSelectionBackground(entryId))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .id(entryId)
        }
    }

    @ViewBuilder
    private var tasksOrEmptySection: some View {
        if !matchedTasks.isEmpty {
            sectionHeader(store.tr("entity_todos"))
            ForEach(matchedTasks) { task in
                taskPaletteRow(task)
                    .id("task:\(task.id)")
            }
        } else if (!trimmedQuery.isEmpty || filter.hasActiveFilters)
            && !shouldShowCreateTaskRow
            && matchingCommands.isEmpty
            && matchedProjects.isEmpty
            && matchingSmartViews.isEmpty
            && matchingAreas.isEmpty {
            Text(store.tr("quick_find_no_results"))
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.vertical, 36)
        }
    }

    private func taskPaletteRow(_ task: TaskItem) -> some View {
        let entryId = "task:\(task.id)"
        let doneCount = task.checklistItems.filter(\.isCompleted).count
        let totalChecklistCount = task.checklistItems.count
        let displayTitle = task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title

        return Button {
            jumpToTask(task)
            dismiss()
        } label: {
            HStack(spacing: 10) {
                Image(systemName: task.status == .completed ? "checkmark.square.fill" : "circle")
                    .foregroundStyle(task.status == .completed ? ThingsMacPalette.accentBlue : .secondary)
                    .frame(width: 20)
                VStack(alignment: .leading, spacing: 2) {
                    Text(displayTitle)
                        .font(.system(size: 13.5))
                        .strikethrough(task.status != .open)
                        .foregroundStyle(task.status != .open ? .secondary : .primary)
                        .lineLimit(1)
                    if !task.notes.isEmpty {
                        Text(task.notes)
                            .font(.system(size: 11.5))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
                Spacer()
                HStack(spacing: 6) {
                    if totalChecklistCount > 0 {
                        Text("\(doneCount)/\(totalChecklistCount)")
                            .font(.system(size: 10.5, weight: .medium))
                            .foregroundStyle(.secondary)
                    }
                    if let whenDate = task.whenDate {
                        Text(shortDateString(whenDate))
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                    if let deadline = task.deadline {
                        HStack(spacing: 3) {
                            Image(systemName: "flag.fill")
                                .font(.system(size: 9.5))
                            Text(shortDateString(deadline))
                                .font(.system(size: 11, weight: .medium))
                        }
                        .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                    }
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(paletteRowSelectionBackground(entryId))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var createTaskPaletteRow: some View {
        let parsed = parsedSyntax
        let isSelected = selectedEntryId == "create_task"

        return Button {
            createTaskAndDismiss()
        } label: {
            VStack(alignment: .leading, spacing: parsed.hasAnyMatch ? 6 : 3) {
                HStack(spacing: 9) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .frame(width: 20)

                    Text("+ \(store.trf("v32_cmd_create_task", parsed.cleanTitle))")
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)

                    Spacer(minLength: 8)

                    Text("↩")
                        .font(.system(size: 10.5, weight: .bold, design: .monospaced))
                        .foregroundStyle(isSelected ? ThingsMacPalette.accentBlue : .secondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(
                            RoundedRectangle(cornerRadius: 4.5, style: .continuous)
                                .fill(Color.primary.opacity(0.06))
                        )
                }

                if parsed.hasAnyMatch {
                    SmartSyntaxPreviewPillsView(
                        store: store,
                        parsed: parsed,
                        showDetectedLabel: true
                    )
                    .padding(.leading, 29)
                } else {
                    Text(store.tr("v32_smart_syntax_hint"))
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                        .padding(.leading, 29)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(
                        isSelected
                            ? ThingsMacPalette.accentBlue.opacity(0.14)
                            : ThingsMacPalette.accentBlue.opacity(0.06)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                            .strokeBorder(
                                isSelected
                                    ? ThingsMacPalette.accentBlue.opacity(0.45)
                                    : ThingsMacPalette.accentBlue.opacity(0.18),
                                lineWidth: 0.85
                            )
                    )
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func commandPaletteRow(_ cmd: PaletteCommandItem) -> some View {
        Button {
            cmd.action()
        } label: {
            HStack(spacing: 10) {
                Image(systemName: cmd.icon)
                    .font(.system(size: 13.5, weight: .semibold))
                    .foregroundStyle(cmd.tint)
                    .frame(width: 20)

                Text(cmd.title)
                    .font(.system(size: 13.5, weight: .medium))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer()

                if let shortcut = cmd.shortcut {
                    Text(shortcut)
                        .font(.system(size: 10.5, weight: .semibold, design: .monospaced))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(
                            RoundedRectangle(cornerRadius: 4.5, style: .continuous)
                                .fill(Color.primary.opacity(0.06))
                        )
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6.5)
            .background(paletteRowSelectionBackground(cmd.id))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func paletteRowSelectionBackground(_ entryId: String) -> some View {
        RoundedRectangle(cornerRadius: 8, style: .continuous)
            .fill(selectedEntryId == entryId ? ThingsMacPalette.accentBlue.opacity(0.13) : Color.clear)
    }

    private func filterTogglePill(
        icon: String,
        title: String,
        isActive: Bool,
        helpText: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 4.5) {
                Image(systemName: icon)
                    .font(.system(size: 11, weight: .medium))
                Text(title)
                    .font(.system(size: 11.5, weight: isActive ? .semibold : .medium))
                    .lineLimit(1)
            }
            .fixedSize(horizontal: true, vertical: false)
            .foregroundStyle(isActive ? ThingsMacPalette.accentBlue : .secondary)
            .padding(.horizontal, 9.5)
            .padding(.vertical, 5)
            .background(
                Capsule(style: .continuous)
                    .fill(isActive ? ThingsMacPalette.accentBlue.opacity(0.13) : Color.primary.opacity(0.045))
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(
                        isActive ? ThingsMacPalette.accentBlue.opacity(0.45) : Color.primary.opacity(0.10),
                        lineWidth: 0.85
                    )
            )
        }
        .buttonStyle(.plain)
        .fixedSize(horizontal: true, vertical: false)
        .thingsTooltip(helpText)
    }

    private func filterMenuLabel(icon: String, title: String, isActive: Bool) -> some View {
        HStack(spacing: 4.5) {
            Image(systemName: icon)
                .font(.system(size: 11, weight: .medium))
            Text(title)
                .font(.system(size: 11.5, weight: isActive ? .semibold : .medium))
                .lineLimit(1)
            Image(systemName: "chevron.down")
                .font(.system(size: 9, weight: .semibold))
        }
        .fixedSize(horizontal: true, vertical: false)
        .foregroundStyle(isActive ? ThingsMacPalette.accentBlue : .secondary)
        .padding(.horizontal, 9.5)
        .padding(.vertical, 5)
        .background(
            Capsule(style: .continuous)
                .fill(isActive ? ThingsMacPalette.accentBlue.opacity(0.13) : Color.primary.opacity(0.045))
        )
        .overlay(
            Capsule(style: .continuous)
                .strokeBorder(
                    isActive ? ThingsMacPalette.accentBlue.opacity(0.45) : Color.primary.opacity(0.10),
                    lineWidth: 0.85
                )
        )
    }

    private func shortDateString(_ epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateFormat = "d MMM"
        return formatter.string(from: date)
    }

    private func sectionHeader(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.system(size: 10.5, weight: .semibold))
            .foregroundStyle(.tertiary)
            .padding(.horizontal, 10)
    }

    private func runSearch() {
        guard !isCommandOnlyMode else {
            matchedTasks = []
            matchedProjects = []
            return
        }
        guard !trimmedQuery.isEmpty || filter.hasActiveFilters else {
            matchedTasks = []
            matchedProjects = []
            return
        }
        if let res = try? store.database.searchQuickFind(rawQuery: trimmedQuery, filter: filter) {
            matchedTasks = res.tasks
            matchedProjects = res.projects
        }
    }

    private func syncDefaultSelection() {
        let ids = orderedNavigableEntryIds
        guard !ids.isEmpty else {
            selectedEntryId = nil
            return
        }
        if parsedSyntax.hasAnyMatch && shouldShowCreateTaskRow {
            selectedEntryId = "create_task"
            return
        }
        if isCommandOnlyMode, let firstCmd = matchingCommands.first {
            selectedEntryId = firstCmd.id
            return
        }
        if !trimmedQuery.isEmpty {
            if let firstTask = matchedTasks.first {
                selectedEntryId = "task:\(firstTask.id)"
                return
            }
            if let firstProj = matchedProjects.first {
                selectedEntryId = "proj:\(firstProj.id)"
                return
            }
            if let firstCmd = matchingCommands.first {
                selectedEntryId = firstCmd.id
                return
            }
            if let firstView = matchingSmartViews.first {
                selectedEntryId = "view:\(firstView.id)"
                return
            }
            if let firstArea = matchingAreas.first {
                selectedEntryId = "area:\(firstArea.id)"
                return
            }
        }
        selectedEntryId = ids.first
    }

    private func moveSelection(delta: Int) {
        let ids = orderedNavigableEntryIds
        guard !ids.isEmpty else { return }
        if let current = selectedEntryId, let idx = ids.firstIndex(of: current) {
            let nextIdx = (idx + delta + ids.count) % ids.count
            selectedEntryId = ids[nextIdx]
        } else {
            selectedEntryId = delta >= 0 ? ids.first : ids.last
        }
    }

    private func installArrowKeyMonitor() {
        guard keyMonitor == nil else { return }
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            let mods = event.modifierFlags.intersection([.command, .option, .control])
            guard mods.isEmpty else { return event }
            if event.keyCode == 125 { // Down arrow
                moveSelection(delta: 1)
                return nil
            } else if event.keyCode == 126 { // Up arrow
                moveSelection(delta: -1)
                return nil
            }
            return event
        }
    }

    private func removeArrowKeyMonitor() {
        if let monitor = keyMonitor {
            NSEvent.removeMonitor(monitor)
            keyMonitor = nil
        }
    }

    private func createTaskAndDismiss() {
        guard shouldShowCreateTaskRow else { return }
        if store.createTaskFromSmartSyntax(query) != nil {
            dismiss()
        }
    }

    private func executeSelectedOrFirstResult() {
        if let sel = selectedEntryId {
            if sel == "create_task" {
                createTaskAndDismiss()
                return
            }
            if let cmd = matchingCommands.first(where: { $0.id == sel }) {
                cmd.action()
                return
            }
            if sel.hasPrefix("view:"),
               let sv = matchingSmartViews.first(where: { "view:\($0.id)" == sel }) {
                store.selectedDestination = .smartView(sv)
                dismiss()
                return
            }
            if sel.hasPrefix("area:"),
               let area = matchingAreas.first(where: { "area:\($0.id)" == sel }) {
                store.selectedDestination = .area(area.id)
                dismiss()
                return
            }
            if sel.hasPrefix("proj:"),
               let proj = matchedProjects.first(where: { "proj:\($0.id)" == sel }) {
                store.selectedDestination = .project(proj.id)
                dismiss()
                return
            }
            if sel.hasPrefix("task:"),
               let task = matchedTasks.first(where: { "task:\($0.id)" == sel }) {
                jumpToTask(task)
                dismiss()
                return
            }
        }

        if let firstCmd = matchingCommands.first, isCommandOnlyMode {
            firstCmd.action()
        } else if parsedSyntax.hasAnyMatch && shouldShowCreateTaskRow {
            createTaskAndDismiss()
        } else if let firstTask = matchedTasks.first {
            jumpToTask(firstTask)
            dismiss()
        } else if let firstProj = matchedProjects.first {
            store.selectedDestination = .project(firstProj.id)
            dismiss()
        } else if let firstCmd = matchingCommands.first, !trimmedQuery.isEmpty {
            firstCmd.action()
        } else if let firstView = matchingSmartViews.first, !trimmedQuery.isEmpty {
            store.selectedDestination = .smartView(firstView)
            dismiss()
        } else if let firstArea = matchingAreas.first, !trimmedQuery.isEmpty {
            store.selectedDestination = .area(firstArea.id)
            dismiss()
        } else if shouldShowCreateTaskRow {
            createTaskAndDismiss()
        } else if let firstCmd = matchingCommands.first {
            firstCmd.action()
        }
    }

    private func jumpToTask(_ task: TaskItem) {
        if task.status == .completed || task.status == .canceled {
            store.selectedDestination = .smartView(.logbook)
        } else if let pid = task.projectId {
            store.selectedDestination = .project(pid)
        } else if task.scheduleState == .today || task.scheduleState == .thisEvening {
            store.selectedDestination = .smartView(.today)
        } else if task.scheduleState == .upcoming {
            store.selectedDestination = .smartView(.upcoming)
        } else if task.scheduleState == .someday {
            store.selectedDestination = .smartView(.someday)
        } else if task.scheduleState == .anytime {
            store.selectedDestination = .smartView(.anytime)
        } else {
            store.selectedDestination = .smartView(.inbox)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            store.selectedTaskIds = [task.id]
            store.expandedTaskId = task.id
        }
    }
}
