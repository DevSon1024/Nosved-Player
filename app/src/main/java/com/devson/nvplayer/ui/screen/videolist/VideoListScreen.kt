package com.devson.nvplayer.ui.screen.videolist

import android.Manifest
import android.app.Activity
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devson.nvplayer.domain.model.DefaultScreen
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.domain.model.ViewMode
import com.devson.nvplayer.domain.model.applySort
import com.devson.nvplayer.ui.components.CustomRenameDialog
import com.devson.nvplayer.ui.common.components.PreviewFloatingActionButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devson.nvplayer.ui.common.sheets.ViewSettingsBottomSheet
import com.devson.nvplayer.ui.screen.videolist.components.folder.FolderListContent
import com.devson.nvplayer.ui.common.sheets.InformationBottomSheet
import com.devson.nvplayer.ui.screen.StorageExplorerScreen
import com.devson.nvplayer.ui.screen.NetworkStreamDialog
import com.devson.nvplayer.ui.screens.videolist.components.topbar.VideoListTopAppBar
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoListContent
import com.devson.nvplayer.ui.screens.videolist.components.explorer.ExplorerListContent
import com.devson.nvplayer.ui.screens.videolist.components.explorer.ExplorerPathStrip
import com.devson.nvplayer.ui.screens.videolist.components.selection.VideoSelectionBottomBar
import com.devson.nvplayer.ui.screens.videolist.utils.applyFolderSort
import com.devson.nvplayer.ui.screens.videolist.utils.shareVideos
import com.devson.nvplayer.ui.common.components.SelectionBottomAppBar
import com.devson.nvplayer.viewmodel.FileOperationsViewModel
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel
import com.devson.nvplayer.ui.screens.videolist.state.ExplorerItem
import kotlin.collections.get

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    onVideoSelected: (Video, List<Video>, Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit = {},
    onNavigateToSearch: (String) -> Unit = {},
    onNavigateToFeed: (Int) -> Unit = {},
    onPlayStream: (Uri) -> Unit = {},
    onNetworkHistoryClick: () -> Unit = {},
    viewModel: VideoListViewModel = viewModel(),
    homeViewModel: HomeViewModel
) {
    val context = LocalContext.current
    var isAnimationFinished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        isAnimationFinished = true
    }
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

    val videosByFolder by viewModel.videosByFolder.collectAsStateWithLifecycle()
    val videosFlat by viewModel.videosFlat.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val viewSettings by viewModel.viewSettings.collectAsStateWithLifecycle()
    val explorerItems by viewModel.explorerItems.collectAsStateWithLifecycle()
    val currentExplorerPath by viewModel.currentExplorerPath.collectAsStateWithLifecycle()
    val availableStorages by viewModel.availableStorages.collectAsStateWithLifecycle()
    val selectedStorage by viewModel.selectedStorage.collectAsStateWithLifecycle()

    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    var searchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var showNetworkDialog by remember { mutableStateOf(false) }
    var showYtdlpMissingDialog by remember { mutableStateOf(false) }

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
    val historyMap by homeViewModel.historyMapFlow.collectAsStateWithLifecycle()
    val lastPlayedVideo by viewModel.quickFabLastPlayedVideo.collectAsStateWithLifecycle()

    LaunchedEffect(historyMap) {
        viewModel.setHistoryMap(historyMap)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.loadWatchHistory()
                // Re-detect storage volumes on resume for SD card insertion/ejection
                viewModel.refreshStorages(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
    val pendingIntentSender by fileOpsViewModel.pendingIntentSender.collectAsStateWithLifecycle()
    LaunchedEffect(pendingIntentSender) {
        pendingIntentSender?.let { sender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    val showOverwriteDialog by fileOpsViewModel.showOverwriteDialog.collectAsStateWithLifecycle()

    //  File operation result → Toast + list reload 
    val opResult by fileOpsViewModel.operationResult.collectAsStateWithLifecycle()
    LaunchedEffect(opResult) {
        opResult?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.loadVideos()
            fileOpsViewModel.clearResult()
        }
    }

    val needsRefresh by fileOpsViewModel.needsRefresh.collectAsStateWithLifecycle()
    LaunchedEffect(needsRefresh) {
        if (needsRefresh) {
            viewModel.loadVideos(forceRefresh = true)
            fileOpsViewModel.onRefreshHandled()
        }
    }

    LaunchedEffect(Unit) {
        fileOpsViewModel.pendingDeletionsFlow.collect { uris ->
            if (uris.isNotEmpty()) {
                fileOpsViewModel.deleteVideos(context, uris, trash = false)
            }
        }
    }

    // Drives progress bar visibility
    val opInProgress by fileOpsViewModel.operationInProgress.collectAsStateWithLifecycle()

    // Derive the active base root dynamically from the selected storage (fallback to internal)
    val baseRoot = selectedStorage?.rootPath
        ?: android.os.Environment.getExternalStorageDirectory().absolutePath

    // Back handler: clears selection first before navigating out
    BackHandler(enabled = selectedFolder != null || isSelectionActive || (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != baseRoot)) {
        when {
            selectedVideos.isNotEmpty() -> selectedVideos = emptySet()
            selectedFolders.isNotEmpty() -> selectedFolders = emptySet()
            selectedFolder != null -> viewModel.selectFolder(null)
            viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != baseRoot -> viewModel.MapsUpInExplorer()
            else -> {}
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val titleText = when (viewSettings.viewMode) {
                ViewMode.ALL_FOLDERS -> selectedFolder?.name
                ViewMode.FILES -> "All Files"
                ViewMode.FOLDERS -> {
                    if (currentExplorerPath == baseRoot) selectedStorage?.name ?: "Internal Storage"
                    else currentExplorerPath.substringAfterLast('/')
                }
            }
            VideoListTopAppBar(
                isSelectionActive = isSelectionActive,
                titleText = titleText,
                selectedCount = selectedVideos.size + selectedFolders.size,
                totalCount = when (viewSettings.viewMode) {
                    ViewMode.ALL_FOLDERS -> if (selectedFolder != null) (videosByFolder[selectedFolder] ?: emptyList()).size else sortedFolderKeys.size
                    ViewMode.FILES -> videosFlat.size
                    ViewMode.FOLDERS -> explorerItems.size
                },
                showBackButton = selectedFolder != null || (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != baseRoot),
                showHomeBackButton = viewSettings.defaultScreen != DefaultScreen.VIDEO_LIST,
                onClearSelection = { 
                    selectedFolders = emptySet()
                    selectedVideos = emptySet()
                },
                onShowInfo = { showInfoBottomSheet = true },
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
                            val allVideos = videosFlat
                            selectedVideos = if (selectedVideos.size == allVideos.size) emptySet() else allVideos.toSet()
                        }
                        ViewMode.FOLDERS -> {
                            val allExpVideos = explorerItems.filterIsInstance<ExplorerItem.VideoItem>().map { it.video }
                            val allExpFolders = explorerItems.filterIsInstance<ExplorerItem.FolderItem>().map { it.folder }
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
                onNetworkStreamClick = { showNetworkDialog = true },
                onBackToFolders = {
                    if (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != baseRoot) {
                        viewModel.MapsUpInExplorer()
                    } else {
                        viewModel.selectFolder(null)
                    }
                },
                onPlayFolder = if ((viewSettings.viewMode == ViewMode.ALL_FOLDERS && selectedFolder != null) ||
                                  (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != baseRoot)) {
                    {
                        val folderVideos = when (viewSettings.viewMode) {
                            ViewMode.ALL_FOLDERS -> videosByFolder[selectedFolder] ?: emptyList()
                            ViewMode.FOLDERS -> explorerItems.filterIsInstance<ExplorerItem.VideoItem>().map { it.video }
                            else -> emptyList()
                        }
                        val sortedVideos = folderVideos.applySort(viewSettings.sortField, viewSettings.sortDirection)
                        viewModel.setFeedVideos(sortedVideos)
                        onNavigateToFeed(0)
                    }
                } else null,
                availableStorages = availableStorages,
                selectedStorage = selectedStorage,
                onStorageSelected = { storage -> viewModel.onStorageSelected(storage) }
            )
        },
        bottomBar = {
            if (isSelectionActive) {
                // Unified URI computation across all view modes
                val allVideosFlat = videosFlat
                val selectedUris: List<Uri> = remember(
                    viewSettings.viewMode, selectedVideos, selectedFolders, selectedFolder, videosFlat
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
                            val folderIds = selectedFolders.map { it.id }
                            val fromFolders = allVideosFlat.filter { video ->
                                folderIds.any { id -> video.path.startsWith(id) }
                            }
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
                        onFeedPlay = { videos ->
                            val sorted = videos.applySort(viewSettings.sortField, viewSettings.sortDirection)
                            viewModel.setFeedVideos(sorted)
                            onNavigateToFeed(0)
                            selectedFolders = emptySet()
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
                        },
                        showTagAndShare = true
                    )
                } else {
                    // FILES, FOLDERS mode, or ALL_FOLDERS inside a specific folder:
                    // use the video-centric bar driven by the unified selectedUris list
                    VideoSelectionBottomBar(
                        selectedVideos = selectedVideos,
                        onFeedPlay = {
                            // Determine the full current playlist depending on view mode
                            val fullPlaylist: List<Video> = when (viewSettings.viewMode) {
                                ViewMode.FILES -> {
                                    val allVideos = videosFlat
                                    allVideos.applySort(viewSettings.sortField, viewSettings.sortDirection)
                                }
                                ViewMode.ALL_FOLDERS -> {
                                    val folderVideos = videosByFolder[selectedFolder] ?: videosFlat
                                    folderVideos.applySort(viewSettings.sortField, viewSettings.sortDirection)
                                }
                                ViewMode.FOLDERS -> {
                                    explorerItems.filterIsInstance<ExplorerItem.VideoItem>().map { it.video }
                                        .applySort(viewSettings.sortField, viewSettings.sortDirection)
                                }
                            }
                            // Find start index: the first selected video in the full sorted playlist
                            val firstSelected = selectedVideos.firstOrNull()
                            val startIdx = if (firstSelected != null) {
                                fullPlaylist.indexOfFirst { it.uri == firstSelected.uri }.takeIf { it >= 0 } ?: 0
                            } else 0
                            if (fullPlaylist.isNotEmpty()) {
                                viewModel.setFeedVideos(fullPlaylist)
                                onNavigateToFeed(startIdx)
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
                        },
                        showTagAndShare = true
                    )
                }
            }
        },
        floatingActionButton = {
            if (viewSettings.showQuickFab && !isSelectionActive) {
                val video = lastPlayedVideo
                val lastHistoryEntry = remember(video, historyMap) { video?.let { historyMap[it.uri] } }
                val allVideosFlat = videosFlat
                PreviewFloatingActionButton(
                    enablePreview = viewSettings.enableFabPreview && video != null,
                    previewUri = video?.uri,
                    previewTitle = video?.title,
                    previewDurationMs = video?.duration ?: 0L,
                    previewLastPositionMs = lastHistoryEntry?.lastPositionMs ?: 0L,
                    onPlay = {
                        video?.let { vid ->
                            val playlist = when (viewSettings.viewMode) {
                                ViewMode.FILES -> allVideosFlat.applySort(viewSettings.sortField, viewSettings.sortDirection)
                                ViewMode.ALL_FOLDERS -> if (selectedFolder != null) {
                                    (videosByFolder[selectedFolder] ?: emptyList()).applySort(viewSettings.sortField, viewSettings.sortDirection)
                                } else {
                                    allVideosFlat.applySort(viewSettings.sortField, viewSettings.sortDirection)
                                }
                                ViewMode.FOLDERS -> {
                                    allVideosFlat.filter { it.path.startsWith(currentExplorerPath) }.applySort(viewSettings.sortField, viewSettings.sortDirection)
                                }
                            }
                            onVideoSelected(vid, playlist, lastHistoryEntry?.lastPositionMs ?: 0L)
                        }
                    },
                    onNetworkStreamClick = { showNetworkDialog = true }
                )
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
            } else if (isLoading || !isAnimationFinished) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.loadVideos(forceRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (viewSettings.viewMode) {
                        ViewMode.ALL_FOLDERS -> {
                            AnimatedContent(
                                targetState = selectedFolder,
                                transitionSpec = {
                                    if (initialState == null && targetState != null) {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                                        ) togetherWith slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                                        )
                                    } else if (initialState != null && targetState == null) {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                                        ) togetherWith slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                                        )
                                    } else {
                                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                                    }
                                },
                                label = "FolderTransition",
                                modifier = Modifier.fillMaxSize()
                            ) { folder ->
                                if (folder == null) {
                                    FolderListContent(
                                        folders = videosByFolder,
                                        settings = viewSettings,
                                        selectedFolders = selectedFolders,
                                        historyMap = historyMap,
                                        onFolderClick = { folderItem ->
                                            if (isSelectionActive) {
                                                selectedFolders =
                                                    if (folderItem in selectedFolders) selectedFolders - folderItem else selectedFolders + folderItem
                                            } else {
                                                viewModel.selectFolder(folderItem)
                                            }
                                        },
                                        onFolderLongClick = { folderItem ->
                                            selectedFolders =
                                                if (folderItem in selectedFolders) selectedFolders - folderItem else selectedFolders + folderItem
                                        },
                                        listState = folderListState,
                                        gridState = folderGridState,
                                        contentPadding = padding
                                    )
                                } else {
                                    val videos = videosByFolder[folder] ?: emptyList()
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
                        }
                        ViewMode.FILES -> {
                            val allVideos = videosFlat
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
                            val allVideosForSize = videosFlat
                            val sortedItems = remember(explorerItems, viewSettings.sortField, viewSettings.sortDirection, allVideosForSize) {
                                val folders = explorerItems.filterIsInstance<ExplorerItem.FolderItem>().map { it.folder }
                                val videos = explorerItems.filterIsInstance<ExplorerItem.VideoItem>().map { it.video }
                                
                                val folderVideosMap = folders.associateWith { folder ->
                                    allVideosForSize.filter { it.path.startsWith(folder.id) }
                                }
                                
                                val sortedFolders = folders.applyFolderSort(folderVideosMap, viewSettings.sortField, viewSettings.sortDirection)
                                val sortedVideos = videos.applySort(viewSettings.sortField, viewSettings.sortDirection)
                                
                                sortedFolders.map { ExplorerItem.FolderItem(it) } + sortedVideos.map { ExplorerItem.VideoItem(it) }
                            }
                            
                            val sortedExpVideos = remember(sortedItems) {
                                sortedItems.filterIsInstance<ExplorerItem.VideoItem>().map { it.video }
                            }

                            val pathSegments = remember(currentExplorerPath) {
                                viewModel.getPathSegments(currentExplorerPath)
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = padding.calculateTopPadding())
                            ) {
                                ExplorerPathStrip(
                                    segments = pathSegments,
                                    onSegmentClick = { segment ->
                                        viewModel.onPathSegmentClicked(segment.absolutePath)
                                    }
                                )
                                ExplorerListContent(
                                    items = sortedItems,
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
                                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // INFORMATION BOTTOM SHEET
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
                val allVideosFlat = videosFlat
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

    //RENAME DIALOG
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
                val allVideosFlat = videosFlat
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

    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { fileOpsViewModel.cancelOverwrite() },
            title = { Text("Replace File") },
            text = { Text("A file with this name already exists in the destination. Do you want to replace it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileOpsViewModel.confirmOverwrite(context)
                        selectedFolders = emptySet()
                        selectedVideos = emptySet()
                    }
                ) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { fileOpsViewModel.cancelOverwrite() }) { Text("Cancel") }
            }
        )
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
                    onPlayStream(uri)
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
                        onNavigateToSettings()
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
}
