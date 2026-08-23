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
import com.maelle.core.network.DownloadHttpClient
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
    @DownloadHttpClient private val okHttpClient: OkHttpClient,
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
                    burnSubtitles = job.burnSubtitles,
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
                    val mediaUrl = plexDownloadQueueRepository.buildMediaUrl(
                        connectionUri = serverContext.connectionUri,
                        queueId = queueId,
                        itemId = queueItemId,
                    )
                    val probe = probeDownloadMetadata(
                        url = mediaUrl,
                        serverToken = serverContext.accessToken,
                    )
                    val targetFile = createTargetFile(
                        jobId = jobId,
                        fileName = probe?.fileName ?: "queue-${job.mediaKey}.mp4",
                    )
                    downloadJobRepository.setTransferArtifact(
                        jobId = jobId,
                        filePath = targetFile.absolutePath,
                        fileName = targetFile.name,
                        mimeType = URLConnection.guessContentTypeFromName(targetFile.name),
                    )
                    downloadJobRepository.updateProgress(
                        jobId = jobId,
                        state = DownloadState.Downloading,
                        bytesDownloaded = targetFile.length(),
                        bytesTotal = probe?.contentLength,
                    )
                    streamToFile(
                        url = mediaUrl,
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

    private data class DownloadMetadata(
        val fileName: String?,
        val contentLength: Long?,
    )

    private fun probeDownloadMetadata(url: String, serverToken: String): DownloadMetadata? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("X-Plex-Token", serverToken)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val length = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                val disposition = response.header("Content-Disposition")
                logger.i(
                    component = "QueueDownloadWorker",
                    message = "Queue media probe: length=$length disposition=$disposition",
                )
                DownloadMetadata(
                    fileName = fileNameFromDisposition(disposition),
                    contentLength = length,
                )
            }
        }.getOrNull()
    }

    private fun fileNameFromDisposition(disposition: String?): String? {
        if (disposition.isNullOrBlank()) return null
        Regex("filename\\*=(?:[A-Za-z0-9-]*'[^']*')?([^;]+)").find(disposition)?.let { match ->
            val raw = match.groupValues[1].trim().trim('"')
            val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrElse { raw }
            return decoded.takeIf { it.isNotBlank() }
        }
        Regex("filename=\"([^\"]+)\"").find(disposition)?.let { match ->
            return match.groupValues[1].takeIf { it.isNotBlank() }
        }
        return Regex("filename=([^;]+)").find(disposition)?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun streamToFile(
        url: String,
        serverToken: String,
        targetFile: File,
        jobId: String,
        jobTitle: String,
    ) {
        var resumeOffset = targetFile.length()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("X-Plex-Token", serverToken)
        if (resumeOffset > 0L) {
            requestBuilder.header("Range", "bytes=$resumeOffset-")
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 206) {
                logger.i(
                    component = "QueueDownloadWorker",
                    message = "Resuming queue download for job=$jobId from byte $resumeOffset",
                )
            } else if (response.code == 503) {
                throw IOException("Transcoded media not ready yet (HTTP 503)")
            } else if (response.isSuccessful && resumeOffset > 0L) {
                logger.i(
                    component = "QueueDownloadWorker",
                    message = "Server ignored range request for job=$jobId; restarting from scratch",
                )
                targetFile.delete()
                resumeOffset = 0L
            } else if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while downloading queued media")
            }

            val body = response.body ?: throw IOException("Empty response body for queued download")
            val contentLength = body.contentLength().takeIf { it > 0L }
            val totalBytes = when {
                response.code == 206 && contentLength != null -> resumeOffset + contentLength
                else -> contentLength
            }

            body.byteStream().use { input ->
                val output = java.io.FileOutputStream(targetFile, resumeOffset > 0L)
                output.buffered().use { buffered ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesDownloaded = resumeOffset
                    var bytesSinceFlush = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        buffered.write(buffer, 0, read)
                        bytesDownloaded += read
                        bytesSinceFlush += read
                        if (bytesSinceFlush >= PROGRESS_GRANULARITY_BYTES) {
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
                            bytesSinceFlush = 0L
                        }
                        read = input.read(buffer)
                    }
                    buffered.flush()
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
        return PlexDownloadQueueRepository.profileForQuality(requestedQuality)
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
