package com.maelle.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.maelle.core.logging.RedactingLogger
import com.maelle.core.notifications.DownloadNotifier
import com.maelle.data.repository.DownloadJobRepository
import com.maelle.data.repository.PlexDownloadQueueRepository
import com.maelle.data.repository.PlexServerRepository
import com.maelle.domain.downloads.model.DownloadState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import java.net.URLConnection
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltWorker
class QueueDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadJobRepository: DownloadJobRepository,
    private val plexServerRepository: PlexServerRepository,
    private val plexDownloadQueueRepository: PlexDownloadQueueRepository,
    private val okHttpClient: OkHttpClient,
    private val notifier: DownloadNotifier,
    private val logger: RedactingLogger,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val job = downloadJobRepository.getJob(jobId) ?: return Result.failure()
        if (job.strategy != com.maelle.domain.downloads.model.DownloadStrategy.Queue) {
            return Result.success()
        }

        promoteToForeground(jobId = jobId, title = job.displayTitle(), label = "Preparing")

        if (runAttemptCount > MAX_ATTEMPTS) {
            downloadJobRepository.updateState(
                jobId = jobId,
                state = DownloadState.Failed,
                errorCategory = "retries_exhausted",
                errorMessage = "Server did not finish preparing this download within $MAX_ATTEMPTS attempts. Retry to submit it again.",
            )
            notifier.notifyFailed(jobId, job.displayTitle(), "Server did not finish preparing")
            return Result.failure()
        }

        val serverContext = plexServerRepository.getServerDownloadContext(job.serverId)
        if (serverContext == null) {
            downloadJobRepository.updateState(
                jobId = jobId,
                state = DownloadState.Failed,
                errorCategory = "missing_server",
                errorMessage = "Selected Plex server connection is unavailable.",
            )
            notifier.notifyFailed(jobId, job.displayTitle(), "Plex server unavailable")
            return Result.failure()
        }

        return runCatching {
            downloadJobRepository.updateState(jobId = jobId, state = DownloadState.Preparing)

            val queueId = job.queueId?.toLongOrNull()
                ?: plexDownloadQueueRepository.getOrCreateQueue(
                    connectionUri = serverContext.connectionUri,
                    serverAccessToken = serverContext.accessToken,
                )
            val queuedItem = if (job.queueItemId == null) {
                plexDownloadQueueRepository.addToQueue(
                    connectionUri = serverContext.connectionUri,
                    serverAccessToken = serverContext.accessToken,
                    queueId = queueId,
                    mediaKey = job.mediaKey,
                    profile = queueProfileFor(job.requestedQuality),
                )
            } else {
                null
            }
            val queueItemId = job.queueItemId?.toLongOrNull() ?: queuedItem?.id

            if (queueItemId == null) {
                downloadJobRepository.updateState(
                    jobId = jobId,
                    state = DownloadState.WaitingForServer,
                )
                return Result.retry()
            }

            downloadJobRepository.updateQueueTracking(
                jobId = jobId,
                queueId = queueId.toString(),
                queueItemId = queueItemId.toString(),
                state = DownloadState.WaitingForServer,
            )

            val queueItem = plexDownloadQueueRepository.getQueueItem(
                connectionUri = serverContext.connectionUri,
                serverAccessToken = serverContext.accessToken,
                queueId = queueId,
                itemId = queueItemId,
            )

            if (queueItem == null) {
                downloadJobRepository.updateState(
                    jobId = jobId,
                    state = DownloadState.WaitingForServer,
                )
                return Result.retry()
            }

            when (queueItem.status) {
                "deciding", "waiting", "processing" -> {
                    downloadJobRepository.updateState(
                        jobId = jobId,
                        state = DownloadState.WaitingForServer,
                    )
                    notifier.notifyProgress(
                        jobId = jobId,
                        title = job.displayTitle(),
                        stateLabel = "Waiting for server transcode",
                        bytesDownloaded = 0L,
                        bytesTotal = null,
                    )
                    Result.retry()
                }

                "available" -> {
                    val targetFile = createTargetFile(jobId = jobId, fileName = "queue-${job.mediaKey}.mp4")
                    downloadJobRepository.setTransferArtifact(
                        jobId = jobId,
                        filePath = targetFile.absolutePath,
                        fileName = targetFile.name,
                        mimeType = URLConnection.guessContentTypeFromName(targetFile.name),
                    )
                    downloadJobRepository.updateProgress(
                        jobId = jobId,
                        state = DownloadState.Downloading,
                        bytesDownloaded = 0L,
                        bytesTotal = null,
                    )
                    streamToFile(
                        url = plexDownloadQueueRepository.buildMediaUrl(
                            connectionUri = serverContext.connectionUri,
                            queueId = queueId,
                            itemId = queueItemId,
                        ),
                        serverToken = serverContext.accessToken,
                        targetFile = targetFile,
                        jobId = jobId,
                        jobTitle = job.displayTitle(),
                    )
                    downloadJobRepository.markCompletedWithArtifact(
                        jobId = jobId,
                        filePath = targetFile.absolutePath,
                        fileName = targetFile.name,
                        mimeType = URLConnection.guessContentTypeFromName(targetFile.name),
                        bytesDownloaded = targetFile.length(),
                        bytesTotal = targetFile.length(),
                    )
                    notifier.notifyCompleted(jobId, job.displayTitle())
                    logger.i(
                        component = "QueueDownloadWorker",
                        message = "Completed queue download for job=$jobId to ${targetFile.absolutePath}",
                    )
                    Result.success()
                }

                "error", "expired" -> {
                    downloadJobRepository.updateState(
                        jobId = jobId,
                        state = DownloadState.Failed,
                        errorCategory = "queue_failed",
                        errorMessage = queueItem.error ?: "Plex queue item failed with status ${queueItem.status}.",
                    )
                    notifier.notifyFailed(jobId, job.displayTitle(), "Server transcode failed")
                    Result.failure()
                }

                else -> {
                    downloadJobRepository.updateState(
                        jobId = jobId,
                        state = DownloadState.WaitingForServer,
                    )
                    Result.retry()
                }
            }
        }.getOrElse { throwable ->
            if (throwable is kotlin.coroutines.cancellation.CancellationException) {
                logger.i(
                    component = "QueueDownloadWorker",
                    message = "Queue download for job=$jobId was interrupted; will resume on next attempt",
                )
                throw throwable
            }
            logger.e(
                component = "QueueDownloadWorker",
                message = "Queue download failed for job=$jobId (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS)",
                throwable = throwable,
            )
            if (runAttemptCount >= MAX_ATTEMPTS) {
                downloadJobRepository.updateState(
                    jobId = jobId,
                    state = DownloadState.Failed,
                    errorCategory = "retries_exhausted",
                    errorMessage = "Queue download kept failing after $MAX_ATTEMPTS attempts. Retry to try again.",
                )
                notifier.notifyFailed(jobId, job.displayTitle(), "Retries exhausted")
                Result.failure()
            } else {
                downloadJobRepository.updateState(
                    jobId = jobId,
                    state = DownloadState.WaitingForServer,
                    errorCategory = "queue_retrying",
                    errorMessage = throwable.message ?: "Queue download will be retried.",
                )
                Result.retry()
            }
        }
    }

    private suspend fun streamToFile(
        url: String,
        serverToken: String,
        targetFile: File,
        jobId: String,
        jobTitle: String,
    ) {
        val request = Request.Builder()
            .url(url)
            .header("X-Plex-Token", serverToken)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while downloading queued media")
            }

            val body = response.body ?: throw IOException("Empty response body for queued download")
            val totalBytes = body.contentLength().takeIf { it > 0L }

            body.byteStream().use { input ->
                targetFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesDownloaded = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        if (bytesDownloaded % PROGRESS_GRANULARITY_BYTES < read.toLong()) {
                            downloadJobRepository.updateProgress(
                                jobId = jobId,
                                state = DownloadState.Downloading,
                                bytesDownloaded = bytesDownloaded,
                                bytesTotal = totalBytes,
                            )
                            notifier.notifyProgress(
                                jobId = jobId,
                                title = jobTitle,
                                stateLabel = "Downloading",
                                bytesDownloaded = bytesDownloaded,
                                bytesTotal = totalBytes,
                            )
                        }
                        read = input.read(buffer)
                    }
                    output.flush()
                }
            }
        }
    }

    private fun createTargetFile(jobId: String, fileName: String): File {
        val baseDir = File(
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "queue-downloads",
        )
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val sanitizedName = fileName.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        return File(baseDir, "${jobId.take(8)}-$sanitizedName")
    }

    private fun queueProfileFor(requestedQuality: String): PlexDownloadQueueRepository.QueueProfile {
        return when (requestedQuality) {
            "1080p" -> PlexDownloadQueueRepository.QueueProfile("1920x1080", 10000, 100)
            "720p" -> PlexDownloadQueueRepository.QueueProfile("1280x720", 4000, 75)
            "480p" -> PlexDownloadQueueRepository.QueueProfile("720x480", 1500, 60)
            else -> PlexDownloadQueueRepository.QueueProfile("1280x720", 4000, 75)
        }
    }

    private suspend fun promoteToForeground(jobId: String, title: String, label: String) {
        val notification = notifier.notifyProgress(
            jobId = jobId,
            title = title,
            stateLabel = label,
            bytesDownloaded = 0L,
            bytesTotal = null,
        )
        runCatching {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            setForeground(ForegroundInfo(DownloadNotifier.notificationId(jobId), notification, type))
        }.onFailure { throwable ->
            logger.w(
                component = "QueueDownloadWorker",
                message = "Foreground promotion unavailable; continuing in background",
                throwable = throwable,
            )
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        private const val PROGRESS_GRANULARITY_BYTES = 512 * 1024L
        private const val MAX_ATTEMPTS = 60
    }
}
