package com.devson.nvplayer.data.model

enum class StreamType {
    DIRECT_FILE,
    ADAPTIVE_MANIFEST,
    PLATFORM_URL,
    UNKNOWN
}

data class VideoQualityOption(
    val id: String,
    val label: String,
    val height: Int = 0,
    val width: Int = 0,
    val bitrate: Long = 0L,
    val fps: Int = 0,
    val isAuto: Boolean = false,
    val isSelected: Boolean = false,
    val isFixed: Boolean = false
)

data class StreamQualityState(
    val streamType: StreamType = StreamType.UNKNOWN,
    val isLoading: Boolean = false,
    val availableQualities: List<VideoQualityOption> = emptyList(),
    val currentQuality: VideoQualityOption? = null,
    val errorMessage: String? = null
)
