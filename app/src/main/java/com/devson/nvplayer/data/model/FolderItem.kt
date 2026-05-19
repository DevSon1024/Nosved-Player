package com.devson.nvplayer.data.model

import android.net.Uri

data class FolderItem(
    val name: String,
    val videoCount: Int,
    val thumbnailUri: Uri?
)
