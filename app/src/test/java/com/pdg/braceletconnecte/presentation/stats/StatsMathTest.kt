package com.pdg.braceletconnecte.presentation.stats

import com.pdg.braceletconnecte.presentation.components.APP_ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private const val HOUR_MS = 60 * 60 * 1000L

private fun zurichMidnightMs(year: Int, month: Int, day: Int): Long =
    LocalDate.of(year, month, day).atStartOfDay(APP_ZONE).toInstant().toEpochMilli()

class StatsMathTest {

    @Test
    fun `hourlyStepDeltas returns 24 hourly buckets of steps taken that hour, resetting the baseline at local midnight`() {
        val aug28Midnight = zurichMidnightMs(2026, 8, 28)
        val aug29Midnight = zurichMidnightMs(2026, 8, 29)
        val nowMs = aug29Midnight + 10 * HOUR_MS // 10:00 local on Aug 29

        val points = listOf(
            // Last reading of Aug 28, at 23:00 local — day total 8000 before the midnight reset.
            Instant.ofEpochMilli(aug28Midnight + 23 * HOUR_MS) to 8000f,
            // Aug 29 starts counting from 0 again.
            Instant.ofEpochMilli(aug29Midnight + 30 * 60 * 1000) to 50f,
            Instant.ofEpochMilli(aug29Midnight + 6 * HOUR_MS) to 1200f,
            // No steps between 06:00 and 09:30 — same cumulative value as the previous reading.
            Instant.ofEpochMilli(aug29Midnight + 9 * HOUR_MS + 30 * 60 * 1000) to 1200f,
        )

        val buckets = hourlyStepDeltas(points, nowMs)

        assertEquals(24, buckets.size)
        assertEquals(Instant.ofEpochMilli(aug28Midnight + 11 * HOUR_MS), buckets[0].instant)
        assertEquals(Instant.ofEpochMilli(nowMs), buckets[23].instant)

        val expected = FloatArray(24)
        expected[12] = 8000f // Aug 28, 23:00 — jump from 0 to 8000 within the pre-midnight hour
        expected[13] = 50f // Aug 29, 00:00 — counter reset to 0, then 50 steps
        expected[19] = 1150f // Aug 29, 06:00 — 1200 - 50
        // index 22 (09:00) stays 0: the 09:30 reading repeats the 06:00 value, no new steps.
        expected.forEachIndexed { index, value -> assertEquals("bucket $index", value, buckets[index].value, 0.001f) }
    }

    @Test
    fun `hourlyStepDeltas fills hours with no measurement as 0 instead of omitting them`() {
        val nowMs = zurichMidnightMs(2026, 8, 29) + 10 * HOUR_MS
        val buckets = hourlyStepDeltas(emptyList(), nowMs)
        assertTrue(buckets.all { it.value == 0f })
    }

    @Test
    fun `dailyStepTotals returns one bucket per day, zero-filling days with no data`() {
        val aug27Midnight = zurichMidnightMs(2026, 8, 27)
        val aug28Midnight = zurichMidnightMs(2026, 8, 28)
        val aug29Midnight = zurichMidnightMs(2026, 8, 29)
        val nowMs = aug29Midnight + 5 * HOUR_MS

        val points = listOf(
            Instant.ofEpochMilli(aug27Midnight + 8 * HOUR_MS) to 500f,
            Instant.ofEpochMilli(aug27Midnight + 20 * HOUR_MS) to 4000f,
            // Aug 28 has no measurements at all.
            Instant.ofEpochMilli(aug29Midnight + 3 * HOUR_MS) to 600f,
        )

        val buckets = dailyStepTotals(points, 3, nowMs)

        assertEquals(
            listOf(
                ChartPoint(Instant.ofEpochMilli(aug27Midnight), 4000f),
                ChartPoint(Instant.ofEpochMilli(aug28Midnight), 0f),
                ChartPoint(Instant.ofEpochMilli(aug29Midnight), 600f),
            ),
            buckets,
        )
    }

    private val MINUTE_MS = 60_000L

    @Test
    fun `movingAverage averages every sample in the window, not just the endpoints`() {
        val base = Instant.parse("2026-01-01T12:00:00Z")
        // 60, 90, 90, 90, 140 one minute apart -> mean 94, within a single 30-min window.
        val points = listOf(60f, 90f, 90f, 90f, 140f).mapIndexed { i, v ->
            base.plusSeconds(i * 60L) to v
        }

        val series = movingAverage(points, windowMs = 30 * MINUTE_MS, stepMs = 5 * MINUTE_MS)

        assertTrue(series.isNotEmpty())
        series.forEach { assertEquals(94f, it.value, 0.01f) }
    }

    @Test
    fun `movingAverage stays a dense curve from a short burst`() {
        val base = Instant.parse("2026-01-01T12:00:00Z")
        val points = listOf(0L, 2L, 4L, 6L).map { base.plusSeconds(it * 60) to 70f }

        val series = movingAverage(points, windowMs = 30 * MINUTE_MS, stepMs = 5 * MINUTE_MS)

        assertTrue("expected >= 2 points, got ${series.size}", series.size >= 2)
    }

    @Test
    fun `movingAverage leaves a gap wider than the window empty`() {
        val base = Instant.parse("2026-01-01T12:00:00Z")
        val points = listOf(0L, 2L, 120L, 122L).map { base.plusSeconds(it * 60) to 80f }

        val series = movingAverage(points, windowMs = 30 * MINUTE_MS, stepMs = 5 * MINUTE_MS)
        val maxGap = series.zipWithNext { a, b -> b.instant.toEpochMilli() - a.instant.toEpochMilli() }.max()

        assertTrue("expected a >30-min hole in the resampled series", maxGap > 30 * MINUTE_MS)
    }

    @Test
    fun `movingAverage returns empty for no samples`() {
        assertEquals(emptyList<ChartPoint>(), movingAverage(emptyList(), 30 * MINUTE_MS, 5 * MINUTE_MS))
    }
}
