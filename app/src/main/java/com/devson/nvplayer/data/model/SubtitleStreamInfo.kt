package com.devson.nvplayer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class SubtitleStreamInfo(
    val index: Int,
    val trackId: Int,
    val title: String,
    val language: String,
    val codecName: String,
    val extension: String
) {
    val isImageBased: Boolean
        get() {
            val lowerCodec = codecName.lowercase()
            return lowerCodec.contains("pgs") ||
                   lowerCodec.contains("vobsub") ||
                   lowerCodec.contains("dvd") ||
                   lowerCodec.contains("bitmap") ||
                   lowerCodec.contains("image") ||
                   lowerCodec.contains("dvb")
        }

    val isExtractable: Boolean
        get() = !isImageBased && extension.isNotBlank()
}
