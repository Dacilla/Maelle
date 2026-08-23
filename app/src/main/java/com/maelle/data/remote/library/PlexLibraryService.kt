package com.maelle.data.remote.library

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface PlexLibraryService {

    @GET("library/sections")
    suspend fun getSections(
        @Header("X-Plex-Token") serverToken: String,
    ): PlexLibrarySectionsResponse

    @GET("library/sections/{sectionKey}/all")
    suspend fun getSectionItems(
        @Path("sectionKey") sectionKey: String,
        @Header("X-Plex-Token") serverToken: String,
    ): PlexLibraryItemsResponse

    @GET("hubs/search")
    suspend fun searchHubs(
        @Query("query") query: String,
        @Query("limit") limit: Int = 30,
        @Header("X-Plex-Token") serverToken: String,
    ): PlexSearchResponse

    @GET
    suspend fun getItemsByPath(
        @Url path: String,
        @Header("X-Plex-Token") serverToken: String,
    ): PlexLibraryItemsResponse

    @GET("library/metadata/{ratingKey}")
    suspend fun getMetadata(
        @Path("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") serverToken: String,
    ): PlexMetadataResponse
}
