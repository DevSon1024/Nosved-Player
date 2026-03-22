package com.devson.nosvedplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.devson.nosvedplayer.util.TrackType
import com.devson.nosvedplayer.util.formatDate
import com.devson.nosvedplayer.util.formatDuration
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Database access for history
    val database = remember { NosvedDatabase.getInstance(context) }
    val watchHistoryDao = remember { database.watchHistoryDao() }

    var metadataList by remember { mutableStateOf<List<DetailedVideoMetadata>>(emptyList()) }
    var isLoading by remember { mutableStateOf(selectedVideos.size == 1) }

    LaunchedEffect(selectedVideos) {
        if (selectedVideos.size == 1) {
            isLoading = true
            val meta = getVideoMetadata(context, selectedVideos.first(), watchHistoryDao)
            metadataList = listOf(meta)
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Information",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (selectedVideos.size > 1) {
                // Multi-selection Info
                MultiSelectionInfo(selectedVideos)
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (metadataList.isNotEmpty()) {
                // Single Video Info
                SingleVideoInfo(metadataList.first())
            }
        }
    }
}

@Composable
private fun MultiSelectionInfo(videos: Set<Video>) {
    val totalSize = videos.sumOf { it.size }
    
    InfoSection(title = "General") {
        InfoRow(label = "Selected", value = "${videos.size} videos")
        InfoRow(label = "Total Size", value = formatSize(totalSize))
    }
}

@Composable
private fun SingleVideoInfo(metadata: DetailedVideoMetadata) {
    val video = metadata.video

    // Section 1: About File
    InfoSection(title = "About File") {
        InfoRow(label = "Name", value = video.title)
        InfoRow(label = "Location", value = video.uri)
        InfoRow(label = "Size", value = formatSize(video.size))
        InfoRow(label = "Date", value = formatDate(video.dateAdded))
    }

    // Section 2: Media
    InfoSection(title = "Media") {
        InfoRow(label = "Format", value = metadata.format)
        InfoRow(label = "Resolution", value = metadata.resolution)
        InfoRow(label = "Duration", value = formatDuration(video.duration))
        metadata.encodingSW?.let { InfoRow(label = "Encoded by", value = it) }
    }

    // Section 3: Playback History
    InfoSection(title = "Playback History") {
        val history = metadata.history
        val playbackState = when {
            history == null -> "Not Played"
            history.lastPositionMs >= history.duration - 5000 -> "Finished"
            else -> "Not Finished"
        }
        InfoRow(label = "State", value = playbackState)
        if (history != null) {
            InfoRow(label = "Last Played", value = formatDate(history.lastPlayedAt))
            InfoRow(label = "Position", value = formatDuration(history.lastPositionMs))
        }
    }

    // Section 4: Streams (Video, Audio, Subtitle)
    val videoTracks = metadata.tracks.filter { it.type == TrackType.VIDEO }
    val audioTracks = metadata.tracks.filter { it.type == TrackType.AUDIO }
    val subtitleTracks = metadata.tracks.filter { it.type == TrackType.SUBTITLE }
    val otherTracks = metadata.tracks.filter { it.type == TrackType.OTHER }

    if (videoTracks.isNotEmpty()) {
        videoTracks.forEachIndexed { index, track ->
            InfoSection(title = "Video Track ${index + 1}") {
                InfoRow(label = "Codec", value = track.codec ?: "Unknown")
                track.language?.let { InfoRow(label = "Language", value = it) }
                track.extra.forEach { (key, value) ->
                    InfoRow(label = key, value = value)
                }
            }
        }
    }

    if (audioTracks.isNotEmpty()) {
        audioTracks.forEachIndexed { index, track ->
            InfoSection(title = "Audio Track ${index + 1}") {
                InfoRow(label = "Codec", value = track.codec ?: "Unknown")
                track.language?.let { InfoRow(label = "Language", value = it) }
                track.extra.forEach { (key, value) ->
                    if (key != "Resolution") { // Clean up potential mis-categorization
                        InfoRow(label = key, value = value)
                    }
                }
            }
        }
    }

    if (subtitleTracks.isNotEmpty()) {
        subtitleTracks.forEachIndexed { index, track ->
            InfoSection(title = "Subtitle Track ${index + 1}") {
                InfoRow(label = "Format", value = track.codec ?: "Unknown")
                track.language?.let { InfoRow(label = "Language", value = it) }
                track.extra.forEach { (key, value) ->
                    InfoRow(label = key, value = value)
                }
            }
        }
    }

    if (otherTracks.isNotEmpty()) {
        otherTracks.forEachIndexed { index, track ->
            InfoSection(title = "Other Track ${index + 1}") {
                InfoRow(label = "Type", value = track.codec ?: "Unknown")
                track.language?.let { InfoRow(label = "Language", value = it) }
                track.extra.forEach { (key, value) ->
                    InfoRow(label = key, value = value)
                }
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}
