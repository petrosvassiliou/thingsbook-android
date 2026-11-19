import SwiftUI
import ThingsbookCore

public struct QuickFindOverlay: View {
    @ObservedObject var store: ThingsbookStore
    @Environment(\.dismiss) private var dismiss

    @State private var query = ""
    @State private var filter = QuickFindFilter()
    @State private var matchedTasks: [TaskItem] = []
    @State private var matchedProjects: [Project] = []
    @FocusState private var isSearchFocused: Bool

    public init(store: ThingsbookStore) {
        self.store = store
    }

    private var matchingSmartViews: [SmartView] {
        guard filter.shouldShowSmartViewsAndAreas(forQuery: query) else { return [] }
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return SmartView.allCases }
        return SmartView.allCases.filter { store.tr($0.titleKey).lowercased().contains(q) }
    }

    private var matchingAreas: [Area] {
        guard filter.shouldShowSmartViewsAndAreas(forQuery: query) else { return [] }
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return store.areas }
        return store.areas.filter { $0.title.lowercased().contains(q) }
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
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                TextField(store.tr("quick_find_placeholder"), text: $query)
                    .textFieldStyle(.plain)
                    .font(.system(size: 16))
                    .focused($isSearchFocused)
                    .onChange(of: query) { _, _ in
                        runSearch()
                    }
                    .onSubmit {
                        selectFirstResult()
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
            }

            Divider()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if !matchingSmartViews.isEmpty {
                        sectionHeader(store.tr("macos_lists_header"))
                        ForEach(matchingSmartViews) { sv in
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
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if !matchingAreas.isEmpty {
                        sectionHeader(store.tr("entity_areas"))
                        ForEach(matchingAreas) { area in
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
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if !matchedProjects.isEmpty {
                        sectionHeader(store.tr("entity_projects"))
                        ForEach(matchedProjects) { proj in
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
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if !matchedTasks.isEmpty {
                        sectionHeader(store.tr("entity_todos"))
                        ForEach(matchedTasks) { task in
                            Button {
                                jumpToTask(task)
                                dismiss()
                            } label: {
                                HStack(spacing: 10) {
                                    Image(systemName: task.status == .completed ? "checkmark.square.fill" : "circle")
                                        .foregroundStyle(task.status == .completed ? ThingsMacPalette.accentBlue : .secondary)
                                        .frame(width: 20)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
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
                                        if !task.checklistItems.isEmpty {
                                            let done = task.checklistItems.filter(\.isCompleted).count
                                            Text("\(done)/\(task.checklistItems.count)")
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
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    } else if (!query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || filter.hasActiveFilters)
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
                .padding(12)
            }
            .frame(maxHeight: 420)
        }
        .frame(width: 640)
        .background(ThingsMacPalette.cardSurface)
        .onAppear {
            isSearchFocused = true
            runSearch()
        }
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
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty || filter.hasActiveFilters else {
            matchedTasks = []
            matchedProjects = []
            return
        }
        if let res = try? store.database.searchQuickFind(rawQuery: trimmed, filter: filter) {
            matchedTasks = res.tasks
            matchedProjects = res.projects
        }
    }

    private func selectFirstResult() {
        if let firstTask = matchedTasks.first {
            jumpToTask(firstTask)
            dismiss()
        } else if let firstProj = matchedProjects.first {
            store.selectedDestination = .project(firstProj.id)
            dismiss()
        } else if let firstView = matchingSmartViews.first {
            store.selectedDestination = .smartView(firstView)
            dismiss()
        } else if let firstArea = matchingAreas.first {
            store.selectedDestination = .area(firstArea.id)
            dismiss()
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
