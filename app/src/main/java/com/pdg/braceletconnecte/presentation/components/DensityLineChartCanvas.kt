package com.pdg.braceletconnecte.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdg.braceletconnecte.presentation.stats.ChartPoint
import com.pdg.braceletconnecte.presentation.stats.splitByGap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val AVERAGE_LINE_COLOR = Color(0xFF3B82F6)
private const val MINUTE_MS = 60_000L
private const val DEFAULT_PX_PER_MINUTE = 6f
private val CHART_HEIGHT = 240.dp
private val LEFT_PAD = 44.dp
private val RIGHT_PAD = 44.dp
private val TOP_PAD = 24.dp
private val BOTTOM_PAD = 32.dp

/**
 * Garmin-style density chart: a light gradient-filled band of raw, minute-bucketed samples with
 * a bold blue moving-average curve on top — both broken into segments wherever the gap between
 * two consecutive points exceeds their gap threshold, so periods with no data render as real
 * empty space rather than a bridged line. Always renders inside a horizontally scrollable strip
 * whose width is computed directly from the domain span (minutes × [pxPerMinute]), auto-scrolled
 * to the most recent data on load. Mirrors the web frontend's `LineChart.tsx`.
 */
@Composable
fun DensityLineChartCanvas(
    data: List<ChartPoint>,
    domainStart: Instant,
    domainEnd: Instant,
    gapMs: Long,
    modifier: Modifier = Modifier,
    averageData: List<ChartPoint> = emptyList(),
    averageGapMs: Long = gapMs,
    minDomain: Float? = null,
    valueSuffix: String = "",
    pxPerMinute: Float = DEFAULT_PX_PER_MINUTE,
    showDayBoundaries: Boolean = false,
    formatValue: (Float) -> String = { it.roundToInt().toString() },
    emptyMessage: String = "Aucune donnée sur cette période.",
) {
    if (data.isEmpty() && averageData.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val domainStartMs = domainStart.toEpochMilli()
    val domainEndMs = domainEnd.toEpochMilli()
    val totalMinutes = ((domainEndMs - domainStartMs) / MINUTE_MS).coerceAtLeast(1L).toFloat()
    val plotWidthDp = totalMinutes * pxPerMinute
    val totalWidthDp = LEFT_PAD.value + plotWidthDp + RIGHT_PAD.value

    val density = LocalDensity.current
    val plotWidthPx = with(density) { plotWidthDp.dp.toPx() }
    val leftPadPx = with(density) { LEFT_PAD.toPx() }
    val topPadPx = with(density) { TOP_PAD.toPx() }
    val bottomPadPx = with(density) { BOTTOM_PAD.toPx() }
    val heightPx = with(density) { CHART_HEIGHT.toPx() }
    val plotHeightPx = heightPx - topPadPx - bottomPadPx

    val values = (data + averageData).map { it.value }
    val valueMin = values.min()
    val valueMax = values.max()
    val floor = minDomain ?: valueMin
    val pad = ((valueMax - floor) * 0.12f).coerceAtLeast(1f)
    val yMin = minDomain ?: (floor - pad)
    val yMax = valueMax + pad

    fun xOf(instantMs: Long): Float =
        leftPadPx + ((instantMs - domainStartMs).toFloat() / (domainEndMs - domainStartMs).toFloat()) * plotWidthPx
    fun yOf(value: Float): Float = topPadPx + plotHeightPx - ((value - yMin) / (yMax - yMin)) * plotHeightPx

    val baselineY = yOf(yMin)
    val rawSegments = splitByGap(data, gapMs).map { segment -> segment.map { Offset(xOf(it.instant.toEpochMilli()), yOf(it.value)) } }
    val avgSegments = splitByGap(averageData, averageGapMs).map { segment -> segment.map { Offset(xOf(it.instant.toEpochMilli()), yOf(it.value)) } }

    // Hovering/tapping reads off the raw per-minute series (not the smoothed average) so the
    // tooltip shows the actual value at that minute, not a coarser trend value.
    val hoverSource = data.ifEmpty { averageData }
    val hoverPoints = hoverSource.map {
        HoverPoint(it.instant, it.value, Offset(xOf(it.instant.toEpochMilli()), yOf(it.value)))
    }

    val dayBoundaries = remember(showDayBoundaries, domainStartMs, domainEndMs) {
        if (!showDayBoundaries) emptyList() else buildDayBoundaries(domainStart, domainEnd)
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(totalWidthDp, data.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    var activeIndex by remember { mutableStateOf<Int?>(null) }

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val axisLabelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val endLabelStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    val tooltipValueStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    val tooltipDateStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val rawAccent = MaterialTheme.colorScheme.primary
    val dayBoundaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val tooltipBg = MaterialTheme.colorScheme.surfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val xLabelCount = (plotWidthPx / with(density) { 160.dp.toPx() }).roundToInt().coerceAtLeast(2)

    Box(modifier = modifier.fillMaxWidth().horizontalScroll(scrollState)) {
        Canvas(
            modifier = Modifier
                .size(totalWidthDp.dp, CHART_HEIGHT)
                .pointerInput(hoverPoints) {
                    detectTapGestures { offset ->
                        activeIndex = hoverPoints.indices.minByOrNull { abs(hoverPoints[it].offset.x - offset.x) }
                    }
                },
        ) {
            // Y-axis grid + mirrored labels on both sides.
            val tickCount = 4
            for (i in 0..tickCount) {
                val fraction = i / tickCount.toFloat()
                val value = yMin + fraction * (yMax - yMin)
                val y = baselineY - fraction * plotHeightPx
                drawLine(gridColor, Offset(leftPadPx, y), Offset(leftPadPx + plotWidthPx, y), strokeWidth = 1f)
                val label = formatValue(value)
                val measuredLeft = textMeasurer.measure(label, axisLabelStyle)
                drawText(measuredLeft, topLeft = Offset(leftPadPx - measuredLeft.size.width - 8.dp.toPx(), y - measuredLeft.size.height / 2f))
                val measuredRight = textMeasurer.measure(label, axisLabelStyle)
                drawText(measuredRight, topLeft = Offset(leftPadPx + plotWidthPx + 8.dp.toPx(), y - measuredRight.size.height / 2f))
            }
            drawLine(gridColor, Offset(leftPadPx, baselineY), Offset(leftPadPx + plotWidthPx, baselineY), strokeWidth = 1.2f)

            // X-axis time labels.
            for (i in 0..xLabelCount) {
                val t = domainStartMs + (i.toFloat() / xLabelCount) * (domainEndMs - domainStartMs)
                val x = xOf(t.toLong())
                val label = formatXLabel(Instant.ofEpochMilli(t.toLong()), domainEndMs - domainStartMs)
                val measured = textMeasurer.measure(label, axisLabelStyle)
                drawText(measured, topLeft = Offset(x - measured.size.width / 2f, baselineY + 12.dp.toPx()))
            }

            // Dashed day-change markers (used instead of the average line for step_count).
            dayBoundaries.forEach { boundary ->
                val x = xOf(boundary.toEpochMilli())
                drawLine(
                    color = dayBoundaryColor,
                    start = Offset(x, topPadPx),
                    end = Offset(x, baselineY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
                val label = boundary.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
                val measured = textMeasurer.measure(label, axisLabelStyle)
                drawText(measured, topLeft = Offset(x - measured.size.width / 2f, topPadPx - measured.size.height - 4.dp.toPx()))
            }

            // Raw density: gradient-filled area + thin line per segment.
            rawSegments.forEach { segment ->
                if (segment.size > 1) {
                    val fillBrush = Brush.verticalGradient(
                        colors = listOf(rawAccent.copy(alpha = 0.55f), rawAccent.copy(alpha = 0.04f)),
                        startY = segment.minOf { it.y },
                        endY = baselineY,
                    )
                    val area = smoothPath(segment)
                    area.lineTo(segment.last().x, baselineY)
                    area.lineTo(segment.first().x, baselineY)
                    area.close()
                    drawPath(area, brush = fillBrush)
                    drawPath(
                        smoothPath(segment),
                        color = rawAccent.copy(alpha = 0.85f),
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                } else if (segment.size == 1) {
                    drawCircle(rawAccent.copy(alpha = 0.7f), radius = 3.dp.toPx(), center = segment[0])
                }
            }

            // Blue moving-average overlay, only where there's underlying data.
            avgSegments.forEach { segment ->
                if (segment.size > 1) {
                    drawPath(
                        smoothPath(segment),
                        color = AVERAGE_LINE_COLOR,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                } else if (segment.size == 1) {
                    drawCircle(AVERAGE_LINE_COLOR, radius = 4.dp.toPx(), center = segment[0])
                }
            }

            hoverPoints.lastOrNull()?.let { last ->
                val label = formatValue(last.value) + valueSuffix
                val measured = textMeasurer.measure(label, endLabelStyle)
                drawText(
                    measured,
                    topLeft = Offset(
                        (last.offset.x - measured.size.width).coerceAtLeast(leftPadPx),
                        (last.offset.y - measured.size.height - 10.dp.toPx()).coerceAtLeast(0f),
                    ),
                )
            }

            activeIndex?.let { index ->
                val active = hoverPoints[index]
                drawLine(dayBoundaryColor, Offset(active.offset.x, topPadPx), Offset(active.offset.x, baselineY), strokeWidth = 1f)
                drawCircle(rawAccent, radius = 6.dp.toPx(), center = active.offset)

                val valueText = formatValue(active.value) + valueSuffix
                val dateText = formatTooltipDate(active.instant)
                val measuredValue = textMeasurer.measure(valueText, tooltipValueStyle)
                val measuredDate = textMeasurer.measure(dateText, tooltipDateStyle)
                val tooltipWidth = maxOf(measuredValue.size.width, measuredDate.size.width) + 24.dp.toPx()
                val tooltipX = (active.offset.x + 12.dp.toPx()).coerceAtMost(leftPadPx + plotWidthPx - tooltipWidth)
                drawRoundRect(
                    color = tooltipBg,
                    topLeft = Offset(tooltipX, topPadPx),
                    size = androidx.compose.ui.geometry.Size(tooltipWidth, 48.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                )
                drawText(measuredValue, topLeft = Offset(tooltipX + 12.dp.toPx(), topPadPx + 8.dp.toPx()))
                drawText(measuredDate, topLeft = Offset(tooltipX + 12.dp.toPx(), topPadPx + 8.dp.toPx() + measuredValue.size.height + 2.dp.toPx()))
            }
        }
    }
}

private data class HoverPoint(val instant: Instant, val value: Float, val offset: Offset)

/** Smooths a polyline into a continuous curve by quadratic-curving through each segment's midpoint. */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.size < 3) {
        points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        return path
    }
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]
        val midX = (current.x + next.x) / 2f
        val midY = (current.y + next.y) / 2f
        path.quadraticBezierTo(current.x, current.y, midX, midY)
    }
    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}

private fun buildDayBoundaries(domainStart: Instant, domainEnd: Instant): List<Instant> {
    val zone = ZoneId.systemDefault()
    val boundaries = mutableListOf<Instant>()
    var next = domainStart.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    while (next.isBefore(domainEnd)) {
        boundaries.add(next)
        next = next.plusSeconds(24 * 60 * 60)
    }
    return boundaries
}

private fun formatXLabel(instant: Instant, spanMs: Long): String {
    val zone = ZoneId.systemDefault()
    return if (spanMs <= 2 * 24 * 60 * 60 * 1000L) {
        instant.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        instant.atZone(zone).format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
    }
}

private fun formatTooltipDate(instant: Instant): String {
    val zone = ZoneId.systemDefault()
    val zoned = instant.atZone(zone)
    val weekday = zoned.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.FRENCH)
    return "$weekday ${zoned.format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.FRENCH))}"
}
