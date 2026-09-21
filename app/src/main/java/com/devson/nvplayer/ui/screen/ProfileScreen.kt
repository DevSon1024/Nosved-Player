package com.devson.nvplayer.ui.screen

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devson.nvplayer.domain.model.HomeSection
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.ui.common.components.AnimatedNosvedLogo
import com.devson.nvplayer.ui.common.components.PreviewFloatingActionButton
import com.devson.nvplayer.ui.screen.videolist.components.video.DurationBadge
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoThumbnail
import com.devson.nvplayer.ui.screens.videolist.utils.shareVideos
import com.devson.nvplayer.util.formatDate
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatSize
import com.devson.nvplayer.viewmodel.FileOperationsViewModel
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    viewModel: VideoListViewModel,
    fileOpsViewModel: FileOperationsViewModel,
    homeViewModel: HomeViewModel,
    onBack: () -> Unit,
    onCustomizeClick: () -> Unit,
    onFolderClick: (String) -> Unit,
    onVideoClick: (Uri, List<Uri>) -> Unit,
    onRecycleBinClick: () -> Unit,
    onSearch: (String) -> Unit,
    onBrowseClick: () -> Unit,
    onFeedClick: () -> Unit,
    onSeeMoreHistoryClick: () -> Unit,
    onStorageAnalyzerClick: () -> Unit = {},
    onVaultClick: () -> Unit = {},
    onStreakClick: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

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
            val found = allVideosFlat.find { it.uri == historyEntry.uri }
            if (found != null) {
                found
            } else {
                val fileName = historyEntry.videoTitle ?: (Uri.parse(historyEntry.uri).lastPathSegment?.substringBeforeLast('.') ?: "Video")
                Video(
                    uri = historyEntry.uri,
                    title = fileName,
                    duration = historyEntry.durationMs,
                    folderName = "External",
                    path = historyEntry.uri,
                    size = historyEntry.fileSize,
                    width = 0,
                    height = 0,
                    dateAdded = historyEntry.lastPlayedAt,
                    dateModified = historyEntry.lastPlayedAt
                )
            }
        }
    }

    var displayedContinueWatchingVideos by remember { mutableStateOf(continueWatchingVideos) }
    LaunchedEffect(continueWatchingVideos) {
        if (clickedVideoUri == null) {
            displayedContinueWatchingVideos = continueWatchingVideos
        }
    }

    val storageInfo by homeViewModel.storageInfo.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Overview & Stats",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onStreakClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = "Watch Streak",
                            tint = Color(0xFFFF6D00)
                        )
                    }
                    IconButton(
                        onClick = onCustomizeClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Customize Profile",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
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
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Profile Header Card
            ProfileHeaderCard(
                totalVideos = allVideosFlat.size,
                watchedCount = history.size,
                foldersCount = folders.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

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

            // Latest Videos Carousel
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

            // Dynamic Sections Loop (Ordered by viewSettings.homeSectionOrder)
            viewSettings.homeSectionOrder.forEach { section ->
                when (section) {
                    HomeSection.SHORTCUTS -> {
                        if (viewSettings.isShortcutsVisible) {
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
                                    QuickActionCardBentoSmall(
                                        title = "Video Feed",
                                        subtitle = "Reels-style vertical player",
                                        icon = Icons.Default.PlayCircle,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        onClick = onFeedClick,
                                        modifier = Modifier.weight(1f)
                                    )

                                    QuickActionCardBentoSmall(
                                        title = "Recycle Bin",
                                        subtitle = "Restore deleted media",
                                        icon = Icons.Default.Delete,
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        iconColor = MaterialTheme.colorScheme.onErrorContainer,
                                        onClick = onRecycleBinClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    QuickActionCardBentoSmall(
                                        title = "Privacy Vault",
                                        subtitle = "Encrypted private files",
                                        icon = Icons.Default.Lock,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        onClick = onVaultClick,
                                        modifier = Modifier.weight(1f)
                                    )

                                    QuickActionCardBentoSmall(
                                        title = "Storage",
                                        subtitle = "Analyze media space",
                                        icon = Icons.Default.Analytics,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        iconColor = MaterialTheme.colorScheme.primary,
                                        onClick = onStorageAnalyzerClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    QuickActionCardBentoSmall(
                                        title = "Watch Streak",
                                        subtitle = "Daily goals & activity",
                                        icon = Icons.Default.Whatshot,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        iconColor = Color(0xFFFF6D00),
                                        onClick = onStreakClick,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    HomeSection.HISTORY -> {
                        if (viewSettings.showHistoryCard) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Continue Watching",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (continueWatchingVideos.isNotEmpty()) {
                                        FilledTonalButton(
                                            onClick = onSeeMoreHistoryClick,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "See All",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                if (continueWatchingVideos.isNotEmpty()) {
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
                                                        shareVideos(context, listOf(video))
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
                                                ElevatedCard(
                                                    modifier = Modifier
                                                        .height(220.dp)
                                                        .width(150.dp)
                                                        .maskClip(MaterialTheme.shapes.extraLarge)
                                                        .clickable { onSeeMoreHistoryClick() },
                                                    shape = MaterialTheme.shapes.extraLarge,
                                                    colors = CardDefaults.elevatedCardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                                    ),
                                                    elevation = CardDefaults.elevatedCardElevation(
                                                        defaultElevation = 3.dp,
                                                        pressedElevation = 6.dp
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
                                                                        MaterialTheme.colorScheme.primaryContainer,
                                                                        CircleShape
                                                                    ),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                                    contentDescription = "See More",
                                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                                )
                                                            }
                                                            Spacer(Modifier.height(8.dp))
                                                            Text(
                                                                text = "See All",
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "No Recent History",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "No recent history. Watch videos for your history to be unlocked here.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HomeSection.DETAILS -> {
                        if (viewSettings.isDetailsVisible && viewSettings.showStorageTracker) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Storage Analytics Card
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable(onClick = onStorageAnalyzerClick),
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
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "${storageInfo.third}%",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
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
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        selectedVideoForInfo?.let { video ->
            VideoInfoDialog(
                video = video,
                onDismissRequest = { selectedVideoForInfo = null }
            )
        }
    }
}

@Composable
private fun ProfileHeaderCard(
    totalVideos: Int,
    watchedCount: Int,
    foldersCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedNosvedLogo(
                        modifier = Modifier.size(34.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        animateOnEntry = true
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nosved Player User",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Personal Media Center",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ProfileMetric(label = "Videos", value = totalVideos.toString())
                ProfileMetric(label = "Watched", value = watchedCount.toString())
                ProfileMetric(label = "Folders", value = foldersCount.toString())
            }
        }
    }
}

@Composable
private fun ProfileMetric(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickActionCardBento(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isProminent: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val gradientBrush = remember(primaryColor, secondaryColor) {
        Brush.linearGradient(
            colors = listOf(
                primaryColor,
                primaryColor.copy(alpha = 0.9f),
                secondaryColor
            )
        )
    }

    val finalBgColor = if (isProminent) Color.Transparent else containerColor.copy(alpha = 0.12f)
    val finalContentColor = if (isProminent) Color.White else MaterialTheme.colorScheme.onSurface
    val finalSubtitleColor = if (isProminent) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    val finalIconBoxBg = if (isProminent) Color.White.copy(alpha = 0.2f) else containerColor
    val finalIconTint = if (isProminent) Color.White else iconColor

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = finalBgColor
        ),
        border = if (isProminent) null else BorderStroke(
            1.dp,
            containerColor.copy(alpha = 0.25f)
        )
    ) {
        val contentModifier = if (isProminent) {
            Modifier.background(gradientBrush).padding(20.dp)
        } else {
            Modifier.padding(20.dp)
        }
        Row(
            modifier = contentModifier,
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
private fun QuickActionCardBentoSmall(
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LatestVideoItem(
    video: Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val cardWidth = remember(screenWidth) { ((screenWidth - 40.dp) / 3.5f).coerceIn(85.dp, 105.dp) }
    val cardHeight = remember(cardWidth) { cardWidth * 0.68f }

    ElevatedCard(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            VideoThumbnail(
                uri = video.thumbnailUri ?: video.uri,
                modifier = Modifier.fillMaxSize(),
                showPlayIcon = false
            )
            DurationBadge(duration = video.duration, alignment = Alignment.TopStart)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
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
private fun HistoryCardItem(
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

    ElevatedCard(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 3.dp,
            pressedElevation = 6.dp
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
