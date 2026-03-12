package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nosvedplayer.viewmodel.ControlIconSize
import com.devson.nosvedplayer.viewmodel.SeekBarStyle
import com.devson.nosvedplayer.ui.components.PlaybackSettingsSheet
import kotlinx.coroutines.launch

// Icon size helpers

private val ControlIconSize.buttonSize: Dp
    get() = when (this) {
        ControlIconSize.SMALL  -> 40.dp
        ControlIconSize.MEDIUM -> 52.dp
        ControlIconSize.LARGE  -> 64.dp
    }

private val ControlIconSize.iconSize: Dp
    get() = when (this) {
        ControlIconSize.SMALL  -> 24.dp
        ControlIconSize.MEDIUM -> 36.dp
        ControlIconSize.LARGE  -> 48.dp
    }

private val ControlIconSize.playButtonSize: Dp
    get() = when (this) {
        ControlIconSize.SMALL  -> 52.dp
        ControlIconSize.MEDIUM -> 68.dp
        ControlIconSize.LARGE  -> 84.dp
    }

private val ControlIconSize.playIconSize: Dp
    get() = when (this) {
        ControlIconSize.SMALL  -> 36.dp
        ControlIconSize.MEDIUM -> 52.dp
        ControlIconSize.LARGE  -> 64.dp
    }

// Main composable

@OptIn(ExperimentalMaterial3Api::class)
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
    onToggleResizeMode: (() -> Unit)? = null,
    // Playback settings
    seekDurationSeconds: Int = 10,
    seekBarStyle: SeekBarStyle = SeekBarStyle.DEFAULT,
    controlIconSize: ControlIconSize = ControlIconSize.MEDIUM,
    onSeekDurationChange: ((Int) -> Unit)? = null,
    onSeekBarStyleChange: ((SeekBarStyle) -> Unit)? = null,
    onControlIconSizeChange: ((ControlIconSize) -> Unit)? = null
) {
    var showSettingsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            //  Top gradient 
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
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
                    // Stats toggle
                    IconButton(onClick = { onToggleStats?.invoke() }) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = "Device Stats",
                            tint = if (showStats) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    // Playback Settings button
                    IconButton(onClick = {
                        scope.launch { sheetState.show() }
                        showSettingsSheet = true
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Playback Settings",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }

            //  Bottom gradient + seekbar + controls 
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    var sliderPosition by remember(currentPosition) { mutableFloatStateOf(currentPosition.toFloat()) }

                    // Row 1: Time + Seekbar + Time
                    Row(
                        modifier = Modifier.fillMaxWidth().offset(y = (-6).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatTime(currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.width(8.dp))

                        if (seekBarStyle == SeekBarStyle.FLAT) {
                            //  Flat seek bar 
                            FlatSeekBar(
                                value = sliderPosition,
                                valueRange = 0f..if (duration > 0) duration.toFloat() else 100f,
                                onValueChange = { sliderPosition = it },
                                onValueChangeFinished = { onSeekTo(sliderPosition.toLong()) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            //  Default Material3 Slider 
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
                        }

                        Spacer(Modifier.width(8.dp))
                        val remaining = if (duration > currentPosition) duration - currentPosition else 0L
                        Text(
                            "-" + formatTime(remaining),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Row 2: Lock | Rewind + Play + Forward | Resize
                    Box(modifier = Modifier.fillMaxWidth().offset(y = (-12).dp)) {
                        IconButton(
                            onClick = { /* TODO: Lock controls */ },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = "Lock Controls", tint = Color.White)
                        }

                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onSeekBackward?.invoke() },
                                modifier = Modifier.size(controlIconSize.buttonSize)
                            ) {
                                Icon(
                                    Icons.Filled.FastRewind,
                                    contentDescription = "Rewind ${seekDurationSeconds}s",
                                    tint = Color.White,
                                    modifier = Modifier.size(controlIconSize.iconSize)
                                )
                            }
                            IconButton(
                                onClick = onPlayPauseToggle,
                                modifier = Modifier.size(controlIconSize.playButtonSize)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(controlIconSize.playIconSize)
                                )
                            }
                            IconButton(
                                onClick = { onSeekForward?.invoke() },
                                modifier = Modifier.size(controlIconSize.buttonSize)
                            ) {
                                Icon(
                                    Icons.Filled.FastForward,
                                    contentDescription = "Forward ${seekDurationSeconds}s",
                                    tint = Color.White,
                                    modifier = Modifier.size(controlIconSize.iconSize)
                                )
                            }
                        }

                        if (onToggleResizeMode != null) {
                            IconButton(
                                onClick = onToggleResizeMode,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            ) {
                                Icon(Icons.Filled.Crop, contentDescription = "Toggle Resize Mode", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    //  Playback Settings Bottom Sheet 
    PlaybackSettingsSheet(
        showSettingsSheet = showSettingsSheet,
        sheetState = sheetState,
        seekDurationSeconds = seekDurationSeconds,
        seekBarStyle = seekBarStyle,
        controlIconSize = controlIconSize,
        onDismissRequest = { showSettingsSheet = false },
        onSeekDurationChange = { onSeekDurationChange?.invoke(it) },
        onSeekBarStyleChange = { onSeekBarStyleChange?.invoke(it) },
        onControlIconSizeChange = { onControlIconSizeChange?.invoke(it) }
    )
}

// Flat seek bar (no thumb, thin track)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlatSeekBar(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier.height(16.dp),
        thumb = { /* no thumb */ },
        track = { sliderState ->
            val fraction = (sliderState.value - valueRange.start) /
                    (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(Color.White)
                )
            }
        }
    )
}

// Time formatter

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
