package com.pdg.braceletconnecte.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Label/hint row with a trailing control (switch, pill) and a top-border divider — mirrors `.setting`. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    showDivider: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (showDivider) {
                    Modifier.drawBehind {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            hint?.let {
                Text(
                    it,
                    fontSize = 11.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.4.dp),
                )
            }
        }
        trailing()
    }
}

/** Hand-built toggle matching the web design's 46×27dp track / 19dp thumb `.switch` spec. */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        label = "switch-track",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "switch-thumb",
    )
    val thumbOffset by animateDpAsState(targetValue = if (checked) 23.dp else 4.dp, label = "switch-offset")

    Box(
        modifier = modifier
            .size(width = 46.dp, height = 27.dp)
            .clickable { onCheckedChange(!checked) }
            .background(trackColor, RoundedCornerShape(50))
            .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .offset(x = thumbOffset)
                .size(19.dp)
                .background(thumbColor, CircleShape),
        )
    }
}
