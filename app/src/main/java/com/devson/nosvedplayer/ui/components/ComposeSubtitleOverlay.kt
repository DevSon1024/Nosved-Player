package com.devson.nosvedplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import kotlin.math.abs

@Composable
fun ComposeSubtitleOverlay(
    player: Player?,
    textSizeScale: Float,
    bgStyle: Int,
    modifier: Modifier = Modifier
) {
    val currentCues = remember { mutableStateListOf<androidx.media3.common.text.Cue>() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                currentCues.clear()
                currentCues.addAll(cueGroup.cues)
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        var dragAccumulator by remember { mutableFloatStateOf(0f) }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                        if (abs(dragAccumulator) > 40f) {
                            player?.let {
                                val seekAmount = if (dragAccumulator > 0) 1000L else -1000L
                                it.seekTo(it.currentPosition + seekAmount)
                            }
                            dragAccumulator = 0f
                        }
                    }
                }
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

                    Text(
                        text = cue.text.toString(),
                        fontSize = (16 * textSizeScale).sp,
                        color = Color.White,
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
