package com.devson.nvplayer.domain.model

enum class ThumbnailMode {
    SMART,
    FIRST_FRAME,
    FRAME_AT_POSITION,
    EMBEDDED_THUMBNAIL;

    val displayName: String
        get() = when (this) {
            SMART -> "Smart (embedded + 33%)"
            FIRST_FRAME -> "First frame"
            FRAME_AT_POSITION -> "Frame position"
            EMBEDDED_THUMBNAIL -> "Embedded thumbnail"
        }
}
