package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    seekDurationSeconds: Int,
    isPlaying: Boolean = false,
    isLocked: Boolean = false,
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

    var showLeftRipple   by remember { mutableStateOf(false) }
    var showCenterRipple by remember { mutableStateOf(false) }
    var showRightRipple  by remember { mutableStateOf(false) }
    // Snapshot of isPlaying at the moment of the center tap (before toggle)
    var centerWasPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(showLeftRipple) {
        if (showLeftRipple)   { delay(600); showLeftRipple = false }
    }
    LaunchedEffect(showCenterRipple) {
        if (showCenterRipple) { delay(600); showCenterRipple = false }
    }
    LaunchedEffect(showRightRipple) {
        if (showRightRipple)  { delay(600); showRightRipple = false }
    }

    //  Double-tap tracking 
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapX    by remember { mutableStateOf(0f) }
    var lastTapY    by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    var singleTapJob by remember { mutableStateOf<Job?>(null) }

    // Invisible Seek Bar tracking
    var scrubDeltaMs by remember { mutableStateOf<Long?>(null) }

    // Fast Forward (2x speed) State
    var isFastForwarding by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                val slopPx = 10.dp.toPx()
                awaitEachGesture {
                    val down   = awaitFirstDown(requireUnconsumed = false)

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
                    val longPressJob = coroutineScope.launch {
                        delay(600)
                        if (isPlaying && !isSwiping) {
                            isLongPressActive = true
                            isFastForwarding = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFastForwardToggle(true)
                        }
                    }

                    while (true) {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (!change.pressed) {
                            longPressJob.cancel()
                            if (isLongPressActive) {
                                isFastForwarding = false
                                onFastForwardToggle(false)
                                break
                            }

                            if (isSwiping && swipeAxis == "HORIZONTAL") {
                                // Commit the seek
                                scrubDeltaMs?.let { delta ->
                                    if (delta != 0L) {
                                        onSeekCommit(delta)
                                    }
                                }
                                scrubDeltaMs = null
                            }

                            //  Tap detection 
                            if (!isSwiping) {
                                val now  = System.currentTimeMillis()
                                val dt   = now - lastTapTime
                                val dx   = change.position.x - lastTapX
                                val dy   = change.position.y - lastTapY
                                val dist = sqrt(dx * dx + dy * dy)

                                if (dt < 300 && dist < 80f) {
                                    //  Double tap: cancel pending single tap
                                    singleTapJob?.cancel()
                                    lastTapTime = 0L
                                    val tapX = change.position.x
                                    when {
                                        tapX < size.width * 0.33f -> {
                                            // Left zone → backward
                                            showLeftRipple = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDoubleTapLeft()
                                        }
                                        tapX > size.width * 0.66f -> {
                                            // Right zone → forward
                                            showRightRipple = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDoubleTapRight()
                                        }
                                        else -> {
                                            // Center zone → play/pause
                                            centerWasPlaying = isPlaying
                                            showCenterRipple = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDoubleTapCenter()
                                        }
                                    }
                                } else {
                                    //  Single tap: delay for potential second tap
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

                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        totalDx += dx
                        totalDy += dy

                        if (!isSwiping && (abs(totalDx) > slopPx || abs(totalDy) > slopPx)) {
                            isSwiping = true
                            longPressJob.cancel()
                            if (isLongPressActive) {
                                isFastForwarding = false
                                onFastForwardToggle(false)
                                isLongPressActive = false
                            }
                            swipeAxis = if (abs(totalDx) > abs(totalDy)) "HORIZONTAL" else "VERTICAL"
                        }

                        if (isSwiping) {
                            change.consume()
                            if (isLongPressActive) break // Prevent swiping while holding fast-forward
                            
                            when (swipeAxis) {
                                "VERTICAL" -> {
                                    val sensitivity = 1.2f
                                    val delta = (-dy / size.height.toFloat()) * sensitivity
                                    if (isRightSide) {
                                        onVolumeSwipe(delta)
                                    } else {
                                        onBrightnessSwipe(delta)
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
        //  Brightness slider - left edge 
        AnimatedVisibility(
            visible = showBrightnessFeedback,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) { EdgeSlider(level = brightnessLevel) }

        //  Volume slider - right edge 
        AnimatedVisibility(
            visible = showVolumeFeedback,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) { EdgeSlider(level = volumeLevel) }

        // Precise scrub overlay (Horizontal seek)
        androidx.compose.animation.AnimatedVisibility(
            visible = scrubDeltaMs != null,
            enter = androidx.compose.animation.fadeIn(), 
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            scrubDeltaMs?.let { deltaMs ->
                Box(
                    modifier = Modifier
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    val sign = if (deltaMs >= 0) "+" else ""
                    androidx.compose.material3.Text(
                        text = "$sign${deltaMs / 1000}s",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }

        // Fast Forward Badge (Top Center)
        androidx.compose.animation.AnimatedVisibility(
            visible = isFastForwarding,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                androidx.compose.material3.Text(
                    text = "⏩ 2x Speed",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }

        //  Left double-tap ripple 
        AnimatedVisibility(
            visible = showLeftRipple,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            SeekRipple(isRightSide = false, label = "-${seekDurationSeconds}s")
        }

        //  Center double-tap ripple (play / pause) 
        AnimatedVisibility(
            visible = showCenterRipple,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CenterRipple(wasPlaying = centerWasPlaying)
        }

        //  Right double-tap ripple 
        AnimatedVisibility(
            visible = showRightRipple,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SeekRipple(isRightSide = true, label = "+${seekDurationSeconds}s")
        }
    }
}

// Edge volume/brightness slider

@Composable
private fun EdgeSlider(level: Float) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        val percentage = (level * 100).toInt()
        Text(
            text = "$percentage%",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .height(140.dp)
                .width(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxHeight(level)
                    .width(6.dp)
                    .background(Color.White)
            )
        }
    }
}
 
// Left / right seek ripple (pill-shaped, curved toward center)
 

@Composable
private fun SeekRipple(isRightSide: Boolean, label: String) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(110.dp)
            .background(
                color = Color.White.copy(alpha = 0.15f),
                shape = if (isRightSide)
                    RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100)
                else
                    RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

// Center play/pause ripple (circular)

@Composable
private fun CenterRipple(wasPlaying: Boolean) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (wasPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (wasPlaying) "Paused" else "Playing",
            tint = Color.White,
            modifier = Modifier.size(44.dp)
        )
    }
}
