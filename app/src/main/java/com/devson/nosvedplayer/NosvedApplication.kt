package com.devson.nosvedplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okio.Path.Companion.toOkioPath

class NosvedApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        // Determine a sensible disk cache size:  10% of free space, clamped between 50 MB and 500 MB.
        val cacheDir = cacheDir.resolve("video_thumbnails_cache")
        val freeBytes = cacheDir.parentFile?.freeSpace ?: (200L * 1024 * 1024)
        val cacheSizeBytes = (freeBytes * 0.10)
            .toLong()
            .coerceIn(50L * 1024 * 1024, 500L * 1024 * 1024)

        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            // Disable hardware bitmaps so frames can be decoded safely on background threads
            // (WorkManager threads have no active GL context).
            // .allowHardware(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20) // Use 20% of app memory for in-memory LRU cache
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
