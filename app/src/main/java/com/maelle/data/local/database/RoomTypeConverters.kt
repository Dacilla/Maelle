package com.maelle.data.local.database

import androidx.room.TypeConverter
import com.maelle.domain.downloads.model.DownloadState
import com.maelle.domain.downloads.model.DownloadStrategy

class RoomTypeConverters {

    @TypeConverter
    fun toDownloadState(value: String): DownloadState = DownloadState.valueOf(value)

    @TypeConverter
    fun fromDownloadState(value: DownloadState): String = value.name

    @TypeConverter
    fun toDownloadStrategy(value: String): DownloadStrategy = DownloadStrategy.valueOf(value)

    @TypeConverter
    fun fromDownloadStrategy(value: DownloadStrategy): String = value.name
}
