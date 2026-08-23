package com.maelle.data.repository

import org.junit.Assert.assertEquals
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
}
