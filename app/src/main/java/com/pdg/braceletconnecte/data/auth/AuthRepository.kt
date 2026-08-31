package com.pdg.braceletconnecte.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pdg.braceletconnecte.data.api.ApiClientProvider
import com.pdg.braceletconnecte.data.api.AuthTokenHolder
import com.pdg.braceletconnecte.data.api.dto.CheckEmailRequestDto
import com.pdg.braceletconnecte.data.api.dto.LoginRequestDto
import com.pdg.braceletconnecte.data.api.dto.RegisterRequestDto
import com.pdg.braceletconnecte.data.api.dto.ResetPasswordRequestDto
import com.pdg.braceletconnecte.data.api.dto.UserDto
import com.pdg.braceletconnecte.domain.DEFAULT_WEBAPP_BASE_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.HttpException

private val Context.authDataStore by preferencesDataStore(name = "auth")

/**
 * Session state persisted via DataStore, mirroring the web frontend's localStorage keys
 * (pdg.token / pdg.user / pdg.apiBaseUrl) so the two clients behave the same way.
 */
class AuthRepository(
    private val context: Context,
    private val apiClientProvider: ApiClientProvider,
    private val tokenHolder: AuthTokenHolder,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _baseUrl = MutableStateFlow(DEFAULT_WEBAPP_BASE_URL)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    init {
        scope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        val prefs = context.authDataStore.data.first()
        val storedBaseUrl = prefs[BASE_URL_KEY] ?: DEFAULT_WEBAPP_BASE_URL
        _baseUrl.value = storedBaseUrl

        val storedToken = prefs[TOKEN_KEY]
        val storedUserJson = prefs[USER_KEY]
        if (storedToken.isNullOrBlank() || storedUserJson.isNullOrBlank()) {
            _authState.value = AuthState.LoggedOut
            return
        }

        tokenHolder.token = storedToken
        val cachedUser = runCatching { json.decodeFromString(UserDto.serializer(), storedUserJson) }.getOrNull()
        if (cachedUser != null) {
            _authState.value = AuthState.LoggedIn(cachedUser, storedToken)
        }

        // Revalidate in the background. A confirmed 401 clears the session; a network hiccup
        // should not force a re-login if we already have a cached user to show.
        runCatching { apiClientProvider.getService(storedBaseUrl).me() }
            .onSuccess { response -> persistSession(storedToken, response.user, storedBaseUrl) }
            .onFailure { throwable ->
                if (throwable is HttpException && throwable.code() == 401) {
                    clearSession()
                } else if (cachedUser == null) {
                    _authState.value = AuthState.LoggedOut
                }
            }
    }

    suspend fun setBaseUrl(newBaseUrl: String) {
        val trimmed = newBaseUrl.trim()
        if (trimmed.isBlank()) return
        _baseUrl.value = trimmed
        context.authDataStore.edit { it[BASE_URL_KEY] = trimmed }
    }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val service = apiClientProvider.getService(_baseUrl.value)
        val response = service.login(LoginRequestDto(email = email, password = password))
        persistSession(response.token, response.user, _baseUrl.value)
    }

    suspend fun register(
        email: String,
        username: String,
        password: String,
        firstName: String?,
        lastName: String?,
    ): Result<Unit> = runCatching {
        val service = apiClientProvider.getService(_baseUrl.value)
        val response = service.register(
            RegisterRequestDto(
                email = email,
                username = username,
                password = password,
                firstName = firstName?.takeIf { it.isNotBlank() },
                lastName = lastName?.takeIf { it.isNotBlank() },
            ),
        )
        persistSession(response.user.token, response.user.toUser(), _baseUrl.value)
    }

    suspend fun logout() {
        runCatching { apiClientProvider.getService(_baseUrl.value).logout() }
        clearSession()
    }

    suspend fun checkEmail(email: String): Result<Unit> = runCatching {
        apiClientProvider.getService(_baseUrl.value).checkEmail(CheckEmailRequestDto(email = email))
        Unit
    }

    suspend fun resetPassword(email: String, password: String, passwordConfirm: String): Result<Unit> = runCatching {
        apiClientProvider.getService(_baseUrl.value).resetPassword(
            ResetPasswordRequestDto(email = email, password = password, passwordConfirm = passwordConfirm),
        )
        Unit
    }

    /** Called by repositories when a request comes back 401 outside of the auth flows above. */
    fun reportUnauthorized() {
        scope.launch { clearSession() }
    }

    fun errorMessage(throwable: Throwable): String {
        return (throwable as? HttpException)?.let(apiClientProvider::parseErrorDetail)
            ?: throwable.message
            ?: "Erreur inconnue"
    }

    private suspend fun persistSession(token: String, user: UserDto, baseUrl: String) {
        tokenHolder.token = token
        context.authDataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USER_KEY] = json.encodeToString(UserDto.serializer(), user)
            prefs[BASE_URL_KEY] = baseUrl
        }
        _authState.value = AuthState.LoggedIn(user, token)
    }

    private suspend fun clearSession() {
        tokenHolder.token = null
        context.authDataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
            prefs.remove(USER_KEY)
        }
        _authState.value = AuthState.LoggedOut
    }

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("pdg.token")
        private val USER_KEY = stringPreferencesKey("pdg.user")
        private val BASE_URL_KEY = stringPreferencesKey("pdg.apiBaseUrl")
    }
}
