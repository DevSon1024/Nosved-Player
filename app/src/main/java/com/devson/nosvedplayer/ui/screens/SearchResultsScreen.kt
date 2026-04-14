package com.devson.nosvedplayer.ui.screens

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.applySort
import com.devson.nosvedplayer.viewmodel.VideoListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    query: String,
    onVideoSelected: (Video, List<Video>, Long) -> Unit,
    onBack: () -> Unit
) {
    val activity = LocalActivity.current as ComponentActivity
    val viewModel: VideoListViewModel = viewModel(activity)
    val viewSettings by viewModel.viewSettings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val homeViewModel: com.devson.nosvedplayer.viewmodel.HomeViewModel = viewModel(activity)
    val history by homeViewModel.history.collectAsState()
    val historyMap = remember(history) { history.associateBy { it.uri } }

    var results by remember { mutableStateOf<List<Video>>(emptyList()) }

    LaunchedEffect(query, isLoading) {
        if (!isLoading) {
            results = viewModel.getSearchResults(query)
                .applySort(viewSettings.sortField, viewSettings.sortDirection)
        }
    }

    Scaffold(
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                VideoListContent(
                    videos = results,
                    settings = viewSettings,
                    selectedVideos = emptySet(),
                    historyMap = historyMap,
                    onVideoClick = { video ->
                        onVideoSelected(video, results, historyMap[video.uri]?.lastPositionMs ?: 0L)
                    },
                    onVideoLongClick = {}
                )
            }
        }
    }
}
