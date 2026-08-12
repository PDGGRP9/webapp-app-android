package com.pdg.braceletconnecte.data.auth

import com.pdg.braceletconnecte.data.api.dto.UserDto

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val user: UserDto, val token: String) : AuthState
}
