package com.devson.nvplayer.ui.screen

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.devson.nvplayer.player.MPVSurfaceView
import com.devson.nvplayer.player.PlayerState
import com.devson.nvplayer.ui.component.PlayerControls
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    playbackState: PlayerState,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    currentUri: Uri?,
    videoWidth: Long,
    videoHeight: Long,
    videoRotation: Long,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onCycleSubtitle: () -> Unit,
    onCycleAudio: () -> Unit,
    onBackClick: () -> Unit,
    onSurfaceReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deepCharcoal = Color(0xFF0F0F11)
    val obsidian = Color(0xFF050505)
    val neonCyan = Color(0xFF00E5FF)

    var controlsVisible by remember { mutableStateOf(true) }
    var isDragging by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Dynamically adjust screen orientation to fit the natural display orientation of the loaded video
    LaunchedEffect(videoWidth, videoHeight, videoRotation) {
        if (videoWidth > 0 && videoHeight > 0) {
            var currentContext = context
            while (currentContext is ContextWrapper) {
                if (currentContext is Activity) {
                    val isRotated = videoRotation == 90L || videoRotation == 270L
                    val displayWidth = if (isRotated) videoHeight else videoWidth
                    val displayHeight = if (isRotated) videoWidth else videoHeight

                    if (displayWidth > displayHeight) {
                        // Landscape video -> Rotate screen to sensor landscape layout
                        currentContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        // Portrait video -> Keep screen in vertical portrait layout
                        currentContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    break
                }
                currentContext = currentContext.baseContext
            }
        }
    }

    val currentOnPlayPauseToggle by rememberUpdatedState(onPlayPauseToggle)
    val currentIsPlaying by rememberUpdatedState(isPlaying)

    // FIX: Enforce standard vertical layout when leaving PlayerScreen to return to lists and pause audio
    DisposableEffect(Unit) {
        onDispose {
            var currentContext = context
            while (currentContext is ContextWrapper) {
                if (currentContext is Activity) {
                    currentContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    break
                }
                currentContext = currentContext.baseContext
            }
            if (currentIsPlaying) {
                currentOnPlayPauseToggle()
            }
        }
    }

    // Auto-hide controls after 3 seconds of inactivity during active playback, unless actively seeking
    LaunchedEffect(controlsVisible, isPlaying, isDragging) {
        if (controlsVisible && isPlaying && !isDragging) {
            delay(3000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        val currentOnSurfaceReady by rememberUpdatedState(onSurfaceReady)

        // ALWAYS keep the AndroidView in the hierarchy so that surface attaches immediately
        AndroidView(
            factory = { ctx ->
                MPVSurfaceView(ctx).apply {
                    onSurfaceCreatedListener = {
                        currentOnSurfaceReady()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        when (playbackState) {
            is PlayerState.Idle -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF1E1035), deepCharcoal),
                                radius = 2200f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = neonCyan)
                }
            }

            is PlayerState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(obsidian),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Playback Error",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = playbackState.message,
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = onBackClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2C2C2C),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }

            else -> {
                // Unified Premium Controls Layer
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    PlayerControls(
                        title = currentUri?.lastPathSegment ?: "Local Video",
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        isDragging = isDragging,
                        onDraggingChanged = { isDragging = it },
                        onPlayPauseToggle = onPlayPauseToggle,
                        onSeek = onSeek,
                        onSetPlaybackSpeed = onSetPlaybackSpeed,
                        onCycleSubtitle = onCycleSubtitle,
                        onCycleAudio = onCycleAudio,
                        onBackClick = onBackClick,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Stops outer click propagation */ }
                    )
                }

                // Separate Buffering overlay if video stalls during playback
                if (playbackState is PlayerState.Loading) {
                    CircularProgressIndicator(
                        color = neonCyan,
                        strokeWidth = 4.dp,
                        modifier = Modifier
                            .size(56.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}