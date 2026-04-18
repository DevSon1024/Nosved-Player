package com.devson.nosvedplayer.ui.screens.videolist.components.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.WatchProgressBar(lastPositionMs: Long, duration: Long) {
    if (lastPositionMs > 0L && duration > 0L) {
        val progress = (lastPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress         = { progress },
            modifier         = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp),
            color            = MaterialTheme.colorScheme.primary,
            trackColor       = Color.Transparent,
            drawStopIndicator = {}
        )
    }
}
