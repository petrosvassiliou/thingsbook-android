import Foundation
import AppKit
import Darwin

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

public enum MacInAppUpdatePhase: Equatable, Sendable {
    case idle
    case checking
    case downloading(version: String, progress: Double?)
    case extracting(version: String)
    case readyToInstall(version: String, stagedAppPath: String, stagingRootPath: String)
    case installingAndRelaunching(version: String)
    case failed(message: String)
}

public enum MacInAppUpdateError: LocalizedError, Equatable, Sendable {
    case missingDownloadUrl
    case downloadFailed(statusCode: Int)
    case mountFailed(details: String)
    case appBundleNotFoundInImage
    case invalidAppBundle(details: String)
    case copyToStagingFailed(details: String)
    case launchUpdaterFailed(details: String)

    public var errorDescription: String? {
        switch self {
        case .missingDownloadUrl:
            return "No macOS update package asset was found in the release."
        case .downloadFailed(let statusCode):
            return "HTTP \(statusCode)"
        case .mountFailed(let details):
            return "Failed to open update package (\(details))."
        case .appBundleNotFoundInImage:
            return "Thingsbook.app was not found inside the update package."
        case .invalidAppBundle(let details):
            return "Invalid update bundle (\(details))."
        case .copyToStagingFailed(let details):
            return "Failed to stage update (\(details))."
        case .launchUpdaterFailed(let details):
            return "Failed to launch installer (\(details))."
        }
    }
}

public enum AppUpdateService {
    public static let currentVersion = "3.0.0-alpha.4"
    public static let displayVersion = "3.0.0-alpha.4"
    public static let currentBuildNumber = 77
    public static let expectedBundleIdentifier = "com.thingsbook.macos"
    private static let releasesEndpoint = "https://api.github.com/repos/petrosvassiliou/thingsbook-android/releases?per_page=30"
    private static let rawAlphaManifestEndpoint = "https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/alpha-3.0/macos/PARITY_MANIFEST.json"
    private static let rawBetaManifestEndpoint = "https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/beta-2.0/macos/PARITY_MANIFEST.json"
    private static let rawStableManifestEndpoint = "https://raw.githubusercontent.com/petrosvassiliou/thingsbook-android/main/macos/PARITY_MANIFEST.json"

    public static var currentVersionIsPrerelease: Bool {
        defaultIncludeBeta(for: currentVersion)
    }

    public static func defaultIncludeBeta(for version: String) -> Bool {
        let trimmed = stripLeadingVersionPrefix(version)
        return trimmed.split(separator: "+", maxSplits: 1, omittingEmptySubsequences: false).first?.contains("-") == true
    }

    /// Always enables pre-release updates when the currently running app is itself a pre-release build
    /// (preventing a persisted `false` preference carried over from 1.8.x from stranding beta users),
    /// while returning to the user's saved `requested` preference once promoted to a stable final release.
    public static func resolveEffectiveIncludeBeta(
        requested: Bool?,
        current: String = currentVersion
    ) -> Bool {
        return (requested == true) || defaultIncludeBeta(for: current)
    }

    public static func checkForUpdates(
        current: String = currentVersion,
        includeBeta: Bool? = nil
    ) async throws -> ReleaseUpdateInfo? {
        let effectiveIncludeBeta = resolveEffectiveIncludeBeta(requested: includeBeta, current: current)
        guard let url = URL(string: releasesEndpoint) else { return nil }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 12)
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        req.setValue("Thingsbook-macOS/\(current)", forHTTPHeaderField: "User-Agent")

        var primaryFailure: Error?
        do {
            let (data, res) = try await URLSession.shared.data(for: req)
            if let http = res as? HTTPURLResponse, (200..<300).contains(http.statusCode),
               let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
                return selectEligibleRelease(from: arr, current: current, includeBeta: effectiveIncludeBeta)
            } else if let http = res as? HTTPURLResponse {
                primaryFailure = MacInAppUpdateError.downloadFailed(statusCode: http.statusCode)
            }
        } catch {
            primaryFailure = error
        }

        // Fallback to raw.githubusercontent.com PARITY_MANIFEST.json (immune to unauthenticated GitHub REST API 60 req/hr rate limits)
        var manifestEndpoints: [String] = [rawStableManifestEndpoint]
        if effectiveIncludeBeta {
            manifestEndpoints.insert(rawBetaManifestEndpoint, at: 0)
            manifestEndpoints.insert(rawAlphaManifestEndpoint, at: 0)
        }

        var fallbackSucceeded = false
        var bestFallback: ReleaseUpdateInfo?
        for endpoint in manifestEndpoints {
            guard let rawUrl = URL(string: endpoint) else { continue }
            var rawReq = URLRequest(url: rawUrl, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
            rawReq.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
            rawReq.setValue("Thingsbook-macOS/\(current)", forHTTPHeaderField: "User-Agent")
            if let (rawData, rawRes) = try? await URLSession.shared.data(for: rawReq),
               let rawHttp = rawRes as? HTTPURLResponse,
               (200..<300).contains(rawHttp.statusCode) {
                fallbackSucceeded = true
                if let candidate = parseFallbackParityManifestData(
                    rawData,
                    current: current,
                    includeBeta: effectiveIncludeBeta
                ) {
                    if let existing = bestFallback {
                        if isSemanticNewer(remote: candidate.versionString, current: existing.versionString, includeBeta: true) {
                            bestFallback = candidate
                        }
                    } else {
                        bestFallback = candidate
                    }
                }
            }
        }

        if let bestFallback {
            return bestFallback
        }
        if fallbackSucceeded {
            return nil
        }
        if let primaryFailure {
            throw primaryFailure
        }
        return nil
    }

    public static func parseFallbackParityManifestData(
        _ data: Data,
        current: String = currentVersion,
        includeBeta: Bool? = nil
    ) -> ReleaseUpdateInfo? {
        let effectiveIncludeBeta = resolveEffectiveIncludeBeta(requested: includeBeta, current: current)
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        let rawVer = ((obj["macosVersion"] as? String) ?? (obj["syncedAndroidVersionName"] as? String) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanVer = stripLeadingVersionPrefix(rawVer)
        guard !cleanVer.isEmpty else { return nil }
        let tag = "v\(cleanVer)"
        guard isValidReleaseTag(tag, allowPrerelease: effectiveIncludeBeta),
              isSemanticNewer(remote: cleanVer, current: current, includeBeta: effectiveIncludeBeta) else {
            return nil
        }
        return ReleaseUpdateInfo(
            tagName: tag,
            versionString: cleanVer,
            releaseNotes: "",
            htmlUrl: "https://github.com/petrosvassiliou/thingsbook-android/releases/tag/\(tag)",
            dmgDownloadUrl: "https://github.com/petrosvassiliou/thingsbook-android/releases/download/\(tag)/Thingsbook-macOS-Universal.dmg"
        )
    }

    public static func selectEligibleRelease(
        from releases: [[String: Any]],
        current: String = currentVersion,
        includeBeta: Bool? = nil
    ) -> ReleaseUpdateInfo? {
        let effectiveIncludeBeta = resolveEffectiveIncludeBeta(requested: includeBeta, current: current)
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

        let effectiveIncludeBeta = resolveEffectiveIncludeBeta(requested: includeBeta, current: current)

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

    // MARK: - In-App Automatic Download, Silent Extraction & Relaunch (No Manual DMG Needed)

    /// Resolves the destination `.app` bundle path to replace in-place.
    /// If the app was launched directly from a mounted disk image (`/Volumes/...`), Gatekeeper
    /// AppTranslocation, or a non-`.app` binary, defaults cleanly to `/Applications/Thingsbook.app`.
    public static func resolveInstallTargetAppURL(bundleURL: URL = Bundle.main.bundleURL) -> URL {
        var candidate = bundleURL.standardizedFileURL
        var resolvedAppURL: URL?
        while candidate.path != "/" && !candidate.path.isEmpty {
            if candidate.pathExtension.lowercased() == "app" {
                resolvedAppURL = candidate
                break
            }
            let parent = candidate.deletingLastPathComponent()
            if parent.path == candidate.path { break }
            candidate = parent
        }

        guard let appURL = resolvedAppURL else {
            return URL(fileURLWithPath: "/Applications/Thingsbook.app")
        }

        let path = appURL.path
        if path.hasPrefix("/Volumes/") || path.contains("/AppTranslocation/") {
            return URL(fileURLWithPath: "/Applications/Thingsbook.app")
        }
        return appURL
    }

    /// Validates that an extracted `.app` bundle has a valid `Info.plist`, expected bundle identifier,
    /// and executable binary before replacing the installed app.
    public static func validateExtractedAppBundle(
        at appURL: URL,
        expectedBundleIdentifier: String = expectedBundleIdentifier
    ) throws {
        guard appURL.pathExtension.lowercased() == "app" else {
            throw MacInAppUpdateError.invalidAppBundle(details: "Not an .app bundle")
        }
        let infoPlistURL = appURL.appendingPathComponent("Contents/Info.plist")
        guard FileManager.default.fileExists(atPath: infoPlistURL.path),
              let plistData = try? Data(contentsOf: infoPlistURL),
              let plistObj = try? PropertyListSerialization.propertyList(from: plistData, options: [], format: nil),
              let plist = plistObj as? [String: Any] else {
            throw MacInAppUpdateError.invalidAppBundle(details: "Missing or unreadable Info.plist")
        }

        let bundleId = (plist["CFBundleIdentifier"] as? String) ?? ""
        guard bundleId == expectedBundleIdentifier else {
            throw MacInAppUpdateError.invalidAppBundle(details: "Unexpected bundle identifier '\(bundleId)'")
        }

        let executableName = ((plist["CFBundleExecutable"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines))
            .flatMap { $0.isEmpty ? nil : $0 } ?? "Thingsbook"
        let executableURL = appURL
            .appendingPathComponent("Contents/MacOS", isDirectory: true)
            .appendingPathComponent(executableName)
        guard FileManager.default.isExecutableFile(atPath: executableURL.path) else {
            throw MacInAppUpdateError.invalidAppBundle(details: "Executable '\(executableName)' is missing")
        }
    }

    public static func shellSingleQuoted(_ value: String) -> String {
        "'" + value.replacingOccurrences(of: "'", with: "'\\''") + "'"
    }

    /// Builds a self-cleaning shell script that waits for the running process (`oldPID`) to exit cleanly,
    /// atomically swaps the staged `.app` into `targetAppPath` (with automatic rollback if `ditto` fails),
    /// clears Gatekeeper quarantine attributes, deletes the staging folder, and relaunches Thingsbook.
    public static func buildAtomicSwapAndRelaunchScript(
        oldPID: Int32,
        stagedAppPath: String,
        targetAppPath: String,
        stagingRootPath: String,
        requiresAdmin: Bool
    ) -> String {
        let targetParent = URL(fileURLWithPath: targetAppPath).deletingLastPathComponent().path
        let qStaged = shellSingleQuoted(stagedAppPath)
        let qTarget = shellSingleQuoted(targetAppPath)
        let qParent = shellSingleQuoted(targetParent)
        let qStagingRoot = shellSingleQuoted(stagingRootPath)

        var lines: [String] = [
            "#!/bin/bash",
            "set -u",
            "OLD_PID=\(oldPID)",
            "STAGED_APP=\(qStaged)",
            "TARGET_APP=\(qTarget)",
            "TARGET_PARENT=\(qParent)",
            "STAGING_ROOT=\(qStagingRoot)",
            "",
            "# Wait up to 12s for Thingsbook to flush SQLite WAL and terminate cleanly",
            "for _ in $(seq 1 80); do",
            "  if ! kill -0 \"$OLD_PID\" 2>/dev/null; then",
            "    break",
            "  fi",
            "  sleep 0.15",
            "done",
            ""
        ]

        if requiresAdmin {
            let privilegedCmd = "mkdir -p \(qParent) && rm -rf \(qTarget) && /usr/bin/ditto \(qStaged) \(qTarget) && (/usr/bin/xattr -cr \(qTarget) 2>/dev/null || true)"
            let escapedForAppleScript = privilegedCmd
                .replacingOccurrences(of: "\\", with: "\\\\")
                .replacingOccurrences(of: "\"", with: "\\\"")
            lines.append("/usr/bin/osascript -e \"do shell script \\\"\(escapedForAppleScript)\\\" with administrator privileges\"")
            lines.append("STATUS=$?")
            lines.append("rm -rf \"$STAGING_ROOT\" 2>/dev/null || true")
            lines.append("if [ \"$STATUS\" -eq 0 ]; then")
            lines.append("  /usr/bin/open \"$TARGET_APP\"")
            lines.append("fi")
        } else {
            lines.append("mkdir -p \"$TARGET_PARENT\" 2>/dev/null || true")
            lines.append("BACKUP_APP=\"$STAGING_ROOT/rollback-Thingsbook.app\"")
            lines.append("rm -rf \"$BACKUP_APP\" 2>/dev/null || true")
            lines.append("if [ -d \"$TARGET_APP\" ]; then")
            lines.append("  mv \"$TARGET_APP\" \"$BACKUP_APP\" 2>/dev/null || rm -rf \"$TARGET_APP\"")
            lines.append("fi")
            lines.append("if /usr/bin/ditto \"$STAGED_APP\" \"$TARGET_APP\"; then")
            lines.append("  /usr/bin/xattr -cr \"$TARGET_APP\" 2>/dev/null || true")
            lines.append("  rm -rf \"$STAGING_ROOT\" 2>/dev/null || true")
            lines.append("  /usr/bin/open \"$TARGET_APP\"")
            lines.append("else")
            lines.append("  if [ -d \"$BACKUP_APP\" ] && [ ! -d \"$TARGET_APP\" ]; then")
            lines.append("    mv \"$BACKUP_APP\" \"$TARGET_APP\" 2>/dev/null || true")
            lines.append("  fi")
            lines.append("  rm -rf \"$STAGING_ROOT\" 2>/dev/null || true")
            lines.append("  /usr/bin/open \"$TARGET_APP\" 2>/dev/null || true")
            lines.append("fi")
        }

        lines.append("")
        return lines.joined(separator: "\n")
    }

    /// Streams the release archive in-app with live byte progress, invisibly mounts and extracts
    /// `Thingsbook.app` into a private staging directory, clears quarantine attributes, verifies its
    /// bundle & code signature, and deletes the temporary archive so the user never handles a `.dmg`.
    public static func downloadAndStageUpdate(
        _ info: ReleaseUpdateInfo,
        onPhaseChange: @escaping @Sendable (MacInAppUpdatePhase) -> Void
    ) async throws -> (stagedAppURL: URL, stagingRootURL: URL) {
        guard let rawUrl = info.dmgDownloadUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !rawUrl.isEmpty,
              let downloadURL = URL(string: rawUrl) else {
            throw MacInAppUpdateError.missingDownloadUrl
        }

        let fm = FileManager.default
        let stagingRootURL = fm.temporaryDirectory
            .appendingPathComponent("thingsbook-autoupdate-\(UUID().uuidString)", isDirectory: true)
        try fm.createDirectory(at: stagingRootURL, withIntermediateDirectories: true)

        let dmgURL = stagingRootURL.appendingPathComponent("Thingsbook-Update.dmg")
        let mountURL = stagingRootURL.appendingPathComponent("mnt", isDirectory: true)
        let stagedAppURL = stagingRootURL.appendingPathComponent("Thingsbook.app", isDirectory: true)

        do {
            onPhaseChange(.downloading(version: info.versionString, progress: 0.0))

            var req = URLRequest(url: downloadURL)
            req.timeoutInterval = 120
            let (asyncBytes, response) = try await URLSession.shared.bytes(for: req)
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                throw MacInAppUpdateError.downloadFailed(statusCode: http.statusCode)
            }

            let expectedBytes = response.expectedContentLength
            fm.createFile(atPath: dmgURL.path, contents: nil)
            let handle = try FileHandle(forWritingTo: dmgURL)
            defer { try? handle.close() }

            var buffer = Data()
            buffer.reserveCapacity(65_536)
            var receivedBytes: Int64 = 0
            var lastReportedPercent = -1
            var lastReportTime = CFAbsoluteTimeGetCurrent()

            for try await byte in asyncBytes {
                buffer.append(byte)
                if buffer.count >= 65_536 {
                    try Task.checkCancellation()
                    try handle.write(contentsOf: buffer)
                    receivedBytes += Int64(buffer.count)
                    buffer.removeAll(keepingCapacity: true)

                    let now = CFAbsoluteTimeGetCurrent()
                    if expectedBytes > 0 {
                        let ratio = min(1.0, max(0.0, Double(receivedBytes) / Double(expectedBytes)))
                        let pct = Int((ratio * 100.0).rounded(.down))
                        if pct > lastReportedPercent && (now - lastReportTime >= 0.06 || pct == 100) {
                            lastReportedPercent = pct
                            lastReportTime = now
                            onPhaseChange(.downloading(version: info.versionString, progress: ratio))
                        }
                    } else if now - lastReportTime >= 0.2 {
                        lastReportTime = now
                        onPhaseChange(.downloading(version: info.versionString, progress: nil))
                    }
                }
            }

            if !buffer.isEmpty {
                try Task.checkCancellation()
                try handle.write(contentsOf: buffer)
                receivedBytes += Int64(buffer.count)
                buffer.removeAll(keepingCapacity: false)
            }
            try handle.close()

            onPhaseChange(.downloading(version: info.versionString, progress: 1.0))
            onPhaseChange(.extracting(version: info.versionString))

            try fm.createDirectory(at: mountURL, withIntermediateDirectories: true)
            let attachResult = try runProcess(
                executablePath: "/usr/bin/hdiutil",
                arguments: ["attach", dmgURL.path, "-mountpoint", mountURL.path, "-nobrowse", "-readonly", "-quiet"]
            )
            guard attachResult.exitCode == 0 else {
                throw MacInAppUpdateError.mountFailed(details: attachResult.stderr.isEmpty ? "exit \(attachResult.exitCode)" : attachResult.stderr)
            }

            defer {
                _ = try? runProcess(
                    executablePath: "/usr/bin/hdiutil",
                    arguments: ["detach", mountURL.path, "-force", "-quiet"]
                )
            }

            let candidateInMount = mountURL.appendingPathComponent("Thingsbook.app", isDirectory: true)
            let sourceAppURL: URL
            if fm.fileExists(atPath: candidateInMount.path) {
                sourceAppURL = candidateInMount
            } else if let items = try? fm.contentsOfDirectory(at: mountURL, includingPropertiesForKeys: nil),
                      let found = items.first(where: { $0.pathExtension.lowercased() == "app" }) {
                sourceAppURL = found
            } else {
                throw MacInAppUpdateError.appBundleNotFoundInImage
            }

            try validateExtractedAppBundle(at: sourceAppURL)

            let dittoResult = try runProcess(
                executablePath: "/usr/bin/ditto",
                arguments: [sourceAppURL.path, stagedAppURL.path]
            )
            guard dittoResult.exitCode == 0 else {
                throw MacInAppUpdateError.copyToStagingFailed(details: dittoResult.stderr.isEmpty ? "ditto exit \(dittoResult.exitCode)" : dittoResult.stderr)
            }

            _ = try? runProcess(
                executablePath: "/usr/bin/xattr",
                arguments: ["-cr", stagedAppURL.path]
            )

            if fm.isExecutableFile(atPath: "/usr/bin/codesign") {
                let verifyResult = try runProcess(
                    executablePath: "/usr/bin/codesign",
                    arguments: ["--verify", "--deep", "--strict", stagedAppURL.path]
                )
                guard verifyResult.exitCode == 0 else {
                    throw MacInAppUpdateError.invalidAppBundle(details: "Code signature verification failed")
                }
            }

            try validateExtractedAppBundle(at: stagedAppURL)
            try? fm.removeItem(at: dmgURL)

            onPhaseChange(.readyToInstall(
                version: info.versionString,
                stagedAppPath: stagedAppURL.path,
                stagingRootPath: stagingRootURL.path
            ))
            return (stagedAppURL, stagingRootURL)
        } catch {
            try? fm.removeItem(at: stagingRootURL)
            throw error
        }
    }

    /// Launches the detached atomic swap-and-relaunch script and terminates the current Thingsbook process
    /// so the updated `.app` replaces it cleanly and reopens within ~1 second.
    @MainActor
    public static func installStagedUpdateAndRelaunch(
        stagedAppURL: URL,
        stagingRootURL: URL,
        targetAppURL: URL? = nil
    ) throws {
        try validateExtractedAppBundle(at: stagedAppURL)

        let destinationAppURL = targetAppURL ?? resolveInstallTargetAppURL()
        let parentDirURL = destinationAppURL.deletingLastPathComponent()
        let fm = FileManager.default
        let isParentWritable = fm.fileExists(atPath: parentDirURL.path)
            ? fm.isWritableFile(atPath: parentDirURL.path)
            : fm.isWritableFile(atPath: "/Applications")
        let isExistingBundleWritable = !fm.fileExists(atPath: destinationAppURL.path)
            || fm.isWritableFile(atPath: destinationAppURL.path)
        let requiresAdmin = !(isParentWritable && isExistingBundleWritable)

        let scriptContent = buildAtomicSwapAndRelaunchScript(
            oldPID: ProcessInfo.processInfo.processIdentifier,
            stagedAppPath: stagedAppURL.path,
            targetAppPath: destinationAppURL.path,
            stagingRootPath: stagingRootURL.path,
            requiresAdmin: requiresAdmin
        )

        let scriptURL = stagingRootURL.appendingPathComponent("apply-update.sh")
        try scriptContent.write(to: scriptURL, atomically: true, encoding: .utf8)
        try fm.setAttributes([.posixPermissions: 0o700], ofItemAtPath: scriptURL.path)

        let launcher = Process()
        launcher.executableURL = URL(fileURLWithPath: "/bin/bash")
        launcher.arguments = ["-c", "/usr/bin/nohup /bin/bash \(shellSingleQuoted(scriptURL.path)) >/dev/null 2>&1 &"]
        launcher.standardInput = FileHandle.nullDevice
        launcher.standardOutput = FileHandle.nullDevice
        launcher.standardError = FileHandle.nullDevice

        do {
            try launcher.run()
            launcher.waitUntilExit()
            if launcher.terminationStatus != 0 {
                throw MacInAppUpdateError.launchUpdaterFailed(details: "exit \(launcher.terminationStatus)")
            }
        } catch let err as MacInAppUpdateError {
            throw err
        } catch {
            throw MacInAppUpdateError.launchUpdaterFailed(details: error.localizedDescription)
        }

        UserDefaults.standard.synchronize()
        NSApplication.shared.terminate(nil)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) {
            Darwin.exit(0)
        }
    }

    private static func runProcess(
        executablePath: String,
        arguments: [String]
    ) throws -> (exitCode: Int32, stdout: String, stderr: String) {
        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: executablePath)
        proc.arguments = arguments
        proc.standardInput = FileHandle.nullDevice

        let outPipe = Pipe()
        let errPipe = Pipe()
        proc.standardOutput = outPipe
        proc.standardError = errPipe

        try proc.run()
        let outData = outPipe.fileHandleForReading.readDataToEndOfFile()
        let errData = errPipe.fileHandleForReading.readDataToEndOfFile()
        proc.waitUntilExit()

        let outStr = String(data: outData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let errStr = String(data: errData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return (proc.terminationStatus, outStr, errStr)
    }

    public static func openReleaseNotesPage(_ info: ReleaseUpdateInfo) {
        if let url = URL(string: info.htmlUrl) {
            NSWorkspace.shared.open(url)
        }
    }

    public static func openUpdatePage(_ info: ReleaseUpdateInfo) {
        openReleaseNotesPage(info)
    }
}
