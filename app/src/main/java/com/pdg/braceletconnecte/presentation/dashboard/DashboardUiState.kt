package com.pdg.braceletconnecte.presentation.dashboard

import com.pdg.braceletconnecte.data.api.dto.MeasurementDto
import com.pdg.braceletconnecte.data.api.dto.StatisticsDto
import com.pdg.braceletconnecte.domain.BiometricMeasurement
import com.pdg.braceletconnecte.domain.ConnectionState

data class DashboardUiState(
    val userDisplayName: String = "",
    val connectionState: ConnectionState = ConnectionState.Idle,
    val latestBleMeasurement: BiometricMeasurement? = null,
    val lastError: String? = null,
    val logLines: List<String> = emptyList(),
    val recentMeasurements: List<MeasurementDto> = emptyList(),
    val statistics: StatisticsDto? = null,
    val isRefreshing: Boolean = false,
) {
    val isBleRunning: Boolean
        get() = connectionState == ConnectionState.Scanning ||
            connectionState == ConnectionState.Connecting ||
            connectionState == ConnectionState.Connected ||
            connectionState == ConnectionState.Publishing ||
            connectionState == ConnectionState.Syncing ||
            connectionState == ConnectionState.Live ||
            connectionState == ConnectionState.Reconnecting

    val lastMeasurement: MeasurementDto?
        get() = recentMeasurements.firstOrNull()
}
