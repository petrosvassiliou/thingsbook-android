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
    public static let currentVersion = "1.7.0"
    public static let currentBuildNumber = 41
    private static let releasesEndpoint = "https://api.github.com/repos/petrosvassiliou/thingsbook-android/releases?per_page=10"

    public static func checkForUpdates(current: String = currentVersion) async throws -> ReleaseUpdateInfo? {
        guard let url = URL(string: releasesEndpoint) else { return nil }
        var req = URLRequest(url: url)
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.timeoutInterval = 10

        let (data, res) = try await URLSession.shared.data(for: req)
        guard let http = res as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }

        for rel in arr {
            if (rel["draft"] as? Bool) == true || (rel["prerelease"] as? Bool) == true {
                continue
            }
            guard let tag = rel["tag_name"] as? String else { continue }
            let cleanRemote = tag.trimmingCharacters(in: CharacterSet(charactersIn: "vV "))
            if isSemanticNewer(remote: cleanRemote, current: current) {
                let assets = (rel["assets"] as? [[String: Any]]) ?? []
                let dmgAsset = assets.first { a in
                    let name = ((a["name"] as? String) ?? "").lowercased()
                    return name.hasSuffix(".dmg") || (name.contains("macos") && name.hasSuffix(".zip"))
                }
                let downloadUrl = dmgAsset?["browser_download_url"] as? String
                let htmlUrl = (rel["html_url"] as? String) ?? "https://github.com/petrosvassiliou/thingsbook-android/releases"
                let body = (rel["body"] as? String) ?? ""
                return ReleaseUpdateInfo(
                    tagName: tag,
                    versionString: cleanRemote,
                    releaseNotes: body,
                    htmlUrl: htmlUrl,
                    dmgDownloadUrl: downloadUrl
                )
            }
        }
        return nil
    }

    public static func isSemanticNewer(remote: String, current: String) -> Bool {
        let rParts = remote.split(separator: ".").map { Int($0.filter(\.isNumber)) ?? 0 }
        let cParts = current.split(separator: ".").map { Int($0.filter(\.isNumber)) ?? 0 }
        let count = max(rParts.count, cParts.count)
        for i in 0..<count {
            let rv = i < rParts.count ? rParts[i] : 0
            let cv = i < cParts.count ? cParts[i] : 0
            if rv > cv { return true }
            if rv < cv { return false }
        }
        return false
    }

    public static func openUpdatePage(_ info: ReleaseUpdateInfo) {
        let urlStr = info.dmgDownloadUrl ?? info.htmlUrl
        if let url = URL(string: urlStr) {
            NSWorkspace.shared.open(url)
        }
    }
}
