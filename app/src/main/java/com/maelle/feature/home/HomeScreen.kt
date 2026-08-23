package com.maelle.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maelle.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maelle.domain.downloads.model.DownloadPlan
import com.maelle.domain.downloads.model.DownloadStrategy
import com.maelle.domain.library.model.PlexLibraryItem
import com.maelle.domain.library.model.PlexLibrarySection
import com.maelle.feature.player.PlayerActivity
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onSwitchServer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedSection = uiState.selectedSection
    val currentBrowseNode = uiState.browseStack.lastOrNull()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.errorMessage != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = viewModel::refresh) {
                        Text("Retry")
                    }
                    OutlinedButton(onClick = onLogout) {
                        Text("Log Out")
                    }
                }
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Maelle",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "Connected to ${uiState.serverName}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = uiState.connectionUri,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (uiState.activePane == HomePane.Downloads) {
                                    "Tracked download jobs:"
                                } else if (selectedSection == null) {
                                    "Library sections available on this server:"
                                } else if (currentBrowseNode != null) {
                                    "Browsing ${currentBrowseNode.title}"
                                } else {
                                    "Browsing ${selectedSection.title}"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PaneActions(
                                activePane = uiState.activePane,
                                onShowBrowse = { viewModel.showPane(HomePane.Browse) },
                                onShowDownloads = { viewModel.showPane(HomePane.Downloads) },
                            )
                        }
                    }

                    if (uiState.activePane == HomePane.Downloads) {
                        item {
                            DownloadsSection(
                                jobs = uiState.downloadJobs,
                                onRetryJob = viewModel::retryDownload,
                                onRefreshJob = viewModel::refreshTrackedDownload,
                                onPauseJob = viewModel::pauseDownload,
                                onResumeJob = viewModel::resumeDownload,
                                onPlayJob = { job ->
                                    val path = job.localFilePath ?: return@DownloadsSection
                                    PlayerActivity.start(
                                        context = context,
                                        filePath = path,
                                        title = listOfNotNull(
                                            job.mediaSecondaryTitle?.takeIf { it.isNotBlank() },
                                            job.mediaTitle.takeIf { it.isNotBlank() },
                                        ).distinct().joinToString(" - "),
                                    )
                                },
                            )
                        }
                    } else if (selectedSection == null) {
                        items(uiState.sections, key = { it.key }) { section ->
                            SectionCard(
                                section = section,
                                onClick = { viewModel.openSection(section) },
                            )
                        }
                    } else {
                        item {
                            SectionActions(
                                sectionTitle = currentBrowseNode?.title ?: selectedSection.title,
                                breadcrumb = buildBreadcrumb(
                                    rootTitle = selectedSection.title,
                                    browseTitles = uiState.browseStack.map { it.title },
                                ),
                                isLoading = uiState.isSectionLoading,
                                errorMessage = uiState.sectionErrorMessage,
                                onBack = viewModel::navigateUp,
                                onRefresh = viewModel::refresh,
                            )
                        }

                        items(uiState.sectionItems, key = { it.ratingKey }) { item ->
                            LibraryItemCard(
                                item = item,
                                onClick = if (item.browsePath != null) {
                                    { viewModel.openItem(item) }
                                } else {
                                    null
                                },
                                onPlanDownload = { viewModel.openDownloadPlanner(item) },
                            )
                        }

                        if (!uiState.isSectionLoading &&
                            uiState.sectionErrorMessage == null &&
                            uiState.sectionItems.isEmpty()
                        ) {
                            item {
                                Text(
                                    text = "No media items were returned for this section.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Build ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_MARKER})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            RowActions(
                                onRefresh = viewModel::refresh,
                                onSwitchServer = onSwitchServer,
                                onLogout = onLogout,
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.isLoadingDownloadPlan || uiState.activeDownloadPlan != null || uiState.downloadPlanErrorMessage != null) {
        DownloadPlanDialog(
            isLoading = uiState.isLoadingDownloadPlan,
            plan = uiState.activeDownloadPlan,
            errorMessage = uiState.downloadPlanErrorMessage,
            onDismiss = viewModel::dismissDownloadPlanner,
            onChooseStrategy = viewModel::createPlannedJob,
        )
    }

    uiState.lastPlannedJobMessage?.let { message ->
        LaunchedEffect(message) {
            delay(4000)
            viewModel.dismissPlannedJobMessage()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    OutlinedButton(onClick = viewModel::dismissPlannedJobMessage) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@Composable
private fun RowActions(
    onRefresh: () -> Unit,
    onSwitchServer: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onRefresh) {
            Text("Refresh Libraries")
        }
        OutlinedButton(onClick = onSwitchServer) {
            Text("Switch Server")
        }
        OutlinedButton(onClick = onLogout) {
            Text("Log Out")
        }
    }
}

@Composable
private fun PaneActions(
    activePane: HomePane,
    onShowBrowse: () -> Unit,
    onShowDownloads: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onShowBrowse,
            enabled = activePane != HomePane.Browse,
        ) {
            Text("Browse")
        }
        OutlinedButton(
            onClick = onShowDownloads,
            enabled = activePane != HomePane.Downloads,
        ) {
            Text("Downloads")
        }
    }
}

@Composable
private fun DownloadsSection(
    jobs: List<HomeDownloadJobItem>,
    onRetryJob: (String) -> Unit,
    onRefreshJob: (String) -> Unit,
    onPauseJob: (String) -> Unit,
    onResumeJob: (String) -> Unit,
    onPlayJob: (HomeDownloadJobItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Queued and tracked jobs",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (jobs.isEmpty()) {
            Text(
                text = "No download jobs have been created yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            jobs.forEach { job ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = job.mediaTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        job.mediaSecondaryTitle?.let { secondaryTitle ->
                            Text(
                                text = secondaryTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Job ${job.jobId.take(8)} • Media key: ${job.mediaKey}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "State: ${job.state.name} • Strategy: ${job.strategy.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (job.queueId != null || job.queueItemId != null) {
                            Text(
                                text = "Queue: ${job.queueId ?: "-"} • Item: ${job.queueItemId ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Requested quality: ${job.requestedQuality}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Progress: ${formatBytes(job.bytesDownloaded)}" +
                                (job.bytesTotal?.let { " / ${formatBytes(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        job.artifactBytes?.let { artifactBytes ->
                            Text(
                                text = "Artifact: ${job.localFileName ?: "unknown"} • ${formatBytes(artifactBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        job.localFilePath?.let { filePath ->
                            Text(
                                text = filePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        job.errorMessage?.let { errorMessage ->
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        DownloadJobActions(
                            job = job,
                            onRetryJob = onRetryJob,
                            onRefreshJob = onRefreshJob,
                            onPauseJob = onPauseJob,
                            onResumeJob = onResumeJob,
                            onPlayJob = onPlayJob,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadJobActions(
    job: HomeDownloadJobItem,
    onRetryJob: (String) -> Unit,
    onRefreshJob: (String) -> Unit,
    onPauseJob: (String) -> Unit,
    onResumeJob: (String) -> Unit,
    onPlayJob: (HomeDownloadJobItem) -> Unit,
) {
    val activeStates = setOf(
        com.maelle.domain.downloads.model.DownloadState.Preparing,
        com.maelle.domain.downloads.model.DownloadState.Downloading,
        com.maelle.domain.downloads.model.DownloadState.WaitingForServer,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (job.state == com.maelle.domain.downloads.model.DownloadState.Completed &&
            job.localFilePath != null
        ) {
            Button(onClick = { onPlayJob(job) }) {
                Text("Play")
            }
        }
        if (job.state in activeStates) {
            OutlinedButton(onClick = { onPauseJob(job.jobId) }) {
                Text("Pause")
            }
        }
        if (job.state == com.maelle.domain.downloads.model.DownloadState.Paused) {
            Button(onClick = { onResumeJob(job.jobId) }) {
                Text("Resume")
            }
        }
        if (job.state == com.maelle.domain.downloads.model.DownloadState.WaitingForServer &&
            job.strategy == DownloadStrategy.Queue
        ) {
            OutlinedButton(onClick = { onRefreshJob(job.jobId) }) {
                Text("Check Queue Again")
            }
        }
        if (job.state == com.maelle.domain.downloads.model.DownloadState.Failed ||
            job.state == com.maelle.domain.downloads.model.DownloadState.NeedsReconciliation
        ) {
            Button(onClick = { onRetryJob(job.jobId) }) {
                Text("Retry Download")
            }
        }
    }
}

@Composable
private fun SectionCard(
    section: PlexLibrarySection,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Type: ${section.type}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Key: ${section.key}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionActions(
    sectionTitle: String,
    breadcrumb: String,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isLoading) {
            CircularProgressIndicator()
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = sectionTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(breadcrumb) },
        )
        Button(onClick = onRefresh) {
            Text("Refresh Section")
        }
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
    }
}

@Composable
private fun LibraryItemCard(
    item: PlexLibraryItem,
    onClick: (() -> Unit)?,
    onPlanDownload: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            item.secondaryTitle?.takeIf { it.isNotBlank() }?.let { secondaryTitle ->
                Text(
                    text = secondaryTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = buildString {
                    append(item.type.replaceFirstChar { it.uppercase() })
                    item.year?.let { append(" • $it") }
                    item.itemCountLabel?.let { append(" • $it") }
                    if (item.browsePath != null) {
                        append(" • Browse")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onPlanDownload) {
                Text("Plan Download")
            }
        }
    }
}

private fun buildBreadcrumb(
    rootTitle: String,
    browseTitles: List<String>,
): String {
    return (listOf(rootTitle) + browseTitles).joinToString(" / ")
}

@Composable
private fun DownloadPlanDialog(
    isLoading: Boolean,
    plan: DownloadPlan?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onChooseStrategy: (DownloadStrategy) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    isLoading -> "Loading Download Plan"
                    plan != null -> "Plan Download"
                    else -> "Download Plan"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    isLoading -> CircularProgressIndicator()
                    errorMessage != null -> Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                    )
                    plan != null -> {
                        Text(
                            text = plan.detail.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        plan.detail.secondaryTitle?.let { secondaryTitle ->
                            Text(
                                text = secondaryTitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = buildString {
                                append(plan.detail.type.replaceFirstChar { it.uppercase() })
                                plan.detail.year?.let { append(" • $it") }
                                plan.detail.container?.let { append(" • $it") }
                                plan.detail.resolution?.let { append(" • ${it}p") }
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        plan.detail.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                            Text(
                                text = summary,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        plan.detail.estimatedBytes?.let { bytes ->
                            Text(
                                text = "Estimated size: ${formatBytes(bytes)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        plan.options.forEach { option ->
                            Button(onClick = { onChooseStrategy(option.strategy) }) {
                                Text(
                                    if (option.recommended) {
                                        "${option.title} (Recommended)"
                                    } else {
                                        option.title
                                    },
                                )
                            }
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    val mib = bytes / (1024.0 * 1024.0)
    return if (gib >= 1.0) {
        String.format(java.util.Locale.US, "%.2f GiB", gib)
    } else {
        String.format(java.util.Locale.US, "%.0f MiB", mib)
    }
}
