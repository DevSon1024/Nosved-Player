package com.devson.nvplayer.util.thumbnail

import android.graphics.Bitmap

sealed class VideoThumbnailResult {
    object Loading : VideoThumbnailResult()
    data class Success(val bitmap: Bitmap) : VideoThumbnailResult()
    data class Error(val throwable: Throwable) : VideoThumbnailResult()
}
