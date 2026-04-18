package com.devson.nosvedplayer.ui.screens.videolist.components.list

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.devson.nosvedplayer.util.formatDuration

@Composable
fun VideoThumbnail(
    uri: String,
    modifier: Modifier = Modifier,
    showPlayIcon: Boolean = true
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .size(512, 512)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)           // subtle crossfade instead of hard pop
                .build(),
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error       = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            contentDescription = "Video Thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (showPlayIcon) {
            // Gradient scrim so icon is legible over any thumbnail colour
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Frosted-glass pill button
                Surface(
                    shape  = CircleShape,
                    color  = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThumbnailSelectionOverlay(isSelected: Boolean, isDense: Boolean = false) {
    val iconSize = if (isDense) 20.dp else 26.dp
    val circleSize = if (isDense) 32.dp else 40.dp
 
    // Animated background scrim
    val scrimAlpha by animateFloatAsState(
        targetValue  = if (isSelected) 0.45f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "scrimAlpha"
    )
    // Animated check scale for a satisfying "pop"
    val checkScale by animateFloatAsState(
        targetValue  = if (isSelected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkScale"
    )
 
    if (scrimAlpha > 0f || isSelected) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = scrimAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .scale(checkScale)
                    .size(circleSize)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint  = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
fun BoxScope.DurationBadge(duration: Long, isGrid: Boolean = false) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(if (isGrid) 6.dp else 4.dp)
            .background(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(5.dp)
            )
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = formatDuration(duration),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (isGrid) 11.sp else 10.sp
        )
    }
}
