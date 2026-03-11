package com.devson.nosvedplayer.model

/**
 * Represents a video media item to be played.
 *
 * @param uri The URI of the video (can be local or remote).
 * @param title The title of the video.
 */
data class Video(
    val uri: String,
    val title: String
)
