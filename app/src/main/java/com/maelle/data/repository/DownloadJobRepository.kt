package com.maelle.data.repository

import com.maelle.data.local.dao.DownloadJobDao
import com.maelle.data.local.entity.DownloadJobEntity
import com.maelle.domain.downloads.model.DownloadState
import com.maelle.domain.downloads.model.DownloadStrategy
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Singleton
class DownloadJobRepository @Inject constructor(
    private val downloadJobDao: DownloadJobDao,
) {

    fun observeJobs(): Flow<List<DownloadJobEntity>> = downloadJobDao.observeAll()

    suspend fun upsert(job: DownloadJobEntity) {
        downloadJobDao.upsert(job)
    }

    suspend fun getJob(jobId: String): DownloadJobEntity? = downloadJobDao.getById(jobId)

    suspend fun createPlannedJob(
        mediaKey: String,
        mediaTitle: String,
        mediaSecondaryTitle: String?,
        serverId: String,
        strategy: DownloadStrategy,
        requestedQuality: String,
        burnSubtitles: Boolean = false,
    ): DownloadJobEntity {
        val now = System.currentTimeMillis()
        val job = DownloadJobEntity(
            jobId = UUID.randomUUID().toString(),
            mediaKey = mediaKey,
            mediaTitle = mediaTitle,
            mediaSecondaryTitle = mediaSecondaryTitle,
            serverId = serverId,
            strategy = strategy,
            state = DownloadState.Queued,
            requestedQuality = requestedQuality,
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
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            burnSubtitles = burnSubtitles,
        )
        downloadJobDao.upsert(job)
        return job
    }

    suspend fun updateState(
        jobId: String,
        state: DownloadState,
        errorCategory: String? = null,
        errorMessage: String? = null,
    ) {
        val current = downloadJobDao.getById(jobId) ?: return
        downloadJobDao.upsert(
            current.copy(
                state = state,
                errorCategory = errorCategory,
                errorMessage = errorMessage,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateQueueTracking(
        jobId: String,
        queueId: String,
        queueItemId: String,
        state: DownloadState,
    ) {
        val current = downloadJobDao.getById(jobId) ?: return
        downloadJobDao.upsert(
            current.copy(
                queueId = queueId,
                queueItemId = queueItemId,
                state = state,
                errorCategory = null,
                errorMessage = null,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateProgress(
        jobId: String,
        state: DownloadState,
        bytesDownloaded: Long,
        bytesTotal: Long?,
    ) {
        val current = downloadJobDao.getById(jobId) ?: return
        if (current.state == DownloadState.Paused) {
            return
        }
        downloadJobDao.upsert(
            current.copy(
                state = state,
                bytesDownloaded = bytesDownloaded,
                bytesTotal = bytesTotal,
                errorCategory = null,
                errorMessage = null,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun prepareForRetry(jobId: String) {
        val current = downloadJobDao.getById(jobId) ?: return
        downloadJobDao.upsert(
            current.copy(
                state = DownloadState.Queued,
                errorCategory = null,
                errorMessage = null,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setTransferArtifact(
        jobId: String,
        filePath: String,
        fileName: String,
        mimeType: String?,
    ) {
        val current = downloadJobDao.getById(jobId) ?: return
        downloadJobDao.upsert(
            current.copy(
                localFilePath = filePath,
                localFileName = fileName,
                artifactMimeType = mimeType,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun markCompletedWithArtifact(
        jobId: String,
        filePath: String,
        fileName: String,
        mimeType: String?,
        bytesDownloaded: Long,
        bytesTotal: Long?,
    ) {
        val current = downloadJobDao.getById(jobId) ?: return
        downloadJobDao.upsert(
            current.copy(
                state = DownloadState.Completed,
                bytesDownloaded = bytesDownloaded,
                bytesTotal = bytesTotal ?: bytesDownloaded,
                localFilePath = filePath,
                localFileName = fileName,
                artifactMimeType = mimeType,
                artifactBytes = bytesDownloaded,
                errorCategory = null,
                errorMessage = null,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun reconcilePersistedJobs(): List<String> {
        val jobsToResume = mutableListOf<String>()
        val jobs = downloadJobDao.getAll()
        jobs.forEach { job ->
            when (job.state) {
                DownloadState.Preparing,
                DownloadState.Downloading,
                DownloadState.WaitingForServer,
                -> {
                    updateState(
                        jobId = job.jobId,
                        state = DownloadState.Queued,
                        errorCategory = null,
                        errorMessage = "Interrupted by app shutdown; resuming automatically.",
                    )
                    jobsToResume += job.jobId
                }

                DownloadState.Completed -> {
                    val filePath = job.localFilePath
                    if (filePath.isNullOrBlank()) {
                        updateState(
                            jobId = job.jobId,
                            state = DownloadState.NeedsReconciliation,
                            errorCategory = "artifact_missing",
                            errorMessage = "Completed download has no recorded artifact path.",
                        )
                    } else {
                        val file = File(filePath)
                        if (!file.exists()) {
                            updateState(
                                jobId = job.jobId,
                                state = DownloadState.NeedsReconciliation,
                                errorCategory = "artifact_missing",
                                errorMessage = "Completed download artifact is missing from disk.",
                            )
                        } else if (job.artifactBytes != file.length()) {
                            markCompletedWithArtifact(
                                jobId = job.jobId,
                                filePath = file.absolutePath,
                                fileName = job.localFileName ?: file.name,
                                mimeType = job.artifactMimeType,
                                bytesDownloaded = file.length(),
                                bytesTotal = job.bytesTotal ?: file.length(),
                            )
                        }
                    }
                }

                else -> Unit
            }
        }
        return jobsToResume
    }
}
