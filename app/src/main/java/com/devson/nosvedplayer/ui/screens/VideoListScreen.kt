package com.devson.nosvedplayer.ui.screens

import android.Manifest
import com.devson.nosvedplayer.ui.components.CustomRenameDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.devson.nosvedplayer.ui.components.SearchSuggestionsPopup
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.devson.nosvedplayer.model.LayoutMode
import com.devson.nosvedplayer.model.SortDirection
import com.devson.nosvedplayer.model.SortField
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewMode
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.viewmodel.FileOperationsViewModel
import com.devson.nosvedplayer.viewmodel.VideoListViewModel
import android.app.Activity
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Scaffold
import com.devson.nosvedplayer.model.applySort
import com.devson.nosvedplayer.ui.components.ViewSettingsBottomSheet
import com.devson.nosvedplayer.util.SelectionBottomAppBar
import com.devson.nosvedplayer.util.formatDate
import com.devson.nosvedplayer.util.formatDuration
import com.devson.nosvedplayer.util.formatRelativeTime
import com.devson.nosvedplayer.util.formatResolutionCompact
import com.devson.nosvedplayer.util.formatSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.devson.nosvedplayer.ui.components.CustomEmptyStateView
import com.devson.nosvedplayer.ui.components.PreviewFloatingActionButton

sealed class VideoWatchState {
    object Unplayed : VideoWatchState()
    object InProgress : VideoWatchState()
    object Completed : VideoWatchState()
}

fun getWatchState(lastPositionMs: Long, duration: Long): VideoWatchState {
    val progress = if (duration > 0) (lastPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    return when {
        progress == 0f -> VideoWatchState.Unplayed
        progress > 0.95f -> VideoWatchState.Completed
        else -> VideoWatchState.InProgress
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    onVideoSelected: (Video, List<Video>, Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit = {},
    onNavigateToSearch: (String) -> Unit = {},
    viewModel: VideoListViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadVideos()
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            permissionLauncher.launch(permission)
        } else {
            viewModel.loadVideos()
        }
    }

    val videosByFolder by viewModel.videosByFolder.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val viewSettings by viewModel.viewSettings.collectAsState()
    val explorerNodes by viewModel.explorerNodes.collectAsState()
    val currentExplorerPath by viewModel.currentExplorerPath.collectAsState()

    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }

    var showSettingsSheet by remember { mutableStateOf(false) }

    //  SELECTION STATE 
    var selectedFolders by remember { mutableStateOf(emptySet<VideoFolder>()) }
    var selectedVideos by remember { mutableStateOf(emptySet<Video>()) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }

    val sortedFolderKeys = remember(videosByFolder, viewSettings.sortField, viewSettings.sortDirection) {
        val keys = videosByFolder.keys.toList()
        keys.applyFolderSort(videosByFolder, viewSettings.sortField, viewSettings.sortDirection)
    }
    val isSelectionActive = selectedFolders.isNotEmpty() || selectedVideos.isNotEmpty()

    // Hoisted scroll states - survive recomposition and view-mode toggling
    val folderListState = rememberLazyListState()
    val folderGridState = rememberLazyGridState()
    var currentFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val videoListState = rememberLazyListState()
    val videoGridState = rememberLazyGridState()

    // Reset video scroll position when entering a different folder
    LaunchedEffect(selectedFolder) {
        if (selectedFolder?.name != currentFolderId) {
            videoListState.scrollToItem(0)
            videoGridState.scrollToItem(0)
            currentFolderId = selectedFolder?.name
        }
    }

    //  Watch History 
    val homeViewModel: com.devson.nosvedplayer.viewmodel.HomeViewModel = viewModel()
    val history by homeViewModel.history.collectAsState()
    val historyMap = remember(history) { history.associateBy { it.uri } }

    //  File Operations 
    val fileOpsViewModel: FileOperationsViewModel = viewModel()

    // Handles MediaStore permission dialogs (delete/rename on API 29-30)
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            fileOpsViewModel.onPermissionGranted(context)
        }
        fileOpsViewModel.clearPendingIntentSender()
    }

    //  Storage Explorer State 
    var storageExplorerOp by remember { mutableStateOf<String?>(null) }
    var storageExplorerUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    if (storageExplorerOp != null) {
        BackHandler { storageExplorerOp = null }
        StorageExplorerScreen(
            operationType = storageExplorerOp!!,
            sourceUris = storageExplorerUris,
            onComplete = {
                storageExplorerOp = null
                selectedFolders = emptySet()
                selectedVideos = emptySet()
            },
            onCancel = {
                storageExplorerOp = null
            }
        )
        return
    }

    //  Dialog state 
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    //  State change callbacks → MainScreen 
    //  Watch pendingIntentSender and launch it automatically 
    val pendingIntentSender by fileOpsViewModel.pendingIntentSender.collectAsState()
    LaunchedEffect(pendingIntentSender) {
        pendingIntentSender?.let { sender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    //  File operation result → Toast + list reload 
    val opResult by fileOpsViewModel.operationResult.collectAsState()
    LaunchedEffect(opResult) {
        opResult?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.loadVideos()
            fileOpsViewModel.clearResult()
        }
    }

    val needsRefresh by fileOpsViewModel.needsRefresh.collectAsState()
    LaunchedEffect(needsRefresh) {
        if (needsRefresh) {
            viewModel.loadVideos(forceRefresh = true)
            fileOpsViewModel.onRefreshHandled()
        }
    }

    // Drives progress bar visibility
    val opInProgress by fileOpsViewModel.operationInProgress.collectAsState()

    // Back handler: clears selection first before navigating out
    BackHandler(enabled = selectedFolder != null || isSelectionActive || (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null)) {
        when {
            selectedVideos.isNotEmpty() -> selectedVideos = emptySet()
            selectedFolders.isNotEmpty() -> selectedFolders = emptySet()
            selectedFolder != null -> viewModel.selectFolder(null)
            viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null -> viewModel.navigateExplorerUp()
            else -> {}
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val titleText = when (viewSettings.viewMode) {
                ViewMode.ALL_FOLDERS -> selectedFolder?.name
                ViewMode.FILES -> "All Files"
                ViewMode.FOLDERS -> currentExplorerPath?.substringBeforeLast('/')?.substringAfterLast('/') ?: "Folders"
            }
            VideoListTopAppBar(
                isSelectionActive = isSelectionActive,
                titleText = titleText,
                selectedCount = selectedVideos.size + selectedFolders.size,
                totalCount = when (viewSettings.viewMode) {
                    ViewMode.ALL_FOLDERS -> if (selectedFolder != null) (videosByFolder[selectedFolder] ?: emptyList()).size else sortedFolderKeys.size
                    ViewMode.FILES -> videosByFolder.values.flatten().size
                    ViewMode.FOLDERS -> explorerNodes.first.size + explorerNodes.second.size
                },
                showBackButton = selectedFolder != null || (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null),
                onClearSelection = { 
                    selectedFolders = emptySet()
                    selectedVideos = emptySet()
                },
                onSelectAll = {
                    when (viewSettings.viewMode) {
                        ViewMode.ALL_FOLDERS -> {
                            if (selectedFolder != null) {
                                val allVideos = videosByFolder[selectedFolder] ?: emptyList()
                                selectedVideos = if (selectedVideos.size == allVideos.size) emptySet() else allVideos.toSet()
                            } else {
                                selectedFolders = if (selectedFolders.size == sortedFolderKeys.size) emptySet() else sortedFolderKeys.toSet()
                            }
                        }
                        ViewMode.FILES -> {
                            val allVideos = videosByFolder.values.flatten()
                            selectedVideos = if (selectedVideos.size == allVideos.size) emptySet() else allVideos.toSet()
                        }
                        ViewMode.FOLDERS -> {
                            val allExpVideos = explorerNodes.second
                            val allExpFolders = explorerNodes.first
                            if (selectedVideos.size + selectedFolders.size == allExpVideos.size + allExpFolders.size) {
                                selectedVideos = emptySet()
                                selectedFolders = emptySet()
                            } else {
                                selectedVideos = allExpVideos.toSet()
                                selectedFolders = allExpFolders.toSet()
                            }
                        }
                    }
                },
                onBack = onBack,
                onNavigateToSettings = onNavigateToSettings,
                onShowSettings = { showSettingsSheet = true },
                onSearch = onNavigateToSearch,
                searchActive = searchActive,
                searchText = searchText,
                onSearchActiveChange = { active ->
                    searchActive = active
                    if (!active) { searchText = ""; viewModel.clearSearch() }
                },
                onSearchTextChange = { text ->
                    searchText = text
                    viewModel.onSearchQueryChanged(text)
                },
                searchSuggestions = searchSuggestions,
                searchFocusRequester = searchFocusRequester,
                keyboard = keyboard,
                onBackToFolders = { 
                    if (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null) {
                        viewModel.navigateExplorerUp()
                    } else {
                        viewModel.selectFolder(null)
                    }
                }
            )
        },
        bottomBar = {
            if (isSelectionActive) {
                // Unified URI computation across all view modes
                val allVideosFlat = remember(videosByFolder) { videosByFolder.values.flatten() }
                val selectedUris: List<Uri> = remember(
                    viewSettings.viewMode, selectedVideos, selectedFolders, selectedFolder, videosByFolder
                ) {
                    when (viewSettings.viewMode) {
                        ViewMode.FILES -> {
                            selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                        }
                        ViewMode.ALL_FOLDERS -> {
                            if (selectedFolder != null) {
                                // Inside a folder: operate on selected individual videos
                                selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                            } else {
                                // Folder-list view: map every video inside selected folders
                                selectedFolders
                                    .flatMap { folder -> videosByFolder[folder] ?: emptyList() }
                                    .mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                            }
                        }
                        ViewMode.FOLDERS -> {
                            // Combine standalone selected videos AND all videos inside selected dirs
                            val fromFolders = selectedFolders
                                .flatMap { f -> allVideosFlat.filter { it.path.startsWith(f.id) } }
                            (selectedVideos.toList() + fromFolders)
                                .distinctBy { it.uri }
                                .mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                        }
                    }
                }

                // In ALL_FOLDERS folder-list view, keep the original SelectionBottomAppBar
                // for its folder-centric features (Play All folder, Rename folder, etc.)
                val useFolderBar = viewSettings.viewMode == ViewMode.ALL_FOLDERS
                    && selectedFolder == null
                    && selectedFolders.isNotEmpty()

                if (useFolderBar) {
                    SelectionBottomAppBar(
                        selectedFolders = selectedFolders,
                        videosByFolder = videosByFolder,
                        viewSettings = viewSettings,
                        onVideoSelected = { video, playlist ->
                            onVideoSelected(video, playlist, historyMap[video.uri]?.lastPositionMs ?: 0L)
                        },
                        onClearSelection = { selectedFolders = emptySet() },
                        onMove = {
                            if (selectedUris.isNotEmpty()) {
                                storageExplorerUris = selectedUris
                                storageExplorerOp = "MOVE"
                            }
                        },
                        onCopy = {
                            if (selectedUris.isNotEmpty()) {
                                storageExplorerUris = selectedUris
                                storageExplorerOp = "COPY"
                            }
                        },
                        onDelete = {
                            if (selectedUris.isNotEmpty()) {
                                showDeleteConfirmation = true
                            }
                        },
                        onRename = {
                            val folder = selectedFolders.first()
                            renameInputText = folder.name
                            showRenameDialog = true
                        },
                        onShare = {
                            val videos = selectedFolders.flatMap { videosByFolder[it] ?: emptyList() }
                            shareVideos(context, videos)
                            selectedFolders = emptySet()
                            selectedVideos = emptySet()
                        },
                        onShowInfo = { showInfoBottomSheet = true },
                        onMarkStatus = { status ->
                            selectedFolders.flatMap { videosByFolder[it] ?: emptyList() }.forEach { video ->
                                val position = when(status) {
                                    "NEW" -> 0L
                                    "RUNNING" -> video.duration / 2
                                    "ENDED" -> video.duration
                                    else -> 0L
                                }
                                homeViewModel.setWatchStatus(video, position)
                            }
                            selectedFolders = emptySet()
                            selectedVideos = emptySet()
                        }
                    )
                } else {
                    // FILES, FOLDERS mode, or ALL_FOLDERS inside a specific folder:
                    // use the video-centric bar driven by the unified selectedUris list
                    VideoSelectionBottomAppBar(
                        selectedVideos = selectedVideos,
                        onPlayAll = {
                            val playVideo = selectedVideos.firstOrNull()
                            if (playVideo != null) {
                                val playlist = when (viewSettings.viewMode) {
                                    ViewMode.FILES -> videosByFolder.values.flatten().applySort(viewSettings.sortField, viewSettings.sortDirection)
                                    ViewMode.ALL_FOLDERS -> (videosByFolder[selectedFolder] ?: emptyList()).applySort(viewSettings.sortField, viewSettings.sortDirection)
                                    ViewMode.FOLDERS -> explorerNodes.second.applySort(viewSettings.sortField, viewSettings.sortDirection)
                                }
                                onVideoSelected(playVideo, playlist, historyMap[playVideo.uri]?.lastPositionMs ?: 0L)
                                selectedVideos = emptySet()
                            }
                        },
                        onMove = {
                            if (selectedUris.isNotEmpty()) {
                                storageExplorerUris = selectedUris
                                storageExplorerOp = "MOVE"
                            }
                        },
                        onCopy = {
                            if (selectedUris.isNotEmpty()) {
                                storageExplorerUris = selectedUris
                                storageExplorerOp = "COPY"
                            }
                        },
                        onDelete = {
                            if (selectedUris.isNotEmpty()) {
                                showDeleteConfirmation = true
                            }
                        },
                        onRename = {
                            val video = selectedVideos.firstOrNull()
                            if (video != null) {
                                renameInputText = video.title.substringBeforeLast(".")
                                showRenameDialog = true
                            }
                        },
                        onShare = {
                            shareVideos(context, selectedVideos.toList())
                            selectedVideos = emptySet()
                            selectedFolders = emptySet()
                        },
                        onShowInfo = { showInfoBottomSheet = true },
                        onMarkStatus = { status ->
                            selectedVideos.forEach { video ->
                                val position = when(status) {
                                    "NEW" -> 0L
                                    "RUNNING" -> video.duration / 2
                                    "ENDED" -> video.duration
                                    else -> 0L
                                }
                                homeViewModel.setWatchStatus(video, position)
                            }
                            selectedVideos = emptySet()
                            selectedFolders = emptySet()
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (viewSettings.showFloatingButton && !isSelectionActive) {
                val allVideosFlat = remember(videosByFolder) { videosByFolder.values.flatten() }

                val lastPlayedVideo = remember(history, selectedFolder, viewSettings.viewMode, currentExplorerPath, allVideosFlat) {
                    if (viewSettings.viewMode == ViewMode.ALL_FOLDERS && selectedFolder != null) {
                        val folderVideos = videosByFolder[selectedFolder] ?: emptyList()
                        val folderUris = folderVideos.map { it.uri }.toSet()
                        val lastHistory = history.firstOrNull { it.uri in folderUris }
                        if (lastHistory != null) folderVideos.find { it.uri == lastHistory.uri } else null
                    } else if (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null) {
                        val pathVideos = allVideosFlat.filter { it.path.startsWith(currentExplorerPath!!) }
                        val pathUris = pathVideos.map { it.uri }.toSet()
                        val lastHistory = history.firstOrNull { it.uri in pathUris }
                        if (lastHistory != null) pathVideos.find { it.uri == lastHistory.uri } else null
                    } else {
                        val lastHistory = history.firstOrNull()
                        if (lastHistory != null) allVideosFlat.find { it.uri == lastHistory.uri } else null
                    }
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
                            val playlist = when (viewSettings.viewMode) {
                                ViewMode.FILES -> allVideosFlat.applySort(viewSettings.sortField, viewSettings.sortDirection)
                                ViewMode.ALL_FOLDERS -> if (selectedFolder != null) {
                                    (videosByFolder[selectedFolder] ?: emptyList()).applySort(viewSettings.sortField, viewSettings.sortDirection)
                                } else {
                                    allVideosFlat.applySort(viewSettings.sortField, viewSettings.sortDirection)
                                }
                                ViewMode.FOLDERS -> if (currentExplorerPath != null) {
                                    allVideosFlat.filter { it.path.startsWith(currentExplorerPath!!) }.applySort(viewSettings.sortField, viewSettings.sortDirection)
                                } else {
                                    allVideosFlat.applySort(viewSettings.sortField, viewSettings.sortDirection)
                                }
                            }
                            onVideoSelected(lastPlayedVideo, playlist, lastHistoryEntry?.lastPositionMs ?: 0L)
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Progress bar shown at top while a file operation is running
            if (opInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding())
                        .align(Alignment.TopCenter)
                )
            }

            if (!hasPermission) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Storage permission is required to find videos.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_VIDEO
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        permissionLauncher.launch(permission)
                    }) {
                        Text("Grant Permission")
                    }
                }
            } else if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.loadVideos(forceRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (viewSettings.viewMode) {
                        ViewMode.ALL_FOLDERS -> {
                            if (selectedFolder == null) {
                                FolderListContent(
                                folders = videosByFolder,
                                settings = viewSettings,
                                selectedFolders = selectedFolders,
                                historyMap = historyMap,
                                onFolderClick = { folder ->
                                    if (isSelectionActive) {
                                        selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
                                    } else {
                                        viewModel.selectFolder(folder)
                                    }
                                },
                                onFolderLongClick = { folder ->
                                    selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
                                },
                                listState = folderListState,
                                gridState = folderGridState,
                                contentPadding = padding
                            )
                        } else {
                            val videos = videosByFolder[selectedFolder] ?: emptyList()
                            val sortedVideos = remember(videos, viewSettings.sortField, viewSettings.sortDirection) {
                                videos.applySort(viewSettings.sortField, viewSettings.sortDirection)
                            }
                            VideoListContent(
                                videos = sortedVideos,
                                settings = viewSettings,
                                selectedVideos = selectedVideos,
                                onVideoClick = { video ->
                                    if (isSelectionActive) {
                                        selectedVideos = if (video in selectedVideos) selectedVideos - video else selectedVideos + video
                                    } else {
                                        onVideoSelected(video, sortedVideos, historyMap[video.uri]?.lastPositionMs ?: 0L)
                                    }
                                },
                                onVideoLongClick = { video ->
                                    selectedVideos = if (video in selectedVideos) selectedVideos - video else selectedVideos + video
                                },
                                listState = videoListState,
                                gridState = videoGridState,
                                historyMap = historyMap,
                                contentPadding = padding
                            )
                        }
                    }
                    ViewMode.FILES -> {
                        val allVideos = remember(videosByFolder) { videosByFolder.values.flatten() }
                        val sortedVideos = remember(allVideos, viewSettings.sortField, viewSettings.sortDirection) {
                            allVideos.applySort(viewSettings.sortField, viewSettings.sortDirection)
                        }
                        VideoListContent(
                            videos = sortedVideos,
                            settings = viewSettings,
                            selectedVideos = selectedVideos,
                            onVideoClick = { video ->
                                if (isSelectionActive) {
                                    selectedVideos = if (video in selectedVideos) selectedVideos - video else selectedVideos + video
                                } else {
                                    onVideoSelected(video, sortedVideos, historyMap[video.uri]?.lastPositionMs ?: 0L)
                                }
                            },
                            onVideoLongClick = { video ->
                                selectedVideos = if (video in selectedVideos) selectedVideos - video else selectedVideos + video
                            },
                            listState = videoListState,
                            gridState = videoGridState,
                            historyMap = historyMap,
                            contentPadding = padding
                        )
                    }
                    ViewMode.FOLDERS -> {
                        val (expFolders, expVideos) = explorerNodes
                        val sortedExpVideos = remember(expVideos, viewSettings.sortField, viewSettings.sortDirection) {
                            expVideos.applySort(viewSettings.sortField, viewSettings.sortDirection)
                        }
                        // Need a map to resolve explorer nodes content
                        val mappedVideosByFolder = remember(videosByFolder, expFolders) {
                            val allVideos = videosByFolder.values.flatten()
                            expFolders.associateWith { folder ->
                                allVideos.filter { it.path.startsWith(folder.id) }
                            }
                        }
                        val sortedExpFolders = remember(expFolders, viewSettings.sortField, viewSettings.sortDirection, mappedVideosByFolder) {
                            expFolders.applyFolderSort(mappedVideosByFolder, viewSettings.sortField, viewSettings.sortDirection)
                        }
                        val allVideosForSize = remember(videosByFolder) { videosByFolder.values.flatten() }

                        ExplorerListContent(
                            folders = sortedExpFolders,
                            videos = sortedExpVideos,
                            allVideosForSize = allVideosForSize,
                            settings = viewSettings,
                            selectedFolders = selectedFolders,
                            selectedVideos = selectedVideos,
                            historyMap = historyMap,
                            onFolderClick = { folder ->
                                if (isSelectionActive) {
                                    selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
                                } else {
                                    viewModel.navigateToExplorerPath(folder.id)
                                }
                            },
                            onFolderLongClick = { folder ->
                                selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
                            },
                            onVideoClick = { video ->
                                if (isSelectionActive) {
                                    selectedVideos = if (video in selectedVideos) selectedVideos - video else selectedVideos + video
                                } else {
                                    onVideoSelected(video, sortedExpVideos, historyMap[video.uri]?.lastPositionMs ?: 0L)
                                }
                            },
                            onVideoLongClick = { video ->
                                selectedVideos = if (video in selectedVideos) selectedVideos - video else selectedVideos + video
                            },
                            listState = folderListState,
                            gridState = folderGridState,
                            contentPadding = padding
                        )
                    }
                }
                }
            }
        }
    }

    // ---- INFORMATION BOTTOM SHEET ----
    if (showInfoBottomSheet && isSelectionActive) {
        val videosToShow = when (viewSettings.viewMode) {
            ViewMode.FILES -> selectedVideos
            ViewMode.ALL_FOLDERS -> {
                if (selectedFolder != null) {
                    selectedVideos
                } else {
                    selectedFolders.flatMap { videosByFolder[it] ?: emptyList() }.toSet()
                }
            }
            ViewMode.FOLDERS -> {
                val allVideosFlat = videosByFolder.values.flatten()
                val fromFolders = selectedFolders.flatMap { f -> 
                    allVideosFlat.filter { it.path.startsWith(f.id) } 
                }
                (selectedVideos + fromFolders).toSet()
            }
        }
        InformationBottomSheet(
            selectedVideos = videosToShow,
            onDismiss = { showInfoBottomSheet = false }
        )
    }

    // ---- RENAME DIALOG ----
    if (showRenameDialog && (selectedFolders.size == 1 || selectedVideos.size == 1)) {
        val isFolder = selectedFolders.size == 1 && selectedFolder == null
        val title = if (isFolder) "Rename Folder" else "Rename Video"
        
        CustomRenameDialog(
            initialName = renameInputText,
            title = title,
            onConfirm = { newName ->
                if (isFolder) {
                    val folder = selectedFolders.first()
                    val folderPath = if (folder.id.startsWith("/")) {
                        folder.id
                    } else {
                        (videosByFolder[folder] ?: emptyList()).firstOrNull()?.path?.substringBeforeLast("/")
                    }
                    if (folderPath != null) {
                        fileOpsViewModel.renameFolder(context, folderPath, newName)
                    } else {
                        Toast.makeText(context, "Could not determine folder path.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val video = if (selectedFolder != null) selectedVideos.firstOrNull() 
                               else (videosByFolder[selectedFolders.first()] ?: emptyList()).firstOrNull()
                    if (video != null) {
                        fileOpsViewModel.renameVideo(context, Uri.parse(video.uri), newName)
                    }
                }
                showRenameDialog = false
                selectedFolders = emptySet()
                selectedVideos = emptySet()
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showSettingsSheet) {
        ViewSettingsBottomSheet(
            settings = viewSettings,
            isFolderView = selectedFolder == null,
            onDismiss = { showSettingsSheet = false },
            viewModel = viewModel
        )
    }

    if (showDeleteConfirmation) {
        val isFolder = viewSettings.viewMode == ViewMode.ALL_FOLDERS && selectedFolder == null && selectedFolders.isNotEmpty()
        val selectedUrisForDelete: List<Uri> = when (viewSettings.viewMode) {
            ViewMode.FILES -> selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
            ViewMode.ALL_FOLDERS -> {
                if (selectedFolder != null) {
                    selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                } else {
                    selectedFolders.flatMap { folder -> videosByFolder[folder] ?: emptyList() }
                        .mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                }
            }
            ViewMode.FOLDERS -> {
                val allVideosFlat = videosByFolder.values.flatten()
                val fromFolders = selectedFolders.flatMap { f -> allVideosFlat.filter { it.path.startsWith(f.id) } }
                (selectedVideos.toList() + fromFolders).distinctBy { it.uri }
                    .mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
            }
        }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Video(s)") },
            text = { Text("Choose how you want to delete the selected video(s).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileOpsViewModel.deleteVideos(context, selectedUrisForDelete, trash = true)
                        selectedFolders = emptySet()
                        selectedVideos = emptySet()
                        showDeleteConfirmation = false
                    }
                ) { Text("Move to Recycle Bin") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            fileOpsViewModel.deleteVideos(context, selectedUrisForDelete, trash = false)
                            selectedFolders = emptySet()
                            selectedVideos = emptySet()
                            showDeleteConfirmation = false
                        }
                    ) { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }
}

// VIDEO LIST CONTENT 
@Composable
fun VideoListContent(
    videos: List<Video>,
    settings: ViewSettings,
    selectedVideos: Set<Video>,
    historyMap: Map<String, com.devson.nosvedplayer.model.WatchHistory> = emptyMap(),
    onVideoClick: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (videos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CustomEmptyStateView(
                heading  = "No Videos Here",
                subtext  = "This folder appears to be empty. Try pulling down to refresh.",
                ctaLabel = "Scan Device for Videos"
            )
        }
        return
    }
    if (settings.layoutMode == LayoutMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.gridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(videos) { video ->
                VideoGridItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    lastPositionMs = historyMap[video.uri]?.lastPositionMs ?: 0L,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) }
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            )
        ) {
            items(videos) { video ->
                VideoListItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    lastPositionMs = historyMap[video.uri]?.lastPositionMs ?: 0L,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) }
                )
            }
        }
    }
}

// VIDEO THUMBNAIL

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
private fun ThumbnailSelectionOverlay(isSelected: Boolean, isDense: Boolean = false) {
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
private fun BoxScope.DurationBadge(duration: Long, isGrid: Boolean = false) {
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

//  SHARED: Watch-progress bar at the bottom of the thumbnail
 
@Composable
private fun BoxScope.WatchProgressBar(lastPositionMs: Long, duration: Long) {
    if (lastPositionMs > 0L && duration > 0L) {
        val progress = (lastPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress         = { progress },
            modifier         = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp),
            color            = MaterialTheme.colorScheme.primary,
            trackColor       = Color.Transparent,
            drawStopIndicator = {}
        )
    }
}

@Composable
fun WatchStateBadge(state: VideoWatchState, isLarge: Boolean = false) {
    val (label, bgColor, textColor) = when (state) {
        is VideoWatchState.Unplayed  -> Triple("New",     MaterialTheme.colorScheme.primary,                          MaterialTheme.colorScheme.onPrimary)
        is VideoWatchState.InProgress -> Triple("Running", MaterialTheme.colorScheme.tertiary,                         MaterialTheme.colorScheme.onTertiary)
        is VideoWatchState.Completed  -> Triple("Ended",   MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f), MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val fontSize = if (isLarge) 11.sp else 9.sp
    val horizontalPadding = if (isLarge) 7.dp else 5.dp
    val verticalPadding = if (isLarge) 3.dp else 2.dp
    val cornerRadius = if (isLarge) 6.dp else 5.dp
    val outerPadding = if (isLarge) 8.dp else 6.dp

    Box(
        modifier = Modifier
            .padding(outerPadding)
            .background(color = bgColor, shape = RoundedCornerShape(cornerRadius))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Text(
            text       = label,
            color      = textColor,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            fontSize   = fontSize
        )
    }
}

// VIDEO LIST ITEM 

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: Video,
    settings: ViewSettings,
    isSelected: Boolean = false,
    lastPositionMs: Long = 0L,
    onClick: (Video) -> Unit,
    onLongClick: (Video) -> Unit
) {
    val haptic = LocalHapticFeedback.current
 
    // Smooth background colour transition on select
    val bgColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "listItemBg"
    )
    val borderColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        animationSpec = tween(180),
        label = "listItemBorder"
    )
 
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .combinedClickable(
                onClick    = { onClick(video) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(video)
                }
            ),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation  = if (isSelected) 0.dp else 1.dp,
            pressedElevation  = 0.dp
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            //  Thumbnail 
            val watchState = getWatchState(lastPositionMs, video.duration)
            Card(
                modifier = Modifier
                    .size(width = 100.dp, height = 60.dp)
                    .then(if (settings.selectByThumbnail) Modifier.clickable { onLongClick(video) } else Modifier)
                    .then(if (watchState is VideoWatchState.Completed) Modifier.alpha(0.6f) else Modifier),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (watchState is VideoWatchState.InProgress)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    else
                        Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                if (settings.showThumbnail) {
                    VideoThumbnail(
                        uri         = video.uri,
                        modifier    = Modifier.fillMaxSize(),
                        showPlayIcon = !isSelected
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }

                // Watch state badge (top-left): NEW / Running / Ended
                if (!isSelected) {
                    WatchStateBadge(watchState, isLarge = false)
                }

                // Duration badge (shown only when displayLengthOverThumbnail is true)
                if (settings.showLength && settings.displayLengthOverThumbnail && !isSelected) {
                    DurationBadge(video.duration, isGrid = false)
                }
 
                // Watch-progress bar
                WatchProgressBar(lastPositionMs, video.duration)
 
                // Selection overlay (animated)
                ThumbnailSelectionOverlay(isSelected, isDense = true)
            }
            }
 
            Spacer(modifier = Modifier.width(14.dp))
 
            //  Text section 
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (settings.showFileExtension) video.title
                           else video.title.substringBeforeLast("."),
                    style     = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines  = 2,
                    overflow  = TextOverflow.Ellipsis,
                    color     = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else if (watchState is VideoWatchState.Completed)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
 
                Spacer(modifier = Modifier.height(5.dp))
 
                VideoMetadataChips(video, settings, lastPositionMs)
            }
        }
    }
}

//  VIDEO GRID ITEM  (replace your existing VideoGridItem) 
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridItem(
    video: Video,
    settings: ViewSettings,
    isSelected: Boolean = false,
    lastPositionMs: Long = 0L,
    onClick: (Video) -> Unit,
    onLongClick: (Video) -> Unit
) {
    val haptic  = LocalHapticFeedback.current
    val isDense = settings.gridColumns >= 3
 
    val bgColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "gridItemBg"
    )
    val borderColor by animateColorAsState(
        targetValue  = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        animationSpec = tween(180),
        label = "gridItemBorder"
    )
 
    // Single-column (full-width cinema card) 
    val watchState = getWatchState(lastPositionMs, video.duration)
    if (settings.gridColumns == 1) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .combinedClickable(
                    onClick    = { onClick(video) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick(video)
                    }
                ),
            shape     = RoundedCornerShape(18.dp),
            colors    = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
            border    = BorderStroke(if (isSelected) 1.5.dp else 0.dp, borderColor)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Wide thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .then(if (settings.selectByThumbnail) Modifier.clickable { onLongClick(video) } else Modifier)
                        .then(if (watchState is VideoWatchState.Completed) Modifier.alpha(0.6f) else Modifier)
                ) {
                    if (settings.showThumbnail) {
                        VideoThumbnail(uri = video.uri, modifier = Modifier.fillMaxSize(), showPlayIcon = !isSelected)
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(
                                if (watchState is VideoWatchState.InProgress)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Movie, null, Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        }
                    }
                    if (!isSelected) WatchStateBadge(watchState, isLarge = true)
                    if (settings.showLength && settings.displayLengthOverThumbnail && !isSelected)
                        DurationBadge(video.duration, isGrid = true)
                    WatchProgressBar(lastPositionMs, video.duration)
                    ThumbnailSelectionOverlay(isSelected)
                }
 
                // Info strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.showFileExtension) video.title
                                   else video.title.substringBeforeLast("."),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 2,
                            overflow   = TextOverflow.Ellipsis,
                            color      = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else if (watchState is VideoWatchState.Completed)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        VideoMetadataChips(video, settings, lastPositionMs)
                    }
                }
            }
        }
        return
    }
 
    // Multi-column compact card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (isDense) 1f else 0.82f)
            .combinedClickable(
                onClick    = { onClick(video) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(video)
                }
            ),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
        border    = BorderStroke(if (isSelected) 1.5.dp else 0.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thumbnail fills most of the card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(if (settings.selectByThumbnail) Modifier.clickable { onLongClick(video) } else Modifier)
                    .then(if (watchState is VideoWatchState.Completed) Modifier.alpha(0.6f) else Modifier)
            ) {
                if (settings.showThumbnail) {
                    VideoThumbnail(
                        uri          = video.uri,
                        modifier     = Modifier.fillMaxSize(),
                        showPlayIcon = !isSelected && !isDense
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            if (watchState is VideoWatchState.InProgress)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Movie, null, Modifier.size(if (isDense) 28.dp else 36.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                }

                if (!isSelected) WatchStateBadge(watchState, isLarge = settings.gridColumns <= 2)

                // Duration badge
                if (settings.showLength && settings.displayLengthOverThumbnail && !isSelected)
                    DurationBadge(video.duration, isGrid = true)
 
                // Watch-progress bar
                WatchProgressBar(lastPositionMs, video.duration)
 
                // Selection overlay
                ThumbnailSelectionOverlay(isSelected, isDense)
            }
 
            // Bottom label (hidden in dense ≥3 columns - too cramped)
            if (!isDense) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 10.dp)
                ) {
                    Text(
                        text = if (settings.showFileExtension) video.title
                               else video.title.substringBeforeLast("."),
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        color      = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else if (watchState is VideoWatchState.Completed)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    VideoMetadataChips(video, settings, lastPositionMs, isGrid = true)
                }
            }
        }
    }
}

// VIDEO METADATA ROW

@Composable
fun VideoMetadataRow(
    video: Video,
    settings: ViewSettings,
    isGrid: Boolean = false,
    lastPositionMs: Long = 0L
) {
    // Alias so callers that still use the old name keep working
    VideoMetadataChips(video, settings, lastPositionMs, isGrid)
}
 
@Composable
fun VideoMetadataChips(
    video: Video,
    settings: ViewSettings,
    lastPositionMs: Long = 0L,
    isGrid: Boolean = false
) {
    // Build ordered token list
    data class MetaToken(val text: String, val isPrimary: Boolean = false)
 
    val tokens = buildList {
        if (settings.showLength && !settings.displayLengthOverThumbnail)
            add(MetaToken(formatDuration(video.duration), isPrimary = true))
        if (settings.showPlayedTime && video.lastPlayedAt != null && video.lastPlayedAt > 0)
            add(MetaToken(formatRelativeTime(LocalContext.current, video.lastPlayedAt)))
        if (settings.showResolution && !video.resolution.isNullOrEmpty())
            add(MetaToken(formatResolutionCompact(video.resolution) ?: video.resolution))
        if (settings.showFrameRate && video.frameRate != null && video.frameRate > 0f)
            add(MetaToken("${video.frameRate.toInt()} fps"))
        if (settings.showFileExtension)
            add(MetaToken(video.title.substringAfterLast('.', video.uri.substringAfterLast('.', "")).uppercase()))
        if (settings.showSize)
            add(MetaToken(formatSize(video.size)))
        if (settings.showDate && video.dateAdded > 0)
            add(MetaToken(formatDate(video.dateAdded)))
        if (settings.showPath)
            add(MetaToken(video.path))
    }.filter { it.text.isNotBlank() }
 
    if (tokens.isEmpty()) return
 
    Row(
        modifier          = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tokens.take(if (isGrid) 2 else 4).forEach { token ->
            MetadataChip(text = token.text, isPrimary = token.isPrimary, isGrid = isGrid)
        }
    }
}
 
@Composable
private fun MetadataChip(text: String, isPrimary: Boolean, isGrid: Boolean = false) {
    val bgColor   = if (isPrimary)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
 
    val textColor = if (isPrimary)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant
 
    val fontSize = if (isGrid) 9.5.sp else 10.5.sp
 
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text       = text,
            style      = MaterialTheme.typography.labelSmall,
            fontSize   = fontSize,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
            color      = textColor,
            maxLines   = 1
        )
    }
}

// EXPLORER LIST CONTENT

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerListContent(
    folders: List<VideoFolder>,
    videos: List<Video>,
    allVideosForSize: List<Video>,
    settings: ViewSettings,
    selectedFolders: Set<VideoFolder>,
    selectedVideos: Set<Video>,
    historyMap: Map<String, com.devson.nosvedplayer.model.WatchHistory> = emptyMap(),
    onFolderClick: (VideoFolder) -> Unit,
    onFolderLongClick: (VideoFolder) -> Unit,
    onVideoClick: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    if (settings.layoutMode == LayoutMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.gridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(folders) { folder ->
                val folderVideos = remember(folder, allVideosForSize) { allVideosForSize.filter { it.path.startsWith(folder.id) } }
                FolderGridItem(
                    folder = folder,
                    videos = folderVideos,
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
                    }
                )
            }
            items(videos) { video ->
                VideoGridItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    lastPositionMs = historyMap[video.uri]?.lastPositionMs ?: 0L,
                    onClick = { onVideoClick(video) },
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onVideoLongClick(video)
                    }
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            )
        ) {
            items(folders) { folder ->
                val folderVideos = remember(folder, allVideosForSize) { allVideosForSize.filter { it.path.startsWith(folder.id) } }
                FolderListItem(
                    folder = folder,
                    videos = folderVideos,
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
                    }
                )
            }
            items(videos) { video ->
                VideoListItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    lastPositionMs = historyMap[video.uri]?.lastPositionMs ?: 0L,
                    onClick = { onVideoClick(video) },
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onVideoLongClick(video)
                    }
                )
            }
        }
    }
}

// Top App Bar Component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoListTopAppBar(
    isSelectionActive: Boolean,
    titleText: String?,
    selectedCount: Int,
    totalCount: Int,
    showBackButton: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowSettings: () -> Unit,
    onBackToFolders: () -> Unit,
    onSearch: (String) -> Unit = {},
    searchActive: Boolean = false,
    searchText: String = "",
    onSearchActiveChange: (Boolean) -> Unit = {},
    onSearchTextChange: (String) -> Unit = {},
    searchSuggestions: List<Video> = emptyList(),
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
) {
    if (isSelectionActive) {
        val allSelected = selectedCount == totalCount
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear Selection")
                }
            },
            title = {
                Text(
                    "$selectedCount / $totalCount selected",
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = if (allSelected) "Unselect All" else "Select All"
                    )
                }
            }
        )
    } else {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background
            ),
            title = {
                if (searchActive) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = onSearchTextChange,
                        placeholder = { Text("Search videos...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = {
                                keyboard?.hide()
                                if (searchText.isNotBlank()) {
                                    onSearchActiveChange(false)
                                    onSearch(searchText)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        )
                    )
                } else {
                    Text(
                        titleText ?: "Nosved Player",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBackToFolders) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                    }
                }
            },
            actions = {
                if (searchActive) {
                    IconButton(onClick = { onSearchActiveChange(false) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Search")
                    }
                } else {
                    IconButton(onClick = { onSearchActiveChange(true) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onShowSettings) {
                        Icon(imageVector = Icons.Filled.Tune, contentDescription = "View Settings")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            }
        )
        if (searchActive && searchSuggestions.isNotEmpty()) {
            SearchSuggestionsPopup(
                suggestions = searchSuggestions,
                keyboard = keyboard,
                onSuggestionClick = { title ->
                    onSearchActiveChange(false)
                    onSearch(title)
                }
            )
        }
    }
}

// VIDEO SELECTION BOTTOM APP BAR
@Composable
fun VideoSelectionBottomAppBar(
    selectedVideos: Set<Video>,
    onPlayAll: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShowInfo: () -> Unit,
    onShare: () -> Unit,
    onMarkStatus: (String) -> Unit
) {
    var showTagDialog by remember { mutableStateOf(false) }

    if (showTagDialog) {
        com.devson.nosvedplayer.util.TagStatusDialog(
            onDismiss = { showTagDialog = false },
            onConfirm = { status ->
                showTagDialog = false
                onMarkStatus(status)
            }
        )
    }

    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play All
            ActionColumn(
                icon = Icons.Filled.PlayCircle,
                label = "Play All",
                onClick = onPlayAll
            )
            // Move
            ActionColumn(icon = Icons.AutoMirrored.Filled.DriveFileMove, label = "Move", onClick = onMove)
            // Copy
            ActionColumn(icon = Icons.Filled.ContentCopy, label = "Copy", onClick = onCopy)
            // Delete
            ActionColumn(icon = Icons.Filled.Delete, label = "Delete", onClick = onDelete)
            // Rename
            if (selectedVideos.size == 1) {
                ActionColumn(
                    icon = Icons.Filled.DriveFileRenameOutline,
                    label = "Rename",
                    onClick = onRename
                )
            }
            // Share
            ActionColumn(icon = Icons.Filled.Share, label = "Share", onClick = onShare)
            // Info
            ActionColumn(icon = Icons.Filled.Info, label = "Info", onClick = onShowInfo)
            // Tagging
            ActionColumn(icon = Icons.AutoMirrored.Filled.Label, label = "Tag", onClick = { showTagDialog = true })
        }
    }
}

@Composable
private fun ActionColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label)
        Text(label, fontSize = 10.sp)
    }
}

// EXTENSIONS FOR SORTING FOLDERS
private fun List<VideoFolder>.applyFolderSort(
    folderMap: Map<VideoFolder, List<Video>>,
    field: SortField,
    direction: SortDirection
): List<VideoFolder> {
    val sorted = when (field) {
        SortField.TITLE -> sortedBy { it.name.lowercase() }
        SortField.DATE -> sortedBy { folder -> folderMap[folder]?.maxOfOrNull { it.dateAdded } ?: 0L }
        SortField.PLAYED_TIME -> sortedBy { folder -> folderMap[folder]?.maxOfOrNull { it.playedTime ?: 0L } ?: 0L }
        SortField.STATUS -> sortedBy { it.name.lowercase() }
        SortField.LENGTH -> sortedBy { folder -> folderMap[folder]?.sumOf { it.duration } ?: 0L }
        SortField.SIZE -> sortedBy { folder -> folderMap[folder]?.sumOf { it.size } ?: 0L }
        SortField.RESOLUTION -> sortedBy { it.name.lowercase() }
        SortField.PATH -> sortedBy { it.id.lowercase() }
        SortField.FRAME_RATE -> sortedBy { it.name.lowercase() }
        SortField.TYPE -> sortedBy { it.name.lowercase() }
    }
    return if (direction == SortDirection.DESCENDING) sorted.reversed() else sorted
}

fun shareVideos(context: android.content.Context, videos: List<com.devson.nosvedplayer.model.Video>) {
    if (videos.isEmpty()) return
    val uris = videos.map { android.net.Uri.parse(it.uri) }
    
    val intent = if (uris.size == 1) {
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, java.util.ArrayList(uris))
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    context.startActivity(android.content.Intent.createChooser(intent, "Share Video"))
}