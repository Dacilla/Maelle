package com.maelle.domain.downloads.model

data class DirectDownloadSpec(
    val title: String,
    val fileName: String,
    val url: String,
    val estimatedBytes: Long?,
)
