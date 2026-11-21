package com.pv.thingsbook

import com.pv.thingsbook.data.backup.ThingsBackupCodec
import com.pv.thingsbook.data.backup.ThingsbookBackupPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationConsistencyTest {

    private val localeResourceDirs = listOf(
        "values",
        "values-de",
        "values-it",
        "values-fr",
        "values-es"
    )

    private val formatSpecRegex = Regex("""%(\d+\$)?[dsf]""")

    private fun resolveResRoot(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate src/main/res from working dir ${File(".").absolutePath}")
    }

    private fun parseStringsXml(file: File): Pair<Map<String, String>, Set<String>> {
        assertTrue("Missing strings.xml file: ${file.path}", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = linkedMapOf<String, String>()
        val untranslatable = linkedSetOf<String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i)
            val attrs = element.attributes
            val name = attrs?.getNamedItem("name")?.nodeValue ?: continue
            val translatableAttr = attrs.getNamedItem("translatable")?.nodeValue
            if (translatableAttr == "false") {
                untranslatable.add(name)
            }
            map[name] = element.textContent ?: ""
        }
        return map to untranslatable
    }

    @Test
    fun `all 5 supported languages have complete and matching string keys and format placeholders`() {
        val resRoot = resolveResRoot()
        val (baseStrings, untranslatableKeys) = parseStringsXml(File(resRoot, "values/strings.xml"))
        val expectedTranslatableKeys = baseStrings.keys - untranslatableKeys

        assertTrue("Expected at least 150 localized keys", expectedTranslatableKeys.size >= 150)
        assertTrue("Missing settings_title key", "settings_title" in expectedTranslatableKeys)
        assertTrue("Missing magic_plus_todo_sub_view key", "magic_plus_todo_sub_view" in expectedTranslatableKeys)

        for (dir in localeResourceDirs.drop(1)) {
            val (localizedStrings, _) = parseStringsXml(File(resRoot, "$dir/strings.xml"))
            val localizedKeys = localizedStrings.keys - untranslatableKeys

            val missingInLocale = expectedTranslatableKeys - localizedKeys
            val extraInLocale = localizedKeys - expectedTranslatableKeys

            assertEquals("Missing string keys in $dir/strings.xml: $missingInLocale", emptySet<String>(), missingInLocale)
            assertEquals("Unexpected extra string keys in $dir/strings.xml: $extraInLocale", emptySet<String>(), extraInLocale)

            for (key in expectedTranslatableKeys) {
                val baseVal = baseStrings.getValue(key)
                val locVal = localizedStrings.getValue(key)
                assertFalse("Blank translation for '$key' in $dir/strings.xml", locVal.isBlank())

                val basePlaceholders = formatSpecRegex.findAll(baseVal).map { it.value }.toSet()
                val locPlaceholders = formatSpecRegex.findAll(locVal).map { it.value }.toSet()
                assertEquals(
                    "Mismatched format specifiers for '$key' in $dir/strings.xml",
                    basePlaceholders,
                    locPlaceholders
                )
            }
        }
    }

    @Test
    fun `legacy backup payload with deprecated showTodayAppBadge decodes cleanly`() {
        val legacyPayloadJson = """
            {
              "schemaVersion": 1,
              "createdAt": 1762500000000,
              "appVersionName": "1.6.8",
              "preferences": {
                "sidebarVisible": true,
                "showTodayAppBadge": true,
                "dailyDigestEnabled": false,
                "dailyDigestHour": 8,
                "dailyDigestMinute": 30,
                "languageTag": "fr"
              }
            }
        """.trimIndent()

        val decoded = ThingsBackupCodec.json.decodeFromString<ThingsbookBackupPayload>(legacyPayloadJson)
        assertEquals(true, decoded.preferences.sidebarVisible)
        assertEquals("fr", decoded.preferences.languageTag)
    }

    @Test
    fun `v1_8_0 map and location detection extracts explicit locations coordinates and street addresses`() {
        val notes = """
            Meet client at Location: Alexanderplatz 1, 10178 Berlin
            Coordinates: 48.137154, 11.576124
            Office HQ at 1600 Amphitheatre Parkway
            Docs: https://thingsbook.app
        """.trimIndent()

        val links = com.pv.thingsbook.ui.components.extractDetectedLinksFromNotes(notes)
        val mapLinks = links.filter {
            it.type == com.pv.thingsbook.ui.components.DetectedNoteLinkType.MAP
        }
        assertTrue("Expected at least 3 MAP links, got: $mapLinks", mapLinks.size >= 3)
        assertTrue(mapLinks.any { it.displayLabel.contains("Alexanderplatz") })
        assertTrue(mapLinks.any { it.displayLabel.contains("48.137154") })
        assertTrue(mapLinks.any { it.displayLabel.contains("1600 Amphitheatre Parkway") })
    }

    @Test
    fun `v1_8_0 backup codec round-trips attachments with AES-256-GCM encryption`() {
        val sampleAttachment = com.pv.thingsbook.data.backup.BackupAttachmentDto(
            id = "att-1",
            taskId = "task-1",
            projectId = null,
            fileName = "receipt.pdf",
            mimeType = "application/pdf",
            sizeBytes = 12,
            dataBase64 = "JVBERi0xLjQK",
            createdAt = 1763000000000L,
            updatedAt = 1763000000000L
        )
        val payload = ThingsbookBackupPayload(
            createdAt = 1763000000000L,
            appVersionName = "1.8.0",
            attachments = listOf(sampleAttachment)
        )
        val encoded = ThingsBackupCodec.encodeBackup(payload, "secretPass".toCharArray(), "1.8.0", 42)
        val decoded = ThingsBackupCodec.decodeBackup(encoded, "secretPass".toCharArray())
        assertEquals(1, decoded.attachments.size)
        assertEquals("receipt.pdf", decoded.attachments.first().fileName)
        assertEquals("JVBERi0xLjQK", decoded.attachments.first().dataBase64)
    }

    @Test
    fun `v3_0_0_beta_15 milestone and target area strings exist across all 5 locales and backup codec preserves isMilestone`() {
        val resRoot = resolveResRoot()
        val expectedByLocale = mapOf(
            "values" to mapOf(
                "v30_milestone_label" to "Milestone",
                "v30_mark_as_milestone" to "Mark as Milestone",
                "v30_remove_milestone" to "Remove Milestone",
                "v30_external_import_target_area" to "Target Area for Import"
            ),
            "values-de" to mapOf(
                "v30_milestone_label" to "Meilenstein",
                "v30_mark_as_milestone" to "Als Meilenstein markieren",
                "v30_remove_milestone" to "Meilenstein entfernen",
                "v30_external_import_target_area" to "Ziel-Bereich für Import"
            ),
            "values-fr" to mapOf(
                "v30_milestone_label" to "Jalon",
                "v30_mark_as_milestone" to "Marquer comme jalon",
                "v30_remove_milestone" to "Retirer le jalon",
                "v30_external_import_target_area" to "Domaine cible pour l\\'import"
            ),
            "values-it" to mapOf(
                "v30_milestone_label" to "Traguardo",
                "v30_mark_as_milestone" to "Segna come traguardo",
                "v30_remove_milestone" to "Rimuovi traguardo",
                "v30_external_import_target_area" to "Area di destinazione per l\\'importazione"
            ),
            "values-es" to mapOf(
                "v30_milestone_label" to "Hito",
                "v30_mark_as_milestone" to "Marcar como hito",
                "v30_remove_milestone" to "Quitar hito",
                "v30_external_import_target_area" to "Área de destino para la importación"
            )
        )

        expectedByLocale.forEach { (dir, expectedMap) ->
            val (strings, _) = parseStringsXml(File(resRoot, "$dir/strings.xml"))
            expectedMap.forEach { (key, expectedText) ->
                val actual = strings[key]
                assertTrue("Missing '$key' in $dir/strings.xml", actual != null)
                // Android XML escapes \' as \' in raw textContent before resource compilation; normalize \' -> '
                assertEquals(
                    "Unexpected translation for '$key' in $dir/strings.xml",
                    expectedText.replace("\\'", "'"),
                    actual!!.replace("\\'", "'")
                )
            }
        }

        val payload = ThingsbookBackupPayload(
            createdAt = 1_765_000_000_000L,
            appVersionName = "3.0.0-beta.15",
            tasks = listOf(
                com.pv.thingsbook.data.backup.BackupTaskDto(
                    id = "ms-1",
                    title = "GA Release Gate",
                    isMilestone = true
                ),
                com.pv.thingsbook.data.backup.BackupTaskDto(
                    id = "std-1",
                    title = "Draft release notes",
                    isMilestone = false
                )
            )
        )
        val encoded = ThingsBackupCodec.encodeBackup(payload, null, "3.0.0-beta.15", 106)
        val decoded = ThingsBackupCodec.decodeBackup(encoded, null)
        assertEquals(true, decoded.tasks.first { it.id == "ms-1" }.isMilestone)
        assertEquals(false, decoded.tasks.first { it.id == "std-1" }.isMilestone)

        // Legacy backup JSON omitting isMilestone defaults safely to false
        val legacyDecoded = ThingsBackupCodec.json.decodeFromString<ThingsbookBackupPayload>(
            """{"schemaVersion":1,"createdAt":1762500000000,"tasks":[{"id":"legacy-1","title":"Old Task"}]}"""
        )
        assertFalse(legacyDecoded.tasks.single().isMilestone)
    }
}


