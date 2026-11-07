package com.devson.nvplayer.ui.screens.videolist.components.explorer

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.R
import com.devson.nvplayer.domain.model.LayoutMode
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.domain.model.ViewSettings
import com.devson.nvplayer.domain.model.WatchHistory
import com.devson.nvplayer.ui.common.components.CustomEmptyStateView
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoGridItem
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoListItem
import com.devson.nvplayer.ui.screen.videolist.components.folder.FolderGridItem
import com.devson.nvplayer.ui.screen.videolist.components.folder.FolderListItem
import com.devson.nvplayer.ui.common.components.fastscroll.FastScroller
import com.devson.nvplayer.ui.common.components.fastscroll.FastScrollSectionHelper
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
    contentPadding: PaddingValues = PaddingValues(0.dp),
    isPermissionRequired: Boolean = false,
    onPermissionClick: (() -> Unit)? = null,
    onScanClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isPermissionRequired) {
                CustomEmptyStateView(
                    icon = Icons.Filled.FolderOpen,
                    heading = "Storage Access Required",
                    subtext = "Android 15 requires All Files Access permission to browse and play videos from external USB drives.",
                    ctaLabel = "Grant Permission",
                    onCtaClick = onPermissionClick
                )
            } else {
                CustomEmptyStateView(
                    icon = Icons.Filled.FolderOpen,
                    heading = "No Files or Folders Found",
                    subtext = "No playable media found in this location.",
                    ctaLabel = stringResource(R.string.folder_scan_cta),
                    onCtaClick = onScanClick
                )
            }
        }
        return
    }

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

    val mostRecentHistoryUri = remember(historyMap) {
        historyMap.values.filter { it.lastPlayedAt > 0L }.maxByOrNull { it.lastPlayedAt }?.uri
    }

    val lastPlayedInExplorer = remember(items, historyMap, folderVideosMap) {
        val videoItems = items.filterIsInstance<ExplorerItem.VideoItem>()
        val folderItems = items.filterIsInstance<ExplorerItem.FolderItem>()

        val playedVideoItem = videoItems.mapNotNull { item ->
            val hist = historyMap[item.video.uri]
            val playedAt = hist?.lastPlayedAt
                ?: if (item.video.lastPlayedAt != null && item.video.lastPlayedAt > 0L) item.video.lastPlayedAt else null
            if (playedAt != null && playedAt > 0L) (item as ExplorerItem) to playedAt else null
        }.maxByOrNull { it.second }

        val playedFolderItem = folderItems.mapNotNull { item ->
            val vids = folderVideosMap[item.folder] ?: emptyList()
            val maxPlayedAt = vids.mapNotNull { v ->
                val hist = historyMap[v.uri]
                val p = hist?.lastPlayedAt ?: if (v.lastPlayedAt != null && v.lastPlayedAt > 0L) v.lastPlayedAt else null
                if (p != null && p > 0L) p else null
            }.maxOrNull()
            if (maxPlayedAt != null && maxPlayedAt > 0L) (item as ExplorerItem) to maxPlayedAt else null
        }.maxByOrNull { it.second }

        listOfNotNull(playedVideoItem, playedFolderItem).maxByOrNull { it.second }
    }

    var lastHandledExplorerTimestamp by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(items, lastPlayedInExplorer, settings.autoScrollToLastPlayed) {
        if (!settings.autoScrollToLastPlayed || items.isEmpty() || lastPlayedInExplorer == null) return@LaunchedEffect
        val (item, playedTimestamp) = lastPlayedInExplorer
        if (playedTimestamp > 0L && playedTimestamp != lastHandledExplorerTimestamp) {
            lastHandledExplorerTimestamp = playedTimestamp
            val targetIndex = items.indexOf(item)
            if (targetIndex > 0) {
                val preScrollIndex = (targetIndex - 2).coerceAtLeast(0)
                if (settings.layoutMode == LayoutMode.GRID) {
                    if (targetIndex > 8) gridState.scrollToItem(preScrollIndex)
                    gridState.animateScrollToItem(targetIndex)
                } else {
                    if (targetIndex > 8) listState.scrollToItem(preScrollIndex)
                    listState.animateScrollToItem(targetIndex)
                }
            }
        }
    }

    FastScroller(
        itemCount = items.size,
        listState = if (settings.layoutMode == LayoutMode.LIST) listState else null,
        gridState = if (settings.layoutMode == LayoutMode.GRID) gridState else null,
        contentPadding = contentPadding,
        sectionLabelProvider = { index ->
            val item = items.getOrNull(index)
            val folderVideos = if (item is ExplorerItem.FolderItem) folderVideosMap[item.folder] else null
            FastScrollSectionHelper.getExplorerSectionLabel(
                item = item,
                folderVideos = folderVideos,
                sortField = settings.sortField
            )
        }
    ) {
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
                        val isRecentlyPlayed = remember(folderVideos, mostRecentHistoryUri) {
                            mostRecentHistoryUri != null && folderVideos.any { it.uri == mostRecentHistoryUri }
                        }
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
                            isRecentlyPlayed = isRecentlyPlayed,
                            historyMap = historyMap,
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    }
                    is ExplorerItem.VideoItem -> {
                        val video = item.video
                        val isRecentlyPlayed = mostRecentHistoryUri != null && video.uri == mostRecentHistoryUri
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
                            isRecentlyPlayed = isRecentlyPlayed,
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
                        val isRecentlyPlayed = remember(folderVideos, mostRecentHistoryUri) {
                            mostRecentHistoryUri != null && folderVideos.any { it.uri == mostRecentHistoryUri }
                        }
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
                            isRecentlyPlayed = isRecentlyPlayed,
                            historyMap = historyMap,
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    }
                    is ExplorerItem.VideoItem -> {
                        val video = item.video
                        val isRecentlyPlayed = mostRecentHistoryUri != null && video.uri == mostRecentHistoryUri
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
                            isRecentlyPlayed = isRecentlyPlayed,
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
}