package com.maelle.core.ui

import java.net.URLEncoder

object PlexImages {

    fun posterUrl(
        baseUrl: String,
        token: String,
        thumbPath: String?,
        width: Int = POSTER_WIDTH,
    ): String? {
        if (thumbPath.isNullOrBlank()) return null
        val encoded = URLEncoder.encode(thumbPath, "UTF-8")
        val height = (width * 3) / 2
        return "$baseUrl/photo/:/transcode?url=$encoded&width=$width&height=$height&minSize=1&X-Plex-Token=$token"
    }

    const val POSTER_WIDTH = 300
}
