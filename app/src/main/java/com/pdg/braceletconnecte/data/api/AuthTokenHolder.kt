package com.pdg.braceletconnecte.data.api

/** In-memory mirror of the currently active bearer token, read synchronously by [AuthInterceptor]. */
class AuthTokenHolder {
    @Volatile
    var token: String? = null
}
