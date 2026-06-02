package com.devson.nvplayer.util.thumbnail

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build

object RepresentativeFrameSelector {
    fun selectFrame(retriever: MediaMetadataRetriever, durationMs: Long, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (durationMs <= 0) {
            return extractScaledFrame(retriever, 0, targetWidth, targetHeight)
        }

        val percentages = listOf(0.10, 0.25, 0.40)
        
        for (percent in percentages) {
            val timeMs = (durationMs * percent).toLong()
            val bitmap = extractScaledFrame(retriever, timeMs, targetWidth, targetHeight)
            if (bitmap != null && !isBlackOrBlank(bitmap)) {
                return bitmap
            }
        }

        return extractScaledFrame(retriever, 0, targetWidth, targetHeight)
    }

    private fun extractScaledFrame(
        retriever: MediaMetadataRetriever,
        timeMs: Long,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val timeUs = timeMs * 1000
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isBlackOrBlank(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val stepX = (width / 5).coerceAtLeast(1)
        val stepY = (height / 5).coerceAtLeast(1)
        
        var totalBrightness = 0.0
        var sampleCount = 0

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val color = bitmap.getPixel(x, y)
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                
                val luminance = 0.299 * r + 0.587 * g + 0.114 * b
                totalBrightness += luminance
                sampleCount++
            }
        }

        val averageBrightness = totalBrightness / sampleCount
        return averageBrightness < 15.0
    }
}
