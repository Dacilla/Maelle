package com.maelle.data.remote.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexPinResponse(
    @SerialName("id")
    val id: Int,
    @SerialName("code")
    val code: String,
)

@Serializable
data class PlexPinStatusResponse(
    @SerialName("authToken")
    val authToken: String? = null,
)
