import SwiftUI
import ThingsbookCore

public struct SidebarView: View {
    @ObservedObject var store: ThingsbookStore

    public init(store: ThingsbookStore) {
        self.store = store
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Top Quick Find Pill
            Button {
                store.isQuickFindPresented = true
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 12.5, weight: .medium))
                        .foregroundStyle(.secondary)
                    Text(store.tr("sidebar_quick_find"))
                        .font(.system(size: 12.5))
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text("⌘F")
                        .font(.system(size: 10.5, weight: .medium))
                        .foregroundStyle(.tertiary)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6.5)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(Color.primary.opacity(0.055))
                )
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 12)
            .padding(.top, 10)
            .padding(.bottom, 6)

            ScrollView {
                VStack(alignment: .leading, spacing: 2) {
                    // Primary GTD Smart Views
                    smartViewRow(.inbox, count: store.inboxTasks.count)
                        .padding(.bottom, 6)

                    smartViewRow(.today, count: store.todayTotalBadgeCount)
                    smartViewRow(.upcoming, count: store.upcomingTasks.count)
                    smartViewRow(.anytime, count: 0)
                    smartViewRow(.someday, count: 0)

                    Divider()
                        .padding(.vertical, 6)
                        .padding(.horizontal, 10)

                    smartViewRow(.logbook, count: 0)
                    if !store.trashedTasks.isEmpty || store.selectedDestination == .smartView(.trash) {
                        smartViewRow(.trash, count: store.trashedTasks.count)
                    }

                    // Standalone Projects
                    if !store.standaloneActiveProjects.isEmpty {
                        Divider()
                            .padding(.vertical, 6)
                            .padding(.horizontal, 10)

                        ForEach(store.standaloneActiveProjects) { proj in
                            projectRow(proj)
                        }
                    }

                    // Areas & Nested Projects
                    if !store.areas.isEmpty {
                        Divider()
                            .padding(.vertical, 6)
                            .padding(.horizontal, 10)

                        ForEach(store.areas) { area in
                            areaSection(area)
                                .padding(.bottom, 4)
                        }
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
            }

            Divider()

            // Bottom Sidebar Bar (Things for Mac: + New List, Cloud Status, Settings)
            HStack(spacing: 10) {
                Menu {
                    Button {
                        store.newProjectDefaultAreaId = nil
                        store.isNewProjectSheetPresented = true
                    } label: {
                        Label(store.tr("sidebar_new_project"), systemImage: "plus.circle")
                    }

                    Button {
                        store.isNewAreaSheetPresented = true
                    } label: {
                        Label(store.tr("sidebar_new_area"), systemImage: "cube")
                    }
                    .disabled(store.areas.count >= ThingsSQLiteDatabase.maxAreas)

                    Divider()

                    Button {
                        store.geminiInitialTab = .projectArchitect
                        store.isGeminiSheetPresented = true
                    } label: {
                        Label(store.tr("gemini_tab_architect"), systemImage: "sparkles")
                    }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "plus")
                            .font(.system(size: 12, weight: .semibold))
                        Text(store.tr("sidebar_new_list"))
                            .font(.system(size: 12.5, weight: .medium))
                    }
                    .foregroundStyle(.secondary)
                }
                .menuStyle(.borderlessButton)
                .fixedSize()

                Spacer()

                // Sync status indicator
                Button {
                    store.settingsTab = .sync
                    store.isSettingsPresented = true
                } label: {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(syncIndicatorColor)
                            .frame(width: 6.5, height: 6.5)
                        Image(systemName: store.preferences.localOnlyDbMode ? "internaldrive" : "icloud")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }
                }
                .buttonStyle(.plain)
                .help(syncHelpLabel)

                Button {
                    store.settingsTab = .general
                    store.isSettingsPresented = true
                } label: {
                    Image(systemName: "slider.horizontal.3")
                        .font(.system(size: 12.5))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .help(store.tr("settings_title"))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 9)
        }
        .background(.ultraThinMaterial)
    }

    private func smartViewRow(_ view: SmartView, count: Int) -> some View {
        let dest = NavigationDestination.smartView(view)
        let selected = store.selectedDestination == dest
        return Button {
            store.selectedDestination = dest
        } label: {
            HStack(spacing: 9) {
                Image(systemName: view.sfSymbol)
                    .font(.system(size: 13.5, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.color(for: view))
                    .frame(width: 20)

                Text(store.tr(view.titleKey))
                    .font(.system(size: 13, weight: selected ? .semibold : .regular))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer()

                if count > 0 {
                    Text("\(count)")
                        .font(.system(size: 11.5, weight: .semibold, design: .rounded))
                        .foregroundStyle(selected ? .primary : .secondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .background(
                            Capsule().fill(selected ? Color.primary.opacity(0.10) : Color.clear)
                        )
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5.5)
            .background(
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .fill(selected ? Color.primary.opacity(0.09) : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func areaSection(_ area: Area) -> some View {
        let dest = NavigationDestination.area(area.id)
        let selected = store.selectedDestination == dest
        let nestedProjects = store.projectsForArea(area.id)

        return VStack(alignment: .leading, spacing: 1) {
            HStack(spacing: 8) {
                Button {
                    store.selectedDestination = dest
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "cube.fill")
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.areaGreen)
                            .frame(width: 20)

                        Text(area.title)
                            .font(.system(size: 12.5, weight: .semibold))
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
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
                .help(store.tr("sidebar_new_project"))
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .background(
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .fill(selected ? Color.primary.opacity(0.09) : Color.clear)
            )
            .contextMenu {
                Button(store.tr("sidebar_new_project")) {
                    store.newProjectDefaultAreaId = area.id
                    store.isNewProjectSheetPresented = true
                }
                Button(store.tr("area_rename_action")) {
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

        return Button {
            store.selectedDestination = dest
        } label: {
            HStack(spacing: 8) {
                ProjectProgressRing(
                    progress: proj.progress,
                    size: 14.5,
                    tint: ThingsMacPalette.accentBlue,
                    isCompleted: proj.status == .completed
                )
                .frame(width: 20)

                Text(proj.title)
                    .font(.system(size: 12.8, weight: selected ? .semibold : .regular))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer()

                if openCount > 0 {
                    Text("\(openCount)")
                        .font(.system(size: 11, design: .rounded))
                        .foregroundStyle(.tertiary)
                }
            }
            .padding(.leading, indented ? 18 : 8)
            .padding(.trailing, 8)
            .padding(.vertical, 4.5)
            .background(
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .fill(selected ? Color.primary.opacity(0.09) : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button(store.tr("project_menu_edit")) {
                store.editingProject = proj
            }
            Button(store.tr("project_menu_duplicate")) {
                if let copy = try? store.database.duplicateProject(proj.id, copySuffix: store.tr("project_copy_suffix")) {
                    store.selectedDestination = .project(copy.id)
                }
            }
            Button(store.tr("project_complete_action")) {
                try? store.database.setProjectStatus(projectId: proj.id, status: .completed)
            }
            Divider()
            Button(role: .destructive) {
                store.deleteProjectWithUndo(proj)
            } label: {
                Text(store.tr("project_menu_delete"))
            }
        }
    }

    private var syncIndicatorColor: Color {
        if store.preferences.localOnlyDbMode { return .secondary }
        switch store.syncSnapshot.status {
        case .synced: return ThingsMacPalette.logbookGreen
        case .syncing: return ThingsMacPalette.todayYellow
        case .offline: return .secondary.opacity(0.55)
        }
    }

    private var syncHelpLabel: String {
        if store.preferences.localOnlyDbMode {
            return store.tr("settings_db_mode_local")
        }
        switch store.syncSnapshot.status {
        case .synced: return store.tr("sync_status_synced")
        case .syncing: return store.tr("sync_status_syncing")
        case .offline: return store.tr("sync_status_offline")
        }
    }
}
