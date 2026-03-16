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
    primary = Color(0xFF5DDEC8),
    onPrimary = Color(0xFF03231E),
    primaryContainer = Color(0xFF1B4741),
    onPrimaryContainer = Color(0xFFD5FFF8),
    secondary = Color(0xFFFFC978),
    onSecondary = Color(0xFF2E1800),
    secondaryContainer = Color(0xFF61400F),
    onSecondaryContainer = Color(0xFFFFE7C0),
    tertiary = Color(0xFF98DCFF),
    onTertiary = Color(0xFF00263A),
    background = Color(0xFF081319),
    onBackground = Color(0xFFF2FBF9),
    surface = Color(0xFF122028),
    onSurface = Color(0xFFF2FBF9),
    surfaceVariant = Color(0xFF233B44),
    onSurfaceVariant = Color(0xFFD3E5E9),
    outline = Color(0xFF5D7983),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF330705)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E8C7B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F3EC),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFFB46A12),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDB8),
    onSecondaryContainer = Color(0xFF3A1F00),
    tertiary = Color(0xFF156AA1),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6FBFA),
    onBackground = Color(0xFF102126),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF102126),
    surfaceVariant = Color(0xFFE2F0EE),
    onSurfaceVariant = Color(0xFF496169),
    outline = Color(0xFF6A828A),
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
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
)

@Composable
fun EX57Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
