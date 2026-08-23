package com.pdg.braceletconnecte.presentation.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pdg.braceletconnecte.presentation.components.AppButton
import com.pdg.braceletconnecte.presentation.components.AppButtonVariant
import com.pdg.braceletconnecte.presentation.components.AppCard
import com.pdg.braceletconnecte.presentation.components.AppScaffold
import com.pdg.braceletconnecte.presentation.components.AppSwitch
import com.pdg.braceletconnecte.presentation.components.Avatar
import com.pdg.braceletconnecte.presentation.components.ConfirmWordDialog
import com.pdg.braceletconnecte.presentation.components.DevicePill
import com.pdg.braceletconnecte.presentation.components.SettingRow
import com.pdg.braceletconnecte.presentation.components.initials

@Composable
fun AccountScreen(
    navController: NavController,
    viewModel: AccountViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    var collectConsent by remember { mutableStateOf(true) }
    var localProcessing by remember { mutableStateOf(true) }
    var isDeleteDialogOpen by remember { mutableStateOf(false) }

    val fullName = listOfNotNull(uiState.user?.firstName, uiState.user?.lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { uiState.user?.username ?: "-" }
    val bracelet = uiState.bracelets.firstOrNull()

    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportData(it, "json") }
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportData(it, "csv") }
    }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        val message = uiState.errorMessage ?: uiState.successMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    AppScaffold(navController = navController, snackbarHostState = snackbarHostState) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 18.4.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(17.6.dp),
        ) {
            Text("Compte", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)

            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(label = initials(uiState.user?.firstName, uiState.user?.lastName ?: uiState.user?.username))
                    Column(modifier = Modifier.padding(start = 14.4.dp)) {
                        Text(fullName, fontSize = 16.8.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            uiState.user?.email ?: "-",
                            fontSize = 13.1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AppCard(title = "Bracelet") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            bracelet?.displayName ?: bracelet?.serialNumber ?: "Aucun bracelet appairé",
                            fontSize = 14.4.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (bracelet != null) {
                            Text(
                                "Numéro de série ${bracelet.serialNumber}",
                                fontSize = 11.8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DevicePill(label = if (bracelet != null) "Appairé" else "Aucun", isActive = bracelet != null)
                }
            }

            AppCard(title = "Mes données · RGPD") {
                SettingRow(
                    label = "Consentement à la collecte",
                    hint = "Mesure du signal PPG et calcul du pouls. Révocable à tout moment.",
                    showDivider = false,
                    trailing = { AppSwitch(checked = collectConsent, onCheckedChange = { collectConsent = it }) },
                )
                SettingRow(
                    label = "Traitement local privilégié",
                    hint = "Minimisation : seules les données nécessaires quittent l'appareil.",
                    trailing = { AppSwitch(checked = localProcessing, onCheckedChange = { localProcessing = it }) },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(9.6.dp), modifier = Modifier.padding(top = 4.8.dp)) {
                    AppButton(
                        text = if (uiState.isExporting) "Export…" else "Exporter en JSON",
                        onClick = { exportJsonLauncher.launch("mes-donnees.json") },
                        variant = AppButtonVariant.Ghost,
                        enabled = !uiState.isExporting,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = if (uiState.isExporting) "Export…" else "Exporter en CSV",
                        onClick = { exportCsvLauncher.launch("mes-donnees.csv") },
                        variant = AppButtonVariant.Ghost,
                        enabled = !uiState.isExporting,
                        modifier = Modifier.weight(1f),
                    )
                }

                AppButton(
                    text = "Supprimer toutes mes données",
                    onClick = { isDeleteDialogOpen = true },
                    variant = AppButtonVariant.Danger,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = buildString {
                        append("Privacy by design — données chiffrées au repos et en transit, ")
                        append("pseudonymisation (séparation données brutes / identité), formats ouverts ")
                        append("et documentés. Tes données t'appartiennent.")
                    },
                    fontSize = 11.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AppButton(
                text = "Se déconnecter",
                onClick = { viewModel.logout {} },
                variant = AppButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (isDeleteDialogOpen) {
        ConfirmWordDialog(
            title = "Supprimer toutes mes données",
            message = "Cette action est définitive et irréversible : toutes tes mesures seront effacées. " +
                "Ton bracelet reste appairé à ton compte. Il n'y a pas de retour en arrière possible.",
            confirmWord = "supprimer",
            confirmButtonLabel = "Supprimer définitivement",
            isSubmitting = uiState.isDeleting,
            onConfirm = { viewModel.deleteAllData { isDeleteDialogOpen = false } },
            onDismiss = { isDeleteDialogOpen = false },
        )
    }
}
