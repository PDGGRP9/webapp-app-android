package com.pdg.braceletconnecte.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class StatRowItem(val value: String, val label: String)

/** Lays out 3 (or more) [MetricCard]s evenly — mirrors the web design's `.stat-row`. */
@Composable
fun StatRow(
    items: List<StatRowItem>,
    modifier: Modifier = Modifier,
    valueFontSize: TextUnit = 20.sp,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.6.dp)) {
        items.forEach { item ->
            MetricCard(label = item.label, value = item.value, modifier = Modifier.weight(1f), valueFontSize = valueFontSize)
        }
    }
}
