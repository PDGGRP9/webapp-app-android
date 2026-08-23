package com.pdg.braceletconnecte.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Palette mirrors the web frontend's lime/dark tokens (src/styles/app.css, src/styles/login.css:
 * --lime, --bg, --card, --card-2, --line, --text, --muted, --danger) for brand consistency across
 * web and Android. The web app is dark-only (color-scheme: dark, hardcoded), so this theme
 * doesn't follow system light/dark mode.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFEBFD72),
    onPrimary = Color(0xFF141A05),
    primaryContainer = Color(0xFF3A4416),
    onPrimaryContainer = Color(0xFFF4FFCF),
    secondary = Color(0xFFEBFD72),
    onSecondary = Color(0xFF141A05),
    secondaryContainer = Color(0xFF1D2609),
    onSecondaryContainer = Color(0xFFF4FFCF),
    tertiary = Color(0xFFEBFD72),
    onTertiary = Color(0xFF141A05),
    tertiaryContainer = Color(0xFF3A4416),
    onTertiaryContainer = Color(0xFFF4FFCF),
    background = Color(0xFF0E1204),
    onBackground = Color(0xFFF4FFCF),
    surface = Color(0xFF171E07),
    onSurface = Color(0xFFF4FFCF),
    surfaceVariant = Color(0xFF1D2609),
    onSurfaceVariant = Color(0xFFA9B87C),
    surfaceContainerHigh = Color(0xFF171E07),
    outline = Color(0x29EBFD72),
    error = Color(0xFFFF9C8A),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFFE2DC),
)

@Composable
fun WebappAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
