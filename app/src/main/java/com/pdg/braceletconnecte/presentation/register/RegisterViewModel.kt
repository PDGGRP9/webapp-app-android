package com.pdg.braceletconnecte.presentation.register

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdg.braceletconnecte.BraceletConnecteApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val email: String = "",
    val username: String = "",
    val password: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val baseUrl: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

class RegisterViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = (application as BraceletConnecteApplication).authRepository

    private val _uiState = MutableStateFlow(RegisterUiState(baseUrl = authRepository.baseUrl.value))
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onFirstNameChange(value: String) = _uiState.update { it.copy(firstName = value) }
    fun onLastNameChange(value: String) = _uiState.update { it.copy(lastName = value) }
    fun onBaseUrlChange(value: String) = _uiState.update { it.copy(baseUrl = value, errorMessage = null) }

    fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email, nom d'utilisateur et mot de passe requis.") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            authRepository.setBaseUrl(state.baseUrl)
            authRepository.register(
                email = state.email.trim(),
                username = state.username.trim(),
                password = state.password,
                firstName = state.firstName,
                lastName = state.lastName,
            )
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
