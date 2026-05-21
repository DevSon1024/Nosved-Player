package com.devson.nvplayer.player

data class TrackInfo(
    val id: Int,      // Native track ID from MPV
    val type: String,  // "sub" or "audio" or "video"
    val name: String,  // Friendly display name
    val selected: Boolean
)
