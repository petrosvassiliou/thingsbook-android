import Foundation
import AppKit

public enum AppLanguage: String, CaseIterable, Codable, Sendable, Identifiable {
    case system = "system"
    case en = "en"
    case de = "de"
    case fr = "fr"
    case it = "it"
    case es = "es"

    public var id: String { rawValue }

    public var resolvedCode: String {
        switch self {
        case .system:
            let preferred = Locale.preferredLanguages.first ?? Locale.current.identifier
            let prefix = String(preferred.prefix(2)).lowercased()
            if ["en", "de", "fr", "it", "es"].contains(prefix) {
                return prefix
            }
            return "en"
        default:
            return rawValue
        }
    }

    public var locale: Locale {
        Locale(identifier: resolvedCode)
    }
}

public enum ThingsResourceLoader {
    public static func url(forResource name: String, withExtension ext: String) -> URL? {
        if let direct = Bundle.main.url(forResource: name, withExtension: ext) {
            return direct
        }
        if let resURL = Bundle.main.resourceURL {
            let bundleCandidate = resURL.appendingPathComponent("ThingsbookMac_ThingsbookCore.bundle")
            if let subBundle = Bundle(url: bundleCandidate),
               let found = subBundle.url(forResource: name, withExtension: ext) {
                return found
            }
            let fileCandidate = resURL.appendingPathComponent("\(name).\(ext)")
            if FileManager.default.fileExists(atPath: fileCandidate.path) {
                return fileCandidate
            }
        }
        let mainRootBundle = Bundle.main.bundleURL.appendingPathComponent("ThingsbookMac_ThingsbookCore.bundle")
        if let subBundle = Bundle(url: mainRootBundle),
           let found = subBundle.url(forResource: name, withExtension: ext) {
            return found
        }
        #if SWIFT_PACKAGE
        if let spmURL = Bundle.module.url(forResource: name, withExtension: ext) {
            return spmURL
        }
        #endif
        return nil
    }

    public static func menuBarIconImage() -> NSImage? {
        if let url2x = url(forResource: "MenuBarIconTemplate@2x", withExtension: "png"),
           let img = NSImage(contentsOf: url2x) {
            img.size = NSSize(width: 18, height: 18)
            img.isTemplate = true
            return img
        }
        if let url1x = url(forResource: "MenuBarIconTemplate", withExtension: "png"),
           let img = NSImage(contentsOf: url1x) {
            img.size = NSSize(width: 18, height: 18)
            img.isTemplate = true
            return img
        }
        return nil
    }
}

public final class ThingsStrings: @unchecked Sendable {
    public static let shared = ThingsStrings()

    private let tables: [String: [String: String]]

    public init() {
        var loaded: [String: [String: String]] = [:]
        if let url = ThingsResourceLoader.url(forResource: "strings", withExtension: "json"),
           let data = try? Data(contentsOf: url),
           let decoded = try? JSONDecoder().decode([String: [String: String]].self, from: data) {
            loaded = decoded
        }
        self.tables = loaded
    }

    public var availableLanguages: [String] {
        Array(tables.keys).sorted()
    }

    public func keys(for langCode: String) -> Set<String> {
        Set((tables[langCode] ?? [:]).keys)
    }

    public func text(_ key: String, lang: AppLanguage) -> String {
        let code = lang.resolvedCode
        if let val = tables[code]?[key] {
            return val
        }
        if let fallback = tables["en"]?[key] {
            return fallback
        }
        return key
    }

    public func format(_ key: String, lang: AppLanguage, _ args: CustomStringConvertible...) -> String {
        let template = text(key, lang: lang)
        return Self.formatPositional(template, args: args)
    }

    /// Safely replaces Android `%1$s`, `%2$d`, `%s`, `%d`, and `%%` placeholders without C `va_list` crashes.
    public static func formatPositional(_ template: String, args: [CustomStringConvertible]) -> String {
        guard !args.isEmpty else {
            return template.replacingOccurrences(of: "%%", with: "%")
        }
        var output = ""
        output.reserveCapacity(template.count + 16)
        var idx = template.startIndex
        var implicitCursor = 0

        while idx < template.endIndex {
            let ch = template[idx]
            if ch != "%" {
                output.append(ch)
                idx = template.index(after: idx)
                continue
            }

            let nextIdx = template.index(after: idx)
            if nextIdx >= template.endIndex {
                output.append("%")
                break
            }

            if template[nextIdx] == "%" {
                output.append("%")
                idx = template.index(after: nextIdx)
                continue
            }

            // Match %1$s / %1$d or unindexed %s / %d
            let remaining = template[nextIdx...]
            if let dollarPos = remaining.firstIndex(of: "$"),
               let posNum = Int(remaining[..<dollarPos]),
               posNum >= 1 {
                let typeIdx = remaining.index(after: dollarPos)
                if typeIdx < remaining.endIndex {
                    let spec = remaining[typeIdx]
                    if spec == "s" || spec == "d" || spec == "f" || spec == "@" {
                        let argVal = (posNum - 1 < args.count) ? args[posNum - 1].description : ""
                        output.append(argVal)
                        idx = remaining.index(after: typeIdx)
                        continue
                    }
                }
            }

            let spec = template[nextIdx]
            if spec == "s" || spec == "d" || spec == "@" {
                let argVal = (implicitCursor < args.count) ? args[implicitCursor].description : ""
                implicitCursor += 1
                output.append(argVal)
                idx = template.index(after: nextIdx)
                continue
            }

            output.append("%")
            idx = nextIdx
        }

        return output
    }
}
