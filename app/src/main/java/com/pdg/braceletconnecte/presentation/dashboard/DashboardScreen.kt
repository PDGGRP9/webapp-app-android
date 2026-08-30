package com.pdg.braceletconnecte.presentation.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pdg.braceletconnecte.data.api.dto.capturedAtInstant
import com.pdg.braceletconnecte.domain.BiometricMeasurement
import com.pdg.braceletconnecte.domain.ConnectionState
import com.pdg.braceletconnecte.presentation.PermissionScreen
import com.pdg.braceletconnecte.presentation.components.AppButton
import com.pdg.braceletconnecte.presentation.components.AppButtonVariant
import com.pdg.braceletconnecte.presentation.components.AppCard
import com.pdg.braceletconnecte.presentation.components.AppCardVariant
import com.pdg.braceletconnecte.presentation.components.AppScaffold
import com.pdg.braceletconnecte.presentation.components.Avatar
import com.pdg.braceletconnecte.presentation.components.DataTable
import com.pdg.braceletconnecte.presentation.components.DevicePill
import com.pdg.braceletconnecte.presentation.components.MetricTile
import com.pdg.braceletconnecte.presentation.components.PulseWaveCanvas
import com.pdg.braceletconnecte.presentation.components.StatRow
import com.pdg.braceletconnecte.presentation.components.StatRowItem
import com.pdg.braceletconnecte.presentation.components.clampSp
import com.pdg.braceletconnecte.presentation.components.formatNumber
import com.pdg.braceletconnecte.presentation.components.formatShortTime
import com.pdg.braceletconnecte.presentation.components.headerDate
import com.pdg.braceletconnecte.presentation.components.initials
import com.pdg.braceletconnecte.presentation.components.timeAgo
import com.pdg.braceletconnecte.presentation.navigation.AppRoutes
import com.pdg.braceletconnecte.presentation.stats.hourlyStepDeltas

@Composable
fun DashboardScreen(
    navController: NavController,
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

    val live = uiState.latestBleMeasurement
    val last = uiState.lastMeasurement
    val heartRate = live?.heartRateBpm ?: last?.heartRateBpm
    val spo2 = live?.spo2Percent ?: last?.spo2Percent
    val lastCapturedAt = live?.capturedAt ?: last?.capturedAtInstant()
    val braceletLabel = uiState.bracelets.firstOrNull()?.displayName
        ?: uiState.bracelets.firstOrNull()?.serialNumber
        ?: "Aucun bracelet"

    // step_count only tracks "today so far" (it resets at local midnight), so a true rolling
    // last-24h total is computed separately by summing the hourly deltas.
    val steps24hPairs = uiState.recentMeasurements.mapNotNull { measurement ->
        measurement.capturedAtInstant()?.let { it to measurement.stepCount.toFloat() }
    }
    val steps24h = hourlyStepDeltas(steps24hPairs).sumOf { it.value.toDouble() }

    AppScaffold(navController = navController) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 18.4.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(17.6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Salut, ${uiState.userDisplayName.ifBlank { "toi" }}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        headerDate(),
                        fontSize = 12.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.2.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DevicePill(label = braceletLabel, isActive = uiState.isPaired && uiState.connectionState != ConnectionState.Error)
                    Avatar(
                        label = initials(uiState.userDisplayName),
                        size = 34.dp,
                        modifier = Modifier
                            .padding(start = 8.8.dp)
                            .clickable { navController.navigate(AppRoutes.ACCOUNT) },
                    )
                }
            }

            BpmHero(heartRateBpm = heartRate, capturedAt = lastCapturedAt)

            AppCard(title = "Signal en direct") {
                PulseWaveCanvas(
                    showGridLines = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                )
                val heartValues = uiState.statistics
                StatRow(
                    items = listOf(
                        StatRowItem(formatNumber(heartValues?.minHeartRateBpm?.toDouble()), "Min"),
                        StatRowItem(formatNumber(heartValues?.avgHeartRateBpm), "Moy"),
                        StatRowItem(formatNumber(heartValues?.maxHeartRateBpm?.toDouble()), "Max"),
                    ),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(11.2.dp)) {
                MetricTile(
                    icon = "🩸",
                    name = "SpO2",
                    value = formatNumber(spo2, " %"),
                    isLive = true,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    icon = "👣",
                    name = "Pas (24h)",
                    value = formatNumber(steps24h),
                    isLive = true,
                    modifier = Modifier.weight(1f),
                )
            }

            BleCard(
                uiState = uiState,
                hasPermissions = hasBlePermissions,
                onRequestPermissions = { launcher.launch(permissions.toTypedArray()) },
                onStart = viewModel::startBleStreaming,
                onStop = viewModel::stopBleStreaming,
                onRefresh = viewModel::refreshBackendData,
            )

            if (uiState.recentMeasurements.isNotEmpty()) {
                AppCard(title = "Dernières mesures") {
                    DataTable(
                        headers = listOf("Heure", "BPM", "SpO2", "Pas"),
                        rows = uiState.recentMeasurements.take(10).map { measurement ->
                            listOf(
                                formatShortTime(measurement.capturedAtInstant()),
                                measurement.heartRateBpm?.toString() ?: "-",
                                formatNumber(measurement.spo2Percent, " %"),
                                measurement.stepCount.toString(),
                            )
                        },
                    )
                }
            }

            uiState.lastError?.let { error ->
                AppCard(variant = AppCardVariant.Card2) {
                    Text("Erreur", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.8.sp)
                }
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
private fun BpmHero(heartRateBpm: Int?, capturedAt: java.time.Instant?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "FRÉQUENCE CARDIAQUE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            val scale by rememberInfiniteBeat()
            Text("❤️", fontSize = 20.sp, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
            Text(
                text = formatNumber(heartRateBpm?.toDouble()),
                fontSize = clampSp(4.6f, 0.26f, 6.2f),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.8.dp),
            )
            Text(
                "bpm",
                fontSize = 16.8.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.8.dp, bottom = 6.dp),
            )
        }
        Text(
            text = if (capturedAt != null) {
                buildAnnotatedString {
                    append("Dernière mesure ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                        append(timeAgo(capturedAt))
                    }
                }
            } else {
                buildAnnotatedString { append("En attente d'une première mesure") }
            },
            fontSize = 12.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.4.dp),
        )
    }
}

@Composable
private fun rememberInfiniteBeat() = androidx.compose.animation.core.rememberInfiniteTransition(label = "beat")
    .animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(485, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "beat-scale",
    )

@Composable
private fun BleCard(
    uiState: DashboardUiState,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
) {
    if (!hasPermissions) {
        PermissionScreen(permissions = blePermissions(), onGrant = onRequestPermissions)
        return
    }

    AppCard(title = "Bracelet ESP32") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DevicePill(label = uiState.connectionState.label, isActive = uiState.isBleRunning)
        }
        Text(
            "Connexion au bracelet ESP32 et retransmission en direct vers le backend.",
            fontSize = 12.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.6.dp)) {
            AppButton(
                text = "Démarrer",
                onClick = onStart,
                enabled = !uiState.isBleRunning,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                text = "Arrêter",
                onClick = onStop,
                variant = AppButtonVariant.Ghost,
                enabled = uiState.isBleRunning,
                modifier = Modifier.weight(1f),
            )
        }
        if (uiState.logLines.isNotEmpty()) {
            AppCard(variant = AppCardVariant.Card2, modifier = Modifier.heightIn(max = 140.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    uiState.logLines.forEach { line ->
                        Text(line, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        TextButton(onClick = onRefresh) { Text("Actualiser les données") }
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
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Associer ce bracelet ?") },
        text = {
            Text(
                "Associer \"${measurement.deviceName}\" (${measurement.macAddress}) à votre compte ? " +
                    "Un seul bracelet peut être associé par compte.",
            )
        },
        confirmButton = {
            AppButton(text = "Associer", onClick = onConfirm, enabled = !isPairing, isLoading = isPairing)
        },
        dismissButton = {
            AppButton(text = "Plus tard", onClick = onDismiss, variant = AppButtonVariant.Ghost, enabled = !isPairing)
        },
    )
}

private fun blePermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
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
