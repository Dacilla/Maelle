package com.maelle.feature.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maelle.app.designsystem.components.DevDetail
import com.maelle.app.designsystem.theme.MaelleTheme

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    MaelleTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val developerMode by viewModel.developerMode.collectAsStateWithLifecycle()
        val context = LocalContext.current
        var showDevDetails by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (uiState.pinCode == null && !uiState.isGeneratingPin && !uiState.isPolling) {
                viewModel.generatePin()
            }
        }

        DisposableEffect(Unit) {
            onDispose { viewModel.reset() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Maelle",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Download from your Plex server and watch anywhere.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))

            when {
                uiState.isGeneratingPin -> {
                    CircularProgressIndicator()
                }

                uiState.errorMessage != null && uiState.pinCode.isNullOrBlank() -> {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::generatePin) {
                        Text("Try Again")
                    }
                }

                else -> {
                    Text(
                        text = "Enter this code at plex.tv/link",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 40.dp, vertical = 20.dp),
                    ) {
                        Text(
                            text = uiState.pinCode.orEmpty(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(uiState.authUrl ?: "https://plex.tv/link")),
                            )
                        },
                        enabled = !uiState.isGeneratingPin && !uiState.authUrl.isNullOrBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open Plex in Browser")
                    }
                    Spacer(Modifier.height(12.dp))
                    if (uiState.isPolling) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Waiting for you to approve this device...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::generatePin, enabled = !uiState.isGeneratingPin) {
                        Text("Use a different code")
                    }
                    if (uiState.errorMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (developerMode) {
                Spacer(Modifier.height(40.dp))
                TextButton(onClick = { showDevDetails = !showDevDetails }) {
                    Text(if (showDevDetails) "Hide details" else "Show details")
                }
                if (showDevDetails) {
                    DevDetail("pinId=${uiState.pinId}")
                    DevDetail("authUrl=${uiState.authUrl}")
                    DevDetail("polling=${uiState.isPolling}")
                }
            }
        }
    }
}
