package com.devson.nosvedplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devson.nosvedplayer.data.NosvedDatabase
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.util.DetailedVideoMetadata
import com.devson.nosvedplayer.util.TrackMetadata
import com.devson.nosvedplayer.util.TrackType
import com.devson.nosvedplayer.util.formatDate
import com.devson.nosvedplayer.util.formatDuration
import com.devson.nosvedplayer.util.formatLogTime
import com.devson.nosvedplayer.util.formatSize
import com.devson.nosvedplayer.util.getVideoMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformationBottomSheet(
    selectedVideos: Set<Video>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { NosvedDatabase.getInstance(context) }
    val dao = remember { database.watchHistoryDao() }
    
    var metadata by remember { mutableStateOf<DetailedVideoMetadata?>(null) }
    var isLoading by remember { mutableStateOf(selectedVideos.size == 1) }

    LaunchedEffect(selectedVideos) {
        if (selectedVideos.size == 1) {
            isLoading = true
            metadata = getVideoMetadata(context, selectedVideos.first(), dao)
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Information",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (selectedVideos.size > 1) {
                AggregatedStats(selectedVideos)
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                metadata?.let { data ->
                    // Section 1: About File
                    InfoSection(title = "About File") {
                        val path = data.video.path
                        val name = path.substringAfterLast('/', data.video.title)
                        val location = path.substringBeforeLast('/', "Unknown")
                        val hasSubtitles = data.tracks.any { it.type == TrackType.SUBTITLE }
                        
                        InfoRow(label = "File Name", value = name)
                        InfoRow(label = "Location", value = location)
                        InfoRow(label = "Size", value = formatSize(data.video.size))
                        InfoRow(label = "Date", value = formatDate(data.video.dateAdded))
                        InfoRow(label = "Subtitle", value = if (hasSubtitles) "Embedded" else "External/None")
                    }

                    // Section 2: Media
                    InfoSection(title = "Media") {
                        InfoRow(label = "Title", value = data.video.title)
                        InfoRow(label = "Format", value = data.format)
                        InfoRow(label = "Resolution", value = data.resolution)
                        InfoRow(label = "Length", value = formatDuration(data.video.duration))
                        data.encodingSW?.let { InfoRow(label = "Encoding SW", value = it) }
                    }

                    // Section 3: Playback History
                    InfoSection(title = "Playback History") {
                        val history = data.history
                        val progress = if (history != null && data.video.duration > 0) {
                            (history.lastPositionMs.toFloat() / data.video.duration.toFloat()) * 100
                        } else 0f
                        
                        val state = when {
                            history == null -> "Not Played"
                            progress >= 95 -> "Finished"
                            else -> "Not Finished"
                        }

                        InfoRow(label = "Finished State", value = state)
                        if (history != null) {
                            InfoRow(label = "Last Playback", value = formatDate(history.lastPlayedAt) + " " + formatLogTime(
                                history.lastPlayedAt
                            ).substringAfter(','))
                            InfoRow(label = "Last Position", value = formatDuration(history.lastPositionMs))
                        }
                    }

                    // Section 4: Streams
                    InfoSection(title = "Streams") {
                        data.tracks.forEachIndexed { index, track ->
                            StreamItem(index + 1, track)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AggregatedStats(selectedVideos: Set<Video>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow(label = "Total Videos", value = "${selectedVideos.size}")
            InfoRow(label = "Total Size", value = formatSize(selectedVideos.sumOf { it.size }))
        }
    }
}

@Composable
fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.5f)
        )
    }
}

@Composable
fun StreamItem(number: Int, track: TrackMetadata) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Stream $number",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
        InfoRow(label = "Type", value = track.type.name.lowercase().replaceFirstChar { it.uppercase() })
        track.codec?.let { InfoRow(label = "Codec", value = it.uppercase()) }
        track.language?.let { InfoRow(label = "Language", value = it) }
        track.extra.forEach { (k, v) ->
            InfoRow(label = k, value = v)
        }
        if (number < 10) { // arbitrary limit or just use Divider
             HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
