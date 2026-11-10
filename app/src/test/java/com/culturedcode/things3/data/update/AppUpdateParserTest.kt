package com.culturedcode.things3.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `parseGitHubReleaseJson prefers attached APK asset when present and falls back to convention otherwise`() {
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
            "https://github.com/petrosvassiliou/thingsbook-android/raw/main/Thingsbook-v1.7.1-full.apk",
            parsedBare.apkDownloadUrl
        )
    }

    @Test
    fun `parseFallbackGradleAndReadme extracts versionName and APK URL`() {
        val gradle = """
            defaultConfig {
                versionCode = 29
                versionName = "1.6.12"
            }
        """.trimIndent()
        val readme = "Download: https://github.com/petrosvassiliou/thingsbook-android/raw/main/Thingsbook-v1.6.12-full.apk"

        val fallback = AppUpdateParser.parseFallbackGradleAndReadme(gradle, readme)
        assertEquals("1.6.12", fallback.versionName)
        assertEquals(
            "https://github.com/petrosvassiliou/thingsbook-android/raw/main/Thingsbook-v1.6.12-full.apk",
            fallback.apkDownloadUrl
        )
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
