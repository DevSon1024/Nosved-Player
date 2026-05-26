package com.devson.nvplayer.ui.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

class FolderShape(private val cornerRadiusDp: Float = 8f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val corner = cornerRadiusDp * density.density
        val path = Path().apply {
            val w = size.width
            val h = size.height
            val tabW = w * 0.45f
            val tabH = h * 0.18f

            moveTo(0f, corner)
            quadraticTo(0f, 0f, corner, 0f)
            lineTo(tabW - corner, 0f)
            quadraticTo(tabW, 0f, tabW + corner, tabH)
            lineTo(w - corner, tabH)
            quadraticTo(w, tabH, w, tabH + corner)
            lineTo(w, h - corner)
            quadraticTo(w, h, w - corner, h)
            lineTo(corner, h)
            quadraticTo(0f, h, 0f, h - corner)
            close()
        }
        return Outline.Generic(path)
    }
}