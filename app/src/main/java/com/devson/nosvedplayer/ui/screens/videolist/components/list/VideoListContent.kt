package com.devson.nosvedplayer.ui.screens.videolist.components.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devson.nosvedplayer.model.LayoutMode
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.model.WatchHistory
import com.devson.nosvedplayer.ui.components.CustomEmptyStateView

@Composable
fun VideoListContent(
    videos: List<Video>,
    settings: ViewSettings,
    selectedVideos: Set<Video>,
    historyMap: Map<String, WatchHistory> = emptyMap(),
    onVideoClick: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (videos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CustomEmptyStateView(
                heading  = "No Videos Here",
                subtext  = "This folder appears to be empty. Try pulling down to refresh.",
                ctaLabel = "Scan Device for Videos"
            )
        }
        return
    }
    if (settings.layoutMode == LayoutMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.gridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(videos) { video ->
                VideoGridItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    lastPositionMs = historyMap[video.uri]?.lastPositionMs ?: 0L,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) }
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            )
        ) {
            items(videos) { video ->
                VideoListItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    lastPositionMs = historyMap[video.uri]?.lastPositionMs ?: 0L,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) }
                )
            }
        }
    }
}
