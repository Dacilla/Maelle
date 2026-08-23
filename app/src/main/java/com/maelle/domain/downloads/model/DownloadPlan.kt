package com.maelle.domain.downloads.model

import com.maelle.domain.library.model.PlexLibraryItem
import com.maelle.domain.library.model.PlexMediaDetail

data class DownloadPlan(
    val item: PlexLibraryItem,
    val detail: PlexMediaDetail,
    val options: List<DownloadPlanOption>,
)

data class DownloadPlanOption(
    val strategy: DownloadStrategy,
    val title: String,
    val description: String,
    val requestedQuality: String,
    val recommended: Boolean,
)
