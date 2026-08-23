package com.pdg.braceletconnecte.presentation.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdg.braceletconnecte.BraceletConnecteApplication
import com.pdg.braceletconnecte.data.auth.AuthState
import com.pdg.braceletconnecte.data.measurements.MEASUREMENTS_POLL_INTERVAL_MS
import com.pdg.braceletconnecte.domain.AppConfig
import com.pdg.braceletconnecte.domain.BiometricMeasurement
import com.pdg.braceletconnecte.domain.BraceletBleClient
import com.pdg.braceletconnecte.domain.BraceletEvent
import com.pdg.braceletconnecte.domain.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BraceletConnecteApplication
    private val authRepository = app.authRepository
    private val braceletRepository = app.braceletRepository
    private val measurementsRepository = app.measurementsRepository

    private val appConfig = AppConfig.default()
    private val bleClient = BraceletBleClient(application, appConfig.bracelet)
    private val timestampFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var bleSessionJob: Job? = null
    private var pollJob: Job? = null

    init {
        val user = (authRepository.authState.value as? AuthState.LoggedIn)?.user
        val displayName = user?.firstName?.takeIf { it.isNotBlank() } ?: user?.username.orEmpty()
        _uiState.update { it.copy(userDisplayName = displayName) }
        startPolling()
    }

    private fun currentUserId(): Long? = (authRepository.authState.value as? AuthState.LoggedIn)?.user?.id

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshBackendData()
                delay(MEASUREMENTS_POLL_INTERVAL_MS)
            }
        }
    }

    fun refreshBackendData() {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            braceletRepository.listBracelets(userId).onSuccess { bracelets ->
                _uiState.update { it.copy(bracelets = bracelets) }
            }
            measurementsRepository.fetchDatas(userId).onSuccess { response ->
                _uiState.update { it.copy(recentMeasurements = response.datas) }
            }
            measurementsRepository.fetchStatistics(userId).onSuccess { statistics ->
                _uiState.update { it.copy(statistics = statistics) }
            }

            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun startBleStreaming() {
        if (bleSessionJob?.isActive == true) return

        _uiState.update {
            it.copy(
                connectionState = ConnectionState.Scanning,
                lastError = null,
                logLines = prependLog(it.logLines, "Démarrage du flux BLE"),
            )
        }

        bleSessionJob = viewModelScope.launch {
            bleClient.observe().collect { event ->
                when (event) {
                    is BraceletEvent.StateChanged -> {
                        _uiState.update { current -> current.copy(connectionState = event.state) }
                    }

                    is BraceletEvent.Log -> appendLog(event.message)

                    is BraceletEvent.Error -> {
                        _uiState.update { current ->
                            current.copy(connectionState = ConnectionState.Error, lastError = event.message)
                        }
                        appendLog(event.message)
                    }

                    is BraceletEvent.MeasurementReceived -> {
                        onMeasurementReceived(event.measurement)
                    }
                }
            }
        }
    }

    fun stopBleStreaming() {
        bleSessionJob?.cancel()
        bleSessionJob = null
        _uiState.update { current ->
            current.copy(
                connectionState = ConnectionState.Stopped,
                logLines = prependLog(current.logLines, "Flux BLE arrêté"),
            )
        }
    }

    private fun onMeasurementReceived(measurement: BiometricMeasurement) {
        _uiState.update { current ->
            current.copy(
                connectionState = ConnectionState.Connected,
                latestBleMeasurement = measurement,
                lastError = null,
                logLines = prependLog(current.logLines, "Mesure reçue ${formatTimestamp(measurement.capturedAt)}"),
                pendingPairingCandidate = if (!current.isPaired && !current.pairingPromptDismissed && current.pendingPairingCandidate == null) {
                    measurement
                } else {
                    current.pendingPairingCandidate
                },
            )
        }
        retransmitMeasurement(measurement)
    }

    private fun retransmitMeasurement(measurement: BiometricMeasurement) {
        _uiState.update { it.copy(connectionState = ConnectionState.Publishing) }
        viewModelScope.launch {
            measurementsRepository.postMeasurement(measurement)
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            connectionState = ConnectionState.Connected,
                            logLines = prependLog(current.logLines, "Mesure transmise au backend"),
                        )
                    }
                }
                .onFailure { throwable ->
                    val message = authRepository.errorMessage(throwable)
                    _uiState.update { current ->
                        current.copy(
                            connectionState = ConnectionState.Connected,
                            lastError = message,
                            logLines = prependLog(current.logLines, "Échec de transmission: $message"),
                        )
                    }
                }
        }
    }

    fun confirmPairing() {
        val candidate = _uiState.value.pendingPairingCandidate ?: return
        val userId = currentUserId() ?: return

        _uiState.update { it.copy(isPairing = true) }
        viewModelScope.launch {
            braceletRepository.pair(
                userId = userId,
                deviceUid = candidate.deviceUid,
                serialNumber = candidate.serialNumber,
                displayName = candidate.deviceName,
                macAddress = candidate.macAddress,
            )
                .onSuccess { bracelet ->
                    _uiState.update { current ->
                        current.copy(
                            isPairing = false,
                            bracelets = listOf(bracelet),
                            pendingPairingCandidate = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isPairing = false,
                            lastError = authRepository.errorMessage(throwable),
                        )
                    }
                }
        }
    }

    fun dismissPairing() {
        _uiState.update { it.copy(pendingPairingCandidate = null, pairingPromptDismissed = true) }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private fun appendLog(message: String) {
        _uiState.update { current -> current.copy(logLines = prependLog(current.logLines, message)) }
    }

    private fun prependLog(logLines: List<String>, message: String): List<String> {
        val line = "${timestampFormatter.format(Instant.now())}  $message"
        return (listOf(line) + logLines).take(8)
    }

    private fun formatTimestamp(instant: Instant): String = timestampFormatter.format(instant)

    override fun onCleared() {
        super.onCleared()
        bleSessionJob?.cancel()
        pollJob?.cancel()
    }
}
