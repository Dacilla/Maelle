package com.maelle.data.remote.auth

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import okhttp3.ResponseBody
import retrofit2.Response

interface PlexAuthService {

    @POST("api/v2/pins")
    suspend fun createPin(): PlexPinResponse

    @GET("api/v2/pins/{pinId}")
    suspend fun getPinStatus(
        @Path("pinId") pinId: Int,
    ): Response<ResponseBody>

    @GET("api/v2/user")
    suspend fun getUser(
        @Header("X-Plex-Token") userToken: String,
    ): Response<ResponseBody>
}
