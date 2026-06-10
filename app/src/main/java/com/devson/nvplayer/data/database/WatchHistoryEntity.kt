package com.devson.nvplayer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val uri: String,
    val lastPositionMs: Long,
    val lastPlayedAt: Long,
    val isNetworkStream: Boolean = false,
    val videoTitle: String? = null
)
