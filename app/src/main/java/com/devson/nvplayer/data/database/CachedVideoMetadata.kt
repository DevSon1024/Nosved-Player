package com.devson.nvplayer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_video_metadata")
data class CachedVideoMetadata(
    @PrimaryKey val uri: String,
    val size: Long,
    val dateModified: Long,
    val duration: Long,
    val externalSubtitleUri: String? = null
)
