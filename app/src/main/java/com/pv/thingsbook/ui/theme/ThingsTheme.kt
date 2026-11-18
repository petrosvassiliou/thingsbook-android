package com.pv.thingsbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class ThingsColorPalette(
    val isDark: Boolean,
    val canvasBackground: Color,
    val sidebarBackground: Color,
    val cardSurface: Color,
    val cardElevatedSurface: Color,
    val hairlineDivider: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val thingsBlue: Color,
    val thingsBlueSoft: Color,
    val inboxBlue: Color,
    val todayStarYellow: Color,
    val eveningMoonIndigo: Color,
    val upcomingCoralRed: Color,
    val anytimeTeal: Color,
    val somedayWarmAmber: Color,
    val logbookGreen: Color,
    val deadlineUrgentRed: Color,
    val deadlineSoftBg: Color,
    val tagPillBg: Color,
    val tagPillText: Color,
    val checkboxBorder: Color,
    val checkboxCheckedFill: Color
)

val LightThingsPalette = ThingsColorPalette(
    isDark = false,
    canvasBackground = Color(0xFFFFFFFF),
    sidebarBackground = Color(0xFFF4F5F8),
    cardSurface = Color(0xFFFFFFFF),
    cardElevatedSurface = Color(0xFFFFFFFF),
    hairlineDivider = Color(0xFFE7EBF1),
    primaryText = Color(0xFF15181E),
    secondaryText = Color(0xFF5F6775),
    tertiaryText = Color(0xFF9199A8),
    thingsBlue = Color(0xFF3898EC),
    thingsBlueSoft = Color(0xFFE5F3FF),
    inboxBlue = Color(0xFF3898EC),
    todayStarYellow = Color(0xFF3898EC),
    eveningMoonIndigo = Color(0xFF4F7AD6),
    upcomingCoralRed = Color(0xFF3898EC),
    anytimeTeal = Color(0xFF3898EC),
    somedayWarmAmber = Color(0xFFD69F3A),
    logbookGreen = Color(0xFF2EA968),
    deadlineUrgentRed = Color(0xFFE53935),
    deadlineSoftBg = Color(0xFFFFECEB),
    tagPillBg = Color(0xFFF1F3F7),
    tagPillText = Color(0xFF535B68),
    checkboxBorder = Color(0xFFBCC4D2),
    checkboxCheckedFill = Color(0xFF3898EC)
)

val DarkThingsPalette = ThingsColorPalette(
    isDark = true,
    canvasBackground = Color(0xFF121417),
    sidebarBackground = Color(0xFF171A1E),
    cardSurface = Color(0xFF1B1F25),
    cardElevatedSurface = Color(0xFF20252D),
    hairlineDivider = Color(0xFF272C35),
    primaryText = Color(0xFFF3F5F8),
    secondaryText = Color(0xFF9AA3B2),
    tertiaryText = Color(0xFF697282),
    thingsBlue = Color(0xFF6BC0FF),
    thingsBlueSoft = Color(0xFF192C40),
    inboxBlue = Color(0xFF6BC0FF),
    todayStarYellow = Color(0xFF6BC0FF),
    eveningMoonIndigo = Color(0xFF8AAEF8),
    upcomingCoralRed = Color(0xFF6BC0FF),
    anytimeTeal = Color(0xFF6BC0FF),
    somedayWarmAmber = Color(0xFFFBBF24),
    logbookGreen = Color(0xFF4ADE80),
    deadlineUrgentRed = Color(0xFFFF5B52),
    deadlineSoftBg = Color(0xFF3D2224),
    tagPillBg = Color(0xFF242932),
    tagPillText = Color(0xFFB5BECC),
    checkboxBorder = Color(0xFF545D6D),
    checkboxCheckedFill = Color(0xFF6BC0FF)
)

val LocalThingsColors = staticCompositionLocalOf { LightThingsPalette }

val ThingsTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    )
)

object ThingsTheme {
    val colors: ThingsColorPalette
        @Composable
        get() = LocalThingsColors.current
}

@Composable
fun Things3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkThingsPalette else LightThingsPalette

    val m3Scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.thingsBlue,
            background = palette.canvasBackground,
            surface = palette.cardSurface,
            onPrimary = Color.White,
            onBackground = palette.primaryText,
            onSurface = palette.primaryText,
            error = palette.deadlineUrgentRed
        )
    } else {
        lightColorScheme(
            primary = palette.thingsBlue,
            background = palette.canvasBackground,
            surface = palette.cardSurface,
            onPrimary = Color.White,
            onBackground = palette.primaryText,
            onSurface = palette.primaryText,
            error = palette.deadlineUrgentRed
        )
    }

    CompositionLocalProvider(LocalThingsColors provides palette) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = ThingsTypography,
            content = content
        )
    }
}
