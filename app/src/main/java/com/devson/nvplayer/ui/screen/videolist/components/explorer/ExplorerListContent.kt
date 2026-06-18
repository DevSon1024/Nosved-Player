package com.devson.nvplayer.ui.screens.videolist.components.explorer

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.domain.model.LayoutMode
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.domain.model.ViewSettings
import com.devson.nvplayer.domain.model.WatchHistory
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoGridItem
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoListItem
import com.devson.nvplayer.ui.screen.videolist.components.folder.FolderGridItem
import com.devson.nvplayer.ui.screen.videolist.components.folder.FolderListItem
import com.devson.nvplayer.ui.screens.videolist.state.ExplorerItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerListContent(
    items: List<ExplorerItem>,
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

    val folders = remember(items) {
        items.filterIsInstance<ExplorerItem.FolderItem>().map { it.folder }
    }

    val folderVideosMap = remember(folders, allVideosForSize) {
        folders.associateWith { folder ->
            allVideosForSize.filter { it.path.startsWith(folder.id) }
        }
    }

    val currentOnFolderClick by rememberUpdatedState(onFolderClick)
    val currentOnFolderLongClick by rememberUpdatedState(onFolderLongClick)
    val currentOnVideoClick by rememberUpdatedState(onVideoClick)
    val currentOnVideoLongClick by rememberUpdatedState(onVideoLongClick)

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
            items(
                items = items,
                key = { item ->
                    when (item) {
                        is ExplorerItem.FolderItem -> "folder_${item.folder.id}"
                        is ExplorerItem.VideoItem -> "video_${item.video.uri}"
                    }
                },
                contentType = { item ->
                    when (item) {
                        is ExplorerItem.FolderItem -> "folder_item"
                        is ExplorerItem.VideoItem -> "video_item"
                    }
                }
            ) { item ->
                when (item) {
                    is ExplorerItem.FolderItem -> {
                        val folder = item.folder
                        val folderVideos = remember(folder, folderVideosMap) { folderVideosMap[folder] ?: emptyList() }
                        val onClick = remember(folder) { { currentOnFolderClick(folder) } }
                        val onLongClick = remember(folder) {
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnFolderLongClick(folder)
                            }
                        }
                        FolderGridItem(
                            folder = folder,
                            videos = folderVideos,
                            settings = settings,
                            isSelected = folder in selectedFolders,
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    }
                    is ExplorerItem.VideoItem -> {
                        val video = item.video
                        val onClick = remember(video) { { _: Video -> currentOnVideoClick(video) } }
                        val onLongClick = remember(video) {
                            { _: Video ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnVideoLongClick(video)
                            }
                        }
                        VideoGridItem(
                            video = video,
                            settings = settings,
                            isSelected = video in selectedVideos,
                            lastPositionMs = historyMap[video.uri]?.lastPositionMs ?: 0L,
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    }
                }
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
            items(
                items = items,
                key = { item ->
                    when (item) {
                        is ExplorerItem.FolderItem -> "folder_${item.folder.id}"
                        is ExplorerItem.VideoItem -> "video_${item.video.uri}"
                    }
                },
                contentType = { item ->
                    when (item) {
                        is ExplorerItem.FolderItem -> "folder_item"
                        is ExplorerItem.VideoItem -> "video_item"
                    }
                }
            ) { item ->
                when (item) {
                    is ExplorerItem.FolderItem -> {
                        val folder = item.folder
                        val folderVideos = remember(folder, folderVideosMap) { folderVideosMap[folder] ?: emptyList() }
                        val onClick = remember(folder) { { currentOnFolderClick(folder) } }
                        val onLongClick = remember(folder) {
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnFolderLongClick(folder)
                            }
                        }
                        FolderListItem(
                            folder = folder,
                            videos = folderVideos,
                            settings = settings,
                            isSelected = folder in selectedFolders,
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    }
                    is ExplorerItem.VideoItem -> {
                        val video = item.video
                        val onClick = remember(video) { { _: Video -> currentOnVideoClick(video) } }
                        val onLongClick = remember(video) {
                            { _: Video ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnVideoLongClick(video)
                            }
                        }
                        VideoListItem(
                            video = video,
                            settings = settings,
                            isSelected = video in selectedVideos,
                            lastPositionMs = historyMap[video.uri]?.lastPositionMs ?: 0L,
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    }
                }
            }
        }
    }
}
