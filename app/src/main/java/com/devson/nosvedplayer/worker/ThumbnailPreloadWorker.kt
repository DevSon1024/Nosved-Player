package com.devson.nosvedplayer.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Size
import com.devson.nosvedplayer.repository.VideoRepository

/**
 * A background [CoroutineWorker] that pre-generates thumbnails for every local
 * video and writes them into Coil's persistent disk cache.
 *
 * Enqueue with [ExistingWorkPolicy.KEEP] so it runs once (or resumes if it was
 * interrupted) and never re-queues unnecessarily.
 */
class ThumbnailPreloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val tag = "ThumbnailPreloadWorker"

    override suspend fun doWork(): Result {
        Log.d(tag, "Starting thumbnail pre-load…")

        val repository = VideoRepository(applicationContext)
        val imageLoader = ImageLoader.Builder(applicationContext).build()

        val videos = try {
            repository.getAllVideos()
        } catch (e: Exception) {
            Log.e(tag, "Failed to query videos from MediaStore", e)
            return Result.failure()
        }

        Log.d(tag, "Pre-loading thumbnails for ${videos.size} video(s).")

        var successCount = 0
        var failCount = 0

        for (video in videos) {
            // Check if WorkManager has requested cancellation between each item.
            if (isStopped) {
                Log.d(tag, "Worker stopped early — will resume on next run.")
                break
            }

            val request = ImageRequest.Builder(applicationContext)
                .data(video.uri)
                // Grab a frame at 1 second to avoid black first-frames.
                .videoFrameMillis(1_000)
                // Downsample to 512×512 to keep the cache lean.
                .size(512, 512)
                // Do NOT pollute RAM — disk only.
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                // Disable hardware bitmaps for background safety.
                .allowHardware(false)
                .build()

            try {
                val result = imageLoader.execute(request)
                if (result.drawable != null) {
                    successCount++
                } else {
                    failCount++
                    Log.w(tag, "Null drawable for: ${video.uri}")
                }
            } catch (e: Exception) {
                failCount++
                Log.w(tag, "Failed to load thumbnail for: ${video.uri}", e)
            }
        }

        // Shut down the local ImageLoader to release its resources.
        imageLoader.shutdown()

        Log.d(tag, "Thumbnail pre-load complete. Success=$successCount, Failed=$failCount")
        return Result.success()
    }

    companion object {
        /** Unique name used to identify this work in WorkManager. */
        const val WORK_NAME = "thumbnail_preload_work"
    }
}
