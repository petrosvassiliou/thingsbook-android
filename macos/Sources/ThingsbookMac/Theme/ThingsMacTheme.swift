import SwiftUI
import AppKit
import ThingsbookCore

public enum ThingsMacPalette {
    /// Thingsbook 3.0 Sky Light Blue accent (#3898EC in Aqua, #6BC0FF in DarkAqua)
    public static let accentBlue = Color(nsColor: NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        return isDark
            ? NSColor(srgbRed: 0.420, green: 0.753, blue: 1.000, alpha: 1.0)
            : NSColor(srgbRed: 0.220, green: 0.596, blue: 0.925, alpha: 1.0)
    })
    /// Glacier Light Blue highlight (#74C4FF in Aqua, #9BD6FF in DarkAqua)
    public static let inboxBlue = Color(nsColor: NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        return isDark
            ? NSColor(srgbRed: 0.608, green: 0.839, blue: 1.000, alpha: 1.0)
            : NSColor(srgbRed: 0.455, green: 0.769, blue: 1.000, alpha: 1.0)
    })
    public static let todayYellow = Color(red: 0.96, green: 0.71, blue: 0.00)
    public static let eveningPurple = Color(red: 0.47, green: 0.39, blue: 0.94)
    public static let geminiPurple = Color(red: 0.45, green: 0.35, blue: 0.93)
    public static let upcomingRed = Color(red: 0.94, green: 0.30, blue: 0.37)
    public static let anytimeTeal = Color(red: 0.08, green: 0.65, blue: 0.59)
    public static let somedayAmber = Color(red: 0.84, green: 0.61, blue: 0.22)
    public static let logbookGreen = Color(red: 0.18, green: 0.68, blue: 0.36)
    public static let trashGray = Color(red: 0.53, green: 0.56, blue: 0.62)
    public static let areaGreen = accentBlue
    public static let deadlineCrimson = Color(red: 0.89, green: 0.22, blue: 0.28)
    /// Milestone Royal Violet (#7D3AED in Aqua, #9F67FF in DarkAqua)
    public static let milestoneViolet = Color(nsColor: NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        return isDark
            ? NSColor(srgbRed: 0.624, green: 0.404, blue: 1.000, alpha: 1.0)
            : NSColor(srgbRed: 0.490, green: 0.230, blue: 0.930, alpha: 1.0)
    })

    /// Crisp Pure White in Light Mode (#FFFFFF) & Deep Neutral Charcoal in Dark Mode (#121417)
    public static let canvasBackground = Color(nsColor: NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        return isDark
            ? NSColor(srgbRed: 0.071, green: 0.078, blue: 0.090, alpha: 1.0)
            : NSColor(srgbRed: 1.000, green: 1.000, blue: 1.000, alpha: 1.0)
    })

    /// Elevated Pure White (#FFFFFF) in Light Mode & Neutral Slate (#1B1F25) in Dark Mode
    public static let cardSurface = Color(nsColor: NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        return isDark
            ? NSColor(srgbRed: 0.106, green: 0.122, blue: 0.145, alpha: 1.0)
            : NSColor(srgbRed: 1.000, green: 1.000, blue: 1.000, alpha: 1.0)
    })

    /// Subtle inset container (#F4F6FA in Light, #171A1E in Dark)
    public static let subtleContainer = Color(nsColor: NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        return isDark
            ? NSColor(srgbRed: 0.090, green: 0.102, blue: 0.118, alpha: 1.0)
            : NSColor(srgbRed: 0.957, green: 0.965, blue: 0.980, alpha: 1.0)
    })

    /// Cohesive monochrome slate icon tint when inactive; illuminates in Sky Light Blue when active.
    public static func color(for view: SmartView, isSelected: Bool = false) -> Color {
        isSelected ? accentBlue : Color.secondary
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
    public let isMilestone: Bool
    public let isInProgress: Bool
    public let helpText: String
    public let onToggleComplete: () -> Void
    public let onCancelTask: () -> Void
    public let onToggleInProgress: (() -> Void)?

    @State private var isHovering = false

    public init(
        status: TaskStatus,
        isSomeday: Bool = false,
        isMilestone: Bool = false,
        isInProgress: Bool = false,
        helpText: String = "",
        onToggleComplete: @escaping () -> Void,
        onCancelTask: @escaping () -> Void,
        onToggleInProgress: (() -> Void)? = nil
    ) {
        self.status = status
        self.isSomeday = isSomeday
        self.isMilestone = isMilestone
        self.isInProgress = isInProgress
        self.helpText = helpText
        self.onToggleComplete = onToggleComplete
        self.onCancelTask = onCancelTask
        self.onToggleInProgress = onToggleInProgress
    }

    public var body: some View {
        let activeInProgress = status == .open && isInProgress

        Button {
            if NSEvent.modifierFlags.contains(.shift), status == .open, let onToggleInProgress {
                onToggleInProgress()
            } else if NSEvent.modifierFlags.contains(.option) {
                onCancelTask()
            } else {
                onToggleComplete()
            }
        } label: {
            ZStack {
                if isMilestone {
                    switch status {
                    case .open:
                        RoundedRectangle(cornerRadius: 3.5, style: .continuous)
                            .strokeBorder(
                                activeInProgress
                                    ? ThingsMacPalette.somedayAmber.opacity(isHovering ? 1.0 : 0.94)
                                    : ThingsMacPalette.milestoneViolet.opacity(isHovering ? 1.0 : 0.88),
                                style: StrokeStyle(
                                    lineWidth: 1.65,
                                    dash: isSomeday ? [2.8, 2.2] : []
                                )
                            )
                            .background(
                                RoundedRectangle(cornerRadius: 3.5, style: .continuous)
                                    .fill(
                                        activeInProgress
                                            ? ThingsMacPalette.somedayAmber.opacity(isHovering ? 0.24 : 0.13)
                                            : ThingsMacPalette.milestoneViolet.opacity(isHovering ? 0.22 : 0.11)
                                    )
                            )
                            .frame(width: 13.5, height: 13.5)
                            .rotationEffect(.degrees(45))

                        if activeInProgress {
                            Circle()
                                .fill(ThingsMacPalette.somedayAmber)
                                .frame(width: 5.2, height: 5.2)
                                .shadow(color: ThingsMacPalette.somedayAmber.opacity(0.45), radius: 2.0)
                        }
                    case .completed:
                        RoundedRectangle(cornerRadius: 3.5, style: .continuous)
                            .fill(ThingsMacPalette.milestoneViolet)
                            .frame(width: 13.5, height: 13.5)
                            .rotationEffect(.degrees(45))
                        Image(systemName: "checkmark")
                            .font(.system(size: 9.0, weight: .bold))
                            .foregroundStyle(.white)
                    case .canceled:
                        RoundedRectangle(cornerRadius: 3.5, style: .continuous)
                            .fill(Color.secondary.opacity(0.28))
                            .frame(width: 13.5, height: 13.5)
                            .rotationEffect(.degrees(45))
                        Image(systemName: "xmark")
                            .font(.system(size: 8.5, weight: .bold))
                            .foregroundStyle(.primary.opacity(0.8))
                    }
                } else {
                    switch status {
                    case .open:
                        RoundedRectangle(cornerRadius: 6.0, style: .continuous)
                            .strokeBorder(
                                activeInProgress
                                    ? ThingsMacPalette.somedayAmber.opacity(isHovering ? 1.0 : 0.94)
                                    : (isHovering ? ThingsMacPalette.accentBlue : Color.secondary.opacity(0.48)),
                                style: StrokeStyle(
                                    lineWidth: 1.55,
                                    dash: isSomeday ? [2.8, 2.2] : []
                                )
                            )
                            .background(
                                RoundedRectangle(cornerRadius: 6.0, style: .continuous)
                                    .fill(
                                        activeInProgress
                                            ? ThingsMacPalette.somedayAmber.opacity(isHovering ? 0.20 : 0.11)
                                            : (isHovering ? ThingsMacPalette.accentBlue.opacity(0.10) : ThingsMacPalette.cardSurface)
                                    )
                            )

                        if activeInProgress {
                            Circle()
                                .fill(ThingsMacPalette.somedayAmber)
                                .frame(width: 6.2, height: 6.2)
                                .shadow(color: ThingsMacPalette.somedayAmber.opacity(0.45), radius: 2.0)
                        }
                    case .completed:
                        Circle()
                            .fill(ThingsMacPalette.accentBlue)
                        Image(systemName: "checkmark")
                            .font(.system(size: 10.0, weight: .bold))
                            .foregroundStyle(.white)
                    case .canceled:
                        RoundedRectangle(cornerRadius: 6.0, style: .continuous)
                            .fill(Color.secondary.opacity(0.28))
                        Image(systemName: "xmark")
                            .font(.system(size: 9.5, weight: .bold))
                            .foregroundStyle(.primary.opacity(0.8))
                    }
                }
            }
            .frame(width: 18, height: 18)
            .animation(.spring(response: 0.24, dampingFraction: 0.72), value: status)
            .animation(.spring(response: 0.24, dampingFraction: 0.78), value: isMilestone)
            .animation(.spring(response: 0.24, dampingFraction: 0.76), value: activeInProgress)
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
    public var inProgressProgress: Double = 0
    public var size: CGFloat = 16
    public var tint: Color = ThingsMacPalette.accentBlue
    public var isCompleted: Bool = false
    public var showFraction: Bool = false
    public var completedCount: Int = 0
    public var totalCount: Int = 0

    public init(
        progress: Double,
        inProgressProgress: Double = 0,
        size: CGFloat = 16,
        tint: Color = ThingsMacPalette.accentBlue,
        isCompleted: Bool = false,
        showFraction: Bool = false,
        completedCount: Int = 0,
        totalCount: Int = 0
    ) {
        let clampedCompleted = min(1.0, max(0.0, progress))
        self.progress = clampedCompleted
        self.inProgressProgress = min(max(0.0, 1.0 - clampedCompleted), max(0.0, inProgressProgress))
        self.size = size
        self.tint = tint
        self.isCompleted = isCompleted
        self.showFraction = showFraction
        self.completedCount = completedCount
        self.totalCount = totalCount
    }

    public var body: some View {
        let barWidth = min(44, max(24, size * 1.65))
        let barHeight = min(7.5, max(5.0, size * 0.34))
        let badgeHeight = min(15.0, max(11.0, size * 0.68))
        let clampedInProgress = min(max(0.0, 1.0 - progress), max(0.0, inProgressProgress))

        HStack(spacing: 5) {
            if isCompleted || progress >= 0.999 {
                ZStack {
                    Capsule(style: .continuous)
                        .fill(tint)
                    Image(systemName: "checkmark")
                        .font(.system(size: badgeHeight * 0.56, weight: .bold))
                        .foregroundStyle(.white)
                }
                .frame(width: barWidth, height: badgeHeight)
            } else {
                ZStack(alignment: .leading) {
                    Capsule(style: .continuous)
                        .fill(tint.opacity(0.20))
                        .frame(width: barWidth, height: barHeight)

                    if clampedInProgress > 0.005 {
                        Capsule(style: .continuous)
                            .fill(ThingsMacPalette.somedayAmber.opacity(0.72))
                            .frame(width: max(barHeight, barWidth * min(1.0, progress + clampedInProgress)), height: barHeight)
                    }

                    if progress > 0.005 {
                        Capsule(style: .continuous)
                            .fill(tint)
                            .frame(width: max(barHeight, barWidth * progress), height: barHeight)
                    }
                }
                .frame(width: barWidth, height: barHeight)
            }

            if showFraction && totalCount > 0 {
                Text("\(completedCount)/\(totalCount)")
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .foregroundStyle(.secondary)
                    .fixedSize()
            }
        }
        .animation(.spring(response: 0.28, dampingFraction: 0.82), value: progress)
        .animation(.spring(response: 0.28, dampingFraction: 0.82), value: clampedInProgress)
    }
}

public struct TagPillBadge: View {
    public let title: String
    public var isSelected: Bool = false
    public var showIcon: Bool = false
    public var helpText: String? = nil
    public var onTap: (() -> Void)? = nil

    @State private var isHovering = false

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
        let interactiveHover = isHovering && onTap != nil
        let content = HStack(spacing: 4) {
            if showIcon {
                Image(systemName: "square.grid.2x2")
                    .font(.system(size: 9.5, weight: .semibold))
            }
            Text(title)
                .font(.system(size: 11.5, weight: isSelected ? .semibold : .medium))
                .lineLimit(1)
        }
        .fixedSize(horizontal: true, vertical: false)
        .foregroundStyle(
            isSelected
                ? Color.white
                : (interactiveHover ? ThingsMacPalette.accentBlue : Color.secondary)
        )
        .padding(.horizontal, 9)
        .padding(.vertical, 3.5)
        .background(
            Capsule(style: .continuous)
                .fill(
                    isSelected
                        ? ThingsMacPalette.accentBlue.opacity(interactiveHover ? 0.90 : 1.0)
                        : (interactiveHover ? ThingsMacPalette.accentBlue.opacity(0.09) : Color.primary.opacity(0.055))
                )
        )
        .overlay(
            Capsule(style: .continuous)
                .strokeBorder(
                    isSelected
                        ? Color.clear
                        : (interactiveHover ? ThingsMacPalette.accentBlue.opacity(0.34) : Color.primary.opacity(0.08)),
                    lineWidth: 0.75
                )
        )

        if let onTap {
            Button(action: onTap) { content }
                .buttonStyle(.plain)
                .onHover { hovering in
                    withAnimation(.easeOut(duration: 0.12)) {
                        isHovering = hovering
                    }
                }
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

public struct ThingsDropTargetModifier: ViewModifier {
    public let cornerRadius: CGFloat
    public let isCapsule: Bool
    public let onDropPayloads: ([String]) -> Bool

    @State private var isDropTargeted = false

    public init(
        cornerRadius: CGFloat = 12,
        isCapsule: Bool = false,
        onDropPayloads: @escaping ([String]) -> Bool
    ) {
        self.cornerRadius = cornerRadius
        self.isCapsule = isCapsule
        self.onDropPayloads = onDropPayloads
    }

    public func body(content: Content) -> some View {
        content
            .overlay {
                if isDropTargeted {
                    if isCapsule {
                        Capsule(style: .continuous)
                            .strokeBorder(ThingsMacPalette.accentBlue, lineWidth: 2.0)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(ThingsMacPalette.accentBlue.opacity(0.13))
                            )
                            .allowsHitTesting(false)
                    } else {
                        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                            .strokeBorder(ThingsMacPalette.accentBlue, lineWidth: 2.0)
                            .background(
                                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                                    .fill(ThingsMacPalette.accentBlue.opacity(0.10))
                            )
                            .allowsHitTesting(false)
                    }
                }
            }
            .animation(.easeOut(duration: 0.12), value: isDropTargeted)
            .dropDestination(for: String.self) { payloads, _ in
                onDropPayloads(payloads)
            } isTargeted: { targeted in
                isDropTargeted = targeted
            }
    }
}

public extension View {
    func thingsCardSurface(cornerRadius: CGFloat = 12, isSelected: Bool = false) -> some View {
        modifier(GlassSurfaceModifier(cornerRadius: cornerRadius, isSelected: isSelected))
    }

    func thingsDropTarget(
        cornerRadius: CGFloat = 12,
        isCapsule: Bool = false,
        onDropPayloads: @escaping ([String]) -> Bool
    ) -> some View {
        modifier(ThingsDropTargetModifier(cornerRadius: cornerRadius, isCapsule: isCapsule, onDropPayloads: onDropPayloads))
    }

    /// Preserves VoiceOver accessibility hint without rendering a visual tooltip outside the top bar.
    func thingsTooltip(_ text: String) -> some View {
        modifier(
            ThingsMacTooltipModifier(
                text: text,
                isVisualTooltipEnabled: false,
                requiresMouseOnly: false,
                isSuppressed: false
            )
        )
    }

    /// Attaches a discreet floating tooltip strictly to top-bar icon buttons (`MacToolbarIconButton`).
    func thingsTopBarIconTooltip(_ text: String) -> some View {
        modifier(
            ThingsMacTooltipModifier(
                text: text,
                isVisualTooltipEnabled: true,
                requiresMouseOnly: false,
                isSuppressed: false
            )
        )
    }

    /// Attaches a mouse-only floating tooltip to timeline task bars, milestones, and rows.
    func thingsTimelineMouseTooltip(_ text: String, isSuppressed: Bool = false) -> some View {
        modifier(
            ThingsMacTooltipModifier(
                text: text,
                isVisualTooltipEnabled: true,
                requiresMouseOnly: true,
                isSuppressed: isSuppressed
            )
        )
    }
}

// MARK: - Discreet Top-Bar & Timeline Mouse-Only AppKit Floating Tooltip Engine

private struct ThingsMacTooltipModifier: ViewModifier {
    let text: String
    let isVisualTooltipEnabled: Bool
    let requiresMouseOnly: Bool
    let isSuppressed: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if isVisualTooltipEnabled && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            content
                .background(
                    ThingsTooltipTrackingRepresentable(
                        text: text,
                        requiresMouseOnly: requiresMouseOnly,
                        isSuppressed: isSuppressed
                    )
                )
                .accessibilityHint(text)
        } else {
            content
                .accessibilityHint(text)
        }
    }
}

private struct ThingsTooltipTrackingRepresentable: NSViewRepresentable {
    let text: String
    let requiresMouseOnly: Bool
    let isSuppressed: Bool

    func makeNSView(context: Context) -> ThingsTooltipTrackingNSView {
        let view = ThingsTooltipTrackingNSView()
        view.tooltipText = text
        view.requiresMouseOnly = requiresMouseOnly
        view.isSuppressed = isSuppressed
        return view
    }

    func updateNSView(_ nsView: ThingsTooltipTrackingNSView, context: Context) {
        nsView.requiresMouseOnly = requiresMouseOnly
        if nsView.isSuppressed != isSuppressed {
            nsView.isSuppressed = isSuppressed
            if isSuppressed {
                ThingsMacTooltipController.shared.anchorExited(nsView)
            }
        }
        if nsView.tooltipText != text {
            nsView.tooltipText = text
            if !isSuppressed {
                ThingsMacTooltipController.shared.anchorUpdatedText(nsView)
            }
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
    var requiresMouseOnly: Bool = false
    var isSuppressed: Bool = false
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

    private func isEligibleMouseHoverEvent(_ event: NSEvent) -> Bool {
        var isMousePointer = !requiresMouseOnly || ThingsMacTooltipController.shared.isMouseInputActive
        if event.type == .mouseMoved {
            if event.subtype == .touch {
                isMousePointer = false
            }
        }
        return TooltipTimingCoordinator.isEligibleMouseHover(
            isMousePointer: isMousePointer,
            pressedMouseButtons: NSEvent.pressedMouseButtons,
            isSuppressed: isSuppressed
        )
    }

    override func mouseEntered(with event: NSEvent) {
        guard isEligibleMouseHoverEvent(event) else {
            ThingsMacTooltipController.shared.anchorExited(self)
            return
        }
        ThingsMacTooltipController.shared.anchorEntered(self)
    }

    override func mouseMoved(with event: NSEvent) {
        guard isEligibleMouseHoverEvent(event) else {
            ThingsMacTooltipController.shared.anchorExited(self)
            return
        }
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
    static let verticalPadding: CGFloat = 5.0
    static let maxTextWidth: CGFloat = 320.0

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
        layer?.cornerRadius = 6.5
        layer?.cornerCurve = .continuous
        layer?.masksToBounds = true
    }

    required init?(coder: NSCoder) {
        nil
    }

    static func measureBubbleSize(for text: String) -> NSSize {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return NSSize(width: 36.0, height: 23.0) }

        let isMultiline = trimmed.contains("\n")
        if !isMultiline {
            let singleLineSize = (trimmed as NSString).size(withAttributes: [.font: textFont])
            if ceil(singleLineSize.width) <= maxTextWidth {
                let bubbleW = max(32.0, ceil(singleLineSize.width + horizontalPadding * 2.0))
                let bubbleH = max(23.0, ceil(singleLineSize.height + verticalPadding * 2.0))
                return NSSize(width: bubbleW, height: bubbleH)
            }
        }

        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = isMultiline ? .left : .center
        paragraph.lineBreakMode = .byWordWrapping
        if isMultiline {
            paragraph.lineSpacing = 2.0
        }
        let wrappedRect = (trimmed as NSString).boundingRect(
            with: NSSize(width: maxTextWidth, height: 220.0),
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
        let path = NSBezierPath(roundedRect: bounds.insetBy(dx: 0.375, dy: 0.375), xRadius: 6.5, yRadius: 6.5)
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

        let isMultiline = trimmed.contains("\n")
        if !isMultiline {
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
                return
            }
        }

        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = isMultiline ? .left : .center
        paragraph.lineBreakMode = .byWordWrapping
        if isMultiline {
            paragraph.lineSpacing = 2.0
        }
        let wrappedAttrs: [NSAttributedString.Key: Any] = [
            .font: Self.textFont,
            .foregroundColor: textColor,
            .paragraphStyle: paragraph
        ]
        let availW = max(16.0, bounds.width - Self.horizontalPadding * 2.0)
        let wrappedRect = (trimmed as NSString).boundingRect(
            with: NSSize(width: availW, height: 220.0),
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

@MainActor
public final class ThingsMacTooltipController {
    public static let shared = ThingsMacTooltipController()

    private var timing = TooltipTimingCoordinator(initialDelayMs: 160, warmHandoffWindowMs: 450)
    private var hoveredAnchors: [WeakTooltipAnchorEntry] = []
    private var pendingWorkItem: DispatchWorkItem?
    private var watchdogTimer: Timer?
    private var localEventMonitor: Any?
    fileprivate private(set) var isMouseInputActive: Bool = true

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
            matching: [
                .mouseMoved,
                .leftMouseDown,
                .rightMouseDown,
                .otherMouseDown,
                .leftMouseDragged,
                .rightMouseDragged,
                .otherMouseDragged,
                .scrollWheel,
                .keyDown
            ]
        ) { [weak self] event in
            MainActor.assumeIsolated {
                guard let self else { return }
                if event.type == .mouseMoved {
                    if event.subtype == .touch {
                        self.isMouseInputActive = false
                        self.dismissImmediately(allowWarmHandoff: false)
                    } else if NSEvent.pressedMouseButtons == 0 {
                        self.isMouseInputActive = true
                    } else {
                        self.dismissImmediately(allowWarmHandoff: false)
                    }
                } else {
                    if event.type == .leftMouseDown || event.type == .leftMouseDragged {
                        if event.subtype == .touch {
                            self.isMouseInputActive = false
                        }
                    }
                    self.dismissImmediately(allowWarmHandoff: false)
                }
            }
            return event
        }
    }

    fileprivate func anchorEntered(_ view: ThingsTooltipTrackingNSView) {
        guard NSEvent.pressedMouseButtons == 0,
              !view.isSuppressed,
              (!view.requiresMouseOnly || isMouseInputActive) else {
            anchorExited(view)
            return
        }
        pruneDetachedAnchors()
        if !hoveredAnchors.contains(where: { $0.id == view.anchorId }) {
            hoveredAnchors.append(WeakTooltipAnchorEntry(id: view.anchorId, view: view))
        }
        reconcileActiveAnchor()
    }

    fileprivate func anchorMoved(_ view: ThingsTooltipTrackingNSView) {
        guard NSEvent.pressedMouseButtons == 0,
              !view.isSuppressed,
              (!view.requiresMouseOnly || isMouseInputActive) else {
            anchorExited(view)
            return
        }
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
        let buttonsPressed = NSEvent.pressedMouseButtons != 0
        hoveredAnchors.removeAll { entry in
            guard !buttonsPressed,
                  let v = entry.view,
                  !v.isSuppressed,
                  (!v.requiresMouseOnly || isMouseInputActive),
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
        guard NSApp.isActive, NSEvent.pressedMouseButtons == 0, !hoveredAnchors.isEmpty else { return nil }

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
                guard NSEvent.pressedMouseButtons == 0,
                      let anchorView,
                      !anchorView.isSuppressed,
                      (!anchorView.requiresMouseOnly || self.isMouseInputActive),
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
            guard NSEvent.pressedMouseButtons == 0,
                  let anchorView,
                  !anchorView.isSuppressed,
                  (!anchorView.requiresMouseOnly || isMouseInputActive),
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
        if !NSApp.isActive || NSEvent.pressedMouseButtons != 0 {
            dismissImmediately(allowWarmHandoff: false)
            return
        }
        reconcileActiveAnchor()
    }

    private static func nowMs() -> Int64 {
        Int64(ProcessInfo.processInfo.systemUptime * 1000.0)
    }
}

// MARK: - Instantaneous (0 ms) Task Row Click + Native Double-Click Open

private struct ThingsInstantTaskTapModifier: ViewModifier {
    let isEnabled: Bool
    let onSingleClick: (NSEvent.ModifierFlags) -> Void
    let onDoubleClick: () -> Void

    @State private var lastPlainClickUptime: TimeInterval = 0

    func body(content: Content) -> some View {
        if isEnabled {
            content.onTapGesture {
                let modifiers = NSEvent.modifierFlags.intersection([.shift, .command, .control, .option])
                if !modifiers.isEmpty {
                    lastPlainClickUptime = 0
                    onSingleClick(modifiers)
                    return
                }
                let now = ProcessInfo.processInfo.systemUptime
                let threshold = max(0.18, min(0.65, NSEvent.doubleClickInterval))
                let appKitClickCount = NSApp.currentEvent?.clickCount ?? 1
                if appKitClickCount >= 2 || (lastPlainClickUptime > 0 && (now - lastPlainClickUptime) <= threshold) {
                    lastPlainClickUptime = 0
                    onDoubleClick()
                } else {
                    lastPlainClickUptime = now
                    onSingleClick(modifiers)
                }
            }
        } else {
            content
        }
    }
}

public extension View {
    /// Fires `onSingleClick` immediately (0 ms, without waiting for `TapGesture(count: 2)` timeout)
    /// and invokes `onDoubleClick` when a second plain click arrives within `NSEvent.doubleClickInterval`.
    func thingsInstantTaskTap(
        isEnabled: Bool = true,
        onSingleClick: @escaping (NSEvent.ModifierFlags) -> Void,
        onDoubleClick: @escaping () -> Void
    ) -> some View {
        modifier(
            ThingsInstantTaskTapModifier(
                isEnabled: isEnabled,
                onSingleClick: onSingleClick,
                onDoubleClick: onDoubleClick
            )
        )
    }
}

// MARK: - Custom Duration Popover (1 Day + Multi-Day / Hours / Minutes / Shorthand)

public struct CustomDurationPopoverView: View {
    @ObservedObject var store: ThingsbookStore
    public let currentDurationMinutes: Int?
    public let onApply: (Int?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var days: Int = 0
    @State private var hours: Int = 0
    @State private var minutes: Int = 30
    @State private var shorthandText: String = ""

    public init(
        store: ThingsbookStore,
        currentDurationMinutes: Int?,
        onApply: @escaping (Int?) -> Void
    ) {
        self.store = store
        self.currentDurationMinutes = currentDurationMinutes
        self.onApply = onApply
    }

    private var hasInvalidShorthand: Bool {
        !shorthandText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && TaskItem.parseCustomDurationMinutes(shorthandText) == nil
    }

    private var computedTotalMinutes: Int {
        if let parsed = TaskItem.parseCustomDurationMinutes(shorthandText) {
            return parsed
        }
        return min(48_000, max(0, days * 480 + hours * 60 + minutes))
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(store.tr("v30_duration_custom_title"), systemImage: "timer")
                    .font(.system(size: 13, weight: .bold))
                Spacer()
                if !hasInvalidShorthand && computedTotalMinutes > 0 {
                    Text(TaskItem.formatCompactDuration(computedTotalMinutes))
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(ThingsMacPalette.anytimeTeal)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(ThingsMacPalette.anytimeTeal.opacity(0.13)))
                }
            }

            ThingsFlowLayout(horizontalSpacing: 5, verticalSpacing: 5) {
                ForEach([15, 30, 45, 60, 90, 120, 240, 480, 960, 1440, 2400], id: \.self) { preset in
                    let isSelected = computedTotalMinutes == preset && shorthandText.isEmpty
                    Button {
                        shorthandText = ""
                        syncFromMinutes(preset)
                        onApply(preset)
                        dismiss()
                    } label: {
                        Text(TaskItem.formatCompactDuration(preset))
                            .font(.system(size: 11.5, weight: isSelected ? .bold : .medium, design: .rounded))
                            .foregroundStyle(isSelected ? ThingsMacPalette.anytimeTeal : Color.primary.opacity(0.86))
                            .padding(.horizontal, 8.5)
                            .padding(.vertical, 4)
                            .background(
                                RoundedRectangle(cornerRadius: 6.5, style: .continuous)
                                    .fill(isSelected ? ThingsMacPalette.anytimeTeal.opacity(0.14) : ThingsMacPalette.subtleContainer)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 6.5, style: .continuous)
                                            .strokeBorder(
                                                isSelected ? ThingsMacPalette.anytimeTeal.opacity(0.38) : Color.primary.opacity(0.10),
                                                lineWidth: 0.8
                                            )
                                    )
                            )
                    }
                    .buttonStyle(.plain)
                }
            }

            HStack(spacing: 8) {
                stepperUnitBox(label: store.tr("v30_duration_days_short"), value: $days, step: 1, range: 0...100)
                stepperUnitBox(label: store.tr("v30_duration_hours_short"), value: $hours, step: 1, range: 0...23)
                stepperUnitBox(label: store.tr("v30_duration_minutes_short"), value: $minutes, step: 5, range: 0...55)
            }

            TextField(store.tr("v30_duration_custom_placeholder"), text: $shorthandText)
                .textFieldStyle(.roundedBorder)
                .font(.system(size: 12.5))
                .overlay(
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .strokeBorder(
                            hasInvalidShorthand ? ThingsMacPalette.deadlineCrimson : Color.clear,
                            lineWidth: 1.2
                        )
                        .allowsHitTesting(false)
                )
                .onSubmit {
                    if !hasInvalidShorthand && computedTotalMinutes > 0 {
                        onApply(computedTotalMinutes)
                        dismiss()
                    }
                }

            HStack {
                if (currentDurationMinutes ?? 0) > 0 {
                    Button(store.tr("v30_duration_clear"), role: .destructive) {
                        onApply(nil)
                        dismiss()
                    }
                    .controlSize(.small)
                }
                Spacer()
                Button(store.tr("action_done")) {
                    guard !hasInvalidShorthand, computedTotalMinutes > 0 else { return }
                    onApply(computedTotalMinutes)
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
                .disabled(hasInvalidShorthand || computedTotalMinutes <= 0)
            }
        }
        .padding(14)
        .frame(width: 304)
        .onAppear {
            syncFromMinutes(currentDurationMinutes ?? 60)
        }
    }

    private func syncFromMinutes(_ total: Int) {
        let safe = max(0, total)
        days = safe / 480
        let rem = safe % 480
        hours = rem / 60
        minutes = rem % 60
    }

    private func stepperUnitBox(
        label: String,
        value: Binding<Int>,
        step: Int,
        range: ClosedRange<Int>
    ) -> some View {
        VStack(spacing: 4) {
            Text(label)
                .font(.system(size: 10.5, weight: .semibold))
                .foregroundStyle(.secondary)
            HStack(spacing: 5) {
                Button {
                    shorthandText = ""
                    value.wrappedValue = max(range.lowerBound, value.wrappedValue - step)
                } label: {
                    Image(systemName: "minus")
                        .font(.system(size: 9.5, weight: .bold))
                        .frame(width: 20, height: 20)
                        .background(Circle().fill(Color.primary.opacity(0.07)))
                }
                .buttonStyle(.plain)

                Text("\(value.wrappedValue)")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .frame(minWidth: 22)

                Button {
                    shorthandText = ""
                    value.wrappedValue = min(range.upperBound, value.wrappedValue + step)
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 9.5, weight: .bold))
                        .frame(width: 20, height: 20)
                        .background(Circle().fill(Color.primary.opacity(0.07)))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(ThingsMacPalette.subtleContainer)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.75)
                    )
            )
        }
    }
}


