package com.culturedcode.things3.domain.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ResolvedWhenSelection(
    val scheduleState: ScheduleState,
    val whenDateEpochMillis: Long?,
    val reminderEpochMillis: Long?
)

/**
 * Pure domain helper for resolving quick schedule presets ("Heute", "Heute Abend", "Morgen",
 * "Jederzeit", "Irgendwann") in WhenNaturalDateDialog.
 *
 * Ensures that clicking any preset chip immediately resolves the target ScheduleState, whenDate,
 * and optional reminder timestamp on the target date — even when a task (such as a daily recurring
 * task) already has an active reminder time.
 */
object WhenScheduleResolver {

    @JvmStatic
    fun computeReminderEpoch(
        targetDate: LocalDate?,
        zone: ZoneId,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int
    ): Long? {
        if (!reminderEnabled || targetDate == null) return null
        return runCatching {
            targetDate.atTime(
                LocalTime.of(
                    reminderHour.coerceIn(0, 23),
                    reminderMinute.coerceIn(0, 59)
                )
            )
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    @JvmStatic
    fun resolvePresetSelection(
        preset: ScheduleState,
        todayDate: LocalDate,
        zone: ZoneId,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int
    ): ResolvedWhenSelection {
        val todayEpoch = todayDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowDate = todayDate.plusDays(1)
        val tomorrowEpoch = tomorrowDate.atStartOfDay(zone).toInstant().toEpochMilli()

        return when (preset) {
            ScheduleState.TODAY -> ResolvedWhenSelection(
                scheduleState = ScheduleState.TODAY,
                whenDateEpochMillis = todayEpoch,
                reminderEpochMillis = computeReminderEpoch(
                    targetDate = todayDate,
                    zone = zone,
                    reminderEnabled = reminderEnabled,
                    reminderHour = reminderHour,
                    reminderMinute = reminderMinute
                )
            )
            ScheduleState.THIS_EVENING -> ResolvedWhenSelection(
                scheduleState = ScheduleState.THIS_EVENING,
                whenDateEpochMillis = todayEpoch,
                reminderEpochMillis = computeReminderEpoch(
                    targetDate = todayDate,
                    zone = zone,
                    reminderEnabled = reminderEnabled,
                    reminderHour = reminderHour,
                    reminderMinute = reminderMinute
                )
            )
            ScheduleState.UPCOMING -> ResolvedWhenSelection(
                scheduleState = ScheduleState.UPCOMING,
                whenDateEpochMillis = tomorrowEpoch,
                reminderEpochMillis = computeReminderEpoch(
                    targetDate = tomorrowDate,
                    zone = zone,
                    reminderEnabled = reminderEnabled,
                    reminderHour = reminderHour,
                    reminderMinute = reminderMinute
                )
            )
            ScheduleState.ANYTIME -> ResolvedWhenSelection(
                scheduleState = ScheduleState.ANYTIME,
                whenDateEpochMillis = null,
                reminderEpochMillis = null
            )
            ScheduleState.SOMEDAY -> ResolvedWhenSelection(
                scheduleState = ScheduleState.SOMEDAY,
                whenDateEpochMillis = null,
                reminderEpochMillis = null
            )
            ScheduleState.INBOX -> ResolvedWhenSelection(
                scheduleState = ScheduleState.INBOX,
                whenDateEpochMillis = null,
                reminderEpochMillis = null
            )
        }
    }
}
