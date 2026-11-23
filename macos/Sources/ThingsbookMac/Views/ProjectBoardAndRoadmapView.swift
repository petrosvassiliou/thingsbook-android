import SwiftUI
import AppKit
import CoreGraphics
import UniformTypeIdentifiers
import ThingsbookCore

// MARK: - Soft Bento 3-Mode Project Switcher (List | Board | Roadmap)

struct ProjectModeSegmentedSwitcher: View {
    @ObservedObject var store: ThingsbookStore
    let project: Project

    private var activeMode: ProjectViewMode {
        ProjectViewMode.normalized(project.viewMode)
    }

    private func label(for mode: ProjectViewMode) -> String {
        switch mode {
        case .list:
            return store.tr("v30_project_mode_list")
        case .board:
            return store.tr("v30_project_mode_board")
        case .roadmap:
            return store.tr("v30_project_mode_roadmap")
        }
    }

    private func icon(for mode: ProjectViewMode) -> String {
        switch mode {
        case .list:
            return "list.bullet"
        case .board:
            return "rectangle.split.3x1"
        case .roadmap:
            return "calendar.day.timeline.left"
        }
    }

    var body: some View {
        HStack(spacing: 3) {
            ForEach(ProjectViewMode.allCases) { mode in
                let isSelected = activeMode == mode
                Button {
                    withAnimation(.spring(response: 0.24, dampingFraction: 0.84)) {
                        store.setProjectViewMode(project.id, mode: mode)
                    }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: icon(for: mode))
                            .font(.system(size: 11, weight: .semibold))
                        Text(label(for: mode))
                            .font(.system(size: 12, weight: isSelected ? .semibold : .medium))
                            .lineLimit(1)
                    }
                    .fixedSize(horizontal: true, vertical: false)
                    .foregroundStyle(isSelected ? ThingsMacPalette.accentBlue : .secondary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .fill(isSelected ? ThingsMacPalette.cardSurface : Color.clear)
                            .shadow(
                                color: Color.black.opacity(isSelected ? 0.07 : 0.0),
                                radius: 4,
                                y: 1
                            )
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .strokeBorder(
                                isSelected ? ThingsMacPalette.accentBlue.opacity(0.32) : Color.clear,
                                lineWidth: 0.85
                            )
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 7.5, style: .continuous))
                }
                .buttonStyle(.plain)
                .thingsTooltip(label(for: mode))
            }
        }
        .padding(3)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer)
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.8)
                )
        )
        .fixedSize(horizontal: true, vertical: false)
    }
}

// MARK: - Phase Start & End Date Pills

struct PhaseStartDatePillButton: View {
    @ObservedObject var store: ThingsbookStore
    let heading: Heading
    var compact: Bool = false

    @State private var isPopoverOpen = false
    @State private var isHovering = false

    private var formattedStartDate: String? {
        guard let startMs = heading.startDate else { return nil }
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.setLocalizedDateFormatFromTemplate("dMMM")
        return df.string(from: DateEpochConverter.date(fromMillis: startMs))
    }

    @ViewBuilder
    var body: some View {
        let hasStartDate = heading.startDate != nil
        let isFullScreen = store.isTimelineFullScreen

        if isFullScreen && !hasStartDate {
            EmptyView()
        } else {
            Button {
                guard !store.isTimelineFullScreen else { return }
                isPopoverOpen = true
            } label: {
                HStack(spacing: compact ? 3 : 4) {
                    Image(systemName: hasStartDate ? "calendar" : "calendar.badge.plus")
                        .font(.system(size: compact ? 9 : 10, weight: .semibold))
                    if let formattedStartDate {
                        Text(store.trf("v30_phase_starts_on", formattedStartDate))
                            .font(.system(size: compact ? 10 : 11, weight: .semibold))
                            .lineLimit(1)
                    } else {
                        Text(store.tr("v30_phase_set_start_date"))
                            .font(.system(size: compact ? 10 : 11, weight: .medium))
                            .lineLimit(1)
                    }
                }
                .foregroundStyle(hasStartDate ? ThingsMacPalette.accentBlue : ((!isFullScreen && isHovering) ? ThingsMacPalette.accentBlue : .secondary))
                .padding(.horizontal, compact ? 6.5 : 8.5)
                .padding(.vertical, compact ? 2 : 3)
                .background(
                    Capsule(style: .continuous)
                        .fill(
                            hasStartDate
                                ? ThingsMacPalette.inboxBlue.opacity((!isFullScreen && isHovering) ? 0.24 : 0.15)
                                : Color.primary.opacity((!isFullScreen && isHovering) ? 0.08 : 0.045)
                        )
                )
                .contentShape(Capsule(style: .continuous))
                .fixedSize(horizontal: true, vertical: false)
            }
            .buttonStyle(.plain)
            .fixedSize(horizontal: true, vertical: false)
            .disabled(isFullScreen)
            .allowsHitTesting(!isFullScreen)
            .onHover { hovering in
                isHovering = hovering && !store.isTimelineFullScreen
            }
            .onChange(of: store.isTimelineFullScreen) { _, fullScreen in
                if fullScreen {
                    isPopoverOpen = false
                }
            }
            .thingsTooltip(isFullScreen ? "" : store.tr("v30_phase_set_start_date"))
            .popover(isPresented: $isPopoverOpen) {
                DeadlinePopoverView(
                    store: store,
                    currentDeadlineMillis: heading.startDate,
                    customTitle: store.tr("v30_phase_set_start_date"),
                    customClearTitle: store.tr("v30_phase_clear_start_date"),
                    accentTint: ThingsMacPalette.accentBlue
                ) { newStartMs in
                    if let newStartMs, let existingEnd = heading.endDate, newStartMs > existingEnd {
                        store.updateHeadingDates(heading.id, startDate: newStartMs, endDate: newStartMs)
                    } else {
                        store.updateHeadingStartDate(heading.id, startDate: newStartMs)
                    }
                }
            }
        }
    }
}

struct PhaseEndDatePillButton: View {
    @ObservedObject var store: ThingsbookStore
    let heading: Heading
    let hasOpenTasks: Bool
    var compact: Bool = false

    @State private var isPopoverOpen = false
    @State private var isHovering = false

    private var formattedEndDate: String? {
        guard let endMs = heading.endDate else { return nil }
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.setLocalizedDateFormatFromTemplate("dMMM")
        return df.string(from: DateEpochConverter.date(fromMillis: endMs))
    }

    private var isOverdue: Bool {
        guard hasOpenTasks, let endMs = heading.endDate else { return false }
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        return DateEpochConverter.epochDay(forMillis: endMs) < todayEpochDay
    }

    @ViewBuilder
    var body: some View {
        let activeTint = isOverdue ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue
        let hasEndDate = heading.endDate != nil
        let isFullScreen = store.isTimelineFullScreen

        if isFullScreen && !hasEndDate {
            EmptyView()
        } else {
            Button {
                guard !store.isTimelineFullScreen else { return }
                isPopoverOpen = true
            } label: {
                HStack(spacing: compact ? 3 : 4) {
                    Image(systemName: hasEndDate ? "calendar.badge.clock" : "calendar.badge.plus")
                        .font(.system(size: compact ? 9 : 10, weight: .semibold))
                    if let formattedEndDate {
                        Text(store.trf("phase_ends_on", formattedEndDate))
                            .font(.system(size: compact ? 10 : 11, weight: .semibold))
                            .lineLimit(1)
                    } else {
                        Text(store.tr("phase_set_end_date"))
                            .font(.system(size: compact ? 10 : 11, weight: .medium))
                            .lineLimit(1)
                    }
                }
                .foregroundStyle(hasEndDate ? activeTint : ((!isFullScreen && isHovering) ? ThingsMacPalette.accentBlue : .secondary))
                .padding(.horizontal, compact ? 6.5 : 8.5)
                .padding(.vertical, compact ? 2 : 3)
                .background(
                    Capsule(style: .continuous)
                        .fill(
                            hasEndDate
                                ? activeTint.opacity((!isFullScreen && isHovering) ? 0.18 : 0.12)
                                : Color.primary.opacity((!isFullScreen && isHovering) ? 0.08 : 0.045)
                        )
                )
                .contentShape(Capsule(style: .continuous))
                .fixedSize(horizontal: true, vertical: false)
            }
            .buttonStyle(.plain)
            .fixedSize(horizontal: true, vertical: false)
            .disabled(isFullScreen)
            .allowsHitTesting(!isFullScreen)
            .onHover { hovering in
                isHovering = hovering && !store.isTimelineFullScreen
            }
            .onChange(of: store.isTimelineFullScreen) { _, fullScreen in
                if fullScreen {
                    isPopoverOpen = false
                }
            }
            .thingsTooltip(isFullScreen ? "" : store.tr("phase_set_end_date"))
            .popover(isPresented: $isPopoverOpen) {
                DeadlinePopoverView(
                    store: store,
                    currentDeadlineMillis: heading.endDate,
                    customTitle: store.tr("phase_set_end_date"),
                    customClearTitle: store.tr("phase_clear_end_date"),
                    accentTint: ThingsMacPalette.accentBlue
                ) { newEndMs in
                    if let newEndMs, let existingStart = heading.startDate, newEndMs < existingStart {
                        store.updateHeadingDates(heading.id, startDate: newEndMs, endDate: newEndMs)
                    } else {
                        store.updateHeadingEndDate(heading.id, endDate: newEndMs)
                    }
                }
            }
        }
    }
}

// MARK: - Mode 2: ProjectBoardWorkspaceView (Soft Bento Kanban)

public struct ProjectBoardWorkspaceView: View {
    @ObservedObject var store: ThingsbookStore
    public let project: Project
    public let openTasks: [TaskItem]
    public let headings: [Heading]

    private static let generalColumnRenameSentinel = "__general__"

    @State private var isCreatingPhase = false
    @State private var newPhaseTitle = ""
    @State private var renamingHeadingId: String? = nil
    @State private var renameText = ""
    @FocusState private var isBoardRenameFocused: Bool

    public init(
        store: ThingsbookStore,
        project: Project,
        openTasks: [TaskItem],
        headings: [Heading]
    ) {
        self.store = store
        self.project = project
        self.openTasks = openTasks
        self.headings = headings
    }

    private var activeHeadings: [Heading] {
        headings
            .filter { !$0.isDeleted && !$0.isArchived }
            .sorted {
                if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                return $0.id < $1.id
            }
    }

    private var validHeadingIds: Set<String> {
        Set(activeHeadings.map(\.id))
    }

    private var unheadedTasks: [TaskItem] {
        TaskViewSorter.sort(
            openTasks.filter { task in
                guard let hid = task.headingId else { return true }
                return !validHeadingIds.contains(hid)
            },
            mode: store.preferences.viewSortMode
        )
    }

    public var body: some View {
        let currentHeadings = activeHeadings
        let unheaded = unheadedTasks
        let showGeneralColumn = !unheaded.isEmpty || currentHeadings.isEmpty

        ScrollView(.horizontal, showsIndicators: true) {
            HStack(alignment: .top, spacing: 14) {
                if showGeneralColumn {
                    kanbanColumn(
                        heading: nil,
                        title: store.tr("v30_board_ungrouped_column"),
                        tasks: unheaded
                    )
                }

                ForEach(currentHeadings) { heading in
                    let columnTasks = TaskViewSorter.sort(
                        openTasks.filter { $0.headingId == heading.id },
                        mode: store.preferences.viewSortMode
                    )
                    kanbanColumn(
                        heading: heading,
                        title: heading.title,
                        tasks: columnTasks
                    )
                }

                addPhaseColumnCard
            }
            .padding(.vertical, 4)
            .padding(.trailing, 8)
        }
    }

    private func kanbanColumn(
        heading: Heading?,
        title: String,
        tasks: [TaskItem]
    ) -> some View {
        let rawOpenCount: Int = {
            if let heading {
                return store.allOpenTasks.filter { $0.projectId == project.id && $0.headingId == heading.id }.count
            }
            return store.allOpenTasks.filter { task in
                guard task.projectId == project.id else { return false }
                guard let hid = task.headingId else { return true }
                return !validHeadingIds.contains(hid)
            }.count
        }()
        let doneCount: Int = {
            if let heading {
                return store.completedTasksForProject(project.id).filter { $0.headingId == heading.id }.count
            }
            return store.completedTasksForProject(project.id).filter { task in
                guard let hid = task.headingId else { return true }
                return !validHeadingIds.contains(hid)
            }.count
        }()
        let totalCount = rawOpenCount + doneCount
        let progress = totalCount > 0 ? Double(doneCount) / Double(totalCount) : 0.0
        let targetRenameId = heading?.id ?? Self.generalColumnRenameSentinel
        let isRenaming = renamingHeadingId == targetRenameId

        return VStack(alignment: .leading, spacing: 10) {
            // Column Header
            VStack(alignment: .leading, spacing: 7) {
                HStack(alignment: .top, spacing: 8) {
                    Capsule(style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .frame(width: 4, height: 18)
                        .padding(.top, 1)

                    if isRenaming {
                        TextField(store.tr("entity_new_heading"), text: $renameText)
                            .textFieldStyle(.roundedBorder)
                            .font(.system(size: 13, weight: .semibold))
                            .focused($isBoardRenameFocused)
                            .onSubmit {
                                if let heading {
                                    commitRename(heading)
                                } else {
                                    commitGeneralRename()
                                }
                            }
                        Button(store.tr("action_save")) {
                            if let heading {
                                commitRename(heading)
                            } else {
                                commitGeneralRename()
                            }
                        }
                        .controlSize(.mini)
                        .buttonStyle(.borderedProminent)
                        Button(store.tr("action_cancel")) {
                            renamingHeadingId = nil
                        }
                        .controlSize(.mini)
                    } else {
                        Text(title)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.primary)
                            .lineLimit(nil)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                            .layoutPriority(1)

                        Text("\(tasks.count)")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 6.5)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(Color.primary.opacity(0.06)))
                            .fixedSize()
                            .padding(.top, 1)
                    }

                    Spacer(minLength: 4)

                    HStack(spacing: 6) {
                        if totalCount > 0 && !isRenaming {
                            ProjectProgressRing(
                                progress: progress,
                                size: 13,
                                tint: ThingsMacPalette.accentBlue,
                                isCompleted: rawOpenCount == 0 && doneCount > 0
                            )
                        }

                        Button {
                            store.createNewTaskInCurrentContext(preferredHeadingId: heading?.id)
                        } label: {
                            Image(systemName: "plus")
                                .font(.system(size: 11.5, weight: .semibold))
                                .foregroundStyle(.secondary)
                                .frame(width: 22, height: 22)
                                .background(
                                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                                        .fill(Color.primary.opacity(0.045))
                                )
                        }
                        .buttonStyle(.plain)
                        .thingsTooltip(store.tr("heading_add_task"))

                        if let heading {
                            Menu {
                                Button(store.tr("macos_rename_heading")) {
                                    renameText = heading.title
                                    renamingHeadingId = heading.id
                                    DispatchQueue.main.async {
                                        isBoardRenameFocused = true
                                    }
                                }
                                Divider()
                                Button(role: .destructive) {
                                    try? store.database.deleteHeading(heading.id)
                                } label: {
                                    Text(store.tr("action_delete"))
                                }
                            } label: {
                                Image(systemName: "ellipsis")
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .foregroundStyle(.secondary)
                                    .frame(width: 20, height: 22)
                            }
                            .menuStyle(.borderlessButton)
                            .menuIndicator(.hidden)
                            .fixedSize()
                        } else if !tasks.isEmpty {
                            Menu {
                                Button(store.tr("macos_rename_heading")) {
                                    renameText = store.tr("v30_board_ungrouped_column")
                                    renamingHeadingId = Self.generalColumnRenameSentinel
                                    DispatchQueue.main.async {
                                        isBoardRenameFocused = true
                                    }
                                }
                                Divider()
                                Button(role: .destructive) {
                                    if renamingHeadingId == Self.generalColumnRenameSentinel {
                                        renamingHeadingId = nil
                                    }
                                    store.deleteTasks(Set(tasks.map(\.id)))
                                } label: {
                                    Text(store.tr("action_delete"))
                                }
                            } label: {
                                Image(systemName: "ellipsis")
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .foregroundStyle(.secondary)
                                    .frame(width: 20, height: 22)
                            }
                            .menuStyle(.borderlessButton)
                            .menuIndicator(.hidden)
                            .fixedSize()
                        }
                    }
                    .fixedSize()
                }

                if let heading {
                    ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 5) {
                        PhaseStartDatePillButton(store: store, heading: heading)
                        PhaseEndDatePillButton(
                            store: store,
                            heading: heading,
                            hasOpenTasks: rawOpenCount > 0
                        )
                    }
                }
            }
            .padding(.horizontal, 4)

            // Task Cards Stack
            VStack(spacing: 8) {
                ForEach(tasks) { task in
                    KanbanTaskCardView(
                        store: store,
                        project: project,
                        columnHeadingId: heading?.id,
                        task: task
                    ) {
                        store.openTaskModal(task.id)
                    }
                }
            }
            .frame(maxWidth: .infinity, minHeight: 36, alignment: .top)

            // Column Footer (+ New To-Do)
            Button {
                store.createNewTaskInCurrentContext(preferredHeadingId: heading?.id)
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "plus")
                        .font(.system(size: 11, weight: .semibold))
                    Text(store.tr("entity_new_todo"))
                        .font(.system(size: 12, weight: .semibold))
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer()
                }
                .foregroundStyle(.secondary)
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(Color.primary.opacity(0.035))
                )
                .contentShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
            }
            .buttonStyle(.plain)
            .thingsTooltip(store.tr("entity_new_todo"))
        }
        .padding(12)
        .frame(width: 300, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: 15, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer.opacity(0.84))
                .overlay(
                    RoundedRectangle(cornerRadius: 15, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.075), lineWidth: 0.85)
                )
        )
        .thingsDropTarget(cornerRadius: 15) { payloads in
            store.dropTasksIntoProjectSection(
                payloads,
                projectId: project.id,
                headingId: heading?.id,
                beforeTaskId: nil
            )
        }
    }

    private var addPhaseColumnCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            if isCreatingPhase {
                Text(store.tr("v30_board_add_phase"))
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.primary)

                TextField(store.tr("entity_new_heading"), text: $newPhaseTitle)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit {
                        submitNewPhase()
                    }

                HStack(spacing: 8) {
                    Button(store.tr("macos_action_add")) {
                        submitNewPhase()
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)

                    Button(store.tr("action_cancel")) {
                        isCreatingPhase = false
                        newPhaseTitle = ""
                    }
                    .controlSize(.small)
                }
            } else {
                Button {
                    isCreatingPhase = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                        Text(store.tr("v30_board_add_phase"))
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.primary)
                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(10)
        .frame(width: 268, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer.opacity(0.52))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(
                            Color.primary.opacity(0.11),
                            style: StrokeStyle(lineWidth: 1.0, dash: [5, 4])
                        )
                )
        )
    }

    private func submitNewPhase() {
        let clean = newPhaseTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        _ = try? store.database.createHeading(projectId: project.id, title: clean)
        newPhaseTitle = ""
        isCreatingPhase = false
    }

    private func commitRename(_ heading: Heading) {
        let clean = renameText.trimmingCharacters(in: .whitespacesAndNewlines)
        defer { renamingHeadingId = nil }
        guard !clean.isEmpty else { return }
        var copy = heading
        copy.title = clean
        try? store.database.updateHeading(copy)
    }

    private func commitGeneralRename() {
        let clean = renameText.trimmingCharacters(in: .whitespacesAndNewlines)
        defer { renamingHeadingId = nil }
        guard !clean.isEmpty else { return }
        _ = store.promoteUngroupedTasksToHeading(projectId: project.id, title: clean)
    }
}

private struct KanbanTaskCardView: View {
    @ObservedObject var store: ThingsbookStore
    let project: Project
    let columnHeadingId: String?
    let task: TaskItem
    let onOpenDetail: () -> Void

    @State private var isHovering = false
    @State private var isDependenciesPopoverOpen = false
    @State private var isWhenPopoverOpen = false
    @State private var isDeadlinePopoverOpen = false
    @State private var isMovePopoverOpen = false

    private var isSelected: Bool {
        store.selectedTaskIds.contains(task.id)
    }

    private var latestTask: TaskItem {
        store.taskIncludingTrash(byId: task.id) ?? task
    }

    var body: some View {
        let isBlocked = store.isTaskActivelyBlocked(task)

        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 9) {
                ThingsCheckboxButton(
                    status: task.status,
                    isSomeday: task.scheduleState == .someday,
                    isMilestone: task.isMilestone,
                    helpText: store.tr("macos_checkbox_help"),
                    onToggleComplete: { store.toggleTaskCompletion(latestTask) },
                    onCancelTask: { store.cancelTask(latestTask) }
                )
                .padding(.top, 1)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 5) {
                        if task.isPinned {
                            Image(systemName: "pin.fill")
                                .font(.system(size: 9.5, weight: .semibold))
                                .foregroundStyle(ThingsMacPalette.todayYellow)
                                .thingsTooltip(store.tr("v25_pinned_badge"))
                        }

                        Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                            .font(.system(size: 13.5, weight: .medium))
                            .foregroundStyle(task.title.isEmpty ? .secondary : .primary)
                            .lineLimit(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    if !task.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Text(task.notes.replacingOccurrences(of: "\n", with: " "))
                            .font(.system(size: 11.5))
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                }

                Spacer(minLength: 0)

                Button(action: onOpenDetail) {
                    Image(systemName: "slider.horizontal.3")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .frame(width: 20, height: 20)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("action_more_options"))
                .opacity(isHovering ? 1.0 : 0.0)
                .allowsHitTesting(isHovering)
            }

            if hasAnyMetadataBadges {
                ThingsFlowLayout(horizontalSpacing: 5, verticalSpacing: 5) {
                    if task.isMilestone {
                        HStack(spacing: 3) {
                            Text("◆ \(store.tr("v30_milestone_label"))")
                                .font(.system(size: 10.5, weight: .bold))
                                .lineLimit(1)
                        }
                        .foregroundStyle(ThingsMacPalette.milestoneViolet)
                        .padding(.horizontal, 6.5)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(ThingsMacPalette.milestoneViolet.opacity(0.14)))
                    }

                    if let blockedText = store.blockedByBadgeText(for: task) {
                        Button {
                            isDependenciesPopoverOpen = true
                        } label: {
                            HStack(spacing: 3) {
                                Image(systemName: "hourglass")
                                    .font(.system(size: 9.5, weight: .semibold))
                                Text(blockedText)
                                    .font(.system(size: 10.5, weight: .semibold))
                                    .lineLimit(1)
                            }
                            .foregroundStyle(ThingsMacPalette.somedayAmber)
                            .padding(.horizontal, 6.5)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(ThingsMacPalette.somedayAmber.opacity(0.14)))
                        }
                        .buttonStyle(.plain)
                    }

                    if let effort = task.resolvedEffort {
                        let tint = effortColor(effort)
                        HStack(spacing: 3) {
                            Image(systemName: effort.sfSymbol)
                                .font(.system(size: 9.5, weight: .semibold))
                            Text(effortTitle(effort))
                                .font(.system(size: 10.5, weight: .semibold))
                                .lineLimit(1)
                        }
                        .foregroundStyle(tint)
                        .padding(.horizontal, 6.5)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(tint.opacity(0.12)))
                    }

                    if let durText = task.formattedTimeblockPill {
                        HStack(spacing: 3) {
                            Image(systemName: task.scheduledStartMinutes != nil ? "clock.badge.checkmark" : "timer")
                                .font(.system(size: 9.5, weight: .semibold))
                            Text(durText)
                                .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                                .lineLimit(1)
                        }
                        .foregroundStyle(ThingsMacPalette.anytimeTeal)
                        .padding(.horizontal, 6.5)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(ThingsMacPalette.anytimeTeal.opacity(0.12)))
                    }

                    if let whenText = whenBadgeText {
                        HStack(spacing: 3) {
                            Image(systemName: whenBadgeIcon)
                                .font(.system(size: 9.5, weight: .semibold))
                            Text(whenText)
                                .font(.system(size: 10.5, weight: .semibold))
                                .lineLimit(1)
                        }
                        .foregroundStyle(whenBadgeTint)
                        .padding(.horizontal, 6.5)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(whenBadgeTint.opacity(0.12)))
                    }

                    if let dl = task.deadline {
                        let dlInfo = deadlineBadgeInfo(dl)
                        HStack(spacing: 3) {
                            Image(systemName: "flag")
                                .font(.system(size: 9.5, weight: .semibold))
                            Text(dlInfo.label)
                                .font(.system(size: 10.5, weight: .semibold))
                                .lineLimit(1)
                        }
                        .foregroundStyle(dlInfo.isUrgent ? ThingsMacPalette.deadlineCrimson : .secondary)
                        .padding(.horizontal, 6.5)
                        .padding(.vertical, 2)
                        .background(
                            Capsule().fill(
                                dlInfo.isUrgent
                                    ? ThingsMacPalette.deadlineCrimson.opacity(0.13)
                                    : Color.primary.opacity(0.055)
                            )
                        )
                    }

                    if !task.checklistItems.isEmpty {
                        let done = task.checklistItems.filter(\.isCompleted).count
                        HStack(spacing: 3) {
                            Image(systemName: "checklist")
                                .font(.system(size: 9.5))
                            Text("\(done)/\(task.checklistItems.count)")
                                .font(.system(size: 10.5, weight: .medium, design: .rounded))
                        }
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 6.5)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(Color.primary.opacity(0.055)))
                    }

                    ForEach(task.tags.prefix(3)) { tag in
                        TagPillBadge(
                            title: tag.title,
                            isSelected: store.selectedFilterTagId == tag.id,
                            showIcon: true
                        ) {
                            store.selectedFilterTagId = (store.selectedFilterTagId == tag.id) ? nil : tag.id
                        }
                    }
                }
            }
        }
        .opacity(isBlocked ? 0.68 : 1.0)
        .padding(11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .thingsCardSurface(cornerRadius: 11, isSelected: isSelected)
        .background(alignment: .bottomLeading) {
            HStack(spacing: 0) {
                Color.clear
                    .frame(width: 1, height: 1)
                    .popover(isPresented: $isDependenciesPopoverOpen) {
                        DependenciesPopoverView(store: store, task: latestTask)
                    }
                Color.clear
                    .frame(width: 1, height: 1)
                    .popover(isPresented: $isWhenPopoverOpen) {
                        WhenPopoverView(
                            store: store,
                            currentSchedule: latestTask.scheduleState,
                            currentWhenMillis: latestTask.whenDate,
                            currentReminderMillis: latestTask.reminderTime
                        ) { resolved in
                            var copy = latestTask
                            copy.scheduleState = resolved.scheduleState
                            copy.whenDate = resolved.whenDateEpochMillis
                            copy.reminderTime = resolved.reminderEpochMillis
                            store.updateTask(copy)
                        }
                    }
                Color.clear
                    .frame(width: 1, height: 1)
                    .popover(isPresented: $isDeadlinePopoverOpen) {
                        DeadlinePopoverView(store: store, currentDeadlineMillis: latestTask.deadline) { dl in
                            var copy = latestTask
                            copy.deadline = dl
                            store.updateTask(copy)
                        }
                    }
                Color.clear
                    .frame(width: 1, height: 1)
                    .popover(isPresented: $isMovePopoverOpen) {
                        MoveDestinationPopoverView(store: store, task: latestTask) { projId, headId, areaId, schedOverride in
                            var copy = latestTask
                            copy.projectId = projId
                            copy.headingId = headId
                            copy.areaId = areaId
                            if let schedOverride {
                                copy.scheduleState = schedOverride
                            }
                            store.updateTask(copy)
                        }
                    }
            }
            .padding(.leading, 16)
        }
        .contentShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
        .onHover { hovering in
            isHovering = hovering
        }
        .thingsInstantTaskTap(
            onSingleClick: { modifiers in
                store.handleRowClick(taskId: task.id, modifiers: modifiers)
            },
            onDoubleClick: {
                onOpenDetail()
            }
        )
        .thingsTaskDraggable(store: store, task: task)
        .thingsDropTarget(cornerRadius: 11) { payloads in
            store.dropTasksIntoProjectSection(
                payloads,
                projectId: project.id,
                headingId: columnHeadingId,
                beforeTaskId: task.id
            )
        }
        .contextMenu {
            kanbanContextMenu
        }
    }

    private var hasAnyMetadataBadges: Bool {
        task.isMilestone
            || store.blockedByBadgeText(for: task) != nil
            || task.resolvedEffort != nil
            || task.formattedTimeblockPill != nil
            || whenBadgeText != nil
            || task.deadline != nil
            || !task.checklistItems.isEmpty
            || !task.tags.isEmpty
    }

    private var whenBadgeText: String? {
        switch task.scheduleState {
        case .today:
            return store.tr("view_today")
        case .thisEvening:
            return store.tr("view_this_evening")
        case .someday:
            return store.tr("view_someday")
        case .upcoming:
            if let w = task.whenDate {
                let df = DateFormatter()
                df.locale = store.preferences.language.locale
                df.setLocalizedDateFormatFromTemplate("dMMM")
                return df.string(from: DateEpochConverter.date(fromMillis: w))
            }
            return store.tr("view_upcoming")
        default:
            if let w = task.whenDate {
                let df = DateFormatter()
                df.locale = store.preferences.language.locale
                df.setLocalizedDateFormatFromTemplate("dMMM")
                return df.string(from: DateEpochConverter.date(fromMillis: w))
            }
            return nil
        }
    }

    private var whenBadgeIcon: String {
        switch task.scheduleState {
        case .today: return "sun.max.fill"
        case .thisEvening: return "moon.stars.fill"
        case .someday: return "cloud.fill"
        default: return "calendar"
        }
    }

    private var whenBadgeTint: Color {
        switch task.scheduleState {
        case .today: return ThingsMacPalette.todayYellow
        case .thisEvening: return ThingsMacPalette.eveningPurple
        case .someday: return ThingsMacPalette.somedayAmber
        default: return ThingsMacPalette.accentBlue
        }
    }

    private func deadlineBadgeInfo(_ millis: Int64) -> (label: String, isUrgent: Bool) {
        let todayDay = DateEpochConverter.epochDay(for: Date())
        let dlDay = DateEpochConverter.epochDay(forMillis: millis)
        let diff = dlDay - todayDay
        if diff == 0 { return (store.tr("deadline_today"), true) }
        if diff < 0 { return (store.trf("deadline_days_overdue", abs(diff)), true) }
        return (store.trf("deadline_days_left", diff), diff <= 1)
    }

    private func effortTitle(_ effort: TaskEffortLevel) -> String {
        switch effort {
        case .quickWin: return store.tr("v30_effort_quick_win")
        case .medium: return store.tr("v30_effort_medium")
        case .deepFocus: return store.tr("v30_effort_deep_focus")
        }
    }

    private func effortColor(_ effort: TaskEffortLevel) -> Color {
        switch effort {
        case .quickWin: return ThingsMacPalette.anytimeTeal
        case .medium: return ThingsMacPalette.accentBlue
        case .deepFocus: return ThingsMacPalette.eveningPurple
        }
    }

    @ViewBuilder
    private var kanbanContextMenu: some View {
        let isBatch = store.selectedTaskIds.count > 1 && store.selectedTaskIds.contains(task.id)
        if isBatch {
            let allMilestones = store.selectedTaskIds.allSatisfy { id in
                store.allOpenTasks.first(where: { $0.id == id })?.isMilestone == true
            }
            Button(store.tr("action_complete")) {
                store.completeSelectedTasks()
            }
            Button(store.tr("view_today")) {
                store.scheduleSelectedTasks(.today)
            }
            Button(store.tr("v25_batch_someday")) {
                store.scheduleSelectedTasks(.someday)
            }
            Button(store.tr("v25_batch_pin")) {
                store.togglePinForSelectedTasks()
            }
            Button {
                store.toggleMilestoneForSelectedTasks()
            } label: {
                Label(
                    store.tr(allMilestones ? "v30_remove_milestone" : "v30_mark_as_milestone"),
                    systemImage: allMilestones ? "diamond.fill" : "diamond"
                )
            }
            Button(store.tr("v25_batch_duplicate")) {
                store.duplicateSelectedTasks()
            }
            Divider()
            Button(role: .destructive) {
                store.deleteSelectedTasks()
            } label: {
                Text(store.tr("action_delete"))
            }
        } else {
            Button {
                onOpenDetail()
            } label: {
                Label(store.tr("v30_open_details_action"), systemImage: "slider.horizontal.3")
            }
            Button {
                store.toggleTaskCompletion(latestTask)
            } label: {
                Label(
                    task.status == .completed ? store.tr("action_reopen_todo") : store.tr("action_complete"),
                    systemImage: task.status == .completed ? "arrow.uturn.backward.circle" : "checkmark.circle"
                )
            }
            Divider()
            Menu {
                Button {
                    store.scheduleTasks([latestTask.id], to: .today)
                } label: {
                    Label(store.tr("view_today"), systemImage: "sun.max.fill")
                }
                Button {
                    store.scheduleTasks([latestTask.id], to: .thisEvening)
                } label: {
                    Label(store.tr("view_this_evening"), systemImage: "moon.stars.fill")
                }
                Button {
                    store.scheduleTasks([latestTask.id], to: .someday)
                } label: {
                    Label(store.tr("view_someday"), systemImage: "cloud.fill")
                }
                Divider()
                Button {
                    isWhenPopoverOpen = true
                } label: {
                    Label(store.tr("repeat_ends_pick_date"), systemImage: "calendar")
                }
            } label: {
                Label(store.tr("action_when"), systemImage: "calendar")
            }
            Button {
                isDeadlinePopoverOpen = true
            } label: {
                Label("\(store.tr("action_deadline"))…", systemImage: "flag")
            }
            Button {
                isMovePopoverOpen = true
            } label: {
                Label("\(store.tr("action_move"))…", systemImage: "folder")
            }
            let projectHeadings = (store.headingsByProject[project.id] ?? [])
                .filter { !$0.isDeleted && !$0.isArchived }
                .sorted {
                    if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                    return $0.id < $1.id
                }
            if !projectHeadings.isEmpty {
                Menu {
                    Button {
                        var copy = latestTask
                        copy.headingId = nil
                        store.updateTask(copy)
                    } label: {
                        if latestTask.headingId == nil {
                            Label(store.tr("v30_board_ungrouped_column"), systemImage: "checkmark")
                        } else {
                            Text(store.tr("v30_board_ungrouped_column"))
                        }
                    }
                    Divider()
                    ForEach(projectHeadings) { heading in
                        Button {
                            var copy = latestTask
                            copy.headingId = heading.id
                            store.updateTask(copy)
                        } label: {
                            if latestTask.headingId == heading.id {
                                Label(heading.title, systemImage: "checkmark")
                            } else {
                                Text(heading.title)
                            }
                        }
                    }
                } label: {
                    Label(store.tr("entity_heading"), systemImage: "flag.checkered")
                }
            }
            Divider()
            Button {
                store.setTasksPinned([latestTask.id], isPinned: !latestTask.isPinned)
            } label: {
                Label(
                    store.tr(latestTask.isPinned ? "v25_unpin_task" : "v25_pin_task"),
                    systemImage: latestTask.isPinned ? "pin.slash" : "pin"
                )
            }
            Button {
                store.setTasksMilestone([latestTask.id], isMilestone: !latestTask.isMilestone)
            } label: {
                Label(
                    store.tr(latestTask.isMilestone ? "v30_remove_milestone" : "v30_mark_as_milestone"),
                    systemImage: latestTask.isMilestone ? "diamond.fill" : "diamond"
                )
            }
            Button {
                _ = try? store.database.duplicateTask(task.id, copySuffix: store.tr("v25_copy_suffix"))
            } label: {
                Label(store.tr("action_duplicate_todo"), systemImage: "plus.square.on.square")
            }
            Divider()
            Button {
                store.cancelTask(latestTask)
            } label: {
                Label(
                    task.status == .canceled ? store.tr("action_reopen_todo") : store.tr("action_cancel_todo"),
                    systemImage: task.status == .canceled ? "arrow.uturn.backward.circle" : "xmark.circle"
                )
            }
            Button(role: .destructive) {
                store.deleteTask(latestTask)
            } label: {
                Label(store.tr("action_delete"), systemImage: "trash")
            }
        }
    }
}

// MARK: - Mode 3: ProjectRoadmapWorkspaceView (Calm Bento Gantt + PDF/PNG Export)

fileprivate enum RoadmapTimelineRow: Identifiable {
    case generalHeader
    case phaseHeader(Heading, RoadmapPhaseBar?)
    case task(RoadmapTaskBar)

    var id: String {
        switch self {
        case .generalHeader:
            return "roadmap_general_header"
        case .phaseHeader(let h, _):
            return "roadmap_phase_\(h.id)"
        case .task(let b):
            return "roadmap_task_\(b.id)"
        }
    }
}

public struct ProjectRoadmapWorkspaceView: View {
    @ObservedObject var store: ThingsbookStore
    public let project: Project
    public let openTasks: [TaskItem]
    public let completedTasks: [TaskItem]
    public let headings: [Heading]

    @State private var dayColumnWidth: CGFloat = 38.0
    @State private var activePhaseDrag: (id: String, deltaDays: Int)? = nil
    @State private var activeTaskDrag: (id: String, deltaDays: Int)? = nil
    @State private var armedPhaseDragId: String? = nil
    @State private var armedTaskDragId: String? = nil
    @State private var unscheduledStartPopoverTaskId: String? = nil
    @State private var isPDFExportSheetPresented: Bool = false

    private let labelColumnWidth: CGFloat = 272
    private let headerHeight: CGFloat = 50
    private let phaseRowHeight: CGFloat = 60
    private let standardRowHeight: CGFloat = 36
    private let rowSpacing: CGFloat = 5

    public init(
        store: ThingsbookStore,
        project: Project,
        openTasks: [TaskItem],
        completedTasks: [TaskItem],
        headings: [Heading]
    ) {
        self.store = store
        self.project = project
        self.openTasks = openTasks
        self.completedTasks = completedTasks
        self.headings = headings
    }

    private func rowHeight(for row: RoadmapTimelineRow) -> CGFloat {
        switch row {
        case .phaseHeader:
            return phaseRowHeight
        case .generalHeader, .task:
            return standardRowHeight
        }
    }

    private func totalTimelineHeight(for rows: [RoadmapTimelineRow]) -> CGFloat {
        let rowsSum = rows.reduce(CGFloat(0)) { $0 + rowHeight(for: $1) }
        let spacingsSum = rows.isEmpty ? 0 : CGFloat(rows.count) * rowSpacing
        return headerHeight + rowsSum + spacingsSum + 10
    }

    private var activeHeadings: [Heading] {
        headings
            .filter { !$0.isDeleted && !$0.isArchived }
            .sorted {
                if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                return $0.id < $1.id
            }
    }

    private var roadmapLayout: ProjectRoadmapLayout {
        let scheduledCompleted = completedTasks.filter { $0.whenDate != nil || $0.deadline != nil }
        return ProjectRoadmapEngine.buildLayout(
            project: project,
            headings: activeHeadings,
            tasks: openTasks + scheduledCompleted,
            todayMillis: DateEpochConverter.startOfDayMillis(for: Date())
        )
    }

    private func computeActiveCascadePreviewMap(for layout: ProjectRoadmapLayout) -> [String: Int] {
        guard !store.isTimelineFullScreen,
              let drag = activeTaskDrag,
              drag.deltaDays != 0 else {
            return [:]
        }
        let todayMs = DateEpochConverter.startOfDayMillis(
            for: DateEpochConverter.date(fromEpochDay: layout.todayEpochDay)
        )
        let allProjectTasks = layout.allTaskBars.map(\.task)
        if store.selectedTaskIds.contains(drag.id) && store.selectedTaskIds.count > 1 {
            var merged: [String: Int] = [:]
            for bar in layout.allTaskBars where store.selectedTaskIds.contains(bar.id) {
                let sub = ProjectRoadmapEngine.computeCascadeDeltaPreviewMap(
                    anchorTask: bar.task,
                    deltaDays: drag.deltaDays,
                    projectTasks: allProjectTasks,
                    todayMillis: todayMs
                )
                for (tid, d) in sub {
                    if let existing = merged[tid] {
                        if abs(d) > abs(existing) { merged[tid] = d }
                    } else {
                        merged[tid] = d
                    }
                }
            }
            return merged
        }
        guard let anchorBar = layout.allTaskBars.first(where: { $0.id == drag.id }) else {
            return [drag.id: drag.deltaDays]
        }
        return ProjectRoadmapEngine.computeCascadeDeltaPreviewMap(
            anchorTask: anchorBar.task,
            deltaDays: drag.deltaDays,
            projectTasks: allProjectTasks,
            todayMillis: todayMs
        )
    }

    private func buildTimelineRows(for layout: ProjectRoadmapLayout) -> [RoadmapTimelineRow] {
        var rows: [RoadmapTimelineRow] = []
        if !layout.generalTaskBars.isEmpty {
            rows.append(.generalHeader)
            for bar in layout.generalTaskBars {
                rows.append(.task(bar))
            }
        }
        for heading in activeHeadings {
            let phaseBar = layout.phaseBar(for: heading.id)
            let childBars = layout.taskBarsByHeading[heading.id] ?? []
            rows.append(.phaseHeader(heading, phaseBar))
            for bar in childBars {
                rows.append(.task(bar))
            }
        }
        return rows
    }

    public var body: some View {
        let layout = roadmapLayout
        let rows = buildTimelineRows(for: layout)
        let totalGridWidth = CGFloat(max(21, layout.totalDays)) * dayColumnWidth

        VStack(alignment: .leading, spacing: 14) {
            ScrollViewReader { scrollProxy in
                VStack(alignment: .leading, spacing: 12) {
                    roadmapToolbar(layout: layout) {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            scrollProxy.scrollTo("roadmap_day_\(layout.todayEpochDay)", anchor: .center)
                        }
                    }

                    if rows.isEmpty {
                        emptyRoadmapHintCard
                    } else {
                        HStack(alignment: .top, spacing: 0) {
                            // Left Pinned Label Column
                            roadmapLeftLabelColumn(rows: rows, layout: layout)

                            Divider()

                            // Right Horizontally Scrollable Timeline Grid
                            ScrollView(.horizontal, showsIndicators: true) {
                                roadmapTimelineGrid(
                                    layout: layout,
                                    rows: rows,
                                    totalGridWidth: totalGridWidth
                                )
                            }
                        }
                        .background(ThingsMacPalette.cardSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 15, style: .continuous)
                                .strokeBorder(Color.primary.opacity(0.085), lineWidth: 0.9)
                        )
                        .shadow(color: Color.black.opacity(0.045), radius: 8, y: 2)
                        .onAppear {
                            DispatchQueue.main.async {
                                scrollProxy.scrollTo("roadmap_day_\(layout.todayEpochDay)", anchor: .center)
                            }
                        }
                    }
                }
            }

            // Unscheduled Tray (hidden in Timeline Full Screen so 100% of height is dedicated to the Gantt Timeline)
            if !store.isTimelineFullScreen && (!layout.unscheduledTasks.isEmpty || rows.isEmpty) {
                unscheduledSectionCard(unscheduledTasks: layout.unscheduledTasks)
            }
        }
        .onChange(of: store.isTimelineFullScreen) { _, fullScreen in
            if fullScreen {
                activePhaseDrag = nil
                activeTaskDrag = nil
                armedPhaseDragId = nil
                armedTaskDragId = nil
                unscheduledStartPopoverTaskId = nil
                isPDFExportSheetPresented = false
            }
        }
        .sheet(isPresented: $isPDFExportSheetPresented) {
            RoadmapPDFExportSheetView(
                store: store,
                project: project,
                headings: activeHeadings,
                layout: layout,
                isPresented: $isPDFExportSheetPresented
            ) { exportOptions in
                DispatchQueue.main.async {
                    exportRoadmapPDFWithOptions(layout: layout, options: exportOptions)
                }
            }
        }
    }

    // MARK: - Roadmap Toolbar

    private func roadmapToolbar(
        layout: ProjectRoadmapLayout,
        onJumpToday: @escaping () -> Void
    ) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 10) {
                roadmapLeadingControls(layout: layout, onJumpToday: onJumpToday)
                Spacer(minLength: 8)
                roadmapTrailingControls(layout: layout)
            }

            VStack(alignment: .leading, spacing: 8) {
                roadmapLeadingControls(layout: layout, onJumpToday: onJumpToday)
                HStack(spacing: 8) {
                    roadmapTrailingControls(layout: layout)
                    Spacer(minLength: 0)
                }
            }
        }
    }

    private func roadmapLeadingControls(
        layout: ProjectRoadmapLayout,
        onJumpToday: @escaping () -> Void
    ) -> some View {
        HStack(spacing: 10) {
            if store.isTimelineFullScreen {
                HStack(spacing: 8) {
                    ProjectProgressRing(
                        progress: project.progress,
                        size: 16,
                        tint: ThingsMacPalette.accentBlue,
                        isCompleted: project.status == .completed
                    )
                    .fixedSize()

                    Text(project.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .frame(maxWidth: 320, alignment: .leading)
                        .thingsTooltip(project.title)
                }
            }

            HStack(spacing: 6) {
                Image(systemName: "calendar")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                Text(formattedWindowLabel(for: layout))
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 11)
            .padding(.vertical, 5.5)
            .background(
                Capsule(style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.8)
                    )
            )

            ThingsActionChipButton(
                title: store.tr("v30_roadmap_jump_today"),
                systemImage: "scope",
                helpText: store.tr("v30_roadmap_jump_today"),
                action: onJumpToday
            )

            if !store.isTimelineFullScreen,
               let primarySelectedId = store.selectedTaskIds.first,
               let selectedTask = (openTasks + completedTasks).first(where: { $0.id == primarySelectedId }) {
                HStack(spacing: 4) {
                    Button {
                        store.shiftTaskRoadmapDates(selectedTask, deltaDays: -1)
                    } label: {
                        Text(store.tr("v30_roadmap_shift_earlier"))
                            .font(.system(size: 11.5, weight: .semibold))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 8.5)
                            .padding(.vertical, 4.5)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .fixedSize(horizontal: true, vertical: false)

                    Button {
                        store.shiftTaskRoadmapDates(selectedTask, deltaDays: 1)
                    } label: {
                        Text(store.tr("v30_roadmap_shift_later"))
                            .font(.system(size: 11.5, weight: .semibold))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 8.5)
                            .padding(.vertical, 4.5)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .fixedSize(horizontal: true, vertical: false)
                }
            }
        }
    }

    private func roadmapTrailingControls(layout: ProjectRoadmapLayout) -> some View {
        let isFull = store.isTimelineFullScreen
        let fullScreenLabel = isFull ? store.tr("v30_timeline_exit_fullscreen") : store.tr("v30_timeline_fullscreen")
        let fullScreenIcon = isFull ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right"

        return HStack(spacing: 8) {
            // Day-width zoom controls
            HStack(spacing: 2) {
                Button {
                    withAnimation(.easeOut(duration: 0.16)) {
                        dayColumnWidth = max(26.0, dayColumnWidth - 10.0)
                    }
                } label: {
                    Image(systemName: "minus.magnifyingglass")
                        .font(.system(size: 11.5, weight: .semibold))
                        .frame(width: 26, height: 24)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("macos_tooltip_zoom_out"))

                Button {
                    withAnimation(.easeOut(duration: 0.16)) {
                        dayColumnWidth = 38.0
                    }
                } label: {
                    Text("\(Int((dayColumnWidth / 38.0 * 100.0).rounded()))%")
                        .font(.system(size: 11, weight: .semibold, design: .rounded))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 6)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("macos_tooltip_zoom_reset"))

                Button {
                    withAnimation(.easeOut(duration: 0.16)) {
                        dayColumnWidth = min(64.0, dayColumnWidth + 10.0)
                    }
                } label: {
                    Image(systemName: "plus.magnifyingglass")
                        .font(.system(size: 11.5, weight: .semibold))
                        .frame(width: 26, height: 24)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("macos_tooltip_zoom_in"))
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.8)
                    )
            )
            .fixedSize()

            // Export Roadmap Menu (hidden in Timeline Full Screen)
            if !isFull {
                Menu {
                    Button {
                        isPDFExportSheetPresented = true
                    } label: {
                        Label(store.tr("v30_roadmap_export_pdf"), systemImage: "doc.richtext")
                    }

                    Button {
                        exportRoadmapPNGInteractively(layout: layout)
                    } label: {
                        Label(store.tr("v30_roadmap_export_png"), systemImage: "photo")
                    }
                } label: {
                    HStack(spacing: 5.5) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 11.5, weight: .semibold))
                        Text(store.tr("v30_roadmap_export"))
                            .font(.system(size: 12, weight: .semibold))
                            .lineLimit(1)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 8.5, weight: .bold))
                            .opacity(0.75)
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 11)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                    )
                }
                .menuStyle(.borderlessButton)
                .menuIndicator(.hidden)
                .fixedSize()
                .thingsTooltip(store.tr("v30_roadmap_export"))
            }

            // Full Screen / Exit Full Screen Toggle Button
            Button {
                store.toggleTimelineFullScreen()
            } label: {
                HStack(spacing: 5.5) {
                    Image(systemName: fullScreenIcon)
                        .font(.system(size: 11, weight: .semibold))
                    Text(fullScreenLabel)
                        .font(.system(size: 12, weight: .semibold))
                        .lineLimit(1)
                }
                .fixedSize(horizontal: true, vertical: false)
                .foregroundStyle(isFull ? ThingsMacPalette.accentBlue : Color.primary.opacity(0.88))
                .padding(.horizontal, 11)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(isFull ? ThingsMacPalette.accentBlue.opacity(0.14) : ThingsMacPalette.subtleContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .strokeBorder(
                                    isFull ? ThingsMacPalette.accentBlue.opacity(0.32) : Color.primary.opacity(0.08),
                                    lineWidth: 0.8
                                )
                        )
                )
                .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            .buttonStyle(.plain)
            .fixedSize(horizontal: true, vertical: false)
            .keyboardShortcut("f", modifiers: [.command, .control])
            .thingsTooltip(isFull ? "\(fullScreenLabel) (Esc)" : "\(fullScreenLabel) (⌃⌘F)")
        }
    }

    // MARK: - Left Label Column

    private func roadmapLeftLabelColumn(rows: [RoadmapTimelineRow], layout: ProjectRoadmapLayout) -> some View {
        let totalHeight = totalTimelineHeight(for: rows)

        return VStack(alignment: .leading, spacing: rowSpacing) {
            HStack {
                Text(project.title)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Spacer()
            }
            .padding(.horizontal, 12)
            .frame(width: labelColumnWidth, height: headerHeight)
            .background(ThingsMacPalette.subtleContainer.opacity(0.75))

            ForEach(rows) { row in
                let rHeight = rowHeight(for: row)
                switch row {
                case .generalHeader:
                    roadmapLeftGeneralHeaderRow(height: rHeight)

                case .phaseHeader(let heading, _):
                    roadmapLeftPhaseHeaderRow(heading: heading, height: rHeight)

                case .task(let bar):
                    roadmapLeftTaskLabelRow(bar: bar, layout: layout, height: rHeight)
                }
            }
        }
        .padding(.bottom, 10)
        .frame(width: labelColumnWidth, height: totalHeight, alignment: .topLeading)
        .background(ThingsMacPalette.subtleContainer.opacity(0.45))
    }

    @ViewBuilder
    private func roadmapLeftGeneralHeaderRow(height: CGFloat) -> some View {
        let generalHeaderRow = HStack(spacing: 6) {
            Capsule()
                .fill(ThingsMacPalette.inboxBlue)
                .frame(width: 3.5, height: 15)
            Text(store.tr("v30_board_ungrouped_column"))
                .font(.system(size: 12.5, weight: .bold))
                .foregroundStyle(.primary)
                .lineLimit(1)
            Spacer()
        }
        .padding(.horizontal, 12)
        .frame(width: labelColumnWidth, height: height)

        if store.isTimelineFullScreen {
            generalHeaderRow
        } else {
            generalHeaderRow
                .thingsDropTarget(cornerRadius: 8) { payloads in
                    store.dropTasksIntoProjectSection(payloads, projectId: project.id, headingId: nil)
                }
        }
    }

    @ViewBuilder
    private func roadmapLeftPhaseHeaderRow(heading: Heading, height: CGFloat) -> some View {
        let hasOpenInPhase = openTasks.contains { $0.headingId == heading.id }
        let showPhasePills = !store.isTimelineFullScreen || heading.startDate != nil || heading.endDate != nil
        let phaseHeaderRow = VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top, spacing: 6) {
                Capsule()
                    .fill(ThingsMacPalette.inboxBlue)
                    .frame(width: 3.5, height: 15)
                    .padding(.top, 1)
                Text(heading.title)
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }

            if showPhasePills {
                HStack(spacing: 5) {
                    PhaseStartDatePillButton(store: store, heading: heading, compact: true)
                    PhaseEndDatePillButton(
                        store: store,
                        heading: heading,
                        hasOpenTasks: hasOpenInPhase,
                        compact: true
                    )
                    Spacer(minLength: 0)
                }
                .padding(.leading, 9.5)
            }
        }
        .padding(.horizontal, 10)
        .frame(width: labelColumnWidth, height: height, alignment: .center)

        if store.isTimelineFullScreen {
            phaseHeaderRow
        } else {
            phaseHeaderRow
                .thingsDropTarget(cornerRadius: 8) { payloads in
                    store.dropTasksIntoProjectSection(payloads, projectId: project.id, headingId: heading.id)
                }
        }
    }

    @ViewBuilder
    private func roadmapLeftTaskLabelRow(
        bar: RoadmapTaskBar,
        layout: ProjectRoadmapLayout,
        height: CGFloat
    ) -> some View {
        let isFullScreen = store.isTimelineFullScreen
        let isSelected = !isFullScreen && store.selectedTaskIds.contains(bar.id)
        let isMilestone = bar.isMilestone || bar.task.isMilestone
        let isRowSuppressed = !isFullScreen && (armedTaskDragId == bar.id || activeTaskDrag?.id == bar.id)
        let rowContent = HStack(spacing: 7) {
            ThingsCheckboxButton(
                status: bar.task.status,
                isSomeday: bar.task.scheduleState == .someday,
                isMilestone: isMilestone,
                helpText: isFullScreen ? "" : store.tr("macos_checkbox_help"),
                onToggleComplete: {
                    guard !store.isTimelineFullScreen else { return }
                    store.toggleTaskCompletion(bar.task)
                },
                onCancelTask: {
                    guard !store.isTimelineFullScreen else { return }
                    store.cancelTask(bar.task)
                }
            )
            .disabled(isFullScreen)
            .allowsHitTesting(!isFullScreen)

            Text(bar.task.title.isEmpty ? store.tr("macos_untitled_todo") : bar.task.title)
                .font(.system(size: 12.5, weight: (isSelected || isMilestone) ? .semibold : .regular))
                .foregroundStyle(
                    bar.task.status == .completed
                        ? .secondary
                        : (isMilestone ? ThingsMacPalette.milestoneViolet : .primary)
                )
                .lineLimit(1)
            Spacer(minLength: 2)
        }
        .padding(.leading, 18)
        .padding(.trailing, 8)
        .frame(width: labelColumnWidth, height: height)
        .background(
            RoundedRectangle(cornerRadius: 7, style: .continuous)
                .fill(
                    isSelected
                        ? (isMilestone ? ThingsMacPalette.milestoneViolet.opacity(0.14) : ThingsMacPalette.accentBlue.opacity(0.12))
                        : Color.clear
                )
        )
        .contentShape(Rectangle())
        .thingsTimelineMouseTooltip(
            roadmapTaskTooltipText(for: bar, layout: layout),
            isSuppressed: isRowSuppressed
        )

        if isFullScreen {
            rowContent
        } else {
            rowContent
                .thingsInstantTaskTap(
                    onSingleClick: { modifiers in
                        store.handleRowClick(taskId: bar.id, modifiers: modifiers)
                    },
                    onDoubleClick: {
                        store.openTaskModal(bar.id)
                    }
                )
                .contextMenu {
                    roadmapTaskContextMenu(for: bar.task, includeDateShift: true)
                }
                .thingsTaskDraggable(store: store, task: bar.task)
        }
    }

    // MARK: - Right Timeline Grid, Bars & Dependency Canvas

    private func roadmapTimelineGrid(
        layout: ProjectRoadmapLayout,
        rows: [RoadmapTimelineRow],
        totalGridWidth: CGFloat
    ) -> some View {
        let totalHeight = totalTimelineHeight(for: rows)
        let cascadePreviewDeltas = computeActiveCascadePreviewMap(for: layout)

        return ZStack(alignment: .topLeading) {
            // 1. Background Day Columns + Month/Day Header + Today Vertical Line
            HStack(spacing: 0) {
                ForEach(0..<layout.totalDays, id: \.self) { dayOffset in
                    let epochDay = layout.startEpochDay + dayOffset
                    let isToday = epochDay == layout.todayEpochDay
                    let isWeekend = isWeekendDay(epochDay)

                    VStack(spacing: 0) {
                        VStack(spacing: 2) {
                            Text(monthHeaderChip(for: epochDay, isFirstColumn: dayOffset == 0))
                                .font(.system(size: 9.5, weight: .bold))
                                .foregroundStyle(isToday ? ThingsMacPalette.accentBlue : .secondary)
                                .lineLimit(1)
                                .frame(height: 15)

                            HStack(spacing: 2) {
                                Text("\(dayNumber(for: epochDay))")
                                    .font(.system(size: 11.5, weight: isToday ? .bold : .semibold, design: .rounded))
                                Text(weekdayInitial(for: epochDay))
                                    .font(.system(size: 9.5, weight: .medium))
                                    .opacity(0.75)
                            }
                            .foregroundStyle(isToday ? .white : (isWeekend ? .secondary : .primary))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(isToday ? ThingsMacPalette.accentBlue : Color.clear)
                            )
                        }
                        .frame(width: dayColumnWidth, height: headerHeight)
                        .background(ThingsMacPalette.subtleContainer.opacity(0.75))
                        .id("roadmap_day_\(epochDay)")

                        Rectangle()
                            .fill(
                                isToday
                                    ? ThingsMacPalette.accentBlue.opacity(0.07)
                                    : (isWeekend ? Color.primary.opacity(0.018) : Color.clear)
                            )
                            .frame(width: dayColumnWidth, height: max(40, totalHeight - headerHeight))
                            .overlay(alignment: .leading) {
                                Rectangle()
                                    .fill(Color.primary.opacity(0.055))
                                    .frame(width: 0.75)
                            }
                            .overlay {
                                if isToday {
                                    Rectangle()
                                        .fill(ThingsMacPalette.accentBlue.opacity(0.65))
                                        .frame(width: 1.6)
                                }
                            }
                    }
                }
            }

            // 2. Cubic-Bézier Dependency Connectors Canvas Overlay (Exact Cumulative Row Y-Offsets)
            Canvas { context, _ in
                var midYByTaskId: [String: CGFloat] = [:]
                var cursorY: CGFloat = headerHeight + rowSpacing
                for row in rows {
                    let rH = rowHeight(for: row)
                    if case .task(let bar) = row {
                        midYByTaskId[bar.id] = cursorY + (rH * 0.5)
                    }
                    cursorY += rH + rowSpacing
                }
                let barById = Dictionary(uniqueKeysWithValues: layout.allTaskBars.map { ($0.id, $0) })

                for edge in layout.dependencyEdges {
                    guard let fromBar = barById[edge.fromTaskId],
                          let toBar = barById[edge.toTaskId],
                          let y0 = midYByTaskId[edge.fromTaskId],
                          let y1 = midYByTaskId[edge.toTaskId] else {
                        continue
                    }

                    let fromDelta = cascadePreviewDeltas[fromBar.id] ?? 0
                    let toDelta = cascadePreviewDeltas[toBar.id] ?? 0
                    let fromShift = CGFloat(fromDelta) * dayColumnWidth
                    let toShift = CGFloat(toDelta) * dayColumnWidth

                    let x0: CGFloat = (fromBar.isMilestone || fromBar.task.isMilestone)
                        ? ((CGFloat(fromBar.endEpochDay - layout.startEpochDay) + 0.5) * dayColumnWidth + 11 + fromShift)
                        : (CGFloat(fromBar.endEpochDay - layout.startEpochDay + 1) * dayColumnWidth - 3 + fromShift)
                    let x1: CGFloat = (toBar.isMilestone || toBar.task.isMilestone)
                        ? ((CGFloat(toBar.startEpochDay - layout.startEpochDay) + 0.5) * dayColumnWidth - 11 + toShift)
                        : (CGFloat(toBar.startEpochDay - layout.startEpochDay) * dayColumnWidth + 3 + toShift)
                    let p0 = CGPoint(x: x0, y: y0)
                    let p3 = CGPoint(x: x1, y: y1)

                    var curve = Path()
                    curve.move(to: p0)
                    if p3.x >= p0.x + 12 {
                        let dx = max(24.0, (p3.x - p0.x) * 0.5)
                        curve.addCurve(
                            to: p3,
                            control1: CGPoint(x: p0.x + dx, y: p0.y),
                            control2: CGPoint(x: p3.x - dx, y: p3.y)
                        )
                    } else {
                        let vSign: CGFloat = p3.y >= p0.y ? 1.0 : -1.0
                        curve.addCurve(
                            to: p3,
                            control1: CGPoint(x: p0.x + 28.0, y: p0.y + 14.0 * vSign),
                            control2: CGPoint(x: p3.x - 28.0, y: p3.y - 14.0 * vSign)
                        )
                    }

                    let edgeColor = edge.isActivelyBlocking
                        ? ThingsMacPalette.somedayAmber
                        : ThingsMacPalette.accentBlue.opacity(0.45)

                    context.stroke(
                        curve,
                        with: .color(edgeColor),
                        style: StrokeStyle(lineWidth: 1.75, lineCap: .round, lineJoin: .round)
                    )

                    var arrow = Path()
                    arrow.move(to: CGPoint(x: p3.x - 6.5, y: p3.y - 3.8))
                    arrow.addLine(to: p3)
                    arrow.addLine(to: CGPoint(x: p3.x - 6.5, y: p3.y + 3.8))
                    context.stroke(
                        arrow,
                        with: .color(edgeColor),
                        style: StrokeStyle(lineWidth: 1.75, lineCap: .round, lineJoin: .round)
                    )
                }
            }
            .frame(width: totalGridWidth, height: totalHeight)
            .allowsHitTesting(false)

            // 3. Draggable Glacier-Blue Phase Bars & Soft Bento Task Bars
            VStack(alignment: .leading, spacing: rowSpacing) {
                Color.clear
                    .frame(width: totalGridWidth, height: headerHeight)

                ForEach(rows) { row in
                    ZStack(alignment: .leading) {
                        Color.clear
                            .frame(width: totalGridWidth, height: rowHeight(for: row))

                        switch row {
                        case .generalHeader:
                            EmptyView()

                        case .phaseHeader(let heading, let maybeBar):
                            if let phaseBar = maybeBar {
                                roadmapPhaseBarView(
                                    heading: heading,
                                    phaseBar: phaseBar,
                                    layout: layout,
                                    totalGridWidth: totalGridWidth
                                )
                            }

                        case .task(let taskBar):
                            roadmapTaskBarView(
                                taskBar: taskBar,
                                layout: layout,
                                cascadePreviewDeltas: cascadePreviewDeltas
                            )
                        }
                    }
                }
            }
        }
        .frame(width: totalGridWidth, height: totalHeight, alignment: .topLeading)
        .coordinateSpace(name: "roadmapTimelineGrid")
    }

    @ViewBuilder
    private func roadmapPhaseBarView(
        heading: Heading,
        phaseBar: RoadmapPhaseBar,
        layout: ProjectRoadmapLayout,
        totalGridWidth: CGFloat
    ) -> some View {
        let isFullScreen = store.isTimelineFullScreen
        let isArmedThis = !isFullScreen && armedPhaseDragId == heading.id
        let delta = (!isFullScreen && activePhaseDrag?.id == heading.id) ? (activePhaseDrag?.deltaDays ?? 0) : 0
        let rawStart = phaseBar.startEpochDay + delta
        let rawEnd = phaseBar.endEpochDay + delta

        if rawEnd >= layout.startEpochDay && rawStart <= layout.endEpochDay {
            let clampedStart = max(layout.startEpochDay, rawStart)
            let clampedEnd = min(layout.endEpochDay, rawEnd)
            let clampedSpan = max(1, clampedEnd - clampedStart + 1)
            let barX = CGFloat(clampedStart - layout.startEpochDay) * dayColumnWidth + 2
            let barW = max(dayColumnWidth - 4, CGFloat(clampedSpan) * dayColumnWidth - 4)
            let estimatedTitleWidth = CGFloat(heading.title.count) * 7.2 + 34
            let fitsInsideBar = barW >= estimatedTitleWidth
            let placeLeading = !fitsInsideBar
                && (totalGridWidth - (barX + barW) < estimatedTitleWidth)
                && (barX >= estimatedTitleWidth)

            let phaseCapsule = HStack(spacing: 6) {
                Image(systemName: "flag.checkered")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(.white.opacity(0.95))

                if fitsInsideBar {
                    Text(heading.title)
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                }

                Spacer(minLength: 0)
            }
            .padding(.horizontal, fitsInsideBar ? 10 : 7)
            .frame(width: barW, height: 26)
            .background(
                Capsule(style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .opacity(phaseBar.hasExplicitDates ? 0.96 : 0.72)
                    .shadow(
                        color: ThingsMacPalette.accentBlue.opacity(isArmedThis ? 0.42 : 0.24),
                        radius: isArmedThis ? 7 : 4,
                        y: isArmedThis ? 2.5 : 1.5
                    )
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(
                        Color.white.opacity(isArmedThis ? 0.85 : (phaseBar.hasExplicitDates ? 0.45 : 0.65)),
                        style: StrokeStyle(lineWidth: isArmedThis ? 1.4 : 0.9, dash: phaseBar.hasExplicitDates ? [] : [4, 3])
                    )
            )
            .overlay(alignment: placeLeading ? .leading : .trailing) {
                if !fitsInsideBar {
                    Text(heading.title)
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .alignmentGuide(placeLeading ? .leading : .trailing) { d in
                            placeLeading ? (d[.trailing] + 6) : (d[.leading] - 6)
                        }
                }
            }
            .scaleEffect(isArmedThis ? 1.02 : 1.0)
            .offset(x: barX)

            if isFullScreen {
                phaseCapsule
            } else {
                phaseCapsule
                    .gesture(
                        LongPressGesture(minimumDuration: 0.28, maximumDistance: 8)
                            .sequenced(before: DragGesture(minimumDistance: 2, coordinateSpace: .named("roadmapTimelineGrid")))
                            .onChanged { value in
                                switch value {
                                case .first:
                                    break
                                case .second(true, nil):
                                    if armedPhaseDragId != heading.id {
                                        armedPhaseDragId = heading.id
                                    }
                                case .second(true, let drag?):
                                    if armedPhaseDragId != heading.id {
                                        armedPhaseDragId = heading.id
                                    }
                                    let snapped = Int((drag.translation.width / dayColumnWidth).rounded())
                                    activePhaseDrag = (heading.id, snapped)
                                default:
                                    break
                                }
                            }
                            .onEnded { value in
                                let finalDelta: Int = {
                                    if case .second(true, let drag?) = value {
                                        return Int((drag.translation.width / dayColumnWidth).rounded())
                                    }
                                    return 0
                                }()
                                armedPhaseDragId = nil
                                activePhaseDrag = nil
                                if finalDelta != 0 {
                                    store.shiftHeadingRoadmapDates(
                                        heading,
                                        deltaDays: finalDelta,
                                        fallbackEpochDays: (phaseBar.startEpochDay, phaseBar.endEpochDay)
                                    )
                                }
                            }
                    )
                    .contextMenu {
                        Button(store.tr("v30_roadmap_shift_earlier")) {
                            store.shiftHeadingRoadmapDates(heading, deltaDays: -1)
                        }
                        Button(store.tr("v30_roadmap_shift_later")) {
                            store.shiftHeadingRoadmapDates(heading, deltaDays: 1)
                        }
                        if heading.startDate != nil {
                            Divider()
                            Button(store.tr("v30_phase_clear_start_date")) {
                                store.updateHeadingStartDate(heading.id, startDate: nil)
                            }
                        }
                        if heading.endDate != nil {
                            Button(store.tr("phase_clear_end_date")) {
                                store.updateHeadingEndDate(heading.id, endDate: nil)
                            }
                        }
                    }
            }
        }
    }

    @ViewBuilder
    private func roadmapPlottedTaskVisual(
        taskBar: RoadmapTaskBar,
        layout: ProjectRoadmapLayout,
        isFullScreen: Bool,
        isSelected: Bool,
        isBlocked: Bool,
        isArmedThis: Bool,
        isDraggingThis: Bool,
        delta: Int
    ) -> some View {
        let task = taskBar.task
        let dragX = CGFloat(delta) * dayColumnWidth
        let displayTitle = task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title

        if taskBar.isMilestone || task.isMilestone {
            let isCompleted = task.status == .completed
            let isOverdue = !isCompleted && (taskBar.endEpochDay + delta) < layout.todayEpochDay
            let milestoneTint: Color = {
                if isCompleted { return ThingsMacPalette.logbookGreen }
                if isOverdue { return ThingsMacPalette.deadlineCrimson }
                return ThingsMacPalette.milestoneViolet
            }()
            let colCenterX = (CGFloat(taskBar.startEpochDay - layout.startEpochDay) + 0.5) * dayColumnWidth

            HStack(spacing: 6) {
                // Prominent 16x16pt 45° rotated royal-violet Diamond centered on the target day column
                ZStack {
                    if isSelected || isArmedThis {
                        RoundedRectangle(cornerRadius: 4.5, style: .continuous)
                            .strokeBorder(milestoneTint.opacity(0.55), lineWidth: 1.6)
                            .frame(width: 21, height: 21)
                            .rotationEffect(.degrees(45))
                    }

                    RoundedRectangle(cornerRadius: 3.5, style: .continuous)
                        .fill(milestoneTint)
                        .frame(width: 16, height: 16)
                        .overlay(
                            RoundedRectangle(cornerRadius: 3.5, style: .continuous)
                                .strokeBorder(Color.white.opacity(0.94), lineWidth: 1.5)
                        )
                        .rotationEffect(.degrees(45))
                        .shadow(
                            color: milestoneTint.opacity(isArmedThis || isDraggingThis ? 0.52 : 0.34),
                            radius: isArmedThis || isDraggingThis ? 6.5 : 4.0,
                            y: isArmedThis || isDraggingThis ? 2.5 : 1.5
                        )

                    if isCompleted {
                        Image(systemName: "checkmark")
                            .font(.system(size: 8.5, weight: .black))
                            .foregroundStyle(.white)
                    } else if isBlocked {
                        Image(systemName: "hourglass")
                            .font(.system(size: 7.5, weight: .black))
                            .foregroundStyle(.white)
                    }
                }
                .frame(width: 24, height: 26)

                // Sleek floating Milestone Callout Pill immediately to the right of the Diamond
                HStack(spacing: 5) {
                    Text("◆ \(displayTitle)")
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundStyle(isCompleted ? .secondary : milestoneTint)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)

                    if !isFullScreen && isSelected {
                        HStack(spacing: 2.5) {
                            Button {
                                store.shiftTaskRoadmapDates(task, deltaDays: -1)
                            } label: {
                                Text("-1d")
                                    .font(.system(size: 9.5, weight: .bold, design: .rounded))
                                    .foregroundStyle(milestoneTint)
                                    .padding(.horizontal, 4.5)
                                    .padding(.vertical, 1.5)
                                    .background(Capsule().fill(milestoneTint.opacity(0.14)))
                            }
                            .buttonStyle(.plain)
                            .thingsTooltip(store.tr("v30_roadmap_shift_earlier"))

                            Button {
                                store.shiftTaskRoadmapDates(task, deltaDays: 1)
                            } label: {
                                Text("+1d")
                                    .font(.system(size: 9.5, weight: .bold, design: .rounded))
                                    .foregroundStyle(milestoneTint)
                                    .padding(.horizontal, 4.5)
                                    .padding(.vertical, 1.5)
                                    .background(Capsule().fill(milestoneTint.opacity(0.14)))
                            }
                            .buttonStyle(.plain)
                            .thingsTooltip(store.tr("v30_roadmap_shift_later"))
                        }
                    }
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 4)
                .background(
                    Capsule(style: .continuous)
                        .fill(
                            isSelected
                                ? milestoneTint.opacity(0.16)
                                : ThingsMacPalette.cardSurface.opacity(0.96)
                        )
                        .shadow(
                            color: Color.black.opacity(isDraggingThis ? 0.16 : 0.06),
                            radius: isDraggingThis ? 6.0 : 2.5,
                            y: isDraggingThis ? 2.0 : 1.0
                        )
                )
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(
                            (isArmedThis || isSelected) ? milestoneTint : milestoneTint.opacity(0.45),
                            lineWidth: (isArmedThis || isSelected) ? 1.5 : 1.0
                        )
                )
                .fixedSize(horizontal: true, vertical: false)
            }
            .fixedSize(horizontal: true, vertical: false)
            .contentShape(Rectangle())
            .thingsTimelineMouseTooltip(
                roadmapTaskTooltipText(for: taskBar, layout: layout),
                isSuppressed: isArmedThis || isDraggingThis
            )
            .scaleEffect(isArmedThis ? 1.03 : 1.0)
            .offset(x: colCenterX - 12 + dragX)
        } else {
            let baseX = CGFloat(taskBar.startEpochDay - layout.startEpochDay) * dayColumnWidth + 3
            let barW = max(dayColumnWidth - 6, CGFloat(taskBar.spanDays) * dayColumnWidth - 6)
            let borderTint: Color = {
                if isArmedThis || isSelected { return ThingsMacPalette.accentBlue }
                if isBlocked { return ThingsMacPalette.somedayAmber }
                if task.status == .completed { return ThingsMacPalette.logbookGreen.opacity(0.6) }
                return ThingsMacPalette.accentBlue.opacity(0.45)
            }()

            HStack(spacing: 6) {
                if isBlocked {
                    Image(systemName: "hourglass")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(ThingsMacPalette.somedayAmber)
                } else if task.status == .completed {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 10.5, weight: .bold))
                        .foregroundStyle(ThingsMacPalette.logbookGreen)
                } else if task.isPinned {
                    Image(systemName: "pin.fill")
                        .font(.system(size: 9.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.todayYellow)
                }

                Text(displayTitle)
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer(minLength: 0)

                if task.deadline != nil {
                    Image(systemName: "flag.fill")
                        .font(.system(size: 9.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                }
            }
            .padding(.horizontal, 8)
            .frame(width: barW, height: 26)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(
                        isSelected
                            ? ThingsMacPalette.accentBlue.opacity(0.16)
                            : ThingsMacPalette.cardSurface
                    )
                    .shadow(color: Color.black.opacity(isDraggingThis ? 0.16 : 0.05), radius: isDraggingThis ? 6.5 : 2, y: isDraggingThis ? 2 : 1)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .strokeBorder(borderTint, lineWidth: (isArmedThis || isSelected) ? 1.55 : 1.05)
            )
            .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .thingsTimelineMouseTooltip(
                roadmapTaskTooltipText(for: taskBar, layout: layout),
                isSuppressed: isArmedThis || isDraggingThis
            )
            .scaleEffect(isArmedThis ? 1.02 : 1.0)
            .offset(x: baseX + dragX)
        }
    }

    private func roadmapTaskTooltipText(
        for taskBar: RoadmapTaskBar,
        layout: ProjectRoadmapLayout
    ) -> String {
        TimelineTaskTooltipFormatter.formatRoadmapTask(
            taskBar: taskBar,
            isBlocked: store.isTaskActivelyBlocked(taskBar.task),
            language: store.preferences.language
        )
    }

    @ViewBuilder
    private func roadmapTaskBarView(
        taskBar: RoadmapTaskBar,
        layout: ProjectRoadmapLayout,
        cascadePreviewDeltas: [String: Int]
    ) -> some View {
        let task = taskBar.task
        let isFullScreen = store.isTimelineFullScreen
        let isSelected = !isFullScreen && store.selectedTaskIds.contains(task.id)
        let isBlocked = store.isTaskActivelyBlocked(task)
        let isArmedThis = !isFullScreen && armedTaskDragId == task.id
        let previewDelta = isFullScreen ? 0 : (cascadePreviewDeltas[task.id] ?? 0)
        let isDraggingThis = !isFullScreen && (
            isArmedThis
            || (activeTaskDrag?.id == task.id)
            || previewDelta != 0
        )
        let delta = previewDelta
        let taskBarContent = roadmapPlottedTaskVisual(
            taskBar: taskBar,
            layout: layout,
            isFullScreen: isFullScreen,
            isSelected: isSelected,
            isBlocked: isBlocked,
            isArmedThis: isArmedThis,
            isDraggingThis: isDraggingThis,
            delta: delta
        )

        if isFullScreen {
            taskBarContent
        } else {
            taskBarContent
                .thingsInstantTaskTap(
                    onSingleClick: { modifiers in
                        store.handleRowClick(taskId: task.id, modifiers: modifiers)
                    },
                    onDoubleClick: {
                        store.openTaskModal(task.id)
                    }
                )
                .gesture(
                    LongPressGesture(minimumDuration: 0.28, maximumDistance: 8)
                        .sequenced(before: DragGesture(minimumDistance: 2, coordinateSpace: .named("roadmapTimelineGrid")))
                        .onChanged { value in
                            switch value {
                            case .first:
                                break
                            case .second(true, nil):
                                if armedTaskDragId != task.id {
                                    armedTaskDragId = task.id
                                }
                            case .second(true, let drag?):
                                if armedTaskDragId != task.id {
                                    armedTaskDragId = task.id
                                }
                                let snapped = Int((drag.translation.width / dayColumnWidth).rounded())
                                activeTaskDrag = (task.id, snapped)
                            default:
                                break
                            }
                        }
                        .onEnded { value in
                            let finalDelta: Int = {
                                if case .second(true, let drag?) = value {
                                    return Int((drag.translation.width / dayColumnWidth).rounded())
                                }
                                return 0
                            }()
                            armedTaskDragId = nil
                            activeTaskDrag = nil
                            if finalDelta != 0 {
                                store.shiftTaskRoadmapDates(task, deltaDays: finalDelta)
                            }
                        }
                )
                .contextMenu {
                    roadmapTaskContextMenu(for: task, includeDateShift: true)
                }
        }
    }

    // MARK: - Unscheduled Section Tray

    private var emptyRoadmapHintCard: some View {
        VStack(spacing: 10) {
            Image(systemName: "calendar.day.timeline.left")
                .font(.system(size: 32, weight: .light))
                .foregroundStyle(ThingsMacPalette.accentBlue.opacity(0.65))
            Text(store.tr("v30_roadmap_unscheduled_hint"))
                .font(.system(size: 13))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 44)
        .thingsCardSurface(cornerRadius: 14)
    }

    private func unscheduledSectionCard(unscheduledTasks: [TaskItem]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "tray.full")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                Text(store.tr("v30_roadmap_unscheduled_section"))
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.primary)

                if !unscheduledTasks.isEmpty {
                    Text("\(unscheduledTasks.count)")
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(Color.primary.opacity(0.06)))
                }

                Text("·")
                    .foregroundStyle(.tertiary)

                Text(store.tr("v30_roadmap_unscheduled_hint"))
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Spacer()
            }

            if !unscheduledTasks.isEmpty {
                VStack(spacing: 5) {
                    ForEach(unscheduledTasks) { task in
                        unscheduledTaskRow(task)
                    }
                }
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer.opacity(0.75))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.07), lineWidth: 0.85)
                )
        )
    }

    private func unscheduledTaskRow(_ task: TaskItem) -> some View {
        let isPopoverOpen = Binding<Bool>(
            get: { unscheduledStartPopoverTaskId == task.id },
            set: { open in unscheduledStartPopoverTaskId = open ? task.id : nil }
        )

        return HStack(spacing: 10) {
            ThingsCheckboxButton(
                status: task.status,
                isSomeday: task.scheduleState == .someday,
                isMilestone: task.isMilestone,
                helpText: store.tr("macos_checkbox_help"),
                onToggleComplete: { store.toggleTaskCompletion(task) },
                onCancelTask: { store.cancelTask(task) }
            )

            Text(task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.primary)
                .lineLimit(1)

            if task.isMilestone {
                Text("◆ \(store.tr("v30_milestone_label"))")
                    .font(.system(size: 10.5, weight: .bold))
                    .foregroundStyle(ThingsMacPalette.milestoneViolet)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(Capsule().fill(ThingsMacPalette.milestoneViolet.opacity(0.14)))
                    .fixedSize(horizontal: true, vertical: false)
            }

            if let hid = task.headingId,
               let heading = activeHeadings.first(where: { $0.id == hid }) {
                Text(heading.title)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.11)))
            }

            Spacer()

            // 1-click Today scheduling button
            ThingsActionChipButton(
                title: store.tr("view_today"),
                systemImage: "sun.max.fill",
                activeTint: ThingsMacPalette.todayYellow,
                helpText: store.tr("view_today")
            ) {
                var copy = task
                copy.scheduleState = .today
                copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
                store.updateTask(copy)
            }

            // 1-click Start Date calendar picker button
            ThingsActionChipButton(
                title: store.tr("v30_phase_set_start_date"),
                systemImage: "calendar.badge.plus",
                activeTint: ThingsMacPalette.accentBlue,
                helpText: store.tr("v30_phase_set_start_date")
            ) {
                unscheduledStartPopoverTaskId = task.id
            }
            .popover(isPresented: isPopoverOpen) {
                DeadlinePopoverView(
                    store: store,
                    currentDeadlineMillis: task.whenDate,
                    customTitle: store.tr("v30_phase_set_start_date"),
                    customClearTitle: store.tr("v30_phase_clear_start_date"),
                    accentTint: ThingsMacPalette.accentBlue
                ) { pickedMs in
                    guard let pickedMs else { return }
                    let todayDay = DateEpochConverter.epochDay(for: Date())
                    let pickedDay = DateEpochConverter.epochDay(forMillis: pickedMs)
                    var copy = task
                    copy.whenDate = pickedMs
                    copy.scheduleState = pickedDay <= todayDay ? .today : .upcoming
                    store.updateTask(copy)
                }
            }

            ThingsUtilityIconButton(
                systemImage: "slider.horizontal.3",
                tint: .secondary,
                helpText: store.tr("action_more_options")
            ) {
                store.openTaskModal(task.id)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .thingsCardSurface(cornerRadius: 10, isSelected: store.selectedTaskIds.contains(task.id))
        .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .thingsTimelineMouseTooltip(
            TimelineTaskTooltipFormatter.formatTodayTimelineTask(
                task: task,
                isBlocked: store.isTaskActivelyBlocked(task),
                language: store.preferences.language
            ),
            isSuppressed: unscheduledStartPopoverTaskId == task.id
        )
        .thingsInstantTaskTap(
            onSingleClick: { modifiers in
                store.handleRowClick(taskId: task.id, modifiers: modifiers)
            },
            onDoubleClick: {
                store.openTaskModal(task.id)
            }
        )
        .contextMenu {
            roadmapTaskContextMenu(for: task, includeDateShift: false)
        }
        .thingsTaskDraggable(store: store, task: task)
    }

    @ViewBuilder
    private func roadmapTaskContextMenu(for task: TaskItem, includeDateShift: Bool) -> some View {
        let latest = store.taskIncludingTrash(byId: task.id) ?? task
        let isBatch = store.selectedTaskIds.count > 1 && store.selectedTaskIds.contains(task.id)

        if isBatch {
            let allMilestones = store.selectedTaskIds.allSatisfy { id in
                store.allOpenTasks.first(where: { $0.id == id })?.isMilestone == true
            }
            Button(store.tr("action_complete")) {
                store.completeSelectedTasks()
            }
            Button(store.tr("view_today")) {
                store.scheduleSelectedTasks(.today)
            }
            Button(store.tr("v25_batch_someday")) {
                store.scheduleSelectedTasks(.someday)
            }
            Button(store.tr("v25_batch_pin")) {
                store.togglePinForSelectedTasks()
            }
            Button {
                store.toggleMilestoneForSelectedTasks()
            } label: {
                Label(
                    store.tr(allMilestones ? "v30_remove_milestone" : "v30_mark_as_milestone"),
                    systemImage: allMilestones ? "diamond.fill" : "diamond"
                )
            }
            Button(store.tr("v25_batch_duplicate")) {
                store.duplicateSelectedTasks()
            }
            Divider()
            Button(role: .destructive) {
                store.deleteSelectedTasks()
            } label: {
                Text(store.tr("action_delete"))
            }
        } else {
            Button {
                store.openTaskModal(task.id)
            } label: {
                Label(store.tr("v30_open_details_action"), systemImage: "slider.horizontal.3")
            }
            Button {
                store.toggleTaskCompletion(latest)
            } label: {
                Label(
                    latest.status == .completed ? store.tr("action_reopen_todo") : store.tr("action_complete"),
                    systemImage: latest.status == .completed ? "arrow.uturn.backward.circle" : "checkmark.circle"
                )
            }
            Divider()
            if includeDateShift {
                Button {
                    store.shiftTaskRoadmapDates(latest, deltaDays: -1)
                } label: {
                    Label(store.tr("v30_roadmap_shift_earlier"), systemImage: "arrow.left")
                }
                Button {
                    store.shiftTaskRoadmapDates(latest, deltaDays: 1)
                } label: {
                    Label(store.tr("v30_roadmap_shift_later"), systemImage: "arrow.right")
                }
            }
            Menu {
                Button {
                    var copy = latest
                    copy.scheduleState = .today
                    copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
                    store.updateTask(copy)
                } label: {
                    Label(store.tr("view_today"), systemImage: "sun.max.fill")
                }
                Button {
                    var copy = latest
                    copy.scheduleState = .thisEvening
                    copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
                    store.updateTask(copy)
                } label: {
                    Label(store.tr("view_this_evening"), systemImage: "moon.stars.fill")
                }
                Button {
                    var copy = latest
                    copy.scheduleState = .someday
                    copy.whenDate = nil
                    copy.deadline = nil
                    store.updateTask(copy)
                } label: {
                    Label(store.tr("view_someday"), systemImage: "cloud.fill")
                }
                Divider()
                Button {
                    if includeDateShift {
                        store.openTaskModal(task.id)
                    } else {
                        unscheduledStartPopoverTaskId = task.id
                    }
                } label: {
                    Label(store.tr("repeat_ends_pick_date"), systemImage: "calendar")
                }
            } label: {
                Label(store.tr("action_when"), systemImage: "calendar")
            }
            if !activeHeadings.isEmpty {
                Menu {
                    Button {
                        var copy = latest
                        copy.headingId = nil
                        store.updateTask(copy)
                    } label: {
                        if latest.headingId == nil {
                            Label(store.tr("v30_board_ungrouped_column"), systemImage: "checkmark")
                        } else {
                            Text(store.tr("v30_board_ungrouped_column"))
                        }
                    }
                    Divider()
                    ForEach(activeHeadings) { heading in
                        Button {
                            var copy = latest
                            copy.headingId = heading.id
                            store.updateTask(copy)
                        } label: {
                            if latest.headingId == heading.id {
                                Label(heading.title, systemImage: "checkmark")
                            } else {
                                Text(heading.title)
                            }
                        }
                    }
                } label: {
                    Label(store.tr("entity_heading"), systemImage: "flag.checkered")
                }
            }
            Divider()
            Button {
                store.setTasksPinned([latest.id], isPinned: !latest.isPinned)
            } label: {
                Label(
                    store.tr(latest.isPinned ? "v25_unpin_task" : "v25_pin_task"),
                    systemImage: latest.isPinned ? "pin.slash" : "pin"
                )
            }
            Button {
                store.setTasksMilestone([latest.id], isMilestone: !latest.isMilestone)
            } label: {
                Label(
                    store.tr(latest.isMilestone ? "v30_remove_milestone" : "v30_mark_as_milestone"),
                    systemImage: latest.isMilestone ? "diamond.fill" : "diamond"
                )
            }
            Button {
                _ = try? store.database.duplicateTask(task.id, copySuffix: store.tr("v25_copy_suffix"))
            } label: {
                Label(store.tr("action_duplicate_todo"), systemImage: "plus.square.on.square")
            }
            Divider()
            Button {
                store.cancelTask(latest)
            } label: {
                Label(
                    latest.status == .canceled ? store.tr("action_reopen_todo") : store.tr("action_cancel_todo"),
                    systemImage: latest.status == .canceled ? "arrow.uturn.backward.circle" : "xmark.circle"
                )
            }
            Button(role: .destructive) {
                store.deleteTask(latest)
            } label: {
                Label(store.tr("action_delete"), systemImage: "trash")
            }
        }
    }

    // MARK: - Configurable Vector PDF & 1-Click 2x Retina PNG Export

    private var sanitizedExportBaseName: String {
        let cleaned = project.title
            .replacingOccurrences(of: "/", with: "-")
            .replacingOccurrences(of: ":", with: "-")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? "Roadmap" : cleaned
    }

    @MainActor
    private func exportRoadmapPDFWithOptions(
        layout: ProjectRoadmapLayout,
        options: RoadmapPDFExportOptions
    ) {
        let panel = NSSavePanel()
        panel.title = store.tr("v30_roadmap_export_pdf")
        panel.canCreateDirectories = true
        panel.allowedContentTypes = [UTType.pdf]
        panel.nameFieldStringValue = "\(sanitizedExportBaseName)-Roadmap.pdf"

        guard panel.runModal() == .OK, let destinationURL = panel.url else { return }

        if let data = ProjectRoadmapExporter.exportRoadmapPDFData(
            project: project,
            headings: activeHeadings,
            layout: layout,
            options: options,
            generalLabel: store.tr("v30_board_ungrouped_column"),
            todayLabel: store.tr("view_today"),
            locale: store.preferences.language.locale
        ) {
            try? data.write(to: destinationURL, options: .atomic)
        }
    }

    @MainActor
    private func exportRoadmapPNGInteractively(layout: ProjectRoadmapLayout) {
        let panel = NSSavePanel()
        panel.title = store.tr("v30_roadmap_export_png")
        panel.canCreateDirectories = true
        panel.allowedContentTypes = [UTType.png]
        panel.nameFieldStringValue = "\(sanitizedExportBaseName)-Roadmap.png"

        guard panel.runModal() == .OK, let destinationURL = panel.url else { return }

        let rawSnapshot = RoadmapExportSnapshotView(
            store: store,
            project: project,
            headings: activeHeadings,
            layout: layout,
            dayColumnWidth: max(36.0, dayColumnWidth)
        )
        let proposed = rawSnapshot.exportCanvasSize
        let snapshotView = rawSnapshot.environment(\.colorScheme, .light)
        let exportedData = ProjectRoadmapExporter.renderViewToPNGData(snapshotView, scale: 2.0, proposedSize: proposed)
            ?? ProjectRoadmapExporter.exportRoadmapPNGData(
                project: project,
                headings: activeHeadings,
                layout: layout,
                generalLabel: store.tr("v30_board_ungrouped_column"),
                todayLabel: store.tr("view_today"),
                locale: store.preferences.language.locale,
                dayColumnWidth: max(36.0, dayColumnWidth),
                scale: 2.0
            )

        if let data = exportedData {
            try? data.write(to: destinationURL, options: .atomic)
        }
    }

    // MARK: - Date Formatting Helpers

    private func formattedWindowLabel(for layout: ProjectRoadmapLayout) -> String {
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.dateStyle = .medium
        let start = DateEpochConverter.date(fromEpochDay: layout.startEpochDay)
        let end = DateEpochConverter.date(fromEpochDay: layout.endEpochDay)
        return "\(df.string(from: start)) – \(df.string(from: end))"
    }

    private func dayNumber(for epochDay: Int) -> Int {
        Calendar.current.component(.day, from: DateEpochConverter.date(fromEpochDay: epochDay))
    }

    private func weekdayInitial(for epochDay: Int) -> String {
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.setLocalizedDateFormatFromTemplate("EEEEE")
        return df.string(from: DateEpochConverter.date(fromEpochDay: epochDay))
    }

    private func monthHeaderChip(for epochDay: Int, isFirstColumn: Bool) -> String {
        let date = DateEpochConverter.date(fromEpochDay: epochDay)
        let dom = Calendar.current.component(.day, from: date)
        guard isFirstColumn || dom == 1 else { return "" }
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.setLocalizedDateFormatFromTemplate("MMM")
        return df.string(from: date)
    }

    private func isWeekendDay(_ epochDay: Int) -> Bool {
        let iso = DateEpochConverter.isoWeekday(for: DateEpochConverter.date(fromEpochDay: epochDay))
        return iso == 6 || iso == 7
    }
}

// MARK: - Soft Bento PDF Export Configuration Dialog

struct RoadmapPDFExportSheetView: View {
    @ObservedObject var store: ThingsbookStore
    let project: Project
    let headings: [Heading]
    let layout: ProjectRoadmapLayout
    @Binding var isPresented: Bool
    let onConfirmExport: (RoadmapPDFExportOptions) -> Void

    @State private var selectedSectionIds: Set<String>
    @State private var timelinePreset: RoadmapPDFTimelinePreset = .fullProject
    @State private var customStartDate: Date
    @State private var customEndDate: Date
    @State private var orientation: RoadmapPDFOrientation = .landscape

    init(
        store: ThingsbookStore,
        project: Project,
        headings: [Heading],
        layout: ProjectRoadmapLayout,
        isPresented: Binding<Bool>,
        onConfirmExport: @escaping (RoadmapPDFExportOptions) -> Void
    ) {
        self.store = store
        self.project = project
        self.headings = headings
        self.layout = layout
        self._isPresented = isPresented
        self.onConfirmExport = onConfirmExport

        let defaults = RoadmapPDFExportOptions.defaultOptions(for: layout, headings: headings, orientation: .landscape)
        self._selectedSectionIds = State(initialValue: defaults.selectedSectionIds)
        self._customStartDate = State(initialValue: DateEpochConverter.date(fromEpochDay: layout.startEpochDay))
        self._customEndDate = State(initialValue: DateEpochConverter.date(fromEpochDay: layout.endEpochDay))
    }

    private var hasGeneralSection: Bool {
        !layout.generalTaskBars.isEmpty
    }

    private var allAvailableSectionIds: Set<String> {
        var ids = Set(headings.map(\.id))
        if hasGeneralSection {
            ids.insert(RoadmapPDFExportOptions.generalSectionId)
        }
        return ids
    }

    private var allSectionsSelected: Bool {
        let all = allAvailableSectionIds
        return !all.isEmpty && all.isSubset(of: selectedSectionIds)
    }

    private var canExport: Bool {
        allAvailableSectionIds.isEmpty || !selectedSectionIds.isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            // Sheet Header
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(ThingsMacPalette.accentBlue.opacity(0.14))
                        .frame(width: 34, height: 34)
                    Image(systemName: "doc.richtext")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(store.tr("v30_pdf_export_dialog_title"))
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.primary)
                    Text(project.title)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer()
            }

            // 1. Project Phases Selection
            if !allAvailableSectionIds.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(store.tr("v30_pdf_phases_label"))
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundStyle(.secondary)

                        Spacer()

                        Button {
                            if allSectionsSelected {
                                selectedSectionIds.removeAll()
                            } else {
                                selectedSectionIds = allAvailableSectionIds
                            }
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: allSectionsSelected ? "checkmark.circle.fill" : "circle")
                                    .font(.system(size: 11, weight: .semibold))
                                Text(store.tr("v30_pdf_select_all_phases"))
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .fixedSize(horizontal: true, vertical: false)
                            }
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                        }
                        .buttonStyle(.plain)
                        .fixedSize(horizontal: true, vertical: false)
                    }

                    ScrollView {
                        VStack(alignment: .leading, spacing: 6) {
                            if hasGeneralSection {
                                phaseSelectionRow(
                                    sectionId: RoadmapPDFExportOptions.generalSectionId,
                                    title: store.tr("v30_board_ungrouped_column"),
                                    taskCount: layout.generalTaskBars.count
                                )
                            }

                            ForEach(headings) { heading in
                                let count = (layout.taskBarsByHeading[heading.id] ?? []).count
                                phaseSelectionRow(
                                    sectionId: heading.id,
                                    title: heading.title,
                                    taskCount: count
                                )
                            }
                        }
                        .padding(10)
                    }
                    .frame(maxHeight: 176)
                    .background(
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .fill(ThingsMacPalette.subtleContainer)
                            .overlay(
                                RoundedRectangle(cornerRadius: 11, style: .continuous)
                                    .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.85)
                            )
                    )
                }
            }

            // 2. Timeline Range Presets & Custom DatePickers
            VStack(alignment: .leading, spacing: 9) {
                Text(store.tr("v30_pdf_timeline_label"))
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(.secondary)

                ThingsFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                    ForEach(RoadmapPDFTimelinePreset.allCases) { preset in
                        let isSelected = timelinePreset == preset
                        Button {
                            applyTimelinePreset(preset)
                        } label: {
                            Text(label(for: preset))
                                .font(.system(size: 12, weight: isSelected ? .semibold : .medium))
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .foregroundStyle(isSelected ? .white : .primary)
                                .padding(.horizontal, 13)
                                .padding(.vertical, 7)
                                .background(
                                    RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                                        .fill(isSelected ? ThingsMacPalette.accentBlue : ThingsMacPalette.subtleContainer)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                                        .strokeBorder(
                                            isSelected ? ThingsMacPalette.accentBlue : Color.primary.opacity(0.08),
                                            lineWidth: 0.85
                                        )
                                )
                                .contentShape(RoundedRectangle(cornerRadius: 8.5, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .fixedSize(horizontal: true, vertical: false)
                    }
                }

                HStack(spacing: 12) {
                    HStack(spacing: 6) {
                        Image(systemName: "calendar")
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                        DatePicker(
                            "",
                            selection: Binding(
                                get: { customStartDate },
                                set: { nextStart in
                                    timelinePreset = .custom
                                    customStartDate = nextStart
                                    if customEndDate < nextStart {
                                        customEndDate = nextStart
                                    }
                                }
                            ),
                            displayedComponents: [.date]
                        )
                        .labelsHidden()
                        .datePickerStyle(.compact)
                    }

                    Text("–")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.secondary)

                    HStack(spacing: 6) {
                        Image(systemName: "flag")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                        DatePicker(
                            "",
                            selection: Binding(
                                get: { customEndDate },
                                set: { nextEnd in
                                    timelinePreset = .custom
                                    customEndDate = nextEnd
                                    if customStartDate > nextEnd {
                                        customStartDate = nextEnd
                                    }
                                }
                            ),
                            displayedComponents: [.date]
                        )
                        .labelsHidden()
                        .datePickerStyle(.compact)
                    }

                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(ThingsMacPalette.subtleContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.85)
                        )
                )
            }

            // 3. Page Orientation (2-Card Soft Bento Picker)
            VStack(alignment: .leading, spacing: 8) {
                Text(store.tr("v30_pdf_orientation_label"))
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(.secondary)

                HStack(spacing: 10) {
                    orientationCard(.landscape)
                    orientationCard(.portrait)
                }
            }

            Divider()

            // 4. Sheet Footer
            HStack(spacing: 10) {
                Spacer()

                Button(store.tr("action_cancel")) {
                    isPresented = false
                }
                .fixedSize(horizontal: true, vertical: false)
                .keyboardShortcut(.cancelAction)

                Button {
                    let sDay = DateEpochConverter.epochDay(for: customStartDate)
                    let eDay = DateEpochConverter.epochDay(for: customEndDate)
                    let effectiveSections = selectedSectionIds.isEmpty ? allAvailableSectionIds : selectedSectionIds
                    let options = RoadmapPDFExportOptions(
                        selectedSectionIds: effectiveSections,
                        startEpochDay: min(sDay, eDay),
                        endEpochDay: max(sDay, eDay),
                        orientation: orientation
                    )
                    isPresented = false
                    onConfirmExport(options)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 11.5, weight: .semibold))
                        Text(store.tr("v30_roadmap_export_pdf"))
                            .font(.system(size: 12.5, weight: .semibold))
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    .padding(.horizontal, 6)
                }
                .buttonStyle(.borderedProminent)
                .tint(ThingsMacPalette.accentBlue)
                .fixedSize(horizontal: true, vertical: false)
                .disabled(!canExport)
                .keyboardShortcut(.defaultAction)
            }
        }
        .padding(20)
        .frame(minWidth: 560, idealWidth: 580)
        .background(ThingsMacPalette.canvasBackground)
    }

    private func phaseSelectionRow(
        sectionId: String,
        title: String,
        taskCount: Int
    ) -> some View {
        let isChecked = selectedSectionIds.contains(sectionId)
        return Button {
            if isChecked {
                selectedSectionIds.remove(sectionId)
            } else {
                selectedSectionIds.insert(sectionId)
            }
        } label: {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: isChecked ? "checkmark.square.fill" : "square")
                    .font(.system(size: 13.5, weight: .semibold))
                    .foregroundStyle(isChecked ? ThingsMacPalette.accentBlue : .secondary)
                    .padding(.top, 1)

                Capsule(style: .continuous)
                    .fill(ThingsMacPalette.inboxBlue)
                    .frame(width: 3.5, height: 15)
                    .padding(.top, 1)

                Text(title)
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(nil)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .layoutPriority(1)

                Spacer(minLength: 6)

                Text("\(taskCount)")
                    .font(.system(size: 10.5, weight: .bold, design: .rounded))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 6.5)
                    .padding(.vertical, 2)
                    .background(Capsule().fill(Color.primary.opacity(0.06)))
                    .fixedSize()
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(isChecked ? ThingsMacPalette.cardSurface : Color.clear)
            )
            .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func orientationCard(_ mode: RoadmapPDFOrientation) -> some View {
        let isSelected = orientation == mode
        let titleKey = mode == .landscape ? "v30_pdf_orientation_landscape" : "v30_pdf_orientation_portrait"
        let sizeSubtitle = mode == .landscape ? "A4 · 842 × 595 pt" : "A4 · 595 × 842 pt"
        let iconW: CGFloat = mode == .landscape ? 28 : 20
        let iconH: CGFloat = mode == .landscape ? 20 : 28

        return Button {
            withAnimation(.spring(response: 0.22, dampingFraction: 0.85)) {
                orientation = mode
            }
        } label: {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 3.5, style: .continuous)
                        .fill(isSelected ? ThingsMacPalette.accentBlue.opacity(0.14) : Color.primary.opacity(0.04))
                        .frame(width: iconW, height: iconH)
                        .overlay(
                            RoundedRectangle(cornerRadius: 3.5, style: .continuous)
                                .strokeBorder(
                                    isSelected ? ThingsMacPalette.accentBlue : Color.secondary.opacity(0.55),
                                    lineWidth: 1.5
                                )
                        )
                    VStack(alignment: .leading, spacing: 2.5) {
                        Capsule()
                            .fill(isSelected ? ThingsMacPalette.accentBlue : Color.secondary.opacity(0.5))
                            .frame(width: iconW * 0.55, height: 2)
                        Capsule()
                            .fill(isSelected ? ThingsMacPalette.inboxBlue : Color.secondary.opacity(0.35))
                            .frame(width: iconW * 0.4, height: 2)
                    }
                }
                .frame(width: 32, height: 32)

                VStack(alignment: .leading, spacing: 2) {
                    Text(store.tr(titleKey))
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(isSelected ? ThingsMacPalette.accentBlue : .primary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(sizeSubtitle)
                        .font(.system(size: 10.5, weight: .medium, design: .rounded))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .fill(isSelected ? ThingsMacPalette.cardSurface : ThingsMacPalette.subtleContainer)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .strokeBorder(
                        isSelected ? ThingsMacPalette.accentBlue : Color.primary.opacity(0.08),
                        lineWidth: isSelected ? 1.4 : 0.85
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func label(for preset: RoadmapPDFTimelinePreset) -> String {
        switch preset {
        case .fullProject:
            return store.tr("v30_pdf_timeline_auto")
        case .fromToday:
            return store.tr("v30_pdf_timeline_from_today")
        case .next30Days:
            return store.tr("v30_pdf_timeline_next_30d")
        case .custom:
            return store.tr("v30_pdf_timeline_custom")
        }
    }

    private func applyTimelinePreset(_ preset: RoadmapPDFTimelinePreset) {
        timelinePreset = preset
        switch preset {
        case .fullProject:
            customStartDate = DateEpochConverter.date(fromEpochDay: layout.startEpochDay)
            customEndDate = DateEpochConverter.date(fromEpochDay: layout.endEpochDay)
        case .fromToday:
            let sDay = layout.todayEpochDay
            let eDay = max(sDay + 14, layout.endEpochDay)
            customStartDate = DateEpochConverter.date(fromEpochDay: sDay)
            customEndDate = DateEpochConverter.date(fromEpochDay: eDay)
        case .next30Days:
            let sDay = layout.todayEpochDay
            let eDay = sDay + 30
            customStartDate = DateEpochConverter.date(fromEpochDay: sDay)
            customEndDate = DateEpochConverter.date(fromEpochDay: eDay)
        case .custom:
            break
        }
    }
}

// MARK: - Unclipped Full-Width Snapshot View for 2x Retina PNG Export

struct RoadmapExportSnapshotView: View {
    @ObservedObject var store: ThingsbookStore
    let project: Project
    let headings: [Heading]
    let layout: ProjectRoadmapLayout
    let dayColumnWidth: CGFloat

    private let labelColumnWidth: CGFloat = 272
    private let headerHeight: CGFloat = 50
    private let phaseRowHeight: CGFloat = 52
    private let standardRowHeight: CGFloat = 34
    private let rowSpacing: CGFloat = 4

    private func rowHeight(for row: RoadmapTimelineRow) -> CGFloat {
        switch row {
        case .phaseHeader:
            return phaseRowHeight
        case .generalHeader, .task:
            return standardRowHeight
        }
    }

    private func computeTimelineHeight(for displayRows: [RoadmapTimelineRow]) -> CGFloat {
        let rowsSum = displayRows.reduce(CGFloat(0)) { $0 + rowHeight(for: $1) }
        let spacingsSum = displayRows.isEmpty ? 0 : CGFloat(displayRows.count) * rowSpacing
        return headerHeight + max(34, rowsSum + spacingsSum + 12)
    }

    private var rows: [RoadmapTimelineRow] {
        var out: [RoadmapTimelineRow] = []
        if !layout.generalTaskBars.isEmpty {
            out.append(.generalHeader)
            for b in layout.generalTaskBars {
                out.append(.task(b))
            }
        }
        for heading in headings {
            let phaseBar = layout.phaseBar(for: heading.id)
            let children = layout.taskBarsByHeading[heading.id] ?? []
            if phaseBar != nil || !children.isEmpty {
                out.append(.phaseHeader(heading, phaseBar))
                for b in children {
                    out.append(.task(b))
                }
            }
        }
        return out
    }

    var exportCanvasSize: CGSize {
        let displayRows = rows
        let gridWidth = CGFloat(max(21, layout.totalDays)) * dayColumnWidth
        let timelineHeight = computeTimelineHeight(for: displayRows)
        return CGSize(
            width: labelColumnWidth + gridWidth + 56,
            height: 76 + timelineHeight + 28
        )
    }

    var body: some View {
        let displayRows = rows
        let gridWidth = CGFloat(max(21, layout.totalDays)) * dayColumnWidth
        let timelineHeight = computeTimelineHeight(for: displayRows)

        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                ProjectProgressRing(
                    progress: project.progress,
                    size: 18,
                    tint: ThingsMacPalette.accentBlue,
                    isCompleted: project.status == .completed,
                    showFraction: project.totalTaskCount > 0,
                    completedCount: project.completedTaskCount,
                    totalCount: project.totalTaskCount
                )

                Text(project.title)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.black)

                Spacer()

                Text(formattedWindowRange)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.secondary)
            }

            HStack(alignment: .top, spacing: 0) {
                // Full unclipped left label column
                VStack(alignment: .leading, spacing: rowSpacing) {
                    Color.clear
                        .frame(width: labelColumnWidth, height: headerHeight)

                    ForEach(displayRows) { row in
                        let rHeight = rowHeight(for: row)
                        switch row {
                        case .generalHeader:
                            Text(store.tr("v30_board_ungrouped_column"))
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(.black)
                                .padding(.horizontal, 12)
                                .frame(width: labelColumnWidth, height: rHeight, alignment: .leading)
                        case .phaseHeader(let h, _):
                            HStack(alignment: .top, spacing: 6) {
                                Capsule()
                                    .fill(ThingsMacPalette.inboxBlue)
                                    .frame(width: 3.5, height: 14)
                                    .padding(.top, 2)
                                Text(h.title)
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundStyle(.black)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)
                                    .fixedSize(horizontal: false, vertical: true)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 12)
                            .frame(width: labelColumnWidth, height: rHeight, alignment: .leading)
                        case .task(let bar):
                            let isMs = bar.isMilestone || bar.task.isMilestone
                            let titleStr = bar.task.title.isEmpty ? store.tr("macos_untitled_todo") : bar.task.title
                            Text(isMs ? "◆ \(titleStr)" : titleStr)
                                .font(.system(size: 11.5, weight: isMs ? .bold : .medium))
                                .foregroundStyle(isMs ? Color(red: 0.49, green: 0.23, blue: 0.93) : .black.opacity(0.85))
                                .lineLimit(1)
                                .padding(.leading, 22)
                                .padding(.trailing, 8)
                                .frame(width: labelColumnWidth, height: rHeight, alignment: .leading)
                        }
                    }
                }
                .padding(.bottom, 12)
                .frame(width: labelColumnWidth, height: timelineHeight, alignment: .topLeading)
                .background(Color(white: 0.97))

                // Full unclipped multi-week timeline grid
                ZStack(alignment: .topLeading) {
                    HStack(spacing: 0) {
                        ForEach(0..<layout.totalDays, id: \.self) { offset in
                            let epochDay = layout.startEpochDay + offset
                            let isToday = epochDay == layout.todayEpochDay
                            let date = DateEpochConverter.date(fromEpochDay: epochDay)
                            let dom = Calendar.current.component(.day, from: date)

                            VStack(spacing: 0) {
                                VStack(spacing: 1) {
                                    Text((offset == 0 || dom == 1) ? monthShort(date) : "")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundStyle(isToday ? ThingsMacPalette.accentBlue : .secondary)
                                        .frame(height: 13)
                                    Text("\(dom)")
                                        .font(.system(size: 11, weight: isToday ? .bold : .medium, design: .rounded))
                                        .foregroundStyle(isToday ? ThingsMacPalette.accentBlue : .black.opacity(0.8))
                                }
                                .frame(width: dayColumnWidth, height: headerHeight)

                                Rectangle()
                                    .fill(isToday ? ThingsMacPalette.accentBlue.opacity(0.07) : Color.clear)
                                    .frame(width: dayColumnWidth, height: max(34, timelineHeight - headerHeight))
                                    .overlay(alignment: .leading) {
                                        Rectangle()
                                            .fill(Color.black.opacity(0.06))
                                            .frame(width: 0.75)
                                    }
                                    .overlay {
                                        if isToday {
                                            Rectangle()
                                                .fill(ThingsMacPalette.accentBlue.opacity(0.65))
                                                .frame(width: 1.5)
                                        }
                                    }
                            }
                        }
                    }

                    Canvas { context, _ in
                        var midYByTaskId: [String: CGFloat] = [:]
                        var cursorY: CGFloat = headerHeight + rowSpacing
                        for row in displayRows {
                            let rH = rowHeight(for: row)
                            if case .task(let tb) = row {
                                midYByTaskId[tb.id] = cursorY + rH * 0.5
                            }
                            cursorY += rH + rowSpacing
                        }
                        let barById = Dictionary(uniqueKeysWithValues: layout.allTaskBars.map { ($0.id, $0) })

                        for edge in layout.dependencyEdges {
                            guard let fromBar = barById[edge.fromTaskId],
                                  let toBar = barById[edge.toTaskId],
                                  let y0 = midYByTaskId[edge.fromTaskId],
                                  let y1 = midYByTaskId[edge.toTaskId] else {
                                continue
                            }
                            let cFromEnd = min(layout.endEpochDay, max(layout.startEpochDay, fromBar.endEpochDay))
                            let cToStart = max(layout.startEpochDay, min(layout.endEpochDay, toBar.startEpochDay))
                            let x0: CGFloat = (fromBar.isMilestone || fromBar.task.isMilestone)
                                ? ((CGFloat(cFromEnd - layout.startEpochDay) + 0.5) * dayColumnWidth + 10)
                                : (CGFloat(cFromEnd - layout.startEpochDay + 1) * dayColumnWidth - 3)
                            let x1: CGFloat = (toBar.isMilestone || toBar.task.isMilestone)
                                ? ((CGFloat(cToStart - layout.startEpochDay) + 0.5) * dayColumnWidth - 10)
                                : (CGFloat(cToStart - layout.startEpochDay) * dayColumnWidth + 3)
                            let p0 = CGPoint(x: x0, y: y0)
                            let p3 = CGPoint(x: x1, y: y1)
                            let dx = max(22.0, abs(p3.x - p0.x) * 0.5)

                            var path = Path()
                            path.move(to: p0)
                            path.addCurve(
                                to: p3,
                                control1: CGPoint(x: p0.x + dx, y: p0.y),
                                control2: CGPoint(x: p3.x - dx, y: p3.y)
                            )
                            let strokeColor = edge.isActivelyBlocking
                                ? ThingsMacPalette.somedayAmber
                                : ThingsMacPalette.accentBlue.opacity(0.45)
                            context.stroke(path, with: .color(strokeColor), lineWidth: 1.6)

                            var arrow = Path()
                            arrow.move(to: CGPoint(x: p3.x - 6, y: p3.y - 3.5))
                            arrow.addLine(to: p3)
                            arrow.addLine(to: CGPoint(x: p3.x - 6, y: p3.y + 3.5))
                            context.stroke(arrow, with: .color(strokeColor), lineWidth: 1.6)
                        }
                    }
                    .frame(width: gridWidth, height: timelineHeight)

                    VStack(alignment: .leading, spacing: rowSpacing) {
                        Color.clear.frame(width: gridWidth, height: headerHeight)

                        ForEach(displayRows) { row in
                            let rHeight = rowHeight(for: row)
                            ZStack(alignment: .leading) {
                                Color.clear.frame(width: gridWidth, height: rHeight)
                                switch row {
                                case .generalHeader:
                                    EmptyView()
                                case .phaseHeader(let h, let maybeBar):
                                    if let pBar = maybeBar,
                                       pBar.endEpochDay >= layout.startEpochDay,
                                       pBar.startEpochDay <= layout.endEpochDay {
                                        let cStart = max(layout.startEpochDay, pBar.startEpochDay)
                                        let cEnd = min(layout.endEpochDay, pBar.endEpochDay)
                                        let cSpan = max(1, cEnd - cStart + 1)
                                        let x = CGFloat(cStart - layout.startEpochDay) * dayColumnWidth + 2
                                        let w = max(dayColumnWidth - 4, CGFloat(cSpan) * dayColumnWidth - 4)
                                        let estTitleW = CGFloat(h.title.count) * 7.2 + 34
                                        let fitsInside = w >= estTitleW
                                        let placeLeading = !fitsInside && (gridWidth - (x + w) < estTitleW) && (x >= estTitleW)

                                        HStack(spacing: 5) {
                                            if fitsInside {
                                                Text(h.title)
                                                    .font(.system(size: 11, weight: .bold))
                                                    .foregroundStyle(.white)
                                                    .lineLimit(1)
                                            }
                                            Spacer(minLength: 0)
                                        }
                                        .padding(.horizontal, fitsInside ? 8 : 0)
                                        .frame(width: w, height: 24)
                                        .background(
                                            Capsule(style: .continuous)
                                                .fill(
                                                    LinearGradient(
                                                        colors: [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                                                        startPoint: .leading,
                                                        endPoint: .trailing
                                                    )
                                                )
                                        )
                                        .overlay(alignment: placeLeading ? .leading : .trailing) {
                                            if !fitsInside {
                                                Text(h.title)
                                                    .font(.system(size: 11, weight: .bold))
                                                    .foregroundStyle(ThingsMacPalette.accentBlue)
                                                    .lineLimit(1)
                                                    .fixedSize(horizontal: true, vertical: false)
                                                    .alignmentGuide(placeLeading ? .leading : .trailing) { d in
                                                        placeLeading ? (d[.trailing] + 6) : (d[.leading] - 6)
                                                    }
                                            }
                                        }
                                        .offset(x: x)
                                    }
                                case .task(let tBar):
                                    if tBar.endEpochDay >= layout.startEpochDay && tBar.startEpochDay <= layout.endEpochDay {
                                        let titleStr = tBar.task.title.isEmpty ? store.tr("macos_untitled_todo") : tBar.task.title
                                        if tBar.isMilestone || tBar.task.isMilestone {
                                            let royalViolet = Color(red: 0.49, green: 0.23, blue: 0.93)
                                            let cDay = min(layout.endEpochDay, max(layout.startEpochDay, tBar.startEpochDay))
                                            let colCenterX = (CGFloat(cDay - layout.startEpochDay) + 0.5) * dayColumnWidth
                                            HStack(spacing: 5) {
                                                RoundedRectangle(cornerRadius: 3, style: .continuous)
                                                    .fill(royalViolet)
                                                    .frame(width: 14, height: 14)
                                                    .overlay(
                                                        RoundedRectangle(cornerRadius: 3, style: .continuous)
                                                            .strokeBorder(Color.white, lineWidth: 1.2)
                                                    )
                                                    .rotationEffect(.degrees(45))
                                                    .frame(width: 20, height: 24)

                                                Text("◆ \(titleStr)")
                                                    .font(.system(size: 11, weight: .bold))
                                                    .foregroundStyle(royalViolet)
                                                    .lineLimit(1)
                                                    .fixedSize(horizontal: true, vertical: false)
                                                    .padding(.horizontal, 7)
                                                    .padding(.vertical, 3)
                                                    .background(
                                                        Capsule(style: .continuous)
                                                            .fill(Color.white)
                                                            .overlay(
                                                                Capsule(style: .continuous)
                                                                    .strokeBorder(royalViolet.opacity(0.55), lineWidth: 0.95)
                                                            )
                                                    )
                                            }
                                            .fixedSize(horizontal: true, vertical: false)
                                            .offset(x: colCenterX - 10)
                                        } else {
                                            let cStart = max(layout.startEpochDay, tBar.startEpochDay)
                                            let cEnd = min(layout.endEpochDay, tBar.endEpochDay)
                                            let cSpan = max(1, cEnd - cStart + 1)
                                            let x = CGFloat(cStart - layout.startEpochDay) * dayColumnWidth + 3
                                            let w = max(dayColumnWidth - 6, CGFloat(cSpan) * dayColumnWidth - 6)
                                            HStack(spacing: 5) {
                                                Text(titleStr)
                                                    .font(.system(size: 11, weight: .semibold))
                                                    .foregroundStyle(.black.opacity(0.88))
                                                    .lineLimit(1)
                                                Spacer(minLength: 0)
                                            }
                                            .padding(.horizontal, 8)
                                            .frame(width: w, height: 24)
                                            .background(
                                                RoundedRectangle(cornerRadius: 7, style: .continuous)
                                                    .fill(Color.white)
                                                    .overlay(
                                                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                                                            .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.5), lineWidth: 1.0)
                                                    )
                                            )
                                            .offset(x: x)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .frame(width: gridWidth, height: timelineHeight)
            }
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(Color.black.opacity(0.10), lineWidth: 0.9)
            )
        }
        .padding(24)
        .background(Color.white)
    }

    private var formattedWindowRange: String {
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.dateStyle = .medium
        let s = DateEpochConverter.date(fromEpochDay: layout.startEpochDay)
        let e = DateEpochConverter.date(fromEpochDay: layout.endEpochDay)
        return "\(df.string(from: s)) – \(df.string(from: e))"
    }

    private func monthShort(_ date: Date) -> String {
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.setLocalizedDateFormatFromTemplate("MMM")
        return df.string(from: date)
    }
}

// MARK: - v3.1 Soft Bento Area Mode Switcher (List | Portfolio Timeline)

public struct AreaModeSegmentedSwitcher: View {
    @ObservedObject var store: ThingsbookStore
    let area: Area

    public init(store: ThingsbookStore, area: Area) {
        self.store = store
        self.area = area
    }

    private var activeMode: ProjectViewMode {
        store.areaViewMode(for: area.id)
    }

    private let supportedModes: [ProjectViewMode] = [.list, .roadmap]

    private func label(for mode: ProjectViewMode) -> String {
        switch mode {
        case .roadmap:
            return store.tr("v31_area_portfolio_timeline")
        default:
            return store.tr("v30_project_mode_list")
        }
    }

    private func icon(for mode: ProjectViewMode) -> String {
        switch mode {
        case .roadmap:
            return "calendar.day.timeline.left"
        default:
            return "list.bullet"
        }
    }

    public var body: some View {
        HStack(spacing: 3) {
            ForEach(supportedModes) { mode in
                let isSelected = activeMode == mode
                Button {
                    withAnimation(.spring(response: 0.24, dampingFraction: 0.84)) {
                        store.setAreaViewMode(area.id, mode: mode)
                    }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: icon(for: mode))
                            .font(.system(size: 11, weight: .semibold))
                        Text(label(for: mode))
                            .font(.system(size: 12, weight: isSelected ? .semibold : .medium))
                            .lineLimit(1)
                    }
                    .fixedSize(horizontal: true, vertical: false)
                    .foregroundStyle(isSelected ? ThingsMacPalette.accentBlue : .secondary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .fill(isSelected ? ThingsMacPalette.cardSurface : Color.clear)
                            .shadow(
                                color: Color.black.opacity(isSelected ? 0.07 : 0.0),
                                radius: 4,
                                y: 1
                            )
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .strokeBorder(
                                isSelected ? ThingsMacPalette.accentBlue.opacity(0.32) : Color.clear,
                                lineWidth: 0.85
                            )
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 7.5, style: .continuous))
                }
                .buttonStyle(.plain)
                .thingsTooltip(label(for: mode))
            }
        }
        .padding(3)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer)
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.8)
                )
        )
        .fixedSize(horizontal: true, vertical: false)
    }
}

// MARK: - v3.1 Soft Bento 7-Day Workload Forecast Card

public struct SevenDayWorkloadStripCard: View {
    @ObservedObject var store: ThingsbookStore
    public let forecast: [DayWorkloadSummary]
    public var compact: Bool = false

    public init(store: ThingsbookStore, forecast: [DayWorkloadSummary], compact: Bool = false) {
        self.store = store
        self.forecast = forecast
        self.compact = compact
    }

    public var body: some View {
        let visibleDays = Array(forecast.prefix(7))
        let maxOverload = visibleDays.filter(\.isOverloaded).map(\.overloadedByMinutes).max() ?? 0
        let statusTint = maxOverload > 0 ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.logbookGreen
        let statusText = maxOverload > 0
            ? store.trf("v31_workload_overloaded_short", TaskItem.formatCompactDuration(maxOverload))
            : store.tr("v31_workload_balanced")
        let statusTooltip = maxOverload > 0
            ? store.trf("v31_workload_overloaded_by", TaskItem.formatCompactDuration(maxOverload))
            : store.tr("v31_workload_balanced")

        VStack(alignment: .leading, spacing: compact ? 8 : 10) {
            HStack(spacing: 6) {
                Image(systemName: "chart.bar.xaxis")
                    .font(.system(size: compact ? 11.5 : 12.5, weight: .bold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                Text(store.tr("v31_workload_7day_title"))
                    .font(.system(size: compact ? 12 : 13, weight: .bold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer(minLength: 4)

                Text(statusText)
                    .font(.system(size: 10.5, weight: .bold, design: .rounded))
                    .foregroundStyle(statusTint)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2.5)
                    .background(
                        Capsule(style: .continuous)
                            .fill(statusTint.opacity(0.13))
                    )
                    .lineLimit(1)
                    .thingsTooltip(statusTooltip)
            }

            HStack(alignment: .bottom, spacing: compact ? 5 : 8) {
                ForEach(Array(visibleDays.enumerated()), id: \.element.epochDay) { idx, day in
                    workloadDayColumn(day: day, isToday: idx == 0)
                }
            }
        }
        .padding(.horizontal, compact ? 12 : 14)
        .padding(.vertical, compact ? 10 : 12)
        .thingsCardSurface(cornerRadius: compact ? 12 : 14)
    }

    private func workloadDayColumn(day: DayWorkloadSummary, isToday: Bool) -> some View {
        let maxBarHeight: CGFloat = compact ? 36 : 44
        let clampedRatio = CGFloat(min(1.0, max(0.0, day.loadFraction)))
        let minFill: CGFloat = day.totalUsedMinutes > 0 ? 4 : 2
        let fillHeight = max(minFill, maxBarHeight * clampedRatio)
        let barColor: Color = {
            if day.isOverloaded { return ThingsMacPalette.deadlineCrimson }
            if day.loadFraction >= 0.8 { return ThingsMacPalette.somedayAmber }
            if day.totalUsedMinutes > 0 { return ThingsMacPalette.accentBlue }
            return Color.primary.opacity(0.10)
        }()
        let plannedLabel = day.plannedTaskMinutes > 0
            ? TaskItem.formatCompactDuration(day.plannedTaskMinutes)
            : "0m"
        let weekdayStr = shortWeekday(for: day.epochDay)
        let tooltipText: String = {
            if day.isOverloaded {
                return "\(weekdayStr): \(store.trf("v31_workload_overloaded_by", TaskItem.formatCompactDuration(day.overloadedByMinutes)))"
            }
            let plannedStr = TaskItem.formatCompactDuration(day.plannedTaskMinutes)
            let freeStr = TaskItem.formatCompactDuration(day.freeWorkdayMinutes)
            return "\(weekdayStr): \(store.trf("v30_today_capacity_summary", plannedStr, freeStr))"
        }()

        return VStack(spacing: 3) {
            Text(plannedLabel)
                .font(.system(size: compact ? 9.0 : 9.5, weight: day.isOverloaded ? .bold : .medium, design: .rounded))
                .foregroundStyle(day.isOverloaded ? ThingsMacPalette.deadlineCrimson : .secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.75)

            ZStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
                    .frame(height: maxBarHeight)

                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(barColor)
                    .frame(height: fillHeight)
            }
            .frame(maxWidth: .infinity)

            Text(weekdayStr)
                .font(.system(size: compact ? 9.5 : 10, weight: isToday ? .heavy : .medium))
                .foregroundStyle(isToday ? ThingsMacPalette.accentBlue : .secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
        .thingsTooltip(tooltipText)
    }

    private func shortWeekday(for epochDay: Int) -> String {
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.setLocalizedDateFormatFromTemplate("EE")
        let raw = df.string(from: DateEpochConverter.date(fromEpochDay: epochDay))
        return String(raw.prefix(3))
    }
}

// MARK: - v3.1 Area Portfolio Timeline Workspace

public struct AreaPortfolioRoadmapWorkspaceView: View {
    @ObservedObject var store: ThingsbookStore
    public let area: Area

    @State private var dayColumnWidth: CGFloat = 38.0
    @State private var expandedProjectIds: Set<String> = []
    @State private var isPDFExportSheetPresented: Bool = false
    @State private var activePhaseDrag: (id: String, deltaDays: Int)? = nil
    @State private var activeTaskDrag: (projectId: String, taskId: String, deltaDays: Int)? = nil
    @State private var armedPhaseDragId: String? = nil
    @State private var armedTaskDragId: String? = nil

    private let labelColumnWidth: CGFloat = 276
    private let headerHeight: CGFloat = 50
    private let projectSummaryRowHeight: CGFloat = 48
    private let subPhaseRowHeight: CGFloat = 40
    private let subTaskRowHeight: CGFloat = 35
    private let rowSpacing: CGFloat = 4

    public init(store: ThingsbookStore, area: Area) {
        self.store = store
        self.area = area
    }

    private enum PortfolioDisplayRow: Identifiable {
        case projectSummary(AreaPortfolioProjectLane, isExpanded: Bool)
        case subPhase(projectId: String, heading: Heading?, phaseBar: RoadmapPhaseBar?, title: String)
        case subTask(projectId: String, taskBar: RoadmapTaskBar, projectLayout: ProjectRoadmapLayout)

        var id: String {
            switch self {
            case .projectSummary(let lane, _):
                return "proj_\(lane.projectId)"
            case .subPhase(let pid, let h, _, _):
                return "phase_\(pid)_\(h?.id ?? "__general__")"
            case .subTask(let pid, let tb, _):
                return "task_\(pid)_\(tb.id)"
            }
        }
    }

    private func rowHeight(for row: PortfolioDisplayRow) -> CGFloat {
        switch row {
        case .projectSummary:
            return projectSummaryRowHeight
        case .subPhase:
            return subPhaseRowHeight
        case .subTask:
            return subTaskRowHeight
        }
    }

    private func totalTimelineHeight(for rows: [PortfolioDisplayRow]) -> CGFloat {
        let rowsSum = rows.reduce(CGFloat(0)) { $0 + rowHeight(for: $1) }
        let spacingsSum = rows.isEmpty ? 0 : CGFloat(rows.count) * rowSpacing
        return headerHeight + rowsSum + spacingsSum + 10
    }

    private var portfolioLayout: AreaPortfolioRoadmapLayout {
        let areaProjects = store.projectsForArea(area.id)
        let allHeadings = areaProjects.flatMap { store.headingsByProject[$0.id] ?? [] }
        let areaProjectIds = Set(areaProjects.map(\.id))
        let scheduledCompleted = store.logbookTasks.filter {
            guard let pid = $0.projectId, areaProjectIds.contains(pid) else { return false }
            return $0.whenDate != nil || $0.deadline != nil
        }
        let openInAreaProjects = store.allOpenTasks.filter {
            guard let pid = $0.projectId else { return false }
            return areaProjectIds.contains(pid)
        }
        return ProjectRoadmapEngine.buildAreaPortfolioLayout(
            area: area,
            projects: areaProjects,
            headings: allHeadings,
            tasks: openInAreaProjects + scheduledCompleted,
            todayMillis: DateEpochConverter.startOfDayMillis(for: Date())
        )
    }

    private func buildDisplayRows(for layout: AreaPortfolioRoadmapLayout) -> [PortfolioDisplayRow] {
        var list: [PortfolioDisplayRow] = []
        let generalLabel = store.tr("v30_board_ungrouped_column")
        for lane in layout.projectLanes {
            let isExp = expandedProjectIds.contains(lane.projectId)
            list.append(.projectSummary(lane, isExpanded: isExp))
            if isExp {
                let sub = lane.projectLayout
                if !sub.generalTaskBars.isEmpty {
                    list.append(.subPhase(projectId: lane.projectId, heading: nil, phaseBar: nil, title: generalLabel))
                    for tb in sub.generalTaskBars {
                        list.append(.subTask(projectId: lane.projectId, taskBar: tb, projectLayout: sub))
                    }
                }
                let headings = (store.headingsByProject[lane.projectId] ?? [])
                    .filter { !$0.isDeleted && !$0.isArchived }
                    .sorted {
                        if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                        return $0.id < $1.id
                    }
                for h in headings {
                    let pBar = sub.phaseBar(for: h.id)
                    let childBars = sub.taskBarsByHeading[h.id] ?? []
                    if pBar != nil || !childBars.isEmpty {
                        list.append(.subPhase(projectId: lane.projectId, heading: h, phaseBar: pBar, title: h.title))
                        for tb in childBars {
                            list.append(.subTask(projectId: lane.projectId, taskBar: tb, projectLayout: sub))
                        }
                    }
                }
            }
        }
        return list
    }

    private func computeCascadePreviewMap(for layout: AreaPortfolioRoadmapLayout) -> [String: Int] {
        guard !store.isTimelineFullScreen,
              let drag = activeTaskDrag,
              drag.deltaDays != 0,
              let pLane = layout.projectLanes.first(where: { $0.projectId == drag.projectId }),
              let anchorBar = pLane.projectLayout.allTaskBars.first(where: { $0.id == drag.taskId }) else {
            return [:]
        }
        let todayMs = DateEpochConverter.startOfDayMillis(
            for: DateEpochConverter.date(fromEpochDay: layout.todayEpochDay)
        )
        return ProjectRoadmapEngine.computeCascadeDeltaPreviewMap(
            anchorTask: anchorBar.task,
            deltaDays: drag.deltaDays,
            projectTasks: pLane.projectLayout.allTaskBars.map(\.task),
            todayMillis: todayMs
        )
    }

    public var body: some View {
        let layout = portfolioLayout
        let rows = buildDisplayRows(for: layout)
        let totalGridWidth = CGFloat(max(21, layout.totalDays)) * dayColumnWidth

        VStack(alignment: .leading, spacing: 14) {
            ScrollViewReader { scrollProxy in
                VStack(alignment: .leading, spacing: 12) {
                    portfolioToolbar(layout: layout) {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            scrollProxy.scrollTo("portfolio_day_\(layout.todayEpochDay)", anchor: .center)
                        }
                    }

                    if layout.projectLanes.isEmpty && layout.unscheduledProjects.isEmpty {
                        emptyPortfolioCard
                    } else if !layout.projectLanes.isEmpty {
                        HStack(alignment: .top, spacing: 0) {
                            portfolioLeftColumn(rows: rows, layout: layout)

                            Divider()

                            ScrollView(.horizontal, showsIndicators: true) {
                                portfolioTimelineGrid(
                                    layout: layout,
                                    rows: rows,
                                    totalGridWidth: totalGridWidth
                                )
                            }
                        }
                        .background(ThingsMacPalette.cardSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 15, style: .continuous)
                                .strokeBorder(Color.primary.opacity(0.085), lineWidth: 0.9)
                        )
                        .shadow(color: Color.black.opacity(0.045), radius: 8, y: 2)
                        .onAppear {
                            DispatchQueue.main.async {
                                scrollProxy.scrollTo("portfolio_day_\(layout.todayEpochDay)", anchor: .center)
                            }
                        }
                    }
                }
            }

            if !store.isTimelineFullScreen && !layout.unscheduledProjects.isEmpty {
                unscheduledProjectsTray(layout.unscheduledProjects)
            }
        }
        .onChange(of: area.id) { _, _ in
            expandedProjectIds.removeAll()
            activePhaseDrag = nil
            activeTaskDrag = nil
            armedPhaseDragId = nil
            armedTaskDragId = nil
        }
        .onChange(of: store.isTimelineFullScreen) { _, fullScreen in
            if fullScreen {
                activePhaseDrag = nil
                activeTaskDrag = nil
                armedPhaseDragId = nil
                armedTaskDragId = nil
                isPDFExportSheetPresented = false
            }
        }
        .sheet(isPresented: $isPDFExportSheetPresented) {
            let bundle = ProjectRoadmapEngine.toExportablePortfolioLayout(
                portfolioLayout: layout,
                expandedProjectIds: expandedProjectIds
            )
            RoadmapPDFExportSheetView(
                store: store,
                project: bundle.project,
                headings: bundle.headings,
                layout: bundle.layout,
                isPresented: $isPDFExportSheetPresented
            ) { exportOptions in
                DispatchQueue.main.async {
                    exportPortfolioPDFWithOptions(bundle: bundle, options: exportOptions)
                }
            }
        }
    }

    // MARK: - Portfolio Toolbar

    private func portfolioToolbar(
        layout: AreaPortfolioRoadmapLayout,
        onJumpToday: @escaping () -> Void
    ) -> some View {
        let isFull = store.isTimelineFullScreen
        let fullScreenLabel = isFull ? store.tr("v30_timeline_exit_fullscreen") : store.tr("v30_timeline_fullscreen")
        let fullScreenIcon = isFull ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right"
        let allExpanded = !layout.projectLanes.isEmpty && expandedProjectIds.count >= layout.projectLanes.count

        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 10) {
                portfolioLeadingControls(layout: layout, allExpanded: allExpanded, onJumpToday: onJumpToday)
                Spacer(minLength: 8)
                portfolioTrailingControls(layout: layout, isFull: isFull, fullScreenLabel: fullScreenLabel, fullScreenIcon: fullScreenIcon)
            }

            VStack(alignment: .leading, spacing: 8) {
                portfolioLeadingControls(layout: layout, allExpanded: allExpanded, onJumpToday: onJumpToday)
                HStack(spacing: 8) {
                    portfolioTrailingControls(layout: layout, isFull: isFull, fullScreenLabel: fullScreenLabel, fullScreenIcon: fullScreenIcon)
                    Spacer(minLength: 0)
                }
            }
        }
    }

    private func portfolioLeadingControls(
        layout: AreaPortfolioRoadmapLayout,
        allExpanded: Bool,
        onJumpToday: @escaping () -> Void
    ) -> some View {
        HStack(spacing: 10) {
            if store.isTimelineFullScreen {
                HStack(spacing: 7) {
                    Image(systemName: "folder.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.areaGreen)
                    Text(area.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
            }

            HStack(spacing: 6) {
                Image(systemName: "calendar")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                Text(formattedWindowLabel(startEpochDay: layout.startEpochDay, endEpochDay: layout.endEpochDay))
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 11)
            .padding(.vertical, 5.5)
            .background(
                Capsule(style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.8)
                    )
            )

            if !layout.projectLanes.isEmpty {
                let toggleLabel = store.tr(allExpanded ? "v31_area_collapse_all" : "v31_area_expand_all")
                ThingsActionChipButton(
                    title: toggleLabel,
                    systemImage: allExpanded ? "rectangle.compress.vertical" : "rectangle.expand.vertical",
                    helpText: toggleLabel
                ) {
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.86)) {
                        if allExpanded {
                            expandedProjectIds.removeAll()
                        } else {
                            expandedProjectIds = Set(layout.projectLanes.map(\.projectId))
                        }
                    }
                }
            }

            ThingsActionChipButton(
                title: store.tr("v30_roadmap_jump_today"),
                systemImage: "scope",
                helpText: store.tr("v30_roadmap_jump_today"),
                action: onJumpToday
            )
        }
    }

    private func portfolioTrailingControls(
        layout: AreaPortfolioRoadmapLayout,
        isFull: Bool,
        fullScreenLabel: String,
        fullScreenIcon: String
    ) -> some View {
        HStack(spacing: 8) {
            HStack(spacing: 2) {
                Button {
                    withAnimation(.easeOut(duration: 0.16)) {
                        dayColumnWidth = max(26.0, dayColumnWidth - 10.0)
                    }
                } label: {
                    Image(systemName: "minus.magnifyingglass")
                        .font(.system(size: 11.5, weight: .semibold))
                        .frame(width: 26, height: 24)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("macos_tooltip_zoom_out"))

                Button {
                    withAnimation(.easeOut(duration: 0.16)) {
                        dayColumnWidth = 38.0
                    }
                } label: {
                    Text("\(Int((dayColumnWidth / 38.0 * 100.0).rounded()))%")
                        .font(.system(size: 11, weight: .semibold, design: .rounded))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 6)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("macos_tooltip_zoom_reset"))

                Button {
                    withAnimation(.easeOut(duration: 0.16)) {
                        dayColumnWidth = min(64.0, dayColumnWidth + 10.0)
                    }
                } label: {
                    Image(systemName: "plus.magnifyingglass")
                        .font(.system(size: 11.5, weight: .semibold))
                        .frame(width: 26, height: 24)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("macos_tooltip_zoom_in"))
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.8)
                    )
            )
            .fixedSize()

            if !isFull && !layout.projectLanes.isEmpty {
                Menu {
                    Button {
                        isPDFExportSheetPresented = true
                    } label: {
                        Label(store.tr("v30_roadmap_export_pdf"), systemImage: "doc.richtext")
                    }

                    Button {
                        exportPortfolioPNGInteractively(layout: layout)
                    } label: {
                        Label(store.tr("v30_roadmap_export_png"), systemImage: "photo")
                    }
                } label: {
                    HStack(spacing: 5.5) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 11.5, weight: .semibold))
                        Text(store.tr("v30_roadmap_export"))
                            .font(.system(size: 12, weight: .semibold))
                            .lineLimit(1)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 8.5, weight: .bold))
                            .opacity(0.75)
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 11)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                    )
                }
                .menuStyle(.borderlessButton)
                .fixedSize()
                .thingsTooltip(store.tr("v30_roadmap_export"))
            }

            Button {
                store.toggleTimelineFullScreen()
            } label: {
                HStack(spacing: 5.5) {
                    Image(systemName: fullScreenIcon)
                        .font(.system(size: 11, weight: .semibold))
                    Text(fullScreenLabel)
                        .font(.system(size: 12, weight: .semibold))
                        .lineLimit(1)
                }
                .fixedSize(horizontal: true, vertical: false)
                .foregroundStyle(isFull ? ThingsMacPalette.accentBlue : Color.primary.opacity(0.88))
                .padding(.horizontal, 11)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(isFull ? ThingsMacPalette.accentBlue.opacity(0.14) : ThingsMacPalette.subtleContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .strokeBorder(
                                    isFull ? ThingsMacPalette.accentBlue.opacity(0.32) : Color.primary.opacity(0.08),
                                    lineWidth: 0.8
                                )
                        )
                )
                .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            .buttonStyle(.plain)
            .fixedSize(horizontal: true, vertical: false)
            .keyboardShortcut("f", modifiers: [.command, .control])
            .thingsTooltip(isFull ? "\(fullScreenLabel) (Esc)" : "\(fullScreenLabel) (⌃⌘F)")
        }
    }

    // MARK: - Left Column

    private func portfolioLeftColumn(
        rows: [PortfolioDisplayRow],
        layout: AreaPortfolioRoadmapLayout
    ) -> some View {
        let totalHeight = totalTimelineHeight(for: rows)

        return VStack(alignment: .leading, spacing: rowSpacing) {
            HStack(spacing: 6) {
                Image(systemName: "folder.fill")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.areaGreen)
                Text(area.title)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Spacer()
            }
            .padding(.horizontal, 12)
            .frame(width: labelColumnWidth, height: headerHeight)
            .background(ThingsMacPalette.subtleContainer.opacity(0.75))

            ForEach(rows) { row in
                let rHeight = rowHeight(for: row)
                switch row {
                case .projectSummary(let lane, let isExpanded):
                    portfolioLeftProjectSummaryRow(lane: lane, isExpanded: isExpanded, height: rHeight)
                case .subPhase(_, _, _, let title):
                    portfolioLeftPhaseRow(title: title, height: rHeight)
                case .subTask(_, let taskBar, let subLayout):
                    portfolioLeftTaskRow(taskBar: taskBar, subLayout: subLayout, height: rHeight)
                }
            }
        }
        .padding(.bottom, 10)
        .frame(width: labelColumnWidth, height: totalHeight, alignment: .topLeading)
        .background(ThingsMacPalette.subtleContainer.opacity(0.45))
    }

    private func portfolioLeftProjectSummaryRow(
        lane: AreaPortfolioProjectLane,
        isExpanded: Bool,
        height: CGFloat
    ) -> some View {
        let proj = lane.project
        return HStack(spacing: 7) {
            Button {
                withAnimation(.spring(response: 0.24, dampingFraction: 0.85)) {
                    if isExpanded {
                        expandedProjectIds.remove(proj.id)
                    } else {
                        expandedProjectIds.insert(proj.id)
                    }
                }
            } label: {
                HStack(spacing: 7) {
                    Image(systemName: isExpanded ? "chevron.down" : "chevron.right")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.secondary)
                        .frame(width: 14)

                    ProjectProgressRing(
                        progress: proj.progress,
                        size: 15,
                        tint: ThingsMacPalette.accentBlue,
                        isCompleted: proj.status == .completed
                    )
                    .fixedSize()

                    VStack(alignment: .leading, spacing: 1.5) {
                        Text(proj.title)
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        Text("\(proj.completedTaskCount)/\(proj.totalTaskCount)")
                            .font(.system(size: 10, weight: .medium, design: .rounded))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 4)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button {
                if store.isTimelineFullScreen {
                    store.isTimelineFullScreen = false
                }
                store.selectedDestination = .project(proj.id)
            } label: {
                Text("↗")
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .frame(width: 24, height: 24)
                    .background(
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(ThingsMacPalette.accentBlue.opacity(0.11))
                    )
            }
            .buttonStyle(.plain)
            .thingsTooltip(store.tr("v31_area_open_project"))
        }
        .padding(.horizontal, 10)
        .frame(width: labelColumnWidth, height: height)
        .background(ThingsMacPalette.subtleContainer.opacity(0.65))
    }

    private func portfolioLeftPhaseRow(title: String, height: CGFloat) -> some View {
        HStack(spacing: 6) {
            Capsule()
                .fill(ThingsMacPalette.inboxBlue)
                .frame(width: 3, height: 13)
            Text(title)
                .font(.system(size: 11.5, weight: .bold))
                .foregroundStyle(ThingsMacPalette.accentBlue)
                .lineLimit(1)
            Spacer()
        }
        .padding(.leading, 22)
        .padding(.trailing, 8)
        .frame(width: labelColumnWidth, height: height)
    }

    @ViewBuilder
    private func portfolioLeftTaskRow(
        taskBar: RoadmapTaskBar,
        subLayout: ProjectRoadmapLayout,
        height: CGFloat
    ) -> some View {
        let isFull = store.isTimelineFullScreen
        let isMilestone = taskBar.isMilestone || taskBar.task.isMilestone
        let isSelected = !isFull && store.selectedTaskIds.contains(taskBar.id)
        let rowView = HStack(spacing: 6) {
            ThingsCheckboxButton(
                status: taskBar.task.status,
                isSomeday: taskBar.task.scheduleState == .someday,
                isMilestone: isMilestone,
                helpText: isFull ? "" : store.tr("macos_checkbox_help"),
                onToggleComplete: {
                    guard !store.isTimelineFullScreen else { return }
                    store.toggleTaskCompletion(taskBar.task)
                },
                onCancelTask: {
                    guard !store.isTimelineFullScreen else { return }
                    store.cancelTask(taskBar.task)
                }
            )
            .disabled(isFull)
            .allowsHitTesting(!isFull)

            Text(
                isMilestone
                    ? "◆ \(taskBar.task.title.isEmpty ? store.tr("macos_untitled_todo") : taskBar.task.title)"
                    : (taskBar.task.title.isEmpty ? store.tr("macos_untitled_todo") : taskBar.task.title)
            )
            .font(.system(size: 12, weight: (isSelected || isMilestone) ? .semibold : .regular))
            .foregroundStyle(
                taskBar.task.status == .completed
                    ? .secondary
                    : (isMilestone ? ThingsMacPalette.milestoneViolet : .primary)
            )
            .lineLimit(1)

            Spacer(minLength: 2)
        }
        .padding(.leading, 30)
        .padding(.trailing, 8)
        .frame(width: labelColumnWidth, height: height)
        .background(
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .fill(isSelected ? ThingsMacPalette.accentBlue.opacity(0.12) : Color.clear)
        )
        .contentShape(Rectangle())
        .thingsTimelineMouseTooltip(
            TimelineTaskTooltipFormatter.formatRoadmapTask(
                taskBar: taskBar,
                isBlocked: store.isTaskActivelyBlocked(taskBar.task),
                language: store.preferences.language
            ),
            isSuppressed: armedTaskDragId == taskBar.id || activeTaskDrag?.taskId == taskBar.id
        )

        if isFull {
            rowView
        } else {
            rowView
                .thingsInstantTaskTap(
                    onSingleClick: { modifiers in
                        store.handleRowClick(taskId: taskBar.id, modifiers: modifiers)
                    },
                    onDoubleClick: {
                        store.openTaskModal(taskBar.id)
                    }
                )
        }
    }

    // MARK: - Right Synchronized Gantt Grid

    private func portfolioTimelineGrid(
        layout: AreaPortfolioRoadmapLayout,
        rows: [PortfolioDisplayRow],
        totalGridWidth: CGFloat
    ) -> some View {
        let totalHeight = totalTimelineHeight(for: rows)
        let cascadePreviewDeltas = computeCascadePreviewMap(for: layout)

        return ZStack(alignment: .topLeading) {
            // 1. Background Day Columns & Header
            HStack(spacing: 0) {
                ForEach(0..<layout.totalDays, id: \.self) { dayOffset in
                    let epochDay = layout.startEpochDay + dayOffset
                    let isToday = epochDay == layout.todayEpochDay
                    let isWeekend = isWeekendDay(epochDay)

                    VStack(spacing: 0) {
                        VStack(spacing: 2) {
                            Text(monthHeaderChip(for: epochDay, isFirstColumn: dayOffset == 0))
                                .font(.system(size: 9.5, weight: .bold))
                                .foregroundStyle(isToday ? ThingsMacPalette.accentBlue : .secondary)
                                .lineLimit(1)
                                .frame(height: 15)

                            HStack(spacing: 2) {
                                Text("\(dayNumber(for: epochDay))")
                                    .font(.system(size: 11.5, weight: isToday ? .bold : .semibold, design: .rounded))
                                Text(weekdayInitial(for: epochDay))
                                    .font(.system(size: 9.5, weight: .medium))
                                    .opacity(0.75)
                            }
                            .foregroundStyle(isToday ? .white : (isWeekend ? .secondary : .primary))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(isToday ? ThingsMacPalette.accentBlue : Color.clear)
                            )
                        }
                        .frame(width: dayColumnWidth, height: headerHeight)
                        .background(ThingsMacPalette.subtleContainer.opacity(0.75))
                        .id("portfolio_day_\(epochDay)")

                        Rectangle()
                            .fill(
                                isToday
                                    ? ThingsMacPalette.accentBlue.opacity(0.07)
                                    : (isWeekend ? Color.primary.opacity(0.018) : Color.clear)
                            )
                            .frame(width: dayColumnWidth, height: max(40, totalHeight - headerHeight))
                            .overlay(alignment: .leading) {
                                Rectangle()
                                    .fill(Color.primary.opacity(0.055))
                                    .frame(width: 0.75)
                            }
                            .overlay {
                                if isToday {
                                    Rectangle()
                                        .fill(ThingsMacPalette.accentBlue.opacity(0.65))
                                        .frame(width: 1.6)
                                }
                            }
                    }
                }
            }

            // 2. Dependency Bézier Curves for Expanded Projects
            Canvas { context, _ in
                var midYByTaskId: [String: CGFloat] = [:]
                var cursorY: CGFloat = headerHeight + rowSpacing
                for row in rows {
                    let rH = rowHeight(for: row)
                    if case .subTask(_, let bar, _) = row {
                        midYByTaskId[bar.id] = cursorY + (rH * 0.5)
                    }
                    cursorY += rH + rowSpacing
                }

                for pLane in layout.projectLanes where expandedProjectIds.contains(pLane.projectId) {
                    let barById = Dictionary(uniqueKeysWithValues: pLane.projectLayout.allTaskBars.map { ($0.id, $0) })
                    for edge in pLane.projectLayout.dependencyEdges {
                        guard let fromBar = barById[edge.fromTaskId],
                              let toBar = barById[edge.toTaskId],
                              let y0 = midYByTaskId[edge.fromTaskId],
                              let y1 = midYByTaskId[edge.toTaskId] else {
                            continue
                        }
                        let fromShift = CGFloat(cascadePreviewDeltas[fromBar.id] ?? 0) * dayColumnWidth
                        let toShift = CGFloat(cascadePreviewDeltas[toBar.id] ?? 0) * dayColumnWidth
                        let x0: CGFloat = (fromBar.isMilestone || fromBar.task.isMilestone)
                            ? ((CGFloat(fromBar.endEpochDay - layout.startEpochDay) + 0.5) * dayColumnWidth + 11 + fromShift)
                            : (CGFloat(fromBar.endEpochDay - layout.startEpochDay + 1) * dayColumnWidth - 3 + fromShift)
                        let x1: CGFloat = (toBar.isMilestone || toBar.task.isMilestone)
                            ? ((CGFloat(toBar.startEpochDay - layout.startEpochDay) + 0.5) * dayColumnWidth - 11 + toShift)
                            : (CGFloat(toBar.startEpochDay - layout.startEpochDay) * dayColumnWidth + 3 + toShift)
                        let p0 = CGPoint(x: x0, y: y0)
                        let p3 = CGPoint(x: x1, y: y1)

                        var curve = Path()
                        curve.move(to: p0)
                        if p3.x >= p0.x + 12 {
                            let dx = max(24.0, (p3.x - p0.x) * 0.5)
                            curve.addCurve(
                                to: p3,
                                control1: CGPoint(x: p0.x + dx, y: p0.y),
                                control2: CGPoint(x: p3.x - dx, y: p3.y)
                            )
                        } else {
                            let vSign: CGFloat = p3.y >= p0.y ? 1.0 : -1.0
                            curve.addCurve(
                                to: p3,
                                control1: CGPoint(x: p0.x + 28.0, y: p0.y + 14.0 * vSign),
                                control2: CGPoint(x: p3.x - 28.0, y: p3.y - 14.0 * vSign)
                            )
                        }
                        let edgeColor = edge.isActivelyBlocking
                            ? ThingsMacPalette.somedayAmber
                            : ThingsMacPalette.accentBlue.opacity(0.45)
                        context.stroke(
                            curve,
                            with: .color(edgeColor),
                            style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round)
                        )
                        var arrow = Path()
                        arrow.move(to: CGPoint(x: p3.x - 6.5, y: p3.y - 3.8))
                        arrow.addLine(to: p3)
                        arrow.addLine(to: CGPoint(x: p3.x - 6.5, y: p3.y + 3.8))
                        context.stroke(
                            arrow,
                            with: .color(edgeColor),
                            style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round)
                        )
                    }
                }
            }
            .frame(width: totalGridWidth, height: totalHeight)
            .allowsHitTesting(false)

            // 3. Project Summary Bars + Collapsed Milestone Diamonds + Expanded Phase/Task Bars
            VStack(alignment: .leading, spacing: rowSpacing) {
                Color.clear
                    .frame(width: totalGridWidth, height: headerHeight)

                ForEach(rows) { row in
                    ZStack(alignment: .leading) {
                        Color.clear
                            .frame(width: totalGridWidth, height: rowHeight(for: row))

                        switch row {
                        case .projectSummary(let lane, let isExpanded):
                            portfolioProjectSummaryBar(
                                lane: lane,
                                isExpanded: isExpanded,
                                layout: layout
                            )

                        case .subPhase(_, let heading, let phaseBar, _):
                            if let heading, let phaseBar {
                                portfolioSubPhaseBar(
                                    heading: heading,
                                    phaseBar: phaseBar,
                                    layout: layout
                                )
                            }

                        case .subTask(let projectId, let taskBar, _):
                            portfolioSubTaskBar(
                                projectId: projectId,
                                taskBar: taskBar,
                                layout: layout,
                                cascadePreviewDeltas: cascadePreviewDeltas
                            )
                        }
                    }
                }
            }
        }
        .frame(width: totalGridWidth, height: totalHeight, alignment: .topLeading)
        .coordinateSpace(name: "portfolioTimelineGrid")
    }

    @ViewBuilder
    private func portfolioProjectSummaryBar(
        lane: AreaPortfolioProjectLane,
        isExpanded: Bool,
        layout: AreaPortfolioRoadmapLayout
    ) -> some View {
        let clampedStart = max(layout.startEpochDay, lane.summaryStartEpochDay)
        let clampedEnd = min(layout.endEpochDay, lane.summaryEndEpochDay)
        let span = max(1, clampedEnd - clampedStart + 1)
        let barX = CGFloat(clampedStart - layout.startEpochDay) * dayColumnWidth + 2
        let barW = max(20.0, CGFloat(span) * dayColumnWidth - 4)

        ZStack(alignment: .leading) {
            HStack(spacing: 6) {
                ProjectProgressRing(
                    progress: lane.project.progress,
                    size: 13,
                    tint: ThingsMacPalette.accentBlue,
                    isCompleted: lane.project.status == .completed
                )
                Text(lane.project.title)
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 9)
            .frame(width: barW, height: 28)
            .background(
                RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                    .fill(ThingsMacPalette.accentBlue.opacity(0.15))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8.5, style: .continuous)
                    .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.52), lineWidth: 1.15)
            )
            .contentShape(RoundedRectangle(cornerRadius: 8.5, style: .continuous))
            .onTapGesture {
                if store.isTimelineFullScreen {
                    store.isTimelineFullScreen = false
                }
                store.selectedDestination = .project(lane.projectId)
            }
            .offset(x: barX)

            // Overlaid 45° Royal-Violet Milestone Diamonds (◆) when collapsed!
            if !isExpanded {
                ForEach(lane.milestoneBars) { mb in
                    if mb.startEpochDay >= layout.startEpochDay && mb.startEpochDay <= layout.endEpochDay {
                        let centerX = (CGFloat(mb.startEpochDay - layout.startEpochDay) + 0.5) * dayColumnWidth
                        let msTitle = mb.task.title.isEmpty ? store.tr("macos_untitled_todo") : mb.task.title
                        ZStack {
                            RoundedRectangle(cornerRadius: 3.0, style: .continuous)
                                .fill(ThingsMacPalette.milestoneViolet)
                                .frame(width: 14, height: 14)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 3.0, style: .continuous)
                                        .strokeBorder(Color.white.opacity(0.95), lineWidth: 1.35)
                                )
                                .rotationEffect(.degrees(45))
                                .shadow(color: ThingsMacPalette.milestoneViolet.opacity(0.38), radius: 3.5, y: 1)
                        }
                        .frame(width: 20, height: 20)
                        .contentShape(Rectangle())
                        .thingsTooltip("◆ \(msTitle)")
                        .onTapGesture {
                            if !store.isTimelineFullScreen {
                                store.openTaskModal(mb.id)
                            }
                        }
                        .offset(x: centerX - 10)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func portfolioSubPhaseBar(
        heading: Heading,
        phaseBar: RoadmapPhaseBar,
        layout: AreaPortfolioRoadmapLayout
    ) -> some View {
        let isFull = store.isTimelineFullScreen
        let isArmed = !isFull && armedPhaseDragId == heading.id
        let delta = (!isFull && activePhaseDrag?.id == heading.id) ? (activePhaseDrag?.deltaDays ?? 0) : 0
        let rawStart = phaseBar.startEpochDay + delta
        let rawEnd = phaseBar.endEpochDay + delta

        if rawEnd >= layout.startEpochDay && rawStart <= layout.endEpochDay {
            let clampedStart = max(layout.startEpochDay, rawStart)
            let clampedEnd = min(layout.endEpochDay, rawEnd)
            let span = max(1, clampedEnd - clampedStart + 1)
            let barX = CGFloat(clampedStart - layout.startEpochDay) * dayColumnWidth + 2
            let barW = max(dayColumnWidth - 4, CGFloat(span) * dayColumnWidth - 4)

            let capsule = HStack(spacing: 5) {
                Image(systemName: "flag.checkered")
                    .font(.system(size: 9.5, weight: .bold))
                    .foregroundStyle(.white.opacity(0.95))
                Text(heading.title)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 8)
            .frame(width: barW, height: 23)
            .background(
                Capsule(style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .opacity(phaseBar.hasExplicitDates ? 0.94 : 0.72)
            )
            .scaleEffect(isArmed ? 1.02 : 1.0)
            .offset(x: barX)

            if isFull {
                capsule
            } else {
                capsule
                    .gesture(
                        LongPressGesture(minimumDuration: 0.28, maximumDistance: 8)
                            .sequenced(before: DragGesture(minimumDistance: 2, coordinateSpace: .named("portfolioTimelineGrid")))
                            .onChanged { value in
                                switch value {
                                case .second(true, nil):
                                    armedPhaseDragId = heading.id
                                case .second(true, let drag?):
                                    armedPhaseDragId = heading.id
                                    let snapped = Int((drag.translation.width / dayColumnWidth).rounded())
                                    activePhaseDrag = (heading.id, snapped)
                                default:
                                    break
                                }
                            }
                            .onEnded { value in
                                let finalDelta: Int = {
                                    if case .second(true, let drag?) = value {
                                        return Int((drag.translation.width / dayColumnWidth).rounded())
                                    }
                                    return 0
                                }()
                                armedPhaseDragId = nil
                                activePhaseDrag = nil
                                if finalDelta != 0 {
                                    store.shiftHeadingRoadmapDates(
                                        heading,
                                        deltaDays: finalDelta,
                                        fallbackEpochDays: (phaseBar.startEpochDay, phaseBar.endEpochDay)
                                    )
                                }
                            }
                    )
            }
        }
    }

    @ViewBuilder
    private func portfolioSubTaskBar(
        projectId: String,
        taskBar: RoadmapTaskBar,
        layout: AreaPortfolioRoadmapLayout,
        cascadePreviewDeltas: [String: Int]
    ) -> some View {
        let task = taskBar.task
        let isFull = store.isTimelineFullScreen
        let isSelected = !isFull && store.selectedTaskIds.contains(task.id)
        let isBlocked = store.isTaskActivelyBlocked(task)
        let isArmed = !isFull && armedTaskDragId == task.id
        let delta = isFull ? 0 : (cascadePreviewDeltas[task.id] ?? 0)
        let isDragging = !isFull && (isArmed || activeTaskDrag?.taskId == task.id || delta != 0)
        let dragX = CGFloat(delta) * dayColumnWidth
        let displayTitle = task.title.isEmpty ? store.tr("macos_untitled_todo") : task.title

        let barVisual: some View = Group {
            if taskBar.isMilestone || task.isMilestone {
                let isCompleted = task.status == .completed
                let isOverdue = !isCompleted && (taskBar.endEpochDay + delta) < layout.todayEpochDay
                let tint: Color = isCompleted
                    ? ThingsMacPalette.logbookGreen
                    : (isOverdue ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.milestoneViolet)
                let colCenterX = (CGFloat(taskBar.startEpochDay - layout.startEpochDay) + 0.5) * dayColumnWidth

                HStack(spacing: 5) {
                    RoundedRectangle(cornerRadius: 3.2, style: .continuous)
                        .fill(tint)
                        .frame(width: 15, height: 15)
                        .overlay(
                            RoundedRectangle(cornerRadius: 3.2, style: .continuous)
                                .strokeBorder(Color.white.opacity(0.94), lineWidth: 1.4)
                        )
                        .rotationEffect(.degrees(45))
                        .frame(width: 22, height: 24)

                    Text("◆ \(displayTitle)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(isCompleted ? .secondary : tint)
                        .lineLimit(1)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3.5)
                        .background(
                            Capsule(style: .continuous)
                                .fill(isSelected ? tint.opacity(0.16) : ThingsMacPalette.cardSurface.opacity(0.96))
                        )
                        .overlay(
                            Capsule(style: .continuous)
                                .strokeBorder(tint.opacity((isArmed || isSelected) ? 0.95 : 0.45), lineWidth: 1.1)
                        )
                }
                .fixedSize(horizontal: true, vertical: false)
                .offset(x: colCenterX - 11 + dragX)
            } else {
                let baseX = CGFloat(taskBar.startEpochDay - layout.startEpochDay) * dayColumnWidth + 3
                let barW = max(dayColumnWidth - 6, CGFloat(taskBar.spanDays) * dayColumnWidth - 6)
                let borderTint: Color = (isArmed || isSelected)
                    ? ThingsMacPalette.accentBlue
                    : (isBlocked ? ThingsMacPalette.somedayAmber : ThingsMacPalette.accentBlue.opacity(0.45))

                HStack(spacing: 5) {
                    if isBlocked {
                        Image(systemName: "hourglass")
                            .font(.system(size: 9.5, weight: .bold))
                            .foregroundStyle(ThingsMacPalette.somedayAmber)
                    }
                    Text(displayTitle)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 8)
                .frame(width: barW, height: 25)
                .background(
                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                        .fill(isSelected ? ThingsMacPalette.accentBlue.opacity(0.16) : ThingsMacPalette.cardSurface)
                        .shadow(color: Color.black.opacity(isDragging ? 0.14 : 0.04), radius: isDragging ? 5 : 2, y: 1)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                        .strokeBorder(borderTint, lineWidth: (isArmed || isSelected) ? 1.5 : 1.0)
                )
                .offset(x: baseX + dragX)
            }
        }
        .contentShape(Rectangle())
        .thingsTimelineMouseTooltip(
            TimelineTaskTooltipFormatter.formatRoadmapTask(
                taskBar: taskBar,
                isBlocked: isBlocked,
                language: store.preferences.language
            ),
            isSuppressed: isDragging
        )

        if isFull {
            barVisual
        } else {
            barVisual
                .thingsInstantTaskTap(
                    onSingleClick: { modifiers in
                        store.handleRowClick(taskId: task.id, modifiers: modifiers)
                    },
                    onDoubleClick: {
                        store.openTaskModal(task.id)
                    }
                )
                .gesture(
                    LongPressGesture(minimumDuration: 0.28, maximumDistance: 8)
                        .sequenced(before: DragGesture(minimumDistance: 2, coordinateSpace: .named("portfolioTimelineGrid")))
                        .onChanged { value in
                            switch value {
                            case .second(true, nil):
                                armedTaskDragId = task.id
                            case .second(true, let drag?):
                                armedTaskDragId = task.id
                                let snapped = Int((drag.translation.width / dayColumnWidth).rounded())
                                activeTaskDrag = (projectId, task.id, snapped)
                            default:
                                break
                            }
                        }
                        .onEnded { value in
                            let finalDelta: Int = {
                                if case .second(true, let drag?) = value {
                                    return Int((drag.translation.width / dayColumnWidth).rounded())
                                }
                                return 0
                            }()
                            armedTaskDragId = nil
                            activeTaskDrag = nil
                            if finalDelta != 0 {
                                store.shiftTaskRoadmapDates(task, deltaDays: finalDelta)
                            }
                        }
                )
        }
    }

    // MARK: - Empty Card & Unscheduled Projects Tray

    private var emptyPortfolioCard: some View {
        VStack(spacing: 10) {
            Image(systemName: "calendar.day.timeline.left")
                .font(.system(size: 32, weight: .light))
                .foregroundStyle(ThingsMacPalette.areaGreen.opacity(0.75))
            Text(store.tr("v31_area_no_projects_timeline"))
                .font(.system(size: 13.5))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 44)
        .thingsCardSurface(cornerRadius: 14)
    }

    private func unscheduledProjectsTray(_ unscheduledProjects: [Project]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "tray.full")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                Text(store.tr("v31_area_unscheduled_projects"))
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundStyle(.primary)

                Text("\(unscheduledProjects.count)")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(Capsule().fill(Color.primary.opacity(0.06)))

                Spacer()
            }

            ThingsFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                ForEach(unscheduledProjects) { proj in
                    Button {
                        store.selectedDestination = .project(proj.id)
                    } label: {
                        HStack(spacing: 7) {
                            ProjectProgressRing(
                                progress: proj.progress,
                                size: 14,
                                tint: ThingsMacPalette.accentBlue,
                                isCompleted: proj.status == .completed
                            )
                            Text(proj.title)
                                .font(.system(size: 12.5, weight: .semibold))
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                            Text("↗")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(ThingsMacPalette.accentBlue)
                        }
                        .padding(.horizontal, 11)
                        .padding(.vertical, 6.5)
                        .thingsCardSurface(cornerRadius: 9)
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(store.tr("v31_area_open_project"))
                }
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer.opacity(0.75))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.07), lineWidth: 0.85)
                )
        )
    }

    // MARK: - Date Helpers

    private func formattedWindowLabel(startEpochDay: Int, endEpochDay: Int) -> String {
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.dateStyle = .medium
        let start = DateEpochConverter.date(fromEpochDay: startEpochDay)
        let end = DateEpochConverter.date(fromEpochDay: endEpochDay)
        return "\(df.string(from: start)) – \(df.string(from: end))"
    }

    private func dayNumber(for epochDay: Int) -> Int {
        Calendar.current.component(.day, from: DateEpochConverter.date(fromEpochDay: epochDay))
    }

    private func weekdayInitial(for epochDay: Int) -> String {
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.setLocalizedDateFormatFromTemplate("EEEEE")
        return df.string(from: DateEpochConverter.date(fromEpochDay: epochDay))
    }

    private func monthHeaderChip(for epochDay: Int, isFirstColumn: Bool) -> String {
        let date = DateEpochConverter.date(fromEpochDay: epochDay)
        let dom = Calendar.current.component(.day, from: date)
        guard isFirstColumn || dom == 1 else { return "" }
        let df = DateFormatter()
        df.locale = store.preferences.language.locale
        df.setLocalizedDateFormatFromTemplate("MMM")
        return df.string(from: date)
    }

    private func isWeekendDay(_ epochDay: Int) -> Bool {
        let iso = DateEpochConverter.isoWeekday(for: DateEpochConverter.date(fromEpochDay: epochDay))
        return iso == 6 || iso == 7
    }

    // MARK: - Portfolio PDF & PNG Export

    private var sanitizedPortfolioExportBaseName: String {
        let invalid = CharacterSet(charactersIn: "/\\?%*|\"<>:")
        let cleaned = area.title
            .components(separatedBy: invalid)
            .joined(separator: "-")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? "Portfolio-Roadmap" : "\(cleaned)-Portfolio"
    }

    @MainActor
    private func exportPortfolioPDFWithOptions(
        bundle: (project: Project, headings: [Heading], layout: ProjectRoadmapLayout),
        options: RoadmapPDFExportOptions
    ) {
        let panel = NSSavePanel()
        panel.title = store.tr("v30_roadmap_export_pdf")
        panel.canCreateDirectories = true
        panel.allowedContentTypes = [UTType.pdf]
        panel.nameFieldStringValue = "\(sanitizedPortfolioExportBaseName)-Roadmap.pdf"

        guard panel.runModal() == .OK, let destinationURL = panel.url else { return }

        if let data = ProjectRoadmapExporter.exportRoadmapPDFData(
            project: bundle.project,
            headings: bundle.headings,
            layout: bundle.layout,
            options: options,
            generalLabel: store.tr("v30_board_ungrouped_column"),
            todayLabel: store.tr("view_today"),
            locale: store.preferences.language.locale
        ) {
            try? data.write(to: destinationURL, options: .atomic)
        }
    }

    @MainActor
    private func exportPortfolioPNGInteractively(layout: AreaPortfolioRoadmapLayout) {
        let panel = NSSavePanel()
        panel.title = store.tr("v30_roadmap_export_png")
        panel.canCreateDirectories = true
        panel.allowedContentTypes = [UTType.png]
        panel.nameFieldStringValue = "\(sanitizedPortfolioExportBaseName)-Roadmap.png"

        guard panel.runModal() == .OK, let destinationURL = panel.url else { return }

        let bundle = ProjectRoadmapEngine.toExportablePortfolioLayout(
            portfolioLayout: layout,
            expandedProjectIds: expandedProjectIds
        )
        let rawSnapshot = RoadmapExportSnapshotView(
            store: store,
            project: bundle.project,
            headings: bundle.headings,
            layout: bundle.layout,
            dayColumnWidth: max(36.0, dayColumnWidth)
        )
        let proposed = rawSnapshot.exportCanvasSize
        let snapshotView = rawSnapshot.environment(\.colorScheme, .light)
        let exportedData = ProjectRoadmapExporter.renderViewToPNGData(snapshotView, scale: 2.0, proposedSize: proposed)
            ?? ProjectRoadmapExporter.exportRoadmapPNGData(
                project: bundle.project,
                headings: bundle.headings,
                layout: bundle.layout,
                generalLabel: store.tr("v30_board_ungrouped_column"),
                todayLabel: store.tr("view_today"),
                locale: store.preferences.language.locale,
                dayColumnWidth: max(36.0, dayColumnWidth),
                scale: 2.0
            )

        if let data = exportedData {
            try? data.write(to: destinationURL, options: .atomic)
        }
    }
}
