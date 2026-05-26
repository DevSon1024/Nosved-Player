package com.devson.nvplayer.ui.screen

import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.devson.nvplayer.model.Video
import com.devson.nvplayer.model.VideoFolder
import com.devson.nvplayer.ui.component.PreviewFloatingActionButton
import com.devson.nvplayer.ui.screens.videolist.components.list.VideoThumbnail
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.viewmodel.FileOperationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    onBrowseClick: () -> Unit
) {
    val context = LocalContext.current

    // Trigger initial loading of videos
    LaunchedEffect(Unit) {
        viewModel.loadVideos()
    }

    val videosByFolder by viewModel.videosByFolder.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val viewSettings by viewModel.viewSettings.collectAsState()

    // Watch History
    val history by homeViewModel.history.collectAsState()
    val historyMap = remember(history) { history.associateBy { it.uri } }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.loadWatchHistory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allVideosFlat = remember(videosByFolder) { videosByFolder.values.flatten() }
    val folders = remember(videosByFolder) { videosByFolder.keys.toList() }

    val continueWatchingVideos = remember(history, allVideosFlat) {
        history.mapNotNull { historyEntry ->
            allVideosFlat.find { it.uri == historyEntry.uri }
        }
    }

    var searchQuery by remember { mutableStateOf("") }

    val storageInfo = remember {
        try {
            val path = Environment.getExternalStorageDirectory().absolutePath
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            
            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes
            
            val totalGB = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val usedGB = usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val progress = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
            val percentage = (progress * 100).toInt()
            
            Triple(totalGB, usedGB, percentage)
        } catch (e: Exception) {
            Triple(0.0, 0.0, 0)
        }
    }

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
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Welcome Header
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
                    Text(
                        text = "Your offline video companion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 2. Search Section
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search your videos...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (searchQuery.isNotEmpty()) {
                        onSearch(searchQuery)
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            // 5. Quick Navigation Dashboard
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Quick Navigation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                QuickActionCard(
                    title = "Browse Video Library",
                    subtitle = "Explore all folders, files, and playlists",
                    icon = { Icon(Icons.Default.VideoLibrary, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                    onClick = onBrowseClick
                )
                QuickActionCard(
                    title = "Recycle Bin",
                    subtitle = "View and restore deleted media files",
                    icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                    onClick = onRecycleBinClick
                )
            }

            // 3. Stats Dashboard Section
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

            // Storage Analytics Card (Premium Design)
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

            // 4. Continue Watching
            if (viewSettings.showHistoryCard && continueWatchingVideos.isNotEmpty()) {
                Column {
                    Text(
                        text = "Continue Watching",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(continueWatchingVideos) { video ->
                            val historyEntry = historyMap[video.uri]
                            val progress = if (video.duration > 0) {
                                (historyEntry?.lastPositionMs ?: 0L).toFloat() / video.duration
                            } else 0f

                            ContinueWatchingCard(
                                video = video,
                                progress = progress,
                                onClick = {
                                    val playlist = continueWatchingVideos.map { Uri.parse(it.uri) }
                                    onVideoClick(Uri.parse(video.uri), playlist)
                                }
                            )
                        }
                    }
                }
            }

            // 6. Recent Folders
            if (viewSettings.showVideoCard && folders.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Recent Folders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    folders.take(4).forEach { folder ->
                        val videoCount = videosByFolder[folder]?.size ?: 0
                        FolderCard(
                            folder = folder,
                            videoCount = videoCount,
                            onClick = { onFolderClick(folder.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
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
fun ContinueWatchingCard(
    video: Video,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                VideoThumbnail(
                    uri = video.thumbnailUri ?: video.uri,
                    modifier = Modifier.fillMaxSize(),
                    showPlayIcon = true
                )
                
                // Duration Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = video.folderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
