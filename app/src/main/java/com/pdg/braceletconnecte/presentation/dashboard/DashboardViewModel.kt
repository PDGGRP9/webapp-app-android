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
    private val measurementStore = app.measurementStore

    private val appConfig = AppConfig.default()
    private val timestampFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    // The BLE client writes straight to the database and only acknowledges
    // afterwards: that is the invariant of the protocol (nothing is flushed on
    // the bracelet before the ACK). Sending to the backend is the
    // MeasurementUploader's job, not BLE's.
    private val bleClient = BraceletBleClient(application, appConfig.bracelet) { measurements ->
        // Write first (that write is what allows the ACK on the BLE side), then
        // wake the uploader: both live data and catch-up go out to the backend
        // right away, without waiting for its 5 s tick.
        measurementStore.save(measurements).also { app.uploader.nudge() }
    }

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
            // Bumped from the default 500: the dashboard's rolling 24h step total needs the
            // same depth of history as the Stats screen to seed its hourly-delta baseline
            // correctly (matches the web frontend's MeasurementsContext limit of 5000).
            measurementsRepository.fetchDatas(userId, limit = 5000).onSuccess { response ->
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
                        _uiState.update { current ->
                            current.copy(
                                connectionState = event.state,
                                logLines = prependLog(current.logLines, event.state.label),
                            )
                        }
                    }

                    is BraceletEvent.Error -> {
                        _uiState.update { current ->
                            current.copy(
                                connectionState = ConnectionState.Error,
                                lastError = event.message,
                                logLines = prependLog(current.logLines, event.message),
                            )
                        }
                        // The session is over (Bluetooth turned off, scan refused...).
                        // We release the job right away, otherwise the "already
                        // running" guard would turn the "Démarrer" button into a
                        // no-op.
                        bleSessionJob?.cancel()
                        bleSessionJob = null
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
