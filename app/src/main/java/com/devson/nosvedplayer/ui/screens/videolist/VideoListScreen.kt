package com.devson.nosvedplayer.ui.screens.videolist

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewMode
import com.devson.nosvedplayer.model.WatchHistory
import com.devson.nosvedplayer.model.applySort
import com.devson.nosvedplayer.ui.components.CustomRenameDialog
import com.devson.nosvedplayer.ui.components.PreviewFloatingActionButton
import com.devson.nosvedplayer.ui.components.ViewSettingsBottomSheet
import com.devson.nosvedplayer.ui.screens.videolist.components.folder.FolderListContent
import com.devson.nosvedplayer.ui.screens.InformationBottomSheet
import com.devson.nosvedplayer.ui.screens.StorageExplorerScreen
import com.devson.nosvedplayer.ui.screens.videolist.components.topbar.VideoListTopAppBar
import com.devson.nosvedplayer.ui.screens.videolist.components.list.VideoListContent
import com.devson.nosvedplayer.ui.screens.videolist.components.explorer.ExplorerListContent
import com.devson.nosvedplayer.ui.screens.videolist.components.selection.VideoSelectionBottomBar
import com.devson.nosvedplayer.ui.screens.videolist.utils.applyFolderSort
import com.devson.nosvedplayer.ui.screens.videolist.utils.shareVideos
import com.devson.nosvedplayer.util.SelectionBottomAppBar
import com.devson.nosvedplayer.viewmodel.FileOperationsViewModel
import com.devson.nosvedplayer.viewmodel.HomeViewModel
import com.devson.nosvedplayer.viewmodel.VideoListViewModel

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
    val homeViewModel: HomeViewModel = viewModel()
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
                    VideoSelectionBottomBar(
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
                PullToRefreshBox(
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
                                            selectedFolders =
                                                if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
                                        } else {
                                            viewModel.selectFolder(folder)
                                        }
                                    },
                                    onFolderLongClick = { folder ->
                                        selectedFolders =
                                            if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
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
