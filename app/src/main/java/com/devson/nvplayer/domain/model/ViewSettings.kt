package com.devson.nvplayer.domain.model

enum class DefaultScreen {
    HOME, FOLDERS, HISTORY, VIDEO_LIST, LIBRARY, VAULT, SETTINGS
}

data class ViewSettings(
    val showQuickFab: Boolean = true,
    val selectByThumbnail: Boolean = false,
    val enableFabPreview: Boolean = true,
    val scanFoldersList: Set<String> = emptySet(),
    val showHistoryCard: Boolean = true,
    val isShortcutsVisible: Boolean = true,
    val isDetailsVisible: Boolean = true,
    val homeSectionOrder: List<HomeSection> = listOf(HomeSection.SHORTCUTS, HomeSection.HISTORY, HomeSection.DETAILS),
    val showStorageTracker: Boolean = true,
    val showLatestVideos: Boolean = true,
    val defaultScreen: DefaultScreen = DefaultScreen.LIBRARY,
    
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
    val thumbnailFramePosition: Float = 33f,
    val newVideoDaysThreshold: Int = 7
)
