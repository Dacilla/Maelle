package com.maelle.domain.library.model

data class PlexLibrarySection(
    val key: String,
    val title: String,
    val type: String,
    val composite: String?,
    val art: String?,
    val thumb: String?,
)
