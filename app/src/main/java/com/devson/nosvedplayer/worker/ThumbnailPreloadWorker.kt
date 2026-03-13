package com.devson.nosvedplayer.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.devson.nosvedplayer.repository.VideoRepository

/**
 * A one-shot background worker that pre-generates and caches thumbnails for every
 * local video found by [VideoRepository]. Thumbnails are written exclusively to the
 * Coil disk cache so they can be served instantly on the next app launch without
 * re-decoding any video frames.
 *
 * Enqueue with [ExistingWorkPolicy.KEEP] so the job is never duplicated.
 */
class ThumbnailPreloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "thumbnail_preload"
        private const val TAG = "ThumbnailPreloadWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting thumbnail pre-generation…")

        val imageLoader = ImageLoader.Builder(applicationContext).build()
        val repository = VideoRepository(applicationContext)

        val videos = try {
            repository.getAllVideos()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query video list", e)
            return Result.failure()
        }

        Log.d(TAG, "Preloading thumbnails for ${videos.size} videos")

        videos.forEach { video ->
            try {
                val request = ImageRequest.Builder(applicationContext)
                    .data(video.uri)
                    .videoFrameMillis(1_000L)       // Grab frame at 1-second mark
                    .size(512, 512)                 // Downsample — keeps cache footprint small
                    .memoryCachePolicy(CachePolicy.DISABLED)   // Don't pollute RAM during background work
                    .diskCachePolicy(CachePolicy.ENABLED)      // Write to (and read from) disk cache only
                    .allowHardware(false)           // Background threads have no GL context
                    .build()

                imageLoader.execute(request)
                Log.v(TAG, "Cached thumbnail: ${video.title}")
            } catch (e: Exception) {
                // One bad file must not abort the entire batch — log and continue
                Log.w(TAG, "Skipping thumbnail for '${video.title}': ${e.message}")
            }
        }

        Log.d(TAG, "Thumbnail pre-generation complete (${videos.size} processed)")
        return Result.success()
    }
}
