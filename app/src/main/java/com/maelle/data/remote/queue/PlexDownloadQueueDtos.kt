package com.maelle.data.remote.queue

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexDownloadQueueResponse(
    @SerialName("MediaContainer")
    val mediaContainer: PlexDownloadQueueContainer = PlexDownloadQueueContainer(),
)

@Serializable
data class PlexDownloadQueueContainer(
    @SerialName("DownloadQueue")
    val queues: List<PlexDownloadQueueDto> = emptyList(),
)

@Serializable
data class PlexDownloadQueueDto(
    @SerialName("id")
    val id: Long,
    @SerialName("status")
    val status: String? = null,
    @SerialName("itemCount")
    val itemCount: Int? = null,
)

@Serializable
data class PlexDownloadQueueItemsResponse(
    @SerialName("MediaContainer")
    val mediaContainer: PlexDownloadQueueItemsContainer = PlexDownloadQueueItemsContainer(),
)

@Serializable
data class PlexDownloadQueueItemsContainer(
    @SerialName("DownloadQueueItem")
    val items: List<PlexDownloadQueueItemDto> = emptyList(),
)

@Serializable
data class PlexDownloadQueueItemDto(
    @SerialName("id")
    val id: Long,
    @SerialName("queueId")
    val queueId: Long,
    @SerialName("key")
    val key: String,
    @SerialName("status")
    val status: String,
    @SerialName("error")
    val error: String? = null,
)
