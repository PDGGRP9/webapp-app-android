package com.pdg.braceletconnecte.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Type-to-confirm destructive-action dialog — mirrors the web design's `ConfirmDialog.tsx`.
 * The confirm button stays disabled until the typed text matches [confirmWord] (case-insensitive).
 */
@Composable
fun ConfirmWordDialog(
    title: String,
    message: String,
    confirmWord: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmButtonLabel: String = "Supprimer",
    cancelButtonLabel: String = "Annuler",
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
) {
    var input by remember { mutableStateOf("") }
    val isMatch = input.trim().equals(confirmWord, ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column {
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    label = { Text("Tape « $confirmWord » pour confirmer") },
                    singleLine = true,
                )
                errorMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                text = if (isSubmitting) "Suppression…" else confirmButtonLabel,
                onClick = onConfirm,
                variant = AppButtonVariant.Danger,
                enabled = isMatch,
                isLoading = isSubmitting,
            )
        },
        dismissButton = {
            AppButton(
                text = cancelButtonLabel,
                onClick = onDismiss,
                variant = AppButtonVariant.Ghost,
                enabled = !isSubmitting,
            )
        },
    )
}
