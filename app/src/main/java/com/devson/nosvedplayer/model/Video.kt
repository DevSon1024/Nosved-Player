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
    val folderName: String = "Unknown",
    val dateAdded: Long = 0L
)
