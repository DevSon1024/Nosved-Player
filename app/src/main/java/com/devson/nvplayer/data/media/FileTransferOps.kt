package com.devson.nvplayer.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.devson.nvplayer.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileTransferOps {

    suspend fun copyVideoScoped(
        context: Context,
        sourceVideo: VideoItem,
        destRelativePath: String,
        onProgress: (percent: Int) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, sourceVideo.title)
                val mimeType = resolver.getType(sourceVideo.uri) ?: "video/mp4"
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val formattedPath = if (destRelativePath.endsWith("/")) destRelativePath else "$destRelativePath/"
                    put(MediaStore.Video.Media.RELATIVE_PATH, formattedPath)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val destUri = resolver.insert(collection, contentValues)
                ?: throw Exception("Failed to insert media item in MediaStore")

            try {
                resolver.openInputStream(sourceVideo.uri).use { input ->
                    if (input == null) throw Exception("Source input stream is null")
                    resolver.openOutputStream(destUri).use { output ->
                        if (output == null) throw Exception("Destination output stream is null")
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesCopied = 0L
                        val fileSize = sourceVideo.size
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesCopied += bytesRead
                            if (fileSize > 0) {
                                val progress = ((totalBytesCopied * 100) / fileSize).toInt()
                                onProgress(progress.coerceIn(0, 100))
                            }
                        }
                    }
                }

                val updatedValues = ContentValues().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                }
                resolver.update(destUri, updatedValues, null, null)

                var absolutePath = ""
                val projection = arrayOf(MediaStore.Video.Media.DATA)
                resolver.query(destUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                        absolutePath = cursor.getString(columnIndex) ?: ""
                    }
                }
                
                if (absolutePath.isEmpty()) {
                    absolutePath = destUri.toString()
                }
                absolutePath
            } catch (e: Exception) {
                resolver.delete(destUri, null, null)
                throw e
            }
        }
    }

    suspend fun copyVideoToTreeUri(
        context: Context,
        sourceVideo: VideoItem,
        destTreeUri: Uri,
        onProgress: (percent: Int) -> Unit = {}
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val rootDoc = DocumentFile.fromTreeUri(context, destTreeUri)
                ?: throw Exception("Failed to get DocumentFile from Tree Uri")
            
            val mimeType = resolver.getType(sourceVideo.uri) ?: "video/mp4"
            val newFile = rootDoc.createFile(mimeType, sourceVideo.title)
                ?: throw Exception("Failed to create file in destination directory")
            
            val destFileUri = newFile.uri

            resolver.openInputStream(sourceVideo.uri).use { input ->
                if (input == null) throw Exception("Source input stream is null")
                resolver.openOutputStream(destFileUri).use { output ->
                    if (output == null) throw Exception("Destination output stream is null")
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesCopied = 0L
                    val fileSize = sourceVideo.size
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesCopied += bytesRead
                        if (fileSize > 0) {
                            val progress = ((totalBytesCopied * 100) / fileSize).toInt()
                            onProgress(progress.coerceIn(0, 100))
                        }
                    }
                }
            }
            destFileUri
        }
    }
}
