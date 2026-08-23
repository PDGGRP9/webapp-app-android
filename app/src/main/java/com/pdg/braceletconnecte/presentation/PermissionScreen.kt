package com.pdg.braceletconnecte.presentation

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdg.braceletconnecte.presentation.components.AppButton
import com.pdg.braceletconnecte.presentation.components.AppCard

private val PERMISSION_LABELS = mapOf(
    Manifest.permission.BLUETOOTH_SCAN to "Recherche Bluetooth",
    Manifest.permission.BLUETOOTH_CONNECT to "Connexion Bluetooth",
    Manifest.permission.ACCESS_FINE_LOCATION to "Localisation précise",
    Manifest.permission.ACCESS_COARSE_LOCATION to "Localisation approximative",
)

/** Embedded inline in the Dashboard's BLE section rather than blocking the whole app. */
@Composable
fun PermissionScreen(
    permissions: List<String>,
    onGrant: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth(), title = "Autoriser le BLE") {
        Text(
            "L'application a besoin d'accéder au Bluetooth pour détecter le bracelet et transmettre les mesures au backend.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            permissions.forEach { permission ->
                Text(
                    "• ${PERMISSION_LABELS[permission] ?: permission}",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        AppButton(text = "Accorder les permissions", onClick = onGrant)
    }
}
