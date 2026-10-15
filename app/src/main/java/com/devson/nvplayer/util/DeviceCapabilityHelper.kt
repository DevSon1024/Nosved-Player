package com.devson.nvplayer.util

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.ContextCompat

/**
 * Utility helper to determine device capabilities such as RAM tier to adjust
 * hardware decoding, caching, and playback pipelines accordingly.
 */
object DeviceCapabilityHelper {

    // Threshold for low-RAM classification: 3GB (allowing minor hardware kernel reserved margin)
    private const val LOW_RAM_THRESHOLD_BYTES = 3L * 1024L * 1024L * 1024L

    /**
     * Determines whether the current device is considered low-RAM (<= 3GB RAM or Android Go / low-RAM device).
     */
    fun isLowRamDevice(context: Context): Boolean {
        val activityManager = ContextCompat.getSystemService(context, ActivityManager::class.java)
            ?: return false

        if (activityManager.isLowRamDevice) {
            return true
        }

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem <= LOW_RAM_THRESHOLD_BYTES
    }

    /**
     * Returns total physical RAM in bytes reported by the kernel.
     */
    fun getTotalRamBytes(context: Context): Long {
        val activityManager = ContextCompat.getSystemService(context, ActivityManager::class.java)
            ?: return 0L
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem
    }
}
