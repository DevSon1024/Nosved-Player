package com.devson.nvplayer.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isDragging: Boolean,
    onDraggingChanged: (Boolean) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onCycleSubtitle: () -> Unit,
    onCycleAudio: () -> Unit,
    onBackClick: () -> Unit,
    playbackSpeed: Float,
    seekBarStyle: String = "line",
    modifier: Modifier = Modifier
) {
    val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
    val currentSpeedIndex = remember(playbackSpeed) { speeds.indexOf(playbackSpeed).coerceAtLeast(0) }

    // Decouple local slider state to stop the visual slider jumping backwards while dragging
    val safeDuration = duration.coerceAtLeast(1L).toFloat()
    var sliderPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }
    var draggingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentPosition) {
        if (!isDragging) {
            sliderPosition = currentPosition.toFloat()
        }
    }

    val safeSliderPos = sliderPosition.coerceIn(0f, safeDuration)
    val playScale by animateFloatAsState(targetValue = if (isPlaying) 1.0f else 1.05f, label = "PlayScale")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.65f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.8f)
                    )
                )
            )
    ) {
        // --- 1. TOP PANEL (Title & Track Selectors) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .clickable { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Go Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .clickable { onCycleSubtitle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Subtitles,
                    contentDescription = "Subtitles",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .clickable { onCycleAudio() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Audiotrack,
                    contentDescription = "Audio Track",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Speed Selection Toggle Button with Premium Glassmorphism
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .clickable {
                        val nextIndex = (currentSpeedIndex + 1) % speeds.size
                        onSetPlaybackSpeed(speeds[nextIndex])
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = "Playback Speed",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${speeds[currentSpeedIndex]}x",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // --- 2. CENTER PANEL (Playback Actions) ---
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rewind 10 Seconds
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    .clickable { onSeek((currentPosition - 10000L).coerceAtLeast(0L)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Replay10,
                    contentDescription = "Rewind 10s",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Modern Play/Pause Circle Button
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.05f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .scale(playScale)
                    .clickable { onPlayPauseToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            // Forward 10 Seconds
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    .clickable { onSeek((currentPosition + 10000L).coerceAtMost(duration)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Forward10,
                    contentDescription = "Forward 10s",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // --- 3. BOTTOM PANEL (Timers, Seekbar & Up Next Hint) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        ) {
            // Timers Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val themePrimary = MaterialTheme.colorScheme.primary

            // Premium Theme Slider Seekbar
            Slider(
                value = safeSliderPos,
                onValueChange = { newVal ->
                    draggingJob?.cancel()
                    onDraggingChanged(true)
                    sliderPosition = newVal
                    onSeek(newVal.toLong())
                },
                onValueChangeFinished = {
                    onSeek(sliderPosition.toLong())
                    draggingJob = scope.launch {
                        delay(800) // Provides ExoPlayer/MPV debounce buffering window
                        onDraggingChanged(false)
                    }
                },
                valueRange = 0f..safeDuration,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = themePrimary,
                    activeTrackColor = themePrimary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                thumb = { _ ->
                    Box(
                        modifier = Modifier
                            .size(if (isDragging) 16.dp else 12.dp)
                            .clip(CircleShape)
                            .background(themePrimary)
                    )
                },
                track = { sliderState ->
                    val rawFraction = ((sliderState.value - 0f) / safeDuration)
                    val safeFraction = if (rawFraction.isNaN()) 0f else rawFraction.coerceIn(0f, 1f)
                    
                    when (seekBarStyle) {
                        "wavy" -> {
                            val infiniteTransition = rememberInfiniteTransition(label = "waveShift")
                            val phaseShift by if (isPlaying) {
                                infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = (2f * Math.PI).toFloat(),
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1500, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "wavePhase"
                                )
                            } else {
                                remember { mutableStateOf(0f) }
                            }

                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                            ) {
                                val width = size.width
                                val height = size.height
                                val centerY = height / 2
                                val activeWidth = width * safeFraction
                                
                                val amplitude = 4.dp.toPx()
                                val wavelength = 20.dp.toPx()
                                
                                // Draw active wavy track
                                if (activeWidth > 0) {
                                    val activePath = Path().apply {
                                        moveTo(0f, centerY)
                                        var x = 0f
                                        while (x <= activeWidth) {
                                            val y = centerY + amplitude * kotlin.math.sin((2 * Math.PI * x / wavelength).toFloat() - phaseShift)
                                            lineTo(x, y)
                                            x += 2f
                                        }
                                        val lastY = centerY + amplitude * kotlin.math.sin((2 * Math.PI * activeWidth / wavelength).toFloat() - phaseShift)
                                        lineTo(activeWidth, lastY)
                                    }
                                    drawPath(
                                        path = activePath,
                                        color = themePrimary,
                                        style = Stroke(
                                            width = 3.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    )
                                }
                                
                                // Draw inactive flat track
                                if (activeWidth < width) {
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.25f),
                                        start = Offset(activeWidth, centerY),
                                        end = Offset(width, centerY),
                                        strokeWidth = 4.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                        "thick" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.25f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(safeFraction.coerceAtLeast(0.001f))
                                            .height(8.dp)
                                            .background(themePrimary)
                                    )
                                }
                            }
                        }
                        else -> {
                            // Standard/Line Style
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.25f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(safeFraction.coerceAtLeast(0.001f))
                                            .height(4.dp)
                                            .background(themePrimary)
                                    )
                                }
                            }
                        }
                    }
                }
            )

            // Up Next Footer Drag Handle styling
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Up Next",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Up Next",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

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