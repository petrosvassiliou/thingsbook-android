package com.culturedcode.things3.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AppUpdateParserTest {

    @Test
    fun `semantic version comparison handles multi-digit patch, minor, major and pre-releases`() {
        assertTrue(AppUpdateParser.isNewerVersion("1.6.9", "1.6.10"))
        assertTrue(AppUpdateParser.isNewerVersion("1.6.11", "v1.6.12"))
        assertTrue(AppUpdateParser.isNewerVersion("1.6.12", "1.7.0"))
        assertTrue(AppUpdateParser.isNewerVersion("1.6.12", "2.0.0"))
        assertFalse(AppUpdateParser.isNewerVersion("1.6.12", "1.6.12"))
        assertFalse(AppUpdateParser.isNewerVersion("1.6.12", "v1.6.11"))
        assertFalse(AppUpdateParser.isNewerVersion("1.6.12", ""))
        assertTrue(AppUpdateParser.isNewerVersion("1.7.0-beta.1", "1.7.0"))
        assertFalse(AppUpdateParser.isNewerVersion("1.7.0", "1.7.0-rc.1"))
    }

    @Test
    fun `stable channel strictly isolates against 2_0 pre-releases and supports SemVer +build metadata`() {
        // Stable v1.8.1 MUST never prompt for 2.0.0 pre-releases
        assertFalse(AppUpdateParser.isNewerVersion("1.8.1", "2.0.0-beta.1"))
        assertFalse(AppUpdateParser.isNewerVersion("1.8.1", "v2.0.0-alpha.1"))
        assertFalse(AppUpdateParser.isNewerVersion("1.8.1", "2.0.0-rc.1"))
        assertFalse(AppUpdateParser.isNewerVersion("1.8.1+1", "2.0.0-beta.2+5"))

        // Stable releases still upgrade cleanly
        assertTrue(AppUpdateParser.isNewerVersion("1.8.1", "1.8.2"))
        assertTrue(AppUpdateParser.isNewerVersion("1.8.1", "2.0.0"))

        // Pre-release channel users can still move between pre-releases
        assertTrue(AppUpdateParser.isNewerVersion("2.0.0-beta.1", "2.0.0-beta.2"))
        assertTrue(AppUpdateParser.isNewerVersion("2.0.0-beta.2", "2.0.0"))

        // SemVer +build metadata ordering when core segments match on stable releases
        assertTrue(AppUpdateParser.compareVersions("1.8.0+1", "1.8.0") > 0)
        assertTrue(AppUpdateParser.compareVersions("1.8.1", "1.8.0+1") > 0)
        assertTrue(AppUpdateParser.compareVersions("1.8.1+1", "1.8.1") > 0)
        assertTrue(AppUpdateParser.isNewerVersion("1.8.1", "1.8.1+1"))
        assertFalse(AppUpdateParser.isNewerVersion("1.8.1+2", "1.8.1+1"))
    }

    @Test
    fun `parseGitHubReleaseJson rejects draft and prerelease payloads`() {
        val draftJson = """{"tag_name":"v1.8.2","draft":true,"prerelease":false,"body":"Draft"}"""
        val prereleaseJson = """{"tag_name":"v2.0.0-beta.1","draft":false,"prerelease":true,"body":"Beta"}"""

        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateParser.parseGitHubReleaseJson(draftJson)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateParser.parseGitHubReleaseJson(prereleaseJson)
        }
    }

    @Test
    fun `parseGitHubReleaseJson extracts markdown raw APK link and cleans release notes`() {
        val sampleJson = """
            {
              "tag_name": "v1.6.12",
              "name": "Thingsbook v1.6.12 — Auto-Update & Search for Updates",
              "html_url": "https://github.com/petrosvassiliou/thingsbook-android/releases/tag/v1.6.12",
              "body": "## Thingsbook v1.6.12\n\n### Direkt-Download (APK)\n- **[Thingsbook-v1.6.12-full.apk herunterladen](https://github.com/petrosvassiliou/thingsbook-android/raw/main/Thingsbook-v1.6.12-full.apk)**\n\n### Neu in v1.6.12\n- **Auto-Update:** Prüft beim Start auf neue Versionen.\n- **Search for Updates:** Manuelle Suche in den Einstellungen.",
              "assets": []
            }
        """.trimIndent()

        val parsed = AppUpdateParser.parseGitHubReleaseJson(sampleJson)
        assertEquals("1.6.12", parsed.versionName)
        assertEquals("v1.6.12", parsed.tagName)
        assertEquals(
            "https://github.com/petrosvassiliou/thingsbook-android/raw/main/Thingsbook-v1.6.12-full.apk",
            parsed.apkDownloadUrl
        )
        assertTrue(parsed.releaseNotes.contains("Auto-Update:"))
        assertFalse(parsed.releaseNotes.contains("-full.apk"))
    }

    @Test
    fun `parseGitHubReleaseJson prefers attached APK asset when present and falls back to GTD-Full convention otherwise`() {
        val withAssetJson = """
            {
              "tag_name": "v1.7.0",
              "name": "Thingsbook v1.7.0",
              "html_url": "https://github.com/petrosvassiliou/thingsbook-android/releases/tag/v1.7.0",
              "body": "Release notes here",
              "assets": [
                {
                  "name": "Thingsbook-v1.7.0-full.apk",
                  "browser_download_url": "https://github.com/petrosvassiliou/thingsbook-android/releases/download/v1.7.0/Thingsbook-v1.7.0-full.apk",
                  "content_type": "application/vnd.android.package-archive"
                }
              ]
            }
        """.trimIndent()

        val parsedAsset = AppUpdateParser.parseGitHubReleaseJson(withAssetJson)
        assertEquals(
            "https://github.com/petrosvassiliou/thingsbook-android/releases/download/v1.7.0/Thingsbook-v1.7.0-full.apk",
            parsedAsset.apkDownloadUrl
        )

        val bareJson = """{"tag_name":"v1.7.1","body":"Fixes"}"""
        val parsedBare = AppUpdateParser.parseGitHubReleaseJson(bareJson)
        assertEquals("1.7.1", parsedBare.versionName)
        assertEquals(
            "https://github.com/petrosvassiliou/thingsbook-android/releases/download/v1.7.1/Thingsbook-v1.7.1-GTD-Full.apk",
            parsedBare.apkDownloadUrl
        )
        assertEquals(
            "https://github.com/petrosvassiliou/thingsbook-android/releases/download/v1.8.1/Thingsbook-v1.8.1-GTD-Full.apk",
            AppUpdateParser.buildDefaultRawApkUrl("1.8.1")
        )
    }

    @Test
    fun `parseFallbackGradleAndReadme extracts stable versionName and rejects pre-release fallback`() {
        val gradle = """
            defaultConfig {
                versionCode = 44
                versionName = "1.8.1"
            }
        """.trimIndent()
        val readme = "Download: https://github.com/petrosvassiliou/thingsbook-android/releases/download/v1.8.1/Thingsbook-v1.8.1-GTD-Full.apk"

        val fallback = AppUpdateParser.parseFallbackGradleAndReadme(gradle, readme)
        assertEquals("1.8.1", fallback.versionName)
        assertEquals(
            "https://github.com/petrosvassiliou/thingsbook-android/releases/download/v1.8.1/Thingsbook-v1.8.1-GTD-Full.apk",
            fallback.apkDownloadUrl
        )

        val betaGradle = """
            defaultConfig {
                versionCode = 50
                versionName = "2.0.0-beta.1"
            }
        """.trimIndent()
        assertThrows(IOException::class.java) {
            AppUpdateParser.parseFallbackGradleAndReadme(betaGradle, null)
        }
    }

    @Test
    fun `isValidApkHeader checks ZIP magic bytes`() {
        val validZip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14)
        val htmlPage = "<!DOCTYPE html>".toByteArray()
        assertTrue(AppUpdateParser.isValidApkHeader(validZip, validZip.size))
        assertFalse(AppUpdateParser.isValidApkHeader(htmlPage, htmlPage.size))
        assertFalse(AppUpdateParser.isValidApkHeader(validZip, 3))
    }
}

