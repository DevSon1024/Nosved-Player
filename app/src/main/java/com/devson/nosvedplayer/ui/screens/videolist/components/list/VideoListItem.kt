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
import androidx.compose.ui.draw.clip
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
fun VideoListItem(
    video: Video,
    settings: ViewSettings,
    isSelected: Boolean = false,
    lastPositionMs: Long = 0L,
    onClick: (Video) -> Unit,
    onLongClick: (Video) -> Unit
) {
    val haptic = LocalHapticFeedback.current
 
    // Smooth background colour transition on select
    val bgColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "listItemBg"
    )
    val borderColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        animationSpec = tween(180),
        label = "listItemBorder"
    )
 
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .combinedClickable(
                onClick    = { onClick(video) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(video)
                }
            ),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation  = if (isSelected) 0.dp else 1.dp,
            pressedElevation  = 0.dp
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            //  Thumbnail 
            val watchState = getWatchState(lastPositionMs, video.duration)
            Card(
                modifier = Modifier
                    .size(width = 100.dp, height = 60.dp)
                    .then(if (settings.selectByThumbnail) Modifier.clickable { onLongClick(video) } else Modifier)
                    .then(if (watchState is VideoWatchState.Completed) Modifier.alpha(0.6f) else Modifier),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (watchState is VideoWatchState.InProgress)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    else
                        Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                if (settings.showThumbnail) {
                    VideoThumbnail(
                        uri         = video.uri,
                        modifier    = Modifier.fillMaxSize(),
                        showPlayIcon = !isSelected
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }

                // Watch state badge (top-left): NEW / Running / Ended
                if (!isSelected) {
                    WatchStateBadge(watchState, isLarge = false)
                }

                // Duration badge (shown only when displayLengthOverThumbnail is true)
                if (settings.showLength && settings.displayLengthOverThumbnail && !isSelected) {
                    DurationBadge(video.duration, isGrid = false)
                }
 
                // Watch-progress bar
                WatchProgressBar(lastPositionMs, video.duration)
 
                // Selection overlay (animated)
                ThumbnailSelectionOverlay(isSelected, isDense = true)
            }
            }
 
            Spacer(modifier = Modifier.width(14.dp))
 
            //  Text section 
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (settings.showFileExtension) video.title
                           else video.title.substringBeforeLast("."),
                    style     = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines  = 2,
                    overflow  = TextOverflow.Ellipsis,
                    color     = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else if (watchState is VideoWatchState.Completed)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
 
                Spacer(modifier = Modifier.height(5.dp))
 
                VideoMetadataChips(video, settings, lastPositionMs)
            }
        }
    }
}
