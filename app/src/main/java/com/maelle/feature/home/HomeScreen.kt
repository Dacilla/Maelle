package com.maelle.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maelle.app.designsystem.theme.MaelleTheme
import com.maelle.domain.downloads.model.DownloadStrategy
import com.maelle.domain.library.model.PlexLibraryItem
import com.maelle.domain.library.model.PlexLibrarySection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onSwitchServer: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    MaelleTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val selectedSection = uiState.selectedSection
        val context = androidx.compose.ui.platform.LocalContext.current

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Maelle",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = uiState.serverName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        if (uiState.activePane == HomePane.Browse) {
                            IconButton(onClick = viewModel::enterSearchMode) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                TabRow(
                    selectedTabIndex = uiState.activePane.ordinal,
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    Tab(
                        selected = uiState.activePane == HomePane.Browse,
                        onClick = { viewModel.showPane(HomePane.Browse) },
                        text = { Text("Browse") },
                    )
                    Tab(
                        selected = uiState.activePane == HomePane.Downloads,
                        onClick = { viewModel.showPane(HomePane.Downloads) },
                        text = { Text("Downloads") },
                    )
                }

                if (uiState.lastPlannedJobMessage != null && uiState.activePane == HomePane.Downloads) {
                    Text(
                        text = uiState.lastPlannedJobMessage.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }

                if (uiState.activePane == HomePane.Downloads) {
                    DownloadsPane(
                        uiState = uiState,
                        settings = settings,
                        onRetryJob = viewModel::retryDownload,
                        onPauseJob = viewModel::pauseDownload,
                        onResumeJob = viewModel::resumeDownload,
                        onRefreshJob = viewModel::refreshTrackedDownload,
                        onPlayJob = { job ->
                            val path = job.localFilePath ?: return@DownloadsPane
                            com.maelle.feature.player.PlayerActivity.start(
                                context = context,
                                filePath = path,
                                title = listOfNotNull(
                                    job.mediaSecondaryTitle?.takeIf { it.isNotBlank() },
                                    job.mediaTitle.takeIf { it.isNotBlank() },
                                ).distinct().joinToString(" - "),
                            )
                        },
                    )
                } else {
                    BrowsePane(
                        uiState = uiState,
                        settings = settings,
                        onSectionSelected = viewModel::openSection,
                        onItemClicked = { item -> onItemClicked(viewModel, item) },
                        onNavigateUp = viewModel::navigateUp,
                        onSearchQueryChanged = viewModel::updateSearchQuery,
                        onSearchOpened = viewModel::enterSearchMode,
                        onSearchClosed = viewModel::exitSearchMode,
                    )
                }
            }
        }

        if (uiState.isLoadingDownloadPlan || uiState.activeDownloadPlan != null || uiState.downloadPlanErrorMessage != null) {
            val plan = uiState.activeDownloadPlan
            DownloadPlanDialog(
                isLoading = uiState.isLoadingDownloadPlan,
                title = plan?.detail?.title ?: uiState.downloadPlanErrorMessage?.let { "Download" } ?: "Download",
                subtitle = plan?.detail?.secondaryTitle,
                summary = plan?.detail?.summary,
                estimatedBytes = plan?.detail?.estimatedBytes,
                options = plan?.options?.map { option ->
                    DownloadPlanOptionUi(
                        quality = option.requestedQuality,
                        label = option.title,
                        description = option.description,
                        strategy = option.strategy,
                        recommended = option.recommended,
                    )
                } ?: emptyList(),
                selectedQuality = uiState.planSelectedQuality,
                burnSubtitles = uiState.planBurnSubtitles,
                burnSubtitlesAvailable = plan?.options
                    ?.any { it.strategy == DownloadStrategy.Queue } == true,
                onQualitySelected = viewModel::selectPlanQuality,
                onBurnSubtitlesToggled = viewModel::togglePlanBurnSubtitles,
                onStart = viewModel::startPlannedDownload,
                onDismiss = viewModel::dismissDownloadPlanner,
                formatSize = { bytes -> formatSize(bytes) },
            )
        }
    }
}

private fun onItemClicked(viewModel: HomeViewModel, item: PlexLibraryItem) {
    if (item.browsePath != null) {
        viewModel.openItem(item)
    } else {
        viewModel.openDownloadPlanner(item)
    }
}

internal fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "0 MiB"
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    val mib = bytes / (1024.0 * 1024.0)
    return if (gib >= 1.0) {
        String.format(java.util.Locale.US, "%.2f GiB", gib)
    } else {
        String.format(java.util.Locale.US, "%.0f MiB", mib)
    }
}
