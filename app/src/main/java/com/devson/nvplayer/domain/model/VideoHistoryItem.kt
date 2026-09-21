package com.devson.nvplayer.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoHistoryItem(
    val historyId: String,
    val originalName: String,
    val watchedDate: String,
    val lastPlayedAt: Long,
    val durationMs: Long,
    val progress: Float,
    val lastPositionMs: Long,
    val isDeleted: Boolean,
    val isThumbnailAvailable: Boolean,
    val uri: String?,
    val path: String?,
    val folderName: String? = null,
    val isCompleted: Boolean = false,
    val totalPlaybackTimeMs: Long = 0L,
    val isNetworkStream: Boolean = false,
    val rawUri: String = uri ?: historyId,
    val rawPath: String? = path
) {
    val isAvailable: Boolean
        get() = !isDeleted

    val displayTitleWithStatus: String
        get() = if (isDeleted) "$originalName [DELETED]" else originalName

    val statusLabel: String?
        get() = if (isDeleted) "[DELETED]" else null
}
