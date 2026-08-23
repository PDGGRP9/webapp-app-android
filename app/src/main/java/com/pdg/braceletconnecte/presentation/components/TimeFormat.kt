package com.pdg.braceletconnecte.presentation.components

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

fun formatShortTime(instant: Instant?): String = instant?.let(shortTimeFormatter::format) ?: "—"

/** Mirrors the web frontend's lib/format.ts formatNumber: 0 decimals if integral, else 1, "-" for null/NaN. */
fun formatNumber(value: Double?, suffix: String = ""): String {
    if (value == null || value.isNaN()) return "-"
    val decimals = if (value % 1.0 == 0.0) 0 else 1
    return String.format(Locale.US, "%.${decimals}f", value) + suffix
}

/** Mirrors the web frontend's lib/format.ts timeAgo: cascading "il y a X s/min/h/j" thresholds. */
fun timeAgo(instant: Instant?, now: Instant = Instant.now()): String {
    if (instant == null) return "-"
    val seconds = Duration.between(instant, now).seconds.coerceAtLeast(0)
    if (seconds < 60) return "il y a $seconds s"
    val minutes = Math.round(seconds / 60.0)
    if (minutes < 60) return "il y a $minutes min"
    val hours = Math.round(minutes / 60.0)
    if (hours < 24) return "il y a $hours h"
    val days = Math.round(hours / 24.0)
    return "il y a $days j"
}

/** Mirrors the web frontend's lib/format.ts headerDate: "Dimanche 23 août · 14:32". */
fun headerDate(instant: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val zonedDateTime = instant.atZone(zone)
    val weekday = zonedDateTime.format(DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH))
        .replaceFirstChar { it.uppercase(Locale.FRENCH) }
    val dayMonth = zonedDateTime.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))
    val time = zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH))
    return "$weekday $dayMonth · $time"
}

/** Mirrors the web frontend's lib/format.ts initials: up to 2 uppercase first-letters, "?" fallback. */
fun initials(vararg parts: String?): String {
    val letters = parts.filterNotNull()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
    return if (letters.isEmpty()) "?" else letters.joinToString("")
}
