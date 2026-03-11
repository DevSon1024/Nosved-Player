package com.devson.nosvedplayer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.devson.nosvedplayer.ui.components.DeviceStatsOverlay
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

    val player by viewModel.playerInstance.collectAsState()
    val isPlaying by viewModel.isPlaying?.collectAsState(initial = false)
        ?: remember { androidx.compose.runtime.mutableStateOf(false) }
    val currentPosition by viewModel.currentPosition?.collectAsState(initial = 0L)
        ?: remember { androidx.compose.runtime.mutableStateOf(0L) }
    val duration by viewModel.duration?.collectAsState(initial = 0L)
        ?: remember { androidx.compose.runtime.mutableStateOf(0L) }
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()
    val isPortrait by viewModel.isPortraitVideo?.collectAsState(initial = null)
        ?: remember { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }

    val showStats by viewModel.showStats.collectAsState()
    val videoFps by viewModel.videoFps?.collectAsState(initial = 0f)
        ?: remember { androidx.compose.runtime.mutableStateOf(0f) }
    val playerError by viewModel.playerError?.collectAsState(initial = null)
        ?: remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val originalOrientation = remember {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // ── Player initialisation ─────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.initializePlayer(context)
    }

    // ── Error handling ────────────────────────────────────────────────────
    LaunchedEffect(playerError) {
        if (playerError != null) {
            Toast.makeText(context, "Playback Error: $playerError", Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // ── Orientation lock based on video dimensions ────────────────────────
    LaunchedEffect(isPortrait) {
        activity ?: return@LaunchedEffect
        val portrait = isPortrait ?: return@LaunchedEffect   // wait until known
        activity.requestedOrientation = if (portrait)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // ── System UI (status bar / nav bar) based on controls visibility ─────
    LaunchedEffect(controlsVisible) {
        if (activity == null) return@LaunchedEffect
        if (controlsVisible) {
            showSystemUI(activity)
        } else {
            hideSystemUI(activity)
        }
    }

    // ── Lifecycle cleanup ─────────────────────────────────────────────────
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = originalOrientation
            if (activity != null) showSystemUI(activity)  // always restore system UI on exit
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Video surface
        key(player) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.player = player
                    }
                },
                update = { view -> if (view.player != player) view.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Gesture overlay
        GestureOverlay(
            modifier = Modifier.fillMaxSize(),
            onSingleTap = { viewModel.toggleControlsVisibility() },
            onDoubleTapLeft = { viewModel.seekBackward(); viewModel.showControlsAndDelayHide() },
            onDoubleTapRight = { viewModel.seekForward(); viewModel.showControlsAndDelayHide() },
            onVolumeSwipe = { delta ->
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val newVol = (current + (delta * maxVolume)).toInt().coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
            },
            onBrightnessSwipe = { delta ->
                val window = activity?.window ?: return@GestureOverlay
                val lp = window.attributes
                val current = if (lp.screenBrightness >= 0f) lp.screenBrightness else 0.5f
                lp.screenBrightness = (current + delta).coerceIn(0.01f, 1f)
                window.attributes = lp
            }
        )

        // 3. Device stats overlay — anchored to right edge, independent of controls
        DeviceStatsOverlay(
            visible = showStats,
            videoFps = videoFps,
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
        )

        // 4. Player controls
        PlayerControls(
            isVisible = controlsVisible,
            isPlaying = isPlaying,
            title = currentVideo?.title ?: "",
            currentPosition = currentPosition,
            duration = duration,
            showStats = showStats,
            onToggleStats = { viewModel.toggleStats() },
            onPlayPauseToggle = {
                viewModel.togglePlayPause()
                viewModel.showControlsAndDelayHide()
            },
            onSeekTo = { pos ->
                viewModel.seekTo(pos)
                viewModel.showControlsAndDelayHide()
            },
            onSeekForward = {
                viewModel.seekForward()
                viewModel.showControlsAndDelayHide()
            },
            onSeekBackward = {
                viewModel.seekBackward()
                viewModel.showControlsAndDelayHide()
            },
            onControlsStateChange = {
                viewModel.showControlsAndDelayHide()
            }
        )
    }
}

/**
 * Hides status bar and navigation bar (immersive fullscreen).
 */
private fun hideSystemUI(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.let { ctrl ->
            ctrl.hide(WindowInsets.Type.systemBars())
            ctrl.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }
}

/**
 * Restores status bar and navigation bar.
 */
private fun showSystemUI(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.show(WindowInsets.Type.systemBars())
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}
