package com.pdg.braceletconnecte.presentation.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pdg.braceletconnecte.data.api.dto.capturedAtInstant
import com.pdg.braceletconnecte.presentation.components.AppButton
import com.pdg.braceletconnecte.presentation.components.AppButtonVariant
import com.pdg.braceletconnecte.presentation.components.AppCard
import com.pdg.braceletconnecte.presentation.components.AppScaffold
import com.pdg.braceletconnecte.presentation.components.AppSelect
import com.pdg.braceletconnecte.presentation.components.BarChartCanvas
import com.pdg.braceletconnecte.presentation.components.DataTable
import com.pdg.braceletconnecte.presentation.components.LineChartCanvas
import com.pdg.braceletconnecte.presentation.components.SegmentedControl
import com.pdg.braceletconnecte.presentation.components.SegmentedOption
import com.pdg.braceletconnecte.presentation.components.StatRow
import com.pdg.braceletconnecte.presentation.components.StatRowItem
import com.pdg.braceletconnecte.presentation.components.formatNumber
import com.pdg.braceletconnecte.presentation.components.formatShortTime
import com.pdg.braceletconnecte.presentation.components.headerDate

@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRawTable by remember { mutableStateOf(false) }

    val cutoff = uiState.range.cutoff()
    val filtered = uiState.measurements.mapNotNull { measurement ->
        val instant = measurement.capturedAtInstant() ?: return@mapNotNull null
        if (instant.isBefore(cutoff)) null else measurement to instant
    }
    val chartPoints = filtered.mapNotNull { (measurement, instant) ->
        uiState.metric.valueOf(measurement)?.let { instant to it.toFloat() }
    }
    val values = chartPoints.map { it.second }
    val minDomain = if (uiState.metric == StatsMetric.STEPS) 0f else null

    AppScaffold(navController = navController) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 18.4.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(17.6.dp),
        ) {
            Column {
                Text("Historique", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    headerDate(),
                    fontSize = 12.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.2.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(11.2.dp)) {
                AppSelect(
                    selected = uiState.metric,
                    options = StatsMetric.entries,
                    optionLabel = { it.label },
                    onSelect = viewModel::selectMetric,
                )
                SegmentedControl(
                    options = StatsRange.entries.map { SegmentedOption(it, it.label) },
                    selected = uiState.range,
                    onSelect = viewModel::selectRange,
                )
                SegmentedControl(
                    options = ChartKind.entries.map { SegmentedOption(it, it.label) },
                    selected = uiState.chartKind,
                    onSelect = viewModel::selectChartKind,
                )
            }

            AppCard(title = uiState.metric.label) {
                if (uiState.chartKind == ChartKind.Line) {
                    LineChartCanvas(points = chartPoints, minDomain = minDomain)
                } else {
                    BarChartCanvas(points = chartPoints, minDomain = minDomain)
                }
                StatRow(
                    items = listOf(
                        StatRowItem(formatNumber(values.average().takeIf { !it.isNaN() }), "Moyenne"),
                        StatRowItem(formatNumber(values.minOrNull()?.toDouble()), "Min"),
                        StatRowItem(formatNumber(values.maxOrNull()?.toDouble()), "Max"),
                        StatRowItem(formatNumber(values.lastOrNull()?.toDouble()), "Dernière"),
                    ),
                )
            }

            AppCard {
                Text("Données brutes", fontWeight = FontWeight.Bold)
                AppButton(
                    text = if (showRawTable) "Masquer le tableau" else "Afficher en tableau",
                    onClick = { showRawTable = !showRawTable },
                    variant = AppButtonVariant.Ghost,
                )
                if (showRawTable) {
                    DataTable(
                        headers = listOf("Heure", "BPM", "SpO2", "Pas"),
                        rows = filtered.map { (measurement, instant) ->
                            listOf(
                                formatShortTime(instant),
                                measurement.heartRateBpm?.toString() ?: "-",
                                formatNumber(measurement.spo2Percent, " %"),
                                measurement.stepCount.toString(),
                            )
                        },
                    )
                }
            }

            uiState.errorMessage?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.8.sp)
            }
        }
    }
}
