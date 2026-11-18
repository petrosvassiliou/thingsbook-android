package com.pv.thingsbook.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pv.thingsbook.ui.theme.ThingsTheme

enum class DetectedNoteLinkType {
    WEB,
    EMAIL,
    PHONE,
    MAP
}

data class DetectedNoteLink(
    val type: DetectedNoteLinkType,
    val displayLabel: String,
    val targetUri: String
)

private val HEADING_REGEX = Regex("(?m)^(#{1,4})\\s+(.+)$")
private val BULLET_REGEX = Regex("(?m)^(\\s*[-*•]\\s+)")
private val BOLD_REGEX = Regex("(\\*\\*|__)([^*_\\n]+?)\\1")
private val ITALIC_REGEX = Regex("(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)")
private val INLINE_CODE_REGEX = Regex("`([^`\\n]+?)`")
private val URL_REGEX = Regex("(?i)\\b((?:https?://|www\\.)[^\\s<>()\"']+)")
private val EMAIL_REGEX = Regex("\\b([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b")
private val PHONE_REGEX = Regex("(?<!\\w)(\\+?[0-9][0-9\\s().-]{6,}[0-9])(?!\\w)")
private val EXPLICIT_LOCATION_REGEX = Regex("(?im)(?:📍|(?:location|ort|adresse|address|map|lieu|indirizzo|dirección|ubicación)\\s*:)\\s*([^\\n]{3,80})")
private val GPS_COORDS_REGEX = Regex("(?<!\\d)(-?\\d{1,2}\\.\\d{3,8}\\s*,\\s*-?\\d{1,3}\\.\\d{3,8})(?!\\d)")
private val EU_STREET_ADDRESS_REGEX = Regex("(?m)(?:^|(?:[-*•]|\\bat)\\s+)([A-ZÄÖÜ][A-Za-zÄÖÜäöüßéèêàùç.\\-\\s]{2,34}(?:straße|strasse|str\\.|weg|platz|allee|gasse|ring|damm|ufer|rue|avenue|boulevard|via|piazza|calle|avenida)\\s+\\d{1,4}[a-zA-Z]?(?:\\s*,\\s*\\d{4,5}\\s+[A-ZÄÖÜa-zäöüß\\-\\s]{2,25})?)", RegexOption.IGNORE_CASE)
private val US_STREET_ADDRESS_REGEX = Regex("(?m)\\b(\\d{1,5}\\s+[A-Z][A-Za-z0-9.\\-\\s]{2,30}\\s+(?:St|Street|Ave|Avenue|Rd|Road|Blvd|Boulevard|Dr|Drive|Ln|Lane|Way|Ct|Court|Pl|Place|Pkwy|Parkway|Hwy|Highway)\\b(?:\\s*,\\s*[A-Za-z.\\-\\s]{2,25})?)")

fun extractDetectedLinksFromNotes(notes: String): List<DetectedNoteLink> {
    if (notes.isBlank()) return emptyList()
    val results = mutableListOf<DetectedNoteLink>()
    val occupiedRanges = mutableListOf<IntRange>()

    for (match in URL_REGEX.findAll(notes)) {
        val raw = match.value.trimEnd('.', ',', ';', ':', ')', ']', '!', '?')
        if (raw.length < 4) continue
        val uri = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            raw
        } else {
            "https://$raw"
        }
        val cleanHost = raw
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .take(36)
        results.add(DetectedNoteLink(DetectedNoteLinkType.WEB, cleanHost, uri))
        occupiedRanges.add(match.range)
    }

    for (match in EMAIL_REGEX.findAll(notes)) {
        if (occupiedRanges.any { it.first <= match.range.last && match.range.first <= it.last }) continue
        val email = match.value
        results.add(DetectedNoteLink(DetectedNoteLinkType.EMAIL, email.take(36), "mailto:$email"))
        occupiedRanges.add(match.range)
    }

    for (match in EXPLICIT_LOCATION_REGEX.findAll(notes)) {
        if (occupiedRanges.any { it.first <= match.range.last && match.range.first <= it.last }) continue
        val loc = match.groupValues.getOrNull(1)?.trim()?.trimEnd('.', ',', ';').orEmpty()
        if (loc.length >= 3) {
            val encoded = java.net.URLEncoder.encode(loc, "UTF-8")
            results.add(DetectedNoteLink(DetectedNoteLinkType.MAP, loc.take(34), "geo:0,0?q=$encoded"))
            occupiedRanges.add(match.range)
        }
    }

    for (match in GPS_COORDS_REGEX.findAll(notes)) {
        if (occupiedRanges.any { it.first <= match.range.last && match.range.first <= it.last }) continue
        val coords = match.groupValues.getOrNull(1)?.replace(" ", "").orEmpty()
        if (coords.isNotEmpty()) {
            results.add(DetectedNoteLink(DetectedNoteLinkType.MAP, coords, "geo:$coords?q=$coords"))
            occupiedRanges.add(match.range)
        }
    }

    for (regex in listOf(EU_STREET_ADDRESS_REGEX, US_STREET_ADDRESS_REGEX)) {
        for (match in regex.findAll(notes)) {
            if (occupiedRanges.any { it.first <= match.range.last && match.range.first <= it.last }) continue
            val addr = match.groupValues.getOrNull(1)?.trim()?.trimEnd('.', ',', ';').orEmpty()
            if (addr.length >= 5) {
                val encoded = java.net.URLEncoder.encode(addr, "UTF-8")
                results.add(DetectedNoteLink(DetectedNoteLinkType.MAP, addr.take(34), "geo:0,0?q=$encoded"))
                occupiedRanges.add(match.range)
            }
        }
    }

    for (match in PHONE_REGEX.findAll(notes)) {
        if (occupiedRanges.any { it.first <= match.range.last && match.range.first <= it.last }) continue
        val rawPhone = match.value.trim()
        val digitCount = rawPhone.count { it.isDigit() }
        // Exclude ISO dates like 2026-11-13
        if (digitCount !in 7..15 || Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(rawPhone)) continue
        val dialDigits = rawPhone.filter { it.isDigit() || it == '+' }
        results.add(DetectedNoteLink(DetectedNoteLinkType.PHONE, rawPhone.take(24), "tel:$dialDigits"))
    }

    return results.distinctBy { it.targetUri }.take(8)
}

@Composable
fun rememberThingsMarkdownVisualTransformation(
    primaryColor: Color = ThingsTheme.colors.primaryText,
    secondaryColor: Color = ThingsTheme.colors.secondaryText,
    accentColor: Color = ThingsTheme.colors.thingsBlue,
    codeBgColor: Color = ThingsTheme.colors.tagPillBg
): VisualTransformation {
    return remember(primaryColor, secondaryColor, accentColor, codeBgColor) {
        VisualTransformation { text ->
            val raw = text.text
            if (raw.isEmpty()) {
                TransformedText(text, OffsetMapping.Identity)
            } else {
                val annotated = buildAnnotatedString {
                    append(raw)

                    // Headings: # Heading
                    for (m in HEADING_REGEX.findAll(raw)) {
                        val hashesRange = m.groups[1]?.range
                        if (hashesRange != null) {
                            addStyle(
                                SpanStyle(color = secondaryColor.copy(alpha = 0.55f), fontWeight = FontWeight.SemiBold),
                                hashesRange.first,
                                hashesRange.last + 1
                            )
                        }
                        addStyle(
                            SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                            m.range.first,
                            m.range.last + 1
                        )
                    }

                    // Bullet prefixes: - item, * item, • item
                    for (m in BULLET_REGEX.findAll(raw)) {
                        addStyle(
                            SpanStyle(color = accentColor, fontWeight = FontWeight.Bold),
                            m.range.first,
                            m.range.last + 1
                        )
                    }

                    // Bold: **text** or __text__
                    for (m in BOLD_REGEX.findAll(raw)) {
                        addStyle(
                            SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                            m.range.first,
                            m.range.last + 1
                        )
                        val delimLen = m.groups[1]?.value?.length ?: 2
                        addStyle(
                            SpanStyle(color = secondaryColor.copy(alpha = 0.45f)),
                            m.range.first,
                            (m.range.first + delimLen).coerceAtMost(raw.length)
                        )
                        addStyle(
                            SpanStyle(color = secondaryColor.copy(alpha = 0.45f)),
                            (m.range.last + 1 - delimLen).coerceAtLeast(0),
                            m.range.last + 1
                        )
                    }

                    // Italic: *text*
                    for (m in ITALIC_REGEX.findAll(raw)) {
                        addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic),
                            m.range.first,
                            m.range.last + 1
                        )
                    }

                    // Inline code: `code`
                    for (m in INLINE_CODE_REGEX.findAll(raw)) {
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = accentColor,
                                background = codeBgColor
                            ),
                            m.range.first,
                            m.range.last + 1
                        )
                    }

                    // Links, emails, phone numbers
                    for (m in URL_REGEX.findAll(raw)) {
                        addStyle(
                            SpanStyle(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            ),
                            m.range.first,
                            m.range.last + 1
                        )
                    }
                    for (m in EMAIL_REGEX.findAll(raw)) {
                        addStyle(
                            SpanStyle(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline
                            ),
                            m.range.first,
                            m.range.last + 1
                        )
                    }
                }
                TransformedText(annotated, OffsetMapping.Identity)
            }
        }
    }
}

@Composable
fun DetectedNotesLinksRow(
    notes: String,
    modifier: Modifier = Modifier
) {
    val links = remember(notes) { extractDetectedLinksFromNotes(notes) }
    if (links.isEmpty()) return

    val context = LocalContext.current
    val colors = ThingsTheme.colors

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        links.forEach { link ->
            val icon = when (link.type) {
                DetectedNoteLinkType.WEB -> Icons.Outlined.OpenInNew
                DetectedNoteLinkType.EMAIL -> Icons.Outlined.Email
                DetectedNoteLinkType.PHONE -> Icons.Outlined.Call
                DetectedNoteLinkType.MAP -> Icons.Outlined.Place
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.thingsBlue.copy(alpha = 0.10f))
                    .border(
                        width = 0.5.dp,
                        color = colors.thingsBlue.copy(alpha = 0.30f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        val launched = runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.targetUri)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            true
                        }.getOrDefault(false)
                        if (!launched && link.type == DetectedNoteLinkType.MAP) {
                            runCatching {
                                val fallbackUrl = "https://www.google.com/maps/search/?api=1&query=${Uri.encode(link.displayLabel)}"
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(webIntent)
                            }
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = link.displayLabel,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = link.displayLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.thingsBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
