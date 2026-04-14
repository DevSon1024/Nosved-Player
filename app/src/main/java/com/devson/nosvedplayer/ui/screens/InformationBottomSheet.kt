package com.devson.nosvedplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devson.nosvedplayer.R
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
    
    val database = remember { NosvedDatabase.getInstance(context) }
    val watchHistoryDao = remember { database.watchHistoryDao() }
    val videoMetadataDao = remember { database.videoMetadataDao() }

    var metadataList by remember { mutableStateOf<List<DetailedVideoMetadata>>(emptyList()) }
    var isLoading by remember { mutableStateOf(selectedVideos.size == 1) }

    LaunchedEffect(selectedVideos) {
        if (selectedVideos.size == 1) {
            isLoading = true
            val meta = getVideoMetadata(context, selectedVideos.first(), watchHistoryDao, videoMetadataDao)
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
                text = stringResource(R.string.information),
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
    
    InfoSection(title = stringResource(R.string.info_general)) {
        InfoRow(label = stringResource(R.string.info_selected), value = stringResource(R.string.folder_videos_count, videos.size))
        InfoRow(label = stringResource(R.string.folder_info_total_size), value = formatSize(totalSize))
    }
}

@Composable
private fun SingleVideoInfo(metadata: DetailedVideoMetadata) {
    val video = metadata.video

    // Section 1: About File
    InfoSection(title = stringResource(R.string.info_about_file)) {
        InfoRow(label = stringResource(R.string.info_file_name), value = video.title)
        InfoRow(label = stringResource(R.string.folder_info_location), value = video.uri)
        InfoRow(label = stringResource(R.string.info_file_size), value = formatSize(video.size))
        InfoRow(label = stringResource(R.string.info_file_date), value = formatDate(video.dateAdded))
    }

    // Section 2: Media
    InfoSection(title = stringResource(R.string.info_media)) {
        InfoRow(label = stringResource(R.string.info_media_format), value = metadata.format)
        InfoRow(label = stringResource(R.string.info_media_resolution), value = metadata.resolution)
        InfoRow(label = stringResource(R.string.info_media_duration), value = formatDuration(video.duration))
        metadata.encodingSW?.let { InfoRow(label = stringResource(R.string.info_media_encoded_by), value = it) }
    }

    // Section 3: Playback History
    InfoSection(title = stringResource(R.string.info_playback_history)) {
        val history = metadata.history
        val playbackState = when {
            history == null -> stringResource(R.string.info_playback_not_played)
            history.lastPositionMs >= history.duration - 5000 -> stringResource(R.string.info_playback_finished)
            else -> stringResource(R.string.info_playback_not_finished)
        }
        InfoRow(label = stringResource(R.string.info_playback_state), value = playbackState)
        if (history != null) {
            InfoRow(label = stringResource(R.string.info_playback_last_played), value = formatDate(history.lastPlayedAt))
            InfoRow(label = stringResource(R.string.info_playback_position), value = formatDuration(history.lastPositionMs))
        }
    }

    // Section 4: Streams (Video, Audio, Subtitle)
    val videoTracks = metadata.tracks.filter { it.type == TrackType.VIDEO }
    val audioTracks = metadata.tracks.filter { it.type == TrackType.AUDIO }
    val subtitleTracks = metadata.tracks.filter { it.type == TrackType.SUBTITLE }
    val otherTracks = metadata.tracks.filter { it.type == TrackType.OTHER }

    if (videoTracks.isNotEmpty()) {
        videoTracks.forEachIndexed { index, track ->
            InfoSection(title = stringResource(R.string.info_track_video, index + 1)) {
                InfoRow(label = stringResource(R.string.info_track_codec), value = track.codec ?: stringResource(R.string.unknown))
                track.language?.let { InfoRow(label = stringResource(R.string.info_track_language), value = it) }
                track.extra.forEach { (key, value) ->
                    if (key != "Resolution") {
                        InfoRow(label = localizeMetadataKey(key), value = localizeMetadataValue(key, value))
                    }
                }
            }
        }
    }

    if (audioTracks.isNotEmpty()) {
        audioTracks.forEachIndexed { index, track ->
            InfoSection(title = stringResource(R.string.info_track_audio, index + 1)) {
                InfoRow(label = stringResource(R.string.info_track_codec), value = track.codec ?: stringResource(R.string.unknown))
                track.language?.let { InfoRow(label = stringResource(R.string.info_track_language), value = it) }
                track.extra.forEach { (key, value) ->
                    InfoRow(label = localizeMetadataKey(key), value = localizeMetadataValue(key, value))
                }
            }
        }
    }

    if (subtitleTracks.isNotEmpty()) {
        subtitleTracks.forEachIndexed { index, track ->
            InfoSection(title = stringResource(R.string.info_track_subtitle, index + 1)) {
                InfoRow(label = stringResource(R.string.info_media_format), value = track.codec ?: stringResource(R.string.unknown))
                track.language?.let { InfoRow(label = stringResource(R.string.info_track_language), value = it) }
                track.extra.forEach { (key, value) ->
                    InfoRow(label = localizeMetadataKey(key), value = localizeMetadataValue(key, value))
                }
            }
        }
    }

    if (otherTracks.isNotEmpty()) {
        otherTracks.forEachIndexed { index, track ->
            InfoSection(title = stringResource(R.string.info_track_other, index + 1)) {
                InfoRow(label = stringResource(R.string.info_track_type), value = track.codec ?: stringResource(R.string.unknown))
                track.language?.let { InfoRow(label = stringResource(R.string.info_track_language), value = it) }
                track.extra.forEach { (key, value) ->
                    InfoRow(label = localizeMetadataKey(key), value = localizeMetadataValue(key, value))
                }
            }
        }
    }
}

@Composable
private fun localizeMetadataKey(key: String): String {
    return when (key) {
        "Title" -> stringResource(R.string.info_label_title)
        "Resolution" -> stringResource(R.string.info_label_resolution)
        "Frame Rate" -> stringResource(R.string.info_label_frame_rate)
        "Sample Rate" -> stringResource(R.string.info_label_sample_rate)
        "Channels" -> stringResource(R.string.info_label_channels)
        "Bitrate" -> stringResource(R.string.info_label_bitrate)
        "Type" -> stringResource(R.string.info_label_type)
        "MIME" -> stringResource(R.string.info_label_mime)
        "Source" -> stringResource(R.string.info_label_source)
        "External" -> stringResource(R.string.info_label_external)
        else -> key
    }
}

@Composable
private fun localizeMetadataValue(key: String, value: String): String {
    return when {
        key == "Type" && value == "Forced" -> stringResource(R.string.info_value_forced)
        value == "External" -> stringResource(R.string.info_label_external)
        value == "External File" -> stringResource(R.string.info_value_external_file)
        else -> value
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
