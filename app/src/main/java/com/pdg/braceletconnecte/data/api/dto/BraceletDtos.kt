package com.pdg.braceletconnecte.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BraceletDto(
    val id: Long,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("device_uid") val deviceUid: String,
    @SerialName("serial_number") val serialNumber: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("firmware_version") val firmwareVersion: String? = null,
    @SerialName("mac_address") val macAddress: String? = null,
    val status: String,
    @SerialName("paired_at") val pairedAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PairBraceletRequestDto(
    @SerialName("user_id") val userId: Long,
    @SerialName("device_uid") val deviceUid: String,
    @SerialName("serial_number") val serialNumber: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("firmware_version") val firmwareVersion: String? = null,
    @SerialName("mac_address") val macAddress: String? = null,
    val status: String? = null,
)

@Serializable
data class PairBraceletResponseDto(
    val bracelet: BraceletDto,
)

@Serializable
data class ListBraceletsResponseDto(
    val bracelets: List<BraceletDto>,
)
