package com.maelle.data.repository

import com.maelle.data.remote.auth.PlexAuthService
import com.maelle.domain.auth.model.PlexPinChallenge
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Singleton
class PlexAuthRepository @Inject constructor(
    private val plexAuthService: PlexAuthService,
    private val json: Json,
) {

    suspend fun createPinChallenge(): PlexPinChallenge {
        val response = plexAuthService.createPin()
        return PlexPinChallenge(
            id = response.id,
            code = response.code,
            authUrl = buildAuthUrl(response.code),
        )
    }

    suspend fun getAuthToken(pinId: Int): String? {
        val response = plexAuthService.getPinStatus(pinId)
        if (!response.isSuccessful) {
            throw IllegalStateException("PIN status request failed with HTTP ${response.code()}")
        }

        val body = response.body()?.string().orEmpty()
        if (body.isBlank()) {
            return null
        }

        val jsonObject = json.parseToJsonElement(body).jsonObject
        val authTokenElement = jsonObject["authToken"] ?: return null
        if (authTokenElement is JsonNull) {
            return null
        }
        return authTokenElement.jsonPrimitive.contentOrNull
    }

    suspend fun isAuthTokenValid(token: String): Boolean {
        val response = plexAuthService.getUser(userToken = token)
        return response.isSuccessful
    }

    private fun buildAuthUrl(pinCode: String): String {
        val encodedCode = java.net.URLEncoder.encode(pinCode, Charsets.UTF_8.name())
        return "https://plex.tv/link/?pin=$encodedCode"
    }
}
