package com.devson.nosvedplayer.ui.screens.videolist.components.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.ui.screens.videolist.components.common.VideoMetadataChips
import com.devson.nosvedplayer.ui.screens.videolist.components.common.VideoWatchState
import com.devson.nosvedplayer.ui.screens.videolist.components.common.WatchProgressBar
import com.devson.nosvedplayer.ui.screens.videolist.components.common.WatchStateBadge
import com.devson.nosvedplayer.ui.screens.videolist.components.common.getWatchState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridItem(
    video: Video,
    settings: ViewSettings,
    isSelected: Boolean = false,
    lastPositionMs: Long = 0L,
    onClick: (Video) -> Unit,
    onLongClick: (Video) -> Unit
) {
    val haptic  = LocalHapticFeedback.current
    val isDense = settings.gridColumns >= 3
 
    val bgColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "gridItemBg"
    )
    val borderColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        animationSpec = tween(180),
        label = "gridItemBorder"
    )
 
    // Single-column (full-width cinema card) 
    val watchState = getWatchState(lastPositionMs, video.duration)
    if (settings.gridColumns == 1) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .combinedClickable(
                    onClick    = { onClick(video) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick(video)
                    }
                ),
            shape     = RoundedCornerShape(18.dp),
            colors    = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
            border    = BorderStroke(if (isSelected) 1.5.dp else 0.dp, borderColor)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Wide thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .then(if (settings.selectByThumbnail) Modifier.clickable { onLongClick(video) } else Modifier)
                        .then(if (watchState is VideoWatchState.Completed) Modifier.alpha(0.6f) else Modifier)
                ) {
                    if (settings.showThumbnail) {
                        VideoThumbnail(uri = video.uri, modifier = Modifier.fillMaxSize(), showPlayIcon = !isSelected)
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(
                                if (watchState is VideoWatchState.InProgress)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Movie, null, Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        }
                    }
                    if (!isSelected) WatchStateBadge(watchState, isLarge = true)
                    if (settings.showLength && settings.displayLengthOverThumbnail && !isSelected)
                        DurationBadge(video.duration, isGrid = true)
                    WatchProgressBar(lastPositionMs, video.duration)
                    ThumbnailSelectionOverlay(isSelected)
                }
 
                // Info strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.showFileExtension) video.title
                                   else video.title.substringBeforeLast("."),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis,
                            color      = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else if (watchState is VideoWatchState.Completed)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        VideoMetadataChips(video, settings, lastPositionMs)
                    }
                }
            }
        }
        return
    }
 
    // Multi-column compact card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (isDense) 1f else 0.82f)
            .combinedClickable(
                onClick    = { onClick(video) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(video)
                }
            ),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
        border    = BorderStroke(if (isSelected) 1.5.dp else 0.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thumbnail fills most of the card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(if (settings.selectByThumbnail) Modifier.clickable { onLongClick(video) } else Modifier)
                    .then(if (watchState is VideoWatchState.Completed) Modifier.alpha(0.6f) else Modifier)
            ) {
                if (settings.showThumbnail) {
                    VideoThumbnail(
                        uri          = video.uri,
                        modifier     = Modifier.fillMaxSize(),
                        showPlayIcon = !isSelected && !isDense
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            if (watchState is VideoWatchState.InProgress)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Movie, null, Modifier.size(if (isDense) 28.dp else 36.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                }

                if (!isSelected) WatchStateBadge(watchState, isLarge = settings.gridColumns <= 2)

                // Duration badge
                if (settings.showLength && settings.displayLengthOverThumbnail && !isSelected)
                    DurationBadge(video.duration, isGrid = true)
 
                // Watch-progress bar
                WatchProgressBar(lastPositionMs, video.duration)
 
                // Selection overlay
                ThumbnailSelectionOverlay(isSelected, isDense)
            }
 
            // Bottom label (hidden in dense ≥3 columns - too cramped)
            if (!isDense) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 10.dp)
                ) {
                    Text(
                        text = if (settings.showFileExtension) video.title
                               else video.title.substringBeforeLast("."),
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        color      = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else if (watchState is VideoWatchState.Completed)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    VideoMetadataChips(video, settings, lastPositionMs, isGrid = true)
                }
            }
        }
    }
}
