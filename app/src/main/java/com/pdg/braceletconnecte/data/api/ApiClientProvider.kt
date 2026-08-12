package com.pdg.braceletconnecte.data.api

import com.pdg.braceletconnecte.BuildConfig
import com.pdg.braceletconnecte.data.api.dto.ErrorResponseDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.create
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Builds and caches one Retrofit/OkHttp client per backend base URL, since the base URL is
 * user-editable (a physical device must target the developer machine's LAN IP, not 10.0.2.2).
 */
class ApiClientProvider(
    private val tokenHolder: AuthTokenHolder,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val servicesByBaseUrl = ConcurrentHashMap<String, WebappApiService>()

    fun getService(baseUrl: String): WebappApiService {
        val normalized = normalizeBaseUrl(baseUrl)
        return servicesByBaseUrl.getOrPut(normalized) { buildService(normalized) }
    }

    private fun buildService(baseUrl: String): WebappApiService {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenHolder))

        if (BuildConfig.DEBUG) {
            clientBuilder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create()
    }

    fun parseErrorDetail(exception: HttpException): String? {
        val body = exception.response()?.errorBody()?.string() ?: return null
        return runCatching { json.decodeFromString(ErrorResponseDto.serializer(), body) }
            .getOrNull()
            ?.detail
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return "$trimmed/"
    }
}
