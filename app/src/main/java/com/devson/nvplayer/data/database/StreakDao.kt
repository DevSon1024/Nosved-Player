package com.devson.nvplayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakDao {

    @Query("SELECT * FROM streak_state WHERE id = 1 LIMIT 1")
    suspend fun getStreak(): StreakStateEntity?

    @Query("SELECT * FROM streak_state WHERE id = 1 LIMIT 1")
    fun getStreakFlow(): Flow<StreakStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(streak: StreakStateEntity)

    @Query(
        """
        UPDATE streak_state 
        SET currentStreak = :currentStreak, 
            longestStreak = :longestStreak, 
            lastQualifyingWatchDate = :lastQualifyingDate, 
            lastUpdatedTimestamp = :timestamp 
        WHERE id = 1
        """
    )
    suspend fun updateStreak(
        currentStreak: Int,
        longestStreak: Int,
        lastQualifyingDate: String?,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE streak_state 
        SET currentStreak = 0, 
            lastUpdatedTimestamp = :timestamp 
        WHERE id = 1
        """
    )
    suspend fun resetCurrentStreak(timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM streak_state")
    suspend fun deleteAll()
}
