package com.maelle.feature.home

import com.maelle.domain.downloads.model.DownloadPlan
import com.maelle.domain.downloads.model.DownloadState
import com.maelle.domain.downloads.model.DownloadStrategy
import com.maelle.domain.library.model.PlexLibraryItem
import com.maelle.domain.library.model.PlexLibrarySection

data class LibraryBrowseNode(
    val title: String,
    val path: String,
)

enum class HomePane {
    Browse,
    Downloads,
}

data class HomeDownloadJobItem(
    val jobId: String,
    val mediaKey: String,
    val mediaTitle: String,
    val mediaSecondaryTitle: String?,
    val strategy: DownloadStrategy,
    val state: DownloadState,
    val requestedQuality: String,
    val queueId: String?,
    val queueItemId: String?,
    val bytesDownloaded: Long,
    val bytesTotal: Long?,
    val localFileName: String?,
    val localFilePath: String?,
    val artifactBytes: Long?,
    val errorMessage: String?,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val isSectionLoading: Boolean = false,
    val serverName: String = "",
    val connectionUri: String = "",
    val activePane: HomePane = HomePane.Browse,
    val sections: List<PlexLibrarySection> = emptyList(),
    val selectedSection: PlexLibrarySection? = null,
    val sectionItems: List<PlexLibraryItem> = emptyList(),
    val browseStack: List<LibraryBrowseNode> = emptyList(),
    val downloadJobs: List<HomeDownloadJobItem> = emptyList(),
    val activeDownloadPlan: DownloadPlan? = null,
    val isLoadingDownloadPlan: Boolean = false,
    val planBurnSubtitles: Boolean = false,
    val downloadPlanErrorMessage: String? = null,
    val lastPlannedJobMessage: String? = null,
    val errorMessage: String? = null,
    val sectionErrorMessage: String? = null,
)
