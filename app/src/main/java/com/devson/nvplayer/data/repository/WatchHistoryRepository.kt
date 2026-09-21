package com.devson.nvplayer.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.devson.nvplayer.data.database.AppDatabase
import com.devson.nvplayer.data.database.WatchHistoryDao
import com.devson.nvplayer.data.database.WatchHistoryEntity
import com.devson.nvplayer.data.model.VideoItem
import com.devson.nvplayer.domain.model.VideoHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class WatchHistoryRepository(
    private val context: Context? = null,
    private val watchHistoryDao: WatchHistoryDao
) {
    constructor(context: Context) : this(
        context = context,
        watchHistoryDao = AppDatabase.getDatabase(context).watchHistoryDao()
    )

    companion object {
        private const val TAG = "WatchHistoryRepository"
    }

    /**
     * Flow of all watch history items (both active and deleted), mapped to domain VideoHistoryItem.
     */
    fun getVideoHistoryFlow(): Flow<List<VideoHistoryItem>> {
        return watchHistoryDao.getAllHistoryFlow().map { entities ->
            entities.map { it.toVideoHistoryItem() }
        }
    }

    /**
     * Flow of only active (non-deleted) watch history items.
     */
    fun getActiveHistoryFlow(): Flow<List<VideoHistoryItem>> {
        return watchHistoryDao.getActiveHistoryFlow().map { entities ->
            entities.map { it.toVideoHistoryItem() }
        }
    }

    /**
     * Flow of deleted watch history items.
     */
    fun getDeletedHistoryFlow(): Flow<List<VideoHistoryItem>> {
        return watchHistoryDao.getDeletedHistoryFlow().map { entities ->
            entities.map { it.toVideoHistoryItem() }
        }
    }

    /**
     * Flow of watch history for a specific calendar date (YYYY-MM-DD).
     */
    fun getHistoryForDateFlow(watchDate: String): Flow<List<VideoHistoryItem>> {
        return watchHistoryDao.getHistoryForDateFlow(watchDate).map { entities ->
            entities.map { it.toVideoHistoryItem() }
        }
    }

    /**
     * Synchronously queries all history items mapped to VideoHistoryItem.
     */
    suspend fun getVideoHistorySync(): List<VideoHistoryItem> = withContext(Dispatchers.IO) {
        watchHistoryDao.getAllHistorySync().map { it.toVideoHistoryItem() }
    }

    private fun safeLogD(msg: String) {
        runCatching { Log.d(TAG, msg) }
    }

    private fun safeLogW(msg: String, tr: Throwable? = null) {
        runCatching { Log.w(TAG, msg, tr) }
    }

    private fun safeLogE(msg: String, tr: Throwable? = null) {
        runCatching { Log.e(TAG, msg, tr) }
    }

    /**
     * Safe mechanism to determine whether a historical video still physically exists.
     * Checks filesystem and ContentResolver without throwing or crashing.
     */
    suspend fun isHistoricalVideoAvailable(history: WatchHistoryEntity): Boolean = withContext(Dispatchers.IO) {
        if (history.isNetworkStream || history.uri.startsWith("http") || history.uri.startsWith("ytdl")) {
            return@withContext true
        }

        val path = history.originalPath ?: (if (history.uri.startsWith("file://")) history.uri.removePrefix("file://") else null)
        if (path != null && path.isNotBlank()) {
            try {
                if (File(path).exists()) {
                    return@withContext true
                }
            } catch (e: Exception) {
                safeLogW("Failed checking file existence for path: $path", e)
            }
        }

        if (history.uri.startsWith("content://")) {
            val ctx = context ?: return@withContext false
            return@withContext runCatching {
                ctx.contentResolver.openAssetFileDescriptor(Uri.parse(history.uri), "r")?.use { true } ?: false
            }.getOrDefault(false)
        }

        return@withContext false
    }

    /**
     * Synchronizes local watch history records with scanned MediaStore videos.
     * Detects disappeared files and marks them isDeleted = true without removing history.
     * Reconciles restored files back to isDeleted = false without duplicating records.
     *
     * @param scannedVideos The videos already discovered during the media scan, avoiding redundant storage scans.
     */
    suspend fun syncWatchHistoryFileStatus(scannedVideos: List<VideoItem>) = withContext(Dispatchers.IO) {
        val records = scannedVideos.map {
            ScannedVideoRecord(
                uri = it.uri.toString(),
                path = it.path,
                title = it.title,
                duration = it.duration,
                folderName = it.folderName
            )
        }
        syncWatchHistoryRecords(records)
    }

    /**
     * Core reconciliation method operating on decoupled scanned records.
     */
    suspend fun syncWatchHistoryRecords(scannedVideos: List<ScannedVideoRecord>) = withContext(Dispatchers.IO) {
        try {
            val activeUris = HashSet<String>(scannedVideos.size)
            val activePaths = HashMap<String, ScannedVideoRecord>(scannedVideos.size)

            for (video in scannedVideos) {
                activeUris.add(video.uri)
                if (video.path.isNotBlank()) {
                    activePaths[video.path] = video
                }
            }

            val localHistory = watchHistoryDao.getLocalHistorySync()

            for (history in localHistory) {
                val existsInMediaStoreUri = activeUris.contains(history.uri)
                val matchingByPath = history.originalPath?.let { activePaths[it] }

                when {
                    existsInMediaStoreUri -> {
                        if (history.isDeleted) {
                            watchHistoryDao.setDeletedStatus(history.uri, false)
                            safeLogD("Video restored at existing URI: ${history.uri}")
                        }
                        // Backfill originalPath if missing
                        if (history.originalPath.isNullOrBlank()) {
                            val scanned = scannedVideos.find { it.uri == history.uri }
                            if (scanned != null && scanned.path.isNotBlank()) {
                                watchHistoryDao.insert(history.copy(originalPath = scanned.path, isDeleted = false))
                            }
                        }
                    }

                    matchingByPath != null -> {
                        // File exists at the same original path, but MediaStore assigned a new URI
                        val newUri = matchingByPath.uri
                        if (newUri != history.uri) {
                            safeLogD("Migrating history URI from ${history.uri} to $newUri at path: ${history.originalPath}")
                            watchHistoryDao.deleteHistory(history.uri)
                            watchHistoryDao.insert(
                                history.copy(
                                    uri = newUri,
                                    isDeleted = false,
                                    durationMs = if (matchingByPath.duration > 0L) matchingByPath.duration else history.durationMs,
                                    videoTitle = history.videoTitle ?: matchingByPath.title,
                                    folderName = history.folderName ?: matchingByPath.folderName
                                )
                            )
                        } else if (history.isDeleted) {
                            watchHistoryDao.setDeletedStatus(history.uri, false)
                        }
                    }

                    else -> {
                        // Not found in MediaStore. Check direct physical file existence
                        val filePath = history.originalPath ?: (if (history.uri.startsWith("file://")) history.uri.removePrefix("file://") else null)
                        val physicalFileExists = filePath?.let {
                            try {
                                File(it).exists()
                            } catch (_: Exception) {
                                false
                            }
                        } ?: false

                        if (physicalFileExists) {
                            if (history.isDeleted) {
                                watchHistoryDao.setDeletedStatus(history.uri, false)
                            }
                        } else {
                            // File does not physically exist. Mark as deleted while preserving all history data.
                            if (!history.isDeleted) {
                                watchHistoryDao.setDeletedStatus(history.uri, true)
                                safeLogD("Marked history record as deleted for URI: ${history.uri}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            safeLogE("Error syncing watch history file status", e)
        }
    }

    /**
     * Explicitly marks a video as deleted.
     */
    suspend fun markAsDeleted(uri: String) = withContext(Dispatchers.IO) {
        watchHistoryDao.markAsDeleted(uri)
    }

    /**
     * Explicitly restores a video status.
     */
    suspend fun restoreHistory(uri: String) = withContext(Dispatchers.IO) {
        watchHistoryDao.restoreHistory(uri)
    }

    /**
     * Permanently removes a history record if explicitly requested by user.
     */
    suspend fun deleteHistoryPermanently(uri: String) = withContext(Dispatchers.IO) {
        watchHistoryDao.deleteHistory(uri)
    }
}

/**
 * Extension mapper converting WatchHistoryEntity to immutable domain VideoHistoryItem.
 */
fun WatchHistoryEntity.toVideoHistoryItem(): VideoHistoryItem {
    val resolvedName = videoTitle?.takeIf { it.isNotBlank() }
        ?: originalPath?.let { File(it).name.takeIf { n -> n.isNotBlank() } }
        ?: run {
            val seg = uri.substringAfterLast('/').substringBefore('?')
            seg.takeIf { it.isNotBlank() } ?: "Video"
        }

    return VideoHistoryItem(
        historyId = historyId.ifBlank { uri },
        originalName = resolvedName,
        watchedDate = watchDate,
        lastPlayedAt = lastPlayedAt,
        durationMs = durationMs,
        progress = playbackProgress,
        lastPositionMs = lastPositionMs,
        isDeleted = isDeleted,
        isThumbnailAvailable = !isDeleted,
        uri = if (!isDeleted) uri else null,
        path = if (!isDeleted) originalPath else null,
        folderName = folderName,
        isCompleted = isCompleted,
        totalPlaybackTimeMs = totalPlaybackTimeMs,
        isNetworkStream = isNetworkStream,
        rawUri = uri,
        rawPath = originalPath
    )
}

/**
 * Lightweight decoupled model representing a discovered media file during scanning.
 */
data class ScannedVideoRecord(
    val uri: String,
    val path: String,
    val title: String,
    val duration: Long = 0L,
    val folderName: String = ""
)
