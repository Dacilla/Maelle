package com.maelle.app.ui

data class MaelleAppUiState(
    val destination: MaelleDestination,
    val selectedServerName: String? = null,
    val selectedConnectionUri: String? = null,
)

enum class MaelleDestination {
    Auth,
    Servers,
    Home,
}
