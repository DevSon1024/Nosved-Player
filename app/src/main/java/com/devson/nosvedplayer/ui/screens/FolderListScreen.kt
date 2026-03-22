package com.devson.nosvedplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.utility.formatDate
import com.devson.nosvedplayer.utility.formatSize

// FOLDER LIST ITEM
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderListItem(
    folder: VideoFolder,
    videos: List<Video>,
    settings: ViewSettings,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                FolderMetadataRow(videos, settings)
            }
        }
    }
}

// FOLDER GRID ITEM

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderGridItem(
    folder: VideoFolder,
    videos: List<Video>,
    settings: ViewSettings,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isDense = settings.gridColumns >= 3
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (isDense) 1f else 0.85f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Icon Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(if (isDense) 8.dp else 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Circular background for the folder icon
                    Box(
                        modifier = Modifier
                            .size(if (isDense) 48.dp else 64.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(if (isDense) 24.dp else 32.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Video count pill for dense grid
                    if (isDense && settings.showFolderVideoCount) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${videos.size}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Text Section (hidden if dense grid to save space)
                if (!isDense) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Changed to use start, end, and bottom explicitly
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FolderMetadataRow(videos, settings, isGrid = true)
                    }
                }
            }

            // Selection checkmark overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// FOLDER METADATA ROW

@Composable
fun FolderMetadataRow(videos: List<Video>, settings: ViewSettings, isGrid: Boolean = false) {
    val metaItems = mutableListOf<String>()

    if (settings.showFolderVideoCount) metaItems.add("${videos.size} videos")
    if (settings.showFolderSize) {
        val totalSize = videos.sumOf { it.size }
        metaItems.add(formatSize(totalSize))
    }
    if (settings.showFolderDate) {
        val oldestVideoDate = videos.minOfOrNull { it.dateAdded } ?: 0L
        if (oldestVideoDate > 0) metaItems.add(formatDate(oldestVideoDate))
    }

    if (metaItems.isNotEmpty()) {
        val text = metaItems.filter { it.isNotEmpty() }.joinToString(" • ")
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (isGrid) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// FOLDER INFO DIALOG

@Composable
fun FolderInfoDialog(
    selectedFolders: Set<VideoFolder>,
    videosByFolder: Map<VideoFolder, List<Video>>,
    onDismiss: () -> Unit
) {
    val allVideos = selectedFolders.flatMap { folder -> videosByFolder[folder] ?: emptyList() }
    val totalVideos = allVideos.size
    val totalSize = allVideos.sumOf { it.size }
    val location = if (selectedFolders.size == 1) {
        val firstVideo = allVideos.firstOrNull()
        if (firstVideo != null && firstVideo.path.contains("/")) {
            firstVideo.path.substringBeforeLast("/")
        } else {
            selectedFolders.first().name
        }
    } else {
        "${selectedFolders.size} folders selected"
    }
    val oldestDate = if (selectedFolders.size == 1) {
        allVideos.minOfOrNull { it.dateAdded }
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
        title = {
            Text(
                if (selectedFolders.size == 1) selectedFolders.first().name else "Selected Folders",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(label = "Total Videos", value = "$totalVideos")
                InfoRow(label = "Total Size", value = formatSize(totalSize))
                InfoRow(label = "Location", value = location)
                if (oldestDate != null && oldestDate > 0L) {
                    InfoRow(label = "Creation Date", value = formatDate(oldestDate))
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

// FOLDER LIST CONTENT

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderListContent(
    folders: Map<VideoFolder, List<Video>>,
    settings: ViewSettings,
    selectedFolders: Set<VideoFolder>,
    onFolderClick: (VideoFolder) -> Unit,
    onFolderLongClick: (VideoFolder) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState()
) {
    val haptic = LocalHapticFeedback.current
    val sortedFolders = remember(folders) { folders.keys.toList().sortedBy { it.name.lowercase() } }

    if (settings.isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.gridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortedFolders) { folder ->
                FolderGridItem(
                    folder = folder,
                    videos = folders[folder] ?: emptyList(),
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
                    }
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(sortedFolders) { folder ->
                FolderListItem(
                    folder = folder,
                    videos = folders[folder] ?: emptyList(),
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
                    }
                )
            }
        }
    }
}