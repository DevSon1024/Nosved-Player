package com.devson.nvplayer.util.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ThumbnailRepository(private val context: Context) {
    private val diskCache = ThumbnailDiskCache(context)
    private val activeJobs = mutableMapOf<ThumbnailKey, Deferred<Bitmap?>>()
    private val jobsMutex = Mutex()
    private val extractionDispatcher = Dispatchers.IO.limitedParallelism(1)

    suspend fun getThumbnail(key: ThumbnailKey, uri: Uri): Bitmap? {
        ThumbnailMemoryCache.get(key)?.let { return it }

        val diskBitmap = withContext(Dispatchers.IO) {
            diskCache.get(key)
        }
        diskBitmap?.let {
            ThumbnailMemoryCache.put(key, it)
            return it
        }

        val deferred = jobsMutex.withLock {
            activeJobs.getOrPut(key) {
                coroutineScope {
                    async(extractionDispatcher) {
                        val bitmap = generateThumbnail(key, uri)
                        if (bitmap != null) {
                            ThumbnailMemoryCache.put(key, bitmap)
                            diskCache.put(key, bitmap)
                        }
                        bitmap
                    }
                }
            }
        }

        return try {
            val bitmap = deferred.await()
            jobsMutex.withLock {
                activeJobs.remove(key)
            }
            bitmap
        } catch (e: Exception) {
            jobsMutex.withLock {
                activeJobs.remove(key)
            }
            null
        }
    }

    private fun generateThumbnail(key: ThumbnailKey, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
            try {
                return context.contentResolver.loadThumbnail(
                    uri,
                    Size(key.width, key.height),
                    null
                )
            } catch (e: Exception) {
                // query external metadata / fallback
            }
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            
            RepresentativeFrameSelector.selectFrame(retriever, durationMs, key.width, key.height)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
