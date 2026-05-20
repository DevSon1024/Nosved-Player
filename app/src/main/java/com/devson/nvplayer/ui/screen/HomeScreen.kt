package com.devson.nvplayer.ui.screen

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.devson.nvplayer.model.DefaultScreen
import com.devson.nvplayer.model.VideoFolder
import com.devson.nvplayer.ui.components.CustomRenameDialog
import com.devson.nvplayer.ui.components.ViewSettingsBottomSheet
import com.devson.nvplayer.ui.screens.videolist.components.folder.FolderListContent
import com.devson.nvplayer.ui.screens.StorageExplorerScreen
import com.devson.nvplayer.ui.screens.videolist.components.topbar.VideoListTopAppBar
import com.devson.nvplayer.ui.screens.videolist.utils.applyFolderSort
import com.devson.nvplayer.ui.screens.videolist.utils.shareVideos
import com.devson.nvplayer.util.SelectionBottomAppBar
import com.devson.nvplayer.viewmodel.FileOperationsViewModel
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel
import com.devson.nvplayer.model.Video
import com.devson.nvplayer.model.ViewMode
import com.devson.nvplayer.model.applySort
import com.devson.nvplayer.ui.screens.videolist.components.list.VideoListContent
import com.devson.nvplayer.ui.screens.videolist.components.explorer.ExplorerListContent
import com.devson.nvplayer.ui.screens.videolist.components.selection.VideoSelectionBottomBar

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: VideoListViewModel,
    fileOpsViewModel: FileOperationsViewModel,
    homeViewModel: HomeViewModel,
    onFolderClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onVideoClick: (Uri) -> Unit,
    onRecycleBinClick: () -> Unit
) {
    val context = LocalContext.current

    // Trigger initial loading of videos
    LaunchedEffect(Unit) {
        viewModel.loadVideos()
    }

    val videosByFolder by viewModel.videosByFolder.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
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

    // SELECTION STATE
    var selectedFolders by remember { mutableStateOf(emptySet<VideoFolder>()) }
    var selectedVideos by remember { mutableStateOf(emptySet<Video>()) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }

    val sortedFolderKeys = remember(videosByFolder, viewSettings.sortField, viewSettings.sortDirection) {
        val keys = videosByFolder.keys.toList()
        keys.applyFolderSort(videosByFolder, viewSettings.sortField, viewSettings.sortDirection)
    }
    val isSelectionActive = selectedFolders.isNotEmpty() || selectedVideos.isNotEmpty()

    // Hoisted scroll states
    val folderListState = rememberLazyListState()
    val folderGridState = rememberLazyGridState()
    val videoListState = rememberLazyListState()
    val videoGridState = rememberLazyGridState()

    // Watch History Map
    val history by homeViewModel.history.collectAsState()
    val historyMap = remember(history) { history.associateBy { it.uri } }

    // Handles MediaStore permission dialogs (delete/rename on API 29-30)
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            fileOpsViewModel.onPermissionGranted(context)
        }
        fileOpsViewModel.clearPendingIntentSender()
    }

    // Storage Explorer State
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
            },
            onCancel = {
                storageExplorerOp = null
            }
        )
        return
    }

    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Watch pendingIntentSender and launch it automatically
    val pendingIntentSender by fileOpsViewModel.pendingIntentSender.collectAsState()
    LaunchedEffect(pendingIntentSender) {
        pendingIntentSender?.let { sender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // File operation result -> Toast + list reload
    val opResult by fileOpsViewModel.operationResult.collectAsState()
    LaunchedEffect(opResult) {
        opResult?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.loadVideos(forceRefresh = true)
            homeViewModel.loadWatchHistory()
            fileOpsViewModel.clearResult()
        }
    }

    val needsRefresh by fileOpsViewModel.needsRefresh.collectAsState()
    LaunchedEffect(needsRefresh) {
        if (needsRefresh) {
            viewModel.loadVideos(forceRefresh = true)
            homeViewModel.loadWatchHistory()
            fileOpsViewModel.onRefreshHandled()
        }
    }

    // Drives progress bar visibility
    val opInProgress by fileOpsViewModel.operationInProgress.collectAsState()

    // Back handler: clears selection first before navigating out
    BackHandler(enabled = isSelectionActive || (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null)) {
        when {
            selectedVideos.isNotEmpty() -> selectedVideos = emptySet()
            selectedFolders.isNotEmpty() -> selectedFolders = emptySet()
            viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null -> viewModel.navigateExplorerUp()
            else -> {}
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val titleText = when (viewSettings.viewMode) {
                ViewMode.ALL_FOLDERS -> "Folders"
                ViewMode.FILES -> "All Files"
                ViewMode.FOLDERS -> currentExplorerPath?.substringBeforeLast('/')?.substringAfterLast('/') ?: "Folders"
            }
            VideoListTopAppBar(
                isSelectionActive = isSelectionActive,
                titleText = titleText,
                selectedCount = selectedVideos.size + selectedFolders.size,
                totalCount = when (viewSettings.viewMode) {
                    ViewMode.ALL_FOLDERS -> sortedFolderKeys.size
                    ViewMode.FILES -> videosByFolder.values.flatten().size
                    ViewMode.FOLDERS -> explorerNodes.first.size + explorerNodes.second.size
                },
                showBackButton = viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null,
                showHomeBackButton = viewSettings.defaultScreen != DefaultScreen.VIDEO_LIST,
                onRecycleBinClick = onRecycleBinClick,
                onClearSelection = {
                    selectedFolders = emptySet()
                    selectedVideos = emptySet()
                },
                onSelectAll = {
                    when (viewSettings.viewMode) {
                        ViewMode.ALL_FOLDERS -> {
                            selectedFolders = if (selectedFolders.size == sortedFolderKeys.size) emptySet() else sortedFolderKeys.toSet()
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
                onBack = {
                    if (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null) {
                        viewModel.navigateExplorerUp()
                    }
                },
                onNavigateToSettings = onSettingsClick,
                onShowSettings = { showSettingsSheet = true },
                onSearch = { query ->
                    // Global search if query is searched
                },
                searchActive = searchActive,
                searchText = searchText,
                onSearchActiveChange = { active ->
                    searchActive = active
                    if (!active) {
                        searchText = ""
                        viewModel.clearSearch()
                    }
                },
                onSearchTextChange = { text ->
                    searchText = text
                    viewModel.onSearchQueryChanged(text)
                },
                searchSuggestions = searchSuggestions,
                searchFocusRequester = searchFocusRequester,
                keyboard = keyboard,
                onBackToFolders = {
                    selectedFolders = emptySet()
                    selectedVideos = emptySet()
                    if (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null) {
                        viewModel.navigateExplorerUp()
                    }
                }
            )
        },
        bottomBar = {
            if (isSelectionActive) {
                // Unified URI computation across all view modes
                val allVideosFlat = remember(videosByFolder) { videosByFolder.values.flatten() }
                val selectedUris: List<Uri> = remember(
                    viewSettings.viewMode, selectedVideos, selectedFolders, videosByFolder
                ) {
                    when (viewSettings.viewMode) {
                        ViewMode.FILES -> {
                            selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                        }
                        ViewMode.ALL_FOLDERS -> {
                            // Folder-list view: map every video inside selected folders
                            selectedFolders
                                .flatMap { folder -> videosByFolder[folder] ?: emptyList() }
                                .mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
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

                val useFolderBar = viewSettings.viewMode == ViewMode.ALL_FOLDERS && selectedFolders.isNotEmpty()

                if (useFolderBar) {
                    SelectionBottomAppBar(
                        selectedFolders = selectedFolders,
                        videosByFolder = videosByFolder,
                        viewSettings = viewSettings,
                        onVideoSelected = { video, playlist ->
                            onVideoClick(Uri.parse(video.uri))
                        },
                        onClearSelection = {
                            selectedFolders = emptySet()
                            selectedVideos = emptySet()
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
                                val position = when (status) {
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
                    VideoSelectionBottomBar(
                        selectedVideos = selectedVideos,
                        onPlayAll = {
                            val playVideo = selectedVideos.firstOrNull()
                            if (playVideo != null) {
                                onVideoClick(Uri.parse(playVideo.uri))
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
                                val position = when (status) {
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
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (opInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding())
                        .align(Alignment.TopCenter)
                )
            }

            if (isLoading && videosByFolder.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.loadVideos(forceRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (viewSettings.viewMode) {
                        ViewMode.ALL_FOLDERS -> {
                            FolderListContent(
                                folders = videosByFolder,
                                settings = viewSettings,
                                selectedFolders = selectedFolders,
                                historyMap = historyMap,
                                onFolderClick = { folder ->
                                    if (isSelectionActive) {
                                        selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
                                    } else {
                                        onFolderClick(folder.name)
                                    }
                                },
                                onFolderLongClick = { folder ->
                                    selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
                                },
                                listState = folderListState,
                                gridState = folderGridState,
                                contentPadding = padding
                            )
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
                                        onVideoClick(Uri.parse(video.uri))
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
                                        onVideoClick(Uri.parse(video.uri))
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
                selectedFolders.flatMap { videosByFolder[it] ?: emptyList() }.toSet()
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
        val isFolder = selectedFolders.size == 1
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
                    val video = selectedVideos.firstOrNull()
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
            isFolderView = viewSettings.viewMode == ViewMode.ALL_FOLDERS || (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath == null),
            onDismiss = { showSettingsSheet = false },
            viewModel = viewModel
        )
    }

    if (showDeleteConfirmation) {
        val selectedUrisForDelete: List<Uri> = when (viewSettings.viewMode) {
            ViewMode.FILES -> selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
            ViewMode.ALL_FOLDERS -> {
                selectedFolders.flatMap { folder -> videosByFolder[folder] ?: emptyList() }
                    .mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
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
            title = { Text("Delete Item(s)") },
            text = { Text("Choose how you want to delete the selected items.") },
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
