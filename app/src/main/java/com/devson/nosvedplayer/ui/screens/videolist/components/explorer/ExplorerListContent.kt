package com.devson.nosvedplayer.ui.screens.videolist.components.explorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.devson.nosvedplayer.model.LayoutMode
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.model.WatchHistory
import com.devson.nosvedplayer.ui.screens.videolist.components.list.VideoGridItem
import com.devson.nosvedplayer.ui.screens.videolist.components.list.VideoListItem
import com.devson.nosvedplayer.ui.screens.videolist.components.folder.FolderGridItem
import com.devson.nosvedplayer.ui.screens.videolist.components.folder.FolderListItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerListContent(
    folders: List<VideoFolder>,
    videos: List<Video>,
    allVideosForSize: List<Video>,
    settings: ViewSettings,
    selectedFolders: Set<VideoFolder>,
    selectedVideos: Set<Video>,
    historyMap: Map<String, WatchHistory> = emptyMap(),
    onFolderClick: (VideoFolder) -> Unit,
    onFolderLongClick: (VideoFolder) -> Unit,
    onVideoClick: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val haptic = LocalHapticFeedback.current

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
            items(folders) { folder ->
                val folderVideos = remember(folder, allVideosForSize) { allVideosForSize.filter { it.path.startsWith(folder.id) } }
                FolderGridItem(
                    folder = folder,
                    videos = folderVideos,
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
                    }
                )
            }
            items(videos) { video ->
                VideoGridItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    lastPositionMs = historyMap[video.uri]?.lastPositionMs ?: 0L,
                    onClick = { onVideoClick(video) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onVideoLongClick(video)
                    }
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
            items(folders) { folder ->
                val folderVideos = remember(folder, allVideosForSize) { allVideosForSize.filter { it.path.startsWith(folder.id) } }
                FolderListItem(
                    folder = folder,
                    videos = folderVideos,
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
                    }
                )
            }
            items(videos) { video ->
                VideoListItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    lastPositionMs = historyMap[video.uri]?.lastPositionMs ?: 0L,
                    onClick = { onVideoClick(video) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onVideoLongClick(video)
                    }
                )
            }
        }
    }
}
