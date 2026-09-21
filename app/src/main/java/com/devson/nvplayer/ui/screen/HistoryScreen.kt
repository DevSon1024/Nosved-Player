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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.R
import com.devson.nvplayer.domain.model.SortField
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoHistoryItem
import com.devson.nvplayer.domain.model.getSectionLabel
import com.devson.nvplayer.ui.common.components.FastScrollerOverlay
import com.devson.nvplayer.ui.screen.videolist.components.common.WatchProgressBar
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoThumbnail
import com.devson.nvplayer.ui.screens.videolist.utils.shareVideos
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatSize
import com.devson.nvplayer.viewmodel.HistoryFilter
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.WatchHistoryViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    allVideos: List<Video>,
    onVideoSelected: (Video, List<Video>, Long) -> Unit,
    onBack: () -> Unit,
    onNavigateToStreak: () -> Unit = {},
    watchHistoryViewModel: WatchHistoryViewModel = viewModel(
        factory = WatchHistoryViewModel.Factory(LocalContext.current)
    ),
    homeViewModel: HomeViewModel? = null,
    onPlayStream: (Uri) -> Unit = {},
    onNetworkHistoryClick: () -> Unit = {},
    onNavigateToYtdlpSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val historyItems by watchHistoryViewModel.filteredHistory.collectAsState()
    val allHistoryItems by watchHistoryViewModel.historyItems.collectAsState()
    val selectedFilter by watchHistoryViewModel.selectedFilter.collectAsState()
    val streakUiState by watchHistoryViewModel.streakUiState.collectAsState()

    var isGridView by rememberSaveable { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf(emptySet<String>()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var selectedDeletedItemForDialog by remember { mutableStateOf<VideoHistoryItem?>(null) }
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

    val availableCount = remember(allHistoryItems) { allHistoryItems.count { !it.isDeleted } }
    val deletedCount = remember(allHistoryItems) { allHistoryItems.count { it.isDeleted } }

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
                        val allSelected = remember(selectedUris, historyItems) {
                            historyItems.isNotEmpty() && selectedUris.size == historyItems.size
                        }
                        IconButton(onClick = {
                            selectedUris = if (allSelected) emptySet() else historyItems.map { it.rawUri }.toSet()
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect All" else "Select All"
                            )
                        }
                        IconButton(onClick = {
                            val selectedVideos = historyItems
                                .filter { it.rawUri in selectedUris && !it.isDeleted }
                                .map { it.toVideo(allVideos) }
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
                                watchHistoryViewModel.removeFromHistory(uri)
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
                        IconButton(onClick = onNavigateToStreak) {
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = "Watch Streak",
                                tint = if (streakUiState.currentStreak > 0) Color(0xFFFF6D00) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showNetworkDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = "Play Network Stream",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (allHistoryItems.isNotEmpty()) {
                            IconButton(onClick = { isGridView = !isGridView }) {
                                Icon(
                                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = if (isGridView) "List View" else "Grid View"
                                )
                            }
                            IconButton(onClick = {
                                selectedUris = historyItems.map { it.rawUri }.toSet()
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
        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (allHistoryItems.isNotEmpty() || streakUiState.currentStreak > 0 || streakUiState.longestStreak > 0) {
                // Top Streak Banner Card
                WatchStreakBannerCard(
                    streakUiState = streakUiState,
                    onClick = onNavigateToStreak,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )

                // Filter Chips Row
                HistoryFilterRow(
                    selectedFilter = selectedFilter,
                    totalCount = allHistoryItems.size,
                    availableCount = availableCount,
                    deletedCount = deletedCount,
                    onSelectFilter = { watchHistoryViewModel.setFilter(it) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            if (historyItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    HistoryEmptyState(
                        filter = selectedFilter,
                        totalCount = allHistoryItems.size,
                        onPlayNetworkClick = { showNetworkDialog = true }
                    )
                }
            } else {
                val availableVideosList = remember(historyItems, allVideos) {
                    historyItems.filter { !it.isDeleted }.map { it.toVideo(allVideos) }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (!isGridView) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 6.dp,
                                bottom = 32.dp,
                                start = 12.dp,
                                end = 12.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = historyItems,
                                key = { it.historyId }
                            ) { item ->
                                val isSelected = item.rawUri in selectedUris

                                HistoryListItem(
                                    item = item,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedUris = if (isSelected) selectedUris - item.rawUri else selectedUris + item.rawUri
                                        } else if (item.isDeleted) {
                                            selectedDeletedItemForDialog = item
                                        } else {
                                            val video = item.toVideo(allVideos)
                                            onVideoSelected(video, availableVideosList, item.lastPositionMs)
                                        }
                                    },
                                    onLongClick = {
                                        selectedUris = if (isSelected) selectedUris - item.rawUri else selectedUris + item.rawUri
                                    },
                                    onRemoveClick = { watchHistoryViewModel.removeFromHistory(item.rawUri) },
                                    onShareClick = {
                                        if (!item.isDeleted) {
                                            shareVideos(context, listOf(item.toVideo(allVideos)))
                                        }
                                    },
                                    onInfoClick = {
                                        if (item.isDeleted) {
                                            selectedDeletedItemForDialog = item
                                        } else {
                                            selectedVideoForInfo = item.toVideo(allVideos)
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 6.dp,
                                bottom = 32.dp,
                                start = 12.dp,
                                end = 12.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = historyItems,
                                key = { it.historyId }
                            ) { item ->
                                val isSelected = item.rawUri in selectedUris

                                HistoryGridItem(
                                    item = item,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedUris = if (isSelected) selectedUris - item.rawUri else selectedUris + item.rawUri
                                        } else if (item.isDeleted) {
                                            selectedDeletedItemForDialog = item
                                        } else {
                                            val video = item.toVideo(allVideos)
                                            onVideoSelected(video, availableVideosList, item.lastPositionMs)
                                        }
                                    },
                                    onLongClick = {
                                        selectedUris = if (isSelected) selectedUris - item.rawUri else selectedUris + item.rawUri
                                    },
                                    onRemoveClick = { watchHistoryViewModel.removeFromHistory(item.rawUri) },
                                    onShareClick = {
                                        if (!item.isDeleted) {
                                            shareVideos(context, listOf(item.toVideo(allVideos)))
                                        }
                                    },
                                    onInfoClick = {
                                        if (item.isDeleted) {
                                            selectedDeletedItemForDialog = item
                                        } else {
                                            selectedVideoForInfo = item.toVideo(allVideos)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    FastScrollerOverlay(
                        itemCount = historyItems.size,
                        sectionTextExtractor = { index ->
                            val item = historyItems.getOrNull(index)
                            if (item != null) {
                                runCatching {
                                    val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                                    val ms = if (item.lastPlayedAt.toString().length < 13) item.lastPlayedAt * 1000L else item.lastPlayedAt
                                    sdf.format(Date(ms))
                                }.getOrDefault("")
                            } else ""
                        },
                        listState = if (isGridView) null else listState,
                        gridState = if (isGridView) gridState else null,
                        topPadding = 0.dp,
                        bottomPadding = 32.dp
                    )
                }
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
                    watchHistoryViewModel.clearAllHistory()
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

    selectedDeletedItemForDialog?.let { deletedItem ->
        DeletedVideoDialog(
            item = deletedItem,
            onDismiss = { selectedDeletedItemForDialog = null },
            onRemoveFromHistory = {
                watchHistoryViewModel.removeFromHistory(deletedItem.rawUri)
                selectedDeletedItemForDialog = null
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

@Composable
private fun HistoryFilterRow(
    selectedFilter: HistoryFilter,
    totalCount: Int,
    availableCount: Int,
    deletedCount: Int,
    onSelectFilter: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == HistoryFilter.ALL,
            onClick = { onSelectFilter(HistoryFilter.ALL) },
            label = { Text("All ($totalCount)") },
            leadingIcon = if (selectedFilter == HistoryFilter.ALL) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null
        )

        FilterChip(
            selected = selectedFilter == HistoryFilter.AVAILABLE,
            onClick = { onSelectFilter(HistoryFilter.AVAILABLE) },
            label = { Text("Available ($availableCount)") },
            leadingIcon = if (selectedFilter == HistoryFilter.AVAILABLE) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null
        )

        FilterChip(
            selected = selectedFilter == HistoryFilter.DELETED,
            onClick = { onSelectFilter(HistoryFilter.DELETED) },
            label = { Text("Deleted ($deletedCount)") },
            leadingIcon = if (selectedFilter == HistoryFilter.DELETED) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null
        )
    }
}

@Composable
private fun HistoryEmptyState(
    filter: HistoryFilter,
    totalCount: Int,
    onPlayNetworkClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val (icon, title, desc) = when {
            totalCount == 0 -> Triple(
                Icons.Default.History,
                stringResource(R.string.history_no_history),
                "Videos you watch will appear here with your watch progress and streak."
            )
            filter == HistoryFilter.DELETED -> Triple(
                Icons.Default.CheckCircle,
                "No Deleted Videos",
                "All watched videos are currently intact on your device."
            )
            filter == HistoryFilter.AVAILABLE -> Triple(
                Icons.Default.FolderOff,
                "No Available Videos",
                "All your watched videos have been deleted from device storage."
            )
            else -> Triple(
                Icons.Default.History,
                "No History Items",
                "No videos match the selected filter."
            )
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (totalCount == 0) {
            Button(
                onClick = onPlayNetworkClick,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryListItem(
    item: VideoHistoryItem,
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
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
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
                if (item.isDeleted) {
                    DeletedVideoThumbnailPlaceholder()
                } else {
                    VideoThumbnail(
                        uri = item.uri ?: item.rawUri,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (item.durationMs > 0L) {
                    WatchProgressBar(item.lastPositionMs, item.durationMs)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.originalName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = formatWatchedDate(item.lastPlayedAt, item.watchedDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isDeleted) {
                        DeletedBadge()
                    } else if (item.durationMs > 0L) {
                        Text(
                            text = "${formatDuration(item.lastPositionMs)} / ${formatDuration(item.durationMs)}",
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
                        if (!item.isDeleted) {
                            DropdownMenuItem(
                                text = { Text("Play") },
                                onClick = {
                                    menuExpanded = false
                                    onClick()
                                },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete from history") },
                            onClick = {
                                menuExpanded = false
                                onRemoveClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                        if (!item.isDeleted) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    menuExpanded = false
                                    onShareClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                        }
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
    item: VideoHistoryItem,
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
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
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
                if (item.isDeleted) {
                    DeletedVideoThumbnailPlaceholder()
                } else {
                    VideoThumbnail(
                        uri = item.uri ?: item.rawUri,
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
                }

                if (item.durationMs > 0L) {
                    WatchProgressBar(item.lastPositionMs, item.durationMs)
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
                            if (!item.isDeleted) {
                                DropdownMenuItem(
                                    text = { Text("Play") },
                                    onClick = {
                                        menuExpanded = false
                                        onClick()
                                    },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete from history") },
                                onClick = {
                                    menuExpanded = false
                                    onRemoveClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                            )
                            if (!item.isDeleted) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    onClick = {
                                        menuExpanded = false
                                        onShareClick()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                )
                            }
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
                    text = item.originalName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatWatchedDate(item.lastPlayedAt, item.watchedDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.isDeleted) {
                        DeletedBadge()
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletedVideoThumbnailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.VideocamOff,
                contentDescription = "File Deleted",
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "DELETED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun DeletedBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = "[DELETED]",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DeletedVideoDialog(
    item: VideoHistoryItem,
    onDismiss: () -> Unit,
    onRemoveFromHistory: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.VideocamOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Video File Deleted",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "\"${item.originalName}\" was moved or deleted from your device storage.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Its watch statistics, progress, and streak contribution are safely preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.rawPath.isNullOrBlank()) {
                    Text(
                        text = "Original Path: ${item.rawPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep in History")
            }
        },
        dismissButton = {
            TextButton(onClick = onRemoveFromHistory) {
                Text(
                    text = "Remove from History",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

fun VideoHistoryItem.toVideo(allVideos: List<Video>): Video {
    val existing = allVideos.find { it.uri == (this.uri ?: this.rawUri) }
    if (existing != null) return existing
    return Video(
        uri = this.uri ?: this.rawUri,
        title = this.originalName,
        duration = this.durationMs,
        folderName = this.folderName ?: (if (this.isNetworkStream) "Network" else "External"),
        path = this.path ?: this.rawPath ?: "",
        size = 0L,
        width = 0,
        height = 0,
        dateAdded = this.lastPlayedAt,
        dateModified = this.lastPlayedAt
    )
}

fun formatWatchedDate(timeMs: Long, dateStr: String? = null): String {
    return try {
        if (timeMs > 0L) {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            val ms = if (timeMs.toString().length < 13) timeMs * 1000L else timeMs
            "Watched " + sdf.format(Date(ms))
        } else if (!dateStr.isNullOrBlank()) {
            val parsed = LocalDate.parse(dateStr)
            val dtf = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            "Watched " + parsed.format(dtf)
        } else {
            "Watched"
        }
    } catch (_: Exception) {
        "Watched"
    }
}
