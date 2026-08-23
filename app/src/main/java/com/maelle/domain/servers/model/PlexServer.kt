package com.maelle.domain.servers.model

data class PlexServer(
    val serverId: String,
    val name: String,
    val accessToken: String,
    val owned: Boolean,
    val connections: List<PlexConnection>,
)
