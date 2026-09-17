package com.devson.nvplayer.ui.screen

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.domain.model.SortField
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.getSectionLabel
import com.devson.nvplayer.ui.common.components.FastScrollerOverlay
import com.devson.nvplayer.ui.screen.videolist.components.common.WatchProgressBar
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoThumbnail
import com.devson.nvplayer.ui.screens.videolist.utils.shareVideos
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatSize
import com.devson.nvplayer.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    allVideos: List<Video>,
    onVideoSelected: (Video, List<Video>, Long) -> Unit,
    onBack: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    onPlayStream: (Uri) -> Unit = {},
    onNetworkHistoryClick: () -> Unit = {},
    onNavigateToYtdlpSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val history by homeViewModel.history.collectAsState()

    var isGridView by rememberSaveable { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf(emptySet<String>()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var selectedVideoForInfo by remember { mutableStateOf<Video?>(null) }

    if (showNetworkDialog) {
        NetworkStreamDialog(
            onDismiss = { showNetworkDialog = false },
            onPlay = { uri ->
                showNetworkDialog = false
                onPlayStream(uri)
            },
            onHistoryClick = {
                showNetworkDialog = false
                onNetworkHistoryClick()
            },
            onNavigateToYtdlpSettings = {
                showNetworkDialog = false
                onNavigateToYtdlpSettings()
            }
        )
    }

    val isSelectionMode = selectedUris.isNotEmpty()

    val historyVideos = remember(history, allVideos) {
        history.mapNotNull { historyEntry ->
            val found = allVideos.find { it.uri == historyEntry.uri }
            if (found != null) {
                found
            } else {
                val isNetwork = historyEntry.uri.startsWith("http") || historyEntry.uri.startsWith("ytdl")
                val fileName = historyEntry.videoTitle ?: (Uri.parse(historyEntry.uri).lastPathSegment?.substringBeforeLast('.') ?: "Video")
                Video(
                    uri = historyEntry.uri,
                    title = fileName,
                    duration = historyEntry.durationMs,
                    folderName = if (isNetwork) "Network" else "External",
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(
                            text = "${selectedUris.size} Selected",
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.history_title),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedUris = emptySet() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit Selection"
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        val allSelected = remember(selectedUris, historyVideos) {
                            historyVideos.isNotEmpty() && selectedUris.size == historyVideos.size
                        }
                        IconButton(onClick = {
                            selectedUris = if (allSelected) emptySet() else historyVideos.map { it.uri }.toSet()
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect All" else "Select All"
                            )
                        }
                        IconButton(onClick = {
                            val selectedVideos = historyVideos.filter { it.uri in selectedUris }
                            if (selectedVideos.isNotEmpty()) {
                                shareVideos(context, selectedVideos)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Selected"
                            )
                        }
                        IconButton(onClick = {
                            selectedUris.forEach { uri ->
                                homeViewModel.removeFromHistory(uri)
                            }
                            selectedUris = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected from History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = { showNetworkDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = "Play Network Stream",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (historyVideos.isNotEmpty()) {
                            IconButton(onClick = { isGridView = !isGridView }) {
                                Icon(
                                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = if (isGridView) "List View" else "Grid View"
                                )
                            }
                            IconButton(onClick = {
                                selectedUris = historyVideos.map { it.uri }.toSet()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = "Select items"
                                )
                            }
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = stringResource(R.string.history_clear_all),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        if (historyVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.history_no_history),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showNetworkDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play Network Stream")
                    }
                }
            }
        } else {
            val playlist = remember(historyVideos) { historyVideos }
            val historyMap = remember(history) { history.associateBy { it.uri } }
            val listState = rememberLazyListState()
            val gridState = rememberLazyGridState()

            Box(modifier = Modifier.fillMaxSize()) {
                if (!isGridView) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 32.dp,
                            start = 12.dp,
                            end = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = historyVideos,
                            key = { it.uri }
                        ) { video ->
                            val historyEntry = historyMap[video.uri]
                            val lastPositionMs = historyEntry?.lastPositionMs ?: 0L
                            val isSelected = video.uri in selectedUris

                            HistoryListItem(
                                video = video,
                                lastPositionMs = lastPositionMs,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedUris = if (isSelected) selectedUris - video.uri else selectedUris + video.uri
                                    } else {
                                        onVideoSelected(video, playlist, lastPositionMs)
                                    }
                                },
                                onLongClick = {
                                    selectedUris = if (isSelected) selectedUris - video.uri else selectedUris + video.uri
                                },
                                onRemoveClick = { homeViewModel.removeFromHistory(video.uri) },
                                onShareClick = { shareVideos(context, listOf(video)) },
                                onInfoClick = { selectedVideoForInfo = video }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 32.dp,
                            start = 12.dp,
                            end = 12.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = historyVideos,
                            key = { it.uri }
                        ) { video ->
                            val historyEntry = historyMap[video.uri]
                            val lastPositionMs = historyEntry?.lastPositionMs ?: 0L
                            val isSelected = video.uri in selectedUris

                            HistoryGridItem(
                                video = video,
                                lastPositionMs = lastPositionMs,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedUris = if (isSelected) selectedUris - video.uri else selectedUris + video.uri
                                    } else {
                                        onVideoSelected(video, playlist, lastPositionMs)
                                    }
                                },
                                onLongClick = {
                                    selectedUris = if (isSelected) selectedUris - video.uri else selectedUris + video.uri
                                },
                                onRemoveClick = { homeViewModel.removeFromHistory(video.uri) },
                                onShareClick = { shareVideos(context, listOf(video)) },
                                onInfoClick = { selectedVideoForInfo = video }
                            )
                        }
                    }
                }

                FastScrollerOverlay(
                    itemCount = historyVideos.size,
                    sectionTextExtractor = { index ->
                        historyVideos.getOrNull(index)?.getSectionLabel(SortField.DATE) ?: ""
                    },
                    listState = if (isGridView) null else listState,
                    gridState = if (isGridView) gridState else null,
                    topPadding = padding.calculateTopPadding(),
                    bottomPadding = padding.calculateBottomPadding()
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    homeViewModel.clearAllHistory()
                    showClearDialog = false
                }) {
                    Text(
                        text = stringResource(R.string.history_clear_button),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.history_cancel_button))
                }
            }
        )
    }

    selectedVideoForInfo?.let { video ->
        VideoInfoDialog(
            video = video,
            onDismissRequest = { selectedVideoForInfo = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryListItem(
    video: Video,
    lastPositionMs: Long,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onShareClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "listItemBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "listItemBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(if (isSelected) 1.5.dp else 0.dp, borderColor), RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 66.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                VideoThumbnail(
                    uri = video.thumbnailUri ?: video.uri,
                    modifier = Modifier.fillMaxSize()
                )
                if (video.duration > 0) {
                    WatchProgressBar(lastPositionMs, video.duration)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (video.duration > 0) {
                        Text(
                            text = "${formatDuration(lastPositionMs)} / ${formatDuration(video.duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (video.size > 0) {
                        Text(
                            text = "• ${formatSize(video.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options"
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
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete from history") },
                            onClick = {
                                menuExpanded = false
                                onRemoveClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                menuExpanded = false
                                onShareClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Info") },
                            onClick = {
                                menuExpanded = false
                                onInfoClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryGridItem(
    video: Video,
    lastPositionMs: Long,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onShareClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "gridItemBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "gridItemBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(if (isSelected) 1.5.dp else 0.dp, borderColor), RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
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

                if (video.duration > 0) {
                    WatchProgressBar(lastPositionMs, video.duration)
                }

                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
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
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete from history") },
                                onClick = {
                                    menuExpanded = false
                                    onRemoveClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    menuExpanded = false
                                    onShareClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Info") },
                                onClick = {
                                    menuExpanded = false
                                    onInfoClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (video.duration > 0) {
                    Text(
                        text = "${formatDuration(lastPositionMs)} / ${formatDuration(video.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
