package com.devson.nvplayer.util

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.ImageFetchResult
import coil3.fetch.FetchResult
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
                if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
                    return@withContext null
                }
                
                // Request a 512x512 thumbnail natively from the OS
                val bitmap = options.context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                
                ImageFetchResult(
                    image = bitmap.asImage(),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } catch (e: Exception) {
                // Fall back to other fetchers if fetching fails
                null
            }
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Only handle content URIs
            if (data.scheme != ContentResolver.SCHEME_CONTENT) {
                return null
            }
            // Ensure the MIME type is video
            val type = try {
                options.context.contentResolver.getType(data)
            } catch (e: Exception) {
                null
            }
            val isVideo = type?.startsWith("video/") == true ||
                    (data.pathSegments.contains("video") && !data.pathSegments.contains("thumbnails"))
            
            return if (isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStoreThumbnailFetcher(data, options)
            } else {
                null
            }
        }
    }
}
