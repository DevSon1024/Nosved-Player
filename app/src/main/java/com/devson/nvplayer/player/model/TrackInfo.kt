package com.devson.nvplayer.player.model

import androidx.compose.runtime.Immutable

@Immutable
data class TrackInfo(
    val id: Int,          // Native track ID from MPV
    val type: String,     // "sub" or "audio" or "video"
    val name: String,     // Friendly display name
    val selected: Boolean,
    val isExternal: Boolean = false,
    val lang: String = "", // Raw ISO 639 language code from MPV (e.g. "eng", "en", "hin")
    val codec: String = "" // Codec name from MPV (e.g. "subrip", "ass", "pgs")
)