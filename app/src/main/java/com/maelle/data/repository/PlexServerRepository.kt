package com.maelle.data.repository

import com.maelle.data.local.dao.ServerDao
import com.maelle.data.local.entity.ServerEntity
import com.maelle.data.remote.resources.PlexResourceMapper
import com.maelle.data.remote.resources.PlexResourcesService
import com.maelle.data.remote.server.ServerConnectionTester
import com.maelle.domain.servers.model.PlexConnection
import com.maelle.domain.servers.model.PlexServer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlexServerRepository @Inject constructor(
    private val plexResourcesService: PlexResourcesService,
    private val plexResourceMapper: PlexResourceMapper,
    private val serverDao: ServerDao,
    private val json: Json,
    private val serverConnectionTester: ServerConnectionTester,
) {

    data class ServerDownloadContext(
        val serverId: String,
        val serverName: String,
        val connectionUri: String,
        val accessToken: String,
    )

    fun observeSelectedServer(selectedServerId: Flow<String?>): Flow<PlexServer?> {
        return selectedServerId.flatMapLatest { serverId ->
            if (serverId.isNullOrBlank()) {
                flowOf(null)
            } else {
                serverDao.observeById(serverId).map { entity ->
                    entity?.toModel(json)
                }
            }
        }
    }

    fun observeCachedServers(): Flow<List<PlexServer>> {
        return serverDao.observeAll().map { servers -> servers.map { it.toModel(json) } }
    }

    suspend fun getServer(serverId: String): PlexServer? {
        return serverDao.getById(serverId)?.toModel(json)
    }

    suspend fun getServerDownloadContext(serverId: String): ServerDownloadContext? {
        val entity = serverDao.getById(serverId) ?: return null
        val connectionUri = entity.lastSelectedConnectionUri ?: return null
        return ServerDownloadContext(
            serverId = entity.serverId,
            serverName = entity.name,
            connectionUri = connectionUri,
            accessToken = entity.accessToken,
        )
    }

    suspend fun refreshServers(userToken: String): List<PlexServer> {
        val resources = plexResourcesService.getResources(userToken = userToken)
        val servers = plexResourceMapper.mapServers(resources)
        serverDao.upsertAll(
            servers.map { server ->
                ServerEntity(
                    serverId = server.serverId,
                    name = server.name,
                    accessToken = server.accessToken,
                    owned = server.owned,
                    cachedConnectionsJson = json.encodeToString(server.connections),
                    lastSelectedConnectionUri = null,
                    lastSuccessfulContactEpochMs = null,
                )
            },
        )
        return servers
    }

    suspend fun selectConnection(serverId: String, connectionUri: String, lastSuccessfulContactEpochMs: Long?) {
        val existing = serverDao.getById(serverId) ?: return
        serverDao.upsert(
            existing.copy(
                lastSelectedConnectionUri = connectionUri,
                lastSuccessfulContactEpochMs = lastSuccessfulContactEpochMs,
            ),
        )
    }

    suspend fun measureConnections(server: PlexServer): Map<String, Int> = coroutineScope {
        server.connections.map { connection ->
            async {
                connection.uri to serverConnectionTester.test(connection.uri, server.accessToken)
            }
        }.awaitAll().toMap()
    }

    fun chooseBestConnection(server: PlexServer, latencies: Map<String, Int>): PlexConnection? {
        val reachable = server.connections
            .mapNotNull { connection ->
                val latency = latencies[connection.uri] ?: return@mapNotNull null
                if (latency < 0) null else connection to latency
            }
            .sortedWith(
                compareBy<Pair<PlexConnection, Int>> { (_, latency) -> latency }
                    .thenBy { (connection, _) -> if (connection.local) 0 else 1 },
            )
        return reachable.firstOrNull()?.first
    }

    private fun ServerEntity.toModel(json: Json): PlexServer {
        return PlexServer(
            serverId = serverId,
            name = name,
            accessToken = accessToken,
            owned = owned,
            connections = json.decodeFromString(cachedConnectionsJson),
        )
    }
}
