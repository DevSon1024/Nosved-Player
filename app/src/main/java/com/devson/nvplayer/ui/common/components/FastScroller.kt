package com.devson.nvplayer.ui.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FastScrollerOverlay(
    itemCount: Int,
    sectionTextExtractor: (index: Int) -> String,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    gridState: LazyGridState? = null,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    bubbleContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    bubbleContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    if (itemCount <= 5) return

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var lastHapticSection by remember { mutableStateOf("") }
    var isUserScrolling by remember { mutableStateOf(false) }

    val isScrollInProgress = listState?.isScrollInProgress == true || gridState?.isScrollInProgress == true

    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            isUserScrolling = true
        } else {
            delay(1500)
            isUserScrolling = false
        }
    }

    val scrollFraction by remember(listState, gridState, itemCount) {
        derivedStateOf {
            if (isDragging) {
                dragFraction
            } else if (listState != null) {
                val firstIndex = listState.firstVisibleItemIndex
                val total = (itemCount - 1).coerceAtLeast(1)
                (firstIndex.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            } else if (gridState != null) {
                val firstIndex = gridState.firstVisibleItemIndex
                val total = (itemCount - 1).coerceAtLeast(1)
                (firstIndex.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val activeIndex by remember(scrollFraction, itemCount) {
        derivedStateOf {
            (scrollFraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
        }
    }

    val currentSectionText by remember(activeIndex) {
        derivedStateOf {
            sectionTextExtractor(activeIndex)
        }
    }

    LaunchedEffect(currentSectionText, isDragging) {
        if (isDragging && currentSectionText.isNotBlank() && currentSectionText != lastHapticSection) {
            lastHapticSection = currentSectionText
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val thumbAlpha by animateFloatAsState(
        targetValue = if (isDragging || isUserScrolling || isScrollInProgress) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "thumbAlpha"
    )

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 4.dp,
        animationSpec = tween(durationMillis = 150),
        label = "thumbWidth"
    )

    val thumbHeight = 48.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .alpha(thumbAlpha)
    ) {
        val minRequiredHeight = topPadding + bottomPadding + thumbHeight
        if (maxHeight <= 0.dp || maxHeight < minRequiredHeight) {
            return@BoxWithConstraints
        }

        val totalHeightPx = with(density) { maxHeight.toPx() }
        val topPaddingPx = with(density) { topPadding.toPx() }
        val bottomPaddingPx = with(density) { bottomPadding.toPx() }
        val thumbHeightPx = with(density) { thumbHeight.toPx() }

        val trackHeightPx = (totalHeightPx - topPaddingPx - bottomPaddingPx - thumbHeightPx).coerceAtLeast(1f)
        val thumbOffsetPx = topPaddingPx + (trackHeightPx * scrollFraction)
        val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }

        val maxThumbY = (maxHeight - bottomPadding - thumbHeight).coerceAtLeast(topPadding)
        val safeThumbOffsetDp = thumbOffsetDp.coerceIn(topPadding, maxThumbY)

        val minBubbleY = topPadding + 4.dp
        val maxBubbleY = (maxHeight - bottomPadding - 56.dp).coerceAtLeast(minBubbleY)
        val bubbleOffsetY = (safeThumbOffsetDp - 8.dp).coerceIn(minBubbleY, maxBubbleY)

        // Section Bubble Popup
        AnimatedVisibility(
            visible = isDragging && currentSectionText.isNotBlank(),
            enter = fadeIn(tween(120)) + scaleIn(tween(120), transformOrigin = TransformOrigin(1f, 0.5f)),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), transformOrigin = TransformOrigin(1f, 0.5f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = (-44).dp,
                    y = bubbleOffsetY
                )
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = bubbleContainerColor,
                shadowElevation = 8.dp,
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(16.dp))
            ) {
                Text(
                    text = currentSectionText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = bubbleContentColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // Fast Scroller Track & Thumb Touch Target
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(36.dp)
                .fillMaxHeight()
                .pointerInput(itemCount, trackHeightPx, topPaddingPx) {
                    detectTapGestures { offset ->
                        val relativeY = (offset.y - topPaddingPx).coerceIn(0f, trackHeightPx)
                        val fraction = (relativeY / trackHeightPx).coerceIn(0f, 1f)
                        dragFraction = fraction
                        val targetIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                        coroutineScope.launch {
                            listState?.scrollToItem(targetIndex)
                            gridState?.scrollToItem(targetIndex)
                        }
                    }
                }
                .pointerInput(itemCount, trackHeightPx, topPaddingPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val relativeY = (offset.y - topPaddingPx).coerceIn(0f, trackHeightPx)
                            val fraction = (relativeY / trackHeightPx).coerceIn(0f, 1f)
                            dragFraction = fraction
                            val targetIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                            coroutineScope.launch {
                                listState?.scrollToItem(targetIndex)
                                gridState?.scrollToItem(targetIndex)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val relativeY = (change.position.y - topPaddingPx).coerceIn(0f, trackHeightPx)
                            val fraction = (relativeY / trackHeightPx).coerceIn(0f, 1f)
                            dragFraction = fraction
                            val targetIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                            coroutineScope.launch {
                                listState?.scrollToItem(targetIndex)
                                gridState?.scrollToItem(targetIndex)
                            }
                        }
                    )
                }
        ) {
            // Visual Scrollbar Thumb
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .offset(y = safeThumbOffsetDp)
                    .size(width = thumbWidth, height = thumbHeight)
                    .clip(CircleShape)
                    .background(
                        thumbColor.copy(alpha = if (isDragging) 1f else 0.75f)
                    )
            )
        }
    }
}
