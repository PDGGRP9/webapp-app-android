package com.pdg.braceletconnecte.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

// Same relative segment shape as the web/mockup's PPG SVG path
// ("l10 0 l7 -46 l8 36 l6 -12 l8 14 l12 6 l49 2"), reused for both the login screen's
// decorative ECG line and the dashboard's live-chart wave.
private val PULSE_DX = floatArrayOf(10f, 7f, 8f, 6f, 8f, 12f, 49f)
private val PULSE_DY = floatArrayOf(0f, -46f, 36f, -12f, 14f, 6f, 2f)
private const val PULSE_UNIT_WIDTH = 100f
private const val PULSE_VIEWBOX_HEIGHT = 96f
private const val PULSE_BASELINE_FRACTION = 70f / PULSE_VIEWBOX_HEIGHT
private const val VISIBLE_UNITS = 3.2f

/** Purely decorative, infinitely-scrolling heartbeat waveform — mirrors `.live-chart`/`.pulse`. */
@Composable
fun PulseWaveCanvas(
    modifier: Modifier = Modifier,
    lineColor: Color = LocalContentColor.current,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp,
    cycleDurationMs: Int = 3200,
    showGridLines: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "pulse-wave")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(cycleDurationMs, easing = LinearEasing)),
        label = "pulse-wave-progress",
    )
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Canvas(modifier = modifier.fillMaxWidth()) {
        if (showGridLines) {
            val lineSpacingPx = 24.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                y += lineSpacingPx
            }
        }

        val unitWidthPx = size.width / VISIBLE_UNITS
        val offsetPx = progress * unitWidthPx
        val unitsNeeded = ceil(size.width / unitWidthPx).toInt() + 2

        val path = Path()
        var first = true
        for (unitIndex in -1..unitsNeeded) {
            val startX = unitIndex * unitWidthPx - offsetPx
            var x = startX
            var y = size.height * PULSE_BASELINE_FRACTION
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.moveTo(x, y)
            }
            for (i in PULSE_DX.indices) {
                x += unitWidthPx * (PULSE_DX[i] / PULSE_UNIT_WIDTH)
                y += size.height * (PULSE_DY[i] / PULSE_VIEWBOX_HEIGHT)
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
