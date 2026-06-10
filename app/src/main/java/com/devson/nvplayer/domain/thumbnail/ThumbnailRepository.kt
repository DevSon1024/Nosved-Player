package com.devson.nvplayer.domain.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.devson.nvplayer.data.repository.ViewSettingsRepository
import com.devson.nvplayer.domain.model.Video
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

class ThumbnailRepository private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: ThumbnailRepository? = null

        fun getInstance(context: Context): ThumbnailRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThumbnailRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val viewSettingsRepo by lazy {
        ViewSettingsRepository.getInstance(context)
    }

    private val imageLoader: ImageLoader by lazy {
        SingletonImageLoader.get(context)
    }

    private val memoryCache: LruCache<String, Bitmap>
    private val ongoingOperations = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val maxConcurrentFolders = 3
    private val generationSemaphore = Semaphore(1)
    private val maxFolderBatchSize = 48
    private val generationFrameDelayMs = 12L

    private data class FolderState(
        val signature: String,
        @Volatile var nextIndex: Int = 0,
    )

    private val folderStates = ConcurrentHashMap<String, FolderState>()
    private val folderJobs = ConcurrentHashMap<String, Job>()

    private val _thumbnailReadyKeys =
        MutableSharedFlow<String>(
            extraBufferCapacity = 256,
        )
    val thumbnailReadyKeys: SharedFlow<String> = _thumbnailReadyKeys.asSharedFlow()

    init {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        val cacheSizeKb = maxMemoryKb / 6
        memoryCache =
            object : LruCache<String, Bitmap>(cacheSizeKb) {
                override fun sizeOf(
                    key: String,
                    value: Bitmap,
                ): Int = value.byteCount / 1024
            }
    }

    suspend fun getThumbnail(
        video: Video,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = thumbnailKey(video, widthPx, heightPx)

            synchronized(memoryCache) {
                memoryCache.get(key)
            }?.let { return@withContext it }

            ongoingOperations[key]?.let { return@withContext it.await() }

            val deferred =
                async {
                    try {
                        getCachedThumbnail(video, widthPx, heightPx)?.let { cached ->
                            synchronized(memoryCache) {
                                memoryCache.put(key, cached)
                            }
                            _thumbnailReadyKeys.tryEmit(key)
                            return@async cached
                        }

                        val bitmap = generationSemaphore.withPermit {
                            val result =
                                runCatching {
                                    imageLoader.execute(buildRequest(video))
                                }.getOrNull() as? SuccessResult ?: return@withPermit null

                            scaleBitmap(result.image.toBitmap(), widthPx, heightPx)
                        } ?: return@async null

                        synchronized(memoryCache) {
                            memoryCache.put(key, bitmap)
                        }
                        _thumbnailReadyKeys.tryEmit(key)
                        bitmap
                    } finally {
                        ongoingOperations.remove(key)
                    }
                }

            ongoingOperations[key] = deferred
            deferred.await()
        }

    suspend fun getCachedThumbnail(
        video: Video,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = thumbnailKey(video, widthPx, heightPx)
            synchronized(memoryCache) {
                memoryCache.get(key)
            }?.let { return@withContext it }

            val snapshot = imageLoader.diskCache?.openSnapshot(diskCacheKey(video)) ?: return@withContext null
            snapshot.use {
                val file = it.data.toFile()
                val decoded =
                    runCatching {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, options)
                        val sampleSize = calculateThumbnailSampleSize(options.outWidth, options.outHeight)
                        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.RGB_565
                        })
                    }.getOrNull() ?: return@withContext null

                val scaled = scaleBitmap(decoded, widthPx, heightPx)
                synchronized(memoryCache) {
                    memoryCache.put(key, scaled)
                }
                return@withContext scaled
            }
        }

    fun getThumbnailFromMemory(
        video: Video,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap? {
        val key = thumbnailKey(video, widthPx, heightPx)
        return synchronized(memoryCache) {
            memoryCache.get(key)
        }
    }

    fun clearThumbnailCache() {
        folderJobs.values.forEach { it.cancel() }
        folderJobs.clear()
        folderStates.clear()
        ongoingOperations.clear()

        synchronized(memoryCache) {
            memoryCache.evictAll()
        }

        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        runCatching { File(context.cacheDir, "video_thumbnails_cache").deleteRecursively() }
        runCatching { File(context.filesDir, "video_thumbnails_cache").deleteRecursively() }
    }

    fun startFolderThumbnailGeneration(
        folderId: String,
        videos: List<Video>,
        widthPx: Int,
        heightPx: Int,
    ) {
        val filteredVideos = videos.take(maxFolderBatchSize)

        if (filteredVideos.isEmpty()) {
            return
        }

        folderJobs.entries.removeAll { !it.value.isActive }

        if (folderJobs.size >= maxConcurrentFolders && !folderJobs.containsKey(folderId)) {
            folderJobs.entries.firstOrNull()?.let { (oldestId, job) ->
                job.cancel()
                folderJobs.remove(oldestId)
                folderStates.remove(oldestId)
            }
        }

        val signature = folderSignature(filteredVideos, widthPx, heightPx)
        val existingState = folderStates[folderId]
        val shouldRestart = existingState == null || existingState.signature != signature

        val state =
            folderStates.compute(folderId) { _, existing ->
                if (existing == null || existing.signature != signature) {
                    FolderState(signature = signature, nextIndex = 0)
                } else {
                    existing
                }
            }!!

        if (shouldRestart) {
            folderJobs.remove(folderId)?.cancel()
            folderJobs[folderId] =
                repositoryScope.launch {
                    var i = state.nextIndex
                    while (i < filteredVideos.size) {
                        getThumbnail(filteredVideos[i], widthPx, heightPx)
                        i++
                        state.nextIndex = i
                        delay(generationFrameDelayMs)
                    }
                }
        }
    }

    fun thumbnailKey(
        video: Video,
        width: Int,
        height: Int,
    ): String = "${videoBaseKey(video)}|$width|$height|${thumbnailModeKey()}"

    fun diskCacheKey(video: Video): String = "video-thumb|${videoBaseKey(video)}|${thumbnailModeKey()}"

    private fun videoBaseKey(video: Video): String {
        val artworkSignature =
            EmbeddedArtworkCandidates.forVideoPath(video.path)
                .asSequence()
                .map(::File)
                .firstOrNull { it.isFile && it.canRead() }
                ?.let { artwork -> "|art:${artwork.name}:${artwork.length()}:${artwork.lastModified()}" }
                .orEmpty()

        return "${video.size}|${video.dateModified}|${video.duration}$artworkSignature"
    }

    private fun buildRequest(video: Video): ImageRequest =
        ImageRequest.Builder(context)
            .data(requestData(video))
            .memoryCacheKey(diskCacheKey(video))
            .diskCacheKey(diskCacheKey(video))
            .build()

    private fun requestData(video: Video): Any {
        val uri = android.net.Uri.parse(video.uri)
        return when {
            uri.scheme == "content" || uri.scheme == "file" -> uri
            video.path.isNotBlank() -> File(video.path)
            else -> uri
        }
    }

    private fun scaleBitmap(
        bitmap: Bitmap,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap {
        if (widthPx <= 0 || heightPx <= 0 || bitmap.isRecycled) {
            return bitmap
        }

        val scale = max(widthPx / bitmap.width.toFloat(), heightPx / bitmap.height.toFloat())
        if (scale >= 1f && bitmap.width <= widthPx * 2 && bitmap.height <= heightPx * 2) {
            return bitmap
        }

        val scaledWidth = max(1, (bitmap.width * scale).roundToInt())
        val scaledHeight = max(1, (bitmap.height * scale).roundToInt())
        val scaled = try {
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } catch (_: IllegalArgumentException) {
            return bitmap
        }
        if (scaled != bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return scaled
    }

    suspend fun getFolderThumbnail(
        folderId: String,
        videos: List<Video>,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext null
        getThumbnail(videos.first(), widthPx, heightPx)
    }

    private fun folderSignature(
        videos: List<Video>,
        widthPx: Int,
        heightPx: Int,
    ): String {
        val md = MessageDigest.getInstance("MD5")
        md.update("$widthPx|$heightPx|${thumbnailModeKey()}|".toByteArray())
        for (video in videos) {
            md.update(video.path.toByteArray())
            md.update("|".toByteArray())
            md.update(video.size.toString().toByteArray())
            md.update("|".toByteArray())
            md.update(video.dateModified.toString().toByteArray())
            md.update(";".toByteArray())
        }
        return md.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun thumbnailModeKey(): String =
        viewSettingsRepo.viewSettingsFlow.value.thumbnailMode.thumbnailModeCacheKey(
            viewSettingsRepo.viewSettingsFlow.value.thumbnailFramePosition
        )
}
