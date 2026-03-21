package com.devson.nosvedplayer.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.devson.nosvedplayer.model.WatchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    /** Insert or update a history entry (keyed by URI). */
    @Upsert
    suspend fun upsert(entry: WatchHistory)

    /** Returns all history entries sorted by most recently played, limited to 50. */
    @Query("SELECT * FROM watch_history ORDER BY lastPlayedAt DESC LIMIT 50")
    fun observeHistory(): Flow<List<WatchHistory>>

    /** Update only the last position and timestamp for an existing entry. */
    @Query("UPDATE watch_history SET lastPositionMs = :positionMs, lastPlayedAt = :playedAt WHERE uri = :uri")
    suspend fun updatePosition(uri: String, positionMs: Long, playedAt: Long)

    /** Delete a single history entry. */
    @Query("DELETE FROM watch_history WHERE uri = :uri")
    suspend fun delete(uri: String)

    /** Clear all history. */
    @Query("DELETE FROM watch_history")
    suspend fun clearAll()

    /** Fetch history entry for a specific URI. */
    @Query("SELECT * FROM watch_history WHERE uri = :uri")
    suspend fun getHistoryByUri(uri: String): WatchHistory?
}
