package com.devson.nvplayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyWatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dailyWatch: DailyWatchEntity)

    @Query("SELECT * FROM daily_watch_records WHERE date = :date LIMIT 1")
    suspend fun getDailyWatch(date: String): DailyWatchEntity?

    @Query("SELECT * FROM daily_watch_records WHERE date = :date LIMIT 1")
    fun getDailyWatchFlow(date: String): Flow<DailyWatchEntity?>

    @Query("SELECT * FROM daily_watch_records ORDER BY date DESC")
    fun getAllDailyWatchesFlow(): Flow<List<DailyWatchEntity>>

    @Query("SELECT * FROM daily_watch_records ORDER BY date DESC")
    suspend fun getAllDailyWatchesSync(): List<DailyWatchEntity>

    @Query("SELECT * FROM daily_watch_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getDailyWatchesInRange(startDate: String, endDate: String): List<DailyWatchEntity>

    @Query("SELECT * FROM daily_watch_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getDailyWatchesInRangeFlow(startDate: String, endDate: String): Flow<List<DailyWatchEntity>>

    @Query("DELETE FROM daily_watch_records WHERE date = :date")
    suspend fun deleteDailyWatch(date: String)

    @Query("DELETE FROM daily_watch_records")
    suspend fun deleteAll()

    @Transaction
    suspend fun recordQualifyingWatch(date: String, videoUri: String): DailyWatchEntity {
        val existing = getDailyWatch(date)
        if (existing == null) {
            val newEntry = DailyWatchEntity(
                date = date,
                qualifyingVideoCount = 1,
                qualifyingVideoUris = videoUri
            )
            insert(newEntry)
            return newEntry
        }
        val currentUris = existing.getWatchedUrisSet()
        if (currentUris.contains(videoUri)) {
            return existing
        }
        val updatedEntry = existing.withWatchedVideo(videoUri)
        insert(updatedEntry)
        return updatedEntry
    }
}
