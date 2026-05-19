package com.devson.nvplayer.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Modern glassmorphic playback controls designed with premium dark mode aesthetics.
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onCycleSubtitle: () -> Unit,
    onCycleAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Premium color palette: Neon Cyan, Dark Charcoal, Deep Purple accents
    val neonCyan = Color(0xFF00E5FF)
    val neonCyanDim = Color(0xFF00B0FF)
    val darkCardBackground = Color(0xCC121212) // Semi-transparent glass look

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekProgressValue by remember { mutableFloatStateOf(0f) }
    
    // Playback speed state
    val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
    var currentSpeedIndex by remember { mutableIntStateOf(1) }

    // Synchronize slider with external progress when the user is not actively dragging it
    val sliderPosition = if (isUserSeeking) seekProgressValue else {
        if (duration > 0) currentPosition.toFloat() / duration else 0f
    }

    // Micro-animations for button presses
    val playScale by animateFloatAsState(targetValue = if (isPlaying) 1.0f else 1.1f, label = "PlayScale")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = darkCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x22FFFFFF),
                            Color(0x05FFFFFF)
                        )
                    )
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Seekbar Slider
            Slider(
                value = sliderPosition,
                onValueChange = {
                    isUserSeeking = true
                    seekProgressValue = it
                },
                onValueChangeFinished = {
                    isUserSeeking = false
                    if (duration > 0) {
                        onSeek((seekProgressValue * duration).toLong())
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = neonCyan,
                    activeTrackColor = neonCyanDim,
                    inactiveTrackColor = Color(0xFF424242)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )

            // 2. Playback Timers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(if (isUserSeeking) (seekProgressValue * duration).toLong() else currentPosition),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatTime(duration),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Playback Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rewind 10 Seconds
                IconButton(
                    onClick = {
                        val newPos = (currentPosition - 10000L).coerceAtLeast(0L)
                        onSeek(newPos)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay10,
                        contentDescription = "Rewind 10 seconds",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Play / Pause Button with Neon Accent Glow
                FilledIconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(64.dp)
                        .scale(playScale),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = neonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Forward 10 Seconds
                IconButton(
                    onClick = {
                        val newPos = (currentPosition + 10000L).coerceAtMost(duration)
                        onSeek(newPos)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forward10,
                        contentDescription = "Forward 10 seconds",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // 4. Secondary Action Buttons (Audio, Subtitle, Speed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Audio Track Cycle
                IconButton(onClick = onCycleAudio) {
                    Icon(
                        imageVector = Icons.Rounded.Audiotrack,
                        contentDescription = "Cycle Audio Track",
                        tint = Color.White
                    )
                }
                
                // Subtitle Cycle
                IconButton(onClick = onCycleSubtitle) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = "Cycle Subtitle",
                        tint = Color.White
                    )
                }
                
                // Playback Speed Toggle
                TextButton(
                    onClick = {
                        currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
                        onSetPlaybackSpeed(speeds[currentSpeedIndex])
                    }
                ) {
                    Text(
                        text = "${speeds[currentSpeedIndex]}x",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Format milliseconds into HH:MM:SS or MM:SS format.
 */
fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
