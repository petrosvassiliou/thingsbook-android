import SwiftUI
import AppKit
import PDFKit
import Speech
import AVFoundation
import UniformTypeIdentifiers
import ThingsbookCore

// MARK: - Native macOS Live Speech Dictation Controller (v1.8)

@MainActor
public final class MacVoiceDictationController: ObservableObject {
    @Published public private(set) var isRecording: Bool = false
    @Published public private(set) var liveTranscript: String = ""

    private var audioEngine: AVAudioEngine?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private var speechRecognizer: SFSpeechRecognizer?

    public init() {}

    public func toggleRecording(
        locale: Locale,
        onTranscript: @escaping @MainActor (String) -> Void,
        onUnavailable: @escaping @MainActor () -> Void
    ) {
        if isRecording {
            stopRecording()
        } else {
            startRecording(locale: locale, onTranscript: onTranscript, onUnavailable: onUnavailable)
        }
    }

    public func startRecording(
        locale: Locale,
        onTranscript: @escaping @MainActor (String) -> Void,
        onUnavailable: @escaping @MainActor () -> Void
    ) {
        stopRecording()

        SFSpeechRecognizer.requestAuthorization { [weak self] authStatus in
            guard authStatus == .authorized else {
                Task { @MainActor in
                    self?.isRecording = false
                    onUnavailable()
                }
                return
            }

            AVCaptureDevice.requestAccess(for: .audio) { [weak self] micGranted in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    guard micGranted else {
                        self.isRecording = false
                        onUnavailable()
                        return
                    }
                    self.beginAudioSession(locale: locale, onTranscript: onTranscript, onUnavailable: onUnavailable)
                }
            }
        }
    }

    private func beginAudioSession(
        locale: Locale,
        onTranscript: @escaping @MainActor (String) -> Void,
        onUnavailable: @escaping @MainActor () -> Void
    ) {
        let recognizer = SFSpeechRecognizer(locale: locale) ?? SFSpeechRecognizer()
        guard let recognizer, recognizer.isAvailable else {
            isRecording = false
            onUnavailable()
            return
        }
        self.speechRecognizer = recognizer

        let engine = AVAudioEngine()
        let inputNode = engine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        guard format.channelCount > 0, format.sampleRate > 0 else {
            isRecording = false
            onUnavailable()
            return
        }

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        self.recognitionRequest = request
        self.audioEngine = engine

        inputNode.removeTap(onBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            request.append(buffer)
        }

        do {
            engine.prepare()
            try engine.start()
            isRecording = true
            liveTranscript = ""
        } catch {
            stopRecording()
            onUnavailable()
            return
        }

        recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
            let transcriptText = result?.bestTranscription.formattedString
            let isFinal = result?.isFinal ?? false
            let hasError = error != nil

            Task { @MainActor [weak self] in
                guard let self else { return }
                if let transcriptText, !transcriptText.isEmpty {
                    self.liveTranscript = transcriptText
                    onTranscript(transcriptText)
                }
                if isFinal || hasError {
                    self.stopRecording()
                }
            }
        }
    }

    public func stopRecording() {
        if let engine = audioEngine {
            if engine.isRunning {
                engine.stop()
            }
            engine.inputNode.removeTap(onBus: 0)
        }
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        recognitionRequest = nil
        recognitionTask = nil
        audioEngine = nil
        isRecording = false
    }
}

// MARK: - Attachment File Preparation & Image Downscaling (v1.8)

public enum MacAttachmentImportError: LocalizedError {
    case tooLarge
    case unreadable

    public var errorDescription: String? {
        switch self {
        case .tooLarge: return "attachment_too_large"
        case .unreadable: return "Unable to read selected file."
        }
    }
}

public enum MacAttachmentImporter {
    public static let maxAttachmentBytes = 5 * 1024 * 1024
    private static let maxRawImageCandidateBytes = 25 * 1024 * 1024
    private static let imageCompressThresholdBytes = 450 * 1024
    private static let maxImageEdgePixels: CGFloat = 1440

    public static func prepareFileForImport(url: URL) throws -> (fileName: String, mimeType: String, data: Data) {
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed { url.stopAccessingSecurityScopedResource() }
        }

        let originalName = url.lastPathComponent.isEmpty ? "attachment" : url.lastPathComponent
        let ext = url.pathExtension.lowercased()
        let utType = UTType(filenameExtension: ext)
        var detectedMime = utType?.preferredMIMEType ?? fallbackMimeType(forExtension: ext)

        let isRasterImage = detectedMime.hasPrefix("image/")
            && detectedMime != "image/svg+xml"
            && detectedMime != "image/gif"

        guard let rawData = try? Data(contentsOf: url), !rawData.isEmpty else {
            throw MacAttachmentImportError.unreadable
        }

        if !isRasterImage && rawData.count > maxAttachmentBytes {
            throw MacAttachmentImportError.tooLarge
        }
        if isRasterImage && rawData.count > maxRawImageCandidateBytes {
            throw MacAttachmentImportError.tooLarge
        }

        var finalData = rawData
        var finalName = originalName

        let needsImageTranscode = isRasterImage && (
            rawData.count > imageCompressThresholdBytes ||
            detectedMime == "image/heic" ||
            detectedMime == "image/heif" ||
            detectedMime == "image/tiff"
        )

        if needsImageTranscode, let compressed = downscaleAndCompressJPEG(rawData: rawData) {
            finalData = compressed
            detectedMime = "image/jpeg"
            let baseName = (originalName as NSString).deletingPathExtension
            let cleanBase = baseName.isEmpty ? "photo" : baseName
            finalName = "\(cleanBase).jpg"
        }

        guard finalData.count <= maxAttachmentBytes else {
            throw MacAttachmentImportError.tooLarge
        }

        return (finalName, detectedMime, finalData)
    }

    private static func downscaleAndCompressJPEG(rawData: Data) -> Data? {
        guard let image = NSImage(data: rawData) else { return nil }
        var pixelWidth: CGFloat = image.size.width
        var pixelHeight: CGFloat = image.size.height
        if let rep = image.representations.first, rep.pixelsWide > 0, rep.pixelsHigh > 0 {
            pixelWidth = CGFloat(rep.pixelsWide)
            pixelHeight = CGFloat(rep.pixelsHigh)
        }
        guard pixelWidth > 0, pixelHeight > 0 else { return nil }

        let longestEdge = max(pixelWidth, pixelHeight)
        let scale = longestEdge > maxImageEdgePixels ? (maxImageEdgePixels / longestEdge) : 1.0
        let targetW = max(1, Int((pixelWidth * scale).rounded()))
        let targetH = max(1, Int((pixelHeight * scale).rounded()))

        guard let bitmapRep = NSBitmapImageRep(
            bitmapDataPlanes: nil,
            pixelsWide: targetW,
            pixelsHigh: targetH,
            bitsPerSample: 8,
            samplesPerPixel: 4,
            hasAlpha: true,
            isPlanar: false,
            colorSpaceName: .calibratedRGB,
            bytesPerRow: 0,
            bitsPerPixel: 0
        ) else {
            return nil
        }

        bitmapRep.size = NSSize(width: targetW, height: targetH)
        NSGraphicsContext.saveGraphicsState()
        if let ctx = NSGraphicsContext(bitmapImageRep: bitmapRep) {
            ctx.imageInterpolation = .high
            NSGraphicsContext.current = ctx
            NSColor.white.setFill()
            NSRect(x: 0, y: 0, width: targetW, height: targetH).fill()
            image.draw(
                in: NSRect(x: 0, y: 0, width: targetW, height: targetH),
                from: .zero,
                operation: .sourceOver,
                fraction: 1.0
            )
        }
        NSGraphicsContext.restoreGraphicsState()

        return bitmapRep.representation(using: .jpeg, properties: [.compressionFactor: 0.80])
    }

    private static func fallbackMimeType(forExtension ext: String) -> String {
        switch ext {
        case "pdf": return "application/pdf"
        case "png": return "image/png"
        case "jpg", "jpeg": return "image/jpeg"
        case "webp": return "image/webp"
        case "gif": return "image/gif"
        case "heic": return "image/heic"
        case "txt", "md": return "text/plain"
        case "csv": return "text/csv"
        default: return "application/octet-stream"
        }
    }
}

// MARK: - Horizontal Attachments Thumbnail & Document Strip (v1.8)

public struct AttachmentsStripView: View {
    @ObservedObject var store: ThingsbookStore
    public let attachments: [TaskAttachment]
    public let onAddClicked: () -> Void

    public init(
        store: ThingsbookStore,
        attachments: [TaskAttachment],
        onAddClicked: @escaping () -> Void
    ) {
        self.store = store
        self.attachments = attachments
        self.onAddClicked = onAddClicked
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 6) {
                Image(systemName: "paperclip")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)
                Text("\(store.tr("attachments_section_title")) (\(attachments.count))")
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(.secondary)
                Spacer()
                Button(action: onAddClicked) {
                    HStack(spacing: 3) {
                        Image(systemName: "plus")
                            .font(.system(size: 10, weight: .bold))
                        Text(store.tr("action_attach_file"))
                            .font(.system(size: 11, weight: .semibold))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    .foregroundStyle(ThingsMacPalette.accentBlue)
                }
                .buttonStyle(.plain)
                .help(store.tr("action_attach_file"))
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 9) {
                    ForEach(attachments) { att in
                        AttachmentChipCard(store: store, attachment: att)
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }
}

private struct AttachmentChipCard: View {
    @ObservedObject var store: ThingsbookStore
    let attachment: TaskAttachment
    @State private var isHovering = false

    var body: some View {
        let resolvedURL = store.database.resolveAttachmentURL(attachment)
        HStack(spacing: 9) {
            if attachment.isImage, let url = resolvedURL, let img = NSImage(contentsOf: url) {
                Image(nsImage: img)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 42, height: 42)
                    .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(attachment.isPdf ? ThingsMacPalette.deadlineCrimson.opacity(0.13) : ThingsMacPalette.accentBlue.opacity(0.12))
                        .frame(width: 42, height: 42)
                    Image(systemName: attachment.isPdf ? "doc.richtext.fill" : "doc.fill")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(attachment.isPdf ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue)
                }
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(attachment.fileName)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .frame(maxWidth: 145, alignment: .leading)
                Text(attachment.formattedSize)
                    .font(.system(size: 10.5, weight: .medium))
                    .foregroundStyle(.secondary)
            }

            Button {
                store.removeAttachment(attachment)
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary.opacity(isHovering ? 0.95 : 0.55))
            }
            .buttonStyle(.plain)
            .help(store.tr("attachment_remove"))
        }
        .padding(.leading, 6)
        .padding(.trailing, 9)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(ThingsMacPalette.subtleContainer)
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .strokeBorder(
                            isHovering ? ThingsMacPalette.accentBlue.opacity(0.42) : Color.primary.opacity(0.08),
                            lineWidth: 0.9
                        )
                )
        )
        .contentShape(Rectangle())
        .onHover { hovering in
            isHovering = hovering
        }
        .onTapGesture {
            store.previewingAttachment = attachment
        }
        .contextMenu {
            Button(store.tr("macos_attachment_preview_title")) {
                store.previewingAttachment = attachment
            }
            if let url = resolvedURL {
                Button(store.tr("attachment_open_external")) {
                    NSWorkspace.shared.open(url)
                }
                Button(store.tr("macos_attachment_quicklook")) {
                    NSWorkspace.shared.activateFileViewerSelecting([url])
                }
            }
            Divider()
            Button(role: .destructive) {
                store.removeAttachment(attachment)
            } label: {
                Text(store.tr("attachment_remove"))
            }
        }
    }
}

// MARK: - Native In-App PDF & Zoomable Image Viewer Sheet (v1.8)

public struct AttachmentPreviewSheet: View {
    @ObservedObject var store: ThingsbookStore
    public let attachment: TaskAttachment
    @Environment(\.dismiss) private var dismiss
    @State private var imageZoom: CGFloat = 1.0

    public init(store: ThingsbookStore, attachment: TaskAttachment) {
        self.store = store
        self.attachment = attachment
    }

    public var body: some View {
        let resolvedURL = store.database.resolveAttachmentURL(attachment)
        VStack(spacing: 0) {
            // Header Toolbar
            HStack(spacing: 10) {
                Image(systemName: attachment.isImage ? "photo" : (attachment.isPdf ? "doc.richtext.fill" : "doc.fill"))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(attachment.isPdf ? ThingsMacPalette.deadlineCrimson : ThingsMacPalette.accentBlue)

                VStack(alignment: .leading, spacing: 1) {
                    Text(attachment.fileName)
                        .font(.system(size: 14, weight: .semibold))
                        .lineLimit(1)
                    Text(attachment.formattedSize)
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }

                Spacer()

                if attachment.isImage {
                    HStack(spacing: 4) {
                        Button {
                            imageZoom = max(0.5, imageZoom - 0.25)
                        } label: {
                            Image(systemName: "minus.magnifyingglass")
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .help(store.tr("macos_tooltip_zoom_out"))

                        Button {
                            imageZoom = 1.0
                        } label: {
                            Text("\(Int((imageZoom * 100).rounded()))%")
                                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                .frame(width: 44)
                        }
                        .buttonStyle(.plain)
                        .help(store.tr("macos_tooltip_zoom_reset"))

                        Button {
                            imageZoom = min(4.0, imageZoom + 0.25)
                        } label: {
                            Image(systemName: "plus.magnifyingglass")
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .help(store.tr("macos_tooltip_zoom_in"))
                    }
                }

                if let url = resolvedURL {
                    Button {
                        NSWorkspace.shared.activateFileViewerSelecting([url])
                    } label: {
                        Label(store.tr("macos_attachment_quicklook"), systemImage: "folder")
                            .lineLimit(1)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .fixedSize(horizontal: true, vertical: false)
                    .help(store.tr("macos_attachment_quicklook"))

                    Button {
                        NSWorkspace.shared.open(url)
                    } label: {
                        Label(store.tr("attachment_open_external"), systemImage: "arrow.up.forward.square")
                            .lineLimit(1)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .fixedSize(horizontal: true, vertical: false)
                    .help(store.tr("attachment_open_external"))
                }

                Button(role: .destructive) {
                    store.removeAttachment(attachment)
                    dismiss()
                } label: {
                    Image(systemName: "trash")
                        .foregroundStyle(ThingsMacPalette.deadlineCrimson)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .help(store.tr("attachment_remove"))

                Button(store.tr("action_done")) {
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
                .fixedSize(horizontal: true, vertical: false)
                .help(store.tr("action_done"))
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .background(ThingsMacPalette.subtleContainer)

            Divider()

            // Content Canvas
            Group {
                if let url = resolvedURL {
                    if attachment.isImage, let nsImg = NSImage(contentsOf: url) {
                        ScrollView([.horizontal, .vertical]) {
                            Image(nsImage: nsImg)
                                .resizable()
                                .scaledToFit()
                                .frame(
                                    width: max(240, min(680, nsImg.size.width)) * imageZoom,
                                    height: max(200, min(520, nsImg.size.height)) * imageZoom
                                )
                                .padding(24)
                                .frame(minWidth: 680, minHeight: 480)
                        }
                        .background(Color.black.opacity(0.04))
                    } else if attachment.isPdf {
                        MacPDFKitRepresentable(url: url)
                            .frame(minWidth: 680, minHeight: 480)
                    } else {
                        VStack(spacing: 14) {
                            Image(nsImage: NSWorkspace.shared.icon(forFile: url.path))
                                .resizable()
                                .scaledToFit()
                                .frame(width: 72, height: 72)

                            Text(attachment.fileName)
                                .font(.system(size: 16, weight: .semibold))

                            Text("\(attachment.mimeType) · \(attachment.formattedSize)")
                                .font(.system(size: 12.5))
                                .foregroundStyle(.secondary)

                            Button {
                                NSWorkspace.shared.open(url)
                            } label: {
                                Label(store.tr("attachment_open_external"), systemImage: "arrow.up.forward.square")
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding(32)
                    }
                } else {
                    VStack(spacing: 10) {
                        Image(systemName: "icloud.and.arrow.down")
                            .font(.system(size: 36))
                            .foregroundStyle(.secondary)
                        Text(attachment.fileName)
                            .font(.system(size: 14, weight: .semibold))
                        Text(attachment.formattedSize)
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(32)
                }
            }
        }
        .frame(minWidth: 700, idealWidth: 780, minHeight: 540, idealHeight: 620)
    }
}

private struct MacPDFKitRepresentable: NSViewRepresentable {
    let url: URL

    func makeNSView(context: Context) -> PDFView {
        let pdfView = PDFView()
        pdfView.autoScales = true
        pdfView.displayMode = .singlePageContinuous
        pdfView.displaysPageBreaks = true
        pdfView.document = PDFDocument(url: url)
        return pdfView
    }

    func updateNSView(_ nsView: PDFView, context: Context) {
        if nsView.document?.documentURL != url {
            nsView.document = PDFDocument(url: url)
        }
    }
}
