package com.devson.nosvedplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN duration INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN size INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN folderName TEXT NOT NULL DEFAULT 'Unknown'")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN path TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE video_metadata_cache ADD COLUMN encodingSW TEXT")
            }
        }

        fun getInstance(context: Context): NosvedDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NosvedDatabase::class.java,
                    "nosved_db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
