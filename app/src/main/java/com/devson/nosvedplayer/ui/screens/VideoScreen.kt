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
import com.devson.nosvedplayer.ui.components.YoutubeStylePlayerControls
import com.devson.nosvedplayer.ui.components.AudioTrackSheet
import com.devson.nosvedplayer.ui.components.SubtitleSheet
import com.devson.nosvedplayer.ui.components.InformationBottomSheet
import com.devson.nosvedplayer.ui.components.PlaybackSettingsSheet
import com.devson.nosvedplayer.viewmodel.VideoViewModel
import com.devson.nosvedplayer.viewmodel.SettingsViewModel

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
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
    val showSeekButtons by viewModel.showSeekButtons.collectAsState()
    val fastplaySpeed by viewModel.fastplaySpeed.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val currentPlaylistIndex by viewModel.currentPlaylistIndex.collectAsState()
    val isSeeking by viewModel.isSeeking.collectAsState()
    val seekPreviewPosition by viewModel.seekPreviewPosition.collectAsState()

    val displayedPosition = if (isSeeking) seekPreviewPosition else currentPosition

    //  Player style preference 
    // Reads the player UI style set in SettingsScreen (default = false = default style)
    val useYoutubeStyle by settingsViewModel.useYoutubePlayerStyle.collectAsState()

    var isLocked by remember { mutableStateOf(false) }

    // Tracks & Subtitles state
    val audioTracks by viewModel.audioTracks?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val selectedAudioIndex by viewModel.selectedAudioIndex?.collectAsState(initial = -1) ?: remember { mutableStateOf(-1) }
    val isAudioBoostEnabled by viewModel.isAudioBoostEnabled?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }

    val subtitleTracks by viewModel.subtitleTracks?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val selectedSubtitleIndex by viewModel.selectedSubtitleIndex?.collectAsState(initial = -1) ?: remember { mutableStateOf(-1) }

    val subtitleTextSizeScale by viewModel.subtitleTextSizeScale.collectAsState()
    val subtitleBgStyle by viewModel.subtitleBgStyle.collectAsState()

    // Modals state (used only in default mode; YT mode handles tracks inline)
    var showAudioSheet by remember { mutableStateOf(false) }
    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    val audioSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSubtitleSheet by remember { mutableStateOf(false) }
    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    val subtitleSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Shared playback-settings sheet (both YT and default style use the same sheet)
    var showPlaybackSettingsSheet by remember { mutableStateOf(false) }
    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    val playbackSettingsSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showInfoSheet by remember { mutableStateOf(false) }

    val externalSubtitleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "application/x-subrip"
            viewModel.loadExternalSubtitle(uri, mimeType)
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    var volumeLevel by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var accumulatedVolume by remember { mutableStateOf(volumeLevel) }
    var showVolumeFeedback by remember { mutableStateOf(false) }
    var volumeFeedbackTrigger by remember { mutableStateOf(0) }

    var brightnessLevel by remember {
        mutableStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
        )
    }
    var showBrightnessFeedback by remember { mutableStateOf(false) }
    var brightnessFeedbackTrigger by remember { mutableStateOf(0) }

    // YT-style seek indicators and fast-forward - driven by GestureOverlay, consumed by YoutubeStylePlayerControls
    var ytShowSeekLeft by remember { mutableStateOf(false) }
    var ytShowSeekRight by remember { mutableStateOf(false) }
    var ytIsFastForwarding by remember { mutableStateOf(false) }

    LaunchedEffect(ytShowSeekLeft) {
        if (ytShowSeekLeft) { delay(800); ytShowSeekLeft = false }
    }
    LaunchedEffect(ytShowSeekRight) {
        if (ytShowSeekRight) { delay(800); ytShowSeekRight = false }
    }

    LaunchedEffect(volumeFeedbackTrigger) {
        if (volumeFeedbackTrigger > 0) { delay(1000); showVolumeFeedback = false }
    }
    LaunchedEffect(brightnessFeedbackTrigger) {
        if (brightnessFeedbackTrigger > 0) { delay(1000); showBrightnessFeedback = false }
    }

    val originalWindowParams = remember {
        val window = activity?.window
        val insetsController = if (window != null) androidx.core.view.WindowCompat.getInsetsController(window, window.decorView) else null
        @Suppress("DEPRECATION")
        object {
            val statusBarColor = window?.statusBarColor
            val navigationBarColor = window?.navigationBarColor
            val isNavBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window?.isNavigationBarContrastEnforced else null
            val isStatusBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window?.isStatusBarContrastEnforced else null
            val systemBarsBehavior = insetsController?.systemBarsBehavior
            val screenBrightness = window?.attributes?.screenBrightness
            val isAppearanceLightStatusBars = insetsController?.isAppearanceLightStatusBars
            val isAppearanceLightNavigationBars = insetsController?.isAppearanceLightNavigationBars
        }
    }

    val originalOrientation = remember {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    LaunchedEffect(Unit) {
        viewModel.initializePlayer(context)
    }

    LaunchedEffect(playerError) {
        if (playerError != null) {
            Toast.makeText(context, "Playback Error: $playerError", Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    LaunchedEffect(isPortrait) {
        activity ?: return@LaunchedEffect
        val portrait = isPortrait ?: return@LaunchedEffect
        activity.requestedOrientation = if (portrait)
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            insetsController.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
            hideSystemUI(activity)
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                @Suppress("DEPRECATION")
                originalWindowParams.statusBarColor?.let { window.statusBarColor = it }
                @Suppress("DEPRECATION")
                originalWindowParams.navigationBarColor?.let { window.navigationBarColor = it }
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    originalWindowParams.isNavBarContrastEnforced?.let { window.isNavigationBarContrastEnforced = it }
                    originalWindowParams.isStatusBarContrastEnforced?.let { window.isStatusBarContrastEnforced = it }
                }
                originalWindowParams.systemBarsBehavior?.let { insetsController.systemBarsBehavior = it }
                originalWindowParams.isAppearanceLightStatusBars?.let { insetsController.isAppearanceLightStatusBars = it }
                originalWindowParams.isAppearanceLightNavigationBars?.let { insetsController.isAppearanceLightNavigationBars = it }
                val lp = window.attributes
                lp.screenBrightness = originalWindowParams.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
                showSystemUI(activity)
            }
            activity?.requestedOrientation = originalOrientation
        }
    }

    //  Player surface 
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        key(player) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                    }
                },
                update = { playerView ->
                    playerView.player = player
                    playerView.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        //  Gesture overlay (volume / brightness swipe - always active) 
        GestureOverlay(
            modifier = Modifier.fillMaxSize(),
            seekDurationSeconds = seekDurationSeconds,
            isPlaying = isPlaying,
            isLocked = isLocked,
            fastplaySpeed = fastplaySpeed,
            currentPosition = currentPosition,
            duration = duration,
            onSingleTap = { viewModel.toggleControlsVisibility() },
            onDoubleTapLeft = {
                if (useYoutubeStyle) {
                    ytShowSeekLeft = true
                }
                viewModel.seekBackward()
            },
            onDoubleTapCenter = { viewModel.togglePlayPause() },
            onDoubleTapRight = {
                if (useYoutubeStyle) {
                    ytShowSeekRight = true
                }
                viewModel.seekForward()
            },
            onSeekStart = { viewModel.beginSeek() },
            onSeekPreview = { pos -> viewModel.updateSeekPreview(pos) },
            onSeekCommit = { absPos -> viewModel.updateSeekPreview(absPos); viewModel.commitSeek() },
            onFastForwardToggle = { active ->
                ytIsFastForwarding = active
                if (active) viewModel.setPlaybackSpeed(fastplaySpeed)
                else viewModel.setPlaybackSpeed(1f)
            },
            onVolumeSwipe = { delta ->
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                accumulatedVolume += delta
                val newVol = (accumulatedVolume * maxVolume).toInt().coerceIn(0, maxVolume)
                if (newVol != currentVol) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
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
            showBrightnessFeedback = showBrightnessFeedback,
            isAudioBoostEnabled = isAudioBoostEnabled
        )

        //  Device stats overlay 
        DeviceStatsOverlay(
            visible = showStats,
            player = player,
            videoFps = videoFps,
            videoDecoderName = videoDecoderName,
            onDismiss = { viewModel.setShowStats(false) },
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopStart)
        )

        //  Player controls - switch between Default and YouTube style 
        val hasPrevious = currentPlaylist.isNotEmpty() && currentPlaylistIndex > 0
        val hasNext = currentPlaylist.isNotEmpty() && currentPlaylistIndex in 0 until currentPlaylist.lastIndex

        if (useYoutubeStyle) {
            //  YouTube-style controls 
            YoutubeStylePlayerControls(
                isVisible = controlsVisible,
                isPlaying = isPlaying,
                title = currentVideo?.title ?: "",
                currentPosition = displayedPosition,
                duration = duration,
                seekDurationSeconds = seekDurationSeconds,
                seekBarStyle = seekBarStyle,
                fastplaySpeed = fastplaySpeed,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                isLandscape = isLandscape,
                isLocked = isLocked,
                audioTracks = audioTracks,
                selectedAudioIndex = selectedAudioIndex,
                subtitleTracks = subtitleTracks,
                selectedSubtitleIndex = selectedSubtitleIndex,
                playlist = currentPlaylist,
                currentPlaylistIndex = currentPlaylistIndex,
                showSeekLeft = ytShowSeekLeft,
                showSeekRight = ytShowSeekRight,
                isFastForwarding = ytIsFastForwarding,
                showSeekButtons = showSeekButtons,
                onPlayPauseToggle = {
                    viewModel.togglePlayPause()
                    viewModel.showControlsAndDelayHide()
                },
                onSeekTo = { pos ->
                    viewModel.updateSeekPreview(pos)
                    viewModel.commitSeek()
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
                onBack = { (context as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() },
                onToggleLock = {
                    isLocked = !isLocked
                    viewModel.showControlsAndDelayHide()
                },
                onSelectAudio = { viewModel.selectAudioTrack(it) },
                onSelectSubtitle = { viewModel.selectSubtitleTrack(it) },
                onSpeedChange = { /* hook into viewModel if speed is supported */ },
                onPlayPrevious = { viewModel.playPreviousVideo() },
                onPlayNext = { viewModel.playNextVideo() },
                onPlayFromPlaylist = { index ->
                    val playlist = currentPlaylist
                    if (index in playlist.indices) {
                        viewModel.playVideo(playlist[index], playlist)
                    }
                },
                onFastForwardActive = { /* handled by GestureOverlay's onFastForwardToggle */ },
                onToggleResizeMode = {
                    val modeLabel = viewModel.toggleResizeMode()
                    Toast.makeText(context, modeLabel, Toast.LENGTH_SHORT).show()
                    viewModel.showControlsAndDelayHide()
                },
                onPipToggle = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val pipParams = android.app.PictureInPictureParams.Builder().build()
                        activity?.enterPictureInPictureMode(pipParams)
                    }
                },
                onControlsStateChange = { viewModel.toggleControlsVisibility() },
                onOpenPlaybackSettings = { showPlaybackSettingsSheet = true },
                onOpenAudioTracks = { showAudioSheet = true },
                onOpenSubtitles = { showSubtitleSheet = true },
                currentPlaybackSpeed = 1f   // Replace with viewModel.playbackSpeed if available
            )
        } else {
            //  Default controls 
            PlayerControls(
                isVisible = controlsVisible,
                isPlaying = isPlaying,
                title = currentVideo?.title ?: "",
                currentPosition = displayedPosition,
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
                    viewModel.updateSeekPreview(pos)
                    viewModel.commitSeek()
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
                onControlsStateChange = { viewModel.toggleControlsVisibility() },
                onToggleResizeMode = {
                    val modeLabel = viewModel.toggleResizeMode()
                    Toast.makeText(context, modeLabel, Toast.LENGTH_SHORT).show()
                    viewModel.showControlsAndDelayHide()
                },
                seekDurationSeconds = seekDurationSeconds,
                seekBarStyle = seekBarStyle,
                controlIconSize = controlIconSize,
                autoPlayEnabled = autoPlayEnabled,
                showSeekButtons = showSeekButtons,
                fastplaySpeed = fastplaySpeed,
                onSeekDurationChange = { viewModel.setSeekDuration(it) },
                onSeekBarStyleChange = { viewModel.setSeekBarStyle(it) },
                onControlIconSizeChange = { viewModel.setControlIconSize(it) },
                onAutoPlayChange = { viewModel.setAutoPlayEnabled(it) },
                onShowSeekButtonsChange = { viewModel.setShowSeekButtons(it) },
                onFastplaySpeedChange = { viewModel.setFastplaySpeed(it) },
                useYoutubeStyle = useYoutubeStyle,
                onYoutubeStyleChange = { settingsViewModel.setYoutubePlayerStyle(it) },
                onInfoClick = {
                    showInfoSheet = true
                    viewModel.showControlsAndDelayHide()
                },
                onOpenAudioTracks = { showAudioSheet = true },
                onOpenSubtitles = { showSubtitleSheet = true },
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
    }

    //  Modals - shared between both player styles 

    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    AudioTrackSheet(
        showSheet = showAudioSheet,
        sheetState = audioSheetState,
        audioTracks = audioTracks,
        selectedTrackIndex = selectedAudioIndex,
        isLandscape = isLandscape,
        isAudioBoostEnabled = isAudioBoostEnabled,
        onToggleAudioBoost = { viewModel.toggleAudioBoost(it) },
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
        onPickExternalSubtitle = { externalSubtitleLauncher.launch("*/*") },
        onTextSizeChange = { viewModel.updateSubtitleTextSizeScale(it) },
        onBgStyleChange = { viewModel.updateSubtitleBgStyle(it) },
        onDismissRequest = { showSubtitleSheet = false }
    )

    //  Shared Playback Settings Sheet (same sheet for both player styles) 
    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    PlaybackSettingsSheet(
        showSettingsSheet = showPlaybackSettingsSheet,
        sheetState = playbackSettingsSheetState,
        seekDurationSeconds = seekDurationSeconds,
        seekBarStyle = seekBarStyle,
        controlIconSize = controlIconSize,
        autoPlayEnabled = autoPlayEnabled,
        useYoutubeStyle = useYoutubeStyle,
        showSeekButtons = showSeekButtons,
        fastplaySpeed = fastplaySpeed,
        isLandscape = isLandscape,
        onDismissRequest = { showPlaybackSettingsSheet = false },
        onSeekDurationChange = { viewModel.setSeekDuration(it) },
        onSeekBarStyleChange = { viewModel.setSeekBarStyle(it) },
        onControlIconSizeChange = { viewModel.setControlIconSize(it) },
        onAutoPlayChange = { viewModel.setAutoPlayEnabled(it) },
        onYoutubeStyleChange = { settingsViewModel.setYoutubePlayerStyle(it) },
        onShowSeekButtonsChange = { viewModel.setShowSeekButtons(it) },
        onFastplaySpeedChange = { viewModel.setFastplaySpeed(it) },
        showStats = showStats,
        onShowStatsChange = { viewModel.setShowStats(it) }
    )

    if (showInfoSheet) {
        val videoSet = currentVideo?.let { setOf(it) } ?: emptySet()
        InformationBottomSheet(
            selectedVideos = videoSet,
            onDismiss = { showInfoSheet = false },
            useSideSheet = true
        )
    }
}

private fun hideSystemUI(activity: Activity) {
    val window = activity.window
    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
}

private fun showSystemUI(activity: Activity) {
    val window = activity.window
    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).show(
        androidx.core.view.WindowInsetsCompat.Type.systemBars()
    )
}