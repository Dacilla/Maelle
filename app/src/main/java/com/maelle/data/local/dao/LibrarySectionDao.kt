package com.maelle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maelle.data.local.entity.LibrarySectionEntity

@Dao
interface LibrarySectionDao {

    @Query("SELECT * FROM library_sections WHERE server_id = :serverId ORDER BY title COLLATE NOCASE ASC")
    suspend fun listByServer(serverId: String): List<LibrarySectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sections: List<LibrarySectionEntity>)

    @Query("DELETE FROM library_sections WHERE server_id = :serverId")
    suspend fun deleteByServer(serverId: String)
}
