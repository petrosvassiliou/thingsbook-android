import SwiftUI
import AppKit
import ThingsbookCore

public enum ThingsMacPalette {
    public static let accentBlue = Color(red: 0.11, green: 0.48, blue: 0.96)       // #1C7AF5
    public static let inboxBlue = Color(red: 0.14, green: 0.55, blue: 0.98)        // #248CFA
    public static let todayYellow = Color(red: 0.98, green: 0.73, blue: 0.08)      // #FAB914
    public static let eveningPurple = Color(red: 0.47, green: 0.39, blue: 0.94)    // #7864F0
    public static let geminiPurple = Color(red: 0.45, green: 0.35, blue: 0.93)     // #7359ED
    public static let upcomingRed = Color(red: 0.94, green: 0.30, blue: 0.37)      // #F04D5E
    public static let anytimeTeal = Color(red: 0.15, green: 0.66, blue: 0.64)      // #26A8A3
    public static let somedayAmber = Color(red: 0.84, green: 0.61, blue: 0.22)     // #D69C38
    public static let logbookGreen = Color(red: 0.18, green: 0.73, blue: 0.41)     // #2EBA69
    public static let trashGray = Color(red: 0.53, green: 0.56, blue: 0.62)
    public static let areaGreen = Color(red: 0.16, green: 0.69, blue: 0.46)
    public static let deadlineCrimson = Color(red: 0.89, green: 0.22, blue: 0.28)

    /// Crisp Things-for-Mac canvas background (#F8FAFC in Light, #11141B in Dark)
    public static let canvasBackground = Color(nsColor: NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        return isDark
            ? NSColor(srgbRed: 0.067, green: 0.078, blue: 0.106, alpha: 1.0)
            : NSColor(srgbRed: 0.973, green: 0.980, blue: 0.990, alpha: 1.0)
    })

    /// Elevated white/charcoal card surface (#FFFFFF in Light, #1B1F29 in Dark)
    public static let cardSurface = Color(nsColor: NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        return isDark
            ? NSColor(srgbRed: 0.106, green: 0.122, blue: 0.161, alpha: 1.0)
            : NSColor(srgbRed: 1.000, green: 1.000, blue: 1.000, alpha: 1.0)
    })

    /// Subtle inset surface for notes boxes & secondary containers (#F4F7FB in Light, #151922 in Dark)
    public static let subtleContainer = Color(nsColor: NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        return isDark
            ? NSColor(srgbRed: 0.082, green: 0.098, blue: 0.133, alpha: 1.0)
            : NSColor(srgbRed: 0.957, green: 0.969, blue: 0.986, alpha: 1.0)
    })

    public static func color(for view: SmartView) -> Color {
        switch view {
        case .inbox: return inboxBlue
        case .today: return todayYellow
        case .upcoming: return upcomingRed
        case .anytime: return anytimeTeal
        case .someday: return somedayAmber
        case .logbook: return logbookGreen
        case .trash: return trashGray
        }
    }

    public static func colorFromArgb(_ argb: UInt32) -> Color {
        let r = Double((argb >> 16) & 0xFF) / 255.0
        let g = Double((argb >> 8) & 0xFF) / 255.0
        let b = Double(argb & 0xFF) / 255.0
        return Color(red: r, green: g, blue: b)
    }
}

public struct ThingsCheckboxButton: View {
    public let status: TaskStatus
    public let isSomeday: Bool
    public let helpText: String
    public let onToggleComplete: () -> Void
    public let onCancelTask: () -> Void

    @State private var isHovering = false

    public init(
        status: TaskStatus,
        isSomeday: Bool = false,
        helpText: String = "",
        onToggleComplete: @escaping () -> Void,
        onCancelTask: @escaping () -> Void
    ) {
        self.status = status
        self.isSomeday = isSomeday
        self.helpText = helpText
        self.onToggleComplete = onToggleComplete
        self.onCancelTask = onCancelTask
    }

    public var body: some View {
        Button {
            if NSEvent.modifierFlags.contains(.option) {
                onCancelTask()
            } else {
                onToggleComplete()
            }
        } label: {
            ZStack {
                switch status {
                case .open:
                    RoundedRectangle(cornerRadius: 5.5, style: .continuous)
                        .strokeBorder(
                            isHovering ? ThingsMacPalette.accentBlue : Color.secondary.opacity(0.48),
                            style: StrokeStyle(
                                lineWidth: 1.55,
                                dash: isSomeday ? [2.8, 2.2] : []
                            )
                        )
                        .background(
                            RoundedRectangle(cornerRadius: 5.5, style: .continuous)
                                .fill(isHovering ? ThingsMacPalette.accentBlue.opacity(0.10) : ThingsMacPalette.cardSurface)
                        )
                case .completed:
                    RoundedRectangle(cornerRadius: 5.5, style: .continuous)
                        .fill(ThingsMacPalette.accentBlue)
                    Image(systemName: "checkmark")
                        .font(.system(size: 10.5, weight: .bold))
                        .foregroundStyle(.white)
                case .canceled:
                    RoundedRectangle(cornerRadius: 5.5, style: .continuous)
                        .fill(Color.secondary.opacity(0.28))
                    Image(systemName: "xmark")
                        .font(.system(size: 9.5, weight: .bold))
                        .foregroundStyle(.primary.opacity(0.8))
                }
            }
            .frame(width: 18, height: 18)
            .contentShape(Rectangle())
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

public struct ProjectProgressRing: View {
    public let progress: Double
    public var size: CGFloat = 16
    public var tint: Color = ThingsMacPalette.accentBlue
    public var isCompleted: Bool = false

    public init(progress: Double, size: CGFloat = 16, tint: Color = ThingsMacPalette.accentBlue, isCompleted: Bool = false) {
        self.progress = min(1.0, max(0.0, progress))
        self.size = size
        self.tint = tint
        self.isCompleted = isCompleted
    }

    public var body: some View {
        ZStack {
            if isCompleted {
                Circle()
                    .fill(ThingsMacPalette.logbookGreen)
                Image(systemName: "checkmark")
                    .font(.system(size: size * 0.52, weight: .bold))
                    .foregroundStyle(.white)
            } else {
                Circle()
                    .strokeBorder(tint.opacity(0.55), lineWidth: 1.5)

                if progress > 0.001 {
                    PieSliceShape(progress: progress)
                        .fill(tint)
                        .padding(2.3)
                }
            }
        }
        .frame(width: size, height: size)
    }
}

private struct PieSliceShape: Shape {
    var progress: Double

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2.0
        path.move(to: center)
        path.addArc(
            center: center,
            radius: radius,
            startAngle: .degrees(-90),
            endAngle: .degrees(-90 + 360.0 * progress),
            clockwise: false
        )
        path.closeSubpath()
        return path
    }
}

public struct TagPillBadge: View {
    public let title: String
    public var isSelected: Bool = false
    public var showIcon: Bool = false
    public var helpText: String? = nil
    public var onTap: (() -> Void)? = nil

    public init(
        title: String,
        isSelected: Bool = false,
        showIcon: Bool = false,
        helpText: String? = nil,
        onTap: (() -> Void)? = nil
    ) {
        self.title = title
        self.isSelected = isSelected
        self.showIcon = showIcon
        self.helpText = helpText
        self.onTap = onTap
    }

    public var body: some View {
        let content = HStack(spacing: 4) {
            if showIcon {
                Image(systemName: "tag")
                    .font(.system(size: 9.5, weight: .semibold))
            }
            Text(title)
                .font(.system(size: 11.5, weight: isSelected ? .semibold : .medium))
                .lineLimit(1)
        }
        .fixedSize(horizontal: true, vertical: false)
        .foregroundStyle(isSelected ? Color.white : Color.secondary)
        .padding(.horizontal, 9)
        .padding(.vertical, 3.5)
        .background(
            Capsule(style: .continuous)
                .fill(isSelected ? ThingsMacPalette.accentBlue : Color.primary.opacity(0.055))
        )
        .overlay(
            Capsule(style: .continuous)
                .strokeBorder(isSelected ? Color.clear : Color.primary.opacity(0.08), lineWidth: 0.75)
        )

        if let onTap {
            Button(action: onTap) { content }
                .buttonStyle(.plain)
                .thingsTooltip(helpText ?? "#\(title)")
        } else if let helpText {
            content.thingsTooltip(helpText)
        } else {
            content
        }
    }
}

/// Responsive wrapping flow layout (macOS 14+) that measures each subview at its natural
/// untruncated width and wraps onto subsequent rows instead of compressing button labels.
public struct ThingsFlowLayout: Layout {
    public var horizontalSpacing: CGFloat
    public var verticalSpacing: CGFloat

    public init(horizontalSpacing: CGFloat = 8, verticalSpacing: CGFloat = 8) {
        self.horizontalSpacing = horizontalSpacing
        self.verticalSpacing = verticalSpacing
    }

    public func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        guard !subviews.isEmpty else { return .zero }
        let widestItem = subviews.map { $0.sizeThatFits(.unspecified).width }.max() ?? 0
        let maxWidth: CGFloat
        if let proposedWidth = proposal.width, proposedWidth.isFinite {
            maxWidth = max(proposedWidth, widestItem)
        } else {
            maxWidth = .infinity
        }

        let rows = computeRows(maxWidth: maxWidth, subviews: subviews)
        var naturalWidth: CGFloat = 0
        var totalHeight: CGFloat = 0
        for (index, row) in rows.enumerated() {
            naturalWidth = max(naturalWidth, row.width)
            totalHeight += row.height
            if index > 0 {
                totalHeight += verticalSpacing
            }
        }

        if let proposedWidth = proposal.width, proposedWidth.isFinite, proposedWidth > 0 {
            return CGSize(width: max(proposedWidth, naturalWidth), height: totalHeight)
        }
        return CGSize(width: naturalWidth, height: totalHeight)
    }

    public func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        guard !subviews.isEmpty else { return }
        let widestItem = subviews.map { $0.sizeThatFits(.unspecified).width }.max() ?? 0
        let maxWidth = bounds.width > 0 ? max(bounds.width, widestItem) : .infinity
        let rows = computeRows(maxWidth: maxWidth, subviews: subviews)

        var y = bounds.minY
        for row in rows {
            var x = bounds.minX
            for item in row.items {
                let yOffset = (row.height - item.size.height) / 2.0
                item.subview.place(
                    at: CGPoint(x: x, y: y + yOffset),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(item.size)
                )
                x += item.size.width + horizontalSpacing
            }
            y += row.height + verticalSpacing
        }
    }

    private struct RowItem {
        let subview: LayoutSubview
        let size: CGSize
    }

    private struct Row {
        var items: [RowItem] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func computeRows(maxWidth: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = []
        var currentRow = Row()

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            let nextWidth = currentRow.items.isEmpty
                ? size.width
                : currentRow.width + horizontalSpacing + size.width

            if !currentRow.items.isEmpty && nextWidth > maxWidth {
                rows.append(currentRow)
                currentRow = Row(
                    items: [RowItem(subview: subview, size: size)],
                    width: size.width,
                    height: size.height
                )
            } else {
                currentRow.items.append(RowItem(subview: subview, size: size))
                currentRow.width = nextWidth
                currentRow.height = max(currentRow.height, size.height)
            }
        }

        if !currentRow.items.isEmpty {
            rows.append(currentRow)
        }
        return rows
    }
}

/// Readable, non-truncating Things-style action button with hover illumination and native macOS tooltip.
public struct ThingsActionChipButton: View {
    public let title: String
    public let systemImage: String
    public var isActive: Bool = false
    public var activeTint: Color = ThingsMacPalette.accentBlue
    public let helpText: String
    public let action: () -> Void

    @State private var isHovering = false

    public init(
        title: String,
        systemImage: String,
        isActive: Bool = false,
        activeTint: Color = ThingsMacPalette.accentBlue,
        helpText: String,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.isActive = isActive
        self.activeTint = activeTint
        self.helpText = helpText
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: 5.5) {
                Image(systemName: systemImage)
                    .font(.system(size: 11.5, weight: .semibold))
                Text(title)
                    .font(.system(size: 12, weight: isActive ? .semibold : .medium))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
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
                            ? activeTint.opacity(isHovering ? 0.18 : 0.11)
                            : (isHovering ? Color.primary.opacity(0.08) : ThingsMacPalette.subtleContainer)
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 7.5, style: .continuous)
                    .strokeBorder(
                        isActive
                            ? activeTint.opacity(isHovering ? 0.48 : 0.32)
                            : Color.primary.opacity(isHovering ? 0.24 : 0.13),
                        lineWidth: 0.9
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 7.5, style: .continuous))
        }
        .buttonStyle(.plain)
        .fixedSize(horizontal: true, vertical: false)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovering = hovering
            }
        }
        .thingsTooltip(helpText)
        .accessibilityLabel(title)
        .accessibilityHint(helpText)
    }
}

/// Rounded icon utility button with hover highlight and reliable button-level macOS tooltip.
public struct ThingsUtilityIconButton: View {
    public let systemImage: String
    public var tint: Color = ThingsMacPalette.accentBlue
    public let helpText: String
    public let action: () -> Void

    @State private var isHovering = false

    public init(
        systemImage: String,
        tint: Color = ThingsMacPalette.accentBlue,
        helpText: String,
        action: @escaping () -> Void
    ) {
        self.systemImage = systemImage
        self.tint = tint
        self.helpText = helpText
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(isHovering ? tint : tint.opacity(0.86))
                .frame(width: 28, height: 28)
                .background(
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(isHovering ? tint.opacity(0.13) : Color.primary.opacity(0.035))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .strokeBorder(isHovering ? tint.opacity(0.28) : Color.primary.opacity(0.08), lineWidth: 0.8)
                )
                .contentShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovering = hovering
            }
        }
        .thingsTooltip(helpText)
        .accessibilityLabel(helpText)
    }
}

public struct GlassSurfaceModifier: ViewModifier {
    public var cornerRadius: CGFloat = 12
    public var isSelected: Bool = false

    public func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(ThingsMacPalette.cardSurface)
                    .shadow(
                        color: Color.black.opacity(isSelected ? 0.11 : 0.05),
                        radius: isSelected ? 18 : 8,
                        x: 0,
                        y: isSelected ? 6 : 2
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(
                        isSelected ? ThingsMacPalette.accentBlue.opacity(0.38) : Color.primary.opacity(0.075),
                        lineWidth: isSelected ? 1.2 : 0.85
                    )
            )
    }
}

public extension View {
    func thingsCardSurface(cornerRadius: CGFloat = 12, isSelected: Bool = false) -> some View {
        modifier(GlassSurfaceModifier(cornerRadius: cornerRadius, isSelected: isSelected))
    }

    /// Preserves VoiceOver accessibility hint without rendering a visual tooltip outside the top bar.
    func thingsTooltip(_ text: String) -> some View {
        modifier(ThingsMacTooltipModifier(text: text, isTopBarIcon: false))
    }

    /// Attaches a discreet floating tooltip strictly to top-bar icon buttons (`MacToolbarIconButton`).
    func thingsTopBarIconTooltip(_ text: String) -> some View {
        modifier(ThingsMacTooltipModifier(text: text, isTopBarIcon: true))
    }
}

// MARK: - Discreet Top-Bar-Icon-Only AppKit Floating Tooltip Engine

private struct ThingsMacTooltipModifier: ViewModifier {
    let text: String
    let isTopBarIcon: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if isTopBarIcon && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            content
                .background(ThingsTooltipTrackingRepresentable(text: text))
                .accessibilityHint(text)
        } else {
            content
                .accessibilityHint(text)
        }
    }
}

private struct ThingsTooltipTrackingRepresentable: NSViewRepresentable {
    let text: String

    func makeNSView(context: Context) -> ThingsTooltipTrackingNSView {
        let view = ThingsTooltipTrackingNSView()
        view.tooltipText = text
        return view
    }

    func updateNSView(_ nsView: ThingsTooltipTrackingNSView, context: Context) {
        if nsView.tooltipText != text {
            nsView.tooltipText = text
            ThingsMacTooltipController.shared.anchorUpdatedText(nsView)
        }
    }

    static func dismantleNSView(_ nsView: ThingsTooltipTrackingNSView, coordinator: ()) {
        ThingsMacTooltipController.shared.anchorExited(nsView)
    }
}

@MainActor
fileprivate final class ThingsTooltipTrackingNSView: NSView {
    let anchorId: String = UUID().uuidString
    var tooltipText: String = ""
    private var trackingAreaRef: NSTrackingArea?

    override func hitTest(_ point: NSPoint) -> NSView? {
        nil
    }

    override func updateTrackingAreas() {
        if let existing = trackingAreaRef {
            removeTrackingArea(existing)
        }
        let area = NSTrackingArea(
            rect: .zero,
            options: [.mouseEnteredAndExited, .mouseMoved, .inVisibleRect, .activeAlways],
            owner: self,
            userInfo: nil
        )
        addTrackingArea(area)
        trackingAreaRef = area
        super.updateTrackingAreas()
    }

    override func mouseEntered(with event: NSEvent) {
        ThingsMacTooltipController.shared.anchorEntered(self)
    }

    override func mouseMoved(with event: NSEvent) {
        ThingsMacTooltipController.shared.anchorMoved(self)
    }

    override func mouseExited(with event: NSEvent) {
        ThingsMacTooltipController.shared.anchorExited(self)
    }

    override func viewWillMove(toWindow newWindow: NSWindow?) {
        if newWindow == nil {
            ThingsMacTooltipController.shared.anchorExited(self)
        }
        super.viewWillMove(toWindow: newWindow)
    }

    func screenRect() -> NSRect {
        guard let window else { return .zero }
        let rectInWindow = convert(bounds, to: nil)
        return window.convertToScreen(rectInWindow)
    }
}

fileprivate final class WeakTooltipAnchorEntry {
    let id: String
    weak var view: ThingsTooltipTrackingNSView?

    init(id: String, view: ThingsTooltipTrackingNSView) {
        self.id = id
        self.view = view
    }
}

fileprivate final class ThingsTooltipBubbleNSView: NSView {
    static let textFont: NSFont = .systemFont(ofSize: 11.5, weight: .medium)
    static let horizontalPadding: CGFloat = 9.5
    static let verticalPadding: CGFloat = 4.5
    static let maxTextWidth: CGFloat = 300.0

    var tooltipText: String = "" {
        didSet {
            if oldValue != tooltipText {
                needsDisplay = true
            }
        }
    }

    override var isFlipped: Bool { true }

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.cornerRadius = 6.0
        layer?.cornerCurve = .continuous
        layer?.masksToBounds = true
    }

    required init?(coder: NSCoder) {
        nil
    }

    static func measureBubbleSize(for text: String) -> NSSize {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return NSSize(width: 36.0, height: 23.0) }

        let singleLineSize = (trimmed as NSString).size(withAttributes: [.font: textFont])
        if ceil(singleLineSize.width) <= maxTextWidth {
            let bubbleW = max(32.0, ceil(singleLineSize.width + horizontalPadding * 2.0))
            let bubbleH = max(23.0, ceil(singleLineSize.height + verticalPadding * 2.0))
            return NSSize(width: bubbleW, height: bubbleH)
        }

        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.lineBreakMode = .byWordWrapping
        let wrappedRect = (trimmed as NSString).boundingRect(
            with: NSSize(width: maxTextWidth, height: 160.0),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [
                .font: textFont,
                .paragraphStyle: paragraph
            ]
        )
        let bubbleW = max(32.0, ceil(min(maxTextWidth, wrappedRect.width) + horizontalPadding * 2.0))
        let bubbleH = max(23.0, ceil(wrappedRect.height + verticalPadding * 2.0))
        return NSSize(width: bubbleW, height: bubbleH)
    }

    override func draw(_ dirtyRect: NSRect) {
        let isDark = effectiveAppearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        let path = NSBezierPath(roundedRect: bounds.insetBy(dx: 0.375, dy: 0.375), xRadius: 6.0, yRadius: 6.0)
        let textColor: NSColor
        if isDark {
            // Soft elevated slate matching Dark mode surface — never pitch black
            NSColor(srgbRed: 0.180, green: 0.196, blue: 0.239, alpha: 0.95).setFill()
            path.fill()
            NSColor.white.withAlphaComponent(0.11).setStroke()
            textColor = NSColor(srgbRed: 0.855, green: 0.875, blue: 0.910, alpha: 1.0)
        } else {
            // Discreet warm-pearl surface with subtle hairline border
            NSColor(srgbRed: 0.969, green: 0.973, blue: 0.984, alpha: 0.95).setFill()
            path.fill()
            NSColor(srgbRed: 0.110, green: 0.118, blue: 0.141, alpha: 0.12).setStroke()
            textColor = NSColor(srgbRed: 0.243, green: 0.267, blue: 0.314, alpha: 1.0)
        }
        path.lineWidth = 0.75
        path.stroke()

        let trimmed = tooltipText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        let singleAttrs: [NSAttributedString.Key: Any] = [
            .font: Self.textFont,
            .foregroundColor: textColor
        ]
        let singleLineSize = (trimmed as NSString).size(withAttributes: singleAttrs)
        if ceil(singleLineSize.width) <= Self.maxTextWidth {
            let drawOrigin = NSPoint(
                x: (bounds.width - singleLineSize.width) / 2.0,
                y: (bounds.height - singleLineSize.height) / 2.0
            )
            (trimmed as NSString).draw(at: drawOrigin, withAttributes: singleAttrs)
        } else {
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = .center
            paragraph.lineBreakMode = .byWordWrapping
            let wrappedAttrs: [NSAttributedString.Key: Any] = [
                .font: Self.textFont,
                .foregroundColor: textColor,
                .paragraphStyle: paragraph
            ]
            let availW = max(16.0, bounds.width - Self.horizontalPadding * 2.0)
            let wrappedRect = (trimmed as NSString).boundingRect(
                with: NSSize(width: availW, height: 160.0),
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: wrappedAttrs
            )
            let drawRect = NSRect(
                x: Self.horizontalPadding,
                y: (bounds.height - wrappedRect.height) / 2.0,
                width: availW,
                height: ceil(wrappedRect.height) + 2.0
            )
            (trimmed as NSString).draw(
                with: drawRect,
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: wrappedAttrs
            )
        }
    }
}

@MainActor
public final class ThingsMacTooltipController {
    public static let shared = ThingsMacTooltipController()

    private var timing = TooltipTimingCoordinator(initialDelayMs: 160, warmHandoffWindowMs: 450)
    private var hoveredAnchors: [WeakTooltipAnchorEntry] = []
    private var pendingWorkItem: DispatchWorkItem?
    private var watchdogTimer: Timer?
    private var localEventMonitor: Any?

    private var panel: NSPanel?
    private var bubbleView: ThingsTooltipBubbleNSView?

    private init() {
        NotificationCenter.default.addObserver(
            forName: NSApplication.didResignActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.dismissImmediately(allowWarmHandoff: false)
            }
        }

        NotificationCenter.default.addObserver(
            forName: NSWindow.didResignKeyNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.dismissImmediately(allowWarmHandoff: false)
            }
        }

        NotificationCenter.default.addObserver(
            forName: NSWindow.willCloseNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.dismissImmediately(allowWarmHandoff: false)
            }
        }

        localEventMonitor = NSEvent.addLocalMonitorForEvents(
            matching: [.leftMouseDown, .rightMouseDown, .otherMouseDown, .scrollWheel, .keyDown]
        ) { [weak self] event in
            MainActor.assumeIsolated {
                self?.dismissImmediately(allowWarmHandoff: false)
            }
            return event
        }
    }

    fileprivate func anchorEntered(_ view: ThingsTooltipTrackingNSView) {
        pruneDetachedAnchors()
        if !hoveredAnchors.contains(where: { $0.id == view.anchorId }) {
            hoveredAnchors.append(WeakTooltipAnchorEntry(id: view.anchorId, view: view))
        }
        reconcileActiveAnchor()
    }

    fileprivate func anchorMoved(_ view: ThingsTooltipTrackingNSView) {
        if !hoveredAnchors.contains(where: { $0.id == view.anchorId }) {
            hoveredAnchors.append(WeakTooltipAnchorEntry(id: view.anchorId, view: view))
            reconcileActiveAnchor()
        }
    }

    fileprivate func anchorUpdatedText(_ view: ThingsTooltipTrackingNSView) {
        guard timing.hoveredAnchorId == view.anchorId else { return }
        let action = timing.updateText(
            anchorId: view.anchorId,
            text: view.tooltipText,
            nowMs: Self.nowMs()
        )
        apply(action, anchorView: view)
    }

    fileprivate func anchorExited(_ view: ThingsTooltipTrackingNSView) {
        hoveredAnchors.removeAll { $0.id == view.anchorId || $0.view == nil }
        reconcileActiveAnchor()
    }

    public func dismissImmediately(allowWarmHandoff: Bool = false) {
        hoveredAnchors.removeAll()
        let action = timing.dismissAll(nowMs: Self.nowMs(), allowWarmHandoff: allowWarmHandoff)
        apply(action, anchorView: nil)
    }

    private func pruneDetachedAnchors() {
        let mouseLoc = NSEvent.mouseLocation
        hoveredAnchors.removeAll { entry in
            guard let v = entry.view,
                  let win = v.window,
                  win.isVisible,
                  !v.isHiddenOrHasHiddenAncestor,
                  !v.tooltipText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return true
            }
            let rect = v.screenRect()
            guard rect.width > 0.5, rect.height > 0.5 else { return true }
            return !rect.insetBy(dx: -3, dy: -3).contains(mouseLoc)
        }
    }

    private func selectBestHoveredAnchor() -> ThingsTooltipTrackingNSView? {
        pruneDetachedAnchors()
        guard NSApp.isActive, !hoveredAnchors.isEmpty else { return nil }

        let keyWindow = NSApp.keyWindow
        let candidates = hoveredAnchors.compactMap { $0.view }

        let windowFiltered: [ThingsTooltipTrackingNSView]
        if let keyWindow, candidates.contains(where: { $0.window === keyWindow }) {
            windowFiltered = candidates.filter { $0.window === keyWindow }
        } else {
            windowFiltered = candidates
        }

        // Prefer the smallest (most specific / innermost) control when tracking rects nest
        return windowFiltered.min { lhs, rhs in
            let lr = lhs.screenRect()
            let rr = rhs.screenRect()
            return (lr.width * lr.height) < (rr.width * rr.height)
        }
    }

    private func reconcileActiveAnchor() {
        let now = Self.nowMs()
        if let winner = selectBestHoveredAnchor() {
            let action: TooltipTimingCoordinator.Action
            if timing.hoveredAnchorId != winner.anchorId {
                action = timing.pointerEntered(
                    anchorId: winner.anchorId,
                    text: winner.tooltipText,
                    nowMs: now
                )
            } else {
                action = timing.updateText(
                    anchorId: winner.anchorId,
                    text: winner.tooltipText,
                    nowMs: now
                )
            }
            apply(action, anchorView: winner)
        } else if let staleId = timing.hoveredAnchorId ?? timing.visibleAnchorId {
            let action = timing.pointerExited(anchorId: staleId, nowMs: now)
            apply(action, anchorView: nil)
        } else {
            pendingWorkItem?.cancel()
            pendingWorkItem = nil
            panel?.orderOut(nil)
            stopWatchdogIfIdle()
        }
    }

    private func apply(
        _ action: TooltipTimingCoordinator.Action,
        anchorView: ThingsTooltipTrackingNSView?
    ) {
        switch action {
        case .none:
            break

        case .hideImmediately:
            pendingWorkItem?.cancel()
            pendingWorkItem = nil
            panel?.orderOut(nil)
            stopWatchdogIfIdle()

        case .scheduleShow(let anchorId, _, let delayMs):
            pendingWorkItem?.cancel()
            panel?.orderOut(nil)
            let work = DispatchWorkItem { [weak self, weak anchorView] in
                guard let self else { return }
                guard let anchorView,
                      anchorView.window != nil,
                      anchorView.window?.isVisible == true,
                      NSApp.isActive,
                      anchorView.screenRect().insetBy(dx: -3, dy: -3).contains(NSEvent.mouseLocation) else {
                    self.reconcileActiveAnchor()
                    return
                }
                let commitAction = self.timing.commitScheduledShow(
                    anchorId: anchorId,
                    nowMs: Self.nowMs()
                )
                self.apply(commitAction, anchorView: anchorView)
            }
            pendingWorkItem = work
            DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(Int(delayMs)), execute: work)
            ensureWatchdogRunning()

        case .showImmediately(_, let text):
            pendingWorkItem?.cancel()
            pendingWorkItem = nil
            guard let anchorView,
                  let parentWin = anchorView.window,
                  parentWin.isVisible,
                  NSApp.isActive else {
                panel?.orderOut(nil)
                stopWatchdogIfIdle()
                return
            }
            presentPanel(
                text: text,
                anchorRect: anchorView.screenRect(),
                parentWindow: parentWin,
                anchorAppearance: anchorView.effectiveAppearance
            )
            ensureWatchdogRunning()
        }
    }

    private func ensurePanelCreated() {
        guard panel == nil else { return }
        let p = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: 140, height: 24),
            styleMask: [.borderless, .nonactivatingPanel],
            backing: .buffered,
            defer: false
        )
        p.isFloatingPanel = true
        p.level = .popUpMenu
        p.backgroundColor = .clear
        p.isOpaque = false
        p.hasShadow = true
        p.ignoresMouseEvents = true
        p.hidesOnDeactivate = true
        p.collectionBehavior = [.transient, .ignoresCycle, .fullScreenAuxiliary]

        let bubble = ThingsTooltipBubbleNSView(frame: NSRect(x: 0, y: 0, width: 140, height: 24))
        p.contentView = bubble
        panel = p
        bubbleView = bubble
    }

    private func presentPanel(
        text: String,
        anchorRect: NSRect,
        parentWindow: NSWindow,
        anchorAppearance: NSAppearance
    ) {
        guard anchorRect.width > 0.5, anchorRect.height > 0.5 else {
            panel?.orderOut(nil)
            return
        }
        ensurePanelCreated()
        guard let panel, let bubbleView else { return }

        panel.appearance = anchorAppearance

        let bubbleSize = ThingsTooltipBubbleNSView.measureBubbleSize(for: text)
        let bubbleW = bubbleSize.width
        let bubbleH = bubbleSize.height

        let anchorCenter = NSPoint(x: anchorRect.midX, y: anchorRect.midY)
        let targetScreen = NSScreen.screens.first(where: { $0.frame.contains(anchorCenter) })
            ?? parentWindow.screen
            ?? NSScreen.main
        let visibleFrame = targetScreen?.visibleFrame ?? NSRect(x: 0, y: 0, width: 1440, height: 900)

        var originY = round(anchorRect.minY - bubbleH - 6)
        if originY < visibleFrame.minY + 8 {
            originY = round(anchorRect.maxY + 6)
        }

        let minX = visibleFrame.minX + 8
        let maxX = max(minX, visibleFrame.maxX - bubbleW - 8)
        let originX = min(max(round(anchorRect.midX - bubbleW / 2.0), minX), maxX)

        bubbleView.tooltipText = text
        bubbleView.frame = NSRect(x: 0, y: 0, width: bubbleW, height: bubbleH)
        bubbleView.needsDisplay = true

        panel.setFrame(NSRect(x: originX, y: originY, width: bubbleW, height: bubbleH), display: true)
        panel.orderFrontRegardless()
    }

    private func ensureWatchdogRunning() {
        guard watchdogTimer == nil else { return }
        watchdogTimer = Timer.scheduledTimer(withTimeInterval: 0.10, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.watchdogTick()
            }
        }
    }

    private func stopWatchdogIfIdle() {
        if pendingWorkItem == nil && !timing.isShowing && hoveredAnchors.isEmpty {
            watchdogTimer?.invalidate()
            watchdogTimer = nil
        }
    }

    private func watchdogTick() {
        if !NSApp.isActive {
            dismissImmediately(allowWarmHandoff: false)
            return
        }
        reconcileActiveAnchor()
    }

    private static func nowMs() -> Int64 {
        Int64(ProcessInfo.processInfo.systemUptime * 1000.0)
    }
}

