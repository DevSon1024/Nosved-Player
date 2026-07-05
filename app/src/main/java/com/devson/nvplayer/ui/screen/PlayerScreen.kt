package com.devson.nvplayer.ui.screen

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.devson.nvplayer.player.engine.MPVSurfaceView
import com.devson.nvplayer.player.engine.PlayerState
import com.devson.nvplayer.ui.common.PlayerControls
import com.devson.nvplayer.ui.common.GestureOverlay
import com.devson.nvplayer.ui.common.icons.AspectIcons
import com.devson.nvplayer.domain.model.PlayerButton
import kotlinx.coroutines.delay
import android.media.AudioManager
import android.content.Context
import com.devson.nvplayer.util.findActivity
import com.devson.nvplayer.player.model.TrackInfo
import com.devson.nvplayer.data.repository.SubtitleFont
import com.devson.nvplayer.data.repository.PlaybackSettings
import com.devson.nvplayer.data.repository.EnhanceMode
import com.devson.nvplayer.data.repository.MultiFingerAction
import android.provider.MediaStore
import com.devson.nvplayer.ui.common.SubtitleSettingsSideSheet
import com.devson.nvplayer.ui.common.sheets.AudioSettingsSideSheet
import com.devson.nvplayer.ui.common.sheets.QualitySettingsSideSheet
import com.devson.nvplayer.ui.common.ComposeSubtitleOverlay
import com.devson.nvplayer.ui.common.sheets.PlayerSettingsSideSheet
import com.devson.nvplayer.ui.common.sheets.EnhanceSettingsSideSheet
import com.devson.nvplayer.player.model.ChapterInfo
import com.devson.nvplayer.ui.common.sheets.ChaptersSideSheet
import com.devson.nvplayer.player.model.DecoderMode
import com.devson.nvplayer.ui.common.sheets.DecoderSideSheet
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.BatteryManager
import android.os.Build
import android.content.Intent
import android.widget.Toast
import com.devson.nvplayer.player.service.MediaPlaybackService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.devson.nvplayer.ui.common.formatTime
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devson.nvplayer.data.repository.DoubleTapAction
import com.devson.nvplayer.data.repository.FullScreenMode
import com.devson.nvplayer.data.repository.OrientationMode
import com.devson.nvplayer.data.repository.SoftButtonMode
import com.devson.nvplayer.player.model.AspectMode
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.ui.screen.videolist.components.common.PlayingIndicator
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoThumbnail
import com.devson.nvplayer.ui.screen.videolist.components.video.DurationBadge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import com.devson.nvplayer.domain.model.LayoutMode
import androidx.compose.ui.platform.LocalConfiguration



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
    onUpdateDoubleTapAction: (DoubleTapAction) -> Unit = {},
    onUpdateTwoFingerAction: (MultiFingerAction) -> Unit = {},
    onUpdateThreeFingerAction: (MultiFingerAction) -> Unit = {},
    onUpdateOrientationMode: (OrientationMode) -> Unit = {},
    onUpdateFullScreenMode: (FullScreenMode) -> Unit = {},
    onUpdateAspectMode: (AspectMode) -> Unit = {},
    onUpdateSoftButtonMode: (SoftButtonMode) -> Unit = {},
    onUpdateControlIconSize: (String) -> Unit = {},
    onUpdateSeekBarStyle: (String) -> Unit = {},
    onUpdateAutoPlayEnabled: (Boolean) -> Unit = {},
    onUpdateShowSeekButtons: (Boolean) -> Unit = {},
    onUpdateShowNextPrevButtons: (Boolean) -> Unit = {},
    onUpdateShowRemainingTime: (Boolean) -> Unit = {},
    onUpdateShowBatteryClockOverlay: (Boolean) -> Unit = {},
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
    onUpdateIsBottomLayoutEnabled: (Boolean) -> Unit = {},
    onUpdateShowControlGradients: (Boolean) -> Unit = {},
    onUpdateShowUpNextQueue: (Boolean) -> Unit = {},
    networkSpeedBytesPerSec: Long = 0L,
    bufferDurationSeconds: Double = 0.0,
    isNetworkStream: Boolean = false,
    bufferedPosition: Long = 0L,
    isDynamicSpeedActive: Boolean = false,
    onSetDynamicSpeedActive: (Boolean) -> Unit = {},
    viewModel: com.devson.nvplayer.viewmodel.PlayerViewModel? = null,
    queueList: List<Video> = emptyList(),
    currentVideoId: String? = null,
    isQueueVisible: Boolean = false,
    onQueueVisibleChange: (Boolean) -> Unit = {},
    onQueueVideoClick: (Video) -> Unit = {},
    onUpdateQueueLayoutMode: (LayoutMode) -> Unit = {}
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
    var showImportSubtitleDialog by remember { mutableStateOf(false) }

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
                AspectMode.FIT -> "Fit Screen"
                AspectMode.STRETCH -> "Stretch"
                AspectMode.CROP -> "Crop"
                AspectMode.ORIGINAL -> "100% Original"
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
        if (currentUri == null) {
            return@remember if (!engineMediaTitle.isNullOrBlank()) engineMediaTitle else "Local Video"
        }
        
        val scheme = currentUri.scheme
        val isLocal = scheme == null || scheme == "content" || scheme == "file"
        
        if (isLocal) {
            // For local videos, try ContentResolver first to get DISPLAY_NAME
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
            
            // If ContentResolver fails, try to extract from the path segment
            val seg = currentUri.lastPathSegment ?: currentUri.toString()
            val name = seg.substringAfterLast('/')
            // Only use the segment name if it is not just a numeric ID and we have a valid engineMediaTitle
            val isNumericId = name.all { it.isDigit() }
            if (isNumericId && !engineMediaTitle.isNullOrBlank() && !engineMediaTitle.all { it.isDigit() }) {
                val dot = engineMediaTitle.lastIndexOf('.')
                return@remember if (dot > 0) engineMediaTitle.substring(0, dot) else engineMediaTitle
            }
            
            val dot = name.lastIndexOf('.')
            val resolvedLocalName = if (dot > 0) name.substring(0, dot) else name
            if (resolvedLocalName.isNotBlank() && !resolvedLocalName.all { it.isDigit() }) {
                return@remember resolvedLocalName
            }
        }
        
        // For network streams or as a fallback, use engineMediaTitle if available
        if (!engineMediaTitle.isNullOrBlank()) {
            val cleanTitle = if (engineMediaTitle.startsWith("http://") || engineMediaTitle.startsWith("https://")) {
                val parsed = runCatching { Uri.parse(engineMediaTitle) }.getOrNull()
                parsed?.lastPathSegment ?: engineMediaTitle
            } else {
                engineMediaTitle
            }
            return@remember cleanTitle
        }
        
        // Final fallback: segment extraction
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
                    OrientationMode.LANDSCAPE,
                    OrientationMode.AUTO -> {
                        // Force landscape regardless of video dimensions
                        currentContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                    OrientationMode.PORTRAIT -> {
                        currentContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    OrientationMode.SYSTEM_DEFAULT -> {
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
                SoftButtonMode.AUTO_HIDE -> {
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (controlsVisible) {
                        insetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    } else {
                        insetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    }
                }
                SoftButtonMode.SHOW -> {
                    insetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                }
                SoftButtonMode.HIDE -> {
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
                        data = currentUri
                        putExtra(MediaPlaybackService.EXTRA_VIDEO_TITLE, videoTitle)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
                            onSetAudioBoostVolume = onSetAudioBoostVolume,
                            isDynamicSpeedActive = isDynamicSpeedActive,
                            onSetDynamicSpeedActive = onSetDynamicSpeedActive,
                            onSaveTapAndHoldSpeed = onUpdateTapAndHoldSpeed
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
                    // Top overlays: PersistentTopBar and/or SpeedSliderHUD
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Persistent top bar overlay when controls are hidden
                        AnimatedVisibility(
                            visible = !controlsVisible && (playbackSettings.showRemainingTime || playbackSettings.showBatteryClockOverlay),
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                        ) {
                            PersistentTopBar(
                                duration = duration,
                                currentPosition = currentPosition,
                                showRemainingTime = playbackSettings.showRemainingTime,
                                showBatteryClock = playbackSettings.showBatteryClockOverlay
                            )
                        }

                        // Dynamic Speed Overlay
                        AnimatedVisibility(
                            visible = isDynamicSpeedActive,
                            enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)),
                            exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150))
                        ) {
                            SpeedSliderHUD(playbackSpeed = playbackSpeed)
                        }
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
                            isBottomLayoutEnabled = playbackSettings.isBottomLayoutEnabled,
                            showControlGradients = playbackSettings.showControlGradients,
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
                            showRemainingTime = playbackSettings.showRemainingTime,
                            showBatteryClockOverlay = playbackSettings.showBatteryClockOverlay,
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
                                Toast.makeText(
                                    context,
                                    if (newVal) "Background play enabled" else "Background play disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                             onTitleClick = {
                                if (playbackSettings.showUpNextQueue) {
                                    onQueueVisibleChange(true)
                                }
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
                                AspectMode.FIT -> AspectIcons.Fit
                                AspectMode.STRETCH -> AspectIcons.Stretch
                                AspectMode.CROP -> AspectIcons.Crop
                                AspectMode.ORIGINAL -> AspectIcons.Original
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
            onDismiss = { showSubtitleSettingsSideSheet = false },
            onImportSubtitleClick = {
                showImportSubtitleDialog = true
                showSubtitleSettingsSideSheet = false
            }
        )

        if (showImportSubtitleDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showImportSubtitleDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(0.9f)
                        .heightIn(max = 500.dp)
                        .fillMaxHeight(0.85f)
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 4.dp
                ) {
                    StorageExplorerScreen(
                        operationType = "SELECT_FILE",
                        allowedExtensions = listOf(".srt", ".vtt", ".ssa", ".ass", ".ttml", ".sub", ".pgs", ".sbv"),
                        onFileSelected = { file ->
                            showImportSubtitleDialog = false
                            activeViewModel?.importSubtitle(Uri.fromFile(file))
                        },
                        onCancel = { showImportSubtitleDialog = false }
                    )
                }
            }
        }

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
            onUpdateTwoFingerAction = onUpdateTwoFingerAction,
            onUpdateThreeFingerAction = onUpdateThreeFingerAction,
            onUpdateLongPressEnabled = onUpdateLongPressEnabled,
            onUpdateTapAndHoldSpeed = onUpdateTapAndHoldSpeed,
            onUpdateLongPressSpeed = onUpdateLongPressSpeed,
            onUpdateOrientationMode = onUpdateOrientationMode,
            onUpdateFullScreenMode = onUpdateFullScreenMode,
            onUpdateAspectMode = onUpdateAspectMode,
            onUpdateSoftButtonMode = onUpdateSoftButtonMode,
            onUpdateControlIconSize = onUpdateControlIconSize,
            onUpdateSeekBarStyle = onUpdateSeekBarStyle,
            onUpdateAutoPlayEnabled = onUpdateAutoPlayEnabled,
            onUpdateShowSeekButtons = onUpdateShowSeekButtons,
            onUpdateShowNextPrevButtons = onUpdateShowNextPrevButtons,
            onUpdateShowRemainingTime = onUpdateShowRemainingTime,
            onUpdateShowBatteryClockOverlay = onUpdateShowBatteryClockOverlay,
            onUpdatePauseWhenObstructed = onUpdatePauseWhenObstructed,
            onUpdateKeepAwakeAlways = onUpdateKeepAwakeAlways,
            onUpdateIsBottomLayoutEnabled = onUpdateIsBottomLayoutEnabled,
            onUpdateShowControlGradients = onUpdateShowControlGradients,
            onUpdateShowUpNextQueue = onUpdateShowUpNextQueue,
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

        if (!isInPipMode && !isLocked && queueList.isNotEmpty() && playbackSettings.showUpNextQueue) {
            val density = LocalDensity.current
            val configuration = LocalConfiguration.current
            val queueLayoutMode = playbackSettings.queueLayoutMode
            val panelHeight = if (queueLayoutMode == LayoutMode.LIST) {
                configuration.screenHeightDp.dp
            } else {
                240.dp
            }
            val panelHeightPx = with(density) { panelHeight.toPx() }
            var dragOffsetY by remember { mutableStateOf(0f) }
            val offsetY by animateFloatAsState(
                targetValue = if (isQueueVisible) 0f else panelHeightPx,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "QueuePanelOffset"
            )
            val totalOffsetY = (offsetY + dragOffsetY).coerceAtLeast(0f)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(panelHeight)
                    .graphicsLayer {
                        translationY = totalOffsetY
                    }
                    .background(
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (dragOffsetY > 80f) {
                                            onQueueVisibleChange(false)
                                        }
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = { dragOffsetY = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                    }
                                )
                            }
                            .padding(top = 10.dp, bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.VideoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Up Next Queue",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${queueList.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onUpdateQueueLayoutMode(LayoutMode.LIST) },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (queueLayoutMode == LayoutMode.LIST) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ViewList,
                                        contentDescription = "List View",
                                        tint = if (queueLayoutMode == LayoutMode.LIST) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onUpdateQueueLayoutMode(LayoutMode.GRID) },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (queueLayoutMode == LayoutMode.GRID) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ViewCarousel,
                                        contentDescription = "Grid/Carousel View",
                                        tint = if (queueLayoutMode == LayoutMode.GRID) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { onQueueVisibleChange(false) },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Hide Queue",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        thickness = 1.dp
                    )
                    val listState = rememberLazyListState()
                    val activeIndex = remember(queueList, currentVideoId) {
                        queueList.indexOfFirst { it.uri == currentVideoId }
                    }
                    LaunchedEffect(isQueueVisible, queueLayoutMode) {
                        if (isQueueVisible && activeIndex >= 0 && queueLayoutMode == LayoutMode.LIST) {
                            listState.animateScrollToItem(activeIndex)
                        }
                    }
                    if (queueLayoutMode == LayoutMode.LIST) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(
                                items = queueList,
                                key = { _, video -> video.uri }
                            ) { index, video ->
                                val isPlaying = video.uri == currentVideoId
                                QueueVideoItem(
                                    video = video,
                                    isPlaying = isPlaying,
                                    onClick = { onQueueVideoClick(video) }
                                )
                            }
                        }
                    } else {
                        val rowState = rememberLazyListState()
                        LaunchedEffect(isQueueVisible, queueLayoutMode) {
                            if (isQueueVisible && queueLayoutMode == LayoutMode.GRID && activeIndex >= 0) {
                                rowState.animateScrollToItem(activeIndex)
                            }
                        }
                        LazyRow(
                            state = rowState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            itemsIndexed(
                                items = queueList,
                                key = { _, video -> video.uri }
                            ) { index, video ->
                                val isPlaying = video.uri == currentVideoId
                                QueueVideoGridItem(
                                    video = video,
                                    isPlaying = isPlaying,
                                    onClick = { onQueueVideoClick(video) }
                                )
                            }
                        }
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

@Composable
private fun SpeedSliderHUD(
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    val speedSteps = remember { listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f) }
    val activeIndex = remember(playbackSpeed) {
        val exactIndex = speedSteps.indexOfFirst { it == playbackSpeed }
        if (exactIndex != -1) exactIndex else {
            speedSteps.mapIndexed { idx, v -> idx to Math.abs(v - playbackSpeed) }
                .minByOrNull { it.second }?.first ?: 4
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "speedPulse")
    val c1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, delayMillis = 0, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "c1"
    )
    val c2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, delayMillis = 140, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "c2"
    )
    val c3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, delayMillis = 280, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "c3"
    )

    Box(
        modifier = modifier
            .width(380.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                val density = LocalDensity.current
                val startPadding = 20.dp
                val endPadding = 20.dp
                val startPaddingPx = with(density) { startPadding.toPx() }
                val endPaddingPx = with(density) { endPadding.toPx() }
                val widthPx = constraints.maxWidth.toFloat()
                val trackWidthPx = widthPx - startPaddingPx - endPaddingPx

                // 1. Draw track line behind ticks
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    val centerY = size.height / 2f
                    // Draw inactive track (entire line)
                    drawLine(
                        color = Color.White.copy(alpha = 0.15f),
                        start = Offset(startPaddingPx, centerY),
                        end = Offset(widthPx - endPaddingPx, centerY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    
                    // Draw active track (from left to active index)
                    val activeThumbX = startPaddingPx + trackWidthPx * (activeIndex.toFloat() / 8f)
                    drawLine(
                        color = Color(0xFF3B82F6), // Premium vibrant blue accent
                        start = Offset(startPaddingPx, centerY),
                        end = Offset(activeThumbX, centerY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 2. Draw labels and tick dots
                speedSteps.forEachIndexed { index, step ->
                    val fraction = index.toFloat() / 8f
                    val stepXPx = startPaddingPx + trackWidthPx * fraction
                    val stepXDp = with(density) { stepXPx.toDp() }
                    val isActive = index == activeIndex

                    // Label above the track
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = stepXDp - 20.dp, y = 2.dp)
                            .width(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", step).removeSuffix("0").removeSuffix(".0") + "x",
                            color = if (isActive) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.6f),
                            fontSize = if (isActive) 11.sp else 9.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    // Tick dot or Active Thumb
                    val dotColor = if (isActive) Color(0xFF3B82F6) else Color.White
                    val dotRadius = if (isActive) 5.dp else 2.5.dp
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = stepXDp - dotRadius, y = 22.dp - dotRadius)
                            .size(dotRadius * 2)
                            .clip(CircleShape)
                            .background(dotColor)
                            .border(
                                width = if (isActive) 1.5.dp else 0.dp,
                                color = if (isActive) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Current Speed Playing Label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-4).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = c1),
                        modifier = Modifier.size(14.dp)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = c2),
                        modifier = Modifier.size(16.dp)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = c3),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = String.format(Locale.US, "%.2fx Speed Playing", playbackSpeed),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

@Composable
private fun QueueVideoItem(
    video: Video,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isPlaying) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        Color.Transparent
    }
    
    val titleColor = if (isPlaying) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            VideoThumbnail(
                uri = video.uri,
                showPlayIcon = false,
                modifier = Modifier.fillMaxSize()
            )
            
            DurationBadge(duration = video.duration)
            
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayingIndicator(
                        isPlaying = true,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = video.title,
                color = titleColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = video.folderName,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueVideoGridItem(
    video: Video,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderStroke = if (isPlaying) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(160.dp)
            .height(150.dp),
        shape = RoundedCornerShape(12.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp))
            ) {
                VideoThumbnail(
                    uri = video.uri,
                    showPlayIcon = false,
                    modifier = Modifier.fillMaxSize()
                )
                DurationBadge(duration = video.duration)
                
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingIndicator(
                            isPlaying = true,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = video.title,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

