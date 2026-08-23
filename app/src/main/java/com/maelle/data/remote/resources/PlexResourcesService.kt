package com.maelle.data.remote.resources

import retrofit2.http.GET
import retrofit2.http.Query

interface PlexResourcesService {

    @GET("api/v2/resources")
    suspend fun getResources(
        @Query("X-Plex-Token") userToken: String,
        @Query("includeHttps") includeHttps: Int = 1,
        @Query("includeRelay") includeRelay: Int = 1,
        @Query("includeIPv6") includeIpv6: Int = 1,
    ): List<PlexResourceDto>
}
