package com.devson.nvplayer.util

import android.content.Context
import android.text.format.DateUtils
import com.devson.nvplayer.domain.model.SortField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDate(timeMs: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val ms = if (timeMs.toString().length < 13) timeMs * 1000L else timeMs
        sdf.format(Date(ms))
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
        val ms = if (timeMs.toString().length < 13) timeMs * 1000L else timeMs
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

fun formatSortField(field: SortField): String {
    return when (field) {
        SortField.TITLE -> "Title"
        SortField.DATE -> "Date Added"
        SortField.PLAYED_TIME -> "Last Played"
        SortField.STATUS -> "Progress Status"
        SortField.LENGTH -> "Duration"
        SortField.SIZE -> "File Size"
        SortField.RESOLUTION -> "Resolution"
        SortField.PATH -> "File Path"
        SortField.FRAME_RATE -> "Frame Rate"
        SortField.TYPE -> "File Type"
    }
}

