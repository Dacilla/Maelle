package com.maelle.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maelle.domain.downloads.model.DownloadStrategy

@Composable
fun DownloadPlanDialog(
    isLoading: Boolean,
    title: String,
    subtitle: String?,
    summary: String?,
    estimatedBytes: Long?,
    options: List<DownloadPlanOptionUi>,
    selectedQuality: String?,
    burnSubtitles: Boolean,
    burnSubtitlesAvailable: Boolean,
    onQualitySelected: (String) -> Unit,
    onBurnSubtitlesToggled: () -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    formatSize: (Long?) -> String,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isLoading) "Preparing download..." else "Download",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    isLoading -> {
                        CircularProgressIndicator()
                    }

                    else -> {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!summary.isNullOrBlank()) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        estimatedBytes?.let { bytes ->
                            Text(
                                text = "Original size: ${formatSize(bytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        HorizontalDivider()

                        options.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedQuality == option.quality,
                                        onClick = { onQualitySelected(option.quality) },
                                    )
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedQuality == option.quality,
                                    onClick = { onQualitySelected(option.quality) },
                                )
                                Column {
                                    Text(
                                        text = option.label +
                                            if (option.recommended) "  •  Recommended" else "",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (burnSubtitlesAvailable) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Burn subtitles into video",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Switch(
                                    checked = burnSubtitles,
                                    onCheckedChange = { onBurnSubtitlesToggled() },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStart,
                enabled = !isLoading && selectedQuality != null,
            ) {
                Text("Start Download")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

data class DownloadPlanOptionUi(
    val quality: String,
    val label: String,
    val description: String,
    val strategy: DownloadStrategy,
    val recommended: Boolean,
)
