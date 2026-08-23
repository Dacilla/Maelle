package com.maelle.data.repository

import com.maelle.data.remote.library.PlexStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTrackBuilderTest {

    private fun stream(
        id: Int,
        streamType: Int? = 3,
        key: String? = "/library/streams/$id",
        format: String? = "srt",
        languageTag: String? = null,
        title: String? = null,
    ) = PlexStreamDto(
        id = id,
        streamType = streamType,
        key = key,
        title = title,
        languageCode = null,
        languageTag = languageTag,
        format = format,
    )

    @Test
    fun `builds tracks for external subtitle streams`() {
        val tracks = PlexLibraryRepository.buildSubtitleTracks(
            streams = listOf(stream(10, languageTag = "eng", title = "English")),
            baseUrl = "https://server",
        )

        assertEquals(1, tracks.size)
        val track = tracks.first()
        assertEquals("https://server/library/streams/10?download=1", track.url)
        assertEquals("eng_English", track.label)
        assertEquals("srt", track.format)
        assertEquals("eng", track.languageCode)
    }

    @Test
    fun `ignores embedded streams and non-subtitle streams`() {
        val tracks = PlexLibraryRepository.buildSubtitleTracks(
            streams = listOf(
                stream(1, streamType = 3, key = null),
                stream(2, streamType = 2),
                stream(3, streamType = 1, key = "/library/streams/3"),
                stream(4, languageTag = "fre"),
            ),
            baseUrl = "https://server",
        )

        assertEquals(1, tracks.size)
        assertEquals("https://server/library/streams/4", tracks.first().url.substringBefore("?"))
    }

    @Test
    fun `sanitizes unsafe characters in labels`() {
        val tracks = PlexLibraryRepository.buildSubtitleTracks(
            streams = listOf(stream(7, languageTag = "es 419 / latino")),
            baseUrl = "https://server",
        )

        assertEquals("es_419___latino", tracks.first().label)
    }

    @Test
    fun `defaults label and format when absent`() {
        val tracks = PlexLibraryRepository.buildSubtitleTracks(
            streams = listOf(stream(9, format = null)),
            baseUrl = "https://s",
        )

        assertEquals("subtitle-9", tracks.first().label)
        assertEquals("srt", tracks.first().format)
    }

    @Test
    fun `labels combine language and title when both present`() {
        val tracks = PlexLibraryRepository.buildSubtitleTracks(
            streams = listOf(
                stream(1, languageTag = "jpn"),
                stream(2, title = "Forced Narrative"),
            ),
            baseUrl = "https://server",
        )

        assertEquals(listOf("jpn", "Forced_Narrative"), tracks.map { it.label })
        assertTrue(tracks.all { it.format == "srt" })
    }
}
