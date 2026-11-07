package com.culturedcode.things3.ui.theme

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
    canvasBackground = Color(0xFFF5F6F8),
    sidebarBackground = Color(0xFFECEEF2),
    cardSurface = Color(0xFFFFFFFF),
    cardElevatedSurface = Color(0xFFFFFFFF),
    hairlineDivider = Color(0xFFE5E7EB),
    primaryText = Color(0xFF1C1E21),
    secondaryText = Color(0xFF6E7582),
    tertiaryText = Color(0xFF9AA1AE),
    thingsBlue = Color(0xFF248BFD),
    thingsBlueSoft = Color(0xFFE8F3FF),
    inboxBlue = Color(0xFF1F9CF0),
    todayStarYellow = Color(0xFFFFC400),
    eveningMoonIndigo = Color(0xFF5E6AD2),
    upcomingCoralRed = Color(0xFFFF4F5E),
    anytimeTeal = Color(0xFF15A697),
    somedayWarmAmber = Color(0xFFD69F3A),
    logbookGreen = Color(0xFF34C759),
    deadlineUrgentRed = Color(0xFFFF3B30),
    deadlineSoftBg = Color(0xFFFFECEB),
    tagPillBg = Color(0xFFEEF0F4),
    tagPillText = Color(0xFF5A6270),
    checkboxBorder = Color(0xFFC4C9D4),
    checkboxCheckedFill = Color(0xFF248BFD)
)

val DarkThingsPalette = ThingsColorPalette(
    isDark = true,
    canvasBackground = Color(0xFF1B1D22),
    sidebarBackground = Color(0xFF16181C),
    cardSurface = Color(0xFF24262D),
    cardElevatedSurface = Color(0xFF2B2E37),
    hairlineDivider = Color(0xFF2E313B),
    primaryText = Color(0xFFF3F4F6),
    secondaryText = Color(0xFF9CA3AF),
    tertiaryText = Color(0xFF6B7280),
    thingsBlue = Color(0xFF3B9EFF),
    thingsBlueSoft = Color(0xFF1F344D),
    inboxBlue = Color(0xFF38BDF8),
    todayStarYellow = Color(0xFFFFD426),
    eveningMoonIndigo = Color(0xFF818CF8),
    upcomingCoralRed = Color(0xFFFF6B78),
    anytimeTeal = Color(0xFF2DD4BF),
    somedayWarmAmber = Color(0xFFFBBF24),
    logbookGreen = Color(0xFF4ADE80),
    deadlineUrgentRed = Color(0xFFFF5B52),
    deadlineSoftBg = Color(0xFF3D2224),
    tagPillBg = Color(0xFF2F333D),
    tagPillText = Color(0xFFB4BCC8),
    checkboxBorder = Color(0xFF525866),
    checkboxCheckedFill = Color(0xFF3B9EFF)
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
