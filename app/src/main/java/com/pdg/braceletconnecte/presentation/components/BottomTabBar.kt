package com.pdg.braceletconnecte.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdg.braceletconnecte.presentation.navigation.AppRoutes

private enum class TabIcon { Direct, Historique, Compte }

private data class Tab(val route: String, val label: String, val icon: TabIcon)

private val TABS = listOf(
    Tab(AppRoutes.DASHBOARD, "Direct", TabIcon.Direct),
    Tab(AppRoutes.STATS, "Historique", TabIcon.Historique),
    Tab(AppRoutes.ACCOUNT, "Compte", TabIcon.Compte),
)

/** Bottom navigation bar — mirrors the web design's `.tabbar` (Direct/Historique/Compte). */
@Composable
fun BottomTabBar(currentRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .drawBehind {
                drawLine(lineColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.5.dp.toPx())
            }
            .navigationBarsPadding()
            .padding(vertical = 8.8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TABS.forEach { tab ->
            val isActive = tab.route == currentRoute
            val tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .clickable(enabled = !isActive) { onNavigate(tab.route) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(modifier = Modifier.size(22.dp)) { drawTabIcon(tab.icon, tint) }
                Text(
                    text = tab.label.uppercase(),
                    color = tint,
                    fontSize = 9.9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 3.5.dp),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTabIcon(icon: TabIcon, color: Color) {
    val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height
    // Icon paths below are scaled from the mockup's 24x24 viewBox SVGs.
    fun px(v: Float) = v / 24f * w
    fun py(v: Float) = v / 24f * h

    when (icon) {
        TabIcon.Direct -> {
            // M3 12h4l2-5 4 10 2-5h6
            val path = Path().apply {
                moveTo(px(3f), py(12f))
                lineTo(px(7f), py(12f))
                lineTo(px(9f), py(7f))
                lineTo(px(13f), py(17f))
                lineTo(px(15f), py(12f))
                lineTo(px(21f), py(12f))
            }
            drawPath(path, color = color, style = stroke)
        }

        TabIcon.Historique -> {
            drawLine(color, Offset(px(5f), py(20f)), Offset(px(5f), py(13f)), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, Offset(px(12f), py(20f)), Offset(px(12f), py(6f)), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, Offset(px(19f), py(20f)), Offset(px(19f), py(16f)), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, Offset(px(3f), py(20f)), Offset(px(21f), py(20f)), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }

        TabIcon.Compte -> {
            drawCircle(color = color, radius = px(3.5f), center = Offset(px(12f), py(8f)), style = stroke)
            val path = Path().apply {
                moveTo(px(5f), py(20f))
                cubicTo(px(5f), py(16.1f), px(8.1f), py(14f), px(12f), py(14f))
                cubicTo(px(15.9f), py(14f), px(19f), py(16.1f), px(19f), py(20f))
            }
            drawPath(path, color = color, style = stroke)
        }
    }
}
