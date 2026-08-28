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
import com.pdg.braceletconnecte.presentation.components.DataTable
import com.pdg.braceletconnecte.presentation.components.DensityLineChartCanvas
import com.pdg.braceletconnecte.presentation.components.SegmentedControl
import com.pdg.braceletconnecte.presentation.components.SegmentedOption
import com.pdg.braceletconnecte.presentation.components.StatRow
import com.pdg.braceletconnecte.presentation.components.StatRowItem
import com.pdg.braceletconnecte.presentation.components.formatNumber
import com.pdg.braceletconnecte.presentation.components.formatShortTime
import com.pdg.braceletconnecte.presentation.components.headerDate
import java.time.Instant

@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRawTable by remember { mutableStateOf(false) }

    val now = Instant.now()
    val config = uiState.range.config()
    val domainStart = now.minusSeconds(uiState.range.hours * 3600)
    val filtered = uiState.measurements.mapNotNull { measurement ->
        val instant = measurement.capturedAtInstant() ?: return@mapNotNull null
        if (instant.isBefore(domainStart)) null else measurement to instant
    }
    val rawPairs = filtered.mapNotNull { (measurement, instant) ->
        uiState.metric.valueOf(measurement)?.let { instant to it.toFloat() }
    }
    val isStepMetric = uiState.metric == StatsMetric.STEPS

    // Red curve: bucket-averaged points, spaced out for readability instead of one point per
    // raw sample. Blue overlay is skipped for steps — a "moving average" of a counter that
    // resets to 0 every midnight doesn't read as a trend; dashed day-boundary markers replace it.
    val chartData = bucketAverage(rawPairs, config.pointBucketMs)
    val averageData = if (isStepMetric) emptyList() else bucketAverage(rawPairs, config.averageBucketMs)

    // step_count resets every midnight, so a plain avg/min/max over raw values is meaningless
    // (min is trivially ~0, avg mixes numbers from different days). Instead, reduce to one
    // "day's total" per day present, then summarize those.
    val summaryValues = if (isStepMetric) dailyTotals(rawPairs) else rawPairs.map { it.second }
    val lastRecord = filtered.maxByOrNull { it.second }
    val avg = summaryValues.takeIf { it.isNotEmpty() }?.average()
    val min = summaryValues.minOrNull()?.toDouble()
    val max = summaryValues.maxOrNull()?.toDouble()
    val last = lastRecord?.let { uiState.metric.valueOf(it.first) }
    val suffix = uiState.metric.suffix()
    val minDomain = if (isStepMetric) 0f else null

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
            }

            AppCard(title = uiState.metric.label) {
                DensityLineChartCanvas(
                    data = chartData,
                    averageData = averageData,
                    domainStart = domainStart,
                    domainEnd = now,
                    gapMs = config.rawGapMs,
                    averageGapMs = config.averageGapMs,
                    minDomain = minDomain,
                    valueSuffix = suffix,
                    showDayBoundaries = isStepMetric,
                    modifier = Modifier.fillMaxWidth(),
                )
                StatRow(
                    items = listOf(
                        StatRowItem(formatNumber(avg), if (isStepMetric) "Moyenne / jour" else "Moyenne"),
                        StatRowItem(formatNumber(min), if (isStepMetric) "Jour min" else "Min"),
                        StatRowItem(formatNumber(max), if (isStepMetric) "Jour max" else "Max"),
                        StatRowItem(formatNumber(last), if (isStepMetric) "Aujourd'hui" else "Dernière"),
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

private fun StatsMetric.suffix(): String = when (this) {
    StatsMetric.HEART_RATE -> " bpm"
    StatsMetric.SPO2 -> " %"
    StatsMetric.STEPS -> ""
    StatsMetric.SIGNAL -> " %"
}
