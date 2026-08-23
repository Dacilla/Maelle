package com.maelle.data.repository

import com.maelle.data.local.dao.AppSessionDao
import com.maelle.data.local.entity.AppSessionEntity
import com.maelle.domain.session.model.AppSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AppSessionRepository @Inject constructor(
    private val appSessionDao: AppSessionDao,
) {

    fun observeSession(): Flow<AppSession> {
        return appSessionDao.observe().map { entity ->
            entity?.toModel() ?: AppSession(
                plexAuthToken = null,
                selectedServerId = null,
                selectedServerName = null,
                selectedConnectionUri = null,
            )
        }
    }

    suspend fun saveAuthToken(token: String) {
        val current = appSessionDao.get()
        appSessionDao.upsert(
            AppSessionEntity(
                plexAuthToken = token,
                selectedServerId = current?.selectedServerId,
                selectedServerName = current?.selectedServerName,
                selectedConnectionUri = current?.selectedConnectionUri,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun selectServer(
        serverId: String,
        serverName: String,
        connectionUri: String,
    ) {
        val current = appSessionDao.get()
        appSessionDao.upsert(
            AppSessionEntity(
                plexAuthToken = current?.plexAuthToken,
                selectedServerId = serverId,
                selectedServerName = serverName,
                selectedConnectionUri = connectionUri,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearSession() {
        appSessionDao.upsert(
            AppSessionEntity(
                plexAuthToken = null,
                selectedServerId = null,
                selectedServerName = null,
                selectedConnectionUri = null,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun AppSessionEntity.toModel(): AppSession {
        return AppSession(
            plexAuthToken = plexAuthToken,
            selectedServerId = selectedServerId,
            selectedServerName = selectedServerName,
            selectedConnectionUri = selectedConnectionUri,
        )
    }
}
