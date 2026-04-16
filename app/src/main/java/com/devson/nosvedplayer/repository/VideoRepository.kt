package com.devson.nosvedplayer.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.devson.nosvedplayer.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.devson.nosvedplayer.data.NosvedDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

class VideoRepository(private val context: Context) {

    suspend fun getAllVideos(
        showHiddenFiles: Boolean = false,
        recognizeNoMedia: Boolean = false,
        scanFoldersList: Set<String> = emptySet()
    ): List<Video> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Video>()

        val watchHistoryDao = NosvedDatabase.getInstance(context).watchHistoryDao()
        val historyList = watchHistoryDao.getAllHistoryList()
        val historyMap = historyList.associate { it.uri to it.lastPositionMs }
        val historyTimestampMap = historyList.associate { it.uri to it.lastPlayedAt }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA,
            "resolution", // Works from API 21
            MediaStore.Video.Media.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        // Pre-calculate roots to filter against
        val hasAllowedFolders = scanFoldersList.any { !it.startsWith("HIDDEN:") }

        // Track paths to avoid duplicates during hidden-file scan
        val seenPaths = mutableSetOf<String>()

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val resolutionColumn = cursor.getColumnIndex("resolution")
            val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val folderId = cursor.getString(bucketIdColumn) ?: "Unknown"
                val folderName = cursor.getString(bucketColumn) ?: "Unknown"
                val dateAdded = cursor.getLong(dateAddedColumn)
                val path = cursor.getString(dataColumn) ?: ""

                // Filter by scanFoldersList
                val matchingRoots = scanFoldersList.filter { root ->
                    val cleanRoot = root.removePrefix("HIDDEN:")
                    path.startsWith(cleanRoot)
                }

                if (matchingRoots.isNotEmpty()) {
                    val longestMatch = matchingRoots.maxByOrNull { it.removePrefix("HIDDEN:").length }
                    if (longestMatch?.startsWith("HIDDEN:") == true) {
                        continue
                    }
                } else if (hasAllowedFolders) {
                    continue
                }

                var finalDuration = duration
                var finalSize = size

                if (finalSize <= 0L || finalDuration <= 0L) {
                    try {
                        if (path.isNotEmpty()) {
                            val file = java.io.File(path)
                            if (file.exists() && file.isFile) {
                                if (finalSize <= 0L) finalSize = file.length()
                            }
                        }

                        val contentUri = ContentUris.withAppendedId(collection, id)
                        context.contentResolver.openFileDescriptor(contentUri, "r")?.use { pfd ->
                            if (finalSize <= 0L) finalSize = pfd.statSize
                            if (finalDuration <= 0L) {
                                val retriever = android.media.MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(pfd.fileDescriptor)
                                    val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    if (timeStr != null) finalDuration = timeStr.toLong()
                                } finally {
                                    retriever.release()
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Resolution: keep full "WIDTHxHEIGHT" format (e.g. "1920x1080")
                var resolutionStr: String? = null
                if (resolutionColumn != -1) {
                    val res = cursor.getString(resolutionColumn)
                    if (!res.isNullOrEmpty()) {
                        // MediaStore stores it as "WIDTHxHEIGHT" already - use as-is
                        resolutionStr = res
                    }
                }

                var mimeTypeStr: String? = null
                if (mimeTypeColumn != -1) {
                    mimeTypeStr = cursor.getString(mimeTypeColumn)
                }

                // Frame rate is NOT extracted at list-load time.
                // Opening a MediaMetadataRetriever for every video is O(n) file I/O
                // and causes 50-200 ms per video - unacceptably slow for large libraries.
                // frameRate stays null here; the detail screen extracts it on demand.

                val contentUri = ContentUris.withAppendedId(collection, id)
                val playedTime = historyMap[contentUri.toString()]

                if (path.isNotEmpty()) seenPaths.add(path)

                videos.add(
                    Video(
                        uri = contentUri.toString(),
                        title = name,
                        duration = finalDuration,
                        size = finalSize,
                        folderId = folderId,
                        folderName = folderName,
                        dateAdded = dateAdded * 1000L,
                        path = path,
                        resolution = resolutionStr,
                        mimeType = mimeTypeStr,
                        // frameRate left null - extracted on demand in the detail view
                        playedTime = playedTime,
                        lastPlayedAt = historyTimestampMap[contentUri.toString()]
                    )
                )
            }
        }

        //  Hidden file scan
        // MediaStore omits files inside hidden directories (starting with '.').
        // Strategy: (1) collect candidate paths quickly with no I/O, then
        //           (2) extract metadata in PARALLEL so the total cost is
        //               ~max(single file cost) rather than sum(all file costs).
        if (showHiddenFiles) {
            val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "flv", "3gp", "ts", "wmv")
            val root = Environment.getExternalStorageDirectory()

            // Step A: collect candidate File objects (pure filesystem listing, no retriever I/O)
            val candidateFiles = mutableListOf<File>()

            fun collectHiddenDir(dir: File) {
                if (!dir.exists() || !dir.isDirectory) return
                if (recognizeNoMedia && File(dir, ".nomedia").exists()) return
                dir.listFiles()?.forEach { child ->
                    if (child.isDirectory) {
                        collectHiddenDir(child)
                    } else if (child.isFile) {
                        val ext = child.extension.lowercase()
                        val childPath = child.absolutePath
                        
                        val matchingRoots = scanFoldersList.filter { root ->
                            val cleanRoot = root.removePrefix("HIDDEN:")
                            childPath.startsWith(cleanRoot)
                        }
                        if (matchingRoots.isNotEmpty()) {
                            val longestMatch = matchingRoots.maxByOrNull { it.removePrefix("HIDDEN:").length }
                            if (longestMatch?.startsWith("HIDDEN:") == true) {
                                return@forEach
                            }
                        } else if (hasAllowedFolders) {
                            return@forEach
                        }

                        if (ext in videoExtensions && childPath !in seenPaths) {
                            seenPaths.add(childPath)
                            candidateFiles.add(child)
                        }
                    }
                }
            }

            root.listFiles { f -> f.isDirectory && f.name.startsWith(".") }?.forEach { collectHiddenDir(it) }
            root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }?.forEach { topDir ->
                topDir.listFiles { f -> f.isDirectory && f.name.startsWith(".") }?.forEach { collectHiddenDir(it) }
            }

            // Step B: resolve metadata in parallel
            if (candidateFiles.isNotEmpty()) {
                val hiddenVideos = coroutineScope {
                    candidateFiles.map { child ->
                        async(Dispatchers.IO) {
                            val fileUri = "file://${child.absolutePath}"
                            var dur = 0L
                            var fr: Float? = null
                            var res: String? = null
                            try {
                                val ret = android.media.MediaMetadataRetriever()
                                ret.setDataSource(child.absolutePath)
                                dur = ret.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                                val w = ret.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                                val h = ret.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                                if (w > 0 && h > 0) res = "${w}x${h}"
                                fr = ret.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                                    ?.toFloatOrNull()?.takeIf { it > 0f }
                                ret.release()
                            } catch (_: Exception) {}

                            Video(
                                uri = fileUri,
                                title = child.name,
                                duration = dur,
                                size = child.length(),
                                folderId = child.parentFile?.absolutePath ?: "Unknown",
                                folderName = child.parentFile?.name ?: "Unknown",
                                dateAdded = child.lastModified(),
                                path = child.absolutePath,
                                resolution = res,
                                mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(child.extension.lowercase()),
                                frameRate = fr,
                                playedTime = historyMap[fileUri],
                                lastPlayedAt = historyTimestampMap[fileUri]
                            )
                        }
                    }.awaitAll()
                }
                videos.addAll(hiddenVideos)
            }
        }
        videos
    }

    suspend fun getTrashedVideos(): List<Video> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Video>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext videos

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_EXPIRES
        )

        val args = android.os.Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Video.Media.IS_TRASHED} == 1")
        }

        context.contentResolver.query(collection, projection, args, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME).takeIf { it >= 0 }
            val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION).takeIf { it >= 0 }
            val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE).takeIf { it >= 0 }
            val bIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID).takeIf { it >= 0 }
            val bNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME).takeIf { it >= 0 }
            val dateAddedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED).takeIf { it >= 0 }
            val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA).takeIf { it >= 0 }
            val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE).takeIf { it >= 0 }
            val expireCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_EXPIRES).takeIf { it >= 0 }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                videos.add(
                    Video(
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        title = nameCol?.let { cursor.getString(it) } ?: "Unknown",
                        duration = durCol?.let { cursor.getLong(it) } ?: 0L,
                        size = sizeCol?.let { cursor.getLong(it) } ?: 0L,
                        folderId = bIdCol?.let { cursor.getString(it) } ?: "Unknown",
                        folderName = bNameCol?.let { cursor.getString(it) } ?: "Unknown",
                        dateAdded = (dateAddedCol?.let { cursor.getLong(it) } ?: 0L) * 1000L,
                        path = dataCol?.let { cursor.getString(it) } ?: "",
                        mimeType = mimeCol?.let { cursor.getString(it) },
                        dateExpires = (expireCol?.let { cursor.getLong(it) } ?: 0L) * 1000L
                    )
                )
            }
        }
        return@withContext videos
    }

    suspend fun searchVideos(
        query: String,
        showHiddenFiles: Boolean = false,
        recognizeNoMedia: Boolean = false,
        scanFoldersList: Set<String> = emptySet()
    ): List<Video> {
        val all = getAllVideos(showHiddenFiles, recognizeNoMedia, scanFoldersList)
        val q = query.trim().lowercase()
        return if (q.isEmpty()) all else all.filter { it.title.lowercase().contains(q) }
    }

    suspend fun getFoldersWithVideos(): Map<com.devson.nosvedplayer.model.VideoFolder, List<Video>> {
        val allVideos = getAllVideos()
        return allVideos.groupBy { com.devson.nosvedplayer.model.VideoFolder(it.folderId, it.folderName) }
    }
}
