package com.devson.nosvedplayer.model

/**
 * Represents a video media item to be played.
 *
 * @param uri The URI of the video (can be local or remote).
 * @param title The title of the video.
 * @param duration The duration of the video in milliseconds.
 * @param size The size of the video in bytes.
 * @param folderName The folder/album name where the video resides (for local videos).
 */
data class Video(
    val uri: String,
    val title: String,
    val duration: Long = 0L,
    val size: Long = 0L,
    val folderId: String = "",
    val folderName: String = "Unknown",
    val dateAdded: Long = 0L,
    val path: String = "",
    val frameRate: Float? = null,
    val resolution: String? = null,
    /** Last resume position in milliseconds (position inside the video). */
    val playedTime: Long? = null,
    /** Wall-clock epoch millis of when this video was last played (for "Played X Ago"). */
    val lastPlayedAt: Long? = null,
    val status: String? = null
)

fun List<Video>.applySort(field: SortField, direction: SortDirection): List<Video> {
    return if (direction == SortDirection.ASCENDING) {
        when (field) {
            SortField.TITLE -> sortedBy { it.title.lowercase() }
            SortField.DATE -> sortedBy { it.dateAdded }
            SortField.PLAYED_TIME -> sortedBy { it.playedTime ?: 0L }
            SortField.STATUS -> sortedBy { it.status.orEmpty().lowercase() }
            SortField.LENGTH -> sortedBy { it.duration }
            SortField.SIZE -> sortedBy { it.size }
            SortField.RESOLUTION -> sortedBy { it.resolution.orEmpty() }
            SortField.PATH -> sortedBy { it.path.lowercase() }
            SortField.FRAME_RATE -> sortedBy { it.frameRate ?: 0f }
            SortField.TYPE -> sortedBy { it.uri.substringAfterLast('.', "").lowercase() }
        }
    } else {
        when (field) {
            SortField.TITLE -> sortedByDescending { it.title.lowercase() }
            SortField.DATE -> sortedByDescending { it.dateAdded }
            SortField.PLAYED_TIME -> sortedByDescending { it.playedTime ?: 0L }
            SortField.STATUS -> sortedByDescending { it.status.orEmpty().lowercase() }
            SortField.LENGTH -> sortedByDescending { it.duration }
            SortField.SIZE -> sortedByDescending { it.size }
            SortField.RESOLUTION -> sortedByDescending { it.resolution.orEmpty() }
            SortField.PATH -> sortedByDescending { it.path.lowercase() }
            SortField.FRAME_RATE -> sortedByDescending { it.frameRate ?: 0f }
            SortField.TYPE -> sortedByDescending { it.uri.substringAfterLast('.', "").lowercase() }
        }
    }
}
