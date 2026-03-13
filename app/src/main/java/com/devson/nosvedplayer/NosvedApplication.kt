package com.devson.nosvedplayer

import android.app.Application
import android.os.StatFs
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient

class NosvedApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        // Use 10% of available disk space for thumbnail cache, capped at 500 MB.
        val cacheDir = cacheDir.resolve("video_thumbnails_cache")
        val diskCacheSize = try {
            val stats = StatFs(cacheDir.absolutePath.also { cacheDir.mkdirs() })
            val available = stats.availableBytes
            // 10% of available space, min 50 MB, max 500 MB
            (available / 10L).coerceIn(50L * 1024 * 1024, 500L * 1024 * 1024)
        } catch (_: Exception) {
            100L * 1024 * 1024 // 100 MB fallback
        }

        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            // Memory cache: ~15% of heap to avoid OOM when scrolling lists
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            // Persistent disk cache for video thumbnails
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir)
                    .maxSizeBytes(diskCacheSize)
                    .build()
            }
            // Hardware bitmaps can crash in background rendering contexts (WorkManager).
            // Disabling globally is safest; the UI performance hit is negligible.
            .allowHardware(false)
            .crossfade(true)
            .build()
    }
}
