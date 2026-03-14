package com.devson.nosvedplayer.model

enum class SortOrder {
    A_TO_Z,
    Z_TO_A,
    NEWEST_FIRST,
    OLDEST_FIRST,
    LARGEST_FIRST,
    SMALLEST_FIRST
}

data class ViewSettings(
    val isGrid: Boolean = false,
    val gridColumns: Int = 2,
    val sortOrder: SortOrder = SortOrder.A_TO_Z,
    val showThumbnail: Boolean = true,
    val showDuration: Boolean = true,
    val showSize: Boolean = true,
    val showDate: Boolean = false, // Not tracked yet, but placeholder
    val showSubtitleType: Boolean = false,
    val showResolution: Boolean = false,
    val showFramerate: Boolean = false,
    val showPlayedTime: Boolean = false,
    val showPath: Boolean = false,
    val showFileExtension: Boolean = false,
    // Folder specific
    val showFolderVideoCount: Boolean = true,
    val showFolderSize: Boolean = false,
    val showFolderDate: Boolean = false
)
