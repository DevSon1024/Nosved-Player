package com.devson.nosvedplayer.util

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreThumbnailFetcher(
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                // Ensure it's a content URI, which is what MediaStore uses
                if (uri.scheme != "content") {
                    return@withContext null
                }
                
                // Request a 512x512 thumbnail natively from the OS
                val bitmap = options.context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                
                DrawableResult(
                    drawable = BitmapDrawable(options.context.resources, bitmap),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } catch (e: Exception) {
                // Fall back to Coil's default VideoFrameDecoder if fetching fails
                null
            }
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher {
            return MediaStoreThumbnailFetcher(data, options)
        }
    }
}
