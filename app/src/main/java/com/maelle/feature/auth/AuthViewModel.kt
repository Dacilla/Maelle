package com.maelle.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maelle.data.repository.AppSessionRepository
import com.maelle.data.repository.PlexAuthRepository
import com.maelle.core.logging.RedactingLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val plexAuthRepository: PlexAuthRepository,
    private val appSessionRepository: AppSessionRepository,
    private val logger: RedactingLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var pollingGeneration: Long = 0L

    fun generatePin() {
        pollingGeneration += 1
        pollingJob?.cancel()
        val generation = pollingGeneration
        viewModelScope.launch {
            _uiState.value = AuthUiState(isGeneratingPin = true)
            runCatching {
                plexAuthRepository.createPinChallenge()
            }.onSuccess { challenge ->
                if (generation != pollingGeneration) return@onSuccess
                logger.i(component = "Auth", message = "Generated Plex PIN challenge ${challenge.id}")
                _uiState.value = AuthUiState(
                    pinCode = challenge.code,
                    pinId = challenge.id,
                    authUrl = challenge.authUrl,
                    isGeneratingPin = false,
                    isPolling = true,
                )
                startPolling(challenge.id, generation)
            }.onFailure { throwable ->
                if (generation != pollingGeneration) return@onFailure
                logger.e(component = "Auth", message = "Failed to create Plex PIN", throwable = throwable)
                _uiState.value = AuthUiState(
                    isGeneratingPin = false,
                    errorMessage = "Failed to generate a Plex PIN. Check connectivity and try again.",
                )
            }
        }
    }

    fun reset() {
        pollingGeneration += 1
        pollingJob?.cancel()
        pollingJob = null
        _uiState.value = AuthUiState()
    }

    private fun startPolling(pinId: Int, generation: Long) {
        pollingJob = viewModelScope.launch {
            while (isActive && generation == pollingGeneration) {
                delay(3_000)
                ensureActive()
                if (generation != pollingGeneration) break
                val token = runCatching { plexAuthRepository.getAuthToken(pinId) }
                    .onFailure { throwable ->
                        if (generation != pollingGeneration || !isActive) return@onFailure
                        logger.e(component = "Auth", message = "Failed while polling PIN status", throwable = throwable)
                        _uiState.value = _uiState.value.copy(
                            isPolling = false,
                            errorMessage = "Failed while checking Plex authentication status.",
                        )
                    }
                    .getOrNull()

                if (generation != pollingGeneration || !isActive) {
                    break
                }

                if (!token.isNullOrBlank()) {
                    val isValid = runCatching { plexAuthRepository.isAuthTokenValid(token) }
                        .onFailure { throwable ->
                            logger.e(component = "Auth", message = "Failed while validating Plex auth token", throwable = throwable)
                        }
                        .getOrDefault(false)

                    if (generation != pollingGeneration || !isActive) {
                        break
                    }

                    if (isValid) {
                        appSessionRepository.saveAuthToken(token)
                        logger.i(component = "Auth", message = "Plex authentication completed for PIN $pinId")
                        _uiState.value = AuthUiState()
                        break
                    } else {
                        logger.w(component = "Auth", message = "Discarding invalid Plex auth token from PIN $pinId")
                        _uiState.value = _uiState.value.copy(
                            isPolling = false,
                            errorMessage = "Plex sign-in completed, but the returned token was not accepted by Plex.",
                        )
                        break
                    }
                }

                if (_uiState.value.errorMessage != null) {
                    break
                }
            }
        }
    }

    override fun onCleared() {
        reset()
        super.onCleared()
    }
}
