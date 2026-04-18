package com.devson.nosvedplayer.ui.screens.videolist.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class VideoWatchState {
    object Unplayed : VideoWatchState()
    object InProgress : VideoWatchState()
    object Completed : VideoWatchState()
}

fun getWatchState(lastPositionMs: Long, duration: Long): VideoWatchState {
    val progress = if (duration > 0) (lastPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    return when {
        progress == 0f -> VideoWatchState.Unplayed
        progress > 0.95f -> VideoWatchState.Completed
        else -> VideoWatchState.InProgress
    }
}

@Composable
fun WatchStateBadge(state: VideoWatchState, isLarge: Boolean = false) {
    val (label, bgColor, textColor) = when (state) {
        is VideoWatchState.Unplayed  -> Triple("New",     MaterialTheme.colorScheme.primary,                          MaterialTheme.colorScheme.onPrimary)
        is VideoWatchState.InProgress -> Triple("Running", MaterialTheme.colorScheme.tertiary,                         MaterialTheme.colorScheme.onTertiary)
        is VideoWatchState.Completed  -> Triple("Ended",   MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f), MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val fontSize = if (isLarge) 11.sp else 9.sp
    val horizontalPadding = if (isLarge) 7.dp else 5.dp
    val verticalPadding = if (isLarge) 3.dp else 2.dp
    val cornerRadius = if (isLarge) 6.dp else 5.dp
    val outerPadding = if (isLarge) 8.dp else 6.dp

    Box(
        modifier = Modifier
            .padding(outerPadding)
            .background(color = bgColor, shape = RoundedCornerShape(cornerRadius))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Text(
            text       = label,
            color      = textColor,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            fontSize   = fontSize
        )
    }
}
