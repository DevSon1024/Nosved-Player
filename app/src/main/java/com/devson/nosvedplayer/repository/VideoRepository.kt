package com.devson.nosvedplayer.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.devson.nosvedplayer.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.devson.nosvedplayer.data.NosvedDatabase

class VideoRepository(private val context: Context) {

    suspend fun getAllVideos(): List<Video> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Video>()
        
        val watchHistoryDao = NosvedDatabase.getInstance(context).watchHistoryDao()
        val historyList = watchHistoryDao.getAllHistoryList()
        val historyMap = historyList.associate { it.uri to it.lastPositionMs }

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
                
                var resolutionStr: String? = null
                if (resolutionColumn != -1) {
                    val res = cursor.getString(resolutionColumn)
                    if (res != null) {
                        val parts = res.split("x")
                        if (parts.size == 2) {
                            val height = parts[1].toIntOrNull()
                            if (height != null) {
                                resolutionStr = "${height}p"
                            }
                        }
                    }
                }

                val contentUri = ContentUris.withAppendedId(collection, id)
                val playedTime = historyMap[contentUri.toString()]

                videos.add(
                    Video(
                        uri = contentUri.toString(),
                        title = name,
                        duration = duration,
                        size = size,
                        folderId = folderId,
                        folderName = folderName,
                        dateAdded = dateAdded * 1000L, // MediaStore stores DATE_ADDED in seconds. Convert to millis.
                        path = path,
                        resolution = resolutionStr,
                        playedTime = playedTime
                    )
                )
            }
        }
        
        return@withContext videos
    }

    suspend fun getFoldersWithVideos(): Map<com.devson.nosvedplayer.model.VideoFolder, List<Video>> {
        val allVideos = getAllVideos()
        return allVideos.groupBy { com.devson.nosvedplayer.model.VideoFolder(it.folderId, it.folderName) }
    }
}
