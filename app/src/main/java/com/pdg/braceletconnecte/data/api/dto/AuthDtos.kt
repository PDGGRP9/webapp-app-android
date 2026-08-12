package com.pdg.braceletconnecte.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RegisterRequestDto(
    val email: String,
    val username: String,
    val password: String,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
)

@Serializable
data class RegisterResponseDto(
    val user: UserWithTokenDto,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponseDto(
    val token: String,
    val user: UserDto,
)

@Serializable
data class LogoutResponseDto(
    val detail: String? = null,
    val token: JsonElement? = null,
)

@Serializable
data class MeResponseDto(
    val user: UserDto,
)

/** Parsed from a non-2xx error body, e.g. {"detail": "Invalid credentials"} or {"detail": "...", "missing": [...]}. */
@Serializable
data class ErrorResponseDto(
    val detail: String? = null,
    val missing: List<String>? = null,
)
