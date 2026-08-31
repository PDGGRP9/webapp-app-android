package com.pdg.braceletconnecte.presentation.account

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdg.braceletconnecte.BraceletConnecteApplication
import com.pdg.braceletconnecte.data.api.dto.BraceletDto
import com.pdg.braceletconnecte.data.api.dto.UserDto
import com.pdg.braceletconnecte.data.auth.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AccountUiState(
    val user: UserDto? = null,
    val bracelets: List<BraceletDto> = emptyList(),
    val isExporting: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BraceletConnecteApplication
    private val authRepository = app.authRepository
    private val braceletRepository = app.braceletRepository
    private val measurementsRepository = app.measurementsRepository

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        val user = (authRepository.authState.value as? AuthState.LoggedIn)?.user
        _uiState.update { it.copy(user = user) }
        val userId = user?.id
        if (userId != null) {
            viewModelScope.launch {
                braceletRepository.listBracelets(userId).onSuccess { bracelets ->
                    _uiState.update { it.copy(bracelets = bracelets) }
                }
            }
        }
    }

    fun exportData(uri: Uri, format: String) {
        _uiState.update { it.copy(isExporting = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            measurementsRepository.exportData(format)
                .onSuccess { body ->
                    val written = withContext(Dispatchers.IO) {
                        runCatching {
                            val out = app.contentResolver.openOutputStream(uri)
                                ?: error("Impossible d'ouvrir le fichier de destination.")
                            out.use { body.byteStream().copyTo(it) }
                        }
                    }
                    if (written.isSuccess) {
                        _uiState.update { it.copy(isExporting = false, successMessage = "Export réussi.") }
                    } else {
                        _uiState.update { it.copy(isExporting = false, errorMessage = "Échec de l'écriture du fichier.") }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isExporting = false, errorMessage = authRepository.errorMessage(throwable))
                    }
                }
        }
    }

    fun deleteAllData(onDone: () -> Unit) {
        _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
        viewModelScope.launch {
            measurementsRepository.deleteAllData()
                .onSuccess {
                    _uiState.update { it.copy(isDeleting = false, successMessage = "Toutes les données ont été supprimées.") }
                    onDone()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isDeleting = false, errorMessage = authRepository.errorMessage(throwable))
                    }
                }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }

    fun clearMessages() = _uiState.update { it.copy(errorMessage = null, successMessage = null) }
}
