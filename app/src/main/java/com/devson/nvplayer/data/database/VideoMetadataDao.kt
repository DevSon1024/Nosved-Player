package com.devson.nvplayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VideoMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: CachedVideoMetadata)

    @Query("SELECT * FROM cached_video_metadata WHERE uri = :uri LIMIT 1")
    suspend fun getMetadataByUri(uri: String): CachedVideoMetadata?
}
