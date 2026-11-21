import SwiftUI
import ThingsbookCore

public struct TemplatesAndImportSheetView: View {
    @EnvironmentObject private var store: ThingsbookStore
    @Environment(\.dismiss) private var dismiss

    @State private var selectedTab: TemplatesSheetTab = .gallery
    @State private var selectedAreaId: String? = nil
    @State private var rawOutlineText: String = ""
    @State private var selectedExportProjectId: String = ""

    private static let sampleMarkdownOutline = """
    # Product Launch 2.0
    ## Readiness & QA
    - [ ] Run clean install & upgrade checks
      - [ ] Verify v1.8.1 SQLite upgrade
      - [ ] Verify .tbbackup restore
    - [ ] Review dark & light mode polish
    ## Release Day
    - [ ] Tag release & publish Universal DMG
    - [ ] Share changelog with early testers
    """

    public init() {}

    private var parsedOutlinePreview: (title: String, notes: String, blueprint: ProjectTemplateBlueprint) {
        ThingsSQLiteDatabase.parseOutlineToBlueprint(
            rawOutlineText,
            defaultTitle: store.tr("entity_new_project")
        )
    }

    private var activeExportableProjects: [Project] {
        store.projects.filter { !$0.isDeleted && $0.status == .active }
    }

    public var body: some View {
        VStack(spacing: 0) {
            headerBar
            Divider()

            Group {
                switch selectedTab {
                case .gallery:
                    templatesGalleryContent
                case .importOutline:
                    markdownImporterContent
                case .externalImportExport:
                    externalImportExportContent
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(ThingsMacPalette.canvasBackground)

            Divider()
            footerBar
        }
        .frame(width: 780, height: 610)
        .background(ThingsMacPalette.cardSurface)
        .onAppear {
            selectedTab = store.templatesInitialTab
            if let overrideAreaId = store.newProjectDefaultAreaId {
                selectedAreaId = overrideAreaId
                store.newProjectDefaultAreaId = nil
            } else if case .area(let aid) = store.selectedDestination {
                selectedAreaId = aid
            } else if case .project(let pid) = store.selectedDestination,
                      let projAreaId = store.project(byId: pid)?.areaId {
                selectedAreaId = projAreaId
            }
            syncDefaultExportProjectSelection()
        }
        .onChange(of: store.projects) { _, _ in
            syncDefaultExportProjectSelection()
        }
    }

    private func syncDefaultExportProjectSelection() {
        let projects = activeExportableProjects
        if !selectedExportProjectId.isEmpty,
           projects.contains(where: { $0.id == selectedExportProjectId }) {
            return
        }
        if case .project(let pid) = store.selectedDestination,
           projects.contains(where: { $0.id == pid }) {
            selectedExportProjectId = pid
        } else {
            selectedExportProjectId = projects.first?.id ?? ""
        }
    }

    private var headerBar: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(ThingsMacPalette.anytimeTeal.opacity(0.15))
                    .frame(width: 36, height: 36)
                Image(systemName: "square.on.square.badge.person.crop")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.anytimeTeal)
            }

            // Non-truncating 3-tab Soft Bento selector (sizes naturally in EN, DE, FR, IT, ES)
            HStack(spacing: 4) {
                sheetTabPill(.gallery, title: store.tr("v2_templates_title"))
                sheetTabPill(.importOutline, title: store.tr("v2_import_markdown_title"))
                sheetTabPill(.externalImportExport, title: store.tr("v30_external_import_export_tab"))
            }
            .padding(3.5)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.8)
            )
            .fixedSize(horizontal: true, vertical: false)

            Spacer(minLength: 8)

            Menu {
                Button(store.tr("areas_none")) {
                    selectedAreaId = nil
                }
                if !store.areas.isEmpty {
                    Divider()
                    ForEach(store.areas) { area in
                        Button(area.title) {
                            selectedAreaId = area.id
                        }
                    }
                }
            } label: {
                HStack(spacing: 5) {
                    Image(systemName: "tray.2.fill")
                        .font(.system(size: 11))
                        .foregroundStyle(ThingsMacPalette.areaGreen)
                    Text(store.area(byId: selectedAreaId)?.title ?? store.tr("areas_assign_chip"))
                        .font(.system(size: 12, weight: .medium))
                        .fixedSize(horizontal: true, vertical: false)
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .background(
                    Capsule(style: .continuous)
                        .fill(Color.primary.opacity(0.06))
                )
            }
            .menuStyle(.borderlessButton)
            .fixedSize(horizontal: true, vertical: false)

            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .thingsTooltip(store.tr("action_cancel"))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 13)
    }

    private func sheetTabPill(_ tab: TemplatesSheetTab, title: String) -> some View {
        let isSelected = selectedTab == tab
        return Button {
            withAnimation(.easeOut(duration: 0.15)) {
                selectedTab = tab
            }
        } label: {
            Text(title)
                .font(.system(size: 12, weight: isSelected ? .semibold : .medium))
                .foregroundStyle(isSelected ? ThingsMacPalette.accentBlue : .primary.opacity(0.82))
                .fixedSize(horizontal: true, vertical: false)
                .padding(.horizontal, 10)
                .padding(.vertical, 5.5)
                .background(
                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                        .fill(isSelected ? ThingsMacPalette.cardSurface : Color.clear)
                        .shadow(color: Color.black.opacity(isSelected ? 0.06 : 0.0), radius: 3, y: 1)
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
        .fixedSize(horizontal: true, vertical: false)
    }

    // MARK: - Tab 1: Starter & Saved Templates Gallery

    private var templatesGalleryContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 10) {
                    sectionHeader(icon: "sparkles.rectangle.stack.fill", color: ThingsMacPalette.accentBlue, title: store.tr("v2_starter_templates"))

                    let starterTemplates = ProjectTemplate.builtInStarterTemplates(for: store.preferences.language.resolvedCode)
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(starterTemplates) { template in
                            templateCard(template, isCustom: false)
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 10) {
                    sectionHeader(icon: "bookmark.square.fill", color: ThingsMacPalette.anytimeTeal, title: store.tr("v2_my_templates"))

                    if store.projectTemplates.isEmpty {
                        HStack {
                            Image(systemName: "square.on.square")
                                .foregroundStyle(.secondary)
                            Text(store.tr("empty_view"))
                                .font(.system(size: 12.5))
                                .foregroundStyle(.secondary)
                            Spacer()
                        }
                        .padding(14)
                        .thingsCardSurface(cornerRadius: 10)
                    } else {
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                            ForEach(store.projectTemplates) { template in
                                templateCard(template, isCustom: true)
                            }
                        }
                    }
                }
            }
            .padding(20)
        }
    }

    private func templateCard(_ template: ProjectTemplate, isCustom: Bool) -> some View {
        let bp = template.blueprint
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(template.title)
                        .font(.system(size: 14, weight: .semibold))
                        .lineLimit(1)

                    if !template.notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Text(template.notes.trimmingCharacters(in: .whitespacesAndNewlines))
                            .font(.system(size: 11.5))
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                }
                Spacer()

                if isCustom {
                    Button(role: .destructive) {
                        store.deleteCustomTemplate(template.id)
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 11.5))
                            .foregroundStyle(ThingsMacPalette.upcomingRed)
                    }
                    .buttonStyle(.borderless)
                    .thingsTooltip(store.tr("action_delete"))
                }
            }

            HStack(spacing: 8) {
                Text("\(bp.headings.count) · \(store.tr("entity_heading"))")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2.5)
                    .background(Capsule().fill(Color.primary.opacity(0.06)))

                Text("\(bp.totalTaskCount) · \(store.tr("entity_todos"))")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2.5)
                    .background(Capsule().fill(Color.primary.opacity(0.06)))

                Spacer()
            }

            Button {
                if store.createProjectFromTemplate(template, areaIdOverride: selectedAreaId) != nil {
                    dismiss()
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 12))
                    Text(store.tr("v2_create_from_template"))
                        .font(.system(size: 12, weight: .semibold))
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .thingsCardSurface(cornerRadius: 11)
    }

    // MARK: - Tab 2: Markdown / JSON Outline Importer

    private var markdownImporterContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(store.tr("v2_import_markdown_hint"))
                    .font(.system(size: 12.5))
                    .foregroundStyle(.secondary)
                Spacer()
                if rawOutlineText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Button("# Markdown") {
                        rawOutlineText = Self.sampleMarkdownOutline
                    }
                    .controlSize(.small)
                } else {
                    Button(store.tr("action_clear")) {
                        rawOutlineText = ""
                    }
                    .controlSize(.small)
                }
            }

            ZStack(alignment: .topLeading) {
                TextEditor(text: $rawOutlineText)
                    .font(.system(size: 12.5, design: .monospaced))
                    .scrollContentBackground(.hidden)
                    .padding(10)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(ThingsMacPalette.cardSurface)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.10), lineWidth: 1)
                    )

                if rawOutlineText.isEmpty {
                    Text(Self.sampleMarkdownOutline)
                        .font(.system(size: 12.5, design: .monospaced))
                        .foregroundStyle(.tertiary)
                        .padding(16)
                        .allowsHitTesting(false)
                }
            }
            .frame(maxHeight: .infinity)

            if !rawOutlineText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                let preview = parsedOutlinePreview
                HStack(spacing: 10) {
                    Image(systemName: "list.bullet.rectangle")
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                    Text(preview.title)
                        .font(.system(size: 13, weight: .semibold))
                        .lineLimit(1)
                    Spacer()
                    Text("\(preview.blueprint.headings.count) · \(store.tr("entity_heading"))")
                        .font(.system(size: 11.5, weight: .medium))
                        .foregroundStyle(.secondary)
                    Text("\(preview.blueprint.totalTaskCount) · \(store.tr("entity_todos"))")
                        .font(.system(size: 11.5, weight: .medium))
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .thingsCardSurface(cornerRadius: 9)
            }
        }
        .padding(20)
    }

    // MARK: - Tab 3: Asana & Microsoft Planner Offline Import / Export

    private var externalImportExportContent: some View {
        let projects = activeExportableProjects
        let resolvedProjectId = projects.contains(where: { $0.id == selectedExportProjectId })
            ? selectedExportProjectId
            : (projects.first?.id ?? "")

        return ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                // 1. Offline Import Soft Bento Card
                VStack(alignment: .leading, spacing: 12) {
                    sectionHeader(
                        icon: "arrow.down.doc.fill",
                        color: ThingsMacPalette.accentBlue,
                        title: store.tr("v30_external_import_section")
                    )

                    Text(store.tr("v30_external_import_desc"))
                        .font(.system(size: 12.5))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)

                    // Prominent Target Area Selector right above Asana / Planner import actions
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 6) {
                            Image(systemName: "tray.2.fill")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(ThingsMacPalette.areaGreen)
                            Text(store.tr("v30_external_import_target_area"))
                                .font(.system(size: 11.5, weight: .bold))
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 0)
                        }

                        ThingsFlowLayout(horizontalSpacing: 7, verticalSpacing: 7) {
                            targetAreaImportPill(
                                title: store.tr("areas_none"),
                                systemImage: "tray",
                                isSelected: selectedAreaId == nil
                            ) {
                                withAnimation(.easeOut(duration: 0.14)) {
                                    selectedAreaId = nil
                                }
                            }

                            ForEach(store.areas) { area in
                                targetAreaImportPill(
                                    title: area.title,
                                    systemImage: "tray.2.fill",
                                    isSelected: selectedAreaId == area.id
                                ) {
                                    withAnimation(.easeOut(duration: 0.14)) {
                                        selectedAreaId = area.id
                                    }
                                }
                            }
                        }
                    }
                    .padding(11)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(ThingsMacPalette.subtleContainer)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .strokeBorder(Color.primary.opacity(0.07), lineWidth: 0.8)
                            )
                    )

                    ThingsFlowLayout(horizontalSpacing: 10, verticalSpacing: 10) {
                        softBentoActionButton(
                            title: store.tr("v30_import_asana"),
                            systemImage: "tray.and.arrow.down.fill",
                            tint: ThingsMacPalette.accentBlue,
                            prominent: true
                        ) {
                            if store.importAsanaProjectInteractively(areaIdOverride: selectedAreaId) {
                                dismiss()
                            }
                        }

                        softBentoActionButton(
                            title: store.tr("v30_import_planner"),
                            systemImage: "tablecells.badge.ellipsis",
                            tint: ThingsMacPalette.anytimeTeal,
                            prominent: true
                        ) {
                            if store.importPlannerProjectInteractively(areaIdOverride: selectedAreaId) {
                                dismiss()
                            }
                        }
                    }
                    .padding(.top, 2)
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .thingsCardSurface(cornerRadius: 12)

                // 2. Project Export Soft Bento Card
                VStack(alignment: .leading, spacing: 14) {
                    sectionHeader(
                        icon: "square.and.arrow.up.fill",
                        color: ThingsMacPalette.anytimeTeal,
                        title: store.tr("v30_external_export_section")
                    )

                    if projects.isEmpty {
                        HStack(spacing: 8) {
                            Image(systemName: "folder.badge.questionmark")
                                .foregroundStyle(.secondary)
                            Text(store.tr("v30_external_no_projects"))
                                .font(.system(size: 12.5))
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer()
                        }
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 9, style: .continuous)
                                .fill(ThingsMacPalette.subtleContainer)
                        )
                    } else {
                        HStack(spacing: 10) {
                            Text(store.tr("v30_external_select_project"))
                                .font(.system(size: 12.5, weight: .semibold))
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: true, vertical: false)

                            Picker(store.tr("v30_external_select_project"), selection: $selectedExportProjectId) {
                                ForEach(projects) { proj in
                                    Text(proj.title).tag(proj.id)
                                }
                            }
                            .labelsHidden()
                            .pickerStyle(.menu)
                            .frame(maxWidth: 340)

                            Spacer()
                        }

                        ThingsFlowLayout(horizontalSpacing: 10, verticalSpacing: 10) {
                            softBentoActionButton(
                                title: store.tr("v30_export_asana_csv"),
                                systemImage: "doc.text",
                                tint: ThingsMacPalette.accentBlue,
                                prominent: false
                            ) {
                                guard !resolvedProjectId.isEmpty else { return }
                                store.exportProjectExternalInteractively(
                                    projectId: resolvedProjectId,
                                    format: .asanaCsv
                                )
                            }

                            softBentoActionButton(
                                title: store.tr("v30_export_asana_json"),
                                systemImage: "curlybraces",
                                tint: ThingsMacPalette.accentBlue,
                                prominent: false
                            ) {
                                guard !resolvedProjectId.isEmpty else { return }
                                store.exportProjectExternalInteractively(
                                    projectId: resolvedProjectId,
                                    format: .asanaJson
                                )
                            }

                            softBentoActionButton(
                                title: store.tr("v30_export_planner_xlsx"),
                                systemImage: "tablecells",
                                tint: ThingsMacPalette.anytimeTeal,
                                prominent: false
                            ) {
                                guard !resolvedProjectId.isEmpty else { return }
                                store.exportProjectExternalInteractively(
                                    projectId: resolvedProjectId,
                                    format: .plannerXlsx
                                )
                            }

                            softBentoActionButton(
                                title: store.tr("v30_export_planner_csv"),
                                systemImage: "list.bullet.rectangle.portrait",
                                tint: ThingsMacPalette.anytimeTeal,
                                prominent: false
                            ) {
                                guard !resolvedProjectId.isEmpty else { return }
                                store.exportProjectExternalInteractively(
                                    projectId: resolvedProjectId,
                                    format: .plannerCsv
                                )
                            }
                        }
                    }
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .thingsCardSurface(cornerRadius: 12)
            }
            .padding(20)
        }
    }

    private func softBentoActionButton(
        title: String,
        systemImage: String,
        tint: Color,
        prominent: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: systemImage)
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(prominent ? tint : .primary.opacity(0.85))
                Text(title)
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(prominent ? tint : .primary)
                    .fixedSize(horizontal: true, vertical: false)
            }
            .padding(.horizontal, 13)
            .padding(.vertical, 8.5)
            .background(
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(prominent ? tint.opacity(0.13) : ThingsMacPalette.subtleContainer)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .strokeBorder(
                        prominent ? tint.opacity(0.36) : Color.primary.opacity(0.12),
                        lineWidth: 0.9
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
        }
        .buttonStyle(.plain)
        .fixedSize(horizontal: true, vertical: false)
    }

    private func targetAreaImportPill(
        title: String,
        systemImage: String,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        let tint = ThingsMacPalette.areaGreen
        return Button(action: action) {
            HStack(spacing: 5.5) {
                Image(systemName: systemImage)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(isSelected ? tint : .secondary)
                Text(title)
                    .font(.system(size: 12, weight: isSelected ? .semibold : .medium))
                    .foregroundStyle(isSelected ? tint : .primary.opacity(0.88))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
            .padding(.horizontal, 10.5)
            .padding(.vertical, 5.5)
            .background(
                Capsule(style: .continuous)
                    .fill(isSelected ? tint.opacity(0.15) : ThingsMacPalette.cardSurface)
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(
                        isSelected ? tint.opacity(0.45) : Color.primary.opacity(0.10),
                        lineWidth: isSelected ? 1.1 : 0.8
                    )
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .fixedSize(horizontal: true, vertical: false)
    }

    private var footerBar: some View {
        HStack {
            Spacer()
            Button(store.tr("action_done")) {
                dismiss()
            }

            if selectedTab == .importOutline {
                Button {
                    if store.importProjectFromOutline(rawOutlineText, areaId: selectedAreaId) != nil {
                        dismiss()
                    }
                } label: {
                    Label(store.tr("v2_import_create_project"), systemImage: "arrow.down.doc.fill")
                        .font(.system(size: 13, weight: .semibold))
                }
                .buttonStyle(.borderedProminent)
                .disabled(rawOutlineText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    private func sectionHeader(icon: String, color: Color, title: String) -> some View {
        HStack(spacing: 7) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(color)
            Text(title)
                .font(.system(size: 13, weight: .bold))
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
