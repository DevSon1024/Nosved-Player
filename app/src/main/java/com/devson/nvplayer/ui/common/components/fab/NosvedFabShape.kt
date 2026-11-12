package com.devson.nvplayer.ui.common.components.fab

import android.graphics.Matrix
import android.graphics.Path as AndroidPath
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * Cached Material 3 Expressive polygon shapes and morph controller for [NosvedExpressiveFab].
 *
 * Polygons are normalized once into [0, 1] x [0, 1] space so [createOutline] only scales the
 * resulting path without rebuilding polygon geometry or matching cubics on recomposition.
 */
object NosvedFabShapes {
    // Asymmetric 6-vertex diagonal-cut badge: bold top-left bevel and sleek bottom-right bevel
    // balanced by soft sweeping corners at top-right and bottom-left.
    private val normalPolygon: RoundedPolygon = RoundedPolygon(
        vertices = floatArrayOf(
            -0.32f, -1.00f,
            0.98f, -0.98f,
            1.00f, 0.48f,
            0.48f, 1.00f,
            -0.98f, 0.98f,
            -1.00f, -0.32f
        ),
        perVertexRounding = listOf(
            CornerRounding(0.24f, smoothing = 0.7f),
            CornerRounding(0.60f, smoothing = 0.9f),
            CornerRounding(0.24f, smoothing = 0.7f),
            CornerRounding(0.24f, smoothing = 0.7f),
            CornerRounding(0.46f, smoothing = 0.85f),
            CornerRounding(0.24f, smoothing = 0.7f)
        ),
        centerX = 0f,
        centerY = 0f
    ).normalized()

    // Expressive 8-vertex soft cushion badge used when expanded.
    private val expandedPolygon: RoundedPolygon = RoundedPolygon(
        vertices = floatArrayOf(
            0.00f, -0.86f,
            0.94f, -0.94f,
            0.86f, 0.00f,
            0.94f, 0.94f,
            0.00f, 0.86f,
            -0.94f, 0.94f,
            -0.86f, 0.00f,
            -0.94f, -0.94f
        ),
        perVertexRounding = listOf(
            CornerRounding(0.55f, smoothing = 0.9f),
            CornerRounding(0.46f, smoothing = 0.85f),
            CornerRounding(0.55f, smoothing = 0.9f),
            CornerRounding(0.46f, smoothing = 0.85f),
            CornerRounding(0.55f, smoothing = 0.9f),
            CornerRounding(0.46f, smoothing = 0.85f),
            CornerRounding(0.55f, smoothing = 0.9f),
            CornerRounding(0.46f, smoothing = 0.85f)
        ),
        centerX = 0f,
        centerY = 0f
    ).normalized()

    internal val morph: Morph = Morph(normalPolygon, expandedPolygon)

    val normalShape: Shape = NosvedMorphShape(progress = 0f)
    val expandedShape: Shape = NosvedMorphShape(progress = 1f)

    /**
     * Returns a cached static shape when at endpoints (0f or 1f) to avoid outline invalidations
     * while idle, or a lightweight morphing shape for intermediate spring frames.
     */
    fun shapeForProgress(progress: Float): Shape {
        val clamped = progress.coerceIn(0f, 1f)
        return when {
            clamped <= 0.001f -> normalShape
            clamped >= 0.999f -> expandedShape
            else -> NosvedMorphShape(clamped)
        }
    }
}

@Immutable
private data class NosvedMorphShape(private val progress: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = AndroidPath()
        NosvedFabShapes.morph.toPath(progress = progress, path = path)
        val matrix = Matrix().apply {
            setScale(size.width, size.height)
        }
        path.transform(matrix)
        return Outline.Generic(path.asComposePath())
    }
}

