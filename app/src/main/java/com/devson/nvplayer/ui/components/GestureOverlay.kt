package com.devson.nvplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.repository.DoubleTapAction
import com.devson.nvplayer.repository.MultiFingerAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.roundToInt

@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    seekDurationSeconds: Int,
    isPlaying: Boolean = false,
    isLocked: Boolean = false,
    fastplaySpeed: Float = 2.0f,
    currentPosition: Long = 0L,
    duration: Long = 0L,
    seekGestureEnabled: Boolean = true,
    seekSensitivity: Float = 0.5f,
    brightnessGestureEnabled: Boolean = true,
    brightnessSensitivity: Float = 0.5f,
    volumeGestureEnabled: Boolean = true,
    volumeSensitivity: Float = 0.5f,
    twoFingerAction: MultiFingerAction = MultiFingerAction.PLAY_PAUSE,
    threeFingerAction: MultiFingerAction = MultiFingerAction.FAST_PLAY,
    longPressEnabled: Boolean = true,
    longPressSpeed: Float = 2.0f,
    doubleTapAction: DoubleTapAction = DoubleTapAction.BOTH,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onSeekSwipe: (Float) -> Unit = {},
    onVolumeSwipe: (Float) -> Unit,
    onBrightnessSwipe: (Float) -> Unit,
    onSeekStart: () -> Unit = {},
    onSeekPreview: (Long) -> Unit = {},
    onSeekCommit: (Long) -> Unit = {},
    onFastForwardToggle: (Boolean, Float) -> Unit = { _, _ -> },
    onZoom: (scaleFactor: Float) -> Unit = {},
    zoomScale: Float = 1f,
    volumeLevel: Float,
    brightnessLevel: Float,
    showVolumeFeedback: Boolean,
    showBrightnessFeedback: Boolean,
    isAudioBoostEnabled: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val updatedSeekDuration by rememberUpdatedState(seekDurationSeconds)
    val updatedCurrentPosition by rememberUpdatedState(currentPosition)
    val updatedDuration by rememberUpdatedState(duration)

    var showCenterRipple by remember { mutableStateOf(false) }
    var centerWasPlaying by remember { mutableStateOf(false) }
    var centerRippleTick by remember { mutableStateOf(0) }
    LaunchedEffect(centerRippleTick) {
        if (centerRippleTick > 0) {
            showCenterRipple = true
            delay(900)
            showCenterRipple = false
        }
    }

    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapX    by remember { mutableStateOf(0f) }
    var lastTapY    by remember { mutableStateOf(0f) }
    var singleTapJob by remember { mutableStateOf<Job?>(null) }

    var scrubPreviewMs by remember { mutableStateOf<Long?>(null) }
    var scrubStartPosition by remember { mutableStateOf(0L) }

    var isFastForwarding by remember { mutableStateOf(false) }
    // FIX: isFastForwardLocked moved inside awaitEachGesture so it resets each gesture.
    // It is hoisted here only for the UI badge — use a separate UI state var.
    var isFastForwardLocked by remember { mutableStateOf(false) }

    var accumulatedLeftMs  by remember { mutableLongStateOf(0L) }
    var accumulatedRightMs by remember { mutableLongStateOf(0L) }
    var showLeftSeek       by remember { mutableStateOf(false) }
    var showRightSeek      by remember { mutableStateOf(false) }
    var leftRippleTick  by remember { mutableStateOf(0) }
    var rightRippleTick by remember { mutableStateOf(0) }

    LaunchedEffect(leftRippleTick) {
        if (leftRippleTick > 0) {
            delay(1200)
            showLeftSeek = false
            delay(400)
            accumulatedLeftMs = 0L
        }
    }

    LaunchedEffect(rightRippleTick) {
        if (rightRippleTick > 0) {
            delay(1200)
            showRightSeek = false
            delay(400)
            accumulatedRightMs = 0L
        }
    }

    var showZoomIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(zoomScale) {
        if (zoomScale > 1.01f) {
            showZoomIndicator = true
            delay(900)
            showZoomIndicator = false
        } else {
            showZoomIndicator = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked, onFastForwardToggle) {
                val slopPx = 24.dp.toPx()
                val twoFingerSlopPx = 24.dp.toPx()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = System.currentTimeMillis()

                    if (isLocked) {
                        var isTap = true
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (abs(dx) > slopPx || abs(dy) > slopPx) isTap = false
                            change.consume()
                            if (!change.pressed) break
                        }
                        if (isTap) onSingleTap()
                        return@awaitEachGesture
                    }

                    val startX = down.position.x
                    val isRightSide = startX > size.width / 2f

                    var totalDx = 0f
                    var totalDy = 0f
                    var isSwiping = false
                    var swipeAxis = ""

                    // FIX: Use an array so the coroutine always reads the live value,
                    // not a snapshot captured at launch time.
                    val longPressArmed = booleanArrayOf(true)

                    var isLongPressActive = false
                    var threeFingerTriggered = false

                    var twoFingerDown = false
                    var twoFingerStartTime = 0L
                    var twoFingerMaxMovePx = 0f
                    var twoFingerHandled = false
                    var wasPinching = false
                    var lastSpan = 0f

                    val longPressJob = coroutineScope.launch {
                        delay(550)
                        // Reads live value via array reference — not a stale copy
                        if (longPressEnabled && isPlaying && longPressArmed[0] && !isFastForwardLocked) {
                            isLongPressActive = true
                            isFastForwarding = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFastForwardToggle(true, longPressSpeed)
                        }
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointerCount = event.changes.count { it.pressed }

                            if (event.changes.size >= 3 && !threeFingerTriggered) {
                                threeFingerTriggered = true
                                longPressArmed[0] = false
                                longPressJob.cancel()
                                when(threeFingerAction) {
                                    MultiFingerAction.FAST_PLAY -> {
                                        isFastForwardLocked = !isFastForwardLocked
                                        isFastForwarding = isFastForwardLocked
                                        onFastForwardToggle(isFastForwardLocked, longPressSpeed)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    MultiFingerAction.PLAY_PAUSE -> {
                                        centerWasPlaying = isPlaying
                                        centerRippleTick++
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDoubleTapCenter()
                                    }
                                    MultiFingerAction.NONE -> {}
                                }
                            }

                            if (!twoFingerHandled && !threeFingerTriggered) {
                                if (pointerCount == 2 && !twoFingerDown) {
                                    twoFingerDown = true
                                    longPressArmed[0] = false
                                    longPressJob.cancel()
                                    twoFingerStartTime = System.currentTimeMillis()
                                    twoFingerMaxMovePx = 0f
                                    val pts = event.changes.filter { it.pressed }
                                    if (pts.size == 2) {
                                        val dx0 = pts[0].position.x - pts[1].position.x
                                        val dy0 = pts[0].position.y - pts[1].position.y
                                        lastSpan = sqrt(dx0 * dx0 + dy0 * dy0).coerceAtLeast(1f)
                                    }
                                }

                                if (twoFingerDown && pointerCount == 2) {
                                    val pts = event.changes.filter { it.pressed }
                                    if (pts.size == 2) {
                                        for (ch in pts) {
                                            val mov = sqrt(
                                                (ch.position.x - ch.previousPosition.x).let { it * it } +
                                                        (ch.position.y - ch.previousPosition.y).let { it * it }
                                            )
                                            if (mov > twoFingerMaxMovePx) twoFingerMaxMovePx = mov
                                        }
                                        val dx = pts[0].position.x - pts[1].position.x
                                        val dy = pts[0].position.y - pts[1].position.y
                                        val currentSpan = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                                        if (twoFingerMaxMovePx > twoFingerSlopPx && lastSpan > 0f) {
                                            wasPinching = true
                                            onZoom(currentSpan / lastSpan)
                                            pts.forEach { it.consume() }
                                        }
                                        lastSpan = currentSpan
                                    }
                                }

                                if (twoFingerDown && pointerCount < 2) {
                                    val elapsed = System.currentTimeMillis() - twoFingerStartTime
                                    if (!wasPinching && elapsed < 300 && twoFingerMaxMovePx < twoFingerSlopPx) {
                                        twoFingerHandled = true
                                        when(twoFingerAction) {
                                            MultiFingerAction.PLAY_PAUSE -> {
                                                centerWasPlaying = isPlaying
                                                centerRippleTick++
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onDoubleTapCenter()
                                            }
                                            MultiFingerAction.FAST_PLAY -> {
                                                isFastForwardLocked = !isFastForwardLocked
                                                isFastForwarding = isFastForwardLocked
                                                onFastForwardToggle(isFastForwardLocked, longPressSpeed)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            MultiFingerAction.NONE -> {}
                                        }
                                        event.changes.forEach { it.consume() }
                                        break
                                    }
                                    twoFingerDown = false
                                    lastSpan = 0f
                                }
                            }

                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) {
                                longPressArmed[0] = false
                                longPressJob.cancel()

                                if (isLongPressActive) break

                                if (threeFingerTriggered || twoFingerHandled) break

                                if (!isSwiping && !twoFingerDown && !wasPinching) {
                                    // FIX: If finger was held > 300ms but long-press didn't
                                    // fire (e.g. video paused), treat as cancelled — not a tap.
                                    val heldMs = System.currentTimeMillis() - downTime
                                    if (heldMs > 300L) break

                                    val now = System.currentTimeMillis()
                                    val dt = now - lastTapTime
                                    val dx = change.position.x - lastTapX
                                    val dy = change.position.y - lastTapY
                                    val dist = sqrt(dx * dx + dy * dy)
                                    val tapX = change.position.x

                                    val isLeftTap = tapX < size.width * 0.33f
                                    val isRightTap = tapX > size.width * 0.66f

                                    val continuingLeftSeek = showLeftSeek && isLeftTap && (now - lastTapTime < 800)
                                    val continuingRightSeek = showRightSeek && isRightTap && (now - lastTapTime < 800)

                                    val canSeek = doubleTapAction == DoubleTapAction.BOTH || doubleTapAction == DoubleTapAction.FAST_FORWARD_REWIND
                                    val canPlayPause = doubleTapAction == DoubleTapAction.BOTH || doubleTapAction == DoubleTapAction.PLAY_PAUSE

                                    if (continuingLeftSeek || continuingRightSeek || (dt < 400 && dist < 100f)) {
                                        singleTapJob?.cancel()
                                        lastTapTime = now
                                        lastTapX = change.position.x
                                        lastTapY = change.position.y

                                        when {
                                            isLeftTap && canSeek -> {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                accumulatedLeftMs += updatedSeekDuration * 1000L
                                                showLeftSeek = true
                                                leftRippleTick++
                                                onDoubleTapLeft()
                                            }
                                            isRightTap && canSeek -> {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                accumulatedRightMs += updatedSeekDuration * 1000L
                                                showRightSeek = true
                                                rightRippleTick++
                                                onDoubleTapRight()
                                            }
                                            (!isLeftTap && !isRightTap) && canPlayPause -> {
                                                if (dt < 400 && dist < 100f) {
                                                    centerWasPlaying = isPlaying
                                                    centerRippleTick++
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onDoubleTapCenter()
                                                }
                                            }
                                        }
                                    } else {
                                        lastTapTime = now
                                        lastTapX = change.position.x
                                        lastTapY = change.position.y
                                        singleTapJob = coroutineScope.launch { delay(300); onSingleTap() }
                                    }
                                }
                                break
                            }

                            if (threeFingerTriggered) {
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            if (isLongPressActive) {
                                change.consume()
                                isSwiping = true
                                continue
                            }

                            if (twoFingerDown) continue

                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            totalDx += dx
                            totalDy += dy

                            if (!isSwiping && (abs(totalDx) > slopPx || abs(totalDy) > slopPx)) {
                                isSwiping = true
                                longPressArmed[0] = false
                                longPressJob.cancel()
                                swipeAxis = if (abs(totalDx) > abs(totalDy)) "HORIZONTAL" else "VERTICAL"
                            }

                            if (isSwiping) {
                                change.consume()
                                if (isFastForwardLocked) continue

                                when (swipeAxis) {
                                    "VERTICAL" -> {
                                        if (isRightSide && !volumeGestureEnabled) continue
                                        if (!isRightSide && !brightnessGestureEnabled) continue

                                        val userSens = if (isRightSide) volumeSensitivity else brightnessSensitivity
                                        val multiplier = 0.4f + (userSens * 1.6f)
                                        val delta = (-dy / size.height.toFloat()) * (1.2f * multiplier)

                                        if (isRightSide) onVolumeSwipe(delta) else onBrightnessSwipe(delta)
                                    }
                                    "HORIZONTAL" -> {
                                        if (!seekGestureEnabled) continue

                                        val multiplier = 0.4f + (seekSensitivity * 1.6f)
                                        val dur = updatedDuration
                                        if (dur > 0L) {
                                            if (scrubPreviewMs == null) {
                                                scrubStartPosition = updatedCurrentPosition
                                                onSeekStart()
                                            }
                                            val deltaMs = (totalDx / size.width.toFloat()) * (120_000L * multiplier)
                                            val newPreview = (scrubStartPosition + deltaMs).toLong().coerceIn(0L, dur)
                                            scrubPreviewMs = newPreview
                                            onSeekPreview(newPreview)
                                        } else {
                                            scrubPreviewMs = ((totalDx / size.width.toFloat()) * (120_000L * multiplier)).toLong()
                                        }
                                        onSeekSwipe(dx)
                                    }
                                }
                            }
                        }
                    } finally {
                        longPressArmed[0] = false
                        longPressJob.cancel()
                        if (isLongPressActive) {
                            isLongPressActive = false
                            if (!isFastForwardLocked) {
                                isFastForwarding = false
                                onFastForwardToggle(false, longPressSpeed)
                            }
                        }
                        if (isSwiping && swipeAxis == "HORIZONTAL") {
                            scrubPreviewMs?.let { previewPos ->
                                onSeekCommit(previewPos)
                            }
                            scrubPreviewMs = null
                        }
                    }
                }
            }
    ) {
        AnimatedVisibility(
            visible = showBrightnessFeedback,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = tween(180)),
            exit  = fadeOut(tween(280)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ModernEdgeSlider(level = brightnessLevel, isVolume = false)
        }

        AnimatedVisibility(
            visible = showVolumeFeedback,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = tween(180)),
            exit  = fadeOut(tween(280)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            ModernEdgeSlider(level = volumeLevel, isVolume = true, isAudioBoostEnabled = isAudioBoostEnabled)
        }

        AnimatedVisibility(
            visible = scrubPreviewMs != null,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.9f),
            exit  = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            scrubPreviewMs?.let { previewMs ->
                val dur = duration.coerceAtLeast(1L)
                val deltaMs = previewMs - currentPosition
                ScrubOverlay(deltaMs = deltaMs, previewMs = previewMs)
            }
        }

        AnimatedVisibility(
            visible = showZoomIndicator,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)),
            exit  = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            val zoomText = String.format("%.1fx", zoomScale)
            androidx.compose.foundation.layout.Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(
                        color = Color(0xCC000000),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Zoomed $zoomText",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        AnimatedVisibility(
            visible = showLeftSeek,
            enter = fadeIn(tween(100)),
            exit  = fadeOut(tween(350)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            AccumulatingSeekRipple(
                isRightSide    = false,
                accumulatedMs  = accumulatedLeftMs,
                rippleTick     = leftRippleTick
            )
        }

        AnimatedVisibility(
            visible = showCenterRipple,
            enter = fadeIn(tween(60)) + scaleIn(
                initialScale = 0.65f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit  = fadeOut(tween(380)) + scaleOut(targetScale = 1.2f, animationSpec = tween(380)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CenterRippleWave(wasPlaying = centerWasPlaying, rippleTick = centerRippleTick)
        }

        AnimatedVisibility(
            visible = showRightSeek,
            enter = fadeIn(tween(100)),
            exit  = fadeOut(tween(350)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            AccumulatingSeekRipple(
                isRightSide   = true,
                accumulatedMs = accumulatedRightMs,
                rippleTick    = rightRippleTick
            )
        }

        AnimatedVisibility(
            visible = isFastForwarding,
            enter = fadeIn(tween(150)) + slideInVertically(initialOffsetY = { -it }) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        ) {
            FastForwardBadge(speed = if(isFastForwarding) longPressSpeed else fastplaySpeed)
        }
    }
}

@Composable
private fun ModernEdgeSlider(level: Float, isVolume: Boolean, isAudioBoostEnabled: Boolean = false) {
    val clampedLevel = level.coerceIn(0f, 1f)
    val animLevel by animateFloatAsState(
        targetValue  = clampedLevel,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label        = "edgeSliderLevel"
    )
    val displayValue = if (!isVolume) {
        val percentage = (clampedLevel * 100).toInt()
        "$percentage%"
    } else {
        val maxSteps = if (isAudioBoostEnabled) 30 else 15
        val currentStep = (clampedLevel * maxSteps).roundToInt().coerceIn(0, maxSteps)
        "$currentStep"
    }

    val volumeIcon = when {
        !isVolume          -> Icons.Filled.BrightnessMedium
        clampedLevel == 0f -> Icons.AutoMirrored.Filled.VolumeOff
        clampedLevel < 0.3f -> Icons.AutoMirrored.Filled.VolumeMute
        clampedLevel < 0.6f -> Icons.AutoMirrored.Filled.VolumeDown
        else               -> Icons.AutoMirrored.Filled.VolumeUp
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector  = volumeIcon,
                contentDescription = null,
                tint         = Color.White,
                modifier     = Modifier.size(22.dp)
            )
            Canvas(
                modifier = Modifier
                    .height(130.dp)
                    .width(6.dp)
            ) {
                val trackH = size.height
                val trackW = size.width
                val radius = trackW / 2f
                drawRoundRect(
                    color        = Color.White.copy(alpha = 0.25f),
                    size         = Size(trackW, trackH),
                    cornerRadius = CornerRadius(radius, radius)
                )
                val filledH = trackH * animLevel
                drawRoundRect(
                    color        = Color.White,
                    topLeft      = Offset(0f, trackH - filledH),
                    size         = Size(trackW, filledH),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
            Text(
                text      = displayValue,
                color     = Color.White,
                fontSize  = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ScrubOverlay(deltaMs: Long, previewMs: Long = 0L) {
    val sign      = if (deltaMs >= 0) "+" else ""
    val secs      = deltaMs / 1000L
    val isForward = deltaMs >= 0
    val totalSec  = previewMs / 1000L
    val ph        = totalSec / 3600
    val pm        = (totalSec % 3600) / 60
    val ps        = totalSec % 60
    val timeLabel = if (ph > 0) "%d:%02d:%02d".format(ph, pm, ps) else "%02d:%02d".format(pm, ps)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(36.dp))
            .background(Color.Black.copy(alpha = 0.70f))
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isForward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                    contentDescription = null,
                    tint     = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text       = "$sign${secs}s",
                    color      = Color.White,
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (previewMs > 0L) {
                Text(
                    text      = timeLabel,
                    color     = Color.White.copy(alpha = 0.80f),
                    fontSize  = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
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
private fun FastForwardBadge(speed: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "speedPulse")
    val chevron1Alpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(420, delayMillis = 0, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "c1")
    val chevron2Alpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(420, delayMillis = 140, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "c2")
    val chevron3Alpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(420, delayMillis = 280, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "c3")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(
                brush = Brush.horizontalGradient(colors = listOf(Color(0x00000000), Color(0x99000000), Color(0x00000000))),
                shape = RoundedCornerShape(50)
            )
            .border(width = 0.5.dp, brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent)), shape = RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = chevron1Alpha), modifier = Modifier.size(14.dp))
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = chevron2Alpha), modifier = Modifier.size(16.dp))
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = chevron3Alpha), modifier = Modifier.size(18.dp))
        }
        Text(text = "${speed}x", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(text = "Speed", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
    }
}