package com.maelle.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.maelle.app.designsystem.components.EmptyMessage
import com.maelle.app.designsystem.components.PosterImage
import com.maelle.core.settings.UserSettings
import com.maelle.domain.library.model.PlexLibraryItem
import com.maelle.domain.library.model.PlexLibrarySection

@Composable
fun BrowsePane(
    uiState: HomeUiState,
    settings: UserSettings,
    onSectionSelected: (PlexLibrarySection) -> Unit,
    onItemClicked: (PlexLibraryItem) -> Unit,
    onNavigateUp: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSearchOpened: () -> Unit,
    onSearchClosed: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (uiState.isSearchMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Search this server") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSearchClosed) {
                    Icon(Icons.Default.Close, contentDescription = "Close search")
                }
            }
            when {
                uiState.isSearching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.searchErrorMessage != null -> {
                    EmptyMessage(uiState.searchErrorMessage)
                }

                uiState.searchQuery.isBlank() -> {
                    EmptyMessage("Type to search movies, shows and episodes.")
                }

                uiState.searchResults.isEmpty() -> {
                    EmptyMessage("No results for \"${uiState.searchQuery}\".")
                }

                else -> {
                    ItemGrid(
                        items = uiState.searchResults,
                        uiState = uiState,
                        settings = settings,
                        onItemClicked = onItemClicked,
                    )
                }
            }
            return@Column
        }

        if (uiState.browseStack.isNotEmpty()) {
            val current = uiState.browseStack.last()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            if (uiState.sections.isEmpty()) {
                when {
                    uiState.isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    else -> {
                        EmptyMessage(
                            uiState.errorMessage
                                ?: "No libraries found on this server.",
                        )
                    }
                }
                return@Column
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(uiState.sections, key = { it.key }) { section ->
                    FilterChip(
                        selected = uiState.selectedSection?.key == section.key,
                        onClick = { onSectionSelected(section) },
                        label = { Text(section.title) },
                    )
                }
            }
            if (uiState.selectedSection == null) {
                EmptyMessage("Pick a library above to start browsing.")
                return@Column
            }
        }

        when {
            uiState.isSectionLoading && uiState.sectionItems.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.sectionErrorMessage != null && uiState.sectionItems.isEmpty() -> {
                EmptyMessage(uiState.sectionErrorMessage)
            }

            else -> {
                ItemGrid(
                    items = uiState.sectionItems,
                    uiState = uiState,
                    settings = settings,
                    onItemClicked = onItemClicked,
                )
            }
        }
    }
}

@Composable
private fun ItemGrid(
    items: List<PlexLibraryItem>,
    uiState: HomeUiState,
    settings: UserSettings,
    onItemClicked: (PlexLibraryItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        state = rememberLazyGridState(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.ratingKey }) { item ->
            PosterCard(
                item = item,
                uiState = uiState,
                devMode = settings.developerMode,
                onClick = { onItemClicked(item) },
            )
        }
    }
}

@Composable
private fun PosterCard(
    item: PlexLibraryItem,
    uiState: HomeUiState,
    devMode: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        PosterImage(
            baseUrl = uiState.imageBaseUrl,
            token = uiState.imageToken,
            thumbPath = item.thumb,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val subtitle = listOfNotNull(
            item.year?.toString(),
            item.itemCountLabel,
        ).joinToString(" • ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (devMode) {
            Text(
                text = item.ratingKey,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
