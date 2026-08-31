package com.pdg.braceletconnecte.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** CSS `clamp(minRem, vwFraction*100vw, maxRem)`-equivalent responsive font size. */
@Composable
fun clampSp(minRem: Float, vwFraction: Float, maxRem: Float): TextUnit {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val vwDp = screenWidthDp * vwFraction
    return vwDp.coerceIn(minRem * 16f, maxRem * 16f).sp
}
