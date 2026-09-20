package com.devson.nvplayer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_watch_records")
data class DailyWatchEntity(
    @PrimaryKey
    val date: String,
    val qualifyingVideoCount: Int = 0,
    val qualifyingVideoUris: String = ""
) {
    fun getWatchedUrisSet(): Set<String> {
        if (qualifyingVideoUris.isBlank()) return emptySet()
        return qualifyingVideoUris.split("\n").filter { it.isNotBlank() }.toSet()
    }

    fun withWatchedVideo(uri: String): DailyWatchEntity {
        val currentSet = getWatchedUrisSet()
        if (currentSet.contains(uri)) {
            return this
        }
        val newSet = currentSet + uri
        return copy(
            qualifyingVideoCount = newSet.size,
            qualifyingVideoUris = newSet.joinToString("\n")
        )
    }
}
