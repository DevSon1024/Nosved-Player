package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.devson.nosvedplayer.util.formatDuration

private val FabShape = RoundedCornerShape(16.dp)

@Composable
fun PreviewFloatingActionButton(
    enablePreview: Boolean,
    previewUri: String?,
    previewTitle: String?,
    previewDurationMs: Long,
    previewLastPositionMs: Long,
    onPlay: () -> Unit
) {
    var isPreviewVisible by remember { mutableStateOf(false) }

    LaunchedEffect(enablePreview) {
        if (!enablePreview) isPreviewVisible = false
    }

    // The Main Scaffold FAB
    Surface(
        modifier = Modifier
            .size(56.dp)
            .pointerInput(enablePreview) {
                detectTapGestures(
                    onTap = { onPlay() },
                    onLongPress = {
                        if (enablePreview && previewUri != null) {
                            isPreviewVisible = true
                        }
                    }
                )
            },
        shape = FabShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // The Interactive Full-Screen Overlay
    PreviewOverlayDialog(
        uri = previewUri,
        title = previewTitle,
        durationMs = previewDurationMs,
        lastPositionMs = previewLastPositionMs,
        isVisible = isPreviewVisible,
        onClose = { isPreviewVisible = false },
        onPlay = {
            isPreviewVisible = false
            onPlay()
        }
    )
}

@Composable
private fun PreviewOverlayDialog(
    uri: String?,
    title: String?,
    durationMs: Long,
    lastPositionMs: Long,
    isVisible: Boolean,
    onClose: () -> Unit,
    onPlay: () -> Unit
) {
    var showDialog by remember { mutableStateOf(isVisible) }

    LaunchedEffect(isVisible) {
        if (isVisible) showDialog = true
    }

    if (showDialog) {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(
                usePlatformDefaultWidth = false, // Allows full screen
                dismissOnBackPress = true,
                dismissOnClickOutside = false // Handled manually by our scrim
            )
        ) {
            val transition = updateTransition(targetState = isVisible, label = "overlay")

            val scrimAlpha by transition.animateFloat(
                transitionSpec = { tween(300) }, label = "scrim"
            ) { if (it) 0.85f else 0f } // Deep background dim

            val cardScale by transition.animateFloat(
                transitionSpec = { spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow) }, label = "cardScale"
            ) { if (it) 1f else 0f }

            val cardAlpha by transition.animateFloat(
                transitionSpec = { tween(200) }, label = "cardAlpha"
            ) { if (it) 1f else 0f }

            val fabRot by transition.animateFloat(
                transitionSpec = { spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium) }, label = "fabRot"
            ) { if (it) 90f else 0f }

            // Keep dialog alive until exit animations finish
            LaunchedEffect(transition.currentState) {
                if (!transition.currentState && !isVisible) {
                    showDialog = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onClose() }) // Clicking background safely closes
                    }
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 16.dp, bottom = 16.dp), // Mirrors Scaffold padding exactly
                    horizontalAlignment = Alignment.End
                ) {
                    // Video Preview Card
                    Box(
                        modifier = Modifier
                            .padding(bottom = 24.dp)
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                                alpha = cardAlpha
                                transformOrigin = TransformOrigin(0.9f, 1f)
                            }
                            .clickable { onPlay() } // Clicking card plays video
                    ) {
                        LastPlayedPreviewCard(uri, title, durationMs, lastPositionMs)
                    }

                    // The 'Close' FAB
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onClose() }) // Clicking X safely closes
                            },
                        shape = FabShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(28.dp)
                                    .rotate(fabRot)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LastPlayedPreviewCard(
    uri: String?,
    title: String?,
    durationMs: Long,
    lastPositionMs: Long
) {
    val context = LocalContext.current
    val playedFormatted = remember(lastPositionMs) { formatDuration(lastPositionMs) }
    val remainingMs = remember(durationMs, lastPositionMs) { (durationMs - lastPositionMs).coerceAtLeast(0L) }
    val remainingFormatted = remember(remainingMs) { formatDuration(remainingMs) }

    Card(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .height(146.dp) // Perfect 16:9 Ratio
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (uri != null) {
                    val cacheKey = remember(uri, lastPositionMs) { "preview_${uri}_${lastPositionMs}" }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uri)
                            .videoFrameMillis(lastPositionMs.coerceAtLeast(1000L))
                            .memoryCacheKey(cacheKey)
                            .diskCacheKey(cacheKey)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(200)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            if (durationMs > 0) {
                val progress = (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playedFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = remainingFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}