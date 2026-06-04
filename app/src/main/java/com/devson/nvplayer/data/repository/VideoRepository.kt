package com.devson.nvplayer.data.repository

import com.devson.nvplayer.data.media.MediaStoreHelper
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.model.VideoItem
import com.devson.nvplayer.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import coil3.imageLoader
import com.devson.nvplayer.util.thumbnail.ThumbnailLoader

class VideoRepository(
    private val mediaStoreHelper: MediaStoreHelper,
    private val context: android.content.Context
) {
    private val settingsRepo = com.devson.nvplayer.repository.PlaybackSettingsRepository(context)

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
        mediaStoreHelper.getAllVideos(blacklisted)
            .filter { it.folderName == folderName }
    }

    /**
     * Retrieves videos that are in the system trash and maps them to the app's Video model.
     */
    suspend fun getTrashedVideos(): List<com.devson.nvplayer.model.Video> = withContext(Dispatchers.IO) {
        val blacklisted = settingsRepo.playbackSettingsFlow.value.blacklistedFolders.toList()
        val trashedItems = mediaStoreHelper.getTrashedVideos(blacklisted)
        trashedItems.map { item ->
            com.devson.nvplayer.model.Video(
                uri = item.uri.toString(),
                title = item.title,
                duration = item.duration,
                folderName = item.folderName,
                path = item.path,
                size = item.size,
                width = item.width,
                height = item.height,
                thumbnailUri = item.thumbnailUri?.toString()
            )
        }
    }

    suspend fun resetThumbnailJobs() {
        withContext(Dispatchers.IO) {
            ThumbnailLoader.getRepository(context).clearActiveJobs()
        }
    }
}
