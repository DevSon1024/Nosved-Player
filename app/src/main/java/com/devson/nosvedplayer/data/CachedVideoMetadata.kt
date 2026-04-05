package com.devson.nosvedplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_metadata_cache")
data class CachedVideoMetadata(
    @PrimaryKey val uri: String,
    val format: String,
    val resolution: String,
    val encodingSW: String?,
    val tracksJson: String
)
