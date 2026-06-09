package com.devson.nvplayer.ui.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AspectIcons {
    val Fit: ImageVector by lazy {
        ImageVector.Builder(
            name = "Fit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Outer screen border
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 5f)
                lineTo(21f, 5f)
                lineTo(21f, 19f)
                lineTo(3f, 19f)
                close()
            }
            // Inner widescreen content (fitted)
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.0f
            ) {
                moveTo(4f, 8f)
                lineTo(20f, 8f)
                lineTo(20f, 16f)
                lineTo(4f, 16f)
                close()
            }
        }.build()
    }

    val Stretch: ImageVector by lazy {
        ImageVector.Builder(
            name = "Stretch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Outer screen border
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 5f)
                lineTo(21f, 5f)
                lineTo(21f, 19f)
                lineTo(3f, 19f)
                close()
            }
            // Horizontal and vertical stretch arrows in center
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Horizontal line with arrowheads
                moveTo(6f, 12f)
                lineTo(18f, 12f)
                // Left arrowhead
                moveTo(9f, 9f)
                lineTo(6f, 12f)
                lineTo(9f, 15f)
                // Right arrowhead
                moveTo(15f, 9f)
                lineTo(18f, 12f)
                lineTo(15f, 15f)

                // Vertical line with arrowheads
                moveTo(12f, 8f)
                lineTo(12f, 16f)
                // Top arrowhead
                moveTo(9f, 10f)
                lineTo(12f, 8f)
                lineTo(15f, 10f)
                // Bottom arrowhead
                moveTo(9f, 14f)
                lineTo(12f, 16f)
                lineTo(15f, 14f)
            }
        }.build()
    }

    val Crop: ImageVector by lazy {
        ImageVector.Builder(
            name = "Crop",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Outer screen border (thin/light)
            path(
                stroke = SolidColor(Color.White.copy(alpha = 0.5f)),
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 5f)
                lineTo(21f, 5f)
                lineTo(21f, 19f)
                lineTo(3f, 19f)
                close()
            }
            // Crop corners/brackets (thick) and outward arrows
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Top-left crop bracket
                moveTo(7f, 5f)
                lineTo(3f, 5f)
                lineTo(3f, 9f)

                // Top-right crop bracket
                moveTo(17f, 5f)
                lineTo(21f, 5f)
                lineTo(21f, 9f)

                // Bottom-left crop bracket
                moveTo(7f, 19f)
                lineTo(3f, 19f)
                lineTo(3f, 15f)

                // Bottom-right crop bracket
                moveTo(17f, 19f)
                lineTo(21f, 19f)
                lineTo(21f, 15f)

                // Diagonal arrows pointing outwards in center
                moveTo(10f, 10f)
                lineTo(7f, 7f)
                moveTo(7f, 9f)
                lineTo(7f, 7f)
                lineTo(9f, 7f)

                moveTo(14f, 10f)
                lineTo(17f, 7f)
                moveTo(15f, 7f)
                lineTo(17f, 7f)
                lineTo(17f, 9f)

                moveTo(10f, 14f)
                lineTo(7f, 17f)
                moveTo(7f, 15f)
                lineTo(7f, 17f)
                lineTo(9f, 17f)

                moveTo(14f, 14f)
                lineTo(17f, 17f)
                moveTo(15f, 17f)
                lineTo(17f, 17f)
                lineTo(17f, 15f)
            }
        }.build()
    }

    val Original: ImageVector by lazy {
        ImageVector.Builder(
            name = "Original",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Outer screen border (dashed)
            // Top dashes
            path(stroke = SolidColor(Color.White.copy(alpha = 0.5f)), strokeLineWidth = 1f) {
                moveTo(3f, 5f); lineTo(6f, 5f)
                moveTo(9f, 5f); lineTo(12f, 5f)
                moveTo(15f, 5f); lineTo(18f, 5f)
                moveTo(20f, 5f); lineTo(21f, 5f)
            }
            // Bottom dashes
            path(stroke = SolidColor(Color.White.copy(alpha = 0.5f)), strokeLineWidth = 1f) {
                moveTo(3f, 19f); lineTo(6f, 19f)
                moveTo(9f, 19f); lineTo(12f, 19f)
                moveTo(15f, 19f); lineTo(18f, 19f)
                moveTo(20f, 19f); lineTo(21f, 19f)
            }
            // Left dashes
            path(stroke = SolidColor(Color.White.copy(alpha = 0.5f)), strokeLineWidth = 1f) {
                moveTo(3f, 5f); lineTo(3f, 8f)
                moveTo(3f, 11f); lineTo(3f, 14f)
                moveTo(3f, 16f); lineTo(3f, 19f)
            }
            // Right dashes
            path(stroke = SolidColor(Color.White.copy(alpha = 0.5f)), strokeLineWidth = 1f) {
                moveTo(21f, 5f); lineTo(21f, 8f)
                moveTo(21f, 11f); lineTo(21f, 14f)
                moveTo(21f, 16f); lineTo(21f, 19f)
            }
            // Inner 1:1 original content (centered, solid)
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.0f
            ) {
                moveTo(8f, 8f)
                lineTo(16f, 8f)
                lineTo(16f, 16f)
                lineTo(8f, 16f)
                close()
            }
        }.build()
    }
}