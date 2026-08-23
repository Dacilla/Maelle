package com.maelle.domain.library.model

data class PlexLibraryItem(
    val ratingKey: String,
    val key: String?,
    val type: String,
    val title: String,
    val secondaryTitle: String?,
    val year: Int?,
    val summary: String?,
    val thumb: String?,
    val art: String?,
    val itemCountLabel: String?,
    val browsePath: String?,
)
