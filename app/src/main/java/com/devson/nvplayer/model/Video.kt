package com.devson.nvplayer.model

enum class LayoutMode {
    LIST, GRID
}

enum class ViewMode {
    ALL_FOLDERS, FILES, FOLDERS
}

enum class SortField {
    TITLE, DATE, PLAYED_TIME, STATUS, LENGTH, SIZE, RESOLUTION, PATH, FRAME_RATE, TYPE
}

enum class SortDirection {
    ASCENDING, DESCENDING
}

data class WatchHistory(
    val uri: String,
    val lastPositionMs: Long
)

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
    val playedTime: Long? = null,
    val lastPlayedAt: Long? = null,
    val resolution: String? = null,
    val frameRate: Float? = null
)

fun List<Video>.applySort(field: SortField, direction: SortDirection): List<Video> {
    val sorted = when (field) {
        SortField.TITLE -> sortedBy { it.title.lowercase() }
        SortField.DATE -> sortedBy { it.dateAdded }
        SortField.PLAYED_TIME -> sortedBy { it.playedTime ?: 0L }
        SortField.STATUS -> sortedBy { it.playedTime ?: 0L }
        SortField.LENGTH -> sortedBy { it.duration }
        SortField.SIZE -> sortedBy { it.size }
        SortField.RESOLUTION -> sortedBy { it.resolution ?: "" }
        SortField.PATH -> sortedBy { it.path.lowercase() }
        SortField.FRAME_RATE -> sortedBy { it.frameRate ?: 0f }
        SortField.TYPE -> sortedBy { it.title.substringAfterLast(".", "").lowercase() }
    }
    return if (direction == SortDirection.DESCENDING) sorted.reversed() else sorted
}
