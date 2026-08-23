package com.maelle.domain.downloads.model

data class DirectDownloadSpec(
    val title: String,
    val fileName: String,
    val url: String,
    val estimatedBytes: Long?,
    val subtitles: List<DirectSubtitleTrack> = emptyList(),
)

data class DirectSubtitleTrack(
    val url: String,
    val label: String,
    val format: String,
    val languageCode: String?,
    val title: String?,
)
