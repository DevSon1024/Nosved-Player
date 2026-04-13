package com.devson.nosvedplayer.util

import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.devson.nosvedplayer.model.SortField
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nosvedplayer.model.applySort
import java.util.TimeZone
import kotlin.math.log10
import kotlin.math.pow


fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(sizeBytes.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", sizeBytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

fun formatDate(epochMs: Long): String {
    val df = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return df.format(Date(epochMs))
}

fun formatDuration(durationMs: Long): String {
    val df = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    df.timeZone = TimeZone.getTimeZone("UTC")
    return if (durationMs >= 3600000) {
        df.format(Date(durationMs))
    } else {
        val dfShort = SimpleDateFormat("mm:ss", Locale.getDefault())
        dfShort.timeZone = TimeZone.getTimeZone("UTC")
        dfShort.format(Date(durationMs))
    }
}

fun formatSortField(field: SortField): String {
    return when (field) {
        SortField.TITLE -> "Title"
        SortField.DATE -> "Date Added"
        SortField.PLAYED_TIME -> "Played Time"
        SortField.STATUS -> "Status"
        SortField.LENGTH -> "Length"
        SortField.SIZE -> "Size"
        SortField.RESOLUTION -> "Resolution"
        SortField.PATH -> "Path"
        SortField.FRAME_RATE -> "Frame Rate"
        SortField.TYPE -> "Type"
    }
}

fun formatRelativeTime(epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    return when {
        diff < 60_000L                  -> "Played Just Now"
        diff < 3_600_000L               -> "Played ${diff / 60_000}m Ago"
        diff < 86_400_000L              -> "Played ${diff / 3_600_000}h Ago"
        diff < 2_592_000_000L           -> "Played ${diff / 86_400_000} Days Ago"
        diff < 31_536_000_000L          -> "Played ${diff / 2_592_000_000} Months Ago"
        else                            -> "Played ${diff / 31_536_000_000} Years Ago"
    }
}

/**
 * Converts a "WIDTHxHEIGHT" resolution string (e.g. "1280x720") into a compact
 * "Xp" label (e.g. "720p") using the smaller dimension - works for both landscape
 * and portrait videos. Returns null if the string can't be parsed.
 */
fun formatResolutionCompact(resolution: String?): String? {
    if (resolution.isNullOrBlank()) return null
    val parts = resolution.split("x", "X", "×")
    if (parts.size != 2) return null
    val w = parts[0].trim().toIntOrNull() ?: return null
    val h = parts[1].trim().toIntOrNull() ?: return null
    return "${minOf(w, h)}p"
}



fun formatLogTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// Bottom App Bar Component
@Composable
fun SelectionBottomAppBar(
    selectedFolders: Set<VideoFolder>,
    videosByFolder: Map<VideoFolder, List<Video>>,
    viewSettings: ViewSettings,
    onVideoSelected: (Video, List<Video>) -> Unit,
    onClearSelection: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShowInfo: () -> Unit,
    onMarkNew: () -> Unit,
    onMarkEnded: () -> Unit
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play All
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable {
                        val sortedSelectedFolders = selectedFolders.sortedBy { it.name.lowercase() }
                        val allVideos = sortedSelectedFolders.flatMap { folder ->
                            (videosByFolder[folder] ?: emptyList()).applySort(viewSettings.sortField, viewSettings.sortDirection)
                        }
                        if (allVideos.isNotEmpty()) {
                            onVideoSelected(allVideos.first(), allVideos)
                        }
                        onClearSelection()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play All")
                Text("Play All", fontSize = 10.sp)
            }

            // Move
            ActionColumn(icon = Icons.AutoMirrored.Filled.DriveFileMove, label = "Move", onClick = onMove)
            // Copy
            ActionColumn(icon = Icons.Filled.ContentCopy, label = "Copy", onClick = onCopy)
            // Delete
            ActionColumn(icon = Icons.Filled.Delete, label = "Delete", onClick = onDelete)

            // Rename
            if (selectedFolders.size == 1) {
                ActionColumn(icon = Icons.Filled.DriveFileRenameOutline, label = "Rename", onClick = onRename)
            }

            // Info
            ActionColumn(icon = Icons.Filled.Info, label = "Info", onClick = onShowInfo)

            ActionColumn(icon = Icons.Filled.FiberNew, label = "Mark New", onClick = onMarkNew)
            ActionColumn(icon = Icons.Filled.DoneAll, label = "Mark Ended", onClick = onMarkEnded)
        }
    }
}

@Composable
private fun ActionColumn(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label)
        Text(label, fontSize = 10.sp)
    }
}