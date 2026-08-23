package com.maelle.data.repository

import com.maelle.data.local.dao.DownloadJobDao
import com.maelle.data.local.entity.DownloadJobEntity
import com.maelle.domain.downloads.model.DownloadState
import com.maelle.domain.downloads.model.DownloadStrategy
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadJobRepositoryTest {

    private class FakeDownloadJobDao : DownloadJobDao {
        val storage = LinkedHashMap<String, DownloadJobEntity>()
        private val flow = MutableStateFlow<List<DownloadJobEntity>>(emptyList())

        private fun publish() {
            flow.value = storage.values.sortedByDescending { it.updatedAtEpochMs }
        }

        override fun observeAll(): Flow<List<DownloadJobEntity>> = flow

        override suspend fun getById(jobId: String): DownloadJobEntity? = storage[jobId]

        override suspend fun getAll(): List<DownloadJobEntity> = storage.values.toList()

        override suspend fun upsert(job: DownloadJobEntity) {
            storage[job.jobId] = job
            publish()
        }
    }

    private lateinit var dao: FakeDownloadJobDao
    private lateinit var repository: DownloadJobRepository
    private lateinit var tempDir: java.nio.file.Path

    @Before
    fun setUp() {
        dao = FakeDownloadJobDao()
        repository = DownloadJobRepository(dao)
        tempDir = Files.createTempDirectory("maelle-repo-test")
    }

    @After
    fun tearDown() {
        Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    private fun job(
        jobId: String,
        state: DownloadState,
        strategy: DownloadStrategy = DownloadStrategy.Direct,
        localFilePath: String? = null,
        artifactBytes: Long? = null,
        bytesDownloaded: Long = 0L,
    ) = DownloadJobEntity(
        jobId = jobId,
        mediaKey = "rating-$jobId",
        mediaTitle = "Title $jobId",
        mediaSecondaryTitle = null,
        serverId = "server-1",
        strategy = strategy,
        state = state,
        requestedQuality = "Original",
        queueId = null,
        queueItemId = null,
        bytesDownloaded = bytesDownloaded,
        bytesTotal = null,
        localFilePath = localFilePath,
        localFileName = localFilePath?.let { it.substringAfterLast('/') },
        artifactMimeType = "video/mp4",
        artifactBytes = artifactBytes,
        errorCategory = null,
        errorMessage = null,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 1_000L,
    )

    @Test
    fun `createPlannedJob starts queued with zero progress`() = runTest {
        val created = repository.createPlannedJob(
            mediaKey = "12345",
            mediaTitle = "A Movie",
            mediaSecondaryTitle = null,
            serverId = "server-1",
            strategy = DownloadStrategy.Queue,
            requestedQuality = "720p",
        )

        assertEquals(DownloadState.Queued, created.state)
        assertEquals(0L, created.bytesDownloaded)
        assertNull(created.bytesTotal)
        assertNull(created.localFilePath)
        assertNotNull(repository.getJob(created.jobId))
    }

    @Test
    fun `updateState records categorized errors`() = runTest {
        repository.upsert(job("j1", DownloadState.Downloading))

        repository.updateState(
            jobId = "j1",
            state = DownloadState.Failed,
            errorCategory = "direct_download_failed",
            errorMessage = "HTTP 500 while downloading media part",
        )

        val stored = repository.getJob("j1")
        assertEquals(DownloadState.Failed, stored?.state)
        assertEquals("direct_download_failed", stored?.errorCategory)
        assertEquals("HTTP 500 while downloading media part", stored?.errorMessage)
    }

    @Test
    fun `prepareForRetry clears errors but preserves transfer progress`() = runTest {
        repository.upsert(
            job("j2", DownloadState.Failed, bytesDownloaded = 4096L).copy(
                errorCategory = "direct_download_failed",
                errorMessage = "boom",
                bytesTotal = 10000L,
            ),
        )

        repository.prepareForRetry("j2")

        val retried = repository.getJob("j2")
        assertEquals(DownloadState.Queued, retried?.state)
        assertNull(retried?.errorCategory)
        assertNull(retried?.errorMessage)
        assertEquals(4096L, retried?.bytesDownloaded)
        assertEquals(10000L, retried?.bytesTotal)
    }

    @Test
    fun `markCompletedWithArtifact records verified file`() = runTest {
        repository.upsert(job("j3", DownloadState.Downloading, bytesDownloaded = 5L))
        val path = Files.createTempFile(tempDir, "artifact", ".mp4")
        Files.write(path, ByteArray(64))

        repository.markCompletedWithArtifact(
            jobId = "j3",
            filePath = path.toString(),
            fileName = "artifact.mp4",
            mimeType = "video/mp4",
            bytesDownloaded = 64L,
            bytesTotal = null,
        )

        val completed = repository.getJob("j3")!!
        assertEquals(DownloadState.Completed, completed.state)
        assertEquals(64L, completed.artifactBytes)
        assertEquals(path.toString(), completed.localFilePath)
    }

    @Test
    fun `reconcile requeues interrupted downloads for automatic resume`() = runTest {
        repository.upsert(job("downloading", DownloadState.Downloading, bytesDownloaded = 999L))
        repository.upsert(job("waiting", DownloadState.WaitingForServer))
        repository.upsert(job("preparing", DownloadState.Preparing))

        val resumable = repository.reconcilePersistedJobs()

        assertEquals(setOf("downloading", "waiting", "preparing"), resumable.toSet())
        resumable.forEach { id ->
            val resumed = repository.getJob(id)!!
            assertEquals(DownloadState.Queued, resumed.state)
        }
        val resumedWithProgress = repository.getJob("downloading")!!
        assertEquals(999L, resumedWithProgress.bytesDownloaded)
    }

    @Test
    fun `reconcile flags completed jobs whose artifact vanished`() = runTest {
        repository.upsert(
            job(
                jobId = "ghost",
                state = DownloadState.Completed,
                localFilePath = tempDir.resolve("missing.mp4").toString(),
                artifactBytes = 128L,
            ),
        )

        val resumable = repository.reconcilePersistedJobs()

        assertTrue(resumable.isEmpty())
        val flagged = repository.getJob("ghost")!!
        assertEquals(DownloadState.NeedsReconciliation, flagged.state)
        assertEquals("artifact_missing", flagged.errorCategory)
    }

    @Test
    fun `reconcile corrects completed artifacts whose size drifted`() = runTest {
        val path = Files.createTempFile(tempDir, "drifted", ".mp4")
        Files.write(path, ByteArray(50))
        repository.upsert(
            job(
                jobId = "drift",
                state = DownloadState.Completed,
                localFilePath = path.toString(),
                artifactBytes = 500L,
            ),
        )

        val resumable = repository.reconcilePersistedJobs()

        assertTrue(resumable.isEmpty())
        val corrected = repository.getJob("drift")!!
        assertEquals(DownloadState.Completed, corrected.state)
        assertEquals(50L, corrected.artifactBytes)
        assertEquals(50L, corrected.bytesDownloaded)
    }

    @Test
    fun `updateProgress ignores writes for paused jobs`() = runTest {
        repository.upsert(
            job("paused", DownloadState.Paused, bytesDownloaded = 2048L).copy(
                errorMessage = "Paused. Resume to continue where it stopped.",
            ),
        )

        repository.updateProgress(
            jobId = "paused",
            state = DownloadState.Downloading,
            bytesDownloaded = 4096L,
            bytesTotal = 10000L,
        )

        val stored = repository.getJob("paused")!!
        assertEquals(DownloadState.Paused, stored.state)
        assertEquals(2048L, stored.bytesDownloaded)
    }

    @Test
    fun `reconcile leaves healthy completed and failed jobs alone`() = runTest {
        val path = Files.createTempFile(tempDir, "healthy", ".mp4")
        Files.write(path, ByteArray(32))
        repository.upsert(
            job(
                jobId = "healthy",
                state = DownloadState.Completed,
                localFilePath = path.toString(),
                artifactBytes = 32L,
            ),
        )
        repository.upsert(job("failed", DownloadState.Failed).copy(errorCategory = "queue_failed"))

        val resumable = repository.reconcilePersistedJobs()

        assertTrue(resumable.isEmpty())
        assertEquals(DownloadState.Completed, repository.getJob("healthy")!!.state)
        val untouchedFailure = repository.getJob("failed")!!
        assertEquals(DownloadState.Failed, untouchedFailure.state)
        assertEquals("queue_failed", untouchedFailure.errorCategory)
    }
}
