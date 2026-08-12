package com.pdg.braceletconnecte.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.Instant

/** Hand-rolled Canvas line chart, mirroring the web frontend's own from-scratch SVG chart — no charting library. */
@Composable
fun LineChartCanvas(
    points: List<Pair<Instant, Float>>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.size < 2) {
        Box(
            modifier = modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Pas assez de données pour tracer un graphique.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val sorted = points.sortedBy { it.first }
    val minX = sorted.first().first.epochSecond.toFloat()
    val maxX = sorted.last().first.epochSecond.toFloat()
    val minY = sorted.minOf { it.second }
    val maxY = sorted.maxOf { it.second }
    val xRange = (maxX - minX).takeIf { it > 0f } ?: 1f
    val yRange = (maxY - minY).takeIf { it > 0f } ?: 1f

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        fun xOf(instant: Instant) = ((instant.epochSecond.toFloat() - minX) / xRange) * size.width
        fun yOf(value: Float) = size.height - ((value - minY) / yRange) * size.height

        val path = Path()
        sorted.forEachIndexed { index, (instant, value) ->
            val x = xOf(instant)
            val y = yOf(value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 4f))

        sorted.forEach { (instant, value) ->
            drawCircle(color = lineColor, radius = 4f, center = Offset(xOf(instant), yOf(value)))
        }
    }
}
