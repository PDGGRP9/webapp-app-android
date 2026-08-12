package com.pdg.braceletconnecte.presentation.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val shortTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

fun formatShortTime(instant: Instant?): String = instant?.let(shortTimeFormatter::format) ?: "—"
