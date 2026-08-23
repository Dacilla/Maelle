package com.maelle.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.maelle.core.network.DownloadHttpClient
import com.maelle.core.network.PlexHeadersInterceptor
import com.maelle.core.network.PlexResourcesRetrofit
import com.maelle.core.network.PlexTvRetrofit
import com.maelle.core.network.LoggingInterceptorFactory
import com.maelle.data.local.database.MaelleDatabase
import com.maelle.data.remote.auth.PlexAuthService
import com.maelle.data.remote.resources.PlexResourcesService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `library_sections` (
                    `server_id` TEXT NOT NULL,
                    `section_key` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `composite` TEXT,
                    `art` TEXT,
                    `thumb` TEXT,
                    `updated_at_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`server_id`, `section_key`)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `library_items` (
                    `server_id` TEXT NOT NULL,
                    `parent_path` TEXT NOT NULL,
                    `rating_key` TEXT NOT NULL,
                    `item_key` TEXT,
                    `type` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `secondary_title` TEXT,
                    `year` INTEGER,
                    `summary` TEXT,
                    `thumb` TEXT,
                    `art` TEXT,
                    `item_count_label` TEXT,
                    `browse_path` TEXT,
                    `updated_at_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`server_id`, `parent_path`, `rating_key`)
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_library_items_server_id_parent_path` " +
                    "ON `library_items` (`server_id`, `parent_path`)",
            )
        }
    }

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `download_jobs` ADD COLUMN `local_file_path` TEXT")
            database.execSQL("ALTER TABLE `download_jobs` ADD COLUMN `local_file_name` TEXT")
            database.execSQL("ALTER TABLE `download_jobs` ADD COLUMN `artifact_mime_type` TEXT")
            database.execSQL("ALTER TABLE `download_jobs` ADD COLUMN `artifact_bytes` INTEGER")
        }
    }

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `download_jobs` ADD COLUMN `media_title` TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE `download_jobs` ADD COLUMN `media_secondary_title` TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        plexHeadersInterceptor: PlexHeadersInterceptor,
        loggingInterceptorFactory: LoggingInterceptorFactory,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(plexHeadersInterceptor)
            .addInterceptor(loggingInterceptorFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @DownloadHttpClient
    fun provideDownloadHttpClient(okHttpClient: OkHttpClient): OkHttpClient {
        return okHttpClient.newBuilder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @PlexTvRetrofit
    fun providePlexTvRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://plex.tv/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @PlexResourcesRetrofit
    fun providePlexResourcesRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://clients.plex.tv/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun providePlexAuthService(
        @PlexTvRetrofit retrofit: Retrofit,
    ): PlexAuthService = retrofit.create(PlexAuthService::class.java)

    @Provides
    @Singleton
    fun providePlexResourcesService(
        @PlexResourcesRetrofit retrofit: Retrofit,
    ): PlexResourcesService = retrofit.create(PlexResourcesService::class.java)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MaelleDatabase {
        return Room.databaseBuilder(
            context,
            MaelleDatabase::class.java,
            "maelle.db",
        ).addMigrations(migration1To2, migration2To3, migration3To4)
            .build()
    }
}
