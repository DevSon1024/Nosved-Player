package com.devson.nvplayer.ui.common.shapes

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

class FolderShape(
    private val cornerRadiusDp: Float = 12f,
    private val tabWidthRatio: Float = 0.36f,
    private val tabHeightRatio: Float = 0.10f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Generic(Path())

        val r = cornerRadiusDp * density.density
        try {
            val minDim = minOf(w, h)
            val corner = r.coerceIn(2f, (minDim * 0.20f).coerceAtLeast(2f))

            val minTabH = corner * 0.5f
            val maxTabH = (h * 0.20f).coerceAtLeast(minTabH)
            val tabH = (h * tabHeightRatio).coerceIn(minTabH, maxTabH)

            val slopeW = (tabH * 1.2f).coerceIn(0f, (w * 0.25f).coerceAtLeast(0f))

            val minTabW = corner
            val maxTabW = (w - slopeW - corner).coerceAtLeast(minTabW)
            val tabW = (w * tabWidthRatio).coerceIn(minTabW, maxTabW)

            val xStart = tabW
            val xEnd = tabW + slopeW

            val path = Path().apply {
                moveTo(0f, corner)
                quadraticTo(0f, 0f, corner, 0f)
                lineTo(xStart, 0f)
                cubicTo(
                    xStart + slopeW * 0.38f, 0f,
                    xStart + slopeW * 0.62f, tabH,
                    xEnd, tabH
                )
                lineTo(w - corner, tabH)
                quadraticTo(w, tabH, w, tabH + corner)
                lineTo(w, h - corner)
                quadraticTo(w, h, w - corner, h)
                lineTo(corner, h)
                quadraticTo(0f, h, 0f, h - corner)
                close()
            }
            return Outline.Generic(path)
        } catch (_: Exception) {
            return Outline.Rounded(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = w,
                    bottom = h,
                    radiusX = r,
                    radiusY = r
                )
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FolderShape) return false
        return cornerRadiusDp == other.cornerRadiusDp &&
            tabWidthRatio == other.tabWidthRatio &&
            tabHeightRatio == other.tabHeightRatio
    }

    override fun hashCode(): Int {
        var result = cornerRadiusDp.hashCode()
        result = 31 * result + tabWidthRatio.hashCode()
        result = 31 * result + tabHeightRatio.hashCode()
        return result
    }

    companion object {
        val Default = FolderShape(12f)
    }
}

val DefaultFolderShape = FolderShape.Default