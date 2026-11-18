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

    public var body: some View {
        VStack(spacing: 0) {
            // Brand Header (Icon B Glacier Liquid Glass Book Emblem)
            HStack(spacing: 9) {
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
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 10)

            ScrollView {
                VStack(alignment: .leading, spacing: 2) {
                    // Primary GTD Smart Views
                    smartViewRow(.inbox, count: store.inboxTasks.count)
                        .padding(.bottom, 6)

                    smartViewRow(.today, count: store.todayTotalBadgeCount)
                    smartViewRow(.upcoming, count: store.upcomingTasks.count)
                    smartViewRow(.anytime, count: store.anytimeTasks.count)
                    smartViewRow(.someday, count: store.somedayTasks.count)

                    weeklyReviewRow

                    Divider()
                        .padding(.vertical, 8)
                        .padding(.horizontal, 10)

                    smartViewRow(.logbook, count: 0)
                    smartViewRow(.trash, count: store.trashedTasks.count + store.trashedProjects.count)

                    // Areas -> Nested Projects Hierarchy (Primary 3.0 Structure)
                    if !store.areas.isEmpty {
                        Divider()
                            .padding(.vertical, 8)
                            .padding(.horizontal, 10)

                        ForEach(store.areas) { area in
                            areaSection(area)
                                .padding(.bottom, 6)
                        }
                    }

                    // Legacy / Unassigned Projects (not yet placed inside an Area)
                    if !store.standaloneActiveProjects.isEmpty {
                        Divider()
                            .padding(.vertical, 8)
                            .padding(.horizontal, 10)

                        ForEach(store.standaloneActiveProjects) { proj in
                            projectRow(proj)
                        }
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
            }

            Divider()

            // Bottom Sidebar Bar (+ New Project defaults into active/first Area, Areas X/8)
            HStack(spacing: 10) {
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
                        store.isNewAreaSheetPresented = true
                    } label: {
                        Label(store.tr("entity_new_area"), systemImage: "cube")
                    }
                    .disabled(store.areas.count >= ThingsSQLiteDatabase.maxAreas)

                    Divider()

                    Button {
                        store.geminiInitialTab = .projectArchitect
                        store.isGeminiSheetPresented = true
                    } label: {
                        Label(store.tr("gemini_project_architect_title"), systemImage: "sparkles")
                    }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "plus")
                            .font(.system(size: 11.5, weight: .semibold))
                        Text(store.tr("entity_new_project"))
                            .font(.system(size: 12.5, weight: .semibold))
                            .lineLimit(1)
                    }
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                }
                .menuStyle(.borderlessButton)
                .fixedSize()
                .thingsTooltip(store.tr("entity_new_project"))

                Spacer()

                Button {
                    store.isAreaManagerPresented = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "cube")
                            .font(.system(size: 10.5))
                        Text("\(store.areas.count)/\(ThingsSQLiteDatabase.maxAreas)")
                            .font(.system(size: 11, weight: .medium, design: .rounded))
                    }
                    .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.trf("areas_active_count", store.areas.count, ThingsSQLiteDatabase.maxAreas))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .background {
            ZStack {
                Rectangle().fill(.ultraThinMaterial)
                Rectangle().fill(ThingsMacPalette.subtleContainer.opacity(0.55))
            }
        }
        .overlay(alignment: .trailing) {
            Rectangle()
                .fill(Color.primary.opacity(0.08))
                .frame(width: 1)
        }
    }

    private var weeklyReviewRow: some View {
        let stalledCount = store.stalledProjects.count
        return Button {
            store.isWeeklyReviewPresented = true
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "checklist.checked")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .frame(width: 20)

                Text(store.tr("v2_weekly_review_title"))
                    .font(.system(size: 13.5, weight: .regular))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer()

                if stalledCount > 0 {
                    Text("\(stalledCount)")
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6.5)
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .thingsTooltip(store.tr("v2_weekly_review_title") + " (⇧⌘R)")
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

    private func smartViewRow(_ view: SmartView, count: Int) -> some View {
        let dest = NavigationDestination.smartView(view)
        let selected = store.selectedDestination == dest
        return Button {
            store.selectedDestination = dest
        } label: {
            HStack(spacing: 10) {
                Image(systemName: view.sfSymbol)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.color(for: view, isSelected: selected))
                    .frame(width: 20)

                Text(store.tr(view.titleKey))
                    .font(.system(size: 13.5, weight: selected ? .semibold : .regular))
                    .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .primary)
                    .lineLimit(1)

                Spacer()

                if count > 0 {
                    Text("\(count)")
                        .font(.system(size: 12, weight: selected ? .bold : .medium, design: .rounded))
                        .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .secondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6.5)
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
        .thingsTooltip("\(store.tr(view.titleKey)) (\(smartViewShortcut(for: view)))")
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
                        Image(systemName: "cube")
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
        let openCount = max(0, proj.totalTaskCount - proj.completedTaskCount)
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
            HStack(spacing: 8) {
                ProjectProgressRing(
                    progress: proj.progress,
                    size: 15,
                    tint: ThingsMacPalette.accentBlue,
                    isCompleted: proj.status == .completed
                )
                .frame(width: 20)

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

                if openCount > 0 {
                    Text("\(openCount)")
                        .font(.system(size: 11.5, weight: .medium, design: .rounded))
                        .foregroundStyle(selected ? ThingsMacPalette.accentBlue : .secondary)
                }
            }
            .padding(.leading, indented ? 26 : 12)
            .padding(.trailing, 12)
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
        .contextMenu {
            Button(store.tr("project_edit_title")) {
                store.editingProject = proj
            }
            Button(store.tr("v2_save_as_template")) {
                store.saveProjectAsTemplate(proj.id)
            }
            Button(store.tr("v2_mark_reviewed")) {
                store.markProjectReviewed(proj.id)
            }
            Button(store.tr("action_duplicate_project")) {
                if let copy = try? store.database.duplicateProject(proj.id, copySuffix: store.tr("macos_copy_suffix")) {
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
}
