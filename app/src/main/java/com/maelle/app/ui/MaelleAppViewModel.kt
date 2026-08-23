package com.maelle.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maelle.core.logging.RedactingLogger
import com.maelle.data.repository.AppSessionRepository
import com.maelle.data.repository.DownloadJobRepository
import com.maelle.data.repository.PlexServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MaelleAppViewModel @Inject constructor(
    private val appSessionRepository: AppSessionRepository,
    private val downloadJobRepository: DownloadJobRepository,
    private val plexServerRepository: PlexServerRepository,
    private val logger: RedactingLogger,
) : ViewModel() {

    init {
        viewModelScope.launch {
            downloadJobRepository.reconcilePersistedJobs()
            logger.i(component = "Downloads", message = "Persisted download jobs reconciled on startup")
        }
    }

    val uiState: StateFlow<MaelleAppUiState> = combine(
        appSessionRepository.observeSession(),
        plexServerRepository.observeSelectedServer(
            appSessionRepository.observeSession().map { it.selectedServerId },
        ),
    ) { session, selectedServer ->
        when {
            session.plexAuthToken.isNullOrBlank() -> {
                MaelleAppUiState(destination = MaelleDestination.Auth)
            }

            selectedServer == null || session.selectedConnectionUri.isNullOrBlank() -> {
                MaelleAppUiState(destination = MaelleDestination.Servers)
            }

            else -> {
                MaelleAppUiState(
                    destination = MaelleDestination.Home,
                    selectedServerName = session.selectedServerName ?: selectedServer.name,
                    selectedConnectionUri = session.selectedConnectionUri,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MaelleAppUiState(destination = MaelleDestination.Auth),
    )

    fun logout() {
        viewModelScope.launch {
            logger.i(component = "Session", message = "Logout requested; clearing persisted session")
            appSessionRepository.clearSession()
            logger.i(component = "Session", message = "Persisted session cleared")
        }
    }
}
