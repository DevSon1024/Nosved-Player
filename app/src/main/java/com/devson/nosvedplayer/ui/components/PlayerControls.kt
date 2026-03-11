package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isPlaying: Boolean,
    title: String,
    currentPosition: Long,
    duration: Long,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onControlsStateChange: () -> Unit,
    onSeekForward: (() -> Unit)? = null,
    onSeekBackward: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    showStats: Boolean = false,
    onToggleStats: (() -> Unit)? = null,
    onToggleResizeMode: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onControlsStateChange
                )
        ) {
            //  Top gradient + title + stats button 
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Removed Resize mode toggle button from Top Bar
                    // Stats toggle button
                    IconButton(onClick = { onToggleStats?.invoke() }) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = "Device Stats",
                            tint = if (showStats) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }

            //  Bottom gradient + seekbar and playback controls 
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var sliderPosition by remember(currentPosition) { mutableFloatStateOf(currentPosition.toFloat()) }

                    // Row 1: Time, Slider, Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatTime(currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = { onSeekTo(sliderPosition.toLong()) },
                            valueRange = 0f..if (duration > 0) duration.toFloat() else 100f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        val remaining = if (duration > currentPosition) duration - currentPosition else 0L
                        Text("-" + formatTime(remaining), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }

                    // Row 2: Secondary controls and main playback
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Left: Lock icon (placeholder for future implementation)
                        IconButton(onClick = { /* TODO: Lock controls */ }, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(Icons.Filled.LockOpen, contentDescription = "Lock Controls", tint = Color.White)
                        }
                        
                        // Center: Rewind, Play/Pause, Forward
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onSeekBackward?.invoke() }, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Filled.FastRewind, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                            IconButton(onClick = onPlayPauseToggle, modifier = Modifier.size(68.dp)) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                            IconButton(onClick = { onSeekForward?.invoke() }, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Filled.FastForward, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                        }
                        
                        // Right: Resize Toggle
                        if (onToggleResizeMode != null) {
                            IconButton(onClick = onToggleResizeMode, modifier = Modifier.align(Alignment.CenterEnd)) {
                                Icon(Icons.Filled.Crop, contentDescription = "Toggle Resize Mode", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
