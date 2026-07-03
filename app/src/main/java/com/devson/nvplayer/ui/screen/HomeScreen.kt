package com.devson.nvplayer.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.ui.common.components.PreviewFloatingActionButton
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoThumbnail
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatDate
import com.devson.nvplayer.util.formatSize
import com.devson.nvplayer.viewmodel.FileOperationsViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.devson.nvplayer.ui.screen.videolist.components.video.DurationBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: VideoListViewModel,
    fileOpsViewModel: FileOperationsViewModel,
    homeViewModel: HomeViewModel,
    onFolderClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onVideoClick: (Uri, List<Uri>) -> Unit,
    onRecycleBinClick: () -> Unit,
    onSearch: (String) -> Unit,
    onBrowseClick: () -> Unit,
    onFeedClick: () -> Unit,
    onSeeMoreHistoryClick: () -> Unit,
    onNetworkHistoryClick: () -> Unit
) {
    val context = LocalContext.current

    // Trigger initial loading of videos
    LaunchedEffect(Unit) {
        viewModel.loadVideos()
    }

    val videosByFolder by viewModel.videosByFolder.collectAsState()
    val videosFlat by viewModel.videosFlat.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val viewSettings by viewModel.viewSettings.collectAsState()

    // Watch History
    val history by homeViewModel.history.collectAsState()
    val historyMap = remember(history) { history.associateBy { it.uri } }

    var clickedVideoUri by remember { mutableStateOf<String?>(null) }
    var selectedVideoForInfo by remember { mutableStateOf<Video?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                clickedVideoUri = null
                homeViewModel.loadWatchHistory(forceVerify = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allVideosFlat = videosFlat
    val folders = remember(videosByFolder) { videosByFolder.keys.toList() }

    val continueWatchingVideos = remember(history, allVideosFlat) {
        history.mapNotNull { historyEntry ->
            allVideosFlat.find { it.uri == historyEntry.uri }
        }
    }

    var displayedContinueWatchingVideos by remember { mutableStateOf(continueWatchingVideos) }
    LaunchedEffect(continueWatchingVideos) {
        if (clickedVideoUri == null) {
            displayedContinueWatchingVideos = continueWatchingVideos
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val storageInfo by homeViewModel.storageInfo.collectAsState()

    var showNetworkDialog by remember { mutableStateOf(false) }
    var showYtdlpMissingDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (viewSettings.showQuickFab) {
                val lastPlayedVideo = remember(history, allVideosFlat) {
                    val lastHistory = history.firstOrNull()
                    if (lastHistory != null) allVideosFlat.find { it.uri == lastHistory.uri } else null
                }

                if (lastPlayedVideo != null) {
                    val lastHistoryEntry = remember(lastPlayedVideo, historyMap) { historyMap[lastPlayedVideo.uri] }
                    PreviewFloatingActionButton(
                        enablePreview = viewSettings.enableFabPreview,
                        previewUri = lastPlayedVideo.uri,
                        previewTitle = lastPlayedVideo.title,
                        previewDurationMs = lastPlayedVideo.duration,
                        previewLastPositionMs = lastHistoryEntry?.lastPositionMs ?: 0L,
                        onPlay = {
                            val playlist = listOf(Uri.parse(lastPlayedVideo.uri))
                            onVideoClick(Uri.parse(lastPlayedVideo.uri), playlist)
                        },
                        onNetworkStreamClick = { showNetworkDialog = true }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Personalized Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Nosved Player",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showNetworkDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = "Network Stream",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Bento Main Library Card
            QuickActionCardBento(
                title = "Browse Video Library",
                subtitle = "Explore all folders, files, and playlists",
                icon = Icons.Default.VideoLibrary,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onBrowseClick,
                isProminent = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            // 2. Latest Videos Carousel
            if (viewSettings.showLatestVideos && allVideosFlat.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Latest Videos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    val latestVideos = remember(allVideosFlat) {
                        allVideosFlat.sortedByDescending { it.dateAdded }.take(10)
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = latestVideos,
                            key = { it.uri }
                        ) { video ->
                            LatestVideoItem(
                                video = video,
                                onClick = {
                                    val playlist = latestVideos.map { Uri.parse(it.uri) }
                                    onVideoClick(Uri.parse(video.uri), playlist)
                                },
                                onLongClick = {
                                    selectedVideoForInfo = video
                                }
                            )
                        }
                    }
                }
            }

            // 3. Continue Watching (Carousel Component)
            if (viewSettings.showHistoryCard && continueWatchingVideos.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Continue Watching",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    val maxVideos = 10
                    val videoDisplayList = remember(displayedContinueWatchingVideos) {
                        if (displayedContinueWatchingVideos.size > maxVideos) displayedContinueWatchingVideos.take(maxVideos) else displayedContinueWatchingVideos
                    }

                    key(videoDisplayList) {
                        val carouselState = rememberCarouselState(itemCount = { videoDisplayList.size + 1 })

                        HorizontalMultiBrowseCarousel(
                            state = carouselState,
                            preferredItemWidth = 150.dp,
                            itemSpacing = 12.dp,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { index ->
                            if (index < videoDisplayList.size) {
                                val video = videoDisplayList[index]
                                val historyEntry = historyMap[video.uri]
                                val lastPositionMs = historyEntry?.lastPositionMs ?: 0L

                                val isClicked = clickedVideoUri == video.uri
                                val animatedAlpha by animateFloatAsState(
                                    targetValue = if (isClicked) 0f else 1f,
                                    animationSpec = tween(durationMillis = 250),
                                    label = "cardAlpha"
                                )
                                val animatedScale by animateFloatAsState(
                                    targetValue = if (isClicked) 0.85f else 1f,
                                    animationSpec = tween(durationMillis = 250),
                                    label = "cardScale"
                                )

                                HistoryCardItem(
                                    video = video,
                                    lastPositionMs = lastPositionMs,
                                    onClick = {
                                        clickedVideoUri = video.uri
                                        coroutineScope.launch {
                                            delay(200)
                                            val playlist = continueWatchingVideos.map { Uri.parse(it.uri) }
                                            onVideoClick(Uri.parse(video.uri), playlist)
                                        }
                                    },
                                    onRemoveClick = {
                                        homeViewModel.removeFromHistory(video.uri)
                                    },
                                    onShareClick = {
                                        com.devson.nvplayer.ui.screens.videolist.utils.shareVideos(context, listOf(video))
                                    },
                                    modifier = Modifier
                                        .graphicsLayer {
                                            alpha = animatedAlpha
                                            scaleX = animatedScale
                                            scaleY = animatedScale
                                        }
                                        .height(220.dp)
                                        .maskClip(RoundedCornerShape(24.dp))
                                )
                            } else {
                                Card(
                                    modifier = Modifier
                                        .height(220.dp)
                                        .width(150.dp)
                                        .maskClip(RoundedCornerShape(24.dp))
                                        .clickable { onSeeMoreHistoryClick() },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = "See More",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "See More",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Quick Navigation Dashboard (Bento Layout)
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Quick Navigation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bento Secondary vertical feed card
                    QuickActionCardBentoSmall(
                        title = "Video Feed",
                        subtitle = "Reels-style vertical player",
                        icon = Icons.Default.PlayCircle,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = onFeedClick,
                        modifier = Modifier.weight(1f)
                    )

                    // Bento Tertiary Recycle Bin Card
                    QuickActionCardBentoSmall(
                        title = "Recycle Bin",
                        subtitle = "Restore deleted media",
                        icon = Icons.Default.Delete,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = onRecycleBinClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 5. Stats Dashboard Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    title = "Folders",
                    value = folders.size.toString(),
                    icon = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    title = "Videos",
                    value = allVideosFlat.size.toString(),
                    icon = { Icon(Icons.Default.VideoLibrary, null, tint = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    title = "Played",
                    value = history.size.toString(),
                    icon = { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.tertiary) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 6. Storage Analytics Card
            if (viewSettings.showStorageTracker) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Storage Analyzer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${storageInfo.third}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                              )
                        }
                        
                        LinearProgressIndicator(
                            progress = { (storageInfo.third.toFloat() / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${String.format("%.1f", storageInfo.second)} GB Used",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "${String.format("%.1f", storageInfo.first)} GB Total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showNetworkDialog) {
        NetworkStreamDialog(
            onDismiss = { showNetworkDialog = false },
            onPlay = { uri ->
                val uriString = uri.toString().lowercase(java.util.Locale.ROOT)
                val isYoutube = uriString.contains("youtube") || uriString.contains("youtu.be")
                val isYtdlpInstalled = java.io.File(
                    com.devson.nvplayer.player.ytdlp.YtdlpManager.getYtdlDir(context),
                    "yt-dlp"
                ).exists()

                if (isYoutube && !isYtdlpInstalled) {
                    showNetworkDialog = false
                    showYtdlpMissingDialog = true
                } else {
                    showNetworkDialog = false
                    onVideoClick(uri, listOf(uri))
                }
            },
            onHistoryClick = {
                showNetworkDialog = false
                onNetworkHistoryClick()
            }
        )
    }

    if (showYtdlpMissingDialog) {
        AlertDialog(
            onDismissRequest = { showYtdlpMissingDialog = false },
            title = {
                Text(
                    text = "yt-dlp Required",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "This stream requires yt-dlp to extract the video. Please install yt-dlp first under App Settings to play YouTube videos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showYtdlpMissingDialog = false
                        onSettingsClick()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Go to Settings")
                }
            },
                    dismissButton = {
                TextButton(onClick = { showYtdlpMissingDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    selectedVideoForInfo?.let { video ->
        VideoInfoDialog(
            video = video,
            onDismissRequest = { selectedVideoForInfo = null }
        )
    }
}

@Composable
fun StatsCard(
    title: String,
    value: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                icon()
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun QuickActionCardBento(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isProminent: Boolean = false
) {
    val finalBgColor = if (isProminent) containerColor else containerColor.copy(alpha = 0.12f)
    val finalContentColor = if (isProminent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val finalSubtitleColor = if (isProminent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    val finalIconBoxBg = if (isProminent) MaterialTheme.colorScheme.onPrimaryContainer else containerColor
    val finalIconTint = if (isProminent) containerColor else iconColor

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = finalBgColor
        ),
        border = if (isProminent) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            containerColor.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(finalIconBoxBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = finalIconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = finalContentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = finalSubtitleColor
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = finalContentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun QuickActionCardBentoSmall(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor.copy(alpha = 0.12f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            containerColor.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    video: Video,
    lastPositionMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (video.duration > 0) lastPositionMs.toFloat() / video.duration else 0f
    val formattedProgress = "${formatDuration(lastPositionMs)} / ${formatDuration(video.duration)}"

    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Thumbnail filling the entire card
            VideoThumbnail(
                uri = video.thumbnailUri ?: video.uri,
                modifier = Modifier.fillMaxSize(),
                showPlayIcon = false
            )

            // 2. Play overlay icon in the center
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 3. Info text overlaid at the bottom on a vertical gradient backplate
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.folderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formattedProgress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            // 4. Thin progress bar at the very bottom
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
}

@Composable
fun FolderCard(
    folder: VideoFolder,
    videoCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$videoCount videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun NetworkStreamDialog(
    onDismiss: () -> Unit,
    onPlay: (Uri) -> Unit,
    onHistoryClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    var urlText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Play Network Stream",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onHistoryClick) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.History,
                        contentDescription = "Stream History",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Enter a video stream URL (HTTP/HTTPS, HLS, RTMP, etc.) to stream directly in the player.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { 
                        urlText = it
                        errorText = null
                    },
                    placeholder = { Text("https://example.com/video.mp4") },
                    singleLine = true,
                    isError = errorText != null,
                    trailingIcon = {
                        if (urlText.isNotEmpty()) {
                            IconButton(onClick = { urlText = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Text"
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                val clipText = clipboardManager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                if (!clipText.isNullOrBlank()) {
                                    urlText = clipText
                                    errorText = null
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste Clipboard"
                                )
                            }
                        }
                    },
                    supportingText = {
                        if (errorText != null) {
                            Text(
                                    text = errorText ?: "",
                                    color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                TextButton(
                    onClick = {
                        urlText = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
                        errorText = null
                    },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load Demo")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = urlText.trim()
                    if (trimmed.isBlank()) {
                        errorText = "URL cannot be empty"
                    } else {
                        val parsedUri = runCatching { Uri.parse(trimmed) }.getOrNull()
                        if (parsedUri == null || parsedUri.scheme.isNullOrBlank()) {
                            errorText = "Please enter a valid URL"
                        } else {
                            onPlay(parsedUri)
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Play")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LatestVideoItem(
    video: Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(160.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            VideoThumbnail(
                uri = video.thumbnailUri ?: video.uri,
                modifier = Modifier.fillMaxSize(),
                showPlayIcon = false
            )
            DurationBadge(duration = video.duration)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VideoInfoDialog(
    video: Video,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Video Information",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoRow(label = "Name", value = video.title)
                InfoRow(label = "Duration", value = formatDuration(video.duration))
                InfoRow(label = "File Size", value = formatSize(video.size))
                InfoRow(label = "Time of Creation", value = formatDate(video.dateAdded))
                InfoRow(label = "Location", value = video.path)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryCardItem(
    video: Video,
    lastPositionMs: Long,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val progress = if (video.duration > 0) lastPositionMs.toFloat() / video.duration else 0f
    val formattedProgress = "${formatDuration(lastPositionMs)} / ${formatDuration(video.duration)}"

    Card(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            VideoThumbnail(
                uri = video.thumbnailUri ?: video.uri,
                modifier = Modifier.fillMaxSize(),
                showPlayIcon = false
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from history") },
                        onClick = {
                            menuExpanded = false
                            onRemoveClick()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            menuExpanded = false
                            onShareClick()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.folderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formattedProgress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }

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
}
