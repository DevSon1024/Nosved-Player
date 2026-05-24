package com.devson.nvplayer.data.repository

import com.devson.nvplayer.data.media.MediaStoreHelper
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.model.VideoItem
import com.devson.nvplayer.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(
    private val mediaStoreHelper: MediaStoreHelper,
    private val context: android.content.Context
) {
    private val settingsRepo = com.devson.nvplayer.repository.PlaybackSettingsRepository(context)

    suspend fun getAllVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val blacklisted = settingsRepo.playbackSettingsFlow.value.blacklistedFolders
        mediaStoreHelper.getAllVideos().filterNot { item ->
            blacklisted.any { blacklistedPath -> item.path.startsWith(blacklistedPath) }
        }
    }

    suspend fun getFolders(): List<FolderItem> = withContext(Dispatchers.IO) {
        val blacklisted = settingsRepo.playbackSettingsFlow.value.blacklistedFolders
        val filteredVideos = mediaStoreHelper.getAllVideos().filterNot { item ->
            blacklisted.any { blacklistedPath -> item.path.startsWith(blacklistedPath) }
        }
        val foldersMap = mutableMapOf<String, MutableList<VideoItem>>()
        filteredVideos.forEach { video ->
            if (!foldersMap.containsKey(video.folderName)) {
                foldersMap[video.folderName] = mutableListOf()
            }
            foldersMap[video.folderName]?.add(video)
        }
        foldersMap.map { (folderName, videos) ->
            FolderItem(
                name = folderName,
                videoCount = videos.size,
                thumbnailUri = videos.firstOrNull()?.thumbnailUri
            )
        }.sortedBy { it.name }
    }

    suspend fun getVideosByFolder(folderName: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val blacklisted = settingsRepo.playbackSettingsFlow.value.blacklistedFolders
        mediaStoreHelper.getAllVideos()
            .filter { it.folderName == folderName }
            .filterNot { item ->
                blacklisted.any { blacklistedPath -> item.path.startsWith(blacklistedPath) }
            }
    }

    /**
     * Retrieves videos that are in the system trash and maps them to the app's Video model.
     */
    suspend fun getTrashedVideos(): List<com.devson.nvplayer.model.Video> = withContext(Dispatchers.IO) {
        val trashedItems = mediaStoreHelper.getTrashedVideos()
        val blacklisted = settingsRepo.playbackSettingsFlow.value.blacklistedFolders
        trashedItems
            .filterNot { item ->
                blacklisted.any { blacklistedPath -> item.path.startsWith(blacklistedPath) }
            }
            .map { item ->
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
}
