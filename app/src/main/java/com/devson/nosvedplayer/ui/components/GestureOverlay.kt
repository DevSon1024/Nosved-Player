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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
    showBrightnessFeedback: Boolean,
    isAudioBoostEnabled: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    // Always read the freshest seekDurationSeconds inside gesture lambdas
    val updatedSeekDuration by rememberUpdatedState(seekDurationSeconds)

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
    var isFastForwardLocked by remember { mutableStateOf(false) }

    //  Accumulating double-tap seek 
    var accumulatedLeftMs  by remember { mutableLongStateOf(0L) }
    var accumulatedRightMs by remember { mutableLongStateOf(0L) }
    var showLeftSeek       by remember { mutableStateOf(false) }
    var showRightSeek      by remember { mutableStateOf(false) }
    var leftRippleTick  by remember { mutableStateOf(0) }
    var rightRippleTick by remember { mutableStateOf(0) }

    LaunchedEffect(leftRippleTick) {
        if (leftRippleTick > 0) {
            delay(1200) // Wait for idle time after last tap
            showLeftSeek = false // Trigger the fade-out animation first
            delay(400) // Wait for the fadeOut(tween(350)) to complete
            accumulatedLeftMs = 0L // Reset the counter invisibly
        }
    }

    LaunchedEffect(rightRippleTick) {
        if (rightRippleTick > 0) {
            delay(1200) // Wait for idle time after last tap
            showRightSeek = false // Trigger the fade-out animation first
            delay(400) // Wait for the fadeOut(tween(350)) to complete
            accumulatedRightMs = 0L // Reset the counter invisibly
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                val slopPx = 10.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    if (isLocked) {
                        var isTap = true
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (abs(dx) > slopPx || abs(dy) > slopPx) {
                                isTap = false
                            }
                            change.consume()
                            if (!change.pressed) break
                        }
                        if (isTap) onSingleTap()
                        return@awaitEachGesture
                    }

                    val startX = down.position.x
                    val isRightSide = startX > size.width / 2f

                    var totalDx  = 0f
                    var totalDy  = 0f
                    var isSwiping  = false
                    var swipeAxis  = ""

                    var isLongPressActive = false
                    // NEW: Track if a 3-finger tap occurred during this gesture
                    var threeFingerTriggered = false

                    val longPressJob = coroutineScope.launch {
                        delay(600)
                        // Prevent normal long-press if 3-finger lock is already active
                        if (isPlaying && !isSwiping && !isFastForwardLocked) {
                            isLongPressActive = true
                            isFastForwarding = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFastForwardToggle(true)
                        }
                    }

                    while (true) {
                        val event  = awaitPointerEvent()

                        // NEW: 3-Finger Toggle Logic
                        if (event.changes.size == 3 && !threeFingerTriggered) {
                            threeFingerTriggered = true
                            isFastForwardLocked = !isFastForwardLocked
                            isFastForwarding = isFastForwardLocked
                            onFastForwardToggle(isFastForwardLocked)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        val change = event.changes.firstOrNull() ?: break

                        if (change.isConsumed) {
                            longPressJob.cancel()
                            break
                        }

                        if (!change.pressed) {
                            longPressJob.cancel()

                            if (isLongPressActive) {
                                isLongPressActive = false
                                // Only stop fast-forward if it wasn't locked by 3 fingers
                                if (!isFastForwardLocked) {
                                    isFastForwarding = false
                                    onFastForwardToggle(false)
                                }
                                break
                            }

                            // If this was a 3-finger gesture, exit cleanly without triggering taps
                            if (threeFingerTriggered) {
                                break
                            }

                            if (isSwiping && swipeAxis == "HORIZONTAL") {
                                scrubDeltaMs?.let { delta ->
                                    if (delta != 0L) onSeekCommit(delta)
                                }
                                scrubDeltaMs = null
                            }

                            if (!isSwiping) {
                                // ... existing single/double tap detection logic ...
                                val now  = System.currentTimeMillis()
                                val dt   = now - lastTapTime
                                val dx   = change.position.x - lastTapX
                                val dy   = change.position.y - lastTapY
                                val dist = sqrt(dx * dx + dy * dy)
                                val tapX = change.position.x

                                val isLeftTap = tapX < size.width * 0.33f
                                val isRightTap = tapX > size.width * 0.66f

                                val continuingLeftSeek = showLeftSeek && isLeftTap && (now - lastTapTime < 800)
                                val continuingRightSeek = showRightSeek && isRightTap && (now - lastTapTime < 800)

                                if (continuingLeftSeek || continuingRightSeek || (dt < 400 && dist < 100f)) {
                                    singleTapJob?.cancel()
                                    lastTapTime = now
                                    lastTapX    = change.position.x
                                    lastTapY    = change.position.y

                                    when {
                                        isLeftTap -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val stepMs = updatedSeekDuration * 1000L
                                            accumulatedLeftMs += stepMs
                                            showLeftSeek = true
                                            leftRippleTick++
                                            onSeekCommit(-stepMs)
                                        }
                                        isRightTap -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val stepMs = updatedSeekDuration * 1000L
                                            accumulatedRightMs += stepMs
                                            showRightSeek = true
                                            rightRippleTick++
                                            onSeekCommit(stepMs)
                                        }
                                        else -> {
                                            // Ensure center tap is actually a double tap (dt < 400 && dist < 100f)
                                            if (dt < 400 && dist < 100f) {
                                                centerWasPlaying = isPlaying
                                                showCenterRipple = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onDoubleTapCenter()
                                            }
                                        }
                                    }
                                } else {
                                    lastTapTime = now
                                    lastTapX    = change.position.x
                                    lastTapY    = change.position.y
                                    singleTapJob = coroutineScope.launch { delay(300); onSingleTap() }
                                }
                            }
                            break
                        }

                        // NEW: If 3 fingers are down, consume the event so we don't trigger swipes
                        if (threeFingerTriggered) {
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        // If long-press fast-forward is active, consume movement and skip all
                        // swipe logic. The fast-forward only stops when the finger is lifted.
                        if (isLongPressActive) {
                            change.consume()
                            isSwiping = true   // prevents swipe callbacks below
                            continue
                        }

                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        totalDx += dx
                        totalDy += dy

                        if (!isSwiping && (abs(totalDx) > slopPx || abs(totalDy) > slopPx)) {
                            isSwiping = true
                            longPressJob.cancel()
                            swipeAxis = if (abs(totalDx) > abs(totalDy)) "HORIZONTAL" else "VERTICAL"
                        }

                        if (isSwiping) {
                            change.consume()
                            if (isFastForwardLocked) continue

                            when (swipeAxis) {
                                "VERTICAL" -> {
                                    val sensitivity = 1.2f
                                    val delta = (-dy / size.height.toFloat()) * sensitivity
                                    if (isRightSide) onVolumeSwipe(delta) else onBrightnessSwipe(delta)
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
            ModernEdgeSlider(level = volumeLevel, isVolume = true, isAudioBoostEnabled = isAudioBoostEnabled)
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
                text      = displayValue,
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

    // Animated arc sweep: 0f → 1f on EACH new tap (rippleTick change)
    val arcProgress = remember { Animatable(0f) }
    LaunchedEffect(rippleTick) {
        if (rippleTick > 0) {
            // Snap back to 0 then animate forward for stacked-tap feel
            arcProgress.snapTo(0f)
            arcProgress.animateTo(
                targetValue   = 1f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            )
        }
    }

    // Background flash on each tap
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
            // Arc + chevron indicator
            Canvas(modifier = Modifier.size(58.dp)) {
                val stroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                val sweep  = 250f * arcProgress.value

                // Arc spans from ~130° to ~310° (open toward the tap side)
                // Right side: opens LEFT  → start at 140°, sweep clockwise
                // Left side:  opens RIGHT → start at -70° (290°), sweep counter-clockwise
                //   = start at 320°, sweep -250° → equivalent to start=320, sweep= -sweep (going CCW)
                //   Simpler: mirror the right arc. startAngle for left = 320° - 250° = 70° reversed.
                //   We draw right arc at startAngle=140, sweep=+250 (CW).
                //   For left arc we want mirror: startAngle= -70° (= 290°), sweep=-250 (CCW).
                //   In Canvas: drawArc with negative sweep draws CCW.

                val bgStartAngle = if (isRightSide) 140f else -70f
                val bgSweep      = if (isRightSide) 250f else -250f

                // Background arc (full)
                drawArc(
                    color      = Color.White.copy(alpha = 0.18f),
                    startAngle = bgStartAngle,
                    sweepAngle = bgSweep,
                    useCenter  = false,
                    style      = stroke
                )
                // Animated fill arc
                drawArc(
                    color      = Color.White.copy(alpha = 0.90f),
                    startAngle = bgStartAngle,
                    sweepAngle = if (isRightSide) sweep else -sweep,
                    useCenter  = false,
                    style      = stroke
                )

                // Double chevron arrows (>>) pointing in seek direction
                val cx = size.width / 2f
                val cy = size.height / 2f
                val aW = 7.dp.toPx()
                val aH = 10.dp.toPx()
                val gap = 5.dp.toPx()
                val arrowColor = Color.White
                val sw = 2.6.dp.toPx()

                if (isRightSide) {
                    // First chevron >
                    drawLine(arrowColor, Offset(cx - aW - gap, cy - aH / 2), Offset(cx - gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx - aW - gap, cy + aH / 2), Offset(cx - gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    // Second chevron >
                    drawLine(arrowColor, Offset(cx - gap, cy - aH / 2), Offset(cx + aW - gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx - gap, cy + aH / 2), Offset(cx + aW - gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                } else {
                    // First chevron <
                    drawLine(arrowColor, Offset(cx + aW + gap, cy - aH / 2), Offset(cx + gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx + aW + gap, cy + aH / 2), Offset(cx + gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    // Second chevron <
                    drawLine(arrowColor, Offset(cx + gap, cy - aH / 2), Offset(cx - aW + gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(arrowColor, Offset(cx + gap, cy + aH / 2), Offset(cx - aW + gap, cy), strokeWidth = sw, cap = StrokeCap.Round)
                }
            }

            Text(
                text       = label,
                color      = Color.White,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )

            Text(
                text      = if (isRightSide) "Forward" else "Rewind",
                color     = Color.White.copy(alpha = 0.70f),
                fontSize  = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

//  Center play/pause ripple

@Composable
private fun CenterRipple(wasPlaying: Boolean) {
    // wasPlaying = state BEFORE the tap, so icon shows what action was just taken:
    // was playing → user just paused → show Pause icon
    // was paused  → user just played → show PlayArrow icon
    val pulseAnim = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) {
        pulseAnim.animateTo(1.05f, animationSpec = tween(200, easing = FastOutSlowInEasing))
        pulseAnim.animateTo(1.00f, animationSpec = tween(150, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier
            .size((64 * pulseAnim.value).dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector  = if (wasPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (wasPlaying) "Paused" else "Playing",
            tint         = Color.White,
            modifier     = Modifier.size(40.dp)
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