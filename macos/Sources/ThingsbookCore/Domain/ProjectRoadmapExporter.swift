import Foundation
import SwiftUI
import AppKit
import CoreGraphics

public enum ProjectRoadmapExporter {
    @MainActor
    public static func renderViewToPDFData<V: View>(
        _ view: V,
        proposedSize: CGSize? = nil
    ) -> Data? {
        let renderer = ImageRenderer(content: view)
        renderer.isOpaque = true
        if let proposedSize, proposedSize.width > 0, proposedSize.height > 0 {
            renderer.proposedSize = ProposedViewSize(proposedSize)
        }

        let pdfMutableData = NSMutableData()
        guard let consumer = CGDataConsumer(data: pdfMutableData as CFMutableData) else {
            return nil
        }

        var wrotePage = false
        renderer.render { size, renderInContext in
            let safeWidth = max(320.0, ceil(size.width))
            let safeHeight = max(180.0, ceil(size.height))
            var mediaBox = CGRect(x: 0, y: 0, width: safeWidth, height: safeHeight)
            guard let pdfContext = CGContext(consumer: consumer, mediaBox: &mediaBox, nil) else {
                return
            }
            pdfContext.beginPDFPage(nil)
            renderInContext(pdfContext)
            pdfContext.endPDFPage()
            pdfContext.closePDF()
            wrotePage = true
        }

        guard wrotePage, pdfMutableData.length >= 5 else { return nil }
        return pdfMutableData as Data
    }

    @MainActor
    public static func renderViewToPNGData<V: View>(
        _ view: V,
        scale: CGFloat = 2.0,
        proposedSize: CGSize? = nil
    ) -> Data? {
        let renderer = ImageRenderer(content: view)
        renderer.scale = max(1.0, scale)
        renderer.isOpaque = true
        if let proposedSize, proposedSize.width > 0, proposedSize.height > 0 {
            renderer.proposedSize = ProposedViewSize(proposedSize)
        }
        guard let cgImage = renderer.cgImage else {
            return nil
        }
        let bitmapRep = NSBitmapImageRep(cgImage: cgImage)
        bitmapRep.size = NSSize(
            width: CGFloat(cgImage.width) / max(1.0, scale),
            height: CGFloat(cgImage.height) / max(1.0, scale)
        )
        return bitmapRep.representation(using: .png, properties: [:])
    }

    @MainActor
    public static func exportRoadmapPDFData(
        project: Project,
        headings: [Heading],
        layout: ProjectRoadmapLayout,
        generalLabel: String = "General",
        todayLabel: String = "Today",
        locale: Locale = .current,
        dayColumnWidth: CGFloat = 38.0
    ) -> Data? {
        let rawSnapshot = CoreRoadmapSnapshotExportView(
            project: project,
            headings: headings,
            layout: layout,
            generalLabel: generalLabel,
            todayLabel: todayLabel,
            locale: locale,
            dayColumnWidth: dayColumnWidth
        )
        let proposed = rawSnapshot.intrinsicSize
        let snapshot = rawSnapshot.environment(\.colorScheme, .light)

        if let data = renderViewToPDFData(snapshot, proposedSize: proposed),
           data.starts(with: Data("%PDF-".utf8)) {
            return data
        }
        return renderHeadlessCoreGraphicsPDF(
            project: project,
            headings: headings,
            layout: layout,
            generalLabel: generalLabel,
            dayColumnWidth: dayColumnWidth
        )
    }

    @MainActor
    public static func exportRoadmapPNGData(
        project: Project,
        headings: [Heading],
        layout: ProjectRoadmapLayout,
        generalLabel: String = "General",
        todayLabel: String = "Today",
        locale: Locale = .current,
        dayColumnWidth: CGFloat = 38.0,
        scale: CGFloat = 2.0
    ) -> Data? {
        let rawSnapshot = CoreRoadmapSnapshotExportView(
            project: project,
            headings: headings,
            layout: layout,
            generalLabel: generalLabel,
            todayLabel: todayLabel,
            locale: locale,
            dayColumnWidth: dayColumnWidth
        )
        let proposed = rawSnapshot.intrinsicSize
        let snapshot = rawSnapshot.environment(\.colorScheme, .light)

        let pngSignature: [UInt8] = [0x89, 0x50, 0x4E, 0x47]
        if let data = renderViewToPNGData(snapshot, scale: scale, proposedSize: proposed),
           data.starts(with: pngSignature) {
            return data
        }
        return renderHeadlessCoreGraphicsPNG(
            project: project,
            headings: headings,
            layout: layout,
            generalLabel: generalLabel,
            dayColumnWidth: dayColumnWidth,
            scale: scale
        )
    }

    // MARK: - Headless Core Graphics Fallbacks

    public static func renderHeadlessCoreGraphicsPDF(
        project: Project,
        headings: [Heading],
        layout: ProjectRoadmapLayout,
        generalLabel: String = "General",
        dayColumnWidth: CGFloat = 38.0
    ) -> Data? {
        let size = computeCanvasSize(headings: headings, layout: layout, dayColumnWidth: dayColumnWidth)
        let pdfData = NSMutableData()
        guard let consumer = CGDataConsumer(data: pdfData as CFMutableData) else { return nil }
        var mediaBox = CGRect(origin: .zero, size: size)
        guard let ctx = CGContext(consumer: consumer, mediaBox: &mediaBox, nil) else { return nil }
        ctx.beginPDFPage(nil)
        drawHeadlessRoadmap(in: ctx, size: size, project: project, headings: headings, layout: layout, generalLabel: generalLabel, dayColumnWidth: dayColumnWidth)
        ctx.endPDFPage()
        ctx.closePDF()
        return pdfData.length > 0 ? (pdfData as Data) : nil
    }

    public static func renderHeadlessCoreGraphicsPNG(
        project: Project,
        headings: [Heading],
        layout: ProjectRoadmapLayout,
        generalLabel: String = "General",
        dayColumnWidth: CGFloat = 38.0,
        scale: CGFloat = 2.0
    ) -> Data? {
        let safeScale = max(1.0, scale)
        let size = computeCanvasSize(headings: headings, layout: layout, dayColumnWidth: dayColumnWidth)
        let pixelWidth = max(64, Int(ceil(size.width * safeScale)))
        let pixelHeight = max(64, Int(ceil(size.height * safeScale)))
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: nil,
            width: pixelWidth,
            height: pixelHeight,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return nil
        }
        ctx.scaleBy(x: safeScale, y: safeScale)
        drawHeadlessRoadmap(in: ctx, size: size, project: project, headings: headings, layout: layout, generalLabel: generalLabel, dayColumnWidth: dayColumnWidth)
        guard let cgImage = ctx.makeImage() else { return nil }
        let rep = NSBitmapImageRep(cgImage: cgImage)
        rep.size = NSSize(width: size.width, height: size.height)
        return rep.representation(using: .png, properties: [:])
    }

    private static func computeCanvasSize(
        headings: [Heading],
        layout: ProjectRoadmapLayout,
        dayColumnWidth: CGFloat
    ) -> CGSize {
        let rowCount = max(1, headings.count + layout.allTaskBars.count + (layout.generalTaskBars.isEmpty ? 0 : 1))
        let width = 240.0 + CGFloat(max(21, layout.totalDays)) * max(24.0, dayColumnWidth) + 48.0
        let height = 120.0 + CGFloat(rowCount) * 38.0 + 32.0
        return CGSize(width: width, height: height)
    }

    private static func drawHeadlessRoadmap(
        in ctx: CGContext,
        size: CGSize,
        project: Project,
        headings: [Heading],
        layout: ProjectRoadmapLayout,
        generalLabel: String,
        dayColumnWidth: CGFloat
    ) {
        ctx.saveGState()
        ctx.setFillColor(CGColor(srgbRed: 1.0, green: 1.0, blue: 1.0, alpha: 1.0))
        ctx.fill(CGRect(origin: .zero, size: size))

        let leftWidth: CGFloat = 240.0
        let gridOriginX: CGFloat = 24.0 + leftWidth
        let dayW = max(24.0, dayColumnWidth)

        // Vertical day grid lines
        ctx.setStrokeColor(CGColor(srgbRed: 0.88, green: 0.90, blue: 0.94, alpha: 1.0))
        ctx.setLineWidth(0.75)
        for dayIdx in 0...layout.totalDays {
            let x = gridOriginX + CGFloat(dayIdx) * dayW
            ctx.move(to: CGPoint(x: x, y: 24.0))
            ctx.addLine(to: CGPoint(x: x, y: size.height - 64.0))
        }
        ctx.strokePath()

        // Today vertical line
        let todayIdx = layout.todayEpochDay - layout.startEpochDay
        if todayIdx >= 0 && todayIdx < layout.totalDays {
            let tx = gridOriginX + (CGFloat(todayIdx) + 0.5) * dayW
            ctx.setStrokeColor(CGColor(srgbRed: 0.220, green: 0.596, blue: 0.925, alpha: 0.85))
            ctx.setLineWidth(1.6)
            ctx.move(to: CGPoint(x: tx, y: 24.0))
            ctx.addLine(to: CGPoint(x: tx, y: size.height - 64.0))
            ctx.strokePath()
        }

        // Phase & task bars (Core Graphics Y-up coordinates)
        var currentTopY = size.height - 108.0
        var taskRectById: [String: CGRect] = [:]

        for bar in layout.generalTaskBars {
            let x = gridOriginX + CGFloat(bar.startEpochDay - layout.startEpochDay) * dayW + 3.0
            let w = max(dayW - 6.0, CGFloat(bar.spanDays) * dayW - 6.0)
            let rect = CGRect(x: x, y: currentTopY, width: w, height: 24.0)
            taskRectById[bar.id] = rect
            ctx.setFillColor(CGColor(srgbRed: 0.94, green: 0.96, blue: 0.99, alpha: 1.0))
            ctx.fill(rect)
            ctx.setStrokeColor(CGColor(srgbRed: 0.220, green: 0.596, blue: 0.925, alpha: 0.65))
            ctx.setLineWidth(1.0)
            ctx.stroke(rect)
            currentTopY -= 36.0
        }

        let activeHeadings = headings.filter { !$0.isDeleted && !$0.isArchived && $0.projectId == project.id }
        for heading in activeHeadings {
            if let phaseBar = layout.phaseBar(for: heading.id) {
                let x = gridOriginX + CGFloat(phaseBar.startEpochDay - layout.startEpochDay) * dayW + 2.0
                let w = max(dayW - 4.0, CGFloat(phaseBar.spanDays) * dayW - 4.0)
                let rect = CGRect(x: x, y: currentTopY, width: w, height: 26.0)
                ctx.setFillColor(CGColor(srgbRed: 0.455, green: 0.769, blue: 1.000, alpha: 0.85))
                ctx.fill(rect)
            }
            currentTopY -= 36.0

            for bar in (layout.taskBarsByHeading[heading.id] ?? []) {
                let x = gridOriginX + CGFloat(bar.startEpochDay - layout.startEpochDay) * dayW + 3.0
                let w = max(dayW - 6.0, CGFloat(bar.spanDays) * dayW - 6.0)
                let rect = CGRect(x: x, y: currentTopY, width: w, height: 24.0)
                taskRectById[bar.id] = rect
                ctx.setFillColor(CGColor(srgbRed: 0.94, green: 0.96, blue: 0.99, alpha: 1.0))
                ctx.fill(rect)
                ctx.setStrokeColor(CGColor(srgbRed: 0.220, green: 0.596, blue: 0.925, alpha: 0.65))
                ctx.setLineWidth(1.0)
                ctx.stroke(rect)
                currentTopY -= 36.0
            }
        }

        // Dependency curves
        for edge in layout.dependencyEdges {
            guard let fromRect = taskRectById[edge.fromTaskId],
                  let toRect = taskRectById[edge.toTaskId] else { continue }
            let p0 = CGPoint(x: fromRect.maxX, y: fromRect.midY)
            let p3 = CGPoint(x: toRect.minX, y: toRect.midY)
            let dx = max(20.0, abs(p3.x - p0.x) * 0.45)
            ctx.setStrokeColor(
                edge.isActivelyBlocking
                    ? CGColor(srgbRed: 0.84, green: 0.61, blue: 0.22, alpha: 0.92)
                    : CGColor(srgbRed: 0.220, green: 0.596, blue: 0.925, alpha: 0.45)
            )
            ctx.setLineWidth(1.5)
            ctx.move(to: p0)
            ctx.addCurve(
                to: p3,
                control1: CGPoint(x: p0.x + dx, y: p0.y),
                control2: CGPoint(x: p3.x - dx, y: p3.y)
            )
            ctx.strokePath()
        }

        let _ = generalLabel
        ctx.restoreGState()
    }
}

private struct CoreRoadmapSnapshotExportView: View {
    let project: Project
    let headings: [Heading]
    let layout: ProjectRoadmapLayout
    let generalLabel: String
    let todayLabel: String
    let locale: Locale
    let dayColumnWidth: CGFloat

    private let labelColumnWidth: CGFloat = 228
    private let rowHeight: CGFloat = 34
    private let rowSpacing: CGFloat = 4
    private let headerHeight: CGFloat = 46

    private enum SnapshotRow: Identifiable {
        case generalHeader
        case phaseHeader(Heading, RoadmapPhaseBar?)
        case task(RoadmapTaskBar)

        var id: String {
            switch self {
            case .generalHeader: return "gen_hdr"
            case .phaseHeader(let h, _): return "phase_\(h.id)"
            case .task(let b): return "task_\(b.id)"
            }
        }
    }

    private var rows: [SnapshotRow] {
        var out: [SnapshotRow] = []
        if !layout.generalTaskBars.isEmpty {
            out.append(.generalHeader)
            for b in layout.generalTaskBars {
                out.append(.task(b))
            }
        }
        let activeHeadings = headings
            .filter { !$0.isDeleted && !$0.isArchived && $0.projectId == project.id }
            .sorted { $0.sortOrder < $1.sortOrder }
        for h in activeHeadings {
            let phaseBar = layout.phaseBar(for: h.id)
            let children = layout.taskBarsByHeading[h.id] ?? []
            if phaseBar != nil || !children.isEmpty {
                out.append(.phaseHeader(h, phaseBar))
                for b in children {
                    out.append(.task(b))
                }
            }
        }
        return out
    }

    var intrinsicSize: CGSize {
        let count = max(1, rows.count)
        let gridWidth = CGFloat(max(21, layout.totalDays)) * dayColumnWidth
        let bodyHeight = headerHeight + CGFloat(count) * rowHeight + CGFloat(max(0, count - 1)) * rowSpacing + 16
        return CGSize(
            width: labelColumnWidth + gridWidth + 48,
            height: 68 + bodyHeight + 28
        )
    }

    var body: some View {
        let displayRows = rows
        let gridWidth = CGFloat(max(21, layout.totalDays)) * dayColumnWidth
        let skyAccent = Color(red: 0.220, green: 0.596, blue: 0.925)
        let glacierBlue = Color(red: 0.455, green: 0.769, blue: 1.000)
        let amberBlock = Color(red: 0.84, green: 0.61, blue: 0.22)

        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Circle()
                    .fill(skyAccent)
                    .frame(width: 12, height: 12)
                Text(project.title)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.black)
                Spacer()
                Text(formattedWindowLabel)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.gray)
            }

            HStack(alignment: .top, spacing: 0) {
                VStack(alignment: .leading, spacing: rowSpacing) {
                    Color.clear
                        .frame(width: labelColumnWidth, height: headerHeight)

                    ForEach(displayRows) { row in
                        switch row {
                        case .generalHeader:
                            Text(generalLabel)
                                .font(.system(size: 12.5, weight: .bold))
                                .foregroundStyle(.black)
                                .padding(.horizontal, 10)
                                .frame(width: labelColumnWidth, height: rowHeight, alignment: .leading)
                        case .phaseHeader(let h, _):
                            HStack(spacing: 6) {
                                Capsule()
                                    .fill(glacierBlue)
                                    .frame(width: 3.5, height: 15)
                                Text(h.title)
                                    .font(.system(size: 12.5, weight: .bold))
                                    .foregroundStyle(.black)
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 10)
                            .frame(width: labelColumnWidth, height: rowHeight, alignment: .leading)
                        case .task(let bar):
                            Text(bar.task.title)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(.black.opacity(0.86))
                                .lineLimit(1)
                                .padding(.leading, 20)
                                .padding(.trailing, 8)
                                .frame(width: labelColumnWidth, height: rowHeight, alignment: .leading)
                        }
                    }
                }
                .background(Color(white: 0.975))

                ZStack(alignment: .topLeading) {
                    // Vertical grid columns + Today line
                    HStack(spacing: 0) {
                        ForEach(0..<layout.totalDays, id: \.self) { offset in
                            let epochDay = layout.startEpochDay + offset
                            let isToday = epochDay == layout.todayEpochDay
                            VStack(spacing: 0) {
                                VStack(spacing: 1) {
                                    Text(dayMonthShort(for: epochDay))
                                        .font(.system(size: 9, weight: .semibold))
                                        .foregroundStyle(isToday ? skyAccent : .gray)
                                    Text("\(dayOfMonth(for: epochDay))")
                                        .font(.system(size: 11, weight: isToday ? .bold : .medium, design: .rounded))
                                        .foregroundStyle(isToday ? skyAccent : .black.opacity(0.78))
                                }
                                .frame(width: dayColumnWidth, height: headerHeight)

                                Rectangle()
                                    .fill(isToday ? skyAccent.opacity(0.08) : Color.clear)
                                    .frame(width: dayColumnWidth)
                                    .overlay(alignment: .leading) {
                                        Rectangle()
                                            .fill(Color.black.opacity(0.06))
                                            .frame(width: 0.75)
                                    }
                                    .overlay {
                                        if isToday {
                                            Rectangle()
                                                .fill(skyAccent.opacity(0.65))
                                                .frame(width: 1.5)
                                        }
                                    }
                            }
                        }
                    }

                    // Bars layer
                    VStack(alignment: .leading, spacing: rowSpacing) {
                        Color.clear.frame(width: gridWidth, height: headerHeight)

                        ForEach(displayRows) { row in
                            ZStack(alignment: .leading) {
                                Color.clear.frame(width: gridWidth, height: rowHeight)
                                switch row {
                                case .generalHeader:
                                    EmptyView()
                                case .phaseHeader(_, let maybeBar):
                                    if let pBar = maybeBar {
                                        let xOff = CGFloat(pBar.startEpochDay - layout.startEpochDay) * dayColumnWidth + 2
                                        let barW = max(dayColumnWidth - 4, CGFloat(pBar.spanDays) * dayColumnWidth - 4)
                                        HStack(spacing: 5) {
                                            Text(pBar.heading.title)
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundStyle(.white)
                                                .lineLimit(1)
                                            Spacer(minLength: 0)
                                        }
                                        .padding(.horizontal, 8)
                                        .frame(width: barW, height: 24)
                                        .background(
                                            Capsule(style: .continuous)
                                                .fill(
                                                    LinearGradient(
                                                        colors: [glacierBlue, skyAccent],
                                                        startPoint: .leading,
                                                        endPoint: .trailing
                                                    )
                                                )
                                        )
                                        .offset(x: xOff)
                                    }
                                case .task(let tBar):
                                    let xOff = CGFloat(tBar.startEpochDay - layout.startEpochDay) * dayColumnWidth + 3
                                    let barW = max(dayColumnWidth - 6, CGFloat(tBar.spanDays) * dayColumnWidth - 6)
                                    HStack(spacing: 5) {
                                        Circle()
                                            .strokeBorder(skyAccent, lineWidth: 1.4)
                                            .frame(width: 10, height: 10)
                                        Text(tBar.task.title)
                                            .font(.system(size: 11, weight: .semibold))
                                            .foregroundStyle(.black.opacity(0.88))
                                            .lineLimit(1)
                                        Spacer(minLength: 0)
                                    }
                                    .padding(.horizontal, 7)
                                    .frame(width: barW, height: 24)
                                    .background(
                                        RoundedRectangle(cornerRadius: 7, style: .continuous)
                                            .fill(Color.white)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 7, style: .continuous)
                                                    .strokeBorder(skyAccent.opacity(0.48), lineWidth: 1.0)
                                            )
                                    )
                                    .offset(x: xOff)
                                }
                            }
                        }
                    }

                    // Dependency edges Canvas overlay
                    Canvas { context, _ in
                        let rowYByTaskId: [String: CGFloat] = {
                            var map: [String: CGFloat] = [:]
                            for (idx, r) in displayRows.enumerated() {
                                if case .task(let tb) = r {
                                    let y = headerHeight + CGFloat(idx) * (rowHeight + rowSpacing) + rowHeight * 0.5
                                    map[tb.id] = y
                                }
                            }
                            return map
                        }()
                        let barById = Dictionary(uniqueKeysWithValues: layout.allTaskBars.map { ($0.id, $0) })

                        for edge in layout.dependencyEdges {
                            guard let fromBar = barById[edge.fromTaskId],
                                  let toBar = barById[edge.toTaskId],
                                  let y0 = rowYByTaskId[edge.fromTaskId],
                                  let y1 = rowYByTaskId[edge.toTaskId] else {
                                continue
                            }
                            let x0 = CGFloat(fromBar.endEpochDay - layout.startEpochDay + 1) * dayColumnWidth - 3
                            let x1 = CGFloat(toBar.startEpochDay - layout.startEpochDay) * dayColumnWidth + 3
                            let p0 = CGPoint(x: x0, y: y0)
                            let p3 = CGPoint(x: x1, y: y1)
                            let dx = max(22.0, abs(p3.x - p0.x) * 0.5)

                            var path = Path()
                            path.move(to: p0)
                            path.addCurve(
                                to: p3,
                                control1: CGPoint(x: p0.x + dx, y: p0.y),
                                control2: CGPoint(x: p3.x - dx, y: p3.y)
                            )
                            let strokeColor = edge.isActivelyBlocking ? amberBlock : skyAccent.opacity(0.45)
                            context.stroke(path, with: .color(strokeColor), lineWidth: 1.6)

                            var arrow = Path()
                            arrow.move(to: CGPoint(x: p3.x - 6, y: p3.y - 3.5))
                            arrow.addLine(to: p3)
                            arrow.addLine(to: CGPoint(x: p3.x - 6, y: p3.y + 3.5))
                            context.stroke(arrow, with: .color(strokeColor), lineWidth: 1.6)
                        }
                    }
                    .allowsHitTesting(false)
                }
                .frame(width: gridWidth)
            }
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(Color.black.opacity(0.09), lineWidth: 0.9)
            )
        }
        .padding(24)
        .background(Color.white)
    }

    private var formattedWindowLabel: String {
        let df = DateFormatter()
        df.locale = locale
        df.dateStyle = .medium
        let start = DateEpochConverter.date(fromEpochDay: layout.startEpochDay)
        let end = DateEpochConverter.date(fromEpochDay: layout.endEpochDay)
        let _ = todayLabel
        return "\(df.string(from: start)) – \(df.string(from: end))"
    }

    private func dayOfMonth(for epochDay: Int) -> Int {
        let d = DateEpochConverter.date(fromEpochDay: epochDay)
        return Calendar.current.component(.day, from: d)
    }

    private func dayMonthShort(for epochDay: Int) -> String {
        let d = DateEpochConverter.date(fromEpochDay: epochDay)
        let dom = Calendar.current.component(.day, from: d)
        let df = DateFormatter()
        df.locale = locale
        if dom == 1 {
            df.setLocalizedDateFormatFromTemplate("MMM")
            return df.string(from: d)
        }
        df.setLocalizedDateFormatFromTemplate("EEEEE")
        return df.string(from: d)
    }
}
