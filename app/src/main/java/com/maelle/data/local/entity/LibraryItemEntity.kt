package com.maelle.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "library_items",
    indices = [
        Index(value = ["server_id", "parent_path"]),
    ],
    primaryKeys = ["server_id", "parent_path", "rating_key"],
)
data class LibraryItemEntity(
    @ColumnInfo(name = "server_id")
    val serverId: String,
    @ColumnInfo(name = "parent_path")
    val parentPath: String,
    @ColumnInfo(name = "rating_key")
    val ratingKey: String,
    @ColumnInfo(name = "item_key")
    val itemKey: String?,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "secondary_title")
    val secondaryTitle: String?,
    @ColumnInfo(name = "year")
    val year: Int?,
    @ColumnInfo(name = "summary")
    val summary: String?,
    @ColumnInfo(name = "thumb")
    val thumb: String?,
    @ColumnInfo(name = "art")
    val art: String?,
    @ColumnInfo(name = "item_count_label")
    val itemCountLabel: String?,
    @ColumnInfo(name = "browse_path")
    val browsePath: String?,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
