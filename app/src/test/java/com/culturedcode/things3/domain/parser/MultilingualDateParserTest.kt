package com.culturedcode.things3.domain.parser

import com.culturedcode.things3.domain.model.ScheduleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class MultilingualDateParserTest {

    private val refDate = LocalDate.of(2026, 11, 7)

    @Test
    fun `parses Today in all 5 languages (EN, DE, IT, FR, ES)`() {
        val inputs = listOf(
            "today" to Locale.ENGLISH,
            "heute" to Locale.GERMAN,
            "oggi" to Locale.ITALIAN,
            "aujourd'hui" to Locale.FRENCH,
            "hoy" to Locale("es")
        )
        for ((expr, locale) in inputs) {
            val result = MultilingualDateParser.parse(expr, locale, refDate)
            assertNotNull("Failed for $expr", result)
            assertEquals(ScheduleState.TODAY, result!!.scheduleState)
        }
    }

    @Test
    fun `parses This Evening in all 5 languages (EN, DE, IT, FR, ES)`() {
        val inputs = listOf(
            "this evening" to Locale.ENGLISH,
            "heute abend" to Locale.GERMAN,
            "stasera" to Locale.ITALIAN,
            "ce soir" to Locale.FRENCH,
            "esta noche" to Locale("es")
        )
        for ((expr, locale) in inputs) {
            val result = MultilingualDateParser.parse(expr, locale, refDate)
            assertNotNull("Failed for $expr", result)
            assertEquals(ScheduleState.THIS_EVENING, result!!.scheduleState)
        }
    }

    @Test
    fun `parses Tomorrow and relative days in all 5 languages`() {
        val tomorrowInputs = listOf(
            "tomorrow" to Locale.ENGLISH,
            "morgen" to Locale.GERMAN,
            "domani" to Locale.ITALIAN,
            "demain" to Locale.FRENCH,
            "mañana" to Locale("es")
        )
        val expectedTomorrow = refDate.plusDays(1)
        val zone = ZoneId.systemDefault()

        for ((expr, locale) in tomorrowInputs) {
            val result = MultilingualDateParser.parse(expr, locale, refDate)
            assertNotNull("Failed for $expr", result)
            assertEquals(ScheduleState.UPCOMING, result!!.scheduleState)
            val parsedDate = Instant.ofEpochMilli(result.whenDateEpochMillis!!).atZone(zone).toLocalDate()
            assertEquals(expectedTomorrow, parsedDate)
        }

        val inThreeDaysInputs = listOf(
            "in 3 days" to Locale.ENGLISH,
            "in 3 tagen" to Locale.GERMAN,
            "tra 3 giorni" to Locale.ITALIAN,
            "dans 3 jours" to Locale.FRENCH,
            "en 3 días" to Locale("es")
        )
        val expectedPlus3 = refDate.plusDays(3)
        for ((expr, locale) in inThreeDaysInputs) {
            val result = MultilingualDateParser.parse(expr, locale, refDate)
            assertNotNull("Failed for $expr", result)
            val parsedDate = Instant.ofEpochMilli(result!!.whenDateEpochMillis!!).atZone(zone).toLocalDate()
            assertEquals(expectedPlus3, parsedDate)
        }
    }

    @Test
    fun `parses Next Friday in all 5 languages`() {
        val nextFridayInputs = listOf(
            "next friday" to Locale.ENGLISH,
            "nächsten freitag" to Locale.GERMAN,
            "venerdì prossimo" to Locale.ITALIAN,
            "vendredi prochain" to Locale.FRENCH,
            "próximo viernes" to Locale("es")
        )
        val expectedFriday = refDate.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
        val zone = ZoneId.systemDefault()

        for ((expr, locale) in nextFridayInputs) {
            val result = MultilingualDateParser.parse(expr, locale, refDate)
            assertNotNull("Failed for $expr", result)
            val parsedDate = Instant.ofEpochMilli(result!!.whenDateEpochMillis!!).atZone(zone).toLocalDate()
            assertEquals(expectedFriday, parsedDate)
        }
    }
}
