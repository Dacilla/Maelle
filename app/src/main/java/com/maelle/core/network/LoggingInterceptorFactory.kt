package com.maelle.core.network

import com.maelle.core.logging.AuthTokenRedactor
import com.maelle.core.logging.RedactingLogger
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.logging.HttpLoggingInterceptor

@Singleton
class LoggingInterceptorFactory @Inject constructor(
    private val logger: RedactingLogger,
    private val tokenRedactor: AuthTokenRedactor,
) {

    fun create(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            logger.d(component = "Http", message = tokenRedactor.redact(message))
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("X-Plex-Token")
            redactHeader("Authorization")
        }
    }
}
