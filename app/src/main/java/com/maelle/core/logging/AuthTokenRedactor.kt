package com.maelle.core.logging

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthTokenRedactor @Inject constructor() {

    fun redact(input: String): String {
        var redacted = input
        redacted = redactQueryParameter(redacted, "X-Plex-Token")
        redacted = redactQueryParameter(redacted, "token")
        redacted = redactHeaderValue(redacted, "X-Plex-Token")
        redacted = redactHeaderValue(redacted, "Authorization")
        return redacted
    }

    private fun redactQueryParameter(input: String, key: String): String {
        val pattern = Regex("(?i)([?&]$key=)([^&#\\s]+)")
        return input.replace(pattern) { match ->
            val value = URLDecoder.decode(match.groupValues[2], StandardCharsets.UTF_8)
            "${match.groupValues[1]}${masked(value)}"
        }
    }

    private fun redactHeaderValue(input: String, key: String): String {
        val pattern = Regex("(?i)($key\\s*[:=]\\s*)([^\\s,]+)")
        return input.replace(pattern) { match ->
            "${match.groupValues[1]}${masked(match.groupValues[2])}"
        }
    }

    private fun masked(value: String): String {
        if (value.isBlank()) {
            return "<redacted>"
        }
        val suffix = value.takeLast(minOf(4, value.length)).uppercase(Locale.US)
        return "<redacted:$suffix>"
    }
}
