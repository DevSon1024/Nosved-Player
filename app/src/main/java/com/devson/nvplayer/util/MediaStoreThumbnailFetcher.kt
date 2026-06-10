package com.devson.nvplayer.util

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class MediaStoreThumbnailFetcher(
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            uri.scheme == ContentResolver.SCHEME_CONTENT
        ) {
            try {
                // Request a 512x512 thumbnail natively from the OS
                val bitmap = options.context.contentResolver.loadThumbnail(
                    uri,
                    Size(512, 512),
                    null
                ) ?: return@withContext null

                return@withContext ImageFetchResult(
                    image = bitmap.asImage(),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } catch (e: Exception) {
                // Fall back to Coil's default VideoFrameDecoder if fetching fails
                return@withContext null
            }
        }
        null
    }

    class Factory : Fetcher.Factory<Uri> {
        private val mimeCache = ConcurrentHashMap<String, Boolean>()

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                data.scheme == ContentResolver.SCHEME_CONTENT
            ) {
                val isVideo = mimeCache.getOrPut(data.toString()) { isVideoUri(data, options) }
                if (isVideo) {
                    return MediaStoreThumbnailFetcher(data, options)
                }
            }
            return null
        }

        private fun isVideoUri(uri: Uri, options: Options): Boolean {
            return when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    val segs = uri.pathSegments
                    when {
                        segs.contains("thumbnails") -> false
                        segs.contains("video") -> true
                        else -> try {
                            options.context.contentResolver.getType(uri)
                                ?.startsWith("video/") == true
                        } catch (_: Exception) { false }
                    }
                }
                ContentResolver.SCHEME_FILE -> {
                    val path = uri.path ?: return false
                    VIDEO_EXTENSIONS.any { path.endsWith(it, ignoreCase = true) }
                }
                else -> false
            }
        }
    }

    class StringFactory : Fetcher.Factory<String> {
        private val inner = Factory()
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uri = try { Uri.parse(data) } catch (_: Exception) { return null }
            return inner.create(uri, options, imageLoader)
        }
    }

    companion object {
        private val VIDEO_EXTENSIONS = listOf(
            ".mp4", ".mkv", ".webm", ".avi", ".3gp",
            ".flv", ".mov", ".m4v", ".ts", ".wmv"
        )
    }
}
