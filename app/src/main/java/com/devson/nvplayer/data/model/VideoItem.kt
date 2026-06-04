package com.devson.nvplayer.data.model

import android.net.Uri

data class VideoItem(
    val uri: Uri,
    val title: String,
    val duration: Long,
    val folderName: String,
    val path: String,
    val thumbnailUri: Uri?,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateModified: Long = 0L
)
