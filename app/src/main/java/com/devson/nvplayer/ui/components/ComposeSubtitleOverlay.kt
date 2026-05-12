package com.devson.nvplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import kotlin.math.abs
import kotlin.math.roundToInt

// Gesture tuning constants

/** Pixels of accumulated X movement before a dialog-jump fires. */
private const val HORIZONTAL_THRESHOLD_PX = 80f

/**
 * Pixels of total movement before we decide which axis the gesture is on.
 * Keeping this small (8 px) makes the lock feel instant but still intentional.
 */
private const val AXIS_LOCK_THRESHOLD_PX = 8f

// 

/**
 * Subtitle overlay with two independent gestures, both scoped ONLY to the
 * visible subtitle plate.  Touches anywhere outside the plate are NOT consumed,
 * so GestureOverlay underneath (seek scrub, volume, brightness) works normally.
 *
 * Gestures on the subtitle plate:
 *  • Vertical drag   → repositions the plate up / down (clamped to screen).
 *  • Horizontal swipe → jumps to the previous / next subtitle dialog.
 *                       Fires once per lift; user must release to fire again.
 *
 * @param subtitleTimingsMs Sorted list of subtitle cue start times in ms.
 *   Pass the times you already parse for your subtitle track.  When empty,
 *   horizontal swipe falls back to a ±5 s seek.
 */
@Composable
fun ComposeSubtitleOverlay(
    player: Player?,
    textSizeScale: Float,
    bgStyle: Int,
    useSystemCaptionStyle: Boolean = true,
    subtitleFont: com.devson.nvplayer.repository.SubtitleFont = com.devson.nvplayer.repository.SubtitleFont.DEFAULT,
    isSubtitleBold: Boolean = false,
    subtitleTimingsMs: List<Long> = emptyList(),
    modifier: Modifier = Modifier
) {
    //  Cue collection 
    val currentCues = remember { mutableStateListOf<androidx.media3.common.text.Cue>() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                currentCues.clear()
                currentCues.addAll(cueGroup.cues)
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    //  Vertical position state 
    // Negative = plate moves upward (away from bottom edge).
    var rawOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = rawOffsetY,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 320f),
        label = "subtitleOffsetY"
    )

    //  Gesture state (reset on every new touch down) 
    // Declared outside pointerInput so the same objects survive recomposition.
    var lockedVertical by remember { mutableStateOf<Boolean?>(null) }
    var xAccumulator  by remember { mutableFloatStateOf(0f) }
    // One-shot flag: once a dialog-jump fires the user must lift and re-touch.
    var seekFired     by remember { mutableStateOf(false) }

    //  Layout 
    // BoxWithConstraints gives us maxHeightPx for the vertical clamp.
    // Its own modifier is fillMaxSize with NO pointerInput → transparent to GestureOverlay.
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val maxHeightPx = constraints.maxHeight.toFloat()

        // Outer Box: bottom-centers the plate but does NOT intercept touches.
        // wrapContentSize() means its hit-area is exactly the plate, nothing more.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
                .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
                // ↑ Padding is layout-only; does NOT extend the touch target.
                .wrapContentSize()
                // pointerInput lives here, on the tight-fitting box.
                // Only touches that land on actual subtitle text reach this handler.
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            lockedVertical = null
                            xAccumulator  = 0f
                            seekFired     = false
                        },
                        onDragEnd = {
                            lockedVertical = null
                            xAccumulator  = 0f
                            seekFired     = false
                        },
                        onDragCancel = {
                            lockedVertical = null
                            xAccumulator  = 0f
                            seekFired     = false
                        }
                    ) { change, dragAmount ->
                        // Consume the event so GestureOverlay doesn't also react
                        // while the finger is on the subtitle plate.
                        change.consume()

                        val dx = dragAmount.x
                        val dy = dragAmount.y

                        //  1. Axis detection 
                        if (lockedVertical == null) {
                            if (abs(dx) + abs(dy) > AXIS_LOCK_THRESHOLD_PX) {
                                lockedVertical = abs(dy) >= abs(dx)
                            }
                            // Not enough movement yet; wait for the next event.
                            return@detectDragGestures
                        }

                        //  2a. Vertical → reposition the plate 
                        if (lockedVertical == true) {
                            rawOffsetY = (rawOffsetY + dy).coerceIn(
                                -maxHeightPx * 0.85f,   // can go almost to the top
                                maxHeightPx * 0.30f     // slight downward tolerance
                            )
                            return@detectDragGestures
                        }

                        //  2b. Horizontal → jump to prev / next dialog 
                        if (seekFired) return@detectDragGestures   // one jump per stroke

                        xAccumulator += dx
                        if (abs(xAccumulator) < HORIZONTAL_THRESHOLD_PX) return@detectDragGestures

                        player?.let { p ->
                            // Swipe RIGHT (xAccumulator > 0) → go to PREVIOUS dialog
                            // Swipe LEFT  (xAccumulator < 0) → go to NEXT dialog
                            val newPos = if (xAccumulator > 0) {
                                findPrevSubtitle(p.currentPosition, subtitleTimingsMs)
                            } else {
                                findNextSubtitle(p.currentPosition, subtitleTimingsMs)
                            }
                            p.seekTo(newPos)
                        }
                        seekFired = true   // lock until finger lifts
                    }
                }
        ) {
            //  Subtitle content 
            // Column is wrapContentSize so the hit-area == the visible text only.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                currentCues.forEach { cue ->

                    // Bitmap cues (PGS / VOBSUB image subtitles)
                    if (cue.bitmap != null) {
                        Image(
                            bitmap = cue.bitmap!!.asImageBitmap(),
                            contentDescription = null
                        )
                    }

                    // Text cues (SRT / ASS / WebVTT)
                    if (!cue.text.isNullOrEmpty()) {
                        val bgColor = when (bgStyle) {
                            1    -> Color(0x80000000)  // semi-transparent
                            2    -> Color.Black         // solid
                            else -> Color.Transparent   // none
                        }
                        val targetFontFamily = if (!useSystemCaptionStyle) {
                            when (subtitleFont) {
                                com.devson.nvplayer.repository.SubtitleFont.DEFAULT -> androidx.compose.ui.text.font.FontFamily.Default
                                com.devson.nvplayer.repository.SubtitleFont.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
                                com.devson.nvplayer.repository.SubtitleFont.SANS_SERIF -> androidx.compose.ui.text.font.FontFamily.SansSerif
                                com.devson.nvplayer.repository.SubtitleFont.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
                            }
                        } else {
                            androidx.compose.ui.text.font.FontFamily.Default
                        }

                        val targetFontWeight = if (!useSystemCaptionStyle && isSubtitleBold) {
                            androidx.compose.ui.text.font.FontWeight.Bold
                        } else {
                            androidx.compose.ui.text.font.FontWeight.Normal
                        }

                        Text(
                            text = cue.text.toString(),
                            fontSize = (16 * textSizeScale).sp,
                            color = Color.White,
                            fontFamily = targetFontFamily,
                            fontWeight = targetFontWeight,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .background(color = bgColor, shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

//  Subtitle navigation helpers 

/**
 * Returns the start time of the next subtitle cue after [currentPositionMs].
 * The 150 ms buffer prevents re-landing on the cue that is currently playing.
 * Falls back to a +5 s seek when no timing list is provided.
 */
private fun findNextSubtitle(currentPositionMs: Long, timings: List<Long>): Long {
    if (timings.isEmpty()) return currentPositionMs + 5_000L
    return timings.firstOrNull { it > currentPositionMs + 150L } ?: currentPositionMs
}

/**
 * Returns the start time of the previous subtitle cue before [currentPositionMs].
 * The 150 ms buffer lets the user re-trigger the current cue from the start
 * only when they are clearly past its beginning; otherwise it jumps one further back.
 * Falls back to a −5 s seek when no timing list is provided.
 */
private fun findPrevSubtitle(currentPositionMs: Long, timings: List<Long>): Long {
    if (timings.isEmpty()) return (currentPositionMs - 5_000L).coerceAtLeast(0L)
    return timings.lastOrNull { it < currentPositionMs - 150L } ?: 0L
}