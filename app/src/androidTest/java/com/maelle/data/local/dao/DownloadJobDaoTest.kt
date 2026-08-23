package com.maelle.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maelle.data.local.database.MaelleDatabase
import com.maelle.data.local.entity.DownloadJobEntity
import com.maelle.domain.downloads.model.DownloadState
import com.maelle.domain.downloads.model.DownloadStrategy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadJobDaoTest {

    private lateinit var database: MaelleDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MaelleDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun job(
        jobId: String,
        state: DownloadState = DownloadState.Queued,
        updatedAt: Long = 1_000L,
    ) = DownloadJobEntity(
        jobId = jobId,
        mediaKey = "key-$jobId",
        mediaTitle = "Title $jobId",
        mediaSecondaryTitle = null,
        serverId = "server-1",
        strategy = DownloadStrategy.Direct,
        state = state,
        requestedQuality = "Original",
        queueId = null,
        queueItemId = null,
        bytesDownloaded = 0L,
        bytesTotal = null,
        localFilePath = null,
        localFileName = null,
        artifactMimeType = null,
        artifactBytes = null,
        errorCategory = null,
        errorMessage = null,
        createdAtEpochMs = 500L,
        updatedAtEpochMs = updatedAt,
    )

    @Test
    fun upsertReplacesExistingRowById() = runTest {
        val dao = database.downloadJobDao()
        dao.upsert(job("j-1", DownloadState.Downloading))
        dao.upsert(job("j-1", DownloadState.Completed, updatedAt = 2_000L))

        val stored = dao.getById("j-1")
        assertEquals(DownloadState.Completed, stored?.state)
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun getByIdReturnsNullForUnknownJob() = runTest {
        assertNull(database.downloadJobDao().getById("missing"))
    }

    @Test
    fun getAllOrdersByUpdatedAtDescending() = runTest {
        val dao = database.downloadJobDao()
        dao.upsert(job("old", updatedAt = 1_000L))
        dao.upsert(job("newest", updatedAt = 9_000L))
        dao.upsert(job("middle", updatedAt = 5_000L))

        val order = dao.getAll().map { it.jobId }
        assertEquals(listOf("newest", "middle", "old"), order)
    }

    @Test
    fun observeAllEmitsCurrentRows() = runTest {
        val dao = database.downloadJobDao()
        dao.upsert(job("j-1"))
        dao.upsert(job("j-2"))

        val ids = dao.observeAll().first().map { it.jobId }.sorted()
        assertEquals(listOf("j-1", "j-2"), ids)
    }
}
