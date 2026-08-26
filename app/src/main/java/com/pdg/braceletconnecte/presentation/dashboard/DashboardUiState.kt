package com.pdg.braceletconnecte.presentation.dashboard

import com.pdg.braceletconnecte.data.api.dto.BraceletDto
import com.pdg.braceletconnecte.data.api.dto.MeasurementDto
import com.pdg.braceletconnecte.data.api.dto.StatisticsDto
import com.pdg.braceletconnecte.domain.BiometricMeasurement
import com.pdg.braceletconnecte.domain.ConnectionState

data class DashboardUiState(
    val userDisplayName: String = "",
    val connectionState: ConnectionState = ConnectionState.Idle,
    val latestBleMeasurement: BiometricMeasurement? = null,
    val lastError: String? = null,
    val bracelets: List<BraceletDto> = emptyList(),
    val recentMeasurements: List<MeasurementDto> = emptyList(),
    val statistics: StatisticsDto? = null,
    val pendingPairingCandidate: BiometricMeasurement? = null,
    val pairingPromptDismissed: Boolean = false,
    val isPairing: Boolean = false,
    val isRefreshing: Boolean = false,
    /** The user pressed "Stop": don't restart on our own. */
    val stoppedByUser: Boolean = false,
) {
    val isBleRunning: Boolean
        get() = connectionState == ConnectionState.Scanning ||
            connectionState == ConnectionState.Connecting ||
            connectionState == ConnectionState.Connected ||
            connectionState == ConnectionState.Publishing ||
            connectionState == ConnectionState.Syncing ||
            connectionState == ConnectionState.Live ||
            connectionState == ConnectionState.Reconnecting

    val isPaired: Boolean
        get() = bracelets.isNotEmpty()

    val lastMeasurement: MeasurementDto?
        get() = recentMeasurements.firstOrNull()
}
