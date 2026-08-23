package com.maelle.data.repository

import com.maelle.core.network.PlexServerServiceFactory
import com.maelle.data.remote.queue.PlexDownloadQueueItemDto
import com.maelle.data.remote.queue.PlexDownloadQueueService
import java.util.UUID
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

    companion object {
        private const val DEFAULT_BITRATE_KBPS = 4000

        fun buildClientProfileExtra(profile: QueueProfile): String {
            val bitrate = profile.videoBitrate ?: DEFAULT_BITRATE_KBPS
            val limitation = buildString {
                append("video.bitrate=")
                append(bitrate)
                if (profile.videoResolution != null) {
                    append("&video.width=")
                    append(profile.videoResolution.substringBefore('x'))
                    append("&video.height=")
                    append(profile.videoResolution.substringAfter('x'))
                }
            }
            return listOf(
                "add-transcode-target(" +
                    "type=videoProfile&context=static&protocol=http" +
                    "&container=mp4&videoCodec=h264&audioCodec=aac,mp3" +
                    "&subtitleCodec=srt,ass&replace=true)",
                "add-limitation(" +
                    "scope=videoCodec&scopeName=h264&type=upperBound" +
                    "&name=$limitation)",
                "add-direct-play-profile(" +
                    "type=videoProfile&container=mp4" +
                    "&videoCodec=h264&audioCodec=aac,mp3&subtitleCodec=srt,ass)",
            ).joinToString("+")
        }

        fun profileForQuality(requestedQuality: String): QueueProfile {
            return when (requestedQuality) {
                "1080p" -> QueueProfile("1920x1080", 10000, 100)
                "720p" -> QueueProfile("1280x720", 4000, 75)
                "480p" -> QueueProfile("720x480", 1500, 60)
                else -> QueueProfile("1280x720", 4000, 75)
            }
        }
    }

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
        burnSubtitles: Boolean,
    ): PlexDownloadQueueItemDto? {
        val service = createService(connectionUri)
        val sessionId = UUID.randomUUID().toString().replace("-", "")
        val response = service.addToQueue(
            queueId = queueId,
            keys = "/library/metadata/$mediaKey",
            path = "/library/metadata/$mediaKey",
            session = sessionId,
            transcodeSessionId = sessionId,
            directPlay = 1,
            directStream = 1,
            directStreamAudio = 1,
            protocol = "http",
            context = "static",
            location = "wan",
            fastSeek = 1,
            mediaIndex = -1,
            partIndex = -1,
            transcodeType = "video",
            maxVideoBitrate = profile.videoBitrate ?: DEFAULT_BITRATE_KBPS,
            videoBitrate = profile.videoBitrate ?: DEFAULT_BITRATE_KBPS,
            videoResolution = profile.videoResolution,
            subtitles = if (burnSubtitles) "burn" else "auto",
            subtitleSize = 100,
            clientProfileExtra = buildClientProfileExtra(profile),
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
