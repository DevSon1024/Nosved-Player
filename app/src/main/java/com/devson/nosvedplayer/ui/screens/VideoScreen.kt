package com.devson.nosvedplayer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.devson.nosvedplayer.ui.components.DeviceStatsOverlay
import com.devson.nosvedplayer.ui.components.GestureOverlay
import com.devson.nosvedplayer.ui.components.PlayerControls
import com.devson.nosvedplayer.ui.components.AudioTrackSheet
import com.devson.nosvedplayer.ui.components.SubtitleSheet
import com.devson.nosvedplayer.viewmodel.VideoViewModel

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val player by viewModel.playerInstance.collectAsState()
    val isPlaying by viewModel.isPlaying?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val currentPosition by viewModel.currentPosition?.collectAsState(initial = 0L)
        ?: remember { mutableStateOf(0L) }
    val duration by viewModel.duration?.collectAsState(initial = 0L)
        ?: remember { mutableStateOf(0L) }
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()
    val isPortrait by viewModel.isPortraitVideo?.collectAsState(initial = null)
        ?: remember { mutableStateOf<Boolean?>(null) }

    val showStats by viewModel.showStats.collectAsState()
    val videoFps by viewModel.videoFps?.collectAsState(initial = 0f)
        ?: remember { mutableStateOf(0f) }
    val videoDecoderName by viewModel.videoDecoderName?.collectAsState(initial = null)
        ?: remember { mutableStateOf<String?>(null) }
    val playerError by viewModel.playerError?.collectAsState(initial = null)
        ?: remember { mutableStateOf<String?>(null) }
    
    val resizeMode by viewModel.resizeMode.collectAsState()
    val seekDurationSeconds by viewModel.seekDurationSeconds.collectAsState()
    val seekBarStyle by viewModel.seekBarStyle.collectAsState()
    val controlIconSize by viewModel.controlIconSize.collectAsState()
    val autoPlayEnabled by viewModel.autoPlayEnabled.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val currentPlaylistIndex by viewModel.currentPlaylistIndex.collectAsState()

    var isLocked by remember { mutableStateOf(false) }

    // Tracks & Subtitles state
    val audioTracks by viewModel.audioTracks?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val selectedAudioIndex by viewModel.selectedAudioIndex?.collectAsState(initial = -1) ?: remember { mutableStateOf(-1) }
    
    val subtitleTracks by viewModel.subtitleTracks?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val selectedSubtitleIndex by viewModel.selectedSubtitleIndex?.collectAsState(initial = -1) ?: remember { mutableStateOf(-1) }
    
    val subtitleTextSizeScale by viewModel.subtitleTextSizeScale.collectAsState()
    val subtitleBgStyle by viewModel.subtitleBgStyle.collectAsState()

    // Modals state
    var showAudioSheet by remember { mutableStateOf(false) }
    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    val audioSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var showSubtitleSheet by remember { mutableStateOf(false) }
    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    val subtitleSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val externalSubtitleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "application/x-subrip" // Default if unknown
            viewModel.loadExternalSubtitle(uri, mimeType)
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Track Volume state
    var volumeLevel by remember { 
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var accumulatedVolume by remember { mutableStateOf(volumeLevel) }
    var showVolumeFeedback by remember { mutableStateOf(false) }
    var volumeFeedbackTrigger by remember { mutableStateOf(0) }
    
    // Track Brightness state
    var brightnessLevel by remember { 
        mutableStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
        )
    }
    var showBrightnessFeedback by remember { mutableStateOf(false) }
    var brightnessFeedbackTrigger by remember { mutableStateOf(0) }

    // Auto-hide feedback
    LaunchedEffect(volumeFeedbackTrigger) {
        if (volumeFeedbackTrigger > 0) { delay(1000); showVolumeFeedback = false }
    }
    LaunchedEffect(brightnessFeedbackTrigger) {
        if (brightnessFeedbackTrigger > 0) { delay(1000); showBrightnessFeedback = false }
    }
    // Capture the original window properties when the screen is composed
    val originalWindowParams = remember {
        val window = activity?.window
        val insetsController = if (window != null) androidx.core.view.WindowCompat.getInsetsController(window, window.decorView) else null

        object {
            val statusBarColor = window?.statusBarColor
            val navigationBarColor = window?.navigationBarColor
            val isNavBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window?.isNavigationBarContrastEnforced else null
            val isStatusBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window?.isStatusBarContrastEnforced else null
            val systemBarsBehavior = insetsController?.systemBarsBehavior
            val screenBrightness = window?.attributes?.screenBrightness
        }
    }

    val originalOrientation = remember {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    //  Player initialisation 
    LaunchedEffect(Unit) {
        viewModel.initializePlayer(context)
    }

    //  Error handling 
    LaunchedEffect(playerError) {
        if (playerError != null) {
            Toast.makeText(context, "Playback Error: $playerError", Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    //  Orientation lock based on video dimensions 
    LaunchedEffect(isPortrait) {
        activity ?: return@LaunchedEffect
        val portrait = isPortrait ?: return@LaunchedEffect   // wait until known
        activity.requestedOrientation = if (portrait)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    //  System UI (status bar / nav bar) based on controls visibility 
    LaunchedEffect(controlsVisible) {
        if (activity == null) return@LaunchedEffect
        if (controlsVisible) {
            showSystemUI(activity)
        } else {
            hideSystemUI(activity)
        }
    }

    //  Lifecycle cleanup 
    DisposableEffect(Unit) {
        activity?.window?.let { window ->
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
        }
        onDispose {
            activity?.window?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // Restore original theme colors and behavior
                originalWindowParams.statusBarColor?.let { window.statusBarColor = it }
                originalWindowParams.navigationBarColor?.let { window.navigationBarColor = it }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    originalWindowParams.isNavBarContrastEnforced?.let { window.isNavigationBarContrastEnforced = it }
                    originalWindowParams.isStatusBarContrastEnforced?.let { window.isStatusBarContrastEnforced = it }
                }

                originalWindowParams.screenBrightness?.let { originalBrightness ->
                    val layoutParams = window.attributes
                    layoutParams.screenBrightness = originalBrightness
                    window.attributes = layoutParams
                }

                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                originalWindowParams.systemBarsBehavior?.let { insetsController.systemBarsBehavior = it }
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = originalOrientation
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
                        this.resizeMode = resizeMode
                        this.player = player
                    }
                },
                update = { view -> 
                    if (view.player != player) view.player = player 
                    if (view.resizeMode != resizeMode) view.resizeMode = resizeMode

                    // Apply Subtitle Customizations
                    val fgColor = android.graphics.Color.WHITE
                    val bgColorInt = when(subtitleBgStyle) {
                        1 -> android.graphics.Color.parseColor("#80000000") // Semi-transparent
                        2 -> android.graphics.Color.parseColor("#FF000000") // Opaque
                        else -> android.graphics.Color.TRANSPARENT // None
                    }
                    val captionStyle = androidx.media3.ui.CaptionStyleCompat(
                        fgColor, 
                        bgColorInt, 
                        android.graphics.Color.TRANSPARENT, 
                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, 
                        android.graphics.Color.BLACK, 
                        android.graphics.Typeface.DEFAULT
                    )
                    view.subtitleView?.setStyle(captionStyle)
                    view.subtitleView?.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleTextSizeScale)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Gesture overlay
        GestureOverlay(
            modifier = Modifier.fillMaxSize(),
            seekDurationSeconds = seekDurationSeconds,
            isPlaying = isPlaying,
            isLocked = isLocked,
            onSingleTap = { viewModel.toggleControlsVisibility() },
            onDoubleTapLeft = { viewModel.seekBackward() },
            onDoubleTapCenter = { viewModel.togglePlayPause() },
            onDoubleTapRight = { viewModel.seekForward() },
            onSeekCommit = { deltaMs -> 
                viewModel.seekByOffset(deltaMs)
            },
            onFastForwardToggle = { active ->
                viewModel.setPlaybackSpeed(if (active) 2.0f else 1.0f)
            },
            onVolumeSwipe = { delta ->
                accumulatedVolume = (accumulatedVolume + delta).coerceIn(0f, 1f)
                val newVol = (accumulatedVolume * maxVolume).toInt()
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                
                if (newVol != currentVol) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                    // Read back to respect Safe Volume limits
                    val actualVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (actualVol < newVol) {
                        accumulatedVolume = actualVol.toFloat() / maxVolume
                    }
                    volumeLevel = actualVol.toFloat() / maxVolume
                }
                showVolumeFeedback = true
                volumeFeedbackTrigger++
            },
            onBrightnessSwipe = { delta ->
                val window = activity?.window
                if (window != null) {
                    val lp = window.attributes
                    val currentBrightness = if (lp.screenBrightness >= 0f) lp.screenBrightness else 0.5f
                    val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
                    lp.screenBrightness = newBrightness
                    window.attributes = lp
                    brightnessLevel = newBrightness
                    showBrightnessFeedback = true
                    brightnessFeedbackTrigger++
                }
            },
            volumeLevel = volumeLevel,
            brightnessLevel = brightnessLevel,
            showVolumeFeedback = showVolumeFeedback,
            showBrightnessFeedback = showBrightnessFeedback
        )

        // 3. Device stats overlay - anchored to right edge, independent of controls
        DeviceStatsOverlay(
            visible = showStats,
            videoFps = videoFps,
            videoDecoderName = videoDecoderName,
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
        )

        // 4. Player controls
        val hasPrevious = currentPlaylist.isNotEmpty() && currentPlaylistIndex > 0
        val hasNext = currentPlaylist.isNotEmpty() && currentPlaylistIndex in 0 until currentPlaylist.lastIndex

        PlayerControls(
            isVisible = controlsVisible,
            isPlaying = isPlaying,
            title = currentVideo?.title ?: "",
            currentPosition = currentPosition,
            duration = duration,
            showStats = showStats,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            onBack = { (context as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() },
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
                viewModel.toggleControlsVisibility()
            },
            onToggleResizeMode = {
                val modeLabel = viewModel.toggleResizeMode()
                Toast.makeText(context, modeLabel, Toast.LENGTH_SHORT).show()
                viewModel.showControlsAndDelayHide()
            },
            // Playback settings
            seekDurationSeconds = seekDurationSeconds,
            seekBarStyle = seekBarStyle,
            controlIconSize = controlIconSize,
            autoPlayEnabled = autoPlayEnabled,
            onSeekDurationChange = { viewModel.setSeekDuration(it) },
            onSeekBarStyleChange = { viewModel.setSeekBarStyle(it) },
            onControlIconSizeChange = { viewModel.setControlIconSize(it) },
            onAutoPlayChange = { viewModel.setAutoPlayEnabled(it) },
            // Audio & Subtitles
            onOpenAudioTracks = { showAudioSheet = true },
            onOpenSubtitles = { showSubtitleSheet = true },
            // Playlist Navigation
            onPlayPrevious = { viewModel.playPreviousVideo() },
            onPlayNext = { viewModel.playNextVideo() },
            isLandscape = isLandscape,
            isLocked = isLocked,
            onToggleLock = { 
                isLocked = !isLocked
                viewModel.showControlsAndDelayHide()
            },
            onPipToggle = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pipParams = android.app.PictureInPictureParams.Builder().build()
                    activity?.enterPictureInPictureMode(pipParams)
                }
            }
        )
    }

    // --- Modals ---

    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    AudioTrackSheet(
        showSheet = showAudioSheet,
        sheetState = audioSheetState,
        audioTracks = audioTracks,
        selectedTrackIndex = selectedAudioIndex,
        isLandscape = isLandscape,
        onSelectTrack = { viewModel.selectAudioTrack(it) },
        onDismissRequest = { showAudioSheet = false }
    )

    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    SubtitleSheet(
        showSheet = showSubtitleSheet,
        sheetState = subtitleSheetState,
        subtitleTracks = subtitleTracks,
        selectedTrackIndex = selectedSubtitleIndex,
        textSizeScale = subtitleTextSizeScale,
        bgStyle = subtitleBgStyle,
        isLandscape = isLandscape,
        onSelectTrack = { viewModel.selectSubtitleTrack(it) },
        onPickExternalSubtitle = { 
            externalSubtitleLauncher.launch("*/*")
        },
        onTextSizeChange = { viewModel.updateSubtitleTextSizeScale(it) },
        onBgStyleChange = { viewModel.updateSubtitleBgStyle(it) },
        onDismissRequest = { showSubtitleSheet = false }
    )
}

/**
 * Hides status bar and navigation bar (immersive fullscreen).
 */
private fun hideSystemUI(activity: Activity) {
    val window = activity.window
    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Restores status bar and navigation bar.
 */
private fun showSystemUI(activity: Activity) {
    val window = activity.window
    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).show(
        androidx.core.view.WindowInsetsCompat.Type.systemBars()
    )
}
