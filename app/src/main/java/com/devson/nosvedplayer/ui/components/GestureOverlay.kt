package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableFloatStateOf
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

/**
 * Unified gesture surface — three tap zones + swipe gestures.
 *
 * Zones (by X position):
 *   Left   (0 – 33%)  → double-tap backward seek
 *   Center (33 – 66%) → double-tap play/pause toggle
 *   Right  (66 – 100%)→ double-tap forward seek
 *
 * Swipes:
 *   Vertical left  → brightness
 *   Vertical right → volume
 *   Horizontal     → seekSwipe (optional)
 */
@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    seekDurationSeconds: Int,
    isPlaying: Boolean = false,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onSeekSwipe: (Float) -> Unit = {},
    onVolumeSwipe: (Float) -> Unit,
    onBrightnessSwipe: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    //  Feedback visibility states 
    var showBrightnessFeedback by remember { mutableStateOf(false) }
    var brightnessLevel        by remember { mutableFloatStateOf(0.5f) }
    var showVolumeFeedback     by remember { mutableStateOf(false) }
    var volumeLevel            by remember { mutableFloatStateOf(0.5f) }

    var showLeftRipple   by remember { mutableStateOf(false) }
    var showCenterRipple by remember { mutableStateOf(false) }
    var showRightRipple  by remember { mutableStateOf(false) }
    // Snapshot of isPlaying at the moment of the center tap (before toggle)
    var centerWasPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(showBrightnessFeedback) {
        if (showBrightnessFeedback) { delay(600); showBrightnessFeedback = false }
    }
    LaunchedEffect(showVolumeFeedback) {
        if (showVolumeFeedback) { delay(600); showVolumeFeedback = false }
    }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val slopPx = 10.dp.toPx()
                awaitEachGesture {
                    val down   = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val isRightSide = startX > size.width / 2f

                    var totalDx  = 0f
                    var totalDy  = 0f
                    var isSwiping  = false
                    var swipeAxis  = ""

                    while (true) {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (!change.pressed) {
                            //  Tap detection 
                            if (!isSwiping) {
                                val now  = System.currentTimeMillis()
                                val dt   = now - lastTapTime
                                val dx   = change.position.x - lastTapX
                                val dy   = change.position.y - lastTapY
                                val dist = sqrt(dx * dx + dy * dy)

                                if (dt < 300 && dist < 80f) {
                                    //  Double tap 
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
                                    //  Single tap 
                                    lastTapTime = now
                                    lastTapX    = change.position.x
                                    lastTapY    = change.position.y
                                    onSingleTap()
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
                            swipeAxis = if (abs(totalDx) > abs(totalDy)) "HORIZONTAL" else "VERTICAL"
                        }

                        if (isSwiping) {
                            change.consume()
                            when (swipeAxis) {
                                "VERTICAL" -> {
                                    val sensitivity = 1.2f
                                    val delta = (-dy / size.height.toFloat()) * sensitivity
                                    if (isRightSide) {
                                        onVolumeSwipe(delta)
                                        val newVolume = (volumeLevel + delta).coerceIn(0f, 1f)
                                        if ((newVolume == 0f || newVolume == 1f) && newVolume != volumeLevel) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        volumeLevel = newVolume
                                        showVolumeFeedback = true
                                    } else {
                                        onBrightnessSwipe(delta)
                                        val newBrightness = (brightnessLevel + delta).coerceIn(0f, 1f)
                                        if ((newBrightness == 0f || newBrightness == 1f) && newBrightness != brightnessLevel) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        brightnessLevel = newBrightness
                                        showBrightnessFeedback = true
                                    }
                                }
                                "HORIZONTAL" -> onSeekSwipe(dx)
                            }
                        }
                    }
                }
            }
    ) {
        //  Brightness slider — left edge 
        AnimatedVisibility(
            visible = showBrightnessFeedback,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) { EdgeSlider(level = brightnessLevel) }

        //  Volume slider — right edge 
        AnimatedVisibility(
            visible = showVolumeFeedback,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) { EdgeSlider(level = volumeLevel) }

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

// 
// Edge volume/brightness slider
// 

@Composable
private fun EdgeSlider(level: Float) {
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
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

// 
// Left / right seek ripple (pill-shaped, curved toward center)
// 

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

// 
// Center play/pause ripple (circular)
// 

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
