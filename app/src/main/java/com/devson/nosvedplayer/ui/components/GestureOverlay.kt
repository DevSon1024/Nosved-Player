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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Unified gesture surface.
 *
 * Uses a single [pointerInput] block with [awaitEachGesture] + manual loop
 * to distinguish taps vs vertical drags without competing detector layers.
 *
 * - Single tap          → onSingleTap()
 * - Double tap (≤300ms) → onDoubleTapLeft / Right
 * - Vertical swipe left → onBrightnessSwipe(+delta = brighter)
 * - Vertical swipe right→ onVolumeSwipe(+delta = louder)
 */
@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onSeekSwipe: (Float) -> Unit = {},
    onVolumeSwipe: (Float) -> Unit,
    onBrightnessSwipe: (Float) -> Unit
) {
    // Independent feedback per zone
    var showBrightnessFeedback by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolumeFeedback by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }

    var showLeftRipple by remember { mutableStateOf(false) }
    var showRightRipple by remember { mutableStateOf(false) }

    LaunchedEffect(brightnessLevel, showBrightnessFeedback) {
        if (showBrightnessFeedback) { delay(500); showBrightnessFeedback = false }
    }
    LaunchedEffect(volumeLevel, showVolumeFeedback) {
        if (showVolumeFeedback) { delay(500); showVolumeFeedback = false }
    }
    LaunchedEffect(showLeftRipple) {
        if (showLeftRipple) { delay(500); showLeftRipple = false }
    }
    LaunchedEffect(showRightRipple) {
        if (showRightRipple) { delay(500); showRightRipple = false }
    }

    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapX by remember { mutableStateOf(0f) }
    var lastTapY by remember { mutableStateOf(0f) }

    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val slopPx = 10.dp.toPx()

                // awaitEachGesture loops automatically and handles cancellation correctly
                awaitEachGesture {
                    // Wait for finger down
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    val isRightSide = startX > size.width / 2f
                    var totalDx = 0f
                    var totalDy = 0f
                    var isSwiping = false
                    var swipeAxis = "" // "VERTICAL" or "HORIZONTAL"

                    // Track until release
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (!change.pressed) {
                            // Finger released — was it a tap?
                            if (!isSwiping) {
                                val now = System.currentTimeMillis()
                                val dt = now - lastTapTime
                                val dx = change.position.x - lastTapX
                                val dy = change.position.y - lastTapY
                                val dist = sqrt(dx * dx + dy * dy)

                                if (dt < 300 && dist < 80f) {
                                    // Double tap
                                    lastTapTime = 0L
                                    val isTapRight = change.position.x > size.width * 0.66f
                                    val isTapLeft = change.position.x < size.width * 0.33f
                                    if (isTapRight) {
                                        showRightRipple = true
                                        onDoubleTapRight()
                                    } else if (isTapLeft) {
                                        showLeftRipple = true
                                        onDoubleTapLeft()
                                    }
                                } else {
                                    // Single tap
                                    lastTapTime = now
                                    lastTapX = change.position.x
                                    lastTapY = change.position.y
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
                            
                            if (swipeAxis == "VERTICAL") {
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
                            } else if (swipeAxis == "HORIZONTAL") {
                                onSeekSwipe(dx)
                            }
                        }
                    }
                }
            }
    ) {
        // Brightness slider — left edge
        AnimatedVisibility(
            visible = showBrightnessFeedback,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            EdgeSlider(level = brightnessLevel)
        }

        // Volume slider — right edge
        AnimatedVisibility(
            visible = showVolumeFeedback,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            EdgeSlider(level = volumeLevel)
        }

        // Left Double Tap Ripple
        AnimatedVisibility(
            visible = showLeftRipple,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            DoubleTapRipple(isRightSide = false, text = "-10s")
        }

        // Right Double Tap Ripple
        AnimatedVisibility(
            visible = showRightRipple,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            DoubleTapRipple(isRightSide = true, text = "+10s")
        }
    }
}

@Composable
private fun EdgeSlider(
    level: Float
) {
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

@Composable
private fun DoubleTapRipple(
    isRightSide: Boolean,
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(100.dp)
            .background(
                color = Color.White.copy(alpha = 0.15f),
                shape = if (isRightSide) RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100)
                else RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
    }
}
