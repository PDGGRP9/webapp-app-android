package com.pdg.braceletconnecte.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw design tokens mirroring the web frontend's CSS custom properties (app.css/login.css).
 * The dark screens (Dashboard/Stats/Account) should prefer `MaterialTheme.colorScheme.*`
 * (already wired to these same values). This object exists for the auth screens
 * (Login/Register/ForgotPassword), which invert the palette to a lime background with ink
 * text — outside what a single app-wide dark ColorScheme can express.
 */
object AppColors {
    val Lime = Color(0xFFEBFD72)
    val LimeSoft = Color(0x24EBFD72)
    val LimeFaint = Color(0x12EBFD72)
    val Ink = Color(0xFF141A05)
    val InkSoft = Color(0xFF3A4416)
    val Bg = Color(0xFF0E1204)
    val Card = Color(0xFF171E07)
    val Card2 = Color(0xFF1D2609)
    val Line = Color(0x29EBFD72)
    val Text = Color(0xFFF4FFCF)
    val Muted = Color(0xFFA9B87C)
    val Danger = Color(0xFFFF9C8A)
}
