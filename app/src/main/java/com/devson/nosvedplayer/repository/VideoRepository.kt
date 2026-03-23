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
import java.io.File

class VideoRepository(private val context: Context) {

    suspend fun getAllVideos(
        showHiddenFiles: Boolean = false,
        recognizeNoMedia: Boolean = false
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
            "resolution" // Works from API 21
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

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

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val folderId = cursor.getString(bucketIdColumn) ?: "Unknown"
                val folderName = cursor.getString(bucketColumn) ?: "Unknown"
                val dateAdded = cursor.getLong(dateAddedColumn)
                val path = cursor.getString(dataColumn) ?: ""

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

                // Frame rate is NOT extracted at list-load time.
                // Opening a MediaMetadataRetriever for every video is O(n) file I/O
                // and causes 50-200 ms per video — unacceptably slow for large libraries.
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
                        // frameRate left null — extracted on demand in the detail view
                        playedTime = playedTime,
                        lastPlayedAt = historyTimestampMap[contentUri.toString()]
                    )
                )
            }
        }

        //  Hidden file scan 
        // MediaStore filters out files inside hidden directories (those with
        // a path segment starting with '.'). Walk those directories manually.
        if (showHiddenFiles) {
            val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "flv", "3gp", "ts", "wmv")
            val root = Environment.getExternalStorageDirectory()

            fun scanHiddenDir(dir: File) {
                if (!dir.exists() || !dir.isDirectory) return
                // Skip if .nomedia present and recognizeNoMedia is true (honour .nomedia)
                if (recognizeNoMedia && File(dir, ".nomedia").exists()) return

                dir.listFiles()?.forEach { child ->
                    if (child.isDirectory) {
                        // Recurse into all subdirs of hidden dirs
                        scanHiddenDir(child)
                    } else if (child.isFile) {
                        val ext = child.extension.lowercase()
                        if (ext in videoExtensions && child.absolutePath !in seenPaths) {
                            seenPaths.add(child.absolutePath)
                            // Build a file:// URI - content URI won't exist for hidden files
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
                                val frStr = ret.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                                fr = frStr?.toFloatOrNull()?.takeIf { it > 0f }
                                ret.release()
                            } catch (_: Exception) {}

                            val folderName = child.parentFile?.name ?: "Unknown"
                            val folderId = child.parentFile?.absolutePath ?: "Unknown"
                            videos.add(
                                Video(
                                    uri = fileUri,
                                    title = child.name,
                                    duration = dur,
                                    size = child.length(),
                                    folderId = folderId,
                                    folderName = folderName,
                                    dateAdded = child.lastModified(),
                                    path = child.absolutePath,
                                    resolution = res,
                                    frameRate = fr,
                                    playedTime = historyMap[fileUri]
                                )
                            )
                        }
                    }
                }
            }

            // Walk only top-level hidden directories to avoid scanning everything
            root.listFiles { f -> f.isDirectory && f.name.startsWith(".") }?.forEach { hiddenDir ->
                scanHiddenDir(hiddenDir)
            }
            // Also scan hidden sub-dirs inside non-hidden dirs
            root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }?.forEach { topDir ->
                topDir.listFiles { f -> f.isDirectory && f.name.startsWith(".") }?.forEach { hiddenSub ->
                    scanHiddenDir(hiddenSub)
                }
            }
        }

        return@withContext videos
    }

    suspend fun getFoldersWithVideos(): Map<com.devson.nosvedplayer.model.VideoFolder, List<Video>> {
        val allVideos = getAllVideos()
        return allVideos.groupBy { com.devson.nosvedplayer.model.VideoFolder(it.folderId, it.folderName) }
    }
}
