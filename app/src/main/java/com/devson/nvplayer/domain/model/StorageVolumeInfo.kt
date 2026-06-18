package com.devson.nvplayer.domain.model

/**
 * Represents a single mounted storage volume (internal or external SD card).
 */
data class StorageVolumeInfo(
    val id: String,
    val name: String,
    val rootPath: String,
    val isInternal: Boolean
)
