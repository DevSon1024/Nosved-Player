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
    val dateAdded: Long = 0L
)

fun List<Video>.applySort(order: SortOrder): List<Video> {
    return when (order) {
        SortOrder.A_TO_Z -> sortedBy { it.title.lowercase() }
        SortOrder.Z_TO_A -> sortedByDescending { it.title.lowercase() }
        SortOrder.NEWEST_FIRST -> sortedByDescending { it.dateAdded }
        SortOrder.OLDEST_FIRST -> sortedBy { it.dateAdded }
        SortOrder.LARGEST_FIRST -> sortedByDescending { it.size }
        SortOrder.SMALLEST_FIRST -> sortedBy { it.size }
    }
}
