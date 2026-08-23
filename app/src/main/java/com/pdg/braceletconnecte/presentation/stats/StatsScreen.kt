package com.pdg.braceletconnecte.presentation.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdg.braceletconnecte.data.api.dto.capturedAtInstant
import com.pdg.braceletconnecte.presentation.components.DataTable
import com.pdg.braceletconnecte.presentation.components.LineChartCanvas
import com.pdg.braceletconnecte.presentation.components.MetricCard
import com.pdg.braceletconnecte.presentation.components.formatShortTime
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val cutoff = uiState.range.cutoff()
    val filtered = uiState.measurements.mapNotNull { measurement ->
        val instant = measurement.capturedAtInstant() ?: return@mapNotNull null
        if (instant.isBefore(cutoff)) null else measurement to instant
    }
    val chartPoints = filtered.mapNotNull { (measurement, instant) ->
        uiState.metric.valueOf(measurement)?.let { instant to it.toFloat() }
    }
    val values = chartPoints.map { it.second }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiques") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatsMetric.entries.forEach { metric ->
                    FilterChip(
                        selected = uiState.metric == metric,
                        onClick = { viewModel.selectMetric(metric) },
                        label = { Text(metric.label) },
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatsRange.entries.forEach { range ->
                    FilterChip(
                        selected = uiState.range == range,
                        onClick = { viewModel.selectRange(range) },
                        label = { Text(range.label) },
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${uiState.metric.label} — ${uiState.range.label}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LineChartCanvas(
                        points = chartPoints,
                        minDomain = if (uiState.metric == StatsMetric.STEPS) 0f else null,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(label = "Moyenne", value = values.average().let(::formatOrDash), modifier = Modifier.weight(1f))
                        MetricCard(label = "Min", value = values.minOrNull()?.let(::formatDecimal) ?: "—", modifier = Modifier.weight(1f))
                        MetricCard(label = "Max", value = values.maxOrNull()?.let(::formatDecimal) ?: "—", modifier = Modifier.weight(1f))
                        MetricCard(label = "Dernière", value = values.lastOrNull()?.let(::formatDecimal) ?: "—", modifier = Modifier.weight(1f))
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Données brutes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = viewModel::toggleRawTable) {
                            Text(if (uiState.showRawTable) "Masquer" else "Afficher")
                        }
                    }
                    if (uiState.showRawTable) {
                        DataTable(
                            headers = listOf("Heure", "BPM", "SpO2", "Pas"),
                            rows = filtered.map { (measurement, instant) ->
                                listOf(
                                    formatShortTime(instant),
                                    measurement.heartRateBpm?.toString() ?: "—",
                                    measurement.spo2Percent?.let(::formatDecimal) ?: "—",
                                    measurement.stepCount.toString(),
                                )
                            },
                        )
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatDecimal(value: Float): String {
    return NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)
}

private fun formatDecimal(value: Double): String {
    return NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)
}

private fun formatOrDash(value: Double): String {
    if (value.isNaN()) return "—"
    return NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)
}
