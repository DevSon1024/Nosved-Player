package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    onVolumeSwipe: (Float) -> Unit,
    onBrightnessSwipe: (Float) -> Unit
) {
    // Independent feedback per zone
    var showBrightnessFeedback by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolumeFeedback by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }

    LaunchedEffect(brightnessLevel) {
        if (showBrightnessFeedback) { delay(1500); showBrightnessFeedback = false }
    }
    LaunchedEffect(volumeLevel) {
        if (showVolumeFeedback) { delay(1500); showVolumeFeedback = false }
    }

    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapX by remember { mutableStateOf(0f) }
    var lastTapY by remember { mutableStateOf(0f) }

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
                    var totalDy = 0f
                    var isSwiping = false

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
                                    if (isRightSide) onDoubleTapRight() else onDoubleTapLeft()
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

                        val dy = change.position.y - change.previousPosition.y
                        totalDy += dy

                        if (!isSwiping && abs(totalDy) > slopPx) {
                            isSwiping = true
                        }

                        if (isSwiping) {
                            change.consume()
                            val delta = -dy / size.height.toFloat()
                            if (isRightSide) {
                                onVolumeSwipe(delta)
                                volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                                showVolumeFeedback = true
                            } else {
                                onBrightnessSwipe(delta)
                                brightnessLevel = (brightnessLevel + delta).coerceIn(0f, 1f)
                                showBrightnessFeedback = true
                            }
                        }
                    }
                }
            }
    ) {
        // Brightness pill — left centre
        AnimatedVisibility(
            visible = showBrightnessFeedback,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp)
        ) {
            FeedbackPill(
                icon = { Icon(Icons.Filled.BrightnessHigh, null, tint = Color.White, modifier = Modifier.size(22.dp)) },
                level = brightnessLevel,
                label = "${(brightnessLevel * 100).toInt()}%"
            )
        }

        // Volume pill — right centre
        AnimatedVisibility(
            visible = showVolumeFeedback,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)
        ) {
            FeedbackPill(
                icon = { Icon(Icons.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(22.dp)) },
                level = volumeLevel,
                label = "${(volumeLevel * 100).toInt()}%"
            )
        }
    }
}

@Composable
private fun FeedbackPill(
    icon: @Composable () -> Unit,
    level: Float,
    label: String
) {
    Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            LinearProgressIndicator(
                progress = { level },
                modifier = Modifier.width(56.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
            Text(text = label, color = Color.White, fontSize = 11.sp, style = MaterialTheme.typography.labelSmall)
        }
    }
}
