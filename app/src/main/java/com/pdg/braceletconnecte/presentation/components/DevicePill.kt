package com.pdg.braceletconnecte.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Header connectivity pill with a pulsing dot — mirrors the web design's `.device` pill. */
@Composable
fun DevicePill(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.error,
    onClick: (() -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "device-led")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "device-led-alpha",
    )
    val dotColor = if (isActive) activeColor else inactiveColor

    Pill(
        text = label,
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        leading = {
            Canvas(modifier = Modifier.size(7.dp)) {
                drawCircle(color = dotColor.copy(alpha = if (isActive) alpha else 1f))
            }
        },
    )
}
