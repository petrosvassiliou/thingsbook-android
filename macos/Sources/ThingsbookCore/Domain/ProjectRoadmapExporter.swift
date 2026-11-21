import Foundation
import SwiftUI
import AppKit
import CoreGraphics

fileprivate enum RoadmapSnapshotRow: Identifiable {
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
        options: RoadmapPDFExportOptions? = nil,
        generalLabel: String = "General",
        todayLabel: String = "Today",
        locale: Locale = .current,
        dayColumnWidth: CGFloat = 38.0
    ) -> Data? {
        let _ = dayColumnWidth
        let resolvedOptions = options ?? RoadmapPDFExportOptions.defaultOptions(
            for: layout,
            headings: headings,
            orientation: .landscape
        )
        let effectiveLayout = options != nil
            ? ProjectRoadmapEngine.filteredLayoutForExport(layout: layout, headings: headings, options: resolvedOptions)
            : layout

        let effectiveHeadings = headings
            .filter {
                !$0.isDeleted
                    && !$0.isArchived
                    && $0.projectId == project.id
                    && resolvedOptions.selectedSectionIds.contains($0.id)
            }
            .sorted {
                if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                return $0.id < $1.id
            }

        let allRows = buildSnapshotRows(
            headings: effectiveHeadings,
            layout: effectiveLayout,
            includeEmptySelectedPhases: options != nil
        )

        let pageSize = resolvedOptions.orientation.pageSize
        let pageMargin: CGFloat = 24.0
        let topBannerHeight: CGFloat = 32.0
        let headerHeight: CGFloat = 42.0
        let phaseRowHeight: CGFloat = 46.0
        let taskRowHeight: CGFloat = 30.0
        let rowSpacing: CGFloat = 4.0
        let availableRowsHeight = max(
            120.0,
            pageSize.height - (pageMargin * 2.0) - topBannerHeight - 10.0 - headerHeight - 16.0
        )

        let pages = paginateRows(
            allRows,
            availableHeight: availableRowsHeight,
            phaseRowHeight: phaseRowHeight,
            taskRowHeight: taskRowHeight,
            rowSpacing: rowSpacing
        )

        let pdfMutableData = NSMutableData()
        guard let consumer = CGDataConsumer(data: pdfMutableData as CFMutableData) else {
            return nil
        }
        var mediaBox = CGRect(origin: .zero, size: pageSize)
        guard let pdfContext = CGContext(consumer: consumer, mediaBox: &mediaBox, nil) else {
            return nil
        }

        var wroteAnyPage = false
        for (pageIndex, pageRows) in pages.enumerated() {
            let pageView = CoreRoadmapPagedPDFPageView(
                project: project,
                layout: effectiveLayout,
                pageRows: pageRows,
                pageIndex: pageIndex,
                totalPages: pages.count,
                orientation: resolvedOptions.orientation,
                generalLabel: generalLabel,
                todayLabel: todayLabel,
                locale: locale,
                pageMargin: pageMargin,
                topBannerHeight: topBannerHeight,
                headerHeight: headerHeight,
                phaseRowHeight: phaseRowHeight,
                taskRowHeight: taskRowHeight,
                rowSpacing: rowSpacing
            )
            .environment(\.colorScheme, .light)

            let renderer = ImageRenderer(content: pageView)
            renderer.isOpaque = true
            renderer.proposedSize = ProposedViewSize(pageSize)

            var renderedViaSwiftUI = false
            renderer.render { _, renderInContext in
                pdfContext.beginPDFPage(nil)
                renderInContext(pdfContext)
                pdfContext.endPDFPage()
                renderedViaSwiftUI = true
            }

            if !renderedViaSwiftUI {
                pdfContext.beginPDFPage(nil)
                drawHeadlessPagedRoadmap(
                    in: pdfContext,
                    pageSize: pageSize,
                    orientation: resolvedOptions.orientation,
                    project: project,
                    pageRows: pageRows,
                    layout: effectiveLayout,
                    generalLabel: generalLabel,
                    pageMargin: pageMargin,
                    headerHeight: headerHeight,
                    phaseRowHeight: phaseRowHeight,
                    taskRowHeight: taskRowHeight,
                    rowSpacing: rowSpacing
                )
                pdfContext.endPDFPage()
            }
            wroteAnyPage = true
        }

        pdfContext.closePDF()
        guard wroteAnyPage, pdfMutableData.length >= 5 else { return nil }
        let data = pdfMutableData as Data
        return data.starts(with: Data("%PDF-".utf8)) ? data : nil
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

    // MARK: - Pagination & Row Construction

    fileprivate static func buildSnapshotRows(
        headings: [Heading],
        layout: ProjectRoadmapLayout,
        includeEmptySelectedPhases: Bool
    ) -> [RoadmapSnapshotRow] {
        var out: [RoadmapSnapshotRow] = []
        if !layout.generalTaskBars.isEmpty {
            out.append(.generalHeader)
            for b in layout.generalTaskBars {
                out.append(.task(b))
            }
        }
        let activeHeadings = headings
            .filter { !$0.isDeleted && !$0.isArchived }
            .sorted {
                if $0.sortOrder != $1.sortOrder { return $0.sortOrder < $1.sortOrder }
                return $0.id < $1.id
            }
        for h in activeHeadings {
            let phaseBar = layout.phaseBar(for: h.id)
            let children = layout.taskBarsByHeading[h.id] ?? []
            if includeEmptySelectedPhases || phaseBar != nil || !children.isEmpty {
                out.append(.phaseHeader(h, phaseBar))
                for b in children {
                    out.append(.task(b))
                }
            }
        }
        return out
    }

    fileprivate static func paginateRows(
        _ rows: [RoadmapSnapshotRow],
        availableHeight: CGFloat,
        phaseRowHeight: CGFloat,
        taskRowHeight: CGFloat,
        rowSpacing: CGFloat
    ) -> [[RoadmapSnapshotRow]] {
        guard !rows.isEmpty else { return [[]] }

        let heightForRow: (RoadmapSnapshotRow) -> CGFloat = { r in
            if case .phaseHeader = r { return phaseRowHeight }
            return taskRowHeight
        }

        var pages: [[RoadmapSnapshotRow]] = []
        var currentPage: [RoadmapSnapshotRow] = []
        var usedHeight: CGFloat = 0

        for idx in rows.indices {
            let row = rows[idx]
            let rowH = heightForRow(row)
            let needed = currentPage.isEmpty ? rowH : (rowSpacing + rowH)

            // Prevent stranding a phase header at the bottom of a page without its first child task
            var needsNextTaskRoom: CGFloat = 0
            if case .phaseHeader = row, idx + 1 < rows.count, case .task = rows[idx + 1] {
                needsNextTaskRoom = rowSpacing + taskRowHeight
            }

            if !currentPage.isEmpty && (usedHeight + needed + needsNextTaskRoom > availableHeight) {
                pages.append(currentPage)
                currentPage = [row]
                usedHeight = rowH
            } else {
                currentPage.append(row)
                usedHeight += needed
            }
        }

        if !currentPage.isEmpty {
            pages.append(currentPage)
        }
        return pages.isEmpty ? [[]] : pages
    }

    // MARK: - Headless Core Graphics Fallbacks

    public static func renderHeadlessCoreGraphicsPDF(
        project: Project,
        headings: [Heading],
        layout: ProjectRoadmapLayout,
        orientation: RoadmapPDFOrientation = .landscape,
        generalLabel: String = "General",
        dayColumnWidth: CGFloat = 38.0
    ) -> Data? {
        let _ = dayColumnWidth
        let pageSize = orientation.pageSize
        let rows = buildSnapshotRows(headings: headings, layout: layout, includeEmptySelectedPhases: false)
        let pages = paginateRows(rows, availableHeight: pageSize.height - 150.0, phaseRowHeight: 46.0, taskRowHeight: 30.0, rowSpacing: 4.0)
        let pdfData = NSMutableData()
        guard let consumer = CGDataConsumer(data: pdfData as CFMutableData) else { return nil }
        var mediaBox = CGRect(origin: .zero, size: pageSize)
        guard let ctx = CGContext(consumer: consumer, mediaBox: &mediaBox, nil) else { return nil }
        for pageRows in pages {
            ctx.beginPDFPage(nil)
            drawHeadlessPagedRoadmap(
                in: ctx,
                pageSize: pageSize,
                orientation: orientation,
                project: project,
                pageRows: pageRows,
                layout: layout,
                generalLabel: generalLabel,
                pageMargin: 24.0,
                headerHeight: 42.0,
                phaseRowHeight: 46.0,
                taskRowHeight: 30.0,
                rowSpacing: 4.0
            )
            ctx.endPDFPage()
        }
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
        let activeHeadings = headings.filter { !$0.isDeleted && !$0.isArchived }
        let phaseCount = activeHeadings.count
        let otherCount = layout.allTaskBars.count + (layout.generalTaskBars.isEmpty ? 0 : 1)
        let width = 272.0 + CGFloat(max(21, layout.totalDays)) * max(24.0, dayColumnWidth) + 48.0
        let height = 120.0 + CGFloat(phaseCount) * 52.0 + CGFloat(otherCount) * 38.0 + 36.0
        return CGSize(width: width, height: height)
    }

    private static func drawHeadlessPagedRoadmap(
        in ctx: CGContext,
        pageSize: CGSize,
        orientation: RoadmapPDFOrientation,
        project: Project,
        pageRows: [RoadmapSnapshotRow],
        layout: ProjectRoadmapLayout,
        generalLabel: String,
        pageMargin: CGFloat,
        headerHeight: CGFloat,
        phaseRowHeight: CGFloat,
        taskRowHeight: CGFloat,
        rowSpacing: CGFloat
    ) {
        ctx.saveGState()
        ctx.setFillColor(CGColor(srgbRed: 1.0, green: 1.0, blue: 1.0, alpha: 1.0))
        ctx.fill(CGRect(origin: .zero, size: pageSize))

        let contentWidth = pageSize.width - pageMargin * 2.0
        let leftWidth: CGFloat = orientation == .landscape ? 216.0 : 188.0
        let timelineWidth = max(120.0, contentWidth - leftWidth)
        let gridOriginX = pageMargin + leftWidth
        let totalDays = max(1, layout.totalDays)
        let dayW = timelineWidth / CGFloat(totalDays)

        let gridTopY = pageSize.height - pageMargin - 42.0 - headerHeight
        let gridBottomY = pageMargin + 12.0

        // Vertical grid lines (adaptive stride when narrow)
        let lineStep = dayW >= 8.0 ? 1 : (dayW >= 3.5 ? 7 : 14)
        ctx.setStrokeColor(CGColor(srgbRed: 0.88, green: 0.90, blue: 0.94, alpha: 1.0))
        ctx.setLineWidth(0.65)
        var dayIdx = 0
        while dayIdx <= totalDays {
            let x = gridOriginX + CGFloat(dayIdx) * dayW
            ctx.move(to: CGPoint(x: x, y: gridBottomY))
            ctx.addLine(to: CGPoint(x: x, y: gridTopY))
            dayIdx += lineStep
        }
        ctx.strokePath()

        // Today vertical line
        let todayIdx = layout.todayEpochDay - layout.startEpochDay
        if todayIdx >= 0 && todayIdx < totalDays {
            let tx = gridOriginX + (CGFloat(todayIdx) + 0.5) * dayW
            ctx.setStrokeColor(CGColor(srgbRed: 0.220, green: 0.596, blue: 0.925, alpha: 0.85))
            ctx.setLineWidth(1.4)
            ctx.move(to: CGPoint(x: tx, y: gridBottomY))
            ctx.addLine(to: CGPoint(x: tx, y: gridTopY))
            ctx.strokePath()
        }

        var cursorTopY = gridTopY - rowSpacing
        var taskRectById: [String: CGRect] = [:]

        for row in pageRows {
            switch row {
            case .generalHeader:
                cursorTopY -= (taskRowHeight + rowSpacing)
            case .phaseHeader(_, let maybeBar):
                let rowMidY = cursorTopY - phaseRowHeight * 0.5
                if let phaseBar = maybeBar,
                   phaseBar.endEpochDay >= layout.startEpochDay,
                   phaseBar.startEpochDay <= layout.endEpochDay {
                    let cStart = max(layout.startEpochDay, phaseBar.startEpochDay)
                    let cEnd = min(layout.endEpochDay, phaseBar.endEpochDay)
                    let cSpan = max(1, cEnd - cStart + 1)
                    let x = gridOriginX + CGFloat(cStart - layout.startEpochDay) * dayW + 1.0
                    let w = max(3.0, CGFloat(cSpan) * dayW - 2.0)
                    let rect = CGRect(x: x, y: rowMidY - 11.0, width: w, height: 22.0)
                    ctx.setFillColor(CGColor(srgbRed: 0.455, green: 0.769, blue: 1.000, alpha: 0.88))
                    ctx.fill(rect)
                }
                cursorTopY -= (phaseRowHeight + rowSpacing)
            case .task(let bar):
                let rowMidY = cursorTopY - taskRowHeight * 0.5
                if bar.endEpochDay >= layout.startEpochDay && bar.startEpochDay <= layout.endEpochDay {
                    let cStart = max(layout.startEpochDay, bar.startEpochDay)
                    let cEnd = min(layout.endEpochDay, bar.endEpochDay)
                    if bar.isMilestone {
                        let centerX = gridOriginX + (CGFloat(cStart - layout.startEpochDay) + 0.5) * dayW
                        let radius: CGFloat = 7.0
                        taskRectById[bar.id] = CGRect(x: centerX - radius, y: rowMidY - radius, width: radius * 2.0, height: radius * 2.0)
                        drawMilestoneDiamondAndLabel(
                            in: ctx,
                            center: CGPoint(x: centerX, y: rowMidY),
                            radius: radius,
                            title: bar.task.title,
                            fontSize: 9.5
                        )
                    } else {
                        let cSpan = max(1, cEnd - cStart + 1)
                        let x = gridOriginX + CGFloat(cStart - layout.startEpochDay) * dayW + 1.5
                        let w = max(3.0, CGFloat(cSpan) * dayW - 3.0)
                        let rect = CGRect(x: x, y: rowMidY - 10.0, width: w, height: 20.0)
                        taskRectById[bar.id] = rect
                        ctx.setFillColor(CGColor(srgbRed: 0.94, green: 0.96, blue: 0.99, alpha: 1.0))
                        ctx.fill(rect)
                        ctx.setStrokeColor(CGColor(srgbRed: 0.220, green: 0.596, blue: 0.925, alpha: 0.65))
                        ctx.setLineWidth(0.9)
                        ctx.stroke(rect)
                    }
                }
                cursorTopY -= (taskRowHeight + rowSpacing)
            }
        }

        for edge in layout.dependencyEdges {
            guard let fromRect = taskRectById[edge.fromTaskId],
                  let toRect = taskRectById[edge.toTaskId] else { continue }
            let p0 = CGPoint(x: fromRect.maxX, y: fromRect.midY)
            let p3 = CGPoint(x: toRect.minX, y: toRect.midY)
            let dx = max(14.0, abs(p3.x - p0.x) * 0.45)
            ctx.setStrokeColor(
                edge.isActivelyBlocking
                    ? CGColor(srgbRed: 0.84, green: 0.61, blue: 0.22, alpha: 0.92)
                    : CGColor(srgbRed: 0.220, green: 0.596, blue: 0.925, alpha: 0.45)
            )
            ctx.setLineWidth(1.3)
            ctx.move(to: p0)
            ctx.addCurve(
                to: p3,
                control1: CGPoint(x: p0.x + dx, y: p0.y),
                control2: CGPoint(x: p3.x - dx, y: p3.y)
            )
            ctx.strokePath()
        }

        let _ = project
        let _ = generalLabel
        ctx.restoreGState()
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

        let leftWidth: CGFloat = 272.0
        let gridOriginX: CGFloat = 24.0 + leftWidth
        let dayW = max(24.0, dayColumnWidth)

        ctx.setStrokeColor(CGColor(srgbRed: 0.88, green: 0.90, blue: 0.94, alpha: 1.0))
        ctx.setLineWidth(0.75)
        for dayIdx in 0...layout.totalDays {
            let x = gridOriginX + CGFloat(dayIdx) * dayW
            ctx.move(to: CGPoint(x: x, y: 24.0))
            ctx.addLine(to: CGPoint(x: x, y: size.height - 64.0))
        }
        ctx.strokePath()

        let todayIdx = layout.todayEpochDay - layout.startEpochDay
        if todayIdx >= 0 && todayIdx < layout.totalDays {
            let tx = gridOriginX + (CGFloat(todayIdx) + 0.5) * dayW
            ctx.setStrokeColor(CGColor(srgbRed: 0.220, green: 0.596, blue: 0.925, alpha: 0.85))
            ctx.setLineWidth(1.6)
            ctx.move(to: CGPoint(x: tx, y: 24.0))
            ctx.addLine(to: CGPoint(x: tx, y: size.height - 64.0))
            ctx.strokePath()
        }

        var currentTopY = size.height - 108.0
        var taskRectById: [String: CGRect] = [:]

        for bar in layout.generalTaskBars where bar.endEpochDay >= layout.startEpochDay && bar.startEpochDay <= layout.endEpochDay {
            let cStart = max(layout.startEpochDay, bar.startEpochDay)
            let cEnd = min(layout.endEpochDay, bar.endEpochDay)
            if bar.isMilestone {
                let centerX = gridOriginX + (CGFloat(cStart - layout.startEpochDay) + 0.5) * dayW
                let midY = currentTopY + 12.0
                let radius: CGFloat = 8.5
                taskRectById[bar.id] = CGRect(x: centerX - radius, y: midY - radius, width: radius * 2.0, height: radius * 2.0)
                drawMilestoneDiamondAndLabel(
                    in: ctx,
                    center: CGPoint(x: centerX, y: midY),
                    radius: radius,
                    title: bar.task.title,
                    fontSize: 11.0
                )
            } else {
                let cSpan = max(1, cEnd - cStart + 1)
                let x = gridOriginX + CGFloat(cStart - layout.startEpochDay) * dayW + 3.0
                let w = max(dayW - 6.0, CGFloat(cSpan) * dayW - 6.0)
                let rect = CGRect(x: x, y: currentTopY, width: w, height: 24.0)
                taskRectById[bar.id] = rect
                ctx.setFillColor(CGColor(srgbRed: 0.94, green: 0.96, blue: 0.99, alpha: 1.0))
                ctx.fill(rect)
                ctx.setStrokeColor(CGColor(srgbRed: 0.220, green: 0.596, blue: 0.925, alpha: 0.65))
                ctx.setLineWidth(1.0)
                ctx.stroke(rect)
            }
            currentTopY -= 36.0
        }

        let activeHeadings = headings.filter { !$0.isDeleted && !$0.isArchived && $0.projectId == project.id }
        for heading in activeHeadings {
            if let phaseBar = layout.phaseBar(for: heading.id),
               phaseBar.endEpochDay >= layout.startEpochDay,
               phaseBar.startEpochDay <= layout.endEpochDay {
                let cStart = max(layout.startEpochDay, phaseBar.startEpochDay)
                let cEnd = min(layout.endEpochDay, phaseBar.endEpochDay)
                let cSpan = max(1, cEnd - cStart + 1)
                let x = gridOriginX + CGFloat(cStart - layout.startEpochDay) * dayW + 2.0
                let w = max(dayW - 4.0, CGFloat(cSpan) * dayW - 4.0)
                let rect = CGRect(x: x, y: currentTopY, width: w, height: 26.0)
                ctx.setFillColor(CGColor(srgbRed: 0.455, green: 0.769, blue: 1.000, alpha: 0.85))
                ctx.fill(rect)
            }
            currentTopY -= 52.0

            for bar in (layout.taskBarsByHeading[heading.id] ?? []) where bar.endEpochDay >= layout.startEpochDay && bar.startEpochDay <= layout.endEpochDay {
                let cStart = max(layout.startEpochDay, bar.startEpochDay)
                let cEnd = min(layout.endEpochDay, bar.endEpochDay)
                if bar.isMilestone {
                    let centerX = gridOriginX + (CGFloat(cStart - layout.startEpochDay) + 0.5) * dayW
                    let midY = currentTopY + 12.0
                    let radius: CGFloat = 8.5
                    taskRectById[bar.id] = CGRect(x: centerX - radius, y: midY - radius, width: radius * 2.0, height: radius * 2.0)
                    drawMilestoneDiamondAndLabel(
                        in: ctx,
                        center: CGPoint(x: centerX, y: midY),
                        radius: radius,
                        title: bar.task.title,
                        fontSize: 11.0
                    )
                } else {
                    let cSpan = max(1, cEnd - cStart + 1)
                    let x = gridOriginX + CGFloat(cStart - layout.startEpochDay) * dayW + 3.0
                    let w = max(dayW - 6.0, CGFloat(cSpan) * dayW - 6.0)
                    let rect = CGRect(x: x, y: currentTopY, width: w, height: 24.0)
                    taskRectById[bar.id] = rect
                    ctx.setFillColor(CGColor(srgbRed: 0.94, green: 0.96, blue: 0.99, alpha: 1.0))
                    ctx.fill(rect)
                    ctx.setStrokeColor(CGColor(srgbRed: 0.220, green: 0.596, blue: 0.925, alpha: 0.65))
                    ctx.setLineWidth(1.0)
                    ctx.stroke(rect)
                }
                currentTopY -= 36.0
            }
        }

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

    fileprivate static let milestoneRoyalVioletNSColor = NSColor(calibratedRed: 0.49, green: 0.23, blue: 0.93, alpha: 1.0)
    fileprivate static let milestoneRoyalVioletColor = Color(nsColor: NSColor(calibratedRed: 0.49, green: 0.23, blue: 0.93, alpha: 1.0))

    fileprivate static func makeDiamondCGPath(center: CGPoint, radius: CGFloat) -> CGPath {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: center.x, y: center.y - radius))
        path.addLine(to: CGPoint(x: center.x + radius, y: center.y))
        path.addLine(to: CGPoint(x: center.x, y: center.y + radius))
        path.addLine(to: CGPoint(x: center.x - radius, y: center.y))
        path.closeSubpath()
        return path
    }

    private static func drawMilestoneDiamondAndLabel(
        in ctx: CGContext,
        center: CGPoint,
        radius: CGFloat,
        title: String,
        fontSize: CGFloat
    ) {
        let diamond = makeDiamondCGPath(center: center, radius: radius)
        ctx.saveGState()
        ctx.addPath(diamond)
        ctx.setFillColor(milestoneRoyalVioletNSColor.cgColor)
        ctx.fillPath()
        ctx.addPath(diamond)
        ctx.setStrokeColor(CGColor(srgbRed: 1.0, green: 1.0, blue: 1.0, alpha: 0.92))
        ctx.setLineWidth(1.1)
        ctx.strokePath()
        ctx.restoreGState()

        let labelString = "◆ \(title)" as NSString
        let attrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.boldSystemFont(ofSize: fontSize),
            .foregroundColor: milestoneRoyalVioletNSColor
        ]
        let prevCtx = NSGraphicsContext.current
        NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: false)
        let textSize = labelString.size(withAttributes: attrs)
        let labelOrigin = CGPoint(
            x: center.x + radius + 5.0,
            y: center.y - textSize.height * 0.5
        )
        labelString.draw(at: labelOrigin, withAttributes: attrs)
        NSGraphicsContext.current = prevCtx
    }
}

// MARK: - True A4 Paginated Vector PDF Page View (842×595 Landscape / 595×842 Portrait)

private struct CoreRoadmapPagedPDFPageView: View {
    let project: Project
    let layout: ProjectRoadmapLayout
    let pageRows: [RoadmapSnapshotRow]
    let pageIndex: Int
    let totalPages: Int
    let orientation: RoadmapPDFOrientation
    let generalLabel: String
    let todayLabel: String
    let locale: Locale
    let pageMargin: CGFloat
    let topBannerHeight: CGFloat
    let headerHeight: CGFloat
    let phaseRowHeight: CGFloat
    let taskRowHeight: CGFloat
    let rowSpacing: CGFloat

    private var pageSize: CGSize { orientation.pageSize }
    private var labelColumnWidth: CGFloat { orientation == .landscape ? 216.0 : 188.0 }
    private var timelineWidth: CGFloat {
        max(120.0, pageSize.width - (pageMargin * 2.0) - labelColumnWidth)
    }
    private var dayColumnWidth: CGFloat {
        timelineWidth / CGFloat(max(1, layout.totalDays))
    }

    private func rowHeight(for row: RoadmapSnapshotRow) -> CGFloat {
        if case .phaseHeader = row { return phaseRowHeight }
        return taskRowHeight
    }

    private var tableBodyHeight: CGFloat {
        let rowsSum = pageRows.reduce(CGFloat(0)) { $0 + rowHeight(for: $1) }
        let spacingsSum = pageRows.isEmpty ? 0 : CGFloat(pageRows.count) * rowSpacing
        return headerHeight + max(36.0, rowsSum + spacingsSum + 10.0)
    }

    var body: some View {
        let skyAccent = Color(red: 0.220, green: 0.596, blue: 0.925)
        let glacierBlue = Color(red: 0.455, green: 0.769, blue: 1.000)
        let amberBlock = Color(red: 0.84, green: 0.61, blue: 0.22)
        let dayW = dayColumnWidth
        let gridW = timelineWidth
        let totalTableH = tableBodyHeight

        VStack(alignment: .leading, spacing: 10) {
            // Top Page Banner (repeats on every page)
            HStack(spacing: 8) {
                Circle()
                    .fill(skyAccent)
                    .frame(width: 10, height: 10)
                Text(project.title)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.black)
                    .lineLimit(1)
                Spacer(minLength: 8)
                Text(formattedWindowLabel)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.gray)
                    .fixedSize(horizontal: true, vertical: false)
                if totalPages > 1 {
                    Text("\(pageIndex + 1) / \(totalPages)")
                        .font(.system(size: 10.5, weight: .bold, design: .rounded))
                        .foregroundStyle(skyAccent)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(skyAccent.opacity(0.12)))
                        .fixedSize()
                }
            }
            .frame(height: topBannerHeight)

            // Main Split Table (Left Pinned Labels + Fitted Timeline Grid)
            HStack(alignment: .top, spacing: 0) {
                VStack(alignment: .leading, spacing: rowSpacing) {
                    HStack {
                        Text(project.title)
                            .font(.system(size: 10.5, weight: .bold))
                            .foregroundStyle(.gray)
                            .lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 10)
                    .frame(width: labelColumnWidth, height: headerHeight)
                    .background(Color(white: 0.955))

                    ForEach(pageRows) { row in
                        let rHeight = rowHeight(for: row)
                        switch row {
                        case .generalHeader:
                            HStack(spacing: 5) {
                                Capsule()
                                    .fill(glacierBlue)
                                    .frame(width: 3, height: 13)
                                Text(generalLabel)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(.black)
                                    .lineLimit(2)
                                    .fixedSize(horizontal: false, vertical: true)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 8)
                            .frame(width: labelColumnWidth, height: rHeight, alignment: .leading)

                        case .phaseHeader(let h, _):
                            HStack(alignment: .top, spacing: 5) {
                                Capsule()
                                    .fill(glacierBlue)
                                    .frame(width: 3.5, height: 15)
                                    .padding(.top, 2)
                                Text(h.title)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(.black)
                                    .lineLimit(3)
                                    .multilineTextAlignment(.leading)
                                    .fixedSize(horizontal: false, vertical: true)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 8)
                            .frame(width: labelColumnWidth, height: rHeight, alignment: .leading)

                        case .task(let bar):
                            Text(bar.isMilestone ? "◆ \(bar.task.title)" : bar.task.title)
                                .font(.system(size: 10.5, weight: bar.isMilestone ? .bold : .medium))
                                .foregroundStyle(
                                    bar.isMilestone
                                        ? ProjectRoadmapExporter.milestoneRoyalVioletColor
                                        : .black.opacity(0.86)
                                )
                                .lineLimit(2)
                                .padding(.leading, 16)
                                .padding(.trailing, 6)
                                .frame(width: labelColumnWidth, height: rHeight, alignment: .leading)
                        }
                    }
                }
                .padding(.bottom, 10)
                .frame(width: labelColumnWidth, height: totalTableH, alignment: .topLeading)
                .background(Color(white: 0.975))

                ZStack(alignment: .topLeading) {
                    // 1. Background Grid & Adaptive Non-Overlapping Header Ticks
                    ZStack(alignment: .topLeading) {
                        Rectangle()
                            .fill(Color(white: 0.955))
                            .frame(width: gridW, height: headerHeight)

                        Canvas { context, size in
                            let lineStep = dayW >= 9.0 ? 1 : (dayW >= 3.5 ? 7 : 14)
                            var idx = 0
                            while idx <= layout.totalDays {
                                let x = CGFloat(idx) * dayW
                                var vLine = Path()
                                vLine.move(to: CGPoint(x: x, y: headerHeight))
                                vLine.addLine(to: CGPoint(x: x, y: size.height))
                                context.stroke(vLine, with: .color(.black.opacity(0.06)), lineWidth: 0.65)
                                idx += lineStep
                            }

                            let todayOffset = layout.todayEpochDay - layout.startEpochDay
                            if todayOffset >= 0 && todayOffset < layout.totalDays {
                                let todayRect = CGRect(
                                    x: CGFloat(todayOffset) * dayW,
                                    y: headerHeight,
                                    width: max(1.5, dayW),
                                    height: max(0, size.height - headerHeight)
                                )
                                context.fill(Path(todayRect), with: .color(skyAccent.opacity(0.08)))

                                let tx = (CGFloat(todayOffset) + 0.5) * dayW
                                var tLine = Path()
                                tLine.move(to: CGPoint(x: tx, y: headerHeight))
                                tLine.addLine(to: CGPoint(x: tx, y: size.height))
                                context.stroke(tLine, with: .color(skyAccent.opacity(0.7)), lineWidth: 1.3)
                            }
                        }
                        .frame(width: gridW, height: totalTableH)

                        // Unclipped adaptive month & day ticks
                        ForEach(0..<layout.totalDays, id: \.self) { offset in
                            let epochDay = layout.startEpochDay + offset
                            if shouldShowMonthTick(offset: offset, epochDay: epochDay, dayW: dayW) {
                                Text(monthLabel(for: epochDay))
                                    .font(.system(size: 8.5, weight: .bold))
                                    .foregroundStyle(.gray)
                                    .fixedSize(horizontal: true, vertical: false)
                                    .offset(x: min(gridW - 26, CGFloat(offset) * dayW + 2), y: 5)
                            }
                            if shouldShowDayTick(offset: offset, epochDay: epochDay, dayW: dayW) {
                                let isToday = epochDay == layout.todayEpochDay
                                Text("\(dayOfMonth(for: epochDay))")
                                    .font(.system(size: 9.5, weight: isToday ? .bold : .semibold, design: .rounded))
                                    .foregroundStyle(isToday ? skyAccent : .black.opacity(0.78))
                                    .fixedSize(horizontal: true, vertical: false)
                                    .offset(x: min(gridW - 18, CGFloat(offset) * dayW + 2), y: 21)
                            }
                        }
                    }

                    // 2. Bars Layer (Clamped + Always-Visible Phase Titles)
                    VStack(alignment: .leading, spacing: rowSpacing) {
                        Color.clear.frame(width: gridW, height: headerHeight)

                        ForEach(pageRows) { row in
                            let rHeight = rowHeight(for: row)
                            ZStack(alignment: .leading) {
                                Color.clear.frame(width: gridW, height: rHeight)
                                switch row {
                                case .generalHeader:
                                    EmptyView()

                                case .phaseHeader(let h, let maybeBar):
                                    if let pBar = maybeBar,
                                       pBar.endEpochDay >= layout.startEpochDay,
                                       pBar.startEpochDay <= layout.endEpochDay {
                                        let cStart = max(layout.startEpochDay, pBar.startEpochDay)
                                        let cEnd = min(layout.endEpochDay, pBar.endEpochDay)
                                        let cSpan = max(1, cEnd - cStart + 1)
                                        let barX = CGFloat(cStart - layout.startEpochDay) * dayW + 1.5
                                        let barW = max(min(dayW - 2, 12), CGFloat(cSpan) * dayW - 3.0)
                                        let estTitleW = CGFloat(h.title.count) * 6.4 + 22.0
                                        let fitsInside = barW >= estTitleW
                                        let placeLeading = !fitsInside && (gridW - (barX + barW) < estTitleW) && (barX >= estTitleW)

                                        HStack(spacing: 4) {
                                            if fitsInside {
                                                Text(h.title)
                                                    .font(.system(size: 10, weight: .bold))
                                                    .foregroundStyle(.white)
                                                    .lineLimit(1)
                                            }
                                            Spacer(minLength: 0)
                                        }
                                        .padding(.horizontal, fitsInside ? 7 : 0)
                                        .frame(width: barW, height: 22)
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
                                        .overlay(alignment: placeLeading ? .leading : .trailing) {
                                            if !fitsInside {
                                                Text(h.title)
                                                    .font(.system(size: 9.5, weight: .bold))
                                                    .foregroundStyle(skyAccent)
                                                    .lineLimit(1)
                                                    .fixedSize(horizontal: true, vertical: false)
                                                    .alignmentGuide(placeLeading ? .leading : .trailing) { d in
                                                        placeLeading ? (d[.trailing] + 5) : (d[.leading] - 5)
                                                    }
                                            }
                                        }
                                        .offset(x: barX)
                                    }

                                case .task(let tBar):
                                    if tBar.endEpochDay >= layout.startEpochDay && tBar.startEpochDay <= layout.endEpochDay {
                                        let cStart = max(layout.startEpochDay, tBar.startEpochDay)
                                        let cEnd = min(layout.endEpochDay, tBar.endEpochDay)
                                        if tBar.isMilestone {
                                            let diamondRadius: CGFloat = 7.0
                                            let centerX = (CGFloat(cStart - layout.startEpochDay) + 0.5) * dayW
                                            let diamondPath = Path(
                                                ProjectRoadmapExporter.makeDiamondCGPath(
                                                    center: CGPoint(x: diamondRadius, y: diamondRadius),
                                                    radius: diamondRadius
                                                )
                                            )
                                            let estLabelW = CGFloat(tBar.task.title.count) * 6.2 + 24.0
                                            let placeLeading = (gridW - (centerX + diamondRadius) < estLabelW) && (centerX - diamondRadius >= estLabelW)

                                            ZStack {
                                                diamondPath
                                                    .fill(ProjectRoadmapExporter.milestoneRoyalVioletColor)
                                                diamondPath
                                                    .stroke(Color.white.opacity(0.92), lineWidth: 1.0)
                                            }
                                            .frame(width: diamondRadius * 2.0, height: diamondRadius * 2.0)
                                            .overlay(alignment: placeLeading ? .leading : .trailing) {
                                                Text("◆ \(tBar.task.title)")
                                                    .font(.system(size: 9.5, weight: .bold))
                                                    .foregroundStyle(ProjectRoadmapExporter.milestoneRoyalVioletColor)
                                                    .lineLimit(1)
                                                    .fixedSize(horizontal: true, vertical: false)
                                                    .alignmentGuide(placeLeading ? .leading : .trailing) { d in
                                                        placeLeading ? (d[.trailing] + 5) : (d[.leading] - 5)
                                                    }
                                            }
                                            .offset(x: centerX - diamondRadius)
                                        } else {
                                            let cSpan = max(1, cEnd - cStart + 1)
                                            let barX = CGFloat(cStart - layout.startEpochDay) * dayW + 1.5
                                            let barW = max(min(dayW - 2, 10), CGFloat(cSpan) * dayW - 3.0)
                                            let showInlineTitle = barW >= 46.0

                                            HStack(spacing: 4) {
                                                if showInlineTitle {
                                                    Text(tBar.task.title)
                                                        .font(.system(size: 9.5, weight: .semibold))
                                                        .foregroundStyle(.black.opacity(0.88))
                                                        .lineLimit(1)
                                                }
                                                Spacer(minLength: 0)
                                            }
                                            .padding(.horizontal, showInlineTitle ? 6 : 0)
                                            .frame(width: barW, height: 20)
                                            .background(
                                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                                    .fill(Color.white)
                                                    .overlay(
                                                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                                                            .strokeBorder(skyAccent.opacity(0.52), lineWidth: 0.9)
                                                    )
                                            )
                                            .offset(x: barX)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Dependency Edges Canvas (Cumulative Dynamic Row Y-Offsets)
                    Canvas { context, _ in
                        var rowYByTaskId: [String: CGFloat] = [:]
                        var cursorY: CGFloat = headerHeight + rowSpacing
                        for r in pageRows {
                            let h = rowHeight(for: r)
                            if case .task(let tb) = r {
                                rowYByTaskId[tb.id] = cursorY + h * 0.5
                            }
                            cursorY += h + rowSpacing
                        }
                        let barById = Dictionary(uniqueKeysWithValues: layout.allTaskBars.map { ($0.id, $0) })

                        for edge in layout.dependencyEdges {
                            guard let fromBar = barById[edge.fromTaskId],
                                  let toBar = barById[edge.toTaskId],
                                  let y0 = rowYByTaskId[edge.fromTaskId],
                                  let y1 = rowYByTaskId[edge.toTaskId] else {
                                continue
                            }
                            let cFromEnd = min(layout.endEpochDay, max(layout.startEpochDay, fromBar.endEpochDay))
                            let cToStart = max(layout.startEpochDay, min(layout.endEpochDay, toBar.startEpochDay))
                            let x0 = fromBar.isMilestone
                                ? ((CGFloat(cFromEnd - layout.startEpochDay) + 0.5) * dayW + 7.0)
                                : (CGFloat(cFromEnd - layout.startEpochDay + 1) * dayW - 1.5)
                            let x1 = toBar.isMilestone
                                ? ((CGFloat(cToStart - layout.startEpochDay) + 0.5) * dayW - 7.0)
                                : (CGFloat(cToStart - layout.startEpochDay) * dayW + 1.5)
                            let p0 = CGPoint(x: x0, y: y0)
                            let p3 = CGPoint(x: x1, y: y1)
                            let dx = max(14.0, abs(p3.x - p0.x) * 0.5)

                            var path = Path()
                            path.move(to: p0)
                            path.addCurve(
                                to: p3,
                                control1: CGPoint(x: p0.x + dx, y: p0.y),
                                control2: CGPoint(x: p3.x - dx, y: p3.y)
                            )
                            let strokeColor = edge.isActivelyBlocking ? amberBlock : skyAccent.opacity(0.45)
                            context.stroke(path, with: .color(strokeColor), lineWidth: 1.3)

                            var arrow = Path()
                            arrow.move(to: CGPoint(x: p3.x - 5, y: p3.y - 3))
                            arrow.addLine(to: p3)
                            arrow.addLine(to: CGPoint(x: p3.x - 5, y: p3.y + 3))
                            context.stroke(arrow, with: .color(strokeColor), lineWidth: 1.3)
                        }
                    }
                    .frame(width: gridW, height: totalTableH)
                    .allowsHitTesting(false)
                }
                .frame(width: gridW, height: totalTableH, alignment: .topLeading)
                .clipped()
            }
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .strokeBorder(Color.black.opacity(0.09), lineWidth: 0.85)
            )

            Spacer(minLength: 0)
        }
        .padding(pageMargin)
        .frame(width: pageSize.width, height: pageSize.height, alignment: .topLeading)
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

    private func monthLabel(for epochDay: Int) -> String {
        let d = DateEpochConverter.date(fromEpochDay: epochDay)
        let df = DateFormatter()
        df.locale = locale
        df.setLocalizedDateFormatFromTemplate("MMM")
        return df.string(from: d)
    }

    private func shouldShowMonthTick(offset: Int, epochDay: Int, dayW: CGFloat) -> Bool {
        if offset == 0 { return true }
        let dom = dayOfMonth(for: epochDay)
        guard dom == 1 else { return false }
        // Avoid overlapping offset == 0 label when day 1 is within ~28pt of left edge
        return CGFloat(offset) * dayW >= 28.0
    }

    private func shouldShowDayTick(offset: Int, epochDay: Int, dayW: CGFloat) -> Bool {
        if dayW >= 20.0 {
            return true
        }
        if dayW >= 11.5 {
            return offset % 2 == 0
        }
        if dayW >= 4.2 {
            let iso = DateEpochConverter.isoWeekday(for: DateEpochConverter.date(fromEpochDay: epochDay))
            return offset == 0 || iso == 1
        }
        return offset % 14 == 0
    }
}

// MARK: - Unclipped Full-Width Snapshot View for 2x Retina PNG Export

private struct CoreRoadmapSnapshotExportView: View {
    let project: Project
    let headings: [Heading]
    let layout: ProjectRoadmapLayout
    let generalLabel: String
    let todayLabel: String
    let locale: Locale
    let dayColumnWidth: CGFloat

    private let labelColumnWidth: CGFloat = 272
    private let phaseRowHeight: CGFloat = 52
    private let standardRowHeight: CGFloat = 34
    private let rowSpacing: CGFloat = 4
    private let headerHeight: CGFloat = 46

    private func rowHeight(for row: RoadmapSnapshotRow) -> CGFloat {
        if case .phaseHeader = row { return phaseRowHeight }
        return standardRowHeight
    }

    private var rows: [RoadmapSnapshotRow] {
        ProjectRoadmapExporter.buildSnapshotRows(
            headings: headings.filter { $0.projectId == project.id },
            layout: layout,
            includeEmptySelectedPhases: false
        )
    }

    private func computeBodyHeight(for displayRows: [RoadmapSnapshotRow]) -> CGFloat {
        let rowsSum = displayRows.reduce(CGFloat(0)) { $0 + rowHeight(for: $1) }
        let spacingsSum = displayRows.isEmpty ? 0 : CGFloat(displayRows.count) * rowSpacing
        return headerHeight + max(34, rowsSum + spacingsSum + 12)
    }

    var intrinsicSize: CGSize {
        let displayRows = rows
        let gridWidth = CGFloat(max(21, layout.totalDays)) * dayColumnWidth
        let bodyHeight = computeBodyHeight(for: displayRows)
        return CGSize(
            width: labelColumnWidth + gridWidth + 48,
            height: 68 + bodyHeight + 28
        )
    }

    var body: some View {
        let displayRows = rows
        let gridWidth = CGFloat(max(21, layout.totalDays)) * dayColumnWidth
        let bodyHeight = computeBodyHeight(for: displayRows)
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
                        let rHeight = rowHeight(for: row)
                        switch row {
                        case .generalHeader:
                            Text(generalLabel)
                                .font(.system(size: 12.5, weight: .bold))
                                .foregroundStyle(.black)
                                .padding(.horizontal, 10)
                                .frame(width: labelColumnWidth, height: rHeight, alignment: .leading)
                        case .phaseHeader(let h, _):
                            HStack(alignment: .top, spacing: 6) {
                                Capsule()
                                    .fill(glacierBlue)
                                    .frame(width: 3.5, height: 15)
                                    .padding(.top, 2)
                                Text(h.title)
                                    .font(.system(size: 12.5, weight: .bold))
                                    .foregroundStyle(.black)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)
                                    .fixedSize(horizontal: false, vertical: true)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 10)
                            .frame(width: labelColumnWidth, height: rHeight, alignment: .leading)
                        case .task(let bar):
                            Text(bar.isMilestone ? "◆ \(bar.task.title)" : bar.task.title)
                                .font(.system(size: 12, weight: bar.isMilestone ? .bold : .medium))
                                .foregroundStyle(
                                    bar.isMilestone
                                        ? ProjectRoadmapExporter.milestoneRoyalVioletColor
                                        : .black.opacity(0.86)
                                )
                                .lineLimit(1)
                                .padding(.leading, 20)
                                .padding(.trailing, 8)
                                .frame(width: labelColumnWidth, height: rHeight, alignment: .leading)
                        }
                    }
                }
                .padding(.bottom, 12)
                .frame(width: labelColumnWidth, height: bodyHeight, alignment: .topLeading)
                .background(Color(white: 0.975))

                ZStack(alignment: .topLeading) {
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
                                    .frame(width: dayColumnWidth, height: max(34, bodyHeight - headerHeight))
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

                    VStack(alignment: .leading, spacing: rowSpacing) {
                        Color.clear.frame(width: gridWidth, height: headerHeight)

                        ForEach(displayRows) { row in
                            let rHeight = rowHeight(for: row)
                            ZStack(alignment: .leading) {
                                Color.clear.frame(width: gridWidth, height: rHeight)
                                switch row {
                                case .generalHeader:
                                    EmptyView()
                                case .phaseHeader(let h, let maybeBar):
                                    if let pBar = maybeBar,
                                       pBar.endEpochDay >= layout.startEpochDay,
                                       pBar.startEpochDay <= layout.endEpochDay {
                                        let cStart = max(layout.startEpochDay, pBar.startEpochDay)
                                        let cEnd = min(layout.endEpochDay, pBar.endEpochDay)
                                        let cSpan = max(1, cEnd - cStart + 1)
                                        let xOff = CGFloat(cStart - layout.startEpochDay) * dayColumnWidth + 2
                                        let barW = max(dayColumnWidth - 4, CGFloat(cSpan) * dayColumnWidth - 4)
                                        let estTitleW = CGFloat(h.title.count) * 7.2 + 34.0
                                        let fitsInside = barW >= estTitleW
                                        let placeLeading = !fitsInside && (gridWidth - (xOff + barW) < estTitleW) && (xOff >= estTitleW)

                                        HStack(spacing: 5) {
                                            if fitsInside {
                                                Text(h.title)
                                                    .font(.system(size: 11, weight: .bold))
                                                    .foregroundStyle(.white)
                                                    .lineLimit(1)
                                            }
                                            Spacer(minLength: 0)
                                        }
                                        .padding(.horizontal, fitsInside ? 8 : 0)
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
                                        .overlay(alignment: placeLeading ? .leading : .trailing) {
                                            if !fitsInside {
                                                Text(h.title)
                                                    .font(.system(size: 11, weight: .bold))
                                                    .foregroundStyle(skyAccent)
                                                    .lineLimit(1)
                                                    .fixedSize(horizontal: true, vertical: false)
                                                    .alignmentGuide(placeLeading ? .leading : .trailing) { d in
                                                        placeLeading ? (d[.trailing] + 6) : (d[.leading] - 6)
                                                    }
                                            }
                                        }
                                        .offset(x: xOff)
                                    }
                                case .task(let tBar):
                                    if tBar.endEpochDay >= layout.startEpochDay && tBar.startEpochDay <= layout.endEpochDay {
                                        let cStart = max(layout.startEpochDay, tBar.startEpochDay)
                                        let cEnd = min(layout.endEpochDay, tBar.endEpochDay)
                                        if tBar.isMilestone {
                                            let diamondRadius: CGFloat = 8.5
                                            let centerX = (CGFloat(cStart - layout.startEpochDay) + 0.5) * dayColumnWidth
                                            let diamondPath = Path(
                                                ProjectRoadmapExporter.makeDiamondCGPath(
                                                    center: CGPoint(x: diamondRadius, y: diamondRadius),
                                                    radius: diamondRadius
                                                )
                                            )
                                            let estLabelW = CGFloat(tBar.task.title.count) * 7.2 + 30.0
                                            let placeLeading = (gridWidth - (centerX + diamondRadius) < estLabelW) && (centerX - diamondRadius >= estLabelW)

                                            ZStack {
                                                diamondPath
                                                    .fill(ProjectRoadmapExporter.milestoneRoyalVioletColor)
                                                diamondPath
                                                    .stroke(Color.white.opacity(0.92), lineWidth: 1.2)
                                            }
                                            .frame(width: diamondRadius * 2.0, height: diamondRadius * 2.0)
                                            .overlay(alignment: placeLeading ? .leading : .trailing) {
                                                Text("◆ \(tBar.task.title)")
                                                    .font(.system(size: 11, weight: .bold))
                                                    .foregroundStyle(ProjectRoadmapExporter.milestoneRoyalVioletColor)
                                                    .lineLimit(1)
                                                    .fixedSize(horizontal: true, vertical: false)
                                                    .alignmentGuide(placeLeading ? .leading : .trailing) { d in
                                                        placeLeading ? (d[.trailing] + 6) : (d[.leading] - 6)
                                                    }
                                            }
                                            .offset(x: centerX - diamondRadius)
                                        } else {
                                            let cSpan = max(1, cEnd - cStart + 1)
                                            let xOff = CGFloat(cStart - layout.startEpochDay) * dayColumnWidth + 3
                                            let barW = max(dayColumnWidth - 6, CGFloat(cSpan) * dayColumnWidth - 6)
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
                        }
                    }

                    Canvas { context, _ in
                        var rowYByTaskId: [String: CGFloat] = [:]
                        var cursorY: CGFloat = headerHeight + rowSpacing
                        for r in displayRows {
                            let h = rowHeight(for: r)
                            if case .task(let tb) = r {
                                rowYByTaskId[tb.id] = cursorY + h * 0.5
                            }
                            cursorY += h + rowSpacing
                        }
                        let barById = Dictionary(uniqueKeysWithValues: layout.allTaskBars.map { ($0.id, $0) })

                        for edge in layout.dependencyEdges {
                            guard let fromBar = barById[edge.fromTaskId],
                                   let toBar = barById[edge.toTaskId],
                                   let y0 = rowYByTaskId[edge.fromTaskId],
                                   let y1 = rowYByTaskId[edge.toTaskId] else {
                                continue
                            }
                            let cFromEnd = min(layout.endEpochDay, max(layout.startEpochDay, fromBar.endEpochDay))
                            let cToStart = max(layout.startEpochDay, min(layout.endEpochDay, toBar.startEpochDay))
                            let x0 = fromBar.isMilestone
                                ? ((CGFloat(cFromEnd - layout.startEpochDay) + 0.5) * dayColumnWidth + 8.5)
                                : (CGFloat(cFromEnd - layout.startEpochDay + 1) * dayColumnWidth - 3)
                            let x1 = toBar.isMilestone
                                ? ((CGFloat(cToStart - layout.startEpochDay) + 0.5) * dayColumnWidth - 8.5)
                                : (CGFloat(cToStart - layout.startEpochDay) * dayColumnWidth + 3)
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
                    .frame(width: gridWidth, height: bodyHeight)
                    .allowsHitTesting(false)
                }
                .frame(width: gridWidth, height: bodyHeight, alignment: .topLeading)
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
