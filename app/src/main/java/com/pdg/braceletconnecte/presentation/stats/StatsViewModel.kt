package com.pdg.braceletconnecte.presentation.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdg.braceletconnecte.BraceletConnecteApplication
import com.pdg.braceletconnecte.data.api.dto.MeasurementDto
import com.pdg.braceletconnecte.data.auth.AuthState
import com.pdg.braceletconnecte.data.measurements.MEASUREMENTS_POLL_INTERVAL_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

enum class StatsMetric(val label: String) {
    HEART_RATE("BPM"),
    SPO2("SpO2"),
    STEPS("Pas"),
    SIGNAL("Signal"),
}

enum class StatsRange(val label: String, val hours: Long) {
    LAST_24H("24h", 24),
    LAST_7D("7j", 24 * 7),
    LAST_30D("30j", 24 * 30),
}

data class StatsUiState(
    val metric: StatsMetric = StatsMetric.HEART_RATE,
    val range: StatsRange = StatsRange.LAST_24H,
    val measurements: List<MeasurementDto> = emptyList(),
    val showRawTable: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BraceletConnecteApplication
    private val authRepository = app.authRepository
    private val measurementsRepository = app.measurementsRepository

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(MEASUREMENTS_POLL_INTERVAL_MS)
            }
        }
    }

    fun selectMetric(metric: StatsMetric) = _uiState.update { it.copy(metric = metric) }
    fun selectRange(range: StatsRange) = _uiState.update { it.copy(range = range) }
    fun toggleRawTable() = _uiState.update { it.copy(showRawTable = !it.showRawTable) }

    private fun refresh() {
        val userId = (authRepository.authState.value as? AuthState.LoggedIn)?.user?.id ?: return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            measurementsRepository.fetchDatas(userId, limit = 500)
                .onSuccess { response ->
                    _uiState.update { it.copy(measurements = response.datas, isLoading = false, errorMessage = null) }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = authRepository.errorMessage(throwable)) }
                }
        }
    }
}

fun StatsMetric.valueOf(measurement: MeasurementDto): Double? = when (this) {
    StatsMetric.HEART_RATE -> measurement.heartRateBpm?.toDouble()
    StatsMetric.SPO2 -> measurement.spo2Percent
    StatsMetric.STEPS -> measurement.stepCount.toDouble()
    StatsMetric.SIGNAL -> measurement.signalQuality?.toDouble()
}

fun StatsRange.cutoff(now: Instant = Instant.now()): Instant = now.minusSeconds(hours * 3600)
