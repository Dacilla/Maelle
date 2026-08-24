package com.maelle.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maelle.core.settings.UserSettingsRepository
import com.maelle.data.repository.AppSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val developerMode: Boolean = false,
    val burnSubtitlesByDefault: Boolean = false,
    val serverName: String = "",
    val versionName: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
    appSessionRepository: AppSessionRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        userSettingsRepository.settings,
        appSessionRepository.observeSession(),
    ) { settings, session ->
        SettingsUiState(
            developerMode = settings.developerMode,
            burnSubtitlesByDefault = settings.burnSubtitlesByDefault,
            serverName = session.selectedServerName ?: "none",
            versionName = com.maelle.BuildConfig.VERSION_NAME,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(versionName = com.maelle.BuildConfig.VERSION_NAME),
    )

    fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch { userSettingsRepository.setDeveloperMode(enabled) }
    }

    fun setBurnSubtitlesByDefault(enabled: Boolean) {
        viewModelScope.launch { userSettingsRepository.setBurnSubtitlesByDefault(enabled) }
    }
}
