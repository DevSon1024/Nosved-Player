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
import com.devson.nosvedplayer.R
import androidx.compose.ui.res.stringResource
import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun formatSortField(field: SortField): String {
    return stringResource(getSortFieldStringRes(field))
}

fun formatDate(epochMs: Long): String {
    val df = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return df.format(Date(epochMs))
}

fun getSortFieldStringRes(field: SortField): Int {
    return when (field) {
        SortField.TITLE -> R.string.sort_field_title
        SortField.DATE -> R.string.sort_field_date
        SortField.PLAYED_TIME -> R.string.sort_field_played_time
        SortField.STATUS -> R.string.sort_field_status
        SortField.LENGTH -> R.string.sort_field_length
        SortField.SIZE -> R.string.sort_field_size
        SortField.RESOLUTION -> R.string.info_label_resolution
        SortField.PATH -> R.string.sort_field_path
        SortField.FRAME_RATE -> R.string.info_label_frame_rate
        SortField.TYPE -> R.string.info_label_type
    }
}

fun formatRelativeTime(context: Context, epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    return when {
        diff < 60_000L                  -> context.getString(R.string.relative_time_just_now)
        diff < 3_600_000L               -> context.getString(R.string.relative_time_m_ago, (diff / 60_000).toInt())
        diff < 86_400_000L              -> context.getString(R.string.relative_time_h_ago, (diff / 3_600_000).toInt())
        diff < 2_592_000_000L           -> context.getString(R.string.relative_time_days_ago, (diff / 86_400_000).toInt())
        diff < 31_536_000_000L          -> context.getString(R.string.relative_time_months_ago, (diff / 2_592_000_000).toInt())
        else                            -> context.getString(R.string.relative_time_years_ago, (diff / 31_536_000_000).toInt())
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
    onShare: () -> Unit,
    onMarkStatus: (String) -> Unit
) {
    var showTagDialog by remember { mutableStateOf(false) }

    if (showTagDialog) {
        TagStatusDialog(
            onDismiss = { showTagDialog = false },
            onConfirm = { status ->
                showTagDialog = false
                onMarkStatus(status)
            }
        )
    }

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
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play_all))
                Text(stringResource(R.string.action_play_all), fontSize = 10.sp)
            }

            // Move
            ActionColumn(icon = Icons.AutoMirrored.Filled.DriveFileMove, label = stringResource(R.string.action_move), onClick = onMove)
            // Copy
            ActionColumn(icon = Icons.Filled.ContentCopy, label = stringResource(R.string.action_copy), onClick = onCopy)
            // Delete
            ActionColumn(icon = Icons.Filled.Delete, label = stringResource(R.string.action_delete), onClick = onDelete)

            // Rename
            if (selectedFolders.size == 1) {
                ActionColumn(icon = Icons.Filled.DriveFileRenameOutline, label = stringResource(R.string.action_rename), onClick = onRename)
            }

            // Share
            ActionColumn(icon = Icons.Filled.Share, label = stringResource(R.string.action_share), onClick = onShare)

            // Info
            ActionColumn(icon = Icons.Filled.Info, label = stringResource(R.string.action_info).substringBefore(" "), onClick = onShowInfo)

            ActionColumn(icon = Icons.AutoMirrored.Filled.Label, label = stringResource(R.string.action_tag), onClick = { showTagDialog = true })
        }
    }
}

@Composable
fun TagStatusDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedStatus by remember { mutableStateOf("NEW") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_update_status)) },
        text = {
            Column {
                listOf(
                    "NEW" to stringResource(R.string.dialog_mark_as_new),
                    "RUNNING" to stringResource(R.string.dialog_mark_as_running),
                    "ENDED" to stringResource(R.string.dialog_mark_as_ended)
                ).forEach { (status, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStatus = status }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedStatus) }) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
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