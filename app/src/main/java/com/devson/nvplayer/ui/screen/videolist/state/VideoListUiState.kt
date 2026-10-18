package com.devson.nvplayer.ui.screens.videolist.state

import androidx.compose.runtime.Immutable
import com.devson.nvplayer.domain.model.StorageVolumeInfo
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.domain.model.ViewSettings

@Immutable
sealed interface ExplorerItem {
    @Immutable
    data class FolderItem(val folder: VideoFolder) : ExplorerItem
    @Immutable
    data class VideoItem(val video: Video) : ExplorerItem
}

@Immutable
data class PathSegment(val name: String, val absolutePath: String)

@Immutable
data class VideoListUiState(
    val videosByFolder: Map<VideoFolder, List<Video>> = emptyMap(),
    val selectedVideos: Set<Video> = emptySet(),
    val selectedFolders: Set<VideoFolder> = emptySet(),
    val selectedFolder: VideoFolder? = null,
    val viewSettings: ViewSettings = ViewSettings(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val explorerItems: List<ExplorerItem> = emptyList(),
    val currentExplorerPath: String = android.os.Environment.getExternalStorageDirectory().absolutePath,
    val searchText: String = "",
    val searchActive: Boolean = false,
    val searchSuggestions: List<String> = emptyList(),
    val availableStorages: List<StorageVolumeInfo> = emptyList(),
    val selectedStorage: StorageVolumeInfo? = null
)

