package com.devson.nvplayer.data.repository

import com.devson.nvplayer.data.media.MediaStoreHelper
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.model.VideoItem
import com.devson.nvplayer.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(private val mediaStoreHelper: MediaStoreHelper) {

    suspend fun getFolders(): List<FolderItem> = withContext(Dispatchers.IO) {
        mediaStoreHelper.getFolders()
    }

    suspend fun getVideosByFolder(folderName: String): List<VideoItem> = withContext(Dispatchers.IO) {
        mediaStoreHelper.getAllVideos().filter { it.folderName == folderName }
    }

    /**
     * Retrieves videos that are in the system trash and maps them to the app's Video model.
     */
    suspend fun getTrashedVideos(): List<com.devson.nvplayer.model.Video> = withContext(Dispatchers.IO) {
        val trashedItems = mediaStoreHelper.getTrashedVideos()
        trashedItems.map { item ->
            com.devson.nvplayer.model.Video(
                uri = item.uri.toString(),
                title = item.title,
                duration = item.duration,
                folderName = item.folderName,
                path = item.path,
                size = item.size,
                width = item.width,
                height = item.height
            )
        }
    }
}
