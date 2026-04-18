package com.devson.nosvedplayer.ui.screens.videolist.utils

import android.content.Context
import android.net.Uri
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.ui.screens.videolist.components.common.VideoWatchState
import com.devson.nosvedplayer.ui.screens.videolist.components.common.getWatchState

/**
 * Common extension functions for Video objects and lists inside the VideoList screen.
 */

fun Video.getWatchStatus(lastPositionMs: Long): VideoWatchState {
    return getWatchState(lastPositionMs, this.duration)
}

fun List<Video>.getUris(): List<Uri> {
    return this.mapNotNull {
        runCatching { Uri.parse(it.uri) }.getOrNull()
    }
}

fun Long.formatAsDuration(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
