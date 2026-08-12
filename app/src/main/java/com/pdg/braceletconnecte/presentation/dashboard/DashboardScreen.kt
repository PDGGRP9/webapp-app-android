package com.pdg.braceletconnecte.presentation.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdg.braceletconnecte.data.api.dto.capturedAtInstant
import com.pdg.braceletconnecte.domain.BiometricMeasurement
import com.pdg.braceletconnecte.domain.ConnectionState
import com.pdg.braceletconnecte.presentation.PermissionScreen
import com.pdg.braceletconnecte.presentation.components.DataTable
import com.pdg.braceletconnecte.presentation.components.MetricCard
import com.pdg.braceletconnecte.presentation.components.formatShortTime
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToStats: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = remember { blePermissions() }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    val hasBlePermissions = permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bracelet Connecté") },
                actions = {
                    TextButton(onClick = onNavigateToStats) { Text("Stats") }
                    IconButton(onClick = viewModel::refreshBackendData) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualiser")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Déconnexion")
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
            Text(
                "Bonjour ${uiState.userDisplayName.ifBlank { "" }}".trim(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            MetricsRow(uiState)

            BleSection(
                uiState = uiState,
                hasPermissions = hasBlePermissions,
                onRequestPermissions = { launcher.launch(permissions.toTypedArray()) },
                onStart = viewModel::startBleStreaming,
                onStop = viewModel::stopBleStreaming,
            )

            StatisticsCard(uiState)

            RecentMeasurementsCard(uiState)

            LogCard(uiState.logLines)

            uiState.lastError?.let { error ->
                ErrorCard(message = error)
            }
        }
    }

    uiState.pendingPairingCandidate?.let { candidate ->
        PairingDialog(
            measurement = candidate,
            isPairing = uiState.isPairing,
            onConfirm = viewModel::confirmPairing,
            onDismiss = viewModel::dismissPairing,
        )
    }
}

@Composable
private fun MetricsRow(uiState: DashboardUiState) {
    val live = uiState.latestBleMeasurement
    val last = uiState.lastMeasurement

    val heartRate = live?.heartRateBpm?.toString() ?: last?.heartRateBpm?.toString() ?: "—"
    val spo2 = live?.spo2Percent?.let(::formatDecimal) ?: last?.spo2Percent?.let(::formatDecimal) ?: "—"
    val steps = live?.stepCount?.toString() ?: last?.stepCount?.toString() ?: "—"
    val signal = live?.signalQuality?.toString() ?: last?.signalQuality?.toString() ?: "—"

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(label = "BPM", value = heartRate, modifier = Modifier.weight(1f))
        MetricCard(label = "SpO2", value = spo2, modifier = Modifier.weight(1f))
        MetricCard(label = "Pas", value = steps, modifier = Modifier.weight(1f))
        MetricCard(label = "Signal", value = signal, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BleSection(
    uiState: DashboardUiState,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    if (!hasPermissions) {
        PermissionScreen(
            permissions = blePermissions(),
            onGrant = onRequestPermissions,
        )
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(uiState.connectionState.label) })
                if (uiState.isPaired) {
                    AssistChip(onClick = {}, label = { Text(uiState.bracelets.first().displayName ?: "Bracelet associé") })
                }
            }
            Text(
                "Connexion au bracelet ESP32 et retransmission en direct vers le backend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, enabled = !uiState.isBleRunning) { Text("Démarrer") }
                OutlinedButton(onClick = onStop, enabled = uiState.isBleRunning) { Text("Arrêter") }
            }
        }
    }
}

@Composable
private fun StatisticsCard(uiState: DashboardUiState) {
    val statistics = uiState.statistics
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Statistiques globales", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (statistics == null) {
                Text(
                    "En attente de données…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(label = "Mesures", value = statistics.measurementsCount.toString(), modifier = Modifier.weight(1f))
                    MetricCard(label = "BPM moyen", value = statistics.avgHeartRateBpm?.let(::formatDecimal) ?: "—", modifier = Modifier.weight(1f))
                    MetricCard(label = "SpO2 moyen", value = statistics.avgSpo2Percent?.let(::formatDecimal) ?: "—", modifier = Modifier.weight(1f))
                    MetricCard(label = "Max pas", value = statistics.maxStepCount.toString(), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecentMeasurementsCard(uiState: DashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Dernières mesures", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            DataTable(
                headers = listOf("Heure", "BPM", "SpO2", "Pas"),
                rows = uiState.recentMeasurements.take(10).map { measurement ->
                    listOf(
                        formatShortTime(measurement.capturedAtInstant()),
                        measurement.heartRateBpm?.toString() ?: "—",
                        measurement.spo2Percent?.let(::formatDecimal) ?: "—",
                        measurement.stepCount.toString(),
                    )
                },
            )
        }
    }
}

@Composable
private fun LogCard(lines: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Journal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Erreur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun PairingDialog(
    measurement: BiometricMeasurement,
    isPairing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Associer ce bracelet ?") },
        text = {
            Text(
                "Associer \"${measurement.deviceName}\" (${measurement.macAddress}) à votre compte ? " +
                    "Un seul bracelet peut être associé par compte.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isPairing) { Text("Associer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPairing) { Text("Plus tard") }
        },
    )
}

private fun blePermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private val ConnectionState.label: String
    get() = when (this) {
        ConnectionState.Idle -> "Prêt"
        ConnectionState.Scanning -> "Scan BLE"
        ConnectionState.Connecting -> "Connexion"
        ConnectionState.Connected -> "Connecté"
        ConnectionState.Publishing -> "Transmission"
        ConnectionState.Stopped -> "Arrêté"
        ConnectionState.Error -> "Erreur"
    }

private fun formatDecimal(value: Double): String {
    return NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)
}
