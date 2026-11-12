package com.devson.nvplayer.ui.common.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val NosvedOuterContour = listOf(
    22.46f to 21.19f, 20.80f to 24.02f, 20.80f to 75.68f, 21.68f to 77.83f,
    25.10f to 79.49f, 38.18f to 79.49f, 41.02f to 78.42f, 42.48f to 75.20f,
    42.68f to 61.52f, 61.23f to 79.00f, 74.80f to 79.49f, 77.44f to 78.61f,
    79.10f to 75.68f, 78.32f to 22.07f, 75.59f to 20.41f, 59.57f to 20.90f,
    57.62f to 23.63f, 57.42f to 33.50f, 40.72f to 20.61f
)

private val NosvedBarsHoleContour = listOf(
    24.02f to 22.95f, 39.84f to 22.75f, 59.96f to 38.48f, 60.74f to 22.95f,
    76.37f to 23.44f, 75.98f to 76.76f, 62.01f to 76.76f, 46.29f to 61.72f,
    46.58f to 59.28f, 62.89f to 49.12f, 41.31f to 35.94f, 40.04f to 38.38f,
    40.04f to 75.78f, 38.38f to 77.15f, 23.73f to 76.56f
)

private val NosvedTriangleHoleContour = listOf(
    44.34f to 41.60f, 56.45f to 48.93f, 55.86f to 50.20f,
    44.82f to 56.54f, 43.26f to 55.76f, 43.26f to 42.58f
)

/** Builds a closed Path from a percentage-space polygon, rounding every vertex. */
private fun buildRoundedContour(
    pointsPct: List<Pair<Float, Float>>,
    w: Float,
    h: Float,
    cornerRadius: Float,
    contentScale: Float = 1f
): Path {
    val n = pointsPct.size
    val pts = pointsPct.map { (px, py) ->
        val sx = (px - 50f) * contentScale + 50f
        val sy = (py - 50f) * contentScale + 50f
        Offset(sx / 100f * w, sy / 100f * h)
    }
    val path = Path()
    for (i in 0 until n) {
        val curr = pts[i]
        val prev = pts[(i - 1 + n) % n]
        val next = pts[(i + 1) % n]

        val toPrev = prev - curr
        val toNext = next - curr
        val prevLen = toPrev.getDistance()
        val nextLen = toNext.getDistance()
        val r = minOf(cornerRadius, prevLen * 0.45f, nextLen * 0.45f)

        val startPt = if (prevLen > 0f) curr + toPrev / prevLen * r else curr
        val endPt = if (nextLen > 0f) curr + toNext / nextLen * r else curr

        if (i == 0) path.moveTo(startPt.x, startPt.y) else path.lineTo(startPt.x, startPt.y)
        path.quadraticTo(curr.x, curr.y, endPt.x, endPt.y)
    }
    path.close()
    return path
}

/**
 * Lightweight static Nosved logo mark reusing the canonical brand geometry without
 * infinite animations or shimmer controllers. Path geometry is cached per size via
 * [drawWithCache], and [colorProvider] allows tint transitions without rebuilding paths.
 */
@Composable
fun NosvedLogoMark(
    modifier: Modifier = Modifier,
    tightBounds: Boolean = true,
    colorProvider: () -> Color
) {
    val scale = if (tightBounds) 1.40f else 1.0f
    Spacer(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val combined = if (w > 0f && h > 0f) {
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addPath(buildRoundedContour(NosvedOuterContour, w, h, w * 0.028f * scale, scale))
                    addPath(buildRoundedContour(NosvedBarsHoleContour, w, h, w * 0.02f * scale, scale))
                    addPath(buildRoundedContour(NosvedTriangleHoleContour, w, h, w * 0.014f * scale, scale))
                }
            } else {
                null
            }
            onDrawBehind {
                if (combined != null) {
                    drawPath(path = combined, color = colorProvider())
                }
            }
        }
    )
}

/** Overload accepting a static [Color] for [NosvedLogoMark]. */
@Composable
fun NosvedLogoMark(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    tightBounds: Boolean = true
) {
    NosvedLogoMark(
        modifier = modifier,
        tightBounds = tightBounds,
        colorProvider = { color }
    )
}

/** Returns the first `progress` fraction (by length) of `path`, for a draw-on reveal. */
private fun trimmedSegment(path: Path, progress: Float): Path {
    val out = Path()
    if (progress <= 0f) return out
    val measure = PathMeasure().apply { setPath(path, false) }
    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), out, true)
    return out
}

/** Point along `path` at fractional `progress` (0f..1f) - used to lead the stroke with a glow. */
private fun tipPosition(path: Path, progress: Float): Offset? {
    if (progress <= 0f || progress >= 1f) return null
    val measure = PathMeasure().apply { setPath(path, false) }
    return measure.getPosition(measure.length * progress)
}

private fun stageProgress(overall: Float, start: Float, end: Float): Float =
    ((overall - start) / (end - start)).coerceIn(0f, 1f)

@Composable
fun AnimatedNosvedLogo() {
    AnimatedNosvedLogo(
        modifier = Modifier,
        color = MaterialTheme.colorScheme.primary,
        animateOnEntry = true
    )
}

@Composable
fun AnimatedNosvedLogo(modifier: Modifier) {
    AnimatedNosvedLogo(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        animateOnEntry = true
    )
}

@Composable
fun AnimatedNosvedLogo(modifier: Modifier, color: Color) {
    AnimatedNosvedLogo(
        modifier = modifier,
        color = color,
        animateOnEntry = true
    )
}

/**
 * Animated Nosved logo: draws the mark on stroke-by-stroke like a pen (outer
 * silhouette, then the hollow bars, then the play triangle), then solidifies
 * into the crisp filled mark with a soft shine sweep. Triggers automatically
 * the moment this composable enters composition (i.e. on screen entry).
 */
@Composable
fun AnimatedNosvedLogo(
    modifier: Modifier,
    color: Color,
    animateOnEntry: Boolean
) {
    var hasAnimated by rememberSaveable { mutableStateOf(!animateOnEntry) }

    val containerAlpha = remember { Animatable(if (hasAnimated) 1f else 0f) }
    val containerScale = remember { Animatable(if (hasAnimated) 1f else 0.72f) }
    val drawProgress = remember { Animatable(if (hasAnimated) 1f else 0f) }
    val fillReveal = remember { Animatable(if (hasAnimated) 1f else 0f) }

    LaunchedEffect(hasAnimated) {
        if (!hasAnimated) {
            launch { containerAlpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
            launch {
                containerScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            delay(90)
            drawProgress.animateTo(1f, tween(durationMillis = 1150, easing = FastOutSlowInEasing))
            fillReveal.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
            // subtle settle "pop" once the mark has fully solidified
            containerScale.animateTo(1.05f, tween(120, easing = FastOutSlowInEasing))
            containerScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            hasAnimated = true
        }
    }

    val idleTransition = rememberInfiniteTransition(label = "nosved_idle")
    val breathe by idleTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val shimmer by idleTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val strokeWidthPx = with(LocalDensity.current) { 3.4.dp.toPx() }
    val glowRadiusPx = with(LocalDensity.current) { 5.dp.toPx() }

    Box(
        modifier = modifier
            .scale(containerScale.value * breathe)
            .alpha(containerAlpha.value),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val outerPath = buildRoundedContour(NosvedOuterContour, w, h, w * 0.028f)
            val barsHolePath = buildRoundedContour(NosvedBarsHoleContour, w, h, w * 0.02f)
            val triHolePath = buildRoundedContour(NosvedTriangleHoleContour, w, h, w * 0.014f)

            // Phase 1: line-drawing sketch reveal
            if (drawProgress.value < 1f || fillReveal.value < 1f) {
                val outerP = stageProgress(drawProgress.value, 0f, 0.5f)
                val barsP = stageProgress(drawProgress.value, 0.38f, 0.8f)
                val triP = stageProgress(drawProgress.value, 0.68f, 1f)
                val sketchAlpha = 1f - fillReveal.value

                val strokeStyle = Stroke(strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(trimmedSegment(outerPath, outerP), color.copy(alpha = sketchAlpha), style = strokeStyle)
                drawPath(trimmedSegment(barsHolePath, barsP), color.copy(alpha = sketchAlpha), style = strokeStyle)
                drawPath(trimmedSegment(triHolePath, triP), color.copy(alpha = sketchAlpha), style = strokeStyle)

                // leading glow "pen tip" following whichever stroke is currently drawing
                val tip = when {
                    outerP in 0.001f..0.999f -> tipPosition(outerPath, outerP)
                    barsP in 0.001f..0.999f -> tipPosition(barsHolePath, barsP)
                    triP in 0.001f..0.999f -> tipPosition(triHolePath, triP)
                    else -> null
                }
                tip?.let { p ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.85f * sketchAlpha), Color.Transparent),
                            center = p,
                            radius = glowRadiusPx * 2.4f
                        ),
                        radius = glowRadiusPx * 2.4f,
                        center = p
                    )
                    drawCircle(color = color.copy(alpha = sketchAlpha), radius = glowRadiusPx * 0.55f, center = p)
                }
            }

            // Phase 2: crisp solid mark + idle shine sweep
            if (fillReveal.value > 0f) {
                val combined = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addPath(outerPath)
                    addPath(barsHolePath)
                    addPath(triHolePath)
                }
                clipPath(combined) {
                    drawRect(color = color.copy(alpha = fillReveal.value))
                    if (fillReveal.value >= 1f) {
                        val bandWidth = w * 0.5f
                        val startX = -bandWidth + shimmer * (w + bandWidth * 2)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                start = Offset(startX, 0f),
                                end = Offset(startX + bandWidth, h)
                            )
                        )
                    }
                }
            }
        }
    }
}
