package com.maelle.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "library_sections",
    primaryKeys = ["server_id", "section_key"],
)
data class LibrarySectionEntity(
    @ColumnInfo(name = "server_id")
    val serverId: String,
    @ColumnInfo(name = "section_key")
    val sectionKey: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "composite")
    val composite: String?,
    @ColumnInfo(name = "art")
    val art: String?,
    @ColumnInfo(name = "thumb")
    val thumb: String?,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
