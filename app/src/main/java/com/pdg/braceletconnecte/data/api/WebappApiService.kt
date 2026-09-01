package com.pdg.braceletconnecte.data.api

import com.pdg.braceletconnecte.data.api.dto.CheckEmailRequestDto
import com.pdg.braceletconnecte.data.api.dto.DatasResponseDto
import com.pdg.braceletconnecte.data.api.dto.LoginRequestDto
import com.pdg.braceletconnecte.data.api.dto.LoginResponseDto
import com.pdg.braceletconnecte.data.api.dto.LogoutResponseDto
import com.pdg.braceletconnecte.data.api.dto.MeResponseDto
import com.pdg.braceletconnecte.data.api.dto.PostMeasurementRequestDto
import com.pdg.braceletconnecte.data.api.dto.PostMeasurementResponseDto
import com.pdg.braceletconnecte.data.api.dto.RegisterRequestDto
import com.pdg.braceletconnecte.data.api.dto.RegisterResponseDto
import com.pdg.braceletconnecte.data.api.dto.ResetPasswordRequestDto
import com.pdg.braceletconnecte.data.api.dto.StatisticsResponseDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface WebappApiService {

    @POST("api/register")
    suspend fun register(@Body body: RegisterRequestDto): RegisterResponseDto

    @POST("api/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    @POST("api/logout")
    suspend fun logout(): LogoutResponseDto

    @GET("api/me")
    suspend fun me(): MeResponseDto

    // No pairing step: POST /api/datas attaches the measurement to whoever the
    // Bearer token belongs to (added automatically by AuthInterceptor).
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

    @POST("api/password/check-email")
    suspend fun checkEmail(@Body body: CheckEmailRequestDto): Response<Unit>

    @POST("api/password/reset")
    suspend fun resetPassword(@Body body: ResetPasswordRequestDto): Response<Unit>

    @DELETE("api/me/data")
    suspend fun deleteAllData(): Response<Unit>

    @Streaming
    @GET("api/me/data/export")
    suspend fun exportData(@Query("format") format: String): Response<ResponseBody>
}
