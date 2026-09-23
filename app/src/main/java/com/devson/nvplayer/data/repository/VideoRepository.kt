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
    val watchHistoryRepository: WatchHistoryRepository by lazy {
        WatchHistoryRepository(context)
    }

    @Volatile
    private var hasSyncedWatchHistoryOnce = false

    suspend fun scanCommonDirectories() = withContext(Dispatchers.IO) {
        val pathsToScan = mutableListOf<String>()
        val commonDirs = arrayOf(
            android.os.Environment.DIRECTORY_DOWNLOADS,
            android.os.Environment.DIRECTORY_MOVIES,
            android.os.Environment.DIRECTORY_DCIM
        )
        val internalRoot = android.os.Environment.getExternalStorageDirectory()
        val recentCutoff = System.currentTimeMillis() - 10 * 60 * 1000L
        for (dir in commonDirs) {
            val file = java.io.File(internalRoot, dir)
            if (file.exists() && file.isDirectory) {
                file.walkTopDown().maxDepth(3).forEach { f ->
                    if (pathsToScan.size < 50 && f.isFile && f.lastModified() > recentCutoff && isVideoFile(f)) {
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

    private fun isVideoFile(file: java.io.File): Boolean {
        val ext = file.extension.lowercase()
        val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "3gp", "mov", "flv", "wmv", "m4v")
        if (ext in videoExtensions) return true
        if (ext == "ts") {
            // Distinguish MPEG Transport Stream video from TypeScript code file
            if (!file.exists() || !file.canRead() || file.length() < 188) return false
            return runCatching {
                file.inputStream().use { stream ->
                    val buffer = ByteArray(564)
                    val read = stream.read(buffer)
                    if (read >= 188) {
                        buffer[0] == 0x47.toByte() || (read >= 376 && buffer[188] == 0x47.toByte())
                    } else false
                }
            }.getOrDefault(false)
        }
        return false
    }

    suspend fun getAllVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val blacklisted = settingsRepo.playbackSettingsFlow.value.blacklistedFolders.toList()
        val videos = mediaStoreHelper.getAllVideos(blacklisted)
        if (!hasSyncedWatchHistoryOnce) {
            hasSyncedWatchHistoryOnce = true
            watchHistoryRepository.syncWatchHistoryFileStatus(videos)
        }
        videos
    }

    suspend fun syncWatchHistory(scannedVideos: List<VideoItem>? = null) = withContext(Dispatchers.IO) {
        val videos = scannedVideos ?: getAllVideos()
        watchHistoryRepository.syncWatchHistoryFileStatus(videos)
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
            
            val cached = metadataDao.getMetadataByUri(uriStr)
            if (item.size > 0 && item.duration > 0) {
                finalSize = item.size
                finalDateModified = item.dateModified * 1000
                finalDuration = item.duration
            } else if (cached != null) {
                finalSize = cached.size
                finalDateModified = cached.dateModified
                finalDuration = cached.duration
            } else {
                val extracted = kotlinx.coroutines.withTimeoutOrNull(1000L) {
                    com.devson.nvplayer.util.getVideoMetadata(context, item.uri)
                } ?: com.devson.nvplayer.util.VideoMetadata(0L, 0L)
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

            val embeddedSubs = cached?.embeddedSubtitles?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val externalSubs = cached?.externalSubtitles?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

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
                frameRate = if (cached?.frameRate != null && cached.frameRate > 0f) cached.frameRate else null,
                thumbnailUri = item.thumbnailUri?.toString(),
                embeddedSubtitles = embeddedSubs,
                externalSubtitles = externalSubs
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
