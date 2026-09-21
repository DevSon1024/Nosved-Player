package com.devson.nvplayer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streak_state")
data class StreakStateEntity(
    @PrimaryKey
    val id: Int = 1,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastQualifyingWatchDate: String? = null,
    val lastUpdatedTimestamp: Long = 0L
)
