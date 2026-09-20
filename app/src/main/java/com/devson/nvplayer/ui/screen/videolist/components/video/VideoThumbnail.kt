package com.devson.nvplayer.ui.screen.videolist.components.video

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
import androidx.compose.runtime.remember
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
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.devson.nvplayer.util.formatDuration

import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.ui.graphics.vector.ImageVector
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.ViewSettings
import com.devson.nvplayer.ui.screen.videolist.components.common.SubtitleBadge
import com.devson.nvplayer.ui.screen.videolist.components.common.getSubtitleTokens

/**
 * A branded Material Design 3 placeholder displayed while video thumbnails load.
 * Uses surfaceContainerHighest with a subtle centered vector watermark icon.
 */
@Composable
fun BrandedThumbnailPlaceholder(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.MovieFilter,
    iconAlpha: Float = 0.35f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconAlpha)
        )
    }
}

@Composable
fun VideoThumbnail(
    uri: String,
    modifier: Modifier = Modifier,
    showPlayIcon: Boolean = true
) {
    val context = LocalContext.current
    val imageRequest = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(512, 384)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(300)
            .build()
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        // Branded M3 watermark placeholder visible during loading
        BrandedThumbnailPlaceholder()

        AsyncImage(
            model = imageRequest,
            contentDescription = "Video Thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (showPlayIcon) {
            // Gradient scrim so icon is legible over any thumbnail color
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
fun DurationPill(
    duration: Long,
    isGrid: Boolean = false,
    modifier: Modifier = Modifier
) {
    val formattedDuration = remember(duration) { formatDuration(duration) }
    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(5.dp)
            )
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = formattedDuration,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (isGrid) 11.sp else 10.sp
        )
    }
}

@Composable
fun BoxScope.VideoThumbnailBadges(
    video: Video,
    settings: ViewSettings,
    isGrid: Boolean = true
) {
    val subtitleTokens = remember(video.embeddedSubtitles, video.externalSubtitles) {
        getSubtitleTokens(video)
    }
    val showDuration = settings.showLength && settings.displayLengthOverThumbnail
    val showSubtitle = isGrid && subtitleTokens.isNotEmpty()

    if (showDuration || showSubtitle) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(if (isGrid) 6.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showSubtitle) {
                subtitleTokens.take(1).forEach { token ->
                    SubtitleBadge(token = token, isGrid = isGrid)
                }
            }
            if (showDuration) {
                DurationPill(duration = video.duration, isGrid = isGrid)
            }
        }
    }
}

@Composable
fun BoxScope.DurationBadge(
    duration: Long,
    isGrid: Boolean,
    alignment: Alignment
) {
    DurationPill(
        duration = duration,
        isGrid = isGrid,
        modifier = Modifier
            .align(alignment)
            .padding(if (isGrid) 6.dp else 4.dp)
    )
}

@Composable
fun BoxScope.DurationBadge(
    duration: Long
) {
    DurationBadge(duration = duration, isGrid = false, alignment = Alignment.BottomEnd)
}

@Composable
fun BoxScope.DurationBadge(
    duration: Long,
    isGrid: Boolean
) {
    DurationBadge(duration = duration, isGrid = isGrid, alignment = Alignment.BottomEnd)
}

@Composable
fun BoxScope.DurationBadge(
    duration: Long,
    alignment: Alignment
) {
    DurationBadge(duration = duration, isGrid = false, alignment = alignment)
}
