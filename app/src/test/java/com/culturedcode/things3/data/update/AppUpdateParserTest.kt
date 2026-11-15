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
    fun `stable channel strictly isolates against 2_0 pre-releases unless includeBeta is enabled`() {
        // Stable v1.8.1 with includeBeta = false MUST reject 2.0.0 pre-releases
        assertFalse(AppUpdateParser.isNewerVersion("1.8.1", "2.0.0-beta.1", includeBeta = false))
        assertFalse(AppUpdateParser.isNewerVersion("1.8.1", "v2.0.0-alpha.1", includeBeta = false))
        assertFalse(AppUpdateParser.isNewerVersion("1.8.1", "2.0.0-rc.1", includeBeta = false))
        assertFalse(AppUpdateParser.isNewerVersion("1.8.1+1", "2.0.0-beta.2+5", includeBeta = false))

        // Stable v1.7.0 / v1.8.0 / v1.8.1 with includeBeta = true detects v2.0.0-beta.1
        assertTrue(AppUpdateParser.isNewerVersion("1.7.0", "2.0.0-beta.1", includeBeta = true))
        assertTrue(AppUpdateParser.isNewerVersion("1.8.0", "2.0.0-beta.1", includeBeta = true))
        assertTrue(AppUpdateParser.isNewerVersion("1.8.1", "v2.0.0-beta.1", includeBeta = true))
        assertFalse(AppUpdateParser.isNewerVersion("2.0.0", "2.0.0-beta.99", includeBeta = true))

        // Stable releases upgrade cleanly on both channels
        assertTrue(AppUpdateParser.isNewerVersion("1.8.1", "1.8.2", includeBeta = false))
        assertTrue(AppUpdateParser.isNewerVersion("1.8.1", "2.0.0", includeBeta = false))

        // SemVer +build metadata ordering when core segments match on stable releases
        assertTrue(AppUpdateParser.compareVersions("1.8.0+1", "1.8.0") > 0)
        assertTrue(AppUpdateParser.compareVersions("1.8.1", "1.8.0+1") > 0)
        assertTrue(AppUpdateParser.compareVersions("1.8.1+1", "1.8.1") > 0)
        assertTrue(AppUpdateParser.isNewerVersion("1.8.1", "1.8.1+1"))
        assertFalse(AppUpdateParser.isNewerVersion("1.8.1+2", "1.8.1+1"))
    }

    @Test
    fun `beta channel compares numeric pre-release identifiers and promotes cleanly to 2_0_0 final`() {
        // Running 2.0.0-beta.1 automatically accepts newer betas and 2.0.0 final
        assertTrue(AppUpdateParser.isNewerVersion("2.0.0-beta.1", "2.0.0-beta.2"))
        assertTrue(AppUpdateParser.isNewerVersion("2.0.0-beta.2", "2.0.0-beta.10"))
        assertTrue(AppUpdateParser.isNewerVersion("2.0.0-beta.1", "2.0.0-beta.10"))
        assertFalse(AppUpdateParser.isNewerVersion("2.0.0-beta.10", "2.0.0-beta.2"))
        assertFalse(AppUpdateParser.isNewerVersion("2.0.0-beta.2", "2.0.0-beta.2"))

        // Alpha -> Beta -> RC -> Final progression
        assertTrue(AppUpdateParser.isNewerVersion("2.0.0-alpha.3", "2.0.0-beta.1"))
        assertTrue(AppUpdateParser.isNewerVersion("2.0.0-beta.99", "2.0.0-rc.1"))
        assertTrue(AppUpdateParser.isNewerVersion("2.0.0-beta.99", "2.0.0"))
        assertTrue(AppUpdateParser.isNewerVersion("2.0.0-rc.2", "2.0.0"))
        assertFalse(AppUpdateParser.isNewerVersion("2.0.0-beta.1", "1.8.1", includeBeta = true))
    }

    @Test
    fun `parseGitHubReleaseJson rejects draft and prerelease when includeBeta is false and accepts prerelease when includeBeta is true`() {
        val draftJson = """{"tag_name":"v2.0.0-beta.2","draft":true,"prerelease":true,"body":"Draft"}"""
        val prereleaseJson = """
            {
              "tag_name": "v2.0.0-beta.1",
              "name": "Thingsbook v2.0.0-beta.1",
              "draft": false,
              "prerelease": true,
              "body": "Public Beta 1",
              "assets": [
                {
                  "name": "Thingsbook-v2.0.0-beta.1-GTD-Full.apk",
                  "browser_download_url": "https://github.com/petrosvassiliou/thingsbook-android/releases/download/v2.0.0-beta.1/Thingsbook-v2.0.0-beta.1-GTD-Full.apk"
                }
              ]
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateParser.parseGitHubReleaseJson(draftJson, includeBeta = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateParser.parseGitHubReleaseJson(draftJson, includeBeta = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateParser.parseGitHubReleaseJson(prereleaseJson, includeBeta = false)
        }

        val betaParsed = AppUpdateParser.parseGitHubReleaseJson(prereleaseJson, includeBeta = true)
        assertEquals("2.0.0-beta.1", betaParsed.versionName)
        assertEquals("v2.0.0-beta.1", betaParsed.tagName)
        assertEquals(
            "https://github.com/petrosvassiliou/thingsbook-android/releases/download/v2.0.0-beta.1/Thingsbook-v2.0.0-beta.1-GTD-Full.apk",
            betaParsed.apkDownloadUrl
        )
    }

    @Test
    fun `parseGitHubReleaseJson parses releases array for beta list endpoint and filters drafts`() {
        val releasesArrayJson = """
            [
              {
                "tag_name": "v2.0.0-beta.3",
                "name": "Draft Beta 3",
                "draft": true,
                "prerelease": true,
                "body": "Unpublished draft"
              },
              {
                "tag_name": "v2.0.0-beta.2",
                "name": "Thingsbook v2.0.0-beta.2",
                "draft": false,
                "prerelease": true,
                "body": "Beta 2 notes",
                "assets": [
                  {
                    "name": "Thingsbook-v2.0.0-beta.2-GTD-Full.apk",
                    "browser_download_url": "https://github.com/petrosvassiliou/thingsbook-android/releases/download/v2.0.0-beta.2/Thingsbook-v2.0.0-beta.2-GTD-Full.apk"
                  }
                ]
              },
              {
                "tag_name": "v1.8.1",
                "name": "Thingsbook v1.8.1",
                "draft": false,
                "prerelease": false,
                "body": "Stable 1.8.1 notes"
              }
            ]
        """.trimIndent()

        val pickedBeta = AppUpdateParser.parseGitHubReleaseJson(releasesArrayJson, includeBeta = true)
        assertEquals("2.0.0-beta.2", pickedBeta.versionName)

        val pickedStable = AppUpdateParser.parseGitHubReleaseJson(releasesArrayJson, includeBeta = false)
        assertEquals("1.8.1", pickedStable.versionName)
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
    fun `parseFallbackGradleAndReadme extracts stable versionName and respects includeBeta`() {
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
                versionCode = 45
                versionName = "2.0.0-beta.1"
            }
        """.trimIndent()
        assertThrows(IOException::class.java) {
            AppUpdateParser.parseFallbackGradleAndReadme(betaGradle, null, includeBeta = false)
        }
        val betaFallback = AppUpdateParser.parseFallbackGradleAndReadme(betaGradle, null, includeBeta = true)
        assertEquals("2.0.0-beta.1", betaFallback.versionName)
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
