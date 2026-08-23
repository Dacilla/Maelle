package com.maelle.feature.servers

import com.maelle.domain.servers.model.PlexConnection
import com.maelle.domain.servers.model.PlexServer

data class ServerSelectionUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val servers: List<ServerCardUiModel> = emptyList(),
)

data class ServerCardUiModel(
    val server: PlexServer,
    val bestConnectionUri: String?,
    val connectionStatuses: Map<String, Int>,
)

fun ServerCardUiModel.bestLatency(): Int? = bestConnectionUri?.let(connectionStatuses::get)

fun ServerCardUiModel.bestConnection(): PlexConnection? {
    return server.connections.firstOrNull { it.uri == bestConnectionUri }
}
