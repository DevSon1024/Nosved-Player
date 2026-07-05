package com.devson.nvplayer.data.repository

import com.devson.nvplayer.data.media.MediaStoreHelper
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.model.VideoItem
import com.devson.nvplayer.domain.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class VideoRepository(
    private val mediaStoreHelper: MediaStoreHelper,
    val context: android.content.Context
) {
    private val settingsRepo = PlaybackSettingsRepository(context)
    val videoMetadataDao = com.devson.nvplayer.data.database.AppDatabase.getDatabase(context).videoMetadataDao()

    suspend fun scanCommonDirectories() = withContext(Dispatchers.IO) {
        val pathsToScan = mutableListOf<String>()
        val commonDirs = arrayOf(
            android.os.Environment.DIRECTORY_DOWNLOADS,
            android.os.Environment.DIRECTORY_MOVIES,
            android.os.Environment.DIRECTORY_DCIM
        )
        val internalRoot = android.os.Environment.getExternalStorageDirectory()
        for (dir in commonDirs) {
            val file = java.io.File(internalRoot, dir)
            if (file.exists() && file.isDirectory) {
                file.walkTopDown().maxDepth(3).forEach { f ->
                    if (f.isFile && isVideoFile(f.name)) {
                        pathsToScan.add(f.absolutePath)
                    }
                }
            }
        }
        if (pathsToScan.isNotEmpty()) {
            kotlinx.coroutines.withTimeoutOrNull(3000L) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                    var completedCount = 0
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        pathsToScan.toTypedArray(),
                        null
                    ) { _, _ ->
                        completedCount++
                        if (completedCount >= pathsToScan.size && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
            }
        }
    }

    private fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp4", "mkv", "webm", "avi", "3gp", "ts", "mov", "flv", "wmv", "m4v")
    }
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
