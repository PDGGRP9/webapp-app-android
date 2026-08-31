package com.pdg.braceletconnecte.data.measurements

import com.pdg.braceletconnecte.data.api.ApiClientProvider
import com.pdg.braceletconnecte.data.api.dto.DatasResponseDto
import com.pdg.braceletconnecte.data.api.dto.PostMeasurementRequestDto
import com.pdg.braceletconnecte.data.api.dto.StatisticsDto
import com.pdg.braceletconnecte.data.auth.AuthRepository
import com.pdg.braceletconnecte.domain.BiometricMeasurement
import okhttp3.ResponseBody
import retrofit2.HttpException

/** Matches the web frontend's MeasurementsContext poll cadence. */
const val MEASUREMENTS_POLL_INTERVAL_MS = 15_000L

class MeasurementsRepository(
    private val apiClientProvider: ApiClientProvider,
    private val authRepository: AuthRepository,
) {
    suspend fun fetchDatas(userId: Long, limit: Int = 500): Result<DatasResponseDto> = runCatching {
        service().getDatas(userId = userId, limit = limit)
    }.onFailure(::handleUnauthorized)

    suspend fun fetchStatistics(userId: Long): Result<StatisticsDto> = runCatching {
        service().getStatistics(userId).statistics
    }.onFailure(::handleUnauthorized)

    /** Retransmits one live BLE measurement straight to the backend (replaces the old MQTT publish step). */
    suspend fun postMeasurement(measurement: BiometricMeasurement): Result<Unit> = runCatching {
        service().postMeasurement(
            PostMeasurementRequestDto(
                deviceUid = measurement.deviceUid,
                serialNumber = measurement.serialNumber,
                displayName = measurement.deviceName,
                macAddress = measurement.macAddress,
                capturedAt = measurement.capturedAt.toString(),
                heartRateBpm = measurement.heartRateBpm,
                spo2Percent = measurement.spo2Percent,
                stepCount = measurement.stepCount,
                motionLevel = measurement.motionLevel,
                signalQuality = measurement.signalQuality,
                sourceTopic = "android-ble",
            ),
        )
        Unit
    }.onFailure(::handleUnauthorized)

    suspend fun exportData(format: String): Result<ResponseBody> = runCatching {
        service().exportData(format).body() ?: error("Réponse d'export vide")
    }.onFailure(::handleUnauthorized)

    suspend fun deleteAllData(): Result<Unit> = runCatching {
        service().deleteAllData()
        Unit
    }.onFailure(::handleUnauthorized)

    private fun service() = apiClientProvider.getService(authRepository.baseUrl.value)

    private fun handleUnauthorized(throwable: Throwable) {
        if (throwable is HttpException && throwable.code() == 401) {
            authRepository.reportUnauthorized()
        }
    }
}
