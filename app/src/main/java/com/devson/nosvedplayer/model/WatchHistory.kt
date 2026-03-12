package com.devson.nosvedplayer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a video that has been watched, stored in Room for history/resume.
 */
@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey
    val uri: String,
    val title: String,
    val duration: Long = 0L,
    val size: Long = 0L,
    val folderName: String = "Unknown",
    /** Last resume position in milliseconds. */
    val lastPositionMs: Long = 0L,
    /** Epoch millis when the video was last played (for ordering). */
    val lastPlayedAt: Long = System.currentTimeMillis()
)
