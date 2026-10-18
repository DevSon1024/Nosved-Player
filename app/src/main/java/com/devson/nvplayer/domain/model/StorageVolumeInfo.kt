package com.devson.nvplayer.domain.model

import androidx.compose.runtime.Immutable
 
/**
 * Represents a single mounted storage volume (internal or external SD card).
 */
@Immutable
data class StorageVolumeInfo(
    val id: String,
    val name: String,
    val rootPath: String,
    val isInternal: Boolean
)
