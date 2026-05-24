package com.devson.nvplayer.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import coil3.size.Dimension
import okio.Buffer
import coil3.video.videoFrameMicros
import coil3.video.videoFramePercent
import coil3.video.videoFrameIndex
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

private val extractionDispatcher = Executors.newFixedThreadPool(2) { runnable ->
    Thread(runnable, "thumbnail-extractor").apply {
        priority = Thread.MIN_PRIORITY
    }
}.asCoroutineDispatcher()

class VideoThumbnailFetcher(
    private val data: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(extractionDispatcher) {
        val context = options.context
        val uri = data
        
        val bitmap: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // On Android 10+ (API 29+), use ContentResolver.loadThumbnail which is highly optimized and safe
                val width = options.size.width.pxOrElse { 512 }
                val height = options.size.height.pxOrElse { 512 }
                context.contentResolver.loadThumbnail(uri, Size(width, height), null)
            } else {
                // On older APIs, retrieve the ID from the content URI or parse file path
                val id = try {
                    ContentUris.parseId(uri)
                } catch (e: Exception) {
                    // If not a content URI with an ID, query the MediaStore by file path
                    val path = uri.path
                    if (path != null) {
                        getVideoIdFromPath(context, path)
                    } else {
                        -1L
                    }
                }
                
                if (id != -1L) {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        if (bitmap != null) {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            val buffer = okio.Buffer().apply { write(bytes) }
            
            SourceFetchResult(
                source = ImageSource(
                    source = buffer,
                    fileSystem = options.fileSystem
                ),
                mimeType = "image/jpeg",
                dataSource = DataSource.MEMORY
            )
        } else {
            null
        }
    }

    private fun getVideoIdFromPath(context: Context, path: String): Long {
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val selection = "${MediaStore.Video.Media.DATA} = ?"
        val selectionArgs = arrayOf(path)
        return try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    cursor.getLong(idColumn)
                } else {
                    -1L
                }
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    private fun Dimension.pxOrElse(block: () -> Int): Int {
        return when (this) {
            is Dimension.Pixels -> px
            else -> block()
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: coil3.ImageLoader): Fetcher? {
            // If the request specifies a video frame time/micros, don't intercept it (let VideoFrameDecoder handle it)
            val hasFrameSpec = options.videoFrameMicros >= 0L ||
                    options.videoFramePercent >= 0.0 ||
                    options.videoFrameIndex >= 0
            if (hasFrameSpec) {
                return null
            }

            // Check if this is a video URI
            val scheme = data.scheme
            val isVideo = if (scheme == ContentResolver.SCHEME_CONTENT) {
                val type = try {
                    context.contentResolver.getType(data)
                } catch (e: Exception) {
                    null
                }
                if (type != null) {
                    type.startsWith("video/")
                } else {
                    data.pathSegments.contains("video") && !data.pathSegments.contains("thumbnails")
                }
            } else if (scheme == ContentResolver.SCHEME_FILE) {
                val path = data.path
                path != null && (
                    path.endsWith(".mp4", ignoreCase = true) ||
                    path.endsWith(".mkv", ignoreCase = true) ||
                    path.endsWith(".webm", ignoreCase = true) ||
                    path.endsWith(".avi", ignoreCase = true) ||
                    path.endsWith(".3gp", ignoreCase = true) ||
                    path.endsWith(".flv", ignoreCase = true)
                )
            } else {
                false
            }

            return if (isVideo) {
                VideoThumbnailFetcher(data, options)
            } else {
                null
            }
        }
    }

    class StringFactory(private val context: Context) : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: coil3.ImageLoader): Fetcher? {
            val uri = try {
                Uri.parse(data)
            } catch (e: Exception) {
                return null
            }
            return Factory(context).create(uri, options, imageLoader)
        }
    }
}
