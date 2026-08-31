package com.pdg.braceletconnecte.presentation.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdg.braceletconnecte.presentation.components.AppButton
import com.pdg.braceletconnecte.presentation.components.AppButtonVariant
import com.pdg.braceletconnecte.presentation.components.AppTextField
import com.pdg.braceletconnecte.presentation.components.clampSp
import com.pdg.braceletconnecte.ui.theme.AppColors

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegistered: () -> Unit,
    viewModel: RegisterViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Lime)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.4.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(17.6.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Crée ton compte",
                color = AppColors.Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = clampSp(1.9f, 0.09f, 2.5f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Un compte suffit pour suivre les mesures de ton bracelet.",
                color = AppColors.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 14.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(11.2.dp)) {
            AppTextField(
                label = "Email",
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                placeholder = "prenom.nom@exemple.ch",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            AppTextField(
                label = "Nom d'utilisateur",
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                placeholder = "demo",
            )
            AppTextField(
                label = "Mot de passe",
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                placeholder = "••••••••••",
                isPassword = true,
            )
            AppTextField(
                label = "Prénom",
                value = uiState.firstName,
                onValueChange = viewModel::onFirstNameChange,
            )
            AppTextField(
                label = "Nom",
                value = uiState.lastName,
                onValueChange = viewModel::onLastNameChange,
            )
            AppTextField(
                label = "URL du backend",
                value = uiState.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                placeholder = "http://10.0.2.2:8000",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            uiState.errorMessage?.let { error ->
                Text(error, color = AppColors.Danger, fontSize = 12.8.sp, fontWeight = FontWeight.SemiBold)
            }

            AppButton(
                text = "Créer le compte",
                onClick = { viewModel.submit(onRegistered) },
                variant = AppButtonVariant.Ink,
                isLoading = uiState.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onNavigateToLogin) {
                    Text("Déjà un compte ? Se connecter", color = AppColors.InkSoft, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
