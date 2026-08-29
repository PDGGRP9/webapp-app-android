package com.pdg.braceletconnecte.presentation.stats

import java.time.Instant
import java.time.ZoneId

/** A single point on a chart, already reduced to one bucket. */
data class ChartPoint(val instant: Instant, val value: Float)

/**
 * Averages raw (instant, value) samples into fixed-size time buckets, oldest first.
 * Mirrors the web frontend's `lib/measurements.ts` `bucketAverage`. A bucket only exists in the
 * output if at least one raw sample fell into it — there's no zero-filling of empty buckets.
 */
fun bucketAverage(points: List<Pair<Instant, Float>>, bucketMs: Long): List<ChartPoint> {
    if (points.isEmpty()) return emptyList()
    val buckets = LinkedHashMap<Long, MutableList<Float>>()
    for ((instant, value) in points) {
        val bucketStart = Math.floorDiv(instant.toEpochMilli(), bucketMs) * bucketMs
        buckets.getOrPut(bucketStart) { mutableListOf() }.add(value)
    }
    return buckets.entries
        .sortedBy { it.key }
        .map { (start, values) -> ChartPoint(Instant.ofEpochMilli(start), values.average().toFloat()) }
}

/**
 * Splits an ascending series into contiguous runs, starting a new run whenever the gap to the
 * previous point exceeds [gapMs]. Mirrors the web frontend's `splitByGap` — used to draw chart
 * lines with real holes instead of bridging silently across periods with no measurements.
 */
fun splitByGap(points: List<ChartPoint>, gapMs: Long): List<List<ChartPoint>> {
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf(mutableListOf(points[0]))
    for (i in 1 until points.size) {
        val previous = points[i - 1]
        val current = points[i]
        val delta = current.instant.toEpochMilli() - previous.instant.toEpochMilli()
        if (delta > gapMs) segments.add(mutableListOf(current)) else segments.last().add(current)
    }
    return segments
}

/**
 * For a counter that resets every calendar day (e.g. step_count), the highest value seen on
 * each local calendar day — i.e. that day's running total — one entry per day present. Mirrors
 * the web frontend's `dailyTotals`.
 */
fun dailyTotals(points: List<Pair<Instant, Float>>): List<Float> {
    val zone = ZoneId.systemDefault()
    val byDay = LinkedHashMap<Long, Float>()
    for ((instant, value) in points) {
        val day = instant.atZone(zone).toLocalDate().toEpochDay()
        val current = byDay[day]
        if (current == null || value > current) byDay[day] = value
    }
    return byDay.values.toList()
}
