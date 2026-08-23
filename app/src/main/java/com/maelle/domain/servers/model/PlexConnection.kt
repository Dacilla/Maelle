package com.maelle.domain.servers.model

import kotlinx.serialization.Serializable

@Serializable
data class PlexConnection(
    val protocol: String,
    val address: String,
    val port: Int,
    val uri: String,
    val local: Boolean,
)
