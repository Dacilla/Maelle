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
import com.maelle.data.repository.PlexLibraryRepository
import com.maelle.data.repository.PlexServerRepository
import com.maelle.domain.downloads.model.DownloadState
import com.maelle.domain.downloads.model.DownloadStrategy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import java.net.URLConnection
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltWorker
class DirectDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadJobRepository: DownloadJobRepository,
    private val plexServerRepository: PlexServerRepository,
    private val plexLibraryRepository: PlexLibraryRepository,
    @DownloadHttpClient private val okHttpClient: OkHttpClient,
    private val notifier: DownloadNotifier,
    private val logger: RedactingLogger,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val job = downloadJobRepository.getJob(jobId)
            ?: return Result.failure()

        if (job.strategy != DownloadStrategy.Direct) {
            return Result.success()
        }

        promoteToForeground(job.displayTitle())

        if (runAttemptCount > MAX_ATTEMPTS) {
            return exhausted(jobId)
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

            val spec = plexLibraryRepository.getDirectDownloadSpec(
                connectionUri = serverContext.connectionUri,
                serverAccessToken = serverContext.accessToken,
                ratingKey = job.mediaKey,
            )
            logger.i(
                component = "DirectDownloadWorker",
                message = "Resolved direct spec for job=$jobId (${spec.estimatedBytes ?: 0} bytes, ${spec.subtitles.size} subtitle tracks)",
            )

            val targetFile = createTargetFile(jobId = jobId, fileName = spec.fileName)
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
                bytesTotal = spec.estimatedBytes,
            )

            transfer(
                url = spec.url,
                serverToken = serverContext.accessToken,
                targetFile = targetFile,
                jobId = jobId,
                jobTitle = job.displayTitle(),
                expectedBytes = spec.estimatedBytes,
            )
            downloadSubtitleSidecars(
                spec = spec,
                videoFile = targetFile,
                serverToken = serverContext.accessToken,
            )

            downloadJobRepository.markCompletedWithArtifact(
                jobId = jobId,
                filePath = targetFile.absolutePath,
                fileName = targetFile.name,
                mimeType = URLConnection.guessContentTypeFromName(targetFile.name),
                bytesDownloaded = targetFile.length(),
                bytesTotal = spec.estimatedBytes ?: targetFile.length(),
            )
            notifier.notifyCompleted(jobId, job.displayTitle())
            logger.i(
                component = "DirectDownloadWorker",
                message = "Completed direct download for job=$jobId to ${targetFile.absolutePath}",
            )
            Result.success()
        }.getOrElse { throwable ->
            if (throwable is CancellationException) {
                logger.i(
                    component = "DirectDownloadWorker",
                    message = "Direct download for job=$jobId was interrupted; partial file kept for resume",
                )
                throw throwable
            }
            logger.e(
                component = "DirectDownloadWorker",
                message = "Direct download failed for job=$jobId (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS)",
                throwable = throwable,
            )
            if (runAttemptCount >= MAX_ATTEMPTS) {
                exhausted(jobId)
            } else {
                downloadJobRepository.updateState(
                    jobId = jobId,
                    state = DownloadState.Failed,
                    errorCategory = "direct_download_failed",
                    errorMessage = throwable.message ?: "Direct download failed.",
                )
                Result.retry()
            }
        }
    }

    private suspend fun exhausted(jobId: String): Result {
        downloadJobRepository.updateState(
            jobId = jobId,
            state = DownloadState.Failed,
            errorCategory = "retries_exhausted",
            errorMessage = "Download kept failing after $MAX_ATTEMPTS attempts. Partial progress is preserved; retry to continue.",
        )
        notifier.notifyFailed(jobId, "Direct download", "Retries exhausted")
        return Result.failure()
    }

    private suspend fun promoteToForeground(title: String) {
        val notification = notifier.notifyProgress(
            jobId = inputData.getString(KEY_JOB_ID) ?: "",
            title = title,
            stateLabel = "Preparing",
            bytesDownloaded = 0L,
            bytesTotal = null,
        )
        runCatching {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            setForeground(ForegroundInfo(DownloadNotifier.notificationId(inputData.getString(KEY_JOB_ID) ?: ""), notification, type))
        }.onFailure { throwable ->
            logger.w(
                component = "DirectDownloadWorker",
                message = "Foreground promotion unavailable; continuing in background",
                throwable = throwable,
            )
        }
    }

    private suspend fun transfer(
        url: String,
        serverToken: String,
        targetFile: File,
        jobId: String,
        jobTitle: String,
        expectedBytes: Long?,
    ) {
        var resumeOffset = targetFile.length()
        if (resumeOffset > 0L && expectedBytes != null && expectedBytes > 0L && resumeOffset >= expectedBytes) {
            targetFile.delete()
            resumeOffset = 0L
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("X-Plex-Token", serverToken)
        if (resumeOffset > 0L) {
            requestBuilder.header("Range", "bytes=$resumeOffset-")
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 206) {
                logger.i(
                    component = "DirectDownloadWorker",
                    message = "Resuming job=$jobId from byte $resumeOffset",
                )
            } else if (response.isSuccessful && resumeOffset > 0L) {
                logger.i(
                    component = "DirectDownloadWorker",
                    message = "Server ignored range request for job=$jobId; restarting from scratch",
                )
                targetFile.delete()
                resumeOffset = 0L
            } else if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while downloading media part")
            }

            val body = response.body ?: throw IOException("Empty response body for direct download")
            val contentLength = body.contentLength().takeIf { it > 0L }
            val totalBytes = when {
                expectedBytes != null && expectedBytes > 0L -> expectedBytes
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
                downloadJobRepository.updateProgress(
                    jobId = jobId,
                    state = DownloadState.Downloading,
                    bytesDownloaded = targetFile.length(),
                    bytesTotal = totalBytes,
                )
            }
        }
    }

    private fun downloadSubtitleSidecars(
        spec: com.maelle.domain.downloads.model.DirectDownloadSpec,
        videoFile: File,
        serverToken: String,
    ) {
        if (spec.subtitles.isEmpty()) return
        val targetDir = videoFile.parentFile ?: return
        val baseName = videoFile.nameWithoutExtension
        spec.subtitles.forEach { track ->
            val target = File(targetDir, "$baseName.${track.label}.${track.format}")
            if (target.exists() && target.length() > 0L) {
                logger.i(
                    component = "DirectDownloadWorker",
                    message = "Subtitle sidecar already present: ${target.name}",
                )
                return@forEach
            }
            runCatching {
                val request = Request.Builder()
                    .url(track.url)
                    .header("X-Plex-Token", serverToken)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} while fetching subtitle ${track.label}")
                    }
                    val body = response.body ?: throw IOException("Empty subtitle body")
                    target.outputStream().use { output -> body.byteStream().copyTo(output) }
                }
                logger.i(
                    component = "DirectDownloadWorker",
                    message = "Saved subtitle sidecar ${target.name}",
                )
            }.onFailure { throwable ->
                logger.w(
                    component = "DirectDownloadWorker",
                    message = "Skipping subtitle ${track.label}; download failed",
                    throwable = throwable,
                )
                target.delete()
            }
        }
    }

    private fun createTargetFile(jobId: String, fileName: String): File {
        val baseDir = File(
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "direct-downloads",
        )
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val sanitizedName = fileName.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        return File(baseDir, "$jobId-$sanitizedName")
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        private const val PROGRESS_GRANULARITY_BYTES = 512 * 1024L
        private const val MAX_ATTEMPTS = 8
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
