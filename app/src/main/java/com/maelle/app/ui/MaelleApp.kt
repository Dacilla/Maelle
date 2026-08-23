package com.maelle.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maelle.feature.auth.AuthScreen
import com.maelle.feature.home.HomeScreen
import com.maelle.feature.servers.ServerSelectionScreen

@Composable
fun MaelleApp() {
    val viewModel: MaelleAppViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState.destination) {
        MaelleDestination.Auth -> AuthScreen()
        MaelleDestination.Servers -> ServerSelectionScreen(
            onLogout = viewModel::logout,
            onCancelPicker = if (uiState.isServerPickerCancelable) {
                viewModel::dismissServerPicker
            } else {
                null
            },
        )
        MaelleDestination.Home -> HomeScreen(
            onLogout = viewModel::logout,
            onSwitchServer = viewModel::showServerPicker,
        )
    }
}
