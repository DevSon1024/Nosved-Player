package com.devson.nvplayer.util.thumbnail

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

data class ThumbnailKey(
    val uriString: String,
    val lastModified: Long,
    val fileSize: Long,
    val width: Int,
    val height: Int
) {
    fun toCacheFileName(): String {
        val safeUri = uriString.replace("[^a-zA-Z0-9]".toRegex(), "_")
        return "${safeUri}_${lastModified}_${fileSize}_${width}x${height}.webp"
    }
}

data class VideoMetadata(
    val lastModified: Long,
    val fileSize: Long
)

fun getVideoMetadata(context: Context, uri: Uri): VideoMetadata {
    if (uri.scheme == "file") {
        val file = File(uri.path ?: "")
        return VideoMetadata(file.lastModified(), file.length())
    } else if (uri.scheme == "content") {
        val projection = arrayOf(MediaStore.Video.Media.DATE_MODIFIED, MediaStore.Video.Media.SIZE)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateModifiedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val lastModified = if (dateModifiedCol != -1) cursor.getLong(dateModifiedCol) * 1000 else 0L
                    val fileSize = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    return VideoMetadata(lastModified, fileSize)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return VideoMetadata(0L, 0L)
}
