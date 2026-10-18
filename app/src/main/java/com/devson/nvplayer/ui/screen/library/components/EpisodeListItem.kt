package com.devson.nvplayer.ui.screen.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.devson.nvplayer.data.database.EpisodeEntity
import com.devson.nvplayer.util.formatDuration

@Composable
fun EpisodeListItem(
    episode: EpisodeEntity,
    durationMs: Long = 0L,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(episode.fileUri) {
        ImageRequest.Builder(context)
            .data(episode.fileUri)
            .size(320, 180)
            .precision(coil3.size.Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }

    val progress = if (durationMs > 0L) {
        (episode.lastPlaybackPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onPlayClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with Play overlay & progress
            Box(
                modifier = Modifier
                    .width(115.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Play icon pill
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Linear Progress bar
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Episode Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "EP ${episode.episodeNumber}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (episode.isWatched) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Watched",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = episode.title ?: "Episode ${episode.episodeNumber}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (durationMs > 0) formatDuration(durationMs) else "Local Episode",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onPlayClick) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play Episode",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
