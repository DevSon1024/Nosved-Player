package com.devson.nvplayer.ui.screen

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.devson.nvplayer.player.MPVSurfaceView
import com.devson.nvplayer.player.PlayerState
import com.devson.nvplayer.ui.component.PlayerControls
import com.devson.nvplayer.ui.component.GestureOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import android.media.AudioManager
import android.content.Context
import com.devson.nvplayer.player.TrackInfo
import com.devson.nvplayer.repository.SubtitleFont
import com.devson.nvplayer.repository.PlaybackSettings
import android.provider.MediaStore
import com.devson.nvplayer.ui.component.SubtitleSettingsSideSheet
import com.devson.nvplayer.ui.component.AudioSettingsSideSheet
import com.devson.nvplayer.ui.component.ComposeSubtitleOverlay
import com.devson.nvplayer.ui.component.PlaybackSpeedSideSheet
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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
    playbackSpeed: Float,
    savedBrightness: Float,
    savedVolume: Int,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onCycleSubtitle: () -> Unit,
    onCycleAudio: () -> Unit,
    onBackClick: () -> Unit,
    onSurfaceReady: () -> Unit,
    onSaveBrightness: (Float) -> Unit,
    onSaveVolume: (Int) -> Unit,
    modifier: Modifier = Modifier,
    seekBarStyle: String = "line",
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,
    onNextClick: () -> Unit = {},
    onPrevClick: () -> Unit = {},
    currentSubtitleText: String = "",
    subtitleTracks: List<TrackInfo> = emptyList(),
    audioTracks: List<TrackInfo> = emptyList(),
    playbackSettings: PlaybackSettings = PlaybackSettings(),
    onSelectSubtitleTrack: (Int) -> Unit = {},
    onSelectAudioTrack: (Int) -> Unit = {},
    onSetSubtitleDelay: (Long) -> Unit = {},
    onSeekNextSubtitle: () -> Unit = {},
    onSeekPrevSubtitle: () -> Unit = {},
    onUpdateUseSystemCaptionStyle: (Boolean) -> Unit = {},
    onUpdateSubtitleFont: (SubtitleFont) -> Unit = {},
    onUpdateIsSubtitleBold: (Boolean) -> Unit = {},
    onUpdateForceAssSubtitleOverride: (Boolean) -> Unit = {},
    onUpdateSubtitleTextSizeScale: (Float) -> Unit = {},
    onUpdateSubtitleBgStyle: (Int) -> Unit = {},
    onUpdateSubtitleDelay: (Long) -> Unit = {},
    onUpdateSubtitleVerticalOffset: (Float) -> Unit = {},
    onUpdateSubtitleGesturesEnabled: (Boolean) -> Unit = {},
    onUpdateCustomPlaybackSpeed: (Float) -> Unit = {},
    onUpdateTapAndHoldSpeed: (Float) -> Unit = {},
    onUpdateDoubleTapSeekDuration: (Long) -> Unit = {},
    onUpdateLongPressEnabled: (Boolean) -> Unit = {},
    onUpdateLongPressSpeed: (Float) -> Unit = {},
    onUpdateDoubleTapAction: (com.devson.nvplayer.repository.DoubleTapAction) -> Unit = {}
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var isDragging by remember { mutableStateOf(false) }
    var showSubtitleSettingsSideSheet by remember { mutableStateOf(false) }
    var showAudioSettingsSideSheet by remember { mutableStateOf(false) }
    var showPlaybackSpeedSideSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    //  Pinch-to-Zoom state 
    // videoScale and videoOffset are owned here so they can be applied to the
    // AndroidView via graphicsLayer.  GestureOverlay reports incremental changes
    // (scaleMultiplier, panDelta) via onZoomChange; we accumulate them here.
    var videoScale by remember { mutableStateOf(1f) }
    var videoOffset by remember { mutableStateOf(Offset.Zero) }

    val onZoomChange: (Float, Offset) -> Unit = { scaleMultiplier, pan ->
        val newScale = (videoScale * scaleMultiplier).coerceIn(1f, 6f)
        videoScale = newScale
        if (newScale <= 1.01f) {
            // Snap back to zero offset when fully zoomed out
            videoOffset = Offset.Zero
            videoScale = 1f
        } else {
            // Clamp pan so the video never wanders completely off-screen
            val maxX = (newScale - 1f) * 900f
            val maxY = (newScale - 1f) * 500f
            videoOffset = Offset(
                (videoOffset.x + pan.x).coerceIn(-maxX, maxX),
                (videoOffset.y + pan.y).coerceIn(-maxY, maxY)
            )
        }
    }

    //  Resolve the real video title 
    // For content:// (MediaStore) URIs, lastPathSegment is just the row ID (e.g.
    // "1000551661").  Query ContentResolver for the actual DISPLAY_NAME instead.
    val videoTitle: String = remember(currentUri) {
        if (currentUri == null) return@remember "Local Video"
        // 1. Try ContentResolver (works for content:// MediaStore URIs)
        try {
            context.contentResolver.query(
                currentUri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameCol >= 0) {
                        val fullName = cursor.getString(nameCol) ?: ""
                        // Strip extension
                        val dot = fullName.lastIndexOf('.')
                        return@remember if (dot > 0) fullName.substring(0, dot) else fullName
                    }
                }
            }
        } catch (_: Exception) { }
        // 2. Fall back: use last path segment and strip extension
        val seg = currentUri.lastPathSegment ?: currentUri.toString()
        val name = seg.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot > 0) name.substring(0, dot) else name
    }

    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = remember(context) { context.findActivity() }

    // Define back handler with portrait forcing first
    val handleBack = {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                currentContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                break
            }
            currentContext = currentContext.baseContext
        }
        onBackClick()
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    // Restore saved brightness and volume on startup
    LaunchedEffect(Unit) {
        activity?.let { act ->
            val lp = act.window.attributes
            lp.screenBrightness = savedBrightness
            act.window.attributes = lp
        }
        if (savedVolume >= 0) {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume.coerceIn(0, maxVol), 0)
        }
    }

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

    // Enforce standard vertical layout when leaving PlayerScreen to return to lists and pause audio,
    // and restore default screen brightness. Hide system bars on entry and restore on exit.
    DisposableEffect(Unit) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
            var currentContext = context
            while (currentContext is ContextWrapper) {
                if (currentContext is Activity) {
                    currentContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    val lp = currentContext.window.attributes
                    lp.screenBrightness = -1.0f // Restore default screen brightness
                    currentContext.window.attributes = lp
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
        modifier = modifier.fillMaxSize()
    ) {
        val currentOnSurfaceReady by rememberUpdatedState(onSurfaceReady)

        // ALWAYS keep the AndroidView in the hierarchy so that surface attaches immediately.
        // graphicsLayer applies the zoom/pan state driven by GestureOverlay's onZoomChange.
        // No `transformable` modifier here — it would never receive events because
        // GestureOverlay sits on top and captures all touches first.
        AndroidView(
            factory = { ctx ->
                MPVSurfaceView(ctx).apply {
                    onSurfaceCreatedListener = {
                        currentOnSurfaceReady()
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = videoScale,
                    scaleY = videoScale,
                    translationX = videoOffset.x,
                    translationY = videoOffset.y
                )
        )

        when (playbackState) {
            is PlayerState.Idle -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                    MaterialTheme.colorScheme.background
                                ),
                                radius = 2200f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            is PlayerState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
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
                            onClick = handleBack,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }

            else -> {
                GestureOverlay(
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    playbackSpeed = playbackSpeed,
                    savedBrightness = savedBrightness,
                    savedVolume = savedVolume,
                    onPlayPauseToggle = onPlayPauseToggle,
                    onSeek = onSeek,
                    onSetPlaybackSpeed = onSetPlaybackSpeed,
                    onSaveBrightness = onSaveBrightness,
                    onSaveVolume = onSaveVolume,
                    controlsVisible = controlsVisible,
                    onControlsVisibleChanged = { controlsVisible = it },
                    customPlaybackSpeed = playbackSettings.customPlaybackSpeed,
                    tapAndHoldSpeed = playbackSettings.tapAndHoldSpeed,
                    doubleTapSeekDurationMs = playbackSettings.doubleTapSeekDuration,
                    playbackSettings = playbackSettings,
                    onZoomChange = onZoomChange
                )

                ComposeSubtitleOverlay(
                    subtitleText = currentSubtitleText,
                    textSizeScale = playbackSettings.subtitleTextSizeScale,
                    bgStyle = playbackSettings.subtitleBgStyle,
                    subtitleFont = playbackSettings.subtitleFont,
                    isSubtitleBold = playbackSettings.isSubtitleBold,
                    isSubtitleGestureEnabled = playbackSettings.subtitleGesturesEnabled,
                    verticalOffsetFraction = playbackSettings.subtitleVerticalOffset,
                    onVerticalOffsetFractionChanged = { offset ->
                        onUpdateSubtitleVerticalOffset(offset)
                    },
                    onSeekNext = onSeekNextSubtitle,
                    onSeekPrev = onSeekPrevSubtitle
                )

                // Unified Premium Controls Layer
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    PlayerControls(
                        title = videoTitle,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        isDragging = isDragging,
                        onDraggingChanged = { isDragging = it },
                        onPlayPauseToggle = onPlayPauseToggle,
                        onSeek = onSeek,
                        onSpeedClick = {
                            showPlaybackSpeedSideSheet = true
                        },
                        onCycleSubtitle = {
                            showSubtitleSettingsSideSheet = true
                        },
                        onCycleAudio = {
                            showAudioSettingsSideSheet = true
                        },
                        onBackClick = handleBack,
                        playbackSpeed = playbackSpeed,
                        seekBarStyle = seekBarStyle,
                        hasNext = hasNext,
                        hasPrevious = hasPrevious,
                        onNextClick = onNextClick,
                        onPrevClick = onPrevClick,
                        showSeekButtons = playbackSettings.showSeekButtons,
                        showElapsedTimeOverlay = playbackSettings.showElapsedTimeOverlay,
                        showRemainingTime = playbackSettings.showRemainingTime,
                        showBatteryClockOverlay = playbackSettings.showBatteryClockOverlay,
                        showScreenRotationButton = playbackSettings.showScreenRotationButton,
                        seekDurationSeconds = playbackSettings.seekDurationSeconds,
                        modifier = Modifier
                    )
                }

                // Separate Buffering overlay if video stalls during playback
                if (playbackState is PlayerState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                        modifier = Modifier
                            .size(56.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }

        SubtitleSettingsSideSheet(
            visible = showSubtitleSettingsSideSheet,
            playbackSettings = playbackSettings,
            subtitleTracks = subtitleTracks,
            onSelectSubtitleTrack = onSelectSubtitleTrack,
            onSetSubtitleDelay = onSetSubtitleDelay,
            onUpdateSubtitleFont = onUpdateSubtitleFont,
            onUpdateIsSubtitleBold = onUpdateIsSubtitleBold,
            onUpdateForceAssSubtitleOverride = onUpdateForceAssSubtitleOverride,
            onUpdateSubtitleTextSizeScale = onUpdateSubtitleTextSizeScale,
            onUpdateSubtitleBgStyle = onUpdateSubtitleBgStyle,
            onUpdateSubtitleDelay = onUpdateSubtitleDelay,
            onUpdateSubtitleVerticalOffset = onUpdateSubtitleVerticalOffset,
            onUpdateSubtitleGesturesEnabled = onUpdateSubtitleGesturesEnabled,
            onDismiss = { showSubtitleSettingsSideSheet = false }
        )

        AudioSettingsSideSheet(
            visible = showAudioSettingsSideSheet,
            audioTracks = audioTracks,
            onSelectAudioTrack = onSelectAudioTrack,
            onDismiss = { showAudioSettingsSideSheet = false }
        )

        PlaybackSpeedSideSheet(
            visible = showPlaybackSpeedSideSheet,
            currentSpeed = playbackSpeed,
            playbackSettings = playbackSettings,
            onSpeedSelected = { speed ->
                onUpdateCustomPlaybackSpeed(speed)
            },
            onUpdateDoubleTapAction = onUpdateDoubleTapAction,
            onUpdateDoubleTapSeekDuration = onUpdateDoubleTapSeekDuration,
            onUpdateLongPressEnabled = onUpdateLongPressEnabled,
            onUpdateTapAndHoldSpeed = onUpdateTapAndHoldSpeed,
            onUpdateLongPressSpeed = onUpdateLongPressSpeed,
            onDismiss = { showPlaybackSpeedSideSheet = false }
        )
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