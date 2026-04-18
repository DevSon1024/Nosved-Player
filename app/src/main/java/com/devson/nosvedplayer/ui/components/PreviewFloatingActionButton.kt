package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Box(contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            visible = enablePreview && isPreviewVisible,
            enter = scaleIn(
                transformOrigin = TransformOrigin(0.9f, 1f),
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
            ) + fadeIn(),
            exit = scaleOut(
                transformOrigin = TransformOrigin(0.9f, 1f),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(),
            modifier = Modifier.padding(bottom = 72.dp, end = 4.dp)
        ) {
            LastPlayedPreviewCard(
                uri = previewUri,
                title = previewTitle,
                durationMs = previewDurationMs,
                lastPositionMs = previewLastPositionMs,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        isPreviewVisible = false
                        onPlay()
                    }
            )
        }

        val fabColor by animateColorAsState(
            targetValue = if (isPreviewVisible) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
            label = "fabColor"
        )
        val iconColor by animateColorAsState(
            targetValue = if (isPreviewVisible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
            label = "iconColor"
        )

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
            color = fabColor,
            shadowElevation = if (isPreviewVisible) 2.dp else 6.dp,
            tonalElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = isPreviewVisible,
                    transitionSpec = {
                        (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut())
                    },
                    label = "iconTransition"
                ) { isVisible ->
                    Icon(
                        imageVector = if (isVisible) Icons.Filled.Close else Icons.Filled.PlayArrow,
                        contentDescription = if (isVisible) "Close Preview" else "Play",
                        tint = iconColor,
                        modifier = Modifier.size(28.dp)
                    )
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
    lastPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playedFormatted = remember(lastPositionMs) { formatDuration(lastPositionMs) }
    val remainingMs = remember(durationMs, lastPositionMs) { (durationMs - lastPositionMs).coerceAtLeast(0L) }
    val remainingFormatted = remember(remainingMs) { formatDuration(remainingMs) }

    Card(
        modifier = modifier.width(180.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .height(101.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (uri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uri)
                            .videoFrameMillis(lastPositionMs.coerceAtLeast(1000L))
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .crossfade(200)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (durationMs > 0) {
                val progress = (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = title ?: "Unknown",
                    style = MaterialTheme.typography.labelLarge,
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = remainingFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}