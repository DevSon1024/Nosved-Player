package com.devson.nvplayer.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["watchDate"]),
        Index(value = ["lastPlayedAt"]),
        Index(value = ["isDeleted"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey val uri: String,
    val lastPositionMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val isNetworkStream: Boolean = false,
    val videoTitle: String? = null,
    val historyId: String = uri,
    val originalPath: String? = null,
    val folderName: String? = null,
    val durationMs: Long = 0L,
    val totalPlaybackTimeMs: Long = 0L,
    val firstWatchedAt: Long = lastPlayedAt,
    val watchDate: String = "",
    val isDeleted: Boolean = false,
    val isCompleted: Boolean = false,
    val playbackProgress: Float = 0f
)
