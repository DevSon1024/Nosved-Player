package com.devson.nvplayer.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
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
    onSpeedClick: () -> Unit,
    onCycleSubtitle: () -> Unit,
    onCycleAudio: () -> Unit,
    onBackClick: () -> Unit,
    playbackSpeed: Float,
    seekBarStyle: String = "line",
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,
    onNextClick: () -> Unit = {},
    onPrevClick: () -> Unit = {},
    showSeekButtons: Boolean = true,
    showElapsedTimeOverlay: Boolean = false,
    showRemainingTime: Boolean = false,
    showBatteryClockOverlay: Boolean = false,
    showScreenRotationButton: Boolean = true,
    seekDurationSeconds: Int = 10,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

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
                if (showElapsedTimeOverlay) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Elapsed: ${formatTime(currentPosition)}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            if (isPortrait) {
                if (showBatteryClockOverlay) {
                    BatteryAndClockOverlay()
                }
            } else {
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

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .clickable { onSpeedClick() }
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
                        val speedText = if (playbackSpeed % 1.0f == 0.0f) {
                            "${playbackSpeed.toInt()}x"
                        } else {
                            String.format(java.util.Locale.US, "%.2fx", playbackSpeed).trimEnd('0').trimEnd('.') + "x"
                        }
                        Text(
                            text = speedText,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }

                if (showScreenRotationButton) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable {
                                activity?.let { act ->
                                    val currentOrientation = act.requestedOrientation
                                    if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                                        currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScreenRotation,
                            contentDescription = "Rotate Screen",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (showBatteryClockOverlay) {
                    Spacer(modifier = Modifier.width(8.dp))
                    BatteryAndClockOverlay()
                }
            }
        }

        // --- 2. CENTER PANEL (Playback Actions) ---
        val centerSpacing = if (isPortrait) 14.dp else 24.dp
        val playCircleSize = if (isPortrait) 68.dp else 76.dp
        val playIconSize = if (isPortrait) 38.dp else 44.dp
        val actionCircleSize = if (isPortrait) 46.dp else 52.dp
        val actionIconSize = if (isPortrait) 22.dp else 26.dp

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(centerSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skip Previous
            Box(
                modifier = Modifier
                    .size(actionCircleSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (hasPrevious) 0.06f else 0.02f))
                    .border(1.dp, Color.White.copy(alpha = if (hasPrevious) 0.1f else 0.03f), CircleShape)
                    .clickable(enabled = hasPrevious) { onPrevClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous Video",
                    tint = Color.White.copy(alpha = if (hasPrevious) 1f else 0.3f),
                    modifier = Modifier.size(actionIconSize)
                )
            }

            // Rewind Button
            if (showSeekButtons) {
                val rewindIcon = when (seekDurationSeconds) {
                    5 -> Icons.Rounded.Replay5
                    30 -> Icons.Rounded.Replay30
                    else -> Icons.Rounded.Replay10
                }
                Box(
                    modifier = Modifier
                        .size(actionCircleSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        .clickable { onSeek((currentPosition - seekDurationSeconds * 1000L).coerceAtLeast(0L)) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = rewindIcon,
                        contentDescription = "Rewind ${seekDurationSeconds}s",
                        tint = Color.White,
                        modifier = Modifier.size(actionIconSize)
                    )
                }
            }

            // Modern Play/Pause Circle Button
            Box(
                modifier = Modifier
                    .size(playCircleSize)
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
                    modifier = Modifier.size(playIconSize)
                )
            }

            // Forward Button
            if (showSeekButtons) {
                val forwardIcon = when (seekDurationSeconds) {
                    5 -> Icons.Rounded.Forward5
                    30 -> Icons.Rounded.Forward30
                    else -> Icons.Rounded.Forward10
                }
                Box(
                    modifier = Modifier
                        .size(actionCircleSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        .clickable { onSeek((currentPosition + seekDurationSeconds * 1000L).coerceAtMost(duration)) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = forwardIcon,
                        contentDescription = "Forward ${seekDurationSeconds}s",
                        tint = Color.White,
                        modifier = Modifier.size(actionIconSize)
                    )
                }
            }

            // Skip Next
            Box(
                modifier = Modifier
                    .size(actionCircleSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (hasNext) 0.06f else 0.02f))
                    .border(1.dp, Color.White.copy(alpha = if (hasNext) 0.1f else 0.03f), CircleShape)
                    .clickable(enabled = hasNext) { onNextClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next Video",
                    tint = Color.White.copy(alpha = if (hasNext) 1f else 0.3f),
                    modifier = Modifier.size(actionIconSize)
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
            if (isPortrait) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Subtitles Quick Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .clickable { onCycleSubtitle() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = "Subtitles",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Subtitles",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Audio Track Quick Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .clickable { onCycleAudio() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Audiotrack,
                                contentDescription = "Audio Track",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Audio",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Playback Speed Quick Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .clickable { onSpeedClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = "Playback Speed",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            val speedText = if (playbackSpeed % 1.0f == 0.0f) {
                                "${playbackSpeed.toInt()}x"
                            } else {
                                String.format(java.util.Locale.US, "%.2fx", playbackSpeed).trimEnd('0').trimEnd('.') + "x"
                            }
                            Text(
                                text = speedText,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Screen Rotation Quick Button
                    if (showScreenRotationButton) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .clickable {
                                    activity?.let { act ->
                                        val currentOrientation = act.requestedOrientation
                                        if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                                            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ScreenRotation,
                                    contentDescription = "Rotate Screen",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Rotate",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Timers Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val durationText = if (showRemainingTime) {
                    "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}"
                } else {
                    formatTime(duration)
                }
                Text(
                    text = "${formatTime(currentPosition)} / $durationText",
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

@Composable
private fun BatteryAndClockOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var batteryPercentage by remember { mutableIntStateOf(100) }
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            batteryPercentage = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            currentTime = timeFormat.format(Date())
            delay(10000L)
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.BatteryChargingFull,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$batteryPercentage%",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = currentTime,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}