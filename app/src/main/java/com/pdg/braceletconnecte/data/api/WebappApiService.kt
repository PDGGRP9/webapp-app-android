package com.pdg.braceletconnecte.data.api

import com.pdg.braceletconnecte.data.api.dto.DatasResponseDto
import com.pdg.braceletconnecte.data.api.dto.ListBraceletsResponseDto
import com.pdg.braceletconnecte.data.api.dto.LoginRequestDto
import com.pdg.braceletconnecte.data.api.dto.LoginResponseDto
import com.pdg.braceletconnecte.data.api.dto.LogoutResponseDto
import com.pdg.braceletconnecte.data.api.dto.MeResponseDto
import com.pdg.braceletconnecte.data.api.dto.PairBraceletRequestDto
import com.pdg.braceletconnecte.data.api.dto.PairBraceletResponseDto
import com.pdg.braceletconnecte.data.api.dto.PostMeasurementRequestDto
import com.pdg.braceletconnecte.data.api.dto.PostMeasurementResponseDto
import com.pdg.braceletconnecte.data.api.dto.RegisterRequestDto
import com.pdg.braceletconnecte.data.api.dto.RegisterResponseDto
import com.pdg.braceletconnecte.data.api.dto.StatisticsResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface WebappApiService {

    @POST("api/register")
    suspend fun register(@Body body: RegisterRequestDto): RegisterResponseDto

    @POST("api/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    @POST("api/logout")
    suspend fun logout(): LogoutResponseDto

    @GET("api/me")
    suspend fun me(): MeResponseDto

    @POST("api/bracelets/pair")
    suspend fun pairBracelet(@Body body: PairBraceletRequestDto): PairBraceletResponseDto

    @GET("api/bracelets/{userId}")
    suspend fun listBracelets(@Path("userId") userId: Long): ListBraceletsResponseDto

    @POST("api/datas")
    suspend fun postMeasurement(@Body body: PostMeasurementRequestDto): PostMeasurementResponseDto

    @GET("api/datas/{userId}")
    suspend fun getDatas(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
    ): DatasResponseDto

    @GET("api/statistics/{userId}")
    suspend fun getStatistics(@Path("userId") userId: Long): StatisticsResponseDto
}
