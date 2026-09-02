package com.pdg.braceletconnecte.data.auth

import com.pdg.braceletconnecte.data.api.dto.UserDto

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState

    /** "Ignorer" on the login screen: the live BLE tab (Direct) is usable without an
     *  account. Historique/Compte still require a real session. */
    data object Guest : AuthState

    data class LoggedIn(val user: UserDto, val token: String) : AuthState
}
