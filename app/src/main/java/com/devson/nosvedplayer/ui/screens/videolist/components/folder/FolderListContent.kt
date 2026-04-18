package com.devson.nosvedplayer.ui.screens.videolist.components.folder

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
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devson.nosvedplayer.R
import com.devson.nosvedplayer.model.LayoutMode
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.model.WatchHistory
import com.devson.nosvedplayer.ui.components.CustomEmptyStateView

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderListContent(
    folders: Map<VideoFolder, List<Video>>,
    settings: ViewSettings,
    selectedFolders: Set<VideoFolder>,
    historyMap: Map<String, WatchHistory> = emptyMap(),
    onFolderClick: (VideoFolder) -> Unit,
    onFolderLongClick: (VideoFolder) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val haptic = LocalHapticFeedback.current
    val sortedFolders = remember(folders) { folders.keys.toList().sortedBy { it.name.lowercase() } }

    if (sortedFolders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CustomEmptyStateView(
                icon    = Icons.Filled.VideoLibrary,
                heading = stringResource(R.string.folder_no_folders_found),
                subtext = stringResource(R.string.folder_no_folders_desc),
                ctaLabel = stringResource(R.string.folder_scan_cta)
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
                top = contentPadding.calculateTopPadding() + 8.dp,
                end = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 40.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortedFolders) { folder ->
                FolderGridItem(
                    folder = folder,
                    videos = folders[folder] ?: emptyList(),
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    historyMap = historyMap,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
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
                bottom = contentPadding.calculateBottomPadding() + 32.dp
            )
        ) {
            items(sortedFolders) { folder ->
                FolderListItem(
                    folder = folder,
                    videos = folders[folder] ?: emptyList(),
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    historyMap = historyMap,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
                    }
                )
            }
        }
    }
}
