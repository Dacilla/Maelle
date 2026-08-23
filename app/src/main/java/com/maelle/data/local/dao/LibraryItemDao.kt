package com.maelle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maelle.data.local.entity.LibraryItemEntity

@Dao
interface LibraryItemDao {

    @Query(
        "SELECT * FROM library_items " +
            "WHERE server_id = :serverId AND parent_path = :parentPath " +
            "ORDER BY title COLLATE NOCASE ASC",
    )
    suspend fun listByParentPath(serverId: String, parentPath: String): List<LibraryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LibraryItemEntity>)

    @Query("DELETE FROM library_items WHERE server_id = :serverId AND parent_path = :parentPath")
    suspend fun deleteByParentPath(serverId: String, parentPath: String)
}
