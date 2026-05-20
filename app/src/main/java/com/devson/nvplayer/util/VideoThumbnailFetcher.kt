package com.devson.nvplayer.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension

data class VideoThumbnailModel(val uriString: String)

class VideoThumbnailKeyer : Keyer<VideoThumbnailModel> {
    override fun key(data: VideoThumbnailModel, options: Options): String? {
        return data.uriString
    }
}

class VideoThumbnailFetcher(
    private val data: VideoThumbnailModel,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val context = options.context
        val uri = Uri.parse(data.uriString)
        
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
            val drawable = BitmapDrawable(context.resources, bitmap)
            return DrawableResult(
                drawable = drawable,
                isSampled = false,
                dataSource = DataSource.DISK
            )
        }
        
        return null
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

    class Factory : Fetcher.Factory<VideoThumbnailModel> {
        override fun create(data: VideoThumbnailModel, options: Options, imageLoader: coil.ImageLoader): Fetcher {
            return VideoThumbnailFetcher(data, options)
        }
    }
}
