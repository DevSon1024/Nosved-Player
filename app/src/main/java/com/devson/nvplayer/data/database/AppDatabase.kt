package com.devson.nvplayer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import androidx.room.TypeConverters

@Database(
    entities = [
        WatchHistoryEntity::class,
        CachedVideoMetadata::class,
        SeriesEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        MovieEntity::class,
        VaultEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(VaultConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun videoMetadataDao(): VideoMetadataDao
    abstract fun mediaLibraryDao(): MediaLibraryDao
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create series table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `series` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `tmdbId` INTEGER,
                        `synopsis` TEXT,
                        `posterUri` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_title` ON `series` (`title`)")

                // Create seasons table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `seasons` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `seriesId` INTEGER NOT NULL,
                        `seasonNumber` INTEGER NOT NULL,
                        FOREIGN KEY(`seriesId`) REFERENCES `series`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_seasons_seriesId` ON `seasons` (`seriesId`)")

                // Create episodes table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `episodes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `seasonId` INTEGER NOT NULL,
                        `episodeNumber` INTEGER NOT NULL,
                        `title` TEXT,
                        `fileUri` TEXT NOT NULL,
                        `durationMillis` INTEGER NOT NULL DEFAULT 0,
                        `lastPlaybackPosition` INTEGER NOT NULL,
                        `isWatched` INTEGER NOT NULL,
                        `introStart` INTEGER,
                        `introEnd` INTEGER,
                        FOREIGN KEY(`seasonId`) REFERENCES `seasons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_seasonId` ON `episodes` (`seasonId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_fileUri` ON `episodes` (`fileUri`)")

                // Create movies table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `movies` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `year` INTEGER,
                        `fileUri` TEXT NOT NULL,
                        `durationMillis` INTEGER NOT NULL DEFAULT 0,
                        `lastPlaybackPosition` INTEGER NOT NULL,
                        `isWatched` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_movies_fileUri` ON `movies` (`fileUri`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create vault_media table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vault_media` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `originalUri` TEXT NOT NULL,
                        `vaultPath` TEXT NOT NULL,
                        `thumbnailPath` TEXT,
                        `fileSize` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `dateAdded` INTEGER NOT NULL,
                        `lastPlaybackPosition` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vault_media_vaultPath` ON `vault_media` (`vaultPath`)")
            }
        }

        private fun addColumnIfNotExists(db: SupportSQLiteDatabase, tableName: String, columnName: String, columnDef: String) {
            try {
                val cursor = db.query("PRAGMA table_info(`$tableName`)")
                var exists = false
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1 && cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                        exists = true
                        break
                    }
                }
                cursor.close()
                if (!exists) {
                    db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDef")
                }
            } catch (_: Exception) {
                // Ignore if duplicate or unable to alter
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "episodes", "durationMillis", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "movies", "durationMillis", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "vault_media", "storageMode", "TEXT NOT NULL DEFAULT 'ENCRYPTED'")
                addColumnIfNotExists(db, "vault_media", "formatVersion", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfNotExists(db, "vault_media", "originalExtension", "TEXT NOT NULL DEFAULT 'mp4'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
