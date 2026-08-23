package com.maelle.feature.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maelle.core.logging.RedactingLogger
import com.maelle.data.repository.AppSessionRepository
import com.maelle.data.repository.PlexServerRepository
import com.maelle.domain.servers.model.PlexServer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ServerSelectionViewModel @Inject constructor(
    private val appSessionRepository: AppSessionRepository,
    private val plexServerRepository: PlexServerRepository,
    private val logger: RedactingLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerSelectionUiState())
    val uiState: StateFlow<ServerSelectionUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val token = appSessionRepository.observeSession().first().plexAuthToken
            if (token.isNullOrBlank()) {
                _uiState.value = ServerSelectionUiState(
                    isLoading = false,
                    errorMessage = "Missing Plex auth token. Log in again.",
                )
                return@launch
            }

            _uiState.value = ServerSelectionUiState(isLoading = true)

            runCatching {
                val servers = plexServerRepository.refreshServers(token)
                buildServerCards(servers)
            }.onSuccess { cards ->
                _uiState.value = ServerSelectionUiState(
                    isLoading = false,
                    servers = cards,
                )
            }.onFailure { throwable ->
                logger.e(component = "Servers", message = "Failed to refresh Plex resources", throwable = throwable)
                _uiState.value = ServerSelectionUiState(
                    isLoading = false,
                    errorMessage = "Failed to fetch Plex servers. Retry after checking network access.",
                )
            }
        }
    }

    fun selectBestConnection(card: ServerCardUiModel) {
        val connection = card.bestConnection() ?: return
        selectConnection(card.server, connection.uri)
    }

    fun selectConnection(server: PlexServer, connectionUri: String) {
        viewModelScope.launch {
            runCatching {
                val latency = _uiState.value.servers
                    .firstOrNull { it.server.serverId == server.serverId }
                    ?.connectionStatuses
                    ?.get(connectionUri)
                    ?.takeIf { it >= 0 }

                plexServerRepository.selectConnection(
                    serverId = server.serverId,
                    connectionUri = connectionUri,
                    lastSuccessfulContactEpochMs = latency?.let { System.currentTimeMillis() },
                )
                appSessionRepository.selectServer(
                    serverId = server.serverId,
                    serverName = server.name,
                    connectionUri = connectionUri,
                )
            }.onFailure { throwable ->
                logger.e(component = "Servers", message = "Failed to persist selected Plex server", throwable = throwable)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save the selected server connection.",
                )
            }
        }
    }

    private suspend fun buildServerCards(servers: List<PlexServer>): List<ServerCardUiModel> {
        return kotlinx.coroutines.coroutineScope {
            servers.map { server ->
                async {
                    val latencies = plexServerRepository.measureConnections(server)
                    val bestConnection = plexServerRepository.chooseBestConnection(server, latencies)
                    ServerCardUiModel(
                        server = server,
                        bestConnectionUri = bestConnection?.uri,
                        connectionStatuses = latencies,
                    )
                }
            }.awaitAll().sortedBy { it.server.name.lowercase() }
        }
    }
}
