package com.pdg.braceletconnecte.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatisticsDto(
    @SerialName("measurements_count") val measurementsCount: Int = 0,
    @SerialName("avg_heart_rate_bpm") val avgHeartRateBpm: Double? = null,
    @SerialName("min_heart_rate_bpm") val minHeartRateBpm: Int? = null,
    @SerialName("max_heart_rate_bpm") val maxHeartRateBpm: Int? = null,
    @SerialName("avg_spo2_percent") val avgSpo2Percent: Double? = null,
    @SerialName("max_step_count") val maxStepCount: Long = 0,
    @SerialName("last_measurement_at") val lastMeasurementAt: String? = null,
)

@Serializable
data class StatisticsResponseDto(
    @SerialName("user_id") val userId: Long,
    val statistics: StatisticsDto,
)
