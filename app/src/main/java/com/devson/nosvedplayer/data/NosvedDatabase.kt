package com.devson.nosvedplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.devson.nosvedplayer.model.WatchHistory

@Database(
    entities = [WatchHistory::class],
    version = 1,
    exportSchema = false
)
abstract class NosvedDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: NosvedDatabase? = null

        fun getInstance(context: Context): NosvedDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NosvedDatabase::class.java,
                    "nosved_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
