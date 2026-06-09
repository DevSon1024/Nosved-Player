package com.devson.nvplayer

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.video.VideoFrameDecoder
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.devson.nvplayer.util.MediaStoreThumbnailFetcher
import com.devson.nvplayer.data.repository.ViewSettingsRepository
import com.devson.nvplayer.domain.thumbnail.CoilVideoThumbnailDecoder
import com.devson.nvplayer.domain.thumbnail.toThumbnailStrategy
import okio.Path.Companion.toOkioPath

class NosvedApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cacheDir = cacheDir.resolve("video_thumbnails_cache")
        val freeBytes = cacheDir.parentFile?.freeSpace ?: (200L * 1024 * 1024)
        val cacheSizeBytes = (freeBytes * 0.10)
            .toLong()
            .coerceIn(50L * 1024 * 1024, 500L * 1024 * 1024)

        val viewSettingsRepo = ViewSettingsRepository.getInstance(context)

        return ImageLoader.Builder(context)
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
                    .maxSizePercent(context, 0.20)
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
