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

    suspend fun getAllVideos(
        blacklistedFolders: List<String> = emptyList(),
        folderName: String? = null
    ): List<VideoItem> = withContext(Dispatchers.IO) {
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
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val selectionParts = mutableListOf<String>()
        val selectionArgsList = mutableListOf<String>()

        if (folderName != null) {
            selectionParts.add("${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} = ?")
            selectionArgsList.add(folderName)
        }

        if (blacklistedFolders.isNotEmpty()) {
            blacklistedFolders.forEach { path ->
                selectionParts.add("${MediaStore.Video.Media.DATA} NOT LIKE ?")
                val cleanPath = if (path.endsWith("/")) path else "$path/"
                selectionArgsList.add("$cleanPath%")
            }
        }

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val selectionArgs = if (selectionArgsList.isNotEmpty()) selectionArgsList.toTypedArray() else null

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(nameColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val data = cursor.getString(dataColumn) ?: ""
                val size = cursor.getLong(sizeColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)

                val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                val resolvedFolderName = File(data).parentFile?.name ?: "Unknown"
                val thumbnailUri = thumbnailsMap[id] ?: contentUri

                videos.add(
                    VideoItem(
                        uri = contentUri,
                        title = title,
                        duration = duration,
                        folderName = resolvedFolderName,
                        path = data,
                        thumbnailUri = thumbnailUri,
                        size = size,
                        width = width,
                        height = height,
                        dateModified = dateModified
                    )
                )
            }
        }
        videos
    }

    suspend fun getVideosByFolder(folderName: String, blacklistedFolders: List<String> = emptyList()): List<VideoItem> =
        getAllVideos(blacklistedFolders, folderName)

    /**
     * Retrieves videos that are in the system trash (MediaStore IS_TRASHED flag).
     * Returns empty list on platforms where the column is unavailable.
     */
    suspend fun getTrashedVideos(blacklistedFolders: List<String> = emptyList()): List<VideoItem> = withContext(Dispatchers.IO) {
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
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        val selectionBuilder = StringBuilder("${MediaStore.Video.Media.IS_TRASHED}=1")
        var selectionArgs: Array<String>? = null
        if (blacklistedFolders.isNotEmpty()) {
            blacklistedFolders.forEach { _ ->
                selectionBuilder.append(" AND ${MediaStore.Video.Media.DATA} NOT LIKE ?")
            }
            selectionArgs = blacklistedFolders.map { path ->
                val cleanPath = if (path.endsWith("/")) path else "$path/"
                "$cleanPath%"
            }.toTypedArray()
        }

        val queryArgs = android.os.Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selectionBuilder.toString())
            if (selectionArgs != null) {
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }
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
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(nameColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val data = cursor.getString(dataColumn) ?: ""
                val size = cursor.getLong(sizeColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)

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
                        height = height,
                        dateModified = dateModified
                    )
                )
            }
        }
        videos
    }

    suspend fun getFolders(blacklistedFolders: List<String> = emptyList()): List<FolderItem> = withContext(Dispatchers.IO) {
        val allVideos = getAllVideos(blacklistedFolders)
        val foldersMap = mutableMapOf<String, MutableList<VideoItem>>()
        
        allVideos.forEach { video ->
            val parentPath = File(video.path).parentFile?.absolutePath ?: video.folderName
            if (!foldersMap.containsKey(parentPath)) {
                foldersMap[parentPath] = mutableListOf()
            }
            foldersMap[parentPath]?.add(video)
        }

        foldersMap.map { (parentPath, videos) ->
            val folderName = File(parentPath).name.ifEmpty { parentPath }
            FolderItem(
                name = folderName,
                path = parentPath,
                videoCount = videos.size,
                thumbnailUri = videos.firstOrNull()?.thumbnailUri
            )
        }.sortedBy { it.name }
    }

    private suspend fun getThumbnailsMap(context: Context): Map<Long, Uri> = withContext(Dispatchers.IO) {
        // MediaStore.Video.Thumbnails is deprecated since API 29.
        // Coil (coil-video) extracts frames from the video content URI directly,
        // so a separate thumbnails map is no longer needed.
        emptyMap()
    }
}
