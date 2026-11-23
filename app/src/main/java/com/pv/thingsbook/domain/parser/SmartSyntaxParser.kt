package com.pv.thingsbook.domain.parser

import com.pv.thingsbook.domain.model.Area
import com.pv.thingsbook.domain.model.Project
import com.pv.thingsbook.domain.model.ProjectStatus
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.Tag
import com.pv.thingsbook.domain.model.parseCustomDurationMinutes
import java.time.LocalDate
import java.util.Locale

data class ParsedSmartSyntax(
    val cleanTitle: String,
    val scheduleState: ScheduleState? = null,
    val whenDate: Long? = null,
    val whenLabel: String? = null,
    val deadline: Long? = null,
    val deadlineLabel: String? = null,
    val durationMinutes: Int? = null,
    val matchedTagIds: List<String> = emptyList(),
    val matchedTags: List<Tag> = emptyList(),
    val matchedProject: Project? = null,
    val matchedArea: Area? = null,
    val isMilestone: Boolean = false,
    val hasAnyMatch: Boolean = false
)

/**
 * Pure, deterministic inline Smart-Syntax parser for Thingsbook v3.2.
 *
 * Supported tokens (must be preceded by start-of-string or whitespace so URLs like
 * `https://a.com/b` and fractions like `3/4` are never altered):
 * - `@<token>` / `@"<phrase>"` -> When schedule (`MultilingualDateParser` across EN/DE/FR/IT/ES)
 * - `!<token>` / `!"<phrase>"` -> `!milestone` sets `isMilestone = true`; date expression sets `deadline`
 * - `◆` (standalone)           -> sets `isMilestone = true`
 * - `~<token>` / `~"<phrase>"` -> Estimated duration (`parseCustomDurationMinutes`, e.g. `~30m`, `~1.5h`, `~2d`)
 * - `#<token>` / `#"<phrase>"` -> Case-insensitive Tag match (exact or `-` -> space)
 * - `/<token>` / `/"<phrase>"` -> Case-insensitive active Project match first (exact, `-` -> space, or unambiguous prefix >= 3 chars), then Area
 */
object SmartSyntaxParser {

    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    fun parse(
        rawTitle: String,
        tags: List<Tag> = emptyList(),
        projects: List<Project> = emptyList(),
        areas: List<Area> = emptyList(),
        locale: Locale = Locale.getDefault(),
        referenceDate: LocalDate = LocalDate.now()
    ): ParsedSmartSyntax {
        val trimmedOriginal = rawTitle.trim()
        if (trimmedOriginal.isEmpty()) {
            return ParsedSmartSyntax(cleanTitle = "")
        }

        val activeTags = tags.filter { !it.isDeleted && it.title.isNotBlank() }
        val activeProjects = projects.filter {
            !it.isDeleted && it.status == ProjectStatus.ACTIVE && it.title.isNotBlank()
        }
        val activeAreas = areas.filter { !it.isDeleted && it.title.isNotBlank() }

        var resolvedScheduleState: ScheduleState? = null
        var resolvedWhenDate: Long? = null
        var resolvedWhenLabel: String? = null
        var resolvedDeadline: Long? = null
        var resolvedDeadlineLabel: String? = null
        var resolvedDurationMinutes: Int? = null
        val resolvedTags = mutableListOf<Tag>()
        val resolvedTagIdSet = LinkedHashSet<String>()
        var resolvedProject: Project? = null
        var resolvedArea: Area? = null
        var resolvedMilestone = false
        val strippedSpans = mutableListOf<IntRange>()

        var index = 0
        val len = rawTitle.length

        while (index < len) {
            val isAtBoundary = index == 0 || rawTitle[index - 1].isWhitespace()
            if (!isAtBoundary) {
                index++
                continue
            }

            val ch = rawTitle[index]

            // Standalone ◆ milestone symbol
            if (ch == '◆' && (index + 1 == len || rawTitle[index + 1].isWhitespace())) {
                resolvedMilestone = true
                strippedSpans += index..index
                index += 1
                continue
            }

            if (ch != '@' && ch != '!' && ch != '~' && ch != '#' && ch != '/') {
                index++
                continue
            }

            if (index + 1 >= len || rawTitle[index + 1].isWhitespace()) {
                index++
                continue
            }

            val scanned = scanTokenAfterPrefix(rawTitle, index + 1)
            if (scanned == null || scanned.rawValue.isBlank()) {
                index++
                continue
            }

            val candidates = buildTokenCandidates(scanned)
            var matchedEndExclusive: Int? = null

            for (cand in candidates) {
                val phrase = cand.value
                val isQuoted = scanned.isQuoted
                val didResolve = when (ch) {
                    '@' -> {
                        val parsedDate = parseNaturalDateToken(phrase, isQuoted, locale, referenceDate)
                        if (parsedDate != null) {
                            resolvedScheduleState = parsedDate.scheduleState
                            resolvedWhenDate = parsedDate.whenDateEpochMillis
                            resolvedWhenLabel = parsedDate.formattedPreview
                            true
                        } else {
                            false
                        }
                    }
                    '!' -> {
                        if (phrase.equals("milestone", ignoreCase = true)) {
                            resolvedMilestone = true
                            true
                        } else {
                            val parsedDate = parseNaturalDateToken(phrase, isQuoted, locale, referenceDate)
                            if (parsedDate != null && parsedDate.whenDateEpochMillis != null) {
                                resolvedDeadline = parsedDate.whenDateEpochMillis
                                resolvedDeadlineLabel = parsedDate.formattedPreview
                                    .removeSuffix(" · ★")
                                    .removeSuffix(" · 🌙")
                                true
                            } else {
                                false
                            }
                        }
                    }
                    '~' -> {
                        val mins = parseDurationToken(phrase, isQuoted)
                        if (mins != null) {
                            resolvedDurationMinutes = mins
                            true
                        } else {
                            false
                        }
                    }
                    '#' -> {
                        val matchedTag = matchTag(phrase, isQuoted, activeTags)
                        if (matchedTag != null) {
                            if (resolvedTagIdSet.add(matchedTag.id)) {
                                resolvedTags += matchedTag
                            }
                            true
                        } else {
                            false
                        }
                    }
                    '/' -> {
                        val proj = matchProject(phrase, isQuoted, activeProjects)
                        if (proj != null) {
                            resolvedProject = proj
                            true
                        } else {
                            val area = matchArea(phrase, isQuoted, activeAreas)
                            if (area != null) {
                                resolvedArea = area
                                true
                            } else {
                                false
                            }
                        }
                    }
                    else -> false
                }

                if (didResolve) {
                    matchedEndExclusive = cand.endExclusive
                    break
                }
            }

            if (matchedEndExclusive != null) {
                strippedSpans += index until matchedEndExclusive
                index = matchedEndExclusive
            } else {
                index = scanned.endExclusive
            }
        }

        if (strippedSpans.isEmpty()) {
            return ParsedSmartSyntax(
                cleanTitle = trimmedOriginal,
                hasAnyMatch = false
            )
        }

        val sb = StringBuilder(rawTitle.length)
        var cursor = 0
        for (span in strippedSpans) {
            if (cursor < span.first) {
                sb.append(rawTitle, cursor, span.first)
            }
            cursor = span.last + 1
        }
        if (cursor < rawTitle.length) {
            sb.append(rawTitle, cursor, rawTitle.length)
        }

        val collapsed = sb.toString().replace(MULTI_SPACE_REGEX, " ").trim()
        val finalTitle = collapsed.ifBlank { trimmedOriginal }

        return ParsedSmartSyntax(
            cleanTitle = finalTitle,
            scheduleState = resolvedScheduleState,
            whenDate = resolvedWhenDate,
            whenLabel = resolvedWhenLabel,
            deadline = resolvedDeadline,
            deadlineLabel = resolvedDeadlineLabel,
            durationMinutes = resolvedDurationMinutes,
            matchedTagIds = resolvedTagIdSet.toList(),
            matchedTags = resolvedTags.toList(),
            matchedProject = resolvedProject,
            matchedArea = resolvedArea,
            isMilestone = resolvedMilestone,
            hasAnyMatch = true
        )
    }

    private data class ScannedToken(
        val rawValue: String,
        val isQuoted: Boolean,
        val endExclusive: Int
    )

    private data class CandidateSlice(
        val value: String,
        val endExclusive: Int
    )

    private fun scanTokenAfterPrefix(text: String, valueStart: Int): ScannedToken? {
        val len = text.length
        if (valueStart >= len) return null
        val openCh = text[valueStart]
        if (openCh == '"' || openCh == '“') {
            val expectedClose = if (openCh == '“') '”' else '"'
            var cursor = valueStart + 1
            while (cursor < len) {
                val c = text[cursor]
                if (c == expectedClose || c == '"') {
                    val afterClose = cursor + 1
                    if (afterClose == len || text[afterClose].isWhitespace() || text[afterClose] in charArrayOf(',', ';')) {
                        val inner = text.substring(valueStart + 1, cursor).trim()
                        return ScannedToken(
                            rawValue = inner,
                            isQuoted = true,
                            endExclusive = afterClose
                        )
                    }
                }
                cursor++
            }
            return null
        }

        var end = valueStart
        while (end < len && !text[end].isWhitespace()) {
            end++
        }
        if (end <= valueStart) return null
        return ScannedToken(
            rawValue = text.substring(valueStart, end),
            isQuoted = false,
            endExclusive = end
        )
    }

    private fun buildTokenCandidates(scanned: ScannedToken): List<CandidateSlice> {
        if (scanned.isQuoted) {
            return listOf(CandidateSlice(scanned.rawValue, scanned.endExclusive))
        }
        val raw = scanned.rawValue
        val trimmedPunct = raw.trimEnd(',', ';')
        return if (trimmedPunct.isNotEmpty() && trimmedPunct.length < raw.length) {
            val punctCount = raw.length - trimmedPunct.length
            listOf(
                CandidateSlice(raw, scanned.endExclusive),
                CandidateSlice(trimmedPunct, scanned.endExclusive - punctCount)
            )
        } else {
            listOf(CandidateSlice(raw, scanned.endExclusive))
        }
    }

    private fun parseNaturalDateToken(
        phrase: String,
        isQuoted: Boolean,
        locale: Locale,
        referenceDate: LocalDate
    ): ParsedNaturalDate? {
        MultilingualDateParser.parse(phrase, locale, referenceDate)?.let { return it }
        if (!isQuoted && '-' in phrase) {
            val spaced = phrase.replace('-', ' ').replace(MULTI_SPACE_REGEX, " ").trim()
            if (spaced.isNotEmpty()) {
                MultilingualDateParser.parse(spaced, locale, referenceDate)?.let { return it }
            }
        }
        return null
    }

    private fun parseDurationToken(phrase: String, isQuoted: Boolean): Int? {
        parseCustomDurationMinutes(phrase)?.let { return it }
        if (!isQuoted && '-' in phrase) {
            val spaced = phrase.replace('-', ' ').trim()
            if (spaced.isNotEmpty()) {
                parseCustomDurationMinutes(spaced)?.let { return it }
            }
        }
        return null
    }

    private fun matchTag(phrase: String, isQuoted: Boolean, activeTags: List<Tag>): Tag? {
        if (activeTags.isEmpty()) return null
        val clean = phrase.trim()
        if (clean.isEmpty()) return null
        activeTags.firstOrNull { it.title.trim().equals(clean, ignoreCase = true) }?.let { return it }
        if (!isQuoted && '-' in clean) {
            val spaced = clean.replace('-', ' ').replace(MULTI_SPACE_REGEX, " ").trim()
            activeTags.firstOrNull { it.title.trim().equals(spaced, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    private fun matchProject(phrase: String, isQuoted: Boolean, activeProjects: List<Project>): Project? {
        if (activeProjects.isEmpty()) return null
        val clean = phrase.trim()
        if (clean.isEmpty()) return null

        // 1. Exact title match (case-insensitive)
        activeProjects.firstOrNull { it.title.trim().equals(clean, ignoreCase = true) }?.let { return it }

        // 2. Hyphen-to-space exact match for unquoted tokens
        val spaced = if (!isQuoted && '-' in clean) {
            clean.replace('-', ' ').replace(MULTI_SPACE_REGEX, " ").trim()
        } else {
            null
        }
        if (!spaced.isNullOrEmpty()) {
            activeProjects.firstOrNull { it.title.trim().equals(spaced, ignoreCase = true) }?.let { return it }
        }

        // 3. Unambiguous prefix match (>= 3 chars)
        val prefixVariants = buildList {
            if (clean.length >= 3) add(clean)
            if (spaced != null && spaced.length >= 3 && !spaced.equals(clean, ignoreCase = true)) add(spaced)
        }
        if (prefixVariants.isNotEmpty()) {
            val prefixMatches = activeProjects.filter { proj ->
                val projTitle = proj.title.trim()
                prefixVariants.any { prefix -> projTitle.startsWith(prefix, ignoreCase = true) }
            }
            if (prefixMatches.size == 1) {
                return prefixMatches.single()
            }
        }
        return null
    }

    private fun matchArea(phrase: String, isQuoted: Boolean, activeAreas: List<Area>): Area? {
        if (activeAreas.isEmpty()) return null
        val clean = phrase.trim()
        if (clean.isEmpty()) return null

        // 1. Exact title match (case-insensitive)
        activeAreas.firstOrNull { it.title.trim().equals(clean, ignoreCase = true) }?.let { return it }

        // 2. Hyphen-to-space exact match for unquoted tokens
        val spaced = if (!isQuoted && '-' in clean) {
            clean.replace('-', ' ').replace(MULTI_SPACE_REGEX, " ").trim()
        } else {
            null
        }
        if (!spaced.isNullOrEmpty()) {
            activeAreas.firstOrNull { it.title.trim().equals(spaced, ignoreCase = true) }?.let { return it }
        }

        // 3. Unambiguous prefix match (>= 3 chars)
        val prefixVariants = buildList {
            if (clean.length >= 3) add(clean)
            if (spaced != null && spaced.length >= 3 && !spaced.equals(clean, ignoreCase = true)) add(spaced)
        }
        if (prefixVariants.isNotEmpty()) {
            val prefixMatches = activeAreas.filter { area ->
                val areaTitle = area.title.trim()
                prefixVariants.any { prefix -> areaTitle.startsWith(prefix, ignoreCase = true) }
            }
            if (prefixMatches.size == 1) {
                return prefixMatches.single()
            }
        }
        return null
    }
}
