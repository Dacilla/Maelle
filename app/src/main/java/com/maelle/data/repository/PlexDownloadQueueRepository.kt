package com.maelle.data.repository

import com.maelle.core.network.PlexServerServiceFactory
import com.maelle.data.remote.queue.PlexDownloadQueueItemDto
import com.maelle.data.remote.queue.PlexDownloadQueueService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexDownloadQueueRepository @Inject constructor(
    private val plexServerServiceFactory: PlexServerServiceFactory,
) {

    data class QueueProfile(
        val videoResolution: String?,
        val videoBitrate: Int?,
        val videoQuality: Int?,
    )

    suspend fun getOrCreateQueue(
        connectionUri: String,
        serverAccessToken: String,
    ): Long {
        val service = createService(connectionUri)
        return service.getOrCreateQueue(serverToken = serverAccessToken)
            .mediaContainer
            .queues
            .first()
            .id
    }

    suspend fun addToQueue(
        connectionUri: String,
        serverAccessToken: String,
        queueId: Long,
        mediaKey: String,
        profile: QueueProfile,
    ): PlexDownloadQueueItemDto? {
        val service = createService(connectionUri)
        val response = service.addToQueue(
            queueId = queueId,
            keys = "/library/metadata/$mediaKey",
            videoResolution = profile.videoResolution,
            videoBitrate = profile.videoBitrate,
            videoQuality = profile.videoQuality,
            serverToken = serverAccessToken,
        )
        return response.mediaContainer.items.firstOrNull()
            ?: service.listQueueItems(
                queueId = queueId,
                serverToken = serverAccessToken,
            ).mediaContainer.items.firstOrNull { item ->
                item.key == "/library/metadata/$mediaKey"
            }
    }

    suspend fun getQueueItem(
        connectionUri: String,
        serverAccessToken: String,
        queueId: Long,
        itemId: Long,
    ): PlexDownloadQueueItemDto? {
        val service = createService(connectionUri)
        return service.getQueueItem(
            queueId = queueId,
            itemId = itemId,
            serverToken = serverAccessToken,
        ).mediaContainer.items.firstOrNull()
    }

    fun buildMediaUrl(connectionUri: String, queueId: Long, itemId: Long): String {
        val baseUrl = if (connectionUri.endsWith("/")) connectionUri.dropLast(1) else connectionUri
        return "$baseUrl/downloadQueue/$queueId/item/$itemId/media"
    }

    private fun createService(connectionUri: String): PlexDownloadQueueService {
        return plexServerServiceFactory.create(
            baseUrl = connectionUri,
            serviceClass = PlexDownloadQueueService::class.java,
        )
    }
}
