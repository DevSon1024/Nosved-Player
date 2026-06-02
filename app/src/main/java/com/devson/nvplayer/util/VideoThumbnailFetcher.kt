package com.devson.nvplayer.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import coil3.size.Dimension
import coil3.video.videoFrameMicros
import coil3.video.videoFramePercent
import coil3.video.videoFrameIndex
import com.devson.nvplayer.util.thumbnail.ThumbnailLoader
import com.devson.nvplayer.util.thumbnail.ThumbnailKey
import com.devson.nvplayer.util.thumbnail.getVideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class VideoThumbnailFetcher(
    private val data: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val context = options.context
        val repository = ThumbnailLoader.getRepository(context)

        val width = options.size.width.pxOrElse { 256 }
        val height = options.size.height.pxOrElse { 256 }

        val metadata = withContext(Dispatchers.IO) {
            getVideoMetadata(context, data)
        }

        val key = ThumbnailKey(
            uriString = data.toString(),
            lastModified = metadata.lastModified,
            fileSize = metadata.fileSize,
            width = width,
            height = height
        )

        val bitmap = repository.getThumbnail(key, data)

        return if (bitmap != null) {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            val buffer = okio.Buffer().apply { write(bytes) }

            SourceFetchResult(
                source = ImageSource(
                    source = buffer,
                    fileSystem = options.fileSystem
                ),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        } else {
            null
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
            val hasFrameSpec = options.videoFrameMicros >= 0L ||
                    options.videoFramePercent >= 0.0 ||
                    options.videoFrameIndex >= 0
            if (hasFrameSpec) {
                return null
            }

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
