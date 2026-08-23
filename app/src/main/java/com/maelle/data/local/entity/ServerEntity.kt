package com.maelle.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey
    @ColumnInfo(name = "server_id")
    val serverId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "access_token")
    val accessToken: String,
    @ColumnInfo(name = "owned")
    val owned: Boolean,
    @ColumnInfo(name = "cached_connections_json")
    val cachedConnectionsJson: String,
    @ColumnInfo(name = "last_selected_connection_uri")
    val lastSelectedConnectionUri: String?,
    @ColumnInfo(name = "last_successful_contact_epoch_ms")
    val lastSuccessfulContactEpochMs: Long?,
)
