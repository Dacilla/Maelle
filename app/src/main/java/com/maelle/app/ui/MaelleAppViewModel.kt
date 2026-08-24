package com.maelle.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maelle.core.logging.RedactingLogger
import com.maelle.data.repository.AppSessionRepository
import com.maelle.data.repository.DirectDownloadScheduler
import com.maelle.data.repository.DownloadJobRepository
import com.maelle.data.repository.PlexServerRepository
import com.maelle.data.repository.QueueDownloadScheduler
import com.maelle.data.repository.SessionRecoveryRepository
import com.maelle.domain.downloads.model.DownloadStrategy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MaelleAppViewModel @Inject constructor(
    private val appSessionRepository: AppSessionRepository,
    private val downloadJobRepository: DownloadJobRepository,
    private val plexServerRepository: PlexServerRepository,
    private val directDownloadScheduler: DirectDownloadScheduler,
    private val queueDownloadScheduler: QueueDownloadScheduler,
    private val sessionRecoveryRepository: SessionRecoveryRepository,
    private val logger: RedactingLogger,
) : ViewModel() {

    private val serverPickerRequested = MutableStateFlow(false)
    private val settingsRequested = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val resumable = downloadJobRepository.reconcilePersistedJobs()
            resumable.forEach { jobId ->
                val job = downloadJobRepository.getJob(jobId) ?: return@forEach
                logger.i(
                    component = "Downloads",
                    message = "Resuming interrupted ${job.strategy.name.lowercase()} job $jobId",
                )
                when (job.strategy) {
                    DownloadStrategy.Direct -> directDownloadScheduler.enqueue(jobId)
                    DownloadStrategy.Queue -> queueDownloadScheduler.enqueue(jobId)
                }
            }
            if (resumable.isNotEmpty()) {
                logger.i(
                    component = "Downloads",
                    message = "Re-enqueued ${resumable.size} interrupted download(s) on startup",
                )
            }
        }
        validatePersistedSession()
    }

    private fun validatePersistedSession() {
        viewModelScope.launch {
            val session = appSessionRepository.observeSession().first()
            val token = session.plexAuthToken ?: return@launch
            logger.i(component = "Session", message = "Validating persisted Plex token on startup")
            when (val outcome = sessionRecoveryRepository.recoverSession()) {
                SessionRecoveryRepository.Outcome.SessionInvalid -> {
                    logger.i(
                        component = "Session",
                        message = "Persisted token was rejected; routing back to sign-in",
                    )
                }

                is SessionRecoveryRepository.Outcome.Recovered -> {
                    logger.i(
                        component = "Session",
                        message = "Session valid; refreshed ${outcome.servers.size} server(s)",
                    )
                }

                SessionRecoveryRepository.Outcome.Inconclusive -> Unit
            }
        }
    }

    val uiState: StateFlow<MaelleAppUiState> = combine(
        appSessionRepository.observeSession(),
        plexServerRepository.observeSelectedServer(
            appSessionRepository.observeSession().map { it.selectedServerId },
        ),
        serverPickerRequested,
        settingsRequested,
    ) { session, selectedServer, pickerRequested, settingsOpen ->
        val hasSelection = selectedServer != null && !session.selectedConnectionUri.isNullOrBlank()
        when {
            session.plexAuthToken.isNullOrBlank() -> {
                MaelleAppUiState(destination = MaelleDestination.Auth)
            }

            pickerRequested -> {
                MaelleAppUiState(
                    destination = MaelleDestination.Servers,
                    isServerPickerCancelable = hasSelection,
                )
            }

            settingsOpen && hasSelection -> {
                MaelleAppUiState(
                    destination = MaelleDestination.Settings,
                    selectedServerName = session.selectedServerName ?: selectedServer.name,
                    selectedConnectionUri = session.selectedConnectionUri,
                )
            }

            !hasSelection -> {
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

    init {
        viewModelScope.launch {
            appSessionRepository.observeSession()
                .map { it.selectedConnectionUri }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    serverPickerRequested.value = false
                    logger.i(component = "Session", message = "Server selection changed; closing server picker")
                }
        }
    }

    fun showServerPicker() {
        settingsRequested.value = false
        serverPickerRequested.value = true
    }

    fun dismissServerPicker() {
        serverPickerRequested.value = false
    }

    fun showSettings() {
        settingsRequested.value = true
    }

    fun dismissSettings() {
        settingsRequested.value = false
    }

    fun logout() {
        viewModelScope.launch {
            logger.i(component = "Session", message = "Logout requested; clearing persisted session")
            appSessionRepository.clearSession()
            logger.i(component = "Session", message = "Persisted session cleared")
        }
    }
}
