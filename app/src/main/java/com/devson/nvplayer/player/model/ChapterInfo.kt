package com.devson.nvplayer.player.model

import androidx.compose.runtime.Immutable

@Immutable
data class ChapterInfo(
    val index: Int,
    val title: String,
    val timeMs: Long
)