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
import com.pdg.braceletconnecte.presentation.components.APP_ZONE
import com.pdg.braceletconnecte.presentation.components.AppButton
import com.pdg.braceletconnecte.presentation.components.AppButtonVariant
import com.pdg.braceletconnecte.presentation.components.AppCard
import com.pdg.braceletconnecte.presentation.components.AppScaffold
import com.pdg.braceletconnecte.presentation.components.AppSelect
import com.pdg.braceletconnecte.presentation.components.BarChartCanvas
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
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private const val DAYS_IN_WEEK = 7

@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRawTable by remember { mutableStateOf(false) }

    val now = Instant.now()
    val isStepMetric = uiState.metric == StatsMetric.STEPS
    // bpm/spo2 have no range toggle any more — they're always the 7-day view.
    val range = if (isStepMetric) uiState.stepRange else StatsRange.LAST_7D
    val domainStart = now.minusSeconds(range.hours * 3600)

    val filtered = uiState.measurements.mapNotNull { measurement ->
        val instant = measurement.capturedAtInstant() ?: return@mapNotNull null
        if (instant.isBefore(domainStart)) null else measurement to instant
    }
    val rawPairs = filtered.mapNotNull { (measurement, instant) ->
        uiState.metric.valueOf(measurement)?.let { instant to it.toFloat() }
    }

    // step_count: bar chart of per-period deltas, computed from the *unfiltered* history — the
    // hourly bucketing needs data before the visible window to seed its running-total baseline.
    val allStepPairs = uiState.measurements.mapNotNull { measurement ->
        measurement.capturedAtInstant()?.let { it to measurement.stepCount.toFloat() }
    }
    val stepBarData = when {
        !isStepMetric -> emptyList()
        range == StatsRange.LAST_24H -> hourlyStepDeltas(allStepPairs, now.toEpochMilli())
        else -> dailyStepTotals(allStepPairs, DAYS_IN_WEEK, now.toEpochMilli())
    }

    // heart_rate_bpm/spo2_percent: smoothed 7-day line chart.
    val chartData = if (isStepMetric) emptyList() else bucketAverage(rawPairs, LINE_CHART_CONFIG.pointBucketMs)
    val averageData = if (isStepMetric) emptyList() else bucketAverage(rawPairs, LINE_CHART_CONFIG.averageBucketMs)

    // Summary numbers always reflect a fixed 7-day window, independent of which bar-chart range
    // is selected for steps, so the "Résumé" card never jumps around when 24h/7j is toggled.
    val summaryDomainStart = now.minusSeconds(StatsRange.LAST_7D.hours * 3600)
    val summaryPairs = uiState.measurements.mapNotNull { measurement ->
        val instant = measurement.capturedAtInstant() ?: return@mapNotNull null
        if (instant.isBefore(summaryDomainStart)) return@mapNotNull null
        val value = uiState.metric.valueOf(measurement) ?: return@mapNotNull null
        instant to value.toFloat()
    }
    val summaryValues = if (isStepMetric) dailyTotals(summaryPairs) else summaryPairs.map { it.second }
    val lastPair = summaryPairs.maxByOrNull { it.first }
    val avg = summaryValues.takeIf { it.isNotEmpty() }?.map { it.toDouble() }?.average()
    val min = summaryValues.minOrNull()?.toDouble()
    val max = summaryValues.maxOrNull()?.toDouble()
    val last = lastPair?.second?.toDouble()
    val suffix = uiState.metric.suffix()

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
                if (isStepMetric) {
                    SegmentedControl(
                        options = StatsRange.entries.map { SegmentedOption(it, it.label) },
                        selected = uiState.stepRange,
                        onSelect = viewModel::selectStepRange,
                    )
                }
            }

            AppCard(title = uiState.metric.label) {
                if (isStepMetric) {
                    BarChartCanvas(
                        data = stepBarData,
                        valueSuffix = suffix,
                        formatAxisLabel = { instant -> if (range == StatsRange.LAST_24H) hourAxisLabel(instant) else dayAxisLabel(instant) },
                        formatTooltipLabel = { instant -> if (range == StatsRange.LAST_24H) hourTooltipLabel(instant) else dayTooltipLabel(instant) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DensityLineChartCanvas(
                        data = chartData,
                        averageData = averageData,
                        domainStart = domainStart,
                        domainEnd = now,
                        gapMs = LINE_CHART_CONFIG.rawGapMs,
                        averageGapMs = LINE_CHART_CONFIG.averageGapMs,
                        valueSuffix = suffix,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                StatRow(
                    items = listOf(
                        StatRowItem(formatNumber(avg), if (isStepMetric) "Moyenne / jour" else "Moyenne"),
                        StatRowItem(
                            "${formatNumber(min)} / ${formatNumber(max)}",
                            if (isStepMetric) "Jour min / max" else "Min / Max",
                        ),
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
}

private fun hourAxisLabel(instant: Instant): String = "${instant.atZone(APP_ZONE).hour}h"

private fun hourTooltipLabel(instant: Instant): String {
    val hour = instant.atZone(APP_ZONE).hour
    return "${hour}h – ${(hour + 1) % 24}h"
}

private fun dayAxisLabel(instant: Instant): String =
    instant.atZone(APP_ZONE).dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.FRENCH)
        .replaceFirstChar { it.uppercase(Locale.FRENCH) }

private fun dayTooltipLabel(instant: Instant): String {
    val zoned = instant.atZone(APP_ZONE)
    val weekday = zoned.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.FRENCH)
        .replaceFirstChar { it.uppercase(Locale.FRENCH) }
    return "$weekday ${zoned.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))}"
}
