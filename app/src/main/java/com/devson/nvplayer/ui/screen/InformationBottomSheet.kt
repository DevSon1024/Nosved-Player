package com.devson.nvplayer.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.model.Video
import com.devson.nvplayer.util.formatDate
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformationBottomSheet(
    selectedVideos: Set<Video>,
    onDismiss: () -> Unit
) {
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
            } else {
                val totalSize = selectedVideos.sumOf { it.size }
                val totalDuration = selectedVideos.sumOf { it.duration }
                val distinctFolders = selectedVideos.map { it.folderName }.distinct()

                InfoItem("Selected Items", "${selectedVideos.size} videos")
                InfoItem("Total Size", formatSize(totalSize))
                InfoItem("Total Duration", formatDuration(totalDuration))
                InfoItem("Location(s)", distinctFolders.joinToString(", "))
            }
        }
    }
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
