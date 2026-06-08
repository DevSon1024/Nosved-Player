package com.devson.nvplayer.util

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
                val bitmap = options.context.contentResolver
                    .loadThumbnail(uri, MINI_KIND_SIZE, null)
                return@withContext ImageFetchResult(
                    image = bitmap.asImage(),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } catch (_: Exception) {}
        }

        val retriever = android.media.MediaMetadataRetriever()
        try {
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                retriever.setDataSource(options.context, uri)
            } else {
                retriever.setDataSource(uri.path)
            }
            val durationMs = retriever
                .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val seekUs = if (durationMs > 0) (durationMs * 0.20).toLong() * 1_000L else 0L

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    seekUs,
                    android.media.MediaMetadataRetriever.OPTION_PREVIOUS_SYNC,
                    512, 384
                )
            } else {
                retriever.getFrameAtTime(seekUs, android.media.MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
            } ?: return@withContext null

            ImageFetchResult(
                image = bitmap.asImage(),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        private val mimeCache = ConcurrentHashMap<String, Boolean>()

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val isVideo = mimeCache.getOrPut(data.toString()) { isVideoUri(data, options) }
            return if (isVideo) MediaStoreThumbnailFetcher(data, options) else null
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
        private val MINI_KIND_SIZE = Size(512, 384)
        private val VIDEO_EXTENSIONS = listOf(
            ".mp4", ".mkv", ".webm", ".avi", ".3gp",
            ".flv", ".mov", ".m4v", ".ts", ".wmv"
        )
    }
}
