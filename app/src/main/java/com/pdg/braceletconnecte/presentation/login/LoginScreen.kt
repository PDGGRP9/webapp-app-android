package com.pdg.braceletconnecte.presentation.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.pdg.braceletconnecte.presentation.components.Pill
import com.pdg.braceletconnecte.presentation.components.PulseWaveCanvas
import com.pdg.braceletconnecte.presentation.components.clampSp
import com.pdg.braceletconnecte.ui.theme.AppColors

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onLoggedIn: () -> Unit,
    onContinueAsGuest: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Pill(text = "Projet PDG · GRP9", borderColor = AppColors.Ink, contentColor = AppColors.InkSoft)
            Pill(text = "Open source", borderColor = AppColors.Ink, contentColor = AppColors.InkSoft)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "BraceCo",
                color = AppColors.Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = clampSp(1.9f, 0.09f, 2.5f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Open source. Sans abonnement. Sans espion.",
                color = AppColors.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 14.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            PulseWaveCanvas(
                lineColor = AppColors.Ink,
                showGridLines = false,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.8f)
                    .height(36.dp),
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
                label = "Mot de passe",
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                placeholder = "••••••••••",
                isPassword = true,
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
                text = "Se connecter",
                onClick = { viewModel.submit(onLoggedIn) },
                variant = AppButtonVariant.Ink,
                isLoading = uiState.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = { viewModel.continueAsGuest(onContinueAsGuest) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.5.dp, AppColors.Ink),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Ink),
                contentPadding = PaddingValues(vertical = 13.6.dp),
            ) {
                Text(
                    "Ignorer et voir les données en direct",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onNavigateToForgotPassword) {
                    Text("Mot de passe oublié ?", color = AppColors.InkSoft, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onNavigateToRegister) {
                    Text("Créer un compte", color = AppColors.InkSoft, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Text(
            text = "TES DONNÉES T'APPARTIENNENT",
            color = AppColors.InkSoft,
            fontSize = 11.2.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = AppColors.InkSoft.copy(alpha = 0.4f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
                .padding(top = 12.8.dp),
        )
    }
}
