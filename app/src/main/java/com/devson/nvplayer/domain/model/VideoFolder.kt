package com.devson.nvplayer.domain.model

import androidx.compose.runtime.Immutable

/**
 * Represents a distinct folder mapped via MediaStore.
 */
@Immutable
data class VideoFolder(
    val id: String,
    val name: String,
    // val videoCount: Int
)

fun isPathHidden(path: String): Boolean {
    if (path.isEmpty()) return false
    val segments = path.split('/', '\\')
    return segments.any { it.startsWith(".") && it.length > 1 }
}

val VideoFolder.isHidden: Boolean
    get() = name.startsWith(".")

val VideoFolder.isPathHidden: Boolean
    get() = name.startsWith(".") || isPathHidden(id)

fun VideoFolder.getSectionLabel(
    sortField: SortField = SortField.TITLE,
    videos: List<Video> = emptyList(),
    historyMap: Map<String, WatchHistory> = emptyMap()
): String {
    return when (sortField) {
        SortField.TITLE -> {
            val firstChar = name.trim().firstOrNull()?.uppercaseChar()
            if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
        }
        SortField.DATE -> {
            val latest = videos.maxOfOrNull {
                if (it.dateAdded.toString().length < 13) it.dateAdded * 1000L else it.dateAdded
            } ?: 0L
            if (latest <= 0L) "Unknown" else {
                try {
                    java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(latest))
                } catch (_: Exception) {
                    "Date"
                }
            }
        }
        SortField.PLAYED_TIME -> {
            val latestPlayed = videos.maxOfOrNull {
                historyMap[it.uri]?.lastPlayedAt ?: it.lastPlayedAt ?: it.playedTime ?: 0L
            } ?: 0L
            if (latestPlayed <= 0L) "Unwatched" else "Played"
        }
        SortField.LENGTH -> {
            val totalDuration = videos.sumOf { it.duration }
            val minutes = totalDuration / 60000
            when {
                minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
                minutes > 0 -> "${minutes}m"
                else -> "< 1m"
            }
        }
        SortField.SIZE -> {
            val totalSize = videos.sumOf { it.size }
            when {
                totalSize >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f GB", totalSize / (1024.0 * 1024.0 * 1024.0))
                totalSize >= 1024L * 1024L -> "${totalSize / (1024 * 1024)} MB"
                else -> "< 1 MB"
            }
        }
    }
}