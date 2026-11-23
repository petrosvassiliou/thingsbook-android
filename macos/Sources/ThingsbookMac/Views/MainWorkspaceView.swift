import SwiftUI
import AppKit
import ThingsbookCore

public enum MacMagicPlusCoordinateSpace {
    public static let name = "ThingsDetailSpace"
}

public struct MainWorkspaceView: View {
    @ObservedObject var store: ThingsbookStore

    @State private var columnVisibility: NavigationSplitViewVisibility = .all
    @State private var geoDetailWidth: CGFloat = 0
    @State private var splitDetailWidth: CGFloat = 0
    @State private var windowContentWidth: CGFloat = 0
    @State private var liveSidebarFrameWidth: CGFloat = 0
    @State private var isMagicPlusMenuOpen = false
    @State private var isMagicPlusHovered = false
    @State private var isMagicPlusPressed = false
    @State private var isMagicPlusDragging = false
    @State private var magicPlusDragOffset: CGSize = .zero
    @GestureState private var isMagicPlusGestureActive = false
    @State private var isQuickFindHovered = false
    @State private var columnVisibilityBeforeTimelineFullScreen: NavigationSplitViewVisibility? = nil
    @State private var isBatchTagPopoverOpen = false
    @State private var decryptPasswordInput = ""
    @State private var decryptError: String?
    @State private var keyDownMonitor: Any? = nil

    public init(store: ThingsbookStore) {
        self.store = store
    }

    private var effectiveDetailWidth: Double {
        let preferredRaw: CGFloat
        if geoDetailWidth > 300, splitDetailWidth > 300 {
            // Prefer GeometryReader unless it is momentarily reporting the unsplit full window width
            if windowContentWidth > 400, abs(geoDetailWidth - windowContentWidth) <= 12, splitDetailWidth < windowContentWidth - 100 {
                preferredRaw = splitDetailWidth
            } else {
                preferredRaw = geoDetailWidth
            }
        } else if geoDetailWidth > 300 {
            preferredRaw = geoDetailWidth
        } else {
            preferredRaw = splitDetailWidth
        }

        return MacToolbarLayoutMetrics.clampedInitialOrLiveDetailWidth(
            rawDetailWidth: Double(preferredRaw),
            windowContentWidth: Double(windowContentWidth),
            sidebarFrameWidth: Double(liveSidebarFrameWidth),
            isSidebarCollapsed: columnVisibility == .detailOnly
        )
    }

    private var toolbarMetrics: MacToolbarLayoutMetrics {
        MacToolbarLayoutMetrics.compute(
            detailWidth: effectiveDetailWidth,
            isSidebarCollapsed: columnVisibility == .detailOnly
        )
    }

    private var canDragMagicPlus: Bool {
        switch store.selectedDestination {
        case .smartView(.logbook), .smartView(.trash):
            return false
        default:
            return !store.isLocked && !store.isTimelineFullScreen
        }
    }

    private var isWideProjectWorkspace: Bool {
        switch store.selectedDestination {
        case .project(let projId):
            guard let project = store.project(byId: projId) else { return false }
            let mode = ProjectViewMode.normalized(project.viewMode)
            return mode == .board || mode == .roadmap
        case .area(let areaId):
            return store.areaViewMode(for: areaId) == .roadmap
        default:
            return false
        }
    }

    private var canShowInspector: Bool {
        geoDetailWidth >= 900 && !store.isTimelineFullScreen && !isWideProjectWorkspace
    }

    public var body: some View {
        ZStack {
            NavigationSplitView(columnVisibility: $columnVisibility) {
                SidebarView(store: store)
                    .navigationSplitViewColumnWidth(min: 252, ideal: 274, max: 340)
            } detail: {
                let isInspectorOpen = canShowInspector && store.isInspectorVisible

                VStack(spacing: 0) {
                    Rectangle()
                        .fill(Color(nsColor: .separatorColor).opacity(0.65))
                        .frame(height: 1)

                    HStack(spacing: 0) {
                        ZStack(alignment: .bottom) {
                            VStack(spacing: 0) {
                                if !store.isTimelineFullScreen, let focus = store.focusTimer {
                                    focusTimerBanner(focus)
                                        .padding(.horizontal, 26)
                                        .padding(.top, 14)
                                        .padding(.bottom, 4)
                                }
                                SmartViewCanvas(store: store, isInspectorOpen: isInspectorOpen)
                                    .clipped()
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

                            // Bottom Overlays: Multi-select bar, Undo Banner, Toast & Magic Plus Button
                            if !store.isTimelineFullScreen {
                                HStack(alignment: .bottom, spacing: 14) {
                                    Spacer()

                                    if store.selectedTaskIds.count > 1 {
                                        multiSelectBar
                                    } else if let undo = store.undoBanner {
                                        undoPillBanner(undo)
                                    } else if let toast = store.toastMessage {
                                        toastPill(toast)
                                    }

                                    Spacer()

                                    magicPlusButton
                                }
                                .padding(.horizontal, 28)
                                .padding(.bottom, 22)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .coordinateSpace(name: MacMagicPlusCoordinateSpace.name)

                        if isInspectorOpen {
                            Divider()
                            WorkspaceInspectorPanelView(store: store)
                                .transition(.move(edge: .trailing).combined(with: .opacity))
                        }
                    }
                }
                .background(
                    ThingsMacPalette.canvasBackground
                        .ignoresSafeArea(.container, edges: .top)
                )
                .background(
                    MacWindowTitlebarConfigurator(
                        targetToolbarWidth: CGFloat(toolbarMetrics.totalBarWidth)
                    ) { winW, sidebarW, measuredDetailW in
                        if winW > 300, abs(winW - windowContentWidth) >= 1.5 {
                            windowContentWidth = winW
                        }
                        if sidebarW >= 0, abs(sidebarW - liveSidebarFrameWidth) >= 1.5 {
                            liveSidebarFrameWidth = sidebarW
                        }
                        if measuredDetailW > 300, abs(measuredDetailW - splitDetailWidth) >= 1.5 {
                            splitDetailWidth = measuredDetailW
                        }
                    }
                )
                .background(
                    GeometryReader { geo in
                        Color.clear
                            .preference(
                                key: DetailPaneWidthPreferenceKey.self,
                                value: geo.size.width
                            )
                            .onChange(of: geo.size.width, initial: true) { _, newWidth in
                                if newWidth > 300, abs(newWidth - geoDetailWidth) >= 1.5 {
                                    geoDetailWidth = newWidth
                                }
                            }
                    }
                )
                .onPreferenceChange(DetailPaneWidthPreferenceKey.self) { newWidth in
                    if newWidth > 300, abs(newWidth - geoDetailWidth) >= 1.5 {
                        geoDetailWidth = newWidth
                    }
                }
                .toolbarBackground(.hidden, for: .windowToolbar)
                .toolbar {
                    if !store.isTimelineFullScreen {
                        ToolbarItem(placement: .primaryAction) {
                            unifiedDetailToolbar(metrics: toolbarMetrics)
                        }
                    }
                }
            }

            // Unified Soft Bento Task Modal Overlay (List | Board | Roadmap | Timeline | Logbook)
            if let expandedId = store.expandedTaskId,
               let modalTask = store.task(byId: expandedId) {
                GeometryReader { modalGeo in
                    ZStack {
                        Color.black.opacity(0.28)
                            .ignoresSafeArea()
                            .contentShape(Rectangle())
                            .onTapGesture {
                                NotificationCenter.default.post(name: .thingsbookDismissTaskModal, object: nil)
                            }

                        TaskBentoModalView(
                            store: store,
                            task: modalTask,
                            availableHeight: modalGeo.size.height
                        )
                        .padding(.vertical, 18)
                        .id(modalTask.id)
                        .transition(.scale(scale: 0.96).combined(with: .opacity))
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .zIndex(40)
            }

            // Touch ID Biometric App Lock Overlay
            if store.isLocked {
                biometricLockOverlay
            }
        }
        .onAppear {
            installTaskKeyboardMonitorIfNeeded()
        }
        .onDisappear {
            removeTaskKeyboardMonitor()
        }
        .onChange(of: store.isTimelineFullScreen) { _, fullScreen in
            if fullScreen {
                isMagicPlusMenuOpen = false
                isBatchTagPopoverOpen = false
            }
            withAnimation(.spring(response: 0.26, dampingFraction: 0.86)) {
                if fullScreen {
                    columnVisibilityBeforeTimelineFullScreen = columnVisibility
                    columnVisibility = .detailOnly
                } else if let previous = columnVisibilityBeforeTimelineFullScreen {
                    columnVisibility = previous
                    columnVisibilityBeforeTimelineFullScreen = nil
                } else {
                    columnVisibility = .all
                }
            }
        }
        .onChange(of: columnVisibility) { _, newVisibility in
            if store.isTimelineFullScreen && newVisibility != .detailOnly {
                columnVisibility = .detailOnly
            }
        }
        .onChange(of: store.preferences.language) { _, newLang in
            NotificationReminderService.shared.configureCategories(lang: newLang)
        }
        .sheet(isPresented: $store.isQuickFindPresented) {
            QuickFindOverlay(store: store)
        }
        .sheet(isPresented: $store.isQuickEntryPresented) {
            QuickEntrySheetView(store: store)
        }
        .sheet(isPresented: $store.isGeminiSheetPresented) {
            GeminiAssistantSheet(store: store)
        }
        .sheet(isPresented: $store.isWeeklyReviewPresented) {
            WeeklyReviewSheetView()
                .environmentObject(store)
        }
        .sheet(isPresented: $store.isTemplatesSheetPresented) {
            TemplatesAndImportSheetView()
                .environmentObject(store)
        }
        .sheet(isPresented: $store.isSettingsPresented) {
            SettingsWindowView(store: store, showCloseButton: true)
        }
        .sheet(isPresented: $store.isNewProjectSheetPresented) {
            ProjectEditorSheetView(store: store, existingProject: nil, defaultAreaId: store.newProjectDefaultAreaId)
        }
        .sheet(item: $store.editingProject) { proj in
            ProjectEditorSheetView(store: store, existingProject: proj)
        }
        .sheet(isPresented: $store.isNewAreaSheetPresented) {
            AreaEditorSheetView(store: store, existingArea: nil)
        }
        .sheet(item: $store.editingArea) { area in
            AreaEditorSheetView(store: store, existingArea: area)
        }
        .sheet(isPresented: $store.isAreaManagerPresented) {
            ManageAreasSheetView(store: store)
        }
        .sheet(isPresented: $store.isTagManagerPresented) {
            ManageTagsSheetView(store: store)
        }
        .sheet(isPresented: $store.isPerspectiveEditorPresented) {
            PerspectiveEditorSheetView(store: store, existingPerspective: store.editingPerspective)
        }
        .sheet(isPresented: $store.isBackupPasswordSheetPresented) {
            backupDecryptSheet
        }
        .sheet(item: $store.previewingAttachment) { att in
            AttachmentPreviewSheet(store: store, attachment: att)
        }
        .alert(
            store.pendingProjectDeletion.map { store.trf("project_delete_prompt_title", $0.project.title) } ?? "",
            isPresented: Binding(
                get: { store.pendingProjectDeletion != nil },
                set: { if !$0 { store.pendingProjectDeletion = nil } }
            ),
            presenting: store.pendingProjectDeletion
        ) { prompt in
            Button(store.tr("project_delete_with_tasks_btn"), role: .destructive) {
                store.confirmDeleteProject(prompt.project, deleteAssociatedTasks: true)
            }
            Button(store.tr("project_delete_keep_tasks_btn")) {
                store.confirmDeleteProject(prompt.project, deleteAssociatedTasks: false)
            }
            Button(store.tr("action_cancel"), role: .cancel) {
                store.pendingProjectDeletion = nil
            }
        } message: { prompt in
            Text(store.trf("project_delete_prompt_tasks_body", prompt.taskCount))
        }
    }

    // MARK: - Keyboard Monitor (Return / Space Open Modal & Escape / Cmd+Return Commit+Dismiss)

    private func installTaskKeyboardMonitorIfNeeded() {
        guard keyDownMonitor == nil else { return }
        keyDownMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            guard !store.isLocked else { return event }

            // Let active popovers (_NSPopoverWindow) or system panels handle their own Escape / Return keys first
            if let keyWin = NSApp.keyWindow {
                let winClass = NSStringFromClass(type(of: keyWin))
                if winClass.contains("Popover") || keyWin is NSPanel {
                    return event
                }
            }

            // When Soft Bento Task Modal is open and unobstructed by sheets/attachment previews:
            if store.expandedTaskId != nil,
               store.activeSheet == nil,
               store.previewingAttachment == nil {
                let cleanMods = event.modifierFlags.intersection([.command, .option, .control, .shift])
                if event.keyCode == 53 && cleanMods.isEmpty {
                    NotificationCenter.default.post(name: .thingsbookDismissTaskModal, object: nil)
                    return nil
                }
                let cmdMods = event.modifierFlags.intersection([.command, .option, .control])
                if (event.keyCode == 36 || event.keyCode == 76) && cmdMods == [.command] {
                    NotificationCenter.default.post(name: .thingsbookDismissTaskModal, object: nil)
                    return nil
                }
                return event
            }

            // In Timeline Full Screen: Escape exits Full Screen, and Return/Space task modal opening is blocked:
            if store.isTimelineFullScreen {
                if store.activeSheet == nil {
                    let cleanMods = event.modifierFlags.intersection([.command, .option, .control, .shift])
                    if event.keyCode == 53 && cleanMods.isEmpty {
                        withAnimation(.spring(response: 0.26, dampingFraction: 0.86)) {
                            store.isTimelineFullScreen = false
                        }
                        return nil
                    }
                }
                let blockingMods = event.modifierFlags.intersection([.command, .option, .control])
                if blockingMods.isEmpty && (event.keyCode == 36 || event.keyCode == 76 || event.keyCode == 49) {
                    return nil
                }
                return event
            }

            // When modal is closed, no sheet is presented, 1 task is selected, and user is not editing text:
            if store.activeSheet == nil,
               !store.isTimelineFullScreen,
               store.expandedTaskId == nil,
               store.selectedTaskIds.count == 1 {
                if let responder = NSApp.keyWindow?.firstResponder, responder is NSText {
                    return event
                }
                let blockingMods = event.modifierFlags.intersection([.command, .option, .control])
                if blockingMods.isEmpty && (event.keyCode == 36 || event.keyCode == 76 || event.keyCode == 49) {
                    if let selectedId = store.selectedTaskIds.first,
                       store.task(byId: selectedId) != nil {
                        store.openTaskModal(selectedId)
                        return nil
                    }
                }
            }

            return event
        }
    }

    private func removeTaskKeyboardMonitor() {
        if let monitor = keyDownMonitor {
            NSEvent.removeMonitor(monitor)
            keyDownMonitor = nil
        }
    }

    // MARK: - Focus Session (25m Pomodoro) Card

    private func focusTimerBanner(_ focus: FocusTimerState) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Image(systemName: "timer")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                VStack(alignment: .leading, spacing: 1) {
                    Text(store.tr("focus_timer_active_label").uppercased())
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(ThingsMacPalette.accentBlue)
                    Text(focus.taskTitle)
                        .font(.system(size: 13.5, weight: .semibold))
                        .lineLimit(1)
                }

                Spacer()

                Text(focus.formattedCountdown)
                    .font(.system(size: 17, weight: .bold, design: .monospaced))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                let pauseLabel = focus.isPaused ? store.tr("focus_timer_resume") : store.tr("focus_timer_pause")
                Button {
                    store.toggleFocusTimerPause()
                } label: {
                    Label(
                        pauseLabel,
                        systemImage: focus.isPaused ? "play.fill" : "pause.fill"
                    )
                    .font(.system(size: 11.5, weight: .semibold))
                    .lineLimit(1)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip(pauseLabel)

                Button(store.tr("focus_timer_plus_5m")) {
                    store.addFiveMinutesToFocusTimer()
                }
                .font(.system(size: 11.5, weight: .semibold))
                .buttonStyle(.bordered)
                .controlSize(.small)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip(store.tr("focus_timer_plus_5m"))

                Button {
                    if let task = store.allOpenTasks.first(where: { $0.id == focus.taskId }) {
                        store.toggleTaskCompletion(task)
                    }
                    store.stopFocusTimer()
                } label: {
                    Label(store.tr("action_complete"), systemImage: "checkmark")
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(ThingsMacPalette.logbookGreen)
                        .lineLimit(1)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .fixedSize(horizontal: true, vertical: false)
                .thingsTooltip(store.tr("action_complete"))

                Button {
                    store.stopFocusTimer()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .thingsTooltip(store.tr("focus_timer_stop"))
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(ThingsMacPalette.accentBlue.opacity(0.14))
                    Capsule()
                        .fill(ThingsMacPalette.accentBlue)
                        .frame(width: max(8, geo.size.width * focus.progress))
                }
            }
            .frame(height: 4)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(ThingsMacPalette.accentBlue.opacity(0.08))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(ThingsMacPalette.accentBlue.opacity(0.24), lineWidth: 1)
                )
        )
        .frame(maxWidth: (canShowInspector && store.isInspectorVisible) ? .infinity : 1360, alignment: .leading)
    }

    // MARK: - Magic Plus Button & Popover Menu

    private var magicPlusButton: some View {
        let activeMode = store.magicPlusActiveDrop?.mode ?? .todo
        let isHeadingMode = isMagicPlusDragging && activeMode == .heading

        return ZStack {
            if isMagicPlusDragging {
                RoundedRectangle(cornerRadius: 15, style: .continuous)
                    .strokeBorder(
                        ThingsMacPalette.accentBlue.opacity(0.28),
                        style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])
                    )
                    .background(
                        RoundedRectangle(cornerRadius: 15, style: .continuous)
                            .fill(ThingsMacPalette.accentBlue.opacity(0.06))
                    )
                    .frame(width: 46, height: 46)
            }

            ZStack(alignment: .top) {
                if isMagicPlusDragging {
                    HStack(spacing: 5) {
                        Image(systemName: isHeadingMode ? "text.insert" : "checkmark.circle.fill")
                            .font(.system(size: 10.5, weight: .bold))
                        Text(store.tr(isHeadingMode ? "entity_new_heading" : "entity_new_todo"))
                            .font(.system(size: 11, weight: .semibold))
                            .lineLimit(1)
                    }
                    .fixedSize(horizontal: true, vertical: false)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 4)
                    .background(
                        Capsule(style: .continuous)
                            .fill(isHeadingMode ? ThingsMacPalette.anytimeTeal : ThingsMacPalette.accentBlue)
                            .shadow(color: Color.black.opacity(0.18), radius: 6, y: 2)
                    )
                    .offset(y: -32)
                    .allowsHitTesting(false)
                }

                Image(systemName: isHeadingMode ? "text.insert" : "plus")
                    .font(.system(size: isHeadingMode ? 16 : 19, weight: .semibold))
                    .foregroundStyle(.white)
                    .rotationEffect(.degrees((isMagicPlusMenuOpen && !isMagicPlusDragging) ? 45 : 0))
                    .frame(width: 46, height: 46)
                    .background(
                        RoundedRectangle(cornerRadius: 15, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: isHeadingMode
                                        ? [ThingsMacPalette.anytimeTeal, ThingsMacPalette.accentBlue]
                                        : [ThingsMacPalette.inboxBlue, ThingsMacPalette.accentBlue],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .brightness((isMagicPlusHovered || isMagicPlusDragging) ? 0.06 : 0.0)
                            .overlay(
                                RoundedRectangle(cornerRadius: 15, style: .continuous)
                                    .strokeBorder(
                                        Color.white.opacity((isMagicPlusHovered || isMagicPlusDragging) ? 0.50 : 0.28),
                                        lineWidth: 1
                                    )
                            )
                            .shadow(
                                color: (isHeadingMode ? ThingsMacPalette.anytimeTeal : ThingsMacPalette.accentBlue)
                                    .opacity(isMagicPlusDragging ? 0.52 : (isMagicPlusHovered ? 0.48 : 0.34)),
                                radius: isMagicPlusDragging ? 18 : (isMagicPlusHovered ? 15 : 12),
                                x: 0,
                                y: isMagicPlusDragging ? 9 : (isMagicPlusHovered ? 7 : 5)
                            )
                    )
                    .scaleEffect(isMagicPlusDragging ? 1.10 : (isMagicPlusPressed ? 0.95 : (isMagicPlusHovered ? 1.06 : 1.0)))
                    .offset(y: (isMagicPlusHovered && !isMagicPlusMenuOpen && !isMagicPlusDragging) ? -1.5 : 0)
                    .animation(.spring(response: 0.22, dampingFraction: 0.72), value: isMagicPlusHovered)
                    .animation(.spring(response: 0.20, dampingFraction: 0.78), value: isMagicPlusPressed)
                    .animation(.spring(response: 0.24, dampingFraction: 0.76), value: isMagicPlusMenuOpen)
                    .animation(.easeOut(duration: 0.14), value: isHeadingMode)
            }
            .offset(magicPlusDragOffset)
        }
        .frame(width: 46, height: 46)
        .contentShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
        .zIndex(isMagicPlusDragging ? 50 : 1)
        .onHover { hovering in
            isMagicPlusHovered = hovering
            if !isMagicPlusDragging {
                if hovering {
                    NSCursor.pointingHand.set()
                } else {
                    NSCursor.arrow.set()
                }
            }
        }
        .gesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .named(MacMagicPlusCoordinateSpace.name))
                .updating($isMagicPlusGestureActive) { _, state, _ in
                    state = true
                }
                .onChanged { value in
                    if !isMagicPlusPressed {
                        isMagicPlusPressed = true
                    }
                    let distance = hypot(value.translation.width, value.translation.height)
                    if canDragMagicPlus && (isMagicPlusDragging || distance >= 6) {
                        if !isMagicPlusDragging {
                            isMagicPlusDragging = true
                            isMagicPlusMenuOpen = false
                            NSCursor.closedHand.set()
                        }
                        magicPlusDragOffset = value.translation
                        store.updateMagicPlusDrag(locationInDetail: value.location)
                    }
                }
                .onEnded { value in
                    isMagicPlusPressed = false
                    if isMagicPlusDragging {
                        let dropLocation = value.location
                        withAnimation(.spring(response: 0.26, dampingFraction: 0.78)) {
                            isMagicPlusDragging = false
                            magicPlusDragOffset = .zero
                        }
                        NSCursor.arrow.set()
                        store.commitMagicPlusDrop(locationInDetail: dropLocation)
                    } else {
                        guard !store.isLocked else { return }
                        if NSEvent.modifierFlags.contains(.option) {
                            store.createNewTaskInCurrentContext()
                        } else {
                            withAnimation(.spring(response: 0.24, dampingFraction: 0.76)) {
                                isMagicPlusMenuOpen.toggle()
                            }
                        }
                    }
                }
        )
        .onChange(of: isMagicPlusGestureActive) { _, active in
            if !active && (isMagicPlusDragging || isMagicPlusPressed) {
                isMagicPlusPressed = false
                if isMagicPlusDragging {
                    withAnimation(.spring(response: 0.24, dampingFraction: 0.80)) {
                        isMagicPlusDragging = false
                        magicPlusDragOffset = .zero
                    }
                    NSCursor.arrow.set()
                    store.cancelMagicPlusDrag()
                }
            }
        }
        .thingsTooltip(store.tr("magic_plus_fab_tooltip"))
        .popover(isPresented: $isMagicPlusMenuOpen, arrowEdge: .bottom) {
            VStack(alignment: .leading, spacing: 4) {
                magicPlusItem(
                    icon: "checkmark.circle",
                    tint: ThingsMacPalette.accentBlue,
                    title: store.tr("entity_new_todo"),
                    subtitle: magicPlusTodoSubtitle,
                    shortcut: "⌘N"
                ) {
                    isMagicPlusMenuOpen = false
                    store.createNewTaskInCurrentContext()
                }

                if case .project(let projId) = store.selectedDestination {
                    magicPlusItem(
                        icon: "text.insert",
                        tint: ThingsMacPalette.anytimeTeal,
                        title: store.tr("entity_new_heading"),
                        subtitle: store.tr("magic_plus_heading_sub"),
                        shortcut: nil
                    ) {
                        isMagicPlusMenuOpen = false
                        store.createHeadingInProject(projId)
                    }
                }

                magicPlusItem(
                    icon: "folder.badge.plus",
                    tint: ThingsMacPalette.inboxBlue,
                    title: store.tr("entity_new_project"),
                    subtitle: store.tr("magic_plus_project_sub_default"),
                    shortcut: "⌥⌘N"
                ) {
                    isMagicPlusMenuOpen = false
                    switch store.selectedDestination {
                    case .area(let aid):
                        store.newProjectDefaultAreaId = aid
                    case .project(let pid):
                        store.newProjectDefaultAreaId = store.project(byId: pid)?.areaId
                    case .perspective(let pid):
                        store.newProjectDefaultAreaId = store.perspective(byId: pid)?.areaId
                    default:
                        store.newProjectDefaultAreaId = nil
                    }
                    store.isNewProjectSheetPresented = true
                }
            }
            .padding(10)
            .frame(width: 288)
        }
    }

    private var magicPlusTodoSubtitle: String {
        switch store.selectedDestination {
        case .project: return store.tr("magic_plus_todo_sub_project")
        case .area: return store.tr("magic_plus_todo_sub_area")
        case .smartView(.inbox): return store.tr("magic_plus_todo_sub_inbox")
        default: return store.tr("magic_plus_todo_sub_view")
        }
    }

    private func magicPlusItem(
        icon: String,
        tint: Color,
        title: String,
        subtitle: String,
        shortcut: String?,
        action: @escaping () -> Void
    ) -> some View {
        MagicPlusPopoverRow(
            icon: icon,
            tint: tint,
            title: title,
            subtitle: subtitle,
            shortcut: shortcut,
            action: action
        )
    }

    // MARK: - Bottom Multi-Select, Undo & Toast Banners

    private var multiSelectBar: some View {
        let selectedCandidates = (store.allOpenTasks + store.logbookTasks).filter { store.selectedTaskIds.contains($0.id) }
        let allSelectedPinned = !selectedCandidates.isEmpty && selectedCandidates.allSatisfy(\.isPinned)

        return HStack(spacing: 11) {
            Text(store.trf("multiselect_count", store.selectedTaskIds.count))
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(.white)
                .fixedSize(horizontal: true, vertical: false)

            Divider()
                .frame(height: 14)
                .overlay(Color.white.opacity(0.25))

            Button {
                store.completeSelectedTasks()
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.circle")
                        .font(.system(size: 11.5, weight: .semibold))
                    Text(store.tr("action_complete"))
                        .font(.system(size: 12, weight: .semibold))
                }
            }
            .foregroundStyle(ThingsMacPalette.logbookGreen)
            .fixedSize(horizontal: true, vertical: false)
            .buttonStyle(.plain)
            .thingsTooltip("\(store.tr("action_complete")) (⌘K)")

            Button {
                store.scheduleSelectedTasks(.today)
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "star.fill")
                        .font(.system(size: 11, weight: .semibold))
                    Text(store.tr("view_today"))
                        .font(.system(size: 12, weight: .semibold))
                }
            }
            .foregroundStyle(ThingsMacPalette.todayYellow)
            .fixedSize(horizontal: true, vertical: false)
            .buttonStyle(.plain)
            .thingsTooltip("\(store.tr("action_when")): \(store.tr("view_today")) (⌘T)")

            Button {
                store.scheduleSelectedTasks(.someday)
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "archivebox.fill")
                        .font(.system(size: 11, weight: .semibold))
                    Text(store.tr("v25_batch_someday"))
                        .font(.system(size: 12, weight: .semibold))
                }
            }
            .foregroundStyle(ThingsMacPalette.somedayAmber)
            .fixedSize(horizontal: true, vertical: false)
            .buttonStyle(.plain)
            .thingsTooltip("\(store.tr("action_when")): \(store.tr("view_someday")) (⌘O)")

            Button {
                store.togglePinForSelectedTasks()
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: allSelectedPinned ? "pin.slash.fill" : "pin.fill")
                        .font(.system(size: 11, weight: .semibold))
                    Text(store.tr("v25_batch_pin"))
                        .font(.system(size: 12, weight: .semibold))
                }
            }
            .foregroundStyle(allSelectedPinned ? ThingsMacPalette.accentBlue : .white.opacity(0.92))
            .fixedSize(horizontal: true, vertical: false)
            .buttonStyle(.plain)
            .thingsTooltip("\(store.tr(allSelectedPinned ? "v25_unpin_task" : "v25_pin_task")) (⇧⌘P)")

            Button {
                isBatchTagPopoverOpen.toggle()
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "tag.fill")
                        .font(.system(size: 11, weight: .semibold))
                    Text(store.tr("v25_batch_tags"))
                        .font(.system(size: 12, weight: .semibold))
                }
            }
            .foregroundStyle(.white.opacity(0.92))
            .fixedSize(horizontal: true, vertical: false)
            .buttonStyle(.plain)
            .thingsTooltip(store.tr("v25_batch_tags"))
            .popover(isPresented: $isBatchTagPopoverOpen, arrowEdge: .bottom) {
                let tagState = store.batchTagSelectionState()
                TagPickerPopoverView(
                    store: store,
                    selectedTagIds: tagState.allSelected,
                    partialSelectedTagIds: tagState.partialSelected,
                    onToggleTag: { tagId in
                        store.batchToggleTagOnSelectedTasks(tagId)
                    }
                )
            }

            Button {
                store.duplicateSelectedTasks()
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "plus.square.on.square")
                        .font(.system(size: 11, weight: .semibold))
                    Text(store.tr("v25_batch_duplicate"))
                        .font(.system(size: 12, weight: .semibold))
                }
            }
            .foregroundStyle(.white.opacity(0.92))
            .fixedSize(horizontal: true, vertical: false)
            .buttonStyle(.plain)
            .thingsTooltip("\(store.tr("v25_batch_duplicate")) (⌘D)")

            Button {
                store.deleteSelectedTasks()
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "trash")
                        .font(.system(size: 11, weight: .semibold))
                    Text(store.tr("action_delete"))
                        .font(.system(size: 12, weight: .semibold))
                }
            }
            .foregroundStyle(ThingsMacPalette.upcomingRed)
            .fixedSize(horizontal: true, vertical: false)
            .buttonStyle(.plain)
            .thingsTooltip("\(store.tr("action_delete")) (⌘⌫)")

            Button {
                isBatchTagPopoverOpen = false
                store.selectedTaskIds.removeAll()
                store.lastSelectedTaskId = nil
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .foregroundStyle(.white.opacity(0.75))
            }
            .buttonStyle(.plain)
            .thingsTooltip(store.tr("multiselect_clear"))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(
            Capsule(style: .continuous)
                .fill(Color.black.opacity(0.86))
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(Color.white.opacity(0.14), lineWidth: 0.75)
                )
                .shadow(color: .black.opacity(0.22), radius: 12, y: 4)
        )
        .onDisappear {
            isBatchTagPopoverOpen = false
        }
    }

    private func undoPillBanner(_ undo: UndoBannerState) -> some View {
        HStack(spacing: 14) {
            Text(undo.message)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
            Button(store.tr("action_undo")) {
                store.performUndo()
            }
            .font(.system(size: 12.5, weight: .bold))
            .foregroundStyle(ThingsMacPalette.todayYellow)
            .fixedSize(horizontal: true, vertical: false)
            .buttonStyle(.plain)
            .thingsTooltip("\(store.tr("action_undo")) (⌘Z)")
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 10)
        .background(
            Capsule(style: .continuous)
                .fill(Color.black.opacity(0.86))
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(Color.white.opacity(0.14), lineWidth: 0.75)
                )
                .shadow(color: .black.opacity(0.22), radius: 12, y: 4)
        )
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    private func toastPill(_ toast: String) -> some View {
        Text(toast)
            .font(.system(size: 12.5, weight: .medium))
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 9)
            .background(
                Capsule(style: .continuous)
                    .fill(Color.black.opacity(0.82))
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(Color.white.opacity(0.14), lineWidth: 0.75)
                    )
            )
            .transition(.opacity)
    }

    // MARK: - Touch ID Lock & Encrypted Backup Unlock

    private var biometricLockOverlay: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                Image(systemName: "touchid")
                    .font(.system(size: 48))
                    .foregroundStyle(ThingsMacPalette.accentBlue)

                Text(store.tr("app_lock_title"))
                    .font(.system(size: 20, weight: .bold))

                Text(store.tr("macos_app_lock_subtitle"))
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 320)

                Button(store.tr("app_lock_unlock_btn")) {
                    store.unlockWithBiometrics()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
            }
            .padding(36)
            .thingsCardSurface(cornerRadius: 18)
        }
        .onAppear {
            store.unlockWithBiometrics()
        }
    }

    private var backupDecryptSheet: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label(store.tr("backup_restore_encrypted_title"), systemImage: "lock.fill")
                .font(.system(size: 15, weight: .semibold))

            Text(store.tr("backup_restore_encrypted_desc"))
                .font(.system(size: 12))
                .foregroundStyle(.secondary)

            SecureField(store.tr("backup_restore_enter_password"), text: $decryptPasswordInput)
                .textFieldStyle(.roundedBorder)
                .onSubmit(unlockPendingBackup)

            if let decryptError {
                Text(decryptError)
                    .font(.system(size: 12))
                    .foregroundStyle(.red)
            }

            HStack {
                Spacer()
                Button(store.tr("action_cancel")) {
                    store.pendingEncryptedBackupData = nil
                    store.isBackupPasswordSheetPresented = false
                    decryptPasswordInput = ""
                    decryptError = nil
                }
                Button(store.tr("backup_restore_confirm_decrypt")) {
                    unlockPendingBackup()
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(22)
        .frame(width: 400)
    }

    private func unlockPendingBackup() {
        do {
            try store.unlockAndRestorePendingBackup(password: decryptPasswordInput)
            decryptPasswordInput = ""
            decryptError = nil
        } catch {
            decryptError = store.tr("backup_err_wrong_password")
        }
    }

    private var syncDotColor: Color {
        if store.preferences.localOnlyDbMode { return .secondary.opacity(0.6) }
        if store.syncSnapshot.errorMessage != nil { return ThingsMacPalette.deadlineCrimson }
        switch store.syncSnapshot.status {
        case .synced: return ThingsMacPalette.logbookGreen
        case .syncing: return ThingsMacPalette.todayYellow
        case .offline: return .secondary.opacity(0.55)
        }
    }

    // MARK: - Adaptive Top Toolbar Controls

    private func unifiedDetailToolbar(metrics: MacToolbarLayoutMetrics) -> some View {
        HStack(spacing: 0) {
            quickFindToolbarButton(metrics: metrics)
            Spacer(minLength: CGFloat(metrics.centerGap))
            detailToolbarActionsBar(metrics: metrics)
        }
        .frame(width: CGFloat(metrics.totalBarWidth), height: CGFloat(metrics.buttonHeight))
        .fixedSize(horizontal: true, vertical: false)
    }

    private var isToolbarDisabled: Bool {
        store.isLocked || store.isTimelineFullScreen
    }

    private func quickFindToolbarButton(metrics: MacToolbarLayoutMetrics) -> some View {
        Button {
            guard !isToolbarDisabled else { return }
            store.isQuickFindPresented = true
        } label: {
            HStack(spacing: 7) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12.5, weight: .medium))
                    .foregroundStyle(isQuickFindHovered ? ThingsMacPalette.accentBlue : .secondary)

                Text(store.tr("quick_find_title"))
                    .font(.system(size: 12.5, weight: .regular))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Spacer(minLength: 4)

                if metrics.showSearchShortcutBadge {
                    Text("⌘F")
                        .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                        .foregroundStyle(.tertiary)
                        .padding(.horizontal, 5.5)
                        .padding(.vertical, 1.5)
                        .background(
                            RoundedRectangle(cornerRadius: 4, style: .continuous)
                                .fill(Color.primary.opacity(0.06))
                        )
                }
            }
            .padding(.horizontal, 10)
            .frame(width: CGFloat(metrics.searchWidth), height: CGFloat(metrics.buttonHeight))
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(ThingsMacPalette.cardSurface.opacity(isQuickFindHovered ? 1.0 : 0.88))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .strokeBorder(
                        isQuickFindHovered
                            ? ThingsMacPalette.accentBlue.opacity(0.45)
                            : Color.primary.opacity(0.14),
                        lineWidth: 1
                    )
            )
            .shadow(color: Color.black.opacity(0.03), radius: 1.5, x: 0, y: 0.5)
            .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
        .fixedSize(horizontal: true, vertical: false)
        .disabled(isToolbarDisabled)
        .opacity(isToolbarDisabled ? 0.38 : 1.0)
        .thingsTooltip(store.tr("topbar_tooltip_quick_find"))
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isQuickFindHovered = hovering && !isToolbarDisabled
            }
            if hovering && !isToolbarDisabled {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }

    private func detailToolbarActionsBar(metrics: MacToolbarLayoutMetrics) -> some View {
        let btnW = CGFloat(metrics.buttonWidth)
        let btnH = CGFloat(metrics.buttonHeight)
        let spacing = CGFloat(metrics.itemSpacing)

        return HStack(spacing: 0) {
            // Quick Entry + Voice Capture
            HStack(spacing: spacing) {
                MacToolbarIconButton(
                    systemImage: "plus.bubble",
                    width: btnW,
                    height: btnH,
                    isDisabled: isToolbarDisabled,
                    helpText: store.tr("topbar_tooltip_quick_entry")
                ) {
                    guard !isToolbarDisabled else { return }
                    store.quickEntryPrefillTitle = ""
                    store.quickEntryPrefillNotes = ""
                    store.quickEntryAutoStartVoice = false
                    store.isQuickEntryPresented = true
                }

                MacToolbarIconButton(
                    systemImage: "mic",
                    width: btnW,
                    height: btnH,
                    isDisabled: isToolbarDisabled,
                    helpText: store.tr("topbar_tooltip_voice_input")
                ) {
                    guard !isToolbarDisabled else { return }
                    store.quickEntryPrefillTitle = ""
                    store.quickEntryPrefillNotes = ""
                    store.quickEntryAutoStartVoice = true
                    store.isQuickEntryPresented = true
                }
            }

            toolbarGroupDivider(metrics: metrics)

            // Gemini Assistant
            MacToolbarGeminiButton(
                minWidth: CGFloat(metrics.geminiPillMinWidth),
                height: btnH,
                showLabel: metrics.showGeminiLabel,
                isDisabled: isToolbarDisabled,
                helpText: store.tr("gemini_hub_title")
            ) {
                guard !isToolbarDisabled else { return }
                store.geminiInitialTab = .brainDump
                store.isGeminiSheetPresented = true
            }

            toolbarGroupDivider(metrics: metrics)

            // Cloud Sync Status + Settings
            HStack(spacing: spacing) {
                MacToolbarIconButton(
                    width: CGFloat(metrics.syncButtonWidth),
                    height: btnH,
                    isDisabled: isToolbarDisabled,
                    helpText: store.tr("topbar_tooltip_cloud_sync"),
                    action: {
                        guard !isToolbarDisabled else { return }
                        if !store.preferences.localOnlyDbMode && store.syncSnapshot.userEmailOrId != nil {
                            store.syncNow()
                        }
                        store.settingsTab = .sync
                        store.isSettingsPresented = true
                    }
                ) {
                    HStack(spacing: 4.5) {
                        Image(systemName: store.preferences.localOnlyDbMode ? "internaldrive" : "icloud")
                            .font(.system(size: 14, weight: .medium))
                        Circle()
                            .fill(syncDotColor)
                            .frame(width: 6.5, height: 6.5)
                    }
                }

                MacToolbarIconButton(
                    systemImage: "gearshape",
                    width: btnW,
                    height: btnH,
                    isDisabled: isToolbarDisabled,
                    helpText: store.tr("topbar_tooltip_settings")
                ) {
                    guard !isToolbarDisabled else { return }
                    store.settingsTab = .general
                    store.isSettingsPresented = true
                }

                if canShowInspector {
                    MacToolbarIconButton(
                        width: btnW,
                        height: btnH,
                        isActive: store.isInspectorVisible,
                        isDisabled: isToolbarDisabled,
                        helpText: store.tr("v30_inspector_toggle_tooltip"),
                        action: {
                            guard !isToolbarDisabled else { return }
                            store.toggleInspector()
                        }
                    ) {
                        Image(systemName: "sidebar.right")
                            .font(.system(size: 14, weight: .medium))
                    }
                    .thingsTooltip(store.tr("v30_inspector_toggle_tooltip"))
                }
            }
        }
        .padding(.trailing, CGFloat(metrics.trailingActionsPadding))
        .fixedSize(horizontal: true, vertical: false)
    }

    private func toolbarGroupDivider(metrics: MacToolbarLayoutMetrics) -> some View {
        Rectangle()
            .fill(Color.primary.opacity(0.11))
            .frame(width: 1, height: 16)
            .padding(.horizontal, CGFloat(metrics.groupDividerPadding))
    }
}

private struct DetailPaneWidthPreferenceKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        let next = nextValue()
        if next > 0 {
            value = next
        }
    }
}

private struct MacToolbarGeminiButton: View {
    let minWidth: CGFloat
    let height: CGFloat
    let showLabel: Bool
    let isDisabled: Bool
    let helpText: String
    let action: () -> Void

    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5.5) {
                Image(systemName: "sparkles")
                    .font(.system(size: 12, weight: .semibold))
                if showLabel {
                    Text("Gemini")
                        .font(.system(size: 12.5, weight: .semibold))
                        .lineLimit(1)
                }
            }
            .foregroundStyle(ThingsMacPalette.geminiPurple)
            .padding(.horizontal, showLabel ? 11 : 0)
            .frame(minWidth: minWidth, minHeight: height, maxHeight: height)
            .background(
                Capsule(style: .continuous)
                    .fill(ThingsMacPalette.geminiPurple.opacity(isHovered ? 0.21 : 0.13))
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(ThingsMacPalette.geminiPurple.opacity(isHovered ? 0.28 : 0.14), lineWidth: 0.75)
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .thingsTooltip(helpText)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovered = hovering
            }
            if hovering && !isDisabled {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }
}

private struct ToolbarSymbolIcon: View {
    let systemImage: String

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 14, weight: .medium))
    }
}

private struct MacToolbarIconButton<Label: View>: View {
    let width: CGFloat
    let height: CGFloat
    let isActive: Bool
    let isDisabled: Bool
    let helpText: String
    let action: () -> Void
    let label: Label

    @State private var isHovered = false

    init(
        width: CGFloat,
        height: CGFloat,
        isActive: Bool = false,
        isDisabled: Bool = false,
        helpText: String,
        action: @escaping () -> Void,
        @ViewBuilder label: () -> Label
    ) {
        self.width = width
        self.height = height
        self.isActive = isActive
        self.isDisabled = isDisabled
        self.helpText = helpText
        self.action = action
        self.label = label()
    }

    var body: some View {
        Button(action: action) {
            label
                .foregroundStyle(
                    isActive
                        ? ThingsMacPalette.accentBlue
                        : (isHovered ? Color.primary : Color.primary.opacity(0.78))
                )
                .frame(width: width, height: height)
                .background(
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(
                            isActive
                                ? ThingsMacPalette.accentBlue.opacity(isHovered ? 0.16 : 0.10)
                                : Color.primary.opacity(isHovered ? 0.075 : 0.0)
                        )
                )
                .contentShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .thingsTopBarIconTooltip(helpText)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovered = hovering
            }
            if hovering && !isDisabled {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }
}

extension MacToolbarIconButton where Label == ToolbarSymbolIcon {
    init(
        systemImage: String,
        width: CGFloat,
        height: CGFloat,
        isActive: Bool = false,
        isDisabled: Bool = false,
        helpText: String,
        action: @escaping () -> Void
    ) {
        self.init(
            width: width,
            height: height,
            isActive: isActive,
            isDisabled: isDisabled,
            helpText: helpText,
            action: action
        ) {
            ToolbarSymbolIcon(systemImage: systemImage)
        }
    }
}

private struct MacWindowTitlebarConfigurator: NSViewRepresentable {
    let targetToolbarWidth: CGFloat
    let onSplitGeometryUpdate: (CGFloat, CGFloat, CGFloat) -> Void

    func makeNSView(context: Context) -> TitlebarSyncNSView {
        let view = TitlebarSyncNSView()
        view.targetToolbarWidth = targetToolbarWidth
        view.onSplitGeometryUpdate = onSplitGeometryUpdate
        return view
    }

    func updateNSView(_ nsView: TitlebarSyncNSView, context: Context) {
        nsView.targetToolbarWidth = targetToolbarWidth
        nsView.onSplitGeometryUpdate = onSplitGeometryUpdate
        nsView.syncAll(forceToolbarRelayout: false)
    }

    final class TitlebarSyncNSView: NSView {
        var targetToolbarWidth: CGFloat = 0
        var onSplitGeometryUpdate: ((CGFloat, CGFloat, CGFloat) -> Void)?

        private var lastInvalidatedToolbarWidth: CGFloat = 0
        private var lastReportedWinW: CGFloat = 0
        private var lastReportedSidebarW: CGFloat = -1
        private var lastReportedDetailW: CGFloat = 0
        private var observers: [NSObjectProtocol] = []

        deinit {
            let tokens = observers
            for token in tokens {
                NotificationCenter.default.removeObserver(token)
            }
        }

        override func viewWillMove(toWindow newWindow: NSWindow?) {
            if newWindow !== window {
                removeObservers()
            }
            super.viewWillMove(toWindow: newWindow)
        }

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            installObserversIfNeeded()
            syncAll(forceToolbarRelayout: true)

            // Deferred passes catch initial NSSplitViewController + NSToolbar attachment on cold launch
            for delay in [0.0, 0.03, 0.12, 0.28] {
                DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
                    self?.syncAll(forceToolbarRelayout: true)
                }
            }
        }

        override func layout() {
            super.layout()
            syncAll(forceToolbarRelayout: false)
        }

        private func removeObservers() {
            for token in observers {
                NotificationCenter.default.removeObserver(token)
            }
            observers.removeAll()
        }

        private func installObserversIfNeeded() {
            removeObservers()
            guard let window else { return }
            let nc = NotificationCenter.default

            observers.append(
                nc.addObserver(forName: NSWindow.didResizeNotification, object: window, queue: .main) { [weak self] _ in
                    self?.syncAll(forceToolbarRelayout: true)
                }
            )
            observers.append(
                nc.addObserver(forName: NSWindow.didBecomeKeyNotification, object: window, queue: .main) { [weak self] _ in
                    self?.syncAll(forceToolbarRelayout: true)
                }
            )
            observers.append(
                nc.addObserver(forName: NSSplitView.didResizeSubviewsNotification, object: nil, queue: .main) { [weak self] note in
                    guard let self,
                          let split = note.object as? NSSplitView,
                          split.window === self.window else { return }
                    self.syncAll(forceToolbarRelayout: true)
                }
            )
        }

        func syncAll(forceToolbarRelayout: Bool) {
            guard let window else { return }
            if !window.titlebarAppearsTransparent {
                window.titlebarAppearsTransparent = true
            }
            if window.titlebarSeparatorStyle != .none {
                window.titlebarSeparatorStyle = .none
            }
            if let items = window.toolbar?.items {
                for item in items where item.toolTip != nil {
                    item.toolTip = nil
                }
            }

            let frameContentW = window.contentRect(forFrameRect: window.frame).width
            let winContentW = frameContentW > 300
                ? frameContentW
                : (window.contentView?.bounds.width ?? 0)

            var sidebarW: CGFloat = 0
            var detailW: CGFloat = bounds.width
            if let splitView = locateAncestorSplitView() {
                // Walk up from `self` (mounted inside the `detail:` column) to find the exact
                // direct child of `splitView` hosting the detail pane — immune to `subviews` z-order!
                var detailPane: NSView? = self
                while let parent = detailPane?.superview, parent !== splitView {
                    detailPane = parent
                }
                if let detailPane, detailPane.superview === splitView {
                    if detailPane.frame.width > 300 {
                        detailW = detailPane.frame.width
                    }
                    let otherPanes = splitView.arrangedSubviews.filter { $0 !== detailPane }
                    sidebarW = otherPanes
                        .filter {
                            !splitView.isSubviewCollapsed($0) &&
                            !$0.isHidden &&
                            $0.frame.width >= 20 &&
                            $0.frame.width <= 420
                        }
                        .map(\.frame.width)
                        .reduce(0, +)
                }
            }

            let geomChanged = abs(winContentW - lastReportedWinW) >= 1.5 ||
                abs(sidebarW - lastReportedSidebarW) >= 1.5 ||
                abs(detailW - lastReportedDetailW) >= 1.5

            if geomChanged && winContentW > 300 {
                lastReportedWinW = winContentW
                lastReportedSidebarW = sidebarW
                lastReportedDetailW = detailW
                let callback = onSplitGeometryUpdate
                DispatchQueue.main.async {
                    callback?(winContentW, sidebarW, detailW)
                }
            }

            let widthDelta = abs(targetToolbarWidth - lastInvalidatedToolbarWidth)
            if forceToolbarRelayout || geomChanged || widthDelta >= 1.0 {
                lastInvalidatedToolbarWidth = targetToolbarWidth
                invalidateToolbarLayout(in: window)
                // Also re-invalidate on the next runloop turn after SwiftUI commits the updated
                // `unifiedDetailToolbar` NSHostingView intrinsicContentSize!
                DispatchQueue.main.async { [weak self] in
                    guard let self, let win = self.window else { return }
                    self.invalidateToolbarLayout(in: win)
                }
            }
        }

        private func locateAncestorSplitView() -> NSSplitView? {
            var ancestor: NSView? = superview
            while let current = ancestor {
                if let sv = current as? NSSplitView, sv.isVertical {
                    return sv
                }
                ancestor = current.superview
            }
            return nil
        }

        private func invalidateToolbarLayout(in window: NSWindow) {
            window.toolbar?.validateVisibleItems()
            guard let items = window.toolbar?.items else { return }
            for item in items {
                guard let itemView = item.view else { continue }
                itemView.invalidateIntrinsicContentSize()
                itemView.needsLayout = true
                var parent = itemView.superview
                var hops = 0
                while let p = parent, hops < 4 {
                    p.invalidateIntrinsicContentSize()
                    p.needsLayout = true
                    p.layoutSubtreeIfNeeded()
                    parent = p.superview
                    hops += 1
                }
            }
        }
    }
}

private struct MagicPlusPopoverRow: View {
    let icon: String
    let tint: Color
    let title: String
    let subtitle: String
    let shortcut: String?
    let action: () -> Void

    @State private var isHovered = false

    private var rowTooltip: String {
        if let shortcut, !shortcut.isEmpty {
            return "\(title) (\(shortcut)) — \(subtitle)"
        }
        return "\(title) — \(subtitle)"
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 11) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 28, height: 28)
                    .background(
                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                            .fill(tint.opacity(isHovered ? 0.18 : 0.12))
                    )

                VStack(alignment: .leading, spacing: 1.5) {
                    HStack(spacing: 6) {
                        Text(title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.primary)
                        if let shortcut {
                            Text(shortcut)
                                .font(.system(size: 10, weight: .semibold, design: .rounded))
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 1)
                                .background(
                                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                                        .fill(Color.primary.opacity(0.07))
                                )
                        }
                    }
                    Text(subtitle)
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer()
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(isHovered ? ThingsMacPalette.accentBlue.opacity(0.09) : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .thingsTooltip(rowTooltip)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovered = hovering
            }
            if hovering {
                NSCursor.pointingHand.set()
            } else {
                NSCursor.arrow.set()
            }
        }
    }
}


