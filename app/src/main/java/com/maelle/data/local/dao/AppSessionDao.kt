package com.maelle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maelle.data.local.entity.AppSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSessionDao {

    @Query("SELECT * FROM app_session WHERE session_id = 1")
    fun observe(): Flow<AppSessionEntity?>

    @Query("SELECT * FROM app_session WHERE session_id = 1")
    suspend fun get(): AppSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: AppSessionEntity)
}
