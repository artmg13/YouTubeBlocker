package com.example.youtubeblocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF356654), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EBDD), onPrimaryContainer = Color(0xFF12382A),
    secondaryContainer = Color(0xFFE2EBDF), onSecondaryContainer = Color(0xFF253B2F),
    background = Color(0xFFF8F9F4), surface = Color(0xFFF8F9F4),
    surfaceContainerHigh = Color(0xFFEBEEE6),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA0D0B8), onPrimary = Color(0xFF073826),
    primaryContainer = Color(0xFF214F3D), onPrimaryContainer = Color(0xFFBCEED3),
    secondaryContainer = Color(0xFF30473A), onSecondaryContainer = Color(0xFFD2E8D7),
    background = Color(0xFF111610), surface = Color(0xFF111610),
)

@Composable
fun YouTubeBlockerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography, content = content)
}
