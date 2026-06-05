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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.devson.nvplayer.player.MPVSurfaceView
import com.devson.nvplayer.player.PlayerState
import com.devson.nvplayer.ui.component.PlayerControls
import com.devson.nvplayer.ui.component.GestureOverlay
import com.devson.nvplayer.ui.component.AspectIcons
import com.devson.nvplayer.model.PlayerButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import android.media.AudioManager
import android.content.Context
import com.devson.nvplayer.player.TrackInfo
import com.devson.nvplayer.repository.SubtitleFont
import com.devson.nvplayer.repository.PlaybackSettings
import com.devson.nvplayer.repository.EnhanceMode
import android.provider.MediaStore
import com.devson.nvplayer.ui.component.SubtitleSettingsSideSheet
import com.devson.nvplayer.ui.component.AudioSettingsSideSheet
import com.devson.nvplayer.ui.component.QualitySettingsSideSheet
import com.devson.nvplayer.ui.component.ComposeSubtitleOverlay
import com.devson.nvplayer.ui.component.PlayerSettingsSideSheet
import com.devson.nvplayer.ui.component.EnhanceSettingsSideSheet
import com.devson.nvplayer.player.ChapterInfo
import com.devson.nvplayer.ui.component.ChaptersSideSheet
import com.devson.nvplayer.player.DecoderMode
import com.devson.nvplayer.ui.component.DecoderSideSheet
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.BatteryManager
import android.content.res.Configuration
import android.os.Build
import android.content.Intent
import com.devson.nvplayer.player.MediaPlaybackService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.devson.nvplayer.ui.component.formatTime
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    onSeek: (Long, Boolean) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onCycleSubtitle: () -> Unit,
    onCycleAudio: () -> Unit,
    onBackClick: () -> Unit,
    onSurfaceReady: () -> Unit,
    onSaveBrightness: (Float) -> Unit,
    onSaveVolume: (Int) -> Unit,
    modifier: Modifier = Modifier,
    seekBarStyle: String = "standard",
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,
    onNextClick: () -> Unit = {},
    onPrevClick: () -> Unit = {},
    currentSubtitleText: String = "",
    subtitleTracks: List<TrackInfo> = emptyList(),
    audioTracks: List<TrackInfo> = emptyList(),
    audioBoosterEnabled: Boolean = false,
    audioBoostVolume: Int = 100,
    onToggleAudioBooster: (Boolean) -> Unit = {},
    onSetAudioBoostVolume: (Int) -> Unit = {},
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
    onUpdateDoubleTapAction: (com.devson.nvplayer.repository.DoubleTapAction) -> Unit = {},
    onUpdateOrientationMode: (com.devson.nvplayer.repository.OrientationMode) -> Unit = {},
    onUpdateFullScreenMode: (com.devson.nvplayer.repository.FullScreenMode) -> Unit = {},
    onUpdateSoftButtonMode: (com.devson.nvplayer.repository.SoftButtonMode) -> Unit = {},
    onUpdateControlIconSize: (String) -> Unit = {},
    onUpdateSeekBarStyle: (String) -> Unit = {},
    onUpdateAutoPlayEnabled: (Boolean) -> Unit = {},
    onUpdateShowSeekButtons: (Boolean) -> Unit = {},
    onUpdateShowNextPrevButtons: (Boolean) -> Unit = {},
    onUpdateShowElapsedTimeOverlay: (Boolean) -> Unit = {},
    onUpdateShowRemainingTime: (Boolean) -> Unit = {},
    onUpdateShowBatteryClockOverlay: (Boolean) -> Unit = {},
    onUpdateShowScreenRotationButton: (Boolean) -> Unit = {},
    onUpdatePauseWhenObstructed: (Boolean) -> Unit = {},
    onUpdateKeepAwakeAlways: (Boolean) -> Unit = {},
    onUpdateEnhanceMode: (EnhanceMode) -> Unit = {},
    onUpdateEnhanceSaturation: (Int) -> Unit = {},
    onUpdateEnhanceContrast: (Int) -> Unit = {},
    onUpdateEnhanceBrightness: (Int) -> Unit = {},
    onUpdateEnhanceGamma: (Int) -> Unit = {},
    onUpdateEnhanceHue: (Int) -> Unit = {},
    chapters: List<ChapterInfo> = emptyList(),
    onSelectChapter: (Int) -> Unit = {},
    currentDecoder: String = "AUTO",
    onUpdateDecoderMode: (DecoderMode) -> Unit = {},
    isHwSupported: Boolean = true,
    onTakeVideoScreenshot: () -> Unit = {},
    onCycleAspectMode: () -> Unit = {},
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    onUpdateBackgroundPlayEnabled: (Boolean) -> Unit = {},
    networkSpeedBytesPerSec: Long = 0L,
    bufferDurationSeconds: Double = 0.0,
    isNetworkStream: Boolean = false,
    bufferedPosition: Long = 0L,
    viewModel: com.devson.nvplayer.viewmodel.PlayerViewModel? = null
) {
    val localContext = LocalContext.current
    val owner = localContext.findActivity() as? androidx.lifecycle.ViewModelStoreOwner
    val resolvedViewModel = remember(owner) {
        owner?.let { androidx.lifecycle.ViewModelProvider(it)[com.devson.nvplayer.viewmodel.PlayerViewModel::class.java] }
    }
    val activeViewModel = viewModel ?: resolvedViewModel
    val bufferedPosition by (activeViewModel?.bufferedPosition ?: kotlinx.coroutines.flow.MutableStateFlow(bufferedPosition)).collectAsStateWithLifecycle()
    val engineMediaTitle by (activeViewModel?.mediaTitle ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var showSubtitleSettingsSideSheet by remember { mutableStateOf(false) }
    var showAudioSettingsSideSheet by remember { mutableStateOf(false) }
    var showPlayerSettingsSideSheet by remember { mutableStateOf(false) }
    var showChaptersSideSheet by remember { mutableStateOf(false) }
    var showDecoderSideSheet by remember { mutableStateOf(false) }
    var showEnhanceSettingsSideSheet by remember { mutableStateOf(false) }
    var showQualitySideSheet by remember { mutableStateOf(false) }

    val topLeftButtons = remember(playbackSettings.topLeftControls) {
        // Landscape TopLeft: always starts with BACK_ARROW + VIDEO_TITLE (non-editable anchor)
        val parsed = playbackSettings.topLeftControls.split(',').mapNotNull { runCatching { PlayerButton.valueOf(it) }.getOrNull() }
        listOf(PlayerButton.BACK_ARROW, PlayerButton.VIDEO_TITLE) +
                parsed.filter { it != PlayerButton.BACK_ARROW && it != PlayerButton.VIDEO_TITLE }
    }
    val topRightButtons = remember(playbackSettings.topRightControls) {
        playbackSettings.topRightControls.split(',').mapNotNull { runCatching { PlayerButton.valueOf(it) }.getOrNull() }
    }
    val bottomLeftButtons = remember(playbackSettings.bottomLeftControls) {
        playbackSettings.bottomLeftControls.split(',').mapNotNull { runCatching { PlayerButton.valueOf(it) }.getOrNull() }
    }
    val bottomRightButtons = remember(playbackSettings.bottomRightControls) {
        playbackSettings.bottomRightControls.split(',').mapNotNull { runCatching { PlayerButton.valueOf(it) }.getOrNull() }
    }
    // Portrait TopLeft: always starts with BACK_ARROW + VIDEO_TITLE (non-editable anchor)
    val portraitTopLeftButtons = remember(playbackSettings.portraitTopLeftControls) {
        val parsed = playbackSettings.portraitTopLeftControls.split(',').mapNotNull { runCatching { PlayerButton.valueOf(it) }.getOrNull() }
        listOf(PlayerButton.BACK_ARROW, PlayerButton.VIDEO_TITLE) +
                parsed.filter { it != PlayerButton.BACK_ARROW && it != PlayerButton.VIDEO_TITLE }
    }
    val portraitTopRightButtons = remember(playbackSettings.portraitTopRightControls) {
        playbackSettings.portraitTopRightControls.split(',').mapNotNull { runCatching { PlayerButton.valueOf(it) }.getOrNull() }
    }
    val portraitBottomButtons = remember(playbackSettings.portraitBottomControls) {
        playbackSettings.portraitBottomControls.split(',').mapNotNull { runCatching { PlayerButton.valueOf(it) }.getOrNull() }
    }
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current

    // HUD overlay state for Aspect Ratio changes
    var aspectOverlayText by remember { mutableStateOf<String?>(null) }
    var isFirstAspectEmission by remember { mutableStateOf(true) }

    LaunchedEffect(playbackSettings.aspectMode) {
        if (isFirstAspectEmission) {
            isFirstAspectEmission = false
        } else {
            aspectOverlayText = when (playbackSettings.aspectMode) {
                com.devson.nvplayer.player.AspectMode.FIT -> "Fit Screen"
                com.devson.nvplayer.player.AspectMode.STRETCH -> "Stretch"
                com.devson.nvplayer.player.AspectMode.CROP -> "Crop"
                com.devson.nvplayer.player.AspectMode.ORIGINAL -> "100% Original"
            }
        }
    }

    LaunchedEffect(aspectOverlayText) {
        if (aspectOverlayText != null) {
            delay(500L)
            aspectOverlayText = null
        }
    }

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
    val videoTitle: String = remember(currentUri, engineMediaTitle) {
        if (currentUri == null) return@remember "Local Video"
        
        // 1. If it's a content URI, try ContentResolver first (to avoid getting row ID from engineMediaTitle)
        if (currentUri.scheme == "content") {
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
                            val nameWithoutExt = if (dot > 0) fullName.substring(0, dot) else fullName
                            if (nameWithoutExt.isNotBlank()) {
                                return@remember nameWithoutExt
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        // 2. Otherwise/Fallback: try engineMediaTitle if it's not null/blank
        if (!engineMediaTitle.isNullOrBlank()) {
            return@remember engineMediaTitle
        }

        // 3. Fall back: use last path segment and strip extension
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
        if (isLocked) {
            showUnlockButton = true
        } else {
            handleBack()
        }
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
        try {
            val serviceIntent = Intent(context, MediaPlaybackService::class.java)
            context.stopService(serviceIntent)
        } catch (_: Exception) {}
    }

    // Dynamically adjust screen orientation based on user setting or video dimensions
    LaunchedEffect(videoWidth, videoHeight, videoRotation, playbackSettings.orientationMode) {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                val orientationMode = playbackSettings.orientationMode
                when (orientationMode) {
                    com.devson.nvplayer.repository.OrientationMode.LANDSCAPE,
                    com.devson.nvplayer.repository.OrientationMode.AUTO -> {
                        // Force landscape regardless of video dimensions
                        currentContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                    com.devson.nvplayer.repository.OrientationMode.PORTRAIT -> {
                        currentContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    com.devson.nvplayer.repository.OrientationMode.SYSTEM_DEFAULT -> {
                        // Auto-detect from video dimensions (original behaviour)
                        if (videoWidth > 0 && videoHeight > 0) {
                            val isRotated = videoRotation == 90L || videoRotation == 270L
                            val displayWidth = if (isRotated) videoHeight else videoWidth
                            val displayHeight = if (isRotated) videoWidth else videoHeight
                            if (displayWidth > displayHeight) {
                                currentContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                currentContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }
                    }
                }
                break
            }
            currentContext = currentContext.baseContext
        }
    }

    val currentOnPlayPauseToggle by rememberUpdatedState(onPlayPauseToggle)
    val currentIsPlaying by rememberUpdatedState(isPlaying)

    // Observe and apply system navigation (soft button mode) settings dynamically
    LaunchedEffect(playbackSettings.softButtonMode, controlsVisible) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            when (playbackSettings.softButtonMode) {
                com.devson.nvplayer.repository.SoftButtonMode.AUTO_HIDE -> {
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (controlsVisible) {
                        insetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    } else {
                        insetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    }
                }
                com.devson.nvplayer.repository.SoftButtonMode.SHOW -> {
                    insetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                }
                com.devson.nvplayer.repository.SoftButtonMode.HIDE -> {
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                }
            }
        }
    }

    // Pause playback when the window focus is lost and the pauseWhenObstructed setting is enabled
    LaunchedEffect(windowInfo.isWindowFocused, playbackSettings.pauseWhenObstructed) {
        if (!windowInfo.isWindowFocused && playbackSettings.pauseWhenObstructed && currentIsPlaying) {
            currentOnPlayPauseToggle()
        }
    }

    // Keep device screen awake during playing, and also when paused if keepAwakeAlways is enabled
    val keepScreenOn = isPlaying || playbackSettings.keepAwakeAlways
    LaunchedEffect(keepScreenOn) {
        activity?.let { act ->
            if (keepScreenOn) {
                act.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                act.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Enforce standard vertical layout when leaving PlayerScreen to return to lists and pause audio,
    // and restore default screen brightness. Restore system bars on exit.
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                // Explicitly clear keep screen awake flag when leaving the player
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                val bgPlayEnabled = playbackSettings.backgroundPlayEnabled
                if (bgPlayEnabled) {
                    val intent = Intent(context, MediaPlaybackService::class.java).apply {
                        putExtra(MediaPlaybackService.EXTRA_VIDEO_TITLE, videoTitle)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } else {
                    currentOnPlayPauseToggle()
                }
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
        // No `transformable` modifier here - it would never receive events because
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
                if (!isInPipMode) {
                    if (!isLocked) {
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
                            onShowMuteIcon = {},
                            onTakeVideoScreenshot = onTakeVideoScreenshot,
                            onZoomChange = onZoomChange,
                            audioBoosterEnabled = audioBoosterEnabled,
                            audioBoostVolume = audioBoostVolume,
                            onSetAudioBoostVolume = onSetAudioBoostVolume
                        )
                    }
                }

                ComposeSubtitleOverlay(
                    subtitleText = currentSubtitleText,
                    textSizeScale = playbackSettings.subtitleTextSizeScale,
                    bgStyle = playbackSettings.subtitleBgStyle,
                    subtitleFont = playbackSettings.subtitleFont,
                    isSubtitleBold = playbackSettings.isSubtitleBold,
                    isSubtitleGestureEnabled = playbackSettings.subtitleGesturesEnabled && !isInPipMode,
                    verticalOffsetFraction = playbackSettings.subtitleVerticalOffset,
                    onVerticalOffsetFractionChanged = { offset ->
                        onUpdateSubtitleVerticalOffset(offset)
                    },
                    onSeekNext = onSeekNextSubtitle,
                    onSeekPrev = onSeekPrevSubtitle
                )

                if (!isInPipMode) {
                    // Persistent top bar overlay when controls are hidden
                    AnimatedVisibility(
                        visible = !controlsVisible && (playbackSettings.showRemainingTime || playbackSettings.showBatteryClockOverlay),
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        PersistentTopBar(
                            duration = duration,
                            currentPosition = currentPosition,
                            showRemainingTime = playbackSettings.showRemainingTime,
                            showBatteryClock = playbackSettings.showBatteryClockOverlay,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 12.dp)
                        )
                    }

                    // Unified Premium Controls Layer
                    AnimatedVisibility(
                        visible = controlsVisible && !isLocked,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        PlayerControls(
                            title = videoTitle,
                            isPlaying = isPlaying,
                            isSmartEnhanceEnabled = playbackSettings.enhanceMode != EnhanceMode.OFF,
                            currentPosition = currentPosition,
                            bufferedPosition = bufferedPosition,
                            isNetworkStream = isNetworkStream,
                            duration = duration,
                            isDragging = isDragging,
                            onDraggingChanged = { isDragging = it },
                            onPlayPauseToggle = onPlayPauseToggle,
                            onSeek = onSeek,
                            onSpeedClick = {
                                showPlayerSettingsSideSheet = true
                            },
                            onEnhanceClick = {
                                showEnhanceSettingsSideSheet = true
                            },
                            onShowChapters = {
                                showChaptersSideSheet = true
                            },
                            hasChapters = chapters.isNotEmpty(),
                            currentDecoder = currentDecoder,
                            onShowDecoder = {
                                showDecoderSideSheet = true
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
                            showNextPrevButtons = playbackSettings.showNextPrevButtons,
                            showElapsedTimeOverlay = playbackSettings.showElapsedTimeOverlay,
                            showRemainingTime = playbackSettings.showRemainingTime,
                            showBatteryClockOverlay = playbackSettings.showBatteryClockOverlay,
                            showScreenRotationButton = playbackSettings.showScreenRotationButton,
                            seekDurationSeconds = playbackSettings.seekDurationSeconds,
                            controlIconSize = playbackSettings.controlIconSize,
                            topLeftButtons = topLeftButtons,
                            topRightButtons = topRightButtons,
                            bottomLeftButtons = bottomLeftButtons,
                            bottomRightButtons = bottomRightButtons,
                            portraitTopLeftButtons = portraitTopLeftButtons,
                            portraitTopRightButtons = portraitTopRightButtons,
                            portraitBottomButtons = portraitBottomButtons,
                            onLockClick = { isLocked = true },
                            onAspectClick = onCycleAspectMode,
                            onPipClick = onEnterPip,
                            currentAspectMode = playbackSettings.aspectMode,
                            isBackgroundPlayEnabled = playbackSettings.backgroundPlayEnabled,
                            onBackgroundPlayClick = {
                                val newVal = !playbackSettings.backgroundPlayEnabled
                                onUpdateBackgroundPlayEnabled(newVal)
                                android.widget.Toast.makeText(
                                    context,
                                    if (newVal) "Background play enabled" else "Background play disabled",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            ytdlQuality = playbackSettings.ytdlQuality,
                            onShowQuality = { showQualitySideSheet = true },
                            modifier = Modifier
                        )
                    }
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

                // Aspect Ratio Overlay HUD
                AnimatedVisibility(
                    visible = aspectOverlayText != null,
                    enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)),
                    exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val overlayIcon = when (playbackSettings.aspectMode) {
                                com.devson.nvplayer.player.AspectMode.FIT -> AspectIcons.Fit
                                com.devson.nvplayer.player.AspectMode.STRETCH -> AspectIcons.Stretch
                                com.devson.nvplayer.player.AspectMode.CROP -> AspectIcons.Crop
                                com.devson.nvplayer.player.AspectMode.ORIGINAL -> AspectIcons.Original
                            }
                            Icon(
                                imageVector = overlayIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = aspectOverlayText ?: "",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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

        QualitySettingsSideSheet(
            visible = showQualitySideSheet,
            playbackSettings = playbackSettings,
            onSelectQuality = { quality ->
                activeViewModel?.changeYtdlQuality(quality)
            },
            onDataSaverToggled = { enabled ->
                activeViewModel?.toggleDataSaver(enabled)
            },
            onDismiss = { showQualitySideSheet = false }
        )

        AudioSettingsSideSheet(
            visible = showAudioSettingsSideSheet,
            audioTracks = audioTracks,
            audioBoosterEnabled = audioBoosterEnabled,
            onToggleAudioBooster = onToggleAudioBooster,
            onSelectAudioTrack = onSelectAudioTrack,
            onDismiss = { showAudioSettingsSideSheet = false }
        )

        PlayerSettingsSideSheet(
            visible = showPlayerSettingsSideSheet,
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
            onUpdateOrientationMode = onUpdateOrientationMode,
            onUpdateFullScreenMode = onUpdateFullScreenMode,
            onUpdateSoftButtonMode = onUpdateSoftButtonMode,
            onUpdateControlIconSize = onUpdateControlIconSize,
            onUpdateSeekBarStyle = onUpdateSeekBarStyle,
            onUpdateAutoPlayEnabled = onUpdateAutoPlayEnabled,
            onUpdateShowSeekButtons = onUpdateShowSeekButtons,
            onUpdateShowNextPrevButtons = onUpdateShowNextPrevButtons,
            onUpdateShowElapsedTimeOverlay = onUpdateShowElapsedTimeOverlay,
            onUpdateShowRemainingTime = onUpdateShowRemainingTime,
            onUpdateShowBatteryClockOverlay = onUpdateShowBatteryClockOverlay,
            onUpdateShowScreenRotationButton = onUpdateShowScreenRotationButton,
            onUpdatePauseWhenObstructed = onUpdatePauseWhenObstructed,
            onUpdateKeepAwakeAlways = onUpdateKeepAwakeAlways,
            onDismiss = { showPlayerSettingsSideSheet = false }
        )

        ChaptersSideSheet(
            visible = showChaptersSideSheet,
            chapters = chapters,
            onSelectChapter = onSelectChapter,
            onDismiss = { showChaptersSideSheet = false }
        )

        DecoderSideSheet(
            visible = showDecoderSideSheet,
            currentMode = if (!isHwSupported) DecoderMode.SW else playbackSettings.decoderMode,
            onSelectMode = { mode ->
                onUpdateDecoderMode(mode)
            },
            onDismiss = { showDecoderSideSheet = false }
        )

        EnhanceSettingsSideSheet(
            visible = showEnhanceSettingsSideSheet,
            playbackSettings = playbackSettings,
            onUpdateEnhanceMode = onUpdateEnhanceMode,
            onUpdateEnhanceSaturation = onUpdateEnhanceSaturation,
            onUpdateEnhanceContrast = onUpdateEnhanceContrast,
            onUpdateEnhanceBrightness = onUpdateEnhanceBrightness,
            onUpdateEnhanceGamma = onUpdateEnhanceGamma,
            onUpdateEnhanceHue = onUpdateEnhanceHue,
            onDismiss = { showEnhanceSettingsSideSheet = false }
        )

        if (!isInPipMode && isNetworkStream) {
            AnimatedVisibility(
                visible = controlsVisible && !isLocked,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 70.dp, end = 16.dp)
            ) {
                StreamingDataPanel(
                    speedBps = networkSpeedBytesPerSec,
                    bufferSec = bufferDurationSeconds
                )
            }
        }

        if (isLocked) {
            // Auto-hide the unlock button after 3 seconds
            LaunchedEffect(showUnlockButton) {
                if (showUnlockButton) {
                    delay(3000L)
                    showUnlockButton = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (showUnlockButton) 0.35f else 0.01f))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            showUnlockButton = !showUnlockButton
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = showUnlockButton,
                    enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f),
                    exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f)
                ) {
                    FloatingActionButton(
                        onClick = {
                            isLocked = false
                            controlsVisible = true
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(72.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LockOpen,
                            contentDescription = "Unlock Controls",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersistentTopBar(
    duration: Long,
    currentPosition: Long,
    showRemainingTime: Boolean,
    showBatteryClock: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var batteryPercentage by remember { mutableIntStateOf(100) }
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(showBatteryClock) {
        if (showBatteryClock) {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (true) {
                batteryPercentage = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                currentTime = timeFormat.format(Date())
                delay(10000L)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showRemainingTime) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HourglassBottom,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(12.dp)
                    )
                    val remainingMs = (duration - currentPosition).coerceAtLeast(0L)
                    Text(
                        text = "-${formatTime(remainingMs)}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (showRemainingTime && showBatteryClock) {
                Spacer(
                    modifier = Modifier
                        .width(1.dp)
                        .height(10.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )
            }

            if (showBatteryClock) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryChargingFull,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "$batteryPercentage%",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = currentTime,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
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

@Composable
fun StreamingDataPanel(
    speedBps: Long,
    bufferSec: Double,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Speed: ${formatSpeed(speedBps)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.HourglassBottom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Buffer: ${String.format(Locale.US, "%.1fs", bufferSec)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB/s", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB/s", kb)
        else -> "$bytesPerSec B/s"
    }
}