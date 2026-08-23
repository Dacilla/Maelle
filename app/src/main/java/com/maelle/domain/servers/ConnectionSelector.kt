package com.maelle.domain.servers

import com.maelle.domain.servers.model.PlexConnection
import javax.inject.Inject

class ConnectionSelector @Inject constructor() {

    fun chooseBest(
        connections: List<PlexConnection>,
        latencies: Map<String, Int>,
    ): PlexConnection? {
        val reachable = connections.mapNotNull { connection ->
            val latency = latencies[connection.uri] ?: return@mapNotNull null
            if (latency < 0) null else connection to latency
        }
        return reachable.minWithOrNull(
            compareBy(
                { (_, latency) -> latency },
                { (connection, _) -> if (connection.local) 0 else 1 },
            ),
        )?.first
    }
}
