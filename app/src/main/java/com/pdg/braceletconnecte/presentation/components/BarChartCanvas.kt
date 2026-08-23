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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant

/** Hand-rolled Canvas bar chart, mirroring the web frontend's BarChart.tsx — bars from a 0/[minDomain] baseline. */
@Composable
fun BarChartCanvas(
    points: List<Pair<Instant, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    minDomain: Float? = null,
    showGrid: Boolean = true,
    gridLineCount: Int = 4,
    xAxisLabelCount: Int = 4,
    formatValue: (Float) -> String = { it.toInt().toString() },
    formatXLabel: (Instant) -> String = { formatShortTime(it) },
) {
    if (points.isEmpty()) {
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
    val baseline = minDomain ?: 0f
    val maxY = (sorted.maxOf { it.second }.coerceAtLeast(baseline)) * 1.15f
    val yRange = (maxY - baseline).takeIf { it > 0f } ?: 1f
    val maxIndex = sorted.indices.maxByOrNull { sorted[it].second } ?: 0

    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val axisLabelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val endLabelStyle = TextStyle(fontSize = 12.sp, color = barColor, fontWeight = FontWeight.Bold)
    val dimBarColor = barColor.copy(alpha = 0.55f)

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val leftPadPx = 36.dp.toPx()
        val bottomPadPx = 20.dp.toPx()
        val plotWidth = size.width - leftPadPx
        val plotHeight = size.height - bottomPadPx

        fun yOf(value: Float) = plotHeight - ((value - baseline) / yRange) * plotHeight

        if (showGrid) {
            for (i in 0..gridLineCount) {
                val fraction = i / gridLineCount.toFloat()
                val value = baseline + fraction * (maxY - baseline)
                val y = plotHeight - fraction * plotHeight
                drawLine(gridColor, Offset(leftPadPx, y), Offset(size.width, y), strokeWidth = 1f)

                val measured = textMeasurer.measure(formatValue(value), axisLabelStyle)
                drawText(
                    measured,
                    topLeft = Offset(leftPadPx - measured.size.width - 6.dp.toPx(), y - measured.size.height / 2f),
                )
            }
        }

        val slotWidth = plotWidth / sorted.size
        val barWidth = slotWidth * 0.6f
        val baselineY = yOf(baseline)

        sorted.forEachIndexed { index, (_, value) ->
            val barLeft = leftPadPx + index * slotWidth + (slotWidth - barWidth) / 2f
            val barTop = yOf(value).coerceAtMost(baselineY)
            val barHeight = (baselineY - barTop).coerceAtLeast(0f)
            drawRoundRect(
                color = if (index == maxIndex) barColor else dimBarColor,
                topLeft = Offset(barLeft, barTop),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
        }

        val labelIndices = if (sorted.size <= xAxisLabelCount) {
            sorted.indices.toList()
        } else {
            val denominator = (xAxisLabelCount - 1).coerceAtLeast(1)
            (0 until xAxisLabelCount).map { i -> (sorted.size - 1) * i / denominator }
        }
        labelIndices.distinct().forEach { index ->
            val (instant, _) = sorted[index]
            val barCenterX = leftPadPx + index * slotWidth + slotWidth / 2f
            val measured = textMeasurer.measure(formatXLabel(instant), axisLabelStyle)
            drawText(
                measured,
                topLeft = Offset(barCenterX - measured.size.width / 2f, plotHeight + 4.dp.toPx()),
            )
        }

        val maxBarCenterX = leftPadPx + maxIndex * slotWidth + slotWidth / 2f
        val measuredEnd = textMeasurer.measure(formatValue(sorted[maxIndex].second), endLabelStyle)
        drawText(
            measuredEnd,
            topLeft = Offset(
                x = maxBarCenterX - measuredEnd.size.width / 2f,
                y = (yOf(sorted[maxIndex].second) - measuredEnd.size.height - 8.dp.toPx()).coerceAtLeast(0f),
            ),
        )
    }
}
