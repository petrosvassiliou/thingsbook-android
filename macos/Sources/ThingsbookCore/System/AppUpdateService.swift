import Foundation
import AppKit

public struct ReleaseUpdateInfo: Equatable, Sendable {
    public let tagName: String
    public let versionString: String
    public let releaseNotes: String
    public let htmlUrl: String
    public let dmgDownloadUrl: String?

    public init(tagName: String, versionString: String, releaseNotes: String, htmlUrl: String, dmgDownloadUrl: String?) {
        self.tagName = tagName
        self.versionString = versionString
        self.releaseNotes = releaseNotes
        self.htmlUrl = htmlUrl
        self.dmgDownloadUrl = dmgDownloadUrl
    }
}

public enum AppUpdateService {
    public static let currentVersion = "2.0.0-beta.4"
    public static let displayVersion = "2.0.0-beta.4"
    public static let currentBuildNumber = 48
    private static let releasesEndpoint = "https://api.github.com/repos/petrosvassiliou/thingsbook-android/releases?per_page=15"

    public static var currentVersionIsPrerelease: Bool {
        defaultIncludeBeta(for: currentVersion)
    }

    public static func defaultIncludeBeta(for version: String) -> Bool {
        let trimmed = stripLeadingVersionPrefix(version)
        return trimmed.split(separator: "+", maxSplits: 1, omittingEmptySubsequences: false).first?.contains("-") == true
    }

    public static func checkForUpdates(
        current: String = currentVersion,
        includeBeta: Bool? = nil
    ) async throws -> ReleaseUpdateInfo? {
        guard let url = URL(string: releasesEndpoint) else { return nil }
        var req = URLRequest(url: url)
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.timeoutInterval = 10

        let (data, res) = try await URLSession.shared.data(for: req)
        guard let http = res as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }

        return selectEligibleRelease(from: arr, current: current, includeBeta: includeBeta)
    }

    public static func selectEligibleRelease(
        from releases: [[String: Any]],
        current: String = currentVersion,
        includeBeta: Bool? = nil
    ) -> ReleaseUpdateInfo? {
        let effectiveIncludeBeta = includeBeta ?? defaultIncludeBeta(for: current)
        var bestCandidate: ReleaseUpdateInfo?

        for rel in releases {
            if (rel["draft"] as? Bool) == true {
                continue
            }
            if !effectiveIncludeBeta && (rel["prerelease"] as? Bool) == true {
                continue
            }
            guard let rawTag = rel["tag_name"] as? String else { continue }
            let tag = rawTag.trimmingCharacters(in: .whitespacesAndNewlines)
            guard isValidReleaseTag(tag, allowPrerelease: effectiveIncludeBeta) else {
                continue
            }

            let cleanRemote = stripLeadingVersionPrefix(tag)
            let remoteBeforePlus = String(cleanRemote.split(separator: "+", maxSplits: 1, omittingEmptySubsequences: false)[0])
            if !effectiveIncludeBeta && remoteBeforePlus.contains("-") {
                continue
            }

            guard isSemanticNewer(remote: cleanRemote, current: current, includeBeta: effectiveIncludeBeta) else {
                continue
            }

            let assets = (rel["assets"] as? [[String: Any]]) ?? []
            let dmgAsset = assets.first { a in
                let name = ((a["name"] as? String) ?? "").lowercased()
                return name.hasSuffix(".dmg")
            }
            guard let dmgAsset,
                  let downloadUrl = (dmgAsset["browser_download_url"] as? String)?
                    .trimmingCharacters(in: .whitespacesAndNewlines),
                  !downloadUrl.isEmpty else {
                continue
            }

            let htmlUrl = (rel["html_url"] as? String) ?? "https://github.com/petrosvassiliou/thingsbook-android/releases"
            let body = (rel["body"] as? String) ?? ""
            let candidate = ReleaseUpdateInfo(
                tagName: tag,
                versionString: cleanRemote,
                releaseNotes: body,
                htmlUrl: htmlUrl,
                dmgDownloadUrl: downloadUrl
            )
            if let existing = bestCandidate {
                if isSemanticNewer(remote: candidate.versionString, current: existing.versionString, includeBeta: true) {
                    bestCandidate = candidate
                }
            } else {
                bestCandidate = candidate
            }
        }
        return bestCandidate
    }

    public static func isSemanticNewer(
        remote: String,
        current: String,
        includeBeta: Bool? = nil
    ) -> Bool {
        let rTrim = stripLeadingVersionPrefix(remote)
        let cTrim = stripLeadingVersionPrefix(current)
        guard !rTrim.isEmpty, !cTrim.isEmpty else { return false }

        let effectiveIncludeBeta = includeBeta ?? defaultIncludeBeta(for: current)

        let rPlusParts = rTrim.split(separator: "+", maxSplits: 1, omittingEmptySubsequences: false)
        let cPlusParts = cTrim.split(separator: "+", maxSplits: 1, omittingEmptySubsequences: false)
        let rBeforePlus = String(rPlusParts[0])
        let cBeforePlus = String(cPlusParts[0])

        let rHasPre = rBeforePlus.contains("-")
        let cHasPre = cBeforePlus.contains("-")
        if !effectiveIncludeBeta && rHasPre {
            return false
        }

        let rCoreStr = String(rBeforePlus.split(separator: "-", maxSplits: 1, omittingEmptySubsequences: false)[0])
        let cCoreStr = String(cBeforePlus.split(separator: "-", maxSplits: 1, omittingEmptySubsequences: false)[0])

        let rCore = rCoreStr.split(separator: ".").map { Int($0) ?? 0 }
        let cCore = cCoreStr.split(separator: ".").map { Int($0) ?? 0 }
        let count = max(max(rCore.count, cCore.count), 3)
        for i in 0..<count {
            let rv = i < rCore.count ? rCore[i] : 0
            let cv = i < cCore.count ? cCore[i] : 0
            if rv > cv { return true }
            if rv < cv { return false }
        }

        if cHasPre && !rHasPre {
            return true
        }
        if !cHasPre && rHasPre {
            return false
        }
        if cHasPre && rHasPre {
            let rPre = String(rBeforePlus.split(separator: "-", maxSplits: 1)[1])
            let cPre = String(cBeforePlus.split(separator: "-", maxSplits: 1)[1])
            if rPre != cPre {
                return comparePrereleaseIdentifiers(rPre, cPre) > 0
            }
        }

        let rBuild = rPlusParts.count > 1 ? (Int(rPlusParts[1].prefix(while: \.isNumber)) ?? 0) : 0
        let cBuild = cPlusParts.count > 1 ? (Int(cPlusParts[1].prefix(while: \.isNumber)) ?? 0) : 0
        return rBuild > cBuild
    }

    private static func comparePrereleaseIdentifiers(_ lhs: String, _ rhs: String) -> Int {
        let lTokens = lhs.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        let rTokens = rhs.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        let maxCount = max(lTokens.count, rTokens.count)
        for i in 0..<maxCount {
            if i >= lTokens.count { return -1 }
            if i >= rTokens.count { return 1 }
            let lt = lTokens[i]
            let rt = rTokens[i]
            if lt == rt { continue }

            let lNum = (!lt.isEmpty && lt.allSatisfy { $0.isASCII && $0.isNumber }) ? UInt64(lt) : nil
            let rNum = (!rt.isEmpty && rt.allSatisfy { $0.isASCII && $0.isNumber }) ? UInt64(rt) : nil
            if let ln = lNum, let rn = rNum {
                return ln > rn ? 1 : -1
            }
            if lNum != nil && rNum == nil {
                return -1
            }
            if lNum == nil && rNum != nil {
                return 1
            }
            let cmp = lt.lowercased().compare(rt.lowercased())
            if cmp == .orderedDescending { return 1 }
            if cmp == .orderedAscending { return -1 }
        }
        return 0
    }

    private static func isValidReleaseTag(_ tag: String, allowPrerelease: Bool) -> Bool {
        let pattern = allowPrerelease
            ? #"^[vV]\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?(\+\d+)?$"#
            : #"^[vV]\d+\.\d+\.\d+(\+\d+)?$"#
        return tag.range(of: pattern, options: .regularExpression) != nil
    }

    private static func stripLeadingVersionPrefix(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while let first = s.first, first == "v" || first == "V" || first.isWhitespace {
            s.removeFirst()
        }
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public static func openUpdatePage(_ info: ReleaseUpdateInfo) {
        let urlStr = info.dmgDownloadUrl ?? info.htmlUrl
        if let url = URL(string: urlStr) {
            NSWorkspace.shared.open(url)
        }
    }
}
