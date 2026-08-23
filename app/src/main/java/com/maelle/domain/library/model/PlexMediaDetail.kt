package com.maelle.domain.library.model

data class PlexMediaDetail(
    val ratingKey: String,
    val title: String,
    val type: String,
    val secondaryTitle: String?,
    val year: Int?,
    val summary: String?,
    val estimatedBytes: Long?,
    val container: String?,
    val resolution: String?,
    val bitrateKbps: Int?,
)
