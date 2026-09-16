package com.devson.nvplayer.domain.model

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

enum class LayoutMode {
    LIST, GRID
}

enum class ViewMode {
    ALL_FOLDERS, FILES, FOLDERS
}

enum class SortField {
    TITLE, DATE, PLAYED_TIME, LENGTH, SIZE
}

enum class SortDirection {
    ASCENDING, DESCENDING
}

@Immutable
data class WatchHistory(
    val uri: String,
    val lastPositionMs: Long,
    val lastPlayedAt: Long = 0L,
    val videoTitle: String? = null,
    val durationMs: Long = 0L,
    val fileSize: Long = 0L
)

@Immutable
data class Video(
    val uri: String,
    val title: String,
    val duration: Long,
    val folderName: String,
    val path: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val playedTime: Long? = null,
    val lastPlayedAt: Long? = null,
    val resolution: String? = null,
    val frameRate: Float? = null,
    val dateExpires: Long? = null,
    val thumbnailUri: String? = null,
    val embeddedSubtitles: List<String> = emptyList(),
    val externalSubtitles: List<String> = emptyList()
)

fun List<Video>.applySort(
    field: SortField,
    direction: SortDirection,
    historyMap: Map<String, WatchHistory> = emptyMap()
): List<Video> {
    val sorted = when (field) {
        SortField.TITLE -> sortedBy { it.title.lowercase() }
        SortField.DATE -> sortedBy {
            if (it.dateAdded.toString().length < 13) it.dateAdded * 1000L else it.dateAdded
        }
        SortField.PLAYED_TIME -> sortedBy {
            historyMap[it.uri]?.lastPlayedAt ?: it.lastPlayedAt ?: it.playedTime ?: 0L
        }
        SortField.LENGTH -> sortedBy { it.duration }
        SortField.SIZE -> sortedBy { it.size }
    }
    return if (direction == SortDirection.DESCENDING) sorted.reversed() else sorted
}

fun Video.getSectionLabel(
    sortField: SortField,
    historyMap: Map<String, WatchHistory> = emptyMap()
): String {
    return when (sortField) {
        SortField.TITLE -> {
            val firstChar = title.trim().firstOrNull()?.uppercaseChar()
            if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
        }
        SortField.DATE -> {
            val ms = if (dateAdded.toString().length < 13) dateAdded * 1000L else dateAdded
            if (ms <= 0L) {
                "Unknown"
            } else {
                try {
                    java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(ms))
                } catch (_: Exception) {
                    "Date"
                }
            }
        }
        SortField.PLAYED_TIME -> {
            val lastPlayed = historyMap[uri]?.lastPlayedAt ?: lastPlayedAt ?: playedTime ?: 0L
            if (lastPlayed <= 0L) "Unwatched" else "Played"
        }
        SortField.LENGTH -> {
            val minutes = duration / 60000
            when {
                minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
                minutes > 0 -> "${minutes}m"
                else -> "< 1m"
            }
        }
        SortField.SIZE -> {
            when {
                size >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
                size >= 1024L * 1024L -> "${size / (1024 * 1024)} MB"
                else -> "< 1 MB"
            }
        }
    }
}

