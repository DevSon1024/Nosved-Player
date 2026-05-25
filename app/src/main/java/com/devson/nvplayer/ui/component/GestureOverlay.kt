package com.devson.nvplayer.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.repository.DoubleTapAction
import com.devson.nvplayer.repository.MultiFingerAction
import com.devson.nvplayer.repository.PlaybackSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.gestures.detectTransformGestures

@Composable
fun GestureOverlay(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    savedBrightness: Float,
    savedVolume: Int,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long, Boolean) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSaveBrightness: (Float) -> Unit,
    onSaveVolume: (Int) -> Unit,
    controlsVisible: Boolean,
    onControlsVisibleChanged: (Boolean) -> Unit,
    customPlaybackSpeed: Float,
    tapAndHoldSpeed: Float,
    doubleTapSeekDurationMs: Long,
    playbackSettings: PlaybackSettings = PlaybackSettings(),
    onShowMuteIcon: (Boolean) -> Unit = {},
    onTakeVideoScreenshot: () -> Unit = {},
    onZoomChange: ((scaleMultiplier: Float, pan: Offset) -> Unit)? = null,
    audioBoosterEnabled: Boolean = false,
    audioBoostVolume: Int = 100,
    onSetAudioBoostVolume: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val audioBoosterEnabledState = rememberUpdatedState(audioBoosterEnabled)
    val audioBoostVolumeState = rememberUpdatedState(audioBoostVolume)
    val onSetAudioBoostVolumeState = rememberUpdatedState(onSetAudioBoostVolume)

    val density = LocalDensity.current
    var showSeekIndicator by remember { mutableStateOf(false) }
    var currentSeekPos by remember { mutableLongStateOf(0L) }
    var seekDeltaSeconds by remember { mutableIntStateOf(0) }

    var lastUnmutedVolume by remember { mutableIntStateOf(maxVolume / 2) }
    var muteOverlayVisible by remember { mutableStateOf(false) }
    var isMutedState by remember { mutableStateOf(false) }
    var muteOverlayTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(muteOverlayTick) {
        if (muteOverlayTick > 0) {
            muteOverlayVisible = true
            delay(1000L)
            muteOverlayVisible = false
        }
    }

    var isLongPressSpeedActive by remember { mutableStateOf(false) }
    var speedBeforeLongPress by remember { mutableStateOf(1.0f) }
    var isFastPlayActive by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showMultiFingerToast by remember { mutableStateOf<String?>(null) }
    var currentVolumePercent by remember { mutableStateOf(0) }
    var currentBrightnessPercent by remember { mutableStateOf(0) }

    // Double tap play/pause ripple states
    var playPauseRippleTick by remember { mutableStateOf(0) }
    var lastIsPlaying by remember { mutableStateOf(isPlaying) }

    // Left/Right double tap seek ripple states
    var leftAccumulatedMs by remember { mutableStateOf(0L) }
    var rightAccumulatedMs by remember { mutableStateOf(0L) }
    var leftRippleTick by remember { mutableStateOf(0) }
    var rightRippleTick by remember { mutableStateOf(0) }
    var leftRippleActive by remember { mutableStateOf(false) }
    var rightRippleActive by remember { mutableStateOf(false) }

    var leftClearJob by remember { mutableStateOf<Job?>(null) }
    var rightClearJob by remember { mutableStateOf<Job?>(null) }

    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    var tapJob by remember { mutableStateOf<Job?>(null) }
    var volumeHideJob by remember { mutableStateOf<Job?>(null) }
    var brightnessHideJob by remember { mutableStateOf<Job?>(null) }

    var currentVolumeFloat by remember {
        mutableStateOf(if (savedVolume >= 0) savedVolume.toFloat() else audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }
    var currentBrightnessFloat by remember {
        mutableStateOf(if (savedBrightness >= 0f) savedBrightness else {
            val lp = activity?.window?.attributes
            if (lp != null && lp.screenBrightness >= 0f) lp.screenBrightness else 0.5f
        })
    }

    LaunchedEffect(isPlaying) {
        if (lastIsPlaying != isPlaying) {
            playPauseRippleTick++
            lastIsPlaying = isPlaying
        }
    }

    val currentPositionState = rememberUpdatedState(currentPosition)
    val durationState = rememberUpdatedState(duration)
    val playbackSpeedState = rememberUpdatedState(playbackSpeed)
    val controlsVisibleState = rememberUpdatedState(controlsVisible)

    val onPlayPauseToggleState = rememberUpdatedState(onPlayPauseToggle)
    val onSeekState = rememberUpdatedState(onSeek)
    val onSetPlaybackSpeedState = rememberUpdatedState(onSetPlaybackSpeed)
    val onSaveBrightnessState = rememberUpdatedState(onSaveBrightness)
    val onSaveVolumeState = rememberUpdatedState(onSaveVolume)
    val onControlsVisibleChangedState = rememberUpdatedState(onControlsVisibleChanged)
    val playbackSettingsState = rememberUpdatedState(playbackSettings)

    // Helper to execute a multi-finger tap action
    fun executeMultiFingerAction(
        action: MultiFingerAction,
        onPlayPause: () -> Unit,
        onSetSpeed: (Float) -> Unit,
        currentSpeed: Float,
        customSpeed: Float,
        audioManager: AudioManager,
        maxVolume: Int,
        onShowToast: (String) -> Unit,
        onFastPlayChanged: (Boolean) -> Unit,
        isFastPlay: Boolean,
        view: android.view.View,
        activity: Activity?,
        lastUnmutedVolume: Int,
        onLastUnmutedVolumeChanged: (Int) -> Unit,
        onShowMuteIcon: (Boolean) -> Unit,
        onTakeVideoScreenshot: () -> Unit,
        onTriggerMuteOverlay: (Boolean) -> Unit,
        isMuted: Boolean
    ) {
        when (action) {
            MultiFingerAction.PLAY_PAUSE -> {
                onPlayPause()
            }
            MultiFingerAction.FAST_PLAY -> {
                if (isFastPlay) {
                    onSetSpeed(customSpeed)
                    onFastPlayChanged(false)
                    onShowToast("Speed: ${customSpeed}x")
                } else {
                    onSetSpeed(2.0f)
                    onFastPlayChanged(true)
                    onShowToast("Fast Play: 2x")
                }
            }
            MultiFingerAction.MUTE -> {
                if (isMuted) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, lastUnmutedVolume, 0)
                    onShowMuteIcon(false)
                    onTriggerMuteOverlay(false)
                } else {
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (currentVol > 0) {
                        onLastUnmutedVolumeChanged(currentVol)
                    }
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    onShowMuteIcon(true)
                    onTriggerMuteOverlay(true)
                }
            }
            MultiFingerAction.SCREENSHOT -> {
                onTakeVideoScreenshot()
            }
            MultiFingerAction.PINCH_ZOOM -> { /* handled by detectTransformGestures, not a tap action */ }
            MultiFingerAction.NONE -> { /* no-op */ }
        }
    }

    // Build the outer modifier chain: first attach pinch-zoom handler (higher priority = Initial pass),
    // then single-finger gesture handler on top.
    // detectTransformGestures runs as a SEPARATE pointerInput so it gets its own copy of all events
    // and will not conflict with the single-finger awaitEachGesture below.
    val pinchZoomEnabled = playbackSettings.twoFingerAction == MultiFingerAction.PINCH_ZOOM
    val pinchModifier = if (pinchZoomEnabled && onZoomChange != null) {
        Modifier.pointerInput(pinchZoomEnabled) {
            detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                // Only fire when it's actually a zoom/pan (zoom != 1 or pan != Zero)
                if (zoom != 1f || pan != Offset.Zero) {
                    onZoomChange(zoom, pan)
                }
            }
        }
    } else Modifier

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // Pinch-zoom handler: runs in parallel with the single-finger handler below.
            // Uses detectTransformGestures which only responds to 2-finger motion, so
            // it never conflicts with single-finger taps, swipes, or long-presses.
            .then(pinchModifier)
            .pointerInput(Unit) {
                coroutineScope {
                    var longPressJob: Job? = null
                    var dragStarted = false
                    var isLeftHalfDrag = false
                    var lastDragY = 0f
                    var multiFingerToastJob: Job? = null
                    var targetSeekPos = 0L

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startPosition = down.position
                        lastDragY = startPosition.y
                        dragStarted = false
                        var isSeekDrag = false
                        var seekStartPos = 0L
                        isLeftHalfDrag = startPosition.x < size.width / 2f

                        val isEdge = startPosition.x < size.width * 0.35f || startPosition.x > size.width * 0.65f
                        longPressJob = launch {
                            delay(500L)
                            if (!dragStarted && isEdge && playbackSettingsState.value.longPressEnabled) {
                                isLongPressSpeedActive = true
                                speedBeforeLongPress = playbackSpeedState.value
                                onSetPlaybackSpeedState.value(tapAndHoldSpeed)
                            }
                        }

                        // Track max pointer count for multi-finger detection
                        var maxPointerCount = 1

                        while (true) {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.count { it.pressed }
                            if (activePointers > maxPointerCount) maxPointerCount = activePointers
                            val dragChange = event.changes.firstOrNull() ?: break

                            if (dragChange.pressed) {
                                val currentPos = dragChange.position
                                val diffY = currentPos.y - startPosition.y
                                val diffX = currentPos.x - startPosition.x

                                if (!dragStarted && !isLongPressSpeedActive && maxPointerCount == 1) {
                                    val settings = playbackSettingsState.value
                                    if (Math.abs(diffY) > viewConfiguration.touchSlop && Math.abs(diffY) > Math.abs(diffX)) {
                                        longPressJob?.cancel()
                                        dragStarted = true

                                        if (isLeftHalfDrag && settings.brightnessGestureEnabled) {
                                            val lp = activity?.window?.attributes
                                            currentBrightnessFloat = if (lp != null && lp.screenBrightness >= 0f) lp.screenBrightness else 0.5f
                                            showBrightnessIndicator = true
                                            brightnessHideJob?.cancel()
                                        } else if (!isLeftHalfDrag && settings.volumeGestureEnabled) {
                                            val sysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            if (audioBoosterEnabledState.value && sysVol >= maxVolume) {
                                                currentVolumeFloat = 1.0f + (audioBoostVolumeState.value - 100) / 100f
                                            } else {
                                                currentVolumeFloat = sysVol.toFloat() / maxVolume.coerceAtLeast(1)
                                            }
                                            currentVolumePercent = (currentVolumeFloat * 100).toInt()
                                            showVolumeIndicator = true
                                            volumeHideJob?.cancel()
                                        } else {
                                            dragStarted = false // disable drag if gesture is off
                                        }
                                    } else if (Math.abs(diffX) > viewConfiguration.touchSlop && Math.abs(diffX) > Math.abs(diffY) && settings.seekGestureEnabled) {
                                        longPressJob?.cancel()
                                        dragStarted = true
                                        isSeekDrag = true
                                        seekStartPos = currentPositionState.value
                                        currentSeekPos = seekStartPos
                                        seekDeltaSeconds = 0
                                        showSeekIndicator = true
                                    }
                                }

                                if (dragStarted && maxPointerCount == 1) {
                                    if (isSeekDrag) {
                                        val settings = playbackSettingsState.value
                                        val pixelsPerCm = density.density * 160f / 2.54f
                                        val secondsDelta = (diffX / pixelsPerCm) * settings.seekSpeedSecPerCm
                                        seekDeltaSeconds = secondsDelta.toInt()
                                        currentSeekPos = (seekStartPos + (secondsDelta * 1000).toLong()).coerceIn(0L, durationState.value)
                                        onSeekState.value(currentSeekPos, false)
                                    } else {
                                        val deltaY = currentPos.y - lastDragY
                                        lastDragY = currentPos.y

                                        val screenHeight = size.height.toFloat().coerceAtLeast(1f)
                                        val settings = playbackSettingsState.value
                                        val sensitivity = if (isLeftHalfDrag) settings.brightnessSensitivity else settings.volumeSensitivity
                                        val deltaPercent = -deltaY / screenHeight * (0.5f + sensitivity * 1.5f)

                                        if (isLeftHalfDrag && settings.brightnessGestureEnabled) {
                                            currentBrightnessFloat = (currentBrightnessFloat + deltaPercent).coerceIn(0.01f, 1.0f)
                                            activity?.let { act ->
                                                val lp = act.window.attributes
                                                lp.screenBrightness = currentBrightnessFloat
                                                act.window.attributes = lp
                                            }
                                            currentBrightnessPercent = (currentBrightnessFloat * 100).toInt()
                                        } else if (!isLeftHalfDrag && settings.volumeGestureEnabled) {
                                            val volumeRangeMax = if (audioBoosterEnabledState.value) 2.0f else 1.0f
                                            currentVolumeFloat = (currentVolumeFloat + deltaPercent * volumeRangeMax).coerceIn(0f, volumeRangeMax)
                                            if (currentVolumeFloat <= 1.0f) {
                                                val newVol = (currentVolumeFloat * maxVolume).toInt().coerceIn(0, maxVolume)
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                                onSetAudioBoostVolumeState.value(100)
                                                currentVolumePercent = (currentVolumeFloat * 100).toInt()
                                            } else {
                                                val newVol = maxVolume
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                                val boostVolPercent = (100 + (currentVolumeFloat - 1.0f) * 100).toInt().coerceIn(100, 200)
                                                onSetAudioBoostVolumeState.value(boostVolPercent)
                                                currentVolumePercent = (currentVolumeFloat * 100).toInt()
                                            }
                                            isMutedState = false
                                            onShowMuteIcon(false)
                                        }
                                    }
                                    dragChange.consume()
                                }
                            } else {
                                longPressJob?.cancel()

                                if (isLongPressSpeedActive) {
                                    isLongPressSpeedActive = false
                                    onSetPlaybackSpeedState.value(speedBeforeLongPress)
                                } else if (maxPointerCount >= 2 && !dragStarted) {
                                    // Multi-finger tap detected
                                    val settings = playbackSettingsState.value
                                    val action = if (maxPointerCount == 2) settings.twoFingerAction else settings.threeFingerAction
                                    val currentFastPlay = isFastPlayActive
                                    executeMultiFingerAction(
                                        action = action,
                                        onPlayPause = onPlayPauseToggleState.value,
                                        onSetSpeed = onSetPlaybackSpeedState.value,
                                        currentSpeed = playbackSpeedState.value,
                                        customSpeed = customPlaybackSpeed,
                                        audioManager = audioManager,
                                        maxVolume = maxVolume,
                                        onShowToast = { msg ->
                                            showMultiFingerToast = msg
                                            multiFingerToastJob?.cancel()
                                            multiFingerToastJob = launch {
                                                delay(1500L)
                                                showMultiFingerToast = null
                                            }
                                        },
                                        onFastPlayChanged = { isFastPlayActive = it },
                                        isFastPlay = currentFastPlay,
                                        view = view,
                                        activity = activity,
                                        lastUnmutedVolume = lastUnmutedVolume,
                                        onLastUnmutedVolumeChanged = { lastUnmutedVolume = it },
                                        onShowMuteIcon = onShowMuteIcon,
                                        onTakeVideoScreenshot = onTakeVideoScreenshot,
                                        onTriggerMuteOverlay = { isMuted ->
                                            isMutedState = isMuted
                                            muteOverlayTick++
                                        },
                                        isMuted = isMutedState
                                    )
                                } else if (dragStarted) {
                                    if (isSeekDrag) {
                                        isSeekDrag = false
                                        showSeekIndicator = false
                                        onSeekState.value(currentSeekPos, true)
                                    } else if (isLeftHalfDrag) {
                                        onSaveBrightnessState.value(currentBrightnessFloat)
                                        brightnessHideJob = launch {
                                            delay(1000L)
                                            showBrightnessIndicator = false
                                        }
                                    } else {
                                        onSaveVolumeState.value(currentVolumeFloat.toInt())
                                        volumeHideJob = launch {
                                            delay(1000L)
                                            showVolumeIndicator = false
                                        }
                                    }
                                } else {
                                    val clickTime = System.currentTimeMillis()
                                    val tapOffset = dragChange.position

                                    if (clickTime - lastTapTime < 300L &&
                                        (tapOffset - lastTapPosition).getDistance() < 100f) {

                                        tapJob?.cancel()
                                        lastTapTime = 0L

                                        val settings = playbackSettingsState.value
                                        val width = size.width
                                        val x = tapOffset.x

                                        if (!leftRippleActive && !rightRippleActive) {
                                            targetSeekPos = currentPositionState.value
                                        }

                                        when (settings.doubleTapAction) {
                                            DoubleTapAction.BOTH -> {
                                                if (x < width * 0.4f) {
                                                    targetSeekPos = (targetSeekPos - doubleTapSeekDurationMs).coerceIn(0L, durationState.value)
                                                    onSeekState.value(targetSeekPos, true)
                                                    leftClearJob?.cancel()
                                                    leftAccumulatedMs += doubleTapSeekDurationMs
                                                    leftRippleTick++
                                                    leftRippleActive = true
                                                    leftClearJob = launch {
                                                        delay(650L)
                                                        leftAccumulatedMs = 0L
                                                        leftRippleActive = false
                                                    }
                                                } else if (x > width * 0.6f) {
                                                    targetSeekPos = (targetSeekPos + doubleTapSeekDurationMs).coerceIn(0L, durationState.value)
                                                    onSeekState.value(targetSeekPos, true)
                                                    rightClearJob?.cancel()
                                                    rightAccumulatedMs += doubleTapSeekDurationMs
                                                    rightRippleTick++
                                                    rightRippleActive = true
                                                    rightClearJob = launch {
                                                        delay(650L)
                                                        rightAccumulatedMs = 0L
                                                        rightRippleActive = false
                                                    }
                                                } else {
                                                    onPlayPauseToggleState.value()
                                                }
                                            }
                                            DoubleTapAction.PLAY_PAUSE -> {
                                                onPlayPauseToggleState.value()
                                            }
                                            DoubleTapAction.FAST_FORWARD -> {
                                                targetSeekPos = (targetSeekPos + doubleTapSeekDurationMs).coerceIn(0L, durationState.value)
                                                onSeekState.value(targetSeekPos, true)
                                                rightClearJob?.cancel()
                                                rightAccumulatedMs += doubleTapSeekDurationMs
                                                rightRippleTick++
                                                rightRippleActive = true
                                                rightClearJob = launch {
                                                    delay(650L)
                                                    rightAccumulatedMs = 0L
                                                    rightRippleActive = false
                                                }
                                            }
                                            DoubleTapAction.REWIND -> {
                                                targetSeekPos = (targetSeekPos - doubleTapSeekDurationMs).coerceIn(0L, durationState.value)
                                                onSeekState.value(targetSeekPos, true)
                                                leftClearJob?.cancel()
                                                leftAccumulatedMs += doubleTapSeekDurationMs
                                                leftRippleTick++
                                                leftRippleActive = true
                                                leftClearJob = launch {
                                                    delay(650L)
                                                    leftAccumulatedMs = 0L
                                                    leftRippleActive = false
                                                }
                                            }
                                            DoubleTapAction.NONE -> {
                                                // Do nothing
                                            }
                                        }
                                    } else {
                                        lastTapTime = clickTime
                                        lastTapPosition = tapOffset
                                        tapJob = launch {
                                            delay(300L)
                                            onControlsVisibleChangedState.value(!controlsVisibleState.value)
                                        }
                                    }
                                }
                                break
                            }
                        }
                    }
                }
            }
    ) {
        if (playPauseRippleTick > 0) {
            CenterRippleWave(wasPlaying = isPlaying, rippleTick = playPauseRippleTick)
        }

        if (leftRippleActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
            ) {
                AccumulatingSeekRipple(
                    isRightSide = false,
                    accumulatedMs = leftAccumulatedMs,
                    rippleTick = leftRippleTick
                )
            }
        }

        if (rightRippleActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            ) {
                AccumulatingSeekRipple(
                    isRightSide = true,
                    accumulatedMs = rightAccumulatedMs,
                    rippleTick = rightRippleTick
                )
            }
        }

        // Multi-finger toast
        val toastMsg = showMultiFingerToast
        if (toastMsg != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 80.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = toastMsg,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Brightness bar
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
                    .padding(vertical = 24.dp, horizontal = 6.dp)
            ) {
                Icon(
                    imageVector = when {
                        currentBrightnessPercent < 33 -> Icons.Rounded.BrightnessLow
                        currentBrightnessPercent < 66 -> Icons.Rounded.BrightnessMedium
                        else -> Icons.Rounded.BrightnessHigh
                    },
                    contentDescription = "Brightness",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(150.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((currentBrightnessPercent / 100f).coerceIn(0f, 1f))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFFD600), Color(0xFFFF8F00))
                                )
                            )
                            .align(Alignment.BottomStart)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$currentBrightnessPercent%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Volume bar
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp)
        ) {
            val isBoosted = currentVolumePercent > 100
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .border(
                        1.dp,
                        if (isBoosted) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(vertical = 24.dp, horizontal = 6.dp)
            ) {
                Icon(
                    imageVector = when {
                        currentVolumePercent == 0 -> Icons.AutoMirrored.Rounded.VolumeOff
                        currentVolumePercent <= 50 -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = "Volume",
                    tint = if (isBoosted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(150.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                ) {
                    val maxVolumeVal = if (audioBoosterEnabledState.value) 2.0f else 1.0f
                    val volumeFraction = (currentVolumePercent / 100f) / maxVolumeVal
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(volumeFraction.coerceIn(0f, 1f))
                            .background(
                                if (isBoosted) Brush.verticalGradient(
                                    colors = listOf(MaterialTheme.colorScheme.error, Color(0xFFFF5252))
                                )
                                else Brush.verticalGradient(
                                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                )
                            )
                            .align(Alignment.BottomStart)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$currentVolumePercent%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isBoosted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }

        AnimatedVisibility(
            visible = isLongPressSpeedActive,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 50.dp)
        ) {
            FastForwardBadge(
                speed = tapAndHoldSpeed,
                isPlaying = isPlaying,
                visible = isLongPressSpeedActive
            )
        }

        // Mute/Unmute Toggle
        AnimatedVisibility(
            visible = muteOverlayVisible,
            enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            ) {
                Icon(
                    imageVector = if (isMutedState) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Mute Toggle",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Horizontal Seek Indicator
        AnimatedVisibility(
            visible = showSeekIndicator,
            enter = fadeIn(animationSpec = tween(120)) + slideInVertically(
                initialOffsetY = { -it / 3 },
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(animationSpec = tween(120)) + slideOutVertically(
                targetOffsetY = { -it / 3 },
                animationSpec = tween(150)
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 50.dp)
        ) {
            SeekIndicatorOverlay(
                currentSeekPos = currentSeekPos,
                duration = duration,
                seekDeltaSeconds = seekDeltaSeconds
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

@Composable
private fun CenterRippleWave(wasPlaying: Boolean, rippleTick: Int) {
    val ring1 = remember { Animatable(0f) }
    val ring2 = remember { Animatable(0f) }
    val ring3 = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }

    LaunchedEffect(rippleTick) {
        ring1.snapTo(0f); ring2.snapTo(0f); ring3.snapTo(0f); scrim.snapTo(0f)
        launch {
            scrim.animateTo(1f, tween(60))
            delay(180)
            scrim.animateTo(0f, tween(400, easing = EaseOut))
        }
        launch { ring1.animateTo(1f, tween(520, easing = EaseOut)) }
        launch { delay(80); ring2.animateTo(1f, tween(520, easing = EaseOut)) }
        launch { delay(180); ring3.animateTo(1f, tween(520, easing = EaseOut)) }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = kotlin.math.sqrt(cx * cx + cy * cy)

        drawRect(color = Color.White.copy(alpha = 0.07f * scrim.value), size = size)

        val r1 = maxRadius * ring1.value
        val a1 = (1f - ring1.value).coerceIn(0f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = 0.55f * a1),
            radius = r1,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5.dp.toPx())
        )

        val r2 = maxRadius * ring2.value
        val a2 = (1f - ring2.value).coerceIn(0f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = 0.30f * a2),
            radius = r2,
            center = Offset(cx, cy),
            style = Stroke(width = 1.8.dp.toPx())
        )

        val r3 = maxRadius * ring3.value
        val a3 = (1f - ring3.value).coerceIn(0f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = 0.15f * a3),
            radius = r3,
            center = Offset(cx, cy),
            style = Stroke(width = 1.2.dp.toPx())
        )

        val burstAlpha = ((1f - ring1.value) * 0.18f).coerceIn(0f, 0.18f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = burstAlpha), Color.Transparent),
                center = Offset(cx, cy),
                radius = maxRadius * 0.35f * ring1.value.coerceAtLeast(0.01f)
            ),
            radius = maxRadius * 0.35f * ring1.value.coerceAtLeast(0.01f),
            center = Offset(cx, cy)
        )
    }
}

@Composable
private fun FastForwardBadge(speed: Float, isPlaying: Boolean, visible: Boolean) {
    val chevron1Alpha: Float
    val chevron2Alpha: Float
    val chevron3Alpha: Float

    if (visible && isPlaying) {
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
        chevron1Alpha = c1
        chevron2Alpha = c2
        chevron3Alpha = c3
    } else {
        chevron1Alpha = 0.8f
        chevron2Alpha = 0.8f
        chevron3Alpha = 0.8f
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(
                brush = Brush.horizontalGradient(colors = listOf(Color(0x00000000), Color(0x99000000), Color(0x00000000))),
                shape = RoundedCornerShape(50)
            )
            .border(
                width = 0.5.dp,
                brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent)),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = chevron1Alpha),
                modifier = Modifier.size(14.dp)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = chevron2Alpha),
                modifier = Modifier.size(16.dp)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = chevron3Alpha),
                modifier = Modifier.size(18.dp)
            )
        }
        Text(text = "${speed}x", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(text = "Speed", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
    }
}

@Composable
private fun AccumulatingSeekRipple(isRightSide: Boolean, accumulatedMs: Long, rippleTick: Int) {
    val secs  = accumulatedMs / 1000L
    val label = if (isRightSide) "+${secs}s" else "-${secs}s"
    val arcProgress = remember { Animatable(0f) }
    LaunchedEffect(rippleTick) {
        if (rippleTick > 0) {
            arcProgress.snapTo(0f)
            arcProgress.animateTo(
                targetValue   = 1f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            )
        }
    }
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(rippleTick) {
        if (rippleTick > 0) {
            flashAlpha.snapTo(0.22f)
            flashAlpha.animateTo(0f, animationSpec = tween(500))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(150.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (isRightSide)
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.10f + flashAlpha.value))
                    else
                        listOf(Color.White.copy(alpha = 0.10f + flashAlpha.value), Color.Transparent)
                ),
                shape = if (isRightSide)
                    RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                else
                    RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(modifier = Modifier.size(58.dp)) {
                val stroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                val sweep  = 250f * arcProgress.value
                val bgStartAngle = if (isRightSide) 140f else -70f
                val bgSweep      = if (isRightSide) 250f else -250f
                drawArc(color = Color.White.copy(alpha = 0.18f), startAngle = bgStartAngle, sweepAngle = bgSweep, useCenter = false, style = stroke)
                drawArc(color = Color.White.copy(alpha = 0.90f), startAngle = bgStartAngle, sweepAngle = if (isRightSide) sweep else -sweep, useCenter = false, style = stroke)
                val cx = size.width / 2f
                val cy = size.height / 2f
                val aW = 7.dp.toPx()
                val aH = 10.dp.toPx()
                val gap = 5.dp.toPx()
                val arrowColor = Color.White
                val sw = 2.6.dp.toPx()
                if (isRightSide) {
                    drawLine(arrowColor, Offset(cx - aW - gap, cy - aH / 2), Offset(cx - gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx - aW - gap, cy + aH / 2), Offset(cx - gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx - gap, cy - aH / 2), Offset(cx + aW - gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx - gap, cy + aH / 2), Offset(cx + aW - gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                } else {
                    drawLine(arrowColor, Offset(cx + aW + gap, cy - aH / 2), Offset(cx + gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx + aW + gap, cy + aH / 2), Offset(cx + gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx + gap, cy - aH / 2), Offset(cx - aW + gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx + gap, cy + aH / 2), Offset(cx - aW + gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                }
            }
            Text(text = label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(text = if (isRightSide) "Forward" else "Rewind", color = Color.White.copy(alpha = 0.70f), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SeekIndicatorOverlay(
    currentSeekPos: Long,
    duration: Long,
    seekDeltaSeconds: Int
) {
    val isForward = seekDeltaSeconds >= 0
    val accentColor = if (isForward) Color(0xFF00E5FF) else Color(0xFFFF6B6B)
    val progressFraction = if (duration > 0L) (currentSeekPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val deltaLabel = if (isForward) "+${seekDeltaSeconds}s" else "${seekDeltaSeconds}s"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .widthIn(min = 200.dp, max = 320.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.55f),
                        Color.Black.copy(alpha = 0.40f)
                    )
                )
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Direction chip + time row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Direction pill chip
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(0.6.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isForward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = deltaLabel,
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            // Current / Total time
            Text(
                text = "${formatTime(currentSeekPos)}  /  ${formatTime(duration)}",
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Seek progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.7f),
                                accentColor
                            )
                        ),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            // Thumb dot - drawn as a small overlay box pinned to fill-end via the progress box above
        }
    }
}
