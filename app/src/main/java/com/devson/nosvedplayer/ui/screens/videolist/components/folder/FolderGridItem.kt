package com.devson.nosvedplayer.ui.screens.videolist.components.folder

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nosvedplayer.R
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.model.WatchHistory
import com.devson.nosvedplayer.ui.screens.videolist.components.common.VideoWatchState
import com.devson.nosvedplayer.ui.screens.videolist.components.common.getWatchState
import com.devson.nosvedplayer.ui.screens.videolist.components.selection.SelectionCheckmarkOverlay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderGridItem(
    folder: VideoFolder,
    videos: List<Video>,
    settings: ViewSettings,
    isSelected: Boolean = false,
    historyMap: Map<String, WatchHistory> = emptyMap(),
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isDense = settings.gridColumns >= 3
    val newCount = remember(videos, historyMap) {
        videos.count { v -> getWatchState(
            historyMap[v.uri]?.lastPositionMs ?: 0L,
            v.duration
        ) is VideoWatchState.Unplayed }
    }
 
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
                    Box(modifier = Modifier.size(width = 124.dp, height = 82.dp).then(if (settings.selectByThumbnail) Modifier.clickable { onLongClick() } else Modifier)) {
                        FolderMediaPreview(
                            videos   = videos,
                            isSelected = false,
                            settings = settings,
                            modifier = Modifier.fillMaxSize()
                        )
                        NewCountBadge(newCount)
                    }
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
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(if (settings.selectByThumbnail) Modifier.clickable { onLongClick() } else Modifier)
                    ) {
                    FolderMediaPreview(
                        videos   = videos,
                        isSelected = false,
                        settings = settings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxSize()
                    )
                    NewCountBadge(newCount)
                    }
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
        Box(modifier = Modifier.fillMaxSize().then(if (settings.selectByThumbnail) Modifier.clickable { onLongClick() } else Modifier)) {
            FolderMediaPreview(
                videos   = videos,
                isSelected = false,
                settings = settings,
                modifier = Modifier.fillMaxSize()
            )
            NewCountBadge(newCount)
 
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
                    text  = stringResource(R.string.folder_videos_count, videos.size),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.5.sp
                )
            }
 
            SelectionCheckmarkOverlay(visible = isSelected)
        }
    }
}
