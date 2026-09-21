package com.devson.nvplayer.ui.screen.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.devson.nvplayer.data.model.LibraryMediaItem
import com.devson.nvplayer.data.model.LibraryMediaType

@Composable
fun MediaHeroCarousel(
    items: List<LibraryMediaItem>,
    onItemClick: (LibraryMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        ) { page ->
            val item = items[page]
            val imageRequest = remember(item.backdropUri ?: item.videoUri) {
                ImageRequest.Builder(context)
                    .data(item.backdropUri ?: item.videoUri)
                    .size(1080, 720)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(400)
                    .build()
            }

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onItemClick(item) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Backdrop Image
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Cinematic Gradient Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.90f)
                                    ),
                                    startY = 50f
                                )
                            )
                    )

                    // Content details inside hero
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        // Category Pill Badge
                        val categoryBadge = when (item.type) {
                            LibraryMediaType.MOVIE -> "FEATURED MOVIE"
                            LibraryMediaType.TV_SHOW -> "FEATURED TV SHOW"
                            else -> "FEATURED TV SHOW"
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = categoryBadge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onItemClick(item) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (item.playbackPositionMs > 0) "Resume" else "Watch Now",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            if (item.year != null || item.seasonCount > 1) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    ) {
                                        Text(
                                            text = item.year?.toString() ?: "${item.seasonCount} Seasons",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pager Dots Indicator
        if (items.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(4.dp)
                            .width(if (isSelected) 18.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }
    }
}
