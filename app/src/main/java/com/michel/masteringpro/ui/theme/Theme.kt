package com.michel.masteringpro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object MichelColors {
    val Background = Color(0xFF07111B)
    val Surface = Color(0xFF0D1A26)
    val Elevated = Color(0xFF132332)
    val Border = Color(0xFF213342)
    val Primary = Color(0xFF74E6B4)
    val Blue = Color(0xFF68B9FF)
    val Violet = Color(0xFFB39DFF)
    val Amber = Color(0xFFFFC66D)
    val Red = Color(0xFFFF6C78)
    val Green = Color(0xFF74E6B4)
    val Text = Color(0xFFF3F7FA)
    val Muted = Color(0xFF9AAAB8)
    val Faint = Color(0xFF647686)
}

private val MichelScheme = darkColorScheme(
    primary = MichelColors.Primary,
    onPrimary = Color(0xFF062016),
    secondary = MichelColors.Blue,
    onSecondary = Color(0xFF07111B),
    tertiary = MichelColors.Violet,
    background = MichelColors.Background,
    onBackground = MichelColors.Text,
    surface = MichelColors.Surface,
    onSurface = MichelColors.Text,
    surfaceVariant = MichelColors.Elevated,
    onSurfaceVariant = MichelColors.Muted,
    outline = MichelColors.Border,
    error = MichelColors.Red
)

@Composable
fun MichelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MichelScheme,
        content = content
    )
}
