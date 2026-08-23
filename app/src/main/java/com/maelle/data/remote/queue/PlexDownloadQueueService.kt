package com.maelle.data.remote.queue

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PlexDownloadQueueService {

    @POST("downloadQueue")
    suspend fun getOrCreateQueue(
        @Header("X-Plex-Token") serverToken: String,
    ): PlexDownloadQueueResponse

    @POST("downloadQueue/{queueId}/add")
    suspend fun addToQueue(
        @Path("queueId") queueId: Long,
        @Query("keys") keys: String,
        @Query("videoResolution") videoResolution: String? = null,
        @Query("videoBitrate") videoBitrate: Int? = null,
        @Query("videoQuality") videoQuality: Int? = null,
        @Header("X-Plex-Token") serverToken: String,
    ): PlexDownloadQueueItemsResponse

    @GET("downloadQueue/{queueId}/items")
    suspend fun listQueueItems(
        @Path("queueId") queueId: Long,
        @Header("X-Plex-Token") serverToken: String,
    ): PlexDownloadQueueItemsResponse

    @GET("downloadQueue/{queueId}/items/{itemId}")
    suspend fun getQueueItem(
        @Path("queueId") queueId: Long,
        @Path("itemId") itemId: Long,
        @Header("X-Plex-Token") serverToken: String,
    ): PlexDownloadQueueItemsResponse
}
