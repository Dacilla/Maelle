package com.maelle.core.network

import android.os.Build
import com.maelle.BuildConfig
import com.maelle.core.device.InstallIdProvider
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class PlexHeadersInterceptor @Inject constructor(
    private val installIdProvider: InstallIdProvider,
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("X-Plex-Client-Identifier", installIdProvider.get())
            .header("X-Plex-Product", "Maelle")
            .header("X-Plex-Version", BuildConfig.VERSION_NAME)
            .header("X-Plex-Platform", "Android")
            .header("X-Plex-Platform-Version", Build.VERSION.RELEASE ?: "unknown")
            .header("X-Plex-Device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .build()
        return chain.proceed(request)
    }
}
