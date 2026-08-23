package com.maelle.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maelle.core.logging.RedactingLogger
import com.maelle.data.repository.AppSessionRepository
import com.maelle.data.repository.DirectDownloadScheduler
import com.maelle.data.repository.DownloadJobRepository
import com.maelle.data.repository.PlexLibraryRepository
import com.maelle.data.repository.QueueDownloadScheduler
import com.maelle.data.repository.PlexServerRepository
import com.maelle.data.repository.SessionRecoveryRepository
import com.maelle.domain.downloads.model.DownloadPlan
import com.maelle.domain.downloads.model.DownloadPlanOption
import com.maelle.domain.downloads.model.DownloadStrategy
import com.maelle.domain.downloads.model.DownloadState
import com.maelle.domain.library.model.PlexLibraryItem
import com.maelle.domain.library.model.PlexLibrarySection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appSessionRepository: AppSessionRepository,
    private val plexServerRepository: PlexServerRepository,
    private val plexLibraryRepository: PlexLibraryRepository,
    private val downloadJobRepository: DownloadJobRepository,
    private val directDownloadScheduler: DirectDownloadScheduler,
    private val queueDownloadScheduler: QueueDownloadScheduler,
    private val sessionRecoveryRepository: SessionRecoveryRepository,
    private val logger: RedactingLogger,
) : ViewModel() {

    private data class ServerContext(
        val serverId: String,
        val serverName: String,
        val connectionUri: String,
        val accessToken: String,
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadJobRepository.observeJobs().collect { jobs ->
                _uiState.value = _uiState.value.copy(
                    downloadJobs = jobs.map { job ->
                        HomeDownloadJobItem(
                            jobId = job.jobId,
                            mediaKey = job.mediaKey,
                            mediaTitle = job.mediaTitle.ifBlank { job.mediaKey },
                            mediaSecondaryTitle = job.mediaSecondaryTitle,
                            strategy = job.strategy,
                            state = job.state,
                            requestedQuality = job.requestedQuality,
                            queueId = job.queueId,
                            queueItemId = job.queueItemId,
                            bytesDownloaded = job.bytesDownloaded,
                            bytesTotal = job.bytesTotal,
                            localFileName = job.localFileName,
                            localFilePath = job.localFilePath,
                            artifactBytes = job.artifactBytes,
                            errorMessage = job.errorMessage,
                        )
                    },
                )
            }
        }
        refresh()
    }

    fun showPane(pane: HomePane) {
        _uiState.value = _uiState.value.copy(activePane = pane)
    }

    fun refresh() {
        viewModelScope.launch {
            val serverContext = resolveServerContext() ?: return@launch
            val selectedSection = _uiState.value.selectedSection
            if (selectedSection == null) {
                loadSections(serverContext)
            } else if (_uiState.value.browseStack.isNotEmpty()) {
                loadBrowsePath(serverContext, _uiState.value.browseStack.last())
            } else {
                loadSectionItems(serverContext, selectedSection)
            }
        }
    }

    fun openSection(section: PlexLibrarySection) {
        viewModelScope.launch {
            val serverContext = resolveServerContext() ?: return@launch
            loadSectionItems(serverContext, section)
        }
    }

    fun closeSection() {
        _uiState.value = _uiState.value.copy(
            selectedSection = null,
            sectionItems = emptyList(),
            browseStack = emptyList(),
            sectionErrorMessage = null,
            isSectionLoading = false,
        )
    }

    fun openItem(item: PlexLibraryItem) {
        val browsePath = item.browsePath ?: return
        viewModelScope.launch {
            val serverContext = resolveServerContext() ?: return@launch
            loadBrowsePath(
                serverContext = serverContext,
                node = LibraryBrowseNode(
                    title = item.title,
                    path = browsePath,
                ),
            )
        }
    }

    fun openDownloadPlanner(item: PlexLibraryItem) {
        viewModelScope.launch {
            val serverContext = resolveServerContext() ?: return@launch
            _uiState.value = _uiState.value.copy(
                isLoadingDownloadPlan = true,
                activeDownloadPlan = null,
                downloadPlanErrorMessage = null,
                lastPlannedJobMessage = null,
            )

            runCatching {
                val detail = plexLibraryRepository.getMediaDetail(
                    connectionUri = serverContext.connectionUri,
                    serverAccessToken = serverContext.accessToken,
                    ratingKey = item.ratingKey,
                )
                DownloadPlan(
                    item = item,
                    detail = detail,
                    options = buildDownloadPlanOptions(item),
                )
            }.onSuccess { plan ->
                _uiState.value = _uiState.value.copy(
                    isLoadingDownloadPlan = false,
                    activeDownloadPlan = plan,
                    downloadPlanErrorMessage = null,
                )
            }.onFailure { throwable ->
                logger.e(
                    component = "Downloads",
                    message = "Failed to build download plan for ${item.ratingKey}",
                    throwable = throwable,
                )
                _uiState.value = _uiState.value.copy(
                    isLoadingDownloadPlan = false,
                    activeDownloadPlan = null,
                    downloadPlanErrorMessage = "Failed to load download details for ${item.title}.",
                )
            }
        }
    }

    fun dismissDownloadPlanner() {
        _uiState.value = _uiState.value.copy(
            activeDownloadPlan = null,
            isLoadingDownloadPlan = false,
            downloadPlanErrorMessage = null,
        )
    }

    fun dismissPlannedJobMessage() {
        _uiState.value = _uiState.value.copy(
            lastPlannedJobMessage = null,
        )
    }

    fun retryDownload(jobId: String) {
        viewModelScope.launch {
            val job = downloadJobRepository.getJob(jobId) ?: return@launch
            runCatching {
                downloadJobRepository.prepareForRetry(jobId)
                when (job.strategy) {
                    DownloadStrategy.Direct -> directDownloadScheduler.enqueue(jobId)
                    DownloadStrategy.Queue -> queueDownloadScheduler.enqueue(jobId)
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    activePane = HomePane.Downloads,
                    lastPlannedJobMessage = "Retried ${job.strategy.name.lowercase()} job ${jobId.take(8)}.",
                )
            }.onFailure { throwable ->
                logger.e(
                    component = "Downloads",
                    message = "Failed to retry download job $jobId",
                    throwable = throwable,
                )
                _uiState.value = _uiState.value.copy(
                    activePane = HomePane.Downloads,
                    lastPlannedJobMessage = "Failed to retry job ${jobId.take(8)}.",
                )
            }
        }
    }

    fun refreshTrackedDownload(jobId: String) {
        viewModelScope.launch {
            val job = downloadJobRepository.getJob(jobId) ?: return@launch
            if (job.strategy != DownloadStrategy.Queue) return@launch

            runCatching {
                queueDownloadScheduler.enqueue(jobId)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    activePane = HomePane.Downloads,
                    lastPlannedJobMessage = "Queued a fresh status check for job ${jobId.take(8)}.",
                )
            }.onFailure { throwable ->
                logger.e(
                    component = "Downloads",
                    message = "Failed to refresh queue download job $jobId",
                    throwable = throwable,
                )
                _uiState.value = _uiState.value.copy(
                    activePane = HomePane.Downloads,
                    lastPlannedJobMessage = "Failed to refresh job ${jobId.take(8)}.",
                )
            }
        }
    }

    fun pauseDownload(jobId: String) {
        viewModelScope.launch {
            val job = downloadJobRepository.getJob(jobId) ?: return@launch
            runCatching {
                downloadJobRepository.updateState(
                    jobId = jobId,
                    state = DownloadState.Paused,
                    errorCategory = null,
                    errorMessage = "Paused. Resume to continue where it stopped.",
                )
                when (job.strategy) {
                    DownloadStrategy.Direct -> directDownloadScheduler.cancel(jobId)
                    DownloadStrategy.Queue -> queueDownloadScheduler.cancel(jobId)
                }
            }.onSuccess {
                logger.i(component = "Downloads", message = "Paused job $jobId")
            }.onFailure { throwable ->
                logger.e(
                    component = "Downloads",
                    message = "Failed to pause job $jobId",
                    throwable = throwable,
                )
            }
        }
    }

    fun resumeDownload(jobId: String) {
        viewModelScope.launch {
            val job = downloadJobRepository.getJob(jobId) ?: return@launch
            runCatching {
                downloadJobRepository.prepareForRetry(jobId)
                when (job.strategy) {
                    DownloadStrategy.Direct -> directDownloadScheduler.enqueue(jobId)
                    DownloadStrategy.Queue -> queueDownloadScheduler.enqueue(jobId)
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    activePane = HomePane.Downloads,
                    lastPlannedJobMessage = "Resumed job ${jobId.take(8)}.",
                )
            }.onFailure { throwable ->
                logger.e(
                    component = "Downloads",
                    message = "Failed to resume job $jobId",
                    throwable = throwable,
                )
                _uiState.value = _uiState.value.copy(
                    activePane = HomePane.Downloads,
                    lastPlannedJobMessage = "Failed to resume job ${jobId.take(8)}.",
                )
            }
        }
    }

    fun createPlannedJob(strategy: DownloadStrategy) {
        viewModelScope.launch {
            val serverContext = resolveServerContext() ?: return@launch
            val plan = _uiState.value.activeDownloadPlan ?: return@launch
            val option = plan.options.firstOrNull { it.strategy == strategy } ?: return@launch

            runCatching {
                downloadJobRepository.createPlannedJob(
                    mediaKey = plan.item.ratingKey,
                    mediaTitle = plan.detail.title,
                    mediaSecondaryTitle = plan.detail.secondaryTitle,
                    serverId = serverContext.serverId,
                    strategy = strategy,
                    requestedQuality = option.requestedQuality,
                )
            }.onSuccess { job ->
                if (strategy == DownloadStrategy.Direct) {
                    directDownloadScheduler.enqueue(job.jobId)
                } else {
                    queueDownloadScheduler.enqueue(job.jobId)
                }
                _uiState.value = _uiState.value.copy(
                    activeDownloadPlan = null,
                    isLoadingDownloadPlan = false,
                    downloadPlanErrorMessage = null,
                    activePane = HomePane.Downloads,
                    lastPlannedJobMessage = when (strategy) {
                        DownloadStrategy.Direct ->
                            "Started direct download for ${plan.item.title} (${job.jobId.take(8)})."
                        DownloadStrategy.Queue ->
                            "Started queue download for ${plan.item.title} (${job.jobId.take(8)})."
                    },
                )
            }.onFailure { throwable ->
                logger.e(
                    component = "Downloads",
                    message = "Failed to create planned download job for ${plan.item.ratingKey}",
                    throwable = throwable,
                )
                _uiState.value = _uiState.value.copy(
                    downloadPlanErrorMessage = "Failed to create a download job for ${plan.item.title}.",
                )
            }
        }
    }

    fun navigateUp() {
        val currentState = _uiState.value
        if (currentState.browseStack.isEmpty()) {
            closeSection()
            return
        }

        val newStack = currentState.browseStack.dropLast(1)
        _uiState.value = currentState.copy(
            browseStack = newStack,
            sectionErrorMessage = null,
            sectionItems = emptyList(),
        )

        viewModelScope.launch {
            val serverContext = resolveServerContext() ?: return@launch
            val selectedSection = _uiState.value.selectedSection ?: return@launch
            if (newStack.isEmpty()) {
                loadSectionItems(serverContext, selectedSection)
            } else {
                loadBrowsePath(serverContext, newStack.last())
            }
        }
    }

    private suspend fun resolveServerContext(): ServerContext? {
        val session = appSessionRepository.observeSession().first()
        val serverId = session.selectedServerId
        val connectionUri = session.selectedConnectionUri

        if (serverId.isNullOrBlank() || connectionUri.isNullOrBlank()) {
            _uiState.value = HomeUiState(
                isLoading = false,
                errorMessage = "Missing selected Plex server connection.",
            )
            return null
        }

        val server = plexServerRepository.getServer(serverId)
        if (server == null) {
            _uiState.value = HomeUiState(
                isLoading = false,
                errorMessage = "Selected Plex server is no longer cached locally.",
            )
            return null
        }

        return ServerContext(
            serverId = serverId,
            serverName = session.selectedServerName ?: server.name,
            connectionUri = connectionUri,
            accessToken = server.accessToken,
        )
    }

    private suspend fun loadSections(
        serverContext: ServerContext,
        allowAuthRetry: Boolean = true,
    ) {
        val cachedSections = plexLibraryRepository.getCachedSections(serverContext.serverId)
        _uiState.value = _uiState.value.copy(
            isLoading = cachedSections.isEmpty(),
            serverName = serverContext.serverName,
            connectionUri = serverContext.connectionUri,
            sections = cachedSections,
            activePane = HomePane.Browse,
            selectedSection = null,
            browseStack = emptyList(),
            sectionItems = emptyList(),
            sectionErrorMessage = null,
            activeDownloadPlan = null,
            isLoadingDownloadPlan = false,
            downloadPlanErrorMessage = null,
            lastPlannedJobMessage = null,
            errorMessage = null,
        )

        val sectionsResult = runCatching {
            plexLibraryRepository.refreshSections(
                serverId = serverContext.serverId,
                connectionUri = serverContext.connectionUri,
                serverAccessToken = serverContext.accessToken,
            )
        }
        if (handleAuthFailureIfNeeded(sectionsResult, allowAuthRetry)) {
            val fresh = resolveServerContext()
            if (fresh != null) {
                loadSections(serverContext = fresh, allowAuthRetry = false)
                return
            }
        }

        sectionsResult
            .onSuccess { sections ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    serverName = serverContext.serverName,
                    connectionUri = serverContext.connectionUri,
                    sections = sections.sortedBy { it.title.lowercase() },
                    errorMessage = null,
                )
            }
            .onFailure { throwable ->
                logger.e(component = "Library", message = "Failed to load Plex library sections", throwable = throwable)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    serverName = serverContext.serverName,
                    connectionUri = serverContext.connectionUri,
                    sections = if (cachedSections.isEmpty()) {
                        emptyList()
                    } else {
                        plexLibraryRepository.getCachedSections(serverContext.serverId)
                    },
                    errorMessage = if (cachedSections.isEmpty()) {
                        "Failed to load Plex library sections."
                    } else {
                        "Failed to refresh Plex library sections. Showing cached data."
                    },
                )
            }
    }

    private suspend fun loadSectionItems(
        serverContext: ServerContext,
        section: PlexLibrarySection,
        allowAuthRetry: Boolean = true,
    ) {
        val cachedItems = plexLibraryRepository.getCachedSectionItems(
            serverId = serverContext.serverId,
            sectionKey = section.key,
        )
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isSectionLoading = cachedItems.isEmpty(),
            serverName = serverContext.serverName,
            connectionUri = serverContext.connectionUri,
            selectedSection = section,
            sectionItems = cachedItems,
            activePane = HomePane.Browse,
            browseStack = emptyList(),
            sectionErrorMessage = null,
            activeDownloadPlan = null,
            isLoadingDownloadPlan = false,
            downloadPlanErrorMessage = null,
            lastPlannedJobMessage = null,
            errorMessage = null,
        )

        val itemsResult = runCatching {
            plexLibraryRepository.refreshSectionItems(
                serverId = serverContext.serverId,
                connectionUri = serverContext.connectionUri,
                serverAccessToken = serverContext.accessToken,
                sectionKey = section.key,
            )
        }
        if (handleAuthFailureIfNeeded(itemsResult, allowAuthRetry)) {
            val fresh = resolveServerContext()
            if (fresh != null) {
                loadSectionItems(fresh, section, allowAuthRetry = false)
                return
            }
        }

        itemsResult
            .onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    isSectionLoading = false,
                    selectedSection = section,
                    sectionItems = items,
                    browseStack = emptyList(),
                    sectionErrorMessage = null,
                )
            }
            .onFailure { throwable ->
                logger.e(
                    component = "Library",
                    message = "Failed to load Plex library items for section ${section.key}",
                    throwable = throwable,
                )
                _uiState.value = _uiState.value.copy(
                    isSectionLoading = false,
                    selectedSection = section,
                    sectionItems = cachedItems,
                    sectionErrorMessage = if (cachedItems.isEmpty()) {
                        "Failed to load items for ${section.title}."
                    } else {
                        "Failed to refresh ${section.title}. Showing cached items."
                    },
                )
            }
    }

    private suspend fun loadBrowsePath(
        serverContext: ServerContext,
        node: LibraryBrowseNode,
        allowAuthRetry: Boolean = true,
    ) {
        val cachedCollection = plexLibraryRepository.getCachedItemsByPath(
            serverId = serverContext.serverId,
            path = node.path,
        )
        val browseStack = _uiState.value.browseStack
            .filterNot { it.path == node.path } + node

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isSectionLoading = cachedCollection.items.isEmpty(),
            serverName = serverContext.serverName,
            connectionUri = serverContext.connectionUri,
            activePane = HomePane.Browse,
            browseStack = browseStack,
            sectionItems = cachedCollection.items,
            sectionErrorMessage = null,
            activeDownloadPlan = null,
            isLoadingDownloadPlan = false,
            downloadPlanErrorMessage = null,
            lastPlannedJobMessage = null,
            errorMessage = null,
        )

        val pathResult = runCatching {
            plexLibraryRepository.refreshItemsByPath(
                serverId = serverContext.serverId,
                connectionUri = serverContext.connectionUri,
                serverAccessToken = serverContext.accessToken,
                title = node.title,
                path = node.path,
            )
        }
        if (handleAuthFailureIfNeeded(pathResult, allowAuthRetry)) {
            val fresh = resolveServerContext()
            if (fresh != null) {
                loadBrowsePath(fresh, node, allowAuthRetry = false)
                return
            }
        }

        pathResult
            .onSuccess { collection ->
                _uiState.value = _uiState.value.copy(
                    isSectionLoading = false,
                    browseStack = browseStack,
                    sectionItems = collection.items,
                    sectionErrorMessage = null,
                )
            }
            .onFailure { throwable ->
                logger.e(
                    component = "Library",
                    message = "Failed to load Plex child items for path ${node.path}",
                    throwable = throwable,
                )
                _uiState.value = _uiState.value.copy(
                    isSectionLoading = false,
                    browseStack = browseStack,
                    sectionItems = cachedCollection.items,
                    sectionErrorMessage = if (cachedCollection.items.isEmpty()) {
                        "Failed to load items for ${node.title}."
                    } else {
                        "Failed to refresh ${node.title}. Showing cached items."
                    },
                )
            }
    }

    private suspend fun handleAuthFailureIfNeeded(
        result: Result<*>,
        allowAuthRetry: Boolean,
    ): Boolean {
        if (!allowAuthRetry) return false
        val throwable = result.exceptionOrNull() ?: return false
        if (!(throwable is HttpException && throwable.code() == 401)) return false

        logger.w(
            component = "Session",
            message = "Plex request was unauthorized; attempting session recovery",
        )
        return when (val outcome = sessionRecoveryRepository.recoverSession()) {
            is SessionRecoveryRepository.Outcome.Recovered -> true

            SessionRecoveryRepository.Outcome.SessionInvalid -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSectionLoading = false,
                    errorMessage = "Your Plex session expired. Sign in again.",
                )
                logger.i(
                    component = "Session",
                    message = "Session invalid after 401; cleared persisted credentials",
                )
                false
            }

            SessionRecoveryRepository.Outcome.Inconclusive -> {
                logger.w(
                    component = "Session",
                    message = "Session recovery inconclusive (${outcome.javaClass.simpleName})",
                )
                false
            }
        }
    }

    private fun buildDownloadPlanOptions(item: PlexLibraryItem): List<DownloadPlanOption> {
        val supportsDirect = item.type in setOf("movie", "episode")
        return buildList {
            if (supportsDirect) {
                add(
                    DownloadPlanOption(
                        strategy = DownloadStrategy.Direct,
                        title = "Direct download",
                        description = "Original-quality file transfer when the server exposes a direct media part.",
                        requestedQuality = "Original",
                        recommended = true,
                    ),
                )
            }
            add(
                DownloadPlanOption(
                    strategy = DownloadStrategy.Queue,
                    title = "Queued transcode",
                    description = "Server-managed preparation flow for downloads that need a queue-backed transcode path.",
                    requestedQuality = "720p",
                    recommended = !supportsDirect,
                ),
            )
        }
    }
}
