package com.devson.nvplayer.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import com.devson.nvplayer.domain.model.StorageVolumeInfo
import java.io.File

/**
 * Queries [StorageManager] for all currently mounted volumes and maps them to
 * [StorageVolumeInfo] instances. Returns at minimum the internal storage volume.
 */
fun getAvailableStorageVolumes(context: Context): List<StorageVolumeInfo> {
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
    val result = mutableListOf<StorageVolumeInfo>()

    if (storageManager != null) {
        val volumes = storageManager.storageVolumes
        for (volume in volumes) {
            val isMounted = volume.state.equals(Environment.MEDIA_MOUNTED, ignoreCase = true) ||
                    volume.state.equals(Environment.MEDIA_MOUNTED_READ_ONLY, ignoreCase = true)
            if (!isMounted) continue

            val rootPath = resolveRootPath(volume, storageManager) ?: continue

            val isInternal = volume.isPrimary
            val name = if (isInternal) {
                "Internal Storage"
            } else {
                volume.getDescription(context) ?: "SD Card"
            }
            val id = if (isInternal) "internal" else (volume.uuid ?: rootPath)

            if (result.none { it.rootPath == rootPath }) {
                result.add(
                    StorageVolumeInfo(
                        id = id,
                        name = name,
                        rootPath = rootPath,
                        isInternal = isInternal
                    )
                )
            }
        }
    }

    // Fallback 1: context.getExternalFilesDirs
    try {
        val externalDirs = context.getExternalFilesDirs(null)
        for (dir in externalDirs) {
            if (dir != null) {
                val fullPath = dir.absolutePath
                if (fullPath.contains("/Android/data")) {
                    val rootPath = fullPath.substringBefore("/Android/data")
                    if (rootPath.isNotBlank() && File(rootPath).exists()) {
                        val isInternal = rootPath.contains("emulated/0") ||
                                rootPath == Environment.getExternalStorageDirectory().absolutePath
                        if (result.none { it.rootPath == rootPath }) {
                            val volumeName = if (isInternal) "Internal Storage" else "SD Card (${rootPath.substringAfterLast('/')})"
                            result.add(
                                StorageVolumeInfo(
                                    id = if (isInternal) "internal" else rootPath.substringAfterLast('/'),
                                    name = volumeName,
                                    rootPath = rootPath,
                                    isInternal = isInternal
                                )
                            )
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {}

    // Fallback 2: Direct /storage check for mounted removable volumes
    try {
        val storageRoot = File("/storage")
        if (storageRoot.exists() && storageRoot.isDirectory) {
            storageRoot.listFiles()?.forEach { file ->
                val name = file.name
                val ignored = setOf("emulated", "self", "knox", "container")
                if (!name.startsWith(".") && name !in ignored && file.isDirectory && file.canRead()) {
                    val rootPath = file.absolutePath
                    if (result.none { it.rootPath == rootPath }) {
                        result.add(
                            StorageVolumeInfo(
                                id = name,
                                name = "SD Card ($name)",
                                rootPath = rootPath,
                                isInternal = false
                            )
                        )
                    }
                }
            }
        }
    } catch (_: Exception) {}

    // Guarantee at least internal storage is present
    if (result.none { it.isInternal }) {
        result.add(0, fallbackInternalVolume())
    }

    // Put internal first
    return result.sortedByDescending { it.isInternal }
}

private fun resolveRootPath(volume: StorageVolume, storageManager: StorageManager): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        volume.directory?.absolutePath ?: runCatching {
            val method = StorageVolume::class.java.getMethod("getPath")
            method.invoke(volume) as? String
        }.getOrNull()
    } else {
        // Reflection fallback for API < 30
        runCatching {
            val method = StorageVolume::class.java.getMethod("getPath")
            method.invoke(volume) as? String
        }.getOrNull()
    }
}

private fun fallbackInternalVolume(): StorageVolumeInfo {
    val path = Environment.getExternalStorageDirectory().absolutePath
    return StorageVolumeInfo(
        id = "internal",
        name = "Internal Storage",
        rootPath = path,
        isInternal = true
    )
}
