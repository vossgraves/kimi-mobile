package com.kimimobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Claude's visual language, taken from the app itself rather than Material's
 * defaults: a true-black canvas, flat grey cards separated by hairlines, thin
 * outline icons, serif for headings, and colour used sparingly — terracotta
 * for identity, blue for state.
 */
object Claude {
    // Canvas and cards
    val Black = Color(0xFF000000)
    val Card = Color(0xFF1C1C1E)
    val CardPressed = Color(0xFF2A2A2C)
    val Elevated = Color(0xFF242426)
    val Hairline = Color(0xFF2E2E30)

    // Text
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFF9B9B9E)
    val TextTertiary = Color(0xFF6E6E72)

    // Accents
    val Terracotta = Color(0xFFD2795A)  // wordmark, new chat, logo
    val Blue = Color(0xFF3B82F6)        // toggles, current selection
    val Danger = Color(0xFFE5484D)

    // Light mode equivalents — the app is designed dark, this is a courtesy.
    val LightCanvas = Color(0xFFFAF9F7)
    val LightCard = Color(0xFFFFFFFF)
    val LightHairline = Color(0xFFE6E4E0)
    val LightTextPrimary = Color(0xFF1A1A1A)
    val LightTextSecondary = Color(0xFF6B6B6B)
}

private val DarkColors = darkColorScheme(
    primary = Claude.Blue,
    onPrimary = Color.White,
    primaryContainer = Claude.Elevated,
    onPrimaryContainer = Claude.TextPrimary,
    secondary = Claude.Terracotta,
    onSecondary = Color.White,
    secondaryContainer = Claude.Card,
    onSecondaryContainer = Claude.TextPrimary,
    tertiary = Claude.Terracotta,
    onTertiary = Color.White,
    tertiaryContainer = Claude.Elevated,
    onTertiaryContainer = Claude.TextPrimary,
    error = Claude.Danger,
    onError = Color.White,
    errorContainer = Color(0xFF3B1416),
    onErrorContainer = Color(0xFFFFB4AB),
    background = Claude.Black,
    onBackground = Claude.TextPrimary,
    surface = Claude.Black,
    onSurface = Claude.TextPrimary,
    surfaceVariant = Claude.Card,
    onSurfaceVariant = Claude.TextSecondary,
    outline = Claude.TextTertiary,
    outlineVariant = Claude.Hairline,
    surfaceContainerLowest = Claude.Black,
    surfaceContainerLow = Claude.Card,
    surfaceContainer = Claude.Card,
    surfaceContainerHigh = Claude.Elevated,
    surfaceContainerHighest = Claude.CardPressed,
    scrim = Color(0xCC000000),
)

private val LightColors = lightColorScheme(
    primary = Claude.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF0B3D91),
    secondary = Claude.Terracotta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E5DE),
    onSecondaryContainer = Color(0xFF4A1F0F),
    tertiary = Claude.Terracotta,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E5DE),
    onTertiaryContainer = Color(0xFF4A1F0F),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    background = Claude.LightCanvas,
    onBackground = Claude.LightTextPrimary,
    surface = Claude.LightCanvas,
    onSurface = Claude.LightTextPrimary,
    surfaceVariant = Claude.LightCard,
    onSurfaceVariant = Claude.LightTextSecondary,
    outline = Color(0xFF8A8A8A),
    outlineVariant = Claude.LightHairline,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Claude.LightCard,
    surfaceContainer = Claude.LightCard,
    surfaceContainerHigh = Color(0xFFF2F0EC),
    surfaceContainerHighest = Color(0xFFEBE9E4),
)

// Cards are generously rounded; pills are fully round.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Serif for headings and moments of identity, sans for everything functional.
private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
)

@Composable
fun KimiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic colour is deliberately unused: the point is to look like Claude,
    // not like the wallpaper.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
