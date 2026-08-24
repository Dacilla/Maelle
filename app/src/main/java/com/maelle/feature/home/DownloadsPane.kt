package com.maelle.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maelle.app.designsystem.components.DevDetail
import com.maelle.app.designsystem.components.EmptyMessage
import com.maelle.app.designsystem.components.StatusChip
import com.maelle.core.settings.UserSettings
import com.maelle.domain.downloads.model.DownloadState

private enum class DownloadFilter(val label: String) {
    All("All"),
    Active("Active"),
    Completed("Completed"),
    Failed("Failed"),
}

@Composable
fun DownloadsPane(
    uiState: HomeUiState,
    settings: UserSettings,
    onRetryJob: (String) -> Unit,
    onPauseJob: (String) -> Unit,
    onResumeJob: (String) -> Unit,
    onRefreshJob: (String) -> Unit,
    onPlayJob: (HomeDownloadJobItem) -> Unit,
) {
    var filter by remember { mutableStateOf(DownloadFilter.All) }

    val filteredJobs = uiState.downloadJobs.filter { job ->
        when (filter) {
            DownloadFilter.All -> true
            DownloadFilter.Active -> job.state in setOf(
                DownloadState.Queued,
                DownloadState.Preparing,
                DownloadState.Downloading,
                DownloadState.WaitingForServer,
                DownloadState.Paused,
            )
            DownloadFilter.Completed -> job.state == DownloadState.Completed
            DownloadFilter.Failed -> job.state in setOf(
                DownloadState.Failed,
                DownloadState.NeedsReconciliation,
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DownloadFilter.entries.forEach { option ->
                androidx.compose.material3.FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label) },
                )
            }
        }

        if (filteredJobs.isEmpty()) {
            EmptyMessage(
                when (filter) {
                    DownloadFilter.All -> "Nothing downloaded yet. Find something in Browse and tap it."
                    DownloadFilter.Active -> "No downloads in progress."
                    DownloadFilter.Completed -> "No completed downloads yet."
                    DownloadFilter.Failed -> "No failed downloads. Nice."
                },
            )
            return@Column
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 4.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filteredJobs, key = { it.jobId }) { job ->
                DownloadJobCard(
                    job = job,
                    devMode = settings.developerMode,
                    onRetry = { onRetryJob(job.jobId) },
                    onPause = { onPauseJob(job.jobId) },
                    onResume = { onResumeJob(job.jobId) },
                    onRefresh = { onRefreshJob(job.jobId) },
                    onPlay = { onPlayJob(job) },
                )
            }
        }
    }
}

@Composable
private fun DownloadJobCard(
    job: HomeDownloadJobItem,
    devMode: Boolean,
    onRetry: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = job.state.accentColor()
    val isActive = job.state in setOf(
        DownloadState.Preparing,
        DownloadState.Downloading,
        DownloadState.WaitingForServer,
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 78.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = job.mediaTitle.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull(
                            job.mediaSecondaryTitle?.takeIf { it.isNotBlank() },
                            job.mediaTitle,
                        ).distinct().joinToString(" - "),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusChip(text = job.state.label(), color = accent)
                        Text(
                            text = when (job.strategy.name.lowercase()) {
                                "direct" -> "Original"
                                else -> job.requestedQuality
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (job.state == DownloadState.Downloading ||
                (job.state == DownloadState.Completed && (job.bytesTotal ?: 0L) > 0L)
            ) {
                Spacer(Modifier.height(10.dp))
                val progress = if ((job.bytesTotal ?: 0L) > 0) {
                    (job.bytesDownloaded.toFloat() / (job.bytesTotal ?: 1L).toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatSize(job.bytesDownloaded)} of ${formatSize(job.bytesTotal)}" +
                        if (job.bytesTotal != null && job.bytesTotal > 0) " - ${(progress * 100).toInt()}%" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            job.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (job.state) {
                    DownloadState.Completed -> {
                        Button(onClick = onPlay) { Text("Play") }
                    }

                    DownloadState.Failed, DownloadState.NeedsReconciliation -> {
                        Button(onClick = onRetry) { Text("Retry") }
                    }

                    DownloadState.Paused -> {
                        Button(onClick = onResume) { Text("Resume") }
                    }

                    DownloadState.WaitingForServer -> {
                        if (job.strategy.name.lowercase() == "queue") {
                            OutlinedButton(onClick = onRefresh) { Text("Check again") }
                        }
                        OutlinedButton(onClick = onPause) { Text("Pause") }
                    }

                    else -> {
                        OutlinedButton(onClick = onPause) { Text("Pause") }
                    }
                }
            }

            if (expanded && devMode) {
                Spacer(Modifier.height(8.dp))
                DevDetail("jobId=${job.jobId}")
                DevDetail("mediaKey=${job.mediaKey}")
                DevDetail("queueId=${job.queueId} item=${job.queueItemId}")
                DevDetail("quality=${job.requestedQuality} strategy=${job.strategy}")
                job.localFilePath?.let { DevDetail("path=$it") }
            } else if (expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Enable Developer Mode in Settings to see technical details.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DownloadState.label(): String = when (this) {
    DownloadState.Queued -> "Queued"
    DownloadState.Preparing -> "Preparing"
    DownloadState.WaitingForServer -> "Preparing on server"
    DownloadState.Downloading -> "Downloading"
    DownloadState.Paused -> "Paused"
    DownloadState.Completed -> "Completed"
    DownloadState.Failed -> "Failed"
    DownloadState.NeedsReconciliation -> "Needs attention"
}

private fun DownloadState.accentColor(): Color = when (this) {
    DownloadState.Completed -> Color(0xFF4CAF50)
    DownloadState.Failed, DownloadState.NeedsReconciliation -> Color(0xFFE57373)
    DownloadState.Downloading -> Color(0xFF64B5F6)
    DownloadState.Preparing, DownloadState.WaitingForServer, DownloadState.Queued -> Color(0xFFFFB74D)
    DownloadState.Paused -> Color(0xFF9E9E9E)
}
