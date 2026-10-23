package com.devson.nvplayer.ui.screen.library

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devson.nvplayer.data.model.LibraryCategory
import com.devson.nvplayer.data.model.LibraryMediaItem
import com.devson.nvplayer.data.model.LibraryMediaType
import com.devson.nvplayer.data.model.LibraryUiState
import com.devson.nvplayer.ui.common.components.PreviewFloatingActionButton
import com.devson.nvplayer.ui.common.popup.SearchSuggestionsPopup
import com.devson.nvplayer.ui.screen.NetworkStreamDialog
import com.devson.nvplayer.ui.screen.library.components.CategoryFilterRow
import com.devson.nvplayer.ui.screen.library.components.ContinueWatchingRow
import com.devson.nvplayer.ui.screen.library.components.MediaHeroCarousel
import com.devson.nvplayer.ui.screen.library.components.MediaPosterCard
import com.devson.nvplayer.ui.screen.library.components.RecentlyAddedRow
import com.devson.nvplayer.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHomeScreen(
    viewModel: LibraryViewModel,
    onMediaClick: (LibraryMediaItem) -> Unit,
    onSeriesClick: (Long) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onPlayStream: (Uri) -> Unit = {},
    onNetworkHistoryClick: () -> Unit = {},
    onNavigateToYtdlpSettings: () -> Unit = {},
    onNavigateToStreak: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()

    var searchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showNetworkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    if (showNetworkDialog) {
        NetworkStreamDialog(
            onDismiss = { showNetworkDialog = false },
            onPlay = { uri ->
                showNetworkDialog = false
                onPlayStream(uri)
            },
            onHistoryClick = {
                showNetworkDialog = false
                onNetworkHistoryClick()
            },
            onNavigateToYtdlpSettings = {
                showNetworkDialog = false
                onNavigateToYtdlpSettings()
            }
        )
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        if (searchActive) {
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { query ->
                                    searchText = query
                                    viewModel.onSearchQueryChanged(query)
                                },
                                placeholder = {
                                    Text(
                                        text = "Search movies, shows, streams...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        if (searchText.isNotBlank()) {
                                            searchActive = false
                                            val query = searchText.trim()
                                            searchText = ""
                                            viewModel.clearSearchSuggestions()
                                            onNavigateToSearch(query)
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent
                                )
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VideoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(20.dp)
                                    )
                                }
                                Text(
                                    text = "Library",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                )
                            }
                        }
                    },
                    actions = {
                        if (searchActive) {
                            IconButton(onClick = {
                                searchActive = false
                                searchText = ""
                                viewModel.clearSearchSuggestions()
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close Search"
                                )
                            }
                        } else {
                            IconButton(onClick = onNavigateToStreak) {
                                Icon(
                                    imageVector = Icons.Filled.Whatshot,
                                    contentDescription = "Watch Streak",
                                    tint = Color(0xFFFF6D00)
                                )
                            }
                            IconButton(onClick = { showNetworkDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = "Play Network Stream",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { searchActive = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                if (searchActive && searchSuggestions.isNotEmpty()) {
                    SearchSuggestionsPopup(
                        suggestions = searchSuggestions,
                        keyboard = keyboardController,
                        onSuggestionClick = { title ->
                            searchActive = false
                            searchText = ""
                            viewModel.clearSearchSuggestions()
                            onNavigateToSearch(title)
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            val quickFabVideo by viewModel.videoListViewModel.quickFabLastPlayedVideo.collectAsStateWithLifecycle()
            val viewSettings by viewModel.videoListViewModel.viewSettings.collectAsStateWithLifecycle()

            val continueWatchingItem = (uiState as? LibraryUiState.Success)?.continueWatching?.firstOrNull()

            val effectiveUri = continueWatchingItem?.videoUri ?: quickFabVideo?.uri
            val effectiveTitle = continueWatchingItem?.title ?: quickFabVideo?.title
            val effectiveDuration = continueWatchingItem?.durationMs ?: quickFabVideo?.duration ?: 0L
            val effectivePosition = continueWatchingItem?.playbackPositionMs ?: 0L

            if (viewSettings.showQuickFab && effectiveUri != null) {
                PreviewFloatingActionButton(
                    enablePreview = viewSettings.enableFabPreview,
                    previewUri = effectiveUri,
                    previewTitle = effectiveTitle,
                    previewDurationMs = effectiveDuration,
                    previewLastPositionMs = effectivePosition,
                    modifier = Modifier.padding(bottom = 88.dp),
                    onPlay = {
                        val currentUiState = uiState
                        val allItems = if (currentUiState is LibraryUiState.Success) currentUiState.allItems else emptyList()
                        val matchItem = allItems.find { it.videoUri == effectiveUri }
                        if (matchItem != null) {
                            onMediaClick(matchItem)
                        } else {
                            onMediaClick(
                                LibraryMediaItem(
                                    id = effectiveUri,
                                    title = effectiveTitle ?: "Video",
                                    cleanedTitle = effectiveTitle ?: "Video",
                                    videoUri = effectiveUri,
                                    type = LibraryMediaType.MOVIE,
                                    durationMs = effectiveDuration,
                                    playbackPositionMs = effectivePosition
                                )
                            )
                        }
                    },
                    onNetworkStreamClick = { showNetworkDialog = true }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                    is LibraryUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is LibraryUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is LibraryUiState.Success -> {
                        val isGlobalEmpty = state.allItems.isEmpty() && selectedCategory == LibraryCategory.ALL && state.heroItems.isEmpty() && state.continueWatching.isEmpty()
                        if (isGlobalEmpty) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center
                            ) {
                                LibraryEmptyState(
                                    onRefresh = { viewModel.refresh() },
                                    onStreamClick = { showNetworkDialog = true }
                                )
                            }
                        } else {
                            val chunkedItems = remember(state.allItems) { state.allItems.chunked(3) }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 128.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // 1. Hero Carousel (3-5 items)
                                if (state.heroItems.isNotEmpty() && selectedCategory == LibraryCategory.ALL) {
                                    item(key = "hero_carousel") {
                                        MediaHeroCarousel(
                                            items = state.heroItems,
                                            onItemClick = { item ->
                                                if (item.seriesId != null && item.type == LibraryMediaType.TV_SHOW) {
                                                    onSeriesClick(item.seriesId)
                                                } else {
                                                    onMediaClick(item)
                                                }
                                            }
                                        )
                                    }
                                }

                                // 2. Category Filter Row
                                item(key = "category_filter") {
                                    CategoryFilterRow(
                                        selectedCategory = selectedCategory,
                                        onSelectCategory = { viewModel.selectCategory(it) }
                                    )
                                }

                                if (state.allItems.isEmpty()) {
                                    item(key = "empty_category") {
                                        CategoryEmptyState(
                                            category = selectedCategory,
                                            onScanClick = { viewModel.refresh() }
                                        )
                                    }
                                } else {
                                    // 4. Continue Watching Row
                                    if (state.continueWatching.isNotEmpty() && selectedCategory == LibraryCategory.ALL) {
                                        item(key = "continue_watching") {
                                            ContinueWatchingRow(
                                                items = state.continueWatching,
                                                onItemClick = onMediaClick
                                            )
                                        }
                                    }

                                    // 5. Recently Added Row
                                    if (state.recentlyAdded.isNotEmpty()) {
                                        item(key = "recently_added") {
                                            RecentlyAddedRow(
                                                title = when (selectedCategory) {
                                                    LibraryCategory.ALL -> "Recently Added"
                                                    LibraryCategory.MOVIES -> "Latest Movies"
                                                    LibraryCategory.SHOWS -> "Popular Shows"
                                                },
                                                items = state.recentlyAdded,
                                                onItemClick = { item ->
                                                    if (item.seriesId != null && item.type == LibraryMediaType.TV_SHOW) {
                                                        onSeriesClick(item.seriesId)
                                                    } else {
                                                        onMediaClick(item)
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    // 6. Categorized Section: Only shown when filtering by specific category (Movies, Shows)
                                    if (selectedCategory != LibraryCategory.ALL) {
                                        item(key = "all_media_header") {
                                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                                Text(
                                                    text = "All ${selectedCategory.displayName}",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 18.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        items(
                                            items = chunkedItems,
                                            key = { row -> row.firstOrNull()?.id ?: "" },
                                            contentType = { "poster_row" }
                                        ) { rowItems ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                for (item in rowItems) {
                                                    MediaPosterCard(
                                                        item = item,
                                                        onClick = {
                                                            if (item.seriesId != null && item.type == LibraryMediaType.TV_SHOW) {
                                                                onSeriesClick(item.seriesId)
                                                            } else {
                                                                onMediaClick(item)
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                repeat(3 - rowItems.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun LibraryEmptyState(
    onRefresh: () -> Unit,
    onStreamClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.MovieFilter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your Smart Library is Empty",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Scan your local storage or stream online videos directly to populate your library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onRefresh,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Storage")
            }

            OutlinedButton(
                onClick = onStreamClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stream URL")
            }
        }
    }
}

@Composable
private fun CategoryEmptyState(
    category: LibraryCategory,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (category) {
                        LibraryCategory.MOVIES -> Icons.Filled.Movie
                        LibraryCategory.SHOWS -> Icons.Filled.Tv
                        else -> Icons.Filled.VideoLibrary
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No ${category.displayName} Found",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "No media files detected under this category. Switch to another tab or refresh your library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onScanClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Re-scan Library")
        }
    }
}
