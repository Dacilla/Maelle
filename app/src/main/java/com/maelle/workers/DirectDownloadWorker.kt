package com.maelle.workers

import android.content.Context
import android.os.Environment
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maelle.core.logging.RedactingLogger
import com.maelle.data.repository.DownloadJobRepository
import com.maelle.data.repository.PlexLibraryRepository
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
class DirectDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadJobRepository: DownloadJobRepository,
    private val plexServerRepository: PlexServerRepository,
    private val plexLibraryRepository: PlexLibraryRepository,
    private val okHttpClient: OkHttpClient,
    private val logger: RedactingLogger,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val job = downloadJobRepository.getJob(jobId)
            ?: return Result.failure()

        if (job.strategy != com.maelle.domain.downloads.model.DownloadStrategy.Direct) {
            return Result.success()
        }

        val serverContext = plexServerRepository.getServerDownloadContext(job.serverId)
        if (serverContext == null) {
            downloadJobRepository.updateState(
                jobId = jobId,
                state = DownloadState.Failed,
                errorCategory = "missing_server",
                errorMessage = "Selected Plex server connection is unavailable.",
            )
            return Result.failure()
        }

        return runCatching {
            downloadJobRepository.updateState(jobId = jobId, state = DownloadState.Preparing)

            val spec = plexLibraryRepository.getDirectDownloadSpec(
                connectionUri = serverContext.connectionUri,
                serverAccessToken = serverContext.accessToken,
                ratingKey = job.mediaKey,
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
                bytesDownloaded = 0L,
                bytesTotal = spec.estimatedBytes,
            )

            streamToFile(
                url = spec.url,
                serverToken = serverContext.accessToken,
                targetFile = targetFile,
                jobId = jobId,
                expectedBytes = spec.estimatedBytes,
            )

            downloadJobRepository.markCompletedWithArtifact(
                jobId = jobId,
                filePath = targetFile.absolutePath,
                fileName = targetFile.name,
                mimeType = URLConnection.guessContentTypeFromName(targetFile.name),
                bytesDownloaded = targetFile.length(),
                bytesTotal = spec.estimatedBytes ?: targetFile.length(),
            )
            logger.i(
                component = "DirectDownloadWorker",
                message = "Completed direct download for job=$jobId to ${targetFile.absolutePath}",
            )
            Result.success()
        }.getOrElse { throwable ->
            logger.e(
                component = "DirectDownloadWorker",
                message = "Direct download failed for job=$jobId",
                throwable = throwable,
            )
            downloadJobRepository.updateState(
                jobId = jobId,
                state = DownloadState.Failed,
                errorCategory = "direct_download_failed",
                errorMessage = throwable.message ?: "Direct download failed.",
            )
            Result.retry()
        }
    }

    private suspend fun streamToFile(
        url: String,
        serverToken: String,
        targetFile: File,
        jobId: String,
        expectedBytes: Long?,
    ) {
        val request = Request.Builder()
            .url(url)
            .header("X-Plex-Token", serverToken)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while downloading media part")
            }

            val body = response.body ?: throw IOException("Empty response body for direct download")
            val totalBytes = if (expectedBytes != null && expectedBytes > 0L) {
                expectedBytes
            } else {
                body.contentLength().takeIf { it > 0L }
            }

            body.byteStream().use { input ->
                targetFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesDownloaded = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        if (bytesDownloaded == read.toLong() || bytesDownloaded % PROGRESS_GRANULARITY_BYTES < read) {
                            downloadJobRepository.updateProgress(
                                jobId = jobId,
                                state = DownloadState.Downloading,
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
            "direct-downloads",
        )
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val sanitizedName = fileName.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        return File(baseDir, "${jobId.take(8)}-$sanitizedName")
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        private const val PROGRESS_GRANULARITY_BYTES = 512 * 1024L
    }
}
