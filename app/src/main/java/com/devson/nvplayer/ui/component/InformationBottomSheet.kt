package com.devson.nvplayer.ui.component

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.mediaInfo.MediaInfoOps
import com.devson.nvplayer.mediaInfo.MediaInfoParser
import com.devson.nvplayer.model.Video
import com.devson.nvplayer.util.formatDate
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatSize

private data class InfoSection(
    val name: String,
    val properties: List<Pair<String, String>>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformationBottomSheet(
    selectedVideos: Set<Video>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var fullMediaInfoText by remember { mutableStateOf<String?>(null) }
    var isLoadingInfo by remember { mutableStateOf(false) }

    LaunchedEffect(selectedVideos) {
        if (selectedVideos.size == 1) {
            val video = selectedVideos.first()
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
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Information",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (selectedVideos.size == 1) {
                val video = selectedVideos.first()
                val parsed = remember(video.title) { MediaInfoParser.parse(video.title) }

                // Premium visual Title Card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = parsed.title.ifBlank { video.title.substringBeforeLast(".") },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val typeLabel = if (parsed.type == "tv") "TV Show" else "Movie"
                            SuggestionChip(
                                onClick = {},
                                label = { Text(typeLabel) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                )
                            )
                            if (parsed.year != null) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(parsed.year) }
                                )
                            }
                            if (parsed.season != null && parsed.episode != null) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("S${parsed.season}E${parsed.episode}") }
                                )
                            }
                        }
                    }
                }

                // Sleek custom pill segment selectors (Overview, Video, Audio, Subtitles)
                var selectedTab by remember { mutableStateOf(0) }
                val tabs = listOf("Overview", "Video", "Audio", "Subtitles")

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { selectedTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isLoadingInfo) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Analyzing media file...",
                                style = MaterialTheme.typography.bodyMedium,
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

                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (selectedTab) {
                                0 -> {
                                    // Overview / General properties
                                    if (generalSections.isNotEmpty()) {
                                        generalSections.forEach { section ->
                                            section.properties.forEach { (key, value) ->
                                                PropertyRow(key, value)
                                            }
                                        }
                                    } else {
                                        // Fallback default info
                                        InfoItem("Filename", video.title)
                                        InfoItem("Duration", formatDuration(video.duration))
                                        InfoItem("Size", formatSize(video.size))
                                        InfoItem("Path", video.path)
                                    }

                                    // Display any other unmatched sections (e.g. Menu) inside Overview
                                    if (otherSections.isNotEmpty()) {
                                        otherSections.forEach { section ->
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = section.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                            section.properties.forEach { (key, value) ->
                                                PropertyRow(key, value)
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    // Video Stream properties
                                    if (videoSections.isNotEmpty()) {
                                        videoSections.forEachIndexed { index, section ->
                                            if (videoSections.size > 1) {
                                                Text(
                                                    text = section.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(vertical = 12.dp)
                                                )
                                            }
                                            section.properties.forEach { (key, value) ->
                                                PropertyRow(key, value)
                                            }
                                        }
                                    } else {
                                        if (video.width > 0 && video.height > 0) {
                                            InfoItem("Resolution", "${video.width}x${video.height}")
                                        }
                                        if (video.frameRate != null && video.frameRate > 0) {
                                            InfoItem("Frame Rate", "${video.frameRate} fps")
                                        }
                                        InfoItem("Video Codec", "Advanced details unavailable.")
                                    }
                                }
                                2 -> {
                                    // Audio Stream properties
                                    if (audioSections.isNotEmpty()) {
                                        audioSections.forEachIndexed { index, section ->
                                            Text(
                                                text = section.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(vertical = 12.dp)
                                            )
                                            section.properties.forEach { (key, value) ->
                                                PropertyRow(key, value)
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No audio stream details found",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                3 -> {
                                    // Subtitle Stream properties
                                    if (subtitleSections.isNotEmpty()) {
                                        subtitleSections.forEachIndexed { index, section ->
                                            Text(
                                                text = section.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(vertical = 12.dp)
                                            )
                                            section.properties.forEach { (key, value) ->
                                                PropertyRow(key, value)
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No embedded subtitle streams found",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Fallback default single video details
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        InfoItem("Name", video.title)
                        InfoItem("Duration", formatDuration(video.duration))
                        InfoItem("Size", formatSize(video.size))
                        if (!video.resolution.isNullOrBlank()) {
                            InfoItem("Resolution", video.resolution)
                        } else if (video.width > 0 && video.height > 0) {
                            InfoItem("Resolution", "${video.width}x${video.height}")
                        }
                        if (video.frameRate != null && video.frameRate > 0) {
                            InfoItem("Frame Rate", "${video.frameRate.toInt()} fps")
                        }
                        if (video.dateAdded > 0) {
                            InfoItem("Date Added", formatDate(video.dateAdded))
                        }
                        InfoItem("Path", video.path)
                    }
                }
            } else {
                val totalSize = selectedVideos.sumOf { it.size }
                val totalDuration = selectedVideos.sumOf { it.duration }
                val distinctFolders = selectedVideos.map { it.folderName }.distinct()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    InfoItem("Selected Items", "${selectedVideos.size} videos")
                    InfoItem("Total Size", formatSize(totalSize))
                    InfoItem("Total Duration", formatDuration(totalDuration))
                    InfoItem("Location(s)", distinctFolders.joinToString(", "))
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1.2f)
                    .padding(end = 16.dp)
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1.5f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

private fun parseMediaInfoText(text: String): List<InfoSection> {
    val sections = mutableListOf<InfoSection>()
    val lines = text.lines()

    var currentSectionName: String? = null
    val currentProperties = mutableListOf<Pair<String, String>>()

    for (line in lines) {
        when {
            // Skip separator lines and empty lines
            line.trim().startsWith("=") || line.trim().isEmpty() -> continue

            // Skip header/footer
            line.contains("MEDIA INFO -") || line.contains("Generated by mpvex") || line.contains("Generated by NVPlayer") -> continue

            // New section (no colon, not indented, has content)
            !line.startsWith(" ") && !line.contains(":") && line.trim().isNotEmpty() -> {
                // Save previous section
                if (currentSectionName != null && currentProperties.isNotEmpty()) {
                    sections.add(InfoSection(currentSectionName, currentProperties.toList()))
                    currentProperties.clear()
                }
                currentSectionName = line.trim()
            }

            // Property line (contains colon)
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

    // Add last section
    if (currentSectionName != null && currentProperties.isNotEmpty()) {
        sections.add(InfoSection(currentSectionName, currentProperties.toList()))
    }

    return sections
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(120.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
