package com.kimi3.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Warm, editorial palette inspired by Claude's design language ----
// Terracotta accent, cream/ivory surfaces, olive-toned darks. No cool grays.

private val LightColors = lightColorScheme(
    primary = Color(0xFF9C4122),           // terracotta
    onPrimary = Color(0xFFFFF8F5),
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF361000),
    secondary = Color(0xFF76574A),         // warm taupe
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCB),
    onSecondaryContainer = Color(0xFF2C160D),
    tertiary = Color(0xFF6A5D2F),          // olive
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1E3A9),
    onTertiaryContainer = Color(0xFF211A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF9F5),        // pampas cream
    onBackground = Color(0xFF1F1B17),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF1F1B17),
    surfaceVariant = Color(0xFFF2E9E2),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD5C3B9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F2EC),
    surfaceContainer = Color(0xFFEEECE5),
    surfaceContainerHigh = Color(0xFFE9E6DF),
    surfaceContainerHighest = Color(0xFFE3E0D9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB69A),           // warm coral
    onPrimary = Color(0xFF5C1C00),
    primaryContainer = Color(0xFF7A2E0C),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFE7BEAE),
    onSecondary = Color(0xFF422A1F),
    secondaryContainer = Color(0xFF5C4034),
    onSecondaryContainer = Color(0xFFFFDBCB),
    tertiary = Color(0xFFD5C78E),
    onTertiary = Color(0xFF383000),
    tertiaryContainer = Color(0xFF50471B),
    onTertiaryContainer = Color(0xFFF1E3A9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1715),        // olive-tinted near black
    onBackground = Color(0xFFEDE0D8),
    surface = Color(0xFF1A1715),
    onSurface = Color(0xFFEDE0D8),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD5C3B9),
    outline = Color(0xFF9E8B81),
    outlineVariant = Color(0xFF52443C),
    surfaceContainerLowest = Color(0xFF141210),
    surfaceContainerLow = Color(0xFF221F1C),
    surfaceContainer = Color(0xFF262320),
    surfaceContainerHigh = Color(0xFF312D2A),
    surfaceContainerHighest = Color(0xFF3C3834),
)

// Generously rounded, Claude-style softness
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Editorial hierarchy: serif for display moments, sans for body
private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
)

@Composable
fun KimiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        // SDK_INT >= S is guaranteed by the check, so no try/catch around composables.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}