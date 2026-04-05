package com.devson.nosvedplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VideoMetadataDao {
    @Query("SELECT * FROM video_metadata_cache WHERE uri = :uri")
    fun getMetadata(uri: String): CachedVideoMetadata?

    @Query("SELECT uri FROM video_metadata_cache")
    fun getAllUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(metadata: CachedVideoMetadata)
}
