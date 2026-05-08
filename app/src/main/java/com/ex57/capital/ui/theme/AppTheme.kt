package com.ex57.capital.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC5A059),
    onPrimary = Color(0xFF1A1306),
    primaryContainer = Color(0xFF3A2C12),
    onPrimaryContainer = Color(0xFFF3E2BB),
    secondary = Color(0xFFDAA520),
    onSecondary = Color(0xFF1B1405),
    secondaryContainer = Color(0xFF3D2F12),
    onSecondaryContainer = Color(0xFFF7E3B0),
    tertiary = Color(0xFFE6D2A3),
    onTertiary = Color(0xFF251A07),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1F1F1F),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2620),
    onSurfaceVariant = Color(0xFFC9B99B),
    outline = Color(0xFF6D5A3A),
    error = Color(0xFFFF5C5C),
    onError = Color(0xFF2D020B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFE0A800),
    onPrimary = Color(0xFF050505),
    primaryContainer = Color(0xFFFFED99),
    onPrimaryContainer = Color(0xFF161200),
    secondary = Color(0xFF7D5C00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE7A8),
    onSecondaryContainer = Color(0xFF221900),
    tertiary = Color(0xFF333333),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F7F3),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE8E4D2),
    onSurfaceVariant = Color(0xFF4F4A39),
    outline = Color(0xFF7A725B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
)

@Immutable
data class AppStatusPalette(
    val accentTeal: Color = Color(0xFFC5A059),
    val success: Color = Color(0xFF2CEF9F),
    val warning: Color = Color(0xFFFF7066),
    val danger: Color = Color(0xFFFF5C5C),
    val textPrimary: Color = Color(0xFFF9FAFB)
)

val StatusPalette = AppStatusPalette()

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

@Composable
fun EX57Theme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
