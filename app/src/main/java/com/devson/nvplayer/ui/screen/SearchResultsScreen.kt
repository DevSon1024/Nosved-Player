package com.devson.nvplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.model.LayoutMode
import com.devson.nvplayer.model.Video
import com.devson.nvplayer.model.applySort
import com.devson.nvplayer.ui.component.InformationBottomSheet
import com.devson.nvplayer.ui.components.IconToggleButton
import com.devson.nvplayer.ui.components.SettingsSectionLabel
import com.devson.nvplayer.ui.screens.videolist.components.list.VideoListContent
import com.devson.nvplayer.viewmodel.VideoListViewModel
import com.devson.nvplayer.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    query: String,
    viewModel: VideoListViewModel,
    homeViewModel: HomeViewModel,
    onVideoSelected: (Video, List<Video>, Long) -> Unit,
    onBack: () -> Unit
) {
    val viewSettings by viewModel.viewSettings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val history by homeViewModel.history.collectAsState()
    val historyMap = remember(history) { history.associateBy { it.uri } }

    var results by remember { mutableStateOf<List<Video>>(emptyList()) }
    var showLayoutSheet by remember { mutableStateOf(false) }
    var selectedInfoVideo by remember { mutableStateOf<Video?>(null) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(query, isLoading) {
        if (!isLoading) {
            results = viewModel.getSearchResults(query)
                .applySort(viewSettings.sortField, viewSettings.sortDirection)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Search For '$query'",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showLayoutSheet = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = "View Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                VideoListContent(
                    videos = results,
                    settings = viewSettings,
                    selectedVideos = emptySet(),
                    historyMap = historyMap,
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 32.dp
                    ),
                    onVideoClick = { video ->
                        onVideoSelected(video, results, historyMap[video.uri]?.lastPositionMs ?: 0L)
                    },
                    onVideoLongClick = {},
                    onInfoClick = { video -> selectedInfoVideo = video }
                )
            }
        }

        if (showLayoutSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLayoutSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Layout Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        IconToggleButton(
                            label = "List",
                            selected = viewSettings.layoutMode == LayoutMode.LIST,
                            selectedIcon = Icons.Filled.ViewAgenda,
                            unselectedIcon = Icons.Outlined.ViewAgenda,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.updateLayoutMode(LayoutMode.LIST) }
                        )
                        IconToggleButton(
                            label = "Grid",
                            selected = viewSettings.layoutMode == LayoutMode.GRID,
                            selectedIcon = Icons.Filled.GridView,
                            unselectedIcon = Icons.Outlined.GridView,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.updateLayoutMode(LayoutMode.GRID) }
                        )
                    }

                    if (viewSettings.layoutMode == LayoutMode.GRID) {
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionLabel("Grid Columns")
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (1..4).forEach { columns ->
                                val isSelected = viewSettings.gridColumns == columns
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.updateGridColumns(columns) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = columns.toString(),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Info bottom sheet for individual search result
        selectedInfoVideo?.let { video ->
            InformationBottomSheet(
                selectedVideos = setOf(video),
                onDismiss = { selectedInfoVideo = null }
            )
        }
    }
}
