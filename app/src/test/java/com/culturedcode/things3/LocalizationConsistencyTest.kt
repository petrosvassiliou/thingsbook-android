package com.culturedcode.things3

import com.culturedcode.things3.data.backup.ThingsBackupCodec
import com.culturedcode.things3.data.backup.ThingsbookBackupPayload
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
}
