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
}
