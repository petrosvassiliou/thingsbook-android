import SwiftUI
import ThingsbookCore

public struct SidebarView: View {
    @ObservedObject var store: ThingsbookStore
    @State private var collapsedAreaIds: Set<String> = []

    public init(store: ThingsbookStore) {
        self.store = store
    }

    private var defaultNewProjectAreaId: String? {
        switch store.selectedDestination {
        case .area(let areaId):
            return areaId
        case .project(let projId):
            return store.projects.first(where: { $0.id == projId })?.areaId ?? store.areas.first?.id
        default:
            return store.areas.first?.id
        }
    }

    private var upcomingMilestoneCount: Int {
        let todayEpochDay = DateEpochConverter.epochDay(for: Date())
        let activeProjectIds = Set(store.projects.map(\.id))
        var count = 0
        for (projId, headings) in store.headingsByProject where activeProjectIds.contains(projId) {
            for h in headings where !h.isDeleted && !h.isArchived && !h.title.isEmpty {
                if let endMs = h.endDate, DateEpochConverter.epochDay(forMillis: endMs) >= todayEpochDay {
                    count += 1
                }
            }
        }
        return count
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Brand Header (Icon B Glacier Liquid Glass Book Emblem)
            HStack(spacing: 9) {
                if let appIcon = ThingsResourceLoader.appIconImage() {
                    Image(nsImage: appIcon)
                        .resizable()
                        .interpolation(.high)
                        .frame(width: 28, height: 28)
                } else {
                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 26, height: 26)
                        .overlay(
                            RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                .strokeBorder(Color.white.opacity(0.55), lineWidth: 0.9)
                        )
                        .overlay {
                            Image(systemName: "book.pages.fill")
                                .font(.system(size: 12.5, weight: .semibold))
                                .foregroundStyle(.white)
                        }
                        .shadow(color: ThingsMacPalette.accentBlue.opacity(0.28), radius: 5, y: 2)
                }

                Text(store.tr("app_name"))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.primary)

                if AppUpdateService.currentVersionIsPrerelease {
                    Text(store.tr("v2_beta_badge"))
                        .font(.system(size: 9.5, weight: .bold, design: .rounded))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1.5)
                        .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.16)))
                }

                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.top, 12)
            .padding(.bottom, 10)

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    // 1. 2x2 Bento Focus Grid (Inbox, Today, Upcoming, Anytime)
                    VStack(spacing: 7) {
                        HStack(spacing: 7) {
                            bentoTile(.inbox, count: store.inboxTasks.count)
                            bentoTile(.today, count: store.todayTotalBadgeCount)
                        }
                        HStack(spacing: 7) {
                            bentoTile(.upcoming, count: store.upcomingTasks.count + upcomingMilestoneCount)
                            bentoTile(.anytime, count: store.anytimeTasks.count)
                        }
                    }

                    // 2. 1x2 Slim Secondary Mini-Cards (Someday, Weekly Review)
                    HStack(spacing: 7) {
                        slimMiniCard(.someday, count: store.somedayTasks.count)
                        weeklyReviewMiniCard
                    }

                    // 2.5 Custom Saved Perspectives (v3.0)
                    perspectivesSection

                    // 3. Areas -> Nested Projects Hierarchy (Workspace Folders + Linear Progress)
                    if !store.areas.isEmpty {
                        Divider()
                            .padding(.vertical, 3)
                            .padding(.horizontal, 4)

                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(store.areas) { area in
                                areaSection(area)
                            }
                        }
                    }

                    // 4. Legacy / Unassigned Projects
                    if !store.standaloneActiveProjects.isEmpty {
                        Divider()
                            .padding(.vertical, 3)
                            .padding(.horizontal, 4)

                        VStack(alignment: .leading, spacing: 2) {
                            ForEach(store.standaloneActiveProjects) { proj in
                                projectRow(proj)
                            }
                        }
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
            }

            Divider()
                .opacity(0.65)

            // Bottom Sidebar Bar (+ New Project ▾ menu with Templates & Import, quiet Logbook/Trash/Areas footer dock)
            HStack(spacing: 8) {
                Menu {
                    Button {
                        store.newProjectDefaultAreaId = defaultNewProjectAreaId
                        store.isNewProjectSheetPresented = true
                    } label: {
                        Label(store.tr("entity_new_project"), systemImage: "folder.badge.plus")
                    }

                    Button {
                        store.templatesInitialTab = .gallery
                        store.isTemplatesSheetPresented = true
                    } label: {
                        Label(store.tr("v2_templates_title"), systemImage: "square.on.square")
                    }

                    Button {
                        store.templatesInitialTab = .importOutline
                        store.isTemplatesSheetPresented = true
                    } label: {
                        Label(store.tr("v2_import_markdown_title"), systemImage: "arrow.down.doc")
                    }

                    Button {
                        store.templatesInitialTab = .externalImportExport
                        store.isTemplatesSheetPresented = true
                    } label: {
                        Label(store.tr("v30_external_import_export_tab"), systemImage: "arrow.up.arrow.down.square")
                    }

                    Button {
                        store.isNewAreaSheetPresented = true
                    } label: {
                        Label(store.tr("entity_new_area"), systemImage: "folder")
                    }
                    .disabled(store.areas.count >= ThingsSQLiteDatabase.maxAreas)

                    Button {
                        store.editingPerspective = nil
                        store.isPerspectiveEditorPresented = true
                    } label: {
                        Label(store.tr("v30_perspective_new"), systemImage: "line.3.horizontal.decrease.circle")
                    }

                    Divider()

                    Button {
                        store.geminiInitialTab = .projectArchitect
                        store.isGeminiSheetPresented = true
                    } label: {
                        Label(store.tr("gemini_project_architect_title"), systemImage: "sparkles")
                    }
                } label: {
                    HStack(spacing: 4.5) {
                        Image(systemName: "plus")
                            .font(.system(size: 11.5, weight: .semibold))
                        Text(store.tr("entity_new_project"))
                            .font(.system(size: 12.5, weight: .semibold))
                            .lineLimit(1)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 8.5, weight: .bold))
                            .opacity(0.78)
                    }
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                }
                .menuStyle(.borderlessButton)
                .fixedSize()
                .thingsTooltip(store.tr("entity_new_project"))

                Spacer(minLength: 4)

                HStack(spacing: 4) {
                    footerSmartIconButton(.logbook)
                    footerSmartIconButton(.trash)

                    Button {
                        store.isAreaManagerPresented = true
                    } label: {
                        HStack(spacing: 3.5) {
                            Image(systemName: "folder")
                                .font(.system(size: 10.5))
                            Text("\(store.areas.count)/\(ThingsSQLiteDatabase.maxAreas)")
                                .font(.system(size: 11, weight: .medium, design: .rounded))
                        }
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 4)
                        .background(
                            RoundedRectangle(cornerRadius: 6, style: .continuous)
                                .fill(Color.primary.opacity(0.04))
                        )
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(store.trf("areas_active_count", store.areas.count, ThingsSQLiteDatabase.maxAreas))
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
        }
        .background {
            ZStack {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(.ultraThinMaterial)
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer.opacity(0.62))
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(Color.primary.opacity(0.09), lineWidth: 0.85)
        )
        .shadow(color: Color.black.opacity(0.07), radius: 10, x: 0, y: 4)
        .padding(.leading, 8)
        .padding(.trailing, 6)
        .padding(.vertical, 8)
        .background(ThingsMacPalette.canvasBackground.ignoresSafeArea())
    }

    private func smartViewShortcut(for view: SmartView) -> String {
        switch view {
        case .inbox: return "⌘1"
        case .today: return "⌘2"
        case .upcoming: return "⌘3"
        case .anytime: return "⌘4"
        case .someday: return "⌘5"
        case .logbook: return "⌘6"
        case .trash: return "⌘7"
        }
    }

    private func bentoTile(_ view: SmartView, count: Int) -> some View {
        let dest = NavigationDestination.smartView(view)
        let selected = store.selectedDestination == dest
        return Button {
            store.selectedDestination = dest
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .center) {
                    Image(systemName: view.sfSymbol)
                        .font(.system(size: 14.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.color(for: view, isSelected: selected))

                    Spacer(minLength: 4)

                    if count > 0 {
                        Text("\(count)")
                            .font(.system(size: 12.5, weight: .bold, design: .rounded))
                            .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .primary)
                    }
                }

                Text(store.tr(view.titleKey))
                    .font(.system(size: 12.5, weight: selected ? .bold : .semibold))
                    .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .primary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 11)
            .padding(.vertical, 9)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .fill(selected ? ThingsMacPalette.accentBlue.opacity(0.16) : ThingsMacPalette.cardSurface.opacity(0.76))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .strokeBorder(
                        selected ? ThingsMacPalette.accentBlue.opacity(0.42) : Color.primary.opacity(0.075),
                        lineWidth: selected ? 1.1 : 0.75
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
        }
        .buttonStyle(.plain)
        .thingsTooltip("\(store.tr(view.titleKey)) (\(smartViewShortcut(for: view)))")
        .thingsDropTarget(cornerRadius: 13) { payloads in
            store.dropTasks(payloads, onto: dest)
        }
    }

    private func slimMiniCard(_ view: SmartView, count: Int) -> some View {
        let dest = NavigationDestination.smartView(view)
        let selected = store.selectedDestination == dest
        return Button {
            store.selectedDestination = dest
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .center) {
                    Image(systemName: view.sfSymbol)
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.color(for: view, isSelected: selected))

                    Spacer(minLength: 4)

                    if count > 0 {
                        Text("\(count)")
                            .font(.system(size: 11.5, weight: .bold, design: .rounded))
                            .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .secondary)
                    }
                }

                Text(store.tr(view.titleKey))
                    .font(.system(size: 12, weight: selected ? .bold : .semibold))
                    .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .primary.opacity(0.9))
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(selected ? ThingsMacPalette.accentBlue.opacity(0.16) : ThingsMacPalette.cardSurface.opacity(0.68))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(
                        selected ? ThingsMacPalette.accentBlue.opacity(0.42) : Color.primary.opacity(0.07),
                        lineWidth: selected ? 1.05 : 0.75
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .thingsTooltip("\(store.tr(view.titleKey)) (\(smartViewShortcut(for: view)))")
        .thingsDropTarget(cornerRadius: 12) { payloads in
            store.dropTasks(payloads, onto: dest)
        }
    }

    private var weeklyReviewMiniCard: some View {
        let stalledCount = store.stalledProjects.count
        let active = store.isWeeklyReviewPresented
        return Button {
            store.isWeeklyReviewPresented = true
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .center) {
                    Image(systemName: "checklist.checked")
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(active ? ThingsMacPalette.accentBlue : ThingsMacPalette.accentBlue.opacity(0.85))

                    Spacer(minLength: 4)

                    if stalledCount > 0 {
                        Text("\(stalledCount)")
                            .font(.system(size: 11.5, weight: .bold, design: .rounded))
                            .foregroundStyle(active ? ThingsMacPalette.accentBlue : .secondary)
                    }
                }

                Text(store.tr("sidebar_review_short"))
                    .font(.system(size: 12, weight: active ? .bold : .semibold))
                    .foregroundStyle(active ? ThingsMacPalette.accentBlue : .primary.opacity(0.9))
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(active ? ThingsMacPalette.accentBlue.opacity(0.16) : ThingsMacPalette.cardSurface.opacity(0.68))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(
                        active ? ThingsMacPalette.accentBlue.opacity(0.42) : Color.primary.opacity(0.07),
                        lineWidth: active ? 1.05 : 0.75
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .thingsTooltip(store.tr("v2_weekly_review_title") + " (⇧⌘R)")
    }

    private func footerSmartIconButton(_ view: SmartView) -> some View {
        let dest = NavigationDestination.smartView(view)
        let selected = store.selectedDestination == dest
        return Button {
            store.selectedDestination = dest
        } label: {
            Image(systemName: view.sfSymbol)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .secondary)
                .frame(width: 25, height: 24)
                .background(
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(selected ? ThingsMacPalette.accentBlue.opacity(0.16) : Color.clear)
                )
                .contentShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
        }
        .buttonStyle(.plain)
        .thingsTooltip("\(store.tr(view.titleKey)) (\(smartViewShortcut(for: view)))")
        .thingsDropTarget(cornerRadius: 6) { payloads in
            store.dropTasks(payloads, onto: dest)
        }
    }

    private func areaSection(_ area: Area) -> some View {
        let dest = NavigationDestination.area(area.id)
        let selected = store.selectedDestination == dest
        let nestedProjects = store.projectsForArea(area.id)
        let isCollapsed = collapsedAreaIds.contains(area.id)

        return VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                Button {
                    withAnimation(.easeOut(duration: 0.14)) {
                        if isCollapsed {
                            collapsedAreaIds.remove(area.id)
                        } else {
                            collapsedAreaIds.insert(area.id)
                        }
                    }
                } label: {
                    Image(systemName: isCollapsed ? "chevron.right" : "chevron.down")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(.tertiary)
                        .frame(width: 16, height: 16)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                Button {
                    store.selectedDestination = dest
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "folder.fill")
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .secondary)
                            .frame(width: 18)

                        Text(area.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .primary)
                            .lineLimit(1)

                        Spacer()
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .thingsTooltip("\(store.tr("entity_area")): \(area.title)")

                Button {
                    store.newProjectDefaultAreaId = area.id
                    store.isNewProjectSheetPresented = true
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .frame(width: 18, height: 18)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.trf("sidebar_new_project_in_area", area.title))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5.5)
            .background(
                Capsule(style: .continuous)
                    .fill(selected ? ThingsMacPalette.accentBlue.opacity(0.15) : Color.clear)
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(selected ? ThingsMacPalette.accentBlue.opacity(0.28) : Color.clear, lineWidth: 0.8)
            )
            .thingsDropTarget(isCapsule: true) { payloads in
                store.dropTasks(payloads, onto: dest)
            }
            .contextMenu {
                Button(store.tr("entity_new_project")) {
                    store.newProjectDefaultAreaId = area.id
                    store.isNewProjectSheetPresented = true
                }
                Button(store.tr("areas_rename_label")) {
                    store.editingArea = area
                }
                Divider()
                Button(role: .destructive) {
                    try? store.database.deleteArea(area.id)
                    if store.selectedDestination == dest {
                        store.selectedDestination = .smartView(.today)
                    }
                } label: {
                    Text(store.tr("action_delete"))
                }
            }

            if !isCollapsed {
                ForEach(nestedProjects) { proj in
                    projectRow(proj, indented: true)
                }
            }
        }
    }

    private func projectRow(_ proj: Project, indented: Bool = false) -> some View {
        let dest = NavigationDestination.project(proj.id)
        let selected = store.selectedDestination == dest
        let stalledInfo = store.projectStalledInfo(proj)
        let statusHelpText: String? = {
            if stalledInfo.hasNoNextAction {
                return store.tr("v2_stalled_project_badge")
            } else if stalledInfo.isUnreviewed14Days {
                return store.tr("v2_requires_review_badge")
            } else {
                return nil
            }
        }()

        return Button {
            store.selectedDestination = dest
        } label: {
            HStack(spacing: 7) {
                Text(proj.title)
                    .font(.system(size: 13, weight: selected ? .semibold : .regular))
                    .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .primary)
                    .lineLimit(1)

                Spacer(minLength: 4)

                if let statusHelpText {
                    Circle()
                        .fill(ThingsMacPalette.somedayAmber)
                        .frame(width: 6, height: 6)
                        .thingsTooltip(statusHelpText)
                }

                if proj.deadline != nil {
                    Image(systemName: "flag")
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                }

                ProjectProgressRing(
                    progress: proj.progress,
                    size: 15,
                    tint: ThingsMacPalette.accentBlue,
                    isCompleted: proj.status == .completed
                )
            }
            .padding(.leading, indented ? 26 : 12)
            .padding(.trailing, 10)
            .padding(.vertical, 5.5)
            .background(
                Capsule(style: .continuous)
                    .fill(selected ? ThingsMacPalette.accentBlue.opacity(0.15) : Color.clear)
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(selected ? ThingsMacPalette.accentBlue.opacity(0.28) : Color.clear, lineWidth: 0.8)
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .thingsTooltip(statusHelpText.map { "\(proj.title) — \($0)" } ?? proj.title)
        .thingsDropTarget(isCapsule: true) { payloads in
            store.dropTasks(payloads, onto: dest)
        }
        .contextMenu {
            Button(store.tr("project_edit_title")) {
                store.editingProject = proj
            }
            Button(store.tr("v2_save_as_template")) {
                store.saveProjectAsTemplate(proj.id)
            }
            Menu(store.tr("v30_external_export_section")) {
                Button(store.tr("v30_export_asana_csv")) {
                    store.exportProjectExternalInteractively(projectId: proj.id, format: .asanaCsv)
                }
                Button(store.tr("v30_export_asana_json")) {
                    store.exportProjectExternalInteractively(projectId: proj.id, format: .asanaJson)
                }
                Divider()
                Button(store.tr("v30_export_planner_xlsx")) {
                    store.exportProjectExternalInteractively(projectId: proj.id, format: .plannerXlsx)
                }
                Button(store.tr("v30_export_planner_csv")) {
                    store.exportProjectExternalInteractively(projectId: proj.id, format: .plannerCsv)
                }
            }
            Button(store.tr("v2_mark_reviewed")) {
                store.markProjectReviewed(proj.id)
            }
            Button(store.tr("action_duplicate_project")) {
                if let copy = try? store.database.duplicateProject(proj.id, copySuffix: store.tr("v25_copy_suffix")) {
                    store.selectedDestination = .project(copy.id)
                }
            }
            Button(store.tr("macos_complete_project")) {
                try? store.database.setProjectStatus(projectId: proj.id, status: .completed)
            }
            Divider()
            Button(role: .destructive) {
                store.deleteProjectWithUndo(proj)
            } label: {
                Text(store.tr("macos_delete_project"))
            }
        }
    }

    private var perspectivesSection: some View {
        let perspectives = store.sortedPerspectives
        return VStack(alignment: .leading, spacing: 3) {
            Divider()
                .padding(.vertical, 3)
                .padding(.horizontal, 4)

            HStack(spacing: 6) {
                Image(systemName: "line.3.horizontal.decrease.circle")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(.secondary)
                Text(store.tr("v30_perspectives_section"))
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Spacer()

                Button {
                    store.editingPerspective = nil
                    store.isPerspectiveEditorPresented = true
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .frame(width: 18, height: 18)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("v30_perspective_new"))
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 2)

            ForEach(perspectives) { perspective in
                perspectiveRow(perspective)
            }
        }
    }

    private func perspectiveRow(_ perspective: SavedPerspective) -> some View {
        let dest = NavigationDestination.perspective(perspective.id)
        let selected = store.selectedDestination == dest
        let count = store.tasksForPerspective(perspective).count

        return Button {
            store.selectedDestination = dest
        } label: {
            HStack(spacing: 7) {
                Image(systemName: selected ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .secondary)
                    .frame(width: 18)

                Text(perspective.title)
                    .font(.system(size: 13, weight: selected ? .semibold : .regular))
                    .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .primary)
                    .lineLimit(1)

                Spacer(minLength: 4)

                if count > 0 {
                    Text("\(count)")
                        .font(.system(size: 11.5, weight: .semibold, design: .rounded))
                        .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .secondary)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5.5)
            .background(
                Capsule(style: .continuous)
                    .fill(selected ? ThingsMacPalette.accentBlue.opacity(0.15) : Color.clear)
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(selected ? ThingsMacPalette.accentBlue.opacity(0.28) : Color.clear, lineWidth: 0.8)
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .thingsTooltip(perspective.title)
        .contextMenu {
            Button(store.tr("v30_perspective_edit")) {
                store.editingPerspective = perspective
                store.isPerspectiveEditorPresented = true
            }
            Divider()
            Button(role: .destructive) {
                store.deletePerspective(perspective.id)
            } label: {
                Text(store.tr("v30_perspective_delete"))
            }
        }
    }
}
