package com.maelle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maelle.data.local.entity.DownloadJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadJobDao {

    @Query("SELECT * FROM download_jobs ORDER BY updated_at_epoch_ms DESC")
    fun observeAll(): Flow<List<DownloadJobEntity>>

    @Query("SELECT * FROM download_jobs WHERE job_id = :jobId LIMIT 1")
    suspend fun getById(jobId: String): DownloadJobEntity?

    @Query("SELECT * FROM download_jobs")
    suspend fun getAll(): List<DownloadJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: DownloadJobEntity)
}
