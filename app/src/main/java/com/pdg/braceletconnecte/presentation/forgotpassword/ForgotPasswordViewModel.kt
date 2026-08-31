package com.pdg.braceletconnecte.presentation.forgotpassword

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdg.braceletconnecte.BraceletConnecteApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ForgotPasswordStep { Email, Reset }

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.Email,
    val email: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

/** Two-step flow mirroring the web frontend's ForgotPasswordPage.tsx exactly. */
class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = (application as BraceletConnecteApplication).authRepository

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onNewPasswordChange(value: String) = _uiState.update { it.copy(newPassword = value, errorMessage = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }

    fun submitEmail() {
        val email = _uiState.value.email.trim()
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            authRepository.checkEmail(email)
                .onSuccess { _uiState.update { it.copy(isSubmitting = false, step = ForgotPasswordStep.Reset) } }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = authRepository.errorMessage(throwable))
                    }
                }
        }
    }

    fun submitReset(onDone: () -> Unit) {
        val state = _uiState.value
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Les mots de passe ne correspondent pas.") }
            return
        }
        val strengthError = passwordStrengthError(state.newPassword)
        if (strengthError != null) {
            _uiState.update { it.copy(errorMessage = strengthError) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            authRepository.resetPassword(state.email.trim(), state.newPassword, state.confirmPassword)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            successMessage = "Mot de passe mis à jour. Redirection vers la connexion…",
                        )
                    }
                    delay(1200)
                    onDone()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = authRepository.errorMessage(throwable))
                    }
                }
        }
    }

    private fun passwordStrengthError(password: String): String? {
        if (password.length < 8) return "Le mot de passe doit contenir au moins 8 caractères."
        if (password.none { it in 'A'..'Z' }) return "Le mot de passe doit contenir au moins une majuscule."
        val hasSpecialChar = password.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' }
        if (!hasSpecialChar) return "Le mot de passe doit contenir au moins un caractère spécial."
        return null
    }
}
