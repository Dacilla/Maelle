package com.maelle.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.maelle.domain.downloads.model.DownloadState
import com.maelle.domain.downloads.model.DownloadStrategy

@Entity(tableName = "download_jobs")
data class DownloadJobEntity(
    @PrimaryKey
    @ColumnInfo(name = "job_id")
    val jobId: String,
    @ColumnInfo(name = "media_key")
    val mediaKey: String,
    @ColumnInfo(name = "media_title")
    val mediaTitle: String,
    @ColumnInfo(name = "media_secondary_title")
    val mediaSecondaryTitle: String?,
    @ColumnInfo(name = "server_id")
    val serverId: String,
    @ColumnInfo(name = "strategy")
    val strategy: DownloadStrategy,
    @ColumnInfo(name = "state")
    val state: DownloadState,
    @ColumnInfo(name = "requested_quality")
    val requestedQuality: String,
    @ColumnInfo(name = "queue_id")
    val queueId: String?,
    @ColumnInfo(name = "queue_item_id")
    val queueItemId: String?,
    @ColumnInfo(name = "bytes_downloaded")
    val bytesDownloaded: Long,
    @ColumnInfo(name = "bytes_total")
    val bytesTotal: Long?,
    @ColumnInfo(name = "local_file_path")
    val localFilePath: String?,
    @ColumnInfo(name = "local_file_name")
    val localFileName: String?,
    @ColumnInfo(name = "artifact_mime_type")
    val artifactMimeType: String?,
    @ColumnInfo(name = "artifact_bytes")
    val artifactBytes: Long?,
    @ColumnInfo(name = "error_category")
    val errorCategory: String?,
    @ColumnInfo(name = "error_message")
    val errorMessage: String?,
    @ColumnInfo(name = "burn_subtitles", defaultValue = "0")
    val burnSubtitles: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
