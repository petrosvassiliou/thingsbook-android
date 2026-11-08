package com.culturedcode.things3.domain.parser

import com.culturedcode.things3.domain.model.ScheduleState
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ParsedNaturalDate(
    val scheduleState: ScheduleState,
    val whenDateEpochMillis: Long?,
    val formattedPreview: String,
    val matchedExpression: String
)

/**
 * Live 5-Language Natural Language Date Parser for Thingsbook (`EN`, `DE`, `IT`, `FR`, `ES`).
 *
 * Recognizes expressions across all 5 languages in real time:
 * - Today: "today", "heute", "oggi", "aujourd'hui", "hoy"
 * - This Evening: "this evening", "tonight", "heute abend", "stasera", "ce soir", "esta noche"
 * - Tomorrow: "tomorrow", "morgen", "domani", "demain", "mañana"
 * - Overmorrow (+2d): "übermorgen", "dopodomani", "après-demain", "pasado mañana"
 * - Someday: "someday", "irgendwann", "prima o poi", "un jour", "algún día"
 * - Anytime: "anytime", "jederzeit", "in qualsiasi momento", "à tout moment", "en cualquier momento"
 * - Relative offsets: "in 3 days", "in 3 tagen", "tra 3 giorni", "fra 3 giorni", "dans 3 jours", "en 3 días",
 *   "in 2 weeks", "in 2 wochen", "tra 2 settimane", "dans 2 semaines", "en 2 semanas"
 * - Next weekday: "next friday", "nächsten freitag", "venerdì prossimo", "vendredi prochain", "próximo viernes"
 *   (and standalone weekday names in EN, DE, IT, FR, ES)
 */
object MultilingualDateParser {

    private val weekdayAliases: Map<String, DayOfWeek> = buildMap {
        // English
        put("monday", DayOfWeek.MONDAY); put("mon", DayOfWeek.MONDAY)
        put("tuesday", DayOfWeek.TUESDAY); put("tue", DayOfWeek.TUESDAY)
        put("wednesday", DayOfWeek.WEDNESDAY); put("wed", DayOfWeek.WEDNESDAY)
        put("thursday", DayOfWeek.THURSDAY); put("thu", DayOfWeek.THURSDAY)
        put("friday", DayOfWeek.FRIDAY); put("fri", DayOfWeek.FRIDAY)
        put("saturday", DayOfWeek.SATURDAY); put("sat", DayOfWeek.SATURDAY)
        put("sunday", DayOfWeek.SUNDAY); put("sun", DayOfWeek.SUNDAY)

        // German (Deutsch)
        put("montag", DayOfWeek.MONDAY)
        put("dienstag", DayOfWeek.TUESDAY)
        put("mittwoch", DayOfWeek.WEDNESDAY)
        put("donnerstag", DayOfWeek.THURSDAY)
        put("freitag", DayOfWeek.FRIDAY)
        put("samstag", DayOfWeek.SATURDAY); put("sonnabend", DayOfWeek.SATURDAY)
        put("sonntag", DayOfWeek.SUNDAY)

        // Italian (Italiano - normalized without accents & with accents)
        put("lunedi", DayOfWeek.MONDAY)
        put("martedi", DayOfWeek.TUESDAY)
        put("mercoledi", DayOfWeek.WEDNESDAY)
        put("giovedi", DayOfWeek.THURSDAY)
        put("venerdi", DayOfWeek.FRIDAY)
        put("sabato", DayOfWeek.SATURDAY)
        put("domenica", DayOfWeek.SUNDAY)

        // French (Français)
        put("lundi", DayOfWeek.MONDAY)
        put("mardi", DayOfWeek.TUESDAY)
        put("mercredi", DayOfWeek.WEDNESDAY)
        put("jeudi", DayOfWeek.THURSDAY)
        put("vendredi", DayOfWeek.FRIDAY)
        put("samedi", DayOfWeek.SATURDAY)
        put("dimanche", DayOfWeek.SUNDAY)

        // Spanish (Español - normalized without accents)
        put("lunes", DayOfWeek.MONDAY)
        put("martes", DayOfWeek.TUESDAY)
        put("miercoles", DayOfWeek.WEDNESDAY)
        put("jueves", DayOfWeek.THURSDAY)
        put("viernes", DayOfWeek.FRIDAY)
        put("sabado", DayOfWeek.SATURDAY)
        put("domingo", DayOfWeek.SUNDAY)
    }

    private val relativeDaysRegex = Regex(
        """^(?:in|tra|fra|dans|en)\s+(\d{1,3})\s*(?:days?|tag(?:en|e)?|giorn[oi]|jours?|dias?|d)$"""
    )

    private val relativeWeeksRegex = Regex(
        """^(?:in|tra|fra|dans|en)\s+(\d{1,2})\s*(?:weeks?|wochen?|settiman[ae]|semaines?|semanas?|w)$"""
    )

    private val isoDateRegex = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
    private val euroDateRegex = Regex("""^(\d{1,2})\.(\d{1,2})\.(?:(\d{4}|\d{2}))?$""")

    fun parse(
        rawInput: String,
        locale: Locale = Locale.getDefault(),
        referenceDate: LocalDate = LocalDate.now(ZoneId.systemDefault())
    ): ParsedNaturalDate? = runCatching {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val normalized = normalizeDiacritics(trimmed.lowercase(locale))
        val zone = ZoneId.systemDefault()
        val formatter = runCatching {
            DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", locale)
        }.getOrDefault(DateTimeFormatter.ISO_LOCAL_DATE)

        fun epochFor(date: LocalDate): Long =
            date.atStartOfDay(zone).toInstant().toEpochMilli()

        // 1. This Evening / Heute Abend / Stasera / Ce soir / Esta noche
        if (normalized in setOf(
                "this evening", "tonight", "evening",
                "heute abend", "abend",
                "stasera", "questa sera",
                "ce soir", "soir",
                "esta noche", "noche"
            )
        ) {
            return ParsedNaturalDate(
                scheduleState = ScheduleState.THIS_EVENING,
                whenDateEpochMillis = epochFor(referenceDate),
                formattedPreview = referenceDate.format(formatter) + " · 🌙",
                matchedExpression = trimmed
            )
        }

        // 2. Today / Heute / Oggi / Aujourd'hui / Hoy
        if (normalized in setOf(
                "today", "tod",
                "heute",
                "oggi",
                "aujourd'hui", "aujourdhui",
                "hoy"
            )
        ) {
            return ParsedNaturalDate(
                scheduleState = ScheduleState.TODAY,
                whenDateEpochMillis = epochFor(referenceDate),
                formattedPreview = referenceDate.format(formatter) + " · ★",
                matchedExpression = trimmed
            )
        }

        // 3. Tomorrow / Morgen / Domani / Demain / Mañana
        if (normalized in setOf(
                "tomorrow", "tmr", "tom",
                "morgen",
                "domani",
                "demain",
                "manana"
            )
        ) {
            val targetDate = referenceDate.plusDays(1)
            return ParsedNaturalDate(
                scheduleState = ScheduleState.UPCOMING,
                whenDateEpochMillis = epochFor(targetDate),
                formattedPreview = targetDate.format(formatter),
                matchedExpression = trimmed
            )
        }

        // 4. Overmorrow (+2 days): Übermorgen / Dopodomani / Après-demain / Pasado mañana
        if (normalized in setOf(
                "overmorrow",
                "ubermorgen", "uebermorgen",
                "dopodomani",
                "apres-demain", "apres demain",
                "pasado manana"
            )
        ) {
            val targetDate = referenceDate.plusDays(2)
            return ParsedNaturalDate(
                scheduleState = ScheduleState.UPCOMING,
                whenDateEpochMillis = epochFor(targetDate),
                formattedPreview = targetDate.format(formatter),
                matchedExpression = trimmed
            )
        }

        // 5. Someday / Irgendwann / Prima o poi / Un jour / Algún día
        if (normalized in setOf(
                "someday", "later",
                "irgendwann", "spater",
                "prima o poi",
                "un jour",
                "algun dia"
            )
        ) {
            return ParsedNaturalDate(
                scheduleState = ScheduleState.SOMEDAY,
                whenDateEpochMillis = null,
                formattedPreview = "📦",
                matchedExpression = trimmed
            )
        }

        // 6. Anytime / Jederzeit / In qualsiasi momento / À tout moment / En cualquier momento
        if (normalized in setOf(
                "anytime",
                "jederzeit",
                "in qualsiasi momento",
                "a tout moment",
                "en cualquier momento"
            )
        ) {
            return ParsedNaturalDate(
                scheduleState = ScheduleState.ANYTIME,
                whenDateEpochMillis = null,
                formattedPreview = "⚡",
                matchedExpression = trimmed
            )
        }

        // 7. Relative Days: "in 3 days", "in 3 tagen", "tra 3 giorni", "dans 3 jours", "en 3 días"
        relativeDaysRegex.matchEntire(normalized)?.let { match ->
            val days = match.groupValues[1].toLongOrNull() ?: return@let
            val targetDate = referenceDate.plusDays(days)
            val state = if (days == 0L) ScheduleState.TODAY else ScheduleState.UPCOMING
            return ParsedNaturalDate(
                scheduleState = state,
                whenDateEpochMillis = epochFor(targetDate),
                formattedPreview = targetDate.format(formatter),
                matchedExpression = trimmed
            )
        }

        // 8. Relative Weeks: "in 2 weeks", "in 2 wochen", "tra 2 settimane", "dans 2 semaines", "en 2 semanas"
        relativeWeeksRegex.matchEntire(normalized)?.let { match ->
            val weeks = match.groupValues[1].toLongOrNull() ?: return@let
            val targetDate = referenceDate.plusWeeks(weeks)
            val state = if (weeks == 0L) ScheduleState.TODAY else ScheduleState.UPCOMING
            return ParsedNaturalDate(
                scheduleState = state,
                whenDateEpochMillis = epochFor(targetDate),
                formattedPreview = targetDate.format(formatter),
                matchedExpression = trimmed
            )
        }

        // 9. Next Weekday or Standalone Weekday in EN, DE, IT, FR, ES
        // Examples: "next friday", "nächsten freitag", "venerdì prossimo", "vendredi prochain", "próximo viernes"
        val cleanedWeekdayToken = normalized
            .replace(
                Regex("""^(?:next|nachsten|nachster|nachste|naechsten|naechste|diesen|kommenden|am|prossimo|prossima|proximo|proxima|prochain|prochaine)\s+"""),
                ""
            )
            .replace(Regex("""\s+(?:prossimo|prossima|prochain|prochaine|proximo|proxima)$"""), "")
            .trim()

        weekdayAliases[cleanedWeekdayToken]?.let { dayOfWeek ->
            val targetDate = referenceDate.with(TemporalAdjusters.next(dayOfWeek))
            return ParsedNaturalDate(
                scheduleState = ScheduleState.UPCOMING,
                whenDateEpochMillis = epochFor(targetDate),
                formattedPreview = targetDate.format(formatter),
                matchedExpression = trimmed
            )
        }

        // 10. Explicit ISO (YYYY-MM-DD) or European (DD.MM.YYYY) date
        isoDateRegex.matchEntire(normalized)?.let { match ->
            val y = match.groupValues[1].toIntOrNull()?.takeIf { it in 1970..2100 } ?: return@let
            val m = match.groupValues[2].toIntOrNull() ?: return@let
            val d = match.groupValues[3].toIntOrNull() ?: return@let
            val date = runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: return@let
            return ParsedNaturalDate(
                scheduleState = if (date <= referenceDate) ScheduleState.TODAY else ScheduleState.UPCOMING,
                whenDateEpochMillis = epochFor(date),
                formattedPreview = date.format(formatter),
                matchedExpression = trimmed
            )
        }

        euroDateRegex.matchEntire(normalized)?.let { match ->
            val d = match.groupValues[1].toIntOrNull() ?: return@let
            val m = match.groupValues[2].toIntOrNull() ?: return@let
            val rawYear = match.groupValues[3]
            val y = when {
                rawYear.isEmpty() -> referenceDate.year
                rawYear.length == 2 -> 2000 + (rawYear.toIntOrNull() ?: return@let)
                else -> rawYear.toIntOrNull()?.takeIf { it in 1970..2100 } ?: return@let
            }
            var date = runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: return@let
            if (rawYear.isEmpty() && date.isBefore(referenceDate)) {
                date = date.plusYears(1)
            }
            return ParsedNaturalDate(
                scheduleState = if (date <= referenceDate) ScheduleState.TODAY else ScheduleState.UPCOMING,
                whenDateEpochMillis = epochFor(date),
                formattedPreview = date.format(formatter),
                matchedExpression = trimmed
            )
        }

        return null
    }.getOrNull()

    private fun normalizeDiacritics(input: String): String {
        val unifiedQuotes = input.replace(Regex("[\\u2018\\u2019\\u02BC`]"), "'")
        val decomposed = Normalizer.normalize(unifiedQuotes, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
