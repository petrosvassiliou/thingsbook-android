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

    @Test
    fun `filterActiveOrUpcomingEvents removes past timed and past-day appointments from Today and Planned views while preserving ongoing, future, and today all-day events`() {
        val zone = ZoneId.of("America/Los_Angeles")
        val yesterday = LocalDate.of(2026, 11, 16)
        val today = LocalDate.of(2026, 11, 17)
        val tomorrow = LocalDate.of(2026, 11, 18)

        // Current time: 2026-11-17 17:30 PST (01:30Z on 2026-11-18 — after 00:00Z UTC boundary!)
        val nowMillis = today.atTime(17, 30).atZone(zone).toInstant().toEpochMilli()

        val pastYesterday = CalendarEventItem(
            id = "ev_yest",
            eventId = 1L,
            calendarId = "1",
            calendarDisplayName = "Work",
            title = "Yesterday Retro",
            startMillis = yesterday.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
            endMillis = yesterday.atTime(11, 0).atZone(zone).toInstant().toEpochMilli(),
            isAllDay = false,
            colorArgb = 0xFF2875D9.toInt(),
            startDateEpochDay = yesterday.toEpochDay(),
            endDateInclusiveEpochDay = yesterday.toEpochDay()
        )
        val pastMorningToday = CalendarEventItem(
            id = "ev_past_today",
            eventId = 2L,
            calendarId = "1",
            calendarDisplayName = "Work",
            title = "Morning Standup",
            startMillis = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
            endMillis = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
            isAllDay = false,
            colorArgb = 0xFF2875D9.toInt(),
            startDateEpochDay = today.toEpochDay(),
            endDateInclusiveEpochDay = today.toEpochDay()
        )
        val ongoingToday = CalendarEventItem(
            id = "ev_ongoing_today",
            eventId = 3L,
            calendarId = "1",
            calendarDisplayName = "Work",
            title = "Evening Sync",
            startMillis = today.atTime(17, 0).atZone(zone).toInstant().toEpochMilli(),
            endMillis = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli(),
            isAllDay = false,
            colorArgb = 0xFF2875D9.toInt(),
            startDateEpochDay = today.toEpochDay(),
            endDateInclusiveEpochDay = today.toEpochDay()
        )
        val laterTonight = CalendarEventItem(
            id = "ev_later_today",
            eventId = 4L,
            calendarId = "1",
            calendarDisplayName = "Home",
            title = "Dinner",
            startMillis = today.atTime(19, 30).atZone(zone).toInstant().toEpochMilli(),
            endMillis = today.atTime(21, 0).atZone(zone).toInstant().toEpochMilli(),
            isAllDay = false,
            colorArgb = 0xFF2875D9.toInt(),
            startDateEpochDay = today.toEpochDay(),
            endDateInclusiveEpochDay = today.toEpochDay()
        )
        // UTC all-day event on 2026-11-17 has endMillis = 2026-11-18T00:00Z = 16:00 PST (< 17:30 PST!), must still stay visible all day!
        val allDayToday = CalendarEventItem(
            id = "ev_allday_today",
            eventId = 5L,
            calendarId = "2",
            calendarDisplayName = "Holidays",
            title = "Company Offsite",
            startMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            endMillis = tomorrow.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            isAllDay = true,
            colorArgb = 0xFF1E9E8E.toInt(),
            startDateEpochDay = today.toEpochDay(),
            endDateInclusiveEpochDay = today.toEpochDay()
        )
        val futureTomorrow = CalendarEventItem(
            id = "ev_tomorrow",
            eventId = 6L,
            calendarId = "1",
            calendarDisplayName = "Work",
            title = "Architecture Review",
            startMillis = tomorrow.atTime(11, 0).atZone(zone).toInstant().toEpochMilli(),
            endMillis = tomorrow.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            isAllDay = false,
            colorArgb = 0xFF2875D9.toInt(),
            startDateEpochDay = tomorrow.toEpochDay(),
            endDateInclusiveEpochDay = tomorrow.toEpochDay()
        )

        val allEvents = listOf(pastYesterday, pastMorningToday, ongoingToday, laterTonight, allDayToday, futureTomorrow)

        val activeToday = DeviceCalendarRepository.filterActiveOrUpcomingEvents(
            events = allEvents,
            forDate = today,
            todayDate = today,
            nowMillis = nowMillis,
            upcomingOnly = false
        )
        assertEquals(listOf("Evening Sync", "Dinner", "Company Offsite"), activeToday.map { it.title })

        // Planned / Upcoming must reject past days AND today, keeping strictly future active appointments
        assertTrue(
            DeviceCalendarRepository.filterActiveOrUpcomingEvents(
                events = allEvents,
                forDate = yesterday,
                todayDate = today,
                nowMillis = nowMillis,
                upcomingOnly = true
            ).isEmpty()
        )
        assertTrue(
            DeviceCalendarRepository.filterActiveOrUpcomingEvents(
                events = allEvents,
                forDate = today,
                todayDate = today,
                nowMillis = nowMillis,
                upcomingOnly = true
            ).isEmpty()
        )
        val activeTomorrow = DeviceCalendarRepository.filterActiveOrUpcomingEvents(
            events = allEvents,
            forDate = tomorrow,
            todayDate = today,
            nowMillis = nowMillis,
            upcomingOnly = true
        )
        assertEquals(listOf("Architecture Review"), activeTomorrow.map { it.title })
    }
}
