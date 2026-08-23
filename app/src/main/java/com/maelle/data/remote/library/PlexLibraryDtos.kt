package com.maelle.data.remote.library

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexLibrarySectionsResponse(
    @SerialName("MediaContainer")
    val mediaContainer: PlexLibrarySectionsContainer = PlexLibrarySectionsContainer(),
)

@Serializable
data class PlexLibrarySectionsContainer(
    @SerialName("Directory")
    val directories: List<PlexLibrarySectionDto> = emptyList(),
)

@Serializable
data class PlexLibrarySectionDto(
    @SerialName("key")
    val key: String,
    @SerialName("title")
    val title: String,
    @SerialName("type")
    val type: String,
    @SerialName("agent")
    val agent: String? = null,
    @SerialName("composite")
    val composite: String? = null,
    @SerialName("art")
    val art: String? = null,
    @SerialName("thumb")
    val thumb: String? = null,
)

@Serializable
data class PlexLibraryItemsResponse(
    @SerialName("MediaContainer")
    val mediaContainer: PlexLibraryItemsContainer = PlexLibraryItemsContainer(),
)

@Serializable
data class PlexLibraryItemsContainer(
    @SerialName("Metadata")
    val metadata: List<PlexLibraryItemDto> = emptyList(),
)

@Serializable
data class PlexMetadataResponse(
    @SerialName("MediaContainer")
    val mediaContainer: PlexMetadataContainer = PlexMetadataContainer(),
)

@Serializable
data class PlexMetadataContainer(
    @SerialName("Metadata")
    val metadata: List<PlexLibraryMetadataDto> = emptyList(),
)

@Serializable
data class PlexLibraryItemDto(
    @SerialName("ratingKey")
    val ratingKey: String,
    @SerialName("key")
    val key: String? = null,
    @SerialName("type")
    val type: String,
    @SerialName("title")
    val title: String? = null,
    @SerialName("grandparentTitle")
    val grandparentTitle: String? = null,
    @SerialName("parentTitle")
    val parentTitle: String? = null,
    @SerialName("titleSort")
    val titleSort: String? = null,
    @SerialName("year")
    val year: Int? = null,
    @SerialName("summary")
    val summary: String? = null,
    @SerialName("thumb")
    val thumb: String? = null,
    @SerialName("art")
    val art: String? = null,
    @SerialName("index")
    val index: Int? = null,
    @SerialName("parentIndex")
    val parentIndex: Int? = null,
    @SerialName("leafCount")
    val leafCount: Int? = null,
    @SerialName("childCount")
    val childCount: Int? = null,
    @SerialName("duration")
    val duration: Long? = null,
)

@Serializable
data class PlexLibraryMetadataDto(
    @SerialName("ratingKey")
    val ratingKey: String,
    @SerialName("updatedAt")
    val updatedAt: Long? = null,
    @SerialName("type")
    val type: String,
    @SerialName("title")
    val title: String? = null,
    @SerialName("grandparentTitle")
    val grandparentTitle: String? = null,
    @SerialName("parentTitle")
    val parentTitle: String? = null,
    @SerialName("year")
    val year: Int? = null,
    @SerialName("summary")
    val summary: String? = null,
    @SerialName("Media")
    val media: List<PlexMediaDto> = emptyList(),
)

@Serializable
data class PlexMediaDto(
    @SerialName("bitrate")
    val bitrate: Int? = null,
    @SerialName("videoResolution")
    val videoResolution: String? = null,
    @SerialName("container")
    val container: String? = null,
    @SerialName("Part")
    val parts: List<PlexPartDto> = emptyList(),
)

@Serializable
data class PlexPartDto(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("file")
    val file: String? = null,
    @SerialName("size")
    val size: Long? = null,
)
