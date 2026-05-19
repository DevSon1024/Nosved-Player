package com.devson.nvplayer.data.repository

import com.devson.nvplayer.data.media.MediaStoreHelper
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(private val mediaStoreHelper: MediaStoreHelper) {

    suspend fun getFolders(): List<FolderItem> = withContext(Dispatchers.IO) {
        mediaStoreHelper.getFolders()
    }

    suspend fun getVideosByFolder(folderName: String): List<VideoItem> = withContext(Dispatchers.IO) {
        mediaStoreHelper.getAllVideos().filter { it.folderName == folderName }
    }
}
