package com.pdg.braceletconnecte.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdg.braceletconnecte.presentation.stats.ChartPoint
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private val CHART_HEIGHT = 220.dp
private val LEFT_PAD = 44.dp
private val RIGHT_PAD = 16.dp
private val TOP_PAD = 20.dp
private val BOTTOM_PAD = 32.dp

/**
 * Fixed-length bar chart (e.g. 24 hourly buckets, or 7 daily buckets): lime bars on the app's
 * dark card background, a value scale on the left, and a tap-to-reveal tooltip. Never scrolls —
 * unlike [DensityLineChartCanvas], the bar count is always small and fixed. Mirrors the web
 * frontend's `BarChart.tsx`.
 */
@Composable
fun BarChartCanvas(
    data: List<ChartPoint>,
    formatAxisLabel: (Instant) -> String,
    formatTooltipLabel: (Instant) -> String,
    modifier: Modifier = Modifier,
    valueSuffix: String = "",
    emptyMessage: String = "Aucune donnée sur cette période.",
    formatValue: (Float) -> String = { it.roundToInt().toString() },
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var activeIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val leftPadPx = with(density) { LEFT_PAD.toPx() }
    val rightPadPx = with(density) { RIGHT_PAD.toPx() }
    val topPadPx = with(density) { TOP_PAD.toPx() }
    val bottomPadPx = with(density) { BOTTOM_PAD.toPx() }
    val heightPx = with(density) { CHART_HEIGHT.toPx() }
    val plotHeightPx = heightPx - topPadPx - bottomPadPx

    val maxValue = data.maxOf { it.value }.coerceAtLeast(0f)
    val yMax = if (maxValue > 0f) maxValue * 1.15f else 10f
    val tickValues = remember(yMax) { niceTicks(yMax, 4) }

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val axisLabelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipValueStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    val tooltipDateStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val barColor = MaterialTheme.colorScheme.primary
    val crosshairColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val tooltipBg = MaterialTheme.colorScheme.surfaceVariant
    val textMeasurer = rememberTextMeasurer()

    // Never more than ~8 axis labels, however many bars there are, so they don't crowd.
    val labelStride = max(1, ceil(data.size / 8.0).toInt())

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .pointerInput(data) {
                detectTapGestures { offset ->
                    val plotWidthPx = size.width - leftPadPx - rightPadPx
                    val slotWidthPx = plotWidthPx / data.size
                    val raw = ((offset.x - leftPadPx) / slotWidthPx).toInt()
                    activeIndex = raw.coerceIn(0, data.size - 1)
                }
            },
    ) {
        val plotWidthPx = size.width - leftPadPx - rightPadPx
        val slotWidthPx = plotWidthPx / data.size
        val barWidthPx = max(4.dp.toPx(), slotWidthPx * 0.55f)
        val baselineY = topPadPx + plotHeightPx

        fun yOf(value: Float): Float = baselineY - (value / yMax) * plotHeightPx
        fun xForIndex(index: Int): Float = leftPadPx + slotWidthPx * index + slotWidthPx / 2f

        tickValues.forEach { tick ->
            val y = yOf(tick)
            drawLine(gridColor, Offset(leftPadPx, y), Offset(leftPadPx + plotWidthPx, y), strokeWidth = 1f)
            val measured = textMeasurer.measure(formatValue(tick), axisLabelStyle)
            drawText(measured, topLeft = Offset(leftPadPx - measured.size.width - 8.dp.toPx(), y - measured.size.height / 2f))
        }
        drawLine(gridColor, Offset(leftPadPx, baselineY), Offset(leftPadPx + plotWidthPx, baselineY), strokeWidth = 1.2f)

        data.forEachIndexed { index, point ->
            val x = leftPadPx + slotWidthPx * index + (slotWidthPx - barWidthPx) / 2f
            val y = yOf(point.value)
            val isActive = index == activeIndex
            drawRoundRect(
                color = if (isActive) barColor else barColor.copy(alpha = 0.82f),
                topLeft = Offset(x, y),
                size = Size(barWidthPx, (baselineY - y).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }

        data.forEachIndexed { index, point ->
            if (index % labelStride != 0 && index != data.size - 1) return@forEachIndexed
            val measured = textMeasurer.measure(formatAxisLabel(point.instant), axisLabelStyle)
            drawText(measured, topLeft = Offset(xForIndex(index) - measured.size.width / 2f, baselineY + 12.dp.toPx()))
        }

        activeIndex?.let { index ->
            val point = data[index]
            val x = xForIndex(index)
            drawLine(crosshairColor, Offset(x, topPadPx), Offset(x, baselineY), strokeWidth = 1f)

            val valueText = formatValue(point.value) + valueSuffix
            val dateText = formatTooltipLabel(point.instant)
            val measuredValue = textMeasurer.measure(valueText, tooltipValueStyle)
            val measuredDate = textMeasurer.measure(dateText, tooltipDateStyle)
            val tooltipWidth = max(measuredValue.size.width, measuredDate.size.width) + 24.dp.toPx()
            val tooltipX = (x - tooltipWidth / 2f).coerceIn(leftPadPx, leftPadPx + plotWidthPx - tooltipWidth)
            drawRoundRect(
                color = tooltipBg,
                topLeft = Offset(tooltipX, topPadPx),
                size = Size(tooltipWidth, 48.dp.toPx()),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
            )
            drawText(measuredValue, topLeft = Offset(tooltipX + 12.dp.toPx(), topPadPx + 8.dp.toPx()))
            drawText(
                measuredDate,
                topLeft = Offset(tooltipX + 12.dp.toPx(), topPadPx + 8.dp.toPx() + measuredValue.size.height + 2.dp.toPx()),
            )
        }
    }
}

private fun niceTicks(max: Float, count: Int): List<Float> {
    if (max <= 0f) return listOf(0f)
    val rawStep = max / count
    val magnitude = 10f.pow(floor(log10(rawStep.toDouble())).toFloat())
    val residual = rawStep / magnitude
    val step = (if (residual > 5f) 10f else if (residual > 2f) 5f else if (residual > 1f) 2f else 1f) * magnitude
    val ticks = mutableListOf<Float>()
    var value = 0f
    while (value <= max + step * 0.001f) {
        ticks.add(value)
        value += step
    }
    return ticks
}
