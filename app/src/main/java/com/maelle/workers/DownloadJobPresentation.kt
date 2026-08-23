package com.maelle.workers

import com.maelle.data.local.entity.DownloadJobEntity

internal fun DownloadJobEntity.displayTitle(): String {
    return listOfNotNull(
        mediaTitle.takeIf { it.isNotBlank() },
        mediaSecondaryTitle?.takeIf { it.isNotBlank() },
    ).distinct().joinToString(" - ").ifBlank { "Plex download" }
}
