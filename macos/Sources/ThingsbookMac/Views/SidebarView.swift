import SwiftUI
import ThingsbookCore

public struct SidebarView: View {
    @ObservedObject var store: ThingsbookStore

    public init(store: ThingsbookStore) {
        self.store = store
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Brand Header
            HStack(spacing: 9) {
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 24, height: 24)
                    .overlay {
                        Image(systemName: "checkmark.square")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(.white)
                    }

                Text(store.tr("app_name"))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.primary)

                if AppUpdateService.currentVersionIsPrerelease {
                    Text(store.tr("v2_beta_badge"))
                        .font(.system(size: 9.5, weight: .bold, design: .rounded))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 5.5)
                        .padding(.vertical, 1.5)
                        .background(Capsule().fill(ThingsMacPalette.accentBlue.opacity(0.14)))
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

                    // Standalone Projects
                    if !store.standaloneActiveProjects.isEmpty {
                        Divider()
                            .padding(.vertical, 8)
                            .padding(.horizontal, 10)

                        ForEach(store.standaloneActiveProjects) { proj in
                            projectRow(proj)
                        }
                    }

                    // Areas & Nested Projects
                    if !store.areas.isEmpty {
                        Divider()
                            .padding(.vertical, 8)
                            .padding(.horizontal, 10)

                        ForEach(store.areas) { area in
                            areaSection(area)
                                .padding(.bottom, 6)
                        }
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
            }

            Divider()

            // Bottom Sidebar Bar (+ New List, Areas X/8, Settings)
            HStack(spacing: 10) {
                Menu {
                    Button {
                        store.newProjectDefaultAreaId = nil
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
                            .font(.system(size: 12.5, weight: .medium))
                            .lineLimit(1)
                    }
                    .foregroundStyle(.secondary)
                }
                .menuStyle(.borderlessButton)
                .fixedSize()

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
                    .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
                .help(store.trf("areas_active_count", store.areas.count, ThingsSQLiteDatabase.maxAreas))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .background(.ultraThinMaterial)
    }

    private var weeklyReviewRow: some View {
        let stalledCount = store.stalledProjects.count
        return Button {
            store.isWeeklyReviewPresented = true
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "checklist.checked")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .frame(width: 20)

                Text(store.tr("v2_weekly_review_title"))
                    .font(.system(size: 13.5, weight: .regular))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer()

                if stalledCount > 0 {
                    Text("\(stalledCount)")
                        .font(.system(size: 11.5, weight: .bold, design: .rounded))
                        .foregroundStyle(ThingsMacPalette.somedayAmber)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .background(Capsule().fill(ThingsMacPalette.somedayAmber.opacity(0.14)))
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6.5)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .help(store.tr("v2_weekly_review_title") + " (⇧⌘R)")
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
                    .foregroundStyle(ThingsMacPalette.color(for: view))
                    .frame(width: 20)

                Text(store.tr(view.titleKey))
                    .font(.system(size: 13.5, weight: selected ? .semibold : .regular))
                    .foregroundStyle(.primary)
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
            .padding(.horizontal, 10)
            .padding(.vertical, 6.5)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(selected ? ThingsMacPalette.accentBlue.opacity(0.12) : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func areaSection(_ area: Area) -> some View {
        let dest = NavigationDestination.area(area.id)
        let selected = store.selectedDestination == dest
        let nestedProjects = store.projectsForArea(area.id)

        return VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 8) {
                Button {
                    store.selectedDestination = dest
                } label: {
                    HStack(spacing: 9) {
                        Image(systemName: "cube.fill")
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.areaGreen)
                            .frame(width: 20)

                        Text(area.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)

                        Spacer()
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

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
                .help(store.tr("entity_new_project"))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5.5)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(selected ? ThingsMacPalette.accentBlue.opacity(0.12) : Color.clear)
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

            ForEach(nestedProjects) { proj in
                projectRow(proj, indented: true)
            }
        }
    }

    private func projectRow(_ proj: Project, indented: Bool = false) -> some View {
        let dest = NavigationDestination.project(proj.id)
        let selected = store.selectedDestination == dest
        let openCount = max(0, proj.totalTaskCount - proj.completedTaskCount)
        let isStalled = store.projectStalledInfo(proj).isStalled

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
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer(minLength: 4)

                if isStalled {
                    Circle()
                        .fill(ThingsMacPalette.somedayAmber)
                        .frame(width: 6, height: 6)
                }

                if proj.deadline != nil {
                    Image(systemName: "flag")
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                }

                if openCount > 0 {
                    Text("\(openCount)")
                        .font(.system(size: 11.5, weight: .medium, design: .rounded))
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.leading, indented ? 20 : 10)
            .padding(.trailing, 10)
            .padding(.vertical, 5.5)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(selected ? ThingsMacPalette.accentBlue.opacity(0.12) : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .help(proj.title)
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
