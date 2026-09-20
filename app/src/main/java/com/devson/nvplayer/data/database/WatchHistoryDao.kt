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

    @Query("SELECT * FROM watch_history WHERE isDeleted = 0 ORDER BY lastPlayedAt DESC")
    fun getActiveHistoryFlow(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE isDeleted = 0 ORDER BY lastPlayedAt DESC")
    suspend fun getActiveHistorySync(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE isDeleted = 1 ORDER BY lastPlayedAt DESC")
    fun getDeletedHistoryFlow(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE isDeleted = 1 ORDER BY lastPlayedAt DESC")
    suspend fun getDeletedHistorySync(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE historyId = :historyId LIMIT 1")
    suspend fun getByHistoryId(historyId: String): WatchHistoryEntity?

    @Query("UPDATE watch_history SET isDeleted = :isDeleted WHERE uri = :uri")
    suspend fun setDeletedStatus(uri: String, isDeleted: Boolean)

    @Query("UPDATE watch_history SET isDeleted = 1 WHERE uri = :uri")
    suspend fun markAsDeleted(uri: String)

    @Query("UPDATE watch_history SET isDeleted = 0 WHERE uri = :uri")
    suspend fun restoreHistory(uri: String)

    @Query("SELECT * FROM watch_history WHERE watchDate = :watchDate ORDER BY lastPlayedAt DESC")
    suspend fun getHistoryForDate(watchDate: String): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE watchDate = :watchDate ORDER BY lastPlayedAt DESC")
    fun getHistoryForDateFlow(watchDate: String): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE watchDate BETWEEN :startDate AND :endDate ORDER BY watchDate ASC, lastPlayedAt DESC")
    suspend fun getHistoryBetweenDates(startDate: String, endDate: String): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE isCompleted = 1 ORDER BY lastPlayedAt DESC")
    fun getCompletedVideosFlow(): Flow<List<WatchHistoryEntity>>

    @Query(
        """
        UPDATE watch_history 
        SET lastPositionMs = :positionMs, 
            playbackProgress = :progress, 
            isCompleted = :isCompleted, 
            totalPlaybackTimeMs = :totalPlaybackTimeMs, 
            lastPlayedAt = :lastPlayedAt, 
            watchDate = :watchDate 
        WHERE uri = :uri
        """
    )
    suspend fun updatePlaybackProgress(
        uri: String,
        positionMs: Long,
        progress: Float,
        isCompleted: Boolean,
        totalPlaybackTimeMs: Long,
        lastPlayedAt: Long,
        watchDate: String
    )
}
