package com.devson.nosvedplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import com.devson.nosvedplayer.model.LayoutMode
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.util.formatDate
import com.devson.nosvedplayer.util.formatSize
import com.devson.nosvedplayer.ui.components.FolderShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.*
import androidx.compose.ui.draw.scale

// FOLDER MEDIA PREVIEW
@Composable
fun FolderMediaPreview(
    videos: List<Video>,
    isSelected: Boolean,
    settings: ViewSettings,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.secondaryContainer
 
    Box(
        modifier = modifier
            .clip(FolderShape())
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (videos.isNotEmpty() && settings.showThumbnail) {
            VideoThumbnail(
                uri         = videos.first().uri,
                modifier    = Modifier.fillMaxSize(),
                showPlayIcon = false
            )
            // Subtle bottom scrim so folder looks layered
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1.0f  to Color.Black.copy(alpha = 0.28f)
                        )
                    )
            )
        } else {
            // No thumbnail — show a tinted icon
            Icon(
                imageVector  = Icons.Filled.FolderOpen,
                contentDescription = null,
                tint         = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                modifier     = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun BoxScope.SelectionCheckmarkOverlay(visible: Boolean = true) {
    val scale by animateFloatAsState(
        targetValue  = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "checkScale"
    )
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .scale(scale)
            .size(24.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector  = Icons.Filled.Check,
            contentDescription = "Selected",
            tint         = MaterialTheme.colorScheme.onPrimary,
            modifier     = Modifier.size(14.dp)
        )
    }
}

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
    val bgColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "folderListBg"
    )
    val borderColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        animationSpec = tween(180),
        label = "folderListBorder"
    )
 
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 0.dp else 1.dp
        ),
        border = BorderStroke(if (isSelected) 1.5.dp else 0.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Folder thumbnail
                FolderMediaPreview(
                    videos   = videos,
                    isSelected = false,
                    settings = settings,
                    modifier = Modifier.size(width = 72.dp, height = 54.dp)
                )
 
                Spacer(modifier = Modifier.width(14.dp))
 
                // Folder name + metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = folder.name,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        color      = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FolderMetadataChips(videos, settings)
                }
            }
 
            // Animated checkmark in top-right
            SelectionCheckmarkOverlay(visible = isSelected)
        }
    }
}

//  FOLDER GRID ITEM  (replace your existing FolderGridItem)
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
 
    val bgColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "folderGridBg"
    )
    val borderColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        animationSpec = tween(180),
        label = "folderGridBorder"
    )
 
    // 1-column: wide landscape card
    if (settings.gridColumns == 1) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            shape     = RoundedCornerShape(18.dp),
            colors    = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
            border    = BorderStroke(if (isSelected) 1.5.dp else 0.dp, borderColor)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FolderMediaPreview(
                        videos   = videos,
                        isSelected = false,
                        settings = settings,
                        modifier = Modifier.size(width = 124.dp, height = 82.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = folder.name,
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis,
                            color      = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FolderMetadataChips(videos, settings, isGrid = false)
                    }
                }
                SelectionCheckmarkOverlay(visible = isSelected)
            }
        }
        return
    }
 
    // 2-column: thumbnail + label strip
    if (settings.gridColumns == 2) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.88f)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
            border    = BorderStroke(if (isSelected) 1.5.dp else 0.dp, borderColor)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    FolderMediaPreview(
                        videos   = videos,
                        isSelected = false,
                        settings = settings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text       = folder.name,
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            color      = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        FolderMetadataChips(videos, settings, isGrid = true)
                    }
                }
                SelectionCheckmarkOverlay(visible = isSelected)
            }
        }
        return
    }
 
    // 3+ columns: full-bleed thumbnail with overlay label
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
        border    = BorderStroke(if (isSelected) 1.5.dp else 0.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FolderMediaPreview(
                videos   = videos,
                isSelected = false,
                settings = settings,
                modifier = Modifier.fillMaxSize()
            )
 
            // Gradient scrim for label legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.40f to Color.Transparent,
                            1.0f  to Color.Black.copy(alpha = 0.72f)
                        )
                    )
            )
 
            // Selected tint
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                )
            }
 
            // Label strip at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Text(
                    text       = folder.name,
                    color      = Color.White,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    text  = "${videos.size} videos",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.5.sp
                )
            }
 
            SelectionCheckmarkOverlay(visible = isSelected)
        }
    }
}

// FOLDER METADATA ROW
@Composable
fun FolderMetadataRow(videos: List<Video>, settings: ViewSettings, isGrid: Boolean = false) {
    // Alias kept for backward compat
    FolderMetadataChips(videos, settings, isGrid)
}
 
@Composable
fun FolderMetadataChips(videos: List<Video>, settings: ViewSettings, isGrid: Boolean = false) {
    val tokens = buildList {
        add(Pair("${videos.size} videos", true))   // count → primary chip
        if (settings.showSize) {
            val totalSize = videos.sumOf { it.size }
            add(Pair(formatSize(totalSize), false))
        }
        if (settings.showDate) {
            val oldest = videos.minOfOrNull { it.dateAdded } ?: 0L
            if (oldest > 0) add(Pair(formatDate(oldest), false))
        }
    }.filter { it.first.isNotBlank() }
 
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

    if (settings.layoutMode == LayoutMode.GRID) {
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