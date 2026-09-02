package com.pdg.braceletconnecte.presentation.stats

import com.pdg.braceletconnecte.presentation.components.APP_ZONE
import java.time.Instant
import java.time.LocalDate

private const val HOUR_MS = 60 * 60 * 1000L

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
 * Resampled sliding-window mean — the blue "average" overlay. Mirrors the web frontend's
 * `movingAverage` in `lib/measurements.ts`.
 *
 * Unlike [bucketAverage] (one mean per fixed 15-min slot, which collapses to a single point
 * when a burst of activity is short), this walks the whole span at a fixed [stepMs] cadence
 * and, at each step, averages *every* raw sample within `±windowMs/2` via a two-pointer sweep
 * (O(n + span/step)). Steps whose window caught no sample are skipped, so a real gap still
 * breaks the line once it goes back through [splitByGap].
 */
fun movingAverage(points: List<Pair<Instant, Float>>, windowMs: Long, stepMs: Long): List<ChartPoint> {
    if (points.isEmpty()) return emptyList()
    val samples = points.map { it.first.toEpochMilli() to it.second }.sortedBy { it.first }
    val half = windowMs / 2
    val firstT = samples.first().first
    val lastT = samples.last().first

    val out = mutableListOf<ChartPoint>()
    var lo = 0
    var hi = 0
    var sum = 0.0
    var t = firstT
    while (t <= lastT + stepMs) {
        while (hi < samples.size && samples[hi].first <= t + half) {
            sum += samples[hi].second
            hi++
        }
        while (lo < hi && samples[lo].first < t - half) {
            sum -= samples[lo].second
            lo++
        }
        val count = hi - lo
        if (count > 0) out.add(ChartPoint(Instant.ofEpochMilli(t), (sum / count).toFloat()))
        t += stepMs
    }
    return out
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
 * each calendar day in [APP_ZONE] — i.e. that day's running total — one entry per day present.
 * Mirrors the web frontend's `dailyTotals`.
 */
fun dailyTotals(points: List<Pair<Instant, Float>>): List<Float> {
    val zone = APP_ZONE
    val byDay = LinkedHashMap<Long, Float>()
    for ((instant, value) in points) {
        val day = instant.atZone(zone).toLocalDate().toEpochDay()
        val current = byDay[day]
        if (current == null || value > current) byDay[day] = value
    }
    return byDay.values.toList()
}

/**
 * step_count is the bracelet's own running total for the day — the firmware resets it to 0 at
 * local midnight and nothing here re-derives it. This helper only *reads* that counter to show
 * "steps taken this hour" = its increase within the hour (a display-only breakdown, it never
 * changes a total). Returns exactly 24 hourly buckets ending at the hour containing [nowMs], oldest
 * first — hours with no measurement (or no walking) come back as 0 rather than being omitted,
 * so the bar chart always renders a full 24 bars. Mirrors the web frontend's `hourlyStepDeltas`.
 */
fun hourlyStepDeltas(points: List<Pair<Instant, Float>>, nowMs: Long = System.currentTimeMillis()): List<ChartPoint> {
    val zone = APP_ZONE
    val ascending = points.sortedBy { it.first }
    fun dayKeyOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    val lastBucketStart = Math.floorDiv(nowMs, HOUR_MS) * HOUR_MS
    val firstBucketStart = lastBucketStart - 23 * HOUR_MS

    var currentDayKey = dayKeyOf(firstBucketStart)
    // Seed the running counter from the last known reading on the same local day, so the very
    // first bucket's delta isn't computed against a false "day just started" baseline.
    var baseline = 0f
    ascending
        .lastOrNull { (instant, _) -> instant.toEpochMilli() < firstBucketStart && dayKeyOf(instant.toEpochMilli()) == currentDayKey }
        ?.let { baseline = it.second }

    val buckets = mutableListOf<ChartPoint>()
    var bucketStart = firstBucketStart
    while (bucketStart <= lastBucketStart) {
        val bucketEnd = bucketStart + HOUR_MS
        val dayKey = dayKeyOf(bucketStart)
        if (dayKey != currentDayKey) {
            currentDayKey = dayKey
            baseline = 0f
        }

        val values = ascending
            .filter { (instant, _) -> instant.toEpochMilli() >= bucketStart && instant.toEpochMilli() < bucketEnd }
            .map { it.second }

        var delta = 0f
        if (values.isNotEmpty()) {
            val bucketEndValue = values.max()
            delta = (bucketEndValue - baseline).coerceAtLeast(0f)
            baseline = bucketEndValue
        }
        buckets.add(ChartPoint(Instant.ofEpochMilli(bucketStart), delta))
        bucketStart += HOUR_MS
    }
    return buckets
}

/**
 * One bar per calendar day ([APP_ZONE]) for the last [days] days ending today, oldest first.
 * Since step_count resets to 0 at local midnight, a day's total is simply the highest reading
 * seen that day (see [dailyTotals]) — days with no data come back as 0. Mirrors the web
 * frontend's `dailyStepTotals`.
 */
fun dailyStepTotals(points: List<Pair<Instant, Float>>, days: Int, nowMs: Long = System.currentTimeMillis()): List<ChartPoint> {
    val zone = APP_ZONE
    val byDay = HashMap<LocalDate, Float>()
    for ((instant, value) in points) {
        val day = instant.atZone(zone).toLocalDate()
        val current = byDay[day]
        if (current == null || value > current) byDay[day] = value
    }

    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return (days - 1 downTo 0).map { offset ->
        val day = today.minusDays(offset.toLong())
        val dayStart = day.atStartOfDay(zone).toInstant()
        ChartPoint(dayStart, byDay[day] ?: 0f)
    }
}
