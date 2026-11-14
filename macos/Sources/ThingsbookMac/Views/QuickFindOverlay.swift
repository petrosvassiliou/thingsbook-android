import SwiftUI
import ThingsbookCore

public struct QuickFindOverlay: View {
    @ObservedObject var store: ThingsbookStore
    @Environment(\.dismiss) private var dismiss

    @State private var query = ""
    @State private var matchedTasks: [TaskItem] = []
    @State private var matchedProjects: [Project] = []
    @FocusState private var isSearchFocused: Bool

    public init(store: ThingsbookStore) {
        self.store = store
    }

    private var matchingSmartViews: [SmartView] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return SmartView.allCases }
        return SmartView.allCases.filter { store.tr($0.titleKey).lowercased().contains(q) }
    }

    private var matchingAreas: [Area] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return store.areas }
        return store.areas.filter { $0.title.lowercased().contains(q) }
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(.secondary)

                TextField(store.tr("quick_find_placeholder"), text: $query)
                    .textFieldStyle(.plain)
                    .font(.system(size: 16))
                    .focused($isSearchFocused)
                    .onChange(of: query) { _, newValue in
                        runSearch(newValue)
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
                }

                Button("ESC") {
                    dismiss()
                }
                .font(.system(size: 10.5, weight: .semibold, design: .monospaced))
                .buttonStyle(.bordered)
                .controlSize(.mini)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 13)

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
                                    Image(systemName: "cube.fill")
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
                                    ProjectProgressRing(progress: proj.progress, size: 15)
                                        .frame(width: 20)
                                    Text(proj.title)
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
                                            .lineLimit(1)
                                        if !task.notes.isEmpty {
                                            Text(task.notes)
                                                .font(.system(size: 11.5))
                                                .foregroundStyle(.secondary)
                                                .lineLimit(1)
                                        }
                                    }
                                    Spacer()
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    } else if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
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
            .frame(maxHeight: 400)
        }
        .frame(width: 560)
        .background(ThingsMacPalette.cardSurface)
        .onAppear {
            isSearchFocused = true
        }
    }

    private func sectionHeader(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.system(size: 10.5, weight: .semibold))
            .foregroundStyle(.tertiary)
            .padding(.horizontal, 10)
    }

    private func runSearch(_ q: String) {
        let trimmed = q.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            matchedTasks = []
            matchedProjects = []
            return
        }
        if let res = try? store.database.searchQuickFind(rawQuery: trimmed) {
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
