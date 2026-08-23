package com.pdg.braceletconnecte.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CheckEmailRequestDto(val email: String)

@Serializable
data class ResetPasswordRequestDto(
    val email: String,
    val password: String,
    @SerialName("password_confirm") val passwordConfirm: String,
)
