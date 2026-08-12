package com.pdg.braceletconnecte.data.bracelet

import com.pdg.braceletconnecte.data.api.ApiClientProvider
import com.pdg.braceletconnecte.data.api.dto.BraceletDto
import com.pdg.braceletconnecte.data.api.dto.PairBraceletRequestDto
import com.pdg.braceletconnecte.data.auth.AuthRepository
import retrofit2.HttpException

class BraceletRepository(
    private val apiClientProvider: ApiClientProvider,
    private val authRepository: AuthRepository,
) {
    suspend fun listBracelets(userId: Long): Result<List<BraceletDto>> = runCatching {
        service().listBracelets(userId).bracelets
    }.onFailure(::handleUnauthorized)

    suspend fun pair(
        userId: Long,
        deviceUid: String,
        serialNumber: String,
        displayName: String?,
        macAddress: String?,
    ): Result<BraceletDto> = runCatching {
        service().pairBracelet(
            PairBraceletRequestDto(
                userId = userId,
                deviceUid = deviceUid,
                serialNumber = serialNumber,
                displayName = displayName,
                macAddress = macAddress,
            ),
        ).bracelet
    }.onFailure(::handleUnauthorized)

    private fun service() = apiClientProvider.getService(authRepository.baseUrl.value)

    private fun handleUnauthorized(throwable: Throwable) {
        if (throwable is HttpException && throwable.code() == 401) {
            authRepository.reportUnauthorized()
        }
    }
}
