package com.devson.nvplayer.ui.screen.videolist.components.folder

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.R
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.domain.model.isPathHidden
import com.devson.nvplayer.domain.model.ViewSettings
import com.devson.nvplayer.domain.model.WatchHistory
import com.devson.nvplayer.ui.common.shapes.FolderShape
import com.devson.nvplayer.ui.screen.videolist.components.common.VideoWatchState
import com.devson.nvplayer.ui.screen.videolist.components.common.getWatchState
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoThumbnail
import com.devson.nvplayer.ui.screens.videolist.components.selection.SelectionCheckmarkOverlay
import com.devson.nvplayer.util.formatDate
import com.devson.nvplayer.util.formatSize

@Composable
fun NewCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    if (count <= 0) return
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 2.dp,
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = stringResource(R.string.folder_new_badge, count),
                color      = MaterialTheme.colorScheme.onPrimary,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 9.sp
            )
        }
    }
}

@Composable
fun FolderMediaPreview(
    videos: List<Video>,
    isSelected: Boolean,
    settings: ViewSettings,
    modifier: Modifier = Modifier,
    isHidden: Boolean = false,
    hiddenIconSize: Dp = 18.dp
) {
    val bgColor = MaterialTheme.colorScheme.secondaryContainer
    val folderShape = remember { FolderShape() }

    Box(
        modifier = modifier
            .clip(folderShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (videos.isNotEmpty() && settings.showThumbnail) {
            val firstVideo = videos.first()
            VideoThumbnail(
                uri = firstVideo.uri,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isHidden) Modifier.blur(16.dp) else Modifier),
                showPlayIcon = false
            )
            // Subtle bottom scrim so folder looks layered; dark scrim over blurred hidden video
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isHidden) {
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.35f),
                                1.0f to Color.Black.copy(alpha = 0.55f)
                            )
                        } else {
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1.0f  to Color.Black.copy(alpha = 0.28f)
                            )
                        }
                    )
            )
        } else {
            // No thumbnail - show a tinted icon
            Icon(
                imageVector  = Icons.Filled.FolderOpen,
                contentDescription = null,
                tint         = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                modifier     = Modifier.size(28.dp)
            )
        }

        // Hidden eye indicator centered over the folder icon / blurred thumbnail
        if (isHidden) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = CircleShape
                    )
                    .padding(hiddenIconSize / 3.5f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = "Hidden",
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(hiddenIconSize)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderListItem(
    folder: VideoFolder,
    videos: List<Video>,
    settings: ViewSettings,
    isSelected: Boolean = false,
    isRecentlyPlayed: Boolean = false,
    historyMap: Map<String, WatchHistory> = emptyMap(),
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isHidden = folder.isPathHidden
    val newCount = remember(videos, historyMap, settings.newVideoDaysThreshold) {
        videos.count { v -> getWatchState(
            historyMap[v.uri]?.lastPositionMs ?: 0L,
            v.duration,
            v.dateAdded,
            settings.newVideoDaysThreshold
        ) is VideoWatchState.New }
    }
    val normalBg = MaterialTheme.colorScheme.surfaceContainerLow
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isRecentlyPlayed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f).compositeOver(normalBg)
            else -> normalBg
        },
        animationSpec = tween(180),
        label = "folderListBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isRecentlyPlayed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        },
        animationSpec = tween(180),
        label = "folderListBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 0.dp else 0.5.dp
        ),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Folder thumbnail
                val folderShape = remember { FolderShape() }
                Box(
                    modifier = Modifier.size(width = 72.dp, height = 54.dp)
                ) {
                    FolderMediaPreview(
                        videos   = videos,
                        isSelected = false,
                        settings = settings,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (settings.selectByThumbnail) Modifier.clip(folderShape).clickable { onLongClick() } else Modifier),
                        isHidden = isHidden
                    )
                    if (newCount > 0) {
                        NewCountBadge(
                            count = newCount,
                            isSelected = isSelected,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (-3).dp, y = (-3).dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Folder name + metadata
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text       = folder.name,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f, fill = false),
                            color      = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                isRecentlyPlayed -> MaterialTheme.colorScheme.primary
                                else       -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (isHidden) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Outlined.VisibilityOff,
                                contentDescription = "Hidden folder",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    if (settings.showPath) {
                        val folderPath = remember(folder.id, videos) {
                            videos.firstOrNull()?.path?.substringBeforeLast('/') ?: folder.id
                        }
                        if (folderPath.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = folderPath,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    FolderMetadataChips(videos, settings)
                }
            }

            // Animated checkmark in top-right
            SelectionCheckmarkOverlay(visible = isSelected)
        }
    }
}

@Composable
fun FolderMetadataRow(videos: List<Video>, settings: ViewSettings, isGrid: Boolean = false) {
    FolderMetadataChips(videos, settings, isGrid)
}
 
@Composable
fun FolderMetadataChips(videos: List<Video>, settings: ViewSettings, isGrid: Boolean = false) {
    val countText = stringResource(R.string.folder_videos_count, videos.size)
    val tokens = remember(videos, settings, countText) {
        buildList {
            add(Pair(countText, true))   // count → primary chip
            if (settings.showSize) {
                val totalSize = videos.sumOf { it.size }
                add(Pair(formatSize(totalSize), false))
            }
            if (settings.showDate) {
                val oldest = videos.minOfOrNull { it.dateAdded } ?: 0L
                if (oldest > 0) add(Pair(formatDate(oldest), false))
            }
        }.filter { it.first.isNotBlank() }
    }
 
    if (tokens.isEmpty()) return
 
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        tokens.take(if (isGrid) 2 else 3).forEach { (text, isPrimary) ->
            FolderMetaChip(text = text, isPrimary = isPrimary)
        }
    }
}
 
@Composable
private fun FolderMetaChip(text: String, isPrimary: Boolean) {
    val bgColor   = if (isPrimary)
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
 
    val textColor = if (isPrimary)
        MaterialTheme.colorScheme.onSecondaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant
 
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text       = text,
            style      = MaterialTheme.typography.labelSmall,
            fontSize   = 10.5.sp,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
            color      = textColor,
            maxLines   = 1
        )
    }
}

@Composable
fun FolderInfoDialog(
    selectedFolders: Set<VideoFolder>,
    videosByFolder: Map<VideoFolder, List<Video>>,
    onDismiss: () -> Unit
) {
    val allVideos = selectedFolders.flatMap { folder -> videosByFolder[folder] ?: emptyList() }
    val totalVideos = allVideos.size
    val totalSize = allVideos.sumOf { it.size }
    val totalDuration = allVideos.sumOf { it.duration }
    val location = if (selectedFolders.size == 1) {
        val firstVideo = allVideos.firstOrNull()
        if (firstVideo != null && firstVideo.path.contains("/")) {
            firstVideo.path.substringBeforeLast("/")
        } else {
            selectedFolders.first().name
        }
    } else {
        stringResource(R.string.folder_info_multiple_locations)
    }
    val oldestDate = if (selectedFolders.size == 1) {
        allVideos.minOfOrNull { it.dateAdded }
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
        title = {
            Text(
                if (selectedFolders.size == 1) selectedFolders.first().name else stringResource(R.string.folder_selection_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(label = stringResource(R.string.folder_info_total_videos), value = "$totalVideos")
                InfoRow(label = stringResource(R.string.folder_info_total_size), value = formatSize(totalSize))
                if (totalDuration > 0L) {
                    InfoRow(label = "Total Duration", value = com.devson.nvplayer.util.formatDuration(totalDuration))
                }
                InfoRow(label = stringResource(R.string.folder_info_location), value = location)
                if (oldestDate != null && oldestDate > 0L) {
                    InfoRow(label = stringResource(R.string.folder_info_creation_date), value = formatDate(oldestDate))
                }
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.5f)
        )
    }
}
