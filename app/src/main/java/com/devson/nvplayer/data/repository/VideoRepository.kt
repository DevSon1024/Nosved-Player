package com.devson.nvplayer.data.repository

import com.devson.nvplayer.data.media.MediaStoreHelper
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.model.VideoItem
import com.devson.nvplayer.domain.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(
    private val mediaStoreHelper: MediaStoreHelper,
    val context: android.content.Context
) {
    private val settingsRepo = PlaybackSettingsRepository(context)
    val videoMetadataDao = com.devson.nvplayer.data.database.AppDatabase.getDatabase(context).videoMetadataDao()

    suspend fun getAllVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val blacklisted = settingsRepo.playbackSettingsFlow.value.blacklistedFolders.toList()
        mediaStoreHelper.getAllVideos(blacklisted)
    }

    suspend fun getFolders(): List<FolderItem> = withContext(Dispatchers.IO) {
        val blacklisted = settingsRepo.playbackSettingsFlow.value.blacklistedFolders.toList()
        mediaStoreHelper.getFolders(blacklisted)
    }

    suspend fun getVideosByFolder(folderName: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val blacklisted = settingsRepo.playbackSettingsFlow.value.blacklistedFolders.toList()
        mediaStoreHelper.getVideosByFolder(folderName, blacklisted)
    }

    /**
     * Retrieves videos that are in the system trash and maps them to the app's Video model.
     */
    suspend fun getTrashedVideos(): List<Video> = withContext(Dispatchers.IO) {
        val blacklisted = settingsRepo.playbackSettingsFlow.value.blacklistedFolders.toList()
        val trashedItems = mediaStoreHelper.getTrashedVideos(blacklisted)
        val metadataDao = videoMetadataDao
        trashedItems.map { item ->
            val uriStr = item.uri.toString()
            val finalSize: Long
            val finalDateModified: Long
            val finalDuration: Long
            
            if (item.size > 0 && item.duration > 0) {
                finalSize = item.size
                finalDateModified = item.dateModified * 1000
                finalDuration = item.duration
            } else {
                val cached = metadataDao.getMetadataByUri(uriStr)
                if (cached != null) {
                    finalSize = cached.size
                    finalDateModified = cached.dateModified
                    finalDuration = cached.duration
                } else {
                    val extracted = com.devson.nvplayer.util.getVideoMetadata(context, item.uri)
                    finalSize = if (extracted.fileSize > 0) extracted.fileSize else item.size
                    finalDateModified = if (extracted.lastModified > 0) extracted.lastModified else item.dateModified * 1000
                    finalDuration = item.duration
                    
                    metadataDao.insertOrUpdate(
                        com.devson.nvplayer.data.database.CachedVideoMetadata(
                            uri = uriStr,
                            size = finalSize,
                            dateModified = finalDateModified,
                            duration = finalDuration
                        )
                    )
                }
            }

            Video(
                uri = uriStr,
                title = item.title,
                duration = finalDuration,
                folderName = item.folderName,
                path = item.path,
                size = finalSize,
                width = item.width,
                height = item.height,
                dateAdded = finalDateModified,
                dateModified = finalDateModified,
                playedTime = null,
                lastPlayedAt = null,
                resolution = "${item.width}x${item.height}",
                frameRate = 30.0f,
                thumbnailUri = item.thumbnailUri?.toString()
            )
        }
    }

    /**
     * Cancels all pending image loads when navigating away from a folder.
     * Coil automatically cancels requests tied to composables when they leave
     * composition, so no manual job management is needed.
     */
    suspend fun resetThumbnailJobs() {
        withContext(Dispatchers.IO) {
            // Cancel all pending Coil image requests for this context.
            // Coil's ImageLoader.shutdown() would be too aggressive (kills the cache),
            // so we simply let Coil cancel in-flight requests naturally as the
            // composables that own them leave composition.
        }
    }
}
