package com.devson.nvplayer.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreHelper(private val context: Context) {

    suspend fun getAllVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val thumbnailsMap = getThumbnailsMap(context)

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
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
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(nameColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val data = cursor.getString(dataColumn)
                val size = cursor.getLong(sizeColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)

                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                val folderName = File(data).parentFile?.name ?: "Unknown"
                val thumbnailUri = thumbnailsMap[id] ?: contentUri

                videos.add(
                    VideoItem(
                        uri = contentUri,
                        title = title,
                        duration = duration,
                        folderName = folderName,
                        path = data,
                        thumbnailUri = thumbnailUri,
                        size = size,
                        width = width,
                        height = height
                    )
                )
            }
        }
        videos
    }

    /**
     * Retrieves videos that are in the system trash (MediaStore IS_TRASHED flag).
     * Returns empty list on platforms where the column is unavailable.
     */
    suspend fun getTrashedVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        // The IS_TRASHED column is available on API 30+.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            return@withContext emptyList()
        }
        val videos = mutableListOf<VideoItem>()
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val thumbnailsMap = getThumbnailsMap(context)

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        val queryArgs = android.os.Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Video.Media.IS_TRASHED}=1")
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Video.Media.DATE_ADDED} DESC")
        }
        context.contentResolver.query(
            collection,
            projection,
            queryArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(nameColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val data = cursor.getString(dataColumn)
                val size = cursor.getLong(sizeColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                val folderName = java.io.File(data).parentFile?.name ?: "Unknown"
                val thumbnailUri = thumbnailsMap[id] ?: contentUri
                videos.add(
                    VideoItem(
                        uri = contentUri,
                        title = title,
                        duration = duration,
                        folderName = folderName,
                        path = data,
                        thumbnailUri = thumbnailUri,
                        size = size,
                        width = width,
                        height = height
                    )
                )
            }
        }
        videos
    }

    suspend fun getFolders(): List<FolderItem> = withContext(Dispatchers.IO) {
        val allVideos = getAllVideos()
        val foldersMap = mutableMapOf<String, MutableList<VideoItem>>()
        
        allVideos.forEach { video ->
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

    private suspend fun getThumbnailsMap(context: Context): Map<Long, Uri> = withContext(Dispatchers.IO) {
        val thumbnailsMap = mutableMapOf<Long, Uri>()
        try {
            val collection = MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Video.Thumbnails.VIDEO_ID,
                MediaStore.Video.Thumbnails._ID
            )
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val videoIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Thumbnails.VIDEO_ID)
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Thumbnails._ID)
                while (cursor.moveToNext()) {
                    val videoId = cursor.getLong(videoIdCol)
                    val thumbId = cursor.getLong(idCol)
                    val thumbUri = ContentUris.withAppendedId(MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI, thumbId)
                    thumbnailsMap[videoId] = thumbUri
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        thumbnailsMap
    }
}
