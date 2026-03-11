package com.devson.nosvedplayer.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.devson.nosvedplayer.ui.components.GestureOverlay
import com.devson.nosvedplayer.ui.components.PlayerControls
import com.devson.nosvedplayer.viewmodel.VideoViewModel

@Composable
fun VideoScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    val isPlaying by viewModel.isPlaying?.collectAsState(initial = false) ?: remember { androidx.compose.runtime.mutableStateOf(false) }
    val currentPosition by viewModel.currentPosition?.collectAsState(initial = 0L) ?: remember { androidx.compose.runtime.mutableStateOf(0L) }
    val duration by viewModel.duration?.collectAsState(initial = 0L) ?: remember { androidx.compose.runtime.mutableStateOf(0L) }
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    LaunchedEffect(Unit) {
        viewModel.initializePlayer(context)
    }

    DisposableEffect(Unit) {
        // Keep screen on while playing video
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Video Player Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // We provide custom controls
                    player = viewModel.getPlayer()
                }
            },
            update = { view ->
                view.player = viewModel.getPlayer()
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Gesture Overlay
        GestureOverlay(
            modifier = Modifier.fillMaxSize(),
            onSingleTap = {
                viewModel.toggleControlsVisibility()
            },
            onDoubleTapLeft = {
                viewModel.seekBackward()
                viewModel.showControlsAndDelayHide()
            },
            onDoubleTapRight = {
                viewModel.seekForward()
                viewModel.showControlsAndDelayHide()
            },
            onVolumeSwipe = { delta ->
                // Delta is normalized between -1.0 and 1.0 (approximate depending on swipe length)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val volumeChange = (delta * maxVolume * 0.1f).toInt() // Adjust sensitivity here
                
                val newVolume = (currentVolume + volumeChange).coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_SHOW_UI)
            },
            onBrightnessSwipe = { delta ->
                val window = activity?.window ?: return@GestureOverlay
                val layoutParams = window.attributes
                // Brightness bounds: 0.0 to 1.0
                val currentBrightness = if (layoutParams.screenBrightness >= 0) layoutParams.screenBrightness else 0.5f // Default to 0.5 if system default
                layoutParams.screenBrightness = (currentBrightness + (delta * 0.1f)).coerceIn(0f, 1f)
                window.attributes = layoutParams
            }
        )

        // 3. Custom Controls
        PlayerControls(
            isVisible = controlsVisible,
            isPlaying = isPlaying,
            title = currentVideo?.title ?: "",
            currentPosition = currentPosition,
            duration = duration,
            onPlayPauseToggle = {
                viewModel.togglePlayPause()
                viewModel.showControlsAndDelayHide()
            },
            onSeekTo = { pos ->
                viewModel.seekTo(pos)
                viewModel.showControlsAndDelayHide()
            },
            onControlsStateChange = {
                viewModel.showControlsAndDelayHide()
            }
        )
    }
}
