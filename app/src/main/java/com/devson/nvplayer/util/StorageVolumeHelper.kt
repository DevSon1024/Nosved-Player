package com.devson.nvplayer.util

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.devson.nvplayer.domain.model.StorageVolumeInfo
import java.io.File

/**
 * Queries [StorageManager] for all currently mounted volumes and maps them to
 * [StorageVolumeInfo] instances.  Returns at minimum the internal storage volume.
 */
fun getAvailableStorageVolumes(context: Context): List<StorageVolumeInfo> {
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        ?: return listOf(fallbackInternalVolume())

    val volumes = storageManager.storageVolumes
    val result = mutableListOf<StorageVolumeInfo>()

    for (volume in volumes) {
        if (!volume.state.equals(android.os.Environment.MEDIA_MOUNTED, ignoreCase = true)) continue

        val rootPath = resolveRootPath(volume, storageManager) ?: continue

        val isInternal = volume.isPrimary
        val name = if (isInternal) {
            "Internal Storage"
        } else {
            volume.getDescription(context) ?: "SD Card"
        }
        val id = if (isInternal) "internal" else (volume.uuid ?: rootPath)

        result.add(
            StorageVolumeInfo(
                id = id,
                name = name,
                rootPath = rootPath,
                isInternal = isInternal
            )
        )
    }

    // Guarantee at least the internal storage is present
    if (result.none { it.isInternal }) {
        result.add(0, fallbackInternalVolume())
    }

    // Put internal first
    return result.sortedByDescending { it.isInternal }
}

private fun resolveRootPath(volume: StorageVolume, storageManager: StorageManager): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        volume.directory?.absolutePath
    } else {
        // Reflection fallback for API < 30
        runCatching {
            val method = StorageVolume::class.java.getMethod("getPath")
            method.invoke(volume) as? String
        }.getOrNull()
    }
}

private fun fallbackInternalVolume(): StorageVolumeInfo {
    val path = android.os.Environment.getExternalStorageDirectory().absolutePath
    return StorageVolumeInfo(
        id = "internal",
        name = "Internal Storage",
        rootPath = path,
        isInternal = true
    )
}
