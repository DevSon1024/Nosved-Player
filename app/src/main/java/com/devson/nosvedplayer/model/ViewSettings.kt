package com.devson.nosvedplayer.model

enum class ViewMode {
    ALL_FOLDERS,
    FILES,
    FOLDERS
}

enum class LayoutMode {
    LIST,
    GRID
}

enum class SortField {
    TITLE, DATE, PLAYED_TIME, STATUS, LENGTH, SIZE, RESOLUTION, PATH, FRAME_RATE, TYPE
}

enum class SortDirection {
    ASCENDING, DESCENDING
}

data class ViewSettings(
    val viewMode: ViewMode = ViewMode.ALL_FOLDERS,
    val layoutMode: LayoutMode = LayoutMode.LIST,
    val gridColumns: Int = 2,
    val sortField: SortField = SortField.TITLE,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val showThumbnail: Boolean = true,
    val showLength: Boolean = true,
    val showFileExtension: Boolean = false,
    val showPlayedTime: Boolean = false,
    val showResolution: Boolean = false,
    val showFrameRate: Boolean = false,
    val showPath: Boolean = false,
    val showSize: Boolean = true,
    val showDate: Boolean = false,
    val displayLengthOverThumbnail: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val recognizeNoMedia: Boolean = false
)
