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
}
