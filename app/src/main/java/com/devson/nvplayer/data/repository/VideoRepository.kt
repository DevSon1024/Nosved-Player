package com.devson.nvplayer.data.repository

import com.devson.nvplayer.data.media.MediaStoreHelper
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.model.VideoItem
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.util.getAvailableStorageVolumes
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val isScanningHidden = java.util.concurrent.atomic.AtomicBoolean(false)

    private val scanMutex = Mutex()
    private var inFlightScan: Deferred<List<VideoItem>>? = null
    private val _cachedVideos = MutableStateFlow<List<VideoItem>?>(null)
    val cachedVideos: StateFlow<List<VideoItem>?> = _cachedVideos.asStateFlow()

    @Volatile
    private var cachedUris: Set<String>? = null
    @Volatile
    private var cachedFolders: List<FolderItem>? = null

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun invalidateCache() {
        _cachedVideos.value = null
        cachedUris = null
        cachedFolders = null
    }

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

    private fun isVideoFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        val videoExtensions = setOf(
            "mp4", "mkv", "webm", "avi", "3gp", "mov", "flv", "wmv", "m4v",
            "ts", "vob", "ogv", "mpg", "mpeg", "m2ts", "rmvb"
        )
        if (ext in videoExtensions) {
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
            return true
        }
        return false
    }

    /**
     * Recursively scans an external/removable storage directory (e.g. USB OTG drive or SD card)
     * for supported video files.
     */
    fun scanVolumeForVideos(
        rootPath: String,
        blacklistedFolders: List<String> = emptyList(),
        includeHidden: Boolean = false
    ): List<VideoItem> {
        val rootDir = File(rootPath)
        if (!rootDir.exists() || !rootDir.canRead() || !rootDir.isDirectory) {
            return emptyList()
        }

        val blacklistedSet = blacklistedFolders.map {
            if (it.endsWith("/")) it else "$it/"
        }.toSet()

        val results = mutableListOf<VideoItem>()
        val ignoredDirNames = if (includeHidden) {
            setOf(
                "Android",
                "LOST.DIR",
                "System Volume Information",
                "\$RECYCLE.BIN",
                ".trashed",
                ".Trash",
                ".git",
                ".gradle",
                ".idea",
                ".vscode",
                ".cache",
                ".thumbnails",
                ".npm",
                "node_modules",
                ".dart_tool",
                "build"
            )
        } else {
            setOf(
                "Android",
                "LOST.DIR",
                "System Volume Information",
                "\$RECYCLE.BIN",
                ".trashed",
                ".thumbnails",
                ".Trash",
                ".git",
                ".gradle",
                ".idea",
                ".vscode",
                ".cache",
                ".thumbnails",
                ".npm",
                "node_modules",
                ".dart_tool",
                "build",
                ".nomedia"
            )
        }

        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(rootDir to 0)
        val maxDepth = 12

        while (queue.isNotEmpty()) {
            val (currentDir, depth) = queue.removeFirst()
            if (depth > maxDepth) continue

            if (!includeHidden) {
                val nomedia = File(currentDir, ".nomedia")
                if (nomedia.exists()) continue
            }

            val children = try {
                currentDir.listFiles()
            } catch (_: Exception) {
                null
            } ?: continue

            for (child in children) {
                val name = child.name
                if (!includeHidden && name.startsWith(".")) continue

                if (child.isDirectory) {
                    if (name !in ignoredDirNames) {
                        val pathWithSlash = if (child.absolutePath.endsWith("/")) child.absolutePath else "${child.absolutePath}/"
                        val isBlacklisted = blacklistedSet.any { pathWithSlash.startsWith(it) }
                        if (!isBlacklisted) {
                            queue.add(child to (depth + 1))
                        }
                    }
                } else if (child.isFile && child.length() > 0L) {
                    if (isVideoFile(child)) {
                        val absPath = child.absolutePath
                        val isBlacklisted = blacklistedSet.any { absPath.startsWith(it) }
                        if (!isBlacklisted) {
                            val uri = Uri.fromFile(child)
                            val parentName = child.parentFile?.name?.ifEmpty { rootDir.name } ?: rootDir.name
                            results.add(
                                VideoItem(
                                    uri = uri,
                                    title = child.name,
                                    duration = 0L,
                                    folderName = parentName,
                                    path = absPath,
                                    thumbnailUri = uri,
                                    size = child.length(),
                                    width = 0,
                                    height = 0,
                                    dateModified = child.lastModified() / 1000L
                                )
                            )
                        }
                    }
                }
            }
        }

        return results
    }

    private fun extractFastVideoMetadata(path: String): Triple<Long, Int, Int> {
        val file = File(path)
        var dur = 0L
        var w = 0
        var h = 0

        val retriever = android.media.MediaMetadataRetriever()
        try {
            if (file.exists() && file.canRead()) {
                retriever.setDataSource(file.absolutePath)
            } else {
                retriever.setDataSource(path)
            }
            dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val temp = w
                w = h
                h = temp
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        if (dur <= 0L || w <= 0 || h <= 0) {
            try {
                val extractor = android.media.MediaExtractor()
                try {
                    if (file.exists() && file.canRead()) {
                        extractor.setDataSource(file.absolutePath)
                    } else {
                        extractor.setDataSource(path)
                    }
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("video/")) {
                            if (w <= 0 && format.containsKey(android.media.MediaFormat.KEY_WIDTH)) {
                                w = format.getInteger(android.media.MediaFormat.KEY_WIDTH)
                            }
                            if (h <= 0 && format.containsKey(android.media.MediaFormat.KEY_HEIGHT)) {
                                h = format.getInteger(android.media.MediaFormat.KEY_HEIGHT)
                            }
                            if (dur <= 0L && format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                                dur = format.getLong(android.media.MediaFormat.KEY_DURATION) / 1000L
                            }
                            break
                        }
                    }
                } finally {
                    extractor.release()
                }
            } catch (_: Exception) {}
        }

        return Triple(dur, w, h)
    }

    suspend fun scanHiddenVideos(
        blacklistedFolders: List<String> = emptyList(),
        onBatchFound: suspend (List<VideoItem>) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!isScanningHidden.compareAndSet(false, true)) {
            return@withContext
        }
        try {
            val roots = mutableListOf<File>()
            val internalRoot = android.os.Environment.getExternalStorageDirectory()
            if (internalRoot != null && internalRoot.exists() && internalRoot.canRead()) {
                roots.add(internalRoot)
            }
            val volumes = getAvailableStorageVolumes(context)
            for (vol in volumes) {
                if (!vol.isInternal) {
                    val f = File(vol.rootPath)
                    if (f.exists() && f.canRead()) {
                        roots.add(f)
                    }
                }
            }

            val blacklistedSet = blacklistedFolders.map {
                if (it.endsWith("/")) it else "$it/"
            }.toSet()

            val ignoredSystemDirNames = setOf(
                "Android",
                "LOST.DIR",
                "System Volume Information",
                "\$RECYCLE.BIN",
                ".trashed",
                ".Trash",
                ".git",
                ".gradle",
                ".idea",
                ".vscode",
                ".cache",
                ".thumbnails",
                ".npm",
                "node_modules",
                ".dart_tool",
                "build"
            )

            val visitedDirs = HashSet<String>()
            val currentBatch = mutableListOf<VideoItem>()
            val maxDepth = 12

            for (rootDir in roots) {
                currentCoroutineContext().ensureActive()
                val queue = ArrayDeque<Triple<File, Int, Boolean>>()
                queue.add(Triple(rootDir, 0, false))

                while (queue.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val (currentDir, depth, isUnderHidden) = queue.removeFirst()
                    if (depth > maxDepth) continue

                    val canonicalPath = try { currentDir.canonicalPath } catch (_: Exception) { currentDir.absolutePath }
                    if (!visitedDirs.add(canonicalPath)) continue

                    val children = try {
                        currentDir.listFiles()
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    val hasNomedia = children.any { it.name.equals(".nomedia", ignoreCase = true) }
                    val dirIsHidden = currentDir.name.startsWith(".")
                    val inHiddenContext = isUnderHidden || dirIsHidden || hasNomedia

                    for (child in children) {
                        currentCoroutineContext().ensureActive()
                        val name = child.name

                        if (child.isDirectory) {
                            if (name !in ignoredSystemDirNames && !child.absolutePath.contains("/Android/data") && !child.absolutePath.contains("/Android/obb")) {
                                val pathWithSlash = if (child.absolutePath.endsWith("/")) child.absolutePath else "${child.absolutePath}/"
                                val isBlacklisted = blacklistedSet.any { pathWithSlash.startsWith(it) }
                                if (!isBlacklisted) {
                                    val childUnderHidden = inHiddenContext || name.startsWith(".")
                                    queue.add(Triple(child, depth + 1, childUnderHidden))
                                }
                            }
                        } else if (child.isFile && child.length() > 0L) {
                            val fileIsHidden = name.startsWith(".")
                            if (inHiddenContext || fileIsHidden) {
                                if (isVideoFile(child)) {
                                    val absPath = child.absolutePath
                                    val isBlacklisted = blacklistedSet.any { absPath.startsWith(it) }
                                    if (!isBlacklisted) {
                                        val uri = Uri.fromFile(child)
                                        val parentName = child.parentFile?.name?.ifEmpty { rootDir.name } ?: rootDir.name
                                        val (fastDur, fastW, fastH) = extractFastVideoMetadata(absPath)
                                        currentBatch.add(
                                            VideoItem(
                                                uri = uri,
                                                title = child.name,
                                                duration = fastDur,
                                                folderName = parentName,
                                                path = absPath,
                                                thumbnailUri = uri,
                                                size = child.length(),
                                                width = fastW,
                                                height = fastH,
                                                dateModified = child.lastModified() / 1000L
                                            )
                                        )

                                        if (currentBatch.size >= 25) {
                                            onBatchFound(currentBatch.toList())
                                            currentBatch.clear()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (currentBatch.isNotEmpty()) {
                onBatchFound(currentBatch.toList())
                currentBatch.clear()
            }
        } finally {
            isScanningHidden.set(false)
        }
    }

    suspend fun getVisibleVideoUris(): Set<String> = withContext(Dispatchers.IO) {
        cachedUris?.let { return@withContext it }
        val videos = getAllVideos(forceRefresh = false)
        cachedUris ?: videos.map { it.uri.toString() }.toHashSet().also { cachedUris = it }
    }

    private fun deriveFoldersFromVideos(videos: List<VideoItem>): List<FolderItem> {
        val foldersMap = LinkedHashMap<String, MutableList<VideoItem>>()
        for (video in videos) {
            val lastSlash = video.path.lastIndexOf('/')
            val parentPath = if (lastSlash > 0) video.path.substring(0, lastSlash) else video.folderName
            foldersMap.getOrPut(parentPath) { mutableListOf() }.add(video)
        }
        return foldersMap.map { (parentPath, vids) ->
            val folderName = parentPath.substringAfterLast('/').ifEmpty { parentPath }
            FolderItem(
                name = folderName,
                path = parentPath,
                videoCount = vids.size,
                thumbnailUri = vids.firstOrNull()?.thumbnailUri
            )
        }.sortedBy { it.name }
    }

    suspend fun getAllVideos(forceRefresh: Boolean = false): List<VideoItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            _cachedVideos.value?.let { return@withContext it }
        }

        val scanToAwait: Deferred<List<VideoItem>> = scanMutex.withLock {
            if (!forceRefresh) {
                _cachedVideos.value?.let { return@withLock CompletableDeferred(it) }
            }
            inFlightScan?.let { existing ->
                if (!forceRefresh) {
                    return@withLock existing
                }
            }

            val deferred = repoScope.async {
                try {
                    performFullScan()
                } finally {
                    scanMutex.withLock {
                        if (inFlightScan == this) {
                            inFlightScan = null
                        }
                    }
                }
            }
            inFlightScan = deferred
            deferred
        }

        scanToAwait.await()
    }

    private suspend fun performFullScan(): List<VideoItem> = withContext(Dispatchers.IO) {
        val blacklisted = settingsRepo.playbackSettingsFlow.value.blacklistedFolders.toList()
        val mediaStoreVideos = mediaStoreHelper.getAllVideos(blacklisted)
        val allVideos = ArrayList<VideoItem>(mediaStoreVideos.size + 16)
        allVideos.addAll(mediaStoreVideos)
        val existingPaths = HashSet<String>(mediaStoreVideos.size + 16)
        for (v in mediaStoreVideos) {
            existingPaths.add(v.path)
        }

        // Also scan external/removable storage volumes (e.g., USB OTG, SD cards)
        // which are not indexed by Android MediaStore.
        try {
            val volumes = getAvailableStorageVolumes(context)
            for (volume in volumes) {
                if (!volume.isInternal) {
                    val volumeVideos = scanVolumeForVideos(volume.rootPath, blacklisted, includeHidden = true)
                    for (v in volumeVideos) {
                        if (existingPaths.add(v.path)) {
                            allVideos.add(v)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Error scanning removable volumes", e)
        }

        // Restore previously discovered & cached hidden videos from videoMetadataDao
        // Fast targeted query: only retrieve rows where uri starts with file:// or /
        try {
            val fileMetadata = videoMetadataDao.getCachedFileUriMetadata()
            for (meta in fileMetadata) {
                val uriObj = runCatching { Uri.parse(meta.uri) }.getOrNull() ?: continue
                val path = uriObj.path
                if (!path.isNullOrBlank() && (meta.uri.startsWith("file://") || meta.uri.startsWith("/"))) {
                    if (!existingPaths.contains(path)) {
                        val file = File(path)
                        if (file.exists() && file.isFile && file.length() > 0L && isVideoFile(file)) {
                            val parent = file.parentFile
                            val pathWithSlash = if (parent != null && parent.absolutePath.endsWith("/")) parent.absolutePath else "${parent?.absolutePath}/"
                            val isBlacklisted = blacklisted.any { pathWithSlash.startsWith(it) }
                            if (!isBlacklisted) {
                                existingPaths.add(path)
                                val parentName = parent?.name.orEmpty().ifEmpty { "Hidden" }
                                allVideos.add(
                                    VideoItem(
                                        uri = uriObj,
                                        title = file.name,
                                        duration = meta.duration,
                                        folderName = parentName,
                                        path = path,
                                        thumbnailUri = uriObj,
                                        size = if (meta.size > 0L) meta.size else file.length(),
                                        width = meta.width,
                                        height = meta.height,
                                        dateModified = if (meta.dateModified > 0L) meta.dateModified / 1000L else file.lastModified() / 1000L
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoRepository", "Error restoring cached hidden videos", e)
        }

        // Defer watch history file status synchronization asynchronously so scanning isn't blocked
        if (!hasSyncedWatchHistoryOnce) {
            hasSyncedWatchHistoryOnce = true
            repoScope.launch {
                try {
                    watchHistoryRepository.syncWatchHistoryFileStatus(allVideos)
                } catch (e: Exception) {
                    android.util.Log.e("VideoRepository", "Error syncing watch history file status", e)
                }
            }
        }

        val uris = HashSet<String>(allVideos.size)
        for (v in allVideos) {
            uris.add(v.uri.toString())
        }
        cachedUris = uris
        cachedFolders = deriveFoldersFromVideos(allVideos)
        _cachedVideos.value = allVideos

        allVideos
    }

    suspend fun syncWatchHistory(scannedVideos: List<VideoItem>? = null) = withContext(Dispatchers.IO) {
        val videos = scannedVideos ?: getAllVideos()
        watchHistoryRepository.syncWatchHistoryFileStatus(videos)
    }

    suspend fun getFolders(forceRefresh: Boolean = false): List<FolderItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedFolders?.let { return@withContext it }
        }
        val videos = getAllVideos(forceRefresh)
        cachedFolders ?: deriveFoldersFromVideos(videos).also { cachedFolders = it }
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
                resolution = if (item.width > 0 && item.height > 0) "${item.width}x${item.height}" else "",
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
