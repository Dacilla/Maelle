package com.maelle.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.maelle.data.local.dao.AppSessionDao
import com.maelle.data.local.dao.DownloadJobDao
import com.maelle.data.local.dao.LibraryItemDao
import com.maelle.data.local.dao.LibrarySectionDao
import com.maelle.data.local.dao.ServerDao
import com.maelle.data.local.entity.AppSessionEntity
import com.maelle.data.local.entity.DownloadJobEntity
import com.maelle.data.local.entity.LibraryItemEntity
import com.maelle.data.local.entity.LibrarySectionEntity
import com.maelle.data.local.entity.ServerEntity

@Database(
    entities = [
        AppSessionEntity::class,
        ServerEntity::class,
        DownloadJobEntity::class,
        LibrarySectionEntity::class,
        LibraryItemEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class MaelleDatabase : RoomDatabase() {
    abstract fun appSessionDao(): AppSessionDao
    abstract fun serverDao(): ServerDao
    abstract fun downloadJobDao(): DownloadJobDao
    abstract fun librarySectionDao(): LibrarySectionDao
    abstract fun libraryItemDao(): LibraryItemDao
}
