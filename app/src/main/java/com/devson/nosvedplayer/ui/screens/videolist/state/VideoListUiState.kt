package com.devson.nosvedplayer.ui.screens.videolist.state

import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewSettings

data class VideoListUiState(
    val videosByFolder: Map<VideoFolder, List<Video>> = emptyMap(),
    val selectedVideos: Set<Video> = emptySet(),
    val selectedFolders: Set<VideoFolder> = emptySet(),
    val selectedFolder: VideoFolder? = null,
    val viewSettings: ViewSettings = ViewSettings(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val explorerNodes: Pair<List<VideoFolder>, List<Video>> = Pair(emptyList(), emptyList()),
    val currentExplorerPath: String? = null,
    val searchText: String = "",
    val searchActive: Boolean = false,
    val searchSuggestions: List<String> = emptyList()
)
