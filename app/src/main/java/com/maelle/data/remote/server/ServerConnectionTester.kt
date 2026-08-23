package com.maelle.data.remote.server

import com.maelle.core.logging.RedactingLogger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class ServerConnectionTester @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val logger: RedactingLogger,
) {

    private val probeClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .writeTimeout(1500, TimeUnit.MILLISECONDS)
            .callTimeout(2500, TimeUnit.MILLISECONDS)
            .build()
    }

    suspend fun test(uri: String, accessToken: String): Int {
        return withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            runCatching {
                val request = Request.Builder()
                    .url(uri.identityUrl())
                    .header("X-Plex-Token", accessToken)
                    .get()
                    .build()
                probeClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        logger.w(
                            component = "ServerConnectionTester",
                            message = "Connection rejected for $uri with status ${response.code}",
                        )
                        return@withContext -1
                    }
                }
                (System.currentTimeMillis() - startedAt).toInt()
            }.getOrElse { throwable ->
                logger.w(
                    component = "ServerConnectionTester",
                    message = "Connection test failed for $uri",
                    throwable = throwable,
                )
                -1
            }
        }
    }

    private fun String.identityUrl(): String {
        val base = if (endsWith("/")) dropLast(1) else this
        return "$base/identity"
    }
}
