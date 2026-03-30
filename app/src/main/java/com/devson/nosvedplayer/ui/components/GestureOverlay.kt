package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

//  Public API 
@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    seekDurationSeconds: Int,
    isPlaying: Boolean = false,
    isLocked: Boolean = false,
    fastplaySpeed: Float = 2.0f,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onSeekSwipe: (Float) -> Unit = {},
    onVolumeSwipe: (Float) -> Unit,
    onBrightnessSwipe: (Float) -> Unit,
    onSeekCommit: (Long) -> Unit = {},
    onFastForwardToggle: (Boolean) -> Unit = {},
    volumeLevel: Float,
    brightnessLevel: Float,
    showVolumeFeedback: Boolean,
    showBrightnessFeedback: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    //  Center ripple 
    var showCenterRipple by remember { mutableStateOf(false) }
    var centerWasPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(showCenterRipple) {
        if (showCenterRipple) { delay(650); showCenterRipple = false }
    }

    //  Double-tap tracking 
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapX    by remember { mutableStateOf(0f) }
    var lastTapY    by remember { mutableStateOf(0f) }
    var singleTapJob by remember { mutableStateOf<Job?>(null) }

    //  Horizontal scrub 
    var scrubDeltaMs by remember { mutableStateOf<Long?>(null) }

    //  Long-press fast-forward 
    var isFastForwarding by remember { mutableStateOf(false) }

    //  Accumulating double-tap seek 
    var accumulatedLeftMs  by remember { mutableLongStateOf(0L) }
    var accumulatedRightMs by remember { mutableLongStateOf(0L) }
    var seekDebounceJob    by remember { mutableStateOf<Job?>(null) }
    var showLeftSeek       by remember { mutableStateOf(false) }
    var showRightSeek      by remember { mutableStateOf(false) }
    // Ripple trigger counters (increment each tap to re-trigger animation)
    var leftRippleTick  by remember { mutableStateOf(0) }
    var rightRippleTick by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                val slopPx = 10.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)

                    //  Locked mode: only allow single-tap 
                    if (isLocked) {
                        var isTap = true
                        while (true) {
                            val event  = awaitPointerEvent()
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

                    //  Touch zone detection 
                    val startX     = down.position.x
                    val isLeftSide  = startX < size.width * 0.25f
                    val isRightSide = startX > size.width * 0.75f
                    val isCenterZone = !isLeftSide && !isRightSide

                    var totalDx     = 0f
                    var totalDy     = 0f
                    var isSwiping   = false
                    var swipeAxis   = ""

                    //  Long-press → fast-forward 
                    var isLongPressActive = false
                    val longPressJob = coroutineScope.launch {
                        delay(550)
                        if (isPlaying && !isSwiping) {
                            isLongPressActive = true
                            isFastForwarding  = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFastForwardToggle(true)
                        }
                    }

                    //  Main event loop 
                    while (true) {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.isConsumed) break

                        if (!change.pressed) {
                            longPressJob.cancel()

                            // Release fast-forward
                            if (isLongPressActive) {
                                isFastForwarding = false
                                onFastForwardToggle(false)
                                break
                            }

                            // Commit horizontal scrub
                            if (isSwiping && swipeAxis == "HORIZONTAL") {
                                scrubDeltaMs?.let { delta ->
                                    if (delta != 0L) onSeekCommit(delta)
                                }
                                scrubDeltaMs = null
                            }

                            //  Tap detection 
                            if (!isSwiping) {
                                val now  = System.currentTimeMillis()
                                val dt   = now - lastTapTime
                                val ddx  = change.position.x - lastTapX
                                val ddy  = change.position.y - lastTapY
                                val dist = sqrt(ddx * ddx + ddy * ddy)

                                if (dt < 400 && dist < 100f) {
                                    //  Double-tap 
                                    singleTapJob?.cancel()
                                    lastTapTime = 0L
                                    val tapX = change.position.x
                                    when {
                                        tapX < size.width * 0.33f -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val stepMs = seekDurationSeconds * 1000L
                                            accumulatedLeftMs += stepMs
                                            showLeftSeek = true
                                            leftRippleTick++
                                            onSeekCommit(-stepMs)
                                            seekDebounceJob?.cancel()
                                            seekDebounceJob = coroutineScope.launch {
                                                delay(1800)
                                                accumulatedLeftMs = 0L
                                                showLeftSeek = false
                                            }
                                        }
                                        tapX > size.width * 0.66f -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val stepMs = seekDurationSeconds * 1000L
                                            accumulatedRightMs += stepMs
                                            showRightSeek = true
                                            rightRippleTick++
                                            onSeekCommit(stepMs)
                                            seekDebounceJob?.cancel()
                                            seekDebounceJob = coroutineScope.launch {
                                                delay(1800)
                                                accumulatedRightMs = 0L
                                                showRightSeek = false
                                            }
                                        }
                                        else -> {
                                            // Center: play/pause
                                            centerWasPlaying = isPlaying
                                            showCenterRipple = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDoubleTapCenter()
                                        }
                                    }
                                } else {
                                    //  Single-tap (delayed) 
                                    lastTapTime = now
                                    lastTapX    = change.position.x
                                    lastTapY    = change.position.y
                                    singleTapJob = coroutineScope.launch {
                                        delay(300)
                                        onSingleTap()
                                    }
                                }
                            }
                            break
                        }

                        //  Drag delta tracking 
                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        totalDx += dx
                        totalDy += dy

                        if (!isSwiping && (abs(totalDx) > slopPx || abs(totalDy) > slopPx)) {
                            isSwiping = true
                            longPressJob.cancel()
                            if (isLongPressActive) {
                                isFastForwarding  = false
                                onFastForwardToggle(false)
                                isLongPressActive = false
                            }
                            swipeAxis = if (abs(totalDx) > abs(totalDy)) "HORIZONTAL" else "VERTICAL"
                        }

                        if (isSwiping) {
                            // Let system handle center-zone vertical swipes (notifications)
                            if (isCenterZone && swipeAxis == "VERTICAL") {
                                // do not consume
                            } else {
                                change.consume()
                            }
                            if (isLongPressActive) break

                            when (swipeAxis) {
                                "VERTICAL" -> {
                                    if (!isCenterZone) {
                                        val sensitivity = 1.2f
                                        val delta = (-dy / size.height.toFloat()) * sensitivity
                                        if (isRightSide) onVolumeSwipe(delta)
                                        else if (isLeftSide) onBrightnessSwipe(delta)
                                    }
                                }
                                "HORIZONTAL" -> {
                                    val deltaMs = (totalDx / size.width.toFloat()) * 100_000L
                                    scrubDeltaMs = deltaMs.toLong()
                                    onSeekSwipe(dx)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        //  Brightness slider (left edge) 
        AnimatedVisibility(
            visible = showBrightnessFeedback,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = tween(180)),
            exit  = fadeOut(tween(280)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ModernEdgeSlider(level = brightnessLevel, isVolume = false)
        }

        //  Volume slider (right edge) 
        AnimatedVisibility(
            visible = showVolumeFeedback,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = tween(180)),
            exit  = fadeOut(tween(280)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            ModernEdgeSlider(level = volumeLevel, isVolume = true)
        }

        //  Horizontal scrub overlay (center) 
        AnimatedVisibility(
            visible = scrubDeltaMs != null,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.9f),
            exit  = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            scrubDeltaMs?.let { deltaMs ->
                ScrubOverlay(deltaMs = deltaMs)
            }
        }

        //  Left accumulating seek 
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

        //  Center play/pause ripple 
        AnimatedVisibility(
            visible = showCenterRipple,
            enter = fadeIn(tween(80)) + scaleIn(initialScale = 0.7f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit  = fadeOut(tween(400)) + scaleOut(targetScale = 1.15f, animationSpec = tween(400)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CenterRipple(wasPlaying = centerWasPlaying)
        }

        //  Right accumulating seek 
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

        //  Fast-forward badge
        AnimatedVisibility(
            visible = isFastForwarding,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f),
            exit  = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            FastForwardBadge(speed = fastplaySpeed)
        }
    }
}

//  Modern edge slider  (volume / brightness)

@Composable
private fun ModernEdgeSlider(level: Float, isVolume: Boolean) {
    val clampedLevel = level.coerceIn(0f, 1f)
    val animLevel by animateFloatAsState(
        targetValue  = clampedLevel,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label        = "edgeSliderLevel"
    )
    val percentage = (clampedLevel * 100).toInt()

    // Choose volume icon tier
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

            // Arc/pill track
            Canvas(
                modifier = Modifier
                    .height(130.dp)
                    .width(6.dp)
            ) {
                val trackH = size.height
                val trackW = size.width
                val radius = trackW / 2f

                // Background track
                drawRoundRect(
                    color        = Color.White.copy(alpha = 0.25f),
                    size         = Size(trackW, trackH),
                    cornerRadius = CornerRadius(radius, radius)
                )
                // Filled portion (bottom-up)
                val filledH = trackH * animLevel
                drawRoundRect(
                    color        = Color.White,
                    topLeft      = Offset(0f, trackH - filledH),
                    size         = Size(trackW, filledH),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }

            Text(
                text      = "$percentage%",
                color     = Color.White,
                fontSize  = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

//  Horizontal-scrub overlay

@Composable
private fun ScrubOverlay(deltaMs: Long) {
    val sign    = if (deltaMs >= 0) "+" else ""
    val secs    = deltaMs / 1000L
    val isForward = deltaMs >= 0

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(36.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment    = Alignment.CenterVertically,
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
    }
}

//  Accumulating seek ripple  (YouTube-style double-tap arc)
@Composable
private fun AccumulatingSeekRipple(
    isRightSide: Boolean,
    accumulatedMs: Long,
    rippleTick: Int
) {
    val secs  = accumulatedMs / 1000L
    val label = if (isRightSide) "+${secs}s" else "-${secs}s"

    // Animated arc sweep (0 → 1 on each new tap)
    val arcProgress = remember { Animatable(0f) }
    LaunchedEffect(rippleTick) {
        if (rippleTick > 0) {
            arcProgress.snapTo(0f)
            arcProgress.animateTo(
                targetValue   = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(140.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (isRightSide)
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.13f))
                    else
                        listOf(Color.White.copy(alpha = 0.13f), Color.Transparent)
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
            // Arc indicator
            Canvas(modifier = Modifier.size(54.dp)) {
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                val sweep  = 260f * arcProgress.value
                val startAngle = if (isRightSide) 140f else -20f - sweep + (260f - sweep)

                // Background arc
                drawArc(
                    color      = Color.White.copy(alpha = 0.2f),
                    startAngle = if (isRightSide) 140f else -120f,
                    sweepAngle = 260f,
                    useCenter  = false,
                    style      = stroke
                )
                // Animated fill arc
                drawArc(
                    color      = Color.White.copy(alpha = 0.85f),
                    startAngle = if (isRightSide) 140f else -120f,
                    sweepAngle = if (isRightSide) sweep else -sweep + 260f,
                    useCenter  = false,
                    style      = stroke
                )

                // Chevron arrows
                val cx = size.width / 2f
                val cy = size.height / 2f
                val arrowColor = Color.White
                val aW = 8.dp.toPx()
                val aH = 12.dp.toPx()
                if (isRightSide) {
                    drawLine(arrowColor, Offset(cx - aW, cy - aH / 2), Offset(cx, cy), strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx - aW, cy + aH / 2), Offset(cx, cy), strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx,      cy - aH / 2), Offset(cx + aW, cy), strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx,      cy + aH / 2), Offset(cx + aW, cy), strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                } else {
                    drawLine(arrowColor, Offset(cx + aW, cy - aH / 2), Offset(cx, cy), strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx + aW, cy + aH / 2), Offset(cx, cy), strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx,      cy - aH / 2), Offset(cx - aW, cy), strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx,      cy + aH / 2), Offset(cx - aW, cy), strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                }
            }

            Text(
                text       = label,
                color      = Color.White,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )

            Text(
                text      = if (isRightSide) "Forward" else "Rewind",
                color     = Color.White.copy(alpha = 0.65f),
                fontSize  = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

//  Center play/pause ripple

@Composable
private fun CenterRipple(wasPlaying: Boolean) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.50f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector  = if (wasPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (wasPlaying) "Paused" else "Playing",
            tint         = Color.White,
            modifier     = Modifier.size(48.dp)
        )
    }
}

//  Fast-forward badge (long-press hold indicator)

@Composable
private fun FastForwardBadge(speed: Float) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(36.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = null,
                tint     = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text       = "${speed}x Speed",
                color      = Color.White,
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}