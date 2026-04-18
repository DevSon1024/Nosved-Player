package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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

    // Rotate 90 degrees so Close icon (X) ends as X (0 or 90) rather than + (45)
    val iconRotation by animateFloatAsState(
        targetValue = if (enablePreview && isPreviewVisible) 90f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "fabIconRotation"
    )

    Box(contentAlignment = Alignment.BottomEnd) {
        if (enablePreview && isPreviewVisible) {
            Popup(
                alignment = Alignment.BottomEnd,
                onDismissRequest = { isPreviewVisible = false },
                properties = PopupProperties(focusable = false)
            ) {
                Box(
                    modifier = Modifier.padding(end = 72.dp, bottom = 12.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    AnimatedVisibility(
                        visible = true,
                        enter = scaleIn(
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f),
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = scaleOut(
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                        ) + fadeOut()
                    ) {
                        LastPlayedPreviewCard(
                            uri = previewUri,
                            title = previewTitle,
                            durationMs = previewDurationMs,
                            lastPositionMs = previewLastPositionMs
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .size(56.dp)
                .pointerInput(enablePreview) {
                    detectTapGestures(
                        onTap = {
                            if (isPreviewVisible) {
                                isPreviewVisible = false
                            } else {
                                onPlay()
                            }
                        },
                        onLongPress = {
                            if (enablePreview && previewUri != null) {
                                isPreviewVisible = true
                            }
                        }
                    )
                },
            shape = FabShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp,
            tonalElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (enablePreview && isPreviewVisible) Icons.Filled.Close else Icons.Filled.PlayArrow,
                    contentDescription = if (enablePreview && isPreviewVisible) "Close Preview" else "Play Last Played",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(iconRotation)
                )
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
        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .height(130.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (uri != null) {
                    val cacheKey = remember(uri, lastPositionMs) { "fab_preview_${uri}_${lastPositionMs}" }
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = title ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playedFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        text = remainingFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
