package com.devson.nosvedplayer.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned

@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onVolumeSwipe: (Float) -> Unit,
    onBrightnessSwipe: (Float) -> Unit
) {
    var width by remember { mutableStateOf(0) }
    var height by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                width = coordinates.size.width
                height = coordinates.size.height
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onSingleTap()
                    },
                    onDoubleTap = { offset ->
                        // Determine left or right side constraint
                        if (offset.x < width / 2) {
                            onDoubleTapLeft()
                        } else {
                            onDoubleTapRight()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var isRightSide = false
                detectDragGestures(
                    onDragStart = { offset ->
                        isRightSide = offset.x > width / 2
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Use negative delta for UP swipe, meaning positive value changes
                        val normalizedDeltaY = -dragAmount.y / height
                        if (isRightSide) {
                            onVolumeSwipe(normalizedDeltaY)
                        } else {
                            onBrightnessSwipe(normalizedDeltaY)
                        }
                    }
                )
            }
    )
}
