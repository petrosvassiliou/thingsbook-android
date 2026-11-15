import SwiftUI
import ThingsbookCore

public struct TemplatesAndImportSheetView: View {
    @EnvironmentObject private var store: ThingsbookStore
    @Environment(\.dismiss) private var dismiss

    @State private var selectedTab: TemplatesSheetTab = .gallery
    @State private var selectedAreaId: String? = nil
    @State private var rawOutlineText: String = ""

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
                }
            }
            .background(ThingsMacPalette.canvasBackground)

            Divider()
            footerBar
        }
        .frame(width: 740, height: 590)
        .background(ThingsMacPalette.cardSurface)
        .onAppear {
            selectedTab = store.templatesInitialTab
            if case .area(let aid) = store.selectedDestination {
                selectedAreaId = aid
            } else if let aid = store.newProjectDefaultAreaId {
                selectedAreaId = aid
            }
        }
    }

    private var headerBar: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(ThingsMacPalette.anytimeTeal.opacity(0.15))
                    .frame(width: 36, height: 36)
                Image(systemName: "square.on.square.badge.person.crop")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.anytimeTeal)
            }

            Picker("", selection: $selectedTab) {
                Text(store.tr("v2_templates_title")).tag(TemplatesSheetTab.gallery)
                Text(store.tr("v2_import_markdown_title")).tag(TemplatesSheetTab.importOutline)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .frame(maxWidth: 360)

            Spacer()

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
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .background(
                    Capsule(style: .continuous)
                        .fill(Color.primary.opacity(0.06))
                )
            }
            .menuStyle(.borderlessButton)
            .fixedSize()

            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .help(store.tr("action_cancel"))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 13)
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
                            Image(systemName: "square.stack.3d.up")
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
                    .help(store.tr("action_delete"))
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
                    Image(systemName: "chart.pie.fill")
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
        }
    }
}
