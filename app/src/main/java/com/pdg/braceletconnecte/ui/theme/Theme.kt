package com.pdg.braceletconnecte.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Palette mirrors the web frontend's fixed dark tokens (src/styles/global.css: --bg, --accent,
 * --accent-2, --warning, --danger) for brand consistency across web and Android. The web app is
 * dark-only (color-scheme: dark, hardcoded), so this theme doesn't follow system light/dark mode.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF5AE4C7),
    onPrimary = Color(0xFF06251E),
    primaryContainer = Color(0xFF0F3D33),
    onPrimaryContainer = Color(0xFFCFFAF0),
    secondary = Color(0xFF5BB5FF),
    onSecondary = Color(0xFF0B2A4A),
    secondaryContainer = Color(0xFF11365C),
    onSecondaryContainer = Color(0xFFDCEBFF),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF78350F),
    tertiaryContainer = Color(0xFF92400E),
    onTertiaryContainer = Color(0xFFFFEDD5),
    background = Color(0xFF07111F),
    onBackground = Color(0xFFF7FAFC),
    surface = Color(0xFF0D1729),
    onSurface = Color(0xFFF7FAFC),
    surfaceVariant = Color(0xFF15233A),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFFB7185),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

@Composable
fun WebappAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
