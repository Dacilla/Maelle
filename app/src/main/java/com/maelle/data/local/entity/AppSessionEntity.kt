package com.maelle.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_session")
data class AppSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: Int = SINGLETON_ID,
    @ColumnInfo(name = "plex_auth_token")
    val plexAuthToken: String?,
    @ColumnInfo(name = "selected_server_id")
    val selectedServerId: String?,
    @ColumnInfo(name = "selected_server_name")
    val selectedServerName: String?,
    @ColumnInfo(name = "selected_connection_uri")
    val selectedConnectionUri: String?,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
