package com.devson.nvplayer.util.thumbnail

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailLoader {
    private var repositoryInstance: ThumbnailRepository? = null

    fun getRepository(context: Context): ThumbnailRepository {
        return repositoryInstance ?: synchronized(this) {
            repositoryInstance ?: ThumbnailRepository(context.applicationContext).also {
                repositoryInstance = it
            }
        }
    }
}

@Composable
fun rememberVideoThumbnailState(
    uriString: String,
    size: Long,
    dateModified: Long,
    width: Int = 256,
    height: Int = 256
): State<VideoThumbnailResult> {
    val context = LocalContext.current
    val repository = remember { ThumbnailLoader.getRepository(context) }
    val uri = remember(uriString) { Uri.parse(uriString) }

    return produceState<VideoThumbnailResult>(initialValue = VideoThumbnailResult.Loading, keys = arrayOf(uriString, size, dateModified, width, height)) {
        value = VideoThumbnailResult.Loading
        
        try {
            val key = ThumbnailKey(
                uriString = uriString,
                lastModified = dateModified,
                fileSize = size,
                width = width,
                height = height
            )

            val bitmap = repository.getThumbnail(key, uri)
            value = if (bitmap != null) {
                VideoThumbnailResult.Success(bitmap)
            } else {
                VideoThumbnailResult.Error(Exception("Failed to generate thumbnail"))
            }
        } catch (e: Exception) {
            value = VideoThumbnailResult.Error(e)
        }
    }
}
