package com.devson.nvplayer.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.devson.nvplayer.data.model.LibraryMediaType
import com.devson.nvplayer.data.model.SeriesDetail
import com.devson.nvplayer.ui.screen.library.components.EpisodeListItem
import com.devson.nvplayer.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Long,
    viewModel: LibraryViewModel,
    onEpisodeSelected: (EpisodeEntity, List<EpisodeEntity>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var seriesDetail by remember { mutableStateOf<SeriesDetail?>(null) }
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(seriesId) {
        val detail = viewModel.getSeriesDetails(seriesId)
        seriesDetail = detail
    }

    val detail = seriesDetail

    if (detail == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val seasons = detail.seasonsWithEpisodes
    val currentSeason = seasons.getOrNull(selectedSeasonIndex)
    val currentEpisodes = currentSeason?.episodes ?: emptyList()
    val firstEpisodeUri = currentEpisodes.firstOrNull()?.fileUri

    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 500f).coerceIn(0f, 1f)
        }
    }

    val imageRequest = remember(firstEpisodeUri) {
        ImageRequest.Builder(context)
            .data(firstEpisodeUri)
            .size(1080, 720)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(400)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 1. Collapsing Backdrop Header with Parallax / Fade
            item(key = "header_backdrop") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .graphicsLayer {
                            alpha = 1f - (scrollOffset * 0.7f)
                            translationY = -listState.firstVisibleItemScrollOffset * 0.3f
                        }
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = detail.series.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Cinematic Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )

                    // Play Next / Play All FAB
                    if (currentEpisodes.isNotEmpty()) {
                        FilledIconButton(
                            onClick = {
                                val firstEp = currentEpisodes.first()
                                onEpisodeSelected(firstEp, currentEpisodes)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 12.dp)
                                .size(56.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play Series",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // 2. Metadata Section
            item(key = "series_metadata") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = detail.series.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Badges row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val typeName = "Series"
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = typeName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${seasons.size} Season${if (seasons.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${detail.totalEpisodes} Episodes",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Synopsis
                    Text(
                        text = detail.series.synopsis ?: "Watch all episodes of ${detail.series.title} categorized in high definition with automated subtitle and chapter detection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 1.dp
                    )
                }
            }

            // 3. Season Selector
            if (seasons.size > 1) {
                item(key = "season_selector") {
                    ScrollableTabRow(
                        selectedTabIndex = selectedSeasonIndex.coerceIn(0, seasons.lastIndex),
                        edgePadding = 16.dp,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { tabPositions ->
                            if (selectedSeasonIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSeasonIndex]),
                                    color = MaterialTheme.colorScheme.primary,
                                    height = 3.dp
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        seasons.forEachIndexed { index, s ->
                            Tab(
                                selected = selectedSeasonIndex == index,
                                onClick = { selectedSeasonIndex = index },
                                text = {
                                    Text(
                                        text = "Season ${s.season.seasonNumber}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (selectedSeasonIndex == index) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (selectedSeasonIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 4. Episodes List Header
            item(key = "episodes_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Episodes (${currentEpisodes.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 5. Episode List Items
            items(currentEpisodes, key = { it.id }) { episode ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    EpisodeListItem(
                        episode = episode,
                        durationMs = episode.durationMillis,
                        onPlayClick = {
                            onEpisodeSelected(episode, currentEpisodes)
                        }
                    )
                }
            }
        }

        // Top App Bar with back button
        Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = scrollOffset),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                AnimatedVisibility(
                    visible = scrollOffset > 0.6f,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = detail.series.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
