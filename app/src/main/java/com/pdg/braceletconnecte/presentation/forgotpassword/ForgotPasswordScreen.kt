package com.pdg.braceletconnecte.presentation.forgotpassword

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
fun ForgotPasswordScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel(),
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
                text = "Mot de passe oublié",
                color = AppColors.Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = clampSp(1.9f, 0.09f, 2.5f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Retrouve l'accès à ton compte en deux étapes.",
                color = AppColors.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 14.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        when (uiState.step) {
            ForgotPasswordStep.Email -> Column(verticalArrangement = Arrangement.spacedBy(11.2.dp)) {
                AppTextField(
                    label = "Email",
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    placeholder = "prenom.nom@exemple.ch",
                )
                uiState.errorMessage?.let { error ->
                    Text(error, color = AppColors.Danger, fontSize = 12.8.sp, fontWeight = FontWeight.SemiBold)
                }
                AppButton(
                    text = "Vérifier",
                    onClick = viewModel::submitEmail,
                    variant = AppButtonVariant.Ink,
                    isLoading = uiState.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onNavigateToLogin) {
                        Text("Retour à la connexion", color = AppColors.InkSoft, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            ForgotPasswordStep.Reset -> Column(verticalArrangement = Arrangement.spacedBy(11.2.dp)) {
                Text("Compte trouvé : ${uiState.email}", color = AppColors.InkSoft, fontSize = 12.8.sp)
                AppTextField(
                    label = "Nouveau mot de passe",
                    value = uiState.newPassword,
                    onValueChange = viewModel::onNewPasswordChange,
                    placeholder = "••••••••••",
                    isPassword = true,
                )
                AppTextField(
                    label = "Confirmer le mot de passe",
                    value = uiState.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    placeholder = "••••••••••",
                    isPassword = true,
                )
                Text(
                    "Minimum 8 caractères, une majuscule et un caractère spécial.",
                    color = AppColors.InkSoft,
                    fontSize = 11.2.sp,
                )
                uiState.errorMessage?.let { error ->
                    Text(error, color = AppColors.Danger, fontSize = 12.8.sp, fontWeight = FontWeight.SemiBold)
                }
                uiState.successMessage?.let { success ->
                    Text(success, color = AppColors.InkSoft, fontSize = 12.8.sp, fontWeight = FontWeight.SemiBold)
                }
                AppButton(
                    text = "Réinitialiser le mot de passe",
                    onClick = { viewModel.submitReset(onNavigateToLogin) },
                    variant = AppButtonVariant.Ink,
                    isLoading = uiState.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
