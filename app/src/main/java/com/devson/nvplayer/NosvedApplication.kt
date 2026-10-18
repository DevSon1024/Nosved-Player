package com.devson.nvplayer

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.video.VideoFrameDecoder
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.devson.nvplayer.player.engine.MPVPlayerEngine
import com.devson.nvplayer.util.DeviceCapabilityHelper
import com.devson.nvplayer.util.MediaStoreThumbnailFetcher
import com.devson.nvplayer.data.repository.ViewSettingsRepository
import com.devson.nvplayer.domain.thumbnail.CoilVideoThumbnailDecoder
import com.devson.nvplayer.domain.thumbnail.toThumbnailStrategy
import okio.Path.Companion.toOkioPath

class NosvedApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cacheDir = cacheDir.resolve("video_thumbnails_cache")
        val cacheSizeBytes = 250L * 1024 * 1024 // 250 MB fast dedicated thumbnail cache

        val viewSettingsRepo = ViewSettingsRepository.getInstance(context)
        val isLowRam = DeviceCapabilityHelper.isLowRamDevice(context)
        val thumbnailDispatcher = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(if (isLowRam) 2 else 4)

        return ImageLoader.Builder(context)
            .coroutineContext(thumbnailDispatcher)
            .components {
                add(
                    CoilVideoThumbnailDecoder.Factory(
                        thumbnailStrategy = {
                            val settings = viewSettingsRepo.viewSettingsFlow.value
                            settings.thumbnailMode.toThumbnailStrategy(settings.thumbnailFramePosition)
                        }
                    )
                )
                add(MediaStoreThumbnailFetcher.Factory())
                add(MediaStoreThumbnailFetcher.StringFactory())
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, if (isLowRam) 0.10 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.toOkioPath())
                    .maxSizeBytes(cacheSizeBytes)
                    .build()
            }
            .crossfade(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
            level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            Log.w("NosvedApplication", "onTrimMemory received: level=$level. Flushing MPV and image caches.")
            try {
                SingletonImageLoader.get(this).memoryCache?.clear()
            } catch (e: Exception) {
                Log.w("NosvedApplication", "Failed to clear coil memory cache: ${e.message}")
            }
            MPVPlayerEngine.activeInstance?.trimMemory(level)
        }
    }
}
