package com.devson.nvplayer.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.devson.nvplayer.ui.component.TopBar
import com.devson.nvplayer.ui.component.VideoListItem
import com.devson.nvplayer.viewmodel.FolderViewModel

@Composable
fun FolderScreen(
    folderName: String,
    viewModel: FolderViewModel,
    onBackClick: () -> Unit,
    onVideoClick: (Uri) -> Unit
) {
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(folderName) {
        viewModel.loadVideos(folderName)
    }

    Scaffold(
        topBar = {
            TopBar(title = folderName, onBackClick = onBackClick)
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (videos.isEmpty()) {
                Text(
                    text = "No videos in this folder.",
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(videos) { video ->
                        VideoListItem(
                            videoItem = video,
                            onClick = { onVideoClick(video.uri) }
                        )
                    }
                }
            }
        }
    }
}
