package com.culturedcode.things3.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.culturedcode.things3.domain.model.CalendarEventItem
import com.culturedcode.things3.domain.model.DeviceCalendarInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasCalendarPermission(): Boolean {
        return runCatching {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    suspend fun queryAvailableCalendars(): List<DeviceCalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) return@withContext emptyList()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.OWNER_ACCOUNT
        )

        val calendars = mutableListOf<DeviceCalendarInfo>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accNameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val accTypeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val colorIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
                val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val ownerIdx = cursor.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)

                while (cursor.moveToNext()) {
                    val calId = if (idIdx >= 0) cursor.getLong(idIdx).toString() else continue
                    val rawName = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val accountName = if (accNameIdx >= 0 && !cursor.isNull(accNameIdx)) cursor.getString(accNameIdx).orEmpty().trim() else ""
                    val accountType = if (accTypeIdx >= 0 && !cursor.isNull(accTypeIdx)) cursor.getString(accTypeIdx).orEmpty().trim() else ""
                    val rawColor = if (colorIdx >= 0 && !cursor.isNull(colorIdx)) cursor.getInt(colorIdx) else DEFAULT_CALENDAR_COLOR
                    val isPrimaryFlag = if (primaryIdx >= 0 && !cursor.isNull(primaryIdx)) cursor.getInt(primaryIdx) == 1 else false
                    val ownerAccount = if (ownerIdx >= 0 && !cursor.isNull(ownerIdx)) cursor.getString(ownerIdx).orEmpty().trim() else ""

                    val displayName = rawName.ifBlank { accountName.ifBlank { "Calendar #$calId" } }
                    val isPrimary = isPrimaryFlag || (accountName.isNotBlank() && accountName.equals(ownerAccount, ignoreCase = true))

                    calendars.add(
                        DeviceCalendarInfo(
                            id = calId,
                            displayName = displayName,
                            accountName = accountName,
                            accountType = accountType,
                            colorArgb = normalizeColorArgb(rawColor),
                            isPrimary = isPrimary
                        )
                    )
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed querying device calendars", err)
        }

        calendars.sortedWith(
            compareBy<DeviceCalendarInfo> { !it.accountType.contains("google", ignoreCase = true) }
                .thenBy { it.accountName.lowercase() }
                .thenByDescending { it.isPrimary }
                .thenBy { it.displayName.lowercase() }
        )
    }

    suspend fun queryEventsForDateRange(
        selectedCalendarIds: Set<String>,
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, List<CalendarEventItem>> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission() || selectedCalendarIds.isEmpty() || endDateInclusive.isBefore(startDate)) {
            return@withContext emptyMap()
        }

        val queryStartMillis = minOf(
            startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        ) - ONE_DAY_MILLIS
        val queryEndMillis = maxOf(
            endDateInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            endDateInclusive.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        ) + ONE_DAY_MILLIS

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, queryStartMillis)
        ContentUris.appendId(builder, queryEndMillis)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS
        )

        val rawEvents = mutableListOf<CalendarEventItem>()

        runCatching {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val eventIdIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val calIdIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
                val calNameIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val displayColorIdx = cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
                val calColorIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)
                val statusIdx = cursor.getColumnIndex(CalendarContract.Instances.STATUS)
                val attendeeStatusIdx = cursor.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS)

                while (cursor.moveToNext()) {
                    val calId = if (calIdIdx >= 0 && !cursor.isNull(calIdIdx)) cursor.getLong(calIdIdx).toString() else continue
                    if (calId !in selectedCalendarIds) continue

                    val status = if (statusIdx >= 0 && !cursor.isNull(statusIdx)) cursor.getInt(statusIdx) else CalendarContract.Instances.STATUS_CONFIRMED
                    if (status == CalendarContract.Instances.STATUS_CANCELED) continue

                    val attendeeStatus = if (attendeeStatusIdx >= 0 && !cursor.isNull(attendeeStatusIdx)) cursor.getInt(attendeeStatusIdx) else 0
                    if (attendeeStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue

                    val eventId = if (eventIdIdx >= 0 && !cursor.isNull(eventIdIdx)) cursor.getLong(eventIdIdx) else 0L
                    val calName = if (calNameIdx >= 0 && !cursor.isNull(calNameIdx)) cursor.getString(calNameIdx).orEmpty().trim() else ""
                    val title = if (titleIdx >= 0 && !cursor.isNull(titleIdx)) cursor.getString(titleIdx).orEmpty().trim() else ""
                    val beginMillis = if (beginIdx >= 0 && !cursor.isNull(beginIdx)) cursor.getLong(beginIdx) else continue
                    val rawEndMillis = if (endIdx >= 0 && !cursor.isNull(endIdx)) cursor.getLong(endIdx) else beginMillis
                    val endMillis = maxOf(beginMillis, rawEndMillis)
                    val isAllDay = if (allDayIdx >= 0 && !cursor.isNull(allDayIdx)) cursor.getInt(allDayIdx) == 1 else false

                    val rawColor = when {
                        displayColorIdx >= 0 && !cursor.isNull(displayColorIdx) -> cursor.getInt(displayColorIdx)
                        calColorIdx >= 0 && !cursor.isNull(calColorIdx) -> cursor.getInt(calColorIdx)
                        else -> DEFAULT_CALENDAR_COLOR
                    }

                    val (evStartDate, evEndDateInclusive) = resolveEventDateRange(
                        beginMillis = beginMillis,
                        endMillis = endMillis,
                        isAllDay = isAllDay,
                        zone = zone
                    )

                    rawEvents.add(
                        CalendarEventItem(
                            id = "${calId}_${eventId}_$beginMillis",
                            eventId = eventId,
                            calendarId = calId,
                            calendarDisplayName = calName,
                            title = title,
                            startMillis = beginMillis,
                            endMillis = endMillis,
                            isAllDay = isAllDay,
                            colorArgb = normalizeColorArgb(rawColor),
                            startDateEpochDay = evStartDate.toEpochDay(),
                            endDateInclusiveEpochDay = evEndDateInclusive.toEpochDay()
                        )
                    )
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed querying calendar event instances", err)
        }

        groupEventsByDateInRange(
            events = rawEvents,
            startDate = startDate,
            endDateInclusive = endDateInclusive
        )
    }

    companion object {
        private const val TAG = "DeviceCalendarRepo"
        private const val ONE_DAY_MILLIS = 86_400_000L
        const val DEFAULT_CALENDAR_COLOR: Int = 0xFF2875D9.toInt()

        fun normalizeColorArgb(rawColor: Int): Int {
            return if ((rawColor ushr 24) == 0) {
                rawColor or 0xFF000000.toInt()
            } else {
                rawColor
            }
        }

        fun resolveEventDateRange(
            beginMillis: Long,
            endMillis: Long,
            isAllDay: Boolean,
            zone: ZoneId
        ): Pair<LocalDate, LocalDate> {
            val effectiveZone = if (isAllDay) ZoneOffset.UTC else zone
            val startLocal = Instant.ofEpochMilli(beginMillis).atZone(effectiveZone).toLocalDate()
            val inclusiveEndMillis = if (endMillis > beginMillis) endMillis - 1L else beginMillis
            val endLocal = Instant.ofEpochMilli(inclusiveEndMillis)
                .atZone(effectiveZone)
                .toLocalDate()
                .coerceAtLeast(startLocal)
            return startLocal to endLocal
        }

        fun groupEventsByDateInRange(
            events: List<CalendarEventItem>,
            startDate: LocalDate,
            endDateInclusive: LocalDate
        ): Map<LocalDate, List<CalendarEventItem>> {
            if (events.isEmpty() || endDateInclusive.isBefore(startDate)) return emptyMap()
            val rangeStartEpoch = startDate.toEpochDay()
            val rangeEndEpoch = endDateInclusive.toEpochDay()
            val byDate = TreeMap<LocalDate, MutableList<CalendarEventItem>>()
            val seenPerDay = HashSet<String>()

            for (event in events) {
                val clampedStart = maxOf(event.startDateEpochDay, rangeStartEpoch)
                val clampedEnd = minOf(event.endDateInclusiveEpochDay, rangeEndEpoch)
                if (clampedEnd < clampedStart) continue
                for (epochDay in clampedStart..clampedEnd) {
                    val dedupKey = "${epochDay}_${event.calendarId}_${event.eventId}_${event.startMillis}"
                    if (!seenPerDay.add(dedupKey)) continue
                    val date = LocalDate.ofEpochDay(epochDay)
                    byDate.getOrPut(date) { mutableListOf() }.add(event)
                }
            }

            val comparator = compareByDescending<CalendarEventItem> { it.isAllDay }
                .thenBy { it.startMillis }
                .thenBy { it.title.lowercase() }

            return byDate.mapValues { (_, list) -> list.sortedWith(comparator) }
        }
    }
}
