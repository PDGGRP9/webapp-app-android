package com.pdg.braceletconnecte.presentation.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdg.braceletconnecte.BraceletConnecteApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val baseUrl: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = (application as BraceletConnecteApplication).authRepository

    private val _uiState = MutableStateFlow(LoginUiState(baseUrl = authRepository.baseUrl.value))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onBaseUrlChange(value: String) = _uiState.update { it.copy(baseUrl = value, errorMessage = null) }

    /** "Ignorer": keep any backend URL typed, then enter guest mode (Direct tab only). */
    fun continueAsGuest(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.setBaseUrl(_uiState.value.baseUrl)
            authRepository.continueAsGuest()
            onDone()
        }
    }

    fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email et mot de passe requis.") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            authRepository.setBaseUrl(state.baseUrl)
            authRepository.login(state.email.trim(), state.password)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                    onSuccess()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = authRepository.errorMessage(throwable))
                    }
                }
        }
    }
}
