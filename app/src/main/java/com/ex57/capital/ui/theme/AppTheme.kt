package com.ex57.capital.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFD60A),
    onPrimary = Color(0xFF050505),
    primaryContainer = Color(0xFF3A3100),
    onPrimaryContainer = Color(0xFFFFF2A8),
    secondary = Color(0xFFFFB000),
    onSecondary = Color(0xFF090909),
    secondaryContainer = Color(0xFF302400),
    onSecondaryContainer = Color(0xFFFFE29A),
    tertiary = Color(0xFFECECEC),
    onTertiary = Color(0xFF101010),
    background = Color(0xFF050505),
    onBackground = Color(0xFFF8F8F2),
    surface = Color(0xFF101010),
    onSurface = Color(0xFFF8F8F2),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFD6D6D6),
    outline = Color(0xFF66604A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF330705)
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
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
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
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
)

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
