package com.devson.nvplayer

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.video.VideoFrameDecoder
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import com.devson.nvplayer.util.MediaStoreThumbnailFetcher

class NosvedApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Determine a sensible disk cache size:  10% of free space, clamped between 50 MB and 500 MB.
        val cacheDir = cacheDir.resolve("video_thumbnails_cache")
        val freeBytes = cacheDir.parentFile?.freeSpace ?: (200L * 1024 * 1024)
        val cacheSizeBytes = (freeBytes * 0.10)
            .toLong()
            .coerceIn(50L * 1024 * 1024, 500L * 1024 * 1024)

        return ImageLoader.Builder(context)
            .components {
                add(MediaStoreThumbnailFetcher.Factory())
                add(VideoFrameDecoder.Factory())
            }
            // Disable hardware bitmaps so frames can be decoded safely on background threads
            // (WorkManager threads have no active GL context).
            // .allowHardware(false)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20) // Use 20% of app memory for in-memory LRU cache
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.toOkioPath())
                    .maxSizeBytes(cacheSizeBytes)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
