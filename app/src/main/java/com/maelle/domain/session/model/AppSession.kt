package com.maelle.domain.session.model

data class AppSession(
    val plexAuthToken: String?,
    val selectedServerId: String?,
    val selectedServerName: String?,
    val selectedConnectionUri: String?,
)
