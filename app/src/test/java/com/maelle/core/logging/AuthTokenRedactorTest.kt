package com.maelle.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthTokenRedactorTest {

    private val redactor = AuthTokenRedactor()

    @Test
    fun `plain text without tokens passes through`() {
        val input = "Fetched 12 sections from server Bridge"

        assertEquals(input, redactor.redact(input))
    }

    @Test
    fun `redacts X-Plex-Token query parameter`() {
        val redacted = redactor.redact("GET https://plex.tv/api/v2/pins?strong=false&X-Plex-Token=abcdef123456")

        assertFalse(redacted.contains("abcdef123456"))
        assertEquals(
            "GET https://plex.tv/api/v2/pins?strong=false&X-Plex-Token=<redacted:3456>",
            redacted,
        )
    }

    @Test
    fun `redacts lowercase token query parameter`() {
        val redacted = redactor.redact("https://host/path?token=SuperSecretValue&x=1")

        assertFalse(redacted.contains("SuperSecretValue"))
        assertEquals("https://host/path?token=<redacted:ALUE>&x=1", redacted)
    }

    @Test
    fun `redaction is idempotent when both query and header rules match`() {
        val url = "https://host/library/parts/1/0/file.bin?download=1&X-Plex-Token=abcdef123456"

        val once = redactor.redact(url)
        val twice = redactor.redact(once)

        assertEquals(once, twice)
        assertFalse(twice.contains("abcdef123456"))
    }

    @Test
    fun `redacts X-Plex-Token header value`() {
        val redacted = redactor.redact("X-Plex-Token: plex-is-great sent=ok")

        assertFalse(redacted.contains("plex-is-great"))
        assertEquals("X-Plex-Token: <redacted:REAT> sent=ok", redacted)
    }

    @Test
    fun `redacts Authorization bearer value while keeping the scheme`() {
        val redacted = redactor.redact("Authorization: Bearer abc123def456 and more text")

        assertFalse(redacted.contains("abc123def456"))
        assertEquals("Authorization: Bearer <redacted:F456> and more text", redacted)
    }

    @Test
    fun `redacts Authorization header without bearer scheme`() {
        val redacted = redactor.redact("Authorization: abc123def456")

        assertFalse(redacted.contains("abc123def456"))
        assertEquals("Authorization: <redacted:F456>", redacted)
    }
}
