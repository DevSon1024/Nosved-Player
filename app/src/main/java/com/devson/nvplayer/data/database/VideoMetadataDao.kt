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

    @Query("SELECT * FROM cached_video_metadata")
    suspend fun getAllMetadataSync(): List<CachedVideoMetadata>

    @Query("UPDATE cached_video_metadata SET externalSubtitleUri = :subUri WHERE uri = :videoUri")
    suspend fun updateExternalSubtitle(videoUri: String, subUri: String?): Int

    suspend fun saveExternalSubtitle(videoUri: String, subUri: String?) {
        val updated = updateExternalSubtitle(videoUri, subUri)
        if (updated == 0) {
            insertOrUpdate(
                CachedVideoMetadata(
                    uri = videoUri,
                    size = 0L,
                    dateModified = 0L,
                    duration = 0L,
                    externalSubtitleUri = subUri
                )
            )
        }
    }
}
