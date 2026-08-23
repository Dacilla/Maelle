package com.maelle.data.remote.resources

import com.maelle.domain.servers.model.PlexConnection
import com.maelle.domain.servers.model.PlexServer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonPrimitive

@Singleton
class PlexResourceMapper @Inject constructor() {

    fun mapServers(resources: List<PlexResourceDto>): List<PlexServer> {
        return resources
            .filter { resource ->
                resource.provides?.split(",")?.any { it.trim().equals("server", ignoreCase = true) } == true &&
                    !resource.accessToken.isNullOrBlank()
            }
            .map { resource ->
                PlexServer(
                    serverId = resource.clientIdentifier,
                    name = resource.name,
                    accessToken = checkNotNull(resource.accessToken),
                    owned = resource.owned.asBoolean(),
                    connections = resource.connections.map { connection ->
                        PlexConnection(
                            protocol = connection.protocol,
                            address = connection.address,
                            port = connection.port,
                            uri = connection.uri,
                            local = connection.local ?: isLocalIp(connection.address),
                        )
                    },
                )
            }
    }

    private fun isLocalIp(address: String): Boolean {
        return address.startsWith("192.168.") ||
            address.startsWith("10.") ||
            address.startsWith("127.") ||
            address == "localhost" ||
            address.matches(Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\..*"))
    }

    private fun kotlinx.serialization.json.JsonElement?.asBoolean(): Boolean {
        val primitive = this as? JsonPrimitive ?: return false
        val content = primitive.content
        return content.equals("true", ignoreCase = true) || content == "1"
    }
}
