package com.devson.nvplayer.util

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.ImageFetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
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
                
                ImageFetchResult(
                    image = BitmapDrawable(options.context.resources, bitmap).asImage(),
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
