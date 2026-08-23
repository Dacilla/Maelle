package com.maelle.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueProfileMappingTest {

    @Test
    fun `maps known quality presets`() {
        assertEquals(
            PlexDownloadQueueRepository.QueueProfile("1920x1080", 10000, 100),
            PlexDownloadQueueRepository.profileForQuality("1080p"),
        )
        assertEquals(
            PlexDownloadQueueRepository.QueueProfile("1280x720", 4000, 75),
            PlexDownloadQueueRepository.profileForQuality("720p"),
        )
        assertEquals(
            PlexDownloadQueueRepository.QueueProfile("720x480", 1500, 60),
            PlexDownloadQueueRepository.profileForQuality("480p"),
        )
    }

    @Test
    fun `falls back to 720p for unknown qualities`() {
        assertEquals(
            PlexDownloadQueueRepository.QueueProfile("1280x720", 4000, 75),
            PlexDownloadQueueRepository.profileForQuality("4k-ultra"),
        )
        assertEquals(
            PlexDownloadQueueRepository.QueueProfile("1280x720", 4000, 75),
            PlexDownloadQueueRepository.profileForQuality(""),
        )
    }

    @Test
    fun `client profile extra carries resolution and bitrate constraints`() {
        val extra = PlexDownloadQueueRepository.buildClientProfileExtra(
            PlexDownloadQueueRepository.QueueProfile("1920x1080", 10000, null),
        )

        assertTrue(extra.startsWith("add-transcode-target(type=videoProfile&context=static&protocol=http"))
        assertTrue(extra.contains("&container=mp4&"))
        assertTrue(extra.contains("videoCodec=h264"))
        assertTrue(extra.contains("audioCodec=aac,mp3"))
        assertTrue(extra.contains("subtitleCodec=srt,ass&replace=true)"))
        assertTrue(extra.contains("add-limitation(scope=videoCodec&scopeName=h264&type=upperBound"))
        assertTrue(extra.contains("video.bitrate=10000&video.width=1920&video.height=1080"))
        assertTrue(extra.endsWith("+add-direct-play-profile(type=videoProfile&container=mp4&videoCodec=h264&audioCodec=aac,mp3&subtitleCodec=srt,ass)"))
    }
}
