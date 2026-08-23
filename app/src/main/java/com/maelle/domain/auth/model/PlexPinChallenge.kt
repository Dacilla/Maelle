package com.maelle.domain.auth.model

data class PlexPinChallenge(
    val id: Int,
    val code: String,
    val authUrl: String,
)
