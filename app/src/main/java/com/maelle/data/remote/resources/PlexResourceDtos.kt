package com.maelle.data.remote.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PlexResourceDto(
    @SerialName("name")
    val name: String,
    @SerialName("provides")
    val provides: String? = null,
    @SerialName("clientIdentifier")
    val clientIdentifier: String,
    @SerialName("accessToken")
    val accessToken: String? = null,
    @SerialName("owned")
    val owned: JsonElement? = null,
    @SerialName("connections")
    val connections: List<PlexResourceConnectionDto> = emptyList(),
)

@Serializable
data class PlexResourceConnectionDto(
    @SerialName("protocol")
    val protocol: String,
    @SerialName("address")
    val address: String,
    @SerialName("port")
    val port: Int,
    @SerialName("uri")
    val uri: String,
    @SerialName("local")
    val local: Boolean? = null,
)
