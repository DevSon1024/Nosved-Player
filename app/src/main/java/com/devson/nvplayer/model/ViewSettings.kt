package com.devson.nvplayer.model

enum class DefaultScreen {
    HOME, FOLDERS, HISTORY
}

data class ViewSettings(
    val recognizeNoMedia: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val showFloatingButton: Boolean = true,
    val selectByThumbnail: Boolean = false,
    val enableFabPreview: Boolean = true,
    val scanFoldersList: Set<String> = emptySet(),
    val showHistoryCard: Boolean = true,
    val showVideoCard: Boolean = true,
    val showStorageTracker: Boolean = true,
    val defaultScreen: DefaultScreen = DefaultScreen.HOME
)
