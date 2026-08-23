package com.maelle.feature.auth

data class AuthUiState(
    val pinCode: String? = null,
    val pinId: Int? = null,
    val authUrl: String? = null,
    val isGeneratingPin: Boolean = false,
    val isPolling: Boolean = false,
    val errorMessage: String? = null,
)
