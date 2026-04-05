package com.devson.nosvedplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.devson.nosvedplayer.model.WatchHistory

import com.devson.nosvedplayer.data.CachedVideoMetadata
import com.devson.nosvedplayer.data.VideoMetadataDao

@Database(
    entities = [WatchHistory::class, CachedVideoMetadata::class],
    version = 2,
    exportSchema = false
)
abstract class NosvedDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun videoMetadataDao(): VideoMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: NosvedDatabase? = null

        fun getInstance(context: Context): NosvedDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NosvedDatabase::class.java,
                    "nosved_db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
