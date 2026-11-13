package com.culturedcode.things3.data.calendar

import com.culturedcode.things3.domain.model.CalendarEventItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DeviceCalendarRepositoryTest {

    @Test
    fun `resolveEventDateRange handles single-day UTC all-day event accurately in negative and positive timezones`() {
        // 2026-11-15 all-day event in Android CalendarContract is stored as 2026-11-15T00:00Z .. 2026-11-16T00:00Z
        val beginUtc = LocalDate.of(2026, 11, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endUtc = LocalDate.of(2026, 11, 16).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val (startNy, endNy) = DeviceCalendarRepository.resolveEventDateRange(
            beginMillis = beginUtc,
            endMillis = endUtc,
            isAllDay = true,
            zone = ZoneId.of("America/New_York")
        )
        assertEquals(LocalDate.of(2026, 11, 15), startNy)
        assertEquals(LocalDate.of(2026, 11, 15), endNy)

        val (startBerlin, endBerlin) = DeviceCalendarRepository.resolveEventDateRange(
            beginMillis = beginUtc,
            endMillis = endUtc,
            isAllDay = true,
            zone = ZoneId.of("Europe/Berlin")
        )
        assertEquals(LocalDate.of(2026, 11, 15), startBerlin)
        assertEquals(LocalDate.of(2026, 11, 15), endBerlin)
    }

    @Test
    fun `groupEventsByDateInRange sorts all-day events first then chronologically and expands multi-day spans`() {
        val day1 = LocalDate.of(2026, 11, 13)
        val day2 = LocalDate.of(2026, 11, 14)

        val timedLater = CalendarEventItem(
            id = "1_10_2000",
            eventId = 10L,
            calendarId = "1",
            calendarDisplayName = "Work",
            title = "Afternoon Review",
            startMillis = 2_000L,
            endMillis = 3_000L,
            isAllDay = false,
            colorArgb = 0xFF2875D9.toInt(),
            startDateEpochDay = day1.toEpochDay(),
            endDateInclusiveEpochDay = day1.toEpochDay()
        )
        val timedEarlier = CalendarEventItem(
            id = "1_11_1000",
            eventId = 11L,
            calendarId = "1",
            calendarDisplayName = "Work",
            title = "Morning Standup",
            startMillis = 1_000L,
            endMillis = 1_500L,
            isAllDay = false,
            colorArgb = 0xFF2875D9.toInt(),
            startDateEpochDay = day1.toEpochDay(),
            endDateInclusiveEpochDay = day1.toEpochDay()
        )
        val allDayTwoDays = CalendarEventItem(
            id = "2_12_500",
            eventId = 12L,
            calendarId = "2",
            calendarDisplayName = "Holidays",
            title = "Conference",
            startMillis = 500L,
            endMillis = 9_000L,
            isAllDay = true,
            colorArgb = 0xFF1E9E8E.toInt(),
            startDateEpochDay = day1.toEpochDay(),
            endDateInclusiveEpochDay = day2.toEpochDay()
        )

        val grouped = DeviceCalendarRepository.groupEventsByDateInRange(
            events = listOf(timedLater, timedEarlier, allDayTwoDays),
            startDate = day1,
            endDateInclusive = day2
        )

        val day1Events = grouped[day1].orEmpty()
        assertEquals(listOf("Conference", "Morning Standup", "Afternoon Review"), day1Events.map { it.title })

        val day2Events = grouped[day2].orEmpty()
        assertEquals(listOf("Conference"), day2Events.map { it.title })
    }

    @Test
    fun `normalizeColorArgb ensures opaque alpha channel when alpha is zero`() {
        val rgbOnly = 0x002875D9
        val normalized = DeviceCalendarRepository.normalizeColorArgb(rgbOnly)
        assertTrue((normalized ushr 24) == 0xFF)
        assertEquals(0xFF2875D9.toInt(), normalized)
    }
}
