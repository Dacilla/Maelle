package com.maelle.core.di

import com.maelle.data.local.database.MaelleDatabase
import com.maelle.data.repository.AppSessionRepository
import com.maelle.data.repository.DownloadJobRepository
import com.maelle.data.repository.PlexLibraryRepository
import com.maelle.data.repository.PlexServerRepository
import com.maelle.core.network.PlexServerServiceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideDownloadJobRepository(
        database: MaelleDatabase,
    ): DownloadJobRepository {
        return DownloadJobRepository(database.downloadJobDao())
    }

    @Provides
    @Singleton
    fun provideAppSessionRepository(
        database: MaelleDatabase,
    ): AppSessionRepository {
        return AppSessionRepository(database.appSessionDao())
    }

    @Provides
    @Singleton
    fun provideServerDao(database: MaelleDatabase) = database.serverDao()

    @Provides
    @Singleton
    fun providePlexLibraryRepository(
        database: MaelleDatabase,
        plexServerServiceFactory: PlexServerServiceFactory,
    ): PlexLibraryRepository {
        return PlexLibraryRepository(
            plexServerServiceFactory = plexServerServiceFactory,
            librarySectionDao = database.librarySectionDao(),
            libraryItemDao = database.libraryItemDao(),
        )
    }
}
