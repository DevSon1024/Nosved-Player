package com.devson.nvplayer.ui.common.sheets

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.data.mediainfo.MediaInfoOps
import com.devson.nvplayer.data.mediainfo.MediaInfoParser
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoThumbnail
import com.devson.nvplayer.util.formatDate
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatSize

private data class InfoSection(
    val name: String,
    val properties: List<Pair<String, String>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformationBottomSheet(
    selectedVideos: Set<Video>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Media Information",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (selectedVideos.size == 1) {
                SingleVideoInformationContent(
                    video = selectedVideos.first(),
                    modifier = Modifier.fillMaxWidth(),
                    isScrollable = true
                )
            } else {
                val totalSize = selectedVideos.sumOf { it.size }
                val totalDuration = selectedVideos.sumOf { it.duration }
                val distinctFolders = selectedVideos.map { it.folderName }.distinct()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.VideoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${selectedVideos.size} Videos Selected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Batch summary statistics",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InfoPropertyRow(
                                icon = Icons.Outlined.Storage,
                                label = "Total Size",
                                value = formatSize(totalSize)
                            )
                            InfoPropertyRow(
                                icon = Icons.Outlined.Schedule,
                                label = "Total Duration",
                                value = formatDuration(totalDuration)
                            )
                            InfoPropertyRow(
                                icon = Icons.Outlined.Folder,
                                label = "Locations",
                                value = distinctFolders.joinToString(", ")
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleVideoInformationContent(
    video: Video,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = true
) {
    val context = LocalContext.current
    var fullMediaInfoText by remember(video.uri) { mutableStateOf<String?>(null) }
    var isLoadingInfo by remember(video.uri) { mutableStateOf(false) }

    LaunchedEffect(video.uri) {
        isLoadingInfo = true
        val uri = runCatching { Uri.parse(video.uri) }.getOrNull()
        if (uri != null) {
            MediaInfoOps.generateTextOutput(context, uri, video.title)
                .onSuccess {
                    fullMediaInfoText = it
                    isLoadingInfo = false
                }
                .onFailure {
                    isLoadingInfo = false
                }
        } else {
            isLoadingInfo = false
        }
    }

    val parsed = remember(video.title, video.duration) { MediaInfoParser.parse(video.title, video.duration) }

    Column(modifier = modifier) {
        // Hero Media Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    VideoThumbnail(
                        uri = video.thumbnailUri ?: video.uri,
                        modifier = Modifier.fillMaxSize(),
                        showPlayIcon = false
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parsed.title.ifBlank { video.title.substringBeforeLast(".") },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val typeLabel = when (parsed.type) {
                            "tv" -> "TV Show"
                            "movie" -> "Movie"
                            else -> "Video"
                        }
                        SuggestionChip(
                            onClick = {},
                            label = { Text(typeLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        if (parsed.year != null) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(parsed.year, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        if (parsed.season != null && parsed.episode != null) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("S${parsed.season}E${parsed.episode}", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        // Tab Selector Row
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf("Overview", "Video", "Audio", "Subtitles")

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                SegmentedButton(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size)
                ) {
                    Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }

        if (isLoadingInfo) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Analyzing media specifications...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (fullMediaInfoText != null) {
            val sections = remember(fullMediaInfoText) { parseMediaInfoText(fullMediaInfoText!!) }

            val generalSections = remember(sections) { sections.filter { it.name.equals("General", ignoreCase = true) } }
            val videoSections = remember(sections) { sections.filter { it.name.startsWith("Video", ignoreCase = true) } }
            val audioSections = remember(sections) { sections.filter { it.name.startsWith("Audio", ignoreCase = true) } }
            val subtitleSections = remember(sections) { sections.filter { it.name.startsWith("Text", ignoreCase = true) } }
            val otherSections = remember(sections) {
                sections.filter {
                    !it.name.equals("General", ignoreCase = true) &&
                    !it.name.startsWith("Video", ignoreCase = true) &&
                    !it.name.startsWith("Audio", ignoreCase = true) &&
                    !it.name.startsWith("Text", ignoreCase = true)
                }
            }

            val detailsContent = @Composable {
                Column(
                    modifier = if (isScrollable) Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                    else Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (generalSections.isNotEmpty()) {
                                        generalSections.forEach { section ->
                                            section.properties.forEach { (key, value) ->
                                                InfoPropertyRow(
                                                    icon = getIconForProperty(key),
                                                    label = key,
                                                    value = value
                                                )
                                            }
                                        }
                                    } else {
                                        InfoPropertyRow(icon = Icons.Outlined.Title, label = "Filename", value = video.title)
                                        InfoPropertyRow(icon = Icons.Outlined.Schedule, label = "Duration", value = formatDuration(video.duration))
                                        InfoPropertyRow(icon = Icons.Outlined.Storage, label = "Size", value = formatSize(video.size))
                                    }

                                    if (video.path.isNotBlank()) {
                                        InfoPropertyRow(icon = Icons.Outlined.Folder, label = "Location", value = video.path)
                                    }
                                    if (video.dateAdded > 0) {
                                        InfoPropertyRow(icon = Icons.Outlined.CalendarToday, label = "Date Added", value = formatDate(video.dateAdded))
                                    }
                                }
                            }

                            if (otherSections.isNotEmpty()) {
                                otherSections.forEach { section ->
                                    ElevatedCard(
                                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = section.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            section.properties.forEach { (key, value) ->
                                                InfoPropertyRow(
                                                    icon = getIconForProperty(key),
                                                    label = key,
                                                    value = value
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (videoSections.isNotEmpty()) {
                                        videoSections.forEachIndexed { _, section ->
                                            if (videoSections.size > 1) {
                                                Text(
                                                    text = section.name,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            section.properties.forEach { (key, value) ->
                                                InfoPropertyRow(
                                                    icon = getIconForProperty(key),
                                                    label = key,
                                                    value = value
                                                )
                                            }
                                        }
                                    } else {
                                        if (video.width > 0 && video.height > 0) {
                                            InfoPropertyRow(icon = Icons.Outlined.AspectRatio, label = "Resolution", value = "${video.width}x${video.height}")
                                        }
                                        if (video.frameRate != null && video.frameRate > 0) {
                                            val formattedFps = if (video.frameRate % 1f == 0f) "${video.frameRate.toInt()} fps" else String.format(java.util.Locale.US, "%.2f fps", video.frameRate)
                                            InfoPropertyRow(icon = Icons.Outlined.Speed, label = "Frame Rate", value = formattedFps)
                                        }
                                        InfoPropertyRow(icon = Icons.Outlined.Movie, label = "Video Codec", value = "Standard Video Stream")
                                    }
                                }
                            }
                        }
                        2 -> {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (audioSections.isNotEmpty()) {
                                        audioSections.forEachIndexed { _, section ->
                                            Text(
                                                text = section.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            section.properties.forEach { (key, value) ->
                                                InfoPropertyRow(
                                                    icon = getIconForProperty(key),
                                                    label = key,
                                                    value = value
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "No audio stream details found",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                        3 -> {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (subtitleSections.isNotEmpty()) {
                                        subtitleSections.forEachIndexed { _, section ->
                                            Text(
                                                text = section.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            section.properties.forEach { (key, value) ->
                                                InfoPropertyRow(
                                                    icon = getIconForProperty(key),
                                                    label = key,
                                                    value = value
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "No embedded subtitle streams found",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isScrollable) {
                SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                    detailsContent()
                }
            } else {
                SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                    detailsContent()
                }
            }
        } else {
            // Fallback default single video details card
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoPropertyRow(icon = Icons.Outlined.Title, label = "Name", value = video.title)
                    InfoPropertyRow(icon = Icons.Outlined.Schedule, label = "Duration", value = formatDuration(video.duration))
                    InfoPropertyRow(icon = Icons.Outlined.Storage, label = "Size", value = formatSize(video.size))
                    if (!video.resolution.isNullOrBlank()) {
                        InfoPropertyRow(icon = Icons.Outlined.AspectRatio, label = "Resolution", value = video.resolution)
                    } else if (video.width > 0 && video.height > 0) {
                        InfoPropertyRow(icon = Icons.Outlined.AspectRatio, label = "Resolution", value = "${video.width}x${video.height}")
                    }
                    if (video.frameRate != null && video.frameRate > 0) {
                        val formattedFps = if (video.frameRate % 1f == 0f) "${video.frameRate.toInt()} fps" else String.format(java.util.Locale.US, "%.2f fps", video.frameRate)
                        InfoPropertyRow(icon = Icons.Outlined.Speed, label = "Frame Rate", value = formattedFps)
                    }
                    if (video.embeddedSubtitles.isNotEmpty()) {
                        InfoPropertyRow(icon = Icons.Outlined.Subtitles, label = "Embedded Subtitles", value = video.embeddedSubtitles.joinToString(", "))
                    }
                    if (video.externalSubtitles.isNotEmpty()) {
                        InfoPropertyRow(icon = Icons.Outlined.Subtitles, label = "External Subtitles", value = video.externalSubtitles.joinToString(", "))
                    }
                    if (video.dateAdded > 0) {
                        InfoPropertyRow(icon = Icons.Outlined.CalendarToday, label = "Date Added", value = formatDate(video.dateAdded))
                    }
                    InfoPropertyRow(icon = Icons.Outlined.Folder, label = "Path", value = video.path)
                }
            }
        }
    }
}

@Composable
fun InfoPropertyRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { clipboardManager.setText(AnnotatedString(value)) }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun getIconForProperty(label: String): ImageVector {
    val l = label.lowercase()
    return when {
        l.contains("format") || l.contains("codec") -> Icons.Outlined.Movie
        l.contains("bit rate") || l.contains("bitrate") -> Icons.Outlined.Speed
        l.contains("width") || l.contains("height") || l.contains("resolution") || l.contains("aspect") -> Icons.Outlined.AspectRatio
        l.contains("frame rate") || l.contains("fps") -> Icons.Outlined.SlowMotionVideo
        l.contains("channel") || l.contains("audio") || l.contains("sample") -> Icons.Outlined.GraphicEq
        l.contains("language") -> Icons.Outlined.Language
        l.contains("duration") || l.contains("time") -> Icons.Outlined.Schedule
        l.contains("size") -> Icons.Outlined.Storage
        l.contains("path") || l.contains("location") || l.contains("folder") -> Icons.Outlined.Folder
        l.contains("title") || l.contains("name") -> Icons.Outlined.Title
        l.contains("date") -> Icons.Outlined.CalendarToday
        else -> Icons.Outlined.Info
    }
}

private fun parseMediaInfoText(text: String): List<InfoSection> {
    val sections = mutableListOf<InfoSection>()
    val lines = text.lines()

    var currentSectionName: String? = null
    val currentProperties = mutableListOf<Pair<String, String>>()

    for (line in lines) {
        when {
            line.trim().startsWith("=") || line.trim().isEmpty() -> continue
            line.contains("MEDIA INFO -") || line.contains("Generated by mpvex") || line.contains("Generated by Nosved Player") -> continue
            !line.startsWith(" ") && !line.contains(":") && line.trim().isNotEmpty() -> {
                if (currentSectionName != null && currentProperties.isNotEmpty()) {
                    sections.add(InfoSection(currentSectionName, currentProperties.toList()))
                    currentProperties.clear()
                }
                currentSectionName = line.trim()
            }
            line.contains(":") -> {
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        currentProperties.add(key to value)
                    }
                }
            }
        }
    }

    if (currentSectionName != null && currentProperties.isNotEmpty()) {
        sections.add(InfoSection(currentSectionName, currentProperties.toList()))
    }

    return sections
}