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
            var consecutivePollFailures = 0
            var pendingToken: String? = null

            while (isActive && generation == pollingGeneration) {
                delay(3_000)
                ensureActive()
                if (generation != pollingGeneration) break

                if (pendingToken == null) {
                    val pollResult = runCatching { plexAuthRepository.getAuthToken(pinId) }
                    val token = pollResult.getOrNull()

                    if (!token.isNullOrBlank()) {
                        pendingToken = token
                        consecutivePollFailures = 0
                        logger.i(
                            component = "Auth",
                            message = "Plex returned an auth token for PIN $pinId",
                        )
                    } else if (pollResult.exceptionOrNull() != null) {
                        consecutivePollFailures += 1
                        logger.w(
                            component = "Auth",
                            message = "PIN status poll failed ($consecutivePollFailures/$MAX_CONSECUTIVE_POLL_FAILURES)",
                            throwable = pollResult.exceptionOrNull(),
                        )
                        if (consecutivePollFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                            _uiState.value = _uiState.value.copy(
                                isPolling = false,
                                errorMessage = "Lost contact with Plex while waiting for approval. Generate a new PIN.",
                            )
                            break
                        }
                    }
                }

                val tokenToValidate = pendingToken ?: continue
                val validationResult = runCatching {
                    plexAuthRepository.isAuthTokenValid(tokenToValidate)
                }

                when {
                    validationResult.getOrDefault(false) -> {
                        appSessionRepository.saveAuthToken(tokenToValidate)
                        logger.i(
                            component = "Auth",
                            message = "Plex authentication completed for PIN $pinId",
                        )
                        _uiState.value = AuthUiState()
                        break
                    }

                    validationResult.exceptionOrNull() != null -> {
                        logger.w(
                            component = "Auth",
                            message = "Token validation could not reach Plex; keeping token for retry",
                            throwable = validationResult.exceptionOrNull(),
                        )
                    }

                    else -> {
                        logger.w(
                            component = "Auth",
                            message = "Discarding invalid Plex auth token from PIN $pinId",
                        )
                        _uiState.value = _uiState.value.copy(
                            isPolling = false,
                            errorMessage = "Plex sign-in completed, but the returned token was not accepted by Plex.",
                        )
                        break
                    }
                }
            }
        }
    }

    override fun onCleared() {
        reset()
        super.onCleared()
    }

    private companion object {
        const val MAX_CONSECUTIVE_POLL_FAILURES = 5
    }
}
