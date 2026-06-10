package com.devson.nvplayer.domain.thumbnail

import com.devson.nvplayer.domain.model.ThumbnailMode
import kotlin.math.roundToInt

sealed class ThumbnailStrategy {
    abstract val cacheKey: String

    data object FirstFrame : ThumbnailStrategy() {
        override val cacheKey: String = "first_frame"
    }

    data class FrameAtPercentage(
        val percentage: Float = 0.33f,
    ) : ThumbnailStrategy() {
        override val cacheKey: String = "frame_${percentage}"
    }

    data class Hybrid(
        val percentage: Float = 0.33f,
    ) : ThumbnailStrategy() {
        override val cacheKey: String = "hybrid_${percentage}"
    }

    data class EmbeddedOrHybrid(
        val percentage: Float = 0.33f,
    ) : ThumbnailStrategy() {
        override val cacheKey: String = "embedded_or_hybrid_${percentage}"
    }

    data object EmbeddedOrFirstFrame : ThumbnailStrategy() {
        override val cacheKey: String = "embedded_or_first_frame"
    }
}

fun ThumbnailMode.toThumbnailStrategy(framePositionPercent: Float): ThumbnailStrategy =
    when (this) {
        ThumbnailMode.SMART -> ThumbnailStrategy.EmbeddedOrHybrid(0.33f)
        ThumbnailMode.FIRST_FRAME -> ThumbnailStrategy.FirstFrame
        ThumbnailMode.FRAME_AT_POSITION ->
            ThumbnailStrategy.FrameAtPercentage((framePositionPercent / 100f).coerceIn(0f, 1f))
        ThumbnailMode.EMBEDDED_THUMBNAIL -> ThumbnailStrategy.EmbeddedOrFirstFrame
    }

internal fun ThumbnailStrategy.prefersEmbeddedPicture(): Boolean =
    this is ThumbnailStrategy.EmbeddedOrFirstFrame || this is ThumbnailStrategy.EmbeddedOrHybrid

fun ThumbnailMode.thumbnailModeCacheKey(framePositionPercent: Float): String =
    when (this) {
        ThumbnailMode.SMART -> "Smart_embedded_v2"
        ThumbnailMode.FRAME_AT_POSITION ->
            "FrameAtPosition_${framePositionPercent.coerceIn(0f, 100f).roundToInt()}"
        ThumbnailMode.EMBEDDED_THUMBNAIL -> "EmbeddedThumbnail_v2"
        else -> name
    }
