package com.devson.nvplayer.ui.common.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun CustomSeekbar(
    position: Float = 0f,
    duration: Float,
    onSeek: (Float, Boolean) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    seekbarStyle: String = "standard",
    isPaused: Boolean = false,
    bufferedDuration: Float = 0f,
    positionProvider: (() -> Float)? = null,
    bufferedDurationProvider: (() -> Float)? = null,
    modifier: Modifier = Modifier
) {
    val safeDuration = duration.coerceAtLeast(0.1f)
    val primaryColor = MaterialTheme.colorScheme.primary

    var isScrubbing by remember { mutableStateOf(false) }
    var latestInteractionPos by remember { mutableFloatStateOf(0f) }

    val currentPositionProvider: () -> Float = {
        if (isScrubbing) {
            latestInteractionPos
        } else {
            val raw = positionProvider?.invoke() ?: position
            raw.coerceIn(0f, safeDuration)
        }
    }

    val currentBufferedDurationProvider: () -> Float = {
        bufferedDurationProvider?.invoke() ?: bufferedDuration
    }

    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(safeDuration) {
                detectTapGestures { offset ->
                    val targetPos = ((offset.x / size.width) * safeDuration).coerceIn(0f, safeDuration)
                    latestInteractionPos = targetPos
                    isScrubbing = true
                    onDraggingChanged(true)
                    onSeek(targetPos, true)
                    scope.launch {
                        isScrubbing = false
                        onDraggingChanged(false)
                    }
                }
            }
            .pointerInput(safeDuration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val targetPos = ((offset.x / size.width) * safeDuration).coerceIn(0f, safeDuration)
                        latestInteractionPos = targetPos
                        isScrubbing = true
                        onDraggingChanged(true)
                        onSeek(targetPos, false)
                    },
                    onDragEnd = {
                        val finalPos = latestInteractionPos.coerceIn(0f, safeDuration)
                        onSeek(finalPos, true)
                        scope.launch {
                            isScrubbing = false
                            onDraggingChanged(false)
                        }
                    },
                    onDragCancel = {
                        val finalPos = latestInteractionPos.coerceIn(0f, safeDuration)
                        onSeek(finalPos, true)
                        scope.launch {
                            isScrubbing = false
                            onDraggingChanged(false)
                        }
                    }
                ) { change, _ ->
                    change.consume()
                    val targetPos = ((change.position.x / size.width) * safeDuration).coerceIn(0f, safeDuration)
                    latestInteractionPos = targetPos
                    onSeek(targetPos, false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (seekbarStyle.lowercase()) {
            "wavy" -> {
                SquigglySeekbarCanvas(
                    positionProvider = currentPositionProvider,
                    duration = safeDuration,
                    isPaused = isPaused,
                    isScrubbing = isScrubbing,
                    bufferedDurationProvider = currentBufferedDurationProvider,
                    primaryColor = primaryColor
                )
            }
            "thick" -> {
                StandardOrThickSeekbarCanvas(
                    positionProvider = currentPositionProvider,
                    duration = safeDuration,
                    isPaused = isPaused,
                    isScrubbing = isScrubbing,
                    isThick = true,
                    bufferedDurationProvider = currentBufferedDurationProvider,
                    primaryColor = primaryColor
                )
            }
            "slim" -> {
                SlimSeekbarCanvas(
                    positionProvider = currentPositionProvider,
                    duration = safeDuration,
                    isPaused = isPaused,
                    isScrubbing = isScrubbing,
                    bufferedDurationProvider = currentBufferedDurationProvider,
                    primaryColor = primaryColor
                )
            }
            else -> {
                StandardOrThickSeekbarCanvas(
                    positionProvider = currentPositionProvider,
                    duration = safeDuration,
                    isPaused = isPaused,
                    isScrubbing = isScrubbing,
                    isThick = false,
                    bufferedDurationProvider = currentBufferedDurationProvider,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

@Composable
private fun SquigglySeekbarCanvas(
    positionProvider: () -> Float,
    duration: Float,
    isPaused: Boolean,
    isScrubbing: Boolean,
    bufferedDurationProvider: () -> Float,
    primaryColor: Color
) {
    var phaseOffset by remember { mutableFloatStateOf(0f) }
    var heightFraction by remember { mutableFloatStateOf(1f) }

    val waveLength = 80f
    val lineAmplitude = 6f
    val phaseSpeed = 10f

    val thumbVisibility by animateFloatAsState(
        targetValue = if (isScrubbing) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "wavy_thumb_visibility"
    )

    LaunchedEffect(isPaused, isScrubbing) {
        val shouldFlatten = isPaused || isScrubbing
        val targetHeight = if (shouldFlatten) 0f else 1f
        val animator = Animatable(heightFraction)
        animator.animateTo(
            targetValue = targetHeight,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f)
        ) {
            heightFraction = value
        }
    }

    LaunchedEffect(isPaused) {
        if (isPaused) return@LaunchedEffect
        var lastFrameTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { frameTimeMillis ->
                val deltaTime = (frameTimeMillis - lastFrameTime) / 1000f
                phaseOffset = (phaseOffset + deltaTime * phaseSpeed) % waveLength
                lastFrameTime = frameTimeMillis
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 5.dp.toPx()
        val currentPos = positionProvider()
        val progress = (currentPos / duration).coerceIn(0f, 1f)
        val totalWidth = size.width
        val totalProgressPx = totalWidth * progress
        val centerY = size.height / 2f

        val path = Path()
        val waveStart = -phaseOffset - waveLength / 2f
        val waveEnd = totalWidth

        path.moveTo(waveStart, centerY)
        var currentX = waveStart
        var waveSign = 1f
        var currentAmp = waveSign * heightFraction * lineAmplitude
        val dist = waveLength / 2f

        while (currentX < waveEnd) {
            waveSign = -waveSign
            val nextX = currentX + dist
            val midX = currentX + dist / 2f
            val nextAmp = waveSign * heightFraction * lineAmplitude

            path.cubicTo(
                midX,
                centerY + currentAmp,
                midX,
                centerY + nextAmp,
                nextX,
                centerY + nextAmp
            )

            currentAmp = nextAmp
            currentX = nextX
        }

        val clipTop = lineAmplitude + strokeWidth

        fun drawWavyPath(startX: Float, endX: Float, color: Color) {
            if (endX <= startX) return
            clipRect(
                left = startX,
                top = centerY - clipTop,
                right = endX,
                bottom = centerY + clipTop
            ) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Played path
        drawWavyPath(0f, totalProgressPx, primaryColor)

        // Unplayed path
        drawWavyPath(totalProgressPx, totalWidth, primaryColor.copy(alpha = 0.3f))

        // Buffered path
        val bufDur = bufferedDurationProvider()
        if (bufDur > 0f) {
            val bufferPx = (totalProgressPx + (bufDur / duration) * totalWidth).coerceIn(totalProgressPx, totalWidth)
            drawWavyPath(totalProgressPx, bufferPx, primaryColor.copy(alpha = 0.55f))
        }

        // Start cap circle
        val startAmp = kotlin.math.cos(kotlin.math.abs(waveStart) / waveLength * (2f * kotlin.math.PI.toFloat()))
        drawCircle(
            color = primaryColor,
            radius = strokeWidth / 2f,
            center = Offset(0f, centerY + startAmp * lineAmplitude * heightFraction)
        )

        // Vertical thumb bar
        val barHalfHeight = (lineAmplitude + strokeWidth) * thumbVisibility
        val barWidth = 5.dp.toPx()
        if (barHalfHeight > 0.5f && thumbVisibility > 0.05f) {
            drawLine(
                color = primaryColor.copy(alpha = thumbVisibility),
                start = Offset(totalProgressPx, centerY - barHalfHeight),
                end = Offset(totalProgressPx, centerY + barHalfHeight),
                strokeWidth = barWidth * thumbVisibility,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun StandardOrThickSeekbarCanvas(
    positionProvider: () -> Float,
    duration: Float,
    isPaused: Boolean,
    isScrubbing: Boolean,
    isThick: Boolean,
    bufferedDurationProvider: () -> Float,
    primaryColor: Color
) {
    var heightFraction by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(isPaused, isScrubbing) {
        val shouldFlatten = isPaused || isScrubbing
        val targetHeight = if (shouldFlatten) 0.7f else 1f
        val animator = Animatable(heightFraction)
        animator.animateTo(
            targetValue = targetHeight,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)
        ) {
            heightFraction = value
        }
    }

    val thumbWidth by animateDpAsState(
        targetValue = when {
            isThick && isScrubbing -> 4.dp
            isScrubbing -> 4.dp
            else -> 6.dp
        },
        animationSpec = spring(stiffness = 900f, dampingRatio = 0.9f),
        label = "seekbar_thumb_width"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val baseTrackHeight = if (isThick) 14.dp.toPx() else 6.dp.toPx()
        val trackHeight = baseTrackHeight * heightFraction
        val centerY = size.height / 2f
        val topY = centerY - trackHeight / 2f
        val totalWidth = size.width
        val currentPos = positionProvider()
        val progress = (currentPos / duration).coerceIn(0f, 1f)
        val playedPx = totalWidth * progress
        val outerRadius = trackHeight / 2f

        val bufDur = bufferedDurationProvider()
        val bufferPx = if (bufDur > 0f) {
            (playedPx + (bufDur / duration) * totalWidth).coerceIn(playedPx, totalWidth)
        } else playedPx

        // 1. Unplayed Track
        drawRoundRect(
            color = primaryColor.copy(alpha = 0.3f),
            topLeft = Offset(0f, topY),
            size = Size(totalWidth, trackHeight),
            cornerRadius = CornerRadius(outerRadius)
        )

        // 2. Buffered Track
        if (bufferPx > 0f) {
            drawRoundRect(
                color = primaryColor.copy(alpha = 0.55f),
                topLeft = Offset(0f, topY),
                size = Size(bufferPx, trackHeight),
                cornerRadius = CornerRadius(outerRadius)
            )
        }

        // 3. Played Track
        if (playedPx > 0f) {
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(0f, topY),
                size = Size(playedPx, trackHeight),
                cornerRadius = CornerRadius(outerRadius)
            )
        }

        // 4. Thumb Indicator
        val thumbW = thumbWidth.toPx()
        val thumbH = if (isThick) trackHeight + 4.dp.toPx() else 18.dp.toPx()
        val thumbRadius = if (isThick) 4.dp.toPx() else thumbW / 2f
        val thumbLeft = (playedPx - thumbW / 2f).coerceIn(0f, totalWidth - thumbW)

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(thumbLeft, centerY - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbRadius)
        )
    }
}

@Composable
private fun SlimSeekbarCanvas(
    positionProvider: () -> Float,
    duration: Float,
    isPaused: Boolean,
    isScrubbing: Boolean,
    bufferedDurationProvider: () -> Float,
    primaryColor: Color
) {
    val trackHeight by animateDpAsState(
        targetValue = when {
            isScrubbing -> 14.dp
            isPaused -> 6.dp
            else -> 8.dp
        },
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.75f),
        label = "slim_seekbar_height"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val currentPos = positionProvider()
        val progress = (currentPos / duration).coerceIn(0f, 1f)
        val totalWidth = size.width
        val playedPx = totalWidth * progress
        val centerY = size.height / 2f
        val height = trackHeight.toPx()
        val outerRadius = height / 2f

        val bufDur = bufferedDurationProvider()
        val bufferPx = if (bufDur > 0f) {
            (playedPx + (bufDur / duration) * totalWidth).coerceIn(playedPx, totalWidth)
        } else playedPx

        fun drawSegment(startX: Float, endX: Float, color: Color) {
            if (endX - startX < 0.5f) return
            val path = Path()
            path.addRoundRect(
                RoundRect(
                    left = startX,
                    top = centerY - outerRadius,
                    right = endX,
                    bottom = centerY + outerRadius,
                    cornerRadius = CornerRadius(outerRadius)
                )
            )
            drawPath(path, color)
        }

        // Unplayed
        drawSegment(0f, totalWidth, primaryColor.copy(alpha = 0.3f))

        // Buffer
        if (bufferPx > 0f) {
            drawSegment(0f, bufferPx, primaryColor.copy(alpha = 0.55f))
        }

        // Played
        if (playedPx > 0f) {
            drawSegment(0f, playedPx, primaryColor)
        }
    }
}
