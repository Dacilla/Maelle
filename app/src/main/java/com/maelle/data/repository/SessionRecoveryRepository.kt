package com.maelle.data.repository

import com.maelle.core.logging.RedactingLogger
import com.maelle.domain.servers.model.PlexServer
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

/**
 * Validates the persisted Plex account token and refreshes cached server
 * credentials. A token that plex.tv rejects clears the whole session so the
 * UI routes back to sign-in instead of looping on 401s.
 */
@Singleton
class SessionRecoveryRepository @Inject constructor(
    private val appSessionRepository: AppSessionRepository,
    private val plexServerRepository: PlexServerRepository,
    private val logger: RedactingLogger,
) {

    sealed interface Outcome {
        data object SessionInvalid : Outcome
        data class Recovered(val servers: List<PlexServer>) : Outcome
        data object Inconclusive : Outcome
    }

    suspend fun recoverSession(): Outcome {
        val token = appSessionRepository.observeSession().first().plexAuthToken
            ?: return Outcome.SessionInvalid

        return try {
            val servers = plexServerRepository.refreshServers(userToken = token)
            Outcome.Recovered(servers)
        } catch (httpException: HttpException) {
            if (httpException.code() == 401) {
                logger.i(
                    component = "Session",
                    message = "Plex rejected the stored account token; clearing session",
                )
                appSessionRepository.clearSession()
                Outcome.SessionInvalid
            } else {
                logger.w(
                    component = "Session",
                    message = "Session recovery got HTTP ${httpException.code()} from plex.tv",
                )
                Outcome.Inconclusive
            }
        } catch (ioException: IOException) {
            logger.w(
                component = "Session",
                message = "Session recovery could not reach plex.tv",
                throwable = ioException,
            )
            Outcome.Inconclusive
        }
    }
}
