package com.maelle.feature.servers

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maelle.app.designsystem.components.DevDetail
import com.maelle.app.designsystem.theme.MaelleTheme

@Composable
fun ServerSelectionScreen(
    onLogout: () -> Unit,
    onCancelPicker: (() -> Unit)? = null,
    viewModel: ServerSelectionViewModel = hiltViewModel(),
) {
    MaelleTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val developerMode by viewModel.developerMode.collectAsStateWithLifecycle()
        var showDevDetails by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        ) {
            Text(
                text = if (onCancelPicker != null) "Switch Server" else "Choose a Server",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (onCancelPicker != null) {
                    "Pick a different server, or keep the one you are using."
                } else {
                    "Select the Plex server to download from."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::refresh) { Text("Retry") }
                        OutlinedButton(onClick = onLogout) { Text("Log Out") }
                    }
                }

                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.servers, key = { it.server.serverId }) { card ->
                            val reachable = card.bestConnectionUri != null
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (reachable) {
                                            Modifier.clickable { viewModel.selectBestConnection(card) }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(
                                                color = if (reachable) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline
                                                },
                                                shape = CircleShape,
                                            ),
                                    )
                                    Spacer(Modifier.size(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = card.server.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = when {
                                                reachable -> "Ready to connect"
                                                else -> "Not reachable from this network"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (developerMode && reachable) {
                                            DevDetail(card.bestConnectionUri.orEmpty())
                                        }
                                    }
                                }
                                if (developerMode) {
                                    Column(Modifier.padding(start = 44.dp, bottom = 14.dp, end = 14.dp)) {
                                        Text(
                                            "Connections",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        card.connectionStatuses.entries
                                            .sortedWith(compareBy { it.value })
                                            .forEach { (uri, latency) ->
                                                val label = if (latency >= 0) "$latency ms" else "unreachable"
                                                Text(
                                                    text = "$uri - $label",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable(enabled = latency >= 0) {
                                                            viewModel.selectConnection(card.server, uri)
                                                        }
                                                        .padding(vertical = 2.dp),
                                                )
                                            }
                                    }
                                }
                            }
                        }

                        item {
                            if (onCancelPicker != null) {
                                OutlinedButton(onClick = onCancelPicker, modifier = Modifier.fillMaxWidth()) {
                                    Text("Keep Current Server")
                                }
                            }
                            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                                Text("Log Out")
                            }
                        }
                    }
                }
            }

            if (developerMode && uiState.errorMessage == null) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = viewModel::refresh) { Text("Re-test connections") }
                    TextButton(onClick = { showDevDetails = !showDevDetails }) {
                        Text(if (showDevDetails) "Hide raw" else "Raw")
                    }
                }
                if (showDevDetails) {
                    uiState.servers.forEach { card ->
                        DevDetail("${card.server.serverId} owned=${card.server.owned}")
                    }
                }
            }
        }
    }
}
