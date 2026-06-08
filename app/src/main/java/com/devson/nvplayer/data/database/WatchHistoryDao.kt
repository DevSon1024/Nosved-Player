package com.devson.nvplayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY lastPlayedAt DESC")
    fun getAllHistoryFlow(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history")
    suspend fun getAllHistorySync(): List<WatchHistoryEntity>

    @Query("DELETE FROM watch_history WHERE uri = :uri")
    suspend fun deleteHistory(uri: String)

    @Query("SELECT * FROM watch_history WHERE uri = :uri")
    suspend fun getHistory(uri: String): WatchHistoryEntity?

    @Query("DELETE FROM watch_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM watch_history WHERE isNetworkStream = 1 ORDER BY lastPlayedAt DESC")
    fun getNetworkStreams(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE uri = :uri LIMIT 1")
    suspend fun getStreamByUri(uri: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStream(stream: WatchHistoryEntity)

    suspend fun insertOrUpdateStream(uri: String, title: String?) {
        val existing = getStreamByUri(uri)
        val resolvedTitle = if (title.isNullOrBlank()) {
            val parsed = android.net.Uri.parse(uri)
            val seg = parsed.lastPathSegment
            if (!seg.isNullOrBlank()) {
                seg
            } else {
                uri
            }
        } else {
            title
        }
        val entity = WatchHistoryEntity(
            uri = uri,
            lastPositionMs = existing?.lastPositionMs ?: 0L,
            lastPlayedAt = System.currentTimeMillis(),
            isNetworkStream = true,
            videoTitle = resolvedTitle
        )
        insertStream(entity)
    }
}
