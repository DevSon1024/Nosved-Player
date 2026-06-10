package com.devson.nvplayer.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class FolderItem(
    val name: String,
    val path: String,
    val videoCount: Int,
    val thumbnailUri: Uri?
)
