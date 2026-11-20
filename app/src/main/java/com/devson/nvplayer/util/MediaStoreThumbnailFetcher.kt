package com.devson.nvplayer.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import java.util.concurrent.ConcurrentHashMap

class MediaStoreThumbnailFetcher(
    private val uri: Uri,
    private val options: Options,
    private val diskCache: Lazy<DiskCache?>
) : Fetcher {

    private val safeDiskCacheKey: String
        get() = "system_thumb_${uri}".encodeUtf8().sha256().hex()

    override suspend fun fetch(): FetchResult? = withContext(FETCH_DISPATCHER) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            uri.scheme != ContentResolver.SCHEME_CONTENT
        ) {
            return@withContext null
        }

        // 1. Instant disk cache lookup to avoid hitting ContentResolver and native MediaExtractor
        if (options.diskCachePolicy.readEnabled) {
            val snapshot = runCatching { diskCache.value?.openSnapshot(safeDiskCacheKey) }.getOrNull()
            if (snapshot != null) {
                val cachedBitmap = snapshot.use { snap ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    runCatching {
                        snap.data.toFile().inputStream().use { stream ->
                            BitmapFactory.decodeStream(stream, null, decodeOptions)
                        }
                    }.getOrNull()
                }

                if (cachedBitmap != null) {
                    return@withContext ImageFetchResult(
                        image = cachedBitmap.asImage(),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            }
        }

        // 2. Query hardware/system thumbnail from ContentResolver
        try {
            val targetWidth = when (val w = options.size.width) {
                is coil3.size.Dimension.Pixels -> w.px.coerceIn(96, 512)
                else -> 320
            }
            val targetHeight = when (val h = options.size.height) {
                is coil3.size.Dimension.Pixels -> h.px.coerceIn(54, 512)
                else -> 180
            }
            val bitmap = options.context.contentResolver.loadThumbnail(
                uri,
                Size(targetWidth, targetHeight),
                null
            ) ?: return@withContext null

            // 3. Persist to Coil disk cache so subsequent startups read locally without IPC
            if (options.diskCachePolicy.writeEnabled) {
                val editor = runCatching { diskCache.value?.openEditor(safeDiskCacheKey) }.getOrNull()
                if (editor != null) {
                    try {
                        editor.data.toFile().outputStream().use { output ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                        }
                        editor.commit()
                    } catch (_: Exception) {
                        runCatching { editor.abort() }
                    }
                }
            }

            return@withContext ImageFetchResult(
                image = bitmap.asImage(),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } catch (_: Exception) {
            // Fall back to Coil's custom video thumbnail decoder
            return@withContext null
        }
    }

    class Factory(
        private val isSystemThumbnailPreferred: () -> Boolean = { true }
    ) : Fetcher.Factory<Uri> {
        private val mimeCache = ConcurrentHashMap<String, Boolean>()

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!isSystemThumbnailPreferred()) {
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                data.scheme == ContentResolver.SCHEME_CONTENT
            ) {
                val isVideo = mimeCache.getOrPut(data.toString()) { isVideoUri(data, options) }
                if (isVideo) {
                    return MediaStoreThumbnailFetcher(data, options, lazy { imageLoader.diskCache })
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

    class StringFactory(
        private val isSystemThumbnailPreferred: () -> Boolean = { true }
    ) : Fetcher.Factory<String> {
        private val inner = Factory(isSystemThumbnailPreferred)
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uri = try { Uri.parse(data) } catch (_: Exception) { return null }
            return inner.create(uri, options, imageLoader)
        }
    }

    companion object {
        private val FETCH_DISPATCHER = Dispatchers.IO.limitedParallelism(4)
        private val VIDEO_EXTENSIONS = listOf(
            ".mp4", ".mkv", ".webm", ".avi", ".3gp",
            ".flv", ".mov", ".m4v", ".ts", ".wmv"
        )
    }
}
