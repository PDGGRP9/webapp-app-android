package com.pdg.braceletconnecte.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Centered top-border stat item — mirrors the web design's `.stat` (used inside [StatRow]). */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier
            .drawBehind {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            .padding(top = 8.8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        MicroLabel(text = label, modifier = Modifier.padding(top = 1.6.dp))
    }
}
