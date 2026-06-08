package com.devson.nvplayer.model

import androidx.compose.runtime.Immutable

/**
 * Represents a distinct folder mapped via MediaStore.
 */
@Immutable
data class VideoFolder(
    val id: String,
    val name: String,
    // val videoCount: Int
)
