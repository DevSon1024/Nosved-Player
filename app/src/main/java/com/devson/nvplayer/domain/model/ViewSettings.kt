package com.devson.nvplayer.domain.model

enum class DefaultScreen {
    HOME, FOLDERS, HISTORY, VIDEO_LIST
}

data class ViewSettings(
    val showQuickFab: Boolean = true,
    val selectByThumbnail: Boolean = false,
    val enableFabPreview: Boolean = true,
    val scanFoldersList: Set<String> = emptySet(),
    val showHistoryCard: Boolean = true,
    val showStorageTracker: Boolean = true,
    val showLatestVideos: Boolean = true,
    val defaultScreen: DefaultScreen = DefaultScreen.HOME,
    
    // New fields for VideoList UI
    val layoutMode: LayoutMode = LayoutMode.LIST,
    val gridColumns: Int = 2,
    val showThumbnail: Boolean = true,
    val showLength: Boolean = true,
    val displayLengthOverThumbnail: Boolean = true,
    val showFileExtension: Boolean = true,
    val showSize: Boolean = true,
    val showDate: Boolean = true,
    val showPath: Boolean = false,
    val showPlayedTime: Boolean = true,
    val showResolution: Boolean = true,
    val showFrameRate: Boolean = true,
    val sortField: SortField = SortField.TITLE,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val viewMode: ViewMode = ViewMode.ALL_FOLDERS,
    val thumbnailMode: ThumbnailMode = ThumbnailMode.FIRST_FRAME,
    val thumbnailFramePosition: Float = 33f
)
