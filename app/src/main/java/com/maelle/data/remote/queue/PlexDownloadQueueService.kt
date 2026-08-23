package com.maelle.data.remote.queue

import retrofit2.http.DELETE
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
        @Query("path") path: String,
        @Query("session") session: String,
        @Query("transcodeSessionId") transcodeSessionId: String,
        @Query("directPlay") directPlay: Int,
        @Query("directStream") directStream: Int,
        @Query("directStreamAudio") directStreamAudio: Int,
        @Query("protocol") protocol: String,
        @Query("context") context: String,
        @Query("location") location: String,
        @Query("fastSeek") fastSeek: Int,
        @Query("mediaIndex") mediaIndex: Int,
        @Query("partIndex") partIndex: Int,
        @Query("transcodeType") transcodeType: String,
        @Query("maxVideoBitrate") maxVideoBitrate: Int,
        @Query("videoBitrate") videoBitrate: Int,
        @Query("videoResolution") videoResolution: String?,
        @Query("subtitles") subtitles: String,
        @Query("subtitleSize") subtitleSize: Int,
        @Query("X-Plex-Client-Profile-Extra") clientProfileExtra: String?,
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
