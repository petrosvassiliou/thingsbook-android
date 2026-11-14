import SwiftUI
import AppKit
import ThingsbookCore

public enum ThingsMacPalette {
    public static let accentBlue = Color(red: 0.13, green: 0.51, blue: 0.96)       // #2182F5
    public static let inboxBlue = Color(red: 0.15, green: 0.56, blue: 0.98)        // #268FFA
    public static let todayYellow = Color(red: 0.98, green: 0.74, blue: 0.09)      // #FABD17
    public static let eveningPurple = Color(red: 0.49, green: 0.42, blue: 0.95)    // #7D6BF2
    public static let upcomingRed = Color(red: 0.95, green: 0.31, blue: 0.38)      // #F24F61
    public static let anytimeTeal = Color(red: 0.16, green: 0.67, blue: 0.65)      // #29ABA6
    public static let somedayAmber = Color(red: 0.82, green: 0.62, blue: 0.25)     // #D19E40
    public static let logbookGreen = Color(red: 0.22, green: 0.75, blue: 0.42)     // #38BF6B
    public static let trashGray = Color(red: 0.56, green: 0.58, blue: 0.63)
    public static let areaGreen = Color(red: 0.20, green: 0.71, blue: 0.47)
    public static let deadlineCrimson = Color(red: 0.91, green: 0.24, blue: 0.30)

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
    public let isRecurring: Bool
    public let onToggleComplete: () -> Void
    public let onCancelTask: () -> Void

    @State private var isHovering = false

    public init(
        status: TaskStatus,
        isSomeday: Bool = false,
        isRecurring: Bool = false,
        onToggleComplete: @escaping () -> Void,
        onCancelTask: @escaping () -> Void
    ) {
        self.status = status
        self.isSomeday = isSomeday
        self.isRecurring = isRecurring
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
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .strokeBorder(
                            isHovering ? ThingsMacPalette.accentBlue : Color.secondary.opacity(0.55),
                            style: StrokeStyle(
                                lineWidth: 1.5,
                                dash: isSomeday ? [2.5, 2.0] : []
                            )
                        )
                        .background(
                            RoundedRectangle(cornerRadius: 5, style: .continuous)
                                .fill(isHovering ? ThingsMacPalette.accentBlue.opacity(0.08) : Color.clear)
                        )
                    if isRecurring {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundStyle(.secondary)
                    }
                case .completed:
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .fill(ThingsMacPalette.accentBlue)
                    Image(systemName: "checkmark")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(.white)
                case .canceled:
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .fill(Color.secondary.opacity(0.32))
                    Image(systemName: "xmark")
                        .font(.system(size: 9.5, weight: .bold))
                        .foregroundStyle(.primary.opacity(0.8))
                }
            }
            .frame(width: 17, height: 17)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.12)) {
                isHovering = hovering
            }
        }
        .help("Click to complete · Option-click to cancel")
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
                    .fill(tint)
                Image(systemName: "checkmark")
                    .font(.system(size: size * 0.52, weight: .bold))
                    .foregroundStyle(.white)
            } else {
                Circle()
                    .strokeBorder(tint.opacity(0.45), lineWidth: 1.4)

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
    public var onTap: (() -> Void)? = nil

    public init(title: String, isSelected: Bool = false, onTap: (() -> Void)? = nil) {
        self.title = title
        self.isSelected = isSelected
        self.onTap = onTap
    }

    public var body: some View {
        let content = Text(title)
            .font(.system(size: 11, weight: .medium))
            .foregroundStyle(isSelected ? Color.white : Color.secondary)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(
                Capsule(style: .continuous)
                    .fill(isSelected ? ThingsMacPalette.accentBlue : Color.primary.opacity(0.06))
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
                    .fill(.regularMaterial)
                    .shadow(color: Color.black.opacity(isSelected ? 0.14 : 0.06), radius: isSelected ? 14 : 6, x: 0, y: isSelected ? 6 : 2)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(
                        isSelected ? ThingsMacPalette.accentBlue.opacity(0.45) : Color.primary.opacity(0.08),
                        lineWidth: isSelected ? 1.2 : 0.8
                    )
            )
    }
}

public extension View {
    func thingsCardSurface(cornerRadius: CGFloat = 11, isSelected: Bool = false) -> some View {
        modifier(GlassSurfaceModifier(cornerRadius: cornerRadius, isSelected: isSelected))
    }
}
