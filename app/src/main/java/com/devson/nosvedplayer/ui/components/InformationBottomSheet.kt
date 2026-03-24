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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * @param useSideSheet When true the sheet slides in from the right edge and takes ~half
 *                     the screen width (used inside the video player). When false it
 *                     shows as a regular ModalBottomSheet (used in the video list screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformationBottomSheet(
    selectedVideos: Set<Video>,
    onDismiss: () -> Unit,
    useSideSheet: Boolean = false
) {
    val context = LocalContext.current
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

    if (useSideSheet) {
        // ── Side panel (video player context) ──────────────────────────────────
        SideSheet(visible = true, onDismissRequest = onDismiss) {
            InfoSheetBody(
                selectedVideos = selectedVideos,
                metadata = metadata,
                isLoading = isLoading,
                compact = true
            )
        }
    } else {
        // ── Regular bottom sheet (video list context) ───────────────────────────
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            InfoSheetBody(
                selectedVideos = selectedVideos,
                metadata = metadata,
                isLoading = isLoading,
                compact = false
            )
        }
    }
}

// ─── Shared body ─────────────────────────────────────────────────────────────

@Composable
private fun InfoSheetBody(
    selectedVideos: Set<Video>,
    metadata: DetailedVideoMetadata?,
    isLoading: Boolean,
    compact: Boolean
) {
    // Font scale for side-panel (compact) mode
    val titleSp: TextUnit = if (compact) 14.sp else 22.sp
    val sectionSp: TextUnit = if (compact) 11.sp else 16.sp
    val labelSp: TextUnit = if (compact) 11.sp else 14.sp
    val valueSp: TextUnit = if (compact) 11.sp else 14.sp
    val streamLabelSp: TextUnit = if (compact) 10.sp else 13.sp
    val hPad = if (compact) 14.dp else 20.dp
    val vertPad = if (compact) 3.dp else 4.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = hPad)
            .verticalScroll(rememberScrollState())
            .padding(bottom = if (compact) 20.dp else 32.dp)
    ) {
        Text(
            text = "Information",
            fontWeight = FontWeight.Bold,
            fontSize = titleSp,
            modifier = Modifier.padding(bottom = if (compact) 12.dp else 16.dp)
        )

        if (selectedVideos.size > 1) {
            AggregatedStats(selectedVideos)
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 100.dp else 200.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(modifier = Modifier.size(if (compact) 28.dp else 40.dp)) }
        } else {
            metadata?.let { data ->
                // Section 1: About File
                CompactInfoSection(title = "About File", sectionSp = sectionSp, compact = compact) {
                    val path = data.video.path
                    val name = path.substringAfterLast('/', data.video.title)
                    val location = path.substringBeforeLast('/', "Unknown")
                    val hasSubtitles = data.tracks.any { it.type == TrackType.SUBTITLE }

                    CompactInfoRow("File Name", name, labelSp, valueSp, vertPad)
                    CompactInfoRow("Location", location, labelSp, valueSp, vertPad)
                    CompactInfoRow("Size", formatSize(data.video.size), labelSp, valueSp, vertPad)
                    CompactInfoRow("Date", formatDate(data.video.dateAdded), labelSp, valueSp, vertPad)
                    CompactInfoRow("Subtitle", if (hasSubtitles) "Embedded" else "None", labelSp, valueSp, vertPad)
                }

                // Section 2: Media
                CompactInfoSection(title = "Media", sectionSp = sectionSp, compact = compact) {
                    CompactInfoRow("Title", data.video.title, labelSp, valueSp, vertPad)
                    CompactInfoRow("Format", data.format, labelSp, valueSp, vertPad)
                    CompactInfoRow("Resolution", data.resolution, labelSp, valueSp, vertPad)
                    CompactInfoRow("Length", formatDuration(data.video.duration), labelSp, valueSp, vertPad)
                    data.encodingSW?.let { CompactInfoRow("Encoding SW", it, labelSp, valueSp, vertPad) }
                }

                // Section 3: Playback History
                CompactInfoSection(title = "Playback History", sectionSp = sectionSp, compact = compact) {
                    val history = data.history
                    val progress = if (history != null && data.video.duration > 0) {
                        (history.lastPositionMs.toFloat() / data.video.duration.toFloat()) * 100
                    } else 0f
                    val state = when {
                        history == null -> "Not Played"
                        progress >= 95  -> "Finished"
                        else            -> "In Progress"
                    }
                    CompactInfoRow("State", state, labelSp, valueSp, vertPad)
                    if (history != null) {
                        CompactInfoRow(
                            "Last Played",
                            formatDate(history.lastPlayedAt) + " " +
                                    formatLogTime(history.lastPlayedAt).substringAfter(','),
                            labelSp, valueSp, vertPad
                        )
                        CompactInfoRow("Position", formatDuration(history.lastPositionMs), labelSp, valueSp, vertPad)
                    }
                }

                // Section 4: Streams
                CompactInfoSection(title = "Streams", sectionSp = sectionSp, compact = compact) {
                    data.tracks.forEachIndexed { index, track ->
                        CompactStreamItem(index + 1, track, streamLabelSp, labelSp, valueSp, vertPad, compact)
                    }
                }
            }
        }
    }
}

// ─── Composable helpers ───────────────────────────────────────────────────────

@Composable
private fun CompactInfoSection(
    title: String,
    sectionSp: TextUnit,
    compact: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val vPad = if (compact) 6.dp else 8.dp
    val cardPad = if (compact) 10.dp else 16.dp

    Column(modifier = Modifier.padding(vertical = vPad)) {
        Text(
            text = title,
            fontSize = sectionSp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = vPad)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(if (compact) 10.dp else 16.dp)
        ) {
            Column(modifier = Modifier.padding(cardPad)) {
                content()
            }
        }
    }
}

@Composable
private fun CompactInfoRow(
    label: String,
    value: String,
    labelSp: TextUnit,
    valueSp: TextUnit,
    vertPad: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = vertPad),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = labelSp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            text = value,
            fontSize = valueSp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.1f),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactStreamItem(
    number: Int,
    track: TrackMetadata,
    streamLabelSp: TextUnit,
    labelSp: TextUnit,
    valueSp: TextUnit,
    vertPad: androidx.compose.ui.unit.Dp,
    compact: Boolean
) {
    Column(modifier = Modifier.padding(vertical = if (compact) 4.dp else 8.dp)) {
        Text(
            text = "Stream $number",
            fontSize = streamLabelSp,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
        CompactInfoRow("Type", track.type.name.lowercase().replaceFirstChar { it.uppercase() }, labelSp, valueSp, vertPad)
        track.codec?.let { CompactInfoRow("Codec", it.uppercase(), labelSp, valueSp, vertPad) }
        track.language?.let { CompactInfoRow("Language", it, labelSp, valueSp, vertPad) }
        track.extra.forEach { (k, v) -> CompactInfoRow(k, v, labelSp, valueSp, vertPad) }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = if (compact) 4.dp else 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

// ─── Keep original helpers for VideoListScreen usage ─────────────────────────

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
            Column(modifier = Modifier.padding(16.dp)) { content() }
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
        track.extra.forEach { (k, v) -> InfoRow(label = k, value = v) }
        if (number < 10) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
