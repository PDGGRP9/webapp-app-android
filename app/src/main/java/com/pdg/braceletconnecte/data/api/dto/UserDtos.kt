package com.pdg.braceletconnecte.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val username: String,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_staff") val isStaff: Boolean = false,
    @SerialName("is_superuser") val isSuperuser: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Same shape as [UserDto] but with the bearer token embedded, as returned by POST /api/register. */
@Serializable
data class UserWithTokenDto(
    val id: Long,
    val email: String,
    val username: String,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_staff") val isStaff: Boolean = false,
    @SerialName("is_superuser") val isSuperuser: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val token: String,
) {
    fun toUser(): UserDto = UserDto(
        id = id,
        email = email,
        username = username,
        firstName = firstName,
        lastName = lastName,
        isActive = isActive,
        isStaff = isStaff,
        isSuperuser = isSuperuser,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
