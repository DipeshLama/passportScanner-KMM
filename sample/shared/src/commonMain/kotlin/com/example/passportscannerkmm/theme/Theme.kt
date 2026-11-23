package com.example.passportscannerkmm.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object DemoColors {
    val Brand = Color(0xFF2F4BFF)
    val BrandDeep = Color(0xFF1B2A8F)
    val BrandSoft = Color(0xFF7C8CFF)
    val Success = Color(0xFF00A86B)
    val Danger = Color(0xFFE5484D)
}

private val LightScheme = lightColorScheme(
    primary = DemoColors.Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E7FF),
    onPrimaryContainer = DemoColors.BrandDeep,
    background = Color(0xFFF6F7FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEFF7),
    onSurface = Color(0xFF14172B),
    onSurfaceVariant = Color(0xFF5B6076),
    outlineVariant = Color(0xFFDDE0EC),
)

private val DarkScheme = darkColorScheme(
    primary = DemoColors.BrandSoft,
    onPrimary = Color(0xFF0B1033),
    primaryContainer = Color(0xFF26307A),
    onPrimaryContainer = Color(0xFFE3E7FF),
    background = Color(0xFF0D0F1A),
    surface = Color(0xFF171A2A),
    surfaceVariant = Color(0xFF222640),
    onSurface = Color(0xFFEDEEF7),
    onSurfaceVariant = Color(0xFFA3A8C3),
    outlineVariant = Color(0xFF2E3354),
)

private val DemoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun DemoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        shapes = DemoShapes,
        content = content,
    )
}
