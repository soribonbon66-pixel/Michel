package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = StudioCyanPrimary,
    onPrimary = Color.Black,
    primaryContainer = StudioBlueSecondary,
    onPrimaryContainer = Color.White,
    secondary = StudioBlueSecondary,
    onSecondary = Color.White,
    tertiary = StudioAmberAccent,
    onTertiary = Color.Black,
    background = StudioDarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = StudioDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = StudioSurfaceContainer,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = StudioBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to studio dark mode
    dynamicColor: Boolean = false, // Keep high-contrast studio rack aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
