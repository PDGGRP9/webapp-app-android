package com.pdg.braceletconnecte.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.time.OffsetDateTime

@Serializable
data class PostMeasurementRequestDto(
    @SerialName("device_uid") val deviceUid: String,
    @SerialName("serial_number") val serialNumber: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("mac_address") val macAddress: String? = null,
    @SerialName("captured_at") val capturedAt: String? = null,
    @SerialName("heart_rate_bpm") val heartRateBpm: Int? = null,
    @SerialName("spo2_percent") val spo2Percent: Double? = null,
    @SerialName("step_count") val stepCount: Long = 0,
    @SerialName("motion_level") val motionLevel: Double? = null,
    @SerialName("signal_quality") val signalQuality: Int? = null,
    @SerialName("source_topic") val sourceTopic: String? = null,
)

@Serializable
data class PostMeasurementResponseDto(
    val measurement: MeasurementDto,
)

@Serializable
data class MeasurementBraceletSummaryDto(
    @SerialName("device_uid") val deviceUid: String? = null,
    @SerialName("serial_number") val serialNumber: String? = null,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class MeasurementDto(
    val id: Long,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("heart_rate_bpm") val heartRateBpm: Int? = null,
    @SerialName("spo2_percent") val spo2Percent: Double? = null,
    @SerialName("step_count") val stepCount: Long = 0,
    @SerialName("motion_level") val motionLevel: Double? = null,
    @SerialName("signal_quality") val signalQuality: Int? = null,
    @SerialName("raw_payload") val rawPayload: JsonElement? = null,
    @SerialName("source_topic") val sourceTopic: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val bracelet: MeasurementBraceletSummaryDto? = null,
)

@Serializable
data class DatasResponseDto(
    @SerialName("user_id") val userId: Long,
    val count: Int,
    val datas: List<MeasurementDto>,
)

/** The backend returns ISO-8601 timestamps (Django's DjangoJSONEncoder), sometimes with a non-Z offset. */
fun MeasurementDto.capturedAtInstant(): Instant? {
    return runCatching { Instant.parse(capturedAt) }
        .recoverCatching { OffsetDateTime.parse(capturedAt).toInstant() }
        .getOrNull()
}
