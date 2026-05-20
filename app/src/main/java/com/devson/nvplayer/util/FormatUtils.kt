package com.devson.nvplayer.util

import android.content.Context
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDate(timeMs: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(timeMs * 1000L)) // MediaStore date is usually in seconds
    } catch (e: Exception) {
        ""
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatRelativeTime(context: Context, timeMs: Long): String {
    return try {
        // MediaStore time is in seconds, convert to milliseconds if needed
        val ms = if (timeMs < 1000000000000L) timeMs * 1000L else timeMs
        DateUtils.getRelativeTimeSpanString(
            ms,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            0x00020 // DateUtils.FORMAT_ABBREVIATE_RELATIVE (sometimes hidden/deprecated in compile stub JARs)
        ).toString()
    } catch (e: Exception) {
        ""
    }
}

fun formatResolutionCompact(resolution: String?): String? {
    if (resolution.isNullOrBlank()) return null
    val parts = resolution.split("x")
    if (parts.size == 2) {
        val height = parts[1].toIntOrNull() ?: return resolution
        return "${height}p"
    }
    return resolution
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val groupIndex = digitGroups.coerceIn(0, units.lastIndex)
    return String.format(Locale.US, "%.2f %s", bytes / Math.pow(1024.0, groupIndex.toDouble()), units[groupIndex])
}

fun formatSortField(field: com.devson.nvplayer.model.SortField): String {
    return when (field) {
        com.devson.nvplayer.model.SortField.TITLE -> "Title"
        com.devson.nvplayer.model.SortField.DATE -> "Date Added"
        com.devson.nvplayer.model.SortField.PLAYED_TIME -> "Last Played"
        com.devson.nvplayer.model.SortField.STATUS -> "Progress Status"
        com.devson.nvplayer.model.SortField.LENGTH -> "Duration"
        com.devson.nvplayer.model.SortField.SIZE -> "File Size"
        com.devson.nvplayer.model.SortField.RESOLUTION -> "Resolution"
        com.devson.nvplayer.model.SortField.PATH -> "File Path"
        com.devson.nvplayer.model.SortField.FRAME_RATE -> "Frame Rate"
        com.devson.nvplayer.model.SortField.TYPE -> "File Type"
    }
}

