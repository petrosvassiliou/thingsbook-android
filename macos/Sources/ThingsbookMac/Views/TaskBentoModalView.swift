import SwiftUI
import AppKit
import UniformTypeIdentifiers
import ThingsbookCore

public extension Notification.Name {
    static let thingsbookDismissTaskModal = Notification.Name("thingsbookDismissTaskModal")
}

public struct TaskBentoModalView: View {
    @ObservedObject var store: ThingsbookStore
    public let task: TaskItem
    public var availableHeight: CGFloat? = nil

    @StateObject private var voiceController = MacVoiceDictationController()
    @State private var draftTitle: String = ""
    @State private var draftNotes: String = ""
    @State private var newChecklistTitle: String = ""
    @State private var dictationTargetsTitle: Bool = false
    @State private var dictationBaseNotes: String = ""

    @State private var isWhenPopoverOpen = false
    @State private var isTimeblockPopoverOpen = false
    @State private var isDeadlinePopoverOpen = false
    @State private var isRepeatPopoverOpen = false
    @State private var isDependenciesPopoverOpen = false
    @State private var isTagPopoverOpen = false
    @State private var isMovePopoverOpen = false
    @State private var isCustomDurationPopoverOpen = false

    @State private var isGeneratingChecklist = false
    @State private var isEditingNotes = false
    @State private var isFileDropTargeted = false
    @State private var isHoveringInProgress = false
    @State private var isHoveringMilestone = false
    @State private var isHoveringPin = false
    @State private var isHoveringMic = false
    @State private var isHoveringMagic = false
    @State private var isHoveringAttachBar = false
    @State private var isHoveringDelete = false
    @State private var isHoveringDone = false
    @State private var isHoveringClose = false
    @State private var didCommitAndDismiss = false
    @State private var measuredBodyHeight: CGFloat = 0
    @State private var draftSaveTask: Task<Void, Never>?
    @State private var modalFlushToken = UUID()

    @FocusState private var isTitleFocused: Bool
    @FocusState private var isNotesFocused: Bool

    public init(store: ThingsbookStore, task: TaskItem, availableHeight: CGFloat? = nil) {
        self.store = store
        self.task = task
        self.availableHeight = availableHeight
    }

    private var resolvedMaxModalHeight: CGFloat {
        max(340, min(650, (availableHeight ?? 680) - 36))
    }

    private var parsedDraftSyntax: ParsedSmartSyntax {
        store.parseSmartSyntax(draftTitle)
    }

    private var resolvedMaxBodyHeight: CGFloat {
        max(210, resolvedMaxModalHeight - (parsedDraftSyntax.hasAnyMatch ? 142 : 106))
    }

    private var needsVerticalScroll: Bool {
        measuredBodyHeight > resolvedMaxBodyHeight + 1
    }

    private var effectiveBodyHeight: CGFloat {
        guard measuredBodyHeight > 0 else {
            return min(320, resolvedMaxBodyHeight)
        }
        return min(measuredBodyHeight, resolvedMaxBodyHeight)
    }

    private var latestTask: TaskItem {
        store.task(byId: task.id) ?? task
    }

    private var modalBodyContent: some View {
        AlignedBentoTwoColumnLayout(spacing: 14, rightWidth: 274) {
            leftPrimaryContentColumn
            rightPropertiesColumn
        }
        .padding(.horizontal, 18)
        .padding(.top, 12)
        .padding(.bottom, 13)
        .frame(maxWidth: .infinity, alignment: .topLeading)
    }

    public var body: some View {
        VStack(spacing: 0) {
            modalHeaderBar
                .fixedSize(horizontal: false, vertical: true)

            if parsedDraftSyntax.hasAnyMatch {
                SmartSyntaxPreviewPillsView(
                    store: store,
                    parsed: parsedDraftSyntax,
                    showDetectedLabel: true,
                    onApply: applyDraftSmartSyntax
                )
                .padding(.horizontal, 18)
                .padding(.bottom, 10)
                .fixedSize(horizontal: false, vertical: true)
            }

            Divider()
                .opacity(0.65)

            ScrollView(.vertical, showsIndicators: needsVerticalScroll) {
                modalBodyContent
                    .fixedSize(horizontal: false, vertical: true)
                    .background(
                        GeometryReader { geo in
                            Color.clear.preference(
                                key: ModalBodyHeightPreferenceKey.self,
                                value: ceil(geo.size.height)
                            )
                        }
                    )
            }
            .scrollDisabled(!needsVerticalScroll)
            .frame(height: effectiveBodyHeight)

            Divider()
                .opacity(0.65)

            modalFooterBar
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(width: 724)
        .fixedSize(horizontal: false, vertical: true)
        .onPreferenceChange(ModalBodyHeightPreferenceKey.self) { newHeight in
            guard newHeight > 0, abs(newHeight - measuredBodyHeight) > 0.5 else { return }
            var tx = Transaction()
            tx.disablesAnimations = true
            withTransaction(tx) {
                measuredBodyHeight = newHeight
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(ThingsMacPalette.cardSurface)
                .shadow(color: Color.black.opacity(0.24), radius: 34, x: 0, y: 14)
                .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 2)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(
                    isFileDropTargeted
                        ? ThingsMacPalette.accentBlue
                        : Color.primary.opacity(0.10),
                    lineWidth: isFileDropTargeted ? 1.8 : 0.9
                )
        )
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .onDrop(of: [UTType.fileURL], isTargeted: $isFileDropTargeted) { providers in
            commitDraftEditsIfNeeded()
            return store.handleFileDropProviders(providers, taskId: latestTask.id)
        }
        .onAppear {
            didCommitAndDismiss = false
            syncDrafts()
            applyInitialFocusRules()
            registerDraftFlushHook()
        }
        .onDisappear {
            draftSaveTask?.cancel()
            voiceController.stopRecording()
            if !didCommitAndDismiss {
                commitDraftEditsIfNeeded(commitPendingChecklist: true)
            }
            if store.flushOpenTaskModalToken == modalFlushToken {
                store.flushOpenTaskModalDrafts = nil
                store.flushOpenTaskModalToken = nil
            }
        }
        .onChange(of: task.id) { _, _ in
            draftSaveTask?.cancel()
            voiceController.stopRecording()
            didCommitAndDismiss = false
            syncDrafts()
            applyInitialFocusRules()
            registerDraftFlushHook()
        }
        .onChange(of: draftTitle) { _, _ in
            scheduleDraftAutoSave()
        }
        .onChange(of: draftNotes) { _, _ in
            scheduleDraftAutoSave()
        }
        .onReceive(NotificationCenter.default.publisher(for: .thingsbookDismissTaskModal)) { _ in
            closeModal()
        }
    }

    // MARK: - Zone A: Top Header Bar

    private var modalHeaderBar: some View {
        let current = latestTask

        return HStack(alignment: .center, spacing: 12) {
            ThingsCheckboxButton(
                status: current.status,
                isSomeday: current.scheduleState == .someday,
                isMilestone: current.isMilestone,
                isInProgress: current.isInProgress,
                helpText: store.tr("macos_checkbox_help"),
                onToggleComplete: {
                    commitDraftEditsIfNeeded(commitPendingChecklist: true)
                    let fresh = latestTask
                    store.toggleTaskCompletion(fresh)
                },
                onCancelTask: {
                    commitDraftEditsIfNeeded(commitPendingChecklist: true)
                    let fresh = latestTask
                    store.cancelTask(fresh)
                },
                onToggleInProgress: {
                    commitDraftEditsIfNeeded(commitPendingChecklist: true)
                    let fresh = latestTask
                    store.toggleTaskInProgress(fresh)
                }
            )

            TextField(store.tr("placeholder_todo_title"), text: $draftTitle)
                .textFieldStyle(.plain)
                .font(.system(size: 18.5, weight: .bold))
                .foregroundStyle(current.status == .open ? .primary : .secondary)
                .strikethrough(current.status == .canceled, color: .secondary)
                .focused($isTitleFocused)
                .frame(maxWidth: .infinity, alignment: .leading)
                .onSubmit {
                    closeModal()
                }

            Spacer(minLength: 8)

            // In Progress Button
            if current.status == .open {
                Button {
                    commitDraftEditsIfNeeded()
                    let fresh = latestTask
                    store.toggleTaskInProgress(fresh)
                } label: {
                    Image(systemName: "circle.lefthalf.filled")
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(
                            current.isInProgress
                                ? ThingsMacPalette.somedayAmber
                                : (isHoveringInProgress ? ThingsMacPalette.somedayAmber : .secondary)
                        )
                        .frame(width: 28, height: 28)
                        .background(
                            RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                .fill(
                                    current.isInProgress
                                        ? ThingsMacPalette.somedayAmber.opacity(isHoveringInProgress ? 0.22 : 0.14)
                                        : (isHoveringInProgress ? ThingsMacPalette.somedayAmber.opacity(0.09) : ThingsMacPalette.subtleContainer)
                                )
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                .strokeBorder(
                                    current.isInProgress
                                        ? ThingsMacPalette.somedayAmber.opacity(isHoveringInProgress ? 0.50 : 0.34)
                                        : (isHoveringInProgress ? ThingsMacPalette.somedayAmber.opacity(0.38) : Color.primary.opacity(0.08)),
                                    lineWidth: 0.8
                                )
                        )
                }
                .buttonStyle(.plain)
                .onHover { hovering in
                    withAnimation(.easeOut(duration: 0.12)) {
                        isHoveringInProgress = hovering
                    }
                }
                .thingsTooltip(store.tr(current.isInProgress ? "v33_clear_in_progress" : "v33_mark_in_progress"))
            }

            // Milestone Diamond Button
            Button {
                commitDraftEditsIfNeeded()
                let fresh = latestTask
                store.setTasksMilestone([fresh.id], isMilestone: !fresh.isMilestone)
            } label: {
                Image(systemName: current.isMilestone ? "diamond.fill" : "diamond")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(
                        current.isMilestone
                            ? ThingsMacPalette.milestoneViolet
                            : (isHoveringMilestone ? ThingsMacPalette.milestoneViolet : .secondary)
                    )
                    .frame(width: 28, height: 28)
                    .background(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .fill(
                                current.isMilestone
                                    ? ThingsMacPalette.milestoneViolet.opacity(isHoveringMilestone ? 0.22 : 0.14)
                                    : (isHoveringMilestone ? ThingsMacPalette.milestoneViolet.opacity(0.09) : ThingsMacPalette.subtleContainer)
                            )
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .strokeBorder(
                                current.isMilestone
                                    ? ThingsMacPalette.milestoneViolet.opacity(isHoveringMilestone ? 0.50 : 0.34)
                                    : (isHoveringMilestone ? ThingsMacPalette.milestoneViolet.opacity(0.38) : Color.primary.opacity(0.08)),
                                lineWidth: 0.8
                            )
                    )
            }
            .buttonStyle(.plain)
            .onHover { hovering in
                withAnimation(.easeOut(duration: 0.12)) {
                    isHoveringMilestone = hovering
                }
            }
            .thingsTooltip(store.tr(current.isMilestone ? "v30_remove_milestone" : "v30_mark_as_milestone"))

            // Pin Button
            Button {
                commitDraftEditsIfNeeded()
                let fresh = latestTask
                store.setTasksPinned([fresh.id], isPinned: !fresh.isPinned)
            } label: {
                Image(systemName: current.isPinned ? "pin.fill" : "pin")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(
                        current.isPinned
                            ? ThingsMacPalette.todayYellow
                            : (isHoveringPin ? ThingsMacPalette.accentBlue : .secondary)
                    )
                    .frame(width: 28, height: 28)
                    .background(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .fill(
                                current.isPinned
                                    ? ThingsMacPalette.todayYellow.opacity(isHoveringPin ? 0.20 : 0.14)
                                    : (isHoveringPin ? ThingsMacPalette.accentBlue.opacity(0.08) : ThingsMacPalette.subtleContainer)
                            )
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .strokeBorder(
                                current.isPinned
                                    ? ThingsMacPalette.todayYellow.opacity(isHoveringPin ? 0.48 : 0.32)
                                    : (isHoveringPin ? ThingsMacPalette.accentBlue.opacity(0.36) : Color.primary.opacity(0.08)),
                                lineWidth: 0.8
                            )
                    )
            }
            .buttonStyle(.plain)
            .onHover { hovering in
                withAnimation(.easeOut(duration: 0.12)) {
                    isHoveringPin = hovering
                }
            }
            .thingsTooltip(store.tr(current.isPinned ? "v25_unpin_task" : "v25_pin_task"))

            // Live Voice Dictation Button
            Button {
                toggleVoiceDictation()
            } label: {
                Image(systemName: voiceController.isRecording ? "waveform.circle.fill" : "mic.fill")
                    .font(.system(size: 13.5, weight: .semibold))
                    .foregroundStyle(
                        voiceController.isRecording
                            ? .white
                            : (isHoveringMic ? ThingsMacPalette.accentBlue : .secondary)
                    )
                    .frame(width: 28, height: 28)
                    .background(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .fill(
                                voiceController.isRecording
                                    ? ThingsMacPalette.deadlineCrimson
                                    : (isHoveringMic ? ThingsMacPalette.accentBlue.opacity(0.08) : ThingsMacPalette.subtleContainer)
                            )
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .strokeBorder(
                                voiceController.isRecording
                                    ? ThingsMacPalette.deadlineCrimson
                                    : (isHoveringMic ? ThingsMacPalette.accentBlue.opacity(0.36) : Color.primary.opacity(0.08)),
                                lineWidth: 0.8
                            )
                    )
            }
            .buttonStyle(.plain)
            .onHover { hovering in
                withAnimation(.easeOut(duration: 0.12)) {
                    isHoveringMic = hovering
                }
            }
            .thingsTooltip(store.tr(voiceController.isRecording ? "macos_voice_stop" : "voice_capture_btn"))

            // Close Button
            Button {
                closeModal()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(isHoveringClose ? Color.primary.opacity(0.75) : Color.secondary.opacity(0.65))
                    .frame(width: 26, height: 26)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .onHover { hovering in
                isHoveringClose = hovering
            }
            .thingsTooltip(store.tr("backup_close_dialog_tooltip"))
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
    }

    // MARK: - Zone B1: Left Primary Content Column (~430 pt)

    private var leftPrimaryContentColumn: some View {
        FlexGrowBentoColumnLayout(
            spacing: 11,
            growIndices: [voiceController.isRecording ? 1 : 0]
        ) {
            if voiceController.isRecording {
                HStack(spacing: 7) {
                    Circle()
                        .fill(ThingsMacPalette.deadlineCrimson)
                        .frame(width: 7, height: 7)
                    Text(store.tr("macos_voice_listening"))
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(
                    RoundedRectangle(cornerRadius: 13, style: .continuous)
                        .fill(ThingsMacPalette.deadlineCrimson.opacity(0.09))
                        .overlay(
                            RoundedRectangle(cornerRadius: 13, style: .continuous)
                                .strokeBorder(ThingsMacPalette.deadlineCrimson.opacity(0.22), lineWidth: 0.85)
                        )
                )
            }

            notesBentoTile
            checklistBentoTile
            attachmentsBentoTile
        }
    }

    private var notesBentoTile: some View {
        let hasNotes = !draftNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let links = MarkdownNotesParser.extractDetectedLinks(from: draftNotes)
        let toggleLabel = isEditingNotes ? store.tr("macos_preview_markdown") : store.tr("macos_edit_markdown")
        let noteLineCount = max(1, draftNotes.components(separatedBy: .newlines).count)
        let notesEditorHeight = min(196, max(92, CGFloat(noteLineCount) * 20 + 20))

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "doc.text")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)
                Text(store.tr("placeholder_notes"))
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(.secondary)

                Spacer()

                if hasNotes || isEditingNotes {
                    Button {
                        isEditingNotes.toggle()
                        if isEditingNotes {
                            DispatchQueue.main.async {
                                isNotesFocused = true
                            }
                        }
                    } label: {
                        Text(toggleLabel)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    .buttonStyle(.plain)
                    .thingsTooltip(toggleLabel)
                }
            }

            if !isEditingNotes {
                Group {
                    if hasNotes {
                        Text(MarkdownNotesParser.renderAttributed(draftNotes))
                            .font(.system(size: 13.5))
                            .fixedSize(horizontal: false, vertical: true)
                    } else {
                        Text(store.tr("placeholder_notes"))
                            .font(.system(size: 13.5))
                            .foregroundStyle(.tertiary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 58, maxHeight: .infinity, alignment: .topLeading)
                .padding(10)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(ThingsMacPalette.cardSurface)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(Color.primary.opacity(0.06), lineWidth: 0.8)
                        )
                )
                .contentShape(Rectangle())
                .onTapGesture {
                    isEditingNotes = true
                    DispatchQueue.main.async {
                        isNotesFocused = true
                    }
                }
            } else {
                ZStack(alignment: .topLeading) {
                    if draftNotes.isEmpty {
                        Text(store.tr("placeholder_notes"))
                            .font(.system(size: 13.5))
                            .foregroundStyle(.tertiary)
                            .padding(.top, 9)
                            .padding(.leading, 11)
                            .allowsHitTesting(false)
                    }
                    TextEditor(text: $draftNotes)
                        .font(.system(size: 13.5))
                        .scrollContentBackground(.hidden)
                        .focused($isNotesFocused)
                        .padding(6)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .frame(
                    maxWidth: .infinity,
                    minHeight: notesEditorHeight,
                    idealHeight: notesEditorHeight,
                    maxHeight: .infinity,
                    alignment: .topLeading
                )
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(ThingsMacPalette.cardSurface)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.32), lineWidth: 1.0)
                        )
                )
            }

            if !links.isEmpty {
                ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                    ForEach(links) { link in
                        Button {
                            if let url = URL(string: link.targetUri) {
                                NSWorkspace.shared.open(url)
                            }
                        } label: {
                            HStack(spacing: 5) {
                                Image(systemName: link.type.sfSymbol)
                                    .font(.system(size: 10.5, weight: .semibold))
                                Text("\(link.displayLabel) ↗")
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .lineLimit(1)
                            }
                            .fixedSize(horizontal: true, vertical: false)
                            .foregroundStyle(ThingsMacPalette.accentBlue)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(ThingsMacPalette.accentBlue.opacity(0.10))
                                    .overlay(
                                        Capsule(style: .continuous)
                                            .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.24), lineWidth: 0.8)
                                    )
                            )
                        }
                        .buttonStyle(.plain)
                        .thingsTooltip(link.targetUri)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(12)
        .background(softBentoTileBackground())
    }

    private var checklistBentoTile: some View {
        let current = latestTask
        let items = current.checklistItems
        let doneCount = items.filter(\.isCompleted).count

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "checklist")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)
                Text(store.tr("action_checklist"))
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(.secondary)

                if !items.isEmpty {
                    Text("\(doneCount)/\(items.count)")
                        .font(.system(size: 10.5, weight: .bold, design: .rounded))
                        .foregroundStyle(doneCount == items.count ? ThingsMacPalette.logbookGreen : ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 6.5)
                        .padding(.vertical, 1.5)
                        .background(
                            Capsule().fill(
                                (doneCount == items.count ? ThingsMacPalette.logbookGreen : ThingsMacPalette.accentBlue)
                                    .opacity(0.13)
                            )
                        )
                }

                Spacer()

                Button {
                    runMagicBreakdown()
                } label: {
                    HStack(spacing: 4.5) {
                        if isGeneratingChecklist {
                            ProgressView().controlSize(.mini)
                        } else {
                            Image(systemName: "sparkles")
                                .font(.system(size: 10.5, weight: .semibold))
                        }
                        Text(store.tr("gemini_magic_breakdown"))
                            .font(.system(size: 11, weight: .semibold))
                            .lineLimit(1)
                    }
                    .fixedSize(horizontal: true, vertical: false)
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 3.5)
                    .background(
                        Capsule()
                            .fill(ThingsMacPalette.accentBlue.opacity(isHoveringMagic ? 0.19 : 0.12))
                            .overlay(
                                Capsule()
                                    .strokeBorder(
                                        ThingsMacPalette.accentBlue.opacity(isHoveringMagic ? 0.42 : 0.0),
                                        lineWidth: 0.8
                                    )
                            )
                    )
                }
                .buttonStyle(.plain)
                .fixedSize(horizontal: true, vertical: false)
                .disabled(isGeneratingChecklist)
                .onHover { hovering in
                    withAnimation(.easeOut(duration: 0.12)) {
                        isHoveringMagic = hovering
                    }
                }
                .thingsTooltip(store.tr("gemini_magic_breakdown_desc"))
            }

            if !items.isEmpty {
                VStack(alignment: .leading, spacing: 3) {
                    ForEach(items) { item in
                        ChecklistItemRowView(store: store, item: item, allItems: items)
                    }
                }
                .padding(.horizontal, 4)
            }

            HStack(spacing: 7) {
                Image(systemName: "plus")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .frame(width: 15)

                TextField(store.tr("placeholder_checklist"), text: $newChecklistTitle)
                    .textFieldStyle(.plain)
                    .font(.system(size: 13))
                    .onSubmit {
                        addPendingChecklistItem()
                    }

                if !newChecklistTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Button(store.tr("macos_action_add")) {
                        addPendingChecklistItem()
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                    .buttonStyle(.plain)
                    .fixedSize(horizontal: true, vertical: false)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(ThingsMacPalette.cardSurface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.07), lineWidth: 0.8)
                    )
            )
        }
        .padding(12)
        .background(softBentoTileBackground())
    }

    private var attachmentsBentoTile: some View {
        let current = latestTask

        return Group {
            if !current.attachments.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    AttachmentsStripView(store: store, attachments: current.attachments) {
                        commitDraftEditsIfNeeded()
                        store.pickAndAttachFiles(taskId: current.id)
                    }
                }
                .padding(12)
                .background(softBentoTileBackground())
            } else {
                Button {
                    commitDraftEditsIfNeeded()
                    store.pickAndAttachFiles(taskId: current.id)
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: isFileDropTargeted ? "arrow.down.doc.fill" : "paperclip")
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundStyle(isFileDropTargeted ? ThingsMacPalette.accentBlue : .secondary)

                        Text(store.tr("attachments_section_title"))
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundStyle(isFileDropTargeted ? ThingsMacPalette.accentBlue : .secondary)
                            .fixedSize(horizontal: true, vertical: false)

                        Spacer(minLength: 8)

                        HStack(spacing: 3.5) {
                            Image(systemName: "plus")
                                .font(.system(size: 10, weight: .bold))
                            Text(store.tr("action_attach_file"))
                                .font(.system(size: 11, weight: .semibold))
                                .lineLimit(1)
                        }
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4)
                        .background(
                            Capsule(style: .continuous)
                                .fill(ThingsMacPalette.accentBlue.opacity(isHoveringAttachBar ? 0.18 : 0.11))
                        )
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 13, style: .continuous)
                            .fill(
                                isFileDropTargeted
                                    ? ThingsMacPalette.accentBlue.opacity(0.08)
                                    : (isHoveringAttachBar ? ThingsMacPalette.accentBlue.opacity(0.045) : ThingsMacPalette.subtleContainer)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 13, style: .continuous)
                                    .strokeBorder(
                                        isFileDropTargeted
                                            ? ThingsMacPalette.accentBlue
                                            : (isHoveringAttachBar ? ThingsMacPalette.accentBlue.opacity(0.32) : Color.primary.opacity(0.065)),
                                        lineWidth: isFileDropTargeted ? 1.3 : 0.85
                                    )
                            )
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                }
                .buttonStyle(.plain)
                .onHover { hovering in
                    withAnimation(.easeOut(duration: 0.12)) {
                        isHoveringAttachBar = hovering
                    }
                }
                .thingsTooltip(store.tr("macos_attachment_drop_hint"))
            }
        }
    }

    // MARK: - Zone B2: Right Properties Column (3 Balanced Soft Bento Cards, ~274 pt)

    private var rightPropertiesColumn: some View {
        FlexGrowBentoColumnLayout(
            spacing: 11,
            growIndices: [0, 1, 2]
        ) {
            scheduleBentoCard
            focusAndDependenciesBentoCard
            organizationBentoCard
        }
    }

    private var scheduleBentoCard: some View {
        let current = latestTask
        let whenLabel = whenSummaryLabel(for: current)
        let dlInfo = current.deadline.map { deadlineSummaryInfo(for: $0) }

        return VStack(alignment: .leading, spacing: 8) {
            bentoSectionHeader(
                icon: "calendar",
                title: "\(store.tr("action_when")) · \(store.tr("action_deadline"))"
            )

            ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                ThingsActionChipButton(
                    title: whenLabel ?? store.tr("action_when"),
                    systemImage: current.scheduleState == .thisEvening
                        ? "moon.stars.fill"
                        : (current.scheduleState == .today ? "sun.max.fill" : "calendar"),
                    isActive: whenLabel != nil || current.reminderTime != nil,
                    activeTint: current.scheduleState == .thisEvening ? ThingsMacPalette.eveningPurple : ThingsMacPalette.accentBlue,
                    helpText: whenButtonTooltip(for: current)
                ) {
                    commitDraftEditsIfNeeded()
                    isWhenPopoverOpen = true
                }
                .popover(isPresented: $isWhenPopoverOpen) {
                    WhenPopoverView(
                        store: store,
                        currentSchedule: current.scheduleState,
                        currentWhenMillis: current.whenDate,
                        currentReminderMillis: current.reminderTime
                    ) { resolved in
                        var copy = latestTask
                        copy.title = draftTitle
                        copy.notes = draftNotes
                        copy.scheduleState = resolved.scheduleState
                        copy.whenDate = resolved.whenDateEpochMillis
                        copy.reminderTime = resolved.reminderEpochMillis
                        store.updateTask(copy)
                    }
                }

                if showQuickThisEveningPill {
                    ThingsActionChipButton(
                        title: store.tr("view_this_evening"),
                        systemImage: "moon.stars.fill",
                        isActive: true,
                        activeTint: ThingsMacPalette.eveningPurple,
                        helpText: store.tr("view_this_evening")
                    ) {
                        quickMoveToThisEveningAndClose()
                    }
                }

                if let rem = current.reminderTime {
                    ThingsActionChipButton(
                        title: shortTimeString(rem),
                        systemImage: "bell.fill",
                        isActive: true,
                        helpText: store.trf("when_task_reminder_set", shortTimeString(rem))
                    ) {
                        commitDraftEditsIfNeeded()
                        isWhenPopoverOpen = true
                    }
                }

                ThingsActionChipButton(
                    title: current.scheduledStartMinutes != nil
                        ? (current.formattedTimeblockPill ?? store.tr("v2_timeblock_title"))
                        : store.tr("v2_timeblock_title"),
                    systemImage: "clock.badge.checkmark",
                    isActive: current.scheduledStartMinutes != nil,
                    activeTint: ThingsMacPalette.anytimeTeal,
                    helpText: timeblockButtonTooltip(for: current)
                ) {
                    commitDraftEditsIfNeeded()
                    isTimeblockPopoverOpen = true
                }
                .popover(isPresented: $isTimeblockPopoverOpen) {
                    TimeblockPopoverView(
                        store: store,
                        currentStartMinutes: current.scheduledStartMinutes,
                        currentDurationMinutes: current.durationMinutes
                    ) { startMins, durMins in
                        var copy = latestTask
                        copy.title = draftTitle
                        copy.notes = draftNotes
                        copy.scheduledStartMinutes = startMins
                        if startMins == nil {
                            if let durMins {
                                copy.durationMinutes = durMins
                            }
                        } else {
                            copy.durationMinutes = durMins
                        }
                        store.updateTask(copy)
                    }
                }

                ThingsActionChipButton(
                    title: dlInfo?.label ?? store.tr("action_deadline"),
                    systemImage: "flag",
                    isActive: dlInfo != nil,
                    activeTint: (dlInfo?.isUrgent == true) ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue,
                    helpText: store.tr("context_adjust_deadline")
                ) {
                    commitDraftEditsIfNeeded()
                    isDeadlinePopoverOpen = true
                }
                .popover(isPresented: $isDeadlinePopoverOpen) {
                    DeadlinePopoverView(store: store, currentDeadlineMillis: current.deadline) { dl in
                        var copy = latestTask
                        copy.title = draftTitle
                        copy.notes = draftNotes
                        copy.deadline = dl
                        store.updateTask(copy)
                    }
                }

                ThingsActionChipButton(
                    title: current.recurrenceRule?.formatShortLabel(locale: store.preferences.language.locale) ?? store.tr("action_repeat"),
                    systemImage: "repeat",
                    isActive: current.recurrenceRule != nil,
                    helpText: repeatButtonTooltip(for: current)
                ) {
                    commitDraftEditsIfNeeded()
                    isRepeatPopoverOpen = true
                }
                .popover(isPresented: $isRepeatPopoverOpen) {
                    RepeatEditorPopoverView(store: store, currentRule: current.recurrenceRule) { newRule in
                        var copy = latestTask
                        copy.title = draftTitle
                        copy.notes = draftNotes
                        copy.recurrenceRule = newRule
                        if newRule != nil && copy.whenDate == nil {
                            copy.scheduleState = .today
                            copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
                        }
                        store.updateTask(copy)
                    }
                }

                ThingsActionChipButton(
                    title: store.tr("v30_milestone_label"),
                    systemImage: current.isMilestone ? "diamond.fill" : "diamond",
                    isActive: current.isMilestone,
                    activeTint: ThingsMacPalette.milestoneViolet,
                    helpText: store.tr(current.isMilestone ? "v30_remove_milestone" : "v30_mark_as_milestone")
                ) {
                    commitDraftEditsIfNeeded()
                    let fresh = latestTask
                    store.setTasksMilestone([fresh.id], isMilestone: !fresh.isMilestone)
                }

                if current.status == .open {
                    ThingsActionChipButton(
                        title: store.tr(current.isInProgress ? "v33_in_progress_badge" : "v33_mark_in_progress"),
                        systemImage: "circle.lefthalf.filled",
                        isActive: current.isInProgress,
                        activeTint: ThingsMacPalette.somedayAmber,
                        helpText: store.tr(current.isInProgress ? "v33_clear_in_progress" : "v33_mark_in_progress")
                    ) {
                        commitDraftEditsIfNeeded()
                        let fresh = latestTask
                        store.toggleTaskInProgress(fresh)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(12)
        .background(softBentoTileBackground())
    }

    private var focusAndDependenciesBentoCard: some View {
        let current = latestTask
        let currentEffort = current.resolvedEffort
        let currentDuration = (current.durationMinutes ?? 0) > 0 ? current.durationMinutes : nil
        let effortColor = currentEffort.map { effortTint(for: $0) } ?? ThingsMacPalette.accentBlue
        let blockedLabel = store.blockedByBadgeText(for: current)
        let isBlockedActive = blockedLabel != nil || !current.blockedByTaskIds.isEmpty

        return VStack(alignment: .leading, spacing: 8) {
            bentoSectionHeader(
                icon: "bolt",
                title: "\(store.tr("v30_effort_label")) · \(store.tr("v30_duration_label"))"
            )

            ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                BentoMenuPill(
                    title: currentEffort.map { effortLocalizedTitle(for: $0) } ?? store.tr("v30_effort_label"),
                    systemImage: currentEffort?.sfSymbol ?? "bolt",
                    isActive: currentEffort != nil,
                    activeTint: effortColor,
                    helpText: currentEffort.map { "\(store.tr("v30_effort_label")): \(effortLocalizedTitle(for: $0))" }
                        ?? store.tr("v30_effort_label")
                ) {
                    ForEach(TaskEffortLevel.allCases, id: \.rawValue) { level in
                        let isSelected = currentEffort == level
                        Button {
                            commitDraftEditsIfNeeded()
                            store.setTaskEffort(current.id, effortLevel: level.rawValue)
                        } label: {
                            Label(
                                effortLocalizedTitle(for: level),
                                systemImage: isSelected ? "checkmark" : level.sfSymbol
                            )
                        }
                    }
                    if currentEffort != nil {
                        Divider()
                        Button(store.tr("v30_effort_none")) {
                            commitDraftEditsIfNeeded()
                            store.setTaskEffort(current.id, effortLevel: nil)
                        }
                    }
                }

                BentoMenuPill(
                    title: currentDuration.map { TaskItem.formatCompactDuration($0) } ?? store.tr("v30_duration_label"),
                    systemImage: "timer",
                    isActive: currentDuration != nil,
                    activeTint: ThingsMacPalette.anytimeTeal,
                    helpText: currentDuration.map { "\(store.tr("v30_duration_label")): \(TaskItem.formatCompactDuration($0))" }
                        ?? store.tr("v30_duration_label")
                ) {
                    ForEach(TaskItem.standaloneDurationPresets, id: \.self) { mins in
                        let isSelected = current.durationMinutes == mins
                        Button {
                            commitDraftEditsIfNeeded()
                            store.setTaskStandaloneDuration(current.id, durationMinutes: mins)
                        } label: {
                            if isSelected {
                                Label(TaskItem.formatCompactDuration(mins), systemImage: "checkmark")
                            } else {
                                Text(TaskItem.formatCompactDuration(mins))
                            }
                        }
                    }
                    Divider()
                    Button(store.tr("v30_duration_custom")) {
                        commitDraftEditsIfNeeded()
                        isCustomDurationPopoverOpen = true
                    }
                    if currentDuration != nil {
                        Button(store.tr("v30_duration_clear"), role: .destructive) {
                            commitDraftEditsIfNeeded()
                            store.setTaskStandaloneDuration(current.id, durationMinutes: nil)
                        }
                    }
                    if (current.actualMinutes ?? 0) > 0 {
                        Button(store.tr("v32_reset_actual_time"), role: .destructive) {
                            commitDraftEditsIfNeeded()
                            store.setTaskActualMinutes(current.id, actualMinutes: nil)
                        }
                    }
                }
                .popover(isPresented: $isCustomDurationPopoverOpen) {
                    CustomDurationPopoverView(
                        store: store,
                        currentDurationMinutes: currentDuration
                    ) { customMins in
                        commitDraftEditsIfNeeded()
                        store.setTaskStandaloneDuration(current.id, durationMinutes: customMins)
                    }
                }

                if let actualBadge = store.formattedActualTimeBadge(for: current) {
                    BentoMenuPill(
                        title: actualBadge,
                        systemImage: "clock.arrow.circlepath",
                        isActive: true,
                        activeTint: ThingsMacPalette.logbookGreen,
                        helpText: actualBadge
                    ) {
                        Button(store.tr("v32_reset_actual_time"), role: .destructive) {
                            commitDraftEditsIfNeeded()
                            store.setTaskActualMinutes(current.id, actualMinutes: nil)
                        }
                    }
                }

                BentoWrappingChip(
                    title: blockedLabel ?? store.tr("v30_blocked_by_label"),
                    systemImage: "hourglass",
                    isActive: isBlockedActive,
                    activeTint: ThingsMacPalette.somedayAmber,
                    helpText: blockedLabel ?? store.tr("v30_blocked_by_label")
                ) {
                    commitDraftEditsIfNeeded()
                    isDependenciesPopoverOpen = true
                }
                .popover(isPresented: $isDependenciesPopoverOpen) {
                    DependenciesPopoverView(store: store, task: current)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(12)
        .background(softBentoTileBackground())
    }

    private var organizationBentoCard: some View {
        let current = latestTask
        let breadcrumb = contextBreadcrumb(for: current)
        let projectHeadings: [Heading] = {
            guard let pid = current.projectId else { return [] }
            return (store.headingsByProject[pid] ?? [])
                .filter { !$0.isDeleted && !$0.isArchived }
                .sorted {
                    if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                    return $0.id < $1.id
                }
        }()
        let activeHeading = projectHeadings.first { $0.id == current.headingId }

        return VStack(alignment: .leading, spacing: 8) {
            bentoSectionHeader(
                icon: "folder",
                title: "\(store.tr("entity_project")) / \(store.tr("entity_area")) · \(store.tr("action_tags"))"
            )

            ThingsFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                BentoWrappingChip(
                    title: breadcrumb ?? store.tr("action_move"),
                    systemImage: "folder",
                    isActive: current.projectId != nil || current.areaId != nil,
                    activeTint: ThingsMacPalette.accentBlue,
                    helpText: moveButtonTooltip
                ) {
                    commitDraftEditsIfNeeded()
                    isMovePopoverOpen = true
                }
                .popover(isPresented: $isMovePopoverOpen) {
                    movePopoverContent
                }

                if !projectHeadings.isEmpty {
                    BentoMenuPill(
                        title: activeHeading?.title ?? store.tr("entity_heading"),
                        systemImage: "flag.checkered",
                        isActive: activeHeading != nil,
                        activeTint: ThingsMacPalette.accentBlue,
                        helpText: store.tr("entity_heading")
                    ) {
                        Button {
                            commitDraftEditsIfNeeded()
                            var copy = latestTask
                            copy.title = draftTitle
                            copy.notes = draftNotes
                            copy.headingId = nil
                            store.updateTask(copy)
                        } label: {
                            if activeHeading == nil {
                                Label(store.tr("v30_board_ungrouped_column"), systemImage: "checkmark")
                            } else {
                                Text(store.tr("v30_board_ungrouped_column"))
                            }
                        }
                        Divider()
                        ForEach(projectHeadings) { heading in
                            Button {
                                commitDraftEditsIfNeeded()
                                var copy = latestTask
                                copy.title = draftTitle
                                copy.notes = draftNotes
                                copy.headingId = heading.id
                                store.updateTask(copy)
                            } label: {
                                if activeHeading?.id == heading.id {
                                    Label(heading.title, systemImage: "checkmark")
                                } else {
                                    Text(heading.title)
                                }
                            }
                        }
                    }
                }

                ForEach(current.tags) { tag in
                    TagPillBadge(
                        title: tag.title,
                        isSelected: true,
                        showIcon: true,
                        helpText: "#\(tag.title)"
                    ) {
                        commitDraftEditsIfNeeded()
                        isTagPopoverOpen = true
                    }
                }

                ThingsActionChipButton(
                    title: "+ \(store.tr("action_tags"))",
                    systemImage: "square.grid.2x2",
                    isActive: false,
                    helpText: tagsButtonTooltip(for: current)
                ) {
                    commitDraftEditsIfNeeded()
                    isTagPopoverOpen = true
                }
                .popover(isPresented: $isTagPopoverOpen) {
                    TagPickerPopoverView(
                        store: store,
                        selectedTagIds: Set(current.tagIds),
                        onToggleTag: { tagId in
                            let base = latestTask
                            var tids = Set(base.tagIds)
                            if tids.contains(tagId) {
                                tids.remove(tagId)
                            } else {
                                tids.insert(tagId)
                            }
                            var copy = base
                            copy.title = draftTitle
                            copy.notes = draftNotes
                            store.updateTask(copy, tagIds: Array(tids))
                        }
                    )
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(12)
        .background(softBentoTileBackground())
    }

    private var movePopoverContent: some View {
        MoveDestinationPopoverView(store: store, task: latestTask) { projId, headId, areaId, schedOverride in
            var copy = latestTask
            copy.title = draftTitle
            copy.notes = draftNotes
            copy.projectId = projId
            copy.headingId = headId
            copy.areaId = areaId
            if let schedOverride {
                copy.scheduleState = schedOverride
            }
            store.updateTask(copy)
        }
    }

    // MARK: - Zone C: Single-Row Bottom Footer Bar

    private var modalFooterBar: some View {
        let current = latestTask

        return HStack(alignment: .center, spacing: 10) {
            ThingsFlowLayout(horizontalSpacing: 7, verticalSpacing: 6) {
                if current.status == .open {
                    ThingsActionChipButton(
                        title: store.tr("action_focus_timer"),
                        systemImage: "timer",
                        helpText: store.tr("action_focus_timer")
                    ) {
                        commitDraftEditsIfNeeded(commitPendingChecklist: true)
                        store.startFocusTimer(for: latestTask)
                    }
                }

                ThingsActionChipButton(
                    title: store.tr("action_duplicate_todo"),
                    systemImage: "plus.square.on.square",
                    helpText: store.tr("action_duplicate_todo")
                ) {
                    commitDraftEditsIfNeeded(commitPendingChecklist: true)
                    let fresh = latestTask
                    if let copy = try? store.database.duplicateTask(fresh.id, copySuffix: store.tr("v25_copy_suffix")) {
                        store.selectedTaskIds = [copy.id]
                        store.lastSelectedTaskId = copy.id
                        store.expandedTaskId = copy.id
                    }
                }

                ThingsUtilityIconButton(
                    systemImage: "doc.on.doc",
                    tint: .secondary,
                    helpText: store.tr("action_copy_todo")
                ) {
                    commitDraftEditsIfNeeded(commitPendingChecklist: true)
                    store.copyTaskToPasteboard(latestTask)
                }

                Button(role: .destructive) {
                    deleteAndCloseModal()
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "trash")
                            .font(.system(size: 11, weight: .semibold))
                        Text(store.tr("action_delete"))
                            .font(.system(size: 12, weight: .semibold))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                    .padding(.horizontal, 10.5)
                    .padding(.vertical, 5.5)
                    .background(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .fill(ThingsMacPalette.deadlineCrimson.opacity(isHoveringDelete ? 0.16 : 0.10))
                            .overlay(
                                RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                                    .strokeBorder(
                                        ThingsMacPalette.deadlineCrimson.opacity(isHoveringDelete ? 0.44 : 0.26),
                                        lineWidth: 0.85
                                    )
                            )
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 7.5, style: .continuous))
                }
                .buttonStyle(.plain)
                .fixedSize(horizontal: true, vertical: false)
                .onHover { hovering in
                    withAnimation(.easeOut(duration: 0.12)) {
                        isHoveringDelete = hovering
                    }
                }
                .thingsTooltip(store.tr("action_delete"))
            }

            Spacer(minLength: 8)

            // Hidden Escape responder button so SwiftUI doesn't overwrite .keyboardShortcut(.return, modifiers: [.command])
            Button("") {
                closeModal()
            }
            .keyboardShortcut(.escape, modifiers: [])
            .opacity(0)
            .frame(width: 0, height: 0)
            .accessibilityHidden(true)

            Button {
                closeModal()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "checkmark")
                        .font(.system(size: 11.5, weight: .bold))
                    Text(store.tr("action_done"))
                        .font(.system(size: 12.5, weight: .bold))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 15)
                .padding(.vertical, 7)
                .background(
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .brightness(isHoveringDone ? 0.06 : 0.0)
                        .shadow(color: ThingsMacPalette.accentBlue.opacity(0.28), radius: 6, y: 2)
                )
                .contentShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
            }
            .buttonStyle(.plain)
            .fixedSize(horizontal: true, vertical: false)
            .onHover { hovering in
                withAnimation(.easeOut(duration: 0.12)) {
                    isHoveringDone = hovering
                }
            }
            .thingsTooltip("\(store.tr("action_done")) (Esc / ⌘↩)")
            .keyboardShortcut(.return, modifiers: [.command])
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 11)
        .background(ThingsMacPalette.subtleContainer.opacity(0.55))
    }

    // MARK: - Soft Bento Styling Helpers

    private func softBentoTileBackground() -> some View {
        RoundedRectangle(cornerRadius: 13, style: .continuous)
            .fill(ThingsMacPalette.subtleContainer)
            .overlay(
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .strokeBorder(Color.primary.opacity(0.065), lineWidth: 0.85)
            )
    }

    private func bentoSectionHeader(icon: String, title: String) -> some View {
        HStack(spacing: 5) {
            Image(systemName: icon)
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(.secondary)
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }

    // MARK: - State, Persistence & Actions

    private func registerDraftFlushHook() {
        store.flushOpenTaskModalToken = modalFlushToken
        store.flushOpenTaskModalDrafts = {
            draftSaveTask?.cancel()
            commitDraftEditsIfNeeded(commitPendingChecklist: true)
        }
    }

    private func syncDrafts() {
        let current = latestTask
        draftTitle = current.title
        draftNotes = current.notes
    }

    private func applyInitialFocusRules() {
        let current = latestTask
        isEditingNotes = TaskCardInteractionRules.shouldAutoEditNotesOnExpand(notes: current.notes)
        if TaskCardInteractionRules.shouldAutoFocusTitleOnExpand(title: current.title) {
            DispatchQueue.main.async {
                isTitleFocused = true
            }
        } else {
            isTitleFocused = false
            isNotesFocused = false
            DispatchQueue.main.async {
                isTitleFocused = false
                isNotesFocused = false
                if let window = NSApp.keyWindow, window.firstResponder is NSText {
                    window.makeFirstResponder(nil)
                }
            }
        }
    }

    private func scheduleDraftAutoSave() {
        draftSaveTask?.cancel()
        draftSaveTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 450_000_000)
            guard !Task.isCancelled else { return }
            commitDraftEditsIfNeeded()
        }
    }

    private func commitDraftEditsIfNeeded(commitPendingChecklist: Bool = false) {
        let base = latestTask
        if draftTitle != base.title || draftNotes != base.notes {
            var copy = base
            copy.title = draftTitle
            copy.notes = draftNotes
            store.updateTask(copy)
        }
        if commitPendingChecklist {
            addPendingChecklistItem(commitDraftsFirst: false)
        }
    }

    private func applyDraftSmartSyntax() {
        let parsed = parsedDraftSyntax
        guard parsed.hasAnyMatch else { return }
        draftSaveTask?.cancel()

        var copy = latestTask
        copy.title = parsed.cleanTitle
        copy.notes = draftNotes

        if let sched = parsed.scheduleState {
            copy.scheduleState = sched
            copy.whenDate = parsed.whenDate
        }
        if let dl = parsed.deadline {
            copy.deadline = dl
        }
        if let dur = parsed.durationMinutes {
            copy.durationMinutes = dur
        }
        if parsed.isMilestone {
            copy.isMilestone = true
        }
        if parsed.isInProgress && copy.status == .open {
            copy.isInProgress = true
        }
        if let proj = parsed.matchedProject {
            copy.projectId = proj.id
            copy.headingId = nil
            copy.areaId = proj.areaId
            if copy.scheduleState == .inbox && parsed.scheduleState == nil {
                copy.scheduleState = .anytime
            }
        } else if let area = parsed.matchedArea {
            copy.projectId = nil
            copy.headingId = nil
            copy.areaId = area.id
            if copy.scheduleState == .inbox && parsed.scheduleState == nil {
                copy.scheduleState = .anytime
            }
        }

        var mergedTagIds = copy.tagIds
        for tid in parsed.matchedTagIds where !mergedTagIds.contains(tid) {
            mergedTagIds.append(tid)
        }

        draftTitle = parsed.cleanTitle
        store.updateTask(copy, tagIds: mergedTagIds)
    }

    private func addPendingChecklistItem(commitDraftsFirst: Bool = true) {
        let clean = newChecklistTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        if commitDraftsFirst {
            commitDraftEditsIfNeeded(commitPendingChecklist: false)
        }
        _ = try? store.database.addChecklistItem(taskId: latestTask.id, title: clean)
        newChecklistTitle = ""
    }

    private func closeModal() {
        guard !didCommitAndDismiss else { return }
        didCommitAndDismiss = true
        draftSaveTask?.cancel()
        voiceController.stopRecording()
        commitDraftEditsIfNeeded(commitPendingChecklist: true)
        withAnimation(.spring(response: 0.24, dampingFraction: 0.88)) {
            store.expandedTaskId = nil
        }
    }

    private func deleteAndCloseModal() {
        didCommitAndDismiss = true
        draftSaveTask?.cancel()
        voiceController.stopRecording()
        let target = latestTask
        withAnimation(.spring(response: 0.24, dampingFraction: 0.88)) {
            store.expandedTaskId = nil
        }
        store.deleteTask(target)
    }

    private func quickMoveToThisEveningAndClose() {
        didCommitAndDismiss = true
        draftSaveTask?.cancel()
        voiceController.stopRecording()
        addPendingChecklistItem(commitDraftsFirst: false)
        var copy = latestTask
        copy.title = draftTitle
        copy.notes = draftNotes
        copy.scheduleState = .thisEvening
        copy.whenDate = DateEpochConverter.startOfDayMillis(for: Date())
        store.updateTask(copy)
        withAnimation(.spring(response: 0.24, dampingFraction: 0.88)) {
            store.expandedTaskId = nil
        }
    }

    private func toggleVoiceDictation() {
        if !voiceController.isRecording {
            dictationTargetsTitle = draftTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            dictationBaseNotes = draftNotes.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        voiceController.toggleRecording(
            locale: store.preferences.language.locale,
            onTranscript: { spoken in
                if dictationTargetsTitle {
                    let lines = spoken.components(separatedBy: .newlines)
                    draftTitle = String((lines.first ?? spoken).prefix(240))
                    if lines.count > 1 {
                        let extra = lines.dropFirst().joined(separator: "\n")
                        draftNotes = dictationBaseNotes.isEmpty ? extra : "\(dictationBaseNotes)\n\(extra)"
                    }
                } else {
                    draftNotes = dictationBaseNotes.isEmpty ? spoken : "\(dictationBaseNotes)\n\(spoken)"
                }
            },
            onUnavailable: {
                store.showToast(store.tr("voice_not_available"))
            }
        )
    }

    private func runMagicBreakdown() {
        commitDraftEditsIfNeeded()
        isGeneratingChecklist = true
        let base = latestTask
        let titleToBreak = draftTitle.isEmpty ? base.title : draftTitle
        let notesToBreak = draftNotes
        let targetId = base.id
        Task {
            do {
                let steps = try await ThingsbookGeminiService.generateMagicChecklist(
                    taskTitle: titleToBreak,
                    taskNotes: notesToBreak,
                    lang: store.preferences.language
                )
                await MainActor.run {
                    for step in steps {
                        _ = try? store.database.addChecklistItem(taskId: targetId, title: step)
                    }
                    isGeneratingChecklist = false
                }
            } catch {
                await MainActor.run {
                    isGeneratingChecklist = false
                    store.showToast(error.localizedDescription)
                }
            }
        }
    }

    // MARK: - Formatting & Display Helpers

    private var showQuickThisEveningPill: Bool {
        let current = latestTask
        return TaskCardInteractionRules.shouldShowQuickThisEveningPill(
            isTodayView: store.selectedDestination == .smartView(.today),
            status: current.status,
            scheduleState: current.scheduleState
        )
    }

    private func contextBreadcrumb(for target: TaskItem) -> String? {
        if let pid = target.projectId, let proj = store.project(byId: pid) {
            if let aid = proj.areaId, let area = store.area(byId: aid) {
                return "\(area.title) › \(proj.title)"
            }
            return proj.title
        }
        if let aid = target.areaId, let area = store.area(byId: aid) {
            return area.title
        }
        return nil
    }

    private func whenSummaryLabel(for target: TaskItem) -> String? {
        switch target.scheduleState {
        case .today:
            return store.tr("view_today")
        case .thisEvening:
            return store.tr("view_this_evening")
        case .someday:
            return store.tr("view_someday")
        case .upcoming:
            if let w = target.whenDate {
                let df = DateFormatter()
                df.locale = store.preferences.language.locale
                df.dateStyle = .medium
                return df.string(from: DateEpochConverter.date(fromMillis: w))
            }
            return store.tr("view_upcoming")
        default:
            return nil
        }
    }

    private func whenButtonTooltip(for target: TaskItem) -> String {
        let base = store.tr("context_when_date_reminder")
        if let label = whenSummaryLabel(for: target) {
            return "\(base) — \(label)"
        }
        return base
    }

    private func repeatButtonTooltip(for target: TaskItem) -> String {
        let base = store.tr("context_configure_repeat")
        if let rule = target.recurrenceRule {
            return "\(base) — \(rule.formatShortLabel(locale: store.preferences.language.locale))"
        }
        return base
    }

    private var moveButtonTooltip: String {
        let base = store.tr("move_dialog_title")
        if let crumb = contextBreadcrumb(for: latestTask) {
            return "\(base) — \(crumb)"
        }
        return base
    }

    private func tagsButtonTooltip(for target: TaskItem) -> String {
        let base = store.tr("context_assign_tags")
        if !target.tags.isEmpty {
            let names = target.tags.map { "#\($0.title)" }.joined(separator: ", ")
            return "\(base) — \(names)"
        }
        return base
    }

    private func timeblockButtonTooltip(for target: TaskItem) -> String {
        let base = store.tr("macos_tooltip_timeblock")
        if let pill = target.formattedTimeblockPill {
            return "\(base) — \(pill)"
        }
        return base
    }

    private func effortLocalizedTitle(for effort: TaskEffortLevel) -> String {
        switch effort {
        case .quickWin:
            return store.tr("v30_effort_quick_win")
        case .medium:
            return store.tr("v30_effort_medium")
        case .deepFocus:
            return store.tr("v30_effort_deep_focus")
        }
    }

    private func effortTint(for effort: TaskEffortLevel) -> Color {
        switch effort {
        case .quickWin:
            return ThingsMacPalette.anytimeTeal
        case .medium:
            return ThingsMacPalette.accentBlue
        case .deepFocus:
            return ThingsMacPalette.eveningPurple
        }
    }

    private func deadlineSummaryInfo(for millis: Int64) -> (label: String, isUrgent: Bool) {
        let todayDay = DateEpochConverter.epochDay(for: Date())
        let dlDay = DateEpochConverter.epochDay(forMillis: millis)
        let diff = dlDay - todayDay
        let isUrgent = diff <= 1
        let label: String = {
            if diff == 0 { return store.tr("deadline_today") }
            if diff < 0 { return store.trf("deadline_days_overdue", abs(diff)) }
            return store.trf("deadline_days_left", diff)
        }()
        return (label, isUrgent)
    }

    private func shortTimeString(_ millis: Int64) -> String {
        let df = DateFormatter()
        df.timeStyle = .short
        return df.string(from: DateEpochConverter.date(fromMillis: millis))
    }
}

private struct ModalBodyHeightPreferenceKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

private struct AlignedBentoTwoColumnLayout: Layout {
    var spacing: CGFloat = 14
    var rightWidth: CGFloat = 274

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        guard subviews.count >= 2 else {
            let fallbackW = proposal.width ?? rightWidth
            let fallbackH = subviews.first?.sizeThatFits(ProposedViewSize(width: fallbackW, height: nil)).height ?? 0
            return CGSize(width: fallbackW, height: fallbackH)
        }
        let totalW: CGFloat = {
            if let pw = proposal.width, pw.isFinite, pw > 0 {
                return pw
            }
            return 688
        }()
        let leftW = max(120, totalW - rightWidth - spacing)
        let leftNatH = subviews[0].sizeThatFits(ProposedViewSize(width: leftW, height: nil)).height
        let rightNatH = subviews[1].sizeThatFits(ProposedViewSize(width: rightWidth, height: nil)).height
        return CGSize(width: totalW, height: max(leftNatH, rightNatH))
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        guard !subviews.isEmpty else { return }
        if subviews.count == 1 {
            subviews[0].place(
                at: CGPoint(x: bounds.minX, y: bounds.minY),
                anchor: .topLeading,
                proposal: ProposedViewSize(width: bounds.width, height: bounds.height)
            )
            return
        }
        let totalW = bounds.width > 0 ? bounds.width : 688
        let leftW = max(120, totalW - rightWidth - spacing)
        let leftNatH = subviews[0].sizeThatFits(ProposedViewSize(width: leftW, height: nil)).height
        let rightNatH = subviews[1].sizeThatFits(ProposedViewSize(width: rightWidth, height: nil)).height
        let targetH = max(leftNatH, rightNatH)

        subviews[0].place(
            at: CGPoint(x: bounds.minX, y: bounds.minY),
            anchor: .topLeading,
            proposal: ProposedViewSize(width: leftW, height: targetH)
        )
        subviews[1].place(
            at: CGPoint(x: bounds.minX + leftW + spacing, y: bounds.minY),
            anchor: .topLeading,
            proposal: ProposedViewSize(width: rightWidth, height: targetH)
        )
    }
}

private struct FlexGrowBentoColumnLayout: Layout {
    var spacing: CGFloat = 11
    var growIndices: Set<Int>

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        guard !subviews.isEmpty else { return .zero }
        let w: CGFloat = {
            if let pw = proposal.width, pw.isFinite, pw > 0 {
                return pw
            }
            return subviews.map { $0.sizeThatFits(.unspecified).width }.max() ?? 274
        }()
        let minHeights = subviews.map { $0.sizeThatFits(ProposedViewSize(width: w, height: nil)).height }
        let naturalTotal = minHeights.reduce(0, +) + spacing * CGFloat(max(0, subviews.count - 1))
        if let proposedH = proposal.height, proposedH.isFinite {
            return CGSize(width: w, height: max(naturalTotal, proposedH))
        }
        return CGSize(width: w, height: naturalTotal)
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        guard !subviews.isEmpty else { return }
        let w = bounds.width > 0 ? bounds.width : (proposal.width ?? 274)
        let minHeights = subviews.map { $0.sizeThatFits(ProposedViewSize(width: w, height: nil)).height }
        let naturalTotal = minHeights.reduce(0, +) + spacing * CGFloat(max(0, subviews.count - 1))

        let activeGrow = growIndices.filter { $0 >= 0 && $0 < subviews.count }
        let targetH: CGFloat = {
            if let proposedH = proposal.height, proposedH.isFinite {
                return max(naturalTotal, proposedH)
            }
            return max(naturalTotal, bounds.height)
        }()
        let surplus = max(0, targetH - naturalTotal)
        let share: CGFloat = activeGrow.isEmpty ? 0 : (surplus / CGFloat(activeGrow.count))

        var y = bounds.minY
        for idx in subviews.indices {
            let childH = minHeights[idx] + (activeGrow.contains(idx) ? share : 0)
            subviews[idx].place(
                at: CGPoint(x: bounds.minX, y: y),
                anchor: .topLeading,
                proposal: ProposedViewSize(width: w, height: childH)
            )
            y += childH + spacing
        }
    }
}

private struct BentoMenuPill<MenuContent: View>: View {
    let title: String
    let systemImage: String
    let isActive: Bool
    let activeTint: Color
    let helpText: String
    @ViewBuilder let menuContent: () -> MenuContent

    @State private var isHovering = false

    var body: some View {
        Menu {
            menuContent()
        } label: {
            HStack(spacing: 5.5) {
                Image(systemName: systemImage)
                    .font(.system(size: 11.5, weight: .semibold))
                Text(title)
                    .font(.system(size: 12, weight: isActive ? .semibold : .medium))
                    .lineLimit(1)
            }
            .foregroundStyle(
                isActive
                    ? activeTint
                    : (isHovering ? Color.primary : Color.primary.opacity(0.88))
            )
        }
        .menuStyle(.borderlessButton)
        .menuIndicator(.hidden)
        .fixedSize()
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(
            RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                .fill(
                    isActive
                        ? activeTint.opacity(isHovering ? 0.17 : 0.11)
                        : ThingsMacPalette.cardSurface
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                        .fill(
                            !isActive && isHovering
                                ? ThingsMacPalette.accentBlue.opacity(0.075)
                                : Color.clear
                        )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                        .strokeBorder(
                            isActive
                                ? activeTint.opacity(isHovering ? 0.50 : 0.32)
                                : (isHovering ? ThingsMacPalette.accentBlue.opacity(0.38) : Color.primary.opacity(0.11)),
                            lineWidth: 0.85
                        )
                )
        )
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovering = hovering
            }
        }
        .thingsTooltip(helpText)
    }
}

private struct BentoWrappingChip: View {
    let title: String
    let systemImage: String
    let isActive: Bool
    let activeTint: Color
    let helpText: String
    let action: () -> Void

    @State private var isHovering = false

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 5.5) {
                Image(systemName: systemImage)
                    .font(.system(size: 11.5, weight: .semibold))
                    .padding(.top, 1)
                Text(title)
                    .font(.system(size: 12, weight: isActive ? .semibold : .medium))
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(
                isActive
                    ? activeTint
                    : (isHovering ? Color.primary : Color.primary.opacity(0.88))
            )
            .padding(.horizontal, 10.5)
            .padding(.vertical, 5.5)
            .background(
                RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                    .fill(
                        isActive
                            ? activeTint.opacity(isHovering ? 0.17 : 0.11)
                            : ThingsMacPalette.cardSurface
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .fill(
                                !isActive && isHovering
                                    ? ThingsMacPalette.accentBlue.opacity(0.075)
                                    : Color.clear
                            )
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                            .strokeBorder(
                                isActive
                                    ? activeTint.opacity(isHovering ? 0.50 : 0.32)
                                    : (isHovering ? ThingsMacPalette.accentBlue.opacity(0.38) : Color.primary.opacity(0.11)),
                                lineWidth: 0.85
                            )
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 7.5, style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovering = hovering
            }
        }
        .thingsTooltip(helpText)
    }
}


