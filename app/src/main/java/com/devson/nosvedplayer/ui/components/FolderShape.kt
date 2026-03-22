package com.devson.nosvedplayer.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class FolderShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(
            Path().apply {
                val tabWidth = size.width * 0.45f
                val tabHeight = size.height * 0.2f
                val cornerRadius = density.run { 8.dp.toPx() }

                // Top-left of the tab
                moveTo(0f, cornerRadius)
                quadraticBezierTo(0f, 0f, cornerRadius, 0f)
                
                // Top-right of the tab
                lineTo(tabWidth - cornerRadius, 0f)
                quadraticBezierTo(tabWidth, 0f, tabWidth + cornerRadius * 0.5f, cornerRadius * 0.5f)
                
                // Slant down to the main folder body
                lineTo(tabWidth + cornerRadius, tabHeight - cornerRadius * 0.5f)
                quadraticBezierTo(tabWidth + cornerRadius * 1.5f, tabHeight, tabWidth + cornerRadius * 2.5f, tabHeight)
                
                // Top-right of the main folder body
                lineTo(size.width - cornerRadius, tabHeight)
                quadraticBezierTo(size.width, tabHeight, size.width, tabHeight + cornerRadius)
                
                // Bottom-right
                lineTo(size.width, size.height - cornerRadius)
                quadraticBezierTo(size.width, size.height, size.width - cornerRadius, size.height)
                
                // Bottom-left
                lineTo(cornerRadius, size.height)
                quadraticBezierTo(0f, size.height, 0f, size.height - cornerRadius)
                
                close()
            }
        )
    }
}
