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
        .help(helpText)
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
    public var onTap: (() -> Void)? = nil

    public init(title: String, isSelected: Bool = false, showIcon: Bool = false, onTap: (() -> Void)? = nil) {
        self.title = title
        self.isSelected = isSelected
        self.showIcon = showIcon
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
        }
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
        } else {
            content
        }
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
}
