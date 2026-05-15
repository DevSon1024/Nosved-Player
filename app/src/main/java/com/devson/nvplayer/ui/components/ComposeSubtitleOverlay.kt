package com.devson.nvplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Gesture tuning constants
private const val HORIZONTAL_THRESHOLD_PX = 80f
private const val AXIS_LOCK_THRESHOLD_PX = 8f

// Arrow feedback state
private enum class SwipeHint { None, Left, Right }

@Composable
fun ComposeSubtitleOverlay(
    player: Player?,
    textSizeScale: Float,
    bgStyle: Int,
    useSystemCaptionStyle: Boolean = true,
    subtitleFont: com.devson.nvplayer.repository.SubtitleFont = com.devson.nvplayer.repository.SubtitleFont.DEFAULT,
    isSubtitleBold: Boolean = false,
    subtitleTimingsMs: List<Long> = emptyList(),
    isSubtitleGestureEnabled: Boolean = true,
    subtitleDelayMs: Long = 0L,
    verticalOffsetFraction: Float = 0f,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    //  Cue collection 
    val currentCues = remember { mutableStateListOf<androidx.media3.common.text.Cue>() }

    DisposableEffect(player, subtitleDelayMs) {
        var delayJob: kotlinx.coroutines.Job? = null
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                delayJob?.cancel()
                if (subtitleDelayMs > 0L) {
                    delayJob = coroutineScope.launch {
                        kotlinx.coroutines.delay(subtitleDelayMs)
                        currentCues.clear()
                        currentCues.addAll(cueGroup.cues)
                    }
                } else {
                    currentCues.clear()
                    currentCues.addAll(cueGroup.cues)
                }
            }
        }
        player?.addListener(listener)
        onDispose { 
            player?.removeListener(listener)
            delayJob?.cancel()
        }
    }

    //  Vertical position state 
    var rawOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = rawOffsetY,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 320f),
        label = "subtitleOffsetY"
    )

    //  Gesture state 
    var lockedVertical by remember { mutableStateOf<Boolean?>(null) }
    var xAccumulator by remember { mutableFloatStateOf(0f) }
    var seekFired by remember { mutableStateOf(false) }

    //  Arrow / highlight feedback 
    var swipeHint by remember { mutableStateOf(SwipeHint.None) }
    // Progress 0..1 while dragging horizontally (before threshold)
    var dragProgress by remember { mutableFloatStateOf(0f) }
    // Fired = show the "committed" pulse then fade
    var seekJustFired by remember { mutableStateOf(false) }

    // Animated values driven by dragProgress / seekJustFired
    val arrowAlpha by animateFloatAsState(
        targetValue = when {
            swipeHint == SwipeHint.None -> 0f
            seekJustFired -> 1f
            else -> 0.4f + dragProgress * 0.6f   // dims → full as thumb moves
        },
        animationSpec = tween(120),
        label = "arrowAlpha"
    )
    val arrowScale by animateFloatAsState(
        targetValue = when {
            swipeHint == SwipeHint.None -> 0.6f
            seekJustFired -> 1.25f
            else -> 0.75f + dragProgress * 0.4f
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "arrowScale"
    )
    // Plate highlight border opacity (only during horizontal drag)
    val highlightAlpha by animateFloatAsState(
        targetValue = if (swipeHint != SwipeHint.None && lockedVertical == false) {
            0.25f + dragProgress * 0.55f
        } else 0f,
        animationSpec = tween(150),
        label = "plateHighlight"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val maxHeightPx = constraints.maxHeight.toFloat()
        val externalOffsetPx = -verticalOffsetFraction * maxHeightPx

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, (animatedOffsetY + externalOffsetPx).roundToInt()) }
                .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
                .wrapContentSize()
                .pointerInput(isSubtitleGestureEnabled) {
                    if (!isSubtitleGestureEnabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = {
                            lockedVertical = null
                            xAccumulator = 0f
                            seekFired = false
                            seekJustFired = false
                            swipeHint = SwipeHint.None
                            dragProgress = 0f
                        },
                        onDragEnd = {
                            // Fade out arrow after a short hold if seek fired
                            coroutineScope.launch {
                                if (seekJustFired) {
                                    kotlinx.coroutines.delay(350)
                                }
                                swipeHint = SwipeHint.None
                                dragProgress = 0f
                                seekJustFired = false
                            }
                            lockedVertical = null
                            xAccumulator = 0f
                            seekFired = false
                        },
                        onDragCancel = {
                            swipeHint = SwipeHint.None
                            dragProgress = 0f
                            seekJustFired = false
                            lockedVertical = null
                            xAccumulator = 0f
                            seekFired = false
                        }
                    ) { change, dragAmount ->
                        change.consume()

                        val dx = dragAmount.x
                        val dy = dragAmount.y

                        //  1. Axis detection 
                        if (lockedVertical == null) {
                            if (abs(dx) + abs(dy) > AXIS_LOCK_THRESHOLD_PX) {
                                lockedVertical = abs(dy) >= abs(dx)
                                // Only show hint once we've committed horizontally
                                if (lockedVertical == false) {
                                    swipeHint = if (dx < 0) SwipeHint.Left else SwipeHint.Right
                                }
                            }
                            return@detectDragGestures
                        }

                        //  2a. Vertical → reposition 
                        if (lockedVertical == true) {
                            rawOffsetY = (rawOffsetY + dy).coerceIn(
                                -maxHeightPx * 0.85f,
                                maxHeightPx * 0.30f
                            )
                            return@detectDragGestures
                        }

                        //  2b. Horizontal → navigate dialog 
                        if (seekFired) return@detectDragGestures

                        xAccumulator += dx

                        // Keep hint direction tracking live even before threshold
                        swipeHint = if (xAccumulator < 0) SwipeHint.Left else SwipeHint.Right
                        dragProgress = (abs(xAccumulator) / HORIZONTAL_THRESHOLD_PX).coerceIn(0f, 1f)

                        if (abs(xAccumulator) < HORIZONTAL_THRESHOLD_PX) return@detectDragGestures

                        // Threshold crossed → fire seek
                        player?.let { p ->
                            val newPos = if (xAccumulator > 0) {
                                findPrevSubtitle(p.currentPosition, subtitleTimingsMs)
                            } else {
                                findNextSubtitle(p.currentPosition, subtitleTimingsMs)
                            }
                            p.seekTo(newPos)
                        }
                        seekFired = true
                        seekJustFired = true
                        dragProgress = 1f
                    }
                }
        ) {
            //  Plate container with highlight border + arrows 
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Scoped highlight ring — wraps snugly around the plate
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = highlightAlpha),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            Color.White.copy(alpha = highlightAlpha * 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                )

                //  Subtitle text column 
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // LEFT arrow (shown on swipe-left = next subtitle)
                    AnimatedArrow(
                        visible = swipeHint == SwipeHint.Left,
                        pointLeft = true,
                        alpha = arrowAlpha,
                        scale = arrowScale,
                        pulsing = seekJustFired
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        currentCues.forEach { cue ->
                            if (cue.bitmap != null) {
                                Image(
                                    bitmap = cue.bitmap!!.asImageBitmap(),
                                    contentDescription = null
                                )
                            }
                            if (!cue.text.isNullOrEmpty()) {
                                val bgColor = when (bgStyle) {
                                    1 -> Color(0x80000000)
                                    2 -> Color.Black
                                    else -> Color.Transparent
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

                    // RIGHT arrow (shown on swipe-right = prev subtitle)
                    AnimatedArrow(
                        visible = swipeHint == SwipeHint.Right,
                        pointLeft = false,
                        alpha = arrowAlpha,
                        scale = arrowScale,
                        pulsing = seekJustFired
                    )
                }
            }
        }
    }
}

//  Arrow chip composable 

@Composable
private fun AnimatedArrow(
    visible: Boolean,
    pointLeft: Boolean,
    alpha: Float,
    scale: Float,
    pulsing: Boolean
) {
    // Infinite bounce when pulsing (seek fired confirmation)
    val infiniteTransition = rememberInfiniteTransition(label = "arrowPulse")
    val pulseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (pulsing) (if (pointLeft) -6f else 6f) else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(260, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseOffset"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .alpha(alpha)
            .scale(scale)
            .offset(x = pulseOffset.dp)
            .size(28.dp)
            .background(
                color = Color.White.copy(alpha = 0.18f),
                shape = CircleShape
            )
    ) {
        Text(
            text = if (pointLeft) "‹" else "›",
            fontSize = 18.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

//  Subtitle navigation helpers 

private fun findNextSubtitle(currentPositionMs: Long, timings: List<Long>): Long {
    if (timings.isEmpty()) return currentPositionMs + 5_000L
    return timings.firstOrNull { it > currentPositionMs + 50L } ?: currentPositionMs
}

private fun findPrevSubtitle(currentPositionMs: Long, timings: List<Long>): Long {
    if (timings.isEmpty()) return (currentPositionMs - 5_000L).coerceAtLeast(0L)
    return timings.lastOrNull { it < currentPositionMs - 50L } ?: 0L
}