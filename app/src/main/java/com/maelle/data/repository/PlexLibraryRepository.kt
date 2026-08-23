package com.maelle.data.repository

import com.maelle.core.network.PlexServerServiceFactory
import com.maelle.data.local.dao.LibraryItemDao
import com.maelle.data.local.dao.LibrarySectionDao
import com.maelle.data.remote.library.PlexLibraryService
import com.maelle.domain.downloads.model.DirectDownloadSpec
import com.maelle.domain.library.model.PlexLibraryItem
import com.maelle.domain.library.model.PlexMediaDetail
import com.maelle.domain.library.model.PlexLibrarySection
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexLibraryRepository @Inject constructor(
    private val plexServerServiceFactory: PlexServerServiceFactory,
    private val librarySectionDao: LibrarySectionDao,
    private val libraryItemDao: LibraryItemDao,
) {

    data class LibraryItemCollection(
        val title: String,
        val items: List<PlexLibraryItem>,
    )

    suspend fun getCachedSections(serverId: String): List<PlexLibrarySection> {
        return librarySectionDao.listByServer(serverId).map { section ->
            PlexLibrarySection(
                key = section.sectionKey,
                title = section.title,
                type = section.type,
                composite = section.composite,
                art = section.art,
                thumb = section.thumb,
            )
        }
    }

    suspend fun refreshSections(
        serverId: String,
        connectionUri: String,
        serverAccessToken: String,
    ): List<PlexLibrarySection> {
        val service = plexServerServiceFactory.create(
            baseUrl = connectionUri,
            serviceClass = PlexLibraryService::class.java,
        )
        val now = System.currentTimeMillis()
        val sections = service.getSections(serverToken = serverAccessToken)
            .mediaContainer
            .directories
            .map { section ->
                PlexLibrarySection(
                    key = section.key,
                    title = section.title,
                    type = section.type,
                    composite = section.composite,
                    art = section.art,
                    thumb = section.thumb,
                )
            }
        librarySectionDao.deleteByServer(serverId)
        if (sections.isNotEmpty()) {
            librarySectionDao.upsertAll(
                sections.map { section ->
                    com.maelle.data.local.entity.LibrarySectionEntity(
                        serverId = serverId,
                        sectionKey = section.key,
                        title = section.title,
                        type = section.type,
                        composite = section.composite,
                        art = section.art,
                        thumb = section.thumb,
                        updatedAtEpochMs = now,
                    )
                },
            )
        }
        return getCachedSections(serverId)
    }

    suspend fun getCachedSectionItems(
        serverId: String,
        sectionKey: String,
    ): List<PlexLibraryItem> {
        return getCachedItems(serverId = serverId, parentPath = sectionParentPath(sectionKey))
    }

    suspend fun refreshSectionItems(
        serverId: String,
        connectionUri: String,
        serverAccessToken: String,
        sectionKey: String,
    ): List<PlexLibraryItem> {
        return refreshCollection(
            serverId = serverId,
            connectionUri = connectionUri,
            serverAccessToken = serverAccessToken,
            parentPath = sectionParentPath(sectionKey),
            title = sectionKey,
            fetch = { service ->
                service.getSectionItems(
                    sectionKey = sectionKey,
                    serverToken = serverAccessToken,
                )
            },
        ).items
    }

    suspend fun getCachedItemsByPath(
        serverId: String,
        path: String,
    ): LibraryItemCollection {
        return LibraryItemCollection(
            title = path,
            items = getCachedItems(serverId = serverId, parentPath = path),
        )
    }

    suspend fun refreshItemsByPath(
        serverId: String,
        connectionUri: String,
        serverAccessToken: String,
        title: String,
        path: String,
    ): LibraryItemCollection {
        return refreshCollection(
            serverId = serverId,
            connectionUri = connectionUri,
            serverAccessToken = serverAccessToken,
            parentPath = path,
            title = title,
            fetch = { service ->
                service.getItemsByPath(
                    path = path.trimStart('/'),
                    serverToken = serverAccessToken,
                )
            },
        )
    }

    private suspend fun refreshCollection(
        serverId: String,
        connectionUri: String,
        serverAccessToken: String,
        parentPath: String,
        title: String,
        fetch: suspend (PlexLibraryService) -> com.maelle.data.remote.library.PlexLibraryItemsResponse,
    ): LibraryItemCollection {
        val service = plexServerServiceFactory.create(
            baseUrl = connectionUri,
            serviceClass = PlexLibraryService::class.java,
        )
        val now = System.currentTimeMillis()
        val items = fetch(service).mediaContainer.metadata.map { item ->
            PlexLibraryItem(
                ratingKey = item.ratingKey,
                key = item.key,
                type = item.type,
                title = item.title ?: item.grandparentTitle ?: "Untitled",
                secondaryTitle = item.parentTitle ?: item.grandparentTitle,
                year = item.year,
                summary = item.summary,
                thumb = item.thumb,
                art = item.art,
                itemCountLabel = when {
                    item.leafCount != null -> "${item.leafCount} items"
                    item.childCount != null -> "${item.childCount} items"
                    else -> null
                },
                browsePath = item.key?.takeIf { path ->
                    item.type in setOf("show", "season") && path.contains("/children")
                },
            )
        }
        libraryItemDao.deleteByParentPath(serverId = serverId, parentPath = parentPath)
        if (items.isNotEmpty()) {
            libraryItemDao.upsertAll(
                items.map { item ->
                    com.maelle.data.local.entity.LibraryItemEntity(
                        serverId = serverId,
                        parentPath = parentPath,
                        ratingKey = item.ratingKey,
                        itemKey = item.key,
                        type = item.type,
                        title = item.title,
                        secondaryTitle = item.secondaryTitle,
                        year = item.year,
                        summary = item.summary,
                        thumb = item.thumb,
                        art = item.art,
                        itemCountLabel = item.itemCountLabel,
                        browsePath = item.browsePath,
                        updatedAtEpochMs = now,
                    )
                },
            )
        }
        return LibraryItemCollection(
            title = title,
            items = getCachedItems(serverId = serverId, parentPath = parentPath),
        )
    }

    private suspend fun getCachedItems(
        serverId: String,
        parentPath: String,
    ): List<PlexLibraryItem> {
        return libraryItemDao.listByParentPath(serverId = serverId, parentPath = parentPath).map { item ->
            PlexLibraryItem(
                ratingKey = item.ratingKey,
                key = item.itemKey,
                type = item.type,
                title = item.title,
                secondaryTitle = item.secondaryTitle,
                year = item.year,
                summary = item.summary,
                thumb = item.thumb,
                art = item.art,
                itemCountLabel = item.itemCountLabel,
                browsePath = item.browsePath,
            )
        }
    }

    private fun sectionParentPath(sectionKey: String): String = "section:$sectionKey"

    suspend fun getMediaDetail(
        connectionUri: String,
        serverAccessToken: String,
        ratingKey: String,
    ): PlexMediaDetail {
        val service = plexServerServiceFactory.create(
            baseUrl = connectionUri,
            serviceClass = PlexLibraryService::class.java,
        )
        val metadata = service.getMetadata(
            ratingKey = ratingKey,
            serverToken = serverAccessToken,
        ).mediaContainer.metadata.first()
        val firstMedia = metadata.media.firstOrNull()
        val firstPart = firstMedia?.parts?.firstOrNull()
        return PlexMediaDetail(
            ratingKey = metadata.ratingKey,
            title = metadata.title ?: metadata.grandparentTitle ?: "Untitled",
            type = metadata.type,
            secondaryTitle = metadata.parentTitle ?: metadata.grandparentTitle,
            year = metadata.year,
            summary = metadata.summary,
            estimatedBytes = firstPart?.size,
            container = firstMedia?.container,
            resolution = firstMedia?.videoResolution,
            bitrateKbps = firstMedia?.bitrate,
        )
    }

    suspend fun getDirectDownloadSpec(
        connectionUri: String,
        serverAccessToken: String,
        ratingKey: String,
    ): DirectDownloadSpec {
        val service = plexServerServiceFactory.create(
            baseUrl = connectionUri,
            serviceClass = PlexLibraryService::class.java,
        )
        val metadata = service.getMetadata(
            ratingKey = ratingKey,
            serverToken = serverAccessToken,
        ).mediaContainer.metadata.first()
        val firstPart = metadata.media.firstOrNull()?.parts?.firstOrNull()
            ?: error("No direct media part available for $ratingKey")
        val partId = firstPart.id ?: error("Missing media part id for $ratingKey")
        val updatedAt = metadata.updatedAt ?: 0L
        val fileName = firstPart.file
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "${metadata.title ?: ratingKey}.bin"
        val encodedFileName = URLEncoder.encode(fileName, Charsets.UTF_8.name()).replace("+", "%20")
        val baseUrl = if (connectionUri.endsWith("/")) connectionUri.dropLast(1) else connectionUri
        return DirectDownloadSpec(
            title = metadata.title ?: metadata.grandparentTitle ?: "Untitled",
            fileName = fileName,
            url = "$baseUrl/library/parts/$partId/$updatedAt/$encodedFileName?download=1",
            estimatedBytes = firstPart.size,
        )
    }
}
