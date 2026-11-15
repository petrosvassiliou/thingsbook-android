import Foundation
import SwiftUI

public enum DetectedNoteLinkType: String, Equatable, Hashable, Sendable {
    case web
    case email
    case phone
    case map

    public var sfSymbol: String {
        switch self {
        case .web: return "safari"
        case .email: return "envelope"
        case .phone: return "phone"
        case .map: return "mappin.and.ellipse"
        }
    }
}

public struct DetectedNoteLink: Identifiable, Equatable, Hashable, Sendable {
    public var id: String { targetUri }
    public let type: DetectedNoteLinkType
    public let displayLabel: String
    public let targetUri: String

    public init(type: DetectedNoteLinkType, displayLabel: String, targetUri: String) {
        self.type = type
        self.displayLabel = displayLabel
        self.targetUri = targetUri
    }
}

public enum MarkdownNotesParser {
    private static let urlRegex = try! NSRegularExpression(
        pattern: #"(?i)\b((?:https?://|www\.)[^\s<>()"']+)"#
    )
    private static let emailRegex = try! NSRegularExpression(
        pattern: #"\b([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})\b"#
    )
    private static let phoneRegex = try! NSRegularExpression(
        pattern: #"(?<!\w)(\+?[0-9][0-9\s().-]{6,}[0-9])(?!\w)"#
    )
    private static let explicitLocationRegex = try! NSRegularExpression(
        pattern: #"(?im)(?:📍|(?:location|ort|adresse|address|map|lieu|indirizzo|dirección|ubicación)\s*:)\s*([^\n]{3,80})"#
    )
    private static let gpsCoordsRegex = try! NSRegularExpression(
        pattern: #"(?<!\d)(-?\d{1,2}\.\d{3,8}\s*,\s*-?\d{1,3}\.\d{3,8})(?!\d)"#
    )
    private static let euStreetAddressRegex = try! NSRegularExpression(
        pattern: #"(?im)(?:^|(?:[-*•]|\bat)\s+)([A-ZÄÖÜ][A-Za-zÄÖÜäöüßéèêàùç.\-\s]{2,34}(?:straße|strasse|str\.|weg|platz|allee|gasse|ring|damm|ufer|rue|avenue|boulevard|via|piazza|calle|avenida)\s+\d{1,4}[a-zA-Z]?(?:\s*,\s*\d{4,5}\s+[A-ZÄÖÜa-zäöüß\-\s]{2,25})?)"#
    )
    private static let usStreetAddressRegex = try! NSRegularExpression(
        pattern: #"(?m)\b(\d{1,5}\s+[A-Z][A-Za-z0-9.\-\s]{2,30}\s+(?:St|Street|Ave|Avenue|Rd|Road|Blvd|Boulevard|Dr|Drive|Ln|Lane|Way|Ct|Court|Pl|Place|Pkwy|Parkway|Hwy|Highway)\b(?:\s*,\s*[A-Za-z.\-\s]{2,25})?)"#
    )
    private static let isoDateGuardRegex = try! NSRegularExpression(
        pattern: #"^\d{4}-\d{2}-\d{2}$"#
    )
    private static let headingRegex = try! NSRegularExpression(
        pattern: #"(?m)^(#{1,4})\s+(.+)$"#
    )
    private static let bulletRegex = try! NSRegularExpression(
        pattern: #"(?m)^(\s*[-*•]\s+)"#
    )
    private static let boldRegex = try! NSRegularExpression(
        pattern: #"(\*\*|__)([^*_\n]+?)\1"#
    )
    private static let italicRegex = try! NSRegularExpression(
        pattern: #"(?<!\*)\*([^*\n]+?)\*(?!\*)"#
    )
    private static let inlineCodeRegex = try! NSRegularExpression(
        pattern: #"`([^`\n]+?)`"#
    )

    private static let mapQueryAllowed: CharacterSet = {
        var set = CharacterSet.urlQueryAllowed
        set.remove(charactersIn: "&+=?#")
        return set
    }()

    public static func extractDetectedLinks(from notes: String) -> [DetectedNoteLink] {
        guard !notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [] }
        let fullRange = NSRange(notes.startIndex..<notes.endIndex, in: notes)
        var results: [DetectedNoteLink] = []
        var occupied: [NSRange] = []

        func overlaps(_ range: NSRange) -> Bool {
            occupied.contains { existing in
                NSIntersectionRange(existing, range).length > 0
            }
        }

        let trimPunctuation = CharacterSet(charactersIn: ".,;:)]!?")
        let trimAddrPunctuation = CharacterSet(charactersIn: ".,;")

        for match in urlRegex.matches(in: notes, range: fullRange) {
            guard let swiftRange = Range(match.range, in: notes) else { continue }
            var raw = String(notes[swiftRange])
            while let last = raw.unicodeScalars.last, trimPunctuation.contains(last) {
                raw.removeLast()
            }
            guard raw.count >= 4 else { continue }
            let lower = raw.lowercased()
            let uri = (lower.hasPrefix("http://") || lower.hasPrefix("https://")) ? raw : "https://\(raw)"
            var cleanHost = raw
            for prefix in ["https://", "http://", "www.", "HTTPS://", "HTTP://", "WWW."] {
                if cleanHost.hasPrefix(prefix) {
                    cleanHost = String(cleanHost.dropFirst(prefix.count))
                }
            }
            let label = String(cleanHost.prefix(36))
            results.append(DetectedNoteLink(type: .web, displayLabel: label, targetUri: uri))
            occupied.append(match.range)
        }

        for match in emailRegex.matches(in: notes, range: fullRange) {
            if overlaps(match.range) { continue }
            guard let swiftRange = Range(match.range, in: notes) else { continue }
            let email = String(notes[swiftRange])
            results.append(DetectedNoteLink(type: .email, displayLabel: String(email.prefix(36)), targetUri: "mailto:\(email)"))
            occupied.append(match.range)
        }

        for match in explicitLocationRegex.matches(in: notes, range: fullRange) {
            if overlaps(match.range) || match.numberOfRanges < 2 { continue }
            guard let groupRange = Range(match.range(at: 1), in: notes) else { continue }
            var loc = String(notes[groupRange]).trimmingCharacters(in: .whitespacesAndNewlines)
            while let last = loc.unicodeScalars.last, trimAddrPunctuation.contains(last) {
                loc.removeLast()
            }
            if loc.count >= 3, let encoded = loc.addingPercentEncoding(withAllowedCharacters: mapQueryAllowed) {
                results.append(DetectedNoteLink(type: .map, displayLabel: String(loc.prefix(34)), targetUri: "https://maps.apple.com/?q=\(encoded)"))
                occupied.append(match.range)
            }
        }

        for match in gpsCoordsRegex.matches(in: notes, range: fullRange) {
            if overlaps(match.range) || match.numberOfRanges < 2 { continue }
            guard let groupRange = Range(match.range(at: 1), in: notes) else { continue }
            let coords = String(notes[groupRange]).replacingOccurrences(of: " ", with: "")
            if !coords.isEmpty {
                results.append(DetectedNoteLink(type: .map, displayLabel: coords, targetUri: "https://maps.apple.com/?ll=\(coords)&q=\(coords)"))
                occupied.append(match.range)
            }
        }

        for regex in [euStreetAddressRegex, usStreetAddressRegex] {
            for match in regex.matches(in: notes, range: fullRange) {
                if overlaps(match.range) || match.numberOfRanges < 2 { continue }
                guard let groupRange = Range(match.range(at: 1), in: notes) else { continue }
                var addr = String(notes[groupRange]).trimmingCharacters(in: .whitespacesAndNewlines)
                while let last = addr.unicodeScalars.last, trimAddrPunctuation.contains(last) {
                    addr.removeLast()
                }
                if addr.count >= 5, let encoded = addr.addingPercentEncoding(withAllowedCharacters: mapQueryAllowed) {
                    results.append(DetectedNoteLink(type: .map, displayLabel: String(addr.prefix(34)), targetUri: "https://maps.apple.com/?q=\(encoded)"))
                    occupied.append(match.range)
                }
            }
        }

        for match in phoneRegex.matches(in: notes, range: fullRange) {
            if overlaps(match.range) { continue }
            guard let swiftRange = Range(match.range, in: notes) else { continue }
            let rawPhone = String(notes[swiftRange]).trimmingCharacters(in: .whitespacesAndNewlines)
            let digitsCount = rawPhone.filter(\.isNumber).count
            let phoneRange = NSRange(rawPhone.startIndex..<rawPhone.endIndex, in: rawPhone)
            if !(7...15).contains(digitsCount) || isoDateGuardRegex.firstMatch(in: rawPhone, range: phoneRange) != nil {
                continue
            }
            let dialDigits = rawPhone.filter { $0.isNumber || $0 == "+" }
            results.append(DetectedNoteLink(type: .phone, displayLabel: String(rawPhone.prefix(24)), targetUri: "tel:\(dialDigits)"))
        }

        var seen = Set<String>()
        var deduped: [DetectedNoteLink] = []
        for item in results {
            if seen.insert(item.targetUri).inserted {
                deduped.append(item)
                if deduped.count >= 8 { break }
            }
        }
        return deduped
    }

    public static func renderHighlightedMarkdown(
        _ notes: String,
        primaryColor: Color,
        secondaryColor: Color,
        accentColor: Color
    ) -> AttributedString {
        var attr = AttributedString(notes)
        guard !notes.isEmpty else { return attr }
        attr.foregroundColor = primaryColor
        let fullRange = NSRange(notes.startIndex..<notes.endIndex, in: notes)

        func applyRange(_ nsRange: NSRange, transform: (inout AttributeContainer) -> Void) {
            guard let strRange = Range(nsRange, in: notes),
                  let lower = AttributedString.Index(strRange.lowerBound, within: attr),
                  let upper = AttributedString.Index(strRange.upperBound, within: attr) else {
                return
            }
            var container = AttributeContainer()
            transform(&container)
            attr[lower..<upper].mergeAttributes(container, mergePolicy: .keepNew)
        }

        for m in headingRegex.matches(in: notes, range: fullRange) {
            applyRange(m.range) { c in
                c.font = .system(size: 14, weight: .bold)
                c.foregroundColor = primaryColor
            }
            if m.numberOfRanges > 1 {
                applyRange(m.range(at: 1)) { c in
                    c.foregroundColor = secondaryColor.opacity(0.6)
                }
            }
        }

        for m in bulletRegex.matches(in: notes, range: fullRange) {
            applyRange(m.range) { c in
                c.font = .system(size: 13, weight: .bold)
                c.foregroundColor = accentColor
            }
        }

        for m in boldRegex.matches(in: notes, range: fullRange) {
            applyRange(m.range) { c in
                c.font = .system(size: 13, weight: .bold)
            }
        }

        for m in italicRegex.matches(in: notes, range: fullRange) {
            applyRange(m.range) { c in
                c.font = .system(size: 13).italic()
            }
        }

        for m in inlineCodeRegex.matches(in: notes, range: fullRange) {
            applyRange(m.range) { c in
                c.font = .system(size: 12.5, design: .monospaced)
                c.foregroundColor = accentColor
                c.backgroundColor = accentColor.opacity(0.12)
            }
        }

        for m in urlRegex.matches(in: notes, range: fullRange) {
            applyRange(m.range) { c in
                c.foregroundColor = accentColor
                c.underlineStyle = .single
            }
        }

        for m in emailRegex.matches(in: notes, range: fullRange) {
            applyRange(m.range) { c in
                c.foregroundColor = accentColor
                c.underlineStyle = .single
            }
        }

        return attr
    }

    public static func renderAttributed(_ notes: String) -> AttributedString {
        renderHighlightedMarkdown(
            notes,
            primaryColor: .primary,
            secondaryColor: .secondary,
            accentColor: Color(red: 0.13, green: 0.51, blue: 0.96)
        )
    }
}
