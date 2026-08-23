package com.pdg.braceletconnecte.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 2-col metrics grid tile — mirrors the web design's `.metric` (dashed border, becomes
 * `.metric.is-live` solid border when [isLive]) with an optional "Bientôt"-style [badgeText].
 */
@Composable
fun MetricTile(
    icon: String,
    name: String,
    value: String,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    badgeText: String? = null,
) {
    val lineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 92.dp)
            .drawBehind {
                val strokeWidth = 1.5.dp.toPx()
                val cornerRadius = CornerRadius(18.dp.toPx())
                val style = if (isLive) {
                    Stroke(width = strokeWidth)
                } else {
                    Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                    )
                }
                drawRoundRect(color = lineColor, style = style, cornerRadius = cornerRadius)
            }
            .padding(horizontal = 14.4.dp, vertical = 13.6.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(icon, fontSize = 18.sp)
                badgeText?.let {
                    Pill(
                        text = it,
                        backgroundColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        borderColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column {
                Text(
                    value,
                    fontSize = 20.8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    name,
                    fontSize = 13.1.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 5.6.dp),
                )
            }
        }
    }
}
