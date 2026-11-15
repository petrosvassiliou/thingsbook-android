package com.culturedcode.things3.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.culturedcode.things3.BuildConfig
import com.culturedcode.things3.domain.util.runCatchingCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

data class AppReleaseInfo(
    val versionName: String,
    val tagName: String,
    val title: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String
)

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState

    data class Checking(val isManual: Boolean) : AppUpdateUiState

    data class UpToDate(val currentVersion: String) : AppUpdateUiState

    data class UpdateAvailable(
        val release: AppReleaseInfo,
        val showPromptBanner: Boolean = true
    ) : AppUpdateUiState

    data class Downloading(
        val release: AppReleaseInfo,
        val progressPercent: Int?,
        val downloadedBytes: Long,
        val totalBytes: Long?
    ) : AppUpdateUiState

    data class ReadyToInstall(
        val release: AppReleaseInfo,
        val apkFilePath: String,
        val needsInstallPermission: Boolean = false
    ) : AppUpdateUiState

    data class Error(
        val message: String,
        val fallbackReleaseUrl: String? = null
    ) : AppUpdateUiState
}

@Serializable
data class GitHubReleaseAssetDto(
    val name: String = "",
    val browser_download_url: String = "",
    val content_type: String = ""
)

@Serializable
data class GitHubReleaseDto(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val html_url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAssetDto> = emptyList()
)

object AppUpdateParser {

    const val GITHUB_OWNER = "petrosvassiliou"
    const val GITHUB_REPO = "thingsbook-android"
    const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val RAW_GRADLE_URL = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/app/build.gradle.kts"
    const val RAW_README_URL = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/README.md"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val apkUrlInTextRegex = Regex(
        """https://(?:github\.com|raw\.githubusercontent\.com|objects\.githubusercontent\.com)/[^\s\)\]"'<>]+\.apk""",
        RegexOption.IGNORE_CASE
    )

    private val gradleVersionNameRegex = Regex("""versionName\s*=\s*"([^"]+)"""")

    fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V").trim()
    }

    /**
     * Compares two semantic version strings (e.g. "1.6.9" vs "1.6.10", "v1.8.0+1" vs "1.8.0", "1.7.0-beta.1" vs "1.7.0").
     * Returns > 0 if [v1] > [v2], < 0 if [v1] < [v2], and 0 if equal.
     */
    fun compareVersions(v1: String, v2: String): Int {
        val norm1 = normalizeVersion(v1)
        val norm2 = normalizeVersion(v2)

        val clean1 = norm1.substringBefore('+')
        val clean2 = norm2.substringBefore('+')
        val build1 = norm1.substringAfter('+', missingDelimiterValue = "").trim()
        val build2 = norm2.substringAfter('+', missingDelimiterValue = "").trim()

        val core1 = clean1.substringBefore('-')
        val core2 = clean2.substringBefore('-')
        val pre1 = clean1.substringAfter('-', missingDelimiterValue = "")
        val pre2 = clean2.substringAfter('-', missingDelimiterValue = "")

        val nums1 = core1.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val nums2 = core2.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(nums1.size, nums2.size).coerceAtLeast(3)

        for (i in 0 until maxLen) {
            val n1 = nums1.getOrElse(i) { 0 }
            val n2 = nums2.getOrElse(i) { 0 }
            if (n1 != n2) return n1.compareTo(n2)
        }

        if (pre1.isNotEmpty() || pre2.isNotEmpty()) {
            return when {
                pre1.isEmpty() -> 1  // Final release > pre-release
                pre2.isEmpty() -> -1
                else -> compareDotSeparatedTokens(pre1, pre2)
            }
        }

        return when {
            build1.isEmpty() && build2.isEmpty() -> 0
            build1.isNotEmpty() && build2.isEmpty() -> 1
            build1.isEmpty() && build2.isNotEmpty() -> -1
            else -> compareDotSeparatedTokens(build1, build2)
        }
    }

    private fun compareDotSeparatedTokens(a: String, b: String): Int {
        val partsA = a.split('.')
        val partsB = b.split('.')
        val minLen = minOf(partsA.size, partsB.size)
        for (i in 0 until minLen) {
            val segA = partsA[i]
            val segB = partsB[i]
            val numA = segA.toLongOrNull()
            val numB = segB.toLongOrNull()
            val cmp = when {
                numA != null && numB != null -> numA.compareTo(numB)
                else -> segA.compareTo(segB, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return partsA.size.compareTo(partsB.size)
    }

    fun isNewerVersion(currentVersion: String, candidateVersion: String): Boolean {
        val normalizedCurrent = normalizeVersion(currentVersion)
        val normalizedCandidate = normalizeVersion(candidateVersion)
        if (normalizedCandidate.isBlank() || !normalizedCandidate.first().isDigit()) return false
        if (!normalizedCurrent.substringBefore('+').contains('-') &&
            normalizedCandidate.substringBefore('+').contains('-')
        ) {
            return false
        }
        return compareVersions(normalizedCandidate, normalizedCurrent) > 0
    }

    fun buildDefaultRawApkUrl(cleanVersion: String): String {
        return "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/v$cleanVersion/Thingsbook-v$cleanVersion-GTD-Full.apk"
    }

    fun parseGitHubReleaseJson(rawJson: String): AppReleaseInfo {
        val dto = json.decodeFromString<GitHubReleaseDto>(rawJson)
        if (dto.draft || dto.prerelease) {
            throw IllegalArgumentException("Skipping draft or pre-release")
        }
        val cleanVersion = normalizeVersion(dto.tag_name)
        require(cleanVersion.isNotBlank() && cleanVersion.first().isDigit()) {
            "Invalid release tag '${dto.tag_name}'"
        }

        val assetApkUrl = dto.assets
            .firstOrNull { asset ->
                asset.browser_download_url.endsWith(".apk", ignoreCase = true) ||
                    asset.name.endsWith(".apk", ignoreCase = true)
            }
            ?.browser_download_url
            ?.takeIf { it.startsWith("https://") }

        val bodyApkUrl = apkUrlInTextRegex.find(dto.body)?.value

        val resolvedApkUrl = assetApkUrl
            ?: bodyApkUrl
            ?: buildDefaultRawApkUrl(cleanVersion)

        val resolvedPageUrl = dto.html_url.takeIf { it.startsWith("https://") }
            ?: "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/v$cleanVersion"

        val cleanedNotes = cleanReleaseNotes(dto.body)
        val resolvedTitle = dto.name.trim().ifBlank { "Thingsbook v$cleanVersion" }

        return AppReleaseInfo(
            versionName = cleanVersion,
            tagName = dto.tag_name.ifBlank { "v$cleanVersion" },
            title = resolvedTitle,
            releaseNotes = cleanedNotes,
            apkDownloadUrl = resolvedApkUrl,
            releasePageUrl = resolvedPageUrl
        )
    }

    fun parseFallbackGradleAndReadme(gradleKts: String, readmeMarkdown: String?): AppReleaseInfo {
        val versionMatch = gradleVersionNameRegex.find(gradleKts)?.groupValues?.getOrNull(1)
            ?: throw IOException("Could not determine latest version from repository")
        val cleanVersion = normalizeVersion(versionMatch)
        require(cleanVersion.isNotBlank() && cleanVersion.first().isDigit()) {
            "Invalid fallback version '$versionMatch'"
        }
        if (cleanVersion.substringBefore('+').contains('-')) {
            throw IOException("Skipping pre-release fallback version")
        }

        val readmeApkUrl = readmeMarkdown?.let { apkUrlInTextRegex.find(it)?.value }
        val apkUrl = readmeApkUrl ?: buildDefaultRawApkUrl(cleanVersion)

        return AppReleaseInfo(
            versionName = cleanVersion,
            tagName = "v$cleanVersion",
            title = "Thingsbook v$cleanVersion",
            releaseNotes = "",
            apkDownloadUrl = apkUrl,
            releasePageUrl = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/v$cleanVersion"
        )
    }

    fun cleanReleaseNotes(markdown: String): String {
        if (markdown.isBlank()) return ""
        val lines = markdown.lines()
        val kept = mutableListOf<String>()
        var skipDownloadSection = false

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("## Thingsbook", ignoreCase = true)) continue
            if (trimmed.startsWith("### Direkt-Download", ignoreCase = true) ||
                trimmed.startsWith("### Direct Download", ignoreCase = true)
            ) {
                skipDownloadSection = true
                continue
            }
            if (trimmed.startsWith("### ")) {
                skipDownloadSection = false
                continue
            }
            if (skipDownloadSection) continue
            if ((trimmed.contains("-full.apk", ignoreCase = true) || trimmed.contains("-gtd-full.apk", ignoreCase = true)) &&
                trimmed.contains("https://", ignoreCase = true)
            ) continue

            val cleanedLine = trimmed
                .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
                .replace(Regex("""`([^`]+)`"""), "$1")
            if (cleanedLine.isNotEmpty() || (kept.isNotEmpty() && kept.last().isNotEmpty())) {
                kept.add(cleanedLine)
            }
        }
        return kept.joinToString("\n").trim()
    }

    fun isValidApkHeader(bytes: ByteArray, readCount: Int): Boolean {
        return readCount >= 4 &&
            bytes[0] == 0x50.toByte() && // 'P'
            bytes[1] == 0x4B.toByte() && // 'K'
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
    }
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun checkForUpdate(): AppReleaseInfo = withContext(Dispatchers.IO) {
        val primaryAttempt = runCatchingCancellable {
            val body = httpGetTextFollowingRedirects(
                url = AppUpdateParser.LATEST_RELEASE_API_URL,
                acceptHeader = "application/vnd.github+json"
            )
            AppUpdateParser.parseGitHubReleaseJson(body)
        }

        primaryAttempt.getOrElse { primaryError ->
            // Fallback to raw main branch files (immune to unauthenticated GitHub REST API rate limits)
            val gradleContent = runCatchingCancellable {
                httpGetTextFollowingRedirects(url = AppUpdateParser.RAW_GRADLE_URL)
            }.getOrElse { throw primaryError }

            val readmeContent = runCatchingCancellable {
                httpGetTextFollowingRedirects(url = AppUpdateParser.RAW_README_URL)
            }.getOrNull()

            AppUpdateParser.parseFallbackGradleAndReadme(gradleContent, readmeContent)
        }
    }

    /**
     * Opens the direct APK download URL in the system browser (e.g. Chrome) so the browser handles
     * the APK download and installation without requiring REQUEST_INSTALL_PACKAGES in Thingsbook.
     */
    fun openApkDownloadInBrowser(release: AppReleaseInfo): Result<Unit> = runCatching {
        val targetUrl = release.apkDownloadUrl.takeIf { it.startsWith("https://") }
            ?: release.releasePageUrl.takeIf { it.startsWith("https://") }
            ?: AppUpdateParser.RELEASES_PAGE_URL
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(viewIntent)
    }

    fun openReleasePageInBrowser(url: String) {
        val safeUrl = url.takeIf { it.startsWith("https://") } ?: AppUpdateParser.RELEASES_PAGE_URL
        runCatching {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        }
    }

    private fun httpGetTextFollowingRedirects(
        url: String,
        acceptHeader: String = "text/plain, application/json, */*"
    ): String {
        val conn = openConnectionFollowingRedirects(
            initialUrl = url,
            acceptHeader = acceptHeader,
            connectTimeoutMs = 12_000,
            readTimeoutMs = 15_000
        )
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code from $url")
            }
            return conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    internal fun openConnectionFollowingRedirects(
        initialUrl: String,
        acceptHeader: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        var currentUrl = URL(initialUrl)
        if (!currentUrl.protocol.equals("https", ignoreCase = true)) {
            throw IOException("Refusing non-HTTPS update URL: ${currentUrl.protocol}")
        }
        repeat(MAX_REDIRECT_HOPS) {
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                setRequestProperty("User-Agent", "Thingsbook-Android/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", acceptHeader)
            }
            val code = conn.responseCode
            if (code in REDIRECT_STATUS_CODES) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect HTTP $code without Location header")
                }
                val nextUrl = URL(currentUrl, location)
                if (!nextUrl.protocol.equals("https", ignoreCase = true)) {
                    throw IOException("Refusing non-HTTPS redirect URL: ${nextUrl.protocol}")
                }
                currentUrl = nextUrl
            } else {
                return conn
            }
        }
        throw IOException("Too many HTTP redirects while reaching $initialUrl")
    }

    companion object {
        private const val MAX_REDIRECT_HOPS = 8
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}
